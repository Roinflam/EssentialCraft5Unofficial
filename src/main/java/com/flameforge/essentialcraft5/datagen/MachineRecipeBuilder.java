package com.flameforge.essentialcraft5.datagen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.recipe.RadiatingChamberRecipe;
import com.flameforge.essentialcraft5.recipe.RecipeJson;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 机器配方的 datagen 构建器。原作 RecipesCore 里写死的机器配方翻译成这里的调用，由 ECRecipeProvider 生成 JSON。
 * <pre>
 * MachineRecipeBuilder.magicianTable(new ItemStack(X), 1000)
 *         .input(0, Ingredient.of(中心)).input(1, Ingredient.of(左上))…
 *         .save(out);
 * </pre>
 * 文件名默认取产物的注册名，同一产物有多条配方时用 {@link #save(Consumer, String)} 指定名字。
 */
public final class MachineRecipeBuilder {

    private final String kind;
    private final RecipeSerializer<?> serializer;
    private final NonNullList<Ingredient> inputs;
    private final ItemStack result;
    private final int mru;
    private float balanceMin = 0F;
    private float balanceMax = 2F;
    private float costModifier = 1F;

    private MachineRecipeBuilder(String kind, RecipeSerializer<?> serializer, int size, ItemStack result, int mru) {
        this.kind = kind;
        this.serializer = serializer;
        this.inputs = NonNullList.withSize(size, Ingredient.EMPTY);
        this.result = result;
        this.mru = mru;
    }

    /** 魔术师台：原料位置 0 中心、1 左上、2 右上、3 左下、4 右下 */
    public static MachineRecipeBuilder magicianTable(ItemStack result, int mru) {
        return new MachineRecipeBuilder("magician_table", ECRecipes.MAGICIAN_TABLE_SERIALIZER.get(),
                MagicianTableRecipe.INPUTS, result, mru);
    }

    /** 辐射室：原料位置 0 上、1 下 */
    public static MachineRecipeBuilder radiatingChamber(ItemStack result, int mru) {
        return new MachineRecipeBuilder("radiating_chamber", ECRecipes.RADIATING_CHAMBER_SERIALIZER.get(),
                RadiatingChamberRecipe.INPUTS, result, mru);
    }

    public MachineRecipeBuilder input(int slot, Ingredient ingredient) {
        inputs.set(slot, ingredient);
        return this;
    }

    /** 辐射室的平衡值区间（原作两个平衡点，顺序无所谓） */
    public MachineRecipeBuilder balance(float a, float b) {
        balanceMin = Math.min(a, b);
        balanceMax = Math.max(a, b);
        return this;
    }

    public MachineRecipeBuilder costModifier(float modifier) {
        costModifier = modifier;
        return this;
    }

    public void save(Consumer<FinishedRecipe> out) {
        save(out, ForgeRegistries.ITEMS.getKey(result.getItem()).getPath());
    }

    public void save(Consumer<FinishedRecipe> out, String name) {
        ResourceLocation id = EssentialCraft5.id(kind + "/" + name);
        out.accept(new FinishedRecipe() {
            @Override
            public void serializeRecipeData(JsonObject json) {
                json.add("ingredients", RecipeJson.writePositional(inputs));
                json.add("result", RecipeJson.writeResult(result));
                json.addProperty("mru", mru);
                if (kind.equals("radiating_chamber")) {
                    json.addProperty("balance_min", balanceMin);
                    json.addProperty("balance_max", balanceMax);
                    json.addProperty("cost_modifier", costModifier);
                }
            }

            @Override
            public ResourceLocation getId() {
                return id;
            }

            @Override
            public RecipeSerializer<?> getType() {
                return serializer;
            }

            @Override
            public @Nullable JsonObject serializeAdvancement() {
                return null;
            }

            @Override
            public @Nullable ResourceLocation getAdvancementId() {
                return null;
            }
        });
    }
}
