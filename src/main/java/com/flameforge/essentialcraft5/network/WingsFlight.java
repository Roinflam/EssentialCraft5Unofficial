package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 魔力之翼（戴在腰带槽时）按住跳跃往上飞的开关状态（第三批框架 G5，ECCharms 用）。
 * <p>
 * 流程（batch3.md §3.1、policy §4.2）：
 * <ol>
 *   <li>客户端（ECCharms 的客户端类，每个客户端 tick）：{@code boolean fly = WingsFlight.clientTick(戴着翅膀 && 按住跳跃);}
 *       返回 true 的 tick 在本地给玩家加竖直速度、生成粒子。状态变了才发 {@link C2SWingsPacket}（只带布尔）。</li>
 *   <li>服务端收到「开」：玩家不是旁观者、Curios 槽里确实戴着 {@value #WINGS_PATH}（按注册名查，走 {@link CuriosCompat#has} 的缓存）
 *       才记下，否则回 {@link S2CWingsStopPacket} 让客户端停。收到「关」直接清掉。</li>
 *   <li>服务端（ECCharms 翅膀的 curioTick）：{@code if (WingsFlight.isActive(player)) { 扣 1 MRU，扣不动就 WingsFlight.stop(player); }}；
 *       摘下翅膀时（onUnequip）也调 {@link #stop}。原作戴着往上飞不扣 MRU（扣费写在永远匹配不上的收包分支里），这里每 tick 1 MRU，和手持一致。</li>
 *   <li>客户端收到叫停：本次按住期间不再飞，松开跳跃后才能重新开始（不会每 tick 发一次「开」包）。</li>
 * </ol>
 * 登录、重生、换维度时服务端清掉状态并发一次叫停，把客户端也复位（断线重连、按着跳跃死掉之类的情况不会留下「客户端以为开着、服务端不扣费」）。
 * 手持翅膀的飞行（原作 onUpdate：拿在手上就自动往上飞）不经过这个开关，由 ECCharms 自己在物品 tick 里做。
 * <p>
 * 状态按玩家对象存在服务端内存里（弱引用），不存盘：重进服务器默认是关的。
 */
public final class WingsFlight {

    public static final String WINGS_PATH = "magical_wings";
    /** 魔力之翼的注册名（ECCharms 注册；框架按名字查，不 import 模块的类） */
    public static final ResourceLocation WINGS_ID = EssentialCraft5.id(WINGS_PATH);

    /** 服务端：开着的玩家（只在服务端主线程读写） */
    private static final Set<Player> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

    /** 客户端：上次发给服务端的状态 */
    private static boolean clientSent;
    /** 客户端：服务端叫停了，等松开跳跃 */
    private static boolean clientDenied;

    private WingsFlight() {
    }

    // ---------------------------------------------------------------- 服务端

    /** 服务端：这个玩家的翅膀开着（收到过「开」、校验通过、之后没被叫停） */
    public static boolean isActive(Player player) {
        return ACTIVE.contains(player);
    }

    /** 服务端：叫停（MRU 用完、摘下翅膀），并通知客户端 */
    public static void stop(Player player) {
        ACTIVE.remove(player);
        notifyStop(player);
    }

    /** 服务端：Curios 槽里戴着魔力之翼（ECCharms 没接线时总是 false） */
    public static boolean isWearingWings(Player player) {
        Item wings = wingsItem();
        return wings != null && CuriosCompat.has(player, wings);
    }

    /** 处理 {@link C2SWingsPacket}（参数是 Player 而不是 ServerPlayer，GameTest 的模拟玩家也能测） */
    public static void handleToggle(Player player, boolean active) {
        if (!active) {
            ACTIVE.remove(player);
            return;
        }
        if (player.isSpectator() || !player.isAlive() || !isWearingWings(player)) {
            // 没戴翅膀却说要飞：不记，回叫停让客户端别在本地飞
            stop(player);
            return;
        }
        ACTIVE.add(player);
    }

    @Nullable
    private static Item wingsItem() {
        return ForgeRegistries.ITEMS.containsKey(WINGS_ID) ? ForgeRegistries.ITEMS.getValue(WINGS_ID) : null;
    }

    private static void notifyStop(Player player) {
        // 模拟玩家（GameTest）和还没连上的玩家没有连接，不发
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            ECNetwork.sendTo(serverPlayer, new S2CWingsStopPacket());
        }
    }

    // ---------------------------------------------------------------- 客户端

    /**
     * 客户端每 tick 调一次（只能在客户端调）。holding = 戴着翅膀而且按住跳跃（由 ECCharms 判断）。
     * 返回这一 tick 是否应该在本地往上飞。状态变化时才发包；服务端叫停后直到 holding 变成 false 之前都返回 false。
     */
    public static boolean clientTick(boolean holding) {
        if (!holding) {
            clientDenied = false;
        }
        boolean want = holding && !clientDenied;
        if (want != clientSent) {
            clientSent = want;
            ECNetwork.sendToServer(new C2SWingsPacket(want));
        }
        return want;
    }

    /** 客户端：当前是不是在飞（给粒子、动画用；和上一 tick clientTick 的返回值一致） */
    public static boolean clientActive() {
        return clientSent;
    }

    /** 客户端收到 {@link S2CWingsStopPacket} */
    static void onStopReceived() {
        clientDenied = true;
        clientSent = false;
    }

    // ---------------------------------------------------------------- 复位

    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class FlightEvents {

        private FlightEvents() {
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            stop(event.getEntity());
        }

        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
            stop(event.getEntity());
        }

        @SubscribeEvent
        public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            stop(event.getEntity());
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            ACTIVE.remove(event.getEntity());
        }
    }
}
