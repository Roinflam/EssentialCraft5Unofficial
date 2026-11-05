package com.flameforge.essentialcraft5.entity.boss;

/**
 * 首领整屏画面的护眼数值与纯函数（第十三批 13c-Fb，batch13.md §5.3.5「护眼」、policy §1.2 / §4.18）。
 * <b>common 类：不 import 任何客户端类</b>，GameTest 在专用服上直接测它；客户端的 {@code client/boss/BossFxClient} 只从这里取数。
 * 照海纳尔的画面（{@code hoanna/HoannaVisuals}）同一套规矩：
 * <ol>
 *   <li><b>状态切换 ≥ 10 秒淡入淡出</b>：每一次改变目标（出现、加深、变浅、消失）都按 {@link #FADE_TICKS}（200 刻）走完，
 *       不论改多少（{@link Fade}）；中途再改就从当前值重新起一段 200 刻。颜色另外单独计时（{@link ColorFade}）：换色同样走满 200 刻，
 *       和强度变没变无关（只换颜色、不换强度的事件也不会一帧跳色）。</li>
 *   <li><b>常驻叠层只画边缘、最浓 ≤ 0.20</b>：边缘晕影的强度 0~1 映射到 0~{@link #EDGE_MAX}（{@link #edgeAlpha}），几种同时在时合计也不超过
 *       {@link #EDGE_MAX}（{@link #combinedEdge}）；从屏幕边缘往里 {@code 短边 × }{@link #EDGE_BAND} 淡到全透明，中间一大片不碰。</li>
 *   <li><b>亮度突变 ≤ 0.1 次 / 秒</b>：唯一允许的突变是一次性的闪光（{@link FlashLimiter}），全局至少隔 {@link #FLASH_INTERVAL}（200 刻）一次，
 *       峰值 ≤ {@link #FLASH_MAX}、{@link #FLASH_TICKS} 刻内淡完；打开原版「隐藏闪电闪烁」时峰值只剩 1/3、间隔翻倍（同远空电弧）。
 *       闪光和晕影都再乘原版「屏幕扭曲效果」滑块。</li>
 * </ol>
 * 客户端配置 {@code [boss_fx] screen_effects}（默认开）关掉以后一律不画。
 */
public final class BossFxRules {

    /** 状态切换的淡入淡出（刻） */
    public static final int FADE_TICKS = 200;
    /** 常驻边缘晕影最浓 */
    public static final float EDGE_MAX = 0.20F;
    /** 晕影从屏幕边缘往里多宽（短边的比例） */
    public static final float EDGE_BAND = 0.22F;
    /** 两次闪光之间至少隔几刻（≤ 0.1 次 / 秒） */
    public static final int FLASH_INTERVAL = 200;
    /** 闪光峰值 */
    public static final float FLASH_MAX = 0.35F;
    /** 一次闪光几刻淡完 */
    public static final int FLASH_TICKS = 12;
    /** 打开「隐藏闪电闪烁」时闪光峰值乘多少 */
    public static final float FLASH_REDUCED = 1F / 3F;

    private BossFxRules() {
    }

    /** 晕影强度（0~1，不是有限数按 0）→ 不透明度 0~{@link #EDGE_MAX} */
    public static float edgeAlpha(float strength) {
        return clamp01(strength) * EDGE_MAX;
    }

    /** 几层晕影同时画时的合计不透明度（上限 {@link #EDGE_MAX}） */
    public static float combinedEdge(float... alphas) {
        float sum = 0F;
        for (float alpha : alphas) {
            if (alpha > 0F && Float.isFinite(alpha)) {
                sum += alpha;
            }
        }
        return Math.min(EDGE_MAX, sum);
    }

    /** 合计超过上限时每层乘多少（≤ 1） */
    public static float edgeScale(float sum) {
        return sum > EDGE_MAX ? EDGE_MAX / sum : 1F;
    }

    /** 闪光峰值（要求的峰值夹到 {@link #FLASH_MAX}；hideFlashes 时只剩 1/3） */
    public static float flashPeak(float requested, boolean hideFlashes) {
        float peak = Math.min(FLASH_MAX, clamp01(requested));
        return hideFlashes ? peak * FLASH_REDUCED : peak;
    }

    /** 闪光间隔（hideFlashes 时翻倍） */
    public static int flashInterval(boolean hideFlashes) {
        return hideFlashes ? FLASH_INTERVAL * 2 : FLASH_INTERVAL;
    }

    /** 闪光开始后第 age 刻的不透明度（线性淡完；age 不在 [0, FLASH_TICKS) 里是 0） */
    public static float flashAlpha(float peak, float age) {
        if (!(age >= 0F) || age >= FLASH_TICKS) {
            return 0F;
        }
        return peak * (1F - age / FLASH_TICKS);
    }

    public static float clamp01(float value) {
        return value > 0F ? Math.min(1F, value) : 0F;
    }

    /** 两端缓的插值进度（t 先夹到 0~1）：起止都没有斜率突变，{@link Fade} 和 {@link ColorFade} 共用 */
    public static float smoothstep(float t) {
        float x = clamp01(t);
        return x * x * (3F - 2F * x);
    }

    /** 两个 RGB（24 位）按 R / G / B 分量线性插值，t 夹到 0~1，各分量四舍五入 */
    public static int lerpRgb(int from, int to, float t) {
        float x = clamp01(t);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, x);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, x);
        int b = lerpChannel(from & 0xFF, to & 0xFF, x);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    /**
     * 一段淡入淡出：每次改目标都从当前值出发、走满 {@link #FADE_TICKS} 刻（或更长）；走到以后停在目标上。
     * 按刻推进（{@link #tick}），渲染时用 {@link #value(float)} 在两刻之间插值。
     */
    public static final class Fade {

        private float from;
        private float target;
        private float current;
        private float previous;
        private int duration = FADE_TICKS;
        private int age = FADE_TICKS;

        /** 改目标（0~1）；和现在的目标一样就不动。duration 小于 {@link #FADE_TICKS} 按 {@link #FADE_TICKS} */
        public void retarget(float value, int duration) {
            float clamped = clamp01(value);
            if (clamped == target) {
                return;
            }
            from = current;
            target = clamped;
            this.duration = Math.max(FADE_TICKS, duration);
            age = 0;
        }

        public void retarget(float value) {
            retarget(value, FADE_TICKS);
        }

        /** 推进一刻 */
        public void tick() {
            previous = current;
            if (age < duration) {
                age++;
                current = from + (target - from) * smoothstep(age / (float) duration);
            } else {
                current = target;
            }
        }

        /** 这一刻和上一刻之间（partial 0~1）的值 */
        public float value(float partial) {
            return previous + (current - previous) * clamp01(partial);
        }

        public float current() {
            return current;
        }

        public float target() {
            return target;
        }

        /** 已经淡到 0、目标也是 0（可以扔掉了） */
        public boolean finished() {
            return target <= 0F && current <= 0F && previous <= 0F;
        }
    }

    /**
     * 颜色的淡变：和 {@link Fade} 分开计时。{@link #snap} 直接摆到某个颜色（新出现的一层，强度本来就从 0 淡入）；
     * {@link #retarget} 换色时从当前显示的颜色出发、走满 {@link #FADE_TICKS} 刻到新颜色，中途再换就从那一刻的颜色重新起一段。
     * 按刻推进（{@link #tick}），渲染时用 {@link #value(float)} 在两刻之间插值。
     */
    public static final class ColorFade {

        private int from;
        private int target;
        private int current;
        private int previous;
        private int age = FADE_TICKS;

        /** 直接摆到 rgb，不淡 */
        public void snap(int rgb) {
            int value = rgb & 0xFFFFFF;
            from = value;
            target = value;
            current = value;
            previous = value;
            age = FADE_TICKS;
        }

        /** 换到 rgb：和现在的目标一样就不动，否则从当前颜色起走满 200 刻 */
        public void retarget(int rgb) {
            int value = rgb & 0xFFFFFF;
            if (value == target) {
                return;
            }
            from = current;
            target = value;
            age = 0;
        }

        /** 推进一刻 */
        public void tick() {
            previous = current;
            if (age < FADE_TICKS) {
                age++;
                current = lerpRgb(from, target, smoothstep(age / (float) FADE_TICKS));
            } else {
                current = target;
            }
        }

        /** 这一刻和上一刻之间（partial 0~1）的颜色 */
        public int value(float partial) {
            return lerpRgb(previous, current, partial);
        }

        public int current() {
            return current;
        }

        public int target() {
            return target;
        }
    }

    /** 闪光限频：全局一份（客户端），{@link #tryFlash} 通过了才闪 */
    public static final class FlashLimiter {

        private long last = Long.MIN_VALUE / 2;

        /** now 是客户端的游戏刻；距上次闪光不够 interval 刻就拒绝 */
        public boolean tryFlash(long now, int interval) {
            if (now - last < interval) {
                return false;
            }
            last = now;
            return true;
        }

        public void reset() {
            last = Long.MIN_VALUE / 2;
        }
    }
}
