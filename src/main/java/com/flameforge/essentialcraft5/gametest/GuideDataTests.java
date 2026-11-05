package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.GuideConditions;
import com.flameforge.essentialcraft5.client.guide.GuideSteps;
import com.flameforge.essentialcraft5.config.GuideConfigSnapshot;
import com.flameforge.essentialcraft5.player.ResearchBookGift;
import com.flameforge.essentialcraft5.registry.ECGuide;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 研究手册：内容文件和运行时的事实对不对得上（格式见 tools/guide/FORMAT.md）。
 * <p>
 * GameTest 服务器上没有客户端类，所以这里不碰手册界面的解析器，直接用 Gson 读 jar 里的
 * {@code assets/essentialcraft5/guide/<语言>/*.json}，逐项对照真实的配置规格、注册表、进度、配方表、结构登记。
 * 离线校验脚本（tools/guide/validate_guide.py）查格式，这里查「脚本看不到的运行时事实」。
 * 另外三条测送书（player/ResearchBookGift）：只送一次、背包满掉在脚下、死亡界面登录时跳过并在重生后补发。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GuideDataTests {

    private static final String EMPTY = "empty";
    private static final String GUIDE = "guide";
    private static final Pattern CFG_PLACEHOLDER = Pattern.compile("\\{cfg:\\s*([A-Za-z0-9_.]+)");
    private static final Pattern CFG_CONDITION = Pattern.compile("^!*\\s*cfg:\\s*([A-Za-z0-9_.]+)");
    private static final Pattern ITEM_PLACEHOLDER = Pattern.compile("\\{item:([^{}|]+)}");
    private static final Pattern BLOCK_PLACEHOLDER = Pattern.compile("\\{block:([^{}|]+)}");

    private GuideDataTests() {
    }

    // ---------------------------------------------------------------- 读内容

    /** 一个语言目录下的全部分类文件：文件名（不含 .json）→ 内容 */
    private static Map<String, JsonObject> book(String language) {
        Path dir = ModList.get().getModFileById(EssentialCraft5.MODID).getFile()
                .findResource("assets", EssentialCraft5.MODID, GUIDE, language);
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
            try (Reader reader = Files.newBufferedReader(file)) {
                out.put(name.substring(0, name.length() - ".json".length()), JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("读不了 " + file + "：" + e.getMessage(), e);
            }
        }
        return out;
    }

    /** 主语言（zh_cn）的整本书；一个分类都没有时直接判失败 */
    private static Map<String, JsonObject> mainBook(GameTestHelper helper) {
        Map<String, JsonObject> book = book("zh_cn");
        helper.assertTrue(!book.isEmpty(), "jar 里没有 assets/essentialcraft5/guide/zh_cn/*.json");
        return book;
    }

    private static List<JsonObject> entries(JsonObject category) {
        List<JsonObject> out = new ArrayList<>();
        if (category.get("entries") instanceof JsonArray array) {
            for (JsonElement e : array) {
                if (e.isJsonObject()) {
                    out.add(e.getAsJsonObject());
                }
            }
        }
        return out;
    }

    private static List<JsonObject> blocks(JsonObject entry) {
        List<JsonObject> out = new ArrayList<>();
        if (entry.get("blocks") instanceof JsonArray array) {
            for (JsonElement e : array) {
                if (e.isJsonObject()) {
                    out.add(e.getAsJsonObject());
                }
            }
        }
        return out;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    /** 字符串或字符串数组 */
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

    /** 条目 items 里的全部物品 id：条件组 {"if": …, "items": [...]} 展开，不管条件（FORMAT.md §1.2） */
    private static List<String> entryItems(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e instanceof JsonArray array) {
            for (JsonElement x : array) {
                if (x.isJsonObject()) {
                    out.addAll(strings(x.getAsJsonObject().get("items")));
                } else if (x.isJsonPrimitive()) {
                    out.add(x.getAsString());
                }
            }
        } else {
            out.addAll(strings(e));
        }
        return out;
    }

    /** 递归收集所有字符串值（跳过下划线开头的字段：_src 这类只给核对阶段看） */
    private static void collectStrings(JsonElement e, List<String> out) {
        if (e == null || e.isJsonNull()) {
            return;
        }
        if (e.isJsonPrimitive()) {
            if (e.getAsJsonPrimitive().isString()) {
                out.add(e.getAsString());
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                collectStrings(x, out);
            }
        } else if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> kv : e.getAsJsonObject().entrySet()) {
                if (!kv.getKey().startsWith("_")) {
                    collectStrings(kv.getValue(), out);
                }
            }
        }
    }

    /** 递归收集所有 if 条件里的每一项（数组展开、|| 拆开）；条目按条件可选的 optional（字符串 / 数组）写法同 if，一并收 */
    private static void collectConditions(JsonElement e, List<String> out) {
        if (e == null || e.isJsonNull() || e.isJsonPrimitive()) {
            return;
        }
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                collectConditions(x, out);
            }
            return;
        }
        for (Map.Entry<String, JsonElement> kv : e.getAsJsonObject().entrySet()) {
            if (kv.getKey().startsWith("_")) {
                continue;
            }
            boolean optionalCond = kv.getKey().equals("optional")
                    && !(kv.getValue().isJsonPrimitive() && kv.getValue().getAsJsonPrimitive().isBoolean());
            if (kv.getKey().equals("if") || optionalCond) {
                for (String cond : strings(kv.getValue())) {
                    for (String part : cond.split(Pattern.quote("||"))) {
                        out.add(part.trim());
                    }
                }
            } else {
                collectConditions(kv.getValue(), out);
            }
        }
    }

    private static void report(GameTestHelper helper, String what, List<String> problems) {
        if (problems.isEmpty()) {
            helper.succeed();
            return;
        }
        for (String p : problems) {
            EssentialCraft5.LOGGER.error("[指南] {}：{}", what, p);
        }
        helper.fail(what + "：" + problems.size() + " 处，第一处 " + problems.get(0));
    }

    // ---------------------------------------------------------------- 1. 配置键

    /** 所有 {cfg:K} 和条件里的 cfg:K，K 都在 EC5 的 SERVER 配置规格里 */
    @GameTest(template = EMPTY)
    public static void guideConfigKeysExist(GameTestHelper helper) {
        Set<String> keys = new LinkedHashSet<>(GuideConfigSnapshot.capture().keySet());
        helper.assertTrue(!keys.isEmpty(), "配置快照是空的（ECConfig.spec() 没建好？）");
        List<String> problems = new ArrayList<>();
        for (String language : List.of("zh_cn", "en_us")) {
            for (Map.Entry<String, JsonObject> cat : book(language).entrySet()) {
                List<String> strings = new ArrayList<>();
                collectStrings(cat.getValue(), strings);
                for (String s : strings) {
                    Matcher m = CFG_PLACEHOLDER.matcher(s);
                    while (m.find()) {
                        if (!keys.contains(m.group(1))) {
                            problems.add(language + "/" + cat.getKey() + "：{cfg:" + m.group(1) + "} 不是配置键");
                        }
                    }
                }
                List<String> conds = new ArrayList<>();
                collectConditions(cat.getValue(), conds);
                for (String c : conds) {
                    Matcher m = CFG_CONDITION.matcher(c);
                    if (m.find() && !keys.contains(m.group(1))) {
                        problems.add(language + "/" + cat.getKey() + "：条件 " + c + " 的键不存在");
                    }
                }
            }
        }
        report(helper, "手册里的配置键", problems);
    }

    // ---------------------------------------------------------------- 2. 配置键表

    /** datagen 导出的 guide/config_keys.json 和真实规格一致（改了配置没重跑 runData 时这里会挂） */
    @GameTest(template = EMPTY)
    public static void guideConfigKeyTableUpToDate(GameTestHelper helper) {
        Path file = ModList.get().getModFileById(EssentialCraft5.MODID).getFile()
                .findResource("assets", EssentialCraft5.MODID, GUIDE, "config_keys.json");
        helper.assertTrue(Files.exists(file), "没有 guide/config_keys.json（没跑 runData？）");
        JsonObject table;
        try (Reader reader = Files.newBufferedReader(file)) {
            table = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("keys");
        } catch (IOException | RuntimeException e) {
            helper.fail("读不了 config_keys.json：" + e.getMessage());
            return;
        }
        Set<String> real = new TreeSet<>(GuideConfigSnapshot.capture().keySet());
        Set<String> listed = new TreeSet<>(table.keySet());
        List<String> problems = new ArrayList<>();
        for (String k : real) {
            if (!listed.contains(k)) {
                problems.add("表里缺 " + k);
            }
        }
        for (String k : listed) {
            if (!real.contains(k)) {
                problems.add("表里多了 " + k + "（配置里已经没有）");
            }
        }
        report(helper, "config_keys.json 与配置规格（重跑 runData）", problems);
    }

    // ---------------------------------------------------------------- 3. 进度

    /** 条目 / 分类的 complete、advancement 都是真实存在的进度 */
    @GameTest(template = EMPTY)
    public static void guideAdvancementsExist(GameTestHelper helper) {
        var advancements = helper.getLevel().getServer().getAdvancements();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonObject> cat : mainBook(helper).entrySet()) {
            checkAdvancement(advancements, cat.getKey(), cat.getValue(), "advancement", problems);
            for (JsonObject entry : entries(cat.getValue())) {
                String where = cat.getKey() + "/" + str(entry, "id");
                checkAdvancement(advancements, where, entry, "complete", problems);
                checkAdvancement(advancements, where, entry, "advancement", problems);
            }
        }
        report(helper, "手册引用的进度", problems);
    }

    private static void checkAdvancement(net.minecraft.server.ServerAdvancementManager advancements, String where, JsonObject o,
                                         String field, List<String> problems) {
        String id = str(o, field);
        if (id == null) {
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || advancements.getAdvancement(rl) == null) {
            problems.add(where + " 的 " + field + " " + id + " 不存在");
        }
    }

    // ---------------------------------------------------------------- 4. 配方

    /** 非 optional 的配方块：每个产物至少有一条配方；recipe_type 已注册且有配方；recipe 这条配方存在 */
    @GameTest(template = EMPTY)
    public static void guideRecipeBlocksHaveRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RecipeManager manager = level.getRecipeManager();
        RegistryAccess access = level.registryAccess();
        Map<Item, Integer> byOutput = new LinkedHashMap<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            ItemStack out;
            try {
                out = recipe.getResultItem(access);
            } catch (RuntimeException e) {
                continue;
            }
            if (out != null && !out.isEmpty()) {
                byOutput.merge(out.getItem(), 1, Integer::sum);
            }
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonObject> cat : mainBook(helper).entrySet()) {
            for (JsonObject entry : entries(cat.getValue())) {
                List<JsonObject> blocks = blocks(entry);
                for (int i = 0; i < blocks.size(); i++) {
                    JsonObject b = blocks.get(i);
                    if (!"recipe".equals(str(b, "type")) || bool(b, "optional")) {
                        continue;
                    }
                    String where = cat.getKey() + "/" + str(entry, "id") + " 第 " + (i + 1) + " 块";
                    List<String> items = new ArrayList<>(strings(b.get("item")));
                    items.addAll(strings(b.get("items")));
                    String recipe = str(b, "recipe");
                    String type = str(b, "recipe_type");
                    if (recipe != null) {
                        ResourceLocation rl = ResourceLocation.tryParse(recipe);
                        if (rl == null || manager.byKey(rl).isEmpty()) {
                            problems.add(where + "：配方 " + recipe + " 不存在");
                        }
                        continue;
                    }
                    if (type != null) {
                        ResourceLocation rl = ResourceLocation.tryParse(type);
                        var recipeType = rl == null ? null : ForgeRegistries.RECIPE_TYPES.getValue(rl);
                        if (recipeType == null) {
                            problems.add(where + "：配方类型 " + type + " 没有注册");
                            continue;
                        }
                        Set<Item> wanted = new LinkedHashSet<>();
                        for (String id : items) {
                            Item item = item(id);
                            if (item != null) {
                                wanted.add(item);
                            }
                        }
                        boolean found = false;
                        for (Recipe<?> r : manager.getRecipes()) {
                            if (r.getType() != recipeType) {
                                continue;
                            }
                            if (wanted.isEmpty()) {
                                found = true;
                                break;
                            }
                            try {
                                if (wanted.contains(r.getResultItem(access).getItem())) {
                                    found = true;
                                    break;
                                }
                            } catch (RuntimeException ignored) {
                                // 取不到产物的配方不算
                            }
                        }
                        if (!found) {
                            problems.add(where + "：配方类型 " + type + " 下没有" + (wanted.isEmpty() ? "" : "产出这些物品的") + "配方");
                        }
                        continue;
                    }
                    for (String id : items) {
                        Item item = item(id);
                        if (item == null) {
                            problems.add(where + "：物品 " + id + " 不存在");
                        } else if (!byOutput.containsKey(item)) {
                            problems.add(where + "：找不到 " + id + " 的配方");
                        }
                    }
                }
            }
        }
        report(helper, "手册的配方块", problems);
    }

    // ---------------------------------------------------------------- 5. 结构

    /** structure 块写的结构 id 都在 StructurePatterns 里登记过 */
    @GameTest(template = EMPTY)
    public static void guideStructuresRegistered(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonObject> cat : mainBook(helper).entrySet()) {
            for (JsonObject entry : entries(cat.getValue())) {
                for (JsonObject b : blocks(entry)) {
                    String id = "structure".equals(str(b, "type")) ? str(b, "structure") : null;
                    if (id == null) {
                        continue;
                    }
                    ResourceLocation rl = ResourceLocation.tryParse(id);
                    if (rl == null || StructurePatterns.get(rl) == null) {
                        problems.add(cat.getKey() + "/" + str(entry, "id") + "：结构 " + id + " 没有登记");
                    }
                }
            }
        }
        report(helper, "手册的结构图", problems);
    }

    // ---------------------------------------------------------------- 6. 注册表

    /** 图标、条目 items、物品块、{item:}、{block:}、结构 / 仪式里的方块、实体预览都在注册表里 */
    @GameTest(template = EMPTY)
    public static void guideReferencesRegistered(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String language : List.of("zh_cn", "en_us")) {
            for (Map.Entry<String, JsonObject> cat : book(language).entrySet()) {
                String c = language + "/" + cat.getKey();
                checkItem(str(cat.getValue(), "icon"), c + " 的图标", problems);
                List<String> strings = new ArrayList<>();
                collectStrings(cat.getValue(), strings);
                for (String s : strings) {
                    Matcher m = ITEM_PLACEHOLDER.matcher(s);
                    while (m.find()) {
                        checkItem(m.group(1).trim(), c + " 的 {item:}", problems);
                    }
                    m = BLOCK_PLACEHOLDER.matcher(s);
                    while (m.find()) {
                        checkBlock(m.group(1).trim(), c + " 的 {block:}", problems);
                    }
                }
                for (JsonObject entry : entries(cat.getValue())) {
                    String where = c + "/" + str(entry, "id");
                    checkItem(str(entry, "icon"), where + " 的图标", problems);
                    for (String id : entryItems(entry.get("items"))) {
                        checkItem(id, where + " 的 items", problems);
                    }
                    for (JsonObject b : blocks(entry)) {
                        String type = str(b, "type");
                        if ("items".equals(type)) {
                            for (String id : strings(b.get("items"))) {
                                checkItem(id, where + " 的物品块", problems);
                            }
                        } else if ("ritual".equals(type) || "structure".equals(type)) {
                            checkItem(str(b, "throw"), where + " 的 throw", problems);
                            checkItem(str(b, "result"), where + " 的 result", problems);
                            checkKeys(b.get("key"), where, problems);
                            if (b.get("layers") instanceof JsonArray layers) {
                                for (JsonElement layer : layers) {
                                    if (layer.isJsonObject()) {
                                        checkKeys(layer.getAsJsonObject().get("key"), where, problems);
                                    }
                                }
                            }
                        } else if ("entity".equals(type)) {
                            String id = str(b, "id");
                            ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
                            if (rl == null || !ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                                problems.add(where + " 的实体 " + id + " 不存在");
                            }
                        }
                    }
                }
            }
        }
        report(helper, "手册引用的物品 / 方块 / 实体", problems);
    }

    private static void checkKeys(JsonElement keys, String where, List<String> problems) {
        if (!(keys instanceof JsonObject obj)) {
            return;
        }
        for (Map.Entry<String, JsonElement> kv : obj.entrySet()) {
            JsonElement v = kv.getValue();
            List<String> ids = new ArrayList<>();
            if (v.isJsonPrimitive()) {
                String s = v.getAsString();
                if (!s.equals("air") && !s.startsWith("#")) {
                    ids.add(s);
                }
            } else if (v.isJsonObject()) {
                ids.addAll(strings(v.getAsJsonObject().get("block")));
            }
            for (String id : ids) {
                checkBlock(id, where + " 的结构 key「" + kv.getKey() + "」", problems);
            }
        }
    }

    private static void checkItem(String id, String where, List<String> problems) {
        if (id != null && item(id) == null) {
            problems.add(where + "：物品 " + id + " 不存在");
        }
    }

    private static void checkBlock(String id, String where, List<String> problems) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.BLOCKS.containsKey(rl)) {
            problems.add(where + "：方块 " + id + " 不存在");
        }
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl != null && ForgeRegistries.ITEMS.containsKey(rl) ? ForgeRegistries.ITEMS.getValue(rl) : null;
        return item == null || item == Items.AIR ? null : item;
    }

    // ---------------------------------------------------------------- 7. 中英骨架

    /** zh_cn 与 en_us（以及生成的 zh_tw / zh_hk）骨架一致：同样的分类、条目 id、块类型与顺序 */
    @GameTest(template = EMPTY)
    public static void guideTranslationsSameSkeleton(GameTestHelper helper) {
        Map<String, JsonObject> zh = mainBook(helper);
        List<String> problems = new ArrayList<>();
        for (String language : List.of("en_us", "zh_tw", "zh_hk")) {
            Map<String, JsonObject> other = book(language);
            if (other.isEmpty() && !language.equals("en_us")) {
                continue;
            }
            if (!other.keySet().equals(zh.keySet())) {
                problems.add(language + " 的分类和 zh_cn 不一样：" + other.keySet() + " vs " + zh.keySet());
                continue;
            }
            for (Map.Entry<String, JsonObject> cat : zh.entrySet()) {
                String a = skeleton(cat.getValue());
                String b = skeleton(other.get(cat.getKey()));
                if (!a.equals(b)) {
                    problems.add(language + "/" + cat.getKey() + " 的条目 / 块结构和 zh_cn 不一样");
                }
            }
        }
        report(helper, "手册各语言的骨架", problems);
    }

    /** 分类的骨架：group、order、每个条目的 id / complete / optional（布尔或条件，比原样）/ items 与块类型序列 */
    private static String skeleton(JsonObject category) {
        StringBuilder sb = new StringBuilder();
        sb.append(str(category, "group")).append('|').append(category.get("order")).append('\n');
        for (JsonObject entry : entries(category)) {
            sb.append(str(entry, "id")).append('|').append(str(entry, "complete")).append('|').append(entry.get("optional"))
                    .append('|').append(entry.get("items")).append(':');
            for (JsonObject b : blocks(entry)) {
                sb.append(str(b, "type")).append(',');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 7b. 按条件可选（optional 收条件，batch10.md §2.5）

    private static final String PORTAL = "general.hoanna.portal_enabled";

    /**
     * 一本合成的小书，覆盖 optional 的各种写法：
     * <ul>
     *   <li>t_main1（主线）：a 不写 optional、b 门关着时可选、c 恒可选、d 恒必做、n 说明篇（没有 complete）</li>
     *   <li>t_main2（主线）：f 门关着时可选（数组写法）、g 恒可选 —— 门关着时这一章一步必做都没有，x/y 改数全部步骤</li>
     *   <li>t_side（支线，order 比主线小也排在主线后面）：h 门<b>开着</b>时可选（门关着时必做）</li>
     * </ul>
     */
    private static Map<String, JsonObject> syntheticBook() {
        String main1 = "{\"id\":\"t_main1\",\"order\":10,\"group\":\"main\",\"entries\":["
                + "{\"id\":\"a\",\"complete\":\"t:a\"},"
                + "{\"id\":\"b\",\"complete\":\"t:b\",\"optional\":\"!cfg:" + PORTAL + "\"},"
                + "{\"id\":\"c\",\"complete\":\"t:c\",\"optional\":true},"
                + "{\"id\":\"d\",\"complete\":\"t:d\",\"optional\":false},"
                + "{\"id\":\"n\"}]}";
        String main2 = "{\"id\":\"t_main2\",\"order\":20,\"group\":\"main\",\"entries\":["
                + "{\"id\":\"f\",\"complete\":\"t:f\",\"optional\":[\"!cfg:" + PORTAL + "\"]},"
                + "{\"id\":\"g\",\"complete\":\"t:g\",\"optional\":true}]}";
        String side = "{\"id\":\"t_side\",\"order\":5,\"group\":\"side\",\"entries\":["
                + "{\"id\":\"h\",\"complete\":\"t:h\",\"optional\":\"cfg:" + PORTAL + "\"}]}";
        Map<String, JsonObject> book = new LinkedHashMap<>();
        for (String json : List.of(side, main2, main1)) {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            book.put(str(o, "id"), o);
        }
        return book;
    }

    /** 真实配置快照，海纳尔之门开关换成 portal；条件求值的警告收进 warnings */
    private static GuideConditions gate(boolean portal, List<String> warnings) {
        Map<String, String> snapshot = new HashMap<>(GuideConfigSnapshot.capture());
        snapshot.put(PORTAL, Boolean.toString(portal));
        return new GuideConditions(snapshot, false, (key, message) -> warnings.add(message));
    }

    /**
     * 照手册界面（GuideView）走一遍的结果：分类按 group（入门 → 主线 → 支线）→ order → id 排，条件不成立的分类 / 条目不算，
     * 条目全被藏掉的分类不显示；每个可见条目交给 {@link GuideSteps.Tally}，第一个能当「下一步」的就是下一步。
     *
     * @param tallies 各分类的计数（按书中顺序）
     * @param next    下一步（分类/条目）；必做全做完为 null
     */
    private record Walk(Map<String, GuideSteps.Tally> tallies, String next) {
        int total(String cat) {
            return tallies.get(cat).total();
        }

        int done(String cat) {
            return tallies.get(cat).doneCount();
        }
    }

    private static Walk walk(Map<String, JsonObject> book, GuideConditions cond, Set<String> done, List<String> warnings) {
        List<JsonObject> cats = new ArrayList<>(book.values());
        cats.sort(Comparator.comparingInt((JsonObject c) -> groupRank(str(c, "group")))
                .thenComparingInt(c -> c.get("order") != null && c.get("order").isJsonPrimitive() ? c.get("order").getAsInt() : 1000)
                .thenComparing(c -> String.valueOf(str(c, "id"))));
        Map<String, GuideSteps.Tally> tallies = new LinkedHashMap<>();
        String next = null;
        for (JsonObject c : cats) {
            if (!cond.test(GuideConditions.parse(c.get("if"), () -> warnings.add(str(c, "id") + " 的 if 写坏了")))) {
                continue;
            }
            GuideSteps.Tally tally = new GuideSteps.Tally();
            boolean visible = false;
            for (JsonObject e : entries(c)) {
                String where = str(c, "id") + "/" + str(e, "id");
                if (!cond.test(GuideConditions.parse(e.get("if"), () -> warnings.add(where + " 的 if 写坏了")))) {
                    continue;
                }
                visible = true;
                String complete = str(e, "complete");
                boolean step = complete != null;
                boolean optional = GuideSteps.OptionalRule.parse(e.get("optional"), problem -> warnings.add(where + "：" + problem))
                        .test(cond::test);
                if (tally.add(step, step && !optional, step && done.contains(complete)) && next == null) {
                    next = where;
                }
            }
            if (visible) {
                tallies.put(str(c, "id"), tally);
            }
        }
        return new Walk(tallies, next);
    }

    /** 和 GuideContent.Group.of 一样：start、main 以外（包括没写）都按支线排 */
    private static int groupRank(String group) {
        return "start".equals(group) ? 0 : "main".equals(group) ? 1 : 2;
    }

    /** 门开着：按条件可选的 b、f 是必做，算进 x/y、能当「下一步」；门开着时可选的 h 是可选；支线排在所有主线后面 */
    @GameTest(template = EMPTY)
    public static void guideOptionalConditionGateOpen(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        GuideConditions open = gate(true, warnings);
        Map<String, JsonObject> book = syntheticBook();

        Walk w = walk(book, open, Set.of("t:a"), warnings);
        helper.assertTrue(List.copyOf(w.tallies().keySet()).equals(List.of("t_main1", "t_main2", "t_side")),
                "分类顺序应是 主线 10、主线 20、支线（order 5 也排在后面），实际 " + w.tallies().keySet());
        helper.assertTrue(w.done("t_main1") == 1 && w.total("t_main1") == 3,
                "门开着时 t_main1 数 a、b、d 三步必做，做了 a：应是 1/3，实际 " + w.done("t_main1") + "/" + w.total("t_main1"));
        helper.assertTrue("t_main1/b".equals(w.next()), "门开着时 b 是必做，做完 a 以后下一步应是 b，实际 " + w.next());
        helper.assertTrue(w.done("t_main2") == 0 && w.total("t_main2") == 1,
                "门开着时 t_main2 只有 f 必做：应是 0/1，实际 " + w.done("t_main2") + "/" + w.total("t_main2"));
        helper.assertTrue(w.total("t_side") == 1, "支线 h 门开着时可选，分类里没有必做步骤，x/y 数全部步骤：y 应是 1，实际 " + w.total("t_side"));

        Walk w2 = walk(book, open, Set.of("t:a", "t:b", "t:d"), warnings);
        helper.assertTrue("t_main2/f".equals(w2.next()), "第一章必做都做完后下一步应跳到 t_main2/f（c 恒可选不算），实际 " + w2.next());
        Walk w3 = walk(book, open, Set.of("t:a", "t:b", "t:d", "t:f"), warnings);
        helper.assertTrue(w3.next() == null, "门开着时 h 是可选的，必做全做完应没有下一步，实际 " + w3.next());
        helper.assertTrue(warnings.isEmpty(), "不应有警告：" + warnings);
        helper.succeed();
    }

    /** 门关着：b、f 变成可选，不算进 x/y、不当「下一步」；t_main2 没有必做步骤了，x/y 改数全部步骤；h 变成必做 */
    @GameTest(template = EMPTY)
    public static void guideOptionalConditionGateClosed(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        GuideConditions closed = gate(false, warnings);
        Map<String, JsonObject> book = syntheticBook();

        Walk w = walk(book, closed, Set.of("t:a"), warnings);
        helper.assertTrue(w.done("t_main1") == 1 && w.total("t_main1") == 2,
                "门关着时 t_main1 只数 a、d：应是 1/2，实际 " + w.done("t_main1") + "/" + w.total("t_main1"));
        helper.assertTrue("t_main1/d".equals(w.next()), "门关着时 b 可选，做完 a 以后下一步应跳过 b 指到 d，实际 " + w.next());
        helper.assertTrue(w.done("t_main2") == 0 && w.total("t_main2") == 2,
                "门关着时 t_main2 一步必做都没有，x/y 数 f、g 两步：应是 0/2，实际 " + w.done("t_main2") + "/" + w.total("t_main2"));
        helper.assertTrue(w.total("t_side") == 1, "门关着时 h 必做：y 应是 1，实际 " + w.total("t_side"));

        Walk w2 = walk(book, closed, Set.of("t:a", "t:d"), warnings);
        helper.assertTrue("t_side/h".equals(w2.next()), "主线必做做完后下一步应是支线里门关着时必做的 h，实际 " + w2.next());
        Walk w3 = walk(book, closed, Set.of("t:a", "t:d", "t:f"), warnings);
        helper.assertTrue(w3.done("t_main2") == 1 && w3.total("t_main2") == 2,
                "做完可选的 f：t_main2 应是 1/2，实际 " + w3.done("t_main2") + "/" + w3.total("t_main2"));

        // 和 if 同一套：键不存在时判定不了，按不成立处理 —— 这一步是必做（同样的条件写在 if 上则是藏掉）
        JsonObject broken = JsonParser.parseString("{\"id\":\"t_bad\",\"order\":1,\"group\":\"main\",\"entries\":["
                + "{\"id\":\"x\",\"complete\":\"t:x\",\"optional\":\"!cfg:general.no_such_key\"}]}").getAsJsonObject();
        List<String> condWarnings = new ArrayList<>();
        Walk wb = walk(Map.of("t_bad", broken), gate(false, condWarnings), Set.of(), warnings);
        helper.assertTrue("t_bad/x".equals(wb.next()), "条件里的键不存在时按必做处理，应被指成下一步，实际 " + wb.next());
        helper.assertTrue(!condWarnings.isEmpty(), "键不存在应报警告");
        helper.assertTrue(warnings.isEmpty(), "不应有别的警告：" + warnings);
        helper.succeed();
    }

    /**
     * 布尔照旧：true / false / 不写 在门开、门关下结果一样；写坏的（数字、对象、空串、空数组）按必做、报一次问题，字符串 "TRUE" 按布尔读。
     * 再对真实手册（zh_cn、en_us）：布尔 optional 两种情形下必做与否不变、等于「写了 complete 且不是 true」；
     * 条件写法的 optional 解析、求值都没有警告（键都在配置里）。
     */
    @GameTest(template = EMPTY)
    public static void guideOptionalBooleanUnchanged(GameTestHelper helper) {
        List<String> condWarnings = new ArrayList<>();
        GuideConditions open = gate(true, condWarnings);
        GuideConditions closed = gate(false, condWarnings);
        for (String raw : List.of("true", "false", "null")) {
            List<String> problems = new ArrayList<>();
            GuideSteps.OptionalRule rule = GuideSteps.OptionalRule.parse(JsonParser.parseString(raw), problems::add);
            boolean expected = raw.equals("true");
            helper.assertTrue(problems.isEmpty(), "optional " + raw + " 不该有问题：" + problems);
            helper.assertTrue(rule.test(open::test) == expected && rule.test(closed::test) == expected && !rule.conditional(),
                    "optional " + raw + " 应恒为" + (expected ? "可选" : "必做"));
        }
        for (String raw : List.of("1", "{}", "\"\"", "[]")) {
            List<String> problems = new ArrayList<>();
            GuideSteps.OptionalRule rule = GuideSteps.OptionalRule.parse(JsonParser.parseString(raw), problems::add);
            helper.assertTrue(!rule.test(open::test) && !rule.test(closed::test), "写坏的 optional " + raw + " 应按必做处理");
            helper.assertTrue(problems.size() == 1, "写坏的 optional " + raw + " 应报一次问题，实际 " + problems);
        }
        List<String> lenientProblems = new ArrayList<>();
        GuideSteps.OptionalRule lenient = GuideSteps.OptionalRule.parse(JsonParser.parseString("\"TRUE\""), lenientProblems::add);
        helper.assertTrue(lenientProblems.isEmpty() && lenient.test(closed::test) && lenient.test(open::test) && !lenient.conditional(),
                "字符串 \"TRUE\" 应按布尔 true 读（恒可选、不报问题）");

        List<String> problems = new ArrayList<>();
        for (String language : List.of("zh_cn", "en_us")) {
            for (Map.Entry<String, JsonObject> cat : book(language).entrySet()) {
                for (JsonObject e : entries(cat.getValue())) {
                    String where = language + "/" + cat.getKey() + "/" + str(e, "id");
                    JsonElement raw = e.get("optional");
                    GuideSteps.OptionalRule rule = GuideSteps.OptionalRule.parse(raw, p -> problems.add(where + "：" + p));
                    boolean step = str(e, "complete") != null;
                    boolean reqOpen = step && !rule.test(open::test);
                    boolean reqClosed = step && !rule.test(closed::test);
                    if (raw == null || raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isBoolean()) {
                        boolean expected = step && !(raw != null && raw.getAsBoolean());
                        if (reqOpen != expected || reqClosed != expected) {
                            problems.add(where + "：布尔 optional 的结果变了（门开 " + reqOpen + "、门关 " + reqClosed + "，应为 " + expected + "）");
                        }
                    }
                }
            }
        }
        helper.assertTrue(condWarnings.isEmpty(), "真实手册里按条件可选的条件求值有警告（键不存在？）：" + condWarnings);
        report(helper, "手册条目的 optional", problems);
    }

    // ---------------------------------------------------------------- 8. 查阅覆盖（只打日志）

    /** 本模组哪些物品在手册里查不到（Ctrl 查阅、潜行右键都不会有反应）。只打日志给人看，不判失败 */
    @GameTest(template = EMPTY)
    public static void guideLookupCoverageReport(GameTestHelper helper) {
        Set<String> indexed = new TreeSet<>();
        for (JsonObject category : mainBook(helper).values()) {
            for (JsonObject entry : entries(category)) {
                indexed.addAll(entryItems(entry.get("items")));
                for (JsonObject b : blocks(entry)) {
                    String type = str(b, "type");
                    if ("recipe".equals(type)) {
                        indexed.addAll(strings(b.get("item")));
                        indexed.addAll(strings(b.get("items")));
                    } else if ("items".equals(type)) {
                        indexed.addAll(strings(b.get("items")));
                    } else if ("ritual".equals(type) && str(b, "result") != null) {
                        indexed.add(str(b, "result"));
                    }
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> e : ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation id = e.getKey().location();
            if (id.getNamespace().equals(EssentialCraft5.MODID) && !indexed.contains(id.toString())) {
                missing.add(id.getPath());
            }
        }
        EssentialCraft5.LOGGER.info("[指南] 查阅覆盖：本模组 {} 个物品在手册里查不到（条目 items / 配方块 / 物品块 / 仪式产物都没写）：{}",
                missing.size(), missing);
        helper.succeed();
    }

    // ---------------------------------------------------------------- 9. 送书

    private static ServerPlayer giftPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "ec5-gift-test"));
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return player;
    }

    private static int books(Player player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ECGuide.RESEARCH_BOOK.get())) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static boolean marked(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return persisted.getBoolean(ResearchBookGift.TAG_GIVEN);
    }

    /** 送书只送一次 */
    @GameTest(template = EMPTY)
    public static void researchBookGivenOnce(GameTestHelper helper) {
        ServerPlayer player = giftPlayer(helper);
        helper.assertTrue(ResearchBookGift.giveIfFirstTime(player), "第一次应该送");
        helper.assertTrue(!ResearchBookGift.giveIfFirstTime(player), "第二次不该再送");
        helper.assertTrue(books(player) == 1, "背包里应有 1 本研究手册，实际 " + books(player));
        helper.assertTrue(marked(player), "送过之后应写下标记");
        helper.succeed();
    }

    /** 背包满：书掉在脚下（有意的做法，不是只提示），标记照写 */
    @GameTest(template = EMPTY)
    public static void researchBookDropsWhenInventoryFull(GameTestHelper helper) {
        ServerPlayer player = giftPlayer(helper);
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
        }
        helper.assertTrue(ResearchBookGift.giveIfFirstTime(player), "背包满时也算送了（掉在脚下）");
        helper.assertTrue(marked(player), "背包满时标记照写");
        helper.assertTrue(books(player) == 0, "背包是满的，书不该在背包里");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(3D),
                e -> e.getItem().is(ECGuide.RESEARCH_BOOK.get()));
        helper.assertTrue(drops.size() == 1, "脚下应该掉了 1 本研究手册，实际 " + drops.size());
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    /** 死亡界面登录：跳过、不写标记；重生（PlayerRespawnEvent）后补发 */
    @GameTest(template = EMPTY)
    public static void researchBookWaitsForRespawn(GameTestHelper helper) {
        ServerPlayer player = giftPlayer(helper);
        player.setHealth(0F);
        helper.assertTrue(!ResearchBookGift.giveIfFirstTime(player), "死着不该送");
        helper.assertTrue(!marked(player), "死着不该写标记");
        helper.assertTrue(books(player) == 0, "死着背包里不该有书");
        player.setHealth(player.getMaxHealth());
        ResearchBookGift.onRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));
        helper.assertTrue(books(player) == 1, "重生后应补发 1 本，实际 " + books(player));
        helper.assertTrue(marked(player), "补发后应写下标记");
        helper.succeed();
    }
}
