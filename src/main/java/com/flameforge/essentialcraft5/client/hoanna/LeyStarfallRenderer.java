package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.entity.LeyStarfallEntity;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 地脉落晶的画法（HS §2.2.3）：地脉结晶的物品模型（{@code ItemDisplayContext.GROUND}）放大 1.5 倍、全亮，绕竖轴慢慢转（每 10 秒一圈），不画新贴图。
 * 不上下浮动、不闪：停住以后就是一颗静静转着的晶体。
 */
@OnlyIn(Dist.CLIENT)
public class LeyStarfallRenderer extends EntityRenderer<LeyStarfallEntity> {

    private static final float SCALE = 1.5F;
    /** 每刻转几度（200 刻一圈） */
    private static final float SPIN_PER_TICK = 1.8F;

    private final ItemRenderer items;
    private ItemStack stack = ItemStack.EMPTY;

    public LeyStarfallRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0F;
    }

    @Override
    public void render(LeyStarfallEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (stack.isEmpty()) {
            stack = new ItemStack(ECHoannaMaterials.LEY_CRYSTAL.get());
        }
        pose.pushPose();
        // 服务端的位置一直在悬停高度，下落只在这里画（见 LeyStarfallEntity）
        pose.translate(0D, 0.25D + entity.visualOffset(partialTick), 0D);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees((entity.tickCount + partialTick) * SPIN_PER_TICK + entity.getId() * 37F)));
        pose.scale(SCALE, SCALE, SCALE);
        items.renderStatic(stack, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(),
                entity.getId());
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LeyStarfallEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
