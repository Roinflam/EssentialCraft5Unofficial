package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.item.BuilderFlightPlan;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.network.S2CBuilderFlightPacket;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * 飞行特效计划和执行分开以后的几处边界：提前量内撤销、往上填时要靠下面撑着的方块、被包住的格子不进飞行包。
 * 提前量 10，材料用完直接结束（不暂停），发包半径 16。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BuilderFlightEdgeTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    static final String BATCH = "ec5_builder_flight_edge";
    private static final int LEAD = 10;

    private BuilderFlightEdgeTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void builderFlightEdgeBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
        ECBuilder.Config.Test.vfxLeadTicks = LEAD;
        ECBuilder.Config.Test.vfxSendRadius = 16;
        ECBuilder.Config.Test.materialPauseSeconds = 0;
        ECBuilder.Config.Test.maxJobsTotal = 256;
        BuilderFlightPlan.startRecording();
    }

    @AfterBatch(batch = BATCH)
    public static void builderFlightEdgeAfter(ServerLevel level) {
        BuilderFlightPlan.stopRecording();
        ECBuilder.Config.Test.reset();
        BuilderJobs.clearAll();
    }

    private static ItemStack wand(BuilderMode mode, Block block) {
        ItemStack stack = new ItemStack(ECBuilder.item(mode));
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(MRUStaffItem.MAX_MRU));
        BuilderSelection.setStoredBlock(stack, ForgeRegistries.BLOCKS.getKey(block));
        return stack;
    }

    private static ItemStack plane(Block block) {
        ItemStack stack = wand(BuilderMode.FILL, block);
        BuilderSelection.setShape(stack, BuilderShape.PLANE);
        BuilderSelection.setSize(stack, BuilderShape.PLANE, 3, 3);
        return stack;
    }

    private static ItemStack points(GameTestHelper helper, BuilderMode mode, Block block, BlockPos a, BlockPos b) {
        ItemStack stack = wand(mode, block);
        BuilderSelection.setShape(stack, BuilderShape.POINTS);
        BuilderSelection.setFirst(stack, helper.absolutePos(a), helper.getLevel().dimension());
        BuilderSelection.setSecond(stack, helper.absolutePos(b));
        return stack;
    }

    private static ServerPlayer flyer(GameTestHelper helper, ItemStack wand, double x, double y, double z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-edge-fly");
        moveTo(helper, player, x, y, z);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        return player;
    }

    private static void moveTo(GameTestHelper helper, ServerPlayer player, double x, double y, double z) {
        Vec3 at = helper.absoluteVec(new Vec3(x + 0.5D, y, z + 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setOnGround(false);
    }

    private static void startOk(GameTestHelper helper, ServerPlayer player) {
        BuilderJobs.StartResult result = BuilderJobs.start(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result == BuilderJobs.StartResult.STARTED, "应能开工，实际 " + result);
    }

    /** 相对 (x0..x1, y, z0..z1) 这一层里是 block 的格数 */
    private static int countLayer(GameTestHelper helper, int x0, int x1, int y, int z0, int z1, Block block) {
        int n = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int cellsSent(ServerPlayer caster, int kind) {
        return BuilderFlightPlan.recorded().stream()
                .filter(sent -> sent.to() == caster && sent.packet().casterId() == caster.getId() && sent.packet().kind() == kind)
                .mapToInt(sent -> sent.packet().count())
                .sum();
    }

    private static void fill(GameTestHelper helper, BlockPos a, BlockPos b, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
            helper.setBlock(pos.immutable(), block);
        }
    }

    /**
     * 先放好一单 A（木板，相对 y = 3），再开一单 B（圆石，y = 4），B 还在提前量里、一格没放时按撤销：
     * 期望只停下 B，A 原样留着。撤销不能越过刚开的 B 去撤 A。
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderFlightUndoInLeadKeepsPrevious(GameTestHelper helper) {
        ItemStack stack = plane(Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 9));
        player.getInventory().items.set(2, new ItemStack(Items.COBBLESTONE, 9));
        startOk(helper, player);
        BuilderJobs.StartResult[] undo = {null};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "A 还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(countLayer(helper, 1, 3, 3, 2, 4, Blocks.OAK_PLANKS) == 9, "A 应放好 9 格木板");
                    moveTo(helper, player, 2, 5, 2);
                    BuilderSelection.setStoredBlock(stack, ForgeRegistries.BLOCKS.getKey(Blocks.COBBLESTONE));
                    startOk(helper, player);
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(countLayer(helper, 1, 3, 4, 2, 4, Blocks.COBBLESTONE) == 0, "B 还在提前量里，应一格没放");
                    undo[0] = BuilderJobs.undo(player);
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    int planks = countLayer(helper, 1, 3, 3, 2, 4, Blocks.OAK_PLANKS);
                    helper.assertTrue(planks == 9, "B 一格没放时按撤销（返回 " + undo[0] + "），A 的木板只剩 " + planks + " / 9");
                })
                .thenSucceed();
    }

    /**
     * 两点框 3 × 3 × 2 填沙子（下面垫一层石头）：18 格都能放成，期望 18 格都飞。第二层计划时下面还是空气，也要按放得下算。
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderFlightStackedSandAllFly(GameTestHelper helper) {
        fill(helper, new BlockPos(1, 2, 2), new BlockPos(3, 2, 4), Blocks.STONE);
        ItemStack stack = points(helper, BuilderMode.FILL, Blocks.SAND, new BlockPos(1, 3, 2), new BlockPos(3, 4, 4));
        ServerPlayer player = flyer(helper, stack, 2, 7, 0);
        player.getInventory().items.set(1, new ItemStack(Items.SAND, 18));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    int placed = countLayer(helper, 1, 3, 3, 2, 4, Blocks.SAND) + countLayer(helper, 1, 3, 4, 2, 4, Blocks.SAND);
                    int flew = cellsSent(player, S2CBuilderFlightPacket.KIND_PLACE);
                    helper.assertTrue(placed == 18, "18 格沙子都应放成，实际 " + placed);
                    helper.assertTrue(flew == placed, "放成 " + placed + " 格，只有 " + flew + " 格飞过");
                })
                .thenSucceed();
    }

    /**
     * 两个 3 × 3 × 3 的石头块，只有正中那格不同（一个是钻石矿、一个是石头），全部替换、选定方块是钻石矿、身上没有任何材料。
     * 正中那格被石头完全包住，客户端本来看不到（服务端反矿透会把它当石头发）。期望两边的飞行包一样；
     * 包里有没有那一格不能取决于里面是什么。
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 100)
    public static void builderFlightEnclosedCellNotLeaked(GameTestHelper helper) {
        fill(helper, new BlockPos(1, 3, 1), new BlockPos(3, 5, 3), Blocks.STONE);
        fill(helper, new BlockPos(7, 3, 1), new BlockPos(9, 5, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 4, 2), Blocks.DIAMOND_ORE);
        ServerPlayer withOre = flyer(helper, points(helper, BuilderMode.REPLACE_ALL, Blocks.DIAMOND_ORE, new BlockPos(1, 3, 1), new BlockPos(3, 5, 3)),
                2, 8, 6);
        ServerPlayer plain = flyer(helper, points(helper, BuilderMode.REPLACE_ALL, Blocks.DIAMOND_ORE, new BlockPos(7, 3, 1), new BlockPos(9, 5, 3)),
                8, 8, 6);
        startOk(helper, withOre);
        startOk(helper, plain);
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    int a = cellsSent(withOre, S2CBuilderFlightPacket.KIND_PLACE);
                    int b = cellsSent(plain, S2CBuilderFlightPacket.KIND_PLACE);
                    BuilderJobs.stop(withOre);
                    BuilderJobs.stop(plain);
                    helper.assertTrue(a > 0 && b > 0, "两单都应发出飞行包（没有材料也会先计划），实际 " + a + " / " + b);
                    helper.assertTrue(a == b, "正中被包住的那格是钻石矿时飞 " + a + " 格、是石头时飞 " + b + " 格，包里缺的格子就是矿的位置");
                })
                .thenSucceed();
    }
}
