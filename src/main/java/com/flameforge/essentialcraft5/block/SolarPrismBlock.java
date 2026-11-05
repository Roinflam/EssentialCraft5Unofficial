package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.SolarPrismBlockEntity;
import com.flameforge.essentialcraft5.registry.ECSolar;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 太阳棱镜。对应原作 BlockSolarPrism（Material.ROCK、MapColor.GOLD、TRANSLUCENT 渲染层、setLightOpacity(0)）。
 * 没有界面、没有 MRU；逻辑全在 {@link SolarPrismBlockEntity}（摆法判定、打光束、光柱渲染）。
 * <p>
 * 形状照原作 BLOCK_AABB：(-1, 0.4, -1) ~ (2, 0.6, 2)，一块伸出本格、盖住周围 8 格的薄板，和模型一致。
 * 1.20.1 允许形状超出本格一格（{@code hasLargeCollisionShape}，实体碰撞会检查外圈），选取框只在射线穿过棱镜本格时出现（同 1.12）。
 * 形状不影响摆法判定：周围 8 格看的是方块本身是不是空气。
 * <p>
 * 不是 {@link MachineBlock}（那个要求方块实体带 MRU），天空光、AO 的处理照抄它（原作 setLightOpacity(0)）。
 */
public class SolarPrismBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Block.box(-16D, 6.4D, -16D, 32D, 9.6D, 32D);

    public SolarPrismBlock(Properties properties) {
        super(properties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** BaseEntityBlock 默认 INVISIBLE */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1F;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ECSolar.SOLAR_PRISM_BE.get().create(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ECSolar.SOLAR_PRISM_BE.get(), SolarPrismBlockEntity::clientTick)
                : createTickerHelper(type, ECSolar.SOLAR_PRISM_BE.get(), SolarPrismBlockEntity::serverTick);
    }
}
