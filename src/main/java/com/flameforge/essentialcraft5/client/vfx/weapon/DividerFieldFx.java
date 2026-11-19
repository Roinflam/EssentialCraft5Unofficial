package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 二分场（跟着 {@link DividerFieldEntity}）：等离子球壳（正对镜头的亮边面片，边上亮、往里淡，像一层发光的壳）、
 * 贴着球面跳的几道电弧、球和地面交界处的圈边电弧、球心一团越蓄越快的光；张开时（实体事件 {@link DividerFieldEntity#EVENT_OPEN}）
 * 球壳往外一涨炸开，熄灭的只淡掉。
 * <p>
 * 常驻的部分按开销来：球面电弧的形状隔 {@value #RESHAPE} 刻才重算一次（每个场的起始刻错开），存在固定数组里；
 * 离镜头 48 格外只画球壳不画电弧；镜头在球里时不画正对镜头的壳（那张面片会糊满屏幕），只留电弧。
 */
public final class DividerFieldFx implements VfxEffect {

    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
    private static final float R = (float) DividerFieldEntity.RADIUS;
    private static final int CHARGE = 40;
    private static final int RESHAPE = 3;
    private static final int ARCS = 3;
    private static final int POINTS = 14;
    private static final int OPEN = 18;
    private static final int FADE = 6;

    private final DividerFieldEntity field;
    private final RandomSource random = RandomSource.create();
    private final int seed = random.nextInt(100000);
    private final double[][] arcs = new double[ARCS][POINTS * 3];
    private final int phase;
    private Vec3 center;
    private int age;
    private int rimSeed;
    private int after = -1;
    private boolean opened;

    public DividerFieldFx(DividerFieldEntity field) {
        this.field = field;
        this.center = field.position();
        this.phase = field.getId() % RESHAPE;
        reshape();
    }

    @Override
    public boolean tick() {
        age++;
        if (after < 0) {
            if (field.isRemoved()) {
                opened = field.openedOnClient();
                after = 0;
                if (opened) {
                    VfxPlasma.sparks(center, Vec3.ZERO, 0D, 30F, 0.25D, 0.7D, 8, 18, TINT);
                    VfxPlasma.embers(center.subtract(0D, 0.5D, 0D), R, 14F, TINT);
                }
            } else {
                center = field.position();
                if ((age + phase) % RESHAPE == 0 && Vfx.visibleAt(center)) {
                    reshape();
                }
                return true;
            }
        }
        after++;
        if (opened && after < 8 && (after & 1) == 0) {
            rimSeed = random.nextInt(100000);
        }
        return after < (opened ? OPEN : FADE);
    }

    /** 重算球面电弧：两点之间顺着球面走，路上加一点抖动，再压回球面 */
    private void reshape() {
        rimSeed = random.nextInt(100000);
        for (double[] path : arcs) {
            Vec3 a = VfxPlasma.randomDir(random);
            Vec3 side = a.cross(VfxPlasma.randomDir(random)).normalize();
            double span = 0.7D + random.nextDouble() * 0.8D;
            Vec3 b = a.scale(Math.cos(span)).add(side.scale(Math.sin(span)));
            for (int i = 0; i < POINTS; i++) {
                double f = i / (double) (POINTS - 1);
                Vec3 d = a.scale(1D - f).add(b.scale(f));
                double jitter = Math.sin(Math.PI * f) * 0.16D;
                d = d.add(VfxPlasma.randomDir(random).scale(jitter)).normalize().scale(R * 1.01D);
                path[i * 3] = d.x;
                path[i * 3 + 1] = d.y;
                path[i * 3 + 2] = d.z;
            }
        }
    }

    @Override
    public void render(VfxDraw draw) {
        long t0 = WeaponVfxStats.start();
        float pt = draw.partialTick();
        Vec3 c = after < 0 ? field.getPosition(pt) : center;
        if (after < 0) {
            renderField(draw, c, Mth.clamp((age + pt) / CHARGE, 0F, 1F), 1F, 1F);
        } else if (opened) {
            renderOpen(draw, c, after + pt);
        } else {
            renderField(draw, c, 1F, 1F, 1F - (after + pt) / FADE);
        }
        WeaponVfxStats.stop(t0);
    }

    private void renderField(VfxDraw draw, Vec3 c, float k, float scale, float alpha) {
        float time = draw.time();
        double dist = draw.camera().distanceTo(c);
        float r = R * scale;
        if (dist > r * 1.05D) {
            float shimmer = 0.85F + 0.15F * Mth.sin(time * 0.35F + seed);
            // 球壳：亮边往里拖尾的面片（冲击波图案），外沿压在球的轮廓上
            draw.billboard(c, r / 0.94F, time * 0.01F, VfxMode.SHOCK, 420, Vfx.argb(TINT.glow(), (0.32F + 0.25F * k) * shimmer * alpha));
            draw.billboard(c, r / 0.9F, 0F, VfxMode.RING, 12, Vfx.argb(TINT.inner(), (0.12F + 0.16F * k) * alpha));
            if (Vfx.tier() >= 2) {
                draw.billboard(c, r * 1.15F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.outer(), (0.14F + 0.1F * k) * alpha));
            }
        }
        // 球心：越蓄越亮、越闪越快
        float beat = 0.7F + 0.3F * Mth.sin(time * (0.4F + 1.4F * k));
        draw.billboard(c, 0.35F + 0.4F * k, time * 0.06F, VfxMode.FLARE, 900, Vfx.argb(TINT.core(), (0.4F + 0.5F * k) * beat * alpha));
        draw.billboard(c, 1.0F + 0.6F * k, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.3F * beat * alpha));
        if (dist > 48D) {
            return;
        }
        int tier = Vfx.tier();
        int count = tier >= 2 ? ARCS : tier == 1 ? 2 : 1;
        for (int i = 0; i < count; i++) {
            double[] path = arcs[i];
            offsetInto(path, c, scale);
            VfxPlasma.arc(draw, SHIFTED, POINTS, 0.028F, (0.55F + 0.4F * k) * alpha, seed + i * 31, TINT);
        }
        VfxPlasma.rimArcs(draw, RandomSource.create(rimSeed), c, r * 0.98F, 2 + Math.round(2F * k), 0.03F, (0.5F + 0.5F * k) * alpha, TINT);
    }

    private void renderOpen(VfxDraw draw, Vec3 c, float t) {
        float f = Mth.clamp(t / 6F, 0F, 1F);
        if (f < 1F) {
            renderField(draw, c, 1F, 1F + 0.35F * Vfx.easeOutCubic(f), 1F - f);
        }
        VfxPlasma.burst(draw, draw.towardCamera(c, 0.8D), 2.6F, t, 12F, seed & 1023, TINT);
        if (t < 1.5F) {
            draw.billboard(c, 2.2F, 0F, VfxMode.FLARE, 1000, Vfx.argb(TINT.core(), 1F - t / 1.5F));
        }
        VfxPlasma.wave(draw, c, R + 1F, t, 5F, 12F, 0.8F, TINT);
        float arcs = 1F - Mth.clamp(t / 8F, 0F, 1F);
        if (arcs > 0F) {
            VfxPlasma.corona(draw, RandomSource.create(rimSeed), c, R * 0.9F, 5, 0.035F, arcs, TINT);
            draw.distortBillboard(c, R, 1, 0, 0.5F * arcs);
        }
    }

    private static final double[] SHIFTED = new double[POINTS * 3];

    private static void offsetInto(double[] path, Vec3 c, float scale) {
        for (int i = 0; i < POINTS; i++) {
            SHIFTED[i * 3] = c.x + path[i * 3] * scale;
            SHIFTED[i * 3 + 1] = c.y + path[i * 3 + 1] * scale;
            SHIFTED[i * 3 + 2] = c.z + path[i * 3 + 2] * scale;
        }
    }

    @Override
    public AABB bounds() {
        return new AABB(center, center).inflate(R * 1.5D + 1D);
    }
}
