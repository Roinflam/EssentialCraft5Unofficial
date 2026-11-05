package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.RadiatingChamberBlockEntity;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 辐射仓：用 MRU 把两个原料辐照成产物，产物随 MRU 的平衡值变化。原作 BlockRadiatingChamber（OBJ 模型，
 * 上原料由 BER 摆在机器顶上显示）。原作 registerBlockSimple 的不透光度是 0，所以透天空光。
 */
public class RadiatingChamberBlock extends MachineBlock<RadiatingChamberBlockEntity> {

    public RadiatingChamberBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<RadiatingChamberBlockEntity> blockEntityType() {
        return ECRadiatingChamber.RADIATING_CHAMBER_BE.get();
    }
}
