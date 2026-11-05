package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.entity.mob.ECFlyingMonster;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.MobWeakness;
import com.flameforge.essentialcraft5.hoanna.HoannaSpawnHooks;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC.Config.PatrollerValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 审判庭巡卫 {@code inquisitorium_patroller}（第十三批 13d-N3，batch13.md §5.2；怪物稿 §2.4.7）：暗影腐化之地悬停的半尺寸全息人形
 * （画面复用全息投影的人形模型和 {@code boss.png}，缩 0.6、青色调、腿淡出，见 {@code client/render/mob/InquisitoriumPatrollerRenderer}）。
 * <ul>
 *   <li><b>悬停</b>：继承飞行怪的基类。有目标时在目标周围 5~9 格、高出 2~4 格的地方换位置（只挑直线飞得到、放得下自己的点）；没目标时在附近慢慢飘。</li>
 *   <li><b>护甲破坏者</b>：看得见、{@code destroyer_range} 格以内每 {@code destroyer_interval} 刻射一发 {@link PatrollerDestroyerEntity}，
 *       效果同全息投影的那一发（不伤人，扣主副手耐久，虚空 / 宅男这类 MRU 盔甲按 0 阶原价改扣 MRU）；每只同时只有一发在飞。</li>
 *   <li><b>定标</b>：{@code strike_range} 格以内每 {@code strike_interval} 刻在目标脚下放一记 {@link OrbitalStrikeEntity}（品红光柱预警
 *       {@code strike_warning_ticks}，默认 2 秒，落下打 {@code strike_damage} = 4 点，伤害类型 {@code orbital_strike}，攻击者是巡卫，
 *       只打技能目标）。落点定在放下那一刻的脚下、不跟人走，走开就躲得掉。每只同时 ≤ {@code max_strikes} 记。</li>
 *   <li><b>精英「审判庭执行机」</b>：一次定 {@code elite_strikes} 记（默认三记，相隔 {@code elite_strike_gap} 刻，每记各落在那一刻目标的脚下）。</li>
 *   <li>弹射物只吃一半（全息投影是非近战 ÷4，这里减成 ÷2：覆写 {@link #hasTrait} 带上 {@code resists_projectile}，倍率走 {@code general.mobs} 的
 *       {@code resistance_multiplier}）。</li>
 *   <li><b>生成</b>（{@link #checkPatrollerSpawnRules}）：不看亮度（全息自己发光）；48 格内同种 ≤ {@code nearby_cap}；
 *       黑暗天灾时改按 64 格、{@code hoanna_spawns.darkness_patroller_max_nearby}（和追加的那一条共用一份计数）。</li>
 * </ul>
 * 生命、护甲在 {@link #finalizeSpawn} 里按配置设成基础值（精英修饰符在那之后由基类挂）。不掉故障核心（掉落表里只有红石、异变残质、精英的异变之心）。
 */
public class InquisitoriumPatrollerEntity extends ECFlyingMonster {

    /** 平时数「附近有几只」的半径 */
    public static final int NEARBY_RADIUS = 48;
    /** 出手时举手的姿势持续多少刻（同步给客户端画） */
    public static final int CAST_POSE_TICKS = 12;
    /** 没目标时最多飘到离地几格 */
    public static final int WANDER_MAX_ALTITUDE = 6;
    /** 护甲破坏者的速度、散布 */
    public static final float DESTROYER_SPEED = 1.0F;
    public static final float DESTROYER_INACCURACY = 1.0F;

    private static final EntityDataAccessor<Boolean> DATA_CASTING = SynchedEntityData.defineId(InquisitoriumPatrollerEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final String TAG_DESTROYER_COOLDOWN = "destroyer_cooldown";
    private static final String TAG_STRIKE_COOLDOWN = "strike_cooldown";

    private int destroyerCooldown = 40;
    private int strikeCooldown = 60;
    private int castTicks;
    /** 执行机这一次定标还剩几记、下一记还要等几刻 */
    private int pendingStrikes;
    private int strikeGap;
    @Nullable
    private PatrollerDestroyerEntity destroyer;
    private final List<OrbitalStrikeEntity> strikes = new ArrayList<>();

    public InquisitoriumPatrollerEntity(EntityType<? extends InquisitoriumPatrollerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    /** 属性默认值（生命 / 护甲照 batch13.md §5.2，飞行速度照怪物稿 0.3） */
    public static AttributeSupplier.Builder createAttributes() {
        PatrollerValues defaults = ECHoannaNativesC.Config.PATROLLER_DEFAULTS;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, defaults.maxHealth())
                .add(Attributes.ARMOR, defaults.armor())
                .add(Attributes.ATTACK_DAMAGE, defaults.strikeDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32D);
    }

    /**
     * 自然生成的判定：不看亮度（同 {@code checkAnyLightMonsterSpawnRules}：非和平、脚下能站），再加附近上限——平时 48 格 ≤ {@code nearby_cap}，
     * 海纳尔黑暗天灾时 64 格 ≤ {@code darkness_patroller_max_nearby}。自然刷怪通过时预订一个名额；刷怪笼只比不预订（同 {@code MobSpawnRules.capped}）。
     */
    public static boolean checkPatrollerSpawnRules(EntityType<InquisitoriumPatrollerEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                                   BlockPos pos, RandomSource random) {
        if (!Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) {
            return false;
        }
        boolean darkness = HoannaSpawnHooks.darknessActive(level.getLevel());
        int radius = darkness ? HoannaSpawnHooks.CALAMITY_RADIUS : NEARBY_RADIUS;
        int cap = darkness ? ECHoannaNativesC.Config.spawns().darknessPatrollerMaxNearby() : ECHoannaNativesC.Config.patroller().nearbyCap();
        if (reason == MobSpawnType.SPAWNER) {
            return MobSpawnRules.nearbyCount(level, type, pos, radius) < cap;
        }
        return MobSpawnRules.tryReserve(level, type, pos, radius, cap);
    }

    /**
     * 实体那一关不看亮度：{@code PathfinderMob.checkSpawnRules} 只多了「走向价值」这一项，{@code Monster} 的走向价值在亮处为负，
     * 不放开的话白天露天、灯铺得很亮的地方都刷不出巡卫（全息自己发光，设计上不看亮度，batch13.md §6.5）。
     * 不改 {@code getWalkTargetValue}：那个还管寻路挑落脚点，改了影响面更大。挡不挡路（{@code checkSpawnObstruction}）由 Forge 另外判。
     */
    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        return true;
    }

    // ================================================================== 生成、数据

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CASTING, false);
    }

    /** 生命、护甲按配置设成基础值、回满（生成时调；精英修饰符在这之后挂） */
    public void applyConfiguredAttributes() {
        PatrollerValues cfg = ECHoannaNativesC.Config.patroller();
        setBase(Attributes.MAX_HEALTH, cfg.maxHealth());
        setBase(Attributes.ARMOR, cfg.armor());
        setHealth(getMaxHealth());
    }

    private void setBase(Attribute attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        applyConfiguredAttributes();
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    /** 弹射物只吃一半：全息的「非近战 ÷4」在巡卫身上减成 ÷2（数据包往类型标签里加的别的特性照常算） */
    @Override
    public boolean hasTrait(TagKey<EntityType<?>> trait) {
        return trait == MobWeakness.RESISTS_PROJECTILE || super.hasTrait(trait);
    }

    /** 正在出手（客户端画举手） */
    public boolean isCasting() {
        return entityData.get(DATA_CASTING);
    }

    // ================================================================== 出手

    /**
     * 朝 target 射一发护甲破坏者（服务端）。已有一发在飞、不是服务端、生成被取消时返回 null。
     */
    @Nullable
    public PatrollerDestroyerEntity shootDestroyer(LivingEntity target) {
        if (!(level() instanceof ServerLevel server) || (destroyer != null && !destroyer.isRemoved())) {
            return null;
        }
        PatrollerDestroyerEntity shot = PatrollerDestroyerEntity.shoot(server, this, target, DESTROYER_SPEED, DESTROYER_INACCURACY);
        destroyer = shot;
        if (shot != null) {
            playSound(ECHoannaNativesC.SOUND_PATROLLER_SHOOT.get(), 0.8F, 1F);
            pose();
        }
        return shot;
    }

    /** 现在还没落下的轨道打击有几记（顺手扔掉已经落下 / 消失的） */
    public int activeStrikes() {
        strikes.removeIf(OrbitalStrikeEntity::isRemoved);
        return strikes.size();
    }

    /**
     * 在 target 脚下放一记小型轨道打击（服务端）：预警 {@code strike_warning_ticks}、伤害 {@code strike_damage}、攻击者是自己、只打技能目标。
     * 不是技能目标、已经有 {@code max_strikes} 记在等、生成被取消时返回 null。
     */
    @Nullable
    public OrbitalStrikeEntity markTarget(LivingEntity target) {
        if (!(level() instanceof ServerLevel server) || !isSkillTarget(target)) {
            return null;
        }
        PatrollerValues cfg = ECHoannaNativesC.Config.patroller();
        if (activeStrikes() >= cfg.maxStrikes()) {
            return null;
        }
        OrbitalStrikeEntity strike = OrbitalStrikeEntity.spawn(server, target.position(), (float) cfg.strikeDamage(), 1F, cfg.strikeWarning(),
                this, this::isSkillTarget);
        if (strike != null) {
            strikes.add(strike);
            playSound(ECHoannaNativesC.SOUND_PATROLLER_MARK.get(), 1F, 1F);
            pose();
        }
        return strike;
    }

    /** 开始一次定标：普通一记；执行机先落一记，剩下的按 {@code elite_strike_gap} 依次落（{@link #customServerAiStep} 里接着放） */
    public void beginMarking(LivingEntity target) {
        markTarget(target);
        if (isElite()) {
            PatrollerValues cfg = ECHoannaNativesC.Config.patroller();
            pendingStrikes = Math.max(0, cfg.eliteStrikes() - 1);
            strikeGap = cfg.eliteStrikeGap();
        }
    }

    /** 执行机这一次定标还剩几记没放（GameTest 用） */
    public int pendingStrikes() {
        return pendingStrikes;
    }

    /** 每刻的计时（{@link #customServerAiStep} 调；GameTest 不放进世界时直接调它推进执行机的连着定标） */
    public void tickTimers() {
        if (destroyerCooldown > 0) {
            destroyerCooldown--;
        }
        if (strikeCooldown > 0) {
            strikeCooldown--;
        }
        if (castTicks > 0 && --castTicks == 0) {
            entityData.set(DATA_CASTING, false);
        }
        if (pendingStrikes > 0 && --strikeGap <= 0) {
            LivingEntity target = getTarget();
            if (target != null && isSkillTarget(target)) {
                markTarget(target);
                pendingStrikes--;
                strikeGap = ECHoannaNativesC.Config.patroller().eliteStrikeGap();
            } else {
                pendingStrikes = 0;
            }
        }
    }

    private void pose() {
        castTicks = CAST_POSE_TICKS;
        entityData.set(DATA_CASTING, true);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        tickTimers();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && random.nextInt(isElite() ? 3 : 6) == 0) {
            level().addParticle(ParticleTypes.END_ROD, getRandomX(0.4D), getY(0.2D + random.nextDouble() * 0.6D), getRandomZ(0.4D),
                    0D, -0.02D, 0D);
        }
    }

    // ================================================================== 移动

    /** 打的时候换到哪：目标周围 5~9 格（从现在的方位偏开最多 90°）、比目标脚下高 2~4 格，直线飞得过去的点 */
    @Nullable
    public Vec3 hoverPoint(LivingEntity target) {
        double current = Math.atan2(getZ() - target.getZ(), getX() - target.getX());
        for (int i = 0; i < 6; i++) {
            double angle = current + (random.nextDouble() - 0.5D) * Math.PI;
            double radius = 5D + random.nextDouble() * 4D;
            Vec3 dest = new Vec3(target.getX() + Math.cos(angle) * radius, target.getY() + 2D + random.nextDouble() * 2D,
                    target.getZ() + Math.sin(angle) * radius);
            if (canFlyTo(dest)) {
                return dest;
            }
        }
        return null;
    }

    /** 直线飞得过去：终点区块已加载、放得下自己、两点之间没有方块挡着 */
    public boolean canFlyTo(Vec3 dest) {
        if (!level().isLoaded(BlockPos.containing(dest))) {
            return false;
        }
        AABB box = getBoundingBox().move(dest.subtract(position()));
        if (!level().noCollision(this, box) || level().containsAnyLiquid(box)) {
            return false;
        }
        double mid = getBbHeight() / 2D;
        return level().clip(new ClipContext(position().add(0D, mid, 0D), dest.add(0D, mid, 0D), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    // ================================================================== 行为目标

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new AttackGoal());
        goalSelector.addGoal(7, new WanderGoal());
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, InquisitoriumPatrollerEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ECHoannaNativesC.SOUND_PATROLLER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECHoannaNativesC.SOUND_PATROLLER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECHoannaNativesC.SOUND_PATROLLER_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_DESTROYER_COOLDOWN, destroyerCooldown);
        tag.putInt(TAG_STRIKE_COOLDOWN, strikeCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        destroyerCooldown = Math.max(0, tag.getInt(TAG_DESTROYER_COOLDOWN));
        strikeCooldown = Math.max(0, tag.getInt(TAG_STRIKE_COOLDOWN));
    }

    /** 绕着目标换位置、射护甲破坏者、定标 */
    private final class AttackGoal extends Goal {
        private int repositionTicks;

        AttackGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && canAttack(target);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            repositionTicks = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target, 30F, 30F);
            if (--repositionTicks <= 0) {
                Vec3 dest = hoverPoint(target);
                if (dest != null) {
                    getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 1D);
                }
                repositionTicks = 30 + random.nextInt(31);
            }
            PatrollerValues cfg = ECHoannaNativesC.Config.patroller();
            double distSq = distanceToSqr(target);
            boolean sees = getSensing().hasLineOfSight(target);
            if (cfg.destroyerInterval() > 0 && destroyerCooldown <= 0 && sees
                    && distSq <= (double) cfg.destroyerRange() * cfg.destroyerRange()) {
                shootDestroyer(target);
                destroyerCooldown = cfg.destroyerInterval();
            }
            if (cfg.strikeInterval() > 0 && strikeCooldown <= 0 && pendingStrikes <= 0 && sees
                    && distSq <= (double) cfg.strikeRange() * cfg.strikeRange()) {
                beginMarking(target);
                strikeCooldown = cfg.strikeInterval();
            }
        }
    }

    /** 没目标时在附近慢慢飘（飘的点离地不远） */
    private final class WanderGoal extends Goal {

        WanderGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getTarget() == null && !getMoveControl().hasWanted() && random.nextInt(reducedTickDelay(40)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            for (int i = 0; i < 6; i++) {
                Vec3 dest = position().add((random.nextDouble() - 0.5D) * 12D, (random.nextDouble() - 0.5D) * 4D, (random.nextDouble() - 0.5D) * 12D);
                if (canFlyTo(dest) && nearGround(dest)) {
                    getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 0.6D);
                    return;
                }
            }
        }

        private boolean nearGround(Vec3 dest) {
            BlockPos.MutableBlockPos pos = BlockPos.containing(dest).mutable();
            for (int i = 0; i <= WANDER_MAX_ALTITUDE; i++) {
                if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                    return true;
                }
                pos.move(0, -1, 0);
            }
            return false;
        }
    }
}
