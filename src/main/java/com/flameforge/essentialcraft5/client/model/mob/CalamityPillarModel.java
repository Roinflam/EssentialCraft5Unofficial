package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.boss.CalamityPillarEntity;
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
 * 灾柱的模型：16 × 4 × 16 的底座、10 × 52 × 10 的柱身，顶上浮着一块转着的晶核。贴图 128 × 64、灰度，由渲染器按那一相的颜色染
 * （{@code tools/gen/uv_template.py --java <本文件> --id calamity_pillar}）。没有关键帧。只在客户端加载。
 */
public class CalamityPillarModel extends ECAnimatedModel<CalamityPillarEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("calamity_pillar"), "main");

    private static final String BASE = "base";
    private static final String SHAFT = "shaft";
    private static final String CAP = "cap";

    private final ModelPart cap;

    public CalamityPillarModel(ModelPart root) {
        super(root);
        this.cap = root.getChild(CAP);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(BASE, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8F, 20F, -8F, 16F, 4F, 16F), PartPose.ZERO);
        root.addOrReplaceChild(SHAFT, CubeListBuilder.create()
                .texOffs(64, 0).addBox(-5F, -32F, -5F, 10F, 52F, 10F), PartPose.ZERO);
        root.addOrReplaceChild(CAP, CubeListBuilder.create()
                .texOffs(0, 20).addBox(-3F, -3F, -3F, 6F, 6F, 6F), PartPose.offset(0F, -36F, 0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    protected void pose(CalamityPillarEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.hover(cap, ageInTicks, 50F, 1F);
        MobPoses.spin(cap, ageInTicks, entity.main() ? 4F : 2F);
    }

    @Override
    protected @Nullable MobAnimStates animStates(CalamityPillarEntity entity) {
        return null;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return null;
    }
}
