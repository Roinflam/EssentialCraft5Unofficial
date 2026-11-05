package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.HeightStateElement;
import com.flameforge.essentialcraft5.client.screen.element.MoonStateElement;
import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.MoonWellMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 月亮井界面，元素与坐标照搬原作 GuiMoonWell。 */
public class MoonWellScreen extends MachineScreen<MoonWellMenu> {

    public MoonWellScreen(MoonWellMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(mruState(25, 58));
        elements.add(new MoonStateElement(25, 40));
        elements.add(new HeightStateElement(152, 40, menu.getPos().getY()));
        // 原作 GuiMRUGenerated("moonwell")：按月相与高度算的每 tick 产量
        elements.add(new TextBarElement(43, 40, TextBarElement.NORMAL_WIDTH,
                () -> Component.translatable("gui.essentialcraft5.mru_per_tick", menu.generation()),
                () -> 0xFFFFFF, false));
        elements.add(balance(25, 22));
    }
}
