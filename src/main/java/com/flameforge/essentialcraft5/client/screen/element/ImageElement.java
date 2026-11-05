package com.flameforge.essentialcraft5.client.screen.element;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 一块静态贴图（装饰用，没有悬停提示）。原作不少 Gui 元素会顺手画几块固定的图（例：GuiESPEStorage 画的熔炉水晶框），
 * 移植时拆成这个元素，由各自的界面加。贴图按 256×256 算（原作界面贴图都是这个尺寸）。
 * <p>
 * MachineScreen 找悬停提示时停在第一个盖住鼠标的元素上，这个元素没有提示，和别的元素重叠时要加在它们<b>后面</b>，免得挡住它们的提示。
 * <pre>{@code
 * elements.add(new ImageElement(74, 15, ESPEStorageElement.TEXTURE, 0, 32, 28, 28));
 * }</pre>
 */
public record ImageElement(int x, int y, ResourceLocation texture, int u, int v, int width, int height) implements ScreenElement {

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        graphics.blit(texture, left, top, u, v, width, height);
    }
}
