package com.flameforge.essentialcraft5.client.vfx;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 路径生成和沿路径画长条的小工具：闪电那种折线（中点位移，越细分抖得越小）、法术丝线那种平滑弧线（三次贝塞尔）。
 * 点都存在 {@code double[]} 里（x, y, z 依次排），不分配 Vec3；{@link #drawPath} 把一条路径按宽度、颜色、可见范围画成 {@link VfxDraw#ribbon}。
 */
public final class VfxPaths {

    /** 一次画路径最多多少个点（{@link #drawPath} 的临时数组按它开） */
    public static final int MAX_POINTS = 129;
    private static final double[] TMP_XYZ = new double[MAX_POINTS * 3];
    private static final float[] TMP_HALF = new float[MAX_POINTS];
    private static final int[] TMP_ARGB = new int[MAX_POINTS];
    private static final float[] TMP_U = new float[MAX_POINTS];

    private VfxPaths() {
    }

    /** 中点位移的点数：2^depth + 1 */
    public static int jaggedPoints(int depth) {
        return (1 << depth) + 1;
    }

    /**
     * 闪电折线：两端钉在 a、b，中间每次对半分、往垂直于连线的随机方向推出去（第一层推 {@code roughness × 全长}，每深一层减半）。
     *
     * @return 点数（2^depth + 1）
     */
    public static int jagged(RandomSource random, Vec3 a, Vec3 b, int depth, float roughness, double[] out) {
        int n = jaggedPoints(depth);
        out[0] = a.x;
        out[1] = a.y;
        out[2] = a.z;
        int last = (n - 1) * 3;
        out[last] = b.x;
        out[last + 1] = b.y;
        out[last + 2] = b.z;
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6D) {
            for (int i = 1; i < n - 1; i++) {
                out[i * 3] = a.x;
                out[i * 3 + 1] = a.y;
                out[i * 3 + 2] = a.z;
            }
            return n;
        }
        double ux = dx / len, uy = dy / len, uz = dz / len;
        double amp = roughness * len;
        for (int step = n - 1; step > 1; step >>= 1) {
            int half = step >> 1;
            for (int i = half; i < n - 1; i += step) {
                int l = (i - half) * 3;
                int r = (i + half) * 3;
                double mx = (out[l] + out[r]) * 0.5D;
                double my = (out[l + 1] + out[r + 1]) * 0.5D;
                double mz = (out[l + 2] + out[r + 2]) * 0.5D;
                // 随机方向去掉沿连线的分量 → 垂直于连线
                double rx = random.nextGaussian(), ry = random.nextGaussian(), rz = random.nextGaussian();
                double along = rx * ux + ry * uy + rz * uz;
                rx -= along * ux;
                ry -= along * uy;
                rz -= along * uz;
                double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
                if (rl > 1.0E-6D) {
                    double s = amp * (random.nextDouble() * 0.8D + 0.2D) / rl;
                    mx += rx * s;
                    my += ry * s;
                    mz += rz * s;
                }
                out[i * 3] = mx;
                out[i * 3 + 1] = my;
                out[i * 3 + 2] = mz;
            }
            amp *= 0.5D;
        }
        return n;
    }

    /** 三次贝塞尔：n 个点均匀取 t */
    public static int bezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int n, double[] out) {
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            double u = 1D - t;
            double a = u * u * u, b = 3D * u * u * t, c = 3D * u * t * t, d = t * t * t;
            out[i * 3] = a * p0.x + b * p1.x + c * p2.x + d * p3.x;
            out[i * 3 + 1] = a * p0.y + b * p1.y + c * p2.y + d * p3.y;
            out[i * 3 + 2] = a * p0.z + b * p1.z + c * p2.z + d * p3.z;
        }
        return n;
    }

    /** 路径上第 i 个点 */
    public static Vec3 point(double[] xyz, int i) {
        return new Vec3(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]);
    }

    /** 路径上按点序号比例 f（0~1）插出来的点 */
    public static Vec3 at(double[] xyz, int n, double f) {
        double x = Math.max(0D, Math.min(1D, f)) * (n - 1);
        int i = Math.min(n - 2, (int) x);
        double t = x - i;
        return new Vec3(xyz[i * 3] + (xyz[i * 3 + 3] - xyz[i * 3]) * t,
                xyz[i * 3 + 1] + (xyz[i * 3 + 4] - xyz[i * 3 + 1]) * t,
                xyz[i * 3 + 2] + (xyz[i * 3 + 5] - xyz[i * 3 + 2]) * t);
    }

    /**
     * 沿路径画一条长条：只画点序号比例在 [from, to] 之间的那一段；半宽两端收细（taper = 两端各多少比例收到 0，0 不收）；
     * 局部坐标 x 用「沿路径走过的格数」（流动光束、等离子要它），颜色整条一样。
     */
    public static void drawPath(VfxDraw draw, double[] xyz, int n, float from, float to, float half, float taper,
                                int argb, int mode, int param) {
        if (n < 2 || to <= from) {
            return;
        }
        n = Math.min(n, MAX_POINTS);
        int count = 0;
        double walked = 0D;
        double px = 0, py = 0, pz = 0;
        for (int i = 0; i < n; i++) {
            float f = i / (float) (n - 1);
            double x = xyz[i * 3], y = xyz[i * 3 + 1], z = xyz[i * 3 + 2];
            if (i > 0) {
                walked += Math.sqrt((x - px) * (x - px) + (y - py) * (y - py) + (z - pz) * (z - pz));
            }
            px = x;
            py = y;
            pz = z;
            if (f < from || f > to) {
                continue;
            }
            TMP_XYZ[count * 3] = x;
            TMP_XYZ[count * 3 + 1] = y;
            TMP_XYZ[count * 3 + 2] = z;
            float w = 1F;
            if (taper > 0F) {
                w = Math.min(1F, Math.min(f, 1F - f) / taper);
                w = 0.25F + 0.75F * w;
            }
            TMP_HALF[count] = half * w;
            TMP_ARGB[count] = argb;
            TMP_U[count] = (float) walked;
            count++;
        }
        draw.ribbon(TMP_XYZ, count, TMP_HALF, TMP_ARGB, TMP_U, mode, param);
    }
}
