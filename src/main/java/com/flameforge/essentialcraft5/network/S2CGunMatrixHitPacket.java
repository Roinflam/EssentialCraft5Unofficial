package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.PacketDistributor;

/**
 * 服务端 → 被打的人和看得见他的人：射线刚给这名玩家加了矩阵损伤，画一次「矩阵受损」特效（ECGuns，GUNS-4 加的损坏特效）。
 * 客户端 {@code client/GunsClient.onMatrixHitPacket} 交给 {@code client/render/MatrixHitEffects}：身上窜品红 / 紫色电弧、冒粒子、放音效，
 * 被打的人自己的屏幕边缘泛一圈品红。发之前已经在服务端限过频（{@code gun/GunMatrixFx}），不是每发都发。
 * <p>
 * 格式：实体 id 一个 VarInt + 强度一个字节（1 轻、2 中、3 重，按枪的伤害分档；解码时夹到 1~3）。
 *
 * @param entity    被打的玩家的实体 id（客户端找不到这个实体就什么都不做）
 * @param intensity 强度 1~3
 */
public record S2CGunMatrixHitPacket(int entity, int intensity) {

    public static final int MIN_INTENSITY = 1;
    public static final int MAX_INTENSITY = 3;

    public static void encode(S2CGunMatrixHitPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entity);
        buf.writeByte(Mth.clamp(packet.intensity, MIN_INTENSITY, MAX_INTENSITY));
    }

    public static S2CGunMatrixHitPacket decode(FriendlyByteBuf buf) {
        int entity = buf.readVarInt();
        return new S2CGunMatrixHitPacket(entity, Mth.clamp(buf.readByte(), MIN_INTENSITY, MAX_INTENSITY));
    }

    /** 发给这名玩家自己和所有正在追踪他的玩家（看得见他的人） */
    public static void sendAround(ServerPlayer target, int intensity) {
        ECNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target), new S2CGunMatrixHitPacket(target.getId(), intensity));
    }
}
