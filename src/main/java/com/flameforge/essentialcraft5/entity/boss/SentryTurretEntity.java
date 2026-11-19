package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECTownSentry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 哨戒炮台 {@code sentry_turret}（废城守望者架起来的技能实体，batch13.md §5.3.3、MS §2.5.2）：{@code turret_health}（30）血、不动、
 * 每 {@code turret_interval_ticks}（30）刻朝守望者名单里最近、看得见、{@code turret_range}（16）格以内的参战者打一发 {@code turret_damage}（6）点
 * {@code mob_bolt}（不生成弹射物实体的射线，照遗民步枪；攻击者是炮台，吃护甲、盾挡得住）。被打掉 {@code turret_respawn_ticks}（20 秒）后由守望者补一座，
 * 同时最多 {@code turret_count}（2）座（{@link TownSentryEntity#tickTurrets}）；活着时守望者正面挡弹射物。
 * <p>
 * <b>不存盘</b>（{@link #shouldBeSaved} 为 false）：区块卸载、关服就没了，守望者读档后有人进名单时重新架，不会读回来没主人的炮台；
 * 守望者死了、散去、被卸载时一起收掉（{@code BossSkillSlots.discardAll}），找不到主人的炮台下一刻自己消失。
 * 不掉东西、不给经验；在 {@code growth_excluded} 里（不给同调点、不算公平来源）。注册在 {@code registry/ECTownSentry}。
 */
public class SentryTurretEntity extends Monster {

    /** 弹道的颜色（守望者的熔橙） */
    public static final int TRACER_COLOR = 0xFF8A3A;

    /** 没有目标时多久再找一次（刻） */
    public static final int RETARGET_TICKS = 10;

    @Nullable
    private TownSentryEntity owner;
    private int cooldown;

    public SentryTurretEntity(EntityType<? extends SentryTurretEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    /** 属性默认值：30 血、不动（移速 0）、推不动 */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, ECTownSentry.Config.DEFAULTS.turrets().health())
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    /** 归哪只守望者（架起来时调，加进世界以前）：按现在的配置设生命、回满血，第一发等半个间隔 */
    public void bind(TownSentryEntity sentry) {
        this.owner = sentry;
        ECTownSentry.Config.Turrets cfg = ECTownSentry.Config.values().turrets();
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(cfg.health());
        }
        setHealth(getMaxHealth());
        cooldown = Math.max(1, cfg.intervalTicks() / 2);
    }

    @Nullable
    public TownSentryEntity owner() {
        return owner;
    }

    public int cooldown() {
        return cooldown;
    }

    // ------------------------------------------------------------------ tick

    @Override
    protected void registerGoals() {
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        tickTurret();
    }

    /** 一刻（{@link #customServerAiStep} 调；GameTest 对 NoAI 的炮台直接调）：主人没了就消失；冷却到了找目标开火 */
    public void tickTurret() {
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        TownSentryEntity sentry = owner;
        if (sentry == null || !sentry.isAlive() || sentry.isRemoved() || sentry.level() != level()) {
            discard();
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        LivingEntity target = sentry.turretTarget(this);
        if (target == null) {
            cooldown = RETARGET_TICKS;
            return;
        }
        fire(target);
        cooldown = ECTownSentry.Config.values().turrets().intervalTicks();
    }

    /**
     * 朝 target 开一枪：转过去、放发射器声和枪声、画一道弹道，打 {@code turret_damage} 点 {@code mob_bolt}（攻击者是炮台）。
     * 不查目标（{@link #tickTurret} 已经按名单、射程、视线挑过）。返回打中没有。
     */
    public boolean fire(LivingEntity target) {
        Vec3 from = getEyePosition();
        Vec3 to = target.position().add(0D, target.getBbHeight() * 0.5D, 0D);
        float yaw = (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), ECTownSentry.SOUND_TURRET_FIRE.get(), SoundSource.HOSTILE, 1F, 1.4F);
            server.playSound(null, getX(), getY(), getZ(), ECHoannaNativesA.SOUND_SCAVENGER_SHOOT.get(), SoundSource.HOSTILE, 0.8F, 1.1F);
            // 枪口火光、弹道、命中由客户端画（FX_TRACER）
            BossWarnings.resolve(this, S2CBossWarningPacket.FX_TRACER, from, 0D, 0D, to.subtract(from), 0, TRACER_COLOR, 0);
        }
        float damage = (float) ECTownSentry.Config.values().turrets().damage();
        return damage > 0F && target.hurt(ECDamageTypes.mobBolt(this), damage);
    }

    // ------------------------------------------------------------------ 规矩

    /** 技能实体：不存盘（区块卸载、关服就没了，守望者读档后自己补） */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECTownSentry.SOUND_TURRET_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECTownSentry.SOUND_TURRET_DEATH.get();
    }
}
