package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.ESPEStorageElement;
import com.flameforge.essentialcraft5.client.screen.element.MRUStorageElement;
import com.flameforge.essentialcraft5.client.screen.element.ScreenElement;
import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.MachineData;
import com.flameforge.essentialcraft5.menu.MachineMenu;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * 机器界面基类。对应原作 DummyCore 的 GuiCommon：
 * 背景用原版发射器界面，把中间的 3×3 格子盖掉，再给每个槽位画框，然后画各个元素。
 * 原作不画标题和「物品栏」字样，这里也不画（元素占了标题的位置）。
 * <p>
 * 数值全部从菜单的 {@link MachineData} 取（服务端同步来的），不读客户端方块实体。
 */
public abstract class MachineScreen<M extends MachineMenu> extends AbstractContainerScreen<M> {

    protected final List<ScreenElement> elements = new ArrayList<>();

    protected MachineScreen(M menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    protected MachineData data() {
        return menu.getData();
    }

    // ---- 原作通用元素（坐标由各界面传入，与原作 Gui 类里的数字一致） ----

    /** 原作 GuiMRUStorage */
    protected ScreenElement mruStorage(int x, int y) {
        return new MRUStorageElement(x, y, data()::mru, data()::maxMRU);
    }

    /** 原作 GuiESPEStorage（只有球，熔炉装饰见 ESPEStorageElement 类注释）。菜单要用 MachineData.serverWithESPE / clientWithESPE */
    protected ScreenElement espeStorage(int x, int y) {
        return new ESPEStorageElement(x, y, data()::espe, data()::maxESPE);
    }

    /** 原作 GuiMRUState：「存量/上限 MRU」 */
    protected ScreenElement mruState(int x, int y) {
        return new TextBarElement(x, y, TextBarElement.WIDE_WIDTH,
                () -> Component.translatable("tooltip.essentialcraft5.mru", data().mru(), data().maxMRU()),
                () -> 0xFFFFFF, false);
    }

    /** 原作 GuiBalanceState：「纯净: 1.0」，按冰冻/纯净/混沌着色 */
    protected ScreenElement balance(int x, int y) {
        return new TextBarElement(x, y, TextBarElement.NORMAL_WIDTH,
                () -> MRUBalance.describe(data().balance()),
                () -> MRUBalance.kind(data().balance()).textColor, false);
    }

    /** 原作 GuiBoundGemState：绑定宝石是否在工作 */
    protected ScreenElement boundGemState(int x, int y) {
        return new TextBarElement(x, y, TextBarElement.NORMAL_WIDTH,
                () -> data().linkStatus().label(),
                () -> data().linkStatus().color, true);
    }

    // ---- 绘制 ----

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(ECTextures.GENERIC_BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // 盖掉发射器中间的 3×3 格子
        graphics.blit(ECTextures.GENERIC_BACKGROUND, leftPos + 60, topPos + 16, 6, 6, 28, 54);
        graphics.blit(ECTextures.GENERIC_BACKGROUND, leftPos + 88, topPos + 16, 6, 6, 28, 54);
        for (Slot slot : menu.slots) {
            graphics.blit(ECTextures.GENERIC_BACKGROUND, leftPos + slot.x - 1, topPos + slot.y - 1, 7, 83, 18, 18);
        }
        for (ScreenElement element : elements) {
            element.render(graphics, leftPos + element.x(), topPos + element.y());
            graphics.setColor(1F, 1F, 1F, 1F);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (menu.getCarried().isEmpty() && hoveredSlot == null) {
            for (ScreenElement element : elements) {
                int ex = leftPos + element.x();
                int ey = topPos + element.y();
                if (mouseX >= ex && mouseX < ex + element.width() && mouseY >= ey && mouseY < ey + element.height()) {
                    List<Component> lines = element.tooltip();
                    if (!lines.isEmpty()) {
                        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                    }
                    break;
                }
            }
        }
    }
}
