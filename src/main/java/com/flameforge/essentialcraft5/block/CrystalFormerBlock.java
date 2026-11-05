package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.CrystalFormerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECCrystal;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 水晶成型器：用 MRU 把三桶水、两块玻璃和一颗钻石做成一颗元素水晶。原作 BlockCrystalFormer（OBJ 模型，
 * 顶上 4 颗小水晶由 BER 画）。原作 registerBlockSimple 的不透光度是 0，所以透天空光。
 */
public class CrystalFormerBlock extends MachineBlock<CrystalFormerBlockEntity> {

    public CrystalFormerBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<CrystalFormerBlockEntity> blockEntityType() {
        return ECCrystal.CRYSTAL_FORMER_BE.get();
    }
}
