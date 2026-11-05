package com.flameforge.essentialcraft5.client.render.mob;

import com.flameforge.essentialcraft5.client.model.mob.ElementalWispModel;
import com.flameforge.essentialcraft5.entity.mob.ElementalWispEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * 元素游灵（13c-M1）：一张灰度贴图，整只按元素染色（{@link ElementalWispEntity.Element#color()}）；发光层同样按元素着色、慢慢呼吸，
 * 精英「元素聚灵」再叠精英层。地底下黑，身体至少按 10 级方块光画，远处也认得出是什么颜色。
 */
public class ElementalWispRenderer extends ECMobRenderer<ElementalWispEntity, ElementalWispModel> {

    public static final String ID = "elemental_wisp";
    /** 身体最低按多少级方块光画 */
    private static final int MIN_BLOCK_LIGHT = 10;

    public ElementalWispRenderer(EntityRendererProvider.Context context) {
        super(context, new ElementalWispModel(context.bakeLayer(ElementalWispModel.LAYER)), 0.3F, ID);
        withTint();
        addLayer(new ElementGlowLayer(this));
        withElite();
    }

    @Override
    public int tintColor(ElementalWispEntity entity) {
        return entity.getElement().color();
    }

    @Override
    public float glowAlpha(ElementalWispEntity entity, float partialTick) {
        return 0.75F + 0.25F * Mth.sin((entity.tickCount + partialTick) * 0.15F);
    }

    @Override
    protected int getBlockLightLevel(ElementalWispEntity entity, BlockPos pos) {
        return Math.max(MIN_BLOCK_LIGHT, super.getBlockLightLevel(entity, pos));
    }

    /** 按元素着色的发光层（同 {@link MobGlowLayer}，RGB 再乘元素色） */
    private static final class ElementGlowLayer extends RenderLayer<ElementalWispEntity, ElementalWispModel> {

        private final ElementalWispRenderer renderer;

        ElementGlowLayer(ElementalWispRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, ElementalWispEntity entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            float a = Math.min(1F, renderer.glowAlpha(entity, partialTick));
            if (!(a > 0.004F)) {
                return;
            }
            int rgb = renderer.tintColor(entity);
            float r = ((rgb >> 16) & 0xFF) / 255F * a;
            float g = ((rgb >> 8) & 0xFF) / 255F * a;
            float b = (rgb & 0xFF) / 255F * a;
            VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(renderer.glowTexture(entity)));
            getParentModel().renderToBuffer(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, 1F);
        }
    }
}
