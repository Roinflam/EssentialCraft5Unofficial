package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 宅男套装的两个按键（客户端 → 服务端，ECComputer 登记）：只带一个字节的意图，{@link #NIGHT_VISION} 切换头盔夜视、{@link #DASH} 全套喷射推进。
 * 戴没戴头盔、是不是全套、冷却、扣不扣得起 MRU 都由服务端自己查（item/ComputerArmorEvents#handlePacket），不信客户端的任何状态。
 * 原作夜视和冲刺都只在客户端算（夜视是本地的永久药水效果，冲刺直接改本地速度），改过的客户端能无冷却连冲。
 */
public record C2SComputerArmorPacket(byte action) {

    /** 切换头盔夜视 */
    public static final byte NIGHT_VISION = 0;
    /** 全套喷射推进 */
    public static final byte DASH = 1;

    public static void encode(C2SComputerArmorPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.action);
    }

    public static C2SComputerArmorPacket decode(FriendlyByteBuf buf) {
        return new C2SComputerArmorPacket(buf.readByte());
    }
}
