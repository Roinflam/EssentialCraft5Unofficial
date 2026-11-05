package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;

import java.util.Arrays;

/**
 * 客户端看到的天灾状态机（纯数据，两端都能加载，GameTest 直接测；客户端的唯一实例在 {@code client/hoanna/HoannaClientEvents}）。
 * 第六批 6a 框架 F6a，batch6a.md §3.4「状态同步」。
 * <p>
 * 喂 {@link #accept}（来自 {@code S2CHoannaEventPacket}，带上收到时的 gameTime），读 {@link #fade}（0~1，t = gameTime + partialTick）：
 * <ul>
 *   <li>新事件：从收到那一刻起按 {@link HoannaFade#active} 淡入（半路进来的也从到达时淡入）。</li>
 *   <li>同一个事件的重发（换维度、重生时服务端会再发一次）：起止刻差不到 {@link #RESYNC_TOLERANCE} 就只校正时间，不重新淡入。</li>
 *   <li>停止（收到 NONE）或换成别的事件：旧事件从当时的强度按 {@link HoannaFade#stopping} 线性淡出；
 *       同一种事件停了又马上开始时取两者较大值，画面不跳。每种事件各有自己的淡出槽，互不打断。</li>
 * </ul>
 * 只在客户端主线程用（包处理已在主线程）。
 */
public final class HoannaEventState {

    /** 同一事件重发时，起止刻差在这个范围内算同一次（客户端与服务端 gameTime 的同步误差、包在路上的时间） */
    public static final int RESYNC_TOLERANCE = 40;

    private static final int SLOTS = HoannaEvent.values().length;

    private HoannaEvent current = HoannaEvent.NONE;
    private long start;
    private long end;
    private long visibleFrom;
    private final float[] stopFrom = new float[SLOTS];
    private final long[] stoppedAt = new long[SLOTS];

    /**
     * 收到状态包。
     *
     * @param now 收到时本地的 gameTime
     */
    public void accept(HoannaEvent event, int remaining, int total, long now) {
        if (event.isNone() || total <= 0) {
            stopCurrent(now);
            return;
        }
        long newStart = now - (total - Math.max(0, Math.min(remaining, total)));
        long newEnd = newStart + total;
        if (event == current && Math.abs(newStart - start) <= RESYNC_TOLERANCE && Math.abs(newEnd - end) <= RESYNC_TOLERANCE) {
            start = newStart;
            end = newEnd;
            return;
        }
        stopCurrent(now);
        current = event;
        start = newStart;
        end = newEnd;
        visibleFrom = now;
    }

    /** 离开海纳尔、断线：立刻清零（不淡出——换了世界，画面本来就整个换了） */
    public void clear() {
        current = HoannaEvent.NONE;
        start = end = visibleFrom = 0L;
        Arrays.fill(stopFrom, 0F);
        Arrays.fill(stoppedAt, 0L);
    }

    private void stopCurrent(long now) {
        if (current.isNone()) {
            return;
        }
        int slot = current.id();
        float value = fade(current, now);
        stopFrom[slot] = value;
        stoppedAt[slot] = now;
        current = HoannaEvent.NONE;
    }

    /** 这种事件在时刻 t（gameTime + partialTick）的画面强度，0~1 */
    public float fade(HoannaEvent event, double t) {
        if (event.isNone()) {
            return 0F;
        }
        int slot = event.id();
        float stopping = HoannaFade.stopping(stopFrom[slot], t, stoppedAt[slot]);
        float active = event == current ? HoannaFade.active(event, t, start, end, visibleFrom) : 0F;
        return Math.max(active, stopping);
    }

    /** 正在进行的事件（停止后淡出期间已经是 NONE；要画淡出就对每种事件调 {@link #fade}） */
    public HoannaEvent current() {
        return current;
    }

    /** 当前事件开始的游戏刻（NONE 时无意义） */
    public long start() {
        return start;
    }

    /** 当前事件结束的游戏刻（NONE 时无意义） */
    public long end() {
        return end;
    }

    /** 还剩多少 tick（NONE 时 0） */
    public int remaining(long now) {
        return current.isNone() ? 0 : (int) Math.max(0L, Math.min(Integer.MAX_VALUE, end - now));
    }

    /** 总时长（NONE 时 0） */
    public int total() {
        return current.isNone() ? 0 : (int) Math.min(Integer.MAX_VALUE, end - start);
    }
}
