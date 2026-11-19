package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.client.GearFxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 服务端 → 附近的玩家：装备成长的特效事件（升阶、战痕、觉醒……，种类见 {@link GearFx}）。只管画面：结算全在服务端，客户端收不到也不影响。
 * 模块包（{@code ECAttunement.init} 登记），和特效大改第 1、2 批同在协议 14 里加，14 还没上服，所以不再升协议号。
 * <p>
 * 格式：种类（1 字节，{@link GearFx} 的序号）、实体 id（VarInt，+1，0 = 没有）、位置（3 × double）、参数（float）。
 * 客户端收到后交给 {@link GearFxClient} 按种类分发；这一批只登记了原版粒子 + 音效的占位画法，正式画法由特效第 3 批（武器）、第 5 批（装备）
 * 在自己的客户端类里 {@code GearFxClient.register} 换掉，服务端不用动。
 */
public record S2CGearFxPacket(int type, int entity, double x, double y, double z, float param) {

    /** 发给这么多格以内的玩家 */
    public static final double RANGE = 64D;

    /** GameTest 出口：设了就把发出去的每个事件也交给它（同 {@code RayShot.BeamSink} 的写法）；null = 不记 */
    @Nullable
    private static volatile Consumer<S2CGearFxPacket> testSink;

    public static void setTestSink(@Nullable Consumer<S2CGearFxPacket> sink) {
        testSink = sink;
    }

    @Nullable
    public GearFx kind() {
        return GearFx.byId(type);
    }

    public Vec3 pos() {
        return new Vec3(x, y, z);
    }

    /** 在 level 的 pos 附近发一个事件（entity 可以是 null） */
    public static void sendAround(ServerLevel level, GearFx type, @Nullable Entity entity, Vec3 pos, float param) {
        S2CGearFxPacket packet = new S2CGearFxPacket(type.ordinal(), entity == null ? -1 : entity.getId(), pos.x, pos.y, pos.z, param);
        Consumer<S2CGearFxPacket> sink = testSink;
        if (sink != null) {
            sink.accept(packet);
        }
        double r2 = RANGE * RANGE;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) <= r2) {
                ECNetwork.sendTo(player, packet);
            }
        }
    }

    public static void encode(S2CGearFxPacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.type);
        buf.writeVarInt(Math.max(0, p.entity + 1));
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.param);
    }

    public static S2CGearFxPacket decode(FriendlyByteBuf buf) {
        int type = buf.readUnsignedByte();
        int entity = buf.readVarInt() - 1;
        double x = finite(buf.readDouble());
        double y = finite(buf.readDouble());
        double z = finite(buf.readDouble());
        float param = buf.readFloat();
        return new S2CGearFxPacket(type, entity, x, y, z, Float.isFinite(param) ? param : 0F);
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0D;
    }

    /** 客户端主线程调（{@code ECNetwork.registerS2C} 已经套了只在客户端跑） */
    public static void handleClient(S2CGearFxPacket packet) {
        GearFxClient.handle(packet);
    }
}
