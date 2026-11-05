package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MagicalEnchanterMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECDevices;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔力附魔台。对应原作 TileMagicalEnchanter。
 * <p>
 * 槽位：0 绑定宝石，1 要附魔的东西，2 输出。不要经验和青金石，只要 MRU 和书架：
 * <ul>
 *   <li><b>等级</b> = 周围 x、z 各 ±2、y 从机器这一层往上 0~2（共 74 格，不含自己）所有方块的附魔能力之和
 *       （Forge getEnchantPowerBonus：书架 1，别的模组的附魔能力方块照它们自己的值），最多 {@code max_enchant_level}（默认 60）。
 *       不要求书架和机器之间是空的（原作如此）。每 {@value #POWER_REFRESH} tick 重算一次。</li>
 *   <li>开工时按当时的等级抽一次附魔（原版附魔台同样的抽法，不含宝藏附魔），并定下耗时 = 等级 × 10 tick；
 *       之后每 tick 耗 {@code mru_usage}（默认 100）MRU、进度 +1，满了就把附魔好的一件放进输出槽。总共等级 × 1000 MRU。</li>
 *   <li>输出槽必须是空的；已经附过魔的东西不能再附。</li>
 * </ul>
 * 移植改动（原作 bug 顺便修）：
 * <ul>
 *   <li>书出<b>附魔书</b>：附魔写进 StoredEnchantments（原作写进 ench，铁砧用不了），而且保留抽到的<b>全部</b>附魔（D7；原版附魔台会随机去掉一条）。
 *       一叠书也能附：每次拿一本（原作按整叠判断，书一叠以上就「无法附魔」，所以原作实际上不能批量出书）。</li>
 *   <li>MRU 一时不够、红石停机、输出槽没清空：<b>暂停</b>，进度和抽到的附魔都留着（原作清零进度、重抽附魔）。</li>
 *   <li>抽到的附魔和进度存盘，读档后接着做（原作不存，读档重抽），但不放进发给客户端的更新包（免得改过的客户端先看结果再决定重不重抽）；
 *       做到一半换了要附魔的东西才清零重抽
 *       （原作不管换没换，抽给剑的附魔会原样加到换进来的镐上）。</li>
 *   <li>书架能力每 20 tick 算一次（原作每 tick 被调 3~4 次、每次扫 74 格）；只读已加载的区块。书的翻页动画只在客户端算。</li>
 *   <li>界面状态由服务端算，客户端不再自己抽附魔表；漏斗、管道不能往输出槽塞东西，只往 1 号槽送能附魔的东西。</li>
 * </ul>
 */
public class MagicalEnchanterBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.magicalenchanter */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruUsage;
        public static ForgeConfigSpec.IntValue maxEnchantLevel;
        public static ForgeConfigSpec.BooleanValue generatesCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("magical_enchanter");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used per tick while enchanting. An item takes (level x 10) ticks.",
                            "附魔时每 tick 消耗的 MRU。附一件要 等级 × 10 tick")
                    .defineInRange("mru_usage", 100, 0, 1_000_000);
            maxEnchantLevel = builder.comment("Cap on the enchanting level from surrounding bookshelves.",
                            "周围书架提供的附魔等级上限")
                    .defineInRange("max_enchant_level", 60, 0, 1000);
            generatesCorruption = builder.comment("Whether the enchanter leaks MRU into nearby MRUCUs while working.",
                            "工作时是否向周围泄漏 MRU")
                    .define("generate_corruption", false);
            maxCorruptionGen = builder.comment("Max MRU leaked per working tick (a random amount from 0 to this).",
                            "工作时每 tick 最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 2, 0, 10000);
            builder.pop();
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_INPUT = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOTS = 3;

    /** 界面状态（原作 GuiEnchantmentState 的文字，加一个红石停机） */
    public static final int STATUS_NOTHING = 0;
    public static final int STATUS_OUTPUT_FULL = 1;
    public static final int STATUS_NO_MRU = 2;
    public static final int STATUS_CANNOT = 3;
    public static final int STATUS_NO_BOOKSHELVES = 4;
    public static final int STATUS_WORKING = 5;
    public static final int STATUS_REDSTONE = 6;

    /** 书架能力多少 tick 重算一次 */
    public static final int POWER_REFRESH = 20;
    /** 每级附魔要多少 tick（原作 getRequiredTimeToAct = 等级 × 10） */
    public static final int TICKS_PER_LEVEL = 10;

    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_LEVEL = "level";
    private static final String TAG_ROLLED = "rolled_enchantments";
    private static final String TAG_ROLLED_FOR = "rolled_for";

    /** 已经做了多少 tick */
    private int progress;
    /** 开工时定下的等级（耗时 = 等级 × 10）；0 表示还没开工 */
    private int lockedLevel;
    /** 开工时抽到的附魔；null 表示还没抽 */
    @Nullable
    private List<EnchantmentInstance> rolled;
    /** 抽附魔时 1 号槽里的东西（数量 1），换了东西就重来 */
    private ItemStack rolledFor = ItemStack.EMPTY;

    private int cachedPower;
    private int powerRefresh;
    /** 抽到空表（这件东西在这个等级没有能附的魔）后，等下一次重算书架再抽 */
    private int rollCooldown;
    private int status = STATUS_NOTHING;

    // ---- 客户端书本动画（照原版 EnchantmentTableBlockEntity.bookAnimationTick，只在客户端算） ----
    public int time;
    public float flip;
    public float oFlip;
    public float flipT;
    public float flipA;
    public float open;
    public float oOpen;
    public float rot;
    public float oRot;
    public float tRot;
    private static final RandomSource ANIMATION_RANDOM = RandomSource.create();

    public MagicalEnchanterBlockEntity(BlockPos pos, BlockState state) {
        super(ECDevices.MAGICAL_ENCHANTER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    /** 槽位规则（两端共用）：0 号绑定宝石，1 号任意物品（原作 SlotGeneric），2 号输出槽不能放 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot != SLOT_OUTPUT && defaultSlotRule(slot, stack);
    }

    /** 这件东西（按一件算）能不能附魔：可附魔、还没附过魔、附魔能力大于 0 */
    public static boolean canEnchant(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack single = stack.copyWithCount(1);
        return single.isEnchantable() && single.getEnchantmentValue() > 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 自动化只往 1 号槽送能附魔的东西 */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        if (slot == SLOT_INPUT) {
            return canEnchant(stack);
        }
        return super.canAutomationInsert(slot, stack);
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == SLOT_OUTPUT;
    }

    // ------------------------------------------------------------------
    // 工作
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (level == null) {
            return;
        }
        if (--powerRefresh <= 0) {
            powerRefresh = POWER_REFRESH;
            cachedPower = scanPower();
            rollCooldown = 0;
        }
        if (rollCooldown > 0) {
            rollCooldown--;
        }
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        // 换了要附魔的东西（或拿走了）：清掉进度和抽到的附魔
        if (rolled != null && (input.isEmpty() || !ItemStack.isSameItemSameTags(input, rolledFor))) {
            reset();
        }
        int usage = ECConfig.get(Config.mruUsage);
        if (input.isEmpty()) {
            status = STATUS_NOTHING;
            return;
        }
        if (!inventory.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
            status = STATUS_OUTPUT_FULL;
            return;
        }
        if (!canEnchant(input)) {
            status = STATUS_CANNOT;
            return;
        }
        if (neighborPowered()) {
            status = STATUS_REDSTONE;
            return;
        }
        if (rolled == null && cachedPower <= 0) {
            status = STATUS_NO_BOOKSHELVES;
            return;
        }
        if (mru.getMRU() < usage) {
            status = STATUS_NO_MRU;
            return;
        }
        if (rolled == null) {
            if (rollCooldown > 0) {
                status = STATUS_CANNOT;
                return;
            }
            List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(level.random, input.copyWithCount(1), cachedPower, false);
            if (list.isEmpty()) {
                rollCooldown = POWER_REFRESH;
                status = STATUS_CANNOT;
                return;
            }
            rolled = list;
            rolledFor = input.copyWithCount(1);
            lockedLevel = cachedPower;
            progress = 0;
        }
        status = STATUS_WORKING;
        mru.extractMRU(usage, true);
        progress++;
        if (ECConfig.get(Config.generatesCorruption)) {
            ECCorruption.randomIncreaseAt(level, worldPosition, level.random, ECConfig.get(Config.maxCorruptionGen));
        }
        if (progress >= requiredTicks()) {
            finish(input);
        }
        markSaved();
    }

    /** 做完：取 1 件，按抽到的附魔附好放进输出槽（书变附魔书，附魔进 StoredEnchantments） */
    private void finish(ItemStack input) {
        List<EnchantmentInstance> list = rolled;
        reset();
        if (list == null) {
            return;
        }
        inventory.setStackInSlot(SLOT_OUTPUT, enchantCopy(input, list));
        inventory.extractItem(SLOT_INPUT, 1, false);
    }

    /** 照原版 EnchantmentMenu：书换成附魔书（带上原来的 NBT，比如改过的名字），全部附魔都保留 */
    public static ItemStack enchantCopy(ItemStack input, List<EnchantmentInstance> enchantments) {
        boolean book = input.is(Items.BOOK);
        ItemStack result;
        if (book) {
            result = new ItemStack(Items.ENCHANTED_BOOK);
            CompoundTag tag = input.getTag();
            if (tag != null) {
                result.setTag(tag.copy());
            }
        } else {
            result = input.copyWithCount(1);
        }
        for (EnchantmentInstance instance : enchantments) {
            if (book) {
                EnchantedBookItem.addEnchantment(result, instance);
            } else {
                result.enchant(instance.enchantment, instance.level);
            }
        }
        return result;
    }

    private void reset() {
        if (rolled != null || progress != 0 || lockedLevel != 0) {
            rolled = null;
            rolledFor = ItemStack.EMPTY;
            progress = 0;
            lockedLevel = 0;
            markSaved();
        }
    }

    /** 原作 getMaxPower：周围 5×3×5（机器这一层往上）除自己以外 74 格的附魔能力之和，封顶 */
    private int scanPower() {
        double sum = 0D;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.setWithOffset(worldPosition, dx, dy, dz);
                    // 只读已加载的区块（机器在区块边上时，旁边的区块可能没加载）
                    if (ECLevels.isFullyLoaded(level, cursor)) {
                        sum += level.getBlockState(cursor).getEnchantPowerBonus(level, cursor);
                    }
                }
            }
        }
        return Mth.clamp(powerToLevel(sum), 0, ECConfig.get(Config.maxEnchantLevel));
    }

    /**
     * 附魔能力之和 → 等级。原作是 int 累加（书架各 1，小数方块每格被截断成 0）；别的模组的方块可能给小数，这里先加再取整。
     * 取整前加一个小量：各格的值是 float，0.7F 转成 double 是 0.69999998…，10 格加起来直接取整会少 1 级
     * （原版附魔台 float 累加再截断也有这个问题）。整数书架不受影响。
     */
    public static int powerToLevel(double sum) {
        return Mth.floor(sum + 1e-4);
    }

    private int requiredTicks() {
        return lockedLevel * TICKS_PER_LEVEL;
    }

    /** 只标记区块要存盘，不像 setChanged 那样通知邻居（进度每 tick 都在变） */
    private void markSaved() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    // ------------------------------------------------------------------
    // 书本动画（客户端）
    // ------------------------------------------------------------------

    @Override
    protected void clientTick() {
        if (level == null) {
            return;
        }
        oOpen = open;
        oRot = rot;
        Player player = level.getNearestPlayer(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, 3D, false);
        if (player != null) {
            double dx = player.getX() - (worldPosition.getX() + 0.5D);
            double dz = player.getZ() - (worldPosition.getZ() + 0.5D);
            tRot = (float) Mth.atan2(dz, dx);
            open += 0.1F;
            if (open < 0.5F || ANIMATION_RANDOM.nextInt(40) == 0) {
                float before = flipT;
                do {
                    flipT += (float) (ANIMATION_RANDOM.nextInt(4) - ANIMATION_RANDOM.nextInt(4));
                } while (before == flipT);
            }
        } else {
            tRot += 0.02F;
            open -= 0.1F;
        }
        rot = wrap(rot);
        tRot = wrap(tRot);
        rot += wrap(tRot - rot) * 0.4F;
        open = Mth.clamp(open, 0F, 1F);
        time++;
        oFlip = flip;
        float delta = Mth.clamp((flipT - flip) * 0.4F, -0.2F, 0.2F);
        flipA += (delta - flipA) * 0.9F;
        flip += flipA;
    }

    private static float wrap(float angle) {
        while (angle >= (float) Math.PI) {
            angle -= (float) Math.PI * 2F;
        }
        while (angle < -(float) Math.PI) {
            angle += (float) Math.PI * 2F;
        }
        return angle;
    }

    /** 原作 getRenderBoundingBox：书会浮到方块上面一点 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition, worldPosition.offset(1, 2, 1));
    }

    // ------------------------------------------------------------------
    // 界面数值（服务端菜单现取）
    // ------------------------------------------------------------------

    public int statusCode() {
        return status;
    }

    /** 周围书架现在能提供的等级 */
    public int power() {
        return cachedPower;
    }

    /** 正在附的等级；没开工为 0 */
    public int lockedLevel() {
        return lockedLevel;
    }

    public int getProgress() {
        return progress;
    }

    /** 正在附的这件要多少 tick；没开工为 0 */
    public int requiredProgress() {
        return rolled == null ? 0 : requiredTicks();
    }

    /** 抽到的附魔（只读副本，测试和调试用）；没开工为空表 */
    public List<EnchantmentInstance> rolledEnchantments() {
        return rolled == null ? List.of() : List.copyOf(rolled);
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    /**
     * 发给客户端的更新包（方块更新、区块数据）里去掉抽到的附魔和进度：客户端用不到（界面数值走 MachineData，
     * 书本动画在客户端自己算），发出去的话改过的客户端能提前看到抽了什么，不满意就取出放回重抽，每次只花一 tick 的 MRU。
     * 存盘（saveAdditional）照写。
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.remove(TAG_ROLLED);
        tag.remove(TAG_ROLLED_FOR);
        tag.remove(TAG_PROGRESS);
        tag.remove(TAG_LEVEL);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (rolled != null) {
            tag.putInt(TAG_PROGRESS, progress);
            tag.putInt(TAG_LEVEL, lockedLevel);
            ListTag list = new ListTag();
            for (EnchantmentInstance instance : rolled) {
                ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(instance.enchantment);
                if (id != null) {
                    list.add(EnchantmentHelper.storeEnchantment(id, instance.level));
                }
            }
            tag.put(TAG_ROLLED, list);
            tag.put(TAG_ROLLED_FOR, rolledFor.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        rolled = null;
        rolledFor = ItemStack.EMPTY;
        progress = 0;
        lockedLevel = 0;
        if (tag.contains(TAG_ROLLED, Tag.TAG_LIST)) {
            List<EnchantmentInstance> list = new ArrayList<>();
            ListTag saved = tag.getList(TAG_ROLLED, Tag.TAG_COMPOUND);
            for (int i = 0; i < saved.size(); i++) {
                CompoundTag entry = saved.getCompound(i);
                ResourceLocation id = EnchantmentHelper.getEnchantmentId(entry);
                Enchantment enchantment = id == null ? null : ForgeRegistries.ENCHANTMENTS.getValue(id);
                if (enchantment != null) {
                    list.add(new EnchantmentInstance(enchantment, EnchantmentHelper.getEnchantmentLevel(entry)));
                }
            }
            ItemStack forStack = ItemStack.of(tag.getCompound(TAG_ROLLED_FOR));
            int level = Math.max(0, tag.getInt(TAG_LEVEL));
            // 附魔全都不认识了（别的模组删了）或数据不全：当作没开工，下次重抽
            if (!list.isEmpty() && !forStack.isEmpty() && level > 0) {
                rolled = list;
                rolledFor = forStack;
                lockedLevel = level;
                progress = Math.max(0, tag.getInt(TAG_PROGRESS));
            }
        }
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MagicalEnchanterMenu(id, inventory, this);
    }

    // ------------------------------------------------------------------ 声音和粒子

    @Override
    protected boolean ambientWorking() {
        return status == STATUS_WORKING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.ENCHANTER;
    }
}
