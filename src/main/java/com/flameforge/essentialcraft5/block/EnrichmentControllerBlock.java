package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.enrichment.EnrichmentTrigger;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import com.flameforge.essentialcraft5.structure.StructureResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 富集仓控制器方块。对应原作 BlockMRUCUECController：没有界面，右键立即检查外壳。
 * 这里把检查结果发给右键的玩家（成形：尺寸、存储方块数、存量；不成形：哪里不对），成形时算进度「收容」。
 */
public class EnrichmentControllerBlock extends BaseEntityBlock {

    public EnrichmentControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnrichmentControllerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ECEnrichment.EC_CONTROLLER_BE.get(), EnrichmentControllerBlockEntity::clientTick)
                : createTickerHelper(type, ECEnrichment.EC_CONTROLLER_BE.get(), EnrichmentControllerBlockEntity::serverTick);
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof EnrichmentControllerBlockEntity be) {
            StructureResult result = be.checkForPlayer();
            for (Component line : be.describe(result)) {
                serverPlayer.displayClientMessage(line, false);
            }
            if (be.isFormed()) {
                EnrichmentTrigger.INSTANCE.trigger(serverPlayer, be.chambers());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof EnrichmentControllerBlockEntity be) {
            be.onBroken();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
