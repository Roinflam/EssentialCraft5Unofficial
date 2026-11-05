package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.PotionStateElement;
import com.flameforge.essentialcraft5.menu.PotionSpreaderMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 药水扩散器界面，元素与坐标照搬原作 GuiPotionSpreader（平衡值和绑定宝石状态在原作里就叠着一截）。 */
public class PotionSpreaderScreen extends MachineScreen<PotionSpreaderMenu> {

    public PotionSpreaderScreen(PotionSpreaderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 4));
        elements.add(boundGemState(90, 4));
        elements.add(mruState(25, 58));
        elements.add(new PotionStateElement(97, 22, menu));
    }
}
