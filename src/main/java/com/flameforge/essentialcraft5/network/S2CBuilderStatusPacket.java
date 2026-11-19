package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.client.BuilderClientStatus;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 创世神之杖的状态（服务端 → 本人，模块包，{@code ECBuilder.init} 登记）：拿着魔杖或有任务时每 10 刻算一次，变了才发（{@code item/BuilderStatus}）。
 * 准星下提示里「手头多少料、可用多少 MRU」用这里的数：随身容器的内容客户端不一定有，储存物品、矩阵容量也是服务端的数。
 * <pre>
 * byte    state             0 空闲 1 施工 2 暂停（材料用完）3 排队 4 撤销中 5 暂停（MRU 不够）6 沿面测量中
 * varlong have              背包和副手里的材料
 * varlong haveInContainers  随身容器里的材料
 * varlong mru               可用 MRU（-1 = 无限）
 * varint  done, total       当前任务的进度
 * varint  queueAhead        排队时前面还有几个
 * varint  pauseTicksLeft    暂停时还剩几刻
 * byte    undoReady         撤销栈里有几条
 * </pre>
 */
public record S2CBuilderStatusPacket(int state, long have, long haveInContainers, long mru, int done, int total, int queueAhead,
                                     int pauseTicksLeft, int undoReady) {

    public static final int IDLE = 0;
    public static final int RUNNING = 1;
    /** 暂停：材料用完 */
    public static final int PAUSED = 2;
    public static final int QUEUED = 3;
    public static final int UNDOING = 4;
    /** 暂停：MRU 不够。暂停原因由服务端给，客户端不按手头的料反推（看得见却取不出来的料也算在手头里） */
    public static final int PAUSED_MRU = 5;
    /** 沿面开工前在测量：done = 已连上的格数，total = 上限 */
    public static final int SURVEYING = 6;

    /** 两种暂停之一 */
    public static boolean paused(int state) {
        return state == PAUSED || state == PAUSED_MRU;
    }

    public static void encode(S2CBuilderStatusPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.state);
        buf.writeVarLong(packet.have);
        buf.writeVarLong(packet.haveInContainers);
        buf.writeVarLong(packet.mru + 1);
        buf.writeVarInt(packet.done);
        buf.writeVarInt(packet.total);
        buf.writeVarInt(packet.queueAhead);
        buf.writeVarInt(packet.pauseTicksLeft);
        buf.writeByte(packet.undoReady);
    }

    public static S2CBuilderStatusPacket decode(FriendlyByteBuf buf) {
        return new S2CBuilderStatusPacket(buf.readUnsignedByte(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong() - 1,
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUnsignedByte());
    }

    /** 客户端主线程调（{@code ECNetwork.registerS2C} 已经套了只在客户端跑） */
    public static void handleClient(S2CBuilderStatusPacket packet) {
        BuilderClientStatus.accept(packet);
    }
}
