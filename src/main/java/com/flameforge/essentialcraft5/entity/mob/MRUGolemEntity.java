package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.MRUGolemClient;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.boss.BossDamageBudget;
import com.flameforge.essentialcraft5.entity.boss.BossRoster;
import com.flameforge.essentialcraft5.entity.boss.BossTier;
import com.flameforge.essentialcraft5.entity.boss.BossWarnings;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import com.flameforge.essentialcraft5.entity.boss.ECBossMonster;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECMRUGolem;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 聚能魔像 {@code mru_golem}（第十三批 13c-M2，batch13.md §5.3.1）：守着新生成的上古遗迹的小 Boss。由 {@link MRUGolemTrigger} 在遗迹的聚集体旁放出来，
 * 刷怪蛋 / 指令也能放（这样放的没有守着的聚集体，也就没有护盾；来源是刷怪蛋 / 其他，不算公平击杀，不掉关键材料）。
 *
 * <h2>守着的聚集体</h2>
 * 记着聚集体的 UUID、触发时聚集体所在的格子（{@link #home()}，追索按它量）和自己出来的位置（{@link #returnPoint()}，往回走走到这里）：
 * <ul>
 *   <li><b>追索</b>：离 home 水平超过 {@code leash_range}（16）格、或者上下差出「往上找地面的高度 + 8」格，就往回走（{@link ReturnHomeGoal}，
 *       抢在近战前面）。量水平距离是因为遗迹多半埋在地下，魔像站在正上方的地面上，直线距离一开始就超过 16 格。</li>
 *   <li><b>聚能护盾</b>（{@link #shieldSource()}）：守着的那团还活着、离魔像水平 ≤ {@code shield_range} 格（上下同上）、还剩 ≥ {@code shield_min_mru}
 *       时，每点伤害从那团扣 {@code shield_mru_per_damage} MRU 抵掉（{@link MRUGolemEvents#mruGolemShield}）。聚集体挪远、MRU 抽干，护盾就没了；
 *       魔像出来以后再挪聚集体不算放弃，只是护盾够不着。</li>
 *   <li>和平难度：名单把它散掉（{@code BossRoster}），散之前把那团的记录改回「等着触发」、原位改成那团现在的位置，难度调回来以后还会再出来。</li>
 * </ul>
 *
 * <h2>招式</h2>
 * 重锤（近战，{@code hammer_damage}）；聚能冲击（目标在半径内时，蓄力 {@code slam_warning_ticks} 刻、脚下画蓝圈，落地打圈里名单内的参战者
 * {@code slam_damage} + 目标最大生命 × {@code slam_health_fraction}，{@code aberrant_strike}）；电弧鞭（目标 {@code whip_min_range}~{@code whip_max_range}
 * 格、看得见，抬手半秒后 {@code whip_damage} 点 {@code mru_discharge}，攻击者是魔像，2 阶魔力防护挡得住）。两个技能只打名单里的参战者（{@link #isSkillTarget}）。
 *
 * <h2>小 Boss 规矩</h2>
 * {@link BossTier#MINI}：谁打都算伤害（关键掉落另要 {@code fair_kill}）、只有单次封顶 {@code per_hit_cap}、吃状态效果、有摔落伤害、能被击退（抗性 0.8）。
 * 名单是「守卫」式（{@link BossRoster.Settings#guardian}）：不拽人、不散去、不锁飞行，离魔像水平 {@value #ESCAPE_RANGE} 格外（或上下差出
 * {@link #verticalReach()}）脱战（{@link #beyondEscape}）。
 * 属性在注册时按默认值登记，生成时按当时的配置改基础值（{@link #applyConfiguredStats}）；读档时属性由原版读回来，不会叠加。
 */
public class MRUGolemEntity extends ECBossMonster {

    public static final int ANIM_HAMMER = 0;
    public static final int ANIM_SLAM = 1;
    public static final int ANIM_WHIP = 2;
    /** 实体事件：护盾抵掉了一下（客户端画聚集体到魔像的电弧）；不和原版（≤ 63）、动画（100~123）的号重 */
    public static final byte EVENT_SHIELD = 70;
    /** 实体事件：电弧鞭打出去了（客户端画魔像到目标的电弧） */
    public static final byte EVENT_WHIP = 71;
    /** 名单脱战距离：离魔像水平这么远就不算参战（上下按 {@link #verticalReach()}） */
    public static final double ESCAPE_RANGE = 32D;
    /** 电弧鞭抬手几刻 */
    public static final int WHIP_WINDUP = 10;
    /** 每隔几刻重新找一次守着的聚集体、更新护盾状态 */
    private static final int GUARD_REFRESH = 10;

    private static final EntityDataAccessor<Integer> DATA_CLUSTER = SynchedEntityData.defineId(MRUGolemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHIELDED = SynchedEntityData.defineId(MRUGolemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_WHIP_TARGET = SynchedEntityData.defineId(MRUGolemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(MRUGolemEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String TAG_PRESENCE = "guard_presence";
    private static final String TAG_HOME = "guard_home";
    private static final String TAG_RETURN = "guard_return";

    public final MobAnimStates anims = new MobAnimStates(3).channel(ANIM_HAMMER, 1).channel(ANIM_SLAM, 1).channel(ANIM_WHIP, 1);

    @Nullable
    private UUID presenceId;
    @Nullable
    private BlockPos home;
    @Nullable
    private BlockPos returnPoint;
    private int slamCooldown;
    private int whipCooldown;
    private int guardClock;

    public MRUGolemEntity(EntityType<? extends MRUGolemEntity> type, Level level) {
        super(type, level, BossTier.MINI, BossEvent.BossBarColor.BLUE);
        this.xpReward = ECMRUGolem.Config.DEFAULTS.stats().xp();
        this.setMaxUpStep(1F);
    }

    /** 注册时的属性（默认值；生成时再按配置改基础值） */
    public static AttributeSupplier.Builder createAttributes() {
        ECMRUGolem.Config.Stats stats = ECMRUGolem.Config.DEFAULTS.stats();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, stats.maxHealth())
                .add(Attributes.ARMOR, stats.armor())
                .add(Attributes.ARMOR_TOUGHNESS, stats.toughness())
                .add(Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance())
                .add(Attributes.ATTACK_DAMAGE, ECMRUGolem.Config.DEFAULTS.attacks().hammerDamage())
                .add(Attributes.ATTACK_KNOCKBACK, 1D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 24D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CLUSTER, -1);
        entityData.define(DATA_SHIELDED, false);
        entityData.define(DATA_WHIP_TARGET, -1);
        entityData.define(DATA_CHARGING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ReturnHomeGoal());
        goalSelector.addGoal(2, new SlamGoal());
        goalSelector.addGoal(3, new WhipGoal());
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1D, true));
        goalSelector.addGoal(6, new HomeStrollGoal());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------ 配置

    private static ECMRUGolem.Config.Values config() {
        return ECMRUGolem.Config.values();
    }

    /** 按现在的配置设属性基础值、回满血（生成时调；基础值是直接设的，调几次都一样） */
    public void applyConfiguredStats() {
        ECMRUGolem.Config.Stats stats = config().stats();
        setBase(Attributes.MAX_HEALTH, stats.maxHealth());
        setBase(Attributes.ARMOR, stats.armor());
        setBase(Attributes.ARMOR_TOUGHNESS, stats.toughness());
        setBase(Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance());
        setBase(Attributes.ATTACK_DAMAGE, config().attacks().hammerDamage());
        xpReward = stats.xp();
        setHealth(getMaxHealth());
    }

    private void setBase(net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
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

    /**
     * 脱战和追索、护盾同一口径：水平超过 {@value #ESCAPE_RANGE} 格、或者上下差出 {@link #verticalReach()} 才算跑远。
     * 魔像出在埋着的遗迹正上方的地面上，触发的人还站在下面的聚集体旁边，直线距离一出来就可能超过 32 格。
     */
    @Override
    public boolean beyondEscape(ServerPlayer player, Vec3 center, double range) {
        return !near(player.position(), center, range);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        applyConfiguredStats();
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    /**
     * 经验按打倒时的配置给。xpReward 是原版不存盘的字段，读档后的魔像只剩构造器写的默认值，所以不读它
     * （生命这类属性是存盘的，按生成时的配置；经验没有存，按现在的）。
     */
    @Override
    public int getExperienceReward() {
        return config().stats().xp();
    }

    // ------------------------------------------------------------------ 守着的聚集体

    /** 守住这团聚集体（触发器调，加进世界以前）：记下它、追索中心是它现在那一格、往回走走到魔像现在站的地方 */
    public void guard(MRUPresenceEntity presence) {
        presenceId = presence.getUUID();
        home = presence.blockPosition();
        returnPoint = blockPosition();
    }

    @Nullable
    public UUID guardedPresenceId() {
        return presenceId;
    }

    /** 追索中心（触发时聚集体那一格；刷怪蛋放的是第一次走刻时站的地方） */
    @Nullable
    public BlockPos home() {
        return home;
    }

    /** 往回走走到哪里 */
    @Nullable
    public BlockPos returnPoint() {
        return returnPoint != null ? returnPoint : home;
    }

    /** 守着的那团（服务端、已加载才找得到） */
    @Nullable
    public MRUPresenceEntity guardedPresence() {
        if (presenceId == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        Entity entity = server.getEntity(presenceId);
        return entity instanceof MRUPresenceEntity presence && presence.isAlive() ? presence : null;
    }

    /** 上下最多差多少还算「在旁边」：往上找地面的高度 + 8（魔像可能站在埋着的聚集体正上方的地面上） */
    public static double verticalReach() {
        return config().trigger().searchHeight() + 8D;
    }

    /** pos 离 center 水平 ≤ range、上下差 ≤ {@link #verticalReach()} */
    public static boolean near(Vec3 pos, Vec3 center, double range) {
        double dx = pos.x - center.x;
        double dz = pos.z - center.z;
        return dx * dx + dz * dz <= range * range && Math.abs(pos.y - center.y) <= verticalReach();
    }

    /** 跑出了追索范围（slack 是额外的余量，往回走时用负数，走进来几格才停） */
    public boolean outsideLeash(double slack) {
        if (home == null) {
            return false;
        }
        return !near(position(), Vec3.atBottomCenterOf(home), config().stats().leashRange() + slack);
    }

    /**
     * 现在能给护盾供能的那团：守着的那团活着、离魔像水平 ≤ {@code shield_range}、上下差在范围内、MRU ≥ {@code shield_min_mru}；
     * {@code shield_mru_per_damage} 为 0 时没有护盾。没有返回 null。
     */
    @Nullable
    public MRUPresenceEntity shieldSource() {
        ECMRUGolem.Config.Shield shield = config().shield();
        if (shield.mruPerDamage() <= 0 || shield.range() <= 0) {
            return null;
        }
        MRUPresenceEntity presence = guardedPresence();
        if (presence == null || presence.level() != level() || presence.getMRU() < shield.minMru()
                || !near(position(), presence.position(), shield.range())) {
            return null;
        }
        return presence;
    }

    /**
     * 护盾抵掉一下：amount 先按单次封顶夹一次（和 {@code BossDamageBudget.resolve} 同一个数），每抵 1 点扣 {@code shield_mru_per_damage}，
     * 那团扣光为止。返回抵掉以后还剩多少（没有护盾原样返回）。服务端调。
     */
    public float absorbWithShield(float amount) {
        if (!(amount > 0F)) {
            return amount;
        }
        MRUPresenceEntity presence = shieldSource();
        if (presence == null) {
            return amount;
        }
        int perPoint = config().shield().mruPerDamage();
        float cap = effectiveLimits().perHit();
        float capped = cap > 0F ? Math.min(amount, cap) : amount;
        int mru = presence.getMRU();
        float points = Math.min(capped, (float) mru / perPoint);
        int cost = Math.min(mru, Mth.ceil(points * perPoint - 1.0E-3F));
        if (cost <= 0 || points <= 0F) {
            return amount;
        }
        presence.getStorage().extractMRU(cost, true);
        level().broadcastEntityEvent(this, EVENT_SHIELD);
        level().playSound(null, getX(), getY() + 1D, getZ(), ECMRUGolem.SOUND_SHIELD.get(), SoundSource.HOSTILE, 1F, 0.9F + random.nextFloat() * 0.2F);
        float left = capped - points;
        return left <= 1.0E-4F ? 0F : left;
    }

    public boolean isShielded() {
        return entityData.get(DATA_SHIELDED);
    }

    public boolean isCharging() {
        return entityData.get(DATA_CHARGING);
    }

    /** 客户端：守着的那团的实体（同步来的实体 id）；不在附近返回 null */
    @Nullable
    public Entity syncedPresence() {
        int id = entityData.get(DATA_CLUSTER);
        return id < 0 ? null : level().getEntity(id);
    }

    @Nullable
    public Entity syncedWhipTarget() {
        int id = entityData.get(DATA_WHIP_TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    /** 服务端：重新找一次守着的那团，更新同步给客户端的实体 id 和护盾状态 */
    public void refreshGuard() {
        MRUPresenceEntity presence = guardedPresence();
        entityData.set(DATA_CLUSTER, presence == null ? -1 : presence.getId());
        entityData.set(DATA_SHIELDED, shieldSource() != null);
    }

    // ------------------------------------------------------------------ tick

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        tickCombat();
    }

    /** 冷却、守护状态（{@link #customServerAiStep} 每刻调；GameTest 对 NoAI 的魔像直接调） */
    public void tickCombat() {
        if (home == null) {
            home = blockPosition();
            returnPoint = home;
        }
        if (slamCooldown > 0) {
            slamCooldown--;
        }
        if (whipCooldown > 0) {
            whipCooldown--;
        }
        if (++guardClock >= GUARD_REFRESH) {
            guardClock = 0;
            refreshGuard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isShielded() && random.nextInt(40) == 0) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MRUGolemClient.shieldArc(this, true));
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (anims.handle(id, tickCount)) {
            return;
        }
        if (id == EVENT_SHIELD) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MRUGolemClient.shieldArc(this, false));
            return;
        }
        if (id == EVENT_WHIP) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MRUGolemClient.whipArc(this));
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

    // ------------------------------------------------------------------ 招式

    @Override
    public boolean doHurtTarget(Entity target) {
        anims.play(this, ANIM_HAMMER);
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            playSound(ECMRUGolem.SOUND_HAMMER.get(), 1F, 0.9F + random.nextFloat() * 0.2F);
        }
        return hit;
    }

    public int slamCooldown() {
        return slamCooldown;
    }

    public int whipCooldown() {
        return whipCooldown;
    }

    /**
     * 聚能冲击开始蓄力：放动画、蓄力音效，标记蓄力中（客户端核心变亮），把蓄力这段的预警圈发给附近的客户端
     * （{@link BossWarnings}，MRU 靛紫，圈心跟着它，蓄满 {@code slam_warning_ticks} 刻落下）
     */
    public void beginSlam() {
        anims.play(this, ANIM_SLAM);
        entityData.set(DATA_CHARGING, true);
        playSound(ECMRUGolem.SOUND_SLAM_CHARGE.get(), 1.5F, 0.8F);
        ECMRUGolem.Config.Attacks attacks = config().attacks();
        BossWarnings.circle(this, this, position(), attacks.slamRadius(), attacks.slamWarningTicks(), BossWarnings.MRU_BLUE);
    }

    /**
     * 聚能冲击落地：半径内（水平；上下 −1~+3 格）每名能打的参战者挨 {@code slam_damage} + 最大生命 × {@code slam_health_fraction}
     * （{@code aberrant_strike}，攻击者是魔像），上冷却。返回打中几个。
     */
    public int slamImpact() {
        entityData.set(DATA_CHARGING, false);
        ECMRUGolem.Config.Attacks attacks = config().attacks();
        slamCooldown = attacks.slamCooldownTicks();
        double radius = attacks.slamRadius();
        AABB box = new AABB(getX() - radius, getY() - 1D, getZ() - radius, getX() + radius, getY() + 3D, getZ() + radius);
        int hits = 0;
        for (ServerPlayer target : rosterTargets(box)) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            float damage = (float) (attacks.slamDamage() + target.getMaxHealth() * attacks.slamHealthFraction());
            if (damage > 0F && target.hurt(ECDamageTypes.aberrantStrike(this), damage)) {
                hits++;
            }
        }
        // 砸地 + 放电由客户端画（FX_GOLEM_SLAM）
        BossWarnings.resolve(this, S2CBossWarningPacket.FX_GOLEM_SLAM, position(), radius, BossWarnings.MRU_BLUE);
        playSound(ECMRUGolem.SOUND_SLAM.get(), 2F, 0.9F);
        return hits;
    }

    /**
     * 碰到 box 的能打的参战者（{@link #isSkillTarget}）。从名单里按 UUID 取在线的人，不扫世界里的实体：技能本来就只打名单里的人，
     * 名单最多十几个，比按方块盒查实体省。
     */
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

    /** 电弧鞭抬手：放动画、把目标同步给客户端（打出去时画电弧用） */
    public void beginWhip(LivingEntity target) {
        anims.play(this, ANIM_WHIP);
        entityData.set(DATA_WHIP_TARGET, target.getId());
    }

    /**
     * 电弧鞭打出去：target 还是能打的参战者、在最远距离 + 1 格内、看得见，就挨 {@code whip_damage} 点 {@code mru_discharge}（攻击者是魔像）。
     * 不论打没打中都上冷却。返回打中没有。
     */
    public boolean whipStrike(LivingEntity target) {
        ECMRUGolem.Config.Attacks attacks = config().attacks();
        whipCooldown = attacks.whipCooldownTicks();
        double reach = attacks.whipMaxRange() + 1D;
        if (!isSkillTarget(target) || distanceToSqr(target) > reach * reach || !hasLineOfSight(target)) {
            return false;
        }
        entityData.set(DATA_WHIP_TARGET, target.getId());
        level().broadcastEntityEvent(this, EVENT_WHIP);
        playSound(ECMRUGolem.SOUND_WHIP.get(), 1.5F, 1F + random.nextFloat() * 0.2F);
        return attacks.whipDamage() > 0D && target.hurt(ECDamageTypes.mruDischarge(this), (float) attacks.whipDamage());
    }

    // ------------------------------------------------------------------ 首领结束

    /**
     * 和平难度散去前把聚集体改回「等着触发」：难度调回来、有人再走近就再出来一次（名单空了不会散，守卫式名单不散去）。
     * 原位一并改成聚集体现在那一格：魔像出来以后再拽聚集体不算放弃，拽远了的这团要是还按遗迹原位量，下一次漂移检查就记成放弃了。
     * 散去的那只不掉东西，换原位没有复制的路。聚集体所在的区块没加载时找不到它，改不回来（不为此同步加载区块）。
     */
    @Override
    public void endBoss(BossEnd reason) {
        if (reason == BossEnd.PEACEFUL) {
            MRUPresenceEntity presence = guardedPresence();
            if (presence != null && presence.getGuardian().state() == MRUGolemGuardian.State.SPAWNED) {
                presence.setGuardian(MRUGolemGuardian.pending(presence.blockPosition()));
            }
        }
        super.endBoss(reason);
    }

    // ------------------------------------------------------------------ 声音

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECMRUGolem.SOUND_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECMRUGolem.SOUND_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ECMRUGolem.SOUND_STEP.get(), 0.8F, 1F);
    }

    // ------------------------------------------------------------------ 存档

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (presenceId != null) {
            tag.putUUID(TAG_PRESENCE, presenceId);
        }
        if (home != null) {
            tag.put(TAG_HOME, NbtUtils.writeBlockPos(home));
        }
        if (returnPoint != null) {
            tag.put(TAG_RETURN, NbtUtils.writeBlockPos(returnPoint));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        presenceId = tag.hasUUID(TAG_PRESENCE) ? tag.getUUID(TAG_PRESENCE) : null;
        home = tag.contains(TAG_HOME, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_HOME)) : null;
        returnPoint = tag.contains(TAG_RETURN, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_RETURN)) : null;
    }

    // ------------------------------------------------------------------ 目标

    /** 往回走：跑出追索范围就走回 {@link #returnPoint()}，走进来 4 格才停；20 秒走不回去就歇 5 秒再试 */
    final class ReturnHomeGoal extends Goal {

        private int ticks;
        private int restUntil;

        ReturnHomeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return tickCount >= restUntil && returnPoint() != null && outsideLeash(0D);
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
            BlockPos to = returnPoint();
            if (to != null) {
                getNavigation().moveTo(to.getX() + 0.5D, to.getY(), to.getZ() + 0.5D, 1.2D);
            }
        }
    }

    /**
     * 闲逛只挑追索范围往里 4 格以内的点。不用原版的 {@code restrictTo}：它会让目标选择（{@code TargetGoal.canAttack}）不理范围外的玩家，
     * 站远处放箭的人魔像连看都不看。
     */
    final class HomeStrollGoal extends WaterAvoidingRandomStrollGoal {

        HomeStrollGoal() {
            super(MRUGolemEntity.this, 0.6D);
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

    /** 聚能冲击：目标在半径内、冷却好了就站定蓄力，蓄满落地 */
    final class SlamGoal extends Goal {

        private int windup;

        SlamGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (slamCooldown > 0 || target == null || !isSkillTarget(target)) {
                return false;
            }
            double reach = config().attacks().slamRadius() + 0.5D;
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
            windup = config().attacks().slamWarningTicks();
            getNavigation().stop();
            beginSlam();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target != null) {
                getLookControl().setLookAt(target, 30F, 30F);
            }
            if (--windup <= 0) {
                slamImpact();
            }
        }

        @Override
        public void stop() {
            if (windup > 0) {
                // 蓄力被打断（死了）也上冷却，不留半截的蓄力标记，客户端的预警圈撤掉
                entityData.set(DATA_CHARGING, false);
                slamCooldown = config().attacks().slamCooldownTicks();
                BossWarnings.cancel(MRUGolemEntity.this);
            }
            windup = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /** 电弧鞭：目标在中距离、看得见、冷却好了，抬手半秒后打出去 */
    final class WhipGoal extends Goal {

        private int windup;
        @Nullable
        private LivingEntity aimed;

        WhipGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (whipCooldown > 0 || target == null || !isSkillTarget(target) || !hasLineOfSight(target)) {
                return false;
            }
            ECMRUGolem.Config.Attacks attacks = config().attacks();
            double distance = distanceTo(target);
            return distance >= attacks.whipMinRange() && distance <= attacks.whipMaxRange();
        }

        @Override
        public boolean canContinueToUse() {
            return windup > 0 && aimed != null && aimed.isAlive();
        }

        @Override
        public void start() {
            aimed = getTarget();
            windup = WHIP_WINDUP;
            getNavigation().stop();
            if (aimed != null) {
                beginWhip(aimed);
            }
        }

        @Override
        public void tick() {
            if (aimed == null) {
                return;
            }
            getLookControl().setLookAt(aimed, 30F, 30F);
            if (--windup <= 0) {
                whipStrike(aimed);
            }
        }

        @Override
        public void stop() {
            if (windup > 0) {
                whipCooldown = config().attacks().whipCooldownTicks() / 2;
            }
            windup = 0;
            aimed = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    // ------------------------------------------------------------------ 事件

    /** Forge 总线（处理器名带 {@code mruGolem} 前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class MRUGolemEvents {

        private MRUGolemEvents() {
        }

        /**
         * 聚能护盾：LOW，比单次封顶和伤害记账（都在 LOWEST）早一步，抵掉的部分不算打实、不记进名单和占比；
         * 带 {@code bypasses_invulnerability} 的（{@code /kill}、虚空）不挡。
         */
        @SubscribeEvent(priority = EventPriority.LOW)
        public static void mruGolemShield(LivingDamageEvent event) {
            if (!(event.getEntity() instanceof MRUGolemEntity golem) || golem.level().isClientSide
                    || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return;
            }
            float left = golem.absorbWithShield(event.getAmount());
            if (left < event.getAmount()) {
                event.setAmount(left);
            }
        }
    }
}
