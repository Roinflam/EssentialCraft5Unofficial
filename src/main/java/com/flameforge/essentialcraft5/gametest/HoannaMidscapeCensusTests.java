package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaFeatures;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.HoannaMidscape;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.CellSpread;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 中景观与里程碑普查（第十三批 13d-H1b，batch13.md §6.1、§7.1、§7.2 第 2 条、§7.4 第 1 条）：纯哈希 + 群系采样，不生成区块。
 * 6 个种子（{@link HoannaTestWorld#resolve}）× 2 048 × 2 048 区块：
 * <ul>
 *   <li>中景观：每个 {@code cell_spread} 14 / 7 格挑中的区块，在区块中心、海平面那一层采群系，按 {@link HoannaMidscape#kindFor} 定种类；
 *       任意 16 × 16 区块窗口 ≤ 4 处必须 100%（构造保证）；每万陆地区块约 25 处（±30%）；</li>
 *   <li>里程碑：{@code cell_spread} 24 / 12 格挑中、中心是陆地群系的区块，每万陆地区块约 17 处（±30%）。</li>
 * </ul>
 * 陆地区块数按每 8 区块一个采样点估（约 6.6 万点）。城镇引用、落在水上 / 树上这些要看真地形的条件不算（城镇只占不到 0.5% 的区块），
 * 所以这里是上限口径；实际普查（tools/gen/hoanna_survey.py）数真方块。参数从注册表里的 placed feature 读（和 JSON 一致）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaMidscapeCensusTests {

    private static final String EMPTY = "empty";
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final int SEEDS = 6;
    private static final int HALF = 1024;
    private static final int SIDE = HALF * 2;
    private static final int WINDOW = 16;
    private static final int LAND_STEP = 8;
    /** 每万陆地区块的推算（batch13.md §7.1） */
    private static final double MIDSCAPE_PER_10K = 25D;
    private static final double MILESTONE_PER_10K = 10_000D / (24 * 24);

    private HoannaMidscapeCensusTests() {
    }

    @GameTest(template = EMPTY, timeoutTicks = 1200)
    public static void hoannaMidscapeCensus(GameTestHelper helper) {
        Registry<PlacedFeature> placed = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        CellSpread midscape = cellOf(placed.get(ECHoannaFeatures.HOANNA_MIDSCAPE));
        CellSpread milestone = cellOf(placed.get(ECHoannaFeatures.HOANNA_MILESTONE));
        helper.assertTrue(midscape != null && milestone != null, "hoanna_midscape / hoanna_milestone 的第一条放置修饰应是 cell_spread");
        int qy = QuartPos.fromBlock(63);
        List<String> report = new ArrayList<>();
        for (int i = 1; i <= SEEDS; i++) {
            HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(helper.getLevel().getServer(), i * SEED_STEP);
            BiomeSource source = hoanna.biomeSource();
            Climate.Sampler sampler = hoanna.randomState().sampler();
            long seed = hoanna.seed();
            // 陆地区块数
            int samples = 0;
            int landSamples = 0;
            for (int x = -HALF; x < HALF; x += LAND_STEP) {
                for (int z = -HALF; z < HALF; z += LAND_STEP) {
                    samples++;
                    if (biomeAt(source, sampler, x, z, qy).is(ECHoanna.HOANNA_LAND)) {
                        landSamples++;
                    }
                }
            }
            double landChunks = (double) SIDE * SIDE * landSamples / samples;
            // 中景观
            boolean[] hit = new boolean[SIDE * SIDE];
            Map<HoannaMidscape.Kind, Integer> kinds = new EnumMap<>(HoannaMidscape.Kind.class);
            int total = 0;
            for (ChunkPos chunk : picks(seed, midscape)) {
                HoannaMidscape.Kind kind = HoannaMidscape.kindFor(biomeAt(source, sampler, chunk.x, chunk.z, qy), chunk.x, chunk.z);
                if (kind != null) {
                    kinds.merge(kind, 1, Integer::sum);
                    hit[(chunk.x + HALF) * SIDE + (chunk.z + HALF)] = true;
                    total++;
                }
            }
            int[] histogram = windowHistogram(hit);
            // 里程碑
            int milestones = 0;
            for (ChunkPos chunk : picks(seed, milestone)) {
                if (biomeAt(source, sampler, chunk.x, chunk.z, qy).is(ECHoanna.HOANNA_LAND)) {
                    milestones++;
                }
            }
            double midscapeRate = total * 10_000D / landChunks;
            double milestoneRate = milestones * 10_000D / landChunks;
            String line = String.format("种子 %d：陆地 %.0f 区块，中景观 %d 处（%s，每万陆地区块 %.1f），16×16 窗口 0..4+ 处 %s；里程碑 %d 处（每万陆地区块 %.1f）",
                    i, landChunks, total, kinds, midscapeRate, Arrays.toString(histogram), milestones, milestoneRate);
            EssentialCraft5.LOGGER.info("[海纳尔普查] {}", line);
            report.add(line);
            helper.assertTrue(histogram[4] == 0 || maxInWindow(hit) <= 4, "种子 " + i + "：有 16 × 16 窗口里中景观超过 4 处 " + line);
            helper.assertTrue(Math.abs(midscapeRate - MIDSCAPE_PER_10K) <= MIDSCAPE_PER_10K * 0.3D, "种子 " + i + "：中景观密度差了 30% 以上 " + line);
            helper.assertTrue(Math.abs(milestoneRate - MILESTONE_PER_10K) <= MILESTONE_PER_10K * 0.3D, "种子 " + i + "：里程碑密度差了 30% 以上 " + line);
            helper.assertTrue(kinds.size() == 3, "种子 " + i + "：三种中景观都该有 " + line);
            // 找最近一处（展示厅、排查用）：找到的就是格子挑中、群系对上的那种
            for (HoannaMidscape.Kind kind : HoannaMidscape.Kind.values()) {
                ChunkPos near = HoannaMidscape.nearest(seed, source, sampler, 63, new ChunkPos(0, 0), kind, 40);
                helper.assertTrue(near != null && CellSpread.isCellChunk(seed, near.x, near.z, midscape.spacing(), midscape.separation(), midscape.salt())
                        && HoannaMidscape.kindFor(biomeAt(source, sampler, near.x, near.z, qy), near.x, near.z) == kind, "种子 " + i + "：找最近的 " + kind + " 不对：" + near);
            }
        }
        helper.succeed();
    }

    private static Holder<Biome> biomeAt(BiomeSource source, Climate.Sampler sampler, int chunkX, int chunkZ, int qy) {
        return source.getNoiseBiome(QuartPos.fromBlock(chunkX * 16 + 8), qy, QuartPos.fromBlock(chunkZ * 16 + 8), sampler);
    }

    private static CellSpread cellOf(PlacedFeature feature) {
        return feature != null && !feature.placement().isEmpty() && feature.placement().get(0) instanceof CellSpread cell ? cell : null;
    }

    /** 普查范围里每一格挑中的区块（落在范围内的） */
    private static List<ChunkPos> picks(long seed, CellSpread cell) {
        List<ChunkPos> out = new ArrayList<>();
        int spacing = cell.spacing();
        int minCell = Math.floorDiv(-HALF, spacing);
        int maxCell = Math.floorDiv(HALF - 1, spacing);
        for (int cx = minCell; cx <= maxCell; cx++) {
            for (int cz = minCell; cz <= maxCell; cz++) {
                ChunkPos pick = CellSpread.pick(seed, cx, cz, spacing, cell.separation(), cell.salt());
                if (pick.x >= -HALF && pick.x < HALF && pick.z >= -HALF && pick.z < HALF) {
                    out.add(pick);
                }
            }
        }
        return out;
    }

    /** 每个 16 × 16 窗口里有几处：下标 0..4，≥ 4 的都算进 4 */
    private static int[] windowHistogram(boolean[] hit) {
        int[] prefix = prefix(hit);
        int stride = SIDE + 1;
        int[] histogram = new int[5];
        for (int x = 0; x + WINDOW <= SIDE; x++) {
            for (int z = 0; z + WINDOW <= SIDE; z++) {
                histogram[Math.min(count(prefix, stride, x, z), 4)]++;
            }
        }
        return histogram;
    }

    /** 窗口里最多几处 */
    private static int maxInWindow(boolean[] hit) {
        int[] prefix = prefix(hit);
        int stride = SIDE + 1;
        int max = 0;
        for (int x = 0; x + WINDOW <= SIDE; x++) {
            for (int z = 0; z + WINDOW <= SIDE; z++) {
                max = Math.max(max, count(prefix, stride, x, z));
            }
        }
        return max;
    }

    private static int[] prefix(boolean[] hit) {
        int stride = SIDE + 1;
        int[] prefix = new int[stride * stride];
        for (int x = 0; x < SIDE; x++) {
            int rowSum = 0;
            for (int z = 0; z < SIDE; z++) {
                rowSum += hit[x * SIDE + z] ? 1 : 0;
                prefix[(x + 1) * stride + (z + 1)] = prefix[x * stride + (z + 1)] + rowSum;
            }
        }
        return prefix;
    }

    private static int count(int[] prefix, int stride, int x, int z) {
        return prefix[(x + WINDOW) * stride + (z + WINDOW)] - prefix[x * stride + (z + WINDOW)]
                - prefix[(x + WINDOW) * stride + z] + prefix[x * stride + z];
    }
}
