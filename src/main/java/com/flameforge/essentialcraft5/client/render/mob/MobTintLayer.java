package com.flameforge.essentialcraft5.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Mob;

/**
 * 染色层：{@link ECMobRenderer#withTint()} 打开后身体由这里画，贴图乘上 {@link ECMobRenderer#tintColor}（同原版羊毛、热带鱼花纹的画法）。
 * 隐身时不画（隐身但旁观者看得见的那一遍由渲染器照原版画半透明、不染色）。
 */
public class MobTintLayer<T extends Mob, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private final ECMobRenderer<T, M> renderer;

    public MobTintLayer(ECMobRenderer<T, M> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        int rgb = renderer.tintColor(entity);
        float r = ((rgb >> 16) & 0xFF) / 255F;
        float g = ((rgb >> 8) & 0xFF) / 255F;
        float b = (rgb & 0xFF) / 255F;
        renderColoredCutoutModel(getParentModel(), renderer.getTextureLocation(entity), pose, buffers, light, entity, r, g, b);
    }
}
