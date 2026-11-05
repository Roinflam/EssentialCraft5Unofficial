package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MRUGolemAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.boss.TownSentryEntity;
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
 * 废城守望者的模型（batch13.md §5.3.3「复用聚能魔像模型 ×1.25 + 肩部件」，贴图 128 × 64，和哨戒炮台共用一张）：
 * 几何和 UV 照抄聚能魔像（{@link MRUGolemModel}），打开魔像预留的两块肩甲，肩甲顶上各加一个炮塔座（{@code right_mount} / {@code left_mount}，
 * UV 在 (68, 33) / (68, 39)）。放大 1.25 倍在渲染器里做。贴图右下角 (68~124, 33~56) 另有哨戒炮台的部位（{@link SentryTurretModel}）。
 * 这里抄一份而不是直接引用魔像的层定义：两张贴图各画各的，魔像以后改模型不会把守望者的 UV 带乱。
 * 动画借魔像的三段：横扫 = 重锤、镇压 = 聚能冲击、警报 = 电弧鞭的抬手（部位名一样）。
 */
public class TownSentryModel extends ECAnimatedModel<TownSentryEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("town_sentry"), "main");

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart core;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public TownSentryModel(ModelPart root) {
        super(root);
        ModelPart waist = root.getChild("waist");
        this.body = waist.getChild("body");
        this.head = body.getChild("head");
        this.core = body.getChild("core");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
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
        PartDefinition rightShoulder = rightArm.addOrReplaceChild("right_shoulder", CubeListBuilder.create()
                .texOffs(36, 22).addBox(-6.5F, -3F, -4F, 8F, 5F, 8F, new CubeDeformation(0.1F)), PartPose.ZERO);
        rightShoulder.addOrReplaceChild("right_mount", CubeListBuilder.create()
                .texOffs(68, 33).addBox(-4.5F, -5F, -2F, 4F, 2F, 4F), PartPose.ZERO);
        PartDefinition leftArm = body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(86, 17).mirror().addBox(0F, -1F, -2.5F, 5F, 11F, 5F), PartPose.offset(8F, -11F, 0F));
        leftArm.addOrReplaceChild("left_fist", CubeListBuilder.create()
                .texOffs(86, 0).mirror().addBox(-3.5F, 0F, -3.5F, 7F, 10F, 7F), PartPose.offset(2.5F, 10F, 0F));
        PartDefinition leftShoulder = leftArm.addOrReplaceChild("left_shoulder", CubeListBuilder.create()
                .texOffs(36, 35).addBox(-1.5F, -3F, -4F, 8F, 5F, 8F, new CubeDeformation(0.1F)), PartPose.ZERO);
        leftShoulder.addOrReplaceChild("left_mount", CubeListBuilder.create()
                .texOffs(68, 39).addBox(0.5F, -5F, -2F, 4F, 2F, 4F), PartPose.ZERO);

        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 30).addBox(-3F, 0F, -3F, 6F, 14F, 6F), PartPose.offset(-3.5F, 10F, 0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 30).mirror().addBox(-3F, 0F, -3F, 6F, 14F, 6F), PartPose.offset(3.5F, 10F, 0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    protected void pose(TownSentryEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.look(head, netHeadYaw, headPitch, 45F, 30F);
        // 比魔像沉：步频同铁傀儡，腿摆 25°、胳膊反着摆 15°
        MobPoses.swingPair(leftLeg, rightLeg, limbSwing, limbSwingAmount, 0.6662F, 25F);
        MobPoses.swingPair(rightArm, leftArm, limbSwing, limbSwingAmount, 0.6662F, 15F);
        body.zRot += Mth.cos(limbSwing * 0.6662F) * 1.5F * Mth.DEG_TO_RAD * limbSwingAmount;
        MobPoses.hover(core, ageInTicks, 40F, 0.2F);
    }

    @Override
    protected @Nullable MobAnimStates animStates(TownSentryEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case TownSentryEntity.ANIM_SWEEP -> MRUGolemAnimations.HAMMER;
            case TownSentryEntity.ANIM_SUPPRESS -> MRUGolemAnimations.SLAM;
            case TownSentryEntity.ANIM_ALARM -> MRUGolemAnimations.WHIP;
            default -> null;
        };
    }
}
