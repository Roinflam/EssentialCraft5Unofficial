package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MoonWellBlockEntity;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * 月亮井界面：没有机器槽位，只显示数值。对应原作 ContainerMoonWell
 * （原作 canInteractWith 恒为 true、走多远都不关，这里按距离判断）。
 * 额外数值：当前月相与高度下的每 tick 产量（拆两格）。
 */
public class MoonWellMenu extends MachineMenu {

    public static final int EXTRA_GENERATION = 0;

    public MoonWellMenu(int id, Inventory playerInventory, MoonWellBlockEntity be) {
        super(ECMenus.MOON_WELL.get(), id, playerInventory, be.getBlockPos(),
                MachineData.server(be, MachineData.wide(() ->
                        (int) MoonWellBlockEntity.potentialGeneration(be.getLevel(), be.getBlockPos()))),
                ECBlocks.MOON_WELL.get(), be);
        addPlayerInventory(playerInventory);
    }

    public MoonWellMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        super(ECMenus.MOON_WELL.get(), id, playerInventory, buf.readBlockPos(), MachineData.client(2), ECBlocks.MOON_WELL.get(), null);
        addPlayerInventory(playerInventory);
    }

    public int generation() {
        return data.extraWide(EXTRA_GENERATION);
    }
}
