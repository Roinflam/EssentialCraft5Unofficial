package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.item.BuilderArea;
import com.flameforge.essentialcraft5.item.BuilderFlightPlan;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMaterials;
import com.flameforge.essentialcraft5.item.BuilderMix;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderModules;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.flameforge.essentialcraft5.item.BuilderUndo;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.network.S2CBuilderFlightPacket;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 创世神之杖飞行特效的服务端一半（tools/plan/builder_upgrade.md §5.1、§5.2）：放置类施工先计划、发飞行包，过 {@code vfx_lead_ticks} 刻才真放。
 * 守着的是计划和执行之间不能出漏洞：计划不取料、不扣 MRU、不改世界；计划之后格子被占、领地拒绝、材料被拿走，执行时照旧被拒、不扣料；
 * 掉线、死亡、换维度、暂停时计划了没执行的格子直接丢掉；混铺、回收、撤销照样数量守恒。另有包的编解码上限、拆包、收包人范围，
 * 以及提前量为 0 时一个包都不发。
 * 这些测试自己设提前量（不受整轮测试的 {@code -PecBuilderLead} 影响），各 batch 在 {@code @BeforeBatch} 设、{@code @AfterBatch} 置回。
 * 铺面形状下飞在相对 (2, 4, 2) 的玩家面朝南，3 × 3 铺在相对 (1..3, 3, 2..4)。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BuilderFlightTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    static final String BATCH = "ec5_builder_flight";
    /** 提前量为 0 */
    static final String ZERO_BATCH = "ec5_builder_flight_zero";
    /** 材料用完暂停 30 秒 */
    static final String PAUSE_BATCH = "ec5_builder_flight_pause";
    /** 每刻耗时预算为 0：每刻只做得成一格，其余挪到下一刻 */
    static final String TIME_BATCH = "ec5_builder_flight_time";
    private static final int LEAD = 10;
    private static final int RADIUS = 16;
    private static final int FULL = MRUStaffItem.MAX_MRU;
    private static final BlockPos A = new BlockPos(1, 3, 2);
    private static final BlockPos B = new BlockPos(3, 3, 4);

    private BuilderFlightTests() {
    }

    private static void before(int lead) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
        ECBuilder.Config.Test.vfxLeadTicks = lead;
        ECBuilder.Config.Test.vfxSendRadius = RADIUS;
        ECBuilder.Config.Test.materialPauseSeconds = 0;
        ECBuilder.Config.Test.maxJobsTotal = 256;
        BuilderTestContainers.enabled = true;
        BuilderFlightPlan.startRecording();
    }

    private static void after() {
        BuilderFlightPlan.stopRecording();
        BuilderTestContainers.enabled = false;
        ECBuilder.Config.Test.reset();
        BuilderJobs.clearAll();
    }

    @BeforeBatch(batch = BATCH)
    public static void builderFlightBefore(ServerLevel level) {
        before(LEAD);
    }

    @AfterBatch(batch = BATCH)
    public static void builderFlightAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = ZERO_BATCH)
    public static void builderFlightZeroBefore(ServerLevel level) {
        before(0);
    }

    @AfterBatch(batch = ZERO_BATCH)
    public static void builderFlightZeroAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = PAUSE_BATCH)
    public static void builderFlightPauseBefore(ServerLevel level) {
        before(LEAD);
        ECBuilder.Config.Test.materialPauseSeconds = 30;
    }

    @AfterBatch(batch = PAUSE_BATCH)
    public static void builderFlightPauseAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = TIME_BATCH)
    public static void builderFlightTimeBefore(ServerLevel level) {
        before(LEAD);
        ECBuilder.Config.Test.maxMillisPerTick = 0D;
    }

    @AfterBatch(batch = TIME_BATCH)
    public static void builderFlightTimeAfter(ServerLevel level) {
        after();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 铺面形状、选定 block、装了 kinds 这几块模块的魔杖 */
    private static ItemStack plane(BuilderMode mode, int width, int length, Block block, BuilderModules.Kind... kinds) {
        ItemStack stack = new ItemStack(ECBuilder.item(mode));
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(FULL));
        BuilderSelection.setShape(stack, BuilderShape.PLANE);
        BuilderSelection.setSize(stack, BuilderShape.PLANE, width, length);
        BuilderSelection.setStoredBlock(stack, ForgeRegistries.BLOCKS.getKey(block));
        if (kinds.length > 0) {
            ListTag list = new ListTag();
            for (BuilderModules.Kind kind : kinds) {
                list.add(StringTag.valueOf(kind.registryName().toString()));
            }
            stack.getOrCreateTag().put(BuilderModules.TAG, list);
        }
        return stack;
    }

    /** 主手拿着 wand、飞在相对 (x + 0.5, y, z + 0.5) 的真玩家，面朝南 */
    private static ServerPlayer flyer(GameTestHelper helper, ItemStack wand, int x, int y, int z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-builder-fly");
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
            if (handler.getStackInSlot(i).is(item)) {
                n += handler.getStackInSlot(i).getCount();
            }
        }
        return n;
    }

    private static int countBox(GameTestHelper helper, Block block) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(A, B)) {
            if (helper.getBlockState(pos.immutable()).is(block)) {
                n++;
            }
        }
        return n;
    }

    private static int countArea(ServerLevel level, BuilderArea area, Block block) {
        int n = 0;
        for (int i = 0; i < area.size(); i++) {
            if (level.getBlockState(area.cellAt(i, true)).is(block)) {
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
        return Objects.requireNonNull(result);
    }

    private static int placeCost(ItemStack wand) {
        return BuilderTier.stats(wand).placeCost();
    }

    /** 发给这名施法者本人的某一种包 */
    private static List<S2CBuilderFlightPacket> sentTo(ServerPlayer caster, int kind) {
        return BuilderFlightPlan.recorded().stream()
                .filter(sent -> sent.to() == caster && sent.packet().casterId() == caster.getId() && sent.packet().kind() == kind)
                .map(BuilderFlightPlan.Sent::packet)
                .toList();
    }

    private static int cellsSent(ServerPlayer caster, int kind) {
        return sentTo(caster, kind).stream().mapToInt(S2CBuilderFlightPacket::count).sum();
    }

    private static S2CBuilderFlightPacket roundTrip(S2CBuilderFlightPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        S2CBuilderFlightPacket.encode(packet, buf);
        S2CBuilderFlightPacket decoded = S2CBuilderFlightPacket.decode(buf);
        if (buf.readableBytes() != 0) {
            throw new IllegalStateException("解码后还剩 " + buf.readableBytes() + " 字节");
        }
        return decoded;
    }

    /** 手写一个包头（种类、施法者、任务、提前量、最小角），后面由调用方接着写 */
    private static FriendlyByteBuf header(int kind) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(kind);
        buf.writeVarInt(7);
        buf.writeVarInt(3);
        buf.writeByte(LEAD);
        buf.writeLong(BlockPos.ZERO.asLong());
        return buf;
    }

    private static boolean rejects(FriendlyByteBuf buf) {
        try {
            S2CBuilderFlightPacket.decode(buf);
            return false;
        } catch (DecoderException e) {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // 计划和执行之间不取料
    // ------------------------------------------------------------------

    /**
     * 开工后、执行之前（计划好了、包发出去了）把人打断：一块料、一点 MRU 都不扣，世界不变，收到收尾包。
     * interrupt 是打断的办法（死亡、换维度、下线）
     */
    private static void interruptBeforeExecute(GameTestHelper helper, Consumer<ServerPlayer> interrupt) {
        interruptBeforeExecute(helper, interrupt, true);
    }

    /** immediate = 打断的当刻任务就结束（事件路径）；false = 等下一刻复核时才结束（魔杖离手这类没有事件的） */
    private static void interruptBeforeExecute(GameTestHelper helper, Consumer<ServerPlayer> interrupt, boolean immediate) {
        ItemStack stack = plane(BuilderMode.FILL, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 9));
        int mru = MRUStaffItem.storedMRU(stack);
        startOk(helper, player);
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(cellsSent(player, S2CBuilderFlightPacket.KIND_PLACE) == 9, "开工后第一刻就计划好 9 格、发出飞行包，实际 "
                            + cellsSent(player, S2CBuilderFlightPacket.KIND_PLACE));
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 9 && MRUStaffItem.storedMRU(stack) == mru && countBox(helper, Blocks.OAK_PLANKS) == 0,
                            "计划时不取料、不扣 MRU、不放方块");
                    interrupt.accept(player);
                    helper.assertTrue(!immediate || !BuilderJobs.isRunning(player.getUUID()), "打断后任务应已结束");
                })
                .thenExecuteAfter(LEAD + 5, () -> {
                    helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "打断后任务应已结束");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 9, "计划了没执行的格子不扣料，实际剩 " + count(player, Items.OAK_PLANKS));
                    helper.assertTrue(MRUStaffItem.storedMRU(stack) == mru, "计划了没执行的格子不扣 MRU");
                    helper.assertTrue(countBox(helper, Blocks.OAK_PLANKS) == 0, "到了执行的时刻也不再放");
                    helper.assertTrue(!sentTo(player, S2CBuilderFlightPacket.KIND_END).isEmpty(), "应发收尾包");
                    helper.assertTrue(BuilderUndo.count(player.getUUID()) == 0, "一格没放成，不留撤销记录");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void builderFlightDeathBeforeExecute(GameTestHelper helper) {
        interruptBeforeExecute(helper, player -> MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player, helper.getLevel().damageSources().generic())));
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void builderFlightDimensionBeforeExecute(GameTestHelper helper) {
        interruptBeforeExecute(helper, player -> MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER)));
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void builderFlightLogoutBeforeExecute(GameTestHelper helper) {
        interruptBeforeExecute(helper, player -> MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player)));
    }

    /** 停下（潜行右键空气那条路）同样丢掉计划了没执行的格子 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void builderFlightStopBeforeExecute(GameTestHelper helper) {
        interruptBeforeExecute(helper, player -> helper.assertTrue(BuilderJobs.stop(player), "应能停下"));
    }

    /** 整单做完：提前量 10 刻后才放下，放下的数、扣的料和 MRU 都和格数一致，最后发收尾包 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void builderFlightPlacesAfterLead(GameTestHelper helper) {
        ItemStack stack = plane(BuilderMode.FILL, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 12));
        int mru = MRUStaffItem.storedMRU(stack);
        startOk(helper, player);
        helper.startSequence()
                .thenExecuteAfter(LEAD - 2, () -> helper.assertTrue(countBox(helper, Blocks.OAK_PLANKS) == 0 && BuilderJobs.progress(player.getUUID()) == 0,
                        "提前量之内不放，进度按真放算也是 0"))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.FINISHED && result.done() == 9, "做完 9 格，实际 " + result);
                    helper.assertTrue(countBox(helper, Blocks.OAK_PLANKS) == 9 && count(player, Items.OAK_PLANKS) == 3, "放 9 格、用 9 块");
                    helper.assertTrue(mru - MRUStaffItem.storedMRU(stack) == 9 * placeCost(stack), "扣 9 格的 MRU");
                    List<S2CBuilderFlightPacket> placed = sentTo(player, S2CBuilderFlightPacket.KIND_PLACE);
                    helper.assertTrue(placed.size() == 1 && placed.get(0).leadTicks() == LEAD && placed.get(0).jobId() > 0, "一个飞行包，提前量 10");
                    helper.assertTrue(!sentTo(player, S2CBuilderFlightPacket.KIND_END).isEmpty(), "做完也发收尾包");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 计划之后、执行之前世界变了：照旧逐格复核
    // ------------------------------------------------------------------

    /** 计划之后有一格被别人放了石头：执行时跳过它，不扣料不扣 MRU，石头不动 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void builderFlightOccupiedAfterPlan(GameTestHelper helper) {
        ItemStack stack = plane(BuilderMode.FILL, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 9));
        int mru = MRUStaffItem.storedMRU(stack);
        startOk(helper, player);
        helper.startSequence()
                .thenExecuteAfter(3, () -> helper.setBlock(new BlockPos(2, 3, 3), Blocks.STONE))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(cellsSent(player, S2CBuilderFlightPacket.KIND_PLACE) == 9, "计划时 9 格都要放");
                    helper.assertTrue(result(helper, player).done() == 8, "只放成 8 格，实际 " + result(helper, player).done());
                    helper.assertTrue(helper.getBlockState(new BlockPos(2, 3, 3)).is(Blocks.STONE), "被占的那格不动");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 1, "只用 8 块，实际剩 " + count(player, Items.OAK_PLANKS));
                    helper.assertTrue(mru - MRUStaffItem.storedMRU(stack) == 8 * placeCost(stack), "只扣 8 格的 MRU");
                })
                .thenSucceed();
    }

    /** 计划之后领地改成拒绝一格：执行时那格照真放被拒，料原样留着，不扣 MRU */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void builderFlightDeniedAfterPlan(GameTestHelper helper) {
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        ItemStack stack = plane(BuilderMode.FILL, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 9));
        int mru = MRUStaffItem.storedMRU(stack);
        startOk(helper, player);
        helper.startSequence()
                .thenExecuteAfter(3, () -> claims.deny(new BlockPos(2, 3, 3)))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.done() == 8 && result.denied() == 1, "放 8 格、被拒 1 格，实际 " + result);
                    helper.assertTrue(helper.getBlockState(new BlockPos(2, 3, 3)).isAir(), "被拒的那格还是空气");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 1, "被拒的料不扣，实际剩 " + count(player, Items.OAK_PLANKS));
                    helper.assertTrue(mru - MRUStaffItem.storedMRU(stack) == 8 * placeCost(stack), "被拒的格不扣 MRU");
                })
                .thenSucceed();
    }

    /** 计划之后材料被拿走 5 块：执行时只放得成 4 格，按「材料用完」结束，不欠料不多扣 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void builderFlightMaterialTakenAfterPlan(GameTestHelper helper) {
        ItemStack stack = plane(BuilderMode.FILL, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 9));
        int mru = MRUStaffItem.storedMRU(stack);
        startOk(helper, player);
        helper.startSequence()
                .thenExecuteAfter(3, () -> player.getInventory().items.get(1).setCount(4))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.OUT_OF_MATERIAL && result.done() == 4, "放 4 格后材料用完，实际 " + result);
                    helper.assertTrue(countBox(helper, Blocks.OAK_PLANKS) == 4 && count(player, Items.OAK_PLANKS) == 0, "放下 4 格、料用光");
                    helper.assertTrue(mru - MRUStaffItem.storedMRU(stack) == 4 * placeCost(stack), "只扣 4 格的 MRU");
                    helper.assertTrue(!sentTo(player, S2CBuilderFlightPacket.KIND_END).isEmpty(), "结束时发收尾包");
                })
                .thenSucceed();
    }

    /** 计划之后魔杖离手：下一刻复核不过，按中断结束，一格不放（魔杖离手不触发事件） */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void builderFlightWandLeftHandAfterPlan(GameTestHelper helper) {
        interruptBeforeExecute(helper, player -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY), false);
    }

    // ------------------------------------------------------------------
    // 数量守恒
    // ------------------------------------------------------------------

    /** 撤销：真方块当刻就删（不为特效推迟），9 块一块不多一块不少地退回，删成的格子发飞回的包、不放原版破坏效果 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 160)
    public static void builderFlightUndoExact(GameTestHelper helper) {
        ItemStack stack = plane(BuilderMode.FILL, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 20));
        startOk(helper, player);
        int[] mru = {0};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 11, "放 9 格用 9 块");
                    mru[0] = MRUStaffItem.storedMRU(stack);
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.STARTED, "应能撤销");
                })
                .thenExecuteAfter(3, () -> helper.assertTrue(countBox(helper, Blocks.AIR) == 9, "撤销当刻就删，不等提前量"))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.kind() == BuilderJobs.Kind.UNDO && result.done() == 9, "撤了 9 格，实际 " + result);
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 20, "9 块退回来，实际 " + count(player, Items.OAK_PLANKS));
                    helper.assertTrue(MRUStaffItem.storedMRU(stack) == mru[0], "撤销不扣也不退 MRU");
                    helper.assertTrue(cellsSent(player, S2CBuilderFlightPacket.KIND_UNDO) == 9, "删成的 9 格都发飞回的包");
                    helper.assertTrue(result.peakEffects() == 0, "发了飞回包的撤销不放原版破坏效果，实际每刻最多 " + result.peakEffects() + " 格");
                    helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.NOTHING_TO_UNDO, "再撤没有东西");
                })
                .thenSucceed();
    }

    /** 混铺 4 × 4（圆石、木板各一半权重）：每种放下的格数 + 剩下的料 = 原来的料 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 160)
    public static void builderFlightMixConserved(GameTestHelper helper) {
        ItemStack stack = plane(BuilderMode.FILL, 4, 4, Blocks.OAK_PLANKS, BuilderModules.Kind.MIX);
        BuilderMix.write(stack, List.of(new BuilderMix.Entry(Blocks.COBBLESTONE, 1), new BuilderMix.Entry(Blocks.OAK_PLANKS, 1)), 7);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        BuilderArea area = BuilderJobs.areaOf(player, stack, BuilderShape.PLANE, null);
        player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 20));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 20));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    int cobble = countArea(level, area, Blocks.COBBLESTONE);
                    int planks = countArea(level, area, Blocks.OAK_PLANKS);
                    helper.assertTrue(result(helper, player).done() == 16 && cobble + planks == 16, "铺满 16 格");
                    helper.assertTrue(cobble + count(player, Items.COBBLESTONE) == 20 && planks + count(player, Items.OAK_PLANKS) == 20,
                            "各种放下的 + 剩下的 = 原来的 20，实际圆石 " + cobble + " + " + count(player, Items.COBBLESTONE)
                                    + "、木板 " + planks + " + " + count(player, Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    /** 全部替换 + 回收：9 格泥土换成木板，泥土 9 块进随身容器，木板从容器里扣 9 块，地上没有掉落物 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 160)
    public static void builderFlightSalvageConserved(GameTestHelper helper) {
        ItemStack stack = plane(BuilderMode.REPLACE_ALL, 3, 3, Blocks.OAK_PLANKS, BuilderModules.Kind.SALVAGE);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        BuilderArea area = BuilderJobs.areaOf(player, stack, BuilderShape.PLANE, null);
        ServerLevel level = helper.getLevel();
        for (int i = 0; i < area.size(); i++) {
            level.setBlockAndUpdate(area.cellAt(i, true), Blocks.DIRT.defaultBlockState());
        }
        ItemStack bag = new ItemStack(Items.BUNDLE);
        IItemHandler handler = BuilderTestContainers.handler(bag);
        handler.insertItem(0, new ItemStack(Items.OAK_PLANKS, 10), false);
        player.getInventory().items.set(1, bag);
        level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(6D)).forEach(ItemEntity::discard);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(result(helper, player).done() == 9 && countArea(level, area, Blocks.OAK_PLANKS) == 9, "9 格都换成木板");
                    helper.assertTrue(countIn(handler, Items.OAK_PLANKS) == 1, "木板从容器里扣 9 块，实际剩 " + countIn(handler, Items.OAK_PLANKS));
                    int dirt = countIn(handler, Items.DIRT) + count(player, Items.DIRT);
                    int dropped = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(6D), e -> e.getItem().is(Items.DIRT)).size();
                    helper.assertTrue(dirt == 9 && dropped == 0, "换下来的泥土 9 块都收回，实际 " + dirt + "，掉在地上的 " + dropped);
                })
                .thenSucceed();
    }

    /** 材料用完暂停：计划了没执行的丢掉、发收尾包；补料后从停下的那格重新计划，最后整单 9 格、用料 9 块 */
    @GameTest(template = EMPTY, batch = PAUSE_BATCH, timeoutTicks = 300)
    public static void builderFlightPauseReplans(GameTestHelper helper) {
        ItemStack stack = plane(BuilderMode.FILL, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 4));
        int mru = MRUStaffItem.storedMRU(stack);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(player.getUUID(), helper.getLevel().getServer().getTickCount());
                    helper.assertTrue(snapshot != null && snapshot.paused(), "还没暂停");
                })
                .thenExecute(() -> {
                    helper.assertTrue(countBox(helper, Blocks.OAK_PLANKS) == 4 && count(player, Items.OAK_PLANKS) == 0, "暂停时放了 4 格、料用光");
                    helper.assertTrue(sentTo(player, S2CBuilderFlightPacket.KIND_END).size() == 1, "暂停时发一次收尾包");
                    player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 5));
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.FINISHED && result.done() == 9, "补料后做完 9 格，实际 " + result);
                    helper.assertTrue(countBox(helper, Blocks.OAK_PLANKS) == 9 && count(player, Items.OAK_PLANKS) == 0, "一共用 9 块");
                    helper.assertTrue(mru - MRUStaffItem.storedMRU(stack) == 9 * placeCost(stack), "一共扣 9 格的 MRU");
                    helper.assertTrue(cellsSent(player, S2CBuilderFlightPacket.KIND_PLACE) == 9 + 5, "接着做时剩下的 5 格重新发包");
                })
                .thenSucceed();
    }

    /** 每刻耗时预算为 0（每刻只做得成一格）：执行拖到后面几刻，飞行包只在计划时发一次，不因为挪到下一刻另发 */
    @GameTest(template = EMPTY, batch = TIME_BATCH, timeoutTicks = 200)
    public static void builderFlightCarryNoResend(GameTestHelper helper) {
        ItemStack stack = plane(BuilderMode.FILL, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 9));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(result(helper, player).done() == 9 && count(player, Items.OAK_PLANKS) == 0, "做完 9 格");
                    helper.assertTrue(cellsSent(player, S2CBuilderFlightPacket.KIND_PLACE) == 9, "9 格各发一次，实际 "
                            + cellsSent(player, S2CBuilderFlightPacket.KIND_PLACE));
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 提前量为 0
    // ------------------------------------------------------------------

    /** 提前量为 0：施工和撤销都当刻做，一个飞行包都不发 */
    @GameTest(template = EMPTY, batch = ZERO_BATCH, timeoutTicks = 160)
    public static void builderFlightZeroLeadNoPacket(GameTestHelper helper) {
        ItemStack stack = plane(BuilderMode.FILL, 3, 3, Blocks.OAK_PLANKS);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 9));
        startOk(helper, player);
        helper.startSequence()
                .thenExecuteAfter(3, () -> helper.assertTrue(countBox(helper, Blocks.OAK_PLANKS) == 9, "提前量为 0 时开工就放"))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> helper.assertTrue(BuilderJobs.undo(player) == BuilderJobs.StartResult.STARTED, "应能撤销"))
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 9 && countBox(helper, Blocks.AIR) == 9, "撤销退回 9 块");
                    long mine = BuilderFlightPlan.recorded().stream().filter(sent -> sent.packet().casterId() == player.getId()).count();
                    helper.assertTrue(mine == 0, "提前量为 0 时不发包，实际发了 " + mine + " 个");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 包
    // ------------------------------------------------------------------

    /** 编解码来回一致；调色板超过 8 种、格数超过 256、下标越界、种类不认得的包解码时丢掉 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFlightPacketLimits(GameTestHelper helper) {
        int stone = Block.getId(Blocks.STONE.defaultBlockState());
        int dirt = Block.getId(Blocks.DIRT.defaultBlockState());
        S2CBuilderFlightPacket packet = new S2CBuilderFlightPacket(S2CBuilderFlightPacket.KIND_PLACE, 11, 22, LEAD, new BlockPos(-5, 60, 9).asLong(),
                new int[] {stone, dirt}, new int[] {S2CBuilderFlightPacket.packCell(0, 0, 0, 0), S2CBuilderFlightPacket.packCell(255, 1, 200, 1)});
        S2CBuilderFlightPacket back = roundTrip(packet);
        helper.assertTrue(back.kind() == 0 && back.casterId() == 11 && back.jobId() == 22 && back.leadTicks() == LEAD && back.count() == 2,
                "包头来回一致");
        helper.assertTrue(back.pos(1).equals(new BlockPos(250, 61, 209)) && back.stateId(1) == dirt && back.stateId(0) == stone, "格子和调色板来回一致");
        S2CBuilderFlightPacket single = roundTrip(new S2CBuilderFlightPacket(S2CBuilderFlightPacket.KIND_UNDO, 1, 2, 0, 0L, new int[] {stone},
                new int[] {S2CBuilderFlightPacket.packCell(3, 4, 5, 0)}));
        helper.assertTrue(single.pos(0).equals(new BlockPos(3, 4, 5)) && single.stateId(0) == stone, "只有一种方块时不写下标");
        S2CBuilderFlightPacket end = roundTrip(new S2CBuilderFlightPacket(S2CBuilderFlightPacket.KIND_END, 1, 2, LEAD, 0L, new int[0], new int[0]));
        helper.assertTrue(end.kind() == S2CBuilderFlightPacket.KIND_END && end.count() == 0, "收尾包没有格子");

        FriendlyByteBuf palette = header(0);
        palette.writeVarInt(9);
        for (int i = 0; i < 9; i++) {
            palette.writeVarInt(stone);
        }
        palette.writeVarInt(0);
        helper.assertTrue(rejects(palette), "调色板 9 种应丢包");

        FriendlyByteBuf count = header(0);
        count.writeVarInt(1);
        count.writeVarInt(stone);
        count.writeVarInt(257);
        for (int i = 0; i < 257 * 3; i++) {
            count.writeByte(0);
        }
        helper.assertTrue(rejects(count), "257 格应丢包");

        FriendlyByteBuf index = header(0);
        index.writeVarInt(2);
        index.writeVarInt(stone);
        index.writeVarInt(dirt);
        index.writeVarInt(1);
        index.writeByte(0);
        index.writeByte(0);
        index.writeByte(0);
        index.writeByte(2);
        helper.assertTrue(rejects(index), "调色板下标越界应丢包");

        FriendlyByteBuf noPalette = header(0);
        noPalette.writeVarInt(0);
        noPalette.writeVarInt(1);
        noPalette.writeByte(0);
        noPalette.writeByte(0);
        noPalette.writeByte(0);
        helper.assertTrue(rejects(noPalette), "有格子没调色板应丢包");

        FriendlyByteBuf kind = header(3);
        kind.writeVarInt(0);
        kind.writeVarInt(0);
        helper.assertTrue(rejects(kind), "不认得的种类应丢包");
        helper.succeed();
    }

    /**
     * 拆包：300 格拆成 256 + 44；9 种方块拆成 8 种 + 1 种；某一维跨度超过 255 从那一格起另开一包。
     * 拆完的每个包都能编解码，按顺序拼回来和原来的格子、方块一一对上
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFlightPacking(GameTestHelper helper) {
        int stone = Block.getId(Blocks.STONE.defaultBlockState());
        LongArrayList many = new LongArrayList();
        IntArrayList manyStates = new IntArrayList();
        for (int i = 0; i < 300; i++) {
            many.add(new BlockPos(100 + i % 20, -60 + i / 100, 40 + i / 20 % 5).asLong());
            manyStates.add(stone);
        }
        List<S2CBuilderFlightPacket> split = BuilderFlightPlan.pack(S2CBuilderFlightPacket.KIND_PLACE, 1, 1, LEAD, many, manyStates);
        helper.assertTrue(split.size() == 2 && split.get(0).count() == 256 && split.get(1).count() == 44, "300 格拆成 256 + 44");
        expectSame(helper, split, many, manyStates);

        Block[] kinds = {Blocks.STONE, Blocks.DIRT, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.GLASS, Blocks.SAND, Blocks.GRAVEL, Blocks.BRICKS, Blocks.GOLD_BLOCK};
        LongArrayList nine = new LongArrayList();
        IntArrayList nineStates = new IntArrayList();
        for (int i = 0; i < kinds.length; i++) {
            nine.add(new BlockPos(i, 0, 0).asLong());
            nineStates.add(Block.getId(kinds[i].defaultBlockState()));
        }
        List<S2CBuilderFlightPacket> byPalette = BuilderFlightPlan.pack(S2CBuilderFlightPacket.KIND_PLACE, 1, 1, LEAD, nine, nineStates);
        helper.assertTrue(byPalette.size() == 2 && byPalette.get(0).palette().length == 8 && byPalette.get(1).palette().length == 1,
                "9 种拆成 8 + 1");
        expectSame(helper, byPalette, nine, nineStates);

        LongArrayList wide = new LongArrayList();
        IntArrayList wideStates = new IntArrayList();
        for (int x : new int[] {0, 100, 255, 256, 600}) {
            wide.add(new BlockPos(x - 300, 5, -2).asLong());
            wideStates.add(stone);
        }
        List<S2CBuilderFlightPacket> bySpan = BuilderFlightPlan.pack(S2CBuilderFlightPacket.KIND_PLACE, 1, 1, LEAD, wide, wideStates);
        helper.assertTrue(bySpan.size() == 3 && bySpan.get(0).count() == 3, "跨度 255 以内同一包，超了另开：应 3 包，实际 " + bySpan.size());
        expectSame(helper, bySpan, wide, wideStates);
        helper.succeed();
    }

    private static void expectSame(GameTestHelper helper, List<S2CBuilderFlightPacket> packets, LongArrayList positions, IntArrayList states) {
        int k = 0;
        for (S2CBuilderFlightPacket raw : packets) {
            S2CBuilderFlightPacket packet = roundTrip(raw);
            helper.assertTrue(packet.count() <= S2CBuilderFlightPacket.MAX_CELLS && packet.palette().length <= S2CBuilderFlightPacket.MAX_PALETTE, "包在上限内");
            for (int i = 0; i < packet.count(); i++, k++) {
                helper.assertTrue(packet.pos(i).asLong() == positions.getLong(k) && packet.stateId(i) == states.getInt(k),
                        "第 " + k + " 格拼回来对不上：" + packet.pos(i) + " / " + BlockPos.of(positions.getLong(k)));
            }
        }
        helper.assertTrue(k == positions.size(), "拼回来的格数应等于原来的 " + positions.size() + "，实际 " + k);
    }

    /** 收包人：施法者本人总在（换了维度也在）；其他人要在施工那个维度、离外框中心 16 格以内 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFlightRecipients(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel nether = Objects.requireNonNull(level.getServer().getLevel(Level.NETHER));
        BlockPos center = helper.absolutePos(new BlockPos(2, 3, 3));
        BoundingBox box = new BoundingBox(center.getX() - 1, center.getY(), center.getZ() - 1, center.getX() + 1, center.getY(), center.getZ() + 1);
        ServerPlayer caster = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-fly-caster");
        ServerPlayer near = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-fly-near");
        near.moveTo(center.getX() + 10.5D, center.getY(), center.getZ() + 0.5D, 0F, 0F);
        ServerPlayer far = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-fly-far");
        far.moveTo(center.getX() + 30.5D, center.getY(), center.getZ() + 0.5D, 0F, 0F);
        ServerPlayer elsewhere = new ServerPlayer(level.getServer(), nether, new GameProfile(UUID.randomUUID(), "ec5-fly-nether"));
        elsewhere.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0F, 0F);
        List<ServerPlayer> got = BuilderFlightPlan.recipients(level, caster, box, List.of(caster, near, far, elsewhere));
        helper.assertTrue(got.size() == 2 && got.contains(caster) && got.contains(near), "只发给施法者和 16 格内同维度的人，实际 " + got.size() + " 人");
        ServerPlayer away = new ServerPlayer(level.getServer(), nether, new GameProfile(UUID.randomUUID(), "ec5-fly-away"));
        List<ServerPlayer> moved = BuilderFlightPlan.recipients(level, away, box, List.of(near));
        helper.assertTrue(moved.size() == 2 && moved.contains(away), "施法者换了维度也收得到");
        helper.assertTrue(BuilderFlightPlan.recipients(null, caster, box, List.of(near)).equals(List.of(caster)), "维度不在了只发施法者");
        helper.succeed();
    }
}
