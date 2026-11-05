package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemTrigger;
import com.flameforge.essentialcraft5.entity.mob.hoanna.HoannaScavengerEntity;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECTownSentry;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 废城守望者 {@code town_sentry}（第十三批 13e-T，batch13.md §5.3.3、MS §2.5.2）：守着海纳尔城镇废墟的小 Boss。由 {@link TownSentryTrigger}
 * 在城镇起点（十字路口）附近放出来；刷怪蛋 / 指令也能放（这样放的不守哪座城，来源不公平，不掉关键材料）。
 * 外形是放大 1.25 倍的聚能魔像，肩上多了两块挂炮台的肩甲。
 *
 * <h2>守着的城</h2>
 * 记着城镇（{@link TownGuardians.Town}：维度 + 起点区块）和醒来的位置（{@link #home()}）。离 home 水平超过 {@code leash_range}（24）格、
 * 或上下差出 {@value #VERTICAL_REACH} 格就往回走（{@link ReturnHomeGoal}，抢在近战前面）。死了在存档里记这座城「打倒了」，被指令删掉记「放弃了」，
 * 和平难度散去时把这座城改回「还没醒」，难度调回来以后有人走近再醒（照聚能魔像 C3-05）。区块卸载只是存进区块，不改存档。
 *
 * <h2>招式（数值都在 {@code general.town_sentry}）</h2>
 * <ul>
 *   <li><b>横扫</b>（近战，{@code sweep_damage} 13，吃护甲）：打主目标的同时，朝主目标那一侧 {@code sweep_angle}（120°）扇形、{@code sweep_range}（3）格
 *       以内名单里的其他参战者也挨一下（{@link #sweepTargets}）；</li>
 *   <li><b>哨戒炮台</b>（{@link SentryTurretEntity}）：名单里有人时在两侧架起 {@code turret_count}（2）座，被打掉 {@code turret_respawn_ticks}（20 秒）后补一座；
 *       名单空了收回去（{@link #tickTurrets}）。炮台还在时，正面 {@code front_guard_angle}（120°，朝向左右各 60°）以内打来的弹射物被挡下
 *       （{@link #guardsAgainst}，返回 false：箭会弹开）；</li>
 *   <li><b>全城警报</b>：第一次降到 {@code alarm_health_fraction}（50%）以下，敲三下钟、叫来 {@code alarm_count}（2）名遗民拾荒者（来源 {@code SUMMONED}，
 *       不掉关键东西、不掷精英）；只一次，记在存档里，读档后不重复（{@link #soundAlarm}）；</li>
 *   <li><b>镇压</b>（每 {@code suppress_cooldown_ticks}，14 秒）：目标在半径内时站定，{@code suppress_warning_ticks}（1.5 秒）地面红圈预警，落地打圈里名单内的参战者
 *       {@code suppress_damage} + 目标最大生命 × {@code suppress_health_fraction}（10 + 12%，{@code aberrant_strike}，无视护甲）。</li>
 * </ul>
 * 横扫的其他人、炮台、镇压都只打名单里的参战者（{@link #isSkillTarget}）。
 *
 * <h2>小 Boss 规矩</h2>
 * {@link BossTier#MINI}：谁打都算伤害（关键掉落另要 {@code fair_kill}）、只有单次封顶 {@code per_hit_cap}（30）、吃状态效果、有摔落伤害；击退抗性 1。
 * 名单是「守卫」式（{@link BossRoster.Settings#guardian}）：不拽人、不散去、不锁飞行，离守望者水平 {@value #ESCAPE_RANGE} 格外（或上下差出
 * {@value #VERTICAL_REACH} 格）脱战（{@link #beyondEscape}）。同调点照普通怪的每只上限（小 Boss 档，{@code AttunementGains.uncappedBoss}）。
 * 属性在注册时按默认值登记，生成时按当时的配置改基础值（{@link #applyConfiguredStats}）；经验按打倒时的配置给。
 * 掉落 {@code entities/town_sentry}（每个池 {@code killed_by_player} + {@code fair_kill}），每名打出一成以上有效伤害的人一份进背包；
 * 异兽录 {@code guide/bestiary/town_sentry} 由 {@code BossLoot.recordVictory} 发给分到掉落的人。
 */
public class TownSentryEntity extends ECBossMonster {

    public static final int ANIM_SWEEP = 0;
    public static final int ANIM_SUPPRESS = 1;
    public static final int ANIM_ALARM = 2;
    /** 名单脱战距离：离守望者水平这么远就不算参战（上下按 {@link #VERTICAL_REACH}） */
    public static final double ESCAPE_RANGE = 32D;
    /** 上下差多少还算在旁边（城里的塔有二十几格高，打的人可能站在塔上） */
    public static final double VERTICAL_REACH = 16D;
    /** 技能实体的种类名（{@link BossSkillSlots}） */
    public static final String SKILL_TURRET = "turret";
    /** 炮台架在离它多远的两侧 */
    public static final double TURRET_OFFSET = 2.5D;
    /** 名单空了以后多久收回炮台（刻） */
    public static final int TURRET_RECALL_TICKS = 100;
    /** 警报的钟敲几下、隔几刻 */
    public static final int ALARM_BELLS = 3;
    public static final int ALARM_BELL_GAP = 8;
    /** 叫来的拾荒者站在离它多远的地方 */
    private static final double SCAVENGER_MIN_RADIUS = 2D;
    private static final double SCAVENGER_MAX_RADIUS = 4D;

    public static final String MSG_ALARM = "message.essentialcraft5.town_sentry.alarm";

    private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(TownSentryEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String TAG_TOWN_DIMENSION = "town_dimension";
    private static final String TAG_TOWN_CHUNK = "town_chunk";
    private static final String TAG_HOME = "guard_home";
    private static final String TAG_ALARM = "alarm_used";

    /** 红圈的颜色 */
    private static final DustParticleOptions RING = new DustParticleOptions(new Vector3f(0.95F, 0.15F, 0.1F), 1.3F);

    public final MobAnimStates anims = new MobAnimStates(3).channel(ANIM_SWEEP, 1).channel(ANIM_SUPPRESS, 1).channel(ANIM_ALARM, 1);

    @Nullable
    private TownGuardians.Town town;
    @Nullable
    private BlockPos home;
    private boolean alarmUsed;
    private int suppressCooldown;
    /** 这一场炮台架过没有（不存盘：炮台不存盘，读档后有人进名单就重新架） */
    private boolean turretsDeployed;
    /** 下一座炮台什么时候补（tickCount；-1 = 没在等） */
    private int turretRespawnAt = -1;
    /** 名单空了多少刻 */
    private int idleTicks;
    /** 警报还剩几下钟没敲、下一下在什么时候 */
    private int bellsLeft;
    private int nextBell;
    /** 和平难度散去中（存档已经改回「还没醒」，移除时不再记放弃） */
    private boolean peacefulEnd;

    public TownSentryEntity(EntityType<? extends TownSentryEntity> type, Level level) {
        super(type, level, BossTier.MINI, BossEvent.BossBarColor.YELLOW);
        this.xpReward = ECTownSentry.Config.DEFAULTS.stats().xp();
        this.setMaxUpStep(1F);
    }

    /** 注册时的属性（默认值；生成时再按配置改基础值） */
    public static AttributeSupplier.Builder createAttributes() {
        ECTownSentry.Config.Stats stats = ECTownSentry.Config.DEFAULTS.stats();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, stats.maxHealth())
                .add(Attributes.ARMOR, stats.armor())
                .add(Attributes.ARMOR_TOUGHNESS, stats.toughness())
                .add(Attributes.ATTACK_DAMAGE, ECTownSentry.Config.DEFAULTS.attacks().sweepDamage())
                .add(Attributes.ATTACK_KNOCKBACK, 1D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance())
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CHARGING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ReturnHomeGoal());
        goalSelector.addGoal(2, new SuppressGoal());
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1D, true));
        goalSelector.addGoal(6, new HomeStrollGoal());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------ 配置

    private static ECTownSentry.Config.Values config() {
        return ECTownSentry.Config.values();
    }

    /** 按现在的配置设属性基础值、回满血（生成时调；基础值是直接设的，调几次都一样） */
    public void applyConfiguredStats() {
        ECTownSentry.Config.Stats stats = config().stats();
        setBase(Attributes.MAX_HEALTH, stats.maxHealth());
        setBase(Attributes.ARMOR, stats.armor());
        setBase(Attributes.ARMOR_TOUGHNESS, stats.toughness());
        setBase(Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance());
        setBase(Attributes.ATTACK_DAMAGE, config().attacks().sweepDamage());
        xpReward = stats.xp();
        setHealth(getMaxHealth());
    }

    private void setBase(Attribute attribute, double value) {
        var instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public BossDamageBudget.Limits bossLimits() {
        return BossDamageBudget.Limits.of(config().stats().perHitCap(), 0D);
    }

    @Override
    public BossRoster.Settings rosterSettings() {
        return BossRoster.Settings.guardian(ESCAPE_RANGE);
    }

    /** 脱战按水平 {@value #ESCAPE_RANGE} 格、上下 {@value #VERTICAL_REACH} 格量（城里有塔，直线距离会把塔上的人算远） */
    @Override
    public boolean beyondEscape(ServerPlayer player, Vec3 center, double range) {
        return !near(player.position(), center, range);
    }

    /** pos 离 center 水平 ≤ range、上下差 ≤ {@value #VERTICAL_REACH} */
    public static boolean near(Vec3 pos, Vec3 center, double range) {
        double dx = pos.x - center.x;
        double dz = pos.z - center.z;
        return dx * dx + dz * dz <= range * range && Math.abs(pos.y - center.y) <= VERTICAL_REACH;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        applyConfiguredStats();
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    /** 经验按打倒时的配置给（xpReward 不存盘，读档后只剩构造器写的默认值） */
    @Override
    public int getExperienceReward() {
        return config().stats().xp();
    }

    // ------------------------------------------------------------------ 守着的城

    /** 守住这座城（触发器调，加进世界以前）：记下城、醒来的位置 */
    public void guardTown(TownGuardians.Town town) {
        this.town = town;
        this.home = blockPosition();
    }

    @Nullable
    public TownGuardians.Town town() {
        return town;
    }

    /** 醒来的位置（追索中心；刷怪蛋放的是第一次走刻时站的地方） */
    @Nullable
    public BlockPos home() {
        return home;
    }

    /** 跑出了追索范围（slack 是额外的余量，往回走时用负数，走进来几格才停） */
    public boolean outsideLeash(double slack) {
        return home != null && !near(position(), Vec3.atBottomCenterOf(home), config().stats().leashRange() + slack);
    }

    private void markTown(TownGuardians.State state) {
        if (town != null && level() instanceof ServerLevel server) {
            TownGuardians.get(server.getServer()).set(town, state);
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        tickCombat();
    }

    /** 冷却、炮台、警报（{@link #customServerAiStep} 每刻调；GameTest 对 NoAI 的守望者直接调） */
    public void tickCombat() {
        if (home == null) {
            home = blockPosition();
        }
        if (suppressCooldown > 0) {
            suppressCooldown--;
        }
        tickBells();
        tickTurrets();
        checkAlarm();
    }

    public boolean isCharging() {
        return entityData.get(DATA_CHARGING);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (anims.handle(id, tickCount)) {
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (target instanceof ServerPlayer player && !level().isClientSide) {
            bossRoster().add(player);
        }
    }

    // ------------------------------------------------------------------ 横扫

    @Override
    public boolean doHurtTarget(Entity target) {
        anims.play(this, ANIM_SWEEP);
        boolean hit = super.doHurtTarget(target);
        sweep(target);
        playSound(ECTownSentry.SOUND_SWEEP.get(), 1.2F, 0.9F + random.nextFloat() * 0.2F);
        if (level() instanceof ServerLevel server) {
            Vec3 at = position().add(target.position().subtract(position()).normalize().scale(1.5D));
            server.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, getY() + getBbHeight() * 0.45D, at.z, 1, 0D, 0D, 0D, 0D);
        }
        return hit;
    }

    /** 横扫带到的其他人：每人挨一下 {@code sweep_damage}（{@code mobAttack}，吃护甲）。返回打中几个 */
    public int sweep(Entity main) {
        double damage = config().attacks().sweepDamage();
        int hits = 0;
        if (damage <= 0D) {
            return 0;
        }
        for (ServerPlayer other : sweepTargets(main)) {
            if (other != main && other.hurt(damageSources().mobAttack(this), (float) damage)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 横扫的扇形里名单内能打的参战者：以朝 main 的方向为中线、张角 {@code sweep_angle}，离它（身体边缘）水平 ≤ {@code sweep_range}、上下 −1~+3 格。
     * 含 main（是参战者的话）。
     */
    public List<ServerPlayer> sweepTargets(Entity main) {
        ECTownSentry.Config.Attacks attacks = config().attacks();
        double reach = attacks.sweepRange() + getBbWidth() * 0.5D;
        double dirX = main.getX() - getX();
        double dirZ = main.getZ() - getZ();
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 1.0E-4D) {
            Vec3 look = Vec3.directionFromRotation(0F, getYRot());
            dirX = look.x;
            dirZ = look.z;
            length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        }
        double cosHalf = Math.cos(Math.toRadians(Mth.clamp(attacks.sweepAngle(), 0D, 360D) * 0.5D));
        AABB box = new AABB(getX() - reach, getY() - 1D, getZ() - reach, getX() + reach, getY() + 3D, getZ() + reach);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : rosterTargets(box)) {
            double dx = player.getX() - getX();
            double dz = player.getZ() - getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > reach) {
                continue;
            }
            if (distance > 1.0E-4D && (dx * dirX + dz * dirZ) / (distance * length) < cosHalf - 1.0E-6D) {
                continue;
            }
            out.add(player);
        }
        return out;
    }

    /** 碰到 box 的能打的参战者（从名单里按 UUID 取在线的人，不扫世界里的实体） */
    public List<ServerPlayer> rosterTargets(AABB box) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!(level() instanceof ServerLevel server)) {
            return out;
        }
        for (UUID id : List.copyOf(bossRoster().participants())) {
            ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), id);
            if (player != null && isSkillTarget(player) && player.getBoundingBox().intersects(box)) {
                out.add(player);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 炮台

    /**
     * 炮台：名单里有在线的参战者时，第一次一口气架满 {@code turret_count} 座；之后少了一座就等 {@code turret_respawn_ticks} 刻补一座；
     * 名单空了 {@value #TURRET_RECALL_TICKS} 刻收回去（下一场重新架）。{@code turret_count} 为 0 时不架、已有的也收掉。
     */
    public void tickTurrets() {
        ECTownSentry.Config.Turrets cfg = config().turrets();
        if (cfg.count() <= 0) {
            if (turretsDeployed || standingTurrets() > 0) {
                recallTurrets();
            }
            return;
        }
        if (bossRoster().online().isEmpty()) {
            if (++idleTicks >= TURRET_RECALL_TICKS) {
                recallTurrets();
            }
            return;
        }
        idleTicks = 0;
        if (!turretsDeployed) {
            turretsDeployed = true;
            turretRespawnAt = -1;
            for (int i = standingTurrets(); i < cfg.count(); i++) {
                deployTurret();
            }
            return;
        }
        if (standingTurrets() >= cfg.count()) {
            turretRespawnAt = -1;
            return;
        }
        if (turretRespawnAt < 0) {
            turretRespawnAt = tickCount + cfg.respawnTicks();
        } else if (tickCount - turretRespawnAt >= 0) {
            turretRespawnAt = -1;
            deployTurret();
        }
    }

    /** 收掉所有炮台（下一场重新架） */
    public void recallTurrets() {
        for (Entity turret : skills().alive(SKILL_TURRET)) {
            turret.discard();
        }
        skills().count(SKILL_TURRET);
        turretsDeployed = false;
        turretRespawnAt = -1;
        idleTicks = 0;
    }

    /** 现在还在的炮台 */
    public List<Entity> turrets() {
        return skills().alive(SKILL_TURRET).stream().filter(Entity::isAlive).toList();
    }

    /** 还立着的炮台数：被打掉、正在倒下（死亡动画那 20 刻还没移除）的不算 */
    private int standingTurrets() {
        return turrets().size();
    }

    /** 炮台还在：正面挡弹射物 */
    public boolean turretsUp() {
        return standingTurrets() > 0;
    }

    /** 下一座炮台什么时候补（tickCount；-1 = 没在等） */
    public int turretRespawnAt() {
        return turretRespawnAt;
    }

    /**
     * 架一座炮台：在它左右两侧（离它 {@value #TURRET_OFFSET} 格，前后再挪两档）找站得下、离别的炮台 1.5 格以上的地方，先试炮台少的那一侧。
     * 找不到就不放（少一座）。成功返回炮台。
     */
    @Nullable
    public SentryTurretEntity deployTurret() {
        if (!(level() instanceof ServerLevel server) || standingTurrets() >= config().turrets().count()) {
            return null;
        }
        EntityType<SentryTurretEntity> type = ECTownSentry.SENTRY_TURRET.get();
        Vec3 spot = turretSpot(server, type);
        if (spot == null) {
            return null;
        }
        SentryTurretEntity turret = type.create(server);
        if (turret == null) {
            return null;
        }
        turret.moveTo(spot.x, spot.y, spot.z, yBodyRot, 0F);
        turret.setYHeadRot(yBodyRot);
        turret.setYBodyRot(yBodyRot);
        turret.bind(this);
        SpawnOrigin.set(turret, SpawnOrigin.Origin.SUMMONED);
        if (!server.addFreshEntity(turret)) {
            return null;
        }
        skills().track(SKILL_TURRET, turret);
        server.playSound(null, spot.x, spot.y, spot.z, ECTownSentry.SOUND_TURRET_DEPLOY.get(), SoundSource.HOSTILE, 1F, 1.2F);
        server.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.3D, spot.z, 8, 0.3D, 0.1D, 0.3D, 0.01D);
        return turret;
    }

    @Nullable
    private Vec3 turretSpot(ServerLevel server, EntityType<?> type) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        // 朝向是 (−sin, cos)；右手边是它转 90°
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        List<Entity> existing = turrets();
        int rightSide = 0;
        for (Entity turret : existing) {
            if ((turret.getX() - getX()) * rightX + (turret.getZ() - getZ()) * rightZ > 0D) {
                rightSide++;
            }
        }
        double first = rightSide <= existing.size() - rightSide ? 1D : -1D;
        for (double side : new double[]{first, -first}) {
            for (double ahead : new double[]{0D, -1.5D, 1.5D}) {
                double x = getX() + rightX * TURRET_OFFSET * side + forwardX * ahead;
                double z = getZ() + rightZ * TURRET_OFFSET * side + forwardZ * ahead;
                BlockPos base = BlockPos.containing(x, getY(), z);
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    BlockPos pos = base.above(dy);
                    if (!MRUGolemTrigger.standable(server, pos, type)) {
                        continue;
                    }
                    Vec3 at = Vec3.atBottomCenterOf(pos);
                    boolean crowded = false;
                    for (Entity turret : existing) {
                        if (turret.position().distanceToSqr(at) < 1.5D * 1.5D) {
                            crowded = true;
                            break;
                        }
                    }
                    if (!crowded) {
                        return at;
                    }
                }
            }
        }
        return null;
    }

    /** 炮台打谁：名单里在线、能打、在射程内、炮台看得见的参战者里最近的一个；没有返回 null */
    @Nullable
    public ServerPlayer turretTarget(SentryTurretEntity turret) {
        double range = config().turrets().range();
        AABB box = turret.getBoundingBox().inflate(range);
        ServerPlayer best = null;
        double bestDistance = range * range;
        for (ServerPlayer player : rosterTargets(box)) {
            double distance = player.distanceToSqr(turret);
            if (distance <= bestDistance && turret.hasLineOfSight(player)) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * 正面挡弹射物：炮台还在、{@code front_guard_angle} 不是 0、这一下是弹射物（{@code is_projectile}）、
     * 打来的位置（弹射物 / 射手的位置）在朝向左右各 张角 / 2 以内。带 {@code bypasses_invulnerability} 的不挡。
     */
    public boolean guardsAgainst(DamageSource source) {
        double angle = config().turrets().guardAngle();
        if (angle <= 0D || !source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !turretsUp()) {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        return from != null && inFront(from, angle);
    }

    /** from 在它朝向（身体）左右各 angle / 2 以内（水平量；angle ≥ 360 时一律算） */
    public boolean inFront(Vec3 from, double angle) {
        if (angle >= 360D) {
            return true;
        }
        double dx = from.x - getX();
        double dz = from.z - getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            return true;
        }
        Vec3 look = Vec3.directionFromRotation(0F, yBodyRot);
        double cos = (dx * look.x + dz * look.z) / length;
        return cos >= Math.cos(Math.toRadians(angle * 0.5D)) - 1.0E-6D;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && guardsAgainst(source)) {
            playSound(ECTownSentry.SOUND_DEFLECT.get(), 1F, 0.8F + random.nextFloat() * 0.2F);
            if (level() instanceof ServerLevel server) {
                Vec3 from = source.getSourcePosition();
                Vec3 at = from == null ? position() : position().add(from.subtract(position()).normalize().scale(getBbWidth() * 0.6D));
                server.sendParticles(ParticleTypes.CRIT, at.x, getY() + getBbHeight() * 0.55D, at.z, 6, 0.2D, 0.3D, 0.2D, 0.1D);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ 警报

    /** 第一次降到 {@code alarm_health_fraction} 以下就拉响警报（只一次，存盘） */
    public void checkAlarm() {
        if (alarmUsed || !isAlive()) {
            return;
        }
        ECTownSentry.Config.Alarm cfg = config().alarm();
        if (cfg.count() <= 0 || cfg.healthFraction() <= 0D || getHealth() > getMaxHealth() * cfg.healthFraction()) {
            return;
        }
        soundAlarm();
    }

    /**
     * 拉响全城警报：敲三下钟、给名单里的人动作栏说一句、叫来 {@code alarm_count} 名遗民拾荒者（来源先记「召唤」再走 Forge 的生成事件，被取消的不算）。
     * 已经响过就什么都不做。返回这次叫来几名。
     */
    public int soundAlarm() {
        if (alarmUsed || !(level() instanceof ServerLevel server)) {
            return 0;
        }
        alarmUsed = true;
        anims.play(this, ANIM_ALARM);
        bellsLeft = ALARM_BELLS;
        nextBell = tickCount;
        tickBells();
        for (ServerPlayer player : bossRoster().online()) {
            player.displayClientMessage(Component.translatable(MSG_ALARM).withStyle(ChatFormatting.GOLD), true);
        }
        int want = config().alarm().count();
        LivingEntity aim = getTarget();
        int spawned = 0;
        for (int i = 0; i < want; i++) {
            HoannaScavengerEntity scavenger = ECHoannaNativesA.HOANNA_SCAVENGER.get().create(server);
            if (scavenger == null) {
                break;
            }
            Vec3 at = scavengerSpot(scavenger);
            if (at == null) {
                continue;
            }
            scavenger.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0F);
            SpawnOrigin.set(scavenger, SpawnOrigin.Origin.SUMMONED);
            ForgeEventFactory.onFinalizeSpawn(scavenger, server, server.getCurrentDifficultyAt(scavenger.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            if (scavenger.isSpawnCancelled()) {
                continue;
            }
            if (aim != null) {
                scavenger.setTarget(aim);
            }
            if (server.addFreshEntity(scavenger)) {
                spawned++;
                server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.8D, at.z, 10, 0.3D, 0.5D, 0.3D, 0.01D);
            }
        }
        return spawned;
    }

    public boolean alarmUsed() {
        return alarmUsed;
    }

    private void tickBells() {
        if (bellsLeft <= 0 || tickCount - nextBell < 0) {
            return;
        }
        bellsLeft--;
        nextBell = tickCount + ALARM_BELL_GAP;
        playSound(ECTownSentry.SOUND_ALARM.get(), 3F, 0.5F);
    }

    /** 拾荒者放在哪：身边 2~4 格、地面上放得下它的点（往下最多找 3 格） */
    @Nullable
    private Vec3 scavengerSpot(Entity scavenger) {
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double radius = SCAVENGER_MIN_RADIUS + random.nextDouble() * (SCAVENGER_MAX_RADIUS - SCAVENGER_MIN_RADIUS);
            BlockPos.MutableBlockPos pos = BlockPos.containing(getX() + Math.cos(angle) * radius, getY() + 1D, getZ() + Math.sin(angle) * radius).mutable();
            for (int dy = 0; dy < 4; dy++) {
                BlockPos below = pos.below();
                if (!level().isLoaded(below)) {
                    break;
                }
                if (!level().getBlockState(below).getCollisionShape(level(), below).isEmpty()) {
                    Vec3 at = new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                    AABB box = scavenger.getType().getDimensions().makeBoundingBox(at);
                    if (level().noCollision(scavenger, box) && !level().containsAnyLiquid(box)) {
                        return at;
                    }
                    break;
                }
                pos.move(0, -1, 0);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 镇压

    public int suppressCooldown() {
        return suppressCooldown;
    }

    /** 镇压开始蓄力：放动画、蓄力音效，标记蓄力中（客户端发光层变亮） */
    public void beginSuppress() {
        anims.play(this, ANIM_SUPPRESS);
        entityData.set(DATA_CHARGING, true);
        playSound(ECTownSentry.SOUND_SUPPRESS_CHARGE.get(), 1.5F, 0.6F);
    }

    /** 蓄力中画一圈红色的预警（服务端发粒子，所有人看得见） */
    public void suppressWarning() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        double radius = config().attacks().suppressRadius();
        int points = Math.max(16, (int) (radius * 8));
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2D * i / points;
            server.sendParticles(RING, getX() + Math.cos(angle) * radius, getY() + 0.1D, getZ() + Math.sin(angle) * radius, 1, 0D, 0D, 0D, 0D);
        }
    }

    /**
     * 镇压落地：半径内（水平；上下 −1~+3 格）每名能打的参战者挨 {@code suppress_damage} + 最大生命 × {@code suppress_health_fraction}
     * （{@code aberrant_strike}，攻击者是守望者），上冷却。返回打中几个。
     */
    public int suppressImpact() {
        entityData.set(DATA_CHARGING, false);
        ECTownSentry.Config.Attacks attacks = config().attacks();
        suppressCooldown = attacks.suppressCooldownTicks();
        double radius = attacks.suppressRadius();
        AABB box = new AABB(getX() - radius, getY() - 1D, getZ() - radius, getX() + radius, getY() + 3D, getZ() + radius);
        int hits = 0;
        for (ServerPlayer target : rosterTargets(box)) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            float damage = (float) (attacks.suppressDamage() + target.getMaxHealth() * attacks.suppressHealthFraction());
            if (damage > 0F && target.hurt(ECDamageTypes.aberrantStrike(this), damage)) {
                hits++;
            }
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2D, getZ(), 4, radius * 0.4D, 0.1D, radius * 0.4D, 0D);
            server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.3D, getZ(), 12, radius * 0.4D, 0.2D, radius * 0.4D, 0D);
        }
        playSound(ECTownSentry.SOUND_SUPPRESS.get(), 2F, 0.8F);
        return hits;
    }

    // ------------------------------------------------------------------ 首领结束

    /** 和平难度散去前把这座城改回「还没醒」（难度调回来、有人再走近就再醒一次）；散去的那只不掉东西 */
    @Override
    public void endBoss(BossEnd reason) {
        if (reason == BossEnd.PEACEFUL && town != null && level() instanceof ServerLevel server) {
            peacefulEnd = true;
            TownGuardians.get(server.getServer()).clear(town);
        }
        super.endBoss(reason);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && !level().isClientSide) {
            markTown(TownGuardians.State.DEFEATED);
            recallTurrets();
        }
    }

    /** 被指令、别的模组直接删掉（不是死、不是区块卸载、不是和平散去）：这座城记「放弃了」，不再醒 */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && !isRemoved() && town != null && !peacefulEnd && level() instanceof ServerLevel server) {
            TownGuardians guardians = TownGuardians.get(server.getServer());
            if (reason == RemovalReason.KILLED) {
                guardians.set(town, TownGuardians.State.DEFEATED);
            } else if (reason == RemovalReason.DISCARDED && guardians.state(town) != TownGuardians.State.DEFEATED) {
                guardians.set(town, TownGuardians.State.ABANDONED);
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ 声音

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECTownSentry.SOUND_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECTownSentry.SOUND_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ECTownSentry.SOUND_STEP.get(), 0.9F, 1F);
    }

    // ------------------------------------------------------------------ 存档

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (town != null) {
            tag.putString(TAG_TOWN_DIMENSION, town.dimension().location().toString());
            tag.putLong(TAG_TOWN_CHUNK, town.startChunk());
        }
        if (home != null) {
            tag.put(TAG_HOME, NbtUtils.writeBlockPos(home));
        }
        tag.putBoolean(TAG_ALARM, alarmUsed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation dimension = tag.contains(TAG_TOWN_DIMENSION, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(TAG_TOWN_DIMENSION)) : null;
        town = dimension != null && tag.contains(TAG_TOWN_CHUNK, Tag.TAG_LONG)
                ? new TownGuardians.Town(ResourceKey.create(Registries.DIMENSION, dimension), tag.getLong(TAG_TOWN_CHUNK)) : null;
        home = tag.contains(TAG_HOME, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_HOME)) : null;
        alarmUsed = tag.getBoolean(TAG_ALARM);
        turretsDeployed = false;
        turretRespawnAt = -1;
    }

    /** 起点区块（没守城时 null） */
    @Nullable
    public ChunkPos townChunk() {
        return town == null ? null : town.chunk();
    }

    // ------------------------------------------------------------------ 目标

    /** 往回走：跑出追索范围就走回 {@link #home()}，走进来 4 格才停；20 秒走不回去就歇 5 秒再试 */
    final class ReturnHomeGoal extends Goal {

        private int ticks;
        private int restUntil;

        ReturnHomeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return tickCount >= restUntil && home != null && outsideLeash(0D);
        }

        @Override
        public boolean canContinueToUse() {
            return ticks < 400 && outsideLeash(-4D);
        }

        @Override
        public void start() {
            ticks = 0;
            moveHome();
        }

        @Override
        public void tick() {
            ticks++;
            if (ticks % 20 == 0 && getNavigation().isDone()) {
                moveHome();
            }
        }

        @Override
        public void stop() {
            if (ticks >= 400) {
                restUntil = tickCount + 100;
            }
            getNavigation().stop();
        }

        private void moveHome() {
            if (home != null) {
                getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.2D);
            }
        }
    }

    /** 闲逛只挑追索范围往里 4 格以内的点（不用原版 restrictTo：它会让目标选择不理范围外的玩家） */
    final class HomeStrollGoal extends WaterAvoidingRandomStrollGoal {

        HomeStrollGoal() {
            super(TownSentryEntity.this, 0.6D);
        }

        @Nullable
        @Override
        protected Vec3 getPosition() {
            Vec3 pos = super.getPosition();
            if (pos == null || home == null) {
                return pos;
            }
            return near(pos, Vec3.atBottomCenterOf(home), Math.max(2D, config().stats().leashRange() - 4D)) ? pos : null;
        }
    }

    /** 镇压：目标在半径内、冷却好了就站定蓄力，蓄满落地 */
    final class SuppressGoal extends Goal {

        private int windup;

        SuppressGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (suppressCooldown > 0 || target == null || !isSkillTarget(target)) {
                return false;
            }
            double reach = config().attacks().suppressRadius() + 0.5D;
            return distanceToSqr(target) <= reach * reach;
        }

        @Override
        public boolean canContinueToUse() {
            return windup > 0 && isAlive();
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public void start() {
            windup = config().attacks().suppressWarningTicks();
            getNavigation().stop();
            beginSuppress();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target != null) {
                getLookControl().setLookAt(target, 30F, 30F);
            }
            if (windup % 4 == 0) {
                suppressWarning();
            }
            if (--windup <= 0) {
                suppressImpact();
            }
        }

        @Override
        public void stop() {
            if (windup > 0) {
                entityData.set(DATA_CHARGING, false);
                suppressCooldown = config().attacks().suppressCooldownTicks();
            }
            windup = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }
}
