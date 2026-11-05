package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.AnimalSeparatorMenu;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * 动物分离器界面。原作借用射线塔的界面（GuiRayTower），元素与坐标照搬 {@link RayTowerScreen}：
 * MRU 球 (7, 4)、平衡值 (25, 4)、绑定宝石状态 (25, 22)、MRU 数值 (25, 58)。
 * <p>
 * 新加：(25, 40)（原作这里空着）在「没有主人 / 主人不在线」时多一条状态，悬停说明怎么办；能工作时不画。
 */
public class AnimalSeparatorScreen extends MachineScreen<AnimalSeparatorMenu> {

    public AnimalSeparatorScreen(AnimalSeparatorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 4));
        elements.add(boundGemState(25, 22));
        elements.add(new OwnerStateElement(menu));
        elements.add(mruState(25, 58));
    }

    /** 主人状态条：没有主人红字、主人不在线金字；能工作（或还没同步到）时整条不画、没有悬停提示 */
    private static final class OwnerStateElement extends TextBarElement {
        private final AnimalSeparatorMenu menu;

        OwnerStateElement(AnimalSeparatorMenu menu) {
            super(25, 40, NORMAL_WIDTH, () -> label(menu.ownerState()), () -> color(menu.ownerState()), true);
            this.menu = menu;
        }

        private static boolean shown(ECProtection.OwnerState state) {
            return state == ECProtection.OwnerState.NONE || state == ECProtection.OwnerState.OFFLINE;
        }

        private static Component label(ECProtection.OwnerState state) {
            return Component.translatable(state == ECProtection.OwnerState.NONE
                    ? "gui.essentialcraft5.owner.none" : "gui.essentialcraft5.owner.offline");
        }

        private static int color(ECProtection.OwnerState state) {
            Integer rgb = (state == ECProtection.OwnerState.NONE ? ChatFormatting.RED : ChatFormatting.GOLD).getColor();
            return rgb == null ? 0xFFFFFF : rgb;
        }

        @Override
        public void render(GuiGraphics graphics, int left, int top) {
            if (shown(menu.ownerState())) {
                super.render(graphics, left, top);
            }
        }

        @Override
        public List<Component> tooltip() {
            ECProtection.OwnerState state = menu.ownerState();
            if (!shown(state)) {
                return List.of();
            }
            String key = state == ECProtection.OwnerState.NONE
                    ? "gui.essentialcraft5.animal_separator.owner_none.hint"
                    : "gui.essentialcraft5.animal_separator.owner_offline.hint";
            return List.of(Component.translatable(key + ".0"), Component.translatable(key + ".1").withStyle(ChatFormatting.GRAY));
        }
    }
}
