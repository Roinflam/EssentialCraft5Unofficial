package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.item.ForgedElementalSwordItem;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 余烬元素剑的命中（普通一下）和元素爆发（同调 2 阶，范围）。火、水、地、风都不是电系，不冒电：
 * 火是余烬和热裂纹，水是往下落的水光，地是带重力的碎屑和裂纹，风是一圈柔波和打着旋往外飘的光尘；复合用模组本色、带一点电晕。
 * <p>
 * 普通命中客户端自己认：每刻看持剑玩家身边刚挨打的生物（受伤动画刚开始，伤害来源是这名玩家），不加包。
 */
final class ElementFx {

    private ElementFx() {
    }

    /** 每刻调一次 */
    static void watchHits(ClientLevel level) {
        for (Player player : level.players()) {
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof ForgedElementalSwordItem) || !Vfx.visibleAt(player.position())) {
                continue;
            }
            ForgedSwordData.Element element = ForgedSwordData.read(stack).map(ForgedSwordData::primary).orElse(null);
            if (element == null) {
                continue;
            }
            AABB box = player.getBoundingBox().inflate(6D);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.hurtTime == e.hurtDuration - 1)) {
                DamageSource source = target.getLastDamageSource();
                // 余烬的落雷同一刻跟着补一下，来源没有直接实体，会盖掉这一下的近战来源
                if (source != null && source.getEntity() == player && (source.getDirectEntity() == player || source.getDirectEntity() == null)) {
                    hit(target, element);
                }
            }
        }
    }

    /** 一下普通命中 */
    static void hit(LivingEntity target, ForgedSwordData.Element element) {
        VfxPlasma.Tint tint = WeaponTints.element(element);
        Vec3 c = target.position().add(0D, target.getBbHeight() * 0.55D, 0D);
        WeaponHitFx fx = WeaponHitFx.on(target, c, tint, 0.9F, 9);
        if (element == ForgedSwordData.Element.COMBINED) {
            fx.electric(3);
        }
        fx.play();
        particles(c, target.getBbWidth() * 0.6D, element.ordinal(), 1F, tint);
    }

    /** 元素爆发：以 c 为中心、半径 3 的范围（index 同包里的元素序号：0 火 1 水 2 地 3 风） */
    static void burst(Vec3 c, int index) {
        VfxPlasma.Tint tint = WeaponTints.element(index);
        Vec3 ground = new Vec3(c.x, Math.floor(c.y + 0.01D) + 0.02D, c.z);
        WeaponHitFx fx = WeaponHitFx.at(c.add(0D, 0.6D, 0D), tint, 1.8F, 16).wave(3.5F).floor(ground);
        if (index == 0 || index == 2) {
            fx.cracks(3F);
        }
        fx.play();
        particles(ground.add(0D, 0.4D, 0D), 2.6D, index, 3F, tint);
    }

    private static void particles(Vec3 c, double radius, int index, float amount, VfxPlasma.Tint tint) {
        RandomSource r = Vfx.random();
        switch (index) {
            case 0 -> {
                VfxPlasma.embers(c.subtract(0D, 0.3D, 0D), radius, 10F * amount, tint);
                VfxPlasma.sparks(c, new Vec3(0D, 1D, 0D), 0.9D, 8F * amount, 0.1D, 0.35D, 6, 14, tint);
            }
            case 1 -> {
                // 水光：往外溅、再往下落的光点
                int n = Vfx.count(12F * amount, c);
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double s = 0.08D + r.nextDouble() * 0.12D;
                    VfxParticles.spawn(c.x, c.y, c.z, Math.cos(a) * s, 0.12D + r.nextDouble() * 0.12D, Math.sin(a) * s, 14 + r.nextInt(10),
                            0.07F, 0.03F, Vfx.argb(tint.inner(), 0.9F), Vfx.argb(tint.glow(), 0F), VfxMode.DOT, 500, 0.96F, 0.025F, 0F, 0F);
                }
            }
            case 2 -> {
                // 碎屑：重、往四周崩开
                int n = Vfx.count(10F * amount, c);
                for (int i = 0; i < n; i++) {
                    Vec3 d = VfxPlasma.randomDir(r);
                    double s = 0.15D + r.nextDouble() * 0.2D;
                    VfxParticles.spawn(c.x, c.y, c.z, d.x * s, Math.abs(d.y) * s + 0.1D, d.z * s, 12 + r.nextInt(8), 0.08F, 0.04F,
                            Vfx.argb(tint.inner(), 1F), Vfx.argb(tint.outer(), 0F), VfxMode.SPARK, 0, 0.92F, 0.05F, 1.6F, 0F);
                }
            }
            case 3 -> {
                // 光尘打着旋往外飘
                int n = Vfx.count(12F * amount, c);
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rr = radius * (0.3D + 0.7D * r.nextDouble());
                    double s = 0.12D;
                    VfxParticles.spawn(c.x + Math.cos(a) * rr * 0.3D, c.y + (r.nextDouble() - 0.3D) * 0.8D, c.z + Math.sin(a) * rr * 0.3D,
                            -Math.sin(a) * s + Math.cos(a) * 0.05D, 0.02D, Math.cos(a) * s + Math.sin(a) * 0.05D, 16 + r.nextInt(10),
                            0.06F, 0.02F, Vfx.argb(tint.inner(), 0.85F), Vfx.argb(tint.glow(), 0F), VfxMode.DOT, 300, 0.93F, -0.002F, 0F, 0F);
                }
            }
            default -> VfxPlasma.sparks(c, Vec3.ZERO, 0D, 10F * amount, 0.12D, 0.4D, 5, 12, tint);
        }
    }
}
