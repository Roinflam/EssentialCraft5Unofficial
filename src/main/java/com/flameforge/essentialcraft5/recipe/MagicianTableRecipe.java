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
import org.jetbrains.annotations.Nullable;

/**
 * 魔术师台配方。对应原作 MagicianTableRecipe：5 个按位置对应的原料（中心 + 四角，空位必须空着）、产物、所需 MRU。
 * <p>
 * 「所需 MRU」在原作里其实是进度：每 tick 进度 +速度系数、同时消耗 MRU，进度满了出产物。
 * 配方原来写死在 RecipesCore 里，这里改成数据包 JSON（type = essentialcraft5:magician_table），
 * 由 datagen 生成，整合包作者可以用数据包或 KubeJS 增删。
 * <pre>
 * { "type": "essentialcraft5:magician_table",
 *   "ingredients": [ 中心, 左上, 右上, 左下, 右下 ]（空位写 null）,
 *   "result": { "item": "...", "count": 1 },
 *   "mru": 1000 }
 * </pre>
 * 容器第 i 格对应第 i 个原料（机器把输入槽按这个顺序包成 Container 传进来）。
 * <p>
 * 第十三批（batch13.md §3.3.5）起同一个配方类型下还有同调的升阶、转移配方（{@link AttunementAscensionRecipe}、{@link AttunementTransferRecipe}，
 * 继承这个类、用自己的序列化器）：产物按输入算（{@link #assemble}），每格可以要好几个（{@link #countAt}），转移配方的旧件做完留在原格（{@link #remainderAt}）。
 * 工作台出产物、扣料都走这三个方法；普通配方的产物是固定的、每格扣 1，行为和以前一样。
 */
public class MagicianTableRecipe implements Recipe<Container> {

    public static final int INPUTS = 5;

    private final ResourceLocation id;
    private final NonNullList<Ingredient> inputs;
    private final ItemStack result;
    private final int mru;

    public MagicianTableRecipe(ResourceLocation id, NonNullList<Ingredient> inputs, ItemStack result, int mru) {
        this.id = id;
        this.inputs = inputs;
        this.result = result;
        this.mru = mru;
    }

    public int getMRU() {
        return mru;
    }

    public ItemStack getResult() {
        return result;
    }

    /** 第 index 格（0 中心、1~4 四角）每做一份扣几个。普通配方每格 1（空格不扣） */
    public int countAt(int index) {
        return 1;
    }

    /**
     * 做完一份以后第 index 格要换成的东西；返回 null 表示照 {@link #countAt} 扣。普通配方都返回 null。
     *
     * @param inSlot 这一格现在的物品（别改它）
     */
    @Nullable
    public ItemStack remainderAt(int index, ItemStack inSlot) {
        return null;
    }

    /** 工作台刚出了一份产物（已经放进输出格）时调；inputs 是扣料之前的五格。普通配方什么都不做 */
    public void crafted(Container inputs, ItemStack result) {
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
        return ECRecipes.MAGICIAN_TABLE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ECRecipes.MAGICIAN_TABLE.get();
    }

    @Override
    public boolean isSpecial() {
        // 不进原版配方书
        return true;
    }

    public static class Serializer implements RecipeSerializer<MagicianTableRecipe> {

        @Override
        public MagicianTableRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new MagicianTableRecipe(id, RecipeJson.positionalIngredients(json, "ingredients", INPUTS),
                    RecipeJson.result(json), GsonHelper.getAsInt(json, "mru"));
        }

        @Override
        public MagicianTableRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            NonNullList<Ingredient> inputs = RecipeJson.readIngredients(buf);
            return new MagicianTableRecipe(id, inputs, buf.readItem(), buf.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, MagicianTableRecipe recipe) {
            RecipeJson.writeIngredients(buf, recipe.inputs);
            buf.writeItem(recipe.result);
            buf.writeVarInt(recipe.mru);
        }
    }
}
