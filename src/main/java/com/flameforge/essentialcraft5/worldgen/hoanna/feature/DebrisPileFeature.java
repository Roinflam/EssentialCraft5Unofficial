package com.flameforge.essentialcraft5.worldgen.hoanna.feature;

import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

/**
 * 残骸堆（第十三批 13d-H1b，HS §2.1.3 {@code ruin_debris} / {@code frozen_debris} / {@code sunken_debris}）：一小堆城里塌下来的东西——
 * 强化石、混凝土、一两根虚空栅栏、偶尔一盏魔力光源、更少见的一个悬浮器（都是已有方块，虚空那几种按注册名取，见 {@link HoannaStructureBlocks}）。
 * 半径 2~3 的一片，中间最多堆 2 层，写 ≈ 30 格。
 * <ul>
 *   <li>{@code ruin}（五个陆地群系）：放在地面上；地面是沙（沙漠）时整堆往下沉一格，算「半埋」；</li>
 *   <li>{@code frozen}（冰冻腐化之地）：同上，一部分混凝土换成浮冰，堆顶盖雪层；</li>
 *   <li>{@code sunken}（海纳尔之海）：放在海床上、只换掉水，栅栏带水（含水），不放光源（光源在水里会挤出一格空的）。</li>
 * </ul>
 * 陆地的两种碰到水面就不放；海底那种只在水下放。
 */
public class DebrisPileFeature extends Feature<DebrisPileFeature.Config> {

    /** 从高度图那一格往下找地面（海床）的格数 */
    public static final int GROUND_SEARCH = 8;
    /** 一堆里放魔力光源 / 悬浮器的几率 */
    public static final float LIGHT_CHANCE = 0.3F;
    public static final float LEVITATOR_CHANCE = 0.12F;

    public enum Style implements StringRepresentable {
        RUIN("ruin"), FROZEN("frozen"), SUNKEN("sunken");

        public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);
        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** {"style": "ruin" | "frozen" | "sunken"} */
    public record Config(Style style) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = Style.CODEC.fieldOf("style").xmap(Config::new, Config::style).codec();
    }

    public DebrisPileFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        return placePile(context.level(), context.origin(), context.config().style(), context.random());
    }

    /** 以 origin 那一列为中心放一堆（origin 的 y 是高度图给的，往下找地面）；放了返回 true */
    public static boolean placePile(WorldGenLevel level, BlockPos origin, Style style, RandomSource random) {
        BoundingBox box = FeatureSupport.writableBox(level, origin);
        boolean sunken = style == Style.SUNKEN;
        int ground = sunken
                ? FeatureSupport.floorY(level, origin.getX(), origin.getZ(), origin.getY(), origin.getY() - GROUND_SEARCH)
                : FeatureSupport.groundY(level, origin.getX(), origin.getZ(), origin.getY(), origin.getY() - GROUND_SEARCH);
        if (ground == Integer.MIN_VALUE) {
            return false;
        }
        BlockPos center = new BlockPos(origin.getX(), ground, origin.getZ());
        boolean centerWet = !level.getFluidState(center.above()).isEmpty();
        if (sunken != centerWet) {
            return false;
        }
        int sink = !sunken && level.getBlockState(center).is(BlockTags.SAND) ? 1 : 0;
        int radius = 2 + random.nextInt(2);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > radius * radius || random.nextFloat() < 0.35F + 0.1F * d2 / (radius * radius)) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int columnGround = sunken ? FeatureSupport.floorY(level, x, z, ground + 2, ground - 3)
                        : FeatureSupport.groundY(level, x, z, ground + 2, ground - 3);
                if (columnGround == Integer.MIN_VALUE) {
                    continue;
                }
                int layers = d2 <= 1 ? 1 + random.nextInt(2) : 1;
                for (int i = 0; i < layers; i++) {
                    pos.set(x, columnGround + 1 + i - sink, z);
                    boolean buried = sink > 0 && i == 0 && level.getBlockState(pos).is(BlockTags.SAND);
                    if (buried ? FeatureSupport.set(level, box, pos.immutable(), rubble(style, random)) : place(level, box, pos, rubble(style, random), sunken)) {
                        placed++;
                    }
                }
            }
        }
        // 一两根虚空栅栏立在堆上（1~2 格高）
        int posts = 1 + random.nextInt(2);
        for (int i = 0; i < posts; i++) {
            int x = center.getX() + random.nextInt(3) - 1;
            int z = center.getZ() + random.nextInt(3) - 1;
            int top = topOfPile(level, x, z, ground + 3, ground - 3, sunken);
            if (top == Integer.MIN_VALUE) {
                continue;
            }
            int height = 1 + random.nextInt(2);
            for (int dy = 1; dy <= height; dy++) {
                BlockState fence = HoannaStructureBlocks.voidFence();
                if (sunken) {
                    fence = HoannaFeatureBlocks.with(fence, BlockStateProperties.WATERLOGGED, true);
                }
                if (place(level, box, pos.set(x, top + dy, z), fence, sunken)) {
                    placed++;
                }
            }
        }
        if (!sunken && random.nextFloat() < LIGHT_CHANCE) {
            int top = topOfPile(level, center.getX(), center.getZ(), ground + 4, ground - 3, false);
            if (top != Integer.MIN_VALUE && place(level, box, pos.set(center.getX(), top + 1, center.getZ()),
                    ECEssence.MAGIC_LIGHT.get().defaultBlockState(), false)) {
                placed++;
            }
        }
        if (random.nextFloat() < LEVITATOR_CHANCE) {
            int x = center.getX() + random.nextInt(5) - 2;
            int z = center.getZ() + random.nextInt(5) - 2;
            int top = topOfPile(level, x, z, ground + 3, ground - 3, sunken);
            BlockState levitator = HoannaStructureBlocks.levitator();
            if (top != Integer.MIN_VALUE && !levitator.isAir() && place(level, box, pos.set(x, top + 1, z), levitator, sunken)) {
                placed++;
            }
        }
        if (style == Style.FROZEN) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int top = topOfPile(level, center.getX() + dx, center.getZ() + dz, ground + 4, ground - 3, false);
                    if (top != Integer.MIN_VALUE && random.nextFloat() < 0.7F) {
                        pos.set(center.getX() + dx, top + 1, center.getZ() + dz);
                        BlockState snow = Blocks.SNOW.defaultBlockState();
                        if (snow.canSurvive(level, pos) && place(level, box, pos, snow, false)) {
                            // 同原版 freeze_top_layer：雪下面的草方块变成「盖雪」的样子
                            BlockState below = level.getBlockState(pos.below());
                            if (below.hasProperty(BlockStateProperties.SNOWY) && !below.getValue(BlockStateProperties.SNOWY)) {
                                FeatureSupport.set(level, box, pos.below(), below.setValue(BlockStateProperties.SNOWY, true));
                            }
                        }
                    }
                }
            }
        }
        return placed > 0;
    }

    /** 碎块：强化石 / 混凝土（冰冻的三成换浮冰） */
    private static BlockState rubble(Style style, RandomSource random) {
        float roll = random.nextFloat();
        if (style == Style.FROZEN && roll < 0.3F) {
            return Blocks.PACKED_ICE.defaultBlockState();
        }
        return roll < 0.55F ? ECMaterials.FORTIFIED_STONE.get().defaultBlockState() : HoannaStructureBlocks.concrete();
    }

    /** 能放就放：陆地上只占能占的格子，海底只占水 */
    private static boolean place(WorldGenLevel level, BoundingBox box, BlockPos pos, BlockState state, boolean sunken) {
        BlockState current = level.getBlockState(pos);
        boolean free = sunken ? current.is(Blocks.WATER) && current.getFluidState().is(Fluids.WATER) && current.getFluidState().isSource()
                : FeatureSupport.isClearable(current);
        return free && FeatureSupport.set(level, box, pos.immutable(), state);
    }

    /** 这一列最上面的实心方块（堆顶或地面） */
    private static int topOfPile(WorldGenLevel level, int x, int z, int fromY, int minY, boolean sunken) {
        return sunken ? FeatureSupport.floorY(level, x, z, fromY, minY) : FeatureSupport.groundY(level, x, z, fromY, minY);
    }
}
