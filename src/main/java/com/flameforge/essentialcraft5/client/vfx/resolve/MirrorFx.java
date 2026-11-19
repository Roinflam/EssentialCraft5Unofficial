package com.flameforge.essentialcraft5.client.vfx.resolve;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 矩阵之影的镜影：
 * <ul>
 *   <li>{@code FX_MIRROR} 现身：脚下一道柔波扩开（没有亮边，地上不留圈），两道扫描环从脚扫到头，一道竖着的等离子光从身上闪过，碎片从四周吸进胸口；
 *       每个镜影和它自己原来站的地方同时闪（换位以后分不清哪个是真身）；</li>
 *   <li>{@code FX_SHATTER} 碎裂（镜影被打碎 / 到时间散掉，心智钉碎掉）：胸口爆点，碎片往四周溅、落下，地上一道柔波；size2 = 规模。</li>
 * </ul>
 */
public final class MirrorFx extends ResolveFx {

    private final boolean shatter;
    private final float height;
    private final float scale;
    private final int burstSeed = random.nextInt(1024);

    MirrorFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level, packet.style() == S2CBossWarningPacket.FX_SHATTER ? 18 : 20, packet.style() == S2CBossWarningPacket.FX_SHATTER ? 1 : 0);
        this.shatter = packet.style() == S2CBossWarningPacket.FX_SHATTER;
        this.height = Mth.clamp(packet.size(), 0.5F, 6F);
        this.scale = shatter ? Mth.clamp(packet.size2(), 0.1F, 2F) : 1F;
    }

    private Vec3 chest() {
        return origin.add(0D, height * 0.55D, 0D);
    }

    @Override
    protected void start() {
        Vec3 c = chest();
        if (shatter) {
            shake(origin, 2D, 0.22F * scale, 4);
            return;
        }
        // 碎片从四周吸进来
        int n = Vfx.count(26F, c);
        for (int i = 0; i < n; i++) {
            Vec3 d = VfxPlasma.randomDir(random);
            double r = 1.4D + random.nextDouble() * 0.8D;
            int life = 7 + random.nextInt(4);
            VfxParticles.spawn(c.x + d.x * r, c.y + d.y * r * 1.3D, c.z + d.z * r, -d.x * r / life, -d.y * r * 1.3D / life, -d.z * r / life, life,
                    0.06F, 0.03F, Vfx.argb(tint.glow(), 0F), Vfx.argb(tint.core(), 1F), VfxMode.SPARK, 0, 1F, 0F, 1.8F, 0F);
        }
    }

    @Override
    protected void release() {
        Vec3 c = chest();
        // 碎片往外溅、落下
        int n = Vfx.count(44F * scale, c);
        for (int i = 0; i < n; i++) {
            Vec3 d = VfxPlasma.randomDir(random);
            double s = (0.18D + random.nextDouble() * 0.4D) * (0.6D + 0.4D * scale);
            VfxParticles.spawn(c.x + d.x * 0.3D, c.y + d.y * 0.5D, c.z + d.z * 0.3D, d.x * s, d.y * s + 0.12D, d.z * s, 12 + random.nextInt(14),
                    0.08F, 0.03F, Vfx.argb(tint.core(), 1F), Vfx.argb(tint.glow(), 0F), VfxMode.SPARK, 0, 0.9F, 0.035F, 1.4F, 0.3F);
        }
        VfxPlasma.embers(origin, 0.8D * scale, 12F * scale, tint);
    }

    @Override
    protected void renderFx(VfxDraw draw, float t, float alpha) {
        Vec3 c = chest();
        if (shatter) {
            float tt = after(t);
            if (holding(t)) {
                draw.billboard(draw.towardCamera(c, 0.8D), 1.2F * scale + 0.3F, 0F, VfxMode.FLARE, 1000, Vfx.argb(tint.core(), 1F));
            }
            VfxPlasma.burst(draw, draw.towardCamera(c, 0.8D), 1.8F * scale + 0.4F, tt, 9F, burstSeed, tint);
            // 地上一道没有亮边、越远越淡的柔波（不画硬边的环）
            VfxPlasma.wave(draw, origin.add(0D, 0.05D, 0D), 2.2F * (0.5F + 0.5F * scale), tt, 4F, 10F, 0.7F, tint);
            return;
        }
        // 脚下扩开的柔波 + 一小团柔光
        Vec3 feet = origin.add(0D, 0.05D, 0D);
        VfxPlasma.wave(draw, feet, 2.2F, t, 5F, 12F, 0.7F, tint);
        float k = 1F - Mth.clamp(t / 12F, 0F, 1F);
        if (k > 0F) {
            draw.flat(origin, 1.4F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.4F * k));
        }
        // 两道扫描环：一道往上、一道晚两刻跟上
        for (int i = 0; i < 2; i++) {
            float st = (t - i * 2F) / 7F;
            if (st < 0F || st > 1F) {
                continue;
            }
            Vec3 at = origin.add(0D, height * Vfx.easeOutCubic(st), 0D);
            float a = Vfx.pulse(st, 0.2F);
            draw.flat(at, 0.75F, 0F, VfxMode.RING, 60, Vfx.argb(tint.core(), 0.9F * a));
            draw.flat(at, 0.85F, 0F, VfxMode.RING, 200, Vfx.argb(tint.glow(), 0.4F * a));
        }
        // 竖着闪过的等离子光
        float flash = Vfx.pulse(t / 9F, 0.2F);
        if (flash > 0.01F) {
            Vec3 head = origin.add(0D, height + 0.6D, 0D);
            draw.strip(feet, head, 0.7F * flash, 0.3F * flash, Vfx.argb(tint.glow(), 0.4F * flash), Vfx.argb(tint.glow(), 0F), VfxMode.STRIP, 50, 0F, 1F);
            draw.strip(feet, head, 0.22F * flash, 0.08F, Vfx.argb(tint.core(), flash), Vfx.argb(tint.inner(), 0F), VfxMode.PLASMA, seed & 1023, 0F,
                    height);
        }
        if (t >= 7F) {
            VfxPlasma.burst(draw, draw.towardCamera(c, 0.8D), 1.3F, t - 7F, 7F, burstSeed, tint);
        }
    }

    @Override
    public AABB bounds() {
        return new AABB(origin, origin).inflate(3D, 1D, 3D).expandTowards(0D, height + 2D, 0D);
    }
}
