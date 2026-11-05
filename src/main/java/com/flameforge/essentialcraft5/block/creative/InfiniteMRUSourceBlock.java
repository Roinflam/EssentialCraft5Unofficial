package com.flameforge.essentialcraft5.block.creative;

import com.flameforge.essentialcraft5.block.MachineBlock;
import com.flameforge.essentialcraft5.blockentity.creative.InfiniteMRUSourceBlockEntity;
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
 * 无尽 MRU 源（只放创造栏）。逻辑在 {@link InfiniteMRUSourceBlockEntity}；没有界面。
 * 空手右键在动作栏报告上一轮给几台机器补了 MRU；手里拿着东西（绑定宝石要点它来绑定）时不拦。
 */
public class InfiniteMRUSourceBlock extends MachineBlock<InfiniteMRUSourceBlockEntity> {

    public static final String STATUS_KEY = "message.essentialcraft5.creative.infinite_mru_source";

    public InfiniteMRUSourceBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    protected BlockEntityType<InfiniteMRUSourceBlockEntity> blockEntityType() {
        return ECCreative.INFINITE_MRU_SOURCE_BE.get();
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
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof InfiniteMRUSourceBlockEntity source) {
            player.displayClientMessage(Component.translatable(STATUS_KEY, source.getLastFilled(),
                    InfiniteMRUSourceBlockEntity.RADIUS).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
