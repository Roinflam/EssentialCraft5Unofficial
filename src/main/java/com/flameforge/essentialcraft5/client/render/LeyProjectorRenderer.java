package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 地脉投影仪「正在投影」时从机器里往下打的一根 MRU 光束（照原作 RenderMagicalQuarry 的外形）：
 * 8 边形细管，半径 0.075（原作 0.75 × 0.1），贴图 {@code special/mru_beam.png}，V 沿长度每 32 格平铺一次、随时间以每 tick 0.1 滚动；
 * 原作从机器 (0.5, 0.3, 0.5) 连到正在挖的那一格，这里没有挖的格子，改成竖直往下 {@link LeyProjectorBlockEntity#BEAM_LENGTH} 格（不低于世界底部），
 * 钻进地下的部分被地面挡住，看到的是机器玻璃壳里、以及下面有空洞处的那一段。
 * <p>
 * 原作关了剔除、加色混合（SRC_ALPHA, ONE）、白色：这里用 {@link RenderType#eyes}（加色、全亮、不写深度），正反两面都画。
 * 渲染盒有限（方块实体的 getRenderBoundingBox，修原作 isGlobalRenderer 到哪都画）；不在投影时什么都不画。
 */
public class LeyProjectorRenderer implements BlockEntityRenderer<LeyProjectorBlockEntity> {

    public static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/special/mru_beam.png");
    private static final RenderType RENDER_TYPE = RenderType.eyes(TEXTURE);
    private static final int SIDES = 8;
    private static final float RADIUS = 0.075F;
    private static final float START_Y = 0.3F;

    public LeyProjectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LeyProjectorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !be.isWorking()) {
            return;
        }
        renderBeam(level, be.getBlockPos().getY(), LeyProjectorBlockEntity.BEAM_LENGTH, partialTick, pose, buffers);
    }

    /**
     * 从方块里 (0.5, {@value #START_Y}, 0.5) 竖直往下画一根 length 格的光束（不低于世界底部），pose 在方块的原点。
     * 地脉凝聚器（第九批）工作时画同一根。
     */
    public static void renderBeam(Level level, int y, int length, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        float bottom = Math.max(level.getMinBuildHeight(), y - length) - y;
        float beamLength = START_Y - bottom;
        if (beamLength <= 0F) {
            return;
        }
        // 先在 long 上取模再加 partialTick，游戏时间大了也不会一顿一顿
        float scroll = (level.getGameTime() % 1000L + partialTick) * 0.1F;
        float vTop = -scroll;
        float vBottom = vTop + beamLength / 32F;
        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        pose.pushPose();
        pose.translate(0.5F, 0F, 0.5F);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        for (int i = 0; i < SIDES; i++) {
            float a0 = i * Mth.TWO_PI / SIDES;
            float a1 = (i + 1) * Mth.TWO_PI / SIDES;
            float x0 = Mth.sin(a0) * RADIUS;
            float z0 = Mth.cos(a0) * RADIUS;
            float x1 = Mth.sin(a1) * RADIUS;
            float z1 = Mth.cos(a1) * RADIUS;
            float u0 = (float) i / SIDES;
            float u1 = (float) (i + 1) / SIDES;
            float nx = Mth.sin((a0 + a1) / 2F);
            float nz = Mth.cos((a0 + a1) / 2F);
            // 外面
            vertex(consumer, matrix, normal, x0, START_Y, z0, u0, vTop, nx, nz);
            vertex(consumer, matrix, normal, x0, bottom, z0, u0, vBottom, nx, nz);
            vertex(consumer, matrix, normal, x1, bottom, z1, u1, vBottom, nx, nz);
            vertex(consumer, matrix, normal, x1, START_Y, z1, u1, vTop, nx, nz);
            // 里面（原作关了剔除，两面都亮）
            vertex(consumer, matrix, normal, x1, START_Y, z1, u1, vTop, -nx, -nz);
            vertex(consumer, matrix, normal, x1, bottom, z1, u1, vBottom, -nx, -nz);
            vertex(consumer, matrix, normal, x0, bottom, z0, u0, vBottom, -nx, -nz);
            vertex(consumer, matrix, normal, x0, START_Y, z0, u0, vTop, -nx, -nz);
        }
        pose.popPose();
    }

    /** eyes 的顶点格式是 NEW_ENTITY（位置、颜色、UV、覆盖层、光照、法线） */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float y, float z,
                               float u, float v, float nx, float nz) {
        consumer.vertex(matrix, x, y, z)
                .color(1F, 1F, 1F, 1F)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, nx, 0F, nz)
                .endVertex();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
