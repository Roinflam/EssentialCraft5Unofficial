package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.ReactorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * MRU 特斯拉反应堆核心。对应原作 BlockMRUReactor（Material.ROCK、MapColor.PURPLE、OBJ 模型 mrureactor_btm、不透光，没有界面）。
 * 逻辑全在 {@link ReactorBlockEntity}（结构、产 MRU、平衡值、电怪）。
 * <p>
 * 方块状态 {@link #FORMED}：结构成形与否，<b>只在翻转时</b>由方块实体 setBlock（原作每 20 刻复查都让客户端重画 3×1×3，L135）。
 * 两侧的支架按它画电栏（{@link ReactorSupportBlock}），渲染器按它决定浮球的位置和颜色、要不要冒电弧；这个状态本身随区块同步，不另发包。
 * <p>
 * 空手右键：在动作栏写结构对不对（不对就写第一个不对的格子）、存了多少 MRU——原作没有界面，也没有任何提示。
 * 拿着东西右键照常交给物品（绑定宝石绑到这台反应堆上就能把 MRU 拉走）。
 */
public class ReactorBlock extends MachineBlock<ReactorBlockEntity> {

    /** 结构成形（照原作 isStructureCorrect） */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public ReactorBlock(Properties properties) {
        // 原作 setLightOpacity(0)：天空光直接穿过
        super(properties, true);
        registerDefaultState(stateDefinition.any().setValue(FORMED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected BlockEntityType<ReactorBlockEntity> blockEntityType() {
        return ECReactor.REACTOR_BE.get();
    }

    /** 原作没有界面 */
    @Override
    protected boolean opensMenu() {
        return false;
    }

    /** 状态里的 FORMED 是否为真（别的方块的状态返回 false） */
    public static boolean isFormed(BlockState state) {
        return state.getBlock() instanceof ReactorBlock && state.getValue(FORMED);
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // 只有空着主手时才接这次右键；拿着东西（绑定宝石、要贴着它放的方块）交给物品
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ReactorBlockEntity be) {
            player.displayClientMessage(be.statusMessage(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 紧挨着的格子变了（支架、核心上方那格）：结构缓存标脏，过了最小间隔就重算（离得远的格子靠 20 刻复查） */
    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ReactorBlockEntity be) {
            be.markStructureDirty();
        }
    }
}
