package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.RayTowerBlockEntity;
import com.flameforge.essentialcraft5.client.vfx.machine.MachineVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/**
 * 射线塔顶上浮着转的水晶。对应原作 RenderRayTower + ModelFloatingCube（末影水晶模型去掉底座）：
 * 外层玻璃壳、内层方块，两层都沿 (1, 0, 1) 轴斜 60°，绕竖轴每 tick 转 3°。
 * <p>
 * 变换照抄原作：先移到 (0.5, 1.6, 0.5) 缩小到 0.4，再按 ModelFloatingCube.render 的顺序放大 2 倍、下移 0.5、
 * 转、上移 0.8 + 0.35（原作把固定的 0.35 当 age 参数传进去，没有上下浮动）……算下来水晶中心在方块上方约 2.1 格。
 * 模型不用注册 ModelLayer：只有这一处用，直接在构造器里烘焙。贴图 256×128，按 64×32 的 UV 布局（同末影水晶）。
 * 方块实体只在下半格，所以不用像原作那样判断 meta。
 */
public class RayTowerRenderer implements BlockEntityRenderer<RayTowerBlockEntity> {

    private static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/entity/raycrystal.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static final float SIN_45 = (float) Math.sin(Math.PI / 4D);
    private static final float TILT = (float) Math.PI / 3F;

    private final ModelPart glass;
    private final ModelPart cube;

    public RayTowerRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = createLayer().bakeRoot();
        this.glass = root.getChild("glass");
        this.cube = root.getChild("cube");
    }

    private static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("glass", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -4F, -4F, 8F, 8F, 8F), PartPose.ZERO);
        root.addOrReplaceChild("cube", CubeListBuilder.create().texOffs(32, 0).addBox(-4F, -4F, -4F, 8F, 8F, 8F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void render(RayTowerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float spin = (be.innerRotation + partialTick) * 3F;
        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        pose.pushPose();
        // RenderRayTower.doRender
        pose.translate(0.5F, 1.6F, 0.5F);
        pose.scale(0.4F, 0.4F, 0.4F);
        // ModelFloatingCube.render
        pose.scale(2F, 2F, 2F);
        pose.translate(0F, -0.5F, 0F);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.translate(0F, 0.8F + 0.35F, 0F);
        pose.mulPose(new Quaternionf().setAngleAxis(TILT, SIN_45, 0F, SIN_45));
        glass.render(pose, consumer, light, overlay);
        pose.scale(0.875F, 0.875F, 0.875F);
        pose.mulPose(new Quaternionf().setAngleAxis(TILT, SIN_45, 0F, SIN_45));
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(0.875F, 0.875F, 0.875F);
        pose.mulPose(new Quaternionf().setAngleAxis(TILT, SIN_45, 0F, SIN_45));
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        cube.render(pose, consumer, light, overlay);
        MachineVfx.submit(MachineVfx.RAY, be, be.getBlockPos().getX() + 0.5D, be.getBlockPos().getY() + 2D, be.getBlockPos().getZ() + 0.5D);
        pose.popPose();
    }
}
