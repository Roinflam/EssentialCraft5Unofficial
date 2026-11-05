package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * 服务端 → 旁人：某名玩家是不是正踩着宅男反重力板在飞（ECComputer 登记，排在模块包末尾）。
 * 原版不把别人的 {@code abilities.flying} 发给旁人，客户端只能按落没落地估，起跳、爬梯子、缓降时都会误判；
 * 这个包只在状态变化时发给看得到这名玩家的客户端（{@link #sendToTrackers}），有人开始追踪这名玩家时再单独补发一次（{@link #sendTo}）。
 * 本人不发（客户端自己看能力）。客户端记在 client/ComputerClient 的一张实体 id 表里，画板子时只查这张表。
 * <p>
 * 格式：实体 id（VarInt）+ 在不在飞（1 字节）。
 *
 * @param entityId 玩家的实体 id
 * @param flying   正在用反重力板飞
 */
public record S2CBoardFlyingPacket(int entityId, boolean flying) {

    public static void encode(S2CBoardFlyingPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.flying);
    }

    public static S2CBoardFlyingPacket decode(FriendlyByteBuf buf) {
        return new S2CBoardFlyingPacket(buf.readVarInt(), buf.readBoolean());
    }

    /** 发给所有看得到 player 的客户端（不含本人；player 没在世界里被追踪时什么都不发） */
    public static void sendToTrackers(ServerPlayer player, boolean flying) {
        ECNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), new S2CBoardFlyingPacket(player.getId(), flying));
    }

    /** 只发给 tracker 一个人：target 现在的状态（假玩家不发） */
    public static void sendTo(ServerPlayer tracker, ServerPlayer target, boolean flying) {
        if (!(tracker instanceof FakePlayer)) {
            ECNetwork.sendTo(tracker, new S2CBoardFlyingPacket(target.getId(), flying));
        }
    }
}
