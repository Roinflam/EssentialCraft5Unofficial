package com.flameforge.essentialcraft5.block.vault;

import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 宝库核心（封印宝库室中央，batch13.md §6.4）：谜题状态、解开名单、已领名单都存在它的方块实体里（{@link VaultCoreBlockEntity}）；
 * 名单上的真玩家右键领一份 {@code chests/sealed_vault_reward}，每人每座一次。不可破坏、活塞推不动；不 tick（门的自动关用封印石的计划刻）。
 */
public class VaultCoreBlock extends Block implements EntityBlock {

    public VaultCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VaultCoreBlockEntity(pos, state);
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
        if (!(player instanceof ServerPlayer server) || !ECProtection.isRealPlayer(player)
                || !(level.getBlockEntity(pos) instanceof VaultCoreBlockEntity core)) {
            return InteractionResult.PASS;
        }
        core.claim(server);
        return InteractionResult.CONSUME;
    }
}
