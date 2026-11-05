package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.mru.MRUBalance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 研究手册的配色、尺寸与绘制原语
 *
 * <h3>调性：源质紫做框，读的地方安静（2026-09-29 护眼改版）</h3>
 * <p>上一版（整块护板深紫底、大块品红「下一步」条、阅读栏周围一直在流动 / 呼吸 / 闪）<b>读久了眼干</b>。
 * 现在的分工（底色、结构线、焦点色的出处见 {@link CodexTheme}）：</p>
 * <ul>
 *   <li><b>读的面</b>（阅读栏、首页格子区）是低饱和的暗灰紫 {@link #PLATE}（HSV 饱和度 0.24，上一版 0.79），不是纯黑；
 *       正文是略带暖意的米白 {@link #BODY}；这一片里<b>没有任何常驻动画</b>（见 {@link #time()} 的清单）；</li>
 *   <li><b>源质紫</b>在框上：外框薰衣草描边 + 四角矩阵节点、顶栏 / 底栏的护板紫带 {@link #BAND}（CIELAB 彩度 22）、左栏的暗紫 {@link #SIDEBAR_BG}（彩度 17；
 *       读的面只有 7）、MRU 紫罗兰的结构线、小标题竖条；</li>
 *   <li><b>高饱和色只画小面积</b>：品红 {@link #FOCUS} 只给选中竖条、悬停描边、链接下划线、「下一步」的细边和箭头；
 *       「下一步」条的底和普通格子一样是低饱和的 {@link #TILE_BG}（上一版是大块暮紫品红）；静止装饰用紫罗兰 {@link #ACCENT}；</li>
 *   <li>闪电（{@link GuideArcs}）只在事件上闪一下（开场、翻页、悬停、打勾），外加顶栏 / 底栏空白、外框上沿内侧、面板外留白里约 10 秒一次的环境电弧，
 *       不进阅读栏和左栏；合成箭头只有尖端 3 列是品红；配方 / 结构图的候选物品只在鼠标压着这张图时轮换。</li>
 * </ul>
 * <p>「源质小知识」框的标题和左色条用原作研究书纸页的米白 {@link #TIP_LORE}，和功能性提示（天青 / 杏橙 / 薄荷 / 蔷薇粉）分开。
 * 行内标签：冰蓝 = 数值与名词、薄荷绿 = 可以 / 好处、蔷薇粉 = 危险、杏橙 = 注意，关键术语和强调是暖白加粗；
 * 平衡值三态（{@link #FROZEN} / {@link #PURE} / {@link #CHAOS}）和配方图的平衡值色条是游戏数据的语义色，原样保留。</p>
 *
 * <h3>对比度（WCAG 相对亮度；各块的底都是不透明的，按实际合成色算）</h3>
 * <p>读的面 {@link #PLATE} #1F1C25（亮度 0.0126）。<b>正文</b> {@link #BODY} 对它 11.2:1 —— 落在 9~12:1 里（再低费眼、再高字边发光晕）；
 * 提示框里 10.5:1、键值表斑马纹上 10.1:1、表头 9.8:1、最低是按下的格子 9.4:1。主文字 {@link #PEARL}（标题、键值表的值）12.0:1，
 * {@code <em>} / {@code <hl>} 加粗的 {@link #TAG_EM} 13.1:1（只是短词）。左栏条目字 {@link #NAV_TEXT} 对左栏 {@link #SIDEBAR_BG} 10.9:1、
 * 可选步骤 {@link #NAV_TEXT_DIM} 7.5:1、左栏里的 {@link #FAINT} 5.9:1（选中行的品红底上 4.9:1）。
 * <b>次要字</b> {@link #MIST} 6.9:1（最低 5.8:1，按下的格子上），最次要的 {@link #FAINT} 5.6:1（最低 4.7:1）；键值表标签 {@link #LABEL} 9.1:1。
 * 链接 {@link #LINK} 8.7:1、物品链接 {@link #ITEM_LINK} 9.1:1、{@code <t>} 9.5:1、杏橙 8.9:1、薄荷 9.7:1、蔷薇粉 8.2:1（最低 6.8:1）；
 * 薰衣草 {@link #GLOW} 当字用（分区标题、面包屑、表头）7.2:1（最低 6.0:1）；提示框标题最低 7.7:1（蔷薇粉），小知识米白 11.9:1。
 * 顶栏 / 底栏紫带 {@link #BAND} 上书名 {@link #PEARL} 11.6:1、副标题和底栏计数 {@link #MIST} 6.7:1、底栏提示和搜索占位 {@link #FAINT} 5.5:1。
 * 品红 {@link #FOCUS} 只当图形用（5.3:1），当文字用 {@link #LINK}。
 * 改色时用同样的算法复核：正文 9~12:1，次要字不低于 4.5:1。</p>
 *
 * <h3>给配方图、结构图留的旧名字</h3>
 * <p>{@link Figures}、{@link GuideFrame} 是旧界面留下来的绘制代码，用的 {@link #TEXT}、{@link #TEXT_DIM}、
 * {@link #BORDER}、{@link #SLOT}、{@link #EDGE} 这几个常量名照旧保留，物品格、合成箭头、平衡值色条等图元也原样留在这里。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class GuideTheme {

    private GuideTheme() {}

    // ==================== 结构色 ====================

    /** 整屏遮罩：比 MRU 聚集体暗面 #100030 更深的靛紫，背后的世界隐约可见，但不干扰阅读（面板外只露几像素，保留源质紫） */
    public static final int SCRIM = 0xE40A0420;
    /** 暗角的颜色（alpha 由 {@link #vignette} 按层叠加） */
    private static final int VIGNETTE = 0x06021A;
    public static final int PLATE = CodexTheme.PLATE;
    public static final int PLATE_HI = CodexTheme.PLATE_HI;
    public static final int PLATE_DEEP = CodexTheme.PLATE_DEEP;
    /** 结构线（MRU 紫罗兰） */
    public static final int EDGE = CodexTheme.EDGE;
    /** 薰衣草：结构性的亮线、刻度、描边、分区标题（MRU 紫罗兰提亮） */
    public static final int GLOW = CodexTheme.GLOW;
    /** 亮薰衣草：「亮一档」的结构字（按键名、平衡值区间框），对读的面 12.0:1 */
    public static final int GLOW_BRIGHT = 0xFFE0D4FA;
    /** 焦点色：品红（当前条目、悬停中的格子 / 链接、选中、下一步的细边），只当小面积的图形用 */
    public static final int FOCUS = CodexTheme.FOCUS;
    /** 暗品红：宝石背光面、箭头的下半 */
    public static final int FOCUS_DEEP = CodexTheme.FOCUS_DEEP;
    /**
     * 静止装饰的紫罗兰（MRU 方块 {@code block/mru.png} 的 #6000C0 ~ #7000E0 提亮）：小标题竖条的下端、徽框角标、
     * 矩阵节点的亮面。这些东西一屏里有好几处，用亮品红会和「当前条目」的品红竖条同形同色，焦点就找不到了
     */
    public static final int ACCENT = CodexTheme.MRU_VIOLET;
    public static final int PEARL = CodexTheme.PEARL;
    public static final int MIST = CodexTheme.MIST;
    public static final int FAINT = CodexTheme.FAINT;

    /** 开场笔头、光幕芯、显影那一下、描线笔头：MRU 光的丁香白芯 */
    public static final int HOT = CodexTheme.MRU_CORE;

    // ==================== 面板上的块 ====================

    /**
     * 顶栏、底栏：护板紫的一条带（魔法护板 #501050 的色相，HSV 饱和度 0.54、明度 0.22；CIELAB 彩度 C≈22，读的面只有 7）——
     * 源质紫主要在这里和外框上。不透明：下面的读的面是灰紫，半透明叠会把紫冲淡。
     * 上一版 #221A30 的彩度只有 16（旧深紫底的一半），截图对比下来「乍看像一套通用暗色主题」，往紫里提了一档
     */
    public static final int BAND = 0xFF261A38;
    /**
     * 左栏的底：比读的面暗一档、明显更紫的暗紫（饱和度 0.52、明度 0.165，彩度 C≈17），导航和正文分成两块；不透明，对比度按它算。
     * 上一版 #19151F 看着「深紫」，实测彩度 7.8 和读的面一样，只是更暗的灰（HSV 饱和度 0.32 是太暗放大出来的）。
     * 左栏不是读正文的面，紫一点不费眼；条目字 {@link #NAV_TEXT} 10.9:1、最次要的 {@link #FAINT} 5.9:1（选中行上 4.9:1）
     */
    public static final int SIDEBAR_BG = 0xFF1D142A;
    /** 导航按钮悬停时的底：读的面往紫罗兰偏一点（饱和度 0.32） */
    public static final int PANEL_HOVER = 0xFF2D2638;
    /** 导航按钮、层按钮按下时的底：暗紫红（矩阵节点 #880080 的色相，压到明度 0.25），只在按住的那一下出现 */
    public static final int PRESS_BG = 0xFF38243F;
    /** 细边框、分隔线、层刻度的暗段：灰紫（结构线再暗、再淡一档） */
    public static final int BORDER = 0xFF4A4060;
    /**
     * 首页分类格子、篇末翻页卡、层按钮、「下一步」条的底：比读的面亮一档的灰紫板（同色相、饱和度 0.26），
     * 几乎不透明。上一版是饱和的暮紫，二十几格铺满首页就是一整片紫
     */
    public static final int TILE_BG = 0xF027232F;
    /** 格子悬停时的底：往紫罗兰偏一点（饱和度 0.32）；描边转品红由调用方画 */
    public static final int TILE_BG_HOVER = 0xF42D2638;
    /** 格子按下时的底：暗紫红 */
    public static final int TILE_BG_PRESS = 0xF838243F;
    /** 左栏条目文字（常态 / 可选步骤压暗一档）：带一点紫的浅灰，对左栏 10.9:1 / 7.5:1 */
    public static final int NAV_TEXT = 0xFFCFC8D6;
    public static final int NAV_TEXT_DIM = 0xFFADA5BA;

    // ==================== 旧界面的颜色（Figures / GuideFrame / 查阅提示在用） ====================

    /** 主文字 */
    public static final int TEXT = CodexTheme.PEARL;
    /** 次要文字 */
    public static final int TEXT_DIM = CodexTheme.MIST;
    /** 成功 / 已完成：薄荷绿（饱和度 0.33，对读的面 9.7:1） */
    public static final int ON = 0xFF8FD4A2;
    /**
     * 失败 / 危险：蔷薇粉（饱和度 0.35）。<b>别和平衡值「混沌」{@link #CHAOS}（珊瑚红）同色</b> ——
     * 同一段里「别这么做」和「混沌」要分得开：色相差 24°、蔷薇粉更淡
     */
    public static final int DANGER = 0xFFF09DB5;
    /** 物品格底色：磨砂灰紫，近黑的物品 2.0:1、白色物品 9.2:1 都看得清 */
    public static final int SLOT = CodexTheme.FROST;
    /** 物品格描边 */
    public static final int SLOT_EDGE = CodexTheme.FROST_EDGE;
    /** 物品格左上角的内高光 */
    public static final int SLOT_HI = CodexTheme.FROST_HI;
    /**
     * 平衡值三态的字色。原作界面用纯蓝 / 青 / 纯红（MRUBalance.Kind.textColor），纯蓝在暗底上几乎看不见，
     * 这里提亮成同色相的浅色，只用于手册。是游戏数据的语义色，换主题也不改。
     */
    public static final int FROZEN = 0xFF8FB8FF;
    public static final int PURE = 0xFF8FF0E8;
    public static final int CHAOS = 0xFFFF8F80;

    // ==================== 正文与行内标签 ====================

    /**
     * 正文：略带暖意的米白（色相取原作研究书纸页 #FFFAEE，饱和度 0.08、明度 0.85），对读的面 11.2:1。
     * 不用纯白、也不用上一版的薰衣草白：冷白字在暗底上读久了发涩，暖一点的浅色更柔和
     */
    public static final int BODY = 0xFFD9D2C7;
    /** {@code <em>}：暖白加粗（旧格式的 **强调**），对读的面 13.1:1 —— 比正文亮一档就够，不用纯白 */
    public static final int TAG_EM = 0xFFEAE3D8;
    /**
     * {@code <hl>}：关键术语，和 {@code <em>} 一样是暖白加粗（加粗在 {@link GuideMarkup} 里）。
     * 不用焦点色：品红只留给能点的东西，没有下划线的关键词不会被当成链接去点
     */
    public static final int TAG_HL = TAG_EM;
    /** {@code <t>}：冰蓝（饱和度 0.28），数值、名词、界面元素名；和结构薰衣草 {@link #GLOW}、平衡值「冰冻」{@link #FROZEN} 各差一截色相 */
    public static final int TAG_T = 0xFFAFC2F2;
    /** {@code <w>}：杏橙（饱和度 0.47），注意、代价 */
    public static final int TAG_W = 0xFFEDB07E;
    /** {@code <g>}：增益 / 可以 */
    public static final int TAG_G = ON;
    /** {@code <b>}：减益 / 危险 */
    public static final int TAG_B = DANGER;
    /** {@code <dim>}：次要说明 */
    public static final int TAG_DIM = TEXT_DIM;

    /**
     * 条目链接色：淡品红（焦点色 {@link #FOCUS} 冲淡到饱和度 0.32，对读的面 8.7:1），常态带点线下划线；
     * 悬停时文字提亮成 {@link #LINK_HOVER}、下划线转 {@link #FOCUS} 实线。焦点色的文字（「下一步」、查阅提示）也用它
     */
    public static final int LINK = 0xFFE7A3F0;
    /** 链接悬停时的字色：更亮更淡的品红白，配焦点色实线下划线 */
    public static final int LINK_HOVER = 0xFFF6CFF8;
    /** 物品链接（{@code {item:}} / {@code {block:}}）：丁香紫，常态点线下划线，悬停转品红（和条目链接色相差 35°） */
    public static final int ITEM_LINK = 0xFFC8B6F2;
    /** 配置里缺这个键时显示的「?」 */
    public static final int MISSING = TAG_W;

    /** 小标题 */
    public static final int HEADING = PEARL;
    /** 键值表的左列标签：比正文暗一档、比 MIST 亮一档 */
    public static final int LABEL = 0xFFC4BCC9;
    /** 表头文字：薰衣草 */
    public static final int TABLE_HEAD = GLOW;

    // ==================== 提示框 ====================

    /** info：天青（和结构薰衣草 {@link #GLOW}、「冰冻」{@link #FROZEN}、「纯净」{@link #PURE} 色相都差 20° 以上） */
    public static final int TIP_INFO = 0xFF9CCFE6;
    public static final int TIP_WARN = TAG_W;
    public static final int TIP_GOOD = ON;
    public static final int TIP_BAD = DANGER;
    /**
     * 源质小知识：原作研究书界面 {@code gui/research_book_generic.png} 纸页的米白（纸页主色 #FFFAEE / #FDF8EC，
     * 压暗一点），只用在标题、左色条、右上切角，表示「这是原作背景，可以跳过」，和四种功能性提示色一眼分开
     */
    public static final int TIP_LORE = 0xFFE6DFD2;
    /**
     * 提示框的底：比读的面亮半档的灰紫（同色相、饱和度 0.25），<b>所有样式同一个底</b>，语义色只放在左色条、标题、右上切角。
     * 框里正文 10.5:1、标题最低 7.7:1
     */
    public static final int TIP_BG = 0xFF25212C;
    /**
     * 结构图「可选」格子上的薄纱：灰紫白（{@link #MIST} 提亮一档），罩在物品图标上像隔着磨砂玻璃看 ——
     * 颜色变淡，深色方块反而更显形
     */
    public static final int OPTIONAL_VEIL = 0x58C8BCEC;
    /** 查阅提示（物品提示里「按住 Ctrl 查阅」）进度圈的底圈：半透明的暖白，按住时上面描一圈品红 */
    public static final int RING_TRACK = 0x70F0E8DC;

    // ==================== 尺寸 ====================

    /** 屏幕四周留白（大屏）；小于 560 宽时降到 {@link #MARGIN_SMALL} */
    public static final int MARGIN = 12;
    public static final int MARGIN_SMALL = 6;
    public static final int HEADER_H = 34;
    public static final int FOOTER_H = 18;
    public static final int PAD = 10;
    /** 主框体切角 */
    public static final int CHAMFER = 10;
    /**
     * 正文行高：字高 9 + 行距 4（上一版 12）。中文一行 9 像素的字排得密，多空一像素换行时眼睛不容易跳错行。
     * 所有块的高度、链接热区、滚动步长都按它算；块的高度用 {@code 行数 × LINE_H − }{@link #LINE_TAIL}（最后一行不留行距）
     */
    public static final int LINE_H = 13;
    /** 一段文字最后一行下面不留的那截行距：{@code LINE_H − 10}（字 9 像素 + 阴影 1 像素） */
    public static final int LINE_TAIL = LINE_H - 10;
    /** 面板最宽多少：再宽阅读栏也不会跟着变宽，只会让两侧空得更多 */
    public static final int PANEL_MAX_W = 760;
    /** 阅读栏最大宽度：一行超过约 50 个汉字，眼睛换行时就容易找错下一行 */
    public static final int READ_MAX_W = 460;

    // ==================== 像素点阵数字 ====================

    /**
     * 0~9 的 5×7 点阵（每行 5 位，最高位是最左一列）
     *
     * <h3>为什么不用字体画</h3>
     * <p>步骤徽记只有 13 像素宽，数字要严格居中。用字体画时居中靠的是字宽，而字形在字宽里的位置取决于字体：
     * 整合包换了像素字体后有的数字偏半格到一格，徽记里一眼就能看出歪。自己画点阵与字体无关。</p>
     */
    private static final int[][] DIGITS = {
            {14, 17, 19, 21, 25, 17, 14},
            {4, 12, 4, 4, 4, 4, 31},
            {14, 17, 1, 6, 8, 17, 31},
            {14, 17, 1, 6, 1, 17, 14},
            {3, 5, 9, 17, 31, 1, 1},
            {31, 16, 30, 1, 1, 17, 14},
            {6, 8, 16, 30, 17, 17, 14},
            {31, 17, 1, 2, 4, 4, 4},
            {14, 17, 17, 14, 17, 17, 14},
            {14, 17, 17, 15, 1, 2, 12}
    };

    /** 点阵数字的宽度（含数字间 1 像素间隔） */
    public static int digitsWidth(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                n++;
            }
        }
        return n == 0 ? 0 : n * 6 - 1;
    }

    /**
     * 以 {@code centerX} 列为中心画一串点阵数字（非数字字符跳过）
     *
     * @param g       画布
     * @param s       数字串
     * @param centerX 中心列（奇数宽度的框传中间那一列）
     * @param top     顶行
     * @param color   颜色
     */
    public static void digits(GuiGraphics g, String s, int centerX, int top, int color) {
        int x = centerX - (digitsWidth(s) - 1) / 2;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') {
                continue;
            }
            int[] glyph = DIGITS[ch - '0'];
            for (int row = 0; row < 7; row++) {
                int bits = glyph[row];
                int col = 0;
                while (col < 5) {
                    if ((bits & (1 << (4 - col))) == 0) {
                        col++;
                        continue;
                    }
                    // 同一行连续的点合成一次 fill
                    int start = col;
                    while (col < 5 && (bits & (1 << (4 - col))) != 0) {
                        col++;
                    }
                    g.fill(x + start, top + row, x + col, top + row + 1, color);
                }
            }
            x += 6;
        }
    }

    // ==================== 颜色工具 ====================

    /** 换 alpha（0~255） */
    public static int withAlpha(int argb, int alpha) {
        return CodexTheme.withAlpha(argb, alpha);
    }

    /**
     * 按比例缩放 alpha（旧界面的 {@code withAlpha(int, float)} 就是它）
     *
     * @param argb 原色
     * @param f    0~1
     * @return 新颜色
     */
    public static int fade(int argb, float f) {
        return CodexTheme.fade(argb, f);
    }

    /**
     * 文字颜色乘透明度
     *
     * <p>原版字体渲染把 alpha 低于 4 的颜色当成「没写 alpha」按不透明画，淡出到最后会突然闪一下全亮，
     * 所以最低保留 5。几乎透明的文字调用方应该直接不画。</p>
     */
    public static int textAlpha(int argb, float scale) {
        int alpha = Math.max(5, (int) (((argb >>> 24) & 0xFF) * Mth.clamp(scale, 0f, 1f)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerpColor(int from, int to, float t) {
        float k = Mth.clamp(t, 0f, 1f);
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * k);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 平衡值对应的 MRU 颜色（纯净青、冰冻蓝、混沌红，和机器里 MRU 粒子同一套渐变） */
    public static int balanceColor(float balance) {
        float[] rgb = MRUBalance.rgb(balance);
        return 0xFF000000 | ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8) | (int) (rgb[2] * 255);
    }

    // ==================== 时间与曲线 ====================

    /**
     * 常驻动画共用的时间源（秒）。取模 12 分钟，免得游戏开久了浮点精度变差。
     *
     * <p>回绕那一刻时间从 720 跳回 0，只有周期能整除 720 秒的动画才看不出接缝。
     * <b>2026-09-29 护眼改版后，常驻动画只剩一个</b>：顶栏空白处 MRU 微光粒的闪光呼吸 6 秒（{@link CodexTheme.MruMotes}，
     * 只在 60%~100% 之间起伏，安静区挡掉了正文区）。阅读栏、左栏、首页格子区里原先那些都改成静止的了：
     * 条目头下沿的流光、左栏选中行的呼吸、实体预览的环顾（鼠标不在时停在侧身角度）、结构图机器格 / 仪式中心的品红四角呼吸、
     * 仪式里上下浮动的物品、平衡值配方的光晕呼吸、合成箭头的进度、首页上浮的光粒、「下一步」条和顶栏下沿流动的 MRU 线、顶栏上游走的光点；
     * 配方 / 结构图里候选物品（标签原料、可换方块）的每秒轮换改成只在鼠标压着那张图时才转（{@link GuideFrame#cycling}，按 {@code Util.getMillis()} 算）。
     * <b>以后再加常驻动画，周期也必须能整除 720 秒</b>，否则每 12 分钟会瞬移一次；而且不许放进阅读栏和左栏。
     * （开场 / 收场 / 翻页 / 电弧 {@link GuideArcs} 按 {@code Util.getMillis()} 算，是事件，不走这个时间源。）</p>
     *
     * @return 秒
     */
    public static float time() {
        return (System.currentTimeMillis() % 720_000L) / 1000f;
    }

    /** 缓出：开头快、结尾慢。用于出现 —— 滑进来后稳稳停住。 */
    public static float easeOutCubic(float t) {
        float inv = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - inv * inv * inv;
    }

    /** 缓入：与缓出互为镜像，用于消失。 */
    public static float easeInCubic(float t) {
        float c = Mth.clamp(t, 0f, 1f);
        return c * c * c;
    }

    /** 带一点过冲再回弹的缓出：格子浮现用。 */
    public static float easeOutBack(float t) {
        float inv = Mth.clamp(t, 0f, 1f) - 1f;
        return 1f + inv * inv * (2.70158f * inv + 1.70158f);
    }

    /**
     * 指数趋近：每帧向目标靠近一截，与帧率无关。悬停高亮、平滑滚动都用它。
     *
     * @param speed 越大越快，16 约等于 0.1 秒走完大半
     */
    public static float approach(float current, float target, float deltaSeconds, float speed) {
        float next = current + (target - current) * (1f - (float) Math.exp(-speed * deltaSeconds));
        // 足够近就直接贴上：指数曲线永远到不了终点，残留的零点几像素会让文字在两个像素之间来回跳
        return Math.abs(target - next) < 0.02f ? target : next;
    }

    /** 呼吸系数：在 min 与 1 之间以 period 秒为周期平滑往复。 */
    public static float breathe(float time, float period, float min) {
        float wave = (float) ((Math.sin(time / period * Math.PI * 2.0) + 1.0) * 0.5);
        return min + (1f - min) * wave;
    }

    // ==================== 绘制原语 ====================

    public static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 四周压暗的暗角（深靛紫）：遮罩均匀的话视线没有着落点，四周再压一档，中间的面板自然浮出来。 */
    public static void vignette(GuiGraphics g, int width, int height, float scale) {
        if (scale <= 0f) {
            return;
        }
        int rings = 6;
        int stepX = Math.max(1, width / (rings * 4));
        int stepY = Math.max(1, height / (rings * 4));
        int color = (((int) (26 * scale)) << 24) | VIGNETTE;
        for (int i = 0; i < rings; i++) {
            int left = i * stepX;
            int top = i * stepY;
            g.fill(0, top, left + stepX, height - top, color);
            g.fill(width - left - stepX, top, width, height - top, color);
            g.fill(left + stepX, 0, width - left - stepX, top + stepY, color);
            g.fill(left + stepX, height - top - stepY, width - left - stepX, height, color);
        }
    }

    /** 从左往右由浓到淡的渐变条。悬停行用它：纯色块会把整行盖住，渐变像一道光扫过去，不抢文字。 */
    public static void gradientRow(GuiGraphics g, int x, int y, int width, int height, int color) {
        if (width <= 0) {
            return;
        }
        int steps = 12;
        int step = Math.max(1, width / steps);
        for (int i = 0; i < steps; i++) {
            int sx = x + i * step;
            int ex = i == steps - 1 ? x + width : Math.min(x + width, sx + step);
            if (sx >= ex) {
                break;
            }
            float k = 1f - (i / (float) steps);
            g.fill(sx, y, ex, y + height, fade(color, k * k));
        }
    }

    /**
     * 斜切徽框：图标嵌在里面的小八边形
     *
     * @param fill    底色
     * @param outline 描边色
     */
    public static void emblem(GuiGraphics g, int x, int y, int size, int fill, int outline) {
        int cut = Math.max(2, size / 4);
        CodexTheme.chamferFill(g, x, y, size, size, cut, fill);
        CodexTheme.chamferOutline(g, x, y, size, size, cut, outline);
    }

    /**
     * 小徽章：斜切底 + 文字，高 11px
     *
     * @return 宽度
     */
    public static int chip(GuiGraphics g, Font font, String text, int x, int y, int color, float alpha) {
        int w = font.width(text) + 8;
        int h = 11;
        CodexTheme.chamferFill(g, x, y, w, h, 2, fade(withAlpha(color, 0x22), alpha));
        CodexTheme.chamferOutline(g, x, y, w, h, 2, fade(withAlpha(color, 0x90), alpha));
        g.drawString(font, text, x + 4, y + 2, textAlpha(color, alpha), false);
        return w;
    }

    public static int chipWidth(Font font, String text) {
        return font.width(text) + 8;
    }

    /** 中间实、两端渐隐的细线（分隔线、实体预览的地台）。分 16 段画，不逐像素。 */
    public static void hairline(GuiGraphics g, int x, int y, int w, int color) {
        if (w <= 0) {
            return;
        }
        final int segments = 16;
        for (int i = 0; i < segments; i++) {
            int sx = x + w * i / segments;
            int ex = x + w * (i + 1) / segments;
            if (ex <= sx) {
                continue;
            }
            float mid = (i + 0.5f) / segments;
            float k = 1f - Math.abs(mid - 0.5f) * 2f;
            g.fill(sx, y, ex, y + 1, fade(color, (float) Math.sqrt(k)));
        }
    }

    /** 虚线：链接的常态下划线。两像素一点，比实线轻，一眼能看出「这里能点」又不抢字。 */
    public static void dotted(GuiGraphics g, int x, int y, int w, int color) {
        for (int i = 0; i < w; i += 2) {
            g.fill(x + i, y, x + i + 1, y + 1, color);
        }
    }

    /** 像素箭头（朝左或朝右的 V 形），画在导航按钮里。字体里的箭头字符在不同字体包里宽窄不一，自己画最稳。 */
    public static void chevron(GuiGraphics g, int cx, int cy, boolean left, int color) {
        for (int i = 0; i < 4; i++) {
            int dx = left ? (i - 2) : (1 - i);
            g.fill(cx + dx, cy - i, cx + dx + 2, cy - i + 1, color);
            if (i > 0) {
                g.fill(cx + dx, cy + i, cx + dx + 2, cy + i + 1, color);
            }
        }
    }

    /** 像素房子：主页按钮。 */
    public static void house(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 1, cy - 4, cx + 1, cy - 3, color);
        g.fill(cx - 2, cy - 3, cx + 2, cy - 2, color);
        g.fill(cx - 4, cy - 2, cx + 4, cy - 1, color);
        g.fill(cx - 3, cy - 1, cx - 2, cy + 4, color);
        g.fill(cx + 2, cy - 1, cx + 3, cy + 4, color);
        g.fill(cx - 3, cy + 3, cx + 3, cy + 4, color);
        g.fill(cx - 1, cy + 1, cx + 1, cy + 3, color);
    }

    /** 放大镜：搜索框的前导图标。 */
    public static void magnifier(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 4, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 4, color);
        g.fill(x + 4, y + 1, x + 5, y + 4, color);
        g.fill(x + 1, y + 4, x + 4, y + 5, color);
        g.fill(x + 4, y + 5, x + 5, y + 6, color);
        g.fill(x + 5, y + 6, x + 6, y + 7, color);
    }

    /**
     * 像素「Aa」：顶栏调界面大小的按钮。大 A 7 行高、小 a 5 行高，底边对齐，整体 10×7、以 (cx, cy) 为中心。
     * 和放大镜同一类：字体里的字母在不同字体包里粗细不一，自己画最稳
     */
    public static void scaleGlyph(GuiGraphics g, int cx, int cy, int color) {
        int x = cx - 5;
        int top = cy - 3;
        // 大 A（5 宽）：尖顶、两条斜腿、第 5 行横梁
        g.fill(x + 2, top, x + 3, top + 1, color);
        g.fill(x + 1, top + 1, x + 2, top + 3, color);
        g.fill(x + 3, top + 1, x + 4, top + 3, color);
        g.fill(x, top + 3, x + 1, top + 7, color);
        g.fill(x + 4, top + 3, x + 5, top + 7, color);
        g.fill(x + 1, top + 4, x + 4, top + 5, color);
        // 小 a（4 宽，后 5 行）：顶弧、右竖、中横、左下、底横
        int ax = x + 6;
        int at = top + 2;
        g.fill(ax + 1, at, ax + 3, at + 1, color);
        g.fill(ax + 3, at + 1, ax + 4, at + 5, color);
        g.fill(ax + 1, at + 2, ax + 3, at + 3, color);
        g.fill(ax, at + 3, ax + 1, at + 4, color);
        g.fill(ax + 1, at + 4, ax + 3, at + 5, color);
    }

    /**
     * 像素四芒星（9×9）：顶栏「开场动画」开关。开着是实心的星，关着改画暗色的星并加一道斜杠。以 (cx, cy) 为中心
     */
    public static void animGlyph(GuiGraphics g, int cx, int cy, int color, int slash, boolean on) {
        // 竖、横两条主轴（长 9、粗 1），中间一格四周补成菱形的饱满感
        g.fill(cx, cy - 4, cx + 1, cy + 5, color);
        g.fill(cx - 4, cy, cx + 5, cy + 1, color);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);
        g.fill(cx - 2, cy - 2, cx - 1, cy - 1, color);
        g.fill(cx + 2, cy - 2, cx + 3, cy - 1, color);
        g.fill(cx - 2, cy + 2, cx - 1, cy + 3, color);
        g.fill(cx + 2, cy + 2, cx + 3, cy + 3, color);
        if (!on) {
            // 斜杠：左下到右上
            for (int i = -4; i <= 4; i++) {
                g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, slash);
            }
        }
    }

    /** 像素对勾（7×5）：已完成的步骤 */
    public static void check(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 2, x + 1, y + 3, color);
        g.fill(x + 1, y + 3, x + 2, y + 4, color);
        g.fill(x + 2, y + 4, x + 3, y + 5, color);
        g.fill(x + 3, y + 3, x + 4, y + 4, color);
        g.fill(x + 4, y + 2, x + 5, y + 3, color);
        g.fill(x + 5, y + 1, x + 6, y + 2, color);
        g.fill(x + 6, y, x + 7, y + 1, color);
    }

    /** 像素锁（7×7）：进度还没解锁的分类 / 条目 */
    public static void lock(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y, x + 5, y + 1, color);
        g.fill(x + 1, y + 1, x + 2, y + 3, color);
        g.fill(x + 5, y + 1, x + 6, y + 3, color);
        g.fill(x, y + 3, x + 7, y + 7, color);
        g.fill(x + 3, y + 4, x + 4, y + 6, PLATE_DEEP);
    }

    /**
     * 按宽度截断并补省略号。
     *
     * @return 放得下的文本
     */
    public static String ellipsize(Font font, String text, int maxW) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (font.width(text) <= maxW) {
            return text;
        }
        int ell = font.width("…");
        if (maxW <= ell) {
            return "";
        }
        return font.plainSubstrByWidth(text, maxW - ell) + "…";
    }

    // ==================== 旧界面留下的图元（配方图、结构图用） ====================

    public static void border(GuiGraphics g, int x, int y, int width, int height, int color) {
        CodexTheme.border(g, x, y, width, height, color);
    }

    /**
     * 四角的 L 形饰角：只出现在转角，比整圈亮边克制，却足以让框从背景里立起来。
     *
     * @param reveal 0~1，从零长到满长
     */
    public static void corners(GuiGraphics g, int x, int y, int width, int height, int length, int color, float reveal) {
        int len = Math.max(1, (int) (length * Mth.clamp(reveal, 0f, 1f)));
        int right = x + width;
        int bottom = y + height;
        g.fill(x, y, x + len, y + 1, color);
        g.fill(x, y, x + 1, y + len, color);
        g.fill(right - len, y, right, y + 1, color);
        g.fill(right - 1, y, right, y + len, color);
        g.fill(x, bottom - 1, x + len, bottom, color);
        g.fill(x, bottom - len, x + 1, bottom, color);
        g.fill(right - len, bottom - 1, right, bottom, color);
        g.fill(right - 1, bottom - len, right, bottom, color);
    }

    /**
     * 18×18 的物品格：磨砂浅紫底 + 左上一道内高光 + 亮一档的细边，悬停时描边转品红、四角长出小饰角。
     *
     * @param hover 0~1 悬停程度
     */
    public static void slot(GuiGraphics g, int x, int y, float hover, float alpha) {
        frostCell(g, x, y, 18, fade(lerpColor(SLOT_EDGE, FOCUS, hover), alpha), alpha);
        if (hover > 0.01f) {
            corners(g, x - 1, y - 1, 20, 20, 4, fade(FOCUS, hover * alpha), 1f);
        }
    }

    /**
     * 物品格「有几种可选」的标记：右上角描边上一个 3 像素的薰衣草小三角（静止）。候选物品平时停在第一个，
     * 鼠标压在这张图上时才每秒轮换（见 {@link GuideFrame#cycling}），悬停出提示「N 种任选」
     */
    public static void slotAlternatives(GuiGraphics g, int x, int y, float alpha) {
        int c = fade(GLOW, alpha);
        g.fill(x + 15, y, x + 18, y + 1, c);
        g.fill(x + 16, y + 1, x + 18, y + 2, c);
        g.fill(x + 17, y + 2, x + 18, y + 3, c);
    }

    /**
     * 任意边长的磨砂格：底 {@link #SLOT}、左上内高光 {@link #SLOT_HI}、1px 描边（结构图缩小的格子也用它）
     *
     * @param edge 描边色（已乘好透明度）
     */
    public static void frostCell(GuiGraphics g, int x, int y, int size, int edge, float alpha) {
        g.fill(x, y, x + size, y + size, fade(SLOT, alpha));
        if (size >= 6) {
            int hi = fade(SLOT_HI, alpha);
            g.fill(x + 1, y + 1, x + size - 1, y + 2, hi);
            g.fill(x + 1, y + 2, x + 2, y + size - 1, hi);
        }
        border(g, x, y, size, size, edge);
    }

    /**
     * 格子上沿的一道极淡的护板嵌线色高光（首页格子、翻页卡、「下一步」条）：斜切的块从切角里面起笔
     *
     * @param inset 左右各缩进几像素（斜切块传切角边长）
     */
    public static void glassSheen(GuiGraphics g, int x, int y, int w, int inset, float alpha) {
        if (w - inset * 2 <= 0) {
            return;
        }
        g.fill(x + inset, y + 1, x + w - inset, y + 2, fade(withAlpha(CodexTheme.INLAY, 0x22), alpha));
    }

    /** 合成箭头未点亮部分的颜色：和物品格描边同一个浅紫，不透明，对护板 3.2:1（对最暗处 4.0:1） */
    private static final int ARROW_IDLE = CodexTheme.FROST_EDGE;

    /** 合成箭头的品红尖端有几列（只有这几列是高饱和色） */
    private static final int ARROW_TIP = 3;

    /**
     * 合成箭头（22×15）：箭身是低饱和的结构紫 —— 从 MRU 紫罗兰结构线 {@link #EDGE} 渐亮到薰衣草 {@link #GLOW}（上亮下深），
     * 只有最尖上 {@value #ARROW_TIP} 列是品红 {@link #FOCUS}（下半压向 {@link #FOCUS_DEEP}）。
     * 上一版整支箭头是蓝紫到品红的饱和渐变，配方图在阅读栏正中间，那一块的高饱和亮像素占了全页的一半，是最抢眼的东西。
     * 未点亮的部分是不透明的浅紫 {@link #ARROW_IDLE}：任何时刻都看得出箭头的形状（现在调用方恒传 1，整支点亮、不走进度）。
     *
     * @param progress 0~1，点亮到哪一列
     */
    public static void arrow(GuiGraphics g, int x, int y, float progress, float alpha) {
        int base = fade(ARROW_IDLE, alpha);
        int litTo = x + Math.round(22 * Mth.clamp(progress, 0f, 1f));
        for (int i = 0; i < 22; i++) {
            int top;
            int bottom;
            if (i < 14) {
                top = y + 5;
                bottom = y + 10;
            } else {
                int k = i - 14;
                top = y + k;
                bottom = y + 15 - k;
            }
            if (bottom <= top) {
                continue;
            }
            if (x + i < litTo) {
                int lit;
                int deep;
                if (i >= 22 - ARROW_TIP) {
                    lit = FOCUS;
                    deep = lerpColor(FOCUS, FOCUS_DEEP, 0.55f);
                } else {
                    lit = lerpColor(EDGE, GLOW, i / (float) (21 - ARROW_TIP));
                    deep = lerpColor(lit, PLATE_DEEP, 0.3f);
                }
                g.fillGradient(x + i, top, x + i + 1, bottom, fade(lit, alpha), fade(deep, alpha));
            } else {
                g.fill(x + i, top, x + i + 1, bottom, base);
            }
        }
    }

    /**
     * 平衡值色条：0~2 横向铺开，颜色是该平衡值的 MRU 颜色；配方允许的区间亮、区间外压暗，1.0（纯净）处一道刻度。
     */
    public static void balanceBar(GuiGraphics g, int x, int y, int width, int height, float min, float max, float alpha) {
        int segments = Math.max(8, width / 2);
        for (int i = 0; i < segments; i++) {
            int sx = x + i * width / segments;
            int ex = x + (i + 1) * width / segments;
            float balance = (i + 0.5f) / segments * MRUBalance.MAX;
            boolean inside = balance >= min && balance <= max;
            g.fill(sx, y, ex, y + height, fade(balanceColor(balance), alpha * (inside ? 0.95f : 0.18f)));
        }
        int from = x + Math.round(Mth.clamp(min / MRUBalance.MAX, 0f, 1f) * width);
        int to = x + Math.round(Mth.clamp(max / MRUBalance.MAX, 0f, 1f) * width);
        if (to > from) {
            border(g, from, y - 1, to - from, height + 2, fade(GLOW_BRIGHT, alpha * 0.8f));
        }
        int pure = x + width / 2;
        g.fill(pure, y + height, pure + 1, y + height + 2, fade(TEXT_DIM, alpha));
    }
}
