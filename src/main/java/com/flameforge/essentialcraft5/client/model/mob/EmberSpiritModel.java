package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.EmberSpiritAnimations;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.hoanna.EmberSpiritEntity;
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
 * 余烬灵（13d-N2）：一颗 4×4×4 的暗火核心，罩一层 6×6×6 的稀疏余烬壳，八块余烬碎块分上下两圈反着公转。贴图 32×32，灰度，
 * 渲染器按这只的余烬整只染色（{@code EmberSpiritEntity.color}）。部位名见常量，关键帧在 {@link EmberSpiritAnimations}。
 * <pre>
 * 部位          贴图起点                                         尺寸
 * core          (0, 0)                                           4×4×4
 * shard_0..3    (16, 0) (24, 0) (16, 4) (24, 4)                  2×2×2（上圈）
 * shard_4..7    (0, 8) (8, 8) (16, 8) (24, 8)                    2×2×2（下圈）
 * shell         (0, 16)                                          6×6×6
 * </pre>
 */
public class EmberSpiritModel extends ECAnimatedModel<EmberSpiritEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("ember_spirit"), "main");

    public static final String SPIRIT = "spirit";
    public static final String CORE = "core";
    public static final String SHELL = "shell";
    public static final String UPPER_RING = "upper_ring";
    public static final String LOWER_RING = "lower_ring";
    public static final String SHARD = "shard_";
    public static final int SHARDS_PER_RING = 4;
    /** 碎块离中心的距离（像素） */
    private static final float SHARD_RADIUS = 5.5F;
    /** 两圈碎块离中心的高差（像素） */
    private static final float RING_OFFSET = 3F;

    private static final int[][] SHARD_UV = {{16, 0}, {24, 0}, {16, 4}, {24, 4}, {0, 8}, {8, 8}, {16, 8}, {24, 8}};

    private final ModelPart spirit;
    private final ModelPart shell;
    private final ModelPart upperRing;
    private final ModelPart lowerRing;

    public EmberSpiritModel(ModelPart root) {
        super(root);
        this.spirit = root.getChild(SPIRIT);
        this.shell = spirit.getChild(SHELL);
        this.upperRing = spirit.getChild(UPPER_RING);
        this.lowerRing = spirit.getChild(LOWER_RING);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 核心离地约 10 像素（碰撞箱 1.2 格高，碎块围着上半截转）
        PartDefinition spirit = root.addOrReplaceChild(SPIRIT, CubeListBuilder.create(), PartPose.offset(0F, 12F, 0F));
        spirit.addOrReplaceChild(CORE, CubeListBuilder.create().texOffs(0, 0).addBox(-2F, -2F, -2F, 4F, 4F, 4F), PartPose.ZERO);
        spirit.addOrReplaceChild(SHELL, CubeListBuilder.create().texOffs(0, 16).addBox(-3F, -3F, -3F, 6F, 6F, 6F), PartPose.ZERO);
        PartDefinition upper = spirit.addOrReplaceChild(UPPER_RING, CubeListBuilder.create(), PartPose.offset(0F, -RING_OFFSET, 0F));
        PartDefinition lower = spirit.addOrReplaceChild(LOWER_RING, CubeListBuilder.create(), PartPose.offset(0F, RING_OFFSET, 0F));
        for (int i = 0; i < SHARDS_PER_RING * 2; i++) {
            PartDefinition ring = i < SHARDS_PER_RING ? upper : lower;
            float a = (i % SHARDS_PER_RING) * Mth.HALF_PI + (i < SHARDS_PER_RING ? 0F : Mth.HALF_PI / 2F);
            ring.addOrReplaceChild(SHARD + i, CubeListBuilder.create().texOffs(SHARD_UV[i][0], SHARD_UV[i][1]).addBox(-1F, -1F, -1F, 2F, 2F, 2F),
                    PartPose.offsetAndRotation(Mth.cos(a) * SHARD_RADIUS, 0F, Mth.sin(a) * SHARD_RADIUS, Mth.PI / 4F, -a, Mth.PI / 4F));
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    protected void pose(EmberSpiritEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.hover(spirit, ageInTicks, 50F, 1.2F);
        MobPoses.spin(upperRing, ageInTicks, 5F);
        MobPoses.spin(lowerRing, ageInTicks, -4F);
        MobPoses.spin(shell, ageInTicks, 1.5F);
        MobPoses.breathe(shell, ageInTicks, 24F, 0.08F);
        // 两圈各自上下起伏，像火苗一样乱窜
        upperRing.y += Mth.sin(ageInTicks * 0.13F) * 0.8F;
        lowerRing.y += Mth.sin(ageInTicks * 0.11F + 1.7F) * 0.8F;
    }

    @Override
    protected @Nullable MobAnimStates animStates(EmberSpiritEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case EmberSpiritEntity.ANIM_CAST -> EmberSpiritAnimations.CAST;
            case EmberSpiritEntity.ANIM_VOLLEY -> EmberSpiritAnimations.VOLLEY;
            default -> null;
        };
    }
}
