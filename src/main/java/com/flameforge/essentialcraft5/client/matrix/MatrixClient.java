package com.flameforge.essentialcraft5.client.matrix;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.network.S2CMatrixCodexPacket;
import com.flameforge.essentialcraft5.network.S2CMatrixStatusPacket;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 矩阵阶的客户端一侧（第十三批 13a，batch13.md §3.1.5）：收图谱包、状态包。只由包的处理器在客户端调用。
 * <p>
 * 图谱直接写进本地玩家数据（{@link IPlayerData#getMatrixCodex()}），灵魂石提示、淬炼台界面、手册首页都从那里读；
 * 状态包原样留着（{@link #lastStatus()}），由淬炼台界面解释。
 */
public final class MatrixClient {

    private static CompoundTag lastStatus = new CompoundTag();

    private MatrixClient() {
    }

    public static void handleCodex(S2CMatrixCodexPacket packet) {
        Player player = Minecraft.getInstance().player;
        IPlayerData data = player == null ? null : PlayerDataEvents.get(player);
        if (data == null) {
            return;
        }
        if (packet.replace()) {
            data.clearMatrixCodex();
        }
        packet.codex().forEach(data::addMatrixCodex);
        packet.codexHoanna().forEach(data::addMatrixCodexHoanna);
    }

    public static void handleStatus(S2CMatrixStatusPacket packet) {
        lastStatus = packet.status();
    }

    /** 最近一次收到的矩阵状态（没收到过是空标签） */
    public static CompoundTag lastStatus() {
        return lastStatus;
    }
}
