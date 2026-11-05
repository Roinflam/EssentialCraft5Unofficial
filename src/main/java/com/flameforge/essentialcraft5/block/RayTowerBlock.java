package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.api.IBoundGemTarget;
import com.flameforge.essentialcraft5.blockentity.RayTowerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 射线塔：两格高的 MRU 中继站。对应原作 BlockRayTower（Material.IRON，setLightOpacity(0)，CUTOUT_MIPPED 渲染层）。
 * <p>
 * 上下两半是同一个方块的两个状态（原作 DummyCore 的 EnumLayer 属性 layer=bottom/top）。
 * 下半格放 JSON 模型（高 1.3 格，顶上还有个 BER 画的浮空水晶），有方块实体；上半格是隐形的，没有方块实体。
 * 右键上半格等于右键下半格，绑定宝石点上半格时绑到下半格（{@link IBoundGemTarget}）。
 * <p>
 * 放置和拆除照原版门的写法：
 * <ul>
 *   <li>上方能放东西才让放（原作要求上方是空气）；玩家放下时在 setPlacedBy 里补上半格，
 *       用指令、GameTest 等不经过玩家放的，在 onPlace 里补（原作 onBlockAdded）。</li>
 *   <li>任何一半没了另一半跟着没（updateShape）。战利品表只在下半格掉自己，所以拆哪一半都只掉一个。</li>
 *   <li>拆上半格时：创造模式、或者工具不对（要镐），先把下半格不掉落地清掉，免得下半格被连带拆掉时掉出东西。</li>
 *   <li>活塞推不动（两半会被推散）。</li>
 * </ul>
 * 碰撞箱：原作两半都是整格，这里按模型收窄到柱子的范围（1.6~14.4 像素），上半格留满高，方便点到顶上的水晶打开界面。
 */
public class RayTowerBlock extends MachineBlock<RayTowerBlockEntity> implements IBoundGemTarget {

    public enum Layer implements StringRepresentable {
        BOTTOM,
        TOP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Layer> LAYER = EnumProperty.create("layer", Layer.class);

    private static final VoxelShape SHAPE = Block.box(1.6D, 0D, 1.6D, 14.4D, 16D, 14.4D);

    public RayTowerBlock(Properties properties) {
        super(properties, true);
        registerDefaultState(stateDefinition.any().setValue(LAYER, Layer.BOTTOM));
    }

    @Override
    protected BlockEntityType<RayTowerBlockEntity> blockEntityType() {
        return ECGenerators.RAY_TOWER_BE.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYER);
    }

    public static boolean isBottom(BlockState state) {
        return state.getValue(LAYER) == Layer.BOTTOM;
    }

    /** 上半格不建方块实体（原作两半各有一个，上半格那个没用） */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isBottom(state) ? super.newBlockEntity(pos, state) : null;
    }

    @Override
    public BlockPos boundGemTarget(BlockGetter level, BlockPos pos, BlockState state) {
        return isBottom(state) ? pos : pos.below();
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ---- 放置 ----

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return defaultBlockState();
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (isBottom(state)) {
            level.setBlock(pos.above(), state.setValue(LAYER, Layer.TOP), Block.UPDATE_ALL);
        }
    }

    /** 不经过玩家放下的（/setblock、GameTest、结构）在这里补上半格；玩家放的此时上半格已经在了 */
    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide || !isBottom(state) || oldState.is(this)) {
            return;
        }
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (!level.isOutsideBuildHeight(above) && !aboveState.is(this) && aboveState.canBeReplaced()) {
            level.setBlock(above, state.setValue(LAYER, Layer.TOP), Block.UPDATE_ALL);
        }
    }

    // ---- 拆除 ----

    @SuppressWarnings("deprecation")
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        Layer layer = state.getValue(LAYER);
        // 朝向另一半的那一面：另一半不在了，自己也没了
        if (direction.getAxis() == Direction.Axis.Y && (layer == Layer.BOTTOM) == (direction == Direction.UP)) {
            return neighbor.is(this) && neighbor.getValue(LAYER) != layer ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !isBottom(state)) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            // 下半格会被 updateShape 连带拆掉并掉落；创造模式、或者工具不对时不该掉，先悄悄清掉（原版门、高花同样处理）
            if (belowState.is(this) && isBottom(belowState)
                    && (player.isCreative() || !player.hasCorrectToolForDrops(belowState))) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, below, Block.getId(belowState));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    // ---- 交互 ----

    /** 右键上半格等于右键下半格（原作 onBlockActivated 里 y-1） */
    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (isBottom(state)) {
            return super.use(state, level, pos, player, hand, hit);
        }
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.is(this) && isBottom(belowState)) {
            return super.use(belowState, level, below, player, hand, hit);
        }
        return InteractionResult.PASS;
    }
}
