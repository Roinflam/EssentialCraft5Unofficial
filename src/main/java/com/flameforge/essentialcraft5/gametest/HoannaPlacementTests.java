package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.HoannaSearchBudget;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.CellSpread;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.RadiationHotspot;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.SpreadExcludingPlacement;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 第十三批 13d-HF 的放置修饰、多集排除、全服找地方预算（batch13.md §6.1、§6.4「多集排除」、§7.2、§7.4 第 5 条）：
 * <ul>
 *   <li>{@code cell_spread}：和原版 random_spread 同参数逐格一致、挑中的区块两两 ≥ separation + 1（10 万格）、14 / 7 时任意 16 × 16 区块窗口 ≤ 4；codec 校验；</li>
 *   <li>{@code radiation_hotspot}：和 {@link Radiation#chunkBase} 一致（走真的 PlacementContext）；</li>
 *   <li>{@code spread_excluding}：排除城镇集 8 区块时，城镇格 8 区块内的一律拒、别的照 random_spread；codec；</li>
 *   <li>{@link HoannaSearchBudget}：两个结构预测同一刻分 256 点、时间保险。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaPlacementTests {

    private static final String EMPTY = "empty";
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final ResourceKey<StructureSet> TOWN_SET = ResourceKey.create(Registries.STRUCTURE_SET, EssentialCraft5.id("hoanna_town"));

    private HoannaPlacementTests() {
    }

    // ================================================================== cell_spread

    /**
     * 10 万格（两组参数各 316 × 316）：{@link CellSpread#pick} 和原版 {@code getPotentialStructureChunk}（LINEAR、同 salt）逐格一致；
     * 相邻格（含对角）挑中的区块切比雪夫距离 ≥ separation + 1；{@link CellSpread#isCellChunk} 只对挑中的那块为真。
     */
    @GameTest(template = EMPTY)
    public static void cellSpreadSpacing(GameTestHelper helper) {
        int[][] params = {{14, 7}, {24, 12}};
        int side = 316;
        int checked = 0;
        for (int p = 0; p < params.length; p++) {
            int spacing = params[p][0];
            int separation = params[p][1];
            long seed = (p + 1) * SEED_STEP;
            int salt = 202610261 + p;
            RandomSpreadStructurePlacement vanilla = new RandomSpreadStructurePlacement(spacing, separation, RandomSpreadType.LINEAR, salt);
            ChunkPos[][] picks = new ChunkPos[side][side];
            for (int i = 0; i < side; i++) {
                for (int j = 0; j < side; j++) {
                    int cellX = i - side / 2;
                    int cellZ = j - side / 2;
                    ChunkPos pick = CellSpread.pick(seed, cellX, cellZ, spacing, separation, salt);
                    ChunkPos expected = vanilla.getPotentialStructureChunk(seed, cellX * spacing, cellZ * spacing);
                    if (!pick.equals(expected)) {
                        helper.fail("第 (" + cellX + ", " + cellZ + ") 格和原版不一致：" + pick + " ≠ " + expected + "（" + spacing + " / " + separation + "）");
                        return;
                    }
                    picks[i][j] = pick;
                    checked++;
                }
            }
            for (int i = 0; i < side; i++) {
                for (int j = 0; j < side; j++) {
                    ChunkPos a = picks[i][j];
                    int[][] neighbours = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
                    for (int[] d : neighbours) {
                        int ni = i + d[0];
                        int nj = j + d[1];
                        if (ni < 0 || nj < 0 || ni >= side || nj >= side) {
                            continue;
                        }
                        ChunkPos b = picks[ni][nj];
                        int chebyshev = Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
                        if (chebyshev < separation + 1) {
                            helper.fail("挑中的区块 " + a + " 和 " + b + " 只隔 " + chebyshev + "（应 ≥ " + (separation + 1) + "）");
                            return;
                        }
                    }
                }
            }
            // 只对挑中的那块为真：抽几格，格里的每个区块逐个判
            for (int k = 0; k < 20; k++) {
                ChunkPos pick = picks[k * 13 % side][k * 29 % side];
                int baseX = Math.floorDiv(pick.x, spacing) * spacing;
                int baseZ = Math.floorDiv(pick.z, spacing) * spacing;
                int hits = 0;
                for (int dx = 0; dx < spacing; dx++) {
                    for (int dz = 0; dz < spacing; dz++) {
                        if (CellSpread.isCellChunk(seed, baseX + dx, baseZ + dz, spacing, separation, salt)) {
                            hits++;
                            helper.assertTrue(baseX + dx == pick.x && baseZ + dz == pick.z, "isCellChunk 为真的应只有挑中的 " + pick);
                        }
                    }
                }
                helper.assertTrue(hits == 1, "每格恰好一个区块为真，实际 " + hits);
            }
        }
        helper.assertTrue(checked >= 100_000, "应核 ≥ 10 万格，实际 " + checked);
        helper.succeed();
    }

    /** 14 / 7（中景观共用的格子）：随机抽 10 万个 16 × 16 区块窗口，每个窗口里挑中的区块 ≤ 4（构造保证） */
    @GameTest(template = EMPTY)
    public static void cellSpreadWindowAtMostFour(GameTestHelper helper) {
        int spacing = 14;
        int separation = 7;
        int salt = 202610261;
        long seed = 7 * SEED_STEP;
        RandomSource random = RandomSource.create(11 * SEED_STEP);
        int max = 0;
        int windows = 100_000;
        int[] histogram = new int[10];
        for (int w = 0; w < windows; w++) {
            int x0 = random.nextInt(200_000) - 100_000;
            int z0 = random.nextInt(200_000) - 100_000;
            int count = 0;
            for (int cx = Math.floorDiv(x0, spacing); cx <= Math.floorDiv(x0 + 15, spacing); cx++) {
                for (int cz = Math.floorDiv(z0, spacing); cz <= Math.floorDiv(z0 + 15, spacing); cz++) {
                    ChunkPos pick = CellSpread.pick(seed, cx, cz, spacing, separation, salt);
                    if (pick.x >= x0 && pick.x <= x0 + 15 && pick.z >= z0 && pick.z <= z0 + 15) {
                        count++;
                    }
                }
            }
            histogram[Math.min(count, histogram.length - 1)]++;
            max = Math.max(max, count);
        }
        EssentialCraft5.LOGGER.info("[海纳尔框架] cell_spread 14 / 7：{} 个 16×16 区块窗口里挑中数的分布 {}", windows, java.util.Arrays.toString(histogram));
        helper.assertTrue(max <= 4, "16 × 16 区块窗口里最多 4 处，实际 " + max);
        helper.succeed();
    }

    /** codec：合法的读得出来、走真的 PlacementContext 和纯函数一致；spacing ≤ separation、spacing 太大都报错 */
    @GameTest(template = EMPTY)
    public static void cellSpreadCodec(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlacementModifier parsed = parse(level, "{\"type\":\"essentialcraft5:cell_spread\",\"spacing\":14,\"separation\":7,\"salt\":202610261}").result().orElse(null);
        helper.assertTrue(parsed instanceof CellSpread spread && spread.spacing() == 14 && spread.separation() == 7 && spread.salt() == 202610261,
                "cell_spread 应能读出来：" + parsed);
        CellSpread spread = (CellSpread) parsed;
        helper.assertTrue(parse(level, "{\"type\":\"essentialcraft5:cell_spread\",\"spacing\":7,\"separation\":7,\"salt\":1}").error().isPresent(),
                "spacing ≤ separation 应报错");
        helper.assertTrue(parse(level, "{\"type\":\"essentialcraft5:cell_spread\",\"spacing\":5000,\"separation\":7,\"salt\":1}").error().isPresent(),
                "spacing 超过 4096 应报错");
        PlacementContext context = new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.empty());
        RandomSource random = RandomSource.create(1L);
        long seed = level.getSeed();
        int placed = 0;
        for (int cx = -60; cx < 60; cx++) {
            for (int cz = -60; cz < 60; cz++) {
                BlockPos origin = new BlockPos(cx * 16, level.getMinBuildHeight(), cz * 16);
                boolean viaContext = spread.getPositions(context, random, origin).findAny().isPresent();
                boolean viaFunction = CellSpread.isCellChunk(seed, cx, cz, 14, 7, 202610261);
                if (viaContext != viaFunction) {
                    helper.fail("区块 (" + cx + ", " + cz + ") 放置修饰和纯函数不一致");
                    return;
                }
                placed += viaContext ? 1 : 0;
            }
        }
        // 120 × 120 区块、每 196 区块一处，约 73 处
        helper.assertTrue(placed >= 50 && placed <= 100, "120 × 120 区块里应约 73 处，实际 " + placed);
        helper.succeed();
    }

    // ================================================================== radiation_hotspot

    /** radiation_hotspot {min_base} 只在 {@link Radiation#chunkBase} ≥ min_base 的区块放（纯函数和真的 PlacementContext 都核），JSON 读得出来 */
    @GameTest(template = EMPTY)
    public static void radiationHotspotMatchesChunkBase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlacementModifier parsed = parse(level, "{\"type\":\"essentialcraft5:radiation_hotspot\",\"min_base\":4}").result().orElse(null);
        helper.assertTrue(parsed instanceof RadiationHotspot hotspot && hotspot.minBase() == 4, "radiation_hotspot 应能读出来：" + parsed);
        PlacementContext context = new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.empty());
        RandomSource random = RandomSource.create(2L);
        int[] atLeast = new int[7];
        int total = 0;
        for (int minBase = 0; minBase <= 6; minBase++) {
            RadiationHotspot filter = new RadiationHotspot(minBase);
            for (int cx = -40; cx < 40; cx++) {
                for (int cz = -40; cz < 40; cz++) {
                    boolean expected = Radiation.chunkBase(cx, cz) >= minBase;
                    BlockPos origin = new BlockPos(cx * 16 + 3, 64, cz * 16 + 9);
                    boolean viaContext = filter.getPositions(context, random, origin).findAny().isPresent();
                    if (filter.test(cx, cz) != expected || viaContext != expected) {
                        helper.fail("区块 (" + cx + ", " + cz + ") 底数 " + Radiation.chunkBase(cx, cz) + "，min_base " + minBase + " 判错");
                        return;
                    }
                    if (expected) {
                        atLeast[minBase]++;
                    }
                    if (minBase == 0) {
                        total++;
                    }
                }
            }
        }
        helper.assertTrue(atLeast[0] == total && atLeast[6] == 0, "min_base 0 全放、6 一个都不放");
        // 原作是 java.util.Random 头一个 nextInt(6)，相邻种子不算均匀，只记分布、断言两头
        EssentialCraft5.LOGGER.info("[海纳尔框架] radiation_hotspot：{} 个区块里底数 ≥ 0..6 的个数 {}", total, java.util.Arrays.toString(atLeast));
        helper.assertTrue(atLeast[4] > 0 && atLeast[4] < total && atLeast[5] > 0, "底数 4、5 的区块都应有，也不是全部");
        helper.succeed();
    }

    // ================================================================== spread_excluding

    /**
     * 用固定种子海纳尔的结构状态：spread_excluding 32 / 16、排除城镇集 8 区块。挑中的格和最近的「群系对的」城镇格（城镇自己的格子独立算，
     * 群系按城镇的采样点）切比雪夫距离 ≤ 8 的一律拒，别的和同参数的 random_spread 一样放。两种都要出现；城镇格落在海里（不会生成城镇）的不挡人，这种也要出现。
     */
    @GameTest(template = EMPTY)
    public static void spreadExcludingRejectsNearTown(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server, 3 * SEED_STEP);
        Holder<StructureSet> town = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(TOWN_SET);
        RandomSpreadStructurePlacement townPlacement = (RandomSpreadStructurePlacement) town.value().placement();
        int salt = 202611011;
        SpreadExcludingPlacement placement = new SpreadExcludingPlacement(32, 16, salt, List.of(new StructurePlacement.ExclusionZone(town, 8)));
        RandomSpreadStructurePlacement plain = new RandomSpreadStructurePlacement(32, 16, RandomSpreadType.LINEAR, salt);
        long seed = hoanna.seed();
        Set<ChunkPos> towns = new HashSet<>();
        Set<ChunkPos> seaTowns = new HashSet<>();
        for (int cx = -12; cx <= 12; cx++) {
            for (int cz = -12; cz <= 12; cz++) {
                ChunkPos cell = townPlacement.getPotentialStructureChunk(seed, cx * townPlacement.spacing(), cz * townPlacement.spacing());
                (SpreadExcludingPlacement.otherBiomeAllows(hoanna.structureState(), town, cell.x, cell.z) ? towns : seaTowns).add(cell);
            }
        }
        int rejected = 0;
        int accepted = 0;
        int nearSeaTown = 0;
        for (int cx = -10; cx <= 10; cx++) {
            for (int cz = -10; cz <= 10; cz++) {
                ChunkPos chunk = plain.getPotentialStructureChunk(seed, cx * 32, cz * 32);
                boolean near = towns.stream().anyMatch(t -> Math.abs(t.x - chunk.x) <= 8 && Math.abs(t.z - chunk.z) <= 8);
                boolean placed = placement.isStructureChunk(hoanna.structureState(), chunk.x, chunk.z);
                helper.assertTrue(placement.isCellChunk(seed, chunk.x, chunk.z) && plain.isStructureChunk(hoanna.structureState(), chunk.x, chunk.z),
                        "同参数的格子应挑中同一块 " + chunk);
                if (placed == near) {
                    helper.fail("区块 " + chunk + (near ? " 离城镇 8 区块内，应拒" : " 离城镇超过 8 区块，应放"));
                    return;
                }
                helper.assertFalse(placement.isStructureChunk(hoanna.structureState(), chunk.x + 1, chunk.z), "没挑中的区块一律不放");
                if (near) {
                    rejected++;
                } else {
                    accepted++;
                    if (seaTowns.stream().anyMatch(t -> Math.abs(t.x - chunk.x) <= 8 && Math.abs(t.z - chunk.z) <= 8)) {
                        nearSeaTown++;
                    }
                }
            }
        }
        helper.assertTrue(rejected > 0 && accepted > 0, "拒的和放的都应出现，实际拒 " + rejected + "、放 " + accepted);
        helper.assertTrue(nearSeaTown > 0, "旁边只有群系不对的城镇格、照样放的应出现");
        helper.succeed();
    }

    /** spread_excluding 的 JSON：读得出来（排除列表指向城镇集）、能写回去；spacing ≤ separation、chunk_count 超过 16 报错 */
    @GameTest(template = EMPTY)
    public static void spreadExcludingCodec(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        String json = "{\"type\":\"essentialcraft5:spread_excluding\",\"spacing\":32,\"separation\":16,\"salt\":202611012,"
                + "\"exclusion_zones\":[{\"other_set\":\"essentialcraft5:hoanna_town\",\"chunk_count\":8}]}";
        DataResult<StructurePlacement> result = StructurePlacement.CODEC.parse(ops, JsonParser.parseString(json));
        StructurePlacement parsed = result.result().orElse(null);
        helper.assertTrue(parsed instanceof SpreadExcludingPlacement placement && placement.spacing() == 32 && placement.separation() == 16
                        && placement.exclusionZones().size() == 1 && placement.exclusionZones().get(0).otherSet().is(TOWN_SET)
                        && placement.exclusionZones().get(0).chunkCount() == 8,
                "spread_excluding 应能读出来：" + result);
        DataResult<JsonElement> encoded = StructurePlacement.CODEC.encodeStart(ops, parsed);
        helper.assertTrue(encoded.result().isPresent() && encoded.result().get().toString().contains("exclusion_zones"), "应能写回 JSON：" + encoded);
        String bad = "{\"type\":\"essentialcraft5:spread_excluding\",\"spacing\":16,\"separation\":16,\"salt\":1,\"exclusion_zones\":[]}";
        helper.assertTrue(StructurePlacement.CODEC.parse(ops, JsonParser.parseString(bad)).error().isPresent(), "spacing ≤ separation 应报错");
        String far = "{\"type\":\"essentialcraft5:spread_excluding\",\"spacing\":32,\"separation\":16,\"salt\":1,"
                + "\"exclusion_zones\":[{\"other_set\":\"essentialcraft5:hoanna_town\",\"chunk_count\":17}]}";
        helper.assertTrue(StructurePlacement.CODEC.parse(ops, JsonParser.parseString(far)).error().isPresent(), "chunk_count 超过 16 应报错（同原版）");
        helper.succeed();
    }

    // ================================================================== 全服预算

    /**
     * 两个结构预测同一刻都在找（群系永远对不上，一直走）：先到的看满 256 个点、后到的这一刻一个不看；下一刻后到的先走。
     * 时间保险：这一刻时间用完时头一批（32 格）照走、之后就停。
     */
    @GameTest(template = EMPTY)
    public static void searchBudgetShared(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server, 5 * SEED_STEP);
        StructureForecast.World world = HoannaForecastTests.world(hoanna);
        Holder<StructureSet> town = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(TOWN_SET);
        StructureForecast.Target never = new StructureForecast.Target(town, (RandomSpreadStructurePlacement) town.value().placement(), biome -> false);
        StructureForecast.Search first = new StructureForecast.Search(world, List.of(never), BlockPos.ZERO, 40_000, chunk -> false);
        StructureForecast.Search second = new StructureForecast.Search(world, List.of(never), new BlockPos(5000, 64, 5000), 40_000, chunk -> false);
        int budget = HoannaSearchBudget.POINTS_PER_TICK;
        HoannaSearchBudget.resetForTest(server, budget, Long.MAX_VALUE);
        first.step(server);
        second.step(server);
        helper.assertTrue(first.samples() == budget && second.samples() == 0,
                "同一刻合计 " + budget + " 个点：先到的看满、后到的不看，实际 " + first.samples() + " / " + second.samples());
        helper.assertTrue(HoannaSearchBudget.take(server).empty(), "这一刻用完了");
        HoannaSearchBudget.resetForTest(server, budget, Long.MAX_VALUE);
        second.step(server);
        first.step(server);
        helper.assertTrue(first.samples() == budget && second.samples() == budget, "下一刻后到的先走，实际 " + first.samples() + " / " + second.samples());
        HoannaSearchBudget.resetForTest(server, budget, 1L);
        first.step(server);
        second.step(server);
        helper.assertTrue(first.samples() > budget && first.samples() <= budget + 32 && second.samples() == budget,
                "时间用完：头一批照走、另一个这一刻不走，实际 " + first.samples() + " / " + second.samples());
        helper.assertFalse(first.done() || second.done(), "群系永远对不上，还没走完");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 工具

    private static DataResult<PlacementModifier> parse(ServerLevel level, String json) {
        return PlacementModifier.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), JsonParser.parseString(json));
    }
}
