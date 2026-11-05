package com.flameforge.essentialcraft5.client.guide;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import com.flameforge.essentialcraft5.client.guide.GuideMarkup.Link;
import com.flameforge.essentialcraft5.client.guide.GuideView.CatView;
import com.flameforge.essentialcraft5.client.guide.GuideView.EntryView;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 研究手册（源质魔法5的流程教程与资料）
 *
 * <p>界面两页：<b>首页</b>是分三区的分类格子，<b>阅读页</b>是
 * 「左目录 + 右正文」竖向滚动。两页共用同一块面板和顶栏 —— 翻页时顶栏、搜索框一动不动，只有正文区在换。</p>
 *
 * <h3>视觉</h3>
 * <p>源质紫做框，读的地方安静（2026-09-29 护眼改版，配色出处见 {@link CodexTheme}、对比度见 {@link GuideTheme}）：
 * 阅读栏和首页格子区是低饱和的暗灰紫、正文是略暖的米白；外框薰衣草描边与四角矩阵节点、顶栏 / 底栏的护板紫带、左栏的暗紫、
 * MRU 紫罗兰的结构线撑起源质魔法的紫；品红只画小面积（选中竖条、悬停描边、链接下划线、「下一步」的细边）。
 * 阅读栏、左栏、首页格子区里<b>没有常驻动画</b>；顶栏下沿那道 MRU 光带是静止的，顶栏空白处偶尔有一两粒很淡的微光粒。</p>
 *
 * <h3>闪电（{@link GuideArcs}）</h3>
 * <p>源质魔法到处是电弧，手册只在事件上噼啪一下：开场从 MRU 光点劈向四角、翻页擦除的前沿（电弧本身当前沿）、
 * 悬停导航按钮 / 首页格子 / 「下一步」条（从块的左上角斜着跳出去）、一步刚打勾；外加顶栏 / 底栏空白、外框上沿内侧、
 * 面板外留白（够宽时）里 8~12 秒一次的环境电弧。形状是 {@code ArcEffect} 的原作电弧，颜色是原作电弧预设；
 * 限速保证任意一秒里至多亮 3 次（翻页优先，不受 350ms 间隔）；收场时还亮着的弧跟着面板一起淡掉。</p>
 *
 * <h3>动效：研究手册显影（分镜与时间轴见 {@link GuideIntro}）</h3>
 * <ul>
 *   <li>开场（三幕约 1800ms，1520ms 就可以操作）：MRU 光粒沿两条旋臂旋进、汇成一粒蓝紫光点 → 品红能量描出灵魂五芒星法阵、五个顶点依次亮起 →
 *       光点拉成 MRU 光线、面板以它为轴上下展开、丁香白笔头描外框、四角矩阵节点卡入 → 紫色光幕自上而下扫出内容 →
 *       书名从附魔台的银河字母逐字显影成真名 → 首页格子跟着光幕逐个成形。每次打开都放完整开场。
 *       开场期间点一下 / 按任意键 / 滚轮（Esc 除外）直接跳到终态，<b>这次输入照常处理</b>；面板展开过半之前的点击和滚轮只跳过、不处理；
 *       F1~F12、单按修饰键不跳；</li>
 *   <li>查阅打开（物品上按住 Ctrl、手持手册潜行右键）走<b>短开场</b>（从光线展开开始，约 280ms），直接翻到那一块并让它亮一下；</li>
 *   <li>收场（约 1000ms，和开场倒着呼应）：封存光幕自下而上盖住内容、书名退回银河字母 → 面板收拢、外框收线回到起笔点 →
 *       法阵收回来缩进光点、光粒从光点散开。{@link #onClose()} 推迟到动画走完，然后回到打开前的界面（从物品栏查阅的回到物品栏）；
 *       收场期间按任意键 / 点一下直接关完（按的键交给游戏，Esc 除外；单按修饰键、F1~F12 不算）；</li>
 *   <li>首页 ↔ 阅读页：正文区 140ms 横向擦除；</li>
 *   <li>面板全程<b>不平移</b>，只裁剪、叠光效 —— 任何时刻命中区都和画出来的位置一致。</li>
 * </ul>
 *
 * <h3>界面大小（{@link GuideScale}）</h3>
 * <p>手册用自己的界面倍数：游戏选「自动」时取虚拟屏幕至少 560×320 的最大倍数（1080p 4 → 3、1440p 6 → 4），手动倍数跟游戏。
 * 顶栏第四个按钮「Aa」左键字小一档、右键字大一档；Ctrl + 滚轮、Ctrl − / = / 0 同样能调，写回 CLIENT 配置 {@code [guide] gui_scale}。
 * 换倍数、改窗口大小后阅读位置按块锚定（{@link GuideReader#anchor()}）。</p>
 *
 * <h3>数值来源</h3>
 * <p>正文里的 {@code {cfg:…}} 取自 {@link GuideClientConfig#snapshot()}（EC5 的 SERVER 配置），视图全局共用
 * （{@link GuideView#current()}）：配置重载、F3+T 重载资源、切换语言、配方表换了、进出创造模式时整份重建并重新排版，
 * 保持当前条目与滚动位置不变（当前条目被条件藏掉了就退回同分类第一篇或首页）。数值来源不在界面上显示：单人游戏里也无所谓「服务器」。</p>
 *
 * <h3>记忆与后退</h3>
 * <p>上次停在哪一页、哪一篇、滚到哪、前进后退历史，都记在静态字段里 —— 关掉再开回到原处，
 * 跨存档也保留（找不到那一篇了就回首页）。后退：顶栏按钮、Backspace、鼠标右键、鼠标侧键、Alt+←。</p>
 *
 * <p>不是暂停界面：单人游戏时世界照常运行，边看书边等机器不耽误事。</p>
 */
@OnlyIn(Dist.CLIENT)
public class GuideScreen extends Screen {

    private enum Page { HOME, READ }

    /** 一个历史位置：entryKey 为 null 表示首页 */
    private record Loc(@Nullable String entryKey, float scroll) {}

    // ==================== 跨开关保留的状态 ====================

    private static Page lastPage = Page.HOME;
    @Nullable
    private static String lastEntryKey;
    private static float lastScroll;
    private static final Deque<Loc> BACK = new ArrayDeque<>();
    private static final Deque<Loc> FORWARD = new ArrayDeque<>();
    private static final int HISTORY_MAX = 64;

    // ==================== 部件 ====================

    /** 关掉手册后回到的界面（从物品栏查阅时就是物品栏）；null = 回到游戏 */
    @Nullable
    private final Screen parent;
    /** 查阅：打开时直接翻到的条目与块（块序号 -1 = 篇首）；null = 回到上次的位置 */
    @Nullable
    private final String targetKey;
    private final int targetBlock;
    /** 从生存物品栏查阅时 2×2 合成结果格的快照（见 {@link CraftResult}）；不是从那里打开的为 null，放回去之后也清成 null */
    @Nullable
    private CraftResult craftResult;
    private GuideView view;
    private GuideSearch search;
    private GuideTextLayout text;
    private final GuideMedia media = new GuideMedia();
    private GuideReader reader;
    private final GuideSidebar sidebar = new GuideSidebar();
    private final GuideHome home = new GuideHome();
    private final GuideRender rc = new GuideRender();
    /** 开场 / 收场 / 翻页动效 */
    private final GuideIntro fx = new GuideIntro();
    /**
     * 顶栏上零星的 MRU 微光粒：位置按种子预算、按面板尺寸缓存（窗口缩放时重算一次），每帧零分配。
     * 按整块面板撒（缩放 3 下约 17 粒柔光粒、2 颗闪光，缩放 2 下封顶 24 粒、5 颗），但顶栏文字、按钮和整个正文区、底栏
     * 每帧登记成安静区，里面一粒都不画 —— 实际只剩顶栏中间空白里零星一两粒（界面缩放 3 时那块空白很窄，常常一粒都没有）
     */
    private final CodexTheme.MruMotes motes = new CodexTheme.MruMotes(0x5EC5, 5000, 24, 30000, 5);
    /** 电弧（开场、翻页、悬停、打勾、环境），见 {@link GuideArcs} */
    private final GuideArcs arcs = new GuideArcs();
    /** 上一帧鼠标压没压在顶栏按钮上（后退 / 前进 / 首页 / 「Aa」）：刚压上去的那一下跳火花 */
    private final boolean[] navWasOver = new boolean[5];
    private EditBox searchBox;

    // ==================== 状态 ====================

    private Page page = Page.HOME;
    @Nullable
    private EntryView current;
    private List<GuideSearch.Hit> hits = List.of();
    /** 键盘选中的搜索结果 */
    private int cursor = -1;
    private boolean restored;
    /** 见 {@link #repositionElements()}：重建控件前输入框是否聚焦着 */
    private boolean keepSearchFocus;

    /**
     * 开场时钟（毫秒）：每帧加上本帧间隔、单帧最多记 {@value GuideIntro#MAX_FRAME_MS}ms（短开场是真实时间，{@link GuideIntro#update} 换算到开场时间轴）。
     * 打开手册的头几帧常常一帧一两百毫秒，按墙钟算会吃掉光粒和半个法阵；逐帧累加时低帧率下开场按比例变慢，最小化时暂停
     */
    private float introT;
    private long closingAt;
    /**
     * 收场期间、有父界面时按了键 / 输入法上屏：下一 tick 再关（这一下的按键和随后的字符事件都还落在手册上）。
     * 当场关掉的话，GLFW 同一次处理里紧跟着的字符事件会交给父界面（创造物品栏的搜索框多一个字、铁砧的改名框多一个字）
     */
    private boolean closeNextTick;
    /** 收场期间、有父界面时按下了鼠标：等松开再关，免得松开那一下落到父界面上（创造物品栏在松开时切标签页） */
    private boolean closeOnRelease;
    /**
     * Ctrl 组合键调界面大小已经「解锁」：打开手册时 Ctrl 没按着，或者打开以后松开过一次。
     * 物品提示上按住 Ctrl 查阅打开的手册，Ctrl 常常还按着，这时滚轮照常滚正文，不当成 Ctrl + 滚轮调大小
     */
    private boolean scaleKeysArmed = !hasControlDown();
    /** Ctrl + 滚轮攒的量：攒到 ±1 才调一档（触控板一次手势会连发很多带小数的量） */
    private double scaleScroll;
    private long lastFrame;
    /** 开场时钟已经从第一帧开始走了（或已被跳过） */
    private boolean introAnchored;
    /** 上一帧画的是哪一页：和当前页不同就说明换页了，播一次横向擦除 */
    @Nullable
    private Page shownPage;
    /** 擦除中：旧页是哪一页、从什么时候开始擦（0 = 没在擦） */
    @Nullable
    private Page wipeFrom;
    private long wipeAt;
    /** 顶栏按钮的悬停量：0~2 导航、3 是「Aa」、4 是开场动画开关 */
    private final float[] navHover = new float[5];

    // ==================== 几何 ====================

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int bodyY;
    private int bodyH;
    private int searchX;
    private int searchY;
    private int searchW;
    private final int[] navX = new int[3];
    private int navY;
    private static final int NAV_W = 16;
    private static final int NAV_H = 14;
    /** 「Aa」按钮（调界面大小）：在三个导航按钮右边，中间空 {@value #SCALE_GAP}px、画一道 1px 结构线 */
    private int scaleX;
    /** 开场动画开关：紧挨在「Aa」左边，中间空 3px */
    private int animX;
    private static final int SCALE_GAP = 5;
    /** 顶栏左侧文字区的右边界（在它之后是导航按钮） */
    private int headerTextRight;
    /** 顶栏左边书名 / 副标题实际占到哪一列（MRU 微光粒的安静区） */
    private int headerQuietRight;
    private String titleText = "";
    private String subtitleText = "";
    private String searchHint = "";

    // 底栏文案缓存：只在页 / 条目 / 搜索模式 / 视图变化时重拼，不每帧 String.format
    private String footerStatus = "";
    private int footerStatusW;
    private String footerHint = "";
    /** 底栏提示的宽：提示右边到计数左边是环境电弧能落的空白 */
    private int footerHintW;
    @Nullable
    private Page footerPage;
    @Nullable
    private EntryView footerEntry;
    private boolean footerSearch;
    @Nullable
    private GuideView footerView;
    /** 底栏写着「主线 x/y 步」：打勾变了要重拼 */
    private int footerProgress = -1;
    private int clearX;
    private int clearY;
    private boolean clearVisible;

    /**
     * @param parent      关掉后回到的界面，可为 null
     * @param targetKey   直接翻到的条目（查阅），null = 回到上次的位置
     * @param targetBlock 那一篇里的第几块（{@link GuideView.EntryView#blocks()} 的下标），-1 = 篇首
     * @param quick       走短开场（查阅打开）
     */
    GuideScreen(@Nullable Screen parent, @Nullable String targetKey, int targetBlock, boolean quick) {
        super(com.flameforge.essentialcraft5.registry.ECGuide.RESEARCH_BOOK.get().getDescription());
        this.parent = parent;
        this.targetKey = targetKey;
        this.targetBlock = targetBlock;
        this.fx.quick = quick;
        // 在 setScreen 换掉物品栏之前拍下来：换的时候物品栏的 removed() 会把结果格清掉
        this.craftResult = CraftResult.capture(parent);
    }

    // ==================== 生命周期 ====================

    @Override
    protected void init() {
        // 第一行：换成手册自己的界面倍数，width / height 改成设完后的虚拟尺寸（首次打开、改窗口大小、切全屏、F3+T、调大小都走这里）
        if (minecraft != null) {
            GuideScale.apply(this, minecraft);
        }
        // 重新 init 时阅读栏宽度会变、整篇重排：先按块记下阅读位置，排完换算回去
        GuideReader.Anchor anchor = searchBox != null && reader != null && page == Page.READ ? reader.anchor() : null;
        if (text == null) {
            text = new GuideTextLayout(this.font);
            reader = new GuideReader(text, media);
        }
        if (view == null) {
            view = GuideView.current();
            view.refreshProgress();
            search = new GuideSearch(view, media);
        }
        computeGeometry();

        // resize / 切全屏会对同一个实例重新 init：先存下搜索词和光标，新建的空输入框不能把它清掉
        boolean reinit = searchBox != null;
        String previous = reinit ? searchBox.getValue() : "";
        int previousCursor = reinit ? searchBox.getCursorPosition() : 0;
        float sideScroll = sidebar.target;
        searchBox = new EditBox(this.font, searchX + 14, searchY + 4, searchW - 14 - 12, 10, Component.translatable("guide.essentialcraft5.ui.search_hint"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(48);
        searchBox.setTextColor(GuideTheme.PEARL);
        if (!previous.isEmpty()) {
            // 先填词、再挂 responder：hits 和键盘选中的那一条都还是这次搜索的，不必重搜 ——
            // 重搜会把选中重置回第一条、把结果列表滚回顶部。setCursorPosition / setHighlightPos 不触发 responder
            searchBox.setValue(previous);
            searchBox.setCursorPosition(previousCursor);
            searchBox.setHighlightPos(previousCursor);
        }
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);
        if (keepSearchFocus) {
            focusSearch();
        }

        if (!restored) {
            restored = true;
            restore();
        }
        relayout();
        if (reinit && sidebar.isSearchMode()) {
            // relayout 里的 showResults 会把结果列表滚回顶部：尺寸变了不该丢掉读者滚到的位置。
            // 目录模式不用管：showTree 本来就保留滚动，还会把当前篇 reveal 进新的可视区
            sidebar.restoreScroll(sideScroll);
        }
        reader.restoreAnchor(view, anchor);
    }

    /**
     * 换了界面大小（「Aa」按钮、Ctrl + 滚轮、Ctrl − = 0）：走原版的 resize 流程重建控件
     * （{@link #repositionElements()} 处理搜索框焦点，{@link #init()} 设新倍数、按块锚定阅读位置），首页滚回顶部，按钮声
     */
    private void rescale() {
        if (minecraft == null) {
            return;
        }
        home.scrollToTop();
        this.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        click();
    }

    /**
     * 改窗口大小 / 切全屏 / 从别的界面回来时，原版走 rebuildWidgets：先 clearWidgets()、再 clearFocus()、
     * 最后才调 {@link #init()} —— 到 init 里旧输入框已经被取消聚焦，在那里读 isFocused() 永远是 false。
     * 只能在这之前记下。丢了焦点的后果不只是光标不闪：接着按退格会走「后退」而不是删字，
     * 从首页开始的搜索会直接被退回首页、搜索词清空。
     */
    @Override
    protected void repositionElements() {
        keepSearchFocus = searchBox != null && searchBox.isFocused();
        try {
            super.repositionElements();
        } finally {
            keepSearchFocus = false;
        }
    }

    /**
     * 按屏幕尺寸算面板与顶栏控件的位置
     */
    private void computeGeometry() {
        int margin = this.width < 560 ? GuideTheme.MARGIN_SMALL : GuideTheme.MARGIN;
        int marginY = this.height < 320 ? 4 : 10;
        panelW = Math.min(this.width - margin * 2, GuideTheme.PANEL_MAX_W);
        panelH = this.height - marginY * 2;
        panelX = (this.width - panelW) / 2;
        panelY = marginY;
        bodyY = panelY + GuideTheme.HEADER_H + 1;
        bodyH = panelH - GuideTheme.HEADER_H - GuideTheme.FOOTER_H - 1;

        searchW = Math.max(96, Math.min(170, panelW * 26 / 100));
        searchX = panelX + panelW - GuideTheme.PAD - searchW;
        searchY = panelY + 9;
        navY = panelY + 10;
        // 后退 / 前进 / 首页，再空 SCALE_GAP 是开场动画开关和「Aa」（调界面大小）
        scaleX = searchX - 10 - NAV_W;
        animX = scaleX - 3 - NAV_W;
        int nx = animX - SCALE_GAP - (NAV_W * 3 + 3 * 2);
        for (int i = 0; i < 3; i++) {
            navX[i] = nx + i * (NAV_W + 3);
        }
        headerTextRight = nx - 8;

        titleText = this.title.getString();
        int textLeft = panelX + GuideTheme.PAD + 22 + 7;
        int room = headerTextRight - textLeft;
        titleText = GuideTheme.ellipsize(this.font, titleText, room);
        subtitleText = GuideTheme.ellipsize(this.font, I18n.get("guide.essentialcraft5.ui.subtitle"), room);
        headerQuietRight = textLeft + Math.max(this.font.width(titleText), this.font.width(subtitleText)) + 6;
        searchHint = GuideTheme.ellipsize(this.font, I18n.get("guide.essentialcraft5.ui.search_hint"), searchW - 14 - 12);
        // 尺寸变了，底栏文案的截断宽度也变了
        footerView = null;
        // 动效：面板几何、书名逐字的位置与宽度（显影动画每帧不再测宽）、首页格子跟光束的换算
        fx.setPanel(panelX, panelY, panelW, panelH);
        fx.prepareTitle(this.font, titleText, subtitleText);
        arcs.layoutIntro(panelX, panelY, panelW, panelH);
        home.setIntroBeam(panelY, panelH);
    }

    /**
     * 各部件按当前页重新排版（尺寸变了 / 视图换了 / 换页）
     */
    private void relayout() {
        int sideW = Math.max(112, Math.min(176, panelW * 27 / 100));
        sidebar.setBounds(panelX + 1, bodyY, sideW, bodyH);
        reader.setBounds(panelX + 2 + sideW, bodyY, panelW - sideW - 3, bodyH);
        int hp = GuideTheme.PAD + 4;
        // 矮屏（480×270）上下各省几像素，四行格子才能不滚动
        int top = bodyH < 240 ? 5 : 8;
        home.layout(view, current, text, panelX + hp, bodyY + top, panelW - hp * 2, bodyH - top * 2 + 2);
        if (sidebar.isSearchMode() && !query().isEmpty()) {
            sidebar.showResults(hits, text);
        } else {
            sidebar.showTree(view, current != null ? current.cat : null, this.font);
            sidebar.reveal(current);
        }
    }

    /**
     * 恢复上次关闭时的位置
     */
    private void restore() {
        EntryView target = targetKey != null ? view.entries.get(targetKey) : null;
        if (target != null) {
            // 查阅：直接翻到那一篇的那一块。原先的位置压进后退历史，读完按后退回得去
            Loc before = lastPage == Page.READ && lastEntryKey != null ? new Loc(lastEntryKey, lastScroll) : new Loc(null, 0f);
            if (!java.util.Objects.equals(before.entryKey(), target.key)) {
                push(BACK, before);
                FORWARD.clear();
            }
            current = target;
            page = Page.READ;
            reader.show(target, 0f, false);
            reader.scrollToBlock(targetBlock);
            home.restartIntro();
            saveState();
            return;
        }
        EntryView ev = lastEntryKey != null ? view.entries.get(lastEntryKey) : null;
        current = ev;
        if (lastPage == Page.READ && ev != null) {
            page = Page.READ;
            reader.show(ev, lastScroll, false);
        } else {
            page = Page.HOME;
            reader.show(ev, ev != null ? lastScroll : 0f, false);
        }
        home.restartIntro();
    }

    private void saveState() {
        lastPage = page;
        lastEntryKey = current != null ? current.key : null;
        lastScroll = reader != null && current != null ? reader.target : 0f;
    }

    @Override
    public void tick() {
        if (!scaleKeysArmed && !hasControlDown()) {
            scaleKeysArmed = true;
        }
        if (!hasControlDown()) {
            scaleScroll = 0D;
        }
        if (closeNextTick && closingAt != 0L && minecraft != null && minecraft.screen == this) {
            closeNextTick = false;
            finishClose();
            return;
        }
        refreshIfStale();
        if (searchBox != null) {
            // 光标闪烁靠输入框自己的 tick 计数，界面不转发它就一直常亮
            searchBox.tick();
        }
        // 兜底：窗口最小化时不渲染，收场动画走不完，这里保证界面最终会关掉（展示厅冻结收场拍图时不关）
        if (closingAt != 0L && Util.getMillis() - closingAt > GuideIntro.CLOSE_MS + 250f && GuideIntro.showroomCloseFreeze < 0f
                && minecraft != null && minecraft.screen == this) {
            finishClose();
        }
    }

    /**
     * 服务端重发了配置 / 资源重载 / 切换了语言：重建视图，保持当前条目与滚动位置
     */
    private void refreshIfStale() {
        if (view == null) {
            return;
        }
        GuideView fresh = GuideView.current();
        if (fresh == view) {
            return;
        }
        String key = current != null ? current.key : null;
        float scroll = reader.target;
        if (view.generation != GuideContentLoader.generation()) {
            // 内容换了，字体也可能跟着资源包换了：字宽缓存一并作废
            text = new GuideTextLayout(this.font);
            GuideReader freshReader = new GuideReader(text, media);
            freshReader.show(reader.entry, scroll, false);
            reader = freshReader;
        }
        view = fresh;
        media.clearRecipes();
        search = new GuideSearch(view, media);
        reader.invalidate();
        EntryView ev = key != null ? view.entries.get(key) : null;
        if (ev == null && key != null) {
            // 这一篇在新配置下被条件藏掉了：退到同分类第一篇，分类也没了就回首页
            CatView cv = view.categoriesById.get(key.substring(0, key.indexOf('/')));
            ev = cv != null ? cv.entries.get(0) : null;
            scroll = 0f;
        }
        current = ev;
        String q = query();
        // 搜索中的阅读页本来就允许「左栏是结果、右栏还没选篇」，不能因为当前篇没了就把人踢回首页 ——
        // 首页和顶栏里还挂着的搜索词对不上，再打一个字又会被推回阅读页
        if (current == null && page == Page.READ && q.isEmpty()) {
            page = Page.HOME;
            home.restart(Util.getMillis());
        }
        reader.show(current, scroll, false);
        hits = q.isEmpty() ? List.of() : search.search(q);
        cursor = hits.isEmpty() ? -1 : Math.min(Math.max(cursor, 0), hits.size() - 1);
        relayout();
        saveState();
    }

    @Override
    public void removed() {
        saveState();
        if (reader != null) {
            reader.release();
        }
        sidebar.release();
        home.release();
        super.removed();
        // 换界面都经过 setScreen → removed()：窗口倍数设回游戏的，下一个界面（物品栏、死亡界面、断线界面……）init 时读到的就是还原后的尺寸
        if (minecraft != null) {
            GuideScale.restore(minecraft);
        }
        // 不管接下来是回物品栏还是被别的界面顶掉，物品栏的合成结果格都放回去（客户端这份本来就该和服务端一样）
        if (craftResult != null) {
            craftResult.restore();
            craftResult = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 开场 / 收场 ====================

    /**
     * 开场期间（以及回首页后格子成形、翻页擦除期间）的点击 / 滚轮 / 按键：动画直接跳到终态
     * （开场时钟拨到头、首页格子全部成形、擦除收尾），
     * 这次输入<b>接着照常处理</b>（面板展开过半之前的点击 / 滚轮例外，见 {@link #introEarly()}）。面板全程不平移，终态下的命中区就是玩家看着的位置。
     * Esc 不走这里：开场中按 Esc 应该从当时的样子直接收场，而不是先闪到终态再收。
     */
    private void skipIntro() {
        if (closingAt != 0L) {
            return;
        }
        introAnchored = true;
        introT = Math.max(introT, fx.introEnd());
        // 不只开场：回首页后格子也要重新成形（二十多个分类时最后几格几百毫秒后才可点），这期间点到一个
        // 已经画出外框、内容还没浮现过半的格子，tileAt 会当它不存在 —— 看得见却点不动。
        // 有输入就一并成形完毕；接下来这次输入若是换页，新一轮成形照常从头播
        home.finishAppear();
        wipeAt = 0L;
    }

    /**
     * 面板展开过半之前（{@value GuideIntro#HALF_UNFOLD}ms 前）：屏幕上还只有遮罩和法阵，玩家看不见自己点在什么上，
     * 终态那个位置可能正好是一个分类格子 —— 这时的点击和滚轮只跳过开场、不当成点到了什么。按键照常处理（直接打字就是搜索，和位置无关）
     */
    private boolean introEarly() {
        return closingAt == 0L && introT < fx.introEnd() && fx.early() && GuideIntro.showroomFreeze < 0f;
    }

    /**
     * 开场期间不跳过的键：F1~F12（F2 截图、F11 切全屏不该把开场打断）和单按的修饰键（Ctrl 是 Ctrl+F 的前半，后面那个 F 照样会跳）
     */
    private static boolean passiveKey(int key) {
        return (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12)
                || key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    @Override
    public void onClose() {
        if (closingAt == 0L) {
            // 开场还没走完就关：开场时间轴冻结在这一刻（见 GuideIntro#update），收场从当时的样子接着走。
            // 动画关着：把起点拨到收场时长以前，下一帧（或下一 tick）就是走完的状态，其余流程不变
            // （有父界面时仍等这一下的按键、字符事件、松开都过去再关）
            long now = Util.getMillis();
            closingAt = GuideScaleConfig.introAnimation() || GuideIntro.showroomCloseFreeze >= 0f
                    ? now : now - (long) GuideIntro.CLOSE_MS - 300L;
            saveState();
            return;
        }
        finishClose();
    }

    /** 收场走完：回到打开前的界面（从物品栏查阅的回到物品栏），没有就回到游戏 */
    private void finishClose() {
        if (minecraft == null) {
            return;
        }
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    // ==================== 导航 ====================

    private Loc here() {
        return page == Page.READ && current != null ? new Loc(current.key, reader.target) : new Loc(null, 0f);
    }

    private static void push(Deque<Loc> stack, Loc loc) {
        Loc top = stack.peek();
        if (top != null && java.util.Objects.equals(top.entryKey(), loc.entryKey())) {
            stack.pop();
        }
        stack.push(loc);
        while (stack.size() > HISTORY_MAX) {
            stack.removeLast();
        }
    }

    /**
     * 打开一篇（记历史）
     *
     * @param ev     条目
     * @param scroll 初始滚动位置
     */
    private void open(@Nullable EntryView ev, float scroll) {
        if (ev == null) {
            return;
        }
        if (page == Page.READ && ev == current) {
            reader.scrollTo(scroll);
            return;
        }
        push(BACK, here());
        FORWARD.clear();
        show(new Loc(ev.key, scroll));
    }

    /**
     * 点正文里的物品（物品块、配方图、结构图、物品链接）：跳到介绍它的条目、滚到那一块并亮一下
     *
     * @return 书里有介绍它的条目、跳过去了
     */
    private boolean openItem(net.minecraft.world.item.ItemStack stack) {
        GuideView.Target target = stack.isEmpty() ? null : view.find(stack.getItem());
        EntryView ev = target == null ? null : view.entries.get(target.entryKey());
        if (ev == null) {
            return false;
        }
        if (view.progress().locked(ev)) {
            return false;
        }
        open(ev, 0f);
        reader.scrollToBlock(target.block());
        return true;
    }

    private void goHome() {
        if (page == Page.HOME) {
            return;
        }
        push(BACK, here());
        FORWARD.clear();
        show(new Loc(null, 0f));
    }

    private void back() {
        travel(BACK, FORWARD);
    }

    private void forward() {
        travel(FORWARD, BACK);
    }

    private void travel(Deque<Loc> from, Deque<Loc> to) {
        while (!from.isEmpty()) {
            Loc loc = from.pop();
            // 历史跨开关、跨存档保留，指向的那篇可能已经被条件藏掉了：跳过
            if (loc.entryKey() != null && !view.entries.containsKey(loc.entryKey())) {
                continue;
            }
            if (loc.entryKey() == null && page == Page.HOME) {
                continue;
            }
            push(to, here());
            show(loc);
            return;
        }
    }

    /**
     * 不记历史地切到某个位置
     */
    private void show(Loc loc) {
        long now = Util.getMillis();
        if (loc.entryKey() == null) {
            page = Page.HOME;
            // 回到首页就是结束这次搜索：留着搜索词的话，首页的格子和顶栏里的关键词对不上
            if (!searchBox.getValue().isEmpty()) {
                searchBox.setValue("");
            }
            home.layout(view, current, text, home.x, home.y, home.w, home.h);
            home.restart(now);
            click();
        } else {
            EntryView ev = view.entries.get(loc.entryKey());
            if (ev == null) {
                return;
            }
            boolean changed = ev != current || page != Page.READ;
            boolean catChanged = current == null || ev.cat != current.cat;
            page = Page.READ;
            current = ev;
            reader.show(ev, loc.scroll(), changed);
            if (!sidebar.isSearchMode()) {
                if (catChanged) {
                    sidebar.showTree(view, ev.cat, this.font);
                }
                sidebar.reveal(ev);
            }
            if (changed && minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
            }
        }
        saveState();
    }

    /** 回首页用按钮声；翻到某一篇用翻页声（在 {@link #show} 里）—— 同一次操作只响一个声音 */
    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private boolean navEnabled(int i) {
        return switch (i) {
            case 0 -> canTravel(BACK);
            case 1 -> canTravel(FORWARD);
            default -> page != Page.HOME;
        };
    }

    /**
     * 栈里有没有 {@link #travel} 不会跳过的位置。只看「栈不空」的话，栈里剩的全是会被跳过的
     * （被条件藏掉的篇、已经在首页时的首页位置）时，按钮亮着、点了没反应、点完才变灰。
     * 跳过规则必须和 travel 保持一致。每帧三个按钮 × 最多 {@value #HISTORY_MAX} 项，开销可以忽略
     */
    private boolean canTravel(Deque<Loc> stack) {
        for (Loc loc : stack) {
            if (loc.entryKey() == null ? page != Page.HOME : view.entries.containsKey(loc.entryKey())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 搜索 ====================

    private String query() {
        return searchBox == null ? "" : GuideSearch.normalize(searchBox.getValue());
    }

    private void onSearchChanged(String value) {
        String q = GuideSearch.normalize(value);
        if (q.isEmpty()) {
            hits = List.of();
            cursor = -1;
            sidebar.showTree(view, current != null ? current.cat : null, this.font);
            sidebar.reveal(current);
            if (page == Page.READ && current == null) {
                // 在首页打字进了阅读页、一篇都没命中，又把词清空了（Esc / × / 退格删光）：
                // 搜索已经结束，右栏却没有可显示的篇 —— 回首页，别停在一个空白的阅读页上。
                // 进搜索时压进去的那格首页位置一并弹掉：回到首页后它只会让「后退」亮着却点不动
                Loc top = BACK.peek();
                if (top != null && top.entryKey() == null) {
                    BACK.pop();
                }
                show(new Loc(null, 0f));
            }
            return;
        }
        hits = search.search(q);
        cursor = hits.isEmpty() ? -1 : 0;
        sidebar.showResults(hits, text);
        if (page == Page.HOME) {
            // 在首页打字：直接进阅读页，左栏就是结果。首页留在历史里，后退能回来
            push(BACK, here());
            FORWARD.clear();
            page = Page.READ;
            if (current == null && !hits.isEmpty()) {
                current = hits.get(0).entry;
                reader.show(current, 0f, true);
            } else {
                reader.show(current, reader.target, true);
            }
            saveState();
        }
    }

    private void focusSearch() {
        searchBox.setFocused(true);
        setFocused(searchBox);
    }

    private void unfocusSearch() {
        searchBox.setFocused(false);
        setFocused(null);
    }

    private void moveCursor(int d) {
        int n = sidebar.resultCount();
        if (n == 0) {
            return;
        }
        cursor = cursor < 0 ? 0 : Math.floorMod(cursor + d, n);
        sidebar.revealResult(cursor);
    }

    private void openCursor() {
        EntryView ev = cursor >= 0 ? sidebar.resultAt(cursor) : null;
        if (ev != null) {
            open(ev, 0f);
        }
    }

    // ==================== 绘制 ====================

    @Override
    public void render(@Nonnull GuiGraphics g, int realMouseX, int realMouseY, float partialTick) {
        // 展示厅拍物品提示时用假鼠标位置（测试窗口在后台，真鼠标在哪都不一定）；平时就是真鼠标
        boolean fakeMouse = showroomMouseX >= 0;
        int mouseX = fakeMouse ? showroomMouseX : realMouseX;
        int mouseY = fakeMouse ? showroomMouseY : realMouseY;
        // HUD 那一段换回了游戏倍数、Post 却没来（Pre 被别的模组取消）：先设回手册的倍数和投影
        GuideScale.ensure(g);
        // 手册上面压过一层（Forge 的 pushGuiLayer）、关掉回来了：倍数已被兜底还原，重新设一遍
        if (minecraft != null && GuideScale.needsReapply(this, minecraft)) {
            this.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        }
        long now = Util.getMillis();
        long frameMs = lastFrame == 0L ? 0L : now - lastFrame;
        float delta = Math.min(0.1f, frameMs / 1000f);
        lastFrame = now;
        if (!introAnchored) {
            // 开场从第一帧算起：init 里建视图、排版可能要几十毫秒，算进去会把开场的头一段（光粒、光点）吃掉
            introAnchored = true;
            if (closingAt == 0L) {
                introT = 0f;
                home.restartIntro();
                if (!GuideScaleConfig.introAnimation() && GuideIntro.showroomFreeze < 0f) {
                    // 开场动画关着：第一帧就是终态，和开场中点一下跳过的效果一样
                    skipIntro();
                }
            }
        } else if (closingAt == 0L && introT < fx.introEnd()) {
            // 逐帧累加：单帧最多记 MAX_FRAME_MS，卡顿的那几帧不会把开场跳掉；收场时停住（开场冻结在按下关闭的那一刻）
            introT = Math.min(fx.introEnd(), introT + Math.min(GuideIntro.MAX_FRAME_MS, (float) frameMs));
        }
        refreshIfStale();

        fx.update(introT, closingAt, now);
        // 首页格子的开场成形读开场时间轴：完整开场、查阅短开场、展示厅冻结共用一条映射
        home.setIntroTime(fx.openT);
        if (fx.closed()) {
            // 只在自己仍是当前界面时关：收场期间被别的界面压上来时本界面只是背景层，这时关会改动图层列表。
            // 收场期间按下的鼠标还没松开时等松开再关（tick 里的兜底照样会在超时后关）
            if (minecraft != null && minecraft.screen == this && !closeOnRelease) {
                finishClose();
            }
            return;
        }
        // 就绪（1520ms）起悬停、提示框、按下态生效；开场走完（1800ms）才开始环境电弧和打勾检查 ——
        // 最后那一段只剩外框充能回落、书名收尾和最后一两格成形，不该挡着玩家操作
        boolean ready = fx.ready();
        boolean settled = fx.settled();
        float time = GuideTheme.time();

        // 换页（不管是点格子、搜索、后退还是配置重载踢回首页）都在这里统一发现，播一次横向擦除，前沿带一道电弧
        if (shownPage != null && shownPage != page && closingAt == 0L) {
            wipeFrom = shownPage;
            wipeAt = now;
            arcs.startWipe(now, bodyY, bodyY + bodyH);
        }
        shownPage = page;
        if (wipeAt != 0L && now - wipeAt >= GuideIntro.WIPE_MS && showroomWipe < 0f) {
            wipeAt = 0L;
        }
        // 环境电弧（顶栏 / 底栏空白、外框上沿内侧、面板外留白，8~12 秒一次）；打勾那一下
        int force = showroomAmbientSite;
        showroomAmbientSite = -1;
        int footR = panelX + panelW - GuideTheme.PAD - footerStatusW - 6;
        arcs.ambient(now, settled && closingAt == 0L && wipeAt == 0L, panelX, panelY, panelW, panelH, GuideTheme.CHAMFER,
                GuideTheme.HEADER_H, GuideTheme.FOOTER_H, headerQuietRight, navX[0] - 4,
                panelX + GuideTheme.PAD + footerHintW + 6, footerView == null ? 0 : footR, this.width, this.height, force);
        if (settled && closingAt == 0L) {
            checkSteps(now);
        }

        rc.beginFrame();
        rc.g = g;
        rc.font = this.font;
        rc.media = media;
        rc.mouseX = mouseX;
        rc.mouseY = mouseY;
        rc.mouseActive = ready;
        // 按下态：直接问 GLFW 左键现在是不是按着（点击在 mouseClicked 里就生效了，这只决定按住时画成什么样）
        rc.mouseDown = ready && minecraft != null
                && GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        rc.now = now;
        rc.time = time;
        rc.arcs = arcs;
        rc.clipTop = 0;
        rc.clipBottom = this.height;
        rc.alpha = 1f;

        g.drawManaged(() -> {
            g.fill(0, 0, this.width, this.height, GuideTheme.fade(GuideTheme.SCRIM, fx.scrim));
            GuideTheme.vignette(g, this.width, this.height, fx.scrim);
            renderPanel(g, mouseX, mouseY, partialTick, ready, delta, now);
            // 电弧画在面板之后、z=0：物品图标（z≈150）压在它上面，不会被光效盖住
            arcs.drawIntro(g, fx.openT, fx.stale, fx.quick, GuideIntro.showroomFreeze >= 0f, now);
            arcs.drawClose(g, fx.closeSparkT(), GuideIntro.showroomCloseFreeze >= 0f, now);
            arcs.draw(g, now, 1f - fx.fade);
            fx.drawBrackets(g);
            fx.drawSeam(g);
        });

        // tooltip 不能放进 drawManaged：它内部自己也 drawManaged，嵌套会让外层后续绘制退化成逐次 flush
        if (ready) {
            renderTooltips(g, mouseX, mouseY);
        }
    }

    /**
     * 面板本体，按开场 / 收场进度裁剪（分镜见 {@link GuideIntro}）
     *
     * <ol>
     *   <li>展开带：以面板中线为轴的一条横带，高度随展开 / 收拢变化；板材只画在带里；</li>
     *   <li>光束之上：顶栏、正文、底栏、输入框只画在扫描光束已经扫过的部分；</li>
     *   <li>带里再叠显影光幕、收场时自下而上盖住内容的封存护板；</li>
     *   <li>外框：描线没走完时画描线（不受展开带裁剪，跑在展开前面）；描完后画常态外框（裁在展开带上下多留 3px 给辉光）；
     *       收场收线时又画回倒着走的描线。</li>
     * </ol>
     * 终态下两层裁剪都不开，和没有动画时的绘制完全一样。
     */
    private void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick, boolean ready, float delta, long now) {
        int bandH = fx.bandHeight();
        if (bandH <= 0) {
            fx.drawTrace(g, GuideTheme.CHAMFER);
            return;
        }
        int bandTop = fx.bandTop();
        boolean clipBand = bandH < panelH;
        if (clipBand) {
            g.enableScissor(panelX - 3, bandTop, panelX + panelW + 3, bandTop + bandH);
        }
        CodexTheme.chamferFill(g, panelX, panelY, panelW, panelH, GuideTheme.CHAMFER, GuideTheme.PLATE);
        CodexTheme.plating(g, panelX, panelY, panelW, panelH, GuideTheme.CHAMFER, 1f);
        CodexTheme.inlay(g, panelX, panelY, panelW, panelH, GuideTheme.CHAMFER, 1f);

        int reveal = fx.revealBottom();
        if (reveal > panelY) {
            boolean clipBeam = reveal < panelY + panelH;
            if (clipBeam) {
                g.enableScissor(panelX, panelY, panelX + panelW, reveal);
            }
            renderHeader(g, mouseX, mouseY, ready, delta, 0);
            // 顶栏上的几粒 MRU 微光粒画在顶栏紫带之上（紫带是不透明的，画在底下就看不见了）；安静区挡掉了正文区和底栏
            quietMotes();
            motes.render(g, panelX, panelY, panelW, panelH, GuideTheme.CHAMFER, rc.time, 1f);
            renderBody(g, delta, ready, now);
            renderFooter(g, 0);
            // 顶栏下沿的 MRU 光带（静止）在正文之后画：带子有 3 像素，下面两层落在正文区顶上，先画会被左栏底色盖掉一半
            CodexTheme.mruDataLine(g, panelX + 1, panelY + GuideTheme.HEADER_H, panelW - 2);
            super.render(g, mouseX, mouseY, partialTick);
            if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
                // 占位提示自己画：EditBox 自带的 hint 颜色不可调，在这套暗色面板上太亮
                g.drawString(this.font, searchHint, searchBox.getX(), searchBox.getY(), GuideTheme.FAINT, false);
            }
            if (clipBeam) {
                g.disableScissor();
            }
        }
        fx.drawBeam(g, GuideTheme.CHAMFER);
        fx.drawSeal(g, GuideTheme.CHAMFER);
        if (clipBand) {
            g.disableScissor();
        }

        if (fx.traceDone()) {
            if (clipBand) {
                g.enableScissor(panelX - 3, bandTop - 3, panelX + panelW + 3, bandTop + bandH + 3);
            }
            CodexTheme.chamferGlow(g, panelX, panelY, panelW, panelH, GuideTheme.CHAMFER, fx.outlineColor(), fx.glowStrength());
            if (clipBand) {
                g.disableScissor();
            }
        } else {
            fx.drawTrace(g, GuideTheme.CHAMFER);
        }
    }

    /**
     * 登记这一帧 MRU 微光粒的安静区（见 {@link CodexTheme.MruMotes}，安静区里一粒都不画）：顶栏左边的书名块、
     * 右边的按钮和搜索框、<b>整个正文区</b>（首页格子区、左栏、阅读栏连同两侧留白）、整条底栏 ——
     * 微光粒只落在顶栏中间的空白里，读字的地方周围一粒都没有（上一版阅读栏两侧的留白里也有）
     */
    private void quietMotes() {
        motes.clearQuiet();
        int top = panelY;
        int headerBottom = top + GuideTheme.HEADER_H;
        motes.quiet(panelX, top, headerQuietRight, headerBottom);
        motes.quiet(navX[0] - 4, top, panelX + panelW, headerBottom);
        motes.quiet(panelX, headerBottom - 3, panelX + panelW, panelY + panelH);
    }

    /**
     * 正文区：当前页；换页后的 {@value GuideIntro#WIPE_MS}ms 里是横向擦除 ——
     * 擦除线一侧画新页、另一侧画旧页（两页的部件都常驻，旧页照原样再画一次就行）。
     * 去阅读页从左往右擦，回首页从右往左擦。旧页不响应鼠标。
     *
     * <p>各部件收到的「开合位移」恒为 0：面板不再平移，参数只是留着没删。</p>
     */
    private void renderBody(GuiGraphics g, float delta, boolean ready, long now) {
        if (wipeAt == 0L || wipeFrom == null || wipeFrom == page) {
            renderPage(g, page, delta, ready, now);
            return;
        }
        float progress = showroomWipe >= 0f ? showroomWipe : Math.min(1f, (now - wipeAt) / GuideIntro.WIPE_MS);
        float e = GuideTheme.easeOutCubic(progress);
        int left = panelX + 1;
        int right = panelX + panelW - 1;
        int span = right - left;
        boolean rightward = page == Page.READ;
        int front = rightward ? left + Math.round(span * e) : right - Math.round(span * e);

        g.enableScissor(rightward ? left : front, bodyY, rightward ? front : right, bodyY + bodyH);
        renderPage(g, page, delta, ready, now);
        g.disableScissor();

        boolean active = rc.mouseActive;
        rc.mouseActive = false;
        g.enableScissor(rightward ? front : left, bodyY, rightward ? right : front, bodyY + bodyH);
        renderPage(g, wipeFrom, delta, false, now);
        g.disableScissor();
        rc.mouseActive = active;

        // 前沿：先画余辉，再画电弧（电弧本身就是前沿，这时不画品红芯线）；都在 z=0，物品图标压在上面
        GuideIntro.drawWipeFront(g, front, bodyY, bodyH, rightward, left, right, !arcs.hasWipeArc());
        arcs.drawWipe(g, front, progress);
    }

    private void renderPage(GuiGraphics g, Page p, float delta, boolean ready, long now) {
        if (p == Page.HOME) {
            // 上一版格子缝里还有十几粒 MRU 光粒在往上飘：首页是找格子、读标题的地方，去掉了
            home.render(rc, delta, 0, ready);
        } else {
            sidebar.render(rc, current, cursor, delta, 0);
            reader.render(rc, view, delta, 0);
        }
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (!rc.hoverStack.isEmpty() || !rc.hoverLines.isEmpty()) {
            // 物品提示 + 图自己的说明行（「可选」「流动的也算」…）+ 书里介绍它的那一篇（「点击查看：…」）
            List<Component> lines = new java.util.ArrayList<>();
            // 显示用的替身（元素水晶）按默认栈出提示，见 GuideStacks
            ItemStack tipStack = GuideStacks.forTooltip(rc.hoverStack);
            if (!tipStack.isEmpty()) {
                lines.addAll(Screen.getTooltipFromItem(this.minecraft, tipStack));
            }
            for (Component line : rc.hoverLines) {
                lines.add(line.copy().withStyle(s -> s.getColor() == null ? s.withColor(GuideTheme.MIST & 0xFFFFFF) : s));
            }
            if (!rc.hoverStack.isEmpty()) {
                GuideView.Target target = view.find(rc.hoverStack.getItem());
                EntryView ev = target == null ? null : view.entries.get(target.entryKey());
                if (ev != null && (ev != current || page != Page.READ) && !view.progress().locked(ev)) {
                    lines.add(Component.translatable("guide.essentialcraft5.ui.goto", ev.title())
                            .withStyle(s -> s.withColor(GuideTheme.LINK & 0xFFFFFF)));
                }
            }
            g.renderTooltip(this.font, lines, tipStack.isEmpty() ? java.util.Optional.empty() : tipStack.getTooltipImage(),
                    mouseX, mouseY);
            return;
        }
        if (page == Page.HOME && wipeAt == 0L) {
            List<Component> tip = home.tooltip(mouseX, mouseY, Util.getMillis());
            if (tip != null) {
                g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
                return;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (GuideTheme.hit(mouseX, mouseY, navX[i], navY, NAV_W, NAV_H)) {
                String key = i == 0 ? "guide.essentialcraft5.ui.nav.back" : i == 1 ? "guide.essentialcraft5.ui.nav.forward" : "guide.essentialcraft5.ui.nav.home";
                g.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
                return;
            }
        }
        if (GuideTheme.hit(mouseX, mouseY, animX, navY, NAV_W, NAV_H)) {
            g.renderComponentTooltip(this.font, List.of(
                    Component.translatable(GuideScaleConfig.introAnimation() ? "guide.essentialcraft5.ui.anim.on" : "guide.essentialcraft5.ui.anim.off"),
                    Component.translatable("guide.essentialcraft5.ui.anim.hint").withStyle(st -> st.withColor(GuideTheme.MIST & 0xFFFFFF))),
                    mouseX, mouseY);
            return;
        }
        if (minecraft != null && GuideTheme.hit(mouseX, mouseY, scaleX, navY, NAV_W, NAV_H)) {
            // 第一行：界面大小：自动（×3）/ ×2（已指定）/ 跟游戏一样（×4）；第二行次要色写怎么调
            g.renderComponentTooltip(this.font, List.of(GuideScale.label(minecraft),
                    Component.translatable("guide.essentialcraft5.ui.scale.hint").withStyle(st -> st.withColor(GuideTheme.MIST & 0xFFFFFF))),
                    mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics g, int mx, int my, boolean ready, float delta, int shift) {
        int top = panelY;
        // 顶栏底色只画在顶栏范围内，但沿用面板的斜切轮廓：直接 fill 整条会把左右上角的切角填平。
        // 半透明地叠在护板上：顶栏是深靛紫的一条带，比中间的护板暗一档。
        // 裁剪框是屏幕坐标，不受 pose 平移影响，要自己加上开合动画的位移
        g.enableScissor(panelX, top + shift, panelX + panelW, top + GuideTheme.HEADER_H + shift);
        CodexTheme.chamferFill(g, panelX + 1, top + 1, panelW - 2, panelH - 2, GuideTheme.CHAMFER - 1, GuideTheme.BAND);
        g.disableScissor();

        // 徽记：斜切的磨砂浅紫徽框里嵌着书本身
        int ex = panelX + GuideTheme.PAD;
        int ey = top + 6;
        GuideTheme.emblem(g, ex, ey, 22, GuideTheme.SLOT, GuideTheme.withAlpha(GuideTheme.GLOW, 0xC0));
        // 左上角一枚紫罗兰角标（静止的装饰不用亮品红，见 GuideTheme.ACCENT）
        CodexTheme.cornerTab(g, ex + 1, ey + 1, 5, GuideTheme.ACCENT);
        g.renderItem(view.bookIcon(), ex + 3, ey + 3);

        int tx = ex + 22 + 7;
        // 书名 + 副标题：开场时书名从附魔台的银河字母逐字显影、副标题从左往右展开，之后就是两次普通的 drawString
        fx.drawTitle(g, this.font, tx, top + 7, top + 19);

        // 不显示「数值来自服务端 / 本地」之类的标签：同步是后台的事，单人游戏里本来也没有「服务器」可言

        // 顶栏按钮：后退 / 前进 / 首页，隔一道结构线是开场动画开关和「Aa」（调界面大小，静止，只有悬停 / 按下态）
        g.fill(animX - 3, navY + 2, animX - 2, navY + NAV_H - 2, GuideTheme.withAlpha(GuideTheme.EDGE, 0xE0));
        boolean animOn = GuideScaleConfig.introAnimation();
        for (int i = 0; i < 5; i++) {
            boolean scale = i == 3;
            boolean anim = i == 4;
            int bx = scale ? scaleX : anim ? animX : navX[i];
            boolean on = scale ? minecraft != null && GuideScale.adjustable(minecraft) : anim || navEnabled(i);
            boolean over = ready && on && GuideTheme.hit(mx, my, bx, navY, NAV_W, NAV_H);
            navHover[i] = GuideTheme.approach(navHover[i], over ? 1f : 0f, delta, 16f);
            if (over && !navWasOver[i]) {
                // 鼠标刚压上去的那一下：按钮左上角斜着跳出一小道基座火花（全局限速）
                arcs.hoverSpark(rc.now, bx, navY, NAV_W, 8);
            }
            navWasOver[i] = over;
            float hv = navHover[i];
            // 三态：常态深灰紫 + 紫罗兰描边；悬停往紫罗兰偏 + 品红描边；按住时暗紫红底
            boolean press = over && rc.mouseDown;
            CodexTheme.chamferFill(g, bx, navY, NAV_W, NAV_H, 3,
                    press ? GuideTheme.PRESS_BG : GuideTheme.lerpColor(GuideTheme.PLATE_DEEP, GuideTheme.PANEL_HOVER, hv));
            CodexTheme.chamferOutline(g, bx, navY, NAV_W, NAV_H, 3, on
                    ? GuideTheme.lerpColor(GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF), GuideTheme.FOCUS, hv)
                    : GuideTheme.withAlpha(GuideTheme.EDGE, 0x70));
            int glyph = !on ? GuideTheme.withAlpha(GuideTheme.FAINT, 0x90)
                    : GuideTheme.lerpColor(GuideTheme.MIST, GuideTheme.PEARL, hv);
            int cx = bx + NAV_W / 2;
            int cy = navY + NAV_H / 2;
            if (scale) {
                GuideTheme.scaleGlyph(g, cx, cy, glyph);
            } else if (anim) {
                // 关着的时候星是暗的、斜杠用品红提示「已关」
                GuideTheme.animGlyph(g, cx, cy, animOn ? glyph : GuideTheme.withAlpha(glyph, 0x90), GuideTheme.FOCUS, animOn);
            } else if (i == 2) {
                GuideTheme.house(g, cx, cy - 1, glyph);
            } else {
                GuideTheme.chevron(g, cx, cy, i == 0, glyph);
            }
        }

        // 搜索框：前导放大镜 + 下划线（比整圈描边轻）；正在输入时转品红
        boolean focused = searchBox != null && searchBox.isFocused();
        int line = focused ? GuideTheme.FOCUS : GuideTheme.EDGE;
        int underY = searchY + 15;
        g.fill(searchX, underY, searchX + searchW, underY + 1, line);
        if (focused) {
            g.fill(searchX, underY + 1, searchX + searchW, underY + 2, GuideTheme.withAlpha(line, 0x40));
        }
        g.fill(searchX, underY - 4, searchX + 1, underY + 1, line);
        g.fill(searchX + searchW - 1, underY - 4, searchX + searchW, underY + 1, line);
        GuideTheme.magnifier(g, searchX + 4, searchY + 4, focused ? GuideTheme.FOCUS : GuideTheme.FAINT);
        clearVisible = searchBox != null && !searchBox.getValue().isEmpty();
        if (clearVisible) {
            clearX = searchX + searchW - 10;
            clearY = searchY + 4;
            boolean over = GuideTheme.hit(mx, my, clearX - 1, clearY - 1, 9, 9);
            int c = over ? GuideTheme.FOCUS : GuideTheme.FAINT;
            for (int i = 0; i < 7; i++) {
                g.fill(clearX + i, clearY + i, clearX + i + 1, clearY + i + 1, c);
                g.fill(clearX + 6 - i, clearY + i, clearX + 7 - i, clearY + i + 1, c);
            }
        }

        // 顶栏下沿的 MRU 流光带在 renderPanel 里、正文之后画
    }

    private void renderFooter(GuiGraphics g, int shift) {
        int fy = panelY + panelH - GuideTheme.FOOTER_H;
        g.enableScissor(panelX, fy + shift, panelX + panelW, panelY + panelH + shift);
        CodexTheme.chamferFill(g, panelX + 1, panelY + 1, panelW - 2, panelH - 2, GuideTheme.CHAMFER - 1, GuideTheme.BAND);
        g.disableScissor();
        g.fill(panelX + 1, fy, panelX + panelW - 1, fy + 1, GuideTheme.withAlpha(GuideTheme.EDGE, 0xE0));

        boolean searching = sidebar.isSearchMode();
        int progressStamp = view.progress().requiredDone();
        if (footerView != view || footerPage != page || footerEntry != current || footerSearch != searching
                || footerProgress != progressStamp) {
            footerProgress = progressStamp;
            footerView = view;
            footerPage = page;
            footerEntry = current;
            footerSearch = searching;
            if (page == Page.HOME) {
                GuideView.Progress progress = view.progress();
                footerStatus = progress.required() > 0
                        ? I18n.get("guide.essentialcraft5.ui.status.home_steps", view.categories.size(), view.entryList.size(),
                        progress.requiredDone(), progress.required())
                        : I18n.get("guide.essentialcraft5.ui.status.home", view.categories.size(), view.entryList.size());
            } else if (current != null) {
                footerStatus = I18n.get("guide.essentialcraft5.ui.status.read", current.cat.title(), current.index + 1, current.cat.entries.size());
            } else {
                footerStatus = "";
            }
            footerStatusW = this.font.width(footerStatus);
            String hintKey = page == Page.HOME ? "guide.essentialcraft5.ui.hint.home"
                    : searching ? "guide.essentialcraft5.ui.hint.search" : "guide.essentialcraft5.ui.hint.read";
            footerHint = GuideTheme.ellipsize(this.font, I18n.get(hintKey),
                    panelW - GuideTheme.PAD * 2 - footerStatusW - 16);
            footerHintW = this.font.width(footerHint);
        }
        int ty = fy + (GuideTheme.FOOTER_H - 8) / 2;
        g.drawString(this.font, footerStatus, panelX + panelW - GuideTheme.PAD - footerStatusW, ty, GuideTheme.MIST, false);
        g.drawString(this.font, footerHint, panelX + GuideTheme.PAD, ty, GuideTheme.FAINT, false);
    }

    // ==================== 打勾电弧 ====================

    /**
     * 每一步上次看到时做完没有（跨开关保留：合上书去做事，回来翻开时刚完成的那几步闪一下）。视图换了（配置 / 内容重载）就重新记，不闪
     */
    @Nullable
    private static boolean[] seenDone;
    private static int seenStamp;
    /** 这一帧要闪的位置（最多 3 处）：左栏 ✓ 的 x、y，或首页的分类格子 */
    private final int[] stepX = new int[3];
    private final int[] stepY = new int[3];
    private final CatView[] stepCat = new CatView[3];
    /** 这一处对应 {@link #seenDone} 的第几个：被限速挡掉时退回「没看到」，下一帧再试 */
    private final int[] stepIdx = new int[3];

    /**
     * 对比每一步做完没有：刚变成「做完了」的，在左栏那一行的 ✓ 上（阅读页）或它所在分类的格子上（首页）闪一下待机电弧。
     * 同一批最多 3 处，一起算一次亮起（{@link GuideArcs#allow}，被挡掉时下一帧再试）；看不见的只记下、不闪。每帧一百多次布尔比较，不分配
     */
    private void checkSteps(long now) {
        GuideView.Progress progress = view.progress();
        List<EntryView> list = view.entryList;
        int n = list.size();
        int stamp = System.identityHashCode(view);
        if (seenDone == null || seenDone.length != n || seenStamp != stamp) {
            seenDone = new boolean[n];
            seenStamp = stamp;
            for (int i = 0; i < n; i++) {
                seenDone[i] = progress.done(list.get(i));
            }
            return;
        }
        int found = 0;
        for (int i = 0; i < n; i++) {
            EntryView ev = list.get(i);
            boolean done = progress.done(ev);
            if (done == seenDone[i]) {
                continue;
            }
            seenDone[i] = done;
            if (!done || found >= 3) {
                continue;
            }
            if (page == Page.READ) {
                int ry = sidebar.entryRowY(ev);
                if (ry != Integer.MIN_VALUE) {
                    stepCat[found] = null;
                    stepX[found] = sidebar.markX();
                    stepY[found] = ry;
                    stepIdx[found] = i;
                    found++;
                }
            } else if (wipeAt == 0L) {
                boolean dup = false;
                for (int k = 0; k < found; k++) {
                    dup |= stepCat[k] == ev.cat;
                }
                if (!dup) {
                    stepCat[found] = ev.cat;
                    stepIdx[found] = i;
                    found++;
                }
            }
        }
        if (found == 0) {
            return;
        }
        if (GuideArcs.allow(now)) {
            for (int k = 0; k < found; k++) {
                if (stepCat[k] == null) {
                    arcs.stepSpark(now, stepX[k], stepY[k], sidebar.rowTextLeft(), sidebar.rowRight());
                } else {
                    home.sparkCategory(arcs, now, stepCat[k]);
                }
            }
        } else {
            // 刚有别的电弧亮过：这几步退回「没看到」，下一帧再试（至多等一秒；那时已经看不见了就只记下、不闪）
            for (int k = 0; k < found; k++) {
                seenDone[stepIdx[k]] = false;
            }
        }
        for (int k = 0; k < found; k++) {
            stepCat[k] = null;
        }
    }

    // ==================== 输入 ====================

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (closingAt != 0L) {
            // 单按的修饰键、F1~F12 不算：物品上按住 Ctrl 查阅后常常一直按着，按住不放的键会不停发重复事件，一收场就会被立刻打断
            if (passiveKey(key)) {
                return false;
            }
            if (parent != null) {
                // 回到的是物品栏这类父界面：这个键（和紧跟着的字符事件）留在手册里吞掉，下一 tick 再关，免得字落进父界面的输入框
                closeNextTick = true;
                return true;
            }
            // 没有父界面：直接关完，这个键交给游戏（关完回到游戏时 W 照样走、E 照样开物品栏）；
            // Esc 例外 —— 交出去会打开暂停菜单
            finishClose();
            return key == GLFW.GLFW_KEY_ESCAPE;
        }
        boolean focused = searchBox.isFocused();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!searchBox.getValue().isEmpty()) {
                searchBox.setValue("");
                unfocusSearch();
                return true;
            }
            if (focused) {
                unfocusSearch();
                return true;
            }
            onClose();
            return true;
        }
        // 开场中按别的键：动画跳到终态，这个键照常处理（F1~F12、单按修饰键不跳）
        if (!passiveKey(key)) {
            skipIntro();
        }
        // Ctrl − / 小键盘 −：字小一档；Ctrl = / 小键盘 +：字大一档；Ctrl 0：回到自动。搜索框聚焦时同样生效（Ctrl 组合不会上屏字）。
        // 查阅打开时一直按着的 Ctrl 不算，要松开过一次（scaleKeysArmed）
        if (hasControlDown() && !hasAltDown() && minecraft != null && scaleKeysArmed) {
            if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
                if (GuideScale.step(minecraft, -1)) {
                    rescale();
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
                if (GuideScale.step(minecraft, 1)) {
                    rescale();
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_KP_0) {
                if (GuideScale.reset(minecraft)) {
                    rescale();
                }
                return true;
            }
        }
        if (hasAltDown() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            if (key == GLFW.GLFW_KEY_LEFT) {
                back();
            } else {
                forward();
            }
            return true;
        }
        if (focused) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                openCursor();
                return true;
            }
            if (sidebar.isSearchMode() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
                moveCursor(key == GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB) {
                unfocusSearch();
                return true;
            }
            searchBox.keyPressed(key, scan, modifiers);
            // 输入框开着时吞掉其余按键：别让字母键触发别的快捷操作
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            back();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB || (hasControlDown() && key == GLFW.GLFW_KEY_F)) {
            focusSearch();
            return true;
        }
        if (page == Page.READ) {
            if (sidebar.isSearchMode() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
                // 左栏是结果列表时 ↑↓ 始终在结果间移动，和底栏提示「↑↓ 选择」一致。
                // 用鼠标点过一条结果后输入框就失焦了，原先这时 ↑↓ 改去滚正文，提示却没变；
                // 正文照样能用 PgUp / PgDn / 空格 / Home / End 和滚轮滚
                moveCursor(key == GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
            float pageStep = bodyH * 0.85f;
            switch (key) {
                case GLFW.GLFW_KEY_UP -> reader.scrollBy(-GuideTheme.LINE_H * 2);
                case GLFW.GLFW_KEY_DOWN -> reader.scrollBy(GuideTheme.LINE_H * 2);
                case GLFW.GLFW_KEY_PAGE_UP -> reader.scrollBy(-pageStep);
                case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> reader.scrollBy(pageStep);
                case GLFW.GLFW_KEY_HOME -> reader.scrollTo(0);
                case GLFW.GLFW_KEY_END -> reader.scrollTo(reader.maxScroll());
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> openCursor();
                default -> {
                    return false;
                }
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            EntryView cont = home.continueTarget();
            if (cont != null) {
                open(cont, !home.continueIsNextStep() && cont.key.equals(lastEntryKey) ? lastScroll : 0f);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            scaleKeysArmed = true;
            scaleScroll = 0D;
        }
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (closingAt != 0L) {
            // 输入法直接上屏（不经过 keyPressed）也算按键：有父界面时吞掉、下一 tick 再关（一次上屏好几个字时其余的字也不落进父界面），没有就直接关完
            if (parent != null) {
                closeNextTick = true;
            } else {
                finishClose();
            }
            return true;
        }
        // 输入法直接上屏的字不经过 keyPressed：这里也要跳过开场
        skipIntro();
        if (!searchBox.isFocused()) {
            // 直接打字就是搜索：不用先去点搜索框
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
            focusSearch();
        }
        return searchBox.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closingAt != 0L) {
            // 收场期间点一下：关完（这一下不交给游戏，免得关书的同时打了一下）。有父界面时等松开再关，松开那一下也留在手册里
            if (parent != null) {
                closeOnRelease = true;
            } else {
                finishClose();
            }
            return true;
        }
        // 开场中点一下：动画跳到终态，这一下照常处理（不吞掉）。面板全程不平移，
        // 终态下的命中区就是玩家看着的位置；还没扫出来的格子此刻也一并成形完毕。
        // 面板展开过半之前例外：只跳过，不当成点到了什么
        boolean early = introEarly();
        skipIntro();
        if (early) {
            return true;
        }
        long now = Util.getMillis();
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            back();
            return true;
        }
        // 开场动画开关：左键右键都是切换
        if ((button == 0 || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) && GuideTheme.hit(mx, my, animX, navY, NAV_W, NAV_H)) {
            if (searchBox.isFocused()) {
                unfocusSearch();
            }
            GuideScaleConfig.setIntroAnimation(!GuideScaleConfig.introAnimation());
            return true;
        }
        // 「Aa」：左键字小一档、右键字大一档（到头了就不动）；右键在这里不是后退
        if ((button == 0 || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) && minecraft != null
                && GuideTheme.hit(mx, my, scaleX, navY, NAV_W, NAV_H)) {
            if (searchBox.isFocused()) {
                unfocusSearch();
            }
            if (GuideScale.step(minecraft, button == 0 ? -1 : 1)) {
                rescale();
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !GuideTheme.hit(mx, my, searchX, searchY, searchW, 18)) {
            // 右键也是后退（和旧手册一样）
            if (searchBox.isFocused()) {
                unfocusSearch();
            }
            back();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
            forward();
            return true;
        }

        // 搜索框：命中范围按外框算，不按 EditBox 自己那条窄窄的文字区
        if (GuideTheme.hit(mx, my, searchX, searchY, searchW, 18)) {
            if (clearVisible && GuideTheme.hit(mx, my, clearX - 2, clearY - 2, 11, 11)) {
                searchBox.setValue("");
            } else if (mx >= searchBox.getX()) {
                // 先让输入框按点击位置放光标；它点在自己范围外会顺手取消聚焦，所以聚焦放在后面
                searchBox.mouseClicked(Math.min(mx, searchBox.getX() + searchBox.getWidth() - 1), searchBox.getY() + 2, button);
            }
            focusSearch();
            return true;
        }
        if (searchBox.isFocused()) {
            // 点别处就收起光标，否则那条闪动的竖线会一直挂在那里
            unfocusSearch();
        }

        if (button == 0) {
            for (int i = 0; i < 3; i++) {
                if (navEnabled(i) && GuideTheme.hit(mx, my, navX[i], navY, NAV_W, NAV_H)) {
                    if (i == 0) {
                        back();
                    } else if (i == 1) {
                        forward();
                    } else {
                        goHome();
                    }
                    return true;
                }
            }
        }

        if (page == Page.HOME) {
            if (home.mouseClicked(mx, my, button)) {
                return true;
            }
            if (button == 0) {
                int t = home.tileAt(mx, my, now);
                CatView cat = home.cat(t);
                if (cat != null) {
                    if (!home.locked(t)) {
                        open(cat.entries.get(0), 0f);
                    }
                    return true;
                }
                if (home.overContinue(mx, my, now)) {
                    EntryView cont = home.continueTarget();
                    open(cont, !home.continueIsNextStep() && cont != null && cont.key.equals(lastEntryKey) ? lastScroll : 0f);
                    return true;
                }
            }
            return super.mouseClicked(mx, my, button);
        }

        if (reader.mouseClicked(mx, my, button) || sidebar.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0) {
            GuideSidebar.Row row = sidebar.rowAt(mx, my);
            if (row != null) {
                if (row.kind == GuideSidebar.Kind.CAT && row.cat != null) {
                    // 点分类名：一步跳到该分类第一篇（已经在这个分类里就回到它的第一篇）
                    open(row.cat.entries.get(0), 0f);
                } else if (row.entry != null) {
                    if (row.kind == GuideSidebar.Kind.RESULT) {
                        cursor = row.resultIndex;
                    }
                    open(row.entry, 0f);
                }
                return true;
            }
            Link link = rc.nextHoverLink;
            if (link != null && link.isLive() && reader.contains(mx, my)) {
                if (link.isItem()) {
                    openItem(link.stack);
                } else {
                    open(view.entries.get(link.entryKey), 0f);
                }
                return true;
            }
            // 物品格（物品块、配方图、结构图）：跳到介绍它的条目
            if (!rc.hoverStack.isEmpty() && reader.contains(mx, my) && openItem(rc.hoverStack)) {
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (page == Page.READ && (reader.mouseDragged(mx, my, button, dx, dy) || sidebar.mouseDragged(my, button))) {
            return true;
        }
        if (page == Page.HOME && home.mouseDragged(my, button)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (closeOnRelease) {
            closeOnRelease = false;
            if (closingAt != 0L && minecraft != null && minecraft.screen == this) {
                finishClose();
            }
            return true;
        }
        // 立体结构图上按下又没怎么拖就松开：当成点了鼠标下的方块，照常跳到介绍它的条目
        if (reader != null && reader.mouseReleased(mx, my, button) && button == 0 && page == Page.READ
                && !rc.hoverStack.isEmpty() && openItem(rc.hoverStack)) {
            sidebar.release();
            home.release();
            return true;
        }
        sidebar.release();
        home.release();
        return super.mouseReleased(mx, my, button);
    }

    // ==================== 展示厅（开发时截图用） ====================

    /**
     * 展示厅截图用：把开场时间轴冻结在某一刻（毫秒；负数恢复正常）。只在开发展示厅里调
     */
    public static void setShowroomFreeze(float millis) {
        GuideIntro.showroomFreeze = millis;
    }

    /**
     * 展示厅截图用：把收场冻结在某一刻（毫秒；负数恢复正常）。冻结期间不会真的关掉，拍完先恢复再 {@code setScreen(null)}。只在开发展示厅里调
     */
    public static void setShowroomCloseFreeze(float millis) {
        GuideIntro.showroomCloseFreeze = millis;
    }

    /**
     * 展示厅截图用：手册的界面倍数（不写配置）。-1 跟游戏、0 自动、≥ 1 指定 —— 这里允许 1 倍（只看版式，不看字）；
     * {@link Integer#MIN_VALUE} 恢复读配置。手册开着时马上按新倍数重建（阅读位置按块锚定）。只在开发展示厅里调
     */
    public static void setShowroomGuideScale(int scale) {
        GuideScale.showroomOverride = scale;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof GuideScreen screen) {
            screen.rescale();
        }
    }

    /** 展示厅日志用：手册现在的倍数和虚拟尺寸，例「×2（640×360）」 */
    public static String showroomScaleInfo() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return "×" + GuideScale.current(mc) + "（" + mc.getWindow().getGuiScaledWidth() + "×" + mc.getWindow().getGuiScaledHeight() + "）";
    }

    /** 展示厅截图用：假鼠标放到「Aa」按钮上（拍悬停提示） */
    public void showroomHoverScale() {
        showroomMouse(scaleX + NAV_W / 2, navY + NAV_H / 2);
    }

    /**
     * 展示厅截图用：翻页擦除停在这个进度（0~1，线性；负数恢复正常）。只在开发展示厅里调
     */
    private static volatile float showroomWipe = -1f;
    /** 展示厅截图用：下一帧强制来一道环境电弧的位置（-1 = 不强制；否则是 GuideArcs.SITE_*：0 顶栏空白、1 底栏空白、2 外框上沿内侧、3 面板外留白） */
    private static volatile int showroomAmbientSite = -1;

    /** 展示厅截图用：把翻页擦除冻结在 progress（0~1；负数恢复正常） */
    public static void setShowroomWipe(float progress) {
        showroomWipe = progress;
    }

    /** 展示厅截图用：事件电弧（悬停、打勾、环境）停在这个年龄（毫秒；负数恢复正常） */
    public static void setShowroomArcAge(float millis) {
        GuideArcs.showroomFreezeAge = millis;
    }

    /** 展示厅截图用：下一帧来一道环境电弧（0 顶栏空白、1 底栏空白、2 外框上沿内侧、3 面板外留白；那一处看不见时换成外框上沿） */
    public static void showroomAmbientArc(int site) {
        showroomAmbientSite = site;
    }

    /** 展示厅截图用：清掉事件电弧，换下一个拍摄对象 */
    public void showroomClearArcs() {
        arcs.clearEvents();
    }

    /** 展示厅截图用：假鼠标放到第 i 个导航按钮上（0 后退、1 前进、2 首页） */
    public void showroomHoverNav(int i) {
        showroomMouse(navX[i] + NAV_W / 2, navY + NAV_H / 2);
    }

    /** 展示厅截图用：假鼠标放到首页第 i 格上 */
    public void showroomHoverTile(int i) {
        int[] c = home.showroomTileCenter(i);
        if (c != null) {
            showroomMouse(c[0], c[1]);
        }
    }

    /** 展示厅截图用：假鼠标放到「下一步」条上 */
    public void showroomHoverContinue() {
        int[] c = home.showroomContinueCenter();
        showroomMouse(c[0], c[1]);
    }

    /** 展示厅：上一次 {@link #showroomOpen} 定位到的块 */
    private int showroomBlock = -1;

    /** 展示厅：假鼠标位置（GUI 坐标，负数 = 用真鼠标） */
    private static volatile int showroomMouseX = -1;
    private static volatile int showroomMouseY = -1;

    /**
     * 展示厅截图用：开始找页面上第一格某种物品（配方图、结构图的物品格），找到后 {@link #showroomProbeResult} 返回它的中心
     *
     * @param item 要找的物品；null 停止查找并清掉假鼠标
     */
    public static void showroomProbe(@Nullable net.minecraft.world.item.Item item) {
        GuideFrame.showroomProbeX = -1;
        GuideFrame.showroomProbeY = -1;
        GuideFrame.showroomProbe = item;
        if (item == null) {
            showroomMouseX = -1;
            showroomMouseY = -1;
        }
    }

    /** 展示厅截图用：{@link #showroomProbe} 找到的格子中心 {x, y}（GUI 坐标）；还没找到返回 null */
    @Nullable
    public static int[] showroomProbeResult() {
        int x = GuideFrame.showroomProbeX;
        return x < 0 ? null : new int[] {x, GuideFrame.showroomProbeY};
    }

    /** 展示厅截图用：假鼠标放到 (x, y)（GUI 坐标）；负数恢复真鼠标 */
    public static void showroomMouse(int x, int y) {
        showroomMouseX = x;
        showroomMouseY = y;
    }

    /** 展示厅截图用：首页滚到最底（支线那几格） */
    public void showroomHomeEnd() {
        skipIntro();
        goHome();
        home.showroomScrollToEnd();
    }

    /**
     * 展示厅截图用：翻到某一篇、滚到第 block 块（-1 = 篇首）
     *
     * @return 有这一篇
     */
    public boolean showroomOpen(String entryKey, int block) {
        skipIntro();
        EntryView ev = view.entries.get(entryKey);
        if (ev == null) {
            return false;
        }
        open(ev, 0f);
        reader.scrollToBlock(block);
        showroomBlock = block;
        return true;
    }

    /** 展示厅截图用：上次定位的块若是多层结构图，切到第 layer 层（从下往上，0 起） */
    public void showroomSelectLayer(int layer) {
        reader.selectLayer(view, showroomBlock, layer);
    }

    /** 展示厅截图用：上次定位的块若是立体结构图，转到给定角度 */
    public void showroomStructureAngles(float yaw, float pitch) {
        reader.structureAngles(view, showroomBlock, yaw, pitch);
    }

    /** 展示厅截图用：上一帧立体结构图模型区的中心 {x, y}（GUI 坐标）；还没画过返回 null */
    @Nullable
    public static int[] showroomStructureCenter() {
        int x = StructureView3D.showroomCenterX;
        return x < 0 ? null : new int[] {x, StructureView3D.showroomCenterY};
    }

    /** 展示厅截图用：全手册的结构图切到平面图（true）/ 立体图（false） */
    public static void showroomStructureFlat(boolean flat) {
        StructureView3D.showroomFlat(flat);
    }

    /** 展示厅截图用：回首页 */
    public void showroomHome() {
        skipIntro();
        goHome();
    }

    /** 展示厅截图用：在搜索框里输入一个词 */
    public void showroomSearch(String query) {
        skipIntro();
        searchBox.setValue(query);
    }

    /**
     * 展示厅截图用：那一篇里第 nth 个（0 起）某种类型的块的序号（-1 = 没有）。截图配置按「条目 + 块类型」定位，内容改写后不用跟着改
     */
    public int showroomFindBlock(String entryKey, String type, int nth) {
        EntryView ev = view.entries.get(entryKey);
        GuideContent.BlockType wanted = GuideContent.BlockType.of(type);
        if (ev == null || wanted == null) {
            return -1;
        }
        List<GuideContent.Block> blocks = ev.blocks();
        int seen = 0;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).type == wanted && seen++ == nth) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (closingAt != 0L) {
            if (parent != null) {
                closeNextTick = true;
            } else {
                finishClose();
            }
            return true;
        }
        boolean early = introEarly();
        skipIntro();
        if (early) {
            return true;
        }
        // Ctrl + 滚轮：向上字大一档、向下字小一档，这时不滚动正文。滚的量攒到 ±1 才调一档（触控板一次手势连发很多小数）；
        // 查阅打开时一直按着的 Ctrl 不算（要松开过一次），照常滚正文
        if (hasControlDown() && minecraft != null && scaleKeysArmed) {
            if (scaleScroll != 0D && Math.signum(scaleScroll) != Math.signum(delta)) {
                scaleScroll = 0D;
            }
            scaleScroll += delta;
            if (Math.abs(scaleScroll) >= 1D) {
                int dir = scaleScroll > 0D ? 1 : -1;
                scaleScroll = 0D;
                if (GuideScale.step(minecraft, dir)) {
                    rescale();
                }
            }
            return true;
        }
        if (page == Page.HOME) {
            return home.mouseScrolled(delta) || super.mouseScrolled(mx, my, delta);
        }
        if (sidebar.mouseScrolled(mx, my, delta) || reader.mouseScrolled(mx, my, delta)) {
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /**
     * 从生存物品栏查阅时，物品栏 2×2 合成结果格的快照。
     *
     * <p>setScreen(手册) 会调物品栏的 removed()，原版 {@code InventoryMenu#removed} 在客户端也会清空结果格
     * （把合成格的东西退回背包那一步才只在服务端做）。服务端那份结果没动、也不会重发，于是回到物品栏时结果格显示是空的 ——
     * 点它照样能取出来，但看上去像是配方没了，要等合成格里的东西变了才重新显示。手册关掉时放回去。</p>
     *
     * <p>只在玩家还是同一个（没有重生、换维度）、结果格还空着、合成格和拍快照时一样时才放：
     * 手册开着的时候服务端改过合成格的话，新的格子和结果会同步过来，这时不该拿旧的盖掉。</p>
     */
    private record CraftResult(LocalPlayer player, ItemStack result, ItemStack[] grid) {

        @Nullable
        static CraftResult capture(@Nullable Screen parent) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (!(parent instanceof InventoryScreen) || player == null) {
                return null;
            }
            InventoryMenu menu = player.inventoryMenu;
            ItemStack result = menu.getSlot(InventoryMenu.RESULT_SLOT).getItem();
            if (result.isEmpty()) {
                return null;
            }
            ItemStack[] grid = new ItemStack[InventoryMenu.CRAFT_SLOT_END - InventoryMenu.CRAFT_SLOT_START];
            for (int i = 0; i < grid.length; i++) {
                grid[i] = menu.getSlot(InventoryMenu.CRAFT_SLOT_START + i).getItem().copy();
            }
            return new CraftResult(player, result.copy(), grid);
        }

        void restore() {
            if (Minecraft.getInstance().player != player) {
                return;
            }
            InventoryMenu menu = player.inventoryMenu;
            Slot slot = menu.getSlot(InventoryMenu.RESULT_SLOT);
            if (!slot.getItem().isEmpty()) {
                return;
            }
            for (int i = 0; i < grid.length; i++) {
                if (!ItemStack.matches(grid[i], menu.getSlot(InventoryMenu.CRAFT_SLOT_START + i).getItem())) {
                    return;
                }
            }
            slot.set(result.copy());
        }
    }
}
