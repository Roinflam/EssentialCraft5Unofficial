package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.menu.CraftingFrameMenu;
import com.flameforge.essentialcraft5.recipe.CraftingPattern;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * 合成框架的样板界面（原作 GuiCraftingFrame）：背景是原版工作台；左上角 (6, 6) 的 20×20 按钮切换模式，
 * 图标取 guifilterbuttons.png 的 (0, 40)「按配方原料」/ (20, 40)「精确」（原作「用 / 不用矿物词典」同一处）。
 * 按钮只发 {@code handleInventoryButtonClick}，模式由服务端改、经菜单的 DataSlot 同步回来（porting_guide §9）。
 * 原作 {@code checkHotbarKeys} 返回 false 挡数字键——这里数字键照常能用（设样本），换走手持那件由 HeldItemMenu 挡。
 */
public class CraftingFrameScreen extends AbstractContainerScreen<CraftingFrameMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final ResourceLocation BUTTONS = EssentialCraft5.id("textures/gui/guifilterbuttons.png");
    private static final int BUTTON_X = 6;
    private static final int BUTTON_Y = 6;
    private static final int BUTTON_SIZE = 20;
    private static final int LABEL_COLOR = 0x404040;

    private ModeButton modeButton;

    public CraftingFrameScreen(CraftingFrameMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
        titleLabelX = 29;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addRenderableWidget(new ModeButton(leftPos + BUTTON_X, topPos + BUTTON_Y));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (modeButton != null && modeButton.isHoveredOrFocused() && modeButton.isMouseOver(mouseX, mouseY)) {
            CraftingPattern.Mode mode = menu.mode();
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("tooltip.essentialcraft5.crafting_frame.mode", Component.translatable(mode.translationKey())),
                    Component.translatable(mode.translationKey() + ".desc").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.essentialcraft5.crafting_frame.mode.toggle").withStyle(ChatFormatting.DARK_GRAY)),
                    mouseX, mouseY);
        } else if (menu.getCarried().isEmpty() && hoveredSlot != null && !hoveredSlot.hasItem()
                && menu.slots.indexOf(hoveredSlot) == CraftingFrameMenu.SLOT_RESULT) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.essentialcraft5.crafting_frame.result_slot"),
                    Component.translatable("gui.essentialcraft5.crafting_frame.result_slot.desc").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        }
    }

    /** 模式按钮：原版按钮底 + 过滤器按钮贴图上的图标 */
    private final class ModeButton extends AbstractButton {

        ModeButton(int x, int y) {
            super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty());
        }

        @Override
        public void onPress() {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CraftingFrameMenu.BUTTON_MODE);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            int u = menu.mode() == CraftingPattern.Mode.EXACT ? 20 : 0;
            RenderSystem.enableBlend();
            graphics.blit(BUTTONS, getX(), getY(), u, 40, BUTTON_SIZE, BUTTON_SIZE);
            RenderSystem.disableBlend();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, Component.translatable(menu.mode().translationKey()));
        }
    }
}
