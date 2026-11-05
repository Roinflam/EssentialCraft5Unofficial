package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 机器方块基类。对应原作各个 BlockContainer 子类里重复的那几段：
 * 右键打开界面（潜行时不开）、被破坏时掉出物品栏。另外记下放置者当主人（{@link #setPlacedBy}，原作没有）。
 * <p>
 * {@code transmitsSkylight}：原作 setLightOpacity(0) 的机器传 true。1.20.1 光靠 noOcclusion 仍会挡 1 级光、挡住天空，
 * 叠起来的月亮井只有最上面那个能发电；传 true 后天空光直接穿过，相邻方块的 AO 也和原作一样（isFullCube=false 时为 1.0）。
 * <p>
 * 注意 BaseEntityBlock 默认渲染形状是 INVISIBLE，这里改回 MODEL，否则方块隐形。
 */
public abstract class MachineBlock<E extends MRUBlockEntity> extends BaseEntityBlock {

    private final boolean transmitsSkylight;

    protected MachineBlock(Properties properties, boolean transmitsSkylight) {
        super(properties);
        this.transmitsSkylight = transmitsSkylight;
    }

    protected abstract BlockEntityType<E> blockEntityType();

    /** 右键是否打开界面（创造 MRU 源之类没有界面的返回 false） */
    protected boolean opensMenu() {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return transmitsSkylight || super.propagatesSkylightDown(state, level, pos);
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return transmitsSkylight ? 1F : super.getShadeBrightness(state, level, pos);
    }

    /**
     * 不让原版拿方块实体当 MenuProvider：旁观者右键时原版会走 ServerPlayer.openMenu（不带额外数据），
     * 客户端菜单工厂读不到坐标就崩。界面只从 {@link #use} 里的 NetworkHooks.openScreen 打开。
     */
    @SuppressWarnings("deprecation")
    @Override
    public @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return null;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType().create(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, blockEntityType(), MRUBlockEntity::tickClient)
                : createTickerHelper(type, blockEntityType(), MRUBlockEntity::tickServer);
    }

    /** 邻居变了（含红石信号）：方块实体缓存的红石状态作废（{@link MRUBlockEntity#neighborPowered}）。子类覆盖时先调 super */
    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        // 两端都作废：自然熔炉这类的客户端粒子也要问红石信号
        if (level.getBlockEntity(pos) instanceof MRUBlockEntity be) {
            be.invalidatePower();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!opensMenu() || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MRUBlockEntity be) {
            NetworkHooks.openScreen(serverPlayer, be, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 记主人（policy §2.3 第 1 条）：放置者是真玩家（不是 FakePlayer 的 ServerPlayer）就把他记成主人，否则清空。
     * 只有经过 BlockItem 放下的才会走到这里；/setblock、结构生成、别的模组直接 setBlock 的机器没有主人，会动世界的机器因此不动世界。
     * 子类覆盖（RayTowerBlock 补上半格）时先调 super。
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MRUBlockEntity be) {
            ECProtection.recordOwner(be, placer);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 同一方块只是换了状态时也会进来，不判断会把物品栏刷出来
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MRUBlockEntity be) {
            be.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
