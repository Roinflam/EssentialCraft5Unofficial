package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.PotionSpreaderBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDevices;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** 药水扩散器。原作 BlockPotionSpreader（OBJ 模型，registerBlockSimple(…, 1, 1, 0)），原作没有 TESR。 */
public class PotionSpreaderBlock extends MachineBlock<PotionSpreaderBlockEntity> {

    public PotionSpreaderBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<PotionSpreaderBlockEntity> blockEntityType() {
        return ECDevices.POTION_SPREADER_BE.get();
    }
}
