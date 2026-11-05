package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.api.IElementalCrystal.Element;
import com.flameforge.essentialcraft5.blockentity.ElementalDecomposerBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.ScreenElement;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.menu.ElementalDecomposerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 元素分解器界面（batch12.md §4.8.2）：MRU 条 (7, 4)、状态 (27, 4)、绑定宝石格在状态右边，3×3 输入格；输入格和输出格之间
 * 四行元素点数「火 12/32」（按元素着色，从上到下火、水、地、风，和右边输出格的行序一样），放不下时整行缩小。
 * 状态悬停写一句怎么办，再写绑定宝石的状态（放不下单独那一条）。
 */
public class ElementalDecomposerScreen extends MachineScreen<ElementalDecomposerMenu> {

    private static final String KEY = "gui.essentialcraft5.elemental_decomposer.";
    /** 四种元素的字色（火红、水蓝、地绿、风淡黄），下标同 Element.ordinal() */
    private static final int[] COLORS = {0xFF7755, 0x66AAFF, 0x99CC55, 0xEEEE99};

    public ElementalDecomposerScreen(ElementalDecomposerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(new StatusTextElement(27, 4, 82, () -> menu.status().label(), () -> menu.status().color,
                null, 0, this::statusTooltip));
        for (Element element : Element.values()) {
            elements.add(new PointsLine(element));
        }
    }

    private List<Component> statusTooltip() {
        Status status = menu.status();
        List<Component> lines = new ArrayList<>();
        lines.add(status.label().withStyle(style -> style.withColor(status.color)));
        lines.add(status.hint().withStyle(ChatFormatting.GRAY));
        lines.add(data().linkStatus().label().copy().withStyle(style -> style.withColor(data().linkStatus().color)));
        return lines;
    }

    static Component elementName(Element element) {
        return Component.translatable(KEY + "element." + element.getSerializedName());
    }

    /**
     * 一行元素点数。第一行输出格和状态字、宝石格同高，点数没法逐行对齐输出格，所以四行挤在输入格右边那一列（84~131）里，
     * 竖直方向跟着输入格走。
     */
    private final class PointsLine implements ScreenElement {
        private static final int X = 84;
        private static final int TOP = 26;
        private static final int LINE = 12;
        private static final int WIDTH = 48;
        private final Element element;

        PointsLine(Element element) {
            this.element = element;
        }

        @Override
        public int x() {
            return X;
        }

        @Override
        public int y() {
            return TOP + element.ordinal() * LINE;
        }

        @Override
        public int width() {
            return WIDTH;
        }

        @Override
        public int height() {
            return LINE;
        }

        @Override
        public void render(GuiGraphics graphics, int left, int top) {
            Font font = Minecraft.getInstance().font;
            Component line = Component.translatable(KEY + "points", elementName(element), menu.points(element), menu.perEssence());
            int lineWidth = font.width(line);
            if (lineWidth <= WIDTH) {
                graphics.drawString(font, line, left, top + 2, COLORS[element.ordinal()], true);
                return;
            }
            // 英文元素名或很大的 points_per_essence 放不下：整行缩小，竖直居中
            float scale = WIDTH / (float) lineWidth;
            graphics.pose().pushPose();
            graphics.pose().translate(left, top + LINE / 2F - 4 * scale, 0);
            graphics.pose().scale(scale, scale, 1F);
            graphics.drawString(font, line, 0, 0, COLORS[element.ordinal()], true);
            graphics.pose().popPose();
        }

        @Override
        public List<Component> tooltip() {
            return List.of(Component.translatable(KEY + "points.tooltip", elementName(element), menu.points(element), menu.perEssence()));
        }
    }
}
