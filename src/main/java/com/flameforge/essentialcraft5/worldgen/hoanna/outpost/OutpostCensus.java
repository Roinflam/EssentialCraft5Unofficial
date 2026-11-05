package com.flameforge.essentialcraft5.worldgen.hoanna.outpost;

import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.SpreadExcludingPlacement;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * 暗影区普查（第十三批 13e-S3，batch13.md §6.4「已定：量完再定 spacing」、§7.4 第 1 条）：在群系源上按 step 区块的步长采样一片方形窗口，
 * 把「审判庭前哨能落的群系」（默认暗影腐化之地）的采样点按四邻接连成一片片，再用 {@link StructureForecast} 数每片里的前哨预测起点。
 * 纯计算：只读群系源和结构集的格子哈希，不加载、不生成区块。
 * <p>
 * 开销：一个采样点约 7~8 µs。2 048 × 2 048 区块按 4 区块步长是 512² ≈ 26 万点、约 2 秒；前哨的格子另采一次（24 格子约 7 300 格）。
 * 这么大的窗口不进 GameTest，由开发指令 {@code /ec5 dev hoanna_census}（{@link OutpostCommands}）跑，结果记在 {@code tools/gen/hoanna_bench.md}。
 * <p>
 * 一片的大小按「采样点数 × step²」折成区块；窗口边上的片被截断了，单独记个数。前哨起点落到最近的采样点上，那一点不是暗影就看周围 8 点，
 * 都不是记「没归到片」（群系边缘：起点在 4 区块的采样格里，采样点却在外面）。
 */
public final class OutpostCensus {

    /** 片的大小分档（区块）：&lt; 64、64~255、256~1 023、1 024~4 095、≥ 4 096 */
    public static final int[] BUCKET_FLOORS = {0, 64, 256, 1024, 4096};
    /** 「一片暗影区」按多大算（区块）：小于它的是零星的几块，不算玩家眼里的一片 */
    public static final int LARGE_PATCH = 256;

    private OutpostCensus() {
    }

    /**
     * 采样好的网格。
     *
     * @param minX  窗口最小角（区块）
     * @param minZ  窗口最小角（区块）
     * @param step  步长（区块）
     * @param side  每边几个采样点
     * @param patch 每个采样点属于第几片（−1 = 不是暗影）
     * @param sizes 每片几个采样点
     * @param edge  每片碰没碰到窗口边
     * @param samples 一共采了几个点
     * @param nanos   采样花的时间
     */
    public record Grid(int minX, int minZ, int step, int side, int[] patch, int[] sizes, boolean[] edge, int samples, long nanos) {

        public int patches() {
            return sizes.length;
        }

        /** 第 i 片的大小（区块） */
        public int sizeChunks(int i) {
            return sizes[i] * step * step;
        }

        /** 暗影采样点一共几个 */
        public int siteCells() {
            int sum = 0;
            for (int size : sizes) {
                sum += size;
            }
            return sum;
        }

        /** 区块 → 最近的采样点所属的片；那一点不是暗影就看周围 8 点，都不是返回 −1 */
        public int patchAt(ChunkPos chunk) {
            int gx = Math.floorDiv(chunk.x - minX + step / 2, step);
            int gz = Math.floorDiv(chunk.z - minZ + step / 2, step);
            int here = at(gx, gz);
            if (here >= 0) {
                return here;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int near = at(gx + dx, gz + dz);
                    if (near >= 0) {
                        return near;
                    }
                }
            }
            return -1;
        }

        private int at(int gx, int gz) {
            if (gx < 0 || gz < 0 || gx >= side || gz >= side) {
                return -1;
            }
            return patch[gx * side + gz];
        }
    }

    /**
     * 一次普查（一种放置）的结果。
     *
     * @param candidates       窗口里结构集格子的候选起点个数
     * @param inBiome          候选里群系对得上、中心那一列也不是液体的个数（结构自己的否决先算，所以「被排除」的比例只算离城镇 / 宝库太近的）
     * @param outposts         预测起点个数（群系对得上、中心不是液体、又过了排除，= 结构预测的结果）
     * @param unassigned       起点没归到任何一片的个数
     * @param bucketPatches    各大小档的片数
     * @param bucketOutposts   各大小档里的起点数
     * @param bucketEmpty      各大小档里一座都没有的片数
     * @param largeSiteChunks  ≥ {@link #LARGE_PATCH} 的片合计多少区块
     */
    public record Tally(int spacing, int separation, int candidates, int inBiome, int outposts, int unassigned,
                        int[] bucketPatches, int[] bucketOutposts, int[] bucketEmpty, long largeSiteChunks) {

        public int largePatches() {
            return sumFrom(bucketPatches, bucketOf(LARGE_PATCH));
        }

        public int largeOutposts() {
            return sumFrom(bucketOutposts, bucketOf(LARGE_PATCH));
        }

        public int largeEmpty() {
            return sumFrom(bucketEmpty, bucketOf(LARGE_PATCH));
        }

        /** ≥ {@link #LARGE_PATCH} 的片平均每片几座 */
        public double perLargePatch() {
            int patches = largePatches();
            return patches == 0 ? 0D : largeOutposts() / (double) patches;
        }

        /** ≥ {@link #LARGE_PATCH} 的片里一座都没有的占比 */
        public double largeEmptyShare() {
            int patches = largePatches();
            return patches == 0 ? 0D : largeEmpty() / (double) patches;
        }

        /** 候选群系对得上、却被排除（城镇 / 宝库 8 区块内）的占比 */
        public double excludedShare() {
            return inBiome == 0 ? 0D : 1D - outposts / (double) inBiome;
        }

        private static int sumFrom(int[] values, int from) {
            int sum = 0;
            for (int i = from; i < values.length; i++) {
                sum += values[i];
            }
            return sum;
        }
    }

    /** 大小（区块）落在哪一档 */
    public static int bucketOf(int sizeChunks) {
        int bucket = 0;
        for (int i = 0; i < BUCKET_FLOORS.length; i++) {
            if (sizeChunks >= BUCKET_FLOORS[i]) {
                bucket = i;
            }
        }
        return bucket;
    }

    /**
     * 采样一片窗口：以 center 为中心、边长 sizeChunks 区块，每 step 区块一个点（区块最小角 +4、海平面，同结构预测），连成片。
     *
     * @param sites 算不算前哨能落的群系（传结构的群系判定，和预测用同一个）
     */
    public static Grid sample(StructureForecast.World world, Predicate<Holder<Biome>> sites, ChunkPos center, int sizeChunks, int step) {
        int stride = Math.max(1, step);
        int side = Math.max(1, sizeChunks / stride);
        int minX = center.x - side * stride / 2;
        int minZ = center.z - side * stride / 2;
        long started = System.nanoTime();
        boolean[] site = new boolean[side * side];
        for (int gx = 0; gx < side; gx++) {
            for (int gz = 0; gz < side; gz++) {
                ChunkPos chunk = new ChunkPos(minX + gx * stride, minZ + gz * stride);
                site[gx * side + gz] = sites.test(world.biomeAt(StructureForecast.sampleX(chunk), StructureForecast.sampleZ(chunk)));
            }
        }
        long nanos = System.nanoTime() - started;
        int[] patch = new int[side * side];
        Arrays.fill(patch, -1);
        List<Integer> sizes = new ArrayList<>();
        List<Boolean> edges = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < site.length; start++) {
            if (!site[start] || patch[start] >= 0) {
                continue;
            }
            int id = sizes.size();
            int size = 0;
            boolean edge = false;
            patch[start] = id;
            queue.add(start);
            while (!queue.isEmpty()) {
                int cell = queue.poll();
                size++;
                int gx = cell / side;
                int gz = cell % side;
                if (gx == 0 || gz == 0 || gx == side - 1 || gz == side - 1) {
                    edge = true;
                }
                int[][] near = {{gx - 1, gz}, {gx + 1, gz}, {gx, gz - 1}, {gx, gz + 1}};
                for (int[] n : near) {
                    if (n[0] < 0 || n[1] < 0 || n[0] >= side || n[1] >= side) {
                        continue;
                    }
                    int next = n[0] * side + n[1];
                    if (site[next] && patch[next] < 0) {
                        patch[next] = id;
                        queue.add(next);
                    }
                }
            }
            sizes.add(size);
            edges.add(edge);
        }
        int[] sizeArray = sizes.stream().mapToInt(Integer::intValue).toArray();
        boolean[] edgeArray = new boolean[edges.size()];
        for (int i = 0; i < edgeArray.length; i++) {
            edgeArray[i] = edges.get(i);
        }
        return new Grid(minX, minZ, stride, side, patch, sizeArray, edgeArray, side * side, nanos);
    }

    /** 数这个结构集（可以是换了 spacing 的放置，见 {@link #withPlacement}）在网格窗口里的预测起点，按片归档 */
    public static Tally tally(StructureForecast.World world, Grid grid, StructureForecast.Target target) {
        int spacing = target.placement().spacing();
        int maxX = grid.minX() + grid.side() * grid.step() - 1;
        int maxZ = grid.minZ() + grid.side() * grid.step() - 1;
        int candidates = 0;
        int inBiome = 0;
        int outposts = 0;
        int unassigned = 0;
        int[] perPatch = new int[grid.patches()];
        for (int cellX = Math.floorDiv(grid.minX(), spacing); cellX <= Math.floorDiv(maxX, spacing); cellX++) {
            for (int cellZ = Math.floorDiv(grid.minZ(), spacing); cellZ <= Math.floorDiv(maxZ, spacing); cellZ++) {
                ChunkPos chunk = StructureForecast.candidate(world, target, cellX, cellZ);
                if (chunk.x < grid.minX() || chunk.x > maxX || chunk.z < grid.minZ() || chunk.z > maxZ) {
                    continue;
                }
                candidates++;
                if (!target.biomes().test(world.biomeAt(StructureForecast.sampleX(chunk), StructureForecast.sampleZ(chunk)))) {
                    continue;
                }
                if (target.veto() != null && target.veto().rejects().test(world, chunk)) {
                    continue;
                }
                inBiome++;
                if (!target.placement().isStructureChunk(world.state(), chunk.x, chunk.z)) {
                    continue;
                }
                outposts++;
                int patch = grid.patchAt(chunk);
                if (patch < 0) {
                    unassigned++;
                } else {
                    perPatch[patch]++;
                }
            }
        }
        int buckets = BUCKET_FLOORS.length;
        int[] bucketPatches = new int[buckets];
        int[] bucketOutposts = new int[buckets];
        int[] bucketEmpty = new int[buckets];
        long largeChunks = 0L;
        for (int i = 0; i < grid.patches(); i++) {
            int size = grid.sizeChunks(i);
            int bucket = bucketOf(size);
            bucketPatches[bucket]++;
            bucketOutposts[bucket] += perPatch[i];
            if (perPatch[i] == 0) {
                bucketEmpty[bucket]++;
            }
            if (size >= LARGE_PATCH) {
                largeChunks += size;
            }
        }
        return new Tally(spacing, target.placement().separation(), candidates, inBiome, outposts, unassigned, bucketPatches, bucketOutposts,
                bucketEmpty, largeChunks);
    }

    /**
     * 同一个结构集、换一种放置（量别的 spacing 用）：排除列表照原来的，salt 用前哨的 {@link OutpostStructures#SALT}
     * （原版 {@code salt()} 不公开；结构集 JSON 的 salt 和这个常量一致由 GameTest 守着）。不是 spread_excluding 的原样返回。
     */
    public static StructureForecast.Target withPlacement(StructureForecast.Target target, int spacing, int separation) {
        if (!(target.placement() instanceof SpreadExcludingPlacement spread)) {
            return target;
        }
        SpreadExcludingPlacement changed = new SpreadExcludingPlacement(spacing, separation, OutpostStructures.SALT, spread.exclusionZones());
        return target.withPlacement(changed);
    }

    /** 一行摘要（日志、指令反馈、bench.md 用） */
    public static String describe(Grid grid, Tally tally) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "%d/%d：暗影 %d 片（≥%d 区块 %d 片、合计 %d 区块），起点 %d（候选 %d、群系对 %d、被排除 %.0f%%、没归到片 %d）；"
                        + "大片平均 %.2f 座、一座都没有 %.0f%%；分档",
                tally.spacing(), tally.separation(), grid.patches(), LARGE_PATCH, tally.largePatches(), tally.largeSiteChunks(), tally.outposts(),
                tally.candidates(), tally.inBiome(), tally.excludedShare() * 100D, tally.unassigned(), tally.perLargePatch(),
                tally.largeEmptyShare() * 100D));
        for (int i = 0; i < BUCKET_FLOORS.length; i++) {
            out.append(String.format(Locale.ROOT, " [≥%d：%d 片 %d 座 空 %d]", BUCKET_FLOORS[i], tally.bucketPatches()[i], tally.bucketOutposts()[i],
                    tally.bucketEmpty()[i]));
        }
        return out.toString();
    }

    /** 每片大小的几个分位数（区块）：中位、75%、90%、最大 */
    public static int[] sizeQuantiles(Grid grid) {
        int[] sizes = new int[grid.patches()];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = grid.sizeChunks(i);
        }
        Arrays.sort(sizes);
        if (sizes.length == 0) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{sizes[sizes.length / 2], sizes[(int) (sizes.length * 0.75)], sizes[(int) (sizes.length * 0.9)], sizes[sizes.length - 1]};
    }

    /** 几种放置一起量（同一张网格） */
    public static List<Tally> tallyAll(StructureForecast.World world, Grid grid, List<StructureForecast.Target> targets) {
        return targets.stream().map(target -> tally(world, grid, target)).toList();
    }
}
