package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.CrystalControllerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECEssence;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 水晶控制器：贴在元素水晶旁边，喂精华定向改水晶的元素。原作 BlockCrystalController（OBJ 模型，TESR 只是把同一个模型再画一遍，
 * 这里直接用 OBJ 方块模型）。原作 registerBlockSimple 的不透光度是 0，所以透天空光。
 */
public class CrystalControllerBlock extends MachineBlock<CrystalControllerBlockEntity> {

    public CrystalControllerBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<CrystalControllerBlockEntity> blockEntityType() {
        return ECEssence.CRYSTAL_CONTROLLER_BE.get();
    }

    /** 旁边放了、挖了水晶：让机器立即重新找水晶（平时每 20 tick 才重找一次） */
    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CrystalControllerBlockEntity be) {
            be.neighborChanged();
        }
    }
}
