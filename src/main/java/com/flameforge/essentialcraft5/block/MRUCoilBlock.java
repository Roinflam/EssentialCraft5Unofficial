package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MRUCoilBlockEntity;
import com.flameforge.essentialcraft5.registry.ECTesla;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * MRU 特斯拉线圈。对应原作 BlockMRUCoil（106 行）。
 * <p>
 * 和原作不同：
 * <ul>
 *   <li><b>界面只给主人、白名单上的人和创造模式</b>（服务端 {@code isCreative()}）：原作放了白名单时「没有权限」的提示对名单上的人也发
 *       （L89 在 if 外），不放白名单谁都能开。被拒的人只在动作栏看到「这是谁的线圈」，不开界面。白名单上的人只能看，
 *       0、1 号格只有主人和创造模式的人能拿放（见 MRUCoilMenu）。</li>
 *   <li>碰撞形状照原作模型 mrucoil.json 的 18 个方块（原作是整格碰撞箱，模型却是镂空的一圈圈框）。</li>
 *   <li>邻居变化时让方块实体重看红石和结构（原作每 20 tick 一次、红石每 tick 查）。</li>
 * </ul>
 * 原作 setLightOpacity(0)：{@code transmitsSkylight = true}。
 */
public class MRUCoilBlock extends MachineBlock<MRUCoilBlockEntity> {

    /** 原作 models/block/mrucoil.json 的 18 个元素（像素） */
    private static final VoxelShape SHAPE = Shapes.or(
            box(4.8, 0, 4.8, 11.2, 3.2, 11.2),
            box(7.2, 3.2, 7.2, 8.8, 12.8, 8.8),
            box(0, 0, 14.4, 16, 1.6, 16),
            box(14.4, 0, 0, 16, 1.6, 16),
            box(0, 0, 0, 1.6, 1.6, 16),
            box(0, 0, 0, 16, 1.6, 1.6),
            box(1.6, 3.2, 12.8, 14.4, 4.8, 14.4),
            box(12.8, 3.2, 1.6, 14.4, 4.8, 14.4),
            box(1.6, 3.2, 1.6, 3.2, 4.8, 14.4),
            box(1.6, 3.2, 1.6, 14.4, 4.8, 3.2),
            box(3.2, 6.4, 11.2, 12.8, 8, 12.8),
            box(11.2, 6.4, 3.2, 12.8, 8, 12.8),
            box(3.2, 6.4, 3.2, 4.8, 8, 12.8),
            box(3.2, 6.4, 3.2, 12.8, 8, 4.8),
            box(4.8, 9.6, 9.6, 11.2, 11.2, 11.2),
            box(9.6, 9.6, 4.8, 11.2, 11.2, 11.2),
            box(4.8, 9.6, 4.8, 6.4, 11.2, 11.2),
            box(4.8, 9.6, 4.8, 11.2, 11.2, 6.4));

    public MRUCoilBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<MRUCoilBlockEntity> blockEntityType() {
        return ECTesla.MRU_COIL_BE.get();
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * 右键：潜行不开（同原作）。主人、白名单上的人、创造模式的人打开界面；别人只在动作栏看到「这是谁的线圈」，返回 CONSUME（不开、也不往副手传）。
     * 访问权在服务端判断（名单和主人都只在服务端有）；客户端照常返回成功（挥手），和原版箱子一样。
     */
    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof MRUCoilBlockEntity coil)) {
            return InteractionResult.PASS;
        }
        if (!coil.canView(serverPlayer)) {
            String owner = ECProtection.ownerName(coil);
            Component message = owner.isEmpty()
                    ? Component.translatable("message.essentialcraft5.mru_coil.no_access.no_owner")
                    : Component.translatable("message.essentialcraft5.mru_coil.no_access", owner);
            serverPlayer.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(serverPlayer, coil, pos);
        return InteractionResult.CONSUME;
    }

    /** 红石、正下方的基座变化：让方块实体马上重看红石；正下方那格变了就尽快重看结构（底座离得远，另有每 20 tick 的复查兜底） */
    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MRUCoilBlockEntity coil) {
            coil.onNeighborChanged(fromPos);
        }
    }
}
