package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * 二分场的品红球（原作 {@code RenderDivider}）：原作载入的 {@code sphere.obj} 就是反应堆浮球那个模型（{@link ReactorRenderer#SPHERE}，
 * 半径 1、底贴 y = 0，反应堆模块已登记成额外模型），这里往下挪 3 格再放大 3 倍，球心落在实体位置、半径 3（= 生效半径）。
 * 原作关了光照、颜色 (1, 0, 1, 0.2)，正反各画一遍好让球里的人也看得见；这里用 {@link RenderType#entityTranslucent}：
 * 不剔背面、上传时按远近排序，一遍就内外都看得见，亮度给满（原作不吃环境亮度）。
 */
public class DividerFieldRenderer extends EntityRenderer<DividerFieldEntity> {

    private static final float SCALE = (float) DividerFieldEntity.RADIUS;
    private static final float RED = 1F;
    private static final float GREEN = 0F;
    private static final float BLUE = 1F;
    private static final float ALPHA = 0.2F;

    private final RandomSource random = RandomSource.create();

    public DividerFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(DividerFieldEntity field, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(ReactorRenderer.SPHERE);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        pose.pushPose();
        pose.translate(0D, -SCALE, 0D);
        pose.scale(SCALE, SCALE, SCALE);
        PoseStack.Pose last = pose.last();
        for (Direction side : Direction.values()) {
            draw(model, side, last, consumer);
        }
        draw(model, null, last, consumer);
        pose.popPose();
        super.render(field, yaw, partialTick, pose, buffers, packedLight);
    }

    private void draw(BakedModel model, @Nullable Direction side, PoseStack.Pose pose, VertexConsumer consumer) {
        random.setSeed(42L);
        for (BakedQuad quad : model.getQuads(null, side, random, ModelData.EMPTY, null)) {
            // 不读模型自带的顶点色，颜色只由这里给
            consumer.putBulkData(pose, quad, RED, GREEN, BLUE, ALPHA, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(DividerFieldEntity field) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
