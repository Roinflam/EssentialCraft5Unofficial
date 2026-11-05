package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.EnchantUpgraderBlockEntity;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Tier;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * 附魔增幅台、附魔升华台（见 {@link EnchantUpgraderBlockEntity}）。两台共用这个类，靠构造参数区分档位和方块实体类型。
 * 方块属性照魔力附魔台（硬度 1、抗爆按 1.12 规则换算、透天空光）；模型是魔力附魔台 OBJ 换了贴图，碰撞箱是整格。
 */
public class EnchantUpgraderBlock extends MachineBlock<EnchantUpgraderBlockEntity> {

    private final Tier tier;
    private final Supplier<BlockEntityType<EnchantUpgraderBlockEntity>> type;

    public EnchantUpgraderBlock(Properties properties, Tier tier, Supplier<BlockEntityType<EnchantUpgraderBlockEntity>> type) {
        super(properties, true);
        this.tier = tier;
        this.type = type;
    }

    public Tier tier() {
        return tier;
    }

    @Override
    public BlockEntityType<EnchantUpgraderBlockEntity> blockEntityType() {
        return type.get();
    }
}
