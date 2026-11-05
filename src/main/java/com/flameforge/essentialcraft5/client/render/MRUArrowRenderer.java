package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.entity.MRUArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * MRU 箭的渲染（原作 RenderMRUArrow）：原版箭的模型和贴图，原作关了光照（全亮），这里把方块光照恒定为 15。
 */
public class MRUArrowRenderer extends ArrowRenderer<MRUArrowEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

    public MRUArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MRUArrowEntity entity) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(MRUArrowEntity entity, BlockPos pos) {
        return 15;
    }
}
