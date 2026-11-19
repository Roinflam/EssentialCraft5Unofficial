package com.flameforge.essentialcraft5.client.vfx;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.boss.BossFxClientConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 屏幕层的特效：
 * <ul>
 *   <li><b>场景拷贝</b>（{@link #captureScene}）：屏幕扭曲要采「画到这里为止的画面」，每帧要画扭曲时把主帧缓冲的颜色拷一份（一次 glBlitFramebuffer）；</li>
 *   <li><b>闪光</b>（{@link #flash}）：整屏一层淡淡的颜色，大招落地那一下用。护眼规矩同首领画面（{@code BossFxRules}）：峰值 ≤ {@value #MAX_FLASH}，
 *       全局至少隔 {@value #FLASH_INTERVAL} 刻一次，乘原版「屏幕扭曲效果」滑块；打开「隐藏闪电闪烁」时峰值只剩 1/3；
 *       客户端配置 {@code [boss_fx] screen_effects} 关掉就不画（同一个「整屏画面」开关）。</li>
 *   <li><b>冲击帧</b>（{@link #impact}）：重招落下那一下，世界画完、特效画之前整屏压一层近黑——背景一下子沉下去、特效照样亮，
 *       1 刻后跳回一半再 {@value #IMPACT_FADE} 刻淡掉，动漫招式那种「咔」的一下。峰值 ≤ {@value #MAX_IMPACT}，全局至少隔 {@value #IMPACT_INTERVAL} 刻，
 *       乘「屏幕扭曲效果」滑块、「隐藏闪电闪烁」时剩 1/3。</li>
 *   <li><b>速度线</b>（{@link #speedLines}）：从屏幕四周往落点冲的细线（集中线），{@value #SPEED_TICKS} 刻淡掉，每 2 刻换一套；落点在身后时往屏幕中心收。</li>
 * </ul>
 * 冲击帧、速度线都在世界阶段画：镜头前一张不测深度的面片（{@link VfxRenderTypes#SCREEN_SHADE} / {@link VfxRenderTypes#SCREEN_LINES}），
 * 所以 F1 藏了 HUD 也照样有；稳妥画法（光影包）下都不画（光影包接管世界阶段的着色，贴脸面片的表现说不准）。
 * 都按游戏刻计时（离线逐帧录制时也对得上），同样受「整屏画面」开关管。
 */
public final class VfxScreen {

    public static final float MAX_FLASH = 0.3F;
    public static final int FLASH_INTERVAL = 40;
    public static final float MAX_IMPACT = 0.5F;
    public static final int IMPACT_INTERVAL = 16;
    private static final int IMPACT_FADE = 7;
    private static final int SPEED_TICKS = 6;
    private static final int SPEED_LINES = 40;
    /** 冲击帧压的颜色（近黑，带一点源质紫，纯黑压下去发灰） */
    private static final int IMPACT_SHADE = 0x06030C;

    private static float impactPeak;
    private static int impactAge = Integer.MAX_VALUE / 2;
    private static long lastImpactTick = Long.MIN_VALUE / 2;

    private static float speedPeak;
    private static int speedAge = Integer.MAX_VALUE / 2;
    private static int speedColor = 0xFFFFFF;
    private static int speedSeed;
    private static Vec3 speedAt = Vec3.ZERO;
    /** 冲击帧面片、速度线贴在镜头前多远（格，比近裁剪面远一点） */
    private static final float SCREEN_DEPTH = 0.2F;
    private static final Vector4f CLIP = new Vector4f();

    @Nullable
    private static TextureTarget scene;
    private static int flashColor;
    private static float flashPeak;
    private static int flashTicks;
    private static int flashAge;
    private static long lastFlashTick = Long.MIN_VALUE / 2;

    private VfxScreen() {
    }

    // ------------------------------------------------------------------ 场景拷贝

    /** 把主帧缓冲的颜色拷进 {@link #sceneTextureId()}；只在渲染线程调 */
    static void captureScene() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (scene == null || scene.width != main.width || scene.height != main.height) {
            if (scene != null) {
                scene.destroyBuffers();
            }
            scene = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            scene.setFilterMode(GL11.GL_LINEAR);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, scene.width, scene.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(false);
    }

    static int sceneTextureId() {
        return scene == null ? 0 : scene.getColorTextureId();
    }

    // ------------------------------------------------------------------ 闪光

    /**
     * 整屏闪一下。
     *
     * @param rgb   颜色
     * @param peak  峰值不透明度（会被夹到 {@value #MAX_FLASH} 以内）
     * @param ticks 多少刻淡完
     */
    public static void flash(int rgb, float peak, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !BossFxClientConfig.screenEffects()) {
            return;
        }
        long now = mc.level.getGameTime();
        if (now - lastFlashTick < FLASH_INTERVAL) {
            return;
        }
        lastFlashTick = now;
        flashColor = rgb;
        flashPeak = Math.min(MAX_FLASH, peak);
        flashTicks = Math.max(1, ticks);
        flashAge = 0;
    }

    static void tick() {
        if (flashAge < flashTicks) {
            flashAge++;
        }
        if (impactAge <= IMPACT_FADE + 2) {
            impactAge++;
        }
        if (speedAge <= SPEED_TICKS) {
            speedAge++;
        }
    }

    static void clear() {
        flashAge = flashTicks = 0;
        lastFlashTick = Long.MIN_VALUE / 2;
        impactAge = speedAge = Integer.MAX_VALUE / 2;
        lastImpactTick = Long.MIN_VALUE / 2;
    }

    // ------------------------------------------------------------------ 冲击帧

    /** 冲击帧：strength 0~1（调用方已按远近衰减），峰值夹到 {@value #MAX_IMPACT} */
    public static void impact(float strength) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || strength <= 0.05F || !BossFxClientConfig.screenEffects()) {
            return;
        }
        long now = mc.level.getGameTime();
        if (now - lastImpactTick < IMPACT_INTERVAL) {
            return;
        }
        lastImpactTick = now;
        impactPeak = Math.min(MAX_IMPACT, strength * MAX_IMPACT);
        impactAge = 0;
    }

    /** 这一帧压多暗：前 2 刻满峰值，之后跳回一半、平方衰减到 0 */
    private static float impactAlpha(float partialTick) {
        if (impactAge > IMPACT_FADE + 2 || !BossFxClientConfig.screenEffects()) {
            return 0F;
        }
        // 满峰值留 2 刻：收包、推进刻数的先后不定，起手那一刻常常已经被推过去，留 2 刻保证至少画出 1 刻
        float t = impactAge + partialTick;
        float a;
        if (t < 2F) {
            a = impactPeak;
        } else {
            float f = 1F - Mth.clamp((t - 2F) / IMPACT_FADE, 0F, 1F);
            a = impactPeak * 0.5F * f * f;
        }
        Minecraft mc = Minecraft.getInstance();
        a *= mc.options.screenEffectScale().get().floatValue();
        if (mc.options.hideLightningFlash().get()) {
            a /= 3F;
        }
        return a;
    }

    /**
     * 世界阶段、特效画之前调：镜头前 {@value #SCREEN_DEPTH} 格贴一张盖满视野的近黑面片（{@link VfxRenderTypes#SCREEN_SHADE}，不测深度），
     * 调用方随后单独提交这一批，好让特效画在它上面。返回画了没有。
     */
    static boolean drawImpactShade(MultiBufferSource source, Matrix4f pose, Camera camera, float partialTick) {
        float a = impactAlpha(partialTick);
        if (a <= 0.004F) {
            return false;
        }
        Vector3f f = camera.getLookVector();
        Vector3f l = camera.getLeftVector();
        Vector3f u = camera.getUpVector();
        float d = SCREEN_DEPTH;
        float h = d * 12F;
        int c = Vfx.argb(IMPACT_SHADE, Mth.clamp(a, 0F, 1F));
        VertexConsumer vc = source.getBuffer(VfxRenderTypes.SCREEN_SHADE);
        for (int i = 0; i < 4; i++) {
            float sx = i == 0 || i == 3 ? -1F : 1F;
            float sy = i < 2 ? -1F : 1F;
            vc.vertex(pose, f.x() * d + (l.x() * sx + u.x() * sy) * h, f.y() * d + (l.y() * sx + u.y() * sy) * h,
                    f.z() * d + (l.z() * sx + u.z() * sy) * h).color(c).endVertex();
        }
        return true;
    }

    // ------------------------------------------------------------------ 速度线

    /** 速度线往世界坐标 at 收；strength 0~1（调用方已按远近衰减），rgb 偏一点结算色（芯色就好，线本身近白） */
    public static void speedLines(Vec3 at, int rgb, float strength) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || strength <= 0.05F || !BossFxClientConfig.screenEffects()) {
            return;
        }
        if (speedAge <= SPEED_TICKS && strength < speedPeak * (1F - speedAge / (float) SPEED_TICKS)) {
            return;
        }
        speedAt = at;
        speedColor = rgb & 0xFFFFFF;
        speedPeak = Mth.clamp(strength, 0F, 1F);
        speedSeed = mc.level.random.nextInt();
        speedAge = 0;
    }

    /**
     * 世界阶段、特效都画完以后调：在镜头前 {@value #SCREEN_DEPTH} 格的平面上画速度线（{@link VfxRenderTypes#SCREEN_LINES}，加色、不测深度），
     * F1 藏了 HUD 也照样有。落点按这一帧的投影矩阵投到屏幕上，在镜头身后就往屏幕中心收。返回画了没有。
     */
    static boolean drawSpeedLines(MultiBufferSource source, Matrix4f pose, Matrix4f projection, Camera camera, float partialTick) {
        if (speedAge > SPEED_TICKS || !BossFxClientConfig.screenEffects()) {
            return false;
        }
        float t = (speedAge + partialTick) / SPEED_TICKS;
        Minecraft mc = Minecraft.getInstance();
        float a = speedPeak * (float) Math.pow(Math.max(0F, 1F - t), 1.5D) * mc.options.screenEffectScale().get().floatValue();
        if (a <= 0.01F) {
            return false;
        }
        // 屏幕坐标用「屏高 = 2」的各向同性单位（x 乘了宽高比），镜头前 d 格处一个单位 = tanY × d 格
        float tanX = 1F / projection.m00();
        float tanY = 1F / projection.m11();
        float aspect = tanX / tanY;
        Vec3 cam = camera.getPosition();
        CLIP.set((float) (speedAt.x - cam.x), (float) (speedAt.y - cam.y), (float) (speedAt.z - cam.z), 1F);
        pose.transform(CLIP);
        projection.transform(CLIP);
        float cx = 0F;
        float cy = 0F;
        if (CLIP.w > 0.05F) {
            cx = Mth.clamp(CLIP.x / CLIP.w * aspect, -1.25F * aspect, 1.25F * aspect);
            cy = Mth.clamp(CLIP.y / CLIP.w, -1.25F, 1.25F);
        }
        float outer = 2.2F * (float) Math.sqrt(aspect * aspect + 1F) + Math.abs(cx) + Math.abs(cy);
        float px = 2F / Math.max(1, mc.getWindow().getHeight());
        int count = Math.max(12, Math.round(SPEED_LINES * Vfx.detail(speedAt)));
        RandomSource r = RandomSource.create(speedSeed + (speedAge >> 1) * 7919L);
        Vector3f f = camera.getLookVector();
        Vector3f l = camera.getLeftVector();
        Vector3f u = camera.getUpVector();
        float d = SCREEN_DEPTH;
        float k = tanY * d;
        int lr = (speedColor >> 16) & 255;
        int lg = (speedColor >> 8) & 255;
        int lb = speedColor & 255;
        VertexConsumer vc = source.getBuffer(VfxRenderTypes.SCREEN_LINES);
        for (int i = 0; i < count; i++) {
            float ang = (i + r.nextFloat() * 0.8F) * Mth.TWO_PI / count;
            float cos = Mth.cos(ang);
            float sin = Mth.sin(ang);
            // 内端停在离落点 0.22~0.5 个屏高的地方，外端出屏；外端宽 3~10 个屏幕像素
            float inner = 0.44F + 0.56F * r.nextFloat();
            float half = (1.5F + 3.5F * r.nextFloat()) * px;
            int la = Math.round(255F * Mth.clamp(a * (0.45F + 0.75F * r.nextFloat()), 0F, 1F));
            screenVertex(vc, pose, f, l, u, d, k, cx + cos * inner, cy + sin * inner).color(lr, lg, lb, 0).endVertex();
            screenVertex(vc, pose, f, l, u, d, k, cx + cos * outer - sin * half, cy + sin * outer + cos * half).color(lr, lg, lb, la).endVertex();
            screenVertex(vc, pose, f, l, u, d, k, cx + cos * outer + sin * half, cy + sin * outer - cos * half).color(lr, lg, lb, la).endVertex();
        }
        return true;
    }

    /** 屏幕上 (x, y)（各向同性单位）对应镜头前 d 格平面上的点：前 × d + 右 × x × k + 上 × y × k */
    private static VertexConsumer screenVertex(VertexConsumer vc, Matrix4f pose, Vector3f f, Vector3f l, Vector3f u, float d, float k, float x, float y) {
        return vc.vertex(pose, f.x() * d - l.x() * x * k + u.x() * y * k, f.y() * d - l.y() * x * k + u.y() * y * k,
                f.z() * d - l.z() * x * k + u.z() * y * k);
    }

    private static void renderFlash(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (flashAge >= flashTicks || !BossFxClientConfig.screenEffects()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float t = (flashAge + partialTick) / flashTicks;
        float a = flashPeak * (1F - t) * (1F - t) * mc.options.screenEffectScale().get().floatValue();
        if (mc.options.hideLightningFlash().get()) {
            a /= 3F;
        }
        if (a <= 0.004F) {
            return;
        }
        graphics.fill(0, 0, width, height, Vfx.argb(flashColor, Mth.clamp(a, 0F, 1F)));
    }

    /** 闪光叠层登记（模组总线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class VfxScreenRegistration {

        private VfxScreenRegistration() {
        }

        @SubscribeEvent
        public static void onVfxRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("vfx_flash", VfxScreen::renderFlash);
        }
    }
}
