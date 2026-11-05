package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.RadiatingChamberBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.RadiatingProgressElement;
import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.RadiatingChamberMenu;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 辐射仓界面，元素与坐标照搬原作 GuiRadiatingChamber：MRU 存量条 (7, 4)、绑定宝石状态 (25, 4)、
 * 平衡值 (25, 40)、MRU 数值 (25, 58)。
 * <p>
 * 原作没有进度显示，玩家看不出机器在不在做、差什么（没有 JEI，平衡值窗口也无从得知）。
 * 原作界面 (25, 22) 那一条正好空着，放一条进度条：条上写状态，悬停看进度、所需平衡值和耗能。
 */
public class RadiatingChamberScreen extends MachineScreen<RadiatingChamberMenu> {

    private static final String KEY = "gui.essentialcraft5.radiating_chamber.";

    public RadiatingChamberScreen(RadiatingChamberMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 40));
        elements.add(boundGemState(25, 4));
        elements.add(mruState(25, 58));
        elements.add(new RadiatingProgressElement(25, 22, TextBarElement.NORMAL_WIDTH, menu::progress, menu::required,
                this::barText, () -> menu.status().color, this::details));
    }

    /** 条上的字：工作中显示百分比，其余显示状态 */
    private Component barText() {
        Status status = menu.status();
        if (status == Status.WORKING && menu.required() > 0) {
            int percent = (int) ((long) Math.min(menu.progress(), menu.required()) * 100 / menu.required());
            return Component.translatable(KEY + "working_percent", percent);
        }
        // 解码故障核心（第十批 10c）：同样写百分比
        if (status == Status.DECODING && menu.required() > 0) {
            int percent = (int) ((long) Math.min(menu.progress(), menu.required()) * 100 / menu.required());
            return Component.translatable(KEY + "decoding_percent", percent);
        }
        return status.label();
    }

    private List<Component> details() {
        List<Component> lines = new ArrayList<>();
        Status status = menu.status();
        lines.add(status.label().withStyle(style -> style.withColor(status.color)));
        int required = menu.required();
        if (required > 0) {
            lines.add(Component.translatable(KEY + "progress", menu.progress(), required));
            float min = menu.balanceMin();
            float max = menu.balanceMax();
            // 原作不限平衡值的配方写的是 ±Float.MAX_VALUE，平衡值本身夹在 [0, 2]，所以覆盖 [0, 2] 就等于不限
            lines.add(min <= MRUBalance.MIN && max >= MRUBalance.MAX
                    ? Component.translatable(KEY + "balance_any")
                    : Component.translatable(KEY + "balance_range", formatBalance(min), formatBalance(max)));
            lines.add(Component.translatable(KEY + "cost", menu.costPerTick()));
        }
        return lines;
    }

    /**
     * 区间端点保留两位小数。纯净配方的区间在 JSON 里写成 0.50000006 ~ 1.4999999（为了和冰冻、混沌不重叠），
     * 两位小数显示为 0.50 ~ 1.50，和原作的写法一致。
     */
    private static String formatBalance(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
