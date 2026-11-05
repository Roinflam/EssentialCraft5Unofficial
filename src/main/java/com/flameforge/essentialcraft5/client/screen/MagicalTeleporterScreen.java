package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.MagicalTeleporterBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.MagicalTeleporterMenu;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.teleport.ECTeleport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔力传送器界面，元素与坐标照搬原作 GuiMagicalTeleporter：MRU 球 (7, 4)、平衡值 (25, 4)、0 号格宝石状态 (25, 22)、MRU 存量 (25, 58)；
 * 槽位 (108, 23)、(140, 23) 在菜单里。
 * <p>
 * 原作界面看不出为什么不传。原作空着的 (25, 40) 加一条状态（宽同 MRU 存量那条）：条上写现在卡在哪一步或蓄能进度，
 * 悬停看这一次的费用、落点预览（按原版玩家尺寸、不查权限）、结构哪一格不对。
 */
public class MagicalTeleporterScreen extends MachineScreen<MagicalTeleporterMenu> {

    private static final String KEY = "gui.essentialcraft5.magical_teleporter.";

    public MagicalTeleporterScreen(MagicalTeleporterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 4));
        elements.add(boundGemState(25, 22));
        elements.add(new StatusBar());
        elements.add(mruState(25, 58));
    }

    /** 条上的字：蓄能时带百分比，否则就是状态 */
    private Component barText() {
        Status status = menu.status();
        if (status == Status.CHARGING && menu.ticksRequired() > 0) {
            int percent = (int) Math.min(100L, 100L * menu.progress() / menu.ticksRequired());
            return Component.translatable(KEY + "charging_percent", percent);
        }
        return status.label();
    }

    private List<Component> details() {
        List<Component> lines = new ArrayList<>();
        Status status = menu.status();
        lines.add(status.label().withStyle(style -> style.withColor(status.color)));
        if (status == Status.INCOMPLETE) {
            BlockPos offset = menu.mismatchOffset();
            StructurePattern.Rule rule = offset == null ? null : ECVoidMachines.TELEPORTER_STRUCTURE.rules().get(offset);
            if (rule != null) {
                BlockPos world = StructurePattern.toWorld(menu.getPos(), offset, Direction.NORTH);
                lines.add(Component.translatable(StructurePattern.KEY_MISMATCH, rule.legend(), world.getX(), world.getY(), world.getZ()));
            }
            lines.add(Component.translatable(KEY + "see_guide").withStyle(ChatFormatting.GRAY));
        }
        if (status != Status.NO_DESTINATION) {
            lines.add(Component.translatable(menu.crossDimension() ? KEY + "cost_dimension" : KEY + "cost", menu.cost()));
            ECTeleport.Result preview = menu.preview();
            MutableComponent landing = preview == null
                    ? Component.translatable(KEY + "preview.unknown")
                    : Component.translatable(KEY + "preview." + preview.key());
            lines.add(Component.translatable(KEY + "landing", landing.withStyle(preview == ECTeleport.Result.OK ? ChatFormatting.GREEN
                    : preview == null || preview == ECTeleport.Result.UNLOADED ? ChatFormatting.YELLOW : ChatFormatting.RED)));
        }
        if (menu.progress() > 0) {
            lines.add(Component.translatable(KEY + "progress", menu.progress(), menu.ticksRequired()));
        }
        lines.add(Component.translatable(KEY + "hint").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /** 状态条：条上写状态，悬停看细节 */
    private final class StatusBar extends TextBarElement {

        StatusBar() {
            super(25, 40, TextBarElement.WIDE_WIDTH, MagicalTeleporterScreen.this::barText, () -> menu.status().color, true);
        }

        @Override
        public List<Component> tooltip() {
            return details();
        }
    }
}
