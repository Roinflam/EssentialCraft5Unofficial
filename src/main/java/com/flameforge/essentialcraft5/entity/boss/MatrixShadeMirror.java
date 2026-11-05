package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 镜影（矩阵之影心相的招，batch13.md §5.3.2）：和矩阵之影长得一样（同一个模型、同一张皮），只有胸口的矩阵格是暗的
 * （发光层另一张 {@code shade_mirror_glow.png}），真身的亮着——看胸口分真假。一碰就碎（1 点生命），会追着参战者出拳（3 点，吃护甲）；
 * 被玩家打碎时那名玩家挨反噬（缓慢 II 3 秒 + 4 点，{@link MatrixShadeSkills#mirrorBacklash}）。
 * <p>
 * 技能实体：不存盘、类别 MISC、不掉东西不给经验、在 {@code growth_excluded} 里；放它的矩阵之影没了、散去、离开心相，或者 {@link #LIFE} 刻到了就消失。
 */
public class MatrixShadeMirror extends Monster {

    public static final int LIFE = 400;

    public final MobAnimStates anims = new MobAnimStates(MatrixShadeEntity.ANIM_SLOTS)
            .channel(MatrixShadeEntity.ANIM_FIST_LEFT, 1).channel(MatrixShadeEntity.ANIM_FIST_RIGHT, 1);

    @Nullable
    private MatrixShadeEntity owner;
    private boolean leftFist;

    public MatrixShadeMirror(EntityType<? extends MatrixShadeMirror> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1D)
                .add(Attributes.ATTACK_DAMAGE, 3D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.FOLLOW_RANGE, 24D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1D);
    }

    /** 放它的矩阵之影（加进世界之前调） */
    public void bind(MatrixShadeEntity shade) {
        owner = shade;
    }

    @Nullable
    public MatrixShadeEntity owner() {
        return owner;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1D, true));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12F));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 5, true, false,
                target -> owner != null && owner.isSkillTarget(target)));
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && !isRemoved() && !isDeadOrDying()) {
            MatrixShadeEntity shade = owner;
            if (shade == null || shade.isRemoved() || shade.isDeadOrDying() || shade.dissipating()
                    || shade.phase() != MatrixShadeEntity.Phase.MIND || tickCount > LIFE) {
                server.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() * 0.5D, getZ(), 12, 0.4D, 0.8D, 0.4D, 0.02D);
                discard();
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        anims.play(this, leftFist ? MatrixShadeEntity.ANIM_FIST_LEFT : MatrixShadeEntity.ANIM_FIST_RIGHT);
        leftFist = !leftFist;
        return super.doHurtTarget(target);
    }

    /** 碎掉：打碎它的玩家挨反噬，当场消失（不放死亡动画） */
    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        if (wasDead || !dead || !(level() instanceof ServerLevel server)) {
            return;
        }
        if (owner != null && !owner.isRemoved() && source.getEntity() instanceof Player player) {
            MatrixShadeSkills.mirrorBacklash(owner, player);
        }
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() * 0.5D, getZ(), 24, 0.4D, 0.8D, 0.4D, 0.05D);
        server.playSound(null, getX(), getY(), getZ(), ECMatrixShade.SOUND_MIRROR_BREAK.get(), SoundSource.HOSTILE, 1.2F, 1F);
        discard();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public int getExperienceReward() {
        return 0;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
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
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECMatrixShade.SOUND_MIRROR_BREAK.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }
}
