package com.flameforge.essentialcraft5.client.vfx.fx;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.client.vfx.VfxScreen;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 天灾之心「灾柱反噬」的特效（等离子画风样片）：灾柱脚下一个半径 r 的预警圈，{@code warn} 刻后炸开。
 * 首领预警圈是全模组用得最多的一类（震波、镇压红圈、聚能冲击、失序之柱、坠身……），这一个的画法会铺到所有预警圈上，
 * 所以预警期首先要「读得懂」：圈多大（亮边 + 圈边电弧）、还剩多久（危险红从中心往外涨，涨满就炸）。
 * <ul>
 *   <li>预警：地面像要熔穿——熔岩裂纹越来越亮，危险红往外涨，圈边通电噼啪，火星往上冒；灾柱顶上的能量越聚越亮，最后几刻爆点开始转；</li>
 *   <li>炸开：屏幕短闪、大爆点，一根粗等离子火柱冲天、三道电弧缠着火柱往上绕，白热冲击波 + 热浪扭曲，地面电流往外爬，火花四溅、浓烟、余辉。</li>
 * </ul>
 * 配色用 {@link VfxPlasma#DANGER}（首领招式统一用危险红，和玩家自己的特效分开）。
 */
public final class PillarBacklashFx implements VfxEffect {

    private static final int AFTER = 32;
    private static final int HELIX_POINTS = 28;
    private static final VfxPlasma.Tint TINT = VfxPlasma.DANGER;

    private final Vec3 base;
    private final double radius;
    private final int warn;
    private final Vec3 top;
    private final double[] helix = new double[HELIX_POINTS * 3];
    private final RandomSource random = RandomSource.create();
    private int crawlSeed;
    private int age;

    /**
     * @param base        圈心（地面）
     * @param pillarHeight 灾柱多高（预警期能量聚在柱顶）
     */
    public PillarBacklashFx(Vec3 base, double radius, int warn, double pillarHeight) {
        this.base = base;
        this.radius = radius;
        this.warn = warn;
        this.top = base.add(0D, pillarHeight + 0.2D, 0D);
    }

    @Override
    public boolean tick() {
        age++;
        int t = age - warn;
        if (age % 2 == 0) {
            crawlSeed = random.nextInt(100000);
        }
        if (t < 0) {
            warning();
        } else if (t == 0) {
            explode();
        }
        return age < warn + AFTER;
    }

    private void warning() {
        float k = age / (float) warn;
        int n = Vfx.count(1F + 5F * k * k, base);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = radius * Math.sqrt(random.nextDouble());
            VfxParticles.spawn(base.x + Math.cos(a) * r, base.y + 0.1D, base.z + Math.sin(a) * r, 0D, 0.03D + random.nextDouble() * 0.06D * (0.5D + k), 0D,
                    14 + random.nextInt(14), 0.06F, 0.02F, Vfx.argb(VfxPalette.EMBER, 0.95F), Vfx.argb(VfxPalette.DANGER, 0F), VfxMode.DOT, 500, 0.98F, -0.001F, 0F, 0F);
        }
        // 后段：圈边往上喷火星
        if (k > 0.6F && age % 2 == 0) {
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Mth.TWO_PI;
                Vec3 p = base.add(Math.cos(a) * radius, 0.1D, Math.sin(a) * radius);
                VfxPlasma.sparks(p, new Vec3(0D, 1D, 0D), 0.35D, 1.5F, 0.15D, 0.35D, 8, 14, TINT);
            }
        }
    }

    private void explode() {
        VfxScreen.flash(VfxPalette.EMBER, 0.18F, 14);
        VfxPlasma.sparks(base.add(0D, 0.3D, 0D), new Vec3(0D, 1D, 0D), 0.55D, 90F, 0.45D, 1.15D, 14, 32, TINT);
        VfxPlasma.sparks(base.add(0D, 0.4D, 0D), Vec3.ZERO, 0D, 40F, 0.3D, 0.7D, 10, 22, TINT);
        VfxPlasma.embers(base, radius, 50F, TINT);
        int smoke = Vfx.count(14F, base);
        for (int i = 0; i < smoke; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = radius * (0.3D + random.nextDouble() * 0.7D);
            VfxParticles.spawn(base.x + Math.cos(a) * r, base.y + 0.4D, base.z + Math.sin(a) * r, Math.cos(a) * 0.05D, 0.04D + random.nextDouble() * 0.04D,
                    Math.sin(a) * 0.05D, 40 + random.nextInt(20), 0.9F, 2.2F, Vfx.argb(VfxPalette.SMOKE, 0.7F), Vfx.argb(VfxPalette.SMOKE, 0F),
                    VfxMode.SMOKE, random.nextInt(500), 0.96F, -0.001F, 0F, 0.01F);
        }
    }

    @Override
    public void render(VfxDraw draw) {
        float time = age + draw.partialTick();
        if (time < warn) {
            renderWarning(draw, time);
        } else {
            renderExplosion(draw, time - warn);
        }
    }

    private void renderWarning(VfxDraw draw, float time) {
        float k = time / warn;
        float heat = 0.15F + 0.85F * k * k;
        // 最后 6 刻地面闪烁加快
        float blink = time > warn - 6F ? 0.75F + 0.25F * Mth.sin(time * 3.1F) : 1F;
        draw.flat(base, (float) radius, 0F, VfxMode.MOLTEN, VfxMode.molten(heat, (float) radius), Vfx.argb(VfxPalette.DANGER, (0.7F + 0.3F * k) * blink));
        draw.flat(base, (float) radius, 0F, VfxMode.FILL, VfxMode.progress(k, 1), Vfx.argb(VfxPalette.DANGER, 0.55F));
        draw.flat(base, (float) radius * 1.03F, 0F, VfxMode.SHOCK, 80, Vfx.argb(VfxPalette.EMBER, 0.5F + 0.4F * k));
        VfxPlasma.rimArcs(draw, RandomSource.create(crawlSeed), base, (float) radius, 2 + Math.round(5F * k), 0.035F, 0.4F + 0.6F * k, TINT);
        draw.distortFlat(base, (float) radius, 1, 0, 0.25F * k);
        // 柱顶聚能
        draw.billboard(top, 0.3F + 1.2F * k, time * 0.1F, VfxMode.FLARE, 900, Vfx.argb(0xFFF4E8, 0.3F + 0.6F * k));
        draw.billboard(top, 0.8F + 1.8F * k, 0F, VfxMode.DOT, 0, Vfx.argb(VfxPalette.DANGER, 0.45F * k));
        VfxPlasma.corona(draw, RandomSource.create(crawlSeed + 17L), top, 0.8F + 1.2F * k, 1 + Math.round(3F * k), 0.035F, 0.5F + 0.5F * k, TINT);
        if (k > 0.7F) {
            float pre = (k - 0.7F) / 0.3F;
            draw.billboard(top.x, top.y, top.z, 2F * pre, 2F * pre, time * 0.3F, VfxMode.BURST, 19, Vfx.argb(0xFFB08A, 0.75F * pre), false);
        }
    }

    private void renderExplosion(VfxDraw draw, float t) {
        VfxPlasma.burst(draw, draw.towardCamera(base.add(0D, 1.4D, 0D), 1.4D), 4.6F, t, 11F, 211, TINT);
        // 火柱：先猛地冲起，再收细散掉
        float col = Vfx.pulse(t / 18F, 0.08F);
        Vec3 bottom = base.add(0D, 0.1D, 0D);
        Vec3 columnTop = base.add(0D, 4D + 11D * Vfx.easeOutExpo(t / 4F), 0D);
        if (col > 0F) {
            float w = (float) radius * col;
            if (Vfx.tier() >= 2) {
                draw.strip(bottom, columnTop, w * 0.95F, 0.6F * col, Vfx.argb(TINT.outer(), 0.22F * col), Vfx.argb(TINT.outer(), 0F), VfxMode.STRIP, 30, 0F, 1F);
            }
            draw.strip(bottom, columnTop, w * 0.55F, 0.4F * col, Vfx.argb(VfxPalette.EMBER, 0.6F * col), Vfx.argb(VfxPalette.DANGER, 0F), VfxMode.BEAM, 11, 0F, 6F);
            draw.strip(bottom, columnTop, w * 0.24F, 0.1F, Vfx.argb(TINT.core(), col), Vfx.argb(VfxPalette.EMBER, 0F), VfxMode.PLASMA, 7, 0F, 6F);
            draw.strip(bottom, columnTop, w * 0.08F, 0.03F * col, Vfx.argb(TINT.core(), col * col), Vfx.argb(TINT.core(), 0F), VfxMode.STRIP, 650, 0F, 1F);
            if (Vfx.tier() >= 1) {
                helixArcs(draw, t, col, columnTop.y);
            }
        }
        VfxPlasma.shockwave(draw, base, (float) radius * 1.25F, t, 7F, 15F, TINT);
        VfxPlasma.groundCrawl(draw, RandomSource.create(crawlSeed), base, (float) radius * 1.15F, 10, 0.04F, 1F - Mth.clamp(t / 14F, 0F, 1F), TINT);
        float scorch = 1F - Mth.clamp(t / AFTER, 0F, 1F);
        draw.flat(base, (float) radius, 0F, VfxMode.MOLTEN, VfxMode.molten(0.4F * scorch, (float) radius), Vfx.argb(VfxPalette.DANGER, 0.8F * scorch));
    }

    /** 三道电弧缠着火柱往上绕（越往上圈越小） */
    private void helixArcs(VfxDraw draw, float t, float col, double topY) {
        double height = (topY - base.y - 0.2D) * 0.85D;
        for (int h = 0; h < 3; h++) {
            RandomSource jitter = RandomSource.create(crawlSeed + h * 131L);
            double phase = h * Mth.TWO_PI / 3D + t * 0.35D;
            for (int i = 0; i < HELIX_POINTS; i++) {
                double f = i / (double) (HELIX_POINTS - 1);
                double ang = phase + f * Mth.TWO_PI * 1.7D;
                double rr = radius * 0.32D * col * (1D - 0.55D * f) + 0.15D;
                helix[i * 3] = base.x + Math.cos(ang) * rr + (jitter.nextDouble() - 0.5D) * 0.12D;
                helix[i * 3 + 1] = base.y + 0.2D + height * f;
                helix[i * 3 + 2] = base.z + Math.sin(ang) * rr + (jitter.nextDouble() - 0.5D) * 0.12D;
            }
            VfxPlasma.arc(draw, helix, HELIX_POINTS, 0.05F, col * col, crawlSeed + h, TINT);
        }
    }

    @Override
    public AABB bounds() {
        return new AABB(base, base).inflate(radius * 1.5D + 1D, 16D, radius * 1.5D + 1D);
    }
}
