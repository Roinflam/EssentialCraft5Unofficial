package com.flameforge.essentialcraft5.worldgen.hoanna.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 中景观地物 {@code essentialcraft5:midscape}（第十三批 13d-H1b，batch13.md §6.1；放法与三种内容见 {@link HoannaMidscape}）。
 * <p>
 * placed feature {@code hoanna_midscape} 只有一条放置修饰 {@code cell_spread}（挑中的区块才进来），挂在冰冻、混沌、恐惧之地三个群系上，
 * 生成步骤 surface_structures（在植被之前：营地中间的雪地上不长草，陨坑里照常长枯树、仙人掌）。这里：
 * <ol>
 *   <li>按区块中心的群系（海平面那一层）和辐射底数挑种类（{@link HoannaMidscape#kindFor}），不放的直接返回；</li>
 *   <li>起点区块和四周 8 块里有城镇这类大结构的引用就不放；</li>
 *   <li>中心那一列从 WORLD_SURFACE_WG 往下 16 格内找地面，找不到（水面上）就不放；</li>
 *   <li>按「世界种子 + 区块坐标」的种子放，只写起点区块 ± 1 个区块。</li>
 * </ol>
 */
public class MidscapeFeature extends Feature<NoneFeatureConfiguration> {

    public static final int GROUND_SEARCH = 16;

    public MidscapeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        int x = HoannaMidscape.centerX(chunk);
        int z = HoannaMidscape.centerZ(chunk);
        int seaLevel = context.chunkGenerator().getSeaLevel();
        HoannaMidscape.Kind kind = HoannaMidscape.kindFor(sampleBiome(level, x, z, seaLevel), chunk.x, chunk.z);
        if (kind == null || HoannaMidscape.blockedByStructures(level, chunk, 1)) {
            return false;
        }
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        int ground = FeatureSupport.groundY(level, x, z, top, top - GROUND_SEARCH);
        if (ground == Integer.MIN_VALUE) {
            return false;
        }
        BoundingBox box = FeatureSupport.writableBox(level, context.origin());
        return HoannaMidscape.place(kind, level, box, new BlockPos(x, ground, z), HoannaMidscape.seedFor(level.getSeed(), chunk), seaLevel);
    }

    /** 区块中心那一格、海平面（生成器的）那一层的群系（四分之一格坐标，读区块里存的群系；和普查对群系源采样是同一个点） */
    public static Holder<Biome> sampleBiome(WorldGenLevel level, int x, int z, int seaLevel) {
        return level.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(seaLevel), QuartPos.fromBlock(z));
    }
}
