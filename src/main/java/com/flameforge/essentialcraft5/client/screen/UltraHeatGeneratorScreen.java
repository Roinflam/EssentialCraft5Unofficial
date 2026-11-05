package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.FlameProgressElement;
import com.flameforge.essentialcraft5.client.screen.element.MRUGeneratedElement;
import com.flameforge.essentialcraft5.client.screen.element.ScreenElement;
import com.flameforge.essentialcraft5.menu.UltraHeatGeneratorMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 终极热力产能器界面，元素与坐标照搬原作 GuiUltraHeatGenerator：
 * 原作 GuiMRUGenerated("ultraHeatGenerator") 是一条「N MRU/t」文字、右边 100 像素处的燃烧火苗，再加一行「Heat: N C」画在 (107, 30)，
 * 这里拆成三个元素。产量和温度都由服务端算好（原作界面自己另算一遍，和实际产量对不上）。文字条悬停时说明温度和产量的关系。
 */
public class UltraHeatGeneratorScreen extends MachineScreen<UltraHeatGeneratorMenu> {

    public UltraHeatGeneratorScreen(UltraHeatGeneratorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(mruState(25, 58));
        elements.add(balance(25, 22));
        elements.add(new MRUGeneratedElement(25, 40, menu::generation, "gui.essentialcraft5.ultra_heat_generator.hint", 5));
        elements.add(new FlameProgressElement(125, 42, menu::burnTime, menu::maxBurnTime));
        elements.add(new HeatElement(107, 30, menu));
    }

    /**
     * 「温度：N°C」一行字（原作没有底框，白字带阴影）。热度不封顶，五位数以上时整行会伸出界面右边框，
     * 所以超过 {@value #MAX_WIDTH} 像素就按比例缩小到这个宽度（x = 107 到边框内侧）。
     */
    private record HeatElement(int x, int y, UltraHeatGeneratorMenu menu) implements ScreenElement {

        private static final int MAX_WIDTH = 64;

        private Component text() {
            return Component.translatable("gui.essentialcraft5.heat", menu.heat());
        }

        @Override
        public void render(GuiGraphics graphics, int left, int top) {
            Font font = Minecraft.getInstance().font;
            Component text = text();
            int full = font.width(text);
            if (full <= MAX_WIDTH) {
                graphics.drawString(font, text, left, top, 0xFFFFFF, true);
                return;
            }
            float scale = (float) MAX_WIDTH / full;
            graphics.pose().pushPose();
            graphics.pose().translate(left, top + (9 - 9 * scale) / 2F, 0F);
            graphics.pose().scale(scale, scale, 1F);
            graphics.drawString(font, text, 0, 0, 0xFFFFFF, true);
            graphics.pose().popPose();
        }

        @Override
        public int width() {
            Font font = Minecraft.getInstance().font;
            return Math.min(MAX_WIDTH, font.width(text()));
        }

        @Override
        public int height() {
            return 9;
        }
    }
}
