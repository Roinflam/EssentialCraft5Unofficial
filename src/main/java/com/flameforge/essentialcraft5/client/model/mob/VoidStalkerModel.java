package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.client.model.mob.anim.VoidStalkerAnimations;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.VoidStalkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * 虚空潜行者的模型（第十三批 13c-M3；贴图 64 × 64，UV 底图 tools/gen/mob_uv/void_stalker.png）。
 * 比末影人矮一点、弓着背的瘦高个，两条长胳膊垂到膝盖、手上一副大爪子，背后浮着上下两块碎晶（慢慢转）。
 * <p>
 * 相位时整只变半透明：不透明度在 {@link #prepareMobModel} 里取 {@link VoidStalkerEntity#phaseAlpha}，
 * {@link #renderToBuffer} 把它乘进去，所以精英层也跟着淡；渲染器在不透明度不到 1 时换成半透明的 RenderType。
 * 部位名是常量（关键帧按名字找，写错不报错）。UV 布局定了就别再改：正式贴图照它画。
 */
public class VoidStalkerModel extends ECAnimatedModel<VoidStalkerEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("void_stalker"), "main");

    public static final String BODY = "body";
    public static final String HEAD = "head";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_ARM = "left_arm";
    public static final String RIGHT_CLAW = "right_claw";
    public static final String LEFT_CLAW = "left_claw";
    public static final String RIGHT_LEG = "right_leg";
    public static final String LEFT_LEG = "left_leg";
    public static final String SHARD_UPPER = "shard_upper";
    public static final String SHARD_LOWER = "shard_lower";

    /** 弓背的角度（弧度）；头往回抬同样的角，脸还是朝前 */
    static final float HUNCH = 0.25F;

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart shardUpper;
    private final ModelPart shardLower;
    private float alpha = 1F;

    public VoidStalkerModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild(BODY);
        this.head = body.getChild(HEAD);
        this.rightArm = body.getChild(RIGHT_ARM);
        this.leftArm = body.getChild(LEFT_ARM);
        this.shardUpper = body.getChild(SHARD_UPPER);
        this.shardLower = body.getChild(SHARD_LOWER);
        this.rightLeg = root.getChild(RIGHT_LEG);
        this.leftLeg = root.getChild(LEFT_LEG);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild(BODY, CubeListBuilder.create().texOffs(28, 0)
                .addBox(-4F, -12F, -2F, 8F, 12F, 4F), PartPose.offsetAndRotation(0F, 10F, 0F, HUNCH, 0F, 0F));
        body.addOrReplaceChild(HEAD, CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -9F, -3.5F, 7F, 9F, 7F), PartPose.offsetAndRotation(0F, -12F, -0.5F, -HUNCH, 0F, 0F));
        PartDefinition rightArm = body.addOrReplaceChild(RIGHT_ARM, CubeListBuilder.create().texOffs(12, 16)
                .addBox(-1.5F, -1F, -1.5F, 3F, 16F, 3F), PartPose.offsetAndRotation(-5.5F, -11F, 0F, -HUNCH, 0F, 0.08F));
        rightArm.addOrReplaceChild(RIGHT_CLAW, CubeListBuilder.create().texOffs(24, 16)
                .addBox(-2F, 0F, -2F, 4F, 5F, 4F), PartPose.offset(0F, 14F, 0F));
        PartDefinition leftArm = body.addOrReplaceChild(LEFT_ARM, CubeListBuilder.create().texOffs(12, 16).mirror()
                .addBox(-1.5F, -1F, -1.5F, 3F, 16F, 3F), PartPose.offsetAndRotation(5.5F, -11F, 0F, -HUNCH, 0F, -0.08F));
        leftArm.addOrReplaceChild(LEFT_CLAW, CubeListBuilder.create().texOffs(24, 16).mirror()
                .addBox(-2F, 0F, -2F, 4F, 5F, 4F), PartPose.offset(0F, 14F, 0F));
        root.addOrReplaceChild(RIGHT_LEG, CubeListBuilder.create().texOffs(0, 16)
                .addBox(-1.5F, 0F, -1.5F, 3F, 14F, 3F), PartPose.offset(-2F, 10F, 0F));
        root.addOrReplaceChild(LEFT_LEG, CubeListBuilder.create().texOffs(0, 16).mirror()
                .addBox(-1.5F, 0F, -1.5F, 3F, 14F, 3F), PartPose.offset(2F, 10F, 0F));
        body.addOrReplaceChild(SHARD_UPPER, CubeListBuilder.create().texOffs(40, 16)
                .addBox(-1F, -2F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(-1F, -9F, 5F, 0F, 0F, 0.35F));
        body.addOrReplaceChild(SHARD_LOWER, CubeListBuilder.create().texOffs(48, 16)
                .addBox(-1F, -2F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(1.5F, -3.5F, 4.5F, 0F, 0F, -0.3F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void prepareMobModel(VoidStalkerEntity entity, float limbSwing, float limbSwingAmount, float partialTick) {
        super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        float a = entity.phaseAlpha(partialTick);
        this.alpha = a > 0F && a <= 1F ? a : 1F;
    }

    /** 这一帧的不透明度（0~1，渲染器据此换半透明的 RenderType） */
    public float alpha() {
        return alpha;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue, float alpha) {
        super.renderToBuffer(pose, consumer, light, overlay, red, green, blue, alpha * this.alpha);
    }

    /** 常驻姿势：转头、迈腿、胳膊跟着荡（幅度小，像拖着走）、背后碎晶慢慢转、上下浮 */
    @Override
    protected void pose(VoidStalkerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.look(head, netHeadYaw, headPitch, 50F, 30F);
        MobPoses.swingPair(leftLeg, rightLeg, limbSwing, limbSwingAmount, 0.6662F, 40F);
        MobPoses.swingPair(rightArm, leftArm, limbSwing, limbSwingAmount, 0.6662F, 18F);
        float idle = Mth.sin(ageInTicks * 0.06F) * 0.05F;
        rightArm.zRot += idle;
        leftArm.zRot -= idle;
        MobPoses.spin(shardUpper, ageInTicks, 3F);
        MobPoses.spin(shardLower, ageInTicks, -4F);
        MobPoses.hover(shardUpper, ageInTicks, 50F, 0.6F);
        MobPoses.hover(shardLower, ageInTicks + 25F, 50F, 0.6F);
    }

    @Override
    protected @Nullable MobAnimStates animStates(VoidStalkerEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case VoidStalkerEntity.ANIM_CLAW -> VoidStalkerAnimations.CLAW;
            case VoidStalkerEntity.ANIM_RIFT_CLAW -> VoidStalkerAnimations.RIFT_CLAW;
            case VoidStalkerEntity.ANIM_PHASE -> VoidStalkerAnimations.PHASE;
            case VoidStalkerEntity.ANIM_PULL -> VoidStalkerAnimations.PULL;
            default -> null;
        };
    }
}
