package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.ElementalDecomposerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDecomposer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 元素分解器（EC5 新机器，第十二批 batch12.md §4.8）。水平朝向，正面（四色漏斗口）朝放置者；朝向只管外观，
 * 漏斗、管道从任何一面进出都一样。右键开界面、挖掉掉出物品栏由 {@link MachineBlock} 做，方块只掉自己（点数和计数不跟着走）。
 */
public class ElementalDecomposerBlock extends MachineBlock<ElementalDecomposerBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ElementalDecomposerBlock(Properties properties) {
        super(properties, false);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected BlockEntityType<ElementalDecomposerBlockEntity> blockEntityType() {
        return ECDecomposer.ELEMENTAL_DECOMPOSER_BE.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.elemental_decomposer").withStyle(ChatFormatting.GRAY));
    }
}
