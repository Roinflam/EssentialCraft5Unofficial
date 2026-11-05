package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.ECFlyingMonster;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.MobWeakness;
import com.flameforge.essentialcraft5.hoanna.HoannaSpawnHooks;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.network.S2CGunBeamPacket;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA.Config.PrismValues;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.MobCategory;
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
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 浮游棱晶 {@code mana_prism}（第十三批 13d-N1，batch13.md §5.2；怪物稿 §2.4.2）：一人高的悬浮多面水晶，中心一颗跳动的光点，下方三片小棱片缓缓转。
 * 魔力腐化之地夜里出没。
 * <ul>
 *   <li><b>保持距离</b>：有目标时绕着它在 {@code keep_min_distance}~{@code keep_max_distance} 格之间换位置（只挑直线飞得到、放得下自己的点）。</li>
 *   <li><b>锁定光束</b>：每 {@code lock_interval} 刻锁一次——看得见、{@code beam_range} 格以内的技能目标，把<b>这一刻</b>指向它的方向记下来，
 *       一道细光线（客户端粒子）沿这个方向连出去，{@code lock_ticks} 刻（默认 1.5 秒）以后沿记下的方向射光束：不生成实体的射线，
 *       碰到方块就停（方块挡得住），打中路上第一个技能目标：固定 {@code beam_damage} + 目标最大生命 × {@code beam_health_percent}，
 *       伤害类型 {@code mob_bolt}（算弹射物，照常吃护甲；没有直接实体，盾牌照原版挡）。锁定期间横移出那条线就打不中。锁定时它停在原地不动。</li>
 *   <li><b>脆</b>：怕近战（{@code weak_to_melee} ×1.5）、不怕箭（{@code resists_projectile} ×0.5），逼人贴上去打。</li>
 *   <li><b>精英「棱晶集束」</b>：一次射 {@code elite_beams} 束，以锁定方向为中间一束、左右每隔 {@code elite_spread_degrees} 度水平排开（扇形）；
 *       同一个目标被几束扫到也只算一下。</li>
 * </ul>
 * 生命、护甲在 {@link #finalizeSpawn} 里按配置设成基础值。光束的画面借枪械光束包（{@code S2CGunBeamPacket}，魔力色）。动画两段：锁定、开火。
 */
public class ManaPrismEntity extends ECFlyingMonster {

    public static final int ANIM_LOCK = 0;
    public static final int ANIM_FIRE = 1;

    /** 光束的半宽（格）：目标碰撞箱外扩这么多算擦到。只比射线粗一点，横移半步就躲开 */
    public static final double BEAM_RADIUS = 0.15D;
    /** 光束画面的透镜颜色（枪械光束包的 balance：3 = 魔力，品红） */
    public static final int BEAM_COLOR = 3;
    /** 没目标时最多飘到离地几格 */
    public static final int WANDER_MAX_ALTITUDE = 5;

    private static final String TAG_LOCK_COOLDOWN = "lock_cooldown";

    /** 锁定还剩几刻（同步给客户端画预览光线；0 = 没在锁） */
    private static final EntityDataAccessor<Integer> DATA_LOCK = SynchedEntityData.defineId(ManaPrismEntity.class, EntityDataSerializers.INT);
    /** 锁定的方向（单位向量） */
    private static final EntityDataAccessor<Vector3f> DATA_LOCK_DIR = SynchedEntityData.defineId(ManaPrismEntity.class, EntityDataSerializers.VECTOR3);

    private static final Vector3f LOCK_COLOR = new Vector3f(0.88F, 0.45F, 1F);

    public final MobAnimStates anims = new MobAnimStates(2).channel(ANIM_LOCK, 1).channel(ANIM_FIRE, 1);

    private int lockTicks;
    private int lockCooldown = 40;
    @Nullable
    private Vec3 lockDir;

    public ManaPrismEntity(EntityType<? extends ManaPrismEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    /** 属性默认值（生成时 {@link #applyConfiguredAttributes} 再按配置改基础值） */
    public static AttributeSupplier.Builder createAttributes() {
        PrismValues defaults = ECHoannaNativesA.Config.PRISM_DEFAULTS;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, defaults.maxHealth())
                .add(Attributes.ARMOR, defaults.armor())
                .add(Attributes.ATTACK_DAMAGE, defaults.beamDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    /** 自然生成的判定：原版怪物（夜里 / 暗处，非和平）；附近数量上限见 {@link #checkPrismSpawnRulesCapped} */
    public static boolean checkPrismSpawnRules(EntityType<ManaPrismEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                               RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    /**
     * 注册给刷怪位置的判定：{@link #checkPrismSpawnRules} + 附近上限。平时 64 格内同种 ≤ {@code mana_prism.nearby_cap}；
     * 地脉风暴里、这个群系本来的怪物表里没有棱晶（也就是风暴给陆地追加的那一条刷出来的）时，改按
     * {@code hoanna_spawns.storm_prism_max_nearby}（batch13.md §6.5「每人附近 ≤ 4」），和 {@code HoannaSpawnHooks} 在刷怪表那一级的闸
     * 同一半径、同一上限：一次组刷 1~2 只也不会越过它。魔力 / 冰冻腐化之地本来就刷棱晶，风暴里照平时的上限。
     * 刷怪笼来问的只比数、不预订（同 {@code MobSpawnRules.capped}）。
     */
    public static boolean checkPrismSpawnRulesCapped(EntityType<ManaPrismEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                                     RandomSource random) {
        return checkPrismSpawnRules(type, level, reason, pos, random) && nearbyCapAllows(type, level, reason, pos);
    }

    /** {@link #checkPrismSpawnRulesCapped} 的附近上限那一半（不看亮度，给 GameTest 直接调） */
    public static boolean nearbyCapAllows(EntityType<?> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos) {
        boolean stormExtra = stormExtraSpawn(level.getLevel(), pos, type);
        int radius = stormExtra ? HoannaSpawnHooks.CALAMITY_RADIUS : ECHoannaNativesA.PRISM_NEARBY_RADIUS;
        int cap = stormExtra ? ECHoannaNativesC.Config.spawns().stormPrismMaxNearby() : ECHoannaNativesA.Config.prism().nearbyCap();
        if (reason == MobSpawnType.SPAWNER) {
            return cap > 0 && MobSpawnRules.nearbyCount(level, type, pos, radius) < cap;
        }
        return MobSpawnRules.tryReserve(level, type, pos, radius, cap);
    }

    /** 这次生成算不算地脉风暴追加的：海纳尔正在地脉风暴，且这一格群系自己的怪物表里没有棱晶 */
    public static boolean stormExtraSpawn(ServerLevel level, BlockPos pos, EntityType<?> type) {
        if (level == null || !HoannaEvents.isActive(level, HoannaEvent.LEY_STORM)) {
            return false;
        }
        for (MobSpawnSettings.SpawnerData data : level.getBiome(pos).value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap()) {
            if (data.type == type) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_LOCK, 0);
        entityData.define(DATA_LOCK_DIR, new Vector3f(0F, 0F, 1F));
    }

    // ================================================================== 生成

    /** 生命、护甲按配置设成基础值、回满（生成时调；精英修饰符在这之后挂） */
    public void applyConfiguredAttributes() {
        PrismValues cfg = ECHoannaNativesA.Config.prism();
        setBase(Attributes.MAX_HEALTH, cfg.maxHealth());
        setBase(Attributes.ARMOR, cfg.armor());
        setBase(Attributes.ATTACK_DAMAGE, cfg.beamDamage());
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

    /** 怕近战、不怕箭 */
    @Override
    public boolean hasTrait(TagKey<EntityType<?>> trait) {
        return trait == MobWeakness.WEAK_TO_MELEE || trait == MobWeakness.RESISTS_PROJECTILE || super.hasTrait(trait);
    }

    // ================================================================== AI

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new AttackGoal());
        goalSelector.addGoal(7, new WanderGoal());
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (lockCooldown > 0) {
            lockCooldown--;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && entityData.get(DATA_LOCK) > 0 && tickCount % 2 == 0) {
            drawLockPreview();
        }
    }

    /** 客户端：沿锁定方向撒一串小粒子到光束会停的地方，终点多几颗（每 2 刻 8 颗） */
    private void drawLockPreview() {
        Vector3f d = entityData.get(DATA_LOCK_DIR);
        Vec3 dir = new Vec3(d.x(), d.y(), d.z());
        if (dir.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 from = beamOrigin();
        Vec3 far = from.add(dir.normalize().scale(ECHoannaNativesA.Config.prism().beamRange()));
        BlockHitResult block = level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        DustParticleOptions dust = new DustParticleOptions(LOCK_COLOR, 0.6F);
        for (int i = 0; i < 5; i++) {
            Vec3 at = from.lerp(end, random.nextDouble());
            level().addParticle(dust, at.x, at.y, at.z, 0D, 0D, 0D);
        }
        for (int i = 0; i < 3; i++) {
            level().addParticle(dust, end.x + (random.nextDouble() - 0.5D) * 0.4D, end.y + (random.nextDouble() - 0.5D) * 0.4D,
                    end.z + (random.nextDouble() - 0.5D) * 0.4D, 0D, 0D, 0D);
        }
    }

    /** 光束从哪射出（身体正中） */
    public Vec3 beamOrigin() {
        return position().add(0D, getBbHeight() * 0.5D, 0D);
    }

    // ================================================================== 锁定与光束

    /** 现在能不能锁 target：冷却好了、没在锁、是技能目标、射程以内、看得见 */
    public boolean canLockOn(LivingEntity target) {
        if (lockCooldown > 0 || lockTicks > 0 || !isSkillTarget(target)) {
            return false;
        }
        double range = ECHoannaNativesA.Config.prism().beamRange();
        return beamOrigin().distanceToSqr(target.getBoundingBox().getCenter()) <= range * range && getSensing().hasLineOfSight(target);
    }

    /** 锁定 target：方向定在这一刻（指向它的碰撞箱中心），之后不再跟着转 */
    public void startLock(LivingEntity target) {
        Vec3 aim = target.getBoundingBox().getCenter().subtract(beamOrigin());
        startLock(aim);
    }

    /** 按给定方向锁定（测试、展示厅用） */
    public void startLock(Vec3 direction) {
        PrismValues cfg = ECHoannaNativesA.Config.prism();
        lockDir = direction.lengthSqr() < 1.0E-8D ? Vec3.directionFromRotation(0F, getYRot()) : direction.normalize();
        lockTicks = cfg.lockTicks();
        lockCooldown = cfg.lockInterval();
        entityData.set(DATA_LOCK, lockTicks);
        entityData.set(DATA_LOCK_DIR, new Vector3f((float) lockDir.x, (float) lockDir.y, (float) lockDir.z));
        anims.play(this, ANIM_LOCK);
        playSound(ECHoannaNativesA.SOUND_PRISM_LOCK.get(), 1F, 1.8F);
    }

    /** 锁定还剩几刻（0 = 没在锁） */
    public int lockTicks() {
        return lockTicks;
    }

    /** 锁定的方向；没在锁返回 null */
    @Nullable
    public Vec3 lockDirection() {
        return lockDir;
    }

    /** 这一轮要射的几束方向：普通一束（锁定方向）；精英按 {@code elite_beams} 以锁定方向为中线水平扇开 */
    public List<Vec3> beamDirections() {
        List<Vec3> out = new ArrayList<>();
        Vec3 dir = lockDir;
        if (dir == null) {
            return out;
        }
        PrismValues cfg = ECHoannaNativesA.Config.prism();
        int beams = isElite() ? Math.max(1, cfg.eliteBeams()) : 1;
        double step = Math.toRadians(cfg.eliteSpread());
        for (int i = 0; i < beams; i++) {
            double offset = (i - (beams - 1) / 2D) * step;
            out.add(dir.yRot((float) offset));
        }
        return out;
    }

    /**
     * 一束光束走到哪、打中谁。
     *
     * @param end 光束停下的地方（碰到的方块表面，或射程尽头）
     * @param hit 路上第一个技能目标（没有为 null）
     */
    public record BeamTrace(Vec3 end, @Nullable LivingEntity hit) {
    }

    /** 从 from 朝 dir 射 range 格：先找挡住的方块，再在这一段上找第一个碰撞箱（外扩 {@value #BEAM_RADIUS}）被穿过的技能目标 */
    public BeamTrace trace(Vec3 from, Vec3 dir, double range) {
        Vec3 far = from.add(dir.normalize().scale(range));
        BlockHitResult block = level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1D), this::isSkillTarget)) {
            AABB box = entity.getBoundingBox().inflate(BEAM_RADIUS);
            double distance;
            if (box.contains(from)) {
                distance = 0D;
            } else {
                Optional<Vec3> crossing = box.clip(from, end);
                if (crossing.isEmpty()) {
                    continue;
                }
                distance = from.distanceToSqr(crossing.get());
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return new BeamTrace(end, best);
    }

    /**
     * 开火（服务端）：按 {@link #beamDirections} 每束各射一道，打中的目标每个只挨一下（固定 + 最大生命 × 比例，{@code mob_bolt}）。
     * 没在锁（方向没了）什么都不做。返回挨了光束的目标。
     */
    public List<LivingEntity> fireBeams() {
        List<LivingEntity> damaged = new ArrayList<>();
        List<Vec3> dirs = beamDirections();
        lockTicks = 0;
        lockDir = null;
        entityData.set(DATA_LOCK, 0);
        if (dirs.isEmpty() || !(level() instanceof ServerLevel server)) {
            return damaged;
        }
        PrismValues cfg = ECHoannaNativesA.Config.prism();
        Vec3 from = beamOrigin();
        Set<LivingEntity> struck = new LinkedHashSet<>();
        for (Vec3 dir : dirs) {
            BeamTrace beam = trace(from, dir, cfg.beamRange());
            S2CGunBeamPacket.sendAround(server, getId(), from, beam.end(), BEAM_COLOR);
            if (beam.hit() != null) {
                struck.add(beam.hit());
            }
        }
        anims.play(this, ANIM_FIRE);
        playSound(ECHoannaNativesA.SOUND_PRISM_SHOOT.get(), 1F, 1.2F + random.nextFloat() * 0.2F);
        for (LivingEntity target : struck) {
            float damage = (float) (cfg.beamDamage() + target.getMaxHealth() * cfg.beamHealthPercent());
            if (target.hurt(ECDamageTypes.mobBolt(this), damage)) {
                damaged.add(target);
            }
        }
        return damaged;
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

    /** 绕着目标换到哪：离目标 {@code keep_min}~{@code keep_max} 格、比目标脚下高 1.5~4 格、直线飞得过去的点 */
    @Nullable
    public Vec3 keepDistancePoint(LivingEntity target) {
        PrismValues cfg = ECHoannaNativesA.Config.prism();
        double current = Math.atan2(getZ() - target.getZ(), getX() - target.getX());
        for (int i = 0; i < 6; i++) {
            double angle = current + (random.nextDouble() - 0.5D) * Math.PI * 0.8D;
            double radius = cfg.keepMin() + random.nextDouble() * Math.max(0D, cfg.keepMax() - cfg.keepMin());
            Vec3 dest = new Vec3(target.getX() + Math.cos(angle) * radius, target.getY() + 1.5D + random.nextDouble() * 2.5D,
                    target.getZ() + Math.sin(angle) * radius);
            if (canFlyTo(dest)) {
                return dest;
            }
        }
        return null;
    }

    // ================================================================== 杂项

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ECHoannaNativesA.SOUND_PRISM_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECHoannaNativesA.SOUND_PRISM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECHoannaNativesA.SOUND_PRISM_DEATH.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_LOCK_COOLDOWN, lockCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lockCooldown = Math.max(0, tag.getInt(TAG_LOCK_COOLDOWN));
    }

    /** 保持距离、锁定、开火 */
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
            return canUse() || lockTicks > 0;
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
        public void stop() {
            lockTicks = 0;
            lockDir = null;
            entityData.set(DATA_LOCK, 0);
        }

        @Override
        public void tick() {
            if (lockTicks > 0) {
                // 锁定时停在原地，光束从这里射出去
                getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.2D);
                if (--lockTicks <= 0) {
                    fireBeams();
                } else {
                    entityData.set(DATA_LOCK, lockTicks);
                }
                return;
            }
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target, 30F, 30F);
            if (--repositionTicks <= 0) {
                Vec3 dest = keepDistancePoint(target);
                if (dest != null) {
                    getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 1D);
                }
                repositionTicks = 30 + random.nextInt(21);
            }
            if (canLockOn(target)) {
                startLock(target);
            }
        }
    }

    /** 没目标时在附近慢慢飘（不飘到离地 {@value #WANDER_MAX_ALTITUDE} 格以上） */
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
                Vec3 dest = position().add((random.nextDouble() - 0.5D) * 10D, (random.nextDouble() - 0.5D) * 3D, (random.nextDouble() - 0.5D) * 10D);
                if (canFlyTo(dest) && nearGround(dest)) {
                    getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 0.5D);
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
