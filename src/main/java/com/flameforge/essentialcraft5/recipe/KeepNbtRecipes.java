package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.registry.ECRecipes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jetbrains.annotations.Nullable;

/**
 * 产物带上某件原料 NBT 的工作台配方：建筑魔杖在工作台里单放一根换模式（无序），普通过滤器加虚空板升级成高级过滤器（有序）。
 * 原作这两处的产物都不带 NBT，换一次模式魔杖里的 MRU、两个点、记下的方块全丢，升级过滤器样本全丢。
 * <pre>
 * { "type": "essentialcraft5:crafting_shapeless_keep_nbt",      有序的写 crafting_shaped_keep_nbt
 *   ……原版 crafting_shapeless / crafting_shaped 的全部字段（ingredients 或 pattern + key、result、group、category）……
 *   "nbt_source": { "item": "essentialcraft5:whitelist_filter" }  原料写法（Ingredient），NBT 从格子里第一件符合它的物品上取
 * }
 * </pre>
 * {@link #carryNbt}：格子里按格号找第一件符合 {@code nbt_source} 的物品，它有 tag 就整份复制给产物；配方产物自己写了 {@code nbt} 的再合并到上面（同名键以配方为准，
 * 但物品新建时自带的默认值不算，比如有耐久的产物自带的 {@code Damage:0} 不会把原料的耐久清零）；数量照配方。没有符合的、或者那件没有 tag，产物就是配方原样。
 * <p>
 * 两个类分别继承原版的 {@link ShapedRecipe} / {@link ShapelessRecipe}，配方类型仍是 {@code minecraft:crafting}、{@code isSpecial()} 仍是 false，
 * 所以配方书、研究手册的配方图（按 {@code RecipeType.CRAFTING} 认、按 {@code IShapedRecipe} 分有序无序）、魔力合成器的反查都照常认它们；
 * 合成器真合成时调的也是 {@code assemble}，NBT 一样带过去。JSON 和网络的读写都交给原版序列化器，后面只多读写一个 {@code nbt_source}。
 */
public final class KeepNbtRecipes {

    public static final String NBT_SOURCE = "nbt_source";

    private KeepNbtRecipes() {
    }

    /**
     * 把格子里第一件符合 {@code source} 的物品的 tag 复制到 {@code result} 上（就地改并返回它）。
     * {@code result} 已有的 tag（配方产物自己写的 nbt）合并到复制来的那份上面，同名键以 {@code result} 为准；物品自带的默认值除外（见 {@link #ownTag}）。
     */
    public static ItemStack carryNbt(ItemStack result, CraftingContainer container, Ingredient source) {
        if (result.isEmpty()) {
            return result;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty() || !source.test(stack)) {
                continue;
            }
            CompoundTag tag = stack.getTag();
            if (tag != null && !tag.isEmpty()) {
                CompoundTag merged = tag.copy();
                CompoundTag own = ownTag(result, merged);
                if (own != null) {
                    merged.merge(own);
                }
                result.setTag(merged);
            }
            break;
        }
        return result;
    }

    /**
     * 产物上要盖到复制来的 tag 上面的那部分：产物的 tag 去掉「这种物品新建时就自带、值也一样、复制来的 tag 里也有」的键。
     * 有耐久的物品一 {@code new ItemStack} 就带 {@code Damage:0}（1.20.1 的构造器和 {@code setTag} 都会补），
     * 不去掉的话原料的耐久每次都被它清零；配方 JSON 里写了和默认不一样的值照样以配方为准。
     */
    @Nullable
    private static CompoundTag ownTag(ItemStack result, CompoundTag copied) {
        CompoundTag own = result.getTag();
        if (own == null || own.isEmpty()) {
            return null;
        }
        CompoundTag defaults = new ItemStack(result.getItem()).getTag();
        if (defaults == null || defaults.isEmpty()) {
            return own;
        }
        CompoundTag explicit = own.copy();
        for (String key : defaults.getAllKeys()) {
            Tag value = explicit.get(key);
            if (value != null && value.equals(defaults.get(key)) && copied.contains(key)) {
                explicit.remove(key);
            }
        }
        return explicit;
    }

    private static Ingredient readSource(JsonObject json) {
        if (!json.has(NBT_SOURCE)) {
            throw new JsonParseException("保留 NBT 的配方缺少 \"" + NBT_SOURCE + "\"");
        }
        return Ingredient.fromJson(json.get(NBT_SOURCE), false);
    }

    // ---------------- 无序 ----------------

    public static class ShapelessKeepNbt extends ShapelessRecipe {

        private final Ingredient nbtSource;

        public ShapelessKeepNbt(ShapelessRecipe base, Ingredient nbtSource) {
            super(base.getId(), base.getGroup(), base.category(), base.getResultItem(RegistryAccess.EMPTY), base.getIngredients());
            this.nbtSource = nbtSource;
        }

        public Ingredient nbtSource() {
            return nbtSource;
        }

        @Override
        public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
            return carryNbt(super.assemble(container, access), container, nbtSource);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return ECRecipes.CRAFTING_SHAPELESS_KEEP_NBT.get();
        }
    }

    public static class ShapelessSerializer implements RecipeSerializer<ShapelessKeepNbt> {

        @Override
        public ShapelessKeepNbt fromJson(ResourceLocation id, JsonObject json) {
            return new ShapelessKeepNbt(RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json), readSource(json));
        }

        @Override
        public ShapelessKeepNbt fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            ShapelessRecipe base = RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buf);
            return new ShapelessKeepNbt(base, Ingredient.fromNetwork(buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ShapelessKeepNbt recipe) {
            RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buf, recipe);
            recipe.nbtSource.toNetwork(buf);
        }
    }

    // ---------------- 有序 ----------------

    public static class ShapedKeepNbt extends ShapedRecipe {

        private final Ingredient nbtSource;

        public ShapedKeepNbt(ShapedRecipe base, Ingredient nbtSource) {
            super(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(), base.getIngredients(),
                    base.getResultItem(RegistryAccess.EMPTY), base.showNotification());
            this.nbtSource = nbtSource;
        }

        public Ingredient nbtSource() {
            return nbtSource;
        }

        @Override
        public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
            return carryNbt(super.assemble(container, access), container, nbtSource);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return ECRecipes.CRAFTING_SHAPED_KEEP_NBT.get();
        }
    }

    public static class ShapedSerializer implements RecipeSerializer<ShapedKeepNbt> {

        @Override
        public ShapedKeepNbt fromJson(ResourceLocation id, JsonObject json) {
            return new ShapedKeepNbt(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json), readSource(json));
        }

        @Override
        public ShapedKeepNbt fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            ShapedRecipe base = RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf);
            return new ShapedKeepNbt(base, Ingredient.fromNetwork(buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ShapedKeepNbt recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
            recipe.nbtSource.toNetwork(buf);
        }
    }
}
