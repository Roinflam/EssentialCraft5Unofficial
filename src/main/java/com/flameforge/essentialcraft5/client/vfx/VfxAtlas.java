package com.flameforge.essentialcraft5.client.vfx;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 稳妥画法用的预烘贴图：{@value #SIZE} × {@value #SIZE}，4 × 4 格，第 i 格是 {@link VfxMode} 第 i 种图案的静止画面。
 * 用自带着色器本身画出来（第一次要用时在渲染线程画一次，资源包重载后重画），所以和完整画法长得一样，只是不会动
 * （法阵不按时间转——稳妥画法里由 {@link VfxDraw} 转整块面片；描出进度、预警填充这类按参数变的取满值那一张）。
 * 自带着色器没编译成时退回一张在内存里算的柔光点贴图（每格都一样），至少看得到发光。
 */
public final class VfxAtlas {

    public static final int SIZE = 1024;
    public static final int GRID = 4;
    /** 每格往里收一点再取样，线性过滤不会采到隔壁格子 */
    private static final float INSET = 2F / (SIZE / (float) GRID);

    @Nullable
    private static TextureTarget baked;
    @Nullable
    private static DynamicTexture fallback;

    private VfxAtlas() {
    }

    /** 资源包重载（着色器重编）后作废，下次用时重画 */
    static void invalidate() {
        RenderSystem.recordRenderCall(() -> {
            if (baked != null) {
                baked.destroyBuffers();
                baked = null;
            }
        });
    }

    /** 贴图 id（还没画就现画一次）；只在渲染线程调 */
    public static int textureId() {
        if (baked != null) {
            return baked.getColorTextureId();
        }
        ShaderInstance shader = VfxShaders.vfx();
        if (shader != null) {
            try {
                bake(shader);
                return baked.getColorTextureId();
            } catch (RuntimeException e) {
                baked = null;
            }
        }
        if (fallback == null) {
            fallback = new DynamicTexture(softDots());
            fallback.setFilter(true, false);
        }
        return fallback.getId();
    }

    /** 贴图坐标：第 mode 格里的局部 (u, v)（0~1） */
    static float u(int mode, float local) {
        int cell = Math.floorMod(mode, GRID * GRID);
        return ((cell % GRID) + INSET + local * (1F - 2F * INSET)) / GRID;
    }

    /** 帧缓冲的贴图坐标从下往上数，烘焙时第 0 行画在最上面，所以这里倒过来 */
    static float v(int mode, float local) {
        int cell = Math.floorMod(mode, GRID * GRID);
        return 1F - ((cell / GRID) + INSET + local * (1F - 2F * INSET)) / GRID;
    }

    private static void bake(ShaderInstance shader) {
        TextureTarget target = new TextureTarget(SIZE, SIZE, false, Minecraft.ON_OSX);
        target.setClearColor(0F, 0F, 0F, 0F);
        target.clear(Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_LINEAR);
        target.bindWrite(true);

        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        float oldFogStart = RenderSystem.getShaderFogStart();
        float oldFogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0F, SIZE, SIZE, 0F, -100F, 100F), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        float cell = SIZE / (float) GRID;
        for (int mode = 0; mode < VfxMode.COUNT; mode++) {
            float x0 = (mode % GRID) * cell;
            float y0 = (mode / GRID) * cell;
            float x1 = x0 + cell;
            float y1 = y0 + cell;
            int param = defaultParam(mode);
            boolean strip = VfxMode.isStrip(mode);
            // 长条类：x 方向是长度 0~1，y 方向是横向 −1~1；圆面类两个方向都是 −1~1
            float ua = strip ? 0F : -1F;
            float ub = 1F;
            vertex(builder, x0, y1, ua, 1F, mode, param);
            vertex(builder, x1, y1, ub, 1F, mode, param);
            vertex(builder, x1, y0, ub, -1F, mode, param);
            vertex(builder, x0, y0, ua, -1F, mode, param);
        }
        BufferUploader.drawWithShader(builder.end());

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
        RenderSystem.setShaderFogStart(oldFogStart);
        RenderSystem.setShaderFogEnd(oldFogEnd);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        baked = target;
    }

    private static void vertex(BufferBuilder builder, float x, float y, float u, float v, int mode, int param) {
        builder.vertex(x, y, 0D).color(255, 255, 255, 255).uv(u, v).uv2(mode, param).endVertex();
    }

    /** 烘焙时每种图案取哪一张 */
    private static int defaultParam(int mode) {
        return switch (mode) {
            case VfxMode.FILL -> VfxMode.FULL;
            case VfxMode.MOLTEN -> 700;
            default -> 0;
        };
    }

    private static NativeImage softDots() {
        int size = 256;
        NativeImage image = new NativeImage(size, size, false);
        int cell = size / GRID;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float lx = ((x % cell) + 0.5F) / cell * 2F - 1F;
                float ly = ((y % cell) + 0.5F) / cell * 2F - 1F;
                float r = (float) Math.sqrt(lx * lx + ly * ly);
                float a = r >= 1F ? 0F : (float) Math.exp(-r * r * 5F) * (1F - r);
                int alpha = Math.min(255, Math.round(a * 255F));
                // NativeImage 是 ABGR
                image.setPixelRGBA(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        return image;
    }
}
