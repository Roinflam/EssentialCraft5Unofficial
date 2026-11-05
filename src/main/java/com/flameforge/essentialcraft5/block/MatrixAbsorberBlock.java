package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MatrixAbsorberBlockEntity;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 矩阵换能器：把灵魂石主人的 UBMRU 换成 MRU。原作 BlockMatrixAbsorber（OBJ 模型，中间悬浮转动的灵魂石由 BER 画）。
 * 原作 registerBlockSimple(…, 1, 1, 0)：不透光度 0，所以 transmitsSkylight=true。
 */
public class MatrixAbsorberBlock extends MachineBlock<MatrixAbsorberBlockEntity> {

    public MatrixAbsorberBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<MatrixAbsorberBlockEntity> blockEntityType() {
        return ECBootstrap.MATRIX_ABSORBER_BE.get();
    }
}
