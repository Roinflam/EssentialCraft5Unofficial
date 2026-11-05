package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.blockentity.MatrixAbsorberBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 矩阵换能器里悬浮转动的灵魂石。对应原作 RenderMatrixAbsorber（DrawUtils.renderItemStack_Full）：
 * 绕竖轴每 tick 转 1°；高度 0.25 + 0.1 起，在 0 ~ 180/500 格之间往返，360 tick 一个来回；缩放 0.5，FIXED 视角。
 * 原作按世界时间（dayTime）转，关了昼夜循环就停；这里按游戏刻（gameTime），一直转。
 * 槽里的物品随方块实体更新包同步过来（MRUBlockEntity 物品栏变了会发包）。
 */
public class MatrixAbsorberRenderer implements BlockEntityRenderer<MatrixAbsorberBlockEntity> {

    private final ItemRenderer itemRenderer;

    public MatrixAbsorberRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(MatrixAbsorberBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = be.getLevel();
        ItemStack stack = be.getInventory().getStackInSlot(MatrixAbsorberBlockEntity.SLOT_SOUL_STONE);
        if (level == null || stack.isEmpty()) {
            return;
        }
        float time = (float) (level.getGameTime() % 360L) + partialTick;
        // 原作 upperIndex：0 → 180 → 0 的三角波
        float bob = time < 180F ? 180F - time : time - 180F;
        pose.pushPose();
        pose.translate(0.5D, 0.35D + bob / 500D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(time));
        pose.scale(0.5F, 0.5F, 0.5F);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, level,
                (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
