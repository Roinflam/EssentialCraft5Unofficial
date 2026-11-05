package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MagicalDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 魔力显示屏（原作 BlockMagicalDisplay，原名带 [NYI]）：贴在墙上、地上或天花板上的一块 0.8 像素薄板，展示一件物品。
 * <ul>
 *   <li>朝向 = 放下时点中的面（原作 L120~123），屏的正面朝它、背面贴着被点的那个方块。</li>
 *   <li>拿着物品右键：屏空时放上 1 个（生存扣 1 个；原作创造模式也扣）；屏上有东西时把它弹出来、不放新的（原作 L75~94）。</li>
 *   <li>潜行空手右键：取出（弹出来）。空手右键：切换显示方式 0 → 1 → 2 → 0（原作 L110~116）。</li>
 *   <li>弹出的物品从屏的正面出来（原作从方块中心生成，贴墙时会卡进墙里）；不看 doTileDrops（那是方块掉落的规则，取回自己的东西不该被它吞掉）。</li>
 *   <li>挖掉掉显示屏自己 + 屏上的物品（原作掉一块魔力屏幕、又没有配方，生存里拿不到）。</li>
 *   <li>不 tick。每次改动当场存盘并通知客户端（1.12 靠区块定期存盘兜底；1.20 不 setChanged 就不存，取出后重启东西又回来 = 复制）。</li>
 * </ul>
 * 右键逻辑只在服务端跑（原作客户端也跑一遍，先改自己的物品栏再等服务端纠正）；客户端直接返回成功，免得手上的方块被放出去。
 */
public class MagicalDisplayBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** 原作 getBoundingBox L125~147：0.05 格（0.8 像素）厚、贴在背面 */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.DOWN, Block.box(0D, 15.2D, 0D, 16D, 16D, 16D));
        SHAPES.put(Direction.UP, Block.box(0D, 0D, 0D, 16D, 0.8D, 16D));
        SHAPES.put(Direction.NORTH, Block.box(0D, 0D, 15.2D, 16D, 16D, 16D));
        SHAPES.put(Direction.SOUTH, Block.box(0D, 0D, 0D, 16D, 16D, 0.8D));
        SHAPES.put(Direction.WEST, Block.box(15.2D, 0D, 0D, 16D, 16D, 16D));
        SHAPES.put(Direction.EAST, Block.box(0D, 0D, 0D, 0.8D, 16D, 16D));
    }

    private final Supplier<BlockEntityType<MagicalDisplayBlockEntity>> blockEntityType;

    public MagicalDisplayBlock(Properties properties, Supplier<BlockEntityType<MagicalDisplayBlockEntity>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
        // 原作默认朝下（贴天花板）；默认状态只影响 /setblock 不写朝向和展示间，改成竖在墙上、正面朝北
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MagicalDisplayBlockEntity(blockEntityType.get(), pos, state);
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MagicalDisplayBlockEntity display)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            if (display.getDisplayed().isEmpty()) {
                // 先放副本、再扣手上的（反过来扣空了就拿不到副本）
                display.setDisplayed(held.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1F, 1F);
            } else if (display.ejectDisplayed()) {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1F, 1F);
            }
        } else if (player.isShiftKeyDown()) {
            if (display.ejectDisplayed()) {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1F, 1F);
            }
        } else {
            display.cycleMode();
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1F, 1F);
        }
        return InteractionResult.CONSUME;
    }

    /** 显示屏自己不 tick：更新包限频时由计划刻补发（{@link MagicalDisplayBlockEntity#flushPendingSync}） */
    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MagicalDisplayBlockEntity display) {
            display.flushPendingSync();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MagicalDisplayBlockEntity display) {
            ItemStack shown = display.takeDisplayed();
            if (!shown.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), shown);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
