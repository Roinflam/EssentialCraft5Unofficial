package com.flameforge.essentialcraft5.client.vfx.resolve;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxCracks;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.client.vfx.warn.BossWarningClient;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 天灾之心「四灾归心」落下（{@code FX_CONVERGE}）：外圈收到安全圈的那一刻，圈外整片炸开。
 * <ul>
 *   <li>顿帧两刻：圈外地面的裂纹一下子白热；</li>
 *   <li>炸开：圈外地里一根接一根冒出主灾颜色的等离子柱（从安全圈边往外一圈圈冒），一道没有亮边的冲击波从安全圈边往外推、越远越淡，
 *       裂纹慢慢冷下去；安全圈里飘起金色的光点，表示「站在这里没事」。</li>
 * </ul>
 * 地上不画安全圈、场地的边（圆环、圆盘、圈边电弧）：范围在预警阶段已经标过，落下时再画一圈硬边像是界面。
 * color = 安全圈色，color2 = 主灾颜色（圈外）。
 */
public final class ConvergeFx extends ResolveFx {

    /** 设计数量下的火柱数（高档、近处） */
    private static final int COLUMNS = 28;

    private final VfxPlasma.Tint danger;
    private final float safe;
    private final float arena;
    private final double[] colX = new double[COLUMNS];
    private final double[] colZ = new double[COLUMNS];
    private final float[] colAt = new float[COLUMNS];
    private final float[] colH = new float[COLUMNS];
    private final int columns;
    /** 圈外裂纹的形状种子（整段不变） */
    private final long crackSeed;
    /** 预警交过来的裂纹网：有就炸亮这张（顿帧白热盖掉预警的红，冷下来换成主灾色），不另画一批 */
    @Nullable
    private final VfxCracks inherited;

    ConvergeFx(S2CBossWarningPacket packet, ClientLevel level) {
        super(packet, level, 40, 2);
        this.danger = VfxPlasma.tint(packet.color2() == 0 ? VfxPalette.DANGER : packet.color2());
        this.safe = Math.max(1F, packet.size());
        this.arena = Math.max(safe + 1F, packet.size2());
        this.columns = Math.max(6, Math.min(COLUMNS, Math.round(COLUMNS * Vfx.detail(origin))));
        RandomSource r = RandomSource.create(packet.owner() * 31L + Math.round(packet.x()));
        this.crackSeed = r.nextLong();
        this.inherited = BossWarningClient.claimCracks(packet.owner(), origin);
        for (int i = 0; i < columns; i++) {
            double ang = (i + r.nextDouble() * 0.7D) * Mth.TWO_PI / columns;
            // 离安全圈越远越晚冒
            double f = Math.sqrt(r.nextDouble());
            double d = safe + 1D + (arena - safe - 1.5D) * f;
            colX[i] = origin.x + Math.cos(ang) * d;
            colZ[i] = origin.z + Math.sin(ang) * d;
            colAt[i] = (float) (f * 6D);
            colH[i] = 6F + r.nextFloat() * 6F;
        }
    }

    @Override
    protected void start() {
        // 服务端另外发了中等震动（SHAKE），这里按距离补一下、再闪一下
        shake(origin, arena, 0.6F, 10);
        punch(origin, arena, 1F, danger.glow(), 0.6F);
        flashNear(origin, arena + 8D, danger.glow(), 0.2F, 16);
    }

    @Override
    protected void release() {
        for (int i = 0; i < columns; i += 2) {
            Vec3 p = new Vec3(colX[i], origin.y + 0.2D, colZ[i]);
            VfxPlasma.sparks(p, new Vec3(0D, 1D, 0D), 0.5D, 5F, 0.3D, 0.8D, 10, 22, danger);
        }
        // 安全圈里飘起金色的光点
        VfxPlasma.embers(origin, safe, 40F, tint);
    }

    @Override
    protected void tickFx(int t) {
        if (t > hold && t < 16 && (t & 1) == 1) {
            double ang = random.nextDouble() * Mth.TWO_PI;
            double d = safe + random.nextDouble() * (arena - safe);
            VfxPlasma.embers(new Vec3(origin.x + Math.cos(ang) * d, origin.y, origin.z + Math.sin(ang) * d), 2D, 6F, danger);
        }
    }

    @Override
    protected void renderFx(VfxDraw draw, float t, float alpha) {
        float tt = after(t);
        // 圈外的裂纹：顿帧时白热，之后慢慢冷（道数按场地周长给，铺在安全圈外到场地边之间，长短不一、连不成圈）
        float heat = holding(t) ? 1F : 0.85F * (1F - Mth.clamp(tt / (life - hold), 0F, 1F));
        if (heat > 0.01F) {
            int n = Math.max(10, Math.round(arena * 1.6F));
            if (inherited != null) {
                inherited.render(draw, inherited::length, (i, behind) -> heat, false, Math.min(1F, heat * 1.6F + 0.2F), danger);
            } else {
                VfxPlasma.groundCracks(draw, crackSeed, origin, safe + 0.6F, arena, n, heat, Math.min(1F, heat * 1.6F + 0.2F), danger);
            }
        }
        // 一根根冒出来的火柱
        for (int i = 0; i < columns; i++) {
            float ct = tt - colAt[i];
            if (ct < 0F) {
                continue;
            }
            float col = Vfx.pulse(ct / 16F, 0.1F);
            if (col <= 0.01F) {
                continue;
            }
            Vec3 bottom = new Vec3(colX[i], origin.y + 0.05D, colZ[i]);
            Vec3 top = bottom.add(0D, colH[i] * Vfx.easeOutExpo(ct / 2.5F), 0D);
            VfxPlasma.beam(draw, bottom, top, 0.9F * col, col, true, seed + i * 5, danger);
            if (ct < 6F) {
                VfxPlasma.burst(draw, bottom.add(0D, 0.4D, 0D), 2F, ct, 6F, (i * 37) & 1023, danger);
            }
        }
        // 冲击波：从安全圈边往外推，越远越淡，推到场地边已经没了
        float wt = Math.max(0F, tt);
        if (!holding(t)) {
            float span = arena - safe;
            float k = Mth.clamp(wt / 16F, 0F, 1F);
            float f = Vfx.easeOutExpo(wt / 9F);
            float r = safe + span * f;
            float a = (1F - f * f) * (1F - k);
            if (a > 0.01F) {
                float half = r / 0.7F;
                int band = Mth.clamp(Math.round(2.4F / half * 1000F), 40, 400);
                draw.flat(origin, half, 0F, VfxMode.WAVE, band, Vfx.argb(danger.glow(), 0.9F * a));
                if (tier() >= 1) {
                    draw.flat(origin, half * 0.97F, 0F, VfxMode.WAVE, Math.min(700, band * 2), Vfx.argb(danger.outer(), 0.5F * a));
                }
                draw.distortFlat(origin, r * 1.05F, 0, Mth.clamp(Math.round(2.4F / r * 1000F), 30, 300), 0.5F * a);
            }
        }
    }

    @Override
    public AABB bounds() {
        return new AABB(origin, origin).inflate(arena + 4D, 2D, arena + 4D).expandTowards(0D, 14D, 0D);
    }
}
