package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.util.ECLevels;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import org.jetbrains.annotations.Nullable;

/**
 * 沿面（{@link BuilderShape#FACE}）的测量：从点中的那格起做广度优先，找出和它相连、同一种方块、点中的那一面露在外面的一片，
 * 结果填充时是这些方块那一面外侧的那一层格子，清除、替换时是这层方块本身（{@link Scan#area(boolean)}，tools/plan/builder_upgrade.md §2.4）。纯计算，两端共用：
 * 服务端分几刻做（{@link BuilderJobs}，每刻读的格数有上限），客户端预览一次做完（格数上限更小）。
 * <p>
 * 规则（n 是点中那一面的朝向，「露在外面」= 那一面外侧那格可替换（{@code canBeReplaced}）且不是同一种方块）：
 * <ul>
 *   <li>起点：点中的那格不是空气、不是液体，那一面露在外面；不满足就整个不算（{@link #invalid}）。</li>
 *   <li>从一格 p 往和 n 垂直的四个方向 d 看 q = p + d：同一种方块且那一面露在外面 → 连上；
 *       q 是同一种方块但那一面被盖住、或 q 是别的实心方块 → 看上一级台阶 q + n；q 是可替换的空格 → 看下一级台阶 q − n。
 *       台阶那格同样要是同一种方块、那一面露在外面才连上。这样顺着地形一级级的台阶也能连成一片，
 *       被盖住的面（上面压着别的方块）不算、也不从它往外连。</li>
 *   <li>不同种方块不连（按方块种类比，不比状态：朝向不同的同种台阶、原木算一种）。</li>
 *   <li>范围：每个轴上离起点不超过 {@link #radius} 格（外框边长不超过档位的边长上限），找到的格数不超过 limit；
 *       到了 limit 还有能连的就停下、记 {@link #capped}，保留的是广度优先先找到的、离起点近的那些。</li>
 *   <li>读世界只经 {@link Reader}：没加载的区块、出了建筑高度或世界边界的格子不读（当连不上），不会为测量加载区块；
 *       外侧那格出了世界边界、建筑高度的方块也就不算。</li>
 * </ul>
 * 每扫一个点最多读 {@value #MAX_READS_PER_NODE} 格（读过的格子记下来、不重读），{@link Scan#step} 按读的格数算预算。
 */
public final class BuilderSurface {

    /** 扫一个点最多读几格：四个方向各「那一格 + 它外侧那格 + 台阶外侧那格」 */
    public static final int MAX_READS_PER_NODE = 12;

    private BuilderSurface() {
    }

    /** 读一格；返回 null = 这格不读（没加载、出了建筑高度或世界边界），当连不上 */
    @FunctionalInterface
    public interface Reader {
        @Nullable
        BlockState read(BlockPos pos);
    }

    /** 读真实世界：只读已加载区块里、建筑高度和世界边界以内的格子（不加载区块）；两端都用这个 */
    public static Reader reader(Level level) {
        WorldBorder border = level.getWorldBorder();
        return pos -> level.isOutsideBuildHeight(pos) || !border.isWithinBounds(pos) || !ECLevels.isFullyLoaded(level, pos)
                ? null : level.getBlockState(pos);
    }

    /** 离起点每个轴最多几格：外框边长 2r + 1 不超过 maxSide */
    public static int radius(int maxSide) {
        return Math.max(0, (maxSide - 1) / 2);
    }

    /** 那一面算「露在外面」：外侧那格可替换，且不是同一种方块 */
    public static boolean open(BlockState outer, Block block) {
        return outer.canBeReplaced() && !outer.is(block);
    }

    /** 一次做完（GameTest、客户端预览用） */
    public static Scan scanAll(Reader reader, BlockPos start, Direction face, int limit, int radius) {
        Scan scan = new Scan(start, face, limit, radius);
        scan.step(reader, Integer.MAX_VALUE);
        return scan;
    }

    /** 一次测量的状态：可以分几次 {@link #step}，每次按读的格数限量 */
    public static final class Scan {
        private final BlockPos start;
        private final Direction face;
        private final int limit;
        private final int radius;
        @Nullable
        private Block block;
        /** 读过的格子；值为 null = 读不了 */
        private final Long2ObjectOpenHashMap<BlockState> seen = new Long2ObjectOpenHashMap<>();
        private final LongOpenHashSet queued = new LongOpenHashSet();
        private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        /** 连上的方块（按找到的先后） */
        private final LongArrayList found = new LongArrayList();
        private final Direction[] around;
        private int reads;
        private boolean begun;
        private boolean done;
        private boolean invalid;
        private boolean capped;

        public Scan(BlockPos start, Direction face, int limit, int radius) {
            this.start = start.immutable();
            this.face = face;
            this.limit = Math.max(1, limit);
            this.radius = Math.max(0, radius);
            Direction[] dirs = new Direction[4];
            int n = 0;
            for (Direction d : Direction.values()) {
                if (d.getAxis() != face.getAxis()) {
                    dirs[n++] = d;
                }
            }
            this.around = dirs;
        }

        /**
         * 接着扫，这一次最多读 budget 格（至少扫一个点，所以 budget 小于 {@value #MAX_READS_PER_NODE} 时也会往前走）；扫完返回 true。
         */
        public boolean step(Reader reader, int budget) {
            if (done) {
                return true;
            }
            int before = reads;
            if (!begun) {
                begun = true;
                BlockState state = read(reader, start);
                if (state == null || state.isAir() || state.liquid()) {
                    return finish(true);
                }
                block = state.getBlock();
                BlockState outer = read(reader, start.relative(face));
                if (outer == null || !open(outer, block)) {
                    return finish(true);
                }
                enqueue(start);
            }
            while (!queue.isEmpty()) {
                if (reads > before && (long) reads - before + MAX_READS_PER_NODE > budget) {
                    return false;
                }
                expand(reader, BlockPos.of(queue.dequeueLong()));
                if (capped) {
                    break;
                }
            }
            return finish(false);
        }

        private boolean finish(boolean bad) {
            done = true;
            invalid = bad;
            queue.clear();
            return true;
        }

        private void expand(Reader reader, BlockPos p) {
            for (Direction d : around) {
                BlockPos q = p.relative(d);
                if (!inRange(q) || queued.contains(q.asLong())) {
                    continue;
                }
                BlockState qs = read(reader, q);
                if (qs == null) {
                    continue;
                }
                if (qs.is(block)) {
                    BlockState qo = read(reader, q.relative(face));
                    if (qo != null && open(qo, block)) {
                        enqueue(q);
                        if (capped) {
                            return;
                        }
                        continue;
                    }
                    candidate(reader, q.relative(face));            // 同种但被盖住：看上一级
                } else if (open(qs, block)) {
                    candidate(reader, q.relative(face.getOpposite()));   // 空着：看下一级
                } else {
                    candidate(reader, q.relative(face));            // 别的实心方块：看上一级
                }
                if (capped) {
                    return;
                }
            }
        }

        /** 台阶那一格：同一种方块、那一面露在外面才连上 */
        private void candidate(Reader reader, BlockPos c) {
            if (!inRange(c) || queued.contains(c.asLong())) {
                return;
            }
            BlockState cs = read(reader, c);
            if (cs == null || !cs.is(block)) {
                return;
            }
            BlockState co = read(reader, c.relative(face));
            if (co != null && open(co, block)) {
                enqueue(c);
            }
        }

        private void enqueue(BlockPos pos) {
            long key = pos.asLong();
            if (queued.contains(key)) {
                return;
            }
            if (found.size() >= limit) {
                capped = true;
                return;
            }
            queued.add(key);
            queue.enqueue(key);
            found.add(key);
        }

        private boolean inRange(BlockPos pos) {
            return Math.abs(pos.getX() - start.getX()) <= radius && Math.abs(pos.getY() - start.getY()) <= radius
                    && Math.abs(pos.getZ() - start.getZ()) <= radius;
        }

        @Nullable
        private BlockState read(Reader reader, BlockPos pos) {
            long key = pos.asLong();
            if (seen.containsKey(key)) {
                return seen.get(key);
            }
            reads++;
            BlockState state = reader.read(pos);
            seen.put(key, state);
            return state;
        }

        public boolean done() {
            return done;
        }

        /** 点中的那格不能沿着铺（空气、液体、那一面被盖住、读不了） */
        public boolean invalid() {
            return invalid;
        }

        /** 到了格数上限还有能连的（结果只保留了前 limit 格） */
        public boolean capped() {
            return capped;
        }

        /** 到现在连上了几格 */
        public int found() {
            return found.size();
        }

        /** 到现在一共读了几格 */
        public int reads() {
            return reads;
        }

        public int limit() {
            return limit;
        }

        public BlockPos start() {
            return start;
        }

        public Direction face() {
            return face;
        }

        /** 沿着的那种方块；还没开始或起点不对时为 null */
        @Nullable
        public Block block() {
            return block;
        }

        /** 填充的结果：连上的每格那一面外侧那格，按 §1.3 排好序（锚点是起点外侧那格）；没扫完时是到现在的部分 */
        public BuilderArea area() {
            return area(false);
        }

        /**
         * 结果。inPlace 为 false（填充）是连上的每格那一面外侧那一层，锚点是起点外侧那格；
         * inPlace 为 true（清除、替换这类作用于已有方块的模式，{@link BuilderMode#inPlace}）是连上的那层方块本身（剥掉 / 换掉那一层），锚点是起点。
         * 连不连、上限、截断都和模式无关，两种结果格数一样。按 §1.3 排好序；没扫完时是到现在的部分。
         */
        public BuilderArea area(boolean inPlace) {
            long[] cells = new long[found.size()];
            for (int i = 0; i < cells.length; i++) {
                cells[i] = inPlace ? found.getLong(i) : BlockPos.of(found.getLong(i)).relative(face).asLong();
            }
            return BuilderShapes.ordered(inPlace ? start : start.relative(face), cells);
        }
    }
}
