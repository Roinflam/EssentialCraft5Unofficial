package com.flameforge.essentialcraft5.client.vfx.warn;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxCracks;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 移动的环（天灾之心的震波、矩阵之影收束时往里收的圈）：地上不画环，环走到哪、哪的地就裂开。
 * <ul>
 *   <li>蓄力（delay 刻）：圆心一团越来越亮的光，脚下的地从圆心往外裂出 2.5 格的一小片、越烧越热，最后几刻越闪越快再压暗；</li>
 *   <li>往外扩：环走过的地方顺着裂纹撕开，打击带（环的半径 ± band）里的缝白热，前沿落在打击带外沿 ~ 外沿 + 0.3 格（宁宽勿窄），
 *       身后的缝 5 格里冷下去退掉；每道主裂纹头上一颗熔亮的点（连起来就是波前），往外冒火花；</li>
 *   <li>往里收：同样，裂纹从外往里烧，打击带里的缝最亮，外面烧过的冷下去。</li>
 * </ul>
 * 环的位置照服务端的走法算（蓄力完每刻走 speed 格），和打击判定对得上。
 */
public final class WarnRingFx extends WarnFx {

    private final float from;
    private final float to;
    private final float speed;
    private final float band;
    private final int delay;
    @Nullable
    private VfxCracks cracks;
    private float[] jag;

    public WarnRingFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level);
        this.from = packet.size();
        this.to = packet.size2();
        this.speed = packet.dirX();
        this.band = Math.max(0.3F, packet.dirY());
        this.delay = Math.min(packet.delay(), duration);
    }

    @Override
    protected int linger() {
        return 4;
    }

    private boolean outward() {
        return speed > 0F;
    }

    private float radiusAt(float t) {
        if (t <= delay) {
            return from;
        }
        float r = from + speed * (t - delay);
        return Mth.clamp(r, Math.min(from, to), Math.max(from, to));
    }

    private VfxCracks cracks(Vec3 c) {
        if (cracks == null) {
            boolean low = Vfx.tier() == 0;
            float lod = low ? 0.65F : Math.max(0.75F, Vfx.detail(c));
            if (outward()) {
                float rOut = Math.max(from, to) + band + 0.3F;
                // 道数随半径涨：大圈上两道之间不超过约 2 格，前沿的光点才连得成一圈
                int count = Mth.clamp(Math.round(rOut * 1.5F * lod), 10, 56);
                cracks = VfxCracks.ring(seed * 97L + 5L, c, 0.4F, rOut, count, rOut > 16F ? (low ? 4 : 5) : (low ? 3 : 4));
            } else {
                float rOut = Math.max(from, to) + band + 0.3F;
                float rIn = Math.max(0.3F, Math.min(from, to) - band - 0.1F);
                int count = Mth.clamp(Math.round(rOut * 1.2F * lod), 12, 36);
                cracks = VfxCracks.inward(seed * 97L + 5L, c, rIn, 0.1F, rOut, count, low ? 3 : 4);
            }
            jag = new float[cracks.size()];
            RandomSource r = RandomSource.create(seed);
            for (int i = 0; i < jag.length; i++) {
                jag[i] = r.nextFloat() * 0.3F;
            }
        }
        cracks.moveTo(c);
        return cracks;
    }

    /** 裂纹的头走到哪（离起始端几格）：蓄力时从圆心裂出一小片；之后跟着打击带的前沿（往外扩是外沿，往里收是内沿） */
    private float head(VfxCracks net, int i, float t) {
        if (outward()) {
            float r;
            if (t < delay) {
                r = 2.5F * Vfx.easeOutCubic(Mth.clamp(t / Math.max(1F, Math.min(delay, 8F)), 0F, 1F));
            } else {
                r = Math.max(2.5F, radiusAt(t) + band) + jag[i];
            }
            return net.distanceToRadius(i, r, true);
        }
        return net.distanceToRadius(i, radiusAt(t) - band - jag[i], false);
    }

    @Override
    protected void tickWarning(float k) {
        Vec3 c = center(1F);
        VfxCracks net = cracks(c);
        if (age <= delay) {
            VfxPlasma.embers(net.randomPoint(random), 0.2D, 0.8F, tint);
            return;
        }
        // 前沿的裂纹头上冒火花（往外扩的往外斜）
        int n = Vfx.count(Math.min(14F, Math.abs(radiusAt(age)) * 0.5F), c);
        for (int j = 0; j < n; j++) {
            int i = random.nextInt(net.size());
            float h = head(net, i, age);
            if (h <= 0.05F || h >= net.length(i) - 0.05F) {
                continue;
            }
            Vec3 p = net.at(i, h);
            double dx = p.x - c.x;
            double dz = p.z - c.z;
            double l = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz)) * (outward() ? 1D : -1D);
            VfxPlasma.sparks(p.add(0D, 0.1D, 0D), new Vec3(dx / l * 0.6D, 1D, dz / l * 0.6D), 0.4D, 1F, 0.12D, 0.35D, 6, 12, tint);
        }
    }

    @Override
    public void render(VfxDraw draw) {
        float pt = draw.partialTick();
        float t = time(pt);
        float a = alpha(t);
        if (a <= 0.01F) {
            return;
        }
        Vec3 c = center(pt);
        VfxCracks net = cracks(c);
        if (t < delay) {
            float k0 = t / Math.max(1, delay);
            float pulse = VfxPlasma.warningPulse(delay - t, t);
            float heat = 0.3F + 0.6F * k0;
            draw.flat(c, 1.1F + 0.9F * k0, 0F, VfxMode.DOT, 300, Vfx.argb(tint.glow(), (0.25F + 0.45F * k0) * pulse * a));
            draw.billboard(c.add(0D, 0.4D, 0D), 0.3F + 0.6F * k0, t * 0.12F, VfxMode.FLARE, 900, Vfx.argb(tint.core(), (0.3F + 0.6F * k0) * a));
            net.render(draw, i -> head(net, i, t), (i, behind) -> heat, false, a * pulse, tint);
            return;
        }
        // 打击带（2 × band）里白热，往后 5 格冷下去、退掉
        float hit = 2F * band + 0.3F;
        float tail = 5F;
        net.render(draw, i -> head(net, i, t),
                (i, behind) -> behind <= hit ? Mth.lerp(behind / hit, 1F, 0.75F) : Mth.lerp(Math.min(1F, (behind - hit) / tail), 0.6F, 0.1F),
                (i, behind) -> behind <= hit ? 1F : 1F - (behind - hit) / tail,
                false, a, tint);
        // 每道主裂纹头上一颗熔亮的点、底下一小片软光：几十颗连起来读得出「波现在走到这」，又不是一道硬边环
        float bead = 0.2F + 0.1F * band;
        for (int i = 0; i < net.size(); i++) {
            if (net.isBranch(i)) {
                continue;
            }
            float h = head(net, i, t);
            if (h <= 0.05F || h >= net.length(i) - 0.05F) {
                continue;
            }
            Vec3 p = net.at(i, h);
            draw.flat(p.add(0D, 0.04D, 0D), bead * 3.5F, 0F, VfxMode.DOT, 300, Vfx.argb(tint.glow(), 0.28F * a));
            draw.billboard(p.add(0D, 0.1D, 0D), bead, 0F, VfxMode.DOT, 650, Vfx.argb(tint.core(), 0.85F * a));
        }
    }

    @Override
    public AABB bounds() {
        return box(Math.max(from, to) + band + 2D, 4D);
    }
}
