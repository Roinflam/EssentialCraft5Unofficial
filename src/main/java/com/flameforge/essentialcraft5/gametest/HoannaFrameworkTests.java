package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.HoannaEventState;
import com.flameforge.essentialcraft5.hoanna.HoannaFade;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CHoannaEventPacket;
import com.flameforge.essentialcraft5.network.S2CHoannaJoltPacket;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaTerrain;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 第六批 6a 框架 F6a「维度骨架与同步」的测试（batch6a.md §6.1 第 12 条）：维度类型、噪声设置与 D1 A′ 的地形副本、群系源干净、
 * 两个同步包、淡入淡出与客户端状态机、测试覆盖、跨世界的模拟领地。
 * <p>
 * GameTest 服里有真的海纳尔世界（{@link #hoannaLevelInGameTest}；种子每次随机），但测试结构都摆在超平坦主世界：
 * 要固定种子的生成器 / 群系源 / 结构集的测试用 {@link HoannaTestWorld#resolve} 从资源解出来，
 * 要「在测试结构里摆方块、又算在海纳尔」的用 {@code ECHoanna.setTestHoannaOverride(helper.getLevel())}。
 * <b>跨多刻、一直开着测试覆盖的测试放进 {@link #OVERRIDE_BATCH} 这一批</b>（同一批的测试共用一个世界、同时在跑，覆盖开着时别的测试的主世界也算海纳尔）；
 * 只在一刻里设、测、清（写 finally）的不用。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaFrameworkTests {

    private static final String EMPTY = "empty";
    /** 跨多刻开着 {@code ECHoanna.setTestHoannaOverride} 的测试用的批名（M2 / M4 的多刻测试也用它） */
    public static final String OVERRIDE_BATCH = "hoanna_override";
    private static final double STEP = 0.5D;
    private static final float MAX_STEP_DELTA = 0.02F;

    private HoannaFrameworkTests() {
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    // ------------------------------------------------------------------
    // 维度类型、噪声设置、GameTest 服里的海纳尔世界
    // ------------------------------------------------------------------

    /** 维度类型照 batch6a.md §6.1 第 3 条：有天光、无顶、natural、床能用、重生锚不能用、坐标 1:1、y −64~320、效果 essentialcraft5:hoanna、不固定时间 */
    @GameTest(template = EMPTY)
    public static void hoannaDimensionType(GameTestHelper helper) {
        DimensionType type = server(helper).registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).get(ECHoanna.DIMENSION_TYPE);
        helper.assertTrue(type != null, "维度类型 essentialcraft5:hoanna 应已加载");
        helper.assertTrue(type.hasSkyLight() && !type.hasCeiling() && !type.ultraWarm() && type.natural(), "应有天光、无顶、不炎热、natural");
        helper.assertTrue(type.coordinateScale() == 1D, "坐标比例应为 1（原作两边同坐标），实际 " + type.coordinateScale());
        helper.assertTrue(type.minY() == -64 && type.height() == 384 && type.logicalHeight() == 384, "高度应为 −64~320");
        helper.assertTrue(ECHoanna.EFFECTS.equals(type.effectsLocation()), "维度效果应是 " + ECHoanna.EFFECTS + "，实际 " + type.effectsLocation());
        helper.assertFalse(type.hasFixedTime(), "不应固定时间（昼夜天气跟主世界）");
        helper.assertTrue(type.ambientLight() == 0F && !type.piglinSafe() && !type.hasRaids(), "环境光 0、猪灵不安全、没有袭击");
        helper.succeed();
    }

    /** 床能用（能设重生点，原作如此；写 false 床会爆炸放火）、重生锚不能用、natural（startSleepInBed 要它）——policy §4.4 */
    @GameTest(template = EMPTY)
    public static void hoannaBedAndAnchor(GameTestHelper helper) {
        DimensionType type = server(helper).registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).get(ECHoanna.DIMENSION_TYPE);
        helper.assertTrue(type != null, "维度类型 essentialcraft5:hoanna 应已加载");
        helper.assertTrue(type.bedWorks(), "海纳尔里床应能用");
        helper.assertFalse(type.respawnAnchorWorks(), "海纳尔里重生锚不应能用");
        helper.assertTrue(type.natural(), "natural 应为 true（睡觉设重生点、指南针和钟都要它）");
        helper.succeed();
    }

    /** 噪声设置：关铜铁大矿脉、开含水层、生成时不放动物、海平面 63、默认石头 / 水、y −64~320、非 legacy 随机源 */
    @GameTest(template = EMPTY)
    public static void hoannaNoiseSettings(GameTestHelper helper) {
        NoiseGeneratorSettings settings = server(helper).registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).get(ECHoanna.NOISE_SETTINGS);
        helper.assertTrue(settings != null, "噪声设置 essentialcraft5:hoanna 应已加载（runData 生成）");
        helper.assertFalse(settings.oreVeinsEnabled(), "不应有铜铁大矿脉");
        helper.assertTrue(settings.isAquifersEnabled(), "应开含水层");
        helper.assertTrue(settings.disableMobGeneration(), "生成区块时不应放动物");
        helper.assertTrue(settings.seaLevel() == HoannaTerrain.SEA_LEVEL, "海平面应为 63，实际 " + settings.seaLevel());
        helper.assertFalse(settings.useLegacyRandomSource(), "应是非 legacy 随机源（噪声按 id 派生，D1 A′ 靠这个）");
        helper.assertTrue(settings.defaultBlock().is(Blocks.STONE) && settings.defaultFluid().is(Blocks.WATER), "默认方块应是石头、默认流体应是水");
        helper.assertTrue(settings.noiseSettings().minY() == -64 && settings.noiseSettings().height() == 384, "噪声高度应为 −64~320");
        helper.succeed();
    }

    /**
     * GameTest 服里<b>有</b>真的海纳尔世界（合入实测 2026-09-29 更正：Forge 的 runGameTestServer 走专用服 {@code Main} 的建世界流程，
     * 数据包维度照样并进来，不走原版 {@code GameTestServer.create} 那条空维度表的路）：维度类型、生成器、噪声设置、群系源都是数据包里的那份；
     * 资源解出来的（{@link HoannaTestWorld}，固定种子）也是同一套设置。
     */
    @GameTest(template = EMPTY)
    public static void hoannaLevelInGameTest(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel real = server.getLevel(ECHoanna.LEVEL);
        helper.assertTrue(real != null, "GameTest 服里应有海纳尔世界（Forge 走专用服流程并入数据包维度）；没有说明 Forge 行为变了，模块测试里用真世界的地方要改回测试覆盖");
        helper.assertTrue(real.dimensionTypeId().equals(ECHoanna.DIMENSION_TYPE), "海纳尔世界的维度类型应是 essentialcraft5:hoanna，实际 " + real.dimensionTypeId().location());
        helper.assertTrue(real.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noise && noise.generatorSettings().is(ECHoanna.NOISE_SETTINGS),
                "海纳尔世界的生成器应是噪声生成器、设置 essentialcraft5:hoanna，实际 " + real.getChunkSource().getGenerator());
        Set<ResourceKey<Biome>> biomes = new HashSet<>();
        for (Holder<Biome> biome : real.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
            biomes.add(biome.unwrapKey().orElseThrow());
        }
        helper.assertTrue(biomes.equals(new HashSet<>(ECHoanna.BIOMES)), "真海纳尔世界的群系源应正好是 ECHoanna.BIOMES，实际 " + biomes);
        helper.assertTrue(ECHoanna.isHoanna(real) && !ECHoanna.isHoanna(helper.getLevel()), "真海纳尔世界算海纳尔，超平坦主世界（没设覆盖）不算");
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        helper.assertTrue(hoanna.generator().generatorSettings().is(ECHoanna.NOISE_SETTINGS), "资源解出的生成器的噪声设置应是 essentialcraft5:hoanna");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // D1 A′：地形副本
    // ------------------------------------------------------------------

    /**
     * 路由里没有一个原版噪声 / 具名密度函数（全换成 essentialcraft5:hoanna/… 副本）；同一种子下海纳尔的大陆度、侵蚀、山脊、温度、湿度和主世界不一样；
     * 同种子重建一次取值完全相同（确定性）。
     * <p>
     * 雕刻器（老式洞穴、峡谷）不走噪声，第 l 个 carver 的种子是「世界种子 + l」，和 id、维度无关：每个海纳尔群系的 carver 列表前 3 个
     * 必须是 probability 0 的占位 {@code essentialcraft5:hoanna/placeholder_0~2}，真正的 carver 序号 ≥ 3，同一序号上没有和主世界平原相同的 carver，
     * 原版三种（cave、cave_extra_underground、canyon）一个不少——占位被删掉或顺序改回去，洞穴、峡谷就又和主世界同坐标一模一样。
     */
    @GameTest(template = EMPTY)
    public static void hoannaTerrainNotOverworld(GameTestHelper helper) {
        assertCarversOffset(helper);
        MinecraftServer server = server(helper);
        RegistryAccess access = server.registryAccess();
        Registry<DensityFunction> functions = access.registryOrThrow(Registries.DENSITY_FUNCTION);
        Registry<NormalNoise.NoiseParameters> noises = access.registryOrThrow(Registries.NOISE);
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        JsonElement router = NoiseRouter.CODEC.encodeStart(ops, hoanna.settings().noiseRouter()).getOrThrow(false, error -> {
            throw new IllegalStateException("编码海纳尔路由失败：" + error);
        });
        Set<String> vanillaRefs = new HashSet<>();
        int copyRefs = 0;
        List<String> strings = new ArrayList<>();
        strings(router, strings::add);
        for (String value : strings) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) {
                continue;
            }
            boolean function = functions.containsKey(id);
            boolean noise = noises.containsKey(id);
            if (!function && !noise) {
                continue;
            }
            if (EssentialCraft5.MODID.equals(id.getNamespace())) {
                copyRefs++;
            } else {
                vanillaRefs.add(value);
            }
        }
        helper.assertTrue(vanillaRefs.isEmpty(), "海纳尔路由不应引用原版噪声 / 密度函数（否则地形和主世界同坐标相同）：" + vanillaRefs);
        helper.assertTrue(copyRefs >= 10, "海纳尔路由应引用十几个副本，实际 " + copyRefs);

        long seed = HoannaTestWorld.SEED;
        HoannaTestWorld.Resolved again = HoannaTestWorld.resolve(server, seed);
        HoannaTestWorld.Resolved overworld = HoannaTestWorld.vanillaOverworld(server, seed);
        int samples = 0;
        int different = 0;
        for (int i = 0; i < 64; i++) {
            int x = (i % 8) * 5_003 - 17_000;
            int z = (i / 8) * 4_001 - 13_000;
            DensityFunction.SinglePointContext at = new DensityFunction.SinglePointContext(x, 64, z);
            NoiseRouter a = hoanna.randomState().router();
            NoiseRouter b = overworld.randomState().router();
            NoiseRouter c = again.randomState().router();
            double[] ours = {a.continents().compute(at), a.erosion().compute(at), a.ridges().compute(at), a.temperature().compute(at), a.vegetation().compute(at)};
            double[] vanilla = {b.continents().compute(at), b.erosion().compute(at), b.ridges().compute(at), b.temperature().compute(at), b.vegetation().compute(at)};
            double[] repeat = {c.continents().compute(at), c.erosion().compute(at), c.ridges().compute(at), c.temperature().compute(at), c.vegetation().compute(at)};
            for (int k = 0; k < ours.length; k++) {
                samples++;
                if (Math.abs(ours[k] - vanilla[k]) > 0.01D) {
                    different++;
                }
                if (ours[k] != repeat[k]) {
                    helper.fail("同种子重建一次取值应完全相同：第 " + k + " 项 " + ours[k] + " / " + repeat[k] + " @ " + x + ", " + z);
                }
            }
        }
        helper.assertTrue(different >= samples * 9 / 10,
                "同种子下海纳尔的气候与地形噪声应和主世界不同（D1 A′），" + samples + " 个取值里只有 " + different + " 个不同");
        helper.succeed();
    }

    /** 占位 carver 的个数（tools/gen/hoanna_biomes.py 的 CARVER_PLACEHOLDERS） */
    private static final int CARVER_PLACEHOLDERS = 3;

    /** 见 {@link #hoannaTerrainNotOverworld} 的说明：海纳尔群系的 carver 序号和主世界错开 */
    private static void assertCarversOffset(GameTestHelper helper) {
        Registry<Biome> biomes = server(helper).registryAccess().registryOrThrow(Registries.BIOME);
        List<ResourceLocation> plains = carvers(biomes.getOrThrow(Biomes.PLAINS));
        List<ResourceLocation> vanilla = List.of(ResourceLocation.withDefaultNamespace("cave"),
                ResourceLocation.withDefaultNamespace("cave_extra_underground"), ResourceLocation.withDefaultNamespace("canyon"));
        helper.assertTrue(plains.equals(vanilla), "对照组：主世界平原的 carver 应是 " + vanilla + "，实际 " + plains);
        List<String> problems = new ArrayList<>();
        for (ResourceKey<Biome> key : ECHoanna.BIOMES) {
            Biome biome = biomes.getOrThrow(key);
            List<ResourceLocation> ids = carvers(biome);
            List<ConfiguredWorldCarver<?>> values = new ArrayList<>();
            biome.getGenerationSettings().getCarvers(GenerationStep.Carving.AIR).forEach(holder -> values.add(holder.value()));
            if (ids.size() < CARVER_PLACEHOLDERS) {
                problems.add(key.location() + " 只有 " + ids.size() + " 个 carver：" + ids);
                continue;
            }
            for (int i = 0; i < CARVER_PLACEHOLDERS; i++) {
                ResourceLocation want = EssentialCraft5.id("hoanna/placeholder_" + i);
                if (!want.equals(ids.get(i))) {
                    problems.add(key.location() + " 的第 " + i + " 个 carver 应是占位 " + want + "，实际 " + ids.get(i));
                } else if (values.get(i).config().probability != 0F) {
                    problems.add(want + " 的 probability 应为 0，实际 " + values.get(i).config().probability);
                }
            }
            for (int i = 0; i < ids.size(); i++) {
                boolean placeholder = ids.get(i).getPath().startsWith("hoanna/placeholder_");
                if (!placeholder && i < CARVER_PLACEHOLDERS) {
                    problems.add(key.location() + " 的真 carver " + ids.get(i) + " 排在第 " + i + " 个（应 ≥ " + CARVER_PLACEHOLDERS + "）");
                }
                if (!placeholder && i < plains.size() && plains.get(i).equals(ids.get(i))) {
                    problems.add(key.location() + " 的第 " + i + " 个 carver " + ids.get(i) + " 和主世界平原同一序号（同种子，洞穴会和主世界重合）");
                }
            }
            if (!ids.containsAll(vanilla)) {
                problems.add(key.location() + " 缺原版 carver：" + ids);
            }
        }
        helper.assertTrue(problems.isEmpty(), "海纳尔的 carver 没和主世界错开（先重跑 tools/gen/hoanna_biomes.py）：" + problems);
    }

    private static List<ResourceLocation> carvers(Biome biome) {
        List<ResourceLocation> out = new ArrayList<>();
        for (Holder<ConfiguredWorldCarver<?>> holder : biome.getGenerationSettings().getCarvers(GenerationStep.Carving.AIR)) {
            out.add(holder.unwrapKey().map(ResourceKey::location).orElse(ResourceLocation.withDefaultNamespace("direct")));
        }
        return out;
    }

    /**
     * 副本和原版逐个比对：每个 essentialcraft5:hoanna/… 噪声的参数等于同名原版噪声，每个密度函数编码成 JSON 后把副本 id 换回原版 id 等于原版的 JSON
     * ——副本是 runData 用原版 bootstrap 代码生成的，这条守住「只改了 id、别的和当前版本的原版一模一样」。
     */
    @GameTest(template = EMPTY)
    public static void hoannaNoiseCopiesMatchVanilla(GameTestHelper helper) {
        RegistryAccess access = server(helper).registryAccess();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        Registry<NormalNoise.NoiseParameters> noises = access.registryOrThrow(Registries.NOISE);
        int noiseCopies = 0;
        for (Map.Entry<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise.NoiseParameters> entry : noises.entrySet()) {
            ResourceLocation vanillaId = HoannaTerrain.vanillaId(entry.getKey().location());
            if (vanillaId == null) {
                continue;
            }
            noiseCopies++;
            NormalNoise.NoiseParameters vanilla = noises.get(vanillaId);
            helper.assertTrue(vanilla != null, entry.getKey().location() + " 没有对应的原版噪声 " + vanillaId);
            helper.assertTrue(vanilla.equals(entry.getValue()), entry.getKey().location() + " 的参数和原版不同：" + entry.getValue() + " / " + vanilla);
        }
        Registry<DensityFunction> functions = access.registryOrThrow(Registries.DENSITY_FUNCTION);
        int functionCopies = 0;
        for (Map.Entry<ResourceKey<DensityFunction>, DensityFunction> entry : functions.entrySet()) {
            ResourceLocation vanillaId = HoannaTerrain.vanillaId(entry.getKey().location());
            if (vanillaId == null) {
                continue;
            }
            functionCopies++;
            DensityFunction vanilla = functions.get(vanillaId);
            helper.assertTrue(vanilla != null, entry.getKey().location() + " 没有对应的原版密度函数 " + vanillaId);
            JsonElement ours = unCopy(encode(ops, entry.getValue(), entry.getKey().location()));
            JsonElement theirs = encode(ops, vanilla, vanillaId);
            helper.assertTrue(ours.equals(theirs), entry.getKey().location() + " 和原版不同（换回原版 id 后）：\n" + ours + "\n" + theirs);
        }
        helper.assertTrue(noiseCopies >= 10 && functionCopies >= 10,
                "应复制十几个噪声和密度函数（先跑 runData），实际 " + noiseCopies + " / " + functionCopies);
        helper.succeed();
    }

    private static JsonElement encode(RegistryOps<JsonElement> ops, DensityFunction function, ResourceLocation id) {
        return DensityFunction.DIRECT_CODEC.encodeStart(ops, function).getOrThrow(false, error -> {
            throw new IllegalStateException("编码密度函数 " + id + " 失败：" + error);
        });
    }

    /** 把 JSON 里所有副本 id 换回原版 id（新建一份） */
    private static JsonElement unCopy(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            ResourceLocation id = ResourceLocation.tryParse(json.getAsString());
            ResourceLocation vanilla = id == null ? null : HoannaTerrain.vanillaId(id);
            return vanilla == null ? json : new JsonPrimitive(vanilla.toString());
        }
        if (json.isJsonArray()) {
            JsonArray out = new JsonArray();
            json.getAsJsonArray().forEach(element -> out.add(unCopy(element)));
            return out;
        }
        if (json.isJsonObject()) {
            JsonObject out = new JsonObject();
            json.getAsJsonObject().entrySet().forEach(entry -> out.add(entry.getKey(), unCopy(entry.getValue())));
            return out;
        }
        return json;
    }

    private static void strings(JsonElement json, Consumer<String> sink) {
        if (json.isJsonPrimitive()) {
            if (json.getAsJsonPrimitive().isString()) {
                sink.accept(json.getAsString());
            }
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> strings(element, sink));
        } else if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(entry -> strings(entry.getValue(), sink));
        }
    }

    // ------------------------------------------------------------------
    // 群系源干净
    // ------------------------------------------------------------------

    /**
     * 群系源只有 7 个海纳尔群系（都在 #is_hoanna、都不在 #minecraft:is_overworld、没有 minecraft: 命名空间）；
     * 结构放置状态里没有任何原版结构集（要塞、村庄、矿井、前哨站、废弃传送门……）；对照组原版主世界里有村庄（确认对照组本身有效）。
     */
    @GameTest(template = EMPTY)
    public static void hoannaBiomeSourceClean(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        Set<ResourceKey<Biome>> seen = new HashSet<>();
        for (Holder<Biome> biome : hoanna.biomeSource().possibleBiomes()) {
            ResourceKey<Biome> key = biome.unwrapKey().orElseThrow();
            seen.add(key);
            helper.assertTrue(EssentialCraft5.MODID.equals(key.location().getNamespace()), "海纳尔里不应有别的命名空间的群系：" + key.location());
            helper.assertTrue(biome.is(ECHoanna.IS_HOANNA), key.location() + " 应在 #essentialcraft5:is_hoanna");
            helper.assertFalse(biome.is(BiomeTags.IS_OVERWORLD), key.location() + " 不应在 #minecraft:is_overworld（主世界的修改器、结构会灌进来）");
            helper.assertTrue(biome.is(ECHoanna.HOANNA_LAND) == !key.equals(ECHoanna.HOANNA_SEA), key.location() + " 的 #hoanna_land 归属不对（只有海不算陆地）");
        }
        helper.assertTrue(seen.equals(new HashSet<>(ECHoanna.BIOMES)), "群系源应正好是 ECHoanna.BIOMES 这 7 个，实际 " + seen);

        List<String> vanillaSets = new ArrayList<>();
        for (Holder<StructureSet> set : hoanna.structureState().possibleStructureSets()) {
            ResourceLocation id = set.unwrapKey().map(ResourceKey::location).orElse(null);
            if (id == null || ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
                vanillaSets.add(String.valueOf(id));
            }
        }
        helper.assertTrue(vanillaSets.isEmpty(), "海纳尔里不应有原版结构集：" + vanillaSets);

        boolean villages = HoannaTestWorld.vanillaOverworld(server, HoannaTestWorld.SEED).structureState().possibleStructureSets().stream()
                .anyMatch(set -> set.is(ResourceKey.create(Registries.STRUCTURE_SET, ResourceLocation.withDefaultNamespace("villages"))));
        helper.assertTrue(villages, "对照组（原版主世界）应有村庄结构集，否则上面的断言没意义");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 同步包、事件枚举、音效
    // ------------------------------------------------------------------

    /** 两个包存读一致、字节数、夹值、未知 id 当 NONE；协议号至少 5（第八批升到 6）；事件编号固定；sound.deep_noise 已注册 */
    @GameTest(template = EMPTY)
    public static void hoannaEventPacketRoundTrip(GameTestHelper helper) {
        helper.assertTrue(Integer.parseInt(ECNetwork.PROTOCOL) >= 5, "第六批 6a 加了两个包，协议号应至少为 5，实际 " + ECNetwork.PROTOCOL);

        S2CHoannaEventPacket packet = new S2CHoannaEventPacket(HoannaEvent.DARKNESS, 12_345, 48_000);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CHoannaEventPacket.encode(packet, buf);
            helper.assertTrue(buf.readableBytes() == 9, "事件包应为 9 字节，实际 " + buf.readableBytes());
            helper.assertTrue(S2CHoannaEventPacket.decode(buf).equals(packet), "事件包存读后不一样");
        } finally {
            buf.release();
        }
        helper.assertTrue(new S2CHoannaEventPacket(HoannaEvent.FUMES, 99_999, 12_000).remaining() == 12_000, "remaining 应夹到 total");
        helper.assertTrue(new S2CHoannaEventPacket(HoannaEvent.FUMES, -5, 12_000).remaining() == 0, "remaining 不应为负");
        helper.assertTrue(new S2CHoannaEventPacket(HoannaEvent.EARTHQUAKE, 10, 0).event().isNone(), "总时长 0 的应当作 NONE");
        helper.assertTrue(S2CHoannaEventPacket.none().equals(new S2CHoannaEventPacket(HoannaEvent.NONE, 5, 5)), "NONE 应归一成 0 / 0");
        FriendlyByteBuf unknown = new FriendlyByteBuf(Unpooled.buffer());
        try {
            unknown.writeByte(200);
            unknown.writeInt(10);
            unknown.writeInt(20);
            helper.assertTrue(S2CHoannaEventPacket.decode(unknown).event().isNone(), "未知事件 id 应当作 NONE");
        } finally {
            unknown.release();
        }

        FriendlyByteBuf jolt = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CHoannaJoltPacket.encode(new S2CHoannaJoltPacket(73), jolt);
            helper.assertTrue(jolt.readableBytes() == 1, "强震包应为 1 字节");
            helper.assertTrue(S2CHoannaJoltPacket.decode(jolt).strength() == 73, "强震包存读后不一样");
        } finally {
            jolt.release();
        }
        helper.assertTrue(new S2CHoannaJoltPacket(500).strength() == S2CHoannaJoltPacket.FULL && new S2CHoannaJoltPacket(-3).strength() == 0,
                "强震强度应夹到 0~100");
        helper.assertTrue(new S2CHoannaJoltPacket(S2CHoannaJoltPacket.FULL).scale() == 1F, "满幅强度 scale 应为 1");

        // 编号上线后不许改（包和存档都用）
        HoannaEvent[] expected = {HoannaEvent.NONE, HoannaEvent.SUN_ARRAY, HoannaEvent.FUMES, HoannaEvent.DARKNESS, HoannaEvent.EARTHQUAKE,
                HoannaEvent.LEY_STORM};
        for (int id = 0; id < expected.length; id++) {
            helper.assertTrue(HoannaEvent.fromId(id) == expected[id] && expected[id].id() == id, "事件编号 " + id + " 应是 " + expected[id]);
            helper.assertTrue(HoannaEvent.byName(expected[id].key()) == expected[id], "按名字应找回 " + expected[id]);
        }
        helper.assertTrue(HoannaEvent.fromId(-1).isNone() && HoannaEvent.fromId(6).isNone(), "越界编号应当作 NONE");
        helper.assertTrue(HoannaEvent.rollable().length == 4 && HoannaEvent.SUN_ARRAY.dawnOnly() && !HoannaEvent.FUMES.dawnOnly(), "常规掷骰 4 种、只有日阵在黎明掷");
        RandomSource random = RandomSource.create(0x9E3779B97F4A7C15L);
        for (int i = 0; i < 200; i++) {
            int duration = HoannaEvent.DARKNESS.duration(random);
            helper.assertTrue(duration == 24_000 || duration == 48_000 || duration == 72_000, "黑暗应持续 1~3 个游戏日，实际 " + duration);
        }
        helper.assertTrue(HoannaEvent.SUN_ARRAY.duration(random) == 12_000 && HoannaEvent.FUMES.duration(random) == 12_000
                && HoannaEvent.EARTHQUAKE.duration(random) == 5_000, "日阵 / 毒雾 12 000、地震 5 000（原作）");

        ResourceLocation deepNoise = EssentialCraft5.id("sound.deep_noise");
        helper.assertTrue(ForgeRegistries.SOUND_EVENTS.containsKey(deepNoise) && ECHoanna.DEEP_NOISE.get().getLocation().equals(deepNoise),
                "sound.deep_noise 应已注册");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 淡入淡出与客户端状态机
    // ------------------------------------------------------------------

    /**
     * 四种事件：新开始、持续、自然结束、中途停止、半路进来、同一事件重发、停了马上再开、换成别的事件——
     * 按 0.5 tick 步长相邻两步的强度差都 ≤ 0.02，强度在 [0, 1]，长事件中段为 1（batch6a.md §3.6 第 1 条）。
     */
    @GameTest(template = EMPTY)
    public static void hoannaFadeContinuous(GameTestHelper helper) {
        helper.assertTrue(HoannaFade.smoothstep(-1) == 0F && HoannaFade.smoothstep(2) == 1F && HoannaFade.smoothstep(0.5) == 0.5F, "smoothstep 端点不对");
        for (HoannaEvent event : HoannaEvent.rollable()) {
            int total = event.baseDuration();
            long t0 = 10_000L;

            // 新开始 → 自然结束（服务端在 end 发 NONE）
            HoannaEventState state = new HoannaEventState();
            state.accept(event, total, total, t0);
            check(helper, state, event, t0 - 20, t0 + total, "开始到结束");
            helper.assertTrue(state.fade(event, t0 + total / 2D) == 1F, event + " 中段强度应为 1");
            state.accept(HoannaEvent.NONE, 0, 0, t0 + total);
            check(helper, state, event, t0 + total, t0 + total + 400, "结束之后");
            helper.assertTrue(state.fade(event, t0 + total + 1) < 0.01F, event + " 结束时应已淡到 0");

            // 中途停止：从当时的强度线性降到 0
            HoannaEventState stopped = new HoannaEventState();
            stopped.accept(event, total, total, t0);
            long stopAt = t0 + Math.min(total / 3, 1_000);
            sweep(helper, stopped, event, t0, t0 + 2_000, "中途停止", t -> {
                if (t == stopAt) {
                    stopped.accept(HoannaEvent.NONE, 0, 0, stopAt);
                }
            });
            helper.assertTrue(stopped.fade(event, stopAt + HoannaFade.STOP_TICKS + 1) == 0F, event + " 停止 200 tick 后应为 0");

            // 半路进来：从到达那一刻淡入，不突变
            HoannaEventState joined = new HoannaEventState();
            long arrive = t0 + total / 2;
            helper.assertTrue(joined.fade(event, arrive) == 0F, "没收到包时应为 0");
            joined.accept(event, total / 2, total, arrive);
            helper.assertTrue(joined.fade(event, arrive) == 0F, event + " 半路进来的第一刻应从 0 开始淡入");
            check(helper, joined, event, arrive, arrive + 400, "半路进来");

            // 同一事件重发（换维度、重生）：不重新淡入
            HoannaEventState resent = new HoannaEventState();
            resent.accept(event, total, total, t0);
            long resendAt = t0 + 1_000;
            float before = resent.fade(event, resendAt);
            resent.accept(event, total - 1_000 - 3, total, resendAt);
            helper.assertTrue(Math.abs(resent.fade(event, resendAt) - before) <= MAX_STEP_DELTA, event + " 同一事件重发不应重新淡入");

            // 停了马上再开同一种
            HoannaEventState restarted = new HoannaEventState();
            restarted.accept(event, total, total, t0);
            long stop2 = t0 + 500;
            long restart = stop2 + 50;
            sweep(helper, restarted, event, t0, restart + 600, "停了马上再开", t -> {
                if (t == stop2) {
                    restarted.accept(HoannaEvent.NONE, 0, 0, stop2);
                } else if (t == restart) {
                    restarted.accept(event, total, total, restart);
                }
            });

            // 换成别的事件：旧的淡出、新的淡入，两者都连续
            HoannaEvent other = event == HoannaEvent.FUMES ? HoannaEvent.DARKNESS : HoannaEvent.FUMES;
            HoannaEventState switched = new HoannaEventState();
            switched.accept(event, total, total, t0);
            long switchAt = t0 + 700;
            sweep(helper, switched, event, t0, switchAt + 500, "换成别的事件（旧）", t -> {
                if (t == switchAt) {
                    switched.accept(other, other.baseDuration(), other.baseDuration(), switchAt);
                }
            });
            check(helper, switched, other, switchAt, switchAt + 500, "换成别的事件（新）");
            helper.assertTrue(switched.current() == other, "换过之后当前事件应是 " + other);
        }
        helper.succeed();
    }

    private interface TickHook {
        void at(long tick);
    }

    /** 在 [from, to] 里按 0.5 tick 采样（不改状态） */
    private static void check(GameTestHelper helper, HoannaEventState state, HoannaEvent event, long from, long to, String what) {
        sweep(helper, state, event, from, to, what, tick -> {
        });
    }

    /** 在 [from, to] 里按 0.5 tick 采样；每到一个整刻先调 hook（模拟那一刻收到包） */
    private static void sweep(GameTestHelper helper, HoannaEventState state, HoannaEvent event, long from, long to, String what, TickHook hook) {
        float last = Float.NaN;
        for (double t = from; t <= to; t += STEP) {
            if (t == Math.floor(t)) {
                hook.at((long) t);
            }
            float value = state.fade(event, t);
            if (value < 0F || value > 1F || Float.isNaN(value)) {
                helper.fail(event + "（" + what + "）强度越界：" + value + " @ " + t);
            }
            if (!Float.isNaN(last) && Math.abs(value - last) > MAX_STEP_DELTA) {
                helper.fail(event + "（" + what + "）强度跳变 " + last + " → " + value + " @ " + t);
            }
            last = value;
        }
    }

    // ------------------------------------------------------------------
    // 测试覆盖、跨世界的模拟领地
    // ------------------------------------------------------------------

    /** setTestHoannaOverride 之后超平坦主世界算海纳尔、level(server) 返回它；清掉以后恢复；按维度键判断不认覆盖 */
    @GameTest(template = EMPTY)
    public static void hoannaTestOverride(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = server(helper);
        helper.assertFalse(ECHoanna.isHoanna(level), "没设覆盖时超平坦主世界不应算海纳尔（上一个测试忘了清？）");
        ServerLevel real = server.getLevel(ECHoanna.LEVEL);
        helper.assertTrue(real != null && ECHoanna.level(server) == real, "没有覆盖时 level(server) 应是真的海纳尔世界（GameTest 服里也有，见 hoannaLevelInGameTest）");
        try {
            ECHoanna.setTestHoannaOverride(level);
            helper.assertTrue(ECHoanna.isHoanna(level) && ECHoanna.level(server) == level, "设了覆盖后应算海纳尔、level(server) 返回覆盖");
            helper.assertTrue(ECHoanna.isHoanna(real), "开着覆盖时真海纳尔世界仍算海纳尔");
            helper.assertFalse(ECHoanna.isHoanna(level.dimension()), "按维度键判断不认测试覆盖");
            helper.assertTrue(ECHoanna.isHoanna(ECHoanna.LEVEL) && !ECHoanna.isHoanna(Level.OVERWORLD), "维度键判断不对");
        } finally {
            ECHoanna.clearTestOverride();
        }
        helper.assertFalse(ECHoanna.isHoanna(level), "清掉覆盖后不应再算海纳尔");
        helper.assertTrue(ECHoanna.testOverride() == null && ECHoanna.level(server) == real, "清掉后 testOverride() 应为 null、level(server) 回到真海纳尔世界");
        helper.succeed();
    }

    /** 模拟领地的跨世界、绝对坐标整块拒绝（F6-D3）：块里的格子放置被拒且不变、块外照常放；放置事件按次数计；没有领地时照常 */
    @GameTest(template = EMPTY)
    public static void hoannaClaimsBoxAcrossLevels(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockPos inside = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos outside = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos free = helper.absolutePos(new BlockPos(3, 1, 1));
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper, level)
                .denyBox(BoundingBox.fromCorners(inside, inside.offset(1, 1, 1)))) {
            helper.assertFalse(ECProtection.placeGuarded(player, level, inside, Blocks.STONE.defaultBlockState(), Direction.UP), "块里的格子应被拒");
            helper.assertTrue(level.getBlockState(inside).isAir(), "被拒的格子不应变");
            helper.assertTrue(ECProtection.placeGuarded(player, level, outside, Blocks.STONE.defaultBlockState(), Direction.UP), "块外的格子应放行");
            helper.assertTrue(level.getBlockState(outside).is(Blocks.STONE), "放行的格子应放上石头");
            helper.assertTrue(claims.placesAt(inside) == 1 && claims.placesAt(outside) == 1 && claims.placeEvents() == 2,
                    "应各收到一次放置事件，实际 " + claims.placesAt(inside) + " / " + claims.placesAt(outside) + "，共 " + claims.placeEvents());
            helper.assertTrue(claims.level() == level, "Claims 应管指定的世界");
        }
        helper.assertTrue(ECProtection.placeGuarded(player, level, free, Blocks.STONE.defaultBlockState(), Direction.UP), "没有领地时照常放");
        helper.succeed();
    }
}
