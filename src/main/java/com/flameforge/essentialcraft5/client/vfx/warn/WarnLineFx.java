package com.flameforge.essentialcraft5.client.vfx.warn;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxCracks;
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
import org.jetbrains.annotations.Nullable;

/**
 * 线预警。两种：
 * <ul>
 *   <li>贴地的线（天光 / 蚀日 / 熔裂棱线）：地上不画光带和亮边。一道主缝从线的正中往两头裂开（开头四分之一、最多 5 刻裂到两头），
 *       一路往左右交替岔出短侧缝，侧缝的尖落在离中线半宽 ~ 半宽 + 0.3 格（线有多宽看侧缝尖到哪，宁宽勿窄）；
 *       之后越烧越热，最后 10 刻白热、越闪越快、侧缝尖往上喷火花，最后 3 刻压暗；落下时整张网白热一下、几刻里退掉（光墙归结算画）。</li>
 *   <li>空中瞄准线（{@link S2CBossWarningPacket#FLAG_TETHER}，熄光里的暗袭）：从首领到落点一道细电弧，越来越亮，
 *       一团能量顺着它反复冲向落点（落点另有一个圆形预警）。</li>
 * </ul>
 */
public final class WarnLineFx extends WarnFx {

    private static final int PATH_DEPTH = 4;

    private final boolean tether;
    private final Vec3 a;
    private final Vec3 b;
    private final float half;
    private final double[] path = new double[VfxPaths.jaggedPoints(PATH_DEPTH) * 3];
    @Nullable
    private VfxCracks cracks;

    public WarnLineFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level);
        this.tether = packet.has(S2CBossWarningPacket.FLAG_TETHER);
        Vec3 d = new Vec3(packet.dirX(), tether ? packet.dirY() : 0D, packet.dirZ());
        if (tether) {
            this.a = origin;
            this.b = origin.add(d.scale(packet.size()));
        } else {
            this.a = origin.subtract(d.scale(packet.size()));
            this.b = origin.add(d.scale(packet.size()));
        }
        this.half = Math.max(0.2F, packet.size2());
    }

    private VfxCracks cracks() {
        if (cracks == null) {
            float spacing = Vfx.tier() == 0 ? 2.2F : 1.6F;
            cracks = VfxCracks.line(seed * 53L + 11L, a, b, half, 0.3F, spacing);
        }
        return cracks;
    }

    /** 主缝从正中裂到两头要几刻（短预警按比例缩） */
    private float growTicks() {
        return Math.max(1F, Math.min(5F, duration * 0.25F));
    }

    private float head(VfxCracks net, int i, float t) {
        if (net.isBranch(i)) {
            return net.branchHead(i, head(net, net.parent(i), t));
        }
        return Vfx.easeOutExpo(Mth.clamp(t / growTicks(), 0F, 1F)) * net.length(i);
    }

    private float crackHeat(float k, float left, float t) {
        if (t >= duration) {
            return 1F;
        }
        float late = Math.min(10F, duration * 0.5F);
        return left < late ? Math.min(1F, 0.55F + 0.45F * (late - left) / (late * 0.7F)) : 0.25F + 0.6F * k;
    }

    @Override
    protected int localZone(Vec3 feet) {
        if (tether) {
            return -1;
        }
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double len2 = dx * dx + dz * dz;
        double f = len2 < 1.0E-6D ? 0D : Mth.clamp(((feet.x - a.x) * dx + (feet.z - a.z) * dz) / len2, 0D, 1D);
        double px = a.x + dx * f - feet.x;
        double pz = a.z + dz * f - feet.z;
        double r = half + 0.3D;
        return px * px + pz * pz <= r * r ? 1 : -1;
    }

    @Override
    protected void tickWarning(float k) {
        if (tether) {
            return;
        }
        VfxCracks net = cracks();
        float len = (float) a.distanceTo(b);
        VfxPlasma.embers(net.randomPoint(random), 0.3D, (0.6F + 1.2F * k) * Mth.clamp(len / 12F, 0.5F, 4F), tint);
        if (duration - age < 10 && (age & 1) == 0) {
            int branches = net.size() - 2;
            int step = Math.max(1, branches / 10);
            Vec3 up = new Vec3(0D, 1D, 0D);
            for (int i = 2 + (age >> 1) % step; i < net.size(); i += step) {
                VfxPlasma.sparks(net.tip(i).add(0D, 0.05D, 0D), up, 0.4D, 0.6F, 0.15D, 0.35D, 8, 14, tint);
            }
        }
    }

    /** 落下那一刻沿缝炸起一串火花 */
    @Override
    protected void onResolve() {
        if (tether || cracks == null) {
            return;
        }
        int n = Math.max(4, (int) Math.min(24D, a.distanceTo(b)));
        for (int i = 0; i < n; i++) {
            VfxPlasma.sparks(cracks.randomPoint(random).add(0D, 0.1D, 0D), new Vec3(0D, 1D, 0D), 0.45D, 1F, 0.15D, 0.4D, 6, 12, tint);
        }
    }

    @Override
    public void render(VfxDraw draw) {
        float pt = draw.partialTick();
        float t = time(pt);
        float alpha = alpha(t);
        if (alpha <= 0.01F) {
            return;
        }
        float k = progress(t);
        if (tether) {
            renderTether(draw, t, k, alpha);
            return;
        }
        VfxCracks net = cracks();
        float heat = crackHeat(k, left(t), t);
        float pulse = t >= duration ? 1F : VfxPlasma.warningPulse(left(t), t);
        net.render(draw, i -> head(net, i, t), (i, behind) -> heat, false, alpha * pulse, tint);
    }

    private void renderTether(VfxDraw draw, float t, float k, float alpha) {
        float blink = VfxPlasma.warningPulse(left(t), t);
        int n = VfxPaths.jagged(RandomSource.create(seed), a, b, PATH_DEPTH, 0.06F, path);
        VfxPlasma.arc(draw, path, n, 0.02F + 0.015F * k, (0.25F + 0.6F * k) * blink * alpha, seed, tint);
        float f = (t * 0.07F) % 1F;
        VfxPlasma.pulse(draw, path, n, f, 0.1F + 0.08F * k, (0.5F + 0.5F * k) * alpha, tint);
        draw.billboard(a, 0.25F + 0.25F * k, 0F, VfxMode.DOT, 300, Vfx.argb(tint.glow(), 0.5F * alpha));
    }

    @Override
    public AABB bounds() {
        return new AABB(a, b).inflate(half + 2D, 3D, half + 2D);
    }
}
