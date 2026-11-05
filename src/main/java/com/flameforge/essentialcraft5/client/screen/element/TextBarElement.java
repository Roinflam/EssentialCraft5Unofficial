package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.client.screen.ECTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 一条带底框的文字（高 18）。对应原作 GuiTextElement（宽 82）及其子类：
 * GuiMRUState（宽 145）、GuiBalanceState、GuiBoundGemState、GuiMRUGenerated 等。
 * 底框用 slot_common.png 的槽位边拼出来：左端 17 像素，中间 16 像素一段段接，右端 17 像素。
 */
public class TextBarElement implements ScreenElement {

    public static final int NORMAL_WIDTH = 82;
    public static final int WIDE_WIDTH = 145;

    private final int x;
    private final int y;
    private final int width;
    private final Supplier<Component> text;
    private final IntSupplier color;
    private final boolean centered;

    public TextBarElement(int x, int y, int width, Supplier<Component> text, IntSupplier color, boolean centered) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.text = text;
        this.color = color;
        this.centered = centered;
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
        drawBar(graphics, left, top, width);
        Font font = Minecraft.getInstance().font;
        Component line = text.get();
        // 原作左对齐的从 x+2 起，离底框的边只隔 1 像素，而且和 StatusTextElement（x+4）摆在同一界面上下两行时差 2 像素对不齐，
        // 这里统一从 x+4 起；原作绑定宝石状态靠手调偏移大致居中，这里直接居中（中文宽度不一样）。
        // 字（含阴影）左右各留 4 像素，放不下时整条缩小，竖直方向仍居中
        int room = width - 8;
        int lineWidth = font.width(line);
        if (lineWidth <= room) {
            int textX = centered ? left + (width - lineWidth) / 2 : left + 4;
            graphics.drawString(font, line, textX, top + 5, color.getAsInt(), true);
            return;
        }
        float scale = room / (float) lineWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(left + 4, top + 9 - 4 * scale, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, line, 0, 0, color.getAsInt(), true);
        graphics.pose().popPose();
    }

    /** 画一条宽 width、高 18 的底框 */
    public static void drawBar(GuiGraphics graphics, int left, int top, int width) {
        graphics.blit(ECTextures.SLOT_COMMON, left, top, 0, 0, 17, 18);
        for (int dx = 17; dx < width - 17; dx += 16) {
            graphics.blit(ECTextures.SLOT_COMMON, left + dx, top, 1, 0, Math.min(16, width - 17 - dx), 18);
        }
        graphics.blit(ECTextures.SLOT_COMMON, left + width - 17, top, 1, 0, 17, 18);
    }
}
