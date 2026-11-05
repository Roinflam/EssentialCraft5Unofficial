package com.flameforge.essentialcraft5.enrichment;

import com.flameforge.essentialcraft5.structure.StructureResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 空心长方体外壳的扫描（MRUCU 富集仓，ECEnrichment 模块自己的，框架的 StructurePattern 只能判定固定形状）。
 * 对应原作 TileMRUCUECController.checkStructure。
 *
 * <h2>规则</h2>
 * 外壳是一个每边 {@code minSize}~{@code maxSize} 格的长方体的六个面，每一格都要是外壳方块（{@code casing}），
 * 内部不管（原作也不查）。起点（控制器）必须在外壳上。
 *
 * <h2>找长方体</h2>
 * 原作从控制器沿 x、z 走到头，再从四个角往上下走，是个没有上限的 while 循环，1.12 里沿途还会加载区块；
 * 控制器不在底层那条棱上时它经常找不到结构。这里：
 * <ol>
 *   <li>从起点沿三个轴各往两边走（每边最多 maxSize 步），得到过起点的三条外壳线。</li>
 *   <li>起点所在的那个面：依次假设面的法向是 y、x、z（顶/底面优先），另两个轴的外壳线就是这个面的长和宽
 *       （外壳的面是实心的，过起点的线一定贯穿整个面）。长、宽不在范围内的跳过。</li>
 *   <li>往法向的两边各试一次：四个角往这边走的最短长度是棱长上限，从 minSize-1 格开始往外找第一层
 *       「整个截面都是外壳方块」的平面，那就是对面；先查截面上紧挨着角的内侧那格，空心的层一下就能排除。</li>
 *   <li>收边（{@link #trimPadding}）：仓搭在外壳方块铺的地板上、或者贴着一堵外壳方块墙，而控制器在侧墙上时，
 *       过起点的线会穿过墙脚一直走进地板（墙），面的这一端就多出了垫着的那几层，仓底成了「内部」，嵌在仓底的存储方块、部件都不算。
 *       所以对起点所在面的两个轴（法向不动）的四个端面，从外往里数连续的「整层都是外壳方块」的层数 n：
 *       n ≥ 2、而且第 n+1 层确定不是整层时，最里面那层整层才是仓的面，把这一端往里收 n-1 层
 *       （收完每边仍不少于 minSize、仍包含起点才收）。实心的仓每层都是整层，不收。</li>
 *   <li>对这个长方体逐格核对外壳（按 y、z、x 的顺序，即最下层最北最西的格子先查）。第一个核对通过的就是结果。</li>
 * </ol>
 * 所以仓整个坐在一片外壳方块地板上、或者一整面贴着外壳方块墙（控制器在顶面或侧墙都行），都能正确找到。
 * 地板、墙只垫了仓的一部分时，控制器在侧墙上可能不行：过起点的线走进了垫层，垫层那一端的角却走不到对面（原来就这样，收边管不到）。
 * 做不到的是：
 * <ul>
 *   <li>控制器所在的那一面和外面的外壳方块在同一平面上连成一片（比如控制器嵌在和地板齐平的底面里），
 *       这时找不出面的边界，判为「找不到外壳」。手册里写了「控制器放在侧墙上最稳」。</li>
 *   <li>内部虽然不查，但内部的外壳方块会干扰找面：从起点所在的线一路连到对面墙的一排、内部整个填满、
 *       或者贴着某一面整层铺满，都可能让扫描把仓判小（另一半墙上的部件不算）。原作沿棱走再核对，不受内部影响。
 *       手册 controller.3 / controller.4 写了「内部别放外壳方块」。</li>
 * </ul>
 *
 * <h2>三态</h2>
 * 只读已完全加载的区块（直接取 LevelChunk，不走 Level.getBlockState，那会在主线程上等区块加载）；超出世界高度的格子按空气算。
 * <ul>
 *   <li>某个候选长方体核对通过 → {@link StructureResult#MATCH}；</li>
 *   <li>否则只要找长方体或核对时碰到没加载的区块 → {@link StructureResult#UNKNOWN}（调用方保留上次的结果）；</li>
 *   <li>否则 → {@link StructureResult#MISMATCH}，并给出最有用的一条原因（缺块 / 被拒 &gt; 尺寸 &gt; 找不到外壳）。</li>
 * </ul>
 * 代价：一次完整扫描读 外壳面积 + 若干条线 个方块，32×32×32 的外壳约 6 千格；收边对空心的仓每个端面只多读几格
 * （先查第 2 层，碰到内部的空气就停；垫了地板的一端多读地板和仓底两层截面），内部整个填满外壳方块时每个端面最多多读 边长 - minSize + 2 层截面（32 格的仓约 31 层）。
 * 调用方要节流（富集仓控制器：标脏后最快 20 tick 一次，平时 200 tick 一次）。
 */
public final class CuboidShellScanner {

    /** 判不成形的原因 */
    public enum Failure {
        /** 找不到一个以起点所在面为一面的长方体（起点不在外壳上，或者面和外面的外壳方块连成一片） */
        NO_SHELL,
        /** 找到了外壳的面，但某条边超出 minSize~maxSize */
        SIZE,
        /** 外壳上有一格不是外壳方块 */
        GAP,
        /** 外壳上的某格被 {@link Visitor} 拒绝（富集仓：第二个控制器） */
        REJECTED
    }

    /**
     * @param box        成形时的长方体（含外壳），否则为 null
     * @param failure    MISMATCH 时的原因
     * @param failurePos GAP / REJECTED 时出问题的格子
     */
    public record Result(StructureResult status, @Nullable BoundingBox box, @Nullable Failure failure, @Nullable BlockPos failurePos) {
        public boolean matches() {
            return status == StructureResult.MATCH;
        }
    }

    /** 核对外壳时逐格回调，用来统计外壳上的部件。每个候选长方体开始核对前调一次 {@link #reset}，只有 MATCH 那一轮的统计有效 */
    public interface Visitor {
        void reset();

        /**
         * @param pos   可变坐标，要留着就 {@code pos.immutable()}
         * @param state 这一格的方块（已经确认是外壳方块，所在区块已加载）
         * @return false 表示这个外壳不能要这一格（判为 MISMATCH / REJECTED）
         */
        boolean accept(BlockPos pos, BlockState state);
    }

    public static final Visitor NO_VISITOR = new Visitor() {
        @Override
        public void reset() {
        }

        @Override
        public boolean accept(BlockPos pos, BlockState state) {
            return true;
        }
    };

    /** 试面法向的顺序：顶 / 底面优先，再侧墙 */
    private static final Direction.Axis[] NORMALS = {Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z};
    private static final Direction.AxisDirection[] SIDES = {Direction.AxisDirection.POSITIVE, Direction.AxisDirection.NEGATIVE};

    private static final int LOADED_NOT_CASING = 0;
    private static final int CASING = 1;
    private static final int UNLOADED = -1;

    private CuboidShellScanner() {
    }

    /**
     * 扫描。只能在服务端主线程调用（取区块用的 getChunkNow 在别的线程恒返回 null，结果会是 UNKNOWN）。
     *
     * @param origin  起点（控制器），必须是外壳的一格
     * @param casing  哪些方块能做外壳
     * @param minSize 每边最少几格（至少按 3 算：要有内部）
     * @param maxSize 每边最多几格（小于 minSize 时按 minSize 算）
     */
    public static Result scan(Level level, BlockPos origin, Predicate<BlockState> casing, int minSize, int maxSize, Visitor visitor) {
        int min = Math.max(3, minSize);
        int max = Math.max(min, maxSize);
        Reader reader = new Reader(level, casing);

        int originTest = reader.test(origin);
        if (originTest == UNLOADED) {
            return new Result(StructureResult.UNKNOWN, null, null, null);
        }
        if (originTest == LOADED_NOT_CASING) {
            return new Result(StructureResult.MISMATCH, null, Failure.GAP, origin.immutable());
        }

        // 1. 过起点的三条外壳线：neg[a] / pos[a] 是沿轴 a 往负 / 正方向连续的外壳格数（不含起点）
        int[] neg = new int[3];
        int[] pos = new int[3];
        for (Direction.Axis axis : Direction.Axis.values()) {
            int n = walk(reader, origin, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE), max);
            int p = walk(reader, origin, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), max);
            if (n == UNLOADED || p == UNLOADED) {
                return new Result(StructureResult.UNKNOWN, null, null, null);
            }
            neg[axis.ordinal()] = n;
            pos[axis.ordinal()] = p;
        }

        Failures failures = new Failures();
        boolean unknown = false;
        for (Direction.Axis normal : NORMALS) {
            Direction.Axis u = firstOther(normal);
            Direction.Axis v = secondOther(normal);
            int uLen = neg[u.ordinal()] + pos[u.ordinal()] + 1;
            int vLen = neg[v.ordinal()] + pos[v.ordinal()] + 1;
            if (uLen < min || vLen < min) {
                // 起点不在法向为 normal 的面上（或者面太窄，没法和「不在面上」区分）
                continue;
            }
            if (uLen > max || vLen > max) {
                failures.add(Failure.SIZE, null);
                continue;
            }
            // 面的四个角（法向坐标和起点相同）
            BlockPos[] corners = new BlockPos[4];
            for (int i = 0; i < 4; i++) {
                int du = (i & 1) == 0 ? -neg[u.ordinal()] : pos[u.ordinal()];
                int dv = (i & 2) == 0 ? -neg[v.ordinal()] : pos[v.ordinal()];
                corners[i] = offset(origin, u, du, v, dv);
            }
            for (Direction.AxisDirection side : SIDES) {
                Direction toward = Direction.fromAxisAndDirection(normal, side);
                // 2. 四条棱往这边都至少要有 min-1 格
                int reach = max - 1;
                boolean cornerUnknown = false;
                for (BlockPos corner : corners) {
                    int w = walk(reader, corner, toward, max - 1);
                    if (w == UNLOADED) {
                        cornerUnknown = true;
                        break;
                    }
                    reach = Math.min(reach, w);
                }
                if (cornerUnknown) {
                    unknown = true;
                    continue;
                }
                if (reach < min - 1) {
                    continue;
                }
                // 3. 找对面：第一层整个截面都是外壳方块的平面
                int far = -1;
                boolean faceUnknown = false;
                for (int k = min - 1; k <= reach; k++) {
                    int t = fullFace(reader, corners[0], u, uLen, v, vLen, toward, k);
                    if (t == UNLOADED) {
                        faceUnknown = true;
                        break;
                    }
                    if (t == CASING) {
                        far = k;
                        break;
                    }
                }
                if (faceUnknown) {
                    unknown = true;
                    continue;
                }
                if (far < 0) {
                    // 四条棱都走到了上限还没有对面：多半是这个方向超长了
                    failures.add(reach >= max - 1 ? Failure.SIZE : Failure.NO_SHELL, null);
                    continue;
                }
                BlockPos farCorner = corners[3].relative(toward, far);
                // 4. 收边：去掉 u、v 两端垫着的外壳方块层（仓底下的地板、贴着的墙）
                BoundingBox box = trimPadding(reader, BoundingBox.fromCorners(corners[0], farCorner), normal, origin, min);
                // 5. 逐格核对
                visitor.reset();
                Result verified = verify(reader, box, visitor);
                if (verified.matches()) {
                    return verified;
                }
                if (verified.status() == StructureResult.UNKNOWN) {
                    unknown = true;
                } else {
                    failures.add(verified.failure(), verified.failurePos());
                }
            }
        }
        if (unknown) {
            return new Result(StructureResult.UNKNOWN, null, null, null);
        }
        return new Result(StructureResult.MISMATCH, null, failures.failure, failures.pos);
    }

    /** 核对长方体 box 的六个面：有一格不是外壳方块就不成形；没加载的格子跳过，最后没有确定的不符就是 UNKNOWN */
    public static Result verify(Level level, BoundingBox box, Predicate<BlockState> casing, Visitor visitor) {
        visitor.reset();
        return verify(new Reader(level, casing), box, visitor);
    }

    private static Result verify(Reader reader, BoundingBox box, Visitor visitor) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean unknown = false;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            boolean yEdge = y == box.minY() || y == box.maxY();
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                boolean fullRow = yEdge || z == box.minZ() || z == box.maxZ();
                int step = fullRow ? 1 : Math.max(1, box.maxX() - box.minX());
                for (int x = box.minX(); x <= box.maxX(); x += step) {
                    cursor.set(x, y, z);
                    BlockState state = reader.state(cursor);
                    if (state == null) {
                        unknown = true;
                        continue;
                    }
                    if (!reader.casing.test(state)) {
                        return new Result(StructureResult.MISMATCH, null, Failure.GAP, cursor.immutable());
                    }
                    if (!visitor.accept(cursor, state)) {
                        return new Result(StructureResult.MISMATCH, null, Failure.REJECTED, cursor.immutable());
                    }
                }
            }
        }
        return unknown ? new Result(StructureResult.UNKNOWN, null, null, null) : new Result(StructureResult.MATCH, box, null, null);
    }

    /**
     * 收边：box 在 normal 以外两个轴上的四个端面，去掉外面垫着的整层外壳方块（见类注释第 4 步）。
     * 法向那两面不动：近面过起点，对面本来就是从里往外找到的第一层整层。
     * 只会返回 box 的子集，而且新的端面已经确认是整层外壳方块，所以 box 能通过核对时收过的也一定能通过。
     */
    private static BoundingBox trimPadding(Reader reader, BoundingBox box, Direction.Axis normal, BlockPos origin, int min) {
        int[] lo = {box.minX(), box.minY(), box.minZ()};
        int[] hi = {box.maxX(), box.maxY(), box.maxZ()};
        boolean trimmed = false;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == normal) {
                continue;
            }
            int a = axis.ordinal();
            int o = axis.choose(origin.getX(), origin.getY(), origin.getZ());
            int n = paddingLayers(reader, lo, hi, axis, true, min);
            // 收完起点要还在盒子里（起点在垫层里是说不通的形状，照旧不收）
            if (n > 0 && lo[a] + n <= o) {
                lo[a] += n;
                trimmed = true;
            }
            n = paddingLayers(reader, lo, hi, axis, false, min);
            if (n > 0 && hi[a] - n >= o) {
                hi[a] -= n;
                trimmed = true;
            }
        }
        return trimmed ? new BoundingBox(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]) : box;
    }

    /**
     * 盒子 [lo, hi] 沿 axis 从最小端（fromMin）或最大端往里，外面垫着几层外壳方块：
     * 从端面往里连续 n 层都是整层外壳方块、n ≥ 2、第 n+1 层确定不是整层时返回 n-1，否则 0（不收）。
     * 收完边长不能少于 min；碰到没加载的区块也不收（这时区块整列没加载，核对外壳时同样会碰到，结果是 UNKNOWN）。
     */
    private static int paddingLayers(Reader reader, int[] lo, int[] hi, Direction.Axis axis, boolean fromMin, int min) {
        int a = axis.ordinal();
        int maxTrim = hi[a] - lo[a] + 1 - min;
        if (maxTrim < 1) {
            return 0;
        }
        int start = fromMin ? lo[a] : hi[a];
        int step = fromMin ? 1 : -1;
        // 先查第 2 层：空心的仓第 2 层有内部的空气，一般一两格就排除了，端面本身都不用读
        if (layer(reader, lo, hi, axis, start + step) != CASING || layer(reader, lo, hi, axis, start) != CASING) {
            return 0;
        }
        for (int n = 2; n - 1 <= maxTrim; n++) {
            int t = layer(reader, lo, hi, axis, start + step * n);
            if (t == UNLOADED) {
                return 0;
            }
            if (t == LOADED_NOT_CASING) {
                return n - 1;
            }
        }
        // 整层一直连到收不动的地方（实心的仓，或者垫得太厚）：不收
        return 0;
    }

    /** 盒子 [lo, hi] 在 axis = coord 处的整个截面是否都是外壳方块（{@link #fullFace} 的写法） */
    private static int layer(Reader reader, int[] lo, int[] hi, Direction.Axis axis, int coord) {
        Direction.Axis u = firstOther(axis);
        Direction.Axis v = secondOther(axis);
        int[] corner = lo.clone();
        corner[axis.ordinal()] = coord;
        return fullFace(reader, new BlockPos(corner[0], corner[1], corner[2]),
                u, hi[u.ordinal()] - lo[u.ordinal()] + 1, v, hi[v.ordinal()] - lo[v.ordinal()] + 1,
                Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), 0);
    }

    /**
     * 从 corner（面的最小角）沿 toward 走 k 格处的整个截面（uLen × vLen）是否都是外壳方块。
     * 先查紧挨着角的内侧那格：墙中间的层那一格是内部（一般是空气），一下就能排除。
     */
    private static int fullFace(Reader reader, BlockPos corner, Direction.Axis u, int uLen, Direction.Axis v, int vLen,
                                Direction toward, int k) {
        BlockPos base = corner.relative(toward, k);
        int quick = reader.test(offset(base, u, 1, v, 1));
        if (quick == LOADED_NOT_CASING) {
            return LOADED_NOT_CASING;
        }
        boolean unknown = quick == UNLOADED;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < uLen; i++) {
            for (int j = 0; j < vLen; j++) {
                cursor.set(base);
                cursor.move(Direction.fromAxisAndDirection(u, Direction.AxisDirection.POSITIVE), i);
                cursor.move(Direction.fromAxisAndDirection(v, Direction.AxisDirection.POSITIVE), j);
                int t = reader.test(cursor);
                if (t == LOADED_NOT_CASING) {
                    return LOADED_NOT_CASING;
                }
                if (t == UNLOADED) {
                    unknown = true;
                }
            }
        }
        return unknown ? UNLOADED : CASING;
    }

    /** 从 start 沿 dir 数连续的外壳格（不含 start），最多 limit 格；碰到没加载的区块返回 {@link #UNLOADED} */
    private static int walk(Reader reader, BlockPos start, Direction dir, int limit) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        for (int i = 1; i <= limit; i++) {
            cursor.move(dir);
            int t = reader.test(cursor);
            if (t == UNLOADED) {
                return UNLOADED;
            }
            if (t == LOADED_NOT_CASING) {
                return i - 1;
            }
        }
        return limit;
    }

    private static BlockPos offset(BlockPos pos, Direction.Axis a, int da, Direction.Axis b, int db) {
        return pos.relative(Direction.fromAxisAndDirection(a, Direction.AxisDirection.POSITIVE), da)
                .relative(Direction.fromAxisAndDirection(b, Direction.AxisDirection.POSITIVE), db);
    }

    private static Direction.Axis firstOther(Direction.Axis axis) {
        return axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
    }

    private static Direction.Axis secondOther(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
    }

    /** 记下最有用的一条原因：缺块 / 被拒 &gt; 尺寸 &gt; 找不到外壳，同级留第一条 */
    private static final class Failures {
        Failure failure = Failure.NO_SHELL;
        @Nullable
        BlockPos pos;
        int rank = -1;

        void add(Failure f, @Nullable BlockPos at) {
            int r = switch (f) {
                case GAP, REJECTED -> 2;
                case SIZE -> 1;
                case NO_SHELL -> 0;
            };
            if (r > rank) {
                rank = r;
                failure = f;
                pos = at;
            }
        }
    }

    /** 只读已加载区块的方块读取器，缓存上一次用到的区块 */
    private static final class Reader {
        final Level level;
        final Predicate<BlockState> casing;
        private long chunkKey = Long.MIN_VALUE;
        @Nullable
        private LevelChunk chunk;

        Reader(Level level, Predicate<BlockState> casing) {
            this.level = level;
            this.casing = casing;
        }

        /** 区块没加载完返回 null；超出世界高度按空气 */
        @Nullable
        BlockState state(BlockPos pos) {
            if (level.isOutsideBuildHeight(pos)) {
                return Blocks.AIR.defaultBlockState();
            }
            int cx = SectionPos.blockToSectionCoord(pos.getX());
            int cz = SectionPos.blockToSectionCoord(pos.getZ());
            long key = ChunkPos.asLong(cx, cz);
            if (key != chunkKey) {
                chunk = level.getChunkSource().getChunkNow(cx, cz);
                chunkKey = key;
            }
            return chunk == null ? null : chunk.getBlockState(pos);
        }

        int test(BlockPos pos) {
            BlockState state = state(pos);
            if (state == null) {
                return UNLOADED;
            }
            return casing.test(state) ? CASING : LOADED_NOT_CASING;
        }
    }
}
