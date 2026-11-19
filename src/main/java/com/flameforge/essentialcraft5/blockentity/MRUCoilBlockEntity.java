package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.advancement.StructureFormedTrigger;
import com.flameforge.essentialcraft5.client.TeslaClient;
import com.flameforge.essentialcraft5.client.render.ArcEffect;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.PlayerListItem;
import com.flameforge.essentialcraft5.menu.MRUCoilMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECSounds;
import com.flameforge.essentialcraft5.registry.ECTesla;
import com.flameforge.essentialcraft5.structure.StructureCache;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructureResult;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.OwnerCellMemory;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MRU 特斯拉线圈。对应原作 TileMRUCoil（257 行）。领地主人的防御塔：自动电射程里的敌对生物，也电<b>闯进来的人</b>
 * （白名单以外、站在线圈主人能动而自己没有权限的地方的玩家）。
 *
 * <h2>结构</h2>
 * 线圈正下方往下数连续的基座（最多数 {@link #MAX_LAYERS_SCANNED} 格），数到的第一格起是 7×7 十字圆形的 25 格底座（{@link ECTesla#COIL_FOUNDATION}），
 * 每 20 tick 复查一次（{@link StructureCache}）。射程 = {@link #radiusFor}：3 + 2 × 基座层数、封顶 32（原作 3 + 层数、无上限），<b>球形</b>
 * （目标碰撞箱上离线圈中心最近的一点在球里就算）。第一次成形（和射程变大）时给离「底座中心 → 线圈」这一竖列 16 格内的玩家触发进度
 * {@code structure_formed}（{@link #playersNearColumn}：高塔最后在地面补底座的人离线圈可能超过 16 格）。
 *
 * <h2>放电（{@link #discharge}）</h2>
 * {@code 工作 = 成形 && MRU ≥ 每击费用 && 没有红石信号}（红石是电平开关，同原作 L145）。工作时每 {@code strike_interval}（20）tick 放一次电：
 * <ol>
 *   <li>先挑玩家（{@code level.players()}，不扫实体分区），过 {@link #playerCheck}，由近到远；</li>
 *   <li>名额没满再挑敌对生物（一次 {@code getEntitiesOfClass(Mob)}，立方体 ±射程、竖直夹到世界上下界），过 {@link #canTargetMob}，由近到远；</li>
 *   <li>每个候选：20 tick 内被任一台线圈打过的跳过（{@link MRUCoilTargets}）；权限这一关没过的跳过（玩家 {@link #mayStrikePlayer}，在视线之前；
 *       怪先只看记录、已知主人不能动的跳过，看得见了才以主人身份探测，{@link #mobPermission}）；看不见的跳过（视线，见下）；玩家第一次进射程先警告 2 秒；
 *       然后 {@link #strike}：伤害<b>生效了才扣</b> 1 000 MRU（原作不管成没成都扣，L217 / L222）。每次最多 3 个目标，MRU 不够下一击就停。</li>
 * </ol>
 * 以主人身份的新探测（玩家、怪合计）每次放电最多 {@link #OWNER_PROBES_PER_DISCHARGE} 格，探过的格子记在 {@link OwnerCellMemory} 里：
 * 混合端上被拒的探测会让主人收到领地插件的「没有权限」，邻居的刷怪塔在射程里时不能每秒刷他十几条。
 * 时间：放电间隔、结构复查、看不见的负缓存、电弧按线圈所在世界的游戏时间；全局表 {@link MRUCoilTargets}（全局冷却、重生保护、警告重新计时）
 * 和本台的警告表按主世界时间（{@link #globalTime} 换算）。原版和纯 Forge 两者相同；混合端上插件建的世界有自己的时间，不换算的话警告永远重来。
 *
 * <h2>打谁（2026-09-25 定的规则 + 2026-09-28 D2 的七条附加克制）</h2>
 * <ul>
 *   <li>敌对生物（{@link Enemy}）：不打受保护的（{@link ECEntityRules#isSpared}：起名、拴绳、驯服……）、Boss（{@code #forge:bosses}，⑥）、
 *       持久生物（{@code isPersistenceRequired}，⑥：捡了玩家东西的僵尸打死会吞掉那些东西）、实体类型标签 {@link ECTesla#MRU_COIL_IMMUNE}。
 *       被动生物、村民、宠物、盔甲架都不打（原作默认连被动生物都打，L51）。
 *       <b>怪脚下那格主人要能动</b>（第十批权限修补 A6，{@link #mobPermission}）：射程伸进邻居的领地时不清邻居刷怪塔里的怪；
 *       主人离线时只打他在线时探测过、能动的格子里的怪（{@link OwnerCellMemory}，记 1 小时），没探测过的不打；没有记下主人的线圈在多人服里不打怪。
 *       已知主人不能动的格子（记 5 分钟）里的怪连视线都不查；关在墙里看不见的怪不发探测。</li>
 *   <li>玩家：<b>主人本人永远不打</b>（记下的主人，以及单人里算作主人的房主，{@link ECProtection#actingOwner}）；白名单（1 号格 {@code player_list}）上的不打；
 *       创造、旁观不打；③ 没有记下主人的线圈（/setblock、别的模组放的）不打玩家；④ 世界出生点水平 {@code spawn_safe_radius}（48）格内的不打（只在主世界）；
 *       ⑤ 主人在界面里关掉「打玩家」的不打；⑦ 被线圈打死、重生 30 秒内的不打；① 要看得见；② 第一次进射程先警告 2 秒。
 *       <b>只打闯入者</b>（第十批权限修补 A1，配置 {@code trespassers_only}，默认开，{@link #mayStrikePlayer}）：两道都要过——
 *       以被打的人自己的身份探测他脚下那格，他在这里没有权限（没被信任）；而且那格是<b>线圈主人能动的</b>（以主人身份探测，记录和打怪共用 {@link OwnerCellMemory}）。
 *       也就是只打闯进主人自己地盘的人：射程伸进第三个人的领地、管理员圈的公共保护区（主城、道路）、出生点保护、世界边界外时，站在那里的人不打。
 *       PvP 关着也照打，「领地主人的防御」不变；野外的路人、在自己领地或被信任的领地里的人不打。
 *       主人离线时和打怪同一套规则：只认他在线时确认过能动的格子（1 小时内、区块没卸载），没确认过的不打、连被打的人都不探测。
 *       单人、没装领地的服上没人会被探测拒绝，等于不打玩家；要原来那样（射程里白名单以外的人都打）就关掉这个配置。
 *       这一条不分纯 Forge / 混合端，一律这样。</li>
 * </ul>
 *
 * <h2>伤害</h2>
 * {@link CoilDischargeSource}：伤害类型 {@code mru_discharge}，<b>不带攻击者、不带位置</b>（policy §1.1 第 3 条「伤害带攻击者」的唯一例外）：
 * PvP 关了、队伍友伤关了也打得到；盾挡不住、没有击退、无视护甲点数；保护附魔、抗性、伤害吸收、不死图腾照常；死亡消息写上主人名字；
 * 打死的生物不掉物品和经验（框架 DeathLootEvents，怪捡走的东西照掉），玩家死亡照常掉落、UBMRU 不会被夺。
 * 对敌对生物 18（原作），对玩家 12 + 当前最大生命 × 15%（D2 C 档），打中玩家加缓慢 II 3 秒、虚弱 I 3 秒、发光 5 秒（没有或剩不到 2 秒才续）。
 *
 * <h2>视线（①）</h2>
 * 从线圈 (0.5, 0.8, 0.5) 出发，先到目标眼睛，不通再到碰撞箱中心，每个目标最多 2 条射线；玩家、怪物各自每次放电最多 {@code rays_per_group}（8）条。
 * 方块按「视觉形状」挡：原版玻璃、染色玻璃、玻璃板、铁栏杆、本模组的强化玻璃不挡，实心方块挡；液体不挡；线圈自己这一竖列（线圈、基座、底座中心）不挡；
 * 没加载的区块当挡住（不触发加载）。看不见的记 {@code unseen_cache_ticks}（30）tick 负缓存（按线圈 × 实体）：否则射程里藏几个离得近、隔着墙的怪，
 * 射线预算每次都花在它们身上，远处看得见的入侵者永远打不到。
 *
 * <h2>和原作不同（报告里的 deviations）</h2>
 * 见上；另外：只在服务端算（原作两端都跑、客户端也扣 MRU）；每击费用统一 1 000（原作静态 200 / 配置 1 000 / 手册 2 000）；
 * 原作线圈旁有 MRU 聚集体时每秒 1 000 MRU 全花在它身上、永远打不到别的（查错 capability + 聚集体恒不受伤 + break），这里聚集体不是生物、伤害生效才扣，自然避开；
 * 白名单每人每 tick 解析一次字符串 → 按物品缓存 UUID 集合；1 号格不收自动化插入（否则领地外放个漏斗就能塞进写着自己名字的白名单）；
 * 渲染盒原作无限大 → 只包住线圈和待机电弧（打向目标的放电是客户端的一次性特效，世界坐标）。女巫吃满伤害（原作 MAGIC 对女巫只有 15%）。
 */
public class MRUCoilBlockEntity extends MRUBlockEntity {

    /** 服务端配置 {@code machines.mru_coil}（原作 tileentities.mrucoil 的几项 + 第四批新加的） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruPerHit;
        public static ForgeConfigSpec.IntValue strikeInterval;
        public static ForgeConfigSpec.IntValue maxTargets;
        public static ForgeConfigSpec.IntValue maxRadius;
        public static ForgeConfigSpec.DoubleValue monsterDamage;
        public static ForgeConfigSpec.DoubleValue playerDamage;
        public static ForgeConfigSpec.DoubleValue playerHealthPercent;
        public static ForgeConfigSpec.IntValue slownessTicks;
        public static ForgeConfigSpec.IntValue slownessLevel;
        public static ForgeConfigSpec.IntValue weaknessTicks;
        public static ForgeConfigSpec.IntValue weaknessLevel;
        public static ForgeConfigSpec.IntValue glowingTicks;
        public static ForgeConfigSpec.BooleanValue hurtPlayers;
        public static ForgeConfigSpec.BooleanValue trespassersOnly;
        public static ForgeConfigSpec.BooleanValue hurtMonsters;
        public static ForgeConfigSpec.IntValue warningTicks;
        public static ForgeConfigSpec.IntValue spawnSafeRadius;
        public static ForgeConfigSpec.IntValue respawnGraceTicks;
        public static ForgeConfigSpec.IntValue raysPerGroup;
        public static ForgeConfigSpec.IntValue unseenCacheTicks;
        public static ForgeConfigSpec.BooleanValue generatesCorruption;
        public static ForgeConfigSpec.IntValue corruptionPerHit;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("mru_coil");
            maxMRU = builder.comment("MRU the coil can hold.", "线圈能存多少 MRU")
                    .defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU * 10, 1, Integer.MAX_VALUE);
            mruPerHit = builder.comment("MRU used for each target actually hit (nothing is used when the hit does no damage).",
                            "每打中一个目标消耗的 MRU（伤害没生效不扣；超过 max_mru 时按 max_mru 算）")
                    .defineInRange("mru_per_hit", 1000, 0, Integer.MAX_VALUE);
            strikeInterval = builder.comment("Ticks between two discharges of the same coil.",
                            "同一台线圈两次放电之间隔几刻")
                    .defineInRange("strike_interval", 20, 20, 1200);
            maxTargets = builder.comment("At most this many targets per discharge (players first, nearest first).",
                            "每次放电最多打几个目标（玩家优先、近的先）")
                    .defineInRange("max_targets", 3, 1, 16);
            maxRadius = builder.comment("Range cap. Range = 3 + 2 per coil base layer, a sphere around the coil.",
                            "射程上限。射程 = 3 + 每层基座 2 格，以线圈为中心的球")
                    .defineInRange("max_radius", 32, 3, 32);
            monsterDamage = builder.comment("Damage dealt to a hostile mob per hit.", "每击对敌对生物的伤害")
                    .defineInRange("monster_damage", 18D, 0D, 1000D);
            playerDamage = builder.comment("Base damage dealt to a player per hit; the percentage below is added on top.",
                            "每击对玩家的基础伤害，另加下面按最大生命算的部分")
                    .defineInRange("player_damage", 12D, 0D, 1000D);
            playerHealthPercent = builder.comment("Extra damage to a player: this fraction of the target's current max health (0.15 = 15%).",
                            "对玩家的额外伤害：目标当前最大生命乘这个比例（0.15 = 15%）")
                    .defineInRange("player_health_percent", 0.15D, 0D, 1D);
            slownessTicks = builder.comment("Slowness given to a player hit by the coil, in ticks (0 = none).", "打中玩家时给的缓慢，刻（0 = 不给）")
                    .defineInRange("slowness_ticks", 60, 0, 1200);
            slownessLevel = builder.comment("Slowness level (2 = Slowness II).", "缓慢的等级（2 = 缓慢 II）")
                    .defineInRange("slowness_level", 2, 1, 5);
            weaknessTicks = builder.comment("Weakness given to a player hit by the coil, in ticks (0 = none).", "打中玩家时给的虚弱，刻（0 = 不给）")
                    .defineInRange("weakness_ticks", 60, 0, 1200);
            weaknessLevel = builder.comment("Weakness level (1 = Weakness I).", "虚弱的等级（1 = 虚弱 I）")
                    .defineInRange("weakness_level", 1, 1, 5);
            glowingTicks = builder.comment("Glowing given to a player hit by the coil, in ticks (0 = none).", "打中玩家时给的发光，刻（0 = 不给）")
                    .defineInRange("glowing_ticks", 100, 0, 1200);
            hurtPlayers = builder.comment("Coils attack players who are not on their whitelist (never their owner). Each coil can also switch this off in its screen.",
                            "线圈打白名单以外的玩家（永远不打主人）。每台线圈还能在界面里单独关掉")
                    .define("hurt_players", true);
            trespassersOnly = builder.comment(
                            "Of those, only intruders are attacked: players with no permission on the block at their feet (a block-break probe as that player)",
                            "while the coil's owner does have permission there (a probe as the owner). Players in a third party's claim or a protected public area",
                            "are left alone. While the owner is offline, only blocks confirmed as the owner's in the last hour count.",
                            "Off: every such player in range is attacked, also in the wild (for servers without claims).",
                            "上面那些人里只打闯入者：他自己在脚下那格没有权限（以他本人的身份做一次破坏探测，被拒），而那格线圈主人有权限（以主人身份探测，放行）。",
                            "站在第三个人的领地、公共保护区里的人不打。主人离线时只认他在线时一小时内确认过的格子。",
                            "关掉：射程里白名单以外的人都打，野外也打（给没装领地的服）")
                    .define("trespassers_only", true);
            hurtMonsters = builder.comment("Coils attack hostile mobs.", "线圈打敌对生物")
                    .define("hurt_monsters", true);
            warningTicks = builder.comment("A player entering a coil's range is warned this many ticks before the first hit (0 = no warning).",
                            "玩家第一次进射程时先警告这么多刻再打（0 = 不警告）")
                    .defineInRange("warning_ticks", 40, 0, 600);
            spawnSafeRadius = builder.comment("Players within this many blocks (horizontally) of the world spawn are never attacked (overworld only; 0 = off).",
                            "世界出生点水平这么多格以内的玩家不打（只在主世界；0 = 关掉）")
                    .defineInRange("spawn_safe_radius", 48, 0, 4096);
            respawnGraceTicks = builder.comment("A player killed by a coil is ignored by all coils for this many ticks after respawning (0 = off).",
                            "被线圈打死的玩家重生后这么多刻内所有线圈都不打他（0 = 关掉）")
                    .defineInRange("respawn_grace_ticks", 600, 0, 12000);
            raysPerGroup = builder.comment("Line-of-sight checks per discharge, for players and for mobs separately (2 per target at most).",
                            "每次放电做几次视线检查，玩家、怪物各算各的（每个目标最多 2 次）")
                    .defineInRange("rays_per_group", 8, 1, 32);
            unseenCacheTicks = builder.comment("A target the coil could not see is skipped for this many ticks.",
                            "看不见的目标这么多刻内直接跳过")
                    .defineInRange("unseen_cache_ticks", 30, 0, 200);
            generatesCorruption = builder.comment("Each hit leaks some MRU into the surroundings (corruption).",
                            "每击向周围泄漏 MRU（腐化）")
                    .define("generates_corruption", false);
            corruptionPerHit = builder.comment("Max MRU leaked per hit when generates_corruption is on.", "开了泄漏时每击最多泄漏多少 MRU")
                    .defineInRange("corruption_per_hit", 20, 0, 10000);
            builder.pop();
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_LIST = 1;
    public static final int SLOTS = 2;
    /** 没有基座时的射程 */
    public static final int BASE_RADIUS = 3;
    /** 每层基座加的射程 */
    public static final int RADIUS_PER_LAYER = 2;
    /** 往下最多数几层基座（数满还是基座 = 底座那一格是基座，不成形） */
    public static final int MAX_LAYERS_SCANNED = 64;
    /** 警告过的玩家这么久没再在射程里出现，下次进来重新警告（下限；放电间隔配得长时按 {@link #warnForgetTicks} 放宽） */
    public static final int WARN_FORGET_TICKS = 200;
    /** 负面状态剩不到这么多刻才续 */
    public static final int EFFECT_REFRESH_BELOW = 40;
    /** 电弧 / 视线的起点高度（原作 monsterLightning 从 (0.5, 0.8, 0.5) 出发） */
    public static final double EMIT_HEIGHT = 0.8D;
    /** 更新包里带上最近一次放电的目标，只在放电后这么多刻内带（区块重新发给玩家时不重放旧电弧） */
    public static final int STRIKE_SHOW_TICKS = 10;
    /**
     * 每次放电以主人身份的新探测最多几格（打闯入者、打怪合计；记录命中的不算）。被拒的探测在混合端上会让主人收到领地插件的提示，
     * 超出的这一轮先不打，下一次放电再问
     */
    public static final int OWNER_PROBES_PER_DISCHARGE = 4;

    /** 界面上的状态 */
    public enum Status {
        /** 还没判定过结构（刚放下、区块还没加载齐） */
        UNKNOWN,
        /** 结构不完整 */
        INCOMPLETE,
        /** 红石信号：停机 */
        REDSTONE,
        /** MRU 不够一击 */
        NO_MRU,
        /** 工作中 */
        ACTIVE
    }

    /** 一名玩家为什么打 / 不打（{@link #playerCheck}；测试和界面用） */
    public enum PlayerCheck {
        OK, NOT_TARGETABLE, CREATIVE, PLAYERS_OFF, NO_OWNER, OWNER, WHITELISTED, RESPAWN_GRACE, SPAWN_AREA, INVULNERABLE, OUT_OF_RANGE
    }

    private enum Sight {
        VISIBLE, BLOCKED, NO_BUDGET
    }

    private final StructureCache structure = new StructureCache(ECTesla.COIL_FOUNDATION);

    // ---- 服务端，存盘 ----
    /** 这台打不打玩家（主人在界面里切，⑤） */
    private boolean hurtPlayers = true;

    // ---- 服务端，不存盘 ----
    private int layers = -1;
    private int radius;
    private boolean powered;
    private long nextStructureCheck;
    private long nextStrike;
    /** 上次通过更新包发出去的「工作中」 */
    private boolean syncedActive;
    /** 警告表：玩家 → {第一次警告的时间, 最近一次在射程里看见的时间}（都是主世界时间，见 {@link #globalTime}） */
    private final Map<UUID, long[]> warnings = new HashMap<>();
    /** 看不见的实体 id → 到这个时间之前跳过 */
    private final Int2LongOpenHashMap unseen = new Int2LongOpenHashMap();
    /** 白名单缓存：按 1 号格的物品实例和 NBT 实例 */
    private ItemStack cachedList = ItemStack.EMPTY;
    @Nullable
    private CompoundTag cachedListTag;
    private Set<UUID> whitelist = Set.of();
    /** 打怪、打闯入者前问「那格主人能不能动」，探过的结论记下来，主人离线时认它记下的格子（A6 / A1，见 {@link #mobPermission}、{@link #mayStrikePlayer}） */
    private final OwnerCellMemory ownerCells = new OwnerCellMemory();
    /** 本 tick 打中的目标（放电结束后发给客户端画电弧） */
    private final List<Integer> pendingStrikes = new ArrayList<>();
    private int[] strikeIds = new int[0];
    private long strikeTime = Long.MIN_VALUE;

    // ---- 客户端 ----
    private boolean clientActive;
    private long clientStrikeTime = Long.MIN_VALUE;
    @Nullable
    private ArcEffect idleArc;

    // ---- 只给 GameTest 用（按台、不存盘） ----
    @Nullable
    private AABB testSearchClip;
    private int testSpawnSafeRadius = -1;
    private int testRaysPerGroup = -1;
    private int testStrikeInterval = -1;
    @Nullable
    private Boolean testTrespassersOnly;

    public MRUCoilBlockEntity(BlockPos pos, BlockState state) {
        super(ECTesla.MRU_COIL_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
        unseen.defaultReturnValue(Long.MIN_VALUE);
    }

    // ------------------------------------------------------------------
    // 槽位
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用）：0 号绑定宝石、1 号玩家白名单（原作 L253~256） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_LIST ? stack.getItem() instanceof PlayerListItem : defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 1 号格（白名单）不收漏斗、管道：否则在领地外放个漏斗就能把写着自己名字的白名单塞进空着的格子 */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        return slot != SLOT_LIST && super.canAutomationInsert(slot, stack);
    }

    /** 渲染用不到物品栏：白名单上的名字、绑定宝石的坐标都不发给附近的客户端，物品栏只走菜单 */
    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    /** 渲染用不到平衡值（界面走菜单） */
    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    // ------------------------------------------------------------------
    // 数值
    // ------------------------------------------------------------------

    /** 射程：3 + 2 × 基座层数，封顶 {@code max_radius}（32） */
    public static int radiusFor(int layers) {
        long raw = BASE_RADIUS + (long) RADIUS_PER_LAYER * Math.max(0, layers);
        return (int) Math.min(ECConfig.get(Config.maxRadius), raw);
    }

    /** 每击费用（不超过 MRU 上限，否则永远攒不够） */
    public int mruPerHit() {
        return Math.max(0, Math.min(ECConfig.get(Config.mruPerHit), mru.getMaxMRU()));
    }

    /** 对玩家的伤害：基础 + 当前最大生命 × 比例 */
    public static float playerDamage(LivingEntity target) {
        return (float) (ECConfig.get(Config.playerDamage) + target.getMaxHealth() * ECConfig.get(Config.playerHealthPercent));
    }

    public static float monsterDamage() {
        return (float) ECConfig.get(Config.monsterDamage);
    }

    private int spawnSafeRadius() {
        return testSpawnSafeRadius >= 0 ? testSpawnSafeRadius : ECConfig.get(Config.spawnSafeRadius);
    }

    private int raysPerGroup() {
        return testRaysPerGroup > 0 ? testRaysPerGroup : ECConfig.get(Config.raysPerGroup);
    }

    /** 两次放电之间隔几刻（配置 {@code strike_interval}，20~1200） */
    public int strikeInterval() {
        return testStrikeInterval > 0 ? testStrikeInterval : ECConfig.get(Config.strikeInterval);
    }

    /**
     * 警告表多久没见到一名玩家就忘掉他（下次进来重新警告）：{@link #WARN_FORGET_TICKS}，且不短于两次放电间隔的 2 倍。
     * 警告表只在放电时刷新；间隔配到 200 以上时如果还按 200 忘，上一次放电写的记录到下一次放电时一定已经删掉，
     * 每次放电都当成「第一次看见」重新警告，永远打不到玩家（4b 审查 COIL-1）。
     */
    public long warnForgetTicks() {
        return Math.max(WARN_FORGET_TICKS, 2L * strikeInterval());
    }

    /**
     * 线圈所在世界的游戏时间 → 全局表 {@link MRUCoilTargets} 用的主世界时间（加上两个时钟之差）。
     * 原版和纯 Forge 的各维度共用主世界时间，差为 0；混合端上插件建的世界有自己的时间（4b 审查 COIL-2）。
     * 传进来的可以是测试里往后推的时间，换算只加差值，不取当前时间。
     */
    public long globalTime(long levelNow) {
        if (level instanceof ServerLevel serverLevel) {
            return levelNow + (MRUCoilTargets.now(serverLevel.getServer()) - serverLevel.getGameTime());
        }
        return levelNow;
    }

    // ------------------------------------------------------------------
    // 状态
    // ------------------------------------------------------------------

    public StructureCache structure() {
        return structure;
    }

    /** 当前射程（不成形为 0） */
    public int radius() {
        return radius;
    }

    /** 数到的基座层数（还没数过为 -1） */
    public int layers() {
        return layers;
    }

    public boolean formed() {
        return structure.formed() && radius > 0;
    }

    /** 服务端：正在工作（成形、MRU 够一击、没有红石） */
    public boolean isActive() {
        return formed() && !powered && mru.getMRU() >= mruPerHit();
    }

    /** 客户端：服务端说它在工作（更新包同步来的，给待机电弧和基座火花用） */
    public boolean isClientActive() {
        return clientActive;
    }

    public Status status() {
        if (!formed()) {
            return structure.lastKnown() == StructureResult.MISMATCH ? Status.INCOMPLETE : Status.UNKNOWN;
        }
        if (powered) {
            return Status.REDSTONE;
        }
        return mru.getMRU() >= mruPerHit() ? Status.ACTIVE : Status.NO_MRU;
    }

    /** 这台打不打玩家（主人在界面里切） */
    public boolean hurtPlayersEnabled() {
        return hurtPlayers;
    }

    public void setHurtPlayers(boolean value) {
        if (hurtPlayers != value) {
            hurtPlayers = value;
            setChanged();
        }
    }

    /** 这个世界允许线圈打玩家（服务端配置） */
    public static boolean playersAllowedByConfig() {
        return ECConfig.get(Config.hurtPlayers);
    }

    /** 这台只打闯入者（配置 {@code trespassers_only}，GameTest 可按台覆盖） */
    public boolean trespassersOnly() {
        Boolean override = testTrespassersOnly;
        return override != null ? override : ECConfig.get(Config.trespassersOnly);
    }

    /** 结构不对时第一格不对的位置（相对线圈）；成形或不知道时为 null */
    @Nullable
    public BlockPos mismatchOffset() {
        StructurePattern.Mismatch mismatch = structure.mismatch();
        return mismatch == null || structure.formed() ? null : mismatch.pos().subtract(worldPosition);
    }

    // ------------------------------------------------------------------
    // 主人、白名单、访问权
    // ------------------------------------------------------------------

    /** 是不是主人本人：记下的主人，或单人里算作主人的房主（{@link ECProtection#actingOwner}） */
    public boolean isOwner(UUID id) {
        return id.equals(ECProtection.owner(this)) || id.equals(ECProtection.actingOwner(this));
    }

    /** 1 号格白名单上的 UUID（按物品缓存） */
    public Set<UUID> whitelist() {
        ItemStack stack = inventory.getStackInSlot(SLOT_LIST);
        if (stack != cachedList || stack.getTag() != cachedListTag) {
            cachedList = stack;
            cachedListTag = stack.getTag();
            whitelist = PlayerListItem.uuids(stack);
        }
        return whitelist;
    }

    public boolean isWhitelisted(UUID id) {
        return whitelist().contains(id);
    }

    /** 能拿放 0、1 号格、切换「打玩家」：主人本人、创造模式（服务端的 isCreative） */
    public boolean canEdit(Player player) {
        return player.isCreative() || isOwner(player.getUUID());
    }

    /** 能打开界面：能编辑的人，或者白名单上的人（只能看） */
    public boolean canView(Player player) {
        return canEdit(player) || isWhitelisted(player.getUUID());
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = level.getGameTime();
        if (now >= nextStructureCheck) {
            nextStructureCheck = now + StructureCache.MIN_INTERVAL;
            powered = level.hasNeighborSignal(worldPosition);
            refreshStructure(serverLevel, false);
        }
        boolean active = isActive();
        if (active != syncedActive) {
            syncedActive = active;
            markClientDirty();
        }
        if (active && now >= nextStrike) {
            nextStrike = now + strikeInterval();
            discharge(serverLevel, now);
        }
        flushStrikes(now);
    }

    /**
     * 方块的 neighborChanged 调：马上重看红石；正下方那格（基座一竖列的顶）变了就下一 tick 重数基座、标脏结构
     * （结构本身按 StructureCache 的节流重判）。旁边接着红石时钟也只是每次重看一下红石，不会每 tick 重数基座。
     */
    public void onNeighborChanged(BlockPos fromPos) {
        if (level == null || level.isClientSide) {
            return;
        }
        powered = level.hasNeighborSignal(worldPosition);
        if (fromPos.equals(worldPosition.below())) {
            structure.markDirty();
            nextStructureCheck = 0L;
        }
    }

    /**
     * 数基座、判底座、算射程；第一次成形（或射程变大）时给 {@link #playersNearColumn} 触发进度。
     * 基座层数变了（底座的位置跟着变）就立即重判，不等 StructureCache 的 20 tick 节流：否则这段时间里射程按新层数算、成形与否却还是旧位置的结果。
     */
    private void refreshStructure(ServerLevel serverLevel, boolean force) {
        int count = countHardeners();
        boolean moved = count != layers;
        layers = count;
        BlockPos origin = worldPosition.below(count + 1);
        boolean was = structure.formed();
        if (force || moved) {
            structure.checkNow(serverLevel, origin, Direction.NORTH);
        } else {
            structure.check(serverLevel, origin, Direction.NORTH);
        }
        int newRadius = structure.formed() ? radiusFor(count) : 0;
        if (structure.formed() && (!was || (radius > 0 && newRadius > radius))) {
            for (ServerPlayer player : playersNearColumn(serverLevel.players(), worldPosition, origin)) {
                StructureFormedTrigger.INSTANCE.trigger(player, ECTesla.COIL_STRUCTURE_ID, newRadius);
            }
        }
        radius = newRadius;
    }

    /**
     * 成形时拿进度的玩家：离「底座中心 → 线圈」这一竖列（线段，两端取方块中心）不超过 {@link StructureFormedTrigger#TRIGGER_RANGE}（16）格的、活着的
     * （旁观者也算，同 {@link StructureFormedTrigger#playersWithin}）。只按线圈算的话，15 层基座的塔最后站在地面补底座的人离线圈约 17 格，
     * 手册「特斯拉线圈」那一步永远不打勾（4b 审查 COIL-4）。纯函数，GameTest 直接测。
     */
    public static List<ServerPlayer> playersNearColumn(Collection<? extends ServerPlayer> candidates, BlockPos coil, BlockPos baseCenter) {
        double x = coil.getX() + 0.5D;
        double z = coil.getZ() + 0.5D;
        double minY = Math.min(coil.getY(), baseCenter.getY()) + 0.5D;
        double maxY = Math.max(coil.getY(), baseCenter.getY()) + 0.5D;
        double rangeSqr = StructureFormedTrigger.TRIGGER_RANGE * StructureFormedTrigger.TRIGGER_RANGE;
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : candidates) {
            if (player.isAlive() && player.distanceToSqr(x, Math.max(minY, Math.min(maxY, player.getY())), z) <= rangeSqr) {
                result.add(player);
            }
        }
        return result;
    }

    /** 线圈正下方连续的基座层数（同一竖列，线圈所在区块一定已加载） */
    private int countHardeners() {
        Block hardener = ECTesla.MRU_COIL_HARDENER.get();
        BlockPos.MutableBlockPos cursor = worldPosition.mutable();
        int count = 0;
        while (count < MAX_LAYERS_SCANNED) {
            cursor.move(Direction.DOWN);
            if (level.isOutsideBuildHeight(cursor) || !level.getBlockState(cursor).is(hardener)) {
                break;
            }
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------
    // 放电
    // ------------------------------------------------------------------

    /**
     * 放一次电（见类注释）。返回打中了几个。不管间隔、不管红石（GameTest 直接调它）。
     *
     * @param now 线圈所在世界的游戏时间（负缓存按它算；和全局表比较时由 {@link #globalTime} 换算成主世界时间）
     */
    public int discharge(ServerLevel serverLevel, long now) {
        return discharge(serverLevel, now, serverLevel.players());
    }

    /**
     * 同 {@link #discharge(ServerLevel, long)}，打玩家那一段只从 {@code candidates} 里挑（tick 里传 {@code level.players()}；
     * GameTest 的测试玩家不在玩家列表里，直接传进来，整段打玩家的流程——探测、警告、视线、打——都走真代码）。
     */
    public int discharge(ServerLevel serverLevel, long now, Collection<? extends ServerPlayer> candidates) {
        int cost = mruPerHit();
        if (!formed() || mru.getMRU() < cost) {
            return 0;
        }
        long global = globalTime(now);
        pruneCaches(now, global);
        int max = ECConfig.get(Config.maxTargets);
        int hits = 0;
        Vec3 center = center();
        int[] ownerProbes = {OWNER_PROBES_PER_DISCHARGE};
        if (playersAllowedByConfig() && hurtPlayers && ECProtection.owner(this) != null) {
            List<ServerPlayer> players = new ArrayList<>();
            for (ServerPlayer player : candidates) {
                if (playerCheck(player, now) == PlayerCheck.OK) {
                    players.add(player);
                }
            }
            players.sort(Comparator.comparingDouble(player -> player.distanceToSqr(center)));
            int[] rays = {raysPerGroup()};
            for (ServerPlayer player : players) {
                if (hits >= max || mru.getMRU() < cost) {
                    break;
                }
                if (MRUCoilTargets.recentlyStruck(player, global) || isUnseen(player, now)) {
                    continue;
                }
                // A1：只打闯进主人地盘的人（探测在视线和警告之前：不是闯入者的人不该收到警告）
                if (!mayStrikePlayer(player, ownerProbes)) {
                    continue;
                }
                Sight sight = sight(player, rays);
                if (sight == Sight.NO_BUDGET) {
                    break;
                }
                if (sight == Sight.BLOCKED) {
                    markUnseen(player, now);
                    continue;
                }
                if (!warningPassed(player, now)) {
                    continue;
                }
                if (strike(player, now)) {
                    hits++;
                }
            }
        }
        if (hits < max && mru.getMRU() >= cost && ECConfig.get(Config.hurtMonsters)) {
            List<Mob> mobs = serverLevel.getEntitiesOfClass(Mob.class, searchBox(), this::canTargetMob);
            mobs.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(center)));
            int[] rays = {raysPerGroup()};
            boolean ownerOnline = ECProtection.onlineOwner(this) != null;
            for (Mob mob : mobs) {
                if (hits >= max || mru.getMRU() < cost) {
                    break;
                }
                if (MRUCoilTargets.recentlyStruck(mob, global) || isUnseen(mob, now)) {
                    continue;
                }
                // A6：怪脚下那格主人能动才打。先只看记录：已知不能动的（邻居刷怪塔）、主人离线又没记过的直接跳过，不花射线、不探测
                BlockPos cell = mob.blockPosition();
                ECProtection.Verdict known = ownerCells.peek(this, cell);
                if (known == ECProtection.Verdict.DENY || (known == ECProtection.Verdict.UNKNOWN && !ownerOnline)) {
                    continue;
                }
                Sight sight = sight(mob, rays);
                if (sight == Sight.NO_BUDGET) {
                    break;
                }
                if (sight == Sight.BLOCKED) {
                    markUnseen(mob, now);
                    continue;
                }
                // 看得见了才以主人身份探测（关在墙里打不到的怪不发探测；每次放电的新探测有上限）
                if (known != ECProtection.Verdict.ALLOW && ownerPermission(cell, ownerProbes) != ECProtection.Verdict.ALLOW) {
                    continue;
                }
                if (strike(mob, now)) {
                    hits++;
                }
            }
        }
        return hits;
    }

    /**
     * 打一下（不查规则、不查视线；规则见 {@link #playerCheck} / {@link #canTargetMob}）。伤害生效才扣 MRU、记全局冷却、给玩家加负面状态。
     * GameTest 直接对不在世界里的测试玩家调它。{@code now} 是线圈所在世界的时间（同 {@link #discharge}）。
     */
    public boolean strike(LivingEntity target, long now) {
        if (level == null || level.isClientSide) {
            return false;
        }
        int cost = mruPerHit();
        if (mru.getMRU() < cost) {
            return false;
        }
        boolean isPlayer = target instanceof Player;
        float amount = isPlayer ? playerDamage(target) : monsterDamage();
        if (!(amount > 0F) || !target.hurt(dischargeSource(), amount)) {
            return false;
        }
        if (cost > 0) {
            mru.extractMRU(cost, true);
        }
        MRUCoilTargets.markStruck(target, globalTime(now));
        if (isPlayer && target.isAlive()) {
            applyEffects(target);
        }
        if (ECConfig.get(Config.generatesCorruption)) {
            ECCorruption.randomIncreaseAt(level, worldPosition, level.random, ECConfig.get(Config.corruptionPerHit));
        }
        pendingStrikes.add(target.getId());
        return true;
    }

    /** 这台线圈的伤害源（死亡消息写主人名字） */
    public DamageSource dischargeSource() {
        return new CoilDischargeSource(ECDamageTypes.holder(level, ECDamageTypes.MRU_DISCHARGE), ECProtection.ownerName(this));
    }

    /** 缓慢 II、虚弱 I、发光（数值走配置）：目标身上没有、等级更低或剩不到 2 秒才续；不带来源实体 */
    public static void applyEffects(LivingEntity target) {
        refreshEffect(target, MobEffects.MOVEMENT_SLOWDOWN, ECConfig.get(Config.slownessTicks), ECConfig.get(Config.slownessLevel) - 1);
        refreshEffect(target, MobEffects.WEAKNESS, ECConfig.get(Config.weaknessTicks), ECConfig.get(Config.weaknessLevel) - 1);
        refreshEffect(target, MobEffects.GLOWING, ECConfig.get(Config.glowingTicks), 0);
    }

    private static void refreshEffect(LivingEntity target, MobEffect effect, int ticks, int amplifier) {
        if (ticks <= 0 || amplifier < 0) {
            return;
        }
        MobEffectInstance current = target.getEffect(effect);
        if (current != null && current.getAmplifier() >= amplifier && !current.endsWithin(EFFECT_REFRESH_BELOW - 1)) {
            return;
        }
        target.addEffect(new MobEffectInstance(effect, ticks, amplifier), null);
    }

    /** 放完电把打中的目标交给客户端画电弧（走更新包，受 MRUBlockEntity 的 10 tick 节流；放电 20 tick 一次，赶得上） */
    private void flushStrikes(long now) {
        if (pendingStrikes.isEmpty()) {
            return;
        }
        strikeIds = pendingStrikes.stream().mapToInt(Integer::intValue).toArray();
        strikeTime = now;
        pendingStrikes.clear();
        markClientDirty();
    }

    // ------------------------------------------------------------------
    // 目标规则
    // ------------------------------------------------------------------

    /** 一名玩家能不能打（不含视线、警告、全局冷却；见类注释）。{@code now} 是线圈所在世界的时间，查重生保护时换算成主世界时间 */
    public PlayerCheck playerCheck(Player player, long now) {
        if (!ECProtection.isRealPlayer(player) || !player.isAlive() || player.isRemoved() || player.level() != level) {
            return PlayerCheck.NOT_TARGETABLE;
        }
        if (player.isCreative() || player.isSpectator()) {
            return PlayerCheck.CREATIVE;
        }
        if (!playersAllowedByConfig() || !hurtPlayers) {
            return PlayerCheck.PLAYERS_OFF;
        }
        if (ECProtection.owner(this) == null) {
            return PlayerCheck.NO_OWNER;
        }
        UUID id = player.getUUID();
        if (isOwner(id)) {
            return PlayerCheck.OWNER;
        }
        if (isWhitelisted(id)) {
            return PlayerCheck.WHITELISTED;
        }
        if (MRUCoilTargets.inRespawnGrace(id, globalTime(now))) {
            return PlayerCheck.RESPAWN_GRACE;
        }
        if (nearWorldSpawn(player)) {
            return PlayerCheck.SPAWN_AREA;
        }
        if (player.invulnerableTime > 10 || player.isInvulnerable()) {
            return PlayerCheck.INVULNERABLE;
        }
        return inRange(player) ? PlayerCheck.OK : PlayerCheck.OUT_OF_RANGE;
    }

    /** 能不能打这只生物（不含视线、全局冷却） */
    public boolean canTargetMob(Mob mob) {
        return mob.isAlive() && !mob.isRemoved() && mob.level() == level
                && mob instanceof Enemy
                && !ECEntityRules.isSpared(mob)
                && !mob.getType().is(Tags.EntityTypes.BOSSES)
                && !mob.isPersistenceRequired()
                && !mob.getType().is(ECTesla.MRU_COIL_IMMUNE)
                && !mob.isInvulnerable()
                && mob.invulnerableTime <= 10
                && inRange(mob);
    }

    /**
     * A1：这名玩家站的地方他自己有没有权限——以<b>他本人</b>的身份对他脚下那格发一次破坏探测（{@link ECProtection#check}，
     * 预算按这台线圈算，结果按（玩家、格子）缓存 {@code probe_cache_ticks}）。距离是 0，按距离判的反作弊不会拦。
     * {@link ECProtection.Verdict#DENY} = 他在别人的领地里、没被信任 = 闯入者；野外、自己的领地、被信任的领地里是 ALLOW；
     * 预算用完 / 区块没加载是 UNKNOWN（这一轮先不打）。出生点保护、世界边界外、管理员圈的公共保护区也会探测成 DENY，
     * 这些地方线圈主人同样没有权限，{@link #mayStrikePlayer} 的第二道（那格要是主人能动的）把它们挡掉。
     */
    public ECProtection.Verdict standingPermission(ServerPlayer player) {
        if (level == null) {
            return ECProtection.Verdict.UNKNOWN;
        }
        return ECProtection.check(player, level, player.blockPosition(), this);
    }

    /**
     * 过了 {@link #playerCheck} 的人还要过这一关（不含视线、警告）：{@code trespassers_only} 关着时都打；开着时只打闯入者，按这个顺序判，
     * 前一步不过就不做后一步（探测越少越好：混合端上被拒的探测会让被探测的人收到领地插件的「没有权限」）——
     * <ol>
     *   <li>先看记录（{@link OwnerCellMemory#peek}，不探测）：已知主人在这格没权限（射程伸进了第三个人的领地、公共保护区）→ 不打，被打的人也不探测；
     *       主人离线、这格又没确认过 → 不打、不探测；</li>
     *   <li>以被打的人本人身份探测他脚下那格（{@link #standingPermission}），DENY 才往下（野外、自己的领地、被信任的地方都不打）；</li>
     *   <li>那格主人能动：记录里有就用记录，没有才以主人身份探测（和打怪共用记录与每次放电的探测上限）。</li>
     * </ol>
     * 预算用完、区块没加载（UNKNOWN）的这一轮都不打。
     */
    public boolean mayStrikePlayer(ServerPlayer player) {
        return mayStrikePlayer(player, new int[] {OWNER_PROBES_PER_DISCHARGE});
    }

    private boolean mayStrikePlayer(ServerPlayer player, int[] ownerProbes) {
        if (!trespassersOnly()) {
            return true;
        }
        BlockPos feet = player.blockPosition();
        ECProtection.Verdict known = ownerCells.peek(this, feet);
        if (known == ECProtection.Verdict.DENY) {
            return false;
        }
        if (known == ECProtection.Verdict.UNKNOWN && ECProtection.onlineOwner(this) == null) {
            return false;
        }
        if (standingPermission(player) != ECProtection.Verdict.DENY) {
            return false;
        }
        return known == ECProtection.Verdict.ALLOW || ownerPermission(feet, ownerProbes) == ECProtection.Verdict.ALLOW;
    }

    /** 这格主人能不能动：记录给得出结论就用记录；要新探测时先花一格本次放电的上限（{@code ownerProbes[0]}），用完了这一轮按 UNKNOWN 算 */
    private ECProtection.Verdict ownerPermission(BlockPos pos, int[] ownerProbes) {
        ECProtection.Verdict known = ownerCells.peek(this, pos);
        if (known != ECProtection.Verdict.UNKNOWN) {
            return known;
        }
        if (ownerProbes[0] <= 0) {
            return ECProtection.Verdict.UNKNOWN;
        }
        ownerProbes[0]--;
        return ownerCells.check(this, pos);
    }

    /**
     * A6：怪脚下那格主人能不能动（{@link OwnerCellMemory#check}）：记录给得出结论就用记录，否则主人在线用主人真身探测、结论记下来；
     * 主人离线只认记下的格子，没记过的不打；没有主人的线圈（多人服）不打怪。线圈的射程伸进邻居的领地时，不会再清掉邻居刷怪塔里的怪。只有 ALLOW 才打。
     * 放电时不直接调它：先 {@link OwnerCellMemory#peek}、过了视线再探测（见 {@link #discharge}）。
     */
    public ECProtection.Verdict mobPermission(Mob mob) {
        return ownerCells.check(this, mob.blockPosition());
    }

    /** 打怪、打闯入者共用的「主人能不能动这格」记录（GameTest 看、测过期用） */
    public OwnerCellMemory ownerCells() {
        return ownerCells;
    }

    /** 目标碰撞箱上离线圈中心最近的一点在射程球里 */
    public boolean inRange(Entity entity) {
        if (radius <= 0) {
            return false;
        }
        Vec3 c = center();
        AABB box = entity.getBoundingBox();
        double dx = c.x - Math.max(box.minX, Math.min(c.x, box.maxX));
        double dy = c.y - Math.max(box.minY, Math.min(c.y, box.maxY));
        double dz = c.z - Math.max(box.minZ, Math.min(c.z, box.maxZ));
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }

    /** ④ 世界出生点水平 spawn_safe_radius 格内（只在主世界：别的维度没有「出生点」） */
    public boolean nearWorldSpawn(Entity entity) {
        int safe = spawnSafeRadius();
        if (safe <= 0 || level == null || level.dimension() != Level.OVERWORLD) {
            return false;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        double dx = entity.getX() - (spawn.getX() + 0.5D);
        double dz = entity.getZ() - (spawn.getZ() + 0.5D);
        return dx * dx + dz * dz <= (double) safe * safe;
    }

    /**
     * ② 第一次进射程先警告：第一次看见（或上次看见已过 {@link #warnForgetTicks}、或他死过 / 重生过 / 换过维度）时在动作栏警告、返回 false；
     * 之后 {@code warning_ticks}（40）刻内都返回 false，过了才返回 true。
     * {@code now} 是线圈所在世界的时间；警告表里记的是换算后的主世界时间，才能和全局表的「警告重新计时」比较。
     */
    public boolean warningPassed(ServerPlayer player, long now) {
        int warnTicks = ECConfig.get(Config.warningTicks);
        if (warnTicks <= 0) {
            return true;
        }
        long global = globalTime(now);
        UUID id = player.getUUID();
        long[] entry = warnings.get(id);
        if (entry == null || entry[0] <= MRUCoilTargets.warningReset(id) || global - entry[1] > warnForgetTicks() || global < entry[0]) {
            warnings.put(id, new long[] {global, global});
            sendWarning(player);
            return false;
        }
        entry[1] = global;
        return global - entry[0] >= warnTicks;
    }

    /** 这名玩家在本台的警告表里（警告过、还没被忘掉；GameTest 用） */
    public boolean hasWarned(UUID id) {
        return warnings.containsKey(id);
    }

    private void sendWarning(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.essentialcraft5.mru_coil.warning", ECProtection.ownerName(this))
                .withStyle(ChatFormatting.RED), true);
        player.playNotifySound(ECSounds.GEN_ELECTRICITY.get(), SoundSource.BLOCKS, 1F, 1F);
    }

    private boolean isUnseen(Entity entity, long now) {
        return unseen.get(entity.getId()) > now;
    }

    private void markUnseen(Entity entity, long now) {
        int ticks = ECConfig.get(Config.unseenCacheTicks);
        if (ticks > 0) {
            unseen.put(entity.getId(), now + ticks);
        }
    }

    /** 清掉过期的负缓存（所在世界时间 now）和很久没见到的警告记录（主世界时间 global） */
    private void pruneCaches(long now, long global) {
        if (!unseen.isEmpty()) {
            unseen.int2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
        }
        if (!warnings.isEmpty()) {
            long forget = warnForgetTicks();
            warnings.values().removeIf(entry -> global - entry[1] > forget || global < entry[0]);
        }
    }

    // ------------------------------------------------------------------
    // 视线
    // ------------------------------------------------------------------

    /** 看不看得见（不花预算、不记缓存；测试用） */
    public boolean canSee(LivingEntity target) {
        return sight(target, new int[] {2}) == Sight.VISIBLE;
    }

    /** 先看眼睛、不通再看碰撞箱中心；预算用完返回 NO_BUDGET（不算「看不见」，不进负缓存） */
    private Sight sight(LivingEntity target, int[] budget) {
        Vec3 from = emitPoint();
        Vec3[] points = {target.getEyePosition(), target.getBoundingBox().getCenter()};
        for (Vec3 to : points) {
            if (budget[0] <= 0) {
                return Sight.NO_BUDGET;
            }
            budget[0]--;
            if (clearLine(from, to)) {
                return Sight.VISIBLE;
            }
        }
        return Sight.BLOCKED;
    }

    /**
     * from → to 之间有没有挡视线的方块：按视觉形状（{@code getVisualShape}，玻璃、玻璃板、铁栏杆为空），液体不挡，
     * 线圈自己这一竖列（线圈、基座、底座中心）不挡，没加载的区块当挡住（直接读已加载的区块，不触发加载）。
     */
    private boolean clearLine(Vec3 from, Vec3 to) {
        return blockerOnLine(from, to) == null;
    }

    /** {@link #clearLine} 的判法：返回挡住视线的第一格（没加载的区块里那一格也算挡住），看得通返回 null */
    @Nullable
    private BlockPos blockerOnLine(Vec3 from, Vec3 to) {
        int x = worldPosition.getX();
        int z = worldPosition.getZ();
        int top = worldPosition.getY();
        int bottom = top - Math.max(0, layers) - 1;
        return BlockGetter.traverseBlocks(from, to, level, (lvl, pos) -> {
            if (pos.getX() == x && pos.getZ() == z && pos.getY() <= top && pos.getY() >= bottom) {
                return null;
            }
            LevelChunk chunk = lvl.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                return pos.immutable();
            }
            BlockState state = chunk.getBlockState(pos);
            if (state.isAir()) {
                return null;
            }
            VoxelShape shape = state.getVisualShape(lvl, pos, CollisionContext.empty());
            return !shape.isEmpty() && shape.clip(from, to, pos) != null ? pos.immutable() : null;
        }, lvl -> null);
    }

    // ------------------------------------------------------------------
    // 几何
    // ------------------------------------------------------------------

    public Vec3 center() {
        return Vec3.atCenterOf(worldPosition);
    }

    /** 电弧和视线的起点（原作 (0.5, 0.8, 0.5)） */
    public Vec3 emitPoint() {
        return new Vec3(worldPosition.getX() + 0.5D, worldPosition.getY() + EMIT_HEIGHT, worldPosition.getZ() + 0.5D);
    }

    /** 查怪的立方体：±射程，竖直夹到世界上下界 */
    public AABB searchBox() {
        AABB box = new AABB(worldPosition).inflate(radius);
        if (level != null) {
            box = new AABB(box.minX, Math.max(box.minY, level.getMinBuildHeight()), box.minZ,
                    box.maxX, Math.min(box.maxY, level.getMaxBuildHeight()), box.maxZ);
        }
        return testSearchClip != null ? box.intersect(testSearchClip) : box;
    }

    // ------------------------------------------------------------------
    // 客户端
    // ------------------------------------------------------------------

    /** 待机电弧（客户端，相对方块角；BER 画） */
    @Nullable
    public ArcEffect idleArc() {
        return idleArc;
    }

    @Override
    protected void clientTick() {
        if (idleArc != null && idleArc.tick()) {
            idleArc = null;
        }
        if (!clientActive) {
            idleArc = null;
        } else if (idleArc == null && level != null) {
            // 原作 L149：从 (0.5, 0.9, 0.5) 到附近随机一点，存活 20 tick，一条接一条
            Vec3 start = new Vec3(0.5D, 0.9D, 0.5D);
            Vec3 end = start.add(level.random.nextDouble() - level.random.nextDouble(),
                    level.random.nextDouble() - level.random.nextDouble(), level.random.nextDouble() - level.random.nextDouble());
            idleArc = new ArcEffect(level.random, start, end, ArcEffect.Style.COIL_IDLE);
        }
    }

    /** 只包住线圈和待机电弧（打向目标的放电由客户端特效在世界坐标里画）；原作是无限大 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5D);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("active", isActive());
        if (strikeIds.length > 0 && level != null && level.getGameTime() - strikeTime <= STRIKE_SHOW_TICKS) {
            tag.putIntArray("strikes", strikeIds);
            tag.putLong("strike_time", strikeTime);
        }
        return tag;
    }

    // ------------------------------------------------------------------
    // 存读档、界面
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        structure.save(tag);
        tag.putBoolean("hurt_players", hurtPlayers);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        structure.load(tag);
        hurtPlayers = !tag.contains("hurt_players") || tag.getBoolean("hurt_players");
        // "active" / "strikes" 只在更新包里有（getUpdateTag），存档里没有
        if (tag.contains("active")) {
            clientActive = tag.getBoolean("active");
        }
        if (tag.contains("strike_time") && level != null && level.isClientSide) {
            long time = tag.getLong("strike_time");
            if (time != clientStrikeTime) {
                clientStrikeTime = time;
                int[] ids = tag.getIntArray("strikes");
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TeslaClient.coilStrike(this, ids));
            }
        }
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MRUCoilMenu(id, inventory, this, player);
    }

    // ------------------------------------------------------------------
    // 只给 GameTest 用（按台、不存盘；GameTest 里别用 ConfigValue.set 改真配置）
    // ------------------------------------------------------------------

    /** 查怪的范围再和这个框取交集（传测试结构的范围），别打到旁边并行测试的怪；null 取消 */
    public void limitSearchForTest(@Nullable AABB clip) {
        this.testSearchClip = clip;
    }

    /** 这台的出生点保护半径改成 radius（测试结构离世界出生点很近；负数取消） */
    public void setSpawnSafeRadiusForTest(int radius) {
        this.testSpawnSafeRadius = radius;
    }

    /** 这台每次放电、每组的视线预算改成 rays（测负缓存时用不着真摆 5 只关在墙后的怪；≤ 0 取消） */
    public void setRaysPerGroupForTest(int rays) {
        this.testRaysPerGroup = rays;
    }

    /** 这台的放电间隔改成 ticks（测间隔配得长时警告表不会先把人忘掉；≤ 0 取消） */
    public void setStrikeIntervalForTest(int ticks) {
        this.testStrikeInterval = ticks;
    }

    /** 这台的 {@code trespassers_only} 改成 value（null 取消，按配置） */
    public void setTrespassersOnlyForTest(@Nullable Boolean value) {
        this.testTrespassersOnly = value;
    }

    /** 立即重数基座、重判结构、重看红石（不等 20 tick） */
    public void refreshNowForTest() {
        if (level instanceof ServerLevel serverLevel) {
            powered = level.hasNeighborSignal(worldPosition);
            refreshStructure(serverLevel, true);
        }
    }

    /**
     * 测试失败时写进断言信息：查怪框里每只怪的类型、格子、离线圈中心多远、生命、过不过 {@link #canTargetMob}、在不在负缓存和全局冷却里、
     * 主人记录怎么说（{@link OwnerCellMemory#peek}），以及眼睛、碰撞箱中心两条视线各挡在哪一格（那格的区块没加载时标出来）；
     * 最后是主人在不在线。只读：不花视线预算、不探测、不记缓存。{@code now} 同 {@link #discharge}。
     */
    public String describeTargetsForTest(ServerLevel serverLevel, long now) {
        StringBuilder out = new StringBuilder();
        Vec3 center = center();
        Vec3 from = emitPoint();
        long global = globalTime(now);
        for (Mob mob : serverLevel.getEntitiesOfClass(Mob.class, searchBox())) {
            BlockPos cell = mob.blockPosition();
            out.append(String.format(Locale.ROOT, "[%s %s d=%.2f hp=%.1f target=%s unseen=%s struck=%s owner=%s sight=",
                    mob.getType().getDescriptionId(), cell.toShortString(), Math.sqrt(mob.distanceToSqr(center)), mob.getHealth(),
                    canTargetMob(mob), isUnseen(mob, now), MRUCoilTargets.recentlyStruck(mob, global), ownerCells.peek(this, cell)));
            for (Vec3 to : new Vec3[] {mob.getEyePosition(), mob.getBoundingBox().getCenter()}) {
                BlockPos blocker = blockerOnLine(from, to);
                if (blocker == null) {
                    out.append("通 ");
                } else {
                    boolean unloaded = serverLevel.getChunkSource().getChunkNow(blocker.getX() >> 4, blocker.getZ() >> 4) == null;
                    out.append("挡@").append(blocker.toShortString()).append(unloaded ? "(区块没加载) " : " ");
                }
            }
            out.append(']');
        }
        return out.append(" 主人在线=").append(ECProtection.onlineOwner(this) != null).toString();
    }

    // ------------------------------------------------------------------
    // 伤害源
    // ------------------------------------------------------------------

    /**
     * 线圈的伤害源：{@code mru_discharge}，<b>不带攻击者、不带位置</b>（见类注释），死亡消息写上主人名字：
     * {@code death.attack.essentialcraft5.mru_discharge.coil}「%1$s被%2$s的 MRU 特斯拉线圈电死了」，
     * 5 秒内被别人打过时用 {@code ….coil.player}（第 3 个参数是那个人，同原版无攻击者伤害的 .player 规则）。
     * 没有主人名字时（线圈没有主人只打怪）退回框架的 {@code death.attack.essentialcraft5.mru_discharge}。
     */
    public static final class CoilDischargeSource extends DamageSource {

        private final String ownerName;

        public CoilDischargeSource(Holder<DamageType> type, String ownerName) {
            super(type);
            this.ownerName = ownerName == null ? "" : ownerName;
        }

        public String ownerName() {
            return ownerName;
        }

        @Override
        public Component getLocalizedDeathMessage(LivingEntity killed) {
            if (ownerName.isEmpty()) {
                return super.getLocalizedDeathMessage(killed);
            }
            String key = "death.attack." + type().msgId() + ".coil";
            LivingEntity credit = killed.getKillCredit();
            return credit != null
                    ? Component.translatable(key + ".player", killed.getDisplayName(), ownerName, credit.getDisplayName())
                    : Component.translatable(key, killed.getDisplayName(), ownerName);
        }
    }
}
