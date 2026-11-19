package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.network.S2CBuilderHoldingsPacket;
import com.flameforge.essentialcraft5.network.S2CBuilderPanelPacket;
import com.flameforge.essentialcraft5.network.S2CBuilderStatusPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 服务端发来的创世神之杖状态（{@link S2CBuilderStatusPacket}）：存最近一份和收到的那一刻，准星下提示读它。只在客户端主线程上用。
 * <ul>
 *   <li>剩余时间按最近 {@value #ETA_WINDOW} 刻里的进度估：每收到一份施工中的状态记一个（时刻、已做格数）样本，
 *       状态、总格数变了或者进度倒退就清掉重记。</li>
 *   <li>待确认：服务端要求再点一次时只在动作栏发一句（{@code message.essentialcraft5.builder.confirm_*}），状态包里没有这一项；
 *       这里认动作栏这句话的翻译键，记下 {@value BuilderJobs#CONFIRM_TICKS} 刻的倒计时（和服务端的待确认时限一致），
 *       收到别的魔杖提示、或者状态变成不是空闲时作废。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class BuilderClientStatus {

    /** 估剩余时间看最近这么多刻的进度 */
    public static final int ETA_WINDOW = 20;
    private static final String MESSAGE_PREFIX = "message.essentialcraft5.builder.";
    private static final String CONFIRM_PREFIX = MESSAGE_PREFIX + "confirm_";

    @Nullable
    private static S2CBuilderStatusPacket latest;
    private static long receivedAt;
    /** 待确认到这一刻（世界时间）为止；0 = 没有 */
    private static long confirmUntil;
    /** {时刻, 已做格数}，旧的在前 */
    private static final Deque<long[]> SAMPLES = new ArrayDeque<>();
    @Nullable
    private static BuilderGhost.Preview jobPreview;
    @Nullable
    private static S2CBuilderPanelPacket panel;
    /** 混铺每种料持有多少（{@link S2CBuilderHoldingsPacket}） */
    private static Map<Item, Long> holdings = Map.of();

    private BuilderClientStatus() {
    }

    public static void accept(S2CBuilderStatusPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        S2CBuilderStatusPacket previous = latest;
        latest = packet;
        boolean wasWorking = previous != null && working(previous.state());
        if (working(packet.state()) && !wasWorking) {
            // 刚开工（含排队、测量）：记下点下去时准星下的那份预览，面板的蓝图按它画进度；撤销没有预览
            jobPreview = packet.state() == S2CBuilderStatusPacket.UNDOING ? null : BuilderGhost.preview();
        } else if (!working(packet.state())) {
            jobPreview = null;
        }
        receivedAt = now;
        if (packet.state() != S2CBuilderStatusPacket.IDLE) {
            confirmUntil = 0L;
        }
        boolean progressing = packet.state() == S2CBuilderStatusPacket.RUNNING || packet.state() == S2CBuilderStatusPacket.UNDOING;
        if (!progressing || previous == null || previous.state() != packet.state() || previous.total() != packet.total()
                || previous.done() > packet.done()) {
            SAMPLES.clear();
        }
        if (progressing) {
            SAMPLES.addLast(new long[] {now, packet.done()});
            while (SAMPLES.size() > 8) {
                SAMPLES.removeFirst();
            }
        }
    }

    @Nullable
    public static S2CBuilderStatusPacket latest() {
        return latest;
    }

    /** 这个状态算「在施工」（做、暂停、排队、撤销、测量）：面板除撤销和停下外都变灰 */
    public static boolean working(int state) {
        return state != S2CBuilderStatusPacket.IDLE;
    }

    /** 现在在施工（按最近一份状态） */
    public static boolean working() {
        S2CBuilderStatusPacket packet = latest;
        return packet != null && working(packet.state());
    }

    /** 开工那一刻准星下的预览（面板蓝图画进度用）；没在施工、撤销中或没有预览时为 null */
    @Nullable
    public static BuilderGhost.Preview jobPreview() {
        return jobPreview;
    }

    public static void acceptPanel(S2CBuilderPanelPacket packet) {
        panel = packet;
    }

    /** 最近一份面板清单；没要过返回 null */
    @Nullable
    public static S2CBuilderPanelPacket panel() {
        return panel;
    }

    public static void acceptHoldings(S2CBuilderHoldingsPacket packet) {
        Map<Item, Long> map = new IdentityHashMap<>();
        for (int i = 0; i < packet.items().size(); i++) {
            Item item = ForgeRegistries.ITEMS.getValue(packet.items().get(i));
            if (item != null && item != Items.AIR) {
                map.put(item, packet.counts().get(i));
            }
        }
        holdings = map;
    }

    /** 混铺时这种料持有多少（服务端按每种发来的）；不知道返回 -1 */
    public static long holding(Item item) {
        Long count = holdings.get(item);
        return count == null ? -1L : count;
    }

    /** 收到最近一份时的世界时间 */
    public static long receivedAt() {
        return receivedAt;
    }

    /** 暂停还剩几刻（按收到以后过去的时间往下扣，不小于 0） */
    public static int pauseTicksLeft(long now) {
        S2CBuilderStatusPacket packet = latest;
        if (packet == null) {
            return 0;
        }
        return (int) Math.max(0L, packet.pauseTicksLeft() - Math.max(0L, now - receivedAt));
    }

    /**
     * 施工 / 撤销还要几刻：按最近 {@value #ETA_WINDOW} 刻里（样本不够时放宽到手头最早的一个）的进度算；
     * 样本跨度不到 10 刻、或这段时间没有进度时返回 -1（不显示）
     */
    public static long etaTicks(long now) {
        S2CBuilderStatusPacket packet = latest;
        if (packet == null || SAMPLES.size() < 2) {
            return -1L;
        }
        long[] last = SAMPLES.peekLast();
        long[] base = null;
        for (long[] sample : SAMPLES) {
            if (sample != last && last[0] - sample[0] <= ETA_WINDOW) {
                base = sample;
                break;
            }
        }
        if (base == null) {
            // 窗口里只有最新这一个（状态包隔得比窗口还久）：退一步用紧挨着它的前一个
            Iterator<long[]> newestFirst = SAMPLES.descendingIterator();
            newestFirst.next();
            base = newestFirst.next();
        }
        if (last[0] - base[0] < 10L) {
            return -1L;
        }
        long doneDelta = last[1] - base[1];
        if (doneDelta <= 0L) {
            return -1L;
        }
        double perTick = doneDelta / (double) (last[0] - base[0]);
        long left = Math.max(0L, packet.total() - packet.done());
        return Math.max(0L, (long) Math.ceil(left / perTick) - Math.max(0L, now - receivedAt));
    }

    /** 待确认还剩几刻；没有返回 0 */
    public static int confirmTicksLeft(long now) {
        return confirmUntil <= now ? 0 : (int) (confirmUntil - now);
    }

    public static void clear() {
        latest = null;
        confirmUntil = 0L;
        SAMPLES.clear();
        jobPreview = null;
        panel = null;
        holdings = Map.of();
    }

    /** 动作栏里魔杖的提示：认出「再点一次确认」那几句，开始倒计时；别的魔杖提示（开工、排队、出错）作废倒计时 */
    @SubscribeEvent
    public static void onBuilderSystemChat(ClientChatReceivedEvent.System event) {
        if (!event.isOverlay() || !(event.getMessage().getContents() instanceof TranslatableContents contents)) {
            return;
        }
        String key = contents.getKey();
        if (!key.startsWith(MESSAGE_PREFIX)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        confirmUntil = key.startsWith(CONFIRM_PREFIX) ? now + BuilderJobs.CONFIRM_TICKS : 0L;
    }
}
