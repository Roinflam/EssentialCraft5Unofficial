package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CMatrixCodexPacket;
import com.flameforge.essentialcraft5.network.S2CPlayerDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家数据的挂载、复制与同步。
 * <p>
 * 同步只发给玩家本人：登录、重生、换维度时整份发一次，之后数据有变化（{@link IPlayerData#markDirty()}）
 * 最多每 {@value #SYNC_INTERVAL} tick 发一次（矩阵换能器每 tick 扣 UBMRU，不限流就是每 tick 一个包）。
 * 原作是每 20 tick 无条件发一次（包 0），还能请求别人的数据（包 1），都去掉了。
 * <p>
 * 第十三批 13a（batch13.md §3.1.5）：普通同步包发 {@link IPlayerData#serializeForSync()}（不带两份图谱）；
 * 图谱在登录、重生、换维度时另发一份整的（{@link S2CMatrixCodexPacket#full}），平时新记一种由矩阵框架只发那一条。
 * 试炼冷却、过载屏障冷却在这里每刻减（{@link IPlayerData#tickCooldowns()}），走完时标记同步一次。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class PlayerDataEvents {

    private static final ResourceLocation ID = EssentialCraft5.id("player_data");
    /** 数据有变化时的同步间隔（tick）；登录、重生、换维度不受限，立即发 */
    private static final int SYNC_INTERVAL = 10;

    private PlayerDataEvents() {
    }

    @Nullable
    public static IPlayerData get(Player player) {
        return player.getCapability(ECCapabilities.PLAYER_DATA).orElse(null);
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            PlayerDataProvider provider = new PlayerDataProvider();
            event.addCapability(ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    /**
     * 死亡重生、从末地回来都会新建玩家对象：整份复制（原作按 UUID 单独存，死亡不丢）。
     * 整份 NBT 里包括腐化效果列表和存档版本号（第三批），所以腐化效果死了也不会掉，D14 迁移也不会因为复制再跑一次。
     * 矩阵后遗症（corruption/Afflictions）是例外：死了就散，从末地回来照留。
     * 第十三批的矩阵容量同样死了清零（batch13.md §3.1.6）；阶、图谱、契约之誉、冷却、试炼储备、同调预算照整份复制保留，
     * 13c 的击杀台账和首领首杀也保留（死一次不该把按人递减的计数清零）。
     */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        IPlayerData from = get(original);
        IPlayerData to = get(event.getEntity());
        if (from != null && to != null) {
            to.deserializeNBT(from.serializeNBT());
            if (event.isWasDeath()) {
                to.clearAfflictions();
                to.setMatrixPool(0, true);
            }
            to.markDirty();
        }
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        // D14：读档时清掉的第一、二批矩阵损伤（见 PlayerData 类注释），写一行日志给服主对账
        if (!player.level().isClientSide && get(player) instanceof PlayerData data) {
            int cleared = data.takeMigratedMatrixDamage();
            if (cleared > 0) {
                EssentialCraft5.LOGGER.info("[EC5] 玩家数据升级到版本 {}：清零 {} 在第一、二批攒下的矩阵损伤 {}（D14）",
                        PlayerData.DATA_VERSION, player.getGameProfile().getName(), cleared);
            }
        }
        syncWithCodex(player);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        syncWithCodex(event.getEntity());
    }

    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncWithCodex(event.getEntity());
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            IPlayerData data = get(event.player);
            if (data == null) {
                return;
            }
            if (data.tickCooldowns()) {
                data.markDirty();
            }
            if (data.isDirty() && event.player.tickCount % SYNC_INTERVAL == 0) {
                sync(event.player);
            }
        }
    }

    private static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            IPlayerData data = get(serverPlayer);
            if (data != null) {
                data.clearDirty();
                ECNetwork.sendTo(serverPlayer, new S2CPlayerDataPacket(data.serializeForSync()));
            }
        }
    }

    /** 登录、重生、换维度：普通同步之外再发一份整的图谱（客户端那边是新的玩家对象，图谱是空的） */
    private static void syncWithCodex(Player player) {
        sync(player);
        if (player instanceof ServerPlayer serverPlayer) {
            IPlayerData data = get(serverPlayer);
            if (data != null) {
                ECNetwork.sendTo(serverPlayer, S2CMatrixCodexPacket.full(data));
            }
        }
    }
}
