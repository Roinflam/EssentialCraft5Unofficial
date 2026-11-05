package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 魔力防护套装的盔甲模型：原作 client/model/ModelArmorEC（ModelBiped 子类，贴图 128×64）改写成 LayerDefinition。
 * <p>
 * 基础部件就是原版人形（{@link HumanoidModel#createMesh}，和 1.12 的 ModelBiped 尺寸、UV 一致），外加 16 个装饰小方块挂在
 * 头、身体、双臂上（位置、旋转、UV 照抄原作）。原作这些小方块是先 addBox 再设 mirror = true，1.12 的 mirror 只影响之后加的方块，
 * 所以它们实际上都没镜像，这里也不镜像。1.12 的旋转顺序是 Z→Y→X，和 1.20 的 {@code rotationZYX} 一样。
 * <p>
 * 膨胀量照原作 ItemArmorEC.getArmorModel 的取法（和原版「外层 1.0、内层 0.5」不一样）：
 * 护腿用 1.0（{@link #LEGS}），胸甲用 0.75（{@link #CHEST}），头盔、靴子用 0.5（{@link #HEAD_FEET}）。
 * 贴图上护腿只画了腿的上 9 像素、靴子只画了腿的下 4 像素，所以两层不会互相挡。
 */
public final class MHSArmorModel {

    private static final String NAME = "mhs_armor";

    public static final ModelLayerLocation LEGS = new ModelLayerLocation(EssentialCraft5.id(NAME), "legs");
    public static final ModelLayerLocation CHEST = new ModelLayerLocation(EssentialCraft5.id(NAME), "chest");
    public static final ModelLayerLocation HEAD_FEET = new ModelLayerLocation(EssentialCraft5.id(NAME), "head_feet");

    public static final float LEGS_INFLATION = 1F;
    public static final float CHEST_INFLATION = 0.75F;
    public static final float HEAD_FEET_INFLATION = 0.5F;

    private MHSArmorModel() {
    }

    public static LayerDefinition create(float inflation) {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(inflation), 0F);
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("body_cross", box(10, 32, -4F, 0F, -2.1F, 1, 11, 1),
                PartPose.offsetAndRotation(0F, 0F, -1F, 0F, 0F, -0.6108652F));
        body.addOrReplaceChild("body_cross_1", box(10, 32, 3F, 0F, -2.1F, 1, 11, 1),
                PartPose.offsetAndRotation(0F, 0F, -1F, 0F, 0F, 0.6108652F));
        body.addOrReplaceChild("body_plate", box(14, 39, -3F, 2F, 1F, 6, 3, 1),
                PartPose.offsetAndRotation(0F, 0F, 1F, 0.1919862F, 0F, 0F));
        body.addOrReplaceChild("body_plate_b", box(14, 39, -3F, 5F, 3F, 6, 3, 1),
                PartPose.offsetAndRotation(0F, 0F, 1F, -0.1919862F, 0F, 0F));
        body.addOrReplaceChild("body_plate_l", box(14, 32, -3F, 2F, 2F, 3, 6, 1),
                PartPose.offsetAndRotation(0F, 0F, 1F, 0F, -0.1919862F, 0F));
        body.addOrReplaceChild("body_plate_r", box(14, 32, 0F, 2F, 2F, 3, 6, 1),
                PartPose.offsetAndRotation(0F, 0F, 1F, 0F, 0.1919862F, 0F));

        PartDefinition leftArm = root.getChild("left_arm");
        leftArm.addOrReplaceChild("lal", box(0, 40, -1.233333F, -0.8666667F, -2.633333F, 1, 10, 1),
                PartPose.offsetAndRotation(0F, 0F, -1.25F, 0.1047198F, -0.7853982F, -0.1047198F));
        leftArm.addOrReplaceChild("lap", box(0, 32, -4F, -2F, -2F, 1, 4, 4),
                PartPose.offsetAndRotation(7.5F, 0F, 0F, 0F, 0F, -0.0872665F));
        leftArm.addOrReplaceChild("lap_1", box(0, 32, -3.5F, 1F, -2F, 1, 4, 4),
                PartPose.offsetAndRotation(6.75F, 0F, 0F, 0F, 0F, -0.0872665F));
        leftArm.addOrReplaceChild("lap_2", box(0, 32, -2F, 5F, -2F, 1, 4, 4),
                PartPose.offsetAndRotation(5F, 0F, 0F, 0F, 0F, -0.0872665F));

        PartDefinition rightArm = root.getChild("right_arm");
        rightArm.addOrReplaceChild("rap", box(0, 32, 3F, -2F, -2F, 1, 4, 4),
                PartPose.offsetAndRotation(-7.5F, 0F, 0F, 0F, 0F, 0.0872665F));
        rightArm.addOrReplaceChild("rap_1", box(0, 32, 2.5F, 1F, -2F, 1, 4, 4),
                PartPose.offsetAndRotation(-6.75F, 0F, 0F, 0F, 0F, 0.0872665F));
        rightArm.addOrReplaceChild("rap_2", box(0, 32, 2F, 5F, -2F, 1, 4, 4),
                PartPose.offsetAndRotation(-6F, 0F, 0F, 0F, 0F, 0.0872665F));
        // 原作叫 helml，却挂在右臂上；照抄
        rightArm.addOrReplaceChild("helml", box(0, 40, -2.5F, -1F, -1.5F, 1, 10, 1),
                PartPose.offsetAndRotation(0F, 0F, -1.25F, 0.122173F, -0.7853982F, -0.1047198F));

        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("helmt", box(0, 52, -5F, -9F, -5F, 10, 1, 10), PartPose.ZERO);
        // 绕 X 转 90°：竖在脸前面的面罩
        head.addOrReplaceChild("helms", box(40, 52, -5F, -5F, -1F, 10, 1, 10),
                PartPose.offsetAndRotation(0F, 0F, 0.1F, 1.570796F, 0F, 0F));

        return LayerDefinition.create(mesh, 128, 64);
    }

    private static CubeListBuilder box(int u, int v, float x, float y, float z, int width, int height, int depth) {
        return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, width, height, depth);
    }
}
