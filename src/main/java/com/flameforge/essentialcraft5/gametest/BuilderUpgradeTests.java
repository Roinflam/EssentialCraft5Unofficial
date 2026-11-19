package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.item.BuilderActions;
import com.flameforge.essentialcraft5.item.BuilderArea;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMaterials;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderShapes;
import com.flameforge.essentialcraft5.item.BuilderStatus;
import com.flameforge.essentialcraft5.item.BuilderUndo;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.network.C2SBuilderActionPacket;
import com.flameforge.essentialcraft5.network.S2CBuilderStatusPacket;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 创世神之杖第 1 批升级（tools/plan/builder_upgrade.md §6.4）的 GameTest：铺面 / 直线的几何和格子顺序、副手材料、随身容器取料
 * （{@link BuilderTestContainers} 的假容器走的是游戏里背包类容器同一条通用路径）、取料和放置之间不丢料不多料、二次确认、撤销规则、
 * 耗时预算、排队、暂停、先探后放、禁用维度、悬空的会掉落的方块、死亡 / 换维度 / 下线时的收尾、C2S 操作包的限频和夹值。
 * 要改全局覆盖值的测试各放一个 batch（同 batch 的测试同时跑），{@code @BeforeBatch} 设、{@code @AfterBatch} 置回。
 * 魔杖的玩家用 {@link ProtectionFrameworkTests#realPlayer}（不进世界），飞在空中时 onGround 为 false，铺面按「脚下一层」= floor(脚底) − 1。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BuilderUpgradeTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    static final String BATCH = "ec5_builder_up";
    static final String TIME_BATCH = "ec5_builder_up_time";
    static final String QUEUE_BATCH = "ec5_builder_up_queue";
    static final String PAUSE_BATCH = "ec5_builder_up_pause";
    static final String NOPROBE_BATCH = "ec5_builder_up_noprobe";
    static final String PROBE_WAIT_BATCH = "ec5_builder_up_probewait";
    static final String STREAK_OFF_BATCH = "ec5_builder_up_streakoff";
    /**
     * 连续被拒那两条各占一批：1 × 20 的线伸出 11 格的场地，和同批并排的测试摆在一起会落进邻居的格子
     * （邻居先放下的木板让这边少做几格），一批只放一条就没有邻居
     */
    static final String STREAK_STOP_BATCH = "ec5_builder_up_streakstop";
    static final String STREAK_RESET_BATCH = "ec5_builder_up_streakreset";
    /** 先探后放那条 1 × 20 的线同样伸出场地，单放一批（理由同上） */
    static final String PROBE_LINE_BATCH = "ec5_builder_up_probeline";
    private static final int FULL = MRUStaffItem.MAX_MRU;

    private BuilderUpgradeTests() {
    }

    private static void before() {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.Test.materialPauseSeconds = 0;
        ECBuilder.Config.Test.maxJobsTotal = 256;
        BuilderTestContainers.enabled = true;
    }

    private static void after() {
        BuilderTestContainers.enabled = false;
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
        BuilderJobs.clearAll();
    }

    @BeforeBatch(batch = BATCH)
    public static void builderUpBefore(ServerLevel level) {
        before();
    }

    @AfterBatch(batch = BATCH)
    public static void builderUpAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = TIME_BATCH)
    public static void builderUpTimeBefore(ServerLevel level) {
        before();
        ECBuilder.Config.Test.maxMillisPerTick = 0D;
    }

    @AfterBatch(batch = TIME_BATCH)
    public static void builderUpTimeAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = QUEUE_BATCH)
    public static void builderUpQueueBefore(ServerLevel level) {
        before();
        ECBuilder.Config.Test.maxJobsTotal = 1;
    }

    @AfterBatch(batch = QUEUE_BATCH)
    public static void builderUpQueueAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = PAUSE_BATCH)
    public static void builderUpPauseBefore(ServerLevel level) {
        before();
        ECBuilder.Config.Test.materialPauseSeconds = 2;
    }

    @AfterBatch(batch = PAUSE_BATCH)
    public static void builderUpPauseAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = STREAK_OFF_BATCH)
    public static void builderUpStreakOffBefore(ServerLevel level) {
        before();
        ECBuilder.Config.Test.deniedStreakLimit = 0;
    }

    @AfterBatch(batch = STREAK_OFF_BATCH)
    public static void builderUpStreakOffAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = STREAK_STOP_BATCH)
    public static void builderUpStreakStopBefore(ServerLevel level) {
        before();
    }

    @AfterBatch(batch = STREAK_STOP_BATCH)
    public static void builderUpStreakStopAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = STREAK_RESET_BATCH)
    public static void builderUpStreakResetBefore(ServerLevel level) {
        before();
    }

    @AfterBatch(batch = STREAK_RESET_BATCH)
    public static void builderUpStreakResetAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = PROBE_LINE_BATCH)
    public static void builderUpProbeLineBefore(ServerLevel level) {
        before();
    }

    @AfterBatch(batch = PROBE_LINE_BATCH)
    public static void builderUpProbeLineAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = NOPROBE_BATCH)
    public static void builderUpNoProbeBefore(ServerLevel level) {
        before();
        ECBuilder.Config.Test.probeAfterDenied = false;
    }

    @AfterBatch(batch = NOPROBE_BATCH)
    public static void builderUpNoProbeAfter(ServerLevel level) {
        after();
    }

    /** 每名玩家每刻只给 1 格新探测：测试每刻先把这 1 格用掉，魔杖的探测就一直拿到 UNKNOWN（跨刻，所以单放一个 batch） */
    @BeforeBatch(batch = PROBE_WAIT_BATCH)
    public static void builderUpProbeWaitBefore(ServerLevel level) {
        before();
        ECProtection.Config.setMaxProbesPerPlayerTickForTest(1);
    }

    @AfterBatch(batch = PROBE_WAIT_BATCH)
    public static void builderUpProbeWaitAfter(ServerLevel level) {
        ECProtection.Config.setMaxProbesPerPlayerTickForTest(null);
        after();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static ItemStack wand(BuilderMode mode, int mru) {
        ItemStack stack = new ItemStack(ECBuilder.item(mode));
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    /** 铺面 / 直线形状、选定 block 的魔杖 */
    private static ItemStack shaped(BuilderMode mode, BuilderShape shape, int width, int length, @Nullable Block block) {
        ItemStack stack = wand(mode, FULL);
        BuilderSelection.setShape(stack, shape);
        BuilderSelection.setSize(stack, shape, width, length);
        if (block != null) {
            BuilderSelection.setStoredBlock(stack, ForgeRegistries.BLOCKS.getKey(block));
        }
        return stack;
    }

    /** 两点形状、两个点和选定方块都设好的魔杖（相对坐标） */
    private static ItemStack points(GameTestHelper helper, BuilderMode mode, BlockPos a, BlockPos b, @Nullable Block block) {
        ItemStack stack = wand(mode, FULL);
        BuilderSelection.setFirst(stack, helper.absolutePos(a), helper.getLevel().dimension());
        BuilderSelection.setSecond(stack, helper.absolutePos(b));
        if (block != null) {
            BuilderSelection.setStoredBlock(stack, ForgeRegistries.BLOCKS.getKey(block));
        }
        return stack;
    }

    /** 主手拿着 wand、飞在相对 (x + 0.5, y, z + 0.5) 的真玩家，面朝南（铺面从脚下往 +z 长、右手是 −x） */
    private static ServerPlayer flyer(GameTestHelper helper, ItemStack wand, int x, int y, int z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-builder");
        Vec3 at = helper.absoluteVec(new Vec3(x + 0.5D, y, z + 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setOnGround(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        return player;
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (BuilderMaterials.isPlainMaterial(stack, item)) {
                n += stack.getCount();
            }
        }
        ItemStack offhand = player.getInventory().offhand.get(0);
        return BuilderMaterials.isPlainMaterial(offhand, item) ? n + offhand.getCount() : n;
    }

    private static int countIn(IItemHandler handler, Item item) {
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (BuilderMaterials.isPlainMaterial(stack, item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static int countBox(GameTestHelper helper, BlockPos a, BlockPos b, Block block) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
            if (helper.getBlockState(pos.immutable()).is(block)) {
                n++;
            }
        }
        return n;
    }

    private static void startOk(GameTestHelper helper, ServerPlayer player) {
        BuilderJobs.StartResult result = BuilderJobs.start(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result == BuilderJobs.StartResult.STARTED, "应能开工，实际 " + result);
    }

    private static BuilderJobs.Result result(GameTestHelper helper, ServerPlayer player) {
        BuilderJobs.Result result = BuilderJobs.lastResult(player.getUUID());
        helper.assertTrue(result != null, "应有施工结果");
        return result;
    }

    private static void expectResult(GameTestHelper helper, ServerPlayer player, BuilderJobs.StopReason reason, int done) {
        BuilderJobs.Result result = result(helper, player);
        helper.assertTrue(result.reason() == reason && result.done() == done,
                "结果应是 " + reason + " / " + done + "，实际 " + result.reason() + " / " + result.done());
    }

    /** 背包第 slot 格放一个测试容器，里面第 0 格放 contents */
    private static ItemStack container(Item kind, ItemStack contents) {
        ItemStack stack = new ItemStack(kind);
        if (!contents.isEmpty()) {
            BuilderTestContainers.handler(stack).insertItem(0, contents, false);
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // 几何（纯函数）
    // ------------------------------------------------------------------

    /**
     * 铺面四种定位、直线、偶数尺寸多出的一格往哪边、格子顺序（第一格是锚点、按圈不减、是排列）、夹尺寸、老魔杖 NBT 当两点、尺寸按形状分开存
     */
    @GameTest(template = EMPTY)
    public static void builderShapeGeometry(GameTestHelper helper) {
        BlockPos hit = new BlockPos(5, 1, 5);
        BuilderShapes.Size size34 = new BuilderShapes.Size(3, 4);
        BuilderArea top = BuilderShapes.area(BuilderShape.PLANE, size34, Direction.NORTH, new BuilderShapes.Hit(hit, Direction.UP), BlockPos.ZERO);
        helper.assertTrue(top.box().equals(new BoundingBox(4, 2, 3, 6, 2, 6)), "顶面铺面（朝北、宽 3 长 4）外框应是 (4,2,3)~(6,2,6)，实际 " + top.box());
        helper.assertTrue(top.size() == 12 && top.cellAt(0, true).equals(new BlockPos(5, 2, 5)), "12 格、第一格是点中格上方");
        BuilderArea bottom = BuilderShapes.area(BuilderShape.PLANE, size34, Direction.NORTH, new BuilderShapes.Hit(hit, Direction.DOWN), BlockPos.ZERO);
        helper.assertTrue(bottom.box().minY() == 0 && bottom.box().maxY() == 0, "底面铺在下方那层");
        BuilderArea side = BuilderShapes.area(BuilderShape.PLANE, size34, Direction.NORTH, new BuilderShapes.Hit(hit, Direction.EAST), BlockPos.ZERO);
        helper.assertTrue(side.box().equals(new BoundingBox(6, 1, 4, 9, 1, 6)), "东侧面沿法线往外长 4、横向居中，实际 " + side.box());
        BuilderArea feet = BuilderShapes.area(BuilderShape.PLANE, new BuilderShapes.Size(2, 3), Direction.SOUTH, null, new BlockPos(0, 10, 0));
        helper.assertTrue(feet.box().equals(new BoundingBox(-1, 10, 0, 0, 10, 2)), "脚下铺面（朝南、宽 2）：多出的一格在右手（−x），实际 " + feet.box());
        BuilderArea line = BuilderShapes.area(BuilderShape.LINE, new BuilderShapes.Size(1, 5), Direction.EAST, new BuilderShapes.Hit(hit, Direction.UP), BlockPos.ZERO);
        helper.assertTrue(line.box().equals(new BoundingBox(5, 2, 5, 9, 2, 5)), "直线沿朝向长 5，实际 " + line.box());
        BuilderArea even = BuilderShapes.area(BuilderShape.PLANE, new BuilderShapes.Size(1, 4), Direction.SOUTH, new BuilderShapes.Hit(hit, Direction.UP), BlockPos.ZERO);
        helper.assertTrue(even.box().minZ() == 4 && even.box().maxZ() == 7, "偶数长以点中格为中心时多出的一格在前方（朝南 = +z），实际 " + even.box());

        BuilderArea big = BuilderShapes.area(BuilderShape.PLANE, new BuilderShapes.Size(9, 9), Direction.WEST, new BuilderShapes.Hit(hit, Direction.UP), BlockPos.ZERO);
        Set<BlockPos> seen = new HashSet<>();
        int lastRing = -1;
        for (int i = 0; i < big.size(); i++) {
            BlockPos pos = big.cellAt(i, true);
            seen.add(pos);
            int ring = Math.max(Math.abs(pos.getX() - 5), Math.abs(pos.getZ() - 5));
            helper.assertTrue(ring >= lastRing, "格子应一圈圈往外：第 " + i + " 格回到了第 " + ring + " 圈");
            lastRing = ring;
        }
        helper.assertTrue(seen.size() == 81 && big.cellAt(0, true).equals(new BlockPos(5, 2, 5)), "81 格各不相同、从锚点开始");

        helper.assertTrue(BuilderShapes.clamp(100, 100, 48, 4096).equals(new BuilderShapes.Size(48, 48)), "边长夹到 48");
        helper.assertTrue(BuilderShapes.clamp(64, 64, 64, 256).equals(new BuilderShapes.Size(64, 4)), "格数超了先缩长");
        helper.assertTrue(BuilderShapes.clamp(0, -3, 48, 4096).equals(new BuilderShapes.Size(1, 1)), "最小 1 × 1");

        ItemStack old = wand(BuilderMode.FILL, FULL);
        BuilderSelection.setFirst(old, helper.absolutePos(BlockPos.ZERO), helper.getLevel().dimension());
        helper.assertTrue(BuilderSelection.shape(old) == BuilderShape.POINTS, "没有形状键的老魔杖是两点");
        int[] defaults = BuilderSelection.size(old, BuilderShape.PLANE);
        helper.assertTrue(defaults[0] == 5 && defaults[1] == 5, "铺面默认 5 × 5");
        BuilderSelection.setSize(old, BuilderShape.PLANE, 7, 9);
        int[] line18 = BuilderSelection.size(old, BuilderShape.LINE);
        helper.assertTrue(line18[0] == 1 && line18[1] == 8, "改铺面尺寸不影响直线（默认宽 1 长 8）");
        helper.assertTrue(BuilderSelection.size(old, BuilderShape.PLANE)[1] == 9, "铺面尺寸写进去了");
        helper.succeed();
    }

    /** 撤销的格子比对：同一方块、整数属性和台阶类型都没变才算没变；方向、连接不比 */
    @GameTest(template = EMPTY)
    public static void builderUndoUnchangedRule(GameTestHelper helper) {
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        helper.assertTrue(BuilderUndo.unchanged(slab, slab), "原样");
        helper.assertTrue(!BuilderUndo.unchanged(slab.setValue(SlabBlock.TYPE, SlabType.DOUBLE), slab), "补成双层算变了");
        BlockState snow = Blocks.SNOW.defaultBlockState();
        helper.assertTrue(!BuilderUndo.unchanged(snow.setValue(SnowLayerBlock.LAYERS, 2), snow), "雪层多了算变了");
        BlockState wheat = Blocks.WHEAT.defaultBlockState();
        helper.assertTrue(!BuilderUndo.unchanged(wheat.setValue(CropBlock.AGE, 3), wheat), "作物长了算变了");
        helper.assertTrue(!BuilderUndo.unchanged(Blocks.EXPOSED_COPPER.defaultBlockState(), Blocks.COPPER_BLOCK.defaultBlockState()), "氧化了算变了");
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
        helper.assertTrue(BuilderUndo.unchanged(stairs.setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.EAST), stairs), "方向不比");
        BlockState wire = Blocks.REDSTONE_WIRE.defaultBlockState();
        helper.assertTrue(BuilderUndo.unchanged(wire.setValue(BlockStateProperties.POWER, 9), wire), "红石信号强度会自己变，不比");
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.DISTANCE, 7);
        helper.assertTrue(BuilderUndo.unchanged(leaves.setValue(BlockStateProperties.DISTANCE, 1), leaves), "树叶距离不比");
        BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.DOWN, true);
        helper.assertTrue(BuilderUndo.unchanged(lichen, lichen), "多面方块原样");
        helper.assertTrue(!BuilderUndo.unchanged(lichen.setValue(BlockStateProperties.NORTH, true), lichen), "多面方块多贴了一面算变了");
        helper.succeed();
    }

    /** 配了 max_hardness 时更硬的格子算受保护；默认不限 */
    @GameTest(template = EMPTY)
    public static void builderMaxHardness(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.OBSIDIAN);
        BlockPos abs = helper.absolutePos(pos);
        helper.assertTrue(BuilderJobs.classify(helper.getLevel(), abs, helper.getLevel().getBlockState(abs)) == BuilderMode.CellKind.OTHER, "默认不限硬度");
        Double saved = ECBuilder.Config.Test.maxHardness;
        try {
            ECBuilder.Config.Test.maxHardness = 10D;
            helper.assertTrue(BuilderJobs.classify(helper.getLevel(), abs, helper.getLevel().getBlockState(abs)) == BuilderMode.CellKind.PROTECTED, "黑曜石超过 10");
            helper.setBlock(pos, Blocks.STONE);
            helper.assertTrue(BuilderJobs.classify(helper.getLevel(), abs, helper.getLevel().getBlockState(abs)) == BuilderMode.CellKind.OTHER, "石头照常");
        } finally {
            ECBuilder.Config.Test.maxHardness = saved;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 铺面、副手、随身容器
    // ------------------------------------------------------------------

    /** 飞在空中右键空气：脚下一层铺 3 × 3（朝南从脚下往前长），用掉 9 块木板 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderPlaneFillsFromFeet(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 20));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    expectResult(helper, player, BuilderJobs.StopReason.FINISHED, 9);
                    helper.assertTrue(countBox(helper, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), Blocks.OAK_PLANKS) == 9, "脚下一层 (1..3, 3, 2..4) 应铺满");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 11, "用掉 9 块，剩 " + count(player, Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    /** 右键方块顶面（走 onItemUseFirst + 确认门槛，小单不用确认）：铺在上面那层，以点中格为中心 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderPlaneOnBlockFace(GameTestHelper helper) {
        BlockPos clicked = new BlockPos(5, 1, 5);
        helper.setBlock(clicked, Blocks.STONE);
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 9));
        BlockPos abs = helper.absolutePos(clicked);
        stack.getItem().onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
        helper.assertTrue(BuilderJobs.isRunning(player.getUUID()), "右键方块顶面应直接开工");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    expectResult(helper, player, BuilderJobs.StopReason.FINISHED, 9);
                    helper.assertTrue(countBox(helper, new BlockPos(4, 2, 4), new BlockPos(6, 2, 6), Blocks.OAK_PLANKS) == 9, "点中格上方 3 × 3 应铺满");
                })
                .thenSucceed();
    }

    /** 副手拿着木板：用木板不用选定的石头；先用背包里的，副手那叠留着 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderOffhandMaterialFirst(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.STONE);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_PLANKS, 5));
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 10));
        player.getInventory().items.set(2, new ItemStack(Items.STONE, 64));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    expectResult(helper, player, BuilderJobs.StopReason.FINISHED, 9);
                    helper.assertTrue(countBox(helper, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), Blocks.OAK_PLANKS) == 9, "铺的应是副手的木板");
                    helper.assertTrue(count(player, Items.STONE) == 64, "选定的石头不动");
                    helper.assertTrue(player.getOffhandItem().getCount() == 5 && player.getInventory().items.get(1).getCount() == 1,
                            "先用背包里的 9 块，副手 5 块留着，实际副手 " + player.getOffhandItem().getCount());
                })
                .thenSucceed();
    }

    /**
     * 材料从背包、随身容器来时，放置事件那一刻报给插件的那只手（副手）临时拿着 1 个这种材料，事件发完换回副手原来的剑；
     * 九格的材料一个不多一个不少（混合端按这只手报放置用的物品，按手里物品判断的插件看到的和玩家亲手放置相同）
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderPlaceShowsMaterialInHand(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.STONE);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        player.setItemInHand(InteractionHand.OFF_HAND, sword);
        player.getInventory().items.set(1, new ItemStack(Items.STONE, 5));
        ItemStack bag = container(Items.BUNDLE, new ItemStack(Items.STONE, 4));
        player.getInventory().items.set(2, bag);
        List<String> seen = new ArrayList<>();
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getEntity() == player) {
                ItemStack hand = player.getOffhandItem();
                seen.add(hand.is(Items.STONE) && hand.getCount() == 1 && !hand.hasTag() ? "stone" : String.valueOf(hand));
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    MinecraftForge.EVENT_BUS.unregister(listener);
                    expectResult(helper, player, BuilderJobs.StopReason.FINISHED, 9);
                    helper.assertTrue(seen.size() == 9 && seen.stream().allMatch("stone"::equals), "九次放置事件里副手都是 1 个石头：" + seen);
                    helper.assertTrue(player.getOffhandItem() == sword && sword.getCount() == 1, "副手换回原来那把剑：" + player.getOffhandItem());
                    helper.assertTrue(count(player, Items.STONE) == 0 && countIn(BuilderTestContainers.handler(bag), Items.STONE) == 0,
                            "背包 5 块、袋子 4 块都用掉");
                    helper.assertTrue(countBox(helper, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), Blocks.STONE) == 9, "放下 9 格");
                })
                .thenSucceed();
    }

    /**
     * 放置事件里玩家下线（插件把人踢掉）：下线存盘之前副手已经换回原来的剑，临时那 1 个材料不留在身上；
     * 任务随下线结束，放下的格数 + 剩下的料 = 原来的料
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderHandSwapRestoredOnLogout(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.STONE);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        player.setItemInHand(InteractionHand.OFF_HAND, sword);
        player.getInventory().items.set(1, new ItemStack(Items.STONE, 9));
        ItemStack[] atLogout = {null};
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getEntity() == player && atLogout[0] == null) {
                MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                atLogout[0] = player.getOffhandItem();
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(atLogout[0] != null && !BuilderJobs.isRunning(player.getUUID()), "还没下线或还在施工"))
                .thenExecute(() -> {
                    MinecraftForge.EVENT_BUS.unregister(listener);
                    helper.assertTrue(atLogout[0] == sword, "下线那一刻副手已是原来的剑：" + atLogout[0]);
                    helper.assertTrue(player.getOffhandItem() == sword, "之后副手仍是那把剑");
                    int placed = countBox(helper, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), Blocks.STONE);
                    helper.assertTrue(placed + count(player, Items.STONE) == 9, "放下的 " + placed + " 格 + 剩下的料应是 9");
                })
                .thenSucceed();
    }

    /**
     * 随身容器取料的顺序和范围：背包里的 2 块先用，再用背包里的容器（6 块），再用胸甲位的容器（取 1 块）；
     * 叠了 2 个的容器、容器里的容器都不碰
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderContainerMaterial(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 2));
        ItemStack bag = container(Items.BUNDLE, new ItemStack(Items.OAK_PLANKS, 6));
        player.getInventory().items.set(2, bag);
        ItemStack stacked = container(Items.PAPER, new ItemStack(Items.OAK_PLANKS, 64));
        stacked.setCount(2);
        player.getInventory().items.set(3, stacked);
        ItemStack inner = container(Items.BUNDLE, new ItemStack(Items.OAK_PLANKS, 64));
        ItemStack outer = container(Items.BUNDLE, inner);
        player.getInventory().items.set(4, outer);
        ItemStack armorBag = container(Items.BUNDLE, new ItemStack(Items.OAK_PLANKS, 6));
        player.getInventory().armor.set(2, armorBag);

        long[] counted = BuilderMaterials.count(player, Items.OAK_PLANKS, stack);
        helper.assertTrue(counted[0] == 2 && counted[1] == 12, "数量：身上 2、容器里 12（不数叠着的和套着的），实际 " + counted[0] + " / " + counted[1]);
        Boolean saved = ECBuilder.Config.Test.allowContainerItems;
        try {
            ECBuilder.Config.Test.allowContainerItems = false;
            helper.assertTrue(BuilderMaterials.count(player, Items.OAK_PLANKS, stack)[1] == 0, "allow_container_items 关掉时不数容器");
        } finally {
            ECBuilder.Config.Test.allowContainerItems = saved;
        }

        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    expectResult(helper, player, BuilderJobs.StopReason.FINISHED, 9);
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 0, "背包里的先用完");
                    helper.assertTrue(countIn(BuilderTestContainers.handler(bag), Items.OAK_PLANKS) == 0, "背包里的容器用完");
                    helper.assertTrue(countIn(BuilderTestContainers.handler(armorBag), Items.OAK_PLANKS) == 5, "胸甲位的容器只取 1 块");
                    helper.assertTrue(countIn(BuilderTestContainers.handler(stacked), Items.OAK_PLANKS) == 64, "叠了 2 个的容器不碰");
                    helper.assertTrue(countIn(BuilderTestContainers.handler(inner), Items.OAK_PLANKS) == 64, "容器里的容器不碰");
                })
                .thenSucceed();
    }

    /** 材料只在容器里、领地拒绝 2 格：那 2 块原样回到容器，放下 7 格；容器剩 + 放下 = 原来的 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderContainerNoDupeOnDeny(GameTestHelper helper) {
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(new BlockPos(1, 3, 4)).deny(new BlockPos(3, 3, 3));
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        ItemStack bag = container(Items.BUNDLE, new ItemStack(Items.OAK_PLANKS, 9));
        player.getInventory().items.set(1, bag);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    int placed = countBox(helper, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), Blocks.OAK_PLANKS);
                    int left = countIn(BuilderTestContainers.handler(bag), Items.OAK_PLANKS) + count(player, Items.OAK_PLANKS);
                    helper.assertTrue(placed == 7, "放下 7 格，实际 " + placed);
                    helper.assertTrue(placed + left == 9, "放下的 + 剩下的应等于原来的 9 块，实际 " + placed + " + " + left);
                    helper.assertTrue(countIn(BuilderTestContainers.handler(bag), Items.OAK_PLANKS) == 2, "被拒的 2 块回到容器里");
                    helper.assertTrue(result(helper, player).denied() == 2, "被拒 2 格");
                })
                .thenSucceed();
    }

    /**
     * 处理器拒绝取出、取出给错东西（带了 NBT）：不放方块、东西不丢（给错的那块塞不回原格就进背包），换下一个容器接着用
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderContainerBadHandler(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        ItemStack refusing = container(Items.WRITABLE_BOOK, new ItemStack(Items.OAK_PLANKS, 9));
        ItemStack tampering = container(Items.KNOWLEDGE_BOOK, new ItemStack(Items.OAK_PLANKS, 9));
        ItemStack good = container(Items.BUNDLE, new ItemStack(Items.OAK_PLANKS, 9));
        player.getInventory().items.set(1, refusing);
        player.getInventory().items.set(2, tampering);
        player.getInventory().items.set(3, good);
        long[] counted = BuilderMaterials.count(player, Items.OAK_PLANKS, stack);
        helper.assertTrue(counted[1] == 9, "取不出的两只容器不算持有，只数正常的 9 块，实际 " + counted[1]);
        int mruBefore = MRUStaffItem.storedMRU(stack);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    expectResult(helper, player, BuilderJobs.StopReason.FINISHED, 9);
                    int placed = countBox(helper, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), Blocks.OAK_PLANKS);
                    int tamperedInBag = 0;
                    for (ItemStack item : player.getInventory().items) {
                        if (item.is(Items.OAK_PLANKS) && item.hasTag()) {
                            tamperedInBag += item.getCount();
                        }
                    }
                    int total = placed + countIn(BuilderTestContainers.handler(refusing), Items.OAK_PLANKS)
                            + countIn(BuilderTestContainers.handler(tampering), Items.OAK_PLANKS) + countIn(BuilderTestContainers.handler(good), Items.OAK_PLANKS)
                            + count(player, Items.OAK_PLANKS) + tamperedInBag;
                    helper.assertTrue(total == 27, "三个容器共 27 块，放下 + 各处剩下的应还是 27，实际 " + total);
                    helper.assertTrue(countIn(BuilderTestContainers.handler(refusing), Items.OAK_PLANKS) == 9, "拒绝取出的容器原样");
                    helper.assertTrue(countIn(BuilderTestContainers.handler(good), Items.OAK_PLANKS) == 0, "正常的容器用了 9 块");
                    helper.assertTrue(MRUStaffItem.storedMRU(stack) == mruBefore - 9 * BuilderMode.PLACE_COST, "只按放下的 9 格扣 MRU");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 确认、撤销
    // ------------------------------------------------------------------

    /** 材料不够：第一次只提示、什么都不放；同一单再点一次开工；换了范围要重新确认 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderConfirmShortMaterial(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 2));
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, true) == BuilderJobs.StartResult.NEEDS_CONFIRM, "材料不够应先要确认");
        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()) && countBox(helper, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), Blocks.OAK_PLANKS) == 0, "确认前什么都不放");
        BuilderSelection.setSize(stack, BuilderShape.PLANE, 2, 3);
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, true) == BuilderJobs.StartResult.NEEDS_CONFIRM, "换了范围要重新确认");
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, true) == BuilderJobs.StartResult.STARTED, "同一单再点一次开工");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> expectResult(helper, player, BuilderJobs.StopReason.OUT_OF_MATERIAL, 2))
                .thenSucceed();
    }

    /**
     * 两点形状、两个点都已设置：有一单在等确认时点方块就是确认（开工，两个点不变）；施工中再点方块回「正在施工」，不提示两个点都已设置
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderPointsBlockClickConfirms(GameTestHelper helper) {
        ItemStack stack = points(helper, BuilderMode.FILL, new BlockPos(1, 3, 1), new BlockPos(3, 3, 3), Blocks.OAK_PLANKS);
        BuilderSelection.setShape(stack, BuilderShape.POINTS);
        ServerPlayer player = flyer(helper, stack, 2, 5, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 2));
        BlockPos first = BuilderSelection.first(stack);
        BlockPos second = BuilderSelection.second(stack);
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, true) == BuilderJobs.StartResult.NEEDS_CONFIRM, "材料不够应先要确认");
        helper.assertTrue(BuilderJobs.awaitingConfirm(player, stack), "应在等确认");
        BlockPos ground = helper.absolutePos(new BlockPos(4, 1, 4));
        UseOnContext click = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false));
        helper.assertTrue(stack.getItem().onItemUseFirst(stack, click) == InteractionResult.CONSUME, "等确认时点方块应接下");
        helper.assertTrue(BuilderJobs.isRunning(player.getUUID()) && !BuilderJobs.awaitingConfirm(player, stack), "点方块应当作确认开工");
        helper.assertTrue(first.equals(BuilderSelection.first(stack)) && second.equals(BuilderSelection.second(stack)), "确认那一下不改两个点");
        helper.assertTrue(stack.getItem().onItemUseFirst(stack, click) == InteractionResult.CONSUME, "施工中点方块应接下");
        helper.assertTrue(first.equals(BuilderSelection.first(stack)) && second.equals(BuilderSelection.second(stack)), "施工中点方块不改两个点");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> expectResult(helper, player, BuilderJobs.StopReason.OUT_OF_MATERIAL, 2))
                .thenSucceed();
    }

    /** 等确认的是另一根铺面魔杖那一单时，拿两点魔杖点方块不算确认，不开工 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 100)
    public static void builderPointsBlockClickOtherPending(GameTestHelper helper) {
        ItemStack plane = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, plane, 2, 5, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 2));
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, true) == BuilderJobs.StartResult.NEEDS_CONFIRM, "铺面那一单应先要确认");
        ItemStack stack = points(helper, BuilderMode.FILL, new BlockPos(1, 3, 1), new BlockPos(3, 3, 3), Blocks.OAK_PLANKS);
        BuilderSelection.setShape(stack, BuilderShape.POINTS);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.assertTrue(!BuilderJobs.awaitingConfirm(player, stack), "等着的不是两点魔杖这一单");
        BlockPos ground = helper.absolutePos(new BlockPos(4, 1, 4));
        UseOnContext click = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false));
        stack.getItem().onItemUseFirst(stack, click);
        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "点方块不应开工");
        helper.assertTrue(countBox(helper, new BlockPos(1, 3, 1), new BlockPos(3, 3, 3), Blocks.OAK_PLANKS) == 0, "什么都不放");
        helper.succeed();
    }

    /**
     * 取不出的料不算持有：料全在拒绝取出的容器里时，开工前按「料不够」要确认；同样的料放进正常容器就不要确认。
     * 顺带量一下数容器的开销（每只容器多一次模拟取出），写进日志「[builder-cost]」，不按耗时判对错。
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderLockedContainerNotCounted(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, container(Items.WRITABLE_BOOK, new ItemStack(Items.OAK_PLANKS, 9)));
        helper.assertTrue(BuilderMaterials.count(player, Items.OAK_PLANKS, stack)[1] == 0, "拒绝取出的容器不算持有");
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, true) == BuilderJobs.StartResult.NEEDS_CONFIRM,
                "料全取不出，应先按料不够要确认");
        // 换一名玩家（上面那单留着待确认，同一单再点会算确认）：料放进正常容器
        ItemStack okWand = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer ok = flyer(helper, okWand, 7, 4, 2);
        ok.getInventory().items.set(1, container(Items.BUNDLE, new ItemStack(Items.OAK_PLANKS, 9)));
        helper.assertTrue(BuilderMaterials.count(ok, Items.OAK_PLANKS, okWand)[1] == 9, "正常容器里的 9 块算持有");
        helper.assertTrue(BuilderJobs.request(ok, InteractionHand.MAIN_HAND, null, true) == BuilderJobs.StartResult.STARTED, "料够，直接开工");
        BuilderJobs.stop(ok);

        // 开销：背包 27 格各放一只 9 格容器、每只都有料，数 2000 次
        ServerPlayer heavy = flyer(helper, shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS), 7, 4, 7);
        for (int slot = 9; slot < 36; slot++) {
            ItemStack bag = container(Items.BUNDLE, ItemStack.EMPTY);
            for (int i = 0; i < 9; i++) {
                BuilderTestContainers.handler(bag).insertItem(i, new ItemStack(Items.OAK_PLANKS, 64), false);
            }
            heavy.getInventory().items.set(slot, bag);
        }
        ItemStack heavyWand = heavy.getMainHandItem();
        long expected = 27L * 9 * 64;
        helper.assertTrue(BuilderMaterials.count(heavy, Items.OAK_PLANKS, heavyWand)[1] == expected, "27 只容器都算");
        int rounds = 2000;
        long started = System.nanoTime();
        long sink = 0;
        for (int round = 0; round < rounds; round++) {
            sink += BuilderMaterials.count(heavy, Items.OAK_PLANKS, heavyWand)[1];
        }
        long perCall = (System.nanoTime() - started) / rounds;
        EssentialCraft5.LOGGER.info("[builder-cost] count() with 27 containers x 9 slots: {} ns per call ({} rounds, checksum {})", perCall, rounds, sink);
        helper.succeed();
    }

    /**
     * 开工前确认和准星下同一个口径：3 × 3 里一格被盔甲架挡着，算放不下、不算进要放的格数，身上 8 块就够、直接开工；
     * 只有 7 块时才按料不够要确认
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderConfirmCountsLikePreview(GameTestHelper helper) {
        net.minecraft.world.entity.decoration.ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new Vec3(2.5D, 3D, 3.5D));
        stand.setNoGravity(true);
        ItemStack shortWand = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer shortOne = flyer(helper, shortWand, 2, 4, 2);
        shortOne.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 7));
        helper.assertTrue(BuilderJobs.request(shortOne, InteractionHand.MAIN_HAND, null, true) == BuilderJobs.StartResult.NEEDS_CONFIRM,
                "7 块不够 8 格，应先要确认");
        ItemStack wand = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, wand, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 8));
        helper.assertTrue(BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, true) == BuilderJobs.StartResult.STARTED,
                "挡着的那格不算要放，8 块够，应直接开工");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(countBox(helper, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), Blocks.OAK_PLANKS) == 8, "放下 8 格");
                    stand.discard();
                })
                .thenSucceed();
    }

    /** 填 3 × 3 → 撤销：格子回空气、退 9 块、MRU 不退；再撤没有东西 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderUndoRefundsPlaced(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 20));
        startOk(helper, player);
        int[] mru = {0};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderUndo.count(player.getUUID()) == 1, "放置任务应留一条撤销记录");
                    mru[0] = MRUStaffItem.storedMRU(stack);
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.STARTED, "应能撤销");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.kind() == BuilderJobs.Kind.UNDO && result.done() == 9, "撤了 9 格，实际 " + result);
                    helper.assertTrue(countBox(helper, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), Blocks.AIR) == 9, "格子回到空气");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 20, "9 块退回来，实际 " + count(player, Items.OAK_PLANKS));
                    helper.assertTrue(MRUStaffItem.storedMRU(stack) == mru[0], "撤销不扣也不退 MRU");
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.NOTHING_TO_UNDO, "再撤没有东西");
                })
                .thenSucceed();
    }

    /** 台阶补成双层、换成了石头的两格不动不退；另外两格撤掉、退 2 块 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderUndoSkipsChanged(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 2, 2, Blocks.OAK_SLAB);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_SLAB, 10));
        startOk(helper, player);
        BlockPos doubled = new BlockPos(2, 3, 2);
        BlockPos stoned = new BlockPos(2, 3, 3);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    expectResult(helper, player, BuilderJobs.StopReason.FINISHED, 4);
                    helper.setBlock(doubled, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
                    helper.setBlock(stoned, Blocks.STONE);
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.STARTED, "应能撤销");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.done() == 2 && result.skipped() == 2, "撤 2 格、2 格变了没动，实际 " + result);
                    helper.assertTrue(helper.getBlockState(doubled).getValue(SlabBlock.TYPE) == SlabType.DOUBLE && helper.getBlockState(stoned).is(Blocks.STONE), "变了的格子不动");
                    helper.assertTrue(count(player, Items.OAK_SLAB) == 8, "10 − 4 + 2 = 8，实际 " + count(player, Items.OAK_SLAB));
                })
                .thenSucceed();
    }

    /**
     * 填完再用清除魔杖清掉：清除不产生记录、不挤掉填充那条；但上一单是清除，撤销（魔杖键 + 右键）不越过它去撤填充那条，
     * 回「上一单是清除」，不开撤销任务、不动格子、不退料（和面板变灰一致）
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderUndoNoClearRecord(GameTestHelper helper) {
        ItemStack fill = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, fill, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 20));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, points(helper, BuilderMode.CLEAR, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), null));
                    startOk(helper, player);
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在清除"))
                .thenExecute(() -> {
                    expectResult(helper, player, BuilderJobs.StopReason.FINISHED, 9);
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 20, "清除拆下的 9 块进背包");
                    helper.assertTrue(BuilderUndo.count(player.getUUID()) == 1, "清除不产生记录，填充那条还在");
                    BuilderJobs.StartResult undo = BuilderJobs.undo(player);
                    helper.assertTrue(undo == BuilderJobs.StartResult.UNDO_CLEARED, "上一单是清除：不撤填充那条，实际 " + undo);
                    helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()) && BuilderUndo.count(player.getUUID()) == 1, "没开撤销任务，记录不动");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 20, "一块都不退，实际 " + count(player, Items.OAK_PLANKS));
                    helper.assertTrue(countBox(helper, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), Blocks.AIR) == 9, "不会多出方块");
                })
                .thenSucceed();
    }

    /** 撤销时领地拒绝的一格不动不退；撤销关掉、换了维度、走远都拒 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderUndoDeniedAndLimits(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 20));
        BlockPos guarded = new BlockPos(2, 3, 3);
        ProtectionFrameworkTests.Claims[] claims = {null};
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    Integer saved = ECBuilder.Config.Test.undoWindowSeconds;
                    try {
                        ECBuilder.Config.Test.undoWindowSeconds = 0;
                        helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.NOTHING_TO_UNDO, "撤销关掉时拒");
                    } finally {
                        ECBuilder.Config.Test.undoWindowSeconds = saved;
                    }
                    Vec3 home = player.position();
                    player.moveTo(home.x + 200D, home.y, home.z);
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.OUT_OF_REACH, "走远了拒");
                    player.moveTo(home.x, home.y, home.z);
                    ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
                    if (nether != null) {
                        ServerLevel overworld = player.serverLevel();
                        player.setServerLevel(nether);
                        helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.UNDO_EXPIRED, "别的维度拒");
                        player.setServerLevel(overworld);
                    }
                    helper.assertTrue(BuilderUndo.count(player.getUUID()) == 1, "被拒的撤销不动记录");
                    claims[0] = ProtectionFrameworkTests.Claims.open(helper).deny(guarded);
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.STARTED, "应能撤销");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    claims[0].close();
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.done() == 8 && result.denied() == 1, "撤 8 格、1 格被拒，实际 " + result);
                    helper.assertTrue(helper.getBlockState(guarded).is(Blocks.OAK_PLANKS), "被拒的格子留着");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 19, "只退 8 块，实际 " + count(player, Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    /** 两个人的记录认领同一格：先撤的删掉、退 1 块，后撤的看到空气不退 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderUndoTwoRecordsSameCell(GameTestHelper helper) {
        BlockPos cell = new BlockPos(2, 3, 2);
        ServerPlayer a = flyer(helper, shaped(BuilderMode.FILL, BuilderShape.PLANE, 1, 1, Blocks.OAK_PLANKS), 2, 4, 2);
        ServerPlayer b = flyer(helper, shaped(BuilderMode.FILL, BuilderShape.PLANE, 1, 1, Blocks.OAK_PLANKS), 2, 4, 2);
        a.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 5));
        b.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 5));
        startOk(helper, a);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(a.getUUID()), "a 还在施工"))
                .thenExecute(() -> {
                    helper.setBlock(cell, Blocks.AIR);
                    startOk(helper, b);
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(b.getUUID()), "b 还在施工"))
                .thenExecute(() -> helper.assertTrue(BuilderJobs.undo(a) == BuilderJobs.StartResult.STARTED, "a 应能撤"))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(a.getUUID()), "a 还在撤销"))
                .thenExecute(() -> helper.assertTrue(BuilderJobs.undo(b) == BuilderJobs.StartResult.STARTED, "b 应能撤"))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(b.getUUID()), "b 还在撤销"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(cell).isAir(), "格子是空气");
                    helper.assertTrue(count(a, Items.OAK_PLANKS) == 5 && count(b, Items.OAK_PLANKS) == 4,
                            "那一格只退一次：a " + count(a, Items.OAK_PLANKS) + "、b " + count(b, Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    /** 撤到一半中断（换手）：没撤的那部分压回栈顶，拿回魔杖再撤能撤完，材料一块不少 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderUndoInterruptedPutsBack(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 9, 9, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 5, 4, 0);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 64));
        startOk(helper, player);
        int[] remaining = {0};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    expectResult(helper, player, BuilderJobs.StopReason.FINISHED, 81);
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.STARTED, "应能撤销");
                })
                .thenWaitUntil(() -> helper.assertTrue(BuilderJobs.progress(player.getUUID()) > 0, "还没开始撤"))
                .thenExecute(() -> player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy()))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.INTERRUPTED && result.done() < 81, "应中断在一半，实际 " + result);
                    BuilderUndo.Record record = BuilderUndo.peek(player.getUUID());
                    remaining[0] = 81 - result.done();
                    helper.assertTrue(record != null && record.size() == remaining[0], "没撤的 " + remaining[0] + " 格压回栈顶，实际 " + (record == null ? -1 : record.size()));
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.STARTED, "拿回魔杖能接着撤");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    helper.assertTrue(result(helper, player).done() == remaining[0], "接着撤完剩下的");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 128, "材料一块不少，实际 " + count(player, Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 先探后放、禁用维度、会掉落的方块、收尾、操作包
    // ------------------------------------------------------------------

    /**
     * 直线 20 格、最后 3 格在别人领地里（开工前探测只挑前 16 格，探不到）：第一次真放被拒以后改先探后放，
     * 后面两格不再真发放置事件。线伸出场地，单放一批（{@link #PROBE_LINE_BATCH}）
     */
    @GameTest(template = EMPTY_LARGE, batch = PROBE_LINE_BATCH, timeoutTicks = 200)
    public static void builderProbeAfterDenied(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.LINE, 1, 20, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 1, 4, 0);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        BlockPos[] guarded = {new BlockPos(1, 3, 17), new BlockPos(1, 3, 18), new BlockPos(1, 3, 19)};
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        for (BlockPos pos : guarded) {
            claims.deny(pos);
        }
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.done() == 17 && result.denied() == 3 && result.probedDenied() == 2, "17 格放下、3 格被拒其中 2 格是探测，实际 " + result);
                    helper.assertTrue(claims.places(guarded[0]) == 1 && claims.places(guarded[1]) == 0 && claims.places(guarded[2]) == 0,
                            "只有第一格真发了放置事件：" + claims.places(guarded[0]) + " / " + claims.places(guarded[1]) + " / " + claims.places(guarded[2]));
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 64 - 17, "被拒的不扣料");
                })
                .thenSucceed();
    }

    /** 开工前探测发现被拒：一开始就先探后放，被拒的格子一次真放置事件都没有 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderPreProbeStartsInProbeMode(GameTestHelper helper) {
        BlockPos guarded = new BlockPos(2, 3, 3);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(guarded);
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    helper.assertTrue(claims.places(guarded) == 0, "被拒的格子不应真发放置事件，实际 " + claims.places(guarded));
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.done() == 8 && result.probedDenied() == 1, "8 格放下、1 格探测被拒，实际 " + result);
                })
                .thenSucceed();
    }

    /**
     * 先探后放时探测预算一直拿不到（同一名玩家名下别的东西每刻先把个人探测预算用完）：等满 {@link BuilderJobs#PROBE_WAIT_LIMIT} 刻就放弃探测、
     * 改走真事件把活做完，不会一直卡着占名额；放弃以后再被拒也不重新打开先探后放（被拒的格子各真发一次放置事件，照样不扣料）。
     * 直线 20 格：第 0 格被拒（开工前探测就探到了，任务一开始就是先探后放），第 10、15 格被拒（这时已改走真事件）。
     */
    @GameTest(template = EMPTY_LARGE, batch = PROBE_WAIT_BATCH, timeoutTicks = 300)
    public static void builderProbeWaitGivesUp(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.LINE, 1, 20, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 1, 4, 0);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        BlockPos first = new BlockPos(1, 3, 0);
        BlockPos[] later = {new BlockPos(1, 3, 10), new BlockPos(1, 3, 15)};
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(first).deny(later[0]).deny(later[1]);
        startOk(helper, player);
        long started = helper.getTick();
        // 每刻在魔杖之前（GameTest 在服务端刻中间跑，施工在刻末）用一个别的调用方探一格没探过的格子，把这名玩家这一刻的 1 格预算用掉
        Object other = new Object();
        AtomicInteger cursor = new AtomicInteger();
        helper.onEachTick(() -> {
            int n = cursor.getAndIncrement();
            BlockPos spare = helper.absolutePos(new BlockPos(4 + n % 8, 8 + n / 8 % 8, n / 64));
            ECProtection.check(player, helper.getLevel(), spare, other);
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    long took = helper.getTick() - started;
                    helper.assertTrue(took >= BuilderJobs.PROBE_WAIT_LIMIT, "应先等满 " + BuilderJobs.PROBE_WAIT_LIMIT + " 刻才放弃探测，实际 " + took + " 刻就做完了");
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.FINISHED, "应做完，实际 " + result);
                    helper.assertTrue(result.done() == 17 && result.denied() == 3 && result.probedDenied() == 1,
                            "17 格放下、3 格被拒其中 1 格是探测，实际 " + result);
                    helper.assertTrue(claims.places(first) == 0, "开工前就探到被拒的第 0 格不应真发放置事件，实际 " + claims.places(first));
                    helper.assertTrue(claims.places(later[0]) == 1 && claims.places(later[1]) == 1,
                            "放弃探测以后被拒的格子各真放一次：" + claims.places(later[0]) + " / " + claims.places(later[1]));
                    helper.assertTrue(countBox(helper, new BlockPos(1, 3, 0), new BlockPos(1, 3, 19), Blocks.OAK_PLANKS) == 17, "放下 17 格");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 64 - 17, "被拒的不扣料");
                })
                .thenSucceed();
    }

    /** 只拒放置的领地铺一条 1 × 20：第 2 格起探测都放行、真放全被拒 */
    private static ProtectionFrameworkTests.Claims placeOnlyClaims(GameTestHelper helper, int from, int to) {
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        for (int z = from; z <= to; z++) {
            claims.denyPlace(new BlockPos(1, 3, z));
        }
        return claims;
    }

    private static int placeEvents(ProtectionFrameworkTests.Claims claims, int from, int to) {
        int n = 0;
        for (int z = from; z <= to; z++) {
            n += claims.places(new BlockPos(1, 3, z));
        }
        return n;
    }

    /**
     * 连续被拒就停：只拒放置的领地里（探测放行、真放被拒）连续 {@value BuilderJobs#DENIED_STREAK_LIMIT} 格真放被拒就停在「没有权限」，
     * 结束提示是「连续 N 格被拒」那一句；后面的格子不再真发放置事件（领地插件每格一条提示的那种就只有这几条）
     */
    @GameTest(template = EMPTY_LARGE, batch = STREAK_STOP_BATCH, timeoutTicks = 200)
    public static void builderDeniedStreakStops(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.LINE, 1, 20, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 1, 4, 0);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        ProtectionFrameworkTests.Claims claims = placeOnlyClaims(helper, 2, 19);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.DENIED && result.done() == 2
                                    && result.denied() == BuilderJobs.DENIED_STREAK_LIMIT && result.probedDenied() == 0,
                            "应放下 2 格、连续被拒 " + BuilderJobs.DENIED_STREAK_LIMIT + " 格后停，实际 " + result);
                    helper.assertTrue("message.essentialcraft5.builder.denied_streak".equals(result.messageKey()), "结束提示应是连续被拒那一句，实际 " + result.messageKey());
                    helper.assertTrue(placeEvents(claims, 2, 19) == BuilderJobs.DENIED_STREAK_LIMIT,
                            "被拒的格子只真放了 " + BuilderJobs.DENIED_STREAK_LIMIT + " 次，实际 " + placeEvents(claims, 2, 19));
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 62, "被拒的不扣料");
                })
                .thenSucceed();
    }

    /**
     * 中间放成一格就重新数：被拒 7 格、放成 1 格、再被拒 7 格，到不了连续上限，照常做完；
     * 整单做完但有被拒的，完成提示写上被拒格数（filled_denied）
     */
    @GameTest(template = EMPTY_LARGE, batch = STREAK_RESET_BATCH, timeoutTicks = 200)
    public static void builderDeniedStreakResets(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.LINE, 1, 20, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 1, 4, 0);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        ProtectionFrameworkTests.Claims claims = placeOnlyClaims(helper, 0, 6);
        for (int z = 8; z <= 14; z++) {
            claims.denyPlace(new BlockPos(1, 3, z));
        }
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.FINISHED && result.done() == 6 && result.denied() == 14,
                            "应做完：放下 6 格、被拒 14 格，实际 " + result);
                    helper.assertTrue("message.essentialcraft5.builder.filled_denied".equals(result.messageKey()), "完成提示应写被拒格数，实际 " + result.messageKey());
                })
                .thenSucceed();
    }

    /** denied_streak_limit 设 0 = 不限：只拒放置的领地里一路被拒到底，照常做完，只受累计上限管 */
    @GameTest(template = EMPTY_LARGE, batch = STREAK_OFF_BATCH, timeoutTicks = 200)
    public static void builderDeniedStreakOff(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.LINE, 1, 20, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 1, 4, 0);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        ProtectionFrameworkTests.Claims claims = placeOnlyClaims(helper, 2, 19);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.FINISHED && result.done() == 2 && result.denied() == 18,
                            "不限连续被拒时应做完，实际 " + result);
                    helper.assertTrue(placeEvents(claims, 2, 19) == 18, "每格都真放了一次");
                })
                .thenSucceed();
    }

    /** 1 × 10 的线（z 0～9，在场地里）先铺上泥土，给全部替换用 */
    private static void dirtLine(GameTestHelper helper) {
        for (int z = 0; z < 10; z++) {
            helper.setBlock(new BlockPos(1, 3, z), Blocks.DIRT);
        }
    }

    /**
     * 全部替换 + 只拒放置的领地（z 3～5）：这几格拆成了、放置被拒，留成空气。它们单独记作「只拆未放」，
     * 不算进「改动了 N 格」也不算「没有权限，未改动」，完成提示仍是 filled（另起一行写只拆未放）；中间放成一格照常重新数连续被拒
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderReplacePlaceDeniedBrokenOnly(GameTestHelper helper) {
        dirtLine(helper);
        ItemStack stack = shaped(BuilderMode.REPLACE_ALL, BuilderShape.LINE, 1, 10, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 1, 4, 0);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        ProtectionFrameworkTests.Claims claims = placeOnlyClaims(helper, 3, 5);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.FINISHED && result.done() == 7
                                    && result.brokenOnly() == 3 && result.denied() == 0,
                            "应做完：换 7 格、3 格只拆未放、没有未改动的被拒格，实际 " + result);
                    helper.assertTrue("message.essentialcraft5.builder.filled".equals(result.messageKey()),
                            "只拆未放的格不算「没有权限，未改动」，实际 " + result.messageKey());
                    helper.assertTrue(BuilderJobs.brokenOnlyMessage(3).getContents() instanceof TranslatableContents t
                            && "message.essentialcraft5.builder.broken_only".equals(t.getKey()), "只拆未放那一句的键");
                    helper.assertTrue(countBox(helper, new BlockPos(1, 3, 3), new BlockPos(1, 3, 5), Blocks.AIR) == 3, "被拒放置的 3 格留成空气");
                    helper.assertTrue(countBox(helper, new BlockPos(1, 3, 0), new BlockPos(1, 3, 9), Blocks.OAK_PLANKS) == 7, "其余 7 格换成木板");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 64 - 7, "放置被拒的不扣料");
                })
                .thenSucceed();
    }

    /**
     * 全部替换 + 只拒放置的领地（z 1～8）：每格都是拆成了、放置被拒，连续被拒照数、不被「拆成了」清零，
     * 连续 {@value BuilderJobs#DENIED_STREAK_LIMIT} 格就停，最后一格（z 9）不动
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderReplacePlaceDeniedStreakStops(GameTestHelper helper) {
        dirtLine(helper);
        ItemStack stack = shaped(BuilderMode.REPLACE_ALL, BuilderShape.LINE, 1, 10, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 1, 4, 0);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        ProtectionFrameworkTests.Claims claims = placeOnlyClaims(helper, 1, 8);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.DENIED && result.done() == 1
                                    && result.brokenOnly() == BuilderJobs.DENIED_STREAK_LIMIT && result.denied() == 0,
                            "应换 1 格、" + BuilderJobs.DENIED_STREAK_LIMIT + " 格只拆未放后停，实际 " + result);
                    helper.assertTrue("message.essentialcraft5.builder.denied_streak".equals(result.messageKey()), "结束提示应是连续被拒那一句，实际 " + result.messageKey());
                    helper.assertTrue(placeEvents(claims, 1, 8) == BuilderJobs.DENIED_STREAK_LIMIT, "被拒的格子各真放一次，实际 " + placeEvents(claims, 1, 8));
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 3, 9)).is(Blocks.DIRT), "停工后最后一格不动");
                })
                .thenSucceed();
    }

    /** 最后一格正好让连续被拒到上限：已经做完了，按做完结算（filled_denied），不报「已停工」 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderStreakOnLastCellFinishes(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.LINE, 1, 10, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 1, 4, 0);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        ProtectionFrameworkTests.Claims claims = placeOnlyClaims(helper, 2, 9);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.FINISHED && result.done() == 2
                                    && result.denied() == BuilderJobs.DENIED_STREAK_LIMIT,
                            "最后一格才到上限应按做完结算，实际 " + result);
                    helper.assertTrue("message.essentialcraft5.builder.filled_denied".equals(result.messageKey()), "完成提示应写被拒格数，实际 " + result.messageKey());
                })
                .thenSucceed();
    }

    /**
     * probe_after_denied 关掉：3 × 11 的直线里第 3～6 排（12 格，施工顺序上连着）在别人领地里。
     * 没在先探后放就不数连续被拒，跳过这 12 格、其余 21 格照做完，只受累计上限管
     */
    @GameTest(template = EMPTY_LARGE, batch = NOPROBE_BATCH, timeoutTicks = 200)
    public static void builderNoProbeLongDeniedRunFinishes(GameTestHelper helper) {
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        for (int z = 3; z <= 6; z++) {
            for (int x = 0; x <= 2; x++) {
                claims.deny(new BlockPos(x, 3, z));
            }
        }
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.LINE, 3, 11, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 1, 4, 0);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.FINISHED && result.done() == 21
                                    && result.denied() == 12 && result.probedDenied() == 0,
                            "应做完：放下 21 格、真被拒 12 格，实际 " + result);
                    helper.assertTrue("message.essentialcraft5.builder.filled_denied".equals(result.messageKey()), "完成提示应写被拒格数，实际 " + result.messageKey());
                    helper.assertTrue(countBox(helper, new BlockPos(0, 3, 0), new BlockPos(2, 3, 10), Blocks.OAK_PLANKS) == 21, "放下 21 格");
                    helper.assertTrue(countBox(helper, new BlockPos(0, 3, 3), new BlockPos(2, 3, 6), Blocks.OAK_PLANKS) == 0, "领地里一格没放");
                })
                .thenSucceed();
    }

    /** 自己已有一单在做时再排一单（前面没有别人）：写「当前这一单完成后开工」，不写「等空出名额」 */
    @GameTest(template = EMPTY)
    public static void builderQueuedBehindOwnJobWording(GameTestHelper helper) {
        Component own = BuilderJobs.queuedMessage(0, true);
        Component slot = BuilderJobs.queuedMessage(0, false);
        Component ahead = BuilderJobs.queuedMessage(3, true);
        helper.assertTrue(own.getContents() instanceof TranslatableContents a && "message.essentialcraft5.builder.queued_own".equals(a.getKey()),
                "自己有单在做应写等这一单做完");
        helper.assertTrue(slot.getContents() instanceof TranslatableContents b && "message.essentialcraft5.builder.queued_slot".equals(b.getKey()),
                "自己没单在做照旧写等空出名额");
        helper.assertTrue(ahead.getContents() instanceof TranslatableContents c && "message.essentialcraft5.builder.queued".equals(c.getKey()),
                "前面有人排着照旧写数");
        helper.succeed();
    }

    /** 排队那一句：前面有单子时写「前面还有 N 个」，前面没有（只是名额被占着）写「等空出名额」 */
    @GameTest(template = EMPTY)
    public static void builderQueuedMessageWording(GameTestHelper helper) {
        Component none = BuilderJobs.queuedMessage(0);
        Component two = BuilderJobs.queuedMessage(2);
        helper.assertTrue(none.getContents() instanceof TranslatableContents a && "message.essentialcraft5.builder.queued_slot".equals(a.getKey()),
                "前面 0 个应写等空出名额");
        helper.assertTrue(two.getContents() instanceof TranslatableContents b && "message.essentialcraft5.builder.queued".equals(b.getKey())
                && b.getArgs().length == 1 && Integer.valueOf(2).equals(b.getArgs()[0]), "前面 2 个照旧写数");
        helper.succeed();
    }

    /** probe_after_denied 关掉：照旧每格真发放置事件 */
    @GameTest(template = EMPTY_LARGE, batch = NOPROBE_BATCH, timeoutTicks = 200)
    public static void builderNoProbeWhenOff(GameTestHelper helper) {
        BlockPos[] guarded = {new BlockPos(2, 3, 3), new BlockPos(3, 3, 4)};
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(guarded[0]).deny(guarded[1]);
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    helper.assertTrue(claims.places(guarded[0]) == 1 && claims.places(guarded[1]) == 1, "关掉时每格都真放一次");
                    helper.assertTrue(result(helper, player).probedDenied() == 0, "没有探测被拒");
                })
                .thenSucceed();
    }

    /** 维度在 disabled_dimensions 里：开工、撤销都拒 */
    @GameTest(template = EMPTY)
    public static void builderDisabledDimension(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        List<String> saved = ECBuilder.Config.Test.disabledDimensions;
        try {
            ECBuilder.Config.Test.disabledDimensions = List.of(helper.getLevel().dimension().location().toString());
            helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.DISABLED_DIMENSION, "开工应被拒");
            helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.DISABLED_DIMENSION, "撤销应被拒");
        } finally {
            ECBuilder.Config.Test.disabledDimensions = saved;
        }
        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "没有任务");
        helper.succeed();
    }

    /** 悬空的沙子跳过、不生成下落实体；从地面往上的一列沙子照放 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderSkipsFloatingFallingBlocks(GameTestHelper helper) {
        ItemStack floating = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.SAND);
        ServerPlayer flyer = flyer(helper, floating, 2, 6, 2);
        flyer.getInventory().items.set(1, new ItemStack(Items.SAND, 64));
        helper.setBlock(new BlockPos(8, 0, 8), Blocks.STONE);
        ItemStack column = points(helper, BuilderMode.FILL, new BlockPos(8, 1, 8), new BlockPos(8, 2, 8), Blocks.SAND);
        ServerPlayer builder = flyer(helper, column, 6, 1, 6);
        builder.getInventory().items.set(1, new ItemStack(Items.SAND, 64));
        startOk(helper, flyer);
        startOk(helper, builder);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(flyer.getUUID()) && !BuilderJobs.isRunning(builder.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    BuilderJobs.Result air = result(helper, flyer);
                    helper.assertTrue(air.done() == 0 && air.skipped() == 9, "悬空的 9 格都跳过，实际 " + air);
                    expectResult(helper, builder, BuilderJobs.StopReason.FINISHED, 2);
                    AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(11, 8, 11)));
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, area, Entity::isAlive).isEmpty(), "没有下落实体");
                    helper.assertTrue(count(flyer, Items.SAND) == 64, "跳过的不扣料");
                })
                .thenSucceed();
    }

    /** 死亡、换维度事件：当前任务中断、排队的丢掉，撤销记录留着 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderDeathAndDimensionCleanup(GameTestHelper helper) {
        ServerPlayer dying = bigCreative(helper, 0);
        ServerPlayer travelling = bigCreative(helper, 6);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BuilderJobs.progress(dying.getUUID()) > 0 && BuilderJobs.progress(travelling.getUUID()) > 0, "还没开始放"))
                .thenExecute(() -> {
                    MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(dying, helper.getLevel().damageSources().generic()));
                    MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(travelling, Level.OVERWORLD, Level.NETHER));
                    for (ServerPlayer player : new ServerPlayer[] {dying, travelling}) {
                        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "任务和排队的都应清掉");
                        helper.assertTrue(result(helper, player).reason() == BuilderJobs.StopReason.INTERRUPTED, "应是中断");
                        helper.assertTrue(BuilderUndo.count(player.getUUID()) == 1, "撤销记录留着");
                    }
                })
                .thenSucceed();
    }

    /** 下线：任务、排队、撤销记录全清 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderLogoutClearsAll(GameTestHelper helper) {
        ServerPlayer player = bigCreative(helper, 0);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BuilderJobs.progress(player.getUUID()) > 0, "还没开始放"))
                .thenExecute(() -> {
                    MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                    helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()) && BuilderUndo.count(player.getUUID()) == 0, "下线全清");
                })
                .thenSucceed();
    }

    /** 创造模式在 x0 起铺一片 5 × 9 × 9（405 格），开工后再排一单 */
    private static ServerPlayer bigCreative(GameTestHelper helper, int x0) {
        ItemStack stack = points(helper, BuilderMode.FILL, new BlockPos(x0 + 1, 1, 1), new BlockPos(x0 + 5, 9, 9), Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, x0, 1, 0);
        player.setGameMode(GameType.CREATIVE);
        startOk(helper, player);
        helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.QUEUED, "第二单应排队");
        return player;
    }

    /** 操作包：尺寸夹值、手里不是魔杖不理、切形状、每 20 刻最多 10 个 */
    @GameTest(template = EMPTY)
    public static void builderActionPacketRules(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SIZE, 0, 500, 500), player), "应处理");
        int[] size = BuilderSelection.size(stack, BuilderShape.PLANE);
        helper.assertTrue(size[0] == ECBuilder.Config.maxSide() && size[1] == ECBuilder.Config.maxSide(), "尺寸夹到 max_side，实际 " + size[0] + " × " + size[1]);
        helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.CYCLE_SHAPE, 0, 0, 0), player)
                && BuilderSelection.shape(stack) == BuilderShape.LINE, "铺面切到直线");
        helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SIZE, 1, 3, 3), player), "副手不是魔杖不理");
        int accepted = 3;
        for (int i = 0; i < 12; i++) {
            if (BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.CLIENT_PREFS, 0, 2000, 0), player)) {
                accepted++;
            }
        }
        helper.assertTrue(accepted <= BuilderActions.MAX_PER_WINDOW + 3, "同一刻里超过上限的应丢掉，实际处理了 " + accepted);
        helper.assertTrue(BuilderActions.confirmThreshold(player.getUUID()) == 2000, "确认阈值记下了");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 按住右键、全部替换的两半方块、撤销先查后停、事件里结束
    // ------------------------------------------------------------------

    /**
     * 按住右键的自动连发（原版每 4 刻一次）不算：材料不够时同一刻、3 刻后的连发都不会自动确认，隔够 {@value BuilderJobs#REQUEST_GAP_TICKS} 刻再按才确认；
     * 小单右键方块连发两下只开一单，不会再排一单
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderHeldRightClickIgnored(GameTestHelper helper) {
        ItemStack shortStack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer shortOf = flyer(helper, shortStack, 2, 4, 2);
        shortOf.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 2));
        Runnable press = () -> shortStack.getItem().use(helper.getLevel(), shortOf, InteractionHand.MAIN_HAND);
        press.run();
        helper.assertTrue(!BuilderJobs.isRunning(shortOf.getUUID()), "材料不够应先要确认");
        press.run();
        helper.assertTrue(!BuilderJobs.isRunning(shortOf.getUUID()), "同一刻的连发不算确认");

        BlockPos clicked = new BlockPos(8, 1, 8);
        helper.setBlock(clicked, Blocks.STONE);
        ItemStack small = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer holder = flyer(helper, small, 8, 4, 5);
        holder.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 20));
        BlockPos abs = helper.absolutePos(clicked);
        for (int i = 0; i < 2; i++) {
            small.getItem().onItemUseFirst(small, new UseOnContext(holder, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
        }
        helper.assertTrue(BuilderJobs.isRunning(holder.getUUID()), "第一下应开工");
        BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(holder.getUUID(), 0);
        helper.assertTrue(snapshot != null && !snapshot.queued(), "在做的是第一单");
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    press.run();
                    helper.assertTrue(!BuilderJobs.isRunning(shortOf.getUUID()), "隔 3 刻的连发也不算确认");
                })
                .thenIdle(BuilderJobs.REQUEST_GAP_TICKS + 1)
                .thenExecute(() -> {
                    press.run();
                    helper.assertTrue(BuilderJobs.isRunning(shortOf.getUUID()), "松开隔够间隔再按算确认、开工");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(shortOf.getUUID()) && !BuilderJobs.isRunning(holder.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    expectResult(helper, shortOf, BuilderJobs.StopReason.OUT_OF_MATERIAL, 2);
                    expectResult(helper, holder, BuilderJobs.StopReason.FINISHED, 9);
                    helper.assertTrue(countBox(helper, new BlockPos(7, 2, 7), new BlockPos(9, 2, 9), Blocks.OAK_PLANKS) == 9, "点中格上方铺满一层");
                    helper.assertTrue(count(holder, Items.OAK_PLANKS) == 11, "只用了一单的 9 块，剩 " + count(holder, Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    /**
     * 全部替换铺一层，压在门、玫瑰丛的上半那层，旁边还有横着伸出来的活塞头：这三格跳过（不拆，主体那半也不会被连带拆掉），其余 6 格换成石头
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderReplaceSkipsAttachedHalf(GameTestHelper helper) {
        BlockPos doorLow = new BlockPos(2, 2, 2);
        BlockPos roseLow = new BlockPos(3, 2, 4);
        BlockPos pistonBase = new BlockPos(0, 3, 3);
        BlockPos pistonHead = new BlockPos(1, 3, 3);
        helper.setBlock(doorLow.below(), Blocks.STONE);
        helper.setBlock(doorLow, Blocks.OAK_DOOR.defaultBlockState());
        helper.setBlock(doorLow.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(roseLow.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(roseLow, Blocks.ROSE_BUSH.defaultBlockState());
        helper.setBlock(roseLow.above(), Blocks.ROSE_BUSH.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(pistonBase.south(), Blocks.REDSTONE_BLOCK);   // 有信号，伸出着的活塞不会自己缩回去
        helper.setBlock(pistonBase, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST).setValue(PistonBaseBlock.EXTENDED, true));
        helper.setBlock(pistonHead, Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, Direction.EAST).setValue(PistonHeadBlock.TYPE, PistonType.DEFAULT));
        ItemStack stack = shaped(BuilderMode.REPLACE_ALL, BuilderShape.PLANE, 3, 3, Blocks.STONE);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.STONE, 64));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.done() == 6 && result.skipped() == 3, "换 6 格、跳过 3 格，实际 " + result);
                    helper.assertTrue(helper.getBlockState(doorLow).is(Blocks.OAK_DOOR) && helper.getBlockState(doorLow.above()).is(Blocks.OAK_DOOR), "门两半都在");
                    helper.assertTrue(helper.getBlockState(roseLow).is(Blocks.ROSE_BUSH) && helper.getBlockState(roseLow.above()).is(Blocks.ROSE_BUSH), "玫瑰丛两半都在");
                    helper.assertTrue(helper.getBlockState(pistonBase).is(Blocks.PISTON) && helper.getBlockState(pistonHead).is(Blocks.PISTON_HEAD), "活塞和活塞头都在");
                    AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(6, 6, 6)));
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, Entity::isAlive).isEmpty(), "没有撒在地上的掉落物");
                    helper.assertTrue(count(player, Items.STONE) == 64 - 6, "只用了 6 块石头，剩 " + count(player, Items.STONE));
                })
                .thenSucceed();
    }

    /**
     * 撤不了（没有记录、走远了）时不动正在做的活和排队的；能撤时才停下正在做的那单（它的记录就在栈顶）、清掉排队的、撤掉放下的全部
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderUndoChecksBeforeStopping(GameTestHelper helper) {
        ServerPlayer player = bigCreative(helper, 0);
        helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.NOTHING_TO_UNDO, "还没放成任何一格，没有能撤的");
        helper.assertTrue(BuilderJobs.isRunning(player.getUUID()), "撤不了时正在做的活照跑");
        helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.BUSY, "排队的那单还在（名额仍是满的）");
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(5, 9, 9);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BuilderJobs.progress(player.getUUID()) > 0, "还没开始放"))
                .thenExecute(() -> {
                    Vec3 home = player.position();
                    player.moveTo(home.x + 200D, home.y, home.z);
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.OUT_OF_REACH, "走远了拒");
                    player.moveTo(home.x, home.y, home.z);
                    BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(player.getUUID(), 0);
                    helper.assertTrue(snapshot != null && snapshot.kind() == BuilderJobs.Kind.BUILD && !snapshot.queued(), "被拒的撤销不停正在做的");
                    helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.BUSY, "排队的那单还在");
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.STARTED, "能撤时应开撤");
                    helper.assertTrue(result(helper, player).reason() == BuilderJobs.StopReason.STOPPED, "施工那单是停下的");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.kind() == BuilderJobs.Kind.UNDO && result.reason() == BuilderJobs.StopReason.FINISHED, "撤销做完，实际 " + result);
                    helper.assertTrue(countBox(helper, a, b, Blocks.OAK_PLANKS) == 0, "放下的全撤掉了，排队的那单也没再开工，剩 " + countBox(helper, a, b, Blocks.OAK_PLANKS));
                })
                .thenSucceed();
    }

    /**
     * 第 3 格的放置事件里换了维度（任务被中断）：只结束一次，结果是中断、做成 3 格，后面的格子不再放
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderFinishOnceWhenInterruptedInEvent(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 20));
        AtomicInteger places = new AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> spy = event -> {
            if (event.getEntity() == player && places.incrementAndGet() == 3) {
                MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.EntityPlaceEvent.class, spy);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenIdle(3)
                .thenExecute(() -> {
                    MinecraftForge.EVENT_BUS.unregister(spy);
                    expectResult(helper, player, BuilderJobs.StopReason.INTERRUPTED, 3);
                    helper.assertTrue(places.get() == 3, "中断以后不再发放置事件，实际 " + places.get());
                    helper.assertTrue(countBox(helper, new BlockPos(1, 3, 2), new BlockPos(3, 3, 4), Blocks.OAK_PLANKS) == 3, "只放下 3 格");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 17, "用掉 3 块，剩 " + count(player, Items.OAK_PLANKS));
                    helper.assertTrue(BuilderUndo.count(player.getUUID()) == 1, "撤销记录留着");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 耗时预算、排队、暂停（各自的 batch）
    // ------------------------------------------------------------------

    /** 耗时预算覆盖成 0：全服每刻只动 1 格（至少 1 格），两个任务轮流推进、都做完 */
    @GameTest(template = EMPTY_LARGE, batch = TIME_BATCH, timeoutTicks = 300)
    public static void builderTimeBudget(GameTestHelper helper) {
        ServerPlayer[] players = new ServerPlayer[2];
        for (int i = 0; i < 2; i++) {
            BlockPos a = new BlockPos(1 + 5 * i, 1, 1);
            players[i] = flyer(helper, points(helper, BuilderMode.FILL, a, a.offset(3, 1, 3), Blocks.OAK_PLANKS), 5 * i, 1, 0);
            players[i].setGameMode(GameType.CREATIVE);
            startOk(helper, players[i]);
        }
        int[] last = {0, 0};
        AtomicInteger ticks = new AtomicInteger();
        helper.onEachTick(() -> {
            int now0 = doneOf(players[0].getUUID());
            int now1 = doneOf(players[1].getUUID());
            int delta = now0 - last[0] + now1 - last[1];
            helper.assertTrue(delta <= 1, "预算为 0 时每刻全服只该动 1 格，实际 " + delta);
            if (delta > 0) {
                ticks.incrementAndGet();
            }
            last[0] = now0;
            last[1] = now1;
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(players[0].getUUID()) && !BuilderJobs.isRunning(players[1].getUUID()), "还在施工"))
                .thenExecute(() -> {
                    expectResult(helper, players[0], BuilderJobs.StopReason.FINISHED, 32);
                    expectResult(helper, players[1], BuilderJobs.StopReason.FINISHED, 32);
                    helper.assertTrue(ticks.get() >= 64, "64 格每刻 1 格至少 64 刻，实际 " + ticks.get());
                })
                .thenSucceed();
    }

    private static int doneOf(UUID id) {
        int progress = BuilderJobs.progress(id);
        if (progress >= 0) {
            return progress;
        }
        BuilderJobs.Result result = BuilderJobs.lastResult(id);
        return result == null ? 0 : result.done();
    }

    /** 全服名额覆盖成 1：自己的第二单排队、第三单忙；别人的也排队；按先来后到全部做完 */
    @GameTest(template = EMPTY_LARGE, batch = QUEUE_BATCH, timeoutTicks = 300)
    public static void builderQueuePerPlayerAndGlobal(GameTestHelper helper) {
        ItemStack first = points(helper, BuilderMode.FILL, new BlockPos(1, 1, 1), new BlockPos(3, 2, 3), Blocks.OAK_PLANKS);
        ServerPlayer a = flyer(helper, first, 0, 1, 0);
        a.setGameMode(GameType.CREATIVE);
        startOk(helper, a);
        helper.assertTrue(BuilderJobs.start(a, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.QUEUED, "自己的第二单排队");
        helper.assertTrue(BuilderJobs.start(a, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.BUSY, "第三单忙");
        ServerPlayer b = flyer(helper, points(helper, BuilderMode.FILL, new BlockPos(6, 1, 1), new BlockPos(8, 2, 3), Blocks.OAK_PLANKS), 5, 1, 0);
        b.setGameMode(GameType.CREATIVE);
        helper.assertTrue(BuilderJobs.start(b, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.QUEUED, "名额满了别人也排队");
        BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(b.getUUID(), 0);
        helper.assertTrue(snapshot != null && snapshot.queued() && snapshot.queueAhead() == 1, "b 前面还有 1 个");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(a.getUUID()) && !BuilderJobs.isRunning(b.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(countBox(helper, new BlockPos(6, 1, 1), new BlockPos(8, 2, 3), Blocks.OAK_PLANKS) == 18, "b 那单做完");
                    helper.assertTrue(countBox(helper, new BlockPos(1, 1, 1), new BlockPos(3, 2, 3), Blocks.OAK_PLANKS) == 18, "a 那单做完");
                })
                .thenSucceed();
    }

    /** 用完暂停（不结束）、暂停时不分预算；补料后接着做完 */
    @GameTest(template = EMPTY_LARGE, batch = PAUSE_BATCH, timeoutTicks = 300)
    public static void builderPauseAndResume(GameTestHelper helper) {
        ItemStack stack = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 4));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(player.getUUID(), 0);
                    helper.assertTrue(snapshot != null && snapshot.paused(), "应暂停");
                })
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.progress(player.getUUID()) == 4, "暂停前放了 4 格");
                    player.getInventory().items.set(5, new ItemStack(Items.OAK_PLANKS, 10));
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    expectResult(helper, player, BuilderJobs.StopReason.FINISHED, 9);
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 5, "补的 10 块用了 5 块");
                })
                .thenSucceed();
    }

    /**
     * 状态包里的暂停原因由服务端给：材料只在一个拒绝取出的随身容器里（看得见、数得到，却取不出来）时，暂停是「材料用完」，
     * 不能因为手头的数大于 0 就当成 MRU 不够；MRU 用光的那单是「MRU 不够」
     */
    @GameTest(template = EMPTY_LARGE, batch = PAUSE_BATCH, timeoutTicks = 300)
    public static void builderPauseReasonInStatus(GameTestHelper helper) {
        ServerPlayer starved = flyer(helper, shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS), 2, 4, 2);
        starved.getInventory().items.set(1, container(Items.WRITABLE_BOOK, new ItemStack(Items.OAK_PLANKS, 9)));
        ItemStack poorWand = shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS);
        poorWand.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(2 * BuilderMode.PLACE_COST));
        ServerPlayer poor = flyer(helper, poorWand, 7, 4, 2);
        poor.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        startOk(helper, starved);
        startOk(helper, poor);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    BuilderJobs.Snapshot a = BuilderJobs.snapshot(starved.getUUID(), 0);
                    BuilderJobs.Snapshot b = BuilderJobs.snapshot(poor.getUUID(), 0);
                    helper.assertTrue(a != null && a.paused() && b != null && b.paused(), "两单都应暂停");
                })
                .thenExecute(() -> {
                    long now = helper.getLevel().getServer().getTickCount();
                    S2CBuilderStatusPacket a = BuilderStatus.compute(starved, now);
                    S2CBuilderStatusPacket b = BuilderStatus.compute(poor, now);
                    helper.assertTrue(a != null && a.haveInContainers() == 0, "拒绝取出的容器里的 9 块不算持有，实际 " + (a == null ? "null" : a.haveInContainers()));
                    helper.assertTrue(a.state() == S2CBuilderStatusPacket.PAUSED && !BuilderJobs.snapshot(starved.getUUID(), 0).pausedForMru(),
                            "取不出料的那单应是「材料用完」，实际状态 " + a.state());
                    helper.assertTrue(b != null && b.state() == S2CBuilderStatusPacket.PAUSED_MRU && BuilderJobs.snapshot(poor.getUUID(), 0).pausedForMru(),
                            "MRU 用光的那单应是「MRU 不够」，实际状态 " + (b == null ? "null" : b.state()));
                    helper.assertTrue(BuilderJobs.progress(poor.getUUID()) == 2, "MRU 只够 2 格");
                    BuilderJobs.stop(starved);
                    BuilderJobs.stop(poor);
                })
                .thenSucceed();
    }

    /** 暂停超时（2 秒）按「材料用完」结束；暂停中换手是中断 */
    @GameTest(template = EMPTY_LARGE, batch = PAUSE_BATCH, timeoutTicks = 300)
    public static void builderPauseTimeoutAndInterrupt(GameTestHelper helper) {
        ServerPlayer waiting = flyer(helper, shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS), 2, 4, 2);
        waiting.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 2));
        ServerPlayer swapping = flyer(helper, shaped(BuilderMode.FILL, BuilderShape.PLANE, 3, 3, Blocks.OAK_PLANKS), 7, 4, 2);
        swapping.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 2));
        startOk(helper, waiting);
        startOk(helper, swapping);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(swapping.getUUID(), 0);
                    helper.assertTrue(snapshot != null && snapshot.paused(), "应暂停");
                })
                .thenExecute(() -> swapping.setItemInHand(InteractionHand.MAIN_HAND, swapping.getMainHandItem().copy()))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(waiting.getUUID()) && !BuilderJobs.isRunning(swapping.getUUID()), "还在等"))
                .thenExecute(() -> {
                    expectResult(helper, waiting, BuilderJobs.StopReason.OUT_OF_MATERIAL, 2);
                    expectResult(helper, swapping, BuilderJobs.StopReason.INTERRUPTED, 2);
                })
                .thenSucceed();
    }
}
