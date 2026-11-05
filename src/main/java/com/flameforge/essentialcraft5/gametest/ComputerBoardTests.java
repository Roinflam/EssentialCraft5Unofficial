package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.item.ComputerBoardItem;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.util.ECFlight;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.List;
import java.util.function.Predicate;

/**
 * 宅男反重力板（第八批 M2，batch8.md §4.2）的 GameTest：戴在腰带槽就能飞、速度按配置；摘下只撤自己给的；创造模式不碰；
 * 被 {@code general.baubles.disabled} 列进去时不给；ECFlight 的压制器生效时暂停；同种只戴一件、只进腰带槽；
 * 「正踩着板子在飞」的状态只在变化时才发给旁人（{@link ComputerBoardItem#syncFlying}）。
 * 真玩家用 {@link ProtectionFrameworkTests#realPlayer}（不在世界里，Curios 不 tick，戴上 / 摘下后直接调 {@link ComputerBoardItem#refresh}，
 * 就是 onEquip / onUnequip / curioTick 里做的事）。禁用配置用 {@code CuriosCompat.Config.setDisabledForTest}，不改真配置。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ComputerBoardTests {

    private static final String EMPTY = "empty";
    private static final float EPS = 1.0E-6F;
    private static final ResourceLocation OTHER_SOURCE = EssentialCraft5.id("test/other_flight");

    private ComputerBoardTests() {
    }

    private static ICuriosItemHandler curios(GameTestHelper helper, ServerPlayer player) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (inventory == null) {
            helper.fail("玩家没有 Curios 饰品栏");
        }
        return inventory;
    }

    /** 直接放进饰品槽（不走 canEquip），让缓存作废；空栈 = 摘下 */
    private static void put(GameTestHelper helper, ServerPlayer player, String slot, int index, ItemStack stack) {
        ICurioStacksHandler handler = curios(helper, player).getCurios().get(slot);
        if (handler == null || handler.getSlots() <= index) {
            helper.fail("玩家没有 Curios 槽位 " + slot + "#" + index);
            return;
        }
        handler.getStacks().setStackInSlot(index, stack);
        CuriosCompat.invalidate(player);
    }

    private static ItemStack board() {
        return new ItemStack(ECComputer.COMPUTER_BOARD.get());
    }

    /** 戴上能飞、速度是配置的 0.1；配置改了下一次确认跟着改；摘下撤掉、能力还原 */
    @GameTest(template = EMPTY)
    public static void computerBoardGrantsFlight(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        put(helper, player, CuriosCompat.BELT, 0, board());
        ComputerBoardItem.refresh(player);
        helper.assertTrue(ECFlight.isGranted(player, ComputerBoardItem.FLIGHT_SOURCE) && player.getAbilities().mayfly, "戴上应能飞");
        helper.assertTrue(Math.abs(player.getAbilities().getFlyingSpeed() - 0.1F) < EPS, "飞行速度应是 0.1，实际 " + player.getAbilities().getFlyingSpeed());
        ECComputer.Config.setTestOverrides(ECComputer.Config.DEFAULTS.toBuilder().boardFlySpeed(0.2D).build());
        try {
            ComputerBoardItem.refresh(player);
            helper.assertTrue(Math.abs(player.getAbilities().getFlyingSpeed() - 0.2F) < EPS, "配置改成 0.2 后确认一次跟着改");
        } finally {
            ECComputer.Config.setTestOverrides(null);
        }
        put(helper, player, CuriosCompat.BELT, 0, ItemStack.EMPTY);
        ComputerBoardItem.refresh(player);
        helper.assertFalse(ECFlight.isGranted(player, ComputerBoardItem.FLIGHT_SOURCE) || player.getAbilities().mayfly, "摘下撤掉飞行");
        helper.assertTrue(Math.abs(player.getAbilities().getFlyingSpeed() - ECFlight.VANILLA_FLY_SPEED) < EPS, "飞行速度还原");
        helper.succeed();
    }

    /** 别的来源也给着飞行时摘下板子：只撤自己的，照样能飞；每 20 刻的兜底（refreshIfGranted）也只在给着时才查 */
    @GameTest(template = EMPTY)
    public static void computerBoardRevokesOnlyOwn(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ECFlight.grant(player, OTHER_SOURCE, 0.05F);
        put(helper, player, CuriosCompat.BELT, 0, board());
        ComputerBoardItem.refresh(player);
        helper.assertTrue(ECFlight.isGranted(player, ComputerBoardItem.FLIGHT_SOURCE), "戴上");
        put(helper, player, CuriosCompat.BELT, 0, ItemStack.EMPTY);
        ComputerBoardItem.refreshIfGranted(player);
        helper.assertFalse(ECFlight.isGranted(player, ComputerBoardItem.FLIGHT_SOURCE), "兜底检查发现板子没了，撤掉自己的");
        helper.assertTrue(ECFlight.isGranted(player, OTHER_SOURCE) && player.getAbilities().mayfly, "别的来源给的照样能飞");
        ECFlight.revoke(player, OTHER_SOURCE);
        helper.succeed();
    }

    /** 创造模式：照记来源、不碰能力（能飞是原版给的，速度不改） */
    @GameTest(template = EMPTY)
    public static void computerBoardCreativeUntouched(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        put(helper, player, CuriosCompat.BELT, 0, board());
        ComputerBoardItem.refresh(player);
        helper.assertTrue(ECFlight.isGranted(player, ComputerBoardItem.FLIGHT_SOURCE) && !ECFlight.isApplied(player), "创造模式照记、不接管");
        helper.assertTrue(Math.abs(player.getAbilities().getFlyingSpeed() - ECFlight.VANILLA_FLY_SPEED) < EPS, "创造模式的飞行速度不改");
        put(helper, player, CuriosCompat.BELT, 0, ItemStack.EMPTY);
        ComputerBoardItem.refresh(player);
        helper.assertTrue(player.getAbilities().mayfly, "创造模式摘下也照样能飞");
        player.setGameMode(GameType.SURVIVAL);
        helper.succeed();
    }

    /** general.baubles.disabled 列进去时不给飞行；戴着时列进去的，下一次确认撤掉 */
    @GameTest(template = EMPTY)
    public static void computerBoardDisabledByConfig(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        put(helper, player, CuriosCompat.BELT, 0, board());
        ComputerBoardItem.refresh(player);
        helper.assertTrue(player.getAbilities().mayfly, "测试前提：没禁用时能飞");
        CuriosCompat.Config.setDisabledForTest(List.of("essentialcraft5:computer_board"));
        try {
            CuriosCompat.invalidate(player);
            ComputerBoardItem.refresh(player);
            helper.assertFalse(ECFlight.isGranted(player, ComputerBoardItem.FLIGHT_SOURCE) || player.getAbilities().mayfly, "被禁用后撤掉");
        } finally {
            CuriosCompat.Config.setDisabledForTest(null);
        }
        helper.succeed();
    }

    /** Boss 战之类的压制器生效时暂停（来源照记），解除后恢复 */
    @GameTest(template = EMPTY)
    public static void computerBoardSuppressed(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Predicate<ServerPlayer> suppressor = p -> p == player;
        ECFlight.registerSuppressor(suppressor);
        try {
            put(helper, player, CuriosCompat.BELT, 0, board());
            ComputerBoardItem.refresh(player);
            helper.assertTrue(ECFlight.isGranted(player, ComputerBoardItem.FLIGHT_SOURCE) && !player.getAbilities().mayfly, "压制中不能飞");
        } finally {
            ECFlight.unregisterSuppressor(suppressor);
        }
        ComputerBoardItem.refresh(player);
        helper.assertTrue(player.getAbilities().mayfly, "压制解除后恢复");
        ECFlight.revoke(player, ComputerBoardItem.FLIGHT_SOURCE);
        helper.succeed();
    }

    /**
     * 飞行状态同步：在飞、并且板子给着飞行才算「踩着板子飞」；和上次发出去的不一样才发（第一次起飞发、再查一次不发、落地发、再查不发）；
     * 别的来源给的飞行不算；旁观者不算。测试玩家不在世界里、没人追踪，发包是空操作，这里只看「发没发」的判断
     */
    @GameTest(template = EMPTY)
    public static void computerBoardFlyingSync(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        helper.assertFalse(ComputerBoardItem.syncFlying(player), "没戴板子、没在飞：不发");
        ECFlight.grant(player, ComputerBoardItem.FLIGHT_SOURCE, 0.1F);
        try {
            helper.assertFalse(ComputerBoardItem.isFlyingOnBoard(player) || ComputerBoardItem.syncFlying(player), "给着飞行但站在地上：不算、不发");
            player.getAbilities().flying = true;
            helper.assertTrue(ComputerBoardItem.isFlyingOnBoard(player), "给着飞行、在飞：算");
            helper.assertTrue(ComputerBoardItem.syncFlying(player), "起飞：发");
            helper.assertFalse(ComputerBoardItem.syncFlying(player), "状态没变：不再发");
            player.getAbilities().flying = false;
            helper.assertTrue(ComputerBoardItem.syncFlying(player), "落地：发");
            helper.assertFalse(ComputerBoardItem.syncFlying(player), "状态没变：不再发");
            player.getAbilities().flying = true;
            helper.assertTrue(ComputerBoardItem.syncFlying(player), "再起飞：发");
        } finally {
            ECFlight.revoke(player, ComputerBoardItem.FLIGHT_SOURCE);
        }
        player.getAbilities().flying = true;
        helper.assertFalse(ComputerBoardItem.isFlyingOnBoard(player), "摘下板子（来源撤掉）：在飞也不算");
        helper.assertTrue(ComputerBoardItem.syncFlying(player), "摘下板子：发一次「不在飞」");
        helper.assertFalse(ComputerBoardItem.syncFlying(player), "之后不再发");

        ServerPlayer other = ProtectionFrameworkTests.realPlayer(helper);
        ECFlight.grant(other, OTHER_SOURCE, 0.05F);
        try {
            other.getAbilities().flying = true;
            helper.assertFalse(ComputerBoardItem.isFlyingOnBoard(other) || ComputerBoardItem.syncFlying(other), "别的来源给的飞行不算");
        } finally {
            ECFlight.revoke(other, OTHER_SOURCE);
        }

        ServerPlayer spectator = ProtectionFrameworkTests.realPlayer(helper);
        ECFlight.grant(spectator, ComputerBoardItem.FLIGHT_SOURCE, 0.1F);
        try {
            spectator.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(spectator.getAbilities().flying, "测试前提：旁观者一直在飞");
            helper.assertFalse(ComputerBoardItem.isFlyingOnBoard(spectator), "旁观者不算");
        } finally {
            spectator.setGameMode(GameType.SURVIVAL);
            ECFlight.revoke(spectator, ComputerBoardItem.FLIGHT_SOURCE);
        }
        helper.succeed();
    }

    /** 只进腰带槽（不进指环槽）；身上已有一块时第二块戴不上；装饰槽不算 */
    @GameTest(template = EMPTY)
    public static void computerBoardSlotRules(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ICuriosItemHandler inventory = curios(helper, player);
        ICurioStacksHandler belt = inventory.getCurios().get(CuriosCompat.BELT);
        ICurioStacksHandler ring = inventory.getCurios().get(CuriosCompat.RING);
        helper.assertTrue(belt != null && ring != null, "测试前提：有腰带槽和指环槽");
        helper.assertTrue(belt.getStacks().isItemValid(0, board()), "腰带槽放得进");
        helper.assertFalse(ring.getStacks().isItemValid(0, board()), "指环槽放不进");
        ComputerBoardItem item = ECComputer.COMPUTER_BOARD.get();
        SlotContext beltSlot = new SlotContext(CuriosCompat.BELT, player, 0, false, true);
        helper.assertTrue(item.canEquip(beltSlot, board()), "身上没有时能戴");
        put(helper, player, CuriosCompat.RING, 0, board());
        helper.assertFalse(item.canEquip(beltSlot, board()), "别的槽里已经有一块（指令塞进去的）时第二块戴不上");
        helper.assertTrue(item.canEquip(new SlotContext(CuriosCompat.BELT, player, 0, true, true), board()), "装饰槽不算");
        put(helper, player, CuriosCompat.RING, 0, ItemStack.EMPTY);
        helper.succeed();
    }
}
