package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.HoannaEventState;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.network.S2CHoannaEventPacket;
import com.flameforge.essentialcraft5.network.S2CHoannaJoltPacket;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端的海纳尔天灾状态（第六批 6a 框架 F6a，batch6a.md §3.4、§5.8 裁定 1；游戏总线、只在客户端）。
 * 事件状态由 {@code S2CHoannaEventPacket} 喂进来，强震由 {@code S2CHoannaJoltPacket} 排进队列；M3 的画面（天空、雾色、光照图、晃动、提示条）只读这里：
 * <pre>
 * float dark = HoannaClientEvents.fade(HoannaEvent.DARKNESS, partialTick);   // 0~1，已含 10 秒淡入淡出
 * for (HoannaClientEvents.Jolt jolt : HoannaClientEvents.consumeJolts()) { … }  // 每帧 / 每刻取一次，取完就清
 * </pre>
 * <ul>
 *   <li>不在海纳尔（{@code ECHoanna.isHoanna(mc.level)} 为 false）时一切为 0：每个客户端 tick 查一次，离开海纳尔立刻清零（不淡出，换世界时画面本来就整个换了）；断线清零。</li>
 *   <li>展示厅：{@link #setShowroomOverride} 强制某个事件、某个强度（其余事件为 0），{@link #clearShowroomOverride} 撤掉。</li>
 *   <li>强震队列最多攒 {@link #MAX_JOLTS} 个（M3 每刻都取，正常只会有 0~1 个）。</li>
 * </ul>
 * 状态机本身（淡入淡出、重发不重新淡入、停止后线性淡出）在 common 的 {@link HoannaEventState}，GameTest 测它。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class HoannaClientEvents {

    /** 强震队列上限 */
    public static final int MAX_JOLTS = 8;

    /** 一次强震：强度 0~1（{@link S2CHoannaJoltPacket#scale()}）、收到时的 gameTime */
    public record Jolt(float strength, long gameTime) {
    }

    private static final HoannaEventState STATE = new HoannaEventState();
    private static final List<Jolt> JOLTS = new ArrayList<>(MAX_JOLTS);
    @Nullable
    private static HoannaEvent showroomEvent;
    private static float showroomFade;
    @Nullable
    private static volatile Float showroomTide;

    private HoannaClientEvents() {
    }

    // ------------------------------------------------------------------
    // 包（ECHoanna.init 登记，已在客户端主线程）
    // ------------------------------------------------------------------

    public static void onEventPacket(S2CHoannaEventPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            STATE.clear();
            return;
        }
        STATE.accept(packet.event(), packet.remaining(), packet.total(), level.getGameTime());
    }

    public static void onJoltPacket(S2CHoannaJoltPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !ECHoanna.isHoanna(level) || packet.strength() <= 0) {
            return;
        }
        if (JOLTS.size() >= MAX_JOLTS) {
            JOLTS.remove(0);
        }
        JOLTS.add(new Jolt(packet.scale(), level.getGameTime()));
    }

    // ------------------------------------------------------------------
    // 给画面读
    // ------------------------------------------------------------------

    /** 本地玩家所在的世界是不是海纳尔（没有世界时 false） */
    public static boolean inHoanna() {
        return ECHoanna.isHoanna(Minecraft.getInstance().level);
    }

    /** 这种事件现在的画面强度 0~1（含淡入淡出；不在海纳尔为 0；展示厅覆盖优先） */
    public static float fade(HoannaEvent event, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.isNone() || !ECHoanna.isHoanna(level)) {
            return 0F;
        }
        HoannaEvent forced = showroomEvent;
        if (forced != null) {
            return forced == event ? showroomFade : 0F;
        }
        return STATE.fade(event, level.getGameTime() + (double) partialTick);
    }

    /** 正在进行的事件（停止后淡出期间已是 NONE；展示厅覆盖优先；不在海纳尔为 NONE） */
    public static HoannaEvent current() {
        if (!inHoanna()) {
            return HoannaEvent.NONE;
        }
        HoannaEvent forced = showroomEvent;
        return forced != null ? forced : STATE.current();
    }

    /**
     * 画面用的潮位（第十三批 13d；光幕不透明度 × (0.6 + 0.8T)）：和服务端同一个 {@link HoannaTide#value}（按月相，开关是同步来的服务端配置）；
     * 展示厅覆盖优先（{@link #setShowroomTide}）。
     */
    public static float tide(@Nullable ClientLevel level) {
        Float forced = showroomTide;
        return forced != null ? forced : HoannaTide.value(level);
    }

    /** 当前事件还剩多少 tick（提示条的 mm:ss；没有事件 0） */
    public static int remainingTicks() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null || showroomEvent != null ? 0 : STATE.remaining(level.getGameTime());
    }

    /** 当前事件的总时长（提示条的进度条；没有事件 0） */
    public static int totalTicks() {
        return showroomEvent != null ? 0 : STATE.total();
    }

    /**
     * 取走排队的强震（每帧或每刻调一次；没有时返回同一个空表，不分配）。
     * 不在海纳尔时队列会在客户端 tick 里清掉。
     */
    public static List<Jolt> consumeJolts() {
        if (JOLTS.isEmpty()) {
            return List.of();
        }
        List<Jolt> out = List.copyOf(JOLTS);
        JOLTS.clear();
        return out;
    }

    /** 状态机本体（只读用；别从画面代码里喂包） */
    public static HoannaEventState state() {
        return STATE;
    }

    // ------------------------------------------------------------------
    // 展示厅
    // ------------------------------------------------------------------

    /** 展示厅拍截图：强制这个事件、这个强度（0~1），其余事件都是 0；传 NONE 等于全关 */
    public static void setShowroomOverride(HoannaEvent event, float fade) {
        showroomEvent = event;
        showroomFade = Math.max(0F, Math.min(1F, fade));
    }

    public static void clearShowroomOverride() {
        showroomEvent = null;
        showroomFade = 0F;
        showroomTide = null;
    }

    /** 展示厅拍「大潮夜 / 新月」对比：画面按这个潮位画（0~1；null 撤掉，按月相）。只动画面，不动服务端 */
    public static void setShowroomTide(@Nullable Float tide) {
        showroomTide = tide == null ? null : Math.max(0F, Math.min(1F, tide));
    }

    // ------------------------------------------------------------------
    // 清零
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onHoannaClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // 服务端在换维度 / 登录之后才发状态包（重生包先到、客户端世界先换成海纳尔），所以这里清掉的只会是离开海纳尔之前的旧状态
        if (!inHoanna()) {
            STATE.clear();
            JOLTS.clear();
        }
    }

    @SubscribeEvent
    public static void onHoannaLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STATE.clear();
        JOLTS.clear();
        clearShowroomOverride();
    }
}
