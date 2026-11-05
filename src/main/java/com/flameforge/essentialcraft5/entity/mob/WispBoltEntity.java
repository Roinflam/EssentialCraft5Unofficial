package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.entity.mob.ElementalWispEntity.Element;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECWildMobs;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 元素聚灵弹（实体 {@code wisp_bolt}，13c-M1）：元素游灵的精英「元素聚灵」轮着射的四色弹。直线飞 {@value #LIFETIME} 刻，
 * 撞上技能目标造成 {@code mob_bolt} 伤害（数额 = 射手的攻击力，精英约 4.2；按弹射物算：虚空 / 宅男按物理挡、审判庭不减、能闪），
 * 打实了带那种元素的效果（{@link ElementalWispEntity#applyElementHit}）。没有射手（射手已经没了）时照 3 点算。
 * 元素存在同步数据里，客户端按它撒对应颜色的粒子。
 */
public class WispBoltEntity extends WildMobBolt {

    public static final int LIFETIME = 60;
    public static final float SPEED = 0.9F;
    public static final float FALLBACK_DAMAGE = 3F;

    private static final EntityDataAccessor<Byte> DATA_ELEMENT = SynchedEntityData.defineId(WispBoltEntity.class, EntityDataSerializers.BYTE);

    public WispBoltEntity(EntityType<? extends WispBoltEntity> type, Level level) {
        super(type, level);
    }

    public WispBoltEntity(Level level, LivingEntity shooter, Element element) {
        super(ECWildMobs.WISP_BOLT.get(), level, shooter);
        entityData.set(DATA_ELEMENT, (byte) element.ordinal());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ELEMENT, (byte) 0);
    }

    public Element getElement() {
        return Element.byIndex(entityData.get(DATA_ELEMENT));
    }

    @Override
    protected int lifetime() {
        return LIFETIME;
    }

    @Override
    protected void hitLiving(LivingEntity target, @Nullable Entity owner) {
        float damage = FALLBACK_DAMAGE;
        if (owner instanceof LivingEntity shooter && shooter.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            damage = (float) shooter.getAttributeValue(Attributes.ATTACK_DAMAGE);
        }
        if (target.hurt(ECDamageTypes.mobBolt(this, owner), damage)) {
            ElementalWispEntity.applyElementHit(target, getElement(), getDeltaMovement(), owner);
        }
    }

    @Override
    protected void trail() {
        level().addParticle(new DustParticleOptions(getElement().rgb(), 1.1F), getX(), getY() + getBbHeight() * 0.5D, getZ(), 0D, 0D, 0D);
    }

    @Override
    protected void burst() {
        for (int i = 0; i < 8; i++) {
            level().addParticle(new DustParticleOptions(getElement().rgb(), 1F), getX(), getY() + getBbHeight() * 0.5D, getZ(),
                    (random.nextDouble() - 0.5D) * 0.3D, (random.nextDouble() - 0.5D) * 0.3D, (random.nextDouble() - 0.5D) * 0.3D);
        }
    }
}
