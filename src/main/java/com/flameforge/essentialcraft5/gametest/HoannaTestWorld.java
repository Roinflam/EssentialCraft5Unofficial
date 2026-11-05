package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;

/**
 * GameTest 里用的「海纳尔生成器」（第六批 6a 框架 F6a，batch6a.md §6.1 第 12 条）。
 * <p>
 * GameTest 服里其实有真的海纳尔世界（合入实测更正：Forge 的 runGameTestServer 走专用服流程，数据包维度照样并进来，见 {@code ECHoanna} 类注释），
 * 但它的种子每次随机。要<b>固定种子</b>的生成器、群系源、结构集的测试从这里拿：
 * 读资源 {@code data/essentialcraft5/dimension/hoanna.json}，用服务端的注册表（数据包加载的维度类型、噪声设置、群系都在）解出 {@link LevelStem}，
 * 再按固定种子手建 {@link RandomState} 和 {@link ChunkGeneratorStructureState}。不生成区块、不建世界。
 * <pre>
 * HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(helper.getLevel().getServer());
 * Climate.Sampler sampler = hoanna.randomState().sampler();
 * Holder&lt;Biome&gt; biome = hoanna.biomeSource().getNoiseBiome(qx, qy, qz, sampler);          // 四分之一坐标
 * List&lt;Holder&lt;StructureSet&gt;&gt; sets = hoanna.structureState().possibleStructureSets();
 * </pre>
 * 要「一个海纳尔 ServerLevel」的测试（找门建门、SavedData、事件计时）不用这个：在测试结构里摆方块的用 {@code ECHoanna.setTestHoannaOverride(helper.getLevel())}，
 * 真传送之类用 {@code server.getLevel(ECHoanna.LEVEL)}。
 * 对照组 {@link #vanillaOverworld}：原版主世界噪声设置 + 原版主世界群系参数表（{@code MultiNoiseBiomeSourceParameterLists.OVERWORLD}）按同一种子手建，
 * 给「主世界不会出现海纳尔的结构」「海纳尔地形不等于主世界」这类断言用（GameTest 服自己的主世界是超平坦，结构集走平坦预设的覆盖表，测不出东西）。
 */
public final class HoannaTestWorld {

    /** 默认种子（随便取的固定值；不同测试要不同地形就传自己的种子） */
    public static final long SEED = 20260929L;
    /** 维度 JSON 的资源位置 */
    public static final ResourceLocation DIMENSION_FILE = EssentialCraft5.id("dimension/hoanna.json");

    /**
     * 解出来的一套生成器。
     *
     * @param stem           维度（类型 + 生成器）
     * @param generator      生成器（{@code minecraft:noise}）
     * @param settings       噪声设置（{@code essentialcraft5:hoanna}）
     * @param randomState    按 {@code seed} 建的随机状态（噪声、气候采样器、地表）
     * @param structureState 按 {@code seed} 建的结构放置状态（{@code createForNormal}：按群系源过滤过的结构集）
     */
    public record Resolved(LevelStem stem, NoiseBasedChunkGenerator generator, NoiseGeneratorSettings settings,
                           RandomState randomState, ChunkGeneratorStructureState structureState, long seed) {

        public BiomeSource biomeSource() {
            return generator.getBiomeSource();
        }

        /** 世界高度范围：维度类型的（和真世界里结构生成的区块一样）；对照组没有维度类型，取噪声设置的 */
        public LevelHeightAccessor height() {
            if (stem != null) {
                DimensionType type = stem.type().value();
                return LevelHeightAccessor.create(type.minY(), type.height());
            }
            return LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth());
        }

        /** 换成结构预测用的 World */
        public StructureForecast.World forecast() {
            return StructureForecast.World.of(structureState, generator, randomState, height());
        }
    }

    private HoannaTestWorld() {
    }

    /** 按默认种子解出海纳尔的生成器（每次调用重新解，几毫秒） */
    public static Resolved resolve(MinecraftServer server) {
        return resolve(server, SEED);
    }

    /** 按指定种子解出海纳尔的生成器；维度 JSON 缺失、解析失败、生成器不是噪声生成器时抛 {@link IllegalStateException}（测试会带着原因失败） */
    public static Resolved resolve(MinecraftServer server, long seed) {
        RegistryAccess access = server.registryAccess();
        JsonElement json = readDimensionJson(server);
        LevelStem stem = LevelStem.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, access), json)
                .getOrThrow(false, error -> {
                    throw new IllegalStateException("解析 " + DIMENSION_FILE + " 失败（先跑 runData？噪声设置是生成的）：" + error);
                });
        if (!(stem.generator() instanceof NoiseBasedChunkGenerator generator)) {
            throw new IllegalStateException("海纳尔的生成器应是 minecraft:noise，实际 " + stem.generator());
        }
        if (!stem.type().is(ECHoanna.DIMENSION_TYPE)) {
            throw new IllegalStateException("海纳尔的维度类型应是 " + ECHoanna.DIMENSION_TYPE.location() + "，实际 " + stem.type());
        }
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        RandomState randomState = RandomState.create(settings, access.lookupOrThrow(Registries.NOISE), seed);
        ChunkGeneratorStructureState structures = ChunkGeneratorStructureState.createForNormal(randomState, seed, generator.getBiomeSource(),
                access.lookupOrThrow(Registries.STRUCTURE_SET));
        return new Resolved(stem, generator, settings, randomState, structures, seed);
    }

    /**
     * 对照组：原版主世界（{@code minecraft:overworld} 噪声设置 + 原版主世界群系参数表）按同一种子手建的随机状态与结构放置状态。
     * {@code stem} 是 null（没有维度类型）；{@code generator} 是新建的 {@link NoiseBasedChunkGenerator}。
     */
    public static Resolved vanillaOverworld(MinecraftServer server, long seed) {
        RegistryAccess access = server.registryAccess();
        Holder<NoiseGeneratorSettings> settings = access.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        Holder<MultiNoiseBiomeSourceParameterList> parameters = access.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), settings);
        RandomState randomState = RandomState.create(settings.value(), access.lookupOrThrow(Registries.NOISE), seed);
        ChunkGeneratorStructureState structures = ChunkGeneratorStructureState.createForNormal(randomState, seed, generator.getBiomeSource(),
                access.lookupOrThrow(Registries.STRUCTURE_SET));
        return new Resolved(null, generator, settings.value(), randomState, structures, seed);
    }

    /** 读维度 JSON 原文（服务端数据包资源） */
    public static JsonElement readDimensionJson(MinecraftServer server) {
        Resource resource = server.getResourceManager().getResource(DIMENSION_FILE)
                .orElseThrow(() -> new IllegalStateException("数据包里没有 " + DIMENSION_FILE + "（tools/gen/hoanna_biomes.py 生成）"));
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("读 " + DIMENSION_FILE + " 失败", e);
        }
    }
}
