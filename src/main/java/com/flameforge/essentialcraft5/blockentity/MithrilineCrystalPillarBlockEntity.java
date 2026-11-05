package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.block.MithrilineCrystalPillarBlock;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.espe.ESPEAccess;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.espe.SidedESPEHandler;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ESPE 水晶柱（米斯莱 / 淡青 / 虚空 / 恶魔 / 暗渎，方块见 {@link MithrilineCrystalPillarBlock}）的底座：看得见天空时攒 ESPE（每 {@value #SKY_CHECK_INTERVAL} tick 结算一次，折合每 tick 的产量不变），
 * 米斯莱熔炉、风之符文、灵魂五芒星从它这里抽。对应原作 TileMithrilineCrystal。
 * <p>
 * 数值（原作 setupConfig 默认值，五种柱子共用这一份配置，原作的这几个字段也是全类共用的静态值）：折合每 tick 0.025 ESPE，末地 0.1，上限 10000；
 * 柱顶再往上（底座 +3 起）没有挡光的方块才产出。原作五种柱子产得一样快，档位只给五芒星认；这里产速再乘档位倍率
 * {@code tier_multipliers}（默认米斯莱 ×1、淡青 ×1.5、虚空 ×2、恶魔 ×3、暗渎 ×4，{@link #tierMultiplier}），上限不变。能进能出（原作 capability 不分面），谁都能从它这里抽 ESPE。
 * <p>
 * 档位（ESPE 存储的 tier）按所在方块取（{@link MithrilineCrystalPillarBlock#tier()}，原作 new TileMithrilineCrystal(meta / 3)），
 * 不存盘；方块实体类型也按方块取（每种柱子各注册一个类型，见方块类注释）。别的模块注册新柱子时直接用 {@code MithrilineCrystalPillarBlockEntity::new}。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>整个 tick 每 {@value #SKY_CHECK_INTERVAL} 刻才做一次：查天空、把这一秒的产量一次存进去（原作每 tick 调 canBlockSeeSky 并累加）；
 *       各根柱子的结算刻按位置错开。「看得见天空」判定照原作的高度图语义：从柱顶上一格往上，
 *       没有任何挡天空光的方块（{@link #blocksSkyLight}）。玻璃、地毯不挡；树叶、水、普通方块挡，台阶（半砖）、楼梯、耕地、土径这类
 *       不满一格、但底面或顶面铺满的也挡（原作它们的不透光度是 255）。雪层底面铺满，也算挡——这点照 1.20 原版天空光，
 *       原作雪层不挡。不能用 1.20 的 canSeeSky：它看天空光照，末地根本没有天空光，原作「末地 0.1」那档就永远拿不到了。</li>
 *   <li>不同步 ESPE 给客户端（原作每 20 tick 广播整份 NBT）：浮动的水晶只按世界时间转，渲染用不到存量。</li>
 *   <li>ESPE 变化只标记区块要存盘，不通知比较器：自己攒的每秒标记一次，被熔炉抽走时立即标记（所在区块不 tick 也不会丢）。</li>
 * </ul>
 */
public class MithrilineCrystalPillarBlockEntity extends BlockEntity {

    /** 原作 tileentities.mithrilinecrystal */
    public static final class Config {
        public static ForgeConfigSpec.DoubleValue espeGenerated;
        public static ForgeConfigSpec.DoubleValue espeGeneratedEnd;
        public static ForgeConfigSpec.BooleanValue requiresUnobstructedSky;
        public static ForgeConfigSpec.DoubleValue maxESPE;
        public static ForgeConfigSpec.ConfigValue<List<? extends Double>> tierMultipliers;

        /** 档位倍率的默认值（米斯莱、淡青、虚空、恶魔、暗渎） */
        public static final List<Double> DEFAULT_TIER_MULTIPLIERS = List.of(1D, 1.5D, 2D, 3D, 4D);

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("mithriline_crystal_pillar");
            espeGenerated = builder.comment("ESPE made per tick when the crystal can see the sky.",
                            "看得见天空时每 tick 产出的 ESPE")
                    .defineInRange("espe_generated", 0.025D, 0D, 1_000_000D);
            espeGeneratedEnd = builder.comment("ESPE made per tick in the End.", "在末地每 tick 产出的 ESPE")
                    .defineInRange("espe_generated_end", 0.1D, 0D, 1_000_000D);
            requiresUnobstructedSky = builder.comment("Whether nothing may block the light above the crystal.",
                            "柱顶上方是否不能有挡光的方块")
                    .define("requires_unobstructed_sky", true);
            maxESPE = builder.defineInRange("max_espe", 10000D, 1D, 1_000_000_000D);
            tierMultipliers = builder.comment("Generation multiplier per pillar tier, in order: mithriline, pale, void, demonic, shade "
                                    + "(the original made all five equally fast). Missing entries use the last one.",
                            "各档水晶柱产速倍率，依次是米斯莱、淡青、虚空、恶魔、暗渎（原作五种一样快）；少写的档按最后一个算")
                    .defineList("tier_multipliers", DEFAULT_TIER_MULTIPLIERS, o -> o instanceof Number n && n.doubleValue() >= 0D);
            builder.pop();
        }
    }

    /** 两次查天空之间隔多少 tick */
    public static final int SKY_CHECK_INTERVAL = 20;
    public static final String TAG_ESPE = "espe_storage";

    private final ESPEStorage storage;
    @Nullable
    private LazyOptional<IESPEHandler> espeCap;
    private int skyCheckTimer;
    private boolean seesSky;
    /** 正在自己的 tick 里改存量 */
    private boolean ticking;
    /** 自己 tick 里攒的 ESPE 还没标记存盘（每 {@link #SKY_CHECK_INTERVAL} tick 标记一次） */
    private boolean unsaved;

    /** 档位 0~4（原作 meta / 3），构造时按方块定下来 */
    private final int tier;

    /** 方块实体类型和档位都按 state 所在的方块取；所有水晶柱的方块实体类型都用这个构造器（{@code MithrilineCrystalPillarBlockEntity::new}） */
    public MithrilineCrystalPillarBlockEntity(BlockPos pos, BlockState state) {
        super(typeOf(state), pos, state);
        this.tier = Math.max(0, MithrilineCrystalPillarBlock.tierOf(state));
        this.storage = new ESPEStorage(() -> ECConfig.get(Config.maxESPE), tier).onChanged(this::storageChanged);
        // 各根柱子的结算刻按位置错开，一片柱子不会挤在同一个 tick 里
        this.skyCheckTimer = 1 + Math.floorMod(pos.hashCode(), SKY_CHECK_INTERVAL);
    }

    /** 方块给的类型；不是水晶柱（存档里方块被换掉这类异常情况）时退回米斯莱柱的类型，同第二批 */
    private static BlockEntityType<?> typeOf(BlockState state) {
        return state.getBlock() instanceof MithrilineCrystalPillarBlock pillar
                ? pillar.blockEntityType() : ECMithriline.MITHRILINE_CRYSTAL_PILLAR_BE.get();
    }

    /** 档位 0~4（米斯莱 0、淡青 1、虚空 2、恶魔 3、暗渎 4），同 ESPE 存储的 getTier() */
    public int getTier() {
        return tier;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MithrilineCrystalPillarBlockEntity be) {
        be.tick(level);
    }

    private void tick(Level level) {
        if (--skyCheckTimer > 0) {
            return;
        }
        skyCheckTimer = SKY_CHECK_INTERVAL;
        seesSky = !ECConfig.get(Config.requiresUnobstructedSky) || seesSky(level, worldPosition);
        if (seesSky) {
            ticking = true;
            try {
                // 一秒结算一次：每刻的产量 × 间隔刻数，平均速率和逐刻累加一样
                storage.addESPE(generation(level, tier) * SKY_CHECK_INTERVAL, true);
            } finally {
                ticking = false;
            }
        }
        // 自己攒的那一份每秒标记一次存盘（被熔炉抽走时 storageChanged 另会立即标记）
        if (unsaved) {
            unsaved = false;
            level.blockEntityChanged(worldPosition);
        }
    }

    /** 这个世界里每 tick 的基础产量（米斯莱柱；原作按维度 id 1 判断末地） */
    public static double generation(Level level) {
        return ECConfig.get(level.dimension() == Level.END ? Config.espeGeneratedEnd : Config.espeGenerated);
    }

    /** 这一档的柱子在这个世界里每 tick 产多少：基础产量 × 档位倍率 */
    public static double generation(Level level, int tier) {
        return generation(level) * tierMultiplier(tier);
    }

    /** 档位倍率（0 米斯莱 … 4 暗渎）；配置少写了的档按最后一个，配置没加载时用默认值 */
    public static double tierMultiplier(int tier) {
        List<? extends Number> list = Config.tierMultipliers == null ? DEFAULT_LIST : ECConfig.getValue(Config.tierMultipliers);
        if (list == null || list.isEmpty()) {
            return 1D;
        }
        return Math.max(0D, list.get(Math.max(0, Math.min(tier, list.size() - 1))).doubleValue());
    }

    private static final List<Double> DEFAULT_LIST = Config.DEFAULT_TIER_MULTIPLIERS;

    /**
     * 原作 canBlockSeeSky(pos.up(3))：柱顶上一格及以上没有挡光的方块。
     * 1.12 的高度图就是「最高的不透光度 &gt; 0 的方块」，这里从柱顶上一格扫到地表高度图（最高的非空气方块）为止，
     * 每格用 {@link #blocksSkyLight} 判断。露天放的柱子一格都不用扫。
     * 下界：放在基岩屋顶下面扫到基岩就看不见天空；放在屋顶上面照样看得见，和原作一样（原作也只看高度图）。
     */
    public static boolean seesSky(Level level, BlockPos bottom) {
        BlockPos.MutableBlockPos cursor = bottom.above(3).mutable();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, cursor.getX(), cursor.getZ());
        for (int y = cursor.getY(); y < top; y++) {
            cursor.setY(y);
            if (blocksSkyLight(level, cursor, level.getBlockState(cursor))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 这一格挡不挡天空光，照原版 ChunkSkyLightSources.isEdgeOccluded：不透光度 &gt; 0（石头、树叶、水），
     * 或者底面、顶面任一面的光照遮挡形状铺满整面（台阶、楼梯、耕地、土径、雪层——它们的 getLightBlock 是 0，
     * 只看不透光度会漏掉）。玻璃（不遮挡）、地毯（不按形状遮光）的遮挡形状是空的，不挡。
     */
    public static boolean blocksSkyLight(Level level, BlockPos pos, BlockState state) {
        if (state.getLightBlock(level, pos) > 0) {
            return true;
        }
        return Block.isFaceFull(LightEngine.getOcclusionShape(level, pos, state, Direction.DOWN), Direction.DOWN)
                || Block.isFaceFull(LightEngine.getOcclusionShape(level, pos, state, Direction.UP), Direction.UP);
    }

    /** 被熔炉抽走（tick 外的变化）立即标记存盘：所在区块可能已加载但不 tick，等不到下一次标记 */
    private void storageChanged() {
        if (ticking) {
            unsaved = true;
        } else if (level != null && !level.isClientSide) {
            level.blockEntityChanged(worldPosition);
        }
    }

    public ESPEStorage getESPEStorage() {
        return storage;
    }

    // ---- 存盘 ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ESPE, storage.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        storage.deserializeNBT(tag.getCompound(TAG_ESPE));
    }

    // ---- 渲染 ----

    /** 水晶在底座上方 0~1 格之间浮动、高 2 格，最高到底座 +3（原作 getRenderBoundingBox 同） */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition, worldPosition.offset(1, 3, 1));
    }

    // ---- capability ----

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ECCapabilities.ESPE && !isRemoved()) {
            if (espeCap == null) {
                // 原作不分面、能进能出
                espeCap = LazyOptional.of(() -> new SidedESPEHandler(storage, ESPEAccess.BOTH));
            }
            return espeCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        if (espeCap != null) {
            espeCap.invalidate();
            espeCap = null;
        }
    }
}
