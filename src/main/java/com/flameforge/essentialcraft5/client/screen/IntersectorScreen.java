package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.IntersectorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * MRU 汇流器界面，元素与坐标照搬原作 GuiMRUIntersector：MRU 条 (7,4)、平衡值 (25,4)、
 * 第一个宝石状态 (25,22)、第二个宝石状态 (25,40)、存量 (25,58)。
 */
public class IntersectorScreen extends MachineScreen<IntersectorMenu> {

    public IntersectorScreen(IntersectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 4));
        elements.add(boundGemState(25, 22));
        elements.add(new TextBarElement(25, 40, TextBarElement.NORMAL_WIDTH,
                () -> menu.secondLinkStatus().label(), () -> menu.secondLinkStatus().color, true));
        elements.add(mruState(25, 58));
    }
}
