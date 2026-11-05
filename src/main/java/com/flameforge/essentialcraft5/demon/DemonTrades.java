package com.flameforge.essentialcraft5.demon;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 恶魔交易池（原作 api/DemonTrade + RecipesCore.registerDemonTrades，写死在代码里，另有 CraftTweaker 接口）：改成数据包，
 * 读 {@code data/<命名空间>/demon_trades/*.json}，服主用数据包加条目、删条目、调权重（不做 CraftTweaker / JEI 联动）。
 *
 * <h2>文件格式</h2>
 * <pre>{@code
 * {"entries": [
 *   {"item": "minecraft:iron_block", "count": 8},
 *   {"soul": "minecraft:villager", "min": 1, "max": 7},
 *   {"item": "essentialcraft5:pistol", "count": 1, "any_damage": true, "weight": 2}
 * ],
 *  "remove": ["minecraft:tnt", {"soul": "minecraft:enderman"}]}
 * }</pre>
 * <ul>
 *   <li>物品条目：{@code count} 默认 1，夹到这种物品一组的上限以内（一次放得进交易格）；{@code any_damage} 默认 false（会损坏的要满耐久，见 {@link DemonWant}）。</li>
 *   <li>灵魂条目：数量在 {@code min}~{@code max} 之间随机（默认 1~7，原作 {@code nextInt(7) + 1}），夹到 1~64。被俘灵魂只能这样写：
 *       写成 {@code {"item": "essentialcraft5:captured_soul"}} 的条目不知道要哪种实体，跳过并警告。</li>
 *   <li>{@code weight}：抽中的权重，默认 1，最大 {@value #MAX_WEIGHT}（写得更大按上限算并警告，几条大权重加起来也不会溢出）；
 *       0 表示不抽（等于删掉）。</li>
 *   <li>{@code remove}：按物品 id（字符串或 {@code {"item": …}}）、灵魂实体 id（{@code {"soul": …}}）删掉<b>所有文件里</b>的同类条目，
 *       先收齐全部文件的条目再删，和文件的读入顺序无关。要整个换掉默认池就在数据包里覆盖 {@code essentialcraft5:demon_trades/default.json}。</li>
 *   <li>物品或实体没注册的条目跳过并记一条警告（装了别的模组才有的东西可以直接写进来）；灵魂条目的实体不在
 *       {@code essentialcraft5:soul_capturable} 标签里时也记警告（灵魂刻刀收不到它的灵魂）——标签要等数据加载完才绑定，所以这条在
 *       {@link TagsUpdatedEvent} 里查。</li>
 *   <li>字段类型写错的（数字加了引号、{@code entries} 写成对象、{@code soul} 写成数组……）同样只跳过那一条（或那一项）并警告，
 *       不影响别的条目和文件。原版 RecipeManager 也是逐条跳过；整个读取器抛出去的话，一处笔误就让专用服开不了、单人存档进不去。</li>
 * </ul>
 * 默认池 {@code default.json} = 原作 53 条删 8 条（基岩、末地传送门框架、三种命令方块、龙蛋、黑暗方尖碑、「永恒的夏日」唱片）后的 45 条。
 * 需求只在服务端召唤时掷一次（原作在构造器里两端都掷），存进恶魔的 NBT。
 *
 * <h2>熟客的第二样（第十三批 13a，batch13.md §3.4、D19 A）</h2>
 * 召唤者的契约之誉到了「熟客」（{@link DemonStanding}），恶魔同时开两样要价（{@link #rollFor}）：第一样照原池抽；第二样从「贵价池」抽，
 * 贵价池不另写文件，就是原池里乘得下的条目换个价（{@link #premium}）：数量 × {@code premium_multiplier}（默认 1.5，向上取整）。
 * 「乘得下」= 灵魂条目（夹到 64），或者物品条目乘完还放得进一组（{@link #premiumFits}；{@code essentialcraft5:demon_unstackable} 标签里的
 * 已填充地图、玩家头颅、成书每件 NBT 都不一样、叠不到一格，按一组 1 个算）；一组只有 1 个、本来就要满满一组的物品条目
 * 不进贵价池，不然一半的第二样会变成几个灵魂，比第一样还便宜（默认池 45 条里有 23 条乘不下）。
 * 第二样按权重从贵价池里和第一样不是同一种的条目抽；没有这样的条目时用第一样那条本身（池里只有一种），
 * 第一样也乘不下（整池都乘不下）才改要 {@value #PREMIUM_SOUL_COUNT} 个任意被俘灵魂（{@link DemonWant#anySoul}）。服主改原池，贵价池跟着变。
 */
public final class DemonTrades extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "demon_trades";
    /** 灵魂刻刀能收灵魂的实体（标签文件由灵魂刻刀一侧提供），这里只用来提醒服主 */
    public static final TagKey<EntityType<?>> SOUL_CAPTURABLE =
            TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id("soul_capturable"));
    public static final int DEFAULT_SOUL_MIN = 1;
    public static final int DEFAULT_SOUL_MAX = 7;
    public static final int MAX_SOUL_COUNT = 64;
    /** 单条权重上限（45 条默认池全按上限算也只有 4 500 万，抽签另用 long 累加兜底） */
    public static final int MAX_WEIGHT = 1_000_000;
    /** 整池都乘不下（没有一条进得了贵价池）时，第二样改要几个任意被俘灵魂（batch13.md §3.4） */
    public static final int PREMIUM_SOUL_COUNT = 2;

    /** 要写在 {@link #INSTANCE} 前面：静态字段按书写顺序初始化，构造器里要用到它 */
    private static final Gson GSON = new GsonBuilder().create();
    public static final DemonTrades INSTANCE = new DemonTrades();

    private static volatile List<Entry> entries = List.of();
    @Nullable
    private static List<Entry> testOverride;

    private DemonTrades() {
        super(GSON, DIRECTORY);
    }

    /**
     * 交易池里的一条。
     *
     * @param item      物品条目的物品（灵魂条目为 null）
     * @param soul      灵魂条目的实体 id（物品条目为 null）
     * @param count     物品条目的数量
     * @param min       灵魂条目数量下限
     * @param max       灵魂条目数量上限
     * @param anyDamage 物品条目不看耐久
     * @param weight    权重（≥ 1）
     */
    public record Entry(@Nullable Item item, @Nullable ResourceLocation soul, int count, int min, int max, boolean anyDamage, int weight) {

        public static Entry item(Item item, int count, boolean anyDamage, int weight) {
            return new Entry(item, null, count, 0, 0, anyDamage, weight);
        }

        public static Entry soul(ResourceLocation entity, int min, int max, int weight) {
            return new Entry(null, entity, 0, min, max, false, weight);
        }

        public boolean isSoul() {
            return soul != null;
        }

        /** 掷出这一条的具体需求（灵魂数量在 min~max 之间） */
        public DemonWant roll(RandomSource random) {
            if (soul != null) {
                return DemonWant.soul(soul, min + random.nextInt(max - min + 1));
            }
            Item wanted = item == null ? Items.AIR : item;
            // 文件读入时标签还没绑定，只按一组夹过；掷的时候再按「一次交得上几件」夹（已填充地图这类只要 1 件）
            return DemonWant.item(wanted, Math.min(count, DemonWant.deliverLimit(wanted)), anyDamage);
        }

        /** 物品条目返回物品 id，灵魂条目返回实体 id（删条目、测试用） */
        @Nullable
        public ResourceLocation id() {
            return soul != null ? soul : ForgeRegistries.ITEMS.getKey(item);
        }
    }

    /**
     * 解析结果。
     *
     * @param entries  最终的交易池（删过的）
     * @param warnings 跳过的条目、被夹住的数量等提示（已经写进日志）
     */
    public record Result(List<Entry> entries, List<String> warnings) {
    }

    // ------------------------------------------------------------------
    // 读取
    // ------------------------------------------------------------------

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Result result = parse(files);
        result.warnings().forEach(warning -> EssentialCraft5.LOGGER.warn("[恶魔交易] {}", warning));
        entries = result.entries();
        EssentialCraft5.LOGGER.info("[恶魔交易] 交易池 {} 条（{} 个文件）", entries.size(), files.size());
    }

    /** 把所有文件解析成交易池：先按文件 id 排序收齐条目，再统一删 remove 里点名的（纯函数，测试直接调） */
    public static Result parse(Map<ResourceLocation, JsonElement> files) {
        List<String> warnings = new ArrayList<>();
        List<Entry> collected = new ArrayList<>();
        List<ResourceLocation> removedItems = new ArrayList<>();
        List<ResourceLocation> removedSouls = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
            String where = file.getKey().toString();
            if (!file.getValue().isJsonObject()) {
                warnings.add(where + "：文件不是 JSON 对象，整个跳过");
                continue;
            }
            JsonObject root = file.getValue().getAsJsonObject();
            JsonArray list = arrayField(root, "entries", where, warnings);
            for (int i = 0; i < list.size(); i++) {
                String at = where + " entries[" + i + "]";
                try {
                    Entry entry = parseEntry(list.get(i), at, warnings);
                    if (entry != null) {
                        collected.add(entry);
                    }
                } catch (RuntimeException e) {
                    // GsonHelper 遇到类型不对（"8"、数组、null）抛 JsonSyntaxException；只跳这一条
                    warnings.add(at + "：" + e.getMessage() + "，跳过");
                }
            }
            JsonArray remove = arrayField(root, "remove", where, warnings);
            for (int i = 0; i < remove.size(); i++) {
                String at = where + " remove[" + i + "]";
                try {
                    parseRemove(remove.get(i), at, removedItems, removedSouls, warnings);
                } catch (RuntimeException e) {
                    warnings.add(at + "：" + e.getMessage() + "，跳过");
                }
            }
        }
        List<Entry> out = new ArrayList<>();
        for (Entry entry : collected) {
            boolean removed = entry.isSoul() ? removedSouls.contains(entry.soul()) : removedItems.contains(entry.id());
            if (!removed) {
                out.add(entry);
            }
        }
        return new Result(Collections.unmodifiableList(out), Collections.unmodifiableList(warnings));
    }

    /**
     * 读一个数组字段：没写当空数组；写了但不是数组（对象、字符串、null）记警告、也当空数组。
     * 先判断再取，不靠异常：entries 已经收进来、remove 才写错时，不会只收下半个文件。
     */
    private static JsonArray arrayField(JsonObject root, String name, String where, List<String> warnings) {
        JsonElement value = root.get(name);
        if (value == null) {
            return new JsonArray();
        }
        if (!value.isJsonArray()) {
            warnings.add(where + "：" + name + " 不是数组，跳过这一项");
            return new JsonArray();
        }
        return value.getAsJsonArray();
    }

    @Nullable
    private static Entry parseEntry(JsonElement element, String where, List<String> warnings) {
        if (!element.isJsonObject()) {
            warnings.add(where + "：不是对象，跳过");
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        int weight = GsonHelper.getAsInt(obj, "weight", 1);
        if (weight <= 0) {
            return null;
        }
        if (weight > MAX_WEIGHT) {
            warnings.add(where + "：weight " + weight + " 超过上限 " + MAX_WEIGHT + "，按 " + MAX_WEIGHT + " 算");
            weight = MAX_WEIGHT;
        }
        if (obj.has("soul")) {
            ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(obj, "soul", ""));
            if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
                warnings.add(where + "：实体 " + GsonHelper.getAsString(obj, "soul", "") + " 没有注册，跳过");
                return null;
            }
            int min = clamp(GsonHelper.getAsInt(obj, "min", DEFAULT_SOUL_MIN), 1, MAX_SOUL_COUNT);
            int max = clamp(GsonHelper.getAsInt(obj, "max", DEFAULT_SOUL_MAX), 1, MAX_SOUL_COUNT);
            return Entry.soul(id, Math.min(min, max), Math.max(min, max), weight);
        }
        if (obj.has("item")) {
            ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(obj, "item", ""));
            Item item = id == null || !ForgeRegistries.ITEMS.containsKey(id) ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                warnings.add(where + "：物品 " + GsonHelper.getAsString(obj, "item", "") + " 没有注册，跳过");
                return null;
            }
            if (item == ECDemonic.CAPTURED_SOUL.get()) {
                // 物品条目不知道要哪种实体：服务端收下任意灵魂，客户端界面却显示「它什么都不要」
                warnings.add(where + "：" + id + " 是被俘灵魂，请改用 {\"soul\": 实体 id} 写，跳过");
                return null;
            }
            int wanted = GsonHelper.getAsInt(obj, "count", 1);
            int stackLimit = new ItemStack(item).getMaxStackSize();
            int count = clamp(wanted, 1, stackLimit);
            if (count != wanted) {
                warnings.add(where + "：" + id + " 的数量 " + wanted + " 超出一组（" + stackLimit + "），按 " + count + " 算");
            }
            return Entry.item(item, count, GsonHelper.getAsBoolean(obj, "any_damage", false), weight);
        }
        warnings.add(where + "：既没有 item 也没有 soul，跳过");
        return null;
    }

    private static void parseRemove(JsonElement element, String where, List<ResourceLocation> items, List<ResourceLocation> souls,
                                    List<String> warnings) {
        if (element.isJsonPrimitive()) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id != null) {
                items.add(id);
                return;
            }
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            ResourceLocation soul = obj.has("soul") ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "soul", "")) : null;
            ResourceLocation item = obj.has("item") ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "item", "")) : null;
            if (soul != null) {
                souls.add(soul);
            }
            if (item != null) {
                items.add(item);
            }
            if (soul != null || item != null) {
                return;
            }
        }
        warnings.add(where + "：看不懂要删什么，跳过");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------
    // 使用
    // ------------------------------------------------------------------

    /** 当前交易池（测试覆盖优先） */
    public static List<Entry> entries() {
        List<Entry> override = testOverride;
        return override != null ? override : entries;
    }

    /**
     * 按权重抽一条、掷出需求；交易池是空的返回 null。
     * 权重用 long 累加：读取时单条已夹到 {@link #MAX_WEIGHT}，但条目极多（或测试直接塞的条目）时 int 仍会溢出成负数，那样就永远抽不出东西。
     */
    @Nullable
    public static DemonWant roll(RandomSource random) {
        Entry entry = pick(random);
        return entry == null ? null : entry.roll(random);
    }

    /** 按权重抽一条（不掷数量）；交易池是空的返回 null。随机数的用法和以前的 {@link #roll} 一样：先抽条目，掷数量的那次留给调用方 */
    @Nullable
    public static Entry pick(RandomSource random) {
        return pick(entries(), random);
    }

    /** 在给定的条目里按权重抽一条；都是 0 权重或空表返回 null */
    @Nullable
    private static Entry pick(List<Entry> pool, RandomSource random) {
        long total = 0L;
        for (Entry entry : pool) {
            total += Math.max(0, entry.weight());
        }
        if (total <= 0L) {
            return null;
        }
        // 合计放得进 int 时照旧用 nextInt（和以前同一串随机数）；放不进时取模，偏差小到可以不管
        long pick = total <= Integer.MAX_VALUE ? random.nextInt((int) total) : Math.floorMod(random.nextLong(), total);
        for (Entry entry : pool) {
            pick -= Math.max(0, entry.weight());
            if (pick < 0L) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 召出一只恶魔时给它的要价：一样，或者召唤者是熟客时两样（第二样是贵价，见类注释）。交易池是空的返回空列表。
     * player 为 null（刷怪蛋、指令召出来、召唤者不在线时补掷）按没有契约之誉算，只开一样。
     */
    public static List<DemonWant> rollFor(@Nullable Player player, RandomSource random) {
        return rollFor(DemonStanding.atLeast(player, DemonStanding.Tier.REGULAR), random);
    }

    /**
     * 同 {@link #rollFor(Player, RandomSource)}，开不开第二样由调用方定（血契替召唤者重抽时按召唤者算，见 {@code DemonEntity#bloodReroll}）。
     *
     * @param regular 按「熟客」开两样
     */
    public static List<DemonWant> rollFor(boolean regular, RandomSource random) {
        Entry first = pick(random);
        if (first == null) {
            return List.of();
        }
        DemonWant want = first.roll(random);
        if (!regular) {
            return List.of(want);
        }
        double factor = DemonStanding.Config.premiumMultiplier();
        Entry second = pickPremium(random, first, factor);
        return List.of(want, second == null ? DemonWant.anySoul(PREMIUM_SOUL_COUNT) : premium(second, random, factor));
    }

    /**
     * 第二样从哪条来：贵价池（{@link #premiumFits}）里和 first 不是同一种的条目按权重抽；一条都没有时用 first 本身（池里只有一种），
     * first 也乘不下时返回 null（调用方改要任意灵魂）。
     */
    @Nullable
    private static Entry pickPremium(RandomSource random, Entry first, double factor) {
        List<Entry> fits = new ArrayList<>();
        for (Entry entry : entries()) {
            if (!sameThing(entry, first) && premiumFits(entry, factor)) {
                fits.add(entry);
            }
        }
        Entry picked = pick(fits, random);
        if (picked != null) {
            return picked;
        }
        return premiumFits(first, factor) ? first : null;
    }

    private static boolean sameThing(Entry a, Entry b) {
        return a.isSoul() == b.isSoul() && Objects.equals(a.id(), b.id());
    }

    /**
     * 这一条进不进得了贵价池：灵魂条目总进得了（乘完夹到 {@link #MAX_SOUL_COUNT}）；物品条目乘完还一次交得上才算
     * （{@link DemonWant#deliverLimit}：一组的上限，已填充地图这类每件都不一样的只算 1 件，所以它们不进贵价池）
     */
    public static boolean premiumFits(Entry entry, double multiplier) {
        if (entry.isSoul()) {
            return true;
        }
        Item item = entry.item() == null ? Items.AIR : entry.item();
        return scaled(entry.count(), Math.max(1D, multiplier)) <= DemonWant.deliverLimit(item);
    }

    /**
     * 一条交易池条目的贵价要价：数量 × multiplier 向上取整；灵魂条目先在 min~max 里掷再乘（夹到 {@link #MAX_SOUL_COUNT}）。
     * {@link #rollFor} 只把乘得下的条目交给它；直接拿乘不下的物品条目来换算时，改要 {@value #PREMIUM_SOUL_COUNT} 个任意被俘灵魂。
     * multiplier 是 1 时和原价一样。
     */
    public static DemonWant premium(Entry entry, RandomSource random, double multiplier) {
        double factor = Math.max(1D, multiplier);
        if (entry.isSoul()) {
            int base = entry.min() + random.nextInt(entry.max() - entry.min() + 1);
            return DemonWant.soul(entry.soul(), Math.min(MAX_SOUL_COUNT, scaled(base, factor)));
        }
        Item item = entry.item() == null ? Items.AIR : entry.item();
        int count = scaled(entry.count(), factor);
        int limit = DemonWant.deliverLimit(item);
        if (count > limit) {
            return DemonWant.anySoul(PREMIUM_SOUL_COUNT);
        }
        return DemonWant.item(item, count, entry.anyDamage());
    }

    /** count × factor 向上取整（减一点点再取整：8 × 1.5 = 12 不会因为浮点误差变成 13），至少是原数 */
    private static int scaled(int count, double factor) {
        long scaled = (long) Math.ceil(count * factor - 1.0E-9D);
        return (int) Math.max(count, Math.min(Integer.MAX_VALUE, scaled));
    }

    /** GameTest 用：临时换掉交易池（null 恢复）。只在同一段同步代码里设、用、置回，别跨刻 */
    public static void setTestOverride(@Nullable List<Entry> pool) {
        testOverride = pool;
    }

    /** 数据包重载时登记读取器；标签绑定之后核对灵魂条目（Forge 总线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class DemonTradesEvents {

        private DemonTradesEvents() {
        }

        @SubscribeEvent
        public static void demonTradesReload(AddReloadListenerEvent event) {
            event.addListener(INSTANCE);
        }

        @SubscribeEvent
        public static void demonTradesTagCheck(TagsUpdatedEvent event) {
            if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
                return;
            }
            for (Entry entry : entries) {
                if (!entry.isSoul()) {
                    continue;
                }
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entry.soul());
                if (type != null && !type.is(SOUL_CAPTURABLE)) {
                    EssentialCraft5.LOGGER.warn("[恶魔交易] 灵魂条目 {} 不在 {} 标签里，灵魂刻刀收不到它的灵魂，这一单交不出来",
                            entry.soul(), SOUL_CAPTURABLE.location());
                }
            }
        }
    }
}
