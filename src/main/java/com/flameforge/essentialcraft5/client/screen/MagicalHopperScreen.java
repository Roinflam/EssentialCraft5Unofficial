package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.MagicalHopperMenu;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 魔力漏斗界面（原作用通用的 GuiCommon：只有中间一个过滤器格，没有 MRU 条）。
 * 新加两条状态（原作这里空着，平时都不画）：顶上 (16, 6) 在「没有主人 / 主人不在线」时说明为什么不搬，底下 (16, 56) 在有红石信号时写「停工」。
 * 两条都用宽条（145 像素）：界面上别的元素只有中间一个格子，英文也放得下。
 * 指着空的过滤器格、手上没拿东西时提示放什么。
 */
public class MagicalHopperScreen extends MachineScreen<MagicalHopperMenu> {

    /** 宽条居中：(176 - 145) / 2 */
    private static final int BAR_X = 16;
    private static final List<Component> FILTER_HINT = List.of(
            Component.translatable("gui.essentialcraft5.magical_hopper.filter_hint.0"),
            Component.translatable("gui.essentialcraft5.magical_hopper.filter_hint.1").withStyle(ChatFormatting.GRAY));

    public MagicalHopperScreen(MagicalHopperMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(new ConditionalBar(BAR_X, 6, () -> ownerShown(menu.ownerState()),
                () -> Component.translatable(menu.ownerState() == ECProtection.OwnerState.NONE
                        ? "gui.essentialcraft5.owner.none" : "gui.essentialcraft5.owner.offline"),
                () -> menu.ownerState() == ECProtection.OwnerState.NONE ? ChatFormatting.RED : ChatFormatting.GOLD,
                () -> {
                    String key = menu.ownerState() == ECProtection.OwnerState.NONE
                            ? "gui.essentialcraft5.magical_hopper.owner_none.hint" : "gui.essentialcraft5.magical_hopper.owner_offline.hint";
                    return List.of(Component.translatable(key + ".0"), Component.translatable(key + ".1").withStyle(ChatFormatting.GRAY));
                }));
        elements.add(new ConditionalBar(BAR_X, 56, menu::redstonePaused,
                () -> Component.translatable("gui.essentialcraft5.magical_hopper.redstone_paused"),
                () -> ChatFormatting.RED,
                () -> List.of(Component.translatable("gui.essentialcraft5.magical_hopper.redstone_paused.hint").withStyle(ChatFormatting.GRAY))));
    }

    private static boolean ownerShown(ECProtection.OwnerState state) {
        return state == ECProtection.OwnerState.NONE || state == ECProtection.OwnerState.OFFLINE;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && !menu.slots.isEmpty() && hoveredSlot == menu.slots.get(0)
                && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.renderComponentTooltip(font, FILTER_HINT, mouseX, mouseY);
        }
    }

    /** 只在条件成立时画出来（也只在那时有悬停提示）的状态条 */
    private static final class ConditionalBar extends TextBarElement {
        private final BooleanSupplier shown;
        private final Supplier<List<Component>> hint;

        ConditionalBar(int x, int y, BooleanSupplier shown, Supplier<Component> text, Supplier<ChatFormatting> color,
                       Supplier<List<Component>> hint) {
            super(x, y, WIDE_WIDTH, text, () -> rgb(color.get()), true);
            this.shown = shown;
            this.hint = hint;
        }

        private static int rgb(ChatFormatting formatting) {
            Integer value = formatting.getColor();
            return value == null ? 0xFFFFFF : value;
        }

        @Override
        public void render(GuiGraphics graphics, int left, int top) {
            if (shown.getAsBoolean()) {
                super.render(graphics, left, top);
            }
        }

        @Override
        public List<Component> tooltip() {
            return shown.getAsBoolean() ? hint.get() : List.of();
        }
    }
}
