package com.flameforge.essentialcraft5.client.guide;

/**
 * 研究手册自己的界面倍数：怎么挑（纯整数函数，不引用任何客户端类，GameTest 直接调）
 *
 * <p>手册打开时临时把窗口的界面倍数换成这里挑出来的值、关掉时还原（{@link GuideScale}）。游戏的「自动」挑的是
 * 能塞下 320×240 的最大倍数，大屏上手册就只剩 427~480 宽、240~270 高的一小块被放大到满屏（1080p 汉字 32 像素高、
 * 一屏 13 行）。手册改挑「虚拟屏幕至少 {@value #MIN_W}×{@value #MIN_H}」的最大倍数：</p>
 * <pre>
 *   游戏选了手动倍数            → 跟游戏一样（手动选几倍是明确的意愿，不替玩家改）
 *   游戏选「自动」（实际倍数 G） → 2..G 里满足 帧缓冲宽 ≥ {@value #MIN_W} × s 且 帧缓冲高 ≥ {@value #MIN_H} × s 的最大 s；
 *                                 一个都不满足时取 min(2, G)；强制 Unicode 字体时往下取偶数
 * </pre>
 * <p>1080p 4 → 3、1440p 6 → 4、4K 9 → 6；1080p 最大化窗口（约 1009 高）÷ 3 = 336 仍过线，和全屏同一档。
 * 逐行的表和 GameTest 断言在 {@code tools/plan/guide_scale.md} §2.3、{@code GuideScaleTests}。</p>
 *
 * <p><b>最小 2 倍</b>：中文、日文、韩文走 unifont，字形是 16 像素点阵、按 8 像素画，1 倍时隔行隔列取样，字糊成一团；
 * 游戏本身就是 1 倍时跟游戏。<b>强制 Unicode 字体</b>：原版此时只用偶数倍（{@code Window#calculateScale} 遇奇数加 1），手册往下取偶数。</p>
 */
public final class GuideScaleMath {

    /** 自动档：手册的虚拟屏幕至少这么宽（面板用大留白 12，左栏 ≥ 144、正文 ≥ 369；竖屏、窄窗口不会挑出一条细长的面板） */
    public static final int MIN_W = 560;
    /** 自动档：手册的虚拟屏幕至少这么高（面板 300、正文区 247，约 17 行起） */
    public static final int MIN_H = 320;

    /** 配置值：自动 */
    public static final int AUTO = 0;
    /** 配置值：总跟游戏的界面缩放一样 */
    public static final int FOLLOW_GAME = -1;
    /**
     * 配置值上限。游戏倍数在 5K 屏上是 12、6K 14、8K 18（{@code Window#calculateScale} 按 320×240 挑），手册的可选档一直到游戏倍数，
     * 所以要比这些都大；再往上的屏幕「字大一档」到这里为止
     */
    public static final int MAX_FIXED = 32;

    private GuideScaleMath() {
    }

    /**
     * 默认档（配置为自动时）
     *
     * @param fbW        帧缓冲宽（像素）
     * @param fbH        帧缓冲高
     * @param gameOption 游戏选项里的界面缩放（0 = 自动）
     * @param gameScale  游戏实际用的倍数（{@code Window#calculateScale} 的结果）
     * @param unicode    强制 Unicode 字体
     * @return 手册用的倍数
     */
    public static int pick(int fbW, int fbH, int gameOption, int gameScale, boolean unicode) {
        if (gameOption != 0) {
            return gameScale;
        }
        int best = -1;
        for (int s = 2; s <= gameScale; s++) {
            if (fbW >= MIN_W * s && fbH >= MIN_H * s) {
                best = s;
            }
        }
        if (best < 0) {
            best = Math.min(2, gameScale);
        }
        return evenIfUnicode(best, gameScale, unicode);
    }

    /**
     * 把指定的倍数夹进可选范围：[最小, 游戏实际倍数]，最小 = 游戏倍数为 1 时 1、否则 2；强制 Unicode 时往下取偶数
     *
     * @param want      想要的倍数
     * @param gameScale 游戏实际用的倍数
     */
    public static int clamp(int want, int gameScale, boolean unicode) {
        int s = Math.max(min(gameScale), Math.min(gameScale, want));
        return evenIfUnicode(s, gameScale, unicode);
    }

    /** 可选的最小倍数：游戏本身是 1 倍时 1，否则 2 */
    public static int min(int gameScale) {
        return gameScale <= 1 ? 1 : 2;
    }

    /**
     * 配置值换成实际倍数
     *
     * @param setting 配置 {@code [guide] gui_scale}：{@value #AUTO} 自动、{@value #FOLLOW_GAME} 跟游戏、1~{@value #MAX_FIXED} 指定
     *                （超过游戏倍数时按游戏倍数画、配置不改 —— 换显示器回来还是原来的选择）
     */
    public static int resolve(int setting, int fbW, int fbH, int gameOption, int gameScale, boolean unicode) {
        if (setting == FOLLOW_GAME) {
            return gameScale;
        }
        if (setting <= AUTO) {
            return pick(fbW, fbH, gameOption, gameScale, unicode);
        }
        return clamp(setting, gameScale, unicode);
    }

    /**
     * 「Aa」按钮 / Ctrl + 滚轮 / Ctrl − = 调一档：到头了就不动，不循环；强制 Unicode 时一档是 2
     *
     * @param current   现在的倍数
     * @param dir       +1 字大一档（倍数 +1），−1 字小一档
     * @param gameScale 游戏实际用的倍数
     * @return 调完的倍数（到头了返回 current）
     */
    public static int step(int current, int dir, int gameScale, boolean unicode) {
        int stride = unicode ? 2 : 1;
        int next = current + Integer.signum(dir) * stride;
        if (next < min(gameScale) || next > gameScale || (dir > 0 && next > MAX_FIXED)) {
            return current;
        }
        return evenIfUnicode(next, gameScale, unicode);
    }

    /** 强制 Unicode 时奇数往下取偶数（最小 2）；游戏倍数是 1 时不动 */
    private static int evenIfUnicode(int s, int gameScale, boolean unicode) {
        if (!unicode || s % 2 == 0 || gameScale <= 1) {
            return s;
        }
        return Math.max(2, s - 1);
    }
}
