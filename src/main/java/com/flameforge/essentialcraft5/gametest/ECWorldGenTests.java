package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECWorldGen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 矿石模块的测试：掉落表（普通 / 时运 / 精准采集）、经验、世界生成数据（矿物特征的母岩、矿脉大小、生物群系修改器）。
 * 掉落表和经验都是随机的，按「范围不越界 + 每个取值的频率在理论值 ±6 个标准差内」判定：
 * 误报概率约十亿分之一，而如果公式被换成原版 uniform_bonus_count 那种梯形分布，时运档的两端频率会差出去，测得出来。
 * 掉落表由 runData 生成，跑 GameTest 之前要先 runData。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ECWorldGenTests {

    private static final String EMPTY = "empty";

    /** 各母岩在世界生成里应该被替换的方块；深板岩、凝灰岩谁都不该替换（主世界不做深板岩变种） */
    private static final Map<ECWorldGen.Host, Set<Block>> REPLACES = Map.of(
            ECWorldGen.Host.OVERWORLD, Set.of(Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE),
            ECWorldGen.Host.NETHER, Set.of(Blocks.NETHERRACK),
            ECWorldGen.Host.END, Set.of(Blocks.END_STONE));
    private static final List<Block> CANDIDATE_HOSTS = List.of(Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
            Blocks.DEEPSLATE, Blocks.TUFF, Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.END_STONE);

    private ECWorldGenTests() {
    }

    /** 普通镐 400 次：1~2（米斯莱 3~10）均匀；精准采集掉矿石本身 1 个。18 种矿石逐个过 */
    @GameTest(template = EMPTY)
    public static void oreDropsPlainAndSilkTouch(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.IRON_PICKAXE);
        ItemStack silk = enchanted(Enchantments.SILK_TOUCH, 1);
        for (ECWorldGen.Ore ore : ECWorldGen.ores()) {
            BlockState state = ore.block().get().defaultBlockState();
            Item drop = ore.drop().get().asItem();
            int max = ore.minDrops() + ore.spread() - 1;
            assertUniform(helper, name(ore) + " 普通挖掘", roll(helper, state, plain, drop, 400, max), ore.minDrops(), max, 400);

            List<ItemStack> silkDrops = Block.getDrops(state, helper.getLevel(), origin(helper), null, null, silk);
            helper.assertTrue(silkDrops.size() == 1 && silkDrops.get(0).is(ore.block().get().asItem()) && silkDrops.get(0).getCount() == 1,
                    name(ore) + " 精准采集应掉矿石本身 1 个，实际 " + silkDrops);
        }
        helper.succeed();
    }

    /** 时运 III 的元素矿 1~8、时运 II 的米斯莱矿 3~26，都应均匀（原作 nextInt(spread×(时运+1)) + min） */
    @GameTest(template = EMPTY)
    public static void oreDropsFortune(GameTestHelper helper) {
        checkFortune(helper, ECWorldGen.FIRE_INFUSED_ORE.getId().getPath(), 3, 1, 8);
        checkFortune(helper, ECWorldGen.NETHER_ELEMENTAL_INFUSED_ORE.getId().getPath(), 1, 1, 4);
        checkFortune(helper, ECWorldGen.MITHRILINE_ORE.getId().getPath(), 2, 3, 26);
        helper.succeed();
    }

    /** 经验 0~2，精准采集不给（原作 getExpDrop + 1.12 Forge BreakEvent 的规则） */
    @GameTest(template = EMPTY)
    public static void oreExperience(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = origin(helper);
        for (ECWorldGen.Ore ore : ECWorldGen.ores()) {
            BlockState state = ore.block().get().defaultBlockState();
            boolean[] seen = new boolean[3];
            for (int i = 0; i < 200; i++) {
                int xp = state.getExpDrop(level, level.getRandom(), pos, 0, 0);
                helper.assertTrue(xp >= 0 && xp <= 2, name(ore) + " 经验应在 0~2，实际 " + xp);
                seen[xp] = true;
            }
            helper.assertTrue(seen[0] && seen[1] && seen[2], name(ore) + " 200 次里 0、1、2 经验应都出现过");
            helper.assertTrue(state.getExpDrop(level, level.getRandom(), pos, 0, 1) == 0, name(ore) + " 精准采集不应给经验");
        }
        helper.succeed();
    }

    /** configured_feature：矿脉大小、生成的方块、替换哪些母岩（主世界矿不进深板岩） */
    @GameTest(template = EMPTY)
    public static void oreFeatureTargets(GameTestHelper helper) {
        Registry<ConfiguredFeature<?, ?>> features = helper.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        for (ECWorldGen.Ore ore : ECWorldGen.ores()) {
            ConfiguredFeature<?, ?> feature = features.get(ore.block().getId());
            helper.assertTrue(feature != null && feature.config() instanceof OreConfiguration, "缺少矿物特征 " + ore.block().getId());
            OreConfiguration config = (OreConfiguration) feature.config();
            helper.assertTrue(config.size == ore.veinSize(), name(ore) + " 矿脉大小应为 " + ore.veinSize() + "，实际 " + config.size);
            helper.assertTrue(config.targetStates.size() == 1, name(ore) + " 应只有一条替换规则");
            OreConfiguration.TargetBlockState target = config.targetStates.get(0);
            helper.assertTrue(target.state.is(ore.block().get()), name(ore) + " 生成的方块不对：" + target.state);
            Set<Block> expected = REPLACES.get(ore.host());
            for (Block host : CANDIDATE_HOSTS) {
                boolean replaced = target.target.test(host.defaultBlockState(), helper.getLevel().getRandom());
                helper.assertTrue(replaced == expected.contains(host),
                        name(ore) + (replaced ? " 不应替换 " : " 应替换 ") + host);
            }
        }
        helper.succeed();
    }

    /** biome_modifier 生效：主世界 / 下界 / 末地的代表生物群系各自带且只带本维度的 6 种矿 */
    @GameTest(template = EMPTY)
    public static void oreFeaturesInBiomes(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        Registry<PlacedFeature> placed = access.registryOrThrow(Registries.PLACED_FEATURE);
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        Map<ECWorldGen.Host, List<ResourceKey<Biome>>> samples = Map.of(
                ECWorldGen.Host.OVERWORLD, List.of(Biomes.PLAINS, Biomes.DEEP_DARK),
                ECWorldGen.Host.NETHER, List.of(Biomes.NETHER_WASTES, Biomes.BASALT_DELTAS),
                ECWorldGen.Host.END, List.of(Biomes.THE_END, Biomes.END_HIGHLANDS));
        for (ECWorldGen.Ore ore : ECWorldGen.ores()) {
            PlacedFeature feature = placed.get(ore.block().getId());
            helper.assertTrue(feature != null, "缺少 placed_feature " + ore.block().getId());
            for (Map.Entry<ECWorldGen.Host, List<ResourceKey<Biome>>> entry : samples.entrySet()) {
                for (ResourceKey<Biome> biome : entry.getValue()) {
                    boolean has = biomes.getOrThrow(biome).getGenerationSettings().hasFeature(feature);
                    helper.assertTrue(has == (entry.getKey() == ore.host()),
                            name(ore) + (has ? " 不应出现在 " : " 应出现在 ") + biome.location());
                }
            }
        }
        helper.succeed();
    }

    // ---- 工具 ----

    private static void checkFortune(GameTestHelper helper, String name, int fortune, int min, int max) {
        ECWorldGen.Ore ore = ECWorldGen.ores().stream().filter(o -> o.block().getId().getPath().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("没有矿石 " + name));
        helper.assertTrue(max == ore.minDrops() + ore.spread() * (fortune + 1) - 1, name + " 的测试区间和规格不一致");
        int trials = 4000;
        int[] histogram = roll(helper, ore.block().get().defaultBlockState(), enchanted(Enchantments.BLOCK_FORTUNE, fortune),
                ore.drop().get().asItem(), trials, max);
        assertUniform(helper, name + " 时运 " + fortune, histogram, min, max, trials);
    }

    /** 挖 trials 次，只允许掉 expected；返回每次总数的直方图（下标 = 数量，越界直接判失败） */
    private static int[] roll(GameTestHelper helper, BlockState state, ItemStack tool, Item expected, int trials, int max) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = origin(helper);
        int[] histogram = new int[max + 1];
        for (int i = 0; i < trials; i++) {
            int total = 0;
            for (ItemStack stack : Block.getDrops(state, level, pos, null, null, tool)) {
                helper.assertTrue(stack.is(expected), state.getBlock() + " 掉了意外的物品 " + stack);
                total += stack.getCount();
            }
            helper.assertTrue(total <= max, state.getBlock() + " 掉落数 " + total + " 超过上限 " + max);
            histogram[total]++;
        }
        return histogram;
    }

    private static void assertUniform(GameTestHelper helper, String what, int[] histogram, int min, int max, int trials) {
        double p = 1.0 / (max - min + 1);
        double tolerance = 6 * Math.sqrt(p * (1 - p) / trials);
        for (int n = 0; n < histogram.length; n++) {
            if (n < min) {
                helper.assertTrue(histogram[n] == 0, what + " 不应掉 " + n + " 个（下限 " + min + "）");
                continue;
            }
            double frequency = histogram[n] / (double) trials;
            helper.assertTrue(Math.abs(frequency - p) <= tolerance,
                    what + " 掉 " + n + " 个的频率 " + String.format("%.4f", frequency) + "，理论 " + String.format("%.4f", p));
        }
    }

    private static ItemStack enchanted(Enchantment enchantment, int level) {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.enchant(enchantment, level);
        return pick;
    }

    private static BlockPos origin(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(2, 1, 2));
    }

    private static String name(ECWorldGen.Ore ore) {
        return ore.block().getId().getPath();
    }
}
