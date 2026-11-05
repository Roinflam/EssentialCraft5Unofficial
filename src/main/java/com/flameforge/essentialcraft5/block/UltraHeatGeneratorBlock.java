package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.UltraHeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECUltraGenerators;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 终极热力产能器：烧燃料、越烧越热、越热产得越多。原作 BlockUltraHeatGenerator（热力产能器的 OBJ 换一张贴图，不透光度 0）。
 * 规则见 {@link UltraHeatGeneratorBlockEntity} 类注释；方块被破坏时燃料和炉渣由 {@link MachineBlock#onRemove} 掉出来。
 */
public class UltraHeatGeneratorBlock extends MachineBlock<UltraHeatGeneratorBlockEntity> {

    public UltraHeatGeneratorBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<UltraHeatGeneratorBlockEntity> blockEntityType() {
        return ECUltraGenerators.ULTRA_HEAT_GENERATOR_BE.get();
    }
}
