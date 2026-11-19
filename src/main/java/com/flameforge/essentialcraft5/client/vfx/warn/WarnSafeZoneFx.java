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
 * 收缩安全圈（四灾归心）：地上不画圈。一开始整片危险区就浮出发丝细的暗裂纹（从场边伸到安全圈边上，没裂的那块就是安全区），
 * 安全区里落几根慢慢飘的金色天光和光雨（不排在边上，远看是「那块地被照亮了」）；一道热潮顺着裂纹从场边往里烧，
 * 每道裂纹上的火头错开 0~1.5 格（参差的一线火头，不是一道弧）。前沿先快后慢（{@link #front}），最后半秒贴着安全区边上压着，
 * 边界最清楚的时候正好是要判断站位的时候。最后 10 刻整张网冲到白热、沿缝喷火花，安全区的光雨反过来往上飘；最后 3 刻压暗再落。
 * 落下时整张网交给结算（{@code ConvergeFx}）炸亮；天灾之心本体同时蓄力（日阵金，不冒电）。
 * 本机提示：站在危险区里最后 20 刻脚边冒余烬、心跳，踏进安全区响一声。
 */
public final class WarnSafeZoneFx extends WarnFx {

    private final float safe;
    private final float outer;
    private final VfxPlasma.Tint danger;
    @Nullable
    private VfxCracks cracks;
    private float[] jag;
    private boolean handedOff;

    public WarnSafeZoneFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level);
        this.safe = Math.max(0.5F, packet.size());
        this.outer = Math.max(safe + 0.5F, packet.size2());
        this.danger = VfxPlasma.tint(packet.color2());
    }

    /** 前沿：先快后慢，终点比安全圈往里 0.3 格（标出来的危险区宁大勿小） */
    private float front(float k) {
        return safe - 0.3F + (outer - safe + 0.3F) * (float) Math.pow(1F - k, 1.6D);
    }

    private VfxCracks cracks(Vec3 c) {
        if (cracks == null) {
            // 低档少一些、每道节数减半（全场最重的一招：高档约 1300 个面片）
            float lod = Vfx.tier() == 0 ? 0.6F : Math.max(0.7F, Vfx.detail(c));
            int count = Mth.clamp(Math.round(outer * 0.95F * lod), 12, 36);
            cracks = VfxCracks.inward(seed * 131L + 3L, c, safe - 0.05F, 0.25F, outer, count, Vfx.tier() == 0 ? 3 : 4);
            jag = new float[cracks.size()];
            RandomSource r = RandomSource.create(seed);
            for (int i = 0; i < jag.length; i++) {
                jag[i] = r.nextFloat() * 1.5F;
            }
        }
        cracks.moveTo(c);
        return cracks;
    }

    /** 第 i 道裂纹烧到哪（离场边那头几格）：主裂纹的头跟着前沿走（每道错开 0~1.5 格，参差的一线火头），侧枝跟着主裂纹 */
    private float head(VfxCracks net, int i, float front) {
        int half = net.size() / 2;
        if (!net.isBranch(i)) {
            return net.lengthAtRadius(i, front + jag[i]);
        }
        float past = head(net, i - half, front) - net.forkOffset(i);
        return Mth.clamp(past, 0F, net.length(i));
    }

    @Override
    protected int localZone(Vec3 feet) {
        Vec3 c = center(1F);
        double d = Math.hypot(feet.x - c.x, feet.z - c.z);
        return d < safe - 0.3D ? 0 : 1;
    }

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
        float left = duration - age;
        boolean late = left < 10F;
        float front = front(k);
        // 安全区的光雨：平时往下落，收紧段反过来往上飘（像在聚光）
        VfxPlasma.lightRain(c, safe - 0.3F, 2.5D, 5D, 4F, late ? 0.03F : -0.04F, tint);
        VfxCracks net = cracks(c);
        int half = net.size() / 2;
        // 火头附近冒余烬，后方稀疏
        int n = Vfx.count(Math.min(14F, front * 0.5F), c);
        for (int i = 0; i < n; i++) {
            int crack = random.nextInt(half);
            Vec3 p = net.pointAt(crack, head(net, crack, front) / Math.max(0.1F, net.length(crack)));
            VfxPlasma.embers(p, 0.4D, 1F, danger);
        }
        VfxPlasma.embers(net.randomPoint(random), 0.5D, 1.5F, danger);
        if (late && (age & 1) == 0) {
            for (int i = 0; i < 8; i++) {
                VfxPlasma.sparks(net.randomPoint(random).add(0D, 0.05D, 0D), new Vec3(0D, 1D, 0D), 0.4D, 1F, 0.15D, 0.4D, 8, 14, danger);
            }
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
        float k = progress(t);
        float left = left(t);
        float pulse = VfxPlasma.warningPulse(left, t);
        float late = left < 10F ? Math.min(1F, (10F - left) / 7F) : 0F;
        // 安全区：5 根慢慢飘的金色天光
        if (t < duration) {
            VfxPlasma.safeLight(draw, owner() * 17L + 5L, c, safe * 0.7F, 5, 7F, t, a * pulse, tint);
        }
        if (!handedOff) {
            VfxCracks net = cracks(c);
            float front = front(k);
            // 头后 4 格通红，再往外冷到暗红但不灭；头前面是发丝细的暗纹；收紧段整张网冲到白热；落下后没被接走就白热一下退掉
            float hot = t >= duration ? 1F : late;
            net.render(draw, i -> head(net, i, front),
                    (i, behind) -> behind < 0F ? Math.max(0.2F, hot) : Math.max(hot, behind < 4F ? Mth.lerp(behind / 4F, 0.85F, 0.45F) : 0.35F),
                    true, a * Math.min(1F, t / 6F) * (t >= duration ? 1F : pulse), danger);
            if (t < duration) {
                for (int i = 0; i < net.size() / 2; i++) {
                    float h = head(net, i, front);
                    if (h > 0.05F && h < net.length(i) - 0.05F) {
                        draw.billboard(net.pointAt(i, h / net.length(i)).add(0D, 0.1D, 0D), 0.2F, 0F, VfxMode.DOT, 650,
                                Vfx.argb(danger.core(), 0.8F * a * pulse));
                    }
                }
            }
        }
        // 天灾之心本体的蓄力（日阵金，不冒电）
        if (t < duration) {
            renderCharge(draw, ownerPoint(pt, 0.5F), k, left, t, a, VfxPlasma.SOLAR, false, 1.3F);
        }
    }

    /** 落下后很快退掉：裂纹网归结算画，安全区里的光点也归结算那边 */
    @Override
    protected int linger() {
        return 3;
    }

    @Override
    public AABB bounds() {
        return box(outer + 2D, 8D);
    }
}
