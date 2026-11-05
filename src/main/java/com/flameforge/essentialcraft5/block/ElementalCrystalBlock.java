package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.ElementalCrystalBlockEntity;
import com.flameforge.essentialcraft5.item.ElementalCrystalItem;
import com.flameforge.essentialcraft5.registry.ECCrystal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 元素水晶。对应原作 BlockElementalCrystal + TileElementalCrystal 的支撑判断。
 * <p>
 * {@link #FACING} 是水晶朝外长的方向（原作 meta = EnumFacing 序号，放置时取点击的那一面）：UP 立在地上，DOWN 挂在天花板上，
 * 支撑方块在反方向那一格，要求它朝着水晶的那一面是完整的（原作 isSideSolid）。
 * 水晶本体由 BER 画（{@link RenderShape#INVISIBLE}，模型只有破坏粒子），碰撞箱是整格（原作如此）；
 * 但照原作（isFullCube=false）不导红石、不算窒息、不挡视线，方块属性里显式关掉，见 ECCrystal.ELEMENTAL_CRYSTAL。
 * <p>
 * 亮度 = 大小 / 100 × 15（原作 getLightValue 读方块实体）。1.20.1 的光照引擎只在方块状态变化时重算，
 * 所以亮度放进方块状态 {@link #LIGHT}：放置时按物品里的大小算，长大时由方块实体跨过整数档才改一次（约每 3 333 tick）。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li><b>支撑方块没了掉的水晶保留数据</b>（原作 bug）：原作每 tick 检查支撑，没了就 dropBlockAsItem，走的是不带 NBT 的默认掉落，
 *       长了 40 分钟的水晶变回大小 0。这里照原版紫水晶簇的写法放进 {@link #canSurvive} / {@link #updateShape}（只在邻居变化时判断，不再每 tick 查），
 *       拆掉支撑时方块经 destroyBlock 按战利品表掉落，和挖下来一样带上大小和四元素。</li>
 *   <li>中键选取：原作 getPickBlock 带上数据，这里同样（{@link #getCloneItemStack}）。</li>
 * </ul>
 */
public class ElementalCrystalBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** 当前亮度 0~15（= 大小 / 100 × 15，向下取整） */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);

    public ElementalCrystalBlock(Properties properties) {
        super(properties);
        // 默认立在地上（DevShowroom 等直接放默认状态的地方，下面是地面才站得住）
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(LIGHT, 0));
    }

    /** 方块属性的 lightLevel 用 */
    public static int lightEmission(BlockState state) {
        return state.getValue(LIGHT);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIGHT);
    }

    @SuppressWarnings("deprecation")
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** 原作不透光度 0：天空光直接穿过（整格碰撞箱的方块默认会挡住天空） */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1F;
    }

    // ------------------------------------------------------------------
    // 放置与支撑
    // ------------------------------------------------------------------

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // 原作 getStateForPlacement 取点击的那一面；BlockItem 随后用 canSurvive 判断能不能放
        ItemStack stack = context.getItemInHand();
        int light = ElementalCrystalBlockEntity.lightFor(ElementalCrystalItem.size(stack));
        return defaultBlockState().setValue(FACING, context.getClickedFace()).setValue(LIGHT, light);
    }

    /** 支撑方块朝着水晶的那一面要完整（原作 isSideSolid(world, 支撑坐标, 朝向)） */
    @SuppressWarnings("deprecation")
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    /** 支撑方块变了且站不住时变成空气：原版 updateOrDestroy 会走 destroyBlock（带方块实体的战利品表，数据跟着掉） */
    @SuppressWarnings("deprecation")
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
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

    /** 中键选取带上大小和元素（原作 getPickBlock）；客户端方块实体的数据由服务端同步 */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof ElementalCrystalBlockEntity crystal) {
            ElementalCrystalItem.writeData(stack, crystal.saveData());
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // 方块实体
    // ------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElementalCrystalBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ECCrystal.ELEMENTAL_CRYSTAL_BE.get(), ElementalCrystalBlockEntity::clientTick)
                : createTickerHelper(type, ECCrystal.ELEMENTAL_CRYSTAL_BE.get(), ElementalCrystalBlockEntity::serverTick);
    }
}
