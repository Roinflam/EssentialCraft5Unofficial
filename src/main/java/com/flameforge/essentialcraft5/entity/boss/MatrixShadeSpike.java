package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 心智钉（矩阵之影心相的招，batch13.md §5.3.2）：浮在场地里的一根矩阵钉，立着时矩阵之影挨的伤害 ×{@code spike_damage_taken}（默认减半，
 * 见 {@link MatrixShadeEntity#hurt}），和它之间连一道紫线。12 点生命、不动、不打人，打掉就好。
 * <p>
 * 技能实体：不存盘、类别 MISC、不掉东西不给经验、在 {@code growth_excluded} 里；放它的矩阵之影没了、散去、离开心相，或者 {@link #LIFE} 刻到了就消失。
 */
public class MatrixShadeSpike extends Mob {

    public static final int LIFE = 600;
    public static final double HEALTH = 12D;
    /** 心智钉和连线的颜色（心相的紫） */
    public static final int LINK_COLOR = 0x9A4CE0;

    @Nullable
    private MatrixShadeEntity owner;

    public MatrixShadeSpike(EntityType<? extends MatrixShadeSpike> type, Level level) {
        super(type, level);
        setNoGravity(true);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0D)
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
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel server) || isRemoved() || isDeadOrDying()) {
            return;
        }
        MatrixShadeEntity shade = owner;
        if (shade == null || shade.isRemoved() || shade.isDeadOrDying() || shade.dissipating()
                || shade.phase() != MatrixShadeEntity.Phase.MIND || tickCount > LIFE) {
            BossWarnings.resolve(this, S2CBossWarningPacket.FX_SHATTER, position(), getBbHeight(), 0.3D, Vec3.ZERO, 0, LINK_COLOR, 0);
            discard();
        }
        // 到矩阵之影的连线由放钉子时发的 FX_LINK 画（MatrixShadeSkills#spike），钉子没了客户端自己断
    }

    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        if (wasDead || !dead || !(level() instanceof ServerLevel server)) {
            return;
        }
        // 被打碎（客户端画，FX_SHATTER）
        BossWarnings.resolve(this, S2CBossWarningPacket.FX_SHATTER, position(), getBbHeight(), 0.5D, Vec3.ZERO, 0, LINK_COLOR, 0);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.2F, 0.7F);
        discard();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
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
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_CLUSTER_BREAK;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }
}
