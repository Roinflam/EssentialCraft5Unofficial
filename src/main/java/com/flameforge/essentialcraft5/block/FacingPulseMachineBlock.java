package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 带朝向、吃红石上升沿的机器方块（batch4 F4-8）。<b>目前没有使用者</b>：原本给魔力执行器、两种方块破坏器用，
 * 它们已随「机器不挖不放方块」去掉（2026-09-28，batch4 §0.0）；留给以后带朝向的脉冲机器。
 * = {@link FacingMachineBlock}（{@link #FACING}、{@link FacingRule}、{@code rotate / mirror}、{@code ticking}、主人状态）
 * + {@link RedstonePulse}（{@link #TRIGGERED}、上升沿、计划刻、方块实体实现 {@code RedstonePulseReceiver}）。
 * datagen 同 {@link FacingMachineBlock}：{@code directionalBlock} 的 {@code forAllStates} 自动覆盖 {@code triggered} 两种取值。
 */
public abstract class FacingPulseMachineBlock<E extends MRUBlockEntity> extends FacingMachineBlock<E> {

    public static final BooleanProperty TRIGGERED = RedstonePulse.TRIGGERED;

    protected FacingPulseMachineBlock(Properties properties, boolean transmitsSkylight, FacingRule rule, boolean ticking) {
        super(properties, transmitsSkylight, rule, ticking);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TRIGGERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TRIGGERED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : RedstonePulse.placementState(state, context);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        RedstonePulse.onPlace(state, level, pos, oldState);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        RedstonePulse.neighborChanged(state, level, pos);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        RedstonePulse.tick(state, level, pos);
    }
}
