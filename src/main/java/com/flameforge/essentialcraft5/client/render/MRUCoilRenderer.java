package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MRUCoilBlockEntity;
import com.flameforge.essentialcraft5.client.vfx.machine.MachineVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
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
 * MRU 特斯拉线圈顶上浮着转的小水晶 + 待机电弧。对应原作 RenderMRUCoil（54 行）+ ModelFloatingCube。
 * <p>
 * 水晶和射线塔同一个模型（{@link RayTowerRenderer}，末影水晶去掉底座：外层玻璃壳、内层方块，斜 60°、每 tick 转 3°），
 * 只是位置和大小不同：原作先移到 (0.5, 0.6, 0.5)、缩小到 0.2，再走 ModelFloatingCube.render 的变换——中心约在方块内 0.86 格高、比中杆顶略高。
 * 待机电弧（{@code MRUCoilBlockEntity.idleArc}，坐标相对方块角）登记给 {@link MachineVfx}，在特效阶段用等离子画法画；打向目标的电弧见 {@code CoilVfx.strike}。
 * 渲染盒只包住线圈周围 1.5 格（{@code MRUCoilBlockEntity.getRenderBoundingBox}，原作是无限大）。
 */
public class MRUCoilRenderer implements BlockEntityRenderer<MRUCoilBlockEntity> {

    private static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/entity/raycrystal.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static final float SIN_45 = (float) Math.sin(Math.PI / 4D);
    private static final float TILT = (float) Math.PI / 3F;

    private final ModelPart glass;
    private final ModelPart cube;

    public MRUCoilRenderer(BlockEntityRendererProvider.Context context) {
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
    public void render(MRUCoilBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float spin = (be.innerRotation + partialTick) * 3F;
        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        pose.pushPose();
        // RenderMRUCoil.doRender
        pose.translate(0.5F, 0.6F, 0.5F);
        pose.scale(0.2F, 0.2F, 0.2F);
        // ModelFloatingCube.render（同射线塔）
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
        pose.popPose();

        if (be.idleArc() != null) {
            BlockPos pos = be.getBlockPos();
            MachineVfx.submit(MachineVfx.COIL, be, pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D);
        }
    }
}
