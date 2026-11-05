package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.recipe.RadiatingChamberRecipe;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.util.NumberText;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.gui.Font;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 手册里的「图」：配方、物品排、摆法、插图。每个图知道自己多大，排版（FigureBlocks）按大小往阅读栏里放，
 * 绘制时给左上角坐标。物品格统一走 {@link GuideFrame#slot}，提示和点击跳转由界面统一处理。
 * <p>
 * 配方图的格子位置照原作界面：魔力工作台是中心加四角（原作 ContainerMagicianTable 的槽位），
 * 辐射仓是上下两格；四种配方图宽度一致（原料区 54、箭头、产物），同一页上下排列时能对齐。
 * 模块登记的配方类型（{@link GuideRecipeViews}）按它给的 {@link RecipeView} 画（{@link Custom}，或一行一条的 {@link Compact}），
 * 自动排版时也用同样的原料区、箭头、产物位置。
 */
public final class Figures {

    /** 图例、说明文字的行高（图的尺寸按它算，和旧界面一致） */
    static final int LINE_H = 10;
    /** 配方图顶上「工作台 / 魔力工作台」那一行的高度 */
    private static final int LABEL_H = 11;
    /** 原料区（3×3 格）边长 */
    private static final int GRID = 54;
    /** 箭头、产物相对图左边的位置 */
    private static final int ARROW_X = 59;
    private static final int OUTPUT_X = 86;
    private static final int RECIPE_W = OUTPUT_X + 18;

    private Figures() {
    }

    /** 页面里的一张图 */
    public abstract static class Figure {

        public abstract int width();

        public abstract int height();

        public abstract void render(GuideFrame f, int x, int y);
    }

    // ================================================================ 配方

    /**
     * 为配方生成对应的图，不认识的类型（或登记的画法出错）返回 null。
     *
     * @param maxWidth 阅读栏宽（说明文字按它折行）
     * @param compact  登记过画法的类型画成一行（{@link Compact}）；内置四种不受影响
     */
    @Nullable
    public static Figure recipe(Recipe<?> recipe, RegistryAccess access, Font font, int maxWidth, boolean compact) {
        RecipeFinder.Kind kind = RecipeFinder.kind(recipe);
        if (kind == null) {
            return null;
        }
        ItemStack result = RecipeFinder.result(recipe, access).copy();
        return switch (kind) {
            case CRAFTING -> new Crafting(recipe, result);
            case MAGICIAN_TABLE -> new Magician((MagicianTableRecipe) recipe, result, font);
            case RADIATING_CHAMBER -> new Radiating((RadiatingChamberRecipe) recipe, result, font);
            case COOKING -> new Cooking((AbstractCookingRecipe) recipe, result, font);
            case CUSTOM -> {
                RecipeView view = GuideRecipeViews.view(recipe);
                if (view == null) {
                    yield null;
                }
                yield compact ? new Compact(view, result, font, maxWidth) : new Custom(view, result, font, maxWidth);
            }
        };
    }

    /** 配方图顶上的一行：机器的小图标 + 名字（名字取物品本身的名字，不另写译文） */
    private static void station(GuideFrame f, ItemStack icon, Component name, int x, int y) {
        if (!icon.isEmpty()) {
            f.smallItem(icon, x, y, 0.5F);
        }
        f.g.drawString(f.font, name, x + (icon.isEmpty() ? 0 : 11), y, f.text(GuideTheme.TEXT_DIM), false);
    }

    private static ItemStack stationIcon(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Item item = location != null && ForgeRegistries.ITEMS.containsKey(location) ? ForgeRegistries.ITEMS.getValue(location) : null;
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static Component stationName(ItemStack icon, String fallback) {
        return icon.isEmpty() ? Component.literal(fallback) : icon.getItem().getDescription();
    }

    /** 原料的候选物品；画不出来的物品换成显示用的替身（见 {@link GuideStacks}，要换时返回新数组，不动配方自己的数组） */
    private static ItemStack[] options(Ingredient ingredient) {
        return ingredient.isEmpty() ? new ItemStack[0] : GuideStacks.display(ingredient.getItems());
    }

    /**
     * 箭头的进度：静止地画满（低饱和的结构紫，只有尖端是品红，见 GuideTheme.arrow）。上一版每 2 秒走一遍，配方图在阅读栏正中间，
     * 读旁边的字时余光里一直有东西在流 —— 阅读栏里不放常驻动画（见 GuideTheme.time）
     */
    private static float arrowProgress(GuideFrame f) {
        return 1F;
    }

    /**
     * 「消耗 N MRU · T 秒」；按机器基础速度 1 进度 / 刻算时间，不足 1 秒不写时间。
     * MRU 先格式化成带千位分隔的文本再传参（直接传 int 时 %s 出来没有分组）。JEI 接入也用它
     */
    public static Component mruLine(int mru, int ticks) {
        String amount = NumberText.group(Integer.toString(mru), GuideValues.groupSeparator());
        if (ticks < 20) {
            return Component.translatable("guide.essentialcraft5.recipe.mru", amount);
        }
        return Component.translatable("guide.essentialcraft5.recipe.mru_time", amount, number(ticks / 20F));
    }

    /**
     * 数字去掉多余的小数：1.0 → 1，0.50 → 0.5；整数部分 ≥ 1000 时按界面语言插千位分隔符（英文 12,000，中文 12000）。
     * 模块写 RecipeView 的说明文字也可以用
     */
    public static String number(float value) {
        String s = String.format(Locale.ROOT, "%.2f", value);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return NumberText.group(s, GuideValues.groupSeparator());
    }

    /** 整数按界面语言插千位分隔符（不经过 float，上千万也不丢精度） */
    public static String whole(long value) {
        return NumberText.group(Long.toString(value), GuideValues.groupSeparator());
    }

    /** 辐射仓配方要求的平衡值区间（「不限」「≤ 0.5」……），JEI 接入和配方图共用 */
    public static Component balanceRange(float min, float max) {
        return Radiating.rangeText(min, max);
    }

    /** 工作台（有序 / 无序）：3×3 原料格 → 产物 */
    static final class Crafting extends Figure {

        private final ItemStack[][] cells = new ItemStack[9][];
        private final ItemStack result;
        private final boolean shapeless;
        private final ItemStack icon = new ItemStack(Items.CRAFTING_TABLE);
        /** 渲染路径上不新建 Component：机器名、「无序」在构造时取好 */
        private final Component name = icon.getItem().getDescription();
        private final Component shapelessText = Component.translatable("guide.essentialcraft5.recipe.shapeless");

        Crafting(Recipe<?> recipe, ItemStack result) {
            this.result = result;
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            if (recipe instanceof IShapedRecipe<?> shaped) {
                int width = Math.max(1, shaped.getRecipeWidth());
                for (int i = 0; i < ingredients.size(); i++) {
                    int cx = i % width;
                    int cy = i / width;
                    if (cx < 3 && cy < 3) {
                        cells[cy * 3 + cx] = options(ingredients.get(i));
                    }
                }
                shapeless = false;
            } else {
                for (int i = 0; i < Math.min(9, ingredients.size()); i++) {
                    cells[i] = options(ingredients.get(i));
                }
                shapeless = true;
            }
        }

        @Override
        public int width() {
            return RECIPE_W;
        }

        @Override
        public int height() {
            return LABEL_H + GRID;
        }

        @Override
        public void render(GuideFrame f, int x, int y) {
            station(f, icon, name, x, y);
            int top = y + LABEL_H;
            for (int i = 0; i < 9; i++) {
                f.slot(cells[i] == null ? GuideFrame.NONE : cells[i], x + (i % 3) * 18, top + (i / 3) * 18);
            }
            GuideTheme.arrow(f.g, x + ARROW_X, top + 19, arrowProgress(f), f.alpha);
            f.slot(result, x + OUTPUT_X, top + 18);
            if (shapeless) {
                f.g.drawString(f.font, shapelessText, x + ARROW_X, top + 38, f.text(GuideTheme.TEXT_DIM), false);
            }
        }
    }

    /**
     * 魔力工作台：中心 + 四角五格（空位必须空着）→ 产物，下面写耗能。
     * 同调升阶 / 转移（{@link GuideRecipeViews#magicianExtras}）：中心格画同调装备、四角带数量、产物随中心那件轮换，
     * 两个下角之间写「n 阶 · 满」（半号字放不进两格物品图标之间时挪到网格下面单独一行，{@link GuideTextFit#fitsBetweenCorners}），
     * 耗能下面多一行矩阵阶要求。
     */
    static final class Magician extends Figure {

        /** 原料顺序与 MagicianTableRecipe 一致：中心、左上、右上、左下、右下 */
        private static final int[][] POS = {{18, 18}, {0, 0}, {36, 0}, {0, 36}, {36, 36}};

        private final ItemStack[][] inputs = new ItemStack[MagicianTableRecipe.INPUTS][];
        private final ItemStack[] results;
        private final ItemStack icon = stationIcon(EssentialCraft5.MODID + ":magician_table");
        private final Component name = stationName(icon, "magician_table");
        private final Component info;
        @Nullable
        private final Component badge;
        @Nullable
        private final Component note;
        /** 徽标在两个下角之间放不下，画在网格下面单独一行 */
        private final boolean badgeBelow;
        private final int width;

        Magician(MagicianTableRecipe recipe, ItemStack result, Font font) {
            GuideRecipeViews.MagicianExtras extras = GuideRecipeViews.magicianExtras(recipe);
            if (extras != null) {
                for (int i = 0; i < inputs.length; i++) {
                    inputs[i] = GuideStacks.display(extras.inputs()[i]);
                }
                results = extras.results().length == 0 ? new ItemStack[]{result} : extras.results();
                badge = extras.badge();
                note = extras.note();
            } else {
                NonNullList<Ingredient> ingredients = recipe.getIngredients();
                for (int i = 0; i < inputs.length; i++) {
                    inputs[i] = i < ingredients.size() ? options(ingredients.get(i)) : new ItemStack[0];
                }
                results = new ItemStack[]{result};
                badge = null;
                note = null;
            }
            // 原作：无升级时每刻进度 +1、耗 1 MRU，进度到「所需 MRU」出产物
            info = mruLine(recipe.getMRU(), recipe.getMRU());
            badgeBelow = badge != null && !GuideTextFit.fitsBetweenCorners((font.width(badge) + 1) / 2);
            width = Math.max(Math.max(RECIPE_W, badgeBelow ? font.width(badge) : 0),
                    Math.max(font.width(info), note == null ? 0 : font.width(note)));
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return LABEL_H + GRID + 12 + (note == null ? 0 : LINE_H) + (badgeBelow ? LINE_H : 0);
        }

        @Override
        public void render(GuideFrame f, int x, int y) {
            station(f, icon, name, x, y);
            int left = x + (width - RECIPE_W) / 2;
            int top = y + LABEL_H;
            // 四角到中心的细线，看出是「围着中心」的摆法
            int line = f.fill(GuideTheme.BORDER);
            f.g.fill(left + 17, top + 17, left + 19, top + 19, line);
            f.g.fill(left + 35, top + 17, left + 37, top + 19, line);
            f.g.fill(left + 17, top + 35, left + 19, top + 37, line);
            f.g.fill(left + 35, top + 35, left + 37, top + 37, line);
            for (int i = 0; i < inputs.length; i++) {
                f.slot(inputs[i], left + POS[i][0], top + POS[i][1]);
            }
            if (badge != null && !badgeBelow) {
                // 两个下角之间的空当（两格物品图标之间 20 像素），小一号字
                f.g.pose().pushPose();
                f.g.pose().translate(left + 27, top + 40, 0F);
                f.g.pose().scale(0.5F, 0.5F, 1F);
                f.g.drawString(f.font, badge, -f.font.width(badge) / 2, 0, f.text(GuideTheme.GLOW), false);
                f.g.pose().popPose();
            }
            GuideTheme.arrow(f.g, left + ARROW_X, top + 19, arrowProgress(f), f.alpha);
            // 产物和中心格用同一个轮换下标（两边候选一样多），中心换一件、产物跟着换
            f.slot(results.length == inputs[0].length ? new ItemStack[]{f.cycle(results)} : results, left + OUTPUT_X, top + 18);
            int lineY = top + GRID + 3;
            if (badge != null && badgeBelow) {
                f.g.drawString(f.font, badge, x + (width - f.font.width(badge)) / 2, lineY, f.text(GuideTheme.GLOW), false);
                lineY += LINE_H;
            }
            f.g.drawString(f.font, info, x + (width - f.font.width(info)) / 2, lineY, f.text(GuideTheme.GLOW), false);
            if (note != null) {
                f.g.drawString(f.font, note, x + (width - f.font.width(note)) / 2, lineY + LINE_H, f.text(GuideTheme.TEXT_DIM), false);
            }
        }
    }

    /** 辐射仓：上下两格 → 产物，下面写耗能和平衡值要求 */
    static final class Radiating extends Figure {

        private static final int BAR_W = 48;
        private static final List<Component> BALANCE_HELP = List.of(
                Component.translatable("guide.essentialcraft5.recipe.balance.help"),
                Component.translatable("guide.essentialcraft5.recipe.balance.help_ranges"));

        private final ItemStack[][] inputs = new ItemStack[RadiatingChamberRecipe.INPUTS][];
        private final ItemStack result;
        private final ItemStack icon = stationIcon(EssentialCraft5.MODID + ":radiating_chamber");
        private final Component name = stationName(icon, "radiating_chamber");
        private final Component info;
        private final Component balanceLabel;
        private final Component range;
        private final float min;
        private final float max;
        private final int width;
        private final Font font;

        Radiating(RadiatingChamberRecipe recipe, ItemStack result, Font font) {
            this.result = result;
            this.font = font;
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            for (int i = 0; i < inputs.length; i++) {
                inputs[i] = i < ingredients.size() ? options(ingredients.get(i)) : new ItemStack[0];
            }
            // 原作：每刻进度 +1，每刻耗 基础耗能（1）× 耗能倍率，进度到「所需 MRU」出产物
            int total = Math.round(recipe.getMRU() * recipe.getCostModifier());
            info = mruLine(total, recipe.getMRU());
            min = recipe.getBalanceMin();
            max = recipe.getBalanceMax();
            balanceLabel = Component.translatable("guide.essentialcraft5.recipe.balance");
            range = rangeText(min, max);
            int balanceRow = font.width(balanceLabel) + 4 + BAR_W + 4 + font.width(range);
            width = Math.max(RECIPE_W, Math.max(font.width(info), balanceRow));
        }

        /** 平衡值区间的文字：不限 / ≤ 0.5（冰冻）/ ≥ 1.5（混沌）/ 0.5 ~ 1.5 */
        static Component rangeText(float min, float max) {
            boolean noMin = min <= MRUBalance.MIN;
            boolean noMax = max >= MRUBalance.MAX;
            if (noMin && noMax) {
                return Component.translatable("guide.essentialcraft5.recipe.balance.any");
            }
            if (noMin) {
                return Component.translatable("guide.essentialcraft5.recipe.balance.at_most", number(max));
            }
            if (noMax) {
                return Component.translatable("guide.essentialcraft5.recipe.balance.at_least", number(min));
            }
            return Component.translatable("guide.essentialcraft5.recipe.balance.between", number(min), number(max));
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return LABEL_H + GRID + 25;
        }

        @Override
        public void render(GuideFrame f, int x, int y) {
            station(f, icon, name, x, y);
            int left = x + (width - RECIPE_W) / 2;
            int top = y + LABEL_H;
            f.slot(inputs[0], left + 18, top);
            f.slot(inputs[1], left + 18, top + 36);
            // 两格之间一个六边形，表示「在辐射」；颜色取配方平衡值区间中点的 MRU 颜色。静止的（上一版 1.8 秒呼吸一次）
            float mid = (Mth.clamp(min, MRUBalance.MIN, MRUBalance.MAX) + Mth.clamp(max, MRUBalance.MIN, MRUBalance.MAX)) / 2F;
            int glow = GuideTheme.fade(GuideTheme.balanceColor(mid), f.alpha * 0.7F);
            BufferBuilder buffer = Shapes.begin(f.g);
            Matrix4f matrix = f.g.pose().last().pose();
            Shapes.outline(buffer, matrix, Shapes.hexagon(left + 27F, top + 27F, 6F), 1.2F, glow);
            Shapes.polygon(buffer, matrix, Shapes.hexagon(left + 27F, top + 27F, 2.5F), glow);
            Shapes.end();
            GuideTheme.arrow(f.g, left + ARROW_X, top + 19, arrowProgress(f), f.alpha);
            f.slot(result, left + OUTPUT_X, top + 18);

            int infoY = top + GRID + 3;
            f.g.drawString(font, info, x + (width - font.width(info)) / 2, infoY, f.text(GuideTheme.GLOW), false);
            int rowY = infoY + 12;
            int rowW = font.width(balanceLabel) + 4 + BAR_W + 4 + font.width(range);
            int rx = x + (width - rowW) / 2;
            f.g.drawString(font, balanceLabel, rx, rowY, f.text(GuideTheme.TEXT_DIM), false);
            rx += font.width(balanceLabel) + 4;
            GuideTheme.balanceBar(f.g, rx, rowY + 2, BAR_W, 4, min, max, f.alpha);
            // 色条的悬停说明：三态各自的范围
            if (f.hovered(rx, rowY, BAR_W, 9)) {
                f.hotspot(rx, rowY, BAR_W, 9, ItemStack.EMPTY, BALANCE_HELP);
            }
            rx += BAR_W + 4;
            f.g.drawString(font, range, rx, rowY, f.text(GuideTheme.TEXT), false);
        }
    }

    /** 熔炉：原料 → 产物 */
    static final class Cooking extends Figure {

        private final ItemStack[] input;
        private final ItemStack result;
        private final ItemStack icon = new ItemStack(Items.FURNACE);
        private final Component name = icon.getItem().getDescription();
        private final Component info;
        private final int width;

        Cooking(AbstractCookingRecipe recipe, ItemStack result, Font font) {
            this.result = result;
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            input = ingredients.isEmpty() ? new ItemStack[0] : options(ingredients.get(0));
            info = Component.translatable("guide.essentialcraft5.recipe.cooking_time", number(recipe.getCookingTime() / 20F));
            width = Math.max(RECIPE_W, font.width(info));
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return LABEL_H + 18 + 12;
        }

        @Override
        public void render(GuideFrame f, int x, int y) {
            station(f, icon, name, x, y);
            int left = x + (width - RECIPE_W) / 2;
            int top = y + LABEL_H;
            f.slot(input, left + 36, top);
            GuideTheme.arrow(f.g, left + ARROW_X, top + 1, arrowProgress(f), f.alpha);
            f.slot(result, left + OUTPUT_X, top);
            f.g.drawString(f.font, info, x + (width - f.font.width(info)) / 2, top + 21, f.text(GuideTheme.TEXT_DIM), false);
        }
    }

    /** 原料格的候选物品，每个都按配方要的数量显示 */
    private static ItemStack[] options(RecipeView.Input input) {
        ItemStack[] options = options(input.ingredient());
        ItemStack[] out = new ItemStack[options.length];
        for (int i = 0; i < options.length; i++) {
            out[i] = options[i].copyWithCount(input.count());
        }
        return out;
    }

    /** 产物：RecipeView 给了就用它的，没给用配方自己的 getResultItem（自动排版时） */
    private static List<ItemStack> outputs(RecipeView view, ItemStack fallback) {
        List<ItemStack> out = new ArrayList<>();
        for (RecipeView.Output output : view.outputs()) {
            out.add(output.stack());
        }
        if (out.isEmpty() && !view.manual() && !fallback.isEmpty()) {
            out.add(fallback);
        }
        return out;
    }

    /**
     * 模块登记的配方类型（{@link GuideRecipeViews}）：照 {@link RecipeView} 画。
     * 自动排版时原料区、箭头、产物的位置和内置的四种图一致（原料每行 3 格靠右贴着箭头，产物每列 3 个），说明文字薰衣草色居中写在下面。
     */
    static final class Custom extends Figure {

        private final RecipeView view;
        private final ItemStack[][] inputs;
        private final int[][] inputPos;
        private final List<ItemStack> outputs;
        private final int[][] outputPos;
        private final int areaW;
        private final int areaH;
        private final int arrowX;
        private final int arrowY;
        private final boolean hasStation;
        private final Component stationName;
        private final List<FormattedCharSequence> notes = new ArrayList<>();
        private final int width;

        Custom(RecipeView view, ItemStack fallback, Font font, int maxWidth) {
            this.view = view;
            List<RecipeView.Input> in = view.inputs();
            this.inputs = new ItemStack[in.size()][];
            this.inputPos = new int[in.size()][];
            this.outputs = outputs(view, fallback);
            this.outputPos = new int[outputs.size()][];
            for (int i = 0; i < in.size(); i++) {
                inputs[i] = options(in.get(i));
            }
            if (view.manual()) {
                int w = 0;
                int h = 0;
                for (int i = 0; i < in.size(); i++) {
                    inputPos[i] = new int[] {in.get(i).x(), in.get(i).y()};
                    w = Math.max(w, in.get(i).x() + 18);
                    h = Math.max(h, in.get(i).y() + 18);
                }
                for (int i = 0; i < outputs.size(); i++) {
                    RecipeView.Output output = view.outputs().get(i);
                    outputPos[i] = new int[] {output.x(), output.y()};
                    w = Math.max(w, output.x() + 18);
                    h = Math.max(h, output.y() + 18);
                }
                boolean arrow = view.arrowX() != RecipeView.AUTO && view.arrowY() != RecipeView.AUTO;
                arrowX = arrow ? view.arrowX() : RecipeView.AUTO;
                arrowY = arrow ? view.arrowY() : RecipeView.AUTO;
                if (arrow) {
                    w = Math.max(w, arrowX + 22);
                    h = Math.max(h, arrowY + 15);
                }
                RecipeView.Background background = view.background();
                areaW = background != null ? background.width() : w;
                areaH = background != null ? background.height() : h;
            } else {
                // 原料：每行最多 3 格，靠右贴着箭头（和工作台的 3×3 原料区右对齐）
                int rowsIn = (in.size() + 2) / 3;
                int rowsOut = Math.min(3, outputs.size());
                int columnsOut = (outputs.size() + 2) / 3;
                areaH = Math.max(1, Math.max(rowsIn, rowsOut)) * 18;
                int inTop = (areaH - rowsIn * 18) / 2;
                for (int i = 0; i < in.size(); i++) {
                    int row = i / 3;
                    int inRow = Math.min(3, in.size() - row * 3);
                    inputPos[i] = new int[] {GRID - inRow * 18 + (i % 3) * 18, inTop + row * 18};
                }
                int outTop = (areaH - rowsOut * 18) / 2;
                for (int i = 0; i < outputs.size(); i++) {
                    outputPos[i] = new int[] {OUTPUT_X + (i / 3) * 20, outTop + (i % 3) * 18};
                }
                arrowX = ARROW_X;
                arrowY = areaH / 2 - 8;
                areaW = Math.max(RECIPE_W, OUTPUT_X + columnsOut * 20 - 2);
            }
            this.hasStation = !view.station().isEmpty() || view.stationName() != null;
            this.stationName = view.stationName() != null ? view.stationName()
                    : view.station().isEmpty() ? Component.empty() : view.station().getItem().getDescription();
            int notesW = 0;
            for (Component note : view.notes()) {
                for (FormattedCharSequence line : font.split(note, Math.max(40, maxWidth))) {
                    notes.add(line);
                    notesW = Math.max(notesW, font.width(line));
                }
            }
            this.width = Math.max(areaW, notesW);
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return (hasStation ? LABEL_H : 0) + areaH + (notes.isEmpty() ? 0 : 3 + notes.size() * 10);
        }

        @Override
        public void render(GuideFrame f, int x, int y) {
            if (hasStation) {
                station(f, view.station(), stationName, x, y);
            }
            int left = x + (width - areaW) / 2;
            int top = y + (hasStation ? LABEL_H : 0);
            RecipeView.Background background = view.background();
            if (background != null) {
                RenderSystem.enableBlend();
                f.g.setColor(1F, 1F, 1F, f.alpha);
                f.g.blit(background.texture(), left, top, background.width(), background.height(), background.u(), background.v(),
                        background.width(), background.height(), background.textureWidth(), background.textureHeight());
                f.g.setColor(1F, 1F, 1F, 1F);
                RenderSystem.disableBlend();
            }
            for (int i = 0; i < inputs.length; i++) {
                f.slot(inputs[i], left + inputPos[i][0], top + inputPos[i][1]);
            }
            if (arrowX != RecipeView.AUTO) {
                GuideTheme.arrow(f.g, left + arrowX, top + arrowY, arrowProgress(f), f.alpha);
            }
            for (int i = 0; i < outputs.size(); i++) {
                f.slot(outputs.get(i), left + outputPos[i][0], top + outputPos[i][1]);
            }
            int ny = top + areaH + 3;
            for (FormattedCharSequence line : notes) {
                f.g.drawString(f.font, line, x + (width - f.font.width(line)) / 2, ny, f.text(GuideTheme.GLOW), false);
                ny += 10;
            }
        }
    }

    /**
     * 一行一条的配方（配方页写了 compact，给几十条的转化表用）：原料 → 产物，右边写第一行说明；
     * 一行放不下时说明挪到下一行。原料太多时只画前几个，最后一格写「…」。不画机器那一行（整页都是同一台机器）。
     */
    static final class Compact extends Figure {

        private static final int ARROW_W = 22;

        private final ItemStack[][] inputs;
        private final List<ItemStack> outputs;
        private final boolean truncated;
        @Nullable
        private final Component note;
        private final boolean noteBelow;
        private final int width;

        Compact(RecipeView view, ItemStack fallback, Font font, int maxWidth) {
            this.outputs = outputs(view, fallback);
            int outW = outputs.size() * 18;
            // 箭头两边各留 2；原料最多画到还放得下产物的数量
            int room = Math.max(18, maxWidth - outW - ARROW_W - 4);
            int fit = Math.max(1, room / 18);
            List<RecipeView.Input> in = view.inputs();
            this.truncated = in.size() > fit;
            int shown = truncated ? fit - 1 : in.size();
            this.inputs = new ItemStack[Math.max(0, shown)][];
            for (int i = 0; i < inputs.length; i++) {
                inputs[i] = options(in.get(i));
            }
            this.note = view.notes().isEmpty() ? null : view.notes().get(0);
            int rowW = rowWidth();
            this.noteBelow = note != null && rowW + 6 + font.width(note) > maxWidth;
            this.width = maxWidth;
        }

        private int rowWidth() {
            return (inputs.length + (truncated ? 1 : 0)) * 18 + 2 + ARROW_W + 2 + outputs.size() * 18;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return 18 + (noteBelow ? 10 : 0);
        }

        @Override
        public void render(GuideFrame f, int x, int y) {
            int cx = x;
            for (ItemStack[] input : inputs) {
                f.slot(input, cx, y);
                cx += 18;
            }
            if (truncated) {
                f.g.drawString(f.font, "…", cx + 6, y + 5, f.text(GuideTheme.TEXT_DIM), false);
                cx += 18;
            }
            GuideTheme.arrow(f.g, cx + 2, y + 1, arrowProgress(f), f.alpha);
            cx += 2 + ARROW_W + 2;
            for (ItemStack output : outputs) {
                f.slot(output, cx, y);
                cx += 18;
            }
            if (note != null) {
                if (noteBelow) {
                    f.g.drawString(f.font, note, x + 2, y + 19, f.text(GuideTheme.GLOW), false);
                } else {
                    f.g.drawString(f.font, note, cx + 6, y + 5, f.text(GuideTheme.GLOW), false);
                }
            }
        }
    }

    // ================================================================ 其它图

    /** 一行说明文字（「没有找到配方」之类），居中、暗色 */
    public static final class Note extends Figure {

        private final Component text;
        private final int width;

        public Note(Component text, Font font) {
            this.text = text;
            this.width = font.width(text);
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return 10;
        }

        @Override
        public void render(GuideFrame f, int x, int y) {
            f.g.drawString(f.font, text, x, y + 1, f.text(GuideTheme.TEXT_DIM), false);
        }
    }

    /** 物品排：一行放不下就折行，整体居中 */
    public static final class ItemRow extends Figure {

        private final List<ItemStack> stacks;
        private final int perRow;

        /** @param perRow 每行最多几个，0 为按宽度放满 */
        public ItemRow(List<ItemStack> stacks, int maxWidth, int perRow) {
            this.stacks = stacks;
            int fit = Math.max(1, (maxWidth + 2) / 20);
            this.perRow = Math.max(1, Math.min(stacks.size(), perRow > 0 ? Math.min(perRow, fit) : fit));
        }

        private int rows() {
            return (stacks.size() + perRow - 1) / perRow;
        }

        @Override
        public int width() {
            return perRow * 20 - 2;
        }

        @Override
        public int height() {
            return Math.max(18, rows() * 20 - 2);
        }

        @Override
        public void render(GuideFrame f, int x, int y) {
            for (int i = 0; i < stacks.size(); i++) {
                int row = i / perRow;
                int inRow = Math.min(perRow, stacks.size() - row * perRow);
                // 末行不满时居中
                int rowLeft = x + (perRow - inRow) * 10;
                f.slot(stacks.get(i), rowLeft + (i % perRow) * 20, y + row * 20);
            }
        }
    }

    /** 插图：等比缩放到给定的宽高以内，紫罗兰细框 + 薰衣草角 */
    public static final class Picture extends Figure {

        private final ResourceLocation texture;
        private final int textureWidth;
        private final int textureHeight;
        private final int width;
        private final int height;

        public Picture(FigureSpec.Image page, int maxWidth, int maxHeight) {
            this.texture = page.texture();
            this.textureWidth = page.width();
            this.textureHeight = page.height();
            float scale = Math.min(1F, Math.min(maxWidth / (float) textureWidth, maxHeight / (float) textureHeight));
            this.width = Math.max(1, Math.round(textureWidth * scale));
            this.height = Math.max(1, Math.round(textureHeight * scale));
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void render(GuideFrame f, int x, int y) {
            RenderSystem.enableBlend();
            f.g.setColor(1F, 1F, 1F, f.alpha);
            f.g.blit(texture, x, y, width, height, 0F, 0F, textureWidth, textureHeight, textureWidth, textureHeight);
            f.g.setColor(1F, 1F, 1F, 1F);
            RenderSystem.disableBlend();
            GuideTheme.border(f.g, x - 1, y - 1, width + 2, height + 2, f.fill(GuideTheme.EDGE));
            GuideTheme.corners(f.g, x - 2, y - 2, width + 4, height + 4, 5, f.fill(GuideTheme.GLOW), 1F);
        }
    }

    // ================================================================ 摆法

    /**
     * 摆法（地面仪式、产能器周围的方块、多方块结构的一层）：俯视网格，上北下南。
     * <ul>
     *   <li>仪式（单层且有 throw）：正中那格四角品红光呼吸，旁边列出「丢出什么 → 得到什么（几率）」；</li>
     *   <li>多层结构：每层一张图，顶上一行写「高度：-1」（有机器位置时，照原作研究书按相对机器的高度）或「第 1 层（共 3 层）」，
     *       再接层名；行首一小列刻度标出是从下往上第几层。机器那格四角品红光呼吸，别的层在机器那一竖列的格子四角点淡薰衣草，方便上下对齐；</li>
     *   <li>可选的格子（StructurePattern 的 optional，比如熔炉的水晶柱位置）画淡、提示「可选」，图例名字后面加「（可选）」。</li>
     * </ul>
     * 网格下面是图例（单层页列出 key 里的全部定义，多层页只列这一层用到的）。
     * 格子默认 18 像素；层太大、阅读栏放不下时整体缩小（最小 8 像素）。
     */
    public static final class Layout extends Figure {

        private static final int CELL = 18;
        private static final int MIN_CELL = 8;
        private static final int GAP = 10;
        private static final int CAPTION_H = 12;
        /** 层刻度：每层一小段，从下往上，当前层品红 */
        private static final int INDICATOR_W = 9;
        private static final int MAX_INDICATOR_LAYERS = 8;

        /**
         * 一种格子。
         *
         * @param blocks   候选方块（多个时每秒轮换显示）
         * @param air      这一格必须是空的（画虚线框）
         * @param optional 可选（画淡）
         * @param label    悬停提示里额外的说明，可为 null
         * @param name     图例里的名字
         */
        private record Legend(Block[] blocks, boolean air, boolean optional, @Nullable Component label, Component name) {
        }

        /** 图例的一项：lines 是名字排好的行（放得下就一行，比整张图还宽时折成几行） */
        private record LegendSlot(Legend legend, List<FormattedCharSequence> lines, int x, int y) {
        }

        private final Legend[] grid;
        private final int rows;
        private final int columns;
        @Nullable
        private final Component caption;
        private final int layerIndex;
        private final int layerCount;
        /** 机器所在的行、列（-1 表示不标）；anchorHere 为 false 时只在那一格四角点淡薰衣草 */
        private final int anchorRow;
        private final int anchorColumn;
        private final boolean anchorHere;
        private final ItemStack thrown;
        private final ItemStack result;
        private final float chance;
        private final Component thrownLabel;
        private final Component resultLabel;
        /** 「80%」：渲染路径上不新建 Component，构造时取好 */
        private final Component chanceText;
        /** 别的层在机器那一竖列的格子：悬停说明 */
        private static final List<Component> ANCHOR_COLUMN = List.of(Component.translatable("guide.essentialcraft5.structure.anchor_column"));
        private static final List<Component> ANCHOR_HERE = List.of(Component.translatable("guide.essentialcraft5.structure.anchor"));
        private final int sideWidth;
        private final boolean sideBelow;
        private final List<LegendSlot> legendSlots = new ArrayList<>();
        private final int legendHeight;
        private final int captionWidth;
        /** 标题行（「高度：-1 · 层名」）：比阅读栏宽时折成几行（层名常写成一句说明） */
        private final List<FormattedCharSequence> captionLines = new ArrayList<>();
        private final int cell;
        private final int width;
        private final Font font;

        private Layout(Legend[] grid, int rows, int columns, List<Legend> legendOrder, @Nullable Component caption, int layerIndex, int layerCount,
                       int anchorRow, int anchorColumn, boolean anchorHere, ItemStack thrown, ItemStack result, float chance,
                       Font font, int maxWidth, int maxHeight) {
            this.font = font;
            this.grid = grid;
            this.rows = rows;
            this.columns = columns;
            this.caption = caption;
            this.layerIndex = layerIndex;
            this.layerCount = layerCount;
            this.anchorRow = anchorRow;
            this.anchorColumn = anchorColumn;
            this.anchorHere = anchorHere;
            this.thrown = thrown;
            this.result = result;
            this.chance = chance;
            this.thrownLabel = Component.translatable("guide.essentialcraft5.ritual.throw");
            this.resultLabel = Component.translatable("guide.essentialcraft5.ritual.result");
            this.chanceText = Component.translatable("guide.essentialcraft5.ritual.chance", Math.round(chance * 100F));

            // 图例：小图标 + 名字，一行放不下就换行。单个名字比整张图还宽时，名字自己折成几行（照正文的中文折行规则），
            // 不然界面缩放 3 下长名字会伸出阅读栏（展示厅截图：线圈基座「可叠多层，每层射程 +2」被切掉）
            int lx = 0;
            int ly = 0;
            int rowH = LINE_H;
            int legendW = 0;
            int textMax = Math.max(1, maxWidth - 10);
            for (Legend legend : legendOrder) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                int textW = font.width(legend.name());
                if (textW <= textMax) {
                    lines.add(legend.name().getVisualOrderText());
                } else {
                    // 照正文的中英混排折行规则（避头尾、英文按词断），见 GuideTextLayout
                    textW = 0;
                    for (String line : new GuideTextLayout(font).wrapPlain(legend.name().getString(), textMax, 8)) {
                        lines.add(FormattedCharSequence.forward(line, Style.EMPTY));
                        textW = Math.max(textW, font.width(line));
                    }
                }
                int itemW = 10 + textW;
                if (lx > 0 && lx + itemW > maxWidth) {
                    lx = 0;
                    ly += rowH;
                    rowH = LINE_H;
                }
                legendSlots.add(new LegendSlot(legend, List.copyOf(lines), lx, ly));
                rowH = Math.max(rowH, Math.max(1, lines.size()) * LINE_H);
                lx += itemW + 8;
                legendW = Math.max(legendW, lx - 8);
            }
            this.legendHeight = legendOrder.isEmpty() ? 0 : ly + rowH;
            boolean indicator = layerCount > 1 && layerCount <= MAX_INDICATOR_LAYERS;
            int indicatorW = indicator ? INDICATOR_W : 0;
            int captionTextW = 0;
            if (caption != null) {
                if (font.width(caption) + indicatorW <= maxWidth) {
                    captionLines.add(caption.getVisualOrderText());
                    captionTextW = font.width(caption);
                } else {
                    for (String line : new GuideTextLayout(font).wrapPlain(caption.getString(), Math.max(20, maxWidth - indicatorW), 4)) {
                        captionLines.add(FormattedCharSequence.forward(line, caption.getStyle()));
                        captionTextW = Math.max(captionTextW, font.width(line));
                    }
                }
            }
            this.captionWidth = caption == null ? 0 : indicatorW + captionTextW;

            // 格子大小：宽度、高度（扣掉标题行和图例）都放得下为止
            int availH = maxHeight - captionHeight() - (legendHeight > 0 ? 6 + legendHeight : 0);
            int fit = Math.min(maxWidth / Math.max(1, columns), availH / Math.max(1, rows));
            this.cell = Mth.clamp(fit, MIN_CELL, CELL);

            // 右侧的「丢出 / 产出」栏；网格加右栏放不下时挪到网格下面
            int labelW = Math.max(font.width(thrownLabel), font.width(resultLabel));
            int chanceW = chance >= 0F ? font.width(chanceText) + 4 : 0;
            int side = thrown.isEmpty() && result.isEmpty() ? 0 : labelW + 4 + 18 + chanceW;
            int gridW = columns * cell;
            this.sideWidth = side;
            this.sideBelow = side > 0 && gridW + GAP + side > maxWidth;
            int bodyW = side == 0 ? gridW : sideBelow ? Math.max(gridW, side) : gridW + GAP + side;
            this.width = Math.min(maxWidth, Math.max(Math.max(bodyW, legendW), captionWidth));
        }

        /**
         * 研究手册 JSON 里摆法页的第 index 层（单层页 index = 0）。
         *
         * @param maxHeight 这张图最多能占多高，层太大时格子缩小
         */
        public static Layout json(FigureSpec.Structure page, int index, Font font, int maxWidth, int maxHeight) {
            List<FigureSpec.StructureLayer> layers = page.layers();
            FigureSpec.StructureLayer layer = layers.get(index);
            int count = layers.size();
            Map<Character, Legend> byChar = new LinkedHashMap<>();
            for (Map.Entry<Character, FigureSpec.StructureKey> entry : layer.key().entrySet()) {
                byChar.put(entry.getKey(), resolve(entry.getValue()));
            }
            List<String> pattern = layer.pattern();
            int rows = pattern.size();
            int columns = columns(pattern);
            Legend[] grid = new Legend[rows * columns];
            Set<Character> used = new HashSet<>();
            for (int r = 0; r < rows; r++) {
                String row = pattern.get(r);
                for (int c = 0; c < row.length(); c++) {
                    Legend legend = row.charAt(c) == ' ' ? null : byChar.get(row.charAt(c));
                    if (legend != null) {
                        grid[r * columns + c] = legend;
                        used.add(row.charAt(c));
                    }
                }
            }
            // 单层页照旧列出 key 的全部定义；多层页只列这一层用到的
            List<Legend> legendOrder = new ArrayList<>();
            for (Map.Entry<Character, Legend> entry : byChar.entrySet()) {
                if (count == 1 || used.contains(entry.getKey())) {
                    legendOrder.add(entry.getValue());
                }
            }

            // 机器位置：在这一层就四角品红光呼吸；在别的层（且各层一样大）就在同一竖列点淡薰衣草
            int anchorRow = -1;
            int anchorColumn = -1;
            boolean anchorHere = false;
            int anchorLayer = -1;
            if (page.anchor() != null) {
                char anchor = page.anchor();
                for (int l = 0; l < count && anchorLayer < 0; l++) {
                    List<String> p = layers.get(l).pattern();
                    for (int r = 0; r < p.size() && anchorLayer < 0; r++) {
                        int c = p.get(r).indexOf(anchor);
                        if (c >= 0) {
                            anchorLayer = l;
                            anchorRow = r;
                            anchorColumn = c;
                        }
                    }
                }
                anchorHere = anchorLayer == index;
                if (!anchorHere && (anchorLayer < 0 || layers.get(anchorLayer).pattern().size() != rows
                        || columns(layers.get(anchorLayer).pattern()) != columns)) {
                    anchorRow = -1;
                    anchorColumn = -1;
                }
            }

            Component base = null;
            if (count > 1) {
                base = anchorLayer >= 0
                        ? Component.translatable("guide.essentialcraft5.structure.height", index - anchorLayer)
                        : Component.translatable("guide.essentialcraft5.structure.layer", index + 1, count);
            }
            Component label = layer.label() == null ? null : Component.literal(Language.getInstance().getOrDefault(layer.label()));
            boolean single = count == 1;
            return new Layout(grid, rows, columns, legendOrder, join(base, label), index, count, anchorRow, anchorColumn, anchorHere,
                    single ? stack(page.thrown()) : ItemStack.EMPTY, single ? stack(page.result()) : ItemStack.EMPTY, single ? page.chance() : -1F,
                    font, maxWidth, maxHeight);
        }

        /**
         * 代码里登记的结构（StructurePattern）的一层。
         *
         * @param index 这是要画的第几层（从下往上，0 起），给层刻度用
         * @param count 一共画几层
         */
        public static Layout code(StructurePattern.Layer layer, int index, int count, Font font, int maxWidth, int maxHeight) {
            Map<StructurePattern.Rule, Legend> byRule = new LinkedHashMap<>();
            Legend[] grid = new Legend[layer.width() * layer.depth()];
            for (int r = 0; r < layer.depth(); r++) {
                for (int c = 0; c < layer.width(); c++) {
                    StructurePattern.Rule rule = layer.at(c, r);
                    if (rule != null) {
                        grid[r * layer.width() + c] = byRule.computeIfAbsent(rule, Layout::resolve);
                    }
                }
            }
            List<Legend> legendOrder = new ArrayList<>();
            for (StructurePattern.Rule rule : layer.distinctRules()) {
                legendOrder.add(byRule.get(rule));
            }
            Component caption = count > 1 ? Component.translatable("guide.essentialcraft5.structure.height", layer.y()) : null;
            return new Layout(grid, layer.depth(), layer.width(), legendOrder, caption, index, count,
                    layer.anchorRow(), layer.anchorColumn(), layer.y() == 0, ItemStack.EMPTY, ItemStack.EMPTY, -1F,
                    font, maxWidth, maxHeight);
        }

        private static int columns(List<String> pattern) {
            int columns = 0;
            for (String row : pattern) {
                columns = Math.max(columns, row.length());
            }
            return columns;
        }

        @Nullable
        private static Component join(@Nullable Component base, @Nullable Component label) {
            if (base == null) {
                return label;
            }
            return label == null ? base : Component.empty().append(base).append(" · ").append(label);
        }

        private static ItemStack stack(@Nullable ResourceLocation id) {
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
                return ItemStack.EMPTY;
            }
            Item item = ForgeRegistries.ITEMS.getValue(id);
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }

        private static Legend resolve(FigureSpec.StructureKey key) {
            Component label = key.label() == null ? null : Component.literal(Language.getInstance().getOrDefault(key.label()));
            if (key.air()) {
                return new Legend(new Block[0], true, false, label, Component.translatable("guide.essentialcraft5.structure.air"));
            }
            List<Block> blocks = new ArrayList<>();
            for (ResourceLocation id : key.blocks()) {
                if (ForgeRegistries.BLOCKS.containsKey(id)) {
                    blocks.add(ForgeRegistries.BLOCKS.getValue(id));
                }
            }
            ITagManager<Block> tags = ForgeRegistries.BLOCKS.tags();
            if (key.tag() != null && tags != null) {
                // 标签成员来自服务端同步的标签，进服后才有
                for (Block block : tags.getTag(tags.createTagKey(key.tag()))) {
                    blocks.add(block);
                }
            }
            Component name = blocks.isEmpty()
                    ? Component.literal(key.tag() != null ? "#" + key.tag() : "?")
                    : blocks.get(0).getName();
            return new Legend(blocks.toArray(new Block[0]), false, false, label, name);
        }

        /** 代码里的规则 → 格子：图例用规则自己的图例文字；不是单纯「某个方块」的规则（标签、谓词、任意方块）悬停时也写出来 */
        private static Legend resolve(StructurePattern.Rule rule) {
            boolean optional = rule.isOptional();
            Component name;
            Component label = null;
            Block[] blocks;
            if (rule.isAir()) {
                blocks = new Block[0];
                // 没给图例键的空气规则沿用手册的「必须是空的」，和 JSON 摆法页一致
                name = rule.legendKey() != null ? rule.legend() : Component.translatable("guide.essentialcraft5.structure.air");
            } else {
                blocks = rule.displayBlocks().toArray(new Block[0]);
                name = rule.legend();
                if (!rule.usesBlockName()) {
                    label = name;
                }
            }
            if (optional) {
                name = Component.translatable("guide.essentialcraft5.structure.optional_entry", name);
            }
            return new Legend(blocks, rule.isAir(), optional, label, name);
        }

        private int gridHeight() {
            return rows * cell;
        }

        private int captionHeight() {
            return caption == null ? 0 : CAPTION_H + Math.max(0, captionLines.size() - 1) * LINE_H;
        }

        private int bodyHeight() {
            int side = sideWidth == 0 ? 0 : 18 * 2 + 4;
            return sideBelow ? gridHeight() + 6 + side : Math.max(gridHeight(), side);
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return captionHeight() + bodyHeight() + (legendHeight > 0 ? 6 + legendHeight : 0);
        }

        @Override
        public void render(GuideFrame f, int x, int y) {
            if (caption != null) {
                renderCaption(f, x + (width - captionWidth) / 2, y + 1);
            }
            int top = y + captionHeight();
            int gridW = columns * cell;
            int bodyW = sideWidth == 0 ? gridW : sideBelow ? Math.max(gridW, sideWidth) : gridW + GAP + sideWidth;
            int left = x + (width - bodyW) / 2;
            int gridLeft = sideBelow ? left + (bodyW - gridW) / 2 : left;
            int gridTop = sideBelow ? top : top + Math.max(0, (bodyHeight() - gridHeight()) / 2);
            boolean ritual = !thrown.isEmpty();
            int centerRow = rows / 2;
            int centerCol = columns / 2;

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    Legend cellLegend = grid[r * columns + c];
                    boolean anchorCell = anchorRow == r && anchorColumn == c;
                    int cx = gridLeft + c * cell;
                    int cy = gridTop + r * cell;
                    List<Component> extra = anchorCell && !anchorHere ? ANCHOR_COLUMN : List.of();
                    if (cellLegend != null) {
                        renderCell(f, cellLegend, cx, cy, extra);
                    } else if (anchorCell) {
                        // 机器那格没写规则（或上下层那一竖列是空的）：机器层画空格子，别的层只留标记；悬停说明
                        if (anchorHere) {
                            slotBackground(f, cx, cy, f.hovered(cx, cy, cell, cell));
                        }
                        if (f.hovered(cx, cy, cell, cell)) {
                            f.hotspot(cx, cy, cell, cell, ItemStack.EMPTY, anchorHere ? ANCHOR_HERE : extra);
                        }
                    }
                    if (anchorCell && anchorHere) {
                        // 机器那一格：四角静止的品红饰角（上一版 1.5 秒呼吸一次，阅读栏里不放常驻动画）
                        GuideTheme.corners(f.g, cx - 2, cy - 2, cell + 4, cell + 4, 5,
                                f.fill(GuideTheme.fade(GuideTheme.FOCUS, 0.85F)), 1F);
                    } else if (anchorCell) {
                        GuideTheme.corners(f.g, cx + 1, cy + 1, cell - 2, cell - 2, 3, f.fill(GuideTheme.fade(GuideTheme.GLOW, 0.7F)), 1F);
                    }
                    if (ritual && cellLegend != null && r == centerRow && c == centerCol) {
                        // 物品丢在这一格的正上方：四角静止的品红饰角，右上角悬着要丢的物品
                        // （上一版饰角 1.5 秒呼吸、物品 2 秒上下浮动；阅读栏里不放常驻动画）
                        GuideTheme.corners(f.g, cx - 2, cy - 2, cell + 4, cell + 4, 5,
                                f.fill(GuideTheme.fade(GuideTheme.FOCUS, 0.85F)), 1F);
                        f.g.pose().pushPose();
                        f.g.pose().translate(0F, 0F, 100F);
                        f.smallItem(thrown, cx + cell / 2, cy - 5, 0.625F);
                        f.g.pose().popPose();
                    }
                }
            }

            if (sideWidth > 0) {
                int sx = sideBelow ? left + (bodyW - sideWidth) / 2 : gridLeft + gridW + GAP;
                int sy = sideBelow ? gridTop + gridHeight() + 6 : top + (bodyHeight() - 40) / 2;
                int labelW = Math.max(font.width(thrownLabel), font.width(resultLabel));
                if (!thrown.isEmpty()) {
                    f.g.drawString(font, thrownLabel, sx, sy + 5, f.text(GuideTheme.TEXT_DIM), false);
                    f.slot(thrown, sx + labelW + 4, sy);
                }
                if (!result.isEmpty()) {
                    int ry = sy + 22;
                    f.g.drawString(font, resultLabel, sx, ry + 5, f.text(GuideTheme.TEXT_DIM), false);
                    f.slot(result, sx + labelW + 4, ry);
                    if (chance >= 0F) {
                        f.g.drawString(font, chanceText, sx + labelW + 4 + 18 + 4, ry + 5, f.text(GuideTheme.GLOW), false);
                    }
                }
            }

            if (legendHeight > 0) {
                int ly0 = top + bodyHeight() + 6;
                for (LegendSlot slot : legendSlots) {
                    Legend item = slot.legend();
                    int lx = x + slot.x();
                    int ly = ly0 + slot.y();
                    if (item.air()) {
                        dashed(f, lx, ly, 8);
                    } else if (item.blocks().length > 0) {
                        f.smallBlock(cycle(f, item.blocks()), lx, ly, 0.5F);
                        if (item.optional()) {
                            fade(f, lx, ly, 8);
                        }
                    }
                    int lineY = ly;
                    for (FormattedCharSequence line : slot.lines()) {
                        f.g.drawString(font, line, lx + 10, lineY, f.text(GuideTheme.TEXT_DIM), false);
                        lineY += LINE_H;
                    }
                }
            }
        }

        /** 标题行：层刻度（从下往上每层一小段，当前层品红）+「高度：-1 · 层名」 */
        private void renderCaption(GuideFrame f, int x, int y) {
            int tx = x;
            if (layerCount > 1 && layerCount <= MAX_INDICATOR_LAYERS) {
                // 8 像素高里分 layerCount 段，段间 1 像素
                int segment = Math.max(1, (8 - (layerCount - 1)) / layerCount);
                int total = layerCount * segment + (layerCount - 1);
                int bottom = y + 4 + total / 2;
                for (int i = 0; i < layerCount; i++) {
                    int sy = bottom - (i + 1) * segment - i;
                    f.g.fill(x, sy, x + 6, sy + segment, f.fill(i == layerIndex ? GuideTheme.FOCUS : GuideTheme.BORDER));
                }
                tx += INDICATOR_W;
            }
            int ly = y;
            for (FormattedCharSequence line : captionLines) {
                f.g.drawString(font, line, tx, ly, f.text(GuideTheme.GLOW), false);
                ly += LINE_H;
            }
        }

        private void renderCell(GuideFrame f, Legend legend, int x, int y, List<Component> extra) {
            boolean hover = f.hovered(x, y, cell, cell);
            if (legend.air()) {
                dashed(f, x + 1, y + 1, cell - 2);
                if (hover) {
                    f.hotspot(x, y, cell, cell, ItemStack.EMPTY, lines(legend, null, extra));
                }
                return;
            }
            slotBackground(f, x, y, hover);
            if (legend.blocks().length == 0) {
                if (hover) {
                    f.hotspot(x, y, cell, cell, ItemStack.EMPTY, lines(legend, null, extra));
                }
                return;
            }
            Block block = cycle(f, legend.blocks());
            if (cell == CELL) {
                f.blockIcon(block, x + 1, y + 1);
            } else {
                f.smallBlock(block, x + 1, y + 1, (cell - 2) / 16F);
            }
            if (legend.optional()) {
                fade(f, x + 1, y + 1, cell - 2);
            }
            if (hover) {
                ItemStack stack = block.asItem() == Items.AIR ? ItemStack.EMPTY : new ItemStack(block);
                f.hotspot(x, y, cell, cell, stack, lines(legend, stack.isEmpty() ? block : null, extra));
            }
        }

        /** 格子底：18 像素用手册的物品格，缩小时画同样配色的磨砂方框 */
        private void slotBackground(GuideFrame f, int x, int y, boolean hover) {
            if (cell == CELL) {
                GuideTheme.slot(f.g, x, y, hover ? 1F : 0F, f.alpha);
                return;
            }
            GuideTheme.frostCell(f.g, x, y, cell, f.fill(hover ? GuideTheme.FOCUS : GuideTheme.SLOT_EDGE), f.alpha);
        }

        /** 提示内容：没有物品形态的方块先写方块名；再加说明、「可选」、候选数量、机器竖列之类的附注 */
        private static List<Component> lines(Legend cell, @Nullable Block nameless, List<Component> extra) {
            List<Component> lines = new ArrayList<>();
            if (cell.air()) {
                lines.add(cell.name());
            }
            if (nameless != null) {
                lines.add(nameless.getName());
            }
            if (cell.label() != null) {
                lines.add(cell.label());
            }
            if (cell.optional()) {
                lines.add(Component.translatable("guide.essentialcraft5.structure.optional"));
            }
            if (cell.blocks().length > 1) {
                lines.add(Component.translatable("guide.essentialcraft5.recipe.alternatives", cell.blocks().length));
            }
            lines.addAll(extra);
            return lines;
        }

        private static Block cycle(GuideFrame f, Block[] blocks) {
            return blocks[f.cycleIndex(blocks.length)];
        }

        /**
         * 「可选」的格子：图标上罩一层浅色薄纱（像隔着磨砂玻璃看，颜色变淡、深色物品反而更显形），再描一圈薰衣草虚线
         * （物品图标画在 z≈150，罩层要更高）。
         *
         * <p>原先盖的是半透明的深色格子底：深色方块（米斯莱护板、水晶柱）一盖，格子里最亮的像素就是底色本身，完全看不见。
         * 「必须空着」的格子是没有底的灰虚线框，和这个带底、带图标的淡格子一眼分得开。</p>
         */
        private static void fade(GuideFrame f, int x, int y, int size) {
            f.g.pose().pushPose();
            f.g.pose().translate(0F, 0F, 200F);
            f.g.fill(x, y, x + size, y + size, GuideTheme.fade(GuideTheme.OPTIONAL_VEIL, f.alpha));
            int color = f.fill(GuideTheme.withAlpha(GuideTheme.GLOW, 0xD0));
            for (int i = 0; i < size; i += 3) {
                int end = Math.min(size, i + 2);
                f.g.fill(x + i, y, x + end, y + 1, color);
                f.g.fill(x + i, y + size - 1, x + end, y + size, color);
                f.g.fill(x, y + i, x + 1, y + end, color);
                f.g.fill(x + size - 1, y + i, x + size, y + end, color);
            }
            f.g.pose().popPose();
        }

        /** 虚线方框：「这一格必须是空的」 */
        private static void dashed(GuideFrame f, int x, int y, int size) {
            int color = f.fill(GuideTheme.TEXT_DIM);
            for (int i = 0; i < size; i += 3) {
                int end = Math.min(size, i + 2);
                f.g.fill(x + i, y, x + end, y + 1, color);
                f.g.fill(x + i, y + size - 1, x + end, y + size, color);
                f.g.fill(x, y + i, x + 1, y + end, color);
                f.g.fill(x + size - 1, y + i, x + size, y + end, color);
            }
        }
    }
}
