package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs.Config.StalkerValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 虚空潜行者 {@code void_stalker}（第十三批 13c-M3，batch13.md §5.2）：末地外岛的怪，只由 {@link VoidStalkerSpawner} 刷。
 * <ul>
 *   <li><b>实体化 / 相位</b>：每轮实体化 {@code solid_ticks}（4 秒，最后 {@code warning_ticks} 0.5 秒是预警：身子一闪一闪、响一声），
 *       然后相位 {@code phase_ticks}（2 秒）。相位时什么伤害都不吃（{@code /kill} 这类无视无敌的除外）、准星和弹射物直接穿过去、推不动，
 *       身子变成半透明、冒反向传送门粒子，跑得快三成，但不出手。相位状态是同步数据，存盘。</li>
 *   <li><b>裂隙爪</b>：近战；冷却好了的那一下打实了（目标的生命或黄心真的少了：盾牌挡下、插件取消都不算）就把目标闪到
 *       {@code blink_min_distance}~{@code blink_max_distance} 格外，落点脚下三格内必须有方块、从目标的眼睛看得通、玩家对落点有权限，
 *       走可取消的传送（{@link VoidStalkerRift}）；一个合格的落点都没有就不闪，冷却照样挂上。</li>
 *   <li><b>精英「裂隙行者」</b>：目标在 5~{@code pull_range} 格、看得见时，先在目标脚下撕开裂隙（{@code pull_warning_ticks}，粒子 + 声音 + 动作栏提示），
 *       到点时目标还在视线里就把他拉到自己跟前（落点同样要从潜行者的眼睛看得通、玩家有权限，走可取消的传送）；躲到它看不见的地方就拉不动。
 *       冷却 {@code pull_cooldown}。</li>
 * </ul>
 * 闪人、拉人都只对技能目标（生存 / 冒险玩家和自己当前的目标，{@link #isSkillTarget}）。生命、护甲、爪击在 {@link #finalizeSpawn} 里按配置设成基础值。
 * 不怕光（{@link #getWalkTargetValue} 恒 0：末地没有天光，挑地方不用看亮度）；自然生成只认方块光不超过维度上限的地方（{@link #checkStalkerSpawnRules}）。
 */
public class VoidStalkerEntity extends ECMonster {

    public static final byte SOLID = 0;
    public static final byte WARNING = 1;
    public static final byte PHASED = 2;

    public static final int ANIM_CLAW = 0;
    public static final int ANIM_RIFT_CLAW = 1;
    public static final int ANIM_PHASE = 2;
    public static final int ANIM_PULL = 3;

    /** 相位时的移动速度加成（倍数 +0.3），临时修饰符，不存盘 */
    public static final UUID PHASE_SPEED_ID = UUID.fromString("5d0b8c0e-6f8e-4a51-9c3a-2b13c0a3f001");
    public static final double PHASE_SPEED_BONUS = 0.3D;
    /** 相位时画面的不透明度 */
    public static final float PHASED_ALPHA = 0.3F;
    /** 裂隙行者拉人：目标至少离多远才拉（贴身了就直接打） */
    public static final double PULL_MIN_DISTANCE = 5D;

    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(VoidStalkerEntity.class, EntityDataSerializers.BYTE);
    private static final String TAG_PHASE = "phase";
    private static final String TAG_PHASE_TICKS = "phase_ticks";
    private static final String TAG_RIFT_COOLDOWN = "rift_claw_cooldown";
    private static final String TAG_PULL_COOLDOWN = "pull_cooldown";

    public final MobAnimStates anims = new MobAnimStates(4).channel(ANIM_CLAW, 1).channel(ANIM_RIFT_CLAW, 1).channel(ANIM_PULL, 1);

    private int phaseTimer;
    private int riftClawCooldown;
    private int pullCooldown;
    /** 客户端：相位上一次变化在哪一刻、变化前是哪一相（算淡入淡出） */
    private int phaseChangedAt = -100;
    private byte previousPhase = SOLID;
    private byte lastSeenPhase = SOLID;

    public VoidStalkerEntity(EntityType<? extends VoidStalkerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
        // 一出生先实体化一会儿，几只同时刷出来时也不一起闪
        this.phaseTimer = 30 + level.random.nextInt(40);
    }

    public static AttributeSupplier.Builder createAttributes() {
        StalkerValues defaults = ECNetherEndMobs.Config.STALKER_DEFAULTS;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, defaults.maxHealth())
                .add(Attributes.ARMOR, defaults.armor())
                .add(Attributes.ATTACK_DAMAGE, defaults.attackDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D);
    }

    /** 生成器刷之前查的这一份：不是和平、方块光不超过这个维度的怪物上限（末地是 0，火把、末地烛照着的地方不刷）、脚下能站怪 */
    public static boolean checkStalkerSpawnRules(EntityType<VoidStalkerEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                                 RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL
                && level.getBrightness(LightLayer.BLOCK, pos) <= level.dimensionType().monsterSpawnBlockLightLimit()
                && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PHASE, SOLID);
    }

    // ================================================================== 生成

    /** 生命、护甲、爪击按配置设成基础值、回满（生成时调；精英修饰符在这之后挂） */
    public void applyConfiguredAttributes() {
        StalkerValues cfg = ECNetherEndMobs.Config.stalker();
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

    /** 末地没有天光：挑地方、自然生成都不看亮度 */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0F;
    }

    // ================================================================== 相位

    public byte phase() {
        return entityData.get(DATA_PHASE);
    }

    public boolean isPhased() {
        return phase() == PHASED;
    }

    /** 这一相还剩几刻 */
    public int phaseTimer() {
        return phaseTimer;
    }

    /** 设到某一相、这一相还剩几刻（服务端；GameTest、展示厅也用）。换了相才放声音、粒子、改速度 */
    public void setPhase(byte phase, int ticks) {
        byte old = phase();
        entityData.set(DATA_PHASE, phase);
        phaseTimer = Math.max(0, ticks);
        if (old != phase && !level().isClientSide) {
            onPhaseChanged(old, phase);
        }
    }

    private void onPhaseChanged(byte from, byte to) {
        applyPhaseSpeed(to == PHASED);
        if (to == WARNING) {
            playSound(ECNetherEndMobs.SOUND_STALKER_PHASE_WARN.get(), 0.8F, 1F);
            anims.play(this, ANIM_PHASE);
        } else if (to == PHASED) {
            playSound(ECNetherEndMobs.SOUND_STALKER_PHASE_OUT.get(), 0.8F, 1F);
            burst(ParticleTypes.REVERSE_PORTAL, position(), 20);
        } else if (from == PHASED) {
            playSound(ECNetherEndMobs.SOUND_STALKER_PHASE_IN.get(), 0.8F, 1F);
            burst(ParticleTypes.PORTAL, position(), 20);
        }
    }

    private void applyPhaseSpeed(boolean phased) {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(PHASE_SPEED_ID);
        if (phased) {
            speed.addTransientModifier(new AttributeModifier(PHASE_SPEED_ID, "essentialcraft5 void stalker phase", PHASE_SPEED_BONUS,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    /**
     * 推一刻相位（服务端 AI 里每刻调）：实体化（{@code solid_ticks − warning_ticks}）→ 预警（{@code warning_ticks}）→ 相位（{@code phase_ticks}）→ 实体化……
     * {@code phase_ticks} 填 0 时一直实体化。
     */
    public void tickPhase() {
        StalkerValues cfg = ECNetherEndMobs.Config.stalker();
        if (cfg.phaseTicks() <= 0) {
            if (phase() != SOLID) {
                setPhase(SOLID, 0);
            }
            return;
        }
        if (phaseTimer > 0 && --phaseTimer > 0) {
            return;
        }
        int warning = Math.max(0, Math.min(cfg.warningTicks(), cfg.solidTicks() - 1));
        byte now = phase();
        if (now == SOLID) {
            if (warning > 0) {
                setPhase(WARNING, warning);
            } else {
                setPhase(PHASED, cfg.phaseTicks());
            }
        } else if (now == WARNING) {
            setPhase(PHASED, cfg.phaseTicks());
        } else {
            setPhase(SOLID, cfg.solidTicks() - warning);
        }
    }

    /** 相位时什么都不吃，只认无视无敌的（{@code /kill}、虚空） */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return (isPhased() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) || super.isInvulnerableTo(source);
    }

    /** 相位时准星、弹射物都穿过去 */
    @Override
    public boolean isPickable() {
        return !isPhased() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return !isPhased() && super.isPushable();
    }

    /** 画面的不透明度（客户端渲染用）：预警时一闪一闪，进出相位各用 4 刻淡过去 */
    public float phaseAlpha(float partialTick) {
        float age = tickCount + partialTick - phaseChangedAt;
        byte now = phase();
        if (now == WARNING) {
            return 0.65F + 0.35F * Mth.abs(Mth.cos(age * 0.9F));
        }
        if (now == PHASED) {
            return Math.max(PHASED_ALPHA, 1F - age / 4F * (1F - PHASED_ALPHA));
        }
        if (previousPhase == PHASED) {
            return Math.min(1F, PHASED_ALPHA + age / 4F * (1F - PHASED_ALPHA));
        }
        return 1F;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PHASE.equals(key) && level().isClientSide) {
            byte now = phase();
            if (now != lastSeenPhase) {
                previousPhase = lastSeenPhase;
                lastSeenPhase = now;
                phaseChangedAt = tickCount;
            }
        }
    }

    // ================================================================== 出手

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new PullGoal());
        goalSelector.addGoal(3, new StalkerMeleeGoal());
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        tickPhase();
        if (riftClawCooldown > 0) {
            riftClawCooldown--;
        }
        if (pullCooldown > 0) {
            pullCooldown--;
        }
    }

    /** 相位时不出手；冷却好了的那一下是裂隙爪，打实了再把人闪走 */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (isPhased()) {
            return false;
        }
        StalkerValues cfg = ECNetherEndMobs.Config.stalker();
        LivingEntity living = target instanceof LivingEntity l ? l : null;
        boolean rift = living != null && cfg.riftClawCooldown() > 0 && riftClawCooldown <= 0 && isSkillTarget(living);
        anims.play(this, rift ? ANIM_RIFT_CLAW : ANIM_CLAW);
        float before = living == null ? 0F : living.getHealth() + living.getAbsorptionAmount();
        boolean hit = super.doHurtTarget(target);
        if (rift && hit && living.isAlive() && living.getHealth() + living.getAbsorptionAmount() < before) {
            riftClawCooldown = cfg.riftClawCooldown();
            riftClaw(living);
        }
        return hit;
    }

    /** 裂隙爪的后半：把 target 闪到附近合格的落点（见类注释）；返回闪成没有 */
    public boolean riftClaw(LivingEntity target) {
        if (!(level() instanceof ServerLevel server) || !isSkillTarget(target)) {
            return false;
        }
        StalkerValues cfg = ECNetherEndMobs.Config.stalker();
        Vec3 spot = VoidStalkerRift.blinkSpot(server, target, random, cfg.blinkMin(), cfg.blinkMax(), this);
        if (spot == null) {
            return false;
        }
        Vec3 from = target.position();
        if (!VoidStalkerRift.displace(target, spot)) {
            return false;
        }
        burst(ParticleTypes.PORTAL, from, 24);
        burst(ParticleTypes.REVERSE_PORTAL, spot, 24);
        server.playSound(null, spot.x, spot.y, spot.z, ECNetherEndMobs.SOUND_STALKER_RIFT_CLAW.get(), SoundSource.HOSTILE, 1F, 1F);
        return true;
    }

    /** 裂隙行者能不能对 target 起手拉人：精英、实体化且这一相还剩得够、冷却好了、目标在 5~pull_range 格、看得见 */
    public boolean canStartPull(@Nullable LivingEntity target) {
        StalkerValues cfg = ECNetherEndMobs.Config.stalker();
        if (!isElite() || target == null || cfg.pullCooldown() <= 0 || pullCooldown > 0 || phase() != SOLID
                || phaseTimer <= cfg.pullWarning() + 2 || !isSkillTarget(target)) {
            return false;
        }
        double dist = distanceToSqr(target);
        double range = cfg.pullRange();
        return dist >= PULL_MIN_DISTANCE * PULL_MIN_DISTANCE && dist <= range * range && hasLineOfSight(target);
    }

    /**
     * 拉人的后半：目标还活着、是技能目标、在 {@code pull_range} + 4 格内、还看得见，就把他传到自己跟前（{@link VoidStalkerRift#pullSpot}）。
     * 相位时、落点找不到、传送被取消都返回 false，什么都不变。
     */
    public boolean pull(LivingEntity target) {
        if (!(level() instanceof ServerLevel server) || isPhased() || !target.isAlive() || !isSkillTarget(target) || !hasLineOfSight(target)) {
            return false;
        }
        double range = ECNetherEndMobs.Config.stalker().pullRange() + 4D;
        if (distanceToSqr(target) > range * range) {
            return false;
        }
        Vec3 spot = VoidStalkerRift.pullSpot(server, this, target);
        if (spot == null) {
            return false;
        }
        Vec3 from = target.position();
        if (!VoidStalkerRift.displace(target, spot)) {
            return false;
        }
        burst(ParticleTypes.REVERSE_PORTAL, from, 24);
        burst(ParticleTypes.PORTAL, spot, 24);
        server.playSound(null, spot.x, spot.y, spot.z, ECNetherEndMobs.SOUND_STALKER_RIFT_PULL.get(), SoundSource.HOSTILE, 1F, 1F);
        return true;
    }

    private void burst(ParticleOptions particle, Vec3 at, int count) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(particle, at.x, at.y + 1D, at.z, count, 0.3D, 0.6D, 0.3D, 0.2D);
        }
    }

    // ================================================================== 杂项

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            byte now = phase();
            if (now == PHASED) {
                for (int i = 0; i < 2; i++) {
                    level().addParticle(ParticleTypes.REVERSE_PORTAL, getRandomX(0.5D), getRandomY(), getRandomZ(0.5D), 0D, 0.02D, 0D);
                }
            } else if (now == WARNING || (isElite() && random.nextInt(3) == 0)) {
                level().addParticle(ParticleTypes.PORTAL, getRandomX(0.5D), getRandomY() - 0.25D, getRandomZ(0.5D),
                        (random.nextDouble() - 0.5D) * 2D, -random.nextDouble(), (random.nextDouble() - 0.5D) * 2D);
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
    protected SoundEvent getAmbientSound() {
        return ECNetherEndMobs.SOUND_STALKER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECNetherEndMobs.SOUND_STALKER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECNetherEndMobs.SOUND_STALKER_DEATH.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte(TAG_PHASE, phase());
        tag.putInt(TAG_PHASE_TICKS, phaseTimer);
        tag.putInt(TAG_RIFT_COOLDOWN, riftClawCooldown);
        tag.putInt(TAG_PULL_COOLDOWN, pullCooldown);
    }

    /** 读档：相位原样接上（相位时的加速是临时修饰符、没存，这里补挂），不放声音 */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        byte phase = tag.getByte(TAG_PHASE);
        if (phase < SOLID || phase > PHASED) {
            phase = SOLID;
        }
        entityData.set(DATA_PHASE, phase);
        applyPhaseSpeed(phase == PHASED);
        if (tag.contains(TAG_PHASE_TICKS)) {
            phaseTimer = Math.max(0, tag.getInt(TAG_PHASE_TICKS));
        }
        riftClawCooldown = Math.max(0, tag.getInt(TAG_RIFT_COOLDOWN));
        pullCooldown = Math.max(0, tag.getInt(TAG_PULL_COOLDOWN));
    }

    /** 测试、展示厅：直接设冷却 */
    public void setCooldowns(int riftClaw, int pull) {
        this.riftClawCooldown = Math.max(0, riftClaw);
        this.pullCooldown = Math.max(0, pull);
    }

    public int riftClawCooldown() {
        return riftClawCooldown;
    }

    public int pullCooldown() {
        return pullCooldown;
    }

    // ================================================================== 目标

    /** 相位时只追不打 */
    private final class StalkerMeleeGoal extends MeleeAttackGoal {
        StalkerMeleeGoal() {
            super(VoidStalkerEntity.this, 1D, true);
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distanceSq) {
            if (!isPhased()) {
                super.checkAndPerformAttack(target, distanceSq);
            }
        }
    }

    /** 裂隙行者拉人：起手站定、在目标脚下撕开裂隙，到点时目标还在视线里就拉过来（见类注释） */
    private final class PullGoal extends Goal {
        private int warning;
        @Nullable
        private LivingEntity target;

        PullGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return canStartPull(getTarget());
        }

        @Override
        public boolean canContinueToUse() {
            return warning > 0 && target != null && target.isAlive() && !isPhased();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            StalkerValues cfg = ECNetherEndMobs.Config.stalker();
            target = getTarget();
            warning = cfg.pullWarning();
            pullCooldown = cfg.pullCooldown();
            getNavigation().stop();
            anims.play(VoidStalkerEntity.this, ANIM_PULL);
            if (target != null) {
                level().playSound(null, target.getX(), target.getY(), target.getZ(), ECNetherEndMobs.SOUND_STALKER_RIFT_OPEN.get(),
                        SoundSource.HOSTILE, 1F, 1F);
                if (target instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable("message.essentialcraft5.void_stalker.rift_open"), true);
                }
            }
        }

        @Override
        public void tick() {
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target, 30F, 30F);
            if (warning % 3 == 0 && level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 0.1D, target.getZ(), 6, 0.4D, 0.05D, 0.4D, 0.02D);
            }
            if (--warning == 0) {
                pull(target);
            }
        }

        @Override
        public void stop() {
            warning = 0;
            target = null;
        }
    }
}
