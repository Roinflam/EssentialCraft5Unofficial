package com.flameforge.essentialcraft5.client.render.mob;

import com.flameforge.essentialcraft5.client.HologramClient;
import com.flameforge.essentialcraft5.client.render.HologramRenderer;
import com.flameforge.essentialcraft5.entity.mob.hoanna.InquisitoriumPatrollerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 审判庭巡卫的渲染器（第十三批 13d-N3，怪物稿 §2.4.7「零新贴图」）：全息投影的人形模型层（{@link HologramClient#HOLOGRAM_LAYER}）和
 * {@link HologramRenderer#TEXTURE}，同一种「发光的影子」混合（{@link HologramRenderer#ghost}），另外：
 * <ul>
 *   <li>整只缩到 {@value #SCALE}（再乘实体的 {@code getScale()}，精英大 1.15 倍）；</li>
 *   <li>染成青色（精英「审判庭执行机」染成金色，和普通的一眼分开）；腿越往下越淡，透明度只有身子的 {@value #LEG_FADE}；</li>
 *   <li>出手时（射破坏者、定标）两手举起。</li>
 * </ul>
 * 全息投影自己的渲染器、模型一行没动。只在客户端加载。
 */
public class InquisitoriumPatrollerRenderer extends MobRenderer<InquisitoriumPatrollerEntity, InquisitoriumPatrollerRenderer.PatrollerModel> {

    public static final float SCALE = 0.6F;
    /** 身子的透明度（全息投影是 0.2；缩小以后太淡，加一点） */
    public static final float ALPHA = 0.32F;
    /** 腿的透明度是身子的几成 */
    public static final float LEG_FADE = 0.4F;
    /** 青色调 */
    public static final float[] CYAN = {0.55F, 1F, 1F};
    /** 精英的金色调 */
    public static final float[] GOLD = {1F, 0.82F, 0.4F};

    public InquisitoriumPatrollerRenderer(EntityRendererProvider.Context context) {
        super(context, new PatrollerModel(context.bakeLayer(HologramClient.HOLOGRAM_LAYER)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(InquisitoriumPatrollerEntity entity) {
        return HologramRenderer.TEXTURE;
    }

    @Override
    protected @Nullable RenderType getRenderType(InquisitoriumPatrollerEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return bodyVisible || translucent ? HologramRenderer.ghost(HologramRenderer.TEXTURE) : super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    @Override
    protected void scale(InquisitoriumPatrollerEntity entity, PoseStack pose, float partialTick) {
        float scale = SCALE * entity.getScale();
        pose.scale(scale, scale, scale);
    }

    @Override
    public void render(InquisitoriumPatrollerEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model.tint = entity.isElite() ? GOLD : CYAN;
        shadowRadius = 0.3F * entity.getScale();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** 全息人形：按 {@link #tint} 染色、身子 {@link #ALPHA}、腿再淡；出手时举手 */
    public static final class PatrollerModel extends HumanoidModel<InquisitoriumPatrollerEntity> {

        float[] tint = CYAN;

        PatrollerModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(InquisitoriumPatrollerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                              float headPitch) {
            // 悬停的影子不迈步：腿只跟着慢慢飘
            super.setupAnim(entity, 0F, 0F, ageInTicks, netHeadYaw, headPitch);
            rightLeg.xRot = 0.15F + (float) Math.sin(ageInTicks * 0.08F) * 0.08F;
            leftLeg.xRot = 0.15F - (float) Math.sin(ageInTicks * 0.08F) * 0.08F;
            if (entity.isCasting()) {
                rightArm.xRot = -2.6F;
                leftArm.xRot = -2.6F;
                rightArm.zRot = 0.25F;
                leftArm.zRot = -0.25F;
            }
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue,
                                   float alpha) {
            float r = red * tint[0];
            float g = green * tint[1];
            float b = blue * tint[2];
            float body = alpha * ALPHA;
            head.render(pose, consumer, light, overlay, r, g, b, body);
            hat.render(pose, consumer, light, overlay, r, g, b, body);
            this.body.render(pose, consumer, light, overlay, r, g, b, body);
            rightArm.render(pose, consumer, light, overlay, r, g, b, body);
            leftArm.render(pose, consumer, light, overlay, r, g, b, body);
            rightLeg.render(pose, consumer, light, overlay, r, g, b, body * LEG_FADE);
            leftLeg.render(pose, consumer, light, overlay, r, g, b, body * LEG_FADE);
        }
    }
}
