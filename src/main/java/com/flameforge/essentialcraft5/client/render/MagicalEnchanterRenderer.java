package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.blockentity.MagicalEnchanterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.util.Mth;

/**
 * 魔力附魔台上方翻页的书。对应原作 RenderMagicalEnchanter（就是原版附魔台的书，高度 0.75 + 0.1，上下浮动 0.01）。
 * 照原版 EnchantTableRenderer 画，动画数值由方块实体的 clientTick 算（只在客户端）。
 */
public class MagicalEnchanterRenderer implements BlockEntityRenderer<MagicalEnchanterBlockEntity> {

    private final BookModel bookModel;

    public MagicalEnchanterRenderer(BlockEntityRendererProvider.Context context) {
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(MagicalEnchanterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        pose.pushPose();
        pose.translate(0.5F, 0.75F, 0.5F);
        float time = be.time + partialTick;
        pose.translate(0F, 0.1F + Mth.sin(time * 0.1F) * 0.01F, 0F);
        float delta = be.rot - be.oRot;
        while (delta >= (float) Math.PI) {
            delta -= (float) Math.PI * 2F;
        }
        while (delta < -(float) Math.PI) {
            delta += (float) Math.PI * 2F;
        }
        float rotation = be.oRot + delta * partialTick;
        pose.mulPose(Axis.YP.rotation(-rotation));
        pose.mulPose(Axis.ZP.rotationDegrees(80F));
        float flip = Mth.lerp(partialTick, be.oFlip, be.flip);
        float right = Mth.frac(flip + 0.25F) * 1.6F - 0.3F;
        float left = Mth.frac(flip + 0.75F) * 1.6F - 0.3F;
        float open = Mth.lerp(partialTick, be.oOpen, be.open);
        bookModel.setupAnim(time, Mth.clamp(right, 0F, 1F), Mth.clamp(left, 0F, 1F), open);
        VertexConsumer consumer = EnchantTableRenderer.BOOK_LOCATION.buffer(buffers, RenderType::entitySolid);
        bookModel.render(pose, consumer, packedLight, packedOverlay, 1F, 1F, 1F, 1F);
        pose.popPose();
    }
}
