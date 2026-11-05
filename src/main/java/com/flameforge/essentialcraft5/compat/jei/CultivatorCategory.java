package com.flameforge.essentialcraft5.compat.jei;

import com.flameforge.essentialcraft5.blockentity.MagicalCultivatorBlockEntity;
import com.flameforge.essentialcraft5.client.CultivatorClient;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.recipe.CultivatorRecipe;
import com.flameforge.essentialcraft5.registry.ECCultivator;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔力栽培器（batch12.md §4.2.3）：每个显式配方一页，样本（画成株数上限个）→ 箭头 → 每种产物（画成每株的上限个，
 * 悬停写每株几个、几率），下面三行说明和研究手册的配方图同一份（{@link CultivatorClient#notes}）。
 * <p>
 * 另有一页说明通用路线：没有配方、种得在普通耕地上的别的模组作物按它自己成熟时的掉落来种；样本格轮换当前能走这条路的作物
 * （按原版类判断，不认识任何具体模组，纯原版下一个都没有时不画格子）。
 */
final class CultivatorCategory implements IRecipeCategory<CultivatorCategory.Page> {

    /** 一页：显式配方，或通用路线说明页（recipe 为 null） */
    record Page(@Nullable CultivatorRecipe recipe, List<ItemStack> genericInputs) {
    }

    private static final String KEY = "jei.essentialcraft5.cultivator.";
    private static final int W = 150;
    private static final int CELL = 18;
    private static final int ARROW_X = 56;
    private static final int ROW_Y = 1;
    private static final int IN_X = ARROW_X - 4 - CELL + 1;
    private static final int OUT_X = ARROW_X + JeiDraw.ARROW_W + 4 + 1;
    private static final int NOTE_LINES = 4;
    private static final int NOTE_Y = CELL + 6;
    private static final int H = NOTE_Y + NOTE_LINES * JeiDraw.LINE_H;

    private final JeiDraw draw;
    private final IDrawable background;
    private final IDrawable icon;

    CultivatorCategory(IGuiHelper gui) {
        draw = new JeiDraw(gui);
        background = gui.createBlankDrawable(W, H);
        icon = gui.createDrawableItemStack(new ItemStack(ECCultivator.MAGICAL_CULTIVATOR.get()));
    }

    /** 全部页：显式配方按 id 排，最后是通用路线说明页（generic_crops 关着时没有这一页） */
    static List<Page> pages(RecipeManager recipes) {
        List<Page> pages = new ArrayList<>();
        for (CultivatorRecipe recipe : CultivatorRecipe.all(recipes)) {
            pages.add(new Page(recipe, List.of()));
        }
        if (ECConfig.get(MagicalCultivatorBlockEntity.Config.genericCrops)) {
            pages.add(new Page(null, genericInputs(recipes)));
        }
        return pages;
    }

    /** 现在能走通用路线的物品（没有显式配方、作物种得在普通耕地上、不在黑名单里） */
    private static List<ItemStack> genericInputs(RecipeManager recipes) {
        List<ItemStack> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (CultivatorRecipe.genericCropOf(stack) != null && !stack.is(ECCultivator.BLACKLIST)
                    && CultivatorRecipe.find(recipes, stack) == null) {
                out.add(stack);
            }
        }
        return out;
    }

    @Override
    public RecipeType<Page> getRecipeType() {
        return EC5JeiPlugin.CULTIVATOR;
    }

    @Override
    public Component getTitle() {
        return ECCultivator.MAGICAL_CULTIVATOR.get().getName();
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
        CultivatorRecipe recipe = page.recipe();
        if (recipe == null) {
            if (!page.genericInputs().isEmpty()) {
                draw.slot(builder, RecipeIngredientRole.INPUT, IN_X, ROW_Y).addItemStacks(page.genericInputs());
            }
            return;
        }
        draw.slot(builder, RecipeIngredientRole.INPUT, IN_X, ROW_Y)
                .addItemStacks(JeiDraw.stacks(recipe.input(), recipe.maxPlants()));
        List<CultivatorRecipe.Output> outputs = recipe.outputs();
        for (int i = 0; i < outputs.size() && i < 3; i++) {
            CultivatorRecipe.Output output = outputs.get(i);
            draw.slot(builder, RecipeIngredientRole.OUTPUT, OUT_X + i * CELL, ROW_Y)
                    .addItemStack(output.display())
                    .addTooltipCallback((view, tooltip) -> tooltip.add(CultivatorClient.piece(output).copy().withStyle(ChatFormatting.GRAY)));
        }
    }

    @Override
    public void draw(Page page, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        draw.arrow(g, ARROW_X, ROW_Y);
        List<FormattedCharSequence> lines = noteLines(page);
        Font font = JeiDraw.font();
        int shown = Math.min(lines.size(), NOTE_LINES);
        for (int i = 0; i < shown; i++) {
            FormattedCharSequence line = i == NOTE_LINES - 1 && lines.size() > NOTE_LINES
                    ? Component.literal("……").getVisualOrderText() : lines.get(i);
            g.drawString(font, line, (W - font.width(line)) / 2, NOTE_Y + i * JeiDraw.LINE_H, JeiDraw.TEXT, false);
        }
    }

    @Override
    public List<Component> getTooltipStrings(Page page, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY < NOTE_Y - 1 || mouseY >= H || noteLines(page).size() <= NOTE_LINES) {
            return List.of();
        }
        return notes(page);
    }

    private static List<Component> notes(Page page) {
        CultivatorRecipe recipe = page.recipe();
        if (recipe != null) {
            return CultivatorClient.notes(recipe);
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "generic.what"));
        lines.add(Component.translatable(KEY + "generic.yield", ECConfig.get(MagicalCultivatorBlockEntity.Config.genericMaxItemsPerPlant)));
        lines.add(CultivatorClient.round(ECConfig.get(MagicalCultivatorBlockEntity.Config.genericTicks),
                ECConfig.get(MagicalCultivatorBlockEntity.Config.genericMruPerPlant),
                ECConfig.get(MagicalCultivatorBlockEntity.Config.genericMaxPlants)));
        if (page.genericInputs().isEmpty()) {
            lines.add(Component.translatable(KEY + "generic.none"));
        }
        return lines;
    }

    private static List<FormattedCharSequence> noteLines(Page page) {
        Font font = JeiDraw.font();
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component note : notes(page)) {
            lines.addAll(font.split(note, W - 4));
        }
        return lines;
    }
}
