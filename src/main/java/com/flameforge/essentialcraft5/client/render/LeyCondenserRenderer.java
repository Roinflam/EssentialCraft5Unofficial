package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity.Target;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.Map;

/**
 * 地脉凝聚器：选了目标就在方块上方画目标物品的「全息影像」（普通物品渲染、全亮，不做半透明），在凝时绕竖轴转、上下浮动，不在凝时静止；
 * 在凝时另画一根和地脉投影仪一样的往下的光束（{@link LeyProjectorRenderer#renderBeam}）。渲染盒有限（方块实体的 getRenderBoundingBox）。
 */
public class LeyCondenserRenderer implements BlockEntityRenderer<LeyCondenserBlockEntity> {

    /** 影像中心离方块底面多高 */
    private static final float IMAGE_Y = 1.3F;
    private static final float IMAGE_SCALE = 0.6F;

    private final ItemRenderer items;
    private final Map<Target, ItemStack> stacks = new EnumMap<>(Target.class);

    public LeyCondenserRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(LeyCondenserBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        boolean working = be.isWorking();
        Target target = be.target();
        if (target != null) {
            float time = (level.getGameTime() % 7200L) + partialTick;
            pose.pushPose();
            pose.translate(0.5F, IMAGE_Y + (working ? Mth.sin(time / 10F) * 0.06F : 0F), 0.5F);
            if (working) {
                pose.mulPose(Axis.YP.rotationDegrees(time * 3F));
            }
            pose.scale(IMAGE_SCALE, IMAGE_SCALE, IMAGE_SCALE);
            ItemStack stack = stacks.computeIfAbsent(target, t -> new ItemStack(t.product));
            items.renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, level,
                    (int) be.getBlockPos().asLong());
            pose.popPose();
        }
        if (working) {
            LeyProjectorRenderer.renderBeam(level, be.getBlockPos().getY(), LeyCondenserBlockEntity.BEAM_LENGTH, partialTick, pose, buffers);
        }
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
