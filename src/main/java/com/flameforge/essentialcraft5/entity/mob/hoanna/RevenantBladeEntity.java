package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.entity.mob.WildMobBolt;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 灵刃（实体 {@code revenant_blade}，第十三批 13d-N3，怪物稿 §2.4.8「墓穴守灵长：追踪灵刃」）：墓穴守灵长在身边两侧放出的弹射物。
 * <ul>
 *   <li>出来以后先追 {@code blade_homing_ticks}（默认 60 刻 = 3 秒）：每刻把速度往目标胸口转一点（转向有上限，绕着跑、拐弯能甩开），之后直着飞，
 *       再过 {@value #COAST_TICKS} 刻散掉。目标没了、不再是技能目标就不追了。</li>
 *   <li>撞到射手的技能目标造成 {@code blade_damage}（默认 5）点 {@code mob_bolt} 伤害（按弹射物算：虚空 / 宅男按物理挡、能闪），伤害带射手当攻击者。</li>
 *   <li><b>能被打掉</b>：玩家或别的有攻击者的伤害打中一下就碎（不掉东西）；没有攻击者的伤害（摔落、火）打不掉。</li>
 *   <li>飞行规矩（只撞技能目标、不碰方块机关、不过传送门、不存盘）照 {@link WildMobBolt}。画面是一串灵魂火焰粒子。</li>
 * </ul>
 */
public class RevenantBladeEntity extends WildMobBolt {

    /** 追完以后再直着飞多少刻 */
    public static final int COAST_TICKS = 40;
    public static final float SPEED = 0.55F;
    /** 每刻最多往目标方向转多少（速度方向和目标方向插值的比例） */
    public static final double TURN = 0.18D;
    public static final float FALLBACK_DAMAGE = 5F;

    private float damage = FALLBACK_DAMAGE;
    private int homingTicks = 60;
    @Nullable
    private LivingEntity target;

    public RevenantBladeEntity(EntityType<? extends RevenantBladeEntity> type, Level level) {
        super(type, level);
    }

    private RevenantBladeEntity(Level level, LivingEntity shooter) {
        super(ECHoannaNativesC.REVENANT_BLADE.get(), level, shooter);
    }

    /**
     * 在 at 放一把追 target 的灵刃（服务端），一开始往上飘一点再转向目标。生成被别的模组取消时返回 null。
     */
    @Nullable
    public static RevenantBladeEntity launch(ServerLevel level, LivingEntity shooter, LivingEntity target, Vec3 at, float damage, int homingTicks) {
        RevenantBladeEntity blade = new RevenantBladeEntity(level, shooter);
        blade.moveTo(at.x, at.y, at.z, shooter.getYRot(), 0F);
        blade.damage = Float.isFinite(damage) ? Math.max(0F, damage) : FALLBACK_DAMAGE;
        blade.homingTicks = Math.max(1, homingTicks);
        blade.target = target;
        blade.setDeltaMovement(new Vec3(0D, 0.25D, 0D));
        return level.addFreshEntity(blade) && !blade.isRemoved() ? blade : null;
    }

    @Override
    protected int lifetime() {
        return homingTicks + COAST_TICKS;
    }

    public float damage() {
        return damage;
    }

    public int homingTicks() {
        return homingTicks;
    }

    @Nullable
    public LivingEntity target() {
        return target;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            steer();
        }
        super.tick();
    }

    /** 追踪：还在追的时间里、目标还在、射手还认它是技能目标时，把速度往目标胸口转 {@value #TURN} */
    private void steer() {
        LivingEntity aim = target;
        if (aim == null || tickCount > homingTicks || !aim.isAlive() || aim.isRemoved() || aim.level() != level()) {
            return;
        }
        if (getOwner() instanceof CatacombRevenantEntity owner && !owner.isSkillTarget(aim)) {
            target = null;
            return;
        }
        Vec3 want = aim.position().add(0D, aim.getBbHeight() * 0.5D, 0D).subtract(position());
        if (want.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 current = getDeltaMovement();
        Vec3 dir = current.lengthSqr() < 1.0E-6D ? want.normalize() : current.normalize().lerp(want.normalize(), TURN).normalize();
        setDeltaMovement(dir.scale(SPEED));
    }

    @Override
    protected void hitLiving(LivingEntity victim, @Nullable Entity owner) {
        victim.hurt(ECDamageTypes.mobBolt(this, owner), damage);
    }

    @Override
    protected void trail() {
        level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.5D, getZ(), 0D, 0D, 0D);
    }

    @Override
    protected void burst() {
        for (int i = 0; i < 6; i++) {
            level().addParticle(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5D, getZ(),
                    (random.nextDouble() - 0.5D) * 0.1D, random.nextDouble() * 0.08D, (random.nextDouble() - 0.5D) * 0.1D);
        }
    }

    /** 能被打中（准星能对上、近战能挥到） */
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return 0.5F;
    }

    /** 有攻击者的伤害打中一下就碎；没有攻击者的（摔落、火、虚空）不管 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || source.getEntity() == null || source.getEntity() == getOwner()) {
            return false;
        }
        if (!level().isClientSide && !isRemoved()) {
            playSound(ECHoannaNativesC.SOUND_BLADE_BREAK.get(), 0.8F, 1F);
            level().broadcastEntityEvent(this, (byte) 3);
            discard();
        }
        return true;
    }
}
