package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.NestCoreBlockEntity;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaFeatures;
import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.flameforge.essentialcraft5.registry.ECHoannaNature;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureRuin;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.AttachedClusterFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.DebrisPileFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.HoannaFeatureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.HoannaMidscape;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.LeyVentFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.MarrowSpikeFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.MilestoneFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.CellSpread;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.RadiationHotspot;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 第十三批 13d-H1b「地物与中景观」的测试（batch13.md §6.1、§7.2、§7.4；HS §2.1.3、§2.1.4）：
 * <ul>
 *   <li>{@code hoannaFeaturesOnlyInHoanna}：18 个新 placed feature 都在、各挂在该挂的群系和步骤上、主世界（以及下界 / 末地）任何群系里都没有，稀有度照表；</li>
 *   <li>五个 Java 地物各在测试结构里放几次（固定种子）：只写在自己的范围里、只用已有方块 / 本批方块；喷口 {@code charge = 2}；</li>
 *   <li>原版写法的地物（潮晶床、辐晶簇、骨堆、黑曜残柱、渊盐结壳、焦痕地面、绯红菌丛）按 JSON 放：方块对、落点对；</li>
 *   <li>中景观：按群系选种类、底数 &lt; 3 不放陨坑、有城镇引用的区块不放；三种各放一次（营地、裂隙两侧 {@code charge = 3}、陨坑分块放 = 整块放）；</li>
 *   <li>里程碑：地面上立柱，落在水上、树上、屋檐下不放。</li>
 * </ul>
 * GameTest 服是超平坦主世界，地形在测试结构里自己铺（{@code hoanna_feature_lab}：33 × 24 × 33 的空结构）；群系选种类直接喂群系 holder，不靠真海纳尔世界。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaFeaturesTests {

    private static final String EMPTY = "empty";
    private static final String LAB = "hoanna_feature_lab";
    private static final int LAB_SIDE = 33;
    private static final int LAB_HEIGHT = 24;
    private static final int MID = 16;
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** 第一个 rarity_filter 的 chance（= tools/gen/hoanna_features.py 的 *_RARITY） */
    private static final Map<ResourceKey<PlacedFeature>, Integer> RARITY_13D = Map.ofEntries(
            Map.entry(ECHoannaFeatures.HOANNA_LEY_VENT, 5), Map.entry(ECHoannaFeatures.HOANNA_LEY_VENT_SPARSE, 24),
            Map.entry(ECHoannaFeatures.HOANNA_CRIMSON_GROWTH, 4), Map.entry(ECHoannaFeatures.FROST_MARROW_SPIKES, 14),
            Map.entry(ECHoannaFeatures.FROZEN_DEBRIS, 24), Map.entry(ECHoannaFeatures.RUIN_DEBRIS, 24), Map.entry(ECHoannaFeatures.SUNKEN_DEBRIS, 16),
            Map.entry(ECHoannaFeatures.TIDE_BUD_BED, 12), Map.entry(ECHoannaFeatures.RADIANT_CLUSTER_HOTSPOT, 3),
            Map.entry(ECHoannaFeatures.DEAD_TREE, 6), Map.entry(ECHoannaFeatures.BONE_PILE, 10), Map.entry(ECHoannaFeatures.TITANITE_OUTCROP, 12),
            Map.entry(ECHoannaFeatures.UMBRAL_CLUSTER_PATCH, 4), Map.entry(ECHoannaFeatures.OBSIDIAN_STUB, 8),
            Map.entry(ECHoannaFeatures.ABYSSAL_SALT_DISK, 3));

    private HoannaFeaturesTests() {
    }

    // ================================================================== 挂在哪

    /** 每个新地物该挂的群系和步骤（HS §2.1.3；冰冻的残骸堆用冻住的那种） */
    private static Map<ResourceKey<PlacedFeature>, Placement> expectedPlacements() {
        List<ResourceKey<Biome>> land = List.of(ECHoanna.FROZEN_CORRUPTION, ECHoanna.MAGIC_CORRUPTION, ECHoanna.CHAOS_CORRUPTION,
                ECHoanna.DREADLANDS, ECHoanna.HOANNA_DESERT, ECHoanna.SHADOW_CORRUPTION);
        List<ResourceKey<Biome>> landButChaos = land.stream().filter(b -> b != ECHoanna.CHAOS_CORRUPTION).toList();
        List<ResourceKey<Biome>> landButFrozen = land.stream().filter(b -> b != ECHoanna.FROZEN_CORRUPTION).toList();
        GenerationStep.Decoration local = GenerationStep.Decoration.LOCAL_MODIFICATIONS;
        GenerationStep.Decoration vegetal = GenerationStep.Decoration.VEGETAL_DECORATION;
        Map<ResourceKey<PlacedFeature>, Placement> out = new LinkedHashMap<>();
        out.put(ECHoannaFeatures.HOANNA_LEY_VENT, new Placement(vegetal, List.of(ECHoanna.CHAOS_CORRUPTION)));
        out.put(ECHoannaFeatures.HOANNA_LEY_VENT_SPARSE, new Placement(vegetal, landButChaos));
        out.put(ECHoannaFeatures.HOANNA_CRIMSON_GROWTH, new Placement(vegetal, List.of(ECHoanna.CHAOS_CORRUPTION)));
        out.put(ECHoannaFeatures.FROST_MARROW_SPIKES, new Placement(local, List.of(ECHoanna.FROZEN_CORRUPTION)));
        out.put(ECHoannaFeatures.FROZEN_DEBRIS, new Placement(local, List.of(ECHoanna.FROZEN_CORRUPTION)));
        out.put(ECHoannaFeatures.RUIN_DEBRIS, new Placement(local, landButFrozen));
        out.put(ECHoannaFeatures.SUNKEN_DEBRIS, new Placement(local, List.of(ECHoanna.HOANNA_SEA)));
        out.put(ECHoannaFeatures.TIDE_BUD_BED, new Placement(vegetal, List.of(ECHoanna.MAGIC_CORRUPTION)));
        out.put(ECHoannaFeatures.RADIANT_CLUSTER_HOTSPOT, new Placement(vegetal, List.of(ECHoanna.DREADLANDS, ECHoanna.HOANNA_DESERT)));
        // 焦痕在植被之前烧（和原版 disk 同一步）：放在植被之后会把仙人掌、枯灌木脚下的沙换掉，它们一更新就掉成物品
        out.put(ECHoannaFeatures.SCORCHED_GROUND, new Placement(GenerationStep.Decoration.UNDERGROUND_ORES,
                List.of(ECHoanna.DREADLANDS, ECHoanna.HOANNA_DESERT)));
        out.put(ECHoannaFeatures.DEAD_TREE, new Placement(vegetal, List.of(ECHoanna.DREADLANDS, ECHoanna.CHAOS_CORRUPTION)));
        out.put(ECHoannaFeatures.BONE_PILE, new Placement(vegetal, List.of(ECHoanna.DREADLANDS)));
        out.put(ECHoannaFeatures.TITANITE_OUTCROP, new Placement(local, List.of(ECHoanna.HOANNA_DESERT)));
        out.put(ECHoannaFeatures.UMBRAL_CLUSTER_PATCH, new Placement(vegetal, List.of(ECHoanna.SHADOW_CORRUPTION)));
        out.put(ECHoannaFeatures.OBSIDIAN_STUB, new Placement(local, List.of(ECHoanna.SHADOW_CORRUPTION)));
        out.put(ECHoannaFeatures.ABYSSAL_SALT_DISK, new Placement(GenerationStep.Decoration.UNDERGROUND_ORES, List.of(ECHoanna.HOANNA_SEA)));
        out.put(ECHoannaFeatures.HOANNA_MIDSCAPE, new Placement(GenerationStep.Decoration.SURFACE_STRUCTURES,
                List.of(ECHoanna.FROZEN_CORRUPTION, ECHoanna.CHAOS_CORRUPTION, ECHoanna.DREADLANDS)));
        out.put(ECHoannaFeatures.HOANNA_MILESTONE, new Placement(vegetal, land));
        return out;
    }

    private record Placement(GenerationStep.Decoration step, List<ResourceKey<Biome>> biomes) {
    }

    /**
     * 18 个新 placed feature：都注册了；只在该挂的海纳尔群系的该步骤里；海纳尔以外的任何群系（主世界、下界、末地、别的模组的）都没有（P3）；
     * 第一个 rarity_filter 照表；中景观 / 里程碑的 cell_spread 参数和 Java 常量一致；辐晶簇 / 焦痕地面前面有 radiation_hotspot{5 / 4}。
     * 海纳尔之海多了原版海草、海带。
     */
    @GameTest(template = EMPTY)
    public static void hoannaFeaturesOnlyInHoanna(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        Registry<PlacedFeature> placed = access.registryOrThrow(Registries.PLACED_FEATURE);
        List<String> problems = new ArrayList<>();
        Map<ResourceKey<PlacedFeature>, Placement> expected = expectedPlacements();
        helper.assertTrue(expected.keySet().equals(Set.copyOf(ECHoannaFeatures.ALL_PLACED)), "对照表和 ALL_PLACED 不一致");
        for (Map.Entry<ResourceKey<PlacedFeature>, Placement> entry : expected.entrySet()) {
            PlacedFeature feature = placed.get(entry.getKey());
            if (feature == null) {
                problems.add("缺 placed_feature " + entry.getKey().location());
                continue;
            }
            for (Map.Entry<ResourceKey<Biome>, Biome> biome : biomes.entrySet()) {
                boolean hoanna = biomes.getHolderOrThrow(biome.getKey()).is(ECHoanna.IS_HOANNA);
                List<HolderSet<PlacedFeature>> steps = biome.getValue().getGenerationSettings().features();
                for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
                    boolean has = step.ordinal() < steps.size() && steps.get(step.ordinal()).stream().anyMatch(h -> h.value() == feature);
                    boolean want = hoanna && step == entry.getValue().step() && entry.getValue().biomes().contains(biome.getKey());
                    if (has != want) {
                        problems.add(biome.getKey().location() + " 第 " + step.getName() + " 步" + (has ? "不应有 " : "应有 ") + entry.getKey().location().getPath());
                    }
                }
            }
            Integer rarity = RARITY_13D.get(entry.getKey());
            if (rarity != null) {
                Integer actual = firstRarity(feature);
                if (!rarity.equals(actual)) {
                    problems.add(entry.getKey().location().getPath() + " 的 rarity_filter 应是 " + rarity + "，实际 " + actual);
                }
            }
        }
        checkCell(placed.get(ECHoannaFeatures.HOANNA_MIDSCAPE), HoannaMidscape.SPACING, HoannaMidscape.SEPARATION, HoannaMidscape.SALT, "hoanna_midscape", problems);
        checkCell(placed.get(ECHoannaFeatures.HOANNA_MILESTONE), 24, 12, 202610264, "hoanna_milestone", problems);
        checkHotspot(placed.get(ECHoannaFeatures.RADIANT_CLUSTER_HOTSPOT), 5, "radiant_cluster_hotspot", problems);
        checkHotspot(placed.get(ECHoannaFeatures.SCORCHED_GROUND), 4, "scorched_ground", problems);
        Biome sea = biomes.getOrThrow(ECHoanna.HOANNA_SEA);
        for (String vanilla : List.of("seagrass_normal", "kelp_cold")) {
            PlacedFeature feature = placed.get(ResourceLocation.withDefaultNamespace(vanilla));
            if (feature == null || !sea.getGenerationSettings().hasFeature(feature)) {
                problems.add("海纳尔之海应有原版 " + vanilla);
            }
        }
        helper.assertTrue(problems.isEmpty(), "13d 地物挂得不对：" + problems);
        helper.succeed();
    }

    /** 第一个 rarity_filter 的 chance（字段是私有的，按编解码器取）；没有返回 null */
    private static Integer firstRarity(PlacedFeature feature) {
        for (PlacementModifier modifier : feature.placement()) {
            if (modifier instanceof RarityFilter) {
                JsonObject json = PlacementModifier.CODEC.encodeStart(JsonOps.INSTANCE, modifier).result()
                        .map(JsonElement::getAsJsonObject).orElse(new JsonObject());
                return json.has("chance") ? json.get("chance").getAsInt() : null;
            }
        }
        return null;
    }

    private static void checkCell(PlacedFeature feature, int spacing, int separation, int salt, String name, List<String> problems) {
        if (feature == null || feature.placement().isEmpty() || !(feature.placement().get(0) instanceof CellSpread cell)) {
            problems.add(name + " 的第一条放置修饰应是 cell_spread");
            return;
        }
        if (cell.spacing() != spacing || cell.separation() != separation || cell.salt() != salt) {
            problems.add(name + " 的 cell_spread 应是 " + spacing + " / " + separation + " / " + salt + "，实际 "
                    + cell.spacing() + " / " + cell.separation() + " / " + cell.salt());
        }
    }

    private static void checkHotspot(PlacedFeature feature, int minBase, String name, List<String> problems) {
        if (feature == null || feature.placement().isEmpty() || !(feature.placement().get(0) instanceof RadiationHotspot hotspot)
                || hotspot.minBase() != minBase) {
            problems.add(name + " 的第一条放置修饰应是 radiation_hotspot{" + minBase + "}");
        }
    }

    // ================================================================== Java 地物

    /**
     * 地脉喷口：地面一格换成喷口 charge 2，四周 8 格焦土（粗泥 / 恐惧泥土），喷口上的草清掉；只写 3 × 2 × 3 的范围；
     * 起点下面是水（水面上）不放、什么都不改；{@code placeVent} 传 3 就是满充能。
     */
    @GameTest(template = LAB)
    public static void leyVentFeaturePlaces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox lab = labBox(helper);
        groundLab(helper, 8, Blocks.GRASS_BLOCK);
        BlockPos ground = helper.absolutePos(new BlockPos(MID, 8, MID));
        level.setBlock(ground.above(), Blocks.GRASS.defaultBlockState(), QUIET);
        Map<BlockPos, BlockState> before = snapshot(level, lab);
        helper.assertTrue(run(ECHoannaFeatures.LEY_VENT_FEATURE.get(), NoneFeatureConfiguration.INSTANCE, level, ground.above(), SEED_STEP),
                "草方块上应放得下喷口");
        Map<BlockPos, BlockState> changed = diff(level, before);
        BlockState vent = level.getBlockState(ground);
        helper.assertTrue(vent.is(HoannaFeatureBlocks.leyVent(0).getBlock()) && vent.getValue(intProperty(vent, "charge")) == LeyVentFeature.GENERATED_CHARGE,
                "地面那格应是 charge 2 的喷口，实际 " + vent);
        helper.assertTrue(level.getBlockState(ground.above()).isAir(), "喷口上的草应清掉");
        int ring = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockState state = level.getBlockState(ground.offset(dx, 0, dz));
                if ((dx != 0 || dz != 0) && (state.is(Blocks.COARSE_DIRT) || state.is(ECHoannaNature.DREAD_DIRT.get()))) {
                    ring++;
                }
            }
        }
        helper.assertTrue(ring == 8, "四周 8 格应是焦土，实际 " + ring);
        assertWithin(helper, changed, ground, 1, 1, 1, "喷口");

        // 满充能
        restore(level, before);
        helper.assertTrue(LeyVentFeature.placeVent(level, lab, ground, 3, 7L), "placeVent 应放得下");
        helper.assertTrue(level.getBlockState(ground).getValue(intProperty(level.getBlockState(ground), "charge")) == 3, "传 3 应是满充能");

        // 水面上不放
        restore(level, before);
        BlockPos pond = helper.absolutePos(new BlockPos(5, 8, 5));
        level.setBlock(pond, Blocks.WATER.defaultBlockState(), QUIET);
        Map<BlockPos, BlockState> wet = snapshot(level, lab);
        helper.assertFalse(run(ECHoannaFeatures.LEY_VENT_FEATURE.get(), NoneFeatureConfiguration.INSTANCE, level, pond.above(), SEED_STEP),
                "水面上不该放喷口");
        helper.assertTrue(diff(level, wet).isEmpty(), "放不下时什么都不该改");
        restore(level, before);
        helper.succeed();
    }

    /**
     * 霜髓冰刺（6 个种子）：只放浮冰和霜髓、霜髓 2~5 块、刺不超过 9 格高、水平不出 4 格；
     * 残骸堆三种（各 6 个种子）：只用强化石 / 混凝土 / 虚空栅栏 / 魔力光源 / 悬浮器（冻住的另有浮冰、雪层），水平不出 4 格；
     * 沉没残骸只换水（不留空气）、栅栏带水、不放光源；陆地的两种在水面上不放。
     */
    @GameTest(template = LAB, timeoutTicks = 200)
    public static void marrowSpikesAndDebrisPiles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox lab = labBox(helper);
        groundLab(helper, 6, Blocks.GRASS_BLOCK);
        BlockPos ground = helper.absolutePos(new BlockPos(MID, 6, MID));
        Map<BlockPos, BlockState> before = snapshot(level, lab);
        Block marrow = HoannaFeatureBlocks.frostMarrow().getBlock();
        for (int i = 1; i <= 6; i++) {
            helper.assertTrue(run(ECHoannaFeatures.MARROW_SPIKE_FEATURE.get(), NoneFeatureConfiguration.INSTANCE, level, ground.above(), i * SEED_STEP),
                    "种子 " + i + "：草地上应放得下冰刺");
            Map<BlockPos, BlockState> changed = diff(level, before);
            assertOnly(helper, changed, s -> s.is(Blocks.PACKED_ICE) || s.is(marrow), "冰刺");
            long marrows = changed.values().stream().filter(s -> s.is(marrow)).count();
            helper.assertTrue(marrows >= 2 && marrows <= 5, "种子 " + i + "：霜髓应 2~5 块，实际 " + marrows);
            assertWithin(helper, changed, ground.above(), 4, MarrowSpikeFeature.MAX_HEIGHT - 1, 4, "冰刺");
            helper.assertTrue(changed.keySet().stream().allMatch(p -> p.getY() > ground.getY()), "冰刺不该改地面");
            restore(level, before);
        }
        Set<Block> rubble = Set.of(ECMaterials.FORTIFIED_STONE.get(), HoannaStructureBlocks.concrete().getBlock(), HoannaStructureBlocks.voidFence().getBlock(),
                ECEssence.MAGIC_LIGHT.get(), HoannaStructureBlocks.levitator().getBlock());
        for (DebrisPileFeature.Style style : List.of(DebrisPileFeature.Style.RUIN, DebrisPileFeature.Style.FROZEN)) {
            for (int i = 1; i <= 6; i++) {
                helper.assertTrue(run(ECHoannaFeatures.DEBRIS_PILE_FEATURE.get(), new DebrisPileFeature.Config(style), level, ground.above(3), i * SEED_STEP),
                        style + " 种子 " + i + "：草地上应放得下");
                Map<BlockPos, BlockState> changed = diff(level, before);
                boolean frozen = style == DebrisPileFeature.Style.FROZEN;
                assertOnly(helper, changed, s -> rubble.contains(s.getBlock()) || (frozen && (s.is(Blocks.PACKED_ICE) || s.is(Blocks.SNOW)
                        || (s.is(Blocks.GRASS_BLOCK) && s.getValue(BlockStateProperties.SNOWY)))), "残骸堆 " + style);
                assertWithin(helper, changed, ground, 4, 5, 4, "残骸堆 " + style);
                restore(level, before);
            }
        }
        // 水池：石头底（y 2）上 4 格水
        for (BlockPos rel : BlockPos.betweenClosed(4, 3, 4, 28, 6, 28)) {
            level.setBlock(helper.absolutePos(rel), Blocks.WATER.defaultBlockState(), QUIET);
        }
        for (BlockPos rel : BlockPos.betweenClosed(4, 2, 4, 28, 2, 28)) {
            level.setBlock(helper.absolutePos(rel), Blocks.STONE.defaultBlockState(), QUIET);
        }
        Map<BlockPos, BlockState> pool = snapshot(level, lab);
        BlockPos seabed = helper.absolutePos(new BlockPos(MID, 3, MID));
        for (int i = 1; i <= 6; i++) {
            helper.assertTrue(run(ECHoannaFeatures.DEBRIS_PILE_FEATURE.get(), new DebrisPileFeature.Config(DebrisPileFeature.Style.SUNKEN), level, seabed, i * SEED_STEP),
                    "种子 " + i + "：水底应放得下沉没残骸");
            Map<BlockPos, BlockState> changed = diff(level, pool);
            for (Map.Entry<BlockPos, BlockState> entry : changed.entrySet()) {
                BlockState state = entry.getValue();
                helper.assertTrue(pool.get(entry.getKey()).is(Blocks.WATER), "沉没残骸只该换掉水：" + entry.getKey());
                helper.assertTrue(rubble.contains(state.getBlock()) && !state.is(ECEssence.MAGIC_LIGHT.get()), "沉没残骸不该放 " + state);
                if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    helper.assertTrue(state.getValue(BlockStateProperties.WATERLOGGED), "水下的栅栏应带水：" + state);
                }
            }
            restore(level, pool);
            helper.assertFalse(run(ECHoannaFeatures.DEBRIS_PILE_FEATURE.get(), new DebrisPileFeature.Config(DebrisPileFeature.Style.RUIN), level,
                    helper.absolutePos(new BlockPos(MID, 7, MID)), i * SEED_STEP), "陆地残骸堆不该放在水面上");
            helper.assertTrue(diff(level, pool).isEmpty(), "放不下时什么都不该改");
        }
        restore(level, before);
        helper.succeed();
    }

    /**
     * 枯树（恐惧泥土上，6 个种子）：只放深色橡木原木 / 去皮原木、没有树叶、树干 3~6 格、水平不出 2 格；
     * 榍石露头（沙地上，6 个种子）：只有砂岩 / 平滑砂岩 / 榍石矿，榍石矿 4~8 块且都露在外面，水平不出 4 格。
     */
    @GameTest(template = LAB, timeoutTicks = 200)
    public static void deadTreeAndOutcrop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox lab = labBox(helper);
        groundLab(helper, 6, ECHoannaNature.DREAD_DIRT.get());
        BlockPos ground = helper.absolutePos(new BlockPos(MID, 6, MID));
        Map<BlockPos, BlockState> before = snapshot(level, lab);
        for (int i = 1; i <= 6; i++) {
            helper.assertTrue(run(ECHoannaFeatures.DEAD_TREE_FEATURE.get(), NoneFeatureConfiguration.INSTANCE, level, ground.above(), i * SEED_STEP),
                    "种子 " + i + "：恐惧泥土上应长得出枯树");
            Map<BlockPos, BlockState> changed = diff(level, before);
            assertOnly(helper, changed, s -> s.is(Blocks.DARK_OAK_LOG) || s.is(Blocks.STRIPPED_DARK_OAK_LOG), "枯树");
            int trunk = 0;
            while (level.getBlockState(ground.above(trunk + 1)).is(Blocks.DARK_OAK_LOG) || level.getBlockState(ground.above(trunk + 1)).is(Blocks.STRIPPED_DARK_OAK_LOG)) {
                trunk++;
            }
            helper.assertTrue(trunk >= 3 && trunk <= 6, "种子 " + i + "：树干应 3~6 格，实际 " + trunk);
            assertWithin(helper, changed, ground.above(), 2, 6, 2, "枯树");
            restore(level, before);
        }
        groundLab(helper, 6, Blocks.SAND);
        Map<BlockPos, BlockState> sand = snapshot(level, lab);
        Block ore = HoannaFeatureBlocks.titaniteOre().getBlock();
        for (int i = 1; i <= 6; i++) {
            helper.assertTrue(run(ECHoannaFeatures.OUTCROP_FEATURE.get(), NoneFeatureConfiguration.INSTANCE, level, ground.above(), i * SEED_STEP),
                    "种子 " + i + "：沙地上应放得下露头");
            Map<BlockPos, BlockState> changed = diff(level, sand);
            assertOnly(helper, changed, s -> s.is(Blocks.SANDSTONE) || s.is(Blocks.SMOOTH_SANDSTONE) || s.is(ore), "露头");
            long ores = changed.values().stream().filter(s -> s.is(ore)).count();
            helper.assertTrue(ores >= 4 && ores <= 8, "种子 " + i + "：榍石矿应 4~8 块，实际 " + ores);
            for (Map.Entry<BlockPos, BlockState> entry : changed.entrySet()) {
                if (entry.getValue().is(ore)) {
                    BlockPos p = entry.getKey();
                    boolean exposed = Direction.stream().filter(d -> d != Direction.DOWN).anyMatch(d -> level.getBlockState(p.relative(d)).isAir());
                    helper.assertTrue(exposed, "榍石矿应露在外面：" + p);
                }
            }
            assertWithin(helper, changed, ground, 4, 4, 4, "露头");
            restore(level, sand);
        }
        restore(level, before);
        helper.succeed();
    }

    /**
     * 贴附晶簇：脚下实心 → 朝上；悬空但挨着墙 → 朝外（背对墙）；只挨着顶 → 朝下；四面都空 → 不放；在水里 → 带水。影晶簇放下仍是成熟的。
     */
    @GameTest(template = LAB)
    public static void attachedClusterFacing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RandomSource random = RandomSource.create(SEED_STEP);
        BlockState radiant = HoannaFeatureBlocks.radiantCluster(Direction.UP);
        BlockState umbral = HoannaFeatureBlocks.umbralCluster(Direction.UP, true);
        BlockPos floor = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), QUIET);
        helper.assertTrue(AttachedClusterFeature.placeAttached(level, floor.above(), radiant, random), "地面上应放得下");
        helper.assertTrue(level.getBlockState(floor.above()).getValue(BlockStateProperties.FACING) == Direction.UP, "贴地应朝上");

        BlockPos wall = helper.absolutePos(new BlockPos(10, 6, 10));
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), QUIET);
        BlockPos side = wall.east();
        helper.assertTrue(AttachedClusterFeature.placeAttached(level, side, umbral, random), "挨着墙应放得下");
        BlockState onWall = level.getBlockState(side);
        helper.assertTrue(onWall.getValue(BlockStateProperties.FACING) == Direction.EAST, "贴西边的墙应朝东，实际 " + onWall);
        helper.assertTrue(onWall.getValue(boolProperty(onWall, "ripe")), "影晶簇应是成熟的");

        BlockPos ceiling = helper.absolutePos(new BlockPos(20, 10, 20));
        level.setBlock(ceiling, Blocks.STONE.defaultBlockState(), QUIET);
        helper.assertTrue(AttachedClusterFeature.placeAttached(level, ceiling.below(), radiant, random), "顶下应放得下");
        helper.assertTrue(level.getBlockState(ceiling.below()).getValue(BlockStateProperties.FACING) == Direction.DOWN, "贴顶应朝下");

        BlockPos nowhere = helper.absolutePos(new BlockPos(26, 16, 26));
        helper.assertFalse(AttachedClusterFeature.placeAttached(level, nowhere, radiant, random), "四面都空不该放");
        helper.assertTrue(level.getBlockState(nowhere).isAir(), "四面都空应还是空气");

        BlockPos wet = helper.absolutePos(new BlockPos(28, 3, 4));
        level.setBlock(wet.below(), Blocks.STONE.defaultBlockState(), QUIET);
        level.setBlock(wet, Blocks.WATER.defaultBlockState(), QUIET);
        helper.assertTrue(AttachedClusterFeature.placeAttached(level, wet, radiant, random), "水底应放得下");
        helper.assertTrue(level.getBlockState(wet).getValue(BlockStateProperties.WATERLOGGED), "水里的晶簇应带水");
        helper.succeed();
    }

    /**
     * 每床、每簇的块数贴设计（HS §2.1.3 / §2.1.7 的存量按它推）：平地上各放 40 次取平均——潮晶床约 4.5 芽（5 次尝试、每次都落地表），
     * 辐晶簇约 3 块（14 次尝试、y 偏移 ±3 只有约 1/4 正好落在地表那一格）。尝试次数改回 12 / 8 时分别约 10 / 1.8，落在范围外。
     */
    @GameTest(template = LAB, timeoutTicks = 200)
    public static void patchYieldsMatchDesign(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Registry<ConfiguredFeature<?, ?>> configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        groundLab(helper, 6, Blocks.GRASS_BLOCK);
        BlockPos ground = helper.absolutePos(new BlockPos(MID, 6, MID));
        Block bud = HoannaFeatureBlocks.tideBud(0).getBlock();
        Block radiant = HoannaFeatureBlocks.radiantCluster(Direction.UP).getBlock();
        int rounds = 40;
        int buds = 0;
        int clusters = 0;
        for (int i = 1; i <= rounds; i++) {
            place(configured, "tide_bud_bed", level, ground.above(), i * SEED_STEP);
            buds += takeAround(level, ground, 3, bud);
            place(configured, "radiant_cluster_hotspot", level, ground.above(), i * SEED_STEP + 1);
            clusters += takeAround(level, ground, 4, radiant);
        }
        double budMean = (double) buds / rounds;
        double clusterMean = (double) clusters / rounds;
        helper.assertTrue(budMean >= 3.8D && budMean <= 5.2D, "潮晶床平均每床应约 4.5 芽，实际 " + budMean);
        helper.assertTrue(clusterMean >= 2.4D && clusterMean <= 4.2D, "辐晶簇平均每簇应约 3 块，实际 " + clusterMean);
        clearLab(helper);
        helper.succeed();
    }

    /** center 周围水平 radius、上下 4 格里有几块 block，数完换回空气 */
    private static int takeAround(ServerLevel level, BlockPos center, int radius, Block block) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -4, -radius), center.offset(radius, 5, radius))) {
            if (level.getBlockState(pos).is(block)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
                n++;
            }
        }
        return n;
    }

    /**
     * 原版写法的地物按 JSON 放（直接放 configured feature，跳过稀有度）：
     * 潮晶床只落在草方块 / 石头上、头顶没有挡路的方块（半边加了木板屋檐）、阶段 0~3；辐晶簇贴着方块长；骨堆只放骨块；
     * 黑曜残柱是黑曜石 / 哭泣黑曜石 2~5 格；焦痕地面只把恐惧泥土换成粗泥 / 灵魂土；绯红菌丛是绯红菌根 / 绯红菌；渊盐结壳只换掉水底的沙。
     */
    @GameTest(template = LAB, timeoutTicks = 200)
    public static void vanillaStyleFeaturesPlaceRightBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox lab = labBox(helper);
        Registry<ConfiguredFeature<?, ?>> configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        groundLab(helper, 6, Blocks.GRASS_BLOCK);
        BlockPos ground = helper.absolutePos(new BlockPos(MID, 6, MID));
        // 西半边（x < MID）头顶 3 格搭木板屋檐
        for (BlockPos rel : BlockPos.betweenClosed(0, 10, 0, MID - 1, 10, LAB_SIDE - 1)) {
            level.setBlock(helper.absolutePos(rel), Blocks.OAK_PLANKS.defaultBlockState(), QUIET);
        }
        Map<BlockPos, BlockState> before = snapshot(level, lab);
        Block bud = HoannaFeatureBlocks.tideBud(0).getBlock();
        int buds = 0;
        for (int i = 1; i <= 6; i++) {
            place(configured, "tide_bud_bed", level, ground.above(), i * SEED_STEP);
            Map<BlockPos, BlockState> changed = diff(level, before);
            assertOnly(helper, changed, s -> s.is(bud), "潮晶床");
            for (BlockPos p : changed.keySet()) {
                helper.assertTrue(p.getX() >= helper.absolutePos(new BlockPos(MID, 0, 0)).getX(), "屋檐下不该长潮晶芽：" + p);
                helper.assertTrue(level.getBlockState(p.below()).is(Blocks.GRASS_BLOCK), "潮晶芽应长在草方块上：" + p);
            }
            buds += changed.size();
            restore(level, before);
        }
        helper.assertTrue(buds > 0, "6 次潮晶床应至少长出一个芽");

        place(configured, "bone_pile", level, ground.above(), SEED_STEP);
        assertOnly(helper, diff(level, before), s -> s.is(Blocks.BONE_BLOCK), "骨堆");
        restore(level, before);
        place(configured, "hoanna_crimson_growth", level, ground.above(), SEED_STEP);
        assertOnly(helper, diff(level, before), s -> s.is(Blocks.CRIMSON_ROOTS) || s.is(Blocks.CRIMSON_FUNGUS), "绯红菌丛");
        restore(level, before);
        place(configured, "radiant_cluster_hotspot", level, ground.above(), SEED_STEP);
        Block radiant = HoannaFeatureBlocks.radiantCluster(Direction.UP).getBlock();
        Map<BlockPos, BlockState> clusters = diff(level, before);
        assertOnly(helper, clusters, s -> s.is(radiant), "辐晶簇");
        for (Map.Entry<BlockPos, BlockState> entry : clusters.entrySet()) {
            helper.assertTrue(entry.getValue().canSurvive(level, entry.getKey()), "辐晶簇应贴着方块：" + entry.getKey());
        }
        restore(level, before);
        BlockPos east = helper.absolutePos(new BlockPos(24, 7, 24));
        place(configured, "obsidian_stub", level, east, SEED_STEP);
        Map<BlockPos, BlockState> stub = diff(level, before);
        assertOnly(helper, stub, s -> s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN), "黑曜残柱");
        helper.assertTrue(stub.size() >= 2 && stub.size() <= 5, "黑曜残柱应 2~5 格，实际 " + stub.size());
        restore(level, before);

        groundLab(helper, 6, ECHoannaNature.DREAD_DIRT.get());
        Map<BlockPos, BlockState> dread = snapshot(level, lab);
        place(configured, "scorched_ground", level, ground.above(), SEED_STEP);
        Map<BlockPos, BlockState> scorched = diff(level, dread);
        helper.assertTrue(!scorched.isEmpty(), "恐惧泥土上应烧出焦痕");
        for (Map.Entry<BlockPos, BlockState> entry : scorched.entrySet()) {
            helper.assertTrue(dread.get(entry.getKey()).is(ECHoannaNature.DREAD_DIRT.get())
                    && (entry.getValue().is(Blocks.COARSE_DIRT) || entry.getValue().is(Blocks.SOUL_SOIL)), "焦痕只该把恐惧泥土换成粗泥 / 灵魂土：" + entry);
        }
        // 水底的沙：y 3 沙、y 4~6 水
        for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, LAB_SIDE - 1, 12, LAB_SIDE - 1)) {
            BlockState state = rel.getY() <= 2 ? Blocks.STONE.defaultBlockState() : rel.getY() == 3 ? Blocks.SAND.defaultBlockState()
                    : rel.getY() <= 6 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(helper.absolutePos(rel), state, QUIET);
        }
        Map<BlockPos, BlockState> sea = snapshot(level, lab);
        place(configured, "abyssal_salt_disk", level, helper.absolutePos(new BlockPos(MID, 4, MID)), SEED_STEP);
        Map<BlockPos, BlockState> salt = diff(level, sea);
        Block crust = HoannaFeatureBlocks.abyssalSaltCrust().getBlock();
        helper.assertTrue(salt.size() >= 9, "渊盐结壳应换掉一片沙，实际 " + salt.size());
        for (Map.Entry<BlockPos, BlockState> entry : salt.entrySet()) {
            helper.assertTrue(sea.get(entry.getKey()).is(Blocks.SAND) && entry.getValue().is(crust), "渊盐结壳只该换掉沙：" + entry);
        }
        clearLab(helper);
        helper.succeed();
    }

    // ================================================================== 中景观

    /**
     * 选种类：冰冻 → 营地、混沌 → 裂隙、恐惧之地且底数 ≥ 3 → 陨坑、底数 &lt; 3 不放，别的四个群系都不放。
     */
    @GameTest(template = EMPTY)
    public static void midscapeKindByBiome(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        ChunkPos hot = null;
        ChunkPos cold = null;
        for (int i = 0; i < 4096 && (hot == null || cold == null); i++) {
            int x = (int) ((i * SEED_STEP) >>> 50) - 8192;
            int z = (int) ((i * SEED_STEP * 31L) >>> 50) - 8192;
            int base = Radiation.chunkBase(x, z);
            if (base >= HoannaMidscape.CRATER_MIN_BASE && hot == null) {
                hot = new ChunkPos(x, z);
            } else if (base < HoannaMidscape.CRATER_MIN_BASE && cold == null) {
                cold = new ChunkPos(x, z);
            }
        }
        helper.assertTrue(hot != null && cold != null, "找不到底数高 / 低的区块");
        Holder<Biome> dread = biomes.getHolderOrThrow(ECHoanna.DREADLANDS);
        helper.assertTrue(HoannaMidscape.kindFor(biomes.getHolderOrThrow(ECHoanna.FROZEN_CORRUPTION), cold.x, cold.z) == HoannaMidscape.Kind.FROZEN_CAMP, "冰冻 → 营地");
        helper.assertTrue(HoannaMidscape.kindFor(biomes.getHolderOrThrow(ECHoanna.CHAOS_CORRUPTION), cold.x, cold.z) == HoannaMidscape.Kind.CHAOS_FISSURE, "混沌 → 裂隙");
        helper.assertTrue(HoannaMidscape.kindFor(dread, hot.x, hot.z) == HoannaMidscape.Kind.FALLOUT_CRATER, "恐惧之地、底数 ≥ 3 → 陨坑");
        helper.assertTrue(HoannaMidscape.kindFor(dread, cold.x, cold.z) == null, "恐惧之地、底数 < 3 不放");
        for (ResourceKey<Biome> other : List.of(ECHoanna.MAGIC_CORRUPTION, ECHoanna.HOANNA_DESERT, ECHoanna.SHADOW_CORRUPTION, ECHoanna.HOANNA_SEA)) {
            helper.assertTrue(HoannaMidscape.kindFor(biomes.getHolderOrThrow(other), hot.x, hot.z) == null, other.location() + " 不放中景观");
        }
        helper.assertTrue(HoannaMidscape.Kind.FROZEN_CAMP.nestType().equals(EssentialCraft5.id("frozen_camp"))
                && HoannaMidscape.Kind.CHAOS_FISSURE.nestType().equals(EssentialCraft5.id("chaos_fissure"))
                && HoannaMidscape.Kind.FALLOUT_CRATER.nestType().equals(EssentialCraft5.id("fallout_crater")), "巢穴类型 id");
        helper.succeed();
    }

    /**
     * 结构引用：区块里（或四周 8 块里）有城镇引用时中景观不放、本区块有引用时里程碑不放；只有不在 {@code blocks_midscape} 里的结构（墓穴）的引用不拦。
     * 测完把引用表还原。
     */
    @GameTest(template = EMPTY)
    public static void midscapeSkipsTownReferences(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure town = structures.get(EssentialCraft5.id("hoanna_town"));
        Structure catacombs = structures.get(EssentialCraft5.id("old_catacombs"));
        helper.assertTrue(town != null && catacombs != null, "缺城镇 / 墓穴结构");
        helper.assertTrue(structures.wrapAsHolder(town).is(HoannaMidscape.BLOCKS_MIDSCAPE), "城镇应在 blocks_midscape 里");
        helper.assertFalse(structures.wrapAsHolder(catacombs).is(HoannaMidscape.BLOCKS_MIDSCAPE), "墓穴（地下）不在 blocks_midscape 里");
        ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ChunkAccess here = level.getChunk(chunk.x, chunk.z);
        ChunkAccess east = level.getChunk(chunk.x + 1, chunk.z);
        Map<Structure, LongSet> hereRefs = copy(here.getAllReferences());
        Map<Structure, LongSet> eastRefs = copy(east.getAllReferences());
        try {
            here.setAllReferences(new HashMap<>());
            east.setAllReferences(new HashMap<>());
            helper.assertFalse(HoannaMidscape.blockedByStructures(level, chunk, 1), "没有引用时不该拦");
            east.addReferenceForStructure(catacombs, ChunkPos.asLong(chunk.x + 1, chunk.z));
            helper.assertFalse(HoannaMidscape.blockedByStructures(level, chunk, 1), "墓穴的引用不该拦");
            east.addReferenceForStructure(town, ChunkPos.asLong(chunk.x + 2, chunk.z));
            helper.assertTrue(HoannaMidscape.blockedByStructures(level, chunk, 1), "东边区块有城镇引用时中景观不该放");
            helper.assertFalse(HoannaMidscape.blockedByStructures(level, chunk, 0), "里程碑只看本区块");
            here.addReferenceForStructure(town, ChunkPos.asLong(chunk.x, chunk.z));
            helper.assertTrue(HoannaMidscape.blockedByStructures(level, chunk, 0), "本区块有城镇引用时里程碑不该放");
        } finally {
            here.setAllReferences(hereRefs);
            east.setAllReferences(eastRefs);
        }
        helper.succeed();
    }

    /**
     * 冰封营地（3 个种子）：正中间的地面上是巢穴核心 {@code frozen_camp}；中间一片雪块；外圈 ≥ 5 根浮冰刺；只放雪块 / 浮冰 / 霜髓 / 核心，
     * 清掉的只有草；水平不出 {@value HoannaMidscape#MAX_REACH} 格。
     */
    @GameTest(template = LAB, timeoutTicks = 200)
    public static void midscapeFrozenCamp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox lab = labBox(helper);
        groundLab(helper, 6, Blocks.GRASS_BLOCK);
        for (int i = 0; i < 40; i++) {
            level.setBlock(helper.absolutePos(new BlockPos(2 + (i * 7) % 29, 7, 2 + (i * 11) % 29)), Blocks.GRASS.defaultBlockState(), QUIET);
        }
        BlockPos center = helper.absolutePos(new BlockPos(MID, 6, MID));
        Map<BlockPos, BlockState> before = snapshot(level, lab);
        Block marrow = HoannaFeatureBlocks.frostMarrow().getBlock();
        for (int i = 1; i <= 3; i++) {
            helper.assertTrue(HoannaMidscape.placeCamp(level, lab, center, i * SEED_STEP), "种子 " + i + "：营地应放得下");
            Map<BlockPos, BlockState> changed = diff(level, before);
            assertNestCore(helper, level, center.above(), HoannaMidscape.Kind.FROZEN_CAMP);
            for (Map.Entry<BlockPos, BlockState> entry : changed.entrySet()) {
                BlockState state = entry.getValue();
                boolean ok = state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.PACKED_ICE) || state.is(marrow) || state.is(ECHoannaFramework.NEST_CORE.get())
                        || (state.isAir() && before.get(entry.getKey()).is(Blocks.GRASS));
                helper.assertTrue(ok, "营地不该放 " + state + "（" + entry.getKey() + "）");
            }
            long snow = changed.values().stream().filter(s -> s.is(Blocks.SNOW_BLOCK)).count();
            helper.assertTrue(snow >= 80, "种子 " + i + "：中间应是一片雪地，实际 " + snow + " 格");
            long spikeColumns = changed.entrySet().stream().filter(e -> e.getValue().is(Blocks.PACKED_ICE) || e.getValue().is(marrow))
                    .map(e -> ChunkPos.asLong(e.getKey().getX(), e.getKey().getZ())).distinct().count();
            helper.assertTrue(spikeColumns >= 5, "种子 " + i + "：外圈应有冰刺，实际 " + spikeColumns + " 列");
            assertWithin(helper, changed, center, HoannaMidscape.MAX_REACH, LAB_HEIGHT, HoannaMidscape.MAX_REACH, "营地");
            restore(level, before);
        }
        clearLab(helper);
        helper.succeed();
    }

    /**
     * 混沌裂隙（3 个种子）：3 × 18 的地裂，中段挖 6 格、底下是岩浆块，没有放任何液体；两侧 4~6 个满充能（charge 3）的喷口；
     * 巢穴核心 {@code chaos_fissure}；水平不出 12 格。挖到的范围旁边有水时整处不放、什么都不改。
     */
    @GameTest(template = LAB, timeoutTicks = 200)
    public static void midscapeChaosFissure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox lab = labBox(helper);
        groundLab(helper, 10, Blocks.GRASS_BLOCK);
        BlockPos center = helper.absolutePos(new BlockPos(MID, 10, MID));
        Map<BlockPos, BlockState> before = snapshot(level, lab);
        Block vent = HoannaFeatureBlocks.leyVent(0).getBlock();
        for (int i = 1; i <= 3; i++) {
            helper.assertTrue(HoannaMidscape.placeFissure(level, lab, center, i * SEED_STEP), "种子 " + i + "：裂隙应放得下");
            Map<BlockPos, BlockState> changed = diff(level, before);
            long magma = changed.values().stream().filter(s -> s.is(Blocks.MAGMA_BLOCK)).count();
            long air = changed.values().stream().filter(BlockState::isAir).count();
            helper.assertTrue(magma == (long) HoannaMidscape.FISSURE_LENGTH * (2 * HoannaMidscape.FISSURE_HALF_WIDTH + 1),
                    "种子 " + i + "：每一列底下都应是岩浆块（54 格），实际 " + magma);
            helper.assertTrue(air >= 3L * (14 * 6 + 2 * 4 + 2 * 2), "种子 " + i + "：地裂挖空的格数不对：" + air);
            int deepest = changed.entrySet().stream().filter(e -> e.getValue().isAir()).mapToInt(e -> e.getKey().getY()).min().orElse(center.getY());
            helper.assertTrue(center.getY() - deepest == HoannaMidscape.FISSURE_DEPTH - 1, "中段应挖 6 格深（地面那格也挖），实际最深到地面下 " + (center.getY() - deepest));
            helper.assertTrue(changed.values().stream().allMatch(s -> s.getFluidState().isEmpty()), "裂隙不该放液体");
            List<BlockState> vents = changed.values().stream().filter(s -> s.is(vent)).toList();
            helper.assertTrue(vents.size() >= 4 && vents.size() <= 6, "种子 " + i + "：两侧应有 4~6 个喷口，实际 " + vents.size());
            helper.assertTrue(vents.stream().allMatch(s -> s.getValue(intProperty(s, "charge")) == 3), "裂隙旁的喷口应满充能");
            BlockPos core = changed.entrySet().stream().filter(e -> e.getValue().is(ECHoannaFramework.NEST_CORE.get())).map(Map.Entry::getKey).findFirst().orElse(null);
            helper.assertTrue(core != null, "裂隙旁应有巢穴核心");
            assertNestCore(helper, level, core, HoannaMidscape.Kind.CHAOS_FISSURE);
            assertWithin(helper, changed, center, HoannaMidscape.MAX_REACH, LAB_HEIGHT, HoannaMidscape.MAX_REACH, "裂隙");
            restore(level, before);
        }
        level.setBlock(center.below(2), Blocks.WATER.defaultBlockState(), QUIET);
        Map<BlockPos, BlockState> wet = snapshot(level, lab);
        helper.assertFalse(HoannaMidscape.placeFissure(level, lab, center, SEED_STEP), "旁边有水不该放");
        helper.assertTrue(diff(level, wet).isEmpty(), "旁边有水时什么都不该改");
        restore(level, before);
        // 比中心高的列：中心那一列垫到地面上 4 格（找地面从这里开始往下找），头顶 1 格是水——挖下去水就灌进来
        for (int dy = 1; dy <= 4; dy++) {
            level.setBlock(center.above(dy), Blocks.DIRT.defaultBlockState(), QUIET);
        }
        level.setBlock(center.above(5), Blocks.WATER.defaultBlockState(), QUIET);
        Map<BlockPos, BlockState> raised = snapshot(level, lab);
        helper.assertFalse(HoannaMidscape.placeFissure(level, lab, center, SEED_STEP), "高出中心 4 格的列头顶有水不该放");
        helper.assertTrue(diff(level, raised).isEmpty(), "高处有水时什么都不该改");
        restore(level, before);
        // 旁边垫高 2 格、上面一格（地面上 3 格）是水：挨着挖空的那一格
        for (BlockPos side : List.of(center.east(2), center.south(2))) {
            level.setBlock(side.above(1), Blocks.DIRT.defaultBlockState(), QUIET);
            level.setBlock(side.above(2), Blocks.DIRT.defaultBlockState(), QUIET);
            level.setBlock(side.above(3), Blocks.WATER.defaultBlockState(), QUIET);
        }
        Map<BlockPos, BlockState> pond = snapshot(level, lab);
        helper.assertFalse(HoannaMidscape.placeFissure(level, lab, center, SEED_STEP), "旁边高处的水塘不该放");
        helper.assertTrue(diff(level, pond).isEmpty(), "旁边高处有水时什么都不该改");
        clearLab(helper);
        helper.succeed();
    }

    /**
     * 辐射陨坑：同一处整块放、和按 4 个象限的 box 倒着顺序分 4 次放，逐格相同；坑真的挖下去了（≤ 地面下 3 格）、坑底有焦土、3~5 个辐晶簇、
     * 正中间的巢穴核心 {@code fallout_crater}；水平不出 {@code StructureRuin.craterReach}（≤ 12）。
     */
    @GameTest(template = LAB, timeoutTicks = 200)
    public static void midscapeCraterChunkSeamless(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox lab = labBox(helper);
        groundLab(helper, 10, ECHoannaNature.DREAD_DIRT.get());
        BlockPos center = helper.absolutePos(new BlockPos(MID, 10, MID));
        Map<BlockPos, BlockState> before = snapshot(level, lab);
        Block radiant = HoannaFeatureBlocks.radiantCluster(Direction.UP).getBlock();
        for (int i = 1; i <= 3; i++) {
            long seed = i * SEED_STEP;
            helper.assertTrue(HoannaMidscape.placeCrater(level, lab, center, seed, level.getMinBuildHeight()), "种子 " + i + "：陨坑应放得下");
            Map<BlockPos, BlockState> whole = diff(level, before);
            restore(level, before);
            List<BoundingBox> quarters = List.of(
                    new BoundingBox(center.getX() + 1, lab.minY(), center.getZ() + 1, lab.maxX(), lab.maxY(), lab.maxZ()),
                    new BoundingBox(lab.minX(), lab.minY(), center.getZ() + 1, center.getX(), lab.maxY(), lab.maxZ()),
                    new BoundingBox(center.getX() + 1, lab.minY(), lab.minZ(), lab.maxX(), lab.maxY(), center.getZ()),
                    new BoundingBox(lab.minX(), lab.minY(), lab.minZ(), center.getX(), lab.maxY(), center.getZ()));
            for (BoundingBox quarter : quarters) {
                HoannaMidscape.placeCrater(level, quarter, center, seed, level.getMinBuildHeight());
            }
            Map<BlockPos, BlockState> split = diff(level, before);
            helper.assertTrue(whole.equals(split), "种子 " + i + "：分 4 块放和整块放不一样（整块 " + whole.size() + " 格、分块 " + split.size() + " 格）");
            long carved = whole.values().stream().filter(BlockState::isAir).count();
            helper.assertTrue(carved >= 300, "种子 " + i + "：坑应挖下去不少格，实际 " + carved);
            int deepest = whole.entrySet().stream().filter(e -> e.getValue().isAir()).mapToInt(e -> e.getKey().getY()).min().orElse(center.getY());
            helper.assertTrue(deepest >= center.getY() - HoannaMidscape.CRATER_DEPTH, "坑底不该低于地面下 3 格，最深 " + (center.getY() - deepest));
            long scorched = whole.values().stream().filter(s -> s.is(Blocks.COARSE_DIRT) || s.is(Blocks.SOUL_SOIL)).count();
            helper.assertTrue(scorched >= 60, "坑底应有焦土，实际 " + scorched);
            long clusters = whole.values().stream().filter(s -> s.is(radiant)).count();
            helper.assertTrue(clusters >= 3 && clusters <= 5, "种子 " + i + "：辐晶簇应 3~5 个，实际 " + clusters);
            BlockPos core = whole.entrySet().stream().filter(e -> e.getValue().is(ECHoannaFramework.NEST_CORE.get())).map(Map.Entry::getKey).findFirst().orElse(null);
            helper.assertTrue(core != null && core.getX() == center.getX() && core.getZ() == center.getZ(), "坑中间应有巢穴核心，实际 " + core);
            assertNestCore(helper, level, core, HoannaMidscape.Kind.FALLOUT_CRATER);
            int reach = StructureRuin.craterReach(HoannaMidscape.craterRadius(seed, center.getX(), center.getZ()));
            helper.assertTrue(reach <= HoannaMidscape.MAX_REACH, "陨坑最远写到 " + reach + " 格，超过 12");
            assertWithin(helper, whole, center, reach, LAB_HEIGHT, reach, "陨坑");
            restore(level, before);
        }
        clearLab(helper);
        helper.succeed();
    }

    // ================================================================== 里程碑

    /**
     * 里程碑：草地上立 3 格强化石 + 顶上里程碑（柱脚的草清掉）；落在水上、树叶上、屋檐下、脚下是原木都不放，也不改任何方块。
     */
    @GameTest(template = LAB)
    public static void milestoneRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox lab = labBox(helper);
        groundLab(helper, 3, Blocks.GRASS_BLOCK);
        BlockPos foot = helper.absolutePos(new BlockPos(4, 4, 4));
        level.setBlock(foot, Blocks.GRASS.defaultBlockState(), QUIET);
        BlockPos pond = helper.absolutePos(new BlockPos(10, 3, 10));
        level.setBlock(pond, Blocks.WATER.defaultBlockState(), QUIET);
        BlockPos leaves = helper.absolutePos(new BlockPos(16, 6, 16));
        level.setBlock(leaves, Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true), QUIET);
        BlockPos log = helper.absolutePos(new BlockPos(20, 4, 20));
        level.setBlock(log, Blocks.OAK_LOG.defaultBlockState(), QUIET);
        BlockPos roofed = helper.absolutePos(new BlockPos(26, 4, 26));
        level.setBlock(roofed.above(8), Blocks.STONE.defaultBlockState(), QUIET);
        Map<BlockPos, BlockState> before = snapshot(level, lab);

        helper.assertTrue(MilestoneFeature.placeAt(level, foot), "草地上应立得起里程碑");
        for (int dy = 0; dy < MilestoneFeature.HEIGHT - 1; dy++) {
            helper.assertTrue(level.getBlockState(foot.above(dy)).is(ECMaterials.FORTIFIED_STONE.get()), "下面 3 格应是强化石");
        }
        helper.assertTrue(level.getBlockState(foot.above(MilestoneFeature.HEIGHT - 1)).is(HoannaFeatureBlocks.ruinMilestone().getBlock()), "顶上应是里程碑");
        helper.assertTrue(diff(level, before).size() == MilestoneFeature.HEIGHT, "只该写柱子那 4 格");
        restore(level, before);

        for (BlockPos bad : List.of(pond.above(), leaves.above(), log.above(), roofed)) {
            helper.assertFalse(MilestoneFeature.placeAt(level, bad), "不该在 " + bad + " 立里程碑");
        }
        helper.assertTrue(diff(level, before).isEmpty(), "放不下时什么都不该改");
        clearLab(helper);
        helper.succeed();
    }

    // ================================================================== 工具

    private static <C extends FeatureConfiguration> boolean run(Feature<C> feature, C config, ServerLevel level, BlockPos origin, long seed) {
        return feature.place(new FeaturePlaceContext<>(Optional.empty(), level, level.getChunkSource().getGenerator(), RandomSource.create(seed), origin, config));
    }

    private static void place(Registry<ConfiguredFeature<?, ?>> registry, String name, ServerLevel level, BlockPos origin, long seed) {
        ConfiguredFeature<?, ?> feature = registry.get(EssentialCraft5.id(name));
        if (feature == null) {
            throw new IllegalStateException("缺 configured_feature " + name);
        }
        feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), origin);
    }

    private static BoundingBox labBox(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        return new BoundingBox(min.getX(), min.getY(), min.getZ(), min.getX() + LAB_SIDE - 1, min.getY() + LAB_HEIGHT - 1, min.getZ() + LAB_SIDE - 1);
    }

    /** 铺地形：y 1~(top − 3) 石头、往上两格泥土（沙地是沙）、y top 是 top 方块，再往上清空 */
    private static void groundLab(GameTestHelper helper, int top, Block surface) {
        ServerLevel level = helper.getLevel();
        Block filler = surface == Blocks.SAND ? Blocks.SAND : surface == ECHoannaNature.DREAD_DIRT.get() ? surface : Blocks.DIRT;
        for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, LAB_SIDE - 1, LAB_HEIGHT - 1, LAB_SIDE - 1)) {
            int y = rel.getY();
            BlockState state = y < top - 2 ? Blocks.STONE.defaultBlockState() : y < top ? filler.defaultBlockState()
                    : y == top ? surface.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(helper.absolutePos(rel), state, QUIET);
        }
    }

    private static void clearLab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, LAB_SIDE - 1, LAB_HEIGHT - 1, LAB_SIDE - 1)) {
            level.setBlock(helper.absolutePos(rel), Blocks.AIR.defaultBlockState(), QUIET);
        }
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, BoundingBox box) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            states.put(pos.immutable(), level.getBlockState(pos));
        }
        return states;
    }

    private static Map<BlockPos, BlockState> diff(ServerLevel level, Map<BlockPos, BlockState> before) {
        Map<BlockPos, BlockState> changed = new LinkedHashMap<>();
        before.forEach((pos, state) -> {
            BlockState now = level.getBlockState(pos);
            if (now != state) {
                changed.put(pos, now);
            }
        });
        return changed;
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> states) {
        states.forEach((pos, state) -> {
            if (level.getBlockState(pos) != state) {
                level.setBlock(pos, state, QUIET);
            }
        });
    }

    private static void assertOnly(GameTestHelper helper, Map<BlockPos, BlockState> changed, Predicate<BlockState> allowed, String what) {
        for (Map.Entry<BlockPos, BlockState> entry : changed.entrySet()) {
            helper.assertTrue(allowed.test(entry.getValue()), what + "不该放 " + entry.getValue() + "（" + entry.getKey() + "）");
        }
    }

    private static void assertWithin(GameTestHelper helper, Map<BlockPos, BlockState> changed, BlockPos center, int dx, int dy, int dz, String what) {
        for (BlockPos pos : changed.keySet()) {
            helper.assertTrue(Math.abs(pos.getX() - center.getX()) <= dx && Math.abs(pos.getZ() - center.getZ()) <= dz
                    && Math.abs(pos.getY() - center.getY()) <= dy, what + "写出了范围：" + pos + "（中心 " + center + "）");
        }
    }

    private static void assertNestCore(GameTestHelper helper, ServerLevel level, BlockPos pos, HoannaMidscape.Kind kind) {
        helper.assertTrue(level.getBlockState(pos).is(ECHoannaFramework.NEST_CORE.get()), "应有巢穴核心：" + pos);
        helper.assertTrue(level.getBlockEntity(pos) instanceof NestCoreBlockEntity core && kind.nestType().equals(core.typeId()),
                "巢穴核心的类型应是 " + kind.nestType());
    }

    private static IntegerProperty intProperty(BlockState state, String name) {
        return (IntegerProperty) state.getBlock().getStateDefinition().getProperty(name);
    }

    private static BooleanProperty boolProperty(BlockState state, String name) {
        return (BooleanProperty) state.getBlock().getStateDefinition().getProperty(name);
    }

    private static Map<Structure, LongSet> copy(Map<Structure, LongSet> references) {
        Map<Structure, LongSet> out = new HashMap<>();
        references.forEach((structure, set) -> out.put(structure, new LongOpenHashSet(set)));
        return out;
    }
}
