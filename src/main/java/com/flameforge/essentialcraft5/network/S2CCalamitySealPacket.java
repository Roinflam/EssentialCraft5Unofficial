package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * 天灾封印的状态（{@code entity/boss/seal/CalamitySeal}，S2C，只发给本人）：低 4 位 = 封着哪几处，{@link #ENGAGED} = 人在一场天灾之心里
 * （封印全解了也在打时只有这一位，客户端照样显示四个已解的图标）。封着时每秒重发一次，客户端 3 秒收不到就当解开；
 * 不挂在首领实体上（实体不在客户端的追踪范围里时「解开」也要送得到）。ECCalamityHeart 登记成模块包。
 */
public record S2CCalamitySealPacket(int state) {

    /** 人在战斗里 */
    public static final int ENGAGED = 16;

    public S2CCalamitySealPacket {
        state = state & 0x1F;
    }

    public static void encode(S2CCalamitySealPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.state);
    }

    public static S2CCalamitySealPacket decode(FriendlyByteBuf buf) {
        return new S2CCalamitySealPacket(buf.readUnsignedByte());
    }

    /** 封着哪几处 */
    public int mask() {
        return state & 15;
    }

    public boolean engaged() {
        return (state & ENGAGED) != 0;
    }

    public static void send(ServerPlayer player, int state) {
        if (!(player instanceof FakePlayer)) {
            ECNetwork.sendTo(player, new S2CCalamitySealPacket(state));
        }
    }
}
