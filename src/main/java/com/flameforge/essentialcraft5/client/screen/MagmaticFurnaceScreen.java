package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.OreProcessing.Status;
import com.flameforge.essentialcraft5.client.screen.element.ProgressBarElement;
import com.flameforge.essentialcraft5.menu.MagmaticFurnaceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 岩浆熔炉界面（四档共用），元素与坐标照搬原作 GuiFurnaceMagic：MRU 存量条 (7, 4)、MRU 数值 (25, 58)、平衡值 (25, 22)、
 * 绑定宝石状态 (25, 40)、进度箭头 (126, 4)（原作 GuiProgressBar_FurnaceMagic）。
 * 进度的总长由服务端按档位算好同步过来；悬停写当前工序、状态、进度和耗能（原作的箭头没有提示）。
 */
public class MagmaticFurnaceScreen extends MachineScreen<MagmaticFurnaceMenu> {

    private static final String KEY = "gui.essentialcraft5.magmatic_furnace.";

    public MagmaticFurnaceScreen(MagmaticFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(mruState(25, 58));
        elements.add(balance(25, 22));
        elements.add(boundGemState(25, 40));
        elements.add(new ProgressBarElement(126, 4, ProgressBarElement.Bar.ARROW_RIGHT, ProgressBarElement.Grow.RIGHT,
                menu::progress, menu::required, this::details));
    }

    private List<Component> details() {
        List<Component> lines = new ArrayList<>();
        Status status = menu.status();
        lines.add(Component.translatable(KEY + "stage", menu.mode().label(), status.label())
                .withStyle(style -> style.withColor(status.color)));
        int required = menu.required();
        lines.add(Component.translatable(KEY + "progress", Math.min(menu.progress(), required), required));
        lines.add(Component.translatable(KEY + "cost", menu.cost()));
        return lines;
    }
}
