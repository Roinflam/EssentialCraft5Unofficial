package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.GuideScaleMath;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 研究手册自己的界面倍数怎么挑（{@link GuideScaleMath}，纯整数函数，不起世界、不碰客户端类）。
 * <p>
 * 默认档逐行对 tools/plan/guide_scale.md §2.3 那张表：游戏选「自动」时取不超过游戏倍数、虚拟屏幕至少 560×320 的最大倍数，最小 2。
 * 外加：游戏选了手动倍数就跟游戏、强制 Unicode 只出偶数、游戏倍数 1 时结果 1、指定值超出游戏倍数压回来、调一档到头不动。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GuideScaleTests {

    private static final String EMPTY = "empty";

    private GuideScaleTests() {
    }

    private static void expect(List<String> problems, String what, int expected, int actual) {
        if (expected != actual) {
            problems.add(what + " 应为 " + expected + "，实际 " + actual);
        }
    }

    /** §2.3 的表：帧缓冲宽、高、游戏「自动」的实际倍数、手册默认档 */
    @GameTest(template = EMPTY)
    public static void guideScaleAutoTable(GameTestHelper helper) {
        int[][] rows = {
                {1920, 1080, 4, 3},
                {1920, 1009, 4, 3},
                {2560, 1440, 6, 4},
                {2560, 1369, 5, 4},
                {3440, 1440, 6, 4},
                {3840, 2160, 9, 6},
                {1920, 1200, 5, 3},
                {1680, 1050, 4, 3},
                {1600, 900, 3, 2},
                {1366, 768, 3, 2},
                {1280, 720, 3, 2},
                {854, 480, 2, 2},
        };
        List<String> problems = new ArrayList<>();
        for (int[] r : rows) {
            String what = r[0] + "×" + r[1] + "（游戏自动 " + r[2] + "）";
            expect(problems, what, r[3], GuideScaleMath.pick(r[0], r[1], 0, r[2], false));
            // 配置为自动（0）时 resolve 和 pick 一致
            expect(problems, what + " resolve(0)", r[3], GuideScaleMath.resolve(GuideScaleMath.AUTO, r[0], r[1], 0, r[2], false));
            int s = r[3];
            // 挑出来的档确实满足阈值（854×480 那行一档都不满足，取 min(2, G)）
            if (r[0] >= GuideScaleMath.MIN_W * 2 && r[1] >= GuideScaleMath.MIN_H * 2
                    && (r[0] < GuideScaleMath.MIN_W * s || r[1] < GuideScaleMath.MIN_H * s)) {
                problems.add(what + " 挑出的 " + s + " 倍虚拟屏幕不足 560×320");
            }
        }
        helper.assertTrue(problems.isEmpty(), "默认档：" + problems);
        helper.succeed();
    }

    /** 游戏选了手动倍数：照搬游戏的；配置 -1 也是跟游戏 */
    @GameTest(template = EMPTY)
    public static void guideScaleFollowsManualGameScale(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        expect(problems, "1920×1080 手动 4", 4, GuideScaleMath.pick(1920, 1080, 4, 4, false));
        expect(problems, "1920×1080 手动 2", 2, GuideScaleMath.pick(1920, 1080, 2, 2, false));
        expect(problems, "2560×1440 手动 3", 3, GuideScaleMath.pick(2560, 1440, 3, 3, false));
        // 手动选了 6、屏幕只够 4（游戏自己夹到 4）：跟游戏实际用的 4
        expect(problems, "1920×1080 手动 6（实际 4）", 4, GuideScaleMath.pick(1920, 1080, 6, 4, false));
        expect(problems, "配置 -1 跟游戏（自动 4）", 4, GuideScaleMath.resolve(GuideScaleMath.FOLLOW_GAME, 1920, 1080, 0, 4, false));
        expect(problems, "配置 -1 跟游戏（手动 2）", 2, GuideScaleMath.resolve(GuideScaleMath.FOLLOW_GAME, 1920, 1080, 2, 2, false));
        helper.assertTrue(problems.isEmpty(), "手动倍数：" + problems);
        helper.succeed();
    }

    /** 强制 Unicode 字体：原版只用偶数倍（遇奇数加 1），手册往下取偶数、最小 2 */
    @GameTest(template = EMPTY)
    public static void guideScaleUnicodeEvenOnly(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        // 1080p 自动 4（偶数，Unicode 下不变）：本来挑 3 → 2
        expect(problems, "1920×1080 Unicode", 2, GuideScaleMath.pick(1920, 1080, 0, 4, true));
        // 1440p 自动 6：挑 4，已是偶数
        expect(problems, "2560×1440 Unicode", 4, GuideScaleMath.pick(2560, 1440, 0, 6, true));
        // 4K 自动 9 在 Unicode 下是 10：挑 6
        expect(problems, "3840×2160 Unicode", 6, GuideScaleMath.pick(3840, 2160, 0, 10, true));
        // 1920×1200 自动 5 在 Unicode 下是 6：本来挑 3 → 2
        expect(problems, "1920×1200 Unicode", 2, GuideScaleMath.pick(1920, 1200, 0, 6, true));
        expect(problems, "指定 3（Unicode，游戏 4）", 2, GuideScaleMath.clamp(3, 4, true));
        expect(problems, "指定 5（Unicode，游戏 6）", 4, GuideScaleMath.clamp(5, 6, true));
        expect(problems, "指定 4（Unicode，游戏 6）", 4, GuideScaleMath.clamp(4, 6, true));
        // 结果全是偶数
        for (int fbW = 800; fbW <= 4000; fbW += 400) {
            for (int g = 2; g <= 10; g += 2) {
                int s = GuideScaleMath.pick(fbW, fbW * 9 / 16, 0, g, true);
                if (s % 2 != 0) {
                    problems.add(fbW + " 宽、游戏 " + g + " 倍时挑出奇数 " + s);
                }
            }
        }
        // 调一档：Unicode 下一档是 2
        expect(problems, "Unicode 下 4 调大（游戏 6）", 6, GuideScaleMath.step(4, 1, 6, true));
        expect(problems, "Unicode 下 4 调小（游戏 6）", 2, GuideScaleMath.step(4, -1, 6, true));
        helper.assertTrue(problems.isEmpty(), "强制 Unicode：" + problems);
        helper.succeed();
    }

    /** 游戏本身是 1 倍时跟游戏（不会被抬到 2）；别的情况最小 2 */
    @GameTest(template = EMPTY)
    public static void guideScaleGameScaleOne(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        expect(problems, "640×480 游戏自动 1", 1, GuideScaleMath.pick(640, 480, 0, 1, false));
        expect(problems, "1920×1080 手动 1", 1, GuideScaleMath.pick(1920, 1080, 1, 1, false));
        expect(problems, "指定 3、游戏 1", 1, GuideScaleMath.clamp(3, 1, false));
        expect(problems, "指定 1、游戏 1", 1, GuideScaleMath.clamp(1, 1, false));
        expect(problems, "最小档（游戏 1）", 1, GuideScaleMath.min(1));
        expect(problems, "最小档（游戏 4）", 2, GuideScaleMath.min(4));
        expect(problems, "指定 1、游戏 4（最小 2）", 2, GuideScaleMath.clamp(1, 4, false));
        expect(problems, "游戏 1 时调大", 1, GuideScaleMath.step(1, 1, 1, false));
        expect(problems, "游戏 1 时调小", 1, GuideScaleMath.step(1, -1, 1, false));
        helper.assertTrue(problems.isEmpty(), "游戏倍数 1：" + problems);
        helper.succeed();
    }

    /** 指定值夹到 [最小, 游戏倍数]：超过游戏倍数时压回来（配置不改，换显示器回来还是原来的选择）；调一档到头不动、不循环 */
    @GameTest(template = EMPTY)
    public static void guideScaleClampAndStep(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        expect(problems, "指定 5、游戏 3", 3, GuideScaleMath.clamp(5, 3, false));
        expect(problems, "指定 10、游戏 4", 4, GuideScaleMath.clamp(10, 4, false));
        expect(problems, "指定 3、游戏 4", 3, GuideScaleMath.clamp(3, 4, false));
        expect(problems, "配置 6、1080p 游戏 4", 4, GuideScaleMath.resolve(6, 1920, 1080, 0, 4, false));
        expect(problems, "配置 2、1080p 游戏 4", 2, GuideScaleMath.resolve(2, 1920, 1080, 0, 4, false));
        expect(problems, "配置 2、游戏手动 3", 2, GuideScaleMath.resolve(2, 1920, 1080, 3, 3, false));
        expect(problems, "3 调大（游戏 4）", 4, GuideScaleMath.step(3, 1, 4, false));
        expect(problems, "4 调大（游戏 4，到头）", 4, GuideScaleMath.step(4, 1, 4, false));
        expect(problems, "3 调小（游戏 4）", 2, GuideScaleMath.step(3, -1, 4, false));
        expect(problems, "2 调小（到头，不到 1）", 2, GuideScaleMath.step(2, -1, 4, false));
        expect(problems, "游戏 2 时只有一档", 2, GuideScaleMath.step(2, 1, 2, false));
        helper.assertTrue(problems.isEmpty(), "夹紧与调档：" + problems);
        helper.succeed();
    }

    /**
     * 5K / 6K / 8K 屏：游戏倍数 12 / 14 / 18（Window#calculateScale 按 320×240 挑），自动档 9 / 10 / 13。
     * 「字大一档」能一直调到游戏倍数、存得进配置（配置上限 MAX_FIXED 比这些都大），不会存成一个更小的数、越调越小
     */
    @GameTest(template = EMPTY)
    public static void guideScaleLargeScreens(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int[][] rows = {
                {5120, 2880, 12, 9},
                {6016, 3384, 14, 10},
                {7680, 4320, 18, 13},
        };
        for (int[] r : rows) {
            String what = r[0] + "×" + r[1] + "（游戏自动 " + r[2] + "）";
            expect(problems, what + " 自动档", r[3], GuideScaleMath.pick(r[0], r[1], 0, r[2], false));
            int s = r[3];
            while (s < r[2]) {
                int next = GuideScaleMath.step(s, 1, r[2], false);
                if (next != s + 1) {
                    problems.add(what + " 从 " + s + " 调大应到 " + (s + 1) + "，实际 " + next);
                    break;
                }
                if (next > GuideScaleMath.MAX_FIXED || GuideScaleMath.resolve(next, r[0], r[1], 0, r[2], false) != next) {
                    problems.add(what + " 调到 " + next + " 存进配置再读回不是 " + next);
                }
                s = next;
            }
            expect(problems, what + " 到游戏倍数就不动", r[2], GuideScaleMath.step(r[2], 1, r[2], false));
        }
        // 游戏倍数比配置上限还大时，调大到上限为止
        int top = GuideScaleMath.MAX_FIXED;
        expect(problems, "到配置上限就不再调大", top, GuideScaleMath.step(top, 1, top + 4, false));
        expect(problems, "上限处照样能调小", top - 1, GuideScaleMath.step(top, -1, top + 4, false));
        helper.assertTrue(problems.isEmpty(), "大屏：" + problems);
        helper.succeed();
    }
}
