package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.HologramClient;
import com.flameforge.essentialcraft5.entity.boss.BossDamageBudget;
import com.flameforge.essentialcraft5.entity.boss.BossHealthGuard;
import com.flameforge.essentialcraft5.entity.boss.BossRules;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.teleport.ECTeleport;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 全息投影（原作 {@code EntityHologram}，413 行）：在海纳尔用全息投影台召出来的 Boss。悬在投影台上方 5 格不动，轮流放四种招，
 * 招与招之间的休息期才打得动；打倒它按最后放完的那一招掉一种数学核心和一堆遗物（batch8.md §4.1）。
 *
 * <h2>攻击循环（照原作，h = 生命 / 最大生命）</h2>
 * 休息 {@link #restTicks}（满血 100 刻、见底 20 刻）；休息到 0 随机挑一招放 {@value #ATTACK_TICKS} 刻，出招时放 {@code stop} 音（音高 2），
 * 收招记下这一招（{@link #prevAttackId()}）再放一次（音高 0.5；原作 0.01 会被夹到 0.5）：
 * <ul>
 *   <li>0 加法：剩 {@value #CLONE_SPAWN_TICK} 刻时，每个参战者身边 ±{@value #CLONE_SPREAD} 格找能站的地方生成 {@link #cloneCount} 只玩家克隆体；</li>
 *   <li>1 减法：每个参战者每 {@value #DESTROYER_INTERVAL} 刻一发护甲破坏者（速度 1.5、散布 0.5；原作每刻一发）；</li>
 *   <li>2 乘法：剩余刻数是 {@value #ORBITAL_INTERVAL} 的倍数时（10 发）在随机一名参战者脚下放轨道打击，伤害从 1 起每发翻倍、
 *       封顶 {@code orbital_max_damage}（{@link #strikeDamage}），延迟 {@link #orbitalDelay}（满血 3 秒、见底 1 秒）；</li>
 *   <li>3 除法：剩余刻数是 {@value #DIVIDER_INTERVAL} 的倍数时（5 轮）每轮 {@link #dividerCount} 个二分场落在随机参战者脚下（2 秒后张开）。</li>
 * </ul>
 * 每只 Boss 同时最多 {@code max_clones} 只克隆体、{@code max_armor_destroyers} 发破坏者、{@code max_dividers} 个二分场（配置 {@code general.hologram}）。
 * 轨道打击、二分场是框架的技能实体（{@link OrbitalStrikeEntity}、{@link DividerFieldEntity}），伤害记在 Boss 头上，Boss 死了它们就熄灭；
 * 打谁由 {@link #isSkillTarget} 给：名单里在线的生存 / 冒险参战者，加上敌对生物（跳过谁都不许碰的、{@code #forge:bosses}、全息投影本身）。
 * 村民、宠物、动物、盔甲架、旁观者都不碰，参战者站到邻居的村民旁边也借不了 Boss 的手。Boss 的二分场不做 0 伤害探测（对象只有这两类，领地插件本来不管怪打人）。
 *
 * <h2>受伤（原作 L362~384）</h2>
 * 带 {@code bypasses_invulnerability} 的（{@code /kill}、掉出世界）照常；{@code orbital_strike} 一律拒绝（原作是匿名伤害源恰好打不动，这里写成规则：
 * 别人遥控器的谓词可能放行 Boss）；其余要攻击者是真玩家（假玩家、生物、没有攻击者的都打不动），非创造模式的只在休息期打得动，
 * <b>而且要和它在同一个世界、离它不超过 {@value #PULL_RANGE} 格</b>：在远处打一下就退开的人，技能落下时已经不在名单里，等于白打不挨打
 * （原作只把 24 格内的人自动加进名单，非召唤者在 24 格外射它本来就不挨打；这里连召唤者一起堵上）。创造模式不受这条限制，和「出招时也打得动」一致。
 * 不是近战的（{@code ECDamageKinds.isMelee} 为假：箭、本模组射线枪、爆炸）只算四分之一（原作只认 isProjectile，原作射线枪的伤害源没有真源头、根本打不动它）；
 * 单次封顶 {@code max_damage_per_hit}（D2 = A，默认 40，0 = 不封顶）：这里夹的是传给父类的输入值，最终掉血在 {@link HologramEvents} 的
 * {@code LivingDamageEvent}（LOWEST）里再夹一次——别的模组在 {@code LivingHurtEvent} / {@code LivingDamageEvent} 里再乘一次（暴击、元素加成），
 * 一下也掉不过封顶。<b>生命真的掉了</b>才累计休息期伤害、放受击音效、把攻击者加进名单：
 * 累计在每次出招时重置成 1（原作出招时 {@code damage = 1}），单次 &gt; {@value #EARLY_ATTACK_THRESHOLD} 或累计 &gt; {@value #EARLY_ATTACK_THRESHOLD}
 * 就把休息时间设成 1（马上出招）。原作把这个累计和轨道打击的伤害放在同一个字段里：乘法攻击放完它停在 1 024，接下来那次休息随便打一下就马上出招；
 * 这里拆成两个字段，乘法之后的休息和别的一样能打几下。领地模组、混合端插件拦下的攻击不累计、不进名单。不覆盖 {@code actuallyHurt}（混合端把 Bukkit 事件插在里面）。
 *
 * <h2>参战名单（D1 = A）</h2>
 * 召唤者，加上打出有效伤害（生命真掉了）的生存 / 冒险真玩家；旁观的、路过的不进名单、不被技能打、不被拉回、不锁飞行。
 * 每 {@value #ROSTER_INTERVAL} 刻只遍历名单（按 UUID 从在线玩家里取；原作遍历全服玩家，还会把换了维度的人拽回来）：
 * <ul>
 *   <li>下线、死了、换了维度、改成创造 / 旁观、离它超过 {@code escape_range}（64）格 → 脱战（移出名单，不拽、不扣血）。
 *       死亡由 {@link HologramEvents} 在 {@code LivingDeathEvent} 里当场移出（{@link #forgetFighter}）：开了立即重生时从死到重生只隔 1~3 刻，
 *       重生出来的是同一个 UUID 的新玩家对象，每 {@value #ROSTER_INTERVAL} 刻一次的 isAlive 检查大多会漏掉；这里的检查只是兜底；</li>
 *   <li>{@value #PULL_RANGE}~{@code escape_range} 格 → 拉回投影台原位的地面：{@code ECTeleport.check(被拉的人, …, 投影台位置.below(), Boss)} 先看落点安全，
 *       再按<b>被拉的这个人</b>探测落点的权限（刷怪蛋、/summon 出来的没有投影台，用出生点正下方 {@value #PULL_FLOOR_SEARCH} 格内第一个有碰撞的方块），
 *       再 {@code ECTeleport.teleport}（发可取消的传送事件，混合端插件也拦得住）；传成了扣 5 点（{@code mobAttack(Boss)}）、放铁砧破碎声。
 *       落点不安全（被堵、底下伤人）、他在落点没有权限（含这一刻探测不出结论）或传送被取消时不拉、不扣血，<b>但人留在名单里</b>，招式照样追着他。
 *       投影台是召唤者摆的，可能在他自己领地里一间封死的屋子里：在那里不能动的人不拽进去，不困人（policy §1.1）。
 *       这样拉不回来的时间连着累计（每次检查 +{@value #ROSTER_INTERVAL} 刻，存盘），满 {@code abandon_ticks}（默认 600）按脱战移出、动作栏说一句；
 *       回到 {@value #PULL_RANGE} 格以内或被拽回来就清零。堵住拉回点换不来免费脱战（站在 24 格边上趁休息打一下、退一步就被移出名单），
 *       也不能借它无限期挂着一场战斗（和第十三批首领通用件 {@code BossRoster} 同一口径，batch13.md 13c 审查修复 C2-04 / C2-05、C2 补 5）。
 *       探测走 {@code ECProtection.check} 的缓存和每刻预算（预算按这只 Boss 算）。原作拉到 Boss 本体位置（半空 5 格，落地还要摔一下）；</li>
 *   <li>名单里的生存 / 冒险玩家：本模组给的飞行由 {@code ECFlight} 的压制器暂停（{@link #isEngaged}），别的来源的飞行这里每 {@value #ROSTER_INTERVAL}
 *       刻关一次并同步（原作只改服务端、不同步，客户端另外关掉 24 格内所有非创造玩家的飞行，那段删了）。</li>
 * </ul>
 * 名单空了开始计 {@code abandon_ticks}（600，存盘；区块没加载时实体不 tick，计时也不走），到点消失；<b>只有投影台召出来、没死的</b>才在投影台原位
 * 掉回一个投影台物品（刷怪蛋、/summon 的不掉）；掉回的台子不会消失（用料里有永恒物质和虚空核心，召唤者脱战后去重生、回家换装，回来还在）。
 * 和平难度下按同样的规则直接消失（和平难度怪物伤害是 0，等于白拿核心）。
 * 不会自然消失（原作没覆盖 canDespawn，最近的玩家离开 128 格就被原版清掉，投影台也没了）。
 *
 * <h2>死亡</h2>
 * 只走一次原版掉落：战利品表按最后放完的那一招选四张实体表之一（{@link #lootTableFor}；还没放完过一招就随机，照原作 L60~62），在 Boss 位置掉出，
 * 表里每个池都带 {@code killed_by_player}（{@code /kill} 不掉）。不放箱子（原作把所在格直接换成箱子）。给 {@value #XP_REWARD} 点经验
 * （同凋灵；原作是 0。原版 {@code dropExperience} 只在玩家 100 刻内打过它时掉，{@code /kill} 不掉，召一次要吃掉一个投影台，刷不起来）。给名单里在线、同维度的玩家发
 * {@code main/hologram}，其中四个盔甲槽全空的另发 {@code main/hologram_big}（原作只写了注释；那段循环取下线玩家的盔甲栏会空指针，一起修了）。
 *
 * <h2>其它</h2>
 * 不受重力、每刻锁回出生点、推不动也不推别人、没有摔落伤害、免火、不吃药水效果、拴不住、不换维度；红色 Boss 血条给所有追踪它的玩家。
 * 招式编号是一个字节的同步数据（变了才发；原作每刻拼一次字符串）。存盘：出生点、名单、放弃计时、召唤者、投影台位置、上一招；
 * 读档时攻击状态重置成休息（技能实体不存盘，读回来的半截攻击没有意义）。Boss 战音乐由客户端的 {@code HologramMusicSound} 绑在实体上循环放。
 * <p>
 * 原作减法攻击 {@code faceEntity(getPlayerFromUUID(..))} 不判空（L220）：参战者下线后的 10 刻里、读档后头 10 刻都会崩服；这里所有出手都只对在线、同维度的参战者。
 *
 * <h2>给进阶型留的口子（审判庭哨兵 {@link InquisitoriumSentinelEntity}）</h2>
 * 生命取哪份配置（{@link #configuredMaxHealth}）、能在哪个维度出现（{@link #allowedIn}）、挑哪一招（{@link #chooseAttack}）、
 * 出招开始 / 每刻 / 收招（{@link #onAttackStart}、{@link #echoStep}、{@link #onAttackEnd}）、打倒时发什么进度（{@link #awardVictory}）、
 * 放弃时掉回什么（{@link #returnedSummoningItem}）、血条颜色（{@link #setBossBarColor}）。四招本身拆成 {@link #spawnClones}、{@link #shootDestroyers}、
 * {@link #strikeRandomFighter}、{@link #placeDividers}，进阶型用它们叠第二招，克隆体、护甲破坏者、二分场的上限照样按这一只 Boss 算。
 * 全息投影自己的行为和以前一样（{@code HologramTests} 守着）。
 */
public class HologramEntity extends Mob implements BossHealthGuard.Guarded {

    /** 休息中 */
    public static final int REST = -1;
    public static final int ADDITION = 0;
    public static final int SUBTRACTION = 1;
    public static final int MULTIPLICATION = 2;
    public static final int DIVISION = 3;
    /** 每招持续多少刻（原作 attackTimer = 100） */
    public static final int ATTACK_TICKS = 100;
    /** 加法攻击在剩几刻时生成克隆体（原作 attackTimer == 20） */
    public static final int CLONE_SPAWN_TICK = 20;
    /** 克隆体生成在参战者身边多远以内（原作 ±6） */
    public static final double CLONE_SPREAD = 6D;
    /** 每只克隆体找落脚点最多试几次 */
    public static final int CLONE_SPOT_TRIES = 8;
    /** 减法攻击每几刻给每个参战者一发 */
    public static final int DESTROYER_INTERVAL = 5;
    public static final float DESTROYER_SPEED = 1.5F;
    public static final float DESTROYER_INACCURACY = 0.5F;
    /** 乘法攻击每几刻一发（原作 attackTimer % 10） */
    public static final int ORBITAL_INTERVAL = 10;
    /** 除法攻击每几刻一轮（原作 attackTimer % 20） */
    public static final int DIVIDER_INTERVAL = 20;
    /** 二分场几刻后张开（原作 2 秒） */
    public static final int DIVIDER_DELAY = 40;
    /** 离开多远会被拉回（原作 RANGE = 24） */
    public static final double PULL_RANGE = 24D;
    /** 拉回扣几点（原作 5） */
    public static final float PULL_DAMAGE = 5F;
    /** 刷怪蛋出来的没有投影台时，出生点往下找几格有碰撞的方块当拉回落点 */
    public static final int PULL_FLOOR_SEARCH = 8;
    /** 名单检查间隔（原作 ticksExisted % 10） */
    public static final int ROSTER_INTERVAL = 10;
    /** 单次或休息期累计超过这么多就马上出招（原作 40） */
    public static final float EARLY_ATTACK_THRESHOLD = 40F;
    /** 不是近战的伤害除以几（原作弹射物 ÷4） */
    public static final float RANGED_DIVISOR = 4F;
    /** 一只全息投影「管」的范围：64 格内不能再召第二只（一次 129³ 查询） */
    public static final double UNIQUE_RANGE = 64D;
    /** 投影台召唤时 Boss 在投影台上方几格（原作 y + 5） */
    public static final int SUMMON_HEIGHT = 5;
    /** 出招 / 收招音的音量（32 格内听得到；参战者在 24 格内） */
    public static final float STOP_VOLUME = 2F;
    /** 打倒它给的经验（同凋灵；原作 0） */
    public static final int XP_REWARD = 50;

    private static final EntityDataAccessor<Byte> DATA_ATTACK = SynchedEntityData.defineId(HologramEntity.class, EntityDataSerializers.BYTE);

    private static final String TAG_BASE = "base";
    private static final String TAG_PARTICIPANTS = "participants";
    private static final String TAG_ABANDON = "abandon_ticks";
    private static final String TAG_SUMMONER = "summoner";
    private static final String TAG_HOLOPAD = "holopad";
    private static final String TAG_PREV_ATTACK = "prev_attack";
    private static final String TAG_STRANDED = "stranded";
    private static final String TAG_STRANDED_ID = "id";
    private static final String TAG_STRANDED_TICKS = "ticks";

    /** 服务端正在世界里的全息投影（ECFlight 压制器查参战者用；只有服务端的实例进来） */
    private static final Set<HologramEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    /** GameTest 用：只把这个范围里的全息投影算进「64 格内已有一只」（同一批测试的结构挨得很近） */
    @Nullable
    private static AABB testScope;

    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private final Set<UUID> participants = new LinkedHashSet<>();
    /** 在拉回距离以外、连着拉不回来多少刻了（按名单检查累计，存盘） */
    private final Map<UUID, Integer> stranded = new LinkedHashMap<>();
    private final List<Entity> clones = new ArrayList<>();
    private final List<Entity> destroyers = new ArrayList<>();
    private final List<Entity> dividers = new ArrayList<>();
    private int attackTimer;
    private int restingTime;
    private int prevAttackId = REST;
    /** 休息期累计伤害（每次出招重置成 1，照原作） */
    private float restDamage = 1F;
    /** 这一轮乘法攻击放到第几发（伤害 = 2 的这么多次方，封顶） */
    private int orbitalCount;
    @Nullable
    private Vec3 base;
    private int abandonTicks;
    @Nullable
    private UUID summoner;
    /** 投影台召出来的才有：投影台原来的位置（拉回落点、放弃时掉回投影台的地方） */
    @Nullable
    private BlockPos holopad;
    private boolean defeated;
    /** 生命统一结算（防绕开伤害流程的秒杀，见 {@link BossHealthGuard}）；构造期间父类调 setHealth 时还是 null */
    private final BossHealthGuard healthGuard = new BossHealthGuard();
    private final BossDamageBudget guardBudget = new BossDamageBudget();

    public HologramEntity(EntityType<? extends HologramEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        // 同凋灵；只在玩家打过它时掉（原版 dropExperience 的条件），/kill 不掉
        this.xpReward = XP_REWARD;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, ECHologram.Config.DEFAULTS.maxHealth())
                .add(Attributes.MOVEMENT_SPEED, 0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ATTACK, (byte) REST);
    }

    // ------------------------------------------------------------------
    // 召唤
    // ------------------------------------------------------------------

    /**
     * 投影台召唤前调（还没加进世界）：最大生命按配置、召唤者进名单、记下投影台位置与出生点。
     */
    public void prepareSummon(ServerPlayer summoner, BlockPos holopadPos) {
        applyConfiguredHealth();
        this.summoner = summoner.getUUID();
        this.holopad = holopadPos.immutable();
        this.base = position();
        participants.add(summoner.getUUID());
    }

    /** 最大生命按配置（{@code max_health}）设好、回满。投影台召唤时调；刷怪蛋、发射器、不带 NBT 的 /summon 由 {@link HologramEvents} 调 */
    public void applyConfiguredHealth() {
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(configuredMaxHealth());
        }
        setHealth(getMaxHealth());
    }

    /** 召出时的最大生命：全息投影取 {@code general.hologram.max_health} */
    protected double configuredMaxHealth() {
        return ECHologram.Config.values().maxHealth();
    }

    /** 这个世界能不能召（海纳尔，或者开了 {@code other_dimensions}） */
    public static boolean dimensionAllowed(Level level) {
        return ECHoanna.isHoanna(level) || ECHologram.Config.values().otherDimensions();
    }

    /** 刷怪蛋、发射器放出这一只时，这个世界放不放行（{@link HologramEvents}）；全息投影同 {@link #dimensionAllowed} */
    public boolean allowedIn(Level level) {
        return dimensionAllowed(level);
    }

    /** center 周围 {@value #UNIQUE_RANGE} 格（129³ 的盒子）里已经有活着的全息投影 */
    public static boolean anyNearby(ServerLevel level, Vec3 center) {
        AABB box = new AABB(center, center).inflate(UNIQUE_RANGE);
        AABB scope = testScope;
        if (scope != null) {
            if (!box.intersects(scope)) {
                return false;
            }
            box = box.intersect(scope);
        }
        return !level.getEntitiesOfClass(HologramEntity.class, box, LivingEntity::isAlive).isEmpty();
    }

    /** GameTest 用：「64 格内已有一只」只看这个范围（null = 不限）。同一段同步代码里设、测、置回 */
    public static void setTestScope(@Nullable AABB scope) {
        testScope = scope;
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        if (!level().isClientSide) {
            healthGuard.observe(this, this);
            if (base == null) {
                base = position();
            }
            lockInPlace();
        }
        super.tick();
        if (level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HologramClient.musicTick(this));
        } else {
            lockInPlace();
        }
    }

    /** 每刻锁回出生点（原作 L156~163） */
    private void lockInPlace() {
        Vec3 at = base;
        if (at != null && (getX() != at.x || getY() != at.y || getZ() != at.z)) {
            setPos(at.x, at.y, at.z);
        }
        setDeltaMovement(Vec3.ZERO);
    }

    /** 名单、放弃、攻击循环、血条（NoAI 的不动；死了不动） */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        serverStep();
    }

    /**
     * 服务端的一刻（{@link #customServerAiStep} 每刻调；GameTest 对 NoAI 的 Boss 直接调）：和平难度消失、每 {@value #ROSTER_INTERVAL} 刻查名单、
     * 名单空了计放弃时间、推进攻击循环、更新血条。
     */
    public void serverStep() {
        if (!(level() instanceof ServerLevel server) || isDeadOrDying() || isRemoved()) {
            return;
        }
        if (server.getDifficulty() == Difficulty.PEACEFUL) {
            shutDown();
            return;
        }
        if (tickCount % ROSTER_INTERVAL == 0) {
            tickRoster();
        }
        if (participants.isEmpty()) {
            if (++abandonTicks >= ECHologram.Config.values().abandonTicks()) {
                shutDown();
                return;
            }
        } else {
            abandonTicks = 0;
        }
        tickCombat();
        bossEvent.setProgress(healthFraction());
    }

    /**
     * 攻击循环推进一刻（见类注释；GameTest 对 NoAI 的 Boss 直接调）。只在服务端调。
     */
    public void tickCombat() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        float h = healthFraction();
        int attack = attackId();
        if (restingTime == 0 && attack == REST) {
            attack = chooseAttack();
            setAttackId(attack);
            attackTimer = ATTACK_TICKS;
            restDamage = 1F;
            orbitalCount = 0;
            playStop(server, 2F);
            onAttackStart(server, attack);
        }
        if (attackTimer > 0 && attack != REST) {
            --attackTimer;
            switch (attack) {
                case ADDITION -> {
                    if (attackTimer == CLONE_SPAWN_TICK) {
                        spawnClones(server, cloneCount(h));
                    }
                }
                case SUBTRACTION -> {
                    if (attackTimer % DESTROYER_INTERVAL == 0) {
                        shootDestroyers(server);
                    }
                }
                case MULTIPLICATION -> {
                    if (attackTimer % ORBITAL_INTERVAL == 0) {
                        callOrbitalStrike(server, h);
                    }
                }
                case DIVISION -> {
                    if (attackTimer % DIVIDER_INTERVAL == 0) {
                        placeDividers(server, dividerCount(h));
                    }
                }
                default -> {
                }
            }
            echoStep(server, attackTimer, h);
        }
        if (attackTimer == 0 && attack != REST) {
            prevAttackId = attack;
            setAttackId(REST);
            restingTime = restTicks(h);
            playStop(server, 0.5F);
            onAttackEnd(server, attack);
        }
        if (restingTime > 0) {
            --restingTime;
        }
    }

    /** 休息到点挑下一招（全息投影四招等概率，照原作） */
    protected int chooseAttack() {
        return random.nextInt(4);
    }

    /** 一招刚开始（招式已经设好、计时 {@value #ATTACK_TICKS}）。全息投影什么都不做 */
    protected void onAttackStart(ServerLevel server, int attack) {
    }

    /** 出招期间每刻在这一招本身之后调一次（timer 是这一刻减过以后的剩余刻数）。全息投影什么都不做 */
    protected void echoStep(ServerLevel server, int timer, float h) {
    }

    /** 一招刚放完（已经记成上一招、回到休息）。全息投影什么都不做 */
    protected void onAttackEnd(ServerLevel server, int attack) {
    }

    private void playStop(ServerLevel server, float pitch) {
        server.playSound(null, getX(), getY(), getZ(), ECHologram.SOUND_STOP.get(), SoundSource.HOSTILE, STOP_VOLUME, pitch);
    }

    // ---------------- 四招 ----------------

    /** 加法：每个在线参战者身边 count 只克隆体，这一只 Boss 合计不超过 {@code max_clones} */
    protected void spawnClones(ServerLevel server, int count) {
        prune(clones);
        int cap = ECHologram.Config.values().maxClones();
        for (ServerPlayer player : onlineParticipants(server)) {
            for (int i = 0; i < count && clones.size() < cap; i++) {
                Vec3 spot = findCloneSpot(server, player);
                if (spot == null) {
                    continue;
                }
                PlayerCloneEntity clone = PlayerCloneEntity.spawn(server, this, player, spot);
                if (clone != null) {
                    clones.add(clone);
                }
            }
        }
    }

    /** 参战者身边 ±6 格、高度 ±2 格里找一处克隆体站得下的地方（脚下有碰撞、身子没碰撞、不泡在液体里、区块已加载）；找不到返回 null */
    @Nullable
    private Vec3 findCloneSpot(ServerLevel server, ServerPlayer player) {
        EntityType<PlayerCloneEntity> type = ECHologram.PLAYER_CLONE.get();
        for (int attempt = 0; attempt < CLONE_SPOT_TRIES; attempt++) {
            double x = player.getX() + (random.nextDouble() * 2D - 1D) * CLONE_SPREAD;
            double z = player.getZ() + (random.nextDouble() * 2D - 1D) * CLONE_SPREAD;
            int baseY = Mth.floor(player.getY());
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                BlockPos feet = BlockPos.containing(x, baseY + dy, z);
                if (!ECLevels.isFullyLoaded(server, feet)) {
                    break;
                }
                BlockPos below = feet.below();
                VoxelShape floor = server.getBlockState(below).getCollisionShape(server, below, CollisionContext.empty());
                if (floor.isEmpty()) {
                    continue;
                }
                double top = below.getY() + floor.max(Direction.Axis.Y);
                Vec3 at = new Vec3(x, top, z);
                AABB box = type.getDimensions().makeBoundingBox(at);
                if (server.noCollision(box) && !server.containsAnyLiquid(box)) {
                    return at;
                }
            }
        }
        return null;
    }

    /** 减法：给每个在线参战者射一发护甲破坏者，这一只 Boss 同时在飞的不超过 {@code max_armor_destroyers} */
    protected void shootDestroyers(ServerLevel server) {
        prune(destroyers);
        int cap = ECHologram.Config.values().maxArmorDestroyers();
        for (ServerPlayer player : onlineParticipants(server)) {
            if (destroyers.size() >= cap) {
                return;
            }
            ArmorDestroyerEntity destroyer = ArmorDestroyerEntity.shoot(server, this, player, DESTROYER_SPEED, DESTROYER_INACCURACY);
            if (destroyer != null) {
                destroyers.add(destroyer);
            }
        }
    }

    private void callOrbitalStrike(ServerLevel server, float h) {
        if (participants.isEmpty()) {
            return;
        }
        int index = orbitalCount++;
        strikeRandomFighter(server, strikeDamage(index, ECHologram.Config.values().orbitalMaxDamage()), orbitalDelay(h));
    }

    /** 乘法的一发：在随机一名在线参战者脚下放一记轨道打击（delay 刻后落下）；没人在线就不放。返回放出的打击（没放成为 null） */
    @Nullable
    protected OrbitalStrikeEntity strikeRandomFighter(ServerLevel server, float damage, int delay) {
        List<ServerPlayer> targets = onlineParticipants(server);
        if (targets.isEmpty()) {
            return null;
        }
        ServerPlayer target = targets.get(random.nextInt(targets.size()));
        return OrbitalStrikeEntity.spawn(server, target.position(), damage, 1F, delay, this, this::isSkillTarget);
    }

    /** 除法：count 个二分场落在随机参战者脚下，这一只 Boss 同时最多 {@code max_dividers} 个 */
    protected void placeDividers(ServerLevel server, int count) {
        prune(dividers);
        int cap = ECHologram.Config.values().maxDividers();
        List<ServerPlayer> targets = onlineParticipants(server);
        for (int i = 0; i < count && !targets.isEmpty() && dividers.size() < cap; i++) {
            ServerPlayer target = targets.get(random.nextInt(targets.size()));
            DividerFieldEntity field = DividerFieldEntity.spawn(server, target.position(), DIVIDER_DELAY, this, this::isSkillTarget, false);
            if (field != null) {
                dividers.add(field);
            }
        }
    }

    private static void prune(List<Entity> spawned) {
        spawned.removeIf(Entity::isRemoved);
    }

    /**
     * Boss 的技能打谁：名单里的生存 / 冒险玩家，加上敌对生物（{@link Enemy}；跳过 {@link ECEntityRules#isSpared}、{@code #forge:bosses}、全息投影本身）。
     * 村民、宠物、动物、盔甲架、旁观者都不碰。
     */
    public boolean isSkillTarget(LivingEntity entity) {
        if (entity instanceof Player player) {
            return participants.contains(player.getUUID()) && fightingMode(player);
        }
        if (!(entity instanceof Enemy) || entity instanceof HologramEntity) {
            return false;
        }
        return !entity.getType().is(Tags.EntityTypes.BOSSES) && !ECEntityRules.isSpared(entity);
    }

    // ---------------- 纯函数（照原作公式，GameTest 直接核） ----------------

    /** 休息多少刻：100 − ⌊80 − 80h⌋（原作 L258；满血 100、见底 20） */
    public static int restTicks(float h) {
        return 100 - Mth.floor(80F - h * 80F);
    }

    /** 加法攻击每个参战者几只克隆体：1 + (3 − ⌊3h⌋)（原作 L195；满血 1、见底 4） */
    public static int cloneCount(float h) {
        return 1 + (3 - Mth.floor(h * 3F));
    }

    /** 轨道打击延迟：round(20 × (1 + 2h)) 刻（原作 3 − (2 − 2h) 秒；满血 60、见底 20） */
    public static int orbitalDelay(float h) {
        return Math.round(20F * (1F + 2F * h));
    }

    /** 除法攻击每轮几个二分场：1 + 5 − ⌊5h⌋（原作 L243；满血 1、见底 6） */
    public static int dividerCount(float h) {
        return 1 + 5 - Mth.floor(h * 5F);
    }

    /** 乘法攻击第 index 发（从 0 起）的伤害：2 的 index 次方，封顶 cap（原作 1 → 512 翻倍不封顶） */
    public static float strikeDamage(int index, double cap) {
        return (float) Math.min(Math.pow(2D, Math.max(0, index)), cap);
    }

    private float healthFraction() {
        float max = getMaxHealth();
        return max > 0F ? Mth.clamp(getHealth() / max, 0F, 1F) : 0F;
    }

    // ------------------------------------------------------------------
    // 参战名单
    // ------------------------------------------------------------------

    /** 名单检查一次（见类注释；GameTest 直接调）。只在服务端调 */
    public void tickRoster() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        int escape = ECHologram.Config.values().escapeRange();
        for (UUID id : List.copyOf(participants)) {
            ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), id);
            if (player == null || !player.isAlive() || player.isRemoved() || player.level() != server || !fightingMode(player)) {
                dropFighter(id);
                continue;
            }
            double distance = player.distanceTo(this);
            if (distance > escape) {
                dropFighter(id);
                continue;
            }
            if (distance > PULL_RANGE) {
                // 拉不回（落点被堵、底下伤人、没有权限、传送被拦）就不拉、不扣血，但人还在战斗里；连着拉不回来满 abandon_ticks 才按脱战移出
                if (pullBack(server, player)) {
                    stranded.remove(id);
                } else if (stranded.merge(id, ROSTER_INTERVAL, Integer::sum) >= ECHologram.Config.values().abandonTicks()) {
                    dropFighter(id);
                    player.displayClientMessage(Component.translatable("message.essentialcraft5.boss.left_fight_stranded"), true);
                    continue;
                }
            } else {
                stranded.remove(id);
            }
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    /** 移出名单，连同拉不回来的累计 */
    private void dropFighter(UUID id) {
        participants.remove(id);
        stranded.remove(id);
    }

    /** 这名参战者在拉回距离以外、连着拉不回来多少刻了（没有就是 0） */
    public int strandedTicks(UUID player) {
        return stranded.getOrDefault(player, 0);
    }

    /**
     * 拉回：落点安全、被拉的人在落点能动、传送没被取消才算拉回来了（扣 5 点、放铁砧破碎声）；否则返回 false，什么都不做（不拉、不扣血，人留在名单里）。
     * 权限以被拉的人的身份探测（缓存 + 每刻预算，预算按这只 Boss 算）；探测不出结论（PENDING）这一轮也不拉。
     */
    private boolean pullBack(ServerLevel server, ServerPlayer player) {
        BlockPos floor = pullFloor(server);
        if (floor == null) {
            return false;
        }
        ECTeleport.Check check = ECTeleport.check(player, server, floor, this);
        Vec3 landing = check.landing();
        if (!check.ok() || landing == null) {
            return false;
        }
        if (!ECTeleport.teleport(player, server, landing, player.getYRot(), player.getXRot())) {
            return false;
        }
        player.resetFallDistance();
        player.hurt(damageSources().mobAttack(this), PULL_DAMAGE);
        server.playSound(null, landing.x, landing.y, landing.z, SoundEvents.ANVIL_BREAK, SoundSource.HOSTILE, 1F, 0.5F);
        return true;
    }

    /**
     * 拉回落点的「脚下那格」（{@code ECTeleport.check} 要的就是它）：投影台召出来的是投影台原位的下面一格（召唤后原位是空气）；
     * 刷怪蛋、/summon 出来的从出生点往下 {@value #PULL_FLOOR_SEARCH} 格内第一个有碰撞的方块；找不到（或没加载）返回 null
     */
    @Nullable
    public BlockPos pullFloor(ServerLevel server) {
        if (holopad != null) {
            return holopad.below();
        }
        Vec3 at = base != null ? base : position();
        BlockPos start = BlockPos.containing(at);
        for (int i = 0; i <= PULL_FLOOR_SEARCH; i++) {
            BlockPos pos = start.below(i);
            if (!ECLevels.isFullyLoaded(server, pos)) {
                return null;
            }
            if (!server.getBlockState(pos).getCollisionShape(server, pos, CollisionContext.empty()).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    private List<ServerPlayer> onlineParticipants(ServerLevel server) {
        List<ServerPlayer> online = new ArrayList<>(participants.size());
        for (UUID id : participants) {
            ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), id);
            if (player != null && player.isAlive() && player.level() == server && fightingMode(player)) {
                online.add(player);
            }
        }
        return online;
    }

    /** 能参战的游戏模式：生存 / 冒险 */
    private static boolean fightingMode(Player player) {
        return !player.isCreative() && !player.isSpectator();
    }

    /** 名单里有没有这个人 */
    public boolean isParticipant(UUID player) {
        return participants.contains(player);
    }

    /** 名单（只读） */
    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    /** 把这名玩家加进名单（召唤者、打出有效伤害的人；GameTest 也用它摆名单）。只收生存 / 冒险的真玩家 */
    public void addParticipant(ServerPlayer player) {
        if (ECProtection.isRealPlayer(player) && fightingMode(player)) {
            participants.add(player.getUUID());
            abandonTicks = 0;
        }
    }

    /**
     * 这名玩家死了：从所有全息投影的名单里移出（{@link HologramEvents} 在 {@code LivingDeathEvent} 里调）。
     * 开了立即重生时，10 刻一次的名单检查看到的往往已经是重生出来的新对象，靠它当场移出，「死了就算退出战斗」才成立。
     */
    public static void forgetFighter(UUID player) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (HologramEntity hologram : List.copyOf(ACTIVE)) {
            hologram.dropFighter(player);
        }
    }

    /**
     * ECFlight 的压制器：这名玩家在某只全息投影的名单里（同一个世界、Boss 还活着）就暂停本模组给的飞行。只遍历服务端在世界里的全息投影
     */
    public static boolean isEngaged(ServerPlayer player) {
        if (ACTIVE.isEmpty()) {
            return false;
        }
        UUID id = player.getUUID();
        for (HologramEntity hologram : List.copyOf(ACTIVE)) {
            if (hologram.isAlive() && hologram.level() == player.level() && hologram.participants.contains(id)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 受伤与死亡
    // ------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (BossRules.unblockable(this, source)) {
            return super.hurt(source, amount);
        }
        if (level().isClientSide || isDeadOrDying() || source.is(ECDamageTypes.ORBITAL_STRIKE)) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer player) || !ECProtection.isRealPlayer(player) || player.isSpectator()) {
            return false;
        }
        if (!player.isCreative() && attackId() != REST) {
            return false;
        }
        // 只有站在拉回范围以内的人打得动：在远处打一下就退开的人，技能落下时已经不在名单里。在父类之前返回，混合端不留副作用
        if (!player.isCreative() && (player.level() != level() || player.distanceTo(this) > PULL_RANGE)) {
            return false;
        }
        float cap = damageCap();
        float counted = cap > 0F ? Math.min(amount, cap) : amount;
        boolean melee = ECDamageKinds.isMelee(source);
        float dealt = melee ? amount : amount / RANGED_DIVISOR;
        if (cap > 0F) {
            dealt = Math.min(dealt, cap);
        }
        float before = getHealth();
        boolean result = super.hurt(source, dealt);
        if (getHealth() < before) {
            restDamage += counted;
            if (counted > EARLY_ATTACK_THRESHOLD || restDamage > EARLY_ATTACK_THRESHOLD) {
                restingTime = 1;
            }
            addParticipant(player);
            SoundEvent sound = melee ? ECHologram.SOUND_DAMAGE_MELEE.get() : ECHologram.SOUND_DAMAGE_PROJECTILE.get();
            level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, melee ? 0.3F : 5F, random.nextFloat() * 2F);
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        if (healthGuard != null && !level().isClientSide) {
            // 先对一遍生命，生命没真扣到 0 就调 die()（直接处决的写法）：不算死
            healthGuard.observe(this, this);
            if (healthGuard.refusesDeath(this)) {
                return;
            }
        }
        boolean wasDead = dead;
        // 打出最后一下的玩家这时还没进名单（hurt 在父类返回、看到生命掉了以后才加），先加上
        if (!wasDead && source.getEntity() instanceof ServerPlayer killer) {
            addParticipant(killer);
        }
        super.die(source);
        if (!wasDead && dead && !defeated && level() instanceof ServerLevel server) {
            defeated = true;
            awardFighters(server);
        }
    }

    /** 给名单里在线、同维度的玩家发打倒它的进度（{@link #awardVictory}） */
    private void awardFighters(ServerLevel server) {
        for (UUID id : List.copyOf(participants)) {
            ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), id);
            if (player == null || player.level() != server) {
                continue;
            }
            awardVictory(player);
        }
    }

    /** 打倒时给一名参战者（在线、同维度）发的进度：「幻影斗士」，四个盔甲槽全空的另发「兰博」 */
    protected void awardVictory(ServerPlayer player) {
        Advancement hologram = player.server.getAdvancements().getAdvancement(ECHologram.ADVANCEMENT_HOLOGRAM);
        Advancement big = player.server.getAdvancements().getAdvancement(ECHologram.ADVANCEMENT_HOLOGRAM_BIG);
        if (hologram != null) {
            player.getAdvancements().award(hologram, ECHologram.ADVANCEMENT_CRITERION);
        }
        if (big != null && noArmor(player)) {
            player.getAdvancements().award(big, ECHologram.ADVANCEMENT_CRITERION);
        }
    }

    private static boolean noArmor(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 按最后放完的那一招选掉落表；还没放完过一招时随机挑一张并记下（照原作 L58~62） */
    @Override
    protected ResourceLocation getDefaultLootTable() {
        if (prevAttackId < ADDITION || prevAttackId > DIVISION) {
            prevAttackId = random.nextInt(4);
        }
        return lootTableFor(prevAttackId);
    }

    /** 招式 → 掉落表：0 加法、1 减法、2 乘法、3 除法 */
    public static ResourceLocation lootTableFor(int attack) {
        String name = switch (attack) {
            case ADDITION -> "addition";
            case SUBTRACTION -> "subtraction";
            case MULTIPLICATION -> "multiplication";
            default -> "division";
        };
        return EssentialCraft5.id("entities/hologram_" + name);
    }

    /** 消失（没人参战、和平难度）：投影台召出来、没死的在投影台原位掉回一个投影台物品 */
    public void shutDown() {
        if (isRemoved()) {
            return;
        }
        if (holopad != null && !defeated && !isDeadOrDying() && level() instanceof ServerLevel server) {
            ItemEntity drop = new ItemEntity(server, holopad.getX() + 0.5D, holopad.getY() + 0.25D, holopad.getZ() + 0.5D,
                    returnedSummoningItem());
            drop.setDefaultPickUpDelay();
            // 掉回的台子不会消失（用料里有永恒物质和虚空核心）
            drop.setUnlimitedLifetime();
            server.addFreshEntity(drop);
        }
        level().playSound(null, getX(), getY(), getZ(), ECHologram.SOUND_SHUTDOWN.get(), SoundSource.HOSTILE, 1F, 1F);
        discard();
    }

    /** 放弃、和平难度时在召唤方块原位掉回的东西：全息投影掉回一个投影台 */
    protected ItemStack returnedSummoningItem() {
        return new ItemStack(ECHologram.HOLOPAD.get());
    }

    // ------------------------------------------------------------------
    // 生命统一结算
    // ------------------------------------------------------------------

    @Override
    public BossHealthGuard healthGuard() {
        return healthGuard;
    }

    @Override
    public BossDamageBudget guardBudget() {
        return guardBudget;
    }

    /** 单次封顶同 {@code max_damage_per_hit}（跟着生命上限放大，{@link BossHealthGuard#limitScale}），没有每秒桶 */
    @Override
    public BossDamageBudget.Limits guardLimits() {
        return BossDamageBudget.Limits.of(ECHologram.Config.values().maxDamagePerHit(), 0D)
                .scaled(healthGuard == null ? 1F : healthGuard.limitScale(this));
    }

    /** 实际用的单次封顶（0 = 不封顶） */
    public float damageCap() {
        return guardLimits().perHit();
    }

    /** 只在休息时打得动：出招时绕开伤害流程的掉血一点不掉 */
    @Override
    public float guardFloor() {
        return attackId() == REST ? 0F : Float.POSITIVE_INFINITY;
    }

    @Override
    public void setHealth(float health) {
        if (healthGuard == null || level().isClientSide) {
            super.setHealth(health);
            return;
        }
        healthGuard.set(this, this, health);
    }

    /** 血量同步数据被直接改了（反射拿到数据键再 set 的写法）：当场结算 */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (healthGuard != null && BossHealthGuard.isHealth(key) && !level().isClientSide) {
            healthGuard.observe(this, this);
        }
    }

    // ------------------------------------------------------------------
    // 招式状态（两端）
    // ------------------------------------------------------------------

    /** 当前招式：{@link #REST}（休息）或 0~3 */
    public int attackId() {
        return entityData.get(DATA_ATTACK);
    }

    private void setAttackId(int attack) {
        entityData.set(DATA_ATTACK, (byte) attack);
    }

    /** 最后放完的那一招（还没放完过为 {@link #REST}） */
    public int prevAttackId() {
        return prevAttackId;
    }

    public int restingTime() {
        return restingTime;
    }

    public int attackTimer() {
        return attackTimer;
    }

    /** 休息期累计伤害（从 1 起） */
    public float restDamage() {
        return restDamage;
    }

    public int abandonTicks() {
        return abandonTicks;
    }

    @Nullable
    public UUID summoner() {
        return summoner;
    }

    /** 投影台召出来的才有 */
    @Nullable
    public BlockPos holopadPos() {
        return holopad;
    }

    /** 现在还在的克隆体 / 护甲破坏者 / 二分场各有几个（服务端） */
    public int liveClones() {
        prune(clones);
        return clones.size();
    }

    public int liveDestroyers() {
        prune(destroyers);
        return destroyers.size();
    }

    public int liveDividers() {
        prune(dividers);
        return dividers.size();
    }

    /**
     * GameTest、展示厅用：直接摆招式状态（attack 为 {@link #REST} 时 timer 无意义）。prev 为 null 不改上一招。
     * 展示厅冻结招式时给 Boss 设 NoAI，攻击循环就停在这里。
     */
    public void setTestState(int attack, int timer, int resting, @Nullable Integer prev) {
        setAttackId(attack < ADDITION || attack > DIVISION ? REST : attack);
        attackTimer = Math.max(0, timer);
        restingTime = Math.max(0, resting);
        if (prev != null) {
            prevAttackId = prev;
        }
        if (attack == MULTIPLICATION) {
            orbitalCount = 0;
        }
    }

    // ------------------------------------------------------------------
    // 世界登记、血条
    // ------------------------------------------------------------------

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) {
            ACTIVE.add(this);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        ACTIVE.remove(this);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    /** 换血条颜色（全息投影是红色；进阶型在构造器里调） */
    protected final void setBossBarColor(BossEvent.BossBarColor color) {
        bossEvent.setColor(color);
    }

    /** 血条（GameTest 看颜色、进度用） */
    public BossEvent bossBar() {
        return bossEvent;
    }

    // ------------------------------------------------------------------
    // 不动、不推、不受影响
    // ------------------------------------------------------------------

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isPushedByFluid(FluidType type) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ------------------------------------------------------------------
    // 声音
    // ------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    /** 受击音效在 {@link #hurt} 里按近战 / 远程自己放（只在生命真掉了时） */
    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECHologram.SOUND_SHUTDOWN.get();
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
        if (base != null) {
            ListTag at = new ListTag();
            at.add(DoubleTag.valueOf(base.x));
            at.add(DoubleTag.valueOf(base.y));
            at.add(DoubleTag.valueOf(base.z));
            tag.put(TAG_BASE, at);
        }
        ListTag list = new ListTag();
        for (UUID id : participants) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put(TAG_PARTICIPANTS, list);
        tag.putInt(TAG_ABANDON, abandonTicks);
        if (summoner != null) {
            tag.putUUID(TAG_SUMMONER, summoner);
        }
        if (holopad != null) {
            tag.put(TAG_HOLOPAD, NbtUtils.writeBlockPos(holopad));
        }
        tag.putInt(TAG_PREV_ATTACK, prevAttackId);
        ListTag strandedList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : stranded.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID(TAG_STRANDED_ID, entry.getKey());
            row.putInt(TAG_STRANDED_TICKS, entry.getValue());
            strandedList.add(row);
        }
        tag.put(TAG_STRANDED, strandedList);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        healthGuard.reset();
        ListTag at = tag.getList(TAG_BASE, Tag.TAG_DOUBLE);
        base = at.size() == 3 ? new Vec3(at.getDouble(0), at.getDouble(1), at.getDouble(2)) : null;
        participants.clear();
        for (Tag entry : tag.getList(TAG_PARTICIPANTS, Tag.TAG_INT_ARRAY)) {
            try {
                participants.add(NbtUtils.loadUUID(entry));
            } catch (IllegalArgumentException ignored) {
                // 坏掉的 UUID 跳过
            }
        }
        abandonTicks = Math.max(0, tag.getInt(TAG_ABANDON));
        stranded.clear();
        for (Tag entry : tag.getList(TAG_STRANDED, Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            int ticks = row.getInt(TAG_STRANDED_TICKS);
            if (row.hasUUID(TAG_STRANDED_ID) && ticks > 0 && participants.contains(row.getUUID(TAG_STRANDED_ID))) {
                stranded.put(row.getUUID(TAG_STRANDED_ID), ticks);
            }
        }
        summoner = tag.hasUUID(TAG_SUMMONER) ? tag.getUUID(TAG_SUMMONER) : null;
        holopad = tag.contains(TAG_HOLOPAD, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_HOLOPAD)) : null;
        prevAttackId = tag.contains(TAG_PREV_ATTACK, Tag.TAG_INT) ? tag.getInt(TAG_PREV_ATTACK) : REST;
        // 读档回到休息：技能实体不存盘，读回来的半截攻击没有意义
        setAttackId(REST);
        attackTimer = 0;
        restingTime = restTicks(healthFraction());
        restDamage = 1F;
        orbitalCount = 0;
    }
}
