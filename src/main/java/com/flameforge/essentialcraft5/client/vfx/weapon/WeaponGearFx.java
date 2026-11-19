package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.GearFxClient;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 武器同调的特效事件（{@link GearFx} 里武器的那八种）的正式画法，替掉 {@link GearFxClient} 里的占位；盔甲那几种和升阶、战痕不在这里。
 * 裁决之刃那几种用裁决之刃的家族色，枪用 MRU 枪的家族色，元素爆发按元素（{@link ElementFx}）。
 */
public final class WeaponGearFx {

    private static final Map<Integer, SealMark> SEALS = new HashMap<>();

    private WeaponGearFx() {
    }

    private static VfxPlasma.Tint verdict() {
        return WeaponTints.family(ECAttunement.VERDICT_BLADE);
    }

    private static VfxPlasma.Tint gun() {
        return WeaponTints.family(ECAttunement.MRU_GUN);
    }

    /** 客户端初始化时调一次 */
    public static void register() {
        GearFxClient.register(GearFx.EXECUTE, (level, entity, pos, param) -> {
            VfxPlasma.Tint tint = verdict();
            WeaponHitFx.on(entity, pos, tint, 1.5F, 12).slash().wave(2F).play();
            VfxPlasma.sparks(pos, new Vec3(0D, -1D, 0D), 0.9D, 14F, 0.15D, 0.45D, 6, 14, tint);
        });
        GearFxClient.register(GearFx.LIFESTEAL, (level, entity, pos, param) -> {
            if (entity != null) {
                drain(pos, entity, Mth.clamp(param, 1F, 8F), verdict());
            }
        });
        GearFxClient.register(GearFx.ARMOR_PEN, (level, entity, pos, param) -> {
            WeaponHitFx.on(entity, pos, VfxPlasma.MRU, 0.7F, 7).play();
            VfxPlasma.sparks(pos, Vec3.ZERO, 1D, 6F, 0.12D, 0.3D, 4, 9, VfxPlasma.MRU);
        });
        GearFxClient.register(GearFx.VERDICT_SEAL, (level, entity, pos, param) -> {
            if (entity == null) {
                return;
            }
            SealMark old = SEALS.get(entity.getId());
            if (old != null) {
                old.life = 0;
            }
            SealMark mark = new SealMark(entity, Mth.clamp((int) param, 20, 400), verdict());
            SEALS.put(entity.getId(), mark);
            VfxEffects.add(mark);
        });
        GearFxClient.register(GearFx.VERDICT_BURST, (level, entity, pos, param) -> {
            if (entity != null) {
                SealMark mark = SEALS.remove(entity.getId());
                if (mark != null) {
                    mark.life = 0;
                }
            }
            VfxPlasma.Tint tint = verdict();
            WeaponHitFx.on(entity, pos, tint, 1.9F, 16).strikeDown().wave(2.6F).electric(4).play();
            VfxPlasma.sparks(pos, new Vec3(0D, 1D, 0D), 1.2D, 24F, 0.2D, 0.6D, 8, 18, tint);
            VfxPlasma.embers(pos.subtract(0D, 0.6D, 0D), 0.8D, 10F, tint);
        });
        GearFxClient.register(GearFx.ELEMENT_BURST, (level, entity, pos, param) -> ElementFx.burst(pos, (int) param));
        GearFxClient.register(GearFx.OVERLOAD_READY, (level, entity, pos, param) -> {
            if (entity != null) {
                VfxEffects.add(new OverloadReady(entity, gun()));
            }
        });
        GearFxClient.register(GearFx.OVERLOAD_FIRE, (level, entity, pos, param) -> {
            VfxPlasma.Tint tint = gun();
            float radius = Math.max(1F, param);
            WeaponHitFx.at(pos, tint, 2.2F, 16).wave(radius + 1F).electric(6).play();
            VfxPlasma.sparks(pos, Vec3.ZERO, 0D, 30F, 0.2D, 0.7D, 8, 18, tint);
            VfxPlasma.embers(pos, radius * 0.7D, 12F, tint);
        });
    }

    static void clear() {
        SEALS.clear();
    }

    /** 吸血：一串光点从目标身上飞回持剑人胸口 */
    private static void drain(Vec3 from, Entity to, float amount, VfxPlasma.Tint tint) {
        if (!Vfx.visibleAt(from)) {
            return;
        }
        RandomSource r = Vfx.random();
        Vec3 chest = to.position().add(0D, to.getBbHeight() * 0.6D, 0D);
        int n = Vfx.count(3F + amount, from);
        for (int i = 0; i < n; i++) {
            Vec3 start = from.add(VfxPlasma.randomDir(r).scale(0.35D));
            int life = 8 + r.nextInt(5);
            Vec3 v = chest.subtract(start).scale(1D / life);
            VfxParticles.spawn(start.x, start.y, start.z, v.x, v.y, v.z, life, 0.07F, 0.04F, Vfx.argb(tint.core(), 0.95F),
                    Vfx.argb(tint.glow(), 0.2F), VfxMode.SPARK, 0, 1F, 0F, 1.4F, 0F);
        }
    }

    /** 罪印：目标头顶一枚慢慢转的星芒套一圈细环，引爆或到时就收掉 */
    private static final class SealMark implements VfxEffect {

        private final Entity target;
        private final VfxPlasma.Tint tint;
        int life;
        private int age;

        SealMark(Entity target, int life, VfxPlasma.Tint tint) {
            this.target = target;
            this.life = life;
            this.tint = tint;
        }

        @Override
        public boolean tick() {
            age++;
            if (age >= life || target.isRemoved() || !target.isAlive()) {
                SEALS.remove(target.getId(), this);
                return false;
            }
            return true;
        }

        @Override
        public void render(VfxDraw draw) {
            long t0 = WeaponVfxStats.start();
            float t = age + draw.partialTick();
            float in = Mth.clamp(t / 4F, 0F, 1F);
            float out = Mth.clamp((life - t) / 10F, 0F, 1F);
            float a = in * out * (0.85F + 0.15F * Mth.sin(t * 0.5F));
            Vec3 p = target.getPosition(draw.partialTick()).add(0D, target.getBbHeight() + 0.55D, 0D);
            draw.billboard(p, 0.32F, t * 0.05F, VfxMode.FLARE, 900, Vfx.argb(tint.core(), 0.9F * a));
            draw.billboard(p, 0.42F, -t * 0.08F, VfxMode.RING, 60, Vfx.argb(tint.inner(), 0.8F * a));
            draw.billboard(p, 0.8F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.3F * a));
            WeaponVfxStats.stop(t0);
        }

        @Override
        public AABB bounds() {
            return target.getBoundingBox().inflate(1D, 1.5D, 1D);
        }
    }

    /** 过载充满：枪口那边一圈电晕噼啪几下、往里收的细环 */
    private static final class OverloadReady implements VfxEffect {

        private static final int LIFE = 14;
        private final Entity shooter;
        private final VfxPlasma.Tint tint;
        private int seed = Vfx.random().nextInt(100000);
        private int age;

        OverloadReady(Entity shooter, VfxPlasma.Tint tint) {
            this.shooter = shooter;
            this.tint = tint;
            VfxPlasma.sparks(hand(shooter, 1F), Vec3.ZERO, 0D, 10F, 0.06D, 0.18D, 5, 10, tint);
        }

        private static Vec3 hand(Entity e, float pt) {
            Vec3 pos = e.getPosition(pt);
            float yaw = e.getViewYRot(pt) * Mth.DEG_TO_RAD;
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            return pos.add(fx * 0.55D - fz * 0.35D, e.getBbHeight() * 0.6D, fz * 0.55D + fx * 0.35D);
        }

        @Override
        public boolean tick() {
            age++;
            if ((age & 1) == 0) {
                seed = Vfx.random().nextInt(100000);
            }
            return age < LIFE && !shooter.isRemoved();
        }

        @Override
        public void render(VfxDraw draw) {
            long t0 = WeaponVfxStats.start();
            float t = age + draw.partialTick();
            float k = 1F - t / LIFE;
            Vec3 h = hand(shooter, draw.partialTick());
            VfxPlasma.corona(draw, RandomSource.create(seed), h, 0.6F, 3, 0.025F, k, tint);
            draw.billboard(h, 0.9F * (1F - Vfx.easeOutCubic(t / LIFE)) + 0.1F, 0F, VfxMode.RING, 50, Vfx.argb(tint.inner(), k));
            draw.billboard(h, 0.25F, t * 0.1F, VfxMode.FLARE, 900, Vfx.argb(tint.core(), k));
            WeaponVfxStats.stop(t0);
        }

        @Override
        public AABB bounds() {
            return shooter.getBoundingBox().inflate(1.5D);
        }
    }
}
