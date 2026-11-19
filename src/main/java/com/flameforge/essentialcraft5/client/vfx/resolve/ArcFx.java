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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 首领的电弧：
 * <ul>
 *   <li>{@code FX_ARC} 一道打到人的电弧（矩阵电弧每一跳、聚能魔像的电弧鞭 / 护盾电弧）：起点一颗星芒，第 0 刻从起点冲到落点，
 *       前几刻噼啪变形，一团能量顺着电弧冲过去，打到的地方炸一个爆点、溅火花；收掉时断成几截往外跳着淡掉；
 *       亮度照真闪电（亮、暗一下、再亮、淡掉）；打在自己身上时冲击帧 + 速度线；</li>
 *   <li>{@code FX_LINK} 心智钉连线（持续）：钉子到矩阵之影胸口一道细电弧，两头跟着实体走，隔一会儿有一团能量从钉子流向它
 *       （「它在从钉子那里吸东西」）；钉子碎了 / 没了就断。</li>
 * </ul>
 * 魔像的两种电弧本来就是客户端自己触发的，用 {@link #local} 直接建，不走网络。
 */
public final class ArcFx extends ResolveFx {

    private static final int DEPTH = 5;
    /** 连线上能量团多久流一次（刻） */
    private static final int LINK_PERIOD = 24;
    /** 一次性电弧从第几刻起散架 */
    private static final float SHATTER_AT = 5F;

    private final boolean link;
    private final float width;
    private final int branches;
    private final double[] path = new double[VfxPaths.jaggedPoints(DEPTH) * 3];
    private final int burstSeed = random.nextInt(1024);

    ArcFx(S2CBossWarningPacket packet, ClientLevel level) {
        this(packet, level, 0.08F, 6);
    }

    private ArcFx(S2CBossWarningPacket packet, ClientLevel level, float width, int branches) {
        super(packet, level, 16, packet.style() == S2CBossWarningPacket.FX_LINK ? 0 : 1);
        this.link = packet.style() == S2CBossWarningPacket.FX_LINK;
        // 连线比电弧细，但钉子离它 6~9 格，太细在场边就看不出连着
        this.width = link ? 0.045F : width;
        this.branches = link ? 1 : branches;
    }

    /**
     * 客户端自己触发的电弧（聚能魔像的电弧鞭、护盾电弧）：from → to，颜色 rgb。
     *
     * @param width    等离子芯的半宽（格）
     * @param branches 分叉数
     */
    public static ArcFx local(ClientLevel level, Entity owner, Vec3 from, Vec3 to, int rgb, float width, int branches) {
        Vec3 d = to.subtract(from);
        S2CBossWarningPacket packet = S2CBossWarningPacket.resolve(S2CBossWarningPacket.FX_ARC, owner.getId(), -1, from.x, from.y, from.z, 0F, 0F,
                (float) d.x, (float) d.y, (float) d.z, 0, 0, rgb, 0);
        ArcFx fx = new ArcFx(packet, level, width, branches);
        fx.begin();
        return fx;
    }

    @Override
    protected void start() {
        if (link) {
            return;
        }
        Vec3 to = origin.add(dir);
        VfxPlasma.sparks(to, dir.scale(-1D), 1.3D, 18F * Math.min(1F, width / 0.08F), 0.15D, 0.45D, 6, 14, tint);
        if (cameraDistance(to) < 3D) {
            // 打在自己身上
            shake(to, 2D, 0.25F, 4);
        }
    }

    @Override
    protected void renderFx(VfxDraw draw, float t, float alpha) {
        if (link) {
            renderLink(draw, t, alpha);
            return;
        }
        Vec3 from = origin;
        Vec3 to = origin.add(dir);
        float a = VfxPlasma.strokeAlpha(t, 9F);
        if (a > 0.01F) {
            // 第 0 刻从起点冲到落点（顿帧那一刻打中）；到第 SHATTER_AT 刻每刻重新劈一次；之后断成几截往外跳着淡掉
            RandomSource r = RandomSource.create(t < SHATTER_AT ? seed + (int) t * 977L : seed);
            int n = VfxPaths.jagged(r, from, to, DEPTH, 0.2F, path);
            float len = (float) dir.length();
            if (t < SHATTER_AT) {
                float head = t < 1F ? 0.2F + 0.8F * Vfx.easeOutExpo(t * 1.4F) : 1F;
                VfxPlasma.forkedArc(draw, r, path, n, head, width, a, seed, branches, len * 0.18F, tint);
                if (head >= 1F) {
                    VfxPlasma.pulse(draw, path, n, (t - 1F) / 2.5F, width * 4F, a, tint);
                }
            } else {
                float jump = (t - SHATTER_AT) * 0.05F * Math.max(1F, len * 0.12F);
                VfxPlasma.shatteredArc(draw, path, n, 4, 0.3F, jump, width, a, seed + 31, tint);
            }
        }
        float k = 1F - Mth.clamp(t / 6F, 0F, 1F);
        if (k > 0F) {
            draw.billboard(draw.towardCamera(from, 0.4D), 0.25F + width * 6F, t * 0.2F, VfxMode.FLARE, 900, Vfx.argb(tint.core(), k));
        }
        VfxPlasma.burst(draw, draw.towardCamera(to, 0.6D), 0.9F + width * 6F, after(t), 8F, burstSeed, tint);
    }

    private void renderLink(VfxDraw draw, float t, float alpha) {
        Entity spike = ownerEntity();
        Entity shade = followEntity();
        if (spike == null || shade == null) {
            return;
        }
        float pt = draw.partialTick();
        Vec3 from = spike.getPosition(pt).add(0D, spike.getBbHeight() * 0.5D, 0D);
        Vec3 to = shade.getPosition(pt).add(0D, shade.getBbHeight() * 0.6D, 0D);
        float a = alpha * Mth.clamp(t / 6F, 0F, 1F) * (0.75F + 0.25F * Mth.sin(t * 0.5F));
        RandomSource r = RandomSource.create(seed);
        int n = VfxPaths.jagged(r, from, to, DEPTH, 0.12F, path);
        VfxPlasma.arc(draw, path, n, width, 0.85F * a, seed, tint);
        float f = (t % LINK_PERIOD) / (LINK_PERIOD * 0.6F);
        if (f <= 1F) {
            VfxPlasma.pulse(draw, path, n, f, 0.18F, a, tint);
        }
        draw.billboard(draw.towardCamera(from, 0.3D), 0.35F, 0F, VfxMode.DOT, 300, Vfx.argb(tint.glow(), 0.6F * a));
        draw.billboard(draw.towardCamera(to, 0.6D), 0.5F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.35F * a));
    }

    @Override
    public AABB bounds() {
        if (link) {
            Entity spike = ownerEntity();
            Entity shade = followEntity();
            if (spike != null && shade != null) {
                return spike.getBoundingBox().minmax(shade.getBoundingBox()).inflate(2D);
            }
            return new AABB(origin, origin).inflate(16D);
        }
        Vec3 to = origin.add(dir);
        return new AABB(origin.x, origin.y, origin.z, to.x, to.y, to.z).inflate(2D);
    }
}
