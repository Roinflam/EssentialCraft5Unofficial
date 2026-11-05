package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.ember.EmberEffect;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 余烬嵌片（第十批 10b，batch10.md §5.7.2；注册名 {@code ember_inlay}，{@code ECEmberForge}）。一片记着一对不同的余烬，
 * 在铁砧上嵌进一把武器（{@code item/ember/InlayEvents}），之后用这把武器打实一下就触发这一对余烬在余烬元素剑上的命中效果
 * （{@link EmberEffect#COMBOS}，和余烬元素剑共用一张表），每下扣 {@code mru_per_hit} MRU；<b>只给效果，不加伤害</b>。
 * <ul>
 *   <li><b>配方</b>：工作台无序「两个不同的余烬 + 淡青宝石 + 注魔合金」，结果 NBT {@value #EMBERS_TAG} 是两个余烬的注册名（按注册名排序）。
 *       只做带命中效果的 14 对（{@link #craftablePairs}，配方由 {@code tools/gen/ember_inlay_recipes.py} 照同一张表生成）：
 *       只加伤害的 13 对、「混沌 + 阴燃」（伤害 + 反噬）做不出。</li>
 *   <li><b>能嵌的武器</b>（{@link #isInlayable}）：主手攻击伤害属性 &gt; 0 的、{@code #forge:tools} 里的、弓 / 弩 / 三叉戟、{@code #essentialcraft5:inlayable} 里的；
 *       余烬元素剑本身（它自己就带余烬）和 {@code #essentialcraft5:inlay_blacklist} 里的不行。只认这些通用规则和本模组的标签，不认具体模组。</li>
 *   <li><b>武器上的记号</b>：根级 NBT {@value #INLAY_TAG}（同样是两个注册名），一把武器只有一片，再嵌就换掉。</li>
 *   <li><b>贴图</b>：底图 + 两层按余烬染色（{@link #tint}），图层是 {@code item/misc/ember_inlay_base / _a / _b}：底图是镶淡青宝石边的注魔合金圆片，右上（第一种）和左下（第二种）两颗余烬画成灰度，按 {@link #TINTS} 乘色。</li>
 * </ul>
 * 配置 {@code general.ember_inlay}（{@link Config}）：{@code enabled}、{@code mru_per_hit}、{@code anvil_levels}。
 */
public class EmberInlayItem extends Item implements CreativeVariants {

    /** 嵌片上记的那一对（两个注册名，排好序） */
    public static final String EMBERS_TAG = "essentialcraft5:embers";
    /** 武器根级 NBT：嵌进去的那一对 */
    public static final String INLAY_TAG = "essentialcraft5:inlay";

    /** 本模组允许嵌的额外物品（默认空，给整合包作者加别的模组的武器） */
    public static final TagKey<Item> INLAYABLE = ECTags.modItem("inlayable");
    /** 不许嵌的（默认空） */
    public static final TagKey<Item> INLAY_BLACKLIST = ECTags.modItem("inlay_blacklist");

    /**
     * 两层的染色（按 {@link EmberKind} 的顺序）：取各自贴图里发光纹路的颜色再提亮，乘在灰度余烬上要亮、要饱和。
     * 几对相近的拉开了：混沌偏琥珀、和燃烧分开；阴燃偏绯红；腐化偏靛蓝、和魔力分开；水晶带一点冰蓝，免得和合金混在一起。
     * 酸蚀黄绿、混沌橙、阴燃绯红、腐化靛紫、水晶冷白、神圣青绿、魔力亮紫、燃烧猩红。
     */
    private static final int[] TINTS = {0xA8EC1E, 0xFF8A1A, 0xC4243A, 0x5A36D8, 0xE0F0FF, 0x22C8C0, 0xB45CFF, 0xFF3C24};

    /** 只给 GameTest：当成在黑名单标签里的物品（标签默认是空的，测试世界里没法临时往标签里加东西）；null = 不加 */
    @Nullable
    private static volatile Predicate<ItemStack> testBlacklist;

    public EmberInlayItem(Properties properties) {
        super(properties);
    }

    /**
     * 服务端配置 {@code general.ember_inlay}（{@code ECEmberForge.init} 登记）。读值走静态方法，没加载时是默认值；
     * GameTest 用 {@link #setTestOverrides}，同一段同步代码里设、测、置回 null。
     */
    public static final class Config {

        public static final boolean DEFAULT_ENABLED = true;
        public static final int DEFAULT_MRU_PER_HIT = 50;
        public static final int DEFAULT_ANVIL_LEVELS = 10;

        private static ForgeConfigSpec.BooleanValue enabled;
        private static ForgeConfigSpec.IntValue mruPerHit;
        private static ForgeConfigSpec.IntValue anvilLevels;

        @Nullable
        private static volatile Boolean testEnabled;
        @Nullable
        private static volatile Integer testMruPerHit;
        @Nullable
        private static volatile Integer testAnvilLevels;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Ember Inlay: put a pair of embers into any weapon on an anvil.", "余烬嵌片：在铁砧上把一对余烬的效果嵌进武器").push("ember_inlay");
            enabled = builder.comment("Whether inlays can be applied on an anvil and whether inlaid weapons trigger their effects.",
                            "铁砧能不能嵌、已经嵌好的武器触不触发效果")
                    .define("enabled", DEFAULT_ENABLED);
            mruPerHit = builder.comment("MRU spent per landed hit (taken from MRU storages you carry). Not enough MRU = no effect; the damage is never changed.",
                            "每次打实扣多少 MRU（从身上的储存物品扣）；扣不起就不触发，伤害本身从不改变")
                    .defineInRange("mru_per_hit", DEFAULT_MRU_PER_HIT, 0, 100_000);
            anvilLevels = builder.comment("Experience levels the anvil charges for inlaying (an old inlay is replaced, not returned).",
                            "在铁砧上嵌一次花多少级经验（旧的嵌片被换掉，不退）")
                    .defineInRange("anvil_levels", DEFAULT_ANVIL_LEVELS, 1, 39);
            builder.pop();
        }

        public static boolean enabled() {
            Boolean test = testEnabled;
            if (test != null) {
                return test;
            }
            return enabled == null ? DEFAULT_ENABLED : ECConfig.get(enabled);
        }

        public static int mruPerHit() {
            Integer test = testMruPerHit;
            if (test != null) {
                return test;
            }
            return mruPerHit == null ? DEFAULT_MRU_PER_HIT : ECConfig.get(mruPerHit);
        }

        public static int anvilLevels() {
            Integer test = testAnvilLevels;
            if (test != null) {
                return test;
            }
            return anvilLevels == null ? DEFAULT_ANVIL_LEVELS : ECConfig.get(anvilLevels);
        }

        /** GameTest 用：三项一起覆盖，传 null = 照配置；测完全部置回 null */
        public static void setTestOverrides(@Nullable Boolean enabled, @Nullable Integer mruPerHit, @Nullable Integer anvilLevels) {
            testEnabled = enabled;
            testMruPerHit = mruPerHit;
            testAnvilLevels = anvilLevels;
        }
    }

    // ------------------------------------------------------------------ 一对余烬

    /**
     * 一对不同的余烬，first 的注册名排在 second 前面（NBT 里就是这个顺序；查效果和顺序无关）。
     */
    public record Pair(EmberKind first, EmberKind second) {

        /** 两种余烬配成一对（顺序无所谓）；同种返回 null */
        @Nullable
        public static Pair of(EmberKind x, EmberKind y) {
            if (x == null || y == null || x == y) {
                return null;
            }
            return x.id().toString().compareTo(y.id().toString()) <= 0 ? new Pair(x, y) : new Pair(y, x);
        }

        /** 这一对在余烬元素剑的表里的那一行（一定有：28 对都在表里） */
        @Nullable
        public EmberEffect.Combo combo() {
            return EmberEffect.combo(first, second);
        }

        /** 这一对的命中效果（没有为空） */
        public Set<EmberEffect> effects() {
            EmberEffect.Combo combo = combo();
            return combo == null ? Set.of() : combo.effects();
        }

        ListTag toTag() {
            ListTag list = new ListTag();
            list.add(StringTag.valueOf(first.id().toString()));
            list.add(StringTag.valueOf(second.id().toString()));
            return list;
        }

        @Nullable
        static Pair fromTag(@Nullable CompoundTag root, String key) {
            if (root == null || !root.contains(key, Tag.TAG_LIST)) {
                return null;
            }
            ListTag list = root.getList(key, Tag.TAG_STRING);
            if (list.size() != 2) {
                return null;
            }
            return of(EmberKind.byId(ResourceLocation.tryParse(list.getString(0))), EmberKind.byId(ResourceLocation.tryParse(list.getString(1))));
        }
    }

    /** 能做成嵌片的 14 对（有命中效果的），按余烬元素剑的表的顺序 */
    public static List<Pair> craftablePairs() {
        List<Pair> out = new ArrayList<>();
        for (EmberEffect.Combo combo : EmberEffect.COMBOS) {
            if (craftable(combo)) {
                out.add(Pair.of(combo.a(), combo.b()));
            }
        }
        return out;
    }

    /** 这一行能不能做成嵌片：有命中效果，而且不带反噬（「混沌 + 阴燃」是伤害 + 反噬，只剩代价，不做） */
    public static boolean craftable(EmberEffect.Combo combo) {
        return !combo.effects().isEmpty() && !(combo.damage() > 0 && combo.effects().equals(Set.of(EmberEffect.DAMAGE_SELF)));
    }

    // ------------------------------------------------------------------ 嵌片

    /** 一片记着这一对的嵌片 */
    public ItemStack create(Pair pair) {
        ItemStack stack = new ItemStack(this);
        stack.getOrCreateTag().put(EMBERS_TAG, pair.toTag());
        return stack;
    }

    /** 嵌片上记的那一对；不是嵌片、没记、记坏了为空 */
    public static Optional<Pair> embers(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof EmberInlayItem)) {
            return Optional.empty();
        }
        return Optional.ofNullable(Pair.fromTag(stack.getTag(), EMBERS_TAG));
    }

    // ------------------------------------------------------------------ 武器

    /** 武器上嵌着的那一对；没嵌、记坏了为空 */
    public static Optional<Pair> inlayOf(ItemStack weapon) {
        return weapon.isEmpty() ? Optional.empty() : Optional.ofNullable(Pair.fromTag(weapon.getTag(), INLAY_TAG));
    }

    /** 把这一对嵌进武器（原地改 NBT；旧的直接覆盖） */
    public static void setInlay(ItemStack weapon, Pair pair) {
        weapon.getOrCreateTag().put(INLAY_TAG, pair.toTag());
    }

    /** 能不能嵌（见类注释）：一次只嵌一件，叠在一起的不行 */
    public static boolean isInlayable(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() != 1) {
            return false;
        }
        Item item = stack.getItem();
        Predicate<ItemStack> extra = testBlacklist;
        if (item instanceof EmberInlayItem || item instanceof ForgedElementalSwordItem || stack.is(INLAY_BLACKLIST)
                || (extra != null && extra.test(stack))) {
            return false;
        }
        if (stack.is(INLAYABLE) || item instanceof ProjectileWeaponItem || item instanceof TridentItem || stack.is(Tags.Items.TOOLS)) {
            return true;
        }
        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            if (modifier.getAmount() > 0D) {
                return true;
            }
        }
        return false;
    }

    /** GameTest 用：把合 predicate 的物品当成黑名单里的；测完置回 null */
    public static void setTestBlacklist(@Nullable Predicate<ItemStack> predicate) {
        testBlacklist = predicate;
    }

    // ------------------------------------------------------------------ 提示、颜色、创造栏

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Optional<Pair> pair = embers(stack);
        if (pair.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.ember_inlay.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.ember_inlay.pair", emberName(pair.get().first()), emberName(pair.get().second()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.ember_inlay.effects", effectNames(pair.get())).withStyle(ChatFormatting.GOLD));
        tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.ember_inlay.use", Config.anvilLevels()));
        tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.ember_inlay.cost", Config.mruPerHit()));
    }

    /** 余烬的短名（酸蚀、混沌……），键 {@code tooltip.essentialcraft5.ember_kind.<种类>} */
    public static MutableComponent emberName(EmberKind kind) {
        return Component.translatable("tooltip.essentialcraft5.ember_kind." + kind.name().toLowerCase(Locale.ROOT)).withStyle(style -> style.withColor(TINTS[kind.ordinal()]));
    }

    /** 这一对的效果名，用「、」连起来（键同余烬元素剑） */
    public static MutableComponent effectNames(Pair pair) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (EmberEffect effect : pair.effects()) {
            if (!first) {
                out.append(Component.translatable("tooltip.essentialcraft5.forged_elemental_sword.separator"));
            }
            out.append(Component.translatable(effect.translationKey()));
            first = false;
        }
        return out;
    }

    /** 物品颜色：0 层（底图）不染，1 层按第一种余烬、2 层按第二种；没记的嵌片两层都是浅灰 */
    public static int tint(ItemStack stack, int tintIndex) {
        if (tintIndex <= 0) {
            return -1;
        }
        Optional<Pair> pair = embers(stack);
        if (pair.isEmpty()) {
            return 0xB0B0B0;
        }
        EmberKind kind = tintIndex == 1 ? pair.get().first() : pair.get().second();
        return TINTS[kind.ordinal()];
    }

    /** 创造栏：14 片能做出来的 */
    @Override
    public List<ItemStack> creativeVariants() {
        List<ItemStack> out = new ArrayList<>();
        for (Pair pair : craftablePairs()) {
            out.add(create(pair));
        }
        return out;
    }
}
