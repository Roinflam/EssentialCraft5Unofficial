package com.flameforge.essentialcraft5.client.model;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.DemonEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * 恶魔的模型（原作 ModelDemon）：1.12 的 ModelBiped（64×32 贴图，{@link HumanoidModel#createMesh} 就是这套 UV）加背后一片 32 × 16 的翅膀
 * （原作 {@code addBox(-16, -4, 2.1, 32, 16, 0)}，贴图 {@code demon_wings.png} 的 (0, 0)），翅膀跟着身体转。
 * <p>
 * 翅膀不在 {@link #headParts} / {@link #bodyParts} 里：主体用恶魔贴图画，翅膀由渲染器的翅膀层换贴图、半透明另画；
 * 发光的眼睛层画的是主体，不会把翅膀也画一遍。
 */
public class DemonModel extends HumanoidModel<DemonEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("demon"), "main");
    private static final String WINGS = "wings";

    public final ModelPart wings;

    public DemonModel(ModelPart root) {
        super(root);
        this.wings = root.getChild(WINGS);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0F);
        mesh.getRoot().addOrReplaceChild(WINGS, CubeListBuilder.create().texOffs(0, 0).addBox(-16F, -4F, 2.1F, 32F, 16F, 0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(DemonEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // 原作 setRotationAngles：翅膀的三个转角照抄身体
        wings.copyFrom(body);
    }
}
