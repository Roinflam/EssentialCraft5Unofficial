package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.CreativeMRUStorageBlockEntity;
import com.flameforge.essentialcraft5.registry.ECBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** 创造 MRU 源：永远是满的，测试和创造模式用。原作 device 方块的 meta 4，没有界面。 */
public class CreativeMRUStorageBlock extends MachineBlock<CreativeMRUStorageBlockEntity> {

    public CreativeMRUStorageBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    protected BlockEntityType<CreativeMRUStorageBlockEntity> blockEntityType() {
        return ECBlockEntities.CREATIVE_MRU_STORAGE.get();
    }

    @Override
    protected boolean opensMenu() {
        return false;
    }
}
