package com.flameforge.essentialcraft5.client.vfx.gear;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 五套盔甲满阶觉醒的画面（服务端结算、发 {@code S2CGearFxPacket}，这里只画）。颜色都取这一套贴图主色往 MRU 紫收过的满阶前一阶色。
 * <ul>
 *   <li>回涌（魔力防护）：胸口炸开，一层柔光往外鼓、地上一圈柔波推到半径，几道电弧从胸口打向四周（魔力防护是 MRU 护盾，带电）；</li>
 *   <li>风暴步（风之）：比空中跳大一号的风，几缕风卷到半径那么远（风系不带电）；</li>
 *   <li>残像（虚空）：原地留一个虚空紫的人影，过一会儿炸开时人影往里一缩、一团暗影 + 柔波 + 火花；</li>
 *   <li>宣判（审判庭）：一道光柱从高处砸进目标、落点爆点 + 柔波，不带电；</li>
 *   <li>超载脉冲（宅男）：胸口一团白热，几道带分叉的电弧贴着地面往外打到半径、地面电流（宅男是 MRU 科技，带电）。</li>
 * </ul>
 * 落点一律不画范围的亮边圈，只用越远越淡的柔波（画风原则）；不加冲击帧、速度线（那是首领大招的）。
 */
final class AwakeningFx {

    /** 还没炸开的残影（炸开的包不带实体，按位置找最近的一个收掉） */
    private static final List<Afterimage> AFTERIMAGES = new ArrayList<>();

    private AwakeningFx() {
    }

    static void surge(@Nullable Entity entity, Vec3 feet, float radius, VfxPlasma.Tint tint) {
        Vec3 chest = entity == null ? feet.add(0D, 1D, 0D) : entity.position().add(0D, entity.getBbHeight() * 0.55D, 0D);
        VfxPlasma.sparks(chest, Vec3.ZERO, 1D, 18F, 0.2D, 0.5D, 6, 14, tint);
        VfxPlasma.embers(chest, 0.9D, 12F, tint);
        VfxEffects.add(new Release(chest, feet, Math.max(1.5F, radius), tint, 5, true));
    }

    static void stormStep(Vec3 feet, float radius, VfxPlasma.Tint tint) {
        VfxEffects.add(new MotionFx.Gust(feet, Math.max(2F, radius), 6, tint));
    }

    static void overcharge(@Nullable Entity entity, Vec3 chest, float radius, VfxPlasma.Tint tint) {
        VfxPlasma.sparks(chest, Vec3.ZERO, 1D, 22F, 0.25D, 0.65D, 6, 14, tint);
        Vec3 feet = entity == null ? chest.subtract(0D, 0.9D, 0D) : entity.position();
        VfxEffects.add(new Release(chest, feet, Math.max(1.5F, radius), tint, 7, false));
    }

    static void sentence(Vec3 at, float damage, VfxPlasma.Tint tint) {
        VfxEffects.add(new Strike(at, damage > 0F ? 1F : 0.75F, tint));
    }

    static void afterimage(@Nullable Entity entity, Vec3 pos, float radius, VfxPlasma.Tint tint) {
        if (radius <= 0F) {
            Afterimage image = new Afterimage(pos, tint);
            AFTERIMAGES.removeIf(a -> a.done);
            if (AFTERIMAGES.size() < 32) {
                AFTERIMAGES.add(image);
                VfxEffects.add(image);
            }
            return;
        }
        // 炸开：pos 是残影胸口（脚下 + 0.9）
        Vec3 feet = pos.subtract(0D, 0.9D, 0D);
        Afterimage nearest = null;
        double best = 4D;
        for (Afterimage a : AFTERIMAGES) {
            double d = a.feet.distanceToSqr(feet);
            if (!a.done && !a.bursting && d < best) {
                best = d;
                nearest = a;
            }
        }
        if (nearest != null) {
            nearest.burst();
        }
        VfxPlasma.sparks(pos, Vec3.ZERO, 1D, 20F, 0.2D, 0.55D, 6, 14, tint);
        VfxPlasma.embers(pos, radius * 0.4D, 10F, tint);
        VfxEffects.add(new VoidBurst(pos, feet, radius, tint));
    }

    // ------------------------------------------------------------------

    /** 回涌 / 超载脉冲：胸口爆点 + 往外鼓的柔光 + 地上柔波 + 往外打的电弧（arcs 道） */
    private static final class Release implements VfxEffect {

        private static final int LIFE = 18;
        private final Vec3 chest;
        private final Vec3 ground;
        private final float radius;
        private final VfxPlasma.Tint tint;
        private final int arcs;
        private final boolean bubble;
        private final int seed = Vfx.random().nextInt(1024);
        private final double[][] paths;
        private final int[] points;
        private final RandomSource shape = RandomSource.create();
        private int age;

        Release(Vec3 chest, Vec3 feet, float radius, VfxPlasma.Tint tint, int arcs, boolean bubble) {
            this.chest = chest;
            this.ground = feet.add(0D, 0.05D, 0D);
            this.radius = radius;
            this.tint = tint;
            this.arcs = arcs;
            this.bubble = bubble;
            this.paths = new double[arcs][VfxPaths.jaggedPoints(4) * 3];
            this.points = new int[arcs];
            reshape();
        }

        /** 电弧形状每两刻换一次（每帧不重算路径） */
        private void reshape() {
            shape.setSeed(seed * 977L + age / 2);
            for (int i = 0; i < arcs; i++) {
                double a = (i + shape.nextDouble() * 0.6D) * Mth.TWO_PI / arcs;
                double r = radius * (0.7D + 0.3D * shape.nextDouble());
                Vec3 to = bubble
                        ? chest.add(Math.cos(a) * r, (shape.nextDouble() - 0.4D) * radius * 0.5D, Math.sin(a) * r)
                        : new Vec3(ground.x + Math.cos(a) * r, ground.y + 0.15D, ground.z + Math.sin(a) * r);
                points[i] = VfxPaths.jagged(shape, chest, to, 4, 0.28F, paths[i]);
            }
        }

        @Override
        public boolean tick() {
            age++;
            if (age % 2 == 0 && age < 8) {
                reshape();
            }
            return age < LIFE;
        }

        @Override
        public void render(VfxDraw draw) {
            if (GearVfx.lod(chest) > 2) {
                return;
            }
            long start = GearVfx.beginTiming();
            float t = age + draw.partialTick();
            Vec3 p = draw.towardCamera(chest, 0.4D);
            if (t < 1.5F) {
                draw.billboard(p, 1.6F, 0F, VfxMode.FLARE, 1000, Vfx.argb(tint.core(), 0.9F));
            }
            VfxPlasma.burst(draw, p, 1.7F, t, 12F, seed, tint);
            if (bubble) {
                float f = Vfx.easeOutExpo(t / 10F);
                float a = (1F - f) * (1F - t / LIFE);
                draw.billboard(chest, 0.8F + radius * 0.75F * f, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.4F * a));
            }
            VfxPlasma.wave(draw, ground, radius, t, 7F, LIFE, 0.9F, tint);
            float stroke = VfxPlasma.strokeAlpha(t, 6F);
            if (stroke > 0.02F) {
                for (int i = 0; i < arcs; i++) {
                    float head = Math.min(1F, t / 2F + 0.2F);
                    if (Vfx.tier() >= 1) {
                        shape.setSeed(seed + i * 53L);
                        VfxPlasma.forkedArc(draw, shape, paths[i], points[i], head, 0.045F, stroke, seed + i, 2, radius * 0.25F, tint);
                    } else {
                        VfxPlasma.arc(draw, paths[i], points[i], head, 0.045F, stroke, seed + i, tint);
                    }
                }
            }
            if (!bubble && t < 10F) {
                shape.setSeed(seed * 31L + age / 2);
                VfxPlasma.groundCrawl(draw, shape, ground, radius * 0.8F, 5, 0.05F, 1F - t / 10F, tint);
            }
            GearVfx.endTiming(start);
        }

        @Override
        public AABB bounds() {
            return new AABB(chest, chest).inflate(radius + 2D);
        }
    }

    /** 宣判：光柱从高处砸进目标，落点爆点 + 柔波 + 往上飘的余辉 */
    private static final class Strike implements VfxEffect {

        private static final int DROP = 3;
        private static final int LIFE = 20;
        private final Vec3 at;
        private final float scale;
        private final VfxPlasma.Tint tint;
        private final int seed = Vfx.random().nextInt(1024);
        private int age;

        Strike(Vec3 at, float scale, VfxPlasma.Tint tint) {
            this.at = at;
            this.scale = scale;
            this.tint = tint;
        }

        @Override
        public boolean tick() {
            age++;
            if (age == DROP) {
                VfxPlasma.sparks(at, new Vec3(0D, 1D, 0D), 1.2D, 18F * scale, 0.2D, 0.5D, 6, 14, tint);
                VfxPlasma.embers(at, 0.8D, 10F * scale, tint);
            }
            return age < LIFE;
        }

        @Override
        public void render(VfxDraw draw) {
            if (GearVfx.lod(at) > 2) {
                return;
            }
            long start = GearVfx.beginTiming();
            float t = age + draw.partialTick();
            Vec3 top = at.add(0D, 9D, 0D);
            if (t < DROP) {
                float f = Vfx.easeInCubic(t / DROP);
                VfxPlasma.beam(draw, top, top.add(0D, (at.y - top.y) * f, 0D), 0.35F * scale, 1F, false, seed, tint);
            } else if (t < DROP + 8F) {
                float a = 1F - (t - DROP) / 8F;
                VfxPlasma.beam(draw, top, at, 0.35F * scale * (0.5F + 0.5F * a), a, true, seed, tint);
            }
            if (t >= DROP) {
                float s = t - DROP;
                Vec3 p = draw.towardCamera(at, 0.5D);
                if (s < 1F) {
                    draw.billboard(p, 1.8F * scale, 0F, VfxMode.FLARE, 1000, Vfx.argb(tint.core(), 0.9F));
                }
                VfxPlasma.burst(draw, p, 1.8F * scale, s, 12F, seed, tint);
                Vec3 ground = new Vec3(at.x, Math.floor(at.y - 0.5D) + 0.05D, at.z);
                VfxPlasma.wave(draw, ground, 3.2F * scale, s, 6F, LIFE - DROP, 0.8F, tint);
            }
            GearVfx.endTiming(start);
        }

        @Override
        public AABB bounds() {
            return new AABB(at, at.add(0D, 9D, 0D)).inflate(4D);
        }
    }

    /** 残影：脚下到头顶一团虚空紫的人影 + 往上漂的暗光点；炸开时往里一缩（最多留 3 秒） */
    private static final class Afterimage implements VfxEffect {

        private static final int MAX_LIFE = 60;
        private static final int SHRINK = 4;
        private final Vec3 feet;
        private final VfxPlasma.Tint tint;
        private final int seed = Vfx.random().nextInt(1024);
        private int age;
        private int burstAt = -1;
        boolean bursting;
        boolean done;

        Afterimage(Vec3 feet, VfxPlasma.Tint tint) {
            this.feet = feet;
            this.tint = tint;
        }

        void burst() {
            bursting = true;
            burstAt = age;
        }

        @Override
        public boolean tick() {
            age++;
            if (age % 3 == 0 && !bursting) {
                VfxPlasma.embers(feet.add(0D, 0.3D, 0D), 0.35D, 1.5F, tint);
            }
            done = bursting ? age - burstAt >= SHRINK : age >= MAX_LIFE;
            return !done;
        }

        @Override
        public void render(VfxDraw draw) {
            if (GearVfx.lod(feet) > 2) {
                return;
            }
            long start = GearVfx.beginTiming();
            float t = age + draw.partialTick();
            float in = Math.min(1F, t / 4F);
            float out = bursting ? Math.max(0F, 1F - (t - burstAt) / SHRINK) : Math.min(1F, (MAX_LIFE - t) / 10F);
            float k = in * out;
            float squeeze = bursting ? out : 1F;
            float breathe = 0.9F + 0.1F * Mth.sin(t * 0.3F + seed);
            Vec3 body = feet.add(0D, 0.85D, 0D);
            Vec3 head = feet.add(0D, 1.6D, 0D);
            draw.billboard(body.x, body.y, body.z, 0.5F * squeeze, 1F * squeeze, 0F, VfxMode.SMOKE, seed, Vfx.argb(VfxPalette.SMOKE, 0.5F * k), true);
            draw.billboard(body.x, body.y, body.z, 0.42F * squeeze * breathe, 0.95F * squeeze, 0F, VfxMode.DOT, 0, Vfx.argb(tint.inner(), 0.6F * k), false);
            draw.billboard(head.x, head.y, head.z, 0.24F * squeeze, 0.24F * squeeze, 0F, VfxMode.DOT, 200, Vfx.argb(tint.inner(), 0.55F * k), false);
            if (Vfx.tier() >= 1) {
                draw.billboard(body.x, body.y, body.z, 0.9F * squeeze, 1.4F * squeeze, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.3F * k), false);
            }
            GearVfx.endTiming(start);
        }

        @Override
        public AABB bounds() {
            return new AABB(feet, feet.add(0D, 2D, 0D)).inflate(1.5D);
        }
    }

    /** 残影炸开：一团往外翻的暗影（半透明压暗）+ 爆点 + 柔波 */
    private static final class VoidBurst implements VfxEffect {

        private static final int LIFE = 18;
        private final Vec3 at;
        private final Vec3 ground;
        private final float radius;
        private final VfxPlasma.Tint tint;
        private final int seed = Vfx.random().nextInt(1024);
        private int age;

        VoidBurst(Vec3 at, Vec3 feet, float radius, VfxPlasma.Tint tint) {
            this.at = at;
            this.ground = feet.add(0D, 0.05D, 0D);
            this.radius = Math.max(1.5F, radius);
            this.tint = tint;
        }

        @Override
        public boolean tick() {
            return ++age < LIFE;
        }

        @Override
        public void render(VfxDraw draw) {
            if (GearVfx.lod(at) > 2) {
                return;
            }
            long start = GearVfx.beginTiming();
            float t = age + draw.partialTick();
            float f = Vfx.easeOutExpo(t / 10F);
            float k = 1F - t / LIFE;
            draw.billboard(at.x, at.y, at.z, 0.6F + radius * 0.5F * f, 0.6F + radius * 0.5F * f, t * 0.05F, VfxMode.SMOKE, seed,
                    Vfx.argb(VfxPalette.SMOKE, 0.55F * k), true);
            Vec3 p = draw.towardCamera(at, 0.4D);
            VfxPlasma.burst(draw, p, 1.5F, t, 12F, seed, tint);
            VfxPlasma.wave(draw, ground, radius, t, 7F, LIFE, 0.85F, tint);
            GearVfx.endTiming(start);
        }

        @Override
        public AABB bounds() {
            return new AABB(at, at).inflate(radius + 1D);
        }
    }

    /** 换世界时清掉还挂着的残影引用 */
    static void clear() {
        AFTERIMAGES.clear();
    }
}
