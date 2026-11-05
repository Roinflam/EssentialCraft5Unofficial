package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.client.model.mob.anim.NetherImpAnimations;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.NetherImpEntity;
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
 * 炼狱小鬼的模型（第十三批 13c-M3；贴图 64 × 32，UV 底图 tools/gen/mob_uv/nether_imp.png）。
 * 大头、带一对角的小人，背后一对膜翅（厚度 0 的薄片，同原版恼鬼的翅膀）、一条带尖的尾巴；两腿垂着，悬空时整只上下轻轻浮。
 * 部位名是常量（关键帧按名字找，写错不报错）。UV 布局定了就别再改：正式贴图照它画。
 */
public class NetherImpModel extends ECAnimatedModel<NetherImpEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("nether_imp"), "main");

    public static final String BODY = "body";
    public static final String HEAD = "head";
    public static final String RIGHT_HORN = "right_horn";
    public static final String LEFT_HORN = "left_horn";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_ARM = "left_arm";
    public static final String RIGHT_LEG = "right_leg";
    public static final String LEFT_LEG = "left_leg";
    public static final String RIGHT_WING = "right_wing";
    public static final String LEFT_WING = "left_wing";
    public static final String TAIL = "tail";
    public static final String TAIL_TIP = "tail_tip";

    /** 翅膀张开的基础角（弧度，绕 Y 往外） */
    static final float WING_SPREAD = 0.75F;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightWing;
    private final ModelPart leftWing;
    private final ModelPart tail;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public NetherImpModel(ModelPart root) {
        super(root);
        this.body = root.getChild(BODY);
        this.head = body.getChild(HEAD);
        this.rightWing = body.getChild(RIGHT_WING);
        this.leftWing = body.getChild(LEFT_WING);
        this.tail = body.getChild(TAIL);
        this.rightLeg = body.getChild(RIGHT_LEG);
        this.leftLeg = body.getChild(LEFT_LEG);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild(BODY, CubeListBuilder.create().texOffs(0, 12)
                .addBox(-3F, -7F, -2F, 6F, 7F, 4F), PartPose.offset(0F, 19F, 0F));
        PartDefinition head = body.addOrReplaceChild(HEAD, CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3F, -6F, -3F, 6F, 6F, 6F), PartPose.offset(0F, -7F, 0F));
        head.addOrReplaceChild(RIGHT_HORN, CubeListBuilder.create().texOffs(24, 0)
                .addBox(-0.5F, -3F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(-2F, -6F, -0.5F, -0.2F, 0F, -0.4F));
        head.addOrReplaceChild(LEFT_HORN, CubeListBuilder.create().texOffs(24, 0).mirror()
                .addBox(-0.5F, -3F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(2F, -6F, -0.5F, -0.2F, 0F, 0.4F));
        body.addOrReplaceChild(RIGHT_ARM, CubeListBuilder.create().texOffs(20, 12)
                .addBox(-1F, -1F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(-4F, -6F, 0F, 0F, 0F, 0.2F));
        body.addOrReplaceChild(LEFT_ARM, CubeListBuilder.create().texOffs(20, 12).mirror()
                .addBox(-1F, -1F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(4F, -6F, 0F, 0F, 0F, -0.2F));
        body.addOrReplaceChild(RIGHT_LEG, CubeListBuilder.create().texOffs(28, 12)
                .addBox(-1F, 0F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(-1.5F, 0F, 0F, 0.25F, 0F, 0.05F));
        body.addOrReplaceChild(LEFT_LEG, CubeListBuilder.create().texOffs(28, 12).mirror()
                .addBox(-1F, 0F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(1.5F, 0F, 0F, 0.25F, 0F, -0.05F));
        body.addOrReplaceChild(RIGHT_WING, CubeListBuilder.create().texOffs(36, 0)
                .addBox(0F, -1F, 0F, 0F, 5F, 8F), PartPose.offsetAndRotation(-1F, -6F, 2F, 0.2F, -WING_SPREAD, 0F));
        body.addOrReplaceChild(LEFT_WING, CubeListBuilder.create().texOffs(36, 0).mirror()
                .addBox(0F, -1F, 0F, 0F, 5F, 8F), PartPose.offsetAndRotation(1F, -6F, 2F, 0.2F, WING_SPREAD, 0F));
        PartDefinition tail = body.addOrReplaceChild(TAIL, CubeListBuilder.create().texOffs(36, 14)
                .addBox(-0.5F, -0.5F, 0F, 1F, 1F, 6F), PartPose.offsetAndRotation(0F, -1F, 2F, -0.7F, 0F, 0F));
        tail.addOrReplaceChild(TAIL_TIP, CubeListBuilder.create().texOffs(50, 14)
                .addBox(-1.5F, -1F, 0F, 3F, 2F, 1F), PartPose.offset(0F, 0F, 6F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** 常驻姿势：转头、翅膀扇（飞得快扇得快）、尾巴摆、整只上下浮、两腿随前进方向往后荡 */
    @Override
    protected void pose(NetherImpEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.look(head, netHeadYaw, headPitch, 45F, 30F);
        float flap = Mth.sin(ageInTicks * (1.3F + Math.min(limbSwingAmount, 1F) * 0.6F));
        rightWing.yRot -= flap * 0.45F;
        leftWing.yRot += flap * 0.45F;
        tail.yRot += Mth.sin(ageInTicks * 0.15F) * 0.3F;
        MobPoses.hover(body, ageInTicks, 40F, 1F);
        float drag = Math.min(limbSwingAmount, 1F) * 0.6F;
        rightLeg.xRot += drag;
        leftLeg.xRot += drag;
        body.xRot += drag * 0.4F;
    }

    @Override
    protected @Nullable MobAnimStates animStates(NetherImpEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case NetherImpEntity.ANIM_CAST -> NetherImpAnimations.CAST;
            case NetherImpEntity.ANIM_BURST -> NetherImpAnimations.BURST;
            case NetherImpEntity.ANIM_SUMMON -> NetherImpAnimations.SUMMON;
            default -> null;
        };
    }
}
