package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.network.S2CBoardFlyingPacket;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.util.ECFlight;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 宅男反重力板（原作 ItemComputerBoard + PlayerTickHandler L452~515）：戴在 Curios 腰带槽（原作写死读 Baubles 第 4 格）就能飞，
 * 不要套装、不要电（原作如此）。飞行由 {@link ECFlight} 授权（来源 {@link #FLIGHT_SOURCE}），速度 {@code general.computer_board.fly_speed}（默认 0.1）。
 * <ul>
 *   <li>戴上（{@link #onEquip}）给飞行；摘下（{@link #onUnequip}）重新看身上还有没有能用的板子，没有就撤——只撤本模组给的，别的来源给的飞行、
 *       创造 / 旁观的飞行不碰。戴着时每 {@value ComputerArmorEvents#CHECK_INTERVAL} 刻（{@link #curioTick}）再确认一次：
 *       跟上配置热改的速度，也让 ECFlight 在重登后的宽限期里认出这个来源还在（没人续的来源 60 刻后撤掉，见 ECFlight 类注释）。</li>
 *   <li>同种只戴一件（{@link CuriosCompat#onlyOneEquipped}）；被 {@code general.baubles.disabled} 列进去时不给飞行（戴着时列进去的，下一次确认时撤掉）。</li>
 *   <li>全息投影的参战者不能飞：Boss 那边给 ECFlight 登记压制器，这里不用管。</li>
 *   <li>飞着时脚下画 board.obj（client/ComputerClient）。别人脚下画不画看服务端发的 {@link S2CBoardFlyingPacket}（{@link #syncFlying}，状态变了才发）。</li>
 * </ul>
 * 原作：客户端单方面把飞行速度改成 0.2（两端不一致）、摘下时不管别的飞行来源一律关飞行。速度 0.1：原作 0.2 按原版创造飞行估约 44 米 / 秒、
 * 疾飞约 87，比鞘翅加烟花还快，多人服生成新区块的压力太大（P2）。
 */
public class ComputerBoardItem extends Item implements ICurioItem {

    /** ECFlight 的来源 */
    public static final ResourceLocation FLIGHT_SOURCE = EssentialCraft5.id("computer_board");

    /** 上次发给旁人的状态是「在用板子飞」的玩家（按玩家对象弱引用，重生、下线就没了；只在服务端线程读写） */
    private static final Set<ServerPlayer> FLYING_SENT = Collections.newSetFromMap(new WeakHashMap<>());

    public ComputerBoardItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------
    // Curios
    // ------------------------------------------------------------------

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CuriosCompat.onlyOneEquipped(slotContext, stack);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (slotContext.entity() instanceof ServerPlayer player && !slotContext.cosmetic()) {
            CuriosCompat.invalidate(player);
            refresh(player);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof ServerPlayer player) {
            // 这时槽里已经是新的东西了：让缓存作废再看身上还有没有别的板子
            CuriosCompat.invalidate(player);
            refresh(player);
        }
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof ServerPlayer player && player.tickCount % ComputerArmorEvents.CHECK_INTERVAL == 0) {
            refresh(player);
        }
    }

    // ------------------------------------------------------------------
    // 飞行
    // ------------------------------------------------------------------

    /** 戴着能用的板子（不是装饰槽里的、没被禁用）就给飞行（重复调只更新速度），否则撤掉本模组为它给的飞行 */
    public static void refresh(ServerPlayer player) {
        if (CuriosCompat.has(player, ECComputer.COMPUTER_BOARD.get())) {
            ECFlight.grant(player, FLIGHT_SOURCE, flySpeed());
        } else {
            ECFlight.revoke(player, FLIGHT_SOURCE);
        }
    }

    /** 每 20 刻的兜底：给着飞行时才查一次（别的模组绕过 Curios 拿走了板子、禁用配置热改） */
    public static void refreshIfGranted(ServerPlayer player) {
        if (ECFlight.isGranted(player, FLIGHT_SOURCE)) {
            refresh(player);
        }
    }

    // ------------------------------------------------------------------
    // 飞行状态同步给旁人
    // ------------------------------------------------------------------

    /**
     * 正踩着反重力板在飞：在飞、反重力板给着飞行（含重登宽限期里还没确认的）、不是旁观者。创造模式戴着板子飞也算，和客户端看本地玩家的判断一致
     */
    public static boolean isFlyingOnBoard(ServerPlayer player) {
        return player.getAbilities().flying && !player.isSpectator() && ECFlight.isGranted(player, FLIGHT_SOURCE);
    }

    /**
     * 每刻调（ComputerArmorEvents）：和上次发出去的状态不一样才发给看得到这名玩家的客户端。
     *
     * @return 这一次是不是发了（状态变了）
     */
    public static boolean syncFlying(ServerPlayer player) {
        boolean flying = isFlyingOnBoard(player);
        if (!flying && FLYING_SENT.isEmpty()) {
            return false;
        }
        if (flying == FLYING_SENT.contains(player)) {
            return false;
        }
        if (flying) {
            FLYING_SENT.add(player);
        } else {
            FLYING_SENT.remove(player);
        }
        S2CBoardFlyingPacket.sendToTrackers(player, flying);
        return true;
    }

    /** tracker 开始看到 target 时补发 target 现在的状态（不管真假都发：tracker 的客户端可能还记着上次离开视野时的「在飞」） */
    public static void sendFlyingState(ServerPlayer tracker, ServerPlayer target) {
        S2CBoardFlyingPacket.sendTo(tracker, target, isFlyingOnBoard(target));
    }

    /** 服务器停下时清掉记录 */
    public static void clearFlyingState() {
        FLYING_SENT.clear();
    }

    /** 现在的飞行速度（配置） */
    public static float flySpeed() {
        return (float) ECComputer.Config.values().boardFlySpeed();
    }

    // ------------------------------------------------------------------
    // 提示
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.computer_board").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(CuriosCompat.onlyOneTooltip());
        if (CuriosCompat.isDisabled(stack)) {
            tooltip.add(CuriosCompat.disabledTooltip());
        }
    }
}
