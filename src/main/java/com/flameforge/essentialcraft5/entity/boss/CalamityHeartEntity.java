package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.entity.boss.seal.CalamitySeal;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.network.S2CBossFxPacket;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 天灾之心（第十三批 13e-B，batch13.md §5.3.5；MS §2.6.2）：主线第二十三章的终局首领（{@link BossTier#BOSS}），拿天灾信标在海纳尔召出来，
 * 把海纳尔的四种天灾浓缩成一场。召唤、花费、退还见 {@link CalamityHeartSummon}、{@link CalamityHeartContracts}；招式见 {@link CalamityHeartSkills}。
 *
 * <h2>场地</h2>
 * 召唤点那一格是场地地面（{@link #arenaFloor}）：参战者 32~64 格被拽回、64 格外脱战（{@link BossRoster}，拉不回来满 600 刻按脱战移出），
 * 名单空 600 刻、和平难度、召唤者死亡 / 下线 / 换维度、不是参战者打倒的死亡都算没打倒：信标和这次吃掉的暗影核心原样退回，它 200 刻后散去。
 * 它自己不离开场地半径，参战者不许飞（名单压制）。不受重力，按阶段悬在地面上方（护盾期 6 格，破盾、失衡、崩解 3 格）。
 * 场地边界（开始拉回的距离）每 2 秒在名单里的人眼前淡淡描一圈。
 *
 * <h2>开场演出（{@code intro_ticks}，默认 120 刻；tools/plan/calamity_heart_v2.md §4）</h2>
 * 演出期间它和灾柱都打不动、不出招，名单照常收人、照常拉回。按演出总长的比例：0~1/3 场地边界从正北顺时针画一圈、它慢慢升起；
 * 0.10 / 0.17 / 0.23 / 0.30 北东南西四根灾柱依次破土（光柱 + 轻震）；0.40 天色压暗、音乐起、重震；0.50 名字大字（客户端画到演出结束）；
 * 0.73 天灾封印落下。{@code intro_ticks} 为 0 时没有演出，灾柱一起立、封印第一刻就落下。读档接着演（存了走到哪一刻）。
 *
 * <h2>天灾封印（{@link CalamitySeal}）</h2>
 * 封印落下后每刻给名单里在线的人续租约：封着哪几处 = 落下时还立着的灾柱 − 后来倒下的（{@code sealHeld}），重立的两根按 {@code reseal_on_reraise} 加回。
 * 打倒、散去、被删、卸载当场 {@link CalamitySeal#releaseBoss}；有人离开名单当场 {@link CalamitySeal#release}；别的退出路径靠租约 2 秒内自动解开。
 *
 * <h2>三阶段（{@link Phase}）</h2>
 * <ol>
 *   <li><b>护盾期</b>：四根灾柱（{@link CalamityPillarEntity}，东西南北各一根，主灾那根 360 血、其余 240）任一根在，它就打不动；
 *       每根活着的柱子让它能用那一相的招。灾柱第一次掉到一半时放一次「灾柱反噬」；打掉一根放一次那一相的「残响」，并破盾 {@code shield_window_ticks}（20 秒）：
 *       这段时间它降到 3 格、打得动，但打不到崩解线以下（柱子没倒完不许跳过）。灾柱不存盘，剩余血量记在它身上（{@code pillars}），读档后它重新立起来。</li>
 *   <li><b>失衡期</b>（四根都倒了）：先放「四灾归心」（预警期间停在原地），之后降到 3 格，缓慢跟着参战者的重心挪（0.15 格 / 刻），每次用两相、每 15 秒换一对，
 *       冷却 ×0.7；每换到一对，{@code combo_delay_ticks} 后放这一对的合相技（第一对再等归心落下）。</li>
 *   <li><b>崩解</b>（生命 &lt; 20%）：停住、不出招，血条上显示 30 秒倒计时，挨打 ×1.25，每 5 秒一次脉冲（24 格内参战者 6 + 4%，{@code calamity}），
 *       每 {@code collapse_echo_interval}（8 秒）垂死反扑一次（断层 / 棱线齐射交替）。
 *       第一次倒计时走完还没打死：随机重立 2 根灾柱、回到 35%，回到护盾期（<b>只一次</b>）；第二次倒计时走完就一直脉冲，直到被打死。
 *       重立之后没有任何回血来源，所以一定打得完。</li>
 * </ol>
 *
 * <h2>画面（{@link S2CBossFxPacket}，只发参战者，按事件发）</h2>
 * {@link S2CBossFxPacket#SKY_DIM}（场地天色压暗，主灾颜色的边缘晕影；进名单补发、离开名单发 0，客户端按它起停音乐）、
 * {@link S2CBossFxPacket#BLACKOUT}（熄光）、{@link S2CBossFxPacket#COLLAPSE}（崩解）；加强那一轮另有 {@link S2CBossFxPacket#INTRO}（名字大字）、
 * {@link S2CBossFxPacket#BANNER}（阶段转换的横幅，编号 {@code BANNER_*}）、{@link S2CBossFxPacket#SHAKE}（镜头震动，{@code SHAKE_*}）。护眼规矩由客户端的 {@code BossFxClient} 管。
 *
 * <h2>打倒</h2>
 * 掉落按 {@link BossLoot} 每名打出 ≥ 10% 的人一份进背包（{@code entities/calamity_heart}，主灾奖励按 {@code essentialcraft5:calamity_main} 条件）；
 * 每人第一次打倒另给 {@code first_kill_bonus} 个天灾残核；进度 {@code main/calamity_heart} 发给分到掉落的人；
 * 信标变「空」回到召唤者手里，当天记一次（{@link CalamityHeartContracts#defeated}）。刷怪蛋 / 指令放出来的没有召唤契约，打倒了什么都不给。
 */
public class CalamityHeartEntity extends ECBossMonster {

    public static final ResourceLocation MAIN_ADVANCEMENT = EssentialCraft5.id("main/calamity_heart");
    /** 灾柱在技能槽里的种类名 */
    public static final String KIND_PILLAR = "calamity_pillar";
    /** 场地外再多这么远就直接回到中心 */
    private static final double LEASH_SLACK = 8D;
    /** 每多少刻看一次有没有新进名单、还没收到天色画面的人 */
    private static final int FX_CHECK_INTERVAL = 10;
    /** 天色压暗的强度 */
    public static final float SKY_DIM_STRENGTH = 0.6F;
    /** 崩解晕影的强度、颜色 */
    public static final float COLLAPSE_STRENGTH = 0.5F;
    public static final int COLLAPSE_COLOR = 0xB0200A;
    /** 失衡期轮换的几对相（下标也是合相技的编号：0 蚀日、1 坠夜、2 瘴影、3 熔裂） */
    private static final int[][] PAIRS = {{0, 1}, {2, 3}, {1, 2}, {3, 0}};

    /** 一对相的合相技横幅用哪个颜色（这一对里排前面那一相） */
    public static int comboColor(int pair) {
        return Aspect.byId(PAIRS[Math.floorMod(pair, PAIRS.length)][0]).color();
    }

    /** 镜头震动的强度（客户端 × 1.2°）：轻（灾柱破土、倒下）、中（四灾归心落下、崩解开始）、重（开场天色压暗、重立、打倒） */
    public static final float SHAKE_LIGHT = 0.3F;
    public static final float SHAKE_MEDIUM = 0.6F;
    public static final float SHAKE_HEAVY = 1F;
    /** 镜头震动的时长（刻；最长 1 秒） */
    public static final int SHAKE_TICKS = 12;
    public static final int SHAKE_LONG_TICKS = 20;
    /** 开场各步在演出里的位置（占演出总长的比例） */
    private static final double INTRO_RING_END = 1D / 3D;
    private static final double[] INTRO_PILLAR_AT = {0.10D, 1D / 6D, 7D / 30D, 0.30D};
    private static final double INTRO_SKY_AT = 0.40D;
    private static final double INTRO_TITLE_AT = 0.50D;
    private static final double INTRO_SEAL_AT = 11D / 15D;
    /** 开场时它往上升的速度（格 / 刻；平时 0.2） */
    private static final double INTRO_RISE_SPEED = 0.125D;
    /** 场地边界每多少刻淡淡描一圈；开场那一圈、平时那一圈的点距（格） */
    private static final int BOUNDARY_INTERVAL = 40;
    private static final double INTRO_RING_SPACING = 1D;
    private static final double BOUNDARY_SPACING = 2.5D;
    /** 灾柱破土的光柱多高 */
    private static final int PILLAR_BURST_HEIGHT = 14;

    /** 横幅编号（= 客户端那张表的下标，只追加）：灾柱倒下（+ 相的序号） */
    public static final int BANNER_PILLAR = 0;
    public static final int BANNER_UNBALANCED = 4;
    public static final int BANNER_CONVERGE = 5;
    public static final int BANNER_COLLAPSE = 6;
    public static final int BANNER_RERAISE = 7;
    public static final int BANNER_UNRAVELING = 8;
    public static final int BANNER_DEFEATED = 9;
    /** 合相技（+ 对的下标） */
    public static final int BANNER_COMBO = 10;
    /** 打倒那条横幅的颜色 */
    public static final int DEFEATED_COLOR = 0xFFE9A8;

    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(CalamityHeartEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(CalamityHeartEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_MAIN = SynchedEntityData.defineId(CalamityHeartEntity.class, EntityDataSerializers.BYTE);
    private static final byte FLAG_BLACKOUT = 1;
    private static final byte FLAG_DISSIPATING = 2;
    private static final byte FLAG_WINDOW = 4;

    private static final String TAG_BOUND = "bound";
    private static final String TAG_SUMMONER = "summoner";
    private static final String TAG_FLOOR = "floor";
    private static final String TAG_MAIN = "main_calamity";
    private static final String TAG_PILLARS = "pillars";
    private static final String TAG_PHASE = "phase";
    private static final String TAG_WINDOW = "shield_window";
    private static final String TAG_STAGE = "collapse_stage";
    private static final String TAG_COLLAPSE = "collapse_ticks";
    private static final String TAG_DISSIPATE = "dissipate";
    private static final String TAG_FIGHT = "fight_ticks";
    private static final String TAG_COLLAPSE_CLOCK = "collapse_clock";
    private static final String TAG_INTRO_TOTAL = "intro_total";
    private static final String TAG_INTRO = "intro_elapsed";
    private static final String TAG_SEAL = "seal_held";
    private static final String TAG_SEAL_DROPPED = "seal_dropped";

    /** 灾柱被加入事件拦下（插件按类型 / 数量限生物）后隔多少刻再试 */
    public static final int PILLAR_RETRY_TICKS = 100;
    /** 同一根灾柱连着被拦下几次就不再等：有契约的按失败退还并散去，刷怪蛋放的那根算倒了 */
    public static final int PILLAR_MAX_REJECTS = 3;

    /** 三阶段 */
    public enum Phase {
        SHIELD, UNBALANCED, COLLAPSE;

        public static Phase byId(int id) {
            Phase[] values = values();
            return values[Math.max(0, Math.min(values.length - 1, id))];
        }
    }

    /** 四相（= 海纳尔的四种天灾），下标也是灾柱的位置：0 北、1 东、2 南、3 西 */
    public enum Aspect {
        SUN_ARRAY(HoannaEvent.SUN_ARRAY, 0xFFD27A, 0, -1),
        FUMES(HoannaEvent.FUMES, 0xA8C83A, 1, 0),
        DARKNESS(HoannaEvent.DARKNESS, 0x4A1E7A, 0, 1),
        EARTHQUAKE(HoannaEvent.EARTHQUAKE, 0x8A3E22, -1, 0);

        private final HoannaEvent event;
        private final int color;
        private final int dx;
        private final int dz;

        Aspect(HoannaEvent event, int color, int dx, int dz) {
            this.event = event;
            this.color = color;
            this.dx = dx;
            this.dz = dz;
        }

        public HoannaEvent event() {
            return event;
        }

        /** 这一相的颜色（灾柱、晕影、粒子） */
        public int color() {
            return color;
        }

        /** 这一相的灾柱在召唤点哪边（单位向量） */
        public int dx() {
            return dx;
        }

        public int dz() {
            return dz;
        }

        /** 小写名（同天灾的名字：sun_array / fumes / darkness / earthquake） */
        public String key() {
            return event.key();
        }

        public static Aspect byId(int id) {
            Aspect[] values = values();
            return values[Math.floorMod(id, values.length)];
        }

        /** 天灾 → 相（日阵 / 毒雾 / 黑暗 / 地震；没有、地脉风暴返回 null，由调用方另定） */
        @Nullable
        public static Aspect of(@Nullable HoannaEvent event) {
            for (Aspect aspect : values()) {
                if (aspect.event == event) {
                    return aspect;
                }
            }
            return null;
        }
    }

    private final CalamityHeartSkills heartSkills = new CalamityHeartSkills(this);
    private boolean bound;
    @Nullable
    private UUID summoner;
    @Nullable
    private BlockPos floor;
    private final BlockPos[] pillarPos = new BlockPos[4];
    /** 每根灾柱剩多少血（0 = 倒了 / 没有） */
    private final float[] pillarHealth = new float[4];
    private final CalamityPillarEntity[] pillars = new CalamityPillarEntity[4];
    private int shieldWindow;
    /** 0 = 没崩解过；1 = 第一次倒计时；2 = 重立过；3 = 第二次倒计时；4 = 一直脉冲 */
    private int collapseStage;
    private int collapseTicks;
    /** 崩解里的脉冲计时（每次开始崩解清零、每刻 +1；和 fightTicks 分开，免得同一刻加两次） */
    private int collapseClock;
    private int dissipate = -1;
    private int fightTicks;
    /** 每根灾柱下次重试的 fightTicks、连着被拦下的次数（不存盘，读档后从头算） */
    private final int[] pillarRetryAt = new int[4];
    private final int[] pillarRejects = new int[4];
    private int aspectClock;
    private int aspectPair;
    private final Set<UUID> fxSeen = new HashSet<>();
    private int dissipateSeen = -1;
    /** 开场演出一共几刻（0 = 没有演出）、已经走了几刻（存盘） */
    private int introTotal;
    private int introElapsed;
    /** 天灾封印压着哪几处（{@link CalamitySeal} 的位；存盘）、落下了没有 */
    private int sealHeld;
    private boolean sealDropped;
    /** 刚立起（开场、重立）的灾柱：生成成功时放破土光柱、带升起动画；读档重新立起来的不放（不存盘） */
    private final boolean[] freshPillar = new boolean[4];
    /** 失衡期还有几刻放这一对的合相技（−1 = 这一对放过了 / 不放；不存盘） */
    private int comboIn = -1;

    public CalamityHeartEntity(EntityType<? extends CalamityHeartEntity> type, Level level) {
        super(type, level, BossTier.BOSS, BossEvent.BossBarColor.RED);
        this.xpReward = ECCalamityHeart.Config.values().fight().experience();
        setNoGravity(true);
    }

    /** 属性默认值（生命 2 400、护甲 16、韧性 8、击退抗性 1；calamity_heart_v2.md §2.1）；召出来时按配置再设一次 */
    public static AttributeSupplier.Builder createAttributes() {
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.DEFAULTS.fight();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, f.maxHealth())
                .add(Attributes.ARMOR, f.armor())
                .add(Attributes.ARMOR_TOUGHNESS, f.toughness())
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PHASE, (byte) 0);
        entityData.define(DATA_FLAGS, (byte) 0);
        entityData.define(DATA_MAIN, (byte) 0);
    }

    /** 没有走路的 AI：招式和位置都由 {@link #serverBossStep} 推，只留转头看人 */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 32F));
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    // ------------------------------------------------------------------
    // 召唤时的数值
    // ------------------------------------------------------------------

    /**
     * 召唤代码在加进世界之前调：记下召唤者、场地地面、主灾、四根灾柱的落脚点，按配置设属性（满血）、灾柱血量。
     * 召唤者另由召唤代码在加进世界后放进名单。
     */
    public void prepareSummon(ServerPlayer player, BlockPos floorPos, Aspect main, BlockPos[] pillarFeet) {
        summoner = player.getUUID();
        bound = true;
        setup(floorPos, main, pillarFeet);
    }

    /** 不经召唤的场地（刷怪蛋放的、GameTest 摆的）：没有召唤者、没有契约 */
    public void setupArena(BlockPos floorPos, Aspect main, @Nullable BlockPos[] pillarFeet) {
        setup(floorPos, main, pillarFeet);
    }

    private void setup(BlockPos floorPos, Aspect main, @Nullable BlockPos[] pillarFeet) {
        floor = floorPos.immutable();
        entityData.set(DATA_MAIN, (byte) main.ordinal());
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
        for (int i = 0; i < 4; i++) {
            pillarPos[i] = pillarFeet == null || pillarFeet[i] == null ? null : pillarFeet[i].immutable();
            pillarHealth[i] = (float) (i == main.ordinal() ? f.mainPillarHealth() : f.pillarHealth());
            freshPillar[i] = true;
        }
        introTotal = Math.max(0, ECCalamityHeart.Config.values().fury().introTicks());
        introElapsed = 0;
        sealHeld = 0;
        sealDropped = false;
        applyStats();
    }

    /** 按配置设属性、满血（只在生成时调一次；读档不重设） */
    public void applyStats() {
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
        setBase(Attributes.MAX_HEALTH, f.maxHealth());
        setBase(Attributes.ARMOR, f.armor());
        setBase(Attributes.ARMOR_TOUGHNESS, f.toughness());
        setHealth(getMaxHealth());
        xpReward = f.experience();
    }

    private void setBase(Attribute attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** 刷怪蛋、指令放出来的：没有召唤者、没有契约，场地是出生点下面那一格，主灾随机，灾柱照立（打倒了什么都不给） */
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        if (!bound && floor == null) {
            setup(blockPosition().below(), Aspect.byId(getRandom().nextInt(4)), null);
        }
        return data;
    }

    // ------------------------------------------------------------------
    // 读数
    // ------------------------------------------------------------------

    public boolean bound() {
        return bound;
    }

    @Nullable
    public UUID summoner() {
        return summoner;
    }

    public Phase phase() {
        return Phase.byId(entityData.get(DATA_PHASE));
    }

    /** 主灾（主灾那根灾柱 240 血、主灾奖励看它） */
    public Aspect mainAspect() {
        return Aspect.byId(entityData.get(DATA_MAIN));
    }

    public boolean blackout() {
        return (entityData.get(DATA_FLAGS) & FLAG_BLACKOUT) != 0;
    }

    public boolean dissipating() {
        return (entityData.get(DATA_FLAGS) & FLAG_DISSIPATING) != 0;
    }

    /** 破盾窗口开着（护盾期打掉一根柱子以后） */
    public boolean shieldOpen() {
        return (entityData.get(DATA_FLAGS) & FLAG_WINDOW) != 0;
    }

    public int shieldWindowTicks() {
        return shieldWindow;
    }

    public int collapseStage() {
        return collapseStage;
    }

    public int collapseTicks() {
        return collapseTicks;
    }

    public int dissipateTicks() {
        return dissipate;
    }

    public CalamityHeartSkills heartSkills() {
        return heartSkills;
    }

    /** 第 i 根灾柱剩的血（0 = 倒了） */
    public float pillarHealth(int index) {
        return pillarHealth[index];
    }

    /** 还立着（血 &gt; 0）的灾柱有几根 */
    public int pillarsStanding() {
        int count = 0;
        for (float health : pillarHealth) {
            if (health > 0F) {
                count++;
            }
        }
        return count;
    }

    /** 第 i 根灾柱的实体（没立起来、不在世界里时 null） */
    @Nullable
    public CalamityPillarEntity pillar(int index) {
        CalamityPillarEntity pillar = pillars[index];
        return pillar == null || pillar.isRemoved() ? null : pillar;
    }

    @Nullable
    public BlockPos pillarFeet(int index) {
        return pillarPos[index];
    }

    /** 开场演出还没走完（它和灾柱打不动、不出招） */
    public boolean introActive() {
        return introElapsed < introTotal;
    }

    /** 开场演出走了几刻、一共几刻（GameTest 用） */
    public int introElapsed() {
        return introElapsed;
    }

    public int introTotal() {
        return introTotal;
    }

    /** 只给 GameTest：跳过还没走完的演出（灾柱照常补立、封印照常落下） */
    public void skipIntroForTest() {
        introElapsed = introTotal;
    }

    /** 封印落下了没有 */
    public boolean sealDropped() {
        return sealDropped;
    }

    /** 这一刻封着哪几处（{@link CalamitySeal} 的位：压着的、灾柱还立着的） */
    public int sealMask() {
        return sealDropped ? sealHeld & standingMask() : 0;
    }

    /** 还立着（血 &gt; 0）的灾柱对应的那几处 */
    private int standingMask() {
        int mask = 0;
        for (int i = 0; i < 4; i++) {
            if (pillarHealth[i] > 0F) {
                mask |= CalamitySeal.part(i);
            }
        }
        return mask;
    }

    /** 失衡期还有几刻放合相技（−1 = 不放；GameTest 用） */
    public int comboIn() {
        return comboIn;
    }

    /** 开场各步落在第几刻（演出总长 × 比例） */
    private int introAt(double fraction) {
        return (int) Math.round(introTotal * fraction);
    }

    /** 开始散去的那一刻（本端 tickCount；没在散去时是当前刻） */
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

    /** 场地中心（地面那一格的顶面中心；没有场地时是自己） */
    public Vec3 arenaCenter() {
        return floor != null ? Vec3.atBottomCenterOf(floor.above()) : position();
    }

    /** 地面顶面的 y */
    public double floorTop() {
        return floor != null ? floor.getY() + 1D : getY();
    }

    /** 这一刻能用哪几相：护盾期是灾柱还在的那几相；失衡期是当前这一对；崩解、散去时一相都没有 */
    public List<Aspect> activeAspects() {
        List<Aspect> out = new ArrayList<>(4);
        if (dissipating()) {
            return out;
        }
        switch (phase()) {
            case SHIELD -> {
                for (int i = 0; i < 4; i++) {
                    if (pillarHealth[i] > 0F) {
                        out.add(Aspect.byId(i));
                    }
                }
            }
            case UNBALANCED -> {
                int[] pair = PAIRS[Math.floorMod(aspectPair, PAIRS.length)];
                out.add(Aspect.byId(pair[0]));
                out.add(Aspect.byId(pair[1]));
            }
            default -> {
            }
        }
        return out;
    }

    /** 冷却倍数（失衡期 × {@code unbalanced_cooldown}，默认 0.7） */
    public double cooldownMultiplier() {
        return phase() == Phase.UNBALANCED ? ECCalamityHeart.Config.values().fight().unbalancedCooldown() : 1D;
    }

    private void setFlag(byte flag, boolean on) {
        byte flags = entityData.get(DATA_FLAGS);
        byte next = (byte) (on ? flags | flag : flags & ~flag);
        if (next != flags) {
            entityData.set(DATA_FLAGS, next);
        }
    }

    void setBlackout(boolean on) {
        setFlag(FLAG_BLACKOUT, on);
    }

    private void setPhase(Phase phase) {
        entityData.set(DATA_PHASE, (byte) phase.ordinal());
    }

    // ------------------------------------------------------------------
    // 光源（熄光时拿着它的人看得见本体和暗袭的轨迹）
    // ------------------------------------------------------------------

    /** 是不是光源：方块物品的方块发光度 &gt; 0（火把、灯笼、魔力灯笼……），或者在 {@code essentialcraft5:calamity_light_sources} 里 */
    @SuppressWarnings("deprecation")
    public static boolean isLightSource(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ECCalamityHeart.LIGHT_SOURCES)) {
            return true;
        }
        return stack.getItem() instanceof BlockItem block && block.getBlock().defaultBlockState().getLightEmission() > 0;
    }

    /** 主手或副手拿着光源 */
    public static boolean holdsLight(Player player) {
        return isLightSource(player.getMainHandItem()) || isLightSource(player.getOffhandItem());
    }

    // ------------------------------------------------------------------
    // ECBoss
    // ------------------------------------------------------------------

    @Override
    public BossDamageBudget.Limits bossLimits() {
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
        return BossDamageBudget.Limits.of(f.perHit(), f.perSecond());
    }

    /** 平时 32~64 格拉回、名单空了散去、压飞行；散去途中不再拉人 */
    @Override
    public BossRoster.Settings rosterSettings() {
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
        BossRoster.Settings arena = BossRoster.Settings.arena(f.pullRange(), f.escapeRange());
        if (dissipating()) {
            return new BossRoster.Settings(arena.engageRange(), 0D, arena.escapeRange(), false, false);
        }
        return arena;
    }

    @Override
    public @Nullable BlockPos arenaFloor() {
        return floor;
    }

    /** 名单空了到点、和平难度：没打倒，信标和暗影核心原样退回，200 刻后散去 */
    @Override
    public void endBoss(BossEnd reason) {
        if (level() instanceof ServerLevel server) {
            CalamityHeartContracts.fail(server, getUUID(), reason == BossEnd.PEACEFUL
                    ? CalamityHeartContracts.Failure.PEACEFUL : CalamityHeartContracts.Failure.ABANDONED);
        }
        beginDissipate();
    }

    /** 打倒：契约结清（信标变空回到召唤者手里、当天记一次），给分到掉落的人发主线进度 */
    @Override
    public void onBossDefeated(List<ServerPlayer> winners) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        CalamityHeartContracts.defeated(server, getUUID());
        for (ServerPlayer player : winners) {
            BossLoot.award(player, MAIN_ADVANCEMENT);
        }
        Set<ServerPlayer> told = new LinkedHashSet<>(winners);
        told.addAll(bossRoster().online());
        for (ServerPlayer player : told) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.calamity_heart.defeated"), false);
        }
        S2CBossFxPacket.send(told, S2CBossFxPacket.of(S2CBossFxPacket.BANNER, this, BANNER_DEFEATED, DEFEATED_COLOR));
        S2CBossFxPacket.send(told, S2CBossFxPacket.of(S2CBossFxPacket.SHAKE, this, SHAKE_HEAVY, SHAKE_LONG_TICKS));
    }

    /**
     * 掉落：每名分得到的人一份（{@link BossLoot}），每人第一次打倒另给 {@code first_kill_bonus} 个天灾残核
     * （首杀在这之后才由 {@link BossLoot#recordVictory} 记上，这里先看还没记过的人）。原版只在 doMobLoot 开着时调这里，关掉掉落时也不多给。
     */
    @Override
    protected void dropFromLootTable(DamageSource source, boolean byPlayer) {
        List<ServerPlayer> winners = BossLoot.winners(this);
        List<ServerPlayer> first = new ArrayList<>();
        String typeId = EntityType.getKey(getType()).toString();
        for (ServerPlayer player : winners) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null && !data.getBossFirstKills().contains(typeId)) {
                first.add(player);
            }
        }
        super.dropFromLootTable(source, byPlayer);
        int bonus = ECCalamityHeart.Config.values().summon().firstKillBonus();
        if (byPlayer && bonus > 0) {
            for (ServerPlayer player : first) {
                ECItemEntities.giveOrDrop(player, new ItemStack(ECCalamityHeart.CALAMITY_REMNANT.get(), bonus));
            }
        }
    }

    /** 只有召唤契约还开着、没在散去的那只打倒了才给东西（刷怪蛋 / 指令放的、已经按失败退过的不给） */
    @Override
    protected boolean rewardsAllowed() {
        return bound && !dissipating() && level() instanceof ServerLevel server && CalamityHeartContracts.isOpen(server, getUUID());
    }

    // ------------------------------------------------------------------
    // 每刻
    // ------------------------------------------------------------------

    /** 名单那部分之后接自己的：散去、契约检查、灾柱、阶段、招式、位置（GameTest 对 NoAI 的直接调） */
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
        if (bound && !CalamityHeartContracts.isOpen(server, getUUID())) {
            // 卸载期间已经按失败退过了：只散去，不再退
            beginDissipate();
            return;
        }
        if (floor == null) {
            floor = blockPosition().below();
        }
        fightTicks++;
        if (introActive()) {
            tickIntro(server);
        } else if (tickCount % BOUNDARY_INTERVAL == 0) {
            boundaryArc(server, 0D, 1D, 0.9F, BOUNDARY_SPACING);
        }
        syncPillars(server);
        if (!sealDropped && introElapsed >= introAt(INTRO_SEAL_AT)) {
            dropSeal(server);
        }
        holdSeal(server);
        if (tickCount % FX_CHECK_INTERVAL == 0) {
            sendSkyToNewcomers();
        }
        if (collapsing()) {
            tickCollapse(server);
        } else {
            tickPhases(server);
            if (!introActive()) {
                heartSkills.tick(server);
            }
        }
        hover(server);
        if (tickCount % 4 == 0) {
            float[] rgb = rgb(mainAspect().color());
            server.sendParticles(new DustParticleOptions(new Vector3f(rgb[0], rgb[1], rgb[2]), 1.4F),
                    getX(), getY() + getBbHeight() * 0.5D, getZ(), 2, 0.6D, 0.8D, 0.6D, 0D);
        }
    }

    /** 正在崩解（两次倒计时里，或者一直脉冲） */
    public boolean collapsing() {
        return collapseStage == 1 || collapseStage == 3 || collapseStage == 4;
    }

    /** 护盾期 / 失衡期之间切换、破盾窗口倒数、失衡期换相、掉到崩解线开始崩解（GameTest 直接调） */
    public void tickPhases(ServerLevel server) {
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
        if (shieldWindow > 0 && --shieldWindow == 0) {
            setFlag(FLAG_WINDOW, false);
            tell("message.essentialcraft5.calamity_heart.shield_up");
        }
        boolean standing = pillarsStanding() > 0;
        if (standing && phase() != Phase.SHIELD) {
            setPhase(Phase.SHIELD);
        } else if (!standing && phase() == Phase.SHIELD) {
            setPhase(Phase.UNBALANCED);
            shieldWindow = 0;
            setFlag(FLAG_WINDOW, false);
            aspectClock = 0;
            aspectPair = getRandom().nextInt(PAIRS.length);
            heartSkills.cancelCast();
            ECCalamityHeart.Config.Fury u = ECCalamityHeart.Config.values().fury();
            boolean converged = heartSkills.converge(server);
            banner(converged ? BANNER_CONVERGE : BANNER_UNBALANCED, mainAspect().color());
            comboIn = u.comboDelayTicks() > 0 ? u.comboDelayTicks() + (converged ? Math.max(1, u.convergeWarnTicks()) : 0) : -1;
        }
        if (phase() == Phase.UNBALANCED) {
            if (++aspectClock >= Math.max(20, f.aspectSwapTicks())) {
                aspectClock = 0;
                aspectPair = (aspectPair + 1) % PAIRS.length;
                int delay = ECCalamityHeart.Config.values().fury().comboDelayTicks();
                comboIn = delay > 0 ? delay : -1;
            }
            if (comboIn > 0 && --comboIn == 0) {
                comboIn = -1;
                int pair = Math.floorMod(aspectPair, PAIRS.length);
                heartSkills.combo(server, pair);
                banner(BANNER_COMBO + pair, comboColor(pair));
            }
            if (getHealth() < getMaxHealth() * f.collapseThreshold() && (collapseStage == 0 || collapseStage == 2)) {
                startCollapse(server);
            }
        }
    }

    /** 开始崩解：停住、不出招，30 秒倒计时（第一次是阶段 1、重立后再来是阶段 3） */
    public void startCollapse(ServerLevel server) {
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
        collapseStage = collapseStage == 0 ? 1 : 3;
        collapseTicks = Math.max(1, f.collapseTicks());
        collapseClock = 0;
        setPhase(Phase.COLLAPSE);
        heartSkills.cancelAll(server);
        setDeltaMovement(Vec3.ZERO);
        server.playSound(null, getX(), getY(), getZ(), ECCalamityHeart.SOUND_COLLAPSE.get(), SoundSource.HOSTILE, 3F, 0.7F);
        S2CBossFxPacket.send(bossRoster().online(), S2CBossFxPacket.of(S2CBossFxPacket.COLLAPSE, this, COLLAPSE_STRENGTH, (float) COLLAPSE_COLOR));
        banner(BANNER_COLLAPSE, COLLAPSE_COLOR);
        shake(SHAKE_MEDIUM, SHAKE_TICKS);
        updateBarName();
    }

    /** 崩解的一刻（GameTest 直接调）：倒计时、脉冲、垂死反扑、血条倒计时；走完第一次重立，走完第二次一直脉冲 */
    public void tickCollapse(ServerLevel server) {
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
        int interval = Math.max(1, f.pulseInterval());
        int echo = ECCalamityHeart.Config.values().fury().collapseEchoInterval();
        if (collapseTicks > 0) {
            collapseTicks--;
        }
        heartSkills.tickEffects(server);
        if (dissipating()) {
            return;
        }
        if (++collapseClock % interval == 0) {
            pulse(server);
            if (dissipating()) {
                // 这一下打死了召唤者：契约已按失败退还、开始散去，不再往下重立灾柱
                return;
            }
        }
        if (echo > 0 && collapseClock % echo == 0) {
            heartSkills.deathThroe(server, collapseClock / echo - 1);
        }
        if (tickCount % 20 == 0) {
            updateBarName();
        }
        if (collapseTicks > 0 || collapseStage == 4) {
            return;
        }
        if (collapseStage == 1) {
            reraise(server);
        } else if (collapseStage == 3) {
            collapseStage = 4;
            banner(BANNER_UNRAVELING, COLLAPSE_COLOR);
            updateBarName();
        }
    }

    /** 崩解的一次脉冲：pulse_range 格内的参战者 6 + 4%（{@code calamity}）；返回打中了几人 */
    public int pulse(ServerLevel server) {
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
        server.playSound(null, getX(), getY(), getZ(), ECCalamityHeart.SOUND_PULSE.get(), SoundSource.HOSTILE, 2F, 0.6F);
        CalamityHeartSkills.ring(server, new Vec3(getX(), floorTop(), getZ()), Math.min(f.pulseRange(), 12D), ParticleTypes.FLAME, 32);
        int hit = 0;
        for (ServerPlayer player : heartSkills.targets(f.pulseRange())) {
            if (CalamityHeartSkills.hit(player, ECDamageTypes.calamity(this), f.pulseDamage(), f.pulsePercent())) {
                hit++;
            }
        }
        return hit;
    }

    /** 第一次倒计时走完：随机重立几根灾柱、回到 35%，回到护盾期（只一次） */
    public void reraise(ServerLevel server) {
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
        collapseStage = 2;
        collapseTicks = 0;
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(order, new Random(getRandom().nextLong()));
        int raise = Math.max(0, Math.min(4, f.reraisePillars()));
        boolean reseal = sealDropped && ECCalamityHeart.Config.values().fury().seal() && ECCalamityHeart.Config.values().fury().resealOnReraise();
        for (int i = 0; i < raise; i++) {
            int index = order.get(i);
            pillarHealth[index] = (float) (index == mainAspect().ordinal() ? f.mainPillarHealth() : f.pillarHealth());
            freshPillar[index] = true;
            if (reseal) {
                sealHeld |= CalamitySeal.part(index);
            }
        }
        setHealthUnguarded((float) Math.max(1D, getMaxHealth() * f.reraiseHealth()));
        setPhase(raise > 0 ? Phase.SHIELD : Phase.UNBALANCED);
        shieldWindow = 0;
        setFlag(FLAG_WINDOW, false);
        syncPillars(server);
        S2CBossFxPacket.send(bossRoster().online(), S2CBossFxPacket.of(S2CBossFxPacket.COLLAPSE, this, 0F, (float) COLLAPSE_COLOR));
        server.playSound(null, getX(), getY(), getZ(), ECCalamityHeart.SOUND_ECHO.get(), SoundSource.HOSTILE, 3F, 0.8F);
        banner(BANNER_RERAISE, mainAspect().color());
        shake(SHAKE_HEAVY, SHAKE_LONG_TICKS);
        updateBarName();
    }

    /** 血条名字：崩解倒计时时带秒数，一直脉冲时另一句，别的时候是本名 */
    private void updateBarName() {
        Component name;
        if (collapseStage == 1 || collapseStage == 3) {
            name = Component.translatable("boss.essentialcraft5.calamity_heart.collapse", getDisplayName(), Math.max(0, (collapseTicks + 19) / 20));
        } else if (collapseStage == 4) {
            name = Component.translatable("boss.essentialcraft5.calamity_heart.unraveling", getDisplayName());
        } else {
            name = getDisplayName();
        }
        bossRoster().bar().setName(name);
    }

    /** 立着的（血 &gt; 0）灾柱缺了就补（读档、区块重载），倒了的收掉；读回每根的血量 */
    public void syncPillars(ServerLevel server) {
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
        for (int i = 0; i < 4; i++) {
            CalamityPillarEntity pillar = pillars[i];
            if (pillarHealth[i] <= 0F) {
                if (pillar != null && !pillar.isRemoved()) {
                    pillar.discard();
                }
                pillars[i] = null;
                continue;
            }
            if (pillar != null && !pillar.isRemoved() && pillar.isAlive()) {
                pillarHealth[i] = Math.max(0.01F, pillar.getHealth());
                continue;
            }
            BlockPos feet = pillarPos[i];
            if (feet == null) {
                feet = groundFor(server, i);
                if (feet == null) {
                    continue;
                }
                pillarPos[i] = feet;
            }
            if (!ECLevels.isFullyLoaded(server, feet) || fightTicks < pillarRetryAt[i]) {
                continue;
            }
            CalamityPillarEntity spawned = ECCalamityHeart.CALAMITY_PILLAR.get().create(server);
            if (spawned == null) {
                continue;
            }
            Aspect aspect = Aspect.byId(i);
            boolean main = aspect == mainAspect();
            spawned.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, 0F, 0F);
            spawned.bind(this, i, aspect, main, main ? f.mainPillarHealth() : f.pillarHealth(), pillarHealth[i]);
            boolean fresh = freshPillar[i];
            // 开场时照样当场立（召唤要确认四根都立得起来），先埋在地下，到这一根的时刻再破土（tickIntro 放光柱）
            int delay = fresh && introActive() ? Math.max(0, introAt(INTRO_PILLAR_AT[i]) - introElapsed) : 0;
            if (fresh) {
                spawned.markRising(server.getGameTime() + delay);
            }
            if (server.addFreshEntity(spawned) && !spawned.isRemoved()) {
                pillars[i] = skills().track(KIND_PILLAR, spawned);
                pillarRejects[i] = 0;
                pillarRetryAt[i] = 0;
                if (fresh) {
                    freshPillar[i] = false;
                    if (delay == 0) {
                        pillarBurst(server, spawned);
                    }
                }
            } else if (pillarRejected(server, i)) {
                return;
            }
        }
    }

    /**
     * 第 i 根灾柱的加入事件被取消了（或者加进去就被删掉）：隔 {@value #PILLAR_RETRY_TICKS} 刻再试，免得每刻都给插件发一次生成事件；
     * 连着 {@value #PILLAR_MAX_REJECTS} 次都立不起来，不让它卡在「柱子有血、却没有实体可打」的永久护盾里：
     * 有契约的按 {@code PILLAR_BLOCKED} 失败退还（信标、暗影核心原样还），刷怪蛋放的把那根记成倒了（不放残响）。
     * 返回 true = 已经开始散去，调用方别再往下立。
     */
    private boolean pillarRejected(ServerLevel server, int i) {
        pillarRetryAt[i] = fightTicks + PILLAR_RETRY_TICKS;
        if (++pillarRejects[i] < PILLAR_MAX_REJECTS) {
            return false;
        }
        pillarRejects[i] = 0;
        pillarRetryAt[i] = 0;
        if (bound) {
            if (!CalamityHeartContracts.fail(server, getUUID(), CalamityHeartContracts.Failure.PILLAR_BLOCKED)) {
                beginDissipate();
            }
            return true;
        }
        pillarHealth[i] = 0F;
        return false;
    }

    /** 打了多少刻（存盘；崩解脉冲另有自己的计时） */
    public int fightTicks() {
        return fightTicks;
    }

    /** 只给 GameTest：把战斗计时往后拨（看灾柱被拦下后的重试间隔，不用真推几百刻招式） */
    public void advanceFightTicksForTest(int ticks) {
        fightTicks += Math.max(0, ticks);
    }

    /** 实际立着（实体在、活着）的灾柱有几根 */
    public int pillarsSpawned() {
        int count = 0;
        for (CalamityPillarEntity pillar : pillars) {
            if (pillar != null && !pillar.isRemoved() && pillar.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /** 没有记下落脚点（刷怪蛋放的）时按高度图找：召唤点往这一相的方向 pillar_offset 格 */
    @Nullable
    private BlockPos groundFor(ServerLevel server, int index) {
        if (floor == null) {
            return null;
        }
        Aspect aspect = Aspect.byId(index);
        int offset = ECCalamityHeart.Config.values().summon().pillarOffset();
        BlockPos column = floor.offset(aspect.dx() * offset, 0, aspect.dz() * offset);
        if (!ECLevels.isFullyLoaded(server, column)) {
            return null;
        }
        int y = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        return new BlockPos(column.getX(), y, column.getZ());
    }

    /** 一根灾柱倒了（它自己死的时候调）：那一相放一次残响、破盾 */
    void onPillarFell(CalamityPillarEntity pillar, int index) {
        if (index < 0 || index > 3 || pillarHealth[index] <= 0F || !(level() instanceof ServerLevel server)) {
            return;
        }
        pillarHealth[index] = 0F;
        pillars[index] = null;
        sealHeld &= ~CalamitySeal.part(index);
        Aspect aspect = Aspect.byId(index);
        server.playSound(null, pillar.getX(), pillar.getY(), pillar.getZ(), ECCalamityHeart.SOUND_PILLAR_BREAK.get(), SoundSource.HOSTILE, 3F, 0.8F);
        if (dissipate >= 0 || collapsing()) {
            return;
        }
        if (pillarsStanding() > 0) {
            // 最后一根倒下时由失衡 / 四灾归心那条横幅接上
            banner(BANNER_PILLAR + index, aspect.color());
        }
        shake(SHAKE_LIGHT, SHAKE_TICKS);
        int window = ECCalamityHeart.Config.values().fight().shieldWindowTicks();
        if (pillarsStanding() > 0 && window > 0) {
            shieldWindow = window;
            setFlag(FLAG_WINDOW, true);
            tell("message.essentialcraft5.calamity_heart.shield_down");
        }
        heartSkills.echo(server, aspect);
    }

    /** 一根灾柱第一次掉到一半（它自己挨打时调）：在它脚下放一次灾柱反噬（开场、崩解、散去时不放） */
    void onPillarHalf(CalamityPillarEntity pillar, int index) {
        if (index < 0 || index > 3 || dissipate >= 0 || collapsing() || introActive() || !(level() instanceof ServerLevel server)) {
            return;
        }
        heartSkills.backlash(server, pillar.position());
    }

    /** 往目标位置慢慢挪：护盾期悬在中心 6 格，破盾、失衡、崩解 3 格；失衡期水平跟着参战者的重心（四灾归心预警时停住），不离开场地；开场时慢慢升起 */
    private void hover(ServerLevel server) {
        ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
        Vec3 center = arenaCenter();
        boolean high = phase() == Phase.SHIELD && !shieldOpen();
        double targetY = floorTop() + (high ? f.shieldHover() : f.lowHover());
        double tx = getX();
        double tz = getZ();
        double speed = 0.2D;
        if (phase() == Phase.SHIELD) {
            tx = center.x;
            tz = center.z;
        } else if (phase() == Phase.UNBALANCED && !heartSkills.converging()) {
            List<ServerPlayer> online = bossRoster().online();
            if (!online.isEmpty()) {
                double sx = 0D;
                double sz = 0D;
                for (ServerPlayer player : online) {
                    sx += player.getX();
                    sz += player.getZ();
                }
                tx = sx / online.size();
                tz = sz / online.size();
            }
            speed = f.followSpeed();
        }
        double radius = f.arenaRadius();
        double ox = tx - center.x;
        double oz = tz - center.z;
        double dist = Math.hypot(ox, oz);
        if (dist > radius && dist > 1.0E-3D) {
            tx = center.x + ox / dist * radius;
            tz = center.z + oz / dist * radius;
        }
        Vec3 delta = new Vec3(tx - getX(), 0D, tz - getZ());
        double h = delta.horizontalDistance();
        if (h > speed && h > 1.0E-6D) {
            delta = delta.scale(speed / h);
        }
        double rise = introActive() ? INTRO_RISE_SPEED : 0.2D;
        double dy = Mth.clamp(targetY - getY(), -rise, rise);
        setDeltaMovement(delta.x, dy, delta.z);
        if (position().distanceTo(center) > radius + LEASH_SLACK + f.shieldHover()) {
            Vec3 old = position();
            moveTo(center.x, targetY, center.z, getYRot(), getXRot());
            if (!server.noCollision(this)) {
                moveTo(old.x, old.y, old.z, getYRot(), getXRot());
            }
        }
    }

    // ------------------------------------------------------------------
    // 散去
    // ------------------------------------------------------------------

    /** 没打倒：不再出招、打不动、不拉人，dissipate_ticks 刻后散去。重复调没有副作用 */
    public void beginDissipate() {
        if (dissipate >= 0 || !(level() instanceof ServerLevel server)) {
            return;
        }
        dissipate = Math.max(1, ECCalamityHeart.Config.values().summon().dissipateTicks());
        setFlag(FLAG_DISSIPATING, true);
        setFlag(FLAG_WINDOW, false);
        heartSkills.cancelAll(server);
        skills().discardAll();
        for (int i = 0; i < 4; i++) {
            pillars[i] = null;
        }
        setTarget(null);
        CalamitySeal.releaseBoss(getUUID());
        tell("message.essentialcraft5.calamity_heart.fading");
        clearFx();
    }

    private void tickDissipate(ServerLevel server) {
        setTarget(null);
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % 3 == 0) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.5D, getZ(), 6, 0.6D, 1D, 0.6D, 0.02D);
        }
        if (--dissipate <= 0) {
            dissipate();
        }
    }

    // ------------------------------------------------------------------
    // 画面
    // ------------------------------------------------------------------

    /** 开场演出的一刻（灾柱召唤时就立好、埋在地下，到各自的时刻在这里放破土光柱；封印落下在 {@link #serverBossStep}） */
    private void tickIntro(ServerLevel server) {
        int t = introElapsed++;
        int ringEnd = Math.max(1, introAt(INTRO_RING_END));
        if (t < ringEnd) {
            boundaryArc(server, t / (double) ringEnd, (t + 1) / (double) ringEnd, 1.8F, INTRO_RING_SPACING);
        }
        if (t == 0) {
            server.playSound(null, getX(), getY(), getZ(), ECCalamityHeart.SOUND_QUAKE.get(), SoundSource.HOSTILE, 3F, 0.4F);
        }
        for (int i = 0; i < 4; i++) {
            CalamityPillarEntity pillar = pillar(i);
            if (pillar != null && t + 1 == introAt(INTRO_PILLAR_AT[i])) {
                pillarBurst(server, pillar);
            }
        }
        if (t == introAt(INTRO_SKY_AT)) {
            sendSkyToNewcomers();
            shake(SHAKE_HEAVY, SHAKE_LONG_TICKS);
            server.playSound(null, getX(), getY(), getZ(), ECCalamityHeart.SOUND_ECHO.get(), SoundSource.HOSTILE, 3F, 0.5F);
        }
        int titleAt = introAt(INTRO_TITLE_AT);
        if (t == titleAt) {
            S2CBossFxPacket.send(bossRoster().online(),
                    S2CBossFxPacket.of(S2CBossFxPacket.INTRO, this, mainAspect().ordinal(), Math.max(20, introTotal - titleAt)));
        }
    }

    /**
     * 场地边界（开始拉回的距离）上从正北顺时针、占整圈 from~to 的那一段光点；只发给名单里在线的人（远距离也看得见）。
     * 开场画一圈（每刻一段），平时每 {@value #BOUNDARY_INTERVAL} 刻淡淡描一整圈。
     */
    private void boundaryArc(ServerLevel server, double from, double to, float size, double spacing) {
        List<ServerPlayer> viewers = bossRoster().online();
        if (viewers.isEmpty()) {
            return;
        }
        double radius = ECCalamityHeart.Config.values().fight().pullRange();
        Vec3 center = arenaCenter();
        int total = Math.max(8, (int) Math.ceil(Math.PI * 2D * radius / spacing));
        float[] rgb = rgb(mainAspect().color());
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(rgb[0], rgb[1], rgb[2]), size);
        for (int i = (int) Math.floor(from * total); i < Math.min(total, (int) Math.ceil(to * total)); i++) {
            double angle = Math.PI * 2D * i / total;
            double x = center.x + Math.sin(angle) * radius;
            double z = center.z - Math.cos(angle) * radius;
            for (ServerPlayer viewer : viewers) {
                server.sendParticles(viewer, dust, true, x, center.y + 0.5D, z, 1, 0D, 0.3D, 0D, 0D);
            }
        }
    }

    /** 灾柱破土（开场、重立）：一道相色光柱冲天、轻震 */
    private void pillarBurst(ServerLevel server, CalamityPillarEntity pillar) {
        float[] rgb = rgb(pillar.aspect().color());
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(rgb[0], rgb[1], rgb[2]), 2F);
        for (int dy = 0; dy <= PILLAR_BURST_HEIGHT; dy++) {
            server.sendParticles(dust, pillar.getX(), pillar.getY() + dy, pillar.getZ(), 3, 0.25D, 0.4D, 0.25D, 0D);
            if (dy % 2 == 0) {
                server.sendParticles(ParticleTypes.END_ROD, pillar.getX(), pillar.getY() + dy, pillar.getZ(), 1, 0.1D, 0.3D, 0.1D, 0.05D);
            }
        }
        server.sendParticles(ParticleTypes.LARGE_SMOKE, pillar.getX(), pillar.getY() + 0.5D, pillar.getZ(), 12, 1D, 0.3D, 1D, 0.02D);
        server.playSound(null, pillar.getX(), pillar.getY(), pillar.getZ(), ECCalamityHeart.SOUND_QUAKE.get(), SoundSource.HOSTILE, 2.5F, 0.8F);
        shake(SHAKE_LIGHT, SHAKE_TICKS);
    }

    /** 封印落下：按此刻还立着的灾柱封；配置关掉、注入点没到位时只记一下「落过了」 */
    private void dropSeal(ServerLevel server) {
        sealDropped = true;
        if (!ECCalamityHeart.Config.values().fury().seal() || !CalamitySeal.available()) {
            sealHeld = 0;
            return;
        }
        sealHeld = standingMask();
        if (sealHeld == 0) {
            return;
        }
        Component line = Component.translatable("message.essentialcraft5.calamity_heart.sealed");
        for (ServerPlayer player : bossRoster().online()) {
            // 符文从四面卷到人身上（附魔台那种字符粒子：count 0 时速度就是起点的偏移，飞向这一点）
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2D * i / 24D;
                server.sendParticles(player, ParticleTypes.ENCHANT, true, player.getX(), player.getY() + 1.2D, player.getZ(), 0,
                        Math.cos(angle) * 3D, 0.5D + (i % 3) * 0.5D, Math.sin(angle) * 3D, 1D);
            }
            player.displayClientMessage(line, true);
        }
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.HOSTILE, 3F, 0.5F);
        holdSeal(server);
    }

    /** 每刻给名单里在线的人续封印租约（封印落下以后；全解了也续，客户端照样显示四处已解） */
    private void holdSeal(ServerLevel server) {
        if (!sealDropped || !ECCalamityHeart.Config.values().fury().seal()) {
            return;
        }
        int mask = sealMask();
        long now = CalamitySeal.now(server.getServer());
        for (ServerPlayer player : bossRoster().online()) {
            CalamitySeal.hold(player, getUUID(), mask, now);
        }
    }

    /** 给名单里在线的人放一条横幅（编号见 {@code BANNER_*}，color 是标题的颜色） */
    void banner(int id, int color) {
        S2CBossFxPacket.send(bossRoster().online(), S2CBossFxPacket.of(S2CBossFxPacket.BANNER, this, id, color));
    }

    /** 名单里在线的人镜头震一下（strength 0~1，客户端 × 1.2°；ticks 刻，最长 20） */
    public void shake(float strength, int ticks) {
        S2CBossFxPacket.send(bossRoster().online(), S2CBossFxPacket.of(S2CBossFxPacket.SHAKE, this, strength, Math.min(20, ticks)));
    }

    /** 新进名单（离开时会从记录里删掉）、还没收到天色画面的人补发天色压暗；正在崩解的另补那一层（开场时等到天色压暗那一步） */
    private void sendSkyToNewcomers() {
        if (introActive() && introElapsed <= introAt(INTRO_SKY_AT)) {
            return;
        }
        List<ServerPlayer> fresh = new ArrayList<>();
        for (ServerPlayer player : bossRoster().online()) {
            if (fxSeen.add(player.getUUID())) {
                fresh.add(player);
            }
        }
        if (fresh.isEmpty()) {
            return;
        }
        S2CBossFxPacket.send(fresh, S2CBossFxPacket.of(S2CBossFxPacket.SKY_DIM, this, SKY_DIM_STRENGTH, (float) mainAspect().color()));
        if (collapsing()) {
            S2CBossFxPacket.send(fresh, S2CBossFxPacket.of(S2CBossFxPacket.COLLAPSE, this, COLLAPSE_STRENGTH, (float) COLLAPSE_COLOR));
        }
    }

    /** 已经收到天色画面的人数（GameTest 用） */
    public int skyRecipients() {
        return fxSeen.size();
    }

    /** 名单里在线的人：三层画面全发 0（打倒、散去） */
    private void clearFx() {
        List<ServerPlayer> online = bossRoster().online();
        S2CBossFxPacket.send(online, S2CBossFxPacket.of(S2CBossFxPacket.SKY_DIM, this, 0F, (float) mainAspect().color()));
        S2CBossFxPacket.send(online, S2CBossFxPacket.of(S2CBossFxPacket.BLACKOUT, this, 0F, (float) CalamityHeartSkills.BLACKOUT_COLOR));
        S2CBossFxPacket.send(online, S2CBossFxPacket.of(S2CBossFxPacket.COLLAPSE, this, 0F, (float) COLLAPSE_COLOR));
        fxSeen.clear();
    }

    /** 有人离开了名单：他那几层画面收掉（还在线的单独发 0，客户端也跟着停音乐），从「收到过」里删掉，再进名单时重新补发 */
    @Override
    public void onFighterLeft(UUID player, @Nullable ServerPlayer online) {
        fxSeen.remove(player);
        CalamitySeal.release(getUUID(), player);
        if (online != null && !online.hasDisconnected()) {
            S2CBossFxPacket.send(online, S2CBossFxPacket.of(S2CBossFxPacket.SKY_DIM, this, 0F, (float) mainAspect().color()));
            S2CBossFxPacket.send(online, S2CBossFxPacket.of(S2CBossFxPacket.BLACKOUT, this, 0F, (float) CalamityHeartSkills.BLACKOUT_COLOR));
            S2CBossFxPacket.send(online, S2CBossFxPacket.of(S2CBossFxPacket.COLLAPSE, this, 0F, (float) COLLAPSE_COLOR));
        }
    }

    /** 给名单里在线的人动作栏说一句 */
    void tell(String key) {
        Component line = Component.translatable(key);
        for (ServerPlayer player : bossRoster().online()) {
            player.displayClientMessage(line, true);
        }
    }

    static float[] rgb(int color) {
        return new float[]{((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F};
    }

    // ------------------------------------------------------------------
    // 受伤
    // ------------------------------------------------------------------

    /**
     * 打不动：开场演出里；散去途中；护盾期破盾窗口没开（灾柱还在）。{@code /kill}、掉出世界照常；玩家打出来的「真伤」一样打不动（{@link BossRules#unblockable}）
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (!BossRules.unblockable(this, source) && (introActive() || dissipating() || phase() == Phase.SHIELD && !shieldOpen())) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    /**
     * 绕开伤害流程的掉血（{@link BossHealthGuard}）也守同样的几条：打不动的时候（同 {@link #isInvulnerableTo}）一点不掉；
     * 护盾期的破盾窗口里压不到崩解线以下
     */
    @Override
    public float guardFloor() {
        if (introActive() || dissipating() || phase() == Phase.SHIELD && !shieldOpen()) {
            return Float.POSITIVE_INFINITY;
        }
        if (phase() == Phase.SHIELD) {
            return (float) (getMaxHealth() * ECCalamityHeart.Config.values().fight().collapseThreshold());
        }
        return 0F;
    }

    /** 崩解时挨打 ×{@code collapse_damage_taken}；护盾期的破盾窗口里打不到崩解线以下（柱子没倒完不能跳过失衡期） */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !BossRules.unblockable(this, source)) {
            ECCalamityHeart.Config.Fight f = ECCalamityHeart.Config.values().fight();
            if (phase() == Phase.COLLAPSE) {
                amount *= (float) f.collapseDamageTaken();
            } else if (phase() == Phase.SHIELD) {
                float floorHealth = (float) (getMaxHealth() * f.collapseThreshold());
                amount = Math.min(amount, getHealth() - floorHealth);
                if (!(amount > 0F)) {
                    return false;
                }
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        if (wasDead || !dead || !(level() instanceof ServerLevel server)) {
            return;
        }
        heartSkills.cancelAll(server);
        CalamitySeal.releaseBoss(getUUID());
        clearFx();
        if (!defeatedByPlayers()) {
            // 不是参战的人打倒的（/kill、掉出世界，刚有人打过也一样）：照没打倒退还；契约已经结掉的什么都不做
            CalamityHeartContracts.fail(server, getUUID(), CalamityHeartContracts.Failure.NO_PLAYER_KILL);
        }
    }

    /** 被别的东西直接删掉（清实体的模组、指令）也按没打倒退还；卸载不算 */
    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (level() instanceof ServerLevel server) {
            heartSkills.cancelAll(server);
            CalamitySeal.releaseBoss(getUUID());
            RemovalReason reason = getRemovalReason();
            if (bound && reason != null && reason.shouldDestroy()) {
                CalamityHeartContracts.fail(server, getUUID(), CalamityHeartContracts.Failure.VANISHED);
            }
        }
        for (int i = 0; i < 4; i++) {
            pillars[i] = null;
        }
    }

    // ------------------------------------------------------------------
    // 声音
    // ------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ECCalamityHeart.SOUND_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECCalamityHeart.SOUND_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECCalamityHeart.SOUND_DEATH.get();
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
        tag.putBoolean(TAG_BOUND, bound);
        if (summoner != null) {
            tag.putUUID(TAG_SUMMONER, summoner);
        }
        if (floor != null) {
            tag.put(TAG_FLOOR, NbtUtils.writeBlockPos(floor));
        }
        tag.putString(TAG_MAIN, mainAspect().key());
        ListTag list = new ListTag();
        for (int i = 0; i < 4; i++) {
            CompoundTag row = new CompoundTag();
            row.putFloat("health", pillarHealth[i]);
            if (pillarPos[i] != null) {
                row.put("pos", NbtUtils.writeBlockPos(pillarPos[i]));
            }
            list.add(row);
        }
        tag.put(TAG_PILLARS, list);
        tag.putByte(TAG_PHASE, (byte) phase().ordinal());
        tag.putInt(TAG_WINDOW, shieldWindow);
        tag.putInt(TAG_STAGE, collapseStage);
        tag.putInt(TAG_COLLAPSE, collapseTicks);
        tag.putInt(TAG_DISSIPATE, dissipate);
        tag.putInt(TAG_FIGHT, fightTicks);
        tag.putInt(TAG_COLLAPSE_CLOCK, collapseClock);
        tag.putInt(TAG_INTRO_TOTAL, introTotal);
        tag.putInt(TAG_INTRO, introElapsed);
        tag.putInt(TAG_SEAL, sealHeld);
        tag.putBoolean(TAG_SEAL_DROPPED, sealDropped);
    }

    /** 读档：生命、属性由父类原样读回；灾柱不存盘，按记下的血量在下一刻重新立起来 */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        bound = tag.getBoolean(TAG_BOUND);
        summoner = tag.hasUUID(TAG_SUMMONER) ? tag.getUUID(TAG_SUMMONER) : null;
        floor = tag.contains(TAG_FLOOR, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_FLOOR)) : null;
        Aspect main = Aspect.of(HoannaEvent.byName(tag.getString(TAG_MAIN).toLowerCase(Locale.ROOT)));
        entityData.set(DATA_MAIN, (byte) (main == null ? 0 : main.ordinal()));
        ListTag list = tag.getList(TAG_PILLARS, Tag.TAG_COMPOUND);
        for (int i = 0; i < 4; i++) {
            CompoundTag row = i < list.size() ? list.getCompound(i) : new CompoundTag();
            float health = row.getFloat("health");
            pillarHealth[i] = Float.isFinite(health) && health > 0F ? health : 0F;
            pillarPos[i] = row.contains("pos", Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(row.getCompound("pos")) : null;
            pillars[i] = null;
        }
        setPhase(Phase.byId(tag.getByte(TAG_PHASE)));
        shieldWindow = Math.max(0, tag.getInt(TAG_WINDOW));
        collapseStage = Mth.clamp(tag.getInt(TAG_STAGE), 0, 4);
        collapseTicks = Math.max(0, tag.getInt(TAG_COLLAPSE));
        dissipate = tag.contains(TAG_DISSIPATE, Tag.TAG_ANY_NUMERIC) ? Math.max(-1, tag.getInt(TAG_DISSIPATE)) : -1;
        fightTicks = Math.max(0, tag.getInt(TAG_FIGHT));
        collapseClock = Math.max(0, tag.getInt(TAG_COLLAPSE_CLOCK));
        // 没有这几项的（加强之前存的）当没有演出，封印第一刻按还立着的灾柱落下
        introTotal = Math.max(0, tag.getInt(TAG_INTRO_TOTAL));
        introElapsed = Mth.clamp(tag.getInt(TAG_INTRO), 0, introTotal);
        sealHeld = tag.getInt(TAG_SEAL) & CalamitySeal.ALL;
        sealDropped = tag.getBoolean(TAG_SEAL_DROPPED);
        comboIn = -1;
        setFlag(FLAG_WINDOW, shieldWindow > 0);
        setFlag(FLAG_DISSIPATING, dissipate >= 0);
        setFlag(FLAG_BLACKOUT, false);
        fxSeen.clear();
    }
}
