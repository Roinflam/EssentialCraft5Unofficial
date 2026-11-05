package com.flameforge.essentialcraft5.client.model.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.anim.ECAnimatedModel;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ChaosLarvaEntity;
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
 * 畸变幼体（13d-N2）：贴地爬的小肉虫，一节身子、一个圆头、一截尾巴。和混沌畸变体共用一张 128×64 贴图（右上角那块，部位表见 {@link ChaosAberrantModel}），
 * 渲染器按 {@code chaos_aberrant} 取贴图和发光层。没有关键帧，爬的时候身子一拱一拱、尾巴左右甩。
 */
public class ChaosLarvaModel extends ECAnimatedModel<ChaosLarvaEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("chaos_larva"), "main");

    public static final String LARVA_BODY = "larva_body";
    public static final String LARVA_HEAD = "larva_head";
    public static final String LARVA_TAIL = "larva_tail";

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart tail;

    public ChaosLarvaModel(ModelPart root) {
        super(root);
        this.body = root.getChild(LARVA_BODY);
        this.head = body.getChild(LARVA_HEAD);
        this.tail = body.getChild(LARVA_TAIL);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild(LARVA_BODY, CubeListBuilder.create().texOffs(108, 0).addBox(-2F, -3F, -3F, 4F, 3F, 6F),
                PartPose.offset(0F, 24F, 0F));
        body.addOrReplaceChild(LARVA_HEAD, CubeListBuilder.create().texOffs(108, 15).addBox(-1.5F, -1.5F, -3F, 3F, 3F, 3F),
                PartPose.offset(0F, -1.5F, -3F));
        body.addOrReplaceChild(LARVA_TAIL, CubeListBuilder.create().texOffs(110, 22).addBox(-1F, -1F, 0F, 2F, 2F, 3F),
                PartPose.offset(0F, -1F, 3F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    protected void pose(ChaosLarvaEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float crawl = Mth.sin(limbSwing * 1.2F) * limbSwingAmount;
        body.yScale *= 1F + crawl * 0.2F;
        body.y -= Math.abs(crawl) * 0.6F;
        head.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.5F;
        tail.yRot += Mth.sin(ageInTicks * 0.4F) * 0.4F;
    }

    @Override
    protected @Nullable MobAnimStates animStates(ChaosLarvaEntity entity) {
        return null;
    }

    @Override
    protected @Nullable AnimationDefinition animation(int slot) {
        return null;
    }
}
