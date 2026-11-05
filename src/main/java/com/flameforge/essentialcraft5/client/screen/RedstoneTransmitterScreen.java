package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.RedstoneTransmitterBlockEntity.Link;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.menu.RedstoneTransmitterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * 红石传递器界面：原作是通用界面 GuiCommon，只有一个宝石槽 (29, 32)。
 * 原作没有的：宝石右边 (50, 31) 一条连接状态（放入宝石 / 已连接 / 目标没加载……），右端写当前输出的信号强度；
 * 悬停写状态、一句怎么办（{@code status.<状态>.hint}）和输出强度。
 */
public class RedstoneTransmitterScreen extends MachineScreen<RedstoneTransmitterMenu> {

    public RedstoneTransmitterScreen(RedstoneTransmitterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(new StatusTextElement(50, 31, 118, this::statusText, this::statusColor,
                () -> String.valueOf(menu.power()), 100, this::tooltip));
    }

    private Component statusText() {
        return Component.translatable(key(menu.link()));
    }

    private int statusColor() {
        return switch (menu.link()) {
            case LINKED -> 0x00FF00;
            case NO_GEM, UNBOUND -> 0xFFFF00;
            default -> 0xFF5555;
        };
    }

    private List<Component> tooltip() {
        return List.of(
                Component.translatable(key(menu.link())),
                Component.translatable(key(menu.link()) + ".hint").withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.essentialcraft5.redstone_transmitter.power", menu.power()).withStyle(ChatFormatting.GRAY));
    }

    private static String key(Link link) {
        return "gui.essentialcraft5.redstone_transmitter.status." + link.name().toLowerCase(Locale.ROOT);
    }
}
