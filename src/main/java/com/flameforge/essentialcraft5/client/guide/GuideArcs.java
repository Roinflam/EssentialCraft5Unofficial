package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.client.render.ArcEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 研究手册里的 2D 电弧：源质魔法的特斯拉线圈、线圈基座、反应堆到处都在放电，手册也在「事件」上噼啪一下
 *
 * <h3>形状与颜色</h3>
 * <p>形状就是世界里那道电弧：{@link ArcEffect#planar} 按原作 DummyCore {@code Lightning} 的步骤生成主干 8 段、支流 4 段（从主干第 3 点牵到终点，
 * 抖动 ×3）、1~2 段的分叉（长弧 12 条；十几像素的短火花只长 {@value #SHORT_BRANCHES} 条，塞满 12 条会成一团毛刺）。
 * 颜色是原作的电弧预设（色值和出处在 {@link CodexTheme} 的 {@code ARC_*}）：打向目标的放电 {@link CodexTheme#ARC_STRIKE}、
 * 线圈待机电弧 {@link CodexTheme#ARC_IDLE}、线圈基座的小火花 {@link CodexTheme#ARC_SPARK}。
 * 画法照原来世界电弧的两层：外层柔光（宽 3 像素、低不透明度），
 * 里层亮芯（1 像素；主干中心再叠半像素的白芯）。抖动和分叉长度按电弧长短换算成像素，长弧封顶。</p>
 *
 * <p><b>不做镜像折叠</b>：上一版把外框、块上沿的弧按基线镜像到一侧，主干首尾钉在基线上、一半的点被翻到基线上，
 * 画出来是一道发光的直线顶着几个「^」—— 像下划线，不像电弧。现在的短火花都是<b>斜着跳</b>的（像线圈基座侧面那样从块的角上
 * 斜着蹦出去一点），需要限制范围的用夹紧框，基线离框留出抖动的余量。</p>
 *
 * <h3>放在哪里（只在事件上，不满屏常驻）</h3>
 * <ul>
 *   <li><b>开场</b>：外框描线从左右侧边中点起笔时，两个起笔点上各骑着外框噼啪一下（{@value #SIDE_SPARK_AT}ms）；
 *       紧接着从中心向面板四角劈出四道电弧（{@value #STRIKE_AT}ms，{@value #STRIKE_MS}ms 淡完），
 *       之后四角的矩阵节点才卡进来 —— 按开场时间轴 {@link GuideIntro#openT} 算，展示厅冻结开场时电弧也停在同一刻。查阅的短开场不放；</li>
 *   <li><b>收场</b>：外框收线、四个笔头回到左右侧边中点那一刻（收场 {@value #CLOSE_SPARK_AT}ms），两个起笔点上再各噼啪一下，和开场呼应；</li>
 *   <li><b>翻页</b>：首页 ↔ 阅读页横向擦除时，<b>电弧本身就是前沿</b>（前沿那道 1px 品红芯不画了，只留余辉），
 *       跟着前沿走（{@value GuideIntro#WIPE_MS}ms，按擦除进度淡入淡出）；翻页优先，见下面的限速；</li>
 *   <li><b>悬停</b>：鼠标移到导航按钮、首页格子、「下一步」条上的那一下，从它左上角的斜切角斜着往右上方跳出一小道基座火花（{@value #HOVER_MS}ms）；
 *       鼠标不动、滚轮滚格子时不跳；</li>
 *   <li><b>打勾</b>：一步刚做完（开着书时完成，或合上书去做、回来翻开时），左栏那一行 ✓ 的右上方往左上跳一道待机电弧（不压 ✓、不进滚动条那一列），
 *       或首页那一格的左上角跳一道；</li>
 *   <li><b>环境电弧</b>：开场结束后每 {@value #AMBIENT_MIN_MS}~{@value #AMBIENT_MAX_MS}ms 随机一次，150~250ms，亮度打六折，
 *       只在这几处里<b>当前看得见</b>的地方挑：顶栏中间的空白、底栏提示和计数之间的空白、外框上沿内侧那一溜（顶栏内容上面的 5 像素）、
 *       面板外面（留白够 {@value #OUTSIDE_MIN}px 才用；界面缩放大时面板外只剩 4~6px，弧会落到屏幕外）。都不进阅读栏和左栏。</li>
 * </ul>
 *
 * <h3>闪烁安全</h3>
 * <p>每道电弧只亮一次：寿命的前 {@value #ATTACK}% 升到峰值、保持到 {@value #HOLD}%、再二次曲线淡掉，中途不重击、不闪烁。
 * 限速（全局、跨开关）记着最近 {@value #MAX_PER_WINDOW} 次「亮起」的时刻：<b>任意 {@value #WINDOW_MS}ms 里至多 {@value #MAX_PER_WINDOW} 次</b>（{@link #admit}），
 * 普通事件之间还至少隔 {@value #MIN_GAP_MS}ms。<b>翻页优先</b>（{@link #allowPriority}）：只受「一秒三次」约束、不受 350ms 间隔 ——
 * 鼠标移上格子马上点下去是最常见的翻页方式，悬停火花刚亮过，上一版的翻页电弧就被间隔吃掉了；翻页电弧亮起时顺手收掉还亮着的悬停火花。
 * 同时亮的几道（开场左右两个起笔点、四道劈向四角、收场左右两个起笔点、几格同时打勾）各算一次。开场的两次、收场的一次也记进限速（屏幕刚关又开时，前一秒的事件还算数，
 * 装不下就不放开场电弧）。电弧都是 1~3 像素宽的细线，没有全屏或大面积的白闪；颜色是品红（红分量占 RGB 和的 45%~59%），不属于「饱和红闪」。</p>
 *
 * <h3>层次与开销</h3>
 * <p>画在 z=0、面板内容之后：物品图标（z≈150）压在电弧上面，电弧不会盖住图标（翻页前沿的余辉也在 z=0）。折线在事件发生时生成、写进下面这几个
 * 预先开好的 float / byte 数组（{@value #SLOTS} 道 × {@link ArcEffect#MAX_SEGMENTS} 段）；渲染只读数组、往 GUI 缓冲写四边形，
 * 不 new、不拼字符串、不装箱，也不 pushPose（翻页那道的横向位移直接加在坐标上）。</p>
 */
@OnlyIn(Dist.CLIENT)
final class GuideArcs {

    // ==================== 形状参数（原作电弧预设的 curve） ====================

    /** 线圈打向目标的放电（{@code TileMRUCoil} L221）：开场劈向四角用它的 curve */
    static final ArcEffect.Style STRIKE = ArcEffect.Style.COIL_STRIKE;
    /** 线圈待机电弧（{@code TileMRUCoil} L149）：翻页前沿用它的 curve */
    static final ArcEffect.Style IDLE = ArcEffect.Style.COIL_IDLE;
    /** 短火花（悬停、打勾）的分叉条数 */
    static final int SHORT_BRANCHES = 5;

    // ==================== 槽位 ====================

    private static final int SEG = ArcEffect.MAX_SEGMENTS;
    /** 开场：0~3 劈向四角，4、5 是左右起笔点的火花 */
    private static final int INTRO = 6;
    /** 翻页前沿那一道（坐标是相对前沿的，画的时候加上前沿的 x） */
    private static final int WIPE = INTRO;
    private static final int FIRST_EVENT = INTRO + 1;
    /** 悬停、打勾、环境电弧共用的环形槽 */
    private static final int EVENTS = 6;
    private static final int SLOTS = FIRST_EVENT + EVENTS;

    /** 事件槽里装的是哪种（翻页优先时只收掉悬停火花） */
    private static final byte TAG_HOVER = 1;
    private static final byte TAG_STEP = 2;
    private static final byte TAG_AMBIENT = 3;

    // ==================== 环境电弧的位置 ====================

    /** 顶栏中间的空白（书名块和按钮之间） */
    static final int SITE_HEADER = 0;
    /** 底栏中间的空白（左边提示和右边计数之间） */
    static final int SITE_FOOTER = 1;
    /** 外框上沿内侧：顶栏内容上面那 5 像素 */
    static final int SITE_EDGE = 2;
    /** 面板外面的留白（够宽的那几边） */
    static final int SITE_OUTSIDE = 3;
    private static final int SITE_COUNT = 4;
    /** 面板外留白至少这么宽才在外面放（1280×720、界面缩放 2 时只有 10~12，弧贴着屏幕边、几乎看不出来） */
    static final int OUTSIDE_MIN = 14;
    /** 空白至少这么宽才放 */
    private static final int GAP_MIN = 40;

    // ==================== 时间（毫秒） ====================

    /** 开场：左右起笔点的火花（外框描线 {@link GuideIntro#TRACE_START} 起笔后不久） */
    static final float SIDE_SPARK_AT = 900f;
    static final float SIDE_SPARK_MS = 150f;
    /** 开场：从中心劈向四角（光线正在向两侧展开、面板刚开始展开） */
    static final float STRIKE_AT = 960f;
    static final float STRIKE_MS = 210f;
    /** 收场：外框收线回到起笔点（{@link GuideIntro#RETRACT_START} + {@link GuideIntro#RETRACT_MS}），左右再噼啪一下 */
    static final float CLOSE_SPARK_AT = 600f;
    /** 悬停火花的寿命 */
    static final float HOVER_MS = 170f;
    /** 打勾那一下的寿命 */
    static final float STEP_MS = 230f;
    /** 环境电弧的间隔（随机均匀取） */
    static final int AMBIENT_MIN_MS = 8000;
    static final int AMBIENT_MAX_MS = 12000;

    /** 亮度包络：寿命的前 12% 升到峰值、保持到 40%、之后二次曲线淡到 0（常量写成「几成」，文档里拼成百分数） */
    static final int ATTACK = 12;
    static final int HOLD = 40;

    // ==================== 闪烁限速（全局，跨开关） ====================

    /** 普通事件两次亮起之间至少隔多久 */
    static final long MIN_GAP_MS = 350L;
    /** 滑动窗口：任意这么长的时间里…… */
    static final long WINDOW_MS = 1000L;
    /** ……至多亮起这么多次 */
    static final int MAX_PER_WINDOW = 3;
    /** 最近 {@value #MAX_PER_WINDOW} 次亮起的时刻（环形，{@link #onsetNext} 指着最早的那个） */
    private static final long[] onsets = {Long.MIN_VALUE / 4, Long.MIN_VALUE / 4, Long.MIN_VALUE / 4};
    private static int onsetNext;

    /**
     * 展示厅截图用：事件电弧（悬停、打勾、环境）停在这个年龄不再过期（毫秒；负数 = 正常）。冻结时不自己排环境电弧。
     * 只在开发展示厅里设，正常游戏恒为 -1
     */
    static volatile float showroomFreezeAge = -1f;

    // ==================== 预先开好的数组 ====================

    /** 段端点：第 s 道第 i 段在 {@code ((s × SEG) + i) × 4} 起的 4 个 float（x0, y0, x1, y1） */
    private final float[] seg = new float[SLOTS * SEG * 4];
    private final byte[] kinds = new byte[SLOTS * SEG];
    /** 每道几段；0 = 空槽 */
    private final int[] count = new int[SLOTS];
    private final byte[] tag = new byte[SLOTS];
    /** 事件电弧亮起的时刻（{@code Util.getMillis()}）；开场的按开场时间轴，不用它 */
    private final long[] born = new long[SLOTS];
    private final float[] life = new float[SLOTS];
    private final float[] peak = new float[SLOTS];
    private final float[] thick = new float[SLOTS];
    /** 亮芯色、柔光色、主干白芯色（生成时算好） */
    private final int[] core = new int[SLOTS];
    private final int[] glow = new int[SLOTS];
    private final int[] hot = new int[SLOTS];
    private final RandomSource random = RandomSource.createNewThreadLocalInstance();
    private int nextEvent;
    /** 下一次环境电弧的时刻；0 = 还没排 */
    private long nextAmbient;
    /** 开场电弧：0 = 还没到、1 = 放行了、2 = 被限速挡掉（这次开场不放） */
    private int introState;
    /** 收场电弧：同上（每个手册界面只收场一次） */
    private int closeState;
    /** 面板几何（收场火花重新生成时用） */
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    // ==================== 闪烁限速 ====================

    /** 普通事件（悬停、打勾、环境）：离上一次亮起不到 {@value #MIN_GAP_MS}ms、或一秒里已经亮了 3 次，就不行。能的话顺手记下 */
    static boolean allow(long now) {
        return admit(now, MIN_GAP_MS, 1);
    }

    /** 翻页：不管 350ms 间隔（点下去的那一下常常紧跟着悬停火花），只守「任意一秒至多 3 次」 */
    static boolean allowPriority(long now) {
        return admit(now, 0L, 1);
    }

    /**
     * @param gap 离最近一次亮起至少隔多久
     * @param k   这一下要亮起几次（同一刻）
     */
    private static boolean admit(long now, long gap, int k) {
        long newest = onsets[(onsetNext + MAX_PER_WINDOW - 1) % MAX_PER_WINDOW];
        if (now - newest < gap || recent(now) + k > MAX_PER_WINDOW) {
            return false;
        }
        for (int i = 0; i < k; i++) {
            record(now);
        }
        return true;
    }

    /** 最近一秒里亮了几次 */
    private static int recent(long now) {
        int n = 0;
        for (int i = 0; i < MAX_PER_WINDOW; i++) {
            if (now - onsets[i] < WINDOW_MS) {
                n++;
            }
        }
        return n;
    }

    private static void record(long at) {
        onsets[onsetNext] = at;
        onsetNext = (onsetNext + 1) % MAX_PER_WINDOW;
    }

    // ==================== 生成 ====================

    /**
     * 把第 s 道电弧的折线生成进数组
     *
     * @param color    亮芯色（{@link CodexTheme} 的 {@code ARC_*}）
     * @param branches 分叉条数（长弧 {@link ArcEffect#BRANCHES}，短火花 {@link #SHORT_BRANCHES}）
     * @param cl       夹紧范围（左、上、右、下；右 ≤ 左 表示不夹）
     */
    private void build(int s, int color, float x0, float y0, float x1, float y1, float curve, float reach, int branches,
                       float cl, float ct, float cr, float cb) {
        int first = s * SEG;
        int n = ArcEffect.planar(random, x0, y0, x1, y1, curve, reach, branches, seg, kinds, first);
        if (cr > cl) {
            int from = first * 4;
            int to = (first + n) * 4;
            for (int i = from; i < to; i += 2) {
                seg[i] = Math.max(cl, Math.min(cr, seg[i]));
                seg[i + 1] = Math.max(ct, Math.min(cb, seg[i + 1]));
            }
        }
        count[s] = n;
        core[s] = color;
        glow[s] = CodexTheme.arcGlow(color);
        hot[s] = CodexTheme.arcHot(color);
    }

    /**
     * 主干抖动（像素）：原作的 curve 是按一道约 0.7 格长的电弧定的，这里按长度等比换算，再封顶 —— 一道横跨半屏的弧照比例抖会晃出几十像素
     */
    private static float curvePx(ArcEffect.Style style, float len, float cap) {
        return Math.min(cap, style.curve() / 0.7f * len);
    }

    /** 事件电弧占一个环形槽 */
    private int eventSlot() {
        int s = FIRST_EVENT + nextEvent;
        nextEvent = (nextEvent + 1) % EVENTS;
        return s;
    }

    // ==================== 开场 ====================

    /**
     * 按面板几何生成开场的六道电弧（尺寸变了重新生成）：四道从中心劈向四角，两道骑在左右侧边中点的外框上
     */
    void layoutIntro(int px, int py, int pw, int ph) {
        panelX = px;
        panelY = py;
        panelW = pw;
        panelH = ph;
        float cx = px + pw / 2;
        float cy = py + ph / 2;
        float[] ends = {px, py, px + pw - 1, py, px, py + ph - 1, px + pw - 1, py + ph - 1};
        for (int k = 0; k < 4; k++) {
            float ex = ends[k * 2];
            float ey = ends[k * 2 + 1];
            float len = (float) Math.sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy));
            build(k, CodexTheme.ARC_STRIKE, cx, cy, ex, ey, curvePx(STRIKE, len, 8f), Math.min(len / 4f, 16f), ArcEffect.BRANCHES, 0, 0, 0, 0);
            peak[k] = 0.95f;
            thick[k] = 1f;
            life[k] = STRIKE_MS;
        }
        // 起笔点：骑在外框那一列上竖着噼啪一小道（这时面板还没展开，框里是空的；界面缩放大时框外只有几像素，放外面会出屏幕）
        build(4, CodexTheme.ARC_SPARK, px, cy - 14, px, cy + 14, 1.8f, 4f, ArcEffect.BRANCHES, 0, 0, 0, 0);
        build(5, CodexTheme.ARC_SPARK, px + pw - 1, cy - 14, px + pw - 1, cy + 14, 1.8f, 4f, ArcEffect.BRANCHES, 0, 0, 0, 0);
        for (int k = 4; k <= 5; k++) {
            peak[k] = 0.9f;
            thick[k] = 0.85f;
            life[k] = SIDE_SPARK_MS;
        }
        introState = 0;
    }

    /**
     * 开场电弧（按开场时间轴画，展示厅冻结开场时停在同一刻）
     *
     * @param openT  开场时间轴上的位置（毫秒）
     * @param fade   开场没走完就关时第一、二幕残留的淡出程度（{@link GuideIntro#stale}），电弧跟着淡掉
     * @param quick  查阅的短开场：不放
     * @param frozen 展示厅冻结开场（截图用）：不走限速
     * @param now    {@code Util.getMillis()}（第一次画出来时记进限速）
     */
    void drawIntro(GuiGraphics g, float openT, float fade, boolean quick, boolean frozen, long now) {
        if (quick || fade >= 0.99f || openT < SIDE_SPARK_AT || openT > STRIKE_AT + STRIKE_MS) {
            return;
        }
        if (introState == 0) {
            // 左右起笔点、劈向四角是两次亮起（相隔 24ms，位置不同），一起问限速；关了马上又开、前一秒的事件还占着名额时，这次开场不放电弧
            if (frozen) {
                introState = 1;
            } else if (admit(now, 0L, 2)) {
                introState = 1;
            } else {
                introState = 2;
            }
        }
        if (introState != 1) {
            return;
        }
        for (int s = 0; s < INTRO; s++) {
            float start = s < 4 ? STRIKE_AT : SIDE_SPARK_AT;
            float a = envelope((openT - start) / life[s]) * peak[s] * (1f - fade);
            if (a > 0.01f && count[s] > 0) {
                drawSlot(g, s, a, 0f);
            }
        }
    }

    /**
     * 收场电弧：外框收线回到左右侧边中点时，两个起笔点上各噼啪一道线圈基座火花（按收场时间轴画；第一次画出来时问限速，
     * 挡掉了这次收场就不放）。形状每次重新生成，不和开场那两道一模一样
     *
     * @param closeT 收场开始后的毫秒数（没在收场为负）
     * @param frozen 展示厅冻结收场（截图用）：不走限速
     */
    void drawClose(GuiGraphics g, float closeT, boolean frozen, long now) {
        if (closeT < CLOSE_SPARK_AT || closeT > CLOSE_SPARK_AT + SIDE_SPARK_MS || panelW <= 0) {
            return;
        }
        if (closeState == 0) {
            if (frozen || admit(now, 0L, 1)) {
                closeState = 1;
                float cy = panelY + panelH / 2;
                // 和开场同一处、同一种火花（槽 4、5）：这时开场早已走完，槽空着
                build(4, CodexTheme.ARC_SPARK, panelX, cy - 14, panelX, cy + 14, 1.8f, 4f, ArcEffect.BRANCHES, 0, 0, 0, 0);
                build(5, CodexTheme.ARC_SPARK, panelX + panelW - 1, cy - 14, panelX + panelW - 1, cy + 14, 1.8f, 4f, ArcEffect.BRANCHES, 0, 0, 0, 0);
                for (int k = 4; k <= 5; k++) {
                    peak[k] = 0.9f;
                    thick[k] = 0.85f;
                    life[k] = SIDE_SPARK_MS;
                }
            } else {
                closeState = 2;
            }
        }
        if (closeState != 1) {
            return;
        }
        float a = envelope((closeT - CLOSE_SPARK_AT) / SIDE_SPARK_MS);
        for (int s = 4; s <= 5; s++) {
            if (a * peak[s] > 0.01f && count[s] > 0) {
                drawSlot(g, s, a * peak[s], 0f);
            }
        }
    }

    // ==================== 翻页 ====================

    /**
     * 擦除开始：生成前沿那道竖直的电弧（坐标相对前沿），收掉还亮着的悬停火花。一秒里已经亮了 3 次时这次擦除没有电弧（前沿画回品红线）
     *
     * @param top    正文区上沿
     * @param bottom 正文区下沿
     */
    void startWipe(long now, int top, int bottom) {
        count[WIPE] = 0;
        if (bottom - top < 16 || !allowPriority(now)) {
            return;
        }
        clearTag(TAG_HOVER);
        float len = bottom - top;
        build(WIPE, CodexTheme.ARC_IDLE, 0f, top + 2, 0f, bottom - 2, curvePx(IDLE, len, 7f), Math.min(len / 4f, 10f),
                ArcEffect.BRANCHES, 0, 0, 0, 0);
        peak[WIPE] = 0.9f;
        thick[WIPE] = 1f;
        life[WIPE] = GuideIntro.WIPE_MS;
    }

    /** 这次擦除有电弧（前沿就不画品红芯线，电弧本身当前沿） */
    boolean hasWipeArc() {
        return count[WIPE] > 0;
    }

    /**
     * @param front    前沿所在列
     * @param progress 擦除进度 0~1（线性，不是缓动后的）
     */
    void drawWipe(GuiGraphics g, float front, float progress) {
        if (count[WIPE] == 0) {
            return;
        }
        float a = envelope(progress) * peak[WIPE];
        if (a > 0.01f) {
            drawSlot(g, WIPE, a, front + 0.5f);
        }
    }

    // ==================== 事件电弧 ====================

    /**
     * 悬停火花：从块左上角的斜切角斜着往右上方跳出去一小道（像线圈基座侧面的火花），大半落在块上沿外面的缝里
     *
     * @param x    块左
     * @param top  块的上沿（外框那一行）
     * @param w    块宽
     * @param room 上沿外面有多少像素空着（格子之间 5、导航按钮上面 8）：火花夹在这里面，不压到上面那一格
     * @return 亮了没有（被限速挡掉为 false）
     */
    boolean hoverSpark(long now, int x, int top, int w, int room) {
        if (w < 10 || !allow(now)) {
            return false;
        }
        int s = eventSlot();
        cornerSpark(s, CodexTheme.ARC_SPARK, x, top, w, room);
        spawn(s, TAG_HOVER, now, HOVER_MS, 1f, 1f);
        return true;
    }

    /**
     * 块左上角斜着跳出去的一小道：起点在斜切角上（上沿往下 3 像素），终点在右上方、上沿外面（离上面那一格留 1 像素），
     * 横着 12~22 像素、斜 15°~37°。夹在块的左右和「上面那一格之下、块里文字之上」
     */
    private void cornerSpark(int s, int color, int x, int top, int w, int room) {
        int r = Math.max(3, room);
        float sx = x + 1.5f;
        float sy = top + 3f;
        float len = Math.max(12f, Math.min(22f, w * 0.3f));
        float ex = sx + len;
        float ey = top - r + 2;
        build(s, color, sx, sy, ex, ey, 1.9f, 3f, SHORT_BRANCHES, x - 1, top - r + 1, x + w - 1, top + 4);
    }

    /**
     * 打勾那一下（左栏）：从 ✓ 右上方 2 像素外往左上方跳一道待机电弧，横着 20 像素，
     * 夹在这一行文字上面那一溜（上一行的下边距 + 这一行的上边距）里：不压 ✓、不压字、不进右边的滚动条那一列。
     * 不经过限速：调用方已经为这一批问过 {@link #allow}
     *
     * @param markX    ✓ 的左边（7×5，画在 rowY + 5）
     * @param rowY     这一行的上沿
     * @param rowLeft  这一行文字的左边（图标右边）：火花不越过
     * @param rowRight 这一行的右边（滚动条那一列的左边）
     */
    void stepSpark(long now, int markX, int rowY, int rowLeft, int rowRight) {
        int s = eventSlot();
        build(s, CodexTheme.ARC_IDLE, markX + 8f, rowY + 3f, markX - 12f, rowY - 1f, 1.2f, 2.5f, SHORT_BRANCHES,
                rowLeft, rowY - 2, rowRight - 1, rowY + 3);
        spawn(s, TAG_STEP, now, STEP_MS, 1f, 0.85f);
    }

    /** 打勾那一下（首页格子）：和悬停火花同一个位置（左上角斜着跳出去），颜色是待机品红 */
    void stepSparkCorner(long now, int x, int top, int w, int room) {
        int s = eventSlot();
        cornerSpark(s, CodexTheme.ARC_IDLE, x, top, w, room);
        spawn(s, TAG_STEP, now, STEP_MS, 1f, 0.85f);
    }

    private void spawn(int s, byte kind, long now, float lifeMs, float pk, float th) {
        tag[s] = kind;
        born[s] = now;
        life[s] = lifeMs;
        peak[s] = pk;
        thick[s] = th;
    }

    /**
     * 环境电弧：开场结束后每 8~12 秒一次，在当前看得见的几处里随机挑一处（见类注释）
     *
     * @param active 开场走完、没在收场、没在翻页
     * @param head   顶栏空白的左右（书名块右边、按钮左边）
     * @param foot   底栏空白的左右（提示右边、计数左边）
     * @param force  展示厅：立刻来一道（-1 = 不强制；否则是 {@code SITE_*}，那一处看不见时换成外框上沿）
     */
    void ambient(long now, boolean active, int px, int py, int pw, int ph, int cut, int headerH, int footerH,
                 int headL, int headR, int footL, int footR, int screenW, int screenH, int force) {
        if (force < 0) {
            if (!active || showroomFreezeAge >= 0f) {
                nextAmbient = 0L;
                return;
            }
            if (nextAmbient == 0L) {
                nextAmbient = now + AMBIENT_MIN_MS + random.nextInt(AMBIENT_MAX_MS - AMBIENT_MIN_MS + 1);
                return;
            }
            if (now < nextAmbient) {
                return;
            }
        }
        int outside = Math.max(Math.max(py, screenH - (py + ph)), Math.max(px, screenW - (px + pw)));
        int valid = 0;
        if (headR - headL >= GAP_MIN) {
            valid |= 1 << SITE_HEADER;
        }
        if (footR - footL >= GAP_MIN) {
            valid |= 1 << SITE_FOOTER;
        }
        if (pw - cut * 2 - 12 >= GAP_MIN) {
            valid |= 1 << SITE_EDGE;
        }
        if (outside >= OUTSIDE_MIN) {
            valid |= 1 << SITE_OUTSIDE;
        }
        if (valid == 0) {
            nextAmbient = now + AMBIENT_MIN_MS;
            return;
        }
        if (!allow(now)) {
            // 刚有别的电弧亮过：一秒后再试，不挤在一起
            nextAmbient = now + 1000L;
            return;
        }
        nextAmbient = now + AMBIENT_MIN_MS + random.nextInt(AMBIENT_MAX_MS - AMBIENT_MIN_MS + 1);
        int site;
        if (force >= 0) {
            site = (valid & (1 << force)) != 0 ? force : (valid & (1 << SITE_EDGE)) != 0 ? SITE_EDGE : Integer.numberOfTrailingZeros(valid);
        } else {
            int pick = random.nextInt(Integer.bitCount(valid));
            site = 0;
            for (int k = 0; k < SITE_COUNT; k++) {
                if ((valid & (1 << k)) != 0 && pick-- == 0) {
                    site = k;
                    break;
                }
            }
        }
        int s = eventSlot();
        if (site == SITE_HEADER || site == SITE_FOOTER) {
            boolean header = site == SITE_HEADER;
            int left = header ? headL : footL;
            int right = header ? headR : footR;
            int gap = right - left;
            float len = Math.min(60f, gap - 8f) * (0.55f + 0.45f * random.nextFloat());
            float x0 = left + 4 + random.nextFloat() * Math.max(0f, gap - 8 - len);
            if (header) {
                // 顶栏中间：夹在顶栏上下几像素以内（柔光 1.5 像素，不碰顶栏下沿那道线和正文）
                float y0 = py + 11 + random.nextInt(Math.max(1, headerH - 22));
                float y1 = y0 + random.nextInt(9) - 4;
                build(s, CodexTheme.ARC_IDLE, x0, y0, x0 + len, y1, 3f, 5f, ArcEffect.BRANCHES, left, py + 4, right, py + headerH - 5);
            } else {
                // 底栏中间：底栏上沿那道线和外框之间，和提示字同一行高
                int fy = py + ph - footerH;
                float y0 = fy + 7 + random.nextInt(Math.max(1, footerH - 13));
                float y1 = y0 + random.nextInt(5) - 2;
                build(s, CodexTheme.ARC_IDLE, x0, y0, x0 + len, y1, 2.2f, 4f, ArcEffect.BRANCHES, left, fy + 3, right, py + ph - 3);
            }
        } else if (site == SITE_EDGE) {
            // 外框上沿内侧：沿着薰衣草描边往里 1~5 像素那一溜噼啪（顶栏的书名、按钮都在 py + 6 以下）
            int span = pw - cut * 2 - 12;
            float len = Math.min(span, 40f + random.nextInt(31));
            float x0 = px + cut + 6 + random.nextFloat() * (span - len);
            float y0 = py + 3;
            build(s, CodexTheme.ARC_IDLE, x0, y0, x0 + len, y0 + random.nextInt(3) - 1, 1.5f, 3f, ArcEffect.BRANCHES,
                    px + cut, py + 1, px + pw - cut, py + 5);
        } else {
            // 面板外面：从够宽的几边里挑一边，基线在留白正中，夹在留白里（不碰外框、不出屏幕）
            int sides = 0;
            for (int k = 0; k < 4; k++) {
                if (outsideRoom(k, px, py, pw, ph, screenW, screenH) >= OUTSIDE_MIN) {
                    sides |= 1 << k;
                }
            }
            int pick = random.nextInt(Integer.bitCount(sides));
            int side = 0;
            for (int k = 0; k < 4; k++) {
                if ((sides & (1 << k)) != 0 && pick-- == 0) {
                    side = k;
                    break;
                }
            }
            int room = outsideRoom(side, px, py, pw, ph, screenW, screenH);
            float amp = (room - 3) / 2f;
            float curve = Math.min(2.5f, amp * 0.5f);
            float reach = Math.min(5f, amp);
            boolean horizontal = side <= 1;
            int span = (horizontal ? pw : ph) - cut * 2 - 12;
            float len = Math.max(20f, Math.min(span, 30f + random.nextInt(41)));
            float along = random.nextFloat() * Math.max(0f, span - len);
            if (horizontal) {
                float x0 = px + cut + 6 + along;
                float lo = side == 0 ? 1 : py + ph + 1;
                float hi = side == 0 ? py - 2 : screenH - 2;
                float y = (lo + hi) / 2f;
                build(s, CodexTheme.ARC_IDLE, x0, y, x0 + len, y, curve, reach, ArcEffect.BRANCHES, px, lo, px + pw, hi);
            } else {
                float y0 = py + cut + 6 + along;
                float lo = side == 2 ? 1 : px + pw + 1;
                float hi = side == 2 ? px - 2 : screenW - 2;
                float x = (lo + hi) / 2f;
                build(s, CodexTheme.ARC_IDLE, x, y0, x, y0 + len, curve, reach, ArcEffect.BRANCHES, lo, py, hi, py + ph);
            }
        }
        spawn(s, TAG_AMBIENT, now, 150f + random.nextInt(101), 0.62f, 0.9f);
    }

    /** 面板外第 side 边（0 上、1 下、2 左、3 右）的留白 */
    private static int outsideRoom(int side, int px, int py, int pw, int ph, int screenW, int screenH) {
        return switch (side) {
            case 0 -> py;
            case 1 -> screenH - (py + ph);
            case 2 -> px;
            default -> screenW - (px + pw);
        };
    }

    /** 清掉所有事件电弧（展示厅换拍摄对象时用；翻页那道也清） */
    void clearEvents() {
        for (int s = WIPE; s < SLOTS; s++) {
            count[s] = 0;
        }
    }

    /** 收掉还亮着的悬停火花（首页滚动时：火花钉在屏幕上，格子已经滑走了） */
    void clearHover() {
        clearTag(TAG_HOVER);
    }

    private void clearTag(byte kind) {
        for (int s = FIRST_EVENT; s < SLOTS; s++) {
            if (tag[s] == kind) {
                count[s] = 0;
            }
        }
    }

    /**
     * 画事件电弧（悬停、打勾、环境），到期的顺手清掉
     *
     * @param show 整体亮度倍数：收场时 1 − 盖住程度，按 Esc 那一刻还亮着的弧跟着面板一起淡掉
     */
    void draw(GuiGraphics g, long now, float show) {
        float freeze = showroomFreezeAge;
        for (int s = FIRST_EVENT; s < SLOTS; s++) {
            if (count[s] == 0) {
                continue;
            }
            float age = freeze >= 0f ? Math.min(freeze, life[s] * 0.99f) : now - born[s];
            if (age >= life[s]) {
                count[s] = 0;
                continue;
            }
            float a = envelope(age / life[s]) * peak[s] * show;
            if (a > 0.01f) {
                drawSlot(g, s, a, 0f);
            }
        }
    }

    // ==================== 画 ====================

    /** 亮度包络：前 {@value #ATTACK}% 升、保持到 {@value #HOLD}%、二次曲线淡掉；只亮一次 */
    static float envelope(float t) {
        if (t <= 0f || t >= 1f) {
            return 0f;
        }
        float attack = ATTACK / 100f;
        float hold = HOLD / 100f;
        if (t < attack) {
            return t / attack;
        }
        if (t < hold) {
            return 1f;
        }
        float k = 1f - (t - hold) / (1f - hold);
        return k * k;
    }

    /**
     * 一道电弧的两层：先整道画外层柔光，再画亮芯，主干中心再叠一道白芯
     *
     * @param a  不透明度 0~1（包络 × 峰值）
     * @param dx 横向位移（翻页那道跟着前沿走）
     */
    private void drawSlot(GuiGraphics g, int s, float a, float dx) {
        int n = count[s];
        int base = s * SEG;
        float th = thick[s];
        int glowC = glow[s];
        int coreC = core[s];
        int hotC = hot[s];
        int trunk = ArcEffect.Kind.TRUNK.ordinal();
        int fork = ArcEffect.Kind.FORK.ordinal();
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < n; i++) {
                int k = kinds[base + i];
                if (pass == 2 && k != trunk) {
                    continue;
                }
                float width;
                float alpha;
                int color;
                if (pass == 0) {
                    width = (k == trunk ? 3f : k == fork ? 2.4f : 1.8f) * th;
                    alpha = a * (k == trunk ? 0.26f : k == fork ? 0.2f : 0.15f);
                    color = glowC;
                } else if (pass == 1) {
                    width = (k == trunk ? 1f : k == fork ? 0.9f : 0.7f) * th;
                    alpha = a * (k == trunk ? 0.92f : k == fork ? 0.8f : 0.62f);
                    color = coreC;
                } else {
                    width = 0.5f * th;
                    alpha = a * 0.85f;
                    color = hotC;
                }
                int c = GuideTheme.withAlpha(color, (int) (255 * alpha));
                if ((c >>> 24) <= 2) {
                    continue;
                }
                int o = (base + i) * 4;
                CodexTheme.segment(g, seg[o] + dx, seg[o + 1], seg[o + 2] + dx, seg[o + 3], width, c, c);
            }
        }
    }
}
