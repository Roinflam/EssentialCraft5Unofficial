package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.EssenceItem;
import com.flameforge.essentialcraft5.menu.CrystalExtractorMenu;
import com.flameforge.essentialcraft5.registry.ECEssence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 水晶提取器。对应原作 TileCrystalExtractor。
 * <p>
 * 贴在元素水晶旁边（东南西北，同一高度），0 号槽绑定宝石，1~12 号是产物格。12 个产物格<b>全空</b>、没有红石信号时开工：
 * 每 tick 消耗 100 MRU（mru_usage）、进度 +1，满 1000（required_ticks）出一轮。每个产物格各抽一次：
 * 随机挑 16 种精华之一（4 稀有度 × 4 元素，等概率），
 * {@code 几率% = (int)(大小 × 3000 × 元素值 / {37500, 50000, 75000, 150000}[稀有度])}，也就是 大小 × 元素值 × {0.08, 0.06, 0.04, 0.02}；
 * {@code random(100) < 几率} 就出，数量 {@code random(1 + 几率 / 4)}，至少 1 个（原作 L60-62）。不消耗水晶。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>一格的数量不超过精华的最大堆叠（64）。原作几率可以到 800%，一格最多算出 200 个（实际物品栏上限 64）；精华堆叠已按 D2 改成 64，产量和原作一样。</li>
 *   <li>进度存盘（原作不存，区块卸载最多白扔 10 万 MRU）。</li>
 *   <li>产物格只能取、不能放（原作是普通槽，放进东西会让机器停工）。</li>
 *   <li>找水晶缓存方向（{@link AdjacentCrystal}），不每 tick 查四个邻居。</li>
 *   <li>界面加了进度条（原作没有任何进度显示），悬停看状态和水晶数值。</li>
 * </ul>
 */
public class CrystalExtractorBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.crystalextractor */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruUsage;
        public static ForgeConfigSpec.IntValue requiredTicks;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("crystal_extractor");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used per working tick.", "工作时每 tick 消耗的 MRU")
                    .defineInRange("mru_usage", 100, 0, 1_000_000);
            requiredTicks = builder.comment("Ticks per extraction round.", "每轮提取要多少 tick")
                    .defineInRange("required_ticks", 1000, 1, 1_000_000);
            builder.pop();
        }
    }

    /** 界面上显示的状态（服务端算好，随菜单同步） */
    public enum Status {
        WORKING(0x00FF00),
        /** 产物格没取空（原作要 12 格全空才开下一轮） */
        OUTPUT_FULL(0xFFFF00),
        NO_CRYSTAL(0xFFFFFF),
        NO_MRU(0xFFFF00),
        POWERED(0xFF0000);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public MutableComponent label() {
            return Component.translatable("gui.essentialcraft5.crystal_extractor.status." + name().toLowerCase(Locale.ROOT));
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NO_CRYSTAL;
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int FIRST_OUTPUT = 1;
    public static final int OUTPUTS = 12;
    public static final int SLOTS = FIRST_OUTPUT + OUTPUTS;

    public static final String TAG_PROGRESS = "progress";
    private static final String TAG_ACTIVE = "active";

    /** 原作 essenceChance：几率% = 大小 × 3000 × 元素值 / 这个数（按稀有度） */
    private static final double[] CHANCE_DIVISORS = {37500D, 50000D, 75000D, 150000D};

    private final AdjacentCrystal crystal = new AdjacentCrystal();
    /** 已推进的 tick 数（原作 progressLevel） */
    private int progress;
    private Status status = Status.NO_CRYSTAL;

    // ---- 给界面的水晶快照 ----
    private boolean hasCrystal;
    private float crystalSize;
    private final float[] crystalElements = new float[4];

    // ---- 客户端粒子开关（原作 spawnParticles：能开工且有 MRU），变了才发包 ----
    private boolean active;
    private boolean sentActive;
    private int activeCooldown;

    public CrystalExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ECEssence.CRYSTAL_EXTRACTOR_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    /** 槽位规则（两端共用）：0 号绑定宝石，产物格什么都不能放（原作 isItemValidForSlot 只认 0 号的宝石） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_GEM && defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot >= FIRST_OUTPUT && slot < SLOTS;
    }

    /** 邻居变了（方块的 neighborChanged 调） */
    public void neighborChanged() {
        crystal.markDirty();
    }

    // ------------------------------------------------------------------
    // 公式（静态，GameTest 直接调）
    // ------------------------------------------------------------------

    public static int requiredTicks() {
        return Math.max(1, ECConfig.get(Config.requiredTicks));
    }

    /**
     * 某种精华的几率（百分数，可以超过 100），原作 {@code (int)(size × 3000 × 元素值 / essenceChance[稀有度])}。
     *
     * @param rarity 0 普通、1 罕见、2 稀有、3 卓越
     */
    public static int chancePercent(double size, double element, int rarity) {
        double value = size * 3000D * element / CHANCE_DIVISORS[rarity];
        return value > 0 ? (int) value : 0;
    }

    /**
     * 一个产物格抽一次（原作 createItems 循环体）：随机挑一种精华，按几率决定出不出、出多少。
     *
     * @param elements 火、水、地、风四个元素值（下标同 {@link IElementalCrystal.Element#ordinal()}）
     * @return 抽中的精华，没抽中是空
     */
    public static ItemStack roll(RandomSource random, double size, double[] elements) {
        int kind = random.nextInt(16);
        int rarity = kind / 4;
        int element = kind % 4;
        int chance = chancePercent(size, elements[element], rarity);
        if (random.nextInt(100) >= chance) {
            return ItemStack.EMPTY;
        }
        EssenceItem item = ECEssence.essence(rarity, IElementalCrystal.Element.byIndex(element));
        int count = random.nextInt(1 + chance / 4);
        return new ItemStack(item, Mth.clamp(count, 1, new ItemStack(item).getMaxStackSize()));
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (level == null) {
            return;
        }
        IElementalCrystal found = crystal.find(level, worldPosition);
        snapshot(found);
        boolean canWork = found != null && outputsEmpty();
        if (neighborPowered()) {
            status = Status.POWERED;
        } else if (!outputsEmpty()) {
            status = Status.OUTPUT_FULL;
        } else if (found == null) {
            status = Status.NO_CRYSTAL;
        } else if (mru.getMRU() < ECConfig.get(Config.mruUsage)) {
            status = Status.NO_MRU;
        } else {
            status = Status.WORKING;
            mru.extractMRU(ECConfig.get(Config.mruUsage), true);
            if (progress + 1 >= requiredTicks()) {
                setProgress(0);
                fill(found);
            } else {
                setProgress(progress + 1);
            }
        }
        updateActive(status != Status.POWERED && canWork && mru.getMRU() > 0);
    }

    private boolean outputsEmpty() {
        for (int i = FIRST_OUTPUT; i < SLOTS; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void fill(IElementalCrystal found) {
        double size = Mth.clamp(found.size(), 0F, IElementalCrystal.MAX_SIZE);
        double[] elements = new double[4];
        for (IElementalCrystal.Element element : IElementalCrystal.Element.values()) {
            elements[element.ordinal()] = found.element(element);
        }
        for (int i = FIRST_OUTPUT; i < SLOTS; i++) {
            ItemStack stack = roll(level.random, size, elements);
            if (!stack.isEmpty()) {
                inventory.setStackInSlot(i, stack);
            }
        }
    }

    private void setProgress(int value) {
        if (value != progress) {
            progress = value;
            // 耗能为 0 时 MRU 不变，基类不会标记存盘，这里自己标
            level.blockEntityChanged(worldPosition);
        }
    }

    private void snapshot(@Nullable IElementalCrystal found) {
        hasCrystal = found != null;
        if (found == null) {
            return;
        }
        crystalSize = found.size();
        for (IElementalCrystal.Element element : IElementalCrystal.Element.values()) {
            crystalElements[element.ordinal()] = found.element(element);
        }
    }

    private void updateActive(boolean now) {
        active = now;
        if (activeCooldown > 0) {
            activeCooldown--;
            return;
        }
        if (active != sentActive) {
            sentActive = active;
            activeCooldown = SYNC_INTERVAL;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void clientTick() {
        if (!active || level == null) {
            return;
        }
        // 原作：从机器范围里冒传送门粒子飞向水晶（每 tick 10 个）
        crystal.find(level, worldPosition);
        Direction side = crystal.side();
        if (side == null) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            level.addParticle(ParticleTypes.PORTAL,
                    worldPosition.getX() + level.random.nextDouble(),
                    worldPosition.getY() + level.random.nextDouble(),
                    worldPosition.getZ() + level.random.nextDouble(),
                    side.getStepX(), 0D, side.getStepZ());
        }
    }

    // ------------------------------------------------------------------
    // 访问（界面数值、GameTest）
    // ------------------------------------------------------------------

    public int getProgress() {
        return progress;
    }

    public Status getStatus() {
        return status;
    }

    public boolean hasCrystal() {
        return hasCrystal;
    }

    public int displaySize() {
        return hasCrystal ? (int) Mth.clamp(crystalSize, 0F, IElementalCrystal.MAX_SIZE) : 0;
    }

    public int displayElement(IElementalCrystal.Element element) {
        return hasCrystal ? (int) Mth.clamp(crystalElements[element.ordinal()], 0F, IElementalCrystal.MAX_ELEMENT) : 0;
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (progress > 0) {
            tag.putInt(TAG_PROGRESS, progress);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        // 只有客户端收到的更新包里有这个键；存档里没有，服务端每 tick 重算
        active = tag.getBoolean(TAG_ACTIVE);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean(TAG_ACTIVE, active);
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CrystalExtractorMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return status == Status.WORKING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.CRYSTAL_EXTRACTOR;
    }
}
