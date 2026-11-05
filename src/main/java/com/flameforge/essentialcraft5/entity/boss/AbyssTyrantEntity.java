package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECSunkenDistrict;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 渊潮领主 {@code abyss_tyrant}（第十三批 13e-S2，batch13.md §5.3.4；MS §2.4.6）：渊鳗放大 2.2 倍的首领，由沉没城区穹顶中央的沉城祭坛召出来
 * （{@code block/SunkenAltarBlock}），在穹顶外的水里游；玩家在穹顶边缘或下水打它。
 * <ul>
 *   <li><b>首领档</b>（{@link BossTier#BOSS}）：只吃参战真玩家的伤害、不是近战的打折、不吃状态效果、不受摔落 / 窒息 / 溺水（{@link BossRules}）；
 *       单次封顶 {@code per_hit_cap}，没有每秒桶。生命、护甲、咬的伤害在召出来时按配置设成基础值（{@link #applyConfiguredAttributes}）。</li>
 *   <li><b>场地</b>：拉回 / 脱战从祭坛量（{@link #arenaFloor} = 祭坛那一格，落点是祭坛顶上、穹顶里的空地）：离祭坛超过 {@code pull_range}
 *       按被拉的人探测落点权限、拽回穹顶，超过 {@code escape_range}（上岸跑远了）脱战；名单空了 600 刻散去。它自己离召出来的位置
 *       {@value #HOME_RANGE} 格以外、或离祭坛超过 {@link #leashRange}（{@code pull_range} − {@value #LEASH_MARGIN}）就游回去，不追出场地，
 *       贴着它打的人不会因为它游远了被拽回穹顶。</li>
 *   <li><b>水中移动</b>照渊鳗（{@link SmoothSwimmingMoveControl} + {@link WaterBoundPathNavigation}，能在水下呼吸、不被水流推）；只盯在水里的玩家
 *       （或打了它的），自己在水里才咬，一口 {@code bite_damage}（普通近战，吃护甲）。</li>
 *   <li><b>漩涡</b>（{@link #tickVortex}）：每 {@code vortex_interval} 刻一次、持续 {@code vortex_ticks} 刻：{@code vortex_radius} 格内、
 *       <b>在水里</b>的参战者每刻往它那里加 {@code vortex_pull} 的速度（改速度，不传送）、空气值每刻多扣一点（溺水速度 ×2；水下呼吸、潮涌能量、
 *       无敌状态不扣，水下呼吸附魔照原版按几率抵掉）。穹顶里是空气，站在里面的人不受影响；路过的、不在名单里的人也不受影响。不改方块、不生成水流。</li>
 *   <li><b>掉落</b>（{@code entities/abyss_tyrant}，每个池 {@code killed_by_player}）：每名打出 ≥ 10% 伤害的参战者一份进背包；
 *       <b>每名玩家每游戏日只领一份</b>（{@code daily_reward_limit}，不分哪座沉城）：分账前查玩家数据 {@code boss_daily} 的
 *       {@value #DAILY_KEY} 键（{@link BossDaily}：主世界日序号 + gameTime），今天领过的跳过、聊天栏说一句，
 *       其余的照常分、记上这一刻（{@code /time} 往回拨不多给，锁了日夜循环也是满 24 000 刻 gameTime 算新的一天）。首杀和异兽录 {@code guide/bestiary/abyss_tyrant} 只给真分到的人（{@link #selectRewardWinners}）。</li>
 * </ul>
 * 刷怪蛋放出来的没有祭坛：拉回落点按首领脚下找（{@link BossRoster#pullFloor}），也不游回去；打倒照样掉东西（每日一份照样算）。
 * 注册在 {@code registry/ECSunkenDistrict}；模型复用渊鳗的几何（{@code client/model/mob/AbyssTyrantModel}）。
 */
public class AbyssTyrantEntity extends ECBossMonster {

    public static final int ANIM_BITE = 0;
    public static final int ANIM_VORTEX = 1;

    /** 玩家数据 {@code boss_daily} 里记这只首领的键（实体类型 id） */
    public static final String DAILY_KEY = EssentialCraft5.MODID + ":abyss_tyrant";

    /**
     * 场地：离祭坛超过这么远拽回（配置 {@code pull_range} 的默认值）。要包得住领主自己的活动范围（{@link #leashRange}，默认 22）
     * 加上咬得到它的距离，不然下水贴着它打的人一游出去就被拽回穹顶。
     */
    public static final double PULL_RANGE = 28D;
    /** 离祭坛超过这么远脱战（配置 {@code escape_range} 的默认值） */
    public static final double ESCAPE_RANGE = 56D;
    /** 领主自己的活动范围比拉回距离小这么多（留给追着它打的人） */
    public static final double LEASH_MARGIN = 6D;

    /** 单次封顶（batch13.md §5.3；配置 {@code per_hit_cap} 的默认值） */
    public static final float PER_HIT_CAP = 25F;
    /** 每秒桶（0 = 不用） */
    public static final float PER_SECOND_CAP = 0F;

    /** 离召出来的位置超过这么远就游回去（格） */
    public static final double HOME_RANGE = 14D;
    /** 活动范围的下限：穹顶外壳（半径 9）外再留 3 格 */
    public static final double MIN_LEASH = 12D;
    /** 漩涡里空气值降到这个数就不再多扣（原版按「正好等于 −20」结算溺水伤害，多扣会跳过那一格） */
    public static final int AIR_FLOOR = -19;

    private static final String TAG_ALTAR = "altar";
    private static final String TAG_HOME = "home";
    private static final String TAG_VORTEX_CLOCK = "vortex_clock";
    private static final String TAG_VORTEX_LEFT = "vortex_left";

    public final MobAnimStates anims = new MobAnimStates(2).channel(ANIM_BITE, 1).channel(ANIM_VORTEX, 1);

    @Nullable
    private BlockPos altar;
    @Nullable
    private Vec3 home;
    private int vortexClock;
    private int vortexLeft;

    public AbyssTyrantEntity(EntityType<? extends AbyssTyrantEntity> type, Level level) {
        super(type, level, BossTier.BOSS, BossEvent.BossBarColor.BLUE);
        this.xpReward = 60;
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    /** 属性默认值（生命 / 护甲 / 咬照 batch13.md §5.3.4，击退抗性 1；召出来时再按配置设） */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    /** 生命、护甲、咬的伤害按配置设成基础值、回满（召出来时调） */
    public void applyConfiguredAttributes() {
        ECSunkenDistrict.Config.Values cfg = ECSunkenDistrict.Config.values();
        setBase(Attributes.MAX_HEALTH, cfg.maxHealth());
        setBase(Attributes.ARMOR, cfg.armor());
        setBase(Attributes.ATTACK_DAMAGE, cfg.biteDamage());
        setHealth(getMaxHealth());
    }

    private void setBase(Attribute attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** 祭坛召出来时调：记下祭坛（场地中心）和出生点（游回去的地方） */
    public void prepareSummon(BlockPos altarPos, Vec3 spawn) {
        this.altar = altarPos.immutable();
        this.home = spawn;
    }

    /** 召它的祭坛（刷怪蛋放的是 null） */
    @Nullable
    public BlockPos altar() {
        return altar;
    }

    // ================================================================== 首领通用件

    @Override
    public BossDamageBudget.Limits bossLimits() {
        return new BossDamageBudget.Limits((float) ECSunkenDistrict.Config.values().perHitCap(), PER_SECOND_CAP);
    }

    @Override
    public BossRoster.Settings rosterSettings() {
        ECSunkenDistrict.Config.Values cfg = ECSunkenDistrict.Config.values();
        return BossRoster.Settings.arena(cfg.pullRange(), cfg.escapeRange());
    }

    /** 场地 = 祭坛那一格：拉回落在祭坛顶上（穹顶里的空地），距离从那里量；刷怪蛋放的没有场地 */
    @Override
    @Nullable
    public BlockPos arenaFloor() {
        return altar;
    }

    /**
     * 每人每日一份（见类注释）：今天（主世界日序号）或更晚领过的跳过并在聊天栏说一句，其余的记上今天。{@code daily_reward_limit} 关掉时全给。
     */
    @Override
    protected List<ServerPlayer> selectRewardWinners(List<ServerPlayer> qualified) {
        if (!ECSunkenDistrict.Config.values().dailyLimit() || !(level() instanceof ServerLevel server)) {
            return qualified;
        }
        return claimDaily(qualified, BossDaily.now(server.getServer()));
    }

    /** 按 now 过一遍每日一份：没领过的记上 now、留下；领过的说一句、去掉（GameTest 直接调） */
    public static List<ServerPlayer> claimDaily(List<ServerPlayer> qualified, BossDaily.Stamp now) {
        List<ServerPlayer> out = new ArrayList<>(qualified.size());
        for (ServerPlayer player : qualified) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data == null) {
                continue;
            }
            if (!mayClaim(data, now)) {
                player.sendSystemMessage(Component.translatable("message.essentialcraft5.abyss_tyrant.claimed_today").withStyle(ChatFormatting.GRAY));
                continue;
            }
            BossDaily.mark(data.getBossDaily(), DAILY_KEY, now);
            out.add(player);
        }
        return out;
    }

    /** 这份玩家数据在 now 这一天还能不能领渊潮领主的奖励（怎么算新的一天见 {@link BossDaily}） */
    public static boolean mayClaim(IPlayerData data, BossDaily.Stamp now) {
        return !BossDaily.usedToday(data.getBossDaily(), DAILY_KEY, now);
    }

    // ================================================================== 水中移动

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public MobType getMobType() {
        return MobType.WATER;
    }

    @Override
    public int getMaxHeadXRot() {
        return 1;
    }

    @Override
    public int getMaxHeadYRot() {
        return 1;
    }

    /** 水里照海豚（同渊鳗）：按自己的速度往输入方向推、阻尼 0.9；没目标时缓缓下沉。出了水照普通生物 */
    @Override
    public void travel(Vec3 input) {
        if (isEffectiveAi() && isInWater()) {
            moveRelative(getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9D));
            if (getTarget() == null) {
                setDeltaMovement(getDeltaMovement().add(0D, -0.005D, 0D));
            }
        } else {
            super.travel(input);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new TryFindWaterGoal(this));
        goalSelector.addGoal(1, new ReturnHomeGoal());
        goalSelector.addGoal(2, new BiteGoal());
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0D, 10));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, LivingEntity::isInWater));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        tickVortex();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    // ================================================================== 咬

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            anims.play(this, ANIM_BITE);
            playSound(ECSunkenDistrict.SOUND_TYRANT_BITE.get(), 1.5F, 1F);
        }
        return hit;
    }

    // ================================================================== 漩涡

    /** 正在放漩涡（还剩几刻；0 = 没在放） */
    public int vortexLeft() {
        return vortexLeft;
    }

    /** 立刻起一次漩涡（服务端；测试直接调）：之后 {@code vortex_ticks} 刻里每刻 {@link #applyVortex} */
    public void startVortex() {
        ECSunkenDistrict.Config.Values cfg = ECSunkenDistrict.Config.values();
        vortexLeft = Math.max(1, cfg.vortexTicks());
        vortexClock = 0;
        anims.play(this, ANIM_VORTEX);
        playSound(ECSunkenDistrict.SOUND_TYRANT_VORTEX.get(), 2F, 1F);
    }

    /** 服务端每刻：名单里有人时计时，到点起一次漩涡；正在放就拉一刻 */
    public void tickVortex() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        ECSunkenDistrict.Config.Values cfg = ECSunkenDistrict.Config.values();
        if (vortexLeft > 0) {
            vortexLeft--;
            applyVortex(server, cfg);
            return;
        }
        if (cfg.vortexInterval() <= 0 || bossRoster().participants().isEmpty()) {
            vortexClock = 0;
            return;
        }
        if (++vortexClock >= cfg.vortexInterval()) {
            startVortex();
        }
    }

    /**
     * 漩涡拉一刻（见类注释）：名单里在线、在水里、{@code vortex_radius} 格内的参战者往它那里加速度、空气值多扣一点。返回这一刻拉到了几个人。
     */
    public int applyVortex(ServerLevel server, ECSunkenDistrict.Config.Values cfg) {
        if (tickCount % 5 == 0) {
            server.sendParticles(ParticleTypes.BUBBLE, getX(), getY(0.5D), getZ(), 12, cfg.vortexRadius() * 0.4D, 0.8D, cfg.vortexRadius() * 0.4D, 0.1D);
        }
        int pulled = 0;
        for (ServerPlayer player : bossRoster().online()) {
            if (!vortexAffects(player, cfg.vortexRadius())) {
                continue;
            }
            pullTowards(player, cfg.vortexPull());
            drainAir(player);
            pulled++;
        }
        return pulled;
    }

    /** 这名玩家这一刻受不受漩涡：技能目标（名单里的生存 / 冒险参战者）、人在水里（穹顶里是空气）、离它 radius 格以内 */
    public boolean vortexAffects(ServerPlayer player, double radius) {
        return radius > 0D && isSkillTarget(player) && player.isInWater() && player.distanceToSqr(this) <= radius * radius;
    }

    /** 往它身上加一点速度（只改速度，不传送） */
    private void pullTowards(ServerPlayer player, double speed) {
        if (!(speed > 0D)) {
            return;
        }
        Vec3 toward = position().add(0D, getBbHeight() * 0.5D, 0D).subtract(player.position());
        double length = toward.length();
        if (length < 1.0E-3D) {
            return;
        }
        player.setDeltaMovement(player.getDeltaMovement().add(toward.scale(speed / length)));
        player.hurtMarked = true;
    }

    /**
     * 溺水速度 ×2：眼睛在水里、不能水下呼吸（水下呼吸 / 潮涌能量）、不是无敌状态时空气值多扣一点；水下呼吸附魔照原版按 n / (n + 1) 的几率抵掉。
     * 扣到 {@value #AIR_FLOOR} 就停（溺水伤害照原版在空气值正好到 −20 的那一刻结算）。返回扣了没有。
     */
    public static boolean drainAir(ServerPlayer player) {
        if (!player.isEyeInFluid(FluidTags.WATER) || player.canBreatheUnderwater() || MobEffectUtil.hasWaterBreathing(player) || player.getAbilities().invulnerable) {
            return false;
        }
        int air = player.getAirSupply();
        if (air <= AIR_FLOOR) {
            return false;
        }
        int respiration = EnchantmentHelper.getRespiration(player);
        if (respiration > 0 && player.getRandom().nextInt(respiration + 1) > 0) {
            return false;
        }
        player.setAirSupply(air - 1);
        return true;
    }

    // ================================================================== 存档、声音

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (altar != null) {
            tag.put(TAG_ALTAR, NbtUtils.writeBlockPos(altar));
        }
        if (home != null) {
            CompoundTag at = new CompoundTag();
            at.putDouble("x", home.x);
            at.putDouble("y", home.y);
            at.putDouble("z", home.z);
            tag.put(TAG_HOME, at);
        }
        tag.putInt(TAG_VORTEX_CLOCK, vortexClock);
        tag.putInt(TAG_VORTEX_LEFT, vortexLeft);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        altar = tag.contains(TAG_ALTAR, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_ALTAR)) : null;
        if (tag.contains(TAG_HOME, Tag.TAG_COMPOUND)) {
            CompoundTag at = tag.getCompound(TAG_HOME);
            home = new Vec3(at.getDouble("x"), at.getDouble("y"), at.getDouble("z"));
        } else {
            home = null;
        }
        vortexClock = Math.max(0, tag.getInt(TAG_VORTEX_CLOCK));
        vortexLeft = Math.max(0, tag.getInt(TAG_VORTEX_LEFT));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ECSunkenDistrict.SOUND_TYRANT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECSunkenDistrict.SOUND_TYRANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECSunkenDistrict.SOUND_TYRANT_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2F;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    // ================================================================== 目标

    /** 只在水里咬（自己出了水就不追着打） */
    private final class BiteGoal extends MeleeAttackGoal {

        BiteGoal() {
            super(AbyssTyrantEntity.this, 1.2D, true);
        }

        @Override
        public boolean canUse() {
            return isInWater() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return isInWater() && super.canContinueToUse();
        }
    }

    /**
     * 自己的活动范围：离场地中心（祭坛顶面中心，和名单量拉回的是同一个点）不超过 {@code pull_range} − {@value #LEASH_MARGIN}，
     * 不小于 {@value #MIN_LEASH}；{@code pull_range} 填 0（不拽）时按 {@code escape_range} 算。
     */
    public static double leashRange(double pullRange, double escapeRange) {
        double base = pullRange > 0D ? pullRange : escapeRange;
        return Math.max(MIN_LEASH, base - LEASH_MARGIN);
    }

    /** 离场地中心超过 {@link #leashRange} 了（没有祭坛的不算） */
    public boolean outsideLeash() {
        if (altar == null) {
            return false;
        }
        ECSunkenDistrict.Config.Values cfg = ECSunkenDistrict.Config.values();
        double leash = leashRange(cfg.pullRange(), cfg.escapeRange());
        return position().distanceToSqr(Vec3.atBottomCenterOf(altar.above())) > leash * leash;
    }

    /**
     * 离出生点超过 {@value #HOME_RANGE} 格、或者离场地中心超过活动范围就游回出生点（不追出场地；刷怪蛋放的没有出生点，不回）。
     * 只按出生点算的话，它离出生点 14 格内随便游，常游到离祭坛 20 多格，追着它打的人一过拉回距离就被拽回穹顶。
     */
    private final class ReturnHomeGoal extends Goal {

        ReturnHomeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (home == null || !isInWater()) {
                return false;
            }
            double toHome = distanceToSqr(home);
            return toHome > HOME_RANGE * HOME_RANGE || (toHome > 4D && outsideLeash());
        }

        @Override
        public boolean canContinueToUse() {
            return home != null && !getNavigation().isDone() && distanceToSqr(home) > 4D;
        }

        @Override
        public void start() {
            if (home != null) {
                getNavigation().moveTo(home.x, home.y, home.z, 1.4D);
            }
        }
    }
}
