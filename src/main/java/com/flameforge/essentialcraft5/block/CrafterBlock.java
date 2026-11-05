package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.CrafterBlockEntity;
import com.flameforge.essentialcraft5.registry.ECCrafter;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 魔力合成器（原作 BlockRedstoneDeviceNotSided 的 meta 2）。吃红石上升沿、不 tick（纯脉冲，平时零开销），右键开界面。
 * 原作整块不透明、硬度 1、抗爆 1（先硬度后抗爆），六面同一张 crafter 贴图。
 */
public class CrafterBlock extends PulseMachineBlock<CrafterBlockEntity> {

    public CrafterBlock(Properties properties) {
        super(properties, false, false);
    }

    @Override
    protected BlockEntityType<CrafterBlockEntity> blockEntityType() {
        return ECCrafter.CRAFTER_BE.get();
    }
}
