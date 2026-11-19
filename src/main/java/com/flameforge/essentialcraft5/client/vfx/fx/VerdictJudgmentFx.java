package com.flameforge.essentialcraft5.client.vfx.fx;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.client.vfx.VfxScreen;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 裁决之刃「断罪」的特效（等离子画风样片）：蓄力 {@value #CHARGE} 刻 → 松手一刀判下范围内所有目标 → 余韵。
 * <ul>
 *   <li>蓄力：范围圈的边在噼啪（圈边电弧越来越密），一道收缩的环往脚下聚；刀上一团越来越亮的白芯，电晕乱跳，火花从四周往刀上收，
 *       最后几刻刀上的爆点开始转；</li>
 *   <li>松手（第 {@value #CHARGE} 刻）：刀上炸开大爆点 + 屏幕短闪，白热冲击波扫到圈边，地面电流往外爬，火花四溅、余辉升起；</li>
 *   <li>每个目标在冲击波扫到它的那一刻挨一道带分叉的等离子电弧（能量团顺着电弧冲过去），落点爆点 + 小冲击波 + 地面电流 + 热浪；</li>
 *   <li>余韵：地上的烧痕、慢慢升起的余辉。</li>
 * </ul>
 */
public final class VerdictJudgmentFx implements VfxEffect {

    public static final int CHARGE = 24;
    private static final int AFTER = 36;
    private static final int LIFE = CHARGE + AFTER;
    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;

    private final Entity caster;
    private final List<LivingEntity> targets;
    private final double radius;
    private final float[] strikeAt;
    private final double[][] arcs;
    private final int[] arcSeeds;
    private final RandomSource random = RandomSource.create();
    private int crawlSeed;
    private int age;

    public VerdictJudgmentFx(Entity caster, List<LivingEntity> targets, double radius) {
        this.caster = caster;
        this.targets = targets;
        this.radius = radius;
        this.strikeAt = new float[targets.size()];
        // 至少一份：蓄力时刀上的电晕借第 0 份种子
        this.arcs = new double[Math.max(1, targets.size())][VfxPaths.jaggedPoints(5) * 3];
        this.arcSeeds = new int[Math.max(1, targets.size())];
        for (int i = 0; i < targets.size(); i++) {
            double d = targets.get(i).position().distanceTo(caster.position());
            // 冲击波扫到它的时刻（冲击波 6 刻扩到圈边，缓动是先快后慢）
            strikeAt[i] = 1F + (float) (d / radius) * 4F;
            arcSeeds[i] = random.nextInt(100000);
        }
    }

    @Override
    public boolean tick() {
        age++;
        Vec3 feet = caster.position();
        Vec3 hand = hand(1F);
        int t = age - CHARGE;
        if (age % 2 == 0) {
            crawlSeed = random.nextInt(100000);
        }
        if (t < 0) {
            charging(feet, hand);
        } else {
            if (t == 0) {
                release(feet, hand);
            }
            for (int i = 0; i < targets.size(); i++) {
                int at = Math.round(strikeAt[i]);
                if (t == at) {
                    strike(i);
                }
                // 刚劈中的几刻每刻换形状（电弧在乱跳），之后隔一刻换
                if (t >= at && (t - at < 6 || (t - at) % 2 == 0)) {
                    arcSeeds[i] = random.nextInt(100000);
                }
            }
        }
        return age < LIFE;
    }

    private void charging(Vec3 feet, Vec3 hand) {
        float k = age / (float) CHARGE;
        // 火花从四周往刀上收
        int n = Vfx.count(2F + 8F * k, hand);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = 1.6D + random.nextDouble() * 1.8D;
            Vec3 from = hand.add(Math.cos(a) * r, (random.nextDouble() - 0.4D) * 1.8D, Math.sin(a) * r);
            Vec3 v = hand.subtract(from).scale(0.17D);
            VfxParticles.spawn(from.x, from.y, from.z, v.x, v.y, v.z, 7, 0.065F, 0.02F,
                    Vfx.argb(VfxPalette.PINK, 0.95F), Vfx.argb(VfxPalette.WHITE_HOT, 0F), VfxMode.SPARK, 0, 0.98F, 0F, 1.8F, 0F);
        }
        // 后半段圈边开始往上冒余辉
        if (k > 0.5F) {
            int e = Vfx.count(2.5F * k, feet);
            for (int i = 0; i < e; i++) {
                double a = random.nextDouble() * Mth.TWO_PI;
                double r = radius * (0.92D + random.nextDouble() * 0.1D);
                VfxParticles.spawn(feet.x + Math.cos(a) * r, feet.y + 0.1D, feet.z + Math.sin(a) * r, 0D, 0.04D + random.nextDouble() * 0.05D, 0D,
                        16 + random.nextInt(12), 0.06F, 0F, Vfx.argb(VfxPalette.WHITE_HOT, 0.9F), Vfx.argb(VfxPalette.MAGENTA, 0F),
                        VfxMode.DOT, 450, 0.97F, -0.001F, 0F, 0F);
            }
        }
        if (age % 2 == 0) {
            arcSeeds[0] = random.nextInt(100000);
        }
    }

    private void release(Vec3 feet, Vec3 hand) {
        VfxScreen.flash(VfxPalette.MAGENTA, 0.16F, 10);
        VfxPlasma.sparks(hand, Vec3.ZERO, 0D, 60F, 0.3D, 0.95D, 10, 24, TINT);
        VfxPlasma.embers(feet, radius, 50F, TINT);
    }

    private void strike(int i) {
        LivingEntity target = targets.get(i);
        Vec3 c = center(target, 1F);
        Vec3 away = c.subtract(caster.position()).multiply(1D, 0D, 1D);
        Vec3 dir = (away.lengthSqr() < 1.0E-4D ? new Vec3(0D, 1D, 0D) : away.normalize()).add(0D, 0.7D, 0D);
        VfxPlasma.sparks(c, dir, 0.9D, 26F, 0.2D, 0.65D, 8, 20, TINT);
        VfxPlasma.embers(target.position(), 0.7D, 10F, TINT);
    }

    @Override
    public void render(VfxDraw draw) {
        float pt = draw.partialTick();
        float time = age + pt;
        Vec3 feet = caster.getPosition(pt);
        Vec3 hand = hand(pt);
        if (time < CHARGE) {
            renderCharge(draw, time, feet, hand);
        } else {
            renderRelease(draw, time - CHARGE, feet, hand, pt);
        }
    }

    private void renderCharge(VfxDraw draw, float time, Vec3 feet, Vec3 hand) {
        float k = time / CHARGE;
        // 范围圈：边上在噼啪，越蓄越亮；一道收缩的环往脚下聚
        draw.flat(feet, (float) radius, 0F, VfxMode.SHOCK, 120, Vfx.argb(VfxPalette.MAGENTA, 0.25F + 0.5F * k));
        draw.flat(feet, (float) radius * 1.01F, 0F, VfxMode.RING, 10, Vfx.argb(VfxPalette.WHITE_HOT, 0.35F * k));
        float shrink = (float) (radius * (1F - Vfx.easeInCubic(k)) + 0.4F);
        draw.flat(feet, shrink, 0F, VfxMode.RING, 25, Vfx.argb(VfxPalette.PINK, 0.65F * k));
        draw.flat(feet, 1.2F + k, 0F, VfxMode.DOT, 0, Vfx.argb(VfxPalette.VIOLET, 0.35F * k));
        VfxPlasma.rimArcs(draw, RandomSource.create(crawlSeed), feet, (float) radius, 1 + Math.round(4F * k), 0.03F, 0.4F + 0.6F * k, TINT);
        // 刀上：越来越亮的白芯 + 电晕；最后几刻爆点开始转
        float flick = 0.8F + 0.2F * Mth.sin(time * 2.7F);
        draw.billboard(hand, 0.25F + 0.6F * k, time * 0.1F, VfxMode.FLARE, 900, Vfx.argb(VfxPalette.WHITE_HOT, (0.5F + 0.5F * k) * flick));
        draw.billboard(hand, 0.8F + 1.2F * k, 0F, VfxMode.DOT, 0, Vfx.argb(VfxPalette.MAGENTA, 0.4F * k));
        VfxPlasma.corona(draw, RandomSource.create(arcSeeds[0] + (long) age * 31L), hand, 1.0F + 1.1F * k, 1 + Math.round(4F * k), 0.04F, 0.9F, TINT);
        if (k > 0.7F) {
            float pre = (k - 0.7F) / 0.3F;
            draw.billboard(hand.x, hand.y, hand.z, 1.4F * pre, 1.4F * pre, time * 0.25F, VfxMode.BURST, 77, Vfx.argb(VfxPalette.PINK, 0.7F * pre), false);
        }
    }

    private void renderRelease(VfxDraw draw, float t, Vec3 feet, Vec3 hand, float pt) {
        // 刀上炸开
        VfxPlasma.burst(draw, draw.towardCamera(hand, 0.6D), 3.4F, t, 10F, 131, TINT);
        float flash = 1F - Mth.clamp(t / 6F, 0F, 1F);
        if (flash > 0F) {
            draw.billboard(hand, 4F, 0F, VfxMode.DOT, 0, Vfx.argb(VfxPalette.MAGENTA, 0.5F * flash));
        }
        VfxPlasma.shockwave(draw, feet, (float) radius * 1.05F, t, 6F, 13F, TINT);
        VfxPlasma.groundCrawl(draw, RandomSource.create(crawlSeed), feet, (float) radius, 8, 0.035F, 1F - Mth.clamp(t / 12F, 0F, 1F), TINT);
        // 地上烧过的痕迹
        float scorch = 1F - Mth.clamp((t - 2F) / (AFTER - 2F), 0F, 1F);
        draw.flat(feet, (float) radius * 1.05F, 0F, VfxMode.DOT, 0, Vfx.argb(VfxPalette.MAGENTA, 0.22F * scorch));
        draw.flat(feet, (float) radius, 0F, VfxMode.RING, 15, Vfx.argb(VfxPalette.VIOLET, 0.5F * scorch));
        // 每个目标：一道带分叉的等离子电弧从刀劈到身上
        for (int i = 0; i < targets.size(); i++) {
            float ts = t - strikeAt[i];
            if (ts < 0F || ts > 14F) {
                continue;
            }
            LivingEntity target = targets.get(i);
            Vec3 c = center(target, pt);
            float a = VfxPlasma.strokeAlpha(ts, 6F);
            RandomSource arcRandom = RandomSource.create(arcSeeds[i]);
            int n = VfxPaths.jagged(arcRandom, hand, c, 5, 0.14F, arcs[i]);
            VfxPlasma.forkedArc(draw, arcRandom, arcs[i], n, 0.075F, a, arcSeeds[i], 5, 1.4F, TINT);
            VfxPlasma.pulse(draw, arcs[i], n, ts / 2.5F, 0.22F, 1F, TINT);
            Vec3 base = target.getPosition(pt);
            VfxPlasma.burst(draw, draw.towardCamera(c, 1.1D), 1.6F, ts, 9F, arcSeeds[i] & 1023, TINT);
            VfxPlasma.shockwave(draw, base, 1.8F, ts, 4F, 9F, TINT);
            float hit = 1F - Mth.clamp(ts / 9F, 0F, 1F);
            VfxPlasma.groundCrawl(draw, RandomSource.create(crawlSeed + i * 7919L), base, 1.6F, 3, 0.022F, hit, TINT);
            draw.distortBillboard(c, 1.5F, 1, 0, 0.5F * hit);
        }
    }

    @Override
    public AABB bounds() {
        return new AABB(caster.position(), caster.position()).inflate(radius + 3D, 8D, radius + 3D);
    }

    /** 刀的大概位置：身前偏右、胸口高度 */
    private Vec3 hand(float pt) {
        Vec3 pos = caster.getPosition(pt);
        float yaw = caster.getViewYRot(pt) * Mth.DEG_TO_RAD;
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        return pos.add(fx * 0.5D - fz * 0.35D, caster.getBbHeight() * 0.62D, fz * 0.5D + fx * 0.35D);
    }

    private static Vec3 center(Entity entity, float pt) {
        return entity.getPosition(pt).add(0D, entity.getBbHeight() * 0.55D, 0D);
    }
}
