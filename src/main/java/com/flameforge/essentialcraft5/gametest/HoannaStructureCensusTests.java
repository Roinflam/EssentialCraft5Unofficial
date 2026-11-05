package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.flameforge.essentialcraft5.worldgen.hoanna.outpost.OutpostStructures;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 海纳尔结构普查（第十三批 13d-HF 起骨架，13e 加三座新结构；batch13.md §6.3、§7.1、§7.2、§7.4）：6 个种子 × 2 048 × 2 048 区块，
 * 用结构预测（不生成区块）数出每种结构的起点，记数据、只断言宽松的界（6 个种子都跑完、日志都打出来再一起判）：
 * <ul>
 *   <li>数量：城镇对推算 ±30%（城镇 = 格子数 × 陆地占比约 0.70）；沉没城区对每万区块 {@value #SUNKEN_PER_10K}（全图）±30%；
 *       封印宝库对每万陆地区块的推算（随这个种子沙漠 / 恐惧之地占陆地的比例走，见下）、审判庭前哨对每万暗影区块 {@value #OUTPOST_PER_10K_SHADE} ±30%；
 *       这两个数是「群系、排除都过了」的起点，中心那一列是液体被否决的另外记、只要求 ≤ {@value #MAX_LIQUID_SHARE} 的比例（地形决定，推算不了）；</li>
 *   <li>同一种两两切比雪夫 ≥ 8 区块（格子放置的 separation 保证）；地表大结构（城镇、宝库、前哨）不分种类两两 ≥ 8 区块（多集排除保证）；
 *       沉没城区离地表大结构 ≥ {@value #SUNKEN_GAP} 区块（沉城也排它们 8 区块）；</li>
 *   <li>任意 16 × 16 区块窗口里地表大结构（三种合起来）≤ 2 座的占比 ≥ 99.5%（前缀和数全部窗口）。</li>
 * </ul>
 * <b>推算</b>（排除只挡群系对的对方候选，{@code spread_excluding} 13e 起就是这么排）：
 * <ul>
 *   <li>宝库：每 1 024 区块一个候选 × 沙漠 / 恐惧之地占陆地的比例 × 不被城镇挡的比例 {@value #VAULT_TOWN_PASS}。宝库和城镇的格子都是 32 区块、
 *       格子边界重合，宝库挑中的区块在格子里偏 0~15、城镇偏 0~23，同一格里两者每个轴上差 ≤ 8 的概率是 0.615，两个轴 0.378，所以约 38% 被同格的
 *       城镇挡掉（不是按随机位置算的 1 − e^−0.28 ≈ 25%）；前哨挡掉的不到 1%，不计。两种群系占陆地的比例按设计约 39%（9.77 × 0.39 × 0.622 ≈ 2.4），
 *       但各种子差得多（6 个种子 32%~53%，两种群系是成片的大块），所以比例按这个种子自己的群系采样算，不用固定值。</li>
 *   <li>前哨：每 576 区块一个候选，格子 24 和城镇的 32 不对齐，被城镇挡的约 1 − e^−0.28 ≈ 25%（随机位置模拟 28%）：17.4 × 0.75 ≈ 13。</li>
 * </ul>
 * 日志里同时打出「群系对的候选」「中心是液体被否决的」、噪声列的平均耗时，以及沉没城区离最近的地表大结构的距离分布。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaStructureCensusTests {

    private static final String EMPTY = "empty";
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final int SEEDS = 6;
    /** 普查范围（区块）：[−HALF, HALF) */
    private static final int HALF = 1024;
    private static final int SIDE = HALF * 2;
    private static final int WINDOW = 16;
    /** 陆地 / 暗影区块数按每 LAND_STEP 区块一个采样点估 */
    private static final int LAND_STEP = 8;
    /** 地表大结构之间（不分种类）的最小切比雪夫距离：多集排除 8 区块 */
    private static final int SURFACE_GAP = 8;
    /** 沉没城区每万区块（全图）的推算：40 / 20 × 海纳尔之海占比（batch13.md §7.1）；排城镇 / 宝库 / 前哨以后只少海岸边的几座 */
    static final double SUNKEN_PER_10K = 1.9D;
    /** 封印宝库不被同格城镇挡掉的比例（推算见类注释）；0 = 只记不断言 */
    static final double VAULT_TOWN_PASS = 0.622D;
    /** 审判庭前哨每万个暗影区块（群系、排除都过了的，推算见类注释；约每 770 个暗影区块一座）；0 = 只记不断言 */
    static final double OUTPOST_PER_10K_SHADE = 13.0D;
    /** 宝库 / 前哨群系、排除都过了以后，因为中心是液体被否决的最多占多少（只防判定写反，不是设计值） */
    static final double MAX_LIQUID_SHARE = 0.4D;
    /** 沉没城区离地表大结构（城镇、宝库、前哨）起点至少几区块：沉城排它们 8 区块（≤ 8 的不放） */
    static final int SUNKEN_GAP = 9;

    private static final ResourceKey<StructureSet> VAULT_SET = ResourceKey.create(Registries.STRUCTURE_SET, EssentialCraft5.id("sealed_vault"));
    private static final ResourceKey<StructureSet> SUNKEN_SET = ResourceKey.create(Registries.STRUCTURE_SET, EssentialCraft5.id("sunken_district"));

    private HoannaStructureCensusTests() {
    }

    @GameTest(template = EMPTY, timeoutTicks = 1800)
    public static void hoannaStructureCensus(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ChunkPos min = new ChunkPos(-HALF, -HALF);
        ChunkPos max = new ChunkPos(HALF - 1, HALF - 1);
        List<String> report = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (int i = 1; i <= SEEDS; i++) {
            HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server, i * SEED_STEP);
            StructureForecast.World world = HoannaForecastTests.world(hoanna);
            StructureForecast.Target town = HoannaForecastTests.townTarget(server, world);
            StructureForecast.Target vault = InquisitoriumOutpostTests.target(server, world, VAULT_SET);
            StructureForecast.Target outpost = InquisitoriumOutpostTests.outpostTarget(server, world);
            StructureForecast.Target sunken = InquisitoriumOutpostTests.target(server, world, SUNKEN_SET);
            // 陆地、暗影区块数（采样估，采样点同结构预测：区块最小角 + SAMPLE_OFFSET、海平面）
            int samples = 0;
            int land = 0;
            int shade = 0;
            int vaultLand = 0;
            for (int x = -HALF; x < HALF; x += LAND_STEP) {
                for (int z = -HALF; z < HALF; z += LAND_STEP) {
                    samples++;
                    Holder<Biome> biome = world.biomeAt(StructureForecast.sampleX(new ChunkPos(x, z)), StructureForecast.sampleZ(new ChunkPos(x, z)));
                    if (biome.is(ECHoanna.HOANNA_LAND)) {
                        land++;
                    }
                    if (outpost.biomes().test(biome)) {
                        shade++;
                    }
                    if (vault.biomes().test(biome)) {
                        vaultLand++;
                    }
                }
            }
            double total = (double) SIDE * SIDE;
            double landChunks = total * land / samples;
            double shadeChunks = total * shade / samples;

            List<ChunkPos> towns = StructureForecast.predictAll(world, town, min, max);
            long columnsStarted = System.nanoTime();
            List<ChunkPos> vaults = StructureForecast.predictAll(world, vault, min, max);
            List<ChunkPos> outposts = StructureForecast.predictAll(world, outpost, min, max);
            long columnNanos = System.nanoTime() - columnsStarted;
            List<ChunkPos> sunkens = StructureForecast.predictAll(world, sunken, min, max);
            int vaultCandidates = biomeCandidates(world, vault, min, max);
            int outpostCandidates = biomeCandidates(world, outpost, min, max);
            // 群系、排除都过了的（不带否决的预测）；减去带否决的就是只因中心那一列是液体被否决的
            List<ChunkPos> vaultsDry = StructureForecast.predictAll(world, withoutVeto(vault), min, max);
            List<ChunkPos> outpostsDry = StructureForecast.predictAll(world, withoutVeto(outpost), min, max);
            int vaultLiquid = vaultsDry.size() - vaults.size();
            int outpostLiquid = outpostsDry.size() - outposts.size();
            int columns = vaultsDry.size() + outpostsDry.size();

            double townExpected = Math.pow((double) SIDE / town.placement().spacing(), 2) * 0.70D;
            double sunkenRate = sunkens.size() * 10_000D / total;
            double vaultRate = vaultsDry.size() * 10_000D / landChunks;
            double vaultLandShare = land == 0 ? 0D : vaultLand / (double) land;
            double vaultExpected = 10_000D / ((double) vault.placement().spacing() * vault.placement().spacing()) * vaultLandShare * VAULT_TOWN_PASS;
            double outpostRate = shadeChunks <= 0 ? 0 : outpostsDry.size() * 10_000D / shadeChunks;
            double vaultLiquidShare = vaultsDry.isEmpty() ? 0D : vaultLiquid / (double) vaultsDry.size();
            double outpostLiquidShare = outpostsDry.isEmpty() ? 0D : outpostLiquid / (double) outpostsDry.size();
            List<ChunkPos> surface = new ArrayList<>(towns);
            surface.addAll(vaults);
            surface.addAll(outposts);
            int[] histogram = windowHistogram(surface);
            long windows = Arrays.stream(histogram).asLongStream().sum();
            double share = (histogram[0] + histogram[1] + histogram[2]) / (double) windows;
            int[] sunkenNear = nearestHistogram(sunkens, surface);
            int sunkenClose = Arrays.stream(sunkenNear, 0, SUNKEN_GAP).sum();
            String line = String.format(java.util.Locale.ROOT, "种子 %d：陆地 %.0f、暗影 %.0f 区块；城镇 %d（推算 %.0f）；"
                            + "宝库 沙漠 / 恐惧之地占陆地 %.0f%%，群系对的候选 %d → 过了排除 %d（每万陆地区块 %.2f，推算 %.2f）→ 中心液体否决 %d（%.0f%%）后 %d；"
                            + "前哨 候选 %d → 过了排除 %d（每万暗影区块 %.1f）→ 中心液体否决 %d（%.0f%%）后 %d；"
                            + "沉城 %d（每万区块 %.2f），离最近地表大结构 < %d 区块的 %d 座（距离 0..%d+ 的分布 %s）；"
                            + "噪声列 %d 列 %.2f ms/列；地表大结构 16×16 窗口 0..4+ %s，≤ 2 占 %.5f",
                    i, landChunks, shadeChunks, towns.size(), townExpected,
                    vaultLandShare * 100D, vaultCandidates, vaultsDry.size(), vaultRate, vaultExpected, vaultLiquid, vaultLiquidShare * 100D, vaults.size(),
                    outpostCandidates, outpostsDry.size(), outpostRate, outpostLiquid, outpostLiquidShare * 100D, outposts.size(),
                    sunkens.size(), sunkenRate, SUNKEN_GAP, sunkenClose, sunkenNear.length - 1, Arrays.toString(sunkenNear),
                    columns, columns == 0 ? 0D : columnNanos / 1_000_000D / columns, Arrays.toString(histogram), share);
            EssentialCraft5.LOGGER.info("[海纳尔普查] {}", line);
            report.add(line);

            check(failures, towns.size() >= townExpected * 0.7D && towns.size() <= townExpected * 1.3D, "城镇数差了 30% 以上", i);
            check(failures, Math.abs(sunkenRate - SUNKEN_PER_10K) <= SUNKEN_PER_10K * 0.3D, "沉城密度差了 30% 以上", i);
            if (VAULT_TOWN_PASS > 0) {
                check(failures, Math.abs(vaultRate - vaultExpected) <= vaultExpected * 0.3D, "宝库密度差了 30% 以上", i);
            }
            if (OUTPOST_PER_10K_SHADE > 0) {
                check(failures, Math.abs(outpostRate - OUTPOST_PER_10K_SHADE) <= OUTPOST_PER_10K_SHADE * 0.3D, "前哨密度差了 30% 以上", i);
            }
            check(failures, vaultLiquidShare <= MAX_LIQUID_SHARE && outpostLiquidShare <= MAX_LIQUID_SHARE, "中心液体否决的比例太高", i);
            check(failures, !vaults.isEmpty() && !outposts.isEmpty() && !sunkens.isEmpty(), "三座新结构都该有", i);
            check(failures, vaultsDry.containsAll(vaults) && outpostsDry.containsAll(outposts), "带否决的预测应是不带否决的子集", i);
            check(failures, sunkenClose == 0, "沉城离地表大结构太近", i);
            gap(failures, "城镇", towns, town.placement().spacing(), 8, i);
            gap(failures, "宝库", vaults, vault.placement().spacing(), 8, i);
            gap(failures, "前哨", outposts, outpost.placement().spacing(), 8, i);
            gap(failures, "沉城", sunkens, sunken.placement().spacing(), 8, i);
            gap(failures, "地表大结构（不分种类）", surface, WINDOW, SURFACE_GAP, i);
            check(failures, share >= 0.995D, "16 × 16 窗口里地表大结构 ≤ 2 座的只占 " + share, i);
        }
        report.forEach(r -> EssentialCraft5.LOGGER.info("[海纳尔普查] 汇总 {}", r));
        helper.assertTrue(failures.isEmpty(), "普查不过：" + failures + "；明细见日志「[海纳尔普查]」");
        helper.succeed();
    }

    private static void check(List<String> failures, boolean ok, String what, int seed) {
        if (!ok) {
            failures.add("种子 " + seed + " " + what);
        }
    }

    private static void gap(List<String> failures, String name, List<ChunkPos> starts, int bucket, int minGap, int seed) {
        String pair = closestPair(starts, bucket, minGap);
        if (pair != null) {
            failures.add("种子 " + seed + " " + name + "有两处挨得太近：" + pair);
        }
    }

    /**
     * 预测 = 实际（第十三批 13e 集成）：固定种子的海纳尔上，封印宝库、沉没城区、审判庭前哨各取 ±30 格里的全部候选，
     * 群系判定（结构预测的口径：区块最小角 + {@value StructureForecast#SAMPLE_OFFSET}、海平面）过了、结构自己的否决（宝库 / 前哨中心那一列是液体）
     * 也没拦的照真生成都布得出来，其余都布不出来。结构类自己的 {@code biomeAllows} 和生成桩都要落在这一点上、否决和定高取同一列，
     * 否则两边对不上（勘测图、解码器会指到空地）。
     */
    @GameTest(template = EMPTY, timeoutTicks = 1200)
    public static void hoannaForecastMatchesGeneration13e(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        StructureForecast.World world = HoannaForecastTests.world(hoanna);
        DimensionType type = Objects.requireNonNull(hoanna.stem()).type().value();
        LevelHeightAccessor height = LevelHeightAccessor.create(type.minY(), type.height());
        List<String> report = new ArrayList<>();
        for (ResourceKey<StructureSet> key : List.of(VAULT_SET, SUNKEN_SET, OutpostStructures.SET)) {
            StructureForecast.Target target = InquisitoriumOutpostTests.target(server, world, key);
            Structure structure = target.set().value().structures().get(0).structure().value();
            int accepted = 0;
            int rejected = 0;
            int checkedAccepted = 0;
            int vetoed = 0;
            for (int cellX = -30; cellX <= 30; cellX++) {
                for (int cellZ = -30; cellZ <= 30; cellZ++) {
                    ChunkPos chunk = StructureForecast.candidate(world, target, cellX, cellZ);
                    boolean biome = target.biomes().test(world.biomeAt(StructureForecast.sampleX(chunk), StructureForecast.sampleZ(chunk)));
                    if (biome) {
                        accepted++;
                        if (checkedAccepted >= 40) {
                            continue;
                        }
                        checkedAccepted++;
                    } else {
                        rejected++;
                        if (rejected > 200) {
                            continue;
                        }
                    }
                    boolean expected = biome && (target.veto() == null || !target.veto().rejects().test(world, chunk));
                    if (biome && !expected) {
                        vetoed++;
                    }
                    boolean valid = structure.generate(server.registryAccess(), hoanna.generator(), hoanna.biomeSource(), hoanna.randomState(),
                            server.getStructureManager(), hoanna.seed(), chunk, 0, height, structure.biomes()::contains).isValid();
                    helper.assertTrue(valid == expected, key.location() + " 的候选 " + chunk + "：群系 " + biome + "、预测 " + expected + "，真生成 " + valid);
                }
            }
            report.add(key.location() + " 群系对的候选 " + accepted + "（真生成核了 " + checkedAccepted + "，其中中心液体否决 " + vetoed + "）、群系不对的 " + rejected);
            helper.assertTrue(checkedAccepted > 0, "测试前提：" + key.location() + " 在 ±30 格里有群系对的候选");
        }
        EssentialCraft5.LOGGER.info("[海纳尔普查] 预测 = 实际：{}", report);
        helper.succeed();
    }

    /** 同一个目标去掉结构自己的否决 */
    private static StructureForecast.Target withoutVeto(StructureForecast.Target target) {
        return new StructureForecast.Target(target.set(), target.placement(), target.biomes());
    }

    /**
     * 每个起点离 others 里最近的一处多远（切比雪夫，区块）：下标 0 .. 2 × {@value #SUNKEN_GAP}，更远的都算进最后一格。
     * others 按 32 区块分桶，只看相邻 3 × 3 桶（够量到 32 区块）
     */
    private static int[] nearestHistogram(List<ChunkPos> starts, List<ChunkPos> others) {
        int bucket = 32;
        int cap = SUNKEN_GAP * 2;
        Map<Long, List<ChunkPos>> byCell = new HashMap<>();
        for (ChunkPos chunk : others) {
            byCell.computeIfAbsent(ChunkPos.asLong(Math.floorDiv(chunk.x, bucket), Math.floorDiv(chunk.z, bucket)), key -> new ArrayList<>()).add(chunk);
        }
        int[] histogram = new int[cap + 1];
        for (ChunkPos a : starts) {
            int best = cap;
            int cellX = Math.floorDiv(a.x, bucket);
            int cellZ = Math.floorDiv(a.z, bucket);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (ChunkPos b : byCell.getOrDefault(ChunkPos.asLong(cellX + dx, cellZ + dz), List.of())) {
                        best = Math.min(best, Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z)));
                    }
                }
            }
            histogram[best]++;
        }
        return histogram;
    }

    /** 实际普查（tools/gen/hoanna_survey.py）的种子和普查区半边（区块）：区块 中心 − 32 … 中心 + 31 */
    private static final int SURVEY_RADIUS = 32;
    /** 沙漠 / 恐惧之地交界、海两处普查中心（区块；hoanna_survey.py --find-biome 在种子 20260929 上找到的） */
    private static final int[][] SURVEY_CENTERS = {{90, 88}, {20, 28}};

    /**
     * 给实际普查出预测坐标（batch13.md §7.4 第 2 条）：种子 {@value HoannaTestWorld#SEED}（= hoanna_survey.py 的种子），三处普查区——沙漠 / 恐惧之地交界、
     * 海、暗影（在 ±1 500 区块里的预测前哨中挑 64 × 64 区块里暗影占比最高的那座当中心）——里城镇、宝库、前哨、沉城的预测起点，
     * 每处一行 JSON 打进日志（「[海纳尔普查] 实际普查预测」开头），交给 {@code hoanna_survey.py --center X,Z --forecast <json>} 逐个对照实际起点。
     */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void hoannaSurveyForecast13e(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        StructureForecast.World world = HoannaForecastTests.world(hoanna);
        StructureForecast.Target town = HoannaForecastTests.townTarget(server, world);
        StructureForecast.Target vault = InquisitoriumOutpostTests.target(server, world, VAULT_SET);
        StructureForecast.Target outpost = InquisitoriumOutpostTests.outpostTarget(server, world);
        StructureForecast.Target sunken = InquisitoriumOutpostTests.target(server, world, SUNKEN_SET);
        // 暗影那一处：暗影占比最高的预测前哨
        ChunkPos best = null;
        double bestShare = -1D;
        for (ChunkPos site : StructureForecast.predictAll(world, outpost, new ChunkPos(-1500, -1500), new ChunkPos(1500, 1500))) {
            int shade = 0;
            int samples = 0;
            for (int dx = -SURVEY_RADIUS; dx < SURVEY_RADIUS; dx += 4) {
                for (int dz = -SURVEY_RADIUS; dz < SURVEY_RADIUS; dz += 4) {
                    ChunkPos at = new ChunkPos(site.x + dx, site.z + dz);
                    samples++;
                    if (outpost.biomes().test(world.biomeAt(StructureForecast.sampleX(at), StructureForecast.sampleZ(at)))) {
                        shade++;
                    }
                }
            }
            double share = shade / (double) samples;
            if (share > bestShare) {
                bestShare = share;
                best = site;
            }
        }
        helper.assertTrue(best != null, "±1 500 区块里应有预测前哨");
        List<int[]> centers = new ArrayList<>(Arrays.asList(SURVEY_CENTERS));
        centers.add(new int[]{best.x, best.z});
        for (int[] center : centers) {
            ChunkPos min = new ChunkPos(center[0] - SURVEY_RADIUS, center[1] - SURVEY_RADIUS);
            ChunkPos max = new ChunkPos(center[0] + SURVEY_RADIUS - 1, center[1] + SURVEY_RADIUS - 1);
            StringBuilder json = new StringBuilder();
            json.append("{\"center_chunk\": [").append(center[0]).append(", ").append(center[1]).append("], \"center_block\": \"")
                    .append(center[0] * 16 + 8).append(',').append(center[1] * 16 + 8).append("\", \"starts\": {");
            List<StructureForecast.Target> targets = List.of(town, vault, outpost, sunken);
            for (int t = 0; t < targets.size(); t++) {
                StructureForecast.Target target = targets.get(t);
                Structure structure = target.set().value().structures().get(0).structure().value();
                String id = String.valueOf(server.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure));
                json.append(t == 0 ? "" : ", ").append('"').append(id).append("\": [");
                List<ChunkPos> starts = StructureForecast.predictAll(world, target, min, max);
                for (int k = 0; k < starts.size(); k++) {
                    json.append(k == 0 ? "" : ", ").append('[').append(starts.get(k).x).append(", ").append(starts.get(k).z).append(']');
                }
                json.append(']');
            }
            json.append("}}");
            EssentialCraft5.LOGGER.info("[海纳尔普查] 实际普查预测 {}", json);
        }
        EssentialCraft5.LOGGER.info("[海纳尔普查] 暗影普查中心：区块 {}，64 × 64 区块里暗影约占 {}%", best,
                String.format(java.util.Locale.ROOT, "%.0f", bestShare * 100D));
        helper.succeed();
    }

    /** 格子挑中、群系对上的候选数（还没过排除） */
    private static int biomeCandidates(StructureForecast.World world, StructureForecast.Target target, ChunkPos min, ChunkPos max) {
        int spacing = target.placement().spacing();
        int n = 0;
        for (int cellX = Math.floorDiv(min.x, spacing); cellX <= Math.floorDiv(max.x, spacing); cellX++) {
            for (int cellZ = Math.floorDiv(min.z, spacing); cellZ <= Math.floorDiv(max.z, spacing); cellZ++) {
                ChunkPos chunk = StructureForecast.candidate(world, target, cellX, cellZ);
                if (chunk.x < min.x || chunk.x > max.x || chunk.z < min.z || chunk.z > max.z) {
                    continue;
                }
                if (target.biomes().test(world.biomeAt(StructureForecast.sampleX(chunk), StructureForecast.sampleZ(chunk)))) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 两两切比雪夫距离 &lt; minGap 的第一对（按 spacing 分桶，只比相邻 3 × 3 桶，所以 spacing 要 ≥ minGap）；没有返回 null */
    private static String closestPair(List<ChunkPos> starts, int spacing, int minGap) {
        Map<Long, List<ChunkPos>> byCell = new HashMap<>();
        for (ChunkPos chunk : starts) {
            byCell.computeIfAbsent(ChunkPos.asLong(Math.floorDiv(chunk.x, spacing), Math.floorDiv(chunk.z, spacing)), key -> new ArrayList<>()).add(chunk);
        }
        for (ChunkPos a : starts) {
            int cellX = Math.floorDiv(a.x, spacing);
            int cellZ = Math.floorDiv(a.z, spacing);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (ChunkPos b : byCell.getOrDefault(ChunkPos.asLong(cellX + dx, cellZ + dz), List.of())) {
                        if (b.equals(a)) {
                            continue;
                        }
                        int chebyshev = Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
                        if (chebyshev < minGap) {
                            return a + " 和 " + b + " 隔 " + chebyshev;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 普查范围里每个 16 × 16 区块窗口（整个落在范围内的）有几处：下标 0..4，≥ 4 的都算进 4 */
    private static int[] windowHistogram(List<ChunkPos> starts) {
        int stride = SIDE + 1;
        int[] prefix = new int[stride * stride];
        boolean[] hit = new boolean[SIDE * SIDE];
        for (ChunkPos chunk : starts) {
            hit[(chunk.x + HALF) * SIDE + (chunk.z + HALF)] = true;
        }
        for (int x = 0; x < SIDE; x++) {
            int rowSum = 0;
            for (int z = 0; z < SIDE; z++) {
                rowSum += hit[x * SIDE + z] ? 1 : 0;
                prefix[(x + 1) * stride + (z + 1)] = prefix[x * stride + (z + 1)] + rowSum;
            }
        }
        int[] histogram = new int[5];
        for (int x = 0; x + WINDOW <= SIDE; x++) {
            for (int z = 0; z + WINDOW <= SIDE; z++) {
                int count = prefix[(x + WINDOW) * stride + (z + WINDOW)] - prefix[x * stride + (z + WINDOW)]
                        - prefix[(x + WINDOW) * stride + z] + prefix[x * stride + z];
                histogram[Math.min(count, 4)]++;
            }
        }
        return histogram;
    }
}
