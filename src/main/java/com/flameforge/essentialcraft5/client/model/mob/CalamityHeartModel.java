package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
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
 * 天灾之心的模型：一颗 16³ 的心核，正面一只竖瞳，四片碎晶绕着它转，腰上一圈断开的环反着转；整颗缓缓上下浮、心核随失衡 / 崩解跳得更快。
 * 贴图 128 × 128（{@code tools/gen/uv_template.py --java <本文件> --id calamity_heart}）。熄光时渲染器只留眼睛（{@link #eyesOnly}）。
 * 没有关键帧。只在客户端加载。
 */
public class CalamityHeartModel extends ECAnimatedModel<CalamityHeartEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("calamity_heart"), "main");

    private static final String CORE = "core";
    private static final String EYE = "eye";
    private static final String ORBIT = "orbit";
    private static final String SHARD_SUN = "shard_sun";
    private static final String SHARD_FUMES = "shard_fumes";
    private static final String SHARD_DARK = "shard_dark";
    private static final String SHARD_QUAKE = "shard_quake";
    private static final String RING = "ring";
    private static final String RING_A = "ring_a";
    private static final String RING_B = "ring_b";
    private static final String RING_C = "ring_c";
    private static final String RING_D = "ring_d";

    private final ModelPart core;
    private final ModelPart eye;
    private final ModelPart orbit;
    private final ModelPart ring;
    /** 熄光时只画眼睛（渲染器每帧设） */
    public boolean eyesOnly;

    public CalamityHeartModel(ModelPart root) {
        super(root);
        this.core = root.getChild(CORE);
        this.eye = root.getChild(EYE);
        this.orbit = root.getChild(ORBIT);
        this.ring = root.getChild(RING);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(CORE, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8F, -8F, -8F, 16F, 16F, 16F), PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild(EYE, CubeListBuilder.create()
                .texOffs(64, 0).addBox(-3F, -4F, -1F, 6F, 8F, 2F), PartPose.offset(0F, 0F, -8.5F));
        // 碎晶轨道起始转 45°：四片落在对角，静止（手册、异兽录里的立绘）时日阵那片不挡在竖瞳前面
        PartDefinition orbit = root.addOrReplaceChild(ORBIT, CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, (float) (Math.PI / 4), 0F));
        orbit.addOrReplaceChild(SHARD_SUN, CubeListBuilder.create()
                .texOffs(0, 32).addBox(-1.5F, -5F, -1.5F, 3F, 10F, 3F), PartPose.offsetAndRotation(0F, -2F, -14F, 0.3F, 0F, 0F));
        orbit.addOrReplaceChild(SHARD_FUMES, CubeListBuilder.create()
                .texOffs(16, 32).addBox(-1.5F, -5F, -1.5F, 3F, 10F, 3F), PartPose.offsetAndRotation(14F, 2F, 0F, 0F, 0F, 0.3F));
        orbit.addOrReplaceChild(SHARD_DARK, CubeListBuilder.create()
                .texOffs(32, 32).addBox(-1.5F, -5F, -1.5F, 3F, 10F, 3F), PartPose.offsetAndRotation(0F, -2F, 14F, -0.3F, 0F, 0F));
        orbit.addOrReplaceChild(SHARD_QUAKE, CubeListBuilder.create()
                .texOffs(48, 32).addBox(-1.5F, -5F, -1.5F, 3F, 10F, 3F), PartPose.offsetAndRotation(-14F, 2F, 0F, 0F, 0F, -0.3F));
        PartDefinition ring = root.addOrReplaceChild(RING, CubeListBuilder.create(), PartPose.offset(0F, 10F, 0F));
        ring.addOrReplaceChild(RING_A, CubeListBuilder.create()
                .texOffs(0, 48).addBox(-10F, -0.5F, -12F, 20F, 1F, 2F), PartPose.ZERO);
        ring.addOrReplaceChild(RING_B, CubeListBuilder.create()
                .texOffs(0, 52).addBox(-10F, -0.5F, 10F, 20F, 1F, 2F), PartPose.ZERO);
        ring.addOrReplaceChild(RING_C, CubeListBuilder.create()
                .texOffs(0, 56).addBox(-12F, -0.5F, -8F, 2F, 1F, 16F), PartPose.ZERO);
        ring.addOrReplaceChild(RING_D, CubeListBuilder.create()
                .texOffs(0, 76).addBox(10F, -0.5F, -8F, 2F, 1F, 16F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    protected void pose(CalamityHeartEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float beat = switch (entity.phase()) {
            case SHIELD -> 40F;
            case UNBALANCED -> 24F;
            case COLLAPSE -> 12F;
        };
        MobPoses.breathe(core, ageInTicks, beat, 0.06F);
        MobPoses.hover(core, ageInTicks, 60F, 1F);
        MobPoses.hover(eye, ageInTicks, 60F, 1F);
        MobPoses.spin(orbit, ageInTicks, 2.5F);
        MobPoses.spin(ring, ageInTicks, -1.2F);
        core.visible = !eyesOnly;
        orbit.visible = !eyesOnly;
        ring.visible = !eyesOnly;
        eye.visible = true;
    }

    @Override
    protected @Nullable MobAnimStates animStates(CalamityHeartEntity entity) {
        return null;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return null;
    }
}
