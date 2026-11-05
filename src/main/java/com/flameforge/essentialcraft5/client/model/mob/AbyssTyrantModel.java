package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.AbyssalEelAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.boss.AbyssTyrantEntity;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * 渊潮领主（13e-S2）：几何、部位名、贴图 UV 全部照渊鳗（{@link AbyssalEelModel#createLayer}，64 × 64，派生贴图从渊鳗换色），
 * 渲染时整体放大 {@value #SCALE} 倍（{@code SunkenDistrictClient}）。摆尾比渊鳗慢、幅度大一点（个头大）；
 * 咬照渊鳗的 {@code BITE}，漩涡借渊鳗群首叫帮手那段（张大嘴、尾巴甩两下）。
 */
public class AbyssTyrantModel extends ECAnimatedModel<AbyssTyrantEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("abyss_tyrant"), "main");
    /** 相对渊鳗的放大倍数（batch13.md §5.3.4） */
    public static final float SCALE = 2.2F;

    private final ModelPart eel;
    private final ModelPart bodyFront;
    private final ModelPart bodyBack;
    private final ModelPart tail;

    public AbyssTyrantModel(ModelPart root) {
        super(root);
        this.eel = root.getChild(AbyssalEelModel.EEL);
        this.bodyFront = eel.getChild(AbyssalEelModel.BODY_FRONT);
        this.bodyBack = bodyFront.getChild(AbyssalEelModel.BODY_BACK);
        this.tail = bodyBack.getChild(AbyssalEelModel.TAIL);
    }

    @Override
    protected void pose(AbyssTyrantEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float amp = 0.3F + Math.min(1F, limbSwingAmount) * 0.35F;
        float t = ageInTicks * 0.2F;
        bodyFront.yRot += Mth.sin(t) * amp * 0.6F;
        bodyBack.yRot += Mth.sin(t - 1.1F) * amp;
        tail.yRot += Mth.sin(t - 2.2F) * amp * 1.3F;
        eel.xRot += headPitch * Mth.DEG_TO_RAD;
        if (!entity.isInWater()) {
            eel.zRot += Mth.HALF_PI;
        }
    }

    @Override
    protected @Nullable MobAnimStates animStates(AbyssTyrantEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case AbyssTyrantEntity.ANIM_BITE -> AbyssalEelAnimations.BITE;
            case AbyssTyrantEntity.ANIM_VORTEX -> AbyssalEelAnimations.CALL;
            default -> null;
        };
    }
}
