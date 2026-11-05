package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.network.S2CMatrixCodexPacket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 九阶表（第十三批 13a，batch13.md §3.1.3）：读 {@code data/<命名空间>/matrix_ranks/rank_<n>.json}（n = 1~9），数据包可改。
 * 每阶的图谱条件、站点、祭品、能量、回响数值都在这里；每阶给什么回报写在代码里（{@link MatrixRank}），不进数据。
 *
 * <h2>文件格式</h2>
 * <pre>{@code
 * {
 *   "codex": {"total": 21, "nether": 4, "expedition": 1, "include": ["essentialcraft5:wind_mage"]},
 *   "requires_alignment": false,
 *   "pentacle_tier": 1,
 *   "offerings": [{"item": "essentialcraft5:wind_gem", "count": 1}, {"tag": "essentialcraft5:essences", "count": 4}],
 *   "energy": {"ubmru": 0, "mru": 40000, "espe": 4000},
 *   "echoes": {"count": 6, "alive": 3, "health": 68, "damage": 8, "time_seconds": 110}
 * }
 * }</pre>
 * <ul>
 *   <li>{@code codex}：图谱各组至少几种。组名 {@code total}（总数）、{@code nether}（{@code essentialcraft5:codex_nether} 里的种类）、
 *       {@code hoanna}（在海纳尔杀过的种类）、{@code hoanna_natives}、{@code bosses}（{@code forge:bosses}）、{@code mod}（本模组的生物）、
 *       {@code end}、{@code expedition}；没写的组是 0。按标签数的组，标签里的种类不够数时按标签的实际种类数算（{@link Codex#required}）。
 *       {@code include} 是必须杀过的具体种类。</li>
 *   <li>{@code requires_alignment}：矩阵属性不能是中性（三阶）。</li>
 *   <li>{@code pentacle_tier}：站点要的灵魂五芒星档位，没写或 −1 = 淬炼台单独就行。</li>
 *   <li>{@code offerings}：最多 {@value #MAX_OFFERINGS} 项（淬炼台有 4 个祭品格），每项一种原料（照配方的 {@code item} / {@code tag} 写法）加数量，
 *       数量 1~64。</li>
 *   <li>{@code energy}：开始试炼时扣的 UBMRU（先扣试炼储备）、台子里的 MRU、五芒星柱子的有效 ESPE。</li>
 *   <li>{@code echoes}：矩阵回响总数、同时最多几只（最多 {@value #MAX_ALIVE}，写多了按 {@value #MAX_ALIVE} 算并警告）、生命、伤害、时限（秒）。</li>
 *   <li>{@code forge:conditions}：Forge 的加载条件（照配方的写法）。还没做出来的内容（八、九阶要的暗影核心、影晶、天灾残核）靠
 *       {@code forge:item_exists} 挡住，条件不满足的那一阶静默不加载。</li>
 * </ul>
 * <b>坏条目只跳过那一阶</b>：文件名不是 {@code rank_1}~{@code rank_9}、字段类型写错、物品没注册、数字越界的，那一阶读不到并记一条警告，
 * 别的阶照常；读不到的阶升不上去（试炼开始不了），已经升到的阶不受影响。同一阶在几个命名空间里都有时，按命名空间字母顺序后读的盖掉前面的并警告。
 * <p>
 * 只在服务端用（淬炼台界面要的「下一阶要什么」由服务端算好发 {@code S2CMatrixStatusPacket}）。
 */
public final class MatrixRankData extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "matrix_ranks";
    /** 一阶最多几项祭品（淬炼台的祭品格数） */
    public static final int MAX_OFFERINGS = 4;
    /** 单项祭品的数量上限（一组） */
    public static final int MAX_OFFERING_COUNT = 64;
    /** 单阶能量、回响数值的上限（防笔误写出天文数字） */
    public static final int MAX_AMOUNT = 100_000_000;
    /** 每场同时最多几只回响（policy §4.16）；数据包写多了按这个算 */
    public static final int MAX_ALIVE = 4;

    private static final Pattern FILE = Pattern.compile("rank_([1-9])");
    /** 要写在 {@link #INSTANCE} 前面：静态字段按书写顺序初始化，构造器里要用到它 */
    private static final Gson GSON = new GsonBuilder().create();
    public static final MatrixRankData INSTANCE = new MatrixRankData();

    private static volatile Map<Integer, Entry> ranks = Map.of();
    @Nullable
    private static Map<Integer, Entry> testOverride;

    private MatrixRankData() {
        super(GSON, DIRECTORY);
    }

    // ------------------------------------------------------------------ 数据

    /**
     * 图谱条件。按标签数的组（下界、海纳尔原生、首领、末地、远征）标签里种类不够时按实际种类数要求，见 {@link #required}。
     *
     * @param include 必须杀过的具体种类（实体类型 id）
     */
    public record Codex(int total, int nether, int hoanna, int hoannaNatives, int bosses, int mod, int end, int expedition,
                       List<ResourceLocation> include) {

        public static final Codex NONE = new Codex(0, 0, 0, 0, 0, 0, 0, 0, List.of());

        /** 这一组写的要求（没写是 0） */
        public int need(MatrixCodex.Group group) {
            return switch (group) {
                case TOTAL -> total;
                case NETHER -> nether;
                case HOANNA -> hoanna;
                case HOANNA_NATIVES -> hoannaNatives;
                case BOSSES -> bosses;
                case MOD -> mod;
                case END -> end;
                case EXPEDITION -> expedition;
            };
        }

        /** 实际要求：按标签数的组不超过标签里的种类数（13d 之前海纳尔原生标签是空的，这一组就不要求） */
        public int required(MatrixCodex.Group group) {
            int need = need(group);
            int tagSize = group.tagSize();
            return tagSize < 0 ? need : Math.min(need, tagSize);
        }
    }

    /**
     * 一项祭品。
     *
     * @param ingredient 原料（物品或标签）
     * @param count      要几个
     */
    public record Offering(Ingredient ingredient, int count) {

        /** 这一格里的东西满不满足这一项（种类对、数量够） */
        public boolean test(ItemStack stack) {
            return !stack.isEmpty() && ingredient.test(stack) && stack.getCount() >= count;
        }
    }

    /**
     * 回响的数值。
     *
     * @param count       一共要打完几只
     * @param alive       同时最多几只
     * @param health      每只的生命
     * @param damage      每下的伤害
     * @param timeSeconds 时限（秒）
     */
    public record Echoes(int count, int alive, int health, int damage, int timeSeconds) {
    }

    /**
     * 一阶的条件。
     *
     * @param rank              第几阶（1~9）
     * @param codex             图谱条件
     * @param requiresAlignment 矩阵属性不能是中性
     * @param pentacleTier      站点要的五芒星档位（−1 = 淬炼台单独就行）
     * @param offerings         祭品（最多 {@value #MAX_OFFERINGS} 项）
     * @param ubmru             UBMRU（先扣试炼储备、不够再扣身上的）
     * @param mru               台子里的 MRU
     * @param espe              五芒星柱子的有效 ESPE
     * @param echoes            回响
     */
    public record Entry(int rank, Codex codex, boolean requiresAlignment, int pentacleTier, List<Offering> offerings,
                        int ubmru, int mru, int espe, Echoes echoes) {

        /** 站点要不要五芒星 */
        public boolean needsPentacle() {
            return pentacleTier >= 0;
        }
    }

    // ------------------------------------------------------------------ 查询

    /** 第 rank 阶的条件；那一阶没读到（数据包删了、写坏了、还没做出来）返回 null */
    @Nullable
    public static Entry get(int rank) {
        Map<Integer, Entry> source = testOverride != null ? testOverride : ranks;
        return source.get(rank);
    }

    /** 读到的全部阶（按阶排） */
    public static Map<Integer, Entry> all() {
        Map<Integer, Entry> source = testOverride != null ? testOverride : ranks;
        return Collections.unmodifiableMap(new TreeMap<>(source));
    }

    /**
     * GameTest 用：临时换掉整张表（null 恢复读到的那份）。只许在同一段同步代码里设、测、清。
     */
    public static void setTestOverride(@Nullable Map<Integer, Entry> override) {
        testOverride = override == null ? null : Map.copyOf(override);
    }

    // ------------------------------------------------------------------ 读取

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Integer, Entry> loaded = load(files, MatrixRankData::warn);
        ranks = Map.copyOf(loaded);
        EssentialCraft5.LOGGER.info("[矩阵阶] 读到 {} 阶的条件：{}", loaded.size(), loaded.keySet());
    }

    /**
     * 把读到的文件变成九阶表（读取器和 GameTest 共用）：文件名不对、条件不满足的跳过，坏的那一阶跳过并警告。
     * 按 id 排序后读，同一阶在几个命名空间里都有时结果和读入顺序无关。
     */
    public static Map<Integer, Entry> load(Map<ResourceLocation, JsonElement> files, Consumer<String> warn) {
        Map<Integer, Entry> loaded = new TreeMap<>();
        Map<Integer, ResourceLocation> from = new TreeMap<>();
        new TreeMap<>(files).forEach((id, json) -> {
            Matcher matcher = FILE.matcher(id.getPath());
            if (!matcher.matches()) {
                warn.accept("文件名要写成 rank_1 ~ rank_9，跳过 " + id);
                return;
            }
            if (!ICondition.shouldRegisterEntry(json)) {
                return;
            }
            int rank = Integer.parseInt(matcher.group(1));
            Entry entry = parse(rank, json, message -> warn.accept(id + "：" + message + "，这一阶跳过"));
            if (entry == null) {
                return;
            }
            ResourceLocation previous = from.put(rank, id);
            if (previous != null) {
                warn.accept(id + " 盖掉了 " + previous + "（同是第 " + rank + " 阶）");
            }
            loaded.put(rank, entry);
        });
        return loaded;
    }

    private static void warn(String message) {
        EssentialCraft5.LOGGER.warn("[矩阵阶] {}", message);
    }

    /**
     * 解析一阶（读取器和 GameTest 共用）。写坏了返回 null，原因交给 warn（只报一次：第一处错就停）。
     */
    @Nullable
    public static Entry parse(int rank, JsonElement json, Consumer<String> warn) {
        try {
            if (!json.isJsonObject()) {
                throw new JsonParseException("整个文件应是一个对象");
            }
            JsonObject root = json.getAsJsonObject();
            Codex codex = root.has("codex") ? parseCodex(GsonHelper.getAsJsonObject(root, "codex")) : Codex.NONE;
            boolean alignment = GsonHelper.getAsBoolean(root, "requires_alignment", false);
            int tier = GsonHelper.getAsInt(root, "pentacle_tier", -1);
            if (tier < -1 || tier > 9) {
                throw new JsonParseException("pentacle_tier 应在 -1 ~ 9，写的是 " + tier);
            }
            List<Offering> offerings = new ArrayList<>();
            if (root.has("offerings")) {
                JsonArray array = GsonHelper.getAsJsonArray(root, "offerings");
                if (array.size() > MAX_OFFERINGS) {
                    throw new JsonParseException("祭品最多 " + MAX_OFFERINGS + " 项（淬炼台只有 " + MAX_OFFERINGS + " 格），写了 " + array.size() + " 项");
                }
                for (JsonElement element : array) {
                    offerings.add(parseOffering(element));
                }
            }
            JsonObject energy = root.has("energy") ? GsonHelper.getAsJsonObject(root, "energy") : new JsonObject();
            int ubmru = amount(energy, "ubmru", 0);
            int mru = amount(energy, "mru", 0);
            int espe = amount(energy, "espe", 0);
            if (!root.has("echoes")) {
                throw new JsonParseException("缺 echoes（回响数值）");
            }
            JsonObject echoes = GsonHelper.getAsJsonObject(root, "echoes");
            int alive = positive(echoes, "alive");
            if (alive > MAX_ALIVE) {
                // policy §4.16：每人同时 ≤ 4 只。写多了不算坏条目（这一阶照常能升），按上限算并提醒
                warn("第 " + rank + " 阶 echoes.alive 写了 " + alive + "，同时最多 " + MAX_ALIVE + " 只，按 " + MAX_ALIVE + " 算");
                alive = MAX_ALIVE;
            }
            Echoes echo = new Echoes(positive(echoes, "count"), alive, positive(echoes, "health"),
                    amount(echoes, "damage", 0), positive(echoes, "time_seconds"));
            return new Entry(rank, codex, alignment, tier, List.copyOf(offerings), ubmru, mru, espe, echo);
        } catch (RuntimeException e) {
            // JsonParseException、物品没注册（JsonSyntaxException）、类型写错（ClassCastException / IllegalStateException）
            warn.accept(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return null;
        }
    }

    private static Codex parseCodex(JsonObject json) {
        List<ResourceLocation> include = new ArrayList<>();
        if (json.has("include")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "include")) {
                if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
                    throw new JsonParseException("include 里应是实体 id 字符串：" + element);
                }
                ResourceLocation id = ResourceLocation.tryParse(primitive.getAsString());
                if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
                    throw new JsonParseException("include 里的实体没注册：" + primitive.getAsString());
                }
                include.add(id);
            }
        }
        for (String key : json.keySet()) {
            if (!key.equals("include") && MatrixCodex.Group.byKey(key) == null) {
                throw new JsonParseException("codex 里不认识的组：" + key);
            }
        }
        return new Codex(group(json, MatrixCodex.Group.TOTAL), group(json, MatrixCodex.Group.NETHER), group(json, MatrixCodex.Group.HOANNA),
                group(json, MatrixCodex.Group.HOANNA_NATIVES), group(json, MatrixCodex.Group.BOSSES), group(json, MatrixCodex.Group.MOD),
                group(json, MatrixCodex.Group.END), group(json, MatrixCodex.Group.EXPEDITION), List.copyOf(include));
    }

    private static int group(JsonObject json, MatrixCodex.Group group) {
        int value = GsonHelper.getAsInt(json, group.key(), 0);
        if (value < 0 || value > S2CMatrixCodexPacket.MAX_ENTRIES) {
            throw new JsonParseException("codex." + group.key() + " 应在 0 ~ " + S2CMatrixCodexPacket.MAX_ENTRIES + "，写的是 " + value);
        }
        return value;
    }

    private static Offering parseOffering(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new JsonParseException("祭品应是对象：" + element);
        }
        JsonObject json = element.getAsJsonObject();
        int count = GsonHelper.getAsInt(json, "count", 1);
        if (count < 1 || count > MAX_OFFERING_COUNT) {
            throw new JsonParseException("祭品数量应在 1 ~ " + MAX_OFFERING_COUNT + "，写的是 " + count);
        }
        JsonObject ingredientJson = json.deepCopy();
        ingredientJson.remove("count");
        Ingredient ingredient = Ingredient.fromJson(ingredientJson);
        return new Offering(ingredient, count);
    }

    private static int amount(JsonObject json, String key, int fallback) {
        int value = GsonHelper.getAsInt(json, key, fallback);
        if (value < 0 || value > MAX_AMOUNT) {
            throw new JsonParseException(key + " 应在 0 ~ " + MAX_AMOUNT + "，写的是 " + value);
        }
        return value;
    }

    private static int positive(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new JsonParseException("echoes 缺 " + key);
        }
        int value = GsonHelper.getAsInt(json, key);
        if (value < 1 || value > MAX_AMOUNT) {
            throw new JsonParseException("echoes." + key + " 应在 1 ~ " + MAX_AMOUNT + "，写的是 " + value);
        }
        return value;
    }

    /** 数据包重载时登记读取器（Forge 总线；类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class MatrixRankDataEvents {

        private MatrixRankDataEvents() {
        }

        @SubscribeEvent
        public static void matrixRankDataReload(AddReloadListenerEvent event) {
            event.addListener(INSTANCE);
        }
    }
}
