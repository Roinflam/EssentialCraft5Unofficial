package com.flameforge.essentialcraft5.client.vfx.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 常驻特效（尾迹、翅膀、穹顶）跟着的东西：真玩家，或演示时摆的假人（{@link Fake}，只在客户端，压测 20 个同屏用）。
 */
public interface GearAnchor {

    Vec3 pos(float partialTick);

    /** 身体朝向（度，0 = 朝 +z，同原版 yBodyRot） */
    float bodyYaw(float partialTick);

    boolean crouching();

    boolean alive();

    /** 本机玩家自己、第一人称（背后、脚下的东西大半看不见） */
    boolean firstPerson();

    AABB box();

    static GearAnchor of(LivingEntity entity) {
        return new Living(entity);
    }

    record Living(LivingEntity entity) implements GearAnchor {

        @Override
        public Vec3 pos(float partialTick) {
            return entity.getPosition(partialTick);
        }

        @Override
        public float bodyYaw(float partialTick) {
            return Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        }

        @Override
        public boolean crouching() {
            return entity.isCrouching();
        }

        @Override
        public boolean alive() {
            return entity.isAlive() && !entity.isRemoved();
        }

        @Override
        public boolean firstPerson() {
            Minecraft mc = Minecraft.getInstance();
            return entity instanceof LocalPlayer && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == entity;
        }

        @Override
        public AABB box() {
            return entity.getBoundingBox();
        }
    }

    /** 演示用的假人：绕 center 转圈（period 刻一圈，0 = 原地不动），身体朝前进方向 */
    final class Fake implements GearAnchor {

        private final Vec3 center;
        private final double radius;
        private final int period;
        private final float phase;
        private int age;
        public boolean alive = true;

        public Fake(Vec3 center, double radius, int period, float phase) {
            this.center = center;
            this.radius = radius;
            this.period = period;
            this.phase = phase;
        }

        public void tick() {
            age++;
        }

        private double angle(float partialTick) {
            return phase + (period <= 0 ? 0D : (age + partialTick) * Mth.TWO_PI / period);
        }

        @Override
        public Vec3 pos(float partialTick) {
            double a = angle(partialTick);
            return center.add(Math.cos(a) * radius, 0D, Math.sin(a) * radius);
        }

        @Override
        public float bodyYaw(float partialTick) {
            double a = angle(partialTick);
            // 逆时针绕圈时前进方向是切线 (-sin, cos)；原版朝向 yaw 的前方是 (-sin yaw, cos yaw)
            double fx = -Math.sin(a);
            double fz = Math.cos(a);
            if (period <= 0) {
                fx = Math.cos(a);
                fz = Math.sin(a);
            }
            return (float) (Mth.atan2(-fx, fz) * Mth.RAD_TO_DEG);
        }

        @Override
        public boolean crouching() {
            return false;
        }

        @Override
        public boolean alive() {
            return alive;
        }

        @Override
        public boolean firstPerson() {
            return false;
        }

        @Override
        public AABB box() {
            Vec3 p = pos(0F);
            return new AABB(p.x - 0.3D, p.y, p.z - 0.3D, p.x + 0.3D, p.y + 1.8D, p.z + 0.3D);
        }
    }
}
