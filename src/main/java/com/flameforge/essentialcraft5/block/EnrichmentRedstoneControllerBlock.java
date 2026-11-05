package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.EnrichmentRedstoneControllerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 富集仓红石控制器方块。对应原作 BlockMRUCUECRedstoneController：向所有方向输出弱信号 15 或 0；
 * 空手潜行右键切换设定、普通右键查看设定（提示显示在快捷栏上方）。逻辑见 {@link EnrichmentRedstoneControllerBlockEntity}。
 */
public class EnrichmentRedstoneControllerBlock extends EnrichmentPartBlock {

    public EnrichmentRedstoneControllerBlock(Properties properties) {
        super(properties, () -> ECEnrichment.EC_REDSTONE_CONTROLLER_BE.get());
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof EnrichmentRedstoneControllerBlockEntity be && be.isPowered() ? 15 : 0;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EnrichmentRedstoneControllerBlockEntity be) {
            if (player.isShiftKeyDown()) {
                be.cycleSetting();
            }
            Component message = be.settingMessage();
            if (!be.enrichmentLink().isLinked()) {
                message = message.copy().append(Component.translatable("message.essentialcraft5.ec_redstone_controller.unlinked"));
            }
            player.displayClientMessage(message, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ECEnrichment.EC_REDSTONE_CONTROLLER_BE.get(), EnrichmentRedstoneControllerBlockEntity::serverTick);
    }
}
