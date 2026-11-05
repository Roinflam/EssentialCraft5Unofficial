package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 畸变幼体 {@code chaos_larva}（第十三批 13d-N2，batch13.md §5.2；怪物稿 §2.4.3）：混沌畸变体死时裂出来、畸变母巢打架时产出来的小肉虫。
 * 生命 6、咬 2、跑得快；{@code larva_lifetime} 刻后自己散掉。技能实体的规矩：
 * <ul>
 *   <li>不存盘（区块卸载、关服就没了）、不再分裂、没有精英、没有刷怪蛋、什么都不掉（掉落表是空的）、不给经验；进 {@code growth_excluded}，
 *       不在 {@code hoanna_natives} 里（矩阵图谱的「海纳尔原生」不记它）；</li>
 *   <li>来源由母体记成「召唤」（不公平），身上记着母体的 UUID（持久数据 {@value #TAG_MOTHER}），母巢按它数自己产的有几只。</li>
 * </ul>
 */
public class ChaosLarvaEntity extends ECMonster {

    /** 记母体 UUID 的持久数据键 */
    public static final String TAG_MOTHER = EssentialCraft5.MODID + ":larva_mother";

    /** 没由母体设过寿命时（指令放的）活多久 */
    public static final int DEFAULT_LIFETIME = 800;

    private int lifetime = DEFAULT_LIFETIME;
    private int age;

    public ChaosLarvaEntity(EntityType<? extends ChaosLarvaEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    /** 属性（怪物稿数值表：6 / 0、咬 2、移速 0.3；技能实体，不走配置） */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** 母体 UUID；不是母体放出来的返回 null */
    @Nullable
    public UUID mother() {
        CompoundTag data = getPersistentData();
        return data.hasUUID(TAG_MOTHER) ? data.getUUID(TAG_MOTHER) : null;
    }

    public void setMother(UUID mother) {
        getPersistentData().putUUID(TAG_MOTHER, mother);
    }

    /** 还剩几刻散掉 */
    public int ticksLeft() {
        return Math.max(0, lifetime - age);
    }

    public void setLifetime(int ticks) {
        this.lifetime = Math.max(1, ticks);
    }

    /** 到点散掉（不算死亡：不掉东西、不给经验、不发死亡事件）。服务端每刻推，测试直接调 */
    public void tickLifetime() {
        if (level().isClientSide || isRemoved()) {
            return;
        }
        if (++age >= lifetime) {
            if (level() instanceof ServerLevel server) {
                server.sendParticles(new DustParticleOptions(ChaosAberrantEntity.CHAOS_RED, 1F), getX(), getY(0.5D), getZ(), 8, 0.2D, 0.15D, 0.2D, 0D);
            }
            discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        tickLifetime();
    }

    /** 技能实体：不存盘，也没有精英变体 */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hasEliteVariant() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public int getExperienceReward() {
        return 0;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ECHoannaNativesB.SOUND_LARVA_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECHoannaNativesB.SOUND_LARVA_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECHoannaNativesB.SOUND_LARVA_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }
}
