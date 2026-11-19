package com.flameforge.essentialcraft5.client.vfx;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * 特效的渲染类型。继承 {@link RenderType} 只为拿到 protected 的状态常量（同 {@code MRUPresenceRenderer.RayRenderType}）。
 * <h2>两套画法</h2>
 * <ul>
 *   <li><b>完整画法</b>：自带着色器 {@code vfx}（{@link #ADD} 加色、{@link #ALPHA} 普通半透明）和 {@code vfx_distort}（{@link #DISTORT}）。
 *       都不写深度、不剔背面、照常做深度测试（被方块挡住的部分不画）；加色和半透明画进粒子缓冲（Fabulous 画质下和半透明方块正确合成，别的画质就是主缓冲）。</li>
 *   <li><b>稳妥画法</b>（{@link Vfx#safeMode()}，光影包开着、或自带着色器没编译成）：同样的面片，换成原版着色器 + {@link VfxAtlas} 预烘贴图。
 *       用哪个原版着色器由 {@link #safeVariant} 定：0 = {@code position_color_tex}（光影包当「带贴图的粒子」画），1 = 信标光柱，2 = 发光眼睛层；
 *       三种在光影包下的表现不同（会不会被光照压暗、有没有泛光），默认值是在影棚光影环境里对比过定的（见 {@link #safeVariant}）。</li>
 * </ul>
 * 稳妥画法的顶点格式各不相同，{@link VfxDraw} 对每个顶点把「位置、颜色、贴图坐标、覆盖层、光照、法线」按顺序全写一遍：
 * 原版顶点缓冲遇到格式里没有的元素直接跳过（{@code BufferVertexConsumer} 按当前元素判断），所以一套写法通吃三种格式。
 */
public final class VfxRenderTypes extends RenderType {

    /**
     * 稳妥画法用哪个原版着色器（见类注释）；开发时可以用 TestHelper 的 setstatic 当场切换对比。
     * 默认 2（发光眼睛层）：2026-11-09 在两款常见光影包下对照过——0 两款都坏（发绿、只剩细线），
     * 1（信标光柱）在其中一款下正常，另一款下泛光过曝、柔光变成一层层硬边的罩子、渐变的地面填充出网点；2 两款都正常，最接近完整画法。
     */
    public static int safeVariant = 2;
    /** 稳妥画法的种类数 */
    public static final int SAFE_VARIANTS = 3;

    private static final ShaderStateShard VFX_SHADER = new ShaderStateShard(VfxShaders::vfx);
    private static final ShaderStateShard DISTORT_SHADER = new ShaderStateShard(VfxShaders::distort);
    private static final EmptyTextureStateShard ATLAS_TEXTURE = new EmptyTextureStateShard(
            () -> RenderSystem.setShaderTexture(0, VfxAtlas.textureId()), () -> {
    });
    private static final EmptyTextureStateShard SCENE_TEXTURE = new EmptyTextureStateShard(VfxRenderTypes::bindScene, () -> {
    });

    /** 完整画法：加色 */
    public static final RenderType ADD = create(EssentialCraft5.MODID + ":vfx_add", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder()
                    .setShaderState(VFX_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(PARTICLES_TARGET)
                    .createCompositeState(false));

    /** 完整画法：普通半透明（烟、暗色的东西） */
    public static final RenderType ALPHA = create(EssentialCraft5.MODID + ":vfx_alpha", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS, 1 << 14, false, false,
            CompositeState.builder()
                    .setShaderState(VFX_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(PARTICLES_TARGET)
                    .createCompositeState(false));

    /** 屏幕扭曲：采场景拷贝（{@link VfxScreen#sceneTextureId()}），按普通半透明盖回主缓冲 */
    public static final RenderType DISTORT = create(EssentialCraft5.MODID + ":vfx_distort", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS, 1 << 13, false, false,
            CompositeState.builder()
                    .setShaderState(DISTORT_SHADER)
                    .setTextureState(SCENE_TEXTURE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** 冲击帧压暗（{@link VfxScreen}）：纯色普通半透明，不测深度、不写深度，贴在镜头前盖满视野，先于特效画 */
    public static final RenderType SCREEN_SHADE = create(EssentialCraft5.MODID + ":vfx_screen_shade", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(PARTICLES_TARGET)
                    .createCompositeState(false));

    /** 速度线（{@link VfxScreen}）：纯色加色三角形，不测深度、不写深度，贴在镜头前，最后画 */
    public static final RenderType SCREEN_LINES = create(EssentialCraft5.MODID + ":vfx_screen_lines", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 2048, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(PARTICLES_TARGET)
                    .createCompositeState(false));

    private static final RenderType[] SAFE_ADD = new RenderType[SAFE_VARIANTS];
    private static final RenderType[] SAFE_ALPHA = new RenderType[SAFE_VARIANTS];

    static {
        ShaderStateShard[] shaders = {POSITION_COLOR_TEX_SHADER, RENDERTYPE_BEACON_BEAM_SHADER, RENDERTYPE_EYES_SHADER};
        VertexFormat[] formats = {DefaultVertexFormat.POSITION_COLOR_TEX, DefaultVertexFormat.BLOCK, DefaultVertexFormat.NEW_ENTITY};
        for (int i = 0; i < SAFE_VARIANTS; i++) {
            SAFE_ADD[i] = create(EssentialCraft5.MODID + ":vfx_safe_add_" + i, formats[i], VertexFormat.Mode.QUADS, 1 << 16, false, false,
                    CompositeState.builder()
                            .setShaderState(shaders[i])
                            .setTextureState(ATLAS_TEXTURE)
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .setOutputState(PARTICLES_TARGET)
                            .createCompositeState(false));
            SAFE_ALPHA[i] = create(EssentialCraft5.MODID + ":vfx_safe_alpha_" + i, formats[i], VertexFormat.Mode.QUADS, 1 << 14, false, false,
                    CompositeState.builder()
                            .setShaderState(shaders[i])
                            .setTextureState(ATLAS_TEXTURE)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .setOutputState(PARTICLES_TARGET)
                            .createCompositeState(false));
        }
    }

    private VfxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
                           boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /** 当前画法下的加色类型 */
    public static RenderType additive(boolean safe) {
        return safe ? SAFE_ADD[Math.floorMod(safeVariant, SAFE_VARIANTS)] : ADD;
    }

    /** 当前画法下的半透明类型 */
    public static RenderType translucent(boolean safe) {
        return safe ? SAFE_ALPHA[Math.floorMod(safeVariant, SAFE_VARIANTS)] : ALPHA;
    }

    /** 所有类型（建固定缓冲用） */
    static RenderType[] all() {
        RenderType[] out = new RenderType[3 + SAFE_VARIANTS * 2];
        out[0] = DISTORT;
        out[1] = ALPHA;
        out[2] = ADD;
        for (int i = 0; i < SAFE_VARIANTS; i++) {
            out[3 + i * 2] = SAFE_ALPHA[i];
            out[4 + i * 2] = SAFE_ADD[i];
        }
        return out;
    }

    private static void bindScene() {
        RenderSystem.setShaderTexture(0, VfxScreen.sceneTextureId());
        ShaderInstance shader = VfxShaders.distort();
        if (shader != null) {
            // 原版的 ScreenSize 是窗口尺寸；录制、截图时主帧缓冲可能和窗口不一样大，按主帧缓冲算屏幕坐标
            var target = Minecraft.getInstance().getMainRenderTarget();
            shader.safeGetUniform("TargetSize").set((float) target.width, (float) target.height);
        }
    }
}
