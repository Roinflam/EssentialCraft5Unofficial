package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
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
 * 在机器里面立着画一件物品（绕竖直轴转）。对应原作 RenderMagicalRepairer / RenderMagicalJukebox 里的
 * DrawUtils.renderItemStack_Full(物品, …, 转角, 0, …, 0.5, 0.65, 0.5)：GROUND 显示方式、中心 (0.5, 0.65, 0.5)。
 * <ul>
 *   <li>修复器：要修的东西每 tick 转 1°（原作 worldTime % 360）。</li>
 *   <li>唱片机：唱片固定转 90°（原作 rotation = 90）。</li>
 * </ul>
 * 客户端方块实体的物品栏由 MRUBlockEntity 的更新包同步过来（物品换了才发，修理中的耐久变化不发）。
 */
public class DeviceItemRenderer<T extends MRUBlockEntity> implements BlockEntityRenderer<T> {

    private static final double ITEM_Y = 0.65D;

    private final ItemRenderer itemRenderer;
    private final int slot;
    /** 每 tick 转多少度；0 表示固定角度 */
    private final float degreesPerTick;
    private final float baseDegrees;

    private DeviceItemRenderer(BlockEntityRendererProvider.Context context, int slot, float degreesPerTick, float baseDegrees) {
        this.itemRenderer = context.getItemRenderer();
        this.slot = slot;
        this.degreesPerTick = degreesPerTick;
        this.baseDegrees = baseDegrees;
    }

    /** 修复器：每 tick 转 1° */
    public static <T extends MRUBlockEntity> BlockEntityRendererProvider<T> spinning(int slot) {
        return context -> new DeviceItemRenderer<>(context, slot, 1F, 0F);
    }

    /** 唱片机：固定转 degrees 度 */
    public static <T extends MRUBlockEntity> BlockEntityRendererProvider<T> fixed(int slot, float degrees) {
        return context -> new DeviceItemRenderer<>(context, slot, 0F, degrees);
    }

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ItemStack stack = be.getInventory().getStackInSlot(slot);
        if (stack.isEmpty()) {
            return;
        }
        float angle = baseDegrees;
        if (degreesPerTick != 0F && be.getLevel() != null) {
            angle += ((be.getLevel().getGameTime() % 360L) + partialTick) * degreesPerTick;
        }
        pose.pushPose();
        pose.translate(0.5D, ITEM_Y, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY, pose, buffers,
                be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
