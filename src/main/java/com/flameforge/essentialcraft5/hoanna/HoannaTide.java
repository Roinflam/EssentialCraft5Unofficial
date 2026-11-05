package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 海纳尔的魔力潮汐（第十三批 13d，batch13.md §6.2；HS §2.2.1）：潮位 T 只看月相，
 * {@code T = 1 − min(p, 8 − p) / 4}（p = 月相 0~7；满月 p = 0 → 1.0，新月 p = 4 → 0），八天里 T ≥ 0.75 的三天（p = 7、0、1）是大潮。
 * 纯函数，两端都能算，不发包、不存档：客户端用同一个公式画光幕，服务端用它算潮晶芽生长、地脉风暴、刷怪权重、坍缩速度。
 * <p>
 * 配置 {@code general.hoanna_tide.enabled}（默认开，{@link ECHoannaFramework.Config}）关掉时 T 恒为 0.5（不是 0：潮汐系数都写成 0.5 时等于「平潮」）。
 * 关掉以后没有大潮，地脉风暴不来；跟潮位走的倍数（光幕、本地怪权重、坍缩）一律回到 ×1，不放大也不缩小（光幕和权重在 T = 0.5 时本来就是 ×1，
 * 坍缩的 1 + 0.5T 在 0.5 时是 ×1.25，所以那边看 {@link #active} 另判）；潮晶芽改成每个夜里都长、几率 ×3/8，平均产量和开着时一样
 * （潮晶是宅男 2 阶的主线材料，不能因为关了潮汐就只剩世界生成时长好的那些）。
 * 客户端读到的开关是服务端同步来的值（服务端配置随登录同步）。
 * <p>
 * 用的地方（各路）：潮晶芽生长、地脉风暴只在大潮夜（{@link #isSpringTide}）；MRU 光幕不透明度 ×(0.6 + 0.8T)；本地怪权重 ×(0.75 + 0.5T)；
 * 坍缩速度 ×(1 + 0.5T)。GameTest 用 {@link #setTestOverride} 固定潮位（null 还原），别去拨月相。
 */
public final class HoannaTide {

    /** 大潮的门槛：T ≥ 0.75 */
    public static final float SPRING_TIDE = 0.75F;
    /** 关掉潮汐时的恒定潮位 */
    public static final float DISABLED_VALUE = 0.5F;
    /** 月相个数 */
    public static final int PHASES = 8;

    @Nullable
    private static volatile Float testOverride;

    private HoannaTide() {
    }

    /** 月相 p（0~7，越界按 8 取模）对应的潮位：1 − min(p, 8 − p) / 4 */
    public static float value(int moonPhase) {
        int p = Math.floorMod(moonPhase, PHASES);
        return 1F - Math.min(p, PHASES - p) / 4F;
    }

    /** 这个世界此刻的潮位：开着时按月相算，关着恒 {@value #DISABLED_VALUE}；测试覆盖优先 */
    public static float value(@Nullable Level level) {
        Float test = testOverride;
        if (test != null) {
            return test;
        }
        if (!enabled() || level == null) {
            return DISABLED_VALUE;
        }
        return value(level.getMoonPhase());
    }

    /** 大潮：潮位 ≥ {@value #SPRING_TIDE}（开着时八天里三天） */
    public static boolean isSpringTide(@Nullable Level level) {
        return value(level) >= SPRING_TIDE;
    }

    /** 纯函数版：这个潮位算不算大潮 */
    public static boolean isSpringTide(float value) {
        return value >= SPRING_TIDE;
    }

    /** 潮位这一刻起不起作用：潮汐开着，或者 GameTest 固定了潮位（{@link #setTestOverride}）。为假时跟潮位走的倍数都按 ×1 */
    public static boolean active() {
        return testOverride != null || enabled();
    }

    /** 配置 {@code general.hoanna_tide.enabled} */
    public static boolean enabled() {
        return ECHoannaFramework.Config.tideEnabled();
    }

    /** 只给 GameTest：固定 {@link #value(Level)} 的返回值（null 还原）；用完一定还原（finally 里） */
    public static void setTestOverride(@Nullable Float value) {
        testOverride = value;
    }
}
