package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ChaosAberrantAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ChaosAberrantEntity;
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
 * 混沌畸变体（13d-N2）：四肢着地的驼背肉怪，身子前低后高，背上一个隆起的肉瘤（贴图上画暗红发光的裂缝）。比例照疣猪兽，碰撞箱 1.2 × 1.6。
 * 贴图 128×64；畸变幼体的 UV 也在这张图右上角（{@link ChaosLarvaModel}）。部位名见常量，关键帧在 {@link ChaosAberrantAnimations}。
 * <pre>
 * 部位        贴图起点   尺寸（宽×高×深）
 * body        (0, 0)     14×12×20
 * hump        (68, 0)    10×5×10
 * head        (0, 32)    10×8×9
 * jaw         (38, 32)   8×3×8
 * front_left_leg   (70, 32)  5×10×5
 * front_right_leg  (90, 32)  5×10×5
 * hind_left_leg    (70, 47)  5×10×5
 * hind_right_leg   (90, 47)  5×10×5
 * （幼体 larva_body (108, 0) 4×3×6、larva_head (108, 15) 3×3×3、larva_tail (110, 22) 2×2×3）
 * </pre>
 */
public class ChaosAberrantModel extends ECAnimatedModel<ChaosAberrantEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("chaos_aberrant"), "main");

    public static final String BODY = "body";
    public static final String HUMP = "hump";
    public static final String HEAD = "head";
    public static final String JAW = "jaw";
    public static final String FRONT_LEFT_LEG = "front_left_leg";
    public static final String FRONT_RIGHT_LEG = "front_right_leg";
    public static final String HIND_LEFT_LEG = "hind_left_leg";
    public static final String HIND_RIGHT_LEG = "hind_right_leg";

    private final ModelPart head;
    private final ModelPart frontLeft;
    private final ModelPart frontRight;
    private final ModelPart hindLeft;
    private final ModelPart hindRight;

    public ChaosAberrantModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild(BODY);
        this.head = body.getChild(HEAD);
        this.frontLeft = root.getChild(FRONT_LEFT_LEG);
        this.frontRight = root.getChild(FRONT_RIGHT_LEG);
        this.hindLeft = root.getChild(HIND_LEFT_LEG);
        this.hindRight = root.getChild(HIND_RIGHT_LEG);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 身子前低后高（绕 X 转 -0.18），腿 10 像素，胯在 y = 14
        PartDefinition body = root.addOrReplaceChild(BODY, CubeListBuilder.create().texOffs(0, 0).addBox(-7F, -6F, -10F, 14F, 12F, 20F),
                PartPose.offsetAndRotation(0F, 9F, 0F, -0.18F, 0F, 0F));
        body.addOrReplaceChild(HUMP, CubeListBuilder.create().texOffs(68, 0).addBox(-5F, -5F, -5F, 10F, 5F, 10F),
                PartPose.offsetAndRotation(0F, -6F, 3F, 0.12F, 0F, 0F));
        PartDefinition head = body.addOrReplaceChild(HEAD, CubeListBuilder.create().texOffs(0, 32).addBox(-5F, -4F, -9F, 10F, 8F, 9F),
                PartPose.offsetAndRotation(0F, 2F, -10F, 0.35F, 0F, 0F));
        head.addOrReplaceChild(JAW, CubeListBuilder.create().texOffs(38, 32).addBox(-4F, 0F, -8F, 8F, 3F, 8F),
                PartPose.offset(0F, 4F, -0.5F));
        root.addOrReplaceChild(FRONT_LEFT_LEG, CubeListBuilder.create().texOffs(70, 32).addBox(-2.5F, 0F, -2.5F, 5F, 10F, 5F),
                PartPose.offset(5F, 14F, -7F));
        root.addOrReplaceChild(FRONT_RIGHT_LEG, CubeListBuilder.create().texOffs(90, 32).addBox(-2.5F, 0F, -2.5F, 5F, 10F, 5F),
                PartPose.offset(-5F, 14F, -7F));
        root.addOrReplaceChild(HIND_LEFT_LEG, CubeListBuilder.create().texOffs(70, 47).addBox(-2.5F, 0F, -2.5F, 5F, 10F, 5F),
                PartPose.offset(5F, 14F, 7F));
        root.addOrReplaceChild(HIND_RIGHT_LEG, CubeListBuilder.create().texOffs(90, 47).addBox(-2.5F, 0F, -2.5F, 5F, 10F, 5F),
                PartPose.offset(-5F, 14F, 7F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    protected void pose(ChaosAberrantEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.look(head, netHeadYaw, headPitch, 30F, 20F);
        // 对角两条腿一起迈（同原版四足）
        MobPoses.swingPair(frontLeft, frontRight, limbSwing, limbSwingAmount, 0.6662F, 40F);
        MobPoses.swingPair(hindRight, hindLeft, limbSwing, limbSwingAmount, 0.6662F, 40F);
        // 喘气：头一点一点
        head.xRot += Mth.sin(ageInTicks * 0.08F) * 0.04F;
    }

    @Override
    protected @Nullable MobAnimStates animStates(ChaosAberrantEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case ChaosAberrantEntity.ANIM_WINDUP -> ChaosAberrantAnimations.WINDUP;
            case ChaosAberrantEntity.ANIM_DASH -> ChaosAberrantAnimations.DASH;
            case ChaosAberrantEntity.ANIM_STUN -> ChaosAberrantAnimations.STUN;
            case ChaosAberrantEntity.ANIM_BITE -> ChaosAberrantAnimations.BITE;
            case ChaosAberrantEntity.ANIM_BROOD -> ChaosAberrantAnimations.BROOD;
            default -> null;
        };
    }
}
