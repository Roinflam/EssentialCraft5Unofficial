package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.ECTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 通用进度条：先画底图（空的箭头），再按 当前/最大 从某一边把亮的那张图画出来。
 * 对应原作 GuiProgressBar_FurnaceMagic（progressbars.png 的横箭头）、GuiProgressBar_MithrilineFurnace（熔炉的红色图标）这类元素，
 * 共性从 {@link MagicianTableProgressElement} 抽出来：像素按 {@link ECTextures#scaled} 向下取整，悬停提示「进度：当前 / 最大（百分比）」，
 * 最大值 ≤ 0 时显示「空闲」。
 * <p>
 * 原作 GuiProgressBar_FurnaceMagic 按 25 像素算宽度、箭头只有 24 宽，满的时候多画一列；这里按贴图区域本身的宽高算。
 *
 * <h2>用法</h2>
 * <pre>{@code
 * // 岩浆熔炉：原作 GuiProgressBar_FurnaceMagic，横箭头从左往右长
 * elements.add(new ProgressBarElement(x, y, ProgressBarElement.Bar.ARROW_RIGHT, ProgressBarElement.Grow.RIGHT,
 *         menu::progress, menu::required));
 * // 自定义提示（比如显示每 tick 耗能）：最后多传一个 Supplier<List<Component>>
 * elements.add(new ProgressBarElement(x, y, Bar.ARROW_DOWN, Grow.DOWN, menu::progress, menu::required,
 *         () -> List.of(Component.translatable("gui.essentialcraft5.xxx", menu.progress()))));
 * // 别的贴图：new ProgressBarElement.Bar(贴图, 底图 u, v, 亮图 u, v, 宽, 高)
 * }</pre>
 * 当前、最大都是 int（菜单同步的就是 int）；ESPE 这类小数进度同步时用 {@code MachineData.wideFixed}，这里传 {@code () -> (int) menu.progress()} 即可，
 * 只影响像素和提示里的数字取整。
 *
 * @param details 悬停提示；null 用默认的「进度 / 空闲」
 */
public record ProgressBarElement(int x, int y, Bar bar, Grow grow, IntSupplier current, IntSupplier max,
                                 @Nullable Supplier<List<Component>> details) implements ScreenElement {

    /** 原作 progressbars.png（256×256）：横箭头亮图 (0,0)、底图 (0,17)，各 24×17；竖箭头亮图 (24,0)、底图 (24,28)，各 9×28 */
    public static final ResourceLocation PROGRESS_BARS = EssentialCraft5.id("textures/gui/progressbars.png");

    /** 进度朝哪边长 */
    public enum Grow {
        /** 从左往右 */
        RIGHT,
        /** 从右往左 */
        LEFT,
        /** 从上往下 */
        DOWN,
        /** 从下往上 */
        UP;

        boolean horizontal() {
            return this == RIGHT || this == LEFT;
        }
    }

    /**
     * 进度条贴图的一块区域：底图和亮图大小相同，各自的左上角 (u, v)。
     *
     * @param textureWidth  整张贴图的宽（原作界面贴图都是 256）
     * @param textureHeight 整张贴图的高
     */
    public record Bar(ResourceLocation texture, int backgroundU, int backgroundV, int fillU, int fillV, int width, int height,
                      int textureWidth, int textureHeight) {

        /** 原作 GuiProgressBar_FurnaceMagic 的横箭头（岩浆熔炉），配 {@link Grow#RIGHT} */
        public static final Bar ARROW_RIGHT = new Bar(PROGRESS_BARS, 0, 17, 0, 0, 24, 17);
        /** progressbars.png 里的竖箭头（原作代码没用上），配 {@link Grow#DOWN} */
        public static final Bar ARROW_DOWN = new Bar(PROGRESS_BARS, 24, 28, 24, 0, 9, 28);
        /** 原作米斯莱熔炉：绿色图标为底、红色从下往上盖，配 {@link Grow#UP}（见 {@link ESPEStorageElement} 类注释的坐标） */
        public static final Bar MITHRILINE_FURNACE = new Bar(ESPEStorageElement.TEXTURE, 0, 0, 14, 0, 14, 14);

        /** 256×256 的贴图 */
        public Bar(ResourceLocation texture, int backgroundU, int backgroundV, int fillU, int fillV, int width, int height) {
            this(texture, backgroundU, backgroundV, fillU, fillV, width, height, 256, 256);
        }
    }

    /** 默认悬停提示（进度 / 空闲） */
    public ProgressBarElement(int x, int y, Bar bar, Grow grow, IntSupplier current, IntSupplier max) {
        this(x, y, bar, grow, current, max, null);
    }

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        int w = bar.width();
        int h = bar.height();
        blit(graphics, left, top, bar.backgroundU(), bar.backgroundV(), w, h);
        int filled = ECTextures.scaled(current.getAsInt(), max.getAsInt(), grow.horizontal() ? w : h);
        if (filled <= 0) {
            return;
        }
        switch (grow) {
            case RIGHT -> blit(graphics, left, top, bar.fillU(), bar.fillV(), filled, h);
            case LEFT -> blit(graphics, left + w - filled, top, bar.fillU() + w - filled, bar.fillV(), filled, h);
            case DOWN -> blit(graphics, left, top, bar.fillU(), bar.fillV(), w, filled);
            case UP -> blit(graphics, left, top + h - filled, bar.fillU(), bar.fillV() + h - filled, w, filled);
        }
    }

    private void blit(GuiGraphics graphics, int screenX, int screenY, int u, int v, int w, int h) {
        graphics.blit(bar.texture(), screenX, screenY, u, v, w, h, bar.textureWidth(), bar.textureHeight());
    }

    @Override
    public int width() {
        return bar.width();
    }

    @Override
    public int height() {
        return bar.height();
    }

    @Override
    public List<Component> tooltip() {
        if (details != null) {
            return details.get();
        }
        int total = max.getAsInt();
        if (total <= 0) {
            return List.of(Component.translatable("gui.essentialcraft5.progress.idle"));
        }
        int done = Math.max(0, Math.min(current.getAsInt(), total));
        return List.of(Component.translatable("gui.essentialcraft5.progress", done, total, (int) ((long) done * 100 / total)));
    }
}
