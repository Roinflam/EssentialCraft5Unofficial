package com.flameforge.essentialcraft5.block.vault;

import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 先辈碑（封印宝库地面平台上的 1 × 1 × 5 碑，batch13.md §6.4）：真玩家右键在聊天栏读出四句碑文。不可破坏、活塞推不动。
 * <p>
 * 碑文由宝库核心给（{@link VaultCoreBlockEntity#readRiddle}）：碑自己不存东西，右键时先找到碑的最下面一块，再到核心那一列往下找
 * （{@code VaultCoreBlockEntity.fromStele}，二十来格，只看已加载的区块）。找不到核心（创造模式单摆的碑）就说字迹看不清。
 */
public class ElderSteleBlock extends Block {

    public ElderSteleBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer server) || !ECProtection.isRealPlayer(player)) {
            return InteractionResult.PASS;
        }
        VaultCoreBlockEntity core = VaultCoreBlockEntity.fromStele(level, pos);
        if (core == null) {
            server.displayClientMessage(Component.translatable("message.essentialcraft5.sealed_vault.riddle.unreadable"), true);
        } else {
            core.readRiddle(server);
        }
        return InteractionResult.CONSUME;
    }
}
