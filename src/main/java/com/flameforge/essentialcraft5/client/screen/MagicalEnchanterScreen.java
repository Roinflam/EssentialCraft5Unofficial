package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.MagicalEnchanterBlockEntity;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.menu.MagicalEnchanterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔力附魔台界面，元素与坐标照搬原作 GuiMagicalEnchanter（平衡值 (25, 4) 和绑定宝石状态 (90, 4) 在原作里就叠着一截）。
 * 附魔状态（原作 GuiEnchantmentState，宽 145，右端 x+122 处写书架等级）由服务端算好状态码同步过来，颜色照原作；
 * 原作没有的：悬停提示写书架等级、正在附的等级和进度。
 */
public class MagicalEnchanterScreen extends MachineScreen<MagicalEnchanterMenu> {

    public MagicalEnchanterScreen(MagicalEnchanterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 4));
        elements.add(boundGemState(90, 4));
        elements.add(mruState(25, 58));
        elements.add(new StatusTextElement(25, 40, 145, () -> statusText(menu.status()), () -> statusColor(menu.status()),
                () -> String.valueOf(menu.power()), 122, this::statusTooltip));
    }

    private List<Component> statusTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.essentialcraft5.enchanter.power", menu.power()));
        if (menu.level() > 0) {
            int required = Math.max(1, menu.required());
            int done = Math.min(menu.progress(), required);
            lines.add(Component.translatable("gui.essentialcraft5.enchanter.level", menu.level()));
            lines.add(Component.translatable("gui.essentialcraft5.progress", done, required, done * 100 / required));
        } else {
            lines.add(Component.translatable("gui.essentialcraft5.enchanter.hint").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    static Component statusText(int status) {
        return Component.translatable(switch (status) {
            case MagicalEnchanterBlockEntity.STATUS_OUTPUT_FULL -> "gui.essentialcraft5.enchanter.output_full";
            case MagicalEnchanterBlockEntity.STATUS_NO_MRU -> "gui.essentialcraft5.no_mru";
            case MagicalEnchanterBlockEntity.STATUS_CANNOT -> "gui.essentialcraft5.enchanter.cannot_enchant";
            case MagicalEnchanterBlockEntity.STATUS_NO_BOOKSHELVES -> "gui.essentialcraft5.enchanter.no_bookshelves";
            case MagicalEnchanterBlockEntity.STATUS_WORKING -> "gui.essentialcraft5.enchanter.working";
            case MagicalEnchanterBlockEntity.STATUS_REDSTONE -> "gui.essentialcraft5.devices.redstone_paused";
            default -> "gui.essentialcraft5.enchanter.nothing";
        });
    }

    static int statusColor(int status) {
        return switch (status) {
            case MagicalEnchanterBlockEntity.STATUS_NOTHING -> 0xFFFF00;
            case MagicalEnchanterBlockEntity.STATUS_WORKING -> 0x00FF00;
            case MagicalEnchanterBlockEntity.STATUS_REDSTONE -> 0xFFAA00;
            default -> 0xFF0000;
        };
    }
}
