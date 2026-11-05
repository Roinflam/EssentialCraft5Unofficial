package com.flameforge.essentialcraft5.worldgen.hoanna.feature;

import com.flameforge.essentialcraft5.registry.ECHoannaNature;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 地脉喷口（第十三批 13d-H1b，HS §2.1.3 {@code hoanna_ley_vent} / {@code hoanna_ley_vent_sparse}）：地表一格换成喷口（{@code charge = 2}，
 * 世界生成时半满，玩家第一次路过就能收一次），四周 3 × 3 的地面换成焦土（粗泥 / 恐惧泥土按 2 : 1），喷口和焦土上面的草花清掉。
 * 写 ≤ 18 格（喷口 1 + 焦土 8 + 上面的草花 9）。
 * <p>
 * 放置：混沌腐化之地 rarity 5、其余五个陆地群系 rarity 24，in_square → heightmap WORLD_SURFACE_WG → biome。高度图的那一格可能是草丛顶上，
 * 这里从它往下 6 格内找地面；找不到（水面、树冠上、悬崖边空着）就不放。地面是水下的也不放（{@link FeatureSupport#groundY} 碰到液体就停）。
 * 喷口只换掉「地表那一层」的方块：草方块、泥土类、沙、恐惧泥土、石头这几种，别的（别人放的方块、矿石）不换。
 */
public class LeyVentFeature extends Feature<NoneFeatureConfiguration> {

    /** 世界生成时喷口的充能 */
    public static final int GENERATED_CHARGE = 2;
    /** 从高度图那一格往下找地面的格数 */
    public static final int GROUND_SEARCH = 6;
    /** 焦土哈希的盐 */
    private static final long SCORCH_SALT = 0x3C6EF372FE94F82BL;

    public LeyVentFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        BoundingBox box = FeatureSupport.writableBox(level, origin);
        int ground = FeatureSupport.groundY(level, origin.getX(), origin.getZ(), origin.getY(), origin.getY() - GROUND_SEARCH);
        if (ground == Integer.MIN_VALUE) {
            return false;
        }
        return placeVent(level, box, new BlockPos(origin.getX(), ground, origin.getZ()), GENERATED_CHARGE, level.getSeed());
    }

    /**
     * 在地面这一格放喷口（换掉它）、四周 8 格地面换成焦土。ground 那一格要是能换的地表方块（或者已经是喷口：分块放时别的区块先放过了），否则不放。
     * 焦土选粗泥还是恐惧泥土按格子坐标哈希（不用顺序随机）：同一个喷口跨区块分几次放，每一格的结果都一样（中景观混沌裂隙靠这个）。
     *
     * @param seed 哈希种子（世界种子派生）
     */
    public static boolean placeVent(WorldGenLevel level, BoundingBox box, BlockPos ground, int charge, long seed) {
        BlockState current = level.getBlockState(ground);
        if (!isSurfaceSoil(current) && !current.is(HoannaFeatureBlocks.leyVent(charge).getBlock())) {
            return false;
        }
        if (box.isInside(ground)) {
            FeatureSupport.set(level, box, ground, HoannaFeatureBlocks.leyVent(charge));
            FeatureSupport.clearAbove(level, box, ground);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                pos.set(ground.getX() + dx, ground.getY(), ground.getZ() + dz);
                if (!box.isInside(pos) || !isSurfaceSoil(level.getBlockState(pos)) || !FeatureSupport.isClearable(level.getBlockState(pos.above()))) {
                    continue;
                }
                boolean coarse = FeatureSupport.intOf(HoannaStructurePiece.posHash(pos.getX(), pos.getY(), pos.getZ(), seed ^ SCORCH_SALT), 3) < 2;
                BlockState scorched = coarse ? Blocks.COARSE_DIRT.defaultBlockState() : ECHoannaNature.DREAD_DIRT.get().defaultBlockState();
                FeatureSupport.set(level, box, pos.immutable(), scorched);
                FeatureSupport.clearAbove(level, box, pos.immutable());
            }
        }
        return true;
    }

    /** 喷口 / 焦土能换掉的地表方块：泥土类（草方块、泥土、粗泥、灰化土……）、恐惧泥土、沙、沙砾、石头 */
    public static boolean isSurfaceSoil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.STONE)
                || state.is(Blocks.SNOW_BLOCK) || state.is(ECHoannaNature.DREAD_DIRT.get());
    }
}
