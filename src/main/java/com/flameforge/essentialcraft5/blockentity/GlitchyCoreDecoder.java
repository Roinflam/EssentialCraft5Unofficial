package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaSearchBudget;
import com.flameforge.essentialcraft5.item.GlitchyCoreItem;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECInquisitoriumOutpost;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.flameforge.essentialcraft5.worldgen.hoanna.outpost.OutpostStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 辐射仓解码故障核心（第十批 10c-M1，batch10.md §5.10.2）：{@link RadiatingChamberBlockEntity} 的特殊分支，状态和进度存在辐射仓里。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li>输入：上、下原料格里只有一个带目标平衡值的故障核心（{@link GlitchyCoreItem#TAG_TARGET}），另一格空着（{@link #input}）。
 *       两格都有东西时照普通配方走（故障核心不是任何配方的原料，就是「空闲」）。漏斗、管道塞不进故障核心（按配方表放位置），要手放。</li>
 *   <li>推进：仓里存的 MRU 平衡值和目标相差 ≤ {@code decode_tolerance}（默认 0.02）的刻才推进 1，一共 {@code decode_ticks}（默认 1000）刻；
 *       {@code decode_mru}（默认 5000）MRU 按刻均摊（{@link #costAt}，加起来正好是总数），这一刻扣不起就停。
 *       平衡值跑出窗口就停住（不清零）；换了输入（换一个核心、拿走、挪到另一格）清零；红石信号停机时进度保留。工作的刻照普通配方一样往周围漏一点 MRU。</li>
 *   <li>输出槽要空着（坐标一组 1 个、各不相同）；不空就停在原处。</li>
 *   <li>进度满了以后算坐标（{@link SiteSearch}），分两步（第十三批 13e-S3 改指前哨，batch13.md §6.4、D29；进度 id、步骤数不变）：
 *       <b>先找前哨</b>：用结构预测（{@link StructureForecast}）在 {@code search_radius}（默认 3000）格内找离解码处最近的一座审判庭前哨，
 *       找到就指它的中心（信标座，= 起点区块中心）。只有落在半径内的候选起点要采一次群系（几百个点以内），和下一步走同一份全服预算。
 *       配置 {@code general.inquisitorium_outpost.decoder_targets_outpost} 关掉、数据包删了前哨的结构集、这个世界不会有前哨
 *       （{@link SiteSource#outposts} 是空的）时跳过这一步。<b>没有前哨</b>才照原来的找暗影腐化之地：
 *       在海纳尔（{@code server.getLevel(ECHoanna.LEVEL)}）的群系源上，以解码处的 x / z 为中心螺旋往外，
 *       每 {@code search_step} 格看一次 y = {@value #SEARCH_Y} 处的群系，第一个落在 {@code #essentialcraft5:inquisitorium_sites}（默认暗影腐化之地）里的点就是坐标；
 *       半径 {@code search_radius}（默认 3000）。只采样群系噪声、不加载区块；海纳尔七个群系的深度参数都是 [0, 1]（没有洞穴群系），看一个高度就够。
 *       一个点约 7~8 µs，所以<b>全服每刻合计</b>最多看 {@value #SEARCH_BUDGET_PER_TICK} 个点、最多花 2 ms（{@link HoannaSearchBudget}，
 *       和海纳尔勘测图、里程碑共用这一份；每看 {@value #SEARCH_CLOCK_CHUNK} 个点看一次表）：几台仓同一刻都在定位时排着队用，不会叠加。默认配置最坏 93 × 93 ≈ 8 650 个点，约 34 刻（不到 2 秒）看完，
 *       这几刻界面写「定位中」。一次搜索最多 {@value #MAX_RINGS} 圈（半径 ÷ 步长超过时步长自动加大，半径不变），总点数有上限。
 *       找不到（半径内没有、维度被删、标签是空的）就退回「解码处朝某个方向 {@code fallback_distance} 格」：方向由世界种子和解码处的 x / z 定
 *       （{@link #fallbackRandom}），同一处解码每次都是这一点，「再打一场回老地方」照样成立。</li>
 *   <li>完成：故障核心扣 1，输出槽放一份审判庭坐标（维度写海纳尔），给 {@value #GUIDE_RADIUS} 格内的玩家发 {@code guide/inquisitorium/decoded}。</li>
 * </ul>
 * GameTest 造的真玩家不在世界的玩家表里：发进度的候选人用 {@link #setTestCandidates} 换；找坐标的群系源用 {@link #setTestSiteSource} 换成固定种子的
 * （只给群系源的两参数 {@link SiteSource} 不找前哨，13e 以前的测试照旧；要测找前哨的用 {@link SiteSource#of}），
 * 要找的群系用 {@link #setTestSites} 换（量最坏情况）；同一刻连着推很多刻的测试用 {@link #setTestBudget} 给每次调用固定的点数（不记全服的账），
 * 测全服预算本身用 {@link #resetBudgetForTest}。都在同一段同步代码里设、测、置回。
 */
public final class GlitchyCoreDecoder {

    /** 看群系的高度（海纳尔的海平面附近） */
    public static final int SEARCH_Y = 64;
    /** 找坐标全服每刻合计最多看几个点（和别的找地方的共用 {@link HoannaSearchBudget}；一个点约 7~8 µs，256 个约 2 ms） */
    public static final int SEARCH_BUDGET_PER_TICK = HoannaSearchBudget.POINTS_PER_TICK;
    /** 找坐标全服每刻合计最多花多少纳秒（慢一些的机器上点数没用完也停） */
    public static final long SEARCH_NANOS_PER_TICK = HoannaSearchBudget.NANOS_PER_TICK;
    /** 每看几个点看一次表 */
    public static final int SEARCH_CLOCK_CHUNK = 32;
    /** 一次搜索最多几圈（螺旋 (2n + 1)² 个点，256 圈约 26 万个点）；半径 ÷ 步长超过时步长加大 */
    public static final int MAX_RINGS = 256;
    /** {@link SiteSearch#finish} 一口气找完时每次看几个点（只给测试用，不受全服预算管） */
    private static final int FINISH_CHUNK = 1024;
    /** 解码完成时多远以内的玩家打勾（同富集仓成形、定向培育的 16 格） */
    public static final double GUIDE_RADIUS = 16D;
    /** 平衡值比较时放过 float 误差（目标是两位小数） */
    private static final double BALANCE_EPSILON = 1.0E-4D;

    public static final String TAG = "decode";
    private static final String TAG_SLOT = "slot";
    private static final String TAG_TARGET = "target";
    private static final String TAG_PROGRESS = "progress";

    @Nullable
    private static List<? extends Player> testCandidates;
    @Nullable
    private static SiteSource testSiteSource;
    @Nullable
    private static Set<Holder<Biome>> testSites;
    @Nullable
    private static Integer testBudget;

    /** 现在在解哪一个：原料格（上 / 下）和核心上的目标平衡值 */
    public record Input(int slot, float target) {
    }

    /**
     * 找坐标用的群系源和气候采样器，外加找前哨用的结构预测世界与前哨结构集（没有就不找前哨）。
     *
     * @param forecast 结构预测用的世界（放置状态、群系源、采样器、海平面）；null = 不找前哨
     * @param outposts 前哨的结构集（{@code StructureForecast.targets}）；空表 = 不找前哨
     */
    public record SiteSource(BiomeSource source, Climate.Sampler sampler, @Nullable StructureForecast.World forecast, List<StructureForecast.Target> outposts) {

        public SiteSource {
            outposts = List.copyOf(outposts);
        }

        /** 只找暗影腐化之地（不找前哨） */
        public SiteSource(BiomeSource source, Climate.Sampler sampler) {
            this(source, sampler, null, List.of());
        }

        /** 先找前哨、再找暗影腐化之地：群系源和采样器取预测世界里的那一套 */
        public static SiteSource of(StructureForecast.World forecast, List<StructureForecast.Target> outposts) {
            return new SiteSource(forecast.biomes(), forecast.sampler(), forecast, outposts);
        }

        /** 同一套群系源，不找前哨（配置关掉时用） */
        public SiteSource withoutOutposts() {
            return forecast == null && outposts.isEmpty() ? this : new SiteSource(source, sampler);
        }

        /** 会不会先找前哨 */
        public boolean findsOutposts() {
            return forecast != null && !outposts.isEmpty();
        }
    }

    /** 界面上显示的数（进度条、平衡值窗口、每刻耗能） */
    public record View(int progress, int required, float balanceMin, float balanceMax, int costPerTick) {
    }

    @Nullable
    private Input tracked;
    private int progress;
    @Nullable
    private SiteSearch search;

    // ------------------------------------------------------------------
    // 输入与进度
    // ------------------------------------------------------------------

    /** 上、下两格里只有一个带目标平衡值的故障核心、另一格空着时返回它；否则 null（照普通配方走） */
    @Nullable
    public static Input input(ItemStack top, ItemStack bottom) {
        Float upper = GlitchyCoreItem.targetBalance(top);
        Float lower = GlitchyCoreItem.targetBalance(bottom);
        if (upper != null && bottom.isEmpty()) {
            return new Input(RadiatingChamberBlockEntity.SLOT_TOP, upper);
        }
        if (lower != null && top.isEmpty()) {
            return new Input(RadiatingChamberBlockEntity.SLOT_BOTTOM, lower);
        }
        return null;
    }

    /** 第 progress 刻（从 0 起）要扣多少 MRU：total 按 ticks 刻均摊，向下取整的差分（ticks 刻加起来正好 total） */
    public static int costAt(int progress, int total, int ticks) {
        if (ticks <= 0 || total <= 0) {
            return 0;
        }
        long next = (long) total * (progress + 1L) / ticks;
        long here = (long) total * progress / ticks;
        return (int) Math.max(0L, next - here);
    }

    /** 记下现在的输入；和上次的不一样（换了核心、挪了格子）就清零 */
    public void track(Input input) {
        if (!input.equals(tracked)) {
            tracked = input;
            progress = 0;
            search = null;
        }
    }

    /** 没有输入了：清零 */
    public void reset() {
        tracked = null;
        progress = 0;
        search = null;
    }

    public int progress() {
        return progress;
    }

    /** 这一次找坐标已经看了几个点（没在找为 0；GameTest 断言全服预算用） */
    public int locateSamples() {
        SiteSearch current = search;
        return current == null ? 0 : current.samples();
    }

    /** 正在解（有输入）时界面上显示的数；没有输入为 null */
    @Nullable
    public View view() {
        Input input = tracked;
        if (input == null) {
            return null;
        }
        ECInquisitorium.Rules.Values values = ECInquisitorium.Rules.values();
        float tolerance = (float) values.decodeTolerance();
        int ticks = values.decodeTicks();
        int cost = ticks <= 0 ? 0 : (int) Math.ceil((double) values.decodeMru() / ticks);
        return new View(Math.min(progress, ticks), ticks, input.target() - tolerance, input.target() + tolerance, cost);
    }

    /** 平衡值在不在窗口里 */
    public static boolean inWindow(float balance, float target, double tolerance) {
        return Math.abs(balance - target) <= tolerance + BALANCE_EPSILON;
    }

    // ------------------------------------------------------------------
    // 一刻
    // ------------------------------------------------------------------

    /** 解码推进一刻（服务端，没有红石信号、有输入时由辐射仓调），返回这一刻辐射仓该显示的状态 */
    RadiatingChamberBlockEntity.Status tick(RadiatingChamberBlockEntity be, ServerLevel level, Input input) {
        track(input);
        ECInquisitorium.Rules.Values values = ECInquisitorium.Rules.values();
        if (!be.inventory.getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).isEmpty()) {
            return RadiatingChamberBlockEntity.Status.OUTPUT_FULL;
        }
        int ticks = values.decodeTicks();
        if (progress < ticks) {
            if (!inWindow(be.mru.getBalance(), input.target(), values.decodeTolerance())) {
                return RadiatingChamberBlockEntity.Status.DECODE_BALANCE;
            }
            int cost = costAt(progress, values.decodeMru(), ticks);
            if (be.mru.getMRU() < cost) {
                return RadiatingChamberBlockEntity.Status.NO_MRU;
            }
            if (cost > 0) {
                be.mru.extractMRU(cost, true);
            }
            if (ECConfig.get(RadiatingChamberBlockEntity.Config.generateCorruption)) {
                ECCorruption.randomIncreaseAt(level, be.getBlockPos(), level.random, ECConfig.get(RadiatingChamberBlockEntity.Config.maxCorruptionGen));
            }
            progress++;
            level.blockEntityChanged(be.getBlockPos());
            if (progress < ticks) {
                return RadiatingChamberBlockEntity.Status.DECODING;
            }
        }
        if (search == null) {
            SiteSource source = siteSource(level);
            if (source != null && !ECInquisitoriumOutpost.Config.decoderTargetsOutpost()) {
                source = source.withoutOutposts();
            }
            Set<Holder<Biome>> sites = testSites;
            int x = be.getBlockPos().getX();
            int z = be.getBlockPos().getZ();
            search = source != null && sites != null
                    ? SiteSearch.start(source, sites, x, z, values.searchRadius(), values.searchStep())
                    : SiteSearch.start(source, x, z, values.searchRadius(), values.searchStep());
        }
        if (!search.done()) {
            Integer fixed = testBudget;
            HoannaSearchBudget.Grant grant = fixed != null ? new HoannaSearchBudget.Grant(fixed, Long.MAX_VALUE) : HoannaSearchBudget.take(level.getServer());
            if (grant.empty()) {
                return RadiatingChamberBlockEntity.Status.LOCATING;
            }
            int before = search.samples();
            long started = System.nanoTime();
            boolean finished = search.advance(grant.points(), grant.nanos());
            if (fixed == null) {
                // 按实际看的点数和花的时间记账：提前找到的不白扣
                HoannaSearchBudget.charge(search.samples() - before, System.nanoTime() - started);
            }
            if (!finished) {
                return RadiatingChamberBlockEntity.Status.LOCATING;
            }
        }
        BlockPos found = search.found();
        InquisitoriumCoordinatesItem.Target target = found != null
                ? new InquisitoriumCoordinatesItem.Target(ECHoanna.ID, found.getX(), found.getZ())
                : fallback(be.getBlockPos(), values.fallbackDistance(), fallbackRandom(level.getSeed(), be.getBlockPos()));
        be.inventory.extractItem(input.slot(), 1, false);
        be.inventory.setStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT, InquisitoriumCoordinatesItem.create(target));
        awardNearby(level, be.getBlockPos());
        reset();
        level.blockEntityChanged(be.getBlockPos());
        return RadiatingChamberBlockEntity.Status.IDLE;
    }

    /** 找不到暗影腐化之地时：解码处朝 random 掷出的方向 distance 格（维度写海纳尔） */
    public static InquisitoriumCoordinatesItem.Target fallback(BlockPos origin, int distance, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2D;
        return new InquisitoriumCoordinatesItem.Target(ECHoanna.ID, origin.getX() + Mth.floor(Math.cos(angle) * distance),
                origin.getZ() + Mth.floor(Math.sin(angle) * distance));
    }

    /** 退回点用的随机源：只由世界种子和解码处的 x / z 决定（高度不算），同一处解码每次掷出同一个方向；相邻的格子先打散再当种子 */
    public static RandomSource fallbackRandom(long worldSeed, BlockPos origin) {
        long mixed = worldSeed ^ (origin.getX() * 0x9E3779B97F4A7C15L) ^ (origin.getZ() * 0xC2B2AE3D27D4EB4FL);
        return RandomSource.create(RandomSupport.mixStafford13(mixed));
    }

    private static void awardNearby(ServerLevel level, BlockPos pos) {
        List<? extends Player> candidates = testCandidates != null ? testCandidates : level.players();
        Vec3 center = Vec3.atCenterOf(pos);
        for (Player player : candidates) {
            if (player instanceof ServerPlayer serverPlayer && player.level() == level
                    && player.position().distanceToSqr(center) <= GUIDE_RADIUS * GUIDE_RADIUS) {
                PlayerPentacleBlockEntity.award(serverPlayer, ECInquisitorium.ADVANCEMENT_DECODED, ECInquisitorium.ADVANCEMENT_CRITERION);
            }
        }
    }

    /** 海纳尔的群系源和前哨结构集（测试覆盖值优先）；维度被数据包删掉时 null；前哨的结构集被删掉时只找暗影腐化之地 */
    @Nullable
    private static SiteSource siteSource(ServerLevel level) {
        SiteSource override = testSiteSource;
        if (override != null) {
            return override;
        }
        ServerLevel hoanna = level.getServer().getLevel(ECHoanna.LEVEL);
        if (hoanna == null) {
            return null;
        }
        StructureForecast.World forecast = StructureForecast.World.of(hoanna);
        Optional<Holder.Reference<StructureSet>> set = hoanna.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolder(OutpostStructures.SET);
        return SiteSource.of(forecast, set.map(holder -> StructureForecast.targets(forecast, holder)).orElse(List.of()));
    }

    // ------------------------------------------------------------------
    // 存盘（辐射仓的 NBT 里一个子标签；找坐标的进度不存，读档后重找）
    // ------------------------------------------------------------------

    public void save(CompoundTag parent) {
        Input input = tracked;
        if (input == null || progress <= 0) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_SLOT, input.slot());
        tag.putFloat(TAG_TARGET, input.target());
        tag.putInt(TAG_PROGRESS, progress);
        parent.put(TAG, tag);
    }

    public void load(CompoundTag parent) {
        reset();
        if (!parent.contains(TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag tag = parent.getCompound(TAG);
        tracked = new Input(tag.getInt(TAG_SLOT), tag.getFloat(TAG_TARGET));
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
    }

    // ------------------------------------------------------------------
    // 测试覆盖
    // ------------------------------------------------------------------

    /** GameTest 用：解码完成时发进度的候选人（null = 世界里的玩家）。同一段同步代码里设、测、置回 */
    public static void setTestCandidates(@Nullable List<? extends Player> candidates) {
        testCandidates = candidates;
    }

    /** GameTest 用：找坐标用这份群系源（固定种子的海纳尔，{@code gametest/HoannaTestWorld}；null = 真的海纳尔世界） */
    public static void setTestSiteSource(@Nullable SiteSource source) {
        testSiteSource = source;
    }

    /** GameTest 用：找这几种群系，不看 {@code #essentialcraft5:inquisitorium_sites}（给一个群系源里没有的，螺旋就走完整圈；null = 照标签） */
    public static void setTestSites(@Nullable Set<Holder<Biome>> sites) {
        testSites = sites == null ? null : Set.copyOf(sites);
    }

    /**
     * GameTest 用：每次调用直接看这么多个点、不看表、不记全服的账（null = 照全服预算）。同一刻里连着推几十刻的测试要它，
     * 不然第一次调用以后这一刻的预算就用完了，并行的测试也会互相抢。
     */
    public static void setTestBudget(@Nullable Integer perCall) {
        testBudget = perCall;
    }

    /** GameTest 用：把这一刻的全服预算（{@link HoannaSearchBudget}）重新摆成 points 个点、nanos 纳秒（测合计和时间保险）；下一刻照常重新给满 */
    public static void resetBudgetForTest(MinecraftServer server, int points, long nanos) {
        HoannaSearchBudget.resetForTest(server, points, nanos);
    }

    // ------------------------------------------------------------------
    // 找暗影腐化之地
    // ------------------------------------------------------------------

    /**
     * 先用结构预测找 radius 格内最近的一座前哨（{@link SiteSource#findsOutposts} 时），没有再以 (x, z) 为中心螺旋往外找
     * {@code #essentialcraft5:inquisitorium_sites} 群系（照原版 {@code BiomeSource.findClosestBiome3d} 的螺旋，只看一个高度）；
     * 可以分几次 {@link #advance} 做完，两步的采样点数合起来算（{@link #samples}，找前哨的另记 {@link #outpostSamples}）。只读群系噪声和格子哈希，不加载区块。
     */
    public static final class SiteSearch {

        @Nullable
        private final SiteSource source;
        @Nullable
        private final StructureForecast.Search outpostSearch;
        private boolean outpostFound;
        private int outpostSamples;
        private final Set<Holder<Biome>> sites;
        private final Iterator<BlockPos.MutableBlockPos> spiral;
        private final int originX;
        private final int originZ;
        private final int step;
        private boolean done;
        @Nullable
        private BlockPos found;
        private int samples;

        private SiteSearch(@Nullable SiteSource source, Set<Holder<Biome>> sites, int x, int z, int radius, int step) {
            this.source = source;
            this.originX = x;
            this.originZ = z;
            this.step = stepFor(radius, step);
            this.sites = sites;
            this.spiral = BlockPos.spiralAround(BlockPos.ZERO, Math.max(0, Math.floorDiv(radius, this.step)), Direction.EAST, Direction.SOUTH).iterator();
            this.outpostSearch = source != null && source.findsOutposts()
                    ? new StructureForecast.Search(source.forecast(), source.outposts(), new BlockPos(x, SEARCH_Y, z), Math.max(0, radius), chunk -> false)
                    : null;
            this.done = source == null || (sites.isEmpty() && outpostSearch == null);
        }

        /** 实际用的步长：至少 1，半径 ÷ 步长超过 {@value GlitchyCoreDecoder#MAX_RINGS} 圈时加大到正好 {@value GlitchyCoreDecoder#MAX_RINGS} 圈（半径不变，一次搜索的总点数有上限） */
        public static int stepFor(int radius, int step) {
            int base = Math.max(1, step);
            int needed = (int) Math.ceil(Math.max(0, radius) / (double) MAX_RINGS);
            return Math.max(base, needed);
        }

        /** 开始找 {@code #essentialcraft5:inquisitorium_sites}；source 为 null（没有海纳尔）或群系源里根本没有这种群系时，一开始就是「找完了、没找到」 */
        public static SiteSearch start(@Nullable SiteSource source, int x, int z, int radius, int step) {
            Set<Holder<Biome>> sites = source == null ? Set.of() : source.source().possibleBiomes().stream()
                    .filter(biome -> biome.is(ECInquisitorium.INQUISITORIUM_SITES))
                    .collect(Collectors.toUnmodifiableSet());
            return new SiteSearch(source, sites, x, z, radius, step);
        }

        /** 同上，要找的群系自己给（GameTest 量最坏情况的耗时：给一个群系源里没有的群系，螺旋走完整圈） */
        public static SiteSearch start(SiteSource source, Set<Holder<Biome>> sites, int x, int z, int radius, int step) {
            return new SiteSearch(source, Set.copyOf(sites), x, z, radius, step);
        }

        /** 再看最多 budget 个点，不看表；返回找完了没有（找到或螺旋走完） */
        public boolean advance(int budget) {
            return advance(budget, Long.MAX_VALUE);
        }

        /**
         * 再看最多 budget 个点、最多花 nanos 纳秒（每 {@value GlitchyCoreDecoder#SEARCH_CLOCK_CHUNK} 个点看一次表，头一批总会看完，保证每次都有进展）；
         * 返回找完了没有（找到或螺旋走完）。
         */
        public boolean advance(int budget, long nanos) {
            if (done || source == null) {
                done = true;
                return true;
            }
            long started = System.nanoTime();
            int left = budget;
            if (outpostSearch != null && !outpostSearch.done()) {
                int before = outpostSearch.samples();
                boolean finished = outpostSearch.advance(left, nanos);
                int used = outpostSearch.samples() - before;
                outpostSamples += used;
                samples += used;
                left -= used;
                if (!finished) {
                    return false;
                }
                Optional<BlockPos> outpost = outpostSearch.result();
                if (outpost.isPresent()) {
                    found = new BlockPos(outpost.get().getX(), SEARCH_Y, outpost.get().getZ());
                    outpostFound = true;
                    done = true;
                    return true;
                }
                if (sites.isEmpty()) {
                    done = true;
                    return true;
                }
                // 这一次的点数、时间用完了就下一次再找群系（前哨那一步这次已经走完，每次都有进展）
                if (left <= 0 || System.nanoTime() - started >= nanos) {
                    return false;
                }
            }
            int quartY = QuartPos.fromBlock(SEARCH_Y);
            for (int i = 0; i < left; i++) {
                if (i > 0 && i % SEARCH_CLOCK_CHUNK == 0 && System.nanoTime() - started >= nanos) {
                    return false;
                }
                if (!spiral.hasNext()) {
                    done = true;
                    return true;
                }
                BlockPos.MutableBlockPos offset = spiral.next();
                int x = originX + offset.getX() * step;
                int z = originZ + offset.getZ() * step;
                samples++;
                Holder<Biome> biome = source.source().getNoiseBiome(QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z), source.sampler());
                if (sites.contains(biome)) {
                    found = new BlockPos(x, SEARCH_Y, z);
                    done = true;
                    return true;
                }
            }
            return false;
        }

        /** 一口气找完（GameTest 用，不受全服预算管） */
        public SiteSearch finish() {
            while (!advance(FINISH_CHUNK)) {
                // 一次最多 FINISH_CHUNK 个点，直到找到或走完
            }
            return this;
        }

        public boolean done() {
            return done;
        }

        /** 找到的点（y 是 {@value #SEARCH_Y}）：前哨的中心，或暗影腐化之地里的一点；没找到为 null */
        @Nullable
        public BlockPos found() {
            return found;
        }

        /** 找到的是不是一座前哨（不是就是群系点或没找到） */
        public boolean foundOutpost() {
            return outpostFound;
        }

        /** 找前哨那一步看了几个点 */
        public int outpostSamples() {
            return outpostSamples;
        }

        /** 一共看了几个点 */
        public int samples() {
            return samples;
        }
    }
}
