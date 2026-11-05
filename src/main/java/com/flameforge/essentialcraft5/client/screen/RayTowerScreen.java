package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.menu.RayTowerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 射线塔界面，元素与坐标照搬原作 GuiRayTower（和充能仓的布局一样，只少一个充能槽）。 */
public class RayTowerScreen extends MachineScreen<RayTowerMenu> {

    public RayTowerScreen(RayTowerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 4));
        elements.add(boundGemState(25, 22));
        elements.add(mruState(25, 58));
    }
}
