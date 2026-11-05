package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * 服务端 → 使用者本人：地脉探测仪扫一次（第十二批，batch12.md §4.3.3）。服务端扣完 MRU、上完冷却才发；客户端收到后以自己所在格为中心
 * 分刻扫客户端已有的方块（{@code client/ley/LeyScanClient}），服务端不扫。
 * <p>
 * 格式：三个 varint + 1 字节。数值是服务端配置，解码时夹到配置的范围内（坏包也只能让客户端扫一个 32 格的立方体）。
 *
 * @param radius        扫描半径（格）
 * @param displayTicks  轮廓画多久（刻）
 * @param maxHighlights 最多画几格
 * @param valuableOnly  只画贵重矿
 */
public record S2CLeyScanPacket(int radius, int displayTicks, int maxHighlights, boolean valuableOnly) {

    public S2CLeyScanPacket {
        radius = Mth.clamp(radius, 1, 32);
        displayTicks = Mth.clamp(displayTicks, 1, 6000);
        maxHighlights = Mth.clamp(maxHighlights, 1, 1024);
    }

    public static void encode(S2CLeyScanPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.radius);
        buf.writeVarInt(packet.displayTicks);
        buf.writeVarInt(packet.maxHighlights);
        buf.writeBoolean(packet.valuableOnly);
    }

    public static S2CLeyScanPacket decode(FriendlyByteBuf buf) {
        return new S2CLeyScanPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }
}
