package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.MagicalRepairerBlockEntity;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.menu.MagicalRepairerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 魔力修复器界面，元素与坐标照搬原作 GuiMagicalRepairer。
 * 修理状态（原作 GuiRepairState，宽 114）由服务端算好状态码同步过来，颜色照原作。
 */
public class MagicalRepairerScreen extends MachineScreen<MagicalRepairerMenu> {

    public MagicalRepairerScreen(MagicalRepairerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 4));
        elements.add(boundGemState(25, 22));
        elements.add(mruState(25, 58));
        elements.add(new StatusTextElement(25, 40, 114, () -> statusText(menu.status()), () -> statusColor(menu.status())));
    }

    static Component statusText(int status) {
        return Component.translatable(switch (status) {
            case MagicalRepairerBlockEntity.STATUS_CANNOT -> "gui.essentialcraft5.repairer.cannot";
            case MagicalRepairerBlockEntity.STATUS_DONE -> "gui.essentialcraft5.repairer.done";
            case MagicalRepairerBlockEntity.STATUS_NO_MRU -> "gui.essentialcraft5.no_mru";
            case MagicalRepairerBlockEntity.STATUS_WORKING -> "gui.essentialcraft5.repairer.working";
            case MagicalRepairerBlockEntity.STATUS_REDSTONE -> "gui.essentialcraft5.devices.redstone_paused";
            default -> "gui.essentialcraft5.repairer.nothing";
        });
    }

    static int statusColor(int status) {
        return switch (status) {
            case MagicalRepairerBlockEntity.STATUS_CANNOT, MagicalRepairerBlockEntity.STATUS_NO_MRU -> 0xFF0000;
            case MagicalRepairerBlockEntity.STATUS_WORKING -> 0x00FF00;
            case MagicalRepairerBlockEntity.STATUS_REDSTONE -> 0xFFAA00;
            default -> 0xFFFF00;
        };
    }
}
