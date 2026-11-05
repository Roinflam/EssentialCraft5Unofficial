package com.flameforge.essentialcraft5.worldgen.hoanna.placement;

import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

/**
 * 结构放置 {@code essentialcraft5:spread_excluding}（第十三批 13d-HF「多集排除」，batch13.md §6.4、§7.2 第 1 条）。
 * <p>
 * 原版 {@code random_spread} 的 {@code exclusion_zone} 一个结构集只能排<b>一个</b>别的集；13e 的封印宝库要同时离城镇、前哨 8 区块以上，
 * 前哨要同时离城镇、宝库 8 区块以上，只排一个的话两套格子在群系交界处可以挨得很近。这里在 {@code random_spread} 上多一个列表：
 * <pre>
 * "placement": {
 *   "type": "essentialcraft5:spread_excluding", "spacing": 32, "separation": 16, "salt": …,
 *   "exclusion_zones": [{"other_set": "essentialcraft5:hoanna_town", "chunk_count": 8}, {"other_set": "essentialcraft5:inquisitorium_outpost", "chunk_count": 8}]
 * }
 * </pre>
 * 其余字段（{@code spacing}、{@code separation}、{@code spread_type}、{@code salt}、{@code frequency}、{@code frequency_reduction_method}、
 * {@code locate_offset}、原版的单个 {@code exclusion_zone}）和 {@code random_spread} 完全一样；{@link #isStructureChunk} 先走原版的判定，
 * 再对列表里每一项查那个集的格子在 ±chunk_count 区块里有没有挑中的区块（同原版 exclusion_zone 的逐格查法），<b>挑中的区块还要过那个集的群系判定</b>
 * （{@link #otherBiomeAllows}：区块最小角 +{@value StructureForecast#SAMPLE_OFFSET}、海平面，和城镇 / 宝库 / 前哨各自 findGenerationPoint 判群系的是同一点）
 * 才挡人。原版的 exclusion_zone 不看群系：宝库的格子大半落在海里、根本不生成，也照样挡掉旁边的前哨，宝库、前哨互相挡、实测只有推算的四到七成
 * （batch13.md §7.2 第 1 条）。群系以外的拒绝（城镇布局不成立、宝库 / 前哨中心是液体）不看：宁可多挡，不为每个候选再算别人的噪声列。
 * 每一项约 (2 × chunk_count + 1)² 次格子哈希（8 区块是 289 次，约 15 µs），挑中的通常 1~2 个、各采一次群系（约 7.5 µs），
 * 所以结构预测（{@code StructureForecast}）先算格子、再采群系，群系过了才走这里。
 * <p>
 * 13d 不用它放任何结构，只做类型、codec 和测试；13e 的宝库 / 前哨用（两者互相排除，查对方时不再看对方的排除列表，见 {@link #excluded}）。
 */
public class SpreadExcludingPlacement extends RandomSpreadStructurePlacement {

    /**
     * 前五个字段照抄 {@code StructurePlacement.placementCodec}（它返回的是五元组，后面接不下九个字段），后四个是 random_spread 的加上排除列表。
     * 先在 MapCodec 上校验再转 Codec（同原版 random_spread），type 分派时字段才和 type 平铺在一层
     */
    @SuppressWarnings("deprecation")
    public static final Codec<SpreadExcludingPlacement> CODEC = RecordCodecBuilder.<SpreadExcludingPlacement>mapCodec(instance -> instance.group(
                    Vec3i.offsetCodec(16).optionalFieldOf("locate_offset", Vec3i.ZERO).forGetter(SpreadExcludingPlacement::locateOffset),
                    StructurePlacement.FrequencyReductionMethod.CODEC.optionalFieldOf("frequency_reduction_method",
                            StructurePlacement.FrequencyReductionMethod.DEFAULT).forGetter(SpreadExcludingPlacement::frequencyReductionMethod),
                    Codec.floatRange(0F, 1F).optionalFieldOf("frequency", 1F).forGetter(SpreadExcludingPlacement::frequency),
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt").forGetter(SpreadExcludingPlacement::salt),
                    StructurePlacement.ExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(SpreadExcludingPlacement::exclusionZone),
                    Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
                    Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
                    RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType),
                    StructurePlacement.ExclusionZone.CODEC.listOf().fieldOf("exclusion_zones").forGetter(SpreadExcludingPlacement::exclusionZones))
            .apply(instance, SpreadExcludingPlacement::new))
            .flatXmap(SpreadExcludingPlacement::validate, SpreadExcludingPlacement::validate).codec();

    /**
     * 排除时采对方候选群系的高度：海纳尔的海平面。海纳尔七个群系的深度参数都是 [0, 1]、偏移都是 0，群系和 y 无关（同 {@code StructureForecast} 第 2 步），
     * 取哪个高度结论都一样；结构放置状态里没有生成器，拿不到海平面，所以写成常量
     */
    public static final int BIOME_SAMPLE_Y = 63;

    /** {@code ChunkGeneratorStructureState.biomeSource}（私有、没有取值方法；SRG 名 f_254681_，开发环境由 Forge 映射成明文名） */
    private static final Field BIOME_SOURCE = ObfuscationReflectionHelper.findField(ChunkGeneratorStructureState.class, "f_254681_");

    private final List<StructurePlacement.ExclusionZone> exclusionZones;

    @SuppressWarnings("deprecation")
    public SpreadExcludingPlacement(Vec3i locateOffset, StructurePlacement.FrequencyReductionMethod method, float frequency, int salt,
                                    Optional<StructurePlacement.ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType,
                                    List<StructurePlacement.ExclusionZone> exclusionZones) {
        super(locateOffset, method, frequency, salt, exclusionZone, spacing, separation, spreadType);
        this.exclusionZones = List.copyOf(exclusionZones);
    }

    /** 测试和代码里建：其余字段取 random_spread 的默认值（不偏移、frequency 1、LINEAR、没有原版的单个排除） */
    public SpreadExcludingPlacement(int spacing, int separation, int salt, List<StructurePlacement.ExclusionZone> exclusionZones) {
        this(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT, 1F, salt, Optional.empty(), spacing, separation, RandomSpreadType.LINEAR,
                exclusionZones);
    }

    private static DataResult<SpreadExcludingPlacement> validate(SpreadExcludingPlacement placement) {
        return placement.spacing() <= placement.separation()
                ? DataResult.error(() -> "spread_excluding 的 spacing 要大于 separation（" + placement.spacing() + " / " + placement.separation() + "）")
                : DataResult.success(placement);
    }

    public List<StructurePlacement.ExclusionZone> exclusionZones() {
        return exclusionZones;
    }

    /** 原版判定（格子挑中、frequency、原版的单个 exclusion_zone）都过了，再看列表里每一个集：±chunk_count 区块里有它挑中、群系也对的区块就不放 */
    @Override
    public boolean isStructureChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        return super.isStructureChunk(state, chunkX, chunkZ) && !excluded(state, chunkX, chunkZ);
    }

    /**
     * 只看列表这一步（结构预测把它放在群系判定之后单独算）。
     * <p>
     * <b>两个集互相排除</b>（13e：宝库排前哨、前哨排宝库）：原版 {@code hasStructureChunkInRange} 对那个集逐格调它的 {@code isStructureChunk}，
     * 对方也是 spread_excluding 时又会回头查这个集，两格挨得近就无限递归（StackOverflowError，世界生成线程直接崩）。
     * 所以对方也是 spread_excluding 时只看它的格子和原版那几步（{@link #baseStructureChunk}：挑中、frequency、原版单个 exclusion_zone），
     * 不再看它自己的排除列表。结果：挨得近、群系都对的一对两边都不放（都在对方的 8 区块内），和「两两中心距 ≥ 8」的要求一致；对方是原版放置时照原版查。
     */
    public boolean excluded(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        for (StructurePlacement.ExclusionZone zone : exclusionZones) {
            if (anyInRange(state, zone.otherSet(), chunkX, chunkZ, zone.chunkCount())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 同原版 {@code hasStructureChunkInRange} 的逐格查法，只是对方是 spread_excluding 时换成 {@link #baseStructureChunk}（见 {@link #excluded}），
     * 而且挑中的区块要过对方的群系判定（{@link #otherBiomeAllows}）才算
     */
    private static boolean anyInRange(ChunkGeneratorStructureState state, Holder<StructureSet> otherSet, int chunkX, int chunkZ, int range) {
        StructurePlacement other = otherSet.value().placement();
        for (int x = chunkX - range; x <= chunkX + range; x++) {
            for (int z = chunkZ - range; z <= chunkZ + range; z++) {
                boolean hit = other instanceof SpreadExcludingPlacement spread ? spread.baseStructureChunk(state, x, z) : other.isStructureChunk(state, x, z);
                if (hit && otherBiomeAllows(state, otherSet, x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 对方集里有没有哪一种结构认这个候选区块的群系：采样点区块最小角 +{@value StructureForecast#SAMPLE_OFFSET}、y {@value #BIOME_SAMPLE_Y}，
     * 群系源和气候采样器取结构放置状态自己的（和世界生成同一套）
     */
    public static boolean otherBiomeAllows(ChunkGeneratorStructureState state, Holder<StructureSet> otherSet, int chunkX, int chunkZ) {
        ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
        Holder<Biome> biome = biomeSource(state).getNoiseBiome(QuartPos.fromBlock(StructureForecast.sampleX(chunk)), QuartPos.fromBlock(BIOME_SAMPLE_Y),
                QuartPos.fromBlock(StructureForecast.sampleZ(chunk)), state.randomState().sampler());
        for (StructureSet.StructureSelectionEntry entry : otherSet.value().structures()) {
            if (entry.structure().value().biomes().contains(biome)) {
                return true;
            }
        }
        return false;
    }

    private static BiomeSource biomeSource(ChunkGeneratorStructureState state) {
        try {
            return (BiomeSource) BIOME_SOURCE.get(state);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("读不到 ChunkGeneratorStructureState 的群系源", e);
        }
    }

    /** 不看本类排除列表的那几步：格子挑中、frequency、原版单个 exclusion_zone（别的 spread_excluding 集查这个集时用，见 {@link #excluded}） */
    public boolean baseStructureChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        return super.isStructureChunk(state, chunkX, chunkZ);
    }

    /** 这个区块是不是它那一格挑中的（纯哈希，不看 frequency、不看排除） */
    public boolean isCellChunk(long worldSeed, int chunkX, int chunkZ) {
        ChunkPos picked = getPotentialStructureChunk(worldSeed, chunkX, chunkZ);
        return picked.x == chunkX && picked.z == chunkZ;
    }

    @Override
    public StructurePlacementType<?> type() {
        return ECHoannaFramework.SPREAD_EXCLUDING.get();
    }
}
