package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.command.MatrixCommands;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.player.GuideMigration;

/**
 * 矩阵阶框架（第十三批 13a，batch13.md §3.1、§9.1）在模组构造期要登记的东西：SERVER 配置 {@code general.matrix_rank}
 * （{@link MatrixConfig}，含 {@code trial}、{@code siphon}、{@code resonance} 子小节）、指令 {@code /ec5 player <p> matrix …}（{@code command/MatrixCommands}）、
 * 手册迁移的补阶下限动作 {@code essentialcraft5:matrix_rank_floor_1~9}。九阶表的读取器（{@link MatrixRankData}）、图谱（{@link MatrixCodex}）、
 * 容量（{@link MatrixPool}）的事件类自带 {@code @EventBusSubscriber}。
 * <p>
 * 由 {@code ECRegistries.register()} 调一次 {@link #init()}（必须在 ECConfig.register 之前）。
 * 淬炼台方块、回响实体这些注册内容在 {@code registry/ECMatrix}。
 */
public final class MatrixSystems {

    private static boolean initialized;

    private MatrixSystems() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ECConfig.general(MatrixConfig::define);
        ECCommands.addPlayerSubcommand(MatrixCommands::build);
        // 手册迁移 v3 补阶下限（batch13.md §2.6）：表里 guide/matrix/rank_<n> 的 then 写 essentialcraft5:matrix_rank_floor_<n>
        for (int rank = 1; rank <= MatrixRank.MAX; rank++) {
            int floor = rank;
            GuideMigration.registerAction(MatrixRank.floorAction(rank), player -> MatrixRank.raiseTo(player, floor));
        }
    }
}
