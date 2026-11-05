package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.RedstoneTransmitterBlockEntity;
import com.flameforge.essentialcraft5.registry.ECRedstoneTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * 红石传递器（原作 BlockRedstoneTransmitter）：无线红石。一次做两个；给一个放进绑定宝石、绑到另一个上，
 * 它就输出另一个所在位置收到的红石信号（逻辑在 {@link RedstoneTransmitterBlockEntity}）。
 * <p>
 * {@link #FACING} 指向它贴着的那个方块（放下时 = 点中那一面的反面，{@link FacingRule#CLICKED_FACE_OPPOSITE}，原作 L100~103）；
 * 模型（原作 {@code models/block/redstonetransmitter.json}：一块底板 + 一根红石火把，cutout_mipped）底板朝北，方块状态按朝向转。
 * <p>
 * 输出存在方块状态 {@link #POWER} 里，六个面都输出弱信号（照原作 L117~126）；{@link #getSignal} 只读自己的状态，
 * 不像原作那样每次被问都去读远处的方块和它的邻居（原作 1.12 读没加载的区块会同步加载，还不判方块实体为空）。
 * 不当红石导体（被强充能也不往外传），瞬间挖掉、空手也掉（原作 ROCK 材质空手挖不掉落，丢东西；这里照原版红石火把）。
 * 形状照模型：底板 + 火把（原作选框是一根 0.3×0.3×0.5 的小柱子）。液体流不进来（forceSolidOn，原作 ROCK 挡液体）。
 * 支撑它的方块没了它也不掉（照原作）。
 */
public class RedstoneTransmitterBlock extends FacingMachineBlock<RedstoneTransmitterBlockEntity> {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    /** 各朝向的形状：底板 3.2~12.8、厚 3.2；火把 6~10、从底板伸出到 10（模型的 cube_0 与 cube_1/2 的外包） */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            SHAPES.put(facing, Shapes.or(
                    attachedBox(facing, 3.2D, 12.8D, 0D, 3.2D),
                    attachedBox(facing, 6D, 10D, 3.2D, 10D)));
        }
    }

    public RedstoneTransmitterBlock(Properties properties) {
        super(properties, true, FacingRule.CLICKED_FACE_OPPOSITE, true);
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    /**
     * 一个贴着 {@code facing} 那一面的盒子：横截面是 {@code [lo, hi]} 的正方形（两个方向相同，模型左右上下对称），
     * 深度 {@code [near, far]} 从贴着的那一面往里量（像素）。
     */
    private static VoxelShape attachedBox(Direction facing, double lo, double hi, double near, double far) {
        double d0 = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? near : 16D - far;
        double d1 = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? far : 16D - near;
        return switch (facing.getAxis()) {
            case X -> Block.box(d0, lo, lo, d1, hi, hi);
            case Y -> Block.box(lo, d0, lo, hi, d1, hi);
            case Z -> Block.box(lo, lo, d0, hi, hi, d1);
        };
    }

    @Override
    protected BlockEntityType<RedstoneTransmitterBlockEntity> blockEntityType() {
        return ECRedstoneTools.REDSTONE_TRANSMITTER_BE.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(facing(state));
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    /** 六个面都输出（照原作）；只读自己的状态 */
    @SuppressWarnings("deprecation")
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }
}
