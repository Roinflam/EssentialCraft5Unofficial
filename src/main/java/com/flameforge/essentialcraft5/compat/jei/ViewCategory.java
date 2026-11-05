package com.flameforge.essentialcraft5.compat.jei;

import com.flameforge.essentialcraft5.client.guide.GuideRecipeViews;
import com.flameforge.essentialcraft5.client.guide.RecipeView;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 研究手册登记过画法的配方类型（米斯莱熔炉、矿石加工、注风）：直接拿手册的 {@link RecipeView}，
 * 原料（带数量）→ 箭头 → 产物，说明文字折行写在下面。手册和 JEI 用同一份画法，说明（ESPE、产量、风之眷顾）不会两边对不上。
 * <p>
 * 只按自动排版摆（原料每行 3 格靠右贴着箭头，最多两行；产物每列 2 个）；写了坐标的手动排版也按自动摆，目前登记的三种都是自动排版。
 * 说明超过 {@link #NOTE_LINES} 行时最后一行换成「……」，鼠标停在说明区看全文。
 */
final class ViewCategory<T extends Recipe<?>> implements IRecipeCategory<T> {

    private static final int W = 150;
    /** 登记的三种都是一进一出，两行六格足够；以后有更多原料的类型再加高 */
    private static final int COLS = 3;
    private static final int ROWS = 2;
    private static final int CELL = 18;
    private static final int ARROW_X = (W - JeiDraw.ARROW_W) / 2;
    private static final int GRID_H = CELL * ROWS;
    private static final int NOTE_LINES = 3;
    private static final int NOTE_Y = GRID_H + 3;
    private static final int H = NOTE_Y + NOTE_LINES * JeiDraw.LINE_H;

    private final RecipeType<T> type;
    private final Component title;
    private final JeiDraw draw;
    private final IDrawable background;
    private final IDrawable icon;

    ViewCategory(IGuiHelper gui, RecipeType<T> type, ItemStack station, Component title) {
        this.type = type;
        this.title = title;
        draw = new JeiDraw(gui);
        background = gui.createBlankDrawable(W, H);
        icon = gui.createDrawableItemStack(station);
    }

    @Override
    public RecipeType<T> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return title;
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
    public boolean isHandled(T recipe) {
        // 手册那边画法出错（登记的函数抛异常）的配方，JEI 里也不列，免得出一页空的
        return GuideRecipeViews.view(recipe) != null;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
        RecipeView view = GuideRecipeViews.view(recipe);
        if (view == null) {
            return;
        }
        List<RecipeView.Input> inputs = view.inputs();
        int inRows = Math.max(1, Math.min(ROWS, (inputs.size() + COLS - 1) / COLS));
        int inTop = (GRID_H - inRows * CELL) / 2;
        for (int i = 0; i < inputs.size() && i < COLS * ROWS; i++) {
            int row = i / COLS;
            int inRow = Math.min(COLS, inputs.size() - row * COLS);
            int col = i % COLS;
            // 每行靠右贴着箭头
            int x = ARROW_X - 4 - (inRow - col) * CELL + 1;
            RecipeView.Input input = inputs.get(i);
            draw.slot(builder, RecipeIngredientRole.INPUT, x, inTop + row * CELL + 1)
                    .addItemStacks(JeiDraw.stacks(input.ingredient(), input.count()));
        }
        List<ItemStack> outputs = new ArrayList<>();
        for (RecipeView.Output output : view.outputs()) {
            outputs.add(output.stack());
        }
        if (outputs.isEmpty()) {
            outputs.add(recipe.getResultItem(GuideRecipeViews.registryAccess()));
        }
        int outCols = Math.max(1, (Math.min(outputs.size(), COLS * ROWS) + ROWS - 1) / ROWS);
        int outRows = Math.min(ROWS, outputs.size());
        int outTop = (GRID_H - outRows * CELL) / 2;
        for (int i = 0; i < outputs.size() && i < outCols * ROWS; i++) {
            int x = ARROW_X + JeiDraw.ARROW_W + 4 + (i / ROWS) * CELL + 1;
            draw.slot(builder, RecipeIngredientRole.OUTPUT, x, outTop + (i % ROWS) * CELL + 1).addItemStack(outputs.get(i));
        }
    }

    @Override
    public void draw(T recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        draw.arrow(g, ARROW_X, (GRID_H - JeiDraw.ARROW_H) / 2);
        List<FormattedCharSequence> lines = noteLines(recipe);
        if (lines == null) {
            return;
        }
        Font font = JeiDraw.font();
        int shown = Math.min(lines.size(), NOTE_LINES);
        for (int i = 0; i < shown; i++) {
            FormattedCharSequence line = i == NOTE_LINES - 1 && lines.size() > NOTE_LINES
                    ? Component.literal("……").getVisualOrderText() : lines.get(i);
            g.drawString(font, line, (W - font.width(line)) / 2, NOTE_Y + i * JeiDraw.LINE_H, JeiDraw.TEXT, false);
        }
    }

    @Override
    public List<Component> getTooltipStrings(T recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY < NOTE_Y - 1 || mouseY >= H) {
            return List.of();
        }
        List<FormattedCharSequence> lines = noteLines(recipe);
        RecipeView view = GuideRecipeViews.view(recipe);
        if (lines == null || view == null || lines.size() <= NOTE_LINES) {
            return List.of();
        }
        return view.notes();
    }

    /** 说明文字折成配方区宽度的行；画法出错时返回 null */
    @Nullable
    private static List<FormattedCharSequence> noteLines(Recipe<?> recipe) {
        RecipeView view = GuideRecipeViews.view(recipe);
        if (view == null) {
            return null;
        }
        Font font = JeiDraw.font();
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component note : view.notes()) {
            lines.addAll(font.split(note, W - 4));
        }
        return lines;
    }
}
