package com.flameforge.essentialcraft5.client.vfx.resolve;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 从地里冲上天的柱子（天灾之心）：
 * <ul>
 *   <li>{@code FX_BACKLASH} 灾柱反噬炸开（接的是灾柱反噬样片的炸开那一段）：顿帧两刻 → 屏幕短闪、大爆点，粗等离子火柱冲天、
 *       三道电弧缠着火柱往上绕，往外推淡的冲击波 + 热浪扭曲，地面裂开、裂纹慢慢冷下去，火花四溅、浓烟、余辉；</li>
 *   <li>{@code FX_PILLAR_RISE} 灾柱破土（开场、重立）：相色的细光柱从地缝里猛地射到 size 格高，地面裂开发光、碎石火星顺着柱子往上冲，
 *       光柱收细后留一会儿余辉（灾柱实体本身照常从地里升起来）。</li>
 * </ul>
 */
public final class EruptionFx extends ResolveFx {

    private static final int HELIX_POINTS = 28;

    private final boolean rise;
    private final double radius;
    private final double height;
    private final double[] helix = new double[HELIX_POINTS * 3];
    private final int burstSeed = random.nextInt(1024);
    private final long crackSeed = random.nextLong();

    EruptionFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level, packet.style() == S2CBossWarningPacket.FX_PILLAR_RISE ? 40 : 34, packet.style() == S2CBossWarningPacket.FX_PILLAR_RISE ? 1 : 2);
        this.rise = packet.style() == S2CBossWarningPacket.FX_PILLAR_RISE;
        this.radius = rise ? 2.2D : Math.max(1D, packet.size());
        this.height = rise ? Math.max(4D, packet.size()) : Math.max(2D, packet.size2());
    }

    @Override
    protected void start() {
        if (rise) {
            // 服务端另外发了轻震（SHAKE），这里只按距离补一下近处的
            shake(origin, 4D, 0.3F, 6);
        } else {
            shake(origin, radius + 2D, 0.7F, 10);
            punch(origin, radius + 4D, 1F, tint.glow(), 0.6F);
            flashNear(origin, radius + 10D, VfxPalette.EMBER, 0.18F, 14);
        }
    }

    @Override
    protected void release() {
        if (rise) {
            VfxPlasma.sparks(origin.add(0D, 0.3D, 0D), new Vec3(0D, 1D, 0D), 0.18D, 70F, 0.6D, 1.5D, 14, 30, tint);
            VfxPlasma.sparks(origin.add(0D, 0.2D, 0D), new Vec3(0D, 0.4D, 0D), 1D, 24F, 0.2D, 0.5D, 10, 18, tint);
            smoke(10F, 0.6F);
            VfxPlasma.embers(origin, radius, 30F, tint);
            return;
        }
        VfxPlasma.sparks(origin.add(0D, 0.3D, 0D), new Vec3(0D, 1D, 0D), 0.55D, 90F, 0.45D, 1.15D, 14, 32, tint);
        VfxPlasma.sparks(origin.add(0D, 0.4D, 0D), Vec3.ZERO, 0D, 40F, 0.3D, 0.7D, 10, 22, tint);
        VfxPlasma.embers(origin, radius, 50F, tint);
        smoke(14F, 0.7F);
    }

    @Override
    protected void tickFx(int t) {
        if (rise && t > hold && t < 26 && (t & 1) == 0) {
            // 顺着光柱往上冲的火星
            VfxPlasma.sparks(origin.add(0D, 0.5D, 0D), new Vec3(0D, 1D, 0D), 0.12D, 4F, 0.8D, 1.4D, 10, 18, tint);
        }
    }

    private void smoke(float designed, float alpha) {
        int n = Vfx.count(designed, origin);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = radius * (0.3D + random.nextDouble() * 0.7D);
            VfxParticles.spawn(origin.x + Math.cos(a) * r, origin.y + 0.4D, origin.z + Math.sin(a) * r, Math.cos(a) * 0.05D,
                    0.04D + random.nextDouble() * 0.04D, Math.sin(a) * 0.05D, 40 + random.nextInt(20), 0.9F, 2.2F, Vfx.argb(VfxPalette.SMOKE, alpha),
                    Vfx.argb(VfxPalette.SMOKE, 0F), VfxMode.SMOKE, random.nextInt(500), 0.96F, -0.001F, 0F, 0.01F);
        }
    }

    @Override
    protected void renderFx(VfxDraw draw, float t, float alpha) {
        float tt = after(t);
        if (holding(t)) {
            // 顿帧：柱脚一小团白热，火柱还没起来，一颗定住的星芒（不铺满招式范围）
            draw.flat(origin, (float) Math.min(radius * 0.5D, 1.6D), 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.6F));
            Vec3 c = draw.towardCamera(origin.add(0D, 1.2D, 0D), 1D);
            draw.billboard(c, rise ? 1.4F : 3.2F, 0F, VfxMode.FLARE, 1000, Vfx.argb(tint.core(), 1F));
            draw.billboard(c, rise ? 2.2F : 5F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.6F));
        }
        if (rise) {
            renderRise(draw, tt);
        } else {
            renderBacklash(draw, tt);
        }
    }

    private void renderBacklash(VfxDraw draw, float t) {
        VfxPlasma.burst(draw, draw.towardCamera(origin.add(0D, 1.4D, 0D), 1.4D), 4.6F, t, 11F, burstSeed, tint);
        // 火柱：先猛地冲起，再收细散掉
        float col = Vfx.pulse(t / 18F, 0.08F);
        Vec3 bottom = origin.add(0D, 0.1D, 0D);
        // 灾柱 4 格高时火柱冲到 15 格（样片的高度），柱子高矮不同按比例
        Vec3 columnTop = origin.add(0D, height + height * 2.75D * Vfx.easeOutExpo(t / 4F), 0D);
        if (col > 0F) {
            float w = (float) radius * col;
            if (tier() >= 2) {
                draw.strip(bottom, columnTop, w * 0.95F, 0.6F * col, Vfx.argb(tint.outer(), 0.22F * col), Vfx.argb(tint.outer(), 0F), VfxMode.STRIP, 30, 0F, 1F);
            }
            draw.strip(bottom, columnTop, w * 0.55F, 0.4F * col, Vfx.argb(VfxPalette.EMBER, 0.6F * col), Vfx.argb(tint.glow(), 0F), VfxMode.BEAM, 11, 0F, 6F);
            draw.strip(bottom, columnTop, w * 0.24F, 0.1F, Vfx.argb(tint.core(), col), Vfx.argb(VfxPalette.EMBER, 0F), VfxMode.PLASMA, 7, 0F, 6F);
            draw.strip(bottom, columnTop, w * 0.08F, 0.03F * col, Vfx.argb(tint.core(), col * col), Vfx.argb(tint.core(), 0F), VfxMode.STRIP, 650, 0F, 1F);
            if (tier() >= 1) {
                helixArcs(draw, t, col, columnTop.y);
            }
        }
        VfxPlasma.wave(draw, origin, (float) radius * 1.4F, t, 7F, 15F, 1F, tint);
        float scorch = 1F - Mth.clamp(t / (life - hold), 0F, 1F);
        VfxPlasma.groundCracks(draw, crackSeed, origin, 0.5F, (float) radius * 1.1F, 10, scorch, Math.min(1F, scorch * 2F + 0.2F), tint);
    }

    /** 三道电弧缠着火柱往上绕（越往上圈越小） */
    private void helixArcs(VfxDraw draw, float t, float col, double topY) {
        double h = (topY - origin.y - 0.2D) * 0.85D;
        for (int k = 0; k < 3; k++) {
            RandomSource jitter = RandomSource.create(seed + k * 131L);
            double phase = k * Mth.TWO_PI / 3D + t * 0.35D;
            for (int i = 0; i < HELIX_POINTS; i++) {
                double f = i / (double) (HELIX_POINTS - 1);
                double ang = phase + f * Mth.TWO_PI * 1.7D;
                double rr = radius * 0.32D * col * (1D - 0.55D * f) + 0.15D;
                helix[i * 3] = origin.x + Math.cos(ang) * rr + (jitter.nextDouble() - 0.5D) * 0.12D;
                helix[i * 3 + 1] = origin.y + 0.2D + h * f;
                helix[i * 3 + 2] = origin.z + Math.sin(ang) * rr + (jitter.nextDouble() - 0.5D) * 0.12D;
            }
            VfxPlasma.arc(draw, helix, HELIX_POINTS, 0.05F, col * col, seed + k, tint);
        }
    }

    private void renderRise(VfxDraw draw, float t) {
        VfxPlasma.burst(draw, draw.towardCamera(origin.add(0D, 0.8D, 0D), 1D), 3.2F, t, 9F, burstSeed, tint);
        // 光柱：3 刻射到顶，之后慢慢收细
        float col = Vfx.pulse((t + 0.5F) / 34F, 0.06F);
        if (col > 0F) {
            Vec3 bottom = origin.add(0D, 0.05D, 0D);
            Vec3 top = origin.add(0D, height * Vfx.easeOutExpo(t / 3F) + 0.5D, 0D);
            float w = 0.85F + 0.5F * col;
            VfxPlasma.beam(draw, bottom, top, w * col, col, true, seed, tint);
            // 柱顶射出去的那一下（前 6 刻）
            float tip = 1F - Mth.clamp(t / 6F, 0F, 1F);
            if (tip > 0F) {
                draw.billboard(draw.towardCamera(top, 0.5D), 1.2F * tip + 0.3F, t * 0.2F, VfxMode.FLARE, 900, Vfx.argb(tint.core(), tip));
            }
        }
        VfxPlasma.wave(draw, origin, (float) radius * 1.6F, t, 6F, 14F, 0.9F, tint);
        float crack = 1F - Mth.clamp(t / (life - hold), 0F, 1F);
        VfxPlasma.groundCracks(draw, crackSeed, origin, 0.4F, (float) radius * 1.3F, 7, crack, Math.min(1F, crack * 2F + 0.2F), tint);
    }

    @Override
    public AABB bounds() {
        return new AABB(origin, origin).inflate(radius * 1.8D + 1D, 2D, radius * 1.8D + 1D).expandTowards(0D, (rise ? height * 1.2D : height * 3.9D) + 2D, 0D);
    }
}
