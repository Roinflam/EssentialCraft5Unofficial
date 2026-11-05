package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.MagicianTableProgressElement;
import com.flameforge.essentialcraft5.menu.MagicianTableMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 魔力工作台界面，元素与坐标照搬原作 GuiMagicianTable。 */
public class MagicianTableScreen extends MachineScreen<MagicianTableMenu> {

    public MagicianTableScreen(MagicianTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(new MagicianTableProgressElement(25, 4, menu::progress, menu::required, menu::speedPercent));
        elements.add(mruState(25, 58));
        elements.add(balance(88, 22));
        elements.add(boundGemState(88, 40));
    }
}
