package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.ReactorBlock;
import com.flameforge.essentialcraft5.blockentity.ReactorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * 反应堆核心上的浮球和电弧。对应原作 RenderMRUReactor（L24~48）：
 * <ul>
 *   <li>浮球：OBJ {@code sphere}（半径 1、底贴 y = 0，贴图 block/model/sphere），缩放 0.55。没成形时灰色（×0.4）、贴在方块里；
 *       成形后上浮 0.5 格、原色。原作关了光照（不分明暗面），但照样吃所在位置的亮度——这里用方块的实心渲染类型画
 *       （方块着色器不按法线算明暗，亮度按传进来的 light），和原作的观感一致。</li>
 *   <li>电弧：方块实体在客户端 tick 里维护的 {@link ReactorBlockEntity#arcs()}（相对方块角），成形时才有，交给框架的 {@link ArcRenderer}。</li>
 * </ul>
 * 原作这个类里还载入了底座 OBJ {@code mrureactor_btm}，但从没画过（底座是方块模型）；这里也只画浮球。
 * 浮球不属于任何方块状态，模型文件由 datagen 随方块生成（{@code block/reactor_sphere}），在 {@code ReactorClient} 里登记成额外模型。
 * 渲染盒见 {@link ReactorBlockEntity#getRenderBoundingBox()}。
 */
public class ReactorRenderer implements BlockEntityRenderer<ReactorBlockEntity> {

    /** 额外模型：models/block/reactor_sphere.json（OBJ sphere） */
    public static final ResourceLocation SPHERE = EssentialCraft5.id("block/reactor_sphere");
    private static final float SCALE = 0.55F;
    /** 没成形时的颜色倍数（原作 GlStateManager.color(0.4, 0.4, 0.4)） */
    private static final float IDLE_SHADE = 0.4F;

    private final RandomSource random = RandomSource.create();

    public ReactorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ReactorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        boolean formed = ReactorBlock.isFormed(be.getBlockState());
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(SPHERE);
        float shade = formed ? 1F : IDLE_SHADE;
        pose.pushPose();
        pose.translate(0.5F, formed ? 0.5F : 0F, 0.5F);
        pose.scale(SCALE, SCALE, SCALE);
        VertexConsumer consumer = buffers.getBuffer(RenderType.solid());
        PoseStack.Pose last = pose.last();
        for (Direction side : Direction.values()) {
            draw(model, side, last, consumer, shade, light, overlay);
        }
        draw(model, null, last, consumer, shade, light, overlay);
        pose.popPose();

        if (formed) {
            for (ArcEffect arc : be.arcs()) {
                ArcRenderer.render(arc, pose, buffers, partialTick);
            }
        }
    }

    private void draw(BakedModel model, @Nullable Direction side, PoseStack.Pose pose, VertexConsumer consumer, float shade,
                      int light, int overlay) {
        random.setSeed(42L);
        for (BakedQuad quad : model.getQuads(null, side, random, ModelData.EMPTY, null)) {
            // 不读模型自带的顶点色（OBJ 的 Kd），颜色只由 shade 决定
            consumer.putBulkData(pose, quad, shade, shade, shade, light, overlay);
        }
    }
}
