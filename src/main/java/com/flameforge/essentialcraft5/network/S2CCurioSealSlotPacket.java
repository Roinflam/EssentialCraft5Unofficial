package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 → 客户端：被饰品封禁场压制的玩家，某一格饰品的真物品变了（ECComputer 登记，接在压制状态包后面；协议 7）。
 * <p>
 * 压制期间 Curios 比对的是遮罩视图（被压制的格子读成空），真物品摘下、换掉它都看不见，不发自己的单格包；追踪这名玩家的人
 * 客户端里那份饰品栏就一直是旧的（渲染照画、客户端查询也查得到），要等对方重新进入追踪范围或者重登。所以由
 * {@code compat/curio/SealSlotKeeper} 每刻对账时发现某格真物品变了就发这个包，给本人和看得到他的人；客户端只用 Curios 公开接口
 * 把那一格写成包里的物品（和 Curios 自己的单格包处理一样）。
 * <p>
 * 格式：实体 id（VarInt）、槽位类型（{@code writeUtf(…, 64)}）、序号（VarInt）、物品（{@code writeItem}）。
 *
 * @param entityId 被压制的玩家的实体 id
 * @param curioId  槽位类型（ring、belt……）
 * @param slot     这一类槽位里的序号
 * @param stack    这一格现在的真物品（拷贝）
 */
public record S2CCurioSealSlotPacket(int entityId, String curioId, int slot, ItemStack stack) {

    /** 槽位类型名的长度上限（Curios 的槽位名都很短，限长防恶意包） */
    private static final int ID_MAX = 64;

    /** GameTest 用：玩家 UUID → 发给他（及旁人）的包（只记，照样发） */
    private static final Map<UUID, List<S2CCurioSealSlotPacket>> CAPTURE = new ConcurrentHashMap<>();

    public static void encode(S2CCurioSealSlotPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeUtf(packet.curioId, ID_MAX);
        buf.writeVarInt(packet.slot);
        buf.writeItem(packet.stack);
    }

    public static S2CCurioSealSlotPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        String curioId = buf.readUtf(ID_MAX);
        int slot = buf.readVarInt();
        return new S2CCurioSealSlotPacket(entityId, curioId, slot, buf.readItem());
    }

    /** 发给本人和看得到他的人：player 的 curioId 第 slot 格现在是 stack */
    public static void send(ServerPlayer player, String curioId, int slot, ItemStack stack) {
        S2CCurioSealSlotPacket packet = new S2CCurioSealSlotPacket(player.getId(), curioId, slot, stack.copy());
        List<S2CCurioSealSlotPacket> capture = CAPTURE.get(player.getUUID());
        if (capture != null) {
            capture.add(packet);
        }
        ECNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }

    /** GameTest 用：开始记发给这名玩家的包，返回收集表 */
    public static List<S2CCurioSealSlotPacket> captureForTest(UUID player) {
        List<S2CCurioSealSlotPacket> list = Collections.synchronizedList(new ArrayList<>());
        CAPTURE.put(player, list);
        return list;
    }

    /** GameTest 用：不再记 */
    public static void stopCapture(UUID player) {
        CAPTURE.remove(player);
    }
}
