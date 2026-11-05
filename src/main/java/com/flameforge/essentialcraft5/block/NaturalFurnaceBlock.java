package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.NaturalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 自然熔炉：烧放进它格子里的花、树苗、草产 MRU。原作 BlockFlowerBurner（OBJ 模型 flowerburner，setLightOpacity(0)）。
 * <p>
 * 第四批改造（batch4.md §0.0）起不碰世界里的方块，所以不再需要主人：谁放的、怎么放的都照常工作，规则见 {@link NaturalFurnaceBlockEntity} 类注释。
 * 方块被破坏时格子里的植物由 {@link MachineBlock#onRemove} 掉出来。
 */
public class NaturalFurnaceBlock extends MachineBlock<NaturalFurnaceBlockEntity> {

    public NaturalFurnaceBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<NaturalFurnaceBlockEntity> blockEntityType() {
        return ECGenerators.NATURAL_FURNACE_BE.get();
    }
}
