package com.flameforge.essentialcraft5.block.hoanna;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 海纳尔的晶簇（第十三批 13d：辐晶簇、影晶簇的共同基类）：朝向、含水、碰撞形状照原版紫水晶簇 {@code AmethystClusterBlock}
 * （贴在哪一面就朝哪边长，贴着的那一格没了就碎），只是不继承紫水晶块：被弹射物打中不响紫水晶的声音。
 * 状态属性 {@code facing}、{@code waterlogged} 是预备定下的（13d-H1b 的地物按它们写），之后不改；掉落、右键收取、粒子在子类。
 */
public class HoannaClusterBlock extends Block implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final VoxelShape upShape;
    private final VoxelShape downShape;
    private final VoxelShape northShape;
    private final VoxelShape southShape;
    private final VoxelShape eastShape;
    private final VoxelShape westShape;

    /**
     * @param height 从贴附面量起的高度（像素，原版紫水晶簇 7）
     * @param inset  四周往里缩的像素（原版 3）
     */
    public HoannaClusterBlock(int height, int inset, Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false).setValue(FACING, Direction.UP));
        upShape = Block.box(inset, 0D, inset, 16 - inset, height, 16 - inset);
        downShape = Block.box(inset, 16 - height, inset, 16 - inset, 16D, 16 - inset);
        northShape = Block.box(inset, inset, 16 - height, 16 - inset, 16 - inset, 16D);
        southShape = Block.box(inset, inset, 0D, 16 - inset, 16 - inset, height);
        eastShape = Block.box(0D, inset, inset, height, 16 - inset, 16 - inset);
        westShape = Block.box(16 - height, inset, inset, 16D, 16 - inset, 16 - inset);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> northShape;
            case SOUTH -> southShape;
            case EAST -> eastShape;
            case WEST -> westShape;
            case DOWN -> downShape;
            case UP -> upShape;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(WATERLOGGED, water).setValue(FACING, context.getClickedFace());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, FACING);
    }
}
