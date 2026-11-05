package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.MatrixAbsorberMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 矩阵换能器界面，元素与坐标照搬原作 GuiMatrixAbsorber。
 * 原作 GuiMRUGenerated("matrixAbsorber") 是一条宽 82 的文字条，写死「10UBMRU > 1MRU」；
 * 这里改成可翻译的「10 UBMRU → 1 MRU」，加了空格后放不进 82 像素，底框换成宽条（145，右边本来就空着）。
 */
public class MatrixAbsorberScreen extends MachineScreen<MatrixAbsorberMenu> {

    public MatrixAbsorberScreen(MatrixAbsorberMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(mruState(25, 58));
        elements.add(balance(25, 22));
        elements.add(new TextBarElement(25, 40, TextBarElement.WIDE_WIDTH,
                () -> Component.translatable("gui.essentialcraft5.matrix_absorber.rate", menu.usage(), menu.generated()),
                () -> 0xFFFFFF, false));
    }
}
