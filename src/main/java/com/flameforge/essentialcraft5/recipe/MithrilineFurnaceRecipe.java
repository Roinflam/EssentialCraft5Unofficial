package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
 * 米斯莱熔炉配方（物品转化）。对应原作 api/MithrilineFurnaceRecipe：一个原料（要求的数量）、产物、所需 ESPE。
 * 原作的 102 条写死在 RecipesCore.registerMithrilineFurnace，这里是数据包 JSON（由 tools/gen/mithriline_furnace_recipes.py 生成）：
 * <pre>
 * { "type": "essentialcraft5:mithriline_furnace",
 *   "ingredient": { "tag": "forge:ingots/iron" },
 *   "count": 8,                                  // 原料要几个，默认 1（原作 stackSize）
 *   "result": { "item": "minecraft:gold_ingot" },
 *   "espe": 64 }                                 // 做一次要多少 ESPE（原作 energy）
 * </pre>
 * {@link #matches} 只看输入格里是不是这种原料（原作 getRecipeByInput 同样只比原料），数量够不够由熔炉另外判断：
 * 数量不够时熔炉告诉玩家「原料不够」，而不是当作没有配方。
 */
public class MithrilineFurnaceRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final Ingredient input;
    private final int count;
    private final ItemStack result;
    private final double espe;

    public MithrilineFurnaceRecipe(ResourceLocation id, Ingredient input, int count, ItemStack result, double espe) {
        this.id = id;
        this.input = input;
        this.count = count;
        this.result = result;
        this.espe = espe;
    }

    public Ingredient getInput() {
        return input;
    }

    /** 原料要几个（原作 stackSize） */
    public int getCount() {
        return count;
    }

    /** 做一次要多少 ESPE（原作 energy） */
    public double getEspe() {
        return espe;
    }

    public ItemStack getResult() {
        return result;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return container.getContainerSize() > 0 && input.test(container.getItem(0));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, input);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ECMithriline.MITHRILINE_FURNACE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ECMithriline.MITHRILINE_FURNACE_TYPE.get();
    }

    /** 不进原版配方书 */
    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<MithrilineFurnaceRecipe> {

        @Override
        public MithrilineFurnaceRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient input = Ingredient.fromJson(GsonHelper.getNonNull(json, "ingredient"));
            int count = GsonHelper.getAsInt(json, "count", 1);
            if (count < 1 || count > 64) {
                throw new JsonParseException("count 要在 1~64 之间，实际 " + count);
            }
            double espe = GsonHelper.getAsDouble(json, "espe");
            // NaN、无穷、负数写进来，熔炉会永远做不完或一 tick 做无数次
            if (!Double.isFinite(espe) || espe < 0) {
                throw new JsonParseException("espe 必须是非负的有限数，实际 " + espe);
            }
            ItemStack result = RecipeJson.result(json);
            if (result.isEmpty()) {
                throw new JsonParseException("result 不能为空");
            }
            return new MithrilineFurnaceRecipe(id, input, count, result, espe);
        }

        @Override
        public MithrilineFurnaceRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            Ingredient input = Ingredient.fromNetwork(buf);
            int count = buf.readVarInt();
            ItemStack result = buf.readItem();
            double espe = buf.readDouble();
            return new MithrilineFurnaceRecipe(id, input, count, result, espe);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, MithrilineFurnaceRecipe recipe) {
            recipe.input.toNetwork(buf);
            buf.writeVarInt(recipe.count);
            buf.writeItem(recipe.result);
            buf.writeDouble(recipe.espe);
        }
    }
}
