package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.network.S2CBossFxPacket;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 矩阵之影（第十三批 13c-M4，batch13.md §5.3.2、D21 A）：拿矩阵导流符在灵魂五芒星上召出来的可选首领（{@link BossTier#BOSS}），
 * 召唤者的一部分矩阵损伤转进它的身体里，打倒它这些损伤就没了。召唤、预扣、退还见 {@link MatrixShadeSummon}、{@link MatrixShadeContracts}。
 *
 * <h2>属性与场地</h2>
 * 生命 = {@code base_health} + a / {@code absorbed_per_health}（a = 转进来的矩阵损伤，默认 300 + a / 600，即 330~420），护甲 8、韧性 4，
 * 单次封顶 25、每秒桶 15（{@link BossDamageBudget}）。召出来的那只以五芒星中心为场地：自己不离开 {@code arena_radius}（20 格），
 * 参战者 24~48 格被拽回、48 格外退出战斗（{@link BossRoster}，拉回被取消、落点被堵或没权限时不拉不扣、人留名单，这样拉不回来满 600 刻才算脱战）。
 * 刷怪蛋 / 指令放出来的没有召唤者、a = 0，场地以出生点为中心，照样打，但打倒了什么都不给（掉落表每个池都要 {@code fair_kill}，
 * 经验、首杀也不给，{@link #rewardsAllowed}；batch13.md §5.4 矩阵结晶刷怪塔上限 0、§9.3 指令和别的模组生成的一律不公平），不退还任何东西。
 *
 * <h2>三相（{@link Phase}，对应五芒星治的身体 / 心智 / 矩阵三类腐化）</h2>
 * 生命高于 2/3 是身相、高于 1/3 是心相、其余是矩阵相，只往前换不往回换。换相时 {@code phase_shift_ticks}（40 刻）打不动、不回血，
 * 正在蓄的招打断，离开心相时收掉镜影和心智钉，给参战者的屏幕边缘换一层颜色（{@link S2CBossFxPacket#PARADOX}）。
 * 打了 {@code enrage_ticks}（5 分钟）以后招式冷却 ×0.7。招式见 {@link MatrixShadeSkills}；近身的普通攻击是「双拳」（吃护甲），
 * 有心智钉立着时它挨的伤害 ×{@code spike_damage_taken}。
 *
 * <h2>结束</h2>
 * <ul>
 *   <li>打倒（参战的玩家打倒、契约还开着）：掉落按 {@link BossLoot} 每名打出 ≥ 10% 的人一份进背包；召唤者分得到时另给 ⌊a / {@code crystal_step}⌋ 颗矩阵结晶
 *       （{@link #onBossDefeated}）；契约结清，转进来的损伤不再退。</li>
 *   <li>失败（名单空了到点、和平难度、召唤者死亡 / 下线 / 换维度 / 在拉回距离以外待了 30 秒、没有玩家参与的死亡（{@code /kill}、掉出世界，
 *       哪怕刚有人打过它）、被别的东西直接删掉）：
 *       {@link MatrixShadeContracts#fail} 退还 a、预扣的 ESPE、导流符；它自己 {@code dissipate_ticks}（200 刻）后散去（{@link #beginDissipate}），
 *       这段时间打不动、不出招、名单不再拉人。</li>
 * </ul>
 * 召出来的那只读档后先看契约还在不在：不在（它卸载期间已经按失败退过了）就直接进入散去，不会再退一次。
 */
public class MatrixShadeEntity extends ECBossMonster {

    public static final float WIDTH = 0.98F;
    public static final float HEIGHT = 3.0F;

    /** 动画段（{@link MobAnimStates}，事件号 100 + 段） */
    public static final int ANIM_FIST_LEFT = 0;
    public static final int ANIM_FIST_RIGHT = 1;
    public static final int ANIM_GRIP = 2;
    public static final int ANIM_SLAM = 3;
    public static final int ANIM_CAST = 4;
    public static final int ANIM_WHISPER = 5;
    public static final int ANIM_ARC = 6;
    public static final int ANIM_CONVERGE = 7;
    public static final int ANIM_PHASE = 8;
    public static final int ANIM_FADE = 9;
    public static final int ANIM_SLOTS = 10;

    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(MatrixShadeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(MatrixShadeEntity.class, EntityDataSerializers.BYTE);
    private static final byte FLAG_SHIFTING = 1;
    private static final byte FLAG_DISSIPATING = 2;
    private static final byte FLAG_ENRAGED = 4;

    private static final String TAG_ABSORBED = "absorbed";
    private static final String TAG_SUMMONER = "summoner";
    private static final String TAG_PENTACLE = "pentacle";
    private static final String TAG_HOME = "home";
    private static final String TAG_BOUND = "bound";
    private static final String TAG_FIGHT = "fight_ticks";
    private static final String TAG_PHASE = "phase";
    private static final String TAG_SHIFT = "phase_shift";
    private static final String TAG_DISSIPATE = "dissipate";
    /** 场地外再多这么远还没走回去，就直接回到中心 */
    private static final double LEASH_SLACK = 12D;
    /** 每多少刻看一次有没有新进名单、还没收到画面的人 */
    private static final int FX_CHECK_INTERVAL = 10;

    /** 三相：画面颜色与强度（悖论画面的边缘晕影：a = 强度，b = 颜色，见 {@link S2CBossFxPacket}） */
    public enum Phase {
        /** 身：双拳、迟滞之握、坠身 */
        BODY(0x8A1C2C, 0.35F),
        /** 心：镜影、心智钉、低语 */
        MIND(0x6A2C9A, 0.6F),
        /** 矩阵：矩阵电弧、失序之柱、收束 */
        MATRIX(0xC0209A, 0.8F);

        private final int color;
        private final float strength;

        Phase(int color, float strength) {
            this.color = color;
            this.strength = strength;
        }

        public int color() {
            return color;
        }

        public float strength() {
            return strength;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Phase byId(int id) {
            Phase[] values = values();
            return values[Math.max(0, Math.min(values.length - 1, id))];
        }

        /** 生命比例对应的相（只往前换由调用方管） */
        public static Phase forHealth(float health, float max) {
            float ratio = max > 0F ? health / max : 0F;
            if (ratio > 2F / 3F) {
                return BODY;
            }
            return ratio > 1F / 3F ? MIND : MATRIX;
        }
    }

    /** 动画状态（两端都建；服务端只用来发事件） */
    public final MobAnimStates anims = new MobAnimStates(ANIM_SLOTS)
            .channel(ANIM_FIST_LEFT, 1).channel(ANIM_FIST_RIGHT, 1).channel(ANIM_GRIP, 1).channel(ANIM_SLAM, 1).channel(ANIM_CAST, 1)
            .channel(ANIM_WHISPER, 1).channel(ANIM_ARC, 1).channel(ANIM_CONVERGE, 1).channel(ANIM_PHASE, 1).channel(ANIM_FADE, 1);

    private final MatrixShadeSkills shadeSkills = new MatrixShadeSkills(this);
    private int absorbed;
    @Nullable
    private UUID summoner;
    @Nullable
    private BlockPos pentacle;
    @Nullable
    private BlockPos home;
    private boolean bound;
    private int fightTicks;
    private int phaseShift;
    private int dissipate = -1;
    /** 已经收到当前这一相画面的人（新进名单的人补发一次；换相、读档清空，离开名单的人删掉） */
    private final Set<UUID> fxSeen = new java.util.HashSet<>();
    private boolean leftFist;
    /** 看到散去标记的那一刻（两端各记各的，客户端渲染淡出用） */
    private int dissipateSeen = -1;

    public MatrixShadeEntity(EntityType<? extends MatrixShadeEntity> type, Level level) {
        super(type, level, BossTier.BOSS, BossEvent.BossBarColor.PURPLE);
        xpReward = ECMatrixShade.Config.values().fight().experience();
    }

    public static AttributeSupplier.Builder createAttributes() {
        ECMatrixShade.Config.Fight f = ECMatrixShade.Config.DEFAULTS.fight();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, f.baseHealth())
                .add(Attributes.ARMOR, f.armor())
                .add(Attributes.ARMOR_TOUGHNESS, f.toughness())
                .add(Attributes.ATTACK_DAMAGE, f.attackDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 40D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PHASE, (byte) 0);
        entityData.define(DATA_FLAGS, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ShadeMeleeGoal(this));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !dissipating() && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> !dissipating() && isSkillTarget(target)));
    }

    // ------------------------------------------------------------------
    // 召唤时的数值
    // ------------------------------------------------------------------

    /**
     * 召唤代码在加进世界之前调：记下召唤者、场地、转进来的损伤，按 a 设生命（满血）、护甲、韧性、攻击。召唤者另由召唤代码在加进世界后放进名单。
     */
    public void prepareSummon(ServerPlayer player, BlockPos pentaclePos, int absorbedDamage) {
        absorbed = Math.max(0, absorbedDamage);
        summoner = player.getUUID();
        pentacle = pentaclePos.immutable();
        home = pentacle;
        bound = true;
        applyStats();
    }

    /** 按配置和 a 设属性、满血（只在生成时调一次；读档不重设，存档里的属性和生命原样读回） */
    public void applyStats() {
        ECMatrixShade.Config.Fight f = ECMatrixShade.Config.values().fight();
        double health = f.baseHealth() + (f.absorbedPerHealth() > 0 ? absorbed / (double) f.absorbedPerHealth() : 0D);
        setBase(Attributes.MAX_HEALTH, health);
        setBase(Attributes.ARMOR, f.armor());
        setBase(Attributes.ARMOR_TOUGHNESS, f.toughness());
        setBase(Attributes.ATTACK_DAMAGE, f.attackDamage());
        setHealth(getMaxHealth());
        xpReward = f.experience();
    }

    private void setBase(net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** 刷怪蛋、指令放出来的：没有召唤者，a = 0，场地以出生点为中心 */
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        if (!bound) {
            applyStats();
            home = blockPosition();
        }
        return data;
    }

    // ------------------------------------------------------------------
    // 读数
    // ------------------------------------------------------------------

    /** 转进来的矩阵损伤 a */
    public int absorbed() {
        return absorbed;
    }

    @Nullable
    public UUID summoner() {
        return summoner;
    }

    /** 召出它的五芒星（刷怪蛋放出来的是 null） */
    @Nullable
    public BlockPos pentacle() {
        return pentacle;
    }

    /** 有召唤契约（导流符召出来的） */
    public boolean bound() {
        return bound;
    }

    public Phase phase() {
        return Phase.byId(entityData.get(DATA_PHASE));
    }

    public boolean phaseShifting() {
        return (entityData.get(DATA_FLAGS) & FLAG_SHIFTING) != 0;
    }

    public boolean dissipating() {
        return (entityData.get(DATA_FLAGS) & FLAG_DISSIPATING) != 0;
    }

    public boolean enraged() {
        return (entityData.get(DATA_FLAGS) & FLAG_ENRAGED) != 0;
    }

    public int fightTicks() {
        return fightTicks;
    }

    public int phaseShiftTicks() {
        return phaseShift;
    }

    public int dissipateTicks() {
        return dissipate;
    }

    public MatrixShadeSkills shadeSkills() {
        return shadeSkills;
    }

    /** 开始散去的那一刻（本端的 tickCount；没在散去时是当前刻） */
    public int dissipateStartTick() {
        return dissipateSeen >= 0 ? dissipateSeen : tickCount;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_FLAGS.equals(key)) {
            if (dissipating()) {
                if (dissipateSeen < 0) {
                    dissipateSeen = tickCount;
                }
            } else {
                dissipateSeen = -1;
            }
        }
    }

    /** 冷却倍数：打满 {@code enrage_ticks} 以后乘 {@code enrage_cooldown_multiplier} */
    public double cooldownMultiplier() {
        ECMatrixShade.Config.Fight f = ECMatrixShade.Config.values().fight();
        return fightTicks >= f.enrageTicks() ? f.enrageCooldown() : 1D;
    }

    /** 场地中心（五芒星的顶面中心；刷怪蛋放出来的是出生点；都没有时是自己） */
    public Vec3 arenaCenter() {
        BlockPos center = home != null ? home : pentacle;
        return center != null ? Vec3.atBottomCenterOf(center.above()) : position();
    }

    private void setFlag(byte flag, boolean on) {
        byte flags = entityData.get(DATA_FLAGS);
        byte next = (byte) (on ? flags | flag : flags & ~flag);
        if (next != flags) {
            entityData.set(DATA_FLAGS, next);
        }
    }

    // ------------------------------------------------------------------
    // ECBoss
    // ------------------------------------------------------------------

    @Override
    public BossDamageBudget.Limits bossLimits() {
        ECMatrixShade.Config.Fight f = ECMatrixShade.Config.values().fight();
        return BossDamageBudget.Limits.of(f.perHit(), f.perSecond());
    }

    /** 平时 24~48 格拉回、名单空了散去、压飞行；散去途中不再拉人、不再计时 */
    @Override
    public BossRoster.Settings rosterSettings() {
        ECMatrixShade.Config.Fight f = ECMatrixShade.Config.values().fight();
        BossRoster.Settings arena = BossRoster.Settings.arena(f.pullRange(), f.escapeRange());
        if (dissipating()) {
            return new BossRoster.Settings(arena.engageRange(), 0D, arena.escapeRange(), false, false);
        }
        return arena;
    }

    @Override
    public @Nullable BlockPos arenaFloor() {
        return pentacle;
    }

    /** 名单空了到点、和平难度：按失败退还，200 刻后散去 */
    @Override
    public void endBoss(BossEnd reason) {
        if (level() instanceof ServerLevel server) {
            MatrixShadeContracts.fail(server, getUUID(), reason == BossEnd.PEACEFUL
                    ? MatrixShadeContracts.Failure.PEACEFUL : MatrixShadeContracts.Failure.ABANDONED);
        }
        beginDissipate();
    }

    /**
     * 打倒：契约结清；召唤者分得到掉落时另给矩阵结晶。异兽录那一步只由 {@link BossLoot#recordVictory} 发给分得到掉落的人
     * （batch13.md §5.3 的参战者口径：打够一成），和聚能魔像、以后的首领同一条规则；「散成了碎片」的提示另外发给名单里此刻在线的人。
     */
    @Override
    public void onBossDefeated(List<ServerPlayer> winners) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        // 多给的结晶只在这一次真把契约结掉时给（已经按失败退过的不给），也照 doMobLoot 规则
        boolean settled = MatrixShadeContracts.defeated(server, getUUID());
        int bonus = settled && server.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT) ? bonusCrystals(absorbed) : 0;
        for (ServerPlayer player : winners) {
            if (bonus > 0 && player.getUUID().equals(summoner)) {
                ECItemEntities.giveOrDrop(player, new ItemStack(ECMobDrops.MATRIX_CRYSTAL.get(), bonus));
            }
        }
        Set<ServerPlayer> told = new LinkedHashSet<>(winners);
        told.addAll(bossRoster().online());
        for (ServerPlayer player : told) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_shade.defeated"), false);
        }
    }

    /** 召唤者多拿几颗矩阵结晶：⌊a / crystal_step⌋（crystal_step 是 0 时不多给） */
    public static int bonusCrystals(int absorbed) {
        int step = ECMatrixShade.Config.values().summon().crystalStep();
        return step > 0 ? Math.max(0, absorbed) / step : 0;
    }

    // ------------------------------------------------------------------
    // 每刻
    // ------------------------------------------------------------------

    /** 名单那部分之后接自己的：散去倒计时、契约检查、换相、场地、招式（GameTest 对 NoAI 的直接调） */
    @Override
    public void serverBossStep() {
        super.serverBossStep();
        if (!(level() instanceof ServerLevel server) || isRemoved() || isDeadOrDying()) {
            return;
        }
        if (dissipate >= 0) {
            tickDissipate(server);
            return;
        }
        if (bound && !MatrixShadeContracts.isOpen(server, getUUID())) {
            // 卸载期间已经按失败退过了（召唤者下线、换维度……）：只散去，不再退
            beginDissipate();
            return;
        }
        if (home == null) {
            home = blockPosition();
        }
        fightTicks++;
        if (tickCount % FX_CHECK_INTERVAL == 0) {
            sendParadoxToNewcomers();
        }
        if (phaseShift > 0 && --phaseShift == 0) {
            setFlag(FLAG_SHIFTING, false);
        }
        if (!enraged() && fightTicks >= ECMatrixShade.Config.values().fight().enrageTicks()) {
            setFlag(FLAG_ENRAGED, true);
        }
        checkPhase();
        keepToArena(server);
        shadeSkills.tick(server);
        if (tickCount % 4 == 0) {
            float[] rgb = rgb(phase().color());
            server.sendParticles(new DustParticleOptions(new Vector3f(rgb[0], rgb[1], rgb[2]), 1.2F),
                    getX(), getY() + getBbHeight() * 0.55D, getZ(), 2, 0.4D, 0.6D, 0.4D, 0D);
        }
    }

    /** 生命掉到下一档就换相（只往前换） */
    public void checkPhase() {
        Phase want = Phase.forHealth(getHealth(), getMaxHealth());
        if (want.ordinal() > phase().ordinal()) {
            enterPhase(want);
        }
    }

    /** 换到 next 相：打不动 phase_shift_ticks 刻、打断正在蓄的招、离开心相时收掉镜影和心智钉、换边缘颜色 */
    public void enterPhase(Phase next) {
        Phase previous = phase();
        entityData.set(DATA_PHASE, (byte) next.ordinal());
        phaseShift = ECMatrixShade.Config.values().fight().phaseShiftTicks();
        setFlag(FLAG_SHIFTING, phaseShift > 0);
        shadeSkills.cancelCast();
        if (previous == Phase.MIND && next != Phase.MIND) {
            discardSkills(MatrixShadeSkills.KIND_MIRROR);
            discardSkills(MatrixShadeSkills.KIND_SPIKE);
        }
        anims.play(this, ANIM_PHASE);
        playSound(ECMatrixShade.SOUND_PHASE.get(), 2F, 1F);
        sendParadox(next.strength(), next.color());
        Component name = Component.translatable("message.essentialcraft5.matrix_shade.phase." + next.key());
        for (ServerPlayer player : bossRoster().online()) {
            player.displayClientMessage(name, true);
        }
    }

    private void discardSkills(String kind) {
        for (Entity entity : skills().alive(kind)) {
            entity.discard();
        }
    }

    /** 离场地中心太远：走回去（{@code MoveTowardsRestrictionGoal}）；再远 12 格还没回去就直接回到中心 */
    private void keepToArena(ServerLevel server) {
        double radius = ECMatrixShade.Config.values().fight().arenaRadius();
        Vec3 center = arenaCenter();
        restrictTo(BlockPos.containing(center), (int) Math.ceil(radius));
        if (position().distanceTo(center) > radius + LEASH_SLACK && !shadeSkills.casting()) {
            Vec3 old = position();
            moveTo(center.x, center.y, center.z, getYRot(), getXRot());
            if (!server.noCollision(this)) {
                moveTo(old.x, old.y, old.z, getYRot(), getXRot());
            } else {
                getNavigation().stop();
            }
        }
    }

    /** 失败：不再出招、打不动、不拉人，dissipate_ticks 刻后散去。重复调没有副作用 */
    public void beginDissipate() {
        if (dissipate >= 0 || !(level() instanceof ServerLevel)) {
            return;
        }
        dissipate = Math.max(1, ECMatrixShade.Config.values().summon().dissipateTicks());
        setFlag(FLAG_DISSIPATING, true);
        shadeSkills.cancelAll();
        skills().discardAll();
        setTarget(null);
        getNavigation().stop();
        anims.play(this, ANIM_FADE);
        sendParadox(0F, phase().color());
        for (ServerPlayer player : bossRoster().online()) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_shade.fading"), true);
        }
    }

    private void tickDissipate(ServerLevel server) {
        setTarget(null);
        if (tickCount % 3 == 0) {
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() * 0.5D, getZ(), 6,
                    0.5D, 1D, 0.5D, 0.02D);
        }
        if (--dissipate <= 0) {
            dissipate();
        }
    }

    /** 悖论画面：只发给名单里在线的人（a = 强度，b = 颜色）；发过的人记下，后进名单的由 {@link #sendParadoxToNewcomers} 补 */
    private void sendParadox(float strength, int color) {
        List<ServerPlayer> online = bossRoster().online();
        fxSeen.clear();
        for (ServerPlayer player : online) {
            fxSeen.add(player.getUUID());
        }
        S2CBossFxPacket.send(online, S2CBossFxPacket.of(S2CBossFxPacket.PARADOX, this, strength, (float) color));
    }

    /** 新进名单（离开名单时从记录里删掉，所以重新上线、跑回来再进名单的也算）、还没收到这一相画面的人补发一次；按「进名单」这个事件发，不每刻发 */
    private void sendParadoxToNewcomers() {
        List<ServerPlayer> fresh = new ArrayList<>();
        for (ServerPlayer player : bossRoster().online()) {
            if (fxSeen.add(player.getUUID())) {
                fresh.add(player);
            }
        }
        if (!fresh.isEmpty()) {
            S2CBossFxPacket.send(fresh, S2CBossFxPacket.of(S2CBossFxPacket.PARADOX, this, phase().strength(), (float) phase().color()));
        }
    }

    /**
     * 有人离开了名单（脱战、下线、死了）：他那一层画面收掉（还在线的单独发强度 0），并从「收到过」里删掉，再进名单时重新补发。
     * 画面只罩在打它的人身上，跑出 48 格回了基地的人不该一直挂着这层颜色（batch13.md §5.3.5）。
     */
    @Override
    public void onFighterLeft(UUID player, @Nullable ServerPlayer online) {
        fxSeen.remove(player);
        if (online != null && !online.hasDisconnected()) {
            S2CBossFxPacket.send(online, S2CBossFxPacket.of(S2CBossFxPacket.PARADOX, this, 0F, (float) phase().color()));
        }
    }

    /** 已经收到当前这一相画面的人数（GameTest 用） */
    public int paradoxRecipients() {
        return fxSeen.size();
    }

    private static float[] rgb(int color) {
        return new float[]{((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F};
    }

    // ------------------------------------------------------------------
    // 受伤、攻击
    // ------------------------------------------------------------------

    /** 换相、散去途中打不动（{@code /kill}、掉出世界照常；玩家打出来的「真伤」一样打不动，{@link BossRules#unblockable}） */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (!BossRules.unblockable(this, source) && (phaseShifting() || dissipating())) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    /** 有心智钉立着时挨打乘 spike_damage_taken；被抓住的人打实一下就挣开 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !BossRules.unblockable(this, source) && skills().count(MatrixShadeSkills.KIND_SPIKE) > 0) {
            amount *= (float) ECMatrixShade.Config.values().skills().spikeDamageTaken();
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof ServerPlayer player) {
            shadeSkills.breakGrip(player);
        }
        return hurt;
    }

    /** 双拳：左右交替出拳（伤害是攻击属性，吃护甲） */
    @Override
    public boolean doHurtTarget(Entity target) {
        anims.play(this, leftFist ? ANIM_FIST_LEFT : ANIM_FIST_RIGHT);
        leftFist = !leftFist;
        return super.doHurtTarget(target);
    }

    /**
     * 只有契约还开着、没在散去的那只打倒了才给东西（掉落、经验、首杀、多给的结晶）：刷怪蛋 / 指令放出来的没有契约；
     * 契约已经按失败退过的（召唤者换了维度、下线……）正在散去，这时再被弄死也只是没了，不能退还和掉落两头都拿。
     */
    @Override
    protected boolean rewardsAllowed() {
        return bound && !dissipating() && level() instanceof ServerLevel server && MatrixShadeContracts.isOpen(server, getUUID());
    }

    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        if (wasDead || !dead || !(level() instanceof ServerLevel server)) {
            return;
        }
        shadeSkills.cancelAll();
        sendParadox(0F, phase().color());
        if (!defeatedByPlayers()) {
            // 不是参战的人打倒的（/kill、掉出世界，刚有人打过也一样）：照失败退还；契约已经结掉的什么都不做
            MatrixShadeContracts.fail(server, getUUID(), MatrixShadeContracts.Failure.NO_PLAYER_KILL);
        }
    }

    /** 被别的东西直接删掉（清理实体的模组、指令）也按失败退还；卸载不算 */
    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (level() instanceof ServerLevel server) {
            shadeSkills.cancelAll();
            RemovalReason reason = getRemovalReason();
            if (bound && reason != null && reason.shouldDestroy()) {
                MatrixShadeContracts.fail(server, getUUID(), MatrixShadeContracts.Failure.VANISHED);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    // ------------------------------------------------------------------
    // 声音
    // ------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ECMatrixShade.SOUND_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECMatrixShade.SOUND_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECMatrixShade.SOUND_DEATH.get();
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_ABSORBED, absorbed);
        if (summoner != null) {
            tag.putUUID(TAG_SUMMONER, summoner);
        }
        if (pentacle != null) {
            tag.put(TAG_PENTACLE, NbtUtils.writeBlockPos(pentacle));
        }
        if (home != null) {
            tag.put(TAG_HOME, NbtUtils.writeBlockPos(home));
        }
        tag.putBoolean(TAG_BOUND, bound);
        tag.putInt(TAG_FIGHT, fightTicks);
        tag.putByte(TAG_PHASE, (byte) phase().ordinal());
        tag.putInt(TAG_SHIFT, phaseShift);
        tag.putInt(TAG_DISSIPATE, dissipate);
    }

    /** 读档：生命、属性由父类原样读回（不按 a 重设）；技能实体不存盘，读回来的没有 */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        absorbed = Math.max(0, tag.getInt(TAG_ABSORBED));
        summoner = tag.hasUUID(TAG_SUMMONER) ? tag.getUUID(TAG_SUMMONER) : null;
        pentacle = tag.contains(TAG_PENTACLE, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_PENTACLE)) : null;
        home = tag.contains(TAG_HOME, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_HOME)) : pentacle;
        bound = tag.getBoolean(TAG_BOUND);
        fightTicks = Math.max(0, tag.getInt(TAG_FIGHT));
        entityData.set(DATA_PHASE, (byte) Phase.byId(tag.getByte(TAG_PHASE)).ordinal());
        phaseShift = Math.max(0, tag.getInt(TAG_SHIFT));
        dissipate = tag.contains(TAG_DISSIPATE, Tag.TAG_ANY_NUMERIC) ? Math.max(-1, tag.getInt(TAG_DISSIPATE)) : -1;
        setFlag(FLAG_SHIFTING, phaseShift > 0);
        setFlag(FLAG_DISSIPATING, dissipate >= 0);
        setFlag(FLAG_ENRAGED, fightTicks >= ECMatrixShade.Config.values().fight().enrageTicks());
        fxSeen.clear();
    }

    // ------------------------------------------------------------------
    // AI
    // ------------------------------------------------------------------

    /** 近身双拳：蓄招、换相、散去途中不打 */
    private static final class ShadeMeleeGoal extends MeleeAttackGoal {

        private final MatrixShadeEntity shade;

        ShadeMeleeGoal(MatrixShadeEntity shade) {
            super(shade, 1D, true);
            this.shade = shade;
        }

        private boolean free() {
            return !shade.dissipating() && !shade.phaseShifting() && !shade.shadeSkills.casting();
        }

        @Override
        public boolean canUse() {
            return free() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return free() && super.canContinueToUse();
        }
    }
}
