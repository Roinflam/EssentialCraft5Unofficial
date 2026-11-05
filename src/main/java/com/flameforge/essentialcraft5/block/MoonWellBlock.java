package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MoonWellBlockEntity;
import com.flameforge.essentialcraft5.registry.ECBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** 月亮井：夜里看得到天空时按月相和高度产出 MRU。原作 BlockMoonWell（半透明渲染层，模型里有染色玻璃）。 */
public class MoonWellBlock extends MachineBlock<MoonWellBlockEntity> {

    public MoonWellBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<MoonWellBlockEntity> blockEntityType() {
        return ECBlockEntities.MOON_WELL.get();
    }
}
