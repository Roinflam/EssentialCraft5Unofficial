package com.flameforge.essentialcraft5.ley;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * 地脉探测仪的扫描函数（第十二批，batch12.md §4.3.3）：在以 center 为中心、边长 2r+1 的立方体里找符合条件的方块，按离中心的距离留最近的 max 个。
 * <p>
 * 客户端用游标版 {@link Cursor}：每个客户端刻只查一段（{@code client/ley/LeyScanClient} 每刻 4 096 格，半径 16 的 35 937 格 9 刻扫完）；
 * GameTest 在服务端世界上直接调整版 {@link #collect}，两边是同一份代码。
 * <p>
 * 只看已经在内存里的区块：世界是 {@link LevelAccessor} 时按整列问 {@code getChunkSource().hasChunk}，没有的那一列整列跳过、不触发加载。
 * 注意 {@code ClientLevel.hasChunk} 恒为 true，所以要问区块源；客户端缺的区块读出来本来也是空气，这里跳过只是省掉无用的读。
 * 遍历顺序：x → z → y（同一列连着查，每列只问一次区块）。
 */
public final class LeyScan {

    private LeyScan() {
    }

    /**
     * 一次扫完（服务端 GameTest 用；客户端分刻扫用 {@link Cursor}）。
     *
     * @return 按到 center 的距离从近到远排好的位置，最多 max 个
     */
    public static List<BlockPos> collect(BlockGetter level, BlockPos center, int radius, Predicate<BlockState> match, int max) {
        Cursor cursor = new Cursor(center, radius, max, match);
        cursor.step(level, Integer.MAX_VALUE);
        List<BlockPos> positions = new ArrayList<>();
        for (Hit hit : cursor.results()) {
            positions.add(hit.pos());
        }
        return positions;
    }

    /**
     * 扫到的一格。
     *
     * @param pos    位置
     * @param state  扫到时的方块状态（客户端按它挑颜色）
     * @param distSq 到中心格的距离平方（按格子坐标算）
     */
    public record Hit(BlockPos pos, BlockState state, int distSq) {
    }

    /** 近的在前；一样近按 x、y、z 排，结果和遍历顺序无关 */
    private static final Comparator<Hit> NEAREST_FIRST = Comparator.comparingInt(Hit::distSq)
            .thenComparingInt(hit -> hit.pos().getX())
            .thenComparingInt(hit -> hit.pos().getY())
            .thenComparingInt(hit -> hit.pos().getZ());

    /**
     * 分段扫描的游标。每次 {@link #step} 最多查 budget 格，扫完后 {@link #results()} 给出最近的 max 格。
     * 一直只保留 max 格（远的先出堆），内存和半径无关。
     */
    public static final class Cursor {
        private final BlockPos center;
        private final int radius;
        private final int side;
        private final long total;
        private final int max;
        private final Predicate<BlockState> match;
        /** 远的在堆顶，满了就把最远的挤出去 */
        private final PriorityQueue<Hit> heap;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private long index;
        private long column = -1;
        private boolean columnLoaded;

        public Cursor(BlockPos center, int radius, int max, Predicate<BlockState> match) {
            this.center = center.immutable();
            this.radius = Math.max(0, radius);
            this.side = 2 * this.radius + 1;
            this.total = (long) side * side * side;
            this.max = Math.max(1, max);
            this.match = match;
            this.heap = new PriorityQueue<>(this.max + 1, NEAREST_FIRST.reversed());
        }

        public BlockPos center() {
            return center;
        }

        public boolean done() {
            return index >= total;
        }

        /**
         * 往下查最多 budget 格（跳过的未加载列、建筑高度以外的格子也算进 budget，开销按格子数封顶）。
         *
         * @return 这次实际走过几格
         */
        public int step(BlockGetter level, int budget) {
            int walked = 0;
            while (index < total && walked < budget) {
                long col = index / side;
                int dy = (int) (index % side) - radius;
                int dx = (int) (col / side) - radius;
                int dz = (int) (col % side) - radius;
                index++;
                walked++;
                int x = center.getX() + dx;
                int y = center.getY() + dy;
                int z = center.getZ() + dz;
                if (col != column) {
                    column = col;
                    columnLoaded = !(level instanceof LevelAccessor accessor) || accessor.getChunkSource().hasChunk(x >> 4, z >> 4);
                }
                if (!columnLoaded || level.isOutsideBuildHeight(y)) {
                    continue;
                }
                BlockState state = level.getBlockState(cursor.set(x, y, z));
                if (state.isAir() || !match.test(state)) {
                    continue;
                }
                heap.add(new Hit(cursor.immutable(), state, dx * dx + dy * dy + dz * dz));
                if (heap.size() > max) {
                    heap.poll();
                }
            }
            return walked;
        }

        /** 到现在为止留下的格子，从近到远（没扫完时是部分结果） */
        public List<Hit> results() {
            List<Hit> hits = new ArrayList<>(heap);
            hits.sort(NEAREST_FIRST);
            return hits;
        }
    }
}
