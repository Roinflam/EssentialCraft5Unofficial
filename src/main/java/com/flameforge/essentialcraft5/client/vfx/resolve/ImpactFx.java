package com.flameforge.essentialcraft5.client.vfx.resolve;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxCracks;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.client.vfx.warn.BossWarningClient;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 地面上「砸下来」的一类结算：同一套层次（顿帧的白热一帧 → 爆点 + 往外推、越远越淡的冲击波 + 地面裂纹 + 火花 / 余辉 / 烟，
 * 电系的再加地面电流），按画面编号调大小、节奏，再加各自的一样招牌。地上不画标出招式范围的圆盘、圆环（范围由预警圈负责）：
 * <ul>
 *   <li>{@code FX_SLAM} 矩阵之影坠身：一道从天上砸到落点的光痕；</li>
 *   <li>{@code FX_SHOCK} 收束：胸口一圈往外撑开的音爆环 + 空气扭曲；</li>
 *   <li>{@code FX_DISORDER} 失序之柱：品红等离子柱冲起；</li>
 *   <li>{@code FX_GOLEM_SLAM} 聚能魔像：MRU 靛紫的电弧沿地面往外劈，再往上窜几道；</li>
 *   <li>{@code FX_SUPPRESS} 镇压：地面烧裂、熔岩从裂缝里透出来慢慢冷掉，熔滴溅起、浓烟；</li>
 *   <li>{@code FX_RUBBLE} 落石：小一号，带石灰色的尘土；</li>
 *   <li>{@code FX_STRIKE} 暗袭：影刃从出手点扑下来的残痕 + 交叉的斩痕；</li>
 *   <li>{@code FX_NOON} 正午：天上压下来一根粗日光柱，地面晒出焦痕、余烬飘起；</li>
 *   <li>{@code FX_PULSE} 崩解脉冲：一圈冲击波（崩解时反复放，不闪屏）。</li>
 * </ul>
 */
public final class ImpactFx extends ResolveFx {

    private final int style;
    private final float radius;
    /** 爆点放射光线的形状（整段不变，不然每两刻换一次会跳） */
    private final int burstSeed = random.nextInt(1024);
    /** 爆点的大小（格）、离地多高 */
    private final float burst;
    private final float lift;
    /** 冲击波半径 = 半径 × shockMul，reach 刻扩满，shockLife 刻散完 */
    private final float shockMul;
    private final float shockReach;
    private final float shockLife;
    /** 地面电流几道（0 = 不画；只给电系：矩阵、魔像、崩解） */
    private final int crawl;
    /** 地面裂纹的初始热度（0 = 不画）和道数 */
    private final float scorch;
    private final int cracks;
    /** 裂纹的形状种子（整段不变） */
    private final long crackSeed = random.nextLong();
    /** 预警交过来的裂纹网：有就炸亮这张，不另画一批 */
    @Nullable
    private final VfxCracks inherited;
    private final double[] path = new double[VfxPaths.jaggedPoints(4) * 3];

    ImpactFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level, lifeOf(packet.style()), holdOf(packet.style()));
        this.style = packet.style();
        this.radius = Math.max(0.5F, packet.size());
        switch (style) {
            case S2CBossWarningPacket.FX_SLAM -> {
                burst = 3.2F;
                lift = 1F;
                shockMul = 1.5F;
                shockReach = 6F;
                shockLife = 14F;
                crawl = 8;
                scorch = 0.8F;
                cracks = 9;
            }
            case S2CBossWarningPacket.FX_SHOCK -> {
                burst = 3F;
                lift = 1.3F;
                shockMul = 1.15F;
                shockReach = 5F;
                shockLife = 13F;
                crawl = 6;
                scorch = 0F;
                cracks = 0;
            }
            case S2CBossWarningPacket.FX_DISORDER -> {
                burst = 2.4F;
                lift = 0.6F;
                shockMul = 1.35F;
                shockReach = 5F;
                shockLife = 12F;
                crawl = 6;
                scorch = 0.5F;
                cracks = 6;
            }
            case S2CBossWarningPacket.FX_GOLEM_SLAM -> {
                burst = 3.4F;
                lift = 0.6F;
                shockMul = 1.25F;
                shockReach = 6F;
                shockLife = 14F;
                crawl = 14;
                scorch = 0.4F;
                cracks = 8;
            }
            case S2CBossWarningPacket.FX_SUPPRESS -> {
                burst = 3.6F;
                lift = 0.7F;
                shockMul = 1.3F;
                shockReach = 7F;
                shockLife = 15F;
                crawl = 0;
                scorch = 1F;
                cracks = 14;
            }
            case S2CBossWarningPacket.FX_RUBBLE -> {
                burst = 1.5F;
                lift = 0.3F;
                shockMul = 1.7F;
                shockReach = 4F;
                shockLife = 10F;
                crawl = 0;
                scorch = 0.35F;
                cracks = 4;
            }
            case S2CBossWarningPacket.FX_STRIKE -> {
                burst = 1.9F;
                lift = 1F;
                shockMul = 1.4F;
                shockReach = 4F;
                shockLife = 10F;
                crawl = 0;
                scorch = 0.3F;
                cracks = 0;
            }
            case S2CBossWarningPacket.FX_NOON -> {
                burst = 4.5F;
                lift = 2.5F;
                shockMul = 1.05F;
                shockReach = 8F;
                shockLife = 18F;
                crawl = 0;
                scorch = 0.6F;
                cracks = 9;
            }
            default -> {
                // FX_PULSE
                burst = 2.6F;
                lift = 2F;
                shockMul = 1F;
                shockReach = 7F;
                shockLife = 14F;
                crawl = 10;
                scorch = 0F;
                cracks = 0;
            }
        }
        // 自己画裂纹的招才接预警的网；不画裂纹的（冲击、暗袭、脉冲）让预警那张自己白热一下退掉
        this.inherited = cracks > 0 && scorch > 0F ? BossWarningClient.claimCracks(packet.owner(), new Vec3(packet.x(), packet.y(), packet.z())) : null;
    }

    private static int lifeOf(int style) {
        return switch (style) {
            case S2CBossWarningPacket.FX_SUPPRESS, S2CBossWarningPacket.FX_NOON -> 36;
            case S2CBossWarningPacket.FX_SLAM, S2CBossWarningPacket.FX_GOLEM_SLAM, S2CBossWarningPacket.FX_DISORDER -> 30;
            case S2CBossWarningPacket.FX_RUBBLE, S2CBossWarningPacket.FX_STRIKE -> 22;
            default -> 26;
        };
    }

    private static int holdOf(int style) {
        return switch (style) {
            case S2CBossWarningPacket.FX_SLAM, S2CBossWarningPacket.FX_SHOCK, S2CBossWarningPacket.FX_GOLEM_SLAM, S2CBossWarningPacket.FX_SUPPRESS,
                 S2CBossWarningPacket.FX_NOON -> 2;
            default -> 1;
        };
    }

    // ------------------------------------------------------------------ 时间轴

    @Override
    protected void start() {
        switch (style) {
            case S2CBossWarningPacket.FX_SLAM -> {
                shake(origin, radius + 2D, 0.55F, 8);
                punch(origin, radius + 2D, 0.85F);
            }
            case S2CBossWarningPacket.FX_SHOCK -> {
                shake(origin, radius + 2D, 0.45F, 7);
            }
            case S2CBossWarningPacket.FX_DISORDER -> shake(origin, radius + 1D, 0.3F, 6);
            case S2CBossWarningPacket.FX_GOLEM_SLAM -> {
                shake(origin, radius + 2D, 0.55F, 8);
                flashNear(origin, radius + 6D, VfxPalette.ORB_BLUE, 0.12F, 10);
            }
            case S2CBossWarningPacket.FX_SUPPRESS -> {
                shake(origin, radius + 2D, 0.65F, 9);
                punch(origin, radius + 2D, 0.9F);
                flashNear(origin, radius + 6D, VfxPalette.EMBER, 0.14F, 12);
            }
            case S2CBossWarningPacket.FX_RUBBLE -> shake(origin, 3D, 0.18F, 4);
            case S2CBossWarningPacket.FX_STRIKE -> shake(origin, 3D, 0.3F, 5);
            case S2CBossWarningPacket.FX_NOON -> {
                shake(origin, radius, 0.4F, 8);
                punch(origin, radius, 0.7F);
                flashNear(origin, radius + 8D, VfxPalette.GOLD, 0.16F, 14);
            }
            default -> shake(origin, radius, 0.3F, 5);
        }
        if (hold == 0) {
            emit();
        }
    }

    @Override
    protected void release() {
        emit();
    }

    /** 炸开那一刻的粒子 */
    private void emit() {
        Vec3 c = origin.add(0D, 0.25D, 0D);
        float r = radius;
        switch (style) {
            case S2CBossWarningPacket.FX_SUPPRESS -> {
                // 熔滴：重、往外溅，落回地上
                VfxPlasma.sparks(c, new Vec3(0D, 1D, 0D), 0.9D, 70F, 0.35D, 0.9D, 16, 30, tint);
                lava(c, 40F);
                smoke(c, r, 12F, VfxPalette.SMOKE, 0.75F);
                VfxPlasma.embers(origin, r, 40F, tint);
            }
            case S2CBossWarningPacket.FX_RUBBLE -> {
                VfxPlasma.sparks(c, new Vec3(0D, 1D, 0D), 0.8D, 14F, 0.2D, 0.5D, 8, 16, tint);
                // 一招落好几块，每块的尘土要淡、要少，不然叠成一大团白雾把落点盖住
                smoke(c, r, 4F, packet.color2() == 0 ? VfxPalette.SMOKE : packet.color2(), 0.28F);
            }
            case S2CBossWarningPacket.FX_NOON -> {
                VfxPlasma.sparks(origin.add(0D, 0.4D, 0D), new Vec3(0D, 1D, 0D), 0.5D, 50F, 0.3D, 0.9D, 14, 30, tint);
                VfxPlasma.embers(origin, r, 70F, tint);
            }
            case S2CBossWarningPacket.FX_GOLEM_SLAM -> {
                VfxPlasma.sparks(c, new Vec3(0D, 1D, 0D), 0.75D, 60F, 0.35D, 1D, 12, 26, tint);
                VfxPlasma.sparks(c, Vec3.ZERO, 0D, 30F, 0.25D, 0.6D, 8, 16, tint);
                VfxPlasma.embers(origin, r, 24F, tint);
                smoke(c, r, 6F, VfxPalette.SMOKE, 0.5F);
            }
            case S2CBossWarningPacket.FX_SHOCK -> {
                Vec3 chest = origin.add(0D, lift, 0D);
                VfxPlasma.sparks(chest, Vec3.ZERO, 0D, 45F, 0.4D, 0.9D, 8, 18, tint);
                VfxPlasma.embers(origin, r, 20F, tint);
            }
            case S2CBossWarningPacket.FX_DISORDER -> {
                VfxPlasma.sparks(c, new Vec3(0D, 1D, 0D), 0.3D, 45F, 0.5D, 1.2D, 12, 26, tint);
                VfxPlasma.embers(origin, r, 26F, tint);
            }
            case S2CBossWarningPacket.FX_STRIKE -> {
                Vec3 back = dir.lengthSqr() < 1.0E-6D ? new Vec3(0D, 1D, 0D) : dir.normalize();
                VfxPlasma.sparks(origin.add(0D, 0.6D, 0D), back.scale(-1D).add(0D, 0.8D, 0D), 0.7D, 26F, 0.25D, 0.7D, 8, 16, tint);
            }
            case S2CBossWarningPacket.FX_SLAM -> {
                VfxPlasma.sparks(c, new Vec3(0D, 1D, 0D), 0.8D, 50F, 0.3D, 0.9D, 12, 24, tint);
                VfxPlasma.sparks(c, Vec3.ZERO, 0D, 24F, 0.25D, 0.6D, 8, 16, tint);
                smoke(c, r, 10F, VfxPalette.SMOKE, 0.7F);
                VfxPlasma.embers(origin, r, 30F, tint);
            }
            default -> {
                VfxPlasma.sparks(origin.add(0D, lift, 0D), Vec3.ZERO, 0D, 24F, 0.3D, 0.7D, 8, 16, tint);
                VfxPlasma.embers(origin, Math.min(r, 6F), 20F, tint);
            }
        }
    }

    @Override
    protected void tickFx(int t) {
        if (style == S2CBossWarningPacket.FX_SUPPRESS && t > hold && t < 24 && (t & 1) == 0) {
            // 熔岩地面还在冒泡
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = Math.sqrt(random.nextDouble()) * radius;
            VfxPlasma.sparks(origin.add(Math.cos(a) * r, 0.1D, Math.sin(a) * r), new Vec3(0D, 1D, 0D), 0.4D, 2F, 0.12D, 0.3D, 8, 14, tint);
        }
        if (style == S2CBossWarningPacket.FX_NOON && t > hold && t < 26 && (t % 3) == 0) {
            VfxPlasma.embers(origin, radius, 6F, tint);
        }
    }

    // ------------------------------------------------------------------ 画

    @Override
    protected void renderFx(VfxDraw draw, float t, float alpha) {
        float tt = after(t);
        Vec3 center = origin.add(0D, lift, 0D);
        if (holding(t)) {
            impactFrame(draw, center);
        }
        // 招牌
        switch (style) {
            case S2CBossWarningPacket.FX_SLAM -> fallStreak(draw, t);
            case S2CBossWarningPacket.FX_SHOCK -> sonicRing(draw, center, tt);
            case S2CBossWarningPacket.FX_DISORDER -> column(draw, tt, 9F, 0.9F, 16F);
            case S2CBossWarningPacket.FX_GOLEM_SLAM -> groundArcs(draw, tt);
            case S2CBossWarningPacket.FX_STRIKE -> slash(draw, t);
            case S2CBossWarningPacket.FX_NOON -> sunColumn(draw, t, tt);
            default -> {
            }
        }
        // 共同的层次
        VfxPlasma.burst(draw, draw.towardCamera(center, 1D), burst, tt, 11F, burstSeed, tint);
        VfxPlasma.wave(draw, origin, radius * shockMul, tt, shockReach, shockLife, 1F, tint);
        if (crawl > 0) {
            VfxPlasma.groundCrawl(draw, RandomSource.create(seed), origin, radius * 1.1F, crawl, 0.04F, 1F - Mth.clamp(tt / 13F, 0F, 1F), tint);
        }
        if (cracks > 0 && scorch > 0F) {
            // 顿帧时裂纹白热，之后照招式的热度慢慢冷下去（镇压的熔岩冷得最慢）
            float k = holding(t) ? 1F : scorch * (1F - Mth.clamp(tt / (life - hold), 0F, 1F));
            if (inherited != null) {
                inherited.render(draw, inherited::length, (i, behind) -> k, false, alpha * Math.min(1F, k * 2F + 0.2F), tint);
            } else {
                VfxPlasma.groundCracks(draw, crackSeed, origin, 0.3F, radius * 0.95F, cracks, k, alpha * Math.min(1F, k * 2F + 0.2F), tint);
            }
        }
    }

    /** 顿帧那一帧：落点一小团白热（不铺满招式范围）、身上一颗定住的星芒 */
    private void impactFrame(VfxDraw draw, Vec3 center) {
        draw.flat(origin, Math.min(radius * 0.45F, 2.2F), 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.5F));
        Vec3 c = draw.towardCamera(center, 1D);
        draw.billboard(c, burst * 0.9F, 0F, VfxMode.FLARE, 1000, Vfx.argb(tint.core(), 1F));
        draw.billboard(c, burst * 1.6F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.6F));
    }

    /** 坠身：从天上砸到落点的一道光痕（顿帧时最亮，之后几刻淡掉、变细） */
    private void fallStreak(VfxDraw draw, float t) {
        float k = 1F - Mth.clamp((t - hold) / 5F, 0F, 1F);
        if (k <= 0F) {
            return;
        }
        Vec3 bottom = origin.add(0D, 0.1D, 0D);
        Vec3 top = origin.add(0D, 11D, 0D);
        float w = 0.5F + 0.4F * k;
        draw.strip(bottom, top, w * 1.6F, 0.05F, Vfx.argb(tint.glow(), 0.45F * k), Vfx.argb(tint.glow(), 0F), VfxMode.STRIP, 40, 0F, 1F);
        draw.strip(bottom, top, w * 0.5F, 0.02F, Vfx.argb(tint.core(), k), Vfx.argb(tint.inner(), 0F), VfxMode.PLASMA, seed & 1023, 0F, 11F);
    }

    /** 收束：胸口一圈往外撑开的音爆环 + 空气扭曲 */
    private void sonicRing(VfxDraw draw, Vec3 center, float tt) {
        float k = 1F - Mth.clamp(tt / 12F, 0F, 1F);
        if (k <= 0F) {
            return;
        }
        float r = 0.6F + radius * Vfx.easeOutExpo(tt / 6F);
        Vec3 c = draw.towardCamera(center, 0.5D);
        draw.billboard(c, r, 0F, VfxMode.RING, 40, Vfx.argb(tint.core(), 0.9F * k));
        draw.billboard(c, r * 1.02F, 0F, VfxMode.RING, 160, Vfx.argb(tint.glow(), 0.6F * k));
        draw.distortBillboard(c, r * 1.12F, 0, 120, 0.8F * k);
    }

    /** 冲起来的等离子柱（失序之柱） */
    private void column(VfxDraw draw, float tt, float height, float width, float span) {
        float col = Vfx.pulse(tt / span, 0.08F);
        if (col <= 0F) {
            return;
        }
        Vec3 bottom = origin.add(0D, 0.1D, 0D);
        Vec3 top = origin.add(0D, 0.1D + height * Vfx.easeOutExpo(tt / 3F), 0D);
        VfxPlasma.beam(draw, bottom, top, width * col, col, true, seed, tint);
        if (tier() >= 1) {
            VfxPlasma.corona(draw, RandomSource.create(seed + 3L), origin.add(0D, 0.3D, 0D), 1.6F, 4, 0.035F, col, tint);
        }
    }

    /** 魔像：电弧沿地面往外劈（每两刻换一次形状），再往上窜几道 */
    private void groundArcs(VfxDraw draw, float tt) {
        float a = VfxPlasma.strokeAlpha(tt, 8F);
        if (a <= 0.01F) {
            return;
        }
        RandomSource r = RandomSource.create(seed + 11L);
        int n = Math.max(2, Math.round(6 * Vfx.detail(origin)));
        Vec3 c = origin.add(0D, 0.12D, 0D);
        for (int i = 0; i < n; i++) {
            double ang = (i + r.nextDouble() * 0.6D) * Mth.TWO_PI / n;
            double len = radius * (0.75D + 0.4D * r.nextDouble());
            boolean up = i % 3 == 0;
            Vec3 to = up ? c.add(Math.cos(ang) * len * 0.4D, 2.2D + r.nextDouble() * 1.8D, Math.sin(ang) * len * 0.4D)
                    : c.add(Math.cos(ang) * len, 0D, Math.sin(ang) * len);
            int pn = VfxPaths.jagged(r, c, to, 4, up ? 0.3F : 0.22F, path);
            if (!up) {
                for (int p = 0; p < pn; p++) {
                    path[p * 3 + 1] = c.y + Math.abs(path[p * 3 + 1] - c.y) * 0.25D;
                }
            }
            VfxPlasma.forkedArc(draw, r, path, pn, 0.05F, a, seed + i, 2, (float) len * 0.35F, tint);
        }
    }

    /** 暗袭：影刃从出手点扑下来的残痕，落点两道交叉的斩痕 */
    private void slash(VfxDraw draw, float t) {
        float k = 1F - Mth.clamp(t / 7F, 0F, 1F);
        if (k <= 0F) {
            return;
        }
        Vec3 hit = origin.add(0D, 1D, 0D);
        if (dir.lengthSqr() > 1.0E-4D) {
            Vec3 from = hit.add(dir.scale(Math.min(1D, 0.35D + 0.65D * k)));
            draw.strip(hit, from, 0.35F * k, 0.02F, Vfx.argb(tint.core(), k), Vfx.argb(tint.glow(), 0F), VfxMode.PLASMA, seed & 1023, 0F,
                    (float) dir.length());
            draw.strip(hit, from, 0.9F * k, 0.05F, Vfx.argb(tint.glow(), 0.4F * k), Vfx.argb(tint.outer(), 0F), VfxMode.STRIP, 40, 0F, 1F);
        }
        float s = 1.6F * (0.6F + 0.4F * Vfx.easeOutExpo(t / 2F));
        for (int i = 0; i < 2; i++) {
            double ang = Math.PI / 4D + i * Math.PI / 2D;
            Vec3 off = new Vec3(Math.cos(ang) * s, 0D, Math.sin(ang) * s);
            Vec3 a = origin.add(0D, 0.05D, 0D).subtract(off);
            Vec3 b = origin.add(0D, 0.05D, 0D).add(off);
            draw.groundStrip(a, b, 0.12F * (0.5F + 0.5F * k), Vfx.argb(tint.core(), k), Vfx.argb(tint.core(), k), VfxMode.STRIP, 500, 0F, 1F);
        }
    }

    /** 正午：天上压下来的一根粗日光柱 */
    private void sunColumn(VfxDraw draw, float t, float tt) {
        float col = holding(t) ? 1F : Vfx.pulse((tt + 1F) / 22F, 0.05F);
        if (col > 0F) {
            Vec3 ground = origin.add(0D, 0.1D, 0D);
            Vec3 sky = origin.add(0D, 26D, 0D);
            VfxPlasma.beam(draw, ground, sky, 2.4F * (0.4F + 0.6F * col), col, true, seed, tint);
        }
    }

    // ------------------------------------------------------------------ 粒子小工具

    /** 熔滴：重、拉长、落地前一直亮 */
    private void lava(Vec3 at, float designed) {
        int n = Vfx.count(designed, at);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double s = 0.12D + random.nextDouble() * 0.25D;
            VfxParticles.spawn(at.x, at.y, at.z, Math.cos(a) * s, 0.35D + random.nextDouble() * 0.35D, Math.sin(a) * s, 24 + random.nextInt(14),
                    0.09F, 0.05F, Vfx.argb(0xFFF0B0, 1F), Vfx.argb(VfxPalette.EMBER, 0.2F), VfxMode.SPARK, 0, 0.96F, 0.05F, 1.6F, 0F);
        }
    }

    /** 往外涌、慢慢升起的烟团（普通半透明混合） */
    private void smoke(Vec3 at, float r, float designed, int rgb, float alpha) {
        int n = Vfx.count(designed, at);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double d = r * (0.2D + random.nextDouble() * 0.8D);
            VfxParticles.spawn(at.x + Math.cos(a) * d, at.y + 0.2D, at.z + Math.sin(a) * d, Math.cos(a) * 0.06D, 0.03D + random.nextDouble() * 0.04D,
                    Math.sin(a) * 0.06D, 36 + random.nextInt(20), 0.7F, 1.9F, Vfx.argb(rgb, alpha), Vfx.argb(rgb, 0F), VfxMode.SMOKE,
                    random.nextInt(500), 0.95F, -0.001F, 0F, 0.01F);
        }
    }

    @Override
    public AABB bounds() {
        double h = style == S2CBossWarningPacket.FX_NOON ? 28D : style == S2CBossWarningPacket.FX_SLAM ? 12D : 10D;
        double r = radius * 1.8D + 2D + (style == S2CBossWarningPacket.FX_STRIKE ? dir.length() : 0D);
        return new AABB(origin, origin).inflate(r, 2D, r).expandTowards(0D, h, 0D);
    }
}
