package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 矩阵之影的召唤契约与退还（batch13.md §5.3.2、D21 A）：挂在主世界的 {@code data/essentialcraft5_matrix_shade.dat}。
 * <p>
 * 每只导流符召出来的矩阵之影一份契约：召唤者、转进去的矩阵损伤 a、召唤用的五芒星（维度 + 坐标）、从柱子里预扣的原始 ESPE、
 * 召唤者在场地外连续待了多少刻（回来就清零）。契约在打倒时结清（{@link #defeated}），失败时退还并删掉（{@link #fail}）——两条路都先删契约再做事，所以同一只不会退两次。
 * <p>
 * 失败的退还：
 * <ul>
 *   <li>矩阵损伤 a 原数还给召唤者，走有上限的 {@link CorruptionEffects#addMatrixDamage(IPlayerData, long, CorruptionEffects.Source)}
 *       （{@code LEGACY}：转出去时没打折，还回来也不打折；满了不超上限，不比召唤前更糟）。</li>
 *   <li>导流符：活着、没在下线就直接给（背包满了掉在脚下）；刚死的等重生、正在下线的等下次上线（先记在他自己的存档里，{@link #PLAYER_KEY}）。</li>
 *   <li>召唤者的玩家对象还在（在线，含刚死、正在下线：{@code PlayerLoggedOutEvent} 发在玩家存盘之前）就直接还到他身上，跟着玩家文件一起落盘；
 *       只有玩家对象不在（重启后读回来的契约，召唤者还没上线）才记进 {@link #pendingRefunds}，登录时补。</li>
 *   <li>结算过的契约（打倒、退还）记进召唤者自己存档里的一张小表（{@link #MAX_SETTLED} 条），登录补退时先查它：
 *       契约表和玩家文件不是同一时刻存盘的，崩服回档时契约表可能回到「还开着」，靠这张表不会再退第二次；反过来最多是玩家吃亏，不会多拿。</li>
 *   <li>预扣的 ESPE 退回那座五芒星的柱子（四档全部柱位，按顺序往里加；只碰已加载的）；退不完（柱子满了、拆了、区块没加载）的部分记进
 *       {@link #pendingEspe}，每 10 秒再试一次，直到退完（五芒星拆了、柱位上一根柱子都没有时保留，等装回去）。</li>
 * </ul>
 * 失败的情况：召唤者死亡 / 下线 / 换维度 / 在拉回距离 {@code pull_range}（24 格；填 0 时按 {@code escape_range}）以外连续待满 {@code summoner_away_ticks}（600 刻）
 * （这四种由这里的事件和每秒检查判；按距离算、不靠首领 tick，首领所在区块不 tick、拉回点被堵时照样会判），
 * 名单空了到点、和平难度（首领的 {@link ECBoss#endBoss}）、没有玩家参与的死亡（{@code /kill}、掉出世界）、被别的东西直接删掉（{@link MatrixShadeEntity#onRemovedFromWorld}）。
 * 失败后它在 {@code dissipate_ticks}（200 刻）后散去；那时它在没加载的区块里的话，下次加载时发现契约没了，直接散去。
 */
public final class MatrixShadeContracts extends SavedData {

    public static final String NAME = "essentialcraft5_matrix_shade";
    public static final int VERSION = 1;
    /** 每多少刻查一次召唤者（离场、换维度、下线兜底） */
    public static final int CHECK_INTERVAL = 20;
    /** 每多少刻重试一次没退完的 ESPE */
    public static final int ESPE_RETRY_INTERVAL = 200;
    /** 挂起的 ESPE 退还最多记多少处（超出的最早那条丢掉并写一行警告，防止拆了又召刷满存档） */
    public static final int MAX_PENDING_ESPE = 256;
    /** 召唤者自己存档里的记录（{@link Player#PERSISTED_NBT_TAG} 下：随玩家文件落盘，死亡重生照留） */
    public static final String PLAYER_KEY = EssentialCraft5.MODID + ":matrix_shade";
    /** 召唤者存档里最多记几只「结算过了」（最早的先丢；一个人同一时间最多一两份契约） */
    public static final int MAX_SETTLED = 16;
    private static final String TAG_SETTLED = "settled";
    private static final String TAG_HELD = "held_conduits";
    private static final double EPS = 1.0E-6D;

    /** 失败的原因（提示文字 message.essentialcraft5.matrix_shade.failed.&lt;小写名&gt;） */
    public enum Failure {
        SUMMONER_DIED,
        SUMMONER_OFFLINE,
        SUMMONER_DIMENSION,
        SUMMONER_AWAY,
        ABANDONED,
        PEACEFUL,
        NO_PLAYER_KILL,
        VANISHED;

        public String messageKey() {
            return "message.essentialcraft5.matrix_shade.failed." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** 一份契约（awayTicks 会变，其余不变） */
    public static final class Contract {
        private final UUID shade;
        private final UUID summoner;
        private final int absorbed;
        private final ResourceKey<Level> dimension;
        private final BlockPos pentacle;
        private final double espe;
        private int awayTicks;

        public Contract(UUID shade, UUID summoner, int absorbed, ResourceKey<Level> dimension, BlockPos pentacle, double espe) {
            this.shade = shade;
            this.summoner = summoner;
            this.absorbed = Math.max(0, absorbed);
            this.dimension = dimension;
            this.pentacle = pentacle.immutable();
            this.espe = Double.isFinite(espe) ? Math.max(0D, espe) : 0D;
        }

        public UUID shade() {
            return shade;
        }

        public UUID summoner() {
            return summoner;
        }

        public int absorbed() {
            return absorbed;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        public BlockPos pentacle() {
            return pentacle;
        }

        /** 从柱子里抽走的原始 ESPE（柱子里的单位，三档按两倍算之前） */
        public double espe() {
            return espe;
        }

        public int awayTicks() {
            return awayTicks;
        }
    }

    /** 一处没退完的 ESPE */
    public record PendingEspe(ResourceKey<Level> dimension, BlockPos pentacle, double amount) {
    }

    /** 一笔等召唤者上线才能还的退还（只在他的玩家对象不在时记：重启后读回来的契约） */
    public record PendingRefund(UUID shade, UUID summoner, int absorbed) {
    }

    /**
     * 一次还到人身上的结算。
     *
     * @param refunded   实际还上的矩阵损伤（满了只还到上限）
     * @param conduitNow 导流符当场给到了（刚死的等重生、正在下线的等下次上线）
     */
    public record Settlement(int refunded, boolean conduitNow) {
    }

    private final Map<UUID, Contract> contracts = new LinkedHashMap<>();
    private final List<PendingRefund> pendingRefunds = new ArrayList<>();
    private final List<PendingEspe> pendingEspe = new ArrayList<>();

    public MatrixShadeContracts() {
    }

    /** 这个服务器的契约表（挂在主世界） */
    public static MatrixShadeContracts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MatrixShadeContracts::load, MatrixShadeContracts::new, NAME);
    }

    public static MatrixShadeContracts get(ServerLevel level) {
        return get(level.getServer());
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    @Nullable
    public Contract contract(UUID shade) {
        return contracts.get(shade);
    }

    public static boolean isOpen(ServerLevel level, UUID shade) {
        return get(level).contracts.containsKey(shade);
    }

    /** 这名玩家名下还开着的契约（一般最多一份） */
    public List<Contract> contractsOf(UUID summoner) {
        List<Contract> out = new ArrayList<>();
        for (Contract contract : contracts.values()) {
            if (contract.summoner.equals(summoner)) {
                out.add(contract);
            }
        }
        return out;
    }

    /** 这名玩家名下等上线补的矩阵损伤合计 */
    public long pendingDamage(UUID player) {
        long sum = 0L;
        for (PendingRefund entry : pendingRefunds) {
            if (entry.summoner.equals(player)) {
                sum += entry.absorbed;
            }
        }
        return sum;
    }

    /** 这名玩家名下等上线补的导流符张数（一份契约一张） */
    public int pendingConduits(UUID player) {
        int count = 0;
        for (PendingRefund entry : pendingRefunds) {
            if (entry.summoner.equals(player)) {
                count++;
            }
        }
        return count;
    }

    public List<PendingEspe> pendingEspe() {
        return List.copyOf(pendingEspe);
    }

    public int openCount() {
        return contracts.size();
    }

    // ------------------------------------------------------------------
    // 开、结、退
    // ------------------------------------------------------------------

    /** 召唤成功后记一份契约 */
    public static void open(ServerLevel level, MatrixShadeEntity shade, ServerPlayer summoner, int absorbed, BlockPos pentacle, double espe) {
        MatrixShadeContracts data = get(level);
        data.contracts.put(shade.getUUID(), new Contract(shade.getUUID(), summoner.getUUID(), absorbed, level.dimension(), pentacle, espe));
        data.setDirty();
    }

    /**
     * 打倒：结清（转进去的损伤不再退），在召唤者自己的存档里记下「这只结过了」；没有契约时什么都不做，返回 false。
     */
    public static boolean defeated(ServerLevel level, UUID shade) {
        MatrixShadeContracts data = get(level);
        Contract contract = data.contracts.remove(shade);
        if (contract == null) {
            return false;
        }
        data.setDirty();
        ServerPlayer summoner = ECProtection.onlinePlayer(level.getServer(), contract.summoner);
        if (summoner != null) {
            markSettled(summoner, shade);
        }
        return true;
    }

    /**
     * 失败：删掉契约，退还矩阵损伤、导流符、预扣的 ESPE，告诉召唤者，让还加载着的那只开始散去。没有契约（已经结清、退过、刷怪蛋放出来的）时什么都不做，返回 false。
     * 召唤者的玩家对象在（在线，含刚死、正在下线）就直接还到他身上（见类注释），不在才记账等他上线。
     */
    public static boolean fail(ServerLevel anyLevel, UUID shade, Failure reason) {
        return get(anyLevel.getServer()).failContract(anyLevel.getServer(), shade, reason);
    }

    /** {@link #fail} 的实体：在这份契约表上判（GameTest 拿读回来的副本模拟回档时直接调） */
    public boolean failContract(MinecraftServer server, UUID shade, Failure reason) {
        Contract contract = contracts.remove(shade);
        if (contract == null) {
            return false;
        }
        setDirty();
        ServerPlayer summoner = ECProtection.onlinePlayer(server, contract.summoner);
        boolean leaving = reason == Failure.SUMMONER_OFFLINE;
        Settlement settled = null;
        if (summoner != null) {
            settled = settle(summoner, contract.shade, contract.absorbed, leaving);
        } else {
            pendingRefunds.add(new PendingRefund(contract.shade, contract.summoner, contract.absorbed));
        }
        ServerLevel pentacleLevel = server.getLevel(contract.dimension);
        double left = contract.espe;
        if (pentacleLevel != null && left > EPS) {
            left = refundEspe(pentacleLevel, contract.pentacle, left);
        }
        if (left > EPS) {
            addPendingEspe(new PendingEspe(contract.dimension, contract.pentacle, left));
        }
        if (summoner != null && settled != null && !leaving) {
            summoner.displayClientMessage(Component.translatable(reason.messageKey()), false);
            for (Component line : refundMessages(settled, contract.absorbed, left > EPS)) {
                summoner.displayClientMessage(line, false);
            }
        }
        if (pentacleLevel != null) {
            Entity entity = pentacleLevel.getEntity(shade);
            if (entity instanceof MatrixShadeEntity matrixShade && !matrixShade.isRemoved() && !matrixShade.isDeadOrDying()) {
                matrixShade.beginDissipate();
            }
        }
        return true;
    }

    /**
     * 退还时告诉召唤者的话：还了多少损伤（满了只还到上限的另说一句）、导流符是当场给还是等重生、ESPE 是退完了还是柱子装不下要过一会儿再退。
     */
    public static List<Component> refundMessages(Settlement settled, int absorbed, boolean espePending) {
        String key;
        if (settled.conduitNow()) {
            key = espePending ? "refunded_espe_pending" : "refunded";
        } else {
            key = espePending ? "refunded_respawn_espe_pending" : "refunded_respawn";
        }
        List<Component> lines = new ArrayList<>(2);
        lines.add(Component.translatable("message.essentialcraft5.matrix_shade." + key, settled.refunded()));
        if (settled.refunded() < absorbed) {
            lines.add(Component.translatable("message.essentialcraft5.matrix_shade.refund_capped", absorbed - settled.refunded()));
        }
        return lines;
    }

    private void addPendingEspe(PendingEspe entry) {
        for (int i = 0; i < pendingEspe.size(); i++) {
            PendingEspe old = pendingEspe.get(i);
            if (old.dimension.equals(entry.dimension) && old.pentacle.equals(entry.pentacle)) {
                pendingEspe.set(i, new PendingEspe(old.dimension, old.pentacle, old.amount + entry.amount));
                setDirty();
                return;
            }
        }
        if (pendingEspe.size() >= MAX_PENDING_ESPE) {
            PendingEspe dropped = pendingEspe.remove(0);
            EssentialCraft5.LOGGER.warn("Matrix Specter ESPE refund list is full ({} entries); dropped {} ESPE owed to the pentacle at {} in {}",
                    MAX_PENDING_ESPE, dropped.amount, dropped.pentacle.toShortString(), dropped.dimension.location());
        }
        pendingEspe.add(entry);
        setDirty();
    }

    /** 矩阵损伤还给玩家（有上限，不打折）；返回实际还上的量 */
    public static int refundDamage(Player player, long amount) {
        IPlayerData data = player.getCapability(ECCapabilities.PLAYER_DATA).orElse(null);
        if (data == null || amount <= 0L) {
            return 0;
        }
        return CorruptionEffects.addMatrixDamage(data, amount, CorruptionEffects.Source.LEGACY);
    }

    /**
     * 把 amount 原始 ESPE 加回 pentacle 周围的柱位（0 ~ {@link ECPentacle#MAX_TIER} 档全部柱位，按顺序；只碰已加载的）；返回没加进去的量。
     * 三、四档五芒星抽的时候按两倍算、抽走的原始量是一半，这里退的就是当时抽走的原始量，所以和档位无关。
     * 13e 加了 4 档以后这张表多了 4 根暗渎柱的位置：4 档预扣时可能抽的是它们，退的时候也要能退进去，不然柱子满着时会退不完、挂成待退。
     * 那一格已经不是灵魂五芒星（拆了、没加载）时一点都不退，整笔留着等它回来：柱位上别人后来摆的柱子不该白拿这笔。
     */
    public static double refundEspe(ServerLevel level, BlockPos pentacle, double amount) {
        if (!(ECLevels.loadedBlockEntity(level, pentacle) instanceof PlayerPentacleBlockEntity)) {
            return amount;
        }
        double left = amount;
        for (BlockPos offset : ECPentacle.pillarOffsets(ECPentacle.MAX_TIER)) {
            if (left <= EPS) {
                break;
            }
            BlockPos at = pentacle.offset(offset);
            IESPEHandler handler = ESPETransfer.loadedHandler(level, at, ESPETransfer.sideFacing(at, pentacle));
            if (handler != null) {
                left = handler.addESPE(left, true);
            }
        }
        return left <= EPS ? 0D : left;
    }

    /** 登录、重生时：把等着他的退还交给这名玩家（见 {@link #deliverTo}） */
    public static void deliver(ServerPlayer player) {
        if (player instanceof FakePlayer) {
            return;
        }
        get(player.serverLevel()).deliverTo(player);
    }

    /**
     * 先补这份契约表里记在他名下的账（他自己存档里记着「结算过了」的那几只只删不退），再把刚死 / 下线时留着的导流符交给他（活着才给）。
     */
    public void deliverTo(ServerPlayer player) {
        long damage = 0L;
        int refunded = 0;
        int count = 0;
        boolean changed = false;
        for (Iterator<PendingRefund> it = pendingRefunds.iterator(); it.hasNext(); ) {
            PendingRefund entry = it.next();
            if (!entry.summoner.equals(player.getUUID())) {
                continue;
            }
            it.remove();
            changed = true;
            Settlement settled = settle(player, entry.shade, entry.absorbed, false);
            if (settled != null) {
                damage += entry.absorbed;
                refunded += settled.refunded();
                count++;
            }
        }
        if (changed) {
            setDirty();
        }
        if (count > 0) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_shade.refunded_later", refunded, count), false);
            if (refunded < damage) {
                EssentialCraft5.LOGGER.debug("Matrix specter refund for {} capped: {} of {}", player.getGameProfile().getName(), refunded, damage);
            }
        }
        if (player.isAlive() && !player.isRemoved()) {
            int held = takeHeldConduits(player);
            if (held > 0) {
                ECItemEntities.giveOrDrop(player, new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get(), held));
                player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_shade.conduit_returned", held), false);
            }
        }
    }

    // ------------------------------------------------------------------
    // 召唤者自己存档里的记录
    // ------------------------------------------------------------------

    /**
     * 把一份契约的损伤和导流符还到这名玩家身上，并在他自己的存档里记下「这只结算过了」；已经记过的什么都不做，返回 null。
     * 损伤直接写进他的玩家数据（刚死的也写：重生时整份复制过去）；导流符活着、没在下线就给，否则留在他存档里等重生 / 上线（{@link #deliverTo}）。
     */
    @Nullable
    public static Settlement settle(ServerPlayer player, UUID shade, int absorbed, boolean leaving) {
        if (isSettled(player, shade)) {
            return null;
        }
        markSettled(player, shade);
        int refunded = refundDamage(player, absorbed);
        boolean now = !leaving && player.isAlive() && !player.isRemoved();
        if (now) {
            ECItemEntities.giveOrDrop(player, new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get()));
        } else {
            CompoundTag record = playerRecord(player);
            record.putInt(TAG_HELD, Math.max(0, record.getInt(TAG_HELD)) + 1);
        }
        return new Settlement(refunded, now);
    }

    /** 这只矩阵之影的契约在这名玩家这里结算过了（打倒或退还） */
    public static boolean isSettled(Player player, UUID shade) {
        for (Tag entry : playerRecord(player).getList(TAG_SETTLED, Tag.TAG_INT_ARRAY)) {
            try {
                if (shade.equals(NbtUtils.loadUUID(entry))) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // 坏掉的 UUID 跳过
            }
        }
        return false;
    }

    private static void markSettled(Player player, UUID shade) {
        if (isSettled(player, shade)) {
            return;
        }
        CompoundTag record = playerRecord(player);
        ListTag list = record.getList(TAG_SETTLED, Tag.TAG_INT_ARRAY);
        list.add(NbtUtils.createUUID(shade));
        while (list.size() > MAX_SETTLED) {
            list.remove(0);
        }
        record.put(TAG_SETTLED, list);
    }

    /** 留在他存档里、等重生 / 上线才给的导流符张数 */
    public static int heldConduits(Player player) {
        return Math.max(0, playerRecord(player).getInt(TAG_HELD));
    }

    private static int takeHeldConduits(Player player) {
        CompoundTag record = playerRecord(player);
        int held = Math.max(0, record.getInt(TAG_HELD));
        record.remove(TAG_HELD);
        return held;
    }

    /** 他存档里的那份记录（没有就建一份挂上去；getCompound 在没有时返回的新标签没挂在上面，要 put 回去） */
    private static CompoundTag playerRecord(Player player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag record = persisted.getCompound(PLAYER_KEY);
        persisted.put(PLAYER_KEY, record);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        return record;
    }

    /**
     * 只给 GameTest：删掉这只的契约、这名玩家名下挂着的损伤和导流符、这座五芒星（绝对坐标）名下挂着的 ESPE，什么都不退。
     * 测试结构清掉以后，这些不能留到后面的测试里（同一处坐标会被别的测试重新摆上柱子）。参数为 null 的那项不碰。
     */
    public static void forgetForTest(ServerLevel level, @Nullable UUID shade, @Nullable UUID summoner, @Nullable BlockPos pentacle) {
        MatrixShadeContracts data = get(level);
        if (shade != null) {
            data.contracts.remove(shade);
        }
        if (summoner != null) {
            data.pendingRefunds.removeIf(entry -> entry.summoner.equals(summoner));
            data.contracts.values().removeIf(contract -> contract.summoner.equals(summoner));
        }
        if (pentacle != null) {
            data.pendingEspe.removeIf(entry -> entry.pentacle.equals(pentacle));
        }
        data.setDirty();
    }

    // ------------------------------------------------------------------
    // 每秒检查
    // ------------------------------------------------------------------

    /**
     * 每 {@value #CHECK_INTERVAL} 刻：召唤者下线（兜底）、换维度、死了、在拉回距离以外太久就判失败（GameTest 直接调）。
     * 「离开」按召唤者离五芒星中心的距离算（拉回距离，填 0 时按脱战距离；和首领名单用同一套规范化），不看首领的名单：
     * 首领所在的区块不 tick（混合端的实体激活距离之类）、拉回点被堵住拉不回来时，名单不动，这里照样计时。
     * 正常打的时候召唤者一出拉回距离就被拽回来，计时清零，不受影响。
     */
    public void checkSummoners(MinecraftServer server, int elapsed) {
        if (contracts.isEmpty()) {
            return;
        }
        ECMatrixShade.Config.Values values = ECMatrixShade.Config.values();
        double away = BossRoster.Settings.arena(values.fight().pullRange(), values.fight().escapeRange()).awayRange();
        int limit = values.summon().summonerAwayTicks();
        for (Contract contract : List.copyOf(contracts.values())) {
            ServerPlayer player = ECProtection.onlinePlayer(server, contract.summoner);
            if (player == null) {
                failContract(server, contract.shade, Failure.SUMMONER_OFFLINE);
                continue;
            }
            if (!player.isAlive()) {
                failContract(server, contract.shade, Failure.SUMMONER_DIED);
                continue;
            }
            if (!player.level().dimension().equals(contract.dimension)) {
                failContract(server, contract.shade, Failure.SUMMONER_DIMENSION);
                continue;
            }
            Vec3 center = Vec3.atBottomCenterOf(contract.pentacle.above());
            if (player.position().distanceTo(center) > away) {
                contract.awayTicks += elapsed;
                setDirty();
                if (limit >= 0 && contract.awayTicks >= limit) {
                    failContract(server, contract.shade, Failure.SUMMONER_AWAY);
                } else if (contract.awayTicks == elapsed) {
                    player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_shade.summoner_away",
                            Math.max(1, (limit - contract.awayTicks) / 20)), true);
                }
            } else if (contract.awayTicks != 0) {
                contract.awayTicks = 0;
                setDirty();
            }
        }
    }

    /** 再试一次没退完的 ESPE（GameTest 直接调） */
    public void retryEspe(MinecraftServer server) {
        if (pendingEspe.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (int i = pendingEspe.size() - 1; i >= 0; i--) {
            PendingEspe entry = pendingEspe.get(i);
            ServerLevel level = server.getLevel(entry.dimension);
            if (level == null || !ECLevels.isFullyLoaded(level, entry.pentacle)) {
                continue;
            }
            double left = refundEspe(level, entry.pentacle, entry.amount);
            if (Math.abs(left - entry.amount) > EPS) {
                changed = true;
                if (left <= EPS) {
                    pendingEspe.remove(i);
                } else {
                    pendingEspe.set(i, new PendingEspe(entry.dimension, entry.pentacle, left));
                }
            }
        }
        if (changed) {
            setDirty();
        }
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    public static MatrixShadeContracts load(CompoundTag tag) {
        MatrixShadeContracts data = new MatrixShadeContracts();
        for (Tag entry : tag.getList("contracts", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            if (!row.hasUUID("shade") || !row.hasUUID("summoner")) {
                continue;
            }
            ResourceLocation dim = ResourceLocation.tryParse(row.getString("dimension"));
            if (dim == null) {
                continue;
            }
            Contract contract = new Contract(row.getUUID("shade"), row.getUUID("summoner"), row.getInt("absorbed"),
                    ResourceKey.create(Registries.DIMENSION, dim), NbtUtils.readBlockPos(row.getCompound("pentacle")), row.getDouble("espe"));
            contract.awayTicks = Math.max(0, row.getInt("away"));
            data.contracts.put(contract.shade, contract);
        }
        for (Tag entry : tag.getList("pending_refunds", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            if (row.hasUUID("shade") && row.hasUUID("player")) {
                data.pendingRefunds.add(new PendingRefund(row.getUUID("shade"), row.getUUID("player"), Math.max(0, row.getInt("absorbed"))));
            }
        }
        int droppedEspe = 0;
        for (Tag entry : tag.getList("pending_espe", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            ResourceLocation dim = ResourceLocation.tryParse(row.getString("dimension"));
            double amount = row.getDouble("amount");
            if (dim != null && Double.isFinite(amount) && amount > EPS) {
                if (data.pendingEspe.size() < MAX_PENDING_ESPE) {
                    data.pendingEspe.add(new PendingEspe(ResourceKey.create(Registries.DIMENSION, dim),
                            NbtUtils.readBlockPos(row.getCompound("pentacle")), amount));
                } else {
                    droppedEspe++;
                }
            }
        }
        if (droppedEspe > 0) {
            EssentialCraft5.LOGGER.warn("Matrix Specter ESPE refund list holds more than {} entries; {} were dropped on load",
                    MAX_PENDING_ESPE, droppedEspe);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", VERSION);
        ListTag list = new ListTag();
        for (Contract contract : contracts.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("shade", contract.shade);
            row.putUUID("summoner", contract.summoner);
            row.putInt("absorbed", contract.absorbed);
            row.putString("dimension", contract.dimension.location().toString());
            row.put("pentacle", NbtUtils.writeBlockPos(contract.pentacle));
            row.putDouble("espe", contract.espe);
            row.putInt("away", contract.awayTicks);
            list.add(row);
        }
        tag.put("contracts", list);
        ListTag refunds = new ListTag();
        for (PendingRefund entry : pendingRefunds) {
            CompoundTag row = new CompoundTag();
            row.putUUID("shade", entry.shade);
            row.putUUID("player", entry.summoner);
            row.putInt("absorbed", entry.absorbed);
            refunds.add(row);
        }
        tag.put("pending_refunds", refunds);
        ListTag espe = new ListTag();
        for (PendingEspe entry : pendingEspe) {
            CompoundTag row = new CompoundTag();
            row.putString("dimension", entry.dimension.location().toString());
            row.put("pentacle", NbtUtils.writeBlockPos(entry.pentacle));
            row.putDouble("amount", entry.amount);
            espe.add(row);
        }
        tag.put("pending_espe", espe);
        return tag;
    }

    // ------------------------------------------------------------------
    // 事件（Forge 总线；类名带模块前缀，见 tools/check_event_handlers.py）
    // ------------------------------------------------------------------

    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class MatrixShadeContractEvents {

        private static int clock;

        private MatrixShadeContractEvents() {
        }

        /** 召唤者死了（没被取消的死亡）：失败，损伤当场还进他的玩家数据（重生时跟着复制），导流符等重生 */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void matrixShadeSummonerDeath(LivingDeathEvent event) {
            if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
                return;
            }
            failAllOf(player, Failure.SUMMONER_DIED);
        }

        /**
         * 召唤者下线：失败。这个事件发在玩家存盘之前，损伤当场还进他的玩家数据、导流符留在他存档里等下次上线，退还和他的玩家文件一起落盘
         * （记进这份契约表的话，两份存档不同时落盘，崩服回档时会出现「损伤转走了、契约却没了」）。
         */
        @SubscribeEvent
        public static void matrixShadeSummonerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                failAllOf(player, Failure.SUMMONER_OFFLINE);
            }
        }

        /** 召唤者换维度：失败，当场退 */
        @SubscribeEvent
        public static void matrixShadeSummonerDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                failAllOf(player, Failure.SUMMONER_DIMENSION);
            }
        }

        @SubscribeEvent
        public static void matrixShadeLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                deliver(player);
            }
        }

        @SubscribeEvent
        public static void matrixShadeRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                deliver(player);
            }
        }

        @SubscribeEvent
        public static void matrixShadeServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            clock++;
            if (clock % CHECK_INTERVAL != 0) {
                return;
            }
            MinecraftServer server = event.getServer();
            if (server == null) {
                return;
            }
            MatrixShadeContracts data = get(server);
            data.checkSummoners(server, CHECK_INTERVAL);
            if (clock % ESPE_RETRY_INTERVAL == 0) {
                data.retryEspe(server);
            }
        }

        private static void failAllOf(ServerPlayer player, Failure reason) {
            MatrixShadeContracts data = get(player.serverLevel());
            for (Contract contract : data.contractsOf(player.getUUID())) {
                data.failContract(player.server, Objects.requireNonNull(contract.shade), reason);
            }
        }
    }
}
