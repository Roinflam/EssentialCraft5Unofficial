package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CBuilderPanelPacket;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 创世神之杖面板的清单（服务端）：面板开着时客户端隔一会儿发 {@code PANEL_SYNC}，这里按 {@link #SYNC_GAP_TICKS} 刻一次限频后回
 * {@link S2CBuilderPanelPacket}：身上的材料（{@link BuilderMaterials#stocks}，最多 {@value S2CBuilderPanelPacket#MAX_MATERIALS} 种）、
 * 撤销上一次现在能不能用和为什么、回收最近拆下过的物品。只读，不改任何东西；不看施工状态（施工中面板照样要显示撤销和进度）。
 * 限频按人记，下线清掉；超频的请求直接丢掉（客户端下一轮还会再要），不占操作包的总限频（免得挤掉玩家真点的操作）。只在服务端线程上用。
 */
public final class BuilderPanel {

    /** 同一个人两次清单之间至少隔这么多刻 */
    public static final int SYNC_GAP_TICKS = 10;

    private static final Map<UUID, Long> LAST_SYNC = new HashMap<>();

    private BuilderPanel() {
    }

    /** 收到 {@code PANEL_SYNC}：真玩家、活着、手里那只手是魔杖、没超频才回一份；返回发了没有 */
    public static boolean request(ServerPlayer player, InteractionHand hand) {
        if (!ECProtection.isRealPlayer(player) || !player.isAlive()) {
            return false;
        }
        ItemStack wand = player.getItemInHand(hand);
        if (BuilderMode.of(wand) == null) {
            return false;
        }
        long now = player.server.getTickCount();
        Long last = LAST_SYNC.get(player.getUUID());
        if (last != null && now - last < SYNC_GAP_TICKS && now >= last) {
            return false;
        }
        LAST_SYNC.put(player.getUUID(), now);
        ECNetwork.sendTo(player, compute(player, wand, now));
        return true;
    }

    /** 这名玩家现在的清单（GameTest 直接调） */
    public static S2CBuilderPanelPacket compute(ServerPlayer player, @Nullable ItemStack wand, long now) {
        UUID id = player.getUUID();
        int state;
        int shape = -1;
        int w = 0;
        int l = 0;
        int h = 0;
        int cells = 0;
        int age = 0;
        boolean clearedSince = false;
        BuilderUndo.Record record = BuilderUndo.peek(id);
        int window = ECBuilder.Config.undoWindowSeconds();
        if (window <= 0) {
            state = S2CBuilderPanelPacket.UNDO_OFF;
        } else if (BuilderJobs.undoBusy(id)) {
            state = S2CBuilderPanelPacket.UNDO_BUSY;
        } else if (record == null || record.size() == 0) {
            state = BuilderJobs.clearedSince(id, null) ? S2CBuilderPanelPacket.UNDO_CLEARED : S2CBuilderPanelPacket.UNDO_NONE;
        } else {
            BoundingBox bounds = record.bounds();
            if (record.dimension() != player.level().dimension() || now - record.createdTick() > window * 20L || bounds == null) {
                state = S2CBuilderPanelPacket.UNDO_EXPIRED;
            } else {
                int reach = wand == null || BuilderMode.of(wand) == null ? 0 : BuilderTier.stats(wand).maxReach();
                state = BuilderSelection.distanceSqToBox(player.position(), bounds) > (double) reach * reach
                        ? S2CBuilderPanelPacket.UNDO_FAR : S2CBuilderPanelPacket.UNDO_READY;
            }
            if (bounds != null) {
                w = bounds.getXSpan();
                l = bounds.getZSpan();
                h = bounds.getYSpan();
            }
            BuilderShape recorded = record.shape();
            shape = recorded == null ? -1 : recorded.ordinal();
            cells = record.size();
            age = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (now - record.createdTick()) / 20L));
            clearedSince = BuilderJobs.clearedSince(id, record);
            if (clearedSince && state == S2CBuilderPanelPacket.UNDO_READY) {
                // 上一单是清除类：「撤销上一次」变灰写原因；服务端撤销（魔杖键 + 右键、按钮）同样不撤，提示同一句（BuilderJobs.undo）
                state = S2CBuilderPanelPacket.UNDO_CLEARED;
            }
        }
        BuilderMaterials.Stocks stocks = BuilderMaterials.stocks(player, wand, S2CBuilderPanelPacket.MAX_MATERIALS);
        List<S2CBuilderPanelPacket.Entry> materials = new ArrayList<>(stocks.list().size());
        for (BuilderMaterials.Stock stock : stocks.list()) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stock.item());
            if (key != null) {
                materials.add(new S2CBuilderPanelPacket.Entry(key, stock.body(), stock.box()));
            }
        }
        List<ResourceLocation> recent = new ArrayList<>();
        for (Item item : BuilderSalvage.recent(id)) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null && recent.size() < S2CBuilderPanelPacket.MAX_RECENT) {
                recent.add(key);
            }
        }
        return new S2CBuilderPanelPacket(state, shape, w, l, h, cells, age, clearedSince, List.copyOf(materials), stocks.truncated(),
                stocks.containersSeen(), List.copyOf(recent));
    }

    static void forget(UUID player) {
        LAST_SYNC.remove(player);
    }

    static void clearAll() {
        LAST_SYNC.clear();
    }
}
