package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MatrixShadeAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeEntity;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeMirror;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * 矩阵之影（和镜影共用）的模型：没有腿、拖一条三节的尾巴浮在离地 5 像素处，两条长臂末端挂着一对大拳头，胸口一块 6 × 6 的矩阵格，
 * 脑后四片碎晶围成一圈慢慢转。整只约 50 像素高（碰撞箱 3 格）。贴图 128 × 128，部位名、UV 见下面的常量和 {@link #createLayer}，
 * 底图用 {@code tools/gen/uv_template.py --java <本文件> --id matrix_shade} 出。
 * <p>
 * 常驻姿势（{@link #pose}）：头跟视线、整只上下浮、尾巴摆、手臂随呼吸轻晃、碎晶环绕 Z 轴转；关键帧见 {@link MatrixShadeAnimations}。只在客户端加载。
 */
public class MatrixShadeModel<T extends Mob> extends ECAnimatedModel<T> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("matrix_shade"), "main");

    public static final String BODY = "body";
    public static final String TORSO = "torso";
    public static final String CHEST_GRID = "chest_grid";
    public static final String HEAD = "head";
    public static final String CREST = "crest";
    public static final String HALO = "halo";
    public static final String SHARD_0 = "shard_0";
    public static final String SHARD_1 = "shard_1";
    public static final String SHARD_2 = "shard_2";
    public static final String SHARD_3 = "shard_3";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_ARM = "left_arm";
    public static final String RIGHT_SHOULDER = "right_shoulder";
    public static final String LEFT_SHOULDER = "left_shoulder";
    public static final String RIGHT_FIST = "right_fist";
    public static final String LEFT_FIST = "left_fist";
    public static final String TAIL_0 = "tail_0";
    public static final String TAIL_1 = "tail_1";
    public static final String TAIL_2 = "tail_2";

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart halo;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart tail0;
    private final ModelPart tail1;
    private final ModelPart tail2;

    public MatrixShadeModel(ModelPart root) {
        super(root);
        this.body = root.getChild(BODY);
        this.head = body.getChild(HEAD);
        this.halo = head.getChild(HALO);
        this.rightArm = body.getChild(RIGHT_ARM);
        this.leftArm = body.getChild(LEFT_ARM);
        this.tail0 = body.getChild(TAIL_0);
        this.tail1 = tail0.getChild(TAIL_1);
        this.tail2 = tail1.getChild(TAIL_2);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild(BODY, CubeListBuilder.create(), PartPose.ZERO);

        PartDefinition torso = body.addOrReplaceChild(TORSO, CubeListBuilder.create()
                .texOffs(0, 20).addBox(-6F, 0F, -3.5F, 12F, 16F, 7F), PartPose.offset(0F, -12F, 0F));
        torso.addOrReplaceChild(CHEST_GRID, CubeListBuilder.create()
                .texOffs(48, 0).addBox(-3F, 3F, -4.5F, 6F, 6F, 1F), PartPose.ZERO);

        PartDefinition head = body.addOrReplaceChild(HEAD, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5F, -10F, -5F, 10F, 10F, 10F), PartPose.offset(0F, -12F, 0F));
        head.addOrReplaceChild(CREST, CubeListBuilder.create()
                .texOffs(40, 0).addBox(-1F, -14F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        PartDefinition halo = head.addOrReplaceChild(HALO, CubeListBuilder.create(), PartPose.offset(0F, -5F, 6.5F));
        halo.addOrReplaceChild(SHARD_0, CubeListBuilder.create()
                .texOffs(64, 0).addBox(-1.5F, -1.5F, -0.5F, 3F, 3F, 1F), PartPose.offsetAndRotation(5.7F, -5.7F, 0F, 0F, 0F, 0.785F));
        halo.addOrReplaceChild(SHARD_1, CubeListBuilder.create()
                .texOffs(72, 0).addBox(-1.5F, -1.5F, -0.5F, 3F, 3F, 1F), PartPose.offsetAndRotation(-5.7F, -5.7F, 0F, 0F, 0F, 0.785F));
        halo.addOrReplaceChild(SHARD_2, CubeListBuilder.create()
                .texOffs(80, 0).addBox(-1.5F, -1.5F, -0.5F, 3F, 3F, 1F), PartPose.offsetAndRotation(-5.7F, 5.7F, 0F, 0F, 0F, 0.785F));
        halo.addOrReplaceChild(SHARD_3, CubeListBuilder.create()
                .texOffs(88, 0).addBox(-1.5F, -1.5F, -0.5F, 3F, 3F, 1F), PartPose.offsetAndRotation(5.7F, 5.7F, 0F, 0F, 0F, 0.785F));

        PartDefinition rightArm = body.addOrReplaceChild(RIGHT_ARM, CubeListBuilder.create()
                .texOffs(40, 20).addBox(-2.5F, -1F, -2.5F, 5F, 16F, 5F), PartPose.offsetAndRotation(-9F, -11F, 0F, 0F, 0F, 0.12F));
        rightArm.addOrReplaceChild(RIGHT_SHOULDER, CubeListBuilder.create()
                .texOffs(0, 56).addBox(-3.5F, -3F, -3.5F, 7F, 4F, 7F), PartPose.ZERO);
        rightArm.addOrReplaceChild(RIGHT_FIST, CubeListBuilder.create()
                .texOffs(80, 20).addBox(-3.5F, 0F, -3.5F, 7F, 7F, 7F), PartPose.offset(0F, 14F, 0F));

        PartDefinition leftArm = body.addOrReplaceChild(LEFT_ARM, CubeListBuilder.create()
                .texOffs(60, 20).addBox(-2.5F, -1F, -2.5F, 5F, 16F, 5F), PartPose.offsetAndRotation(9F, -11F, 0F, 0F, 0F, -0.12F));
        leftArm.addOrReplaceChild(LEFT_SHOULDER, CubeListBuilder.create()
                .texOffs(28, 56).addBox(-3.5F, -3F, -3.5F, 7F, 4F, 7F), PartPose.ZERO);
        leftArm.addOrReplaceChild(LEFT_FIST, CubeListBuilder.create()
                .texOffs(80, 34).addBox(-3.5F, 0F, -3.5F, 7F, 7F, 7F), PartPose.offset(0F, 14F, 0F));

        PartDefinition tail0 = body.addOrReplaceChild(TAIL_0, CubeListBuilder.create()
                .texOffs(0, 44).addBox(-4F, 0F, -2.5F, 8F, 5F, 5F), PartPose.offset(0F, 4F, 0F));
        PartDefinition tail1 = tail0.addOrReplaceChild(TAIL_1, CubeListBuilder.create()
                .texOffs(28, 44).addBox(-3F, 0F, -2F, 6F, 5F, 4F), PartPose.offset(0F, 5F, 0F));
        tail1.addOrReplaceChild(TAIL_2, CubeListBuilder.create()
                .texOffs(50, 44).addBox(-2F, 0F, -1.5F, 4F, 5F, 3F), PartPose.offset(0F, 5F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    protected void pose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.look(head, netHeadYaw, headPitch, 45F, 30F);
        MobPoses.hover(body, ageInTicks, 50F, 1.2F);
        halo.zRot += Mth.wrapDegrees(ageInTicks * 1.5F) * Mth.DEG_TO_RAD;
        float sway = Mth.sin(ageInTicks * 0.08F);
        tail0.xRot += 0.15F + sway * 0.08F + limbSwingAmount * 0.35F;
        tail1.xRot += 0.12F + Mth.sin(ageInTicks * 0.08F - 0.8F) * 0.1F;
        tail2.xRot += 0.10F + Mth.sin(ageInTicks * 0.08F - 1.6F) * 0.12F;
        rightArm.xRot += Mth.sin(ageInTicks * 0.06F) * 0.05F + Mth.cos(limbSwing * 0.4F) * 0.3F * limbSwingAmount;
        leftArm.xRot += Mth.sin(ageInTicks * 0.06F + 1.2F) * 0.05F - Mth.cos(limbSwing * 0.4F) * 0.3F * limbSwingAmount;
    }

    @Override
    protected @Nullable MobAnimStates animStates(T entity) {
        if (entity instanceof MatrixShadeEntity shade) {
            return shade.anims;
        }
        return entity instanceof MatrixShadeMirror mirror ? mirror.anims : null;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return MatrixShadeAnimations.forSlot(slot);
    }
}
