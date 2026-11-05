package com.flameforge.essentialcraft5.item.ember;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 余烬元素剑锻出来时定下的数据（原作 {@code ItemElementalSword} 的 NBT：{@code ember_0~3}、{@code focus_0~3}、{@code primary}、{@code secondary}）。
 * 存在物品 NBT 的子标签 {@value #TAG} 里：
 * <pre>{@code
 * ec5_forged: { embers: ["essentialcraft5:acidic_ember", …×4], foci: ["essentialcraft5:fire_focus", …×4], primary: "fire", secondary: "water" }
 * }</pre>
 * MRU 仍在顶层 {@code mru} 键（{@code MRUItemStorage}）。没有这个子标签的剑是「没锻造的」：没有属性、没有效果、模型是整把剑。
 *
 * <h2>主副元素（{@link #forge}）</h2>
 * <ul>
 *   <li>主元素：某种焦点的个数<b>严格多于</b>其余三种就是它，否则（2 + 2、1 + 1 + 1 + 1、2 + 1 + 1……）是复合（原作 {@code setPrimaryAttribute} L271~285）。</li>
 *   <li>副元素：在出现过的元素里等概率挑一个，用世界的随机数（原作是一个种子固定为 8192 的静态 {@code Random}，所有剑共用一条序列）。</li>
 * </ul>
 * 原作锻炉先扣掉物品、再读名字，焦点（一组 1 个）扣完是空栈、读出来是空气，所以 1.12 的剑全是「复合 / 复合」，单个扔进去的余烬也记不下；
 * 锻炉现在先记下 8 件的注册名再扣（{@code EmberForgeBlockEntity}），这里的规则第一次真正起作用。
 */
public record ForgedSwordData(List<ResourceLocation> embers, List<ResourceLocation> foci, Element primary, Element secondary) {

    /** NBT 子标签名 */
    public static final String TAG = "ec5_forged";
    private static final String TAG_EMBERS = "embers";
    private static final String TAG_FOCI = "foci";
    private static final String TAG_PRIMARY = "primary";
    private static final String TAG_SECONDARY = "secondary";
    /** 一把剑最多记几个余烬 / 焦点（锻炉各 4 个；读档时多出来的不认） */
    public static final int SLOTS = 4;

    public ForgedSwordData {
        embers = List.copyOf(embers);
        foci = List.copyOf(foci);
    }

    /**
     * 剑的元素。{@link #damage} 是主元素给的攻击伤害加成（原作 {@code getAttributeModifiers} L156~170），
     * {@link #layer} 是物品模型的层名（原作物品方块状态 {@code bottom=… / top=…} 的取值，复合叫 normal）。
     */
    public enum Element {
        FIRE(7, "fire_focus"),
        WATER(3, "water_focus"),
        EARTH(8, "earth_focus"),
        AIR(8, "air_focus"),
        COMBINED(5, null);

        private static final Element[] VALUES = values();
        /** 四种单一元素（不含复合），按原作的顺序 */
        public static final List<Element> SINGLE = List.of(FIRE, WATER, EARTH, AIR);

        private final int damage;
        @Nullable
        private final ResourceLocation focus;

        Element(int damage, @Nullable String focus) {
            this.damage = damage;
            this.focus = focus == null ? null : EssentialCraft5.id(focus);
        }

        public int damage() {
            return damage;
        }

        /** NBT 里的写法：fire / water / earth / air / combined */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 模型层名：fire / water / earth / air / normal */
        public String layer() {
            return this == COMBINED ? "normal" : id();
        }

        /** 提示框里的名字 */
        public String translationKey() {
            return "tooltip.essentialcraft5.element." + id();
        }

        /** 这个元素的焦点注册名（复合为 null） */
        @Nullable
        public ResourceLocation focus() {
            return focus;
        }

        /** 按 NBT 写法找；认不出返回 null */
        @Nullable
        public static Element byId(String id) {
            for (Element element : VALUES) {
                if (element.id().equals(id)) {
                    return element;
                }
            }
            return null;
        }

        /** 按焦点注册名找；不是四种焦点之一返回 null */
        @Nullable
        public static Element ofFocus(@Nullable ResourceLocation id) {
            if (id == null) {
                return null;
            }
            for (Element element : SINGLE) {
                if (id.equals(element.focus)) {
                    return element;
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 锻造
    // ------------------------------------------------------------------

    /**
     * 按 4 个余烬、4 个焦点的注册名定下一把剑（纯函数：只用传进来的随机数）。不是焦点的 id 不计数，不是余烬的 id 照存、不配对。
     */
    public static ForgedSwordData forge(List<ResourceLocation> embers, List<ResourceLocation> foci, RandomSource random) {
        int[] counts = new int[Element.SINGLE.size()];
        for (ResourceLocation id : foci) {
            Element element = Element.ofFocus(id);
            if (element != null) {
                counts[element.ordinal()]++;
            }
        }
        Element primary = Element.COMBINED;
        for (int i = 0; i < counts.length; i++) {
            boolean most = true;
            for (int j = 0; j < counts.length; j++) {
                if (j != i && counts[i] <= counts[j]) {
                    most = false;
                    break;
                }
            }
            if (most) {
                primary = Element.SINGLE.get(i);
                break;
            }
        }
        List<Element> present = new ArrayList<>(4);
        for (Element element : Element.SINGLE) {
            if (counts[element.ordinal()] > 0) {
                present.add(element);
            }
        }
        Element secondary = present.isEmpty() ? primary : present.get(random.nextInt(present.size()));
        return new ForgedSwordData(embers, foci, primary, secondary);
    }

    // ------------------------------------------------------------------
    // 派生（都由存下来的注册名现算，表改了旧剑跟着变）
    // ------------------------------------------------------------------

    /** 剑上出现过的余烬种类（认不出的注册名跳过） */
    public Set<EmberKind> emberKinds() {
        Set<EmberKind> kinds = EnumSet.noneOf(EmberKind.class);
        for (ResourceLocation id : embers) {
            EmberKind kind = EmberKind.byId(id);
            if (kind != null) {
                kinds.add(kind);
            }
        }
        return kinds;
    }

    /** 两两配出的组合（含没有效果的那两对） */
    public List<EmberEffect.Combo> combos() {
        return EmberEffect.combosOf(emberKinds());
    }

    /** 余烬组合的攻击伤害加成合计（每对都算，最多 36）；不封顶，实际生效的看 {@code ForgedElementalSwordItem.emberBonus} */
    public int emberDamage() {
        int sum = 0;
        for (EmberEffect.Combo combo : combos()) {
            sum += combo.damage();
        }
        return sum;
    }

    /** 余烬给的命中型效果（集合：同一效果两对都出也只算一次） */
    public Set<EmberEffect> effects() {
        Set<EmberEffect> effects = EnumSet.noneOf(EmberEffect.class);
        for (EmberEffect.Combo combo : combos()) {
            effects.addAll(combo.effects());
        }
        return effects;
    }

    /** 主元素 + 余烬合计（不封顶）；剑的属性和提示用 {@code ForgedElementalSwordItem.attackBonus}（余烬部分按配置封顶） */
    public int attackDamage() {
        return primary.damage() + emberDamage();
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    public static boolean isForged(ItemStack stack) {
        return stack.getTagElement(TAG) != null;
    }

    /** 没锻造（没有子标签）返回空；子标签里认不出的元素按复合算 */
    public static Optional<ForgedSwordData> read(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? Optional.empty() : Optional.of(load(tag));
    }

    public static ForgedSwordData load(CompoundTag tag) {
        Element primary = Element.byId(tag.getString(TAG_PRIMARY));
        Element secondary = Element.byId(tag.getString(TAG_SECONDARY));
        primary = primary == null ? Element.COMBINED : primary;
        return new ForgedSwordData(ids(tag.getList(TAG_EMBERS, Tag.TAG_STRING)), ids(tag.getList(TAG_FOCI, Tag.TAG_STRING)),
                primary, secondary == null ? primary : secondary);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_EMBERS, list(embers));
        tag.put(TAG_FOCI, list(foci));
        tag.putString(TAG_PRIMARY, primary.id());
        tag.putString(TAG_SECONDARY, secondary.id());
        return tag;
    }

    /** 写进剑的 NBT（覆盖原有的锻造数据，别的键不动） */
    public void writeTo(ItemStack stack) {
        stack.addTagElement(TAG, save());
    }

    /**
     * 物品模型用的键「下层/上层」（原作 {@code bottom=主元素层名, top=副元素层名}，5 × 5 = 25 种）。
     * 只读两个字符串，渲染线程每帧调也不贵；没锻造的是 {@code normal/normal}（整把剑）。
     */
    public static String modelKey(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null) {
            return modelKey(Element.COMBINED, Element.COMBINED);
        }
        Element primary = Element.byId(tag.getString(TAG_PRIMARY));
        Element secondary = Element.byId(tag.getString(TAG_SECONDARY));
        primary = primary == null ? Element.COMBINED : primary;
        return modelKey(primary, secondary == null ? primary : secondary);
    }

    public static String modelKey(Element bottom, Element top) {
        return bottom.layer() + "/" + top.layer();
    }

    private static List<ResourceLocation> ids(ListTag list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        List<ResourceLocation> out = new ArrayList<>(Math.min(list.size(), SLOTS));
        for (int i = 0; i < list.size() && out.size() < SLOTS; i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    private static ListTag list(List<ResourceLocation> ids) {
        ListTag list = new ListTag();
        for (ResourceLocation id : ids) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }
}
