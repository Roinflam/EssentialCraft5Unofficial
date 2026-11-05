package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.blockentity.RadiatingChamberBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 把上原料（1 号槽）平放在辐射仓顶部中间的凹台上。对应原作 RenderRadiatingChamber
 * （DrawUtils.renderItemStack_Full(物品, …, 0, 90, …, 0.5, 0.875, 0.5)：绕 X 轴转 90° 平放，高度 0.875 正是 OBJ 里凹台的高度）。
 * <p>
 * 原作用 GROUND 显示方式，它自带的向上平移在平放后变成水平方向的偏移，物品会偏离凹台中心；
 * 这里改用不带平移的 FIXED、再缩到一半，居中摆放，大小和原作 GROUND（0.5 倍）一致。
 * 客户端方块实体的物品栏由 MRUBlockEntity 的更新包同步过来。
 */
public class RadiatingChamberRenderer implements BlockEntityRenderer<RadiatingChamberBlockEntity> {

    /** 凹台表面高 0.875，抬高半个像素，免得物品背面陷进台面 */
    private static final double ITEM_Y = 0.875D + 1D / 32D;

    private final ItemRenderer itemRenderer;

    public RadiatingChamberRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(RadiatingChamberBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        ItemStack stack = be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP);
        if (stack.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5D, ITEM_Y, 0.5D);
        pose.mulPose(Axis.XP.rotationDegrees(90F));
        pose.scale(0.5F, 0.5F, 0.5F);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY, pose, buffers,
                be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
