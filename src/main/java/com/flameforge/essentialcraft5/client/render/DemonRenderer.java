package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.DemonModel;
import com.flameforge.essentialcraft5.entity.DemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 恶魔的渲染（原作 RenderDemon）：人形模型放大 1.4 倍、影子 0.5，背后半透明的翅膀，外加发光的眼睛层。
 * <p>
 * 眼睛层（{@code demon_eyes.png}，同原版末影人的 {@link EyesLayer}）原作写在 1.7 的 {@code shouldRenderPass} 里，1.12 从不调用，所以原作里眼睛不发光；这里补上。
 * 不用 HumanoidMobRenderer：恶魔手里不拿东西、不戴头颅，那几层用不到。
 */
public class DemonRenderer extends MobRenderer<DemonEntity, DemonModel> {

    public static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/entity/demon.png");
    public static final ResourceLocation EYES = EssentialCraft5.id("textures/entity/demon_eyes.png");
    public static final ResourceLocation WINGS = EssentialCraft5.id("textures/entity/demon_wings.png");
    /** 原作 preRenderCallback */
    private static final float SCALE = 1.4F;
    private static final RenderType EYES_TYPE = RenderType.eyes(EYES);
    private static final RenderType WINGS_TYPE = RenderType.entityTranslucent(WINGS);

    public DemonRenderer(EntityRendererProvider.Context context) {
        super(context, new DemonModel(context.bakeLayer(DemonModel.LAYER)), 0.5F);
        addLayer(new WingsLayer(this));
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES_TYPE;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(DemonEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(DemonEntity entity, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    /** 翅膀：换翅膀贴图、半透明混合（原作 GL_SRC_ALPHA / GL_ONE_MINUS_SRC_ALPHA），受光照和受伤变红照常 */
    private static final class WingsLayer extends RenderLayer<DemonEntity, DemonModel> {

        WingsLayer(RenderLayerParent<DemonEntity, DemonModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, DemonEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            VertexConsumer consumer = buffers.getBuffer(WINGS_TYPE);
            getParentModel().wings.render(pose, consumer, light, LivingEntityRenderer.getOverlayCoords(entity, 0F));
        }
    }
}
