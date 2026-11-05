package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.blockentity.CrystalFormerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * 水晶成型器顶上的 4 颗小水晶。对应原作 RenderCrystalFormer：元素水晶模型缩到 0.5、尖朝上，
 * 放在 (0.8, 1.1, 0.5)、(0.2, 1.1, 0.5)、(0.5, 1.1, 0.8)、(0.5, 1.1, 0.2)，贴中性水晶的贴图。
 * 原作没开混合（不透明），这里用不透明的 entityCutoutNoCull。
 */
public class CrystalFormerRenderer implements BlockEntityRenderer<CrystalFormerBlockEntity> {

    private static final float SCALE = 0.5F;
    private static final float[][] SPOTS = {{0.8F, 0.5F}, {0.2F, 0.5F}, {0.5F, 0.8F}, {0.5F, 0.2F}};
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(ElementalCrystalRenderer.NEUTRAL);

    private final ElementalCrystalModel model = new ElementalCrystalModel();

    public CrystalFormerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CrystalFormerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        for (float[] spot : SPOTS) {
            pose.pushPose();
            pose.translate(spot[0], 1.1F, spot[1]);
            pose.scale(SCALE, SCALE, SCALE);
            pose.mulPose(Axis.XP.rotationDegrees(180F));
            model.render(pose, consumer, light, overlay, 1F, 1F, 1F, 1F);
            pose.popPose();
        }
    }
}
