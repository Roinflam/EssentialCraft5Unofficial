package com.flameforge.essentialcraft5.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 方块状态检测器（原作 BlockMetadataManager「元数据管理器」）：把正前方方块的「数值」当红石信号输出——作物长到第几期、蛋糕被吃了几口、
 * 堆肥桶 / 炼药锅有多满、雪有几层……朝前方那一面不输出、不连红石线，其余五面输出弱信号。没有方块实体。
 * <p>
 * 原作输出前方方块的 1.12 metadata（很多方块的 meta 是朝向、颜色，本来就不是数值），1.20 没有 metadata，改成（batch4 §3.5）：
 * <b>前方方块状态里按 {@link #PRIORITY} 取第一个有的整数属性的原值</b>，超过 15 按 15，都没有输出 0。小麦熟了是 7，和老玩家记的 1.12 一样。
 * <p>
 * 刷新改成<b>自己感知前方变化</b>（原作研究书 L1027 自认它不会自己刷新，小麦长了要手动给它一次方块更新）：
 * 照原版侦测器，前方那格的形状更新（{@link #updateShape}；作物生长用标志 2 的 setBlock 也会触发）或邻居更新时排一个 {@link #UPDATE_DELAY} 刻的计划刻，
 * 计划刻里重算，变了才改方块状态（原版会通知六个邻居）。删掉原作「收到邻居更新就转发给另外五个邻居」（L34~43，会连锁放大更新）。
 * 平时没有任何开销；只读紧挨着的一格（计划刻只在 tick 中的区块跑，紧挨的格子一定已加载）。
 * <p>
 * 朝向 = 点中的面（{@link FacingRule#CLICKED_FACE}，原作 L57~60）：前方背对着被点的方块。不当红石导体（同原版侦测器、红石块：被强充能也不往外传）。
 */
public class MetadataManagerBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    /** 前方变化后多少刻重算（同原版侦测器） */
    public static final int UPDATE_DELAY = 2;

    /**
     * 整数属性的优先顺序：前方方块有好几个整数属性时取排在前面的（例：海龟蛋有 eggs 和 hatch，取蛋的个数）。
     * age 作物 / 仙人掌 / 甘蔗 / 火 / 海带 / 藤蔓 → level 炼药锅 / 堆肥桶 / 液体 / 光源方块 → bites 蛋糕 → candles → pickles 海泡菜 → eggs → hatch →
     * layers 雪 → moisture 耕地 → honey_level 蜂巢 → charges 重生锚 → power 红石线 / 阳光探测器 → delay 中继器 → note 音符盒 → distance 树叶 / 脚手架 →
     * stage 树苗 → dusted 可疑的沙子 → flower_amount 粉红色花簇 → rotation 告示牌 / 旗帜 / 头颅。
     */
    public static final List<String> PRIORITY = List.of(
            "age", "level", "bites", "candles", "pickles", "eggs", "hatch", "layers", "moisture", "honey_level",
            "charges", "power", "delay", "note", "distance", "stage", "dusted", "flower_amount", "rotation");

    public MetadataManagerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWER, 0));
    }

    /** 前方方块的「数值」（0~15）：按 {@link #PRIORITY} 取第一个整数属性的原值，超过 15 按 15，没有就是 0 */
    public static int valueOf(BlockState front) {
        IntegerProperty best = null;
        int bestRank = Integer.MAX_VALUE;
        for (Property<?> property : front.getProperties()) {
            if (property instanceof IntegerProperty integer) {
                int rank = PRIORITY.indexOf(integer.getName());
                if (rank >= 0 && rank < bestRank) {
                    best = integer;
                    bestRank = rank;
                }
            }
        }
        return best == null ? 0 : Mth.clamp(front.getValue(best), 0, 15);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = FacingRule.CLICKED_FACE.apply(context);
        BlockState front = context.getLevel().getBlockState(context.getClickedPos().relative(facing));
        return defaultBlockState().setValue(FACING, facing).setValue(POWER, valueOf(front));
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

    // ------------------------------------------------------------------
    // 刷新
    // ------------------------------------------------------------------

    /** 排一次重算（已经排着就不重复排；客户端什么都不做） */
    private void scheduleRecheck(LevelAccessor level, BlockPos pos) {
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, UPDATE_DELAY);
        }
    }

    /** 前方那格变了（形状更新：作物生长、放置、破坏都会走到这里） */
    @SuppressWarnings("deprecation")
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING)) {
            scheduleRecheck(level, pos);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /** 兜底：跳过形状更新、只发邻居更新的改动（活塞推拉等）；只认前方那一格 */
    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (fromPos.equals(pos.relative(state.getValue(FACING)))) {
            scheduleRecheck(level, pos);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    /** 刚放下、被换上（/setblock、结构、别的方式）时也算一次 */
    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!state.is(oldState.getBlock())) {
            scheduleRecheck(level, pos);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int value = valueOf(level.getBlockState(pos.relative(state.getValue(FACING))));
        if (value != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, value), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------
    // 红石
    // ------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * 除了朝前方那一面都输出（原作 L50~54）。{@code direction} 是「来问的那一格指向本方块」的方向：
     * 前方那格来问时是 FACING 的反方向。
     */
    @SuppressWarnings("deprecation")
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(FACING).getOpposite() ? 0 : state.getValue(POWER);
    }

    /**
     * 前方那一面不连红石线（原作 L45~48）。Forge 传来的 {@code direction} 是「红石线指向本方块」的方向（原版侦测器只连背面 = 方向等于它的 FACING），
     * 前方的红石线指向本方块是 FACING 的反方向；null（红石线爬墙时问头顶的方块）不连。
     */
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && direction != state.getValue(FACING).getOpposite();
    }
}
