package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.enrichment.EnrichmentControllers;
import com.flameforge.essentialcraft5.enrichment.EnrichmentPart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 没有界面的富集仓部件方块（存储方块、输出器；红石控制器继承它）。对应原作 BlockMRUCUECHoldingChamber / BlockMRUCUECEjector。
 * <p>
 * 放下、拆掉时通知附近的控制器重扫外壳（{@link EnrichmentControllers}）；拆掉时还告诉自己所属的控制器，
 * 控制器立即把它从部件表里去掉（存储方块立即扣容量）。
 * 原作 setLightOpacity(0) 的整格方块：1.20 里整格不透明方块照样挡光，这里不改（内部暗一些，不影响玩法）。
 */
public class EnrichmentPartBlock extends BaseEntityBlock {

    private final Supplier<? extends BlockEntityType<?>> type;

    public EnrichmentPartBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        super(properties);
        this.type = type;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.get().create(pos, state);
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
            removed(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** 部件方块被拆掉或换成别的方块时调用（方块实体还在）：通知控制器 */
    public static void removed(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof EnrichmentPart part) {
            part.enrichmentLink().onPartRemoved();
        }
        EnrichmentControllers.notifyChanged(level, pos);
    }
}
