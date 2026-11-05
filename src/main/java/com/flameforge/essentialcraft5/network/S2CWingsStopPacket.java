package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 本人：魔力之翼停下（没戴翅膀、MRU 用完、登录 / 重生 / 换维度重置状态）。客户端收到后在玩家松开跳跃之前不再往上飞，
 * 见 {@link WingsFlight#clientTick}。不带数据。
 */
public record S2CWingsStopPacket() {

    public static void encode(S2CWingsStopPacket packet, FriendlyByteBuf buf) {
    }

    public static S2CWingsStopPacket decode(FriendlyByteBuf buf) {
        return new S2CWingsStopPacket();
    }

    /** 只改 {@link WingsFlight} 的静态字段，不碰客户端类，所以不用 DistExecutor */
    public static void handle(S2CWingsStopPacket packet, Supplier<NetworkEvent.Context> ctx) {
        WingsFlight.onStopReceived();
    }
}
