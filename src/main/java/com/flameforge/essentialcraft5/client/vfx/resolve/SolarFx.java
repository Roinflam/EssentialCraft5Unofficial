package com.flameforge.essentialcraft5.client.vfx.resolve;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 天灾之心日阵的两种落下：
 * <ul>
 *   <li>{@code FX_PRISM} 天光 / 蚀日棱线：一排日光柱从天上砸下来——从线的正中往两头一根接一根落地（和预警里危险色从中间往两头涨对得上），
 *       沿线立起一道光墙，地面留一条柔和的余光、余烬飘起，两端爆点；</li>
 *   <li>{@code FX_SUNFALL} 烈日落下：被标记的人头上砸下一根粗日光柱，脚下炸开、冲击波往外推淡、地面晒出几道焦痕；
 *       有遮挡时光柱打在遮挡顶上溅开，地面没事。</li>
 * </ul>
 */
public final class SolarFx extends ResolveFx {

    /** 棱线：光柱从正中落到两头要几刻 */
    private static final float SWEEP = 3F;
    /** 光柱从多高落下来 */
    private static final double SKY = 16D;
    /** 棱线的光柱从多高落下来：比烈日矮，一排光柱太高就成了满屏激光 */
    private static final double PRISM_SKY = 11D;
    /** 棱线：光柱间距（格）。再密几根光柱的辉光就叠成一面白墙，看不出是一根根砸下来的 */
    private static final double PRISM_GAP = 6D;
    /** 棱线：单根光柱的亮度（几根同时亮，单根不能按满亮画） */
    private static final float PRISM_BRIGHT = 0.7F;

    private final boolean prism;
    private final Vec3 a;
    private final Vec3 b;
    private final float half;
    /** 烈日：光柱落在哪（露天 = 脚下，有遮挡 = 遮挡顶上） */
    private final Vec3 impact;
    private final boolean blocked;
    private final int burstSeed = random.nextInt(1024);
    private final long crackSeed = random.nextLong();

    SolarFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level, packet.style() == S2CBossWarningPacket.FX_PRISM ? 30 : 30, 2);
        this.prism = packet.style() == S2CBossWarningPacket.FX_PRISM;
        this.a = origin.subtract(dir.x, 0D, dir.z);
        this.b = origin.add(dir.x, 0D, dir.z);
        this.half = Math.max(0.3F, packet.size2());
        boolean exposed = packet.size2() >= 0.5F;
        Vec3 hitAt = origin;
        if (!prism && !exposed) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(origin.x), Mth.floor(origin.z));
            if (top > origin.y + 1D) {
                hitAt = new Vec3(origin.x, top, origin.z);
            }
        }
        this.impact = hitAt;
        this.blocked = !prism && !exposed;
    }

    // ------------------------------------------------------------------ 时间轴

    @Override
    protected void start() {
        if (prism) {
            shake(nearestOnLine(), half + 2D, 0.4F, 7);
        } else if (!blocked) {
            shake(impact, 3D, 0.55F, 8);
            punch(impact, 4D, 0.85F);
            flashNear(impact, 10D, VfxPalette.GOLD, 0.15F, 12);
        } else {
            shake(impact, 3D, 0.2F, 4);
        }
    }

    @Override
    protected void release() {
        if (prism) {
            int n = Math.max(2, Math.round((float) (a.distanceTo(b) / 3D) * Vfx.detail(origin)));
            for (int i = 0; i <= n; i++) {
                Vec3 p = a.lerp(b, i / (double) n).add(0D, 0.2D, 0D);
                VfxPlasma.sparks(p, new Vec3(0D, 1D, 0D), 0.6D, 4F, 0.25D, 0.6D, 10, 20, tint);
            }
            VfxPlasma.embers(origin, Math.min(6D, a.distanceTo(b) * 0.5D), 24F, tint);
            return;
        }
        if (blocked) {
            VfxPlasma.sparks(impact.add(0D, 0.1D, 0D), new Vec3(0D, 0.3D, 0D), 1.2D, 30F, 0.25D, 0.6D, 8, 16, tint);
            return;
        }
        VfxPlasma.sparks(impact.add(0D, 0.2D, 0D), new Vec3(0D, 1D, 0D), 0.6D, 60F, 0.35D, 1D, 12, 26, tint);
        VfxPlasma.sparks(impact.add(0D, 0.4D, 0D), Vec3.ZERO, 0D, 24F, 0.25D, 0.6D, 8, 16, tint);
        VfxPlasma.embers(impact, 2.5D, 36F, tint);
    }

    /** 线上离镜头最近的点（震屏按它算） */
    private Vec3 nearestOnLine() {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 ab = b.subtract(a);
        double len2 = ab.x * ab.x + ab.z * ab.z;
        double f = len2 < 1.0E-6D ? 0D : Mth.clamp(((camera.x - a.x) * ab.x + (camera.z - a.z) * ab.z) / len2, 0D, 1D);
        return a.lerp(b, f);
    }

    // ------------------------------------------------------------------ 画

    @Override
    protected void renderFx(VfxDraw draw, float t, float alpha) {
        if (prism) {
            renderPrism(draw, t);
        } else {
            renderSunfall(draw, t);
        }
    }

    private void renderPrism(VfxDraw draw, float t) {
        double len = a.distanceTo(b);
        if (len < 0.5D) {
            return;
        }
        // 一排光柱：正中先落，SWEEP 刻落到两头；每根落地后亮 14 刻收掉
        int n = Math.max(2, Math.round((float) (len / PRISM_GAP) * Vfx.detail(origin)));
        if ((n & 1) == 1) {
            n++;
        }
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n * 2F - 1F;
            float land = Math.abs(f) * SWEEP;
            float lt = t - land;
            if (lt < 0F) {
                continue;
            }
            float col = lt < hold ? 1F : Vfx.pulse((lt - hold + 1F) / 14F, 0.06F);
            if (col <= 0.01F) {
                continue;
            }
            Vec3 p = origin.add(dir.x * f, 0.05D, dir.z * f);
            float w = half * (0.55F + 0.35F * col);
            VfxPlasma.beam(draw, p, p.add(0D, PRISM_SKY, 0D), w, col * PRISM_BRIGHT, true, seed + i * 7, tint);
            if (lt < 6F) {
                VfxPlasma.burst(draw, p.add(0D, 0.4D, 0D), half * 1.4F, lt, 6F, (burstSeed + i * 13) & 1023, tint);
            }
        }
        // 地面：跟着光柱往两头铺的一层柔和余光（不画白热的硬边带子，范围由预警标过）
        float e = Mth.clamp(t / SWEEP, 0F, 1F);
        float k = 1F - Mth.clamp((t - SWEEP) / 18F, 0F, 1F);
        if (k > 0F) {
            Vec3 fa = origin.add(-dir.x * e, 0D, -dir.z * e);
            Vec3 fb = origin.add(dir.x * e, 0D, dir.z * e);
            draw.groundStrip(fa, fb, half * 1.7F, Vfx.argb(tint.glow(), 0.35F * k), Vfx.argb(tint.glow(), 0.35F * k), VfxMode.BEAM, seed & 1023, 0F,
                    (float) len);
        }
        // 光墙：沿线竖着一片柔光，落满后慢慢淡掉
        float wall = (t < SWEEP ? e : 1F - Mth.clamp((t - SWEEP) / 12F, 0F, 1F));
        if (wall > 0.01F) {
            float h = 4.5F;
            draw.plane(origin.x, origin.y + h, origin.z, (float) dir.x * e, 0F, (float) dir.z * e, 0F, h, 0F, VfxMode.DOT, 0,
                    Vfx.argb(tint.glow(), 0.2F * wall), false);
        }
        // 两端
        if (t >= SWEEP) {
            float et = t - SWEEP;
            for (Vec3 end : new Vec3[]{a, b}) {
                VfxPlasma.burst(draw, end.add(0D, 0.6D, 0D), half * 2.6F, et, 10F, burstSeed, tint);
            }
        }
    }

    private void renderSunfall(VfxDraw draw, float t) {
        float tt = after(t);
        Vec3 top = impact.add(0D, SKY + 6D, 0D);
        Vec3 ground = impact.add(0D, 0.05D, 0D);
        // 光柱：顿帧时一根细而极亮的，之后胀粗、慢慢收掉
        float col = holding(t) ? 1F : Vfx.pulse((tt + 1F) / 18F, 0.08F);
        if (col > 0.01F) {
            float w = holding(t) ? 0.6F : 0.6F + 1.2F * Vfx.easeOutExpo(tt / 3F) * col;
            VfxPlasma.beam(draw, ground, top, w, col, true, seed, tint);
        }
        if (holding(t)) {
            Vec3 c = draw.towardCamera(impact.add(0D, 1D, 0D), 1D);
            draw.billboard(c, 2.4F, 0F, VfxMode.FLARE, 1000, Vfx.argb(tint.core(), 1F));
            draw.flat(impact, 1.4F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.6F));
        }
        if (blocked) {
            // 打在遮挡顶上：光溅开、一圈很淡的波，地面不炸
            VfxPlasma.burst(draw, impact.add(0D, 0.3D, 0D), 1.6F, tt, 8F, burstSeed, tint);
            VfxPlasma.wave(draw, impact, 2.4F, tt, 4F, 10F, 0.7F, tint);
            return;
        }
        VfxPlasma.burst(draw, draw.towardCamera(impact.add(0D, 1D, 0D), 1D), 3.2F, tt, 11F, burstSeed, tint);
        VfxPlasma.wave(draw, impact, 3.6F, tt, 6F, 14F, 1F, tint);
        float scorch = holding(t) ? 1F : 0.8F * (1F - Mth.clamp(tt / (life - hold), 0F, 1F));
        VfxPlasma.groundCracks(draw, crackSeed, impact, 0.3F, 2.4F, 6, scorch, Math.min(1F, scorch * 2F + 0.2F), tint);
    }

    @Override
    public AABB bounds() {
        if (prism) {
            return new AABB(a.x, a.y, a.z, b.x, b.y, b.z).inflate(half * 3D + 3D, 2D, half * 3D + 3D).expandTowards(0D, SKY + 2D, 0D);
        }
        return new AABB(impact, impact).inflate(4D, 2D, 4D).expandTowards(0D, SKY + 8D, 0D);
    }
}
