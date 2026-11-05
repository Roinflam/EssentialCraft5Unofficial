package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.blockentity.MagicalCultivatorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * 魔力栽培器里的作物（batch12.md §4.2.3）：透过日光玻璃顶看得见。
 * <ul>
 *   <li>{@link CropBlock}：按进度画 {@code getStateForAge(进度 × 最大年龄)} 的方块模型，缩到 0.8、立在土槽上。</li>
 *   <li>别的方块物品（树苗、菌、蘑菇、紫颂花、甘蔗……）：画它放下时的方块模型，按进度从 0.3 放大到 0.8。
 *       机器只有顶面透明，扁平的物品图标从上往下看只剩一条边，所以这里画方块模型（十字形的从上面看得清）。</li>
 *   <li>不是方块的物品：画物品模型，同样从 0.3 放大到 0.8。</li>
 * </ul>
 * 进度只按 {@link MagicalCultivatorBlockEntity#RENDER_STAGES} 档同步（跨档才发更新包），不在一轮里时画最小的那档；
 * 物品栏不发给附近的人，样本是哪种东西单独随更新包来（{@link MagicalCultivatorBlockEntity#getRenderSeed}）。
 * 只在 {@value #VIEW_DISTANCE} 格内画。
 */
public class MagicalCultivatorRenderer implements BlockEntityRenderer<MagicalCultivatorBlockEntity> {

    private static final int VIEW_DISTANCE = 32;
    /** 土槽顶面的高度（侧面贴图下半是土槽） */
    private static final double SOIL_Y = 0.1D;

    private final BlockRenderDispatcher blocks;
    private final ItemRenderer items;

    public MagicalCultivatorRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(MagicalCultivatorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack seed = be.getRenderSeed();
        if (seed.isEmpty()) {
            return;
        }
        int stage = Math.max(0, be.getRenderStage());
        float growth = stage / (float) (MagicalCultivatorBlockEntity.RENDER_STAGES - 1);
        Block block = seed.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
        pose.pushPose();
        if (block instanceof CropBlock crop) {
            BlockState state = crop.getStateForAge(Math.round(growth * crop.getMaxAge()));
            pose.translate(0.1D, SOIL_Y, 0.1D);
            pose.scale(0.8F, 0.8F, 0.8F);
            blocks.renderSingleBlock(state, pose, buffers, light, overlay, ModelData.EMPTY, null);
        } else {
            float scale = 0.3F + 0.5F * growth;
            if (block != null && block.defaultBlockState().getRenderShape() == RenderShape.MODEL) {
                pose.translate(0.5D - scale / 2D, SOIL_Y, 0.5D - scale / 2D);
                pose.scale(scale, scale, scale);
                blocks.renderSingleBlock(block.defaultBlockState(), pose, buffers, light, overlay, ModelData.EMPTY, null);
            } else {
                pose.translate(0.5D, SOIL_Y + scale / 2D, 0.5D);
                pose.scale(scale, scale, scale);
                items.renderStatic(seed, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
            }
        }
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }
}
