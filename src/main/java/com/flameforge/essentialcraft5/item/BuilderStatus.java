package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CBuilderHoldingsPacket;
import com.flameforge.essentialcraft5.network.S2CBuilderStatusPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 创世神之杖的状态包（{@link S2CBuilderStatusPacket}）：每 {@value #INTERVAL} 刻给拿着魔杖或有任务的玩家算一次，和上次发的不一样才发。
 * 材料按 {@link BuilderJobs#materialItems} 那几种合计数（混铺时表里几种加起来；背包和副手、随身容器分开），MRU 是魔杖自带的加身上储存物品和矩阵的。只在服务端线程上用。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class BuilderStatus {

    public static final int INTERVAL = 10;

    /** 上次发给每人的 */
    private static final Map<UUID, S2CBuilderStatusPacket> SENT = new HashMap<>();
    /** 上次发给每人的混铺每种持有数（没发过、或上次发的是空表时没有这一项） */
    private static final Map<UUID, List<Holding>> SENT_HOLDINGS = new HashMap<>();

    private BuilderStatus() {
    }

    @SubscribeEvent
    public static void onBuilderStatusTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<Holding> holdings = new ArrayList<>(4);
            S2CBuilderStatusPacket packet = compute(player, now, holdings);
            UUID id = player.getUUID();
            if (packet == null) {
                SENT.remove(id);
                sendHoldings(player, List.of());
                continue;
            }
            if (!packet.equals(SENT.get(id))) {
                SENT.put(id, packet);
                ECNetwork.sendTo(player, packet);
            }
            sendHoldings(player, holdings);
        }
    }

    /** 混铺时一种料持有多少（背包、副手和随身容器合计） */
    public record Holding(Item item, long count) {
    }

    /** 混铺每种的持有数变了才发；混铺不生效时发一次空表（发过非空的才发） */
    private static void sendHoldings(ServerPlayer player, List<Holding> holdings) {
        UUID id = player.getUUID();
        List<Holding> last = SENT_HOLDINGS.get(id);
        if (holdings.equals(last) || holdings.isEmpty() && last == null) {
            return;
        }
        if (holdings.isEmpty()) {
            SENT_HOLDINGS.remove(id);
        } else {
            SENT_HOLDINGS.put(id, List.copyOf(holdings));
        }
        List<ResourceLocation> items = new ArrayList<>(holdings.size());
        List<Long> counts = new ArrayList<>(holdings.size());
        for (Holding holding : holdings) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(holding.item());
            if (key != null) {
                items.add(key);
                counts.add(holding.count());
            }
        }
        ECNetwork.sendTo(player, new S2CBuilderHoldingsPacket(items, counts));
    }

    /** 这名玩家现在的状态；没拿魔杖、也没有任务返回 null（不发） */
    @Nullable
    public static S2CBuilderStatusPacket compute(ServerPlayer player, long now) {
        return compute(player, now, null);
    }

    /** 同上；混铺生效时把每种的持有数（按表的顺序）填进 holdings（给缺料红字按每种判） */
    @Nullable
    public static S2CBuilderStatusPacket compute(ServerPlayer player, long now, @Nullable List<Holding> holdings) {
        InteractionHand hand = BuilderMode.of(player.getMainHandItem()) != null ? InteractionHand.MAIN_HAND
                : BuilderMode.of(player.getOffhandItem()) != null ? InteractionHand.OFF_HAND : null;
        BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(player.getUUID(), now);
        if (hand == null && snapshot == null) {
            return null;
        }
        long have = 0;
        long inContainers = 0;
        long mru = 0;
        if (hand != null) {
            ItemStack wand = player.getItemInHand(hand);
            BuilderMode mode = BuilderMode.of(wand);
            Item[] items = mode == null ? new Item[0] : BuilderJobs.materialItems(player, hand, wand, mode);
            boolean mixed = holdings != null && BuilderMix.forWand(wand, mode) != null;
            for (Item item : items) {
                long[] counts = BuilderMaterials.count(player, item, wand);
                have += counts[0];
                inContainers += counts[1];
                if (mixed) {
                    holdings.add(new Holding(item, counts[0] + counts[1]));
                }
            }
            long available = player.getAbilities().instabuild ? Long.MAX_VALUE : BuilderJobs.availableMru(player, wand);
            mru = available == Long.MAX_VALUE ? -1 : available;
        }
        int state = S2CBuilderStatusPacket.IDLE;
        int done = 0;
        int total = 0;
        int ahead = 0;
        int pause = 0;
        if (snapshot != null) {
            state = snapshot.surveying() ? S2CBuilderStatusPacket.SURVEYING
                    : snapshot.queued() ? S2CBuilderStatusPacket.QUEUED
                    : snapshot.paused() ? (snapshot.pausedForMru() ? S2CBuilderStatusPacket.PAUSED_MRU : S2CBuilderStatusPacket.PAUSED)
                    : snapshot.kind() == BuilderJobs.Kind.UNDO ? S2CBuilderStatusPacket.UNDOING : S2CBuilderStatusPacket.RUNNING;
            done = snapshot.done();
            total = snapshot.total();
            ahead = snapshot.queueAhead();
            pause = snapshot.pauseTicksLeft();
        }
        return new S2CBuilderStatusPacket(state, have, inContainers, mru, done, total, ahead, pause,
                Math.min(255, BuilderUndo.count(player.getUUID())));
    }

    static void forget(UUID player) {
        SENT.remove(player);
        SENT_HOLDINGS.remove(player);
    }

    static void clearAll() {
        SENT.clear();
        SENT_HOLDINGS.clear();
    }
}
