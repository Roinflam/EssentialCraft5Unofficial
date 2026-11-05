package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.block.MatrixCrucibleBlock;
import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.entity.MatrixEchoEntity;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.menu.MatrixCrucibleMenu;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECMatrix;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 矩阵试炼的状态机（第十三批 13a，batch13.md §3.1.2、§3.1.6，D16 = A；限制见 policy §4.16）。只在服务端、只在内存里：
 * 试炼不跨重启，下线就算失败。每人同时一场，每台淬炼台同时一场。
 *
 * <h2>开始（{@link #tryStart}）</h2>
 * 先过 {@link MatrixTrialChecks#evaluate}（缺一样都开不了，什么都不扣）。回响模式下先放出第一只回响，<b>放得出来才扣能量</b>：
 * 台子周围 {@code spawn_radius_min ~ max} 格的空位里随机找，每个点要能站（脚下结实、身体不碰方块、不在液体里），
 * 并且以试炼者的身份问那一格能不能动（{@link ECProtection#check}）；一只都放不出来（别人的领地、没空地、被别的模组或插件拒了）就不开始、
 * 不扣东西。然后扣：五芒星柱子的有效 ESPE（扣之前当场重判一次五芒星的档位）、台子里的 MRU、UBMRU（先扣试炼储备，不够再扣身上的），
 * 锁住祭品格（成功才消耗），给试炼者一条只有他看得见的首领血条。
 *
 * <h2>进行</h2>
 * <ul>
 *   <li>回响：同时最多 {@code alive} 只、陆续放（两只之间至少 {@value #SPAWN_INTERVAL} 刻），打倒 {@code count} 只成功；
 *       只有试炼者亲手打倒的才算（别的死法、被挪远了的补一只）；全服回响总数到 {@code max_echoes_total} 时先不放新的，这期间不计时。</li>
 *   <li>回响走不到试炼者身边（墙后、封闭的地下室、坑里），每 {@value #STUCK_CHECK_INTERVAL} 刻查一次、连续 {@value #STUCK_STRIKES} 次，
 *       就挪到试炼者周围 {@value #RELOCATE_MIN}~{@value #RELOCATE_MAX} 格能站、他能动的空地上（{@link #checkStuck}）；不算打倒、不另补。</li>
 *   <li>中途放不出回响时按原因分开（batch13.md §3.1.2 只定了「一只都放不出来时中止、退还」，开始时那一条由「先放回响再扣能量」做到；
 *       中途的口径在这里收紧，免得玩家自己堵死生成点、把失败代价躲掉）：
 *       <ul>
 *         <li>这场放过回响的格子（{@link Trial#usedCells}）都还站得下、但以试炼者身份问已经不能动了（领地中途变了），
 *             或者找到了地方、回响却被别的模组或插件拒了：场上又一只都没有，持续 {@value #STALL_TICKS} 刻，试炼<b>中止</b>、
 *             已扣的能量退回（UBMRU 回储备和身上、MRU 回台子、ESPE 放回那座五芒星的柱子；放不下的部分提示里说），不算失败、不冷却；</li>
 *         <li>放过的格子被方块、液体堵住了（玩家自己能控制的地形）：不中止、不退，计时照走，到点按超时失败。</li>
 *       </ul></li>
 *   <li>和平难度或服主关了回响（{@code trial.mobs_enabled = false}）：不放回响，改「引导」，在台子 {@value MatrixTrialChecks#START_RADIUS} 格内站着不动、
 *       不受伤 30 + 10 × 阶 秒（{@link #channelTicks}）；动了、受伤了、走出 3 格都从头算。引导也有时限：要站的时间 × 2 +
 *       {@value #CHANNEL_SLACK_SECONDS} 秒，首领血条上一直写着还剩几秒，到点还没站够按超时失败（不设时限的话，站在 3~12 格之间就能一直占着台子）。
 *       回响试炼进行中改成和平难度，回响会被收回，这时试炼当场改成引导、时限换成引导的。</li>
 * </ul>
 *
 * <h2>失败（{@link Failure}）与成功</h2>
 * 死亡、下线、换维度、离台子超过 {@code leave_radius} 格（至少 {@value #MIN_LEAVE_RADIUS} 格，比开始距离远）、超时，另加台子没了（被挖、被换掉）：
 * 已扣的能量不退、祭品留在格子里（台子被挖掉时祭品照常掉在原地）、冷却 {@code fail_cooldown_seconds}（存玩家数据，退出重进不清）。
 * 换维度另有一条兜底：玩家对象被换掉了（从末地出口传送门回主世界不发换维度事件，看完结尾后服务端另建一个玩家对象），
 * 或者旧对象以别的原因被移除了，每刻开头都会发现；冷却、提示记在当前在线的那个玩家对象上。
 * 成功：阶 +1（{@link MatrixRank#set}）、按位置消耗祭品、矩阵损伤 + {@code success_matrix_damage_per_rank} × 新阶（走带来源的
 * {@link CorruptionEffects#addMatrixDamage(IPlayerData, long, CorruptionEffects.Source)}，按新阶打折、有上限）、补手册的阶步骤进度、
 * 标题 + 原版信标启动和不死图腾的声音。
 *
 * <h2>试炼储备（{@link #deposit}）</h2>
 * 拿着绑定自己的灵魂石潜行右键任意一台淬炼台：身上的 UBMRU 存进本人的试炼储备，上限是下一阶要的 UBMRU；记在人身上，哪台都能用，
 * 死亡不丢，只能花在试炼上。一、二阶要 5 000 / 2 万 UBMRU，而 UBMRU 死亡清零、矩阵换能器还在一直扣，不能分次存的话一死就白攒。
 * 试炼进行中不收（开始时储备已经扣过，这时再存会按旧阶的上限存进去，升阶以后就卡在里面）；储备超过当前上限的部分
 * （升阶、指令改阶、迁移补阶以后上限变小了）在存的时候和试炼成功时退回身上（{@link #returnExcessReserve}）。
 */
public final class MatrixTrials {

    /** 两只回响之间至少隔几刻（陆续放出来） */
    public static final int SPAWN_INTERVAL = 10;
    /** 每次找生成点最多试几个位置 */
    public static final int SPAWN_TRIES = 16;
    /** 中途放不出回响、场上又没有，持续这么多刻就中止并退能量 */
    public static final int STALL_TICKS = 200;
    /** 回响离台子超过「离开半径 + 这么多」就收回、另补一只（跑丢了、被推远了） */
    public static final int ECHO_LEASH_EXTRA = 8;
    /** 引导时脚挪开超过这么远就算动了 */
    public static final double CHANNEL_MOVE_TOLERANCE = 0.3D;
    /** 引导的时限 = 要站的时间 × 2 + 这么多秒 */
    public static final int CHANNEL_SLACK_SECONDS = 60;
    /** 离开半径至少这么多格：比开始距离（{@value MatrixTrialChecks#START_RADIUS}）远，免得一开始就判走远了 */
    public static final int MIN_LEAVE_RADIUS = (int) Math.ceil(MatrixTrialChecks.START_RADIUS) + 1;
    /** 回收距离至少是最远生成距离再加这么多格，免得刚放出来的回响下一刻就被收回 */
    public static final int LEASH_PAST_SPAWN = 4;
    /** 卡住检测：每只回响每隔这么多刻查一次 */
    public static final int STUCK_CHECK_INTERVAL = 20;
    /** 卡住检测：连续这么多次走不到试炼者身边就挪过去 */
    public static final int STUCK_STRIKES = 3;
    /** 卡住的回响挪到试炼者周围多远（格） */
    public static final int RELOCATE_MIN = 2;
    public static final int RELOCATE_MAX = 4;
    /** 每场最多记几个放过回响的格子（判断中途放不出来的原因用） */
    public static final int MAX_USED_CELLS = 16;
    /** 找生成点时同一列上下试的高度（相对台子） */
    private static final int[] DY_ORDER = {0, 1, -1, 2, -2, 3, -3};

    /** 进行中的试炼：试炼者 UUID → 试炼（只在服务端主线程用） */
    private static final Map<UUID, Trial> TRIALS = new LinkedHashMap<>();

    /** GameTest 覆盖值（null = 按配置） */
    @Nullable
    private static int[] testSpawnRadius;
    @Nullable
    private static Integer testLeaveRadius;
    @Nullable
    private static Integer testChannelTicks;
    @Nullable
    private static Boolean testChannelMode;
    private static boolean testEchoNoAi;
    @Nullable
    private static Integer testChannelSlackSeconds;

    private MatrixTrials() {
    }

    // ------------------------------------------------------------------ 结果枚举

    /** 失败的五种原因（另加台子没了） */
    public enum Failure {
        DEATH("death"),
        LOGOUT("logout"),
        DIMENSION("dimension"),
        LEFT("left"),
        TIMEOUT("timeout"),
        CRUCIBLE("crucible");

        private final String key;

        Failure(String key) {
            this.key = key;
        }

        /** 语言键 message.essentialcraft5.matrix_trial.failed.&lt;key&gt; */
        public String key() {
            return key;
        }
    }

    /** 点「开始」的结果 */
    public enum StartResult {
        STARTED,
        /** 条件没满足（原因见 {@link Start#blocker()}） */
        BLOCKED,
        /** 回响一只都放不出来（领地、空地），什么都没扣 */
        NO_ROOM,
        /** 周围的格子这一刻还问不出能不能动（探测预算、区块），什么都没扣，稍后再点 */
        ROOM_UNKNOWN,
        /** 核对时够、扣的时候不够（同一刻里被别的东西花掉了，或者五芒星当场重判档位不够了），已扣的退回 */
        PAYMENT,
        /** 找到了地方，回响却被别的模组或插件拒了（实体加不进世界），什么都没扣 */
        SPAWN_DENIED
    }

    public record Start(StartResult result, @Nullable MatrixTrialChecks.Blocker blocker) {

        public boolean started() {
            return result == StartResult.STARTED;
        }
    }

    /**
     * 找生成点的结果。pos 为 null 时后三项说明原因：
     *
     * @param unknown        有格子这一刻还问不出能不能动
     * @param claimChanged   这场放过回响的格子都还站得下、但以试炼者身份问已经不能动了（领地中途变了）
     * @param terrainBlocked 这场放过回响的格子有被方块、液体堵住的
     */
    public record Placement(@Nullable Vec3 pos, boolean unknown, boolean claimChanged, boolean terrainBlocked) {

        public Placement(@Nullable Vec3 pos, boolean unknown) {
            this(pos, unknown, false, false);
        }
    }

    /** 存试炼储备的结果：存进去多少；存不进时 reason 是语言键 message.essentialcraft5.matrix_trial.reserve.&lt;reason&gt; 的后缀 */
    public record Deposit(int amount, @Nullable String reason) {
    }

    // ------------------------------------------------------------------ 一场试炼

    public static final class Trial {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final BlockPos pos;
        private final MatrixRankData.Entry entry;
        private boolean channel;
        private int ticks;
        private int timeLimit;
        private int killed;
        private final List<MatrixEchoEntity> echoes = new ArrayList<>();
        private int spawnCooldown;
        private int stall;
        private boolean waiting;
        /** 这场成功放过回响的格子（脚下那格，最多 {@value #MAX_USED_CELLS} 个）：中途放不出来时拿它们判原因 */
        private final Set<BlockPos> usedCells = new LinkedHashSet<>();
        /** 上一次放回响时实体被拒了（别的模组、插件取消了加入世界） */
        private boolean spawnRejected;
        /** 中止时能量是不是全退回去了（柱子、台子满了放不下的算没退全） */
        private boolean refundedInFull = true;
        // 付了多少（中止时按这些退）
        private int paidReserve;
        private int paidBody;
        private int paidMru;
        private int paidEspe;
        @Nullable
        private BlockPos pentaclePos;
        private int pentacleTier = -1;
        // 引导
        private int channelProgress;
        @Nullable
        private Vec3 anchor;
        private boolean hurt;
        private final ServerBossEvent bar;

        private Trial(ServerPlayer player, ServerLevel level, BlockPos pos, MatrixRankData.Entry entry, boolean channel) {
            this.player = player;
            this.level = level;
            this.pos = pos.immutable();
            this.entry = entry;
            this.bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
            setChannel(channel);
        }

        private void setChannel(boolean value) {
            channel = value;
            ticks = 0;
            channelProgress = 0;
            anchor = null;
            timeLimit = value ? channelTicks(entry.rank()) * 2 + channelSlackSeconds() * 20 : entry.echoes().timeSeconds() * 20;
        }

        public ServerPlayer player() {
            return player;
        }

        public ServerLevel level() {
            return level;
        }

        public BlockPos pos() {
            return pos;
        }

        /** 这场在试第几阶 */
        public int rank() {
            return entry.rank();
        }

        public MatrixRankData.Entry entry() {
            return entry;
        }

        public boolean channel() {
            return channel;
        }

        /** 已经计了多少刻（等全服名额时不计） */
        public int ticks() {
            return ticks;
        }

        public int timeLimit() {
            return timeLimit;
        }

        /** 试炼者亲手打倒了几只 */
        public int killed() {
            return killed;
        }

        /** 场上活着的回响（只读副本） */
        public List<MatrixEchoEntity> echoes() {
            return List.copyOf(echoes);
        }

        public int channelProgress() {
            return channelProgress;
        }

        public int channelNeeded() {
            return channelTicks(entry.rank());
        }

        /** 这场放过回响的格子（只读副本） */
        public List<BlockPos> usedCells() {
            return List.copyOf(usedCells);
        }

        /** 中止时能量是不是全退回去了 */
        public boolean refundedInFull() {
            return refundedInFull;
        }

        /** 看得见这场首领血条的人（只有试炼者） */
        public Collection<ServerPlayer> barViewers() {
            return bar.getPlayers();
        }

        /** 这场是不是在这台台子上 */
        public boolean at(MatrixCrucibleBlockEntity crucible) {
            return crucible.getLevel() == level && pos.equals(crucible.getBlockPos());
        }

        @Nullable
        MatrixCrucibleBlockEntity crucible() {
            if (!level.isLoaded(pos)) {
                return null;
            }
            return level.getBlockEntity(pos) instanceof MatrixCrucibleBlockEntity be && !be.isRemoved() ? be : null;
        }

        private Vec3 center() {
            return Vec3.atCenterOf(pos);
        }
    }

    // ------------------------------------------------------------------ 查询

    /** 这名玩家正在进行的试炼（没有返回 null） */
    @Nullable
    public static Trial trialOf(UUID player) {
        return TRIALS.get(player);
    }

    /** 全部进行中的试炼（只读） */
    public static Collection<Trial> trials() {
        return List.copyOf(TRIALS.values());
    }

    /** 全服活着的回响总数 */
    public static int totalEchoes() {
        int total = 0;
        for (Trial trial : TRIALS.values()) {
            total += trial.echoes.size();
        }
        return total;
    }

    /** 这只回响该打谁：属于某场进行中的试炼时返回试炼者，否则 null（回响会自己消失） */
    @Nullable
    public static ServerPlayer targetFor(MatrixEchoEntity echo) {
        UUID id = echo.trialist();
        Trial trial = id == null ? null : TRIALS.get(id);
        if (trial == null || !trial.echoes.contains(echo)) {
            return null;
        }
        return trial.player;
    }

    /** 这次试炼走引导：和平难度，或者服主关了回响 */
    public static boolean channelMode(@Nullable Level level) {
        if (testChannelMode != null) {
            return testChannelMode;
        }
        return MatrixRank.peaceful(level) || !MatrixTrialConfig.mobsEnabled();
    }

    /** 引导要站多少刻：(peaceful_channel_seconds + 10 × 阶) × 20 */
    public static int channelTicks(int rank) {
        if (testChannelTicks != null) {
            return testChannelTicks;
        }
        return (MatrixTrialConfig.peacefulChannelSeconds() + 10 * Math.max(0, rank)) * 20;
    }

    /** 试炼储备的上限：下一阶要的 UBMRU（已经九阶、下一阶没数据时是 0） */
    public static int reserveCap(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        int rank = data == null ? 0 : data.getMatrixRank();
        MatrixRankData.Entry next = rank < MatrixRank.MAX ? MatrixRankData.get(rank + 1) : null;
        return next == null ? 0 : next.ubmru();
    }

    private static int spawnRadiusMin() {
        return testSpawnRadius != null ? testSpawnRadius[0] : MatrixTrialConfig.spawnRadiusMin();
    }

    private static int spawnRadiusMax() {
        return testSpawnRadius != null ? testSpawnRadius[1] : MatrixTrialConfig.spawnRadiusMax();
    }

    /** 离开半径：配置值，但至少 {@value #MIN_LEAVE_RADIUS} 格（填得比开始距离还小时，一开始就会判走远了） */
    public static int leaveRadius() {
        return Math.max(MIN_LEAVE_RADIUS, testLeaveRadius != null ? testLeaveRadius : MatrixTrialConfig.leaveRadius());
    }

    /** 回响离台子超过这么远就收回另补：离开半径 + {@value #ECHO_LEASH_EXTRA}，但至少比最远生成距离远 {@value #LEASH_PAST_SPAWN} 格 */
    public static double leashRadius() {
        return Math.max(leaveRadius() + ECHO_LEASH_EXTRA, spawnRadiusMax() + LEASH_PAST_SPAWN);
    }

    private static int channelSlackSeconds() {
        return testChannelSlackSeconds != null ? testChannelSlackSeconds : CHANNEL_SLACK_SECONDS;
    }

    // ------------------------------------------------------------------ 开始

    /** 点「开始淬炼」（菜单按钮调；服务端）。结果的提示由调用方发 */
    public static Start tryStart(ServerPlayer player, MatrixCrucibleBlockEntity crucible) {
        MatrixTrialChecks.Report report = MatrixTrialChecks.evaluate(player, crucible);
        if (!report.ready() || report.entry() == null || !(crucible.getLevel() instanceof ServerLevel level)) {
            return new Start(StartResult.BLOCKED, report.first());
        }
        MatrixRankData.Entry entry = report.entry();
        Trial trial = new Trial(player, level, crucible.getBlockPos(), entry, report.channel());
        MatrixEchoEntity first = null;
        if (!trial.channel) {
            Placement placement = findSpawn(trial, level.random);
            if (placement.pos() == null) {
                return new Start(placement.unknown() ? StartResult.ROOM_UNKNOWN : StartResult.NO_ROOM, null);
            }
            first = spawnEcho(trial, placement.pos());
            if (first == null) {
                return new Start(StartResult.SPAWN_DENIED, null);
            }
        }
        if (!pay(trial, player, crucible, report)) {
            if (first != null) {
                first.discard();
            }
            return new Start(StartResult.PAYMENT, null);
        }
        if (first != null) {
            trial.echoes.add(first);
            trial.spawnCooldown = SPAWN_INTERVAL;
        }
        TRIALS.put(player.getUUID(), trial);
        crucible.setLockedBy(player.getUUID());
        updateBar(trial);
        trial.bar.addPlayer(player);
        MutableComponent message = trial.channel
                ? Component.translatable("message.essentialcraft5.matrix_trial.started_channel", MatrixRank.rankName(entry.rank()),
                channelTicks(entry.rank()) / 20)
                : Component.translatable("message.essentialcraft5.matrix_trial.started", MatrixRank.rankName(entry.rank()),
                entry.echoes().count(), entry.echoes().timeSeconds());
        player.displayClientMessage(message.withStyle(ChatFormatting.LIGHT_PURPLE), true);
        level.playSound(null, trial.pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1F, 0.8F);
        refreshStatus(player);
        return new Start(StartResult.STARTED, null);
    }

    /** 扣能量：ESPE（可能失败，先扣）→ MRU → UBMRU（先储备）。任何一步不够就把前面扣的退回、返回 false */
    private static boolean pay(Trial trial, ServerPlayer player, MatrixCrucibleBlockEntity crucible, MatrixTrialChecks.Report report) {
        MatrixRankData.Entry entry = trial.entry;
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return false;
        }
        PlayerPentacleBlockEntity pentacle = report.site().pentacle();
        // 核对用的是五芒星 40 刻才重判一次的档位，结构刚拆掉的那两秒里还算站点；扣之前当场再判一次
        int needTier = MatrixTrialChecks.needTier(entry);
        if (needTier >= 0 && (pentacle == null || pentacle.recheckTier() < needTier)) {
            return false;
        }
        if (entry.espe() > 0) {
            if (pentacle == null || !pentacle.consumeESPE(entry.espe())) {
                return false;
            }
            trial.paidEspe = entry.espe();
            trial.pentaclePos = pentacle.getBlockPos();
            trial.pentacleTier = pentacle.getTier();
        }
        if (entry.mru() > 0) {
            int got = crucible.getMRUStorage().extractMRU(entry.mru(), true);
            trial.paidMru = got;
            if (got < entry.mru()) {
                refund(trial, data, crucible);
                return false;
            }
        }
        int need = entry.ubmru();
        if (need > 0) {
            int reserve = data.getMatrixTrialReserve();
            int fromReserve = Math.min(reserve, need);
            int fromBody = need - fromReserve;
            if (data.getUBMRU() < fromBody) {
                refund(trial, data, crucible);
                return false;
            }
            data.setMatrixTrialReserve(reserve - fromReserve);
            data.setUBMRU(data.getUBMRU() - fromBody);
            trial.paidReserve = fromReserve;
            trial.paidBody = fromBody;
        }
        return true;
    }

    /**
     * 退回这场已经扣的能量：UBMRU 回储备和身上，MRU 回台子，ESPE 放回那座五芒星的柱子。
     * 台子、柱子在这期间又被灌满了的话，放不下的那部分就没了（MRU、ESPE 不许超过容器上限）。
     *
     * @return 全退回去了；有放不下的返回 false（中止的提示照这个换一句）
     */
    private static boolean refund(Trial trial, IPlayerData data, @Nullable MatrixCrucibleBlockEntity crucible) {
        boolean full = true;
        if (trial.paidReserve > 0) {
            data.setMatrixTrialReserve(data.getMatrixTrialReserve() + trial.paidReserve);
        }
        if (trial.paidBody > 0) {
            data.setUBMRU((int) Math.min(Integer.MAX_VALUE, (long) data.getUBMRU() + trial.paidBody));
        }
        if (trial.paidMru > 0) {
            int left = crucible == null ? trial.paidMru : crucible.getMRUStorage().addMRU(trial.paidMru, true);
            full &= left <= 0;
        }
        if (trial.paidEspe > 0 && trial.pentaclePos != null) {
            full &= refundEspe(trial.level, trial.pentaclePos, trial.pentacleTier, trial.paidEspe) <= 1.0E-6D;
        }
        trial.paidReserve = 0;
        trial.paidBody = 0;
        trial.paidMru = 0;
        trial.paidEspe = 0;
        trial.refundedInFull = full;
        return full;
    }

    /**
     * 把 cost 有效 ESPE 折回原始 ESPE（{@link PlayerPentacleBlockEntity#rawCost}，3 档起减半，和扣的时候 {@code consumeESPE} 的算法一样），
     * 按柱子顺序放回去。只动已加载的柱子。
     *
     * @return 放不下、没放回去的原始 ESPE
     */
    private static double refundEspe(ServerLevel level, BlockPos pentacle, int tier, int cost) {
        double raw = PlayerPentacleBlockEntity.rawCost(cost) / (double) PlayerPentacleBlockEntity.multiplier(tier);
        if (tier < 0) {
            return raw;
        }
        for (BlockPos offset : ECPentacle.pillarOffsets(tier)) {
            if (raw <= 1.0E-6D) {
                break;
            }
            BlockPos at = pentacle.offset(offset);
            IESPEHandler handler = ESPETransfer.loadedHandler(level, at, ESPETransfer.sideFacing(at, pentacle));
            if (handler != null) {
                raw = handler.addESPE(raw, true);
            }
        }
        return Math.max(0D, raw);
    }

    // ------------------------------------------------------------------ 回响的生成

    /**
     * 在台子周围找一个能放回响的点：随机方向、{@code spawn_radius_min ~ max} 格远，同一列上下 3 格里找第一个能站的格子
     * （脚下结实、身体不碰方块、脚下不是液体），再以试炼者的身份问那一格能不能动。问不出结论的不算、记一下「还不知道」。
     * <p>
     * 随机找不到时，再试这场放过回响的格子（{@link Trial#usedCells}），顺带判明放不出来的原因（{@link Placement#claimChanged()}、
     * {@link Placement#terrainBlocked()}），中途是中止退还还是照常计时就看这两项（见类注释）。只对照放过的格子、不对照整圈：
     * 不然把台子放在别人领地边上、再把自己这边的格子堵死，整圈就只剩「不能动」，照样能骗到退还。
     */
    public static Placement findSpawn(Trial trial, RandomSource random) {
        ServerLevel level = trial.level;
        int min = spawnRadiusMin();
        int max = Math.max(min, spawnRadiusMax());
        Placement ring = searchRing(trial, trial.center(), trial.pos.getY(), min, max, random);
        if (ring.pos() != null || trial.usedCells.isEmpty()) {
            return ring;
        }
        boolean unknown = ring.unknown();
        boolean usedUnknown = false;
        boolean terrain = false;
        for (BlockPos feet : trial.usedCells) {
            if (!level.isLoaded(feet)) {
                usedUnknown = true;
                continue;
            }
            if (!standable(level, feet)) {
                terrain = true;
                continue;
            }
            ECProtection.Verdict verdict = ECProtection.check(trial.player, level, feet, trial);
            if (verdict == ECProtection.Verdict.ALLOW) {
                return new Placement(Vec3.atBottomCenterOf(feet), false);
            }
            if (verdict == ECProtection.Verdict.UNKNOWN) {
                usedUnknown = true;
            }
        }
        return new Placement(null, unknown || usedUnknown, !terrain && !usedUnknown, terrain);
    }

    /**
     * 在 center 周围 min~max 格（水平）随机找 {@value #SPAWN_TRIES} 列，每列以 baseY 为准上下 3 格找第一个能站的格子，
     * 以试炼者身份问能不能动；问到「能」就返回。问不出结论的记成 unknown。
     */
    private static Placement searchRing(Trial trial, Vec3 center, int baseY, double min, double max, RandomSource random) {
        ServerLevel level = trial.level;
        boolean unknown = false;
        for (int i = 0; i < SPAWN_TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double distance = min + random.nextDouble() * (max - min);
            int x = Mth.floor(center.x + Math.cos(angle) * distance);
            int z = Mth.floor(center.z + Math.sin(angle) * distance);
            for (int dy : DY_ORDER) {
                BlockPos feet = new BlockPos(x, baseY + dy, z);
                if (!level.isLoaded(feet)) {
                    break;
                }
                if (!standable(level, feet)) {
                    continue;
                }
                ECProtection.Verdict verdict = ECProtection.check(trial.player, level, feet, trial);
                if (verdict == ECProtection.Verdict.ALLOW) {
                    return new Placement(Vec3.atBottomCenterOf(feet), false);
                }
                if (verdict == ECProtection.Verdict.UNKNOWN) {
                    unknown = true;
                }
                break;
            }
        }
        return new Placement(null, unknown);
    }

    /** 一个回响站得下：脚下方块顶面结实、脚下不是液体、身体不碰任何方块 */
    public static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        if (!level.getFluidState(feet).isEmpty()) {
            return false;
        }
        return level.noCollision(ECMatrix.MATRIX_ECHO.get().getAABB(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D));
    }

    /** 放一只回响进世界；被别的模组、插件拒了返回 null（并记下 {@code spawnRejected}） */
    @Nullable
    private static MatrixEchoEntity spawnEcho(Trial trial, Vec3 at) {
        IPlayerData data = PlayerDataEvents.get(trial.player);
        MatrixRankData.Echoes stats = trial.entry.echoes();
        MatrixEchoEntity echo = MatrixEchoEntity.create(trial.level, trial.player, data == null ? 0 : data.getMatrixType(),
                stats.health(), stats.damage(), trial.pos, at);
        trial.spawnRejected = true;
        if (echo == null) {
            return null;
        }
        if (testEchoNoAi) {
            echo.setNoAi(true);
        }
        if (!trial.level.addFreshEntity(echo) || echo.isRemoved()) {
            return null;
        }
        trial.spawnRejected = false;
        if (trial.usedCells.size() < MAX_USED_CELLS) {
            trial.usedCells.add(BlockPos.containing(at));
        }
        echo.setTarget(trial.player);
        trial.level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1D, at.z, 30, 0.3D, 0.8D, 0.3D, 0.02D);
        trial.level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.8F, 1.2F);
        return echo;
    }

    // ------------------------------------------------------------------ 每刻

    /** 每个服务端刻末推进所有试炼（事件调；GameTest 也靠它） */
    public static void tickAll(MinecraftServer server) {
        if (TRIALS.isEmpty()) {
            return;
        }
        for (Trial trial : List.copyOf(TRIALS.values())) {
            if (TRIALS.get(trial.player.getUUID()) == trial) {
                tick(trial);
            }
        }
    }

    private static void tick(Trial trial) {
        ServerPlayer player = trial.player;
        if (player.hasDisconnected()) {
            fail(trial, Failure.LOGOUT);
            return;
        }
        if (player.isRemoved() && player.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED || player.isDeadOrDying()) {
            fail(trial, Failure.DEATH);
            return;
        }
        // 换维度：在别的世界了；或者这个玩家对象以别的原因被移除、被换掉了。从末地出口传送门回主世界不发换维度事件，
        // 旧对象带着 CHANGED_DIMENSION 被移除、level 还是末地，看完结尾后服务端另建一个玩家对象（PlayerList.respawn）
        if (player.isRemoved() || player.level() != trial.level || onlineReplacement(trial) != null) {
            fail(trial, Failure.DIMENSION);
            return;
        }
        MatrixCrucibleBlockEntity crucible = trial.crucible();
        if (crucible == null) {
            fail(trial, Failure.CRUCIBLE);
            return;
        }
        if (!MatrixTrialChecks.near(player, trial.pos, leaveRadius())) {
            fail(trial, Failure.LEFT);
            return;
        }
        if (!trial.channel && channelMode(trial.level)) {
            for (MatrixEchoEntity echo : trial.echoes) {
                echo.discard();
            }
            trial.echoes.clear();
            trial.setChannel(true);
            player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_trial.became_channel",
                    channelTicks(trial.rank()) / 20).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        boolean done = trial.channel ? tickChannel(trial) : tickEchoes(trial, crucible);
        if (done || TRIALS.get(player.getUUID()) != trial) {
            return;
        }
        if (!trial.waiting) {
            trial.ticks++;
        }
        if (trial.ticks >= trial.timeLimit) {
            fail(trial, Failure.TIMEOUT);
            return;
        }
        if (trial.ticks % 20 == 0) {
            updateBar(trial);
        }
    }

    /** 在线玩家表里同一 UUID 的另一个玩家对象（试炼记的那个被换掉了时）；没有、或者就是同一个时返回 null */
    @Nullable
    private static ServerPlayer onlineReplacement(Trial trial) {
        MinecraftServer server = trial.level.getServer();
        ServerPlayer current = server.getPlayerList().getPlayer(trial.player.getUUID());
        return current != null && current != trial.player ? current : null;
    }

    /** 试炼者现在在线的那个玩家对象：被换掉了取新的，不然就是试炼记的那个 */
    private static ServerPlayer currentPlayer(Trial trial) {
        ServerPlayer replacement = onlineReplacement(trial);
        return replacement != null ? replacement : trial.player;
    }

    /** @return 这一刻里试炼结束了（成功或中止） */
    private static boolean tickEchoes(Trial trial, MatrixCrucibleBlockEntity crucible) {
        Vec3 center = trial.center();
        double leash = leashRadius();
        trial.echoes.removeIf(echo -> {
            if (echo.isRemoved()) {
                return true;
            }
            if (echo.position().distanceToSqr(center) > leash * leash) {
                echo.discard();
                return true;
            }
            return false;
        });
        for (MatrixEchoEntity echo : trial.echoes) {
            if ((echo.tickCount + echo.getId()) % STUCK_CHECK_INTERVAL == 0) {
                checkStuck(trial, echo);
            }
        }
        MatrixRankData.Echoes stats = trial.entry.echoes();
        if (trial.killed >= stats.count()) {
            succeed(trial);
            return true;
        }
        int remaining = stats.count() - trial.killed;
        int alive = trial.echoes.size();
        trial.waiting = false;
        if (alive < Math.min(stats.alive(), remaining)) {
            if (trial.spawnCooldown > 0) {
                trial.spawnCooldown--;
            } else if (totalEchoes() >= MatrixTrialConfig.maxEchoesTotal()) {
                // 全服名额满了：等着，场上一只都没有时不计时（不让别人的试炼把你的时间耗光）
                trial.waiting = alive == 0;
            } else {
                Placement placement = findSpawn(trial, trial.level.random);
                MatrixEchoEntity echo = placement.pos() == null ? null : spawnEcho(trial, placement.pos());
                if (echo != null) {
                    trial.echoes.add(echo);
                    trial.spawnCooldown = SPAWN_INTERVAL;
                    trial.stall = 0;
                } else if (placement.pos() != null && trial.spawnRejected || placement.claimChanged()) {
                    // 回响被别的模组、插件拒了，或者放过的格子都变成了不能动的领地：不是试炼者造成的，攒够了就中止退还
                    trial.stall++;
                } else if (placement.terrainBlocked() || !placement.unknown()) {
                    // 被方块、液体堵住了：试炼者自己能控制，不退，计时照走（见类注释）
                    trial.stall = 0;
                }
            }
        } else {
            trial.stall = 0;
        }
        if (trial.echoes.isEmpty() && trial.stall >= STALL_TICKS) {
            abort(trial, crucible);
            return true;
        }
        return false;
    }

    /**
     * 卡住检测（每只回响每 {@value #STUCK_CHECK_INTERVAL} 刻一次）：离试炼者超过它的近战距离、寻路又到不了（有路径、但走不到），
     * 记一次；连续 {@value #STUCK_STRIKES} 次就把它挪到试炼者周围 {@value #RELOCATE_MIN}~{@value #RELOCATE_MAX} 格
     * 能站、试炼者能动的空地上（同生成点的规矩）。不算打倒、不另补、不重置生成间隔。生成时不寻路：那样每放一只要试十几个点，
     * 这里每场最多 4 只、每秒各一次，开销有上限。寻路给不出路径（在半空、刚被打飞）的那一次不算，也不清零。
     *
     * @return 这一次挪过去了
     */
    public static boolean checkStuck(Trial trial, MatrixEchoEntity echo) {
        ServerPlayer player = trial.player;
        double reach = echo.getBbWidth() * 2D;
        double reachSqr = reach * reach + player.getBbWidth();
        if (echo.distanceToSqr(player.getX(), player.getY(), player.getZ()) <= reachSqr) {
            echo.setStuckStrikes(0);
            return false;
        }
        net.minecraft.world.level.pathfinder.Path path = echo.getNavigation().createPath(player, 1);
        if (path == null) {
            return false;
        }
        if (path.canReach()) {
            echo.setStuckStrikes(0);
            return false;
        }
        echo.setStuckStrikes(echo.stuckStrikes() + 1);
        if (echo.stuckStrikes() < STUCK_STRIKES) {
            return false;
        }
        Placement near = searchRing(trial, player.position(), player.blockPosition().getY(), RELOCATE_MIN, RELOCATE_MAX, trial.level.random);
        if (near.pos() == null) {
            return false;
        }
        Vec3 at = near.pos();
        echo.getNavigation().stop();
        echo.moveTo(at.x, at.y, at.z, echo.getYRot(), echo.getXRot());
        echo.setDeltaMovement(Vec3.ZERO);
        echo.setStuckStrikes(0);
        trial.level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1D, at.z, 20, 0.3D, 0.8D, 0.3D, 0.02D);
        return true;
    }

    /** @return 这一刻里引导完成了 */
    private static boolean tickChannel(Trial trial) {
        ServerPlayer player = trial.player;
        boolean near = MatrixTrialChecks.near(player, trial.pos, MatrixTrialChecks.START_RADIUS);
        boolean moved = trial.anchor != null && player.position().distanceToSqr(trial.anchor) > CHANNEL_MOVE_TOLERANCE * CHANNEL_MOVE_TOLERANCE;
        if (!near || moved || trial.hurt) {
            if (trial.channelProgress > 0) {
                String key = !near ? "channel_far" : trial.hurt ? "channel_hurt" : "channel_moved";
                player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_trial." + key).withStyle(ChatFormatting.YELLOW), true);
            }
            trial.channelProgress = 0;
            trial.anchor = near ? player.position() : null;
            trial.hurt = false;
            updateBar(trial);
            return false;
        }
        if (trial.anchor == null) {
            trial.anchor = player.position();
        }
        trial.channelProgress++;
        if (trial.channelProgress % 20 == 0) {
            trial.level.sendParticles(ParticleTypes.ENCHANT, trial.pos.getX() + 0.5D, trial.pos.getY() + 1.4D, trial.pos.getZ() + 0.5D,
                    6, 0.3D, 0.3D, 0.3D, 0.5D);
        }
        if (trial.channelProgress >= trial.channelNeeded()) {
            succeed(trial);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 结束

    private static void succeed(Trial trial) {
        ServerPlayer player = trial.player;
        MatrixCrucibleBlockEntity crucible = trial.crucible();
        int[] match = crucible == null ? null : MatrixTrialChecks.matchOfferings(trial.entry.offerings(), crucible.offerings());
        if (crucible == null || match == null) {
            // 锁着的格子被别的途径清掉了（指令、别的模组）：按台子没了算
            fail(trial, Failure.CRUCIBLE);
            return;
        }
        for (int i = 0; i < match.length; i++) {
            crucible.consumeOffering(match[i], trial.entry.offerings().get(i).count());
        }
        IPlayerData data = PlayerDataEvents.get(player);
        int rank = trial.rank();
        if (data != null) {
            if (data.getMatrixRank() < rank) {
                MatrixRank.set(player, rank);
            }
            long damage = (long) MatrixTrialConfig.successMatrixDamagePerRank() * rank;
            if (damage > 0L) {
                CorruptionEffects.addMatrixDamage(data, damage, CorruptionEffects.Source.BATCH13);
            }
        }
        MatrixRank.grantRankSteps(player, rank);
        finish(trial);
        // 上限按新的下一阶算，变小了的话多出来的储备退回身上（一直卡在里面就花不掉了）
        returnExcessReserve(player);
        if (player.connection != null) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.essentialcraft5.matrix_trial.success",
                    MatrixRank.rankName(rank)).withStyle(ChatFormatting.LIGHT_PURPLE)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(MatrixRank.title(rank).withStyle(ChatFormatting.GRAY)));
        }
        trial.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 1F);
        trial.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
        trial.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1D, player.getZ(), 40, 0.5D, 0.8D, 0.5D, 0.3D);
        EssentialCraft5.LOGGER.debug("[矩阵试炼] {} 升到 {} 阶", player.getGameProfile().getName(), rank);
    }

    /**
     * 失败：能量不退、祭品留着、冷却。冷却和提示记在试炼者现在在线的那个玩家对象上（换维度时旧对象的玩家数据已经作废了），
     * 不在线再用试炼记的那个。
     */
    public static void fail(Trial trial, Failure reason) {
        if (TRIALS.get(trial.player.getUUID()) != trial) {
            return;
        }
        ServerPlayer player = currentPlayer(trial);
        IPlayerData data = PlayerDataEvents.get(player);
        int cooldownSeconds = MatrixTrialConfig.failCooldownSeconds();
        if (data != null) {
            data.setMatrixTrialCooldown(cooldownSeconds * 20);
        }
        finish(trial);
        if (reason != Failure.LOGOUT) {
            player.sendSystemMessage(Component.translatable(failMessageKey(reason, cooldownSeconds),
                    Component.translatable("message.essentialcraft5.matrix_trial.failed." + reason.key()), cooldownSeconds)
                    .withStyle(ChatFormatting.RED));
            trial.level.playSound(null, trial.pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1F, 0.8F);
        }
    }

    /**
     * 失败提示用哪条：台子没了时不说「祭品还在淬炼台里」（台子被挖掉时祭品已经掉在原地），冷却为 0 时不说「几秒后才能再试」。
     * 四条的参数都是（原因, 冷却秒数），用不到的不写进文字。
     */
    public static String failMessageKey(Failure reason, int cooldownSeconds) {
        String key = reason == Failure.CRUCIBLE ? "message.essentialcraft5.matrix_trial.failed_crucible" : "message.essentialcraft5.matrix_trial.failed";
        return cooldownSeconds > 0 ? key : key + "_no_cooldown";
    }

    /** 中止：退能量、不冷却（中途领地变了、回响被拒了，见类注释）；台子、柱子满了放不下的部分提示里说一声 */
    private static void abort(Trial trial, @Nullable MatrixCrucibleBlockEntity crucible) {
        ServerPlayer player = currentPlayer(trial);
        IPlayerData data = PlayerDataEvents.get(player);
        boolean full = data == null || refund(trial, data, crucible);
        finish(trial);
        player.sendSystemMessage(Component.translatable(full ? "message.essentialcraft5.matrix_trial.aborted"
                : "message.essentialcraft5.matrix_trial.aborted_partial").withStyle(ChatFormatting.YELLOW));
    }

    private static void finish(Trial trial) {
        TRIALS.remove(trial.player.getUUID(), trial);
        for (MatrixEchoEntity echo : trial.echoes) {
            echo.discard();
        }
        trial.echoes.clear();
        trial.bar.removeAllPlayers();
        MatrixCrucibleBlockEntity crucible = trial.crucible();
        if (crucible != null && trial.player.getUUID().equals(crucible.lockedBy())) {
            crucible.setLockedBy(null);
        }
        refreshStatus(trial.player);
    }

    private static void updateBar(Trial trial) {
        Component rank = MatrixRank.rankName(trial.rank());
        if (trial.channel) {
            int needed = trial.channelNeeded();
            int left = Math.max(0, (needed - trial.channelProgress + 19) / 20);
            int limit = Math.max(0, (trial.timeLimit - trial.ticks + 19) / 20);
            trial.bar.setName(Component.translatable("bossbar.essentialcraft5.matrix_trial.channel", rank, left, limit));
            trial.bar.setProgress(needed <= 0 ? 1F : Mth.clamp(trial.channelProgress / (float) needed, 0F, 1F));
        } else {
            int count = trial.entry.echoes().count();
            int left = Math.max(0, (trial.timeLimit - trial.ticks + 19) / 20);
            trial.bar.setName(Component.translatable("bossbar.essentialcraft5.matrix_trial", rank, trial.killed, count, left));
            trial.bar.setProgress(count <= 0 ? 1F : Mth.clamp(trial.killed / (float) count, 0F, 1F));
        }
    }

    /** 试炼者开着这台的界面时，让界面下一刻重发一次状态 */
    private static void refreshStatus(ServerPlayer player) {
        if (player.containerMenu instanceof MatrixCrucibleMenu menu) {
            menu.requestStatus();
        }
    }

    // ------------------------------------------------------------------ 外部通知

    /**
     * 回响死了（{@link MatrixEchoEntity#die} 调）：只有试炼者亲手打倒的才算。来源实体是试炼者，或者来源没有实体
     * （别的模组 setHealth 致死、走 {@code tickDeath} 补的那一次）而击杀归属是试炼者都算；别人的伤害进不了 hurt，
     * 回响的击杀归属只可能是试炼者，所以这条不会让别人顶替。虚空、/kill 这类 bypasses_invulnerability 不认击杀归属：
     * 先打一下再推下虚空不算打倒。
     */
    public static void onEchoDeath(MatrixEchoEntity echo, DamageSource source) {
        UUID id = echo.trialist();
        Trial trial = id == null ? null : TRIALS.get(id);
        if (trial == null || !trial.echoes.remove(echo)) {
            return;
        }
        boolean byTrialist = echo.isTrialist(source.getEntity())
                || source.getEntity() == null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && echo.isTrialist(echo.getKillCredit());
        if (byTrialist) {
            trial.killed++;
            updateBar(trial);
        }
    }

    /** 试炼者死了 */
    public static void onTrialistDeath(ServerPlayer player) {
        Trial trial = TRIALS.get(player.getUUID());
        if (trial != null) {
            fail(trial, Failure.DEATH);
        }
    }

    /** 试炼者下线 */
    public static void onLogout(ServerPlayer player) {
        Trial trial = TRIALS.get(player.getUUID());
        if (trial != null) {
            fail(trial, Failure.LOGOUT);
        }
    }

    /** 试炼者换了维度 */
    public static void onDimensionChange(ServerPlayer player) {
        Trial trial = TRIALS.get(player.getUUID());
        if (trial != null) {
            fail(trial, Failure.DIMENSION);
        }
    }

    /**
     * 试炼者重生成了一个新的玩家对象（死亡重生、从末地出口回来）：试炼还挂在旧对象上的话按换维度失败。
     * 死亡那条路在死亡事件里已经失败过，这里是末地出口那条路的兜底（tick 开头也会发现）。
     */
    public static void onRespawn(ServerPlayer player) {
        Trial trial = TRIALS.get(player.getUUID());
        if (trial != null && trial.player != player) {
            fail(trial, Failure.DIMENSION);
        }
    }

    /** 试炼者受了伤（引导从头算） */
    public static void onTrialistHurt(ServerPlayer player) {
        Trial trial = TRIALS.get(player.getUUID());
        if (trial != null && trial.channel) {
            trial.hurt = true;
        }
    }

    // ------------------------------------------------------------------ 试炼储备

    /**
     * 存试炼储备：身上的 UBMRU 存进去，直到下一阶要的数（{@link #reserveCap}）。动作栏说存了多少、为什么存不进。
     * 先把超过上限的旧储备退回身上（{@link #returnExcessReserve}）；试炼进行中不收（开始时储备已经扣过，这时按旧阶的上限存进去，
     * 升阶以后就卡在里面了）。
     */
    public static Deposit deposit(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return new Deposit(0, "no_data");
        }
        returnExcessReserve(player);
        int rank = data.getMatrixRank();
        MatrixRankData.Entry next = rank < MatrixRank.MAX ? MatrixRankData.get(rank + 1) : null;
        int cap = next == null ? 0 : next.ubmru();
        int reserve = data.getMatrixTrialReserve();
        int body = Math.max(0, data.getUBMRU());
        int amount = Math.max(0, Math.min(cap - reserve, body));
        String reason = null;
        if (!MatrixRank.enabled()) {
            reason = "disabled";
        } else if (trialOf(player.getUUID()) != null) {
            reason = "running";
        } else if (rank >= MatrixRank.MAX) {
            reason = "not_needed";
        } else if (next == null) {
            reason = "no_data";
        } else if (cap <= 0) {
            reason = "no_ubmru";
        } else if (reserve >= cap) {
            reason = "full";
        } else if (body <= 0) {
            reason = "empty";
        }
        if (reason != null) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_trial.reserve." + reason, reserve, cap)
                    .withStyle(ChatFormatting.YELLOW), true);
            return new Deposit(0, reason);
        }
        data.setUBMRU(body - amount);
        data.setMatrixTrialReserve(reserve + amount);
        player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_trial.reserve.stored", amount, reserve + amount, cap)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1F, 1F);
        refreshStatus(player);
        return new Deposit(amount, null);
    }

    /**
     * 试炼储备超过当前上限（下一阶要的 UBMRU）的部分退回身上，动作栏说一声。上限会变小：升了阶（三阶起的阶不要 UBMRU）、
     * 指令改了阶、迁移补了阶下限；储备只能花在试炼上，不退的话就永远卡在里面。试炼进行中不动（那时储备是开始前的样子，结束再算）。
     *
     * @return 退回了多少
     */
    public static int returnExcessReserve(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null || trialOf(player.getUUID()) != null) {
            return 0;
        }
        int reserve = data.getMatrixTrialReserve();
        int excess = reserve - reserveCap(player);
        if (excess <= 0) {
            return 0;
        }
        data.setMatrixTrialReserve(reserve - excess);
        data.setUBMRU((int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, data.getUBMRU()) + excess));
        player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_trial.reserve.returned", excess)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return excess;
    }

    // ------------------------------------------------------------------ 测试

    /**
     * GameTest 用：生成半径、离开半径、引导刻数、是否走引导的覆盖值（null 恢复按配置）；回响不带 AI（测试玩家不在世界实体表里，
     * 回响追不到也打不到，省得乱走）。只许在同一个测试里设，测完调 {@link #clearTestOverrides}。
     */
    public static void setTestOverrides(@Nullable int[] spawnRadius, @Nullable Integer leaveRadius, @Nullable Integer channelTicks,
                                        @Nullable Boolean channelMode, boolean echoNoAi) {
        testSpawnRadius = spawnRadius == null ? null : new int[]{spawnRadius[0], spawnRadius[1]};
        testLeaveRadius = leaveRadius;
        testChannelTicks = channelTicks;
        testChannelMode = channelMode;
        testEchoNoAi = echoNoAi;
    }

    public static void clearTestOverrides() {
        setTestOverrides(null, null, null, null, false);
        testChannelSlackSeconds = null;
    }

    /** GameTest 用：引导时限里「再加多少秒」的覆盖值（null = {@value #CHANNEL_SLACK_SECONDS}），测引导超时时缩短用例；{@link #clearTestOverrides} 一起清 */
    public static void setTestChannelSlack(@Nullable Integer seconds) {
        testChannelSlackSeconds = seconds;
    }

    /** GameTest 用：结束全部试炼、清现场（批次收尾时调） */
    public static void clearAllForTest() {
        for (Trial trial : List.copyOf(TRIALS.values())) {
            finish(trial);
        }
        TRIALS.clear();
    }

    /** GameTest 用：结束某名玩家的试炼（不冷却、不发提示），清现场 */
    public static void clearForTest(UUID player) {
        Trial trial = TRIALS.get(player);
        if (trial != null) {
            finish(trial);
        }
    }

    // ------------------------------------------------------------------ 事件

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class MatrixTrialEvents {

        private MatrixTrialEvents() {
        }

        @SubscribeEvent
        public static void matrixTrialServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                tickAll(event.getServer());
            }
        }

        /** 试炼者死亡 = 失败（被复活类效果救下、事件被取消的不算） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void matrixTrialPlayerDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                onTrialistDeath(player);
            }
        }

        @SubscribeEvent
        public static void matrixTrialLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                onLogout(player);
            }
        }

        @SubscribeEvent
        public static void matrixTrialChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                onDimensionChange(player);
            }
        }

        /** 重生成新的玩家对象（末地出口回来不发换维度事件，这里兜底） */
        @SubscribeEvent
        public static void matrixTrialRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                onRespawn(player);
            }
        }

        /**
         * 别人的钓竿钩不住回响（钩子当它不在、穿过去）：钩子挂上以后收竿会把它直接拽过去（{@code FishingHook.pullEntity} 不经过伤害），
         * 旁人在远处反复钓，能把回响拽出回收距离、白耗试炼者的时间。试炼者自己的钩子照常。
         */
        @SubscribeEvent
        public static void matrixTrialEchoHooked(ProjectileImpactEvent event) {
            if (event.getProjectile() instanceof FishingHook hook && event.getRayTraceResult() instanceof EntityHitResult hit
                    && hit.getEntity() instanceof MatrixEchoEntity echo && !echo.isTrialist(hook.getOwner())) {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            }
        }

        /** 引导中受伤从头算：只看真的扣了血的那一下（LOWEST、没被取消、量 &gt; 0） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void matrixTrialPlayerHurt(LivingDamageEvent event) {
            if (event.getAmount() > 0F && event.getEntity() instanceof ServerPlayer player) {
                onTrialistHurt(player);
            }
        }

        /**
         * 拿着绑定自己的灵魂石潜行右键淬炼台 = 存试炼储备。潜行拿着东西时原版不调方块的 use，这里在交互事件里接住并取消，
         * 免得灵魂石自己的潜行右键（解绑）跟着生效。两端都取消（客户端取消了才不会再发「使用物品」）；存的动作只在服务端做。
         * 领地插件取消了交互的，这里收不到（不收已取消的事件）。
         */
        @SubscribeEvent
        public static void matrixTrialReserveUse(PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
                return;
            }
            ItemStack stack = event.getItemStack();
            if (!(stack.getItem() instanceof SoulStoneItem) || !player.getUUID().equals(SoulStoneItem.getOwner(stack))) {
                return;
            }
            Level level = event.getLevel();
            if (!(level.getBlockState(event.getPos()).getBlock() instanceof MatrixCrucibleBlock)) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                deposit(serverPlayer);
            }
        }

        @SubscribeEvent
        public static void matrixTrialServerStopped(ServerStoppedEvent event) {
            TRIALS.clear();
        }
    }
}
