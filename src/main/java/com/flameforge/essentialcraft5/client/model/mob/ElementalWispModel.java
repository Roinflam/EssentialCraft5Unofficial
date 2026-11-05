package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.client.model.mob.anim.MobPoses;
import com.flameforge.essentialcraft5.client.model.mob.anim.ElementalWispAnimations;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.ElementalWispEntity;
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
 * 元素游灵（13c-M1）：一团 4×4×4 的核心，外面罩一层 6×6×6 的稀疏光壳，四块碎晶绕着转，底下拖一条两节的尾巴。贴图 32×32，灰度，
 * 由渲染器按元素整只染色（火橙、水蓝、土褐、风白青）。部位名见常量，关键帧在 {@link ElementalWispAnimations}。
 * <pre>
 * 部位         贴图起点   尺寸
 * core         (0, 0)     4×4×4
 * tail         (16, 0)    2×4×2
 * tail_tip     (24, 0)    1×3×1
 * shard_0..3   (0,8) (8,8) (16,8) (24,8)  2×2×2
 * veil         (0, 12)    6×6×6
 * </pre>
 */
public class ElementalWispModel extends ECAnimatedModel<ElementalWispEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("elemental_wisp"), "main");

    public static final String WISP = "wisp";
    public static final String CORE = "core";
    public static final String VEIL = "veil";
    public static final String TAIL = "tail";
    public static final String TAIL_TIP = "tail_tip";
    public static final String SHARDS = "shards";
    public static final String SHARD = "shard_";
    public static final int SHARD_COUNT = 4;
    /** 碎晶离中心的距离（像素） */
    private static final float SHARD_RADIUS = 4.5F;

    private final ModelPart wisp;
    private final ModelPart veil;
    private final ModelPart tail;
    private final ModelPart tailTip;
    private final ModelPart shards;
    private final ModelPart[] shardParts = new ModelPart[SHARD_COUNT];

    public ElementalWispModel(ModelPart root) {
        super(root);
        this.wisp = root.getChild(WISP);
        this.veil = wisp.getChild(VEIL);
        this.tail = wisp.getChild(TAIL);
        this.tailTip = tail.getChild(TAIL_TIP);
        this.shards = wisp.getChild(SHARDS);
        for (int i = 0; i < SHARD_COUNT; i++) {
            shardParts[i] = shards.getChild(SHARD + i);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 核心离地约 7 像素（碰撞箱 0.8 格高），整团往上挪
        PartDefinition wisp = root.addOrReplaceChild(WISP, CubeListBuilder.create(), PartPose.offset(0F, 16F, 0F));
        wisp.addOrReplaceChild(CORE, CubeListBuilder.create().texOffs(0, 0).addBox(-2F, -2F, -2F, 4F, 4F, 4F), PartPose.ZERO);
        wisp.addOrReplaceChild(VEIL, CubeListBuilder.create().texOffs(0, 12).addBox(-3F, -3F, -3F, 6F, 6F, 6F), PartPose.ZERO);
        PartDefinition tail = wisp.addOrReplaceChild(TAIL, CubeListBuilder.create().texOffs(16, 0).addBox(-1F, 0F, -1F, 2F, 4F, 2F),
                PartPose.offset(0F, 2F, 0F));
        tail.addOrReplaceChild(TAIL_TIP, CubeListBuilder.create().texOffs(24, 0).addBox(-0.5F, 0F, -0.5F, 1F, 3F, 1F),
                PartPose.offset(0F, 4F, 0F));
        PartDefinition shards = wisp.addOrReplaceChild(SHARDS, CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < SHARD_COUNT; i++) {
            float a = i * Mth.HALF_PI;
            shards.addOrReplaceChild(SHARD + i, CubeListBuilder.create().texOffs(i * 8, 8).addBox(-1F, -1F, -1F, 2F, 2F, 2F),
                    PartPose.offsetAndRotation(Mth.cos(a) * SHARD_RADIUS, 0F, Mth.sin(a) * SHARD_RADIUS, 0F, -a, Mth.PI / 4F));
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    protected void pose(ElementalWispEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.hover(wisp, ageInTicks, 40F, 1F);
        MobPoses.spin(shards, ageInTicks, 6F);
        // 碎晶各自上下错开浮动
        for (int i = 0; i < SHARD_COUNT; i++) {
            shardParts[i].y += Mth.sin(ageInTicks * 0.15F + i * Mth.HALF_PI);
        }
        MobPoses.spin(veil, ageInTicks, -2F);
        MobPoses.breathe(veil, ageInTicks, 30F, 0.06F);
        // 尾巴跟着移动方向甩：走得越快甩得越开
        float sway = Mth.sin(ageInTicks * 0.2F) * 0.25F;
        tail.zRot += sway;
        tail.xRot += limbSwingAmount * 0.6F;
        tailTip.zRot += sway * 1.5F;
        wisp.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.5F;
    }

    @Override
    protected @Nullable MobAnimStates animStates(ElementalWispEntity entity) {
        return entity.anims;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return switch (slot) {
            case ElementalWispEntity.ANIM_CHARGE -> ElementalWispAnimations.CHARGE;
            case ElementalWispEntity.ANIM_DASH -> ElementalWispAnimations.DASH;
            case ElementalWispEntity.ANIM_CAST -> ElementalWispAnimations.CAST;
            default -> null;
        };
    }
}
