package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.DemonicPentacleBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDemon;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 恶魔五芒星（原作 BlockDemonicPentacle）：地上一张 1/16 高、没有碰撞的符文。搭好结构（{@link ECDemon#STRUCTURE}）后右键，
 * 从 16 根水晶柱里抽 ESPE 召唤一只恶魔（{@link DemonicPentacleBlockEntity#summon}）。
 * <p>
 * 和原作的区别：原作右键只看 ESPE 够不够，不查结构、不查已有恶魔、不查头顶，客户端也跟着扣一次；这里只在服务端判，结构完整、头顶有空、
 * 一阵一只、交易池不空、ESPE 够都满足才扣，不够时快捷栏写清缺什么（policy §4.5）。召唤只认真玩家：假玩家召出来的恶魔记的是假玩家的 UUID，
 * 没有哪个真玩家对得上，除了创造模式谁都交易不了、打发不走（ESPE 白花，阵上多一只赶不走的恶魔）；有的模组的假玩家借用主人的 UUID，
 * 又等于机器替人召唤。
 * 不透光度 0（原作），天空光直接穿过。没有碰撞箱，但靠 forceSolidOn 挡液体（原作 Material.ROCK，见 {@link ECDemon#DEMONIC_PENTACLE}）。
 */
public class DemonicPentacleBlock extends BaseEntityBlock {

    /** 原作 BLOCK_AABB (0, 0, 0, 1, 0.0625, 1)；碰撞箱为空（noCollission） */
    private static final VoxelShape SHAPE = Block.box(0D, 0D, 0D, 16D, 1D, 16D);

    public DemonicPentacleBlock(Properties properties) {
        super(properties);
    }

    /** BaseEntityBlock 默认不画方块模型；空模型只提供破坏粒子 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DemonicPentacleBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ECDemon.DEMONIC_PENTACLE_BE.get(), (l, p, s, be) -> be.clientTick())
                : createTickerHelper(type, ECDemon.DEMONIC_PENTACLE_BE.get(), (l, p, s, be) -> be.serverTick());
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !ECProtection.isRealPlayer(serverPlayer)
                || !(level.getBlockEntity(pos) instanceof DemonicPentacleBlockEntity be)) {
            return InteractionResult.PASS;
        }
        DemonicPentacleBlockEntity.Outcome outcome = be.summon(serverPlayer);
        if (outcome.message() != null) {
            serverPlayer.displayClientMessage(outcome.message(), true);
        }
        return InteractionResult.CONSUME;
    }

    /** 紧挨着的方块变了（正下方的虚空石被拆之类）：结构下一次判定立即重算 */
    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DemonicPentacleBlockEntity be) {
            be.structure().markDirty();
        }
    }
}
