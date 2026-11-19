package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** 武器专用的几种拼法（底座 VfxPlasma 的层次，换成武器要的参数） */
final class WeaponDraw {

    private static final double[] CUT = new double[4];
    private static final double NEAR = 0.3D;

    private WeaponDraw() {
    }

    /**
     * 枪械光束：层次同 {@link VfxPlasma#beam}，多了两样：开头 fadeIn 格从透明渐显（管口不是一圈硬边），
     * slope 大于 0 时起点一段按斜率收细（开狙击镜时镜里是一条细线，半宽 = min(half, slope × (离起点的距离 + offset))）。
     * head（0~1）只画到那里，出手那一下光从枪口冲到落点。
     */
    static void gunBeam(VfxDraw draw, Vec3 a, Vec3 b, float head, float half, float alpha, float fadeIn, float slope, float offset,
                        int seed, VfxPlasma.Tint tint) {
        double len = a.distanceTo(b);
        if (alpha <= 0.01F || len < 1.0E-3D || head <= 0.01F) {
            return;
        }
        double end = len * Math.min(1F, head);
        // 光束从镜头旁边穿到身后时（别人朝自己开枪、第三人称正面），身后那段的面片朝向会翻过来拧成蝴蝶结，切到镜头前 0.3 格为止
        Vector3f look = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
        Vec3 cam = draw.camera();
        double da = (a.x - cam.x) * look.x() + (a.y - cam.y) * look.y() + (a.z - cam.z) * look.z();
        double db = (b.x - cam.x) * look.x() + (b.y - cam.y) * look.y() + (b.z - cam.z) * look.z();
        double lo = 0D;
        if (da < NEAR && db < NEAR) {
            return;
        } else if (da < NEAR) {
            lo = len * (NEAR - da) / (db - da);
        } else if (db < NEAR) {
            end = Math.min(end, len * (da - NEAR) / (da - db));
        }
        if (end - lo < 1.0E-3D) {
            return;
        }
        int n = 0;
        CUT[n++] = 0D;
        if (fadeIn > 0F && fadeIn < end) {
            CUT[n++] = fadeIn;
        }
        if (slope > 0F) {
            double cone = half / slope - offset;
            if (cone > 0D && cone < end) {
                CUT[n++] = cone;
            }
        }
        CUT[n++] = end;
        java.util.Arrays.sort(CUT, 0, n);
        Vec3 dir = b.subtract(a).scale(1D / len);
        int tier = Vfx.tier();
        for (int i = 0; i + 1 < n; i++) {
            double s0 = Math.max(lo, CUT[i]);
            double s1 = CUT[i + 1];
            if (s1 - s0 < 1.0E-4D) {
                continue;
            }
            Vec3 p0 = a.add(dir.scale(s0));
            Vec3 p1 = a.add(dir.scale(s1));
            float w0 = width(s0, half, slope, offset);
            float w1 = width(s1, half, slope, offset);
            float a0 = alpha * fade(s0, fadeIn);
            float a1 = alpha * fade(s1, fadeIn);
            float u0 = (float) s0;
            float u1 = (float) s1;
            if (tier >= 2) {
                draw.strip(p0, p1, w0 * 1.9F, w1 * 1.9F, Vfx.argb(tint.outer(), 0.22F * a0), Vfx.argb(tint.outer(), 0.22F * a1),
                        VfxMode.STRIP, 30, 0F, 1F);
            }
            draw.strip(p0, p1, w0, w1, Vfx.argb(tint.glow(), 0.62F * a0), Vfx.argb(tint.glow(), 0.62F * a1), VfxMode.BEAM, seed & 1023, u0, u1);
            if (tier >= 1) {
                draw.strip(p0, p1, w0 * 0.45F, w1 * 0.45F, Vfx.argb(tint.inner(), a0), Vfx.argb(tint.inner(), a1), VfxMode.PLASMA,
                        (seed * 7 + 3) & 1023, u0, u1);
            }
            draw.strip(p0, p1, w0 * 0.16F, w1 * 0.16F, Vfx.argb(tint.core(), a0), Vfx.argb(tint.core(), a1), VfxMode.STRIP, 650, 0F, 1F);
        }
    }

    private static float width(double s, float half, float slope, float offset) {
        return slope > 0F ? (float) Math.min(half, slope * (s + offset)) : half;
    }

    private static float fade(double s, float fadeIn) {
        return fadeIn > 0F ? (float) Mth.clamp(s / fadeIn, 0D, 1D) : 1F;
    }

    /** 点到线段最近距离的平方 */
    static double segmentDistanceSqr(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        if (l2 < 1.0E-9D) {
            return p.distanceToSqr(a);
        }
        double t = Mth.clamp(p.subtract(a).dot(ab) / l2, 0D, 1D);
        return p.distanceToSqr(a.add(ab.scale(t)));
    }

    /** 镜头贴着光束时变淡（被射线穿过脑袋、走进光束里的人不被染屏）：0.6 格外 1，0.2 格内 0 */
    static float nearFade(VfxDraw draw, Vec3 a, Vec3 b) {
        double d2 = segmentDistanceSqr(draw.camera(), a, b);
        if (d2 >= 0.36D) {
            return 1F;
        }
        return (float) Mth.clamp((Math.sqrt(d2) - 0.2D) / 0.4D, 0D, 1D);
    }

    /** 枪口闪光：星芒 + 小爆点 + 一团辉光，t 是开火后过了几刻，life 刻散完 */
    static void muzzleFlash(VfxDraw draw, Vec3 at, float size, float t, float life, int seed, VfxPlasma.Tint tint) {
        if (t < 0F || t >= life) {
            return;
        }
        float k = 1F - t / life;
        float kk = k * k;
        draw.billboard(at, size * (0.8F + 0.5F * k), seed * 0.37F, VfxMode.FLARE, 900, Vfx.argb(tint.core(), kk));
        draw.billboard(at.x, at.y, at.z, size * 1.4F, size * 1.4F, seed * 0.91F, VfxMode.BURST, seed & 1023, Vfx.argb(tint.inner(), 0.8F * kk), false);
        draw.billboard(at, size * 2.4F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.4F * k));
    }
}
