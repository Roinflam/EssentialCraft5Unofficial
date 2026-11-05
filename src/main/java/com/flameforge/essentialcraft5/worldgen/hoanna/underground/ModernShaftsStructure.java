package com.flameforge.essentialcraft5.worldgen.hoanna.underground;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/**
 * 现代矿井（原作 {@code MapGenModernShafts} + {@code StructureModernShaftStart}；M6，batch6a §5.6），写法照 1.20.1 原版 {@code MineshaftStructure}（L61）。
 * <p>
 * 大厅在起点区块 (minX + 2, minZ + 2)、y 50，布局见 {@link ModernShaftPieces}；布局完整座挪到海平面以下（{@code moveBelowSeaLevel(…, 10)}，
 * 海纳尔海平面 63、min_y −64：最高点在 y 52 以下随机）。高度只在这里定一次（跨区块一致）。
 * <p>
 * <b>放置</b>：数据包 {@code worldgen/structure_set/modern_shafts.json}（random_spread 16 / 8、salt 202609293，每 256 区块一座，相邻至少隔 8 区块；
 * 原作每区块 0.4% × 离原点打折、/locate 扫 1 000 圈会卡死）；群系 {@code #essentialcraft5:has_structure/modern_shafts} = 海纳尔全部 7 个群系；
 * 不用 beard、不写 spawn_overrides。每座约 127 个件、91 段走廊，平均约 5 辆运输矿车、2.6 个洞穴蜘蛛刷怪笼，脚下和横梁是能直接挖的虚空石。
 */
public class ModernShaftsStructure extends Structure {

    public static final Codec<ModernShaftsStructure> CODEC = Structure.simpleCodec(ModernShaftsStructure::new);

    public ModernShaftsStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        WorldgenRandom random = context.random();
        ChunkGenerator generator = context.chunkGenerator();
        StructurePiecesBuilder builder = new StructurePiecesBuilder();
        ModernShaftPieces.layout(builder, random, chunk.getBlockX(2), chunk.getBlockZ(2));
        int dy = ModernShaftPieces.moveBelowSeaLevel(builder, generator.getSeaLevel(), generator.getMinY(), random);
        return Optional.of(new GenerationStub(new BlockPos(chunk.getMiddleBlockX(), ModernShaftPieces.START_Y + dy, chunk.getMinBlockZ()),
                Either.right(builder)));
    }

    @Override
    public StructureType<?> type() {
        return UndergroundStructures.MODERN_SHAFTS.get();
    }
}
