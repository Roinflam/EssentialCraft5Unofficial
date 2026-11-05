package com.flameforge.essentialcraft5.mru;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.corruption.MRUCorruption;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.util.ECProtection;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 机器工作时向周围泄漏 MRU（原作 ECUtils.randomIncreaseCorruptionAt / increaseCorruptionAt）：
 * 32 格内有 MRU 聚集体（MRUCU）就给最近的那个加 MRU，没有就在机器处新建一个；同时给 6 格内的玩家叠加「MRU 腐化」（矩阵后遗症，corruption/MRUCorruption）。
 * <p>
 * 原作这两个函数在 32 格内没有聚集体时会因为 getClosestMRUCU 的空指针（被 catch 吞掉）什么都不做，只有附近本来就有聚集体（多半是上古遗迹那团）
 * 时才生效；机器的问题移植时顺便修了，所以移植后到处都会生效（相应的保护见下面「领地」和 api/CorruptionEffects 的「不毁号的几道闸」）。
 * 腐化<b>不扩散</b>：MRUCU 不会把周围方块变成腐化方块。
 * <p>
 * 实现契约（由 MRU 聚集体模块填写本类的方法体，调用方只管调用）：
 * <ul>
 *   <li>只在服务端生效，客户端调用直接返回。</li>
 *   <li>取到的量为 0 时不建、不加聚集体（原作会建一个 0 MRU、永不消失的聚集体），但照原作给玩家叠一层效果
 *       （2026-09-24 改回原作节奏：不计 0 的话默认泄漏上限 1 的机器只有一半 tick 在叠，正好被自然衰减抵消，效果不伤人）。</li>
 *   <li>不能每 tick 搜实体：原作每台机器每 tick 在 64×32×64 的范围里找实体。要先按位置累积，
 *       每 20 tick 统一结算一次（找最近的 MRUCU 或新建、给玩家叠效果）。</li>
 * </ul>
 * <p>
 * 实现：按「维度 → 方块位置」累积泄漏量和调用次数，维度 tick 结束时若游戏时间是 20 的倍数就结算这个维度。
 * 每个位置结算时：
 * <ol>
 *   <li>该位置的区块（及其实体）已经卸载就作废这笔泄漏——不能为此加载区块，也不能在没加载实体的地方新建聚集体（会和已有的重复）。</li>
 *   <li>给水平 32、竖直 16 格内最近的聚集体加上累计量；没有就在该方块中心新建一个
 *       （原作 createMRUCUAt：平衡值 1 + randomFloat，即 0~2 的三角分布；flag = true 第一轮不伤人；alwaysStay = false）。</li>
 *   <li>对该位置水平 6、竖直 3 格内非创造、非旁观的玩家叠加 MRU 腐化，叠加次数 = 这个位置累计的调用次数（含取到 0 的），
 *       所以效果的增长速度和原作「每 tick 叠一次」相同。</li>
 * </ol>
 *
 * <h2>领地（第十批，{@code general.corruption.respect_claims}，默认开）</h2>
 * 不然邻居在领地边上开几台漏 MRU 的机器，就能把你家里的聚集体喂过 1 万，或者隔着边界给你叠 MRU 腐化。所以：
 * <ul>
 *   <li>第 2 步只把泄漏汇进<b>泄漏方能动</b>的聚集体。泄漏点那一格里正好飘着的那团（就是在这里凝出来的）直接汇进去，不问；
 *       别的从近到远最多试 {@value #MAX_FEED_TRIES} 团，第一团能动的就汇进去，都不行才在泄漏点另凝一团（以后它就在泄漏点那一格，照上一条直接汇）。
 *       <ul>
 *         <li>泄漏点是本模组的机器：以机器主人的身份问（没主人的算不能），结论记在机器上（{@link LeakFeedRecord}，
 *             能动、不能动都记 {@value #FEED_RECORD_TICKS} 刻，存盘）。主人不在线时只认以前问出「能动」、记在机器上的那团（过没过期都认），
 *             别的一律当不能动。每问一次都是以主人的身份对那一格发一次破坏事件（混合端上插件会当成他真挖了一格），所以只在主人在线、
 *             没记过或记录过期时才问。</li>
 *         <li>泄漏点是仪式（带「谁漏的」的重载）：丢东西的人在线就以他的身份问；没有人（发射器吐的钻石）或他已经下线，
 *             只汇进泄漏点那一格的聚集体。</li>
 *         <li>两样都不是（别的模组直接调本类）：照旧找最近的。</li>
 *       </ul>
 *       主人在线、却因为探测预算用完或区块没加载全问不出结论时，这笔泄漏留到下一轮再结算（最多 {@value #MAX_DEFERRALS} 轮），
 *       不急着另凝一团（重启后一台主人在线的机器群第一轮会一下子问很多格）。</li>
 *   <li>第 3 步「MRU 腐化」只叠给漏的那个人：机器 → 机器的主人（单人里没记主人的算房主；多人服里没主人的机器谁都不叠），
 *       仪式 → 丢东西的人（没有人时谁都不叠）。队友、路过的人、邻居都不叠，也就不用为此探测任何格子。
 *       别的模组直接调本类的照旧叠给范围内所有人。</li>
 * </ul>
 * 单人、野外、没有领地模组时问什么都放行，和以前一样（叠 MRU 腐化那一步除外：只叠给主人）。
 */
public final class ECCorruption {

    /** 结算间隔（tick） */
    public static final int SETTLE_INTERVAL = 20;
    /** 原作 getClosestMRUCU(w, p, 32)：水平 ±32、竖直 ±16 */
    public static final int SEARCH_RADIUS = 32;
    /** 领地开着时一笔泄漏最多试几团聚集体（从近到远） */
    public static final int MAX_FEED_TRIES = 4;
    /** 机器上「这团能不能汇」的结论记多久（游戏刻，1 小时） */
    public static final int FEED_RECORD_TICKS = 72_000;
    /** 主人在线却问不出结论时，这笔泄漏最多往后挪几轮 */
    public static final int MAX_DEFERRALS = 3;
    /** 原作 new AxisAlignedBB(p).grow(6, 3, 6) */
    private static final double EFFECT_RANGE_XZ = 6D;
    private static final double EFFECT_RANGE_Y = 3D;

    /** 一个位置上攒着的泄漏 */
    private static final class Pending {
        int amount;
        int calls;
        /** 最近一次给了「谁漏的」的那个人（仪式扔东西的人）；没有为 null */
        @Nullable
        UUID actor;
        /** 走过带「谁漏的」的重载（仪式）：这时没有人（发射器吐的钻石）也不能随便汇进别人的聚集体 */
        boolean attributed;
        /** 已经因为问不出结论往后挪过几轮 */
        int deferred;
    }

    /** 选汇进哪团的结果：target 为 null 时 retry 表示「主人在线、只是这一轮问不出来」 */
    private record Choice(@Nullable MRUPresenceEntity target, boolean retry) {
        static final Choice NONE = new Choice(null, false);
    }

    /** 只在服务端线程读写 */
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Pending>> PENDING = new HashMap<>();

    private ECCorruption() {
    }

    /** 原作 randomIncreaseCorruptionAt：泄漏 0~maxAmount 之间的随机量；取到 0 也算一次（照样给附近玩家叠效果） */
    public static void randomIncreaseAt(Level level, BlockPos pos, RandomSource random, int maxAmount) {
        if (maxAmount <= 0) {
            return;
        }
        record(level, pos, maxAmount == Integer.MAX_VALUE ? random.nextInt(maxAmount) : random.nextInt(maxAmount + 1));
    }

    /** 原作 increaseCorruptionAt：在 pos 处泄漏 amount MRU（先记账，下一次 20 tick 结算时生效） */
    public static void increaseAt(Level level, BlockPos pos, int amount) {
        if (amount > 0) {
            record(level, pos, amount);
        }
    }

    /**
     * 同上，并记下是谁漏的（钻石仪式传丢钻石的人，没有人时传 null）：结算时只汇进他能动的聚集体、MRU 腐化只叠给他（见类注释「领地」）；
     * 传了 null 的（发射器吐的钻石）只汇进泄漏点那一格的聚集体。泄漏点是本模组的机器时不用这个重载，按机器主人算。
     */
    public static void increaseAt(Level level, BlockPos pos, int amount, @Nullable Player actor) {
        if (amount > 0) {
            Pending pending = record(level, pos, amount);
            if (pending != null) {
                pending.attributed = true;
                if (actor != null) {
                    pending.actor = actor.getUUID();
                }
            }
        }
    }

    @Nullable
    private static Pending record(Level level, BlockPos pos, int amount) {
        if (amount < 0 || !(level instanceof ServerLevel)) {
            return null;
        }
        Long2ObjectOpenHashMap<Pending> byPos = PENDING.computeIfAbsent(level.dimension(), key -> new Long2ObjectOpenHashMap<>());
        long key = pos.asLong();
        Pending pending = byPos.get(key);
        if (pending == null) {
            pending = new Pending();
            byPos.put(key, pending);
        }
        pending.amount = (int) Math.min(Integer.MAX_VALUE, (long) pending.amount + amount);
        pending.calls++;
        return pending;
    }

    /** 立即结算 level 里攒着的全部泄漏。正常由维度 tick 每 20 tick 调一次；GameTest 也直接调它。 */
    public static void settle(ServerLevel level) {
        settle(level, level.players());
    }

    /**
     * 同上，叠 MRU 腐化时只看 players 里的人（GameTest 的真玩家不在 {@code level.players()} 里，由测试传进来；正常游戏传全部玩家）。
     */
    public static void settle(ServerLevel level, List<? extends ServerPlayer> players) {
        // 先整张表拿出来再处理：结算过程中如果又有泄漏进来（含往后挪的），记到新表里，下一轮再算
        Long2ObjectOpenHashMap<Pending> byPos = PENDING.remove(level.dimension());
        if (byPos == null || byPos.isEmpty()) {
            return;
        }
        for (Long2ObjectMap.Entry<Pending> entry : byPos.long2ObjectEntrySet()) {
            settleAt(level, BlockPos.of(entry.getLongKey()), entry.getValue(), players);
        }
    }

    private static void settleAt(ServerLevel level, BlockPos pos, Pending pending, List<? extends ServerPlayer> players) {
        if (!level.areEntitiesLoaded(ChunkPos.asLong(pos))) {
            return;
        }
        if (pending.amount > 0) {
            Choice choice = chooseTarget(level, pos, pending);
            if (choice.target() != null) {
                choice.target().getStorage().addMRU(pending.amount, true);
            } else if (choice.retry() && pending.deferred < MAX_DEFERRALS) {
                defer(level, pos, pending);
            } else {
                RandomSource random = level.getRandom();
                float balance = 1F + random.nextFloat() - random.nextFloat();
                level.addFreshEntity(MRUPresenceEntity.create(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        pending.amount, balance, true, false));
            }
        }
        AABB range = new AABB(pos).inflate(EFFECT_RANGE_XZ, EFFECT_RANGE_Y, EFFECT_RANGE_XZ);
        Predicate<ServerPlayer> recipient = stackRecipients(level, pos, pending);
        for (ServerPlayer player : players) {
            if (!player.isCreative() && !player.isSpectator() && player.isAlive() && range.intersects(player.getBoundingBox())
                    && recipient.test(player)) {
                MRUCorruption.stack(player, pending.calls);
            }
        }
    }

    /** 这一轮问不出结论：泄漏量（不含叠效果的次数，那一步这一轮已经做了）留到下一轮 */
    private static void defer(ServerLevel level, BlockPos pos, Pending pending) {
        Pending next = record(level, pos, pending.amount);
        if (next != null) {
            next.calls--;   // record 记了一次调用；叠效果的次数这一轮已经用掉了
            next.attributed |= pending.attributed;
            if (next.actor == null) {
                next.actor = pending.actor;
            }
            next.deferred = Math.max(next.deferred, pending.deferred + 1);
        }
    }

    /** 这笔泄漏汇进哪一团（见类注释「领地」）；{@code respect_claims} 关着时照原作取最近的 */
    private static Choice chooseTarget(ServerLevel level, BlockPos pos, Pending pending) {
        if (!CorruptionEffects.respectClaims()) {
            return new Choice(MRUPresenceEntity.findClosest(level, pos, SEARCH_RADIUS), false);
        }
        MRUBlockEntity machine = level.getBlockEntity(pos) instanceof MRUBlockEntity found ? found : null;
        if (machine == null && !pending.attributed) {
            return new Choice(MRUPresenceEntity.findClosest(level, pos, SEARCH_RADIUS), false);   // 别的模组直接调本类，照旧
        }
        List<MRUPresenceEntity> near = MRUPresenceEntity.findNear(level, pos, SEARCH_RADIUS);
        if (near.isEmpty()) {
            return Choice.NONE;
        }
        for (MRUPresenceEntity presence : near) {
            if (presence.blockPosition().equals(pos)) {
                return new Choice(presence, false);   // 就是在这里凝出来的那团：不问
            }
        }
        if (machine != null) {
            ServerPlayer owner = ECProtection.onlineOwner(machine);
            boolean undecided = false;
            for (int i = 0; i < near.size() && i < MAX_FEED_TRIES; i++) {
                ECProtection.Verdict verdict = feedVerdict(level, machine, owner, near.get(i));
                if (verdict == ECProtection.Verdict.ALLOW) {
                    return new Choice(near.get(i), false);
                }
                undecided |= verdict == ECProtection.Verdict.UNKNOWN && owner != null;
            }
            return new Choice(null, undecided);
        }
        ServerPlayer actor = pending.actor == null ? null : ECProtection.onlinePlayer(level.getServer(), pending.actor);
        if (actor == null) {
            return Choice.NONE;   // 没有人（发射器）或人已下线：只汇进泄漏点那一格的，上面没找到
        }
        boolean undecided = false;
        for (int i = 0; i < near.size() && i < MAX_FEED_TRIES; i++) {
            ECProtection.Verdict verdict = ECProtection.check(actor, level, near.get(i).blockPosition(), null);
            if (verdict == ECProtection.Verdict.ALLOW) {
                return new Choice(near.get(i), false);
            }
            undecided |= verdict == ECProtection.Verdict.UNKNOWN;
        }
        return new Choice(null, undecided);
    }

    /**
     * 机器的泄漏能不能汇进 presence：先查机器上的记录（{@link LeakFeedRecord}），主人在线、没记过或过期了才以主人身份探测并记下。
     * 主人不在线：只认以前记下的「能动」（过没过期都认），否则 UNKNOWN；服主关了 {@code require_owner_online} 时放行（同 {@link ECProtection#checkAsOwner}）。
     * 没有主人（多人服）DENY。
     */
    private static ECProtection.Verdict feedVerdict(ServerLevel level, MRUBlockEntity machine, @Nullable ServerPlayer owner, MRUPresenceEntity presence) {
        LeakFeedRecord record = machine.leakFeedRecord();
        long now = level.getGameTime();
        BlockPos target = presence.blockPosition();
        if (owner != null) {
            Boolean known = owner.getUUID().equals(record.by()) ? record.lookup(presence.getUUID(), target.asLong(), now, false) : null;
            if (known != null) {
                return known ? ECProtection.Verdict.ALLOW : ECProtection.Verdict.DENY;
            }
            ECProtection.Verdict verdict = ECProtection.check(owner, level, target, machine);
            if (verdict != ECProtection.Verdict.UNKNOWN) {
                record.put(owner.getUUID(), presence.getUUID(), target.asLong(), verdict == ECProtection.Verdict.ALLOW, now + FEED_RECORD_TICKS);
                level.blockEntityChanged(machine.getBlockPos());   // 记录要存盘；不通知比较器
            } else if (owner.getUUID().equals(record.by())
                    && Boolean.TRUE.equals(record.lookup(presence.getUUID(), target.asLong(), now, true))) {
                return ECProtection.Verdict.ALLOW;   // 这一刻问不出来（预算用完）：过期的「能动」先照认，下次再问
            }
            return verdict;
        }
        UUID recorded = machine.getOwner();
        if (recorded == null) {
            return ECProtection.Verdict.DENY;
        }
        if (!ECProtection.Config.requireOwnerOnline()) {
            return ECProtection.Verdict.ALLOW;
        }
        Boolean known = recorded.equals(record.by()) ? record.lookup(presence.getUUID(), target.asLong(), now, true) : null;
        return Boolean.TRUE.equals(known) ? ECProtection.Verdict.ALLOW : ECProtection.Verdict.UNKNOWN;
    }

    /**
     * MRU 腐化叠给谁（见类注释「领地」第二条），不探测任何格子：{@code respect_claims} 关着、或者不是真玩家，照旧叠；
     * 泄漏点是本模组的机器 → 只叠给在线的主人（{@link ECProtection#onlineOwner}：单人里算房主，多人服没主人谁都不叠）；
     * 走过带人重载的仪式 → 只叠给丢东西的人（没有人时谁都不叠）；两样都不是 → 照旧叠给范围内所有人。
     */
    private static Predicate<ServerPlayer> stackRecipients(ServerLevel level, BlockPos pos, Pending pending) {
        if (!CorruptionEffects.respectClaims()) {
            return player -> true;
        }
        UUID only;
        if (level.getBlockEntity(pos) instanceof MRUBlockEntity machine) {
            ServerPlayer owner = ECProtection.onlineOwner(machine);
            only = owner == null ? null : owner.getUUID();
        } else if (pending.attributed) {
            only = pending.actor;
        } else {
            return player -> true;
        }
        return player -> !ECProtection.isRealPlayer(player) || only != null && only.equals(player.getUUID());
    }

    /** 世界 tick 驱动结算；维度卸载、服务器关闭时丢掉没结算的泄漏（单人模式换存档时不能带过去） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class Events {

        private Events() {
        }

        @SubscribeEvent
        public static void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level
                    && level.getGameTime() % SETTLE_INTERVAL == 0) {
                settle(level);
            }
        }

        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            if (event.getLevel() instanceof ServerLevel level) {
                PENDING.remove(level.dimension());
            }
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            PENDING.clear();
        }
    }
}
