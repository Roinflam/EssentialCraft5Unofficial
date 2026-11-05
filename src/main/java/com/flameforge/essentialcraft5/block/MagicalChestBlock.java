package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MagicalChestBlockEntity;
import com.flameforge.essentialcraft5.menu.MagicalChestMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 魔力箱与虚空箱（原作 BlockChestEC 的 MAGICAL / VOID 两种 meta）：同一个类按 {@link Layout} 各注册一个方块（ECStorage）。
 * <p>
 * 一格大小的 54 格（虚空箱 117 格）箱子。<b>不是 MRU 机器</b>（原作 TileMagicalChest 就不是）：不继承 {@code MachineBlock}，
 * 方块实体也不继承 {@code MRUBlockEntity}——后者的更新包是整份存档数据，会把整箱物品广播给附近所有客户端（原作 L161~170 的毛病）。
 * <ul>
 *   <li>朝向存在方块状态里（原作存在方块实体的 rotation 字段，结构旋转、/clone 不跟着转）：放下时正面朝着放置者，等价原作的 rotation×90+180。</li>
 *   <li>形状对齐箱子模型 1~15 × 0~14 像素，和原版箱子一样（原作是整格碰撞箱、模型却只有 14 像素）。</li>
 *   <li>右键开界面，潜行不开（原作 onBlockActivated 潜行返回 false）。界面用原版 {@code openMenu}：菜单的客户端构造不读任何额外数据，
 *       所以旁观者也能像原版箱子那样看（{@link #getMenuProvider}），看的人不算进开合人数。</li>
 *   <li>挖掉撒出内容（原作 breakBlock → dropInventoryItems），掉落物本身不带内容。</li>
 *   <li>比较器输出（原作没有，原版箱子有）。</li>
 *   <li>开合：原版 ContainerOpenersCounter，只在人数变化时发一次方块事件；服务端不 tick，盖子动画只在客户端算。</li>
 * </ul>
 */
public class MagicalChestBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(1D, 0D, 1D, 15D, 14D, 15D);

    /**
     * 箱子的规格：格数、界面坐标和贴图、方块实体渲染用的模型贴图。
     *
     * @param slotX / slotY 第一格左上角（原作 8 + 列×18、18 + 行×18）
     * @param invX / invY   玩家物品栏第一格（快捷栏在 invY + 58）；原作魔力箱是 (8, 140)，比原版 ChestMenu 的 139 低 1 像素，照贴图
     * @param labels        界面上写不写标题和「物品栏」字样：贴图在箱子格上方、玩家物品栏上方留了写字的空才写
     */
    public record Layout(int rows, int cols, int slotX, int slotY, int invX, int invY, int width, int height, boolean labels,
                         ResourceLocation guiTexture, ResourceLocation modelTexture) {

        public static final Layout MAGICAL = new Layout(6, 9, 8, 18, 8, 140, 176, 222, true,
                EssentialCraft5.id("textures/gui/magical_chest.png"), EssentialCraft5.id("textures/block/chests/magical.png"));

        /**
         * 虚空箱（原作 GuiMagicalChest / ContainerMagicalChest 的另一组数值，按贴图像素核过）：9 行 13 列在 (12, 8)（12 + 列×18、8 + 行×18），
         * 玩家物品栏在原作默认的 (8, 84) 上再偏移 (40, 90) = (48, 174)，快捷栏 y 232；界面 256 × 256。
         * 贴图格子从 y 8 就开始、物品栏上方也没留空（最后一行箱子格到 168，物品栏 174 起），所以不写字（原作也不写）。
         * 界面 256 高也不用缩：1.20.1 选界面倍率时保证缩放后高度 ≥ 240（{@code Window.calculateScale}），格子区（y 8~248）正好 240 高，
         * 最坏只裁掉上下各 8 像素的边框；只有强制 Unicode 字体（奇数倍率被加 1）或窗口本身不到 240 像素高时才会更小，这时原版大箱子同样被裁。
         */
        public static final Layout VOID = new Layout(9, 13, 12, 8, 48, 174, 256, 256, false,
                EssentialCraft5.id("textures/gui/void_chest.png"), EssentialCraft5.id("textures/block/chests/void.png"));

        public int slots() {
            return rows * cols;
        }
    }

    private final Layout layout;
    private final Supplier<BlockEntityType<MagicalChestBlockEntity>> blockEntityType;
    private final Supplier<MenuType<MagicalChestMenu>> menuType;

    public MagicalChestBlock(Properties properties, Layout layout, Supplier<BlockEntityType<MagicalChestBlockEntity>> blockEntityType,
                             Supplier<MenuType<MagicalChestMenu>> menuType) {
        super(properties);
        this.layout = layout;
        this.blockEntityType = blockEntityType;
        this.menuType = menuType;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Layout layout() {
        return layout;
    }

    public MenuType<MagicalChestMenu> menuType() {
        return menuType.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return SHAPE;
    }

    /** 方块模型只有破坏粒子，箱子由方块实体渲染器画（原作渲染类型 INVISIBLE + TESR） */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MagicalChestBlockEntity(blockEntityType.get(), pos, state);
    }

    /** 服务端不 tick（开合人数靠计划刻复核）；客户端每 tick 推进盖子动画 */
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, blockEntityType.get(), MagicalChestBlockEntity::lidAnimateTick) : null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MagicalChestBlockEntity chest) {
            player.openMenu(chest);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 旁观者右键走原版的这条路（和原版箱子一样能看、不能拿，也不算开合人数） */
    @SuppressWarnings("deprecation")
    @Override
    public @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MagicalChestBlockEntity chest ? chest : null;
    }

    /** 开合人数的复核（原版 ChestBlock.tick 的写法：有人开着时每 5 刻一次，走开或掉线没关界面的人会被数掉） */
    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MagicalChestBlockEntity chest) {
            chest.recheckOpen();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 同一方块只是换了朝向时也会进来，不判断会把东西撒出来
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof MagicalChestBlockEntity chest) {
                chest.dropContents();
            }
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MagicalChestBlockEntity chest
                ? ItemHandlerHelper.calcRedstoneFromInventory(chest.getInventory()) : 0;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }
}
