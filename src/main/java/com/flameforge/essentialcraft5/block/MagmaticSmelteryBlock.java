package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MagmaticSmelteryBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMagmatic;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 岩浆冶炼炉：用 MRU 和岩浆把矿石熔成岩浆合金、再炼成成品，1 块矿出 4 个锭。原作 BlockMagmaticSmeltery（OBJ 模型）。
 * 原作 registerBlockSimple 的不透光度是 0，所以透天空光。
 */
public class MagmaticSmelteryBlock extends MachineBlock<MagmaticSmelteryBlockEntity> {

    public MagmaticSmelteryBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<MagmaticSmelteryBlockEntity> blockEntityType() {
        return ECMagmatic.MAGMATIC_SMELTERY_BE.get();
    }
}
