package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.recipe.OreSmeltingRecipe;
import com.flameforge.essentialcraft5.registry.ECMagmatic;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 岩浆合金（原作 ItemMagicalAlloy / magicalAlloy → magmatic_alloy）：矿石在岩浆冶炼炉、岩浆熔炉里熔成它，再炼成成品。
 * 单物品 + NBT：{@code recipe} 存矿石加工配方的 ID（原作存矿辞名）。第二层按配方颜色染色（客户端 MagmaticClient 注册），
 * 提示里写是哪种矿石熔的（原作取矿辞里第一个物品的名字）。
 * <p>
 * 创造模式物品栏按配方展开（原作 getSubItems 按矿表展开）：配方表只有客户端收到以后才知道，
 * 由 MagmaticClient 在 RecipesUpdatedEvent 里把配方交给 {@link #setKnownRecipes}，矿石标签是空的不展开。
 */
public class MagmaticAlloyItem extends Item implements CreativeVariants {

    public static final String TAG_RECIPE = "recipe";

    /** 客户端最近一次收到的矿石加工配方（按 {@link OreSmeltingRecipe#ORDER} 排好）；服务端恒为空 */
    private static volatile List<OreSmeltingRecipe> knownRecipes = List.of();

    public MagmaticAlloyItem(Properties properties) {
        super(properties);
    }

    /** 某条配方熔出来的合金 */
    public static ItemStack create(ResourceLocation recipe, int count) {
        ItemStack stack = new ItemStack(ECMagmatic.MAGMATIC_ALLOY.get(), count);
        stack.getOrCreateTag().putString(TAG_RECIPE, recipe.toString());
        return stack;
    }

    /** 合金 NBT 里的配方 ID；不是合金或没有 NBT 返回 null */
    @Nullable
    public static ResourceLocation recipeId(ItemStack stack) {
        if (!stack.is(ECMagmatic.MAGMATIC_ALLOY.get())) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_RECIPE, Tag.TAG_STRING)) {
            return null;
        }
        return ResourceLocation.tryParse(tag.getString(TAG_RECIPE));
    }

    /** 合金对应的配方（配方被数据包删了、或者 NBT 坏了返回 null） */
    @Nullable
    public static OreSmeltingRecipe recipe(Level level, ItemStack stack) {
        return OreSmeltingRecipe.byId(level.getRecipeManager(), recipeId(stack));
    }

    /**
     * 机器用的查找缓存：合金格的堆叠（引用、NBT）或配方表换了才重新解析 NBT 里的配方 ID，
     * 否则每 tick 都要新建一个 ResourceLocation 再查表。
     */
    public static final class Cache {
        @Nullable
        private RecipeManager seenRecipes;
        private ItemStack seenStack = ItemStack.EMPTY;
        @Nullable
        private CompoundTag seenTag;
        @Nullable
        private OreSmeltingRecipe result;

        @Nullable
        public OreSmeltingRecipe get(Level level, ItemStack stack) {
            RecipeManager recipes = level.getRecipeManager();
            if (recipes != seenRecipes || stack != seenStack || stack.getTag() != seenTag) {
                seenRecipes = recipes;
                seenStack = stack;
                seenTag = stack.getTag();
                result = recipe(level, stack);
            }
            return result;
        }
    }

    public static void setKnownRecipes(List<OreSmeltingRecipe> recipes) {
        knownRecipes = List.copyOf(recipes);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (level == null) {
            return;
        }
        OreSmeltingRecipe recipe = recipe(level, stack);
        if (recipe == null) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.magmatic_alloy.unknown").withStyle(ChatFormatting.RED));
            return;
        }
        ItemStack ore = firstReal(recipe.getOre().getItems());
        if (!ore.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.magmatic_alloy.ore", ore.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        ItemStack product = recipe.product();
        if (!product.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.magmatic_alloy.product",
                    OreSmeltingRecipe.PRODUCTS_PER_ALLOY, product.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }

    /** 第一个真实物品（空标签在客户端会变成一个叫「Empty Tag」的屏障，跳过） */
    public static ItemStack firstReal(ItemStack[] stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && !stack.is(Items.BARRIER)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> creativeVariants() {
        List<ItemStack> list = new ArrayList<>();
        for (OreSmeltingRecipe recipe : knownRecipes) {
            if (!firstReal(recipe.getOre().getItems()).isEmpty()) {
                list.add(create(recipe.getId(), 1));
            }
        }
        return list;
    }
}
