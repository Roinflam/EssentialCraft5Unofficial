package com.flameforge.essentialcraft5.hoanna.portal;

/**
 * 进门暗角的强度曲线（纯数学，两端都能用；画在 {@code client/hoanna/HoannaPortalOverlay}，GameTest {@code hoannaPortalOverlayPureMath} 守着）。
 * batch6a §3.1 第 2 条 / §3.4：一次性的屏幕叠层最浓 ≤ 0.40 ×「屏幕扭曲效果」；按等待进度线性渐显（等到一半 = 0.20 × 缩放），
 * 离开门后至多 {@value #FADE_OUT_TICKS} 刻（0.5 秒）线性淡到 0；不旋转、不反胃、不闪。
 * <ul>
 *   <li>强度每刻只朝目标走一步（{@link #step}）：往上每刻至多 1 / max(等待时间, {@value #MIN_RISE_TICKS})，往下每刻至多 1 / {@value #FADE_OUT_TICKS}。
 *       所以离开一下又马上回去（计数没减完）也不会一下子亮起来；创造模式（等 1 刻）只会泛起一点点。</li>
 *   <li>站满等待时间后又过了 {@value #STALL_TICKS} 刻还没被传走（服务端拒了：建门全被领地拦下、门被关了……）就当离开处理、淡掉，不一直绿着。</li>
 * </ul>
 */
public final class PortalOverlayCurve {

    /** 最浓的不透明度（×「屏幕扭曲效果」） */
    public static final float MAX_ALPHA = 0.40F;
    /** 从最浓淡到 0 要几刻 */
    public static final int FADE_OUT_TICKS = 10;
    /** 渐显最快几刻到顶（等待时间更短时也按这个速度，免得一下子亮起来） */
    public static final int MIN_RISE_TICKS = 20;
    /** 站满等待时间后再过多少刻还没传走就淡掉 */
    public static final int STALL_TICKS = 40;
    /** 暗角从屏幕边缘往里多宽（短边的比例） */
    public static final float BAND = 0.25F;

    private PortalOverlayCurve() {
    }

    /** 等待进度 0~1（等待时间 ≤ 0 时按 1 算） */
    public static float progress(int insideTicks, int waitTicks) {
        if (waitTicks <= 0) {
            return insideTicks > 0 ? 1F : 0F;
        }
        return clamp01((float) insideTicks / waitTicks);
    }

    /** 强度这一刻朝 target 走一步（见类注释） */
    public static float step(float current, float target, int waitTicks) {
        float rise = 1F / Math.max(MIN_RISE_TICKS, waitTicks);
        float fall = 1F / FADE_OUT_TICKS;
        float next = target > current ? Math.min(target, current + rise) : Math.max(target, current - fall);
        return clamp01(next);
    }

    /** 强度 → 暗角外沿的不透明度：MAX_ALPHA × 强度 ×「屏幕扭曲效果」（0~1） */
    public static float alpha(float strength, double screenEffectScale) {
        return MAX_ALPHA * clamp01(strength) * clamp01((float) screenEffectScale);
    }

    private static float clamp01(float value) {
        return value > 0F ? Math.min(value, 1F) : 0F;
    }
}
