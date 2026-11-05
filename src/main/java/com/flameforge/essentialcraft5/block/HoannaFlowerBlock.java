package com.flameforge.essentialcraft5.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 海纳尔绿花（原作 BlockHoannaFlower，flowerGreen → green_flower；第六批 6a M1）：普通的一格花，长在泥土类方块和耕地上（原版 BushBlock）。
 * 原作只放创造栏；D11 A 起四种腐化之地会生成，海纳尔的草方块用骨粉也会长（地物类型 {@code minecraft:flower}，见 {@code green_flower_patch.json}）。
 * 形状 = 原作 BlockBush 的 BUSH_AABB（0.3, 0, 0.3, 0.7, 0.6, 0.7），按随机偏移挪（同原版 {@code FlowerBlock}）。
 * 原作硬度 1（徒手挖 1.5 秒）→ 瞬间挖，同原版花（policy / batch6a §8）。不做炖菜效果、不做花盆。
 */
public class HoannaFlowerBlock extends BushBlock {

    private static final VoxelShape SHAPE = Block.box(4.8D, 0.0D, 4.8D, 11.2D, 9.6D, 11.2D);

    public HoannaFlowerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }
}
