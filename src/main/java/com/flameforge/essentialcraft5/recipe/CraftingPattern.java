package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.CraftingFrameItem;
import com.flameforge.essentialcraft5.menu.GhostSlots;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.common.util.RecipeMatcher;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 合成框架的样板（物品 NBT）与它在合成器里的解析结果（batch4 §3.3）。
 *
 * <h2>NBT（键 {@value #TAG}）</h2>
 * {@code {Grid: [{Slot: 0~8, …样本（GhostSlots.save 的格式）}], Mode: "ingredient" | "exact", Recipe: "命名空间:路径"}}；样本按<b>行</b>排
 * （原作框架格子按列排、合成器按行排，读的时候转置、取走样本重算时又漏了转置）。读的时候全部校验：格子号在 0~8、配方 ID 用 {@code tryParse}、
 * 读不出的样本当空格；<b>产物不存</b>，每次用配方现算（原作存了一份产物，而且信它）。空样板、默认模式、没有配方时整个键删掉。
 *
 * <h2>两种模式（原作「用 / 不用矿物词典」）</h2>
 * <ul>
 *   <li>{@link Mode#INGREDIENT 按配方原料}（默认）：每格收配方在这个位置接受的所有物品（{@code Ingredient.test}）。
 *       有序配方像 {@code ShapedRecipe.matches} 那样试遍偏移和镜像，找到和样本对得上的摆法；无序配方用 Forge 的 {@code RecipeMatcher} 给每个样本分一个原料。
 *       拿不到位置的配方（特殊配方：原版 {@code CustomRecipe} 系——药箭、染色皮甲、修复物品、复制成书 / 地图 / 旗帜等——与模组自定义的类型）整张退回精确。原作的矿物词典比较把「两件都没有矿辞」当成相同，框架会收进配不上的东西然后卡死；
 *       按配方原料永远不会收进配方配不上的东西。</li>
 *   <li>{@link Mode#EXACT 精确}：每格只收和样本同物品、同 NBT 的（{@link GhostSlots#matches}，哈希样本比指纹），<b>不比数量</b>（原作连数量一起比，格子里超过 1 个就永远不合成）。</li>
 * </ul>
 *
 * <h2>解析（{@link #resolve}）</h2>
 * 不是框架、或 9 格样本全空 → {@link State#NONE}（「空框架当没装」，修原作空框架每 tick 空指针崩服、打开界面崩客户端）；
 * 配方 ID 找不到、不是工作台配方、或者样本已经配不上它（数据包改了配方）→ {@link State#INVALID}（不合成，界面提示）；其余 {@link State#VALID}。
 * 机器按「框架物品对象 + 它的 NBT 对象 + RecipeManager 实例」缓存（{@link Cache}），{@code /reload} 换了 RecipeManager 自动重算。
 */
public final class CraftingPattern {

    /** 框架物品 NBT 里的键 */
    public static final String TAG = "ec5_pattern";
    public static final int SIZE = CrafterGrid.SIZE;

    private static final String GRID = "Grid";
    private static final String SLOT = "Slot";
    private static final String MODE = "Mode";
    private static final String RECIPE = "Recipe";
    /** 读 Grid 列表时最多看这么多项（正常最多 9 项；/give 塞进来的超长列表不逐项解析） */
    private static final int MAX_GRID_ENTRIES = 64;

    /** 匹配模式 */
    public enum Mode {
        /** 按配方原料（默认） */
        INGREDIENT("ingredient"),
        /** 精确 */
        EXACT("exact");

        public final String id;

        Mode(String id) {
            this.id = id;
        }

        public Mode next() {
            return this == INGREDIENT ? EXACT : INGREDIENT;
        }

        public String translationKey() {
            return "gui.essentialcraft5.crafting_frame.mode." + id;
        }

        /** 不认识的（被改过的 NBT）按默认的「按配方原料」 */
        public static Mode byId(@Nullable String id) {
            return EXACT.id.equals(id) ? EXACT : INGREDIENT;
        }

        public static Mode byOrdinal(int ordinal) {
            return ordinal == EXACT.ordinal() ? EXACT : INGREDIENT;
        }
    }

    /** 装在合成器里的状态（界面同步的是 ordinal：0 没装、1 有效、2 失效） */
    public enum State {
        /** 没装框架，或框架是空的 */
        NONE,
        /** 有效：只收样板要的、按框架的配方合成 */
        VALID,
        /** 失效：配方不存在或配不上样本，不合成 */
        INVALID;

        public static State byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NONE;
        }
    }

    private static final CraftingPattern NONE_PATTERN = new CraftingPattern(State.NONE, emptySamples(), Mode.INGREDIENT, null, null);

    private final State state;
    private final ItemStack[] samples;
    private final Mode mode;
    @Nullable
    private final CraftingRecipe recipe;
    /** 每格收什么（只在 VALID 时有） */
    @Nullable
    private final List<Predicate<ItemStack>> cells;

    private CraftingPattern(State state, ItemStack[] samples, Mode mode, @Nullable CraftingRecipe recipe,
                            @Nullable List<Predicate<ItemStack>> cells) {
        this.state = state;
        this.samples = samples;
        this.mode = mode;
        this.recipe = recipe;
        this.cells = cells;
    }

    // ------------------------------------------------------------------
    // 框架物品的 NBT
    // ------------------------------------------------------------------

    public static ItemStack[] emptySamples() {
        ItemStack[] out = new ItemStack[SIZE];
        Arrays.fill(out, ItemStack.EMPTY);
        return out;
    }

    /** 9 格样本（按行排；没有的是空物品，数组本身不为 null） */
    public static ItemStack[] samples(ItemStack frame) {
        ItemStack[] out = emptySamples();
        CompoundTag data = frame.getTagElement(TAG);
        if (data == null) {
            return out;
        }
        ListTag grid = data.getList(GRID, Tag.TAG_COMPOUND);
        for (int i = 0; i < grid.size() && i < MAX_GRID_ENTRIES; i++) {
            CompoundTag entry = grid.getCompound(i);
            if (!entry.contains(SLOT, Tag.TAG_ANY_NUMERIC)) {
                continue;
            }
            int slot = entry.getInt(SLOT);
            if (slot >= 0 && slot < SIZE) {
                out[slot] = GhostSlots.load(entry);
            }
        }
        return out;
    }

    public static Mode mode(ItemStack frame) {
        CompoundTag data = frame.getTagElement(TAG);
        return data == null ? Mode.INGREDIENT : Mode.byId(data.getString(MODE));
    }

    /** 记下的配方 ID；没有或写坏了返回 null */
    @Nullable
    public static ResourceLocation recipeId(ItemStack frame) {
        CompoundTag data = frame.getTagElement(TAG);
        if (data == null || !data.contains(RECIPE, Tag.TAG_STRING)) {
            return null;
        }
        return ResourceLocation.tryParse(data.getString(RECIPE));
    }

    /** 写进框架（只动 {@value #TAG} 这个键；空样板、默认模式、没有配方时删掉这个键） */
    public static void write(ItemStack frame, ItemStack[] samples, Mode mode, @Nullable ResourceLocation recipe) {
        ListTag grid = new ListTag();
        for (int i = 0; i < SIZE && i < samples.length; i++) {
            ItemStack sample = samples[i];
            if (sample != null && !sample.isEmpty()) {
                CompoundTag entry = GhostSlots.save(sample);
                entry.putByte(SLOT, (byte) i);
                grid.add(entry);
            }
        }
        if (grid.isEmpty() && mode == Mode.INGREDIENT && recipe == null) {
            frame.removeTagKey(TAG);
            return;
        }
        CompoundTag data = new CompoundTag();
        if (!grid.isEmpty()) {
            data.put(GRID, grid);
        }
        data.putString(MODE, mode.id);
        if (recipe != null) {
            data.putString(RECIPE, recipe.toString());
        }
        frame.getOrCreateTag().put(TAG, data);
    }

    public static boolean allEmpty(ItemStack[] samples) {
        for (ItemStack sample : samples) {
            if (sample != null && !sample.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 按 ID 找工作台配方；没有、或者不是工作台配方返回 null */
    @Nullable
    public static CraftingRecipe craftingRecipe(RecipeManager manager, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        Optional<? extends Recipe<?>> found = manager.byKey(id);
        return found.isPresent() && found.get() instanceof CraftingRecipe crafting ? crafting : null;
    }

    /**
     * 样本能配出的配方 ID：记下的那条还配得上就留着它（反查选了第二条配方时不被第一条顶掉），否则现查一条（和原版工作台每改一格重算一次一样）；
     * 样本全空或配不出返回 null。
     */
    @Nullable
    public static ResourceLocation matchRecipe(ItemStack[] samples, @Nullable ResourceLocation current, Level level) {
        if (allEmpty(samples)) {
            return null;
        }
        CrafterGrid grid = new CrafterGrid(samples);
        RecipeManager manager = level.getRecipeManager();
        try {
            CraftingRecipe kept = craftingRecipe(manager, current);
            if (kept != null && kept.matches(grid, level)) {
                return kept.getId();
            }
            return manager.getRecipeFor(RecipeType.CRAFTING, grid, level)
                    .map(Recipe::getId).orElse(null);
        } catch (RuntimeException e) {
            // 别的模组的配方在奇怪的格子上抛异常：当作配不出（不让一次界面点击把服务器带崩）
            EssentialCraft5.LOGGER.warn("合成框架匹配配方时出错，当作配不出", e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    /** 没装框架 / 空框架 */
    public static CraftingPattern none() {
        return NONE_PATTERN;
    }

    /** 解析一个框架（规则见类注释）。level 为 null（还没进世界）或不是框架时返回 {@link #none()} */
    public static CraftingPattern resolve(ItemStack frame, @Nullable Level level) {
        if (level == null || !(frame.getItem() instanceof CraftingFrameItem)) {
            return NONE_PATTERN;
        }
        ItemStack[] samples = samples(frame);
        if (allEmpty(samples)) {
            return NONE_PATTERN;
        }
        Mode mode = mode(frame);
        CraftingRecipe recipe = craftingRecipe(level.getRecipeManager(), recipeId(frame));
        try {
            if (recipe == null || !recipe.matches(new CrafterGrid(samples), level)) {
                return new CraftingPattern(State.INVALID, samples, mode, null, null);
            }
            return new CraftingPattern(State.VALID, samples, mode, recipe, cellRules(recipe, samples, mode));
        } catch (RuntimeException e) {
            EssentialCraft5.LOGGER.warn("解析合成框架（配方 {}）时出错，当作失效", recipe == null ? null : recipe.getId(), e);
            return new CraftingPattern(State.INVALID, samples, mode, null, null);
        }
    }

    /** 每格收什么：按配方原料时尽量按位置取原料，拿不到位置的格子（或整张配方）退回精确 */
    private static List<Predicate<ItemStack>> cellRules(CraftingRecipe recipe, ItemStack[] samples, Mode mode) {
        Ingredient[] byCell = mode == Mode.INGREDIENT ? ingredientsByCell(recipe, samples) : null;
        List<Predicate<ItemStack>> rules = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            ItemStack sample = samples[i];
            if (sample.isEmpty()) {
                rules.add(stack -> false);
            } else if (byCell != null && byCell[i] != null && !byCell[i].isEmpty()) {
                Ingredient ingredient = byCell[i];
                rules.add(stack -> !stack.isEmpty() && ingredient.test(stack));
            } else {
                rules.add(stack -> !stack.isEmpty() && GhostSlots.matches(sample, stack));
            }
        }
        return rules;
    }

    /** 每格对应的配方原料；拿不到位置信息返回 null（整张退回精确），某一格分不到时那一格为 null */
    @Nullable
    static Ingredient[] ingredientsByCell(CraftingRecipe recipe, ItemStack[] samples) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof IShapedRecipe<?> shaped) {
            return shapedCells(shaped.getRecipeWidth(), shaped.getRecipeHeight(), ingredients, samples);
        }
        if (recipe instanceof ShapelessRecipe) {
            return shapelessCells(ingredients, samples);
        }
        return null;
    }

    /** 有序配方：照 ShapedRecipe.matches 的顺序试偏移和镜像（先镜像），找到和样本对得上的摆法 */
    @Nullable
    private static Ingredient[] shapedCells(int width, int height, NonNullList<Ingredient> ingredients, ItemStack[] samples) {
        if (width < 1 || height < 1 || width > CrafterGrid.WIDTH || height > CrafterGrid.HEIGHT || ingredients.size() != width * height) {
            return null;
        }
        for (int ox = 0; ox <= CrafterGrid.WIDTH - width; ox++) {
            for (int oy = 0; oy <= CrafterGrid.HEIGHT - height; oy++) {
                for (boolean mirrored : new boolean[] {true, false}) {
                    Ingredient[] cells = placeShaped(width, height, ingredients, samples, ox, oy, mirrored);
                    if (cells != null) {
                        return cells;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static Ingredient[] placeShaped(int width, int height, NonNullList<Ingredient> ingredients, ItemStack[] samples,
                                            int ox, int oy, boolean mirrored) {
        Ingredient[] cells = new Ingredient[SIZE];
        for (int x = 0; x < CrafterGrid.WIDTH; x++) {
            for (int y = 0; y < CrafterGrid.HEIGHT; y++) {
                int k = x - ox;
                int l = y - oy;
                Ingredient ingredient = Ingredient.EMPTY;
                if (k >= 0 && l >= 0 && k < width && l < height) {
                    ingredient = ingredients.get(mirrored ? width - k - 1 + l * width : k + l * width);
                }
                int cell = x + y * CrafterGrid.WIDTH;
                if (!ingredient.test(samples[cell])) {
                    return null;
                }
                cells[cell] = ingredient;
            }
        }
        return cells;
    }

    /** 无序配方：Forge 的 RecipeMatcher 给每个样本分一个原料（每格收它分到的那个原料接受的一切，合起来仍配得上这条配方） */
    @Nullable
    private static Ingredient[] shapelessCells(NonNullList<Ingredient> ingredients, ItemStack[] samples) {
        List<ItemStack> inputs = new ArrayList<>();
        List<Integer> cellOf = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            if (!samples[i].isEmpty()) {
                inputs.add(samples[i]);
                cellOf.add(i);
            }
        }
        if (inputs.size() != ingredients.size()) {
            return null;
        }
        int[] match = RecipeMatcher.findMatches(inputs, ingredients);
        if (match == null) {
            return null;
        }
        Ingredient[] cells = new Ingredient[SIZE];
        for (int j = 0; j < match.length; j++) {
            cells[cellOf.get(j)] = ingredients.get(match[j]);
        }
        return cells;
    }

    // ------------------------------------------------------------------
    // 读结果
    // ------------------------------------------------------------------

    public State state() {
        return state;
    }

    public boolean isValid() {
        return state == State.VALID;
    }

    /** 有效时的配方；否则 null */
    @Nullable
    public CraftingRecipe recipe() {
        return recipe;
    }

    public Mode mode() {
        return mode;
    }

    /** 第 cell 格的样本（按行排；空格是空物品） */
    public ItemStack sample(int cell) {
        return cell >= 0 && cell < SIZE ? samples[cell] : ItemStack.EMPTY;
    }

    /** 有效框架的第 cell 格收不收 stack（空物品、样本为空的格子一律不收）；不是有效框架时返回 false */
    public boolean accepts(int cell, ItemStack stack) {
        return cells != null && cell >= 0 && cell < SIZE && !stack.isEmpty() && cells.get(cell).test(stack);
    }

    @Override
    public String toString() {
        return "CraftingPattern[" + state.name().toLowerCase(Locale.ROOT) + ", " + mode.id + ", "
                + (recipe == null ? "-" : recipe.getId()) + "]";
    }

    /**
     * 解析结果的缓存：框架物品对象、它的 NBT 对象、RecipeManager 实例三者都没变就直接用上次的结果。
     * 不是框架的物品（包括 Forge 的 {@code SlotItemHandler.getMaxStackSize} 临时把框架格清空的那一下）直接返回 {@link #none()}，
     * 不顶掉缓存（否则每次都要重新解析）。只在一个线程里用（机器在服务端线程，客户端菜单在渲染线程）。
     */
    public static final class Cache {
        private ItemStack frame = ItemStack.EMPTY;
        @Nullable
        private CompoundTag tag;
        @Nullable
        private RecipeManager manager;
        private CraftingPattern pattern = NONE_PATTERN;

        public CraftingPattern get(ItemStack stack, @Nullable Level level) {
            if (level == null || !(stack.getItem() instanceof CraftingFrameItem)) {
                return NONE_PATTERN;
            }
            RecipeManager current = level.getRecipeManager();
            if (stack != frame || stack.getTag() != tag || current != manager) {
                frame = stack;
                tag = stack.getTag();
                manager = current;
                pattern = resolve(stack, level);
            }
            return pattern;
        }

        /** 下次一定重新解析 */
        public void invalidate() {
            frame = ItemStack.EMPTY;
            tag = null;
            manager = null;
            pattern = NONE_PATTERN;
        }
    }
}
