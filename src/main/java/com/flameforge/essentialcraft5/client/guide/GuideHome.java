package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.api.MatrixRanks;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Group;
import com.flameforge.essentialcraft5.client.guide.GuideView.CatView;
import com.flameforge.essentialcraft5.client.guide.GuideView.EntryView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页：三个分区的格子 + 底部「下一步 / 继续阅读」条
 *
 * <h3>布局</h3>
 * <p>研究手册是按游戏进度排的流程教程，二十多个分类，所以首页不做一屏十个大格子，而是分三区往下排
 * （可滚动）：</p>
 * <ul>
 *   <li><b>入门</b>：一条横幅（每个入门分类一条），图标 + 标题 + 副标题；</li>
 *   <li><b>主线</b>：第一章起的小格子，每格写章号、短标题（去掉「第一章 · 」前缀）、完成数 x/y，锁着的写「未解锁」；</li>
 *   <li><b>支线</b>：同样的小格子，完成数按支线里的步骤算（支线全是可选步骤）。</li>
 * </ul>
 * <p>格子按阅读区宽度排 3~5 列（427×240 的最窄界面是 4 列）。完整标题和副标题在悬停提示里。
 * 底部的「下一步」条<b>固定</b>在首页最下面、不随格子滚动：流程里第一个没做完的步骤永远一眼可见，
 * 全做完了（或没有步骤）改成「继续阅读 / 新手从这里开始」。</p>
 *
 * <h3>动效：成形</h3>
 * <p>每个格子（和底下那条）各自走一段 {@value #FORM_MS}ms 的成形，进度 f 从 0 到 1：</p>
 * <ul>
 *   <li>f 0 ~ {@value #TRACE_END}：描边先到 —— 外框从顶边中点往两侧描，在底边中点合拢，两个丁香白笔头；
 *       描完后外框在剩下的时间里由薰衣草冷却成常态的紫罗兰结构线色；</li>
 *   <li>f 0 ~ {@value #SCAN_END}：一道小光幕自上而下扫过格子；</li>
 *   <li>f {@value #CONTENT_FROM} ~ 1：内容后到 —— 底色、徽框、图标、文字带一点回弹地浮现。</li>
 * </ul>
 * <p><b>什么时候开始</b>：开场时按显影光幕（{@link GuideIntro#BEAM_START} 起，{@link GuideIntro#BEAM_MS}ms 匀速扫过面板）
 * 到达格子顶边的时刻，同一行里再从左到右各错开 {@value #INTRO_COL_MS}ms；回首页时不跟光幕，
 * 按序号从 {@value #TILE_DELAY_MS}ms 起每格错开 {@value #TILE_STAGGER_MS}ms。</p>
 * <p>悬停时上浮 1px、描边转品红并外溢一圈辉光，鼠标移上去的那一下左上角斜着跳出一小道基座火花（{@link GuideArcs#hoverSpark}）；
 * 按住左键时沉回原位、底色转暗紫红（按下）。悬停过渡用指数趋近，与帧率无关。</p>
 *
 * <h3>护眼（2026-09-29）</h3>
 * <p>格子和「下一步」条都是低饱和的灰紫板 {@link GuideTheme#TILE_BG}；「下一步」靠品红细边、品红箭头和淡品红的「下一步」三个字认出来，
 * 不再是一整条暮紫品红底加两道流动的 MRU 线。首页原先在格子缝里上浮的 MRU 光粒也去掉了 —— 这一片是读字、找格子的地方，没有常驻动画。</p>
 */
@OnlyIn(Dist.CLIENT)
final class GuideHome {

    private static final int GAP = 5;
    /** 分区标题一行的高度 */
    private static final int HEAD_H = 15;
    /** 分区之间多空一点 */
    private static final int SECTION_GAP = 6;
    private static final int BANNER_H = 30;
    private static final int TILE_H = 30;
    private static final int TILE_H_ROOMY = 36;
    private static final int CONT_H = 20;

    /** 一个格子从开始成形到完全成形 */
    private static final float FORM_MS = 200f;
    /** 成形进度到这里时外框描完（描边先到） */
    private static final float TRACE_END = 0.55f;
    /** 成形进度到这里时格子里的小光幕走到底 */
    private static final float SCAN_END = 0.6f;
    /** 成形进度从这里开始内容浮现（内容后到） */
    private static final float CONTENT_FROM = 0.3f;
    /** 回首页（非开场）时第一格的延迟与逐格错开 */
    private static final float TILE_DELAY_MS = 20f;
    private static final float TILE_STAGGER_MS = 14f;
    private static final int TILE_STAGGER_CAP = 20;
    /** 开场时同一行相邻两格的错开：光幕同时扫到一整行，行内再从左到右依次点亮 */
    private static final float INTRO_COL_MS = 16f;
    /** 描线笔头：MRU 光的丁香白芯 */
    private static final int HEAD = GuideTheme.HOT;

    private static final int TILE_BG = GuideTheme.TILE_BG;
    private static final int TILE_BG_HOVER = GuideTheme.TILE_BG_HOVER;
    private static final int TILE_BG_PRESS = GuideTheme.TILE_BG_PRESS;

    int x;
    int y;
    int w;
    int h;

    @Nullable
    private GuideView view;
    private List<CatView> cats = List.of();
    private int cols = 4;
    private int tileW;
    private int tileH = TILE_H;
    private int emblem;
    /** 每个分类格子的位置（相对内容顶，未减滚动）与尺寸 */
    private int[] tx = new int[0];
    private int[] ty = new int[0];
    private int[] tw = new int[0];
    private int[] th = new int[0];
    private boolean[] banner = new boolean[0];
    /** 分区标题：位置（相对内容顶）与文字 */
    private final List<String> heads = new ArrayList<>();
    private final List<Integer> headY = new ArrayList<>();
    private int contentH;
    /** 可滚动区（格子）的高度：底部条固定在下面 */
    private int areaH;

    // 排版时量好、截好的文字：每帧十几个格子 × 两三段文字逐个 font.width 是纯浪费
    private String[] titles = new String[0];
    private String[] subs = new String[0];
    private int[] titleW = new int[0];
    private String[] metas = new String[0];
    private int[] metaW = new int[0];
    private int[] metaColor = new int[0];
    private boolean[] metaDone = new boolean[0];
    private String lockedText = "";
    private String emptyText = "";
    private int emptyW;
    /** 第一行分区标题右边的「矩阵 · n阶」（batch13.md §3.1.5）：矩阵分类看得见时才画；阶变了才重排文字 */
    private boolean showRank;
    private int rankShown = -1;
    private String rankText = "";
    private int rankW;

    // 底部条
    @Nullable
    private EntryView cont;
    /** 底部条的类型：0 = 下一步，1 = 继续阅读，2 = 新手从这里开始，3 = 主线已完成 */
    private int contKind;
    private int contY;
    private String contLabel = "";
    private String contTitle = "";
    private String contCat = "";
    private int contLabelW;
    private int contCatW;
    /** 排版时的进度状态：打勾变了要重排文字 */
    private int progressStamp = -1;
    @Nullable
    private EntryView lastRead;

    private float scroll;
    private float target;
    private final GuideScrollDrag bar = new GuideScrollDrag();
    private float[] hover = new float[0];
    private float contHover;
    /** 上一帧悬停的格子 / 是否压在「下一步」条上：变了的那一下跳火花 */
    private int lastHovered = -1;
    private boolean contWasOver;
    /** 上一帧的鼠标位置：鼠标不动、滚轮滚格子时格子从光标下滑过，不算「移上去」，不跳火花 */
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private long shownAt;
    /** 这一轮成形是不是开场：是就跟着开场光幕走 */
    private boolean intro;
    /** 开场光幕扫过的范围（就是整块面板） */
    private int beamTop;
    private int beamH = 1;
    private GuideTextLayout text;
    /**
     * 开场这一轮成形读的时钟：开场时间轴上的毫秒（{@link GuideIntro#openT}，每帧由界面给）。完整开场、查阅短开场、
     * 展示厅冻结三种情况共用这一条映射；回首页时的重新成形不读它，按墙钟
     */
    private float introTime;

    // ==================== 排版 ====================

    /**
     * @param view     视图
     * @param lastRead 上次读到的条目（「继续阅读」的目标），没有为 null
     * @param text     排版器
     */
    void layout(GuideView view, @Nullable EntryView lastRead, GuideTextLayout text, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.text = text;
        this.lastRead = lastRead;
        boolean changed = this.view != view;
        this.view = view;
        this.cats = view.categories;
        int n = cats.size();
        showRank = cats.stream().anyMatch(c -> "matrix".equals(c.id()));
        rankShown = -1;
        if (changed || hover.length != n) {
            hover = new float[n];
        }
        Font font = text.font();

        cols = w >= 560 ? 5 : w >= 330 ? 4 : w >= 240 ? 3 : 2;
        tileW = (w - (cols - 1) * GAP) / cols;
        tileH = tileW >= 120 ? TILE_H_ROOMY : TILE_H;
        emblem = tileH - 10;

        tx = new int[n];
        ty = new int[n];
        tw = new int[n];
        th = new int[n];
        banner = new boolean[n];
        heads.clear();
        headY.clear();
        int cy = 0;
        Group section = null;
        int col = 0;
        int mainCount = 0;
        int sideCount = 0;
        for (CatView cat : cats) {
            if (cat.group() == Group.MAIN) {
                mainCount++;
            } else if (cat.group() == Group.SIDE) {
                sideCount++;
            }
        }
        for (int i = 0; i < n; i++) {
            CatView cat = cats.get(i);
            if (cat.group() != section) {
                if (section != null) {
                    // 上一区最后一行没排满：收尾
                    if (col > 0) {
                        cy += tileH + GAP;
                        col = 0;
                    }
                    cy += SECTION_GAP - GAP;
                }
                section = cat.group();
                heads.add(switch (section) {
                    case START -> I18n.get("guide.essentialcraft5.ui.group.start");
                    case MAIN -> I18n.get("guide.essentialcraft5.ui.group.main", mainCount);
                    case SIDE -> I18n.get("guide.essentialcraft5.ui.group.side", sideCount);
                });
                headY.add(cy);
                cy += HEAD_H;
            }
            if (section == Group.START) {
                // 入门：整行横幅
                tx[i] = 0;
                ty[i] = cy;
                tw[i] = w;
                th[i] = BANNER_H;
                banner[i] = true;
                cy += BANNER_H + GAP;
                continue;
            }
            tx[i] = col * (tileW + GAP);
            ty[i] = cy;
            tw[i] = tileW;
            th[i] = tileH;
            col++;
            if (col >= cols) {
                col = 0;
                cy += tileH + GAP;
            }
        }
        if (col > 0) {
            cy += tileH + GAP;
        }
        contentH = Math.max(0, cy - GAP);
        areaH = h - CONT_H - 6;
        contY = y + h - CONT_H;

        titles = new String[n];
        subs = new String[n];
        titleW = new int[n];
        lockedText = I18n.get("guide.essentialcraft5.badge.locked");
        for (int i = 0; i < n; i++) {
            CatView cat = cats.get(i);
            int room = textRoom(i);
            titles[i] = GuideTheme.ellipsize(font, banner[i] ? cat.title() : cat.shortTitle(), room);
            titleW[i] = font.width(titles[i]);
            subs[i] = banner[i] ? GuideTheme.ellipsize(font, cat.subtitle(), room) : "";
        }
        metas = new String[n];
        metaW = new int[n];
        metaColor = new int[n];
        metaDone = new boolean[n];
        progressStamp = -1;
        refreshProgressText(font);
        emptyText = I18n.get("guide.essentialcraft5.ui.empty");
        emptyW = font.width(emptyText);
        target = Mth.clamp(target, 0, maxScroll());
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    /** 格子里文字区的宽度 */
    private int textRoom(int i) {
        if (banner[i]) {
            return tw[i] - (8 + 22 + 8) - 8 - 40;
        }
        return tw[i] - (5 + emblem + 5) - 4;
    }

    /**
     * 完成数、「下一步」条的文字（打勾状态变了才重排：进度每半秒最多变一次）
     */
    private void refreshProgressText(Font font) {
        if (view == null) {
            return;
        }
        GuideView.Progress progress = view.progress();
        int stamp = progress.requiredDone() * 1000 + (progress.next() == null ? -1 : progress.next().globalIndex);
        int n = cats.size();
        boolean anyLocked = false;
        for (int i = 0; i < n; i++) {
            anyLocked |= progress.locked(cats.get(i));
        }
        stamp = stamp * 31 + (anyLocked ? 1 : 0);
        for (int i = 0; i < n; i++) {
            CatView cat = cats.get(i);
            stamp = stamp * 31 + progress.doneCount(cat);
        }
        if (stamp == progressStamp) {
            return;
        }
        progressStamp = stamp;
        for (int i = 0; i < n; i++) {
            CatView cat = cats.get(i);
            String meta;
            int color = GuideTheme.MIST;
            boolean done = false;
            int total = progress.total(cat);
            if (progress.locked(cat)) {
                meta = lockedText;
                color = GuideTheme.FAINT;
            } else if (total > 0) {
                int d = progress.doneCount(cat);
                meta = d + "/" + total;
                done = d >= total;
                color = done ? GuideTheme.ON : GuideTheme.MIST;
            } else {
                meta = I18n.get("guide.essentialcraft5.ui.entry_count", cat.entries.size());
            }
            if (cat.chapter > 0 && !banner[i]) {
                meta = I18n.get("guide.essentialcraft5.ui.tile.chapter", cat.chapter) + " · " + meta;
            }
            metas[i] = GuideTheme.ellipsize(font, meta, banner[i] ? 60 : textRoom(i));
            metaW[i] = font.width(metas[i]);
            metaColor[i] = color;
            metaDone[i] = done;
        }

        // 底部条：优先「下一步」；必做步骤全做完了是「主线已完成」，指向最后一个主线章的最后一条（门开着是终端与模块章的尾声，
        // 门关着是物流章的「主线之后」）；没有必做步骤可数时，读过东西是「继续阅读」，一篇都没读过是「新手从这里开始」
        EntryView next = progress.next();
        EntryView mainEnd = next == null && progress.required() > 0 && progress.requiredDone() == progress.required() ? lastMainEntry() : null;
        if (next != null) {
            cont = next;
            contKind = 0;
        } else if (mainEnd != null) {
            cont = mainEnd;
            contKind = 3;
        } else if (lastRead != null) {
            cont = lastRead;
            contKind = 1;
        } else {
            cont = view.entryList.isEmpty() ? null : view.entryList.get(0);
            contKind = 2;
        }
        if (cont != null) {
            contLabel = I18n.get(contKind == 0 ? "guide.essentialcraft5.ui.next_step"
                    : contKind == 3 ? "guide.essentialcraft5.ui.main_done"
                    : contKind == 1 ? "guide.essentialcraft5.ui.continue" : "guide.essentialcraft5.ui.start_here");
            // 主线的步骤后面写「第 N 章」，别的写分类名
            contCat = cont.cat.chapter > 0 ? I18n.get("guide.essentialcraft5.ui.chapter", cont.cat.chapter) : cont.cat.shortTitle();
            int room = w - 20 - font.width(contLabel) - 8 - font.width(contCat) - 14;
            contTitle = GuideTheme.ellipsize(font, cont.title(), Math.max(20, room));
            if (room < 20) {
                contCat = "";
            }
            contLabelW = font.width(contLabel);
            contCatW = font.width(contCat);
        }
    }

    /** 最后一个可见主线章（章号最大）的最后一条；没有主线章为 null */
    @Nullable
    private EntryView lastMainEntry() {
        for (int i = cats.size() - 1; i >= 0; i--) {
            CatView cat = cats.get(i);
            if (cat.chapter > 0 && !cat.entries.isEmpty()) {
                return cat.entries.get(cat.entries.size() - 1);
            }
        }
        return null;
    }

    /** 重新播放格子成形（回到首页时）：按序号错开 */
    void restart(long now) {
        shownAt = now;
        intro = false;
        scroll = target = 0;
    }

    /**
     * 开场：格子跟着开场显影光幕成形（时钟由 {@link #setIntroTime} 每帧给）
     */
    void restartIntro() {
        intro = true;
        introTime = 0f;
        scroll = target = 0;
    }

    /** 开场光幕扫过的范围：面板的上沿与高度（尺寸变了要重设） */
    void setIntroBeam(int top, int height) {
        beamTop = top;
        beamH = Math.max(1, height);
    }

    /**
     * 开场这一轮成形的时钟（每帧由界面给）：开场时间轴上的毫秒
     *
     * @param openT {@link GuideIntro#openT}
     */
    void setIntroTime(float openT) {
        introTime = openT;
    }

    /** 跳过开场：所有格子直接成形完毕 */
    void finishAppear() {
        intro = false;
        shownAt = Long.MIN_VALUE / 4;
    }

    /** 首页滚回顶部（换了界面大小：列数变了，原来的滚动量对不上） */
    void scrollToTop() {
        scroll = target = 0;
    }

    private float maxScroll() {
        return Math.max(0, contentH - areaH);
    }

    private int tileX(int i) {
        return x + tx[i];
    }

    private int tileY(int i) {
        return y + ty[i] - Math.round(scroll);
    }

    /**
     * 第 i 格（i == 分类数时是底部条）从 shownAt 起多久开始成形
     */
    private float formDelay(int i) {
        boolean isCont = i >= cats.size();
        if (intro) {
            // 光幕匀速扫过面板：到达这一格顶边的时刻 = 起点 + 相对高度 × 扫完用时
            int top = isCont ? contY : y + ty[i];
            float rel = Mth.clamp((top - beamTop) / (float) beamH, 0f, 1f);
            int column = isCont || banner[i] ? 0 : tx[i] / Math.max(1, tileW + GAP);
            return GuideIntro.BEAM_START + rel * GuideIntro.BEAM_MS + column * INTRO_COL_MS;
        }
        return TILE_DELAY_MS + Math.min(i, TILE_STAGGER_CAP) * TILE_STAGGER_MS;
    }

    /** 成形进度 0~1（线性） */
    private float form(int i, long now) {
        float t = intro ? introTime : (float) (now - shownAt);
        float elapsed = t - formDelay(i);
        return elapsed <= 0f ? 0f : Math.min(1f, elapsed / FORM_MS);
    }

    /** 内容浮现量（easeOutBack，中途会略超过 1） */
    private static float content(float form) {
        return form <= CONTENT_FROM ? 0f : GuideTheme.easeOutBack((form - CONTENT_FROM) / (1f - CONTENT_FROM));
    }

    private float appear(int i, long now) {
        return content(form(i, now));
    }

    // ==================== 命中 ====================

    /**
     * @return 鼠标下的格子序号；没浮现完的格子不响应
     */
    int tileAt(double mx, double my, long now) {
        if (my < y || my >= y + areaH) {
            return -1;
        }
        for (int i = 0; i < cats.size(); i++) {
            int x0 = tileX(i);
            int y0 = tileY(i);
            if (mx >= x0 && mx < x0 + tw[i] && my >= y0 && my < y0 + th[i]) {
                return appear(i, now) >= 0.5f ? i : -1;
            }
        }
        return -1;
    }

    @Nullable
    CatView cat(int i) {
        return i >= 0 && i < cats.size() ? cats.get(i) : null;
    }

    /** 这个格子被进度锁着（点不开） */
    boolean locked(int i) {
        return view != null && i >= 0 && i < cats.size() && view.progress().locked(cats.get(i));
    }

    boolean overContinue(double mx, double my, long now) {
        if (cont == null) {
            return false;
        }
        return mx >= x && mx < x + w && my >= contY && my < contY + CONT_H && appear(cats.size(), now) >= 0.5f;
    }

    @Nullable
    EntryView continueTarget() {
        return cont;
    }

    /** 「下一步」条指的是不是流程里的下一步（是的话打开时不恢复上次的滚动位置） */
    boolean continueIsNextStep() {
        return contKind == 0;
    }

    /**
     * 悬停提示：格子的完整标题 + 副标题（小格子里只放得下短标题）；锁着的再加一行解锁条件
     *
     * @return 提示行；鼠标不在格子上为 null
     */
    @Nullable
    List<Component> tooltip(double mx, double my, long now) {
        int i = tileAt(mx, my, now);
        CatView cat = cat(i);
        if (cat == null) {
            return null;
        }
        List<Component> lines = new ArrayList<>(3);
        lines.add(Component.literal(cat.title()));
        if (!cat.subtitle().isEmpty()) {
            lines.add(Component.literal(cat.subtitle()).withStyle(s -> s.withColor(GuideTheme.MIST & 0xFFFFFF)));
        }
        if (locked(i) && cat.category.advancement != null && view != null) {
            lines.add(view.lockHint(cat).copy().withStyle(s -> s.withColor(GuideTheme.FAINT & 0xFFFFFF)));
        }
        return lines;
    }

    boolean mouseScrolled(double delta) {
        if (maxScroll() <= 0) {
            return false;
        }
        target = Mth.clamp(target - (float) delta * (tileH + GAP), 0, maxScroll());
        return true;
    }

    /**
     * 滚动条画在格子区右侧的留白里（{@code x + w + 1}），命中区就是那一条留白，和格子不重叠
     */
    private boolean overScrollbar(double mx, double my) {
        return maxScroll() > 0 && mx >= x + w && mx < x + w + GuideScrollDrag.HIT_W && my >= y && my < y + areaH;
    }

    /** 按在滚动条上：点轨道跳过去 / 抓住滑块（与正文、左栏的滚动条同一手感） */
    boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !overScrollbar(mx, my)) {
            return false;
        }
        scroll = target = bar.press(my, y, areaH, scroll, maxScroll());
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
        scroll = target = bar.drag(my, areaH, maxScroll());
        return true;
    }

    void release() {
        bar.release();
    }

    // ==================== 绘制 ====================

    void render(GuideRender r, float delta, int offsetY, boolean settled) {
        GuiGraphics g = r.g;
        long now = r.now;
        scroll = GuideTheme.approach(scroll, target, delta, 18f);

        if (cats.isEmpty()) {
            g.drawString(r.font, emptyText, x + (w - emptyW) / 2, y + h / 2 - 4, GuideTheme.FAINT, false);
            return;
        }
        refreshProgressText(r.font);

        int hovered = settled && r.mouseActive ? tileAt(r.mouseX, r.mouseY, now) : -1;
        for (int i = 0; i < cats.size(); i++) {
            hover[i] = GuideTheme.approach(hover[i], i == hovered && !locked(i) ? 1f : 0f, delta, 16f);
        }
        boolean contOver = settled && r.mouseActive && overContinue(r.mouseX, r.mouseY, now);
        contHover = GuideTheme.approach(contHover, contOver ? 1f : 0f, delta, 16f);
        // 鼠标移到一个新格子 / 「下一步」条上的那一下：左上角斜着跳出一小道基座火花（全局限速，来回划过一排格子也不会连闪）。
        // 只认鼠标真的动了：鼠标不动、滚轮滚格子时格子从光标下一格格滑过，上一版每 350ms 跳一道、贴着限速上限一直在闪
        boolean moved = r.mouseX != lastMouseX || r.mouseY != lastMouseY;
        lastMouseX = r.mouseX;
        lastMouseY = r.mouseY;
        if (hovered != lastHovered) {
            if (moved && hovered >= 0 && !locked(hovered) && r.arcs != null) {
                int top = tileY(hovered);
                if (top - GAP >= y && top < y + areaH) {
                    r.arcs.hoverSpark(now, tileX(hovered), top, tw[hovered], GAP);
                }
            }
            lastHovered = hovered;
        }
        if (contOver && !contWasOver && r.arcs != null) {
            r.arcs.hoverSpark(now, x, contY, w, GAP);
        }
        contWasOver = contOver;
        // 正在滚：火花钉在屏幕上，格子已经滑走了，收掉
        if (r.arcs != null && Math.abs(scroll - target) > 0.5f) {
            r.arcs.clearHover();
        }

        // 格子区（可滚动）：上下各多留几像素给悬停上浮和辉光
        g.enableScissor(x - 4, y - 3 + offsetY, x + w + 4, y + areaH + offsetY);
        int sc = Math.round(scroll);
        for (int k = 0; k < heads.size(); k++) {
            int hy = y + headY.get(k) - sc;
            if (hy + HEAD_H < y - 4 || hy > y + areaH) {
                continue;
            }
            drawHead(r, heads.get(k), hy, k);
        }
        for (int i = 0; i < cats.size(); i++) {
            int x0 = tileX(i);
            int y0 = tileY(i);
            if (y0 + th[i] < y - 4 || y0 > y + areaH + 4) {
                continue;
            }
            float f = form(i, now);
            if (f <= 0f) {
                continue;
            }
            drawTile(r, i, x0, y0, f);
        }
        g.disableScissor();

        // 滚动条在渐隐带之前画：渐隐带在 z=300 写深度，之后再画 z=0 的东西会过不了深度测试（滚动条底部 7 行会被吃掉）
        float max = maxScroll();
        if (max > 0) {
            boolean active = bar.dragging() || (settled && r.mouseActive && overScrollbar(r.mouseX, r.mouseY));
            CodexTheme.scrollbar(g, x + w + 1, y, areaH, scroll, max, active);
        }
        // 格子区下沿的渐隐：告诉读者下面还有（底部条固定，不跟着滚）。
        // 抬到 z=300 是为了盖住物品图标（z=150 上下）；右边停在 x + w，不越过滚动条那一列（同 GuideReader.fadeEdge）
        if (scroll < max - 1f) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 300f);
            for (int i = 0; i < 8; i++) {
                g.fill(x - 4, y + areaH - 8 + i, x + w, y + areaH - 7 + i, GuideTheme.fade(GuideTheme.PLATE, i / 8f * 0.85f));
            }
            g.pose().popPose();
        }

        if (cont != null) {
            float f = form(cats.size(), now);
            if (f > 0f) {
                drawContinue(r, contY, f);
            }
        }
    }

    /** 展示厅截图用：直接滚到最底 */
    void showroomScrollToEnd() {
        scroll = target = maxScroll();
    }

    /** 展示厅截图用：第 i 格的中心（GUI 坐标）；没有这一格返回 null */
    @Nullable
    int[] showroomTileCenter(int i) {
        if (i < 0 || i >= cats.size()) {
            return null;
        }
        return new int[] {tileX(i) + tw[i] / 2, tileY(i) + th[i] / 2};
    }

    /** 展示厅截图用：「下一步」条的中心 */
    int[] showroomContinueCenter() {
        return new int[] {x + w / 2, contY + CONT_H / 2};
    }

    /**
     * 一步刚做完：那一步所在分类的格子左上角斜着跳一道待机电弧（格子不在可见范围里就不闪）
     *
     * @return 闪了没有
     */
    boolean sparkCategory(GuideArcs arcs, long now, CatView cat) {
        int i = cats.indexOf(cat);
        if (i < 0) {
            return false;
        }
        int top = tileY(i);
        if (top - GAP < y || top >= y + areaH) {
            return false;
        }
        arcs.stepSparkCorner(now, tileX(i), top, tw[i], GAP);
        return true;
    }

    /** 分区标题：一粒紫罗兰的小矩阵节点 + 薰衣草字 + 往右的紫罗兰细线 */
    private void drawHead(GuideRender r, String label, int hy, int index) {
        GuiGraphics g = r.g;
        int cy = hy + 5;
        CodexTheme.gemSmall(g, x + 2, cy, 1f);
        g.drawString(r.font, label, x + 8, hy + 1, GuideTheme.withAlpha(GuideTheme.GLOW, 0xE0), false);
        int lx = x + 8 + r.font.width(label) + 6;
        int rx = x + w;
        if (index == 0 && showRank) {
            // 首页第一行右边写自己的矩阵阶，读客户端玩家数据（阶随普通同步包发给本人）
            int rank = MatrixRanks.get(Minecraft.getInstance().player);
            if (rank != rankShown) {
                rankShown = rank;
                rankText = I18n.get("guide.essentialcraft5.ui.home.matrix_rank", I18n.get("matrix_rank.essentialcraft5.rank." + rank));
                rankW = r.font.width(rankText);
            }
            if (lx + rankW + 6 <= rx) {
                rx -= rankW;
                g.drawString(r.font, rankText, rx, hy + 1, GuideTheme.MIST, false);
                rx -= 6;
            }
        }
        if (lx < rx) {
            g.fill(lx, cy, rx, cy + 1, GuideTheme.withAlpha(GuideTheme.EDGE, 0x90));
        }
    }

    private void drawTile(GuideRender r, int i, int x0, int y0, float form) {
        float appear = content(form);
        if (appear > 0.01f) {
            drawTileContent(r, i, x0, y0, appear, form >= 1f);
        }
        if (form < 1f) {
            // 成形中的外框画在内容之上：底色和外框是同一圈像素，后画的底色会把描线盖掉
            formFrame(r.g, x0, y0, tw[i], th[i], 4, form, hover[i]);
        }
    }

    /**
     * @param withOutline 画不画常态外框：成形中外框由 {@link #formFrame} 画（不跟内容缩放）
     */
    private void drawTileContent(GuideRender r, int i, int x0, int y0, float appear, boolean withOutline) {
        GuiGraphics g = r.g;
        CatView cat = cats.get(i);
        float a = Mth.clamp(appear, 0f, 1f);
        float hv = hover[i];
        boolean locked = locked(i);
        int wd = tw[i];
        int ht = th[i];

        // 浮现时从 0.88 倍回弹到原大，悬停时上浮 1px、按下时沉回原位；用 pose 做，格子里的所有东西一起动
        boolean press = r.mouseDown && hv > 0.5f && !locked;
        float cx = x0 + wd / 2f;
        float cy = y0 + ht / 2f;
        float scale = 0.88f + 0.12f * appear;
        g.pose().pushPose();
        g.pose().translate(cx, cy - (press ? 0f : hv), 0f);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-cx, -cy, 0f);

        int cut = 4;
        int tileBg = press ? TILE_BG_PRESS : GuideTheme.lerpColor(TILE_BG, TILE_BG_HOVER, hv);
        CodexTheme.chamferFill(g, x0, y0, wd, ht, cut, GuideTheme.fade(tileBg, a));
        GuideTheme.glassSheen(g, x0, y0, wd, cut, a);
        if (hv > 0.01f) {
            for (int layer = 2; layer >= 1; layer--) {
                CodexTheme.chamferOutline(g, x0 - layer, y0 - layer, wd + layer * 2, ht + layer * 2, cut + layer,
                        GuideTheme.withAlpha(GuideTheme.FOCUS, (int) (46 * hv * a / layer)));
            }
        }
        if (withOutline) {
            int base = metaDone[i] ? GuideTheme.withAlpha(GuideTheme.ON, 0x90) : GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF);
            int edge = press ? GuideTheme.LINK_HOVER : GuideTheme.lerpColor(base, GuideTheme.FOCUS, hv);
            CodexTheme.chamferOutline(g, x0, y0, wd, ht, cut, GuideTheme.fade(edge, a));
        }
        // 主线的一章做完了：左上角一枚绿角标
        if (metaDone[i]) {
            CodexTheme.cornerTab(g, x0 + 1, y0 + 1, 4, GuideTheme.fade(GuideTheme.ON, a));
        }

        // 悬停时标题提亮到暖白（不到纯白）
        int titleColor = GuideTheme.textAlpha(locked ? GuideTheme.FAINT : GuideTheme.lerpColor(GuideTheme.PEARL, GuideTheme.TAG_EM, hv), a);
        int subColor = GuideTheme.textAlpha(GuideTheme.MIST, a);
        int metaCol = GuideTheme.textAlpha(hv > 0.5f && !locked ? GuideTheme.lerpColor(metaColor[i], GuideTheme.LINK, hv) : metaColor[i], a);
        int emblemEdge = GuideTheme.fade(GuideTheme.lerpColor(GuideTheme.withAlpha(GuideTheme.GLOW, locked ? 0x50 : 0xA0), GuideTheme.FOCUS, hv), a);
        if (banner[i]) {
            int size = 22;
            int ex = x0 + 8;
            int ey = y0 + (ht - size) / 2;
            GuideTheme.emblem(g, ex, ey, size, GuideTheme.fade(GuideTheme.SLOT, a), emblemEdge);
            icon(g, cat.icon(), ex + size / 2f, ey + size / 2f, a, 1f);
            int textX = ex + size + 8;
            g.drawString(r.font, titles[i], textX, y0 + 5, titleColor, true);
            g.drawString(r.font, subs[i], textX, y0 + 17, subColor, false);
            g.drawString(r.font, metas[i], x0 + wd - 8 - metaW[i], y0 + 5, metaCol, false);
        } else {
            int ex = x0 + 5;
            int ey = y0 + (ht - emblem) / 2;
            GuideTheme.emblem(g, ex, ey, emblem, GuideTheme.fade(GuideTheme.SLOT, a), emblemEdge);
            icon(g, cat.icon(), ex + emblem / 2f, ey + emblem / 2f, a, Mth.clamp((emblem - 4) / 16f, 0.75f, 1.25f));
            if (locked) {
                GuideTheme.lock(g, ex + emblem - 7, ey + emblem - 7, GuideTheme.fade(GuideTheme.MIST, a));
            }
            int textX = ex + emblem + 5;
            int top = y0 + (ht - 20) / 2;
            g.drawString(r.font, titles[i], textX, top, titleColor, false);
            g.drawString(r.font, metas[i], textX, top + 11, metaCol, false);
        }
        g.pose().popPose();
    }

    private static void icon(GuiGraphics g, ItemStack stack, float cx, float cy, float alpha, float s) {
        // 物品图标没有透明度可调：浮现到一半再出现，免得一个实心图标先于半透明的格子蹦出来
        if (alpha < 0.35f) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        g.pose().scale(s, s, 1f);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    private void drawContinue(GuideRender r, int cy, float form) {
        float appear = content(form);
        if (appear > 0.01f) {
            drawContinueContent(r, cy, appear, form >= 1f);
        }
        if (form < 1f) {
            formFrame(r.g, x, cy, w, CONT_H, 4, form, contHover);
        }
    }

    private void drawContinueContent(GuideRender r, int by, float appear, boolean withOutline) {
        GuiGraphics g = r.g;
        float a = Mth.clamp(appear, 0f, 1f);
        float hv = contHover;
        // 「下一步」是全屏唯一在催你做事的东西，但它就在读者眼皮底下、一直在：底色和普通格子一样是低饱和的灰紫板，
        // 靠品红细边、品红箭头、淡品红的「下一步」三个字认出来（上一版是一整条暮紫品红底 + 两道 24 秒流一圈的 MRU 线）。
        // 悬停时上沿才亮起一道静止的 MRU 线。「主线已完成」同一套画法，品红换成薄荷绿（和格子上的「做完了」同色），也不加动画
        boolean nextStep = contKind == 0;
        boolean mainDone = contKind == 3;
        boolean press = r.mouseDown && hv > 0.5f;
        int bg = press ? TILE_BG_PRESS : GuideTheme.lerpColor(TILE_BG, TILE_BG_HOVER, hv);
        CodexTheme.chamferFill(g, x, by, w, CONT_H, 4, GuideTheme.fade(bg, a));
        GuideTheme.glassSheen(g, x, by, w, 4, a);
        if (hv > 0.01f) {
            CodexTheme.mruLine(g, x + 5, by + 1, w - 10, (int) (0x70 * hv * a), 0.25f);
        }
        if (withOutline) {
            int base = nextStep ? GuideTheme.withAlpha(GuideTheme.FOCUS, 0xB0)
                    : mainDone ? GuideTheme.withAlpha(GuideTheme.ON, 0xB0) : GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF);
            CodexTheme.chamferOutline(g, x, by, w, CONT_H, 4, GuideTheme.fade(GuideTheme.lerpColor(base, GuideTheme.FOCUS, hv), a));
        }
        int ty0 = by + (CONT_H - 8) / 2;
        GuideTheme.chevron(g, x + 10 + Math.round(hv * 2f), by + CONT_H / 2, false,
                GuideTheme.fade(nextStep ? GuideTheme.FOCUS : mainDone ? GuideTheme.lerpColor(GuideTheme.ON, GuideTheme.FOCUS, hv)
                        : GuideTheme.lerpColor(GuideTheme.GLOW, GuideTheme.FOCUS, hv), a));
        int tx0 = x + 20;
        // 焦点色的字用 LINK（品红本身当文字太艳）
        g.drawString(r.font, contLabel, tx0, ty0, GuideTheme.textAlpha(nextStep ? GuideTheme.LINK : mainDone ? GuideTheme.ON : GuideTheme.MIST, a), false);
        tx0 += contLabelW + 8;
        g.drawString(r.font, contTitle, tx0, ty0, GuideTheme.textAlpha(GuideTheme.lerpColor(GuideTheme.PEARL, GuideTheme.TAG_EM, hv), a), true);
        if (!contCat.isEmpty()) {
            g.drawString(r.font, contCat, x + w - 8 - contCatW, ty0, GuideTheme.textAlpha(GuideTheme.FAINT, a), false);
        }
    }

    /**
     * 成形中的外框与小光幕（成形完就不再调用，外框回到内容里按常态画）
     *
     * @param form 成形进度 0~1
     * @param hv   悬停量：回首页时格子可能边成形边被悬停，外框照常转品红
     */
    private static void formFrame(GuiGraphics g, int x, int y, int w, int h, int cut, float form, float hv) {
        int cool = GuideTheme.lerpColor(GuideTheme.withAlpha(GuideTheme.GLOW, 0xE0), GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF),
                (form - TRACE_END) / (1f - TRACE_END));
        int edge = GuideTheme.lerpColor(cool, GuideTheme.FOCUS, hv);
        float tp = GuideTheme.easeOutCubic(form / TRACE_END);
        if (tp >= 1f) {
            CodexTheme.chamferOutline(g, x, y, w, h, cut, edge);
        } else {
            float d = tp * CodexTheme.chamferPerimeter(w, h, cut) / 2f;
            CodexTheme.chamferArc(g, x, y, w, h, cut, -d, d, edge);
            if (d > 3f) {
                CodexTheme.chamferArc(g, x, y, w, h, cut, d - 3f, d, HEAD);
                CodexTheme.chamferArc(g, x, y, w, h, cut, -d, -d + 3f, HEAD);
            }
        }
        if (form < SCAN_END) {
            float s = form / SCAN_END;
            int sy = y + 1 + Math.min(h - 3, Math.round((h - 3) * s));
            // 斜切角那几行要往里收，光幕不能画到切掉的角上
            int top = sy - y;
            int bottom = y + h - 1 - sy;
            int in = Math.max(1, Math.max(cut - top, cut - bottom));
            GuideTheme.hairline(g, x + in, sy, w - in * 2, GuideTheme.withAlpha(GuideTheme.GLOW, (int) (0xC0 * (1f - s))));
        }
    }
}
