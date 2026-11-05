package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.MagicalFurnaceBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.MagicalFurnaceMenu;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔力熔炉界面，元素与坐标照搬原作 GuiMagicalFurnace：MRU 球 (7, 4)、平衡值 (25, 4)、宝石状态 (25, 22)、MRU 存量 (25, 58)；宝石格 (108, 23) 在菜单里。
 * <p>
 * 原作界面上看不出熔炉在干什么。原作空着的 (25, 40) 加一条状态（宽同 MRU 存量那条）：熔炼时写进度，停住时写原因，
 * 悬停看结构哪一格不对、东西往哪儿扔、产物落在哪。
 */
public class MagicalFurnaceScreen extends MachineScreen<MagicalFurnaceMenu> {

    private static final String KEY = "gui.essentialcraft5.magical_furnace.";

    public MagicalFurnaceScreen(MagicalFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 4));
        elements.add(boundGemState(25, 22));
        elements.add(new StatusBar());
        elements.add(mruState(25, 58));
    }

    private Component barText() {
        Status status = menu.status();
        if (status == Status.SMELTING && menu.ticksRequired() > 0) {
            return Component.translatable(KEY + "progress", menu.progress(), menu.ticksRequired());
        }
        return status.label();
    }

    private List<Component> details() {
        List<Component> lines = new ArrayList<>();
        Status status = menu.status();
        lines.add(status.label().withStyle(style -> style.withColor(status.color)));
        if (status == Status.INCOMPLETE) {
            BlockPos offset = menu.mismatchOffset();
            StructurePattern.Rule rule = offset == null ? null : ECVoidMachines.FURNACE_STRUCTURE.rules().get(offset);
            if (rule != null) {
                BlockPos world = StructurePattern.toWorld(menu.getPos(), offset, Direction.NORTH);
                lines.add(Component.translatable(StructurePattern.KEY_MISMATCH, rule.legend(), world.getX(), world.getY(), world.getZ()));
            }
            lines.add(Component.translatable(KEY + "see_guide").withStyle(ChatFormatting.GRAY));
        }
        if (menu.progress() > 0 && status != Status.SMELTING) {
            lines.add(Component.translatable(KEY + "progress", menu.progress(), menu.ticksRequired()));
        }
        lines.add(Component.translatable(KEY + "hint.0").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(KEY + "hint.1").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /** 状态条：条上写状态，悬停看细节 */
    private final class StatusBar extends TextBarElement {

        StatusBar() {
            super(25, 40, TextBarElement.WIDE_WIDTH, MagicalFurnaceScreen.this::barText, () -> menu.status().color, true);
        }

        @Override
        public List<Component> tooltip() {
            return details();
        }
    }
}
