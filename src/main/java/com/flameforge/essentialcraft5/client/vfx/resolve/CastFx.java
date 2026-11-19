package com.flameforge.essentialcraft5.client.vfx.resolve;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 天灾之心身上起手的两种大招画面（之后各招的预警、结算照常）：
 * <ul>
 *   <li>{@code FX_COMBO} 合相技：这一对的两种相色各一股能量，绕着它往里卷、越卷越紧，第 {@value #MERGE} 刻合在一起——
 *       顿两刻、炸开（两种颜色的爆点叠在一起），地面两道颜色不同的冲击波；</li>
 *   <li>{@code FX_THROE} 垂死反扑：崩解里它身上猛地炸开一下，几道暗红电弧劈到地上，地面冲击波 + 电流。</li>
 * </ul>
 */
public final class CastFx extends ResolveFx {

    /** 合相技：两股能量第几刻合到一起 */
    private static final int MERGE = 10;
    private static final int HOLD = 2;
    private static final int POINTS = 30;

    private final boolean combo;
    private final VfxPlasma.Tint second;
    private final float bodyRadius;
    private final Vec3 floor;
    private final float reach;
    private final double[] stream = new double[POINTS * 3];
    private final double[] path = new double[VfxPaths.jaggedPoints(4) * 3];
    private final int burstSeed = random.nextInt(1024);
    private final long crackSeed = random.nextLong();

    CastFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level, packet.style() == S2CBossWarningPacket.FX_COMBO ? 40 : 30, packet.style() == S2CBossWarningPacket.FX_COMBO ? 0 : 2);
        this.combo = packet.style() == S2CBossWarningPacket.FX_COMBO;
        this.second = VfxPlasma.tint(packet.color2());
        this.bodyRadius = Math.max(0.8F, combo ? packet.size() : 1.5F);
        this.floor = origin.subtract(0D, Math.max(0D, packet.dirY()), 0D);
        this.reach = combo ? 9F : Math.max(3F, packet.size());
    }

    @Override
    protected void start() {
        if (!combo) {
            shake(floor, reach, 0.5F, 8);
        }
    }

    @Override
    protected void release() {
        // 垂死反扑的炸开
        VfxPlasma.sparks(origin, Vec3.ZERO, 0D, 50F, 0.35D, 0.9D, 10, 22, tint);
        VfxPlasma.embers(floor, reach * 0.5D, 30F, tint);
    }

    @Override
    protected void tickFx(int t) {
        if (combo && t == MERGE) {
            shake(floor, reach, 0.55F, 9);
            flashNear(origin, reach + 10D, tint.glow(), 0.16F, 14);
        }
        if (combo && t == MERGE + HOLD) {
            VfxPlasma.sparks(origin, Vec3.ZERO, 0D, 30F, 0.35D, 0.9D, 10, 22, tint);
            VfxPlasma.sparks(origin, Vec3.ZERO, 0D, 30F, 0.35D, 0.9D, 10, 22, second);
            VfxPlasma.embers(floor, 4D, 20F, tint);
            VfxPlasma.embers(floor, 4D, 20F, second);
        }
    }

    @Override
    protected void renderFx(VfxDraw draw, float t, float alpha) {
        if (combo) {
            renderCombo(draw, t);
        } else {
            renderThroe(draw, t);
        }
    }

    private void renderCombo(VfxDraw draw, float t) {
        if (t < MERGE + HOLD) {
            // 两股能量绕着它往里卷
            float k = Mth.clamp(t / MERGE, 0F, 1F);
            float a = Mth.clamp(t / 3F, 0F, 1F);
            // 从场地上空几格外卷进来：起点要远在本体轮廓外，站在场边也看得出是两股不同颜色的能量
            double rr = (bodyRadius + 7D) * (1D - k) + bodyRadius * 0.4D;
            for (int s = 0; s < 2; s++) {
                for (int i = 0; i < POINTS; i++) {
                    double f = i / (double) (POINTS - 1);
                    double ang = s * Math.PI + f * Math.PI * 2.4D + t * 0.5D;
                    double r = rr * (0.35D + 0.65D * f);
                    stream[i * 3] = origin.x + Math.cos(ang) * r;
                    stream[i * 3 + 1] = origin.y + (f - 0.3D) * 5D * (1D - k) * (s == 0 ? 1D : -1D);
                    stream[i * 3 + 2] = origin.z + Math.sin(ang) * r;
                }
                VfxPlasma.trail(draw, stream, POINTS, 0.8F + 0.5F * k, a, seed + s, s == 0 ? tint : second);
            }
            Vec3 c = draw.towardCamera(origin, bodyRadius + 0.5D);
            draw.billboard(c, 0.6F + 1.8F * k, t * 0.15F, VfxMode.FLARE, 900, Vfx.argb(tint.core(), 0.4F + 0.6F * k));
            draw.billboard(c, 1.5F + 2.5F * k, 0F, VfxMode.DOT, 0, Vfx.argb(second.glow(), 0.4F * k));
            if (t >= MERGE) {
                // 顿帧：合在一起的那一下，定住
                // 星芒用等离子层的颜色、不用芯色：纯白的一大颗会把两种相色都盖成白
                draw.billboard(c, bodyRadius * 1.6F + 1F, 0F, VfxMode.FLARE, 1000, Vfx.argb(tint.inner(), 1F));
                draw.billboard(c, bodyRadius * 3.6F + 2F, 0F, VfxMode.DOT, 0, Vfx.argb(second.glow(), 0.6F));
            }
            return;
        }
        float tt = t - MERGE - HOLD;
        Vec3 c = draw.towardCamera(origin, bodyRadius + 0.5D);
        VfxPlasma.burst(draw, c, bodyRadius * 2F + 3F, tt, 12F, burstSeed, tint);
        VfxPlasma.burst(draw, c, bodyRadius * 2F + 2.4F, tt, 12F, (burstSeed + 311) & 1023, second);
        VfxPlasma.wave(draw, floor, reach, tt, 7F, 15F, 1F, tint);
        VfxPlasma.wave(draw, floor, reach * 0.7F, tt - 2F, 7F, 15F, 0.8F, second);
        // 地上两种相色的裂纹（相不是电，不铺地面电流），慢慢冷下去
        float heat = 1F - Mth.clamp(tt / 24F, 0F, 1F);
        VfxPlasma.groundCracks(draw, crackSeed, floor, 0.8F, reach * 0.75F, 5, heat, Math.min(1F, heat * 2F), tint);
        VfxPlasma.groundCracks(draw, crackSeed + 1L, floor, 0.8F, reach * 0.6F, 4, heat, Math.min(1F, heat * 2F), second);
    }

    private void renderThroe(VfxDraw draw, float t) {
        float tt = after(t);
        Vec3 c = draw.towardCamera(origin, 1.8D);
        if (holding(t)) {
            draw.billboard(c, 3F, 0F, VfxMode.FLARE, 1000, Vfx.argb(tint.core(), 1F));
            draw.billboard(c, 6F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.6F));
        }
        VfxPlasma.burst(draw, c, 5F, tt, 12F, burstSeed, tint);
        VfxPlasma.wave(draw, floor, reach, tt, 8F, 16F, 1F, tint);
        // 劈到地上的几道电弧
        float a = VfxPlasma.strokeAlpha(tt, 8F);
        if (a > 0.01F && origin.y - floor.y > 0.5D) {
            RandomSource r = RandomSource.create(seed + 21L);
            int n = Math.max(2, Math.round(5 * Vfx.detail(origin)));
            for (int i = 0; i < n; i++) {
                double ang = (i + r.nextDouble() * 0.5D) * Mth.TWO_PI / n;
                double d = reach * (0.3D + 0.35D * r.nextDouble());
                Vec3 to = floor.add(Math.cos(ang) * d, 0.05D, Math.sin(ang) * d);
                int pn = VfxPaths.jagged(r, origin, to, 4, 0.22F, path);
                VfxPlasma.forkedArc(draw, r, path, pn, 0.06F, a, seed + i, 3, 1.6F, tint);
                if (tt < 6F) {
                    VfxPlasma.burst(draw, to.add(0D, 0.3D, 0D), 1.3F, tt, 6F, (burstSeed + i * 29) & 1023, tint);
                }
            }
        }
        if (tier() >= 1 && tt < 14F) {
            VfxPlasma.groundCrawl(draw, RandomSource.create(seed), floor, reach * 0.9F, 10, 0.04F, 1F - tt / 14F, tint);
        }
    }

    @Override
    public AABB bounds() {
        return new AABB(floor, floor).inflate(reach + 3D, 2D, reach + 3D).expandTowards(0D, origin.y - floor.y + 6D, 0D);
    }
}
