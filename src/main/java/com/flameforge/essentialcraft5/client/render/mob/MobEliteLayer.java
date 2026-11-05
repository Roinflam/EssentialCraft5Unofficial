package com.flameforge.essentialcraft5.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Mob;

/**
 * 精英层：{@link ECMobRenderer#isElite} 为真时，把 {@code <id>_elite.png}（只有精英纹路，其余透明）半透明叠在身上。
 * 受光照、受伤变红照常（同身体），所以精英纹路在暗处也是暗的，发光的部分另画进 {@code _glow.png}。隐身时不画。
 */
public class MobEliteLayer<T extends Mob, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private final ECMobRenderer<T, M> renderer;

    public MobEliteLayer(ECMobRenderer<T, M> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || !renderer.isElite(entity)) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(renderer.eliteTexture(entity)));
        getParentModel().renderToBuffer(pose, consumer, light, LivingEntityRenderer.getOverlayCoords(entity, 0F), 1F, 1F, 1F, 1F);
    }
}
