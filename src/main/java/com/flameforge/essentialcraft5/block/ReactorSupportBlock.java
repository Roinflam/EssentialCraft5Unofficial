package com.flameforge.essentialcraft5.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * MRU 特斯拉反应堆支架。对应原作 BlockReactorSupport（没有方块实体，Material.ROCK、MapColor.PURPLE、不透光）。
 * <p>
 * 两组外观状态，照原作 {@code getActualState}（L59~74）：
 * <ul>
 *   <li>{@link #LAYER}：下面一格不是支架 = 底，下面一格是、再下面一格不是 = 中，下面两格都是 = 顶（DummyCore {@code EnumLayer.LAYERTHREE}）。
 *       反应堆里的支架两格高，所以是「底 + 中」；叠三根以上时最上面的是「顶」（原作画成一整块魔力护板）。</li>
 *   <li>四个方向的「电栏」：那一面紧挨着的是<b>已成形</b>的反应堆核心（{@link ReactorBlock#FORMED}）才画。只有下层那两根挨着核心，
 *       上层那两根旁边是空气，所以只有下层亮（原作也一样）。</li>
 * </ul>
 * 1.20.1 没有 {@code getActualState}（原作每次渲染都去读四个邻格的方块实体），改成存进方块状态：
 * 放下时（{@link #getStateForPlacement}）和邻格变化时（{@link #updateShape}）重算整个状态。下面的支架状态一变，
 * 原版会接着通知它上面的支架，所以三根叠放时拆掉最下面那根，上面两根会一路改过来；核心成形 / 散架时 setBlock 改了 FORMED，
 * 同样会通知两侧的支架。
 * 旋转、镜像照原作 {@code withRotation / withMirror} 调换四个方向。
 */
public class ReactorSupportBlock extends Block {

    /** 底 / 中 / 顶 */
    public enum Layer implements StringRepresentable {
        BOTTOM("bottom"),
        MIDDLE("middle"),
        TOP("top");

        private final String name;

        Layer(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Layer> LAYER = EnumProperty.create("layer", Layer.class);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public ReactorSupportBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(LAYER, Layer.BOTTOM)
                .setValue(NORTH, Boolean.FALSE)
                .setValue(EAST, Boolean.FALSE)
                .setValue(SOUTH, Boolean.FALSE)
                .setValue(WEST, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYER, NORTH, EAST, SOUTH, WEST);
    }

    /** 某个方向对应的电栏属性（只有水平四向） */
    public static BooleanProperty bars(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("支架只有水平四个方向的电栏：" + direction);
        };
    }

    /** 按周围的方块算出这个位置上支架该有的完整状态（层、四向电栏） */
    public BlockState computeState(BlockState state, BlockGetter level, BlockPos pos) {
        boolean below = level.getBlockState(pos.below()).is(this);
        boolean belowTwo = below && level.getBlockState(pos.below(2)).is(this);
        BlockState result = state.setValue(LAYER, below ? (belowTwo ? Layer.TOP : Layer.MIDDLE) : Layer.BOTTOM);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            result = result.setValue(bars(direction), ReactorBlock.isFormed(level.getBlockState(pos.relative(direction))));
        }
        return result;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return computeState(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        return computeState(state, level, pos);
    }

    /** 原作 setLightOpacity(0)：天空光直接穿过（同 MachineBlock 的 transmitsSkylight） */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1F;
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH)).setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> state.setValue(NORTH, state.getValue(EAST)).setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST)).setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90 -> state.setValue(NORTH, state.getValue(WEST)).setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST)).setValue(WEST, state.getValue(SOUTH));
            default -> state;
        };
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default -> super.mirror(state, mirror);
        };
    }
}
