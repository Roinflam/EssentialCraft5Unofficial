package com.flameforge.essentialcraft5.block.vault;

import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 封印石（封印宝库前厅和宝库室之间 3 × 3 的封印门，batch13.md §6.4）：答对时九块一起切到 {@link #OPEN}（没有碰撞，人能走过去），
 * {@code door_open_ticks}（600 刻）后自己关上；宝库自己的方块改自己的状态。不可破坏、活塞推不动。
 * <p>
 * 关门用计划刻（宝库核心开门时排一次）：到点先问核心门还要开多久（中途又被打开过就再排一次）；门洞里站着生物时晚
 * {@value #BLOCKED_RETRY} 刻再关，不把人关进石头里。关着的封印石：解开名单上的人、或者人在宝库室里面（挖墙进来出不去的）
 * 右键能把门再打开（{@link VaultCoreBlockEntity#openFromSeal}）。
 */
public class VaultSealBlock extends Block {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** 门洞里有生物时晚多少刻再关 */
    public static final int BLOCKED_RETRY = 20;

    public VaultSealBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    /** 开着的门洞没有选框：准星能穿过去点到门后的宝库核心 */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return state.getValue(OPEN) && type == PathComputationType.LAND;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPEN)) {
            return;
        }
        VaultCoreBlockEntity core = VaultCoreBlockEntity.fromSeal(level, pos);
        long remaining = core == null ? 0L : core.openRemaining(level);
        if (remaining > 0L) {
            level.scheduleTick(pos, this, (int) remaining);
            return;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
            level.scheduleTick(pos, this, BLOCKED_RETRY);
            return;
        }
        level.setBlock(pos, state.setValue(OPEN, false), Block.UPDATE_ALL);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || state.getValue(OPEN)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer server) || !ECProtection.isRealPlayer(player)) {
            return InteractionResult.PASS;
        }
        VaultCoreBlockEntity core = VaultCoreBlockEntity.fromSeal(level, pos);
        if (core == null) {
            return InteractionResult.PASS;
        }
        core.openFromSeal(server);
        return InteractionResult.CONSUME;
    }
}
