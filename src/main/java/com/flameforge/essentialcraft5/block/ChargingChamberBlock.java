package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.ChargingChamberBlockEntity;
import com.flameforge.essentialcraft5.registry.ECBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** 充能仓：用绑定宝石拉来的 MRU 给物品充能。原作 BlockChargingChamber（OBJ 模型）。 */
public class ChargingChamberBlock extends MachineBlock<ChargingChamberBlockEntity> {

    public ChargingChamberBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<ChargingChamberBlockEntity> blockEntityType() {
        return ECBlockEntities.CHARGING_CHAMBER.get();
    }
}
