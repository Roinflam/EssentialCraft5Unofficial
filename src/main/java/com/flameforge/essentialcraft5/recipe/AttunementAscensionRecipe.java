package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageEvent;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 同调升阶（batch13.md §3.3.5、§8.2「升阶在哪」）：魔力工作台同一个配方类型 {@code essentialcraft5:magician_table}、序列化器
 * {@code essentialcraft5:attunement_ascension}。中心放一件攒满了的同调装备，四角放材料（每格可以要好几个），出来的是同一件升一阶
 * （附魔、染色、模块、MRU、余烬……全部 NBT 照留）；写了 {@code result} 时升格成另一件物品（裁决之刃用），NBT 照搬、同调从 0 阶起、带上旧件累计的一半。
 * <pre>
 * { "type": "essentialcraft5:attunement_ascension",
 *   "gear": "essentialcraft5:computer_armor",          家族（标签 essentialcraft5:attunement/computer_armor）
 *   "parts": ["chest"],                                可省：只认这几个部位（head / chest / legs / feet / mainhand）
 *   "from_stage": 1,                                   中心那件现在是几阶（升到 from_stage + 1）
 *   "ingredients": [ 左上, 右上, 左下, 右下 ],           空位写 null；一格要好几个写 {"item": …, "count": 2}
 *                                                      或 {"ingredient": 任意原料 JSON, "count": 2}
 *   "result": { "item": … },                           可省：省了 = 同一件升一阶
 *   "mru": 70000 }
 * </pre>
 * 匹配（{@link #matches}）：中心是这个家族（和部位）、阶 = {@code from_stage}（升格配方可以等于家族最高阶，最高阶算「攒满」）、本阶同调度攒满、物品上的<b>阶戳</b> ≥ 升到下一阶要的矩阵阶
 * （{@code general.attunement.rank_gate}，默认第 n 阶要 2n − 1 阶）；四角原料对得上、数量够。工作台查配方时只有容器和世界、没有玩家
 * （漏斗进料时主人可能不在线），所以门槛只读阶戳，不读谁的矩阵阶。同调总开关关着、家族被 {@code disabled_families} 关掉时不匹配（不出产物）。
 * <p>
 * 按产物查配方时（研究手册的物品页）不列同一件升一阶的配方：{@link #getResultItem} 返回空，免得每件盔甲的配方页多出五张升阶图；
 * 升格配方照常返回产物。研究手册和 JEI 按 {@link #displayCenter} / {@link #displayResult} 画。
 */
public class AttunementAscensionRecipe extends MagicianTableRecipe implements PositionalInsertRule.SlotTest {

    /** 四角原料在容器里的下标从 1 起 */
    public static final int CORNERS = 4;

    private final ResourceLocation family;
    private final Set<EquipmentSlot> parts;
    private final int fromStage;
    private final int[] counts;
    private final ItemStack promoteTo;

    public AttunementAscensionRecipe(ResourceLocation id, ResourceLocation family, Set<EquipmentSlot> parts, int fromStage,
                                     NonNullList<Ingredient> corners, int[] cornerCounts, ItemStack promoteTo, int mru) {
        super(id, inputs(family, corners), promoteTo, mru);
        this.family = family;
        this.parts = parts.isEmpty() ? Set.of() : EnumSet.copyOf(parts);
        this.fromStage = fromStage;
        this.counts = new int[INPUTS];
        this.counts[0] = 1;
        for (int i = 0; i < CORNERS; i++) {
            this.counts[i + 1] = i < cornerCounts.length ? Math.max(1, cornerCounts[i]) : 1;
        }
        this.promoteTo = promoteTo;
    }

    private static NonNullList<Ingredient> inputs(ResourceLocation family, NonNullList<Ingredient> corners) {
        NonNullList<Ingredient> list = NonNullList.withSize(INPUTS, Ingredient.EMPTY);
        list.set(0, Ingredient.of(AttunementFamily.tagFor(family)));
        for (int i = 0; i < CORNERS && i < corners.size(); i++) {
            list.set(i + 1, corners.get(i));
        }
        return list;
    }

    public ResourceLocation family() {
        return family;
    }

    /** 只认的部位；空 = 家族里哪件都行 */
    public Set<EquipmentSlot> parts() {
        return parts;
    }

    public int fromStage() {
        return fromStage;
    }

    public int toStage() {
        return fromStage + 1;
    }

    /** 升格成的物品；同一件升一阶时为空 */
    public ItemStack promoteTo() {
        return promoteTo;
    }

    /** 升到下一阶要的矩阵阶（按现在的配置；矩阵阶关掉时 0） */
    public int requiredRank() {
        return AttunementConfig.requiredRank(toStage());
    }

    /**
     * 研究手册配方图下面那行矩阵阶要求；矩阵阶关掉（要求 0）时 null。同一件升一阶写「升到 n 阶要矩阵 r 阶」；
     * 升格（写了 {@code result}，产物从 0 阶起）只写「升格要矩阵 r 阶」，不然读着像中心那件还能再升一阶。门槛照旧按 {@link #toStage} 算。
     */
    @Nullable
    public Component rankNote() {
        int rank = requiredRank();
        if (rank <= 0) {
            return null;
        }
        return promoteTo.isEmpty() ? Component.translatable("guide.essentialcraft5.recipe.attunement.rank", toStage(), rank)
                : Component.translatable("guide.essentialcraft5.recipe.attunement.rank_promote", rank);
    }

    @Override
    public int countAt(int index) {
        return index >= 0 && index < INPUTS ? counts[index] : 1;
    }

    /** 中心这件对不对：家族、部位、阶、攒满、阶戳（不看数量） */
    public boolean acceptsCenter(ItemStack stack) {
        AttunementFamily owner = AttunementFamily.byId(family);
        if (owner == null || stack.isEmpty() || !stack.is(owner.tag()) || !owner.active()) {
            return false;
        }
        // 同一件升一阶时中心不能已是最高阶；升格（写了 result）的中心正好是攒满的最高阶（裁决之刃要 2 阶的余烬元素剑），所以放宽到最高阶
        int highest = promoteTo.isEmpty() ? owner.maxStage() - 1 : owner.maxStage();
        if (fromStage < 0 || fromStage > highest || Attunement.stage(stack) != fromStage) {
            return false;
        }
        if (!parts.isEmpty() && !parts.contains(AttunementFamily.slotOf(stack, owner.kind()))) {
            return false;
        }
        return Attunement.points(stack) >= Attunement.need(owner, fromStage) && Attunement.rankStamp(stack) >= requiredRank();
    }

    @Override
    public boolean acceptsAt(int index, ItemStack stack) {
        if (index == 0) {
            return acceptsCenter(stack);
        }
        return index > 0 && index < INPUTS && getIngredients().get(index).test(stack);
    }

    @Override
    public boolean matches(Container container, Level level) {
        if (container.getContainerSize() < INPUTS) {
            return false;
        }
        for (int i = 0; i < INPUTS; i++) {
            ItemStack stack = container.getItem(i);
            if (!acceptsAt(i, stack) || (!stack.isEmpty() && stack.getCount() < countAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        ItemStack center = container.getItem(0);
        AttunementFamily owner = AttunementFamily.byId(family);
        if (center.isEmpty() || owner == null || !owner.active()) {
            return ItemStack.EMPTY;
        }
        return promoteTo.isEmpty() ? Attunement.ascended(center) : Attunement.promoted(center, promoteTo);
    }

    /** 工作台出了一份：发阶变化事件（这时没有玩家） */
    @Override
    public void crafted(Container inputs, ItemStack result) {
        AttunementFamily owner = AttunementFamily.of(result);
        if (owner == null) {
            owner = AttunementFamily.byId(family);
        }
        if (owner != null) {
            MinecraftForge.EVENT_BUS.post(new AttunementStageEvent(null, result, owner, fromStage, Attunement.stage(result),
                    AttunementStageEvent.Cause.ASCENSION));
        }
    }

    /** 同一件升一阶时返回空（不进「按产物查」的索引）；升格时是升格成的物品 */
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return promoteTo;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ECAttunement.ASCENSION_SERIALIZER.get();
    }

    // ------------------------------------------------------------------
    // 给研究手册、JEI 画图用
    // ------------------------------------------------------------------

    /** 中心格的候选：家族（和部位）里每件物品，带着「from_stage 阶、本阶攒满、阶戳够」的同调键 */
    public List<ItemStack> displayCenter() {
        List<ItemStack> out = new ArrayList<>();
        AttunementFamily owner = AttunementFamily.byId(family);
        List<ItemStack> options = owner == null ? List.of() : owner.members();
        for (ItemStack option : options) {
            if (!parts.isEmpty() && !parts.contains(AttunementFamily.slotOf(option, owner.kind()))) {
                continue;
            }
            ItemStack stack = option.copyWithCount(1);
            Attunement.set(stack, fromStage, Attunement.need(owner, fromStage));
            Attunement.stampRank(stack, requiredRank());
            out.add(stack);
        }
        return out;
    }

    /** 中心放 center 时出来的样子（不看开关） */
    public ItemStack displayResult(ItemStack center) {
        if (center.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return promoteTo.isEmpty() ? Attunement.ascended(center) : Attunement.promoted(center, promoteTo);
    }

    /** 第 index 格的候选，数量照配方要的写上（空格是空数组） */
    public ItemStack[] displayInputs(int index) {
        if (index == 0) {
            return displayCenter().toArray(ItemStack[]::new);
        }
        ItemStack[] items = getIngredients().get(index).getItems();
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = items[i].copyWithCount(countAt(index));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // JSON / 网络
    // ------------------------------------------------------------------

    /** 一格原料 + 数量 */
    public record Counted(Ingredient ingredient, int count) {
        public static final Counted EMPTY = new Counted(Ingredient.EMPTY, 1);
    }

    /**
     * 读一格「可带数量」的原料：null → 空格；{@code {"ingredient": …, "count": n}}；{@code {"item"/"tag": …, "count": n}}；
     * 其余照普通原料读（数量 1）。
     */
    public static Counted counted(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Counted.EMPTY;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            int count = GsonHelper.getAsInt(object, "count", 1);
            if (count < 1 || count > 64) {
                throw new JsonParseException("count 要在 1 ~ 64，实际 " + count);
            }
            if (object.has("ingredient")) {
                return new Counted(Ingredient.fromJson(object.get("ingredient")), count);
            }
            JsonObject copy = object.deepCopy();
            copy.remove("count");
            return new Counted(Ingredient.fromJson(copy), count);
        }
        return new Counted(Ingredient.fromJson(element), 1);
    }

    /** 读定长的四角（或任意格数）：不足的补空格 */
    public static List<Counted> countedList(JsonObject json, String key, int size) {
        JsonArray array = GsonHelper.getAsJsonArray(json, key);
        if (array.size() > size) {
            throw new JsonParseException(key + " 最多 " + size + " 个，实际 " + array.size());
        }
        List<Counted> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(i < array.size() ? counted(array.get(i)) : Counted.EMPTY);
        }
        return out;
    }

    /** 家族名：不带命名空间时按本模组（同配置 disabled_families 的写法，{@link AttunementConfig#familyId}） */
    public static ResourceLocation familyId(String raw) {
        ResourceLocation id = AttunementConfig.familyId(raw);
        if (id == null) {
            throw new JsonParseException("家族名不对：" + raw);
        }
        return id;
    }

    static EquipmentSlot part(String name) {
        try {
            EquipmentSlot slot = EquipmentSlot.byName(name.toLowerCase(Locale.ROOT));
            if (slot == EquipmentSlot.OFFHAND) {
                throw new IllegalArgumentException();
            }
            return slot;
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("parts 只认 head / chest / legs / feet / mainhand，实际 " + name);
        }
    }

    public static class Serializer implements RecipeSerializer<AttunementAscensionRecipe> {

        @Override
        public AttunementAscensionRecipe fromJson(ResourceLocation id, JsonObject json) {
            ResourceLocation family = familyId(GsonHelper.getAsString(json, "gear"));
            Set<EquipmentSlot> parts = EnumSet.noneOf(EquipmentSlot.class);
            if (json.has("parts")) {
                for (JsonElement element : GsonHelper.getAsJsonArray(json, "parts")) {
                    parts.add(part(element.getAsString()));
                }
            }
            int fromStage = GsonHelper.getAsInt(json, "from_stage");
            if (fromStage < 0 || fromStage >= Attunement.MAX_STAGE) {
                throw new JsonParseException("from_stage 要在 0 ~ " + (Attunement.MAX_STAGE - 1) + "，实际 " + fromStage);
            }
            List<Counted> corners = countedList(json, "ingredients", CORNERS);
            NonNullList<Ingredient> ingredients = NonNullList.withSize(CORNERS, Ingredient.EMPTY);
            int[] counts = new int[CORNERS];
            for (int i = 0; i < CORNERS; i++) {
                ingredients.set(i, corners.get(i).ingredient());
                counts[i] = corners.get(i).count();
            }
            ItemStack result = json.has("result") ? RecipeJson.result(json) : ItemStack.EMPTY;
            return new AttunementAscensionRecipe(id, family, parts, fromStage, ingredients, counts, result, GsonHelper.getAsInt(json, "mru"));
        }

        @Override
        public AttunementAscensionRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            ResourceLocation family = buf.readResourceLocation();
            int mask = buf.readVarInt();
            Set<EquipmentSlot> parts = EnumSet.noneOf(EquipmentSlot.class);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if ((mask & (1 << slot.ordinal())) != 0) {
                    parts.add(slot);
                }
            }
            int fromStage = buf.readVarInt();
            NonNullList<Ingredient> ingredients = RecipeJson.readIngredients(buf);
            int[] counts = buf.readVarIntArray();
            ItemStack result = buf.readItem();
            int mru = buf.readVarInt();
            return new AttunementAscensionRecipe(id, family, parts, fromStage, ingredients, counts, result, mru);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, AttunementAscensionRecipe recipe) {
            buf.writeResourceLocation(recipe.family);
            int mask = 0;
            for (EquipmentSlot slot : recipe.parts) {
                mask |= 1 << slot.ordinal();
            }
            buf.writeVarInt(mask);
            buf.writeVarInt(recipe.fromStage);
            NonNullList<Ingredient> corners = NonNullList.withSize(CORNERS, Ingredient.EMPTY);
            int[] counts = new int[CORNERS];
            for (int i = 0; i < CORNERS; i++) {
                corners.set(i, recipe.getIngredients().get(i + 1));
                counts[i] = recipe.counts[i + 1];
            }
            RecipeJson.writeIngredients(buf, corners);
            buf.writeVarIntArray(counts);
            buf.writeItem(recipe.promoteTo);
            buf.writeVarInt(recipe.getMRU());
        }
    }

    /** 测试：不进配方表、直接拿来比对的配方（id 随意） */
    public static AttunementAscensionRecipe of(ResourceLocation id, ResourceLocation family, Set<EquipmentSlot> parts, int fromStage,
                                              List<Counted> corners, ItemStack promoteTo, int mru) {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(CORNERS, Ingredient.EMPTY);
        int[] counts = new int[CORNERS];
        for (int i = 0; i < CORNERS && i < corners.size(); i++) {
            ingredients.set(i, corners.get(i).ingredient());
            counts[i] = corners.get(i).count();
        }
        return new AttunementAscensionRecipe(id, family, parts, fromStage, ingredients, counts, promoteTo, mru);
    }
}
