package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.CrafterBlockEntity;
import com.flameforge.essentialcraft5.menu.CrafterMenu;
import com.flameforge.essentialcraft5.recipe.CraftingPattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 魔力合成器界面（原作 GuiCrafter）：背景是原版工作台，框架格 (94, 17) 补一个格子框；没有 MRU 条（合成器不用 MRU）。
 * 装了框架时，空着的合成格里画样本的半透明图（原作 L64~76；原作空框架时这里空指针、一打开界面就崩客户端，这里只读同步来的框架 NBT，空框架什么都不画）。
 * 框架失效时标题位置换成红字提示；指着空的框架格时提示「放合成框架（可选）」。
 */
public class CrafterScreen extends MachineScreen<CrafterMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    /** 原版幽灵配方的遮罩色（GhostRecipe L68） */
    private static final int GHOST_OVERLAY = 0x30FFFFFF;
    private static final int LABEL_COLOR = 0x404040;
    private static final int ERROR_COLOR = 0xC02020;

    /** 框架样本的解析缓存：框架物品对象或它的 NBT 换了才重新读 */
    @Nullable
    private ItemStack cachedFrame;
    @Nullable
    private CompoundTag cachedTag;
    private ItemStack[] cachedSamples = CraftingPattern.emptySamples();

    public CrafterScreen(CrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        titleLabelX = 29;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // 工作台贴图上框架那一格是空白的，补一个格子框（取自原版发射器界面，同 MachineScreen）
        graphics.blit(ECTextures.GENERIC_BACKGROUND, leftPos + CrafterMenu.FRAME_X - 1, topPos + CrafterMenu.FRAME_Y - 1, 7, 83, 18, 18);
        if (menu.frameState() == CraftingPattern.State.NONE) {
            return;
        }
        ItemStack[] samples = samples(menu.frame());
        for (int i = 0; i < CrafterBlockEntity.GRID_SLOTS; i++) {
            Slot slot = menu.slots.get(i);
            if (slot.hasItem() || samples[i].isEmpty()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            graphics.renderFakeItem(samples[i], x, y);
            graphics.fill(RenderType.guiGhostRecipeOverlay(), x, y, x + 16, y + 16, GHOST_OVERLAY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.frameState() == CraftingPattern.State.INVALID) {
            graphics.drawString(font, Component.translatable("gui.essentialcraft5.crafter.frame_invalid"), titleLabelX, titleLabelY, ERROR_COLOR, false);
        } else {
            graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!menu.getCarried().isEmpty() || hoveredSlot == null || hoveredSlot.hasItem()) {
            return;
        }
        int index = menu.slots.indexOf(hoveredSlot);
        if (index == CrafterBlockEntity.SLOT_FRAME) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.essentialcraft5.crafter.frame_slot"),
                    Component.translatable("gui.essentialcraft5.crafter.frame_slot.desc").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (index >= 0 && index < CrafterBlockEntity.GRID_SLOTS && menu.frameState() != CraftingPattern.State.NONE) {
            ItemStack sample = samples(menu.frame())[index];
            if (!sample.isEmpty()) {
                graphics.renderComponentTooltip(font, List.of(
                        Component.translatable("gui.essentialcraft5.crafter.expects", sample.getHoverName()).withStyle(ChatFormatting.GRAY)),
                        mouseX, mouseY);
            }
        }
    }

    private ItemStack[] samples(ItemStack frame) {
        if (frame != cachedFrame || frame.getTag() != cachedTag) {
            cachedFrame = frame;
            cachedTag = frame.getTag();
            cachedSamples = CraftingPattern.samples(frame);
        }
        return cachedSamples;
    }
}
