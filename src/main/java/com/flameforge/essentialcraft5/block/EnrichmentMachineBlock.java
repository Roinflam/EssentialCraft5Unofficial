package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.enrichment.EnrichmentControllers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 有界面的富集仓部件方块（接收器、状态检测器）。对应原作 BlockMRUCUECAcceptor / BlockMRUCUECStateChecker：
 * 右键开界面（潜行不开）、拆掉时掉出物品栏（MachineBlock），另外和 {@link EnrichmentPartBlock} 一样在放下 / 拆掉时通知控制器。
 *
 * @param <E> 方块实体（同时实现 EnrichmentPart）
 */
public class EnrichmentMachineBlock<E extends MRUBlockEntity> extends MachineBlock<E> {

    private final Supplier<BlockEntityType<E>> type;
    private final boolean ticking;

    /** @param ticking 方块实体要不要 tick（状态检测器只在界面打开时被读数，不用 tick） */
    public EnrichmentMachineBlock(Properties properties, Supplier<BlockEntityType<E>> type, boolean ticking) {
        super(properties, false);
        this.type = type;
        this.ticking = ticking;
    }

    @Override
    protected BlockEntityType<E> blockEntityType() {
        return type.get();
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return ticking ? super.getTicker(level, state, blockEntityType) : null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            EnrichmentControllers.notifyChanged(level, pos);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            EnrichmentPartBlock.removed(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
