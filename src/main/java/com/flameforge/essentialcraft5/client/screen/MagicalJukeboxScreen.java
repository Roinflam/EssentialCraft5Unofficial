package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.MagicalJukeboxBlockEntity;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.menu.MagicalJukeboxMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 魔力唱片机界面，元素与坐标照搬原作 GuiMagicalJukebox。
 * 原作没有的：唱片槽右边 (44, 4) 一条播放状态（没有唱片 / 播放中 / 已停止）。
 */
public class MagicalJukeboxScreen extends MachineScreen<MagicalJukeboxMenu> {

    public MagicalJukeboxScreen(MagicalJukeboxMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 40));
        elements.add(boundGemState(25, 22));
        elements.add(mruState(25, 58));
        elements.add(new StatusTextElement(44, 4, 82, this::statusText, this::statusColor));
    }

    private boolean hasRecord() {
        return menu.getSlot(MagicalJukeboxBlockEntity.SLOT_RECORD).hasItem();
    }

    private Component statusText() {
        if (!hasRecord()) {
            return Component.translatable("gui.essentialcraft5.jukebox.empty");
        }
        return Component.translatable(menu.playing() ? "gui.essentialcraft5.jukebox.playing" : "gui.essentialcraft5.jukebox.stopped");
    }

    private int statusColor() {
        if (!hasRecord()) {
            return 0xFFFF00;
        }
        return menu.playing() ? 0x00FF00 : 0xFFFFFF;
    }
}
