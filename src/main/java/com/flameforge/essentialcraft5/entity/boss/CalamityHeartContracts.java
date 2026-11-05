package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.CalamityBeaconItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBossFramework;
import com.flameforge.essentialcraft5.registry.ECShade;
import com.flameforge.essentialcraft5.util.ECItemEntities;
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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 天灾之心的召唤契约（batch13.md §5.3.5）：挂在主世界的 {@code data/essentialcraft5_calamity_heart.dat}，写法照矩阵之影的 {@link MatrixShadeContracts}。
 * <p>
 * 每只用信标召出来的天灾之心一份契约：召唤者、维度、召唤点、召唤时从手里收走的信标（原样）、这次从背包扣的暗影核心数。
 * 打倒时结清（{@link #defeated}）：信标打上 {@code spent} 还给召唤者，召唤者这一游戏日记一次（{@code boss_daily}）；
 * 失败时退还（{@link #fail}）：信标原样、扣的暗影核心一个不少还回去，这一天不算。两条路都先删契约再做事，同一只不会结两次。
 * <p>
 * 东西怎么交：召唤者的玩家对象在（在线，含刚死、正在下线）就交到他身上——活着进背包（满了掉在脚下），刚死 / 正在下线的先记在他自己的存档里
 * （{@link #PLAYER_KEY}，跟着玩家文件落盘），重生 / 下次上线给；玩家对象不在（重启后读回来的契约，召唤者还没上线）才记进 {@link #pendingDeliveries}，登录时补。
 * 结算过的契约记进召唤者存档里的小表（{@link #MAX_SETTLED} 条），登录补交时先查它：崩服回档时契约表可能回到「还开着」，靠这张表不会交第二次。
 * <p>
 * 失败的情况：召唤者死亡 / 下线 / 换维度（这里的事件和每秒检查判）、名单空了到点、首领连着 {@code abandon_ticks} 不在 tick 范围里（每秒检查判）、
 * 和平难度（首领的 {@link ECBoss#endBoss}）、不是参战者打倒的死亡、被别的东西直接删掉、灾柱连着被拦下立不起来。失败后首领在 {@code dissipate_ticks} 后散去；那时它在没加载的区块里，下次加载时发现契约没了直接散去。
 */
public final class CalamityHeartContracts extends SavedData {

    public static final String NAME = "essentialcraft5_calamity_heart";
    public static final int VERSION = 1;
    /** 每多少刻查一次召唤者（下线兜底、死了、换维度） */
    public static final int CHECK_INTERVAL = 20;
    /** 召唤者存档里的记录（{@link Player#PERSISTED_NBT_TAG} 下） */
    public static final String PLAYER_KEY = EssentialCraft5.MODID + ":calamity_heart";
    /** 每日一次记在玩家数据 {@code boss_daily} 下的键（值 = 最后一次打倒的游戏日序号） */
    public static final String DAILY_KEY = EssentialCraft5.MODID + ":calamity_heart_summon";
    public static final int MAX_SETTLED = 16;
    private static final String TAG_SETTLED = "settled";
    private static final String TAG_HELD = "held_items";

    /** 失败的原因（提示文字 message.essentialcraft5.calamity_heart.failed.&lt;小写名&gt;） */
    public enum Failure {
        SUMMONER_DIED,
        SUMMONER_OFFLINE,
        SUMMONER_DIMENSION,
        ABANDONED,
        PEACEFUL,
        NO_PLAYER_KILL,
        VANISHED,
        PILLAR_BLOCKED;

        public String messageKey() {
            return "message.essentialcraft5.calamity_heart.failed." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** 一份契约 */
    public record Contract(UUID heart, UUID summoner, ResourceKey<Level> dimension, BlockPos point, ItemStack beacon, int cores) {

        public Contract {
            point = point.immutable();
            beacon = beacon.copy();
            cores = Math.max(0, cores);
        }

        /** 失败时退的：信标原样 + 扣的暗影核心 */
        public List<ItemStack> refund() {
            List<ItemStack> out = new ArrayList<>(2);
            if (!beacon.isEmpty()) {
                out.add(beacon.copy());
            }
            if (cores > 0) {
                out.add(new ItemStack(ECShade.SHADE_CORE.get(), cores));
            }
            return out;
        }

        /** 打倒后还的：信标打上 spent */
        public ItemStack spentBeacon() {
            ItemStack out = beacon.isEmpty() ? new ItemStack(com.flameforge.essentialcraft5.registry.ECCalamityHeart.CALAMITY_BEACON.get()) : beacon.copy();
            CalamityBeaconItem.setSpent(out, true);
            return out;
        }
    }

    /** 一笔等召唤者上线才能交的东西（只在他的玩家对象不在时记）；day ≥ 0 时交的时候顺带记每日一次 */
    public record PendingDelivery(UUID heart, UUID summoner, List<ItemStack> items, long day) {
    }

    private final Map<UUID, Contract> contracts = new LinkedHashMap<>();
    private final List<PendingDelivery> pendingDeliveries = new ArrayList<>();
    /** 首领不在（区块没加载或不在实体 tick 范围里）连着多少刻了（只在内存里，重启后从头算） */
    private final Map<UUID, Integer> heartAway = new HashMap<>();

    public CalamityHeartContracts() {
    }

    public static CalamityHeartContracts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CalamityHeartContracts::load, CalamityHeartContracts::new, NAME);
    }

    public static CalamityHeartContracts get(ServerLevel level) {
        return get(level.getServer());
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    @Nullable
    public Contract contract(UUID heart) {
        return contracts.get(heart);
    }

    public static boolean isOpen(ServerLevel level, UUID heart) {
        return get(level).contracts.containsKey(heart);
    }

    public List<Contract> contractsOf(UUID summoner) {
        List<Contract> out = new ArrayList<>();
        for (Contract contract : contracts.values()) {
            if (contract.summoner.equals(summoner)) {
                out.add(contract);
            }
        }
        return out;
    }

    /** 这名玩家名下等上线补交的笔数 */
    public int pendingFor(UUID player) {
        int count = 0;
        for (PendingDelivery entry : pendingDeliveries) {
            if (entry.summoner.equals(player)) {
                count++;
            }
        }
        return count;
    }

    public int openCount() {
        return contracts.size();
    }

    // ------------------------------------------------------------------
    // 每日一次
    // ------------------------------------------------------------------

    /** 这名玩家这一游戏日已经打倒过天灾之心（怎么算新的一天见 {@link BossDaily}：主世界日序号变大，或者离上次满 24 000 刻 gameTime） */
    public static boolean defeatedToday(Player player, MinecraftServer server) {
        IPlayerData data = PlayerDataEvents.get(player);
        return data != null && BossDaily.usedToday(data.getBossDaily(), DAILY_KEY, BossDaily.now(server));
    }

    /**
     * 记一次：日序号只往大里改（往回拨时间后不会把记录拨小），gameTime 记成交到他手上的这一刻（等上线补交的从补交时算起）。
     * day 是打倒那天的日序号，比现在小时按现在算。
     */
    public static void markDaily(Player player, long day) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null || day < 0L || player.getServer() == null) {
            return;
        }
        BossDaily.Stamp now = BossDaily.now(player.getServer());
        BossDaily.mark(data.getBossDaily(), DAILY_KEY, new BossDaily.Stamp(Math.max(day, now.day()), now.gameTime()));
    }

    // ------------------------------------------------------------------
    // 开、结、退
    // ------------------------------------------------------------------

    /** 召唤成功后记一份契约（信标、扣的暗影核心都已经从召唤者身上拿走） */
    public static void open(ServerLevel level, CalamityHeartEntity heart, ServerPlayer summoner, BlockPos point, ItemStack beacon, int cores) {
        CalamityHeartContracts data = get(level);
        data.contracts.put(heart.getUUID(), new Contract(heart.getUUID(), summoner.getUUID(), level.dimension(), point, beacon, cores));
        data.setDirty();
    }

    /** 打倒：结清，spent 的信标还给召唤者、这一天记一次；没有契约时什么都不做，返回 false */
    public static boolean defeated(ServerLevel level, UUID heart) {
        return get(level).defeatContract(level.getServer(), heart);
    }

    public boolean defeatContract(MinecraftServer server, UUID heart) {
        Contract contract = contracts.remove(heart);
        if (contract == null) {
            return false;
        }
        setDirty();
        long day = BossDaily.dayIndex(server);
        List<ItemStack> items = List.of(contract.spentBeacon());
        ServerPlayer summoner = ECProtection.onlinePlayer(server, contract.summoner);
        if (summoner == null) {
            pendingDeliveries.add(new PendingDelivery(heart, contract.summoner, items, day));
            return true;
        }
        Boolean now = settle(summoner, heart, items, day, false);
        if (now != null) {
            summoner.displayClientMessage(Component.translatable(now ? "message.essentialcraft5.calamity_heart.beacon_returned"
                    : "message.essentialcraft5.calamity_heart.beacon_returned_later"), false);
        }
        return true;
    }

    /** 失败：删掉契约，信标和暗影核心原样退回，告诉召唤者，让还加载着的那只开始散去；没有契约时什么都不做，返回 false */
    public static boolean fail(ServerLevel anyLevel, UUID heart, Failure reason) {
        return get(anyLevel.getServer()).failContract(anyLevel.getServer(), heart, reason);
    }

    public boolean failContract(MinecraftServer server, UUID heart, Failure reason) {
        Contract contract = contracts.remove(heart);
        if (contract == null) {
            return false;
        }
        setDirty();
        List<ItemStack> items = contract.refund();
        ServerPlayer summoner = ECProtection.onlinePlayer(server, contract.summoner);
        boolean leaving = reason == Failure.SUMMONER_OFFLINE;
        if (summoner == null) {
            pendingDeliveries.add(new PendingDelivery(heart, contract.summoner, items, -1L));
        } else {
            Boolean now = settle(summoner, heart, items, -1L, leaving);
            if (now != null && !leaving) {
                summoner.displayClientMessage(Component.translatable(reason.messageKey()), false);
                summoner.displayClientMessage(Component.translatable(now ? "message.essentialcraft5.calamity_heart.refunded"
                        : "message.essentialcraft5.calamity_heart.refunded_respawn", contract.cores), false);
            }
        }
        ServerLevel level = server.getLevel(contract.dimension);
        if (level != null) {
            Entity entity = level.getEntity(heart);
            if (entity instanceof CalamityHeartEntity calamity && !calamity.isRemoved() && !calamity.isDeadOrDying()) {
                calamity.beginDissipate();
            }
        }
        return true;
    }

    /** 登录、重生时：补交等着他的东西 */
    public static void deliver(ServerPlayer player) {
        if (player instanceof FakePlayer) {
            return;
        }
        get(player.serverLevel()).deliverTo(player);
    }

    /** 先补契约表里记在他名下的（他存档里记着结算过的只删不交），再把刚死 / 下线时留着的东西交给他（活着才给） */
    public void deliverTo(ServerPlayer player) {
        int count = 0;
        boolean changed = false;
        for (Iterator<PendingDelivery> it = pendingDeliveries.iterator(); it.hasNext(); ) {
            PendingDelivery entry = it.next();
            if (!entry.summoner.equals(player.getUUID())) {
                continue;
            }
            it.remove();
            changed = true;
            if (settle(player, entry.heart, entry.items, entry.day, false) != null) {
                count++;
            }
        }
        if (changed) {
            setDirty();
        }
        if (count > 0) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.calamity_heart.delivered_later", count), false);
        }
        if (player.isAlive() && !player.isRemoved()) {
            List<ItemStack> held = takeHeld(player);
            for (ItemStack stack : held) {
                ECItemEntities.giveOrDrop(player, stack);
            }
            if (!held.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.essentialcraft5.calamity_heart.held_returned"), false);
            }
        }
    }

    // ------------------------------------------------------------------
    // 召唤者自己存档里的记录
    // ------------------------------------------------------------------

    /**
     * 把一份契约的东西交到这名玩家身上并记下「结算过了」，day ≥ 0 时顺带记每日一次；已经记过的什么都不做，返回 null。
     * 活着、没在下线就当场给（返回 true），否则留在他存档里等重生 / 上线（返回 false）。
     */
    @Nullable
    public static Boolean settle(ServerPlayer player, UUID heart, List<ItemStack> items, long day, boolean leaving) {
        if (isSettled(player, heart)) {
            return null;
        }
        markSettled(player, heart);
        markDaily(player, day);
        boolean now = !leaving && player.isAlive() && !player.isRemoved();
        if (now) {
            for (ItemStack stack : items) {
                ECItemEntities.giveOrDrop(player, stack.copy());
            }
        } else {
            CompoundTag record = playerRecord(player);
            ListTag held = record.getList(TAG_HELD, Tag.TAG_COMPOUND);
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    held.add(stack.save(new CompoundTag()));
                }
            }
            record.put(TAG_HELD, held);
        }
        return now;
    }

    public static boolean isSettled(Player player, UUID heart) {
        for (Tag entry : playerRecord(player).getList(TAG_SETTLED, Tag.TAG_INT_ARRAY)) {
            try {
                if (heart.equals(NbtUtils.loadUUID(entry))) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // 坏掉的 UUID 跳过
            }
        }
        return false;
    }

    private static void markSettled(Player player, UUID heart) {
        if (isSettled(player, heart)) {
            return;
        }
        CompoundTag record = playerRecord(player);
        ListTag list = record.getList(TAG_SETTLED, Tag.TAG_INT_ARRAY);
        list.add(NbtUtils.createUUID(heart));
        while (list.size() > MAX_SETTLED) {
            list.remove(0);
        }
        record.put(TAG_SETTLED, list);
    }

    /** 留在他存档里、等重生 / 上线才给的东西（副本） */
    public static List<ItemStack> held(Player player) {
        List<ItemStack> out = new ArrayList<>();
        for (Tag entry : playerRecord(player).getList(TAG_HELD, Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) entry);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    private static List<ItemStack> takeHeld(Player player) {
        List<ItemStack> out = held(player);
        playerRecord(player).remove(TAG_HELD);
        return out;
    }

    private static CompoundTag playerRecord(Player player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag record = persisted.getCompound(PLAYER_KEY);
        persisted.put(PLAYER_KEY, record);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        return record;
    }

    /** 只给 GameTest：删掉这只的契约、这名玩家名下的契约和挂着的补交，什么都不退。参数为 null 的那项不碰 */
    public static void forgetForTest(ServerLevel level, @Nullable UUID heart, @Nullable UUID summoner) {
        CalamityHeartContracts data = get(level);
        if (heart != null) {
            data.contracts.remove(heart);
            data.pendingDeliveries.removeIf(entry -> entry.heart.equals(heart));
        }
        if (summoner != null) {
            data.pendingDeliveries.removeIf(entry -> entry.summoner.equals(summoner));
            data.contracts.values().removeIf(contract -> contract.summoner.equals(summoner));
        }
        data.setDirty();
    }

    // ------------------------------------------------------------------
    // 每秒检查
    // ------------------------------------------------------------------

    /**
     * 召唤者下线（兜底）、死了、换维度就判失败；首领自己连着 {@code abandon_ticks} 刻不在（区块卸载了、或者没人在它的实体 tick 范围里，
     * 比如一队人一起传送回城）也按「没人和它交手」失败退还——这时首领不 tick，名单空了散去那条走不到，召唤者又一直在线、在同一维度，
     * 信标和暗影核心会一直锁在契约里。首领下次加载时发现契约没了，照常散去。（GameTest 直接调，每调一次算过了 {@value #CHECK_INTERVAL} 刻）
     */
    public void checkSummoners(MinecraftServer server) {
        if (contracts.isEmpty()) {
            heartAway.clear();
            return;
        }
        heartAway.keySet().retainAll(contracts.keySet());
        int limit = ECBossFramework.Config.values().abandonTicks();
        for (Contract contract : List.copyOf(contracts.values())) {
            ServerPlayer player = ECProtection.onlinePlayer(server, contract.summoner);
            if (player == null) {
                failContract(server, contract.heart, Failure.SUMMONER_OFFLINE);
            } else if (!player.isAlive()) {
                failContract(server, contract.heart, Failure.SUMMONER_DIED);
            } else if (!player.level().dimension().equals(contract.dimension)) {
                failContract(server, contract.heart, Failure.SUMMONER_DIMENSION);
            } else if (heartTicking(server, contract)) {
                heartAway.remove(contract.heart);
            } else if (heartAway.merge(contract.heart, CHECK_INTERVAL, Integer::sum) >= limit) {
                heartAway.remove(contract.heart);
                failContract(server, contract.heart, Failure.ABANDONED);
            }
        }
    }

    /** 这份契约的首领现在加载着、在实体 tick 范围里（只查一次实体表，不加载区块） */
    private static boolean heartTicking(MinecraftServer server, Contract contract) {
        ServerLevel level = server.getLevel(contract.dimension);
        if (level == null) {
            return false;
        }
        Entity entity = level.getEntity(contract.heart);
        return entity instanceof CalamityHeartEntity heart && !heart.isRemoved() && level.isPositionEntityTicking(heart.blockPosition());
    }

    /** 只给 GameTest：这只首领已经连着不在多少刻 */
    public int heartAwayTicks(UUID heart) {
        return heartAway.getOrDefault(heart, 0);
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    public static CalamityHeartContracts load(CompoundTag tag) {
        CalamityHeartContracts data = new CalamityHeartContracts();
        for (Tag entry : tag.getList("contracts", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            ResourceLocation dim = ResourceLocation.tryParse(row.getString("dimension"));
            if (!row.hasUUID("heart") || !row.hasUUID("summoner") || dim == null) {
                continue;
            }
            Contract contract = new Contract(row.getUUID("heart"), row.getUUID("summoner"), ResourceKey.create(Registries.DIMENSION, dim),
                    NbtUtils.readBlockPos(row.getCompound("point")), ItemStack.of(row.getCompound("beacon")), row.getInt("cores"));
            data.contracts.put(contract.heart, contract);
        }
        for (Tag entry : tag.getList("pending", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            if (!row.hasUUID("heart") || !row.hasUUID("player")) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            for (Tag item : row.getList("items", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.of((CompoundTag) item);
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
            data.pendingDeliveries.add(new PendingDelivery(row.getUUID("heart"), row.getUUID("player"), items,
                    row.contains("day", Tag.TAG_LONG) ? row.getLong("day") : -1L));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", VERSION);
        ListTag list = new ListTag();
        for (Contract contract : contracts.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("heart", contract.heart);
            row.putUUID("summoner", contract.summoner);
            row.putString("dimension", contract.dimension.location().toString());
            row.put("point", NbtUtils.writeBlockPos(contract.point));
            row.put("beacon", contract.beacon.save(new CompoundTag()));
            row.putInt("cores", contract.cores);
            list.add(row);
        }
        tag.put("contracts", list);
        ListTag pending = new ListTag();
        for (PendingDelivery entry : pendingDeliveries) {
            CompoundTag row = new CompoundTag();
            row.putUUID("heart", entry.heart);
            row.putUUID("player", entry.summoner);
            ListTag items = new ListTag();
            for (ItemStack stack : entry.items) {
                items.add(stack.save(new CompoundTag()));
            }
            row.put("items", items);
            if (entry.day >= 0L) {
                row.putLong("day", entry.day);
            }
            pending.add(row);
        }
        tag.put("pending", pending);
        return tag;
    }

    // ------------------------------------------------------------------
    // 事件（Forge 总线；类名带模块前缀，见 tools/check_event_handlers.py）
    // ------------------------------------------------------------------

    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class CalamityHeartContractEvents {

        private static int clock;

        private CalamityHeartContractEvents() {
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void calamityHeartSummonerDeath(LivingDeathEvent event) {
            if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
                return;
            }
            failAllOf(player, Failure.SUMMONER_DIED);
        }

        /** 下线：这个事件发在玩家存盘之前，退的东西留在他存档里、跟着玩家文件一起落盘，下次上线给 */
        @SubscribeEvent
        public static void calamityHeartSummonerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                failAllOf(player, Failure.SUMMONER_OFFLINE);
            }
        }

        @SubscribeEvent
        public static void calamityHeartSummonerDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                failAllOf(player, Failure.SUMMONER_DIMENSION);
            }
        }

        @SubscribeEvent
        public static void calamityHeartLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                deliver(player);
            }
        }

        @SubscribeEvent
        public static void calamityHeartRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                deliver(player);
            }
        }

        @SubscribeEvent
        public static void calamityHeartServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            clock++;
            if (clock % CHECK_INTERVAL != 0) {
                return;
            }
            MinecraftServer server = event.getServer();
            if (server != null) {
                get(server).checkSummoners(server);
            }
        }

        private static void failAllOf(ServerPlayer player, Failure reason) {
            CalamityHeartContracts data = get(player.serverLevel());
            for (Contract contract : data.contractsOf(player.getUUID())) {
                data.failContract(player.server, contract.heart, reason);
            }
        }
    }
}
