package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.MagicalChestBlock;
import com.flameforge.essentialcraft5.blockentity.MagicalChestBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 魔力箱的方块实体渲染（原作 RenderMagicalChest，用的是 1.12 原版 {@code ModelChest}）。
 * <p>
 * 贴图 textures/block/chests/magical.png 是 1.12 的箱子贴图排布，1.15 起原版箱子的模型和贴图都改过，不能套 1.20 的 ChestRenderer；
 * 这里照 1.12 ModelChest 原样建模型层（盖子 14×5×14、锁扣 2×4×1、箱体 14×10×14，64×64），渲染也照原作的坐标系：
 * 先 translate(0, 1, 1) 再 scale(1, -1, -1)（等于绕 X 轴转 180°，不翻面）。
 * <p>
 * 朝向：原作绕 Y 转 rotation×90+180；这里按方块状态的 FACING 在翻转之前转（-FACING.toYRot()，和原版 ChestRenderer 一样），
 * 不转时锁扣朝南，转完锁扣朝 FACING（放下时朝着放置者）。盖子角度 = -(开合度 × 90°)，线性（原作没有原版的缓动）。
 */
public class MagicalChestRenderer implements BlockEntityRenderer<MagicalChestBlockEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("magical_chest"), "main");

    private final ModelPart lid;
    private final ModelPart lock;
    private final ModelPart bottom;

    public MagicalChestRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        this.lid = root.getChild("lid");
        this.lock = root.getChild("lock");
        this.bottom = root.getChild("bottom");
    }

    /** 1.12 ModelChest：addBox(x, y, z, 宽, 高, 深) + rotationPoint 原样搬过来 */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(0F, -5F, -14F, 14F, 5F, 14F),
                PartPose.offset(1F, 7F, 15F));
        root.addOrReplaceChild("lock", CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -2F, -15F, 2F, 4F, 1F),
                PartPose.offset(8F, 7F, 15F));
        root.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 19).addBox(0F, 0F, 0F, 14F, 10F, 14F),
                PartPose.offset(1F, 6F, 1F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(MagicalChestBlockEntity chest, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = chest.getBlockState();
        Direction facing = state.hasProperty(MagicalChestBlock.FACING) ? state.getValue(MagicalChestBlock.FACING) : Direction.SOUTH;
        float angle = -(chest.getOpenNess(partialTick) * ((float) Math.PI / 2F));
        lid.xRot = angle;
        lock.xRot = angle;

        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5F, -0.5F, -0.5F);
        pose.translate(0F, 1F, 1F);
        pose.scale(1F, -1F, -1F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(chest.layout().modelTexture()));
        lid.render(pose, consumer, packedLight, packedOverlay);
        lock.render(pose, consumer, packedLight, packedOverlay);
        bottom.render(pose, consumer, packedLight, packedOverlay);
        pose.popPose();
    }
}
