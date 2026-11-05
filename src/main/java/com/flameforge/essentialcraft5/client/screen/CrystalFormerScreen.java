package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.CrystalFormerBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.ProgressBarElement;
import com.flameforge.essentialcraft5.menu.CrystalFormerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 水晶成型器界面，元素与坐标照搬原作 GuiCrystalFormer：MRU 存量条 (7, 4)、绑定宝石状态 (25, 22)、
 * 平衡值 (25, 40)、MRU 数值 (25, 58)。
 * <p>
 * 原作没有进度显示（1 000 tick 里看不出机器在不在做、差什么）。宝石槽 (26, 5) 和产物槽 (82, 5) 之间正好空着一块，
 * 放一个横箭头进度条（progressbars.png 的 24×17 箭头），悬停看状态、进度和耗能。
 */
public class CrystalFormerScreen extends MachineScreen<CrystalFormerMenu> {

    private static final String KEY = "gui.essentialcraft5.crystal_former.";

    public CrystalFormerScreen(CrystalFormerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(mruState(25, 58));
        elements.add(boundGemState(25, 22));
        elements.add(balance(25, 40));
        elements.add(new ProgressBarElement(50, 5, ProgressBarElement.Bar.ARROW_RIGHT, ProgressBarElement.Grow.RIGHT,
                menu::progress, menu::required, this::details));
    }

    private List<Component> details() {
        List<Component> lines = new ArrayList<>();
        Status status = menu.status();
        lines.add(status.label().withStyle(style -> style.withColor(status.color)));
        int required = menu.required();
        if (required > 0) {
            int done = Math.max(0, Math.min(menu.progress(), required));
            lines.add(Component.translatable("gui.essentialcraft5.progress", done, required, (int) ((long) done * 100 / required)));
        }
        lines.add(Component.translatable(KEY + "cost", menu.costPerTick()));
        if (status == Status.IDLE) {
            lines.add(Component.translatable(KEY + "needs"));
        }
        return lines;
    }
}
