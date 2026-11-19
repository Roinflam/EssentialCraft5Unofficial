package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.client.vfx.machine.MachineVfx;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 矩阵淬炼台台座上方转着的灵魂石（第十三批 13a）：只是外观，不是槽里的东西。平时每刻转 1 度、上下浮动；
 * 有人在这台上试炼时（{@link MatrixCrucibleBlockEntity#isTrialActiveClient()}，随方块实体更新包来）转快 4 倍、满亮度。
 * 写法照矩阵换能器的 {@link MatrixAbsorberRenderer}。
 */
public class MatrixCrucibleRenderer implements BlockEntityRenderer<MatrixCrucibleBlockEntity> {

    private final ItemRenderer itemRenderer;
    private ItemStack display = ItemStack.EMPTY;

    public MatrixCrucibleRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(MatrixCrucibleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        if (display.isEmpty()) {
            display = new ItemStack(ECBootstrap.SOUL_STONE.get());
        }
        boolean active = be.isTrialActiveClient();
        float time = (level.getGameTime() % 3600L) + partialTick;
        float bob = Mth.sin(time / 20F) * 0.05F;
        pose.pushPose();
        pose.translate(0.5D, 1.15D + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(time * (active ? 4F : 1F)));
        pose.scale(0.6F, 0.6F, 0.6F);
        itemRenderer.renderStatic(display, ItemDisplayContext.FIXED, active ? LightTexture.FULL_BRIGHT : light, overlay, pose, buffers, level,
                (int) be.getBlockPos().asLong());
        pose.popPose();
        MachineVfx.submit(MachineVfx.CRUCIBLE, be, be.getBlockPos().getX() + 0.5D, be.getBlockPos().getY() + 1D, be.getBlockPos().getZ() + 0.5D);
    }
}
