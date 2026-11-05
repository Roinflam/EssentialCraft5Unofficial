package com.flameforge.essentialcraft5.block.vault;

import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.VaultLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 宝库祭座（封印宝库前厅东南西北各一座，各刻一种元素，batch13.md §6.4）：按碑文的顺序往祭座放对应元素的元素魔晶。不可破坏、活塞推不动。
 * <p>
 * 状态：{@link #ELEMENT} 刻的元素（结构生成时按谜题定，只管样子，判对错看宝库核心里存的谜题）、{@link #FILLED} 这一次尝试里已经放满
 * （亮起来，亮度 {@value #LIT_LIGHT}）。祭座自己不存东西：右键时找锁孔（东南西北 3 格中的一格）→ 再找宝库核心，交给
 * {@link VaultCoreBlockEntity#usePedestal}。只认真玩家的主手；右键走原版交互事件，领地插件拦得到。
 */
public class VaultPedestalBlock extends Block {

    public static final EnumProperty<VaultElement> ELEMENT = EnumProperty.create("element", VaultElement.class);
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    /** 放满后的亮度 */
    public static final int LIT_LIGHT = 7;

    public VaultPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ELEMENT, VaultElement.FIRE).setValue(FILLED, false));
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(FILLED) ? LIT_LIGHT : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ELEMENT, FILLED);
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
        Direction side = VaultCoreBlockEntity.pedestalSide(level, pos);
        VaultCoreBlockEntity core = side == null ? null
                : VaultCoreBlockEntity.fromKeyhole(level, pos.relative(side.getOpposite(), VaultLayout.PEDESTAL_DISTANCE));
        if (core == null) {
            return InteractionResult.PASS;
        }
        core.usePedestal(server, player.getItemInHand(hand), side);
        return InteractionResult.CONSUME;
    }
}
