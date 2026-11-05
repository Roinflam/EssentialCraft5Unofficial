package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.SunRayAbsorberBlockEntity;
import com.flameforge.essentialcraft5.registry.ECSolar;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * 日光吸收器界面：没有机器槽位，只显示数值。对应原作 ContainerSunRayAbsorber
 * （原作 canInteractWith 恒为 true、走多远都不关，这里按距离判断）。
 * 额外数值：被光束照到时每刻的产量（服务端配置，拆两格）。
 */
public class SunRayAbsorberMenu extends MachineMenu {

    public static final int EXTRA_GENERATION = 0;
    private static final int EXTRA_COUNT = 2;

    public SunRayAbsorberMenu(int id, Inventory playerInventory, SunRayAbsorberBlockEntity be) {
        super(ECSolar.SUN_RAY_ABSORBER_MENU.get(), id, playerInventory, be.getBlockPos(),
                MachineData.server(be, MachineData.wide(be::getGeneration)), ECSolar.SUN_RAY_ABSORBER.get(), be);
        addPlayerInventory(playerInventory);
    }

    public SunRayAbsorberMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        super(ECSolar.SUN_RAY_ABSORBER_MENU.get(), id, playerInventory, buf.readBlockPos(),
                MachineData.client(EXTRA_COUNT), ECSolar.SUN_RAY_ABSORBER.get(), null);
        addPlayerInventory(playerInventory);
    }

    public int generation() {
        return data.extraWide(EXTRA_GENERATION);
    }
}
