package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.blockentity.EnchantUpgraderBlockEntity;
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
 * 附魔增幅台 / 升华台上方悬浮转动的附魔书：1 号格里有书就画书，没有就画输出格的成品。
 * 平时每 tick 转 2°、上下缓缓浮动；工作时转得快一倍、浮得高一点（{@link EnchantUpgraderBlockEntity#isWorking}，随更新包同步）。
 * 升华台的书浮得更高一点，台子本身高一些。槽里的物品随方块实体更新包同步（MRUBlockEntity 物品栏变了会发包）。
 */
public class EnchantUpgraderRenderer implements BlockEntityRenderer<EnchantUpgraderBlockEntity> {

    private final ItemRenderer itemRenderer;

    public EnchantUpgraderRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(EnchantUpgraderBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        ItemStack stack = be.getInventory().getStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK);
        if (stack.isEmpty()) {
            stack = be.getInventory().getStackInSlot(EnchantUpgraderBlockEntity.SLOT_OUTPUT);
        }
        if (stack.isEmpty()) {
            return;
        }
        boolean working = be.isWorking();
        float time = (float) (level.getGameTime() % 720L) + partialTick;
        float spin = time * (working ? 4F : 2F);
        float bob = (float) Math.sin(time / (working ? 6F : 12F)) * (working ? 0.06F : 0.04F);
        double base = be.tier() == com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Tier.ASCENDER ? 1.28D : 1.12D;
        pose.pushPose();
        pose.translate(0.5D, base + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(0.6F, 0.6F, 0.6F);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, 0xF000F0, overlay, pose, buffers, level,
                (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
