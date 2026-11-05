package com.flameforge.essentialcraft5.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 魔晶簇：元素仪式在地上凝聚出的一小片碎晶，敲碎掉 1~6 颗对应的魔晶（掉落写在战利品表里，见 ECMaterials）。
 * 对应原作 BlockDrops 的 4 个 TYPE（火/水/地/风），1.20.1 拆成 4 个方块。
 * <p>
 * 原作：Material.CLOTH、没设硬度（瞬间破坏）、没有碰撞箱、选择框 0~0.1 格高；不透明度 0。
 * 原作不要求下面有支撑方块，下面的方块被挖掉后照样悬空，这里保持一致。
 * 原作 isFullCube=false，所以精准采集也不掉方块本身。
 */
public class DropsClusterBlock extends Block {

    /** 原作 BLOCK_AABB：(0, 0, 0) ~ (1, 0.1, 1) */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1.6, 16);

    public DropsClusterBlock(Properties properties) {
        super(properties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
