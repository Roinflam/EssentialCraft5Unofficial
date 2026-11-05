package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeSpike;
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
 * 心智钉的模型：一根 4 × 16 × 4 的竖钉加一个尖，腰上套一圈转着的环，整根缓缓上下浮。贴图 32 × 32
 * （{@code tools/gen/uv_template.py --java <本文件> --id mind_spike}）。没有关键帧。只在客户端加载。
 */
public class MindSpikeModel extends ECAnimatedModel<MatrixShadeSpike> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("mind_spike"), "main");

    public static final String SPIKE = "spike";
    public static final String TIP = "tip";
    public static final String RING = "ring";

    private final ModelPart spike;
    private final ModelPart ring;

    public MindSpikeModel(ModelPart root) {
        super(root);
        this.spike = root.getChild(SPIKE);
        this.ring = root.getChild(RING);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition spike = root.addOrReplaceChild(SPIKE, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2F, -8F, -2F, 4F, 16F, 4F), PartPose.offset(0F, 12F, 0F));
        spike.addOrReplaceChild(TIP, CubeListBuilder.create()
                .texOffs(16, 0).addBox(-1F, -12F, -1F, 2F, 4F, 2F), PartPose.ZERO);
        root.addOrReplaceChild(RING, CubeListBuilder.create()
                .texOffs(0, 20).addBox(-4F, -0.5F, -4F, 8F, 1F, 8F), PartPose.offset(0F, 12F, 0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    protected void pose(MatrixShadeSpike entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.hover(spike, ageInTicks, 40F, 1F);
        MobPoses.hover(ring, ageInTicks, 40F, 1F);
        MobPoses.spin(ring, ageInTicks, 6F);
        MobPoses.spin(spike, ageInTicks, -1.5F);
    }

    @Override
    protected @Nullable MobAnimStates animStates(MatrixShadeSpike entity) {
        return null;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return null;
    }
}
