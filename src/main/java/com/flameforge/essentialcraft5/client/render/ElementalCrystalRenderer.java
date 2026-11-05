package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.api.IElementalCrystal.Element;
import com.flameforge.essentialcraft5.block.ElementalCrystalBlock;
import com.flameforge.essentialcraft5.blockentity.ElementalCrystalBlockEntity;
import com.flameforge.essentialcraft5.client.guide.StaticBlockPreview;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 元素水晶的 BER。对应原作 RenderElementalCrystal：同一个模型叠 5 层半透明——中性层 alpha 0.5，
 * 再按火、水、地、风的值各叠一层（alpha = 值 / 100）；大小按 ⌊大小⌋ / 100 缩放（原作 MathUtils.getPercentage 取整），
 * 所以新做出来的水晶（大小 1）几乎看不见，随生长变大。
 * <p>
 * 六个朝向的平移、旋转照抄原作 doRender 里 meta 0~5 的六段（meta 就是 FACING 的序号）。
 * 原作关了物品光照、开了混合、不剔除背面：这里用 entityTranslucent（不剔除、带光照图），5 层按原作顺序依次画。
 * 研究手册的立体结构图画长满、四种元素都是 0 的样子（{@link #renderPreview}），新建的方块实体大小是 1，照常画几乎看不见。
 */
public class ElementalCrystalRenderer implements BlockEntityRenderer<ElementalCrystalBlockEntity>, StaticBlockPreview {

    public static final ResourceLocation NEUTRAL = texture("mcrystaltex");
    private static final ResourceLocation[] ELEMENT_TEXTURES = {
            texture("fcrystaltex"), texture("wcrystaltex"), texture("ecrystaltex"), texture("acrystaltex")};
    private static final float NEUTRAL_ALPHA = 0.5F;

    private final ElementalCrystalModel model = new ElementalCrystalModel();

    public ElementalCrystalRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ResourceLocation texture(String name) {
        return EssentialCraft5.id("textures/block/model/" + name + ".png");
    }

    @Override
    public void render(ElementalCrystalBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float scale = (int) be.size() / IElementalCrystal.MAX_SIZE;
        if (scale <= 0F) {
            return;
        }
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(ElementalCrystalBlock.FACING) ? state.getValue(ElementalCrystalBlock.FACING) : Direction.UP;
        pose.pushPose();
        transform(pose, facing, scale);
        model.render(pose, buffers.getBuffer(RenderType.entityTranslucent(NEUTRAL)), light, overlay, 1F, 1F, 1F, NEUTRAL_ALPHA);
        for (Element element : Element.values()) {
            float alpha = be.element(element) / IElementalCrystal.MAX_ELEMENT;
            if (alpha > 0F) {
                model.render(pose, buffers.getBuffer(RenderType.entityTranslucent(ELEMENT_TEXTURES[element.ordinal()])),
                        light, overlay, 1F, 1F, 1F, Math.min(alpha, 1F));
            }
        }
        pose.popPose();
    }

    @Override
    public void renderPreview(BlockState state, PoseStack pose, MultiBufferSource buffers) {
        Direction facing = state.hasProperty(ElementalCrystalBlock.FACING) ? state.getValue(ElementalCrystalBlock.FACING) : Direction.UP;
        pose.pushPose();
        transform(pose, facing, 1F);
        model.render(pose, buffers.getBuffer(RenderType.entityTranslucent(NEUTRAL)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1F, 1F, 1F, NEUTRAL_ALPHA);
        pose.popPose();
    }

    /** 原作 doRender 的六段（x/y/z 是方块角，这里 pose 已经在方块角上） */
    static void transform(PoseStack pose, Direction facing, float scale) {
        float shrink = 1F - scale;
        switch (facing) {
            case UP -> { // meta 1：立在地上
                pose.translate(0.5F, 1.4F - shrink * 1.4F, 0.5F);
                pose.scale(scale, scale, scale);
                pose.mulPose(Axis.XP.rotationDegrees(180F));
            }
            case DOWN -> { // meta 0：挂在天花板上，模型本来的朝向
                pose.translate(0.5F, -0.4F + shrink * 1.4F, 0.5F);
                pose.scale(scale, scale, scale);
            }
            case NORTH -> { // meta 2：贴在南边的方块上，往北长
                pose.translate(0.5F, 0.5F, -0.5F + shrink * 1.5F);
                pose.scale(scale, scale, scale);
                pose.mulPose(Axis.XP.rotationDegrees(90F));
            }
            case SOUTH -> { // meta 3
                pose.translate(0.5F, 0.5F, 1.5F - shrink * 1.5F);
                pose.scale(scale, scale, scale);
                pose.mulPose(Axis.XP.rotationDegrees(-90F));
            }
            case WEST -> { // meta 4
                pose.translate(-0.5F + shrink * 1.5F, 0.5F, 0.5F);
                pose.scale(scale, scale, scale);
                pose.mulPose(Axis.XP.rotationDegrees(90F));
                pose.mulPose(Axis.ZP.rotationDegrees(270F));
            }
            case EAST -> { // meta 5
                pose.translate(1.5F - shrink * 1.5F, 0.5F, 0.5F);
                pose.scale(scale, scale, scale);
                pose.mulPose(Axis.XP.rotationDegrees(90F));
                pose.mulPose(Axis.ZP.rotationDegrees(90F));
            }
        }
    }
}
