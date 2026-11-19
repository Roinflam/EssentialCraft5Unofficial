package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.EntryItems;
import com.flameforge.essentialcraft5.client.guide.GuideConditions;
import com.flameforge.essentialcraft5.config.GuideConfigSnapshot;
import com.flameforge.essentialcraft5.util.NumberText;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 研究手册的两处格式能力：英文千位分隔（{@link NumberText}、界面语言键 {@code fmt.group_sep}）
 * 和条目 {@code items} 的条件组（{@link EntryItems}）。
 * <p>
 * GameTest 服务器上没有手册界面的类，这里用的 {@link EntryItems}、{@link GuideConditions} 都不带 {@code @OnlyIn}，
 * 和界面里查阅索引、搜索用的是同一份解析和求值；配置快照取真实的 SERVER 配置（{@link GuideConfigSnapshot}），
 * 只把海纳尔之门的开关换成指定值。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GuideFormatTests {

    private static final String EMPTY = "empty";
    private static final String PORTAL = "general.hoanna.portal_enabled";
    private static final String GROUP_SEP = "guide.essentialcraft5.ui.fmt.group_sep";

    private GuideFormatTests() {
    }

    // ---------------------------------------------------------------- 工具

    /** 真实配置快照，海纳尔之门开关换成 portal */
    private static GuideConditions conditions(boolean portal, List<String> warnings) {
        Map<String, String> snapshot = new HashMap<>(GuideConfigSnapshot.capture());
        snapshot.put(PORTAL, Boolean.toString(portal));
        return new GuideConditions(snapshot, false, (key, message) -> warnings.add(message));
    }

    private static Path modResource(String... path) {
        return ModList.get().getModFileById(EssentialCraft5.MODID).getFile().findResource(path);
    }

    private static JsonObject readObject(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("读不了 " + file + "：" + e.getMessage(), e);
        }
    }

    /** 一个语言目录下的全部分类文件：文件名（不含 .json）→ 内容 */
    private static Map<String, JsonObject> book(String language) {
        Path dir = modResource("assets", EssentialCraft5.MODID, "guide", language);
        Map<String, JsonObject> out = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(files::add);
        } catch (IOException e) {
            throw new IllegalStateException("列不出 " + dir, e);
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            out.put(name.substring(0, name.length() - ".json".length()), readObject(file));
        }
        return out;
    }

    private static List<JsonObject> objects(JsonElement e) {
        List<JsonObject> out = new ArrayList<>();
        if (e instanceof JsonArray array) {
            for (JsonElement x : array) {
                if (x.isJsonObject()) {
                    out.add(x.getAsJsonObject());
                }
            }
        }
        return out;
    }

    private static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) {
            return out;
        }
        if (e.isJsonPrimitive()) {
            out.add(e.getAsString());
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (x != null && x.isJsonPrimitive()) {
                    out.add(x.getAsString());
                }
            }
        }
        return out;
    }

    private static boolean holds(GuideConditions conditions, JsonObject o, Runnable bad) {
        return conditions.test(GuideConditions.parse(o.get("if"), bad));
    }

    /** 只有门开着才拿得到的花纹方块（混凝土、虚空石、恶魔护板三种底材 × 16 种花纹）加虚空岩浆熔炉 */
    private static Set<String> hoannaOnlyItems() {
        Set<String> out = new TreeSet<>();
        for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
            String path = id.getPath();
            if (id.getNamespace().equals(EssentialCraft5.MODID) && (path.startsWith("fancy_concrete_")
                    || path.startsWith("fancy_void_stone_") || path.startsWith("fancy_demonic_plating_"))) {
                out.add(id.toString());
            }
        }
        out.add(EssentialCraft5.MODID + ":void_magmatic_furnace");
        return out;
    }

    // ---------------------------------------------------------------- 1. 千位分隔

    /** NumberText.group：整数部分 ≥ 4 位才分组，负号和小数部分照旧，空分隔符原样；语言键英文是逗号、中文（含繁体）是空串 */
    @GameTest(template = EMPTY)
    public static void guideNumberGrouping(GameTestHelper helper) {
        String[][] cases = {
                {"1234567.5", ",", "1,234,567.5"},
                {"-1000", ",", "-1,000"},
                {"999", ",", "999"},
                {"-999.75", ",", "-999.75"},
                {"1000", ",", "1,000"},
                {"123456", ",", "123,456"},
                {"12345.6789", ",", "12,345.6789"},
                {"0.123456", ",", "0.123456"},
                {"1234567.5", "", "1234567.5"},
                {"∞", ",", "∞"},
                {"?", ",", "?"},
                {"1234567", " ", "1 234 567"},
        };
        List<String> problems = new ArrayList<>();
        for (String[] c : cases) {
            String got = NumberText.group(c[0], c[1]);
            if (!c[2].equals(got)) {
                problems.add("group(\"" + c[0] + "\", \"" + c[1] + "\") 应为 " + c[2] + "，实际 " + got);
            }
        }
        if (NumberText.group("1234", null) == null || !NumberText.group("1234", null).equals("1234")) {
            problems.add("分隔符为 null 时应原样返回");
        }
        // 界面语言键：英文逗号；中文和繁体空串（不分组）。语言文件是 runData / make_traditional 的产物
        Map<String, String> expected = Map.of("en_us", ",", "zh_cn", "", "zh_tw", "", "zh_hk", "");
        for (Map.Entry<String, String> e : expected.entrySet()) {
            Path file = modResource("assets", EssentialCraft5.MODID, "lang", e.getKey() + ".json");
            if (!Files.exists(file)) {
                problems.add("没有 lang/" + e.getKey() + ".json（没跑 runData / make_traditional？）");
                continue;
            }
            JsonElement v = readObject(file).get(GROUP_SEP);
            if (v == null || !v.isJsonPrimitive() || !e.getValue().equals(v.getAsString())) {
                problems.add("lang/" + e.getKey() + ".json 的 " + GROUP_SEP + " 应为 \"" + e.getValue() + "\"，实际 " + v);
            }
        }
        helper.assertTrue(problems.isEmpty(), "千位分隔：" + problems);
        helper.succeed();
    }

    // ---------------------------------------------------------------- 2. 条件组的解析与过滤

    /** 条件组只在条件成立时算数；写坏的组按不成立处理（或整组跳过）并报问题，普通 id 不受影响 */
    @GameTest(template = EMPTY)
    public static void guideEntryItemGroupsFollowConditions(GameTestHelper helper) {
        String ns = EssentialCraft5.MODID + ":";
        JsonElement json = JsonParser.parseString("[\"" + ns + "magic_plating\","
                + "{\"if\": \"cfg:" + PORTAL + "\", \"items\": [\"" + ns + "void_stone\", \"" + ns + "void_glass\"]},"
                + "{\"if\": {\"bad\": true}, \"items\": [\"" + ns + "void_core\"]},"
                + "{\"items\": [\"" + ns + "void_fabric\"]},"
                + "{\"if\": \"!cfg:" + PORTAL + "\", \"items\": [[\"" + ns + "nested\"], \"" + ns + "pale_plating\"]},"
                + "null]");
        List<String> problems = new ArrayList<>();
        EntryItems items = EntryItems.parse(json, problems::add);
        // 写坏的：if 是对象、缺 if、组里嵌套数组、顶层的 null，各报一条
        helper.assertTrue(problems.size() == 4, "应报 4 处写坏的地方，实际 " + problems);

        List<String> warnings = new ArrayList<>();
        List<ResourceLocation> closed = items.visible(conditions(false, warnings)::test);
        List<ResourceLocation> open = items.visible(conditions(true, warnings)::test);
        helper.assertTrue(closed.equals(List.of(ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID, "magic_plating"),
                        ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID, "pale_plating"))),
                "门关着时应只有 magic_plating 和 !门 组里的 pale_plating，实际 " + closed);
        helper.assertTrue(open.equals(List.of(ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID, "magic_plating"),
                        ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID, "void_stone"),
                        ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID, "void_glass"))),
                "门开着时应是 magic_plating、void_stone、void_glass（if 写坏的组不成立、缺 if 的组跳过），实际 " + open);
        // 条件全当成立时拿到的就是解析出来的全部物品
        helper.assertTrue(!items.visible(c -> true).contains(ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID, "void_fabric")),
                "缺 if 的组应整组跳过");

        // 没有条件组时 visible 原样返回，不受条件求值影响
        EntryItems plain = EntryItems.parse(JsonParser.parseString("[\"" + ns + "magic_plating\"]"), problems::add);
        helper.assertTrue(plain.visible(c -> false).size() == 1, "没有条件组时 visible 应返回全部");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 3. 门关着时不进查阅索引

    /**
     * 海纳尔之门关着时，只有门开着才拿得到的 48 个花纹方块和虚空岩浆熔炉不进查阅索引（中英两份）：
     * 条目 items（按条件组过滤）、可见块里的配方产物 / 物品块 / 结构产物都不收它们；门开着时它们由
     * decorations/patterns、magmatic/furnace_upgrades 两篇收录。两篇里其余物品门开门关都在。
     */
    @GameTest(template = EMPTY)
    public static void guideHoannaItemsLeaveLookupWhenGateClosed(GameTestHelper helper) {
        Set<String> targets = hoannaOnlyItems();
        helper.assertTrue(targets.size() == 49, "应是 48 个花纹方块 + 虚空岩浆熔炉，实际 " + targets.size());
        List<String> problems = new ArrayList<>();
        for (String language : List.of("zh_cn", "en_us")) {
            Map<String, JsonObject> book = book(language);
            if (book.isEmpty()) {
                problems.add("jar 里没有 guide/" + language);
                continue;
            }
            for (boolean portal : new boolean[]{false, true}) {
                List<String> warnings = new ArrayList<>();
                GuideConditions conditions = conditions(portal, warnings);
                // 物品 → 第一次被索引的位置（条目 items 优先，其次各块），只收可见的分类 / 条目 / 块
                Map<String, String> entryIndex = new LinkedHashMap<>();
                Map<String, String> blockIndex = new LinkedHashMap<>();
                Set<String> patternsAndFurnace = new TreeSet<>();
                for (Map.Entry<String, JsonObject> cat : book.entrySet()) {
                    String where = language + "/" + cat.getKey();
                    if (!holds(conditions, cat.getValue(), () -> problems.add(where + " 的 if 格式不对"))) {
                        continue;
                    }
                    for (JsonObject entry : objects(cat.getValue().get("entries"))) {
                        String key = cat.getKey() + "/" + (entry.has("id") ? entry.get("id").getAsString() : "?");
                        if (!holds(conditions, entry, () -> problems.add(language + "/" + key + " 的 if 格式不对"))) {
                            continue;
                        }
                        EntryItems items = EntryItems.parse(entry.get("items"), p -> problems.add(language + "/" + key + " 的 items：" + p));
                        for (ResourceLocation id : items.visible(conditions::test)) {
                            entryIndex.putIfAbsent(id.toString(), key);
                            if (key.equals("decorations/patterns") || key.equals("magmatic/furnace_upgrades")) {
                                patternsAndFurnace.add(id.toString());
                            }
                        }
                        for (JsonObject b : objects(entry.get("blocks"))) {
                            if (!holds(conditions, b, () -> problems.add(language + "/" + key + " 有块的 if 格式不对"))) {
                                continue;
                            }
                            List<String> shown = new ArrayList<>();
                            String type = b.has("type") ? b.get("type").getAsString() : "";
                            if (type.equals("recipe")) {
                                shown.addAll(strings(b.get("item")));
                                shown.addAll(strings(b.get("items")));
                            } else if (type.equals("items")) {
                                shown.addAll(strings(b.get("items")));
                            } else if (type.equals("structure") || type.equals("ritual")) {
                                shown.addAll(strings(b.get("result")));
                            }
                            for (String id : shown) {
                                blockIndex.putIfAbsent(id, key);
                            }
                        }
                    }
                }
                String state = language + (portal ? " 门开" : " 门关");
                if (!warnings.isEmpty()) {
                    // 判定不了的条件按不成立处理，只会藏得更多；这里只记日志（配置键是否存在由 GuideDataTests 守着）
                    EssentialCraft5.LOGGER.info("[指南] {} 条件求值报警 {} 条：{}", state, warnings.size(), warnings);
                }
                for (String t : targets) {
                    if (!portal && (entryIndex.containsKey(t) || blockIndex.containsKey(t))) {
                        problems.add(state + "：" + t + " 仍在查阅索引里（" + entryIndex.getOrDefault(t, blockIndex.get(t)) + "）");
                    }
                    if (portal && !patternsAndFurnace.contains(t)) {
                        problems.add(state + "：" + t + " 不在 decorations/patterns 或 magmatic/furnace_upgrades 的条目 items 里");
                    }
                }
                // 其余 96 个花纹方块和两台岩浆熔炉不受门影响
                int others = 0;
                for (String id : patternsAndFurnace) {
                    if (!targets.contains(id)) {
                        others++;
                    }
                }
                if (others != 98) {
                    problems.add(state + "：两篇里门以外的物品应是 96 个花纹方块 + 2 台岩浆熔炉，实际 " + others);
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "条目 items 条件组：" + problems.size() + " 处，" + problems.subList(0, Math.min(5, problems.size())));
        helper.succeed();
    }

    // ---------------------------------------------------------------- 3. 列表取项 at:N

    private static final String TRAIT_LEVELS = "general.matrix_rank.resonance.trait_level_multipliers";
    private static final String STAGE_PRICES = "general.computer_armor.stage_price_multipliers";
    /** 手册正文里的 {cfg:键|格式链} */
    private static final Pattern CFG_TOKEN = Pattern.compile("[{]cfg:([A-Za-z0-9_.]+)([|][^}]*)?[}]");

    private static void expectNumber(GuideConditions cond, String key, int index, double expected, List<String> problems) {
        Object v = cond.element(key, index);
        if (!(v instanceof Double d) || Math.abs(d - expected) > 1.0E-9) {
            problems.add(key + " 的第 " + index + " 项应是 " + expected + "，实际 " + v);
        }
    }

    /**
     * 格式链 {@code at:N}（{@link GuideConditions#element}，界面的 GuideValues 用的同一个取值）：
     * 默认配置下相性倍数、宅男套装各阶单价取得到手册写的那几项；越界、负数、不是列表、没有这个键都取不到（界面显示「?」）；
     * 列表项按单个值的形状转成数字 / 布尔 / 字符串。再把四种语言手册里每一个 {@code at:N} 拿真实配置取一遍，
     * 矩阵章的特性耗电、宅男套装的各阶电费不许再写死（改配置后手册要跟着变）
     */
    @GameTest(template = EMPTY)
    public static void guideCfgListElement(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        GuideConditions cond = conditions(true, warnings);
        List<String> problems = new ArrayList<>();
        expectNumber(cond, TRAIT_LEVELS, 0, 1.0D, problems);
        expectNumber(cond, TRAIT_LEVELS, 1, 1.25D, problems);
        expectNumber(cond, TRAIT_LEVELS, 2, 1.5D, problems);
        expectNumber(cond, STAGE_PRICES, 1, 0.9D, problems);
        expectNumber(cond, STAGE_PRICES, 4, 0.8D, problems);
        if (cond.element(TRAIT_LEVELS, 3) != null || cond.element(TRAIT_LEVELS, -1) != null) {
            problems.add("越界、负数下标应取不到");
        }
        if (cond.element("general.matrix_rank.resonance.trait_mru_cost", 0) != null || cond.element("general.no_such_key", 0) != null) {
            problems.add("不是列表的键、没有的键应取不到");
        }
        Map<String, String> shapes = new HashMap<>();
        shapes.put("test.list", new Gson().toJson(List.of(2, true, "abc")));
        GuideConditions shaped = new GuideConditions(shapes, false, (key, message) -> warnings.add(message));
        if (!Double.valueOf(2D).equals(shaped.element("test.list", 0)) || !Boolean.TRUE.equals(shaped.element("test.list", 1))
                || !"abc".equals(shaped.element("test.list", 2))) {
            problems.add("列表项应按形状转成 2.0 / true / abc，实际 " + shaped.element("test.list", 0) + " / "
                    + shaped.element("test.list", 1) + " / " + shaped.element("test.list", 2));
        }

        int seen = 0;
        for (String language : List.of("zh_cn", "en_us", "zh_tw", "zh_hk")) {
            Path dir = modResource("assets", EssentialCraft5.MODID, "guide", language);
            List<Path> files = new ArrayList<>();
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().forEach(files::add);
            } catch (IOException e) {
                problems.add("列不出 " + dir + "：" + e.getMessage());
                continue;
            }
            for (Path file : files) {
                String text;
                try {
                    text = Files.readString(file);
                } catch (IOException e) {
                    problems.add("读不了 " + file + "：" + e.getMessage());
                    continue;
                }
                String where = language + "/" + file.getFileName();
                Matcher m = CFG_TOKEN.matcher(text);
                while (m.find()) {
                    String chain = m.group(2) == null ? "" : m.group(2);
                    for (String part : chain.split("[|]")) {
                        if (part.startsWith("at:")) {
                            seen++;
                            int index = Integer.parseInt(part.substring(3));
                            if (cond.element(m.group(1), index) == null) {
                                problems.add(where + "：" + m.group() + " 在当前配置下取不到");
                            }
                        } else if (part.startsWith("stage:") && cond.stageElement(m.group(1), Integer.parseInt(part.substring(6))) == null) {
                            problems.add(where + "：" + m.group() + " 在当前配置下取不到");
                        }
                    }
                }
                // 只盯宅男套装的单价：虚空套装 3 阶起 ×0.875 是代码常量（VoidArmorItem.DISCOUNT），没有配置列表可读，写成 mul:0.875 是对的
                if (file.getFileName().toString().equals("attunement.json") && text.contains("computer_armor.mru_per_damage|mul:0.")) {
                    problems.add(where + "：宅男套装各阶电费又写死了倍数，应读 " + STAGE_PRICES);
                }
                if (file.getFileName().toString().equals("matrix.json") && !text.contains("{cfg:general.matrix_rank.resonance.trait_mru_cost}")) {
                    problems.add(where + "：矩阵特性每次的耗电应读 general.matrix_rank.resonance.trait_mru_cost");
                }
            }
        }
        if (seen < 4 * 20) {
            problems.add("四种语言里的 at:N 只找到 " + seen + " 个，矩阵章和同调章应各有一批");
        }
        helper.assertTrue(problems.isEmpty(), "格式链 at:N：" + problems.size() + " 处，" + problems.subList(0, Math.min(5, problems.size())));
        helper.succeed();
    }

    /**
     * 按阶取值 {@code stage:N}（{@link GuideConditions#stageElement}，界面的 GuideValues 用的同一个取值）和条件里的 {@code |at:N} / {@code |stage:N}：
     * 列表比 N 短沿用最后一项（服主只填 {@code [0]} 时每一阶都是 0）、空列表 / 不是列表 / 负数取不到；{@code at:N} 越界照旧取不到；
     * 条件先取那一项再判、再比较，取不到按判定不了（取反也不成立），空白容忍，能和 {@code ||} 连用；真实配置下风之护甲 2 阶的空中跳次数取得到
     */
    @GameTest(template = EMPTY)
    public static void guideCfgStageElement(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> snapshot = new HashMap<>();
        snapshot.put("t.jumps", "[0]");
        snapshot.put("t.cd", "[120, 80]");
        snapshot.put("t.empty", "[]");
        snapshot.put("t.num", "3");
        GuideConditions c = new GuideConditions(snapshot, false, (key, message) -> warnings.add(message));
        List<String> problems = new ArrayList<>();
        if (!Double.valueOf(0D).equals(c.stageElement("t.jumps", 2)) || !Double.valueOf(120D).equals(c.stageElement("t.cd", 0))
                || !Double.valueOf(80D).equals(c.stageElement("t.cd", 3))) {
            problems.add("stage:N 取值不对：" + c.stageElement("t.jumps", 2) + " / " + c.stageElement("t.cd", 0) + " / " + c.stageElement("t.cd", 3));
        }
        if (c.stageElement("t.empty", 0) != null || c.stageElement("t.num", 0) != null || c.stageElement("t.cd", -1) != null
                || c.stageElement("t.none", 0) != null) {
            problems.add("空列表、不是列表、负数、没有的键应取不到");
        }
        if (c.element("t.cd", 3) != null) {
            problems.add("at:N 越界照旧取不到");
        }
        Map<String, Boolean> expect = new LinkedHashMap<>();
        expect.put("cfg:t.jumps|stage:2>0", false);
        expect.put("!cfg:t.jumps|stage:2>0", true);
        expect.put("cfg:t.jumps|stage:0", false);
        expect.put("cfg:t.cd|stage:3==80", true);
        expect.put("cfg:t.cd|at:1>=80", true);
        expect.put("cfg:t.cd|at:3>0", false);
        expect.put("!cfg:t.cd|at:3>0", false);
        expect.put("!cfg:t.empty|stage:0>0", false);
        expect.put("cfg: t.cd | stage:1 > 0", true);
        expect.put("cfg:t.jumps|stage:3>0 || cfg:t.cd|stage:0>100", true);
        expect.put("cfg:t.num|stage:0>0", false);
        for (Map.Entry<String, Boolean> e : expect.entrySet()) {
            if (c.test(e.getKey()) != e.getValue()) {
                problems.add(e.getKey() + " 应为 " + e.getValue());
            }
        }
        GuideConditions real = conditions(true, warnings);
        if (!Double.valueOf(2D).equals(real.stageElement("general.wind_armor.air_jumps", 2))
                || !real.test("cfg:general.wind_armor.air_jumps|stage:2>0") || !real.test("cfg:general.wind_armor.deflect_cooldown_ticks|stage:3>0")) {
            problems.add("默认配置下风之护甲 2 阶应能空中跳两次、3 阶偏转间隔大于 0");
        }
        helper.assertTrue(problems.isEmpty(), "stage:N 与条件取项：" + problems);
        helper.succeed();
    }

    /** 两篇风之护甲（wind_gear、attunement 两章里 id 是 wind_armor 的条目）的 blocks，每种语言各两份 */
    private static List<JsonElement> windArmorBlocks(String language, List<String> problems) {
        List<JsonElement> out = new ArrayList<>();
        for (String file : List.of("wind_gear.json", "attunement.json")) {
            JsonObject category = readObject(modResource("assets", EssentialCraft5.MODID, "guide", language, file));
            boolean found = false;
            for (JsonElement e : category.getAsJsonArray("entries")) {
                JsonObject entry = e.getAsJsonObject();
                if ("wind_armor".equals(entry.get("id").getAsString())) {
                    out.add(entry.get("blocks"));
                    found = true;
                }
            }
            if (!found) {
                problems.add(language + "/" + file + " 里没有 wind_armor 条目");
            }
        }
        return out;
    }

    /**
     * 风之护甲两篇按每一阶的配置显示和藏行（中英都查）：默认配置下空中跳次数、偏转间隔都看得见；{@code air_jumps} 填 {@code [0]} 时
     * 没有一句看得见的话再报空中跳次数，「空中跳」那一节也藏掉；{@code deflect_cooldown_ticks} 填 {@code [0]} 时不再写「每 0 秒」，
     * 换成每一颗都穿过去的那几行
     */
    @GameTest(template = EMPTY)
    public static void guideWindArmorStageRows(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Map<String, String> base = new HashMap<>(GuideConfigSnapshot.capture());
        Map<String, String> noJumps = new HashMap<>(base);
        noJumps.put("general.wind_armor.air_jumps", "[0]");
        Map<String, String> noCooldown = new HashMap<>(base);
        noCooldown.put("general.wind_armor.deflect_cooldown_ticks", "[0]");
        GuideConditions normal = new GuideConditions(base, false, (key, message) -> warnings.add(message));
        GuideConditions jumpsOff = new GuideConditions(noJumps, false, (key, message) -> warnings.add(message));
        GuideConditions every = new GuideConditions(noCooldown, false, (key, message) -> warnings.add(message));
        // 第一项是小节标题，后面是「每一颗都」的几种写法（中文阶表写「所有飞来的」、风之护甲篇写「每一个都」；英文有句首大写和句中小写两种，只比后半截）
        Map<String, String[]> words = Map.of("zh_cn", new String[]{"空中跳", "每一个都", "所有飞来的"}, "en_us", new String[]{"Midair jumps", "very incoming projectile"});
        for (String language : List.of("zh_cn", "en_us")) {
            String heading = words.get(language)[0];
            List<String> alls = List.of(words.get(language)).subList(1, words.get(language).length);
            List<JsonElement> blocks = windArmorBlocks(language, problems);
            List<String> jumpsNormal = new ArrayList<>();
            List<String> cooldownNormal = new ArrayList<>();
            List<String> allNormal = new ArrayList<>();
            List<String> jumpsHidden = new ArrayList<>();
            List<String> headingHidden = new ArrayList<>();
            List<String> cooldownZero = new ArrayList<>();
            List<String> allZero = new ArrayList<>();
            for (JsonElement b : blocks) {
                visibleMentions(b, normal, "air_jumps|stage:", language, jumpsNormal);
                visibleMentions(b, normal, "deflect_cooldown_ticks|stage:", language, cooldownNormal);
                for (String all : alls) {
                    visibleMentions(b, normal, all, language, allNormal);
                    visibleMentions(b, every, all, language, allZero);
                }
                visibleMentions(b, jumpsOff, "air_jumps|stage:", language, jumpsHidden);
                visibleMentions(b, jumpsOff, heading, language, headingHidden);
                visibleMentions(b, every, "deflect_cooldown_ticks|stage:", language, cooldownZero);
            }
            headingHidden.removeIf(s -> !s.equals(language + "：" + heading));
            if (jumpsNormal.size() < 3 || cooldownNormal.size() < 4 || !allNormal.isEmpty()) {
                problems.add(language + " 默认配置：空中跳 " + jumpsNormal.size() + " 处、偏转间隔 " + cooldownNormal.size() + " 处、「每一颗都」" + allNormal.size() + " 处");
            }
            if (!jumpsHidden.isEmpty() || !headingHidden.isEmpty()) {
                problems.add(language + " 空中跳关掉以后还看得见：" + jumpsHidden + headingHidden);
            }
            if (!cooldownZero.isEmpty() || allZero.size() < 4) {
                problems.add(language + " 间隔填 0：还在写间隔 " + cooldownZero + "，「每一颗都」只有 " + allZero.size() + " 处");
            }
        }
        helper.assertTrue(problems.isEmpty(), "风之护甲按阶显示：" + problems);
        helper.succeed();
    }

    // ---------------------------------------------------------------- 4. 凝结关掉时的凝结条目

    private static final String CONDENSE = "machines.enrichment_chamber.condense.enabled";
    private static final String CONDENSED_LINK = "[[pentacle/condensed";

    /** 一段 JSON 里条件成立、看得见的文字中含 target 的，记进 out（下划线开头的字段和 if 本身不算正文） */
    private static void visibleMentions(JsonElement e, GuideConditions cond, String target, String where, List<String> out) {
        if (e instanceof JsonObject o) {
            if (o.has("if") && !holds(cond, o, () -> out.add(where + " 的 if 写坏了"))) {
                return;
            }
            for (Map.Entry<String, JsonElement> kv : o.entrySet()) {
                if (!kv.getKey().startsWith("_") && !kv.getKey().equals("if")) {
                    visibleMentions(kv.getValue(), cond, target, where, out);
                }
            }
        } else if (e instanceof JsonArray array) {
            for (JsonElement x : array) {
                visibleMentions(x, cond, target, where, out);
            }
        } else if (e != null && e.isJsonPrimitive() && e.getAsString().contains(target)) {
            out.add(where + "：" + e.getAsString());
        }
    }

    /**
     * 富集仓凝结关掉（machines.enrichment_chamber.condense.enabled = false）时，第十二章的凝结矩阵条目藏起来，
     * 全书再没有一句看得见的话指向它（第七章富集仓、第十二章稳定器、第十五章五阶步骤、第十六章坍缩）；凝结开着时条目照常显示。
     * 中英两份都查，矩阵阶开着（门照常）
     */
    @GameTest(template = EMPTY)
    public static void guideCondenseOffHidesCondensed(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (boolean condense : new boolean[]{false, true}) {
            List<String> warnings = new ArrayList<>();
            Map<String, String> snapshot = new HashMap<>(GuideConfigSnapshot.capture());
            snapshot.put(PORTAL, "true");
            snapshot.put("general.matrix_rank.enabled", "true");
            snapshot.put(CONDENSE, Boolean.toString(condense));
            GuideConditions cond = new GuideConditions(snapshot, false, (key, message) -> warnings.add(message));
            for (String language : List.of("zh_cn", "en_us")) {
                Map<String, JsonObject> book = book(language);
                JsonObject pentacle = book.get("pentacle");
                if (pentacle == null) {
                    problems.add(language + " 没有 pentacle 章");
                    continue;
                }
                boolean found = false;
                for (JsonObject entry : objects(pentacle.get("entries"))) {
                    if ("condensed".equals(entry.has("id") ? entry.get("id").getAsString() : "")) {
                        found = true;
                        boolean shown = holds(cond, entry, () -> problems.add(language + " pentacle/condensed 的 if 写坏了"));
                        if (shown != condense) {
                            problems.add(language + "：凝结" + (condense ? "开着时凝结矩阵条目应显示" : "关掉时凝结矩阵条目应藏起来"));
                        }
                    }
                }
                if (!found) {
                    problems.add(language + " 没有 pentacle/condensed 条目");
                }
                if (condense) {
                    continue;
                }
                for (Map.Entry<String, JsonObject> cat : book.entrySet()) {
                    visibleMentions(cat.getValue(), cond, CONDENSED_LINK, language + "/" + cat.getKey(), problems);
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "凝结关掉时的手册：" + problems.size() + " 处，" + problems.subList(0, Math.min(5, problems.size())));
        helper.succeed();
    }
}
