package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.registry.ECMagmatic;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 矿石加工配方（岩浆冶炼炉、岩浆熔炉共用；以后的岩浆法杖也用它）。对应原作 api/OreSmeltingRecipe 那张写死的矿表。
 * <pre>
 * { "type": "essentialcraft5:ore_smelting",
 *   "ore":    { "tag": "forge:ores/iron" }            矿石（Ingredient；也可以直接写标签名字符串 "forge:ores/iron"）
 *   "raw":    { "tag": "forge:raw_materials/iron" }   可选：粗矿，倍率减半
 *   "output": { "item": "minecraft:iron_ingot" }      成品（{"item": …} 或 {"tag": …}；字符串按物品 id，"#" 开头按标签）
 *   "drop_amount": 1,                                  掉落数（原作 dropAmount）
 *   "color": "#E2C0AA",                                岩浆合金第二层的颜色（整数或 "#RRGGBB" / "0xRRGGBB"）
 *   "priority": 0 }                                    可选：同一个物品对上几条配方时大的优先，一样大按配方 ID
 * </pre>
 * 产量（{@link #yieldOf}）：矿石 → 掉落数 × 机器倍率 个岩浆合金（冶炼炉 ×2、岩浆熔炉 ×1）；粗矿倍率减半（D8），
 * 算出来不是整数时（岩浆熔炉的粗铁：1 × 1 ÷ 2）改成吃两个出一份。每个岩浆合金再炼成 2 个成品。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>原作按物品的<b>第一个</b>矿辞查表，矿辞排序不对就认不出来；这里看物品在不在配方的 ore / raw 里。</li>
 *   <li>原作按「ore 前缀换成 ingot」拼成品的矿辞，1.20 的标签不能这么拼，每条配方写明产物。
 *       产物写标签时，取标签里 minecraft 命名空间的物品，没有就取注册名最小的（结果可重复）。</li>
 *   <li>矿表只带原版和本模组的矿（D8），原作表里锡、铅、银等别的模组的矿由整合包自己写数据包。</li>
 *   <li>下界金矿单列一条（priority 1），和金矿、深层金矿分开：原版下界金矿是掉金粒的，整合包要调它的产量时不牵连金矿。</li>
 * </ul>
 * {@link #matches} 看 0 号格是矿石或粗矿；机器用 {@link Cache} 查，不每 tick 遍历配方表。
 */
public class OreSmeltingRecipe implements Recipe<Container> {

    /** 冶炼炉：矿石 → 掉落数 × 2 个合金（原作 TileMagmaticSmelter） */
    public static final int SMELTERY_MULTIPLIER = 2;
    /** 岩浆熔炉：矿石 → 掉落数 × 1 个合金（原作 TileFurnaceMagic） */
    public static final int FURNACE_MULTIPLIER = 1;
    /** 一个岩浆合金炼成几个成品（原作两台机器都写死 2） */
    public static final int PRODUCTS_PER_ALLOY = 2;
    /** 掉落数上限：冶炼炉一次出 掉落数 × 2 个合金，要装得进一格 */
    public static final int MAX_DROP_AMOUNT = 32;

    private final ResourceLocation id;
    private final Ingredient ore;
    private final Ingredient raw;
    /** 写成物品的产物；写成标签时为空 */
    private final ItemStack outputItem;
    @Nullable
    private final TagKey<Item> outputTag;
    private final int dropAmount;
    private final int color;
    private final int priority;

    public OreSmeltingRecipe(ResourceLocation id, Ingredient ore, Ingredient raw, ItemStack outputItem, @Nullable TagKey<Item> outputTag,
                             int dropAmount, int color, int priority) {
        this.id = id;
        this.ore = ore;
        this.raw = raw;
        this.outputItem = outputItem;
        this.outputTag = outputTag;
        this.dropAmount = dropAmount;
        this.color = color & 0xFFFFFF;
        this.priority = priority;
    }

    // ------------------------------------------------------------------
    // 读数
    // ------------------------------------------------------------------

    public Ingredient getOre() {
        return ore;
    }

    /** 粗矿；没有时是 Ingredient.EMPTY */
    public Ingredient getRaw() {
        return raw;
    }

    public boolean hasRaw() {
        return !raw.isEmpty();
    }

    public int getDropAmount() {
        return dropAmount;
    }

    /** 岩浆合金第二层的颜色（0xRRGGBB） */
    public int getColor() {
        return color;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isOre(ItemStack stack) {
        return !stack.isEmpty() && ore.test(stack);
    }

    public boolean isRaw(ItemStack stack) {
        return !stack.isEmpty() && hasRaw() && raw.test(stack);
    }

    /** 这个物品能不能进这条配方（矿石或粗矿） */
    public boolean accepts(ItemStack stack) {
        return isOre(stack) || isRaw(stack);
    }

    /**
     * 一个成品（数量 1）。产物写成标签时现查：优先 minecraft 命名空间的物品，没有就取注册名最小的；标签是空的返回空物品。
     */
    public ItemStack product() {
        if (!outputItem.isEmpty()) {
            return outputItem.copyWithCount(1);
        }
        if (outputTag == null) {
            return ItemStack.EMPTY;
        }
        Item best = null;
        ResourceLocation bestId = null;
        ITagManager<Item> tags = ForgeRegistries.ITEMS.tags();
        if (tags == null) {
            return ItemStack.EMPTY;
        }
        for (Item item : tags.getTag(outputTag)) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (item == Items.AIR || key == null) {
                continue;
            }
            if (best == null || better(key, bestId)) {
                best = item;
                bestId = key;
            }
        }
        return best == null ? ItemStack.EMPTY : new ItemStack(best);
    }

    private static boolean better(ResourceLocation candidate, ResourceLocation current) {
        boolean candidateVanilla = "minecraft".equals(candidate.getNamespace());
        boolean currentVanilla = "minecraft".equals(current.getNamespace());
        if (candidateVanilla != currentVanilla) {
            return candidateVanilla;
        }
        return candidate.compareTo(current) < 0;
    }

    /** 吃掉几个输入、出几个岩浆合金 */
    public record Yield(int consumed, int alloys) {
    }

    /**
     * 产量。矿石：掉落数 × 倍率；粗矿减半——能整除就吃 1 个出一半，不能整除就吃 2 个出一整份（D8：冶炼炉 1 粗矿 → 掉落数 × 1）。
     *
     * @param multiplier 机器倍率（{@link #SMELTERY_MULTIPLIER} / {@link #FURNACE_MULTIPLIER}）
     */
    public Yield yieldOf(boolean rawInput, int multiplier) {
        int full = Math.max(1, dropAmount) * Math.max(1, multiplier);
        if (!rawInput) {
            return new Yield(1, full);
        }
        return full % 2 == 0 ? new Yield(1, full / 2) : new Yield(2, full);
    }

    /** 这个输入对应的产量；不是这条配方的输入返回 null */
    @Nullable
    public Yield yieldFor(ItemStack input, int multiplier) {
        if (isOre(input)) {
            return yieldOf(false, multiplier);
        }
        if (isRaw(input)) {
            return yieldOf(true, multiplier);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 查找
    // ------------------------------------------------------------------

    /** 优先级高的在前，一样高按配方 ID（结果可重复） */
    public static final Comparator<OreSmeltingRecipe> ORDER =
            Comparator.comparingInt(OreSmeltingRecipe::getPriority).reversed().thenComparing(OreSmeltingRecipe::getId);

    /** 这个物品（矿石或粗矿）对应的配方，没有返回 null */
    @Nullable
    public static OreSmeltingRecipe find(RecipeManager recipes, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        OreSmeltingRecipe best = null;
        for (OreSmeltingRecipe recipe : recipes.getAllRecipesFor(ECMagmatic.ORE_SMELTING.get())) {
            if (recipe.accepts(stack) && (best == null || ORDER.compare(recipe, best) < 0)) {
                best = recipe;
            }
        }
        return best;
    }

    /** 按 ID 取（岩浆合金 NBT 里存的就是它），不是这个类型的返回 null */
    @Nullable
    public static OreSmeltingRecipe byId(RecipeManager recipes, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return recipes.byKey(id).filter(OreSmeltingRecipe.class::isInstance).map(OreSmeltingRecipe.class::cast).orElse(null);
    }

    /** 全部配方，按 {@link #ORDER} 排好 */
    public static List<OreSmeltingRecipe> all(RecipeManager recipes) {
        return recipes.getAllRecipesFor(ECMagmatic.ORE_SMELTING.get()).stream().sorted(ORDER).toList();
    }

    /**
     * 机器用的查找缓存：输入格的物品（引用、NBT、是否为空）或配方表换了才重新查，
     * 否则每 tick 都遍历一遍配方表测标签。
     */
    public static final class Cache {
        @Nullable
        private RecipeManager seenRecipes;
        private ItemStack seenStack = ItemStack.EMPTY;
        @Nullable
        private CompoundTag seenTag;
        private boolean seenEmpty = true;
        private Item seenItem = Items.AIR;
        @Nullable
        private OreSmeltingRecipe result;

        @Nullable
        public OreSmeltingRecipe get(Level level, ItemStack stack) {
            RecipeManager recipes = level.getRecipeManager();
            // 引用变了是换了一堆；NBT 引用变了是物品被改了；原地 shrink 到 0 引用不变，要单独看是否为空
            if (recipes != seenRecipes || stack != seenStack || stack.getTag() != seenTag || stack.isEmpty() != seenEmpty
                    || stack.getItem() != seenItem) {
                seenRecipes = recipes;
                seenStack = stack;
                seenTag = stack.getTag();
                seenEmpty = stack.isEmpty();
                seenItem = stack.getItem();
                result = find(recipes, stack);
            }
            return result;
        }
    }

    // ------------------------------------------------------------------
    // Recipe
    // ------------------------------------------------------------------

    @Override
    public boolean matches(Container container, Level level) {
        return container.getContainerSize() > 0 && accepts(container.getItem(0));
    }

    /** 一个成品（配方页、按产物查配方用；真正的产量由机器按 {@link #yieldOf} 算） */
    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return product();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return product();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return hasRaw() ? NonNullList.of(Ingredient.EMPTY, ore, raw) : NonNullList.of(Ingredient.EMPTY, ore);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ECMagmatic.ORE_SMELTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ECMagmatic.ORE_SMELTING.get();
    }

    /** 不进原版配方书（否则客户端日志会报不认识的配方类别） */
    @Override
    public boolean isSpecial() {
        return true;
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    public static class Serializer implements RecipeSerializer<OreSmeltingRecipe> {

        @Override
        public OreSmeltingRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient ore = ingredient(json, "ore");
            if (ore.isEmpty()) {
                throw new JsonParseException("ore 不能为空");
            }
            Ingredient raw = json.has("raw") ? ingredient(json, "raw") : Ingredient.EMPTY;
            ItemStack outputItem = ItemStack.EMPTY;
            TagKey<Item> outputTag = null;
            JsonElement output = json.get("output");
            if (output == null) {
                throw new JsonParseException("缺少 output");
            }
            if (output.isJsonPrimitive()) {
                String text = output.getAsString();
                if (text.startsWith("#")) {
                    outputTag = TagKey.create(Registries.ITEM, ResourceLocation.parse(text.substring(1)));
                } else {
                    outputItem = itemStack(ResourceLocation.parse(text));
                }
            } else {
                JsonObject object = GsonHelper.convertToJsonObject(output, "output");
                if (object.has("tag")) {
                    outputTag = TagKey.create(Registries.ITEM, ResourceLocation.parse(GsonHelper.getAsString(object, "tag")));
                } else {
                    outputItem = CraftingHelper.getItemStack(object, true).copyWithCount(1);
                }
            }
            int dropAmount = GsonHelper.getAsInt(json, "drop_amount", 1);
            // 冶炼炉一块矿出 掉落数 × 2 个合金，要装得进一格（64）；超过的话输出格永远放不下，机器会一直卡在「输出槽已满」
            if (dropAmount < 1 || dropAmount > MAX_DROP_AMOUNT) {
                throw new JsonParseException("drop_amount 要在 1～" + MAX_DROP_AMOUNT + " 之间，实际 " + dropAmount);
            }
            int color = json.has("color") ? color(json.get("color")) : 0xFFFFFF;
            return new OreSmeltingRecipe(id, ore, raw, outputItem, outputTag, dropAmount, color, GsonHelper.getAsInt(json, "priority", 0));
        }

        /** 字符串按标签名，对象 / 数组按 Ingredient */
        private static Ingredient ingredient(JsonObject json, String key) {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                throw new JsonParseException("缺少 " + key);
            }
            if (element.isJsonPrimitive()) {
                String text = element.getAsString();
                return Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse(text.startsWith("#") ? text.substring(1) : text)));
            }
            return Ingredient.fromJson(element, false);
        }

        private static ItemStack itemStack(ResourceLocation id) {
            if (!ForgeRegistries.ITEMS.containsKey(id)) {
                throw new JsonParseException("没有物品 " + id);
            }
            return new ItemStack(ForgeRegistries.ITEMS.getValue(id));
        }

        private static int color(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsInt();
            }
            String text = element.getAsString().trim().toLowerCase(Locale.ROOT);
            if (text.startsWith("#")) {
                text = text.substring(1);
            } else if (text.startsWith("0x")) {
                text = text.substring(2);
            }
            try {
                return Integer.parseUnsignedInt(text, 16);
            } catch (NumberFormatException e) {
                throw new JsonParseException("color 写法不对：" + element);
            }
        }

        @Override
        public OreSmeltingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            Ingredient ore = Ingredient.fromNetwork(buf);
            Ingredient raw = buf.readBoolean() ? Ingredient.fromNetwork(buf) : Ingredient.EMPTY;
            // 产物在服务端就解析成具体物品发过来（客户端的标签可能还没到）
            ItemStack product = buf.readItem();
            int dropAmount = buf.readVarInt();
            int color = buf.readInt();
            int priority = buf.readVarInt();
            return new OreSmeltingRecipe(id, ore, raw, product, null, dropAmount, color, priority);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, OreSmeltingRecipe recipe) {
            recipe.ore.toNetwork(buf);
            buf.writeBoolean(recipe.hasRaw());
            if (recipe.hasRaw()) {
                recipe.raw.toNetwork(buf);
            }
            buf.writeItem(recipe.product());
            buf.writeVarInt(recipe.dropAmount);
            buf.writeInt(recipe.color);
            buf.writeVarInt(recipe.priority);
        }
    }
}
