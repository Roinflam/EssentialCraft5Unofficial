package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：魔力之翼「开始 / 停止往上飞」（第三批框架 G5）。只带一个布尔。
 * <p>
 * 原作 ClientProxy.wingsAction 发的是「坐标 + 玩家 UUID」字符串，服务端直接 setPosition（伪造就能传送任何人，
 * 只是收发的字符串不一致才从没生效，policy §3.3）。这里不带坐标、不带 UUID：认人用 {@code ctx.getSender()}，
 * 移动由客户端本地加速度（玩家移动本来就是客户端说了算），服务端只记「开着」并按 tick 扣 MRU，处理见 {@link WingsFlight}。
 */
public record C2SWingsPacket(boolean active) {

    public static void encode(C2SWingsPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
    }

    public static C2SWingsPacket decode(FriendlyByteBuf buf) {
        return new C2SWingsPacket(buf.readBoolean());
    }

    public static void handle(C2SWingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) {
            WingsFlight.handleToggle(sender, packet.active);
        }
    }
}
