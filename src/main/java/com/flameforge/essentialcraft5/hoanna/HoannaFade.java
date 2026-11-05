package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;

/**
 * 海纳尔天灾画面的淡入淡出（纯数学，两端都能加载，GameTest 直接测；第六批 6a 框架 F6a，batch6a.md §3.1 第 3 条、§3.4）。
 * <p>
 * 事件从 {@code start} 到 {@code end}（游戏刻）：强度 = smoothstep((t − 起点) / 淡入) × smoothstep((end − t) / 淡出)，
 * 淡入 / 淡出 200 tick（10 秒），地震 60 / 100；t = gameTime + partialTick。「起点」取 {@code max(start, visibleFrom)}：
 * 半路进海纳尔（登录、过门）时事件早就开始了，画面也从到达那一刻起慢慢淡入，不会一进来就整屏变暗。
 * 中途停止（指令、服主关事件）：从停止那一刻的强度线性降到 0，用 {@link #STOP_TICKS}（不管哪种事件）。
 * <p>
 * 相邻两个 0.5 tick 的强度差：smoothstep 的最大斜率 1.5 / 时长，地震淡入 60 tick 时 1.5 / 60 × 0.5 = 0.0125 ≤ 0.02（GameTest 守着）。
 */
public final class HoannaFade {

    /** 普通事件的淡入 / 淡出（tick） */
    public static final int FADE_TICKS = 200;
    /** 地震的淡入 / 淡出（tick）：晃动来得快一些、停得稍慢 */
    public static final int QUAKE_FADE_IN = 60;
    public static final int QUAKE_FADE_OUT = 100;
    /** 中途停止时线性降到 0 的时长 */
    public static final int STOP_TICKS = 200;

    private HoannaFade() {
    }

    public static int fadeIn(HoannaEvent event) {
        return event == HoannaEvent.EARTHQUAKE ? QUAKE_FADE_IN : FADE_TICKS;
    }

    public static int fadeOut(HoannaEvent event) {
        return event == HoannaEvent.EARTHQUAKE ? QUAKE_FADE_OUT : FADE_TICKS;
    }

    /** 3x² − 2x³，x 先夹到 [0, 1] */
    public static float smoothstep(double x) {
        if (x <= 0D) {
            return 0F;
        }
        if (x >= 1D) {
            return 1F;
        }
        return (float) (x * x * (3D - 2D * x));
    }

    /**
     * 正在进行的事件在时刻 t 的强度（0~1）。
     *
     * @param start       事件开始的游戏刻
     * @param end         事件结束的游戏刻（start + 总时长）
     * @param visibleFrom 本客户端开始看到它的游戏刻（收到状态包那一刻；比 start 早时按 start）
     */
    public static float active(HoannaEvent event, double t, long start, long end, long visibleFrom) {
        if (event.isNone() || end <= start) {
            return 0F;
        }
        double from = Math.max(start, visibleFrom);
        return smoothstep((t - from) / fadeIn(event)) * smoothstep((end - t) / fadeOut(event));
    }

    /**
     * 中途停止后的强度：从停止那一刻的强度 {@code from} 线性降到 0。
     *
     * @param stoppedAt 停止的游戏刻
     */
    public static float stopping(float from, double t, long stoppedAt) {
        if (from <= 0F) {
            return 0F;
        }
        double left = 1D - (t - stoppedAt) / STOP_TICKS;
        if (left >= 1D) {
            return from;
        }
        return left <= 0D ? 0F : (float) (from * left);
    }
}
