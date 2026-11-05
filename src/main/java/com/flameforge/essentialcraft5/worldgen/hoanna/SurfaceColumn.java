package com.flameforge.essentialcraft5.worldgen.hoanna;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * 一列按噪声算的地表（第十三批 13e，结构判定用）：一次 {@code getBaseColumn} 同时得出两个高度——
 * {@link #surface()} = {@code WORLD_SURFACE_WG}（第一格空气）、{@link #floor()} = {@code OCEAN_FLOOR_WG}（第一格不挡移动的，水算空），
 * 和分两次调 {@code getFirstFreeHeight} 的结果一样。两个高度不同就是顶上有一层液体：噪声这一步只出默认方块、默认流体（水）、含水层的水 / 岩浆和空气。
 * <p>
 * 开销和一次 {@code getFirstFreeHeight} 同一量级（每次新建 NoiseChunk、整棵密度函数重新包一遍，约 3 ms；整列算到底比算到地表多一些格子），
 * 封印宝库、审判庭前哨定高本来就要取起点这一列，判液体不多算一列。结构预测（{@code StructureForecast} 第 4 步）用同一个方法、同一列，
 * 所以预测和实际生成对液体的结论一样。
 */
public record SurfaceColumn(int surface, int floor) {

    /** 顶上是液体（湖、河、海、岩浆湖） */
    public boolean liquidTop() {
        return surface > floor;
    }

    /** 结构判定里取一列（只在 findGenerationPoint 里调，不碰区块） */
    public static SurfaceColumn of(Structure.GenerationContext context, int x, int z) {
        return sample(context.chunkGenerator(), x, z, context.heightAccessor(), context.randomState());
    }

    public static SurfaceColumn sample(ChunkGenerator generator, int x, int z, LevelHeightAccessor height, RandomState random) {
        NoiseColumn column = generator.getBaseColumn(x, z, height, random);
        int min = height.getMinBuildHeight();
        int surface = min;
        int floor = min;
        boolean surfaceFound = false;
        for (int y = height.getMaxBuildHeight() - 1; y >= min; y--) {
            BlockState state = column.getBlock(y);
            if (!surfaceFound && Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(state)) {
                surface = y + 1;
                surfaceFound = true;
            }
            if (Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state)) {
                floor = y + 1;
                break;
            }
        }
        return new SurfaceColumn(surface, floor);
    }
}
