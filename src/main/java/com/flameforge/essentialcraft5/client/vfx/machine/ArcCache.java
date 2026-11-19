package com.flameforge.essentialcraft5.client.vfx.machine;

import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 常驻电弧的形状缓存：按「哪一台的哪一道 + 第几轮」存一条折线，轮次没变就直接拿，变了才重新生成。
 * 开放寻址的定长表，不分配；表满了挤掉最久没用的那格（几十台机器远远用不满）。
 */
final class ArcCache {

    static final int DEPTH = 3;
    static final int POINTS = (1 << DEPTH) + 1;
    private static final int SIZE = 1024;
    private static final int PROBE = 8;
    private static final long[] KEY = new long[SIZE];
    private static final long[] EPOCH = new long[SIZE];
    private static final long[] USED = new long[SIZE];
    private static final boolean[] FILLED = new boolean[SIZE];
    private static final double[][] PATH = new double[SIZE][];
    private static final RandomSource RANDOM = RandomSource.create();

    private ArcCache() {
    }

    /** 拿 key 这一道在 epoch 这一轮的折线（POINTS 个点）；没有就从 a 到 b 生成一条存起来 */
    static double[] path(long key, long epoch, double ax, double ay, double az, double bx, double by, double bz, float roughness) {
        int home = (int) (mix(key) & (SIZE - 1));
        int victim = home;
        long oldest = Long.MAX_VALUE;
        long now = MachineVfx.ticks();
        for (int p = 0; p < PROBE; p++) {
            int i = (home + p) & (SIZE - 1);
            if (FILLED[i] && KEY[i] == key) {
                USED[i] = now;
                if (EPOCH[i] != epoch) {
                    fill(i, key, epoch, ax, ay, az, bx, by, bz, roughness);
                }
                return PATH[i];
            }
            long used = FILLED[i] ? USED[i] : Long.MIN_VALUE;
            if (used < oldest) {
                oldest = used;
                victim = i;
            }
        }
        FILLED[victim] = true;
        KEY[victim] = key;
        USED[victim] = now;
        fill(victim, key, epoch, ax, ay, az, bx, by, bz, roughness);
        return PATH[victim];
    }

    private static void fill(int i, long key, long epoch, double ax, double ay, double az, double bx, double by, double bz, float roughness) {
        if (PATH[i] == null) {
            PATH[i] = new double[POINTS * 3];
        }
        EPOCH[i] = epoch;
        RANDOM.setSeed(key * 0x9E3779B97F4A7C15L + epoch);
        VfxPaths.jagged(RANDOM, new Vec3(ax, ay, az), new Vec3(bx, by, bz), DEPTH, roughness, PATH[i]);
    }

    static void clear() {
        java.util.Arrays.fill(FILLED, false);
    }

    /** 同一台机器、同一道电弧的 key */
    static long key(long pos, int index) {
        return pos * 31L + index;
    }

    /** 这一台在第几轮：period 刻换一次形状，按位置错开相位，几十台不会同一刻一起换 */
    static long epoch(long pos, int period) {
        return (MachineVfx.ticks() + (mix(pos) & 1023L)) / period;
    }

    static long mix(long x) {
        x ^= x >>> 33;
        x *= 0xFF51AFD7ED558CCDL;
        x ^= x >>> 33;
        return x;
    }
}
