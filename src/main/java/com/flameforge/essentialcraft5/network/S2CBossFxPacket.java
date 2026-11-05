package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.client.boss.BossFxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 参战者：首领的整屏画面（第十三批 13c，batch13.md §5.3.5「网络」）。按事件发（换相、开始、结束），不逐刻发；
 * 只发给这只首领的参战者（{@link #send}），旁观的人收不到，画面也就只罩在打它的人身上。
 * <p>
 * 格式：类型（1 字节，{@link #type}）、首领的实体 id（VarInt）、两个参数（float，含义按类型；不是有限数的读成 0）。
 * 客户端处理在 {@code client/boss/BossFxClient}（按类型交给各路登记的处理器、管淡入淡出），收到时实体可能还没同步到，那边按 id 找、找不到就忽略。
 * 整屏效果照 policy §1.2 的护眼规矩（状态切换 ≥ 10 秒淡入淡出、亮度突变 ≤ 0.1 次 / 秒、常驻叠层只画边缘且 ≤ 0.20），由客户端负责。
 * <p>
 * 类型只追加、不改已有的编号（改了要升 {@link ECNetwork#PROTOCOL}）：
 * <ul>
 *   <li>{@link #PARADOX}（0）：矩阵之影的悖论画面（13c-M4），客户端登记成边缘晕影：a = 强度 0~1（身相 0.35、心相 0.6、矩阵相 0.8，
 *       0 = 淡出），b = 颜色 RGB（整数写成 float：身相 0x8A1C2C、心相 0x6A2C9A、矩阵相 0xC0209A）。每次换相发给名单里在线的人，
 *       后进名单（或重新上线）的人补发一次当前这一相，打倒、散去时发 a = 0；</li>
 *   <li>{@link #BLACKOUT}（1）：天灾之心的熄光（13e），边缘晕影：a = 1 开始 / 0 结束，b = 颜色 RGB（0x14051E）；</li>
 *   <li>{@link #SKY_DIM}（2）：天灾之心的场地天色压暗（13e），边缘晕影：a = 强度（0.6，0 = 淡出），b = 主灾那一相的颜色；
 *       客户端顺带按它开 / 关首领音乐；后进名单的人补发一次，打倒、散去时发 a = 0；</li>
 *   <li>{@link #COLLAPSE}（3）：天灾之心的崩解（13e），边缘晕影：a = 强度（0.5，0 = 结束），b = 颜色 RGB（0xB0200A）；</li>
 *   <li>{@link #INTRO}（4）：天灾之心开场的名字大字（加强那一轮，calamity_heart_v2.md §4）：a = 主灾那一相的序号，b = 显示多少刻；</li>
 *   <li>{@link #BANNER}（5）：天灾之心阶段转换的横幅：a = 横幅编号（{@code CalamityHeartEntity.BANNER_*}），b = 标题颜色 RGB；</li>
 *   <li>{@link #SHAKE}（6）：镜头震动：a = 强度 0~1（客户端 × 1.2°），b = 多少刻（≤ 20）。</li>
 * </ul>
 * 后三种不挂在首领实体上（打倒那一刻实体已经在死）：客户端用 {@code BossFxClient.registerUnbound} 登记，实体找不到也照样处理。
 *
 * @param type     画面类型，0~255
 * @param entityId 首领的实体 id
 * @param a        参数一
 * @param b        参数二
 */
public record S2CBossFxPacket(int type, int entityId, float a, float b) {

    /** 矩阵之影的悖论画面（13c-M4） */
    public static final int PARADOX = 0;
    /** 天灾之心的熄光（13e） */
    public static final int BLACKOUT = 1;
    /** 天灾之心的场地天色压暗（13e） */
    public static final int SKY_DIM = 2;
    /** 天灾之心的崩解（13e） */
    public static final int COLLAPSE = 3;
    /** 天灾之心开场的名字大字 */
    public static final int INTRO = 4;
    /** 天灾之心阶段转换的横幅 */
    public static final int BANNER = 5;
    /** 镜头震动 */
    public static final int SHAKE = 6;

    public S2CBossFxPacket {
        type = type & 0xFF;
        a = Float.isFinite(a) ? a : 0F;
        b = Float.isFinite(b) ? b : 0F;
    }

    public static S2CBossFxPacket of(int type, Entity boss, float a, float b) {
        return new S2CBossFxPacket(type, boss.getId(), a, b);
    }

    public static void encode(S2CBossFxPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.type);
        buf.writeVarInt(packet.entityId);
        buf.writeFloat(packet.a);
        buf.writeFloat(packet.b);
    }

    public static S2CBossFxPacket decode(FriendlyByteBuf buf) {
        return new S2CBossFxPacket(buf.readUnsignedByte(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(S2CBossFxPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BossFxClient.handle(packet));
    }

    /** 发给参战者（假玩家不发）；名单由首领自己管（{@code entity/boss/BossRoster}） */
    public static void send(Iterable<? extends ServerPlayer> participants, S2CBossFxPacket packet) {
        for (ServerPlayer player : participants) {
            send(player, packet);
        }
    }

    /** 发给一名参战者（假玩家不发） */
    public static void send(ServerPlayer player, S2CBossFxPacket packet) {
        if (!(player instanceof FakePlayer)) {
            ECNetwork.sendTo(player, packet);
        }
    }
}
