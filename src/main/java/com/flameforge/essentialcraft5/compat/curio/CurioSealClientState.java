package com.flameforge.essentialcraft5.compat.curio;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.event.CurioSealEvent;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.network.S2CCurioSealPacket;
import com.flameforge.essentialcraft5.network.S2CCurioSealSlotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端记的压制状态：哪些玩家（按实体 id）正被饰品封禁场压制，本人的来源名。遮罩视图、查询过滤在客户端也要用
 * （客户端的 curioTick、按键技能先在客户端查一遍），格子禁用图标、提示也读这里。
 * <p>
 * 数据部分是普通 Java（专用服加载 {@link CurioSeal} 时会碰到这个类，但读到的永远是空表）；收包和清理在 {@link Client} 里，只在客户端加载。
 * 集合写时复制（客户端主线程写），读不加锁。
 */
public final class CurioSealClientState {

    private static volatile Set<Integer> sealedIds = Set.of();
    private static volatile List<String> localSources = List.of();
    private static volatile int localMore;
    /** 展示厅 / 截图覆盖：本客户端画面当成被压制（不发包、不影响服务端） */
    private static volatile boolean showroomSealed;
    private static volatile List<String> showroomSources = List.of();

    private CurioSealClientState() {
    }

    /** 没有任何人被记成被压制（热路径先看这个） */
    public static boolean isEmpty() {
        return sealedIds.isEmpty() && !showroomSealed;
    }

    /** 这个实体 id 的玩家在本客户端看来正被压制 */
    public static boolean isSealed(int entityId) {
        return sealedIds.contains(entityId) || (showroomSealed && isLocalPlayerId(entityId));
    }

    /** 本人的来源名（至多 3 个），没被压制时为空 */
    public static List<String> localSources() {
        return showroomSealed ? showroomSources : localSources;
    }

    /** 本人没列名字的来源数 */
    public static int localMoreSources() {
        return showroomSealed ? 0 : localMore;
    }

    /**
     * 展示厅 / 截图用：本客户端把本人画成被压制（{@code sealed = false} 撤销）。只改本客户端的状态，不发包。
     */
    public static void setShowroomSealed(boolean sealed, List<String> sources) {
        showroomSealed = sealed;
        showroomSources = List.copyOf(sources);
    }

    private static boolean isLocalPlayerId(int entityId) {
        return Client.localPlayerId() == entityId;
    }

    private static void set(int entityId, boolean sealed) {
        Set<Integer> current = sealedIds;
        if (current.contains(entityId) == sealed) {
            return;
        }
        Set<Integer> next = new HashSet<>(current);
        if (sealed) {
            next.add(entityId);
        } else {
            next.remove(entityId);
        }
        sealedIds = Set.copyOf(next);
    }

    private static void clear() {
        sealedIds = Set.of();
        localSources = List.of();
        localMore = 0;
    }

    /** 只在客户端加载的部分：收包、退出世界清空、实体离开世界删掉它那条 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
    public static final class Client {

        private Client() {
        }

        static int localPlayerId() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player == null ? Integer.MIN_VALUE : minecraft.player.getId();
        }

        /** 压制状态包（ECComputer 登记，只在客户端主线程调） */
        public static void onPacket(S2CCurioSealPacket packet) {
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            boolean was = sealedIds.contains(packet.entityId());
            set(packet.entityId(), packet.sealed());
            boolean local = minecraft.player != null && minecraft.player.getId() == packet.entityId();
            if (local) {
                localSources = packet.sealed() ? packet.sources() : List.of();
                localMore = packet.sealed() ? packet.moreSources() : 0;
            }
            Entity entity = level == null ? null : level.getEntity(packet.entityId());
            if (entity instanceof Player player) {
                CuriosCompat.invalidate(player);
                if (was && !packet.sealed()) {
                    // 同服务端：解除时清掉按格缓存的 ICurio（压制期间换过的东西，见 CurioSlotCaches）
                    CurioSlotCaches.resetAll(player);
                }
                if (was != packet.sealed()) {
                    MinecraftForge.EVENT_BUS.post(new CurioSealEvent(player, packet.sealed(), List.of(),
                            local ? packet.sources() : List.of(), local ? packet.moreSources() : 0));
                }
            }
        }

        /**
         * 压制期间的单格同步包（ECComputer 登记，只在客户端主线程调）：把那名玩家本地饰品栏的那一格写成真物品，
         * 写法和 Curios 自己的单格包一样，只用公开接口
         */
        public static void onSlotPacket(S2CCurioSealSlotPacket packet) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || !(level.getEntity(packet.entityId()) instanceof LivingEntity entity)) {
                return;
            }
            CuriosApi.getCuriosInventory(entity).ifPresent(inventory -> inventory.getStacksHandler(packet.curioId()).ifPresent(handler -> {
                IDynamicStackHandler stacks = handler.getStacks();
                if (packet.slot() >= 0 && packet.slot() < stacks.getSlots()) {
                    stacks.setStackInSlot(packet.slot(), packet.stack());
                }
            }));
            if (entity instanceof Player player) {
                CuriosCompat.invalidate(player);
            }
        }

        @SubscribeEvent
        public static void onCurioSealLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            clear();
        }

        @SubscribeEvent
        public static void onCurioSealLevelUnload(LevelEvent.Unload event) {
            if (event.getLevel().isClientSide()) {
                // 换维度、退出世界：服务端在开始追踪 / 重新压制时会再发，本地记的全部作废
                clear();
            }
        }

        @SubscribeEvent
        public static void onCurioSealEntityLeave(EntityLeaveLevelEvent event) {
            if (event.getLevel().isClientSide() && event.getEntity() instanceof Player player) {
                set(player.getId(), false);
            }
        }
    }
}
