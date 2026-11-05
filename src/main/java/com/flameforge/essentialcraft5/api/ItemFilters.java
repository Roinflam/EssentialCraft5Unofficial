package com.flameforge.essentialcraft5.api;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.menu.GhostSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 过滤 API（batch4 F4-10）：过滤器物品（{@link IItemFilter}）的数据格式与判定。漏斗等只认这里，不 import 过滤器物品类。
 *
 * <h2>数据</h2>
 * 物品 NBT 键 {@value #TAG}：{@code {samples: [{Slot: b, item: {…}, tag?: "ns:path"} 或 {Slot: b, id, hash, damage, tag?}], ignore_damage, ignore_nbt, match_tags}}。
 * 样本格式和体积规则见 {@code GhostSlots}：单个样本 NBT &gt; 512 字节只存 id + 内容哈希（过滤器样本例外，整份留着）；整个物品 NBT ≤ 8 KB，超了放不进
 * （{@link #setSample} 返回 false）。黑白名单、普通 / 高级看物品本身（{@link IItemFilter}），不存 NBT。
 * 每格样本可以带一个选中的物品标签 {@code tag}（高级过滤器「按标签」用，见下）：没有这个键 = 还没选过；空字符串 = 明确选了「按物品」。
 *
 * <h2>判定（重写，原作 {@code ECUtils.canFilterAcceptItem} L618~679）</h2>
 * <ul>
 *   <li>槽空、或放的不是过滤器 = 全收（原作 {@code TileMagicalHopper} L68~70）。</li>
 *   <li>普通样本：{@link ItemStack#isSameItemSameTags}（哈希样本比 id + 哈希 + 耐久）。高级过滤器才读 {@link Flag} 的开关。</li>
 *   <li>高级过滤器打开「按标签」、这一格选了标签、样本现在还在这个标签里：候选在这个标签里就算「同一种」，代替物品同一性，
 *       NBT 照样要比（受「忽略耐久 / 忽略 NBT」管，同原作 L651）；没选标签的格子照旧按物品比。标签成员每次实时查（{@link ItemStack#is(TagKey)}），
 *       {@code /reload} 后不会用旧的成员表。</li>
 *   <li>样本本身是过滤器 = 「它放行的那组物品」（递归判定），<b>最多 {@value #MAX_DEPTH} 层</b>，更深的当不匹配；{@link #setSample} 也不让放进第三层。
 *       过滤器样本不按标签（每层用自己的开关）。</li>
 *   <li>白名单：任一样本匹配 → 收；黑名单：任一匹配 → 拒，否则收。<b>空白名单什么都不收，空黑名单什么都收</b>（原作 L679）。</li>
 * </ul>
 * 修掉的原作问题（改玩法）：黑名单只看第一格、第一格空时放行一切（L627~632）→ 看全部 9 格；
 * 高级白名单「忽略矿辞」时恒真（L641）、两件都没登记矿辞也算「矿辞相同」（L596~598）→ 改成每格选一个标签；
 * 套娃不限层、能套自己递归到栈溢出（L621~625）→ 两层；比较连数量一起比（样本 1 个、候选 64 个永远不等）→ 不比数量。
 *
 * <h2>按标签：每格选一个标签（原作的「矿辞」）</h2>
 * 原作第三个开关是「忽略矿辞」，默认关，也就是高级过滤器默认按矿辞比。这里反过来：开关叫「按标签」（{@link Flag#MATCH_TAGS}），默认关 = 按物品比，
 * 从普通过滤器升级上来行为不变、不会悄悄多收；要按标签分拣自己打开。
 * <ul>
 *   <li>打开开关时（{@link #setFlag}），还没选过标签的格子自动选一个默认标签（{@link #defaultTag}，相当于 1.12 的矿辞名）；开着时新放进去的样本也自动选。
 *       同一格换了物品就清掉原来的选择（{@link #setSamples}）。</li>
 *   <li>界面里鼠标指着样本格滚轮，在这件样本的全部标签（{@link #tagChoices}）和「按物品」之间切换（{@link #setSampleTag}，只收这件样本自己的标签）。</li>
 * </ul>
 *
 * <h2>机器怎么用</h2>
 * <pre>{@code
 * private final ItemFilters.Cache filterCache = new ItemFilters.Cache();
 * // 每次要判定时（过滤器对象和 NBT 没变就不重编）：
 * ItemFilters.Compiled filter = filterCache.get(inventory.getStackInSlot(FILTER_SLOT));
 * if (filter.test(item.getItem())) { … }                        // 漏斗：掉落物
 * if (filter.testBlock(state, level, cell)) { … }               // 按方块判断（取 getCloneItemStack；原作给高级方块破坏器用，那台机器已去掉）
 * }</pre>
 * 界面（过滤器物品的 {@code HeldItemMenu}）读写样本用 {@link #samples} / {@link #setSample}，开关用 {@link #flag} / {@link #setFlag}，
 * 每格的标签用 {@link #sampleTag} / {@link #effectiveTag} / {@link #setSampleTag} / {@link #tagChoices}；
 * 幽灵格的「肯收」规则用 {@link #canBeSample}（第三层过滤器放不进）。
 */
public final class ItemFilters {

    /** 过滤器物品标签：ECLogistics 的白 / 黑名单过滤器和两种高级过滤器都在里面。机器的过滤器格子按它放行 */
    public static final TagKey<Item> FILTERS = ItemTags.create(EssentialCraft5.id("filters"));
    /** 物品 NBT 里过滤器数据的键 */
    public static final String TAG = "ec5_filter";
    /** 3×3 */
    public static final int SLOTS = 9;
    /** 套娃最多两层（过滤器里放过滤器），更深的当不匹配 */
    public static final int MAX_DEPTH = 2;
    /** 整个过滤器物品 NBT 的上限（同幽灵格） */
    public static final int MAX_FILTER_BYTES = GhostSlots.MAX_ITEM_BYTES;
    /** 每格选中的标签 id 最长这么多字符，更长的标签不能选（读到也当没选） */
    public static final int MAX_TAG_LENGTH = 256;

    private static final String SAMPLES = "samples";
    private static final String SLOT = "Slot";
    private static final String SAMPLE_TAG = "tag";

    /**
     * 默认标签不考虑的「横切」分组：它们按开采层、矿石产率、护甲部位、工具用途把不同材料混在一起
     * （铁矿石和金矿石都在 {@code forge:ores_in_ground/stone}、{@code forge:ore_rates/singular} 里，七种头盔都在 {@code forge:armors/helmets} 里；
     * {@code forge:tools/} 也是按用途收一整类，整合包里常见的农夫乐事就把六种原版镐全放进了 {@code forge:tools/pickaxes}）。
     * 玩家照样能用滚轮手动选它们。
     */
    private static final List<String> CROSS_CUTTING_PREFIXES = List.of("ores_in_ground/", "ore_bearing_ground/", "ore_rates/", "armors/", "tools/");

    /** 什么都收（没装过滤器） */
    public static final Compiled ACCEPT_ALL = new Compiled(false, candidate -> true, true);

    /** 高级过滤器的开关（普通过滤器不读；界面上三个按钮按这个顺序） */
    public enum Flag {
        /** 不比耐久（原作「忽略 meta」，1.20 里只剩耐久） */
        IGNORE_DAMAGE("ignore_damage"),
        /** 只比物品（或标签），不比 NBT */
        IGNORE_NBT("ignore_nbt"),
        /** 按每格选中的标签匹配（原作「忽略矿辞」的反面，默认关；见类注释「按标签」） */
        MATCH_TAGS("match_tags");

        private final String key;

        Flag(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    private ItemFilters() {
    }

    // ------------------------------------------------------------------
    // 身份
    // ------------------------------------------------------------------

    /** 是不是过滤器物品（Item 实现 {@link IItemFilter}）。只在标签里、没实现接口的物品不算（拿不到黑白名单） */
    public static boolean isFilter(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IItemFilter;
    }

    /** 过滤器是不是黑名单；不是过滤器返回 false */
    public static boolean isBlacklist(ItemStack stack) {
        return isFilter(stack) && ((IItemFilter) stack.getItem()).isBlacklist();
    }

    /** 过滤器是不是高级版；不是过滤器返回 false */
    public static boolean isAdvanced(ItemStack stack) {
        return isFilter(stack) && ((IItemFilter) stack.getItem()).isAdvanced();
    }

    /**
     * 套娃层数：不是过滤器 0；过滤器 = 1 + 里面过滤器样本的最大层数（最多数到 {@link #MAX_DEPTH} + 1 就停，数据再深也不往下读）。
     */
    public static int depth(ItemStack stack) {
        return depth(stack, 0);
    }

    private static int depth(ItemStack stack, int above) {
        if (!isFilter(stack)) {
            return 0;
        }
        if (above >= MAX_DEPTH) {
            return 1;
        }
        int inner = 0;
        for (ItemStack sample : samples(stack)) {
            inner = Math.max(inner, depth(sample, above + 1));
        }
        return 1 + inner;
    }

    /** 这件物品能不能当样本放进一个过滤器：普通物品都能；过滤器只有层数 &lt; {@link #MAX_DEPTH} 的能（放进去后外层不超过两层） */
    public static boolean canBeSample(ItemStack stack) {
        return !isFilter(stack) || depth(stack) < MAX_DEPTH;
    }

    // ------------------------------------------------------------------
    // 判定
    // ------------------------------------------------------------------

    /** 过滤器格子里的东西放不放行 candidate：槽空或不是过滤器 = 全收。一次性判定用；机器反复判定用 {@link Cache} */
    public static boolean accepts(ItemStack filterSlot, ItemStack candidate) {
        return compile(filterSlot).test(candidate);
    }

    /**
     * 方块版（原作给高级方块破坏器用，那台机器已去掉，2026-09-28）：候选物品 = {@code state.getBlock().getCloneItemStack(level, pos, state)}（原作 {@code b.getItem(world, p, state)}）；
     * 拿不到物品形态（空气、液体、抛异常的模组方块）时白名单拒、黑名单收。
     */
    public static boolean acceptsBlock(ItemStack filterSlot, BlockState state, BlockGetter level, BlockPos pos) {
        return compile(filterSlot).testBlock(state, level, pos);
    }

    /** 把过滤器格子里的东西编译成判定器（槽空或不是过滤器 = {@link #ACCEPT_ALL}） */
    public static Compiled compile(ItemStack filterSlot) {
        if (!isFilter(filterSlot)) {
            return ACCEPT_ALL;
        }
        return compile(spec(filterSlot));
    }

    /** 从已经拆好的规格编译（测试用：不依赖过滤器物品，嵌套用 {@link Entry#filter}） */
    public static Compiled compile(Spec spec) {
        return new Compiled(spec.blacklist(), compileMatcher(spec, 1), false);
    }

    /**
     * 一个过滤器（第 depth 层）的判定：样本任一匹配 → 白名单收 / 黑名单拒。
     * 返回的是「这个过滤器放不放行」，被外层当成一个样本时，「匹配」= 它放行。
     */
    private static Predicate<ItemStack> compileMatcher(Spec spec, int depth) {
        boolean advanced = spec.advanced();
        boolean ignoreDamage = advanced && spec.flags().contains(Flag.IGNORE_DAMAGE);
        boolean ignoreNbt = advanced && spec.flags().contains(Flag.IGNORE_NBT);
        boolean matchTags = advanced && spec.flags().contains(Flag.MATCH_TAGS);
        List<Predicate<ItemStack>> matchers = new ArrayList<>(spec.entries().size());
        for (Entry entry : spec.entries()) {
            if (entry.nested() != null) {
                if (depth >= MAX_DEPTH) {
                    // 第三层及更深：当不匹配（不往下编，数据再深也不递归）
                    matchers.add(candidate -> false);
                } else {
                    matchers.add(compileMatcher(entry.nested(), depth + 1));
                }
            } else if (!entry.sample().isEmpty()) {
                ItemStack sample = entry.sample();
                TagKey<Item> tag = matchTags ? entry.tag() : null;
                if (tag == null) {
                    matchers.add(candidate -> GhostSlots.matches(sample, candidate, ignoreDamage, ignoreNbt));
                } else {
                    // 「样本还在这个标签里」每次现查：数据包把它移出标签后这一格退回按物品比，不会拿一个和样本无关的标签放行东西
                    matchers.add(candidate -> sample.is(tag)
                            ? !candidate.isEmpty() && candidate.is(tag) && GhostSlots.nbtMatches(sample, candidate, ignoreDamage, ignoreNbt)
                            : GhostSlots.matches(sample, candidate, ignoreDamage, ignoreNbt));
                }
            }
        }
        boolean blacklist = spec.blacklist();
        return candidate -> {
            for (Predicate<ItemStack> matcher : matchers) {
                if (matcher.test(candidate)) {
                    return !blacklist;
                }
            }
            // 没有一个匹配：白名单拒（含空白名单）、黑名单收（含空黑名单）
            return blacklist;
        };
    }

    /** 方块的物品形态；拿不到（空气、液体、模组方块抛异常）返回空 */
    public static ItemStack cloneItem(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack stack = state.getBlock().getCloneItemStack(level, pos, state);
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------
    // 读写数据
    // ------------------------------------------------------------------

    /** 9 格样本（没放的格子是空；哈希样本是带标记的无 NBT 物品，见 {@code GhostSlots}）。不是过滤器返回 9 个空 */
    public static NonNullList<ItemStack> samples(ItemStack filter) {
        NonNullList<ItemStack> out = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        Stored[] stored = stored(filter);
        for (int i = 0; i < SLOTS; i++) {
            out.set(i, stored[i].sample());
        }
        return out;
    }

    /**
     * 设第 slot 格的样本（空 = 清空）：存 {@code GhostSlots.sampleOf(sample)}（数量 1、大 NBT 转哈希），sample 本身不动。
     * 放不进时什么都不改、返回 false：不是过滤器、格子号越界、样本是第三层过滤器（{@link #canBeSample}）、改完整个物品 NBT 超过 {@link #MAX_FILTER_BYTES}。
     * 这一格的标签选择照 {@link #setSamples} 的规则处理。
     */
    public static boolean setSample(ItemStack filter, int slot, ItemStack sample) {
        if (!isFilter(filter) || slot < 0 || slot >= SLOTS || !canBeSample(sample)) {
            return false;
        }
        NonNullList<ItemStack> next = samples(filter);
        next.set(slot, sample);
        return setSamples(filter, next);
    }

    /**
     * 一次写整组样本（界面写回用），规则同 {@link #setSample}；任一格放不进就整组不写、返回 false。
     * 标签选择：同一格的样本没变（{@link ItemStack#matches}）就原样留着；换了物品就清掉；高级过滤器开着「按标签」时，换上来的样本自动选 {@link #defaultTag}。
     */
    public static boolean setSamples(ItemStack filter, List<ItemStack> samples) {
        if (!isFilter(filter) || samples.size() > SLOTS) {
            return false;
        }
        Stored[] old = stored(filter);
        boolean autoTag = isAdvanced(filter) && flag(filter, Flag.MATCH_TAGS);
        Stored[] next = new Stored[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            ItemStack raw = i < samples.size() ? samples.get(i) : ItemStack.EMPTY;
            if (!canBeSample(raw)) {
                return false;
            }
            ItemStack sample = GhostSlots.sampleOf(raw);
            String tag = null;
            if (!sample.isEmpty()) {
                if (ItemStack.matches(old[i].sample(), sample)) {
                    tag = old[i].tag();
                } else if (autoTag) {
                    TagKey<Item> chosen = defaultTag(sample);
                    tag = chosen == null ? null : chosen.location().toString();
                }
            }
            next[i] = new Stored(sample, tag);
        }
        return write(filter, next);
    }

    /**
     * 第 slot 格存着的标签选择：没选过、选了「按物品」、存的东西不是合法的标签 id（或超过 {@link #MAX_TAG_LENGTH} 字符）都返回 null。
     * 不管开关开没开、样本还在不在这个标签里（判定实际用的见 {@link #effectiveTag}）。
     */
    @Nullable
    public static TagKey<Item> sampleTag(ItemStack filter, int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return null;
        }
        return parseTag(stored(filter)[slot].tag());
    }

    /** 第 slot 格判定时实际用的标签：存着的选择（{@link #sampleTag}），并且这一格的样本现在还在这个标签里；否则 null（按物品比）。不看开关 */
    @Nullable
    public static TagKey<Item> effectiveTag(ItemStack filter, int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return null;
        }
        Stored stored = stored(filter)[slot];
        TagKey<Item> tag = parseTag(stored.tag());
        return tag != null && !stored.sample().isEmpty() && stored.sample().is(tag) ? tag : null;
    }

    /**
     * 选第 slot 格的标签（null = 明确选「按物品」）。只收这一格样本自己的标签（{@link #tagChoices} 里有的）；
     * 格子空着、样本是过滤器、标签不属于这件样本、改完超过 {@link #MAX_FILTER_BYTES} 时什么都不改、返回 false。
     * 界面的滚轮走这里（菜单在服务端按按钮号算出下一个标签，不收客户端给的字符串）。
     */
    public static boolean setSampleTag(ItemStack filter, int slot, @Nullable TagKey<Item> tag) {
        if (!isFilter(filter) || slot < 0 || slot >= SLOTS) {
            return false;
        }
        Stored[] stored = stored(filter);
        ItemStack sample = stored[slot].sample();
        if (sample.isEmpty() || isFilter(sample)) {
            return false;
        }
        if (tag != null && !tagChoices(sample).contains(tag)) {
            return false;
        }
        stored[slot] = new Stored(sample, tag == null ? "" : tag.location().toString());
        return write(filter, stored);
    }

    /**
     * 一件样本可以选的标签：它所在的全部物品标签，按 id 排序（id 超过 {@link #MAX_TAG_LENGTH} 字符的去掉）。
     * 空物品、过滤器样本（套娃按它放行的那组物品比，不按标签）返回空表。
     */
    public static List<TagKey<Item>> tagChoices(ItemStack sample) {
        if (sample.isEmpty() || isFilter(sample)) {
            return List.of();
        }
        return sample.getTags()
                .filter(tag -> tag.location().toString().length() <= MAX_TAG_LENGTH)
                .sorted(Comparator.comparing(tag -> tag.location().toString()))
                .toList();
    }

    /**
     * 默认标签（相当于 1.12 的矿辞名）：{@link #isMaterialTag} 认的「类别/材质」标签里取成员最少的一个（一样多按 id）；没有就返回 null（只按物品比）。
     * 例：铁锭 → {@code forge:ingots/iron}；铁矿石 → {@code forge:ores/iron}；白色染色玻璃 → {@code forge:glass/white}（不是收了 17 种玻璃的 {@code forge:glass/silica}）；
     * 橡木原木、铁头盔、弓 → 没有。不能「按 id 取第一个」：{@code forge:glass/silica} 按 id 排在 {@code glass/white} 前面，白色玻璃会默认收进所有玻璃。
     */
    @Nullable
    public static TagKey<Item> defaultTag(ItemStack sample) {
        TagKey<Item> best = null;
        int bestSize = Integer.MAX_VALUE;
        for (TagKey<Item> tag : tagChoices(sample)) {
            if (!isMaterialTag(tag)) {
                continue;
            }
            int size = tagSize(tag);
            // tagChoices 已按 id 排好，只在严格更少时替换 = 一样多时留 id 小的
            if (size < bestSize) {
                best = tag;
                bestSize = size;
            }
        }
        return best;
    }

    /** 标签里有几种物品（界面提示用；标签没绑定时 0） */
    public static int tagSize(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.getTag(tag).map(HolderSet::size).orElse(0);
    }

    /**
     * 能不能当默认标签（{@link #defaultTag} 的候选范围）：{@code forge:} / {@code c:} 下路径带 {@code /} 的「类别/材质」标签，
     * 去掉 {@link #CROSS_CUTTING_PREFIXES} 五组横切分组（{@code ores_in_ground/}、{@code ore_bearing_ground/}、{@code ore_rates/}、{@code armors/}、{@code tools/}）。
     * 只看 id，不看成员（测试直接拿它守这五组，不依赖当时装了哪些模组的标签成员）。
     */
    public static boolean isMaterialTag(TagKey<Item> tag) {
        String namespace = tag.location().getNamespace();
        String path = tag.location().getPath();
        if (!("forge".equals(namespace) || "c".equals(namespace)) || path.indexOf('/') < 0) {
            return false;
        }
        for (String prefix : CROSS_CUTTING_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static TagKey<Item> parseTag(@Nullable String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_TAG_LENGTH) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id == null ? null : TagKey.create(Registries.ITEM, id);
    }

    /** 读出 9 格（样本 + 标签选择的原始字符串：null = 没选过、"" = 按物品）。不是过滤器、没有数据时 9 格全空 */
    private static Stored[] stored(ItemStack filter) {
        Stored[] out = new Stored[SLOTS];
        Arrays.fill(out, Stored.EMPTY);
        CompoundTag data = data(filter);
        if (data == null) {
            return out;
        }
        ListTag list = data.getList(SAMPLES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte(SLOT) & 0xFF;
            if (slot < SLOTS) {
                ItemStack sample = GhostSlots.load(entry);
                String tag = entry.contains(SAMPLE_TAG, Tag.TAG_STRING) ? entry.getString(SAMPLE_TAG) : null;
                out[slot] = sample.isEmpty() ? Stored.EMPTY : new Stored(sample, tag);
            }
        }
        return out;
    }

    private static boolean write(ItemStack filter, Stored[] slots) {
        ListTag list = new ListTag();
        for (int i = 0; i < slots.length; i++) {
            ItemStack sample = slots[i].sample();
            if (!sample.isEmpty()) {
                CompoundTag entry = GhostSlots.save(sample);
                entry.putByte(SLOT, (byte) i);
                if (slots[i].tag() != null) {
                    entry.putString(SAMPLE_TAG, slots[i].tag());
                }
                list.add(entry);
            }
        }
        // 先写在副本上量体积，超了原物品一点不动
        ItemStack probe = filter.copy();
        CompoundTag data = probe.getOrCreateTagElement(TAG);
        if (list.isEmpty()) {
            data.remove(SAMPLES);
        } else {
            data.put(SAMPLES, list);
        }
        CompoundTag tag = probe.getTag();
        if (tag != null && tag.sizeInBytes() > MAX_FILTER_BYTES) {
            return false;
        }
        filter.setTag(tag);
        return true;
    }

    /** 一格的存档内容：样本，和标签选择的原始字符串（null = 没选过，"" = 明确选了按物品） */
    private record Stored(ItemStack sample, @Nullable String tag) {
        static final Stored EMPTY = new Stored(ItemStack.EMPTY, null);
    }

    /** 高级开关（数据里有就读，普通过滤器也能读到但判定时不用） */
    public static boolean flag(ItemStack filter, Flag flag) {
        CompoundTag data = data(filter);
        return data != null && data.getBoolean(flag.key());
    }

    /**
     * 设高级开关（不是过滤器什么都不做）。写在副本上再换掉整个 NBT 对象，机器的 {@link Cache} 才看得出变了。
     * 高级过滤器打开 {@link Flag#MATCH_TAGS} 时，还没选过标签的格子顺手选上 {@link #defaultTag}（明确选了「按物品」的不动）；
     * 选上以后超过 {@link #MAX_FILTER_BYTES} 的话只开开关、不选默认标签。
     */
    public static void setFlag(ItemStack filter, Flag flag, boolean value) {
        if (!isFilter(filter)) {
            return;
        }
        ItemStack probe = filter.copy();
        CompoundTag data = probe.getOrCreateTagElement(TAG);
        if (value) {
            data.putBoolean(flag.key(), true);
        } else {
            data.remove(flag.key());
        }
        if (flag == Flag.MATCH_TAGS && value && isAdvanced(probe)) {
            Stored[] slots = stored(probe);
            boolean changed = false;
            for (int i = 0; i < SLOTS; i++) {
                if (!slots[i].sample().isEmpty() && slots[i].tag() == null) {
                    TagKey<Item> chosen = defaultTag(slots[i].sample());
                    if (chosen != null) {
                        slots[i] = new Stored(slots[i].sample(), chosen.location().toString());
                        changed = true;
                    }
                }
            }
            if (changed) {
                // write 超上限时不改 probe，等于只开开关
                write(probe, slots);
            }
        }
        filter.setTag(probe.getTag());
    }

    @Nullable
    private static CompoundTag data(ItemStack filter) {
        CompoundTag tag = filter.getTag();
        return tag != null && tag.contains(TAG, Tag.TAG_COMPOUND) ? tag.getCompound(TAG) : null;
    }

    /** 把过滤器物品拆成规格（嵌套的过滤器样本一并拆开，最多拆到 {@link #MAX_DEPTH} + 1 层，再深的不读）；不是过滤器返回 null */
    @Nullable
    public static Spec spec(ItemStack filter) {
        return isFilter(filter) ? spec(filter, 1) : null;
    }

    private static Spec spec(ItemStack filter, int depth) {
        IItemFilter kind = (IItemFilter) filter.getItem();
        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
        for (Flag flag : Flag.values()) {
            if (flag(filter, flag)) {
                flags.add(flag);
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (Stored stored : stored(filter)) {
            ItemStack sample = stored.sample();
            if (sample.isEmpty()) {
                continue;
            }
            if (isFilter(sample)) {
                // 已经是第二层：再往里的过滤器判定时当不匹配，不再往下读，只占个位（数据再深也不递归）
                entries.add(new Entry(sample, depth >= MAX_DEPTH ? Spec.EMPTY_WHITELIST : spec(sample, depth + 1)));
            } else {
                entries.add(Entry.tagged(sample, parseTag(stored.tag())));
            }
        }
        return new Spec(kind.isBlacklist(), kind.isAdvanced(), flags, entries);
    }

    // ------------------------------------------------------------------
    // 类型
    // ------------------------------------------------------------------

    /**
     * 过滤器规格（拆开的 NBT）。{@link #compile(Spec)} 用它，测试可以直接构造（不需要注册过的过滤器物品）。
     *
     * @param flags 高级开关（{@code advanced} 为假时不起作用）
     */
    public record Spec(boolean blacklist, boolean advanced, Set<Flag> flags, List<Entry> entries) {
        /** 空白名单（什么都不收） */
        public static final Spec EMPTY_WHITELIST = new Spec(false, false, Set.of(), List.of());

        public Spec {
            flags = flags.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(flags));
            entries = List.copyOf(entries);
        }

        /** 普通白 / 黑名单，样本全是普通物品 */
        public static Spec of(boolean blacklist, ItemStack... samples) {
            List<Entry> entries = new ArrayList<>();
            for (ItemStack sample : samples) {
                entries.add(Entry.item(sample));
            }
            return new Spec(blacklist, false, Set.of(), entries);
        }
    }

    /**
     * 一格样本：普通物品（{@code nested} 为 null），或一个过滤器（{@code nested} = 它的规格，「匹配」= 它放行）。
     *
     * @param sample 样本物品（过滤器样本时是过滤器物品本身，判定只看 nested）
     * @param tag    这一格选中的标签（高级过滤器开着「按标签」才用；null = 按物品比）
     */
    public record Entry(ItemStack sample, @Nullable Spec nested, @Nullable TagKey<Item> tag) {

        public Entry(ItemStack sample, @Nullable Spec nested) {
            this(sample, nested, null);
        }

        public static Entry item(ItemStack sample) {
            return new Entry(GhostSlots.sampleOf(sample), null, null);
        }

        /** 带标签的普通样本（tag 为 null 时同 {@link #item}） */
        public static Entry tagged(ItemStack sample, @Nullable TagKey<Item> tag) {
            return new Entry(GhostSlots.sampleOf(sample), null, tag);
        }

        public static Entry filter(Spec nested) {
            return new Entry(ItemStack.EMPTY, nested, null);
        }
    }

    /**
     * 编译好的判定器：{@link #test} 判物品，{@link #testBlock} 判方块（拿不到物品形态时白名单拒、黑名单收）。
     * 不可变，可以在机器里缓存（{@link Cache}）。
     */
    public static final class Compiled implements Predicate<ItemStack> {
        private final boolean blacklist;
        private final Predicate<ItemStack> matcher;
        private final boolean acceptAll;

        private Compiled(boolean blacklist, Predicate<ItemStack> matcher, boolean acceptAll) {
            this.blacklist = blacklist;
            this.matcher = matcher;
            this.acceptAll = acceptAll;
        }

        @Override
        public boolean test(ItemStack candidate) {
            return acceptAll || matcher.test(candidate);
        }

        /** 方块：候选 = {@link ItemFilters#cloneItem}；拿不到物品形态时白名单拒、黑名单收（没装过滤器全收） */
        public boolean testBlock(BlockState state, BlockGetter level, BlockPos pos) {
            if (acceptAll) {
                return true;
            }
            ItemStack candidate = cloneItem(state, level, pos);
            return candidate.isEmpty() ? blacklist : matcher.test(candidate);
        }

        /** 没装过滤器（全收） */
        public boolean acceptsAll() {
            return acceptAll;
        }

        public boolean isBlacklist() {
            return blacklist;
        }
    }

    /**
     * 机器用的编译缓存：过滤器格子里的<b>对象</b>和它的 <b>NBT 对象</b>都没变就返回上次的结果
     * （同 {@code MRUBlockEntity} 的绑定宝石缓存）。{@link #setSample} / 物品栏换东西都会换掉对象，原地改 NBT 的代码要自己 {@link #invalidate}。
     */
    public static final class Cache {
        private ItemStack stack = ItemStack.EMPTY;
        @Nullable
        private CompoundTag tag;
        /** 同一个对象数量被扣到 0 时 isEmpty 会变，对象身份比不出来，另记一份 */
        private boolean empty = true;
        private Compiled compiled = ACCEPT_ALL;
        private boolean valid;

        public Compiled get(ItemStack filterSlot) {
            if (!valid || filterSlot != stack || filterSlot.getTag() != tag || filterSlot.isEmpty() != empty) {
                stack = filterSlot;
                tag = filterSlot.getTag();
                empty = filterSlot.isEmpty();
                compiled = compile(filterSlot);
                valid = true;
            }
            return compiled;
        }

        public void invalidate() {
            valid = false;
            stack = ItemStack.EMPTY;
            tag = null;
            empty = true;
        }
    }
}
