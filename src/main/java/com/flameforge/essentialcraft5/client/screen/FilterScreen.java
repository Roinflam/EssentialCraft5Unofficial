package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ItemFilters;
import com.flameforge.essentialcraft5.menu.FilterMenu;
import com.flameforge.essentialcraft5.menu.GhostSlot;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品过滤器界面（原作 {@code GuiFilter}）：背景是原版发射器界面（原作 L50），中间 3×3 就是样本格。
 * 标题是过滤器的名字；原作不画字，这里在「物品栏」字样的位置写一行「样本不会被消耗」（原作格子里放的是真物品，老玩家会以为东西被吞了）。
 * 指着空的样本格、手上没拿东西时提示怎么放样本。
 *
 * <h2>高级过滤器</h2>
 * 左边 (20, 6) / (20, 30) / (20, 54) 三个 20 × 20 的开关按钮（原作 L35~38），原版按钮底上画 guifilterbuttons.png 的图标（v = 0 / 20 / 40）：
 * 左列 u 0 是绿勾「这一项要比」，右列 u 20 是红叉「这一项不管」。前两个开关是「忽略」，开着画红叉；第三个是「按标签」（原作是「忽略矿辞」，语义反过来了），
 * 开着画绿勾，图标的意思始终和开关的效果一致。按钮只发 {@code handleInventoryButtonClick}，开关由服务端改、经菜单同步回来（porting_guide §9）。
 * 标题和「样本不会被消耗」挪到按钮右边（x 44），不压按钮。
 * <p>
 * 「按标签」开着时：选了标签的样本格左上角画一个青色 {@code #}，物品提示写选中的标签和它有几种物品；鼠标指着样本格滚轮换标签
 * （往下滚下一个、往上滚上一个，在这件样本的全部标签和「按物品」之间循环）。开关关着时滚轮照常交给原版（什么都不做）。
 */
public class FilterScreen extends AbstractContainerScreen<FilterMenu> {

    private static final ResourceLocation BUTTONS = EssentialCraft5.id("textures/gui/guifilterbuttons.png");
    private static final int BUTTON_X = 20;
    private static final int BUTTON_Y = 6;
    private static final int BUTTON_STEP = 24;
    private static final int BUTTON_SIZE = 20;
    /** 高级过滤器的标题和底下那行字的 x（按钮右边留 4 像素） */
    private static final int ADVANCED_LABEL_X = 44;
    /** 选了标签的样本格角标颜色（青） */
    private static final int TAG_BADGE_COLOR = 0x55FFFF;

    private static final Component NOTE = Component.translatable("gui.essentialcraft5.filter.note");
    private static final List<Component> GHOST_HINT = List.of(
            Component.translatable("gui.essentialcraft5.filter.ghost_hint.0"),
            Component.translatable("gui.essentialcraft5.filter.ghost_hint.1").withStyle(ChatFormatting.GRAY),
            Component.translatable("gui.essentialcraft5.filter.ghost_hint.2").withStyle(ChatFormatting.GRAY));

    private final List<FlagButton> flagButtons = new ArrayList<>();

    public FilterScreen(FilterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
        if (menu.isAdvanced()) {
            titleLabelX = ADVANCED_LABEL_X;
            inventoryLabelX = ADVANCED_LABEL_X;
        }
    }

    @Override
    protected void init() {
        super.init();
        flagButtons.clear();
        if (menu.isAdvanced()) {
            for (ItemFilters.Flag flag : ItemFilters.Flag.values()) {
                flagButtons.add(addRenderableWidget(new FlagButton(flag,
                        leftPos + BUTTON_X, topPos + BUTTON_Y + flag.ordinal() * BUTTON_STEP)));
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(ECTextures.GENERIC_BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 高级过滤器的标题挪到 x 44 以后，英文名（Item Filter (Whitelist, Advanced)）或改过的长名字会伸出界面右边，超了就截断加省略号
        int maxTitle = imageWidth - titleLabelX - 6;
        if (font.width(title) > maxTitle) {
            String ellipsis = "…";
            graphics.drawString(font, font.plainSubstrByWidth(title.getString(), maxTitle - font.width(ellipsis)) + ellipsis,
                    titleLabelX, titleLabelY, 0x404040, false);
        } else {
            graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        }
        graphics.drawString(font, NOTE, inventoryLabelX, inventoryLabelY, 0x606060, false);
        if (byTag()) {
            // 物品在 z 150、数量字在 z 200，角标再往上放
            graphics.pose().pushPose();
            graphics.pose().translate(0F, 0F, 300F);
            for (int i = 0; i < ItemFilters.SLOTS; i++) {
                Slot slot = menu.slots.get(i);
                if (slot.hasItem() && menu.selectedTag(i) != null) {
                    graphics.drawString(font, "#", slot.x, slot.y, TAG_BADGE_COLOR, true);
                }
            }
            graphics.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        FlagButton hoveredButton = null;
        for (FlagButton button : flagButtons) {
            if (button.isHoveredOrFocused() && button.isMouseOver(mouseX, mouseY)) {
                hoveredButton = button;
            }
        }
        if (hoveredButton != null) {
            graphics.renderComponentTooltip(font, hoveredButton.tooltipLines(), mouseX, mouseY);
        } else if (hoveredSlot instanceof GhostSlot && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.renderComponentTooltip(font, GHOST_HINT, mouseX, mouseY);
        }
    }

    /** 样本格的物品提示：「按标签」开着时在后面加上这一格按什么比、能不能滚轮换 */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        if (!byTag() || !(hoveredSlot instanceof GhostSlot) || ItemFilters.isFilter(stack)) {
            return lines;
        }
        List<Component> out = new ArrayList<>(lines);
        int index = hoveredSlot.getContainerSlot();
        TagKey<Item> tag = menu.selectedTag(index);
        if (tag != null) {
            out.add(Component.translatable("gui.essentialcraft5.filter.sample_tag", tag.location().toString(), ItemFilters.tagSize(tag))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            out.add(Component.translatable("gui.essentialcraft5.filter.sample_item").withStyle(ChatFormatting.GRAY));
        }
        int choices = ItemFilters.tagChoices(stack).size();
        out.add((choices > 0
                ? Component.translatable("gui.essentialcraft5.filter.sample_scroll", choices)
                : Component.translatable("gui.essentialcraft5.filter.sample_no_tags")).withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    /** 「按标签」开着时，鼠标指着有样本的格子滚轮 = 这一格换下一个 / 上一个标签 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0D && byTag() && hoveredSlot instanceof GhostSlot && hoveredSlot.hasItem()
                && minecraft != null && minecraft.gameMode != null) {
            int base = delta < 0D ? FilterMenu.BUTTON_NEXT_TAG : FilterMenu.BUTTON_PREV_TAG;
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, base + hoveredSlot.getContainerSlot());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean byTag() {
        return menu.isAdvanced() && menu.flagOn(ItemFilters.Flag.MATCH_TAGS);
    }

    /** 开关按钮：原版按钮底 + 过滤器按钮贴图上的图标 */
    private final class FlagButton extends AbstractButton {

        private final ItemFilters.Flag flag;

        FlagButton(ItemFilters.Flag flag, int x, int y) {
            super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty());
            this.flag = flag;
        }

        @Override
        public void onPress() {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FilterMenu.BUTTON_FLAG + flag.ordinal());
            }
        }

        /** 这一项现在「要比」：两个「忽略」开关关着、「按标签」开着 */
        private boolean compared() {
            boolean on = menu.flagOn(flag);
            return flag == ItemFilters.Flag.MATCH_TAGS ? on : !on;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            RenderSystem.enableBlend();
            graphics.blit(BUTTONS, getX(), getY(), compared() ? 0 : BUTTON_SIZE, flag.ordinal() * BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE);
            RenderSystem.disableBlend();
        }

        private Component state() {
            String base = switch (flag) {
                case IGNORE_DAMAGE -> "gui.essentialcraft5.filter.damage";
                case IGNORE_NBT -> "gui.essentialcraft5.filter.nbt";
                case MATCH_TAGS -> "gui.essentialcraft5.filter.tag";
            };
            return Component.translatable(base + (compared() ? ".matched" : ".ignored"));
        }

        List<Component> tooltipLines() {
            String desc = "gui.essentialcraft5.filter." + flag.key() + (menu.flagOn(flag) ? ".on" : ".off");
            return List.of(state(),
                    Component.translatable(desc).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.essentialcraft5.filter.toggle").withStyle(ChatFormatting.DARK_GRAY));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, state());
        }
    }
}
