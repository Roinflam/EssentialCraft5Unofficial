package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.FrostbittenAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.hoanna.FrostbittenEntity;
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
 * 霜蚀者（13d-N1）：冻在冰壳里的人形遗骸。人形骨架，头外面罩一层冰壳，肩上、背上长着三簇冰晶；精英「霜棺督卫」左臂多一面冰盾。
 * 手臂僵直地半抬着，走路一顿一顿。贴图 64×64，部位名见常量，关键帧在 {@link FrostbittenAnimations}。
 * <pre>
 * 部位        贴图起点   尺寸
 * head        (0, 0)     8×8×8
 * head_ice    (32, 0)    8×8×8（外扩 0.5）
 * body        (16, 16)   8×12×4
 * right_arm   (40, 16)   4×12×4
 * left_arm    (40, 16)   4×12×4（镜像）
 * right_leg   (0, 16)    4×12×4
 * left_leg    (0, 16)    4×12×4（镜像）
 * crystal_a   (0, 32)    2×5×2
 * crystal_b   (8, 32)    3×6×3
 * crystal_c   (20, 32)   2×4×2
 * shield      (28, 32)   10×14×1（只有精英画）
 * </pre>
 */
public class FrostbittenModel extends ECAnimatedModel<FrostbittenEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("frostbitten"), "main");

    public static final String HEAD = "head";
    public static final String HEAD_ICE = "head_ice";
    public static final String BODY = "body";
    public static final String CRYSTAL_A = "crystal_a";
    public static final String CRYSTAL_B = "crystal_b";
    public static final String CRYSTAL_C = "crystal_c";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_ARM = "left_arm";
    public static final String SHIELD = "shield";
    public static final String RIGHT_LEG = "right_leg";
    public static final String LEFT_LEG = "left_leg";

    /** 手臂平时往前抬的角度（弧度），冻僵了放不下来 */
    private static final float ARM_RAISE = -0.35F;
    /** 精英举盾时左臂多抬的角度（弧度） */
    private static final float SHIELD_RAISE = -0.5F;

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart shield;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public FrostbittenModel(ModelPart root) {
        super(root);
        this.head = root.getChild(HEAD);
        this.rightArm = root.getChild(RIGHT_ARM);
        this.leftArm = root.getChild(LEFT_ARM);
        this.shield = leftArm.getChild(SHIELD);
        this.rightLeg = root.getChild(RIGHT_LEG);
        this.leftLeg = root.getChild(LEFT_LEG);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild(HEAD, CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F),
                PartPose.ZERO);
        head.addOrReplaceChild(HEAD_ICE, CubeListBuilder.create().texOffs(32, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(0.5F)),
                PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild(BODY, CubeListBuilder.create().texOffs(16, 16).addBox(-4F, 0F, -2F, 8F, 12F, 4F),
                PartPose.ZERO);
        body.addOrReplaceChild(CRYSTAL_A, CubeListBuilder.create().texOffs(0, 32).addBox(-1F, -5F, -1F, 2F, 5F, 2F),
                PartPose.offsetAndRotation(-3F, 0.5F, 0.5F, 0.15F, 0F, -0.45F));
        body.addOrReplaceChild(CRYSTAL_B, CubeListBuilder.create().texOffs(8, 32).addBox(-1.5F, -6F, -1.5F, 3F, 6F, 3F),
                PartPose.offsetAndRotation(0.5F, 4F, 2F, 0.6F, 0.3F, 0F));
        body.addOrReplaceChild(CRYSTAL_C, CubeListBuilder.create().texOffs(20, 32).addBox(-1F, -4F, -1F, 2F, 4F, 2F),
                PartPose.offsetAndRotation(3.5F, 0.5F, 0F, -0.1F, 0F, 0.5F));
        root.addOrReplaceChild(RIGHT_ARM, CubeListBuilder.create().texOffs(40, 16).addBox(-3F, -2F, -2F, 4F, 12F, 4F),
                PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild(LEFT_ARM, CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1F, -2F, -2F, 4F, 12F, 4F),
                PartPose.offset(5F, 2F, 0F));
        leftArm.addOrReplaceChild(SHIELD, CubeListBuilder.create().texOffs(28, 32).addBox(-5F, -7F, -1F, 10F, 14F, 1F),
                PartPose.offset(0.5F, 6F, -3F));
        root.addOrReplaceChild(RIGHT_LEG, CubeListBuilder.create().texOffs(0, 16).addBox(-2F, 0F, -2F, 4F, 12F, 4F),
                PartPose.offset(-1.9F, 12F, 0F));
        root.addOrReplaceChild(LEFT_LEG, CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2F, 0F, -2F, 4F, 12F, 4F),
                PartPose.offset(1.9F, 12F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    protected void pose(FrostbittenEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.look(head, netHeadYaw, headPitch, 45F, 30F);
        boolean elite = ECMonster.isElite(entity);
        shield.visible = elite;
        // 冻僵的步子：腿摆得小、一顿一顿
        MobPoses.swingPair(leftLeg, rightLeg, limbSwing, limbSwingAmount, 0.5F, 32F);
        rightArm.xRot += ARM_RAISE;
        leftArm.xRot += ARM_RAISE + (elite ? SHIELD_RAISE : 0F);
        MobPoses.swingPair(leftArm, rightArm, limbSwing, limbSwingAmount, 0.5F, elite ? 8F : 16F);
        // 冷得发抖
        float shiver = Mth.sin(ageInTicks * 1.7F) * 0.015F;
        rightArm.zRot += 0.06F + shiver;
        leftArm.zRot -= 0.06F + shiver;
        head.zRot += shiver * 0.5F;
    }

    @Override
    protected @Nullable MobAnimStates animStates(FrostbittenEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case FrostbittenEntity.ANIM_ATTACK -> FrostbittenAnimations.ATTACK;
            case FrostbittenEntity.ANIM_BREATH -> FrostbittenAnimations.BREATH;
            case FrostbittenEntity.ANIM_SEAL -> FrostbittenAnimations.SEAL;
            default -> null;
        };
    }
}
