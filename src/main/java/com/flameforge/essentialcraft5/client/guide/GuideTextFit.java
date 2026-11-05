package com.flameforge.essentialcraft5.client.guide;

import java.util.function.ToIntFunction;

/**
 * 研究手册（和 JEI 的同款配方图）里窄地方的字怎么放：纯函数，不引用客户端类，GameTest 直接调；宽度由调用方传量法（客户端传 {@code font::width}）。
 * <ul>
 *   <li>魔力工作台同调配方图两个下角格之间的「n 阶 · 满」：半号字放得进两格物品图标之间（{@value #CORNER_GAP} 像素）才画在那里，
 *       放不下（英文 Stage 2 · full 约 35 像素）就挪到网格下面单独一行，不压在角格的物品图标底下只露出半截。</li>
 *   <li>左栏表头「目录」和右对齐的条目数：两样挤在一起时左边的标题从尾巴截掉、补省略号，条目数到三位数也不叠字。</li>
 * </ul>
 */
public final class GuideTextFit {

    /** 两个下角格的物品图标之间有多宽：格框之间 18 像素，图标各往格子里缩 1 像素 */
    public static final int CORNER_GAP = 20;
    /** 截断时补的省略号 */
    public static final String ELLIPSIS = "…";

    private GuideTextFit() {
    }

    /** 宽 halfWidth（已经是半号字的宽度）的徽标能不能放进两个下角格之间 */
    public static boolean fitsBetweenCorners(int halfWidth) {
        return halfWidth <= CORNER_GAP;
    }

    /**
     * text 宽不超过 avail 就原样返回；超过时从尾巴按字截掉、补上 {@value #ELLIPSIS}，结果的宽不超过 avail；连省略号都放不下返回空串。
     */
    public static String clip(String text, int avail, ToIntFunction<String> width) {
        if (width.applyAsInt(text) <= avail) {
            return text;
        }
        int room = avail - width.applyAsInt(ELLIPSIS);
        if (room < 0) {
            return "";
        }
        int end = text.length();
        while (end > 0 && width.applyAsInt(text.substring(0, end)) > room) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end).stripTrailing() + ELLIPSIS;
    }
}
