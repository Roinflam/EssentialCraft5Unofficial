package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.loot.HoannaLoot;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.underground.ModernShaftPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.underground.OldCatacombsPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.underground.UndergroundStructures;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 第六批 6a 模块 M6「古代墓穴与现代矿井」（batch6a §5.6、§4.4 第 6 条）。
 * <ul>
 *   <li>{@code undergroundStructureSets}：两个结构集是 random_spread（墓穴 12 / 5、salt 202609292；矿井 16 / 8、salt 202609293，linear），
 *       结构在地下那一步、不用 beard、群系正好是海纳尔的 7 个，矿井不写 spawn_overrides、墓穴只覆盖怪物类别（13d 的墓穴守灵）；海纳尔的结构集里有它们，
 *       按同一种子手建的原版主世界里没有（GameTest 服自己的主世界是超平坦，测不出来）。</li>
 *   <li>{@code catacombLayoutBoundedAndConnected}：500 个种子的墓穴布局——所有件离起点房间中心 ≤ 112 格、互不相交、同一高度；
 *       深度规则（房间 ≤ 7、走廊 ≤ 8）；每条走廊两头接着房间的位置对（向南的走廊把房间接在南端，修原作 L316）；
 *       房间平均 12~17 间，出口占房间 0.07~0.13；出口烟囱只在地面高过房顶时加、顶在地面上一格。模拟值见 {@code cata_sim.py}（房间平均 14.5）。</li>
 *   <li>{@code shaftLayoutWithinReferenceRange}：300 个种子的矿井布局——离大厅中心 ≤ 112 格、深度 ≤ 9、互不相交、
 *       大厅向东有支路的 ≥ 20%（修原作 L570；模拟约 52%）；挪到海平面以下后最高点 ≤ 52、支路口跟着一起挪。</li>
 *   <li>{@code undergroundPiecesRoundTripNbt}：7 种拼件 createTag 再按注册的类型读回，NBT 逐字节相同、字段相同（读不回来会导致区块加载失败）。</li>
 *   <li>{@code catacombExitRoomHasChestOnVoidStone}：出口房间 (2, 2, 2) 是 {@code chests/catacombs} 的箱子（正面朝梯子）、下面垫虚空石
 *       （虚空石还没注册时是石头）；梯子从地板一直通到烟囱顶、挂在没开口的那面墙上；房顶、烟囱顶打通。</li>
 *   <li>{@code catacombPiecesChunkSeamless} / {@code shaftCorridorChunkSeamless}：同一批拼件整块生成、和分两个 chunkBox（倒着顺序）生成，
 *       结果逐格相同；只处理一半时另一半和 chunkBox 外一格不变（结构只写正在生成的区块）。长草 + 破损 + 出口的房间箱子不被盖掉。</li>
 *   <li>{@code undergroundStructuresGenerateInHoanna}：用固定种子的海纳尔生成器（{@link HoannaTestWorld}）真跑 {@code Structure.generate}：
 *       起点都有效、同一区块两次生成的拼件 NBT 相同；墓穴出口烟囱顶在真实地面上（高过房顶）；矿井整座在海平面 − 10 以下。</li>
 *   <li>{@code placeCatacombsEndToEnd} / {@code placeShaftsEndToEnd}（batch {@value #PLACE_BATCH}）：在超平坦主世界远处按固定种子生成、
 *       结构起点整份 NBT 读回相同，放进起点周围 5 × 5 区块，有强化石房间和走廊外壳 / 有矿井的栅栏立柱和横梁。</li>
 * </ul>
 * 别的模块的方块（虚空石、虚空栅栏、混凝土、根系）按注册名取，还没注册时拼件退回石头 / 空气，相关断言按 {@link HoannaStructureBlocks#isRegistered} 放宽。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaUndergroundTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 结构端到端放置的批（和城镇的放在一起，单独一批、超时长） */
    public static final String PLACE_BATCH = "hoanna_place";
    /** 测试写方块用的 flag：不连带更新邻居 */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** 打散种子用的大奇数（挨着的小种子 LegacyRandomSource 第一次抽的数几乎一样） */
    private static final long SPREAD = 0x9E3779B97F4A7C15L;
    private static final ResourceLocation CATACOMBS = EssentialCraft5.id("old_catacombs");
    private static final ResourceLocation SHAFTS = EssentialCraft5.id("modern_shafts");

    private HoannaUndergroundTests() {
    }

    // ------------------------------------------------------------------ 放置规则

    @GameTest(template = EMPTY)
    public static void undergroundStructureSets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        checkStructure(helper, level, CATACOMBS, 12, 5, 202609292, UndergroundStructures.OLD_CATACOMBS.get());
        checkStructure(helper, level, SHAFTS, 16, 8, 202609293, UndergroundStructures.MODERN_SHAFTS.get());

        MinecraftServer server = level.getServer();
        Set<ResourceLocation> hoanna = setIds(HoannaTestWorld.resolve(server).structureState().possibleStructureSets());
        helper.assertTrue(hoanna.contains(CATACOMBS) && hoanna.contains(SHAFTS), "海纳尔的结构集里应有墓穴和矿井：" + hoanna);
        Set<ResourceLocation> overworld = setIds(HoannaTestWorld.vanillaOverworld(server, HoannaTestWorld.SEED).structureState().possibleStructureSets());
        helper.assertFalse(overworld.contains(CATACOMBS) || overworld.contains(SHAFTS), "原版主世界不该有墓穴、矿井的结构集：" + overworld);
        helper.succeed();
    }

    private static void checkStructure(GameTestHelper helper, ServerLevel level, ResourceLocation id, int spacing, int separation, int salt,
                                       Object type) {
        StructureSet set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(id);
        helper.assertTrue(set != null, "数据包里应有结构集 " + id);
        helper.assertTrue(set.structures().size() == 1 && set.structures().get(0).structure().is(ResourceKey.create(Registries.STRUCTURE, id)),
                id + " 的结构集应只有它自己");
        helper.assertTrue(set.placement() instanceof RandomSpreadStructurePlacement, id + " 应是 random_spread（/locate 按格子算）");
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
        helper.assertTrue(placement.spacing() == spacing && placement.separation() == separation && placement.spreadType() == RandomSpreadType.LINEAR,
                id + " 的间距应是 " + spacing + " / " + separation + "、linear，实际 " + placement.spacing() + " / " + placement.separation());
        JsonElement json = StructurePlacement.CODEC.encodeStart(JsonOps.INSTANCE, set.placement()).getOrThrow(false, error -> {
        });
        helper.assertTrue(json.getAsJsonObject().get("salt").getAsInt() == salt, id + " 的 salt 应是 " + salt + "：" + json);

        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
        helper.assertTrue(structure != null && structure.type() == type, "数据包里应有结构 " + id + "，类型是本模块注册的");
        helper.assertTrue(structure.step() == GenerationStep.Decoration.UNDERGROUND_STRUCTURES, id + " 应在 underground_structures 那一步");
        helper.assertTrue(structure.terrainAdaptation() == TerrainAdjustment.NONE, id + " 不该用 beard（地形适配 none）");
        // 第十三批 13d 起古代墓穴写了怪物类别的 spawn_overrides（墓穴守灵，batch13.md §6.5，HoannaSpawnTests 核内容）；别的类别、现代矿井照旧不写
        boolean catacombs = id.getPath().equals("old_catacombs");
        helper.assertTrue(catacombs ? structure.spawnOverrides().keySet().equals(java.util.Set.of(net.minecraft.world.entity.MobCategory.MONSTER))
                : structure.spawnOverrides().isEmpty(), id + (catacombs ? " 只该覆盖怪物类别的刷怪" : " 不该写 spawn_overrides"));
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        for (ResourceKey<Biome> key : ECHoanna.BIOMES) {
            helper.assertTrue(structure.biomes().contains(biomes.getHolderOrThrow(key)), id + " 应能生成在 " + key.location());
        }
        helper.assertFalse(structure.biomes().contains(biomes.getHolderOrThrow(Biomes.PLAINS)), id + " 不该能生成在原版群系");
    }

    private static Set<ResourceLocation> setIds(List<Holder<StructureSet>> sets) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (Holder<StructureSet> set : sets) {
            set.unwrapKey().ifPresent(key -> ids.add(key.location()));
        }
        return ids;
    }

    // ------------------------------------------------------------------ 布局

    @GameTest(template = EMPTY)
    public static void catacombLayoutBoundedAndConnected(GameTestHelper helper) {
        int seeds = 500;
        long rooms = 0;
        long exits = 0;
        int maxRadius = 0;
        for (int i = 0; i < seeds; i++) {
            long seed = (i + 1L) * SPREAD;
            StructurePiecesBuilder builder = new StructurePiecesBuilder();
            OldCatacombsPieces.Room start = catacombLayout(builder, seed);
            List<StructurePiece> pieces = builder.build().pieces();
            BoundingBox startBox = start.getBoundingBox();
            BlockPos center = startBox.getCenter();
            helper.assertTrue(startBox.minY() >= OldCatacombsPieces.MIN_Y && startBox.minY() <= OldCatacombsPieces.MAX_Y,
                    "起点房间高度应在 6~32：" + startBox.minY());
            List<OldCatacombsPieces.Room> roomList = new ArrayList<>();
            List<OldCatacombsPieces.Corridor> corridors = new ArrayList<>();
            for (StructurePiece piece : pieces) {
                BoundingBox box = piece.getBoundingBox();
                int radius = OldCatacombsPieces.radiusOf(center, box);
                maxRadius = Math.max(maxRadius, radius);
                helper.assertTrue(radius <= OldCatacombsPieces.CATACOMB_MAX_RADIUS, "种子 " + i + "：件离起点中心 " + radius + " 格，超过 112");
                helper.assertTrue(box.minY() == startBox.minY() && box.maxY() == startBox.maxY(), "种子 " + i + "：墓穴的件应在同一高度");
                if (piece instanceof OldCatacombsPieces.Room room) {
                    roomList.add(room);
                    helper.assertTrue(room.getGenDepth() <= OldCatacombsPieces.MAX_ROOM_DEPTH, "房间深度应 ≤ 7：" + room.getGenDepth());
                    helper.assertTrue(box.getXSpan() == 5 && box.getZSpan() == 5 && box.getYSpan() == 5, "房间应是 5 × 5 × 5：" + box);
                } else if (piece instanceof OldCatacombsPieces.Corridor corridor) {
                    corridors.add(corridor);
                    helper.assertTrue(corridor.getGenDepth() <= OldCatacombsPieces.MAX_ROOM_DEPTH + 1, "走廊深度应 ≤ 8：" + corridor.getGenDepth());
                    int length = corridor.direction().getAxis() == Direction.Axis.X ? box.getXSpan() : box.getZSpan();
                    int width = corridor.direction().getAxis() == Direction.Axis.X ? box.getZSpan() : box.getXSpan();
                    helper.assertTrue(length >= 32 && length <= 63 && width == 5, "走廊应是 5 宽、32~63 长：" + box);
                } else {
                    helper.fail("布局阶段不该有别的件：" + piece);
                }
            }
            for (int a = 0; a < pieces.size(); a++) {
                for (int b = a + 1; b < pieces.size(); b++) {
                    helper.assertFalse(pieces.get(a).getBoundingBox().intersects(pieces.get(b).getBoundingBox()),
                            "种子 " + i + "：两个件相交 " + pieces.get(a).getBoundingBox() + " / " + pieces.get(b).getBoundingBox());
                }
            }
            // 连通：每条走廊的起点那头接着一间开了口的房间；除起点外每间房都有一条走廊接在它进来的那面
            for (OldCatacombsPieces.Corridor corridor : corridors) {
                BoundingBox box = corridor.getBoundingBox();
                Direction dir = corridor.direction();
                BoundingBox parent = roomBefore(box, dir);
                helper.assertTrue(roomList.stream().anyMatch(r -> r.getBoundingBox().equals(parent) && r.isOpen(dir)),
                        "种子 " + i + "：走廊 " + box + "（" + dir + "）起点那头应接着一间朝它开口的房间 " + parent);
            }
            for (OldCatacombsPieces.Room room : roomList) {
                Direction from = room.from();
                if (room == start) {
                    helper.assertTrue(from == null, "起点房间没有「进来的那面」");
                    continue;
                }
                helper.assertTrue(from != null && room.isOpen(from), "种子 " + i + "：非起点房间应在进来的那面开口");
                Direction corridorDir = from.getOpposite();
                helper.assertTrue(corridors.stream().anyMatch(c -> c.direction() == corridorDir
                                && roomAfter(c.getBoundingBox(), corridorDir).equals(room.getBoundingBox())),
                        "种子 " + i + "：房间 " + room.getBoundingBox() + " 应接在一条朝 " + corridorDir + " 的走廊尽头（向南的走廊接南端）");
                if (room.isExit()) {
                    exits++;
                }
            }
            if (start.isExit()) {
                exits++;
            }
            rooms += roomList.size();

            // 出口烟囱：地面不高于房顶不加；高于房顶就每个出口房间一个，顶 = 地面上第一格空位
            int exitRooms = (int) roomList.stream().filter(OldCatacombsPieces.Room::isExit).count();
            StructurePiecesBuilder low = new StructurePiecesBuilder();
            catacombLayout(low, seed);
            helper.assertTrue(OldCatacombsPieces.addExits(low, (x, z) -> startBox.maxY()) == 0, "地面不高于房顶时不该加烟囱");
            int top = startBox.maxY() + 40;
            helper.assertTrue(OldCatacombsPieces.addExits(builder, (x, z) -> top) == exitRooms, "每个出口房间应有一个烟囱");
            for (StructurePiece piece : builder.build().pieces()) {
                if (piece instanceof OldCatacombsPieces.Exit exit) {
                    helper.assertTrue(exit.top() == top && exit.getBoundingBox().maxY() == top && exit.floorY() == startBox.minY(),
                            "烟囱顶应在给的地面高度：" + exit.getBoundingBox());
                }
            }
        }
        // 同一个种子布局两次完全一样
        StructurePiecesBuilder first = new StructurePiecesBuilder();
        StructurePiecesBuilder second = new StructurePiecesBuilder();
        catacombLayout(first, 77L * SPREAD);
        catacombLayout(second, 77L * SPREAD);
        helper.assertTrue(boxes(first).equals(boxes(second)), "同一个种子两次布局应完全一样");

        double meanRooms = (double) rooms / seeds;
        double exitShare = (double) exits / rooms;
        helper.assertTrue(meanRooms >= 12D && meanRooms <= 17D, "每座墓穴房间平均应在 12~17（模拟 14.5），实际 " + meanRooms);
        helper.assertTrue(exitShare >= 0.07D && exitShare <= 0.13D, "出口占房间的比例应在 0.07~0.13（EXIT_CHANCE 0.1），实际 " + exitShare);
        helper.assertTrue(maxRadius <= OldCatacombsPieces.CATACOMB_MAX_RADIUS && maxRadius >= 90, "最远的件应被截在 112 格以内（又不至于太近），实际 " + maxRadius);
        helper.succeed();
    }

    /** 走廊（走向 dir）起点那头的房间包围盒 */
    private static BoundingBox roomBefore(BoundingBox corridor, Direction dir) {
        return switch (dir) {
            case SOUTH -> new BoundingBox(corridor.minX(), corridor.minY(), corridor.minZ() - 5, corridor.minX() + 4, corridor.maxY(), corridor.minZ() - 1);
            case NORTH -> new BoundingBox(corridor.minX(), corridor.minY(), corridor.maxZ() + 1, corridor.minX() + 4, corridor.maxY(), corridor.maxZ() + 5);
            case EAST -> new BoundingBox(corridor.minX() - 5, corridor.minY(), corridor.minZ(), corridor.minX() - 1, corridor.maxY(), corridor.minZ() + 4);
            default -> new BoundingBox(corridor.maxX() + 1, corridor.minY(), corridor.minZ(), corridor.maxX() + 5, corridor.maxY(), corridor.minZ() + 4);
        };
    }

    /** 走廊（走向 dir）尽头的房间包围盒 */
    private static BoundingBox roomAfter(BoundingBox corridor, Direction dir) {
        return switch (dir) {
            case SOUTH -> new BoundingBox(corridor.minX(), corridor.minY(), corridor.maxZ() + 1, corridor.minX() + 4, corridor.maxY(), corridor.maxZ() + 5);
            case NORTH -> new BoundingBox(corridor.minX(), corridor.minY(), corridor.minZ() - 5, corridor.minX() + 4, corridor.maxY(), corridor.minZ() - 1);
            case EAST -> new BoundingBox(corridor.maxX() + 1, corridor.minY(), corridor.minZ(), corridor.maxX() + 5, corridor.maxY(), corridor.minZ() + 4);
            default -> new BoundingBox(corridor.minX() - 5, corridor.minY(), corridor.minZ(), corridor.minX() - 1, corridor.maxY(), corridor.minZ() + 4);
        };
    }

    @GameTest(template = EMPTY)
    public static void shaftLayoutWithinReferenceRange(GameTestHelper helper) {
        int seeds = 300;
        int eastBranches = 0;
        long totalPieces = 0;
        int maxRadius = 0;
        int maxDepth = 0;
        for (int i = 0; i < seeds; i++) {
            long seed = (i + 1L) * SPREAD;
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(seed));
            StructurePiecesBuilder builder = new StructurePiecesBuilder();
            ModernShaftPieces.Room room = ModernShaftPieces.layout(builder, random, 2, 2);
            List<StructurePiece> pieces = builder.build().pieces();
            totalPieces += pieces.size();
            BlockPos center = room.getBoundingBox().getCenter();
            for (StructurePiece piece : pieces) {
                int radius = OldCatacombsPieces.radiusOf(center, piece.getBoundingBox());
                maxRadius = Math.max(maxRadius, radius);
                maxDepth = Math.max(maxDepth, piece.getGenDepth());
                helper.assertTrue(radius <= 112, "种子 " + i + "：矿井的件离大厅中心 " + radius + " 格，超过 112（8 区块引用范围）");
                helper.assertTrue(piece.getGenDepth() <= ModernShaftPieces.MAX_DEPTH + 1, "种子 " + i + "：深度应 ≤ 9：" + piece.getGenDepth());
            }
            for (int a = 0; a < pieces.size(); a++) {
                for (int b = a + 1; b < pieces.size(); b++) {
                    helper.assertFalse(pieces.get(a).getBoundingBox().intersects(pieces.get(b).getBoundingBox()),
                            "种子 " + i + "：两个件相交 " + pieces.get(a).getBoundingBox() + " / " + pieces.get(b).getBoundingBox());
                }
            }
            BoundingBox roomBox = room.getBoundingBox();
            if (room.entrances().stream().anyMatch(e -> e.minX() == roomBox.maxX() - 1)) {
                eastBranches++;
            }
            ModernShaftPieces.moveBelowSeaLevel(builder, 63, -64, random);
            BoundingBox all = builder.getBoundingBox();
            helper.assertTrue(all.maxY() <= 63 - ModernShaftPieces.SEA_LEVEL_MARGIN - 1 && all.minY() >= -64,
                    "种子 " + i + "：挪完应整座在 y 52 以下、世界底之上：" + all);
            BoundingBox moved = room.getBoundingBox();
            for (BoundingBox entrance : room.entrances()) {
                helper.assertTrue(entrance.intersects(moved), "种子 " + i + "：支路口应跟着大厅一起挪：" + entrance + " / " + moved);
            }
        }
        double share = (double) eastBranches / seeds;
        double meanPieces = (double) totalPieces / seeds;
        helper.assertTrue(share >= 0.2D, "大厅向东有支路的应 ≥ 20%（修原作 L570；模拟约 52%），实际 " + share);
        helper.assertTrue(maxDepth == ModernShaftPieces.MAX_DEPTH + 1, "300 座里应有长到深度 9 的，实际最深 " + maxDepth);
        helper.assertTrue(meanPieces >= 90D && meanPieces <= 170D, "每座矿井平均件数应在 90~170（模拟约 127），实际 " + meanPieces);
        helper.assertTrue(maxRadius <= 112, "最远 " + maxRadius);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 存档

    @GameTest(template = EMPTY)
    public static void undergroundPiecesRoundTripNbt(GameTestHelper helper) {
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(helper.getLevel());
        List<StructurePiece> pieces = new ArrayList<>();
        // 墓穴：找一个有出口房间的种子，按「地面在房顶上 20 格」加烟囱
        for (int i = 1; i < 200; i++) {
            StructurePiecesBuilder builder = new StructurePiecesBuilder();
            OldCatacombsPieces.Room start = catacombLayout(builder, i * SPREAD);
            int top = start.getBoundingBox().maxY() + 20;
            if (OldCatacombsPieces.addExits(builder, (x, z) -> top) > 0) {
                pieces.addAll(builder.build().pieces());
                break;
            }
        }
        // 矿井：挪到海平面以下后再存（大厅的支路口要跟着挪）
        for (int i = 1; i < 200; i++) {
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(i * SPREAD));
            StructurePiecesBuilder builder = new StructurePiecesBuilder();
            ModernShaftPieces.layout(builder, random, 2, 2);
            ModernShaftPieces.moveBelowSeaLevel(builder, 63, -64, random);
            List<StructurePiece> shaft = builder.build().pieces();
            if (shaft.stream().anyMatch(p -> p instanceof ModernShaftPieces.Stairs) && shaft.stream().anyMatch(p -> p instanceof ModernShaftPieces.Crossing)
                    && shaft.stream().anyMatch(p -> p instanceof ModernShaftPieces.Corridor c && c.hasRails())
                    && shaft.stream().anyMatch(p -> p instanceof ModernShaftPieces.Corridor c && c.isSpiderCorridor())) {
                pieces.addAll(shaft);
                break;
            }
        }
        Set<ResourceLocation> seen = new HashSet<>();
        for (StructurePiece piece : pieces) {
            CompoundTag tag = piece.createTag(context);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            seen.add(id);
            StructurePieceType type = BuiltInRegistries.STRUCTURE_PIECE.get(id);
            helper.assertTrue(type != null && type == piece.getType(), "拼件类型 " + id + " 应已注册、和拼件自己的类型一致");
            StructurePiece copy = type.load(context, tag);
            helper.assertTrue(copy.getClass() == piece.getClass(), id + " 读回的类不对：" + copy.getClass());
            helper.assertTrue(copy.getBoundingBox().equals(piece.getBoundingBox()) && copy.getGenDepth() == piece.getGenDepth()
                    && copy.getOrientation() == piece.getOrientation(), id + " 读回的包围盒 / 深度 / 朝向不一样");
            CompoundTag again = copy.createTag(context);
            helper.assertTrue(again.equals(tag), id + " 读回再存应和原来一样：\n" + tag + "\n" + again);
            if (piece instanceof OldCatacombsPieces.Room room && copy instanceof OldCatacombsPieces.Room back) {
                helper.assertTrue(room.from() == back.from() && room.openings() == back.openings() && room.isExit() == back.isExit()
                        && room.isBroken() == back.isBroken() && room.isGrown() == back.isGrown() && room.seed() == back.seed(), "墓穴房间字段读回不一样");
            }
            if (piece instanceof OldCatacombsPieces.Exit exit && copy instanceof OldCatacombsPieces.Exit back) {
                helper.assertTrue(exit.top() == back.top() && exit.side() == back.side() && exit.fromFloor() == back.fromFloor()
                        && exit.floorY() == back.floorY(), "烟囱字段读回不一样");
            }
            if (piece instanceof ModernShaftPieces.Room room && copy instanceof ModernShaftPieces.Room back) {
                helper.assertTrue(room.entrances().equals(back.entrances()) && !room.entrances().isEmpty(), "矿井大厅的支路口读回不一样");
            }
            if (piece instanceof ModernShaftPieces.Corridor corridor && copy instanceof ModernShaftPieces.Corridor back) {
                helper.assertTrue(corridor.hasRails() == back.hasRails() && corridor.isSpiderCorridor() == back.isSpiderCorridor()
                        && corridor.numSections() == back.numSections(), "矿井走廊字段读回不一样");
                if (corridor.isSpiderCorridor()) {
                    helper.assertTrue(corridor.spawnerPos().equals(back.spawnerPos()), "刷怪笼位置只看种子，读回应不变");
                }
            }
        }
        Set<ResourceLocation> expected = new HashSet<>();
        for (String name : List.of("catacomb_room", "catacomb_corridor", "catacomb_exit", "shaft_room", "shaft_corridor", "shaft_crossing", "shaft_stairs")) {
            expected.add(EssentialCraft5.id(name));
        }
        helper.assertTrue(seen.equals(expected), "7 种拼件都应测到：" + seen);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 出口房间与烟囱

    @GameTest(template = EMPTY_LARGE)
    public static void catacombExitRoomHasChestOnVoidStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox chunkBox = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(10, 7, 10)));
        BoundingBox roomBox = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(3, 1, 3)), helper.absolutePos(new BlockPos(7, 5, 7)));
        // 东面接着走廊（开口），西墙完整：梯子挂西墙、从地板通到烟囱顶
        OldCatacombsPieces.Room room = new OldCatacombsPieces.Room(2, 11L * SPREAD, roomBox, Direction.EAST, false, false, true);
        int top = helper.absolutePos(new BlockPos(0, 7, 0)).getY();
        OldCatacombsPieces.Exit exit = new OldCatacombsPieces.Exit(room, top);
        helper.assertTrue(exit.side() == Direction.WEST && exit.fromFloor(), "西墙没开口：梯子应挂西墙、从地板开始");
        placeAll(level, List.of(room, exit), chunkBox);

        BlockPos chestPos = helper.absolutePos(new BlockPos(5, 3, 5));
        BlockState chest = level.getBlockState(chestPos);
        helper.assertTrue(chest.is(Blocks.CHEST) && chest.getValue(ChestBlock.FACING) == Direction.WEST, "(2, 2, 2) 应是正面朝梯子（西）的箱子：" + chest);
        BlockEntity be = level.getBlockEntity(chestPos);
        helper.assertTrue(be instanceof ChestBlockEntity && HoannaLoot.CATACOMBS.toString().equals(be.saveWithoutMetadata().getString("LootTable")),
                "箱子应带 chests/catacombs 战利品表");
        BlockState base = level.getBlockState(chestPos.below());
        Block expectedBase = HoannaStructureBlocks.isRegistered(HoannaStructureBlocks.VOID_STONE_ID) ? HoannaStructureBlocks.voidStone().getBlock() : Blocks.STONE;
        helper.assertTrue(base.is(expectedBase), "箱子下面应垫虚空石（没注册时是石头）：" + base);

        Block fortified = ECMaterials.FORTIFIED_STONE.get();
        for (int y = 2; y <= 7; y++) {
            BlockState ladder = level.getBlockState(helper.absolutePos(new BlockPos(4, y, 5)));
            helper.assertTrue(ladder.is(Blocks.LADDER) && ladder.getValue(LadderBlock.FACING) == Direction.EAST, "rel y " + y + " 应是挂西墙的梯子：" + ladder);
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(3, y, 5))).is(fortified), "梯子后面应是强化石墙（rel y " + y + "）");
        }
        // 房顶（rel y 5）和烟囱顶（rel y 7）打通、四周一圈强化石；房间东面开口
        for (int y : new int[]{5, 6, 7}) {
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(5, y, 5))).isAir()
                    && level.getBlockState(helper.absolutePos(new BlockPos(6, y, 6))).isAir(), "烟囱里应是空的（rel y " + y + "）");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(3, y, 3))).is(fortified)
                    && level.getBlockState(helper.absolutePos(new BlockPos(7, y, 7))).is(fortified), "烟囱四角应是强化石（rel y " + y + "）");
        }
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(3, 1, 3))).is(fortified), "房间地板应是强化石");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(7, 3, 5))).isAir(), "房间东面应开口");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(6, 2, 6))).isAir(), "室内应是空的");

        // 四面都开口的出口房间：梯子只从房顶那层开始（原作的写法）
        OldCatacombsPieces.Room open = new OldCatacombsPieces.Room(2, 12L * SPREAD, roomBox, Direction.EAST, false, false, true);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            open.open(side);
        }
        OldCatacombsPieces.Exit openExit = new OldCatacombsPieces.Exit(open, top);
        helper.assertTrue(openExit.side() == Direction.WEST && !openExit.fromFloor(), "四面都开口时梯子挂西墙、从房顶开始");
        helper.assertTrue(OldCatacombsPieces.ladderSide(1 << Direction.WEST.get2DDataValue()) == Direction.EAST, "西墙开口时应挂东墙");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 跨区块一致

    @GameTest(template = EMPTY_LARGE)
    public static void catacombPiecesChunkSeamless(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox region = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(10, 7, 10)));
        BoundingBox whole = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(10, 7, 10)));
        // 破损 + 长草 + 出口的房间（东面接走廊），走廊往东；房间从 x 0~4，切在 x 2 | 3（梯子和它挂的墙、箱子都在西半）
        BoundingBox roomBox = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 3)), helper.absolutePos(new BlockPos(4, 5, 7)));
        OldCatacombsPieces.Room room = new OldCatacombsPieces.Room(1, 5L * SPREAD, roomBox, Direction.EAST, true, true, true);
        OldCatacombsPieces.Corridor corridor = new OldCatacombsPieces.Corridor(1, 6L * SPREAD,
                BoundingBox.fromCorners(helper.absolutePos(new BlockPos(5, 1, 3)), helper.absolutePos(new BlockPos(10, 5, 7))), Direction.EAST);
        OldCatacombsPieces.Exit exit = new OldCatacombsPieces.Exit(room, helper.absolutePos(new BlockPos(0, 7, 0)).getY());
        List<StructurePiece> pieces = List.of(room, corridor, exit);
        BoundingBox west = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(2, 7, 10)));
        BoundingBox east = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(3, 1, 0)), helper.absolutePos(new BlockPos(10, 7, 10)));

        Map<BlockPos, BlockState> afterWhole = seamCompare(helper, level, pieces, region, whole, west, east);
        // 长草 + 破损也盖不掉出口的东西；长草房间的墙后面补回强化石、梯子挂得住
        BlockPos chestPos = helper.absolutePos(new BlockPos(2, 3, 5));
        helper.assertTrue(afterWhole.get(chestPos).is(Blocks.CHEST), "长草 + 破损的出口房间箱子应还在：" + afterWhole.get(chestPos));
        Block expectedBase = HoannaStructureBlocks.isRegistered(HoannaStructureBlocks.VOID_STONE_ID) ? HoannaStructureBlocks.voidStone().getBlock() : Blocks.STONE;
        helper.assertTrue(afterWhole.get(chestPos.below()).is(expectedBase), "箱子下面的虚空石应还在");
        helper.assertTrue(afterWhole.get(chestPos.above()).isAir(), "箱子正上方不该有碎块（压着实心方块的箱子打不开）");
        for (int y = 2; y <= 7; y++) {
            helper.assertTrue(afterWhole.get(helper.absolutePos(new BlockPos(1, y, 5))).is(Blocks.LADDER), "梯子应从地板通到顶（rel y " + y + "）");
        }
        if (HoannaStructureBlocks.isRegistered(HoannaStructureBlocks.ROOT_ID)) {
            helper.assertTrue(afterWhole.get(helper.absolutePos(new BlockPos(3, 2, 6))).is(HoannaStructureBlocks.root().getBlock()),
                    "长草房间室内 (3, 1, 3) 应有根系");
        }
        int leaves = 0;
        for (BlockState state : afterWhole.values()) {
            if (state.is(Blocks.OAK_LEAVES)) {
                leaves++;
                helper.assertTrue(state.getValue(LeavesBlock.PERSISTENT), "墓穴里的树叶应不会腐烂");
            }
        }
        helper.assertTrue(leaves > 10, "长草房间应有一些树叶，实际 " + leaves);
        helper.succeed();
    }

    @GameTest(template = EMPTY_LARGE)
    public static void shaftCorridorChunkSeamless(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox region = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(10, 7, 10)));
        BoundingBox whole = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(10, 7, 10)));
        // 两段朝南的走廊（各两组支架）：一段铺铁轨、一段是洞穴蜘蛛走廊；切在 z 4 | 5（组与组之间，火把和横梁在同一边）
        ModernShaftPieces.Corridor rails = new ModernShaftPieces.Corridor(1, 21L * SPREAD,
                BoundingBox.fromCorners(helper.absolutePos(new BlockPos(1, 2, 0)), helper.absolutePos(new BlockPos(3, 4, 9))), Direction.SOUTH, true, false);
        ModernShaftPieces.Corridor spiders = new ModernShaftPieces.Corridor(1, 22L * SPREAD,
                BoundingBox.fromCorners(helper.absolutePos(new BlockPos(6, 2, 0)), helper.absolutePos(new BlockPos(8, 4, 9))), Direction.SOUTH, false, true);
        helper.assertTrue(rails.numSections() == 2 && spiders.numSections() == 2, "10 格长的走廊应是 2 组支架");
        List<StructurePiece> pieces = List.of(rails, spiders);
        BoundingBox north = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(10, 7, 4)));
        BoundingBox south = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 5)), helper.absolutePos(new BlockPos(10, 7, 10)));

        Map<BlockPos, BlockState> afterWhole = seamCompare(helper, level, pieces, region, whole, north, south);
        Block beam = HoannaStructureBlocks.voidStone().getBlock();
        Block fence = HoannaStructureBlocks.voidFence().getBlock();
        int railCount = 0;
        int webCount = 0;
        for (int z = 0; z <= 9; z++) {
            helper.assertTrue(afterWhole.get(helper.absolutePos(new BlockPos(2, 1, z))).is(beam), "走廊地板下原来是空气的格子应补虚空石（z " + z + "）");
            if (afterWhole.get(helper.absolutePos(new BlockPos(2, 2, z))).is(Blocks.RAIL)) {
                railCount++;
            }
            for (int x = 6; x <= 8; x++) {
                for (int y = 2; y <= 3; y++) {
                    if (afterWhole.get(helper.absolutePos(new BlockPos(x, y, z))).is(Blocks.COBWEB)) {
                        webCount++;
                    }
                }
            }
        }
        for (int k : new int[]{2, 7}) {
            helper.assertTrue(afterWhole.get(helper.absolutePos(new BlockPos(1, 2, k))).is(fence) && afterWhole.get(helper.absolutePos(new BlockPos(3, 3, k))).is(fence),
                    "每组两根栅栏立柱（z " + k + "）");
            helper.assertTrue(afterWhole.get(helper.absolutePos(new BlockPos(1, 4, k))).is(beam) && afterWhole.get(helper.absolutePos(new BlockPos(3, 4, k))).is(beam),
                    "每组两头有虚空石横梁（z " + k + "）");
        }
        helper.assertTrue(railCount >= 3, "铺铁轨的走廊 10 格里应有 70% 左右是铁轨，实际 " + railCount);
        helper.assertTrue(webCount >= 10, "洞穴蜘蛛走廊下面两层 60% 是蜘蛛网，实际 " + webCount);
        BlockPos spawner = spiders.spawnerPos();
        helper.assertTrue(afterWhole.get(spawner).is(Blocks.SPAWNER), "洞穴蜘蛛走廊应有一个刷怪笼：" + spawner);
        helper.succeed();
    }

    /**
     * 整块放一遍、记下结果、还原；再分两块（先 second 后 first，和整块的遍历顺序反过来）放一遍：只放 second 时 first 那半和 region 里 chunkBox 外的格子不变，
     * 两种放法逐格相同（比方块种类：完整区块里铁轨放下时会按邻居拐弯，那是完整区块才有的，真实生成里没有）。返回整块放的结果（已还原世界）。
     */
    private static Map<BlockPos, BlockState> seamCompare(GameTestHelper helper, ServerLevel level, List<StructurePiece> pieces, BoundingBox region,
                                                          BoundingBox whole, BoundingBox first, BoundingBox second) {
        Map<BlockPos, BlockState> original = snapshot(level, region);
        placeAll(level, pieces, whole);
        Map<BlockPos, BlockState> afterWhole = snapshot(level, region);
        restore(level, original);
        helper.killAllEntitiesOfClass(MinecartChest.class);

        placeAll(level, pieces, second);
        for (Map.Entry<BlockPos, BlockState> entry : snapshot(level, region).entrySet()) {
            if (!second.isInside(entry.getKey())) {
                helper.assertTrue(entry.getValue() == original.get(entry.getKey()), "只处理一半时 chunkBox 外不该有变化：" + entry.getKey());
            }
        }
        placeAll(level, pieces, first);
        Map<BlockPos, BlockState> afterSplit = snapshot(level, region);
        int changed = 0;
        for (Map.Entry<BlockPos, BlockState> entry : afterWhole.entrySet()) {
            BlockState split = afterSplit.get(entry.getKey());
            helper.assertTrue(split.getBlock() == entry.getValue().getBlock(),
                    "分两块放和整块放在 " + entry.getKey() + " 不一样：整块 " + entry.getValue() + "，分块 " + split);
            if (entry.getValue() != original.get(entry.getKey())) {
                changed++;
            }
            if (!whole.isInside(entry.getKey())) {
                helper.assertTrue(entry.getValue() == original.get(entry.getKey()), "chunkBox 外不该有变化：" + entry.getKey());
            }
        }
        helper.assertTrue(changed > 50, "应放了不少方块，实际 " + changed);
        restore(level, original);
        helper.killAllEntitiesOfClass(MinecartChest.class);
        return afterWhole;
    }

    // ------------------------------------------------------------------ 真的海纳尔生成器

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void undergroundStructuresGenerateInHoanna(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        DimensionType dimension = hoanna.stem().type().value();
        LevelHeightAccessor height = LevelHeightAccessor.create(dimension.minY(), dimension.height());
        Structure catacombs = server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(CATACOMBS);
        Structure shafts = server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(SHAFTS);
        helper.assertTrue(catacombs != null && shafts != null, "数据包里应有两个结构");
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(helper.getLevel());
        int exits = 0;
        for (int i = 0; i < 24; i++) {
            ChunkPos chunk = new ChunkPos(i * 37 - 400, i * 53 - 600);
            StructureStart start = generate(server, hoanna, height, catacombs, chunk);
            helper.assertTrue(start.isValid(), "墓穴在海纳尔 " + chunk + " 应能生成");
            helper.assertTrue(piecesTag(context, start).equals(piecesTag(context, generate(server, hoanna, height, catacombs, chunk))),
                    "同一区块生成两次的墓穴应完全一样：" + chunk);
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof OldCatacombsPieces.Exit exit) {
                    exits++;
                    int roomTop = exit.floorY() + OldCatacombsPieces.ROOM_SIZE - 1;
                    helper.assertTrue(exit.top() > roomTop && exit.top() >= 40, "墓穴烟囱应通到真实地面（高过房顶、不低于 y 40）：" + exit.getBoundingBox());
                }
            }
            StructureStart mine = generate(server, hoanna, height, shafts, chunk);
            helper.assertTrue(mine.isValid(), "矿井在海纳尔 " + chunk + " 应能生成");
            BoundingBox box = mine.getBoundingBox();
            helper.assertTrue(box.maxY() <= hoanna.settings().seaLevel() - ModernShaftPieces.SEA_LEVEL_MARGIN - 1 && box.minY() >= dimension.minY(),
                    "矿井应整座在海平面 − 10 以下：" + box);
        }
        helper.assertTrue(exits > 0, "24 座墓穴里应有带烟囱的出口");
        helper.succeed();
    }

    private static StructureStart generate(MinecraftServer server, HoannaTestWorld.Resolved hoanna, LevelHeightAccessor height, Structure structure,
                                           ChunkPos chunk) {
        return structure.generate(server.registryAccess(), hoanna.generator(), hoanna.biomeSource(), hoanna.randomState(), server.getStructureManager(),
                hoanna.seed(), chunk, 0, height, structure.biomes()::contains);
    }

    private static ListTag piecesTag(StructurePieceSerializationContext context, StructureStart start) {
        ListTag list = new ListTag();
        for (StructurePiece piece : start.getPieces()) {
            list.add(piece.createTag(context));
        }
        return list;
    }

    // ------------------------------------------------------------------ 端到端放置

    @GameTest(template = EMPTY, batch = PLACE_BATCH, timeoutTicks = 600)
    public static void placeCatacombsEndToEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(30000 >> 4, 34000 >> 4);
        StructureStart start = placeNear(helper, level, CATACOMBS, chunk, 2);
        BoundingBox startRoom = start.getPieces().get(0).getBoundingBox();
        Block fortified = ECMaterials.FORTIFIED_STONE.get();
        int shell = 0;
        int total = 0;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (withinChunks(pos, chunk, 2) && level.getBlockState(pos).is(fortified)) {
                    total++;
                    if (startRoom.isInside(pos)) {
                        shell++;
                    }
                }
            }
        }
        helper.assertTrue(shell >= 20, "起点房间应有强化石外壳，实际 " + shell + " 格");
        helper.assertTrue(total >= 200, "起点周围 5 × 5 区块里应有墓穴的强化石房间和走廊，实际 " + total + " 格");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = PLACE_BATCH, timeoutTicks = 600)
    public static void placeShaftsEndToEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(34000 >> 4, 34000 >> 4);
        StructureStart start = placeNear(helper, level, SHAFTS, chunk, 2);
        Block fence = HoannaStructureBlocks.voidFence().getBlock();
        Block beam = HoannaStructureBlocks.voidStone().getBlock();
        int checked = 0;
        int railCells = 0;
        int rails = 0;
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof ModernShaftPieces.Corridor corridor) {
                BlockPos post = worldPos(corridor, 0, 0, 2);
                BlockPos beamPos = worldPos(corridor, 0, 2, 2);
                // 超平坦主世界：矿井挪到 y 2 往上，整座悬在空中、碰不到液体，每组支架都在
                if (withinChunks(post, chunk, 2) && withinChunks(beamPos, chunk, 2)) {
                    helper.assertTrue(level.getBlockState(post).is(fence), "走廊第一组应有栅栏立柱：" + post + " " + level.getBlockState(post));
                    helper.assertTrue(level.getBlockState(beamPos).is(beam), "走廊第一组应有横梁：" + beamPos + " " + level.getBlockState(beamPos));
                    checked++;
                }
                if (corridor.hasRails()) {
                    for (int z = 0; z < corridor.numSections() * 5; z++) {
                        BlockPos pos = worldPos(corridor, 1, 0, z);
                        if (withinChunks(pos, chunk, 2)) {
                            railCells++;
                            if (level.getBlockState(pos).is(Blocks.RAIL)) {
                                rails++;
                            }
                        }
                    }
                }
            }
        }
        helper.assertTrue(checked >= 1, "起点周围 5 × 5 区块里应有矿井走廊");
        helper.assertTrue(railCells < 10 || rails > 0, "铺铁轨的走廊（" + railCells + " 格）里应有铁轨（每格 70%），实际 " + rails);
        helper.succeed();
    }

    /**
     * 按固定种子在超平坦主世界的 chunk 处生成结构（群系谓词恒真，同 /place），起点整份 NBT 读回应相同，然后同步加载起点周围 ±radius 区块、逐个放进去。
     * GameTest 服的主世界是超平坦：墓穴（y 6~32）、矿井（挪到 y 2 往上）都悬在地面以上的空气里，方块一个不少，正好看全。
     */
    private static StructureStart placeNear(GameTestHelper helper, ServerLevel level, ResourceLocation id, ChunkPos chunk, int radius) {
        long seed = 0x5EED6A06L * SPREAD;
        StructureStart start = UndergroundStructures.generateAt(level, id, chunk, seed);
        helper.assertTrue(start.isValid(), id + " 应能生成（数据包里有这个结构）");

        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
        CompoundTag tag = start.createTag(context, chunk);
        StructureStart loaded = StructureStart.loadStaticStart(context, tag, seed);
        helper.assertTrue(loaded != null && loaded.getPieces().size() == start.getPieces().size(), id + " 的结构起点应能整份读回（件数一样）");
        helper.assertTrue(loaded.createTag(context, chunk).equals(tag), id + " 的结构起点读回再存应一样");

        int placed = UndergroundStructures.placeAround(level, start, chunk, radius);
        helper.assertTrue(placed == (2 * radius + 1) * (2 * radius + 1), "应放进 " + (2 * radius + 1) + " × " + (2 * radius + 1) + " 个区块");
        return start;
    }

    private static boolean withinChunks(BlockPos pos, ChunkPos center, int radius) {
        return Math.abs((pos.getX() >> 4) - center.x) <= radius && Math.abs((pos.getZ() >> 4) - center.z) <= radius;
    }

    /** 有朝向的拼件的局部坐标 → 世界坐标（照 StructurePiece.getWorldX / getWorldZ 的规则） */
    private static BlockPos worldPos(StructurePiece piece, int x, int y, int z) {
        BoundingBox box = piece.getBoundingBox();
        Direction orientation = Objects.requireNonNull(piece.getOrientation());
        int wx = switch (orientation) {
            case WEST -> box.maxX() - z;
            case EAST -> box.minX() + z;
            default -> box.minX() + x;
        };
        int wz = switch (orientation) {
            case NORTH -> box.maxZ() - z;
            case SOUTH -> box.minZ() + z;
            default -> box.minZ() + x;
        };
        return new BlockPos(wx, box.minY() + y, wz);
    }

    // ------------------------------------------------------------------ 工具

    /** 墓穴布局：起点在区块 (0, 0) 的 (2, y, 2)，高度照结构的抽法 */
    private static OldCatacombsPieces.Room catacombLayout(StructurePiecesBuilder builder, long seed) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(seed));
        int y = random.nextIntBetweenInclusive(OldCatacombsPieces.MIN_Y, OldCatacombsPieces.MAX_Y);
        return OldCatacombsPieces.layout(builder, random, 2, y, 2);
    }

    private static List<BoundingBox> boxes(StructurePiecesBuilder builder) {
        List<BoundingBox> list = new ArrayList<>();
        for (StructurePiece piece : builder.build().pieces()) {
            list.add(piece.getBoundingBox());
        }
        return list;
    }

    /** 按顺序对每个拼件调 postProcess（和 StructureStart.placeInChunk 一样：只处理和 chunkBox 相交的件） */
    private static void placeAll(ServerLevel level, List<StructurePiece> pieces, BoundingBox chunkBox) {
        RandomSource random = RandomSource.create(42L);
        ChunkPos chunkPos = new ChunkPos(chunkBox.getCenter());
        BlockPos pivot = pieces.get(0).getBoundingBox().getCenter();
        for (StructurePiece piece : pieces) {
            if (piece.getBoundingBox().intersects(chunkBox)) {
                piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), random, chunkBox, chunkPos, pivot);
            }
        }
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, BoundingBox box) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            states.put(pos.immutable(), level.getBlockState(pos));
        }
        return states;
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> states) {
        states.forEach((pos, state) -> {
            if (level.getBlockState(pos) != state) {
                level.setBlock(pos, state, QUIET);
            }
        });
    }
}
