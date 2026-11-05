package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.loot.HoannaLoot;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECHoannaStructures;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureRuin;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 第六批框架 F6b（batch6a §6.2 第 7 条）：海纳尔结构拼件基类、确定性弹坑与缺损、结构注册名。
 * <ul>
 *   <li>{@code craterIsChunkSeamless}：同一个坑整块挖、和分两个 chunkBox（倒着顺序）各挖一半，结果逐格相同；黑曜石、水、箱子不动，
 *       挨着水的格子不挖，海平面下不挖，只写 chunkBox 里，挖完没有悬空的沙和花。</li>
 *   <li>{@code ruinDecayIsChunkSeamless}：按楼层缺损同样跨 chunkBox 一致、只动匹配的方块、概率大致对。</li>
 *   <li>{@code pieceBaseNbtRoundTrip}：拼件基类字段（包围盒、朝向、深度、种子、地面高度、子类字段）createTag 再读回相同；位置哈希只看坐标和种子；
 *       已登记的结构 / 拼件类型都注册进了原版注册表、名字都在计划表里（名字写进区块存档，上线后不能改）。</li>
 *   <li>{@code pieceShapeUpdates}：EC 栅栏放下后按邻居连上（完整区块当场算），生成中的区块登记后处理；fillDown 穿过草丛、有深度上限；
 *       HoannaStructureBlocks / HoannaLoot 的 id。</li>
 * </ul>
 * GameTest 服的世界是超平坦、区块都是完整区块，所以这里挖坑走的是「当场重算形状」那条路；生成中区块的后处理只能用手建的 ProtoChunk 测登记。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaStructureFrameworkTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 测试写方块用的 flag：不连带更新邻居，摆出来的就是想要的状态 */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private HoannaStructureFrameworkTests() {
    }

    // ------------------------------------------------------------------ 弹坑

    @GameTest(template = EMPTY_LARGE)
    public static void craterIsChunkSeamless(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox region = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(10, 7, 10)));
        // 地形：y1~3 石头、y4~5 泥土、y6 草方块；坑心在 (5, 6, 5) 的地表
        for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, 10, 6, 10)) {
            BlockState state = rel.getY() <= 3 ? Blocks.STONE.defaultBlockState()
                    : rel.getY() <= 5 ? Blocks.DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            level.setBlock(helper.absolutePos(rel), state, QUIET);
        }
        BlockPos obsidian = helper.absolutePos(new BlockPos(6, 5, 5));
        BlockPos fortified = helper.absolutePos(new BlockPos(4, 5, 5));
        BlockPos water = helper.absolutePos(new BlockPos(3, 4, 7));
        BlockPos chest = helper.absolutePos(new BlockPos(7, 5, 5));
        level.setBlock(obsidian, Blocks.OBSIDIAN.defaultBlockState(), QUIET);
        level.setBlock(fortified, ECMaterials.FORTIFIED_STONE.get().defaultBlockState(), QUIET);
        level.setBlock(water, Blocks.WATER.defaultBlockState(), QUIET);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), QUIET);
        // 坑沿上：水平距坑心 √20 的 8 格放沙（压着底下的草方块），水平距 5 的 12 格放花
        int[][] sand = {{1, 3}, {3, 1}, {7, 1}, {9, 3}, {1, 7}, {3, 9}, {7, 9}, {9, 7}};
        int[][] flowers = {{0, 5}, {10, 5}, {5, 0}, {5, 10}, {1, 2}, {2, 1}, {8, 1}, {9, 2}, {1, 8}, {2, 9}, {8, 9}, {9, 8}};
        for (int[] xz : sand) {
            level.setBlock(helper.absolutePos(new BlockPos(xz[0], 7, xz[1])), Blocks.SAND.defaultBlockState(), QUIET);
        }
        for (int[] xz : flowers) {
            level.setBlock(helper.absolutePos(new BlockPos(xz[0], 7, xz[1])), Blocks.POPPY.defaultBlockState(), QUIET);
        }

        helper.assertTrue(StructureRuin.hardnessFactor(Blocks.DIRT.defaultBlockState()) == StructureRuin.K_SOFT
                && StructureRuin.hardnessFactor(Blocks.STONE.defaultBlockState()) == StructureRuin.K_STONE
                && StructureRuin.hardnessFactor(ECMaterials.FORTIFIED_STONE.get().defaultBlockState()) == StructureRuin.K_HARD
                && StructureRuin.hardnessFactor(Blocks.OBSIDIAN.defaultBlockState()) == 0F
                && StructureRuin.hardnessFactor(Blocks.WATER.defaultBlockState()) == 0F, "抗爆分档应是 泥土 0.6 / 石头 0.35 / 强化石 0.2 / 黑曜石、水 0");

        Map<BlockPos, BlockState> original = snapshot(level, region);
        BlockPos center = helper.absolutePos(new BlockPos(5, 6, 5));
        int seaLevel = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        long seed = 0x9E3779B97F4A7C15L * 11L;
        float radius = 8F;

        int whole = StructureRuin.carveCrater(level, region, center, radius, seed, seaLevel);
        Map<BlockPos, BlockState> afterWhole = snapshot(level, region);
        restore(level, original);
        helper.assertTrue(snapshot(level, region).equals(original), "还原后应和挖之前一样");

        // 分两块挖，先东半（x > 坑心）后西半——和整块挖的遍历顺序反过来
        BoundingBox east = new BoundingBox(center.getX() + 1, region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ());
        BoundingBox west = new BoundingBox(region.minX(), region.minY(), region.minZ(), center.getX(), region.maxY(), region.maxZ());
        int eastCount = StructureRuin.carveCrater(level, east, center, radius, seed, seaLevel);
        for (BlockPos pos : BlockPos.betweenClosed(west.minX(), west.minY(), west.minZ(), west.maxX(), west.maxY(), west.maxZ())) {
            helper.assertTrue(level.getBlockState(pos) == original.get(pos), "只挖东半时西半不该有变化：" + pos.immutable());
        }
        int westCount = StructureRuin.carveCrater(level, west, center, radius, seed, seaLevel);
        Map<BlockPos, BlockState> afterSplit = snapshot(level, region);
        helper.assertTrue(whole == eastCount + westCount, "分两块挖的格数应等于整块挖：整块 " + whole + "，东 " + eastCount + " + 西 " + westCount);
        for (Map.Entry<BlockPos, BlockState> entry : afterWhole.entrySet()) {
            BlockState split = afterSplit.get(entry.getKey());
            helper.assertTrue(split == entry.getValue(), "分两块挖和整块挖在 " + entry.getKey() + " 不一样：整块 " + entry.getValue() + "，分块 " + split);
        }

        helper.assertTrue(whole >= 30, "半径 8 的坑应挖掉不少格，实际 " + whole);
        helper.assertTrue(afterWhole.get(obsidian).is(Blocks.OBSIDIAN), "黑曜石（抗爆 ≥ 20）不该被挖");
        helper.assertTrue(afterWhole.get(chest).is(Blocks.CHEST) && level.getBlockEntity(chest) != null, "带方块实体的箱子不该被挖");
        helper.assertTrue(afterWhole.get(water).is(Blocks.WATER), "水不该被挖");
        for (Direction direction : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos next = water.relative(direction);
            helper.assertFalse(afterWhole.get(next).isAir(), "挨着水的 " + next + " 不该被挖（水会流进坑里）");
        }
        int carvedCells = 0;
        BoundingBox bounds = StructureRuin.craterBounds(center, radius);
        for (Map.Entry<BlockPos, BlockState> entry : afterWhole.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState before = original.get(pos);
            if (entry.getValue() != before) {
                helper.assertTrue(bounds.isInside(pos), "改动的 " + pos + " 超出了 craterBounds " + bounds + "（拼件包围盒会截断坑）");
            }
            if (!entry.getValue().isAir() || before.isAir()) {
                continue;
            }
            if (before.is(Blocks.POPPY)) {
                continue; // 花没了依托被后处理拆掉，或者本身被挖
            }
            carvedCells++;
            helper.assertTrue(pos.getY() >= seaLevel, "海平面以下的 " + pos + " 不该被挖");
            BlockState above = afterWhole.get(pos.above());
            if (above != null) {
                helper.assertFalse(above.getBlock() instanceof FallingBlock, "挖空的 " + pos + " 上面还压着会掉的 " + above + "（悬空的沙）");
                helper.assertFalse(above.is(Blocks.POPPY), "挖空的 " + pos + " 上面还留着花（该被形状后处理拆掉）");
            }
        }
        helper.assertTrue(carvedCells > 0, "应至少挖掉一格方块");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 缺损

    @GameTest(template = EMPTY_LARGE)
    public static void ruinDecayIsChunkSeamless(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox region = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(9, 6, 9)));
        // 一座实心的「楼」：强化石墙 + 里面圆石碎石，顶上一层沙，中间一个箱子
        for (BlockPos pos : BlockPos.betweenClosed(region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ())) {
            boolean wall = pos.getX() == region.minX() || pos.getX() == region.maxX() || pos.getZ() == region.minZ() || pos.getZ() == region.maxZ();
            BlockState state = pos.getY() == region.maxY() ? Blocks.SAND.defaultBlockState()
                    : wall ? ECMaterials.FORTIFIED_STONE.get().defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
            level.setBlock(pos, state, QUIET);
        }
        BlockPos chest = helper.absolutePos(new BlockPos(5, 3, 5));
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), QUIET);
        Map<BlockPos, BlockState> original = snapshot(level, region);

        long seed = 0x9E3779B97F4A7C15L * 23L;
        Block fortified = ECMaterials.FORTIFIED_STONE.get();
        StructureRuin.DecayChance chance = state -> state.is(Blocks.COBBLESTONE) ? 0.6F : state.is(fortified) ? 0.35F : 0F;
        int whole = StructureRuin.decay(level, region, region, seed, chance);
        Map<BlockPos, BlockState> afterWhole = snapshot(level, region);
        restore(level, original);

        int split = helper.absolutePos(new BlockPos(4, 0, 0)).getX();
        BoundingBox east = new BoundingBox(split + 1, region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ());
        BoundingBox west = new BoundingBox(region.minX(), region.minY(), region.minZ(), split, region.maxY(), region.maxZ());
        int eastCount = StructureRuin.decay(level, east, region, seed, chance);
        int westCount = StructureRuin.decay(level, west, region, seed, chance);
        Map<BlockPos, BlockState> afterSplit = snapshot(level, region);
        helper.assertTrue(whole == eastCount + westCount, "分块缺损的格数应等于整块：整块 " + whole + "，分块 " + eastCount + " + " + westCount);
        helper.assertTrue(afterSplit.equals(afterWhole), "分两块缺损和整块缺损结果应逐格相同");

        int cobble = 0;
        int cobbleGone = 0;
        for (Map.Entry<BlockPos, BlockState> entry : original.entrySet()) {
            BlockState before = entry.getValue();
            BlockState after = afterWhole.get(entry.getKey());
            if (before.is(Blocks.SAND)) {
                helper.assertTrue(after.is(Blocks.SAND), "沙不在缺损名单里，不该被挖：" + entry.getKey());
            }
            if (before.is(Blocks.COBBLESTONE) && !afterWhole.get(entry.getKey().above()).is(Blocks.SAND)) {
                cobble++;
                if (after.isAir()) {
                    cobbleGone++;
                }
            }
            if (after.isAir() && !before.isAir()) {
                BlockState above = afterWhole.get(entry.getKey().above());
                helper.assertFalse(above != null && above.getBlock() instanceof FallingBlock, "缺掉的 " + entry.getKey() + " 上面压着沙，应不挖");
            }
        }
        helper.assertTrue(afterWhole.get(chest).is(Blocks.CHEST), "箱子不该被挖");
        float ratio = cobble == 0 ? 0F : cobbleGone / (float) cobble;
        helper.assertTrue(ratio > 0.45F && ratio < 0.75F, "碎石按 0.6 的概率缺，实际 " + cobbleGone + " / " + cobble);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 拼件基类

    @GameTest(template = EMPTY)
    public static void pieceBaseNbtRoundTrip(GameTestHelper helper) {
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(helper.getLevel());
        // 用一个原版已注册的类型只是为了让 createTag 写得出 id；这里测的是基类字段，读回走本类的 NBT 构造器
        StructurePieceType type = StructurePieceType.MINE_SHAFT_ROOM;
        BoundingBox box = new BoundingBox(10, 64, 20, 30, 80, 40);
        ProbePiece piece = new ProbePiece(type, box, 0x0123456789ABCDEFL, 42);
        piece.setOrientation(Direction.EAST);
        piece.setGroundY(71);
        CompoundTag tag = piece.createTag(context);
        helper.assertTrue(tag.contains(HoannaStructurePiece.TAG_SEED) && tag.contains(HoannaStructurePiece.TAG_GROUND), "NBT 里应有种子和地面高度：" + tag);
        ProbePiece copy = new ProbePiece(type, tag);
        helper.assertTrue(copy.getBoundingBox().equals(box), "包围盒读回应相同：" + copy.getBoundingBox());
        helper.assertTrue(copy.getOrientation() == Direction.EAST && copy.getGenDepth() == 3, "朝向、深度读回应相同");
        helper.assertTrue(copy.seed() == piece.seed() && copy.groundY() == 71 && copy.hasGround(), "种子、地面高度读回应相同");
        helper.assertTrue(copy.extra == 42, "子类 saveExtra 写的字段读回应相同");

        ProbePiece noGround = new ProbePiece(type, box, -5L, 0);
        CompoundTag noGroundTag = noGround.createTag(context);
        helper.assertFalse(noGroundTag.contains(HoannaStructurePiece.TAG_GROUND), "没设地面高度时不写 " + HoannaStructurePiece.TAG_GROUND);
        ProbePiece noGroundCopy = new ProbePiece(type, noGroundTag);
        helper.assertTrue(!noGroundCopy.hasGround() && noGroundCopy.groundY() == HoannaStructurePiece.NO_GROUND && noGroundCopy.seed() == -5L,
                "没设地面高度的读回应还是没设");
        helper.assertTrue(noGroundCopy.getOrientation() == null, "没设朝向的读回应是 null");

        // 位置哈希只看坐标和种子
        double sum = 0D;
        int n = 0;
        for (int x = -40; x < 40; x += 3) {
            for (int y = -20; y < 90; y += 7) {
                for (int z = -40; z < 40; z += 3) {
                    long hash = piece.posHash(x, y, z);
                    helper.assertTrue(hash == copy.posHash(x, y, z) && hash == (Mth.getSeed(x, y, z) ^ piece.seed()), "位置哈希应只看坐标和种子");
                    float unit = HoannaStructurePiece.unit(hash);
                    helper.assertTrue(unit >= 0F && unit < 1F, "unit 应在 [0, 1)：" + unit);
                    sum += unit;
                    n++;
                }
            }
        }
        double mean = sum / n;
        helper.assertTrue(Math.abs(mean - 0.5D) < 0.02D, "unit 的均值应约 0.5，实际 " + mean + "（" + n + " 格）");

        // 已登记的结构 / 拼件类型：真的注册进原版注册表，名字都在计划表里；某个模块登记了就得登记齐
        checkRegistered(helper, ECHoannaStructures.structureTypeIds(), ECHoannaStructures.PLANNED_STRUCTURE_TYPES, true);
        checkRegistered(helper, ECHoannaStructures.pieceTypeIds(), ECHoannaStructures.PLANNED_PIECE_TYPES, false);
        checkComplete(helper, ECHoannaStructures.pieceTypeIds(), ECHoannaStructures.PLANNED_TOWN_PIECES, "城镇");
        checkComplete(helper, ECHoannaStructures.pieceTypeIds(), ECHoannaStructures.PLANNED_UNDERGROUND_PIECES, "地下");
        checkComplete(helper, ECHoannaStructures.pieceTypeIds(), ECHoannaStructures.PLANNED_VAULT_PIECES, "封印宝库");
        checkComplete(helper, ECHoannaStructures.pieceTypeIds(), ECHoannaStructures.PLANNED_SUNKEN_PIECES, "沉没城区");
        checkComplete(helper, ECHoannaStructures.pieceTypeIds(), ECHoannaStructures.PLANNED_OUTPOST_PIECES, "审判庭前哨");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pieceShapeUpdates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(4, 3, 4)));
        ProbePiece piece = new ProbePiece(StructurePieceType.MINE_SHAFT_ROOM, area, 7L, 0);

        // 完整区块：EC 栅栏（不在原版名单里）两根挨着放，当场按邻居连上
        BlockPos a = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos b = a.east();
        BlockState fence = ECAlloys.MAGIC_FENCE.get().defaultBlockState();
        piece.place(level, fence, a, area);
        piece.place(level, fence, b, area);
        helper.assertTrue(level.getBlockState(a).getValue(FenceBlock.EAST) && level.getBlockState(b).getValue(FenceBlock.WEST),
                "两根挨着放的 EC 栅栏应连上：" + level.getBlockState(a) + " / " + level.getBlockState(b));
        BlockPos outside = helper.absolutePos(new BlockPos(1, 2, 1)).above(5);
        piece.place(level, fence, outside, area);
        helper.assertTrue(level.getBlockState(outside).isAir(), "chunkBox 外面不该放方块");

        // 生成中的区块：登记后处理（原版名单里的方块由原版登记，不重复）
        ChunkPos chunkPos = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ProtoChunk proto = new ProtoChunk(chunkPos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        BlockPos marked = new BlockPos(chunkPos.getMinBlockX() + 3, a.getY(), chunkPos.getMinBlockZ() + 5);
        HoannaStructurePiece.requestShapeUpdate(level, proto, marked, false);
        ShortList list = proto.getPostProcessing()[proto.getSectionIndex(marked.getY())];
        helper.assertTrue(list != null && list.contains(ProtoChunk.packOffsetCoordinates(marked)), "生成中的区块应登记形状后处理");
        BlockPos skipped = marked.east();
        HoannaStructurePiece.requestShapeUpdate(level, proto, skipped, true);
        ShortList skippedList = proto.getPostProcessing()[proto.getSectionIndex(skipped.getY())];
        helper.assertTrue(skippedList == null || !skippedList.contains(ProtoChunk.packOffsetCoordinates(skipped)), "原版已登记过的不重复登记");
        helper.assertTrue(HoannaStructurePiece.needsShapeUpdate(fence) && HoannaStructurePiece.needsShapeUpdate(Blocks.WALL_TORCH.defaultBlockState())
                        && HoannaStructurePiece.needsShapeUpdate(Blocks.LADDER.defaultBlockState()) && !HoannaStructurePiece.needsShapeUpdate(Blocks.STONE.defaultBlockState()),
                "栅栏、墙上火把、梯子要形状后处理，石头不要");

        // fillDown：穿过草丛补到实心方块，有深度上限
        // 地面用泥土：草丛放在石头上活不了，上面一补方块（完整区块会连带更新邻居形状）它就碎了（合入时这条挂过）
        BlockPos floor = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(floor, Blocks.DIRT.defaultBlockState(), QUIET);
        level.setBlock(floor.above(), Blocks.GRASS.defaultBlockState(), QUIET);
        BlockPos column = floor.above(2);
        int limited = piece.fill(level, Blocks.COBBLESTONE.defaultBlockState(), column, area, 1);
        helper.assertTrue(limited == 1 && level.getBlockState(column).is(Blocks.COBBLESTONE) && level.getBlockState(floor.above()).is(Blocks.GRASS),
                "深度上限 1 时只补 1 格");
        level.setBlock(column, Blocks.AIR.defaultBlockState(), QUIET);
        int filled = piece.fill(level, Blocks.COBBLESTONE.defaultBlockState(), column, area, HoannaStructurePiece.DEFAULT_FILL_DEPTH);
        helper.assertTrue(filled == 2 && level.getBlockState(floor.above()).is(Blocks.COBBLESTONE) && level.getBlockState(floor).is(Blocks.DIRT),
                "应穿过草丛补到泥土上面（2 格），实际 " + filled);

        // 别的模块的方块按注册名取，没注册时退回；箱子表 id
        for (ResourceLocation id : HoannaStructureBlocks.ALL_IDS) {
            helper.assertTrue(id.getNamespace().equals(EssentialCraft5.MODID), "结构方块应是本模组的：" + id);
        }
        checkBlock(helper, HoannaStructureBlocks.VOID_STONE_ID, HoannaStructureBlocks.voidStone(), Blocks.STONE);
        checkBlock(helper, HoannaStructureBlocks.VOID_FENCE_ID, HoannaStructureBlocks.voidFence(), Blocks.STONE);
        checkBlock(helper, HoannaStructureBlocks.CONCRETE_ID, HoannaStructureBlocks.concrete(), Blocks.STONE);
        checkBlock(helper, HoannaStructureBlocks.ROOT_ID, HoannaStructureBlocks.root(), Blocks.AIR);
        checkBlock(helper, HoannaStructureBlocks.LEVITATOR_ID, HoannaStructureBlocks.levitator(), Blocks.AIR);
        checkBlock(helper, HoannaStructureBlocks.SPREADER_ID, HoannaStructureBlocks.spreader(), Blocks.AIR);
        helper.assertTrue(HoannaLoot.TOWN_TOWER.equals(EssentialCraft5.id("chests/town_tower")) && HoannaLoot.CATACOMBS.equals(EssentialCraft5.id("chests/catacombs")),
                "箱子表 id 应是 chests/town_tower、chests/catacombs");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 工具

    /** 测试用的最小拼件：多一个 int 字段，把受保护的方法开出来 */
    private static final class ProbePiece extends HoannaStructurePiece {
        final int extra;

        ProbePiece(StructurePieceType type, BoundingBox box, long seed, int extra) {
            super(type, 3, box, seed);
            this.extra = extra;
        }

        ProbePiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
            this.extra = tag.getInt("extra");
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("extra", extra);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                                ChunkPos chunkPos, BlockPos pivot) {
        }

        /** 没有朝向时局部坐标就是世界坐标 */
        void place(WorldGenLevel level, BlockState state, BlockPos pos, BoundingBox chunkBox) {
            placeBlock(level, state, pos.getX(), pos.getY(), pos.getZ(), chunkBox);
        }

        int fill(WorldGenLevel level, BlockState state, BlockPos pos, BoundingBox chunkBox, int maxDepth) {
            return fillDown(level, state, pos.getX(), pos.getY(), pos.getZ(), chunkBox, maxDepth);
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

    private static void checkRegistered(GameTestHelper helper, List<ResourceLocation> ids, Set<String> planned, boolean structureTypes) {
        for (ResourceLocation id : ids) {
            helper.assertTrue(id.getNamespace().equals(EssentialCraft5.MODID) && planned.contains(id.getPath()),
                    id + " 不在计划的名字表里（名字写进存档，上线后不能改；要加新名字先改 ECHoannaStructures 的计划表）");
            boolean present = structureTypes ? BuiltInRegistries.STRUCTURE_TYPE.containsKey(id) : BuiltInRegistries.STRUCTURE_PIECE.containsKey(id);
            helper.assertTrue(present, id + " 登记了但没注册进原版注册表");
        }
        if (!structureTypes) {
            for (ResourceLocation id : ids) {
                StructurePieceType type = BuiltInRegistries.STRUCTURE_PIECE.get(id);
                helper.assertTrue(Objects.equals(BuiltInRegistries.STRUCTURE_PIECE.getKey(type), id), id + " 反查注册名对不上");
            }
        }
    }

    private static void checkComplete(GameTestHelper helper, List<ResourceLocation> ids, Set<String> group, String name) {
        Set<String> registered = new HashSet<>();
        for (ResourceLocation id : ids) {
            if (group.contains(id.getPath())) {
                registered.add(id.getPath());
            }
        }
        helper.assertTrue(registered.isEmpty() || registered.equals(group), name + "拼件登记了一部分：" + registered + "，应是全部 " + group);
    }

    private static void checkBlock(GameTestHelper helper, ResourceLocation id, BlockState state, Block fallback) {
        if (HoannaStructureBlocks.isRegistered(id)) {
            helper.assertTrue(Objects.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()), id), id + " 已注册，应取到它，实际 " + state);
        } else {
            helper.assertTrue(state.is(fallback), id + " 没注册时应退回 " + fallback + "，实际 " + state);
        }
    }
}
