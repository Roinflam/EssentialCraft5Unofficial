package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.AbyssalEelAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.hoanna.AbyssalEelEntity;
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
 * 渊鳗（13d-N2）：头、下颌、两节身子、一片尾鳍，背上一道背鳍。碰撞箱只有头那么大（0.9 × 0.6），身子拖在后面是画面；
 * 游的时候身子一节比一节晚半拍地左右摆（按游动快慢加大幅度），抬头低头整条跟着转。贴图 64×64，部位名见常量，关键帧在 {@link AbyssalEelAnimations}。
 * <pre>
 * 部位        贴图起点   尺寸（宽×高×深）
 * head        (0, 0)     5×4×7
 * jaw         (24, 0)    4×1×6
 * body_front  (0, 11)    4×4×9
 * dorsal_fin  (26, 11)   1×2×7
 * body_back   (0, 24)    3×3×9
 * tail        (0, 36)    1×5×6
 * </pre>
 */
public class AbyssalEelModel extends ECAnimatedModel<AbyssalEelEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("abyssal_eel"), "main");

    public static final String EEL = "eel";
    public static final String HEAD = "head";
    public static final String JAW = "jaw";
    public static final String BODY_FRONT = "body_front";
    public static final String DORSAL_FIN = "dorsal_fin";
    public static final String BODY_BACK = "body_back";
    public static final String TAIL = "tail";

    private final ModelPart eel;
    private final ModelPart bodyFront;
    private final ModelPart bodyBack;
    private final ModelPart tail;

    public AbyssalEelModel(ModelPart root) {
        super(root);
        this.eel = root.getChild(EEL);
        this.bodyFront = eel.getChild(BODY_FRONT);
        this.bodyBack = bodyFront.getChild(BODY_BACK);
        this.tail = bodyBack.getChild(TAIL);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 整条以头后颈为轴（离地 4 像素），头朝 -Z
        PartDefinition eel = root.addOrReplaceChild(EEL, CubeListBuilder.create(), PartPose.offset(0F, 20F, -1F));
        PartDefinition head = eel.addOrReplaceChild(HEAD, CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2F, -7F, 5F, 4F, 7F), PartPose.ZERO);
        head.addOrReplaceChild(JAW, CubeListBuilder.create().texOffs(24, 0).addBox(-2F, 0F, -6F, 4F, 1F, 6F), PartPose.offset(0F, 2F, -0.5F));
        PartDefinition front = eel.addOrReplaceChild(BODY_FRONT, CubeListBuilder.create().texOffs(0, 11).addBox(-2F, -2F, 0F, 4F, 4F, 9F),
                PartPose.ZERO);
        front.addOrReplaceChild(DORSAL_FIN, CubeListBuilder.create().texOffs(26, 11).addBox(-0.5F, -2F, 0F, 1F, 2F, 7F),
                PartPose.offset(0F, -2F, 1F));
        PartDefinition back = front.addOrReplaceChild(BODY_BACK, CubeListBuilder.create().texOffs(0, 24).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 9F),
                PartPose.offset(0F, 0F, 9F));
        back.addOrReplaceChild(TAIL, CubeListBuilder.create().texOffs(0, 36).addBox(-0.5F, -2.5F, 0F, 1F, 5F, 6F),
                PartPose.offset(0F, 0F, 9F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    protected void pose(AbyssalEelEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float amp = 0.25F + Math.min(1F, limbSwingAmount) * 0.35F;
        float t = ageInTicks * 0.3F;
        bodyFront.yRot += Mth.sin(t) * amp * 0.6F;
        bodyBack.yRot += Mth.sin(t - 1.1F) * amp;
        tail.yRot += Mth.sin(t - 2.2F) * amp * 1.3F;
        eel.xRot += headPitch * Mth.DEG_TO_RAD;
        if (!entity.isInWater()) {
            // 搁浅时整条侧躺
            eel.zRot += Mth.HALF_PI;
        }
    }

    @Override
    protected @Nullable MobAnimStates animStates(AbyssalEelEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case AbyssalEelEntity.ANIM_BITE -> AbyssalEelAnimations.BITE;
            case AbyssalEelEntity.ANIM_DRAG -> AbyssalEelAnimations.DRAG;
            case AbyssalEelEntity.ANIM_CALL -> AbyssalEelAnimations.CALL;
            default -> null;
        };
    }
}
