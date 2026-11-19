package com.flameforge.essentialcraft5.client.vfx.resolve;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
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

/**
 * 渊潮领主的漩涡（{@code FX_VORTEX}，持续）：以它为中心、半径 = 拉人的范围，三条青绿的等离子旋臂往里卷（边上不画环），
 * 中心一团电晕；水里的光点顺着旋臂被吸进去。跟着领主走，画完包里的时长、领主没了就收（原版气泡照旧由服务端撒）。
 */
public final class VortexFx extends ResolveFx {

    private static final int ARMS = 3;
    private static final int POINTS = 26;

    private final float radius;
    /** 身体中间离脚下多高（第一次拿到跟随实体的位置时算） */
    private double lift = Double.NaN;
    private final double[] arm = new double[POINTS * 3];

    VortexFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level, 0, 0);
        this.radius = Math.max(1.5F, packet.size());
    }

    private Vec3 center(float partialTick) {
        Vec3 feet = followPos(partialTick);
        if (feet == origin) {
            return origin;
        }
        if (Double.isNaN(lift)) {
            // 包里的位置是身体中间，跟随给的是脚下：差就是离地多高
            lift = origin.y - feet.y;
        }
        return feet.add(0D, lift, 0D);
    }

    /** 开头淡入、最后 10 刻淡出 */
    private float envelope(float t) {
        return Mth.clamp(t / 6F, 0F, 1F) * Mth.clamp((life - t) / 10F, 0F, 1F);
    }

    @Override
    protected void tickFx(int t) {
        float e = envelope(t);
        if (e <= 0.05F) {
            return;
        }
        Vec3 c = center(1F);
        int n = Vfx.count(4F * e, c);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = radius * (0.7D + random.nextDouble() * 0.3D);
            double y = (random.nextDouble() - 0.5D) * 2D;
            // 切向 + 往里，转着被吸进去
            double vx = -Math.sin(a) * 0.22D - Math.cos(a) * 0.12D;
            double vz = Math.cos(a) * 0.22D - Math.sin(a) * 0.12D;
            VfxParticles.spawn(c.x + Math.cos(a) * r, c.y + y, c.z + Math.sin(a) * r, vx, -y * 0.03D, vz, 18 + random.nextInt(10), 0.07F, 0.02F,
                    Vfx.argb(tint.core(), 0.9F), Vfx.argb(tint.glow(), 0F), VfxMode.SPARK, 0, 0.97F, 0F, 2.2F, 0F);
        }
    }

    @Override
    protected void renderFx(VfxDraw draw, float t, float alpha) {
        float e = envelope(t) * alpha;
        if (e <= 0.01F) {
            return;
        }
        Vec3 c = center(draw.partialTick());
        float spin = t * 0.22F;
        // 旋臂：外头细、往里收粗变亮（拖尾的头在里面）
        for (int k = 0; k < ARMS; k++) {
            double phase = spin + k * Mth.TWO_PI / ARMS;
            for (int i = 0; i < POINTS; i++) {
                double f = i / (double) (POINTS - 1);
                double r = 0.5D + (radius - 0.5D) * f;
                double ang = phase + f * Mth.PI * 1.6D;
                arm[i * 3] = c.x + Math.cos(ang) * r;
                arm[i * 3 + 1] = c.y + Math.sin(f * 6D + t * 0.15D + k) * 0.25D;
                arm[i * 3 + 2] = c.z + Math.sin(ang) * r;
            }
            VfxPlasma.trail(draw, arm, POINTS, 0.45F, 0.85F * e, seed + k, tint);
        }
        // 水里一层很淡的柔光、中心的电晕；边上不画环（看着像全息界面，范围也不靠它标）
        draw.flat(c, radius * 0.9F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.outer(), 0.08F * e));
        Vec3 core = draw.towardCamera(c, 0.6D);
        draw.billboard(core, 0.8F, spin, VfxMode.FLARE, 800, Vfx.argb(tint.core(), 0.7F * e));
        draw.billboard(core, 1.8F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.4F * e));
        if (tier() >= 1) {
            VfxPlasma.corona(draw, RandomSource.create(seed), c, 1.4F, 3, 0.03F, 0.8F * e, tint);
        }
        if (draw.distortionEnabled()) {
            draw.distortFlat(c, radius, 1, 0, 0.3F * e);
        }
    }

    @Override
    public AABB bounds() {
        Vec3 c = center(1F);
        return new AABB(c, c).inflate(radius + 2D, 3D, radius + 2D);
    }
}
