package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.HoannaScavengerAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.hoanna.HoannaScavengerEntity;
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
 * 遗民拾荒者（13d-N1）：人形，头上罩着兜帽式的防毒面具（外扩一层）、嘴前一截滤罐，背一个铁皮罐。右手拿撬棍或步枪（按实体的武器显示一件），
 * 精英「遗民队长」左臂多一面展开的护盾。贴图 64×64，两种武器和护盾都在同一张上；部位名见常量，关键帧在 {@link HoannaScavengerAnimations}。
 * <pre>
 * 部位          贴图起点   尺寸
 * head          (0, 0)     8×8×8
 * mask          (32, 0)    8×8×8（外扩 0.5）
 * body          (16, 16)   8×12×4
 * right_arm     (40, 16)   4×12×4
 * left_arm      (40, 16)   4×12×4（镜像）
 * right_leg     (0, 16)    4×12×4
 * left_leg      (0, 16)    4×12×4（镜像）
 * tank          (0, 32)    4×8×3
 * filter        (14, 32)   4×3×2
 * crowbar       (26, 32)   1×1×10
 * crowbar_hook  (48, 32)   1×3×1
 * rifle         (0, 43)    2×3×12
 * shield        (28, 43)   10×12×1（只有精英画）
 * </pre>
 */
public class HoannaScavengerModel extends ECAnimatedModel<HoannaScavengerEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("hoanna_scavenger"), "main");

    public static final String HEAD = "head";
    public static final String MASK = "mask";
    public static final String FILTER = "filter";
    public static final String BODY = "body";
    public static final String TANK = "tank";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_ARM = "left_arm";
    public static final String CROWBAR = "crowbar";
    public static final String CROWBAR_HOOK = "crowbar_hook";
    public static final String RIFLE = "rifle";
    public static final String SHIELD = "shield";
    public static final String RIGHT_LEG = "right_leg";
    public static final String LEFT_LEG = "left_leg";

    /** 拿步枪时右臂平时端着的角度（弧度） */
    private static final float RIFLE_CARRY = -0.6F;

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart crowbar;
    private final ModelPart rifle;
    private final ModelPart shield;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public HoannaScavengerModel(ModelPart root) {
        super(root);
        this.head = root.getChild(HEAD);
        this.rightArm = root.getChild(RIGHT_ARM);
        this.leftArm = root.getChild(LEFT_ARM);
        this.crowbar = rightArm.getChild(CROWBAR);
        this.rifle = rightArm.getChild(RIFLE);
        this.shield = leftArm.getChild(SHIELD);
        this.rightLeg = root.getChild(RIGHT_LEG);
        this.leftLeg = root.getChild(LEFT_LEG);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild(HEAD, CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F),
                PartPose.ZERO);
        head.addOrReplaceChild(MASK, CubeListBuilder.create().texOffs(32, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(0.5F)),
                PartPose.ZERO);
        head.addOrReplaceChild(FILTER, CubeListBuilder.create().texOffs(14, 32).addBox(-2F, -3F, -6F, 4F, 3F, 2F), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild(BODY, CubeListBuilder.create().texOffs(16, 16).addBox(-4F, 0F, -2F, 8F, 12F, 4F),
                PartPose.ZERO);
        body.addOrReplaceChild(TANK, CubeListBuilder.create().texOffs(0, 32).addBox(-2F, 1F, 2F, 4F, 8F, 3F), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild(RIGHT_ARM, CubeListBuilder.create().texOffs(40, 16).addBox(-3F, -2F, -2F, 4F, 12F, 4F),
                PartPose.offset(-5F, 2F, 0F));
        PartDefinition crowbar = rightArm.addOrReplaceChild(CROWBAR, CubeListBuilder.create().texOffs(26, 32).addBox(-0.5F, -0.5F, -9F, 1F, 1F, 10F),
                PartPose.offset(-1F, 9.5F, 0F));
        crowbar.addOrReplaceChild(CROWBAR_HOOK, CubeListBuilder.create().texOffs(48, 32).addBox(-0.5F, -3F, -0.5F, 1F, 3F, 1F),
                PartPose.offsetAndRotation(0F, 0F, -8.5F, -0.4F, 0F, 0F));
        rightArm.addOrReplaceChild(RIFLE, CubeListBuilder.create().texOffs(0, 43).addBox(-1F, -1F, -10F, 2F, 3F, 12F),
                PartPose.offset(-1F, 9F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild(LEFT_ARM, CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1F, -2F, -2F, 4F, 12F, 4F),
                PartPose.offset(5F, 2F, 0F));
        leftArm.addOrReplaceChild(SHIELD, CubeListBuilder.create().texOffs(28, 43).addBox(-5F, -6F, -0.5F, 10F, 12F, 1F),
                PartPose.offset(0.5F, 5F, -3F));
        root.addOrReplaceChild(RIGHT_LEG, CubeListBuilder.create().texOffs(0, 16).addBox(-2F, 0F, -2F, 4F, 12F, 4F),
                PartPose.offset(-1.9F, 12F, 0F));
        root.addOrReplaceChild(LEFT_LEG, CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2F, 0F, -2F, 4F, 12F, 4F),
                PartPose.offset(1.9F, 12F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    protected void pose(HoannaScavengerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.look(head, netHeadYaw, headPitch, 50F, 40F);
        boolean rifleHeld = entity.weapon() == HoannaScavengerEntity.Weapon.RIFLE;
        boolean elite = ECMonster.isElite(entity);
        crowbar.visible = !rifleHeld;
        rifle.visible = rifleHeld;
        shield.visible = elite;
        MobPoses.swingPair(leftLeg, rightLeg, limbSwing, limbSwingAmount, 0.6662F, 40F);
        MobPoses.swingPair(leftArm, rightArm, limbSwing, limbSwingAmount, 0.6662F, rifleHeld ? 10F : 30F);
        if (rifleHeld) {
            rightArm.xRot += RIFLE_CARRY;
            leftArm.xRot += RIFLE_CARRY * 0.8F;
            leftArm.yRot += 0.45F;
        }
        if (elite) {
            leftArm.xRot -= 0.35F;
        }
        // 背着罐子微微佝偻，呼吸时肩一起一伏
        float breath = Mth.sin(ageInTicks * 0.09F) * 0.03F;
        rightArm.zRot += 0.05F + breath;
        leftArm.zRot -= 0.05F + breath;
    }

    @Override
    protected @Nullable MobAnimStates animStates(HoannaScavengerEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case HoannaScavengerEntity.ANIM_SWING -> HoannaScavengerAnimations.SWING;
            case HoannaScavengerEntity.ANIM_AIM -> HoannaScavengerAnimations.AIM;
            case HoannaScavengerEntity.ANIM_SHOOT -> HoannaScavengerAnimations.SHOOT;
            case HoannaScavengerEntity.ANIM_STIM -> HoannaScavengerAnimations.STIM;
            case HoannaScavengerEntity.ANIM_FLARE -> HoannaScavengerAnimations.FLARE;
            default -> null;
        };
    }
}
