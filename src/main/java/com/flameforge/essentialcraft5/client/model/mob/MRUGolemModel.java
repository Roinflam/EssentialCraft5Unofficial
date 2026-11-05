package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MRUGolemAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemEntity;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * 聚能魔像的模型（batch13.md §10「构装体族」，贴图 128 × 64）：石砌的躯干、胸口嵌一块发光的聚能核心、背上两根晶柱，长臂垂到膝盖，拳头是两块大石。
 * 部位层级：{@code waist}（腰）→ {@code body}（躯干）→ {@code head} / {@code core} / {@code crystal_left} / {@code crystal_right} /
 * {@code right_arm} → {@code right_fist}、{@code left_arm} → {@code left_fist}；腿 {@code right_leg} / {@code left_leg} 挂在根上。
 * 两条胳膊上各挂一块默认藏起来的肩甲（{@code right_shoulder} / {@code left_shoulder}），给以后复用这套模型的构装体（废城守望者）打开用。
 * 动画在 {@link MRUGolemAnimations}，按实体的 {@link MRUGolemEntity#anims} 播；走路摆臂摆腿、转头、核心微微起伏是程序算的（{@link #pose}）。
 */
public class MRUGolemModel extends ECAnimatedModel<MRUGolemEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("mru_golem"), "main");

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart core;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightShoulder;
    private final ModelPart leftShoulder;

    public MRUGolemModel(ModelPart root) {
        super(root);
        ModelPart waist = root.getChild("waist");
        this.body = waist.getChild("body");
        this.head = body.getChild("head");
        this.core = body.getChild("core");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.rightShoulder = rightArm.getChild("right_shoulder");
        this.leftShoulder = leftArm.getChild("left_shoulder");
        setShoulders(false);
    }

    /** 肩甲显不显示（聚能魔像不显示） */
    public void setShoulders(boolean visible) {
        rightShoulder.visible = visible;
        leftShoulder.visible = visible;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition waist = root.addOrReplaceChild("waist", CubeListBuilder.create()
                .texOffs(0, 18).addBox(-5F, -5F, -3.5F, 10F, 5F, 7F), PartPose.offset(0F, 10F, 0F));
        PartDefinition body = waist.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(36, 0).addBox(-8F, -13F, -4.5F, 16F, 13F, 9F), PartPose.offset(0F, -5F, 0F));
        body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -8F, -5.5F, 9F, 9F, 9F), PartPose.offset(0F, -13F, -1F));
        body.addOrReplaceChild("core", CubeListBuilder.create()
                .texOffs(114, 0).addBox(-2F, -2.5F, -1F, 4F, 5F, 2F), PartPose.offset(0F, -7F, -5F));
        body.addOrReplaceChild("crystal_left", CubeListBuilder.create()
                .texOffs(114, 7).addBox(-1.5F, -7F, -1.5F, 3F, 7F, 3F), PartPose.offsetAndRotation(4F, -11F, 3.5F, -0.35F, 0F, 0.3F));
        body.addOrReplaceChild("crystal_right", CubeListBuilder.create()
                .texOffs(114, 7).mirror().addBox(-1.5F, -7F, -1.5F, 3F, 7F, 3F), PartPose.offsetAndRotation(-4F, -11F, 3.5F, -0.35F, 0F, -0.3F));

        PartDefinition rightArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(86, 17).addBox(-5F, -1F, -2.5F, 5F, 11F, 5F), PartPose.offset(-8F, -11F, 0F));
        rightArm.addOrReplaceChild("right_fist", CubeListBuilder.create()
                .texOffs(86, 0).addBox(-3.5F, 0F, -3.5F, 7F, 10F, 7F), PartPose.offset(-2.5F, 10F, 0F));
        rightArm.addOrReplaceChild("right_shoulder", CubeListBuilder.create()
                .texOffs(36, 22).addBox(-6.5F, -3F, -4F, 8F, 5F, 8F, new CubeDeformation(0.1F)), PartPose.ZERO);
        PartDefinition leftArm = body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(86, 17).mirror().addBox(0F, -1F, -2.5F, 5F, 11F, 5F), PartPose.offset(8F, -11F, 0F));
        leftArm.addOrReplaceChild("left_fist", CubeListBuilder.create()
                .texOffs(86, 0).mirror().addBox(-3.5F, 0F, -3.5F, 7F, 10F, 7F), PartPose.offset(2.5F, 10F, 0F));
        leftArm.addOrReplaceChild("left_shoulder", CubeListBuilder.create()
                .texOffs(36, 35).addBox(-1.5F, -3F, -4F, 8F, 5F, 8F, new CubeDeformation(0.1F)), PartPose.ZERO);

        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 30).addBox(-3F, 0F, -3F, 6F, 14F, 6F), PartPose.offset(-3.5F, 10F, 0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 30).mirror().addBox(-3F, 0F, -3F, 6F, 14F, 6F), PartPose.offset(3.5F, 10F, 0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    protected void pose(MRUGolemEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.look(head, netHeadYaw, headPitch, 45F, 30F);
        // 原版铁傀儡的步频（0.6662），腿摆 30°、胳膊反着摆 20°
        MobPoses.swingPair(leftLeg, rightLeg, limbSwing, limbSwingAmount, 0.6662F, 30F);
        MobPoses.swingPair(rightArm, leftArm, limbSwing, limbSwingAmount, 0.6662F, 20F);
        // 躯干随步子轻轻左右晃，核心一直微微起伏
        body.zRot += Mth.cos(limbSwing * 0.6662F) * 2F * Mth.DEG_TO_RAD * limbSwingAmount;
        MobPoses.hover(core, ageInTicks, 40F, 0.25F);
    }

    @Override
    protected @Nullable MobAnimStates animStates(MRUGolemEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case MRUGolemEntity.ANIM_HAMMER -> MRUGolemAnimations.HAMMER;
            case MRUGolemEntity.ANIM_SLAM -> MRUGolemAnimations.SLAM;
            case MRUGolemEntity.ANIM_WHIP -> MRUGolemAnimations.WHIP;
            default -> null;
        };
    }
}
