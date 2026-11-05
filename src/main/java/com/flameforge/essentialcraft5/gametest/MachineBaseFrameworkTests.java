package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ItemFilters;
import com.flameforge.essentialcraft5.block.FacingRule;
import com.flameforge.essentialcraft5.block.MachineOwnerStatus;
import com.flameforge.essentialcraft5.block.RedstonePulse;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PulseGate;
import com.flameforge.essentialcraft5.blockentity.RedstonePulseReceiver;
import com.flameforge.essentialcraft5.item.MagnetizingStaffItem;
import com.flameforge.essentialcraft5.menu.GhostSlot;
import com.flameforge.essentialcraft5.menu.GhostSlots;
import com.flameforge.essentialcraft5.menu.HeldItemMenu;
import com.flameforge.essentialcraft5.registry.ECBlockEntities;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 第四批框架 C「方块、界面与规则」的测试（batch4 §4.8 F4-8、§4.9 F4-9、§4.10 F4-10、§4.12 F4-12）。
 * <p>
 * 框架阶段还没有用这些基类的方块（补种器、破坏器由 4a 模块注册；测试时不能临时 new 方块，注册表已冻结），所以：
 * 上升沿只测纯逻辑（{@link PulseGate}）和 {@link RedstonePulse} 的各个入口——借充能仓的方块实体类型放一台只在测试里的 {@link TestPulseMachine}
 * （同 {@code MachineFrameworkTests.TestESPEMachine} 的写法），手动按刻调 {@code RedstonePulse.tick}；
 * {@code TRIGGERED} 的上升 / 下降沿借原版投掷器（它的状态里有 TRIGGERED）。方块层整条接线由第一个用它的模块测（batch4 §4.8）。
 * 过滤器的套娃用 {@link ItemFilters.Spec} 直接构造（过滤器物品 ECLogistics 才注册）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MachineBaseFrameworkTests {

    private static final String EMPTY = "empty";

    private MachineBaseFrameworkTests() {
    }

    // ==================================================================
    // F4-8 红石上升沿与朝向
    // ==================================================================

    /** 只在测试里的吃脉冲的机器：借充能仓的方块实体类型，0 MRU、0 格；记脉冲次数，可以要求续做 n 刻、可以让下一次脉冲抛异常 */
    private static final class TestPulseMachine extends MRUBlockEntity implements RedstonePulseReceiver {
        private final PulseGate gate;
        private int pulses;
        private int continues;
        private int continueFor;
        private int remaining;
        private boolean throwNext;

        TestPulseMachine(BlockPos pos, int interval) {
            super(ECBlockEntities.CHARGING_CHAMBER.get(), pos, ECBlocks.CHARGING_CHAMBER.get().defaultBlockState(), () -> 0, 0);
            this.gate = new PulseGate(() -> interval);
        }

        @Override
        protected boolean hasBoundGemSlot() {
            return false;
        }

        @Override
        public PulseGate pulseGate() {
            return gate;
        }

        @Override
        public boolean onRedstonePulse(ServerLevel level, BlockState state) {
            if (throwNext) {
                throwNext = false;
                throw new IllegalStateException("MachineBaseFrameworkTests 故意抛出，用来测 RedstonePulse 接住异常（不是 bug）");
            }
            pulses++;
            remaining = continueFor;
            return remaining > 0;
        }

        @Override
        public boolean continuePulse(ServerLevel level, BlockState state) {
            continues++;
            return --remaining > 0;
        }

        @Override
        public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return null;
        }
    }

    /** 在 relative 放一台测试机器（先放充能仓再换方块实体） */
    private static TestPulseMachine placePulseMachine(GameTestHelper helper, BlockPos relative, int interval) {
        helper.setBlock(relative, ECBlocks.CHARGING_CHAMBER.get());
        TestPulseMachine be = new TestPulseMachine(helper.absolutePos(relative), interval);
        helper.getLevel().setBlockEntity(be);
        helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(relative)) == be, "测试机器应已放进世界");
        return be;
    }

    /** PulseGate 纯逻辑：间隔按游戏时间、挂起只记一次、续做到间隔结束、存读档、时间倒退不卡死、间隔现取 */
    @GameTest(template = EMPTY)
    public static void pulseGateLogic(GameTestHelper helper) {
        int[] interval = {20};
        PulseGate gate = new PulseGate(() -> interval[0]);
        helper.assertTrue(gate.isReady(0) && gate.readyAt() == PulseGate.NEVER, "新闸门应随时可触发");
        helper.assertTrue(gate.nextWake(100) == PulseGate.NEVER, "没挂起、没续做时不用醒");
        gate.markPending();
        helper.assertTrue(gate.nextWake(100) == 101, "新闸门挂起后应下一刻醒");

        helper.assertTrue(gate.tryFire(100) && gate.lastFire() == 100 && !gate.isPending(), "第一次应触发并清掉挂起");
        helper.assertFalse(gate.tryFire(119), "间隔 20 内（第 19 刻）不应触发");
        helper.assertTrue(gate.lastFire() == 100, "没触发时不应改上次时间");
        gate.markPending();
        gate.markPending();
        helper.assertTrue(gate.isPending() && gate.nextWake(105) == 120, "冷却中挂起（记一次）应在间隔结束那刻醒");
        helper.assertTrue(gate.tryFire(120) && !gate.isPending(), "到点应触发");

        gate.startContinuing();
        helper.assertTrue(gate.mayContinue(121) && gate.mayContinue(139), "间隔内应能续做");
        helper.assertFalse(gate.mayContinue(140), "到间隔结束应停止续做");
        helper.assertTrue(gate.nextWake(121) == 122, "续做时应下一刻醒");
        helper.assertTrue(gate.tryFire(140) && !gate.isContinuing(), "新的一次触发应结束上一次续做");

        interval[0] = 5;
        helper.assertTrue(gate.isReady(145) && !gate.isReady(144), "间隔每次现取（配置改了立刻生效）");
        helper.assertTrue(gate.tryFire(145), "新间隔下应触发");
        helper.assertTrue(gate.isReady(10), "游戏时间比上次还早（回档）时应当成可以触发");

        gate.markPending();
        CompoundTag tag = new CompoundTag();
        gate.save(tag, "pulse");
        PulseGate loaded = new PulseGate(() -> 5);
        loaded.load(tag, "pulse");
        helper.assertTrue(loaded.lastFire() == 145 && loaded.isPending() && !loaded.isContinuing(), "存读档应保留上次时间和挂起");

        CompoundTag emptyTag = new CompoundTag();
        new PulseGate(() -> 5).save(emptyTag, "pulse");
        helper.assertFalse(emptyTag.contains("pulse"), "从没触发过的闸门不应写存档");
        loaded.load(emptyTag, "pulse");
        helper.assertTrue(loaded.lastFire() == PulseGate.NEVER && !loaded.isPending(), "读不到时应回到初始状态");

        PulseGate unlimited = new PulseGate(() -> 0);
        helper.assertTrue(unlimited.tryFire(7) && unlimited.tryFire(7), "间隔 0 应随时可触发");
        helper.succeed();
    }

    /**
     * RedstonePulse.tick 的调度：触发一次后续做 3 刻；续做期间来的上升沿挂起到间隔结束才执行（中间排着计划刻）；
     * 模块抛异常时被接住、这次作废、不续做。
     */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void redstonePulseSchedulesAndContinues(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos throwingRel = new BlockPos(3, 1, 3);
        ServerLevel level = helper.getLevel();
        long[] firstFire = new long[1];
        TestPulseMachine[] holder = new TestPulseMachine[2];

        helper.runAtTickTime(1, () -> {
            TestPulseMachine machine = placePulseMachine(helper, rel, 10);
            holder[0] = machine;
            machine.continueFor = 3;
            machine.pulseGate().markPending();
            BlockPos pos = helper.absolutePos(rel);
            RedstonePulse.tick(level.getBlockState(pos), level, pos);
            firstFire[0] = level.getGameTime();
            helper.assertTrue(machine.pulses == 1 && machine.pulseGate().isContinuing(), "挂起的上升沿应触发一次并进入续做");
            helper.assertTrue(level.getBlockTicks().hasScheduledTick(pos, ECBlocks.CHARGING_CHAMBER.get()), "续做应排下一刻的计划刻");
        });
        for (int t = 2; t <= 4; t++) {
            int tick = t;
            helper.runAtTickTime(t, () -> {
                TestPulseMachine machine = holder[0];
                if (tick == 2) {
                    machine.pulseGate().markPending();   // 续做期间又来一个上升沿
                }
                BlockPos pos = helper.absolutePos(rel);
                RedstonePulse.tick(level.getBlockState(pos), level, pos);
            });
        }
        helper.runAtTickTime(5, () -> {
            TestPulseMachine machine = holder[0];
            helper.assertTrue(machine.continues == 3 && !machine.pulseGate().isContinuing(), "应续做 3 刻后停止，实际 " + machine.continues);
            helper.assertTrue(machine.pulses == 1 && machine.pulseGate().isPending(), "间隔内的上升沿应挂起、不执行");
            BlockPos pos = helper.absolutePos(rel);
            RedstonePulse.tick(level.getBlockState(pos), level, pos);
            helper.assertTrue(machine.pulses == 1, "间隔没到不应执行");
            helper.assertTrue(level.getBlockTicks().hasScheduledTick(pos, ECBlocks.CHARGING_CHAMBER.get()), "挂起的上升沿应排在间隔结束时");
        });
        helper.runAtTickTime(13, () -> {
            TestPulseMachine machine = holder[0];
            BlockPos pos = helper.absolutePos(rel);
            helper.assertTrue(level.getGameTime() - firstFire[0] >= 10, "测试时序不对：距第一次触发还不到 10 刻");
            machine.continueFor = 0;
            RedstonePulse.tick(level.getBlockState(pos), level, pos);
            helper.assertTrue(machine.pulses == 2 && !machine.pulseGate().isPending() && !machine.pulseGate().isContinuing(),
                    "间隔到了应执行挂起的那次（只一次）");

            TestPulseMachine throwing = placePulseMachine(helper, throwingRel, 10);
            holder[1] = throwing;
            throwing.throwNext = true;
            throwing.continueFor = 5;
            throwing.pulseGate().markPending();
            BlockPos throwingPos = helper.absolutePos(throwingRel);
            RedstonePulse.tick(level.getBlockState(throwingPos), level, throwingPos);
            helper.assertTrue(throwing.pulses == 0 && !throwing.pulseGate().isPending() && !throwing.pulseGate().isContinuing()
                    && throwing.pulseGate().lastFire() != PulseGate.NEVER, "抛异常的那次应作废（算触发过、不续做、不挂起）");
            helper.succeed();
        });
    }

    /**
     * TRIGGERED 的维护（借原版投掷器，它的状态里有 TRIGGERED）：放下时按当时信号初始化；上升沿置真并排计划刻，下降沿置假；
     * 换成这个方块时信号不一致只排计划刻、不马上改。邻格的红石块用标志 2 放（不通知投掷器，免得原版自己的逻辑先动）。
     */
    @GameTest(template = EMPTY)
    public static void redstonePulseTriggeredState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(rel);
        BlockPos side = pos.east();
        helper.setBlock(rel, Blocks.DROPPER.defaultBlockState().setValue(RedstonePulse.TRIGGERED, false));

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        // 点西边石头的东面：放置格就是 pos（点空气会被当成「替换被点的格子」，放置格就不对了）
        helper.setBlock(rel.west(), Blocks.STONE);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos.west()), Direction.EAST, pos.west(), false);
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(Items.DROPPER), hit);
        helper.assertTrue(context.getClickedPos().equals(pos), "放置格应是投掷器那格");
        BlockState base = Blocks.DROPPER.defaultBlockState();
        helper.assertFalse(RedstonePulse.placementState(base, context).getValue(RedstonePulse.TRIGGERED), "没信号时放下应是未触发");

        level.setBlock(side, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.assertTrue(RedstonePulse.placementState(base, context).getValue(RedstonePulse.TRIGGERED), "放在有信号的地方应直接记成已触发（不误触发）");

        RedstonePulse.neighborChanged(level.getBlockState(pos), level, pos);
        helper.assertTrue(level.getBlockState(pos).getValue(RedstonePulse.TRIGGERED), "上升沿应置 TRIGGERED");
        helper.assertTrue(level.getBlockTicks().hasScheduledTick(pos, Blocks.DROPPER), "上升沿应排计划刻");

        level.setBlock(side, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        RedstonePulse.neighborChanged(level.getBlockState(pos), level, pos);
        helper.assertFalse(level.getBlockState(pos).getValue(RedstonePulse.TRIGGERED), "下降沿应清 TRIGGERED");

        // onPlace：旧方块不是它、信号和状态不一致 → 只排计划刻，状态不变（到计划刻里再同步）
        BlockPos other = helper.absolutePos(new BlockPos(1, 1, 3));
        level.setBlock(other.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(other, Blocks.DROPPER.defaultBlockState().setValue(RedstonePulse.TRIGGERED, false), Block.UPDATE_CLIENTS);
        RedstonePulse.onPlace(level.getBlockState(other), level, other, Blocks.AIR.defaultBlockState());
        helper.assertTrue(level.getBlockTicks().hasScheduledTick(other, Blocks.DROPPER), "信号不一致时应排一刻去同步");
        helper.assertFalse(level.getBlockState(other).getValue(RedstonePulse.TRIGGERED), "onPlace 里不应直接改状态");
        helper.succeed();
    }

    /** FacingRule 三种规则（realPlayer 构造放置上下文）；没有玩家时朝向点中的面；发射器放的照原版 */
    @GameTest(template = EMPTY)
    public static void facingRules(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(target.below()), Direction.UP, target.below(), false);
        ItemStack stack = new ItemStack(Items.STONE);

        player.setYRot(180F);   // 朝北
        player.setYHeadRot(180F);   // 「看向哪边」按头部朝向算（LivingEntity.getViewYRot 返回 yHeadRot），只设身体朝向没用
        player.setXRot(0F);
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, top);
        helper.assertTrue(FacingRule.CLICKED_FACE.apply(context) == Direction.UP, "点中顶面应朝上");
        helper.assertTrue(FacingRule.CLICKED_FACE_OPPOSITE.apply(context) == Direction.DOWN, "点中顶面的反面应朝下");
        helper.assertTrue(FacingRule.FACING_PLACER.apply(context) == Direction.SOUTH, "朝北看的玩家放下应朝南（朝着放置者）");

        player.setXRot(90F);    // 低头
        context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, top);
        helper.assertTrue(FacingRule.FACING_PLACER.apply(context) == Direction.UP, "低头放下应朝上");

        BlockHitResult east = new BlockHitResult(Vec3.atCenterOf(target.west()), Direction.EAST, target.west(), false);
        BlockPlaceContext noPlayer = new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND, stack, east);
        helper.assertTrue(FacingRule.FACING_PLACER.apply(noPlayer) == Direction.EAST, "没有放置者时应退回点中的面");
        DirectionalPlaceContext dispensed = new DirectionalPlaceContext(helper.getLevel(), target, Direction.NORTH, stack, Direction.UP);
        helper.assertTrue(FacingRule.FACING_PLACER.apply(dispensed) == Direction.UP, "发射器放的应照原版（朝下看的反面）");
        helper.succeed();
    }

    /** 没界面机器的主人状态：空手主手才接管；没有主人 / 主人不在线的提示键 */
    @GameTest(template = EMPTY)
    public static void ownerStatusMessage(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        TestPulseMachine machine = placePulseMachine(helper, rel, 20);
        BlockPos pos = helper.absolutePos(rel);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);

        helper.assertTrue(key(MachineOwnerStatus.message(machine)).equals("message.essentialcraft5.machine_owner.none"), "没有主人应提示不会工作");
        helper.assertTrue(MachineOwnerStatus.use(helper.getLevel(), pos, player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "空手主手右键应接管（服务端 CONSUME）");
        helper.assertTrue(MachineOwnerStatus.use(helper.getLevel(), pos, player, InteractionHand.OFF_HAND) == InteractionResult.PASS, "副手那一轮应 PASS");
        player.setShiftKeyDown(true);
        helper.assertTrue(MachineOwnerStatus.use(helper.getLevel(), pos, player, InteractionHand.MAIN_HAND) == InteractionResult.PASS, "潜行应 PASS");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        helper.assertTrue(MachineOwnerStatus.use(helper.getLevel(), pos, player, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                "手里有东西应 PASS（还能对着它放方块）");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(MachineOwnerStatus.use(helper.getLevel(), pos.above(), player, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                "不是机器的格子应 PASS");

        // 主人不在线（realPlayer 不在玩家列表里；在线主人的提示要框架 A 的在线覆盖，由用它的模块测）
        machine.setOwner(UUID.randomUUID(), "Alice");
        Component offline = MachineOwnerStatus.message(machine);
        String expected = ECProtection.Config.requireOwnerOnline()
                ? "message.essentialcraft5.machine_owner.offline" : "message.essentialcraft5.machine_owner.offline_working";
        helper.assertTrue(key(offline).equals(expected), "主人不在线应提示 " + expected + "，实际 " + key(offline));
        helper.assertTrue(offline.getString().contains("Alice") || argsContain(offline, "Alice"), "提示里应有主人名字");
        helper.succeed();
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
    }

    private static boolean argsContain(Component component, String text) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            for (Object arg : translatable.getArgs()) {
                if (String.valueOf(arg).contains(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================================================================
    // F4-9 幽灵格与手持物品菜单
    // ==================================================================

    /** 测试用的手持物品菜单：9 个幽灵格（不收基岩）+ 玩家背包；样本写进手上物品的 test_samples */
    private static final class TestHeldMenu extends HeldItemMenu {
        static final String KEY = "test_samples";
        private final SimpleContainer ghosts = new SimpleContainer(9);

        TestHeldMenu(Inventory inventory, InteractionHand hand) {
            super(null, 0, inventory, hand);
            CompoundTag tag = held().getTag();
            ListTag saved = tag == null ? new ListTag() : tag.getList(KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < saved.size(); i++) {
                CompoundTag entry = saved.getCompound(i);
                ghosts.setItem(entry.getByte("Slot"), GhostSlots.load(entry));
            }
            for (int i = 0; i < 9; i++) {
                addSlot(new GhostSlot(ghosts, i, 62 + i % 3 * 18, 17 + i / 3 * 18, stack -> !stack.is(Items.BEDROCK)));
            }
            addPlayerInventory(inventory, 8, 84);
        }

        @Override
        protected boolean saveTo(ItemStack held) {
            ListTag list = new ListTag();
            for (int i = 0; i < 9; i++) {
                ItemStack sample = ghosts.getItem(i);
                if (!sample.isEmpty()) {
                    CompoundTag entry = GhostSlots.save(sample);
                    entry.putByte("Slot", (byte) i);
                    list.add(entry);
                }
            }
            held.getOrCreateTag().put(KEY, list);
            return true;
        }

        ItemStack ghost(int i) {
            return slots.get(i).getItem();
        }

        /** 菜单槽号：背包下标 9~35 就是同号槽，快捷栏 k 是 36 + k */
        static int hotbarSlot(int k) {
            return 36 + k;
        }
    }

    private static int savedSamples(ItemStack held) {
        CompoundTag tag = held.getTag();
        return tag == null ? 0 : tag.getList(TestHeldMenu.KEY, Tag.TAG_COMPOUND).size();
    }

    private static int itemEntitiesAround(GameTestHelper helper, Player player) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4)).size();
    }

    /** 幽灵格的各种点击：设样本不耗光标、Shift 复制不减背包、数字键 / F、挡手持格、锁定槽、丢弃不掉东西、拖拽和双击跳过、立刻写回 */
    @GameTest(template = EMPTY)
    public static void ghostSlotClicks(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Inventory inventory = player.getInventory();
        inventory.selected = 0;
        ItemStack held = new ItemStack(Items.PAPER);
        inventory.setItem(0, held);
        inventory.setItem(3, new ItemStack(Items.IRON_INGOT, 7));
        inventory.setItem(20, new ItemStack(Items.COBBLESTONE, 10));
        inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.GOLD_INGOT, 2));
        TestHeldMenu menu = new TestHeldMenu(inventory, InteractionHand.MAIN_HAND);
        helper.assertTrue(menu.slots.get(TestHeldMenu.hotbarSlot(0)) instanceof HeldItemMenu.LockedSlot, "手持那格应是锁定槽");
        helper.assertTrue(menu.heldSlot() == 0 && menu.stillValid(player), "刚打开应有效");

        // 左键：样本 = 光标的副本，光标不动；立刻写回
        menu.setCarried(new ItemStack(Items.DIAMOND, 5));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.DIAMOND) && menu.getCarried().getCount() == 5, "左键设样本不应消耗光标");
        helper.assertTrue(menu.ghost(0).is(Items.DIAMOND) && menu.ghost(0).getCount() == 1, "样本应是一个钻石");
        helper.assertTrue(savedSamples(held) == 1, "点击后手上物品的 NBT 应立刻更新");
        // 右键清空；光标空的左键也清空
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(menu.ghost(0).isEmpty() && savedSamples(held) == 0, "右键应清空并写回");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.ghost(0).isEmpty(), "光标空时左键应清空");
        // 格子不收的样本：什么都不变
        menu.setCarried(new ItemStack(Items.BEDROCK));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.ghost(0).isEmpty() && menu.getCarried().is(Items.BEDROCK), "不收的样本应什么都不变");
        menu.setCarried(ItemStack.EMPTY);

        // Shift：从背包复制进第一个空幽灵格，背包不减；Shift 幽灵格 = 清空
        menu.clicked(20, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(menu.ghost(0).is(Items.COBBLESTONE) && inventory.getItem(20).getCount() == 10, "Shift 应复制样本、不减背包");
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(menu.ghost(0).isEmpty(), "Shift 幽灵格应清空");

        // 数字键 / F：取那格的副本
        menu.clicked(1, 3, ClickType.SWAP, player);
        helper.assertTrue(menu.ghost(1).is(Items.IRON_INGOT) && inventory.getItem(3).getCount() == 7, "数字键应设成快捷栏物品的副本");
        menu.clicked(2, Inventory.SLOT_OFFHAND, ClickType.SWAP, player);
        helper.assertTrue(menu.ghost(2).is(Items.GOLD_INGOT) && inventory.getItem(Inventory.SLOT_OFFHAND).getCount() == 2, "F 键应设成副手物品的副本");
        // 数字键 = 手持那格：一律挡掉（对幽灵格、对背包格都是）
        menu.clicked(3, 0, ClickType.SWAP, player);
        helper.assertTrue(menu.ghost(3).isEmpty(), "数字键换手持格对幽灵格应无效");
        menu.clicked(20, 0, ClickType.SWAP, player);
        helper.assertTrue(inventory.getItem(0) == held && inventory.getItem(20).is(Items.COBBLESTONE), "数字键不应把手持的物品换走");
        // 点锁定槽：什么都不变
        int locked = TestHeldMenu.hotbarSlot(0);
        menu.clicked(locked, 0, ClickType.PICKUP, player);
        menu.clicked(locked, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(locked, 1, ClickType.THROW, player);
        helper.assertTrue(inventory.getItem(0) == held && menu.getCarried().isEmpty(), "锁定槽应拿不走");

        // 丢弃键：清空样本，不掉东西
        int before = itemEntitiesAround(helper, player);
        menu.clicked(1, 1, ClickType.THROW, player);
        helper.assertTrue(menu.ghost(1).isEmpty() && itemEntitiesAround(helper, player) == before, "丢弃键应只清空样本、不掉物品");

        // 拖拽、双击收集跳过幽灵格和锁定槽；双击对幽灵格什么都不做
        helper.assertFalse(menu.canDragTo(menu.slots.get(2)) || menu.canDragTo(menu.slots.get(locked)), "拖拽应跳过幽灵格和锁定槽");
        helper.assertFalse(menu.canTakeItemForPickAll(new ItemStack(Items.GOLD_INGOT), menu.slots.get(2)), "双击收集应跳过幽灵格");
        menu.setCarried(new ItemStack(Items.GOLD_INGOT));
        menu.clicked(2, 0, ClickType.PICKUP_ALL, player);
        helper.assertTrue(menu.getCarried().getCount() == 1 && menu.ghost(2).is(Items.GOLD_INGOT), "双击幽灵格不应收集到样本");
        menu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && menu.quickMoveStack(player, 20).isEmpty(), "quickMoveStack 应什么都不搬");

        // 写回的内容和重新打开读到的一致
        TestHeldMenu reopened = new TestHeldMenu(inventory, InteractionHand.MAIN_HAND);
        helper.assertTrue(reopened.ghost(2).is(Items.GOLD_INGOT) && reopened.ghost(0).isEmpty() && reopened.ghost(1).isEmpty(), "重新打开应读回同样的样本");

        // stillValid：切快捷栏、换成另一个对象都失效
        inventory.selected = 1;
        helper.assertFalse(menu.stillValid(player), "切了快捷栏应失效");
        inventory.selected = 0;
        helper.assertTrue(menu.stillValid(player), "切回来是同一个对象应仍有效");
        ItemStack replacement = held.copy();
        inventory.setItem(0, replacement);
        helper.assertFalse(menu.stillValid(player), "换成另一件（哪怕一模一样）应失效");
        // 失效到关界面之间的点击一律不理：不能写进换上来的那件（原作的复制点）
        menu.setCarried(new ItemStack(Items.APPLE));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.ghost(0).isEmpty() && ItemStack.isSameItemSameTags(replacement, held), "换上来的那件不应被写");
        menu.setCarried(ItemStack.EMPTY);
        helper.succeed();
    }

    /** 从副手打开：F 键（40）挡掉，背包里没有锁定槽，数字键照常 */
    @GameTest(template = EMPTY)
    public static void heldMenuFromOffhand(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Inventory inventory = player.getInventory();
        ItemStack held = new ItemStack(Items.PAPER);
        inventory.setItem(Inventory.SLOT_OFFHAND, held);
        inventory.setItem(2, new ItemStack(Items.EMERALD, 3));
        TestHeldMenu menu = new TestHeldMenu(inventory, InteractionHand.OFF_HAND);
        helper.assertTrue(menu.heldSlot() == Inventory.SLOT_OFFHAND && menu.stillValid(player), "副手打开应记 40 号格");
        helper.assertTrue(menu.slots.stream().noneMatch(slot -> slot instanceof HeldItemMenu.LockedSlot), "副手不在背包 36 格里，不应有锁定槽");
        menu.clicked(0, Inventory.SLOT_OFFHAND, ClickType.SWAP, player);
        helper.assertTrue(menu.ghost(0).isEmpty() && inventory.getItem(Inventory.SLOT_OFFHAND) == held, "F 键换副手应无效");
        menu.clicked(TestHeldMenu.hotbarSlot(2), Inventory.SLOT_OFFHAND, ClickType.SWAP, player);
        helper.assertTrue(inventory.getItem(Inventory.SLOT_OFFHAND) == held && inventory.getItem(2).is(Items.EMERALD), "F 键不应把副手的物品换走");
        menu.clicked(0, 2, ClickType.SWAP, player);
        helper.assertTrue(menu.ghost(0).is(Items.EMERALD) && savedSamples(held) == 1, "数字键应照常设样本，并写进副手那件");
        helper.succeed();
    }

    /** 大 NBT 样本存哈希、改一个字就不匹配；整个物品 NBT 超 8 KB 时这次点击撤销 */
    @GameTest(template = EMPTY)
    public static void ghostSampleSizeLimits(GameTestHelper helper) {
        ItemStack book = bigBook('a');
        ItemStack sample = GhostSlots.sampleOf(book);
        helper.assertTrue(GhostSlots.isHashed(sample) && sample.getTag().sizeInBytes() < GhostSlots.MAX_SAMPLE_BYTES, "大书的样本应只存 id + 哈希");
        helper.assertTrue(GhostSlots.matches(sample, book), "哈希样本应匹配原书");
        helper.assertFalse(GhostSlots.matches(sample, bigBook('b')), "改了内容的书不应匹配");
        helper.assertFalse(GhostSlots.matches(sample, new ItemStack(Items.WRITABLE_BOOK)), "空书不应匹配");
        ItemStack loaded = GhostSlots.load(GhostSlots.save(sample));
        helper.assertTrue(GhostSlots.isHashed(loaded) && GhostSlots.matches(loaded, book), "哈希样本存读后应仍然匹配原书");
        ItemStack small = new ItemStack(Items.DIAMOND_SWORD);
        small.setHoverName(Component.literal("Edge"));
        helper.assertFalse(GhostSlots.isHashed(GhostSlots.sampleOf(small)), "小 NBT 的样本应整份保留");
        helper.assertTrue(ItemStack.isSameItemSameTags(GhostSlots.load(GhostSlots.save(small)), small), "小样本存读应原样");

        // 8 KB：手上物品本身已经接近上限，再放一个样本就超 → 撤销
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Inventory inventory = player.getInventory();
        inventory.selected = 0;
        ItemStack held = new ItemStack(Items.PAPER);
        CompoundTag tag = held.getOrCreateTag();
        tag.putString("pad", "");
        int chars = (GhostSlots.MAX_ITEM_BYTES - 100 - tag.sizeInBytes()) / 2;
        tag.putString("pad", "x".repeat(chars));
        inventory.setItem(0, held);
        TestHeldMenu menu = new TestHeldMenu(inventory, InteractionHand.MAIN_HAND);
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.setHoverName(Component.literal("n".repeat(60)));
        menu.setCarried(named);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.ghost(0).isEmpty() && savedSamples(held) == 0, "超过 8 KB 时这次点击应撤销、手上物品不动");
        tag.putString("pad", "x".repeat(Math.max(0, chars - 600)));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.ghost(0).is(Items.DIAMOND) && savedSamples(held) == 1, "腾出空间后同样的点击应生效");
        helper.succeed();
    }

    /** 一本约 200 KB（按 sizeInBytes）的书：100 页、每页 1000 个 fill 字符 */
    private static ItemStack bigBook(char fill) {
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        ListTag pages = new ListTag();
        for (int i = 0; i < 100; i++) {
            pages.add(StringTag.valueOf(String.valueOf(fill).repeat(1000)));
        }
        book.getOrCreateTag().put("pages", pages);
        return book;
    }

    // ==================================================================
    // F4-10 过滤 API
    // ==================================================================

    private static ItemFilters.Compiled compile(ItemFilters.Spec spec) {
        return ItemFilters.compile(spec);
    }

    /** 白 / 黑名单基本匹配、黑名单第一格空（回归）、空名单、NBT 区分、没装过滤器全收 */
    @GameTest(template = EMPTY)
    public static void filterBasics(GameTestHelper helper) {
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack cobble = new ItemStack(Items.COBBLESTONE, 32);
        ItemFilters.Compiled white = compile(ItemFilters.Spec.of(false, stone));
        helper.assertTrue(white.test(stone) && !white.test(dirt), "白名单只收样本");
        ItemFilters.Compiled black = compile(ItemFilters.Spec.of(true, stone));
        helper.assertTrue(!black.test(stone) && black.test(dirt), "黑名单拒样本、收别的");
        // 原作 bug：黑名单第一格是空的就放行一切
        ItemFilters.Compiled blackGap = compile(ItemFilters.Spec.of(true, ItemStack.EMPTY, new ItemStack(Items.COBBLESTONE)));
        helper.assertTrue(!blackGap.test(cobble) && blackGap.test(stone), "黑名单第一格空时仍应按后面的格子拒（数量不影响）");
        helper.assertFalse(compile(ItemFilters.Spec.of(false)).test(stone), "空白名单什么都不收");
        helper.assertTrue(compile(ItemFilters.Spec.of(true)).test(stone), "空黑名单什么都收");

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack enchanted = new ItemStack(Items.IRON_SWORD);
        enchanted.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 1);
        helper.assertFalse(compile(ItemFilters.Spec.of(false, enchanted)).test(sword), "附魔剑的样本不应收普通剑");
        helper.assertFalse(compile(ItemFilters.Spec.of(false, sword)).test(enchanted), "普通剑的样本不应收附魔剑");

        helper.assertTrue(ItemFilters.accepts(ItemStack.EMPTY, dirt) && ItemFilters.accepts(stone, dirt), "槽空或放的不是过滤器应全收");
        helper.assertTrue(ItemFilters.compile(ItemStack.EMPTY).acceptsAll() && !ItemFilters.isFilter(stone), "不是过滤器 = 全收");

        // 大 NBT 样本在过滤器里也按哈希比
        ItemFilters.Compiled bookFilter = compile(ItemFilters.Spec.of(false, bigBook('q')));
        helper.assertTrue(bookFilter.test(bigBook('q')) && !bookFilter.test(bigBook('r')), "哈希样本：同内容收、改一字拒");
        helper.succeed();
    }

    /** 套娃：两层生效（内层 = 它放行的那组物品），第三层当不匹配 */
    @GameTest(template = EMPTY)
    public static void filterNesting(GameTestHelper helper) {
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemFilters.Spec innerWhite = ItemFilters.Spec.of(false, stone);
        ItemFilters.Spec outer = new ItemFilters.Spec(false, false, Set.of(), List.of(ItemFilters.Entry.filter(innerWhite)));
        helper.assertTrue(compile(outer).test(stone) && !compile(outer).test(dirt), "白名单套白名单：内层放行的才收");

        ItemFilters.Spec innerBlack = ItemFilters.Spec.of(true, stone);
        ItemFilters.Spec outerOfBlack = new ItemFilters.Spec(false, false, Set.of(), List.of(ItemFilters.Entry.filter(innerBlack)));
        helper.assertTrue(!compile(outerOfBlack).test(stone) && compile(outerOfBlack).test(dirt), "白名单套黑名单：内层放行的（石头以外）才收");

        ItemFilters.Spec blackOfWhite = new ItemFilters.Spec(true, false, Set.of(), List.of(ItemFilters.Entry.filter(innerWhite)));
        helper.assertTrue(!compile(blackOfWhite).test(stone) && compile(blackOfWhite).test(dirt), "黑名单套白名单：内层放行的被拒");

        ItemFilters.Spec third = new ItemFilters.Spec(false, false, Set.of(), List.of(ItemFilters.Entry.filter(outer)));
        helper.assertFalse(compile(third).test(stone), "第三层应当不匹配");
        ItemFilters.Spec thirdBlack = new ItemFilters.Spec(true, false, Set.of(), List.of(ItemFilters.Entry.filter(outer)));
        helper.assertTrue(compile(thirdBlack).test(stone), "黑名单里的第三层也当不匹配（所以放行）");
        helper.succeed();
    }

    /** 高级开关只有高级过滤器读；按标签开着、这一格没选标签时仍按物品比 */
    @GameTest(template = EMPTY)
    public static void filterAdvancedFlags(GameTestHelper helper) {
        ItemStack damaged = new ItemStack(Items.IRON_SWORD);
        damaged.setDamageValue(50);
        ItemStack fresh = new ItemStack(Items.IRON_SWORD);
        ItemStack enchanted = new ItemStack(Items.IRON_SWORD);
        enchanted.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 1);
        List<ItemFilters.Entry> damagedSample = List.of(ItemFilters.Entry.item(damaged));

        helper.assertFalse(compile(new ItemFilters.Spec(false, false, EnumSet.of(ItemFilters.Flag.IGNORE_DAMAGE), damagedSample)).test(fresh),
                "普通过滤器不读开关：耐久不同不收");
        helper.assertTrue(compile(new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.IGNORE_DAMAGE), damagedSample)).test(fresh),
                "高级 + 忽略耐久：耐久不同也收");
        helper.assertFalse(compile(new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.IGNORE_DAMAGE), damagedSample)).test(enchanted),
                "忽略耐久不忽略附魔");
        helper.assertTrue(compile(new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.IGNORE_NBT), damagedSample)).test(enchanted),
                "高级 + 忽略 NBT：同物品都收");
        helper.assertFalse(compile(new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.MATCH_TAGS),
                List.of(ItemFilters.Entry.item(new ItemStack(Items.OAK_PLANKS))))).test(new ItemStack(Items.BIRCH_PLANKS)), "按标签开着但没选标签：按物品比");
        helper.succeed();
    }

    /**
     * 按标签（第六批 6b，D1 = B）：每格选一个标签，候选在标签里就算同一种、NBT 照样比（耐久值单独比，新建物品自带的 Damage:0 和没有 NBT 算一样）；只有高级 + 开关开着才生效；
     * 样本自己不在所选标签里时退回按物品比（不拿一个和样本无关的标签放行东西）；套娃里每层用自己的开关
     */
    @GameTest(template = EMPTY)
    public static void filterMatchTagsSpec(GameTestHelper helper) {
        List<ItemFilters.Entry> planks = List.of(ItemFilters.Entry.tagged(new ItemStack(Items.OAK_PLANKS), ItemTags.PLANKS));
        ItemStack birch = new ItemStack(Items.BIRCH_PLANKS);
        helper.assertTrue(compile(new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.MATCH_TAGS), planks)).test(birch),
                "高级 + 按标签：橡木木板样本选 minecraft:planks 收白桦木板");
        helper.assertFalse(compile(new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.MATCH_TAGS), planks)).test(new ItemStack(Items.STONE)),
                "不在标签里的不收");
        helper.assertFalse(compile(new ItemFilters.Spec(false, true, Set.of(), planks)).test(birch), "开关没开：按物品比");
        helper.assertFalse(compile(new ItemFilters.Spec(false, false, EnumSet.of(ItemFilters.Flag.MATCH_TAGS), planks)).test(birch), "普通过滤器不读开关");

        ItemStack namedBirch = new ItemStack(Items.BIRCH_PLANKS);
        namedBirch.setHoverName(Component.literal("n"));
        helper.assertFalse(compile(new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.MATCH_TAGS), planks)).test(namedBirch),
                "按标签也比 NBT：改过名的白桦木板不收");
        helper.assertTrue(compile(new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.MATCH_TAGS, ItemFilters.Flag.IGNORE_NBT), planks)).test(namedBirch),
                "按标签 + 忽略 NBT：改过名的也收");

        // 石头样本却选了木板标签（只能是改数据得来的）：按物品比，收石头、不收木板
        List<ItemFilters.Entry> wrong = List.of(ItemFilters.Entry.tagged(new ItemStack(Items.STONE), ItemTags.PLANKS));
        ItemFilters.Compiled stale = compile(new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.MATCH_TAGS), wrong));
        helper.assertTrue(stale.test(new ItemStack(Items.STONE)) && !stale.test(birch), "样本不在所选标签里：退回按物品比");

        ItemFilters.Spec inner = new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.MATCH_TAGS), planks);
        ItemFilters.Spec outerPlain = new ItemFilters.Spec(false, false, Set.of(), List.of(ItemFilters.Entry.filter(inner)));
        helper.assertTrue(compile(outerPlain).test(birch), "普通过滤器里套的高级过滤器按自己的开关按标签比");
        ItemFilters.Spec innerExact = ItemFilters.Spec.of(false, new ItemStack(Items.OAK_PLANKS));
        ItemFilters.Spec outerTags = new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.MATCH_TAGS), List.of(ItemFilters.Entry.filter(innerExact)));
        helper.assertFalse(compile(outerTags).test(birch), "外层的按标签不传给里面的普通过滤器");

        // GhostSlots.nbtMatches：只比 NBT 不比物品
        ItemStack namedDiamond = new ItemStack(Items.DIAMOND);
        namedDiamond.setHoverName(Component.literal("x"));
        ItemStack namedEmerald = new ItemStack(Items.EMERALD);
        namedEmerald.setHoverName(Component.literal("x"));
        helper.assertTrue(GhostSlots.nbtMatches(namedDiamond, namedEmerald, false, false), "NBT 一样的不同物品：nbtMatches 为真");
        helper.assertFalse(GhostSlots.matches(namedDiamond, namedEmerald), "matches 仍要同一种物品");
        helper.assertTrue(GhostSlots.nbtMatches(new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD), false, false), "都没有 NBT：为真");
        helper.assertFalse(GhostSlots.nbtMatches(namedDiamond, new ItemStack(Items.EMERALD), false, false), "一个有名字一个没有：为假");

        // 标签里同时有带耐久和不带耐久的物品（minecraft:creeper_igniters = 打火石 + 火焰弹）：新建的打火石自带 Damage:0，
        // 和「没有 NBT」的火焰弹算一样；损耗过的打火石不算（没开忽略耐久）
        ItemStack freshIgniter = new ItemStack(Items.FLINT_AND_STEEL);
        helper.assertTrue(freshIgniter.getTag() != null && freshIgniter.getTag().contains("Damage") && new ItemStack(Items.FIRE_CHARGE).getTag() == null,
                "前提：新打火石自带 Damage:0、火焰弹没有 NBT");
        EnumSet<ItemFilters.Flag> byTag = EnumSet.of(ItemFilters.Flag.MATCH_TAGS);
        ItemFilters.Compiled fireCharge = compile(new ItemFilters.Spec(false, true, byTag,
                List.of(ItemFilters.Entry.tagged(new ItemStack(Items.FIRE_CHARGE), ItemTags.CREEPER_IGNITERS))));
        helper.assertTrue(fireCharge.test(freshIgniter), "火焰弹样本选 creeper_igniters：收没损耗的新打火石");
        ItemStack wornIgniter = new ItemStack(Items.FLINT_AND_STEEL);
        wornIgniter.setDamageValue(5);
        helper.assertFalse(fireCharge.test(wornIgniter), "损耗为 5 的打火石不收（没开忽略耐久）");
        ItemFilters.Compiled igniter = compile(new ItemFilters.Spec(false, true, byTag,
                List.of(ItemFilters.Entry.tagged(new ItemStack(Items.FLINT_AND_STEEL), ItemTags.CREEPER_IGNITERS))));
        helper.assertTrue(igniter.test(new ItemStack(Items.FIRE_CHARGE)), "反过来：新打火石样本选 creeper_igniters 收火焰弹");
        helper.assertTrue(compile(new ItemFilters.Spec(false, true, EnumSet.of(ItemFilters.Flag.MATCH_TAGS, ItemFilters.Flag.IGNORE_DAMAGE),
                List.of(ItemFilters.Entry.tagged(new ItemStack(Items.FIRE_CHARGE), ItemTags.CREEPER_IGNITERS)))).test(wornIgniter),
                "开了忽略耐久：损耗过的打火石也收");
        helper.succeed();
    }

    /** 方块版：候选取 getCloneItemStack；拿不到物品形态（空气、水）时白名单拒、黑名单收 */
    @GameTest(template = EMPTY)
    public static void filterBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stonePos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos waterPos = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos airPos = helper.absolutePos(new BlockPos(3, 2, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.WATER);
        ItemFilters.Compiled white = compile(ItemFilters.Spec.of(false, new ItemStack(Items.STONE)));
        ItemFilters.Compiled black = compile(ItemFilters.Spec.of(true, new ItemStack(Items.STONE)));
        helper.assertTrue(white.testBlock(level.getBlockState(stonePos), level, stonePos), "白名单石头应收石头方块");
        helper.assertFalse(black.testBlock(level.getBlockState(stonePos), level, stonePos), "黑名单石头应拒石头方块");
        helper.assertTrue(ItemFilters.cloneItem(level.getBlockState(waterPos), level, waterPos).isEmpty(), "水没有物品形态");
        helper.assertFalse(white.testBlock(level.getBlockState(waterPos), level, waterPos), "没有物品形态：白名单拒");
        helper.assertTrue(black.testBlock(level.getBlockState(waterPos), level, waterPos), "没有物品形态：黑名单收");
        helper.assertFalse(white.testBlock(level.getBlockState(airPos), level, airPos), "空气：白名单拒");
        helper.assertTrue(black.testBlock(level.getBlockState(airPos), level, airPos), "空气：黑名单收");
        helper.assertTrue(ItemFilters.acceptsBlock(ItemStack.EMPTY, level.getBlockState(waterPos), level, waterPos), "没装过滤器全收");
        helper.succeed();
    }

    /** 编译缓存：同一个对象、同一个 NBT 对象返回同一个判定器；NBT 换了重编 */
    @GameTest(template = EMPTY)
    public static void filterCache(GameTestHelper helper) {
        ItemFilters.Cache cache = new ItemFilters.Cache();
        ItemStack notFilter = new ItemStack(Items.STONE);
        ItemFilters.Compiled first = cache.get(notFilter);
        helper.assertTrue(first == ItemFilters.ACCEPT_ALL && cache.get(notFilter) == first, "不是过滤器应返回全收并缓存");
        ItemStack other = new ItemStack(Items.STONE);
        helper.assertTrue(cache.get(other) == ItemFilters.ACCEPT_ALL, "换了对象应重算（结果仍是全收）");
        helper.assertTrue(ItemFilters.samples(notFilter).size() == ItemFilters.SLOTS && ItemFilters.samples(notFilter).stream().allMatch(ItemStack::isEmpty),
                "不是过滤器的样本应是 9 个空");
        helper.assertFalse(ItemFilters.setSample(notFilter, 0, new ItemStack(Items.DIRT)), "不是过滤器不应写样本");
        helper.assertTrue(ItemFilters.canBeSample(new ItemStack(Items.DIRT)) && ItemFilters.depth(new ItemStack(Items.DIRT)) == 0,
                "普通物品层数 0、能当样本");
        helper.succeed();
    }

    // ==================================================================
    // F4-12 生物与掉落物规则
    // ==================================================================

    /**
     * isSpared 照旧（原太阳棱镜名单）；belongsToOther：自己驯服的不算别人的（D1 选 B），别人的、没主人的机器、起了名字的仍算。
     * 骆驼（4a 审查 FW-7）：没装鞍的照野生动物算（机器能处理、光束照烧），装了鞍的谁都不碰
     */
    @GameTest(template = EMPTY)
    public static void entityOwnershipRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        Cow wild = EntityType.COW.create(level);
        Cow named = EntityType.COW.create(level);
        Cow leashed = EntityType.COW.create(level);
        Wolf mine = EntityType.WOLF.create(level);
        Wolf theirs = EntityType.WOLF.create(level);
        Wolf namedMine = EntityType.WOLF.create(level);
        Horse myHorse = EntityType.HORSE.create(level);
        Horse ownerless = EntityType.HORSE.create(level);
        Villager villager = EntityType.VILLAGER.create(level);
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
        IronGolem built = EntityType.IRON_GOLEM.create(level);
        IronGolem village = EntityType.IRON_GOLEM.create(level);
        SnowGolem snow = EntityType.SNOW_GOLEM.create(level);
        Allay allay = EntityType.ALLAY.create(level);
        Camel wildCamel = EntityType.CAMEL.create(level);
        Camel saddledCamel = EntityType.CAMEL.create(level);
        helper.assertTrue(wild != null && named != null && leashed != null && mine != null && theirs != null && namedMine != null
                && myHorse != null && ownerless != null && villager != null && trader != null && built != null && village != null
                && snow != null && allay != null && wildCamel != null && saddledCamel != null, "造不出测试生物");

        named.setCustomName(Component.literal("Daisy"));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.OAK_FENCE);
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, helper.absolutePos(new BlockPos(1, 1, 1)));
        leashed.setLeashedTo(knot, false);
        mine.setTame(true);
        mine.setOwnerUUID(owner);
        theirs.setTame(true);
        theirs.setOwnerUUID(stranger);
        namedMine.setTame(true);
        namedMine.setOwnerUUID(owner);
        namedMine.setCustomName(Component.literal("Rex"));
        myHorse.setTamed(true);
        myHorse.setOwnerUUID(owner);
        ownerless.setTamed(true);
        built.setPlayerCreated(true);
        allay.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, owner);
        saddledCamel.equipSaddle(null);
        helper.assertTrue(saddledCamel.isSaddled() && !wildCamel.isSaddled(), "骆驼的鞍没装上");

        // isSpared：和原太阳棱镜 sparedFromBeam 一样
        helper.assertFalse(ECEntityRules.isSpared(wild) || ECEntityRules.isSpared(village), "野牛、村庄铁傀儡不受保护");
        helper.assertTrue(ECEntityRules.isSpared(named) && ECEntityRules.isSpared(leashed) && ECEntityRules.isSpared(mine)
                && ECEntityRules.isSpared(theirs) && ECEntityRules.isSpared(myHorse) && ECEntityRules.isSpared(ownerless)
                && ECEntityRules.isSpared(villager) && ECEntityRules.isSpared(trader) && ECEntityRules.isSpared(built)
                && ECEntityRules.isSpared(snow) && ECEntityRules.isSpared(allay), "有名字、拴绳、驯服、村民、商人、玩家造的傀儡、认主悦灵都受保护");
        // 骆驼的 isTamed() 恒为真却从不记主人：没装鞍的不算驯服，装了鞍的算「有人在用」
        helper.assertFalse(ECEntityRules.isTamed(wildCamel) || ECEntityRules.isSpared(wildCamel), "没装鞍的骆驼不受保护");
        helper.assertTrue(ECEntityRules.isSpared(saddledCamel), "装了鞍的骆驼受保护");

        // belongsToOther
        helper.assertTrue(ECEntityRules.TAMED_BY_MACHINE_OWNER_IS_OWN, "D1 已定 B");
        helper.assertFalse(ECEntityRules.belongsToOther(mine, owner), "自己驯服的狼不算别人的");
        helper.assertFalse(ECEntityRules.belongsToOther(myHorse, owner), "自己驯服的马不算别人的");
        helper.assertTrue(ECEntityRules.belongsToOther(theirs, owner), "别人驯服的狼算别人的");
        helper.assertTrue(ECEntityRules.belongsToOther(mine, null), "没有主人的机器：驯服的都算别人的");
        helper.assertTrue(ECEntityRules.belongsToOther(ownerless, owner), "驯服了却没记主人的算别人的");
        helper.assertTrue(ECEntityRules.belongsToOther(namedMine, owner), "起了名字的即使是自己的也不动");
        helper.assertTrue(ECEntityRules.belongsToOther(allay, owner), "认主悦灵一律不动");
        helper.assertTrue(ECEntityRules.belongsToOther(leashed, owner) && ECEntityRules.belongsToOther(villager, owner)
                && ECEntityRules.belongsToOther(built, owner) && ECEntityRules.belongsToOther(snow, owner), "拴绳、村民、傀儡一律不动");
        helper.assertFalse(ECEntityRules.belongsToOther(wild, owner) || ECEntityRules.belongsToOther(wild, null), "野牛谁的机器都能处理");
        helper.assertFalse(ECEntityRules.belongsToOther(wildCamel, owner) || ECEntityRules.belongsToOther(wildCamel, null), "没装鞍的骆驼谁的机器都能处理");
        helper.assertTrue(ECEntityRules.belongsToOther(saddledCamel, owner) && ECEntityRules.belongsToOther(saddledCamel, null), "装了鞍的骆驼谁的机器都不碰");
        helper.assertTrue(ECEntityRules.belongsToOther(ProtectionFrameworkTests.realPlayer(helper), owner), "玩家一律不当牲口处理");
        helper.assertTrue(ECEntityRules.tamedOwner(mine).equals(owner) && ECEntityRules.tamedOwner(wild) == null, "tamedOwner 应读出主人");
        helper.succeed();
    }

    /** 掉落物归属：/give 溢出（指定拾取人）读得出、丢出者、永不可拾取；磁化法杖的判定不变 */
    @GameTest(template = EMPTY)
    public static void itemEntityOwnership(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        // /give 背包放不下时：drop → setNoPickUpDelay → setTarget（原版 GiveCommand）
        ItemEntity overflow = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.DIAMOND, 64));
        overflow.setNoPickUpDelay();
        overflow.setTarget(owner);
        level.addFreshEntity(overflow);
        helper.assertTrue(owner.equals(ECItemEntities.target(overflow)), "应读出 /give 溢出物的拾取人");
        helper.assertTrue(ECItemEntities.pickableBy(overflow, owner), "拾取人自己能拿");
        helper.assertFalse(ECItemEntities.pickableBy(overflow, other) || ECItemEntities.pickableBy(overflow, null), "别人、没主人的机器不能拿");
        helper.assertFalse(ECItemEntities.neverPickUp(overflow), "/give 溢出物不是永不可拾取");

        ItemEntity thrown = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STONE));
        thrown.setThrower(other);
        thrown.setDefaultPickUpDelay();
        helper.assertTrue(other.equals(ECItemEntities.thrower(thrown)) && ECItemEntities.target(thrown) == null, "应读出丢出者、没有拾取人");
        helper.assertTrue(ECItemEntities.pickableBy(thrown, owner) && ECItemEntities.pickableBy(thrown, null), "没指定拾取人：谁都能拿（不看丢出者）");
        helper.assertTrue(ECItemEntities.pickupDelay(thrown) == 10, "默认拾取延迟 10");

        ItemEntity display = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.EMERALD));
        display.setNeverPickUp();
        helper.assertTrue(ECItemEntities.neverPickUp(display), "setNeverPickUp 的应认出来");

        // 磁化法杖：丢出者或拾取人是别人时不拉（转调后行为不变）
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemEntity mineThrown = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STONE));
        mineThrown.setThrower(player.getUUID());
        ItemEntity forOther = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STONE));
        forOther.setTarget(other);
        ItemEntity mobDrop = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.BONE));
        helper.assertTrue(MagnetizingStaffItem.mayPull(mineThrown, player) && MagnetizingStaffItem.mayPull(mobDrop, player), "自己丢的、怪物掉的照拉");
        helper.assertFalse(MagnetizingStaffItem.mayPull(thrown, player) || MagnetizingStaffItem.mayPull(forOther, player), "别人丢的、指定给别人的不拉");
        overflow.discard();
        helper.succeed();
    }
}
