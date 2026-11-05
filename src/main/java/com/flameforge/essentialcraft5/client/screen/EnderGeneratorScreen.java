package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.EnderGeneratorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 末影产能器界面，元素与坐标照搬原作 GuiEnderGenerator：
 * MRU 条 (7, 4)、存量 (25, 58)、平衡值 (25, 22)、GuiMRUGenerated("enderGenerator") 的「N MRU/hit」(25, 40)。
 * 原作那条写死英文，这里用键 gui.essentialcraft5.mru_per_hit（hardcoded_strings.md 的建议键名），悬停时说明怎么产出。
 */
public class EnderGeneratorScreen extends MachineScreen<EnderGeneratorMenu> {

    private static final String HINT_KEY = "gui.essentialcraft5.ender_generator.hint";
    private static final int HINT_LINES = 3;

    public EnderGeneratorScreen(EnderGeneratorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(mruState(25, 58));
        elements.add(balance(25, 22));
        elements.add(new TextBarElement(25, 40, TextBarElement.NORMAL_WIDTH,
                () -> Component.translatable("gui.essentialcraft5.mru_per_hit", menu.generation()), () -> 0xFFFFFF, false) {
            @Override
            public List<Component> tooltip() {
                List<Component> lines = new ArrayList<>(HINT_LINES);
                for (int i = 0; i < HINT_LINES; i++) {
                    lines.add(Component.translatable(HINT_KEY + "." + i));
                }
                return lines;
            }
        });
    }
}
