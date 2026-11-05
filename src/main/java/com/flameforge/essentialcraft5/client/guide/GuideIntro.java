package com.flameforge.essentialcraft5.client.guide;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 研究手册的开场 / 收场 / 翻页动效：研究手册显影 v2（2026-10-13 重排，方案 tools/plan/guide_scale.md §3、§4）
 *
 * <p>面板本身从头到尾<b>不平移</b>，只改变「哪一部分被画出来」（裁剪）和叠加在上面的光效 ——
 * 所以任何时刻控件都在它最终的位置上，点击的命中区和看到的东西不会对不上。分镜是源质魔法自己的：MRU 光粒汇聚成蓝紫光点（像 MRU 聚集体）、
 * 品红能量描出灵魂五芒星法阵、紫色光幕显影、书名由附魔台的银河字母逐字显影。颜色只有 MRU 的蓝紫 / 紫罗兰 / 品红
 * （{@link CodexTheme#mru}）和丁香白的光芯，法阵、光粒都是程序化画的。不做色偏和故障效果。</p>
 *
 * <h3>计时</h3>
 * <p>开场按<b>逐帧累加</b>的时钟走（{@code GuideScreen} 每帧加上本帧间隔、单帧最多记 {@value #MAX_FRAME_MS}ms）：打开手册的头几帧
 * （建视图、第一次画物品图标、字形图集）常常一帧一两百毫秒，按墙钟算会直接吃掉光粒和半个法阵。20fps 以上完全不受影响，
 * 更低时开场按比例变慢、不会被跳掉；最小化时没有帧，开场暂停。收场按墙钟算（{@code Util.getMillis()}），最小化时由界面的 tick 兜底关掉。</p>
 *
 * <h3>开场分镜（毫秒，开场时间轴，共 {@value #OPEN_MS}）</h3>
 * <p>整体放慢会让展开和光幕拖沓、收尾发黏，所以重排成三幕：前两幕把中心的仪式（光粒、法阵）放慢到看得清，第三幕的展开和显影只比旧版稍慢。</p>
 * <pre>
 *  第一幕 汇聚
 *    0 ─  260  遮罩淡入（缓出，深靛紫）
 *    0 ─  480  24 粒 MRU 光粒沿两条旋臂向心旋进（半径按 easeInCubic 收到 0，同时转 0.6 圈），每粒拖 2 像素渐淡的尾迹，越近越亮
 *  400 ─  860  蓝紫光点（MRU 聚集体那样：蓝紫光晕、紫罗兰光球、丁香白芯）400 ─ 460 升到峰值后稳定亮着，800 ─ 860 被光线接走；只升起一次，不闪
 *  第二幕 法阵
 *  460 ─  760  外环顺时针描一圈（缓出），环外辉光随着描；十枚节点随外环点亮
 *  500 ─  790  内环逆时针描一圈
 *  560 ─  860  灵魂五芒星按 0 → 2 → 4 → 1 → 3 → 0 五笔连起来，每笔约 60ms
 *  860 ─ 1080  法阵成形：五个顶点依次亮一粒丁香白十字光（间隔 25ms、各 120ms 淡掉），整阵继续缓转
 *  800 ─  940  光点拉成 MRU 光线（丁香白芯，上下两层蓝紫到品红的渐变辉光），从中心向两侧展开（缓出）
 *  940 ─ 1180  法阵外扩到 1.55 倍、淡出，面板在它背后展开
 *  第三幕 显影
 *  880 ─ 1180  面板以光线为轴上下展开（缓出）；光线分成上下两道外推、渐隐
 *  880 ─ 1160  外框描线：四个丁香白笔头从左右侧边中点出发，在顶边 / 底边中点合拢，外圈一层品红辉光
 *  900         左右起笔点各噼啪一道线圈基座火花；960 ─ 1170 从中心向四角劈出四道线圈电弧（{@link GuideArcs}）
 * 1060 ─ 1190  四角矩阵节点 + 薰衣草饰角从外侧 10px 带回弹卡入（easeOutBack），卡到位时闪一下品红十字光
 * 1160 ─ 1400  外框充能：辉光冲到 2.5 倍、偏品红，再回落到常态的薰衣草
 * 1100 ─ 1520  紫色光幕自上而下匀速扫过面板：光幕之上的内容显现（裁剪），拖一段由紫罗兰褪到蓝紫的余辉，后面飘着几粒 MRU 光粒
 * 1100 ─ ~1780 首页格子跟着光幕逐个成形（每格 200ms，行内错开 16ms，见 {@link GuideHome}）
 * 1160 ─ 1300  书名：附魔台的银河字母（minecraft:alt 字体）以薰衣草色从左到右铺开，每 60ms 换一次字形
 * 1300 ─ 1670  逐字显影成真名（最后一个字 1600），显影那一下闪丁香白、70ms 褪回；一道品红短线标出正在显影的那个字
 * 1420 ─ 1660  副标题从左往右展开，前沿带一个品红光标块
 * 1520         就绪（{@value #READY_MS}）：悬停、提示框、按下态生效
 * 1800         开场结束：环境电弧、打勾检查从这里开始；之后只画外框和四角
 * </pre>
 * <p>跳过：开场期间的点击 / 按键 / 滚轮 / 输入法上屏让动画跳到终态、这次输入照常处理；面板展开过半之前（{@value #HALF_UNFOLD}ms 前）
 * 的点击和滚轮<b>只跳过、不处理</b>（那时还看不见内容，终态那个位置可能正好是一个格子）；Esc 从当时的样子收场；F1~F12、单按修饰键不跳。
 * 见 {@code GuideScreen#skipIntro}。</p>
 * <p>查阅短开场（物品上按住 Ctrl、手持手册潜行右键）：从「光线展开」那一刻开始，开场时间轴 {@value #QUICK_FROM} → {@value #OPEN_MS}
 * 用 {@value #QUICK_MS}ms 走完；不画光粒、法阵、顶点十字光，开场电弧不放，光点在 800 ─ 860 被光线接走正好当起点；遮罩约 70ms 淡入。</p>
 *
 * <h3>收场分镜（毫秒，从按下关闭算起，共 {@value #CLOSE_MS}）：和开场倒着呼应，「封存 → 合卷 → 归一」</h3>
 * <pre>
 *  第一幕 封存
 *    0 ─  340  封存光幕自下而上扫过面板（开场光幕倒过来）：光幕之下的内容被护板盖住，余辉拖在下面
 *    0 ─  220  书名从右往左逐字退回银河字母；0 ─ 180 副标题从右往左收起
 *    0 ─  200  外框充能：辉光升到 2.2 倍、偏品红，一直亮到外框收线
 *    0 ─  300  四角矩阵节点闪一下品红十字光（0 ─ 140），随即向外弹开、淡掉（60 ─ 300）
 *  第二幕 合卷
 *  340 ─  620  面板以中线为轴上下收拢（缓入）；上下沿各成一道 MRU 光线，相向合拢
 *  320 ─  600  外框收线：四个笔头从顶边 / 底边中点倒着走回左右侧边中点
 *  600         笔头回到起笔点，左右各噼啪一道线圈基座火花（{@link GuideArcs}，进限速）
 *  第三幕 归一
 *  560 ─  760  法阵从 1.55 倍收回原大、显形（开场外扩的倒放），反向缓转
 *  620 ─  780  光线两端向中心收回；700 ─ 760 收成蓝紫光点
 *  760 ─  900  法阵缩进光点
 *  860 ─ 1000  光粒沿两条旋臂从光点向外散开、渐隐（开场汇聚的倒放）；900 ─ 1000 光点淡掉
 *  560 ─ 1000  遮罩淡出（缓入）
 * </pre>
 * <p>收场期间任意键（单按修饰键、F1~F12 除外）/ 点击直接关完（{@code GuideScreen}）。开场没走完就关：开场时间轴冻结在按下关闭的那一刻（逐帧累加停止），
 * 第一、二幕的东西（光粒、光点、法阵、顶点十字光、开场电弧）在 {@value #STALE_MS}ms 内淡掉，其余从当时的样子接着收；
 * 面板还没开始展开（开场 {@value #UNFOLD_START}ms 前）就关的，没有可封存、可收拢的面板，残留淡掉后直接接第三幕（不放收线火花），整个收场约 600ms。</p>
 *
 * <h3>翻页</h3>
 * <p>首页 ↔ 阅读页：正文区横向擦除 {@value #WIPE_MS}ms（缓出）。擦除线两侧分别是新页和旧页，
 * 前沿是一道跟着走的待机电弧（{@link GuideArcs}），新页一侧拖着紫罗兰余辉；一秒里已经亮过 3 次、电弧被限速挡掉时，前沿画回一道竖直的品红线（丁香白芯）。</p>
 *
 * <h3>闪烁与开销</h3>
 * <p>没有全屏或大面积的闪：遮罩单调淡入淡出；光点、顶点十字光、宝石闪光都是小面积、单次升降；书名显影的丁香白闪是单字；外框充能是 1px 线条的辉光、单次起落。
 * 银河字母的字形序列是静态预分配的，字宽在排版时量好；法阵的圆弧、五芒星是直接写进 GUI 缓冲的四边形，三角函数现算、不分配。
 * 开场结束后除了四角宝石与饰角（约 40 次 fill）和外框，这里什么都不画。</p>
 */
@OnlyIn(Dist.CLIENT)
final class GuideIntro {

    // ==================== 开场时间轴（毫秒） ====================

    static final float SCRIM_MS = 260f;
    /** 第一幕：光粒沿旋臂向心旋进 */
    static final float DUST_MS = 480f;
    /** 蓝紫光点：升起、稳定亮着、被光线接走 */
    static final float SPARK_START = 400f;
    static final float SPARK_RISE_MS = 60f;
    static final float SPARK_OUT_START = 800f;
    static final float SPARK_OUT_MS = 60f;
    /** 第二幕：外环、内环、五芒星 */
    static final float RING_START = 460f;
    static final float RING_MS = 300f;
    static final float INNER_START = 500f;
    static final float INNER_MS = 290f;
    static final float STAR_START = 560f;
    static final float STAR_MS = 300f;
    /** 法阵成形：五个顶点依次亮一粒十字光 */
    static final float VERTEX_START = 860f;
    static final float VERTEX_GAP_MS = 25f;
    static final float VERTEX_MS = 120f;
    /** 光点拉成 MRU 光线 */
    static final float LINE_START = 800f;
    static final float LINE_MS = 140f;
    /** 法阵向外扩开、淡出 */
    static final float CIRCLE_OUT_START = 940f;
    static final float CIRCLE_OUT_MS = 240f;
    /** 第三幕：展开、描框 */
    static final float UNFOLD_START = 880f;
    static final float UNFOLD_MS = 300f;
    static final float TRACE_START = 880f;
    static final float TRACE_MS = 280f;
    /** 外框描完后的充能辉光回落 */
    static final float FLASH_MS = 240f;
    static final float BRACKET_START = 1060f;
    static final float BRACKET_MS = 130f;
    /** 宝石卡到位后那一下闪光的衰减 */
    static final float BRACKET_FLASH_MS = 180f;
    /** 显影光幕匀速扫过整块面板；首页格子按光幕到达自己顶边的时刻开始成形 */
    static final float BEAM_START = 1100f;
    static final float BEAM_MS = 420f;
    static final float TITLE_START = 1160f;
    static final float TITLE_END = 1670f;
    /** 银河字母从左到右铺满一行用的时间 */
    static final float SCRAMBLE_LEAD = 140f;
    /** 银河字母换字形的间隔 */
    static final float SCRAMBLE_STEP_MS = 60f;
    /** 每个字显影时闪丁香白、褪回常态色的时长 */
    static final float SETTLE_FLASH_MS = 70f;
    static final float SUB_START = 1420f;
    static final float SUB_MS = 240f;
    /** 就绪：悬停、提示框、按下态从这里生效（最后 280ms 只剩收尾，不挡操作） */
    static final float READY_MS = 1520f;
    /** 开场结束：环境电弧、打勾检查从这里开始 */
    static final float OPEN_MS = 1800f;
    /** 面板展开过半：在这之前的点击 / 滚轮只跳过开场、不当成点到了什么 */
    static final float HALF_UNFOLD = UNFOLD_START + UNFOLD_MS / 2f;

    /** 逐帧累加时单帧最多记这么多毫秒 */
    static final float MAX_FRAME_MS = 50f;

    /** 查阅短开场：从开场时间轴的这一刻（光线展开）开始 */
    static final float QUICK_FROM = 800f;
    /** 查阅短开场：{@link #QUICK_FROM} → {@link #OPEN_MS} 用这么长的真实时间走完 */
    static final float QUICK_MS = 280f;
    /** 查阅短开场的遮罩淡入（开场时间轴上的毫秒，约合真实 70ms） */
    static final float QUICK_SCRIM_SPAN = 250f;

    // ==================== 收场时间轴（毫秒） ====================

    static final float CLOSE_MS = 1000f;
    /** 开场没走完就关：第一、二幕的残留在这么长时间里淡掉 */
    static final float STALE_MS = 160f;
    /** 第一幕：封存光幕自下而上 */
    static final float SEAL_MS = 340f;
    /** 外框充能升到峰值 */
    static final float CHARGE_MS = 200f;
    /** 书名逐字退回银河字母 / 副标题收起 */
    static final float RUNE_BACK_MS = 220f;
    static final float SUB_BACK_MS = 180f;
    /** 四角节点闪一下（0 ─ {@value #NODE_FLASH_MS}），随即向外弹开淡掉 */
    static final float NODE_FLASH_MS = 140f;
    static final float RELEASE_START = 60f;
    static final float RELEASE_MS = 240f;
    /** 第二幕：面板收拢、外框收线 */
    static final float FOLD_START = 340f;
    static final float FOLD_MS = 280f;
    static final float RETRACT_START = 320f;
    static final float RETRACT_MS = 280f;
    /** 第三幕：法阵收回、光线收拢、光点、法阵缩进光点、光粒散开 */
    static final float RING_BACK_START = 560f;
    static final float RING_BACK_MS = 200f;
    static final float LINE_IN_START = 620f;
    static final float LINE_IN_MS = 160f;
    static final float DOT_START = 700f;
    static final float DOT_RISE_MS = 60f;
    static final float RING_IN_START = 760f;
    static final float RING_IN_MS = 140f;
    static final float SCATTER_START = 860f;
    static final float SCATTER_MS = 140f;
    static final float DOT_OUT_START = 900f;
    static final float DOT_OUT_MS = 100f;
    static final float SCRIM_OUT_START = 560f;

    // ==================== 翻页 ====================

    static final float WIPE_MS = 140f;

    /** 笔头、光幕芯、显影那一下：MRU 光的丁香白芯 */
    static final int HOT = GuideTheme.HOT;

    /**
     * 光幕、封存护板、中线 MRU 光线、法阵这几层叠在内容之上时抬到的 z：物品图标画在 z≈150、数量字在 200、
     * 实体预览更低，留在 z=0 会过不了深度测试 —— 封存时图标会实心地浮在空白护板上，
     * 光幕扫过图标时会从它背后穿过去。300 仍低于 tooltip 的 400（与正文上下渐隐带同一个值）
     */
    private static final float OVERLAY_Z = 300f;

    /** 汇聚 / 散开的 MRU 光粒数量（两条旋臂各一半） */
    private static final int DUST = 24;
    /** 法阵环间的节点数 */
    private static final int RUNES = 10;
    /** 法阵外扩 / 收回的倍数 */
    private static final float CIRCLE_SPREAD = 0.55f;
    private static final float TAU = (float) (Math.PI * 2.0);
    /** 五芒星的笔顺（顶点序号），顶点十字光也按这个顺序亮 */
    private static final int[] STAR_ORDER = {0, 2, 4, 1, 3};

    // ==================== 银河字母（附魔台那套字形，静态预分配） ====================

    /** 附魔台上那串字用的字体（原版 EnchantmentNames 的 ALT_FONT）；加粗：银河字母笔画只有 1 像素，在书名那一行显得太细 */
    private static final Style ALT = Style.EMPTY.withFont(ResourceLocation.withDefaultNamespace("alt")).withBold(true);
    private static final FormattedCharSequence[] GALACTIC = new FormattedCharSequence[26];

    static {
        for (int i = 0; i < 26; i++) {
            GALACTIC[i] = FormattedCharSequence.forward(String.valueOf((char) ('a' + i)), ALT);
        }
    }

    /**
     * 展示厅截图用：把开场时间轴冻结在这一刻（开场时间轴上的毫秒，负数 = 不冻结）。只在开发展示厅里设，正常游戏恒为 -1
     */
    static volatile float showroomFreeze = -1f;
    /**
     * 展示厅截图用：把收场冻结在这一刻（毫秒，负数 = 不冻结；冻结时不会真的关掉）。只在开发展示厅里设，正常游戏恒为 -1
     */
    static volatile float showroomCloseFreeze = -1f;

    // ==================== 本帧状态（update 算好，绘制时只读） ====================

    /** 开场时间轴上的位置（毫秒，封顶 {@link #OPEN_MS}）；收场时冻结在按下关闭的那一刻 */
    float openT;
    /** 收场开始后的毫秒数；没在收场为 -1 */
    float closeT = -1f;
    float scrim;
    /** 中线宽度占面板宽的比例 */
    float line;
    /** 面板展开高度占面板高的比例 */
    float unfold;
    /** 外框描线进度（收场时倒着退回 0） */
    float trace;
    /** 显影光幕位置占面板高的比例；1 = 扫完 */
    float beam;
    /** 收场时内容被封存的程度（封存光幕扫过的比例；电弧、开场光幕跟着它淡掉） */
    float fade;
    /** 收场时开场第一、二幕残留的淡出程度（开场走完才关时用不上） */
    float stale;
    /** 短开场（查阅打开） */
    boolean quick;

    // ==================== 几何与书名排版 ====================

    private int px;
    private int py;
    private int pw;
    private int ph;

    private String title = "";
    private String subtitle = "";
    private int subtitleW;
    private String[] glyphs = new String[0];
    private int[] glyphX = new int[0];
    private int[] glyphW = new int[0];
    private boolean[] blank = new boolean[0];
    private final int[] galacticW = new int[GALACTIC.length];
    /** 光粒位置的草稿（x, y），每帧复用 */
    private final float[] mote = new float[2];

    void setPanel(int x, int y, int w, int h) {
        px = x;
        py = y;
        pw = w;
        ph = h;
    }

    /**
     * 书名按字拆开、量好每个字的位置与宽度（只在排版时做一次）
     *
     * <p>显影时每个字位按<b>真名那个字的宽度</b>占位，银河字母在字位里居中 ——
     * 两者宽窄不一，整串一起画的话后面的字会跟着左右抖。</p>
     */
    void prepareTitle(Font font, String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
        this.subtitleW = font.width(subtitle);
        int n = title.codePointCount(0, title.length());
        glyphs = new String[n];
        glyphX = new int[n];
        glyphW = new int[n];
        blank = new boolean[n];
        int x = 0;
        int i = 0;
        for (int off = 0; off < title.length() && i < n; i++) {
            int cp = title.codePointAt(off);
            int len = Character.charCount(cp);
            String s = title.substring(off, off + len);
            glyphs[i] = s;
            glyphX[i] = x;
            glyphW[i] = font.width(s);
            blank[i] = Character.isWhitespace(cp);
            x += glyphW[i];
            off += len;
        }
        for (int k = 0; k < GALACTIC.length; k++) {
            galacticW[k] = font.width(GALACTIC[k]);
        }
    }

    // ==================== 时间轴 ====================

    /** 逐帧累加的开场时钟走到这里就停（完整开场 {@value #OPEN_MS}、短开场 {@value #QUICK_MS}） */
    float introEnd() {
        return quick ? QUICK_MS : OPEN_MS;
    }

    /**
     * 按当前时间算出本帧各段的进度
     *
     * @param introT    逐帧累加的开场时钟（毫秒，短开场是真实时间，这里换算到开场时间轴）
     * @param closingAt 开始收场的时刻（{@code Util.getMillis()}），没在收场为 0
     * @param now       {@code Util.getMillis()}
     */
    void update(float introT, long closingAt, long now) {
        float closeRaw = -1f;
        if (closingAt == 0L) {
            closeT = -1f;
        } else {
            float frozen = showroomCloseFreeze;
            closeRaw = frozen >= 0f ? Math.min(frozen, CLOSE_MS - 1f) : Math.max(0f, now - closingAt);
            closeT = closeRaw;
        }
        float t = Math.max(0f, introT);
        float mapped = quick ? QUICK_FROM + t * (OPEN_MS - QUICK_FROM) / QUICK_MS : t;
        float freeze = showroomFreeze;
        openT = freeze >= 0f && closeT < 0f ? Math.min(freeze, OPEN_MS) : Math.min(mapped, OPEN_MS);
        scrim = quick ? GuideTheme.easeOutCubic((openT - QUICK_FROM) / QUICK_SCRIM_SPAN) : GuideTheme.easeOutCubic(openT / SCRIM_MS);
        line = GuideTheme.easeOutCubic((openT - LINE_START) / LINE_MS);
        unfold = GuideTheme.easeOutCubic((openT - UNFOLD_START) / UNFOLD_MS);
        trace = GuideTheme.easeOutCubic((openT - TRACE_START) / TRACE_MS);
        beam = Mth.clamp((openT - BEAM_START) / BEAM_MS, 0f, 1f);
        fade = 0f;
        stale = 0f;
        if (closeT >= 0f) {
            stale = GuideTheme.easeOutCubic(closeRaw / STALE_MS);
            if (unfold <= 0f) {
                // 面板还没开始展开就关了：没有可封存、可收拢的面板，第一、二幕的残留淡掉后直接接第三幕（法阵收回、光点、光粒散开），
                // 不让玩家对着一片遮罩干等 400ms
                closeT = closeRaw + (RING_BACK_START - STALE_MS);
            }
            fade = Mth.clamp(closeT / SEAL_MS, 0f, 1f);
            unfold *= 1f - GuideTheme.easeInCubic((closeT - FOLD_START) / FOLD_MS);
            trace *= 1f - GuideTheme.easeInCubic((closeT - RETRACT_START) / RETRACT_MS);
            line *= 1f - GuideTheme.easeInCubic((closeT - LINE_IN_START) / LINE_IN_MS);
            scrim *= 1f - GuideTheme.easeInCubic((closeT - SCRIM_OUT_START) / (CLOSE_MS - SCRIM_OUT_START));
        }
    }

    /**
     * 收场火花用的收场时间：面板还没展开就关的（直接接第三幕）没有外框可收线，不放火花，返回 -1
     */
    float closeSparkT() {
        return closeT >= 0f && unfoldAtClose() ? closeT : -1f;
    }

    /** 收场时面板展开过（按开场时间轴冻结的那一刻算） */
    private boolean unfoldAtClose() {
        return openT > UNFOLD_START;
    }

    /** 收场走完：可以真正关掉界面了 */
    boolean closed() {
        return closeT >= CLOSE_MS;
    }

    /** 就绪且没在收场：悬停、提示框、按下态生效 */
    boolean ready() {
        return closeT < 0f && openT >= READY_MS;
    }

    /** 开场走完且没在收场：环境电弧、打勾检查 */
    boolean settled() {
        return closeT < 0f && openT >= OPEN_MS;
    }

    /** 面板还没展开过半（这时的点击 / 滚轮只跳过开场） */
    boolean early() {
        return closeT < 0f && openT < HALF_UNFOLD;
    }

    /** 面板当前露出来的高度（以面板中线为轴上下展开） */
    int bandHeight() {
        return unfold >= 1f ? ph : Math.round(ph * unfold);
    }

    int bandTop() {
        return py + (ph - bandHeight()) / 2;
    }

    /** 光幕所在行：它上面的内容已经显现 */
    int revealBottom() {
        return beam >= 1f ? py + ph : py + Math.round(ph * beam);
    }

    /** 外框描完了（画常态外框）；收场收线时回到 false，改画倒着走的描线 */
    boolean traceDone() {
        return trace >= 1f;
    }

    /** 外框辉光的充能量：开场描完那一刻冲到顶、{@value #FLASH_MS}ms 回落；收场 {@value #CHARGE_MS}ms 升到顶、一直亮到收线 */
    private float outlineFlash() {
        if (closeT >= 0f) {
            float open = openFlash();
            return Math.max(open, GuideTheme.easeOutCubic(closeT / CHARGE_MS) * 0.8f);
        }
        return openFlash();
    }

    private float openFlash() {
        float since = openT - TRACE_START - TRACE_MS;
        return since < 0f ? 1f : Math.max(0f, 1f - since / FLASH_MS);
    }

    /** 外框辉光强度：开场描完那一刻冲到 2.5 倍，{@value #FLASH_MS}ms 内回落到常态的 1；收场升到约 2.2 倍 */
    float glowStrength() {
        return 1f + 1.5f * outlineFlash();
    }

    /** 外框线色：充能时偏向品红，回落到常态的薰衣草 */
    int outlineColor() {
        return GuideTheme.lerpColor(CodexTheme.withAlpha(GuideTheme.GLOW, 0xB0), GuideTheme.FOCUS, outlineFlash() * 0.7f);
    }

    // ==================== 绘制：中心的仪式 ====================

    /**
     * 光粒 / 中心光点 / 法阵 / 中线（上下两道 MRU 光线）
     *
     * <p>面板还没展开时是屏幕正中的一道线；展开中分成两道，贴着裁剪带的上下沿外推、渐隐；
     * 收场时反过来相向合拢，再向中心收成一粒蓝紫光点，法阵收回来缩进光点，光粒从光点散开。</p>
     *
     * <p>和盖层一样抬到 {@link #OVERLAY_Z}：两道线落在展开带<b>里面</b>，封存护板已经在这些像素上写了 z=300 的深度，
     * 留在 z=0 画会整条过不了深度测试。</p>
     */
    void drawSeam(GuiGraphics g) {
        float keep = 1f - stale;
        float spark = 0f;
        float dustOpen = 0f;
        boolean circleOpen = false;
        float scatter = -1f;
        boolean circleClose = false;
        if (keep > 0.01f) {
            float s = openT < SPARK_START ? 0f
                    : openT < SPARK_START + SPARK_RISE_MS ? GuideTheme.easeOutCubic((openT - SPARK_START) / SPARK_RISE_MS)
                    : openT < SPARK_OUT_START ? 1f
                    : 1f - Mth.clamp((openT - SPARK_OUT_START) / SPARK_OUT_MS, 0f, 1f);
            spark = s * keep;
            if (!quick) {
                dustOpen = openT < DUST_MS ? keep : 0f;
                circleOpen = openT > RING_START && openT < CIRCLE_OUT_START + CIRCLE_OUT_MS;
            }
        }
        if (closeT >= 0f) {
            if (closeT > DOT_START) {
                float d = closeT < DOT_START + DOT_RISE_MS ? GuideTheme.easeOutCubic((closeT - DOT_START) / DOT_RISE_MS)
                        : 1f - GuideTheme.easeInCubic((closeT - DOT_OUT_START) / DOT_OUT_MS);
                spark = Math.max(spark, d);
            }
            circleClose = closeT > RING_BACK_START && closeT < RING_IN_START + RING_IN_MS;
            if (closeT > SCATTER_START) {
                scatter = Math.min(1f, (closeT - SCATTER_START) / SCATTER_MS);
            }
        }
        float k = 1f - unfold;
        boolean lines = k > 0.002f && line > 0.002f;
        if (spark <= 0.01f && dustOpen <= 0f && !circleOpen && !circleClose && scatter < 0f && !lines) {
            // 开场走完后每帧都走到这里：先判空再 pushPose（它每次都新建矩阵），常态下零分配
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        if (dustOpen > 0f) {
            float p = openT / DUST_MS;
            dustLayer(g, p, false, dustOpen * Math.min(1f, p * 5f));
        }
        if (circleOpen) {
            openCircle(g, keep);
        }
        if (circleClose) {
            closeCircle(g);
        }
        seamLayer(g, spark, lines ? k : 0f);
        if (scatter >= 0f && scatter < 1f) {
            dustLayer(g, scatter, true, 1f - scatter);
        }
        g.pose().popPose();
    }

    /**
     * MRU 光粒：24 粒排在两条旋臂上（下标决定在哪条臂、离中心多远，哈希给一点抖动，不存状态）。
     * 汇聚时半径按 easeInCubic 收到 0、同时转 0.6 圈；散开时倒着放（半径缓出放大到 0.9、转回去）。
     * 每粒拖 2 像素渐淡的尾迹（沿运动方向往回），越近中心越亮；柔光粒（芯 + 四邻半透明）为主，四粒里有一粒带十字闪光
     *
     * @param p       进度 0~1
     * @param outward 散开（收场）；false = 汇聚（开场）
     * @param alpha   整体亮度 0~1
     */
    private void dustLayer(GuiGraphics g, float p, boolean outward, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        float reach = Math.max(48f, Math.min(pw, ph) * 0.55f);
        float back = Math.max(0f, p - 0.04f);
        for (int i = 0; i < DUST; i++) {
            motePos(i, back, outward, reach);
            float bx = mote[0];
            float by = mote[1];
            float rf = motePos(i, p, outward, reach);
            float x = mote[0];
            float y = mote[1];
            int h = hash(i * 7919 + 13);
            int color = CodexTheme.moteColor(h >>> 5);
            int a = (int) (0xE0 * alpha * (0.45f + 0.55f * (1f - Math.min(1f, rf))));
            if (a <= 4) {
                continue;
            }
            int ix = Math.round(x);
            int iy = Math.round(y);
            // 尾迹：沿运动方向往回 1、2 像素
            float dx = x - bx;
            float dy = y - by;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 0.3f) {
                float ux = dx / len;
                float uy = dy / len;
                int t1x = Math.round(x - ux);
                int t1y = Math.round(y - uy);
                int t2x = Math.round(x - ux * 2f);
                int t2y = Math.round(y - uy * 2f);
                g.fill(t1x, t1y, t1x + 1, t1y + 1, CodexTheme.withAlpha(color, a / 2));
                g.fill(t2x, t2y, t2x + 1, t2y + 1, CodexTheme.withAlpha(color, a / 5));
            }
            if ((h & 3) == 0) {
                CodexTheme.sparkle(g, ix, iy, 2, color, a);
            } else {
                g.fill(ix, iy, ix + 1, iy + 1, CodexTheme.withAlpha(color, a));
                int halo = CodexTheme.withAlpha(color, a * 2 / 5);
                g.fill(ix - 1, iy, ix, iy + 1, halo);
                g.fill(ix + 1, iy, ix + 2, iy + 1, halo);
                g.fill(ix, iy - 1, ix + 1, iy, halo);
                g.fill(ix, iy + 1, ix + 1, iy + 2, halo);
            }
        }
    }

    /**
     * 第 i 粒光粒在进度 p 时的位置，写进 {@link #mote}
     *
     * @return 半径比例（1 = 在旋臂上的起始位置，0 = 中心）
     */
    private float motePos(int i, float p, boolean outward, float reach) {
        int h = hash(i * 7919 + 13);
        int arm = i & 1;
        int j = i >> 1;
        int perArm = DUST / 2;
        // 旋臂：越往外起始半径越大、角度越往后，两条臂相差半圈
        float r0 = reach * (0.38f + 0.62f * j / (perArm - 1)) * (0.92f + ((h >>> 8) % 17) / 100f);
        float a0 = arm * (float) Math.PI + j * 0.36f + ((h % 100) / 100f - 0.5f) * 0.22f;
        float rf;
        float turn;
        if (outward) {
            float e = GuideTheme.easeOutCubic(p);
            rf = 0.9f * e;
            turn = TAU * 0.6f * (1f - e);
        } else {
            float e = GuideTheme.easeInCubic(p);
            rf = 1f - e;
            turn = TAU * 0.6f * (0.4f * p + 0.6f * e);
        }
        float r = r0 * rf;
        float ang = a0 + turn;
        mote[0] = px + pw / 2f + (float) Math.cos(ang) * r;
        mote[1] = py + ph / 2f + (float) Math.sin(ang) * r * 0.62f;
        return rf;
    }

    /** 法阵的基准半径（面板短边的 20%，夹在 34~90） */
    private float circleRadius() {
        return Mth.clamp(Math.min(pw, ph) * 0.2f, 34f, 90f);
    }

    /** 开场的法阵：描外环、内环、五芒星，成形时五个顶点依次亮十字光，最后外扩淡出 */
    private void openCircle(GuiGraphics g, float keep) {
        float outer = GuideTheme.easeOutCubic((openT - RING_START) / RING_MS);
        if (outer <= 0f) {
            return;
        }
        float inner = GuideTheme.easeOutCubic((openT - INNER_START) / INNER_MS);
        float star = Mth.clamp((openT - STAR_START) / STAR_MS, 0f, 1f) * 5f;
        float out = Mth.clamp((openT - CIRCLE_OUT_START) / CIRCLE_OUT_MS, 0f, 1f);
        float alpha = (1f - out * out * (3f - 2f * out)) * keep;
        float factor = 1f + CIRCLE_SPREAD * GuideTheme.easeOutCubic(out);
        float rot = openT / 1000f * 1.1f;
        circleLayer(g, outer, inner, star, factor, alpha, rot, openT / 700f, true);
    }

    /** 收场的法阵：从 1.55 倍收回原大、显形（反向缓转），再缩进光点 */
    private void closeCircle(GuiGraphics g) {
        float factor;
        float alpha;
        if (closeT < RING_IN_START) {
            float p = GuideTheme.easeOutCubic((closeT - RING_BACK_START) / RING_BACK_MS);
            factor = 1f + CIRCLE_SPREAD * (1f - p);
            alpha = p;
        } else {
            float p = Mth.clamp((closeT - RING_IN_START) / RING_IN_MS, 0f, 1f);
            factor = 1f - 0.92f * GuideTheme.easeInCubic(p);
            float tail = Mth.clamp((p - 0.4f) / 0.6f, 0f, 1f);
            alpha = 1f - tail * tail * (3f - 2f * tail);
        }
        float rot = -closeT / 1000f * 1.1f;
        circleLayer(g, 1f, 1f, 5f, factor, alpha, rot, closeT / 700f, false);
    }

    /**
     * 品红能量的法阵（EC5 灵魂五芒星的样子）：外环（顺时针）、内环（逆时针）、五芒星（按 0 → 2 → 4 → 1 → 3 → 0 一笔画）、环间十枚节点。
     * 颜色沿圆周在 MRU 的蓝紫、紫罗兰、品红之间渐变（{@link CodexTheme#mru}）
     *
     * @param outer   外环描到几成（节点跟着点亮）
     * @param inner   内环描到几成
     * @param star    五芒星画到第几笔（0~5，小数是这一笔画了几成）
     * @param factor  半径倍数（外扩 / 收回）
     * @param alpha   整体不透明度
     * @param rot     整阵转角（弧度）
     * @param shift   渐变色相位
     * @param crosses 画成形时的顶点十字光（开场）
     */
    private void circleLayer(GuiGraphics g, float outer, float inner, float star, float factor, float alpha, float rot, float shift,
                             boolean crosses) {
        if (alpha <= 0.01f) {
            return;
        }
        float cx = px + pw / 2f;
        float cy = py + ph / 2f;
        float radius = circleRadius() * factor;
        float sweep = TAU * outer;

        // 外环：实线内外各一圈向外 / 向内渐隐的柔光（贴着实线浓、远端透明）。
        // 等浓度的宽带叠在遮罩和世界画面上像一圈灰烟，不像光
        CodexTheme.mruArc(g, cx, cy, radius + 3.7f, 6f, rot, rot + sweep, shift, alpha * 0.38f, 0f);
        CodexTheme.mruArc(g, cx, cy, radius - 2.2f, 3f, rot, rot + sweep, shift, 0f, alpha * 0.22f);
        CodexTheme.mruArc(g, cx, cy, radius, 1.4f, rot, rot + sweep, shift, alpha * 0.95f);
        // 内环：逆时针
        float innerR = radius * 0.78f;
        if (inner > 0f) {
            CodexTheme.mruArc(g, cx, cy, innerR, 1.1f, -rot, -rot - TAU * inner, shift + 0.5f, alpha * 0.85f);
        }

        // 五芒星：顶点在内环上，一笔一笔连（每笔缓出，看得出是一笔一笔画的）
        float spin = -rot * 0.5f;
        int starA = (int) (0xE0 * alpha);
        for (int j = 0; j < 5 && star > j; j++) {
            float f = GuideTheme.easeOutCubic(Math.min(1f, star - j));
            float a0 = spin + TAU * STAR_ORDER[j] / 5f;
            float a1 = spin + TAU * STAR_ORDER[(j + 1) % 5] / 5f;
            float x0 = cx + (float) Math.sin(a0) * innerR;
            float y0 = cy - (float) Math.cos(a0) * innerR;
            float x1 = cx + (float) Math.sin(a1) * innerR;
            float y1 = cy - (float) Math.cos(a1) * innerR;
            int c0 = CodexTheme.withAlpha(CodexTheme.mru(a0 / TAU + shift), starA);
            int c1 = CodexTheme.withAlpha(CodexTheme.mru((a0 + (a1 - a0) * f) / TAU + shift), starA);
            CodexTheme.segment(g, x0, y0, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, 1f, c0, c1);
        }

        // 节点：两环之间，外环描到哪里就点亮到哪里
        float runeR = (radius + innerR) * 0.5f;
        int runeA = (int) (0xF0 * alpha);
        for (int i = 0; i < RUNES; i++) {
            float at = TAU * i / RUNES;
            if (at > sweep) {
                break;
            }
            float ang = rot + at + TAU / RUNES * 0.5f;
            int rx = Math.round(cx + (float) Math.sin(ang) * runeR);
            int ry = Math.round(cy - (float) Math.cos(ang) * runeR);
            int color = CodexTheme.mru(ang / TAU + shift) & 0x00FFFFFF;
            if ((i & 1) == 0) {
                CodexTheme.sparkle(g, rx, ry, 1, color, runeA);
            } else {
                g.fill(rx, ry, rx + 1, ry + 1, CodexTheme.withAlpha(color, runeA));
            }
        }

        // 法阵成形：五个顶点按笔顺依次亮一粒丁香白十字光（单次升降，小面积）
        if (crosses && openT >= VERTEX_START) {
            for (int j = 0; j < 5; j++) {
                float t = (openT - VERTEX_START - j * VERTEX_GAP_MS) / VERTEX_MS;
                if (t <= 0f || t >= 1f) {
                    continue;
                }
                float pop = t < 0.2f ? t / 0.2f : (1f - t) / 0.8f;
                float va = spin + TAU * STAR_ORDER[j] / 5f;
                int vx = Math.round(cx + (float) Math.sin(va) * innerR);
                int vy = Math.round(cy - (float) Math.cos(va) * innerR);
                CodexTheme.sparkle(g, vx, vy, 3, HOT, (int) (0xE8 * pop * alpha));
            }
        }
    }

    /**
     * @param spark 中心光点强度（≤ 0.01 不画）
     * @param k     中线强度（1 − 展开量；0 不画）
     */
    private void seamLayer(GuiGraphics g, float spark, float k) {
        int cx = px + pw / 2;
        int cy = py + ph / 2;
        if (spark > 0.01f) {
            // 蓝紫光点（像一颗 MRU 聚集体）：最外一层蓝紫十字光，一圈蓝紫光晕，紫罗兰光球，丁香白芯
            CodexTheme.sparkle(g, cx, cy, 5, CodexTheme.MRU_BLUE, (int) (0x90 * spark));
            g.fill(cx - 4, cy - 1, cx + 5, cy + 2, CodexTheme.withAlpha(CodexTheme.MRU_BLUE, (int) (0x58 * spark)));
            g.fill(cx - 1, cy - 3, cx + 2, cy + 4, CodexTheme.withAlpha(CodexTheme.MRU_BLUE, (int) (0x58 * spark)));
            g.fill(cx - 2, cy - 1, cx + 3, cy + 2, CodexTheme.withAlpha(CodexTheme.MRU_VIOLET, (int) (0xC8 * spark)));
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, CodexTheme.withAlpha(HOT, (int) (0xF0 * spark)));
        }
        if (k <= 0f) {
            return;
        }
        float a = (float) Math.sqrt(k);
        int lw = Math.max(1, Math.round(pw * line));
        int lx = px + (pw - lw) / 2;
        int band = bandHeight();
        float shift = (closeT >= 0f ? openT + closeT : openT) / 700f;
        if (band <= 1) {
            glowLine(g, lx, cy, lw, a, shift);
            return;
        }
        int top = bandTop();
        glowLine(g, lx, top, lw, a, shift);
        glowLine(g, lx, top + band - 1, lw, a, shift + 0.5f);
    }

    /** 一道 MRU 光线：上下各两层渐淡的蓝紫到品红渐变辉光，中间一道丁香白芯（中段实、两端渐隐） */
    private static void glowLine(GuiGraphics g, int x, int y, int w, float a, float shift) {
        CodexTheme.mruLine(g, x, y - 2, w, (int) (0x2C * a), shift);
        CodexTheme.mruLine(g, x, y - 1, w, (int) (0x90 * a), shift);
        GuideTheme.hairline(g, x, y, w, CodexTheme.withAlpha(HOT, (int) (0xF0 * a)));
        CodexTheme.mruLine(g, x, y + 1, w, (int) (0x90 * a), shift);
        CodexTheme.mruLine(g, x, y + 2, w, (int) (0x2C * a), shift);
    }

    // ==================== 绘制：面板 ====================

    /**
     * 外框描线（描完之后由界面画常态外框，这里不再画）；收场时同一条线倒着收回起笔点
     *
     * <p>从左右侧边中点（弧长 1/4、3/4 周处）出发，各自向上下两个方向走，
     * 走满 1/4 周时四个笔头在顶边、底边中点合拢。线是薰衣草，外面同步描一圈品红的淡辉光，笔头 6px 丁香白。
     * 不受展开带裁剪：开场时外框跑在展开前面，收场时面板在收线的外框里合拢。</p>
     */
    void drawTrace(GuiGraphics g, int cut) {
        if (trace <= 0f || trace >= 1f) {
            return;
        }
        int per = CodexTheme.chamferPerimeter(pw, ph, cut);
        int perGlow = CodexTheme.chamferPerimeter(pw + 2, ph + 2, cut + 1);
        float d = trace * per / 4f;
        float dGlow = trace * perGlow / 4f;
        int lineColor = CodexTheme.withAlpha(GuideTheme.GLOW, 0xD0);
        int glowColor = CodexTheme.withAlpha(GuideTheme.FOCUS, 0x40);
        int headColor = CodexTheme.withAlpha(HOT, 0xFF);
        for (int side = 1; side <= 3; side += 2) {
            float o = per / 4f * side;
            float og = perGlow / 4f * side;
            CodexTheme.chamferArc(g, px - 1, py - 1, pw + 2, ph + 2, cut + 1, og - dGlow, og + dGlow, glowColor);
            CodexTheme.chamferArc(g, px, py, pw, ph, cut, o - d, o + d, lineColor);
            if (d > 6f) {
                CodexTheme.chamferArc(g, px, py, pw, ph, cut, o - d, o - d + 6f, headColor);
                CodexTheme.chamferArc(g, px, py, pw, ph, cut, o + d - 6f, o + d, headColor);
            }
        }
    }

    /**
     * 四角：薰衣草 L 形饰角 + 一粒矩阵节点（紫罗兰菱形、暗品红背光面）。开场从外侧 10px 带回弹卡入，卡到位时闪一下品红十字光，
     * 之后常驻一层淡薰衣草 + 节点；收场先闪一下，再向外弹开、淡掉
     */
    void drawBrackets(GuiGraphics g) {
        if (openT < BRACKET_START) {
            return;
        }
        float p = (openT - BRACKET_START) / BRACKET_MS;
        float vis = Math.min(1f, p / 0.3f);
        int off = Math.round((1f - GuideTheme.easeOutBack(p)) * 10f);
        float since = openT - BRACKET_START - BRACKET_MS;
        float flash = since < 0f ? Math.min(1f, p * 2.5f) : Math.max(0f, 1f - since / BRACKET_FLASH_MS);
        if (closeT >= 0f) {
            float k = closeT / NODE_FLASH_MS;
            float pop = k >= 1f ? 0f : k < 0.43f ? k / 0.43f : (1f - k) / 0.57f;
            flash = Math.max(flash, pop);
            float release = Mth.clamp((closeT - RELEASE_START) / RELEASE_MS, 0f, 1f);
            off += Math.round(release * release * 10f);
            vis *= 1f - release;
        }
        if (vis <= 0.01f) {
            return;
        }
        int alpha = (int) ((0x70 + 0x8F * flash) * vis);
        int color = CodexTheme.withAlpha(GuideTheme.lerpColor(GuideTheme.GLOW, HOT, flash * 0.5f), alpha);
        int m = 3 + off;
        int l = px - m;
        int t = py - m;
        int r = px + pw + m - 1;
        int b = py + ph + m - 1;
        CodexTheme.brackets(g, l, t, pw + m * 2, ph + m * 2, 7, color);
        if (flash > 0.02f) {
            int sa = (int) (0xB0 * flash * vis);
            CodexTheme.sparkle(g, l, t, 4, GuideTheme.FOCUS, sa);
            CodexTheme.sparkle(g, r, t, 4, GuideTheme.FOCUS, sa);
            CodexTheme.sparkle(g, l, b, 4, GuideTheme.FOCUS, sa);
            CodexTheme.sparkle(g, r, b, 4, GuideTheme.FOCUS, sa);
        }
        CodexTheme.gem(g, l, t, vis);
        CodexTheme.gem(g, r, t, vis);
        CodexTheme.gem(g, l, b, vis);
        CodexTheme.gem(g, r, b, vis);
    }

    /**
     * 开场的显影光幕 + 由紫罗兰褪到蓝紫的余辉 + 光幕后飘着的几粒 MRU 光粒（只在光幕扫动期间；开场没走完就关时跟着封存淡掉）
     */
    void drawBeam(GuiGraphics g, int cut) {
        if (beam <= 0f || beam >= 1f) {
            return;
        }
        float a = 1f - fade;
        if (a <= 0.01f) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        beamLayer(g, cut, revealBottom(), a, true, openT - BEAM_START);
        g.pose().popPose();
    }

    /**
     * 收场第一幕：封存光幕自下而上扫过面板，光幕之下画回一层空白护板（底色 + 渐变 + 嵌线，和面板本身一样），盖住内容
     *
     * <p>护板往里收 1 像素（渐变收 2 像素）：外框那一圈不写 z=300 的深度，之后画的外框、收线的笔头、火花才不会被挡住。</p>
     */
    void drawSeal(GuiGraphics g, int cut) {
        if (closeT < 0f || fade <= 0f) {
            return;
        }
        int sealY = py + ph - Math.round(ph * fade);
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        if (sealY < py + ph - 1) {
            g.enableScissor(px, Math.max(py, sealY), px + pw, py + ph);
            CodexTheme.chamferFill(g, px + 1, py + 1, pw - 2, ph - 2, cut - 1, GuideTheme.PLATE);
            CodexTheme.plating(g, px + 2, py + 2, pw - 4, ph - 4, Math.max(2, cut - 2), 1f);
            CodexTheme.inlay(g, px, py, pw, ph, cut, 1f);
            g.disableScissor();
        }
        if (fade < 1f) {
            beamLayer(g, cut, sealY, 1f, false, closeT);
        }
        g.pose().popPose();
    }

    /**
     * 一道扫过面板的光幕：本体整行紫罗兰、中段丁香白芯，走过的一侧拖 16 行二次衰减的余辉（近处紫罗兰、远处褪成蓝紫 ——
     * 像 MRU 渗进护板里），前方一点散光，走过的一侧飘着几粒 MRU 光粒
     *
     * @param by   光幕所在行
     * @param a    整体不透明度
     * @param down 向下扫（开场显影）；false = 向上扫（收场封存）
     * @param t    这道光幕已经走了多少毫秒（光粒上浮用）
     */
    private void beamLayer(GuiGraphics g, int cut, int by, float a, boolean down, float t) {
        int behind = down ? -1 : 1;
        for (int k = 16; k >= 1; k--) {
            float f = 1f - k / 17f;
            int c = GuideTheme.lerpColor(CodexTheme.MRU_BLUE, CodexTheme.MRU_VIOLET, f);
            row(g, by + behind * k, cut, CodexTheme.withAlpha(c, (int) (0x44 * f * f * a)));
        }
        row(g, by - behind, cut, CodexTheme.withAlpha(CodexTheme.MRU_VIOLET, (int) (0x34 * a)));
        row(g, by - behind * 2, cut, CodexTheme.withAlpha(CodexTheme.MRU_VIOLET, (int) (0x14 * a)));
        row(g, by, cut, CodexTheme.withAlpha(CodexTheme.MRU_VIOLET, (int) (0xD0 * a)));
        if (by > py && by < py + ph - 1) {
            int in = inset(by, cut);
            GuideTheme.hairline(g, px + in, by, pw - in * 2, CodexTheme.withAlpha(HOT, (int) (0xD0 * a)));
        }
        int span = pw - cut * 2 - 8;
        if (span <= 32) {
            return;
        }
        for (int k = 0; k < 7; k++) {
            int h = hash(k * 131 + (down ? 7 : 29));
            float rise = t * (0.02f + (h % 5) * 0.008f);
            // 开场：光幕上方、随光幕下移并缓缓上浮；收场：光幕下方、被光幕带着往上走，越落越远
            int yy = down ? by - 3 - (int) ((h >>> 6) % 18 + rise) : by + 3 + (int) ((h >>> 6) % 18 + rise * 0.5f);
            if (yy <= py + 1 || yy >= py + ph - 2) {
                continue;
            }
            int xx = px + cut + 4 + (h >>> 11) % span;
            int color = CodexTheme.moteColor(h >>> 3);
            g.fill(xx, yy, xx + 1, yy + 1, CodexTheme.withAlpha(color, (int) (0xB0 * a)));
        }
    }

    /**
     * 顶栏书名（显影动画）+ 副标题（从左往右展开）；开场走完后就是两次普通的 drawString。
     * 收场时书名从右往左逐字退回银河字母、副标题从右往左收起，随后被封存光幕盖住
     *
     * @param x    文字左
     * @param y    书名行顶
     * @param subY 副标题行顶
     */
    void drawTitle(GuiGraphics g, Font font, int x, int y, int subY) {
        boolean titleDone = openT >= TITLE_END;
        if (titleDone && closeT < 0f) {
            g.drawString(font, title, x, y, GuideTheme.PEARL, true);
            g.drawString(font, subtitle, x, subY, GuideTheme.MIST, false);
            return;
        }
        int n = glyphs.length;
        if (titleDone) {
            runesBack(g, font, x, y, n);
        } else if (openT >= TITLE_START && n > 0) {
            decode(g, font, x, y, n);
        }
        float sp = (openT - SUB_START) / SUB_MS;
        if (sp <= 0f) {
            return;
        }
        float shown = GuideTheme.easeOutCubic(sp);
        boolean cursor = sp < 1f;
        if (sp >= 1f && closeT >= 0f) {
            float back = closeT / SUB_BACK_MS;
            shown = 1f - GuideTheme.easeInCubic(back);
            cursor = back < 1f;
        }
        if (shown >= 1f && !cursor) {
            g.drawString(font, subtitle, x, subY, GuideTheme.MIST, false);
            return;
        }
        int rw = Math.round(subtitleW * shown);
        if (rw > 0) {
            g.enableScissor(x, subY - 1, x + rw, subY + 10);
            g.drawString(font, subtitle, x, subY, GuideTheme.MIST, false);
            g.disableScissor();
        }
        if (cursor) {
            g.fill(x + rw, subY, x + rw + 2, subY + 8, CodexTheme.withAlpha(GuideTheme.FOCUS, 0xC0));
        }
    }

    /** 开场：银河字母从左到右铺开，再逐字显影成真名 */
    private void decode(GuiGraphics g, Font font, int x, int y, int n) {
        float t = openT - TITLE_START;
        float settleSpan = TITLE_END - TITLE_START - SCRAMBLE_LEAD - SETTLE_FLASH_MS;
        int cursor = -1;
        for (int i = 0; i < n; i++) {
            if (t < SCRAMBLE_LEAD * i / n) {
                // 银河字母还没铺到这里，后面的更没到
                break;
            }
            if (blank[i]) {
                continue;
            }
            int gx = x + glyphX[i];
            float settleAt = SCRAMBLE_LEAD + settleSpan * (i + 1) / n;
            if (t >= settleAt) {
                float k = (t - settleAt) / SETTLE_FLASH_MS;
                g.drawString(font, glyphs[i], gx, y, GuideTheme.lerpColor(HOT, GuideTheme.PEARL, k), true);
                continue;
            }
            if (cursor < 0) {
                cursor = i;
            }
            galactic(g, font, i, gx, y, t);
        }
        if (cursor >= 0) {
            int cx = x + glyphX[cursor];
            g.fill(cx, y + 10, cx + Math.max(2, glyphW[cursor] - 1), y + 11, GuideTheme.FOCUS);
        }
    }

    /** 收场：从右往左逐字退回银河字母，一道品红短线标出下一个要退回的字 */
    private void runesBack(GuiGraphics g, Font font, int x, int y, int n) {
        int cursor = -1;
        for (int i = n - 1; i >= 0; i--) {
            if (blank[i]) {
                continue;
            }
            int gx = x + glyphX[i];
            float turnAt = RUNE_BACK_MS * (n - 1 - i) / Math.max(1, n);
            if (closeT < turnAt) {
                if (cursor < 0) {
                    cursor = i;
                }
                g.drawString(font, glyphs[i], gx, y, GuideTheme.PEARL, true);
            } else {
                galactic(g, font, i, gx, y, closeT + 500f);
            }
        }
        if (cursor >= 0 && closeT > 0f) {
            int cx = x + glyphX[cursor];
            g.fill(cx, y + 10, cx + Math.max(2, glyphW[cursor] - 1), y + 11, GuideTheme.FOCUS);
        }
    }

    /** 第 i 个字位上的一个银河字母（每个字位各自的相位，免得整串同一帧一起跳），在字位里居中 */
    private void galactic(GuiGraphics g, Font font, int i, int gx, int y, float t) {
        int h = hash(i * 131 + (int) ((t + i * 23f) / SCRAMBLE_STEP_MS));
        int idx = h % GALACTIC.length;
        int nx = gx + (glyphW[i] - galacticW[idx]) / 2;
        g.drawString(font, GALACTIC[idx], nx, y, CodexTheme.withAlpha(GuideTheme.GLOW, 0xD0), false);
    }

    /**
     * 翻页擦除的前沿：新页那一侧拖 8px 紫罗兰余辉；这次擦除有电弧时电弧本身就是前沿（{@link GuideArcs}，调用方随后画），
     * 没有电弧（一秒里已经亮过 3 次）才画一道竖直的品红线（丁香白芯）。
     * <p>画在 z=0：物品图标（z≈150）压在余辉和前沿线上面，光效不盖图标。代价是正文上下 8px 的渐隐带（写了 z=300 的深度）
     * 里不画前沿 —— 那两条带本来就是「内容淡出」的地方，前沿在那里断开看着就是跟着淡掉。</p>
     *
     * @param x         前沿所在列
     * @param y         上
     * @param h         高
     * @param rightward 是否从左往右擦（新页在左侧）
     * @param left      擦除区左边界（余辉不越界）
     * @param right     擦除区右边界（不含）
     * @param line      画品红前沿线（没有电弧时）
     */
    static void drawWipeFront(GuiGraphics g, int x, int y, int h, boolean rightward, int left, int right, boolean line) {
        int dir = rightward ? -1 : 1;
        for (int k = 8; k >= 1; k--) {
            int xx = x + dir * k;
            if (xx < left || xx >= right) {
                continue;
            }
            float f = 1f - k / 9f;
            g.fill(xx, y, xx + 1, y + h, CodexTheme.withAlpha(CodexTheme.MRU_VIOLET, (int) (0x44 * f * f)));
        }
        if (line && x >= left && x < right) {
            g.fill(x, y, x + 1, y + h, CodexTheme.withAlpha(GuideTheme.FOCUS, 0xD0));
            g.fill(x, y + h / 4, x + 1, y + h - h / 4, CodexTheme.withAlpha(HOT, 0xB0));
        }
    }

    // ==================== 工具 ====================

    /** 面板在第 yy 行的左右内缩：斜切角那几行要往里收，光效不能画到切掉的角上 */
    private int inset(int yy, int cut) {
        int top = yy - py;
        int bottom = py + ph - 1 - yy;
        int in = 1;
        if (top < cut) {
            in = Math.max(in, cut - top);
        }
        if (bottom < cut) {
            in = Math.max(in, cut - bottom);
        }
        return in;
    }

    /** 面板内的一整行（按斜切角内缩；最上最下一行是外框，不画） */
    private void row(GuiGraphics g, int yy, int cut, int color) {
        if (yy <= py || yy >= py + ph - 1 || (color >>> 24) == 0) {
            return;
        }
        int in = inset(yy, cut);
        g.fill(px + in, yy, px + pw - in, yy + 1, color);
    }

    /** 整数哈希（非负）：银河字母、光粒用的伪随机，不分配 Random */
    static int hash(int v) {
        v ^= v >>> 16;
        v *= 0x7feb352d;
        v ^= v >>> 15;
        v *= 0x846ca68b;
        v ^= v >>> 16;
        return v & 0x7FFFFFFF;
    }
}
