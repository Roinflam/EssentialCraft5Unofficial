package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.entity.mob.WildMobBolt;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECWildMobs;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 疾风弹（实体 {@code storm_gust}，13c-M1）：风暴术士（风法师 type 3）代替 MRU 箭射出的一团风。直线飞 {@value #LIFETIME} 刻，
 * 撞上技能目标造成 {@code general.mobs.storm_sorcerer.gust_damage} 点 {@code mob_bolt} 伤害（按弹射物算），打实了顺着飞行方向把人推开
 * （{@code gust_knockback}，按击退抗性打折，略微往上抬）。不碰方块。
 */
public class StormGustEntity extends WildMobBolt {

    public static final int LIFETIME = 50;
    public static final float SPEED = 1.3F;

    public StormGustEntity(EntityType<? extends StormGustEntity> type, Level level) {
        super(type, level);
    }

    public StormGustEntity(Level level, LivingEntity shooter) {
        super(ECWildMobs.STORM_GUST.get(), level, shooter);
    }

    @Override
    protected int lifetime() {
        return LIFETIME;
    }

    @Override
    protected void hitLiving(LivingEntity target, @Nullable Entity owner) {
        float damage = (float) ECWildMobs.Config.stormGustDamage();
        boolean landed = damage <= 0F || target.hurt(ECDamageTypes.mobBolt(this, owner), damage);
        if (landed) {
            push(target, getDeltaMovement());
        }
    }

    /** 顺着 motion 的水平方向推开（{@code gust_knockback} × (1 − 击退抗性)），往上抬 0.25 × 同一个系数 */
    public static void push(LivingEntity target, Vec3 motion) {
        double strength = ECWildMobs.Config.stormGustKnockback() * (1D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        Vec3 flat = new Vec3(motion.x, 0D, motion.z);
        if (strength <= 0D || flat.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 dir = flat.normalize().scale(strength);
        target.push(dir.x, 0.25D * Math.min(1D, strength), dir.z);
        target.hurtMarked = true;
    }

    @Override
    protected void trail() {
        level().addParticle(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.5D, getZ(), 0D, 0D, 0D);
        if (random.nextBoolean()) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.5D, getZ(), 0D, 0D, 0D);
        }
    }

    @Override
    protected void burst() {
        for (int i = 0; i < 6; i++) {
            level().addParticle(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.5D, getZ(),
                    (random.nextDouble() - 0.5D) * 0.2D, (random.nextDouble() - 0.5D) * 0.2D, (random.nextDouble() - 0.5D) * 0.2D);
        }
    }
}
