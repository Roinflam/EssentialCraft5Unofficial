package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * 服务端 → 周围的人：画一道枪械射线的光束（第五批框架 F5-5）。原作每发生成一个会存盘的射线实体（{@code EntityMRURay}），
 * 这里不生成实体，只发这个包，客户端 {@code client/GunsClient.onBeamPacket} 按射手是不是自己定光束的画法，交给 {@code client/render/BeamEffects} 画一道 60 tick 的光束（ECGuns 登记处理器）。
 * <p>
 * 格式（41 字节）：射手的实体 id 一个 int（射手自己的客户端从枪口画，不从镜头位置画，审查 GUNS-3）+ 起点三个 double（眼睛位置，远离原点也不丢精度）
 * + 终点相对起点的偏移三个 float（射线最长 64 格，float 足够）+ balance 一个字节（0 中性、1 混沌、2 冰冻、3 魔力、4 暗影，见 {@link com.flameforge.essentialcraft5.gun.GunStats}）。
 * 解码时 balance 夹到 0~4，偏移不是有限数的当作零长度（客户端照样收，不画）。
 *
 * @param shooter 射手的实体 id（只用来认「是不是我打的」，客户端找不到这个实体也照样画）
 */
public record S2CGunBeamPacket(int shooter, Vec3 from, Vec3 to, int balance) {

    /** 发给光束起点多少格以内的玩家（射程也是 64，射线另一端的人照样在范围里） */
    public static final double SEND_RANGE = 64D;
    /** balance 的上限（暗影） */
    public static final int MAX_BALANCE = 4;

    public static void encode(S2CGunBeamPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.shooter);
        buf.writeDouble(packet.from.x);
        buf.writeDouble(packet.from.y);
        buf.writeDouble(packet.from.z);
        buf.writeFloat((float) (packet.to.x - packet.from.x));
        buf.writeFloat((float) (packet.to.y - packet.from.y));
        buf.writeFloat((float) (packet.to.z - packet.from.z));
        buf.writeByte(Mth.clamp(packet.balance, 0, MAX_BALANCE));
    }

    public static S2CGunBeamPacket decode(FriendlyByteBuf buf) {
        int shooter = buf.readInt();
        Vec3 from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float dx = buf.readFloat();
        float dy = buf.readFloat();
        float dz = buf.readFloat();
        int balance = Mth.clamp(buf.readByte(), 0, MAX_BALANCE);
        if (!Float.isFinite(dx) || !Float.isFinite(dy) || !Float.isFinite(dz)) {
            return new S2CGunBeamPacket(shooter, from, from, balance);
        }
        return new S2CGunBeamPacket(shooter, from, from.add(dx, dy, dz), balance);
    }

    /** 发给起点 {@link #SEND_RANGE} 格内、同一维度的所有玩家（含射手自己） */
    public static void sendAround(ServerLevel level, int shooter, Vec3 from, Vec3 to, int balance) {
        ECNetwork.CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(from.x, from.y, from.z, SEND_RANGE, level.dimension())),
                new S2CGunBeamPacket(shooter, from, to, balance));
    }
}
