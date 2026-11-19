package com.flameforge.essentialcraft5.client.vfx.fx;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;

/**
 * 反重力板飞行的尾迹（等离子画风样片）。板子画在脚下（{@code ComputerClient.renderBoard}），尾迹从板尾两个喷口拖出来；
 * 每刻记一次喷口位置（最多 {@value #HISTORY} 个），画的时候第一个点用这一帧插值出来的位置，尾巴不会一顿一顿。
 * {@code flying} 返回假以后不再记新点，旧点从后往前收掉，收完特效结束。
 * <ul>
 *   <li>两道等离子尾焰：外晕、辉光、等离子芯、白热细芯四层，几团推进脉冲顺着尾焰往后流，两道之间来回交织一条细电弧；</li>
 *   <li>喷口：转着的爆点 + 星芒 + 光晕在闪，两个喷口之间时不时跳一道小电弧，喷口后一团热浪；</li>
 *   <li>往后甩火花，沿途留下慢慢升起的余辉；板子底下一圈品红的悬浮光晕。</li>
 * </ul>
 */
public final class BoardTrailFx implements VfxEffect {

    private static final int HISTORY = 40;
    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
    private static final int PULSES = 3;
    private static final int WEAVE_POINTS = 24;

    private final LivingEntity rider;
    private final BooleanSupplier flying;
    private final double[] left = new double[HISTORY * 3];
    private final double[] right = new double[HISTORY * 3];
    private final double[] xyz = new double[(HISTORY + 1) * 3];
    private final float[] half = new float[HISTORY + 1];
    private final int[] argb = new int[HISTORY + 1];
    private final float[] u = new float[HISTORY + 1];
    private final double[] bridge = new double[VfxPaths.jaggedPoints(3) * 3];
    private final RandomSource random = RandomSource.create();
    private int bridgeSeed;
    private int recorded;
    private int age;

    public BoardTrailFx(LivingEntity rider, BooleanSupplier flying) {
        this.rider = rider;
        this.flying = flying;
    }

    @Override
    public boolean tick() {
        age++;
        boolean on = flying.getAsBoolean() && rider.isAlive();
        if (age % 2 == 0) {
            bridgeSeed = random.nextInt(100000);
        }
        if (on) {
            push(left, nozzle(1F, -1));
            push(right, nozzle(1F, 1));
            recorded = Math.min(HISTORY, recorded + 1);
            particles();
        } else {
            // 不飞了：尾巴从后往前一点点收掉
            recorded = Math.max(0, recorded - 2);
        }
        return on || recorded > 0;
    }

    private void particles() {
        Vec3 back = forward(1F).scale(-1D);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 p = nozzle(1F, side);
            int n = Vfx.count(4F, p);
            for (int i = 0; i < n; i++) {
                Vec3 v = back.scale(0.18D + random.nextDouble() * 0.25D).add((random.nextDouble() - 0.5D) * 0.14D, -0.03D, (random.nextDouble() - 0.5D) * 0.14D);
                int end = random.nextFloat() < 0.3F ? VfxPalette.ORB_BLUE : VfxPalette.MAGENTA;
                VfxParticles.spawn(p.x, p.y, p.z, v.x, v.y, v.z, 8 + random.nextInt(10), 0.055F, 0.01F, Vfx.argb(VfxPalette.WHITE_HOT, 1F),
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
        float t = age + pt;
        boolean live = flying.getAsBoolean();
        int tier = Vfx.tier();
        for (int side = -1; side <= 1; side += 2) {
            double[] hist = side < 0 ? left : right;
            Vec3 head = nozzle(pt, side);
            int n;
            if (tier >= 2) {
                n = build(hist, head, 1.4F, TINT.outer(), 0.18F, live);
                draw.ribbon(xyz, n, half, argb, u, VfxMode.STRIP, 20);
            }
            n = build(hist, head, 0.75F, TINT.glow(), 0.42F, live);
            draw.ribbon(xyz, n, half, argb, u, VfxMode.STRIP, 70);
            if (tier >= 1) {
                n = build(hist, head, 0.3F, TINT.inner(), 1F, live);
                draw.ribbon(xyz, n, half, argb, u, VfxMode.PLASMA, side < 0 ? 3 : 17);
            }
            n = build(hist, head, 0.085F, TINT.core(), 1F, live);
            draw.ribbon(xyz, n, half, argb, u, VfxMode.STRIP, 650);
            // 推进脉冲：几团亮点顺着尾焰往后流，越往后越淡
            if (tier >= 1 && n >= 4) {
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
                draw.billboard(head.x, head.y, head.z, 0.95F * flick, 0.95F * flick, t * 0.5F * side, VfxMode.BURST, side < 0 ? 5 : 9,
                        Vfx.argb(TINT.inner(), 0.85F), false);
                draw.billboard(head, 0.45F * flick, t * 0.4F, VfxMode.FLARE, 700, Vfx.argb(TINT.core(), 1F));
                draw.billboard(head, 1.0F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.45F));
                draw.distortBillboard(head.add(forward(pt).scale(-0.6D)), 0.8F, 1, 0, 0.7F);
            }
        }
        if (tier >= 1) {
            weave(draw, t);
        }
        if (live) {
            // 两个喷口之间的小电弧：一半时间在跳
            if (tier >= 1 && (bridgeSeed & 1) == 0) {
                RandomSource r = RandomSource.create(bridgeSeed);
                int bn = VfxPaths.jagged(r, nozzle(pt, -1), nozzle(pt, 1), 3, 0.35F, bridge);
                VfxPlasma.arc(draw, bridge, bn, 0.025F, 0.85F, bridgeSeed, TINT);
            }
            // 板子底下的悬浮光晕（比板子大一圈，从板边溢出来）
            Vec3 feet = rider.getPosition(pt).add(0D, -0.15D, 0D);
            float hover = 0.85F + 0.15F * Mth.sin(t * 0.35F);
            draw.flat(feet, 1.9F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.4F * hover));
            draw.flat(feet, 1.5F * hover, 0F, VfxMode.RING, 18, Vfx.argb(TINT.inner(), 0.4F));
        }
    }

    /** 两道尾焰之间来回交织的一条细电弧（顺着尾迹往后流，越往后越淡） */
    private void weave(VfxDraw draw, float t) {
        int m = Math.min(recorded, WEAVE_POINTS);
        if (m < 4) {
            return;
        }
        RandomSource jitter = RandomSource.create(bridgeSeed);
        for (int i = 0; i < m; i++) {
            double s = 0.5D + 0.5D * Math.sin(i * 0.9D - t * 0.5D);
            int k = i * 3;
            xyz[k] = Mth.lerp(s, left[k], right[k]) + (jitter.nextDouble() - 0.5D) * 0.06D;
            xyz[k + 1] = Mth.lerp(s, left[k + 1], right[k + 1]) + Math.sin(i * 1.3D + t * 0.7D) * 0.08D;
            xyz[k + 2] = Mth.lerp(s, left[k + 2], right[k + 2]) + (jitter.nextDouble() - 0.5D) * 0.06D;
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

    /** 把「当前喷口 + 记下的历史」排成一串点，半宽和不透明度往尾巴收到 0，返回点数 */
    private int build(double[] hist, Vec3 head, float width, int rgb, float alpha, boolean live) {
        int n = 0;
        if (live) {
            xyz[0] = head.x;
            xyz[1] = head.y;
            xyz[2] = head.z;
            n = 1;
        }
        for (int i = 0; i < recorded; i++) {
            xyz[n * 3] = hist[i * 3];
            xyz[n * 3 + 1] = hist[i * 3 + 1];
            xyz[n * 3 + 2] = hist[i * 3 + 2];
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

    private static void push(double[] hist, Vec3 p) {
        System.arraycopy(hist, 0, hist, 3, hist.length - 3);
        hist[0] = p.x;
        hist[1] = p.y;
        hist[2] = p.z;
    }

    private Vec3 forward(float pt) {
        float yaw = Mth.rotLerp(pt, rider.yBodyRotO, rider.yBodyRot) * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(yaw), 0D, Math.cos(yaw));
    }

    /** 喷口：板尾往后 0.6 格，左右各 0.28 格（side = −1 左、1 右），比脚底低一点 */
    private Vec3 nozzle(float pt, int side) {
        Vec3 f = forward(pt);
        Vec3 s = new Vec3(-f.z, 0D, f.x);
        return rider.getPosition(pt).add(f.scale(-0.6D)).add(s.scale(0.28D * side)).add(0D, -0.05D, 0D);
    }

    @Override
    public AABB bounds() {
        return rider.getBoundingBox().inflate(24D);
    }
}
