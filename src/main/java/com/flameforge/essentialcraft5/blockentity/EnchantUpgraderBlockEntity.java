package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.block.EnchantUpgraderBlock;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Entry;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Step;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Tier;
import com.flameforge.essentialcraft5.espe.ESPEAccess;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.menu.EnchantUpgraderMenu;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECEnchantUpgrade;
import com.flameforge.essentialcraft5.registry.ECMachineSounds;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 附魔增幅台、附魔升华台（EC5 新机器，原作没有）：把一本<b>单一附魔</b>的附魔书一级一级往上推。
 * 增幅台管 1 → 25 级，升华台管 25 → 50 级；两台是同一个类，档位由方块定（{@link EnchantUpgraderBlock#tier()}）。
 * 规则、价格表、配置都在 {@link EnchantUpgrades}。
 *
 * <h2>流程</h2>
 * 书放进 1 号格，材料放进 3、4 号格，MRU 靠绑定宝石（0 号格）或旁边的线，ESPE 机器自己从周围八个水晶柱位置抽。
 * 每一步（等级 n − 1 → n）：先查门槛——输出格空着、没被红石停住、材料够、（升华台）主人在线且五芒星档位和矩阵余量够——
 * 然后<b>一次扣掉这一步的材料</b>，再按刻边走边扣 MRU 和 ESPE（{@code ⌊T·(p+1)/N⌋ − ⌊T·p/N⌋}，走满正好 T，哪样不够就停在原处），
 * 走满后书留在 1 号格接着升下一步，等级到了目标（默认这台的最高，界面上可调）才送进输出格。
 * 做到一半把书拿走或换掉，这一步付的材料不退、进度清零；停电、缺料、缺 MRU / ESPE 只是暂停，进度存盘。
 *
 * <h2>升华台的矩阵门槛</h2>
 * 材料、能量之外，升华台每一步还要<b>机器主人</b>在线，并且他打开过至少 {@code required_pentacle_tier} 档的五芒星、
 * 矩阵损伤离上限还放得下这一步的量；走满的那一刻把这笔矩阵损伤记到他身上（走 {@link CorruptionEffects#addMatrixDamage}，
 * 所以高损伤照常会抽到腐化效果）。有再多能量也绕不过去：要么等损伤治好再继续，要么停手。
 * 专用服里没有记下主人的机器不工作，单人里没记主人的算房主，规则同其它机器（{@link ECProtection#onlineOwner}）。
 *
 * <h2>其它</h2>
 * 不碰世界方块、不碰实体、不发 Forge 事件、不加载区块；ESPE 只读已加载的方块实体。对外的 ESPE 只进不出。
 * 漏斗、管道只能往对应格里送：书进 1 号格（已有单一附魔、没被排除），材料进 3、4 号格（这台用得到的），输出格任何一面都能抽。
 */
public class EnchantUpgraderBlockEntity extends MRUBlockEntity {

    public static final int SLOT_GEM = 0;
    public static final int SLOT_BOOK = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_A = 3;
    public static final int SLOT_B = 4;
    public static final int SLOTS = 5;

    /** 抽 ESPE 的间隔（刻） */
    public static final int ESPE_PULL_INTERVAL = 10;

    /** ESPE 源的位置：同一高度、隔一格一圈（四正四斜，同地脉凝聚器） */
    public static final List<Vec3i> SOURCES = List.of(
            new Vec3i(2, 0, 0), new Vec3i(-2, 0, 0), new Vec3i(0, 0, 2), new Vec3i(0, 0, -2),
            new Vec3i(2, 0, 2), new Vec3i(-2, 0, 2), new Vec3i(2, 0, -2), new Vec3i(-2, 0, -2));

    private static final String TAG_STEP = "step_level";
    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_STEP_FOR = "step_for";
    private static final String TAG_TARGET = "target";
    /** 只在更新包里（给客户端画特效），存档里没有 */
    private static final String TAG_WORKING = "working";

    /** 界面状态。顺序只许在末尾追加：界面按序号读 */
    public enum Status {
        IDLE(0xFFFF00),
        BAD_BOOK(0xFF0000),
        NOT_ACCEPTED(0xFF0000),
        TOO_LOW(0xFF0000),
        OUTPUT_FULL(0xFFAA00),
        POWERED(0xFFAA00),
        NO_MATERIAL(0xFFFF00),
        NO_OWNER(0xFF0000),
        LOW_TIER(0xFF0000),
        MATRIX_FULL(0xFFFF00),
        TOO_EXPENSIVE(0xFF0000),
        NO_MRU(0xFFFF00),
        NO_ESPE(0xFFFF00),
        WORKING(0x00FF00);

        public static final Status[] VALUES = values();
        public final int color;

        Status(int color) {
            this.color = color;
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
        }
    }

    private final Tier tier;
    /** 正在做的这一步的目标等级；0 = 没有在做的一步 */
    private int stepLevel;
    private int progress;
    /** 开这一步时 1 号格里的书（数量 1），换了就清零 */
    private ItemStack stepFor = ItemStack.EMPTY;
    /** 目标等级；0 = 跟着机器的上限走 */
    private int target;
    private Status status = Status.IDLE;
    /** 这一刻有没有在做事（只用来画特效，变了才发更新包） */
    private boolean working;
    private boolean wantsEspe;
    private long nextEspePull;

    public EnchantUpgraderBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state, Tier tier) {
        super(type, pos, state, () -> ECConfig.get(EnchantUpgrades.config(tier).maxMRU), SLOTS,
                () -> ECConfig.get(EnchantUpgrades.config(tier).maxESPE), 0);
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }

    // ------------------------------------------------------------------
    // 槽位
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用，客户端菜单不知道是哪一档，所以只按槽位分）：0 绑定宝石，1 附魔书，2 输出不能放，3、4 材料 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_GEM -> defaultSlotRule(slot, stack);
            case SLOT_BOOK -> stack.is(Items.ENCHANTED_BOOK);
            case SLOT_OUTPUT -> false;
            default -> true;
        };
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 自动化：书只收单一附魔、没被排除的；材料格只收这一档用得到的东西 */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_BOOK -> EnchantUpgrades.acceptsBook(stack, tier);
            case SLOT_A -> EnchantUpgrades.allMaterialA(tier).contains(stack.getItem());
            case SLOT_B -> EnchantUpgrades.allMaterialB(tier).contains(stack.getItem());
            default -> super.canAutomationInsert(slot, stack);
        };
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == SLOT_OUTPUT;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    @Override
    protected ESPEAccess espeAccess(@Nullable Direction side) {
        return ESPEAccess.INSERT;
    }

    private ESPEStorage espeStorage() {
        ESPEStorage storage = getESPEStorage();
        if (storage == null) {
            throw new IllegalStateException("附魔升级机器没有 ESPE 存储");
        }
        return storage;
    }

    // ------------------------------------------------------------------
    // 工作
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        Level lvl = level;
        if (lvl == null) {
            return;
        }
        wantsEspe = false;
        status = run(lvl);
        setWorking(status == Status.WORKING);
        if (wantsEspe) {
            pullEspe(lvl);
        }
    }

    private Status run(Level lvl) {
        ItemStack book = inventory.getStackInSlot(SLOT_BOOK);
        if (stepLevel > 0 && (book.isEmpty() || !ItemStack.isSameItemSameTags(book, stepFor))) {
            // 书拿走或换了：这一步付掉的材料不退（不然可以先付便宜的一步再换成别的书）
            resetStep();
        }
        if (book.isEmpty()) {
            return Status.IDLE;
        }
        Entry entry = EnchantUpgrades.read(book);
        if (entry == null) {
            return Status.BAD_BOOK;
        }
        int cap = EnchantUpgrades.cap(entry.enchantment(), tier);
        if (cap <= 0) {
            return Status.NOT_ACCEPTED;
        }
        if (entry.level() < tier.minLevel) {
            return Status.TOO_LOW;
        }
        if (entry.level() >= Math.min(cap, targetLevel())) {
            // 到位了：送进输出格（输出格满了就等着）
            if (!inventory.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
                return Status.OUTPUT_FULL;
            }
            inventory.setStackInSlot(SLOT_OUTPUT, book.copyWithCount(1));
            inventory.setStackInSlot(SLOT_BOOK, ItemStack.EMPTY);
            resetStep();
            return Status.IDLE;
        }
        if (neighborPowered()) {
            return Status.POWERED;
        }
        int next = entry.level() + 1;
        if (stepLevel != 0 && stepLevel != next) {
            resetStep();
        }
        Step step = EnchantUpgrades.step(tier, next);
        wantsEspe = step.espe() > 0;
        if (stepLevel == 0) {
            if (step.mruPerTick() > mru.getMaxMRU() || step.espePerTick() > espeStorage().getMaxESPE()) {
                return Status.TOO_EXPENSIVE;
            }
            Status blocked = gate(step);
            if (blocked != null) {
                return blocked;
            }
            ItemStack a = inventory.getStackInSlot(SLOT_A);
            ItemStack b = inventory.getStackInSlot(SLOT_B);
            if (!step.a().satisfiedBy(a) || !step.b().satisfiedBy(b)) {
                return Status.NO_MATERIAL;
            }
            inventory.extractItem(SLOT_A, step.a().count(), false);
            inventory.extractItem(SLOT_B, step.b().count(), false);
            stepLevel = next;
            progress = 0;
            stepFor = book.copyWithCount(1);
        }
        int mruNeed = slice(step.mru(), step.ticks(), progress);
        int espeNeed = slice(step.espe(), step.ticks(), progress);
        if (mru.getMRU() < mruNeed) {
            return Status.NO_MRU;
        }
        if (espeStorage().getESPE() < espeNeed) {
            return Status.NO_ESPE;
        }
        boolean last = progress + 1 >= step.ticks();
        if (last) {
            Status blocked = gate(step);
            if (blocked != null) {
                return blocked;
            }
        }
        mru.extractMRU(mruNeed, true);
        espeStorage().extractESPE(espeNeed, true);
        progress++;
        if (last) {
            completeStep(book, next, step);
        } else {
            markSaved();
        }
        return Status.WORKING;
    }

    /** 每刻该扣的量：⌊T·(p+1)/N⌋ − ⌊T·p/N⌋，走满 N 刻正好 T */
    private static int slice(int total, int ticks, int progress) {
        return (int) ((long) total * (progress + 1) / ticks - (long) total * progress / ticks);
    }

    /**
     * 升华台的门槛（开一步前和最后一刻各查一次）：主人在线，打开过够高档的五芒星，矩阵损伤放得下这一步。
     * 增幅台和配置关掉门槛时恒放行。不放行返回对应状态。
     */
    @Nullable
    private Status gate(Step step) {
        EnchantUpgrades.MachineConfig cfg = EnchantUpgrades.config(tier);
        if (cfg.matrixGate == null || !ECConfig.get(cfg.matrixGate)) {
            return null;
        }
        ServerPlayer owner = ECProtection.onlineOwner(this);
        IPlayerData data = owner == null ? null : PlayerDataEvents.get(owner);
        if (data == null) {
            return Status.NO_OWNER;
        }
        if (data.getPentacleTier() < ECConfig.get(cfg.requiredPentacleTier)) {
            return Status.LOW_TIER;
        }
        if (CorruptionEffects.matrixRoom(data) < step.matrix()) {
            return Status.MATRIX_FULL;
        }
        return null;
    }

    /** 走满：书升一级留在输入格，这一步的矩阵损伤记到主人身上，状态清零 */
    private void completeStep(ItemStack book, int level, Step step) {
        inventory.setStackInSlot(SLOT_BOOK, EnchantUpgrades.withLevel(book, level));
        stepLevel = 0;
        progress = 0;
        stepFor = ItemStack.EMPTY;
        if (step.matrix() > 0) {
            ServerPlayer owner = ECProtection.onlineOwner(this);
            IPlayerData data = owner == null ? null : PlayerDataEvents.get(owner);
            if (data != null) {
                int added = CorruptionEffects.addMatrixDamage(data, step.matrix());
                owner.displayClientMessage(Component.translatable("message.essentialcraft5.enchant_upgrade.matrix",
                        level, added).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        if (this.level != null) {
            this.level.playSound(null, worldPosition, (tier == Tier.ASCENDER ? ECMachineSounds.ASCEND_DONE : ECMachineSounds.ENCHANT_DONE).get(),
                    SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        markSaved();
    }

    private void resetStep() {
        if (stepLevel != 0 || progress != 0 || !stepFor.isEmpty()) {
            stepLevel = 0;
            progress = 0;
            stepFor = ItemStack.EMPTY;
            markSaved();
        }
    }

    private void pullEspe(Level lvl) {
        long now = lvl.getGameTime();
        if (now < nextEspePull) {
            return;
        }
        nextEspePull = now + ESPE_PULL_INTERVAL;
        ESPEStorage storage = espeStorage();
        if (storage.getESPE() < storage.getMaxESPE()) {
            ESPETransfer.pullFromAround(lvl, worldPosition, storage, SOURCES);
        }
    }

    private void markSaved() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    private void setWorking(boolean now) {
        if (working != now) {
            working = now;
            markClientDirty();
        }
    }

    // ------------------------------------------------------------------
    // 界面数值（服务端菜单现取）
    // ------------------------------------------------------------------

    public Status status() {
        return status;
    }

    public int stepLevel() {
        return stepLevel;
    }

    public int progress() {
        return progress;
    }

    /** 正在做的这一步一共多少刻；没在做为 0 */
    public int required() {
        return stepLevel == 0 ? 0 : EnchantUpgrades.step(tier, stepLevel).ticks();
    }

    /** 目标等级（夹在 [最低 + 1, 上限] 里；没设过就是上限） */
    public int targetLevel() {
        return target <= 0 ? tier.cap : Math.max(tier.minLevel + 1, Math.min(tier.cap, target));
    }

    public void setTargetLevel(int level) {
        int clamped = Math.max(tier.minLevel + 1, Math.min(tier.cap, level));
        int stored = clamped >= tier.cap ? 0 : clamped;
        if (stored != target) {
            target = stored;
            markSaved();
        }
    }

    public boolean isWorking() {
        return working;
    }

    @Override
    protected boolean ambientWorking() {
        return status == Status.WORKING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return tier == Tier.ASCENDER ? MachineAmbiences.ASCENDER : MachineAmbiences.AMPLIFIER;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition, worldPosition.offset(1, 2, 1));
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean(TAG_WORKING, working);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (target > 0) {
            tag.putInt(TAG_TARGET, target);
        }
        if (stepLevel > 0 && !stepFor.isEmpty()) {
            tag.putInt(TAG_STEP, stepLevel);
            tag.putInt(TAG_PROGRESS, progress);
            tag.put(TAG_STEP_FOR, stepFor.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        target = Math.max(0, tag.getInt(TAG_TARGET));
        stepLevel = 0;
        progress = 0;
        stepFor = ItemStack.EMPTY;
        if (tag.contains(TAG_STEP_FOR)) {
            ItemStack saved = ItemStack.of(tag.getCompound(TAG_STEP_FOR));
            int level = tag.getInt(TAG_STEP);
            // 数据不全就当没开工：这一步付的材料已经扣了，进度从头来
            if (!saved.isEmpty() && level > 0) {
                stepLevel = level;
                stepFor = saved;
                progress = Math.max(0, tag.getInt(TAG_PROGRESS));
            }
        }
        if (tag.contains(TAG_WORKING)) {
            working = tag.getBoolean(TAG_WORKING);
        }
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new EnchantUpgraderMenu(id, inventory, this);
    }

    /** 方块给的类型和档位（方块实体类型按方块取，同水晶柱） */
    public static EnchantUpgraderBlockEntity create(BlockPos pos, BlockState state) {
        EnchantUpgraderBlock block = (EnchantUpgraderBlock) state.getBlock();
        return new EnchantUpgraderBlockEntity(block.blockEntityType(), pos, state, block.tier());
    }
}
