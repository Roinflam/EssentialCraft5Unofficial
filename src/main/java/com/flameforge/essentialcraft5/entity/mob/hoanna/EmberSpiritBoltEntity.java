package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.entity.mob.WildMobBolt;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 余烬弹（实体 {@code ember_spirit_bolt}，第十三批 13d-N2）：余烬灵吐的一团余烬。直线飞 {@value #LIFETIME} 刻，只撞射手的技能目标，
 * 撞上造成 {@code mob_bolt} 伤害（数额在吐出时取射手的攻击力，精英 ×1.4；按弹射物算：虚空 / 宅男按物理挡、审判庭不减、能闪），
 * 打实了带那种余烬的效果（{@link EmberSpiritHit}）。撞方块只散掉，不点火、不碰方块（{@link WildMobBolt} 的规矩）。
 * 余烬种类存在同步数据里，客户端按它撒对应颜色的粒子；不存盘。
 */
public class EmberSpiritBoltEntity extends WildMobBolt {

    public static final int LIFETIME = 60;
    public static final float SPEED = 0.8F;
    /** 没有记下伤害（指令放的）时按这么多算 */
    public static final float FALLBACK_DAMAGE = 6F;

    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(EmberSpiritBoltEntity.class, EntityDataSerializers.BYTE);

    private float damage = FALLBACK_DAMAGE;

    public EmberSpiritBoltEntity(EntityType<? extends EmberSpiritBoltEntity> type, Level level) {
        super(type, level);
    }

    public EmberSpiritBoltEntity(Level level, LivingEntity shooter, EmberKind kind, float damage) {
        super(ECHoannaNativesB.EMBER_SPIRIT_BOLT.get(), level, shooter);
        entityData.set(DATA_KIND, (byte) kind.ordinal());
        this.damage = damage;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_KIND, (byte) EmberKind.COMMON.ordinal());
    }

    public EmberKind kind() {
        EmberKind[] all = EmberKind.values();
        return all[Math.floorMod(entityData.get(DATA_KIND), all.length)];
    }

    /** 这颗打中时的伤害 */
    public float damage() {
        return damage;
    }

    @Override
    protected int lifetime() {
        return LIFETIME;
    }

    @Override
    protected void hitLiving(LivingEntity target, @Nullable Entity owner) {
        if (target.hurt(ECDamageTypes.mobBolt(this, owner), damage)) {
            EmberSpiritHit.apply(kind(), target, this, owner, random);
        }
        playSound(ECHoannaNativesB.SOUND_EMBER_BOLT_FIZZLE.get(), 0.6F, 1F);
    }

    @Override
    protected void trail() {
        DustParticleOptions dust = new DustParticleOptions(EmberSpiritEntity.rgb(kind()), 1.1F);
        level().addParticle(dust, getX(), getY() + getBbHeight() * 0.5D, getZ(), 0D, 0D, 0D);
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY() + getBbHeight() * 0.5D, getZ(), 0D, 0.01D, 0D);
        }
    }

    @Override
    protected void burst() {
        DustParticleOptions dust = new DustParticleOptions(EmberSpiritEntity.rgb(kind()), 1F);
        for (int i = 0; i < 8; i++) {
            level().addParticle(dust, getX(), getY() + getBbHeight() * 0.5D, getZ(),
                    (random.nextDouble() - 0.5D) * 0.3D, (random.nextDouble() - 0.5D) * 0.3D, (random.nextDouble() - 0.5D) * 0.3D);
        }
    }
}
