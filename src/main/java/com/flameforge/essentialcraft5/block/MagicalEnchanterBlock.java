package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MagicalEnchanterBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDevices;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 魔力附魔台。原作 BlockMagicalEnchanter（OBJ 模型高 0.75，碰撞箱是整格，照原作；registerBlockSimple(…, 1, 1, 0)）。
 * 书的翻页动画由 BER 画（client/render/MagicalEnchanterRenderer）。
 */
public class MagicalEnchanterBlock extends MachineBlock<MagicalEnchanterBlockEntity> {

    public MagicalEnchanterBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<MagicalEnchanterBlockEntity> blockEntityType() {
        return ECDevices.MAGICAL_ENCHANTER_BE.get();
    }
}
