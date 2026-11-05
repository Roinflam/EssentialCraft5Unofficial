package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.EssenceItem;
import com.flameforge.essentialcraft5.menu.CrystalControllerMenu;
import com.flameforge.essentialcraft5.registry.ECEssence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

/**
 * 水晶控制器。对应原作 TileCrystalController。
 * <p>
 * 贴在元素水晶旁边（东南西北，同一高度），0 号槽绑定宝石，1 号槽放精华。没有红石信号、水晶还在生长时：
 * <ul>
 *   <li>每 tick 有 {@code 5%}（use_mru_chance）的几率消耗 {@code 100}（mru_usage）MRU（原作的「空转」耗能）；</li>
 *   <li>存着至少 {@code mru_usage × 10} MRU 时，每 tick 有 {@code 0.1% × (稀有度 + 1)} 的几率吃掉 1 个精华和 mru_usage × 10 MRU，
 *       给水晶对应的元素加 {@code random(2 × (稀有度 + 1))} 点（普通 0~1 …… 卓越 0~7），结果夹在 0~100。</li>
 * </ul>
 * 移植改动（batch2 D3）：
 * <ul>
 *   <li>原作不管旁边有没有水晶、水晶长没长成，每 tick 都有 5% 几率扣 100 MRU（平均 5 MRU/t 白烧）。这里只在旁边有<b>生长中</b>的水晶时扣。</li>
 *   <li>原作加完超过 100（或低于 0）整次作废，但精华和 MRU 照扣；这里夹到 100。那种元素已经是 100 时不再吃精华（原作会一直白吃）。</li>
 *   <li>1 号槽只收精华（原作什么都能放）。</li>
 *   <li>界面显示真实几率（每 tick 0.1% × (稀有度 + 1)）。原作写的是 2 × (稀有度 + 1)%，和实际差了一个数量级。</li>
 *   <li>找水晶缓存方向（{@link AdjacentCrystal}），不每 tick 查四个邻居。</li>
 *   <li>吃精华的门槛从「多于」1000 MRU 改成「至少」1000 MRU（本来就只扣 1000）。</li>
 * </ul>
 * 研究手册第九章的「定向培育」（第十批）：喂完一次后喂的那种元素到了 {@code bred_threshold}（默认 20），
 * 给 16 格内的玩家打勾（{@link #GUIDE_BRED}；原来那步「拿到控制器」的进度留着不改）。
 */
public class CrystalControllerBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.crystalcontroller */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruUsage;
        public static ForgeConfigSpec.DoubleValue useMRUChance;
        public static ForgeConfigSpec.DoubleValue mutationChance;
        public static ForgeConfigSpec.IntValue bredThreshold;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("crystal_controller");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used by the idle drain; feeding one essence costs 10 times this.",
                            "空转每次消耗的 MRU；每吃一个精华消耗它的 10 倍")
                    .defineInRange("mru_usage", 100, 0, 100_000);
            useMRUChance = builder.comment("Chance per tick of the idle drain (only next to a growing crystal).",
                            "每 tick 空转耗能的几率（只在旁边有生长中的水晶时）")
                    .defineInRange("use_mru_chance", 0.05D, 0D, 1D);
            mutationChance = builder.comment("Chance per tick to feed one essence = this × (rarity + 1); rarity 0 common … 3 exceptional.",
                            "每 tick 吃一个精华的几率 = 这个值 × (稀有度 + 1)；稀有度 0 普通 …… 3 卓越")
                    .defineInRange("mutation_chance_modifier", 0.001D, 0D, 1D);
            bredThreshold = builder.comment("Research book step \"selective breeding\": done when a feed leaves the fed element at this value or higher.",
                            "研究手册「定向培育」一步：喂完一次后，喂的那种元素到这个值以上就算做成")
                    .defineInRange("bred_threshold", DEFAULT_BRED_THRESHOLD, 1, (int) IElementalCrystal.MAX_ELEMENT);
            builder.pop();
        }
    }

    /** 界面上显示的状态（服务端算好，随菜单同步） */
    public enum Status {
        WORKING(0x00FF00),
        NO_CRYSTAL(0xFFFFFF),
        /** 水晶长满了（大小 100），元素再也改不了 */
        GROWN(0xFFFF00),
        NO_ESSENCE(0xFFFFFF),
        NO_MRU(0xFFFF00),
        /** 精华对应的元素已经是 100 */
        ELEMENT_FULL(0xFFFF00),
        POWERED(0xFF0000);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public MutableComponent label() {
            return Component.translatable("gui.essentialcraft5.crystal_controller.status." + name().toLowerCase(Locale.ROOT));
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NO_CRYSTAL;
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_ESSENCE = 1;
    public static final int SLOTS = 2;
    /** 吃一个精华的耗能是空转耗能的几倍（原作 mruUsage × 10） */
    public static final int FEED_COST_MULTIPLIER = 10;
    /** 「定向培育」的默认门槛：普通精华喂一颗水晶的整个生长期平均能加约 25 点，两三代就能推到；30 要四五代 */
    public static final int DEFAULT_BRED_THRESHOLD = 20;
    /** 培育到门槛时多远以内的玩家打勾（同富集仓成形的 16 格） */
    public static final double GUIDE_RADIUS = 16D;
    /** 研究手册第九章「定向培育」一步，只由代码发（进度文件被数据包删掉时什么都不做） */
    public static final ResourceLocation GUIDE_BRED = EssentialCraft5.id("guide/essence/bred_crystal");
    public static final String GUIDE_CRITERION = "awarded_by_code";

    private static final String TAG_ACTIVE = "active";
    /** 原作 REDSTONE 粒子带 (-1, 1, 0) 的颜色参数，实际显示成绿色 */
    private static final DustParticleOptions PARTICLE = new DustParticleOptions(new Vector3f(0.1F, 0.9F, 0.1F), 1F);

    private final AdjacentCrystal crystal = new AdjacentCrystal();
    private Status status = Status.NO_CRYSTAL;

    // ---- 给界面的水晶快照（服务端每 tick 更新） ----
    private boolean hasCrystal;
    private float crystalSize;
    private final float[] crystalElements = new float[4];

    // ---- 客户端粒子开关：旁边有水晶且机器里有 MRU（原作的 spawnParticles 条件），变了才发包 ----
    private boolean active;
    private boolean sentActive;
    private int activeCooldown;

    public CrystalControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ECEssence.CRYSTAL_CONTROLLER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    /** 槽位规则（两端共用）：0 号绑定宝石，1 号只放精华 */
    public static boolean slotRule(int slot, ItemStack stack) {
        if (slot == SLOT_ESSENCE) {
            return stack.getItem() instanceof EssenceItem;
        }
        return defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 邻居变了（方块的 neighborChanged 调） */
    public void neighborChanged() {
        crystal.markDirty();
    }

    // ------------------------------------------------------------------
    // 规则（静态，GameTest 直接调）
    // ------------------------------------------------------------------

    /** 吃一个精华的耗能（原作 mruUsage × 10） */
    public static int feedCost() {
        return ECConfig.get(Config.mruUsage) * FEED_COST_MULTIPLIER;
    }

    /** 这种精华每 tick 被吃掉的几率（原作 mutateModifier × (稀有度 + 1)） */
    public static double feedChance(EssenceItem essence) {
        return ECConfig.get(Config.mutationChance) * (essence.rarity() + 1);
    }

    /** 一个精华最多加几点（原作 rand.nextInt((稀有度 + 1) × 2) 的上界减 1） */
    public static int maxGain(EssenceItem essence) {
        return 2 * (essence.rarity() + 1) - 1;
    }

    /**
     * 不看几率，这一 tick 能不能喂精华；不能时给出原因（界面状态）。红石停机由调用方先判断。
     *
     * @param storedMRU 机器里的 MRU
     * @param cost      吃一个精华的耗能
     */
    public static Status evaluate(@Nullable IElementalCrystal crystal, ItemStack essence, int storedMRU, int cost) {
        if (crystal == null) {
            return Status.NO_CRYSTAL;
        }
        if (!crystal.isGrowing()) {
            return Status.GROWN;
        }
        if (!(essence.getItem() instanceof EssenceItem item)) {
            return Status.NO_ESSENCE;
        }
        if (storedMRU < cost) {
            return Status.NO_MRU;
        }
        if (crystal.element(item.element()) >= IElementalCrystal.MAX_ELEMENT) {
            return Status.ELEMENT_FULL;
        }
        return Status.WORKING;
    }

    /**
     * 把一个精华喂给水晶：对应元素加 {@code random(2 × (稀有度 + 1))}，夹到 [0, 100]（原作越界整次作废）。
     *
     * @return 实际加上的点数
     */
    public static float feed(IElementalCrystal crystal, EssenceItem essence, RandomSource random) {
        return crystal.mutate(essence.element(), random.nextInt(2 * (essence.rarity() + 1)));
    }

    /** 「定向培育」的门槛（配置没加载时用默认值） */
    public static int bredThreshold() {
        return Config.bredThreshold == null ? DEFAULT_BRED_THRESHOLD : ECConfig.get(Config.bredThreshold);
    }

    /** 喂完以后算不算培育成了：喂的那种元素到了门槛（只看喂的那种，水晶自己随机突变推不到 20） */
    public static boolean isBred(IElementalCrystal crystal, IElementalCrystal.Element fed) {
        return crystal.element(fed) >= bredThreshold();
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
        if (neighborPowered()) {
            status = Status.POWERED;
        } else {
            work(found);
        }
        updateActive(found != null && mru.getMRU() > 0);
    }

    private void work(@Nullable IElementalCrystal found) {
        // D3：空转耗能只在旁边有生长中的水晶时发生（原作不管有没有水晶）
        if (found != null && found.isGrowing()) {
            int usage = ECConfig.get(Config.mruUsage);
            if (level.random.nextDouble() < ECConfig.get(Config.useMRUChance) && mru.getMRU() >= usage) {
                mru.extractMRU(usage, true);
            }
        }
        int cost = feedCost();
        ItemStack essence = inventory.getStackInSlot(SLOT_ESSENCE);
        status = evaluate(found, essence, mru.getMRU(), cost);
        if (status != Status.WORKING) {
            return;
        }
        EssenceItem item = (EssenceItem) essence.getItem();
        if (level.random.nextDouble() < feedChance(item)) {
            feedOne(found, item, level.random, level.players());
        }
    }

    /**
     * 喂一个精华（几率已经判过）：扣 MRU、加元素、吃掉精华。喂完那种元素到了门槛（{@link #isBred}），
     * 给 candidates 里 {@value #GUIDE_RADIUS} 格内的真玩家打「定向培育」的勾。只在服务端、状态是 WORKING 时调。
     */
    private void feedOne(IElementalCrystal found, EssenceItem item, RandomSource random, List<? extends Player> candidates) {
        mru.extractMRU(feedCost(), true);
        feed(found, item, random);
        inventory.extractItem(SLOT_ESSENCE, 1, false);
        snapshot(found);
        if (isBred(found, item.element())) {
            awardNearby(candidates);
        }
    }

    private void awardNearby(List<? extends Player> candidates) {
        double cx = worldPosition.getX() + 0.5D;
        double cy = worldPosition.getY() + 0.5D;
        double cz = worldPosition.getZ() + 0.5D;
        for (Player player : candidates) {
            if (player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer) && player.isAlive()
                    && player.level() == level && player.distanceToSqr(cx, cy, cz) <= GUIDE_RADIUS * GUIDE_RADIUS) {
                PlayerPentacleBlockEntity.award(serverPlayer, GUIDE_BRED, GUIDE_CRITERION);
            }
        }
    }

    /**
     * 不看几率喂一次（GameTest 用；红石停机、旁边的水晶、精华、MRU 照常判）。玩家名单由调用方给：
     * GameTest 造的真玩家不在世界的玩家表里，正常 tick 传的是 {@code level.players()}。
     *
     * @return 喂了没有
     */
    public boolean feedNow(RandomSource random, List<? extends Player> candidates) {
        if (level == null || level.isClientSide || neighborPowered()) {
            return false;
        }
        IElementalCrystal found = crystal.find(level, worldPosition);
        ItemStack essence = inventory.getStackInSlot(SLOT_ESSENCE);
        status = evaluate(found, essence, mru.getMRU(), feedCost());
        if (status != Status.WORKING) {
            return false;
        }
        feedOne(found, (EssenceItem) essence.getItem(), random, candidates);
        return true;
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

    /** 粒子开关变了才发更新包，最快 {@value MRUBlockEntity#SYNC_INTERVAL} tick 一次（MRU 在 0 附近来回跳时不刷包） */
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
        // 原作：有 MRU、旁边有水晶时每 tick 冒两个粒子
        if (!active || level == null) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(PARTICLE,
                    worldPosition.getX() + 0.3D + level.random.nextDouble() / 2D,
                    worldPosition.getY() + 0.3D + i / 2D,
                    worldPosition.getZ() + 0.3D + level.random.nextDouble() / 2D, 0D, 0D, 0D);
        }
    }

    // ------------------------------------------------------------------
    // 访问（界面数值、GameTest）
    // ------------------------------------------------------------------

    public Status getStatus() {
        return status;
    }

    public boolean hasCrystal() {
        return hasCrystal;
    }

    /** 界面显示的大小（0~100，取整） */
    public int displaySize() {
        return hasCrystal ? (int) Mth.clamp(crystalSize, 0F, IElementalCrystal.MAX_SIZE) : 0;
    }

    /** 界面显示的元素值（取整，原作也是 (int)） */
    public int displayElement(IElementalCrystal.Element element) {
        return hasCrystal ? (int) Mth.clamp(crystalElements[element.ordinal()], 0F, IElementalCrystal.MAX_ELEMENT) : 0;
    }

    /** 1 号槽的精华每 tick 被吃掉的几率（百分数），没有精华时 0 */
    public double currentChancePercent() {
        return inventory.getStackInSlot(SLOT_ESSENCE).getItem() instanceof EssenceItem essence ? feedChance(essence) * 100D : 0D;
    }

    // ------------------------------------------------------------------
    // 同步
    // ------------------------------------------------------------------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean(TAG_ACTIVE, active);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 只有客户端收到的更新包里有这个键；存档里没有，服务端每 tick 重算
        active = tag.getBoolean(TAG_ACTIVE);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CrystalControllerMenu(id, inventory, this);
    }
}
