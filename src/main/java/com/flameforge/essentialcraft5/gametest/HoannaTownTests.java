package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.loot.HoannaLoot;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaStructures;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureRuin;
import com.flameforge.essentialcraft5.worldgen.hoanna.town.HoannaTownPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.town.HoannaTownStructure;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
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
import java.util.function.IntBinaryOperator;

/**
 * 城镇废墟（M5，batch6a §5.5、§4.4 第 6 条）。
 * <ul>
 *   <li>{@code structureSetsUseGridPlacement}：结构集 random_spread 32 / 8、salt 202609291、linear；结构 surface_structures、不用 beard、没有 spawn_overrides、
 *       只在 6 个陆地群系。</li>
 *   <li>{@code structuresNotInOverworld}：原版主世界（原版噪声 + 原版群系表手建）的结构集里没有城镇，海纳尔（固定种子手建）有；真世界里只有海纳尔有。</li>
 *   <li>{@code townOnlyOnLand}：海纳尔之海上的候选区块不生成，陆地上的生成、所有件的地面不低于海平面。</li>
 *   <li>{@code townLayoutMatchesOriginal}：500 个区块的布局和原作模拟一致（件数、塔数、道路 ≤ 8、离中心 ≤ 64、弹坑半径 ≤ 15、包围盒在起点 ±8 区块里；
 *       D15 道路分叉照原作写反：南北向的路只有起点那两条）；同一区块布局两次结果一样。</li>
 *   <li>{@code townPiecesSampleOneColumnEach}（6a 审查 M5-1）：拼件定高每个非道路件只取包围盒中心 1 列、按拼件顺序，各件地面 = 那一列；
 *       每座平均 ≤ 20 列（一列噪声约 3 ms，/locate 和探索类指南针在主线程上等它）。</li>
 *   <li>{@code houseRuinKeepsGroundFloor}：3 层的房子（加弹坑）：一楼楼板全是强化石、一楼的墙没缺、四角地下是悬浮器、顶层墙有洞；只给半个 chunkBox 时另一半一格不动；
 *       分两半盖和一次盖完逐格相同。</li>
 *   <li>{@code houseCratersSpareGroundFloor}：房子的坑对强化石 / 强化玻璃用系数 0.5、只挖二楼楼板（地面 + 5）及以上。F = 1 / 2 的房子挑坑心正好在二楼楼板的种子：
 *       一楼楼板、墙、天花板一格不缺，坑心和上、东南西北五格炸空；实心强化石里单独挖一个半径 3 的房子坑，挖掉 ≥ 6 格、坑心以下一格不动。</li>
 *   <li>{@code townRoadFollowsSurface}：台阶地面上铺路：草 / 泥土 / 沙 / 水的地表换成混凝土（草丛清掉），石头列、路外面的列不动。</li>
 *   <li>{@code piecesRoundTripNbt}：7 种拼件 createTag 再按注册的拼件类型读回，NBT、包围盒、自定义字段都相同；整座城镇的结构起点存档读回相同（读不回来区块会加载失败）。</li>
 *   <li>{@code placeTownEndToEnd}（批 {@value #PLACE_BATCH}）：照 {@code /place structure} 的同一套调用，在远处超平坦上放一座挑过种子（有塔）的城镇：
 *       塔顶箱子（{@code chests/town_tower}）= 塔数 × 4；改动全在结构包围盒里；按区块正序、倒序各放一遍，结果逐格相同（跨区块确定性）。</li>
 * </ul>
 * GameTest 服的主世界是超平坦、区块都是完整区块，所以拼件放在远处（x、z 约 2.8 万 / 3 万格，和测试结构不挨着）直接调 postProcess；
 * 要海纳尔地形 / 群系的用 {@link HoannaTestWorld}（固定种子手建生成器，不生成区块）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaTownTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 结构端到端测试的批（M6 的墓穴 / 矿井端到端也放这一批）：同批的测试一起跑，放在远处的空地上，不和别的测试抢区块 */
    public static final String PLACE_BATCH = "hoanna_place";

    private static final ResourceKey<Structure> TOWN = ResourceKey.create(Registries.STRUCTURE, EssentialCraft5.id("hoanna_town"));
    private static final ResourceKey<StructureSet> TOWN_SET = ResourceKey.create(Registries.STRUCTURE_SET, EssentialCraft5.id("hoanna_town"));
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** 布局统计用的世界种子（随便取的固定值） */
    private static final long LAYOUT_WORLD_SEED = 20260929L;
    /** 房子测试的空地（区块 1750, 1750 往东约 8 个区块；各测试在 x 上错开：+0~20、+48~130） */
    private static final int HOUSE_AREA = 28000;
    /** 端到端测试的城镇起点区块（x、z 约 3 万格） */
    private static final ChunkPos TOWN_CHUNK = new ChunkPos(1875, 1875);
    /** 打散候选种子用的大奇数（坑：挨着的小种子第一次抽到的数几乎一样） */
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;

    private HoannaTownTests() {
    }

    // ------------------------------------------------------------------ 数据包

    @GameTest(template = EMPTY)
    public static void structureSetsUseGridPlacement(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        StructureSet set = access.registryOrThrow(Registries.STRUCTURE_SET).get(TOWN_SET);
        helper.assertTrue(set != null, "结构集 essentialcraft5:hoanna_town 没加载（worldgen/structure_set/hoanna_town.json）");
        helper.assertTrue(set.placement() instanceof RandomSpreadStructurePlacement, "城镇应按格子放（random_spread），实际 " + set.placement());
        RandomSpreadStructurePlacement spread = (RandomSpreadStructurePlacement) set.placement();
        helper.assertTrue(spread.spacing() == 32 && spread.separation() == 8 && spread.spreadType() == RandomSpreadType.LINEAR,
                "城镇间距应是 32 / 8、linear（D13 A），实际 " + spread.spacing() + " / " + spread.separation() + " / " + spread.spreadType());
        JsonElement json = StructurePlacement.CODEC.encodeStart(JsonOps.INSTANCE, spread).result().orElseThrow();
        helper.assertTrue(json.getAsJsonObject().get("salt").getAsInt() == 202609291, "salt 应是 202609291：" + json);
        helper.assertTrue(!json.getAsJsonObject().has("frequency") || json.getAsJsonObject().get("frequency").getAsFloat() == 1F,
                "不该降频：" + json);
        helper.assertTrue(set.structures().size() == 1 && set.structures().get(0).structure().is(TOWN) && set.structures().get(0).weight() == 1,
                "结构集里应只有城镇一种");

        Structure structure = town(helper.getLevel());
        helper.assertTrue(structure.step() == GenerationStep.Decoration.SURFACE_STRUCTURES, "城镇应在 surface_structures 这一步放");
        helper.assertTrue(structure.terrainAdaptation() == TerrainAdjustment.NONE, "城镇不用 beard（terrain_adaptation none）");
        helper.assertTrue(structure.spawnOverrides().isEmpty(), "城镇不写 spawn_overrides");
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        for (ResourceKey<Biome> key : ECHoanna.BIOMES) {
            boolean expected = !key.equals(ECHoanna.HOANNA_SEA);
            helper.assertTrue(structure.biomes().contains(biomes.getHolderOrThrow(key)) == expected,
                    key.location() + (expected ? " 是陆地群系，应能生成城镇" : " 是海，不该生成城镇"));
        }
        helper.assertFalse(structure.biomes().contains(biomes.getHolderOrThrow(Biomes.PLAINS)), "原版群系不该生成城镇");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void structuresNotInOverworld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved vanilla = HoannaTestWorld.vanillaOverworld(server, HoannaTestWorld.SEED);
        helper.assertTrue(vanilla.structureState().possibleStructureSets().stream()
                .anyMatch(set -> set.is(BuiltinStructureSets.VILLAGES)),
                "对照组：原版主世界的结构集里应有村庄（不然这条测试测不出东西）");
        helper.assertFalse(hasTownSet(vanilla.structureState().possibleStructureSets()), "原版主世界的结构集里不该有城镇废墟");
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        helper.assertTrue(hasTownSet(hoanna.structureState().possibleStructureSets()), "海纳尔的结构集里应有城镇废墟");
        for (ServerLevel level : server.getAllLevels()) {
            boolean has = hasTownSet(level.getChunkSource().getGeneratorState().possibleStructureSets());
            boolean hoannaLevel = level.dimension().equals(ECHoanna.LEVEL);
            helper.assertTrue(has == hoannaLevel, level.dimension().location() + (hoannaLevel ? " 应有城镇废墟" : " 不该有城镇废墟"));
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void townOnlyOnLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(level.getServer());
        Structure structure = town(level);
        LevelHeightAccessor height = LevelHeightAccessor.create(hoanna.generator().getMinY(), hoanna.generator().getGenDepth());
        int seaLevel = hoanna.generator().getSeaLevel();
        ChunkPos sea = null;
        ChunkPos land = null;
        for (int i = 0; i < 4096 && (sea == null || land == null); i++) {
            ChunkPos chunk = new ChunkPos((i % 64) * 7 - 224, (i / 64) * 7 - 224);
            // 和结构自己判群系的位置一样：起点中心那一列的地面（水底算地面，不低于海平面）
            int x = chunk.getMinBlockX() + 4;
            int z = chunk.getMinBlockZ() + 4;
            int y = Math.max(seaLevel, hoanna.generator().getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, hoanna.randomState()));
            Holder<Biome> biome = hoanna.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                    hoanna.randomState().sampler());
            if (biome.is(ECHoanna.HOANNA_SEA)) {
                sea = sea == null ? chunk : sea;
            } else if (land == null) {
                land = chunk;
            }
        }
        helper.assertTrue(sea != null && land != null, "在 ±1500 格里没找到海或陆地（群系表变了？）：海 " + sea + "，陆地 " + land);
        StructureStart atSea = generate(level, structure, hoanna, sea, height);
        helper.assertFalse(atSea.isValid(), "海纳尔之海 " + sea + " 上不该生成城镇");
        StructureStart onLand = generate(level, structure, hoanna, land, height);
        helper.assertTrue(onLand.isValid(), "陆地 " + land + " 上应能生成城镇（布局非道路件 > 2 几乎总成立）");
        for (StructurePiece piece : onLand.getPieces()) {
            if (piece instanceof HoannaStructurePiece hoannaPiece) {
                helper.assertTrue(hoannaPiece.hasGround() && hoannaPiece.groundY() >= seaLevel,
                        "每件都应在生成阶段定好地面、不低于海平面 " + seaLevel + "：" + piece + " → " + hoannaPiece.groundY());
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 布局

    @GameTest(template = EMPTY)
    public static void townLayoutMatchesOriginal(GameTestHelper helper) {
        int samples = 500;
        int valid = 0;
        long pieces = 0;
        long towers = 0;
        int maxRoads = 0;
        int maxExtent = 0;
        int maxNorthSouthRoads = 0;
        int reach = StructureRuin.craterReach(HoannaTownStructure.EXPLOSION_RADIUS);
        for (int i = 0; i < samples; i++) {
            ChunkPos chunk = new ChunkPos((i % 25) * 32 - 400, (i / 25) * 32 - 320);
            HoannaTownPieces.Layout layout = HoannaTownStructure.plan(largeFeatureRandom(LAYOUT_WORLD_SEED, chunk), chunk).layout();
            if (!layout.isValid()) {
                continue;
            }
            valid++;
            pieces += layout.pieces().size();
            towers += layout.count(HoannaTownPieces.Kind.TOWER);
            maxRoads = Math.max(maxRoads, layout.count(HoannaTownPieces.Kind.ROAD));
            maxExtent = Math.max(maxExtent, layout.extent());
            int northSouth = 0;
            for (HoannaTownPieces.Planned piece : layout.pieces()) {
                if (piece.kind() == HoannaTownPieces.Kind.ROAD && piece.facing().getAxis() == Direction.Axis.Z) {
                    northSouth++;
                }
                // 引用范围：结构起点 ±8 区块（包围盒按最大的坑往外扩）
                helper.assertTrue(piece.minX() - reach >= chunk.getMinBlockX() - 128 && piece.maxX() + reach <= chunk.getMaxBlockX() + 128
                                && piece.minZ() - reach >= chunk.getMinBlockZ() - 128 && piece.maxZ() + reach <= chunk.getMaxBlockZ() + 128,
                        "件 " + piece + " 超出起点区块 " + chunk + " ±8 区块");
                if (piece.kind() == HoannaTownPieces.Kind.HOUSE) {
                    helper.assertTrue(piece.floors() >= 1 && HoannaTownStructure.houseCraterRadius(piece.floors()) <= 15F,
                            "房子的坑半径应 ≤ 15：层数 " + piece.floors());
                }
            }
            maxNorthSouthRoads = Math.max(maxNorthSouthRoads, northSouth);
        }
        double meanPieces = pieces / (double) valid;
        double meanTowers = towers / (double) valid;
        helper.assertTrue(valid >= samples * 0.98, "几乎每个候选都该是有效城镇（非道路件 > 2），实际 " + valid + " / " + samples);
        helper.assertTrue(meanPieces >= 20 && meanPieces <= 27, "件数平均应约 23（原作模拟 23.4），实际 " + meanPieces);
        helper.assertTrue(meanTowers >= 2.6 && meanTowers <= 3.9, "塔平均应约 3.2（原作模拟 3.25），实际 " + meanTowers);
        helper.assertTrue(maxRoads <= 8, "道路最多 8 条（起点 4 条 + 南北两条路各分 2 条），实际 " + maxRoads);
        helper.assertTrue(maxNorthSouthRoads <= 2, "D15 照原作：东西向的路不分叉，南北向的路只有起点那两条，实际最多 " + maxNorthSouthRoads);
        helper.assertTrue(maxExtent <= HoannaTownPieces.TOWN_MAX_RADIUS, "所有件离中心应 ≤ 64 格，实际 " + maxExtent);
        helper.assertTrue(HoannaTownStructure.EXPLOSION_RADIUS <= 15F && HoannaTownStructure.houseCraterRadius(9) <= 15F, "坑半径应 ≤ 15");

        ChunkPos chunk = new ChunkPos(12, -7);
        HoannaTownStructure.Plan first = HoannaTownStructure.plan(largeFeatureRandom(LAYOUT_WORLD_SEED, chunk), chunk);
        HoannaTownStructure.Plan second = HoannaTownStructure.plan(largeFeatureRandom(LAYOUT_WORLD_SEED, chunk), chunk);
        helper.assertTrue(first.equals(second), "同一区块、同一种子布局两次应完全相同");
        helper.succeed();
    }

    /**
     * 6a 审查 M5-1：定高每件只取包围盒中心 1 列。以前取四角 + 中心 5 列，每座约 80 列噪声、/locate 平均 235 ms，
     * 探索类指南针（整合包里的 ExplorersCompass）在服务端主线程上等它，每找到一座没生成过的城镇全服卡 0.2~0.36 秒。
     * 用假地面（按坐标算、不碰噪声）记下 createPieces 取了哪些列。
     */
    @GameTest(template = EMPTY)
    public static void townPiecesSampleOneColumnEach(GameTestHelper helper) {
        int samples = 100;
        int towns = 0;
        long columns = 0;
        int startGround = 70;
        for (int i = 0; i < samples; i++) {
            ChunkPos chunk = new ChunkPos((i % 10) * 32 - 160, (i / 10) * 32 - 160);
            HoannaTownStructure.Plan plan = HoannaTownStructure.plan(largeFeatureRandom(LAYOUT_WORLD_SEED, chunk), chunk);
            if (!plan.layout().isValid()) {
                continue;
            }
            towns++;
            List<BlockPos> sampled = new ArrayList<>();
            IntBinaryOperator groundAt = (x, z) -> {
                sampled.add(new BlockPos(x, 0, z));
                return fakeGround(x, z);
            };
            List<HoannaStructurePiece> pieces = HoannaTownStructure.createPieces(plan, startGround, groundAt);
            List<BlockPos> expected = new ArrayList<>();
            for (HoannaTownPieces.Planned planned : plan.layout().pieces()) {
                if (planned.kind() != HoannaTownPieces.Kind.START && planned.kind() != HoannaTownPieces.Kind.ROAD) {
                    BlockPos center = planned.box().getCenter();
                    expected.add(new BlockPos(center.getX(), 0, center.getZ()));
                }
            }
            helper.assertTrue(sampled.equals(expected), "城镇 " + chunk + "：每个非道路件应只取包围盒中心 1 列、按拼件顺序；实际取了 " + sampled.size()
                    + " 列，应 " + expected.size() + " 列（前几列 " + sampled.subList(0, Math.min(6, sampled.size())) + "，应 "
                    + expected.subList(0, Math.min(6, expected.size())) + "）");
            columns += sampled.size();
            for (HoannaStructurePiece piece : pieces) {
                BlockPos center = piece.getBoundingBox().getCenter();
                if (piece instanceof HoannaTownPieces.Start || piece instanceof HoannaTownPieces.Road) {
                    helper.assertTrue(piece.groundY() == startGround, "起点、道路的地面应是起点那一列 " + startGround + "：" + piece + " → " + piece.groundY());
                } else if (piece instanceof HoannaTownPieces.Tower || piece instanceof HoannaTownPieces.House
                        || piece instanceof HoannaTownPieces.Spreader || piece instanceof HoannaTownPieces.Torch) {
                    int ground = fakeGround(center.getX(), center.getZ());
                    helper.assertTrue(piece.groundY() == ground, "件的地面应是包围盒中心那一列 " + ground + "：" + piece + " → " + piece.groundY());
                }
            }
        }
        helper.assertTrue(towns >= samples * 0.9, "几乎每个候选都该是有效城镇，实际 " + towns + " / " + samples);
        double mean = columns / (double) towns;
        helper.assertTrue(mean <= 20, "每座城定高平均应约 15.5 列（非道路件数），实际 " + mean);
        helper.succeed();
    }

    /** 假地面：按坐标算、各列不同（不碰噪声） */
    private static int fakeGround(int x, int z) {
        return 64 + Math.floorMod(x * 7 + z * 13, 23);
    }

    // ------------------------------------------------------------------ 房子

    @GameTest(template = EMPTY)
    public static void houseRuinKeepsGroundFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int x0 = HOUSE_AREA + 4;
        int z0 = HOUSE_AREA + 4;
        level.getChunk(SectionPos.blockToSectionCoord(x0), SectionPos.blockToSectionCoord(z0));
        int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, x0 + 3, z0 + 3);
        int floors = 3;
        int bottom = ground - HoannaTownPieces.HOUSE_FLOOR_Y;
        BoundingBox box = new BoundingBox(x0, bottom, z0, x0 + 6, bottom + HoannaTownPieces.House.height(floors) - 1, z0 + 6);
        HoannaTownPieces.House house = new HoannaTownPieces.House(1, box, Direction.NORTH, 0x5DEECE66DL * 7L, floors, 3);
        house.setGroundY(ground);
        List<HoannaStructurePiece> pieces = new ArrayList<>();
        pieces.add(house);
        pieces.addAll(HoannaTownStructure.houseCraters(house, ground));
        helper.assertTrue(pieces.size() == 4, "3 层的房子应有 3 个坑，实际 " + (pieces.size() - 1));

        BoundingBox region = box.inflatedBy(1);
        for (HoannaStructurePiece piece : pieces) {
            region = BoundingBox.encapsulatingBoxes(List.of(region, piece.getBoundingBox())).orElseThrow();
        }
        Snapshot original = Snapshot.take(level, region);
        int split = x0 + 3;
        BoundingBox west = columns(level, region.minX(), split, region.minZ(), region.maxZ());
        BoundingBox east = columns(level, split + 1, region.maxX(), region.minZ(), region.maxZ());

        // 只给西半：东半一格不动
        process(level, generator, pieces, west);
        Map<BlockPos, BlockState> westOnly = original.changes(level);
        for (BlockPos pos : westOnly.keySet()) {
            helper.assertTrue(pos.getX() <= split, "只给西半 chunkBox 时东半的 " + pos + " 被改了（" + westOnly.get(pos) + "）");
        }
        process(level, generator, pieces, east);
        Map<BlockPos, BlockState> splitResult = original.changes(level);
        original.restore(level);
        process(level, generator, pieces, columns(level, region.minX(), region.maxX(), region.minZ(), region.maxZ()));
        Map<BlockPos, BlockState> wholeResult = original.changes(level);
        helper.assertTrue(splitResult.equals(wholeResult), "分两半盖和一次盖完应逐格相同：分块改了 " + splitResult.size() + " 格，整块 " + wholeResult.size() + " 格");

        Block fortified = ECMaterials.FORTIFIED_STONE.get();
        Block glass = ECMaterials.FORTIFIED_GLASS.get();
        for (int dx = 0; dx <= 6; dx++) {
            for (int dz = 0; dz <= 6; dz++) {
                BlockPos floor = new BlockPos(x0 + dx, ground, z0 + dz);
                helper.assertTrue(level.getBlockState(floor).is(fortified), "一楼楼板应全是强化石：" + floor + " 是 " + level.getBlockState(floor));
                boolean wall = dx == 0 || dx == 6 || dz == 0 || dz == 6;
                for (int dy = 0; dy < 5; dy++) {
                    BlockState state = level.getBlockState(floor.above(dy));
                    if (wall || dy == 4) {
                        helper.assertTrue(state.is(fortified) || state.is(glass), "一楼的墙和天花板不该缺：" + floor.above(dy) + " 是 " + state);
                    }
                }
                boolean corner = (dx == 0 || dx == 6) && (dz == 0 || dz == 6);
                BlockPos below = floor.below();
                if (corner && HoannaStructureBlocks.isRegistered(HoannaStructureBlocks.LEVITATOR_ID)) {
                    helper.assertTrue(level.getBlockState(below).is(HoannaStructureBlocks.levitator().getBlock()), "一楼四角地下应是悬浮器：" + below);
                } else {
                    helper.assertTrue(level.getBlockState(below) == original.get(below), "地下只有四角放悬浮器，别的格不动：" + below);
                }
            }
        }
        int topFloor = house.storeyY(floors);
        int holes = 0;
        for (int dy = 0; dy < 5; dy++) {
            for (int d = 0; d <= 6; d++) {
                for (BlockPos pos : new BlockPos[]{new BlockPos(x0 + d, topFloor + dy, z0), new BlockPos(x0 + d, topFloor + dy, z0 + 6),
                        new BlockPos(x0, topFloor + dy, z0 + d), new BlockPos(x0 + 6, topFloor + dy, z0 + d)}) {
                    if (level.getBlockState(pos).isAir()) {
                        holes++;
                    }
                }
            }
        }
        helper.assertTrue(holes >= 1, "顶层的墙应缺了一些（缺损 " + HoannaTownPieces.House.SHELL_DECAY + " + 弹坑）");
        original.restore(level);
        helper.succeed();
    }

    /**
     * 房子的坑：强化石 / 强化玻璃的有效半径系数 0.5（按抗爆分档是 0.2，半径 3 的坑只挖得掉坑心那一格），只挖二楼楼板（地面 + 5）及以上。
     * F = 1 / 2 的房子（House1 半径 5、House2 半径 4）坑可以落在二楼，挑坑心正好在二楼楼板上的种子：不裁的话一楼天花板（地面 + 4）离坑心 1 格，
     * 系数 0.5 时一定被打穿。盖房子 + 房子的坑以后一楼楼板、墙、天花板一格不缺；坑心和它上、东南西北五格都炸空了。
     * 另在实心强化石里单独挖一个半径 3、下限 = 坑心的房子坑：挖掉 ≥ 6 格（坑心 + 上 + 四周），坑心以下一格不动。
     */
    @GameTest(template = EMPTY)
    public static void houseCratersSpareGroundFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Block fortified = ECMaterials.FORTIFIED_STONE.get();
        Block glass = ECMaterials.FORTIFIED_GLASS.get();
        helper.assertTrue(HoannaTownPieces.Crater.HOUSE_SHELL_FACTOR == 0.5F
                        && HoannaTownPieces.Crater.houseFactor(fortified.defaultBlockState()) == HoannaTownPieces.Crater.HOUSE_SHELL_FACTOR
                        && HoannaTownPieces.Crater.houseFactor(glass.defaultBlockState()) == HoannaTownPieces.Crater.HOUSE_SHELL_FACTOR
                        && HoannaTownPieces.Crater.houseFactor(Blocks.STONE.defaultBlockState()) == StructureRuin.K_STONE
                        && HoannaTownPieces.Crater.houseFactor(Blocks.DIRT.defaultBlockState()) == StructureRuin.K_SOFT
                        && HoannaTownPieces.Crater.houseFactor(Blocks.OBSIDIAN.defaultBlockState()) == 0F,
                "房子的坑：强化石 / 强化玻璃 0.5，别的方块照抗爆分档");
        helper.assertTrue(HoannaTownPieces.House.SHELL_DECAY == 0.25F, "墙和楼板的缺损应是 0.25，实际 " + HoannaTownPieces.House.SHELL_DECAY);
        helper.assertFalse(new HoannaTownPieces.Crater(0, BlockPos.ZERO, 15F, 1L).isHouseCrater(), "爆炸件的坑不是房子的坑");

        int[][] kinds = {{1, 5}, {2, 4}};   // {F, 半径}：House1、House2
        for (int k = 0; k < kinds.length; k++) {
            int floors = kinds[k][0];
            int radius = kinds[k][1];
            int far = radius * 2;
            int x0 = HOUSE_AREA + 48 + k * 32;
            int z0 = HOUSE_AREA + 4;
            level.getChunk(SectionPos.blockToSectionCoord(x0), SectionPos.blockToSectionCoord(z0));
            int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, x0 + radius, z0 + radius);
            int bottom = ground - HoannaTownPieces.HOUSE_FLOOR_Y;
            BoundingBox box = new BoundingBox(x0, bottom, z0, x0 + far, bottom + HoannaTownPieces.House.height(floors) - 1, z0 + far);
            HoannaTownPieces.House house = null;
            List<HoannaTownPieces.Crater> craters = List.of();
            BlockPos low = null;
            for (long i = 1; i <= 400 && low == null; i++) {
                house = new HoannaTownPieces.House(1, box, Direction.NORTH, i * SEED_STEP, floors, radius);
                craters = HoannaTownStructure.houseCraters(house, ground);
                for (HoannaTownPieces.Crater crater : craters) {
                    if (crater.center().getY() == ground + HoannaTownPieces.STOREY_HEIGHT) {
                        low = crater.center();
                        break;
                    }
                }
            }
            if (low == null) {
                helper.fail("F = " + floors + "：400 个种子里没有坑心落在二楼楼板上的房子");
                return;
            }
            house.setGroundY(ground);
            for (HoannaTownPieces.Crater crater : craters) {
                helper.assertTrue(crater.isHouseCrater() && crater.floorY() == ground + HoannaTownPieces.STOREY_HEIGHT,
                        "F = " + floors + " 的坑应只挖二楼楼板（地面 + 5 = " + (ground + HoannaTownPieces.STOREY_HEIGHT) + "）及以上，实际下限 " + crater.floorY());
            }
            List<HoannaStructurePiece> pieces = new ArrayList<>();
            pieces.add(house);
            pieces.addAll(craters);
            BoundingBox region = box.inflatedBy(1);
            for (HoannaStructurePiece piece : pieces) {
                region = BoundingBox.encapsulatingBoxes(List.of(region, piece.getBoundingBox())).orElseThrow();
            }
            Snapshot original = Snapshot.take(level, region);
            try {
                process(level, generator, pieces, columns(level, region.minX(), region.maxX(), region.minZ(), region.maxZ()));
                for (int dx = 0; dx <= far; dx++) {
                    for (int dz = 0; dz <= far; dz++) {
                        BlockPos floor = new BlockPos(x0 + dx, ground, z0 + dz);
                        helper.assertTrue(level.getBlockState(floor).is(fortified), "F = " + floors + "：一楼楼板应全是强化石：" + floor + " 是 " + level.getBlockState(floor));
                        boolean wall = dx == 0 || dx == far || dz == 0 || dz == far;
                        for (int dy = 1; dy < HoannaTownPieces.STOREY_HEIGHT; dy++) {
                            BlockState state = level.getBlockState(floor.above(dy));
                            if (wall || dy == HoannaTownPieces.STOREY_HEIGHT - 1) {
                                helper.assertTrue(state.is(fortified) || state.is(glass),
                                        "F = " + floors + "：一楼的墙和天花板不该被坑打穿（坑心 " + low + "）：" + floor.above(dy) + " 是 " + state);
                            }
                        }
                    }
                }
                for (BlockPos pos : new BlockPos[]{low, low.above(), low.north(), low.south(), low.east(), low.west()}) {
                    helper.assertTrue(level.getBlockState(pos).isAir(),
                            "F = " + floors + "：坑心 " + low + " 和上、四周应炸空（强化石系数 0.5，有效半径 ≥ 1.27）：" + pos + " 是 " + level.getBlockState(pos));
                }
            } finally {
                original.restore(level);
            }
        }

        // 实心强化石里单独挖一个房子坑，下限 = 坑心
        int cx = HOUSE_AREA + 120;
        int cz = HOUSE_AREA + 8;
        level.getChunk(SectionPos.blockToSectionCoord(cx), SectionPos.blockToSectionCoord(cz));
        int cy = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz) + 6;
        BlockPos center = new BlockPos(cx, cy, cz);
        BoundingBox cube = new BoundingBox(cx - 3, cy - 3, cz - 3, cx + 3, cy + 3, cz + 3);
        Snapshot before = Snapshot.take(level, cube);
        try {
            for (BlockPos pos : BlockPos.betweenClosed(cube.minX(), cube.minY(), cube.minZ(), cube.maxX(), cube.maxY(), cube.maxZ())) {
                level.setBlock(pos, fortified.defaultBlockState(), QUIET);
            }
            HoannaTownPieces.Crater crater = new HoannaTownPieces.Crater(0, center, 3F, 0x5DEECE66DL * SEED_STEP, cy);
            BoundingBox bounds = crater.getBoundingBox();
            process(level, generator, List.<HoannaStructurePiece>of(crater), columns(level, bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ()));
            int carved = 0;
            for (BlockPos pos : BlockPos.betweenClosed(cube.minX(), cube.minY(), cube.minZ(), cube.maxX(), cube.maxY(), cube.maxZ())) {
                if (level.getBlockState(pos).isAir()) {
                    carved++;
                    helper.assertTrue(pos.getY() >= cy, "房子坑的下限以下不该挖：" + pos.immutable());
                }
            }
            helper.assertTrue(carved >= 6, "半径 3 的房子坑在强化石里应挖掉 ≥ 6 格（有效半径 ≥ 1.27；按 0.2 只有坑心 1 格），实际 " + carved);
            for (BlockPos pos : new BlockPos[]{center, center.above(), center.north(), center.south(), center.east(), center.west()}) {
                helper.assertTrue(level.getBlockState(pos).isAir(), "坑心和上、四周应炸空：" + pos);
            }
        } finally {
            before.restore(level);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 道路

    @GameTest(template = EMPTY_LARGE)
    public static void townRoadFollowsSurface(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 路占 x 1~5、z 0~9；每两排一个台阶：草 y2、泥土 y3、沙 y4、石头 y5、水 y3、草上长着花 y2
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 9; z++) {
                BlockState top;
                int topY;
                if (x == 0 || x == 6) {
                    top = Blocks.GRASS_BLOCK.defaultBlockState();
                    topY = 2;
                } else if (z <= 1) {
                    top = Blocks.GRASS_BLOCK.defaultBlockState();
                    topY = 2;
                } else if (z <= 3) {
                    top = Blocks.DIRT.defaultBlockState();
                    topY = 3;
                } else if (z <= 5) {
                    top = Blocks.SAND.defaultBlockState();
                    topY = 4;
                } else if (z <= 7) {
                    top = stone;
                    topY = 5;
                } else if (z == 8) {
                    top = Blocks.WATER.defaultBlockState();
                    topY = 3;
                } else {
                    top = Blocks.GRASS_BLOCK.defaultBlockState();
                    topY = 2;
                }
                for (int y = 1; y < topY; y++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), stone, QUIET);
                }
                level.setBlock(helper.absolutePos(new BlockPos(x, topY, z)), top, QUIET);
                if (z == 9 && x >= 1 && x <= 5) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, 3, z)), Blocks.POPPY.defaultBlockState(), QUIET);
                }
            }
        }
        BoundingBox roadBox = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(1, 1, 0)), helper.absolutePos(new BlockPos(5, 5, 9)));
        HoannaTownPieces.Road road = new HoannaTownPieces.Road(0, roadBox, Direction.SOUTH, 7L);
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        BlockPos far = helper.absolutePos(new BlockPos(10, 0, 10));
        BoundingBox area = columns(level, Math.min(corner.getX(), far.getX()), Math.max(corner.getX(), far.getX()),
                Math.min(corner.getZ(), far.getZ()), Math.max(corner.getZ(), far.getZ()));
        road.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(0L), area,
                new ChunkPos(corner), corner);

        Block concrete = HoannaStructureBlocks.concrete().getBlock();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 9; z++) {
                boolean onRoad = x >= 1 && x <= 5;
                int topY = !onRoad || z <= 1 || z == 9 ? 2 : z <= 3 ? 3 : z <= 5 ? 4 : z <= 7 ? 5 : 3;
                BlockState state = level.getBlockState(helper.absolutePos(new BlockPos(x, topY, z)));
                if (!onRoad) {
                    helper.assertTrue(state.is(Blocks.GRASS_BLOCK), "路外面的列不该动：(" + x + ", " + z + ") 是 " + state);
                } else if (z == 6 || z == 7) {
                    helper.assertTrue(state.is(Blocks.STONE), "石头地表不铺路：(" + x + ", " + z + ") 是 " + state);
                } else {
                    helper.assertTrue(state.is(concrete), "草 / 泥土 / 沙 / 水的地表应换成混凝土：(" + x + ", " + topY + ", " + z + ") 是 " + state);
                }
                BlockState above = level.getBlockState(helper.absolutePos(new BlockPos(x, topY + 1, z)));
                helper.assertTrue(above.isAir(), "路面上方应是空的（花要清掉）：(" + x + ", " + (topY + 1) + ", " + z + ") 是 " + above);
                if (onRoad && topY > 2) {
                    helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, topY - 1, z))).is(Blocks.STONE),
                            "只换最上面一格：(" + x + ", " + (topY - 1) + ", " + z + ")");
                }
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 存档

    @GameTest(template = EMPTY)
    public static void piecesRoundTripNbt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
        BoundingBox box = new BoundingBox(100, 60, 200, 114, 80, 214);
        List<HoannaStructurePiece> pieces = List.of(
                new HoannaTownPieces.Start(0, box, Direction.WEST, 11L),
                new HoannaTownPieces.Road(0, box, Direction.NORTH, 12L),
                new HoannaTownPieces.Tower(1, box, Direction.EAST, 13L),
                new HoannaTownPieces.House(1, box, Direction.SOUTH, 14L, 3, 7),
                new HoannaTownPieces.Spreader(1, box, Direction.NORTH, 15L),
                new HoannaTownPieces.Torch(2, box, Direction.WEST, 16L),
                new HoannaTownPieces.Crater(1, new BlockPos(107, 70, 207), 15F, -17L));
        Set<String> types = new HashSet<>();
        int ground = 60;
        for (HoannaStructurePiece piece : pieces) {
            piece.setGroundY(ground++);
            CompoundTag tag = piece.createTag(context);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            helper.assertTrue(id != null && BuiltInRegistries.STRUCTURE_PIECE.containsKey(id), "拼件类型没注册：" + tag.getString("id"));
            types.add(id.getPath());
            StructurePieceType type = BuiltInRegistries.STRUCTURE_PIECE.get(id);
            StructurePiece copy = type.load(context, tag);
            helper.assertTrue(copy.getClass() == piece.getClass(), id + " 读回来的类不对：" + copy.getClass());
            helper.assertTrue(copy.getBoundingBox().equals(piece.getBoundingBox()) && copy.getOrientation() == piece.getOrientation()
                    && copy.getGenDepth() == piece.getGenDepth(), id + " 的包围盒 / 朝向 / 深度读回不同");
            HoannaStructurePiece hoannaCopy = (HoannaStructurePiece) copy;
            helper.assertTrue(hoannaCopy.seed() == piece.seed() && hoannaCopy.groundY() == piece.groundY(), id + " 的种子 / 地面读回不同");
            helper.assertTrue(copy.createTag(context).equals(tag), id + " 再存一次 NBT 应完全相同：\n" + tag + "\n" + copy.createTag(context));
            if (copy instanceof HoannaTownPieces.House house) {
                helper.assertTrue(house.floors() == 3 && house.radius() == 7, "房子的层数 / 半径读回不同");
            }
            if (copy instanceof HoannaTownPieces.Crater crater) {
                helper.assertTrue(crater.center().equals(new BlockPos(107, 70, 207)) && crater.radius() == 15F
                        && crater.getBoundingBox().equals(StructureRuin.craterBounds(crater.center(), 15F)), "弹坑的中心 / 半径 / 包围盒读回不同");
            }
        }
        helper.assertTrue(types.equals(ECHoannaStructures.PLANNED_TOWN_PIECES), "7 种城镇拼件都应测到：" + types);

        // 房子的坑多存一个挖掘下限；爆炸件的坑不存（旧存档里的坑也没有），读回来还是爆炸件的坑
        HoannaTownPieces.Crater houseCrater = new HoannaTownPieces.Crater(1, new BlockPos(107, 75, 207), 4F, -18L, 70);
        CompoundTag houseTag = houseCrater.createTag(context);
        HoannaTownPieces.Crater houseCopy = new HoannaTownPieces.Crater(houseTag);
        helper.assertTrue(houseCopy.isHouseCrater() && houseCopy.floorY() == 70 && houseCopy.createTag(context).equals(houseTag),
                "房子的坑的挖掘下限读回不同：" + houseTag);
        CompoundTag explosionTag = new HoannaTownPieces.Crater(1, new BlockPos(107, 70, 207), 15F, -17L).createTag(context);
        helper.assertTrue(!explosionTag.contains("Floor") && !new HoannaTownPieces.Crater(explosionTag).isHouseCrater(),
                "爆炸件的坑不存挖掘下限、读回来不是房子的坑：" + explosionTag);

        // 整座城镇的结构起点：存档再读回
        Structure structure = town(level);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        ChunkPos chunk = new ChunkPos(1500, 1500);
        long seed = 12345L * SEED_STEP;
        StructureStart start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), seed, chunk, 0, level, biome -> true);
        helper.assertTrue(start.isValid(), "超平坦上应能布出一座城镇");
        CompoundTag startTag = start.createTag(context, chunk);
        StructureStart loaded = StructureStart.loadStaticStart(context, startTag, seed);
        helper.assertTrue(loaded != null && loaded.getPieces().size() == start.getPieces().size(), "结构起点读回来的件数不对");
        for (int i = 0; i < start.getPieces().size(); i++) {
            helper.assertTrue(loaded.getPieces().get(i).createTag(context).equals(start.getPieces().get(i).createTag(context)),
                    "第 " + i + " 件读回不同：" + start.getPieces().get(i));
        }
        helper.assertTrue(loaded.getBoundingBox().equals(start.getBoundingBox()), "结构包围盒读回不同");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 端到端

    @GameTest(template = EMPTY, batch = PLACE_BATCH, timeoutTicks = 600)
    public static void placeTownEndToEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Structure structure = town(level);
        long seed = seedWithTower(TOWN_CHUNK);
        helper.assertTrue(seed != 0L, "200 个候选种子里没有带塔的城镇");
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        // 照 PlaceCommand.placeStructure：generate → 包围盒涉及的区块逐个 placeInChunk（世界种子换成挑过的固定种子，/place 用的世界种子每次随机）
        StructureStart start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), seed, TOWN_CHUNK, 0, level, biome -> true);
        helper.assertTrue(start.isValid(), "放置失败：结构起点无效");
        BoundingBox bounds = start.getBoundingBox();
        helper.assertTrue(bounds.minX() >= TOWN_CHUNK.getMinBlockX() - 128 && bounds.maxX() <= TOWN_CHUNK.getMaxBlockX() + 128
                        && bounds.minZ() >= TOWN_CHUNK.getMinBlockZ() - 128 && bounds.maxZ() <= TOWN_CHUNK.getMaxBlockZ() + 128,
                "结构包围盒 " + bounds + " 超出起点区块 ±8 区块（超出的部分永远生成不出来）");
        int towers = 0;
        int centerX = TOWN_CHUNK.getMinBlockX() + 4;
        int centerZ = TOWN_CHUNK.getMinBlockZ() + 4;
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof HoannaTownPieces.Tower) {
                towers++;
            }
            if (!(piece instanceof HoannaTownPieces.Crater)) {
                BoundingBox box = piece.getBoundingBox();
                helper.assertTrue(Math.max(Math.max(Math.abs(box.minX() - centerX), Math.abs(box.maxX() - centerX)),
                                Math.max(Math.abs(box.minZ() - centerZ), Math.abs(box.maxZ() - centerZ))) <= HoannaTownPieces.TOWN_MAX_RADIUS,
                        "件 " + piece + " 离中心超过 64 格");
            }
        }
        helper.assertTrue(towers >= 1, "挑的种子应有塔");

        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(bounds.minX()), SectionPos.blockToSectionCoord(bounds.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(bounds.maxX()), SectionPos.blockToSectionCoord(bounds.maxZ()));
        List<ChunkPos> chunks = new ArrayList<>();
        ChunkPos.rangeClosed(min, max).forEach(chunks::add);
        for (ChunkPos chunk : chunks) {
            level.getChunk(chunk.x, chunk.z);
        }
        BoundingBox region = new BoundingBox(bounds.minX() - 1, level.getMinBuildHeight(), bounds.minZ() - 1,
                bounds.maxX() + 1, Math.min(bounds.maxY() + 1, level.getMaxBuildHeight() - 1), bounds.maxZ() + 1);
        resetToFlatLayers(level, region);
        Snapshot original = Snapshot.take(level, region);

        for (ChunkPos chunk : chunks) {
            placeInChunk(level, generator, start, chunk);
        }
        int chests = countChests(level, TOWN_CHUNK, 5, HoannaLoot.TOWN_TOWER);
        helper.assertTrue(chests == towers * 4, "每座塔顶应有 4 个 chests/town_tower 箱子：塔 " + towers + "，箱子 " + chests);
        Map<BlockPos, BlockState> forward = original.changes(level);
        helper.assertTrue(forward.size() > 1000, "一座城镇应改动不少方块，实际 " + forward.size());
        for (BlockPos pos : forward.keySet()) {
            helper.assertTrue(pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX() && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ(),
                    "改动的 " + pos + " 在结构包围盒 " + bounds + " 外面");
        }

        original.restore(level);
        for (int i = chunks.size() - 1; i >= 0; i--) {
            placeInChunk(level, generator, start, chunks.get(i));
        }
        Map<BlockPos, Block> reverse = blocks(original.changes(level));
        Map<BlockPos, Block> forwardBlocks = blocks(forward);
        // 比方块种类：完整区块上（/place）栅栏连接由写方块的先后决定（真生成时是区块后处理统一算），不算结构的差别
        helper.assertTrue(reverse.equals(forwardBlocks), "按区块正序、倒序放的结果应逐格相同：正序改 " + forwardBlocks.size() + " 格，倒序改 " + reverse.size() + " 格"
                + firstDifference(forwardBlocks, reverse));
        original.restore(level);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 工具

    private static Structure town(ServerLevel level) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(TOWN);
        if (!(structure instanceof HoannaTownStructure)) {
            throw new IllegalStateException("结构 essentialcraft5:hoanna_town 没加载或类型不对：" + structure);
        }
        return structure;
    }

    private static boolean hasTownSet(List<Holder<StructureSet>> sets) {
        return sets.stream().anyMatch(set -> set.is(TOWN_SET));
    }

    /** 和结构生成用的随机一样：{@code setLargeFeatureSeed(种子, 区块 x, 区块 z)}（Structure.GenerationContext.makeRandom） */
    private static WorldgenRandom largeFeatureRandom(long seed, ChunkPos chunk) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunk.x, chunk.z);
        return random;
    }

    /** 挑一个在这个区块布出来有塔的世界种子；找不到返回 0 */
    private static long seedWithTower(ChunkPos chunk) {
        for (long i = 1; i <= 200; i++) {
            long seed = i * SEED_STEP;
            HoannaTownPieces.Layout layout = HoannaTownStructure.plan(largeFeatureRandom(seed, chunk), chunk).layout();
            if (layout.isValid() && layout.count(HoannaTownPieces.Kind.TOWER) >= 1) {
                return seed;
            }
        }
        return 0L;
    }

    private static StructureStart generate(ServerLevel level, Structure structure, HoannaTestWorld.Resolved hoanna, ChunkPos chunk,
                                           LevelHeightAccessor height) {
        return structure.generate(level.registryAccess(), hoanna.generator(), hoanna.biomeSource(), hoanna.randomState(), level.getStructureManager(),
                hoanna.seed(), chunk, 0, height, structure.biomes()::contains);
    }

    /** x、z 范围内整个高度的 chunkBox（同 postProcess 拿到的区块范围） */
    private static BoundingBox columns(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        return new BoundingBox(minX, level.getMinBuildHeight(), minZ, maxX, level.getMaxBuildHeight() - 1, maxZ);
    }

    /** 照 StructureStart.placeInChunk：和 box 相交的件按表里的顺序 postProcess */
    private static void process(ServerLevel level, ChunkGenerator generator, List<HoannaStructurePiece> pieces, BoundingBox box) {
        BlockPos pivot = new BlockPos(box.minX(), 0, box.minZ());
        for (HoannaStructurePiece piece : pieces) {
            if (piece.getBoundingBox().intersects(box)) {
                piece.postProcess(level, level.structureManager(), generator, RandomSource.create(0L), box, new ChunkPos(pivot), pivot);
            }
        }
    }

    /**
     * 把 region 换回超平坦的原样（只有地层，上面全是空气）。GameTest 服的主世界是开着结构生成的超平坦（run-gametest/server.properties），
     * 世界种子每次随机：有的种子会在这片空地上生成村庄，城镇贴着包围盒放下去时，挨着的村庄方块（玻璃板、栅栏、楼梯、火把）跟着更新形状，
     * 包围盒外面那一圈就有了改动，这条测试就偶尔挂在「改动在结构包围盒外面」。先清成地层，快照和比较只看城镇自己。
     * 用 QUIET 写（不更新邻居形状），区域外面的方块不碰。主世界不是超平坦（不该发生）时什么都不做。
     */
    private static void resetToFlatLayers(ServerLevel level, BoundingBox region) {
        if (!(level.getChunkSource().getGenerator() instanceof FlatLevelSource flat)) {
            return;
        }
        List<BlockState> layers = flat.settings().getLayers();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = region.minY(); y <= region.maxY(); y++) {
            int index = y - level.getMinBuildHeight();
            BlockState layer = index >= 0 && index < layers.size() && layers.get(index) != null
                    ? layers.get(index) : Blocks.AIR.defaultBlockState();
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    if (level.getBlockState(pos.set(x, y, z)) != layer) {
                        level.setBlock(pos, layer, QUIET);
                    }
                }
            }
        }
    }

    /** 照 PlaceCommand.placeStructure 的每区块那一步 */
    private static void placeInChunk(ServerLevel level, ChunkGenerator generator, StructureStart start, ChunkPos chunk) {
        start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
                new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxBuildHeight(),
                        chunk.getMaxBlockZ()), chunk);
    }

    private static int countChests(ServerLevel level, ChunkPos center, int radius, ResourceLocation lootTable) {
        int count = 0;
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                LevelChunk chunk = level.getChunk(x, z);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof ChestBlockEntity
                            && lootTable.toString().equals(blockEntity.saveWithoutMetadata().getString("LootTable"))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static Map<BlockPos, Block> blocks(Map<BlockPos, BlockState> states) {
        Map<BlockPos, Block> blocks = new LinkedHashMap<>();
        states.forEach((pos, state) -> blocks.put(pos, state.getBlock()));
        return blocks;
    }

    private static String firstDifference(Map<BlockPos, Block> a, Map<BlockPos, Block> b) {
        for (Map.Entry<BlockPos, Block> entry : a.entrySet()) {
            if (!Objects.equals(entry.getValue(), b.get(entry.getKey()))) {
                return "；例：" + entry.getKey() + " 正序 " + entry.getValue() + "，倒序 " + b.get(entry.getKey());
            }
        }
        for (Map.Entry<BlockPos, Block> entry : b.entrySet()) {
            if (!a.containsKey(entry.getKey())) {
                return "；例：" + entry.getKey() + " 只有倒序改成了 " + entry.getValue();
            }
        }
        return "";
    }

    /** 一块区域原来的方块：之后可以列出改动、原样还原（带方块实体的格子还原成原来的方块时方块实体也跟着没了） */
    private static final class Snapshot {
        private final BoundingBox region;
        private final BlockState[] states;

        private Snapshot(BoundingBox region, BlockState[] states) {
            this.region = region;
            this.states = states;
        }

        static Snapshot take(ServerLevel level, BoundingBox region) {
            BlockState[] states = new BlockState[region.getXSpan() * region.getYSpan() * region.getZSpan()];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int i = 0;
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    for (int x = region.minX(); x <= region.maxX(); x++) {
                        states[i++] = level.getBlockState(pos.set(x, y, z));
                    }
                }
            }
            return new Snapshot(region, states);
        }

        BlockState get(BlockPos pos) {
            return states[((pos.getY() - region.minY()) * region.getZSpan() + (pos.getZ() - region.minZ())) * region.getXSpan()
                    + (pos.getX() - region.minX())];
        }

        /** 和原来不一样的格子 → 现在的方块 */
        Map<BlockPos, BlockState> changes(ServerLevel level) {
            Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int i = 0;
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    for (int x = region.minX(); x <= region.maxX(); x++) {
                        BlockState now = level.getBlockState(pos.set(x, y, z));
                        if (now != states[i++]) {
                            changes.put(pos.immutable(), now);
                        }
                    }
                }
            }
            return changes;
        }

        void restore(ServerLevel level) {
            for (BlockPos pos : changes(level).keySet()) {
                level.setBlock(pos, get(pos), QUIET);
            }
        }
    }
}
