package com.flameforge.essentialcraft5.client.screen.element;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * 「N MRU/t」产量文字条（宽 82）。原作 GuiMRUGenerated 的文字部分（火苗、植物图标拆成了别的元素）。
 * 悬停时显示几行说明，键为 {@code hintKey + ".0"}、{@code ".1"}……（原作界面没有说明，只能靠研究书）。
 */
public class MRUGeneratedElement extends TextBarElement {

    private final String hintKey;
    private final int hintLines;

    public MRUGeneratedElement(int x, int y, IntSupplier generation, String hintKey, int hintLines) {
        super(x, y, NORMAL_WIDTH, () -> Component.translatable("gui.essentialcraft5.mru_per_tick", generation.getAsInt()),
                () -> 0xFFFFFF, false);
        this.hintKey = hintKey;
        this.hintLines = hintLines;
    }

    @Override
    public List<Component> tooltip() {
        List<Component> lines = new ArrayList<>(hintLines);
        for (int i = 0; i < hintLines; i++) {
            lines.add(Component.translatable(hintKey + "." + i));
        }
        return lines;
    }
}
