package com.flameforge.essentialcraft5.client.vfx.warn;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxCracks;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.entity.boss.BossWarnings;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemEntity;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 圆形 / 扇形预警（灾柱反噬、坠身、砸地、镇压、失序之柱、落石落点、正午、暗袭落点、吐息）：地上不画圈，范围里的地面裂开。
 * <ul>
 *   <li>开头四分之一（最多 5 刻）：从圆心往外劈出一张裂纹网，每道错开一点起步、长到头时尖上弹两颗火花「啪」地定住；
 *       裂纹尖落在半径 ~ 半径 + 0.3（宁大勿小），裂纹尖到哪就打到哪。扇形只在扇形里劈，头尾两道贴着两条边走，边本身不画线。</li>
 *   <li>之后裂纹越烧越热、沿缝冒余烬；电系（魔像、矩阵之影）每道裂纹上隔几刻跑一团电流。</li>
 *   <li>最后 10 刻缝里冲到白热、越闪越快，裂纹尖往上喷火花；最后 3 刻整张网压暗（「一暗就落」，见 {@link VfxPlasma#warningPulse}）。</li>
 *   <li>落下：结算（{@code ImpactFx}）把这张网接过去炸亮（{@link #handOff}）；没人接的话自己白热一下、{@link #linger()} 刻里退掉。</li>
 * </ul>
 * 首领身上同时蓄力（{@link #renderCharge}，首领离圈心不远时才画）；带柱子的（灾柱反噬）蓄力画在柱顶。
 * 本机玩家站在范围里时脚边冒余烬、最后 20 刻每 5 刻一下心跳（{@link #localZone}）。
 */
public final class WarnCircleFx extends WarnFx {

    private final float radius;
    private final boolean sector;
    private final Vec3 dir;
    private final float half;
    private final boolean pillar;
    private final float height;
    /** 电系首领（魔像、矩阵之影）：裂纹上跑电流、蓄力带电晕 */
    private final boolean electric;
    private final boolean golem;
    /** 裂纹网（第一次用时建），交给结算后不再画 */
    @Nullable
    private VfxCracks cracks;
    private float[] delay;
    private boolean[] grown;
    private boolean handedOff;
    private final double[] path = new double[96 * 3];

    public WarnCircleFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level);
        this.radius = Math.max(0.3F, packet.size());
        this.sector = packet.has(S2CBossWarningPacket.FLAG_SECTOR);
        Vec3 d = new Vec3(packet.dirX(), 0D, packet.dirZ());
        this.dir = d.lengthSqr() < 1.0E-8D ? new Vec3(1D, 0D, 0D) : d.normalize();
        this.half = sector ? packet.size2() : Mth.PI;
        this.pillar = packet.has(S2CBossWarningPacket.FLAG_PILLAR);
        this.height = pillar ? packet.size2() : 0F;
        int rgb = packet.color() & 0xFFFFFF;
        this.golem = level.getEntity(packet.owner()) instanceof MRUGolemEntity;
        this.electric = golem || rgb == BossWarnings.MRU_BLUE || rgb == BossWarnings.MATRIX;
    }

    /** 没被结算接走时：白热一下、几刻里退掉 */
    @Override
    protected int linger() {
        return 4;
    }

    /** 首领身上蓄力的位置：魔像在胸口核心，别的在身体中间；首领不在客户端、或者离圈心太远（隔空点的落点）时不画 */
    @Nullable
    private Vec3 chargeAt(float pt, Vec3 c) {
        Vec3 p = ownerPoint(pt, golem ? 0.6F : 0.5F);
        if (p == null) {
            return null;
        }
        double dx = p.x - c.x;
        double dz = p.z - c.z;
        return dx * dx + dz * dz <= (radius + 2D) * (radius + 2D) ? p : null;
    }

    private VfxPlasma.Tint chargeTint() {
        return golem ? VfxPlasma.MRU : tint;
    }

    private VfxCracks cracks(Vec3 c) {
        if (cracks == null) {
            float span = sector ? 2F * half : Mth.TWO_PI;
            float a0 = sector ? (float) Math.atan2(dir.z, dir.x) - half : 0F;
            float lod = Math.max(0.6F, Vfx.detail(c));
            int count = sector
                    ? Mth.clamp(Math.round(radius * span / 1.6F * lod) + 1, 3, 16)
                    : Mth.clamp(Math.round(radius * 2.6F * lod), 6, radius > 6F ? 20 : 14);
            int depth = (radius > 6F ? 4 : 3) - (Vfx.tier() == 0 ? 1 : 0);
            cracks = VfxCracks.radial(seed * 31L + 7L, c, Math.min(0.4F, radius * 0.2F), radius, 0.3F, count, a0, span, depth, 1);
            delay = new float[cracks.size()];
            grown = new boolean[cracks.size()];
            RandomSource r = RandomSource.create(seed);
            for (int i = 0; i < count; i++) {
                delay[i] = r.nextFloat() * Math.min(1.5F, growTicks() * 0.3F);
            }
        }
        cracks.moveTo(c);
        return cracks;
    }

    /** 裂纹从圆心往外长满要几刻（短预警按比例缩） */
    private float growTicks() {
        return Math.max(1F, Math.min(5F, duration * 0.25F));
    }

    /** 第 i 道裂纹长到哪（格）：主裂纹按自己的起步时间长，侧枝等主裂纹长过岔口才接着长 */
    private float head(VfxCracks net, int i, float t) {
        if (net.isBranch(i)) {
            return net.branchHead(i, head(net, net.parent(i), t));
        }
        return Vfx.easeOutExpo(Mth.clamp((t - delay[i]) / growTicks(), 0F, 1F)) * net.length(i);
    }

    /** 裂纹的热度：前段慢慢热，最后 10 刻冲到 1（缝里发白）；落下后没被接走就是白热 */
    private float crackHeat(float k, float left, float t) {
        if (t >= duration) {
            return 1F;
        }
        float late = Math.min(10F, duration * 0.5F);
        return left < late ? Math.min(1F, 0.55F + 0.45F * (late - left) / (late * 0.7F)) : 0.25F + 0.6F * k;
    }

    @Override
    protected int localZone(Vec3 feet) {
        Vec3 c = center(1F);
        double dx = feet.x - c.x;
        double dz = feet.z - c.z;
        double r = radius + 0.3D;
        if (dx * dx + dz * dz > r * r) {
            return -1;
        }
        if (sector) {
            double len = Math.sqrt(dx * dx + dz * dz);
            // 扇形：夹角在半角以内（边上同样往外多算 0.3 格）
            if (len > 0.3D && (dx * dir.x + dz * dir.z) / len < Math.cos(half + Math.min(0.5D, 0.3D / len))) {
                return -1;
            }
        }
        return 1;
    }

    /** 结算接手裂纹网：圆心对得上（1.5 格内）就交出去，自己不再画 */
    @Override
    @Nullable
    VfxCracks handOff(Vec3 at) {
        if (cracks == null || handedOff || cancelled()) {
            return null;
        }
        Vec3 c = cracks.center();
        if ((c.x - at.x) * (c.x - at.x) + (c.z - at.z) * (c.z - at.z) > 2.25D) {
            return null;
        }
        handedOff = true;
        return cracks;
    }

    @Override
    protected void tickWarning(float k) {
        Vec3 c = center(1F);
        VfxCracks net = cracks(c);
        float t = age;
        float left = duration - t;
        Vec3 up = new Vec3(0D, 1D, 0D);
        for (int i = 0; i < net.size(); i++) {
            // 每道长到头那一刻，尖上弹两颗火花，「啪」地定住
            if (!grown[i] && head(net, i, t) >= net.length(i) * 0.98F) {
                grown[i] = true;
                VfxPlasma.sparks(net.tip(i).add(0D, 0.05D, 0D), up, 0.35D, 2F, 0.12D, 0.3D, 6, 10, tint);
            }
        }
        float area = Mth.clamp(radius * radius / 12F * (sector ? half / Mth.PI : 1F), 1F, 4F);
        VfxPlasma.embers(net.randomPoint(random), 0.2D, 1.5F * (0.3F + k) * area, tint);
        if (left < 10F && (age & 1) == 0) {
            int mains = mains(net);
            int step = Math.max(1, mains / 10);
            for (int i = (age >> 1) % step; i < mains; i += step) {
                VfxPlasma.sparks(net.tip(i).add(0D, 0.05D, 0D), up, 0.4D, 0.6F, 0.15D, 0.35D, 8, 14, tint);
            }
        }
    }

    /** 主裂纹有几道（建网时主裂纹排在前面） */
    private static int mains(VfxCracks net) {
        int n = 0;
        while (n < net.size() && !net.isBranch(n)) {
            n++;
        }
        return n;
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
        float k = progress(t);
        float left = left(t);
        float pulse = t >= duration ? 1F : VfxPlasma.warningPulse(left, t);
        if (!handedOff) {
            VfxCracks net = cracks(c);
            float heat = crackHeat(k, left, t);
            net.render(draw, i -> head(net, i, t), (i, behind) -> heat, false, a * pulse, tint);
            // 电系：每道主裂纹隔 6 刻跑一团电流，从中心冲到尖上
            if (electric && Vfx.tier() >= 1 && t < duration) {
                int mains = mains(net);
                for (int i = 0; i < mains; i++) {
                    float f = ((t + i * 2.3F) % 6F) / 6F;
                    if (head(net, i, t) >= net.length(i) * 0.98F && f < 0.8F) {
                        int n = net.path(i, path);
                        VfxPlasma.pulse(draw, path, n, f / 0.8F, 0.08F, 0.8F * a * pulse, tint);
                    }
                }
            }
        }
        if (t >= duration) {
            return;
        }
        if (pillar && height > 0F) {
            renderPillarTop(draw, c.add(0D, height + 0.2D, 0D), k, t, a);
        } else {
            renderCharge(draw, chargeAt(pt, c), k, left, t, a, chargeTint(), electric, electric ? 1F : 0.8F);
        }
    }

    /** 柱顶聚能（灾柱反噬） */
    private void renderPillarTop(VfxDraw draw, Vec3 top, float k, float t, float a) {
        draw.billboard(top, 0.3F + 1.2F * k, t * 0.1F, VfxMode.FLARE, 900, Vfx.argb(tint.core(), (0.3F + 0.6F * k) * a));
        draw.billboard(top, 0.8F + 1.8F * k, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.45F * k * a));
        VfxPlasma.corona(draw, RandomSource.create(seed + 17L), top, 0.8F + 1.2F * k, 1 + Math.round(3F * k), 0.035F, (0.5F + 0.5F * k) * a, tint);
        if (k > 0.7F) {
            float pre = (k - 0.7F) / 0.3F;
            draw.billboard(top.x, top.y, top.z, 2F * pre, 2F * pre, t * 0.3F, VfxMode.BURST, 19, Vfx.argb(tint.inner(), 0.75F * pre * a), false);
        }
    }

    @Override
    public AABB bounds() {
        return box(radius + 2D, pillar ? height + 4D : 6D);
    }
}
