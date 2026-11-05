package com.flameforge.essentialcraft5.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Mob;

/**
 * 发光层（同原版末影人、蜘蛛眼睛的 {@code EyesLayer}）：{@code <id>_glow.png} 用 {@code RenderType.eyes} 全亮叠加，黑夜里也看得见。
 * eyes 是加色混合，强度 {@link ECMobRenderer#glowAlpha} 直接乘在 RGB 上（乘 alpha 没用）；隐身时不画。
 */
public class MobGlowLayer<T extends Mob, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private final ECMobRenderer<T, M> renderer;

    public MobGlowLayer(ECMobRenderer<T, M> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        float alpha = renderer.glowAlpha(entity, partialTick);
        if (!(alpha > 0.004F)) {
            return;
        }
        float a = Math.min(1F, alpha);
        int color = renderer.glowColor(entity);
        float r = ((color >> 16) & 0xFF) / 255F * a;
        float g = ((color >> 8) & 0xFF) / 255F * a;
        float b = (color & 0xFF) / 255F * a;
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(renderer.glowTexture(entity)));
        getParentModel().renderToBuffer(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, 1F);
    }
}
