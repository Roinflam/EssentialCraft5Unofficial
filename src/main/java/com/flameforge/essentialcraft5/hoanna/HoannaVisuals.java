package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * 海纳尔的天空与画面用到的全部数值和纯函数（第六批 6a M3「天空与画面」，计划 tools/plan/batch6a.md §3、§5.3）。
 * <b>common 类：不 import 任何客户端类</b>，GameTest 在专用服上直接测它（{@code gametest/HoannaVisualTests}）；客户端的
 * {@code client/hoanna/*}（天空、雾、光照图、晃动、雾团、晕影、提示条）只从这里取数。
 *
 * <h2>护眼原则（目标是看着不难受、人性化、符合源质风格，§3.1；GameTest 守着带 ★ 的）</h2>
 * <ol>
 *   <li>★ <b>大面积低饱和</b>：天、雾、草、叶、水的 CIELAB 彩度 ≤ 26（颜色在群系 JSON 里，由 {@code tools/gen/hoanna_biomes.py} 生成；改色只改那张表）。
 *       招牌色只放在小面积上：残日、伴月（{@link #BODIES}）、MRU 光幕、远空电弧、余烬、传送门。</li>
 *   <li><b>不整屏铺色、没有常驻动画</b>：常驻的屏幕叠层（辐射晕影、毒雾近处）只画边缘、中间不碰、最浓 ≤ 0.20（★ {@link #vignetteAlpha}）；
 *       天空除了随时间转，自己不做动画（光幕、星星都是静态的）。</li>
 *   <li>★ <b>闪烁安全</b>：唯一的亮度突变是远空电弧，全局至少隔 {@link ArcLimiter#MIN_INTERVAL} tick 一道（≤ 0.1 次 / 秒，远低于 3 次 / 秒）；
 *       打开原版「隐藏天空闪烁效果」时电弧峰值只剩约 1/3（{@link #ARC_PEAK_REDUCED}）、间隔翻倍。事件状态切换都是 200 tick 平滑淡入淡出（地震 60 / 100，{@link HoannaFade}）。</li>
 *   <li>★ <b>运动舒适</b>：所有天体和原版太阳同一个角速度（{@link Body#speed()} 恒为 1），只差固定相位和纬度。镜头晃动只改画面、不改真实朝向和准星：
 *       |roll| ≤ {@link #MAX_ROLL}°、|pitch| ≤ {@link #MAX_PITCH}°、主频 ≤ 2.1 Hz，乘原版「屏幕扭曲效果」× 客户端配置 {@code camera_shake}，任一为 0 就不晃；
 *       创造 / 旁观不晃（{@link #shake}）。不画云（{@link #CLOUD_HEIGHT}）。</li>
 *   <li><b>地平线无缝</b>：天穹下沿被天空雾淡成雾色（同一帧、同一个来源），天穹和雾色走同一个染色函数 {@link #tint}；雾的 L* 高于天顶 L*（群系表自检）。
 *       另有一圈不靠雾距离的地平线带（★ {@link #horizonAlpha}）：别的模组把天空雾推远（远景、关天空雾）也不会在地平线挤出一道硬线，整个下半球都是雾色。</li>
 *   <li>★ <b>看得见</b>：黑暗事件天光 ×{@link #DARK_SKY_LIGHT}、方块光 ×{@link #DARK_BLOCK_LIGHT}（{@link #lightmapFactor}；火把照亮，夜视和亮度滑块照常有效）；
 *       夜空亮度下限 {@link #NIGHT_SKY_FLOOR}；毒雾离镜头 3.5 格内淡出（{@link #fumeHazeAlpha}），镜头永远不会埋进整屏雾团；
 *       失明 / 黑暗效果遮住视野时雾团跟着淡没（{@link #fumeHazeVisibility}），不会在一片黑里发亮。</li>
 *   <li><b>源质风格</b>：天顶暗石板蓝紫、地平线暖尘色（群系表）；残日 / 伴月带 MRU 的淡紫和淡品红；光幕用研究手册的 MRU 渐变
 *       （{@code CodexTheme.MRU_BLUE / MRU_VIOLET / MRU_MAGENTA} 各和 #8C80B0 混 40%，{@link #AURORA_COLORS}）；远空电弧用线圈待机电弧的颜色
 *       （{@code ArcEffect.Style.COIL_IDLE} 与 #B0A0D0 混 30%，{@link #ARC_COLOR}）；毒雾是全维度唯一的病态黄绿；辐射用 EC 自己的紫（原作效果色 0x660066 一系），不用核绿。</li>
 * </ol>
 * <p>
 * 性能预算（§3.3）：天空每帧 ≤ 8 次绘制、CPU ≤ 0.05 ms，天空代码自己稳态每帧不分配（静态 VBO、复用矩阵）；毒雾雾团 GPU ≤ 0.3 ms。
 * <p>
 * 第十三批 13d（batch13.md §6.2，HS §2.2）加的几项，同样守上面的护眼原则：
 * <ul>
 *   <li><b>魔力潮汐</b>：光幕「星空那一份」× (0.6 + 0.8T)（{@link #auroraTideFactor}；平时 0.10 → 满月 0.14、新月 0.06），不加动画。</li>
 *   <li><b>地脉风暴</b>：天和雾往暗紫 {@link #STORM_TINT}（彩度约 23）混，最多 {@value #STORM_TINT_MAX}；淡入淡出照默认 200 刻（{@link HoannaFade}）；
 *       光幕压低（视角里竖直方向 × {@value #STORM_AURORA_LOWER}）、加宽（横向 × {@value #STORM_AURORA_WIDEN}）、不透明度 × {@value #STORM_AURORA_ALPHA}，
 *       整体仍 ≤ {@value #AURORA_MAX_ALPHA}；玩家周围缓降的淡紫光点每刻 ≤ {@value #STORM_MOTES_PER_TICK} 颗、{@value #STORM_MOTE_RADIUS} 格内
 *       （24 格外不画），同时 ≤ {@value #MAX_STORM_MOTES} 颗。<b>没有闪光</b>：远空电弧照旧走 {@link ArcLimiter}。</li>
 *   <li><b>辐射热点</b>：所在区块底数 ≥ {@value #HOTSPOT_MIN_BASE}（{@link #hotspotVisible}）时每 {@value #HOTSPOT_INTERVAL} 刻在 {@value #HOTSPOT_RADIUS} 格内
 *       放 1 颗灰绿细尘，客户端自己算 {@code Radiation.chunkBase}，不发包。</li>
 * </ul>
 */
public final class HoannaVisuals {

    private HoannaVisuals() {
    }

    // ==================================================================
    // 维度效果（HoannaEffects）
    // ==================================================================

    /** 云高：NaN = 不画云（D5 A；原版 LevelRenderer 和常见的渲染优化模组都直接跳过）。别自己画云：会绕开渲染优化、远景模组的云雾 */
    public static final float CLOUD_HEIGHT = Float.NaN;

    /** 雾色随亮度：R、G 通道 = c × (b × 0.88 + 0.12)，B = c × (b × 0.84 + 0.16)（主世界 0.94 / 0.06、0.91 / 0.09；夜里的下限约翻倍、略偏蓝紫） */
    public static final float FOG_SCALE_RG = 0.88F;
    public static final float FOG_BASE_RG = 0.12F;
    public static final float FOG_SCALE_B = 0.84F;
    public static final float FOG_BASE_B = 0.16F;

    /** 雾色随亮度的系数：通道 0 / 1 是 R、G，2 是 B（{@code getBrightnessDependentFogColor} 用） */
    public static float fogBrightnessFactor(int channel, float brightness) {
        return channel == 2 ? brightness * FOG_SCALE_B + FOG_BASE_B : brightness * FOG_SCALE_RG + FOG_BASE_RG;
    }

    /** 日出扇的不透明度是原版的多少（雾在日出方向的混色也用它，转头时雾色摆动随之变小） */
    public static final float SUNRISE_ALPHA_SCALE = 0.55F;

    /**
     * 日出 / 日落扇的颜色（照原版 {@code DimensionSpecialEffects.getSunriseColor} 的时间窗和强度曲线，只换颜色、压低不透明度）：
     * rgb = (0.62 + 0.15·f3, 0.36 + 0.25·f3², 0.50 − 0.08·f3)，从 #9E5C80 灰紫经 #B16B75 玫瑰到 #C49C6B 暖沙；alpha = 原版 × {@link #SUNRISE_ALPHA_SCALE}。
     *
     * @param out 至少 4 个 float，写 r、g、b、a
     * @return {@code out}；不在日出日落时间窗里返回 null（同原版）
     */
    public static float[] sunrise(float timeOfDay, float[] out) {
        float f1 = Mth.cos(timeOfDay * ((float) Math.PI * 2F));
        if (f1 < -0.4F || f1 > 0.4F) {
            return null;
        }
        float f3 = f1 / 0.4F * 0.5F + 0.5F;
        float f4 = 1F - (1F - Mth.sin(f3 * (float) Math.PI)) * 0.99F;
        f4 *= f4;
        out[0] = 0.62F + 0.15F * f3;
        out[1] = 0.36F + 0.25F * f3 * f3;
        out[2] = 0.50F - 0.08F * f3;
        out[3] = f4 * SUNRISE_ALPHA_SCALE;
        return out;
    }

    /** 白天系数 0~1（原版天空亮度同一条曲线：clamp(cos(2π·t)·2 + 0.5)），正午 1、午夜 0 */
    public static float daylight(float timeOfDay) {
        return Mth.clamp(Mth.cos(timeOfDay * ((float) Math.PI * 2F)) * 2F + 0.5F, 0F, 1F);
    }

    /** 夜晚系数 = 1 − 白天系数；日落（t = 0.25）正好 0.5 */
    public static float night(float timeOfDay) {
        return 1F - daylight(timeOfDay);
    }

    // ==================================================================
    // 染色（天穹与雾色共用，§3.4）
    // ==================================================================

    /** 日阵：天和雾往暖沙色混，最多 0.18 × 白天系数 */
    public static final int SUN_ARRAY_TINT = 0xB08A64;
    public static final float SUN_ARRAY_TINT_MAX = 0.18F;
    /** 地震：往尘土色混，最多 0.10 */
    public static final int QUAKE_TINT = 0x7A6A58;
    public static final float QUAKE_TINT_MAX = 0.10F;
    /** 毒雾：往病态黄绿混，最多 0.40 × 附近强度 p */
    public static final int FUME_TINT = 0x6E7A4E;
    public static final float FUME_TINT_MAX = 0.40F;
    /** 黑暗：天和雾乘到 0.25 */
    public static final float DARKNESS_SKY = 0.25F;
    /** 夜空亮度下限（天穹逐分量取大；GameTest 守着它不被染色压到更暗） */
    public static final int NIGHT_SKY_FLOOR = 0x0E1018;
    /** 地脉风暴：天和雾往暗紫混，最多 0.35 × 风暴淡入值（颜色彩度约 23，守「大面积低饱和」） */
    public static final int STORM_TINT = 0x40385A;
    public static final float STORM_TINT_MAX = 0.35F;

    /**
     * 天穹和雾色的事件染色（只改颜色、不改雾距离）。顺序：日阵 → 地震 → 毒雾 → 黑暗（最后乘，黑暗 f = 1 时输出正好是输入 × 0.25）。
     * 输入输出都是 0~1 的线性 sRGB 分量（和原版雾色同一个空间），输出夹在 [0, 1]。
     *
     * @param darkness 黑暗淡入值 0~1
     * @param sunArray 日阵淡入值 0~1
     * @param daylight 白天系数 {@link #daylight}
     * @param quake    地震淡入值 0~1
     * @param fume     毒雾附近强度 p（0~1）
     * @param out      至少 3 个 float
     */
    public static void tint(float r, float g, float b, float darkness, float sunArray, float daylight, float quake, float fume, float[] out) {
        tint(r, g, b, darkness, sunArray, daylight, quake, fume, 0F, out);
    }

    /**
     * 同上，另加地脉风暴的暗紫（在毒雾之后、黑暗之前混；风暴和别的天灾不会同时开着，淡出重叠时照这个顺序叠）。
     *
     * @param storm 风暴淡入值 0~1
     */
    public static void tint(float r, float g, float b, float darkness, float sunArray, float daylight, float quake, float fume, float storm,
                            float[] out) {
        float cr = r;
        float cg = g;
        float cb = b;
        float k = SUN_ARRAY_TINT_MAX * clamp01(sunArray) * clamp01(daylight);
        if (k > 0F) {
            cr = Mth.lerp(k, cr, red(SUN_ARRAY_TINT));
            cg = Mth.lerp(k, cg, green(SUN_ARRAY_TINT));
            cb = Mth.lerp(k, cb, blue(SUN_ARRAY_TINT));
        }
        k = QUAKE_TINT_MAX * clamp01(quake);
        if (k > 0F) {
            cr = Mth.lerp(k, cr, red(QUAKE_TINT));
            cg = Mth.lerp(k, cg, green(QUAKE_TINT));
            cb = Mth.lerp(k, cb, blue(QUAKE_TINT));
        }
        k = FUME_TINT_MAX * clamp01(fume);
        if (k > 0F) {
            cr = Mth.lerp(k, cr, red(FUME_TINT));
            cg = Mth.lerp(k, cg, green(FUME_TINT));
            cb = Mth.lerp(k, cb, blue(FUME_TINT));
        }
        k = STORM_TINT_MAX * clamp01(storm);
        if (k > 0F) {
            cr = Mth.lerp(k, cr, red(STORM_TINT));
            cg = Mth.lerp(k, cg, green(STORM_TINT));
            cb = Mth.lerp(k, cb, blue(STORM_TINT));
        }
        float dark = Mth.lerp(clamp01(darkness), 1F, DARKNESS_SKY);
        out[0] = clamp01(cr * dark);
        out[1] = clamp01(cg * dark);
        out[2] = clamp01(cb * dark);
    }

    /** 天穹颜色和夜空下限逐分量取大（只给天穹用，雾不取） */
    public static void applyNightFloor(float[] rgb) {
        rgb[0] = Math.max(rgb[0], red(NIGHT_SKY_FLOOR));
        rgb[1] = Math.max(rgb[1], green(NIGHT_SKY_FLOOR));
        rgb[2] = Math.max(rgb[2], blue(NIGHT_SKY_FLOOR));
    }

    // ==================================================================
    // 天穹与地平线带（6a 审查 M3-1 / M3-2）
    // ==================================================================

    /** 天穹圆盘（照原版 {@code buildSkyDisc(16)}）：高 16、半径 512，外沿仰角 atan(16/512) ≈ 1.79° */
    public static final float SKY_DISC_HEIGHT = 16F;
    public static final float SKY_DISC_RADIUS = 512F;

    /**
     * 地平线带的环（仰角，度，从下往上）。带子是半径 {@link #HORIZON_RADIUS} 的一圈球面，每一环一个顶点 alpha（{@link #horizonAlpha}），
     * 环和环之间 GPU 线性插值（{@link #horizonBandAlpha} 就是插值后的值）。
     * <ul>
     *   <li>最低一环 −90°：整个下半球都画到（M3-2：天穹和原来的裙边之间、视距小时被远平面裁掉的那一圈都没写像素，开光影时光影包会在那里画它自己的天）。
     *       原来的 y = −16 裙边去掉了，它的活由这一段干（alpha 1、雾色，和以前一样）。</li>
     *   <li>0° 以上是地平线渐变（M3-1），最高一环 alpha 为 0。</li>
     * </ul>
     */
    public static final float[] HORIZON_RINGS = {-90F, -10F, 0F, 4F, 5F, 6F, 7F, 8.5F, 10F, 12F, 14.5F, 17.5F, 21F, 25F, 30F};
    /** 带子半径：必须 ≤ 100（远平面 = 视距 × 64 格，视距 2 时只有 128；GameRenderer.getDepthFar） */
    public static final float HORIZON_RADIUS = 80F;
    /** 每一环多少段方位角 */
    public static final int HORIZON_SEGMENTS = 32;
    /**
     * 地平线带的参考雾距离：带子的渐变 = 原版视距 12（天空雾终点 192 格）时天穹下沿的那条雾量曲线。
     * 天穹本身照旧走原版天空雾（FOG_SKY）；带子不带雾、按仰角给 alpha，所以别的模组把天空雾终点推远（远景模组设成远景视距约 640 格、
     * 视距 32 原版就是 512、关天空雾的模组）时，地平线也至少有这么厚的一层尘霾，不会在 1.8°~5° 里挤成一道硬线。
     * 原版视距 ≤ 12 时天空雾本来就比它厚，叠上去雾霭会略高一点（10° 处雾量约 0.59 → 0.76，见 GameTest horizonBandIndependentOfFog 的模型）。
     */
    public static final float HORIZON_REF_DISTANCE = 192F;

    /**
     * 地平线带在仰角 {@code elevationDeg} 处的目标不透明度：≤ 0° 为 1；往上 = 原版天空雾在参考距离下的雾量
     * smoothstep(0, 192, 16 / tan e)（约 4.8° 前都是 1、10° 约 0.46、20° 约 0.13），再线性拉到最高一环正好为 0（拉完 10° 约 0.43、20° 约 0.08）。单调不增。
     */
    public static float horizonAlpha(float elevationDeg) {
        if (elevationDeg <= 0F) {
            return 1F;
        }
        float top = HORIZON_RINGS[HORIZON_RINGS.length - 1];
        if (elevationDeg >= top) {
            return 0F;
        }
        float atTop = horizonRaw(top);
        return clamp01((horizonRaw(elevationDeg) - atTop) / (1F - atTop));
    }

    private static float horizonRaw(float elevationDeg) {
        float distance = SKY_DISC_HEIGHT / (float) Math.tan(elevationDeg * Mth.DEG_TO_RAD);
        return smoothstep(0F, HORIZON_REF_DISTANCE, distance);
    }

    /** 画出来的地平线带在仰角 {@code elevationDeg} 处的不透明度（各环的 {@link #horizonAlpha} 之间线性插值；最低环以下 1、最高环以上 0） */
    public static float horizonBandAlpha(float elevationDeg) {
        float[] rings = HORIZON_RINGS;
        if (elevationDeg <= rings[0]) {
            return horizonAlpha(rings[0]);
        }
        for (int i = 0; i + 1 < rings.length; i++) {
            if (elevationDeg <= rings[i + 1]) {
                float t = (elevationDeg - rings[i]) / (rings[i + 1] - rings[i]);
                return Mth.lerp(t, horizonAlpha(rings[i]), horizonAlpha(rings[i + 1]));
            }
        }
        return 0F;
    }

    // ==================================================================
    // 黑暗事件的光照图（D6 A）
    // ==================================================================

    /** 黑暗 f = 1、没有方块光时整格的亮度倍数（天光） */
    public static final float DARK_SKY_LIGHT = 0.22F;
    /** 黑暗 f = 1、方块光 15（火把旁边）时的亮度倍数 */
    public static final float DARK_BLOCK_LIGHT = 0.88F;

    /**
     * 光照图一格的亮度倍数：lerp(f, 1, lerp(方块光 / 15, 0.22, 0.88))。在 Forge 的 {@code adjustLightmapColors} 里乘，
     * 那一步在夜视和伽马之前（{@code LightTexture} L138 / L140 / L157），所以夜视药水、亮度滑块照常有效。
     *
     * @param darkness   黑暗淡入值 0~1
     * @param blockLight 光照图的横坐标 pixelX（方块光 0~15）
     */
    public static float lightmapFactor(float darkness, int blockLight) {
        float lit = Mth.lerp(Mth.clamp(blockLight, 0, 15) / 15F, DARK_SKY_LIGHT, DARK_BLOCK_LIGHT);
        return Mth.lerp(clamp01(darkness), 1F, lit);
    }

    // ==================================================================
    // 天体（D4 A；§3.3 第 4 条）
    // ==================================================================

    /** 天体种类（决定贴图：原版 sun.png / moon_phases.png） */
    public enum Kind {
        SUN, MOON
    }

    /**
     * 一个天体。位置 = rotX(φ)·rotZ(θ)·(0, 100, 0)，全部挂在原版太阳的同一个转动 {@code YP(−90°)·XP(timeOfDay × 360°)} 上：
     * φ 是沿太阳轨道的固定相位差、θ 是离开轨道的纬度，和太阳同速（{@link #speed} 恒为 1）。
     *
     * @param halfSize    半边长（原版太阳 30、月亮 20）
     * @param rgb         顶点颜色（亮度 {@code brightness} 乘进 RGB；加色混合下 RGB 就是强度）
     * @param phaseOffset 月亮：相位 = (当前月相 + offset) % 8
     * @param extra       客户端配置 {@code sky_extras = false} 时不画
     */
    public record Body(String id, Kind kind, float phi, float theta, float halfSize, int rgb, float brightness, float speed,
                       int phaseOffset, boolean extra) {

        /** 顶点颜色的一个通道（0 R、1 G、2 B），已乘亮度 */
        public float channel(int c) {
            int v = c == 0 ? rgb >> 16 & 255 : c == 1 ? rgb >> 8 & 255 : rgb & 255;
            return v / 255F * brightness;
        }
    }

    /**
     * 顶点 alpha 恒为 1：1.20.1 的 {@code position_tex_color} 着色器在「贴图 alpha × 顶点 alpha < 0.1」时丢弃片元，
     * 亮度差放在 RGB 里，淡入淡出全走 {@code setShaderColor} 的 alpha
     */
    public static final float BODY_VERTEX_ALPHA = 1F;

    /** 平时的天体：一主日 + 两残日、主月 + 伴月（§3.3 表） */
    public static final List<Body> BODIES = List.of(
            new Body("main_sun", Kind.SUN, 0F, 0F, 24F, 0xFFE9C8, 1F, 1F, 0, false),
            new Body("remnant_sun_a", Kind.SUN, 38F, 12F, 11F, 0xC8B0FF, 0.75F, 1F, 0, true),
            new Body("remnant_sun_b", Kind.SUN, -27F, -9F, 8F, 0xF0C0E8, 0.6F, 1F, 0, true),
            new Body("main_moon", Kind.MOON, 180F, 0F, 18F, 0xD8D4E8, 1F, 1F, 0, false),
            new Body("companion_moon", Kind.MOON, 205F, 10F, 9F, 0xB8A8D8, 0.8F, 1F, 4, true));

    /** 日阵另加的 6 个残日（整组 alpha = 日阵淡入值；颜色在三种淡色里轮换、亮度 × 0.5~0.7） */
    public static final List<Body> SUN_ARRAY_BODIES = List.of(
            new Body("array_sun_1", Kind.SUN, 52F, 8F, 12F, 0xFFE9C8, 0.70F, 1F, 0, false),
            new Body("array_sun_2", Kind.SUN, -52F, -14F, 10F, 0xC8B0FF, 0.66F, 1F, 0, false),
            new Body("array_sun_3", Kind.SUN, 71F, 5F, 9F, 0xF0C0E8, 0.62F, 1F, 0, false),
            new Body("array_sun_4", Kind.SUN, -71F, -6F, 8F, 0xFFE9C8, 0.58F, 1F, 0, false),
            new Body("array_sun_5", Kind.SUN, 95F, 16F, 7F, 0xC8B0FF, 0.54F, 1F, 0, false),
            new Body("array_sun_6", Kind.SUN, -95F, -11F, 6F, 0xF0C0E8, 0.50F, 1F, 0, false));

    /** 天体离镜头的距离（原版 100） */
    public static final float BODY_DISTANCE = 100F;

    /** 日月整体不透明度：(1 − 雨) × (1 − 黑暗淡入值) */
    public static float celestialAlpha(float rain, float darkness) {
        return clamp01(1F - rain) * clamp01(1F - darkness);
    }

    // ==================================================================
    // 星星、MRU 光幕（sky_extras）
    // ==================================================================

    /** 星星：自建 900 颗（照原版 drawStars 的生成法、固定种子），大小 0.10~0.20，半径 100，淡紫白，不闪烁（原作没有星星） */
    public static final int STAR_COUNT = 900;
    public static final long STAR_SEED = 20260929L;
    public static final float STAR_MIN_SIZE = 0.10F;
    public static final float STAR_MAX_SIZE = 0.20F;
    public static final float STAR_RADIUS = 100F;
    public static final int STAR_COLOR = 0xD8D0F0;
    /** 黑暗事件时星星另加的亮度 */
    public static final float STAR_DARKNESS_BONUS = 0.35F;

    /**
     * 星星的 {@code setShaderColor} 系数（rgb 和 alpha 都乘它，加色混合下实际强度约是它的平方，同原版）：
     * 原版星亮度（最高 0.5）× (1 − 雨) + 0.35 × 黑暗淡入值，夹到 1
     */
    public static float starAlpha(float starBrightness, float rain, float darkness) {
        return clamp01(starBrightness * clamp01(1F - rain) + STAR_DARKNESS_BONUS * clamp01(darkness));
    }

    /** 光幕：绕 Z 轴倾斜 35° 的大圆上取 220° 一段、宽 ±9°，96 段 */
    public static final float AURORA_TILT = 35F;
    public static final float AURORA_ARC = 220F;
    public static final float AURORA_HALF_WIDTH = 9F;
    public static final int AURORA_SEGMENTS = 96;
    public static final float AURORA_RADIUS = 100F;
    /** 光幕横向三排顶点的颜色：下沿 MRU 蓝、中线 MRU 紫罗兰、上沿 MRU 品红，各和 #8C80B0 混 40%（{@code CodexTheme.MRU_*}） */
    public static final int[] AURORA_COLORS = {mix(0x6A40FF, 0x8C80B0, 0.4F), mix(0xA050FF, 0x8C80B0, 0.4F), mix(0xF050F0, 0x8C80B0, 0.4F)};
    public static final float AURORA_STAR_ALPHA = 0.10F;
    public static final float AURORA_DARKNESS_ALPHA = 0.08F;

    /** 光幕整体不透明度：0.10 × min(1, 星亮度 × 2 × (1 − 雨)) + 0.08 × 黑暗淡入值（平潮、没有风暴时的写法，同 {@link #auroraAlpha(float, float, float, float, float)} 传 T = 0.5、风暴 0） */
    public static float auroraAlpha(float starBrightness, float rain, float darkness) {
        return AURORA_STAR_ALPHA * Math.min(1F, starBrightness * 2F * clamp01(1F - rain)) + AURORA_DARKNESS_ALPHA * clamp01(darkness);
    }

    /** 潮汐对光幕的系数 0.6 + 0.8T（T 夹在 0~1：新月 0.6、平潮 1.0、满月 1.4） */
    public static final float AURORA_TIDE_BASE = 0.6F;
    public static final float AURORA_TIDE_SCALE = 0.8F;
    /** 风暴中光幕不透明度的倍数、整体上限 */
    public static final float STORM_AURORA_ALPHA = 1.5F;
    public static final float AURORA_MAX_ALPHA = 0.20F;
    /** 风暴中光幕在视角里竖直方向缩到多少（压低）、横向（光幕宽度方向）放到多少（加宽） */
    public static final float STORM_AURORA_LOWER = 0.75F;
    public static final float STORM_AURORA_WIDEN = 1.6F;

    public static float auroraTideFactor(float tide) {
        return AURORA_TIDE_BASE + AURORA_TIDE_SCALE * clamp01(tide);
    }

    /**
     * 光幕整体不透明度（第十三批 13d）：星空那一份 0.10 × 夜色 × (0.6 + 0.8T)，夜色 = min(1, 星亮度 × 2 × (1 − 雨))，风暴中夜色至少取风暴淡入值 × (1 − 雨)
     * （风暴从黄昏起，天还没黑透光幕就出来）；再加黑暗那一份 0.08 × 黑暗淡入值；整体 × lerp(1, 1.5, 风暴淡入值)，夹到 {@value #AURORA_MAX_ALPHA}。
     * T = 0.5、风暴 0 时和三个参数的版本完全一样。
     */
    public static float auroraAlpha(float starBrightness, float rain, float darkness, float tide, float storm) {
        float s = clamp01(storm);
        float dry = clamp01(1F - rain);
        float night = Math.max(Math.min(1F, starBrightness * 2F * dry), s * dry);
        float base = AURORA_STAR_ALPHA * night * auroraTideFactor(tide) + AURORA_DARKNESS_ALPHA * clamp01(darkness);
        return Math.min(AURORA_MAX_ALPHA, base * Mth.lerp(s, 1F, STORM_AURORA_ALPHA));
    }

    /** 风暴中光幕在视角里的竖直缩放（1 → {@value #STORM_AURORA_LOWER}） */
    public static float stormAuroraLower(float storm) {
        return Mth.lerp(clamp01(storm), 1F, STORM_AURORA_LOWER);
    }

    /** 风暴中光幕的横向放大（1 → {@value #STORM_AURORA_WIDEN}） */
    public static float stormAuroraWiden(float storm) {
        return Mth.lerp(clamp01(storm), 1F, STORM_AURORA_WIDEN);
    }

    /** 光幕沿长度的淡出（两端 sin，s = 0~1） */
    public static float auroraEnvelope(float s) {
        return Mth.sin(clamp01(s) * (float) Math.PI);
    }

    // ==================================================================
    // 远空电弧（sky_extras，D4 A；§3.3 第 7 条）
    // ==================================================================

    /** 电弧寿命（tick）：2 tick 升到峰值、停 1 tick、9 tick 线性淡出 */
    public static final int ARC_LIFETIME = 12;
    public static final int ARC_RISE = 2;
    public static final int ARC_HOLD = 1;
    public static final float ARC_PEAK = 0.35F;
    /** 打开「隐藏天空闪烁效果」时的峰值（约 1/3） */
    public static final float ARC_PEAK_REDUCED = 0.12F;
    /** 电弧颜色：线圈待机电弧 COIL_IDLE (1, 0.2, 1) 与 #B0A0D0 混 30% */
    public static final int ARC_COLOR = mix(0xFF33FF, 0xB0A0D0, 0.3F);
    /** 电弧所在的天球半径、高度角、跨度（度） */
    public static final float ARC_RADIUS = 90F;
    public static final float ARC_MIN_ELEVATION = 4F;
    public static final float ARC_MAX_ELEVATION = 18F;
    public static final float ARC_MIN_SPAN = 6F;
    public static final float ARC_MAX_SPAN = 14F;

    /** 电弧在出现后 {@code age}（tick + partialTick）时的不透明度倍数 */
    public static float arcAlpha(float age, boolean reduced) {
        if (age < 0F || age >= ARC_LIFETIME) {
            return 0F;
        }
        float peak = reduced ? ARC_PEAK_REDUCED : ARC_PEAK;
        if (age < ARC_RISE) {
            return peak * age / ARC_RISE;
        }
        if (age < ARC_RISE + ARC_HOLD) {
            return peak;
        }
        return peak * (ARC_LIFETIME - age) / (ARC_LIFETIME - ARC_RISE - ARC_HOLD);
    }

    /** 这时候能不能出电弧：夜晚系数 > 0.5，或黑暗事件淡入值 > 0.5 */
    public static boolean arcAllowed(float night, float darkness) {
        return night > 0.5F || darkness > 0.5F;
    }

    /**
     * 远空电弧的全局限速（一个客户端一个实例）：两道之间隔 {@link #MIN_INTERVAL}~{@link #MAX_INTERVAL} tick（随机），
     * 打开「隐藏天空闪烁效果」时翻倍、而且离上一道不到 2 × {@link #MIN_INTERVAL} 一律不出（中途打开开关也守得住）；
     * 白天且不在黑暗事件里不出。纯数据，GameTest 用 1 万个种子各跑 2 小时游戏时间测。
     */
    public static final class ArcLimiter {

        public static final int MIN_INTERVAL = 240;
        public static final int MAX_INTERVAL = 400;
        public static final int REDUCED_FACTOR = 2;

        private long nextAt = Long.MIN_VALUE / 4;
        private long lastFire = Long.MIN_VALUE / 4;

        /**
         * 在 {@code tick}（游戏刻）这一刻要不要出一道电弧；返回 true 时已经排好下一道的最早时刻。
         *
         * @param night    夜晚系数 {@link HoannaVisuals#night}
         * @param darkness 黑暗淡入值
         * @param reduced  原版「隐藏天空闪烁效果」
         */
        public boolean tryFire(long tick, float night, float darkness, boolean reduced, RandomSource random) {
            if (tick < nextAt || !arcAllowed(night, darkness)) {
                return false;
            }
            if (reduced && tick - lastFire < (long) MIN_INTERVAL * REDUCED_FACTOR) {
                return false;
            }
            lastFire = tick;
            nextAt = tick + interval(random, reduced);
            return true;
        }

        /** 下一道最早在哪一刻（测试跳着走用） */
        public long readyAt() {
            return nextAt;
        }

        /** 离开海纳尔、断线时清掉（下次进来从头算） */
        public void reset() {
            nextAt = Long.MIN_VALUE / 4;
            lastFire = Long.MIN_VALUE / 4;
        }

        /** 两道之间的间隔：240~400 tick，隐藏闪烁时 × 2 */
        public static int interval(RandomSource random, boolean reduced) {
            int base = MIN_INTERVAL + random.nextInt(MAX_INTERVAL - MIN_INTERVAL + 1);
            return reduced ? base * REDUCED_FACTOR : base;
        }
    }

    // ==================================================================
    // 地震镜头晃动（D7 A；§3.4）
    // ==================================================================

    /** 总量上限（度） */
    public static final float MAX_ROLL = 1.5F;
    public static final float MAX_PITCH = 1.0F;
    /** 持续晃动：roll = 0.45°·sin(2π·0.9t + φ₁) + 0.25°·sin(2π·1.7t + φ₂)，pitch = 0.25°·sin(2π·1.3t + φ₃) + 0.12°·sin(2π·2.1t + φ₄) */
    static final float ROLL_A1 = 0.45F;
    static final float ROLL_F1 = 0.9F;
    static final float ROLL_A2 = 0.25F;
    static final float ROLL_F2 = 1.7F;
    static final float PITCH_A1 = 0.25F;
    static final float PITCH_F1 = 1.3F;
    static final float PITCH_A2 = 0.12F;
    static final float PITCH_F2 = 2.1F;
    private static final double PHI_1 = 0.0D;
    private static final double PHI_2 = 1.9D;
    private static final double PHI_3 = 0.7D;
    private static final double PHI_4 = 2.6D;
    /** 强震（服务端发 S2CHoannaJoltPacket）：2.0 Hz、roll +1.0°、pitch +0.6°、衰减 τ = 0.6 秒、持续 1.5 秒（正好 3 个周期，头尾都是 0，不跳） */
    public static final float JOLT_ROLL = 1.0F;
    public static final float JOLT_PITCH = 0.6F;
    public static final float JOLT_HZ = 2.0F;
    public static final float JOLT_TAU = 0.6F;
    public static final float JOLT_SECONDS = 1.5F;

    /**
     * 最近几次强震（客户端一份；纯数据，不分配）。时间一律用秒（游戏刻 / 20）。
     */
    public static final class JoltTrack {

        public static final int CAPACITY = 4;
        private final double[] start = new double[CAPACITY];
        private final float[] strength = new float[CAPACITY];
        private int next;

        public JoltTrack() {
            clear();
        }

        /** 记一次强震：从 {@code seconds} 起、强度 0~1（最多记 {@link #CAPACITY} 次，挤掉最早的） */
        public void add(double seconds, float jolt) {
            start[next] = seconds;
            strength[next] = clamp01(jolt);
            next = (next + 1) % CAPACITY;
        }

        public void clear() {
            for (int i = 0; i < CAPACITY; i++) {
                start[i] = Double.NEGATIVE_INFINITY;
                strength[i] = 0F;
            }
            next = 0;
        }

        /** 时刻 t 所有强震叠起来的 roll（out[0]）与 pitch（out[1]），度，没乘任何系数 */
        void sample(double t, float[] out) {
            float roll = 0F;
            float pitch = 0F;
            for (int i = 0; i < CAPACITY; i++) {
                double dt = t - start[i];
                if (strength[i] <= 0F || dt < 0D || dt > JOLT_SECONDS) {
                    continue;
                }
                float wave = (float) (Math.exp(-dt / JOLT_TAU) * Math.sin(2D * Math.PI * JOLT_HZ * dt)) * strength[i];
                roll += JOLT_ROLL * wave;
                pitch += JOLT_PITCH * wave;
            }
            out[0] = roll;
            out[1] = pitch;
        }
    }

    /**
     * 地震的镜头偏移（只改画面，在 {@code ViewportEvent.ComputeCameraAngles} 里加到 roll / pitch 上）。
     *
     * @param seconds           时刻（游戏刻 + partialTick，÷ 20）
     * @param quakeFade         地震淡入值 0~1
     * @param jolts             强震（可 null）
     * @param screenEffectScale 原版「屏幕扭曲效果」0~1
     * @param cameraShake       客户端配置 camera_shake 0~1
     * @param exempt            创造 / 旁观：不晃
     * @param out               至少 2 个 float：roll、pitch（度）
     */
    public static void shake(double seconds, float quakeFade, JoltTrack jolts, float screenEffectScale, float cameraShake,
                             boolean exempt, float[] out) {
        float k = clamp01(quakeFade) * clamp01(screenEffectScale) * clamp01(cameraShake);
        if (exempt || k <= 0F) {
            out[0] = 0F;
            out[1] = 0F;
            return;
        }
        double w = 2D * Math.PI;
        float roll = (float) (ROLL_A1 * Math.sin(w * ROLL_F1 * seconds + PHI_1) + ROLL_A2 * Math.sin(w * ROLL_F2 * seconds + PHI_2));
        float pitch = (float) (PITCH_A1 * Math.sin(w * PITCH_F1 * seconds + PHI_3) + PITCH_A2 * Math.sin(w * PITCH_F2 * seconds + PHI_4));
        if (jolts != null) {
            jolts.sample(seconds, out);
            roll += out[0];
            pitch += out[1];
        }
        out[0] = Mth.clamp(Mth.clamp(roll, -MAX_ROLL, MAX_ROLL) * k, -MAX_ROLL, MAX_ROLL);
        out[1] = Mth.clamp(Mth.clamp(pitch, -MAX_PITCH, MAX_PITCH) * k, -MAX_PITCH, MAX_PITCH);
    }

    /** 强震声的音量（sound.deep_noise，定义里已乘 0.25、音高 0.5）：0.5 + 0.2 × 强度，即 0.5~0.7 */
    public static float joltVolume(float strength) {
        return 0.5F + 0.2F * clamp01(strength);
    }

    // ==================================================================
    // 事件粒子与环境音（§3.4）
    // ==================================================================

    /** 日阵余烬：原版 DustColorTransitionOptions(#FFB066 → #6A2E1A, 1.2)，淡入值 > 0.2 才出 */
    public static final int EMBER_FROM = 0xFFB066;
    public static final int EMBER_TO = 0x6A2E1A;
    public static final float EMBER_SCALE = 1.2F;
    public static final float EMBER_MIN_FADE = 0.2F;
    /** 同时最多几个余烬、几团地震尘（按粒子的最长寿命记账，见 {@code HoannaEventVisuals}） */
    public static final int MAX_EMBERS = 80;
    public static final int MAX_DUST = 40;
    /** 地脉风暴的光点：淡紫（伴月的颜色一系，只占小面积）、大小、每刻最多几颗、离玩家最远几格、同时最多几颗、风暴淡入值超过多少才放 */
    public static final int STORM_MOTE_COLOR = 0xC8B0FF;
    public static final float STORM_MOTE_SCALE = 0.8F;
    public static final int STORM_MOTES_PER_TICK = 2;
    public static final float STORM_MOTE_RADIUS = 16F;
    /** 光点离镜头超过这么远就不放（{@link #STORM_MOTE_RADIUS} 本来就在它以内，留给以后调半径时守着） */
    public static final float STORM_MOTE_MAX_DISTANCE = 24F;
    public static final int MAX_STORM_MOTES = 64;
    public static final float STORM_MOTE_MIN_FADE = 0.2F;
    /** 辐射热点的细尘：区块底数至少几、几刻一颗、玩家几格内、颜色（灰绿）、大小 */
    public static final int HOTSPOT_MIN_BASE = 4;
    public static final int HOTSPOT_INTERVAL = 10;
    public static final float HOTSPOT_RADIUS = 8F;
    public static final int HOTSPOT_COLOR = 0x8C9A6E;
    public static final float HOTSPOT_SCALE = 0.6F;

    /** 这个底数的区块要不要冒细尘（底数 0~5：≥ 4 的是辐射热点） */
    public static boolean hotspotVisible(int chunkBase) {
        return chunkBase >= HOTSPOT_MIN_BASE;
    }

    /** 黑暗：洞穴音每 tick 0.25%（原作 1%）、远处怪物音每 tick 0.1%，都乘黑暗淡入值、AMBIENT 类别 */
    public static final float DARK_CAVE_CHANCE = 0.0025F;
    public static final float DARK_MONSTER_CHANCE = 0.001F;

    // ==================================================================
    // 毒雾雾团（client/hoanna/FumeHazeRenderer，§3.4）
    // ==================================================================

    /** 雾团颜色（黄绿，全维度唯一的病态色） */
    public static final int FUME_HAZE_COLOR = 0x98A66C;
    public static final float FUME_HAZE_MAX_ALPHA = 0.30F;
    /** 每只毒雾几团、半径（格） */
    public static final int FUME_PUFFS = 3;
    public static final float FUME_PUFF_MIN_RADIUS = 1.6F;
    public static final float FUME_PUFF_MAX_RADIUS = 2.2F;
    /** 离镜头多近开始淡出（1.0~3.5 格 smoothstep）、多远淡没（40~56 格） */
    public static final float FUME_NEAR_START = 1.0F;
    public static final float FUME_NEAR_END = 3.5F;
    public static final float FUME_FAR_START = 40F;
    public static final float FUME_FAR_END = 56F;
    /** 附近强度 p：Σ max(0, 1 − d / 10)，夹到 1 */
    public static final float FUME_NEARBY_RANGE = 10F;

    /**
     * 雾团中心的不透明度：0.30 × (0.4 + 0.6 × 光照) × smoothstep(1.0, 3.5, d) × (1 − smoothstep(40, 56, d))。
     * 光照 0~1 每 tick 采一次：夜里雾团跟着暗（颜色另乘 {@link #fumeHazeShade}），不会像原作那样越老越亮；比计划的「只乘光照」多留 0.4 的底，
     * 夜里毒雾事件（最长 10 分钟）照样看得见危险在哪。
     */
    public static float fumeHazeAlpha(float light, float distance) {
        float near = smoothstep(FUME_NEAR_START, FUME_NEAR_END, distance);
        float far = 1F - smoothstep(FUME_FAR_START, FUME_FAR_END, distance);
        return FUME_HAZE_MAX_ALPHA * (0.4F + 0.6F * clamp01(light)) * near * far;
    }

    /** 雾团颜色的亮度倍数：0.3 + 0.7 × 光照（夜里是暗的黄绿） */
    public static float fumeHazeShade(float light) {
        return 0.3F + 0.7F * clamp01(light);
    }

    /** 失明遮住视野要多少 tick 退回去（原版 FogRenderer：效果最后 20 tick 雾距离和雾色慢慢恢复） */
    public static final int BLINDNESS_FADE_TICKS = 20;

    /**
     * 失明的遮挡强度 0~1，照原版 {@code FogRenderer.BlindnessFogFunction}：无限时长恒为 1，否则 min(1, 剩余 tick / 20)
     * （剩最后 1 秒时雾退回去）。
     */
    public static float blindnessStrength(int duration, boolean infinite) {
        return infinite ? 1F : clamp01(duration / (float) BLINDNESS_FADE_TICKS);
    }

    /**
     * 镜头实体被失明 / 黑暗遮住视野的程度 0~1。只认这两种效果（原版此时也不画天空，{@code HoannaSkyRenderer.effectBlocksSky} 同一个口径），
     * 强度照原版雾：失明 {@link #blindnessStrength}，黑暗取效果自带的淡入系数（{@code MobEffectInstance.FactorData}，约 1 秒升到 1、结束前约 1 秒降回 0）；
     * 两种都有取大的，不是生物为 0。
     */
    public static float visionBlocked(@Nullable Entity camera, float partialTick) {
        if (!(camera instanceof LivingEntity living)) {
            return 0F;
        }
        float blocked = 0F;
        MobEffectInstance blindness = living.getEffect(MobEffects.BLINDNESS);
        if (blindness != null) {
            blocked = blindnessStrength(blindness.getDuration(), blindness.isInfiniteDuration());
        }
        MobEffectInstance darkness = living.getEffect(MobEffects.DARKNESS);
        if (darkness != null) {
            Optional<MobEffectInstance.FactorData> factor = darkness.getFactorData();
            blocked = Math.max(blocked, factor.isPresent() ? clamp01(factor.get().getFactor(living, partialTick)) : 1F);
        }
        return blocked;
    }

    /**
     * 雾团在失明 / 黑暗下还剩几成：1 − {@link #visionBlocked}。雾团用 {@code position_color} 画、不吃雾，
     * 不这样压的话失明时四周一片黑，毒雾却还是一团团亮着的黄绿；跟着效果强度走，雾退回来时雾团也一起淡回来。
     */
    public static float fumeHazeVisibility(float visionBlocked) {
        return 1F - clamp01(visionBlocked);
    }

    /** 一只毒雾对附近强度的贡献 */
    public static float fumeNearbyWeight(float distance) {
        return Math.max(0F, 1F - distance / FUME_NEARBY_RANGE);
    }

    /** 附近强度 p 超过它才去拉近地形雾（ViewportEvent.RenderFog） */
    public static final float FUME_FOG_THRESHOLD = 0.01F;
    /**
     * 地形雾拉近：终点 = lerp(p, 原终点, min(原终点, max(24, 原终点 × 0.35)))，起点 = lerp(p, 原起点, min(原起点, 新终点 × 0.1))。
     * 只拉近、不推远：原终点已经 < 24 格（失明 5 格、黑暗 15 格、别的模组的近雾）时原样不动（6a 审查 M3-4）。
     */
    public static final float FUME_FOG_MIN_END = 24F;
    public static final float FUME_FOG_END_SCALE = 0.35F;
    public static final float FUME_FOG_START_SCALE = 0.1F;

    /** 毒雾近处的地形雾终点（对任意 p 都 ≤ 原终点） */
    public static float fumeFogEnd(float p, float end) {
        return Mth.lerp(clamp01(p), end, Math.min(end, Math.max(FUME_FOG_MIN_END, end * FUME_FOG_END_SCALE)));
    }

    /** 毒雾近处的地形雾起点（随 p 连续变化，p 刚过门槛时不跳） */
    public static float fumeFogStart(float p, float start, float newEnd) {
        return Mth.lerp(clamp01(p), start, Math.min(start, newEnd * FUME_FOG_START_SCALE));
    }

    // ==================================================================
    // 辐射晕影（client/hoanna/RadiationOverlay，§3.4；原作没有）
    // ==================================================================

    /** 晕影颜色：EC 紫（原作辐射效果色 0x660066 一系，不用核绿） */
    public static final int RADIATION_VIGNETTE_COLOR = 0x6B4A7E;
    /** 从屏幕边缘往里多宽（屏幕短边的比例） */
    public static final float RADIATION_VIGNETTE_BAND = 0.22F;
    public static final int RADIATION_VIGNETTE_FROM = 1_000;
    public static final int RADIATION_VIGNETTE_FULL = 30_000;
    public static final float RADIATION_VIGNETTE_MIN = 0.05F;
    public static final float RADIATION_VIGNETTE_MAX = 0.20F;
    /** 每 tick 往目标平滑多少 */
    public static final float RADIATION_VIGNETTE_RATE = 0.1F;

    /** 辐射晕影的目标不透明度：r < 1 000 为 0，否则 0.05 + 0.15 × clamp((r − 1 000) / 29 000)（1 万约 0.10、3 万封顶 0.20） */
    public static float vignetteAlpha(int radiation) {
        if (radiation < RADIATION_VIGNETTE_FROM) {
            return 0F;
        }
        float t = (radiation - RADIATION_VIGNETTE_FROM) / (float) (RADIATION_VIGNETTE_FULL - RADIATION_VIGNETTE_FROM);
        return RADIATION_VIGNETTE_MIN + (RADIATION_VIGNETTE_MAX - RADIATION_VIGNETTE_MIN) * clamp01(t);
    }

    // ==================================================================
    // 事件提示条（client/hoanna/HoannaEventHud，D8 A）
    // ==================================================================

    /** 开头多少 tick 展开显示名字 + 进度条，之后缩成「图标 + mm:ss」 */
    public static final int HUD_EXPANDED_TICKS = 200;
    /** 出现、消失、展开→收起的交叉淡化（tick） */
    public static final int HUD_FADE_TICKS = 10;

    /** 事件的强调色（只占小面积：左边竖条、图标、进度条）：日阵琥珀、毒雾酸绿、黑暗紫、地震赭、地脉风暴淡紫（比黑暗亮一档、偏蓝） */
    public static int hudAccent(HoannaEvent event) {
        return switch (event) {
            case SUN_ARRAY -> 0xFFB347;
            case FUMES -> 0xA8C64A;
            case DARKNESS -> 0x7A5BA8;
            case EARTHQUAKE -> 0xB08A5A;
            case LEY_STORM -> 0x9B7FD4;
            default -> 0x9992A6;
        };
    }

    /** 提示条没有 Boss 血条时离屏幕顶多远 */
    public static final int HUD_TOP_MARGIN = 4;

    /**
     * 一条 Boss 血条（{@code CustomizeGuiOverlayEvent.BossEventProgress}）占到哪一行：y + 增量 − 字高，即下一条的名字顶
     * （原版 y + 10，血条下留 5 像素）。被取消的事件照样算：自定义血条模组取消事件自己画、并用增量声明占多高（6a 审查 M3-5）。
     */
    public static int hudBossSlotBottom(int y, int increment, int lineHeight) {
        return y + increment - lineHeight;
    }

    /** 提示条的顶：这一帧各条 Boss 血条 {@link #hudBossSlotBottom} 的最大值（没有时传 −1），至少 {@link #HUD_TOP_MARGIN} */
    public static int hudTop(int bossBottom) {
        return Math.max(HUD_TOP_MARGIN, bossBottom);
    }

    /** 原版快捷栏半宽（{@code Gui.renderHotbar}：182 宽、以 screenWidth / 2 居中；选中框再往外 1 像素） */
    public static final int HOTBAR_HALF_WIDTH = 91;
    /** 快捷栏那一行的顶离屏幕底多远（选中框、副手槽都从 screenHeight − 23 画起） */
    public static final int HOTBAR_ROW_TOP = 23;
    /** 收起态和快捷栏左端、副手槽顶之间留的空 */
    public static final int HUD_COLLAPSED_GAP = 2;
    /** 收起态的高（9 像素图标上下各留 2） */
    public static final int HUD_COLLAPSED_HEIGHT = 13;
    /** 收起态离屏幕左边至少多远；放不下就退回顶部居中 */
    public static final int HUD_EDGE_MARGIN = 2;

    /**
     * 收起态（图标 + mm:ss）的左上角，写进 {@code out[0]}、{@code out[1]}。
     * <p>
     * 开头 10 秒的展开态在顶部居中，要让人看见「来了什么」；之后要一直挂几十分钟，放在顶部会一直压着画面正上方，所以收起后挪到
     * <b>快捷栏左端的左上方</b>：右边离快捷栏左端（screenWidth / 2 − 91）再空 {@link #HUD_COLLAPSED_GAP}，底边在副手槽顶（screenHeight − 23）上面
     * {@link #HUD_COLLAPSED_GAP}。原版 HUD 在这一块是空的：
     * <ul>
     *   <li>血量、吸收、护甲从快捷栏左端往右排（x ≥ screenWidth / 2 − 91），饥饿、氧气、坐骑血量在右半边，经验条和等级数字在快捷栏正上方——都在快捷栏那一栏里，这里整个在它左边；</li>
     *   <li>副手槽（左手为副手时，在快捷栏左边 29 宽）和「攻击指示器：快捷栏」（主手为左手时画在左边）都在快捷栏那一行（screenHeight − 23 往下），这里在它上面；</li>
     *   <li>聊天栏的底是 screenHeight − 40，这里的顶是 screenHeight − 25 − 高，13 高时在 screenHeight − 38，碰不到。</li>
     * </ul>
     * 屏幕太窄（GUI 缩放把宽缩到放不下，左边剩不到 {@link #HUD_EDGE_MARGIN}）或太矮时退回顶部居中（{@code top} 是 {@link #hudTop} 的结果），不出屏、不压快捷栏。
     */
    public static void hudCollapsedPos(int screenWidth, int screenHeight, int w, int h, int top, int[] out) {
        int right = screenWidth / 2 - HOTBAR_HALF_WIDTH - HUD_COLLAPSED_GAP;
        int x = right - w;
        int y = screenHeight - HOTBAR_ROW_TOP - HUD_COLLAPSED_GAP - h;
        if (x < HUD_EDGE_MARGIN || y < top) {
            x = (screenWidth - w) / 2;
            y = top;
        }
        out[0] = x;
        out[1] = y;
    }

    /** 剩余时间 mm:ss（分钟不补零，最多 3 位；负数按 0） */
    public static String formatRemaining(int ticks) {
        int seconds = Math.max(0, (ticks + 19) / 20);
        int minutes = Math.min(999, seconds / 60);
        int rest = seconds % 60;
        return minutes + ":" + (rest < 10 ? "0" : "") + rest;
    }

    // ==================================================================
    // 小工具
    // ==================================================================

    /** 往目标平滑：current + (target − current) × rate，差很小时直接到位 */
    public static float approach(float current, float target, float rate) {
        float next = current + (target - current) * rate;
        return Math.abs(target - next) < 0.001F ? target : next;
    }

    /** 标准 smoothstep：x 在 [e0, e1] 里从 0 平滑升到 1 */
    public static float smoothstep(float e0, float e1, float x) {
        if (e1 == e0) {
            return x < e0 ? 0F : 1F;
        }
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3F - 2F * t);
    }

    public static float clamp01(float v) {
        return v <= 0F ? 0F : v >= 1F ? 1F : v;
    }

    public static float red(int rgb) {
        return (rgb >> 16 & 255) / 255F;
    }

    public static float green(int rgb) {
        return (rgb >> 8 & 255) / 255F;
    }

    public static float blue(int rgb) {
        return (rgb & 255) / 255F;
    }

    /** 两个 0xRRGGBB 颜色按 t 混合（t = 0 取 a） */
    public static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, a >> 16 & 255, b >> 16 & 255));
        int g = Math.round(Mth.lerp(t, a >> 8 & 255, b >> 8 & 255));
        int bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return r << 16 | g << 8 | bl;
    }

    /**
     * CIELAB（sRGB → XYZ D65 → Lab，同 {@code tools/gen/hoanna_biomes.py} 的 lab()）。
     *
     * @param out 至少 3 个 float：L*、a*、b*
     */
    public static void lab(float r, float g, float b, float[] out) {
        double lr = linear(r);
        double lg = linear(g);
        double lb = linear(b);
        double x = (0.4124D * lr + 0.3576D * lg + 0.1805D * lb) / 0.95047D;
        double y = 0.2126D * lr + 0.7152D * lg + 0.0722D * lb;
        double z = (0.0193D * lr + 0.1192D * lg + 0.9505D * lb) / 1.08883D;
        double fx = labF(x);
        double fy = labF(y);
        double fz = labF(z);
        out[0] = (float) (116D * fy - 16D);
        out[1] = (float) (500D * (fx - fy));
        out[2] = (float) (200D * (fy - fz));
    }

    /** CIELAB 彩度 C* = √(a*² + b*²)（判断「紫不紫」用它，HSV 饱和度在很暗的颜色上会虚高） */
    public static float chroma(float r, float g, float b) {
        float[] lab = new float[3];
        lab(r, g, b, lab);
        return (float) Math.hypot(lab[1], lab[2]);
    }

    public static float chroma(int rgb) {
        return chroma(red(rgb), green(rgb), blue(rgb));
    }

    /** CIELAB 明度 L* */
    public static float lightness(float r, float g, float b) {
        float[] lab = new float[3];
        lab(r, g, b, lab);
        return lab[0];
    }

    public static float lightness(int rgb) {
        return lightness(red(rgb), green(rgb), blue(rgb));
    }

    private static double linear(float c) {
        return c <= 0.04045F ? c / 12.92D : Math.pow((c + 0.055D) / 1.055D, 2.4D);
    }

    private static double labF(double t) {
        return t > 216D / 24389D ? Math.cbrt(t) : (24389D / 27D * t + 16D) / 116D;
    }
}
