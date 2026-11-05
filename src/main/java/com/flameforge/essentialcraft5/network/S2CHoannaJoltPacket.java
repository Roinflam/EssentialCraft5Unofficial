package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.common.util.FakePlayer;

/**
 * 服务端 → 本人：地震里这一下是强震（第六批 6a 框架 F6a，D7；batch6a.md §3.4、§5.4）。
 * 强震由服务端决定（M4：每 20 tick 以 15% 概率、没戴 μ 指环时推一把并发这个包，平均约 6.7 秒一次），
 * 客户端收到后把一次镜头冲击排进 {@code client/hoanna/HoannaClientEvents} 的队列，M3 的 {@code HoannaEventVisuals} 取出来画（只动画面、不动准星）。
 * <p>
 * 格式（1 字节）：强度百分比 0~100（{@link #FULL} = 满幅：roll +1.0°、pitch +0.6°，再乘淡入值、「屏幕扭曲效果」和客户端配置 camera_shake）。
 *
 * @param strength 0~100，超出的夹住
 */
public record S2CHoannaJoltPacket(int strength) {

    public static final int FULL = 100;

    public S2CHoannaJoltPacket {
        strength = Mth.clamp(strength, 0, FULL);
    }

    /** 0~1 */
    public float scale() {
        return strength / (float) FULL;
    }

    public static void encode(S2CHoannaJoltPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.strength);
    }

    public static S2CHoannaJoltPacket decode(FriendlyByteBuf buf) {
        return new S2CHoannaJoltPacket(buf.readUnsignedByte());
    }

    /** 只发给这个人（假玩家不发，见 {@link S2CHoannaEventPacket#send}） */
    public static void send(ServerPlayer player, int strength) {
        if (!(player instanceof FakePlayer)) {
            ECNetwork.sendTo(player, new S2CHoannaJoltPacket(strength));
        }
    }
}
