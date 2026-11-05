package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.client.matrix.MatrixClient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 本人：矩阵状态（第十三批 13a，batch13.md §3.1.5）。淬炼台界面要的「下一阶要什么、哪几样已经满足」在打开界面时由服务端算好发过来，
 * 客户端不自己算（图谱分组、站点、祭品、能量都要服务端的数据）。
 * <p>
 * 内容是一个 NBT，字段由矩阵框架和淬炼台定（客户端放在 {@link MatrixClient#lastStatus()}）；包本身只管搬运。
 */
public record S2CMatrixStatusPacket(CompoundTag status) {

    public static void encode(S2CMatrixStatusPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.status);
    }

    public static S2CMatrixStatusPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new S2CMatrixStatusPacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(S2CMatrixStatusPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MatrixClient.handleStatus(packet));
    }
}
