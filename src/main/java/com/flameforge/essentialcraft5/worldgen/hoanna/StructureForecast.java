package com.flameforge.essentialcraft5.worldgen.hoanna;

import com.flameforge.essentialcraft5.hoanna.HoannaSearchBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * 结构预测（第十三批 13d-HF，batch13.md §6.3「结构预测」；HS §2.3.1）：不加载、不生成任何区块，算出某种结构在哪些区块起头。
 * 海纳尔勘测图、里程碑找最近的城镇、13e 的解码器改指前哨都用它。
 * <h2>怎么算</h2>
 * <ol>
 *   <li>按结构集（{@code random_spread} 或 {@code spread_excluding}）的格子从中心那一格往外一圈圈走：每格的候选区块
 *       {@code getPotentialStructureChunk} 是纯哈希；</li>
 *   <li>候选起点采样群系源（{@link #samplePoint}：区块最小角 +{@value #SAMPLE_OFFSET}、y 取海平面，约 7~8 µs / 点），
 *       在结构的群系标签里才往下算——海纳尔七个群系的深度参数都是 [0, 1]（没有洞穴群系），哪个高度采样结果都一样，
 *       所以和城镇 {@code findGenerationPoint} 在起点地面高度上判群系是同一个结论；</li>
 *   <li>群系过了才走 {@code StructurePlacement.isStructureChunk}（frequency、原版 exclusion_zone、{@code spread_excluding} 的多集排除，
 *       排除一项约 289 次格子哈希、约 15 µs，外加对方候选的一次群系采样）；</li>
 *   <li>最后是结构自己在群系之外还会拒掉的情形（{@link Vetoed}，{@link Target#veto}）：城镇的「非道路件 ≤ 2 返回空」（纯计算，
 *       同种子同区块抽出来的布局和真生成一样）；封印宝库、审判庭前哨的「起点那一列顶上是液体（湖、河、海）不放」（一次噪声列，约 3 ms，
 *       和它们定高取的是同一列，见 {@link SurfaceColumn}）。</li>
 * </ol>
 * 四步都和世界生成用的是同一个判定，所以预测 = 实际（GameTest {@code forecastMatchesTownStarts}、{@code hoannaForecastMatchesGeneration13e} 逐个对）。
 * 一个结构集里有几种结构按权重抽的（原版村庄那种）这里只看「要找的结构里有没有哪一种认这个群系」，不套第 4 步；海纳尔的结构集都只有一种结构。
 * <h2>开销</h2>
 * 一次采样算一个「点」；增量的 {@link Search} 每刻按 {@link HoannaSearchBudget}（全服每刻合计 ≤ 256 点、≤ 2 ms，和故障核心解码共用）推进。
 * 第 4 步要算噪声列的（宝库、前哨）每算一列就看一次表，一列约 3 ms，所以那一刻最多超出预算一列的量；只有群系、排除都过了、又比已找到的更近的候选才算这一列
 * （勘测图找宝库、解码器找前哨每次通常几列，分几刻做完）。
 * 2 048 格半径 = 128 区块：城镇（32 格子）约 64 格、前哨（24）约 114 格，每次 ≤ 114 个点 ≈ 1 ms。
 * 找到一处以后再走到「这一圈里不可能更近」为止（同原版 {@code findNearestMapStructure} 多看一圈）。
 * <h2>用法</h2>
 * <pre>
 * StructureForecast.Search search = new StructureForecast.Search(level, TOWN_SET, player.blockPosition(), 2048, skipped::contains);
 * // 每刻（服务端主线程）：
 * if (search.step(level.getServer())) { search.result().ifPresent(pos -&gt; …); }
 * </pre>
 * 测试和离线普查用同步、不限预算的 {@link #predictAll}、{@link Search#finish}。
 */
public final class StructureForecast {

    /** 群系采样点相对区块最小角的偏移（和城镇 {@code findGenerationPoint} 的起点一样；13e 的新结构起点也取这里） */
    public static final int SAMPLE_OFFSET = 4;
    /** 一次 {@link Search#finish} 每批多少点（只给测试 / 普查用） */
    private static final int FINISH_CHUNK = 1024;
    /** 每走多少格看一次表 */
    private static final int CLOCK_CHUNK = 32;

    private StructureForecast() {
    }

    // ================================================================== 世界与目标

    /**
     * 算预测要的几样东西（都不碰区块）。
     *
     * @param state       结构放置状态（世界种子、按群系源过滤过的结构集）
     * @param biomes      群系源
     * @param sampler     气候采样器
     * @param seaLevel    采样高度
     * @param generator   生成器（第 4 步算噪声列用）
     * @param randomState 随机状态（同上）
     * @param height      世界高度范围（同上，和结构生成时的区块一样）
     */
    public record World(ChunkGeneratorStructureState state, BiomeSource biomes, Climate.Sampler sampler, int seaLevel, ChunkGenerator generator,
                        RandomState randomState, LevelHeightAccessor height) {

        public static World of(ServerLevel level) {
            ServerChunkCache cache = level.getChunkSource();
            return of(cache.getGeneratorState(), cache.getGenerator(), cache.randomState(),
                    LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()));
        }

        /** 离线解出来的一套生成器（GameTest、带种子的普查） */
        public static World of(ChunkGeneratorStructureState state, ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height) {
            return new World(state, generator.getBiomeSource(), randomState.sampler(), generator.getSeaLevel(), generator, randomState, height);
        }

        public long seed() {
            return state.getLevelSeed();
        }

        /** 采样一个点的群系（四分之一坐标，y 取海平面） */
        public Holder<Biome> biomeAt(int blockX, int blockZ) {
            return biomes.getNoiseBiome(QuartPos.fromBlock(blockX), QuartPos.fromBlock(seaLevel), QuartPos.fromBlock(blockZ), sampler);
        }
    }

    /**
     * 要找的一个结构集。
     *
     * @param set       结构集（判 frequency、排除用）
     * @param placement 它的放置（{@code random_spread} 或其子类 {@code spread_excluding}）
     * @param biomes    候选起点的群系要过的判定（要找的那几种结构的群系标签合起来）
     * @param veto      群系、排除都过了以后结构自己的否决（第 4 步）；null = 不否决
     */
    public record Target(Holder<StructureSet> set, RandomSpreadStructurePlacement placement, Predicate<Holder<Biome>> biomes, @Nullable StartVeto veto) {

        public Target(Holder<StructureSet> set, RandomSpreadStructurePlacement placement, Predicate<Holder<Biome>> biomes) {
            this(set, placement, biomes, null);
        }

        /** 同一个集、换一种放置（普查量别的 spacing 用），群系和否决照旧 */
        public Target withPlacement(RandomSpreadStructurePlacement changed) {
            return new Target(set, changed, biomes, veto);
        }
    }

    /**
     * 结构在群系之外还会拒掉的候选（第 4 步）。
     *
     * @param rejects 这个候选区块会不会被结构自己拒掉（只读哈希、群系源、噪声，不碰区块）
     * @param heavy   要算噪声列（约 3 ms）：{@link Search} 每算一次就看一次表
     */
    public record StartVeto(BiPredicate<World, ChunkPos> rejects, boolean heavy) {
    }

    /** 结构类实现它，预测就照它的规则多否决一步（实现必须和自己的 findGenerationPoint 用同一个判定） */
    public interface Vetoed {
        StartVeto forecastVeto();
    }

    /** 只有一种结构、而且它带否决时取它的；几种结构混抽的集不否决（见类注释） */
    @Nullable
    private static StartVeto vetoOf(List<Holder<Structure>> structures) {
        return structures.size() == 1 && structures.get(0).value() instanceof Vetoed vetoed ? vetoed.forecastVeto() : null;
    }

    /** 这个结构集（不在这个世界的可能结构集里、放置不是 random_spread 的返回空表） */
    public static List<Target> targets(World world, Holder<StructureSet> set) {
        if (!world.state().possibleStructureSets().contains(set) || !(set.value().placement() instanceof RandomSpreadStructurePlacement placement)) {
            return List.of();
        }
        List<Holder<Structure>> structures = set.value().structures().stream().map(StructureSet.StructureSelectionEntry::structure).toList();
        return List.of(new Target(set, placement, biome -> structures.stream().anyMatch(structure -> structure.value().biomes().contains(biome)),
                vetoOf(structures)));
    }

    /** 按注册名找结构集（服务端注册表里没有的返回空表） */
    public static List<Target> targets(ServerLevel level, ResourceKey<StructureSet> key) {
        World world = World.of(level);
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolder(key)
                .map(holder -> targets(world, holder)).orElse(List.of());
    }

    /** 能生成这几种结构的结构集（这个世界可能有的、放置是 random_spread 的），每个集只认要找的那几种的群系 */
    public static List<Target> targets(World world, HolderSet<Structure> wanted) {
        List<Target> out = new ArrayList<>();
        for (Holder<StructureSet> set : world.state().possibleStructureSets()) {
            if (!(set.value().placement() instanceof RandomSpreadStructurePlacement placement)) {
                continue;
            }
            List<Holder<Structure>> matching = set.value().structures().stream().map(StructureSet.StructureSelectionEntry::structure)
                    .filter(wanted::contains).toList();
            if (!matching.isEmpty()) {
                StartVeto veto = matching.size() == set.value().structures().size() ? vetoOf(matching) : null;
                out.add(new Target(set, placement, biome -> matching.stream().anyMatch(structure -> structure.value().biomes().contains(biome)), veto));
            }
        }
        return out;
    }

    // ================================================================== 单个区块

    /** 采样群系的点：区块最小角 +{@value #SAMPLE_OFFSET}（y 由 {@link World#seaLevel} 定） */
    public static int sampleX(ChunkPos chunk) {
        return chunk.getMinBlockX() + SAMPLE_OFFSET;
    }

    public static int sampleZ(ChunkPos chunk) {
        return chunk.getMinBlockZ() + SAMPLE_OFFSET;
    }

    public static BlockPos samplePoint(ChunkPos chunk, int seaLevel) {
        return new BlockPos(sampleX(chunk), seaLevel, sampleZ(chunk));
    }

    /** 预测结果的坐标：起点区块的中心，y 取海平面 */
    public static BlockPos resultPos(ChunkPos chunk, int seaLevel) {
        return new BlockPos(chunk.getMiddleBlockX(), seaLevel, chunk.getMiddleBlockZ());
    }

    /** 这一格（cellX, cellZ）的候选区块（纯哈希） */
    public static ChunkPos candidate(World world, Target target, int cellX, int cellZ) {
        int spacing = target.placement().spacing();
        return target.placement().getPotentialStructureChunk(world.seed(), cellX * spacing, cellZ * spacing);
    }

    /** 候选区块过不过后三步（群系、isStructureChunk、结构自己的否决）。会采一次群系；带重否决的（宝库、前哨）群系和排除都过了再算一列噪声 */
    public static boolean accepts(World world, Target target, ChunkPos chunk) {
        if (!target.biomes().test(world.biomeAt(sampleX(chunk), sampleZ(chunk)))) {
            return false;
        }
        if (!target.placement().isStructureChunk(world.state(), chunk.x, chunk.z)) {
            return false;
        }
        return target.veto() == null || !target.veto().rejects().test(world, chunk);
    }

    /** 这个区块是不是预测的起点（先判它是不是自己那一格的候选，不是的话不采样） */
    public static boolean isPredictedStart(World world, Target target, ChunkPos chunk) {
        int spacing = target.placement().spacing();
        ChunkPos picked = candidate(world, target, Math.floorDiv(chunk.x, spacing), Math.floorDiv(chunk.z, spacing));
        return picked.equals(chunk) && accepts(world, target, chunk);
    }

    /**
     * 同步、不限预算：[min, max]（区块坐标，含两端）里这个结构集的全部预测起点，按格子先 x 后 z 的顺序。
     * 给 GameTest 和离线普查用；每个候选采一次群系。
     */
    public static List<ChunkPos> predictAll(World world, Target target, ChunkPos min, ChunkPos max) {
        int spacing = target.placement().spacing();
        List<ChunkPos> out = new ArrayList<>();
        for (int cellX = Math.floorDiv(min.x, spacing); cellX <= Math.floorDiv(max.x, spacing); cellX++) {
            for (int cellZ = Math.floorDiv(min.z, spacing); cellZ <= Math.floorDiv(max.z, spacing); cellZ++) {
                ChunkPos chunk = candidate(world, target, cellX, cellZ);
                if (chunk.x < min.x || chunk.x > max.x || chunk.z < min.z || chunk.z > max.z) {
                    continue;
                }
                if (accepts(world, target, chunk)) {
                    out.add(chunk);
                }
            }
        }
        return out;
    }

    // ================================================================== 增量搜索

    /**
     * 找离中心最近的一处（水平距离，起点区块中心到中心点），可以分几刻做完。只读哈希和群系源，不加载区块。
     * 几个结构集一起找时，每次挑「下一圈可能最近」的那个集往外走；找到一处以后，每个集都走到「这一圈不可能更近」才停。
     */
    public static final class Search {

        private final World world;
        private final BlockPos center;
        private final ChunkPos centerChunk;
        private final long radiusSq;
        private final Predicate<ChunkPos> skip;
        private final List<Walker> walkers = new ArrayList<>();
        private int samples;
        private boolean done;
        private long bestDistSq = Long.MAX_VALUE;
        private BlockPos best;

        /** 按结构集的注册名找（注册表里没有、这个世界不会有的，一开始就是「找完了、没找到」） */
        public Search(ServerLevel level, ResourceKey<StructureSet> set, BlockPos center, int radiusBlocks, Predicate<ChunkPos> skip) {
            this(World.of(level), targets(level, set), center, radiusBlocks, skip);
        }

        /** 按结构找（能生成它们的结构集都找，取最近的） */
        public Search(ServerLevel level, HolderSet<Structure> structures, BlockPos center, int radiusBlocks, Predicate<ChunkPos> skip) {
            this(World.of(level), structures, center, radiusBlocks, skip);
        }

        public Search(World world, HolderSet<Structure> structures, BlockPos center, int radiusBlocks, Predicate<ChunkPos> skip) {
            this(world, targets(world, structures), center, radiusBlocks, skip);
        }

        /**
         * @param radiusBlocks 水平半径（格）：起点区块中心离 center 超过它的不算
         * @param skip         跳过这些起点区块（勘测图的 Skip 表）；不采样
         */
        public Search(World world, List<Target> targets, BlockPos center, int radiusBlocks, Predicate<ChunkPos> skip) {
            this.world = world;
            this.center = center.immutable();
            this.centerChunk = new ChunkPos(center);
            long radius = Math.max(0, radiusBlocks);
            this.radiusSq = radius * radius;
            this.skip = skip;
            int radiusChunks = (int) ((radius + 15) / 16) + 1;
            for (Target target : targets) {
                walkers.add(new Walker(target, radiusChunks));
            }
            this.done = walkers.isEmpty();
        }

        /** 用全服预算推进一次（服务端主线程每刻调）：返回找完了没有 */
        public boolean step(MinecraftServer server) {
            if (done) {
                return true;
            }
            HoannaSearchBudget.Grant grant = HoannaSearchBudget.take(server);
            if (grant.empty()) {
                return false;
            }
            int before = samples;
            long started = System.nanoTime();
            boolean finished = advance(grant.points(), grant.nanos());
            HoannaSearchBudget.charge(samples - before, System.nanoTime() - started);
            return finished;
        }

        /**
         * 再看最多 maxSamples 个点、最多花 maxNanos 纳秒（每走 {@value StructureForecast#CLOCK_CHUNK} 格看一次表，头一批总会走完，保证每次都有进展）；
         * 返回找完了没有。不记全服的账（{@link #step} 记）。
         */
        public boolean advance(int maxSamples, long maxNanos) {
            if (done) {
                return true;
            }
            long started = System.nanoTime();
            int used = 0;
            int visited = 0;
            boolean heavy = false;
            while (used < maxSamples) {
                if (visited > 0 && (heavy || visited % CLOCK_CHUNK == 0) && System.nanoTime() - started >= maxNanos) {
                    return false;
                }
                Walker walker = nextWalker();
                if (walker == null) {
                    done = true;
                    return true;
                }
                visited++;
                int cost = walker.visitOne();
                used += cost;
                // 可能算过噪声列（约 3 ms）：下一格之前就看表，一刻最多超出一列的量
                heavy = cost > 0 && walker.target.veto() != null && walker.target.veto().heavy();
            }
            if (nextWalker() == null) {
                done = true;
            }
            return done;
        }

        /** 一口气找完（GameTest 用，不受全服预算管） */
        public Search finish() {
            while (!advance(FINISH_CHUNK, Long.MAX_VALUE)) {
                // 一批最多 FINISH_CHUNK 个点，直到找完
            }
            return this;
        }

        /** 下一圈可能最近的那个集；全都走完（或不可能更近了）返回 null */
        private Walker nextWalker() {
            Walker pick = null;
            long pickBound = Long.MAX_VALUE;
            for (Walker walker : walkers) {
                if (walker.exhausted()) {
                    continue;
                }
                long bound = walker.ringLowerBoundSq();
                if (bound > bestDistSq || bound > radiusSq) {
                    walker.stop();
                    continue;
                }
                if (pick == null || bound < pickBound) {
                    pick = walker;
                    pickBound = bound;
                }
            }
            return pick;
        }

        public boolean done() {
            return done;
        }

        /** 最近一处的起点区块中心（y 海平面）；没找完或没找到为空 */
        public Optional<BlockPos> result() {
            return done ? Optional.ofNullable(best) : Optional.empty();
        }

        /** 一共采样了几个群系点 */
        public int samples() {
            return samples;
        }

        private long distSq(ChunkPos chunk) {
            long dx = chunk.getMiddleBlockX() - center.getX();
            long dz = chunk.getMiddleBlockZ() - center.getZ();
            return dx * dx + dz * dz;
        }

        /** 一个结构集的一圈圈格子 */
        private final class Walker {

            private final Target target;
            private final int spacing;
            private final int centerCellX;
            private final int centerCellZ;
            private final int maxRing;
            private int ring;
            private int index;
            private boolean stopped;

            Walker(Target target, int radiusChunks) {
                this.target = target;
                this.spacing = target.placement().spacing();
                this.centerCellX = Math.floorDiv(centerChunk.x, spacing);
                this.centerCellZ = Math.floorDiv(centerChunk.z, spacing);
                this.maxRing = radiusChunks / spacing + 1;
            }

            boolean exhausted() {
                return stopped || ring > maxRing;
            }

            void stop() {
                stopped = true;
            }

            /** 这一圈的格子离中心至少多远（平方，格）：第 r 圈里的区块和中心区块在某个轴上至少差 (r − 1) × spacing + 1 个区块 */
            long ringLowerBoundSq() {
                if (ring <= 0) {
                    return 0L;
                }
                long blocks = Math.max(0L, ((long) (ring - 1) * spacing + 1L) * 16L - 16L);
                return blocks * blocks;
            }

            /** 走这一圈的下一格；返回这一格采样了几个点（0 或 1） */
            int visitOne() {
                int[] cell = cellAt(ring, index);
                index++;
                if (index >= cellsInRing(ring)) {
                    ring++;
                    index = 0;
                }
                ChunkPos chunk = candidate(world, target, centerCellX + cell[0], centerCellZ + cell[1]);
                long dist = distSq(chunk);
                if (dist > radiusSq || dist >= bestDistSq || skip.test(chunk)) {
                    return 0;
                }
                samples++;
                if (accepts(world, target, chunk)) {
                    bestDistSq = dist;
                    best = resultPos(chunk, world.seaLevel());
                }
                return 1;
            }
        }
    }

    /** 第 r 圈有几格（切比雪夫距离 = r 的格子，r = 0 时 1 格） */
    static int cellsInRing(int ring) {
        return ring == 0 ? 1 : 8 * ring;
    }

    /** 第 r 圈第 i 格相对中心格的偏移：先上下两条边（z = −r、z = r），再左右两条边去掉角 */
    static int[] cellAt(int ring, int i) {
        if (ring == 0) {
            return new int[]{0, 0};
        }
        int side = 2 * ring + 1;
        if (i < side) {
            return new int[]{i - ring, -ring};
        }
        i -= side;
        if (i < side) {
            return new int[]{i - ring, ring};
        }
        i -= side;
        int inner = side - 2;
        if (i < inner) {
            return new int[]{-ring, i - ring + 1};
        }
        i -= inner;
        return new int[]{ring, i - ring + 1};
    }
}
