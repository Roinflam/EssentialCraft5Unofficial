package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.CreativeESPEStorageBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** 创造 ESPE 源：永远是满的，测试和创造模式用。原作 device 方块的 meta 8，没有界面（同一批的创造 MRU 源是 meta 4）。 */
public class CreativeESPEStorageBlock extends MachineBlock<CreativeESPEStorageBlockEntity> {

    public CreativeESPEStorageBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    protected BlockEntityType<CreativeESPEStorageBlockEntity> blockEntityType() {
        return ECMithriline.CREATIVE_ESPE_STORAGE_BE.get();
    }

    @Override
    protected boolean opensMenu() {
        return false;
    }
}
