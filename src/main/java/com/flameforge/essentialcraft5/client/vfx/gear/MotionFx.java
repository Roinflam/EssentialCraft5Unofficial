package com.flameforge.essentialcraft5.client.vfx.gear;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 装备动作的一次性画面：宅男套装喷射、虚空闪避、审判庭定罪、风之空中跳（和风暴步共用一套风）。
 * 都很短（半秒到两秒），跟着实体的跟着实体，实体在客户端没了就停在最后的位置上放完。
 */
final class MotionFx {

    private MotionFx() {
    }

    /** 一次性画面的共同部分：寿命、跟随、包围盒 */
    abstract static class Shot implements VfxEffect {

        @Nullable
        final Entity entity;
        final int life;
        final float reach;
        final int seed = Vfx.random().nextInt(1024);
        Vec3 at;
        Vec3 atO;
        int age;

        Shot(@Nullable Entity entity, Vec3 at, int life, float reach) {
            this.entity = entity;
            this.at = at;
            this.atO = at;
            this.life = life;
            this.reach = reach;
        }

        /** 跟着实体时 at 每刻更新成 follow(实体)；不跟的返回 null */
        @Nullable
        Vec3 follow(Entity e) {
            return null;
        }

        @Override
        public boolean tick() {
            atO = at;
            if (entity != null && entity.isAlive() && !entity.isRemoved()) {
                Vec3 p = follow(entity);
                if (p != null) {
                    at = p;
                }
            }
            age++;
            onTick();
            return age < life;
        }

        void onTick() {
        }

        Vec3 at(float pt) {
            return atO == at ? at : new Vec3(Mth.lerp(pt, atO.x, at.x), Mth.lerp(pt, atO.y, at.y), Mth.lerp(pt, atO.z, at.z));
        }

        @Override
        public AABB bounds() {
            return new AABB(at, at).inflate(reach);
        }
    }

    // ------------------------------------------------------------------ 喷射

    /**
     * 宅男套装喷射：起飞点炸开一团（爆点 + 往后喷的火花 + 一圈垂直于冲刺方向往外推的柔波，没有亮边），
     * 之后十几刻里身后拖一道四层等离子尾焰，越拖越淡。
     */
    static final class JetDash extends Shot {

        private static final int TRAIL = 14;
        private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
        private final Vec3 dir;
        private final Vec3 launch;
        private final double[] ring = new double[TRAIL * 3];
        private final double[] xyz = new double[(TRAIL + 1) * 3];
        private int head;
        private int recorded;

        JetDash(@Nullable Entity entity, Vec3 feet, Vec3 dir, float speed) {
            super(entity, feet.add(0D, 0.9D, 0D), 22, 6F + speed * 4F);
            this.dir = dir.lengthSqr() < 1.0E-6D ? new Vec3(0D, 0D, 1D) : dir.normalize();
            this.launch = at;
            push(at);
            Vec3 back = this.dir.scale(-1D);
            VfxPlasma.sparks(launch, back, 0.45D, 22F, 0.25D, 0.7D, 6, 14, TINT);
            VfxPlasma.embers(launch, 0.5D, 8F, TINT);
            // 往后喷的几缕热气：慢的品红光点
            RandomSource random = Vfx.random();
            int n = Vfx.count(10F, launch);
            for (int i = 0; i < n; i++) {
                Vec3 d = back.add(VfxPlasma.randomDir(random).scale(0.5D)).normalize().scale(0.08D + random.nextDouble() * 0.12D);
                VfxParticles.spawn(launch.x, launch.y, launch.z, d.x, d.y, d.z, 10 + random.nextInt(8), 0.25F, 0.05F,
                        Vfx.argb(TINT.inner(), 0.6F), Vfx.argb(TINT.outer(), 0F), VfxMode.DOT, 0, 0.88F, 0F, 0F, 0F);
            }
        }

        @Override
        Vec3 follow(Entity e) {
            return e.position().add(0D, e.getBbHeight() * 0.5D, 0D);
        }

        @Override
        void onTick() {
            if (age < TRAIL - 2) {
                push(at);
            } else {
                recorded = Math.max(0, recorded - 2);
            }
        }

        private void push(Vec3 p) {
            head = (head + 1) % TRAIL;
            ring[head * 3] = p.x;
            ring[head * 3 + 1] = p.y;
            ring[head * 3 + 2] = p.z;
            recorded = Math.min(TRAIL, recorded + 1);
        }

        @Override
        public void render(VfxDraw draw) {
            float pt = draw.partialTick();
            float t = age + pt;
            int lod = GearVfx.lod(at);
            if (lod > 2) {
                return;
            }
            long start = GearVfx.beginTiming();
            // 起飞点：爆点 + 垂直于冲刺方向的柔波
            if (t < 10F) {
                Vec3 p = draw.towardCamera(launch, 0.3D);
                if (t < 1F) {
                    draw.billboard(p, 1.2F, 0F, VfxMode.FLARE, 1000, Vfx.argb(TINT.core(), 0.9F));
                }
                VfxPlasma.burst(draw, p, 1.3F, t, 9F, seed, TINT);
            }
            if (t < 12F && lod <= 1) {
                float f = Vfx.easeOutExpo(t / 8F);
                float r = 0.5F + 2.4F * f;
                float a = (1F - f * f) * (1F - t / 12F);
                Vec3 e1 = dir.cross(Math.abs(dir.y) < 0.9D ? new Vec3(0D, 1D, 0D) : new Vec3(1D, 0D, 0D)).normalize().scale(r / 0.7D);
                Vec3 e2 = dir.cross(e1).normalize().scale(r / 0.7D);
                Vec3 c = launch.subtract(dir.scale(0.4D + t * 0.06D));
                draw.plane(c.x, c.y, c.z, (float) e1.x, (float) e1.y, (float) e1.z, (float) e2.x, (float) e2.y, (float) e2.z,
                        VfxMode.WAVE, 220, Vfx.argb(TINT.glow(), 0.85F * a), false);
                if (Vfx.tier() >= 1) {
                    draw.plane(c.x, c.y, c.z, (float) (e1.x * 0.97D), (float) (e1.y * 0.97D), (float) (e1.z * 0.97D), (float) (e2.x * 0.97D),
                            (float) (e2.y * 0.97D), (float) (e2.z * 0.97D), VfxMode.WAVE, 440, Vfx.argb(TINT.outer(), 0.5F * a), false);
                }
            }
            // 身后的尾焰：第一个点用这一帧插值出来的位置
            if (recorded >= 2) {
                Vec3 h = at(pt);
                xyz[0] = h.x;
                xyz[1] = h.y;
                xyz[2] = h.z;
                int n = 1;
                for (int i = 0; i < recorded; i++) {
                    int k = Math.floorMod(head - i, TRAIL) * 3;
                    xyz[n * 3] = ring[k];
                    xyz[n * 3 + 1] = ring[k + 1];
                    xyz[n * 3 + 2] = ring[k + 2];
                    n++;
                }
                float fade = 1F - Mth.clamp((t - 6F) / 16F, 0F, 1F);
                VfxPlasma.trail(draw, xyz, n, 0.42F, fade, seed, TINT);
            }
            GearVfx.endTiming(start);
        }

        @Override
        public AABB bounds() {
            return new AABB(launch, at).inflate(3D);
        }
    }

    // ------------------------------------------------------------------ 虚空闪避

    /**
     * 虚空闪避：人原地「相位」了一下——身形处留下一团虚空紫的影子，几道横着的错位光条在身上乱跳（像信号断了一下），
     * 四周的光点往里吸，最后一点星芒收掉。配色取虚空盔甲。
     */
    static final class VoidDodge extends Shot {

        private final VfxPlasma.Tint tint;
        private final RandomSource glitch = RandomSource.create();

        VoidDodge(@Nullable Entity entity, Vec3 feet, VfxPlasma.Tint tint) {
            super(entity, feet, 12, 3F);
            this.tint = tint;
            RandomSource random = Vfx.random();
            Vec3 c = feet.add(0D, 0.9D, 0D);
            int n = Vfx.count(16F, c);
            for (int i = 0; i < n; i++) {
                Vec3 d = VfxPlasma.randomDir(random);
                double r = 0.9D + random.nextDouble() * 0.5D;
                int life = 6 + random.nextInt(4);
                // 从壳上往心里飞，寿命刚好飞到
                VfxParticles.spawn(c.x + d.x * r, c.y + d.y * r * 1.3D, c.z + d.z * r, -d.x * r / life, -d.y * r * 1.3D / life, -d.z * r / life,
                        life, 0.05F, 0.02F, Vfx.argb(tint.core(), 0.9F), Vfx.argb(tint.glow(), 0F), VfxMode.SPARK, 0, 1F, 0F, 1.4F, 0F);
            }
        }

        @Override
        public void render(VfxDraw draw) {
            float pt = draw.partialTick();
            float t = age + pt;
            if (GearVfx.lod(at) > 2) {
                return;
            }
            long start = GearVfx.beginTiming();
            float k = 1F - t / life;
            Vec3 body = at.add(0D, 0.9D, 0D);
            // 暗的底影（半透明混合，压下去一块）+ 虚空紫的身形光
            draw.billboard(body.x, body.y, body.z, 0.55F, 1.05F, 0F, VfxMode.SMOKE, seed, Vfx.argb(VfxPalette.SMOKE, 0.45F * k), true);
            draw.billboard(body.x, body.y, body.z, 0.42F, 0.95F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.5F * k), false);
            // 错位光条：两刻换一次位置
            glitch.setSeed(seed * 131L + (age >> 1));
            int bars = Vfx.tier() >= 1 ? 5 : 3;
            for (int i = 0; i < bars; i++) {
                double y = at.y + 0.15D + glitch.nextDouble() * 1.65D;
                double off = (glitch.nextDouble() - 0.5D) * 0.7D;
                float w = 0.3F + glitch.nextFloat() * 0.35F;
                Vec3 side = horizontalSide(draw.camera());
                Vec3 a = new Vec3(at.x + side.x * (off - w), y, at.z + side.z * (off - w));
                Vec3 b = new Vec3(at.x + side.x * (off + w), y, at.z + side.z * (off + w));
                int rgb = glitch.nextFloat() < 0.35F ? tint.core() : tint.inner();
                draw.strip(a, b, 0.035F, 0.035F, Vfx.argb(rgb, 0.9F * k), Vfx.argb(rgb, 0.9F * k), VfxMode.STRIP, 600, 0F, 1F);
            }
            if (t > life - 4F) {
                float f = (t - (life - 4F)) / 4F;
                draw.billboard(draw.towardCamera(body, 0.3D), 0.6F * (1F - f), t * 0.3F, VfxMode.FLARE, 800, Vfx.argb(tint.core(), 1F - f));
            }
            GearVfx.endTiming(start);
        }

        /** 屏幕上的横向（水平面里垂直于视线的方向） */
        private Vec3 horizontalSide(Vec3 camera) {
            double dx = at.x - camera.x, dz = at.z - camera.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            return len < 1.0E-4D ? new Vec3(1D, 0D, 0D) : new Vec3(-dz / len, 0D, dx / len);
        }
    }

    // ------------------------------------------------------------------ 定罪

    /**
     * 审判庭定罪：一道细光矛从头顶上方落进目标胸口，钉进去那一下一个小爆点，之后两秒里三颗光点绕着目标转、头顶一点星芒在闪，
     * 标出「这个被记上了」。配色是审判庭的品红（同哨兵刷怪蛋的斑点色），不带电。
     */
    static final class Convict extends Shot {

        private static final int DROP = 4;
        private static final VfxPlasma.Tint TINT = VfxPlasma.tint(0xE040FF);
        private final float width;
        private final float height;

        Convict(@Nullable Entity entity, Vec3 at, int markTicks) {
            super(entity, at, Mth.clamp(markTicks, 20, 50), 4F);
            this.width = entity == null ? 0.6F : entity.getBbWidth();
            this.height = entity == null ? 1.8F : entity.getBbHeight();
        }

        @Override
        Vec3 follow(Entity e) {
            return e.position().add(0D, e.getBbHeight() * 0.6D, 0D);
        }

        @Override
        void onTick() {
            if (age == DROP) {
                VfxPlasma.sparks(at, Vec3.ZERO, 1D, 10F, 0.1D, 0.3D, 5, 10, TINT);
            }
        }

        @Override
        public void render(VfxDraw draw) {
            float pt = draw.partialTick();
            float t = age + pt;
            Vec3 c = at(pt);
            int lod = GearVfx.lod(c);
            if (lod > 2) {
                return;
            }
            long start = GearVfx.beginTiming();
            if (t < DROP + 3F) {
                float f = Vfx.easeInCubic(Math.min(1F, t / DROP));
                Vec3 top = c.add(0D, height * 0.4D + 3.2D, 0D);
                Vec3 tip = top.add(0D, (c.y - top.y) * f, 0D);
                float a = t < DROP ? 1F : 1F - (t - DROP) / 3F;
                VfxPlasma.beam(draw, top, tip, 0.1F, a, false, seed, TINT);
            }
            if (t >= DROP) {
                VfxPlasma.burst(draw, draw.towardCamera(c, 0.4D), 0.8F, t - DROP, 10F, seed, TINT);
            }
            float mark = Mth.clamp((t - DROP) / 4F, 0F, 1F) * Math.min(1F, (life - t) / 10F);
            if (mark > 0.01F && lod <= 1) {
                double r = Math.max(0.45D, width * 0.85D);
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.18D + i * Mth.TWO_PI / 3D;
                    double y = c.y + Math.sin(t * 0.11D + i * 2.1D) * 0.2D;
                    draw.billboard(c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r, 0.09F, 0.09F, 0F, VfxMode.DOT, 700,
                            Vfx.argb(TINT.core(), 0.95F * mark), false);
                    draw.billboard(c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r, 0.26F, 0.26F, 0F, VfxMode.DOT, 0,
                            Vfx.argb(TINT.glow(), 0.4F * mark), false);
                }
                float blink = 0.7F + 0.3F * Mth.sin(t * 0.5F);
                Vec3 crown = c.add(0D, height * 0.4D + 0.45D, 0D);
                draw.billboard(crown, 0.28F * blink, 0F, VfxMode.FLARE, 700, Vfx.argb(TINT.inner(), 0.85F * mark));
            }
            GearVfx.endTiming(start);
        }
    }

    // ------------------------------------------------------------------ 风

    /**
     * 风之空中跳 / 风暴步：脚下一圈没有亮边的柔波往外推，几缕风沿着半圈往外、往下卷开，一捧淡色的气往下散。
     * 风系不带电（画风原则）。strength 1 = 普通空中跳；风暴步按半径放大、多卷几缕。
     */
    static final class Gust extends Shot {

        private static final int POINTS = 9;
        private final VfxPlasma.Tint tint;
        private final float radius;
        private final int curls;
        private final double[] xyz = new double[POINTS * 3];
        private final float[] half = new float[POINTS];
        private final int[] argb = new int[POINTS];
        private final float[] u = new float[POINTS];

        Gust(Vec3 feet, float radius, int curls, VfxPlasma.Tint tint) {
            super(null, feet, 14, radius + 1F);
            this.tint = tint;
            this.radius = radius;
            this.curls = curls;
            RandomSource random = Vfx.random();
            int n = Vfx.count(6F + curls * 2F, feet);
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Mth.TWO_PI;
                double s = 0.08D + random.nextDouble() * 0.1D * radius;
                VfxParticles.spawn(feet.x, feet.y + 0.1D, feet.z, Math.cos(a) * s, -0.04D - random.nextDouble() * 0.04D, Math.sin(a) * s,
                        10 + random.nextInt(8), 0.22F, 0.4F, Vfx.argb(tint.inner(), 0.35F), Vfx.argb(tint.glow(), 0F), VfxMode.DOT, 0, 0.82F, 0F, 0F,
                        0F);
            }
        }

        @Override
        public void render(VfxDraw draw) {
            float pt = draw.partialTick();
            float t = age + pt;
            int lod = GearVfx.lod(at);
            if (lod > 2) {
                return;
            }
            long start = GearVfx.beginTiming();
            Vec3 ground = at.add(0D, 0.05D, 0D);
            VfxPlasma.wave(draw, ground, radius, t, 6F, life, 0.9F, tint);
            int k = lod == 0 ? curls : Math.max(2, curls / 2);
            float f = Vfx.easeOutCubic(t / life);
            float fade = 1F - f;
            for (int c = 0; c < k; c++) {
                // 每缕从脚边的一个角度开始，往外扩、往下沉、顺着转
                double a0 = seed * 0.37D + c * Mth.TWO_PI / k + t * 0.12D;
                double r0 = 0.25D + radius * 0.75D * f;
                double y0 = at.y + 0.25D - 0.35D * f;
                double sweep = 2.2D;
                for (int i = 0; i < POINTS; i++) {
                    double s = i / (double) (POINTS - 1);
                    double a = a0 - sweep * s;
                    double r = r0 * (1D - 0.35D * s);
                    xyz[i * 3] = at.x + Math.cos(a) * r;
                    xyz[i * 3 + 1] = y0 + 0.12D * s;
                    xyz[i * 3 + 2] = at.z + Math.sin(a) * r;
                    float taper = (float) (Math.sin(Math.PI * s));
                    half[i] = 0.07F * taper;
                    argb[i] = Vfx.argb(tint.glow(), 0.55F * fade * taper);
                    u[i] = (float) (s * r0 * sweep);
                }
                draw.ribbon(xyz, POINTS, half, argb, u, VfxMode.STRIP, 70);
                if (lod == 0) {
                    for (int i = 0; i < POINTS; i++) {
                        float taper = (float) Math.sin(Math.PI * i / (POINTS - 1));
                        half[i] = 0.018F * taper;
                        argb[i] = Vfx.argb(tint.core(), 0.8F * fade * taper);
                    }
                    draw.ribbon(xyz, POINTS, half, argb, u, VfxMode.STRIP, 650);
                }
            }
            GearVfx.endTiming(start);
        }
    }
}
