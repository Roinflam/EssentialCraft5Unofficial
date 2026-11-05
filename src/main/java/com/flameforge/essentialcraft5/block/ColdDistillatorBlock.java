package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.ColdDistillatorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 冷凝器：按周围的冰雪产 MRU。原作 BlockColdDistillator（OBJ 模型，setLightOpacity(0)）。
 * 原作装了 CodeChickenLib 时改用 TESR 画同一个 OBJ（colddistillatortemp），效果一样，这里只用方块模型。
 */
public class ColdDistillatorBlock extends MachineBlock<ColdDistillatorBlockEntity> {

    public ColdDistillatorBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<ColdDistillatorBlockEntity> blockEntityType() {
        return ECGenerators.COLD_DISTILLATOR_BE.get();
    }
}
