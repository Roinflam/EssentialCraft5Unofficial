package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.client.ClientPacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端 → 本人：整份玩家数据。 */
public record S2CPlayerDataPacket(CompoundTag data) {

    public static void encode(S2CPlayerDataPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.data);
    }

    public static S2CPlayerDataPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new S2CPlayerDataPacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(S2CPlayerDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        // 客户端类只在 lambda 执行时才加载，专用服不会碰到
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePlayerData(packet.data));
    }
}
