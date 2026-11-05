package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.flameforge.essentialcraft5.worldgen.hoanna.town.HoannaTownStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 第十三批 13d-HF 的结构预测（batch13.md §6.3「结构预测」、HS §2.3.1）：
 * <ul>
 *   <li>城镇：固定种子 11 × 11 格，预测起点和真的 {@code findValidGenerationPoint}（加上结构状态的 {@code isStructureChunk}）逐个对，必须完全一致
 *       （13e 起预测也模拟「布局非道路件 ≤ 2 返回空」，{@code HoannaTownStructure.forecastVeto}）；</li>
 *   <li>布局否决：群系、格子都对的候选里布局不成立的占比（就是 13d 预测不模拟布局时的指空率，记日志、&lt; 3%），预测起点里一个都没有；</li>
 *   <li>真海纳尔世界上搜一遍，已加载区块数不变；</li>
 *   <li>增量搜索的结果 = 暴力列出全部起点取最近（含跳过表）。</li>
 * </ul>
 * 两个搜索同一刻分预算在 {@link HoannaPlacementTests#searchBudgetShared}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaForecastTests {

    private static final String EMPTY = "empty";
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final ResourceKey<StructureSet> TOWN_SET = ResourceKey.create(Registries.STRUCTURE_SET, EssentialCraft5.id("hoanna_town"));

    private HoannaForecastTests() {
    }

    /** 离线解出来的海纳尔（不是 ServerLevel）换成预测用的 World */
    public static StructureForecast.World world(HoannaTestWorld.Resolved hoanna) {
        return hoanna.forecast();
    }

    /** 城镇集在这套生成器里的预测目标（只有一个） */
    public static StructureForecast.Target townTarget(MinecraftServer server, StructureForecast.World world) {
        Holder<StructureSet> town = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(TOWN_SET);
        List<StructureForecast.Target> targets = StructureForecast.targets(world, town);
        if (targets.size() != 1) {
            throw new IllegalStateException("城镇集应是海纳尔可能的结构集、放置是 random_spread，实际目标 " + targets.size() + " 个");
        }
        return targets.get(0);
    }

    /** 和结构生成用的随机一样：{@code setLargeFeatureSeed(种子, 区块 x, 区块 z)} */
    static WorldgenRandom largeFeatureRandom(long seed, ChunkPos chunk) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunk.x, chunk.z);
        return random;
    }

    /** 这个起点按原作布局是不是一座成立的城镇（非道路件 &gt; 2） */
    static boolean townLayoutValid(long seed, ChunkPos chunk) {
        return HoannaTownStructure.plan(largeFeatureRandom(seed, chunk), chunk).layout().isValid();
    }

    // ================================================================== 预测 = 实际

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void forecastMatchesTownStarts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server, 2 * SEED_STEP);
        StructureForecast.World world = world(hoanna);
        StructureForecast.Target target = townTarget(server, world);
        Structure structure = target.set().value().structures().get(0).structure().value();
        LevelHeightAccessor height = LevelHeightAccessor.create(hoanna.generator().getMinY(), hoanna.generator().getGenDepth());
        int both = 0;
        int neither = 0;
        int emptyLayouts = 0;
        for (int cellX = -5; cellX <= 5; cellX++) {
            for (int cellZ = -5; cellZ <= 5; cellZ++) {
                ChunkPos chunk = StructureForecast.candidate(world, target, cellX, cellZ);
                boolean predicted = StructureForecast.accepts(world, target, chunk);
                helper.assertTrue(predicted == StructureForecast.isPredictedStart(world, target, chunk), "accepts 和 isPredictedStart 应一致：" + chunk);
                Structure.GenerationContext context = new Structure.GenerationContext(server.registryAccess(), hoanna.generator(), hoanna.biomeSource(),
                        hoanna.randomState(), server.getStructureManager(), hoanna.seed(), chunk, height, structure.biomes()::contains);
                boolean actual = target.placement().isStructureChunk(hoanna.structureState(), chunk.x, chunk.z)
                        && structure.findValidGenerationPoint(context).isPresent();
                if (actual && !predicted) {
                    helper.fail("区块 " + chunk + " 实际会生成城镇，预测却漏了");
                    return;
                }
                if (predicted && !actual) {
                    helper.fail("区块 " + chunk + " 预测有城镇，实际却不生成（布局成立：" + townLayoutValid(hoanna.seed(), chunk) + "）");
                    return;
                }
                if (!predicted && target.biomes().test(world.biomeAt(StructureForecast.sampleX(chunk), StructureForecast.sampleZ(chunk)))
                        && target.placement().isStructureChunk(hoanna.structureState(), chunk.x, chunk.z)) {
                    helper.assertFalse(townLayoutValid(hoanna.seed(), chunk), "群系、格子都对却没预测的只能是布局不成立：" + chunk);
                    emptyLayouts++;
                } else if (predicted) {
                    both++;
                } else {
                    neither++;
                }
                // 不是候选的区块一律不算起点
                helper.assertFalse(StructureForecast.isPredictedStart(world, target, new ChunkPos(chunk.x + 1, chunk.z)), "候选旁边的区块不是起点");
            }
        }
        EssentialCraft5.LOGGER.info("[海纳尔框架] 城镇预测 121 格：都有 {}、都没有 {}（其中群系、格子都对但布局不成立 {}）", both, neither, emptyLayouts);
        helper.assertTrue(both > 20 && neither > 0, "陆地上的城镇和海上的空格都应出现，实际 " + both + " / " + neither);
        helper.succeed();
    }

    /**
     * 布局否决：640 × 640 区块里群系、格子都对的候选，布局不成立（非道路件 ≤ 2，实际返回空）的 &lt; 3%（这就是 13d 预测不模拟布局时的指空率，记日志）；
     * 预测起点（带否决）里一个都没有，而且正好 = 候选去掉这些
     */
    @GameTest(template = EMPTY)
    public static void forecastEmptyTownRate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server, 4 * SEED_STEP);
        StructureForecast.World world = world(hoanna);
        StructureForecast.Target target = townTarget(server, world);
        helper.assertTrue(target.veto() != null && !target.veto().heavy(), "城镇目标带布局否决（纯计算）");
        ChunkPos min = new ChunkPos(-320, -320);
        ChunkPos max = new ChunkPos(319, 319);
        List<ChunkPos> starts = StructureForecast.predictAll(world, target, min, max);
        List<ChunkPos> candidates = StructureForecast.predictAll(world, new StructureForecast.Target(target.set(), target.placement(), target.biomes()), min, max);
        helper.assertTrue(candidates.size() >= 200, "640 × 640 区块里应有 ≥ 200 个群系、格子都对的候选，实际 " + candidates.size());
        int empty = 0;
        for (ChunkPos chunk : candidates) {
            boolean valid = townLayoutValid(hoanna.seed(), chunk);
            if (!valid) {
                empty++;
            }
            helper.assertTrue(valid == starts.contains(chunk), "候选 " + chunk + "：布局成立 " + valid + "，预测 " + starts.contains(chunk));
        }
        helper.assertTrue(starts.size() == candidates.size() - empty, "预测起点 = 候选去掉布局不成立的");
        EssentialCraft5.LOGGER.info("[海纳尔框架] 城镇布局否决：{} 个候选里 {} 个布局不成立（{}%）", candidates.size(), empty,
                String.format(java.util.Locale.ROOT, "%.2f", empty * 100D / candidates.size()));
        helper.assertTrue(empty * 100 < candidates.size() * 3, "布局不成立应 < 3%，实际 " + empty + " / " + candidates.size());
        helper.succeed();
    }

    // ================================================================== 不碰区块

    /** 在真的海纳尔世界上从远处搜一遍（2 048 格半径），前后已加载区块数一样，而且真看了群系 */
    @GameTest(template = EMPTY)
    public static void forecastLoadsNoChunks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel hoanna = server.getLevel(ECHoanna.LEVEL);
        if (hoanna == null) {
            helper.fail("GameTest 服上没有海纳尔世界");
            return;
        }
        int before = hoanna.getChunkSource().getLoadedChunksCount();
        BlockPos center = new BlockPos(123_456, 64, -98_765);
        StructureForecast.Search search = new StructureForecast.Search(hoanna, TOWN_SET, center, 2048, chunk -> false).finish();
        int after = hoanna.getChunkSource().getLoadedChunksCount();
        helper.assertTrue(search.done() && search.samples() > 0, "应找完、看过群系，实际看了 " + search.samples() + " 个点");
        helper.assertTrue(before == after, "搜索不该加载区块：前 " + before + "、后 " + after);
        helper.assertTrue(search.result().map(pos -> pos.distSqr(new BlockPos(center.getX(), pos.getY(), center.getZ())) <= 2048L * 2048L).orElse(true),
                "结果应在半径内：" + search.result());
        helper.succeed();
    }

    // ================================================================== 搜索 = 暴力

    /** 三个种子、三个中心：增量搜索（不限预算地走完）的最近一处 = 暴力列出全部起点取最近；把它记进跳过表后 = 第二近的 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void forecastSearchFindsNearest(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int radius = 2048;
        int[][] centers = {{0, 0}, {2500, -1300}, {-7777, 4321}};
        for (int i = 0; i < centers.length; i++) {
            HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server, (6 + i) * SEED_STEP);
            StructureForecast.World world = world(hoanna);
            StructureForecast.Target target = townTarget(server, world);
            BlockPos center = new BlockPos(centers[i][0], 64, centers[i][1]);
            ChunkPos middle = new ChunkPos(center);
            int reach = radius / 16 + 2;
            List<ChunkPos> all = StructureForecast.predictAll(world, target, new ChunkPos(middle.x - reach, middle.z - reach),
                    new ChunkPos(middle.x + reach, middle.z + reach));
            ChunkPos nearest = nearest(all, center, radius, Set.of());
            StructureForecast.Search search = new StructureForecast.Search(world, List.of(target), center, radius, chunk -> false).finish();
            Optional<BlockPos> found = search.result();
            if (!sameDistance(found, nearest, center)) {
                helper.fail("中心 " + center + "：搜索得 " + found + "，暴力最近 " + nearest);
                return;
            }
            if (nearest == null) {
                continue;
            }
            Set<ChunkPos> skipped = new HashSet<>(Set.of(nearest));
            ChunkPos second = nearest(all, center, radius, skipped);
            StructureForecast.Search again = new StructureForecast.Search(world, List.of(target), center, radius, skipped::contains).finish();
            if (!sameDistance(again.result(), second, center)) {
                helper.fail("中心 " + center + " 跳过 " + nearest + "：搜索得 " + again.result() + "，暴力第二近 " + second);
                return;
            }
            // 半径内的城镇格最多 (2 × 2048 / 512 + 3)² 个，每格最多看一个点
            helper.assertTrue(search.samples() <= 121, "看的点数应 ≤ 121，实际 " + search.samples());
        }
        helper.succeed();
    }

    private static ChunkPos nearest(List<ChunkPos> starts, BlockPos center, int radius, Set<ChunkPos> skipped) {
        ChunkPos best = null;
        long bestDist = (long) radius * radius;
        for (ChunkPos chunk : starts) {
            if (skipped.contains(chunk)) {
                continue;
            }
            long dist = distSq(chunk, center);
            if (dist <= bestDist && (best == null || dist < distSq(best, center))) {
                best = chunk;
            }
        }
        return best;
    }

    private static long distSq(ChunkPos chunk, BlockPos center) {
        long dx = chunk.getMiddleBlockX() - center.getX();
        long dz = chunk.getMiddleBlockZ() - center.getZ();
        return dx * dx + dz * dz;
    }

    /** 两边都没有，或距离相同（同距离的两处谁都算对） */
    private static boolean sameDistance(Optional<BlockPos> found, ChunkPos expected, BlockPos center) {
        if (expected == null || found.isEmpty()) {
            return expected == null && found.isEmpty();
        }
        return distSq(new ChunkPos(found.get()), center) == distSq(expected, center);
    }
}
