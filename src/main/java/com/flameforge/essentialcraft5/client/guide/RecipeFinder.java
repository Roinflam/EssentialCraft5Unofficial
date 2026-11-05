package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.recipe.RadiatingChamberRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从客户端的 RecipeManager 里找「产出某物品」的配方。指南的配方页不写死配方，靠它现查，
 * 所以数据包、KubeJS 改了配方，书里自动跟着变。
 * <p>
 * 认界面画得出来的：工作台（有序 / 无序）、魔力工作台、辐射仓、熔炉这四种内置的，加上模块用
 * {@link GuideRecipeViews#register} 登记过画法的配方类型（{@link Kind#CUSTOM}，米斯莱熔炉、矿石加工……）。
 * 按结果物品建一次索引：每次登录是新的 RecipeManager 实例，按实例判断；/reload 时服务端重发配方、
 * 客户端在同一个实例上 replaceRecipes，这种情况靠 RecipesUpdatedEvent 调 {@link #invalidate}。
 */
public final class RecipeFinder {

    /** 画法分类，顺序就是同一物品多个配方时的显示顺序（登记的类型排在内置四种之后，按登记先后） */
    public enum Kind {
        CRAFTING, MAGICIAN_TABLE, RADIATING_CHAMBER, COOKING, CUSTOM
    }

    private static final Comparator<Recipe<?>> ORDER = Comparator.comparingInt((Recipe<?> recipe) -> {
        Kind kind = kind(recipe);
        return kind == null ? Integer.MAX_VALUE : kind.ordinal();
    }).thenComparingInt(recipe -> GuideRecipeViews.order(recipe.getType())).thenComparing(recipe -> recipe.getId().toString());

    @Nullable
    private static RecipeManager indexedManager;
    private static Map<Item, List<Recipe<?>>> index = Map.of();

    private RecipeFinder() {
    }

    /** 产出这个物品的全部配方（已排序）。没登录（没有配方表）时返回空表 */
    public static List<Recipe<?>> find(@Nullable RecipeManager manager, RegistryAccess access, Item item) {
        if (manager == null) {
            return List.of();
        }
        if (manager != indexedManager) {
            index = buildIndex(manager, access);
            indexedManager = manager;
        }
        return index.getOrDefault(item, List.of());
    }

    /**
     * 某个配方类型下全部画得出来的配方，按配方 id 排序（配方页的 recipe_type）。
     *
     * @param outputs 不为空时只要产出这些物品之一的
     */
    public static List<Recipe<?>> ofType(@Nullable RecipeManager manager, RegistryAccess access, RecipeType<?> type, Set<Item> outputs) {
        if (manager == null) {
            return List.of();
        }
        List<Recipe<?>> out = new ArrayList<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (recipe.getType() != type || kind(recipe) == null) {
                continue;
            }
            if (!outputs.isEmpty()) {
                ItemStack result = result(recipe, access);
                if (result.isEmpty() || !outputs.contains(result.getItem())) {
                    continue;
                }
            }
            out.add(recipe);
        }
        out.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        return out;
    }

    /**
     * 一个配方块要画的全部配方：写了 recipe 按 id 画那一条；写了 recipe_type 按类型列（有 item 时按产物筛），否则逐个物品按产物查。
     * 配方类型不存在、配方 id 不存在（或画不出来）时返回空表。
     */
    public static List<Recipe<?>> forPage(@Nullable RecipeManager manager, RegistryAccess access, FigureSpec.Recipes page) {
        if (page.recipe() != null) {
            if (manager == null) {
                return List.of();
            }
            Recipe<?> recipe = manager.byKey(page.recipe()).orElse(null);
            return recipe == null || kind(recipe) == null ? List.of() : List.of(recipe);
        }
        if (page.recipeType() == null) {
            List<Recipe<?>> out = new ArrayList<>();
            for (ResourceLocation id : page.items()) {
                Item item = item(id);
                if (item != null) {
                    out.addAll(find(manager, access, item));
                }
            }
            return out;
        }
        RecipeType<?> type = ForgeRegistries.RECIPE_TYPES.containsKey(page.recipeType()) ? ForgeRegistries.RECIPE_TYPES.getValue(page.recipeType()) : null;
        if (type == null) {
            return List.of();
        }
        Set<Item> outputs = new HashSet<>();
        for (ResourceLocation id : page.items()) {
            Item item = item(id);
            if (item != null) {
                outputs.add(item);
            }
        }
        return ofType(manager, access, type, outputs);
    }

    /** 配方表换了（RecipesUpdatedEvent）时调用，下次查询重建索引 */
    public static void invalidate() {
        indexedManager = null;
        index = Map.of();
    }

    @Nullable
    public static Kind kind(Recipe<?> recipe) {
        if (recipe instanceof MagicianTableRecipe) {
            return Kind.MAGICIAN_TABLE;
        }
        if (recipe instanceof RadiatingChamberRecipe) {
            return Kind.RADIATING_CHAMBER;
        }
        if (recipe.getType() == RecipeType.CRAFTING) {
            return Kind.CRAFTING;
        }
        if (recipe.getType() == RecipeType.SMELTING && recipe instanceof AbstractCookingRecipe) {
            return Kind.COOKING;
        }
        if (GuideRecipeViews.has(recipe.getType())) {
            return Kind.CUSTOM;
        }
        return null;
    }

    /** 配方的产物；个别模组的特殊配方在没有输入时取结果会出错，当作没有产物 */
    static ItemStack result(Recipe<?> recipe, RegistryAccess access) {
        try {
            ItemStack result = recipe.getResultItem(access);
            return result == null ? ItemStack.EMPTY : result;
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    @Nullable
    private static Item item(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        return item == null || item == Items.AIR ? null : item;
    }

    private static Map<Item, List<Recipe<?>>> buildIndex(RecipeManager manager, RegistryAccess access) {
        Map<Item, List<Recipe<?>>> map = new HashMap<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (kind(recipe) == null) {
                continue;
            }
            ItemStack result = result(recipe, access);
            if (result.isEmpty()) {
                continue;
            }
            map.computeIfAbsent(result.getItem(), item -> new ArrayList<>()).add(recipe);
        }
        for (List<Recipe<?>> list : map.values()) {
            list.sort(ORDER);
        }
        return map;
    }
}
