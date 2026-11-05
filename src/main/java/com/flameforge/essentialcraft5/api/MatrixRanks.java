package com.flameforge.essentialcraft5.api;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 读玩家矩阵阶的小接口（第十三批 13a，batch13.md §3.1、§9.1）：同调框架写阶戳、各处显示「现在几阶」都调 {@link #get}。
 * 两端都能用（阶随普通同步包发给本人；客户端只知道自己的）。
 * <p>
 * 这里只读数，不管门和回报：服主关掉矩阵阶（{@code general.matrix_rank.enabled = false}）时「门全开、已有的阶照给回报」，
 * 判门、判回报用矩阵框架自己的接口（{@code matrix/MatrixRank}）。
 */
public final class MatrixRanks {

    /** 最高阶 */
    public static final int MAX = IPlayerData.MAX_MATRIX_RANK;

    private MatrixRanks() {
    }

    /** 玩家的矩阵阶 0 ~ {@link #MAX}；没有玩家数据（假玩家、还没挂上）时是 0 */
    public static int get(@Nullable Player player) {
        if (player == null) {
            return 0;
        }
        IPlayerData data = player.getCapability(ECCapabilities.PLAYER_DATA).orElse(null);
        return data == null ? 0 : data.getMatrixRank();
    }
}
