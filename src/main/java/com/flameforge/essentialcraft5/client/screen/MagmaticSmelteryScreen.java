package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.OreProcessing.Status;
import com.flameforge.essentialcraft5.client.screen.element.FluidTankElement;
import com.flameforge.essentialcraft5.client.screen.element.ProgressBarElement;
import com.flameforge.essentialcraft5.menu.MagmaticSmelteryMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * 岩浆冶炼炉界面，元素与坐标照搬原作 GuiMagmaticSmeltery：MRU 存量条 (7, 4)、岩浆罐 (25, 4)、绑定宝石状态 (43, 4)、
 * 平衡值 (43, 40)、MRU 数值 (25, 58)。
 * <p>
 * 原作没有进度显示，缺岩浆、缺 MRU、输出满了都看不出来。矿石列 (126) 和合金列 (155) 之间正好空着一条，
 * 放一根竖箭头：有矿在熔时显示熔矿进度，否则显示炼合金的进度；悬停写两道工序各自的状态、进度和耗能。
 */
public class MagmaticSmelteryScreen extends MachineScreen<MagmaticSmelteryMenu> {

    private static final String KEY = "gui.essentialcraft5.magmatic_smeltery.";

    public MagmaticSmelteryScreen(MagmaticSmelteryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(43, 40));
        elements.add(boundGemState(43, 4));
        elements.add(mruState(25, 58));
        elements.add(new FluidTankElement(25, 4, Fluids.LAVA, menu::lava, menu::lavaCapacity));
        elements.add(new ProgressBarElement(145, 5, ProgressBarElement.Bar.ARROW_DOWN, ProgressBarElement.Grow.DOWN,
                this::shownProgress, this::shownRequired, this::details));
    }

    /** 熔矿有进度时显示熔矿，否则显示炼合金（原作岩浆熔炉的进度条也是这样二选一） */
    private boolean showsOre() {
        return menu.oreProgress() > 0 || menu.oreStatus() == Status.WORKING || menu.alloyProgress() <= 0;
    }

    private int shownProgress() {
        return showsOre() ? menu.oreProgress() : menu.alloyProgress();
    }

    private int shownRequired() {
        return showsOre() ? menu.oreRequired() : menu.alloyRequired();
    }

    private List<Component> details() {
        List<Component> lines = new ArrayList<>();
        lines.add(stage("ore", menu.oreStatus(), menu.oreProgress(), menu.oreRequired()));
        lines.add(stage("alloy", menu.alloyStatus(), menu.alloyProgress(), menu.alloyRequired()));
        lines.add(Component.translatable(KEY + "cost", menu.oreCost(), menu.alloyCost()));
        return lines;
    }

    /** 「熔矿：工作中（120 / 400）」 */
    private static Component stage(String name, Status status, int progress, int required) {
        return Component.translatable(KEY + name, status.label(), Math.min(progress, required), required)
                .withStyle(style -> style.withColor(status.color));
    }
}
