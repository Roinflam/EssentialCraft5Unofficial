package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.menu.ChargingChamberMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 充能仓界面，元素与坐标照搬原作 GuiChargingChamber。 */
public class ChargingChamberScreen extends MachineScreen<ChargingChamberMenu> {

    public ChargingChamberScreen(ChargingChamberMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 4));
        elements.add(boundGemState(25, 22));
        elements.add(mruState(25, 58));
    }
}
