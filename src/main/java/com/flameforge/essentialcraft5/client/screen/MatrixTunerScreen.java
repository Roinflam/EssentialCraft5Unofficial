package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.MatrixTunerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MatrixTunerBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MatrixTunerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigDecimal;
import java.util.List;

/**
 * 矩阵调律器界面。左边 MRU 条 (7, 4)，状态条 (27, 4)（悬停写这个状态是什么意思），右上 ESPE 球 (134, 4)；
 * 第二行写正在调的人还剩多少损伤，第三行写单价，第四行是私人 / 公开按钮；绑定宝石格在 (152, 44)。
 * 单价读服务端配置（进服时 Forge 发给客户端），状态和数值都从菜单同步来，不读客户端方块实体。
 */
public class MatrixTunerScreen extends MachineScreen<MatrixTunerMenu> {

    private static final String KEY = "gui.essentialcraft5.matrix_tuner.";

    private Button modeButton;

    public MatrixTunerScreen(MatrixTunerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(new StatusTextElement(27, 4, 104, () -> statusText(menu.status()), () -> menu.status().color,
                null, 0, () -> List.of(Component.translatable(KEY + "status." + key(menu.status()) + ".hint").withStyle(ChatFormatting.GRAY))));
        elements.add(espeStorage(134, 4));
        elements.add(new TextBarElement(27, 22, 104, this::remainingText, () -> 0xFFFFFF, false));
        elements.add(new TextBarElement(27, 40, 104, MatrixTunerScreen::priceText, () -> 0xD0D0D0, false));
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addRenderableWidget(Button.builder(modeText(), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MatrixTunerMenu.BUTTON_TOGGLE);
            }
        }).bounds(leftPos + 27, topPos + 60, 104, 16).tooltip(Tooltip.create(Component.translatable(KEY + "mode.hint"))).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (modeButton != null) {
            modeButton.setMessage(modeText());
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component modeText() {
        return Component.translatable(KEY + (menu.isPublic() ? "mode.public" : "mode.private"));
    }

    private Component remainingText() {
        int remaining = menu.remaining();
        return remaining < 0 ? Component.translatable(KEY + "nobody") : Component.translatable(KEY + "remaining", remaining);
    }

    private static Component priceText() {
        String espe = BigDecimal.valueOf(ECConfig.get(MatrixTunerBlockEntity.Config.espePerDamage)).stripTrailingZeros().toPlainString();
        return Component.translatable(KEY + "price", espe, ECConfig.get(MatrixTunerBlockEntity.Config.mruPerDamage));
    }

    static Component statusText(Status status) {
        return Component.translatable(KEY + "status." + key(status));
    }

    private static String key(Status status) {
        return switch (status) {
            case IDLE -> "idle";
            case WORKING -> "working";
            case CLEAN -> "clean";
            case NO_ESPE -> "no_espe";
            case NO_MRU -> "no_mru";
            case NOT_ALLOWED -> "not_allowed";
            case DISABLED -> "disabled";
        };
    }
}
