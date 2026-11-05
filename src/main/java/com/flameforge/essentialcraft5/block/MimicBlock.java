package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.block.camo.Camo;
import com.flameforge.essentialcraft5.blockentity.MimicBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 拟态方块（原作 BlockMimic）：拿方块右键它，它就长成那个方块的样子（方块不消耗）；两只手都空着时潜行右键变回原样。
 * <b>永远没有碰撞箱</b>（原作 L142~145，伪装以后也没有）：看着是墙，人和怪都能穿过去——暗门、藏机器、藏矿道口。
 * 但怪物寻路时把它当墙（{@link #isPathfindable}），不会专门从这里走进来，只有被推、被击退才会穿进去。
 * <p>
 * 照原作的：
 * <ul>
 *   <li>选框取被模仿方块的形状（原作 L114~126）；地图颜色、脚步 / 破坏声音也取被模仿的（原作只有地图颜色）。</li>
 *   <li>恒挡光 15（{@link #getLightBlock}，原作 setLightOpacity(15)）：伪装成玻璃也不透光；也不让天空光往下穿。</li>
 *   <li>硬度 3、抗爆照原作先 setResistance(15) 换算（{@code ECBlocks.legacyResistance(3, 15, true)} = 9），要镐挖。</li>
 *   <li>六个面都算结实（{@link #getBlockSupportShape}，原作没覆盖 getBlockFaceShape，默认 SOLID）：墙上、顶上的火把，按钮、拉杆、红石线、门
 *       都能贴在它上面（暗门的开关可以装在拟态墙上）；栅栏、墙、玻璃板会连过来。和伪装无关（伪装成半砖也一样）。</li>
 *   <li>寻路当墙（{@link #isPathfindable} 恒假，原作 Material.ROCK 的 blocksMovement 恒真 → WalkNodeProcessor 判 BLOCKED）：
 *       怪不会把路线画过拟态墙；拟态地板上面对怪是能走的地面，走上去照样掉下去。</li>
 * </ul>
 * 和原作的区别：
 * <ul>
 *   <li>铁轨、压力板也能放在它上面，打火石点在顶上的火也留得住（原作 isFullCube 为假，这几样放不上）：1.20 里红石线和门要 FULL 支撑，
 *       FULL 蕴含 RIGID / CENTER，没法只放行前者。无害。</li>
 *   <li>设伪装只在服务端做（原作两端都改、靠同步纠正），客户端只预判这次右键会不会被吃掉，结果随方块实体更新包过来。</li>
 *   <li>被模仿的状态按「放在拟态方块这一格」算（{@link AtMimicContext}）：楼梯朝向、半砖上下半、栅栏连接都按这一格和点中的面定。</li>
 *   <li>不能当伪装的：没有方块实体、普通模型渲染的方块以外的一切（箱子、熔炉、液体、屏障……），以及 {@code essentialcraft5:mimic_blacklist} 里的
 *       （{@link Camo#isValid}；本模块把拟态方块自己放进去），另外代码里再挡一次拟态方块自己（数据包删了标签也不会套娃）。</li>
 *   <li>冒险模式、旁观者改不了伪装（{@code mayBuild}；原作不查）。</li>
 *   <li>里面不刷怪：本模块把它放进 {@code minecraft:prevent_mob_spawning_inside}（原作看着是墙的拟态方块里面是黑的、又没碰撞，会刷出怪来）；
 *       上面也不刷怪（{@code isValidSpawn} 恒假，站上去本来就会掉下去）。</li>
 *   <li>液体流不进来（Properties.forceSolidOn，原作 Material.ROCK 挡液体）：否则 1.20 的水会把没碰撞箱的方块冲掉。</li>
 * </ul>
 * 权限：改伪装的是右键它的玩家本人，右键方块本身就归领地插件 / 领地模组管（Forge 的右键事件、混合端转发的交互事件），出生点保护由原版在调 {@code use} 之前查；
 * 只改这一格自己的外观，不改世界（policy §1.1「不算触发」）。
 */
public class MimicBlock extends BaseEntityBlock {

    /** 读被模仿方块的形状时防重入（被模仿的方块在 getShape 里又回头读这一格时不至于无限递归） */
    private static final ThreadLocal<Boolean> READING_SHAPE = ThreadLocal.withInitial(() -> false);

    public MimicBlock(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------
    // 伪装规则
    // ------------------------------------------------------------------

    /** 这个状态能不能当伪装（两端一样）：框架规则 + 不是拟态方块自己 */
    public static boolean acceptsCamo(@Nullable BlockState state) {
        return Camo.isValid(state) && !(state.getBlock() instanceof MimicBlock);
    }

    /**
     * 手里这件东西会让拟态方块变成什么样；不是方块物品、不能当伪装时返回 null。
     * 用方块自己的 getStateForPlacement（放在拟态方块这一格、点中的面照玩家点的），算不出来退回默认状态。
     */
    @Nullable
    public static BlockState camoFor(Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack held, BlockHitResult hit) {
        if (!(held.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        BlockState state;
        try {
            state = block.getStateForPlacement(new AtMimicContext(level, player, hand, held, hit, pos));
        } catch (RuntimeException e) {
            // 别的模组的方块在陌生位置算放置状态出错：当默认状态处理，不打印
            state = null;
        }
        if (state == null) {
            state = block.defaultBlockState();
        }
        return acceptsCamo(state) ? state : null;
    }

    /**
     * 某格拟态方块当前的伪装（没有、读不到返回 null）。服务端在别的线程上调用时读不到方块实体，返回 null。
     * 存档里的伪装读档时不校验（数据包改了黑名单不让已经伪装好的突然变样），所以这里再挡一次拟态方块自己：
     * 改过 NBT 的存档里伪装成拟态方块时，形状、地图颜色、声音按没伪装算，不会互相调用到栈溢出。
     */
    @Nullable
    public static BlockState camoAt(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MimicBlockEntity mimic) {
            BlockState camo = mimic.camo();
            return camo == null || camo.getBlock() instanceof MimicBlock ? null : camo;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 右键
    // ------------------------------------------------------------------

    /**
     * 不潜行、手里是能当伪装的方块 → 设成它（不消耗），吃掉这次右键（手里的方块不会顺手放出去）；
     * 潜行（1.20 里两只手都空着时潜行右键才会调到这里）且已经伪装 → 清掉。其余 PASS（手里的方块照常贴着它放）。
     * 服务端改、客户端只按同样的判断决定挥不挥手。
     */
    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || !player.mayBuild() || !(level.getBlockEntity(pos) instanceof MimicBlockEntity mimic)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (!held.isEmpty() || mimic.camo() == null) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                mimic.setCamo(null);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockState camo = camoFor(level, pos, player, hand, held, hit);
        if (camo == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            mimic.setCamo(camo);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------
    // 形状、光、外观
    // ------------------------------------------------------------------

    /** 选框：被模仿方块的形状；没伪装、读不到、形状为空（免得选不中、挖不掉）或出错时整格 */
    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (READING_SHAPE.get()) {
            return Shapes.block();
        }
        BlockState camo = camoAt(level, pos);
        if (camo == null) {
            return Shapes.block();
        }
        READING_SHAPE.set(true);
        try {
            VoxelShape shape = camo.getShape(level, pos, context);
            return shape.isEmpty() ? Shapes.block() : shape;
        } catch (RuntimeException e) {
            return Shapes.block();
        } finally {
            READING_SHAPE.set(false);
        }
    }

    /** 永远没有碰撞箱（原作 NULL_AABB） */
    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * 支撑形状恒为整格：六个面对 FULL / CENTER / RIGID 都算结实（原作 getBlockFaceShape 默认 SOLID）。
     * 不覆盖的话默认取碰撞形状（空），火把、按钮、拉杆、红石线、门都贴不上去。
     * 常量、不读方块实体：原版在 BlockStateBase.Cache 里按状态缓存 isFaceSturdy（用 EmptyBlockGetter 算）。
     * 雪层看的是碰撞形状、上面刷怪由 isValidSpawn 挡着，都不受影响；副作用是铁轨、压力板也放得上（原作不行，见类注释）。
     */
    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /**
     * 寻路一律当墙（陆地、飞行、水里都是）：原作 Material.ROCK 挡运动，WalkNodeProcessor 判 BLOCKED。
     * 不覆盖的话默认按「碰撞不是满格」算能走，怪会把路线画过拟态墙、专门从暗门穿进来，反而把拟态地板当悬崖绕开。
     * 水里本来也是假（它不能含水，这一格的流体恒为空）。实体被推、被击退照样穿得过去（碰撞仍为空）。
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    /** 不参与遮挡计算（noOcclusion 下原版本来不读；这里写死，免得有人在别的线程上经由它去读方块实体） */
    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    /** 恒挡光 15（原作 setLightOpacity(15)）；和伪装无关，不读方块实体，原版按方块状态缓存 */
    @SuppressWarnings("deprecation")
    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    /** BaseEntityBlock 默认 INVISIBLE；外观由框架的伪装模型画（没伪装时画自己的贴图） */
    @SuppressWarnings("deprecation")
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public MapColor getMapColor(BlockState state, BlockGetter level, BlockPos pos, MapColor defaultColor) {
        BlockState camo = camoAt(level, pos);
        return camo != null ? camo.getMapColor(level, pos) : defaultColor;
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        BlockState camo = camoAt(level, pos);
        return camo != null ? camo.getSoundType(level, pos, entity) : super.getSoundType(state, level, pos, entity);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MimicBlockEntity(pos, state);
    }

    // ------------------------------------------------------------------

    /**
     * 「把这个方块放在拟态方块这一格」的放置上下文：点中的面、点击位置照玩家这次右键，但放置位置是拟态方块自己
     * （原版的上下文会算成点中的面外面那一格，楼梯朝向、栅栏连接就按错的格子算了）。只拿来算状态，不真放。
     */
    private static final class AtMimicContext extends BlockPlaceContext {
        private final BlockPos at;

        AtMimicContext(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit, BlockPos at) {
            super(level, player, hand, stack, hit);
            this.at = at.immutable();
        }

        @Override
        public BlockPos getClickedPos() {
            return at;
        }

        @Override
        public boolean replacingClickedOnBlock() {
            return false;
        }
    }
}
