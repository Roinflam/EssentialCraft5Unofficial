package com.flameforge.essentialcraft5.client.vfx.gear;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;

/**
 * 反重力板飞行的尾迹，照第二版定稿样片（{@code fx/BoardTrailFx}）的层次接到真飞行上：两道四层尾焰 + 推进脉冲、两道之间交织的细电弧、
 * 喷口转着的爆点、两喷口间跳的小电弧、板底悬浮光晕、往后甩的火花和余辉。
 * <p>
 * 和样片不同的地方都是为了几十个人同屏：喷口历史是定长环形缓冲（每刻只写一个点，不挪数组）；悬停不动时不记新点、尾巴自己收回去；
 * 按离镜头远近分三档（{@link GearVfx#lod}），远档只画辉光和芯两层、尾巴截短，再远只记点不画；
 * 自己第一人称时只画脚下看得见的一小截，不画交织电弧和扭曲。
 */
final class BoardJetFx implements VfxEffect {

    private static final int HISTORY = 40;
    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
    private static final int PULSES = 3;
    private static final int WEAVE_POINTS = 24;
    /** 喷口挪动不到这么远就不记新点（悬停时尾巴收回去，不在原地叠成一团） */
    private static final double MIN_STEP_SQ = 0.04D * 0.04D;

    private final GearAnchor rider;
    private final BooleanSupplier flying;
    private final double[] left = new double[HISTORY * 3];
    private final double[] right = new double[HISTORY * 3];
    private final double[] xyz = new double[(HISTORY + 1) * 3];
    private final float[] half = new float[HISTORY + 1];
    private final int[] argb = new int[HISTORY + 1];
    private final float[] u = new float[HISTORY + 1];
    private final double[] bridge = new double[VfxPaths.jaggedPoints(3) * 3];
    private final RandomSource random = RandomSource.create();
    private final RandomSource shape = RandomSource.create();
    private int head;
    private int recorded;
    private int bridgeSeed;
    private int age;
    private boolean live;

    BoardJetFx(GearAnchor rider, BooleanSupplier flying) {
        this.rider = rider;
        this.flying = flying;
    }

    @Override
    public boolean tick() {
        age++;
        live = flying.getAsBoolean() && rider.alive();
        if (age % 2 == 0) {
            bridgeSeed = random.nextInt(100000);
        }
        if (live) {
            Vec3 l = nozzle(1F, -1);
            Vec3 r = nozzle(1F, 1);
            int k = head * 3;
            double dx = l.x - left[k], dy = l.y - left[k + 1], dz = l.z - left[k + 2];
            if (recorded == 0 || dx * dx + dy * dy + dz * dz > MIN_STEP_SQ) {
                head = (head + 1) % HISTORY;
                put(left, l);
                put(right, r);
                recorded = Math.min(HISTORY, recorded + 1);
            } else {
                recorded = Math.max(1, recorded - 1);
            }
            if (GearVfx.lod(l) <= 1) {
                particles(l, r);
            }
        } else {
            recorded = Math.max(0, recorded - 2);
        }
        return live || recorded > 0;
    }

    private void put(double[] hist, Vec3 p) {
        int k = head * 3;
        hist[k] = p.x;
        hist[k + 1] = p.y;
        hist[k + 2] = p.z;
    }

    private void particles(Vec3 l, Vec3 r) {
        Vec3 back = forward(1F).scale(-1D);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 p = side < 0 ? l : r;
            int n = Vfx.count(4F, p);
            for (int i = 0; i < n; i++) {
                double vx = back.x * (0.18D + random.nextDouble() * 0.25D) + (random.nextDouble() - 0.5D) * 0.14D;
                double vz = back.z * (0.18D + random.nextDouble() * 0.25D) + (random.nextDouble() - 0.5D) * 0.14D;
                int end = random.nextFloat() < 0.3F ? VfxPalette.ORB_BLUE : VfxPalette.MAGENTA;
                VfxParticles.spawn(p.x, p.y, p.z, vx, -0.03D, vz, 8 + random.nextInt(10), 0.055F, 0.01F, Vfx.argb(VfxPalette.WHITE_HOT, 1F),
                        Vfx.argb(end, 0F), VfxMode.SPARK, 0, 0.9F, 0.035F, 1.8F, 0F);
            }
            if (age % 2 == 0) {
                VfxPlasma.embers(p, 0.15D, 1F, TINT);
            }
        }
    }

    @Override
    public void render(VfxDraw draw) {
        float pt = draw.partialTick();
        Vec3 feet = rider.pos(pt);
        int lod = GearVfx.lod(feet);
        if (lod > 2) {
            return;
        }
        long start = GearVfx.beginTiming();
        float t = age + pt;
        boolean firstPerson = rider.firstPerson();
        int tier = Vfx.tier();
        // 第一人称只看得见脚下那一截；远档尾巴截短
        int keep = firstPerson ? 10 : lod == 2 ? HISTORY / 2 : HISTORY;
        boolean full = lod == 0 && !firstPerson;
        for (int side = -1; side <= 1; side += 2) {
            double[] hist = side < 0 ? left : right;
            Vec3 nozzle = nozzle(pt, side);
            int n;
            if (tier >= 2 && full) {
                n = build(hist, nozzle, keep, 1.4F, TINT.outer(), 0.18F);
                draw.ribbon(xyz, n, half, argb, u, VfxMode.STRIP, 20);
            }
            n = build(hist, nozzle, keep, 0.75F, TINT.glow(), 0.42F);
            draw.ribbon(xyz, n, half, argb, u, VfxMode.STRIP, 70);
            if (tier >= 1 && lod <= 1) {
                n = build(hist, nozzle, keep, 0.3F, TINT.inner(), 1F);
                draw.ribbon(xyz, n, half, argb, u, VfxMode.PLASMA, side < 0 ? 3 : 17);
            }
            n = build(hist, nozzle, keep, 0.085F, TINT.core(), 1F);
            draw.ribbon(xyz, n, half, argb, u, VfxMode.STRIP, 650);
            if (tier >= 1 && full && n >= 4) {
                for (int j = 0; j < PULSES; j++) {
                    float f = (t * 0.045F + j / (float) PULSES + (side < 0 ? 0F : 0.17F)) % 1F;
                    float fade = (1F - f) * (1F - f);
                    Vec3 p = VfxPaths.at(xyz, n, f * 0.85F);
                    draw.billboard(p, 0.18F + 0.14F * fade, 0F, VfxMode.DOT, 700, Vfx.argb(TINT.core(), 0.95F * fade));
                    draw.billboard(p, 0.5F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.4F * fade));
                }
            }
            if (live) {
                float flick = 0.75F + 0.25F * Mth.sin(t * 4.3F + side);
                if (lod <= 1) {
                    draw.billboard(nozzle.x, nozzle.y, nozzle.z, 0.95F * flick, 0.95F * flick, t * 0.5F * side, VfxMode.BURST, side < 0 ? 5 : 9,
                            Vfx.argb(TINT.inner(), 0.85F), false);
                    draw.billboard(nozzle, 0.45F * flick, t * 0.4F, VfxMode.FLARE, 700, Vfx.argb(TINT.core(), 1F));
                }
                draw.billboard(nozzle, 1.0F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.45F));
                if (full) {
                    draw.distortBillboard(nozzle.add(forward(pt).scale(-0.6D)), 0.8F, 1, 0, 0.7F);
                }
            }
        }
        if (tier >= 1 && full) {
            weave(draw, t);
        }
        if (live) {
            if (tier >= 1 && lod == 0 && (bridgeSeed & 1) == 0) {
                shape.setSeed(bridgeSeed);
                int bn = VfxPaths.jagged(shape, nozzle(pt, -1), nozzle(pt, 1), 3, 0.35F, bridge);
                VfxPlasma.arc(draw, bridge, bn, 0.025F, 0.85F, bridgeSeed, TINT);
            }
            Vec3 under = feet.add(0D, -0.15D, 0D);
            float hover = 0.85F + 0.15F * Mth.sin(t * 0.35F);
            draw.flat(under, 1.9F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.4F * hover));
            if (lod <= 1) {
                draw.flat(under, 1.5F * hover, 0F, VfxMode.RING, 18, Vfx.argb(TINT.inner(), 0.4F));
            }
        }
        GearVfx.endTiming(start);
    }

    /** 两道尾焰之间来回交织的一条细电弧（顺着尾迹往后流，越往后越淡） */
    private void weave(VfxDraw draw, float t) {
        int m = Math.min(recorded, WEAVE_POINTS);
        if (m < 4) {
            return;
        }
        shape.setSeed(bridgeSeed);
        for (int i = 0; i < m; i++) {
            int k = slot(i) * 3;
            double s = 0.5D + 0.5D * Math.sin(i * 0.9D - t * 0.5D);
            int o = i * 3;
            xyz[o] = Mth.lerp(s, left[k], right[k]) + (shape.nextDouble() - 0.5D) * 0.06D;
            xyz[o + 1] = Mth.lerp(s, left[k + 1], right[k + 1]) + Math.sin(i * 1.3D + t * 0.7D) * 0.08D;
            xyz[o + 2] = Mth.lerp(s, left[k + 2], right[k + 2]) + (shape.nextDouble() - 0.5D) * 0.06D;
            float f = 1F - i / (float) (m - 1);
            half[i] = 0.12F * (0.3F + 0.7F * f);
            argb[i] = Vfx.argb(TINT.glow(), 0.45F * f);
            u[i] = i * 0.4F;
        }
        draw.ribbon(xyz, m, half, argb, u, VfxMode.STRIP, 70);
        for (int i = 0; i < m; i++) {
            float f = 1F - i / (float) (m - 1);
            half[i] = 0.03F * (0.3F + 0.7F * f);
            argb[i] = Vfx.argb(TINT.core(), f);
        }
        draw.ribbon(xyz, m, half, argb, u, VfxMode.STRIP, 600);
    }

    /** 第 i 新的历史点在环形缓冲里的下标（0 = 最新） */
    private int slot(int i) {
        return Math.floorMod(head - i, HISTORY);
    }

    /** 「这一帧的喷口 + 记下的历史」排成一串点，半宽和不透明度往尾巴收到 0，返回点数 */
    private int build(double[] hist, Vec3 nozzle, int keep, float width, int rgb, float alpha) {
        int n = 0;
        if (live) {
            xyz[0] = nozzle.x;
            xyz[1] = nozzle.y;
            xyz[2] = nozzle.z;
            n = 1;
        }
        int m = Math.min(recorded, keep);
        for (int i = 0; i < m; i++) {
            int k = slot(i) * 3;
            xyz[n * 3] = hist[k];
            xyz[n * 3 + 1] = hist[k + 1];
            xyz[n * 3 + 2] = hist[k + 2];
            n++;
        }
        double walked = 0D;
        for (int i = 0; i < n; i++) {
            float f = n <= 1 ? 0F : i / (float) (n - 1);
            float k = 1F - f;
            half[i] = width * (0.15F + 0.85F * k);
            argb[i] = Vfx.argb(rgb, alpha * k * k);
            if (i > 0) {
                double dx = xyz[i * 3] - xyz[i * 3 - 3], dy = xyz[i * 3 + 1] - xyz[i * 3 - 2], dz = xyz[i * 3 + 2] - xyz[i * 3 - 1];
                walked += Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            u[i] = (float) walked;
        }
        return n;
    }

    private Vec3 forward(float pt) {
        float yaw = rider.bodyYaw(pt) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0D, Mth.cos(yaw));
    }

    /** 喷口：板尾往后 0.6 格，左右各 0.28 格（side = −1 左、1 右），比脚底低一点 */
    private Vec3 nozzle(float pt, int side) {
        Vec3 f = forward(pt);
        return rider.pos(pt).add(-f.x * 0.6D - f.z * 0.28D * side, -0.05D, -f.z * 0.6D + f.x * 0.28D * side);
    }

    @Override
    public AABB bounds() {
        return rider.box().inflate(24D);
    }
}
