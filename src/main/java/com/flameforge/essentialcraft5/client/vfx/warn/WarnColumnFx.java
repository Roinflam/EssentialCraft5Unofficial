package com.flameforge.essentialcraft5.client.vfx.warn;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxCracks;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 光柱预警（烈日标记）：被标记的人头顶一根等离子光柱，跟着人走；前八成是又宽又柔、越来越亮的光柱，几团光点顺着柱子往下流，
 * 柱顶能量越聚越亮；最后两成收细成白热的一道（瞄准完成）。脚下不画圈：一小片裂纹从脚下劈到标记半径 ~ 半径 + 0.3 格、跟着人走，
 * 照圆形预警的读法越烧越热、最后几刻越闪越快再压暗。落下那一刻整根闪一下，脚下的缝白热一下退掉。
 */
public final class WarnColumnFx extends WarnFx {

    private final float height;
    private final float mark;
    private final int beamSeed;
    @Nullable
    private VfxCracks cracks;

    public WarnColumnFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level);
        this.height = Math.max(1F, packet.size());
        this.mark = Math.max(0.4F, packet.size2());
        this.beamSeed = random.nextInt(1024);
    }

    private Vec3 feet(float partialTick) {
        Vec3 f = followFeet(partialTick);
        return f == null ? origin : f;
    }

    private VfxCracks cracks(Vec3 base) {
        if (cracks == null) {
            int count = Mth.clamp(Math.round(mark * 4F), 5, 9);
            cracks = VfxCracks.radial(beamSeed * 61L + 3L, base, Math.min(0.3F, mark * 0.25F), mark, 0.3F, count, 2);
        }
        cracks.moveTo(base);
        return cracks;
    }

    private float growTicks() {
        return Math.max(1F, Math.min(5F, duration * 0.25F));
    }

    private float head(VfxCracks net, int i, float t) {
        if (net.isBranch(i)) {
            return net.branchHead(i, head(net, net.parent(i), t));
        }
        return Vfx.easeOutExpo(Mth.clamp(t / growTicks(), 0F, 1F)) * net.length(i);
    }

    @Override
    protected void tickWarning(float k) {
        Vec3 base = feet(1F);
        int n = Vfx.count(0.6F + 2F * k, base);
        for (int i = 0; i < n; i++) {
            double y = random.nextDouble() * height;
            double ang = random.nextDouble() * Math.PI * 2D;
            double r = 0.3D + random.nextDouble() * 0.4D;
            VfxParticles.spawn(base.x + Math.cos(ang) * r, base.y + y, base.z + Math.sin(ang) * r, 0D, -0.05D - random.nextDouble() * 0.05D, 0D,
                    10 + random.nextInt(10), 0.05F, 0.02F, Vfx.argb(tint.core(), 0.9F), Vfx.argb(tint.glow(), 0F), VfxMode.DOT, 500, 0.98F, 0F, 0F, 0F);
        }
    }

    @Override
    protected void onResolve() {
        Vec3 base = feet(1F);
        VfxPlasma.sparks(base.add(0D, 0.2D, 0D), Vec3.ZERO, 0D, 18F, 0.15D, 0.45D, 8, 16, tint);
    }

    @Override
    public void render(VfxDraw draw) {
        float pt = draw.partialTick();
        float t = time(pt);
        float a = alpha(t);
        if (a <= 0.01F) {
            return;
        }
        float k = progress(t);
        float blink = VfxPlasma.warningPulse(left(t), t);
        Vec3 base = feet(pt);
        Vec3 top = base.add(0D, height, 0D);
        float focus = k < 0.8F ? 0F : (k - 0.8F) / 0.2F;
        float width = (0.55F - 0.38F * focus) * (0.6F + 0.4F * k);
        VfxPlasma.beam(draw, base, top, width, (0.35F + 0.55F * k) * blink * a, true, beamSeed, tint);
        // 顺着柱子往下流的光点
        if (Vfx.tier() >= 1) {
            for (int j = 0; j < 3; j++) {
                float f = (t * 0.035F + j / 3F) % 1F;
                Vec3 p = top.add(0D, -height * f, 0D);
                draw.billboard(p, 0.14F + 0.1F * k, 0F, VfxMode.DOT, 700, Vfx.argb(tint.core(), (0.4F + 0.5F * k) * (1F - f * 0.5F) * a));
            }
        }
        // 柱顶聚能
        draw.billboard(top, 0.3F + 0.9F * k, t * 0.1F, VfxMode.FLARE, 900, Vfx.argb(tint.core(), (0.3F + 0.6F * k) * a));
        draw.billboard(top, 0.7F + 1.3F * k, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.4F * k * a));
        VfxPlasma.corona(draw, RandomSource.create(seed + 29L), top, 0.6F + 0.9F * k, 1 + Math.round(3F * k), 0.03F, (0.4F + 0.6F * k) * a, tint);
        // 脚下的裂纹
        VfxCracks net = cracks(base);
        float left = left(t);
        float late = Math.min(10F, duration * 0.5F);
        float heat = t >= duration ? 1F : left < late ? Math.min(1F, 0.55F + 0.45F * (late - left) / (late * 0.7F)) : 0.25F + 0.6F * k;
        float pulse = t >= duration ? 1F : VfxPlasma.warningPulse(left, t);
        net.render(draw, i -> head(net, i, t), (i, behind) -> heat, false, a * pulse, tint);
        float s = snap(t);
        if (s > 0F) {
            draw.strip(base, top, 0.35F * s + 0.05F, 0.2F * s, Vfx.argb(tint.core(), s), Vfx.argb(tint.core(), 0F), VfxMode.STRIP, 500, 0F, 1F);
            VfxPlasma.burst(draw, draw.towardCamera(base.add(0D, 1D, 0D), 0.8D), 1.6F, t - duration, linger(), 41, tint);
        }
    }

    @Override
    public AABB bounds() {
        Vec3 base = feet(1F);
        return new AABB(base, base).inflate(mark + 2D, 0D, mark + 2D).expandTowards(0D, height + 3D, 0D).move(0D, -1D, 0D);
    }
}
