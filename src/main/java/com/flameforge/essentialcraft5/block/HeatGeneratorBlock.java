package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.HeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** 热力产能器：烧燃料、按四周相隔一格的热源产 MRU。原作 BlockHeatGenerator（OBJ 模型，setLightOpacity(0)）。 */
public class HeatGeneratorBlock extends MachineBlock<HeatGeneratorBlockEntity> {

    public HeatGeneratorBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<HeatGeneratorBlockEntity> blockEntityType() {
        return ECGenerators.HEAT_GENERATOR_BE.get();
    }
}
