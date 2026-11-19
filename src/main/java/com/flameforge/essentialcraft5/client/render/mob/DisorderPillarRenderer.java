package com.flameforge.essentialcraft5.client.render.mob;

import com.flameforge.essentialcraft5.entity.boss.MatrixShadePillar;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 失序之柱的画法：预警的 1.5 秒里地上的圈由首领预警包画（{@code client/vfx/warn}，起手时服务端发），这里不画；
 * 炸开以后一根光柱（贴图右半边，十字两片）在 1 秒里淡掉；地上不再画圈（结算不标范围，落点交给 {@code FX_DISORDER} 的裂纹和柱脚电晕）。
 * 全亮、半透明，不受光照。贴图 {@code textures/entity/disorder_pillar/disorder_pillar.png}（64 × 32：左半 32 × 32 是圈，现在没用到；右半是柱子的竖纹；灰度，按顶点颜色染成品红）。
 * 客户端只按自己的 tickCount 算进度（实体加进客户端的那一刻和服务端差不了一两刻）。只在客户端加载。
 */
public class DisorderPillarRenderer extends EntityRenderer<MatrixShadePillar> {

    public static final ResourceLocation TEXTURE = MobTextures.base("disorder_pillar");
    private static final float HEIGHT = 4F;
    private static final int R = 0xE0, G = 0x40, B = 0xC0;

    public DisorderPillarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(MatrixShadePillar entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        float radius = MatrixShadePillar.RADIUS;
        pose.pushPose();
        if (age >= MatrixShadePillar.WARN) {
            float fade = 1F - Mth.clamp((age - MatrixShadePillar.WARN) / MatrixShadePillar.LINGER, 0F, 1F);
            int alpha = (int) (230 * fade);
            if (alpha > 4) {
                float half = radius * 0.6F;
                column(pose, buffer, half, alpha, 0F);
                column(pose, buffer, half, alpha, 90F);
            }
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** 竖着的一片光柱（宽 2·half、高 {@link #HEIGHT}），绕 Y 转 angle 度；贴图右半 */
    private static void column(PoseStack pose, VertexConsumer buffer, float half, int alpha, float angle) {
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vertex(buffer, m, n, -half, 0F, 0F, 0.5F, 1F, alpha, 0F, 0F, 1F);
        vertex(buffer, m, n, half, 0F, 0F, 1F, 1F, alpha, 0F, 0F, 1F);
        vertex(buffer, m, n, half, HEIGHT, 0F, 1F, 0F, alpha, 0F, 0F, 1F);
        vertex(buffer, m, n, -half, HEIGHT, 0F, 0.5F, 0F, alpha, 0F, 0F, 1F);
        pose.popPose();
    }

    private static void vertex(VertexConsumer buffer, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int alpha,
                               float nx, float ny, float nz) {
        buffer.vertex(m, x, y, z).color(R, G, B, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, nx, ny, nz).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(MatrixShadePillar entity) {
        return TEXTURE;
    }
}
