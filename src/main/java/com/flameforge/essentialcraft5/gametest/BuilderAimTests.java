package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.item.BuilderActions;
import com.flameforge.essentialcraft5.item.BuilderAim;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderModules;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderShapes;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.network.C2SBuilderActionPacket;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 远域模块远距离瞄准（tools/plan/builder_upgrade.md §2.2，{@link BuilderAim}）的 GameTest：判定规则（同一格认、挨着一格认、差远了拒、背面拒、
 * 空气拒）、服务端复核（没装远域、形状不对、不是魔杖、坐标 / 面乱填、太远、死了 / 旁观、墙后那一格、区块没加载且不会被加载）、
 * 包的格式（只有 AIM_START 多带坐标和面）、从 30 格外瞄准铺面走完二次确认和开工（6 刻间隔照旧）再撤销、施工距离照旧卡。
 * 客户端的射线、虚影、拦右键测不到（要开游戏），射线本身是两端共用的 {@link BuilderAim#trace}，这里按服务端测。
 * 改覆盖值的放 {@link #CAP_BATCH}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BuilderAimTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 读默认配置 */
    static final String AIM_BATCH = "ec5_builder_aim";
    /** 瞄准距离上限 100、远域不加施工距离 */
    static final String CAP_BATCH = "ec5_builder_aim_cap";

    private BuilderAimTests() {
    }

    @BeforeBatch(batch = AIM_BATCH)
    public static void builderAimBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
    }

    @AfterBatch(batch = AIM_BATCH)
    public static void builderAimAfter(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
    }

    @BeforeBatch(batch = CAP_BATCH)
    public static void builderAimCapBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
        ECBuilder.Config.Test.aimDistance = 100;
        ECBuilder.Config.Test.moduleValues = new ECBuilder.Config.ModuleValues(2D, 1.4D, 2D, 0.6D, 0);
    }

    @AfterBatch(batch = CAP_BATCH)
    public static void builderAimCapAfter(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 二阶填充魔杖，满 MRU，形状铺面；reach 为 true 时装上远域 */
    private static ItemStack wand(boolean reach) {
        ItemStack stack = new ItemStack(ECBuilder.item(BuilderMode.FILL));
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(MRUStaffItem.MAX_MRU));
        BuilderSelection.setShape(stack, BuilderShape.PLANE);
        if (reach) {
            withReach(stack);
        }
        return stack;
    }

    private static ItemStack withReach(ItemStack stack) {
        ListTag list = new ListTag();
        list.add(StringTag.valueOf(BuilderModules.Kind.REACH.registryName().toString()));
        stack.getOrCreateTag().put(BuilderModules.TAG, list);
        return stack;
    }

    /** 站在相对坐标 (x, y, z)、朝向 yRot（0 = 南）、俯仰 xRot（90 = 正下方）的真玩家，悬空，主手拿着 wand */
    private static ServerPlayer aimer(GameTestHelper helper, ItemStack wand, double x, double y, double z, float yRot, float xRot) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-builder-aim");
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        player.moveTo(at.x, at.y, at.z, yRot, xRot);
        player.setOnGround(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        return player;
    }

    /** 相对坐标 (cx ± 1, y, cz ± 1) 铺 3 × 3 石头 */
    private static void floor(GameTestHelper helper, int cx, int y, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.STONE);
            }
        }
    }

    private static BuilderAim.Verdict verify(GameTestHelper helper, ServerPlayer player, BlockPos relative, Direction face) {
        return BuilderAim.verify(player, player.getMainHandItem(), helper.absolutePos(relative), face.get3DDataValue(), false);
    }

    private static C2SBuilderActionPacket roundTrip(C2SBuilderActionPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        C2SBuilderActionPacket.encode(packet, buf);
        C2SBuilderActionPacket back = C2SBuilderActionPacket.decode(buf);
        if (buf.readableBytes() != 0) {
            throw new IllegalStateException("包没读完，剩 " + buf.readableBytes() + " 字节");
        }
        return back;
    }

    // ------------------------------------------------------------------
    // 判定（纯函数）
    // ------------------------------------------------------------------

    /**
     * 判定（不含遮挡）：同一格要同一面；挨着的一格（含对角）面朝着眼睛就过；差 2 格、背面、空气、服务端没点中拒；
     * 朝向按轮廓所在的平面算（眼睛在地毯顶面和格子中心之间也看得见地毯顶面；整格时看不见）
     */
    @GameTest(template = EMPTY, batch = AIM_BATCH)
    public static void builderAimJudgeRules(GameTestHelper helper) {
        BlockPos hit = new BlockPos(10, 5, 10);
        BuilderShapes.Hit server = new BuilderShapes.Hit(hit, Direction.UP);
        Vec3 eye = new Vec3(10.5D, 40D, 10.5D);
        AABB cube = new AABB(0D, 0D, 0D, 1D, 1D, 1D);
        helper.assertTrue(BuilderAim.judge(server, hit, Direction.UP, eye, cube) == BuilderAim.Verdict.OK, "同一格同一面认");
        helper.assertTrue(BuilderAim.judge(server, hit, Direction.NORTH, eye, cube) == BuilderAim.Verdict.MISMATCH, "同一格换一面拒");
        helper.assertTrue(BuilderAim.judge(server, hit.offset(1, 0, 1), Direction.UP, eye, cube) == BuilderAim.Verdict.OK, "对角挨着的一格认");
        helper.assertTrue(BuilderAim.judge(server, hit.offset(0, -1, 0), Direction.UP, eye, cube) == BuilderAim.Verdict.OK,
                "下面挨着那格只过判定（被盖住要靠 verify 的遮挡检查拒）");
        helper.assertTrue(BuilderAim.judge(server, hit.offset(2, 0, 0), Direction.UP, eye, cube) == BuilderAim.Verdict.MISMATCH, "差 2 格拒");
        helper.assertTrue(BuilderAim.judge(server, hit.offset(1, 2, 0), Direction.UP, eye, cube) == BuilderAim.Verdict.MISMATCH, "竖着差 2 格拒");
        helper.assertTrue(BuilderAim.judge(server, hit.offset(1, 0, 0), Direction.DOWN, eye, cube) == BuilderAim.Verdict.MISMATCH, "从上面看，报底面拒");
        helper.assertTrue(BuilderAim.judge(server, hit.offset(1, 0, 0), Direction.EAST, eye, cube) == BuilderAim.Verdict.MISMATCH,
                "眼睛在西边的正上方，东面看不见");
        helper.assertTrue(BuilderAim.judge(server, hit.offset(1, 0, 0), Direction.UP, eye, null) == BuilderAim.Verdict.MISMATCH, "报的是空气拒");
        helper.assertTrue(BuilderAim.judge(null, hit, Direction.UP, eye, cube) == BuilderAim.Verdict.MISMATCH, "服务端没点中拒");
        helper.assertTrue(BuilderAim.near(hit, hit.offset(-1, 1, -1)) && !BuilderAim.near(hit, hit.offset(0, 0, -2)), "near 按每个轴差 1");
        helper.assertTrue(BuilderAim.faces(new Vec3(20D, 5.5D, 10.5D), hit, Direction.EAST)
                && !BuilderAim.faces(new Vec3(20D, 5.5D, 10.5D), hit, Direction.WEST), "从东边看得见东面、看不见西面");
        AABB carpet = new AABB(0D, 0D, 0D, 1D, 0.0625D, 1D);
        Vec3 low = new Vec3(30.5D, 5.2D, 10.5D);
        helper.assertTrue(BuilderAim.faces(low, hit, Direction.UP, carpet) && !BuilderAim.faces(low, hit, Direction.UP, cube),
                "眼睛在 y + 0.2：地毯顶面看得见，整格顶面看不见");
        AABB topSlab = new AABB(0D, 0.5D, 0D, 1D, 1D, 1D);
        Vec3 under = new Vec3(30.5D, 5.3D, 10.5D);
        helper.assertTrue(BuilderAim.faces(under, hit, Direction.DOWN, topSlab) && !BuilderAim.faces(under, hit, Direction.DOWN, cube),
                "眼睛在 y + 0.3：上半砖底面看得见，整格底面看不见");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 服务端复核
    // ------------------------------------------------------------------

    /**
     * 站在 30 多格高处往正下方看：射线点中石头顶面；同一格、挨着的一格认；差 2 格、背面、空气拒；没装远域、形状不对、不是魔杖、
     * 面 / 坐标乱填、太远、死了 / 旁观都拒；装了远域的铺设魔杖也认
     */
    @GameTest(template = EMPTY_LARGE, batch = AIM_BATCH)
    public static void builderAimVerifyLookingDown(GameTestHelper helper) {
        floor(helper, 4, 1, 4);
        ServerPlayer player = aimer(helper, wand(true), 4.5D, 32D, 4.5D, 0F, 90F);
        BuilderAim.Trace trace = BuilderAim.trace(player, player.getMainHandItem());
        helper.assertTrue(trace.hit() != null && trace.hit().pos().equals(helper.absolutePos(new BlockPos(4, 1, 4)))
                && trace.hit().face() == Direction.UP && !trace.unloaded(), "射线点中正下方石头的顶面：" + trace);

        helper.assertTrue(verify(helper, player, new BlockPos(4, 1, 4), Direction.UP) == BuilderAim.Verdict.OK, "同一格认");
        helper.assertTrue(verify(helper, player, new BlockPos(5, 1, 5), Direction.UP) == BuilderAim.Verdict.OK, "挨着的一格认");
        helper.assertTrue(verify(helper, player, new BlockPos(6, 1, 4), Direction.UP) == BuilderAim.Verdict.MISMATCH, "差 2 格拒");
        helper.assertTrue(verify(helper, player, new BlockPos(4, 1, 4), Direction.DOWN) == BuilderAim.Verdict.MISMATCH, "底面拒");
        helper.assertTrue(verify(helper, player, new BlockPos(4, 2, 4), Direction.UP) == BuilderAim.Verdict.MISMATCH, "上面那格是空气，拒");
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        helper.assertTrue(verify(helper, player, new BlockPos(3, 1, 3), Direction.UP) == BuilderAim.Verdict.MISMATCH, "挨着那格的顶面被盖住，拒");
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.STONE_SLAB);
        helper.assertTrue(verify(helper, player, new BlockPos(5, 1, 4), Direction.UP) == BuilderAim.Verdict.OK, "挨着的下半砖顶面看得见，认");

        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 4));
        int up = Direction.UP.get3DDataValue();
        helper.assertTrue(BuilderAim.verify(player, player.getMainHandItem(), target, 6, false) == BuilderAim.Verdict.BAD_PACKET, "面是 6 拒");
        helper.assertTrue(BuilderAim.verify(player, player.getMainHandItem(), target, -1, false) == BuilderAim.Verdict.BAD_PACKET, "面是 -1 拒");
        helper.assertTrue(BuilderAim.verify(player, player.getMainHandItem(), target.atY(5000), up, false) == BuilderAim.Verdict.BAD_PACKET,
                "出了建筑高度拒");
        helper.assertTrue(BuilderAim.verify(player, player.getMainHandItem(), new BlockPos(31_000_000, target.getY(), target.getZ()), up, false)
                == BuilderAim.Verdict.BAD_PACKET, "出了世界边界拒");
        helper.assertTrue(BuilderAim.verify(player, player.getMainHandItem(), target.offset(0, 0, 120), up, false) == BuilderAim.Verdict.TOO_FAR,
                "离眼睛 120 多格拒（二阶装远域施工距离 96）");
        helper.assertTrue(BuilderAim.verify(player, player.getMainHandItem(), target.offset(20_000, 0, 0), up, false) == BuilderAim.Verdict.TOO_FAR,
                "两万格外拒");

        helper.assertTrue(BuilderAim.verify(player, wand(false), target, up, false) == BuilderAim.Verdict.OK, "没装远域也认（瞄准距离 = 施工距离 64）");
        helper.assertTrue(BuilderAim.distance(wand(false)) == 64 && BuilderAim.distance(wand(true)) == 96, "瞄准距离等于施工距离、含远域");
        BlockPos far = target.offset(0, 0, 70);
        helper.assertTrue(BuilderAim.verify(player, wand(false), far, up, false) == BuilderAim.Verdict.TOO_FAR, "没装远域、70 多格外拒");
        helper.assertTrue(BuilderAim.verify(player, wand(true), far, up, false) != BuilderAim.Verdict.TOO_FAR, "装了远域、同一处不按距离拒");
        ItemStack line = wand(true);
        BuilderSelection.setShape(line, BuilderShape.LINE);
        helper.assertTrue(BuilderAim.verify(player, line, target, up, false) == BuilderAim.Verdict.WRONG_SHAPE, "直线不用远距离瞄准");
        ItemStack points = withReach(new ItemStack(ECBuilder.item(BuilderMode.FILL)));
        helper.assertTrue(BuilderAim.verify(player, points, target, up, false) == BuilderAim.Verdict.WRONG_SHAPE, "两点不用远距离瞄准");
        helper.assertTrue(BuilderAim.verify(player, new ItemStack(Items.STICK), target, up, false) == BuilderAim.Verdict.NOT_WAND, "不是魔杖拒");
        ItemStack paver = withReach(new ItemStack(ECBuilder.PAVING_WAND.get()));
        helper.assertTrue(BuilderAim.verify(player, paver, target, up, false) == BuilderAim.Verdict.OK, "装了远域的铺设魔杖（默认铺面）认");
        helper.assertTrue(BuilderAim.verify(player, new ItemStack(ECBuilder.PAVING_WAND.get()), target, up, false) == BuilderAim.Verdict.OK,
                "没装远域的铺设魔杖（施工距离 32）30 格处认");

        ServerPlayer spectator = aimer(helper, wand(true), 4.5D, 32D, 4.5D, 0F, 90F);
        spectator.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(verify(helper, spectator, new BlockPos(4, 1, 4), Direction.UP) == BuilderAim.Verdict.BAD_PACKET, "旁观拒");
        ServerPlayer dead = aimer(helper, wand(true), 4.5D, 32D, 4.5D, 0F, 90F);
        dead.setHealth(0F);
        helper.assertTrue(verify(helper, dead, new BlockPos(4, 1, 4), Direction.UP) == BuilderAim.Verdict.BAD_PACKET, "死了拒");
        helper.succeed();
    }

    /**
     * 平视一堵墙：墙后那一格不管报正面还是顶面都拒（中间先碰到墙）；墙上挨着的那一格、视线没被挡住的认；同一格报侧面（眼睛正对着）拒
     */
    @GameTest(template = EMPTY_LARGE, batch = AIM_BATCH)
    public static void builderAimWallHidesNeighbour(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 4, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 4, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 4, 9), Blocks.STONE);
        ServerPlayer player = aimer(helper, wand(true), 4.5D, 3D, 0.5D, 0F, 0F);
        BuilderAim.Trace trace = BuilderAim.trace(player, player.getMainHandItem());
        helper.assertTrue(trace.hit() != null && trace.hit().pos().equals(helper.absolutePos(new BlockPos(4, 4, 8)))
                && trace.hit().face() == Direction.NORTH, "射线点中墙的北面：" + trace);
        helper.assertTrue(verify(helper, player, new BlockPos(4, 4, 8), Direction.NORTH) == BuilderAim.Verdict.OK, "墙本身认");
        helper.assertTrue(verify(helper, player, new BlockPos(4, 4, 9), Direction.NORTH) == BuilderAim.Verdict.MISMATCH, "墙后那一格的北面拒");
        helper.assertTrue(verify(helper, player, new BlockPos(4, 4, 9), Direction.UP) == BuilderAim.Verdict.MISMATCH, "墙后那一格的顶面拒");
        helper.assertTrue(verify(helper, player, new BlockPos(5, 4, 8), Direction.NORTH) == BuilderAim.Verdict.OK, "墙上旁边那一格看得见，认");
        helper.assertTrue(verify(helper, player, new BlockPos(4, 4, 8), Direction.WEST) == BuilderAim.Verdict.MISMATCH, "正对着时侧面拒");
        helper.succeed();
    }

    /**
     * 区块没加载：报的格子在没加载的区块里拒（开工包提示「没加载」），复核完那个区块仍没加载；射线经过没加载的格子就停、不去读它
     */
    @GameTest(template = EMPTY_LARGE, batch = AIM_BATCH)
    public static void builderAimUnloadedNeverLoads(GameTestHelper helper) {
        ServerPlayer player = aimer(helper, wand(true), 4.5D, 32D, 4.5D, 0F, 90F);
        player.moveTo(player.getX() + 200000D, player.getY(), player.getZ(), 0F, 90F);
        BlockPos below = player.blockPosition().below(20);
        ServerLevel level = helper.getLevel();
        int cx = SectionPos.blockToSectionCoord(below.getX());
        int cz = SectionPos.blockToSectionCoord(below.getZ());
        helper.assertTrue(level.getChunkSource().getChunkNow(cx, cz) == null, "测试前那个区块本来就没加载");
        helper.assertTrue(BuilderAim.verify(player, player.getMainHandItem(), below, Direction.UP.get3DDataValue(), false)
                == BuilderAim.Verdict.NOT_LOADED, "没加载拒");
        BuilderAim.Outcome outcome = BuilderAim.start(player, InteractionHand.MAIN_HAND, below, Direction.UP.get3DDataValue());
        helper.assertTrue(outcome.verdict() == BuilderAim.Verdict.NOT_LOADED && outcome.start() == null, "开工包也拒：" + outcome);
        helper.assertTrue(level.getChunkSource().getChunkNow(cx, cz) == null, "复核没有把区块加载出来");

        // 纯射线：中间一段标成没加载，射线停在那里，不读后面的墙
        helper.setBlock(new BlockPos(4, 4, 8), Blocks.STONE);
        Vec3 from = helper.absoluteVec(new Vec3(4.5D, 4.5D, 0.5D));
        Vec3 to = from.add(0D, 0D, 20D);
        int gapZ = helper.absolutePos(new BlockPos(4, 4, 5)).getZ();
        int[] readsPastGap = {0};
        BuilderAim.Trace cut = BuilderAim.trace(level, from, to, CollisionContext.empty(), pos -> {
            if (pos.getZ() > gapZ) {
                readsPastGap[0]++;
            }
            return pos.getZ() != gapZ;
        });
        helper.assertTrue(cut.unloaded() && cut.hit() == null && readsPastGap[0] == 0, "射线在没加载的格子停下：" + cut + "，之后还问了 " + readsPastGap[0] + " 格");
        BuilderAim.Trace whole = BuilderAim.trace(level, from, to, CollisionContext.empty(), pos -> true);
        helper.assertTrue(whole.hit() != null && whole.hit().pos().equals(helper.absolutePos(new BlockPos(4, 4, 8))), "都加载时点中墙：" + whole);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 包
    // ------------------------------------------------------------------

    /** 只有 AIM_START 多带坐标和面，别的操作的字节和以前一样；乱填的面经 BuilderActions 丢掉 */
    @GameTest(template = EMPTY, batch = AIM_BATCH)
    public static void builderAimPacketFormat(GameTestHelper helper) {
        BlockPos pos = new BlockPos(-123456, -60, 98765);
        C2SBuilderActionPacket aim = roundTrip(C2SBuilderActionPacket.aim(1, pos, Direction.WEST));
        helper.assertTrue(aim.action() == C2SBuilderActionPacket.AIM_START && aim.hand() == 1 && BlockPos.of(aim.pos()).equals(pos)
                && aim.face() == Direction.WEST.get3DDataValue(), "开工包来回一致：" + aim);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        C2SBuilderActionPacket.encode(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SIZE, 0, 5, 7), buf);
        helper.assertTrue(buf.readableBytes() == 4, "调尺寸包仍是 4 字节，实际 " + buf.readableBytes());
        C2SBuilderActionPacket size = roundTrip(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SIZE, 0, 5, 7));
        helper.assertTrue(size.a() == 5 && size.b() == 7 && size.pos() == 0L && size.face() == 0, "调尺寸包来回一致：" + size);

        ServerPlayer player = aimer(helper, wand(true), 1.5D, 2D, 1.5D, 0F, 90F);
        BlockPos below = helper.absolutePos(new BlockPos(1, 0, 1));
        helper.assertTrue(!BuilderActions.handle(roundTrip(new C2SBuilderActionPacket(C2SBuilderActionPacket.AIM_START, 0, 0, 0,
                below.asLong(), 200)), player), "面是 200 的包丢掉");
        helper.assertTrue(!BuilderActions.handle(roundTrip(C2SBuilderActionPacket.aim(0, below.atY(4000), Direction.UP)), player),
                "出了建筑高度的包丢掉");
        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "没开工");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 开工
    // ------------------------------------------------------------------

    /**
     * 30 多格高处往下瞄准 3 × 3 铺面（确认阈值 4）：第一下只要确认；紧接着再来一下在 6 刻内被忽略；隔 7 刻经操作包再来一下开工，
     * 铺在点中格上方那层；然后撤销，方块收回、材料退回
     */
    @GameTest(template = EMPTY_LARGE, batch = AIM_BATCH, timeoutTicks = 300)
    public static void builderAimStartsAfterConfirm(GameTestHelper helper) {
        floor(helper, 4, 1, 4);
        ItemStack stack = wand(true);
        BuilderSelection.setSize(stack, BuilderShape.PLANE, 3, 3);
        BuilderSelection.setStoredBlock(stack, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(Blocks.OAK_PLANKS)));
        ServerPlayer player = aimer(helper, stack, 4.5D, 32D, 4.5D, 0F, 90F);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 9));
        helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.CLIENT_PREFS, 0, 4, 0), player), "报确认阈值 4");
        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 4));
        int up = Direction.UP.get3DDataValue();
        helper.assertTrue(player.getEyePosition().distanceTo(Vec3.atCenterOf(target)) > 30D, "离点中的格子 30 格以上");
        helper.startSequence()
                .thenExecute(() -> {
                    BuilderAim.Outcome first = BuilderAim.start(player, InteractionHand.MAIN_HAND, target, up);
                    helper.assertTrue(first.verdict() == BuilderAim.Verdict.OK && first.start() == BuilderJobs.StartResult.NEEDS_CONFIRM,
                            "第一下要确认：" + first);
                    BuilderAim.Outcome again = BuilderAim.start(player, InteractionHand.MAIN_HAND, target, up);
                    helper.assertTrue(again.verdict() == BuilderAim.Verdict.THROTTLED, "6 刻内再来一下忽略：" + again);
                    helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还没开工");
                })
                .thenIdle(7)
                .thenExecute(() -> {
                    helper.assertTrue(BuilderActions.handle(roundTrip(C2SBuilderActionPacket.aim(0, target, Direction.UP)), player), "确认的包认了");
                    helper.assertTrue(BuilderJobs.isRunning(player.getUUID()), "确认后开工");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            helper.assertTrue(helper.getBlockState(new BlockPos(4 + dx, 2, 4 + dz)).is(Blocks.OAK_PLANKS),
                                    "点中格上方那层 (" + (4 + dx) + ", 2, " + (4 + dz) + ") 铺上了");
                        }
                    }
                    helper.assertTrue(player.getInventory().countItem(Items.OAK_PLANKS) == 0, "用掉 9 块木板");
                    BuilderJobs.StartResult undo = BuilderJobs.undo(player);
                    helper.assertTrue(undo == BuilderJobs.StartResult.STARTED || undo == BuilderJobs.StartResult.QUEUED, "能撤销：" + undo);
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 4)).isAir(), "撤销后收回");
                    helper.assertTrue(player.getInventory().countItem(Items.OAK_PLANKS) == 9, "材料退回 9 块，实际 "
                            + player.getInventory().countItem(Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    /**
     * 瞄准距离等于施工距离、远域不加施工距离（覆盖成 +0）：铺设魔杖（施工距离 32）在 50 格高处往下瞄准按太远拒、什么都没放，
     * 20 格高处认；上限覆盖成 10 时瞄准距离取上限；远域物品提示只写施工距离加多少
     */
    @GameTest(template = EMPTY_LARGE, batch = CAP_BATCH)
    public static void builderAimStillBoundByWorkRange(GameTestHelper helper) {
        floor(helper, 4, 1, 4);
        ItemStack paver = withReach(new ItemStack(ECBuilder.PAVING_WAND.get()));
        paver.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(MRUStaffItem.MAX_MRU));
        BuilderSelection.setStoredBlock(paver, Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(Blocks.OAK_PLANKS)));
        helper.assertTrue(BuilderAim.distance(paver) == 32, "瞄准距离 = 施工距离 32（上限 100 不起作用）");
        ServerPlayer player = aimer(helper, paver, 4.5D, 50D, 4.5D, 0F, 90F);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 64));
        BuilderAim.Outcome outcome = BuilderAim.start(player, InteractionHand.MAIN_HAND, helper.absolutePos(new BlockPos(4, 1, 4)),
                Direction.UP.get3DDataValue());
        helper.assertTrue(outcome.verdict() == BuilderAim.Verdict.TOO_FAR && outcome.start() == null, "50 格高处超出施工距离，按太远拒：" + outcome);
        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()) && helper.getBlockState(new BlockPos(4, 2, 4)).isAir(), "什么都没放");
        ServerPlayer near = aimer(helper, paver.copy(), 4.5D, 20D, 4.5D, 0F, 90F);
        helper.assertTrue(verify(helper, near, new BlockPos(4, 1, 4), Direction.UP) == BuilderAim.Verdict.OK, "20 格高处认");
        ECBuilder.Config.Test.aimDistance = 10;
        try {
            helper.assertTrue(BuilderAim.distance(paver) == 10, "上限覆盖成 10 时取上限");
            helper.assertTrue(verify(helper, near, new BlockPos(4, 1, 4), Direction.UP) == BuilderAim.Verdict.TOO_FAR, "上限 10 时 20 格高处拒");
        } finally {
            ECBuilder.Config.Test.aimDistance = 100;
        }

        List<Component> tip = new ArrayList<>();
        ItemStack module = new ItemStack(ECBuilder.module(BuilderModules.Kind.REACH));
        module.getItem().appendHoverText(module, helper.getLevel(), tip, TooltipFlag.NORMAL);
        helper.assertTrue(!tip.isEmpty() && tip.get(0).getContents() instanceof TranslatableContents c
                && c.getArgs().length == 1 && "0".equals(String.valueOf(c.getArgs()[0])),
                "远域提示写施工距离 +0：" + tip);
        helper.succeed();
    }
}
