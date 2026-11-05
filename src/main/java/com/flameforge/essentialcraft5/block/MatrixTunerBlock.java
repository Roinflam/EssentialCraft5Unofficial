package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MatrixTunerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMatrixTuner;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 矩阵调律器（EC5 新机器，第十二批，见 {@link MatrixTunerBlockEntity}）。右键开界面、挖掉掉出物品栏、记主人由 {@link MachineBlock} 做。
 * <p>
 * 顶面比整格低 3 像素（{@link MatrixTunerBlockEntity#TOP} = 13/16），站上去脚陷进去一点，一眼看得出「这是给人站的」；
 * 碰撞箱和外形一样（batch12.md §4.6.3）。底面是整面，下面的方块照常被它挡住；顶面不满，上面方块的底面照常画。
 */
public class MatrixTunerBlock extends MachineBlock<MatrixTunerBlockEntity> {

    private static final VoxelShape SHAPE = Block.box(0D, 0D, 0D, 16D, 13D, 16D);

    public MatrixTunerBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    protected BlockEntityType<MatrixTunerBlockEntity> blockEntityType() {
        return ECMatrixTuner.MATRIX_TUNER_BE.get();
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
        tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_tuner").withStyle(ChatFormatting.GRAY));
    }
}
