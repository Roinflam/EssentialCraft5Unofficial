package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.registry.ECRecipes;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * 辐射室配方。对应原作 RadiatingChamberRecipe：2 个按位置对应的原料、产物、所需 MRU（其实是进度 tick 数）、
 * 平衡值区间（机器里 MRU 的平衡值要落在 [下限, 上限] 内才能做）、耗能倍率（每 tick 消耗 = 机器基础耗能 × 倍率）。
 * <pre>
 * { "type": "essentialcraft5:radiating_chamber",
 *   "ingredients": [ 上, 下 ]（空位写 null）,
 *   "result": { "item": "..." },
 *   "mru": 1000, "balance_min": 0.0, "balance_max": 2.0, "cost_modifier": 1.0 }
 * </pre>
 * {@link #matches} 只比原料；平衡值由机器用 {@link #acceptsBalance} 另外判断（Recipe 接口拿不到机器的平衡值）。
 */
public class RadiatingChamberRecipe implements Recipe<Container> {

    public static final int INPUTS = 2;

    private final ResourceLocation id;
    private final NonNullList<Ingredient> inputs;
    private final ItemStack result;
    private final int mru;
    private final float balanceMin;
    private final float balanceMax;
    private final float costModifier;

    public RadiatingChamberRecipe(ResourceLocation id, NonNullList<Ingredient> inputs, ItemStack result, int mru,
                                  float balanceA, float balanceB, float costModifier) {
        this.id = id;
        this.inputs = inputs;
        this.result = result;
        this.mru = mru;
        // 原作构造时就取 min/max，两个平衡点写反也没关系
        this.balanceMin = Math.min(balanceA, balanceB);
        this.balanceMax = Math.max(balanceA, balanceB);
        this.costModifier = costModifier;
    }

    public int getMRU() {
        return mru;
    }

    public float getBalanceMin() {
        return balanceMin;
    }

    public float getBalanceMax() {
        return balanceMax;
    }

    public float getCostModifier() {
        return costModifier;
    }

    public ItemStack getResult() {
        return result;
    }

    public boolean acceptsBalance(float balance) {
        return balance >= balanceMin && balance <= balanceMax;
    }

    @Override
    public boolean matches(Container container, Level level) {
        if (container.getContainerSize() < INPUTS) {
            return false;
        }
        for (int i = 0; i < INPUTS; i++) {
            if (!inputs.get(i).test(container.getItem(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= INPUTS;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return inputs;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ECRecipes.RADIATING_CHAMBER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ECRecipes.RADIATING_CHAMBER.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<RadiatingChamberRecipe> {

        @Override
        public RadiatingChamberRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new RadiatingChamberRecipe(id, RecipeJson.positionalIngredients(json, "ingredients", INPUTS),
                    RecipeJson.result(json), GsonHelper.getAsInt(json, "mru"),
                    GsonHelper.getAsFloat(json, "balance_min", 0F), GsonHelper.getAsFloat(json, "balance_max", 2F),
                    GsonHelper.getAsFloat(json, "cost_modifier", 1F));
        }

        @Override
        public RadiatingChamberRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            NonNullList<Ingredient> inputs = RecipeJson.readIngredients(buf);
            ItemStack result = buf.readItem();
            int mru = buf.readVarInt();
            return new RadiatingChamberRecipe(id, inputs, result, mru, buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, RadiatingChamberRecipe recipe) {
            RecipeJson.writeIngredients(buf, recipe.inputs);
            buf.writeItem(recipe.result);
            buf.writeVarInt(recipe.mru);
            buf.writeFloat(recipe.balanceMin);
            buf.writeFloat(recipe.balanceMax);
            buf.writeFloat(recipe.costModifier);
        }
    }
}
