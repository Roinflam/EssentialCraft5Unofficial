package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.registry.ECBossFramework;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 灾柱（天灾之心护盾期的四根柱子，batch13.md §5.3.5）：240 血、主灾那根 360（calamity_heart_v2.md §2.1）；任一根在，天灾之心打不动；
 * 第一次掉到一半放一次灾柱反噬（{@link CalamityHeartEntity#onPillarHalf}）；打掉一根放一次那一相的「残响」、天灾之心破盾 20 秒（{@link CalamityHeartEntity#onPillarFell}）。
 * 天灾之心开场演出期间打不动。刚立起（开场、重立）的那一根带 {@value #RISE_TICKS} 刻的破土动画（{@link #riseProgress}，客户端画）。
 * <p>
 * 挨打的口径同 Boss 档（{@link BossRules}）：只吃真玩家打的（假玩家、生物、宠物、没有攻击者的都打不动；没有攻击者的 {@code bypasses_invulnerability}
 * —— {@code /kill}、掉出世界 —— 照常），生存 / 冒险的要离天灾之心不超过它的参战距离；拒 {@code orbital_strike}；不是近战的 ×{@code non_melee_multiplier}
 * （默认 0.5）；单次封顶 {@code pillar_per_hit_cap}（40）。打实的有效伤害记进天灾之心的伤害账（打柱子的人也算出了力，进名单）。
 * 不吃状态效果、不受摔落 / 窒息 / 溺水、推不动、不受重力。
 * <p>
 * 不存盘：剩余血量记在天灾之心身上，读档、区块重载后由它重新立起来（{@link CalamityHeartEntity#syncPillars}）；
 * 它自己找不到天灾之心（首领没了、没加载）连着 {@value #ORPHAN_TICKS} 刻就消失。注册在 {@code registry/ECCalamityHeart}。
 */
public class CalamityPillarEntity extends Mob implements BossHealthGuard.Guarded {

    /** 普通灾柱的生命（默认值；以配置 {@code pillar_health} 为准） */
    public static final double HEALTH = 240D;
    /** 主灾那根的生命（默认值；以配置 {@code main_pillar_health} 为准） */
    public static final double MAIN_HEALTH = 360D;
    /** 破土动画多少刻 */
    public static final int RISE_TICKS = 16;
    private static final int NO_RISE = Integer.MIN_VALUE;
    /** 破土最多推迟多少刻（开场最长 600 刻） */
    private static final int MAX_RISE_DELAY = 1200;
    /** 找不到天灾之心多少刻就消失 */
    public static final int ORPHAN_TICKS = 40;

    private static final EntityDataAccessor<Byte> DATA_ASPECT = SynchedEntityData.defineId(CalamityPillarEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_MAIN = SynchedEntityData.defineId(CalamityPillarEntity.class, EntityDataSerializers.BOOLEAN);
    /** 破土那一刻的世界时间（低 32 位；{@link #NO_RISE} = 没有破土动画：读档、区块重载后重新立起来的） */
    private static final EntityDataAccessor<Integer> DATA_RISE = SynchedEntityData.defineId(CalamityPillarEntity.class, EntityDataSerializers.INT);

    @Nullable
    private CalamityHeartEntity heart;
    private int index = -1;
    private int orphan;
    /** 直接改生命时的结算（构造期间还是 null）；灾柱不用每秒桶，桶只是给结算用的空壳 */
    private final BossHealthGuard healthGuard = new BossHealthGuard();
    private final BossDamageBudget guardBudget = new BossDamageBudget();

    public CalamityPillarEntity(EntityType<? extends CalamityPillarEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setNoAi(true);
        setNoGravity(true);
    }

    /** 属性默认值：240 血、不动、推不动（实际血量由天灾之心生成时按配置设） */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ASPECT, (byte) 0);
        entityData.define(DATA_MAIN, false);
        entityData.define(DATA_RISE, NO_RISE);
    }

    /** 天灾之心立柱子时调（加进世界之前）：第几根、哪一相、是不是主灾、最大生命和现在的生命 */
    public void bind(CalamityHeartEntity owner, int slot, CalamityHeartEntity.Aspect aspect, boolean main, double maxHealth, float health) {
        heart = owner;
        index = slot;
        entityData.set(DATA_ASPECT, (byte) aspect.ordinal());
        entityData.set(DATA_MAIN, main);
        AttributeInstance max = getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(Math.max(1D, maxHealth));
        }
        float value = Math.max(0.01F, Math.min(getMaxHealth(), health));
        if (healthGuard == null || level().isClientSide) {
            setHealth(value);
        } else {
            healthGuard.unguarded(this, value);
        }
    }

    public CalamityHeartEntity.Aspect aspect() {
        return CalamityHeartEntity.Aspect.byId(entityData.get(DATA_ASPECT));
    }

    /** 天灾之心立起刚破土的这一根时调（加进世界之前）：记下破土那一刻（开场时可以在之后几刻，之前一直埋在地下），客户端按它画升起动画 */
    public void markRising(long gameTime) {
        int low = (int) gameTime;
        entityData.set(DATA_RISE, low == NO_RISE ? NO_RISE + 1 : low);
    }

    /** 破土动画走到哪（0 = 还在地下、刚破土，1 = 立好了；没有动画时一直是 1） */
    public float riseProgress(float partialTick) {
        int rise = entityData.get(DATA_RISE);
        if (rise == NO_RISE) {
            return 1F;
        }
        // 只比低 32 位（差值按 int 回绕照样对）；还没到破土的时刻埋在地下，差得离谱（时间被改过）就不画动画
        int elapsed = (int) level().getGameTime() - rise;
        if (elapsed < 0) {
            return elapsed > -MAX_RISE_DELAY ? 0F : 1F;
        }
        return Mth.clamp((elapsed + partialTick) / RISE_TICKS, 0F, 1F);
    }

    /** 主灾那根（换成更亮的颜色、240 血） */
    public boolean main() {
        return entityData.get(DATA_MAIN);
    }

    @Nullable
    public CalamityHeartEntity heart() {
        return heart;
    }

    public int index() {
        return index;
    }

    // ------------------------------------------------------------------
    // 每刻
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        if (healthGuard != null && !level().isClientSide) {
            healthGuard.observe(this, this);
        }
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (heart == null || heart.isRemoved() || !heart.isAlive() || heart.dissipating()) {
            if (++orphan >= ORPHAN_TICKS) {
                discard();
            }
            return;
        }
        orphan = 0;
        if (tickCount % 5 == 0) {
            float[] rgb = CalamityHeartEntity.rgb(aspect().color());
            server.sendParticles(new DustParticleOptions(new Vector3f(rgb[0], rgb[1], rgb[2]), main() ? 1.6F : 1.1F),
                    getX(), getY() + getBbHeight() * 0.8D, getZ(), 2, 0.3D, 0.6D, 0.3D, 0D);
        }
    }

    // ------------------------------------------------------------------
    // 挨打
    // ------------------------------------------------------------------

    /** 这一下打不打得动（见类注释） */
    public boolean accepts(DamageSource source) {
        if (BossRules.unblockable(this, source)) {
            return true;
        }
        if (heart != null && heart.introActive()) {
            return false;
        }
        if (source.is(ECDamageTypes.ORBITAL_STRIKE) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING)) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer player) || !ECProtection.isRealPlayer(player) || player.isSpectator()) {
            return false;
        }
        if (player.isCreative()) {
            return true;
        }
        CalamityHeartEntity owner = heart;
        return owner != null && player.level() == level() && player.distanceTo(owner) <= owner.rosterSettings().engageRange();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (!level().isClientSide && !accepts(source)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    // ------------------------------------------------------------------
    // 生命结算（BossHealthGuard）：直接改生命的写法也按单次封顶结算，开场演出里一点不掉
    // ------------------------------------------------------------------

    @Override
    public BossHealthGuard healthGuard() {
        return healthGuard;
    }

    @Override
    public BossDamageBudget guardBudget() {
        return guardBudget;
    }

    @Override
    public BossDamageBudget.Limits guardLimits() {
        return BossDamageBudget.Limits.of(ECCalamityHeart.Config.values().fight().pillarPerHit(), 0D)
                .scaled(healthGuard == null ? 1F : healthGuard.limitScale(this));
    }

    @Override
    public float guardFloor() {
        return heart != null && heart.introActive() ? Float.POSITIVE_INFINITY : 0F;
    }

    @Override
    public void setHealth(float health) {
        if (healthGuard == null || level().isClientSide) {
            super.setHealth(health);
            return;
        }
        healthGuard.set(this, this, health);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (healthGuard != null && BossHealthGuard.isHealth(key) && !level().isClientSide) {
            healthGuard.observe(this, this);
        }
    }

    /** 不是近战的打折、单次封顶；打实了把扣掉的血记进天灾之心的伤害账；这一下跨过一半血时通知天灾之心放反噬 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !BossRules.unblockable(this, source)) {
            if (!ECDamageKinds.isMelee(source)) {
                amount *= (float) ECBossFramework.Config.values().nonMeleeMultiplier();
            }
            float cap = guardLimits().perHit();
            if (cap > 0F) {
                amount = Math.min(amount, cap);
            }
        }
        float before = getHealth();
        boolean hurt = super.hurt(source, amount);
        CalamityHeartEntity owner = heart;
        if (hurt && owner != null && !level().isClientSide) {
            float dealt = Math.max(0F, before - Math.max(0F, getHealth()));
            if (dealt > 0F) {
                owner.bossRoster().noteDamage(source, dealt);
            }
            float half = getMaxHealth() * 0.5F;
            if (before > half && getHealth() <= half && getHealth() > 0F) {
                owner.onPillarHalf(this, index);
            }
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (healthGuard != null && !level().isClientSide) {
            // 先对一遍生命，生命没真扣到 0 就调 die()：不算倒下
            healthGuard.observe(this, this);
            if (healthGuard.refusesDeath(this)) {
                return;
            }
        }
        boolean wasDead = dead;
        super.die(source);
        CalamityHeartEntity owner = heart;
        if (!wasDead && dead && owner != null && !level().isClientSide) {
            owner.onPillarFell(this, index);
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
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
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    /** 技能实体：不存盘 */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
