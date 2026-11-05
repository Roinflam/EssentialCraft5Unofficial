package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.FlameProgressElement;
import com.flameforge.essentialcraft5.client.screen.element.MRUGeneratedElement;
import com.flameforge.essentialcraft5.menu.HeatGeneratorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 热力产能器界面，元素与坐标照搬原作 GuiHeatGenerator：
 * 原作 GuiMRUGenerated("heatGenerator") 是一条「N MRU/t」文字，再在它右边 100 像素处画燃烧火苗，这里拆成两个元素。
 * 文字条悬停时说明四周热源的系数。
 */
public class HeatGeneratorScreen extends MachineScreen<HeatGeneratorMenu> {

    public HeatGeneratorScreen(HeatGeneratorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(mruState(25, 58));
        elements.add(balance(25, 22));
        elements.add(new MRUGeneratedElement(25, 40, menu::generation, "gui.essentialcraft5.heat_generator.hint", 3));
        elements.add(new FlameProgressElement(125, 42, menu::burnTime, menu::maxBurnTime));
    }
}
