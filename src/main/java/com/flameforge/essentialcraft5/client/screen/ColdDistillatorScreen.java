package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.MRUGeneratedElement;
import com.flameforge.essentialcraft5.menu.ColdDistillatorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 冷凝器界面，元素与坐标照搬原作 GuiColdDistillator（原作 GuiMRUGenerated("cold") 就是一条「N MRU/t」文字）。
 * 文字条悬停时说明各种冷源的冷值。
 */
public class ColdDistillatorScreen extends MachineScreen<ColdDistillatorMenu> {

    public ColdDistillatorScreen(ColdDistillatorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(mruState(25, 58));
        elements.add(new MRUGeneratedElement(25, 40, menu::generation, "gui.essentialcraft5.cold_distillator.hint", 4));
        elements.add(balance(25, 22));
    }
}
