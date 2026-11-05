package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.client.screen.ECTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.function.IntSupplier;

/**
 * 燃烧进度的小火苗（15×15），剩余时间越少火苗越矮。原作 GuiMRUGenerated 里热力产能器、自然熔炉两个分支画的那个：
 * 底图取原版熔炉界面 (55, 36) 那块（空火苗槽），亮的火苗取 (176, 0)。
 * <p>
 * 原作亮火苗的高度按 15 像素算、从贴图第 15 行往上取，会多带出一行下面箭头图案的边；这里照原版熔炉按 13 级画。
 */
public record FlameProgressElement(int x, int y, IntSupplier burnTime, IntSupplier maxBurnTime) implements ScreenElement {

    /** 1.20.1 的熔炉界面还是整张贴图（1.20.2 才拆成精灵） */
    private static final ResourceLocation FURNACE = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        graphics.blit(FURNACE, left, top, 55, 36, 15, 15);
        int burn = burnTime.getAsInt();
        int max = maxBurnTime.getAsInt();
        if (burn > 0 && max > 0) {
            int lit = ECTextures.scaled(burn, max, 13);
            // 同原版 AbstractFurnaceScreen：火苗底边对齐第 12 行，往上长 lit+1 行
            graphics.blit(FURNACE, left + 1, top + 12 - lit, 176, 12 - lit, 14, lit + 1);
        }
    }

    @Override
    public int width() {
        return 15;
    }

    @Override
    public int height() {
        return 15;
    }
}
