package com.flameforge.essentialcraft5.worldgen.hoanna.vault;

import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.flameforge.essentialcraft5.worldgen.hoanna.SurfaceColumn;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.List;
import java.util.Optional;

/**
 * 封印宝库 {@code essentialcraft5:sealed_vault}（第十三批 13e，batch13.md §6.4）：地面一座先辈碑，底下螺旋井通到前厅和宝库室；碑文给出四座祭座的顺序，解开后宝库核心给解开名单上的人每人一份。
 * <p>
 * <b>放置</b>（数据包 {@code worldgen/structure_set/sealed_vault.json}）：{@code spread_excluding} 32 / 16、salt 202610265，排除城镇集、前哨集各 8 区块；群系 {@code #essentialcraft5:has_structure/sealed_vault}（海纳尔沙漠、恐惧之地）；{@code terrain_adaptation none}、不写 spawn_overrides。
 * 只挂海纳尔的群系标签，主世界、下界、末地连判定都不做（{@code ChunkGeneratorStructureState.createForNormal} 按群系源过滤结构集）。
 * <p>
 * <b>{@link #findGenerationPoint}</b>：先判群系（海平面高度采样），再定高起点 1 列（起点区块正中，{@link SurfaceColumn}：一次噪声列同时得出
 * {@code WORLD_SURFACE_WG} 和 {@code OCEAN_FLOOR_WG}，约 3 ms；只这 1 列，别再加）。这一列顶上是液体（湖、河）就不放：井口和碑泡在水里、
 * 竖井会灌水；结构预测照同一列否决（{@link #forecastVeto}），勘测图不会指到湖里。除此以外不拒绝（水边、山坡照放）。
 * 起点区块正中就是螺旋楼梯井的中心，布局见 {@link VaultLayout}，拼件见 {@link VaultPieces}。
 * 结构 JSON 的 step 照旧是 {@code surface_structures}（和城镇同一阶段，在矿物、植被之前；地面上有平台和碑，不改成地下阶段）。
 */
public class SealedVaultStructure extends Structure implements StructureForecast.Vetoed {

    public static final Codec<SealedVaultStructure> CODEC = simpleCodec(SealedVaultStructure::new);

    public SealedVaultStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return VaultStructures.SEALED_VAULT.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!biomeAllows(context)) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        // 定高只取起点 1 列（约 3 ms），别再加列；顶上是液体的不放（预测照同一列否决）
        SurfaceColumn column = SurfaceColumn.of(context, x, z);
        if (column.liquidTop()) {
            return Optional.empty();
        }
        int ground = column.surface();
        VaultLayout.Plan plan = VaultLayout.plan(x, ground, z, context.heightAccessor().getMinBuildHeight());
        WorldgenRandom random = context.random();
        long seed = random.nextLong();
        // 生成桩放在群系采样点上：原版还会按桩的位置再判一次群系，和 biomeAllows、结构预测对的是同一点
        return Optional.of(new GenerationStub(new BlockPos(StructureForecast.sampleX(chunk), ground, StructureForecast.sampleZ(chunk)),
                builder -> addPieces(builder, plan, seed)));
    }

    /**
     * 按布局加四个拼件（顺序 = 同一区块里写的顺序：前厅 → 井 → 宝库室 → 平台和碑）。seed 是这座宝库的种子：
     * 各拼件的缺损种子和谜题种子都从它派生，同一座宝库每次布局都一样。展示厅、测试直接调它在任意世界摆一座。
     */
    public static void addPieces(StructurePieceAccessor builder, VaultLayout.Plan plan, long seed) {
        RandomSource random = RandomSource.create(seed);
        long puzzle = random.nextLong();
        builder.addPiece(VaultPieces.Antechamber.of(plan, random.nextLong(), puzzle));
        builder.addPiece(VaultPieces.Shaft.of(plan, random.nextLong()));
        builder.addPiece(VaultPieces.Chamber.of(plan, random.nextLong(), puzzle));
        builder.addPiece(VaultPieces.Stele.of(plan, random.nextLong()));
    }

    /**
     * 不经结构系统，在任意世界的 (ox, ground, oz) 直接摆一座（展示厅、GameTest 用）：同步加载包围盒碰到的区块，逐区块放四个拼件。
     * 不登记结构起点（{@code /locate}、勘测图找不到它），宝库本身照常能玩。返回宝库核心的位置。
     */
    public static BlockPos placeDirect(ServerLevel level, int ox, int ground, int oz, long seed) {
        VaultLayout.Plan plan = VaultLayout.plan(ox, ground, oz, level.getMinBuildHeight());
        StructurePiecesBuilder builder = new StructurePiecesBuilder();
        addPieces(builder, plan, seed);
        List<StructurePiece> pieces = builder.build().pieces();
        BoundingBox bounds = plan.bounds();
        for (int cx = SectionPos.blockToSectionCoord(bounds.minX()); cx <= SectionPos.blockToSectionCoord(bounds.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(bounds.minZ()); cz <= SectionPos.blockToSectionCoord(bounds.maxZ()); cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                level.getChunk(cx, cz);
                BoundingBox chunkBox = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
                for (StructurePiece piece : pieces) {
                    if (piece.getBoundingBox().intersects(chunkBox)) {
                        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), chunkBox, chunk,
                                new BlockPos(ox, ground, oz));
                    }
                }
            }
        }
        return plan.core();
    }

    /** 结构预测的第 4 步：起点区块正中那一列顶上是液体就不放（和 findGenerationPoint 同一列、同一个判定） */
    @Override
    public StructureForecast.StartVeto forecastVeto() {
        return new StructureForecast.StartVeto((world, chunk) -> SurfaceColumn.sample(world.generator(), chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(),
                world.height(), world.randomState()).liquidTop(), true);
    }

    /**
     * 群系：起点区块最小角 +{@value StructureForecast#SAMPLE_OFFSET}、海平面那一点（和 {@link StructureForecast} 采样的同一点）在不在这座结构的群系标签里。
     * 一次群系采样，不算地面。别改回区块中心：预测（勘测图、普查）按最小角 +4 采样，两点群系不同时预测会对不上实际生成
     */
    public static boolean biomeAllows(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int y = context.chunkGenerator().getSeaLevel();
        Holder<Biome> biome = context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(StructureForecast.sampleX(chunk)), QuartPos.fromBlock(y),
                QuartPos.fromBlock(StructureForecast.sampleZ(chunk)), context.randomState().sampler());
        return context.validBiome().test(biome);
    }
}
