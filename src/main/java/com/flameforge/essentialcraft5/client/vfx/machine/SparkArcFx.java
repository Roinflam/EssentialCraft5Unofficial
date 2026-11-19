package com.flameforge.essentialcraft5.client.vfx.machine;

import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 一道短小的电弧（线圈基座侧面的火花、矩阵受损时身上窜出来的电），两刻换一次形状，最后三分之一淡掉 */
final class SparkArcFx implements VfxEffect {

    private static final RandomSource SHAPE = RandomSource.create();

    private final Vec3 from;
    private final Vec3 to;
    private final int life;
    private final float width;
    private final float roughness;
    private final double[] path = new double[ArcCache.POINTS * 3];
    private final AABB bounds;
    private final int seed;
    private int age;

    SparkArcFx(Vec3 from, Vec3 to) {
        this(from, to, 40, 0.026F, 0.18F);
    }

    SparkArcFx(Vec3 from, Vec3 to, int life, float width, float roughness) {
        this.from = from;
        this.to = to;
        this.life = life;
        this.width = width;
        this.roughness = roughness;
        this.bounds = new AABB(from, to).inflate(0.5D);
        this.seed = SHAPE.nextInt(1 << 20);
        reshape();
    }

    private void reshape() {
        SHAPE.setSeed(seed * 131L + age / 2);
        VfxPaths.jagged(SHAPE, from, to, ArcCache.DEPTH, roughness, path);
    }

    @Override
    public boolean tick() {
        age++;
        if ((age & 1) == 0) {
            reshape();
        }
        return age < life;
    }

    @Override
    public void render(VfxDraw draw) {
        float t = age + draw.partialTick();
        float fadeFrom = life * 0.66F;
        float a = t < fadeFrom ? 1F : Math.max(0F, (life - t) / (life - fadeFrom));
        // 刚出来那两刻最亮，之后轻轻地一闪一闪
        a *= age < 2 ? 1F : 0.6F + 0.4F * (((age * 7 + seed) & 3) / 3F);
        float dist = (float) draw.camera().distanceTo(from);
        CoilVfx.arc(draw, path, ArcCache.POINTS, width, a, dist, seed & 1023);
    }

    @Override
    public AABB bounds() {
        return bounds;
    }
}
