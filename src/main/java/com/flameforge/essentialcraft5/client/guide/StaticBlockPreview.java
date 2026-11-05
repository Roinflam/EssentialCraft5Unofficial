package com.flameforge.essentialcraft5.client.guide;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 方块实体渲染器给研究手册的立体结构图画一帧<b>静止</b>的画面（{@link StructureView3D}）。
 *
 * <p>方块模型是空的、样子全靠方块实体渲染器画的方块（水晶柱里浮着转的水晶）才需要它。渲染器本身按世界时间转，
 * 直接拿来画就成了阅读栏里一直在动的东西；实现这个接口的渲染器在手册里画固定的一帧。坐标系和方块实体渲染器一样（方块的西北下角为原点）。</p>
 */
@OnlyIn(Dist.CLIENT)
public interface StaticBlockPreview {

    void renderPreview(BlockState state, PoseStack pose, MultiBufferSource buffers);
}
