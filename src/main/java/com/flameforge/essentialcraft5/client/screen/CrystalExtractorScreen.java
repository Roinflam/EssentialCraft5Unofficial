package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.blockentity.CrystalExtractorBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.ProgressBarElement;
import com.flameforge.essentialcraft5.menu.CrystalExtractorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 水晶提取器界面，元素与坐标照搬原作 GuiCrystalExtractor：MRU 存量条 (7, 4)、绑定宝石状态 (25, 4)、MRU 数值 (25, 58)。
 * <p>
 * 原作没有进度显示（一轮 1000 tick，玩家看不出机器在不在做）。产物格右边 (134~170, 22~58) 正好空着，
 * 放一个竖箭头进度条（progressbars.png 里原作没用上的那个），悬停看状态、进度和旁边水晶的数值。
 */
public class CrystalExtractorScreen extends MachineScreen<CrystalExtractorMenu> {

    private static final String KEY = "gui.essentialcraft5.crystal_extractor.";

    public CrystalExtractorScreen(CrystalExtractorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(boundGemState(25, 4));
        elements.add(mruState(25, 58));
        elements.add(new ProgressBarElement(147, 26, ProgressBarElement.Bar.ARROW_DOWN, ProgressBarElement.Grow.DOWN,
                menu::progress, menu::required, this::details));
    }

    private List<Component> details() {
        List<Component> lines = new ArrayList<>();
        Status status = menu.status();
        lines.add(status.label().withStyle(style -> style.withColor(status.color)));
        int required = menu.required();
        if (required > 0) {
            int done = Math.max(0, Math.min(menu.progress(), required));
            lines.add(Component.translatable("gui.essentialcraft5.progress", done, required, (int) ((long) done * 100 / required))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (menu.hasCrystal()) {
            lines.add(Component.translatable(KEY + "crystal", menu.size(),
                    menu.element(IElementalCrystal.Element.FIRE), menu.element(IElementalCrystal.Element.WATER),
                    menu.element(IElementalCrystal.Element.EARTH), menu.element(IElementalCrystal.Element.AIR))
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
