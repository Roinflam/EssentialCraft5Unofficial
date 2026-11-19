package com.flameforge.essentialcraft5.client.vfx;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 发光粒子池：不走原版粒子引擎，所有粒子存在几组定长数组里，每刻统一推进、每帧统一画进 {@link VfxDraw}（一个渲染类型、一次提交）。
 * 原版粒子每个都是一个对象、按种类分批画，几千个发光粒子时开销主要在对象和批次切换上；这里没有分配、没有排序（加色混合不需要）。
 * <p>
 * 一个粒子：位置、速度、阻力（每刻速度 × drag）、重力、寿命、开始 / 结束时的大小和颜色、模式（{@link VfxMode#DOT} 光点、
 * {@link VfxMode#SPARK} 顺着速度拉长的火花、{@link VfxMode#FLARE} 星芒、{@link VfxMode#BURST} 爆点、{@link VfxMode#SMOKE} 烟团〔半透明〕）、自转。
 * 池满了新粒子直接丢掉（不挤掉旧的：旧的往往是正在消散的那一批，挤掉会突然缺一块）。
 */
public final class VfxParticles {

    private static final int MAX = 8192;

    private static final double[] X = new double[MAX];
    private static final double[] Y = new double[MAX];
    private static final double[] Z = new double[MAX];
    private static final double[] PX = new double[MAX];
    private static final double[] PY = new double[MAX];
    private static final double[] PZ = new double[MAX];
    private static final float[] VX = new float[MAX];
    private static final float[] VY = new float[MAX];
    private static final float[] VZ = new float[MAX];
    private static final float[] DRAG = new float[MAX];
    private static final float[] GRAVITY = new float[MAX];
    private static final float[] SIZE0 = new float[MAX];
    private static final float[] SIZE1 = new float[MAX];
    private static final int[] COLOR0 = new int[MAX];
    private static final int[] COLOR1 = new int[MAX];
    private static final int[] AGE = new int[MAX];
    private static final int[] LIFE = new int[MAX];
    private static final byte[] MODE = new byte[MAX];
    private static final short[] PARAM = new short[MAX];
    private static final float[] STRETCH = new float[MAX];
    private static final float[] SPIN = new float[MAX];
    private static final float[] ROT = new float[MAX];
    private static int count;

    private VfxParticles() {
    }

    /** 按质量档位的粒子上限 */
    public static int capacity() {
        return switch (Vfx.quality()) {
            case LOW -> 1500;
            case MEDIUM -> 4000;
            case HIGH -> MAX;
        };
    }

    public static int count() {
        return count;
    }

    /**
     * 放一个粒子。
     *
     * @param life    寿命（刻）
     * @param size0   开始时的半径（格），size1 结束时
     * @param color0  开始时的颜色（ARGB），color1 结束时（一般把 alpha 设成 0 让它淡掉）
     * @param mode    {@link VfxMode} 里的 DOT / SPARK / FLARE / BURST / SMOKE
     * @param drag    每刻速度乘这个数（1 = 没有阻力）
     * @param gravity 每刻往下加的速度（格 / 刻²，负数往上飘）
     * @param stretch 火花：拖尾长度 = 速度 × stretch 刻
     * @param spin    自转（弧度 / 刻）
     */
    public static void spawn(double x, double y, double z, double vx, double vy, double vz, int life, float size0, float size1,
                             int color0, int color1, int mode, int param, float drag, float gravity, float stretch, float spin) {
        if (count >= capacity() || life <= 0) {
            return;
        }
        int i = count++;
        X[i] = PX[i] = x;
        Y[i] = PY[i] = y;
        Z[i] = PZ[i] = z;
        VX[i] = (float) vx;
        VY[i] = (float) vy;
        VZ[i] = (float) vz;
        LIFE[i] = life;
        AGE[i] = 0;
        SIZE0[i] = size0;
        SIZE1[i] = size1;
        COLOR0[i] = color0;
        COLOR1[i] = color1;
        MODE[i] = (byte) mode;
        PARAM[i] = (short) Mth.clamp(param, 0, Short.MAX_VALUE);
        DRAG[i] = drag;
        GRAVITY[i] = gravity;
        STRETCH[i] = stretch;
        SPIN[i] = spin;
        ROT[i] = Vfx.random().nextFloat() * Mth.TWO_PI;
    }

    /** 每刻推进（{@link VfxEffects} 在客户端刻末尾调） */
    static void tick() {
        int i = 0;
        while (i < count) {
            if (++AGE[i] >= LIFE[i]) {
                remove(i);
                continue;
            }
            PX[i] = X[i];
            PY[i] = Y[i];
            PZ[i] = Z[i];
            VX[i] *= DRAG[i];
            VY[i] = VY[i] * DRAG[i] - GRAVITY[i];
            VZ[i] *= DRAG[i];
            X[i] += VX[i];
            Y[i] += VY[i];
            Z[i] += VZ[i];
            ROT[i] += SPIN[i];
            i++;
        }
    }

    static void clear() {
        count = 0;
    }

    private static void remove(int i) {
        int last = --count;
        if (i == last) {
            return;
        }
        X[i] = X[last];
        Y[i] = Y[last];
        Z[i] = Z[last];
        PX[i] = PX[last];
        PY[i] = PY[last];
        PZ[i] = PZ[last];
        VX[i] = VX[last];
        VY[i] = VY[last];
        VZ[i] = VZ[last];
        DRAG[i] = DRAG[last];
        GRAVITY[i] = GRAVITY[last];
        SIZE0[i] = SIZE0[last];
        SIZE1[i] = SIZE1[last];
        COLOR0[i] = COLOR0[last];
        COLOR1[i] = COLOR1[last];
        AGE[i] = AGE[last];
        LIFE[i] = LIFE[last];
        MODE[i] = MODE[last];
        PARAM[i] = PARAM[last];
        STRETCH[i] = STRETCH[last];
        SPIN[i] = SPIN[last];
        ROT[i] = ROT[last];
    }

    /** 画全部粒子 */
    static void render(VfxDraw draw) {
        float pt = draw.partialTick();
        Vec3 cam = draw.camera();
        double cull = Vfx.LOD_CULL * Vfx.LOD_CULL;
        for (int i = 0; i < count; i++) {
            double x = Mth.lerp(pt, PX[i], X[i]);
            double y = Mth.lerp(pt, PY[i], Y[i]);
            double z = Mth.lerp(pt, PZ[i], Z[i]);
            double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
            if (dx * dx + dy * dy + dz * dz > cull) {
                continue;
            }
            float t = (AGE[i] + pt) / LIFE[i];
            float size = Mth.lerp(t, SIZE0[i], SIZE1[i]);
            int color = Vfx.lerp(COLOR0[i], COLOR1[i], t);
            // 头两刻淡入，免得一出生就是一个硬亮点
            if (AGE[i] < 2) {
                color = Vfx.fade(color, (AGE[i] + pt) / 2F);
            }
            int mode = MODE[i];
            if (mode == VfxMode.SPARK) {
                float s = STRETCH[i];
                Vec3 head = new Vec3(x, y, z);
                Vec3 tail = new Vec3(x - VX[i] * s, y - VY[i] * s, z - VZ[i] * s);
                draw.strip(tail, head, size * 0.3F, size, Vfx.fade(color, 0.6F), color, VfxMode.SPARK, PARAM[i], 0F, 1F);
            } else {
                draw.billboard(x, y, z, size, size, ROT[i] + SPIN[i] * pt, mode, PARAM[i], color, mode == VfxMode.SMOKE);
            }
        }
    }
}
