package com.flameforge.essentialcraft5.compat.jei;

import com.flameforge.essentialcraft5.client.guide.GuideRecipeViews;
import com.flameforge.essentialcraft5.client.guide.GuideTextFit;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.registry.ECMagicianTable;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * 魔力工作台：中心 + 四角五格（配方里没写的位置必须空着）→ 产物，下面写耗能。
 * 摆法照工作台界面和研究手册的配方图：原料顺序与 {@link MagicianTableRecipe} 一致（中心、左上、右上、左下、右下）。
 * 同调升阶 / 转移（同一个配方类型）照研究手册的画法（{@link GuideRecipeViews#magicianExtras}）：中心格是同调装备、四角带数量、
 * 产物和中心格连着轮换（{@code createFocusLink}），两个下角之间写「n 阶 · 满」（半号字放不进两格物品图标之间时改写在箭头上方，
 * 判法同研究手册 {@link GuideTextFit#fitsBetweenCorners}），箭头下面写矩阵阶要求。
 */
final class MagicianTableCategory implements IRecipeCategory<MagicianTableRecipe> {

    private static final int W = 128;
    private static final int H = 66;
    /** 五格的图标位置：中心、左上、右上、左下、右下 */
    private static final int[][] POS = {{19, 19}, {1, 1}, {37, 1}, {1, 37}, {37, 37}};
    private static final int ARROW_X = 62;
    private static final int ARROW_Y = 19;
    private static final int OUT_X = 96;

    private final JeiDraw draw;
    private final IDrawable background;
    private final IDrawable icon;

    MagicianTableCategory(IGuiHelper gui) {
        draw = new JeiDraw(gui);
        background = gui.createBlankDrawable(W, H);
        icon = gui.createDrawableItemStack(new ItemStack(ECMagicianTable.MAGICIAN_TABLE.get()));
    }

    @Override
    public RecipeType<MagicianTableRecipe> getRecipeType() {
        return EC5JeiPlugin.MAGICIAN_TABLE;
    }

    @Override
    public Component getTitle() {
        return ECMagicianTable.MAGICIAN_TABLE.get().getName();
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
    public void setRecipe(IRecipeLayoutBuilder builder, MagicianTableRecipe recipe, IFocusGroup focuses) {
        GuideRecipeViews.MagicianExtras extras = GuideRecipeViews.magicianExtras(recipe);
        if (extras != null) {
            IRecipeSlotBuilder center = null;
            for (int i = 0; i < MagicianTableRecipe.INPUTS; i++) {
                IRecipeSlotBuilder slot = draw.slot(builder, RecipeIngredientRole.INPUT, POS[i][0], POS[i][1]);
                slot.addItemStacks(List.of(extras.inputs()[i]));
                if (i == 0) {
                    center = slot;
                }
            }
            IRecipeSlotBuilder output = draw.slot(builder, RecipeIngredientRole.OUTPUT, OUT_X, ARROW_Y + 1);
            output.addItemStacks(List.of(extras.results()));
            if (extras.results().length == extras.inputs()[0].length && extras.results().length > 1) {
                builder.createFocusLink(center, output);
            }
            return;
        }
        NonNullList<Ingredient> inputs = recipe.getIngredients();
        for (int i = 0; i < MagicianTableRecipe.INPUTS; i++) {
            Ingredient ingredient = i < inputs.size() ? inputs.get(i) : Ingredient.EMPTY;
            var slot = draw.slot(builder, RecipeIngredientRole.INPUT, POS[i][0], POS[i][1]);
            if (!ingredient.isEmpty()) {
                slot.addIngredients(ingredient);
            }
        }
        draw.slot(builder, RecipeIngredientRole.OUTPUT, OUT_X, ARROW_Y + 1).addItemStack(recipe.getResultItem(GuideRecipeViews.registryAccess()));
    }

    @Override
    public void draw(MagicianTableRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        // 四角到中心的细线，看出是「围着中心」的摆法（同研究手册的配方图）
        int line = 0xFF8B8B8B;
        g.fill(18, 18, 20, 20, line);
        g.fill(36, 18, 38, 20, line);
        g.fill(18, 36, 20, 38, line);
        g.fill(36, 36, 38, 38, line);
        draw.arrow(g, ARROW_X, ARROW_Y);
        JeiDraw.centered(g, JeiDraw.mruLine(recipe.getMRU(), recipe.getMRU()), W, H - JeiDraw.LINE_H, JeiDraw.TEXT);
        GuideRecipeViews.MagicianExtras extras = GuideRecipeViews.magicianExtras(recipe);
        if (extras == null) {
            return;
        }
        if (extras.badge() != null) {
            boolean between = GuideTextFit.fitsBetweenCorners((JeiDraw.font().width(extras.badge()) + 1) / 2);
            g.pose().pushPose();
            // 放得下：两个下角之间；放不下：网格和产物之间、箭头上方的空当
            g.pose().translate(between ? 28 : (POS[2][0] + 18 + OUT_X) / 2F, between ? 40 : ARROW_Y - 9, 0F);
            g.pose().scale(0.5F, 0.5F, 1F);
            g.drawString(JeiDraw.font(), extras.badge(), -JeiDraw.font().width(extras.badge()) / 2, 0, JeiDraw.TEXT, false);
            g.pose().popPose();
        }
        if (extras.note() != null) {
            // 箭头下面、产物左下的空当，写不下时缩小
            Component note = extras.note();
            int room = W - ARROW_X;
            float scale = Math.min(1F, room / (float) Math.max(1, JeiDraw.font().width(note)));
            g.pose().pushPose();
            g.pose().translate(ARROW_X, ARROW_Y + 22, 0F);
            g.pose().scale(scale, scale, 1F);
            g.drawString(JeiDraw.font(), note, 0, 0, JeiDraw.TEXT_DIM, false);
            g.pose().popPose();
        }
    }
}
