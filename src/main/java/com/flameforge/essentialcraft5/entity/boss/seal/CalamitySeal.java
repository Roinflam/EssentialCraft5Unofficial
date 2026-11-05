package com.flameforge.essentialcraft5.entity.boss.seal;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.network.S2CCalamitySealPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 天灾封印（tools/plan/calamity_heart_v2.md §1）：天灾之心战斗期间，参战者身上的附魔按物品所在的位置分四处失效，一处由一根灾柱压着。
 *
 * <h2>四处（位 = 灾柱那一相的序号）</h2>
 * {@link #HEAD_CHEST}（日阵）头盔、胸甲；{@link #OTHER}（毒雾）副手、饰品栏、背包里其余物品；{@link #MAIN_HAND}（黑暗）主手当前那格；
 * {@link #LEGS_FEET}（地震）护腿、靴子。只看物品此刻在哪一格：换到主手的那件就按主手算。
 *
 * <h2>状态不落地</h2>
 * 不写物品 NBT、不存盘。服务端只在内存里记「谁被哪只首领封了哪几处」：首领每刻给名单里的人续一次租约（到期 = 现在 + {@value #LEASE_TICKS} 刻），
 * 每刻末结算，没续上的就解开；打倒、散去、离开名单另外当场调 {@link #releaseBoss} / {@link #release}。所以首领不再 tick（区块卸载、模组删了它）
 * 最多 2 秒也会解开，重启、崩服、回档都不会留下被封的物品。时钟是 {@code server.getTickCount()}（不受 /time 影响）。
 *
 * <h2>认物品</h2>
 * 每刻末给被封的人重建一张「物品对象 → 哪一处」的表（身上 6 格、背包 36 格、饰品栏，按对象比），钩子只查这张表：掉在地上的、放进箱子的、
 * 别人手里的一律不受影响。客户端另有一张本地玩家自己的表（{@code client/boss/CalamitySealClient} 按服务端发来的掩码建），两张表分开放，
 * 单人游戏里两边的物品是不同的对象，互不干扰。
 *
 * <h2>钩子（{@code mixin/seal/}）</h2>
 * <ul>
 *   <li>效果路：{@link #level}（{@code ItemStack.getEnchantmentLevel}、{@code EnchantmentHelper.getTagEnchantmentLevel}）、
 *       {@link #filterEffects}（{@code ItemStack.getAllEnchantments}，原版 {@code runIterationOnItem} 走它）——封着就当没有；</li>
 *   <li>读表路：{@link #filterTable}（{@code EnchantmentHelper.getEnchantments}）——同样去掉，但本人开着自己背包以外的界面时不去
 *       （铁砧、砂轮、附魔台、物品编辑器都是「读出来改完写回去」）；</li>
 *   <li>写表路：{@link #restoreHidden}（{@code EnchantmentHelper.setEnchantments}）——写到被封的物品上时，把调用方看不到的那几条原样补回去。</li>
 * </ul>
 * 永远不封：诅咒（{@link Enchantment#isCurse()}）、标签 {@link #EXEMPT} 里的、附魔书；物品主人死了 / 正在死、创造、旁观时不封。
 * 没人被封时钩子只读两个 volatile 引用就返回。只在服务端主线程写服务端那张表、客户端主线程写客户端那张；表发布后不再改，换新表时整个替换。
 */
public final class CalamitySeal {

    /** 头盔、胸甲（日阵灾柱） */
    public static final int HEAD_CHEST = 1;
    /** 副手、饰品栏、背包里其余物品（毒雾灾柱） */
    public static final int OTHER = 2;
    /** 主手当前那格（黑暗灾柱） */
    public static final int MAIN_HAND = 4;
    /** 护腿、靴子（地震灾柱） */
    public static final int LEGS_FEET = 8;
    /** 四处都封 */
    public static final int ALL = 15;
    /** 租约多少刻（首领每刻续） */
    public static final int LEASE_TICKS = 40;
    /** 封着时每多少刻给本人重发一次状态（客户端 3 秒收不到就当解开） */
    public static final int HEARTBEAT_TICKS = 20;

    /** 不封的附魔（默认空，整合包用数据包追加） */
    public static final TagKey<Enchantment> EXEMPT = TagKey.create(Registries.ENCHANTMENT, EssentialCraft5.id("calamity_seal_exempt"));

    private static final Logger LOGGER = LoggerFactory.getLogger("essentialcraft5/calamity_seal");

    /** 表里的一格：物品主人、这件在哪一处、主人的状态 */
    public record Seat(Player owner, int part, MaskSource source) {
    }

    /** 物品主人此刻被封了哪几处（两端各自实现） */
    public interface MaskSource {
        int mask();
    }

    /** 一名被封（或进了名单、等结算）的人 */
    private static final class Holder implements MaskSource {
        ServerPlayer player;
        final Map<UUID, Lease> leases = new HashMap<>();
        volatile int mask;
        boolean engaged;
        int sentState = -1;
        long lastSent;

        Holder(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public int mask() {
            return mask;
        }

        /** 按没到期的租约重算（返回是否还在任何一场里） */
        boolean recompute(long now) {
            int m = 0;
            Iterator<Lease> it = leases.values().iterator();
            while (it.hasNext()) {
                Lease lease = it.next();
                if (lease.until < now) {
                    it.remove();
                } else {
                    m |= lease.mask;
                }
            }
            mask = m;
            engaged = !leases.isEmpty();
            return engaged;
        }
    }

    private static final class Lease {
        int mask;
        long until;
    }

    /** 服务端：玩家 UUID → 状态（只在服务端主线程读写） */
    private static final Map<UUID, Holder> HOLDERS = new HashMap<>();
    private static volatile Map<ItemStack, Seat> serverTable = Map.of();
    private static volatile Map<ItemStack, Seat> clientTable = Map.of();
    private static volatile Set<Enchantment> exempt = Set.of();
    private static volatile boolean available = true;
    /** GameTest 用：不等刻末，hold 完立刻重建表 */
    private static volatile boolean eagerForTest;

    private CalamitySeal() {
    }

    // ================================================================== 首领调

    /** 这一相的灾柱压着的那一处 */
    public static int part(int aspectOrdinal) {
        return 1 << (aspectOrdinal & 3);
    }

    /** 注入点没全部到位（另一个模组改了同一处之类）时整个停用，免得只封一半 */
    public static boolean available() {
        return available;
    }

    static void setAvailable(boolean value) {
        available = value;
    }

    /**
     * 首领每刻给名单里的一个人续租约：mask = 这一刻封着哪几处（可以是 0：人在战斗里、封印全解了，客户端照样显示四个已解的图标）。
     * 假玩家不封。now 是 {@link #now}。
     */
    public static void hold(ServerPlayer player, UUID boss, int mask, long now) {
        if (player instanceof FakePlayer || !available) {
            return;
        }
        Holder holder = HOLDERS.computeIfAbsent(player.getUUID(), id -> new Holder(player));
        holder.player = player;
        Lease lease = holder.leases.computeIfAbsent(boss, id -> new Lease());
        lease.mask = mask & ALL;
        lease.until = now + LEASE_TICKS;
        if (eagerForTest) {
            settle(player.server, now);
        }
    }

    /** 一个人离开了这只首领的名单：这只给的封印当场去掉 */
    public static void release(UUID boss, UUID player) {
        Holder holder = HOLDERS.get(player);
        if (holder != null && holder.leases.remove(boss) != null) {
            refreshNow(holder);
        }
    }

    /** 这只首领的战斗结束（打倒、散去、被删、卸载）：它给所有人的封印当场去掉 */
    public static void releaseBoss(UUID boss) {
        for (Holder holder : HOLDERS.values()) {
            if (holder.leases.remove(boss) != null) {
                refreshNow(holder);
            }
        }
    }

    /** 当场按剩下的租约改掩码、发给本人，表在刻末重建（掩码先变，钩子立刻看得到） */
    private static void refreshNow(Holder holder) {
        MinecraftServer server = holder.player.server;
        long now = server == null ? 0L : now(server);
        holder.recompute(now);
        send(holder, now);
        if (eagerForTest && server != null) {
            settle(server, now);
        }
    }

    /** 服务端时钟 */
    public static long now(MinecraftServer server) {
        return server.getTickCount();
    }

    /** 这名玩家此刻（服务端）被封了哪几处 */
    public static int maskOf(Player player) {
        Holder holder = HOLDERS.get(player.getUUID());
        return holder == null || holder.player != player ? 0 : holder.mask;
    }

    /** 这名玩家在不在某一场天灾之心的封印里（含全解了、还在打的） */
    public static boolean engaged(Player player) {
        Holder holder = HOLDERS.get(player.getUUID());
        return holder != null && holder.player == player && holder.engaged;
    }

    // ================================================================== 钩子（两端）

    /** 没人被封（热路径先看这个） */
    public static boolean noOne() {
        return serverTable.isEmpty() && clientTable.isEmpty();
    }

    @Nullable
    private static Seat seat(ItemStack stack) {
        Map<ItemStack, Seat> server = serverTable;
        Seat seat = server.isEmpty() ? null : server.get(stack);
        if (seat == null) {
            Map<ItemStack, Seat> client = clientTable;
            seat = client.isEmpty() ? null : client.get(stack);
        }
        return seat;
    }

    /** 这件物品上的这个附魔此刻被封着没有；table = 读整张附魔表那条路（开着别的界面时不封） */
    public static boolean isSealed(ItemStack stack, @Nullable Enchantment enchantment, boolean table) {
        if (enchantment == null || noOne()) {
            return false;
        }
        Seat seat = seat(stack);
        if (seat == null || (seat.source().mask() & seat.part()) == 0 || enchantment.isCurse() || exempt.contains(enchantment)) {
            return false;
        }
        Player owner = seat.owner();
        if (owner.isRemoved() || owner.isDeadOrDying() || owner.isSpectator() || owner.isCreative()) {
            return false;
        }
        return !table || owner.containerMenu == owner.inventoryMenu;
    }

    /** 这件物品此刻有没有任何一处封印压着（物品提示、光泽用；不看具体附魔） */
    public static boolean isSealedStack(ItemStack stack) {
        if (noOne()) {
            return false;
        }
        Seat seat = seat(stack);
        if (seat == null || (seat.source().mask() & seat.part()) == 0) {
            return false;
        }
        Player owner = seat.owner();
        return !owner.isRemoved() && !owner.isDeadOrDying() && !owner.isSpectator() && !owner.isCreative();
    }

    /** 这件物品在哪一处（没在表里返回 0） */
    public static int partOf(ItemStack stack) {
        Seat seat = noOne() ? null : seat(stack);
        return seat == null ? 0 : seat.part();
    }

    /** 等级查询（效果路）：封着返回 0 */
    public static int level(ItemStack stack, Enchantment enchantment, int level) {
        if (level <= 0 || noOne()) {
            return level;
        }
        return isSealed(stack, enchantment, false) ? 0 : level;
    }

    /** 全部附魔（效果路）：去掉封着的；没去掉什么时原样返回 */
    public static Map<Enchantment, Integer> filterEffects(ItemStack stack, Map<Enchantment, Integer> map) {
        return filter(stack, map, false);
    }

    /** 读整张附魔表：去掉封着的（本人开着别的界面时不去） */
    public static Map<Enchantment, Integer> filterTable(ItemStack stack, Map<Enchantment, Integer> map) {
        return filter(stack, map, true);
    }

    private static Map<Enchantment, Integer> filter(ItemStack stack, Map<Enchantment, Integer> map, boolean table) {
        if (map == null || map.isEmpty() || noOne() || seat(stack) == null) {
            return map;
        }
        Map<Enchantment, Integer> out = null;
        for (Enchantment enchantment : map.keySet()) {
            if (isSealed(stack, enchantment, table)) {
                if (out == null) {
                    out = new LinkedHashMap<>(map);
                }
                out.remove(enchantment);
            }
        }
        return out == null ? map : out;
    }

    /**
     * 写回附魔表之前：被封的物品上真有、这一刻读表时会被藏起来、要写的表里又没有的附魔原样补回去
     * （调用方是「读出来、改完、写回去」，没看见的那几条不能因为封印丢掉）
     */
    public static Map<Enchantment, Integer> restoreHidden(ItemStack stack, Map<Enchantment, Integer> map) {
        if (map == null || noOne() || seat(stack) == null) {
            return map;
        }
        Map<Enchantment, Integer> real = EnchantmentHelper.deserializeEnchantments(stack.getEnchantmentTags());
        Map<Enchantment, Integer> out = null;
        for (Map.Entry<Enchantment, Integer> entry : real.entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!map.containsKey(enchantment) && isSealed(stack, enchantment, true)) {
                if (out == null) {
                    out = new LinkedHashMap<>(map);
                }
                out.put(enchantment, entry.getValue());
            }
        }
        return out == null ? map : out;
    }

    // ================================================================== 建表

    /** 按位置把一名玩家的物品放进表（客户端也用）：护甲两组、主手、其余 */
    public static void collect(Player player, MaskSource source, Map<ItemStack, Seat> into) {
        Inventory inventory = player.getInventory();
        put(into, inventory.armor.get(3), player, HEAD_CHEST, source);
        put(into, inventory.armor.get(2), player, HEAD_CHEST, source);
        put(into, inventory.armor.get(1), player, LEGS_FEET, source);
        put(into, inventory.armor.get(0), player, LEGS_FEET, source);
        for (ItemStack stack : inventory.offhand) {
            put(into, stack, player, OTHER, source);
        }
        for (int i = 0; i < inventory.items.size(); i++) {
            put(into, inventory.items.get(i), player, i == inventory.selected ? MAIN_HAND : OTHER, source);
        }
        CuriosCompat.forEachEquipped(player, stack -> put(into, stack, player, OTHER, source));
    }

    private static void put(Map<ItemStack, Seat> into, ItemStack stack, Player owner, int part, MaskSource source) {
        if (!stack.isEmpty() && !stack.is(Items.ENCHANTED_BOOK) && !into.containsKey(stack)) {
            into.put(stack, new Seat(owner, part, source));
        }
    }

    /** 客户端建好的表（client/boss/CalamitySealClient 调；空表 = 本地玩家没被封） */
    public static void publishClient(Map<ItemStack, Seat> table) {
        clientTable = table.isEmpty() ? Map.of() : table;
    }

    /** 刻末结算：过期的租约去掉、掩码变了的发给本人、重建服务端的表 */
    static void settle(MinecraftServer server, long now) {
        if (HOLDERS.isEmpty()) {
            if (!serverTable.isEmpty()) {
                serverTable = Map.of();
            }
            return;
        }
        Map<ItemStack, Seat> table = null;
        Iterator<Holder> it = HOLDERS.values().iterator();
        while (it.hasNext()) {
            Holder holder = it.next();
            boolean engaged = holder.recompute(now);
            if (holder.player.isRemoved() && !holder.player.hasDisconnected()) {
                // 重生 / 换维度以后旧对象被删：新对象还没被首领续上，先当解开
                holder.mask = 0;
            }
            send(holder, now);
            if (!engaged) {
                it.remove();
                continue;
            }
            if (holder.mask != 0) {
                if (table == null) {
                    table = new IdentityHashMap<>();
                }
                collect(holder.player, holder, table);
            }
        }
        serverTable = table == null ? Map.of() : table;
    }

    /** 状态变了、或者封着满一次心跳，发给本人（离线、假玩家不发） */
    private static void send(Holder holder, long now) {
        int state = (holder.engaged ? S2CCalamitySealPacket.ENGAGED : 0) | (holder.mask & ALL);
        boolean beat = holder.engaged && now - holder.lastSent >= HEARTBEAT_TICKS;
        if (state == holder.sentState && !beat) {
            return;
        }
        holder.sentState = state;
        holder.lastSent = now;
        ServerPlayer player = holder.player;
        if (player.connection != null && !player.hasDisconnected()) {
            S2CCalamitySealPacket.send(player, state);
        }
    }

    /** 这名玩家的记录整个去掉（下线、重生、换维度、死亡），还在线的发一次「解开」 */
    private static void drop(UUID player, boolean tell) {
        Holder holder = HOLDERS.remove(player);
        if (holder == null) {
            return;
        }
        holder.leases.clear();
        holder.mask = 0;
        holder.engaged = false;
        if (tell && holder.player.connection != null && !holder.player.hasDisconnected()) {
            S2CCalamitySealPacket.send(holder.player, 0);
        }
        Map<ItemStack, Seat> table = serverTable;
        if (!table.isEmpty()) {
            Map<ItemStack, Seat> next = new IdentityHashMap<>();
            for (Map.Entry<ItemStack, Seat> entry : table.entrySet()) {
                if (!entry.getValue().owner().getUUID().equals(player)) {
                    next.put(entry.getKey(), entry.getValue());
                }
            }
            serverTable = next.isEmpty() ? Map.of() : next;
        }
    }

    private static void refreshExempt() {
        Set<Enchantment> set = new HashSet<>();
        BuiltInRegistries.ENCHANTMENT.getTag(EXEMPT).ifPresent(tag -> tag.forEach(holder -> set.add(holder.value())));
        exempt = set.isEmpty() ? Set.of() : Set.copyOf(set);
    }

    /** 全部清掉（停服、GameTest） */
    public static void clearAll() {
        HOLDERS.clear();
        serverTable = Map.of();
        clientTable = Map.of();
    }

    /** GameTest：hold / release 后立刻重建表（不等刻末） */
    public static void setEagerForTest(boolean eager) {
        eagerForTest = eager;
    }

    /** GameTest：按服务端时钟立刻结算一次 */
    public static void settleForTest(MinecraftServer server, long now) {
        settle(server, now);
    }

    /** Forge 总线（类名、方法名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class CalamitySealEvents {

        private CalamitySealEvents() {
        }

        @SubscribeEvent
        public static void calamitySealServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && (!HOLDERS.isEmpty() || !serverTable.isEmpty())) {
                settle(event.getServer(), now(event.getServer()));
            }
        }

        @SubscribeEvent
        public static void calamitySealLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            drop(event.getEntity().getUUID(), false);
        }

        @SubscribeEvent
        public static void calamitySealRespawn(PlayerEvent.PlayerRespawnEvent event) {
            drop(event.getEntity().getUUID(), true);
        }

        @SubscribeEvent
        public static void calamitySealDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            drop(event.getEntity().getUUID(), true);
        }

        /** 死亡当场解开（掉落、不死图腾、保留物品的附魔都按没封算）；先于别的监听器 */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void calamitySealDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && HOLDERS.containsKey(player.getUUID())) {
                drop(player.getUUID(), true);
            }
        }

        @SubscribeEvent
        public static void calamitySealTags(TagsUpdatedEvent event) {
            refreshExempt();
        }

        @SubscribeEvent
        public static void calamitySealStopped(ServerStoppedEvent event) {
            HOLDERS.clear();
            serverTable = Map.of();
        }
    }

    /** 模组总线：加载完成时核对注入点（先把两个目标类加载出来，混入插件才有结果），没到位就停用 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class CalamitySealSetup {

        private CalamitySealSetup() {
        }

        @SubscribeEvent
        public static void calamitySealLoadComplete(FMLLoadCompleteEvent event) {
            LOGGER.debug("Calamity Seal hooks: {} / {}", ItemStack.class.getName(), EnchantmentHelper.class.getName());
            boolean complete = CalamitySealMixinStatus.complete();
            setAvailable(complete);
            if (!complete) {
                LOGGER.warn("Calamity Seal is disabled: the enchantment hooks did not all apply ({}).", CalamitySealMixinStatus.describe());
            }
        }
    }
}
