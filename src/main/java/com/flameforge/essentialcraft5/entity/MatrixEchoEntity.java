package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.matrix.MatrixTrials;
import com.flameforge.essentialcraft5.registry.ECMatrix;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 矩阵回响（第十三批 13a，batch13.md §3.1.2，限制见 policy §4.16）：淬炼台试炼时放出来的、长着试炼者自己样子的半透明影子。
 * 外观复用玩家克隆体那一套（{@code client/render/MatrixEchoRenderer}：试炼者的皮肤、半透明，按试炼者的矩阵属性着色），没有贴图。
 * <ul>
 *   <li><b>只盯试炼者</b>：没有找目标的 AI，目标每刻由试炼指定为试炼者本人；只会近战打它。</li>
 *   <li><b>只吃试炼者的伤害</b>：{@link #isInvulnerableTo} 挡掉来源不是试炼者的一切伤害（别人、宠物、火、摔、溺水、仙人掌……），
 *       只有 {@code bypasses_invulnerability}（虚空、/kill）照常，那种死法不算打倒、试炼会补一只。试炼者自己打出的火焰类伤害
 *       （反弹回去的火球、带 {@code is_fire} 的法术）照常吃；身上不会着火（着火的伤害没有攻击者，本来就挡掉，烧着只是假象）。</li>
 *   <li><b>别人的药水、治疗也不吃</b>（{@link #addEffect}、{@link #forceAddEffect}、{@link #heal}）：效果的来源追到真正的施放者
 *       （喷溅、滞留药水和药箭追到扔的人、射的人），不是试炼者就不收；没有来源的效果只收有害的（别的模组的武器常常不带来源），
 *       增益一律不收。瞬间治疗不经过伤害，直接调 {@code heal}，这里整个不处理：回响本来没有正当的回血来源。
 *       别人的钓竿钩不住它（{@code MatrixTrials.MatrixTrialEvents} 让钩子穿过去），拽不走。</li>
 *   <li><b>死了不走原版死亡流程</b>（{@link #die} 不调父类）：不发 {@code LivingDeathEvent}、不掉落、不给经验、不给 UBMRU、不进图谱、
 *       不给同调度，也不让别的模组、混合端插件把它当成一次击杀（杀怪给钱之类）；放一下碎裂的粒子和声音，告诉试炼「打倒了一只」，就地移除。
 *       另外它在 {@code essentialcraft5:growth_excluded} 里，凡是读这个标签的成长机制都不认它。</li>
 *   <li>不存盘（注册时 {@code noSave}）、不能 /summon、不捡东西、不踩耕地、不破坏方块（没有破门之类的 AI）、不换维度。</li>
 *   <li>生命被别的模组直接 {@code setHealth} 到 0（不经过 {@code die}）时，{@link #tickDeath} 当刻补走一次 {@link #die}：
 *       立刻报告试炼、腾出名额，不等原版那 20 刻的倒地动画。</li>
 *   <li>不属于任何正在进行的试炼（试炼结束了、服务器重载了）的回响下一刻自己消失。</li>
 * </ul>
 * 生命、攻击力在生成时按这一阶的数据设好（{@link #create}）；难度对伤害的缩放照原版。
 */
public class MatrixEchoEntity extends Monster {

    public static final double MOVEMENT_SPEED = 0.3D;
    public static final double FOLLOW_RANGE = 32D;

    private static final EntityDataAccessor<Optional<UUID>> DATA_TRIALIST =
            SynchedEntityData.defineId(MatrixEchoEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** 试炼者的矩阵属性（0 中性、1 混沌、2 冰冻、3 魔力、4 暗影），客户端按它着色 */
    private static final EntityDataAccessor<Integer> DATA_MATRIX_TYPE =
            SynchedEntityData.defineId(MatrixEchoEntity.class, EntityDataSerializers.INT);

    /** 放出它的淬炼台（服务端，不存盘） */
    @Nullable
    private BlockPos crucible;
    /** 连续几次被判成「走不到试炼者身边」（试炼的卡住检测用，服务端，不存盘） */
    private int stuckStrikes;

    public MatrixEchoEntity(EntityType<? extends MatrixEchoEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20D)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 3D)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    /**
     * 造一只（还没加进世界）：外观认 trialist，生命、伤害按这一阶的数据，记下来源（{@link SpawnOrigin.Origin#SUMMONED}）。
     * 加进世界由试炼做（{@code level.addFreshEntity}，被别的模组或混合端插件拒了就不放）。
     */
    @Nullable
    public static MatrixEchoEntity create(ServerLevel level, ServerPlayer trialist, int matrixType, int health, int damage, BlockPos crucible,
                                          Vec3 at) {
        MatrixEchoEntity echo = ECMatrix.MATRIX_ECHO.get().create(level);
        if (echo == null) {
            return null;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(trialist.getZ() - at.z, trialist.getX() - at.x)) - 90D);
        echo.moveTo(at.x, at.y, at.z, yaw, 0F);
        echo.setYHeadRot(yaw);
        echo.entityData.set(DATA_TRIALIST, Optional.of(trialist.getUUID()));
        echo.entityData.set(DATA_MATRIX_TYPE, Math.max(0, Math.min(4, matrixType)));
        echo.crucible = crucible.immutable();
        AttributeInstance maxHealth = echo.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(Math.max(1, health));
        }
        echo.setHealth(echo.getMaxHealth());
        AttributeInstance attack = echo.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(Math.max(0, damage));
        }
        SpawnOrigin.set(echo, SpawnOrigin.Origin.SUMMONED);
        return echo;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TRIALIST, Optional.empty());
        entityData.define(DATA_MATRIX_TYPE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1D, true));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------
    // 身份
    // ------------------------------------------------------------------

    /** 试炼者的 UUID（两端；没有时为 null） */
    @Nullable
    public UUID trialist() {
        return entityData.get(DATA_TRIALIST).orElse(null);
    }

    /** 试炼者的矩阵属性（两端） */
    public int matrixType() {
        return entityData.get(DATA_MATRIX_TYPE);
    }

    /** 放出它的淬炼台（服务端；不是试炼放出来的为 null） */
    @Nullable
    public BlockPos crucible() {
        return crucible;
    }

    /** 这名玩家是不是它的试炼者 */
    public boolean isTrialist(@Nullable Entity entity) {
        UUID id = trialist();
        return entity instanceof Player && id != null && id.equals(entity.getUUID());
    }

    /** 卡住检测的连续次数（{@code MatrixTrials} 读写） */
    public int stuckStrikes() {
        return stuckStrikes;
    }

    public void setStuckStrikes(int strikes) {
        stuckStrikes = Math.max(0, strikes);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        // 不属于任何正在进行的试炼就消失；属于的话目标永远是试炼者本人
        ServerPlayer target = MatrixTrials.targetFor(this);
        if (target == null) {
            discard();
            return;
        }
        if (getTarget() != target) {
            setTarget(target);
        }
    }

    @Override
    public void setTarget(@Nullable net.minecraft.world.entity.LivingEntity target) {
        // 只认试炼者：别的东西（别的模组的嘲讽、引怪效果）想改它的目标一律不收
        if (target == null || isTrialist(target)) {
            super.setTarget(target);
        }
    }

    // ------------------------------------------------------------------
    // 伤害与死亡
    // ------------------------------------------------------------------

    /** 只有试炼者打得动它；虚空、/kill 这类 bypasses_invulnerability 照常 */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (super.isInvulnerableTo(source)) {
            return true;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return !isTrialist(source.getEntity());
    }

    /** 只打试炼者 */
    @Override
    public boolean doHurtTarget(Entity target) {
        return isTrialist(target) && super.doHurtTarget(target);
    }

    // ------------------------------------------------------------------
    // 药水效果与治疗：只收试炼者的
    // ------------------------------------------------------------------

    /** 别人给的效果不收（见类注释）；客户端收服务端同步来的效果不拦 */
    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        if (!level().isClientSide && !acceptsEffect(effect, source)) {
            return false;
        }
        return super.addEffect(effect, source);
    }

    @Override
    public void forceAddEffect(MobEffectInstance effect, @Nullable Entity source) {
        if (!level().isClientSide && !acceptsEffect(effect, source)) {
            return;
        }
        super.forceAddEffect(effect, source);
    }

    /**
     * 这份效果收不收：来源追到施放者（{@link TraceableEntity#getOwner()}：弹射物、药水云），是试炼者才收；
     * 没有来源的只收有害效果（别的模组的武器加效果时常常不带来源，不能因此让试炼者的武器失效），增益、中性一律不收。
     */
    public boolean acceptsEffect(MobEffectInstance effect, @Nullable Entity source) {
        Entity caster = source instanceof TraceableEntity traced ? traced.getOwner() : source;
        if (caster == null) {
            return source == null && effect.getEffect().getCategory() == MobEffectCategory.HARMFUL;
        }
        return isTrialist(caster);
    }

    /** 回响没有正当的回血来源：瞬间治疗、别的模组的群体治疗都绕过伤害判断，直接调这里，一律不处理 */
    @Override
    public void heal(float amount) {
    }

    /** 不着火：着火的伤害没有攻击者，本来就伤不到它，烧着只是假象 */
    @Override
    public void setRemainingFireTicks(int ticks) {
        super.setRemainingFireTicks(0);
    }

    /**
     * 生命归零却没走 {@link #die}（别的模组直接 {@code setHealth} 扣血）时，原版只会放 20 刻倒地动画再移除，试炼收不到「打倒了一只」，
     * 那 20 刻里还占着名额。这里在服务端当刻补走一次 {@link #die}，伤害来源取最近一次受伤的那一下（40 刻内），没有就用 generic，
     * 算不算试炼者打倒的由 {@code MatrixTrials.onEchoDeath} 按来源和击杀归属判；{@code dead} 标记防止重复报告。
     */
    @Override
    protected void tickDeath() {
        if (!level().isClientSide && !dead && !isRemoved()) {
            DamageSource last = getLastDamageSource();
            die(last != null ? last : damageSources().generic());
            return;
        }
        super.tickDeath();
    }

    /**
     * 不走原版的死亡流程（见类注释）：告诉试炼、放碎裂效果、就地移除。
     * 不调父类：父类会发 {@code LivingDeathEvent}、掉落、给经验、记击杀者的统计。
     */
    @Override
    public void die(DamageSource source) {
        if (isRemoved() || dead) {
            return;
        }
        dead = true;
        if (level() instanceof ServerLevel server) {
            MatrixTrials.onEchoDeath(this, source);
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1D, getZ(), 24, 0.3D, 0.6D, 0.3D, 0.05D);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1F, 0.8F);
        }
        discard();
    }

    // ------------------------------------------------------------------
    // 不掉、不捡、不踩、不存、不走
    // ------------------------------------------------------------------

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return BuiltInLootTables.EMPTY;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean canTrample(BlockState state, BlockPos pos, float fallDistance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    /** 和平难度不按原版清掉：试炼自己处理（收回回响、改成引导），免得试炼还以为它在场 */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /** 离玩家远了也不按原版规则消失（试炼自己管它的去留） */
    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return null;
    }
}
