package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.client.render.ArcEffect;
import com.flameforge.essentialcraft5.hoanna.HoannaClientConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaVisuals;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * 海纳尔的天空（第六批 6a M3，batch6a.md §3.3；{@link HoannaEffects#renderSky} 调，原版天空不画）。每帧按顺序：
 * <ol>
 *   <li>先 {@code setupFog}；{@code isFoggy}、镜头在细雪 / 岩浆里、镜头实体有失明或黑暗效果时到此为止（原版此时也不画天空，清屏色就是雾色）。</li>
 *   <li><b>天穹</b>：照原版 {@code buildSkyDisc(16)} 的静态圆盘、{@code position} 着色器（带天空雾，下沿淡成雾色），颜色 =
 *       {@link HoannaVisuals#tint}(level.getSkyColor) 再和夜空下限逐分量取大。</li>
 *   <li><b>地平线带</b>（6a 审查 M3-1 / M3-2，取代原来 y = −16 的裙边）：半径 {@link HoannaVisuals#HORIZON_RADIUS} 的一圈球面，从 −90° 到 30°，
 *       {@code position_color}、<b>不带雾</b>、颜色 = 当前雾色、顶点 alpha 按仰角（{@link HoannaVisuals#horizonAlpha}：地平线以下全是 1，往上照原版视距 12 的天空雾曲线淡到 0）。
 *       <ul>
 *         <li>天穹的渐变全靠天空雾距离：远景模组把天空雾终点推到约 640 格时，渐变被挤进 1.8°~5°、外沿只有约 0.9 的雾量，海纳尔暗天 + 亮雾就成了一道硬线；
 *             带子不看雾距离，任何模组改天空雾都至少有这么厚的尘霾。</li>
 *         <li>整个下半球都写成雾色（不画原版黑盘；远景模组也常把地平线下的天空像素画成雾色，两边一致）。以前天穹外沿（+1.79°）和裙边外沿（−1.79°）之间、
 *             以及视距 &lt; 8 时被远平面（视距 × 64 格）裁掉的外圈一个像素都没写，开光影时光影包会在那里画它自己的天。半径 ≤ 100 保证视距 2 也不被裁。</li>
 *       </ul></li>
 *   <li><b>日出扇</b>：原版的朝向与形状（静态 VBO，形变用矩阵缩放），颜色 {@link HoannaVisuals#sunrise}。</li>
 *   <li><b>日月</b>：加色混合，整组挂在原版太阳的同一个转动上（{@link HoannaVisuals#BODIES}；日阵另 6 个残日一个 VBO，alpha = 日阵淡入值）。
 *       顶点 alpha 恒为 1，淡入淡出全走 {@code setShaderColor}（{@code position_tex_color} 的 0.1 丢弃阈值）。</li>
 *   <li><b>星星</b>、<b>MRU 光幕</b>（{@code sky_extras}）：同一姿态，随夜空慢慢转，本身不做动画。</li>
 *   <li><b>远空电弧</b>（{@code sky_extras}）：由 {@link HoannaEventVisuals} 按 {@link HoannaVisuals.ArcLimiter} 排期；{@code position_color}、加色、
 *       <b>不写深度</b>（天空阶段写深度会挡住远处地形和远景模组的远景；所以不能用 ArcRenderer / {@code RenderType.lightning()}）。
 *       形状用 {@link ArcEffect#planar} 在（方位角, 高度角）平面上生成、贴到半径 90 的天球上，出现时烘成一个 VBO。</li>
 *   <li>收尾：{@code setShaderColor(1,1,1,1)}、{@code depthMask(true)}、{@code disableBlend}、{@code defaultBlendFunc}；剔除只在地平线带、光幕、电弧前后关开。</li>
 * </ol>
 * 预算：≤ 8 次绘制（天穹、地平线带、日出扇、日、日阵、月、星、光幕、电弧——日出扇只在晨昏、日阵只在白天、电弧只在夜里，同时最多 8 个），
 * 约 6 k 顶点（地平线带 14 × 32 个四边形）。<b>本类自己稳态每帧不分配</b>：全是静态 VBO（渲染线程懒建，退出世界、资源重载时 {@link #release}），复用矩阵和数组，
 * 不 pushPose、不 new Random、不 new Vec3；主月相位变了（每 MC 日至多一次）或 {@code sky_extras} 改了才重建日 / 月那一份；
 * {@code level.getSkyColor} 是原版方法（原版天空每帧同样调一次）。
 * <p>
 * 和别的渲染的叠加（§3.5）：开光影时 {@code renderSky} 返回 true 的整片天都在 CUSTOM_SKY 阶段（地平线带盖满下半球，光影的像素分类里地平线附近不再有「原版天空」的缝）；
 * 天穹用传进来的 {@code setupFog}（FOG_SKY），改雾的模组多只管 FOG_TERRAIN、保留天空雾；远景模组推远天空雾时地平线由地平线带兜底。展示厅：{@link #showroomArc}。
 */
@OnlyIn(Dist.CLIENT)
public final class HoannaSkyRenderer {

    private static final ResourceLocation SUN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation MOON_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    /** 低于它的不透明度不画（省一次绘制） */
    private static final float MIN_ALPHA = 0.004F;
    private static final float HALF_PI = (float) Math.PI / 2F;
    private static final float TWO_PI = (float) Math.PI * 2F;

    /** 电弧：芯、光晕的半宽（格，天球半径 90 处；1080p 视野 70 时芯约 2 像素、光晕约 8 像素）、分叉粗细、分叉条数 */
    private static final float ARC_CORE_HALF_WIDTH = 0.12F;
    private static final float ARC_GLOW_HALF_WIDTH = 0.4F;
    private static final float ARC_GLOW_ALPHA = 0.3F;
    private static final float ARC_BRANCH_WIDTH = 0.7F;
    private static final int ARC_BRANCHES = 6;

    @Nullable
    private static VertexBuffer dome;
    @Nullable
    private static VertexBuffer band;
    @Nullable
    private static VertexBuffer fan;
    @Nullable
    private static VertexBuffer suns;
    @Nullable
    private static VertexBuffer sunArray;
    @Nullable
    private static VertexBuffer moons;
    @Nullable
    private static VertexBuffer stars;
    @Nullable
    private static VertexBuffer aurora;
    @Nullable
    private static VertexBuffer arc;
    private static boolean sunsExtras;
    private static int moonsPhase = -1;
    private static boolean moonsExtras;

    private static final Matrix4f CELESTIAL = new Matrix4f();
    /** 风暴中光幕用的矩阵（压低、加宽；平时直接用 CELESTIAL） */
    private static final Matrix4f AURORA_POSE = new Matrix4f();
    private static final Matrix4f FAN = new Matrix4f();
    private static final float[] RGB = new float[3];

    // ---- 电弧（出现时把折线写进这几个数组，下一帧在渲染线程烘成 VBO） ----
    private static final float[] ARC_POINTS = new float[ArcEffect.MAX_SEGMENTS * 4];
    private static final byte[] ARC_KINDS = new byte[ArcEffect.MAX_SEGMENTS];
    private static final RandomSource ARC_RANDOM = RandomSource.create();
    private static int arcSegments;
    private static boolean arcDirty;
    private static long arcStart = Long.MIN_VALUE / 4;
    private static boolean arcActive;
    /** 展示厅：电弧停在峰值 */
    private static boolean arcHold;

    private HoannaSkyRenderer() {
    }

    // ==================================================================
    // 每帧
    // ==================================================================

    static void render(HoannaEffects effects, ClientLevel level, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projection,
                       boolean isFoggy, Runnable setupFog) {
        setupFog.run();
        if (isFoggy) {
            return;
        }
        FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.POWDER_SNOW || fluid == FogType.LAVA || effectBlocksSky(camera.getEntity())) {
            return;
        }
        ensureStatic();
        boolean extras = HoannaClientConfig.skyExtras();
        float darkness = HoannaClientEvents.fade(HoannaEvent.DARKNESS, partialTick);
        float sunArrayFade = HoannaClientEvents.fade(HoannaEvent.SUN_ARRAY, partialTick);
        float quake = HoannaClientEvents.fade(HoannaEvent.EARTHQUAKE, partialTick);
        float storm = HoannaClientEvents.fade(HoannaEvent.LEY_STORM, partialTick);
        float fume = FumeHazeRenderer.nearby(partialTick);
        float timeOfDay = level.getTimeOfDay(partialTick);
        float rain = level.getRainLevel(partialTick);
        Matrix4f view = poseStack.last().pose();

        // 1. 天穹
        Vec3 sky = level.getSkyColor(camera.getPosition(), partialTick);
        HoannaVisuals.tint((float) sky.x, (float) sky.y, (float) sky.z, darkness, sunArrayFade, HoannaVisuals.daylight(timeOfDay), quake, fume, storm, RGB);
        HoannaVisuals.applyNightFloor(RGB);
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        ShaderInstance position = RenderSystem.getShader();
        RenderSystem.setShaderColor(RGB[0], RGB[1], RGB[2], 1F);
        draw(dome, view, projection, position);

        // 2. 地平线带（不带雾、按仰角的 alpha；地平线以下整片雾色）
        float[] fog = RenderSystem.getShaderFogColor();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(fog[0], fog[1], fog[2], 1F);
        draw(band, view, projection, RenderSystem.getShader());
        RenderSystem.enableCull();

        // 3. 日出扇
        float[] sunrise = effects.getSunriseColor(timeOfDay, partialTick);
        if (sunrise != null) {
            float alpha = sunrise[3] * (1F - 0.75F * darkness);
            if (alpha > MIN_ALPHA) {
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.setShaderColor(sunrise[0], sunrise[1], sunrise[2], alpha);
                float flip = Mth.sin(level.getSunAngle(partialTick)) < 0F ? (float) Math.PI : 0F;
                // 原版：XP(90)·ZP(flip)·ZP(90)，顶点 z = −cos·40·a；这里 VBO 按 a = 1 建，z 方向用矩阵缩放（a 用原版的强度，不含 ×0.55）
                FAN.set(view).rotateX(HALF_PI).rotateZ(flip).rotateZ(HALF_PI)
                        .scale(1F, 1F, sunrise[3] / HoannaVisuals.SUNRISE_ALPHA_SCALE);
                draw(fan, FAN, projection, RenderSystem.getShader());
            }
        }

        // 4. 日月（加色）
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        CELESTIAL.set(view).rotateY(-HALF_PI).rotateX(timeOfDay * TWO_PI);
        float bodyAlpha = HoannaVisuals.celestialAlpha(rain, darkness);
        if (bodyAlpha > MIN_ALPHA) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            ShaderInstance textured = RenderSystem.getShader();
            RenderSystem.setShaderTexture(0, SUN_LOCATION);
            ensureSuns(extras);
            RenderSystem.setShaderColor(1F, 1F, 1F, bodyAlpha);
            draw(suns, CELESTIAL, projection, textured);
            if (sunArrayFade > MIN_ALPHA) {
                RenderSystem.setShaderColor(1F, 1F, 1F, bodyAlpha * sunArrayFade);
                draw(sunArray, CELESTIAL, projection, textured);
            }
            RenderSystem.setShaderTexture(0, MOON_LOCATION);
            ensureMoons(level.getMoonPhase(), extras);
            RenderSystem.setShaderColor(1F, 1F, 1F, bodyAlpha);
            draw(moons, CELESTIAL, projection, textured);
        }

        if (extras) {
            // 5. 星星（原版做法：画前关雾、画完按天空雾重设）
            float starBrightness = level.getStarBrightness(partialTick);
            float starAlpha = HoannaVisuals.starAlpha(starBrightness, rain, darkness);
            if (starAlpha > MIN_ALPHA) {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.setShaderColor(starAlpha, starAlpha, starAlpha, starAlpha);
                FogRenderer.setupNoFog();
                draw(stars, CELESTIAL, projection, RenderSystem.getShader());
                setupFog.run();
            }
            // 6. MRU 光幕
            float auroraAlpha = HoannaVisuals.auroraAlpha(starBrightness, rain, darkness, HoannaClientEvents.tide(level), storm);
            if (auroraAlpha > MIN_ALPHA) {
                RenderSystem.disableCull();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.setShaderColor(1F, 1F, 1F, auroraAlpha);
                draw(aurora, auroraPose(view, timeOfDay, storm), projection, RenderSystem.getShader());
                RenderSystem.enableCull();
            }
            // 7. 远空电弧
            renderArc(level, partialTick, view, projection);
        }

        // 8. 收尾
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    /**
     * 光幕的姿态：平时就是 {@link #CELESTIAL}；风暴中先在视角里把竖直方向缩到 {@link HoannaVisuals#stormAuroraLower}（整条往地平线压），
     * 再在光幕自己的坐标里沿大圆极点方向拉宽 {@link HoannaVisuals#stormAuroraWiden}（极点 n = Rz(tilt)·(0, 1, 0)，所以是 Rz(tilt)·S_y·Rz(−tilt)）。
     * 天空不写深度、不受远平面影响（半径 100 × 1.6 也在 GameRenderer 的远平面内），缩放只改形状。
     */
    private static Matrix4f auroraPose(Matrix4f view, float timeOfDay, float storm) {
        if (storm <= MIN_ALPHA) {
            return CELESTIAL;
        }
        float tilt = HoannaVisuals.AURORA_TILT * Mth.DEG_TO_RAD;
        return AURORA_POSE.set(view).scale(1F, HoannaVisuals.stormAuroraLower(storm), 1F)
                .rotateY(-HALF_PI).rotateX(timeOfDay * TWO_PI)
                .rotateZ(tilt).scale(1F, HoannaVisuals.stormAuroraWiden(storm), 1F).rotateZ(-tilt);
    }

    private static void renderArc(ClientLevel level, float partialTick, Matrix4f view, Matrix4f projection) {
        if (!arcActive) {
            return;
        }
        float age = arcHold ? HoannaVisuals.ARC_RISE + 0.5F : (float) (level.getGameTime() - arcStart) + partialTick;
        if (age >= HoannaVisuals.ARC_LIFETIME || age < 0F) {
            arcActive = false;
            return;
        }
        boolean reduced = Minecraft.getInstance().options.hideLightningFlash().get();
        float alpha = HoannaVisuals.arcAlpha(age, reduced);
        if (alpha <= MIN_ALPHA) {
            return;
        }
        if (arcDirty || arc == null) {
            arcDirty = false;
            arc = replace(arc, buildArc());
        }
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
        draw(arc, view, projection, RenderSystem.getShader());
        RenderSystem.enableCull();
    }

    private static void draw(@Nullable VertexBuffer buffer, Matrix4f pose, Matrix4f projection, @Nullable ShaderInstance shader) {
        if (buffer == null || shader == null) {
            return;
        }
        buffer.bind();
        buffer.drawWithShader(pose, projection, shader);
        VertexBuffer.unbind();
    }

    /**
     * 失明 / 黑暗效果时原版也不画天空（{@link HoannaEventVisuals} 的雾色、毒雾拉近也按它让位）。
     * 毒雾雾团认同样两种效果，但按强度淡出（{@code HoannaVisuals.visionBlocked}），跟着原版雾一起退回来。
     */
    static boolean effectBlocksSky(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    // ==================================================================
    // 电弧排期（HoannaEventVisuals 在客户端 tick 里调）
    // ==================================================================

    /**
     * 出一道远空电弧：随机方位、高度角 4°~18°、跨 6°~14°（折线现在生成，VBO 下一帧在渲染线程烘）。
     *
     * @param gameTime 出现的游戏刻（寿命按它算）
     */
    static void spawnArc(long gameTime) {
        float azimuth = ARC_RANDOM.nextFloat() * 360F;
        spawnArcAt(gameTime, azimuth, Mth.lerp(ARC_RANDOM.nextFloat(), HoannaVisuals.ARC_MIN_ELEVATION, HoannaVisuals.ARC_MAX_ELEVATION));
    }

    private static void spawnArcAt(long gameTime, float azimuth, float elevation) {
        float span = Mth.lerp(ARC_RANDOM.nextFloat(), HoannaVisuals.ARC_MIN_SPAN, HoannaVisuals.ARC_MAX_SPAN);
        float direction = ARC_RANDOM.nextBoolean() ? 1F : -1F;
        float endElevation = Mth.clamp(elevation + (ARC_RANDOM.nextFloat() - 0.5F) * 8F, HoannaVisuals.ARC_MIN_ELEVATION, HoannaVisuals.ARC_MAX_ELEVATION);
        arcSegments = ArcEffect.planar(ARC_RANDOM, azimuth, elevation, azimuth + direction * span, endElevation,
                span * 0.07F, span * 0.25F, ARC_BRANCHES, ARC_POINTS, ARC_KINDS, 0);
        arcDirty = true;
        arcActive = true;
        arcStart = gameTime;
    }

    /**
     * 展示厅：马上在镜头正前方（高度角 10°）出一道电弧。
     *
     * @param hold true = 停在峰值不消失（拍截图用），直到下一次调用传 false 或 {@link #clearShowroomArc}
     */
    public static void showroomArc(boolean hold) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Entity camera = minecraft.getCameraEntity() != null ? minecraft.getCameraEntity() : minecraft.player;
        float yaw = camera != null ? camera.getYRot() : 0F;
        spawnArcAt(level.getGameTime(), yaw - 5F, 10F);
        arcHold = hold;
    }

    public static void clearShowroomArc() {
        arcHold = false;
        arcActive = false;
    }

    /** 离开海纳尔、退出世界时：电弧清掉 */
    static void resetArc() {
        arcActive = false;
        arcHold = false;
    }

    // ==================================================================
    // 静态几何
    // ==================================================================

    private static void ensureStatic() {
        if (dome == null) {
            dome = upload(buildSkyDisc(HoannaVisuals.SKY_DISC_HEIGHT));
            band = upload(buildHorizonBand());
            fan = upload(buildFan());
            sunArray = upload(buildBodies(HoannaVisuals.SUN_ARRAY_BODIES, HoannaVisuals.Kind.SUN, true, 0));
            stars = upload(buildStars());
            aurora = upload(buildAurora());
        }
    }

    private static void ensureSuns(boolean extras) {
        if (suns == null || sunsExtras != extras) {
            sunsExtras = extras;
            suns = replace(suns, buildBodies(HoannaVisuals.BODIES, HoannaVisuals.Kind.SUN, extras, 0));
        }
    }

    private static void ensureMoons(int phase, boolean extras) {
        if (moons == null || moonsPhase != phase || moonsExtras != extras) {
            moonsPhase = phase;
            moonsExtras = extras;
            moons = replace(moons, buildBodies(HoannaVisuals.BODIES, HoannaVisuals.Kind.MOON, extras, phase));
        }
    }

    /** 丢掉全部 VBO（退出世界、资源重载；下次画天空时重建）。只能在渲染线程调 */
    public static void release() {
        dome = close(dome);
        band = close(band);
        fan = close(fan);
        suns = close(suns);
        sunArray = close(sunArray);
        moons = close(moons);
        stars = close(stars);
        aurora = close(aurora);
        arc = close(arc);
        moonsPhase = -1;
        resetArc();
    }

    @Nullable
    private static VertexBuffer close(@Nullable VertexBuffer buffer) {
        if (buffer != null) {
            buffer.close();
        }
        return null;
    }

    private static VertexBuffer replace(@Nullable VertexBuffer old, BufferBuilder.RenderedBuffer data) {
        close(old);
        return upload(data);
    }

    private static VertexBuffer upload(BufferBuilder.RenderedBuffer data) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(data);
        VertexBuffer.unbind();
        return buffer;
    }

    private static BufferBuilder builder() {
        return Tesselator.getInstance().getBuilder();
    }

    /** 照原版 {@code LevelRenderer.buildSkyDisc}：半径 512 的扇形圆盘（y 为负时绕向反过来，从上面看是正面） */
    private static BufferBuilder.RenderedBuffer buildSkyDisc(float y) {
        BufferBuilder b = builder();
        float radius = HoannaVisuals.SKY_DISC_RADIUS;
        float f = Math.signum(y) * radius;
        b.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        b.vertex(0D, y, 0D).endVertex();
        for (int i = -180; i <= 180; i += 45) {
            float rad = i * Mth.DEG_TO_RAD;
            b.vertex(f * Mth.cos(rad), y, radius * Mth.sin(rad)).endVertex();
        }
        return b.end();
    }

    /**
     * 地平线带：{@link HoannaVisuals#HORIZON_RINGS} 相邻两环之间 {@link HoannaVisuals#HORIZON_SEGMENTS} 个四边形，白色顶点、alpha = 该环的
     * {@link HoannaVisuals#horizonAlpha}（颜色走 setShaderColor = 雾色）。最低一环 −90° 缩成一点（那一圈四边形退化成三角形）。画的时候关剔除，绕向无所谓。
     */
    private static BufferBuilder.RenderedBuffer buildHorizonBand() {
        BufferBuilder b = builder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] rings = HoannaVisuals.HORIZON_RINGS;
        int segments = HoannaVisuals.HORIZON_SEGMENTS;
        float radius = HoannaVisuals.HORIZON_RADIUS;
        for (int i = 0; i + 1 < rings.length; i++) {
            float e0 = rings[i] * Mth.DEG_TO_RAD;
            float e1 = rings[i + 1] * Mth.DEG_TO_RAD;
            float a0 = HoannaVisuals.horizonAlpha(rings[i]);
            float a1 = HoannaVisuals.horizonAlpha(rings[i + 1]);
            for (int j = 0; j < segments; j++) {
                float az0 = j * TWO_PI / segments;
                float az1 = (j + 1) * TWO_PI / segments;
                bandVertex(b, e0, az0, radius, a0);
                bandVertex(b, e0, az1, radius, a0);
                bandVertex(b, e1, az1, radius, a1);
                bandVertex(b, e1, az0, radius, a1);
            }
        }
        return b.end();
    }

    private static void bandVertex(BufferBuilder b, float elevation, float azimuth, float radius, float alpha) {
        float horizontal = Mth.cos(elevation) * radius;
        b.vertex(horizontal * Mth.cos(azimuth), Mth.sin(elevation) * radius, horizontal * Mth.sin(azimuth)).color(1F, 1F, 1F, alpha).endVertex();
    }

    /** 日出扇（原版形状，a = 1）：中心白、不透明，外圈透明；颜色和强度全走 setShaderColor */
    private static BufferBuilder.RenderedBuffer buildFan() {
        BufferBuilder b = builder();
        b.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        b.vertex(0D, 100D, 0D).color(1F, 1F, 1F, 1F).endVertex();
        for (int j = 0; j <= 16; j++) {
            float angle = j * TWO_PI / 16F;
            float sin = Mth.sin(angle);
            float cos = Mth.cos(angle);
            b.vertex(sin * 120F, cos * 120F, -cos * 40F).color(1F, 1F, 1F, 0F).endVertex();
        }
        return b.end();
    }

    /**
     * 一组天体（只取 {@code kind} 那一种；{@code extras} 为 false 时跳过带 extra 标记的）。四边形照原版太阳 / 月亮的顶点顺序建在 (0, 100, 0)，
     * 再乘 rotX(φ)·rotZ(θ)；月亮的 UV 照原版按相位取 moon_phases.png 的 4 × 2 格。
     */
    private static BufferBuilder.RenderedBuffer buildBodies(Iterable<HoannaVisuals.Body> bodies, HoannaVisuals.Kind kind, boolean extras, int phase) {
        BufferBuilder b = builder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f m = new Matrix4f();
        Vector3f v = new Vector3f();
        for (HoannaVisuals.Body body : bodies) {
            if (body.kind() != kind || (body.extra() && !extras)) {
                continue;
            }
            m.identity().rotateX(body.phi() * Mth.DEG_TO_RAD).rotateZ(body.theta() * Mth.DEG_TO_RAD);
            float s = body.halfSize();
            float h = HoannaVisuals.BODY_DISTANCE;
            float r = body.channel(0);
            float g = body.channel(1);
            float bl = body.channel(2);
            float a = HoannaVisuals.BODY_VERTEX_ALPHA;
            float u0 = 0F;
            float v0 = 0F;
            float u1 = 1F;
            float v1 = 1F;
            if (kind == HoannaVisuals.Kind.MOON) {
                int p = Math.floorMod(phase + body.phaseOffset(), 8);
                int col = p % 4;
                int row = p / 4 % 2;
                // 原版月亮在 y = −100：(−s, s)→(u1, v1)、(s, s)→(u0, v1)、(s, −s)→(u0, v0)、(−s, −s)→(u1, v0)；rotX(180°) 换回顶上的坐标
                u0 = (col + 1) / 4F;
                u1 = col / 4F;
                v0 = (row + 1) / 2F;
                v1 = row / 2F;
            }
            quadVertex(b, m, v, -s, h, -s, u0, v0, r, g, bl, a);
            quadVertex(b, m, v, s, h, -s, u1, v0, r, g, bl, a);
            quadVertex(b, m, v, s, h, s, u1, v1, r, g, bl, a);
            quadVertex(b, m, v, -s, h, s, u0, v1, r, g, bl, a);
        }
        return b.end();
    }

    private static void quadVertex(BufferBuilder b, Matrix4f m, Vector3f v, float x, float y, float z, float u, float tv,
                                   float r, float g, float bl, float a) {
        m.transformPosition(x, y, z, v);
        b.vertex(v.x(), v.y(), v.z()).uv(u, tv).color(r, g, bl, a).endVertex();
    }

    /** 星星：照原版 {@code drawStars} 的生成法（单位球里取点、随机自转），固定种子、900 颗、大小 0.10~0.20、淡紫白顶点色 */
    private static BufferBuilder.RenderedBuffer buildStars() {
        RandomSource random = RandomSource.create(HoannaVisuals.STAR_SEED);
        BufferBuilder b = builder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float cr = HoannaVisuals.red(HoannaVisuals.STAR_COLOR);
        float cg = HoannaVisuals.green(HoannaVisuals.STAR_COLOR);
        float cb = HoannaVisuals.blue(HoannaVisuals.STAR_COLOR);
        int made = 0;
        for (int attempt = 0; made < HoannaVisuals.STAR_COUNT && attempt < HoannaVisuals.STAR_COUNT * 8; attempt++) {
            double x = random.nextFloat() * 2F - 1F;
            double y = random.nextFloat() * 2F - 1F;
            double z = random.nextFloat() * 2F - 1F;
            double size = HoannaVisuals.STAR_MIN_SIZE + random.nextFloat() * (HoannaVisuals.STAR_MAX_SIZE - HoannaVisuals.STAR_MIN_SIZE);
            double len = x * x + y * y + z * z;
            if (len >= 1D || len <= 0.01D) {
                continue;
            }
            len = 1D / Math.sqrt(len);
            x *= len;
            y *= len;
            z *= len;
            double px = x * HoannaVisuals.STAR_RADIUS;
            double py = y * HoannaVisuals.STAR_RADIUS;
            double pz = z * HoannaVisuals.STAR_RADIUS;
            double yaw = Math.atan2(x, z);
            double sinYaw = Math.sin(yaw);
            double cosYaw = Math.cos(yaw);
            double pitch = Math.atan2(Math.sqrt(x * x + z * z), y);
            double sinPitch = Math.sin(pitch);
            double cosPitch = Math.cos(pitch);
            double spin = random.nextDouble() * Math.PI * 2D;
            double sinSpin = Math.sin(spin);
            double cosSpin = Math.cos(spin);
            for (int j = 0; j < 4; j++) {
                double a = ((j & 2) - 1) * size;
                double c = ((j + 1 & 2) - 1) * size;
                double d21 = a * cosSpin - c * sinSpin;
                double d22 = c * cosSpin + a * sinSpin;
                double d23 = d21 * sinPitch;
                double d24 = -d21 * cosPitch;
                double d25 = d24 * sinYaw - d22 * cosYaw;
                double d26 = d22 * sinYaw + d24 * cosYaw;
                b.vertex(px + d25, py + d23, pz + d26).color(cr, cg, cb, 1F).endVertex();
            }
            made++;
        }
        return b.end();
    }

    /**
     * MRU 光幕：本地坐标里 XZ 平面的大圆绕 Z 轴倾斜 35°，取以 u = 180° 为中心的 220° 一段（午夜时正好是北方天空、最高仰角 35°，两端各有 20° 落到地平线下），
     * 宽 ±9°（朝大圆的极点方向）：三排顶点——靠地平线的一沿 MRU 蓝、中线紫罗兰、朝天顶的一沿品红；横向 alpha 0 → 1 → 0，纵向两端 sin 淡出。
     */
    private static BufferBuilder.RenderedBuffer buildAurora() {
        BufferBuilder b = builder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float tilt = HoannaVisuals.AURORA_TILT * Mth.DEG_TO_RAD;
        float sinTilt = Mth.sin(tilt);
        float cosTilt = Mth.cos(tilt);
        // 大圆的极点 n = Rz(tilt)·(0, 1, 0)
        float nx = -sinTilt;
        float ny = cosTilt;
        float half = HoannaVisuals.AURORA_HALF_WIDTH * Mth.DEG_TO_RAD;
        float[] rowOffset = {half, 0F, -half};
        float[] rowAlpha = {0F, 1F, 0F};
        int segments = HoannaVisuals.AURORA_SEGMENTS;
        float from = (180F - HoannaVisuals.AURORA_ARC / 2F) * Mth.DEG_TO_RAD;
        float step = HoannaVisuals.AURORA_ARC * Mth.DEG_TO_RAD / segments;
        float radius = HoannaVisuals.AURORA_RADIUS;
        for (int i = 0; i < segments; i++) {
            float ua = from + i * step;
            float ub = ua + step;
            float ea = HoannaVisuals.auroraEnvelope(i / (float) segments);
            float eb = HoannaVisuals.auroraEnvelope((i + 1) / (float) segments);
            for (int row = 0; row < 2; row++) {
                auroraVertex(b, ua, rowOffset[row], cosTilt, sinTilt, nx, ny, radius, row, rowAlpha[row] * ea);
                auroraVertex(b, ub, rowOffset[row], cosTilt, sinTilt, nx, ny, radius, row, rowAlpha[row] * eb);
                auroraVertex(b, ub, rowOffset[row + 1], cosTilt, sinTilt, nx, ny, radius, row + 1, rowAlpha[row + 1] * eb);
                auroraVertex(b, ua, rowOffset[row + 1], cosTilt, sinTilt, nx, ny, radius, row + 1, rowAlpha[row + 1] * ea);
            }
        }
        return b.end();
    }

    private static void auroraVertex(BufferBuilder b, float u, float offset, float cosTilt, float sinTilt, float nx, float ny,
                                     float radius, int row, float alpha) {
        // 大圆上的点 p = Rz(tilt)·(cos u, 0, sin u)；横向偏移 q = cos(w)·p + sin(w)·n（p ⊥ n，q 仍是单位向量）
        float px = Mth.cos(u) * cosTilt;
        float py = Mth.cos(u) * sinTilt;
        float pz = Mth.sin(u);
        float cw = Mth.cos(offset);
        float sw = Mth.sin(offset);
        float qx = cw * px + sw * nx;
        float qy = cw * py + sw * ny;
        float qz = cw * pz;
        int color = HoannaVisuals.AURORA_COLORS[row];
        b.vertex(qx * radius, qy * radius, qz * radius)
                .color(HoannaVisuals.red(color), HoannaVisuals.green(color), HoannaVisuals.blue(color), alpha).endVertex();
    }

    /**
     * 当前电弧的朝镜头细条（两层：光晕、芯；分叉细一些）。镜头在天空坐标的原点，细条宽度方向 = 线段方向 × 指向原点的方向，和视角无关，所以能烘成静态 VBO。
     * 方位角按原版偏航角：0 = 南（+Z）、90 = 西（−X）。
     */
    private static BufferBuilder.RenderedBuffer buildArc() {
        BufferBuilder b = builder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int color = HoannaVisuals.ARC_COLOR;
        float cr = HoannaVisuals.red(color);
        float cg = HoannaVisuals.green(color);
        float cb = HoannaVisuals.blue(color);
        float gr = Mth.lerp(0.5F, cr, 1F);
        float gg = Mth.lerp(0.5F, cg, 1F);
        float gb = Mth.lerp(0.5F, cb, 1F);
        Vector3f a = new Vector3f();
        Vector3f c = new Vector3f();
        for (int layer = 0; layer < 2; layer++) {
            boolean glow = layer == 0;
            for (int i = 0; i < arcSegments; i++) {
                float width = (glow ? ARC_GLOW_HALF_WIDTH : ARC_CORE_HALF_WIDTH)
                        * (ARC_KINDS[i] == ArcEffect.Kind.BRANCH.ordinal() ? ARC_BRANCH_WIDTH : 1F);
                skyPoint(ARC_POINTS[i * 4], ARC_POINTS[i * 4 + 1], a);
                skyPoint(ARC_POINTS[i * 4 + 2], ARC_POINTS[i * 4 + 3], c);
                if (glow) {
                    strip(b, a, c, width, gr, gg, gb, ARC_GLOW_ALPHA);
                } else {
                    strip(b, a, c, width, cr, cg, cb, 1F);
                }
            }
        }
        return b.end();
    }

    private static void skyPoint(float azimuthDeg, float elevationDeg, Vector3f out) {
        float az = azimuthDeg * Mth.DEG_TO_RAD;
        // 分叉的抖动可能伸到地平线下：托在 1° 以上，海面上看不到电弧从地平线底下冒出来
        float el = Math.max(1F, elevationDeg) * Mth.DEG_TO_RAD;
        float cosEl = Mth.cos(el);
        out.set(-Mth.sin(az) * cosEl, Mth.sin(el), Mth.cos(az) * cosEl).mul(HoannaVisuals.ARC_RADIUS);
    }

    private static void strip(BufferBuilder b, Vector3f a, Vector3f c, float halfWidth, float r, float g, float bl, float alpha) {
        float dx = c.x() - a.x();
        float dy = c.y() - a.y();
        float dz = c.z() - a.z();
        // 中点 → 原点（镜头）
        float wx = -(a.x() + c.x()) * 0.5F;
        float wy = -(a.y() + c.y()) * 0.5F;
        float wz = -(a.z() + c.z()) * 0.5F;
        float sx = dy * wz - dz * wy;
        float sy = dz * wx - dx * wz;
        float sz = dx * wy - dy * wx;
        float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (length < 1.0E-6F) {
            return;
        }
        float k = halfWidth / length;
        sx *= k;
        sy *= k;
        sz *= k;
        b.vertex(a.x() + sx, a.y() + sy, a.z() + sz).color(r, g, bl, alpha).endVertex();
        b.vertex(c.x() + sx, c.y() + sy, c.z() + sz).color(r, g, bl, alpha).endVertex();
        b.vertex(c.x() - sx, c.y() - sy, c.z() - sz).color(r, g, bl, alpha).endVertex();
        b.vertex(a.x() - sx, a.y() - sy, a.z() - sz).color(r, g, bl, alpha).endVertex();
    }
}
