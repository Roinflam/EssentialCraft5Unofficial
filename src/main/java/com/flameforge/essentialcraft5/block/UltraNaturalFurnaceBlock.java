package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.UltraNaturalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.registry.ECUltraGenerators;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 终极自然熔炉：烧放进格子里的花、树叶、草、原木产 MRU。原作 BlockUltraFlowerBurner（自然熔炉的 OBJ 换一张贴图，不透光度 0）。
 * 不碰世界里的方块，所以不需要主人，规则见 {@link UltraNaturalFurnaceBlockEntity} 类注释；方块被破坏时格子里的东西由 {@link MachineBlock#onRemove} 掉出来。
 */
public class UltraNaturalFurnaceBlock extends MachineBlock<UltraNaturalFurnaceBlockEntity> {

    public UltraNaturalFurnaceBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<UltraNaturalFurnaceBlockEntity> blockEntityType() {
        return ECUltraGenerators.ULTRA_NATURAL_FURNACE_BE.get();
    }
}
