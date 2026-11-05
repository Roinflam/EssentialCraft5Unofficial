package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.MobWeakness;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA.Config.FrostValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 霜蚀者 {@code frostbitten}（第十三批 13d-N1，batch13.md §5.2；怪物稿 §2.4.1）：冻在冰壳里的人形遗骸，关节长着冰晶，冰冻腐化之地夜里出没。
 * <ul>
 *   <li><b>冻结</b>：近战打实一下给目标加 {@code freeze_per_hit} 刻冰冻（原版的 {@code TicksFrozen}，满 140 刻后照原版细雪规则冻伤、减速）。
 *       穿着皮革护甲的照原版不会冻（{@code LivingEntity.canFreeze} 看 {@code #freeze_immune_wearables}）——这是原版就给了的对策。
 *       加冰冻刻在 {@code ECHoannaNativesA.HoannaNativesAEvents} 里、打实以后才加（{@link #chill}）。</li>
 *   <li><b>冰息</b>：每 {@code breath_interval} 刻一口：先蓄力 {@code breath_charge} 刻（白雾往嘴边聚、停下不动，预警），然后朝
 *       <b>蓄力开始那一刻</b>的方向往前方 90° 锥形喷 {@code breath_range} 格：固定 {@code breath_damage} + 目标最大生命 × {@code breath_health_percent}
 *       （{@code aberrant_strike}，无视护甲，保护附魔、抗性照常减），另加 {@code breath_freeze_ticks} 刻冰冻。蓄力时往旁边闪开就躲掉了；
 *       只打技能目标、隔着方块喷不到。</li>
 *   <li><b>弱点</b>：怕火（{@code weak_to_fire}，×1.5，按 {@link #hasTrait}）；自己不会冻、不吃冻伤。</li>
 *   <li><b>精英「霜棺督卫」</b>：左手冰盾，正面 90° 以内来的弹射物全挡、正面的近战少吃 {@code elite_shield_melee_reduction}（从背后、侧面打照常吃）；
 *       每 {@code elite_seal_interval} 刻冰封一次：脚下亮起 {@code elite_seal_radius} 格的冰圈（预警 {@code elite_seal_warning} 刻），
 *       到时还站在圈里的技能目标冻透 {@code elite_seal_ticks} 刻、走不动（缓慢 IV），不直接扣血；穿皮革的照样不冻。看见冰圈走出去就躲开了。</li>
 * </ul>
 * 生命、护甲、攻击在 {@link #finalizeSpawn} 里按配置设成基础值（精英修饰符在那之后由基类挂）。动画三段：近战、冰息、冰封（{@link #anims}）。
 */
public class FrostbittenEntity extends ECMonster {

    public static final int ANIM_ATTACK = 0;
    public static final int ANIM_BREATH = 1;
    public static final int ANIM_SEAL = 2;

    /** 冰息锥形的半角余弦（前方 90° = 左右各 45°） */
    public static final double BREATH_CONE_COS = Math.cos(Math.toRadians(45D));
    /** 冰息上下够多高（格，目标中心和嘴的高度差） */
    public static final double BREATH_VERTICAL = 2.5D;
    /** 冰盾正面的半角余弦（正面 90°） */
    public static final double SHIELD_COS = Math.cos(Math.toRadians(45D));
    /** 冰封上下够多高（格） */
    public static final double SEAL_VERTICAL = 2D;
    /** 冰封时附带的缓慢等级（0 起算，3 = 缓慢 IV） */
    public static final int SEAL_SLOW_AMPLIFIER = 3;

    private static final String TAG_BREATH_COOLDOWN = "breath_cooldown";
    private static final String TAG_SEAL_COOLDOWN = "seal_cooldown";

    /** 冰息还要蓄几刻（同步给客户端画白雾；0 = 没在蓄） */
    private static final EntityDataAccessor<Integer> DATA_BREATH = SynchedEntityData.defineId(FrostbittenEntity.class, EntityDataSerializers.INT);

    public final MobAnimStates anims = new MobAnimStates(3).channel(ANIM_ATTACK, 1).channel(ANIM_BREATH, 1).channel(ANIM_SEAL, 1);

    private int breathCooldown = 60;
    private int sealCooldown = 100;
    private int breathTicks;
    private int sealTicks;
    @Nullable
    private Vec3 breathDir;

    public FrostbittenEntity(EntityType<? extends FrostbittenEntity> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    /** 属性默认值（生成时 {@link #applyConfiguredAttributes} 再按配置改基础值） */
    public static AttributeSupplier.Builder createAttributes() {
        FrostValues defaults = ECHoannaNativesA.Config.FROST_DEFAULTS;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, defaults.maxHealth())
                .add(Attributes.ARMOR, defaults.armor())
                .add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.ATTACK_DAMAGE, defaults.attackDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    /** 自然生成的判定：原版怪物（夜里 / 暗处，非和平）；附近数量上限在注册时另包一层 */
    public static boolean checkFrostbittenSpawnRules(EntityType<FrostbittenEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                                     RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_BREATH, 0);
    }

    // ================================================================== 生成

    /** 生命、护甲、攻击按配置设成基础值、回满（生成时调；精英修饰符在这之后挂） */
    public void applyConfiguredAttributes() {
        FrostValues cfg = ECHoannaNativesA.Config.frost();
        setBase(Attributes.MAX_HEALTH, cfg.maxHealth());
        setBase(Attributes.ARMOR, cfg.armor());
        setBase(Attributes.ATTACK_DAMAGE, cfg.attackDamage());
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

    // ================================================================== AI

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new SkillGoal());
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (breathCooldown > 0) {
            breathCooldown--;
        }
        if (sealCooldown > 0) {
            sealCooldown--;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            anims.play(this, ANIM_ATTACK);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            int charging = entityData.get(DATA_BREATH);
            if (charging > 0) {
                // 白雾往嘴边聚（蓄力的预警），每刻两颗
                Vec3 mouth = mouth();
                for (int i = 0; i < 2; i++) {
                    Vec3 from = mouth.add((random.nextDouble() - 0.5D) * 2.4D, (random.nextDouble() - 0.5D) * 1.6D, (random.nextDouble() - 0.5D) * 2.4D);
                    Vec3 v = mouth.subtract(from).scale(0.12D);
                    level().addParticle(ParticleTypes.SNOWFLAKE, from.x, from.y, from.z, v.x, v.y, v.z);
                }
            } else if (random.nextInt(isElite() ? 6 : 14) == 0) {
                level().addParticle(ParticleTypes.SNOWFLAKE, getRandomX(0.5D), getY(0.4D + random.nextDouble() * 0.5D), getRandomZ(0.5D), 0D, -0.02D, 0D);
            }
        }
    }

    /** 嘴的位置（冰息从这里喷） */
    public Vec3 mouth() {
        return new Vec3(getX(), getEyeY() - 0.15D, getZ());
    }

    // ================================================================== 冻结

    /** 这一下是不是它亲手的近战（直接实体就是它、伤害类型是怪物近战） */
    public static boolean isMeleeFrom(DamageSource source, Entity attacker) {
        return source.getDirectEntity() == attacker && source.is(DamageTypes.MOB_ATTACK);
    }

    /**
     * 给 target 加 ticks 刻冰冻：能冻（{@code canFreeze}：穿皮革、免冻的种类、旁观者都不冻）才加，最多加到「冻透所需刻数 + ticks」，
     * 已经比这还多的（冰封给的）不往下拉。返回加上了没有。
     */
    public boolean chill(LivingEntity target, int ticks) {
        if (ticks <= 0 || !target.canFreeze()) {
            return false;
        }
        int current = target.getTicksFrozen();
        int cap = target.getTicksRequiredToFreeze() + ticks;
        target.setTicksFrozen(Math.max(current, Math.min(current + ticks, cap)));
        return true;
    }

    /** 自己不会冻 */
    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypeTags.IS_FREEZING) || super.isInvulnerableTo(source);
    }

    /** 怕火（×{@code weakness_multiplier}） */
    @Override
    public boolean hasTrait(TagKey<EntityType<?>> trait) {
        return trait == MobWeakness.WEAK_TO_FIRE || super.hasTrait(trait);
    }

    // ================================================================== 冰息

    /** 冰息现在能不能对 target 起手：开着、冷却好了、是技能目标、在射程里、看得见 */
    public boolean canBreathAt(LivingEntity target) {
        FrostValues cfg = ECHoannaNativesA.Config.frost();
        if (cfg.breathInterval() <= 0 || breathCooldown > 0 || breathTicks > 0 || sealTicks > 0 || !isSkillTarget(target)) {
            return false;
        }
        double range = cfg.breathRange();
        return distanceToSqr(target) <= (range + 0.5D) * (range + 0.5D) && getSensing().hasLineOfSight(target);
    }

    /** 开始蓄力：方向定在这一刻（水平指向 target），之后不再跟着转 */
    public void startBreath(LivingEntity target) {
        FrostValues cfg = ECHoannaNativesA.Config.frost();
        Vec3 flat = new Vec3(target.getX() - getX(), 0D, target.getZ() - getZ());
        breathDir = flat.lengthSqr() < 1.0E-6D ? Vec3.directionFromRotation(0F, getYRot()) : flat.normalize();
        breathTicks = cfg.breathCharge();
        breathCooldown = cfg.breathInterval();
        entityData.set(DATA_BREATH, breathTicks);
        getNavigation().stop();
        anims.play(this, ANIM_BREATH);
        playSound(ECHoannaNativesA.SOUND_FROSTBITTEN_BREATH_CHARGE.get(), 1F, 1F);
    }

    /** 冰息还要蓄几刻（0 = 没在蓄） */
    public int breathTicks() {
        return breathTicks;
    }

    /** 蓄力时定下的方向（水平单位向量）；没在蓄返回 null */
    @Nullable
    public Vec3 breathDirection() {
        return breathDir;
    }

    /**
     * 喷出去（服务端）：前方 90° 锥形、{@code breath_range} 格以内、嘴看得见的技能目标各挨一下 {@code aberrant_strike}
     * （固定 + 最大生命 × 比例）并加冰冻。返回挨了这一下的目标。没在蓄（或者蓄的方向没了）什么都不做。
     */
    public List<LivingEntity> releaseBreath() {
        List<LivingEntity> hit = new ArrayList<>();
        Vec3 dir = breathDir;
        breathTicks = 0;
        breathDir = null;
        entityData.set(DATA_BREATH, 0);
        if (dir == null || !(level() instanceof ServerLevel server)) {
            return hit;
        }
        FrostValues cfg = ECHoannaNativesA.Config.frost();
        Vec3 mouth = mouth();
        double range = cfg.breathRange();
        playSound(ECHoannaNativesA.SOUND_FROSTBITTEN_BREATH.get(), 1.2F, 0.9F);
        for (int i = 0; i < 24; i++) {
            double t = 0.5D + random.nextDouble() * (range - 0.5D);
            Vec3 spread = dir.yRot((float) ((random.nextDouble() - 0.5D) * Math.PI / 2D)).scale(t);
            server.sendParticles(ParticleTypes.SNOWFLAKE, mouth.x + spread.x, mouth.y + (random.nextDouble() - 0.5D), mouth.z + spread.z,
                    1, 0D, 0D, 0D, 0.01D);
        }
        for (LivingEntity target : skillTargets(new AABB(mouth, mouth).inflate(range + 1D, BREATH_VERTICAL + 1D, range + 1D))) {
            Vec3 center = target.getBoundingBox().getCenter();
            if (!inBreathCone(mouth, dir, center, range) || !clearLine(mouth, center)) {
                continue;
            }
            float damage = (float) (cfg.breathDamage() + target.getMaxHealth() * cfg.breathHealthPercent());
            if (target.hurt(ECDamageTypes.aberrantStrike(this), damage)) {
                hit.add(target);
                chill(target, cfg.breathFreeze());
            }
        }
        return hit;
    }

    /**
     * 纯函数：point 在不在从 origin 朝 dir（水平单位向量）喷出的冰息里——水平距离 ≤ range、水平夹角 ≤ 45°、高度差 ≤ {@value #BREATH_VERTICAL}。
     * 几乎正好在嘴的正上 / 正下方（水平 0.5 格以内）的也算。
     */
    public static boolean inBreathCone(Vec3 origin, Vec3 dir, Vec3 point, double range) {
        Vec3 d = point.subtract(origin);
        if (Math.abs(d.y) > BREATH_VERTICAL) {
            return false;
        }
        Vec3 flat = new Vec3(d.x, 0D, d.z);
        double length = flat.length();
        if (length > range) {
            return false;
        }
        if (length < 0.5D) {
            return true;
        }
        Vec3 facing = new Vec3(dir.x, 0D, dir.z);
        if (facing.lengthSqr() < 1.0E-8D) {
            return false;
        }
        return flat.scale(1D / length).dot(facing.normalize()) >= BREATH_CONE_COS;
    }

    private boolean clearLine(Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    // ================================================================== 冰封（精英）

    /** 冰封现在能不能起手：精英、开着、冷却好了、target 是技能目标且在圈边附近 */
    public boolean canSealNear(LivingEntity target) {
        FrostValues cfg = ECHoannaNativesA.Config.frost();
        if (!isElite() || cfg.sealInterval() <= 0 || sealCooldown > 0 || breathTicks > 0 || sealTicks > 0 || !isSkillTarget(target)) {
            return false;
        }
        double reach = cfg.sealRadius() + 1D;
        return distanceToSqr(target) <= reach * reach;
    }

    /** 开始冰封：脚下亮冰圈，{@code elite_seal_warning} 刻后冻住还在圈里的人 */
    public void startSeal() {
        FrostValues cfg = ECHoannaNativesA.Config.frost();
        sealTicks = cfg.sealWarning();
        sealCooldown = cfg.sealInterval();
        getNavigation().stop();
        anims.play(this, ANIM_SEAL);
        playSound(ECHoannaNativesA.SOUND_FROSTBITTEN_SEAL_WARN.get(), 1F, 1F);
        sealRing(cfg.sealRadius());
    }

    /** 冰封还要预警几刻（0 = 没在冰封） */
    public int sealTicks() {
        return sealTicks;
    }

    /** 冰圈：圆周上一圈雪花（服务端发给周围的人） */
    private void sealRing(double radius) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        int points = Mth.clamp((int) (radius * 6D), 8, 48);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2D * i / points;
            server.sendParticles(ParticleTypes.SNOWFLAKE, getX() + Math.cos(a) * radius, getY() + 0.1D, getZ() + Math.sin(a) * radius,
                    1, 0D, 0.02D, 0D, 0D);
        }
    }

    /**
     * 冻住（服务端）：水平 {@code elite_seal_radius} 格、上下 {@value #SEAL_VERTICAL} 格以内的技能目标，能冻的冻透 {@code elite_seal_ticks} 刻
     * （冰冻刻至少设到「冻透所需 + 2 × 时长」，按原版每刻退 2 刻算正好冻透那么久）并给缓慢 IV。不扣血。返回冻住了谁。
     */
    public List<LivingEntity> releaseSeal() {
        List<LivingEntity> sealed = new ArrayList<>();
        sealTicks = 0;
        if (!(level() instanceof ServerLevel)) {
            return sealed;
        }
        FrostValues cfg = ECHoannaNativesA.Config.frost();
        double radius = cfg.sealRadius();
        playSound(ECHoannaNativesA.SOUND_FROSTBITTEN_SEAL.get(), 1.2F, 0.8F);
        for (LivingEntity target : skillTargets(getBoundingBox().inflate(radius, SEAL_VERTICAL, radius))) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            if (dx * dx + dz * dz > radius * radius || Math.abs(target.getY() - getY()) > SEAL_VERTICAL || !target.canFreeze()) {
                continue;
            }
            int frozen = target.getTicksRequiredToFreeze() + cfg.sealTicks() * 2;
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), frozen));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, cfg.sealTicks(), SEAL_SLOW_AMPLIFIER), this);
            sealed.add(target);
        }
        return sealed;
    }

    // ================================================================== 冰盾（精英）

    /**
     * 正面挡（冰盾、遗民队长的护盾共用）：伤害有来源位置、不是穿盾的（{@code #bypasses_shield}，含穿甲的大招和 {@code /kill}），
     * 来源在 self 身体朝向左右 acos(cosHalf) 以内。
     */
    public static boolean shieldFacing(LivingEntity self, DamageSource source, double cosHalf) {
        if (source.is(DamageTypeTags.BYPASSES_SHIELD)) {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return false;
        }
        Vec3 to = new Vec3(from.x - self.getX(), 0D, from.z - self.getZ());
        if (to.lengthSqr() < 1.0E-6D) {
            return false;
        }
        Vec3 facing = Vec3.directionFromRotation(0F, self.getYRot());
        return to.normalize().dot(facing) >= cosHalf;
    }

    /** 霜棺督卫的冰盾：正面来的弹射物整个挡掉（{@code hurt} 返回 false）、正面的其余伤害按 {@code elite_shield_melee_reduction} 减 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isElite() && shieldFacing(this, source, SHIELD_COS)) {
            if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                playSound(ECHoannaNativesA.SOUND_FROSTBITTEN_SHIELD.get(), 1F, 0.9F + random.nextFloat() * 0.2F);
                return false;
            }
            amount *= (float) (1D - ECHoannaNativesA.Config.frost().shieldMeleeReduction());
        }
        return super.hurt(source, amount);
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
        return ECHoannaNativesA.SOUND_FROSTBITTEN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECHoannaNativesA.SOUND_FROSTBITTEN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECHoannaNativesA.SOUND_FROSTBITTEN_DEATH.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_BREATH_COOLDOWN, breathCooldown);
        tag.putInt(TAG_SEAL_COOLDOWN, sealCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        breathCooldown = Math.max(0, tag.getInt(TAG_BREATH_COOLDOWN));
        sealCooldown = Math.max(0, tag.getInt(TAG_SEAL_COOLDOWN));
    }

    /** 冰息 / 冰封：起手、蓄着（站住不动、看着目标）、到点放出去；打断了（目标没了、自己死了）就作废，冷却照挂 */
    private final class SkillGoal extends Goal {

        SkillGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && (canSealNear(target) || canBreathAt(target));
        }

        @Override
        public boolean canContinueToUse() {
            return isAlive() && (breathTicks > 0 || sealTicks > 0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            if (canSealNear(target)) {
                startSeal();
            } else {
                startBreath(target);
            }
        }

        @Override
        public void tick() {
            getNavigation().stop();
            LivingEntity target = getTarget();
            if (target != null) {
                getLookControl().setLookAt(target, 30F, 30F);
            }
            if (breathTicks > 0) {
                entityData.set(DATA_BREATH, breathTicks);
                if (--breathTicks <= 0) {
                    releaseBreath();
                }
            } else if (sealTicks > 0) {
                if (sealTicks % 4 == 0) {
                    sealRing(ECHoannaNativesA.Config.frost().sealRadius());
                }
                if (--sealTicks <= 0) {
                    releaseSeal();
                }
            }
        }

        @Override
        public void stop() {
            breathTicks = 0;
            sealTicks = 0;
            breathDir = null;
            entityData.set(DATA_BREATH, 0);
        }
    }
}
