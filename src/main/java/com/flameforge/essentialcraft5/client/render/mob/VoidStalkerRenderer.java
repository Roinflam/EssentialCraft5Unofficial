package com.flameforge.essentialcraft5.client.render.mob;

import com.flameforge.essentialcraft5.client.model.mob.VoidStalkerModel;
import com.flameforge.essentialcraft5.entity.mob.VoidStalkerEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;

/**
 * 虚空潜行者的渲染器（第十三批 13c-M3）：在 {@link ECMobRenderer} 上加「相位半透明」——
 * 模型这一帧的不透明度不到 1（预警闪烁、相位中、进出相位的淡入淡出）时身体换成半透明的 RenderType，
 * 发光层（眼睛、碎晶）跟着变暗但留一点，相位时还看得见它在哪。
 */
public class VoidStalkerRenderer extends ECMobRenderer<VoidStalkerEntity, VoidStalkerModel> {

    /** 相位时发光层最暗到多少 */
    static final float MIN_GLOW = 0.45F;

    public VoidStalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new VoidStalkerModel(context.bakeLayer(VoidStalkerModel.LAYER)), 0.6F, "void_stalker");
        withGlow();
        withElite();
    }

    @Override
    protected @Nullable RenderType getRenderType(VoidStalkerEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (bodyVisible && !translucent && getModel().alpha() < 1F) {
            return RenderType.entityTranslucent(getTextureLocation(entity));
        }
        return super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    @Override
    public float glowAlpha(VoidStalkerEntity entity, float partialTick) {
        return Math.max(MIN_GLOW, entity.phaseAlpha(partialTick));
    }
}
