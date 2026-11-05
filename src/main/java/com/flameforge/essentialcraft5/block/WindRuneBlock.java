package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.WindRuneBlockEntity;
import com.flameforge.essentialcraft5.registry.ECWind;
import net.minecraft.core.BlockPos;
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
 * 风之符文（原作 BlockWindRune + TileWindRune）：放在祭坛正中的薄片，结构完整时手持可注风的物品右键它，从祭坛的 8 根 ESPE 水晶柱里抽 ESPE 注风。
 * 结构见 {@link ECWind#RUNE_STRUCTURE}，注风逻辑见 {@link WindRuneBlockEntity#imbue}。
 * <p>
 * 外形照原作：1/16 格高、没有碰撞箱（Properties.noCollission），方块模型是空的，地上 3×3 的符文图案由 BER 在结构完整时画。
 * 原作 Material.ROCK 挡液体，这里靠 Properties.forceSolidOn（见 {@link ECWind#WIND_RUNE}）：水、岩浆流不进这一格，不会把符文冲掉或烧掉。
 * 右键只有主手算（原作只看主手）；手里不是注风原料时让给别的交互（比如拿着方块贴着符文放），空手右键报告祭坛状态（新增）；
 * 潜行空手右键问风之心愿、拿着风之药水潜行右键换心愿（第十批 10b，见 WindRuneBlockEntity 类注释）。
 * 不覆盖 neighborChanged 去给结构缓存标脏：紧挨符文的 6 格里只有正下方那块护板属于结构，其余 40 格邻居更新通知不到，
 * 统一靠方块实体每 20 tick 复查（StructureCache 默认）；右键时再立即判一次。
 */
public class WindRuneBlock extends BaseEntityBlock {

    /** 原作 BLOCK_AABB (0, 0, 0, 1, 0.0625, 1) */
    private static final VoxelShape SHAPE = Block.box(0D, 0D, 0D, 16D, 1D, 16D);

    public WindRuneBlock(Properties properties) {
        super(properties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** BaseEntityBlock 默认 INVISIBLE；改回 MODEL，破坏粒子要用空模型里的 particle 贴图（符文图案由 BER 画） */
    @SuppressWarnings("deprecation")
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindRuneBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ECWind.WIND_RUNE_BE.get(), WindRuneBlockEntity::clientTick)
                : createTickerHelper(type, ECWind.WIND_RUNE_BE.get(), WindRuneBlockEntity::serverTick);
    }

    /**
     * 原作 onBlockActivated 两端都执行注风（客户端也扣 ESPE 和物品）；这里只在服务端做，客户端只预判「这次右键会不会被符文吃掉」：
     * 主手是注风原料（客户端也有同步来的配方表）或空手时返回 SUCCESS（挥手、不再发「使用物品」包，免得水瓶被顺手喝掉），其余 PASS。
     */
    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()
                || !(level.getBlockEntity(pos) instanceof WindRuneBlockEntity rune)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return rune.wouldHandle(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        WindRuneBlockEntity.Outcome outcome = rune.imbue(player);
        return outcome == WindRuneBlockEntity.Outcome.PASS ? InteractionResult.PASS : InteractionResult.CONSUME;
    }
}
