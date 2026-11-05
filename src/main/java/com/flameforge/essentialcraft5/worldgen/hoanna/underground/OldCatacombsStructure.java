package com.flameforge.essentialcraft5.worldgen.hoanna.underground;

import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/**
 * 古代墓穴（原作 {@code MapGenOldCatacombs} + {@code StructureOldCatacombs.Start}；M6，batch6a §5.6）。
 * <p>
 * 起点房间在起点区块 (minX + 2, minZ + 2)，高度 6~32（原作的 1.12 绝对高度，海纳尔 min_y = −64，不用抬；在石头层里）；
 * 布局照原作：房间 → 四个方向的走廊 → 走廊尽头的房间……，房间深度 ≤ 7 才出走廊、走廊深度 ≤ 6 才出房间，
 * 超出 {@link OldCatacombsPieces#CATACOMB_MAX_RADIUS} 格的件按「放不下」处理（1.20.1 只在起点周围 8 区块内引用拼件，原作最远 267 格会被截断）。
 * 布局算完后给每个出口房间按噪声算一次地面高度（{@code WORLD_SURFACE_WG}），地面高过房顶就加一个烟囱件——
 * 高度只在这里算一次，不在 postProcess 里按区块各算各的（原作房间跨两个区块时只有一边搜得到地面，出半截烟囱）。
 * <p>
 * <b>放置</b>：数据包 {@code worldgen/structure_set/old_catacombs.json}（random_spread 12 / 5、salt 202609292，每 144 区块一座，
 * /locate 按格子算，不再逐区块扫 1 000 圈）；群系 {@code #essentialcraft5:has_structure/old_catacombs} = 海纳尔全部 7 个群系；
 * 不用 beard。刷怪覆盖写在结构 JSON 里（第十三批 13d，batch13.md §6.5）：怪物类别按拼件包围盒（{@code bounding_box: piece}）换成墓穴守灵 60、骷髅 30、僵尸 20。每座约 14.5 个房间、27 条走廊，平均 1.5 个出口（有箱子），22% 没有出口。
 */
public class OldCatacombsStructure extends Structure {

    public static final Codec<OldCatacombsStructure> CODEC = Structure.simpleCodec(OldCatacombsStructure::new);

    public OldCatacombsStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        WorldgenRandom random = context.random();
        int x = chunk.getMinBlockX() + 2;
        int z = chunk.getMinBlockZ() + 2;
        int y = random.nextIntBetweenInclusive(OldCatacombsPieces.MIN_Y, OldCatacombsPieces.MAX_Y);
        StructurePiecesBuilder builder = new StructurePiecesBuilder();
        OldCatacombsPieces.layout(builder, random, x, y, z);
        OldCatacombsPieces.addExits(builder,
                (px, pz) -> HoannaStructurePiece.groundYAt(context, px, pz, Heightmap.Types.WORLD_SURFACE_WG));
        return Optional.of(new GenerationStub(new BlockPos(x + 2, y, z + 2), Either.right(builder)));
    }

    @Override
    public StructureType<?> type() {
        return UndergroundStructures.OLD_CATACOMBS.get();
    }
}
