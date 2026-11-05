package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.client.HologramClient;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.InquisitoriumSentinelEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 审判庭哨兵的渲染（第十批 10c-M1，batch10.md §5.10.5）：不另画贴图，全息投影的模型和贴图（{@link HologramRenderer#TEXTURE}）换色——
 * 本体乘暗紫 {@link #TINT}，外面再画一层放大 {@value #OUTLINE_SCALE} 倍的品红影子当描边（{@link #OUTLINE}）；混合、透明度、不写深度同全息投影
 * （{@link HologramRenderer#ghost}）。头顶照样转着当前招式的核心（{@link HologramRenderer#coreFor}，休息时是故障核心），
 * 有回声时，回声那一招的核心缩小一圈绕着它转。血条的紫色在服务端设（{@code InquisitoriumSentinelEntity} 构造器）。
 */
public class InquisitoriumSentinelRenderer extends MobRenderer<InquisitoriumSentinelEntity, HumanoidModel<InquisitoriumSentinelEntity>> {

    /** 本体的颜色乘数（暗紫） */
    public static final float[] TINT = {0.55F, 0.22F, 0.85F};
    /** 描边的颜色乘数（品红） */
    public static final float[] OUTLINE = {1F, 0.15F, 1F};
    /** 描边那层放大多少 */
    public static final float OUTLINE_SCALE = 1.08F;
    /** 回声核心离头顶核心多远、缩到多大 */
    public static final float ECHO_ORBIT = 0.55F;
    public static final float ECHO_SCALE = 0.6F;

    private final ItemRenderer itemRenderer;

    public InquisitoriumSentinelRenderer(EntityRendererProvider.Context context) {
        super(context, new SentinelModel(context.bakeLayer(HologramClient.HOLOGRAM_LAYER)), 0.5F);
        this.itemRenderer = context.getItemRenderer();
        addLayer(new OutlineLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(InquisitoriumSentinelEntity entity) {
        return HologramRenderer.TEXTURE;
    }

    @Override
    protected @Nullable RenderType getRenderType(InquisitoriumSentinelEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return bodyVisible || translucent ? HologramRenderer.ghost(HologramRenderer.TEXTURE) : super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    @Override
    public void render(InquisitoriumSentinelEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        float spin = (entity.tickCount + partialTick) % 360F;
        ItemStack core = HologramRenderer.coreFor(entity.attackId());
        pose.pushPose();
        pose.translate(0F, HologramRenderer.CORE_HEIGHT, 0F);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        itemRenderer.renderStatic(core, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                entity.level(), entity.getId());
        int echo = entity.echoAttack();
        if (echo != HologramEntity.REST) {
            // 回声的核心：绕着头顶的核心反着转（3 度 / 刻），小一圈
            pose.mulPose(Axis.YP.rotationDegrees(-spin * 4F));
            pose.translate(ECHO_ORBIT, 0F, 0F);
            pose.scale(ECHO_SCALE, ECHO_SCALE, ECHO_SCALE);
            itemRenderer.renderStatic(HologramRenderer.coreFor(echo), ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    pose, buffers, entity.level(), entity.getId() + 1);
        }
        pose.popPose();
    }

    /** 人形模型：本体乘暗紫、透明度乘全息投影的 {@link HologramRenderer#ALPHA}；描边层走 {@link #renderTinted} 自己给颜色 */
    static final class SentinelModel extends HumanoidModel<InquisitoriumSentinelEntity> {

        SentinelModel(ModelPart root) {
            super(root);
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue,
                                   float alpha) {
            renderTinted(pose, consumer, light, overlay, TINT, alpha);
        }

        void renderTinted(PoseStack pose, VertexConsumer consumer, int light, int overlay, float[] tint, float alpha) {
            super.renderToBuffer(pose, consumer, light, overlay, tint[0], tint[1], tint[2], alpha * HologramRenderer.ALPHA);
        }
    }

    /** 品红描边：同一个模型放大一点再画一遍（姿势、动作和本体一样，层在本体之后画） */
    static final class OutlineLayer extends RenderLayer<InquisitoriumSentinelEntity, HumanoidModel<InquisitoriumSentinelEntity>> {

        OutlineLayer(RenderLayerParent<InquisitoriumSentinelEntity, HumanoidModel<InquisitoriumSentinelEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, InquisitoriumSentinelEntity entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible() || !(getParentModel() instanceof SentinelModel model)) {
                return;
            }
            pose.pushPose();
            // 模型空间里 y 向下、身高 1.5 格，绕身体中间放大
            pose.translate(0F, 0.75F, 0F);
            pose.scale(OUTLINE_SCALE, OUTLINE_SCALE, OUTLINE_SCALE);
            pose.translate(0F, -0.75F, 0F);
            model.renderTinted(pose, buffers.getBuffer(HologramRenderer.ghost(HologramRenderer.TEXTURE)), light, OverlayTexture.NO_OVERLAY, OUTLINE, 0.6F);
            pose.popPose();
        }
    }
}
