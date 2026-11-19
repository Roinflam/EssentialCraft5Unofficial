package com.flameforge.essentialcraft5.client.screen;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 创世神之杖面板「蓝图台」的版面：<b>纯函数</b>，输入界面尺寸（GUI 缩放之后的宽高）、当前页、档位和几段字的宽度，输出每个控件的整数矩形。
 * 渲染（{@link BuilderPanelScreen}）、点击判定、展示厅量测和 GameTest 都只看这里算出来的数，不另外凑坐标。原型是 {@code panel_proto/a/v5.html}，
 * 数照它量（台子最大 520 × 300、离屏幕边至少 6、左栏占四成夹在 150～200、页签 40 × 13、底条 30 高……），写在下面的常量里。
 * <p>
 * 规矩：
 * <ul>
 *   <li>坐标全是整数；居中一律 {@code x + (容器宽 - 内容宽) / 2} 向下取整，奇偶差出来的 1 像素统一留在右边 / 下边（内容偏左上）。
 *       等分用 {@link #split}：除不尽的像素从左边起每份多给 1。</li>
 *   <li>同一行的控件高度、上沿一致（中线一致）；文字只给框，字在框里按 {@code font.width} 量了再省略或折行，不出框。</li>
 *   <li>窄窗口（台子宽不到 {@value #WIDE_MIN} 时，即界面宽 427 以下，原型没有这一档）：左栏缩到三成多（104～150），尺寸那行不写单字标签，
 *       右边两列里窄的那一列从 96 缩到 80；
 *       内容高度不够时先去掉形状说明那两行，模块页的上限从五行换成两行小结、再不够就不写。
 *       台子比 {@value #MIN_FRAME_W} × {@value #MIN_FRAME_H} 还小（界面缩放 1 下的极小窗口）只画一块提示，写「窗口太小」。</li>
 * </ul>
 */
public final class BuilderPanelLayout {

    /** 台子离屏幕边至少这么多 */
    public static final int MARGIN = 6;
    /** 台子最大宽高（大窗口下不再变大，同原型） */
    public static final int MAX_W = 520;
    public static final int MAX_H = 300;
    /** 台子比这还小就只画「窗口太小」 */
    public static final int MIN_FRAME_W = 300;
    public static final int MIN_FRAME_H = 200;
    /** 台子宽不到这个算窄（界面宽 427 以下） */
    public static final int WIDE_MIN = 415;
    /** 台子内边距（左右栏、底条离台子边） */
    public static final int PAD = 7;
    /** 顶栏：一行控件的上沿、行高 */
    public static final int TITLE_TOP = 5;
    public static final int TITLE_H = 11;
    /** 顶栏下面那条分隔线、主体的上沿（相对台子） */
    public static final int RULE1 = 19;
    public static final int MAIN_TOP = 23;
    /** 主体下面留给底条的高度（分隔线 + 底条 + 下边距） */
    public static final int BOTTOM_ROOM = 38;
    /** 底条高、底条里按钮高 */
    public static final int BAR_H = 30;
    public static final int BAR_BTN_H = 20;
    /** 左右栏之间 */
    public static final int GAP = 6;
    /** 页签 */
    public static final int TAB_MIN_W = 40;
    public static final int TAB_H = 13;
    public static final int TAB_GAP = 2;
    /** 页签下面内容区的上沿（相对主体上沿） */
    public static final int CONTENT_TOP = 17;
    /** 小节标题高（字 8 + 1）、标题到下面内容的距离 */
    public static final int HEAD_H = 9;
    public static final int HEAD_GAP = 11;
    /** 行距（字 8 + 2） */
    public static final int LINE = 10;
    /** 材料、模块一行的高 */
    public static final int ROW = 22;
    /** 形状按钮高、间隔 */
    public static final int SHAPE_H = 28;
    public static final int SHAPE_GAP = 2;
    /** 模式一行的高、行距 */
    public static final int MODE_H = 13;
    public static final int MODE_PITCH = 14;
    /** 右边两列里窄的那一列（混铺、丢弃清单）宽：宽版 / 窄版 */
    public static final int SIDE_W = 96;
    public static final int SIDE_W_NARROW = 80;
    /** 两列之间（中间画一条 1 像素的竖线） */
    public static final int COL_GAP = 7;
    /** 混铺一行的行距、丢弃清单一格的间距 */
    public static final int MIX_PITCH = 18;
    public static final int SLOT = 18;
    public static final int SLOT_PITCH = 19;
    /** 小方按钮（加减、拆、关闭）边长 */
    public static final int MINI = 9;
    public static final int CLOSE = 11;
    /** 开关 */
    public static final int TOGGLE_W = 16;
    public static final int TOGGLE_H = 9;
    /** MRU 条 */
    public static final int MRU_BAR_W = 40;
    public static final int MRU_BAR_H = 5;
    /** 撤销、停下按钮的最小宽度（字更宽时加宽，左右各留 5） */
    public static final int UNDO_MIN_W = 66;
    public static final int STOP_MIN_W = 40;
    /** 进度那一块最宽、说明那一块至少留多宽 */
    public static final int PROGRESS_MAX_W = 168;
    public static final int NOTE_MIN_W = 80;
    /** 蓝图下面那块三行字的高 */
    public static final int INFO_H = 34;
    /** 滚动条宽（列表右边留出来） */
    public static final int SCROLLBAR = 3;
    public static final int SCROLL_ROOM = 5;

    /** 三页 */
    public enum Page {
        RANGE,
        BLOCK,
        MODULES
    }

    /** 一个矩形（界面坐标，整数） */
    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }

        public boolean intersects(Rect o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }

        public boolean inside(Rect o) {
            return x >= o.x && y >= o.y && x + w <= o.x + o.w && y + h <= o.y + o.h;
        }
    }

    /**
     * 算版面要的输入。几段字的宽度（界面像素，按 {@code font.width} 量）由调用方给：顶栏「蓝图台 档名 几阶」整段、「MRU」、MRU 数、
     * 三个页签里最宽的、五种模式名里最宽的、「撤销上一次」「停下」、尺寸那行的单字标签里最宽的。
     *
     * @param running    在施工（底条要留出进度那一块）
     * @param shapes     这一档能用的形状数（一阶 3、二三阶 5）
     * @param modes      这一档能用的模式数（一阶 1，只写一行「只能填充」）
     * @param slots      模块槽数
     * @param discardCandidates 丢弃清单里能挑的格数（决定网格要几行）
     */
    public record Inputs(int width, int height, Page page, int shapes, int modes, int slots, boolean running,
                         int titleWidth, int mruLabelWidth, int mruTextWidth, int tabLabelWidth, int modeLabelWidth,
                         int undoLabelWidth, int stopLabelWidth, int sizeLabelWidth, int discardCandidates) {
    }

    /** 一个带名字的框（测试和展示厅量测按名字认；parent 是它所在的那块，同一块里的框不许重叠；row 相同的框中线一致） */
    public record Box(String id, Rect rect, String parent, @Nullable String row) {
    }

    /** 混铺一行 */
    public record MixRow(Rect row, Rect icon, Rect minus, Rect value, Rect plus, Rect percent, Rect remove, Rect add) {
    }

    /** 模块一行 */
    public record ModuleRow(Rect row, Rect icon, Rect name, Rect desc, Rect toggle, Rect remove) {
    }

    // ------------------------------------------------------------------
    // 结果（都是界面坐标）
    // ------------------------------------------------------------------

    public final Inputs in;
    public final boolean tooSmall;
    public final boolean narrow;
    public final Rect frame;
    // 顶栏
    @Nullable
    public final Rect title;
    @Nullable
    public final Rect close;
    @Nullable
    public final Rect mruLabel;
    @Nullable
    public final Rect mruBar;
    @Nullable
    public final Rect mruText;
    /** 施工中顶栏中间写「先停下才能改」的那一段；放不下为 null */
    @Nullable
    public final Rect lockNote;
    public final int rule1Y;
    // 左栏
    @Nullable
    public final Rect left;
    @Nullable
    public final Rect blueprint;
    @Nullable
    public final Rect info;
    /** 蓝图下面三行：形状和要放格数、尺寸、MRU */
    public final List<Rect> infoLines;
    /** 尺寸那行的两组加减号（左边一组调第一个数、右边一组调第二个数，同原型「边 - +  长 - +」）和两个单字标签 */
    public final List<Rect> sizeMinus;
    public final List<Rect> sizePlus;
    public final List<Rect> sizeLabels;
    /** 尺寸那行左边写「12 × 12」的地方 */
    @Nullable
    public final Rect sizeText;
    // 右栏
    public final List<Rect> tabs;
    @Nullable
    public final Rect tabRule;
    @Nullable
    public final Rect content;
    // 范围页
    @Nullable
    public final Rect shapeHead;
    public final List<Rect> shapeButtons;
    @Nullable
    public final Rect shapeTip;
    @Nullable
    public final Rect modeHead;
    public final List<Rect> modeRows;
    /** 模式行右边那句说明；放不下的为空表 */
    public final List<Rect> modeDescs;
    /** 一阶：只写一行「只能填充」 */
    @Nullable
    public final Rect onlyFill;
    // 方块页
    @Nullable
    public final Rect listHead;
    @Nullable
    public final Rect listWell;
    /** 列表能滚的那块（含右边 {@value #SCROLL_ROOM} 像素的滚动条位置） */
    @Nullable
    public final Rect listView;
    @Nullable
    public final Rect columnRule;
    @Nullable
    public final Rect mixHead;
    @Nullable
    public final Rect mixBar;
    public final List<MixRow> mixRows;
    // 模块页
    @Nullable
    public final Rect moduleHead;
    public final List<ModuleRow> moduleRows;
    /** 上限：完整五行（limitsFull）或两行小结；没地方为 null */
    @Nullable
    public final Rect limits;
    public final boolean limitsFull;
    @Nullable
    public final Rect discardHead;
    @Nullable
    public final Rect discardGrid;
    public final int discardCols;
    @Nullable
    public final Rect discardNote;
    // 底条
    public final int rule2Y;
    @Nullable
    public final Rect bar;
    @Nullable
    public final Rect undo;
    @Nullable
    public final Rect undoNote;
    @Nullable
    public final Rect progress;
    @Nullable
    public final Rect stop;
    /** 「窗口太小」那一块 */
    @Nullable
    public final Rect tooSmallNote;

    private final List<Box> boxes = new ArrayList<>();

    private BuilderPanelLayout(Inputs in) {
        this.in = in;
        int fw = Math.min(in.width - 2 * MARGIN, MAX_W);
        int fh = Math.min(in.height - 2 * MARGIN, MAX_H);
        this.tooSmall = fw < MIN_FRAME_W || fh < MIN_FRAME_H;
        this.narrow = fw < WIDE_MIN;
        if (tooSmall) {
            int w = Math.max(1, Math.min(in.width - 4, 200));
            int h = Math.max(1, Math.min(in.height - 4, 40));
            this.frame = new Rect((in.width - w) / 2, (in.height - h) / 2, w, h);
            // 内缩 4，框本身不到 9 像素时缩得少一点（不出框）
            int d = Math.max(0, Math.min(4, Math.min((w - 1) / 2, (h - 1) / 2)));
            this.tooSmallNote = new Rect(frame.x + d, frame.y + d, w - 2 * d, h - 2 * d);
            add("frame", frame, "screen", null);
            add("tooSmallNote", tooSmallNote, "frame", null);
            title = close = mruLabel = mruBar = mruText = lockNote = null;
            rule1Y = rule2Y = 0;
            left = blueprint = info = sizeText = tabRule = content = null;
            infoLines = sizeMinus = sizePlus = sizeLabels = tabs = shapeButtons = modeRows = modeDescs = List.of();
            shapeHead = shapeTip = modeHead = onlyFill = listHead = listWell = listView = columnRule = mixHead = mixBar = null;
            mixRows = List.of();
            moduleHead = limits = discardHead = discardGrid = discardNote = null;
            moduleRows = List.of();
            limitsFull = false;
            discardCols = 0;
            bar = undo = undoNote = progress = stop = null;
            return;
        }
        this.tooSmallNote = null;
        int x0 = (in.width - fw) / 2;
        int y0 = (in.height - fh) / 2;
        this.frame = new Rect(x0, y0, fw, fh);
        add("frame", frame, "screen", null);

        // ---------------- 顶栏：左边标题，右边 [MRU 条 数] [关闭] ----------------
        int rowY = y0 + TITLE_TOP;
        this.close = new Rect(x0 + fw - PAD - CLOSE, rowY, CLOSE, CLOSE);
        int mruRight = close.x - 5;
        int mruTextW = Math.max(0, in.mruTextWidth);
        this.mruText = new Rect(mruRight - mruTextW, rowY, mruTextW, TITLE_H);
        this.mruBar = new Rect(mruText.x - 4 - MRU_BAR_W, rowY + (TITLE_H - MRU_BAR_H) / 2, MRU_BAR_W, MRU_BAR_H);
        this.mruLabel = new Rect(mruBar.x - 3 - in.mruLabelWidth, rowY, in.mruLabelWidth, TITLE_H);
        int titleX = x0 + PAD + 2;
        int titleW = Math.max(0, Math.min(in.titleWidth, mruLabel.x - 8 - titleX));
        this.title = new Rect(titleX, rowY, titleW, TITLE_H);
        int noteX = title.right() + 6;
        int noteW = mruLabel.x - 6 - noteX;
        this.lockNote = noteW >= 30 ? new Rect(noteX, rowY, noteW, TITLE_H) : null;
        add("title", title, "frame", "titleRow");
        if (lockNote != null) {
            add("lockNote", lockNote, "frame", "titleRow");
        }
        add("mruLabel", mruLabel, "frame", "titleRow");
        add("mruBar", mruBar, "frame", "titleRow");
        add("mruText", mruText, "frame", "titleRow");
        add("close", close, "frame", "titleRow");
        this.rule1Y = y0 + RULE1;

        // ---------------- 主体 ----------------
        int mainY = y0 + MAIN_TOP;
        int mainH = fh - MAIN_TOP - BOTTOM_ROOM;
        int px = x0 + PAD;
        int pw = narrow ? clamp(fw * 34 / 100, 104, 150) : clamp(fw * 40 / 100, 150, 200);
        int rx = px + pw + GAP;
        int rw = x0 + fw - PAD - rx;
        this.left = new Rect(px, mainY, pw, mainH);
        add("left", left, "frame", null);
        this.blueprint = new Rect(px + 4, mainY + 4, pw - 8, mainH - 8 - INFO_H - 2);
        this.info = new Rect(px + 5, mainY + mainH - INFO_H - 3, pw - 10, INFO_H);
        add("blueprint", blueprint, "left", null);
        add("info", info, "left", null);
        this.infoLines = List.of(new Rect(info.x, info.y + 1, info.w, HEAD_H), new Rect(info.x, info.y + 11, info.w, 11),
                new Rect(info.x, info.y + 24, info.w, HEAD_H));
        for (int i = 0; i < infoLines.size(); i++) {
            add("infoLine" + i, infoLines.get(i), "info", null);
        }
        // 尺寸那行：右起「标签 [-] [+]」两组，组间留 5；左边剩下的写尺寸
        Rect sizeLine = infoLines.get(1);
        List<Rect> minus = new ArrayList<>(2);
        List<Rect> plus = new ArrayList<>(2);
        List<Rect> labels = new ArrayList<>(2);
        int cx = sizeLine.right();
        int by = sizeLine.y + 1;
        // 窄版不写单字标签（宽 0，悬停提示照样有），组间留 3：把地方让给左边的「12 × 12」
        int labelW = narrow ? 0 : in.sizeLabelWidth;
        for (int k = 1; k >= 0; k--) {
            cx -= MINI;
            Rect p = new Rect(cx, by, MINI, MINI);
            cx -= MINI + 1;
            Rect m = new Rect(cx, by, MINI, MINI);
            cx -= labelW + (narrow ? 0 : 1);
            Rect lab = new Rect(cx, by, labelW, MINI);
            cx -= narrow ? 3 : 5;
            minus.add(0, m);
            plus.add(0, p);
            labels.add(0, lab);
        }
        this.sizeMinus = List.copyOf(minus);
        this.sizePlus = List.copyOf(plus);
        this.sizeLabels = List.copyOf(labels);
        this.sizeText = new Rect(sizeLine.x, sizeLine.y, Math.max(0, cx - sizeLine.x), sizeLine.h);
        for (int k = 0; k < 2; k++) {
            add("sizeLabel" + k, sizeLabels.get(k), "infoLine1", "sizeRow");
            add("sizeMinus" + k, sizeMinus.get(k), "infoLine1", "sizeRow");
            add("sizePlus" + k, sizePlus.get(k), "infoLine1", "sizeRow");
        }
        add("sizeText", sizeText, "infoLine1", null);

        // 页签
        int tabW = Math.max(TAB_MIN_W, in.tabLabelWidth + 8);
        if (3 * tabW + 2 * TAB_GAP > rw) {
            tabW = (rw - 2 * TAB_GAP) / 3;
        }
        List<Rect> tabList = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            Rect t = new Rect(rx + i * (tabW + TAB_GAP), mainY, tabW, TAB_H);
            tabList.add(t);
            add("tab" + i, t, "frame", "tabRow");
        }
        this.tabs = List.copyOf(tabList);
        int tabsRight = tabList.get(2).right() + TAB_GAP;
        this.tabRule = new Rect(tabsRight, mainY + TAB_H - 1, Math.max(0, rx + rw - tabsRight), 1);
        int cy = mainY + CONTENT_TOP;
        int ch = mainH - CONTENT_TOP;
        this.content = new Rect(rx, cy, rw, ch);
        add("content", content, "frame", null);

        // ---------------- 三页 ----------------
        Rect shapeHeadR = null;
        Rect shapeTipR = null;
        Rect modeHeadR = null;
        Rect onlyFillR = null;
        List<Rect> shapeBtns = new ArrayList<>();
        List<Rect> modeRowList = new ArrayList<>();
        List<Rect> modeDescList = new ArrayList<>();
        Rect listHeadR = null;
        Rect listWellR = null;
        Rect listViewR = null;
        Rect columnRuleR = null;
        Rect mixHeadR = null;
        Rect mixBarR = null;
        List<MixRow> mixRowList = new ArrayList<>();
        Rect moduleHeadR = null;
        List<ModuleRow> moduleRowList = new ArrayList<>();
        Rect limitsR = null;
        boolean full = false;
        Rect discardHeadR = null;
        Rect discardGridR = null;
        Rect discardNoteR = null;
        int cols = 0;
        int sideW = narrow ? SIDE_W_NARROW : SIDE_W;
        int w1 = rw - sideW - COL_GAP;
        int sideX = rx + w1 + COL_GAP;
        switch (in.page) {
            case RANGE -> {
                shapeHeadR = new Rect(rx, cy, rw, HEAD_H);
                add("shapeHead", shapeHeadR, "content", null);
                int n = Math.max(1, in.shapes);
                for (int[] part : split(rx, rw, n, SHAPE_GAP)) {
                    Rect b = new Rect(part[0], cy + HEAD_GAP, part[1], SHAPE_H);
                    shapeBtns.add(b);
                    add("shape" + (shapeBtns.size() - 1), b, "content", "shapeRow");
                }
                int afterShapes = cy + HEAD_GAP + SHAPE_H;
                int modesH = HEAD_GAP + (in.modes > 1 ? (in.modes - 1) * MODE_PITCH + MODE_H : 14);
                // 形状说明两行（20）+ 上下各留一点；内容区不够高时不写（悬停提示照样有）
                boolean tip = ch - (afterShapes + 2 + 20 + 5 - cy) >= modesH;
                if (tip) {
                    shapeTipR = new Rect(rx, afterShapes + 2, rw, 20);
                    add("shapeTip", shapeTipR, "content", null);
                }
                int modeY = tip ? shapeTipR.bottom() + 5 : afterShapes + 2;
                modeHeadR = new Rect(rx, modeY, rw, HEAD_H);
                add("modeHead", modeHeadR, "content", null);
                if (in.modes > 1) {
                    int descX = rx + 4 + 9 + 4 + in.modeLabelWidth + 8;
                    int descW = rx + rw - 4 - descX;
                    for (int i = 0; i < in.modes; i++) {
                        Rect r = new Rect(rx, modeY + HEAD_GAP + i * MODE_PITCH, rw, MODE_H);
                        modeRowList.add(r);
                        add("mode" + i, r, "content", null);
                        if (descW >= 40) {
                            Rect d = new Rect(descX, r.y, descW, MODE_H);
                            modeDescList.add(d);
                            add("modeDesc" + i, d, "mode" + i, null);
                        }
                    }
                } else {
                    onlyFillR = new Rect(rx, modeY + HEAD_GAP, rw, 14);
                    add("onlyFill", onlyFillR, "content", null);
                }
            }
            case BLOCK -> {
                columnRuleR = new Rect(rx + w1 + COL_GAP / 2, cy, 1, ch);
                listHeadR = new Rect(rx, cy, w1, HEAD_H);
                listWellR = new Rect(rx, cy + HEAD_GAP, w1, ch - HEAD_GAP);
                listViewR = new Rect(rx + 1, cy + HEAD_GAP + 1, w1 - 2, ch - HEAD_GAP - 2);
                add("listHead", listHeadR, "content", null);
                add("listWell", listWellR, "content", null);
                add("columnRule", columnRuleR, "content", null);
                mixHeadR = new Rect(sideX, cy, sideW, HEAD_H);
                mixBarR = new Rect(sideX, cy + HEAD_GAP, sideW, 6);
                add("mixHead", mixHeadR, "content", null);
                add("mixBar", mixBarR, "content", null);
                for (int i = 0; i < 4; i++) {
                    int ry = cy + 20 + i * MIX_PITCH;
                    Rect row = new Rect(sideX, ry, sideW, MIX_PITCH - 1);
                    Rect icon = new Rect(sideX, ry, 16, 16);
                    Rect minusR = new Rect(sideX + 20, ry + 4, MINI, MINI);
                    Rect value = new Rect(minusR.right(), ry + 4, MINI, MINI);
                    Rect plusR = new Rect(value.right(), ry + 4, MINI, MINI);
                    Rect remove = new Rect(sideX + sideW - MINI, ry + 4, MINI, MINI);
                    Rect pct = new Rect(plusR.right() + 4, ry + 4, Math.max(0, remove.x - 3 - plusR.right() - 4), MINI);
                    Rect addR = new Rect(sideX, ry + 2, sideW, 13);
                    MixRow mixRow = new MixRow(row, icon, minusR, value, plusR, pct, remove, addR);
                    mixRowList.add(mixRow);
                    String rowId = "mixRow" + i;
                    add(rowId, row, "content", null);
                    add(rowId + "Icon", icon, rowId, null);
                    add(rowId + "Minus", minusR, rowId, rowId);
                    add(rowId + "Value", value, rowId, rowId);
                    add(rowId + "Plus", plusR, rowId, rowId);
                    add(rowId + "Pct", pct, rowId, rowId);
                    add(rowId + "Remove", remove, rowId, rowId);
                }
            }
            case MODULES -> {
                columnRuleR = new Rect(rx + w1 + COL_GAP / 2, cy, 1, ch);
                add("columnRule", columnRuleR, "content", null);
                moduleHeadR = new Rect(rx, cy, w1, HEAD_H);
                add("moduleHead", moduleHeadR, "content", null);
                int slots = Math.max(0, in.slots);
                for (int i = 0; i < slots; i++) {
                    int ry = cy + HEAD_GAP + i * (ROW + 1);
                    Rect row = new Rect(rx, ry, w1, ROW);
                    Rect remove = new Rect(rx + w1 - 4 - MINI, ry + (ROW - MINI) / 2, MINI, MINI);
                    Rect toggle = new Rect(remove.x - 3 - TOGGLE_W, ry + (ROW - TOGGLE_H) / 2, TOGGLE_W, TOGGLE_H);
                    Rect icon = new Rect(rx + 3, ry + 3, 16, 16);
                    Rect name = new Rect(rx + 23, ry + 2, Math.max(0, toggle.x - 3 - rx - 23), HEAD_H);
                    Rect desc = new Rect(rx + 23, ry + 12, Math.max(0, toggle.x - 3 - rx - 23), HEAD_H);
                    ModuleRow moduleRow = new ModuleRow(row, icon, name, desc, toggle, remove);
                    moduleRowList.add(moduleRow);
                    String rowId = "moduleRow" + i;
                    add(rowId, row, "content", null);
                    add(rowId + "Icon", icon, rowId, null);
                    add(rowId + "Name", name, rowId, null);
                    add(rowId + "Desc", desc, rowId, null);
                    add(rowId + "Toggle", toggle, rowId, rowId);
                    add(rowId + "Remove", remove, rowId, rowId);
                }
                int ly = cy + HEAD_GAP + slots * (ROW + 1) + 4;
                int room = cy + ch - ly;
                if (room >= HEAD_GAP + 5 * LINE - 2 && w1 >= 150) {
                    limitsR = new Rect(rx, ly, w1, HEAD_GAP + 5 * LINE - 2);
                    full = true;
                } else if (room >= 2 * LINE - 1) {
                    limitsR = new Rect(rx, ly, w1, 2 * LINE - 1);
                }
                if (limitsR != null) {
                    add("limits", limitsR, "content", null);
                }
                discardHeadR = new Rect(sideX, cy, sideW, HEAD_H);
                add("discardHead", discardHeadR, "content", null);
                cols = Math.max(1, (sideW - SCROLL_ROOM + 1) / SLOT_PITCH);
                int rows = Math.max(1, (Math.max(0, in.discardCandidates) + cols - 1) / cols);
                int gh = Math.max(SLOT, Math.min(rows * SLOT_PITCH - 1, ch - HEAD_GAP - 12));
                discardGridR = new Rect(sideX, cy + HEAD_GAP, sideW, gh);
                discardNoteR = new Rect(sideX, discardGridR.bottom() + 3, sideW, HEAD_H);
                add("discardGrid", discardGridR, "content", null);
                add("discardNote", discardNoteR, "content", null);
            }
            default -> {
            }
        }
        this.shapeHead = shapeHeadR;
        this.shapeButtons = List.copyOf(shapeBtns);
        this.shapeTip = shapeTipR;
        this.modeHead = modeHeadR;
        this.modeRows = List.copyOf(modeRowList);
        this.modeDescs = List.copyOf(modeDescList);
        this.onlyFill = onlyFillR;
        this.listHead = listHeadR;
        this.listWell = listWellR;
        this.listView = listViewR;
        this.columnRule = columnRuleR;
        this.mixHead = mixHeadR;
        this.mixBar = mixBarR;
        this.mixRows = List.copyOf(mixRowList);
        this.moduleHead = moduleHeadR;
        this.moduleRows = List.copyOf(moduleRowList);
        this.limits = limitsR;
        this.limitsFull = full;
        this.discardHead = discardHeadR;
        this.discardGrid = discardGridR;
        this.discardNote = discardNoteR;
        this.discardCols = cols;

        // ---------------- 底条：[撤销上一次] 说明 [进度] [停下] ----------------
        this.rule2Y = y0 + fh - BAR_H - 6;
        this.bar = new Rect(x0 + PAD, y0 + fh - BAR_H - 4, fw - 2 * PAD, BAR_H);
        add("bar", bar, "frame", null);
        int btnY = bar.y + (BAR_H - BAR_BTN_H) / 2;
        int undoW = Math.max(UNDO_MIN_W, in.undoLabelWidth + 10);
        int stopW = Math.max(STOP_MIN_W, in.stopLabelWidth + 10);
        this.undo = new Rect(bar.x, btnY, undoW, BAR_BTN_H);
        this.stop = new Rect(bar.right() - stopW, btnY, stopW, BAR_BTN_H);
        int mx = undo.right() + 5;
        int mw = stop.x - 5 - mx;
        int pwidth = in.running ? Math.max(0, Math.min(PROGRESS_MAX_W, mw - NOTE_MIN_W)) : 0;
        int dw = mw - (pwidth > 0 ? pwidth + 6 : 0);
        this.undoNote = new Rect(mx, bar.y, Math.max(0, dw), BAR_H);
        this.progress = pwidth > 0 ? new Rect(mx + dw + 6, bar.y, pwidth, BAR_H) : null;
        add("undo", undo, "bar", "barRow");
        add("undoNote", undoNote, "bar", null);
        if (progress != null) {
            add("progress", progress, "bar", null);
        }
        add("stop", stop, "bar", "barRow");
    }

    /** 按输入算一份版面 */
    public static BuilderPanelLayout compute(Inputs in) {
        return new BuilderPanelLayout(in);
    }

    /** 所有带名字的框（按添加顺序） */
    public List<Box> boxes() {
        return Collections.unmodifiableList(boxes);
    }

    /** 按名字找框；没有返回 null */
    @Nullable
    public Rect box(String id) {
        for (Box box : boxes) {
            if (box.id().equals(id)) {
                return box.rect();
            }
        }
        return null;
    }

    /** 一行字写下整份版面（展示厅打进日志量对齐用）：{@code 宽x高 页 narrow|wide|tooSmall | id x,y,w,h; …} */
    public String describe() {
        StringBuilder out = new StringBuilder();
        out.append(in.width).append('x').append(in.height).append(' ').append(in.page).append(' ')
                .append(tooSmall ? "tooSmall" : narrow ? "narrow" : "wide").append(" |");
        for (Box box : boxes) {
            Rect r = box.rect();
            out.append(' ').append(box.id()).append(' ').append(r.x).append(',').append(r.y).append(',').append(r.w).append(',').append(r.h).append(';');
        }
        return out.toString();
    }

    private void add(String id, Rect rect, String parent, @Nullable String row) {
        boxes.add(new Box(id, rect, parent, row));
    }

    /** 把一段宽度分成 n 份、中间留 gap；除不尽的像素从左边起每份多给 1。返回每份的 {x, 宽} */
    public static List<int[]> split(int x, int w, int n, int gap) {
        int total = w - gap * (n - 1);
        int base = Math.floorDiv(total, n);
        int rem = total - base * n;
        List<int[]> out = new ArrayList<>(n);
        int cx = x;
        for (int i = 0; i < n; i++) {
            int cw = base + (i < rem ? 1 : 0);
            out.add(new int[] {cx, cw});
            cx += cw + gap;
        }
        return out;
    }

    /** 在 [x, x + w) 里居中放宽 inner 的东西，返回它的左边（差出来的奇数像素留在右边） */
    public static int center(int x, int w, int inner) {
        return x + Math.floorDiv(w - inner, 2);
    }

    /**
     * 字宽（{@code font.width}）末尾不着墨的像素：字距 1，加上中文字形右边留的 1。按字宽直接居中，中文整体偏左 1 像素
     * （截图量：两字页签左 11 右 13）；减掉这 2 像素再居中，中文正好居中，英文和数字（末尾只有字距那 1 像素）左右留白相差不超过 1 像素。
     */
    public static final int TEXT_TAIL = 2;

    /** 一行字在 [x, x + w) 里水平居中时的起点：按着墨的宽居中（见 {@link #TEXT_TAIL}）；字比框宽时从框左边起 */
    public static int centerText(int x, int w, int textWidth) {
        return Math.max(x, center(x, w, Math.max(0, textWidth - TEXT_TAIL)));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
