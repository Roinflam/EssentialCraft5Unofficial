package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MatrixWraithAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.MatrixWraithEntity;
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
 * 矩阵游魂（13c-M1）：兜帽人形、没有腿，下半身是一截袍子拖着一片后摆，离地飘着；胸口一颗紫色矩阵核（发光层）。手臂比人长，
 * 平时半抬着往前探。贴图 64×64，部位名见常量，关键帧在 {@link MatrixWraithAnimations}。
 * <pre>
 * 部位        贴图起点   尺寸
 * head        (0, 0)     8×8×8
 * hood        (32, 0)    8×8×8（外扩 0.5）
 * body        (16, 16)   8×12×4
 * right_arm   (40, 16)   4×14×4
 * left_arm    (40, 16)   4×14×4（镜像）
 * robe        (0, 34)    8×10×6
 * robe_tail   (28, 34)   6×8×1
 * core        (44, 34)   2×2×2
 * </pre>
 */
public class MatrixWraithModel extends ECAnimatedModel<MatrixWraithEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("matrix_wraith"), "main");

    public static final String WRAITH = "wraith";
    public static final String HEAD = "head";
    public static final String HOOD = "hood";
    public static final String BODY = "body";
    public static final String CORE = "core";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_ARM = "left_arm";
    public static final String ROBE = "robe";
    public static final String ROBE_TAIL = "robe_tail";

    /** 渲染时整只缩放（模型 2.15 格高，缩到和 1.9 格的碰撞箱齐平；UV 不受影响） */
    public static final float RENDER_SCALE = 0.9F;

    /** 手臂平时往前探的角度（弧度） */
    private static final float ARM_REACH = -0.55F;

    private final ModelPart wraith;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart robe;
    private final ModelPart robeTail;

    public MatrixWraithModel(ModelPart root) {
        super(root);
        this.wraith = root.getChild(WRAITH);
        this.head = wraith.getChild(HEAD);
        this.rightArm = wraith.getChild(RIGHT_ARM);
        this.leftArm = wraith.getChild(LEFT_ARM);
        this.robe = wraith.getChild(ROBE);
        this.robeTail = robe.getChild(ROBE_TAIL);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 整只往上抬 2 像素：袍子下摆不着地
        PartDefinition wraith = root.addOrReplaceChild(WRAITH, CubeListBuilder.create(), PartPose.offset(0F, -2F, 0F));
        PartDefinition head = wraith.addOrReplaceChild(HEAD, CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F),
                PartPose.ZERO);
        head.addOrReplaceChild(HOOD, CubeListBuilder.create().texOffs(32, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(0.5F)),
                PartPose.ZERO);
        PartDefinition body = wraith.addOrReplaceChild(BODY, CubeListBuilder.create().texOffs(16, 16).addBox(-4F, 0F, -2F, 8F, 12F, 4F),
                PartPose.ZERO);
        body.addOrReplaceChild(CORE, CubeListBuilder.create().texOffs(44, 34).addBox(-1F, -1F, -1F, 2F, 2F, 2F),
                PartPose.offset(0F, 5F, -2F));
        wraith.addOrReplaceChild(RIGHT_ARM, CubeListBuilder.create().texOffs(40, 16).addBox(-3F, -2F, -2F, 4F, 14F, 4F),
                PartPose.offset(-5F, 2F, 0F));
        wraith.addOrReplaceChild(LEFT_ARM, CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1F, -2F, -2F, 4F, 14F, 4F),
                PartPose.offset(5F, 2F, 0F));
        PartDefinition robe = wraith.addOrReplaceChild(ROBE, CubeListBuilder.create().texOffs(0, 34).addBox(-4F, 0F, -3F, 8F, 10F, 6F),
                PartPose.offset(0F, 12F, 0F));
        robe.addOrReplaceChild(ROBE_TAIL, CubeListBuilder.create().texOffs(28, 34).addBox(-3F, 0F, 0F, 6F, 8F, 1F),
                PartPose.offsetAndRotation(0F, 8F, 2F, 0.9F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    protected void pose(MatrixWraithEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.look(head, netHeadYaw, headPitch, 50F, 40F);
        MobPoses.hover(wraith, ageInTicks, 60F, 1F);
        // 手臂半抬往前探，跟着走路前后错开一点、平时轻轻晃
        rightArm.xRot += ARM_REACH;
        leftArm.xRot += ARM_REACH;
        MobPoses.swingPair(leftArm, rightArm, limbSwing, limbSwingAmount, 0.6662F, 20F);
        float idle = Mth.sin(ageInTicks * 0.08F) * 0.06F;
        rightArm.zRot += 0.08F + idle;
        leftArm.zRot -= 0.08F + idle;
        // 袍子随走动往后飘
        robe.xRot += limbSwingAmount * 0.35F;
        robeTail.xRot += Mth.sin(ageInTicks * 0.12F) * 0.12F + limbSwingAmount * 0.4F;
    }

    @Override
    protected @Nullable MobAnimStates animStates(MatrixWraithEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case MatrixWraithEntity.ANIM_ATTACK -> MatrixWraithAnimations.ATTACK;
            case MatrixWraithEntity.ANIM_BLINK_OUT -> MatrixWraithAnimations.BLINK_OUT;
            case MatrixWraithEntity.ANIM_BLINK_IN -> MatrixWraithAnimations.BLINK_IN;
            default -> null;
        };
    }
}
