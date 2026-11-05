package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MagicalRepairerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDevices;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 魔力修复器。原作 BlockMagicalRepairer（OBJ 模型，registerBlockSimple(…, 1, 1, 0) 不透光度 0）。
 * 右键开界面、潜行不开、挖掉掉物品栏都在 {@link MachineBlock} 里。
 * <p>
 * 原作装了 CodeChickenLib 时方块本身只留破坏粒子、OBJ 由 TESR 画；这里 OBJ 走方块模型，BER 只画上方旋转的工具。
 */
public class MagicalRepairerBlock extends MachineBlock<MagicalRepairerBlockEntity> {

    public MagicalRepairerBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<MagicalRepairerBlockEntity> blockEntityType() {
        return ECDevices.MAGICAL_REPAIRER_BE.get();
    }
}
