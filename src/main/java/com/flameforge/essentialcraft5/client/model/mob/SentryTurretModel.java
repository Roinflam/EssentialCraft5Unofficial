package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.boss.SentryTurretEntity;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * 哨戒炮台的模型：方底座 {@code base}、立柱 {@code pole}、能转的炮塔 {@code turret_head} 和往前伸的炮管 {@code barrel}。
 * 和废城守望者共用一张 128 × 64 的贴图（{@code textures/entity/town_sentry/town_sentry.png}），UV 在右下角：底座 (68, 45)、炮塔 (100, 33)、
 * 炮管 (100, 45)、立柱 (116, 45)，不和守望者的部位重叠（{@link TownSentryModel}）。炮塔跟着头的朝向转，没有关键帧动画；渲染器放大 1.3 倍。
 */
public class SentryTurretModel extends ECAnimatedModel<SentryTurretEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("sentry_turret"), "main");

    private final ModelPart head;

    public SentryTurretModel(ModelPart root) {
        super(root);
        this.head = root.getChild("pole").getChild("turret_head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create()
                .texOffs(68, 45).addBox(-4F, -3F, -4F, 8F, 3F, 8F), PartPose.offset(0F, 24F, 0F));
        PartDefinition pole = root.addOrReplaceChild("pole", CubeListBuilder.create()
                .texOffs(116, 45).addBox(-1F, -6F, -1F, 2F, 6F, 2F), PartPose.offset(0F, 21F, 0F));
        PartDefinition head = pole.addOrReplaceChild("turret_head", CubeListBuilder.create()
                .texOffs(100, 33).addBox(-2.5F, -5F, -2.5F, 5F, 5F, 5F), PartPose.offset(0F, -6F, 0F));
        head.addOrReplaceChild("barrel", CubeListBuilder.create()
                .texOffs(100, 45).addBox(-1F, -3.5F, -8.5F, 2F, 2F, 6F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    protected void pose(SentryTurretEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.look(head, netHeadYaw, headPitch, 180F, 30F);
    }

    @Override
    protected @Nullable MobAnimStates animStates(SentryTurretEntity entity) {
        return null;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return null;
    }
}
