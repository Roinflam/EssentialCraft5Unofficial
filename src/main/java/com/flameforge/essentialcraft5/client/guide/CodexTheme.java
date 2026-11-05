package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.client.render.ArcEffect;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * 研究手册的视觉原语与配色：源质魔法的紫色做框，读的地方安静
 *
 * <p>绘制原语（斜切几何、1px 锐线 + 叠层辉光、四角括号、滚动条……）来自作者自己写的通用指南引擎，随 EC5 以 CC BY-SA 发布。
 * 颜色的出处仍是 EC5 自己的贴图（{@code assets/essentialcraft5/textures/}），每个常量的注释里写了取自哪里、为什么这样调。</p>
 *
 * <h3>护眼（2026-09-29 改）</h3>
 * <p>上一版整块底是魔法护板的深紫（#290F49 一带，HSV 饱和度 0.79），「下一步」条是大块品红，阅读栏周围还有流光、微光粒、
 * 呼吸在动 —— 读久了眼干。现在<b>分两层</b>：</p>
 * <ul>
 *   <li><b>读的面</b>（阅读栏、首页格子区）= 护板暮紫 #501050 的色相（约 260°）、饱和度压到 0.24、明度 0.145 的暗灰紫 {@link #PLATE}，
 *       不是纯黑（纯黑配亮字有光晕），也没有微光团；正文是略带暖意的米白（{@code GuideTheme.BODY}，对这块底 11.2:1）；</li>
 *   <li><b>源质紫</b>留给框：外框薰衣草描边和四角矩阵节点、顶栏 / 底栏的护板紫带（{@code GuideTheme.BAND} #261A38）、左栏的暗紫
 *       （{@code GuideTheme.SIDEBAR_BG} #1D142A）、MRU 紫罗兰的结构线 {@link #EDGE}、小标题竖条，和少量的焦点品红 {@link #FOCUS}。
 *       大面积里真正带紫的是这两块（CIELAB 彩度 22 / 17，读的面只有 7），读正文的那一块不紫。</li>
 * </ul>
 * <ul>
 *   <li>焦点 = 品红：{@code item/matrix/*.png} 的矩阵节点 #B000A8、药水 #B800B8、MRU 聚集体的品红漩涡 #F848F8 ——
 *       <b>{@link #FOCUS} 只画小面积</b>（选中竖条、悬停描边、链接下划线、「下一步」的细边和箭头），不再做大块底色；
 *       按下时是它的暗面 {@link #FOCUS_DEEP}；静止装饰（矩阵节点亮面、徽框角标、小标题竖条）用 MRU 紫罗兰 {@link #MRU_VIOLET}；</li>
 *   <li>发光 = MRU 的蓝紫到品红渐变（{@link #mru}）：开场法阵、光幕、顶栏下沿那道线；闪电（{@code GuideArcs}）用原作电弧的颜色；</li>
 *   <li>放物品的格子、徽框是比底亮一档的磨砂灰紫（{@link #FROST}），深色物品也看得清。</li>
 * </ul>
 *
 * <h3>实现约束</h3>
 * <p>{@code GuiGraphics.fill} 只能画轴对齐的纯色矩形，所以斜边一律用 1px 矩形堆成阶梯。
 * 渐变、法阵、电弧要逐顶点颜色 / 任意角度，就直接往 {@code RenderType.gui()} 的缓冲里写四边形（{@link #quad}）——
 * 和 fill 是同一个缓冲、同一次绘制调用，层次按调用顺序，不用额外 flush。每帧零分配。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class CodexTheme {

    private CodexTheme() {}

    // ==================== 配色：读的面（低饱和的暗灰紫） ====================

    /**
     * 底的四角：一点点明暗（左上亮、左下暗，差不到 4 级），读起来是一整块平静的面。
     * 色相取魔法护板 {@code block/magicplatingblock.png} 的暮紫 #501050 往 MRU 紫罗兰偏（约 260°），
     * 饱和度从上一版的 0.79 压到 0.24 上下 —— 看得出是紫调的暗灰，不是黑，也不是「一块紫」
     */
    public static final int PLATE_TL = 0xFF211E28;
    public static final int PLATE_TR = 0xFF201C27;
    public static final int PLATE_BL = 0xFF1D1A23;
    public static final int PLATE_BR = 0xFF1F1B25;

    /** 读的面的主色（四角的平均，HSV 260° / 0.24 / 0.145）：阅读栏的上下渐隐带、收场盖板、首页格子区的渐隐都用它 */
    public static final int PLATE = 0xFF1F1C25;

    /** 高一层的板材：表头、步骤徽记、键值表的斑马纹（同色相、同饱和度，亮一档） */
    public static final int PLATE_HI = 0xFF2A2633;

    /**
     * 深色板材：导航按钮、公式框、实体预览框这些不放物品的「凹进去」的块（同色相，暗一档）。
     * <b>放物品的地方不用它</b>：深色物品压在深底上看不见，物品格、徽框用 {@link #FROST}
     */
    public static final int PLATE_DEEP = 0xFF16131B;

    /** 护板嵌线：{@code magicplatingblock_ct_tex.png} 里拼接护板的淡紫双嵌线 #FAD1FE（外框内侧那一圈，alpha 很低） */
    public static final int INLAY = 0xFFFAD1FE;

    // ==================== 配色：物品格（磨砂灰紫） ====================

    /**
     * 磨砂灰紫：物品格、徽框的底。MRU 紫罗兰 #6000C0 冲淡成灰紫（饱和度 0.20），比读的面亮一大档 ——
     * 近黑的物品（米斯莱护板、水晶柱）对它 2.0:1、白色物品 9.2:1，和上一版的浅紫格一样看得清，只是不再发紫
     */
    public static final int FROST = 0xFF4A4556;
    /** 磨砂格的描边：比格子亮一档的灰紫（对读的面 3.8:1） */
    public static final int FROST_EDGE = 0xFF7C7394;
    /** 磨砂格左上角的内高光（受光的一边） */
    public static final int FROST_HI = 0xFF585268;

    // ==================== 配色：结构（MRU 紫罗兰） ====================

    /**
     * 结构线：板材之间的接缝、表格线、格子外框 —— MRU 方块 {@code block/mru.png} 的紫罗兰 #6000C0 提亮、稍微降饱和（0.52），
     * 对读的面 3.0:1。暗灰底上一圈圈细紫线就是这套界面的「源质紫」，线细，不会满眼紫
     */
    public static final int EDGE = 0xFF7556B4;

    /**
     * 薰衣草：结构性发光线、刻度、外框描边、分区标题 —— MRU 紫罗兰 #6000C0 往白提，饱和度从 0.43 收到 0.35
     * （当字用时对读的面 7.2:1，不刺眼）。撑起整套观感的主力，但绝不抢焦点
     */
    public static final int GLOW = 0xFFB49CF0;

    // ==================== 配色：焦点（矩阵 / 药水的品红） ====================

    /**
     * 品红：全屏唯一的焦点色，<b>只画小面积的图形</b>（选中竖条、悬停描边、下划线、光标、「下一步」的细边和箭头）。
     * 取矩阵 {@code item/matrix/} 的亮品红节点与 MRU 聚集体品红漩涡 #F848F8 之间。对读的面 5.3:1，
     * 当文字用太艳：焦点色的文字用 {@code GuideTheme.LINK}
     */
    public static final int FOCUS = 0xFFF040E8;

    /** 品红的暗面：宝石背光面、箭头的下半 —— 矩阵节点 #B000A8 / 药水 #B800B8 压暗 */
    public static final int FOCUS_DEEP = 0xFFA000A8;

    // ==================== 配色：文字 ====================

    /**
     * 主文字（书名、标题、键值表的值）：略带暖意的米白（原作研究书纸页 {@code gui/research_book_generic.png} #FFFAEE 的色相，
     * 饱和度 0.08、明度压到 0.88）—— 不用纯白，对读的面 12.0:1
     */
    public static final int PEARL = 0xFFE0D9CE;

    /** 次要文字：带一点紫的灰（饱和度 0.10），对读的面 6.9:1，最不利处（按下的格子上）5.8:1 */
    public static final int MIST = 0xFFABA3B6;

    /** 更次要：底部提示、空态、锁着的格子，对读的面 5.6:1，最不利处（按下的格子上）4.7:1 */
    public static final int FAINT = 0xFF9992A6;

    // ==================== 配色：MRU 光（蓝紫 → 紫罗兰 → 品红） ====================

    /** MRU 聚集体的蓝紫光球 {@code #5820F8}（示意拼图里的蓝紫球）提亮一点：渐变的冷端 */
    public static final int MRU_BLUE = 0xFF6A40FF;
    /** MRU 方块的紫罗兰 {@code #6000C0 ~ #7000E0} 提亮：渐变的中段 */
    public static final int MRU_VIOLET = 0xFFA050FF;
    /** MRU 聚集体的品红漩涡 {@code #F848F8}：渐变的暖端 */
    public static final int MRU_MAGENTA = 0xFFF050F0;
    /** MRU 光的芯：品红漩涡中心的白里带一点丁香（笔头、光点、光幕芯） */
    public static final int MRU_CORE = 0xFFF4E8FF;

    // ==================== 配色：电弧（原作电弧预设，GuideArcs 用） ====================

    /**
     * 线圈打向目标的放电：原作 {@code TileMRUCoil} L221 {@code new Lightning(..., 1, 0, 0.7)}（{@link ArcEffect.Style#COIL_STRIKE}）= #FF00B3，
     * 品红偏玫红。开场从中心光点劈向四角
     */
    public static final int ARC_STRIKE = arcColor(ArcEffect.Style.COIL_STRIKE);
    /**
     * 线圈待机时身上冒的电弧：原作 {@code TileMRUCoil} L149（1, 0.2, 1，{@link ArcEffect.Style#COIL_IDLE}）= #FF33FF。
     * 翻页前沿、打勾、环境电弧
     */
    public static final int ARC_IDLE = arcColor(ArcEffect.Style.COIL_IDLE);
    /**
     * 线圈基座侧面的小火花：原作 {@code TileMRUCoilHardener} L35（1, 0.1, 0.8，{@link ArcEffect.Style#COIL_HARDENER}）= #FF1ACC。
     * 悬停、开场描框起笔
     */
    public static final int ARC_SPARK = arcColor(ArcEffect.Style.COIL_HARDENER);
    /** 电弧外层柔光 = 预设色往白提这么多（照 {@code ArcRenderer} 的外层：更淡、更宽） */
    public static final float ARC_GLOW_LIFT = 0.45f;
    /** 主干中心那道白芯 = 预设色往白提这么多（原作电弧芯是发白的） */
    public static final float ARC_HOT_LIFT = 0.7f;
    /** 往白提的目标 */
    private static final int ARC_WHITE = 0xFFFFFFFF;

    /** 原作电弧预设的颜色（0~1 的三个分量）→ 不透明 ARGB */
    private static int arcColor(ArcEffect.Style style) {
        return 0xFF000000 | (Math.round(style.red() * 255f) << 16) | (Math.round(style.green() * 255f) << 8) | Math.round(style.blue() * 255f);
    }

    /** 电弧外层柔光色 */
    static int arcGlow(int core) {
        return GuideTheme.lerpColor(core, ARC_WHITE, ARC_GLOW_LIFT);
    }

    /** 电弧主干白芯色 */
    static int arcHot(int core) {
        return GuideTheme.lerpColor(core, ARC_WHITE, ARC_HOT_LIFT);
    }

    /** MRU 渐变的色标（首尾相接）：蓝紫 → 紫罗兰 → 品红 → 紫罗兰 → 回到蓝紫 */
    private static final int[] MRU_RAMP = {MRU_BLUE & 0xFFFFFF, MRU_VIOLET & 0xFFFFFF, MRU_MAGENTA & 0xFFFFFF, MRU_VIOLET & 0xFFFFFF};

    /**
     * MRU 微光粒的颜色：紫罗兰、薰衣草最多，少量品红和蓝紫 —— 全是 MRU 粒子自己的颜色，没有别的色相
     */
    private static final int[] MOTE_COLORS = {0xB48CFF, 0xB48CFF, 0xC8A8FF, 0x9A6CFF, 0xE070F0, 0x7A5CFF};

    // ==================== 尺寸 ====================

    /** 面板四周留白 */
    public static final int MARGIN = 14;
    /**
     * 顶栏高度
     *
     * <p>从 46 降到 34：搜索框原先单独占第二行，现在与计数徽章同排靠右，
     * 顶栏只需要容下一行内容。省下的 12px 全部还给网格。</p>
     */
    public static final int HEADER_H = 34;
    /** 底栏高度 */
    public static final int FOOTER_H = 24;
    /** 内边距 */
    public static final int PAD = 12;
    /** 主框体切角边长 */
    public static final int CHAMFER = 12;
    /** 滚动条宽度 */
    public static final int SCROLLBAR_W = 3;
    /** 滚动滑块最小高度 */
    public static final int THUMB_MIN_H = 18;
    // ==================== 颜色工具 ====================

    /**
     * 换 alpha。
     *
     * @param argb  原色
     * @param alpha 0~255
     * @return 新颜色
     */
    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha))) << 24);
    }

    /**
     * 按比例缩放 alpha。
     *
     * @param argb 原色
     * @param f    0~1
     * @return 新颜色
     */
    public static int fade(int argb, float f) {
        return withAlpha(argb, (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f))));
    }

    /**
     * 缓出三次方。
     *
     * @param t 0~1
     * @return 缓动值
     */
    public static float easeOutCubic(float t) {
        float c = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - c * c * c;
    }

    /** 两个 RGB（不看 alpha）按 k 插值，结果不透明 */
    private static int mixRgb(int a, int b, float k) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /**
     * MRU 渐变上的一点（不透明）
     *
     * @param t 位置，按 1 取模（负数也行）：0 = 蓝紫，0.25 = 紫罗兰，0.5 = 品红，0.75 = 紫罗兰，1 回到蓝紫
     * @return ARGB
     */
    public static int mru(float t) {
        float f = t - (float) Math.floor(t);
        float p = f * MRU_RAMP.length;
        int i = (int) p;
        if (i >= MRU_RAMP.length) {
            i = MRU_RAMP.length - 1;
        }
        return mixRgb(MRU_RAMP[i], MRU_RAMP[(i + 1) % MRU_RAMP.length], p - i);
    }

    /**
     * 第 i 种 MRU 光粒颜色（RGB，alpha 为 0；按颜色表长度取模）：开场汇聚的光粒、首页上浮的光粒用
     *
     * @param i 任意非负整数（一般是哈希）
     */
    public static int moteColor(int i) {
        return MOTE_COLORS[Math.floorMod(i, MOTE_COLORS.length)];
    }

    // ==================== 逐顶点颜色的四边形 ====================

    /**
     * 往 GUI 缓冲里写一个四边形（每个顶点各自的颜色）
     *
     * <p>{@code RenderType.gui()} 开着背面剔除，顶点要和 {@code GuiGraphics.fill} 同一个绕向
     * （屏幕坐标下鞋带公式为负）；反了就倒过来写。三角形写成有两个顶点重合的四边形。</p>
     */
    static void quad(GuiGraphics g, float x0, float y0, int c0, float x1, float y1, int c1,
                     float x2, float y2, int c2, float x3, float y3, int c3) {
        if (((c0 | c1 | c2 | c3) >>> 24) == 0) {
            return;
        }
        float area = (x0 * y1 - x1 * y0) + (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y0 - x0 * y3);
        if (area == 0f) {
            return;
        }
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();
        if (area < 0f) {
            vertex(vc, m, x0, y0, c0);
            vertex(vc, m, x1, y1, c1);
            vertex(vc, m, x2, y2, c2);
            vertex(vc, m, x3, y3, c3);
        } else {
            vertex(vc, m, x3, y3, c3);
            vertex(vc, m, x2, y2, c2);
            vertex(vc, m, x1, y1, c1);
            vertex(vc, m, x0, y0, c0);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, int argb) {
        vc.vertex(m, x, y, 0f).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    /** 左右渐变的矩形 */
    public static void hGradient(GuiGraphics g, int x0, int y0, int x1, int y1, int left, int right) {
        quad(g, x0, y0, left, x0, y1, left, x1, y1, right, x1, y0, right);
    }

    /**
     * 一段粗线，两端各自的颜色（开场法阵的描线）
     *
     * @param width 线宽（像素，可以是小数）
     */
    public static void segment(GuiGraphics g, float x1, float y1, float x2, float y2, float width, int c1, int c2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-3f) {
            return;
        }
        float nx = -dy / len * width * 0.5f;
        float ny = dx / len * width * 0.5f;
        quad(g, x1 + nx, y1 + ny, c1, x1 - nx, y1 - ny, c1, x2 - nx, y2 - ny, c2, x2 + nx, y2 + ny, c2);
    }

    /**
     * MRU 光的圆弧：颜色随角度在蓝紫、紫罗兰、品红之间绕一圈（开场法阵）
     *
     * @param r     半径（线的中心）
     * @param width 线宽
     * @param from  起始角（弧度，0 = 正上方，顺时针）
     * @param to    终止角；小于 from 时逆时针画
     * @param shift 渐变相位（让颜色跟着法阵转）
     * @param alpha 0~1
     */
    public static void mruArc(GuiGraphics g, float cx, float cy, float r, float width, float from, float to,
                              float shift, float alpha) {
        mruArc(g, cx, cy, r, width, from, to, shift, alpha, alpha);
    }

    /**
     * 内外两侧不同浓度的 MRU 圆弧：辉光用（贴着实线的一侧浓、远端透明，就是向外渐隐的一圈柔光，
     * 不会像等浓度的宽带那样看起来是一圈灰烟）
     *
     * @param alphaIn  内沿（半径 r − width/2）的不透明度 0~1
     * @param alphaOut 外沿（半径 r + width/2）的不透明度 0~1
     */
    public static void mruArc(GuiGraphics g, float cx, float cy, float r, float width, float from, float to,
                              float shift, float alphaIn, float alphaOut) {
        int ai = (int) (255 * Math.max(0f, Math.min(1f, alphaIn)));
        int ao = (int) (255 * Math.max(0f, Math.min(1f, alphaOut)));
        float span = to - from;
        if ((ai <= 2 && ao <= 2) || Math.abs(span) < 1.0e-3f) {
            return;
        }
        int n = Math.max(2, (int) Math.ceil(Math.abs(span) / (Math.PI * 2.0 / 72.0)));
        float r0 = r - width * 0.5f;
        float r1 = r + width * 0.5f;
        float prevA = from;
        float ps = (float) Math.sin(prevA);
        float pc = (float) -Math.cos(prevA);
        int prevC = mru(prevA / (float) (Math.PI * 2.0) + shift);
        for (int i = 1; i <= n; i++) {
            float ang = from + span * i / n;
            float s = (float) Math.sin(ang);
            float c = (float) -Math.cos(ang);
            int col = mru(ang / (float) (Math.PI * 2.0) + shift);
            quad(g, cx + ps * r0, cy + pc * r0, withAlpha(prevC, ai), cx + ps * r1, cy + pc * r1, withAlpha(prevC, ao),
                    cx + s * r1, cy + c * r1, withAlpha(col, ao), cx + s * r0, cy + c * r0, withAlpha(col, ai));
            ps = s;
            pc = c;
            prevC = col;
        }
    }

    // ==================== 底：魔法护板 ====================

    /**
     * 读的面：斜切八边形的四角渐变（差别极小，只是让大块面不那么「平涂」）
     *
     * <p>渐变按四角颜色双线性插值到八边形的八个顶点，拆成上梯形、中矩形、下梯形三个四边形，一次绘制调用。
     * 上一版在这上面还叠了三团 MRU 微光（紫罗兰、品红、蓝紫的椭圆），正好压在阅读栏底下、一块深一块浅 ——
     * 读字时眼睛要不停适应底色的明暗，去掉了；紫色只留在框上。</p>
     *
     * @param cut   切角边长
     * @param alpha 0~1（开场收场淡入淡出用）
     */
    public static void plating(GuiGraphics g, int x, int y, int w, int h, int cut, float alpha) {
        if (w <= cut * 2 || h <= cut * 2 || alpha <= 0.01f) {
            return;
        }
        float u = (float) cut / w;
        float v = (float) cut / h;
        int tl = fade(PLATE_TL, alpha);
        int tr = fade(PLATE_TR, alpha);
        int bl = fade(PLATE_BL, alpha);
        int br = fade(PLATE_BR, alpha);
        int topL = bilerp(tl, tr, bl, br, u, 0f);
        int topR = bilerp(tl, tr, bl, br, 1f - u, 0f);
        int midTL = bilerp(tl, tr, bl, br, 0f, v);
        int midTR = bilerp(tl, tr, bl, br, 1f, v);
        int midBL = bilerp(tl, tr, bl, br, 0f, 1f - v);
        int midBR = bilerp(tl, tr, bl, br, 1f, 1f - v);
        int botL = bilerp(tl, tr, bl, br, u, 1f);
        int botR = bilerp(tl, tr, bl, br, 1f - u, 1f);
        int r = x + w;
        int b = y + h;
        quad(g, x + cut, y, topL, x, y + cut, midTL, r, y + cut, midTR, r - cut, y, topR);
        quad(g, x, y + cut, midTL, x, b - cut, midBL, r, b - cut, midBR, r, y + cut, midTR);
        quad(g, x, b - cut, midBL, x + cut, b, botL, r - cut, b, botR, r, b - cut, midBR);
    }

    /**
     * 护板嵌线：外框里面 3 像素处一圈淡淡的淡紫线（{@code magicplatingblock_ct_tex.png} 拼接护板的嵌线），
     * 在底之后、内容之前画，内容的底会盖住它，只在空白处透出来
     *
     * @param alpha 0~1
     */
    public static void inlay(GuiGraphics g, int x, int y, int w, int h, int cut, float alpha) {
        int a = (int) (0x2C * alpha);
        if (a <= 2 || w <= cut * 2 + 8 || h <= cut * 2 + 8) {
            return;
        }
        chamferOutline(g, x + 3, y + 3, w - 6, h - 6, Math.max(2, cut - 2), withAlpha(INLAY, a));
    }

    private static int bilerp(int tl, int tr, int bl, int br, float u, float v) {
        return lerpArgb(lerpArgb(tl, tr, u), lerpArgb(bl, br, u), v);
    }

    private static int lerpArgb(int a, int b, float k) {
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * k);
        return withAlpha(mixRgb(a, b, k), aa);
    }

    /**
     * 四角闪光（MRU 能量闪一下）：中心 1px 丁香白的芯，四臂由内向外变淡
     *
     * @param arm   臂长（像素）
     * @param color RGB（alpha 忽略）
     * @param alpha 0~255
     */
    public static void sparkle(GuiGraphics g, int x, int y, int arm, int color, int alpha) {
        if (alpha <= 4) {
            return;
        }
        for (int k = arm; k >= 1; k--) {
            int a = alpha * (arm + 1 - k) / (arm + 2);
            int c = withAlpha(color, a);
            g.fill(x - k, y, x - k + 1, y + 1, c);
            g.fill(x + k, y, x + k + 1, y + 1, c);
            g.fill(x, y - k, x + 1, y - k + 1, c);
            g.fill(x, y + k, x + 1, y + k + 1, c);
        }
        g.fill(x, y, x + 1, y + 1, withAlpha(MRU_CORE, alpha));
    }

    /**
     * 顶栏上零星几粒 MRU 微光粒：位置按种子预算好、按尺寸缓存，每帧只读数组画 fill，零分配
     *
     * <p>两种：柔光粒（1px 芯 + 四邻半透明的一圈，像游戏里 MRU 粒子那团软光，静态）和极少的四角闪光
     * （极慢地呼吸，周期 {@value #TWINKLE_S} 秒、只在 60%~100% 之间起伏，能整除 {@link GuideTheme#time} 的 720 秒回绕）。
     * 颜色只有 MRU 自己的紫罗兰、薰衣草、品红、蓝紫，不透明度比上一版低一档。</p>
     *
     * <h3>安静区</h3>
     * <p>调用方每帧用 {@link #quiet} 登记「安静区」（最多 {@value #MAX_QUIET} 块）：<b>安静区里什么都不画</b>。
     * 手册把整个正文区（阅读栏、左栏、首页格子区）、底栏、顶栏的文字块和按钮都登记进去，微光粒实际只落在顶栏中间的空白里 ——
     * 读字的地方周围没有任何在动、在闪的东西。登记只是写几个 int，判定是每粒几次整数比较，都不分配。</p>
     */
    public static final class MruMotes {
        /** 闪光的呼吸周期（秒）：720 / 6 = 120，回绕处无接缝 */
        static final float TWINKLE_S = 6f;
        /** 安静区最多几块 */
        static final int MAX_QUIET = 8;

        private static final int MOTE = 0;
        private static final int SPARK = 1;

        private final int seed;
        private final int areaPerMote;
        private final int maxMote;
        private final int areaPerSpark;
        private final int maxSpark;
        private int w = -1;
        private int h = -1;
        private int cut;
        private int count;
        private int[] sx = new int[0];
        private int[] sy = new int[0];
        private int[] color = new int[0];
        private int[] alpha = new int[0];
        private int[] kind = new int[0];
        private float[] phase = new float[0];
        private int rng;
        /** 安静区：每块 {左, 上, 右, 下}（屏幕坐标，右、下不含） */
        private final int[] quiet = new int[MAX_QUIET * 4];
        private int quietCount;

        /**
         * @param seed         种子（不同的地方用不同的种子，免得同一屏里两块长得一样）
         * @param areaPerMote  每多少平方像素一粒柔光粒
         * @param maxMote      柔光粒上限
         * @param areaPerSpark 每多少平方像素一颗闪光
         * @param maxSpark     闪光上限
         */
        public MruMotes(int seed, int areaPerMote, int maxMote, int areaPerSpark, int maxSpark) {
            this.seed = seed;
            this.areaPerMote = Math.max(500, areaPerMote);
            this.maxMote = maxMote;
            this.areaPerSpark = Math.max(1000, areaPerSpark);
            this.maxSpark = maxSpark;
        }

        /** 清掉上一帧登记的安静区 */
        public void clearQuiet() {
            quietCount = 0;
        }

        /** 登记一块安静区（屏幕坐标，右、下不含）；满了就忽略 */
        public void quiet(int x0, int y0, int x1, int y1) {
            if (quietCount >= MAX_QUIET || x1 <= x0 || y1 <= y0) {
                return;
            }
            int o = quietCount * 4;
            quiet[o] = x0;
            quiet[o + 1] = y0;
            quiet[o + 2] = x1;
            quiet[o + 3] = y1;
            quietCount++;
        }

        /** 这一粒（连同它的光晕 / 闪光臂，半径 {@code reach}）碰没碰到安静区 */
        private boolean quietAt(int px, int py, int reach) {
            for (int i = 0; i < quietCount; i++) {
                int o = i * 4;
                if (px + reach >= quiet[o] && py + reach >= quiet[o + 1] && px - reach < quiet[o + 2] && py - reach < quiet[o + 3]) {
                    return true;
                }
            }
            return false;
        }

        private int next(int bound) {
            // xorshift：只在排版时跑，不分配 Random
            rng ^= rng << 13;
            rng ^= rng >>> 17;
            rng ^= rng << 5;
            return (rng & 0x7FFFFFFF) % Math.max(1, bound);
        }

        private boolean inside(int x, int y, int margin) {
            int dx = Math.min(x, w - 1 - x);
            int dy = Math.min(y, h - 1 - y);
            return dx >= margin && dy >= margin && dx + dy >= cut + margin + 1;
        }

        private void layout(int w, int h, int cut) {
            this.w = w;
            this.h = h;
            this.cut = cut;
            rng = seed * 0x9E3779B1 + 0x6A09E667;
            if (rng == 0) {
                rng = 1;
            }
            int area = Math.max(0, w * h);
            int motes = Math.min(maxMote, area / areaPerMote);
            int sparks = Math.min(maxSpark, area / areaPerSpark);
            int n = motes + sparks;
            sx = new int[n];
            sy = new int[n];
            color = new int[n];
            alpha = new int[n];
            kind = new int[n];
            phase = new float[n];
            count = 0;
            for (int i = 0; i < n; i++) {
                int k = i < motes ? MOTE : SPARK;
                // 闪光的十字臂 3 像素，离边（和切角）要多留几格，免得臂伸出面板
                int margin = k == SPARK ? 6 : 3;
                int px = 0;
                int py = 0;
                boolean ok = false;
                for (int attempt = 0; attempt < 8 && !ok; attempt++) {
                    px = next(w);
                    py = next(h);
                    ok = inside(px, py, margin);
                }
                if (!ok) {
                    continue;
                }
                sx[count] = px;
                sy[count] = py;
                kind[count] = k;
                color[count] = MOTE_COLORS[next(MOTE_COLORS.length)];
                alpha[count] = k == MOTE ? 0x40 + next(0x30) : 0x80 + next(0x30);
                phase[count] = next(6000) / 1000f;
                count++;
            }
        }

        /**
         * @param x    区域左
         * @param y    区域上
         * @param w    区域宽
         * @param h    区域高
         * @param cut  区域的切角（光粒不落在切掉的角上）
         * @param time {@link GuideTheme#time}
         * @param fade 整体不透明度 0~1
         */
        public void render(GuiGraphics g, int x, int y, int w, int h, int cut, float time, float fade) {
            if (w <= 8 || h <= 8 || fade <= 0.01f) {
                return;
            }
            if (w != this.w || h != this.h || cut != this.cut) {
                layout(w, h, cut);
            }
            for (int i = 0; i < count; i++) {
                int px = x + sx[i];
                int py = y + sy[i];
                boolean spark = kind[i] == SPARK;
                if (quietCount > 0 && quietAt(px, py, spark ? 3 : 1)) {
                    continue;
                }
                int a = (int) (alpha[i] * fade);
                if (spark) {
                    float tw = GuideTheme.breathe(time + phase[i], TWINKLE_S, 0.6f);
                    sparkle(g, px, py, 3, color[i], (int) (a * tw));
                    continue;
                }
                g.fill(px, py, px + 1, py + 1, withAlpha(color[i], a));
                int halo = withAlpha(color[i], a * 2 / 5);
                g.fill(px - 1, py, px, py + 1, halo);
                g.fill(px + 1, py, px + 2, py + 1, halo);
                g.fill(px, py - 1, px + 1, py, halo);
                g.fill(px, py + 1, px + 1, py + 2, halo);
            }
        }
    }

    // ==================== 框体 ====================

    /**
     * 直角 1px 描边
     *
     * <p>大框体一律用斜切，但格子这种小元件不能：18~22px 的尺度上切角会把四个角
     * 吃掉将近一半，凹槽会糊成一个八边形。小元件用直角、大框体用斜切，
     * 这个对比本身也是这套视觉语言的一部分。</p>
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param color 线色
     */
    public static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * 斜切板材：填充一个四角被斜切掉的矩形。
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param cut   切角边长
     * @param color 填充色
     */
    public static void chamferFill(GuiGraphics g, int x, int y, int w, int h, int cut, int color) {
        g.fill(x, y + cut, x + w, y + h - cut, color);
        for (int i = 0; i < cut; i++) {
            int inset = cut - i;
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /**
     * 斜切描边：沿斜切矩形的轮廓画 1px 线。
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param cut   切角边长
     * @param color 线色
     */
    public static void chamferOutline(GuiGraphics g, int x, int y, int w, int h, int cut, int color) {
        g.fill(x + cut, y, x + w - cut, y + 1, color);
        g.fill(x + cut, y + h - 1, x + w - cut, y + h, color);
        g.fill(x, y + cut, x + 1, y + h - cut, color);
        g.fill(x + w - 1, y + cut, x + w, y + h - cut, color);
        for (int i = 0; i < cut; i++) {
            int inset = cut - i;
            g.fill(x + inset - 1, y + i, x + inset, y + i + 1, color);
            g.fill(x + w - inset, y + i, x + w - inset + 1, y + i + 1, color);
            g.fill(x + inset - 1, y + h - i - 1, x + inset, y + h - i, color);
            g.fill(x + w - inset, y + h - i - 1, x + w - inset + 1, y + h - i, color);
        }
    }

    /**
     * 发光的斜切描边：轮廓外再叠两层递减 alpha。
     *
     * @param g        画布
     * @param x        左
     * @param y        上
     * @param w        宽
     * @param h        高
     * @param cut      切角边长
     * @param color    线色
     * @param strength 辉光强度 0~1
     */
    public static void chamferGlow(GuiGraphics g, int x, int y, int w, int h, int cut, int color, float strength) {
        for (int layer = 2; layer >= 1; layer--) {
            int a = (int) (40 * strength / layer);
            if (a <= 0) continue;
            chamferOutline(g, x - layer, y - layer, w + layer * 2, h + layer * 2,
                    cut + layer, withAlpha(color, a));
        }
        chamferOutline(g, x, y, w, h, cut, color);
    }

    /**
     * 斜切轮廓的周长：{@link #chamferOutline} 那一圈的像素数（四条直边 + 四条切角斜边）。
     *
     * @param w   宽
     * @param h   高
     * @param cut 切角边长
     * @return 像素数
     */
    public static int chamferPerimeter(int w, int h, int cut) {
        return 2 * w + 2 * h - 4 * cut;
    }

    /**
     * 斜切轮廓的一段弧：「描线」动画用
     *
     * <p>画的像素和 {@link #chamferOutline} 完全是同一圈，只是按弧长截取其中一段。
     * 弧长从<b>顶边中点</b>起算、顺时针增加（顶边右半 → 右上切角 → 右边 → 右下切角 → 底边 →
     * 左下切角 → 左边 → 左上切角 → 顶边左半），周长见 {@link #chamferPerimeter}。
     * {@code from} / {@code to} 可以是负数或超过周长，按周长取模；跨过起点时自动拆成两段。
     * 直边一段只要一次 fill，斜边逐像素（最多 cut 次）。</p>
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param cut   切角边长
     * @param from  起点弧长（像素，含）
     * @param to    终点弧长（像素，不含）
     * @param color 线色
     */
    public static void chamferArc(GuiGraphics g, int x, int y, int w, int h, int cut, float from, float to, int color) {
        if (w <= cut * 2 || h <= cut * 2 || to <= from) {
            return;
        }
        int per = chamferPerimeter(w, h, cut);
        int start = (int) Math.floor(from);
        int len = (int) Math.floor(to) - start;
        if (len >= per) {
            chamferOutline(g, x, y, w, h, cut, color);
            return;
        }
        if (len <= 0) {
            return;
        }
        int s = Math.floorMod(start, per);
        if (s + len <= per) {
            arcRange(g, x, y, w, h, cut, s, s + len, color);
        } else {
            arcRange(g, x, y, w, h, cut, s, per, color);
            arcRange(g, x, y, w, h, cut, 0, s + len - per, color);
        }
    }

    /** 画弧长落在 [lo, hi) 里的轮廓像素（0 ≤ lo < hi ≤ 周长） */
    private static void arcRange(GuiGraphics g, int x, int y, int w, int h, int cut, int lo, int hi, int color) {
        int cx = x + w / 2;
        int o = 0;
        // 顶边右半 → 右上切角 → 右边 → 右下切角
        o = arcRun(g, lo, hi, o, x + w - cut - cx, cx, y, 1, 0, color);
        o = arcRun(g, lo, hi, o, cut, x + w - cut, y, 1, 1, color);
        o = arcRun(g, lo, hi, o, h - cut * 2, x + w - 1, y + cut, 0, 1, color);
        o = arcRun(g, lo, hi, o, cut, x + w - 1, y + h - cut, -1, 1, color);
        // 底边（向左）→ 左下切角 → 左边（向上）→ 左上切角 → 顶边左半
        o = arcRun(g, lo, hi, o, w - cut * 2, x + w - cut - 1, y + h - 1, -1, 0, color);
        o = arcRun(g, lo, hi, o, cut, x + cut - 1, y + h - 1, -1, -1, color);
        o = arcRun(g, lo, hi, o, h - cut * 2, x, y + h - cut - 1, 0, -1, color);
        o = arcRun(g, lo, hi, o, cut, x, y + cut - 1, 1, -1, color);
        arcRun(g, lo, hi, o, cx - x - cut, x + cut, y, 1, 0, color);
    }

    /**
     * 轮廓上的一段（直边或 45° 斜边）：只画它落在 [lo, hi) 里的部分
     *
     * @return 下一段的起点弧长
     */
    private static int arcRun(GuiGraphics g, int lo, int hi, int start, int len, int x0, int y0, int dx, int dy, int color) {
        if (len <= 0) {
            return start;
        }
        int a = Math.max(lo, start) - start;
        int b = Math.min(hi, start + len) - start;
        if (b > a) {
            if (dx != 0 && dy != 0) {
                for (int k = a; k < b; k++) {
                    int px = x0 + dx * k;
                    int py = y0 + dy * k;
                    g.fill(px, py, px + 1, py + 1, color);
                }
            } else {
                int x1 = x0 + dx * a;
                int y1 = y0 + dy * a;
                int x2 = x0 + dx * (b - 1);
                int y2 = y0 + dy * (b - 1);
                g.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
            }
        }
        return start + len;
    }

    /**
     * 四角括号：只出现在转角的 L 形。
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param len   臂长
     * @param color 颜色
     */
    public static void brackets(GuiGraphics g, int x, int y, int w, int h, int len, int color) {
        if (len <= 0) return;
        int r = x + w, b = y + h;
        g.fill(x, y, x + len, y + 1, color);
        g.fill(x, y, x + 1, y + len, color);
        g.fill(r - len, y, r, y + 1, color);
        g.fill(r - 1, y, r, y + len, color);
        g.fill(x, b - 1, x + len, b, color);
        g.fill(x, b - len, x + 1, b, color);
        g.fill(r - len, b - 1, r, b, color);
        g.fill(r - 1, b - len, r, b, color);
    }

    /**
     * 矩阵节点（5×5 菱形）：照 {@code item/matrix/*.png} 晶格上的节点 —— 左上是亮面（MRU 紫罗兰 + 一点护板嵌线的淡紫高光），
     * 右下是暗品红背光面。分隔线中间、面板四角都有它，是静止的装饰，所以亮面不用焦点品红 {@link #FOCUS}
     *
     * @param cx    中心列
     * @param cy    中心行
     * @param alpha 0~1
     */
    public static void gem(GuiGraphics g, int cx, int cy, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        int lit = fade(MRU_VIOLET, alpha);
        int dark = fade(FOCUS_DEEP, alpha);
        g.fill(cx, cy - 2, cx + 1, cy - 1, lit);
        g.fill(cx - 1, cy - 1, cx + 1, cy, lit);
        g.fill(cx + 1, cy - 1, cx + 2, cy, dark);
        g.fill(cx - 2, cy, cx + 1, cy + 1, lit);
        g.fill(cx + 1, cy, cx + 3, cy + 1, dark);
        g.fill(cx - 1, cy + 1, cx + 2, cy + 2, dark);
        g.fill(cx, cy + 2, cx + 1, cy + 3, dark);
        g.fill(cx - 1, cy - 1, cx, cy, fade(INLAY, alpha));
    }

    /** 3×3 的小矩阵节点（分区标题前那一粒）：十字形，竖笔紫罗兰、横笔暗品红，中心一点淡紫高光 */
    public static void gemSmall(GuiGraphics g, int cx, int cy, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        g.fill(cx - 1, cy, cx + 2, cy + 1, fade(FOCUS_DEEP, alpha));
        g.fill(cx, cy - 1, cx + 1, cy + 2, fade(MRU_VIOLET, alpha));
        g.fill(cx, cy, cx + 1, cy + 1, fade(INLAY, alpha));
    }

    /**
     * MRU 光线：蓝紫 → 紫罗兰 → 品红的渐变，两端各 1/8 渐隐成透明
     *
     * @param alpha 0~255
     * @param shift 渐变相位（缓慢流动用）
     */
    public static void mruLine(GuiGraphics g, int x, int y, int w, int alpha, float shift) {
        if (w <= 8 || alpha <= 2) {
            return;
        }
        final int seg = 8;
        for (int i = 0; i < seg; i++) {
            int x0 = x + w * i / seg;
            int x1 = x + w * (i + 1) / seg;
            float a0 = i == 0 ? 0f : 1f;
            float a1 = i == seg - 1 ? 0f : 1f;
            int c0 = withAlpha(mru(shift + i / (float) seg), (int) (alpha * a0));
            int c1 = withAlpha(mru(shift + (i + 1) / (float) seg), (int) (alpha * a1));
            hGradient(g, x0, y, x1, y + 1, c0, c1);
        }
    }

    /**
     * 顶栏下沿的 MRU 光带：3 像素高、由上往下渐淡（上沿一道线、下面两层柔光），蓝紫 → 紫罗兰 → 品红 → 紫罗兰的渐变
     *
     * <p><b>静止的</b>：它就贴在阅读栏上沿，上一版的渐变 24 秒流一圈、上面还有一粒丁香白光点来回游走，读字时余光里一直有东西在动；
     * 现在不流动、没有光点，不透明度也降了（亮线 0xE0 → 0x90）。品红那一段落在左栏上方（相位 0.25），不压在正文正上方。
     * 界面「活着」的感觉交给偶尔在外框 / 顶栏上闪一下的电弧（{@code GuideArcs}）。</p>
     */
    public static void mruDataLine(GuiGraphics g, int x, int y, int w) {
        mruLine(g, x, y - 1, w, 0x18, 0.21f);
        mruLine(g, x, y, w, 0x90, 0.25f);
        mruLine(g, x, y + 1, w, 0x38, 0.31f);
        mruLine(g, x, y + 2, w, 0x14, 0.37f);
    }

    /**
     * 角标：格子左上角的斜切三角，用来表示「当前」或「已完成」。
     *
     * @param g     画布
     * @param x     格子左
     * @param y     格子上
     * @param size  直角边长
     * @param color 颜色
     */
    public static void cornerTab(GuiGraphics g, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            g.fill(x, y + i, x + size - i, y + i + 1, color);
        }
    }

    /**
     * 徽章：斜切小块底 + 文字。
     *
     * @param g    画布
     * @param font 字体
     * @param text 文本
     * @param x    左
     * @param y    上
     * @param bg   底色
     * @param fg   字色
     * @return 徽章宽度
     */
    public static int badge(GuiGraphics g, Font font, String text, int x, int y, int bg, int fg) {
        int w = font.width(text) + 12;
        int h = font.lineHeight + 5;
        chamferFill(g, x, y, w, h, 3, bg);
        chamferOutline(g, x, y, w, h, 3, withAlpha(fg, 0x70));
        // 带阴影：徽章里是细体数字，压在护板上不带阴影会糊掉
        g.drawString(font, text, x + 6, y + 3, fg, true);
        return w;
    }

    /**
     * 滚动条：轨道 + 发光滑块。常态薰衣草，压上去 / 拖动时转品红。
     *
     * @param g         画布
     * @param x         左
     * @param y         上
     * @param trackH    轨道高
     * @param scroll    当前滚动量
     * @param maxScroll 最大滚动量
     * @param active    鼠标压在滚动条上或正在拖拽 —— 抓取状态要有明确反馈
     */
    public static void scrollbar(GuiGraphics g, int x, int y, int trackH,
                                 double scroll, double maxScroll, boolean active) {
        if (maxScroll <= 0) return;
        g.fill(x, y, x + SCROLLBAR_W, y + trackH, withAlpha(EDGE, 0x90));
        double visibleRatio = trackH / (trackH + maxScroll);
        int thumbH = Math.max(THUMB_MIN_H, (int) (trackH * visibleRatio));
        int thumbY = y + (int) ((trackH - thumbH) * (scroll / maxScroll));
        // 激活时辉光加宽一圈并转品红，让「抓住了」看得出来
        int halo = active ? 2 : 1;
        g.fill(x - halo, thumbY, x + SCROLLBAR_W + halo, thumbY + thumbH,
                withAlpha(active ? FOCUS : GLOW, active ? 0x50 : 0x30));
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, active ? FOCUS : GLOW);
    }
}
