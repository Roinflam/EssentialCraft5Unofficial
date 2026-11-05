package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.registry.ECAttunement;
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
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 同调转移（batch13.md §3.3.5、D10 C）：换套时把旧件攒的同调度带一半到新件上。魔力工作台同一个配方类型、序列化器 {@code essentialcraft5:attunement_transfer}。
 * <ul>
 *   <li>中心：新件，0 阶、本阶还没攒满；左上：同一部位的旧件，属于档位更低的同种家族（魔力防护 → 风之 → 虚空 / 审判庭 → 宅男），攒过同调度；
 *       其余三角照 JSON（可带数量）。</li>
 *   <li>出来的是新件：拿到旧件累计的一半，只进本阶点数、不跨阶，最多新件 1 阶门槛的两倍（{@link Attunement#transferAmount}）。</li>
 *   <li>旧件留在左上角（工作台只有一个输出格），只清掉同调度和累计，阶照留，玩家自己取回；三角材料照数量扣。</li>
 * </ul>
 * 余烬元素剑 → 裁决之刃不走这里：升格配方（{@link AttunementAscensionRecipe} 写了 result）自带一半。
 * <pre>
 * { "type": "essentialcraft5:attunement_transfer",
 *   "kind": "armor",                            armor / weapon
 *   "ingredients": [ 右上, 左下, 右下 ],          同升阶配方，可带 count
 *   "mru": 10000 }
 * </pre>
 * {@link #getResultItem} 返回空（不进「按产物查」的索引），研究手册和 JEI 按 {@link #displayInputs} / {@link #displayResult} 画。
 */
public class AttunementTransferRecipe extends MagicianTableRecipe implements PositionalInsertRule.SlotTest {

    /** 旧件在第几格（容器下标，1 = 左上） */
    public static final int OLD_SLOT = 1;
    /** JSON 里的三角从容器第几格起（右上、左下、右下） */
    private static final int FIRST_EXTRA = 2;
    private static final int EXTRAS = 3;

    private final AttunementFamily.Kind kind;
    private final int[] counts;

    public AttunementTransferRecipe(ResourceLocation id, AttunementFamily.Kind kind, NonNullList<Ingredient> extras, int[] extraCounts, int mru) {
        super(id, inputs(kind, extras), ItemStack.EMPTY, mru);
        this.kind = kind;
        this.counts = new int[INPUTS];
        this.counts[0] = 1;
        this.counts[OLD_SLOT] = 1;
        for (int i = 0; i < EXTRAS; i++) {
            this.counts[FIRST_EXTRA + i] = i < extraCounts.length ? Math.max(1, extraCounts[i]) : 1;
        }
    }

    private static NonNullList<Ingredient> inputs(AttunementFamily.Kind kind, NonNullList<Ingredient> extras) {
        NonNullList<Ingredient> list = NonNullList.withSize(INPUTS, Ingredient.EMPTY);
        Ingredient gear = gearOf(kind);
        list.set(0, gear);
        list.set(OLD_SLOT, gear);
        for (int i = 0; i < EXTRAS && i < extras.size(); i++) {
            list.set(FIRST_EXTRA + i, extras.get(i));
        }
        return list;
    }

    /** 这一种（盔甲 / 武器）全部家族的标签合成一个原料（登记家族在配方加载之前就做完了） */
    private static Ingredient gearOf(AttunementFamily.Kind kind) {
        return Ingredient.fromValues(AttunementFamily.all().stream()
                .filter(family -> family.kind() == kind)
                .map(family -> (Ingredient.Value) new Ingredient.TagValue(family.tag())));
    }

    public AttunementFamily.Kind kind() {
        return kind;
    }

    @Override
    public int countAt(int index) {
        return index >= 0 && index < INPUTS ? counts[index] : 1;
    }

    @Override
    public boolean acceptsAt(int index, ItemStack stack) {
        if (index == 0) {
            AttunementFamily family = familyOf(stack);
            return family != null && family.active() && Attunement.stage(stack) == 0 && Attunement.need(stack) > Attunement.points(stack);
        }
        if (index == OLD_SLOT) {
            return familyOf(stack) != null && Attunement.total(stack) > 0;
        }
        return index > 0 && index < INPUTS && getIngredients().get(index).test(stack);
    }

    /**
     * 新件（中心）、旧件（左上）只许手放：逐格看，任何攒过点的同调盔甲都像旧件、任何 0 阶没攒满的都像新件，漏斗会把配不成对的东西塞进来卡住工作台；
     * 转移完旧件也要玩家自己取回。三角材料照常许漏斗补
     */
    @Override
    public boolean automatable(int index) {
        return index != 0 && index != OLD_SLOT;
    }

    @Nullable
    private AttunementFamily familyOf(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        return family != null && family.kind() == kind ? family : null;
    }

    /** 旧件能不能转给新件：同一部位、旧件家族档位更低、真能转出点数 */
    public boolean pairs(ItemStack newPiece, ItemStack oldPiece) {
        AttunementFamily to = familyOf(newPiece);
        AttunementFamily from = familyOf(oldPiece);
        if (to == null || from == null || from.tier() >= to.tier()) {
            return false;
        }
        if (AttunementFamily.slotOf(newPiece, kind) != AttunementFamily.slotOf(oldPiece, kind)) {
            return false;
        }
        return Attunement.transferAmount(oldPiece, newPiece) > 0;
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
        return pairs(container.getItem(0), container.getItem(OLD_SLOT));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        ItemStack newPiece = container.getItem(0);
        ItemStack oldPiece = container.getItem(OLD_SLOT);
        AttunementFamily family = familyOf(newPiece);
        if (family == null || !family.active() || !pairs(newPiece, oldPiece)) {
            return ItemStack.EMPTY;
        }
        ItemStack out = newPiece.copyWithCount(1);
        Attunement.addPoints(out, Attunement.transferAmount(oldPiece, out));
        Attunement.stampRank(out, Attunement.rankStamp(oldPiece));
        return out;
    }

    /** 旧件留在原格，只清同调度和累计 */
    @Override
    @Nullable
    public ItemStack remainderAt(int index, ItemStack inSlot) {
        return index == OLD_SLOT && !inSlot.isEmpty() ? Attunement.cleared(inSlot) : null;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ECAttunement.TRANSFER_SERIALIZER.get();
    }

    // ------------------------------------------------------------------
    // 给研究手册、JEI 画图用：中心画宅男、左上画虚空（各部位轮换），旧件写着攒了 1 000 点
    // ------------------------------------------------------------------

    /** 示例用的旧件累计 */
    public static final int DISPLAY_OLD_TOTAL = 1000;

    /** 第 index 格的示例物品，数量照配方要的写上 */
    public ItemStack[] displayInputs(int index) {
        if (index == 0 || index == OLD_SLOT) {
            List<ItemStack> out = new ArrayList<>();
            for (ItemStack[] pair : displayPairs()) {
                out.add(pair[index == 0 ? 0 : 1]);
            }
            return out.toArray(ItemStack[]::new);
        }
        ItemStack[] items = getIngredients().get(index).getItems();
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = items[i].copyWithCount(countAt(index));
        }
        return out;
    }

    /** 示例：最高档家族的每件配最低档家族的同一部位 */
    public List<ItemStack[]> displayPairs() {
        AttunementFamily low = null;
        AttunementFamily high = null;
        for (AttunementFamily family : AttunementFamily.all()) {
            if (family.kind() != kind) {
                continue;
            }
            if (low == null || family.tier() < low.tier()) {
                low = family;
            }
            if (high == null || family.tier() > high.tier()) {
                high = family;
            }
        }
        List<ItemStack[]> out = new ArrayList<>();
        if (low == null || high == null || low == high) {
            return out;
        }
        List<ItemStack> olds = low.members();
        for (ItemStack neu : high.members()) {
            for (ItemStack old : olds) {
                if (AttunementFamily.slotOf(old, kind) == AttunementFamily.slotOf(neu, kind)) {
                    ItemStack oldCopy = old.copyWithCount(1);
                    Attunement.set(oldCopy, 1, 0);
                    oldCopy.getOrCreateTagElement(Attunement.TAG).putInt(Attunement.KEY_TOTAL, DISPLAY_OLD_TOTAL);
                    out.add(new ItemStack[]{neu.copyWithCount(1), oldCopy});
                    break;
                }
            }
        }
        return out;
    }

    /** 中心放 newPiece、左上放 oldPiece 时出来的样子 */
    public ItemStack displayResult(ItemStack newPiece, ItemStack oldPiece) {
        if (newPiece.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = newPiece.copyWithCount(1);
        Attunement.addPoints(out, Attunement.transferAmount(oldPiece, out));
        return out;
    }

    // ------------------------------------------------------------------

    public static class Serializer implements RecipeSerializer<AttunementTransferRecipe> {

        @Override
        public AttunementTransferRecipe fromJson(ResourceLocation id, JsonObject json) {
            AttunementFamily.Kind kind = kind(GsonHelper.getAsString(json, "kind"));
            List<AttunementAscensionRecipe.Counted> extras = AttunementAscensionRecipe.countedList(json, "ingredients", EXTRAS);
            NonNullList<Ingredient> ingredients = NonNullList.withSize(EXTRAS, Ingredient.EMPTY);
            int[] counts = new int[EXTRAS];
            for (int i = 0; i < EXTRAS; i++) {
                ingredients.set(i, extras.get(i).ingredient());
                counts[i] = extras.get(i).count();
            }
            return new AttunementTransferRecipe(id, kind, ingredients, counts, GsonHelper.getAsInt(json, "mru"));
        }

        static AttunementFamily.Kind kind(String name) {
            try {
                return AttunementFamily.Kind.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("kind 只认 armor / weapon，实际 " + name);
            }
        }

        @Override
        public AttunementTransferRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            AttunementFamily.Kind kind = buf.readEnum(AttunementFamily.Kind.class);
            NonNullList<Ingredient> extras = RecipeJson.readIngredients(buf);
            int[] counts = buf.readVarIntArray();
            return new AttunementTransferRecipe(id, kind, extras, counts, buf.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, AttunementTransferRecipe recipe) {
            buf.writeEnum(recipe.kind);
            NonNullList<Ingredient> extras = NonNullList.withSize(EXTRAS, Ingredient.EMPTY);
            int[] counts = new int[EXTRAS];
            for (int i = 0; i < EXTRAS; i++) {
                extras.set(i, recipe.getIngredients().get(FIRST_EXTRA + i));
                counts[i] = recipe.counts[FIRST_EXTRA + i];
            }
            RecipeJson.writeIngredients(buf, extras);
            buf.writeVarIntArray(counts);
            buf.writeVarInt(recipe.getMRU());
        }
    }

    /** 测试用：同 JSON */
    public static AttunementTransferRecipe of(ResourceLocation id, AttunementFamily.Kind kind, List<AttunementAscensionRecipe.Counted> extras, int mru) {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(EXTRAS, Ingredient.EMPTY);
        int[] counts = new int[EXTRAS];
        for (int i = 0; i < EXTRAS && i < extras.size(); i++) {
            ingredients.set(i, extras.get(i).ingredient());
            counts[i] = extras.get(i).count();
        }
        return new AttunementTransferRecipe(id, kind, ingredients, counts, mru);
    }
}
