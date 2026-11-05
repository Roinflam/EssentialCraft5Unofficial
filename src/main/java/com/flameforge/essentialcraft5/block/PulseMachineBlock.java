package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 吃红石上升沿、不带朝向的机器方块（batch4 F4-8）：洗牌器、繁殖器、剪毛器、动物分离器、合成器。
 * 方块状态只有 {@link #TRIGGERED}；上升沿逻辑全在 {@link RedstonePulse}，方块实体实现 {@code RedstonePulseReceiver}。
 * <p>
 * {@code ticking}（照 {@code EnrichmentMachineBlock}）：{@link MachineBlock#getTicker} 对所有机器都返回 {@code tickServer}，
 * 纯脉冲、不从绑定宝石拉 MRU 的（农牧设备、合成器）传 false，{@link #getTicker} 返回 null，不白 tick；
 * 要从绑定宝石拉 MRU 或定时干活的（分离器）传 true。不 tick 的机器 {@code MRUBlockEntity} 的「物品栏变了发更新包」不会自动发生（它在 tick 末尾做），要同步就自己 {@code sendBlockUpdated}。
 * <p>
 * 没有界面（{@link #opensMenu()} 为假）时空手右键显示主人状态（{@link MachineOwnerStatus}）。
 * datagen：方块状态多了 {@code triggered}，{@code simpleBlock} / {@code cubeAll} 之类不带属性的写法自动覆盖两种取值，不用改。
 */
public abstract class PulseMachineBlock<E extends MRUBlockEntity> extends MachineBlock<E> {

    public static final BooleanProperty TRIGGERED = RedstonePulse.TRIGGERED;

    private final boolean ticking;

    /**
     * @param transmitsSkylight 同 {@link MachineBlock}（原作 setLightOpacity(0) 的传 true）
     * @param ticking           方块实体要不要 tick（见类注释）
     */
    protected PulseMachineBlock(Properties properties, boolean transmitsSkylight, boolean ticking) {
        super(properties, transmitsSkylight);
        this.ticking = ticking;
        registerDefaultState(stateDefinition.any().setValue(TRIGGERED, false));
    }

    /** 方块实体是否 tick（构造参数） */
    public boolean isTicking() {
        return ticking;
    }

    /** 空手右键显示主人状态（默认：没有界面的机器才显示） */
    protected boolean showsOwnerStatus() {
        return !opensMenu();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TRIGGERED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return RedstonePulse.placementState(defaultBlockState(), context);
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
