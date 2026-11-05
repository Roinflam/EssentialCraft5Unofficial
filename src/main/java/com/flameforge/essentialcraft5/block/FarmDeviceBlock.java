package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.FarmDeviceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * 农牧红石设备的方块（ECFarmDevices）：魔力物品洗牌器、魔力繁殖器、魔力剪毛器共用。原作是 {@code BlockRedstoneDeviceNotSided} 的 meta 1 / 3 / 5
 * （硬度 1、抗爆 1，{@code BlocksCore} L168，整块不透明，六面同贴图）。
 * <p>
 * 红石从无到有那一下触发一次（{@link PulseMachineBlock}，方块实体不 tick）；没有界面，空手右键在动作栏显示主人状态（{@link MachineOwnerStatus}）。
 * 原作 {@code canProvidePower} 恒为 true（红石线会连过来，但它并不输出信号，只影响外观），不移植。
 */
public class FarmDeviceBlock<E extends FarmDeviceBlockEntity> extends PulseMachineBlock<E> {

    private final Supplier<BlockEntityType<E>> type;

    public FarmDeviceBlock(Properties properties, Supplier<BlockEntityType<E>> type) {
        super(properties, false, false);
        this.type = type;
    }

    @Override
    protected BlockEntityType<E> blockEntityType() {
        return type.get();
    }

    @Override
    protected boolean opensMenu() {
        return false;
    }
}
