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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 宝库锁孔（封印宝库前厅中央，batch13.md §6.4）：每次尝试先往这里放 1 个地脉结晶（这一次的钥匙，答对答错都不退）。不可破坏、活塞推不动。
 * <p>
 * 状态 {@link #KEYED}：这一次已经放了结晶（亮度 {@value #LIT_LIGHT}）。锁孔自己不存东西，右键交给宝库核心
 * （{@link VaultCoreBlockEntity#useKeyhole}，核心在锁孔北边 10 格）。只认真玩家的主手。
 */
public class VaultKeyholeBlock extends Block {

    public static final BooleanProperty KEYED = BooleanProperty.create("keyed");
    public static final int LIT_LIGHT = 7;

    public VaultKeyholeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KEYED, false));
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(KEYED) ? LIT_LIGHT : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KEYED);
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
        VaultCoreBlockEntity core = VaultCoreBlockEntity.fromKeyhole(level, pos);
        if (core == null) {
            return InteractionResult.PASS;
        }
        core.useKeyhole(server, player.getItemInHand(hand));
        return InteractionResult.CONSUME;
    }
}
