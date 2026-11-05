package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.MithrilineFurnaceBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.ESPEStorageElement;
import com.flameforge.essentialcraft5.client.screen.element.ImageElement;
import com.flameforge.essentialcraft5.client.screen.element.ProgressBarElement;
import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.menu.MithrilineFurnaceMenu;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 米斯莱熔炉界面，元素与坐标照搬原作 GuiMithrilineFurnace：ESPE 球 (4, 64)、进度图标 (81, 48)（原作画在 y=62 往上长）、
 * 产物槽外的水晶框 (74, 15)（原作由 GuiESPEStorage 顺带画）。
 * <p>
 * 原作界面上一个字都没有，结构不对、原料不够、ESPE 不够时玩家看不出为什么不动。左上角原作空着的地方加一条状态（宽 66，
 * 不碰产物槽外的水晶框），悬停看结构哪一格不对、接上了几处 ESPE 源、这条配方要几个原料和多少 ESPE。
 */
public class MithrilineFurnaceScreen extends MachineScreen<MithrilineFurnaceMenu> {

    private static final String KEY = "gui.essentialcraft5.mithriline_furnace.";
    /** 状态条宽度：左边到 x=70，水晶框从 x=74 开始 */
    private static final int STATUS_WIDTH = 66;

    public MithrilineFurnaceScreen(MithrilineFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(new StatusBar(4, 4, () -> menu.status().label(), () -> menu.status().color));
        elements.add(espeStorage(4, 64));
        // 进度按 ×100 取整画像素，1 ESPE 的配方也能看出进度
        elements.add(new ProgressBarElement(81, 48, ProgressBarElement.Bar.MITHRILINE_FURNACE, ProgressBarElement.Grow.UP,
                () -> (int) Math.round(menu.progress() * 100D), () -> (int) Math.round(menu.required() * 100D), this::progressDetails));
        // 装饰图放在最后：和别的元素重叠时不挡它们的提示
        elements.add(new ImageElement(74, 15, ESPEStorageElement.TEXTURE, 0, 32, 28, 28));
    }

    private List<Component> progressDetails() {
        double required = menu.required();
        if (required <= 0) {
            return List.of(Component.translatable("gui.essentialcraft5.progress.idle"));
        }
        return List.of(Component.translatable(KEY + "progress", ESPEStorage.format(menu.progress()), ESPEStorage.format(required)));
    }

    private List<Component> statusDetails() {
        List<Component> lines = new ArrayList<>();
        Status status = menu.status();
        lines.add(status.label().withStyle(style -> style.withColor(status.color)));
        switch (status) {
            case INCOMPLETE -> {
                BlockPos offset = menu.mismatchOffset();
                StructurePattern.Rule rule = offset == null ? null : ECMithriline.FURNACE_STRUCTURE.rules().get(offset);
                if (rule != null) {
                    BlockPos world = StructurePattern.toWorld(menu.getPos(), offset, Direction.NORTH);
                    lines.add(Component.translatable(StructurePattern.KEY_MISMATCH, rule.legend(), world.getX(), world.getY(), world.getZ()));
                }
                lines.add(Component.translatable(KEY + "see_guide").withStyle(ChatFormatting.GRAY));
            }
            case UNLOADED -> lines.add(Component.translatable("structure.essentialcraft5.unknown"));
            default -> lines.add(Component.translatable(KEY + "sources", menu.sources()));
        }
        if (menu.required() > 0 || menu.countNeeded() > 0) {
            lines.add(Component.translatable(KEY + "recipe", menu.countNeeded(), ESPEStorage.format(menu.required())));
        }
        return lines;
    }

    /** 状态条：条上写状态，悬停看细节 */
    private final class StatusBar extends TextBarElement {

        StatusBar(int x, int y, Supplier<Component> text, IntSupplier color) {
            super(x, y, STATUS_WIDTH, text, color, true);
        }

        @Override
        public List<Component> tooltip() {
            return statusDetails();
        }
    }
}
