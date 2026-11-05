package com.flameforge.essentialcraft5.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 元素水晶模型：原作 ModelElementalCrystal（Techne 导出的 22 个方块，贴图按 128×128 的 UV 布局）逐个改写成 LayerDefinition。
 * 元素水晶的 BER 和水晶成型器顶上的 4 颗小水晶共用。
 * <p>
 * 对照：原作 {@code new ModelRenderer(this, u, v)} → texOffs(u, v)；addBox 参数不变；setRotationPoint + setRotation → offsetAndRotation
 * （1.12 的 ModelRenderer 和 1.20 的 ModelPart 都按 Z、Y、X 的顺序转，单位同为 1/16 格）。
 * 原作每个方块在 addBox <b>之后</b>才设 mirror = true，那时方块已经建好，镜像没有生效，所以这里不镜像。
 * <p>
 * 模型坐标里 y 向下是水晶的根部（y≈30），尖端在 y≈10；不转的时候就是倒挂的水晶（原作 meta 0）。
 * 只有两处用，不注册 ModelLayer，构造时直接烘焙。
 */
public final class ElementalCrystalModel {

    /** 原作 textureWidth / textureHeight（实际贴图 256×256 或 128×128，UV 按比例取，结果一样） */
    private static final int TEXTURE_SIZE = 128;

    /** u, v, 原点 x, y, z, 宽, 高, 深, 转角 x, y, z（原作 Shape1~Shape22 的数字） */
    private static final float[][] SHAPES = {
            {0, 0, 1F, 10F, -7F, 4, 20, 5, 0.2094395F, 0.2094395F, 0.3316126F},
            {18, 0, 3F, 13F, -6F, 3, 3, 3, -0.5585054F, -0.8552113F, 0.418879F},
            {18, 0, 1F, 13F, -4F, 3, 3, 3, -3.141593F, -0.9599311F, -0.4014257F},
            {18, 0, 1F, 18F, -1F, 3, 3, 3, -3.141593F, -0.9599311F, -1.029744F},
            {18, 0, 1F, 21F, -3F, 3, 3, 3, -0.0523599F, -0.6632251F, -0.9424778F},
            {18, 0, -1F, 20F, -6F, 3, 3, 3, 0.2268928F, -0.6632251F, -0.4712389F},
            {0, 0, -5F, 12F, 1F, 4, 14, 5, -0.0174533F, 0.2094395F, -0.2617994F},
            {0, 0, 2F, 16F, -1F, 4, 7, 5, -0.2268928F, 0.2094395F, 0.2617994F},
            {0, 0, -3F, 19F, -3F, 3, 5, 5, -0.0523599F, -0.7504916F, -0.1745329F},
            {0, 0, -5F, 18F, -9F, 4, 8, 4, 0.2443461F, -0.7330383F, -0.2617994F},
            {0, 0, 6F, 15F, 4F, 4, 8, 4, -0.0872665F, -0.7330383F, 0.3839724F},
            {0, 0, -4F, 19F, 4F, 3, 7, 3, -0.3316126F, -0.7330383F, 0.2268928F},
            {0, 0, 5F, 18F, -7F, 3, 7, 3, 0.1396263F, -0.7330383F, 0.0349066F},
            {18, 0, -4F, 14F, 4F, 3, 3, 3, -3.141593F, -0.9599311F, -0.7504916F},
            {18, 0, 4F, 23F, 6F, 3, 3, 3, -2.844887F, -1.099557F, -0.7853982F},
            {18, 0, -3F, 19F, 4F, 3, 3, 3, -2.740167F, -0.9250245F, -0.2617994F},
            {18, 0, -7F, 19F, -6F, 3, 3, 3, -2.408554F, -1.047198F, -0.5585054F},
            {18, 0, -6F, 22F, 0F, 3, 3, 3, -2.722714F, -1.064651F, -0.7330383F},
            {18, 0, 4F, 17F, 0F, 3, 3, 3, -0.9250245F, -0.8552113F, 0.4886922F},
            {18, 0, 6F, 15F, 7F, 3, 3, 3, -0.9250245F, -0.8726646F, 0.9773844F},
            {18, 0, 6F, 18F, -5F, 2, 2, 2, -0.715585F, -0.9599311F, 0.9250245F},
            {18, 0, -4F, 19F, 6F, 2, 2, 2, -0.715585F, -0.9599311F, 0.9250245F},
    };

    private final ModelPart root;

    public ElementalCrystalModel() {
        this.root = createLayer().bakeRoot();
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (int i = 0; i < SHAPES.length; i++) {
            float[] s = SHAPES[i];
            root.addOrReplaceChild("shape" + (i + 1),
                    CubeListBuilder.create().texOffs((int) s[0], (int) s[1]).addBox(0F, 0F, 0F, s[5], s[6], s[7]),
                    PartPose.offsetAndRotation(s[2], s[3], s[4], s[8], s[9], s[10]));
        }
        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    public void render(PoseStack pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue, float alpha) {
        root.render(pose, consumer, light, overlay, red, green, blue, alpha);
    }
}
