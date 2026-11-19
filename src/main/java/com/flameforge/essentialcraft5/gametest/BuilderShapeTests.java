package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.item.BuilderActions;
import com.flameforge.essentialcraft5.item.BuilderAim;
import com.flameforge.essentialcraft5.item.BuilderArea;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMix;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderModules;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderShapes;
import com.flameforge.essentialcraft5.item.BuilderStatus;
import com.flameforge.essentialcraft5.item.BuilderSurface;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.network.C2SBuilderActionPacket;
import com.flameforge.essentialcraft5.network.S2CBuilderStatusPacket;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 创世神之杖的竖墙、沿面（tools/plan/builder_upgrade.md §2.4）：
 * <ul>
 *   <li>竖墙几何（纯函数 {@link BuilderShapes#wall}）：四个朝向、顶面 / 侧面 / 底面点中、没点中，偶数宽多出来的一格在右手边，
 *       格子顺序（放置类从下往上、清除类从上往下），尺寸夹边长和格数、NBT 存成 {@code [W, 1, H]}；</li>
 *   <li>沿面几何（纯函数 {@link BuilderSurface}，用假世界测）：平面、带台阶的面（上一级、下一级都连，高两格不连）、被盖住的面不算、
 *       不同种方块不连、到格数上限截断（留离起点近的）、半径、侧面、读不了的格子（没加载）不越过、分几次扫和一次扫完结果一样；</li>
 *   <li>服务端：竖墙开工和撤销；沿面分刻测量（跨多刻、状态是测量中、期间再点是忙、测完开工、撤销）、各种中途取消后名额还回来、
 *       测量占排队名额、被盖住 / 没点中拒、一阶没有这两种形状、换形状的顺序、远域远距离瞄准沿面和竖墙、混铺在两种新形状上和实际一致。</li>
 * </ul>
 * 客户端的虚影、准星下提示、滚轮调竖墙尺寸测不到（要开游戏）；它们和服务端共用这里测的纯函数。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BuilderShapeTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 默认配置（同时在做的任务数放到 256，免得别的测试占名额） */
    static final String BATCH = "ec5_builder_shape";
    /** 沿面测量每刻只读 16 格：一片 81 格的地面要测十几刻 */
    static final String SCAN_BATCH = "ec5_builder_shape_scan";
    /** 测量每刻 16 格、施工每刻 1 格：测量和排队名额 */
    static final String SLOT_BATCH = "ec5_builder_shape_slot";

    private static final int FULL = MRUStaffItem.MAX_MRU;
    private static final BlockPos O = new BlockPos(100, 64, 100);

    private BuilderShapeTests() {
    }

    private static void before() {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
        ECBuilder.Config.Test.maxJobsTotal = 256;
        ECBuilder.Config.Test.materialPauseSeconds = 0;
    }

    private static void after() {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
    }

    @BeforeBatch(batch = BATCH)
    public static void builderShapeBefore(ServerLevel level) {
        before();
    }

    @AfterBatch(batch = BATCH)
    public static void builderShapeAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = SCAN_BATCH)
    public static void builderShapeScanBefore(ServerLevel level) {
        before();
        ECBuilder.Config.Test.faceScanPerTick = 16;
    }

    @AfterBatch(batch = SCAN_BATCH)
    public static void builderShapeScanAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = SLOT_BATCH)
    public static void builderShapeSlotBefore(ServerLevel level) {
        before();
        ECBuilder.Config.Test.faceScanPerTick = 16;
        ECBuilder.Config.setTestOverrides(null, 1, null);
    }

    @AfterBatch(batch = SLOT_BATCH)
    public static void builderShapeSlotAfter(ServerLevel level) {
        after();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 二阶、满 MRU、形状 shape、选定 block 的魔杖，装上 kinds 这几块模块 */
    private static ItemStack wand(BuilderMode mode, BuilderShape shape, @Nullable Block block, BuilderModules.Kind... kinds) {
        ItemStack stack = new ItemStack(ECBuilder.item(mode));
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(FULL));
        BuilderSelection.setShape(stack, shape);
        if (block != null) {
            BuilderSelection.setStoredBlock(stack, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block)));
        }
        if (kinds.length > 0) {
            ListTag list = new ListTag();
            for (BuilderModules.Kind kind : kinds) {
                list.add(StringTag.valueOf(kind.registryName().toString()));
            }
            stack.getOrCreateTag().put(BuilderModules.TAG, list);
        }
        return stack;
    }

    /** 主手拿着 wand、飞在相对 (x + 0.5, y, z + 0.5) 的真玩家，面朝南（右手是 −x） */
    private static ServerPlayer flyer(GameTestHelper helper, ItemStack wand, int x, int y, int z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-builder-shape");
        Vec3 at = helper.absoluteVec(new Vec3(x + 0.5D, y, z + 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setOnGround(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        return player;
    }

    /** 相对坐标 x0..x1、y、z0..z1 铺一层 block */
    private static void floor(GameTestHelper helper, Block block, int x0, int x1, int y, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                helper.setBlock(new BlockPos(x, y, z), block);
            }
        }
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static Set<BlockPos> cells(BuilderArea area) {
        Set<BlockPos> out = new HashSet<>();
        for (int i = 0; i < area.size(); i++) {
            out.add(area.cellAt(i, true));
        }
        return out;
    }

    private static Set<BlockPos> set(BlockPos... positions) {
        return new HashSet<>(Arrays.asList(positions));
    }

    /** 竖墙：width × height，朝 facing，从 anchor 起往 up 的方向长，右手方向 right 的格子 offsets（相对锚点） */
    private static Set<BlockPos> wallCells(BlockPos anchor, Direction right, int[] offsets, int height, int step) {
        Set<BlockPos> out = new HashSet<>();
        for (int dy = 0; dy < height; dy++) {
            for (int b : offsets) {
                out.add(anchor.relative(right, b).above(dy * step));
            }
        }
        return out;
    }

    /** 假世界：没写的格子是空气；unreadable 认的格子读不了（当没加载），asked 记下问过的格子 */
    private static final class FakeWorld {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final Set<BlockPos> asked = new HashSet<>();
        Predicate<BlockPos> unreadable = pos -> false;

        FakeWorld fill(Block block, int x0, int x1, int y0, int y1, int z0, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        blocks.put(new BlockPos(x, y, z), block.defaultBlockState());
                    }
                }
            }
            return this;
        }

        FakeWorld set(int x, int y, int z, Block block) {
            blocks.put(new BlockPos(x, y, z), block.defaultBlockState());
            return this;
        }

        BuilderSurface.Reader reader() {
            return pos -> {
                BlockPos at = pos.immutable();
                asked.add(at);
                return unreadable.test(at) ? null : blocks.getOrDefault(at, Blocks.AIR.defaultBlockState());
            };
        }

        BuilderSurface.Scan scan(int x, int y, int z, Direction face, int limit, int radius) {
            return BuilderSurface.scanAll(reader(), new BlockPos(x, y, z), face, limit, radius);
        }
    }

    /** y 层、x0..x1、z0..z1 的格子 */
    private static Set<BlockPos> layer(int x0, int x1, int y, int z0, int z1) {
        Set<BlockPos> out = new HashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                out.add(new BlockPos(x, y, z));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 竖墙几何
    // ------------------------------------------------------------------

    /**
     * 竖墙：点中顶面从上方那格起往上，四个朝向都沿玩家朝向的左右展开、偶数宽多出来的一格在右手边；点中侧面从侧面外那格起；
     * 点中底面从下方那格起往下；没点中从脚下那层往前一格起。格子顺序：第一格是锚点，先底层再上层，清除类从顶层起
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderWallGeometry(GameTestHelper helper) {
        BuilderShapes.Hit top = new BuilderShapes.Hit(O, Direction.UP);
        BlockPos above = O.above();
        // 宽 4（偶数）、高 2：多出来的那一格在右手边（右 = 朝向顺时针转 90°）
        Map<Direction, Set<BlockPos>> expected = Map.of(
                Direction.SOUTH, Set.of(new BlockPos(101, 65, 100), new BlockPos(100, 65, 100), new BlockPos(99, 65, 100), new BlockPos(98, 65, 100)),
                Direction.NORTH, Set.of(new BlockPos(99, 65, 100), new BlockPos(100, 65, 100), new BlockPos(101, 65, 100), new BlockPos(102, 65, 100)),
                Direction.EAST, Set.of(new BlockPos(100, 65, 99), new BlockPos(100, 65, 100), new BlockPos(100, 65, 101), new BlockPos(100, 65, 102)),
                Direction.WEST, Set.of(new BlockPos(100, 65, 101), new BlockPos(100, 65, 100), new BlockPos(100, 65, 99), new BlockPos(100, 65, 98)));
        for (Map.Entry<Direction, Set<BlockPos>> entry : expected.entrySet()) {
            BuilderArea wall = BuilderShapes.wall(new BuilderShapes.Size(4, 2), entry.getKey(), top, BlockPos.ZERO);
            Set<BlockPos> want = new HashSet<>(entry.getValue());
            for (BlockPos pos : entry.getValue()) {
                want.add(pos.above());
            }
            helper.assertTrue(cells(wall).equals(want) && wall.size() == 8, "朝 " + entry.getKey() + " 的 4 × 2 竖墙：" + cells(wall));
            helper.assertTrue(wall.cellAt(0, true).equals(above) && wall.anchor().equals(above), "朝 " + entry.getKey() + " 第一格是锚点");
            for (int i = 0; i < 4; i++) {
                helper.assertTrue(wall.cellAt(i, true).getY() == 65 && wall.cellAt(i, false).getY() == 66,
                        "朝 " + entry.getKey() + " 放置先底层、清除先顶层（第 " + i + " 格）");
            }
            helper.assertTrue(wall.cellAt(4, false).getY() == 65 && wall.cellAt(0, false).equals(above.above()),
                    "清除类从顶层锚点那一列起");
            BoundingBox box = wall.box();
            helper.assertTrue(box.getYSpan() == 2 && Math.max(box.getXSpan(), box.getZSpan()) == 4 && Math.min(box.getXSpan(), box.getZSpan()) == 1,
                    "外框 4 × 2 × 1：" + box);
        }
        // 奇数宽居中：宽 3 朝南（右 = 西 = −x）
        BuilderArea odd = BuilderShapes.wall(new BuilderShapes.Size(3, 1), Direction.SOUTH, top, BlockPos.ZERO);
        helper.assertTrue(cells(odd).equals(set(new BlockPos(99, 65, 100), above, new BlockPos(101, 65, 100))), "宽 3 左右各一格：" + cells(odd));
        helper.assertTrue(odd.cellAt(0, true).equals(above), "从中间那格起");

        // 侧面点中：从侧面外那格起往上（朝西看着东面）
        BuilderArea side = BuilderShapes.wall(new BuilderShapes.Size(3, 2), Direction.WEST, new BuilderShapes.Hit(O, Direction.EAST), BlockPos.ZERO);
        helper.assertTrue(cells(side).equals(wallCells(O.east(), Direction.NORTH, new int[] {-1, 0, 1}, 2, 1)), "侧面点中：" + cells(side));
        helper.assertTrue(side.anchor().equals(O.east()), "侧面点中锚点是侧面外那格");

        // 底面点中：从下方那格起往下挂
        BuilderArea hang = BuilderShapes.wall(new BuilderShapes.Size(1, 3), Direction.SOUTH, new BuilderShapes.Hit(O, Direction.DOWN), BlockPos.ZERO);
        helper.assertTrue(cells(hang).equals(set(O.below(), O.below(2), O.below(3))), "底面点中往下：" + cells(hang));
        helper.assertTrue(hang.cellAt(0, true).equals(O.below(3)) && hang.cellAt(0, false).equals(O.below()),
                "往下挂的墙放置照样从最低一层起，清除从贴着天花板那层起");

        // 没点中：脚下那层往前一格起往上（脚下支撑的那一格是 O，人站在 O 上方，朝南）
        BuilderArea air = BuilderShapes.wall(new BuilderShapes.Size(1, 2), Direction.SOUTH, null, O);
        helper.assertTrue(cells(air).equals(set(O.above().south(), O.above(2).south())), "没点中：" + cells(air));
        helper.succeed();
    }

    /**
     * 竖墙尺寸：没存过是 5 × 4；夹尺寸时先缩高（第二个数）；经操作包设 100 × 100 夹到二阶边长 48，NBT 存成 [48, 1, 48]；
     * 沿面不收尺寸包；换形状二阶依次两点 → 铺面 → 直线 → 竖墙 → 沿面 → 两点，一阶只在三种里转，一阶存了竖墙 / 沿面当默认铺面
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderWallSizeAndShapeCycle(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL, BuilderShape.WALL, Blocks.OAK_PLANKS);
        helper.assertTrue(Arrays.equals(BuilderSelection.size(stack, BuilderShape.WALL), new int[] {5, 4}), "竖墙默认 5 × 4");
        helper.assertTrue(BuilderShapes.clamp(10, 100, 48, 256).equals(new BuilderShapes.Size(10, 25)), "格数超了先缩高");
        ServerPlayer player = flyer(helper, stack, 2, 3, 2);
        helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SIZE, 0, 100, 100), player), "竖墙收尺寸包");
        helper.assertTrue(Arrays.equals(BuilderSelection.size(stack, BuilderShape.WALL), new int[] {48, 48}), "夹到边长 48："
                + Arrays.toString(BuilderSelection.size(stack, BuilderShape.WALL)));
        int[] raw = stack.getOrCreateTag().getCompound(BuilderSelection.TAG_SIZE).getIntArray("wall");
        helper.assertTrue(Arrays.equals(raw, new int[] {48, 1, 48}), "NBT 存成 [宽, 1, 高]：" + Arrays.toString(raw));
        helper.assertTrue(Arrays.equals(BuilderSelection.size(stack, BuilderShape.PLANE), new int[] {5, 5}), "竖墙的尺寸不冲掉铺面的");

        BuilderSelection.setShape(stack, BuilderShape.FACE);
        helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SIZE, 0, 7, 7), player), "沿面不收尺寸包");

        BuilderSelection.setShape(stack, BuilderShape.POINTS);
        BuilderShape[] order = {BuilderShape.PLANE, BuilderShape.LINE, BuilderShape.WALL, BuilderShape.FACE, BuilderShape.POINTS};
        for (BuilderShape want : order) {
            helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.CYCLE_SHAPE, 0, 0, 0), player)
                    && BuilderSelection.shape(stack) == want, "二阶换形状下一个应是 " + want + "，实际 " + BuilderSelection.shape(stack));
        }
        helper.assertTrue(BuilderTier.PAVER.nextShape(BuilderShape.LINE) == BuilderShape.POINTS, "一阶直线下一个跳过竖墙、沿面回到两点");
        ItemStack paver = new ItemStack(ECBuilder.PAVING_WAND.get());
        BuilderSelection.setShape(paver, BuilderShape.WALL);
        helper.assertTrue(BuilderSelection.shape(paver) == BuilderShape.PLANE, "一阶存了竖墙当铺面");
        BuilderSelection.setShape(paver, BuilderShape.FACE);
        helper.assertTrue(BuilderSelection.shape(paver) == BuilderShape.PLANE, "一阶存了沿面当铺面");
        ItemStack ascended = wand(BuilderMode.FILL, BuilderShape.FACE, null);
        ascended.getOrCreateTag().putInt(BuilderTier.TAG_TIER, BuilderTier.ASCENDED.level());
        helper.assertTrue(BuilderSelection.shape(ascended) == BuilderShape.FACE, "三阶能用沿面");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 沿面几何（假世界）
    // ------------------------------------------------------------------

    /** 平面：5 × 5 石头顶面 → 上方那层 25 格，第一格是起点上方那格；侧面：4 × 4 的竖墙东面 → 东边那一列 16 格，放置从最低一层起 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFaceFlatAndSide(GameTestHelper helper) {
        FakeWorld world = new FakeWorld().fill(Blocks.STONE, 0, 4, 0, 0, 0, 4);
        BuilderSurface.Scan scan = world.scan(2, 0, 2, Direction.UP, 1000, 47);
        BuilderArea area = scan.area();
        helper.assertTrue(!scan.invalid() && !scan.capped() && scan.found() == 25, "平面连上 25 格：" + scan.found());
        helper.assertTrue(cells(area).equals(layer(0, 4, 1, 0, 4)), "结果是上方那一层");
        helper.assertTrue(area.cellAt(0, true).equals(new BlockPos(2, 1, 2)) && area.anchor().equals(new BlockPos(2, 1, 2)), "第一格是起点外侧那格");
        helper.assertTrue(area.box().equals(new BoundingBox(0, 1, 0, 4, 1, 4)), "外框：" + area.box());

        FakeWorld wall = new FakeWorld().fill(Blocks.STONE, 0, 0, 0, 3, 0, 3);
        BuilderSurface.Scan east = wall.scan(0, 1, 1, Direction.EAST, 1000, 47);
        BuilderArea side = east.area();
        Set<BlockPos> want = new HashSet<>();
        for (int y = 0; y <= 3; y++) {
            for (int z = 0; z <= 3; z++) {
                want.add(new BlockPos(1, y, z));
            }
        }
        helper.assertTrue(cells(side).equals(want), "侧面：东边那一列 16 格：" + cells(side));
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(side.cellAt(i, true).getY() == 0 && side.cellAt(i, false).getY() == 3, "侧面的放置先最低一层、清除先最高一层");
        }
        helper.succeed();
    }

    /**
     * 带台阶的面：低处点中，高一格的那一片也连上（上一级）；高处点中，低一格的也连上（下一级）；高两格的不连
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFaceSteps(GameTestHelper helper) {
        FakeWorld step = new FakeWorld().fill(Blocks.STONE, 0, 4, 0, 0, 0, 4).fill(Blocks.STONE, 3, 4, 1, 1, 0, 4);
        Set<BlockPos> want = layer(0, 2, 1, 0, 4);
        want.addAll(layer(3, 4, 2, 0, 4));
        BuilderSurface.Scan low = step.scan(0, 0, 0, Direction.UP, 1000, 47);
        helper.assertTrue(cells(low.area()).equals(want), "低处点中连上高一级：" + cells(low.area()));
        BuilderSurface.Scan high = step.scan(4, 1, 4, Direction.UP, 1000, 47);
        helper.assertTrue(cells(high.area()).equals(want), "高处点中连上低一级：" + cells(high.area()));
        BuilderArea area = low.area();
        for (int i = 0; i < 15; i++) {
            helper.assertTrue(area.cellAt(i, true).getY() == 1, "放置先低的那层");
        }
        for (int i = 0; i < 10; i++) {
            helper.assertTrue(area.cellAt(i, false).getY() == 2, "清除先高的那层");
        }

        FakeWorld cliff = new FakeWorld().fill(Blocks.STONE, 0, 4, 0, 0, 0, 4).fill(Blocks.STONE, 3, 4, 1, 2, 0, 4);
        BuilderSurface.Scan bottom = cliff.scan(0, 0, 0, Direction.UP, 1000, 47);
        helper.assertTrue(cells(bottom.area()).equals(layer(0, 2, 1, 0, 4)), "高两格的不连：" + cells(bottom.area()));
        helper.succeed();
    }

    /**
     * 被盖住的面不算、也不往外连；不同种方块不连；外侧是草这类可替换的照样算露在外面；点中被盖住的面、点中空气都不算
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFaceCoveredAndOtherBlocks(GameTestHelper helper) {
        FakeWorld world = new FakeWorld().fill(Blocks.STONE, 0, 4, 0, 0, 0, 4)
                .fill(Blocks.COBBLESTONE, 4, 4, 0, 0, 0, 4)
                .set(2, 1, 2, Blocks.DIRT)
                .set(1, 1, 1, Blocks.GRASS);
        BuilderSurface.Scan scan = world.scan(0, 0, 0, Direction.UP, 1000, 47);
        Set<BlockPos> want = layer(0, 3, 1, 0, 4);
        want.remove(new BlockPos(2, 1, 2));
        helper.assertTrue(cells(scan.area()).equals(want) && scan.found() == 19,
                "去掉被泥土盖住的一格和圆石那一列，草照样算：" + cells(scan.area()));
        helper.assertTrue(!cells(scan.area()).contains(new BlockPos(2, 2, 2)), "泥土是别的方块，不从它往上连");
        helper.assertTrue(world.scan(2, 0, 2, Direction.UP, 1000, 47).invalid(), "点中被盖住的面不算");
        helper.assertTrue(world.scan(2, 3, 2, Direction.UP, 1000, 47).invalid(), "点中空气不算");
        helper.assertTrue(world.scan(4, 0, 0, Direction.UP, 1000, 47).found() == 5, "点中圆石只连圆石那一列");
        helper.succeed();
    }

    /**
     * 上限：9 × 9 的地面、上限 5 格 → 只留起点和四个邻格，记到了上限；半径 1 → 只有 3 × 3，没到上限；
     * 读不了的格子（没加载）当连不上，不越过它去读后面的格子；分几次扫（每次 16 格）和一次扫完结果一样、每次读的不超过预算
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFaceLimitsAndUnreadable(GameTestHelper helper) {
        FakeWorld world = new FakeWorld().fill(Blocks.STONE, 0, 8, 0, 0, 0, 8);
        BuilderSurface.Scan capped = world.scan(4, 0, 4, Direction.UP, 5, 47);
        helper.assertTrue(capped.capped() && capped.found() == 5, "到上限截断：" + capped.found());
        helper.assertTrue(cells(capped.area()).equals(set(new BlockPos(4, 1, 4), new BlockPos(3, 1, 4), new BlockPos(5, 1, 4),
                new BlockPos(4, 1, 3), new BlockPos(4, 1, 5))), "留下离起点最近的：" + cells(capped.area()));
        BuilderSurface.Scan exact = world.scan(4, 0, 4, Direction.UP, 81, 47);
        helper.assertTrue(!exact.capped() && exact.found() == 81, "正好 81 格不算超上限");
        BuilderSurface.Scan near = world.scan(4, 0, 4, Direction.UP, 1000, 1);
        helper.assertTrue(!near.capped() && cells(near.area()).equals(layer(3, 5, 1, 3, 5)), "半径 1 只有 3 × 3：" + cells(near.area()));
        helper.assertTrue(BuilderSurface.radius(48) == 23 && BuilderSurface.radius(1) == 0, "半径让外框边长不超过边长上限");

        FakeWorld strip = new FakeWorld().fill(Blocks.STONE, 0, 9, 0, 0, 0, 2);
        strip.unreadable = pos -> pos.getX() >= 5;
        BuilderSurface.Scan cut = strip.scan(0, 0, 1, Direction.UP, 1000, 47);
        helper.assertTrue(cells(cut.area()).equals(layer(0, 4, 1, 0, 2)), "读不了的那段之后不连：" + cells(cut.area()));
        helper.assertTrue(strip.asked.stream().noneMatch(pos -> pos.getX() > 5), "没越过读不了的格子去问后面的：" + strip.asked.stream()
                .filter(pos -> pos.getX() > 5).toList());

        BuilderSurface.Scan whole = world.scan(4, 0, 4, Direction.UP, 1000, 47);
        BuilderSurface.Scan parts = new BuilderSurface.Scan(new BlockPos(4, 0, 4), Direction.UP, 1000, 47);
        BuilderSurface.Reader reader = world.reader();
        int steps = 0;
        boolean done = false;
        while (!done && steps < 10000) {
            int before = parts.reads();
            done = parts.step(reader, 16);
            helper.assertTrue(parts.reads() - before <= 16, "一次读了 " + (parts.reads() - before) + " 格，超过 16");
            steps++;
        }
        long[] a = whole.area().cells(true);
        long[] b = parts.area().cells(true);
        helper.assertTrue(done && steps > 5 && Arrays.equals(a, b), "分 " + steps + " 次扫完，结果和一次扫完一样");
        helper.succeed();
    }

    /** 真世界的读法不加载区块：没加载的区块里的格子读出来是 null，读完那个区块仍没加载 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFaceReaderNeverLoads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(new BlockPos(2, 1, 2)).offset(200000, 0, 200000);
        int cx = SectionPos.blockToSectionCoord(far.getX());
        int cz = SectionPos.blockToSectionCoord(far.getZ());
        helper.assertTrue(level.getChunkSource().getChunkNow(cx, cz) == null, "测试前那个区块本来就没加载");
        BuilderSurface.Reader reader = BuilderSurface.reader(level);
        helper.assertTrue(reader.read(far) == null, "没加载的格子读出 null");
        BuilderSurface.Scan scan = BuilderSurface.scanAll(reader, far, Direction.UP, 100, 10);
        helper.assertTrue(scan.invalid() && level.getChunkSource().getChunkNow(cx, cz) == null, "测量也不加载那个区块");
        helper.assertTrue(reader.read(helper.absolutePos(new BlockPos(2, 1, 2))) != null, "已加载的照常读");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 服务端：竖墙
    // ------------------------------------------------------------------

    /** 点中石头顶面、朝南的 3 × 2 竖墙：放在上方那格左右各一格、两层；用掉 6 块木板；撤销收回、退料 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderWallBuildsAndUndo(GameTestHelper helper) {
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.STONE);
        ItemStack stack = wand(BuilderMode.FILL, BuilderShape.WALL, Blocks.OAK_PLANKS);
        BuilderSelection.setSize(stack, BuilderShape.WALL, 3, 2);
        ServerPlayer player = flyer(helper, stack, 5, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        BuilderJobs.StartResult result = BuilderJobs.request(player, InteractionHand.MAIN_HAND,
                new BuilderShapes.Hit(helper.absolutePos(new BlockPos(5, 1, 5)), Direction.UP), true);
        helper.assertTrue(result == BuilderJobs.StartResult.STARTED, "开工：" + result);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    for (int x = 4; x <= 6; x++) {
                        for (int y = 2; y <= 3; y++) {
                            helper.assertTrue(helper.getBlockState(new BlockPos(x, y, 5)).is(Blocks.OAK_PLANKS), "(" + x + ", " + y + ", 5) 放上了");
                        }
                    }
                    helper.assertTrue(helper.getBlockState(new BlockPos(5, 4, 5)).isAir() && helper.getBlockState(new BlockPos(5, 2, 6)).isAir(),
                            "墙外的格子没动");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 58, "用掉 6 块");
                    BuilderJobs.StartResult undo = BuilderJobs.undo(player);
                    helper.assertTrue(undo == BuilderJobs.StartResult.STARTED, "撤销：" + undo);
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(new BlockPos(5, 3, 5)).isAir() && helper.getBlockState(new BlockPos(4, 2, 5)).isAir(), "撤销收回");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 64, "退回 6 块：" + count(player, Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 清除、替换的起点：点中的那格本身 / 那层方块本身
    // ------------------------------------------------------------------

    /**
     * 几何：清除、替换（inPlace）下竖墙点中顶面 / 侧面从点中格本身往上，点中底面从点中格本身往下挂；铺面点中顶面 / 底面是点中格那一层、以它为中心，
     * 点中侧面从点中格起逆着法线往里；直线点中方块时从点中格起、沿玩家朝向；没点中时两类一样。填充的起点照旧（外侧那格），和老的写法一致。
     * 沿面 area(true) 是连上的那层方块本身、锚点是起点，格数、顺序规则和 area(false) 一样
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderInPlaceStartGeometry(GameTestHelper helper) {
        BuilderShapes.Size wall32 = new BuilderShapes.Size(3, 2);
        BuilderShapes.Hit top = new BuilderShapes.Hit(O, Direction.UP);
        BuilderShapes.Hit east = new BuilderShapes.Hit(O, Direction.EAST);
        BuilderShapes.Hit down = new BuilderShapes.Hit(O, Direction.DOWN);
        int[] three = {-1, 0, 1};

        // 竖墙
        BuilderArea wallTop = BuilderShapes.wall(wall32, Direction.SOUTH, top, BlockPos.ZERO, true);
        helper.assertTrue(cells(wallTop).equals(wallCells(O, Direction.WEST, three, 2, 1)) && wallTop.anchor().equals(O),
                "清除类竖墙点中顶面：从点中格那层往上：" + cells(wallTop));
        BuilderArea wallSide = BuilderShapes.wall(wall32, Direction.WEST, east, BlockPos.ZERO, true);
        helper.assertTrue(cells(wallSide).equals(wallCells(O, Direction.NORTH, three, 2, 1)) && wallSide.anchor().equals(O),
                "清除类竖墙点中侧面：就是点中格所在的那堵墙：" + cells(wallSide));
        // 斜着看（朝向判成北、点中的是东面）：清除类照样沿东面展开，不横穿点中格切到墙后面；填充照旧按玩家朝向
        BuilderArea wallOblique = BuilderShapes.wall(wall32, Direction.NORTH, east, BlockPos.ZERO, true);
        helper.assertTrue(cells(wallOblique).equals(wallCells(O, Direction.NORTH, three, 2, 1)),
                "清除类竖墙斜着点中东面：沿东面（南北向）展开：" + cells(wallOblique));
        BuilderArea fillOblique = BuilderShapes.wall(wall32, Direction.NORTH, east, BlockPos.ZERO, false);
        helper.assertTrue(cells(fillOblique).equals(wallCells(O.east(), Direction.EAST, three, 2, 1)),
                "填充竖墙斜着点中东面照旧按玩家朝向（东西向）展开：" + cells(fillOblique));
        BuilderArea wallDown = BuilderShapes.wall(new BuilderShapes.Size(1, 3), Direction.SOUTH, down, BlockPos.ZERO, true);
        helper.assertTrue(cells(wallDown).equals(set(O, O.below(), O.below(2))) && wallDown.cellAt(0, false).equals(O),
                "清除类竖墙点中底面：从点中格往下挂，清除从点中格起：" + cells(wallDown));
        BuilderArea wallAir = BuilderShapes.wall(new BuilderShapes.Size(1, 2), Direction.SOUTH, null, O, true);
        helper.assertTrue(cells(wallAir).equals(set(O.above().south(), O.above(2).south())), "没点中时清除类和填充一样：" + cells(wallAir));
        // 填充照旧
        helper.assertTrue(BuilderShapes.wall(wall32, Direction.SOUTH, top, BlockPos.ZERO, false).anchor().equals(O.above())
                        && cells(BuilderShapes.wall(wall32, Direction.WEST, east, BlockPos.ZERO, false)).equals(wallCells(O.east(), Direction.NORTH, three, 2, 1))
                        && BuilderShapes.wall(wall32, Direction.SOUTH, down, BlockPos.ZERO, false).anchor().equals(O.below()),
                "填充竖墙的起点照旧在点中那一面外侧");

        // 铺面（同 BuilderUpgradeTests.builderShapeGeometry 的点中格和尺寸）
        BlockPos hit = new BlockPos(5, 1, 5);
        BuilderShapes.Size size34 = new BuilderShapes.Size(3, 4);
        BuilderArea planeTop = BuilderShapes.area(BuilderShape.PLANE, size34, Direction.NORTH, new BuilderShapes.Hit(hit, Direction.UP), BlockPos.ZERO, true);
        helper.assertTrue(planeTop.box().equals(new BoundingBox(4, 1, 3, 6, 1, 6)) && planeTop.cellAt(0, true).equals(hit),
                "清除类铺面点中顶面：点中格那一层、以它为中心，实际 " + planeTop.box());
        BuilderArea planeBottom = BuilderShapes.area(BuilderShape.PLANE, size34, Direction.NORTH, new BuilderShapes.Hit(hit, Direction.DOWN), BlockPos.ZERO, true);
        helper.assertTrue(planeBottom.box().equals(new BoundingBox(4, 1, 3, 6, 1, 6)), "清除类铺面点中底面：也是点中格那一层，实际 " + planeBottom.box());
        BuilderArea planeSide = BuilderShapes.area(BuilderShape.PLANE, size34, Direction.NORTH, new BuilderShapes.Hit(hit, Direction.EAST), BlockPos.ZERO, true);
        helper.assertTrue(planeSide.box().equals(new BoundingBox(2, 1, 4, 5, 1, 6)) && planeSide.anchor().equals(hit),
                "清除类铺面点中东侧面：从点中格起往里（往西）长 4、横向居中，实际 " + planeSide.box());
        BuilderArea planeFeet = BuilderShapes.area(BuilderShape.PLANE, new BuilderShapes.Size(2, 3), Direction.SOUTH, null, new BlockPos(0, 10, 0), true);
        helper.assertTrue(planeFeet.box().equals(new BoundingBox(-1, 10, 0, 0, 10, 2)), "没点中时清除类铺面照旧在脚下一层，实际 " + planeFeet.box());
        BuilderArea lineInPlace = BuilderShapes.area(BuilderShape.LINE, new BuilderShapes.Size(1, 5), Direction.EAST, new BuilderShapes.Hit(hit, Direction.UP),
                BlockPos.ZERO, true);
        helper.assertTrue(lineInPlace.box().equals(new BoundingBox(5, 1, 5, 9, 1, 5)) && lineInPlace.anchor().equals(hit)
                        && lineInPlace.cellAt(0, true).equals(hit), "清除类直线点中顶面：从点中格起、就是点中那排，实际 " + lineInPlace.box());
        BuilderArea lineSide = BuilderShapes.area(BuilderShape.LINE, new BuilderShapes.Size(1, 3), Direction.WEST, new BuilderShapes.Hit(hit, Direction.EAST),
                BlockPos.ZERO, true);
        helper.assertTrue(lineSide.box().equals(new BoundingBox(3, 1, 5, 5, 1, 5)), "清除类直线点中东侧面：从点中格起沿玩家朝向（往西），实际 " + lineSide.box());
        BuilderArea lineFill = BuilderShapes.area(BuilderShape.LINE, new BuilderShapes.Size(1, 5), Direction.EAST, new BuilderShapes.Hit(hit, Direction.UP),
                BlockPos.ZERO, false);
        BuilderArea lineFillSide = BuilderShapes.area(BuilderShape.LINE, new BuilderShapes.Size(1, 3), Direction.WEST, new BuilderShapes.Hit(hit, Direction.EAST),
                BlockPos.ZERO, false);
        helper.assertTrue(lineFill.box().equals(new BoundingBox(5, 2, 5, 9, 2, 5)) && lineFill.anchor().equals(hit.above())
                        && lineFillSide.box().equals(new BoundingBox(4, 1, 5, 6, 1, 5)) && lineFillSide.anchor().equals(hit.east()),
                "填充直线的起点照旧在点中那一面外侧，实际 " + lineFill.box() + " / " + lineFillSide.box());
        helper.assertTrue(BuilderShapes.area(BuilderShape.LINE, new BuilderShapes.Size(1, 4), Direction.SOUTH, null, new BlockPos(0, 10, 0), true).box()
                .equals(BuilderShapes.area(BuilderShape.LINE, new BuilderShapes.Size(1, 4), Direction.SOUTH, null, new BlockPos(0, 10, 0), false).box()),
                "没点中时清除类直线和填充一样");
        BuilderArea planeFill = BuilderShapes.area(BuilderShape.PLANE, size34, Direction.NORTH, new BuilderShapes.Hit(hit, Direction.UP), BlockPos.ZERO, false);
        helper.assertTrue(planeFill.box().equals(new BoundingBox(4, 2, 3, 6, 2, 6))
                        && BuilderShapes.area(BuilderShape.PLANE, size34, Direction.NORTH, new BuilderShapes.Hit(hit, Direction.EAST), BlockPos.ZERO, false)
                        .box().equals(new BoundingBox(6, 1, 4, 9, 1, 6)),
                "填充铺面的起点照旧在点中那一面外侧，实际 " + planeFill.box());
        helper.assertTrue(BuilderShapes.area(BuilderShape.WALL, wall32, Direction.SOUTH, top, BlockPos.ZERO, true).anchor().equals(O),
                "area 转给竖墙时带上 inPlace");

        // 沿面：两层石头的顶层
        FakeWorld world = new FakeWorld().fill(Blocks.STONE, 0, 4, 0, 1, 0, 4);
        BuilderSurface.Scan scan = world.scan(2, 1, 2, Direction.UP, 1000, 47);
        BuilderArea onLayer = scan.area(true);
        BuilderArea outer = scan.area(false);
        helper.assertTrue(cells(onLayer).equals(layer(0, 4, 1, 0, 4)) && onLayer.anchor().equals(new BlockPos(2, 1, 2))
                        && onLayer.cellAt(0, true).equals(new BlockPos(2, 1, 2)),
                "清除类沿面是连上的那层方块本身（顶层），锚点是起点：" + cells(onLayer));
        helper.assertTrue(cells(outer).equals(layer(0, 4, 2, 0, 4)) && Arrays.equals(outer.cells(true), scan.area().cells(true)),
                "填充沿面照旧铺在外侧那一层，area() 就是 area(false)");
        helper.assertTrue(onLayer.size() == outer.size() && onLayer.size() == 25, "两种结果格数一样");
        FakeWorld wall = new FakeWorld().fill(Blocks.STONE, 0, 1, 0, 3, 0, 3);
        BuilderArea peel = wall.scan(1, 1, 1, Direction.EAST, 1000, 47).area(true);
        Set<BlockPos> eastColumn = new HashSet<>();
        for (int y = 0; y <= 3; y++) {
            for (int z = 0; z <= 3; z++) {
                eastColumn.add(new BlockPos(1, y, z));
            }
        }
        helper.assertTrue(cells(peel).equals(eastColumn), "侧面清除类剥掉露在东面的那一列方块（x = 1），不碰后面那列：" + cells(peel));
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(peel.cellAt(i, false).getY() == 3, "剥一层照样从上往下拆");
        }
        helper.succeed();
    }

    /**
     * 竖墙对着三堵已有的石墙（3 × 2，墙顶上另有一格石头）侧面各点一下：清除把那堵墙拆掉、墙顶那格不动；
     * 替换把那堵墙换成木板、墙前的空气不放；填充照旧在墙前那一列放木板、墙不动
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderWallInPlaceForClearAndReplace(GameTestHelper helper) {
        int[] centers = {1, 5, 9};
        for (int cx : centers) {
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int y = 1; y <= 2; y++) {
                    helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
                }
            }
            helper.setBlock(new BlockPos(cx, 3, 6), Blocks.STONE);
        }
        BuilderMode[] modes = {BuilderMode.CLEAR, BuilderMode.REPLACE_ALL, BuilderMode.FILL};
        ServerPlayer[] players = new ServerPlayer[3];
        for (int i = 0; i < 3; i++) {
            ItemStack stack = wand(modes[i], BuilderShape.WALL, modes[i] == BuilderMode.CLEAR ? null : Blocks.OAK_PLANKS);
            BuilderSelection.setSize(stack, BuilderShape.WALL, 3, 2);
            players[i] = flyer(helper, stack, centers[i], 1, 2);
            players[i].getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
            BuilderJobs.StartResult result = BuilderJobs.request(players[i], InteractionHand.MAIN_HAND,
                    new BuilderShapes.Hit(helper.absolutePos(new BlockPos(centers[i], 1, 6)), Direction.NORTH), true);
            helper.assertTrue(result == BuilderJobs.StartResult.STARTED, modes[i] + " 开工：" + result);
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (ServerPlayer player : players) {
                        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工");
                    }
                })
                .thenExecute(() -> {
                    for (int x = 0; x <= 2; x++) {
                        for (int y = 1; y <= 2; y++) {
                            helper.assertTrue(helper.getBlockState(new BlockPos(x, y, 6)).isAir(), "清除拆掉了墙 (" + x + ", " + y + ", 6)");
                        }
                    }
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 3, 6)).is(Blocks.STONE), "清除：高 2，墙顶那格不动");
                    for (int x = 4; x <= 6; x++) {
                        for (int y = 1; y <= 2; y++) {
                            helper.assertTrue(helper.getBlockState(new BlockPos(x, y, 6)).is(Blocks.OAK_PLANKS), "替换把墙换成木板 (" + x + ", " + y + ", 6)");
                            helper.assertTrue(helper.getBlockState(new BlockPos(x, y, 5)).isAir(), "替换：墙前的空气不放 (" + x + ", " + y + ", 5)");
                        }
                    }
                    helper.assertTrue(helper.getBlockState(new BlockPos(5, 3, 6)).is(Blocks.STONE), "替换：墙顶那格不动");
                    helper.assertTrue(count(players[1], Items.OAK_PLANKS) == 58, "替换用掉 6 块：" + count(players[1], Items.OAK_PLANKS));
                    for (int x = 8; x <= 10; x++) {
                        for (int y = 1; y <= 2; y++) {
                            helper.assertTrue(helper.getBlockState(new BlockPos(x, y, 5)).is(Blocks.OAK_PLANKS), "填充照旧放在墙前 (" + x + ", " + y + ", 5)");
                            helper.assertTrue(helper.getBlockState(new BlockPos(x, y, 6)).is(Blocks.STONE), "填充不动墙 (" + x + ", " + y + ", 6)");
                        }
                    }
                    helper.assertTrue(count(players[2], Items.OAK_PLANKS) == 58, "填充用掉 6 块：" + count(players[2], Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    /**
     * 铺面（3 × 3）点中两层石头地面的顶面：清除拆掉顶层、底层不动；替换把顶层换成木板、上方不放；填充照旧铺在上方那层
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderPlaneInPlaceForClearAndReplace(GameTestHelper helper) {
        inPlaceLayers(helper, BuilderShape.PLANE);
    }

    /**
     * 沿面点中两层石头地面的顶面：清除剥掉连上的那层（顶层）、底层不动；替换把那层换成木板、上方不放；填充照旧铺在外侧那层。
     * 三片中间隔一格空气，各自只连自己那片
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderFaceInPlaceForClearAndReplace(GameTestHelper helper) {
        inPlaceLayers(helper, BuilderShape.FACE);
    }

    /**
     * 直线（长 3，朝南）点中两层石头条（x 1 / 5 / 9，z 2~6，y 1~2）北端那格的顶面：清除拆掉点中那排（z 2~4 的顶层），替换把那排换成木板、上方不放，
     * 填充照旧铺在上方那排；三者都不动底层和 z 5~6
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderLineInPlaceForClearAndReplace(GameTestHelper helper) {
        int[] columns = {1, 5, 9};
        for (int cx : columns) {
            for (int y = 1; y <= 2; y++) {
                floor(helper, Blocks.STONE, cx, cx, y, 2, 6);
            }
        }
        BuilderMode[] modes = {BuilderMode.CLEAR, BuilderMode.REPLACE_ALL, BuilderMode.FILL};
        ServerPlayer[] players = new ServerPlayer[3];
        for (int i = 0; i < 3; i++) {
            ItemStack stack = wand(modes[i], BuilderShape.LINE, modes[i] == BuilderMode.CLEAR ? null : Blocks.OAK_PLANKS);
            BuilderSelection.setSize(stack, BuilderShape.LINE, 1, 3);
            players[i] = flyer(helper, stack, columns[i], 5, 1);
            players[i].getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
            helper.assertTrue(players[i].getDirection() == Direction.SOUTH, "玩家朝南");
            BuilderJobs.StartResult result = BuilderJobs.request(players[i], InteractionHand.MAIN_HAND,
                    new BuilderShapes.Hit(helper.absolutePos(new BlockPos(columns[i], 2, 2)), Direction.UP), true);
            helper.assertTrue(result == BuilderJobs.StartResult.STARTED, "直线 " + modes[i] + " 开工：" + result);
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (ServerPlayer player : players) {
                        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工");
                    }
                })
                .thenExecute(() -> {
                    for (int i = 0; i < 3; i++) {
                        int x = columns[i];
                        for (int z = 2; z <= 6; z++) {
                            BlockState low = helper.getBlockState(new BlockPos(x, 1, z));
                            BlockState mid = helper.getBlockState(new BlockPos(x, 2, z));
                            BlockState up = helper.getBlockState(new BlockPos(x, 3, z));
                            String at = modes[i] + " (" + x + ", " + z + ")";
                            helper.assertTrue(low.is(Blocks.STONE), at + "：底层不动");
                            if (z > 4) {
                                helper.assertTrue(mid.is(Blocks.STONE) && up.isAir(), at + "：长 3 以外不动，实际 " + mid + " / " + up);
                                continue;
                            }
                            switch (modes[i]) {
                                case CLEAR -> helper.assertTrue(mid.isAir() && up.isAir(), at + "：点中那排拆掉，实际 " + mid);
                                case REPLACE_ALL -> helper.assertTrue(mid.is(Blocks.OAK_PLANKS) && up.isAir(), at + "：点中那排换成木板、上方不放，实际 " + mid + " / " + up);
                                default -> helper.assertTrue(mid.is(Blocks.STONE) && up.is(Blocks.OAK_PLANKS), at + "：填充铺在上方那排，实际 " + mid + " / " + up);
                            }
                        }
                    }
                    helper.assertTrue(count(players[1], Items.OAK_PLANKS) == 61 && count(players[2], Items.OAK_PLANKS) == 61,
                            "替换、填充各用掉 3 块：" + count(players[1], Items.OAK_PLANKS) + " / " + count(players[2], Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    /**
     * 撤销两条路一致：放一格木板后清除一片石头（每刻 1 格），清除做到一半和做完以后，魔杖键 + 右键那条（{@link BuilderJobs#undo}）都不撤、
     * 回「上一单是清除」，那格木板还在，清除也没被停下；面板同一时刻也是「上一次是清除」
     */
    @GameTest(template = EMPTY_LARGE, batch = SLOT_BATCH, timeoutTicks = 300)
    public static void builderUndoRefusedAfterClear(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        floor(helper, Blocks.STONE, 4, 6, 1, 4, 6);
        ItemStack fill = wand(BuilderMode.FILL, BuilderShape.PLANE, Blocks.OAK_PLANKS);
        BuilderSelection.setSize(fill, BuilderShape.PLANE, 1, 1);
        ItemStack clear = wand(BuilderMode.CLEAR, BuilderShape.PLANE, null);
        BuilderSelection.setSize(clear, BuilderShape.PLANE, 3, 3);
        ServerPlayer player = flyer(helper, fill, 3, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 16));
        UUID id = player.getUUID();
        java.util.function.IntSupplier panel = () -> com.flameforge.essentialcraft5.item.BuilderPanel.compute(player, player.getMainHandItem(),
                helper.getLevel().getServer().getTickCount()).undoState();
        BlockPos plank = new BlockPos(1, 2, 1);
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND,
                new BuilderShapes.Hit(helper.absolutePos(new BlockPos(1, 1, 1)), Direction.UP), true) == BuilderJobs.StartResult.STARTED, "放一格木板");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在放"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(plank).is(Blocks.OAK_PLANKS), "木板放上了");
                    helper.assertTrue(panel.getAsInt() == com.flameforge.essentialcraft5.network.S2CBuilderPanelPacket.UNDO_READY, "放完能撤");
                    player.setItemInHand(InteractionHand.MAIN_HAND, clear);
                    helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND,
                            new BuilderShapes.Hit(helper.absolutePos(new BlockPos(5, 1, 5)), Direction.UP), true) == BuilderJobs.StartResult.STARTED,
                            "清除那片石头");
                })
                .thenWaitUntil(() -> helper.assertTrue(BuilderJobs.progress(id) >= 1, "还没拆成一格"))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.progress(id) < 9, "清除还没做完（每刻 1 格）");
                    helper.assertTrue(panel.getAsInt() == com.flameforge.essentialcraft5.network.S2CBuilderPanelPacket.UNDO_CLEARED,
                            "清除做到一半：面板变灰，实际 " + panel.getAsInt());
                    BuilderJobs.StartResult undo = BuilderJobs.undo(player);
                    helper.assertTrue(undo == BuilderJobs.StartResult.UNDO_CLEARED, "清除做到一半时撤销不撤：" + undo);
                    helper.assertTrue(BuilderJobs.progress(id) >= 1, "清除没被停下");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在清"))
                .thenExecute(() -> {
                    for (int x = 4; x <= 6; x++) {
                        for (int z = 4; z <= 6; z++) {
                            helper.assertTrue(helper.getBlockState(new BlockPos(x, 1, z)).isAir(), "清除做完 (" + x + ", 1, " + z + ")");
                        }
                    }
                    helper.assertTrue(panel.getAsInt() == com.flameforge.essentialcraft5.network.S2CBuilderPanelPacket.UNDO_CLEARED,
                            "清除做完：面板变灰，实际 " + panel.getAsInt());
                    BuilderJobs.StartResult undo = BuilderJobs.undo(player);
                    helper.assertTrue(undo == BuilderJobs.StartResult.UNDO_CLEARED, "上一单是清除：撤销不撤更早那次放置：" + undo);
                    helper.assertTrue(!BuilderJobs.isRunning(id), "没开撤销任务");
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(plank).is(Blocks.OAK_PLANKS), "木板还在"))
                .thenSucceed();
    }

    /**
     * 主手清除做着时用副手的填充魔杖排一单：清除做完、填充开工放下一格后，正在做的是填充，两条撤销路都能撤它
     * （记录是排队时建的，比清除结束得早，不能按时刻比）
     */
    @GameTest(template = EMPTY_LARGE, batch = SLOT_BATCH, timeoutTicks = 300)
    public static void builderUndoQueuedFillAfterClear(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        floor(helper, Blocks.STONE, 4, 6, 1, 4, 6);
        ItemStack clear = wand(BuilderMode.CLEAR, BuilderShape.PLANE, null);
        BuilderSelection.setSize(clear, BuilderShape.PLANE, 3, 3);
        ItemStack fill = wand(BuilderMode.FILL, BuilderShape.PLANE, Blocks.OAK_PLANKS);
        BuilderSelection.setSize(fill, BuilderShape.PLANE, 3, 3);
        ServerPlayer player = flyer(helper, clear, 3, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        UUID id = player.getUUID();
        BuilderJobs.StartResult c = BuilderJobs.request(player, InteractionHand.MAIN_HAND,
                new BuilderShapes.Hit(helper.absolutePos(new BlockPos(5, 1, 5)), Direction.UP), false);
        helper.assertTrue(c == BuilderJobs.StartResult.STARTED, "清除开工：" + c);
        player.setItemInHand(InteractionHand.OFF_HAND, fill);
        BuilderJobs.StartResult f = BuilderJobs.request(player, InteractionHand.OFF_HAND,
                new BuilderShapes.Hit(helper.absolutePos(new BlockPos(1, 1, 1)), Direction.UP), false);
        helper.assertTrue(f == BuilderJobs.StartResult.QUEUED, "填充排队：" + f);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    // 只看进度会在清除还没做完时就放行（那时进度是清除的）：要等那片石头全拆完、填充放下了第一格
                    for (int x = 4; x <= 6; x++) {
                        for (int z = 4; z <= 6; z++) {
                            helper.assertTrue(helper.getBlockState(new BlockPos(x, 1, z)).isAir(), "清除没做完");
                        }
                    }
                    boolean placed = false;
                    for (int x = 0; x <= 2; x++) {
                        for (int z = 0; z <= 2; z++) {
                            placed |= helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.OAK_PLANKS);
                        }
                    }
                    helper.assertTrue(placed && BuilderJobs.progress(id) >= 1, "填充还没放下一格");
                })
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.progress(id) < 9, "填充已经做完了");
                    int panel = com.flameforge.essentialcraft5.item.BuilderPanel.compute(player, player.getMainHandItem(),
                            helper.getLevel().getServer().getTickCount()).undoState();
                    helper.assertTrue(panel == com.flameforge.essentialcraft5.network.S2CBuilderPanelPacket.UNDO_READY, "面板能撤，实际 " + panel);
                    BuilderJobs.StartResult undo = BuilderJobs.undo(player);
                    helper.assertTrue(undo == BuilderJobs.StartResult.STARTED, "正在做的是填充，撤销撤它，实际 " + undo);
                })
                .thenSucceed();
    }

    /**
     * 三阶（撤销栈 3 层）：放一格 → 清除一格 → 再放一格；撤掉清除之后放的那格以后，再撤不越过清除去撤最早那格
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderUndoStackStopsAtClear(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(8, 1, 1), Blocks.STONE);
        ItemStack fill = wand(BuilderMode.FILL, BuilderShape.PLANE, Blocks.OAK_PLANKS);
        fill.getOrCreateTag().putInt(BuilderTier.TAG_TIER, BuilderTier.ASCENDED.level());
        BuilderSelection.setSize(fill, BuilderShape.PLANE, 1, 1);
        ItemStack clear = wand(BuilderMode.CLEAR, BuilderShape.PLANE, null);
        clear.getOrCreateTag().putInt(BuilderTier.TAG_TIER, BuilderTier.ASCENDED.level());
        BuilderSelection.setSize(clear, BuilderShape.PLANE, 1, 1);
        ServerPlayer player = flyer(helper, fill, 4, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 16));
        UUID id = player.getUUID();
        java.util.function.BiConsumer<ItemStack, BlockPos> start = (stack, pos) -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BuilderJobs.StartResult result = BuilderJobs.request(player, InteractionHand.MAIN_HAND,
                    new BuilderShapes.Hit(helper.absolutePos(pos), Direction.UP), false);
            helper.assertTrue(result == BuilderJobs.StartResult.STARTED, "开工 " + pos + "：" + result);
        };
        helper.startSequence()
                .thenExecute(() -> start.accept(fill, new BlockPos(1, 1, 1)))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在放第一格"))
                .thenExecute(() -> start.accept(clear, new BlockPos(5, 1, 5)))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在清"))
                .thenExecute(() -> start.accept(fill, new BlockPos(8, 1, 1)))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在放第二格"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 5)).isAir() && helper.getBlockState(new BlockPos(8, 2, 1)).is(Blocks.OAK_PLANKS),
                            "清掉一格、放上第二格");
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.STARTED, "清除之后放的那格能撤");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在撤"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(new BlockPos(8, 2, 1)).isAir(), "第二格撤掉了");
                    BuilderJobs.StartResult undo = BuilderJobs.undo(player);
                    helper.assertTrue(undo == BuilderJobs.StartResult.UNDO_CLEARED, "再撤停在清除那里，实际 " + undo);
                    int panel = com.flameforge.essentialcraft5.item.BuilderPanel.compute(player, player.getMainHandItem(),
                            helper.getLevel().getServer().getTickCount()).undoState();
                    helper.assertTrue(panel == com.flameforge.essentialcraft5.network.S2CBuilderPanelPacket.UNDO_CLEARED, "面板也变灰，实际 " + panel);
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 1)).is(Blocks.OAK_PLANKS), "最早那格还在"))
                .thenSucceed();
    }

    /** 三片 3 × 3、两层的石头（x 0~2 / 4~6 / 8~10，z 4~6，y 1~2），分别用清除、替换、填充点中中心那格的顶面 */
    private static void inPlaceLayers(GameTestHelper helper, BuilderShape shape) {
        int[] centers = {1, 5, 9};
        for (int cx : centers) {
            for (int y = 1; y <= 2; y++) {
                floor(helper, Blocks.STONE, cx - 1, cx + 1, y, 4, 6);
            }
        }
        BuilderMode[] modes = {BuilderMode.CLEAR, BuilderMode.REPLACE_ALL, BuilderMode.FILL};
        ServerPlayer[] players = new ServerPlayer[3];
        for (int i = 0; i < 3; i++) {
            ItemStack stack = wand(modes[i], shape, modes[i] == BuilderMode.CLEAR ? null : Blocks.OAK_PLANKS);
            if (shape == BuilderShape.PLANE) {
                BuilderSelection.setSize(stack, BuilderShape.PLANE, 3, 3);
            }
            players[i] = flyer(helper, stack, centers[i], 5, 1);
            players[i].getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
            BuilderJobs.StartResult result = BuilderJobs.request(players[i], InteractionHand.MAIN_HAND,
                    new BuilderShapes.Hit(helper.absolutePos(new BlockPos(centers[i], 2, 5)), Direction.UP), true);
            BuilderJobs.StartResult want = shape == BuilderShape.FACE ? BuilderJobs.StartResult.SURVEYING : BuilderJobs.StartResult.STARTED;
            helper.assertTrue(result == want, shape + " " + modes[i] + " 开工：" + result);
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (ServerPlayer player : players) {
                        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在测量 / 施工");
                    }
                })
                .thenExecute(() -> {
                    if (shape == BuilderShape.FACE) {
                        for (int i = 0; i < 3; i++) {
                            helper.assertTrue(BuilderJobs.lastSurvey(players[i].getUUID()) == BuilderJobs.StartResult.STARTED,
                                    modes[i] + " 测完开工：" + BuilderJobs.lastSurvey(players[i].getUUID()));
                        }
                    }
                    for (int x = 0; x <= 10; x++) {
                        for (int z = 4; z <= 6; z++) {
                            int i = x / 4;
                            if (x % 4 == 3) {
                                continue;
                            }
                            BlockState low = helper.getBlockState(new BlockPos(x, 1, z));
                            BlockState mid = helper.getBlockState(new BlockPos(x, 2, z));
                            BlockState up = helper.getBlockState(new BlockPos(x, 3, z));
                            String at = modes[i] + " (" + x + ", " + z + ")";
                            helper.assertTrue(low.is(Blocks.STONE), at + "：底层不动");
                            switch (modes[i]) {
                                case CLEAR -> helper.assertTrue(mid.isAir() && up.isAir(), at + "：顶层拆掉，实际 " + mid);
                                case REPLACE_ALL -> helper.assertTrue(mid.is(Blocks.OAK_PLANKS) && up.isAir(), at + "：顶层换成木板、上方不放，实际 " + mid + " / " + up);
                                default -> helper.assertTrue(mid.is(Blocks.STONE) && up.is(Blocks.OAK_PLANKS), at + "：填充铺在上方那层，实际 " + mid + " / " + up);
                            }
                        }
                    }
                    helper.assertTrue(count(players[1], Items.OAK_PLANKS) == 55 && count(players[2], Items.OAK_PLANKS) == 55,
                            "替换、填充各用掉 9 块：" + count(players[1], Items.OAK_PLANKS) + " / " + count(players[2], Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 服务端：沿面测量
    // ------------------------------------------------------------------

    /**
     * 9 × 9 石头地面上点一下沿面（每刻只测 16 格）：先进入测量（状态包是测量中、期间再点是忙），过两刻还在测；测完开工，
     * 81 格木板铺在上方那层；撤销收回、退料
     */
    @GameTest(template = EMPTY_LARGE, batch = SCAN_BATCH, timeoutTicks = 300)
    public static void builderFaceSurveysAcrossTicks(GameTestHelper helper) {
        floor(helper, Blocks.STONE, 1, 9, 1, 1, 9);
        ItemStack stack = wand(BuilderMode.FILL, BuilderShape.FACE, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 5, 5, 5);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 64));
        UUID id = player.getUUID();
        BuilderShapes.Hit hit = new BuilderShapes.Hit(helper.absolutePos(new BlockPos(5, 1, 5)), Direction.UP);
        BuilderJobs.StartResult result = BuilderJobs.request(player, InteractionHand.MAIN_HAND, hit, true);
        helper.assertTrue(result == BuilderJobs.StartResult.SURVEYING && BuilderJobs.isSurveying(id) && BuilderJobs.isRunning(id), "开始测量：" + result);
        long now = helper.getLevel().getServer().getTickCount();
        BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(id, now);
        helper.assertTrue(snapshot != null && snapshot.surveying() && snapshot.total() == BuilderTier.stats(stack).maxVolume(), "现状是测量中：" + snapshot);
        S2CBuilderStatusPacket status = BuilderStatus.compute(player, now);
        helper.assertTrue(status != null && status.state() == S2CBuilderStatusPacket.SURVEYING, "状态包是测量中：" + status);
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, hit, true) == BuilderJobs.StartResult.BUSY, "测量中再点是忙");
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    BuilderJobs.Snapshot mid = BuilderJobs.snapshot(id, helper.getLevel().getServer().getTickCount());
                    helper.assertTrue(BuilderJobs.isSurveying(id) && mid != null && mid.surveying() && mid.done() > 0 && mid.done() < 81,
                            "两刻后还在测、已连上一部分：" + mid);
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isSurveying(id), "还在测量"))
                .thenExecute(() -> helper.assertTrue(BuilderJobs.lastSurvey(id) == BuilderJobs.StartResult.STARTED, "测完开工：" + BuilderJobs.lastSurvey(id)))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在施工"))
                .thenExecute(() -> {
                    for (int x = 1; x <= 9; x++) {
                        for (int z = 1; z <= 9; z++) {
                            helper.assertTrue(helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.OAK_PLANKS), "(" + x + ", 2, " + z + ") 铺上了");
                        }
                    }
                    helper.assertTrue(helper.getBlockState(new BlockPos(0, 2, 5)).isAir() && helper.getBlockState(new BlockPos(5, 3, 5)).isAir(),
                            "地面外的格子没动");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 128 - 81, "用掉 81 块：" + count(player, Items.OAK_PLANKS));
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.STARTED, "能撤销");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在撤销"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(new BlockPos(5, 2, 5)).isAir() && helper.getBlockState(new BlockPos(1, 2, 9)).isAir(), "撤销收回");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 128, "退回 81 块");
                })
                .thenSucceed();
    }

    /**
     * 测量中途取消：换了手里的东西、走远、潜行对空气右键（停下）、换了形状、变成旁观、换维度、下线，都在下一刻（或当场）没了测量、没开工，
     * 名额还回来（同一个人马上能再点一下开始新的测量）
     */
    @GameTest(template = EMPTY_LARGE, batch = SCAN_BATCH, timeoutTicks = 100)
    public static void builderFaceSurveyCancels(GameTestHelper helper) {
        floor(helper, Blocks.STONE, 1, 9, 1, 1, 9);
        BuilderShapes.Hit hit = new BuilderShapes.Hit(helper.absolutePos(new BlockPos(5, 1, 5)), Direction.UP);
        ServerPlayer[] players = new ServerPlayer[7];
        for (int i = 0; i < players.length; i++) {
            players[i] = flyer(helper, wand(BuilderMode.FILL, BuilderShape.FACE, Blocks.OAK_PLANKS), 5, 5, 5);
            players[i].getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
            BuilderJobs.StartResult result = BuilderJobs.request(players[i], InteractionHand.MAIN_HAND, hit, true);
            helper.assertTrue(result == BuilderJobs.StartResult.SURVEYING, "第 " + i + " 个开始测量：" + result);
        }
        ServerLevel level = helper.getLevel();
        players[0].setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        players[1].moveTo(players[1].getX() + 200D, players[1].getY(), players[1].getZ());
        helper.assertTrue(BuilderJobs.stop(players[2]) && !BuilderJobs.isSurveying(players[2].getUUID()), "停下当场取消测量");
        BuilderSelection.setShape(players[3].getMainHandItem(), BuilderShape.PLANE);
        players[4].setGameMode(GameType.SPECTATOR);
        BuilderJobs.onBuilderChangeDimension(new PlayerEvent.PlayerChangedDimensionEvent(players[5], Level.OVERWORLD, Level.NETHER));
        helper.assertTrue(!BuilderJobs.isSurveying(players[5].getUUID()), "换维度当场取消");
        BuilderJobs.onBuilderLogout(new PlayerEvent.PlayerLoggedOutEvent(players[6]));
        helper.assertTrue(!BuilderJobs.isSurveying(players[6].getUUID()), "下线当场取消");
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    for (int i = 0; i < players.length; i++) {
                        UUID id = players[i].getUUID();
                        helper.assertTrue(!BuilderJobs.isSurveying(id) && !BuilderJobs.isRunning(id) && BuilderJobs.lastSurvey(id) == null,
                                "第 " + i + " 个的测量取消了、没开工");
                    }
                    helper.assertTrue(helper.getBlockState(new BlockPos(5, 2, 5)).isAir(), "什么都没放");
                    // 名额还回来：拿回魔杖、走回来，再点一下照样开始测量
                    players[0].setItemInHand(InteractionHand.MAIN_HAND, wand(BuilderMode.FILL, BuilderShape.FACE, Blocks.OAK_PLANKS));
                    Vec3 back = helper.absoluteVec(new Vec3(5.5D, 5D, 5.5D));
                    players[1].moveTo(back.x, back.y, back.z);
                    for (int i = 0; i < 3; i++) {
                        BuilderJobs.StartResult again = BuilderJobs.request(players[i], InteractionHand.MAIN_HAND, hit, true);
                        helper.assertTrue(again == BuilderJobs.StartResult.SURVEYING, "第 " + i + " 个再点一下又开始测量：" + again);
                        helper.assertTrue(BuilderJobs.stop(players[i]), "停下");
                    }
                    helper.assertTrue(level.getBlockState(hit.pos().above()).isAir(), "都没开工");
                })
                .thenSucceed();
    }

    /**
     * 测量占排队名额：手上一单在做（每刻 1 格）时点沿面开始测量，这时再点任何形状都是忙；测完那一单排进队（不是插到前面开工），
     * 名额照旧是 1 在做 + 1 排队
     */
    @GameTest(template = EMPTY_LARGE, batch = SLOT_BATCH, timeoutTicks = 400)
    public static void builderFaceSurveyTakesQueueSlot(GameTestHelper helper) {
        floor(helper, Blocks.STONE, 1, 3, 1, 1, 3);
        ItemStack line = wand(BuilderMode.FILL, BuilderShape.LINE, Blocks.OAK_PLANKS);
        BuilderSelection.setSize(line, BuilderShape.LINE, 1, 10);
        ServerPlayer player = flyer(helper, line, 8, 6, 0);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        UUID id = player.getUUID();
        BuilderJobs.StartResult first = BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, false);
        helper.assertTrue(first == BuilderJobs.StartResult.STARTED, "直线开工：" + first);
        ItemStack face = wand(BuilderMode.FILL, BuilderShape.FACE, Blocks.OAK_PLANKS);
        player.setItemInHand(InteractionHand.OFF_HAND, face);
        BuilderShapes.Hit hit = new BuilderShapes.Hit(helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP);
        BuilderJobs.StartResult survey = BuilderJobs.request(player, InteractionHand.OFF_HAND, hit, false);
        helper.assertTrue(survey == BuilderJobs.StartResult.SURVEYING, "手上有一单时照样能开始测量：" + survey);
        BuilderJobs.StartResult third = BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, false);
        helper.assertTrue(third == BuilderJobs.StartResult.BUSY, "测量占了排队名额，再点是忙：" + third);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isSurveying(id), "还在测量"))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.lastSurvey(id) == BuilderJobs.StartResult.QUEUED, "测完排进队：" + BuilderJobs.lastSurvey(id));
                    BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(id, helper.getLevel().getServer().getTickCount());
                    helper.assertTrue(snapshot != null && !snapshot.queued() && !snapshot.surveying(), "现状显示在做的那单：" + snapshot);
                    helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, false) == BuilderJobs.StartResult.BUSY,
                            "1 在做 + 1 排队，再点还是忙");
                    BuilderJobs.stop(player);
                    helper.assertTrue(!BuilderJobs.isRunning(id), "停下连排队的一起清掉");
                })
                .thenSucceed();
    }

    /**
     * 沿面要二次确认（料不够）：测量要几十刻，确认的 60 刻从提示发出那刻算，不从点下去那刻算——提示出来后隔 40 刻（离第一下点已超过 60 刻）
     * 再点一下，第二次测完照样算确认、开工
     */
    @GameTest(template = EMPTY_LARGE, batch = SCAN_BATCH, timeoutTicks = 400)
    public static void builderFaceConfirmAfterLongSurvey(GameTestHelper helper) {
        floor(helper, Blocks.STONE, 1, 9, 1, 1, 9);
        ItemStack stack = wand(BuilderMode.FILL, BuilderShape.FACE, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 5, 5, 5);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 10));
        UUID id = player.getUUID();
        BuilderShapes.Hit hit = new BuilderShapes.Hit(helper.absolutePos(new BlockPos(5, 1, 5)), Direction.UP);
        long[] firstClick = new long[1];
        long[] prompted = new long[1];
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, hit, true) == BuilderJobs.StartResult.SURVEYING, "开始测量");
        firstClick[0] = helper.getLevel().getServer().getTickCount();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isSurveying(id), "还在测量"))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.lastSurvey(id) == BuilderJobs.StartResult.NEEDS_CONFIRM, "料不够要确认：" + BuilderJobs.lastSurvey(id));
                    prompted[0] = helper.getLevel().getServer().getTickCount();
                    helper.assertTrue(prompted[0] - firstClick[0] > 20, "测量跨了 20 刻以上：" + (prompted[0] - firstClick[0]));
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    long now = helper.getLevel().getServer().getTickCount();
                    helper.assertTrue(now - firstClick[0] > 60 && now - prompted[0] <= 60, "这时离第一下点已过 60 刻、离提示还不到 60 刻");
                    helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, hit, true) == BuilderJobs.StartResult.SURVEYING, "再点一下又测量");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isSurveying(id), "第二次还在测量"))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.lastSurvey(id) == BuilderJobs.StartResult.STARTED, "第二下算确认、开工：" + BuilderJobs.lastSurvey(id));
                    BuilderJobs.stop(player);
                })
                .thenSucceed();
    }

    /** 沿面没对准方块（右键空气）、点中被盖住的面、点中空气：拒，不进测量 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH)
    public static void builderFaceRejectsBadHit(GameTestHelper helper) {
        floor(helper, Blocks.STONE, 1, 3, 1, 1, 3);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.DIRT);
        ServerPlayer player = flyer(helper, wand(BuilderMode.FILL, BuilderShape.FACE, Blocks.OAK_PLANKS), 2, 5, 2);
        UUID id = player.getUUID();
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, true) == BuilderJobs.StartResult.NO_SURFACE, "没对准方块拒");
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND,
                new BuilderShapes.Hit(helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP), true) == BuilderJobs.StartResult.NO_SURFACE, "被盖住的面拒");
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND,
                new BuilderShapes.Hit(helper.absolutePos(new BlockPos(5, 3, 5)), Direction.UP), true) == BuilderJobs.StartResult.NO_SURFACE, "点中空气拒");
        helper.assertTrue(!BuilderJobs.isSurveying(id) && !BuilderJobs.isRunning(id), "没进测量");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 远域、混铺
    // ------------------------------------------------------------------

    /**
     * 装了远域：30 多格高处往下，竖墙复核认（不是「形状不对」）；沿面经远距离瞄准开始测量，测完铺满 3 × 3 地面上方那层
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderFaceAndWallLongAim(GameTestHelper helper) {
        floor(helper, Blocks.STONE, 3, 5, 1, 3, 5);
        ItemStack wall = wand(BuilderMode.FILL, BuilderShape.WALL, Blocks.OAK_PLANKS, BuilderModules.Kind.REACH);
        ItemStack face = wand(BuilderMode.FILL, BuilderShape.FACE, Blocks.OAK_PLANKS, BuilderModules.Kind.REACH);
        ItemStack line = wand(BuilderMode.FILL, BuilderShape.LINE, Blocks.OAK_PLANKS, BuilderModules.Kind.REACH);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-builder-shape");
        Vec3 at = helper.absoluteVec(new Vec3(4.5D, 32D, 4.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 90F);
        player.setOnGround(false);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 4));
        int up = Direction.UP.get3DDataValue();
        helper.assertTrue(BuilderAim.verify(player, wall, target, up, false) == BuilderAim.Verdict.OK, "竖墙远距离瞄准认");
        helper.assertTrue(BuilderAim.verify(player, line, target, up, false) == BuilderAim.Verdict.WRONG_SHAPE, "直线照旧不放远");
        player.setItemInHand(InteractionHand.MAIN_HAND, face);
        BuilderAim.Outcome outcome = BuilderAim.start(player, InteractionHand.MAIN_HAND, target, up);
        helper.assertTrue(outcome.verdict() == BuilderAim.Verdict.OK && outcome.start() == BuilderJobs.StartResult.SURVEYING, "沿面远距离开始测量：" + outcome);
        UUID id = player.getUUID();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在测量或施工"))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.lastSurvey(id) == BuilderJobs.StartResult.STARTED, "测完开工：" + BuilderJobs.lastSurvey(id));
                    for (int x = 3; x <= 5; x++) {
                        for (int z = 3; z <= 5; z++) {
                            helper.assertTrue(helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.OAK_PLANKS), "(" + x + ", 2, " + z + ") 铺上了");
                        }
                    }
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 55, "用掉 9 块");
                })
                .thenSucceed();
    }

    /** 混铺在竖墙上：确认扫出的各种格数等于逐格按表算的，铺出来每格都和表算的一样 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderMixOnWall(GameTestHelper helper) {
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.STONE);
        ItemStack stack = wand(BuilderMode.FILL, BuilderShape.WALL, null, BuilderModules.Kind.MIX);
        BuilderSelection.setSize(stack, BuilderShape.WALL, 5, 4);
        BuilderMix.write(stack, List.of(new BuilderMix.Entry(Blocks.COBBLESTONE, 1), new BuilderMix.Entry(Blocks.OAK_PLANKS, 2)), 777);
        ServerPlayer player = flyer(helper, stack, 5, 4, 1);
        player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 64));
        BuilderShapes.Hit hit = new BuilderShapes.Hit(helper.absolutePos(new BlockPos(5, 1, 5)), Direction.UP);
        BuilderMix.Table table = Objects.requireNonNull(BuilderMix.forWand(stack, BuilderMode.FILL), "混铺表应生效");
        BuilderArea area = BuilderJobs.areaOf(player, stack, BuilderShape.WALL, hit);
        expectMixMatches(helper, player, stack, table, area, () -> BuilderJobs.request(player, InteractionHand.MAIN_HAND, hit, true), 20);
    }

    /** 混铺在沿面上：按服务端同一套测量算出的范围，确认扫出的各种格数、铺出来的每格都和表算的一样 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderMixOnFace(GameTestHelper helper) {
        floor(helper, Blocks.SMOOTH_STONE, 2, 7, 1, 2, 7);
        ItemStack stack = wand(BuilderMode.FILL, BuilderShape.FACE, null, BuilderModules.Kind.MIX);
        BuilderMix.write(stack, List.of(new BuilderMix.Entry(Blocks.COBBLESTONE, 1), new BuilderMix.Entry(Blocks.OAK_PLANKS, 1)), 4242);
        ServerPlayer player = flyer(helper, stack, 4, 5, 4);
        player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 64));
        BuilderShapes.Hit hit = new BuilderShapes.Hit(helper.absolutePos(new BlockPos(4, 1, 4)), Direction.UP);
        BuilderMix.Table table = Objects.requireNonNull(BuilderMix.forWand(stack, BuilderMode.FILL), "混铺表应生效");
        BuilderTier.Stats stats = BuilderTier.stats(stack);
        BuilderArea area = BuilderSurface.scanAll(BuilderSurface.reader(helper.getLevel()), hit.pos(), hit.face(), stats.maxVolume(),
                BuilderSurface.radius(stats.maxSide())).area();
        helper.assertTrue(area.size() == 36, "测出 36 格：" + area.size());
        expectMixMatches(helper, player, stack, table, area, () -> BuilderJobs.request(player, InteractionHand.MAIN_HAND, hit, true), 36);
    }

    private static void expectMixMatches(GameTestHelper helper, ServerPlayer player, ItemStack stack, BuilderMix.Table table, BuilderArea area,
                                         java.util.function.Supplier<BuilderJobs.StartResult> start, int cells) {
        long seed = BuilderMix.seedFor(table, area);
        int[] want = new int[table.size()];
        for (int i = 0; i < area.size(); i++) {
            BlockPos pos = area.cellAt(i, true);
            Block block = BuilderMix.blockAt(table, seed, area.box(), pos);
            for (int k = 0; k < table.size(); k++) {
                if (table.block(k) == block) {
                    want[k]++;
                }
            }
        }
        helper.assertTrue(want[0] > 0 && want[1] > 0 && want[0] + want[1] == cells, "两种都该出现：" + Arrays.toString(want));
        BuilderJobs.Plan plan = BuilderJobs.plan(player, stack, BuilderMode.FILL, area,
                BuilderJobs.chooseBlock(player, InteractionHand.MAIN_HAND, stack, BuilderMode.FILL), table);
        helper.assertTrue(plan.place() == cells && Arrays.equals(plan.need(), want), "确认扫出的同预览：" + Arrays.toString(plan.need())
                + " / " + Arrays.toString(want));
        BuilderJobs.StartResult result = start.get();
        helper.assertTrue(result == BuilderJobs.StartResult.STARTED || result == BuilderJobs.StartResult.SURVEYING, "开工：" + result);
        UUID id = player.getUUID();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在施工"))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    for (int i = 0; i < area.size(); i++) {
                        BlockPos pos = area.cellAt(i, true);
                        Block expect = BuilderMix.blockAt(table, seed, area.box(), pos);
                        helper.assertTrue(level.getBlockState(pos).is(expect), "第 " + i + " 格应是 " + expect + "，实际 " + level.getBlockState(pos));
                    }
                    helper.assertTrue(count(player, Items.COBBLESTONE) == 64 - want[0] && count(player, Items.OAK_PLANKS) == 64 - want[1],
                            "各种用掉的料等于放下的格数");
                })
                .thenSucceed();
    }
}
