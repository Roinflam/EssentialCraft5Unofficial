package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.CatacombRevenantAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.hoanna.CatacombRevenantEntity;
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
 * 墓穴守灵的模型（第十三批 13d-N3；贴图 64 × 64，UV 底图用 tools/gen/uv_template.py 按本文件出）。
 * 原版骷髅的细骨架（头、躯干、细胳膊细腿），外面套一层锈蚀板甲（头盔、胸甲、两块肩甲，外扩一点、和身体共用一张贴图），
 * 右手握一把长剑（剑身 + 护手，是模型的一部分，不是手持物品），肋间一团灵火（发光层画亮）。
 * 部位名是常量（关键帧按名字找，写错不报错）。UV 布局定了就别再改：正式贴图照它画。
 */
public class CatacombRevenantModel extends ECAnimatedModel<CatacombRevenantEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("catacomb_revenant"), "main");

    public static final String HEAD = "head";
    public static final String HELMET = "helmet";
    public static final String BODY = "body";
    public static final String CHESTPLATE = "chestplate";
    public static final String SOULFIRE = "soulfire";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_ARM = "left_arm";
    public static final String RIGHT_PAULDRON = "right_pauldron";
    public static final String LEFT_PAULDRON = "left_pauldron";
    public static final String SWORD = "sword";
    public static final String GUARD = "guard";
    public static final String RIGHT_LEG = "right_leg";
    public static final String LEFT_LEG = "left_leg";

    /** 握剑的右臂平时往前抬的角（弧度） */
    static final float SWORD_ARM_PITCH = -0.45F;

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart soulfire;

    public CatacombRevenantModel(ModelPart root) {
        super(root);
        this.head = root.getChild(HEAD);
        this.rightArm = root.getChild(RIGHT_ARM);
        this.leftArm = root.getChild(LEFT_ARM);
        this.rightLeg = root.getChild(RIGHT_LEG);
        this.leftLeg = root.getChild(LEFT_LEG);
        this.soulfire = root.getChild(BODY).getChild(SOULFIRE);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild(HEAD, CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4F, -8F, -4F, 8F, 8F, 8F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild(HELMET, CubeListBuilder.create().texOffs(32, 0)
                .addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(0.5F)), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild(BODY, CubeListBuilder.create().texOffs(16, 16)
                .addBox(-4F, 0F, -2F, 8F, 12F, 4F), PartPose.offset(0F, 0F, 0F));
        body.addOrReplaceChild(CHESTPLATE, CubeListBuilder.create().texOffs(16, 32)
                .addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.4F)), PartPose.ZERO);
        body.addOrReplaceChild(SOULFIRE, CubeListBuilder.create().texOffs(40, 48)
                .addBox(-2F, 3F, -1F, 4F, 4F, 2F), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild(RIGHT_ARM, CubeListBuilder.create().texOffs(40, 16)
                .addBox(-1F, -2F, -1F, 2F, 12F, 2F), PartPose.offsetAndRotation(-5F, 2F, 0F, SWORD_ARM_PITCH, 0F, 0F));
        rightArm.addOrReplaceChild(RIGHT_PAULDRON, CubeListBuilder.create().texOffs(40, 32)
                .addBox(-2.5F, -2.5F, -2F, 4F, 4F, 4F), PartPose.ZERO);
        rightArm.addOrReplaceChild(SWORD, CubeListBuilder.create().texOffs(0, 48)
                .addBox(-0.5F, 8.5F, -14F, 1F, 2F, 13F), PartPose.ZERO);
        rightArm.addOrReplaceChild(GUARD, CubeListBuilder.create().texOffs(28, 48)
                .addBox(-1.5F, 8F, -2F, 3F, 3F, 1F), PartPose.ZERO);
        PartDefinition leftArm = root.addOrReplaceChild(LEFT_ARM, CubeListBuilder.create().texOffs(40, 16).mirror()
                .addBox(-1F, -2F, -1F, 2F, 12F, 2F), PartPose.offset(5F, 2F, 0F));
        leftArm.addOrReplaceChild(LEFT_PAULDRON, CubeListBuilder.create().texOffs(40, 32).mirror()
                .addBox(-1.5F, -2.5F, -2F, 4F, 4F, 4F), PartPose.ZERO);
        root.addOrReplaceChild(RIGHT_LEG, CubeListBuilder.create().texOffs(0, 16)
                .addBox(-1F, 0F, -1F, 2F, 12F, 2F), PartPose.offset(-2F, 12F, 0F));
        root.addOrReplaceChild(LEFT_LEG, CubeListBuilder.create().texOffs(0, 16).mirror()
                .addBox(-1F, 0F, -1F, 2F, 12F, 2F), PartPose.offset(2F, 12F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** 常驻姿势：转头、走路摆腿摆左臂（右臂握剑只轻摆）、灵火一胀一缩 */
    @Override
    protected void pose(CatacombRevenantEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.look(head, netHeadYaw, headPitch, 45F, 30F);
        MobPoses.swingPair(leftLeg, rightLeg, limbSwing, limbSwingAmount, 0.6662F, 60F);
        leftArm.xRot += Mth.cos(limbSwing * 0.6662F + Mth.PI) * 0.8F * limbSwingAmount;
        rightArm.xRot += Mth.cos(limbSwing * 0.6662F) * 0.25F * limbSwingAmount;
        leftArm.zRot -= 0.05F + Mth.sin(ageInTicks * 0.067F) * 0.04F;
        rightArm.zRot += 0.05F + Mth.sin(ageInTicks * 0.067F) * 0.04F;
        float flicker = 1F + Mth.sin(ageInTicks * 0.3F) * 0.08F;
        soulfire.xScale *= flicker;
        soulfire.yScale *= flicker;
    }

    @Override
    protected @Nullable MobAnimStates animStates(CatacombRevenantEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case CatacombRevenantEntity.ANIM_SLASH -> CatacombRevenantAnimations.SLASH;
            case CatacombRevenantEntity.ANIM_CALL -> CatacombRevenantAnimations.CALL;
            default -> null;
        };
    }
}
