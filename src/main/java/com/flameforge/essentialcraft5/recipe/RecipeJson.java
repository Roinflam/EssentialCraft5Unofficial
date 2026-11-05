package com.flameforge.essentialcraft5.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;

/**
 * 机器配方 JSON 与网络读写的共用小工具。
 * <p>
 * 原作机器配方的原料是按位置对应的（魔术师台 5 格、辐射室 2 格），空位写 JSON null，表示「这一格必须空着」
 * （1.12 的 Ingredient.EMPTY.apply 只认空物品，1.20.1 的 Ingredient.EMPTY.test 同样如此）。
 */
public final class RecipeJson {

    private RecipeJson() {
    }

    /** 读定长的位置原料数组，null 或缺少的位置为 Ingredient.EMPTY */
    public static NonNullList<Ingredient> positionalIngredients(JsonObject json, String key, int size) {
        JsonArray array = GsonHelper.getAsJsonArray(json, key);
        if (array.size() > size) {
            throw new JsonParseException(key + " 最多 " + size + " 个，实际 " + array.size());
        }
        NonNullList<Ingredient> list = NonNullList.withSize(size, Ingredient.EMPTY);
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element != null && !element.isJsonNull()) {
                list.set(i, Ingredient.fromJson(element));
            }
        }
        return list;
    }

    public static JsonArray writePositional(NonNullList<Ingredient> ingredients) {
        JsonArray array = new JsonArray();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                array.add(com.google.gson.JsonNull.INSTANCE);
            } else {
                array.add(ingredient.toJson());
            }
        }
        return array;
    }

    public static ItemStack result(JsonObject json) {
        return CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "result"), true);
    }

    public static JsonObject writeResult(ItemStack stack) {
        JsonObject result = new JsonObject();
        result.addProperty("item", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        if (stack.getCount() != 1) {
            result.addProperty("count", stack.getCount());
        }
        if (stack.hasTag()) {
            result.addProperty("nbt", stack.getTag().toString());
        }
        return result;
    }

    public static void writeIngredients(FriendlyByteBuf buf, NonNullList<Ingredient> ingredients) {
        buf.writeVarInt(ingredients.size());
        ingredients.forEach(ingredient -> ingredient.toNetwork(buf));
    }

    public static NonNullList<Ingredient> readIngredients(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        NonNullList<Ingredient> list = NonNullList.withSize(size, Ingredient.EMPTY);
        for (int i = 0; i < size; i++) {
            list.set(i, Ingredient.fromNetwork(buf));
        }
        return list;
    }
}
