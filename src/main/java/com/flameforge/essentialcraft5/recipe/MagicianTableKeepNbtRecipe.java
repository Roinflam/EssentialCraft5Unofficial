package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.registry.ECRecipes;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力工作台「中心那件照留、改几个 NBT 键」的配方：同一个配方类型 {@code essentialcraft5:magician_table}、序列化器
 * {@code essentialcraft5:magician_table_keep_nbt}。产物 = 中心那件（数量 1、整份 NBT 和 MRU 照留）合并上 {@code set_nbt}；
 * 普通的魔力工作台配方产物是固定的一份新物品，带不走 NBT。创世神之杖升格（二阶 → 三阶，写 {@code builder_tier: 3}）用它。
 * <pre>
 * { "type": "essentialcraft5:magician_table_keep_nbt",
 *   "ingredients": [ 中心, 左上, 右上, 左下, 右下 ]（空位写 null）,
 *   "set_nbt": { "builder_tier": 3 },              合并进中心那件的 NBT（JSON 对象或 SNBT 字符串）
 *   "result": { "item": … },                       只给研究手册、JEI 显示用（显示时同样合并 set_nbt）
 *   "mru": 100000 }
 * </pre>
 * 中心那件已经带着 {@code set_nbt} 里全部的键和值时不匹配（升过一次的不会再吃一份材料），漏斗也不会把它塞进中心格
 * （{@link PositionalInsertRule.SlotTest}：只按原料判断的话，升过的魔杖照样被送进中心，配方不开工、也不扣它，工作台就卡住了）。
 * 数字按数值比（{@code 3b}、{@code 3L} 都算 3）。{@code set_nbt} 不能为空。
 */
public class MagicianTableKeepNbtRecipe extends MagicianTableRecipe implements PositionalInsertRule.SlotTest {

    private final CompoundTag setNbt;

    public MagicianTableKeepNbtRecipe(ResourceLocation id, NonNullList<Ingredient> inputs, ItemStack display, int mru, CompoundTag setNbt) {
        super(id, inputs, applied(display, setNbt), mru);
        this.setNbt = setNbt.copy();
    }

    /** 合并进中心那件的键（副本） */
    public CompoundTag setNbt() {
        return setNbt.copy();
    }

    /** stack 的副本（数量 1）合并上 tag */
    public static ItemStack applied(ItemStack stack, CompoundTag tag) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = stack.copyWithCount(1);
        out.getOrCreateTag().merge(tag.copy());
        return out;
    }

    /** 中心那件是不是已经带着全部要设的键和值 */
    public boolean alreadyApplied(ItemStack center) {
        CompoundTag tag = center.getTag();
        if (tag == null || setNbt.isEmpty()) {
            return false;
        }
        for (String key : setNbt.getAllKeys()) {
            if (!sameValue(setNbt.get(key), tag.get(key))) {
                return false;
            }
        }
        return true;
    }

    /** 两个值相同：都是数字时按数值比（不管 byte / int / long），其余按 equals */
    private static boolean sameValue(@Nullable Tag want, @Nullable Tag have) {
        if (want == null || have == null) {
            return false;
        }
        if (want instanceof NumericTag a && have instanceof NumericTag b) {
            return a.getAsNumber().doubleValue() == b.getAsNumber().doubleValue();
        }
        return want.equals(have);
    }

    @Override
    public boolean acceptsAt(int index, ItemStack stack) {
        if (index < 0 || index >= getIngredients().size() || !getIngredients().get(index).test(stack)) {
            return false;
        }
        return index != 0 || stack.isEmpty() || !alreadyApplied(stack);
    }

    @Override
    public boolean matches(Container container, Level level) {
        return super.matches(container, level) && !container.getItem(0).isEmpty() && !alreadyApplied(container.getItem(0));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        ItemStack center = container.getItem(0);
        if (center.isEmpty() || alreadyApplied(center)) {
            return ItemStack.EMPTY;
        }
        return applied(center, setNbt);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ECRecipes.MAGICIAN_TABLE_KEEP_NBT_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<MagicianTableKeepNbtRecipe> {

        @Override
        public MagicianTableKeepNbtRecipe fromJson(ResourceLocation id, JsonObject json) {
            CompoundTag tag = CraftingHelper.getNBT(json.get("set_nbt"));
            if (tag.isEmpty()) {
                throw new JsonSyntaxException("set_nbt 不能为空：" + id);
            }
            return new MagicianTableKeepNbtRecipe(id, RecipeJson.positionalIngredients(json, "ingredients", INPUTS),
                    RecipeJson.result(json), GsonHelper.getAsInt(json, "mru"), tag);
        }

        @Override
        public MagicianTableKeepNbtRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            NonNullList<Ingredient> inputs = RecipeJson.readIngredients(buf);
            ItemStack display = buf.readItem();
            int mru = buf.readVarInt();
            CompoundTag tag = buf.readNbt();
            return new MagicianTableKeepNbtRecipe(id, inputs, display, mru, tag == null ? new CompoundTag() : tag);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, MagicianTableKeepNbtRecipe recipe) {
            RecipeJson.writeIngredients(buf, recipe.getIngredients());
            buf.writeItem(recipe.getResult());
            buf.writeVarInt(recipe.getMRU());
            buf.writeNbt(recipe.setNbt);
        }
    }
}
