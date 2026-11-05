package com.flameforge.essentialcraft5.worldgen.hoanna.underground;

import com.flameforge.essentialcraft5.registry.ECHoannaStructures;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.RegistryObject;

/**
 * 古代墓穴与现代矿井（M6，batch6a §5.6）的注册入口：2 个结构类型 {@code old_catacombs}、{@code modern_shafts}，7 个拼件类型
 * {@code catacomb_room / catacomb_corridor / catacomb_exit / shaft_room / shaft_corridor / shaft_crossing / shaft_stairs}。
 * <p>
 * 由 {@code ECHoannaStructures.init()} 调 {@link #init()}：本类加载时下面的静态字段登记进 ECHoannaStructures，赶在 RegisterEvent 之前。
 * 拼件继承 {@code worldgen/hoanna/HoannaStructurePiece}，别的模块的方块（虚空石、虚空栅栏、混凝土、根系）走 {@code HoannaStructureBlocks}，
 * 墓穴出口箱子表 {@code loot/HoannaLoot.CATACOMBS}，矿井的矿车箱子用原版 {@code minecraft:chests/abandoned_mineshaft}。
 * <p>
 * <b>注册名上线后不能改</b>：结构类型 id 写在数据包 {@code worldgen/structure/*.json} 的 type 里，拼件类型 id 写进区块存档的结构起点，
 * 改名后旧存档的区块读不回这些拼件。放置规则（间距、salt、群系标签）在数据包 JSON 里，服主能用数据包覆盖。
 */
public final class UndergroundStructures {

    /** 古代墓穴（数据包 {@code worldgen/structure/old_catacombs.json}，结构集 12 / 5、salt 202609292） */
    public static final RegistryObject<StructureType<OldCatacombsStructure>> OLD_CATACOMBS =
            ECHoannaStructures.structureType("old_catacombs", OldCatacombsStructure.CODEC);
    /** 现代矿井（数据包 {@code worldgen/structure/modern_shafts.json}，结构集 16 / 8、salt 202609293） */
    public static final RegistryObject<StructureType<ModernShaftsStructure>> MODERN_SHAFTS =
            ECHoannaStructures.structureType("modern_shafts", ModernShaftsStructure.CODEC);

    /** 墓穴房间（5 × 5 × 5 强化石壳；破损 / 长草 / 出口三种标记） */
    public static final RegistryObject<StructurePieceType> CATACOMB_ROOM =
            ECHoannaStructures.contextlessPiece("catacomb_room", OldCatacombsPieces.Room::new);
    /** 墓穴走廊（5 × 5 × 32~63，两端开口） */
    public static final RegistryObject<StructurePieceType> CATACOMB_CORRIDOR =
            ECHoannaStructures.contextlessPiece("catacomb_corridor", OldCatacombsPieces.Corridor::new);
    /** 墓穴出口烟囱（出口房间正上方的 5 × 5 强化石烟囱 + 梯子，通到地面） */
    public static final RegistryObject<StructurePieceType> CATACOMB_EXIT =
            ECHoannaStructures.contextlessPiece("catacomb_exit", OldCatacombsPieces.Exit::new);

    /** 矿井起点大厅 */
    public static final RegistryObject<StructurePieceType> SHAFT_ROOM =
            ECHoannaStructures.contextlessPiece("shaft_room", ModernShaftPieces.Room::new);
    /** 矿井走廊（虚空栅栏立柱 + 虚空石横梁） */
    public static final RegistryObject<StructurePieceType> SHAFT_CORRIDOR =
            ECHoannaStructures.contextlessPiece("shaft_corridor", ModernShaftPieces.Corridor::new);
    /** 矿井十字路口（四角虚空石柱） */
    public static final RegistryObject<StructurePieceType> SHAFT_CROSSING =
            ECHoannaStructures.contextlessPiece("shaft_crossing", ModernShaftPieces.Crossing::new);
    /** 矿井阶梯（往下 5 格） */
    public static final RegistryObject<StructurePieceType> SHAFT_STAIRS =
            ECHoannaStructures.contextlessPiece("shaft_stairs", ModernShaftPieces.Stairs::new);

    private UndergroundStructures() {
    }

    /** 空实现即可：调用它只是为了触发本类加载（静态字段登记） */
    public static void init() {
    }

    // ---------------------------------------------------------------- 展示厅 / 测试用（正常生成不走这里）

    /**
     * 在 level 的 chunk 处按给定种子生成一座结构的起点（同 {@code /place structure}：群系谓词恒真、用这个世界自己的生成器），只算布局不放方块。
     * 生成不了（数据包里没有这个结构、起点无效）返回 {@link StructureStart#INVALID_START}。
     * 例：展示厅在超平坦世界里放一座墓穴——墓穴在 y 6~32，悬在地面以上的空气里，走廊网一眼看全；矿井在超平坦里会挪到 y 2 往上。
     */
    public static StructureStart generateAt(ServerLevel level, ResourceLocation structureId, ChunkPos chunk, long seed) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureId);
        if (structure == null) {
            return StructureStart.INVALID_START;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return structure.generate(level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), seed, chunk, 0, level, biome -> true);
    }

    /**
     * 把起点放进 chunk 周围 ±radius 个区块（同步加载这些区块，(2r + 1)² 个；在主线程上调，同一刻里读结果）。返回放了几个区块。
     * 用这个世界的随机数（同 /place），箱子、刷怪笼的内容每次不同，方块摆法按拼件种子定死。
     */
    public static int placeAround(ServerLevel level, StructureStart start, ChunkPos chunk, int radius) {
        if (!start.isValid()) {
            return 0;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int placed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos pos = new ChunkPos(chunk.x + dx, chunk.z + dz);
                level.getChunk(pos.x, pos.z);
                BoundingBox chunkBox = new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(), pos.getMaxBlockX(),
                        level.getMaxBuildHeight(), pos.getMaxBlockZ());
                start.placeInChunk(level, level.structureManager(), generator, level.getRandom(), chunkBox, pos);
                placed++;
            }
        }
        return placed;
    }
}
