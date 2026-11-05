package com.flameforge.essentialcraft5.client.guide;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.flameforge.essentialcraft5.client.guide.GuideView.CatView;
import com.flameforge.essentialcraft5.client.guide.GuideView.EntryView;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 阅读页左栏：目录（分组折叠列表）或搜索结果
 *
 * <h3>为什么是「只展开当前分类」的折叠目录</h3>
 * <p>二十多个分类、一百多篇，全部展开要滚好几屏，找别的分类得先滚过当前分类的所有条目；
 * 只放一排分类图标又得靠悬停才知道哪个是哪个。折叠目录两头都顾到：
 * 所有分类的名字始终在眼前，<b>点一下分类名就直接跳到该分类第一篇</b>（切换一步到位），
 * 当前分类自动展开、其余收起，树线把条目挂在分类下面，层级一眼可见。</p>
 *
 * <p>行在切换时一次性拍平成 {@link Row} 序列、标签按宽度截好，渲染时只遍历可见的行、不做任何字符串运算。</p>
 *
 * <h3>EC5：分区与步骤标记</h3>
 * <p>分类按「入门 / 主线 / 支线」分区，区名是一行不可点的小标题；主线分类前写章号。条目行右侧标步骤状态：
 * 做完了打 ✓（绿），全书的下一步是一枚品红的小箭头，可选步骤标题压暗，被进度锁着的画一把锁。</p>
 */
@OnlyIn(Dist.CLIENT)
final class GuideSidebar {

    private static final int HEAD_H = 15;
    /** 分区小标题行 */
    private static final int GROUP_H = 13;
    private static final int CAT_H = 18;
    private static final int ENTRY_H = 15;
    private static final int RESULT_H = 23;
    private static final int SCROLLBAR_ZONE = 6;

    enum Kind { CAT, ENTRY, RESULT, GROUP }

    /**
     * 一行
     */
    static final class Row {
        final Kind kind;
        @Nullable
        final CatView cat;
        @Nullable
        final EntryView entry;
        final int y;
        final int h;
        final String label;
        final String sub;
        final String count;
        /** count 的字宽（建行时量好，渲染时右对齐用） */
        final int countW;
        /** 是不是本分类最后一个条目（树线画到一半） */
        final boolean last;
        /** 搜索结果的序号 */
        final int resultIndex;

        Row(Kind kind, @Nullable CatView cat, @Nullable EntryView entry, int y, int h, String label, String sub,
            String count, int countW, boolean last, int resultIndex) {
            this.kind = kind;
            this.cat = cat;
            this.entry = entry;
            this.y = y;
            this.h = h;
            this.label = label;
            this.sub = sub;
            this.count = count;
            this.countW = countW;
            this.last = last;
            this.resultIndex = resultIndex;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    @Nullable
    private GuideView view;
    private int contentH;
    private boolean searchMode;
    private String headLabel = "";
    private String headCount = "";
    private int headCountW;
    /** 表头标题实际画出来的样子（和计数挤不下时截掉尾巴，{@link GuideTextFit#clip}）；标题、计数、栏宽变了才重算 */
    private String headShown = "";
    private String headShownFor = "";
    private int headShownRoom = -1;
    /** 没有搜索结果时的提示（按栏宽折好行） */
    private List<String> emptyLines = List.of();

    int x;
    int y;
    int w;
    int h;
    float scroll;
    float target;
    private final GuideScrollDrag bar = new GuideScrollDrag();

    void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    boolean isSearchMode() {
        return searchMode;
    }

    private int listTop() {
        return y + HEAD_H + 2;
    }

    private int listH() {
        return h - HEAD_H - 2;
    }

    // ==================== 构建 ====================

    /**
     * 目录模式：全部分类，只展开当前分类
     */
    void showTree(GuideView view, @Nullable CatView expanded, Font font) {
        boolean modeChanged = searchMode;
        searchMode = false;
        this.view = view;
        rows.clear();
        int rowW = w - SCROLLBAR_ZONE;
        int cy = 0;
        GuideContent.Group group = null;
        for (CatView cat : view.categories) {
            if (cat.group() != group) {
                group = cat.group();
                String label = I18n.get(switch (group) {
                    case START -> "guide.essentialcraft5.ui.side.start";
                    case MAIN -> "guide.essentialcraft5.ui.side.main";
                    case SIDE -> "guide.essentialcraft5.ui.side.side";
                });
                cy += rows.isEmpty() ? 0 : 3;
                rows.add(new Row(Kind.GROUP, null, null, cy, GROUP_H, GuideTheme.ellipsize(font, label, rowW - 12), "",
                        "", 0, false, -1));
                cy += GROUP_H;
            }
            String count = String.valueOf(cat.entries.size());
            int countW = font.width(count);
            int labelW = rowW - 22 - countW - 8;
            // 主线分类前写章号（「01 元素」）；窄栏里完整的「第一章 · 元素」放不下
            String label = cat.chapter > 0 ? String.format("%02d %s", cat.chapter, cat.shortTitle()) : cat.shortTitle();
            rows.add(new Row(Kind.CAT, cat, null, cy, CAT_H, GuideTheme.ellipsize(font, label, labelW), "",
                    count, countW, false, -1));
            cy += CAT_H;
            if (expanded != null && cat == expanded) {
                for (int i = 0; i < cat.entries.size(); i++) {
                    EntryView ev = cat.entries.get(i);
                    rows.add(new Row(Kind.ENTRY, cat, ev, cy, ENTRY_H,
                            GuideTheme.ellipsize(font, ev.title(), rowW - 34 - 4 - (ev.entry.isStep() || ev.entry.advancement != null ? 9 : 0)),
                            "", "", 0, i == cat.entries.size() - 1, -1));
                    cy += ENTRY_H;
                }
                cy += 3;
            }
        }
        contentH = cy;
        headLabel = I18n.get("guide.essentialcraft5.ui.contents");
        headCount = I18n.get("guide.essentialcraft5.ui.entry_count", view.entryList.size());
        headCountW = font.width(headCount);
        if (modeChanged) {
            scroll = target = 0;
        }
        clamp();
    }

    /**
     * 搜索模式：命中列表（带分类名与上下文）
     *
     * @param text 排版器：「没有结果」的提示要按词、按避头尾规则折行
     */
    void showResults(List<GuideSearch.Hit> hits, GuideTextLayout text) {
        Font font = text.font();
        boolean modeChanged = !searchMode;
        searchMode = true;
        rows.clear();
        int rowW = w - SCROLLBAR_ZONE;
        int cy = 0;
        for (int i = 0; i < hits.size(); i++) {
            GuideSearch.Hit hit = hits.get(i);
            EntryView ev = hit.entry;
            String sub = hit.snippet.isEmpty() ? ev.cat.title() : ev.cat.title() + " · " + hit.snippet;
            rows.add(new Row(Kind.RESULT, ev.cat, ev, cy, RESULT_H,
                    GuideTheme.ellipsize(font, ev.title(), rowW - 24 - 4),
                    GuideTheme.ellipsize(font, sub, rowW - 24 - 4), "", 0, false, i));
            cy += RESULT_H;
        }
        contentH = cy;
        if (hits.isEmpty()) {
            // 窄栏里一句话可能放不下：只在结果变化时折一次。走正文的折行规则 ——
            // 按像素硬切的话英文会从单词中间断开（"No matching entries. T" / "ry another…"），中文逗号也可能落到行首
            emptyLines = text.wrapPlain(I18n.get("guide.essentialcraft5.ui.no_results"), w - 16, 4);
        }
        headLabel = I18n.get("guide.essentialcraft5.ui.results");
        headCount = I18n.get("guide.essentialcraft5.ui.result_count", hits.size());
        headCountW = font.width(headCount);
        // 每次输入结果都会变：回到顶部，第一条永远在眼前
        scroll = target = 0;
        if (modeChanged) {
            clamp();
        }
    }

    // ==================== 滚动 ====================

    private float maxScroll() {
        return Math.max(0, contentH - listH() + 4);
    }

    private void clamp() {
        float max = maxScroll();
        target = Math.max(0, Math.min(max, target));
        scroll = Math.max(0, Math.min(max, scroll));
    }

    /**
     * 把某篇（目录模式）滚进可视区
     */
    void reveal(@Nullable EntryView ev) {
        if (ev == null) {
            return;
        }
        for (Row row : rows) {
            if (row.entry == ev) {
                revealRow(row);
                return;
            }
        }
    }

    /**
     * 把第 index 条结果滚进可视区
     */
    void revealResult(int index) {
        for (Row row : rows) {
            if (row.resultIndex == index) {
                revealRow(row);
                return;
            }
        }
    }

    private void revealRow(Row row) {
        int lh = listH();
        int top = row.y;
        if (row.kind == Kind.ENTRY && row.entry != null) {
            // 目录模式下连同所属分类的标题行一起带进来（放得下的话），否则看不到自己在哪个分类里
            int catTop = row.y - row.entry.index * ENTRY_H - CAT_H;
            if (row.y + row.h - catTop <= lh) {
                top = catTop;
            }
        }
        if (top < target) {
            target = top;
        } else if (row.y + row.h > target + lh) {
            target = row.y + row.h - lh + 4;
        }
        clamp();
    }

    /**
     * 窗口尺寸变了、重建了列表之后，把滚动位置放回原处（{@link #showResults} 会把它归零）
     */
    void restoreScroll(float to) {
        scroll = target = to;
        clamp();
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx, my)) {
            return false;
        }
        target -= (float) (delta * 30);
        clamp();
        return true;
    }

    private boolean overScrollbar(double mx, double my) {
        int top = listTop();
        return maxScroll() > 0 && mx >= x + w - SCROLLBAR_ZONE && mx < x + w && my >= top && my < top + listH();
    }

    /** 按在滚动条上：点轨道跳过去 / 抓住滑块（与正文的滚动条同一手感） */
    boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !overScrollbar(mx, my)) {
            return false;
        }
        scroll = target = bar.press(my, listTop() + 1, listH() - 2, scroll, maxScroll());
        return true;
    }

    boolean mouseDragged(double my, int button) {
        if (!bar.dragging()) {
            return false;
        }
        if (button != 0) {
            bar.release();
            return false;
        }
        scroll = target = bar.drag(my, listH() - 2, maxScroll());
        return true;
    }

    void release() {
        bar.release();
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * 鼠标下的行
     */
    @Nullable
    Row rowAt(double mx, double my) {
        int top = listTop();
        if (mx < x || mx >= x + w - SCROLLBAR_ZONE || my < top || my >= top + listH()) {
            return null;
        }
        double local = my - top + scroll;
        for (Row row : rows) {
            if (local >= row.y && local < row.y + row.h) {
                return row.kind == Kind.GROUP ? null : row;
            }
        }
        return null;
    }

    int resultCount() {
        return searchMode ? rows.size() : 0;
    }

    @Nullable
    EntryView resultAt(int index) {
        for (Row row : rows) {
            if (row.resultIndex == index) {
                return row.entry;
            }
        }
        return null;
    }

    /**
     * 某一篇在左栏里的那一行现在画在屏幕哪一行（打勾电弧用）：目录模式、这一行完整地在可视范围里才有，否则 {@link Integer#MIN_VALUE}
     */
    int entryRowY(EntryView ev) {
        if (searchMode) {
            return Integer.MIN_VALUE;
        }
        int top = listTop();
        int base = top - Math.round(scroll);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.entry == ev && row.kind == Kind.ENTRY) {
                int ry = base + row.y;
                return ry >= top && ry + row.h <= top + listH() ? ry : Integer.MIN_VALUE;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** 条目行右侧步骤标记（✓ / 箭头 / 锁）的左边 */
    int markX() {
        return x + (w - SCROLLBAR_ZONE) - 10;
    }

    /** 条目行文字的左边（图标右边，见 drawEntry 的 left + 19）：打勾电弧不越过 */
    int rowTextLeft() {
        return x + 16 + 19;
    }

    /** 条目行的右边（滚动条那一列的左边）：打勾电弧不越过 */
    int rowRight() {
        return x + w - SCROLLBAR_ZONE;
    }

    // ==================== 绘制 ====================

    /**
     * @param r       绘制上下文
     * @param current 当前条目
     * @param cursor  键盘选中的搜索结果序号（-1 表示没有）
     * @param delta   距上一帧的秒数
     * @param offsetY 开合动画的位移
     */
    void render(GuideRender r, @Nullable EntryView current, int cursor, float delta, int offsetY) {
        GuiGraphics g = r.g;
        scroll = GuideTheme.approach(scroll, target, delta, 18f);

        // 左栏底色比右栏深一档：导航与正文分成两块，视线不会在两边来回串
        g.fill(x, y, x + w, y + h, GuideTheme.SIDEBAR_BG);
        g.fill(x + w, y, x + w + 1, y + h, GuideTheme.withAlpha(GuideTheme.EDGE, 0xC0));

        // 栏头：「目录 / 搜索结果」+ 计数。窄栏、英文、条目数到三位数时两样会叠在一起（427 宽的「Contents」和「291 entries」），
        // 标题截到计数左边留 4 像素
        int headRoom = w - SCROLLBAR_ZONE - 2 - headCountW - 4 - 6;
        if (headRoom != headShownRoom || !headLabel.equals(headShownFor)) {
            headShown = GuideTextFit.clip(headLabel, headRoom, r.font::width);
            headShownFor = headLabel;
            headShownRoom = headRoom;
        }
        g.drawString(r.font, headShown, x + 6, y + 4, searchMode ? GuideTheme.GLOW : GuideTheme.MIST, false);
        g.drawString(r.font, headCount, x + w - SCROLLBAR_ZONE - 2 - headCountW, y + 4,
                searchMode && rows.isEmpty() ? GuideTheme.TAG_W : GuideTheme.FAINT, false);
        g.fill(x + 4, y + HEAD_H, x + w - 4, y + HEAD_H + 1, GuideTheme.withAlpha(GuideTheme.EDGE, 0xA0));

        int top = listTop();
        int lh = listH();
        if (rows.isEmpty() && searchMode) {
            int ly = top + 10;
            for (String s : emptyLines) {
                g.drawString(r.font, s, x + 8, ly, GuideTheme.FAINT, false);
                ly += GuideTheme.LINE_H;
            }
            return;
        }

        g.enableScissor(x, top + offsetY, x + w, top + lh + offsetY);
        int base = top - Math.round(scroll);
        Row hovered = r.mouseActive ? rowAt(r.mouseX, r.mouseY) : null;
        int rowW = w - SCROLLBAR_ZONE;
        for (Row row : rows) {
            int ry = base + row.y;
            if (ry + row.h < top) {
                continue;
            }
            if (ry > top + lh) {
                break;
            }
            boolean hover = row == hovered;
            switch (row.kind) {
                case CAT -> drawCat(r, row, ry, rowW, hover, current);
                case ENTRY -> drawEntry(r, row, ry, rowW, hover, current);
                case RESULT -> drawResult(r, row, ry, rowW, hover, current, cursor);
                case GROUP -> drawGroup(r, row, ry, rowW);
            }
        }
        g.disableScissor();

        float max = maxScroll();
        if (max > 0) {
            boolean active = bar.dragging() || (r.mouseActive && overScrollbar(r.mouseX, r.mouseY));
            CodexTheme.scrollbar(g, x + w - SCROLLBAR_ZONE + 2, top + 1, lh - 2, scroll, max, active);
        }
    }

    private void drawCat(GuideRender r, Row row, int ry, int rowW, boolean hover, @Nullable EntryView current) {
        GuiGraphics g = r.g;
        boolean active = current != null && current.cat == row.cat;
        if (active) {
            g.fill(x, ry, x + rowW, ry + row.h, GuideTheme.withAlpha(GuideTheme.GLOW, 0x14));
            g.fill(x, ry, x + 2, ry + row.h, GuideTheme.GLOW);
        } else if (hover) {
            GuideTheme.gradientRow(g, x, ry, rowW, row.h, GuideTheme.withAlpha(GuideTheme.FOCUS, 0x26));
        }
        icon(g, row.cat.icon(), x + 5, ry + 3);
        int color = active ? GuideTheme.PEARL : hover ? GuideTheme.PEARL : GuideTheme.MIST;
        g.drawString(r.font, row.label, x + 21, ry + 5, color, active);
        g.drawString(r.font, row.count, x + rowW - 4 - row.countW, ry + 5, active ? GuideTheme.GLOW : GuideTheme.FAINT, false);
    }

    /**
     * 分区小标题：薰衣草小字 + 往右的紫罗兰细线，不可点。字不透明：叠在左栏暗底上 7.2:1
     * （原先 alpha 0xB0 只有 4.1:1，低于次要字的 4.5:1）；和条目行的层级靠缩进、细线和不带图标区分
     */
    private void drawGroup(GuideRender r, Row row, int ry, int rowW) {
        GuiGraphics g = r.g;
        g.drawString(r.font, row.label, x + 5, ry + 3, GuideTheme.GLOW, false);
        int lx = x + 5 + r.font.width(row.label) + 4;
        if (lx < x + rowW - 4) {
            g.fill(lx, ry + 7, x + rowW - 4, ry + 8, GuideTheme.withAlpha(GuideTheme.EDGE, 0x80));
        }
    }

    private void drawEntry(GuideRender r, Row row, int ry, int rowW, boolean hover, @Nullable EntryView current) {
        GuiGraphics g = r.g;
        boolean selected = row.entry == current;
        // 树线：把条目挂在分类下面
        int tx = x + 10;
        int treeColor = GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF);
        g.fill(tx, ry, tx + 1, row.last ? ry + row.h / 2 + 1 : ry + row.h, treeColor);
        g.fill(tx + 1, ry + row.h / 2, tx + 5, ry + row.h / 2 + 1, treeColor);
        int left = x + 16;
        if (selected) {
            g.fill(left, ry, x + rowW, ry + row.h, GuideTheme.withAlpha(GuideTheme.FOCUS, 0x24));
            // 选中行一根静止的品红竖条：视线离开再回来时靠它找回当前位置。上一版它会缓慢呼吸（2.4 秒），
            // 就在左栏里、读正文时的余光范围内一直明暗变化 —— 左栏和阅读栏里不放常驻动画
            g.fill(left, ry, left + 2, ry + row.h, GuideTheme.FOCUS);
        } else if (hover) {
            GuideTheme.gradientRow(g, left, ry, x + rowW - left, row.h, GuideTheme.withAlpha(GuideTheme.FOCUS, 0x26));
        }
        icon(g, row.entry.icon(), left + 4, ry + 2);
        // 步骤标记：✓ 做完了（薄荷绿）、下一步（品红小箭头）、被进度锁着（锁）；可选步骤标题压暗一档
        GuideView.Progress progress = view != null ? view.progress() : null;
        boolean locked = progress != null && progress.locked(row.entry);
        boolean done = progress != null && progress.done(row.entry);
        boolean next = progress != null && progress.next() == row.entry;
        int mx = x + rowW - 10;
        if (locked) {
            GuideTheme.lock(g, mx, ry + 4, GuideTheme.FAINT);
        } else if (done) {
            GuideTheme.check(g, mx, ry + 5, GuideTheme.ON);
        } else if (next) {
            GuideTheme.chevron(g, mx + 3, ry + row.h / 2, false, GuideTheme.FOCUS);
        }
        int base = locked ? GuideTheme.FAINT : row.entry.optional && !done ? GuideTheme.NAV_TEXT_DIM : GuideTheme.NAV_TEXT;
        int color = selected || hover ? GuideTheme.PEARL : base;
        g.drawString(r.font, row.label, left + 19, ry + 4, color, false);
    }

    private void drawResult(GuideRender r, Row row, int ry, int rowW, boolean hover, @Nullable EntryView current, int cursor) {
        GuiGraphics g = r.g;
        boolean selected = row.entry == current;
        boolean focused = row.resultIndex == cursor;
        if (selected) {
            g.fill(x, ry, x + rowW, ry + row.h, GuideTheme.withAlpha(GuideTheme.FOCUS, 0x20));
            g.fill(x, ry, x + 2, ry + row.h, GuideTheme.FOCUS);
        } else if (hover || focused) {
            GuideTheme.gradientRow(g, x, ry, rowW, row.h, GuideTheme.withAlpha(GuideTheme.FOCUS, focused ? 0x3A : 0x26));
        }
        if (focused) {
            CodexTheme.brackets(g, x + 1, ry, rowW - 2, row.h, 3, GuideTheme.FOCUS);
        }
        g.renderItem(row.entry.icon(), x + 4, ry + 3);
        g.drawString(r.font, row.label, x + 24, ry + 3, selected || hover ? GuideTheme.PEARL : GuideTheme.NAV_TEXT, false);
        g.drawString(r.font, row.sub, x + 24, ry + 13, GuideTheme.FAINT, false);
        g.fill(x + 4, ry + row.h - 1, x + rowW - 4, ry + row.h, GuideTheme.withAlpha(GuideTheme.EDGE, 0x50));
    }

    /**
     * 0.75 倍的物品图标（12px）：列表行高只有十几像素，原尺寸图标会把行撑开
     */
    private static void icon(GuiGraphics g, ItemStack stack, int ix, int iy) {
        g.pose().pushPose();
        g.pose().translate(ix, iy, 0f);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }
}
