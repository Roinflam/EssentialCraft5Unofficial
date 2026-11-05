package com.flameforge.essentialcraft5.client.render.mob;

import com.flameforge.essentialcraft5.client.model.mob.EmberSpiritModel;
import com.flameforge.essentialcraft5.entity.mob.hoanna.EmberSpiritEntity;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
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
 * 余烬灵（13d-N2）：一张灰度贴图，身体按这只的余烬整只染色（{@link EmberSpiritEntity#color}，照 13c 元素游灵的 {@code withTint}）；
 * 发光层同样着色、慢慢呼吸——精英「余烬双生」的发光层换成第二种余烬的颜色，远处就看得出它带两种。再叠精英层。
 * 余烬本身发光，身体至少按 {@value #MIN_BLOCK_LIGHT} 级方块光画。
 */
public class EmberSpiritRenderer extends ECMobRenderer<EmberSpiritEntity, EmberSpiritModel> {

    public static final String ID = "ember_spirit";
    /** 身体最低按多少级方块光画 */
    private static final int MIN_BLOCK_LIGHT = 12;

    public EmberSpiritRenderer(EntityRendererProvider.Context context) {
        super(context, new EmberSpiritModel(context.bakeLayer(EmberSpiritModel.LAYER)), 0.3F, ID);
        withTint();
        addLayer(new EmberGlowLayer(this));
        withElite();
    }

    @Override
    public int tintColor(EmberSpiritEntity entity) {
        return EmberSpiritEntity.color(entity.getEmber());
    }

    /** 发光层的颜色：精英用第二种余烬，普通的同身体 */
    @Override
    public int glowColor(EmberSpiritEntity entity) {
        EmberKind second = entity.getSecondEmber();
        return EmberSpiritEntity.color(second != null ? second : entity.getEmber());
    }

    @Override
    public float glowAlpha(EmberSpiritEntity entity, float partialTick) {
        return 0.7F + 0.3F * Mth.sin((entity.tickCount + partialTick) * 0.2F);
    }

    @Override
    protected int getBlockLightLevel(EmberSpiritEntity entity, BlockPos pos) {
        return Math.max(MIN_BLOCK_LIGHT, super.getBlockLightLevel(entity, pos));
    }

    /** 按余烬着色的发光层（同 {@link MobGlowLayer}，RGB 再乘余烬色） */
    private static final class EmberGlowLayer extends RenderLayer<EmberSpiritEntity, EmberSpiritModel> {

        private final EmberSpiritRenderer renderer;

        EmberGlowLayer(EmberSpiritRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, EmberSpiritEntity entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            float a = Math.min(1F, renderer.glowAlpha(entity, partialTick));
            if (!(a > 0.004F)) {
                return;
            }
            int rgb = renderer.glowColor(entity);
            float r = ((rgb >> 16) & 0xFF) / 255F * a;
            float g = ((rgb >> 8) & 0xFF) / 255F * a;
            float b = (rgb & 0xFF) / 255F * a;
            VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(renderer.glowTexture(entity)));
            getParentModel().renderToBuffer(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, 1F);
        }
    }
}
