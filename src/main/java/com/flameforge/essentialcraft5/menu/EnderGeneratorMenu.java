package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.EnderGeneratorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECSolar;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * 末影产能器界面：没有机器槽位，只显示数值。对应原作 ContainerEnderGenerator。
 * 额外数值：每打中一次的产量（服务端配置，拆两格）。
 */
public class EnderGeneratorMenu extends MachineMenu {

    public static final int EXTRA_GENERATION = 0;
    private static final int EXTRA_COUNT = 2;

    public EnderGeneratorMenu(int id, Inventory playerInventory, EnderGeneratorBlockEntity be) {
        super(ECSolar.ENDER_GENERATOR_MENU.get(), id, playerInventory, be.getBlockPos(),
                MachineData.server(be, MachineData.wide(be::getGeneration)), ECSolar.ENDER_GENERATOR.get(), be);
        addPlayerInventory(playerInventory);
    }

    public EnderGeneratorMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        super(ECSolar.ENDER_GENERATOR_MENU.get(), id, playerInventory, buf.readBlockPos(),
                MachineData.client(EXTRA_COUNT), ECSolar.ENDER_GENERATOR.get(), null);
        addPlayerInventory(playerInventory);
    }

    /** 每打中一次的产量 */
    public int generation() {
        return data.extraWide(EXTRA_GENERATION);
    }
}
