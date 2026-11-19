package com.flameforge.essentialcraft5.client.vfx.resolve;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 废城守望者炮台的一枪（{@code FX_TRACER}）：枪口火光，一颗熔橙的等离子弹头拖着亮尾飞过去（每刻约 {@value #SPEED} 格，打远的也只要一两刻），
 * 身后留一道很快淡掉的弹道，打到的地方爆点 + 火花。
 */
public final class TracerFx extends ResolveFx {

    /** 弹头每刻飞多少格 */
    private static final float SPEED = 14F;
    /** 弹头亮尾多长（格） */
    private static final double HEAD = 2.6D;

    private final Vec3 to;
    private final double length;
    private final float travel;
    private final int burstSeed = random.nextInt(1024);
    private boolean landed;

    TracerFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level, 12, 0);
        this.to = origin.add(dir);
        this.length = dir.length();
        this.travel = Math.max(0.6F, (float) length / SPEED);
    }

    @Override
    protected void start() {
        Vec3 d = length < 1.0E-4D ? Vec3.ZERO : dir.scale(1D / length);
        VfxPlasma.sparks(origin, d, 0.35D, 6F, 0.1D, 0.3D, 4, 8, tint);
    }

    @Override
    protected void tickFx(int t) {
        if (!landed && t + 1 >= travel) {
            landed = true;
            VfxPlasma.sparks(to, dir.scale(-1D), 1.1D, 14F, 0.12D, 0.4D, 6, 14, tint);
        }
    }

    @Override
    protected void renderFx(VfxDraw draw, float t, float alpha) {
        // 枪口火光
        float m = 1F - Mth.clamp(t / 3F, 0F, 1F);
        if (m > 0F) {
            Vec3 muzzle = draw.towardCamera(origin, 0.3D);
            draw.billboard(muzzle, 0.55F * m + 0.2F, t, VfxMode.FLARE, 800, Vfx.argb(tint.core(), m));
            draw.billboard(muzzle, 0.9F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.6F * m));
        }
        if (length < 0.2D) {
            return;
        }
        float f = Mth.clamp(t / travel, 0F, 1F);
        Vec3 head = origin.add(dir.scale(f));
        // 弹道：从枪口到弹头一道细线，打到以后几刻里淡掉
        float trace = f < 1F ? 0.35F : 0.35F * (1F - Mth.clamp((t - travel) / 6F, 0F, 1F));
        if (trace > 0.01F) {
            draw.strip(origin, head, 0.05F, 0.08F, Vfx.argb(tint.glow(), 0.4F * trace), Vfx.argb(tint.glow(), trace), VfxMode.STRIP, 200, 0F, 1F);
        }
        // 弹头 + 亮尾
        if (f < 1F) {
            double back = Math.min(HEAD, length * f);
            Vec3 tail = head.subtract(dir.scale(back / length));
            draw.strip(tail, head, 0.05F, 0.32F, Vfx.argb(tint.glow(), 0F), Vfx.argb(tint.glow(), 0.7F), VfxMode.STRIP, 60, 0F, 1F);
            draw.strip(tail, head, 0.015F, 0.12F, Vfx.argb(tint.core(), 0F), Vfx.argb(tint.core(), 1F), VfxMode.PLASMA, seed & 1023, 0F, (float) back);
            draw.billboard(head, 0.34F, 0F, VfxMode.DOT, 700, Vfx.argb(tint.core(), 1F));
        }
        // 命中
        if (t >= travel) {
            VfxPlasma.burst(draw, draw.towardCamera(to, 0.5D), 1.1F, t - travel, 7F, burstSeed, tint);
        }
    }

    @Override
    public AABB bounds() {
        return new AABB(origin.x, origin.y, origin.z, to.x, to.y, to.z).inflate(1.5D);
    }
}
