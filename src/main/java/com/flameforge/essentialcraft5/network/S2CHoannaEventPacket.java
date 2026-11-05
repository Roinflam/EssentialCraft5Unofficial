package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * 服务端 → 客户端：海纳尔现在的天灾状态（第六批 6a 框架 F6a，batch6a.md §3.4 / §5.4；登记在 {@code ECHoanna.init}）。
 * <p>
 * 格式（9 字节）：事件 id 一个字节（{@link HoannaEvent#id()}，0 = 没有 / 停止）+ 剩余 tick 一个 int + 总时长一个 int。
 * 客户端按收到时的 gameTime 还原 {@code start = gameTime − (total − remaining)}、{@code end = start + total}，淡入淡出自己算（{@code hoanna/HoannaFade}），
 * 所以整个事件期间只在变化时发一次，不用每刻同步。解码时未知 id 当 NONE、remaining 夹到 [0, total]。
 * <p>
 * 什么时候发（M4 的 {@code HoannaEvents}）：事件开始 / 结束 / 被指令停止时发给海纳尔里的所有人（{@link #broadcast}）；
 * 登录、换维度、重生时单发给这个人（{@link #send}；不在海纳尔发 {@link #none()}，客户端离开海纳尔也会自己清零）。
 *
 * @param remaining 还剩多少 tick（≥ 0）
 * @param total     总时长（tick，≥ 0；NONE 时 0）
 */
public record S2CHoannaEventPacket(HoannaEvent event, int remaining, int total) {

    public S2CHoannaEventPacket {
        total = Math.max(0, total);
        remaining = Math.max(0, Math.min(remaining, total));
        if (event.isNone() || total == 0) {
            event = HoannaEvent.NONE;
            remaining = 0;
            total = 0;
        }
    }

    /** 「没有事件」（事件结束、被停止、玩家不在海纳尔） */
    public static S2CHoannaEventPacket none() {
        return new S2CHoannaEventPacket(HoannaEvent.NONE, 0, 0);
    }

    public static void encode(S2CHoannaEventPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.event.id());
        buf.writeInt(packet.remaining);
        buf.writeInt(packet.total);
    }

    public static S2CHoannaEventPacket decode(FriendlyByteBuf buf) {
        HoannaEvent event = HoannaEvent.fromId(buf.readUnsignedByte());
        int remaining = buf.readInt();
        int total = buf.readInt();
        return new S2CHoannaEventPacket(event, remaining, total);
    }

    /** 发给一个人（假玩家不发：Mohist 系混合端上发给假玩家的包会一直攒在队列里，policy §2.1） */
    public static void send(ServerPlayer player, S2CHoannaEventPacket packet) {
        if (!(player instanceof FakePlayer)) {
            ECNetwork.sendTo(player, packet);
        }
    }

    /** 发给这个世界里的所有真玩家（被 {@code ECHoanna.setTestHoannaOverride} 当成海纳尔的测试世界也照发） */
    public static void broadcast(ServerLevel level, S2CHoannaEventPacket packet) {
        for (ServerPlayer player : level.players()) {
            send(player, packet);
        }
    }
}
