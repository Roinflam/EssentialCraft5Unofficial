package com.flameforge.essentialcraft5.client.vfx;

import com.flameforge.essentialcraft5.config.VfxClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 特效底座的总入口：画法（完整 / 稳妥）、质量档位、远近细节、帧率保护、颜色小工具。只在客户端用。
 * <h2>花多少</h2>
 * 每个特效生成粒子、分叉、面片前先问 {@link #count(float, Vec3)}：设计时按 HIGH 档、近处写数量，这里乘
 * 「质量档位 × 原版粒子设置 × 远近 × 帧率保护」再取整（小数部分按概率进位，平均下来数量对，少量的特效也不会被一刀切成 0）。
 * <ul>
 *   <li>质量：HIGH 1，MEDIUM 0.65，LOW 0.35；原版「粒子：减少」再乘 0.5、「最少」乘 0.2；</li>
 *   <li>远近：{@value #LOD_NEAR} 格以内 1，往外线性降到 {@value #LOD_FAR} 格处 0.25，{@value #LOD_CULL} 格外不画（{@link #visibleAt}）；</li>
 *   <li>帧率保护：每帧量一次帧时间，最近一秒平均超过 {@value #SLOW_FRAME_MS} ms（不到 30 帧）就把系数每秒乘 0.85，最低 0.35；
 *       平均低于 {@value #FAST_FRAME_MS} ms 再慢慢回到 1。录制工具逐帧离线渲染时帧时间不代表真实帧率，可以把 {@link #governorEnabled} 关掉。</li>
 * </ul>
 * 硬上限：粒子池 {@link VfxParticles#capacity()}（按档位 1500 / 4000 / 8000），同时存在的特效 {@link VfxEffects#MAX_EFFECTS}。
 */
public final class Vfx {

    public static final double LOD_NEAR = 24D;
    public static final double LOD_FAR = 80D;
    public static final double LOD_CULL = 128D;
    private static final double SLOW_FRAME_MS = 33D;
    private static final double FAST_FRAME_MS = 22D;

    /** 帧率保护开关（录制时关掉，见类注释） */
    public static boolean governorEnabled = true;
    /** 开发对照用：0 = 按配置和光影检测，1 = 强制稳妥画法，2 = 强制完整画法（光影包下会出错，看出错的样子用） */
    public static int forceMode = 0;
    /** 开发对照用：-1 = 按配置，0 / 1 / 2 = 强制 LOW / MEDIUM / HIGH（录低档样片用） */
    public static int forceQuality = -1;

    private static final RandomSource RANDOM = RandomSource.create();
    private static float governor = 1F;
    private static long lastFrameNanos;
    private static double frameAccumMs;
    private static int frameCount;
    private static boolean frameSafe;

    private Vfx() {
    }

    public static RandomSource random() {
        return RANDOM;
    }

    // ------------------------------------------------------------------ 画法

    /** 这一帧用稳妥画法（每帧开头 {@link #beginFrame} 定一次） */
    public static boolean safeMode() {
        return frameSafe;
    }

    private static boolean computeSafe() {
        if (!VfxShaders.ready() || forceMode == 1) {
            return true;
        }
        if (forceMode == 2) {
            return false;
        }
        return switch (VfxClientConfig.shaderPackMode()) {
            case SAFE -> true;
            case FULL -> false;
            case AUTO -> VfxCompat.shaderPackInUse();
        };
    }

    /** 能不能画屏幕扭曲（完整画法、扭曲着色器在、配置开、非 LOW、非 Fabulous） */
    public static boolean distortionAllowed() {
        return !frameSafe && VfxShaders.distort() != null && VfxClientConfig.distortion()
                && quality() != VfxClientConfig.Quality.LOW && !Minecraft.useShaderTransparency();
    }

    // ------------------------------------------------------------------ 数量

    /** 生效的质量档位（配置，或开发对照时强制的那一档） */
    public static VfxClientConfig.Quality quality() {
        if (forceQuality >= 0) {
            return VfxClientConfig.Quality.values()[Math.min(forceQuality, VfxClientConfig.Quality.values().length - 1)];
        }
        return VfxClientConfig.quality();
    }

    /**
     * 结构档（层数、分叉、装饰这类不能按比例缩的东西用）：2 = 全部层次；1 = 去掉最外层辉光、二级分叉、地面电流这些次要装饰；
     * 0 = 只留看得懂的主体（芯 + 一层辉光）。帧率保护压到 0.6 以下时再降一档。
     */
    public static int tier() {
        int t = switch (quality()) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
        };
        return governor < 0.6F ? Math.max(0, t - 1) : t;
    }

    /** 质量档位 × 原版粒子设置 × 帧率保护（不含远近） */
    public static float scale() {
        float q = switch (quality()) {
            case LOW -> 0.35F;
            case MEDIUM -> 0.65F;
            case HIGH -> 1F;
        };
        ParticleStatus particles = Minecraft.getInstance().options.particles().get();
        if (particles == ParticleStatus.DECREASED) {
            q *= 0.5F;
        } else if (particles == ParticleStatus.MINIMAL) {
            q *= 0.2F;
        }
        return q * governor;
    }

    /** 远近系数（离镜头的距离） */
    public static float lod(Vec3 at) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = camera.distanceTo(at);
        if (d <= LOD_NEAR) {
            return 1F;
        }
        if (d >= LOD_CULL) {
            return 0F;
        }
        return (float) Mth.clampedMap(d, LOD_NEAR, LOD_FAR, 1D, 0.25D);
    }

    /** 离镜头近到值得画 */
    public static boolean visibleAt(Vec3 at) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return camera.distanceToSqr(at) < LOD_CULL * LOD_CULL;
    }

    /** 按 HIGH 档、近处设计的数量 → 这一次实际生成几个（小数按概率进位） */
    public static int count(float designed, Vec3 at) {
        float n = designed * scale() * lod(at);
        int whole = (int) n;
        return whole + (RANDOM.nextFloat() < n - whole ? 1 : 0);
    }

    /** 细节系数（分叉数、段数这类不是粒子的数量用；不低于 0.3，形状不会塌） */
    public static float detail(Vec3 at) {
        return Math.max(0.3F, scale() * lod(at));
    }

    /** 每帧开头调一次：定画法、量帧时间 */
    static void beginFrame() {
        frameSafe = computeSafe();
        long now = Util.getNanos();
        if (lastFrameNanos != 0L) {
            double ms = (now - lastFrameNanos) / 1.0E6D;
            if (ms < 1000D) {
                frameAccumMs += ms;
                frameCount++;
            }
        }
        lastFrameNanos = now;
        if (frameAccumMs >= 1000D && frameCount > 0) {
            double average = frameAccumMs / frameCount;
            if (!governorEnabled) {
                governor = 1F;
            } else if (average > SLOW_FRAME_MS) {
                governor = Math.max(0.35F, governor * 0.85F);
            } else if (average < FAST_FRAME_MS) {
                governor = Math.min(1F, governor + 0.05F);
            }
            frameAccumMs = 0D;
            frameCount = 0;
        }
    }

    /** 当前的帧率保护系数（调试用） */
    public static float governor() {
        return governor;
    }

    // ------------------------------------------------------------------ 颜色

    /** RGB + 不透明度 → ARGB */
    public static int argb(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** 两个 ARGB 按 t 插值 */
    public static int lerp(int from, int to, float t) {
        t = Mth.clamp(t, 0F, 1F);
        int a = (int) Mth.lerp(t, (from >>> 24) & 255, (to >>> 24) & 255);
        int r = (int) Mth.lerp(t, (from >> 16) & 255, (to >> 16) & 255);
        int g = (int) Mth.lerp(t, (from >> 8) & 255, (to >> 8) & 255);
        int b = (int) Mth.lerp(t, from & 255, to & 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 把不透明度乘上一个系数 */
    public static int fade(int argb, float factor) {
        int a = Mth.clamp(Math.round(((argb >>> 24) & 255) * factor), 0, 255);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ 缓动

    public static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0F, 1F);
        float u = 1F - t;
        return 1F - u * u * u;
    }

    public static float easeInCubic(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return t * t * t;
    }

    public static float easeOutExpo(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return t >= 1F ? 1F : 1F - (float) Math.pow(2D, -10D * t);
    }

    /** 0 → 1 → 0 的包络：attack 份时间升到 1，之后到 1 慢慢落 */
    public static float pulse(float t, float attack) {
        if (t <= 0F || t >= 1F) {
            return 0F;
        }
        return t < attack ? t / attack : 1F - easeInCubic((t - attack) / (1F - attack));
    }
}
