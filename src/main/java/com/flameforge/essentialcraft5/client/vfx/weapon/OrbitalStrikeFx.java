package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.client.vfx.VfxScreen;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 轨道打击（跟着 {@link OrbitalStrikeEntity}）：
 * <ul>
 *   <li>倒数：一道细的等离子光柱从落点直上天空，越近落下越亮；几颗光球顺着光柱往下滑、越滑越快，每颗套一圈细环；
 *       最后四成时间落点上空 8~14 格有电弧游走（预兆），落点地面一团越来越亮的光；</li>
 *   <li>落下：最后 {@value #FALL} 刻一道粗光柱的头从天上砸下来；</li>
 *   <li>落点（收到实体事件 {@link OrbitalStrikeEntity#EVENT_LAND} 才放，熄灭的只淡掉）：爆点、柔波、地面裂纹、火花和余辉往上喷，
 *       光柱余辉收细淡掉，落点周围跳几道电弧，离得近的屏幕闪一下。</li>
 * </ul>
 */
public final class OrbitalStrikeFx implements VfxEffect {

    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
    private static final int FALL = 5;
    private static final int IMPACT = 32;
    private static final int FADE = 6;
    private static final float DROP = 60F;
    private static final int BEADS = 4;

    private final OrbitalStrikeEntity strike;
    private final RandomSource random = RandomSource.create();
    private final int seed = random.nextInt(100000);
    private final double[] arcPath = new double[VfxPaths.jaggedPoints(4) * 3];
    private Vec3 foot;
    private double height;
    private int arcSeed;
    /** 实体没了以后过了几刻；-1 = 还在倒数 */
    private int after = -1;
    private boolean landed;

    public OrbitalStrikeFx(OrbitalStrikeEntity strike) {
        this.strike = strike;
        this.foot = strike.position();
        this.height = columnHeight(foot);
    }

    private static double columnHeight(Vec3 foot) {
        var level = Minecraft.getInstance().level;
        double top = level == null ? 320D : level.getMaxBuildHeight();
        return Mth.clamp(top - foot.y, 8D, 160D);
    }

    @Override
    public boolean tick() {
        if (after < 0) {
            if (strike.isRemoved()) {
                landed = strike.landedOnClient();
                after = 0;
                if (landed) {
                    land();
                }
            } else {
                foot = strike.position();
                if ((strike.tickCount & 1) == 0) {
                    arcSeed = random.nextInt(100000);
                }
                charging();
                return true;
            }
        }
        after++;
        if (landed && after < 10 && (after & 1) == 0) {
            arcSeed = random.nextInt(100000);
        }
        return after < (landed ? IMPACT : FADE);
    }

    private void charging() {
        float rem = strike.remainingTicks(0F);
        float k = progress(rem);
        if (k > 0.5F) {
            VfxPlasma.embers(foot, 0.9D, 0.8F * k, TINT);
        }
    }

    private void land() {
        Vec3 c = foot.add(0D, 0.3D, 0D);
        VfxPlasma.sparks(c, new Vec3(0D, 1D, 0D), 1.3D, 46F, 0.3D, 1.1D, 10, 26, TINT);
        VfxPlasma.embers(foot, 3.5D, 30F, TINT);
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = camera.distanceTo(foot);
        if (d < 20D) {
            VfxScreen.flash(VfxPalette.MAGENTA, 0.14F * (float) (1D - d / 20D), 8);
        }
    }

    private float progress(float rem) {
        int delay = Math.max(1, strike.delayTicks());
        return Mth.clamp(1F - rem / delay, 0F, 1F);
    }

    @Override
    public void render(VfxDraw draw) {
        long t0 = WeaponVfxStats.start();
        float pt = draw.partialTick();
        if (after < 0) {
            renderCharge(draw, strike.getPosition(pt), strike.remainingTicks(pt), draw.time());
        } else if (landed) {
            renderImpact(draw, after + pt);
        } else {
            float k = 1F - (after + pt) / FADE;
            VfxPlasma.beam(draw, foot, foot.add(0D, height, 0D), 0.14F, 0.5F * k, true, seed, TINT);
        }
        WeaponVfxStats.stop(t0);
    }

    private void renderCharge(VfxDraw draw, Vec3 c, float rem, float time) {
        float k = progress(rem);
        Vec3 top = c.add(0D, height, 0D);
        float flick = 0.85F + 0.15F * Mth.sin(time * 1.9F);
        VfxPlasma.beam(draw, c, top, 0.1F + 0.08F * k, (0.45F + 0.5F * k) * flick, true, seed, TINT);
        // 落点地面：一团越来越亮的光（没有亮边）
        draw.flat(c.add(0D, 0.03D, 0D), 0.8F + 1.2F * k, time * 0.02F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.25F + 0.45F * k));
        draw.billboard(c.add(0D, 0.15D, 0D), 0.3F + 0.5F * k, time * 0.05F, VfxMode.FLARE, 900, Vfx.argb(TINT.core(), 0.4F + 0.5F * k));
        // 串珠：顺着光柱往下滑的光球，越近落下滑得越快
        float speed = 0.012F + 0.05F * k * k;
        double span = Math.min(height, 40D);
        for (int i = 0; i < BEADS; i++) {
            float f = frac(i / (float) BEADS - time * speed);
            Vec3 p = c.add(0D, 0.6D + span * f, 0D);
            float a = (0.35F + 0.6F * k) * Math.min(1F, (1F - f) * 4F) * Math.min(1F, f * 6F);
            draw.billboard(p, 0.18F + 0.12F * k, 0F, VfxMode.DOT, 600, Vfx.argb(TINT.core(), a));
            draw.billboard(p, 0.55F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.4F * a));
            if (Vfx.tier() >= 1) {
                draw.flat(p.subtract(0D, 0.02D, 0D), 0.5F + 0.2F * f, 0F, VfxMode.RING, 40, Vfx.argb(TINT.inner(), 0.6F * a));
            }
        }
        // 预兆：落下前空中游走的电弧
        if (k > 0.6F) {
            float a = (k - 0.6F) / 0.4F;
            RandomSource r = RandomSource.create(arcSeed);
            int arcs = Math.max(1, Math.round((1 + 2 * a) * Vfx.detail(c)));
            for (int i = 0; i < arcs; i++) {
                Vec3 from = c.add((r.nextDouble() - 0.5D) * 6D, 8D + r.nextDouble() * 6D, (r.nextDouble() - 0.5D) * 6D);
                Vec3 to = c.add(0D, 4D + r.nextDouble() * 6D, 0D).add((r.nextDouble() - 0.5D) * 1.2D, 0D, (r.nextDouble() - 0.5D) * 1.2D);
                int n = VfxPaths.jagged(r, from, to, 4, 0.22F, arcPath);
                VfxPlasma.forkedArc(draw, r, arcPath, n, 0.05F, 0.75F * a, arcSeed + i, 2, 1.2F, TINT);
            }
        }
        // 落下：粗光柱的头从天上砸下来
        if (rem < FALL) {
            float f = Mth.clamp(rem / FALL, 0F, 1F);
            Vec3 head = c.add(0D, DROP * f * f, 0D);
            VfxPlasma.beam(draw, head, c.add(0D, DROP + 20D, 0D), 0.55F, 0.95F, false, seed + 1, TINT);
            VfxPlasma.burst(draw, head, 1.4F, 1F, 6F, seed & 1023, TINT);
        }
    }

    private void renderImpact(VfxDraw draw, float t) {
        Vec3 c = foot;
        VfxPlasma.burst(draw, draw.towardCamera(c.add(0D, 0.8D, 0D), 1.2D), 3.4F, t, 14F, seed & 1023, TINT);
        if (t < 1.5F) {
            draw.billboard(c.add(0D, 1D, 0D), 3F, 0F, VfxMode.FLARE, 1000, Vfx.argb(TINT.core(), 1F - t / 1.5F));
        }
        VfxPlasma.wave(draw, c, 5.5F, t, 6F, 16F, 1F, TINT);
        float heat = 1F - Mth.clamp(t / IMPACT, 0F, 1F);
        VfxPlasma.groundCracks(draw, seed, c, 0.4F, 4.2F, 9, heat, Math.min(1F, heat * 1.6F), TINT);
        // 光柱余辉：落下那道粗光柱收细、往上淡掉
        float column = 1F - Mth.clamp(t / 9F, 0F, 1F);
        if (column > 0F) {
            VfxPlasma.beam(draw, c, c.add(0D, height, 0D), 0.15F + 0.6F * column, column, true, seed + 1, TINT);
        }
        float arcs = 1F - Mth.clamp(t / 10F, 0F, 1F);
        if (arcs > 0F) {
            RandomSource r = RandomSource.create(arcSeed);
            VfxPlasma.corona(draw, r, c.add(0D, 0.6D, 0D), 3F, 5, 0.04F, arcs, TINT);
            VfxPlasma.groundCrawl(draw, r, c, 4F, 6, 0.035F, arcs, TINT);
        }
        float hit = 1F - Mth.clamp(t / 8F, 0F, 1F);
        if (hit > 0F) {
            draw.distortBillboard(c.add(0D, 1D, 0D), 3F, 1, 0, 0.6F * hit);
        }
    }

    private static float frac(float x) {
        return x - Mth.floor(x);
    }

    @Override
    public AABB bounds() {
        return new AABB(foot, foot.add(0D, Math.max(height, DROP + 20D), 0D)).inflate(6D, 1D, 6D);
    }
}
