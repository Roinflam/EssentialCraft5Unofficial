package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.item.WindSwordItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 风之剑挥砍：身前一道斜着扫过去的月牙形风痕（拖尾的层次、风的青绿色），扫过的地方甩出几粒光尘。非电系，不冒电。
 * 客户端看挥手动画自己画（别人挥手也有原版的动画包），打没打中都画。
 */
final class WindSlashFx implements VfxEffect {

    private static final VfxPlasma.Tint TINT = WeaponTints.WIND;
    private static final int SWEEP = 3;
    private static final int LIFE = 8;
    private static final int POINTS = 12;
    private static final double RADIUS = 1.7D;
    private static final double SPAN = Math.toRadians(150D);

    private final Player player;
    private final double yaw;
    private final double side;
    private final double tilt;
    private final double[] path = new double[POINTS * 3];
    private final int seed = Vfx.random().nextInt(100000);
    private int age;

    WindSlashFx(Player player) {
        this.player = player;
        this.yaw = player.getYRot() * Mth.DEG_TO_RAD;
        HumanoidArm arm = player.swingingArm == InteractionHand.OFF_HAND ? player.getMainArm().getOpposite() : player.getMainArm();
        this.side = arm == HumanoidArm.RIGHT ? 1D : -1D;
        // 每一下斜的方向不一样，连砍时看得出是几刀
        this.tilt = (Vfx.random().nextDouble() * 0.6D + 0.2D) * (Vfx.random().nextBoolean() ? 1D : -1D);
    }

    static void watch(ClientLevel level) {
        for (Player player : level.players()) {
            if (player.swinging && player.swingTime == 0 && player.getItemInHand(player.swingingArm).getItem() instanceof WindSwordItem
                    && Vfx.visibleAt(player.position())) {
                VfxEffects.add(new WindSlashFx(player));
            }
        }
    }

    /** 扫过的角度 f（0 起手、1 收刀）上那一点 */
    private Vec3 point(Vec3 base, double f) {
        // 从拿剑那一侧扫到另一侧
        double a = side * (SPAN / 2D - SPAN * f);
        double ax = Math.sin(a), az = Math.cos(a);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -fz, rz = fx;
        double x = (fx * az + rx * ax) * RADIUS;
        double z = (fz * az + rz * ax) * RADIUS;
        double y = tilt * (0.5D - f) * 1.4D;
        return base.add(x, y, z);
    }

    @Override
    public boolean tick() {
        age++;
        if (age <= SWEEP) {
            Vec3 base = player.position().add(0D, player.getBbHeight() * 0.62D, 0D);
            Vec3 p = point(base, age / (double) SWEEP);
            RandomSource r = Vfx.random();
            int n = Vfx.count(3F, p);
            for (int i = 0; i < n; i++) {
                Vec3 d = p.subtract(base).normalize().scale(0.06D + r.nextDouble() * 0.05D);
                VfxParticles.spawn(p.x, p.y, p.z, d.x, d.y + 0.01D, d.z, 12 + r.nextInt(8), 0.06F, 0.015F,
                        Vfx.argb(TINT.inner(), 0.85F), Vfx.argb(TINT.glow(), 0F), VfxMode.DOT, 300, 0.92F, -0.001F, 0F, 0F);
            }
        }
        return age < LIFE && !player.isRemoved();
    }

    @Override
    public void render(VfxDraw draw) {
        long t0 = WeaponVfxStats.start();
        float t = age + draw.partialTick();
        float head = Math.min(1F, t / SWEEP);
        float tailLen = 0.8F;
        float tail = Math.max(0F, head - tailLen);
        float fade = t <= SWEEP ? 1F : 1F - (t - SWEEP) / (LIFE - SWEEP);
        if (fade > 0.01F && head - tail > 0.02F) {
            Vec3 base = player.getPosition(draw.partialTick()).add(0D, player.getBbHeight() * 0.62D, 0D);
            for (int i = 0; i < POINTS; i++) {
                double f = head - (head - tail) * i / (POINTS - 1D);
                Vec3 p = point(base, f);
                path[i * 3] = p.x;
                path[i * 3 + 1] = p.y;
                path[i * 3 + 2] = p.z;
            }
            VfxPlasma.trail(draw, path, POINTS, 0.36F, 0.95F * fade, seed, TINT);
        }
        WeaponVfxStats.stop(t0);
    }

    @Override
    public AABB bounds() {
        return player.getBoundingBox().inflate(RADIUS + 1D);
    }
}
