package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.DreadCactusBlock;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaNature;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaSurfaceRules;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 第六批 6a M1「群系与海纳尔植物」的测试（batch6a.md §5.1）：群系标签 / 气候 / 配色 / 刷怪表、biome_modifier（EC 矿、上古遗迹、风法师）、
 * 群系普查、地物顺序、地表规则、6 个方块和魔力果实的行为与掉落。
 * <p>
 * 群系、地物、biome_modifier 都是 {@code tools/gen/hoanna_biomes.py} 生成的数据；噪声设置（含地表规则）和掉落表是 runData 生成的——先跑脚本和 runData 再跑这里。
 * 固定种子的海纳尔生成器用 {@link HoannaTestWorld}（普查不生成区块、不碰真海纳尔世界）；群系的设置直接从服务端注册表取（biome_modifier 已经生效）。
 * 方块测试都在超平坦主世界的测试结构里摆，不需要「算在海纳尔」，所以不开 {@code ECHoanna.setTestHoannaOverride}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaNatureTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";

    /**
     * 配色常量表（D3 A，batch6a.md §3.2）：sky、fog、water、water_fog、grass（= foliage）。
     * <b>和 tools/gen/hoanna_biomes.py 的 PALETTE 一字不差</b>：看截图调色时两边一起改，脚本重跑后这条测试确认群系 JSON 真的重新生成了。
     */
    static final Map<ResourceKey<Biome>, int[]> PALETTE = Map.of(
            ECHoanna.DREADLANDS, new int[]{0x3B4260, 0x857B73, 0x3D4A5C, 0x1F2630, 0x7F8A7C},
            ECHoanna.HOANNA_DESERT, new int[]{0x474560, 0xA08A6C, 0x4A4A34, 0x2A2818, 0xA8906A},
            ECHoanna.MAGIC_CORRUPTION, new int[]{0x3C3A5C, 0x7C6A8C, 0x45385F, 0x221C30, 0x86688C},
            ECHoanna.FROZEN_CORRUPTION, new int[]{0x37465E, 0x7F8FA3, 0x3A5878, 0x16263A, 0x5F7FA8},
            ECHoanna.CHAOS_CORRUPTION, new int[]{0x43384E, 0x85655C, 0x3A1A1A, 0x1E1010, 0x6E4440},
            ECHoanna.SHADOW_CORRUPTION, new int[]{0x2A2A36, 0x4A4852, 0x1E2230, 0x0E1016, 0x3A3A3E},
            ECHoanna.HOANNA_SEA, new int[]{0x3B4260, 0x7E8A94, 0x35505E, 0x132028, 0x707C70});

    /**
     * 群系粒子（= tools/gen/hoanna_biomes.py 的 PARTICLES；恐惧之地、沙漠、海不加）：粒子类型、概率。
     * 魔力腐化之地不用女巫冒的品红闪点（彩度太高，满地飘着太扎眼），是 EC 淡紫的小尘粒 {@link #MAGIC_DUST}。
     */
    private static final Map<ResourceKey<Biome>, BiomeParticle> PARTICLES = Map.of(
            ECHoanna.MAGIC_CORRUPTION, new BiomeParticle("minecraft:dust", 0.002F),
            ECHoanna.CHAOS_CORRUPTION, new BiomeParticle("minecraft:crimson_spore", 0.003F),
            ECHoanna.FROZEN_CORRUPTION, new BiomeParticle("minecraft:snowflake", 0.003F),
            ECHoanna.SHADOW_CORRUPTION, new BiomeParticle("minecraft:ash", 0.006F));

    /** 魔力腐化之地的尘粒：颜色 (0.59, 0.52, 0.75) ≈ #9684BE（EC 淡紫）、个头 0.6 */
    private static final float[] MAGIC_DUST = {0.59F, 0.52F, 0.75F, 0.6F};

    private record BiomeParticle(String type, float probability) {
    }

    /** 主世界 6 种 EC 矿（placed feature 注册名），海纳尔经 hoanna_ores.json 复用 */
    private static final List<String> EC_ORES = List.of("elemental_infused_ore", "fire_infused_ore", "water_infused_ore",
            "earth_infused_ore", "air_infused_ore", "mithriline_ore");

    /** 普查：65 536 × 65 536 格、步长 512，共 128 × 128 = 16 384 点，y 取海平面（和 tools/gen/hoanna_climate/climate.py --test 同一份） */
    private static final int CENSUS_HALF = 32768;
    private static final int CENSUS_STEP = 512;

    /** 各群系上的 EC 植被地物（D11 A；= hoanna_features.py 的 BY_BIOME 第 9 步里 6a 的那几项；恐惧之地的恐惧仙人掌是第十三批 13d 补上的） */
    private static final Map<ResourceKey<Biome>, List<ResourceKey<PlacedFeature>>> EC_VEGETATION = Map.of(
            ECHoanna.MAGIC_CORRUPTION, List.of(ECHoannaNature.HOANNA_TREES, ECHoannaNature.GREEN_FLOWER_PATCH,
                    ECHoannaNature.HOANNA_GRASS_PATCH, ECHoannaNature.MAGICAL_FRUIT_PATCH),
            ECHoanna.CHAOS_CORRUPTION, List.of(ECHoannaNature.HOANNA_TREES, ECHoannaNature.GREEN_FLOWER_PATCH, ECHoannaNature.HOANNA_GRASS_PATCH),
            ECHoanna.FROZEN_CORRUPTION, List.of(ECHoannaNature.HOANNA_TREES, ECHoannaNature.GREEN_FLOWER_PATCH, ECHoannaNature.HOANNA_GRASS_PATCH),
            ECHoanna.SHADOW_CORRUPTION, List.of(ECHoannaNature.HOANNA_TREES, ECHoannaNature.GREEN_FLOWER_PATCH, ECHoannaNature.HOANNA_GRASS_PATCH),
            ECHoanna.DREADLANDS, List.of(ECHoannaNature.DREAD_CACTUS_PATCH),
            ECHoanna.HOANNA_DESERT, List.of(ECHoannaNature.DREAD_CACTUS_PATCH),
            ECHoanna.HOANNA_SEA, List.of());

    /** 地物稀有度（= hoanna_features.py 的 *_RARITY；上古遗迹是 D12 A 按 64 × 64 普查微调后的 3：rarity 2 实测每 21.3 区块一座，3 约每 32 区块；魔力果实第十三批 13d 由 48 改 24） */
    private static final Map<ResourceKey<PlacedFeature>, Integer> RARITY = Map.of(
            ECHoannaNature.GREEN_FLOWER_PATCH, 8,
            ECHoannaNature.HOANNA_GRASS_PATCH, 2,
            ECHoannaNature.DREAD_CACTUS_PATCH, 2,
            ECHoannaNature.MAGICAL_FRUIT_PATCH, 24,
            ECHoannaNature.ELDER_MRUCU_HOANNA, 3);

    private HoannaNatureTests() {
    }

    // ------------------------------------------------------------------
    // 群系数据
    // ------------------------------------------------------------------

    /** is_hoanna 7 个、hoanna_land 6 个（不含海）；都不在任何 #minecraft:is_* / #forge:is_* 里；恐惧之地和沙漠会融化雪傀儡 */
    @GameTest(template = EMPTY)
    public static void hoannaBiomesTagged(GameTestHelper helper) {
        Registry<Biome> biomes = biomes(helper);
        List<String> problems = new ArrayList<>();
        int hoanna = biomes.getTag(ECHoanna.IS_HOANNA).map(HolderSet.Named::size).orElse(-1);
        int land = biomes.getTag(ECHoanna.HOANNA_LAND).map(HolderSet.Named::size).orElse(-1);
        helper.assertTrue(hoanna == 7, "#essentialcraft5:is_hoanna 应有 7 个群系，实际 " + hoanna);
        helper.assertTrue(land == 6, "#essentialcraft5:hoanna_land 应有 6 个群系，实际 " + land);
        for (ResourceKey<Biome> key : ECHoanna.BIOMES) {
            Holder.Reference<Biome> holder = biomes.getHolderOrThrow(key);
            if (!holder.is(ECHoanna.IS_HOANNA)) {
                problems.add(key.location() + " 不在 is_hoanna");
            }
            if (holder.is(ECHoanna.HOANNA_LAND) == key.equals(ECHoanna.HOANNA_SEA)) {
                problems.add(key.location() + (key.equals(ECHoanna.HOANNA_SEA) ? " 不应" : " 应") + "在 hoanna_land");
            }
            holder.tags().filter(HoannaNatureTests::isDimensionTag)
                    .forEach(tag -> problems.add(key.location() + " 不应在 #" + tag.location() + "（会把整套主世界 / 下界 / 末地的修改器和结构灌进海纳尔）"));
            boolean melts = key.equals(ECHoanna.DREADLANDS) || key.equals(ECHoanna.HOANNA_DESERT);
            if (holder.is(BiomeTags.SNOW_GOLEM_MELTS) != melts) {
                problems.add(key.location() + (melts ? " 应" : " 不应") + "在 #minecraft:snow_golem_melts");
            }
        }
        helper.assertTrue(problems.isEmpty(), "群系标签不对：" + problems);
        helper.succeed();
    }

    /** 气候：四个腐化之地和海会下雨、温度 0.5、降水 0.5（冰冻也是 0.5，照原作）；恐惧之地、沙漠不下雨、2.0 / 0.0 */
    @GameTest(template = EMPTY)
    public static void hoannaBiomeClimate(GameTestHelper helper) {
        Registry<Biome> biomes = biomes(helper);
        for (ResourceKey<Biome> key : ECHoanna.BIOMES) {
            Biome biome = biomes.getOrThrow(key);
            boolean dry = key.equals(ECHoanna.DREADLANDS) || key.equals(ECHoanna.HOANNA_DESERT);
            Biome.ClimateSettings climate = biome.getModifiedClimateSettings();
            helper.assertTrue(climate.hasPrecipitation() == !dry, key.location() + " 下不下雨不对：" + climate);
            helper.assertTrue(climate.temperature() == (dry ? 2.0F : 0.5F), key.location() + " 温度不对：" + climate);
            helper.assertTrue(climate.downfall() == (dry ? 0.0F : 0.5F), key.location() + " 降水不对：" + climate);
        }
        helper.succeed();
    }

    /** 7 × 6 颜色等于 {@link #PALETTE}（草 = 叶）；四个群系的粒子类型和概率照 {@link #PARTICLES}，魔力腐化之地另查尘粒的颜色和个头 */
    @GameTest(template = EMPTY)
    public static void hoannaBiomePalette(GameTestHelper helper) {
        Registry<Biome> biomes = biomes(helper);
        List<String> problems = new ArrayList<>();
        for (ResourceKey<Biome> key : ECHoanna.BIOMES) {
            BiomeSpecialEffects effects = biomes.getOrThrow(key).getSpecialEffects();
            int[] expected = PALETTE.get(key);
            int[] actual = {effects.getSkyColor(), effects.getFogColor(), effects.getWaterColor(), effects.getWaterFogColor(),
                    effects.getGrassColorOverride().orElse(-1), effects.getFoliageColorOverride().orElse(-1)};
            String[] names = {"sky", "fog", "water", "water_fog", "grass", "foliage"};
            for (int i = 0; i < names.length; i++) {
                int want = expected[Math.min(i, 4)];
                if (actual[i] != want) {
                    problems.add(String.format("%s 的 %s 应为 #%06X，实际 #%06X", key.location(), names[i], want, actual[i]));
                }
            }
            Optional<AmbientParticleSettings> particle = effects.getAmbientParticleSettings();
            BiomeParticle wantParticle = PARTICLES.get(key);
            if (wantParticle == null) {
                if (particle.isPresent()) {
                    problems.add(key.location() + " 不应有群系粒子");
                }
            } else {
                String got = particle.map(p -> ForgeRegistries.PARTICLE_TYPES.getKey(p.getOptions().getType())).map(ResourceLocation::toString).orElse("无");
                if (!wantParticle.type().equals(got)) {
                    problems.add(key.location() + " 的群系粒子应为 " + wantParticle.type() + "，实际 " + got);
                } else {
                    float probability = particleProbability(particle.get());
                    if (!(Math.abs(probability - wantParticle.probability()) <= 1.0E-6F)) {
                        problems.add(key.location() + " 的群系粒子概率应为 " + wantParticle.probability() + "，实际 " + probability);
                    }
                    if (key.equals(ECHoanna.MAGIC_CORRUPTION)) {
                        if (particle.get().getOptions() instanceof DustParticleOptions dust) {
                            float[] actualDust = {dust.getColor().x(), dust.getColor().y(), dust.getColor().z(), dust.getScale()};
                            for (int i = 0; i < MAGIC_DUST.length; i++) {
                                if (Math.abs(actualDust[i] - MAGIC_DUST[i]) > 1.0E-4F) {
                                    problems.add(String.format("魔力腐化之地的尘粒应为颜色 (%.2f, %.2f, %.2f)、个头 %.2f，实际 (%.3f, %.3f, %.3f)、%.3f",
                                            MAGIC_DUST[0], MAGIC_DUST[1], MAGIC_DUST[2], MAGIC_DUST[3],
                                            actualDust[0], actualDust[1], actualDust[2], actualDust[3]));
                                    break;
                                }
                            }
                        } else {
                            problems.add("魔力腐化之地的群系粒子应是 DustParticleOptions，实际 " + particle.get().getOptions());
                        }
                    }
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "群系配色不对（先重跑 tools/gen/hoanna_biomes.py，改色要两边一起改）：" + problems);
        helper.succeed();
    }

    /**
     * 修改器生效后：被动类别（动物、蝙蝠、鱼、美西螈……）全空；怪物表是原版平原的（僵尸 95）外加风法师 2 / 1~6（wind_mage_spawns_hoanna.json）；
     * 主世界的平原仍有风法师（原有的 wind_mage_spawns.json 不动）。
     */
    @GameTest(template = EMPTY)
    public static void hoannaBiomesNoPassiveSpawns(GameTestHelper helper) {
        Registry<Biome> biomes = biomes(helper);
        EntityType<?> windMage = ForgeRegistries.ENTITY_TYPES.getValue(EssentialCraft5.id("wind_mage"));
        boolean hasWindMage = ForgeRegistries.ENTITY_TYPES.containsKey(EssentialCraft5.id("wind_mage"));
        List<String> problems = new ArrayList<>();
        for (ResourceKey<Biome> key : ECHoanna.BIOMES) {
            MobSpawnSettings mobs = biomes.getOrThrow(key).getMobSettings();
            for (MobCategory category : MobCategory.values()) {
                if (category != MobCategory.MONSTER && !mobs.getMobs(category).isEmpty()) {
                    problems.add(key.location() + " 的 " + category.getName() + " 刷怪表应为空：" + mobs.getMobs(category).unwrap());
                }
            }
            List<MobSpawnSettings.SpawnerData> monsters = mobs.getMobs(MobCategory.MONSTER).unwrap();
            if (monsters.stream().noneMatch(d -> d.type == EntityType.ZOMBIE && d.getWeight().asInt() == 95)) {
                problems.add(key.location() + " 应有僵尸 95（原版平原的怪物表）");
            }
            if (hasWindMage && monsters.stream().noneMatch(d -> d.type == windMage && d.getWeight().asInt() == 2 && d.minCount == 1 && d.maxCount == 6)) {
                problems.add(key.location() + " 应有风法师 2 / 1~6（forge/biome_modifier/wind_mage_spawns_hoanna.json）");
            }
        }
        if (hasWindMage && biomes.getOrThrow(Biomes.PLAINS).getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream()
                .noneMatch(d -> d.type == windMage)) {
            problems.add("主世界平原的风法师不见了");
        }
        helper.assertTrue(problems.isEmpty(), "刷怪表不对：" + problems);
        helper.succeed();
    }

    /**
     * biome_modifier：7 个海纳尔群系的 underground_ores 有主世界 6 种 EC 矿、underground_structures 有 elder_mrucu_hoanna、没有主世界那份 elder_mrucu；
     * 平原反过来（有 elder_mrucu、没有海纳尔版）。海纳尔版的第一个放置条件是 rarity_filter 3（D12 A，普查后由 2 改 3），各植被地物的稀有度照 §5.1。
     */
    @GameTest(template = EMPTY)
    public static void hoannaOresAndRuinsInHoannaOnly(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        Registry<PlacedFeature> placed = access.registryOrThrow(Registries.PLACED_FEATURE);
        List<String> problems = new ArrayList<>();
        PlacedFeature elder = placed.get(EssentialCraft5.id("elder_mrucu"));
        PlacedFeature elderHoanna = placed.get(ECHoannaNature.ELDER_MRUCU_HOANNA);
        helper.assertTrue(elder != null && elderHoanna != null, "缺 placed_feature elder_mrucu 或 elder_mrucu_hoanna");
        List<PlacedFeature> ores = new ArrayList<>();
        for (String ore : EC_ORES) {
            PlacedFeature feature = placed.get(EssentialCraft5.id(ore));
            helper.assertTrue(feature != null, "缺 placed_feature " + ore);
            ores.add(feature);
        }
        for (ResourceKey<Biome> key : ECHoanna.BIOMES) {
            List<HolderSet<PlacedFeature>> steps = biomes.getOrThrow(key).getGenerationSettings().features();
            for (int i = 0; i < ores.size(); i++) {
                if (!stepHas(steps, GenerationStep.Decoration.UNDERGROUND_ORES, ores.get(i))) {
                    problems.add(key.location() + " 的 underground_ores 缺 " + EC_ORES.get(i));
                }
            }
            if (!stepHas(steps, GenerationStep.Decoration.UNDERGROUND_STRUCTURES, elderHoanna)) {
                problems.add(key.location() + " 的 underground_structures 缺 elder_mrucu_hoanna");
            }
            if (biomes.getOrThrow(key).getGenerationSettings().hasFeature(elder)) {
                problems.add(key.location() + " 不应有主世界的 elder_mrucu（稀有度 16 那份）");
            }
        }
        BiomeGenerationSettings plains = biomes.getOrThrow(Biomes.PLAINS).getGenerationSettings();
        helper.assertTrue(plains.hasFeature(elder) && !plains.hasFeature(elderHoanna), "平原应只有主世界那份 elder_mrucu");
        for (Map.Entry<ResourceKey<PlacedFeature>, Integer> entry : RARITY.entrySet()) {
            PlacedFeature feature = placed.get(entry.getKey());
            Integer chance = feature == null ? null : rarityOf(feature);
            if (!entry.getValue().equals(chance)) {
                problems.add(entry.getKey().location() + " 的第一个放置条件应是 rarity_filter " + entry.getValue() + "，实际 " + chance);
            }
        }
        helper.assertTrue(problems.isEmpty(), "海纳尔的矿 / 遗迹 / 地物不对：" + problems);
        helper.succeed();
    }

    /**
     * 群系普查（固定种子 {@link HoannaTestWorld#SEED}，不生成区块）：按四分之一坐标 65 536 × 65 536 格、步长 512 取 16 384 点，y 取海平面，
     * 陆地上五个主群系各 17~23%、暗影 1~3%，海不为 0。
     * <p>
     * 计划原写 16 384 格、步长 128；温度噪声一个起伏约 4 000 格，那么小的窗口里只有十几个起伏，占比随种子在 12~30% 之间乱跳（M1 用
     * tools/gen/hoanna_climate 离线复算过），所以放大窗口、点数不变。这个种子离线复算是 冰冻 20.0 / 魔力 20.6 / 混沌 20.3 / 恐惧之地 19.3 / 沙漠 17.6、暗影 2.2%、海 30.6%。
     * 不达标就用 climate.py 普查、改 hoanna_biomes.py 的 T_BOUNDS / SHADOW_HUMIDITY 重跑。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void hoannaBiomeCensus(GameTestHelper helper) {
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(helper.getLevel().getServer());
        Climate.Sampler sampler = hoanna.randomState().sampler();
        BiomeSource source = hoanna.biomeSource();
        int qy = QuartPos.fromBlock(63);
        Map<ResourceKey<Biome>, Integer> counts = new LinkedHashMap<>();
        ECHoanna.BIOMES.forEach(key -> counts.put(key, 0));
        int total = 0;
        List<String> strangers = new ArrayList<>();
        for (int x = -CENSUS_HALF; x < CENSUS_HALF; x += CENSUS_STEP) {
            for (int z = -CENSUS_HALF; z < CENSUS_HALF; z += CENSUS_STEP) {
                Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), qy, QuartPos.fromBlock(z), sampler);
                Optional<ResourceKey<Biome>> key = biome.unwrapKey();
                if (key.isEmpty() || !counts.containsKey(key.get())) {
                    strangers.add(String.valueOf(key.map(ResourceKey::location).orElse(null)));
                } else {
                    counts.merge(key.get(), 1, Integer::sum);
                }
                total++;
            }
        }
        helper.assertTrue(strangers.isEmpty(), "普查出了海纳尔以外的群系：" + strangers.stream().distinct().toList());
        int all = total;
        int land = all - counts.get(ECHoanna.HOANNA_SEA);
        Map<String, String> shares = new LinkedHashMap<>();
        counts.forEach((key, n) -> shares.put(key.location().getPath(),
                String.format("%.1f%%", 100.0 * n / (key.equals(ECHoanna.HOANNA_SEA) ? all : land))));
        String report = "（陆地占比，海按全部点）" + shares;
        helper.assertTrue(counts.get(ECHoanna.HOANNA_SEA) > 0, "普查里没有海纳尔之海 " + report);
        for (ResourceKey<Biome> key : List.of(ECHoanna.FROZEN_CORRUPTION, ECHoanna.MAGIC_CORRUPTION, ECHoanna.CHAOS_CORRUPTION,
                ECHoanna.DREADLANDS, ECHoanna.HOANNA_DESERT)) {
            double share = 100.0 * counts.get(key) / land;
            helper.assertTrue(share >= 17.0 && share <= 23.0, key.location().getPath() + " 应占陆地 17~23% " + report);
        }
        double shadow = 100.0 * counts.get(ECHoanna.SHADOW_CORRUPTION) / land;
        helper.assertTrue(shadow >= 1.0 && shadow <= 3.0, "暗影腐化之地应占陆地 1~3% " + report);
        EssentialCraft5.LOGGER.info("[海纳尔] 群系普查（种子 {}，{} 点）：{}", hoanna.seed(), total, report);
        helper.succeed();
    }

    /**
     * 地物顺序：海纳尔 7 个群系（biome_modifier 生效后）按维度排序不矛盾（否则启动即崩「Feature order cycle」）；
     * 各群系的 EC 植被照 D11 A；腐化之地的「花」地物是绿花（草方块用骨粉会长绿花），别的群系没有花地物。
     */
    @GameTest(template = EMPTY)
    public static void hoannaFeatureOrderConsistent(GameTestHelper helper) {
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(helper.getLevel().getServer());
        List<Holder<Biome>> possible = List.copyOf(hoanna.biomeSource().possibleBiomes());
        helper.assertTrue(possible.size() == 7, "海纳尔群系源应有 7 个群系，实际 " + possible.size());
        try {
            FeatureSorter.buildFeaturesPerStep(possible, holder -> holder.value().getGenerationSettings().features(), true);
        } catch (IllegalStateException e) {
            helper.fail("海纳尔的地物顺序矛盾（hoanna_biomes.py 的植被要从主序列按顺序取）：" + e.getMessage());
            return;
        }
        RegistryAccess access = helper.getLevel().registryAccess();
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        Registry<PlacedFeature> placed = access.registryOrThrow(Registries.PLACED_FEATURE);
        ConfiguredFeature<?, ?> greenFlowers = access.registryOrThrow(Registries.CONFIGURED_FEATURE).get(ECHoannaNature.GREEN_FLOWER_PATCH_CONFIGURED);
        helper.assertTrue(greenFlowers != null, "缺 configured_feature green_flower_patch");
        List<ResourceKey<PlacedFeature>> all = List.of(ECHoannaNature.HOANNA_TREES, ECHoannaNature.GREEN_FLOWER_PATCH,
                ECHoannaNature.HOANNA_GRASS_PATCH, ECHoannaNature.DREAD_CACTUS_PATCH, ECHoannaNature.MAGICAL_FRUIT_PATCH);
        List<String> problems = new ArrayList<>();
        for (ResourceKey<Biome> key : ECHoanna.BIOMES) {
            BiomeGenerationSettings settings = biomes.getOrThrow(key).getGenerationSettings();
            List<ResourceKey<PlacedFeature>> wanted = EC_VEGETATION.get(key);
            for (ResourceKey<PlacedFeature> featureKey : all) {
                PlacedFeature feature = placed.get(featureKey);
                if (feature == null) {
                    problems.add("缺 placed_feature " + featureKey.location());
                    continue;
                }
                boolean has = stepHas(settings.features(), GenerationStep.Decoration.VEGETAL_DECORATION, feature);
                if (has != wanted.contains(featureKey)) {
                    problems.add(key.location() + (has ? " 不应有 " : " 应有 ") + featureKey.location().getPath());
                }
            }
            boolean flowers = wanted.contains(ECHoannaNature.GREEN_FLOWER_PATCH);
            List<ConfiguredFeature<?, ?>> flowerFeatures = settings.getFlowerFeatures();
            boolean ok = flowers ? flowerFeatures.size() == 1 && flowerFeatures.get(0) == greenFlowers : flowerFeatures.isEmpty();
            if (!ok) {
                problems.add(key.location() + " 的花地物应" + (flowers ? "只有绿花" : "为空") + "，实际 " + flowerFeatures.size() + " 个");
            }
        }
        helper.assertTrue(problems.isEmpty(), "海纳尔植被不对：" + problems);
        helper.succeed();
    }

    /**
     * 地表规则：{@link HoannaSurfaceRules#rules()} 3 条、每条最外层都是 above_preliminary_surface（不包会把洞穴地面也铺成恐惧泥土 / 沙子）；
     * 噪声设置 essentialcraft5:hoanna 的地表规则开头就是这 3 条、后面接原版主世界规则（改了规则没重跑 runData 时这里会挂）。
     */
    @GameTest(template = EMPTY)
    public static void hoannaSurfaceRulesWrapped(GameTestHelper helper) {
        List<SurfaceRules.RuleSource> rules = HoannaSurfaceRules.rules();
        helper.assertTrue(rules.size() == 3, "海纳尔地表规则应有 3 条（恐惧之地、沙漠、海），实际 " + rules.size());
        List<JsonElement> encoded = new ArrayList<>();
        for (SurfaceRules.RuleSource rule : rules) {
            JsonObject json = encode(rule).getAsJsonObject();
            encoded.add(json);
            boolean wrapped = "minecraft:condition".equals(json.get("type").getAsString())
                    && "minecraft:above_preliminary_surface".equals(json.getAsJsonObject("if_true").get("type").getAsString());
            helper.assertTrue(wrapped, "每条地表规则最外层都要是 ifTrue(abovePreliminarySurface, …)：" + json);
        }
        helper.assertTrue(encoded.get(0).toString().contains("essentialcraft5:dread_dirt"), "第一条应把恐惧之地铺成恐惧泥土：" + encoded.get(0));
        NoiseGeneratorSettings settings = helper.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).get(ECHoanna.NOISE_SETTINGS);
        helper.assertTrue(settings != null, "缺噪声设置 essentialcraft5:hoanna（先跑 runData）");
        JsonObject generated = encode(settings.surfaceRule()).getAsJsonObject();
        JsonArray sequence = generated.getAsJsonArray("sequence");
        helper.assertTrue("minecraft:sequence".equals(generated.get("type").getAsString()) && sequence != null && sequence.size() == rules.size() + 1,
                "噪声设置的地表规则应是 sequence(海纳尔 " + rules.size() + " 条, 原版主世界)（没重跑 runData？）");
        for (int i = 0; i < rules.size(); i++) {
            helper.assertTrue(encoded.get(i).equals(sequence.get(i)), "噪声设置里第 " + (i + 1) + " 条地表规则和代码不一致（改了 HoannaSurfaceRules 没重跑 runData？）");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 方块
    // ------------------------------------------------------------------

    /**
     * 恐惧仙人掌：沙上活、两格叠放活（原版仙人掌不能长在它上面）、恐惧泥土 / 草方块上掉、旁边有实心方块掉；
     * 随机刻照原版长高，最多 3 格；寻路同原版仙人掌（仙人掌那格 DAMAGE_OTHER、旁边 DANGER_OTHER，审查 M1-2：以前原版寻路只认 Blocks.CACTUS，
     * 宠物和怪会贴着走、反复中毒）。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void dreadCactusRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cactus = ECHoannaNature.DREAD_CACTUS.get().defaultBlockState();
        // 沙上活、两格叠放活
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.SAND);
        helper.assertTrue(cactus.canSurvive(level, helper.absolutePos(new BlockPos(1, 2, 1))), "恐惧仙人掌应能长在沙子上");
        helper.setBlock(new BlockPos(1, 2, 1), cactus);
        helper.assertTrue(cactus.canSurvive(level, helper.absolutePos(new BlockPos(1, 3, 1))), "恐惧仙人掌应能叠在恐惧仙人掌上（canSustainPlant）");
        helper.assertFalse(Blocks.CACTUS.defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(1, 3, 1))),
                "原版仙人掌不应能长在恐惧仙人掌上");
        // 寻路：和旁边摆的原版仙人掌逐格对照（仙人掌那格、贴着它、脚下是石头的那格）
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 8), Blocks.SAND);
        helper.setBlock(new BlockPos(1, 2, 8), Blocks.CACTUS);
        helper.setBlock(new BlockPos(2, 1, 8), Blocks.STONE);
        BlockPathTypes dreadSelf = pathType(helper, new BlockPos(1, 2, 1));
        BlockPathTypes dreadBeside = pathType(helper, new BlockPos(2, 2, 1));
        BlockPathTypes vanillaSelf = pathType(helper, new BlockPos(1, 2, 8));
        BlockPathTypes vanillaBeside = pathType(helper, new BlockPos(2, 2, 8));
        helper.assertTrue(vanillaSelf == BlockPathTypes.DAMAGE_OTHER && vanillaBeside == BlockPathTypes.DANGER_OTHER,
                "对照组原版仙人掌应是 DAMAGE_OTHER / 旁边 DANGER_OTHER，实际 " + vanillaSelf + " / " + vanillaBeside);
        helper.assertTrue(dreadSelf == vanillaSelf, "恐惧仙人掌那格的寻路类型应同原版仙人掌 " + vanillaSelf + "，实际 " + dreadSelf);
        helper.assertTrue(dreadBeside == vanillaBeside, "贴着恐惧仙人掌的格子应同原版 " + vanillaBeside + "（生物绕开），实际 " + dreadBeside);
        // 恐惧泥土、草方块上掉
        helper.setBlock(new BlockPos(3, 1, 1), ECHoannaNature.DREAD_DIRT.get());
        helper.assertFalse(cactus.canSurvive(level, helper.absolutePos(new BlockPos(3, 2, 1))), "恐惧仙人掌不应能长在恐惧泥土上");
        helper.setBlock(new BlockPos(5, 1, 1), Blocks.GRASS_BLOCK);
        helper.assertFalse(cactus.canSurvive(level, helper.absolutePos(new BlockPos(5, 2, 1))), "恐惧仙人掌不应能长在草方块上");
        // 旁边有实心方块掉（照原版仙人掌）
        helper.setBlock(new BlockPos(7, 1, 1), Blocks.SAND);
        helper.setBlock(new BlockPos(8, 2, 1), Blocks.STONE);
        helper.assertFalse(cactus.canSurvive(level, helper.absolutePos(new BlockPos(7, 2, 1))), "旁边有实心方块时恐惧仙人掌不应能活");
        // 随机刻长高，最多 3 格
        BlockPos base = new BlockPos(5, 1, 5);
        helper.setBlock(base, Blocks.SAND);
        helper.setBlock(base.above(), cactus);
        RandomSource random = RandomSource.create(20260929L);
        for (int round = 0; round < 100; round++) {
            for (int dy = 1; dy <= 5; dy++) {
                BlockPos pos = helper.absolutePos(base.above(dy));
                BlockState state = level.getBlockState(pos);
                if (state.is(ECHoannaNature.DREAD_CACTUS.get())) {
                    state.randomTick(level, pos, random);
                }
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            helper.assertBlockPresent(ECHoannaNature.DREAD_CACTUS.get(), base.above(dy));
        }
        helper.assertBlockPresent(Blocks.AIR, base.above(4));
        helper.succeed();
    }

    /**
     * 恐惧仙人掌的中毒有节流（原作每刻刷新 = 每秒 20 个效果包）：猪连着 30 刻碰它，有中毒，第 30 刻的剩余时间比第 5 刻少（没被每刻刷新）；
     * 创造模式的玩家不中毒，生存的玩家中毒。僵尸是亡灵、本来就不吃中毒，所以用猪（计划原写僵尸）。
     */
    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void dreadCactusPoisonThrottled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.SAND);
        helper.setBlock(new BlockPos(1, 2, 1), ECHoannaNature.DREAD_CACTUS.get());
        BlockPos cactusPos = helper.absolutePos(new BlockPos(1, 2, 1));
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 3));

        ServerPlayer creative = ProtectionFrameworkTests.realPlayer(helper);
        creative.setGameMode(GameType.CREATIVE);
        level.getBlockState(cactusPos).entityInside(level, cactusPos, creative);
        helper.assertTrue(creative.getEffect(MobEffects.POISON) == null, "创造模式的玩家不应中毒");
        ServerPlayer survival = ProtectionFrameworkTests.realPlayer(helper);
        level.getBlockState(cactusPos).entityInside(level, cactusPos, survival);
        MobEffectInstance survivalPoison = survival.getEffect(MobEffects.POISON);
        helper.assertTrue(survivalPoison != null && survivalPoison.getAmplifier() == 0 && survivalPoison.getDuration() == DreadCactusBlock.POISON_TICKS,
                "生存的玩家碰到应中毒 I、" + DreadCactusBlock.POISON_TICKS + " 刻，实际 " + survivalPoison);

        int[] durationAt5 = {-1};
        for (int tick = 1; tick <= 30; tick++) {
            int now = tick;
            helper.runAtTickTime(tick, () -> {
                level.getBlockState(cactusPos).entityInside(level, cactusPos, pig);
                if (now == 5) {
                    durationAt5[0] = poisonTicks(pig);
                }
            });
        }
        helper.runAtTickTime(30, () -> {
            int durationAt30 = poisonTicks(pig);
            helper.assertTrue(durationAt5[0] > 0 && durationAt30 > 0, "猪碰到恐惧仙人掌应中毒（第 5 刻 " + durationAt5[0] + "，第 30 刻 " + durationAt30 + "）");
            helper.assertTrue(durationAt30 < durationAt5[0],
                    "中毒不应每刻刷新：第 30 刻剩 " + durationAt30 + " 刻，应少于第 5 刻的 " + durationAt5[0]);
            helper.succeed();
        });
    }

    /** 草丛掉落：剪刀 → 本体；徒手 1 万次只出小麦种子、每次最多 1 个、12.5% ± 6σ（约 ±2%）；时运 III 不提高 */
    @GameTest(template = EMPTY)
    public static void hoannaTallGrassLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockState grass = ECHoannaNature.HOANNA_TALL_GRASS.get().defaultBlockState();
        List<ItemStack> sheared = Block.getDrops(grass, level, pos, null, null, new ItemStack(Items.SHEARS));
        helper.assertTrue(sheared.size() == 1 && sheared.get(0).is(ECHoannaNature.HOANNA_TALL_GRASS.get().asItem()) && sheared.get(0).getCount() == 1,
                "剪刀剪草丛应掉本体 1 个，实际 " + sheared);
        int trials = 10000;
        double p = ECHoannaNature.TALL_GRASS_SEED_CHANCE;
        double tolerance = 6 * Math.sqrt(p * (1 - p) / trials);
        for (ItemStack tool : List.of(ItemStack.EMPTY, enchanted(Items.DIAMOND_HOE, Enchantments.BLOCK_FORTUNE, 3))) {
            int seeds = 0;
            for (int i = 0; i < trials; i++) {
                for (ItemStack stack : Block.getDrops(grass, level, pos, null, null, tool)) {
                    helper.assertTrue(stack.is(Items.WHEAT_SEEDS) && stack.getCount() == 1, "草丛只应掉 1 个小麦种子，实际 " + stack);
                    seeds++;
                }
            }
            double frequency = seeds / (double) trials;
            helper.assertTrue(Math.abs(frequency - p) <= tolerance,
                    (tool.isEmpty() ? "徒手" : "时运 III") + " 掉种子的频率 " + String.format("%.4f", frequency) + "，应为 " + p + " ± " + String.format("%.4f", tolerance));
        }
        helper.succeed();
    }

    /** 魔力果实块：空手 500 次 3~7 个果实；时运 III 3~9（封顶 9，出现过 8 以上）；精准采集掉本体；果实补 8 点饥饿、饱和度系数 10 */
    @GameTest(template = EMPTY)
    public static void magicalFruitBlockLootAndFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockState block = ECHoannaNature.MAGICAL_FRUIT_BLOCK.get().defaultBlockState();
        Item fruit = ECHoannaNature.MAGICAL_FRUIT.get();
        int[] plain = fruitCounts(helper, level, pos, block, ItemStack.EMPTY, fruit, 500);
        helper.assertTrue(plain[0] >= ECHoannaNature.FRUIT_MIN && plain[1] <= ECHoannaNature.FRUIT_MAX,
                "空手应掉 3~7 个，实际 " + plain[0] + "~" + plain[1]);
        int[] fortune = fruitCounts(helper, level, pos, block, enchanted(Items.DIAMOND_AXE, Enchantments.BLOCK_FORTUNE, 3), fruit, 500);
        helper.assertTrue(fortune[0] >= ECHoannaNature.FRUIT_MIN && fortune[1] <= ECHoannaNature.FRUIT_CAP && fortune[1] > ECHoannaNature.FRUIT_MAX,
                "时运 III 应掉 3~9 个且出现过 8 以上，实际 " + fortune[0] + "~" + fortune[1]);
        List<ItemStack> silk = Block.getDrops(block, level, pos, null, null, enchanted(Items.DIAMOND_AXE, Enchantments.SILK_TOUCH, 1));
        helper.assertTrue(silk.size() == 1 && silk.get(0).is(ECHoannaNature.MAGICAL_FRUIT_BLOCK.get().asItem()), "精准采集应掉魔力果实块本身，实际 " + silk);
        FoodProperties food = new ItemStack(fruit).getFoodProperties(null);
        helper.assertTrue(food != null && food.getNutrition() == ECHoannaNature.FRUIT_NUTRITION
                        && food.getSaturationModifier() == ECHoannaNature.FRUIT_SATURATION,
                "魔力果实应是 8 点饥饿、饱和度系数 10（原作 ItemFood(8, 10F)）");
        helper.succeed();
    }

    /**
     * 恐惧泥土、魔力果实块、根系（和 M7 的混凝土，注册了才查）完全不透光（原作不透光度 0 是 bug）；恐惧泥土在 #dirt 与铲子的挖掘标签里，
     * 枯灌木、绿花、草丛能长在上面；根系不是原木；草丛可被替换、不能叠在草丛上（原作能叠成柱）。
     */
    @GameTest(template = EMPTY)
    public static void hoannaBlocksOpaqueAndTagged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos probe = helper.absolutePos(new BlockPos(0, 3, 0));
        List<Block> solid = new ArrayList<>(List.of(ECHoannaNature.DREAD_DIRT.get(), ECHoannaNature.MAGICAL_FRUIT_BLOCK.get(), ECHoannaNature.ROOT.get()));
        ResourceLocation concrete = EssentialCraft5.id("concrete");
        if (ForgeRegistries.BLOCKS.containsKey(concrete)) {
            solid.add(ForgeRegistries.BLOCKS.getValue(concrete));
        }
        for (Block block : solid) {
            BlockState state = block.defaultBlockState();
            helper.assertTrue(!state.propagatesSkylightDown(level, probe) && state.getLightBlock(level, probe) == 15 && state.canOcclude(),
                    block + " 应完全不透光");
        }
        BlockState dirt = ECHoannaNature.DREAD_DIRT.get().defaultBlockState();
        helper.assertTrue(dirt.is(BlockTags.DIRT) && dirt.is(BlockTags.MINEABLE_WITH_SHOVEL), "恐惧泥土应在 #minecraft:dirt 和 mineable/shovel");
        helper.assertTrue(ECHoannaNature.ROOT.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE)
                && !ECHoannaNature.ROOT.get().defaultBlockState().is(BlockTags.LOGS), "根系应是斧挖、不是原木");
        helper.assertTrue(ECHoannaNature.MAGICAL_FRUIT_BLOCK.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE), "魔力果实块应是斧挖");

        helper.setBlock(new BlockPos(2, 1, 2), ECHoannaNature.DREAD_DIRT.get());
        BlockPos onDirt = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.assertTrue(Blocks.DEAD_BUSH.defaultBlockState().canSurvive(level, onDirt), "枯灌木应能长在恐惧泥土上（原作恐惧之地一株都长不出）");
        helper.assertTrue(ECHoannaNature.GREEN_FLOWER.get().defaultBlockState().canSurvive(level, onDirt), "绿花应能种在恐惧泥土上");
        BlockState tallGrass = ECHoannaNature.HOANNA_TALL_GRASS.get().defaultBlockState();
        helper.assertTrue(tallGrass.canSurvive(level, onDirt), "草丛应能长在恐惧泥土上");
        helper.assertTrue(tallGrass.is(BlockTags.REPLACEABLE) && tallGrass.canBeReplaced(), "草丛应能被直接替换（同原版矮草）");
        helper.setBlock(new BlockPos(2, 2, 2), tallGrass);
        helper.assertFalse(tallGrass.canSurvive(level, helper.absolutePos(new BlockPos(2, 3, 2))), "草丛不应能叠在草丛上（原作能叠成柱）");
        helper.succeed();
    }

    /**
     * 植物走原版 / Forge 的钩子（policy §2.3a、batch6a §7：领地、保护模组靠这些事件管）：
     * 恐惧仙人掌长高前发 CropGrowEvent.Pre，被拒就不长（年龄也不涨）、放行照常长；
     * 玩家对草丛用骨粉发 BonemealEvent，被取消时草丛不变、骨粉不扣，没人拦时长成原版高草丛、扣 1 个骨粉。
     */
    @GameTest(template = EMPTY)
    public static void hoannaPlantsRespectForgeHooks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 仙人掌：年龄 15 的一格，下一次随机刻就该往上长
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.SAND);
        BlockState ripe = ECHoannaNature.DREAD_CACTUS.get().defaultBlockState().setValue(CactusBlock.AGE, 15);
        helper.setBlock(new BlockPos(1, 2, 1), ripe);
        BlockPos cactusPos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos above = cactusPos.above();
        RandomSource random = RandomSource.create(1L);
        Consumer<BlockEvent.CropGrowEvent.Pre> denyGrow = event -> {
            if (event.getLevel() == level && event.getPos().equals(above)) {
                event.setResult(Event.Result.DENY);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.CropGrowEvent.Pre.class, denyGrow);
        try {
            for (int i = 0; i < 5; i++) {
                level.getBlockState(cactusPos).randomTick(level, cactusPos, random);
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denyGrow);
        }
        helper.assertTrue(level.getBlockState(above).isAir() && level.getBlockState(cactusPos).getValue(CactusBlock.AGE) == 15,
                "作物生长事件被拒时恐惧仙人掌不应长高、年龄不应变");
        level.getBlockState(cactusPos).randomTick(level, cactusPos, random);
        helper.assertTrue(level.getBlockState(above).is(ECHoannaNature.DREAD_CACTUS.get()), "没人拦时恐惧仙人掌应长高一格");

        // 骨粉
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(3, 2, 3), ECHoannaNature.HOANNA_TALL_GRASS.get());
        BlockPos grassPos = helper.absolutePos(new BlockPos(3, 2, 3));
        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 2);
        Consumer<BonemealEvent> cancel = event -> {
            if (event.getLevel() == level && event.getPos().equals(grassPos)) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BonemealEvent.class, cancel);
        boolean used;
        try {
            used = BoneMealItem.applyBonemeal(boneMeal, level, grassPos, player);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertTrue(!used && boneMeal.getCount() == 2 && level.getBlockState(grassPos).is(ECHoannaNature.HOANNA_TALL_GRASS.get()),
                "骨粉事件被取消时草丛不应变、骨粉不应扣");
        helper.assertTrue(BoneMealItem.applyBonemeal(boneMeal, level, grassPos, player) && boneMeal.getCount() == 1,
                "没人拦时骨粉应生效并扣 1 个");
        helper.assertTrue(level.getBlockState(grassPos).is(Blocks.TALL_GRASS) && level.getBlockState(grassPos.above()).is(Blocks.TALL_GRASS),
                "草丛用骨粉应长成原版高草丛");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static Registry<Biome> biomes(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
    }

    /** 维度级的群系标签：#minecraft:is_* 与 #forge:is_*（is_hot、is_overworld……）；海纳尔的群系一个都不许进 */
    private static boolean isDimensionTag(TagKey<Biome> tag) {
        String namespace = tag.location().getNamespace();
        return ("minecraft".equals(namespace) || "forge".equals(namespace)) && tag.location().getPath().startsWith("is_");
    }

    private static boolean stepHas(List<HolderSet<PlacedFeature>> steps, GenerationStep.Decoration step, PlacedFeature feature) {
        int index = step.ordinal();
        return index < steps.size() && steps.get(index).stream().anyMatch(holder -> holder.value() == feature);
    }

    /** 测试结构里一格的陆地寻路类型（WalkNodeEvaluator 的静态判定，不带生物） */
    private static BlockPathTypes pathType(GameTestHelper helper, BlockPos relative) {
        return WalkNodeEvaluator.getBlockPathTypeStatic(helper.getLevel(), helper.absolutePos(relative).mutable());
    }

    /** 第一个放置条件是 rarity_filter 时返回 chance，否则 null（RarityFilter 的字段是私有的，按编解码器取） */
    private static Integer rarityOf(PlacedFeature feature) {
        if (feature.placement().isEmpty()) {
            return null;
        }
        PlacementModifier first = feature.placement().get(0);
        JsonObject json = PlacementModifier.CODEC.encodeStart(JsonOps.INSTANCE, first).result()
                .map(JsonElement::getAsJsonObject).orElse(new JsonObject());
        return json.has("type") && "minecraft:rarity_filter".equals(json.get("type").getAsString()) ? json.get("chance").getAsInt() : null;
    }

    /** 群系粒子的概率（AmbientParticleSettings 的字段是私有的，按编解码器取；编不出来返回 NaN） */
    private static float particleProbability(AmbientParticleSettings settings) {
        return AmbientParticleSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).result()
                .map(JsonElement::getAsJsonObject)
                .filter(json -> json.has("probability"))
                .map(json -> json.get("probability").getAsFloat())
                .orElse(Float.NaN);
    }

    private static JsonElement encode(SurfaceRules.RuleSource rule) {
        return SurfaceRules.RuleSource.CODEC.encodeStart(JsonOps.INSTANCE, rule).getOrThrow(false, error -> {
            throw new IllegalStateException("地表规则编码失败：" + error);
        });
    }

    private static int poisonTicks(Pig pig) {
        MobEffectInstance poison = pig.getEffect(MobEffects.POISON);
        return poison == null ? -1 : poison.getDuration();
    }

    /** 挖 trials 次，只许掉 expected，返回 {最少, 最多} 个 */
    private static int[] fruitCounts(GameTestHelper helper, ServerLevel level, BlockPos pos, BlockState state, ItemStack tool, Item expected, int trials) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < trials; i++) {
            int total = 0;
            for (ItemStack stack : Block.getDrops(state, level, pos, null, null, tool)) {
                helper.assertTrue(stack.is(expected), "魔力果实块掉了意外的物品 " + stack);
                total += stack.getCount();
            }
            min = Math.min(min, total);
            max = Math.max(max, total);
        }
        return new int[]{min, max};
    }

    private static ItemStack enchanted(Item item, Enchantment enchantment, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(enchantment, level);
        return stack;
    }
}
