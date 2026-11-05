package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 带 6 向朝向、不吃上升沿的机器方块（batch4 F4-8）：魔力漏斗、红石传递器（漏斗的红石是「有信号就停」的电平开关，用 {@link RedstonePulse#isPowered}）。
 * 要朝向又要上升沿的用 {@link FacingPulseMachineBlock}（它继承这个类）。
 * <p>
 * 朝向在放下时按 {@link FacingRule} 定（构造参数；要特殊规则就覆盖 {@link #facingFor}），{@code rotate / mirror} 照原版 {@code DirectionalBlock}
 * （结构方块旋转、{@code /clone} 镜像时跟着转）。{@code ticking} 同 {@link PulseMachineBlock}。没有界面时空手右键显示主人状态。
 * datagen：{@code directionalBlock(block, model)}（按 {@link #FACING} 转模型；{@code forAllStates} 自动覆盖别的属性）。
 */
public abstract class FacingMachineBlock<E extends MRUBlockEntity> extends MachineBlock<E> {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final FacingRule rule;
    private final boolean ticking;

    /**
     * @param transmitsSkylight 同 {@link MachineBlock}
     * @param rule              放下时的朝向规则
     * @param ticking           方块实体要不要 tick（见 {@link PulseMachineBlock} 类注释）
     */
    protected FacingMachineBlock(Properties properties, boolean transmitsSkylight, FacingRule rule, boolean ticking) {
        super(properties, transmitsSkylight);
        this.rule = rule;
        this.ticking = ticking;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** 方块状态里的朝向（没有这个属性的状态返回北） */
    public static Direction facing(BlockState state) {
        return state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
    }

    public FacingRule facingRule() {
        return rule;
    }

    /** 方块实体是否 tick（构造参数） */
    public boolean isTicking() {
        return ticking;
    }

    /** 放下时的朝向（默认按构造时的 {@link FacingRule}） */
    protected Direction facingFor(BlockPlaceContext context) {
        return rule.apply(context);
    }

    /** 空手右键显示主人状态（默认：没有界面的机器才显示） */
    protected boolean showsOwnerStatus() {
        return !opensMenu();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, facingFor(context));
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
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return ticking ? super.getTicker(level, state, type) : null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (showsOwnerStatus()) {
            return MachineOwnerStatus.use(level, pos, player, hand);
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
