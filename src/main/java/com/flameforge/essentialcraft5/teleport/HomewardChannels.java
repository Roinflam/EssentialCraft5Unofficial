package com.flameforge.essentialcraft5.teleport;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.HomewardCompassItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECHomewardCompass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 归途罗盘的引导、打断、传送与冷却（第十二批，batch12.md §4.5.2、§4.5.3）。只在服务端主线程读写。
 *
 * <h2>引导</h2>
 * {@link HomewardCompassItem#tryStart} 判完起手条件后调 {@link #begin}：进入使用状态（{@code channel_ticks}），记下起点和目标，
 * 给目标区块加一张原版 {@code PORTAL} 票（300 刻自己过期，不用收；同一张票再加一次只是续期），放一声音量 0.3 的传送门起动声。
 * 每人同时只有一个引导。引导中这几样会打断，打断、松手都不扣钱、不进冷却：
 * <ul>
 *   <li>离起点超过 {@code move_tolerance}（直线距离，水平 + 竖直）：{@link #onUseTick}；</li>
 *   <li>受伤：{@code LivingDamageEvent} 最低优先级、没被取消、伤害 &gt; 0（{@link #onHomewardDamage}）。混合端插件取消的伤害事件走不到这里；</li>
 *   <li>身上挂上了「锚定」（别人的相位锚模块给的）：起手时不许，引导中挂上的同样当场打断，到点（含重试）再查一次，免得起手以后才被锚住的人照样传走；</li>
 *   <li>松手（{@code releaseUsing}）、换了手里的东西（原版直接停止使用、不调 releaseUsing，所以登记表另外核对，见 {@link #busy}）、换维度、死亡、下线。</li>
 * </ul>
 *
 * <h2>到点（{@link #finish}）</h2>
 * {@link ECTeleport#check}（落点安全、落脚格和脚下一格的三态权限、出生点保护、世界边界、区块没加载）：
 * <ul>
 *   <li>OK：按到点时的位置算价，再 {@code canAfford} 一次，{@link ECTeleport#teleport} 传成以后才扣 MRU（全从身上的储存物品扣）、写冷却、清摔落距离，
 *       两头各放一声末影人传送声。先传后扣：插件取消了传送就什么都不扣（同魔力传送器）；</li>
 *   <li>UNLOADED / PENDING（目标区块还在加载、权限一时给不出结论）：挂进重试表，玩家原地不动的话每 {@value #RETRY_INTERVAL} 刻再查一次（每次续一下区块票），
 *       最多 {@value #RETRY_LIMIT} 刻，还不行就说「目的地还没准备好」，不扣钱；重试期间走开、受伤同样打断；</li>
 *   <li>其余（被挡住、脚下没方块、有伤人的方块、没有权限、在世界外）：说原因（复用魔力传送器界面的那组文字），不扣钱、不进冷却。</li>
 * </ul>
 *
 * <h2>战斗锁</h2>
 * 被生物（含玩家）打中过以后 {@code combat_lock_ticks} 刻内不能起手。不用原版的 {@code getLastHurtByMob}：原版在 {@code LivingEntity.baseTick} 里
 * 100 刻后、或者打人的那只死了就把它清掉，撑不住默认 200 刻的锁，杀掉追兵也会让锁提前解开。所以在同一个伤害监听里自己记「最后一次被活物打中的服务端刻数」
 * （{@code server.getTickCount()}，不受 /time 影响）：伤害源的 {@code getEntity()} 是活物、不是自己就记，伤害数值不限（伤害吸收整下吃掉的也算挨了打）；
 * 弹射物按射手算。下线不删这条记录：退出重进不能拿来提前解开战斗锁；过期的记录由 {@code recordHit} 顺手清，关服时全清。
 *
 * <h2>冷却</h2>
 * 到期刻记在玩家持久数据（{@link HomewardCompassItem#READY_KEY}），同时加同样长的物品冷却给客户端画遮罩；登录、从末地回来补回物品冷却，死亡重生清掉
 * （同全息诱饵投影仪）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class HomewardChannels {

    /** 区块票的等级半径（同魔力传送器：落点所在区块和一圈邻区块都完整加载） */
    public static final int TICKET_RADIUS = 1;
    /** 目的地没准备好时每隔几刻再查一次 */
    public static final int RETRY_INTERVAL = 10;
    /** 到点以后最多再等几刻 */
    public static final int RETRY_LIMIT = 40;
    /** 战斗记录超过这么多刻就当过期、顺手清掉（配置的战斗锁最长 6000） */
    private static final int HIT_MAX_AGE = 6000;
    /** 每隔这么多刻清一次过期的战斗记录 */
    private static final int HIT_PRUNE_INTERVAL = 1200;

    private static final String MSG = "message.essentialcraft5.homeward_compass.";
    public static final String MSG_MOVED = MSG + "moved";
    public static final String MSG_HURT = MSG + "hurt";
    public static final String MSG_RELEASED = MSG + "released";
    public static final String MSG_NOT_READY = MSG + "not_ready";
    public static final String MSG_FAILED = MSG + "failed";
    public static final String MSG_ARRIVED = MSG + "arrived";
    /** 落点不合格的原因：复用魔力传送器界面的文字（blocked / no_floor / hazard / denied / out_of_world / cancelled） */
    private static final String TELEPORTER = "gui.essentialcraft5.magical_teleporter.";

    /** 到点（或重试一次）的结果 */
    public enum Outcome {
        /** 传走了、扣了钱、进了冷却 */
        TELEPORTED,
        /** 目的地还没准备好，进了重试表 */
        WAITING,
        /** 没传成，不扣钱、不进冷却 */
        FAILED
    }

    /**
     * 一次引导。
     *
     * @param player 引导的人（GameTest 的测试玩家不在玩家列表里，所以直接存对象，下线 / 关服时清表）
     * @param origin 起手时站的位置
     * @param home   归途点
     */
    private record Channel(ServerPlayer player, Vec3 origin, HomewardCompassItem.Home home) {
    }

    /**
     * 到点了但目的地还没准备好，等着重试。
     *
     * @param origin    到点时站的位置（之后走开就打断）
     * @param deadline  过了这一刻还不行就放弃（服务端刻数）
     * @param nextCheck 下一次查的刻数
     */
    private record Retry(ServerPlayer player, Vec3 origin, HomewardCompassItem.Home home, int deadline, int nextCheck) {
    }

    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static final Map<UUID, Retry> RETRIES = new HashMap<>();
    /** 玩家 UUID → 最后一次被活物打中的服务端刻数 */
    private static final Map<UUID, Integer> LAST_HIT = new HashMap<>();
    private static int lastPrune;

    private HomewardChannels() {
    }

    // ------------------------------------------------------------------ 引导

    /**
     * 这名玩家正在引导或在等目的地。登记表里有、人却已经不在用罗盘了（换了手里的东西、被别的东西停止使用）就当场删掉，免得卡住以后的起手。
     */
    public static boolean busy(ServerPlayer player) {
        UUID id = player.getUUID();
        if (RETRIES.containsKey(id)) {
            return true;
        }
        Channel channel = CHANNELS.get(id);
        if (channel == null) {
            return false;
        }
        if (channel.player() != player || !usingCompass(player)) {
            CHANNELS.remove(id);
            return false;
        }
        return true;
    }

    private static boolean usingCompass(ServerPlayer player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof HomewardCompassItem;
    }

    /**
     * 起手条件都过了以后开始引导（见类注释）。别的模组取消了「开始使用物品」事件时进不了使用状态，返回 false、什么都不登记。
     *
     * @param dest 归途点所在的世界（调用方已经确认存在）
     */
    public static boolean begin(ServerPlayer player, InteractionHand hand, HomewardCompassItem.Home home, ServerLevel dest) {
        player.startUsingItem(hand);
        if (!usingCompass(player)) {
            return false;
        }
        CHANNELS.put(player.getUUID(), new Channel(player, player.position(), home));
        preload(dest, home.pos());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS,
                0.3F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        return true;
    }

    /** 给目标区块加（或续）一张 PORTAL 票；坐标在世界外的不加（物品 NBT 可能是改出来的） */
    private static void preload(ServerLevel dest, BlockPos pos) {
        if (!dest.isInWorldBounds(pos) || !dest.getWorldBorder().isWithinBounds(pos)) {
            return;
        }
        dest.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), TICKET_RADIUS, pos);
    }

    /** 引导中每刻（服务端，罗盘的 onUseTick）：走开就打断 */
    public static void onUseTick(ServerPlayer player) {
        Channel channel = CHANNELS.get(player.getUUID());
        if (channel == null || channel.player() != player) {
            // 没登记的引导（重进游戏后残留的使用状态之类）：直接停掉，不传
            player.stopUsingItem();
            return;
        }
        if (moved(player, channel.origin())) {
            interrupt(player, MSG_MOVED);
        } else if (anchored(player)) {
            interrupt(player, HomewardCompassItem.MSG_ANCHORED);
        }
    }

    private static boolean anchored(ServerPlayer player) {
        return player.hasEffect(ECEffects.ANCHORED.get());
    }

    private static boolean moved(ServerPlayer player, Vec3 origin) {
        double tolerance = ECHomewardCompass.Config.values().moveTolerance();
        return player.position().distanceToSqr(origin) > tolerance * tolerance;
    }

    /** 松手（罗盘的 releaseUsing）：取消，不扣钱、不进冷却 */
    public static void cancel(ServerPlayer player) {
        if (CHANNELS.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.translatable(MSG_RELEASED).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** 打断引导或重试：停止使用、删登记、在动作栏说原因 */
    private static void interrupt(ServerPlayer player, String messageKey) {
        boolean wasChanneling = CHANNELS.remove(player.getUUID()) != null;
        boolean wasWaiting = RETRIES.remove(player.getUUID()) != null;
        if (!wasChanneling && !wasWaiting) {
            return;
        }
        if (usingCompass(player)) {
            player.stopUsingItem();
        }
        player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), true);
    }

    /** 不说话地清掉这名玩家的引导和重试（换维度、死亡、下线） */
    private static void drop(ServerPlayer player) {
        boolean channeling = CHANNELS.remove(player.getUUID()) != null;
        RETRIES.remove(player.getUUID());
        if (channeling && usingCompass(player)) {
            player.stopUsingItem();
        }
    }

    // ------------------------------------------------------------------ 到点

    /** 引导满了（罗盘的 finishUsingItem，服务端）：查落点、传送、扣费、冷却，或者进重试表 */
    public static Outcome finish(ServerPlayer player) {
        Channel channel = CHANNELS.remove(player.getUUID());
        if (channel == null || channel.player() != player) {
            return Outcome.FAILED;
        }
        return attempt(player, channel.home(), 0);
    }

    /**
     * 试着传一次。
     *
     * @param waited 已经重试了几刻（0 = 刚到点）；到了 {@link #RETRY_LIMIT} 还是没准备好就放弃
     */
    private static Outcome attempt(ServerPlayer player, HomewardCompassItem.Home home, int waited) {
        MinecraftServer server = player.server;
        ECHomewardCompass.Config.Values config = ECHomewardCompass.Config.values();
        ServerLevel dest = server.getLevel(home.dimension());
        if (dest == null) {
            return fail(player, Component.translatable(HomewardCompassItem.MSG_NO_DIMENSION));
        }
        ResourceKey<Level> from = player.level().dimension();
        // 引导期间规则可能变了（热重载）：到点再按现在的规则判一次
        if (!config.enabled()) {
            return fail(player, Component.translatable(HomewardCompassItem.MSG_DISABLED));
        }
        if (!HomewardCompassItem.targetAllowed(from, home.dimension(), config)) {
            return fail(player, Component.translatable(config.allowCrossDimension()
                    ? HomewardCompassItem.MSG_DENIED_DIMENSION : HomewardCompassItem.MSG_CROSS_DISABLED));
        }
        if (!HomewardCompassItem.rankAllows(player, from, home.dimension())) {
            return fail(player, MatrixRank.gateMessage(player, MatrixRank.Gate.HOANNA_PORTAL));
        }
        if (anchored(player)) {
            return fail(player, Component.translatable(HomewardCompassItem.MSG_ANCHORED));
        }
        preload(dest, home.pos());
        // 探测预算按玩家算（caller 传 null），每人各有一份，不和别人的罗盘抢
        ECTeleport.Check check = ECTeleport.check(player, dest, home.pos(), null);
        ECTeleport.Result result = check.result();
        if (result == ECTeleport.Result.UNLOADED || result == ECTeleport.Result.PENDING) {
            if (waited >= RETRY_LIMIT) {
                return fail(player, Component.translatable(MSG_NOT_READY));
            }
            int now = server.getTickCount();
            RETRIES.put(player.getUUID(), new Retry(player, player.position(), home, now + RETRY_LIMIT - waited, now + RETRY_INTERVAL));
            return Outcome.WAITING;
        }
        Vec3 landing = check.landing();
        if (result != ECTeleport.Result.OK || landing == null) {
            return fail(player, Component.translatable(TELEPORTER + result.key()));
        }
        boolean sameDimension = from.equals(home.dimension());
        int price = HomewardCompassItem.cost(player.blockPosition(), home.pos(), sameDimension, config);
        if (!MRUDrain.canAfford(player, ItemStack.EMPTY, price)) {
            MRUStaffItem.tellNoMRU(player, price);
            return Outcome.FAILED;
        }
        ServerLevel departureLevel = player.serverLevel();
        Vec3 departure = player.position();
        if (!ECTeleport.teleport(player, dest, landing, player.getYRot(), player.getXRot())) {
            return fail(player, Component.translatable(TELEPORTER + "cancelled"));
        }
        player.resetFallDistance();
        // 同一刻里 canAfford 和这里之间没有别的东西动 MRU，扣费一定成功
        MRUDrain.playerUseMRU(player, ItemStack.EMPTY, price);
        HomewardCompassItem.startCooldown(player, sameDimension ? config.cooldownTicks() : config.crossDimensionCooldownTicks());
        player.awardStat(Stats.ITEM_USED.get(ECHomewardCompass.HOMEWARD_COMPASS.get()));
        departureLevel.playSound(null, departure.x, departure.y, departure.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1F, 1F);
        dest.playSound(null, landing.x, landing.y, landing.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1F, 1F);
        player.displayClientMessage(Component.translatable(MSG_ARRIVED).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return Outcome.TELEPORTED;
    }

    private static Outcome fail(ServerPlayer player, Component reason) {
        player.displayClientMessage(Component.translatable(MSG_FAILED, reason).withStyle(ChatFormatting.RED), true);
        return Outcome.FAILED;
    }

    /** 重试表每刻走一遍（服务端刻末）：走开、换了世界的打断；到了查的刻数再试一次 */
    private static void tickRetries(MinecraftServer server) {
        if (RETRIES.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Retry>> iterator = RETRIES.entrySet().iterator();
        Map<UUID, Retry> due = new HashMap<>();
        while (iterator.hasNext()) {
            Retry retry = iterator.next().getValue();
            ServerPlayer player = retry.player();
            if (player.isRemoved() || !player.isAlive()) {
                iterator.remove();
                continue;
            }
            if (now - retry.nextCheck() >= 0) {
                due.put(player.getUUID(), retry);
            }
        }
        for (Retry retry : due.values()) {
            ServerPlayer player = retry.player();
            if (moved(player, retry.origin())) {
                interrupt(player, MSG_MOVED);
                continue;
            }
            RETRIES.remove(player.getUUID());
            int waited = RETRY_LIMIT - (retry.deadline() - now);
            attempt(player, retry.home(), Math.max(RETRY_INTERVAL, waited));
        }
    }

    /** 清掉不在用罗盘的引导（换了手里的东西时原版直接停止使用，不会通知物品） */
    private static void sweepChannels() {
        if (CHANNELS.isEmpty()) {
            return;
        }
        CHANNELS.values().removeIf(channel -> channel.player().isRemoved() || !usingCompass(channel.player()));
    }

    // ------------------------------------------------------------------ 战斗锁

    /** 战斗锁还剩几刻（0 = 能起手） */
    public static int combatLockLeft(ServerPlayer player, int lockTicks) {
        Integer hit = LAST_HIT.get(player.getUUID());
        if (hit == null || lockTicks <= 0) {
            return 0;
        }
        int age = player.server.getTickCount() - hit;
        // 比现在还晚的记录（重启后刻数从 0 算）当过期
        if (age < 0 || age >= lockTicks) {
            return 0;
        }
        return lockTicks - age;
    }

    private static void recordHit(ServerPlayer victim) {
        int now = victim.server.getTickCount();
        LAST_HIT.put(victim.getUUID(), now);
        if (now - lastPrune >= HIT_PRUNE_INTERVAL || now < lastPrune) {
            lastPrune = now;
            LAST_HIT.values().removeIf(t -> now - t > HIT_MAX_AGE || now < t);
        }
    }

    // ------------------------------------------------------------------ 事件

    /** 被活物打中：记战斗锁；伤害 &gt; 0 时打断引导 / 重试 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHomewardDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            recordHit(player);
        }
        if (event.getAmount() > 0F && (CHANNELS.containsKey(player.getUUID()) || RETRIES.containsKey(player.getUUID()))) {
            interrupt(player, MSG_HURT);
        }
    }

    @SubscribeEvent
    public static void onHomewardServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        sweepChannels();
        tickRetries(event.getServer());
    }

    @SubscribeEvent
    public static void onHomewardLogin(PlayerEvent.PlayerLoggedInEvent event) {
        HomewardCompassItem.restoreCooldown(event.getEntity());
    }

    /** 从末地回来（换了新的玩家对象，物品冷却没了）补回冷却；死亡重生清掉，同全息诱饵投影仪 */
    @SubscribeEvent
    public static void onHomewardRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.isEndConquered()) {
            HomewardCompassItem.restoreCooldown(event.getEntity());
        } else {
            HomewardCompassItem.clearReady(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onHomewardChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            drop(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHomewardDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            drop(player);
        }
    }

    @SubscribeEvent
    public static void onHomewardLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            drop(player);
        }
    }

    @SubscribeEvent
    public static void onHomewardServerStopped(ServerStoppedEvent event) {
        CHANNELS.clear();
        RETRIES.clear();
        LAST_HIT.clear();
        lastPrune = 0;
    }

    // ------------------------------------------------------------------ 给测试看的

    /** 只给 GameTest：这名玩家在不在重试表里 */
    public static boolean waitingForTest(ServerPlayer player) {
        return RETRIES.containsKey(player.getUUID());
    }

    /** 只给 GameTest：清掉这名玩家的引导、重试和战斗记录 */
    public static void forgetForTest(@Nullable ServerPlayer player) {
        if (player != null) {
            drop(player);
            LAST_HIT.remove(player.getUUID());
        }
    }
}
