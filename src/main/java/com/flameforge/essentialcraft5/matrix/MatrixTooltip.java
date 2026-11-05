package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.util.NumberText;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 灵魂石提示里的矩阵几行（第十三批 13a，batch13.md §3.1.5）：只给绑定者本人看（客户端只有自己的玩家数据），由
 * {@code item/SoulStoneItem.appendHoverText} 在「矩阵属性」那行之后调 {@link #appendSoulStone}。
 * <pre>
 * 矩阵 · 五阶　图谱 21 种
 * 矩阵容量 12 400 / 20 000          （二阶起）
 * 换入开着：只换 20 000 以上的 UBMRU （二阶起；潜行右键灵魂石开关）
 * （登记的拼接点：相性、试炼储备、保留线……）
 * </pre>
 * 别的部分要往这里加行的，在注册类的 {@code init()} 里 {@link #register} 一个 {@link Line}（两端都会登记，只在客户端画提示时调）。
 * 数字的千位分组跟着界面语言走（同研究手册：英文逗号、中文不分组）。
 */
public final class MatrixTooltip {

    /** 研究手册的千位分隔符键（中文是空串） */
    private static final String GROUP_SEP_KEY = "guide.essentialcraft5.ui.fmt.group_sep";

    /** 往灵魂石提示里加行的拼接点 */
    @FunctionalInterface
    public interface Line {
        /**
         * @param viewer  看提示的本人（就是灵魂石绑定的人）
         * @param data    本人的玩家数据（客户端那份）
         * @param tooltip 往里加行
         */
        void append(Player viewer, IPlayerData data, List<Component> tooltip);
    }

    private static final List<Line> LINES = new CopyOnWriteArrayList<>();

    private MatrixTooltip() {
    }

    /** 登记一行（按登记先后排在矩阵、容量两行之后） */
    public static void register(Line line) {
        LINES.add(Objects.requireNonNull(line));
    }

    /** 灵魂石提示里的矩阵几行（只在本人看自己的石头时调） */
    public static void appendSoulStone(Player viewer, IPlayerData data, List<Component> tooltip) {
        int rank = data.getMatrixRank();
        tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.matrix",
                        MatrixRank.rankName(rank).withStyle(ChatFormatting.LIGHT_PURPLE),
                        Component.literal(number(data.getMatrixCodex().size())).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.DARK_GRAY));
        int cap = MatrixPool.capacity(rank);
        if (cap > 0 || data.getMatrixPool() > 0) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.matrix_pool",
                            Component.literal(number(data.getMatrixPool())).withStyle(ChatFormatting.AQUA),
                            Component.literal(number(cap)).withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (cap > 0) {
            boolean on = data.isMatrixPoolAbsorb();
            tooltip.add(Component.translatable(on ? "tooltip.essentialcraft5.soul_stone.matrix_absorb_on" : "tooltip.essentialcraft5.soul_stone.matrix_absorb_off",
                            Component.literal(number(MatrixPool.reserveLine(viewer))).withStyle(ChatFormatting.GREEN))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        for (Line line : LINES) {
            line.append(viewer, data, tooltip);
        }
    }

    /** 按界面语言分组的数字（没有分隔符键时不分组） */
    public static String number(long value) {
        return NumberText.group(Long.toString(value), Language.getInstance().getOrDefault(GROUP_SEP_KEY, ""));
    }
}
