package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB.Config.AberrantValues;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 混沌畸变体 {@code chaos_aberrant}（第十三批 13d-N2，batch13.md §5.2；怪物稿 §2.4.3）：混沌 / 暗影腐化之地四肢着地的驼背肉怪，背上几道暗红的裂缝。
 * <ul>
 *   <li><b>近战</b>：普通一口 {@code attack_damage}（吃护甲）。</li>
 *   <li><b>冲撞</b>：每 {@code charge_cooldown} 刻一次，目标在 3~{@code charge_distance}+2 格、看得见、是技能目标时：低头蓄力 {@code charge_windup} 刻，
 *       方向在蓄力开始那一刻定死，地上沿这条线冒暗红的尘（预警）；蓄完直线冲 {@code charge_distance} 格，路上撞到的技能目标各挨一下
 *       {@code charge_damage} + 目标最大生命 × {@code charge_health_percent}（{@code aberrant_strike}：无视护甲，保护附魔、抗性照常减）并被撞飞。
 *       往旁边让开就躲得掉。冲的时候被墙 / 台阶挡住就眩晕自己 {@code stun_ticks} 刻，这段时间挨打 ×{@code stun_damage_multiplier}。</li>
 *   <li><b>狂暴</b>：生命剩到 {@code frenzy_health} 以下时移速 +{@code frenzy_speed}；地震天灾里出生的一出来就狂暴，攻击另加 {@code quake_attack_bonus}
 *       （存档键 {@value #TAG_QUAKE}，修饰符是固定 UUID 的临时修饰符，不进存档，读档按标记重新挂，不会叠）。</li>
 *   <li><b>分裂</b>：野外刷出来的（来源公平）普通个体死时裂出 {@code split_count} 只畸变幼体（{@link ChaosLarvaEntity}：来源「召唤」、不存盘、不再分裂、
 *       {@code larva_lifetime} 刻后散掉、什么都不掉、不给经验）。刷怪笼 / 刷怪蛋来的不裂。</li>
 *   <li><b>精英「畸变母巢」</b>：死时不裂，改成打起来以后每 {@code brood_interval} 刻产一只幼体（它产的、还活着的最多 {@code brood_max} 只）；
 *       冲撞停下的地方留一滩 {@code residue_ticks} 刻的混沌残液（只是粒子 + 范围效果，不改方块），技能目标站在里面缓慢 II。</li>
 * </ul>
 * 冲撞的状态机在 {@link #tickCharge()}（服务端 {@code customServerAiStep} 每刻推，NoAI 时不推；测试直接调它），状态同步给客户端放对应的循环动画。
 * 生命、护甲、近战伤害在 {@link #finalizeSpawn} 里按配置设成基础值（精英修饰符在那之后由基类挂）。
 */
public class ChaosAberrantEntity extends ECMonster {

    /** 动画段：蓄力、冲撞、眩晕（三段跟着同步的状态循环播）、咬、产卵 */
    public static final int ANIM_WINDUP = 0;
    public static final int ANIM_DASH = 1;
    public static final int ANIM_STUN = 2;
    public static final int ANIM_BITE = 3;
    public static final int ANIM_BROOD = 4;

    /** 冲撞速度（格 / 刻） */
    public static final double DASH_SPEED = 0.6D;
    /** 冲撞时撞人的判定比自己的碰撞箱大多少（格） */
    public static final double DASH_HIT_INFLATE = 0.3D;
    /** 冲撞撞中的击退力度（原版普通近战是 0.4） */
    public static final double CHARGE_KNOCKBACK = 1.4D;
    /** 冲撞只在目标离这么远以外才起（格；贴脸时用近战） */
    public static final double CHARGE_MIN_RANGE = 3D;
    /** 数「自己产的、还活着的幼体」看多大范围 */
    public static final int BROOD_RADIUS = 32;
    /** 一只母巢同时留着的残液最多几滩 */
    public static final int MAX_PUDDLES = 3;
    /** 残液的半径（格） */
    public static final double PUDDLE_RADIUS = 1.5D;

    /** 冲撞路线 / 残液的颜色（和混沌之地的暗红水一个色系） */
    public static final Vector3f CHAOS_RED = new Vector3f(0.62F, 0.08F, 0.12F);

    public static final UUID FRENZY_SPEED_ID = UUID.fromString("1f4d8c3e-7a52-4c0e-9d13-0d0a13d20001");
    public static final UUID QUAKE_ATTACK_ID = UUID.fromString("1f4d8c3e-7a52-4c0e-9d13-0d0a13d20002");
    private static final String FRENZY_NAME = "essentialcraft5 aberrant frenzy";

    public static final String TAG_QUAKE = "quake_frenzy";
    private static final String TAG_CHARGE_COOLDOWN = "charge_cooldown";
    private static final String TAG_BROOD_COOLDOWN = "brood_cooldown";

    /** 冲撞的四个阶段 */
    public enum ChargeState { IDLE, WINDUP, DASH, STUNNED }

    private static final EntityDataAccessor<Byte> DATA_CHARGE = SynchedEntityData.defineId(ChaosAberrantEntity.class, EntityDataSerializers.BYTE);

    public final MobAnimStates anims = new MobAnimStates(5).channel(ANIM_WINDUP, 1).channel(ANIM_DASH, 1).channel(ANIM_STUN, 1)
            .channel(ANIM_BITE, 2).channel(ANIM_BROOD, 2);

    private int chargeTimer;
    private int chargeCooldown = 60;
    private double chargeTravelled;
    private Vec3 chargeDir = Vec3.ZERO;
    private final Set<UUID> chargeHit = new HashSet<>();
    private int broodCooldown;
    private boolean quakeFrenzy;
    private boolean split;
    private final List<Puddle> puddles = new ArrayList<>(MAX_PUDDLES);

    /** 一滩残液：中心和到期的刻（按这只的 tickCount） */
    private record Puddle(Vec3 center, int expiresAt) {
    }

    public ChaosAberrantEntity(EntityType<? extends ChaosAberrantEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    /** 属性默认值（生命 / 护甲照 batch13.md §5.2，其余照怪物稿数值表；生成时再按配置设） */
    public static AttributeSupplier.Builder createAttributes() {
        AberrantValues d = ECHoannaNativesB.Config.ABERRANT_DEFAULTS;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, d.maxHealth())
                .add(Attributes.ARMOR, d.armor())
                .add(Attributes.ATTACK_DAMAGE, d.attackDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    /** 自然生成的判定：原版怪物判定（夜里 / 暗处、非和平、脚下能站）；附近数量上限在注册时另包一层 */
    public static boolean checkAberrantSpawnRules(EntityType<ChaosAberrantEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                                  RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CHARGE, (byte) ChargeState.IDLE.ordinal());
    }

    // ================================================================== 生成、狂暴

    /** 生命、护甲、近战伤害按配置设成基础值、回满（生成时调；精英修饰符在这之后挂） */
    public void applyConfiguredAttributes() {
        AberrantValues cfg = ECHoannaNativesB.Config.aberrant();
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
        if (HoannaEvents.isActive(level.getLevel(), HoannaEvent.EARTHQUAKE)) {
            quakeFrenzy = true;
        }
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        refreshFrenzy();
        return data;
    }

    /** 是不是地震里出生的（一出来就狂暴） */
    public boolean isQuakeFrenzied() {
        return quakeFrenzy;
    }

    /** 现在狂暴着没有：地震里出生的一直狂暴；别的生命剩到 {@code frenzy_health} 以下才狂暴 */
    public boolean isFrenzied() {
        if (quakeFrenzy) {
            return true;
        }
        double threshold = ECHoannaNativesB.Config.aberrant().frenzyHealth();
        return threshold > 0D && getHealth() <= getMaxHealth() * threshold;
    }

    /** 按现在的状态挂 / 摘两个狂暴修饰符（固定 UUID 的临时修饰符，不进存档；数值变了才动） */
    public void refreshFrenzy() {
        AberrantValues cfg = ECHoannaNativesB.Config.aberrant();
        syncModifier(Attributes.MOVEMENT_SPEED, FRENZY_SPEED_ID, isFrenzied() ? cfg.frenzySpeed() : 0D);
        syncModifier(Attributes.ATTACK_DAMAGE, QUAKE_ATTACK_ID, quakeFrenzy ? cfg.quakeAttack() : 0D);
    }

    private void syncModifier(Attribute attribute, UUID id, double amount) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && current.getAmount() == amount) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        if (amount != 0D) {
            instance.addTransientModifier(new AttributeModifier(id, FRENZY_NAME, amount, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    // ================================================================== AI

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ChargeGoal());
        goalSelector.addGoal(2, new BiteGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        tickCharge();
        tickBrood();
        tickPuddles();
        refreshFrenzy();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            ChargeState state = getChargeState();
            anims.animateWhen(ANIM_WINDUP, state == ChargeState.WINDUP, tickCount);
            anims.animateWhen(ANIM_DASH, state == ChargeState.DASH, tickCount);
            anims.animateWhen(ANIM_STUN, state == ChargeState.STUNNED, tickCount);
            if (random.nextInt(state == ChargeState.IDLE ? 6 : 2) == 0) {
                // 背上裂缝冒出来的暗红碎屑
                level().addParticle(new DustParticleOptions(CHAOS_RED, 0.8F), getRandomX(0.4D), getY(0.85D), getRandomZ(0.4D), 0D, 0.02D, 0D);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            anims.play(this, ANIM_BITE);
        }
        return hit;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }

    // ================================================================== 冲撞

    public ChargeState getChargeState() {
        ChargeState[] all = ChargeState.values();
        return all[Math.floorMod(entityData.get(DATA_CHARGE), all.length)];
    }

    private void setChargeState(ChargeState state) {
        entityData.set(DATA_CHARGE, (byte) state.ordinal());
    }

    /** 没在冲撞的任何一个阶段 */
    public boolean isChargeIdle() {
        return getChargeState() == ChargeState.IDLE;
    }

    /** 冲撞方向（单位水平向量；没在冲撞时是上一次的） */
    public Vec3 chargeDirection() {
        return chargeDir;
    }

    /** 冲撞冷却还剩几刻 */
    public int chargeCooldown() {
        return chargeCooldown;
    }

    /** 朝 target 起一次冲撞（方向这时定死）；没在冲撞里才起得来 */
    public boolean startCharge(LivingEntity target) {
        Vec3 dir = new Vec3(target.getX() - getX(), 0D, target.getZ() - getZ());
        return startCharge(dir);
    }

    /** 沿水平方向 dir 起一次冲撞（测试、展示厅也用）：进入蓄力，冷却从这时开始算 */
    public boolean startCharge(Vec3 dir) {
        if (!isChargeIdle() || level().isClientSide) {
            return false;
        }
        Vec3 flat = new Vec3(dir.x, 0D, dir.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            flat = Vec3.directionFromRotation(0F, getYRot()).multiply(1D, 0D, 1D);
        }
        AberrantValues cfg = ECHoannaNativesB.Config.aberrant();
        chargeDir = flat.normalize();
        chargeTimer = cfg.chargeWindup();
        chargeCooldown = cfg.chargeCooldown();
        chargeHit.clear();
        face(chargeDir);
        setChargeState(ChargeState.WINDUP);
        getNavigation().stop();
        playSound(ECHoannaNativesB.SOUND_ABERRANT_WINDUP.get(), 1F, 1F);
        return true;
    }

    /** 冲撞状态机推一刻（服务端；见类注释） */
    public void tickCharge() {
        if (level().isClientSide) {
            return;
        }
        if (chargeCooldown > 0) {
            chargeCooldown--;
        }
        AberrantValues cfg = ECHoannaNativesB.Config.aberrant();
        switch (getChargeState()) {
            case WINDUP -> {
                holdStill();
                face(chargeDir);
                if (chargeTimer % 4 == 0) {
                    showPath(cfg.chargeDistance());
                }
                if (--chargeTimer <= 0) {
                    chargeTravelled = 0D;
                    chargeTimer = Mth.ceil(cfg.chargeDistance() / DASH_SPEED) + 4;
                    setChargeState(ChargeState.DASH);
                    playSound(ECHoannaNativesB.SOUND_ABERRANT_CHARGE.get(), 1.2F, 1F);
                }
            }
            case DASH -> dashStep(cfg);
            case STUNNED -> {
                holdStill();
                if (chargeTimer % 5 == 0 && level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() + 0.2D, getZ(), 3, 0.3D, 0.1D, 0.3D, 0D);
                }
                if (--chargeTimer <= 0) {
                    setChargeState(ChargeState.IDLE);
                }
            }
            default -> {
            }
        }
    }

    private void dashStep(AberrantValues cfg) {
        getNavigation().stop();
        face(chargeDir);
        Vec3 before = position();
        move(MoverType.SELF, new Vec3(chargeDir.x * DASH_SPEED, 0D, chargeDir.z * DASH_SPEED));
        setDeltaMovement(0D, getDeltaMovement().y, 0D);
        double moved = Math.sqrt(Mth.square(getX() - before.x) + Mth.square(getZ() - before.z));
        chargeTravelled += moved;
        for (LivingEntity target : skillTargets(getBoundingBox().inflate(DASH_HIT_INFLATE))) {
            if (chargeHit.add(target.getUUID())) {
                strike(target);
            }
        }
        if (horizontalCollision && moved < DASH_SPEED * 0.5D) {
            hitWall(cfg);
            return;
        }
        if (chargeTravelled >= cfg.chargeDistance() - 1.0E-3D || --chargeTimer <= 0) {
            endDash(cfg);
        }
    }

    /**
     * 冲撞撞中 target：{@code charge_damage} + 目标最大生命 × {@code charge_health_percent}（{@code aberrant_strike}），打实了沿冲撞方向撞飞。
     * 返回打实了没有。
     */
    public boolean strike(LivingEntity target) {
        AberrantValues cfg = ECHoannaNativesB.Config.aberrant();
        float damage = chargeDamage(cfg, target);
        if (!(damage > 0F) || !target.hurt(ECDamageTypes.aberrantStrike(this), damage)) {
            return false;
        }
        Vec3 dir = chargeDir.lengthSqr() > 1.0E-6D ? chargeDir : target.position().subtract(position()).multiply(1D, 0D, 1D).normalize();
        target.knockback(CHARGE_KNOCKBACK, -dir.x, -dir.z);
        target.push(0D, 0.25D, 0D);
        target.hurtMarked = true;
        return true;
    }

    /** 冲撞对 target 的伤害（固定 + 目标最大生命 × 比例） */
    public static float chargeDamage(AberrantValues cfg, LivingEntity target) {
        return (float) (cfg.chargeDamage() + target.getMaxHealth() * cfg.chargeHealthPercent());
    }

    private void hitWall(AberrantValues cfg) {
        leaveResidue(cfg);
        if (cfg.stunTicks() > 0) {
            chargeTimer = cfg.stunTicks();
            setChargeState(ChargeState.STUNNED);
            playSound(ECHoannaNativesB.SOUND_ABERRANT_STUNNED.get(), 1F, 1F);
        } else {
            setChargeState(ChargeState.IDLE);
        }
    }

    private void endDash(AberrantValues cfg) {
        leaveResidue(cfg);
        setChargeState(ChargeState.IDLE);
    }

    private void holdStill() {
        getNavigation().stop();
        setDeltaMovement(0D, getDeltaMovement().y, 0D);
    }

    private void face(Vec3 dir) {
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
    }

    /** 预警：沿定死的方向在地上冒一排暗红的尘，一直画到冲撞的终点 */
    private void showPath(double distance) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(CHAOS_RED, 1.2F);
        for (double d = 1D; d <= distance; d += 1D) {
            server.sendParticles(dust, getX() + chargeDir.x * d, getY() + 0.1D, getZ() + chargeDir.z * d, 2, 0.2D, 0.02D, 0.2D, 0D);
        }
        server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.1D, getZ(), 3, 0.4D, 0.02D, 0.4D, 0.01D);
    }

    /** 眩晕时挨打更疼 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (getChargeState() == ChargeState.STUNNED) {
            amount *= (float) ECHoannaNativesB.Config.aberrant().stunDamageTaken();
        }
        return super.hurt(source, amount);
    }

    // ================================================================== 幼体

    /**
     * 在身边放 count 只畸变幼体（服务端）：来源先记成「召唤」再走 Forge 的生成事件，被取消的不算；幼体记着母体 UUID，盯着 target。
     * 返回放成的那几只。
     */
    public List<ChaosLarvaEntity> spawnLarvae(int count, @Nullable LivingEntity target) {
        List<ChaosLarvaEntity> out = new ArrayList<>(Math.max(0, count));
        if (!(level() instanceof ServerLevel server) || count <= 0) {
            return out;
        }
        int lifetime = ECHoannaNativesB.Config.aberrant().larvaLifetime();
        for (int i = 0; i < count; i++) {
            ChaosLarvaEntity larva = ECHoannaNativesB.CHAOS_LARVA.get().create(server);
            if (larva == null) {
                break;
            }
            Vec3 at = larvaSpot(larva);
            larva.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0F);
            larva.setMother(getUUID());
            larva.setLifetime(lifetime);
            SpawnOrigin.set(larva, Origin.SUMMONED);
            ForgeEventFactory.onFinalizeSpawn(larva, server, server.getCurrentDifficultyAt(larva.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            if (larva.isSpawnCancelled()) {
                continue;
            }
            if (target != null && target.isAlive()) {
                larva.setTarget(target);
            }
            if (server.addFreshEntity(larva)) {
                out.add(larva);
                server.sendParticles(new DustParticleOptions(CHAOS_RED, 1F), larva.getX(), larva.getY(0.5D), larva.getZ(), 6, 0.2D, 0.2D, 0.2D, 0D);
            }
        }
        if (!out.isEmpty()) {
            playSound(ECHoannaNativesB.SOUND_ABERRANT_SPLIT.get(), 1F, 1F);
        }
        return out;
    }

    /** 幼体放在哪：身边 1 格内放得下它的点；都放不下就放在自己脚下 */
    private Vec3 larvaSpot(ChaosLarvaEntity larva) {
        for (int i = 0; i < 6; i++) {
            Vec3 at = new Vec3(getX() + (random.nextDouble() - 0.5D) * 2D, getY(), getZ() + (random.nextDouble() - 0.5D) * 2D);
            AABB box = larva.getType().getDimensions().makeBoundingBox(at);
            if (level().noCollision(larva, box) && !level().containsAnyLiquid(box)) {
                return at;
            }
        }
        return position();
    }

    /** {@value #BROOD_RADIUS} 格以内它产的、还活着的幼体有几只 */
    public int broodCount() {
        UUID self = getUUID();
        return level().getEntitiesOfClass(ChaosLarvaEntity.class, getBoundingBox().inflate(BROOD_RADIUS),
                larva -> larva.isAlive() && self.equals(larva.mother())).size();
    }

    /** 母巢产卵：打起来（有目标）以后按冷却产一只，自己产的活着的够数就不产。返回这次产了没有（测试也直接调） */
    public boolean tickBrood() {
        if (!isElite() || level().isClientSide) {
            return false;
        }
        AberrantValues cfg = ECHoannaNativesB.Config.aberrant();
        if (cfg.broodInterval() <= 0 || cfg.broodMax() <= 0) {
            return false;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (broodCooldown > 0) {
            broodCooldown--;
            return false;
        }
        broodCooldown = cfg.broodInterval();
        if (broodCount() < cfg.broodMax() && !spawnLarvae(1, target).isEmpty()) {
            anims.play(this, ANIM_BROOD);
            return true;
        }
        return false;
    }

    /** 死时裂开：只有野外刷出来的普通个体才裂（母巢、刷怪笼 / 刷怪蛋 / 召唤来的都不裂）；同一只只裂一次 */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!dead || split || level().isClientSide) {
            return;
        }
        split = true;
        if (splitsOnDeath()) {
            LivingEntity killer = source.getEntity() instanceof LivingEntity living ? living : getTarget();
            spawnLarvae(ECHoannaNativesB.Config.aberrant().splitCount(), killer);
        }
    }

    /** 这只死了会不会裂：不是精英、来源公平 */
    public boolean splitsOnDeath() {
        return !isElite() && SpawnOrigin.isFair(this);
    }

    // ================================================================== 残液

    private void leaveResidue(AberrantValues cfg) {
        if (!isElite() || cfg.residueTicks() <= 0) {
            return;
        }
        if (puddles.size() >= MAX_PUDDLES) {
            puddles.remove(0);
        }
        puddles.add(new Puddle(position(), tickCount + cfg.residueTicks()));
    }

    /** 残液现在有几滩（测试用） */
    public int puddleCount() {
        return puddles.size();
    }

    /** 每刻：过期的收掉；站在里面的技能目标缓慢 II；每 4 刻冒一次粒子（测试也直接调） */
    public void tickPuddles() {
        if (puddles.isEmpty() || !(level() instanceof ServerLevel server)) {
            return;
        }
        puddles.removeIf(puddle -> tickCount >= puddle.expiresAt());
        DustParticleOptions dust = new DustParticleOptions(CHAOS_RED, 1.4F);
        for (Puddle puddle : puddles) {
            Vec3 c = puddle.center();
            AABB box = new AABB(c.x - PUDDLE_RADIUS, c.y - 0.5D, c.z - PUDDLE_RADIUS, c.x + PUDDLE_RADIUS, c.y + 1D, c.z + PUDDLE_RADIUS);
            for (LivingEntity target : skillTargets(box)) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), this);
            }
            if (tickCount % 4 == 0) {
                server.sendParticles(dust, c.x, c.y + 0.05D, c.z, 4, PUDDLE_RADIUS * 0.5D, 0.01D, PUDDLE_RADIUS * 0.5D, 0D);
            }
        }
    }

    // ================================================================== 存档、声音

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_QUAKE, quakeFrenzy);
        tag.putInt(TAG_CHARGE_COOLDOWN, chargeCooldown);
        tag.putInt(TAG_BROOD_COOLDOWN, broodCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        quakeFrenzy = tag.getBoolean(TAG_QUAKE);
        chargeCooldown = Math.max(0, tag.getInt(TAG_CHARGE_COOLDOWN));
        broodCooldown = Math.max(0, tag.getInt(TAG_BROOD_COOLDOWN));
        refreshFrenzy();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ECHoannaNativesB.SOUND_ABERRANT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECHoannaNativesB.SOUND_ABERRANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECHoannaNativesB.SOUND_ABERRANT_DEATH.get();
    }

    // ================================================================== 目标

    /** 冲撞：只管起冲撞、占着移动 / 转头，状态机在实体上推 */
    private final class ChargeGoal extends Goal {

        ChargeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            AberrantValues cfg = ECHoannaNativesB.Config.aberrant();
            LivingEntity target = getTarget();
            if (cfg.chargeCooldown() <= 0 || chargeCooldown > 0 || !isChargeIdle() || !onGround() || target == null || !isSkillTarget(target)) {
                return false;
            }
            double distSq = distanceToSqr(target);
            double max = cfg.chargeDistance() + 2D;
            return distSq >= CHARGE_MIN_RANGE * CHARGE_MIN_RANGE && distSq <= max * max && getSensing().hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return !isChargeIdle();
        }

        @Override
        public void start() {
            LivingEntity target = getTarget();
            if (target != null) {
                startCharge(target);
            }
        }
    }

    /** 普通近战，冲撞的哪个阶段都不咬 */
    private final class BiteGoal extends MeleeAttackGoal {

        BiteGoal() {
            super(ChaosAberrantEntity.this, 1.0D, true);
        }

        @Override
        public boolean canUse() {
            return isChargeIdle() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return isChargeIdle() && super.canContinueToUse();
        }
    }
}
