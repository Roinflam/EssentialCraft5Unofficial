package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：某名玩家的饰品封禁场压制状态（ECComputer 登记，接在反重力板飞行状态包后面；协议 7）。
 * 只在状态变化时发：给本人（带来源名，提示用）和看得到他的人（来源名留空）；有人开始追踪一名被压制的玩家时单独补发一次。
 * 本人被压制期间来源换了人，只给本人再发一次（状态不变，客户端只更新名字）。
 * 客户端记在 {@code compat/curio/CurioSealClientState}：遮罩视图、查询过滤、格子禁用图标都靠它。
 * <p>
 * 格式：实体 id（VarInt）、是否被压制（1 字节）、名字数（VarInt，≤ {@value #MAX_NAMES}）+ 名字（{@code writeUtf(…, 16)}）、
 * 余下没列名字的来源数（VarInt）。
 *
 * @param entityId    被压制的玩家的实体 id
 * @param sealed      是否被压制
 * @param sources     来源名字（至多 {@value #MAX_NAMES} 个；发给旁人时为空）
 * @param moreSources 没列名字的来源数
 */
public record S2CCurioSealPacket(int entityId, boolean sealed, List<String> sources, int moreSources) {

    /** 包里最多带几个名字 */
    public static final int MAX_NAMES = 3;
    /** 原版玩家名上限 */
    private static final int NAME_MAX = 16;

    public S2CCurioSealPacket {
        sources = List.copyOf(sources.size() > MAX_NAMES ? sources.subList(0, MAX_NAMES) : sources);
        moreSources = Math.max(0, moreSources);
    }

    public static void encode(S2CCurioSealPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.sealed);
        buf.writeVarInt(packet.sources.size());
        for (String name : packet.sources) {
            buf.writeUtf(name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name, NAME_MAX);
        }
        buf.writeVarInt(packet.moreSources);
    }

    public static S2CCurioSealPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean sealed = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_NAMES) {
            throw new IllegalArgumentException("Too many seal source names: " + count);
        }
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(buf.readUtf(NAME_MAX));
        }
        return new S2CCurioSealPacket(entityId, sealed, names, buf.readVarInt());
    }

    /** 发给本人（带名字）和看得到他的人（不带名字） */
    public static void sendState(ServerPlayer player, boolean sealed, List<String> names, int more) {
        if (!(player instanceof FakePlayer)) {
            ECNetwork.sendTo(player, new S2CCurioSealPacket(player.getId(), sealed, names, more));
        }
        ECNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player),
                new S2CCurioSealPacket(player.getId(), sealed, List.of(), 0));
    }

    /** 只发给本人（压制期间来源换了人，更新名字） */
    public static void sendNames(ServerPlayer player, List<String> names, int more) {
        if (!(player instanceof FakePlayer)) {
            ECNetwork.sendTo(player, new S2CCurioSealPacket(player.getId(), true, names, more));
        }
    }

    /** 只发给 tracker 一个人：target 被压制（开始追踪时补发） */
    public static void sendTo(ServerPlayer tracker, ServerPlayer target) {
        if (!(tracker instanceof FakePlayer)) {
            ECNetwork.sendTo(tracker, new S2CCurioSealPacket(target.getId(), true, List.of(), 0));
        }
    }
}
