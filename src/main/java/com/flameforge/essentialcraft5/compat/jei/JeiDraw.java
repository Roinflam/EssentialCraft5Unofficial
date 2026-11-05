package com.flameforge.essentialcraft5.compat.jei;

import com.flameforge.essentialcraft5.client.guide.Figures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * 几个分类共用的画法：格子底、箭头（原版熔炉界面里那支，带进度动画）、说明文字。
 * JEI 的配方区是浅灰底，字用深灰（同 JEI 自己的说明文字）。
 */
final class JeiDraw {

    /** 原版熔炉界面：空箭头在 (79, 34)，填满的进度箭头在 (176, 14)，都是 24×17 */
    private static final ResourceLocation FURNACE = new ResourceLocation("minecraft", "textures/gui/container/furnace.png");
    static final int ARROW_W = 24;
    static final int ARROW_H = 17;
    static final int TEXT = 0xFF404040;
    static final int TEXT_DIM = 0xFF707070;
    static final int LINE_H = 10;

    final IDrawableStatic slot;
    final IDrawableStatic arrowEmpty;
    final IDrawableAnimated arrowFull;

    JeiDraw(IGuiHelper gui) {
        slot = gui.getSlotDrawable();
        arrowEmpty = gui.drawableBuilder(FURNACE, 79, 34, ARROW_W, ARROW_H).build();
        arrowFull = gui.createAnimatedDrawable(gui.drawableBuilder(FURNACE, 176, 14, ARROW_W, ARROW_H).build(),
                80, IDrawableAnimated.StartDirection.LEFT, false);
    }

    /** 一个带底的格子：(x, y) 是物品图标的左上角，底画在外面一圈 */
    IRecipeSlotBuilder slot(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y) {
        return builder.addSlot(role, x, y).setBackground(slot, -1, -1);
    }

    void arrow(GuiGraphics g, int x, int y) {
        arrowEmpty.draw(g, x, y);
        arrowFull.draw(g, x, y);
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }

    /** 一行字，水平居中在 [0, width) 里 */
    static void centered(GuiGraphics g, Component text, int width, int y, int color) {
        Font font = font();
        g.drawString(font, text, (width - font.width(text)) / 2, y, color, false);
    }

    /** 「消耗 N MRU · T 秒」：直接用研究手册配方图那一行（无升级时每刻进度 +1，ticks 刻做完） */
    static Component mruLine(int mru, int ticks) {
        return Figures.mruLine(mru, ticks);
    }

    /** 原料的候选物品，每个都显示成 count 个（配方要几个就画几个） */
    static List<ItemStack> stacks(Ingredient ingredient, int count) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            out.add(stack.copyWithCount(Math.max(1, count)));
        }
        return out;
    }
}
