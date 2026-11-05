package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.client.matrix.MatrixClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 本人：矩阵图谱（第十三批 13a，batch13.md §3.1.5）。普通同步包不带图谱（{@link IPlayerData#serializeForSync()}），
 * 图谱单独走这个包：登录、重生、换维度发整份（{@link #full}，{@code replace} 为真，客户端先清空再填）；平时新记一种只发那一条
 * （{@link #added}，{@code replace} 为假，客户端只往里加）。
 * <p>
 * 解码限长：每份最多 {@value #MAX_ENTRIES} 条、每条最多 {@value #MAX_ID_LENGTH} 字符（实体类型 id），超出的包直接报错断开。
 */
public record S2CMatrixCodexPacket(boolean replace, List<String> codex, List<String> codexHoanna) {

    /** 每份图谱最多带多少条（配置 {@code codex_max_entries} 的上限也不超过它） */
    public static final int MAX_ENTRIES = 4096;
    public static final int MAX_ID_LENGTH = 256;

    /** 整份（登录、重生、换维度） */
    public static S2CMatrixCodexPacket full(IPlayerData data) {
        return new S2CMatrixCodexPacket(true, List.copyOf(data.getMatrixCodex()), List.copyOf(data.getMatrixCodexHoanna()));
    }

    /** 新记的几条：总图谱新记的种类、海纳尔那份新记的种类，没有的传 null */
    public static S2CMatrixCodexPacket added(@Nullable String codexId, @Nullable String hoannaId) {
        return new S2CMatrixCodexPacket(false, codexId == null ? List.of() : List.of(codexId), hoannaId == null ? List.of() : List.of(hoannaId));
    }

    public static void encode(S2CMatrixCodexPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.replace);
        writeList(buf, packet.codex);
        writeList(buf, packet.codexHoanna);
    }

    public static S2CMatrixCodexPacket decode(FriendlyByteBuf buf) {
        boolean replace = buf.readBoolean();
        List<String> codex = readList(buf);
        List<String> hoanna = readList(buf);
        return new S2CMatrixCodexPacket(replace, codex, hoanna);
    }

    public static void handle(S2CMatrixCodexPacket packet, Supplier<NetworkEvent.Context> ctx) {
        // 客户端类只在 lambda 执行时才加载，专用服不会碰到
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MatrixClient.handleCodex(packet));
    }

    private static void writeList(FriendlyByteBuf buf, List<String> values) {
        int size = Math.min(values.size(), MAX_ENTRIES);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeUtf(values.get(i), MAX_ID_LENGTH);
        }
    }

    private static List<String> readList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalArgumentException("矩阵图谱包条目数不对：" + size);
        }
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf(MAX_ID_LENGTH));
        }
        return values;
    }
}
