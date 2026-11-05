package com.flameforge.essentialcraft5.compat.jei;

import com.flameforge.essentialcraft5.client.guide.Figures;
import com.flameforge.essentialcraft5.client.guide.GuideRecipeViews;
import com.flameforge.essentialcraft5.recipe.RadiatingChamberRecipe;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * 辐射仓：上下两格 → 产物，下面两行写耗能和平衡值要求（同研究手册的配方图）。
 * 鼠标停在平衡值那一行上，提示框解释平衡值是什么、各区间叫什么。
 */
final class RadiatingChamberCategory implements IRecipeCategory<RadiatingChamberRecipe> {

    private static final int W = 128;
    private static final int H = 76;
    private static final int IN_X = 28;
    private static final int[] IN_Y = {1, 37};
    private static final int ARROW_X = 54;
    private static final int ARROW_Y = 19;
    private static final int OUT_X = 88;
    private static final int MRU_Y = 57;
    private static final int BALANCE_Y = MRU_Y + JeiDraw.LINE_H;
    private static final List<Component> BALANCE_HELP = List.of(
            Component.translatable("guide.essentialcraft5.recipe.balance.help"),
            Component.translatable("guide.essentialcraft5.recipe.balance.help_ranges"));

    private final JeiDraw draw;
    private final IDrawable background;
    private final IDrawable icon;

    RadiatingChamberCategory(IGuiHelper gui) {
        draw = new JeiDraw(gui);
        background = gui.createBlankDrawable(W, H);
        icon = gui.createDrawableItemStack(new ItemStack(ECRadiatingChamber.RADIATING_CHAMBER.get()));
    }

    @Override
    public RecipeType<RadiatingChamberRecipe> getRecipeType() {
        return EC5JeiPlugin.RADIATING_CHAMBER;
    }

    @Override
    public Component getTitle() {
        return ECRadiatingChamber.RADIATING_CHAMBER.get().getName();
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
    public void setRecipe(IRecipeLayoutBuilder builder, RadiatingChamberRecipe recipe, IFocusGroup focuses) {
        var inputs = recipe.getIngredients();
        for (int i = 0; i < RadiatingChamberRecipe.INPUTS; i++) {
            Ingredient ingredient = i < inputs.size() ? inputs.get(i) : Ingredient.EMPTY;
            var slot = draw.slot(builder, RecipeIngredientRole.INPUT, IN_X, IN_Y[i]);
            if (!ingredient.isEmpty()) {
                slot.addIngredients(ingredient);
            }
        }
        draw.slot(builder, RecipeIngredientRole.OUTPUT, OUT_X, ARROW_Y + 1).addItemStack(recipe.getResultItem(GuideRecipeViews.registryAccess()));
    }

    @Override
    public void draw(RadiatingChamberRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        // 上下两格之间的细竖线：两样东西一起放进去
        g.fill(IN_X + 7, 18, IN_X + 9, 36, 0xFF8B8B8B);
        draw.arrow(g, ARROW_X, ARROW_Y);
        // 原作：每刻进度 +1，每刻耗 基础耗能（1）× 耗能倍率，进度到「所需 MRU」出产物
        int total = Math.round(recipe.getMRU() * recipe.getCostModifier());
        JeiDraw.centered(g, JeiDraw.mruLine(total, recipe.getMRU()), W, MRU_Y, JeiDraw.TEXT);
        JeiDraw.centered(g, balanceLine(recipe), W, BALANCE_Y, JeiDraw.TEXT_DIM);
    }

    @Override
    public List<Component> getTooltipStrings(RadiatingChamberRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        int width = JeiDraw.font().width(balanceLine(recipe));
        int left = (W - width) / 2;
        if (mouseY >= BALANCE_Y - 1 && mouseY < BALANCE_Y + JeiDraw.LINE_H - 1 && mouseX >= left && mouseX < left + width) {
            return BALANCE_HELP;
        }
        return List.of();
    }

    private static Component balanceLine(RadiatingChamberRecipe recipe) {
        return Component.empty()
                .append(Component.translatable("guide.essentialcraft5.recipe.balance"))
                .append(" ")
                .append(Figures.balanceRange(recipe.getBalanceMin(), recipe.getBalanceMax()));
    }
}
