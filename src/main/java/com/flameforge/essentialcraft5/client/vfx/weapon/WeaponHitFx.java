package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 武器一次性的命中画面，几样东西按需要拼：爆点（必有）、贴地柔波、地面裂纹、电晕、从头顶劈下来的电弧、竖着的一道斩光。
 * 跟着实体时爆点贴在它身上走（打中移动的生物不会留在半空）。电系的才开电晕和电弧（{@link #electric}）。
 */
final class WeaponHitFx implements VfxEffect {

    @Nullable
    private final Entity follow;
    private final Vec3 offset;
    private Vec3 at;
    private final VfxPlasma.Tint tint;
    private final float size;
    private final int life;
    private final int seed = Vfx.random().nextInt(100000);
    private float wave;
    private float cracks;
    @Nullable
    private Vec3 floor;
    private int corona;
    private boolean strikeDown;
    private boolean slash;
    private final double[] arc = new double[VfxPaths.jaggedPoints(4) * 3];
    private int arcSeed = seed;
    private int age;

    private WeaponHitFx(@Nullable Entity follow, Vec3 at, VfxPlasma.Tint tint, float size, int life) {
        this.follow = follow;
        this.at = at;
        this.offset = follow == null ? Vec3.ZERO : at.subtract(follow.position());
        this.tint = tint;
        this.size = size;
        this.life = life;
    }

    static WeaponHitFx at(Vec3 at, VfxPlasma.Tint tint, float size, int life) {
        return new WeaponHitFx(null, at, tint, size, life);
    }

    /** 贴着实体：at 是打中时的位置，之后跟着实体平移 */
    static WeaponHitFx on(@Nullable Entity entity, Vec3 at, VfxPlasma.Tint tint, float size, int life) {
        return new WeaponHitFx(entity, at, tint, size, life);
    }

    WeaponHitFx wave(float radius) {
        this.wave = radius;
        return this;
    }

    WeaponHitFx cracks(float radius) {
        this.cracks = radius;
        return this;
    }

    /** 地波和裂纹贴的地面；不给就画在爆点高度，侧着看是一道横线 */
    WeaponHitFx floor(Vec3 floor) {
        this.floor = floor;
        return this;
    }

    WeaponHitFx electric(int arcs) {
        this.corona = arcs;
        return this;
    }

    WeaponHitFx strikeDown() {
        this.strikeDown = true;
        return this;
    }

    WeaponHitFx slash() {
        this.slash = true;
        return this;
    }

    void play() {
        if (Vfx.visibleAt(at)) {
            VfxEffects.add(this);
        }
    }

    private Vec3 pos(float pt) {
        if (follow != null && !follow.isRemoved()) {
            return follow.getPosition(pt).add(offset);
        }
        return at;
    }

    @Override
    public boolean tick() {
        age++;
        at = pos(1F);
        if ((age & 1) == 0) {
            arcSeed = Vfx.random().nextInt(100000);
        }
        return age < life;
    }

    @Override
    public void render(VfxDraw draw) {
        long t0 = WeaponVfxStats.start();
        float t = age + draw.partialTick();
        Vec3 c = pos(draw.partialTick());
        Vec3 near = draw.towardCamera(c, 0.6D);
        VfxPlasma.burst(draw, near, size, t, life * 0.7F, seed & 1023, tint);
        if (t < 1F) {
            draw.billboard(near, size * 0.8F, 0F, VfxMode.FLARE, 1000, Vfx.argb(tint.core(), 1F - t));
        }
        Vec3 ground = follow != null ? follow.getPosition(draw.partialTick()) : floor != null ? floor : c;
        if (wave > 0F) {
            VfxPlasma.wave(draw, ground, wave, t, life * 0.4F, life, 0.8F, tint);
        }
        if (cracks > 0F) {
            float heat = 1F - Mth.clamp(t / life, 0F, 1F);
            VfxPlasma.groundCracks(draw, seed, ground, 0.3F, cracks, 6, heat, Math.min(1F, heat * 1.5F), tint);
        }
        float k = 1F - Mth.clamp(t / (life * 0.6F), 0F, 1F);
        if (corona > 0 && k > 0F) {
            VfxPlasma.corona(draw, RandomSource.create(arcSeed), c, size * 0.7F, corona, 0.03F, k, tint);
        }
        if (strikeDown) {
            float a = VfxPlasma.strokeAlpha(t, 5F);
            if (a > 0.01F) {
                RandomSource r = RandomSource.create(arcSeed);
                int n = VfxPaths.jagged(r, c.add((r.nextDouble() - 0.5D) * 1.5D, 4.5D, (r.nextDouble() - 0.5D) * 1.5D), c, 4, 0.18F, arc);
                VfxPlasma.forkedArc(draw, r, arc, n, Math.min(1F, t / 0.6F), 0.06F, a, arcSeed, 3, 1F, tint);
            }
        }
        if (slash && t < 4F) {
            float a = 1F - t / 4F;
            float h = 1.1F + size * 0.5F;
            VfxPlasma.beam(draw, c.add(0D, h, 0D), c.subtract(0D, h, 0D), 0.12F * (0.5F + a), a, true, seed, tint);
        }
        WeaponVfxStats.stop(t0);
    }

    @Override
    public AABB bounds() {
        double r = Math.max(Math.max(wave, cracks), size) + 1D;
        return new AABB(at, at).inflate(r, strikeDown ? 5D : r, r);
    }
}
