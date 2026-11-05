package com.flameforge.essentialcraft5.compat.jei;

import com.flameforge.essentialcraft5.api.IElementalCrystal.Element;
import com.flameforge.essentialcraft5.blockentity.ElementalDecomposerBlockEntity;
import com.flameforge.essentialcraft5.client.guide.Figures;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECDecomposer;
import com.flameforge.essentialcraft5.registry.ECEssence;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 元素分解器（batch12.md §4.8.3）：四页，每页一种元素。上面列这种元素的分解标签里的物品（8 列 × 3 行，放不下时最后一格轮换剩下的），
 * 下面箭头 → 普通精华、罕见精华，再下面两行写多少件出一颗、每几颗有一颗罕见、每件的间隔和 MRU。
 * 同时在几个标签里的物品只按火 → 水 → 地 → 风的第一个算，所以每页只列真正分到这种元素的。
 */
final class DecomposerCategory implements IRecipeCategory<DecomposerCategory.Page> {

    record Page(Element element, List<ItemStack> items) {
    }

    private static final String KEY = "jei.essentialcraft5.decomposer.";
    private static final int W = 150;
    private static final int CELL = 18;
    private static final int COLS = 8;
    private static final int ROWS = 3;
    private static final int GRID_X = (W - COLS * CELL) / 2 + 1;
    private static final int ROW_Y = ROWS * CELL + 4;
    private static final int ARROW_X = 44;
    private static final int OUT_X = ARROW_X + JeiDraw.ARROW_W + 6;
    private static final int NOTE_Y = ROW_Y + CELL + 4;
    private static final int NOTE_LINES = 3;
    private static final int H = NOTE_Y + NOTE_LINES * JeiDraw.LINE_H;

    private final JeiDraw draw;
    private final IDrawable background;
    private final IDrawable icon;

    DecomposerCategory(IGuiHelper gui) {
        draw = new JeiDraw(gui);
        background = gui.createBlankDrawable(W, H);
        icon = gui.createDrawableItemStack(new ItemStack(ECDecomposer.ELEMENTAL_DECOMPOSER.get()));
    }

    /** 四页，物品取客户端现在的标签（数据包改了标签、重新进服后 JEI 重建时跟着变） */
    static List<Page> pages() {
        List<Page> pages = new ArrayList<>();
        for (Element element : Element.values()) {
            List<ItemStack> items = new ArrayList<>();
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(ECDecomposer.tag(element))) {
                ItemStack stack = new ItemStack(holder.value());
                if (ElementalDecomposerBlockEntity.elementOf(stack) == element) {
                    items.add(stack);
                }
            }
            if (!items.isEmpty()) {
                pages.add(new Page(element, items));
            }
        }
        return pages;
    }

    @Override
    public RecipeType<Page> getRecipeType() {
        return EC5JeiPlugin.DECOMPOSER;
    }

    @Override
    public Component getTitle() {
        return ECDecomposer.ELEMENTAL_DECOMPOSER.get().getName();
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Page page, IFocusGroup focuses) {
        List<ItemStack> items = page.items();
        int cells = COLS * ROWS;
        for (int i = 0; i < items.size() && i < cells; i++) {
            int x = GRID_X + (i % COLS) * CELL;
            int y = 1 + (i / COLS) * CELL;
            List<ItemStack> here = i == cells - 1 && items.size() > cells ? items.subList(i, items.size()) : List.of(items.get(i));
            draw.slot(builder, RecipeIngredientRole.INPUT, x, y).addItemStacks(here);
        }
        int every = ECConfig.get(ElementalDecomposerBlockEntity.Config.uncommonEvery);
        draw.slot(builder, RecipeIngredientRole.OUTPUT, OUT_X, ROW_Y + 1)
                .addItemStack(new ItemStack(ECEssence.essence(0, page.element())));
        if (every > 0) {
            draw.slot(builder, RecipeIngredientRole.OUTPUT, OUT_X + CELL + 4, ROW_Y + 1)
                    .addItemStack(new ItemStack(ECEssence.essence(1, page.element())))
                    .addTooltipCallback((view, tooltip) -> tooltip.add(
                            Component.translatable(KEY + "uncommon", every).withStyle(ChatFormatting.GRAY)));
        }
    }

    @Override
    public void draw(Page page, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        draw.arrow(g, ARROW_X, ROW_Y);
        Font font = JeiDraw.font();
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component note : notes()) {
            lines.addAll(font.split(note, W - 4));
        }
        for (int i = 0; i < lines.size() && i < NOTE_LINES; i++) {
            FormattedCharSequence line = lines.get(i);
            g.drawString(font, line, (W - font.width(line)) / 2, NOTE_Y + i * JeiDraw.LINE_H, JeiDraw.TEXT, false);
        }
    }

    private static List<Component> notes() {
        List<Component> lines = new ArrayList<>();
        int per = ElementalDecomposerBlockEntity.pointsPerEssence();
        int every = ECConfig.get(ElementalDecomposerBlockEntity.Config.uncommonEvery);
        lines.add(every > 0 ? Component.translatable(KEY + "rate", per, every) : Component.translatable(KEY + "rate_common", per));
        lines.add(Component.translatable(KEY + "cost", ECConfig.get(ElementalDecomposerBlockEntity.Config.mruPerItem),
                Figures.number(ECConfig.get(ElementalDecomposerBlockEntity.Config.ticksPerItem) / 20F)));
        return lines;
    }
}
