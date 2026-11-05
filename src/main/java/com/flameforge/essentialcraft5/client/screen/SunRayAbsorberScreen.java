package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.MRUGeneratedElement;
import com.flameforge.essentialcraft5.menu.SunRayAbsorberMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 日光吸收器界面，元素与坐标照搬原作 GuiSunRayAbsorber：
 * MRU 条 (7, 4)、存量 (25, 58)、平衡值 (25, 22)、GuiMRUGenerated("sunray") 的「N MRU/t」(25, 40)。
 * 产量文字悬停时说明它什么时候产出（原作界面没有说明）。
 */
public class SunRayAbsorberScreen extends MachineScreen<SunRayAbsorberMenu> {

    public SunRayAbsorberScreen(SunRayAbsorberMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(mruState(25, 58));
        elements.add(balance(25, 22));
        elements.add(new MRUGeneratedElement(25, 40, menu::generation, "gui.essentialcraft5.sun_ray_absorber.hint", 3));
    }
}
