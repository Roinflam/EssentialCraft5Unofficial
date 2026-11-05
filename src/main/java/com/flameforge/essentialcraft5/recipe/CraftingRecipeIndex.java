package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.menu.GhostSlots;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.IShapedRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 合成框架的「放产物反查配方」（batch4 §3.3）：按产物找工作台配方、把配方摆成 9 格样本。
 * <p>
 * 修的原作问题（{@code ContainerCraftingFrame} L99~126、{@code ECUtils.findRecipeByIS} L508~562）：
 * 原料不满 9 格（2×2、少于 9 件的无序配方）时下标越界、点击在服务端抛异常；配方按行排、框架按列排又没转置，不对称的配方（楼梯）摆反了装进合成器配不上；
 * 每格随机挑一个可选原料；每次点击都扫一遍全部配方。
 * <p>
 * 索引：{@code Map<物品, 配方表>}，第一次用时从 {@code getAllRecipesFor(CRAFTING)} 建，跳过 {@code isSpecial()}（烟花、染色这类代码里现算的）、
 * {@code isIncomplete()}（标签是空的）和产物为空的，每种产物的配方按 ID 排序（{@code byType} 背后是 HashMap，遍历顺序不固定）。
 * 按 RecipeManager 实例缓存（弱引用，{@code /reload} 后旧的自动丢掉），恶意客户端连点也不会每次扫全表。
 */
public final class CraftingRecipeIndex {

    private static final Map<RecipeManager, Map<Item, List<CraftingRecipe>>> INDEX = Collections.synchronizedMap(new WeakHashMap<>());

    private CraftingRecipeIndex() {
    }

    /** 产出这件物品的工作台配方（按 ID 排序）；产物本身带 NBT 的配方要求 NBT 也一样 */
    public static List<CraftingRecipe> recipesFor(Level level, ItemStack product) {
        if (product.isEmpty()) {
            return List.of();
        }
        RegistryAccess access = level.registryAccess();
        List<CraftingRecipe> all = index(level.getRecipeManager(), access).getOrDefault(product.getItem(), List.of());
        List<CraftingRecipe> out = new ArrayList<>(all.size());
        for (CraftingRecipe recipe : all) {
            ItemStack result = recipe.getResultItem(access);
            if (result.getTag() == null || ItemStack.isSameItemSameTags(result, product)) {
                out.add(recipe);
            }
        }
        return out;
    }

    /**
     * 反查：从 current 之后（循环）找第一条能摆成样本、摆出来又配得上自己的配方；current 不在表里就从第一条开始。
     * 返回 null 表示没有能用的配方。
     */
    @Nullable
    public static Choice next(Level level, ItemStack product, @Nullable ResourceLocation current) {
        List<CraftingRecipe> recipes = recipesFor(level, product);
        if (recipes.isEmpty()) {
            return null;
        }
        int start = 0;
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).getId().equals(current)) {
                start = i + 1;
                break;
            }
        }
        for (int n = 0; n < recipes.size(); n++) {
            CraftingRecipe recipe = recipes.get((start + n) % recipes.size());
            ItemStack[] samples = layout(recipe);
            if (samples == null) {
                continue;
            }
            try {
                if (recipe.matches(new CrafterGrid(samples), level)) {
                    return new Choice(recipe, samples);
                }
            } catch (RuntimeException ignored) {
                // 别的模组的配方在这种摆法上抛异常：跳过这条
            }
        }
        return null;
    }

    /** 反查的结果：配方和摆好的 9 格样本 */
    public record Choice(CraftingRecipe recipe, ItemStack[] samples) {
    }

    /**
     * 把配方摆成 9 格样本（按行排）：有序配方按宽高从左上角放，无序配方从 0 号格依次放；每格取原料可选物品里的第一个（确定的，原作随机挑）。
     * 摆不下（超过 3×3、超过 9 件）、或者某个原料一个可选物品都没有时返回 null。
     */
    @Nullable
    public static ItemStack[] layout(CraftingRecipe recipe) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        ItemStack[] samples = CraftingPattern.emptySamples();
        if (recipe instanceof IShapedRecipe<?> shaped) {
            int width = shaped.getRecipeWidth();
            int height = shaped.getRecipeHeight();
            if (width < 1 || height < 1 || width > CrafterGrid.WIDTH || height > CrafterGrid.HEIGHT || ingredients.size() != width * height) {
                return null;
            }
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    Ingredient ingredient = ingredients.get(col + row * width);
                    if (ingredient.isEmpty()) {
                        continue;
                    }
                    ItemStack sample = firstItem(ingredient);
                    if (sample.isEmpty()) {
                        return null;
                    }
                    samples[col + row * CrafterGrid.WIDTH] = sample;
                }
            }
            return samples;
        }
        List<Ingredient> nonEmpty = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                nonEmpty.add(ingredient);
            }
        }
        if (nonEmpty.isEmpty() || nonEmpty.size() > CrafterGrid.SIZE) {
            return null;
        }
        for (int i = 0; i < nonEmpty.size(); i++) {
            ItemStack sample = firstItem(nonEmpty.get(i));
            if (sample.isEmpty()) {
                return null;
            }
            samples[i] = sample;
        }
        return samples;
    }

    private static ItemStack firstItem(Ingredient ingredient) {
        for (ItemStack option : ingredient.getItems()) {
            if (!option.isEmpty()) {
                return GhostSlots.sampleOf(option);
            }
        }
        return ItemStack.EMPTY;
    }

    private static Map<Item, List<CraftingRecipe>> index(RecipeManager manager, RegistryAccess access) {
        Map<Item, List<CraftingRecipe>> cached = INDEX.get(manager);
        if (cached != null) {
            return cached;
        }
        Map<Item, List<CraftingRecipe>> built = new HashMap<>();
        for (CraftingRecipe recipe : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            try {
                if (recipe.isSpecial() || recipe.isIncomplete()) {
                    continue;
                }
                ItemStack result = recipe.getResultItem(access);
                if (result == null || result.isEmpty()) {
                    continue;
                }
                built.computeIfAbsent(result.getItem(), item -> new ArrayList<>()).add(recipe);
            } catch (RuntimeException ignored) {
                // 别的模组的配方取产物时抛异常：不进索引
            }
        }
        Comparator<CraftingRecipe> byId = Comparator.comparing(recipe -> recipe.getId().toString());
        built.replaceAll((item, list) -> {
            list.sort(byId);
            return List.copyOf(list);
        });
        Map<Item, List<CraftingRecipe>> frozen = Map.copyOf(built);
        INDEX.put(manager, frozen);
        return frozen;
    }
}
