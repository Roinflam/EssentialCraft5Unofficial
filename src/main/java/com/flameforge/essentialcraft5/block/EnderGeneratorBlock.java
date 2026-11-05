package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.EnderGeneratorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECSolar;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 末影产能器：把附近的末影人拉到头顶打，每打中一次产出 MRU。原作 BlockEnderGenerator（OBJ 模型 endergenerator，setLightOpacity(0)）。
 * 原作装了 CodeChickenLib 时方块改由 TESR 画同一个 OBJ（RenderEnderGenerator、RenderEnderGeneratorAsItem），效果一样，这里只用方块模型。
 */
public class EnderGeneratorBlock extends MachineBlock<EnderGeneratorBlockEntity> {

    public EnderGeneratorBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<EnderGeneratorBlockEntity> blockEntityType() {
        return ECSolar.ENDER_GENERATOR_BE.get();
    }
}
