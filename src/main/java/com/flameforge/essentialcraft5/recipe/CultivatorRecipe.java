package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MagicalCultivatorBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECCultivator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 魔力栽培器的配方（类型 {@code essentialcraft5:cultivator}，batch12.md §4.2.2）。种子 / 树苗放进机器当样本、不消耗，
 * 每轮按株数各掷一遍产物。
 * <pre>
 * { "type": "essentialcraft5:cultivator",
 *   "kind": "crop",                                 crop / tree，下面省略的字段按它取默认（作物 2400 刻 / 800 MRU / 16 株，树 6000 / 6000 / 4）
 *   "input": {"item": "minecraft:wheat_seeds"},     样本（Ingredient）
 *   "ticks": 2400,                                  一轮多少刻（机器再乘配置的倍率）
 *   "mru_per_plant": 800,                           每株一轮多少 MRU（边做边扣）
 *   "max_plants": 16,                               株数上限（株数 = min(样本数量, 这个)）
 *   "needs_water": true,                            要不要水（默认要）
 *   "bonemeal": true,                               能不能用骨粉催长（默认能）
 *   "outputs": [ {"item": "minecraft:wheat", "min": 1, "max": 1},
 *                {"item": "minecraft:wheat_seeds", "chance": 0.25} ] }   每株：先按 chance（默认 1）判出不出，再在 [min, max]（默认 1）里取数量
 * </pre>
 * 配方 JSON 由 {@code tools/gen/cultivator_recipes.py} 生成到 {@code data/essentialcraft5/recipes/cultivator/}。
 * <p>
 * <b>通用作物路线</b>（{@link #generic}）：没有显式配方的种子，方块是 {@link CropBlock} 的子类、并且自己说能种在普通耕地上（{@link #growsOnFarmland}），
 * 就按成熟状态的方块掉落每株掷一遍，减去 1 个样本（相当于补种），每株最多 {@code generic_max_items_per_plant} 件。
 * 这种配方不进配方管理器，开工时临时造。要特殊土壤、特殊底座的作物不收，免得栽培器绕开它们自己的门槛。
 */
public class CultivatorRecipe implements Recipe<Container> {

    /** 输出格数（机器 4~12 号槽） */
    public static final int OUTPUT_SLOTS = 9;
    /** 通用路线开工时按每个空输出格能放这么多件估最坏情况（掉落是什么事先不知道） */
    public static final int GENERIC_ITEMS_PER_SLOT = 64;

    /** 配方的种类：省略的字段取各自的默认值 */
    public enum Kind {
        CROP(2400, 800, 16),
        TREE(6000, 6000, 4);

        public final int ticks;
        public final int mruPerPlant;
        public final int maxPlants;

        Kind(int ticks, int mruPerPlant, int maxPlants) {
            this.ticks = ticks;
            this.mruPerPlant = mruPerPlant;
            this.maxPlants = maxPlants;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Kind byKey(String key) {
            for (Kind kind : values()) {
                if (kind.key().equals(key)) {
                    return kind;
                }
            }
            throw new JsonParseException("kind 只能是 crop 或 tree，实际 " + key);
        }
    }

    /**
     * 一种产物（每株）。
     *
     * @param chance 出这一种的几率（0~1，1 = 必出）；先判几率，再在 [min, max] 里取数量
     */
    public record Output(Item item, int min, int max, float chance) {

        /** 每株最多几件（最坏情况） */
        public int worst() {
            return max;
        }

        public ItemStack display() {
            return new ItemStack(item, Math.max(1, Math.min(max, new ItemStack(item).getMaxStackSize())));
        }
    }

    private final ResourceLocation id;
    private final Kind kind;
    private final Ingredient input;
    private final int ticks;
    private final int mruPerPlant;
    private final int maxPlants;
    private final boolean needsWater;
    private final boolean bonemeal;
    private final List<Output> outputs;
    /** 通用路线：掷哪种作物的成熟掉落；显式配方为 null */
    @Nullable
    private final CropBlock crop;
    /** 通用路线每株最多几件；显式配方不用 */
    private final int maxItemsPerPlant;

    public CultivatorRecipe(ResourceLocation id, Kind kind, Ingredient input, int ticks, int mruPerPlant, int maxPlants,
                            boolean needsWater, boolean bonemeal, List<Output> outputs) {
        this(id, kind, input, ticks, mruPerPlant, maxPlants, needsWater, bonemeal, outputs, null, 0);
    }

    private CultivatorRecipe(ResourceLocation id, Kind kind, Ingredient input, int ticks, int mruPerPlant, int maxPlants,
                             boolean needsWater, boolean bonemeal, List<Output> outputs, @Nullable CropBlock crop, int maxItemsPerPlant) {
        this.id = id;
        this.kind = kind;
        this.input = input;
        this.ticks = ticks;
        this.mruPerPlant = mruPerPlant;
        this.maxPlants = maxPlants;
        this.needsWater = needsWater;
        this.bonemeal = bonemeal;
        this.outputs = List.copyOf(outputs);
        this.crop = crop;
        this.maxItemsPerPlant = maxItemsPerPlant;
    }

    // ------------------------------------------------------------------
    // 读数
    // ------------------------------------------------------------------

    public Kind kind() {
        return kind;
    }

    public Ingredient input() {
        return input;
    }

    /** 配方写的一轮时长（还没乘机器配置的倍率） */
    public int ticks() {
        return ticks;
    }

    public int mruPerPlant() {
        return mruPerPlant;
    }

    public int maxPlants() {
        return maxPlants;
    }

    public boolean needsWater() {
        return needsWater;
    }

    public boolean bonemeal() {
        return bonemeal;
    }

    /** 显式配方的产物表；通用路线是空表（产物来自掉落） */
    public List<Output> outputs() {
        return outputs;
    }

    public boolean isGeneric() {
        return crop != null;
    }

    /** 通用路线掷哪种作物；显式配方为 null */
    @Nullable
    public CropBlock genericCrop() {
        return crop;
    }

    public int maxItemsPerPlant() {
        return maxItemsPerPlant;
    }

    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && input.test(stack);
    }

    // ------------------------------------------------------------------
    // 通用作物路线
    // ------------------------------------------------------------------

    /**
     * 这种方块能不能种在普通耕地上：只问作物自己的 {@code mayPlaceOn(耕地)}（访问转换器放开了 BushBlock 上的声明，按 BushBlock 调用会分派到子类）。
     * 不看光照、不看世界里的方块；要特殊土壤、特殊底座的作物（覆盖成只认自己方块的那种）在这里就是 false。
     */
    public static boolean growsOnFarmland(Block block) {
        if (!(block instanceof BushBlock bush)) {
            return false;
        }
        try {
            return bush.mayPlaceOn(Blocks.FARMLAND.defaultBlockState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            // 别的模组的 mayPlaceOn 可能要读世界（空世界里什么都没有），出错就当种不了
            return false;
        }
    }

    /** 这种方块能不能走通用路线：是 {@link CropBlock} 的子类、并且自己说能种在普通耕地上（定稿 D5） */
    public static boolean genericEligible(Block block) {
        return block instanceof CropBlock && growsOnFarmland(block);
    }

    /**
     * 这个物品能不能走通用路线（不看黑名单、总开关、有没有显式配方，这些由机器判）：
     * 放下来是 {@link CropBlock} 的子类、并且能种在普通耕地上。返回作物方块，不能走时返回 null。
     */
    @Nullable
    public static CropBlock genericCropOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        return genericEligible(block) ? (CropBlock) block : null;
    }

    /** 通用路线的配方，数值取当前配置（machines.magical_cultivator.generic_*） */
    public static CultivatorRecipe generic(CropBlock crop, Item input) {
        return generic(crop, input, ECConfig.get(MagicalCultivatorBlockEntity.Config.genericTicks),
                ECConfig.get(MagicalCultivatorBlockEntity.Config.genericMruPerPlant),
                ECConfig.get(MagicalCultivatorBlockEntity.Config.genericMaxPlants),
                ECConfig.get(MagicalCultivatorBlockEntity.Config.genericMaxItemsPerPlant));
    }

    /**
     * 通用路线的配方（不管这个物品有没有显式配方，GameTest 直接拿原版小麦调）：作物的时长、每株 MRU、株数上限、每株最多几件由调用方给，
     * 要水、能用骨粉。不进配方管理器，id 只用来认它（{@code essentialcraft5:cultivator_generic/<命名空间>/<路径>}）。
     */
    public static CultivatorRecipe generic(CropBlock crop, Item input, int ticks, int mruPerPlant, int maxPlants, int maxItemsPerPlant) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(input);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID,
                "cultivator_generic/" + (key == null ? "unknown" : key.getNamespace() + "/" + key.getPath()));
        return new CultivatorRecipe(id, Kind.CROP, Ingredient.of(input), Math.max(1, ticks), Math.max(0, mruPerPlant),
                Math.max(1, maxPlants), true, true, List.of(), crop, Math.max(1, maxItemsPerPlant));
    }

    /**
     * 通用路线一株的产物：成熟状态（{@code getStateForAge(getMaxAge())}）的方块掉落，参数只给位置（机器中心）、空手（TOOL = 空）、方块状态，
     * 不给实体（谁也没在挖）；减去 1 个样本（补种），合计最多 {@link #maxItemsPerPlant} 件、多的丢掉。掷表抛的异常照原样抛给调用的人。
     */
    public List<ItemStack> rollGenericPlant(ServerLevel level, Vec3 origin, Item sample) {
        if (crop == null) {
            return List.of();
        }
        BlockState mature = crop.getStateForAge(crop.getMaxAge());
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack drop : mature.getDrops(params)) {
            if (!drop.isEmpty()) {
                drops.add(drop.copy());
            }
        }
        for (ItemStack drop : drops) {
            if (drop.is(sample)) {
                drop.shrink(1);
                break;
            }
        }
        List<ItemStack> out = new ArrayList<>();
        int room = maxItemsPerPlant;
        for (ItemStack drop : drops) {
            if (room <= 0) {
                break;
            }
            if (drop.isEmpty()) {
                continue;
            }
            int take = Math.min(room, drop.getCount());
            out.add(drop.copyWithCount(take));
            room -= take;
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 产物
    // ------------------------------------------------------------------

    /** 显式配方：plants 株各掷一遍（几率先判，再在 [min, max] 里取数量），同种合起来、按最大堆叠拆好 */
    public List<ItemStack> rollExplicit(RandomSource random, int plants) {
        Map<Item, Integer> totals = new LinkedHashMap<>();
        for (int plant = 0; plant < plants; plant++) {
            for (Output output : outputs) {
                if (output.chance() < 1F && random.nextFloat() >= output.chance()) {
                    continue;
                }
                int count = output.min() + (output.max() > output.min() ? random.nextInt(output.max() - output.min() + 1) : 0);
                if (count > 0) {
                    totals.merge(output.item(), count, Integer::sum);
                }
            }
        }
        List<ItemStack> out = new ArrayList<>();
        totals.forEach((item, count) -> split(out, new ItemStack(item), count));
        return out;
    }

    /** 显式配方的最坏情况：每种产物取 max × 株数，按最大堆叠拆好（开工前、出货前拿它模拟塞进输出格） */
    public List<ItemStack> worstCase(int plants) {
        List<ItemStack> out = new ArrayList<>();
        for (Output output : outputs) {
            split(out, new ItemStack(output.item()), output.worst() * plants);
        }
        return out;
    }

    /** 通用路线开工时要几个空输出格（每株按 {@link #maxItemsPerPlant} 件算，每格按 {@value #GENERIC_ITEMS_PER_SLOT} 件） */
    public int genericSlotsNeeded(int plants) {
        long items = (long) maxItemsPerPlant * plants;
        return (int) Math.min(OUTPUT_SLOTS + 1, (items + GENERIC_ITEMS_PER_SLOT - 1) / GENERIC_ITEMS_PER_SLOT);
    }

    /** 把 count 个同种东西按最大堆叠拆成几堆追加到 out */
    public static void split(List<ItemStack> out, ItemStack kind, int count) {
        int max = Math.max(1, kind.getMaxStackSize());
        while (count > 0) {
            int take = Math.min(max, count);
            out.add(kind.copyWithCount(take));
            count -= take;
        }
    }

    // ------------------------------------------------------------------
    // 查找
    // ------------------------------------------------------------------

    /** 按配方 id 排（同一样东西对上几条时取 id 最小的，结果可重复） */
    public static final Comparator<CultivatorRecipe> ORDER = Comparator.comparing(CultivatorRecipe::getId);

    /** 这个样本的显式配方；没有返回 null */
    @Nullable
    public static CultivatorRecipe find(RecipeManager recipes, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CultivatorRecipe best = null;
        for (CultivatorRecipe recipe : recipes.getAllRecipesFor(ECCultivator.CULTIVATOR.get())) {
            if (recipe.accepts(stack) && (best == null || ORDER.compare(recipe, best) < 0)) {
                best = recipe;
            }
        }
        return best;
    }

    /** 全部显式配方，按 id 排好 */
    public static List<CultivatorRecipe> all(RecipeManager recipes) {
        return recipes.getAllRecipesFor(ECCultivator.CULTIVATOR.get()).stream().sorted(ORDER).toList();
    }

    /** 机器用的查找缓存：样本的物品种类或配方表换了才重新查，不每刻遍历配方表 */
    public static final class Cache {
        @Nullable
        private RecipeManager seenRecipes;
        private Item seenItem = Items.AIR;
        @Nullable
        private CompoundTag seenTag;
        @Nullable
        private CultivatorRecipe result;

        @Nullable
        public CultivatorRecipe get(Level level, ItemStack stack) {
            RecipeManager recipes = level.getRecipeManager();
            Item item = stack.isEmpty() ? Items.AIR : stack.getItem();
            if (recipes != seenRecipes || item != seenItem || stack.getTag() != seenTag) {
                seenRecipes = recipes;
                seenItem = item;
                seenTag = stack.getTag();
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

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return getResultItem(registryAccess);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    /** 第一种产物（按产物查配方、配方页兜底用；真正的产量由机器每轮掷） */
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).display();
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
        return ECCultivator.CULTIVATOR_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ECCultivator.CULTIVATOR.get();
    }

    /** 不进原版配方书 */
    @Override
    public boolean isSpecial() {
        return true;
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    public static class Serializer implements RecipeSerializer<CultivatorRecipe> {

        @Override
        public CultivatorRecipe fromJson(ResourceLocation id, JsonObject json) {
            Kind kind = Kind.byKey(GsonHelper.getAsString(json, "kind", Kind.CROP.key()));
            JsonElement inputJson = json.get("input");
            if (inputJson == null || inputJson.isJsonNull()) {
                throw new JsonParseException("缺少 input");
            }
            Ingredient input = Ingredient.fromJson(inputJson, false);
            if (input.isEmpty()) {
                throw new JsonParseException("input 不能为空");
            }
            int ticks = GsonHelper.getAsInt(json, "ticks", kind.ticks);
            int mru = GsonHelper.getAsInt(json, "mru_per_plant", kind.mruPerPlant);
            int maxPlants = GsonHelper.getAsInt(json, "max_plants", kind.maxPlants);
            if (ticks < 1) {
                throw new JsonParseException("ticks 至少 1，实际 " + ticks);
            }
            if (mru < 0) {
                throw new JsonParseException("mru_per_plant 不能为负，实际 " + mru);
            }
            if (maxPlants < 1 || maxPlants > 64) {
                throw new JsonParseException("max_plants 要在 1～64 之间，实际 " + maxPlants);
            }
            boolean needsWater = GsonHelper.getAsBoolean(json, "needs_water", true);
            boolean bonemeal = GsonHelper.getAsBoolean(json, "bonemeal", true);
            JsonArray array = GsonHelper.getAsJsonArray(json, "outputs");
            List<Output> outputs = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject object = GsonHelper.convertToJsonObject(element, "outputs[]");
                ResourceLocation itemId = ResourceLocation.parse(GsonHelper.getAsString(object, "item"));
                if (!ForgeRegistries.ITEMS.containsKey(itemId)) {
                    throw new JsonParseException("没有物品 " + itemId);
                }
                Item item = ForgeRegistries.ITEMS.getValue(itemId);
                int min = GsonHelper.getAsInt(object, "min", 1);
                int max = GsonHelper.getAsInt(object, "max", Math.max(1, min));
                float chance = GsonHelper.getAsFloat(object, "chance", 1F);
                if (min < 0 || max < min || max < 1) {
                    throw new JsonParseException(itemId + " 的数量范围不对：" + min + "～" + max);
                }
                if (!(chance > 0F) || chance > 1F) {
                    throw new JsonParseException(itemId + " 的 chance 要在 (0, 1] 里，实际 " + chance);
                }
                outputs.add(new Output(item, min, max, chance));
            }
            if (outputs.isEmpty()) {
                throw new JsonParseException("outputs 不能为空");
            }
            CultivatorRecipe recipe = new CultivatorRecipe(id, kind, input, ticks, mru, maxPlants, needsWater, bonemeal, outputs);
            // 最坏情况要的格数超过输出格时，这台机器永远开不了工（开工前要按最坏情况放得下），配方直接判错
            int slots = recipe.worstCase(maxPlants).size();
            if (slots > OUTPUT_SLOTS) {
                throw new JsonParseException("满株数的最坏情况要 " + slots + " 格，输出格只有 " + OUTPUT_SLOTS + " 格");
            }
            return recipe;
        }

        @Override
        public CultivatorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            Kind kind = buf.readEnum(Kind.class);
            Ingredient input = Ingredient.fromNetwork(buf);
            int ticks = buf.readVarInt();
            int mru = buf.readVarInt();
            int maxPlants = buf.readVarInt();
            boolean needsWater = buf.readBoolean();
            boolean bonemeal = buf.readBoolean();
            int count = buf.readVarInt();
            List<Output> outputs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Item item = buf.readById(net.minecraft.core.registries.BuiltInRegistries.ITEM);
                outputs.add(new Output(item == null ? Items.AIR : item, buf.readVarInt(), buf.readVarInt(), buf.readFloat()));
            }
            return new CultivatorRecipe(id, kind, input, ticks, mru, maxPlants, needsWater, bonemeal, outputs);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, CultivatorRecipe recipe) {
            buf.writeEnum(recipe.kind);
            recipe.input.toNetwork(buf);
            buf.writeVarInt(recipe.ticks);
            buf.writeVarInt(recipe.mruPerPlant);
            buf.writeVarInt(recipe.maxPlants);
            buf.writeBoolean(recipe.needsWater);
            buf.writeBoolean(recipe.bonemeal);
            buf.writeVarInt(recipe.outputs.size());
            for (Output output : recipe.outputs) {
                buf.writeId(net.minecraft.core.registries.BuiltInRegistries.ITEM, output.item());
                buf.writeVarInt(output.min());
                buf.writeVarInt(output.max());
                buf.writeFloat(output.chance());
            }
        }
    }
}
