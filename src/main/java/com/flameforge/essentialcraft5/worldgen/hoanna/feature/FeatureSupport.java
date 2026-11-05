package com.flameforge.essentialcraft5.worldgen.hoanna.feature;

import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.function.Predicate;

/**
 * 第十三批 13d-H1b 各个 Java 地物共用的几件事（batch13.md §6.1、§7.2 第 4 条）：
 * <ul>
 *   <li><b>写方块范围</b>：{@link #writableBox} = 起点区块 ± 1 个区块（世界生成时 {@code WorldGenRegion} 能写的就是这 3 × 3 区块，
 *       写到外面原版只打一条错误、在开发环境里还会停住）。地物自己还守着「起点在区块内、半径 ≤ 12」，这个框是兜底；
 *       在完整世界里（GameTest、{@code /place}）同样按起点区块 ± 1 算，行为和生成时一致。</li>
 *   <li><b>找地面</b>：{@link #groundY} 从给定高度往下找第一格「地面」（实心、不含液体，跳过空气、草花、雪层、树叶），
 *       不读高度图：生成中的区块和完整区块用的高度图种类不一样，按方块找两边一样。</li>
 *   <li><b>能不能占</b>：{@link #isClearable}（空气、可替换的草花、雪层）——地物只往这些格子里放东西，不盖掉别人放的方块。</li>
 * </ul>
 */
public final class FeatureSupport {

    /** 写方块用的 flag：只通知客户端（同原版地物的 2），世界生成里没有邻居更新 */
    public static final int FLAGS = Block.UPDATE_CLIENTS;

    private FeatureSupport() {
    }

    /** 起点所在区块 ± 1 个区块的范围（整个世界高度） */
    public static BoundingBox writableBox(WorldGenLevel level, BlockPos origin) {
        ChunkPos chunk = new ChunkPos(origin);
        return new BoundingBox(chunk.getMinBlockX() - 16, level.getMinBuildHeight(), chunk.getMinBlockZ() - 16,
                chunk.getMaxBlockX() + 16, level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ() + 16);
    }

    /** 在 box 里才写；返回写没写 */
    public static boolean set(WorldGenLevel level, BoundingBox box, BlockPos pos, BlockState state) {
        if (!box.isInside(pos)) {
            return false;
        }
        boolean placed = level.setBlock(pos, state, FLAGS);
        if (placed && HoannaStructurePiece.needsShapeUpdate(state)) {
            HoannaStructurePiece.requestShapeUpdate(level, pos);
        }
        return placed;
    }

    /** 地面：有碰撞的实心方块、不含液体，不是树叶 / 原木以外的植物、不是雪层 */
    @SuppressWarnings("deprecation")
    public static boolean isGround(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)
                && !state.is(Blocks.SNOW) && state.isSolid();
    }

    /** 能被地物占掉的格子：空气、可替换且不含液体的（草、花、枯灌木、雪层） */
    public static boolean isClearable(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty()) || state.is(Blocks.SNOW);
    }

    /**
     * 从 fromY 往下找到 minY（含），第一格 {@link #isGround} 的 y；找不到（一路都是空气 / 植物，或者先碰到了液体）返回 {@link Integer#MIN_VALUE}。
     * 碰到液体就停：地物不放在水下、也不放在水面上。
     */
    public static int groundY(LevelReader level, int x, int z, int fromY, int minY) {
        return groundY(level, x, z, fromY, minY, state -> false);
    }

    /** 同 {@link #groundY(LevelReader, int, int, int, int)}，skip 认得的方块当空气跳过（地物找地面时跳过自己放过的东西） */
    public static int groundY(LevelReader level, int x, int z, int fromY, int minY, Predicate<BlockState> skip) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, fromY, z);
        for (int y = fromY; y >= minY; y--) {
            BlockState state = level.getBlockState(pos.setY(y));
            if (!state.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (isGround(state) && !skip.test(state)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** 同 {@link #groundY}，但遇到液体不停（给海底用：跳过水找海床） */
    public static int floorY(LevelReader level, int x, int z, int fromY, int minY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, fromY, z);
        for (int y = fromY; y >= minY; y--) {
            if (isGround(level.getBlockState(pos.setY(y)))) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** 上面一格是草花 / 雪层就清成空气（双格高的草连上半截一起清）；只清 box 里的 */
    public static void clearAbove(WorldGenLevel level, BoundingBox box, BlockPos ground) {
        BlockPos above = ground.above();
        BlockState state = level.getBlockState(above);
        if (state.isAir() || !isClearable(state)) {
            return;
        }
        set(level, box, above, Blocks.AIR.defaultBlockState());
        BlockPos top = above.above();
        if (level.getBlockState(top).getBlock() == state.getBlock()) {
            set(level, box, top, Blocks.AIR.defaultBlockState());
        }
    }

    /** 哈希 → [0, n) 的整数（{@link HoannaStructurePiece#unit} 的整数版） */
    public static int intOf(long hash, int n) {
        return Math.min(n - 1, (int) (HoannaStructurePiece.unit(hash) * n));
    }
}
