package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.MagicalCultivatorBlockEntity.Status;
import com.flameforge.essentialcraft5.client.CultivatorClient;
import com.flameforge.essentialcraft5.client.screen.element.ProgressBarElement;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.menu.MagicalCultivatorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔力栽培器界面（batch12.md §4.2.2）：MRU 条 (7, 4)；状态 (27, 4)、绑定宝石状态 (27, 22)、「16 株 · 每轮 2 分钟」(27, 40)；
 * 种子格 / 水格 / 催长格一排在 y = 60，右边横箭头进度条，再往右是 3×3 输出格。状态悬停给一句怎么办。
 */
public class MagicalCultivatorScreen extends MachineScreen<MagicalCultivatorMenu> {

    private static final String KEY = "gui.essentialcraft5.magical_cultivator.";

    public MagicalCultivatorScreen(MagicalCultivatorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(new StatusTextElement(27, 4, 82, () -> menu.status().label(), () -> menu.status().color,
                null, 0, this::statusTooltip));
        elements.add(boundGemState(27, 22));
        elements.add(new StatusTextElement(27, 40, 82, this::roundLine, () -> 0xFFFFFF));
        elements.add(new ProgressBarElement(MagicalCultivatorMenu.ARROW_X, MagicalCultivatorMenu.SLOTS_Y, ProgressBarElement.Bar.ARROW_RIGHT,
                ProgressBarElement.Grow.RIGHT, menu::progress, menu::roundTicks, this::progressTooltip));
    }

    /** 「16 株 · 每轮 2 分钟」；种子格空着或种不了时只写「—」 */
    private Component roundLine() {
        int plants = menu.plants();
        int ticks = menu.displayTicks();
        if (plants <= 0 || ticks <= 0) {
            return Component.literal("—");
        }
        return Component.translatable(KEY + "round", plants, CultivatorClient.duration(ticks));
    }

    private List<Component> statusTooltip() {
        Status status = menu.status();
        List<Component> lines = new ArrayList<>();
        lines.add(status.label().withStyle(style -> style.withColor(status.color)));
        lines.add(status.hint().withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private List<Component> progressTooltip() {
        List<Component> lines = new ArrayList<>();
        Status status = menu.status();
        lines.add(status.label().withStyle(style -> style.withColor(status.color)));
        int required = menu.roundTicks();
        if (required > 0) {
            int done = Math.max(0, Math.min(menu.progress(), required));
            lines.add(Component.translatable("gui.essentialcraft5.progress", done, required, (int) ((long) done * 100 / required))
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
