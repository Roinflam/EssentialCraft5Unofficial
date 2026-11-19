package com.flameforge.essentialcraft5.client.vfx.machine;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 特斯拉线圈打向目标的一次放电，照等离子第二版的线圈样片：出电点爆点 + 电晕，四层电弧带分叉、前 8 刻每刻换形状、亮度照真闪电回击，
 * 一团能量顺着电弧冲到目标；落点爆点、冲击波、地面电流、火花和余辉。
 * 线圈一秒打一次、基地里常常一排，所以按生成时离镜头的远近减：24 格外不画电晕、地面电流、热浪和烧痕，40 格外分叉减半、不画冲击波。
 * 形状只在换形状的那一刻算一次（存在自己的数组里），每帧只画。
 */
final class CoilStrikeFx implements VfxEffect {

    static final int LIFE = 18;
    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
    private static final int MAX_TARGETS = 6;
    private static final RandomSource SHAPE = RandomSource.create();

    private final Vec3 emit;
    private final Entity[] targets;
    private final double[][] paths;
    private final int[] counts;
    private final int[] seeds;
    private final boolean near;
    private final boolean mid;
    private final RandomSource random = RandomSource.create();
    private int coronaSeed;
    private int age;
    private AABB bounds;

    CoilStrikeFx(Vec3 emit, List<Entity> list, float dist) {
        this.emit = emit;
        int n = Math.min(MAX_TARGETS, list.size());
        this.targets = list.subList(0, n).toArray(new Entity[0]);
        this.paths = new double[n][VfxPaths.jaggedPoints(5) * 3];
        this.counts = new int[n];
        this.seeds = new int[n];
        this.near = dist <= 24F;
        this.mid = dist <= 40F;
        reshape(0F);
        bounds = computeBounds();
    }

    private void reshape(float pt) {
        coronaSeed = random.nextInt(100000);
        for (int i = 0; i < targets.length; i++) {
            seeds[i] = random.nextInt(100000);
            SHAPE.setSeed(seeds[i]);
            counts[i] = VfxPaths.jagged(SHAPE, emit, center(targets[i], pt), 5, 0.12F, paths[i]);
        }
    }

    private static Vec3 center(Entity e, float pt) {
        return e.getPosition(pt).add(0D, e.getBbHeight() * 0.5D, 0D);
    }

    @Override
    public boolean tick() {
        age++;
        if (age < 9 || age % 2 == 0) {
            reshape(1F);
        }
        if (age == 1) {
            VfxPlasma.sparks(emit, Vec3.ZERO, 0D, 14F, 0.15D, 0.45D, 6, 14, TINT);
            for (Entity target : targets) {
                Vec3 c = target.getBoundingBox().getCenter();
                Vec3 away = c.subtract(emit).multiply(1D, 0D, 1D);
                Vec3 dir = (away.lengthSqr() < 1.0E-4D ? Vec3.ZERO : away.normalize()).add(0D, 0.8D, 0D);
                VfxPlasma.sparks(c, dir, 0.9D, 22F, 0.18D, 0.55D, 8, 20, TINT);
                VfxPlasma.embers(target.position(), 0.6D, 8F, TINT);
            }
        }
        if (age % 4 == 0) {
            bounds = computeBounds();
        }
        return age < LIFE;
    }

    @Override
    public void render(VfxDraw draw) {
        float pt = draw.partialTick();
        float t = age + pt;
        VfxPlasma.burst(draw, emit, 1.7F, t, 11F, 53, TINT);
        if (near) {
            float crown = 1F - Mth.clamp(t / 14F, 0F, 1F);
            SHAPE.setSeed(coronaSeed);
            VfxPlasma.corona(draw, SHAPE, emit, 1.0F, 4, 0.03F, crown, TINT);
        }
        float a = VfxPlasma.strokeAlpha(t, 6F);
        for (int i = 0; i < targets.length; i++) {
            Entity target = targets[i];
            Vec3 feet = target.getPosition(pt);
            Vec3 c = feet.add(0D, target.getBbHeight() * 0.5D, 0D);
            if (a > 0F) {
                // 落点跟着目标走：只把折线最后一个点钉到目标身上，形状照旧
                int n = counts[i];
                paths[i][(n - 1) * 3] = c.x;
                paths[i][(n - 1) * 3 + 1] = c.y;
                paths[i][(n - 1) * 3 + 2] = c.z;
                SHAPE.setSeed(seeds[i] * 31L + 7L);
                VfxPlasma.forkedArc(draw, SHAPE, paths[i], n, 0.085F, a, seeds[i], mid ? 6 : 3, 1.6F, TINT);
                VfxPlasma.pulse(draw, paths[i], n, t / 2F, 0.25F, 1F, TINT);
            }
            VfxPlasma.burst(draw, draw.towardCamera(c, 1.1D), 1.8F, t, 10F, (seeds[0] + i * 37) & 1023, TINT);
            if (mid) {
                VfxPlasma.shockwave(draw, feet, 1.8F, t, 4F, 10F, TINT);
            }
            if (near) {
                float hit = 1F - Mth.clamp(t / 10F, 0F, 1F);
                SHAPE.setSeed(coronaSeed + i * 7919L);
                VfxPlasma.groundCrawl(draw, SHAPE, feet, 1.5F, 3, 0.022F, hit, TINT);
                float scorch = 1F - Mth.clamp(t / LIFE, 0F, 1F);
                draw.flat(feet, 1.3F, 0F, VfxMode.DOT, 0, Vfx.argb(VfxPalette.MAGENTA, 0.5F * scorch));
                draw.distortBillboard(c, 1.6F, 1, 0, 0.7F * hit);
            }
        }
    }

    private AABB computeBounds() {
        AABB box = new AABB(emit, emit);
        for (Entity target : targets) {
            box = box.minmax(target.getBoundingBox());
        }
        return box.inflate(3D);
    }

    @Override
    public AABB bounds() {
        return bounds;
    }
}
