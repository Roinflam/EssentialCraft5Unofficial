package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMatrix;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 矩阵淬炼台（第十三批 13a，batch13.md §3.1.2，见 {@link MatrixCrucibleBlockEntity}）。右键开界面、挖掉掉出物品栏、记主人由 {@link MachineBlock} 做；
 * 拿着绑定自己的灵魂石潜行右键是「存试炼储备」，在 {@code matrix/MatrixTrials} 的交互事件里处理（潜行时原版不调方块的 use）。
 * <p>
 * 外形是 12/16 高的台座（底座、柱身、台面三层），碰撞箱和外形一样；台座上方转的灵魂石是方块实体渲染器画的，不占碰撞。
 */
public class MatrixCrucibleBlock extends MachineBlock<MatrixCrucibleBlockEntity> {

    /** 和模型一样的三层：底座、柱身、台面 */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0D, 0D, 0D, 16D, 4D, 16D), Block.box(3D, 4D, 3D, 13D, 9D, 13D),
            Block.box(1D, 9D, 1D, 15D, 12D, 15D));

    public MatrixCrucibleBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    protected BlockEntityType<MatrixCrucibleBlockEntity> blockEntityType() {
        return ECMatrix.MATRIX_CRUCIBLE_BE.get();
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_crucible").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_crucible.reserve").withStyle(ChatFormatting.DARK_GRAY));
    }
}
