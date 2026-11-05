package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.block.MagicalChestBlock;
import com.flameforge.essentialcraft5.menu.MagicalChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 魔力箱 / 虚空箱界面（原作 GuiMagicalChest）：按 {@link MagicalChestBlock.Layout} 把界面贴图整张画上去（魔力箱 176 × 222，虚空箱 256 × 256）。
 * 原作不写标题和「物品栏」字样；魔力箱的贴图顶上和玩家物品栏上方都留了原版箱子那样的空行，这里照原版箱子写上。
 * 虚空箱的贴图没留空（{@code Layout.labels} 为假）：照原版的位置写，标题会压住第一行格子（y 8 起），「物品栏」写在 y 162 压住最后一行（152~168），所以不写。
 */
public class MagicalChestScreen extends AbstractContainerScreen<MagicalChestMenu> {

    public MagicalChestScreen(MagicalChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        MagicalChestBlock.Layout layout = menu.layout();
        imageWidth = layout.width();
        imageHeight = layout.height();
        inventoryLabelX = layout.invX();
        // 原版：玩家物品栏第一格上方 12 像素（原版箱子 139 - 11 = 128，这里物品栏低 1 像素，字仍放在 128）
        inventoryLabelY = layout.invY() - 12;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(menu.layout().guiTexture(), leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.layout().labels()) {
            super.renderLabels(graphics, mouseX, mouseY);
        }
    }
}
