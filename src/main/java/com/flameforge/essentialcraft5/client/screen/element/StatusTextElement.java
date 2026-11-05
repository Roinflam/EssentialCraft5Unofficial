package com.flameforge.essentialcraft5.client.screen.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 带底框的状态文字，可选在右端再画一个数字、可选悬停提示。对应原作 GuiRepairState（宽 114）、
 * GuiEnchantmentState（宽 145，右端 x+122 处画书架等级）。底框和 {@link TextBarElement} 一样拼。
 * 文字、颜色、数字、提示都是现取的（数值来自菜单同步的 MachineData）。
 */
public class StatusTextElement implements ScreenElement {

    private final int x;
    private final int y;
    private final int width;
    private final Supplier<Component> text;
    private final IntSupplier color;
    /** 右端的数字（原作附魔台的书架等级）；null 不画 */
    @Nullable
    private final Supplier<String> rightText;
    private final int rightX;
    @Nullable
    private final Supplier<List<Component>> tooltip;

    public StatusTextElement(int x, int y, int width, Supplier<Component> text, IntSupplier color,
                             @Nullable Supplier<String> rightText, int rightX, @Nullable Supplier<List<Component>> tooltip) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.text = text;
        this.color = color;
        this.rightText = rightText;
        this.rightX = rightX;
        this.tooltip = tooltip;
    }

    /** 只有文字 */
    public StatusTextElement(int x, int y, int width, Supplier<Component> text, IntSupplier color) {
        this(x, y, width, text, color, null, 0, null);
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return 18;
    }

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        TextBarElement.drawBar(graphics, left, top, width);
        Font font = Minecraft.getInstance().font;
        // 原作文字从 x+4~6 起画；中文宽度不一样，这里统一从 x+4 起，放不下时整条缩小
        Component line = text.get();
        int room = (rightText != null ? rightX - 2 : width - 4) - 4;
        int lineWidth = font.width(line);
        if (lineWidth <= room) {
            graphics.drawString(font, line, left + 4, top + 5, color.getAsInt(), true);
        } else {
            float scale = room / (float) lineWidth;
            graphics.pose().pushPose();
            graphics.pose().translate(left + 4, top + 9 - 4 * scale, 0);
            graphics.pose().scale(scale, scale, 1F);
            graphics.drawString(font, line, 0, 0, color.getAsInt(), true);
            graphics.pose().popPose();
        }
        if (rightText != null) {
            graphics.drawString(font, rightText.get(), left + rightX, top + 5, 0xFFFFFF, true);
        }
    }

    @Override
    public List<Component> tooltip() {
        return tooltip == null ? List.of() : tooltip.get();
    }
}
