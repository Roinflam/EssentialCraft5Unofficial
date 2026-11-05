package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * ESPE 存量球（18×18），从下往上填绿色。对应原作 GuiESPEStorage：
 * 空球取 mithrilinefurnaceelements.png 的 (0, 14)，满球取 (18, 14)，按 ⌊存量/上限×18⌋ 行从底部往上画。
 * 原作没有悬停提示，这里补上「存量/上限 ESPE」（和 MRU 存量条一样）。
 * <p>
 * 数值从菜单的 {@code MachineData.espe()/maxESPE()} 取（菜单用 serverWithESPE / clientWithESPE），
 * 一般直接用 {@code MachineScreen.espeStorage(x, y)}。
 *
 * <h2>原作顺带画的熔炉装饰</h2>
 * 原作 GuiESPEStorage 在球以外还画了两块米斯莱熔炉专用的图，这里拆出来，由熔炉界面自己加（坐标是熔炉界面里的，原作球在 (4, 64)）：
 * <ul>
 *   <li>输出槽外的水晶框：{@code new ImageElement(74, 15, ESPEStorageElement.TEXTURE, 0, 32, 28, 28)}（原作球坐标 +(70, −49)）；</li>
 *   <li>进度图标：{@code new ProgressBarElement(81, 48, ProgressBarElement.Bar.MITHRILINE_FURNACE, ProgressBarElement.Grow.UP, 当前, 所需)}
 *       （原作绿色底图在球坐标 +(77, −16)，红色进度 GuiProgressBar_MithrilineFurnace(81, 62) 从 y=62 往上长，是同一块区域）。</li>
 * </ul>
 * 注意 MachineScreen 先画槽位框再画元素，水晶框会盖住输出槽的框（原作也是这样，物品照常画在最上面）。
 */
public record ESPEStorageElement(int x, int y, DoubleSupplier espe, DoubleSupplier maxESPE) implements ScreenElement {

    /** 原作 mithrilinefurnaceelements.png（256×256）：(0,0) 绿色进度底图 14×14、(14,0) 红色进度 14×14、(0,14) 空球 18×18、(18,14) 满球 18×18、(0,32) 水晶框 28×28 */
    public static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/gui/mithrilinefurnaceelements.png");

    private static final int SIZE = 18;

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        graphics.blit(TEXTURE, left, top, 0, 14, SIZE, SIZE);
        int filled = filled(espe.getAsDouble(), maxESPE.getAsDouble());
        if (filled > 0) {
            graphics.blit(TEXTURE, left, top + SIZE - filled, 18, 14 + SIZE - filled, SIZE, filled);
        }
    }

    /** 原作 MathHelper.floor(current / max × 18)；上限为 0、NaN 时不填，超出上限按满算 */
    private static int filled(double current, double max) {
        if (!(max > 0) || !(current > 0)) {
            return 0;
        }
        return (int) Math.floor(Math.min(current, max) / max * SIZE);
    }

    @Override
    public int width() {
        return SIZE;
    }

    @Override
    public int height() {
        return SIZE;
    }

    @Override
    public List<Component> tooltip() {
        return List.of(Component.translatable("tooltip.essentialcraft5.espe",
                ESPEStorage.format(espe.getAsDouble()), ESPEStorage.format(maxESPE.getAsDouble())));
    }
}
