package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.ColdDistillatorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * 冷凝器界面：没有机器槽位（原作 ContainerColdDistillator 只有玩家背包，canInteractWith 恒为 true，这里按距离判断）。
 * 额外数值：当前每 tick 产量（拆两格）。
 */
public class ColdDistillatorMenu extends MachineMenu {

    public static final int EXTRA_GENERATION = 0;

    public ColdDistillatorMenu(int id, Inventory playerInventory, ColdDistillatorBlockEntity be) {
        super(ECGenerators.COLD_DISTILLATOR_MENU.get(), id, playerInventory, be.getBlockPos(),
                MachineData.server(be, MachineData.wide(be::getGeneration)), ECGenerators.COLD_DISTILLATOR.get(), be);
        addPlayerInventory(playerInventory);
    }

    public ColdDistillatorMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        super(ECGenerators.COLD_DISTILLATOR_MENU.get(), id, playerInventory, buf.readBlockPos(),
                MachineData.client(2), ECGenerators.COLD_DISTILLATOR.get(), null);
        addPlayerInventory(playerInventory);
    }

    public int generation() {
        return data.extraWide(EXTRA_GENERATION);
    }
}
