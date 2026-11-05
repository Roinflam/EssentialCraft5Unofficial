package com.flameforge.essentialcraft5.block.creative;

import com.flameforge.essentialcraft5.block.MachineBlock;
import com.flameforge.essentialcraft5.blockentity.creative.InfiniteESPESourceBlockEntity;
import com.flameforge.essentialcraft5.registry.ECCreative;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 无尽 ESPE 源（只放创造栏）。逻辑在 {@link InfiniteESPESourceBlockEntity}；没有界面。
 * 空手右键在动作栏报告上一轮补了几个；手里拿着东西时不拦（照常放方块、用物品）。
 */
public class InfiniteESPESourceBlock extends MachineBlock<InfiniteESPESourceBlockEntity> {

    public static final String STATUS_KEY = "message.essentialcraft5.creative.infinite_espe_source";

    public InfiniteESPESourceBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    protected BlockEntityType<InfiniteESPESourceBlockEntity> blockEntityType() {
        return ECCreative.INFINITE_ESPE_SOURCE_BE.get();
    }

    @Override
    protected boolean opensMenu() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof InfiniteESPESourceBlockEntity source) {
            player.displayClientMessage(Component.translatable(STATUS_KEY, source.getLastFilled(),
                    InfiniteESPESourceBlockEntity.RADIUS).withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
