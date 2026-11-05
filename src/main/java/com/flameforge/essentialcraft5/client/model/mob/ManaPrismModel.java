package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.ManaPrismAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ManaPrismEntity;
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
 * 浮游棱晶（13d-N1）：竖着的双锥水晶（中段一块方晶、上下各收两级成尖），绕竖轴转 45° 让棱朝前；棱前嵌着一颗光点（发光层），
 * 底下三片小棱片绕着转。悬浮核心族，和元素游灵同一种做法。贴图 64×32，部位名见常量，关键帧在 {@link ManaPrismAnimations}。
 * <pre>
 * 部位        贴图起点   尺寸
 * mid         (0, 0)     6×6×6
 * upper       (24, 0)    4×5×4
 * lower       (40, 0)    4×5×4
 * top         (56, 0)    2×3×2
 * bottom      (56, 5)    2×3×2
 * core        (24, 10)   2×2×2
 * shard_0     (0, 14)    2×4×1
 * shard_1     (8, 14)    2×4×1
 * shard_2     (16, 14)   2×4×1
 * </pre>
 */
public class ManaPrismModel extends ECAnimatedModel<ManaPrismEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("mana_prism"), "main");

    public static final String PRISM = "prism";
    public static final String CRYSTAL = "crystal";
    public static final String MID = "mid";
    public static final String UPPER = "upper";
    public static final String LOWER = "lower";
    public static final String TOP = "top";
    public static final String BOTTOM = "bottom";
    public static final String CORE = "core";
    public static final String SHARDS = "shards";
    public static final String SHARD_0 = "shard_0";
    public static final String SHARD_1 = "shard_1";
    public static final String SHARD_2 = "shard_2";

    /** 渲染时整只缩放（模型 22 像素高，缩到和 1.2 格的碰撞箱差不多） */
    public static final float RENDER_SCALE = 0.9F;
    /** 小棱片离中心的距离（像素） */
    private static final float SHARD_RADIUS = 5F;
    /** 120°（弧度） */
    private static final float THIRD = Mth.TWO_PI / 3F;

    private final ModelPart prism;
    private final ModelPart crystal;
    private final ModelPart core;
    private final ModelPart shards;
    private final ModelPart[] shardParts = new ModelPart[3];

    public ManaPrismModel(ModelPart root) {
        super(root);
        this.prism = root.getChild(PRISM);
        this.crystal = prism.getChild(CRYSTAL);
        this.core = prism.getChild(CORE);
        this.shards = prism.getChild(SHARDS);
        this.shardParts[0] = shards.getChild(SHARD_0);
        this.shardParts[1] = shards.getChild(SHARD_1);
        this.shardParts[2] = shards.getChild(SHARD_2);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 中段离地约 11 像素（碰撞箱 1.2 格高），整团往上挪
        PartDefinition prism = root.addOrReplaceChild(PRISM, CubeListBuilder.create(), PartPose.offset(0F, 12F, 0F));
        PartDefinition crystal = prism.addOrReplaceChild(CRYSTAL, CubeListBuilder.create(), PartPose.rotation(0F, Mth.PI / 4F, 0F));
        crystal.addOrReplaceChild(MID, CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -3F, -3F, 6F, 6F, 6F), PartPose.ZERO);
        crystal.addOrReplaceChild(UPPER, CubeListBuilder.create().texOffs(24, 0).addBox(-2F, -8F, -2F, 4F, 5F, 4F), PartPose.ZERO);
        crystal.addOrReplaceChild(LOWER, CubeListBuilder.create().texOffs(40, 0).addBox(-2F, 3F, -2F, 4F, 5F, 4F), PartPose.ZERO);
        crystal.addOrReplaceChild(TOP, CubeListBuilder.create().texOffs(56, 0).addBox(-1F, -11F, -1F, 2F, 3F, 2F), PartPose.ZERO);
        crystal.addOrReplaceChild(BOTTOM, CubeListBuilder.create().texOffs(56, 5).addBox(-1F, 8F, -1F, 2F, 3F, 2F), PartPose.ZERO);
        prism.addOrReplaceChild(CORE, CubeListBuilder.create().texOffs(24, 10).addBox(-1F, -1F, -1F, 2F, 2F, 2F), PartPose.offset(0F, 0F, -4F));
        PartDefinition shards = prism.addOrReplaceChild(SHARDS, CubeListBuilder.create(), PartPose.offset(0F, 9F, 0F));
        shards.addOrReplaceChild(SHARD_0, CubeListBuilder.create().texOffs(0, 14).addBox(-1F, -2F, -0.5F, 2F, 4F, 1F),
                PartPose.offsetAndRotation(SHARD_RADIUS, 0F, 0F, 0F, -Mth.HALF_PI, 0.3F));
        shards.addOrReplaceChild(SHARD_1, CubeListBuilder.create().texOffs(8, 14).addBox(-1F, -2F, -0.5F, 2F, 4F, 1F),
                PartPose.offsetAndRotation(Mth.cos(THIRD) * SHARD_RADIUS, 0F, Mth.sin(THIRD) * SHARD_RADIUS, 0F, -Mth.HALF_PI - THIRD, 0.3F));
        shards.addOrReplaceChild(SHARD_2, CubeListBuilder.create().texOffs(16, 14).addBox(-1F, -2F, -0.5F, 2F, 4F, 1F),
                PartPose.offsetAndRotation(Mth.cos(THIRD * 2F) * SHARD_RADIUS, 0F, Mth.sin(THIRD * 2F) * SHARD_RADIUS, 0F, -Mth.HALF_PI - THIRD * 2F, 0.3F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    protected void pose(ManaPrismEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.hover(prism, ageInTicks, 60F, 1F);
        MobPoses.spin(crystal, ageInTicks, 1.5F);
        MobPoses.spin(shards, ageInTicks, -3F);
        for (int i = 0; i < shardParts.length; i++) {
            shardParts[i].y += Mth.sin(ageInTicks * 0.12F + i * THIRD) * 0.8F;
        }
        // 光点一跳一跳
        float pulse = 1F + Math.max(0F, Mth.sin(ageInTicks * 0.25F)) * 0.25F;
        core.xScale *= pulse;
        core.yScale *= pulse;
        core.zScale *= pulse;
        prism.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.3F;
    }

    @Override
    protected @Nullable MobAnimStates animStates(ManaPrismEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case ManaPrismEntity.ANIM_LOCK -> ManaPrismAnimations.LOCK;
            case ManaPrismEntity.ANIM_FIRE -> ManaPrismAnimations.FIRE;
            default -> null;
        };
    }
}
