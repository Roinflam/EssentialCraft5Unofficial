package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.client.screen.ECTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 辐射仓的进度条（宽 width、高 18）。原作辐射仓界面没有任何进度显示，这是新加的：
 * 底框和 {@link TextBarElement} 一样，里面按进度从左往右填会动的 MRU 贴图（和 MRU 存量条同一张），
 * 上面居中写状态文字，悬停显示进度、平衡值区间等详情。
 */
public record RadiatingProgressElement(int x, int y, int width, IntSupplier progress, IntSupplier max,
                                       Supplier<Component> text, IntSupplier color,
                                       Supplier<List<Component>> details) implements ScreenElement {

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        TextBarElement.drawBar(graphics, left, top, width);
        // 框内 (width-2)×16 像素
        int filled = ECTextures.scaled(progress.getAsInt(), max.getAsInt(), width - 2);
        if (filled > 0) {
            ECTextures.spriteTiled(graphics, ECTextures.blockSprite(ECTextures.MRU_SPRITE), left + 1, top + 1, filled, 16);
        }
        Font font = Minecraft.getInstance().font;
        Component line = text.get();
        graphics.drawString(font, line, left + (width - font.width(line)) / 2, top + 5, color.getAsInt(), true);
    }

    @Override
    public int height() {
        return 18;
    }

    @Override
    public List<Component> tooltip() {
        return details.get();
    }
}
