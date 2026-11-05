package com.flameforge.essentialcraft5.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Block;
import com.flameforge.essentialcraft5.client.guide.GuideContent.BlockType;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Book;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Category;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Entry;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Group;
import com.flameforge.essentialcraft5.client.guide.GuideContent.TipStyle;
import com.flameforge.essentialcraft5.EssentialCraft5;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 从资源包读取手册内容：{@code assets/essentialcraft5/guide/<语言>/<分类id>.json}（格式见 tools/guide/FORMAT.md）
 *
 * <h3>为什么走资源包而不是数据包</h3>
 * <p>内容是给客户端看的文字，本来就该跟着客户端的语言走；放在资源包里，整合包作者还能用自己的
 * 资源包覆盖某一篇（{@code listResources} 只返回最上层那份）。数值不写死在文字里，
 * 所以不需要服务端下发内容 —— 服务端只下发配置快照（{@link GuideClientConfig}）。</p>
 *
 * <h3>语言回退</h3>
 * <p>按分类逐个回退，而不是整本书回退：某个语言只翻了一半时，翻了的分类照常用译文，
 * 没翻的分类才回退。{@code zh_*} 先回退简体中文再回退英文，其余语言先英文后中文 ——
 * 繁体玩家读简体比读英文轻松。</p>
 *
 * <h3>宽容解析</h3>
 * <p>这本书是玩家进服后第一个打开的东西，任何一个写坏的块都不能让它打不开。
 * 所以坏掉的最小单位只跳过它自己：坏块跳过块、坏条目跳过条目、坏文件跳过分类，
 * 每种问题只报一次（{@link GuideLog}）。下划线开头的字段（{@code _src} 等）一律忽略，
 * 那是给核对阶段看的。</p>
 *
 * <p>结果按语言缓存。资源重载（F3+T、切换语言都会触发）时由 {@link GuideReloadListener}
 * 调 {@link #invalidate()} 丢掉；界面通过 {@link #generation()} 发现内容换了。</p>
 *
 * <p>同一目录上一层的 {@code guide/config_keys.json}（datagen 导出的配置键表，给校验脚本用）和旧格式留下的
 * {@code guide/<分类>.json} 都不在语言目录里，这里不会读到。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class GuideContentLoader {

    private static final String ROOT = "guide";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    @Nullable
    private static Book cached;
    @Nullable
    private static String cachedLanguage;
    private static int generation;

    private GuideContentLoader() {}

    /**
     * 当前语言的整本书（首次调用时加载）
     *
     * @return 书；一个分类都读不到时是空书，不会是 null
     */
    @Nonnull
    public static synchronized Book get() {
        String language = currentLanguage();
        if (cached == null || !language.equals(cachedLanguage)) {
            long start = System.nanoTime();
            try {
                cached = load(Minecraft.getInstance().getResourceManager(), language);
            } catch (RuntimeException e) {
                // 单个文件的问题在 load 里已经各自兜住了，能走到这里的是资源管理器本身出了意外。
                // 缓存一本空书而不是往上抛：get() 每帧都会被调，抛出去就是每帧一次异常、界面打不开
                GuideLog.warnOnce("load:" + language, "读取指南内容失败，本次显示为空: " + e);
                cached = Book.empty(language);
            }
            cachedLanguage = language;
            generation++;
            // _draft：机械转换出来、还没按写法要求改写的分类（只记日志，界面不区分）
            long drafts = cached.categories.stream().filter(c -> c.draft).count();
            EssentialCraft5.LOGGER.debug(String.format("[指南] 读取 %d 个分类（语言 %s，其中草稿 %d 个），耗时 %.1f ms",
                    cached.categories.size(), language, drafts, (System.nanoTime() - start) / 1e6));
        }
        return cached;
    }

    /**
     * 内容版本号：每次重新加载 +1。界面拿它和自己手上那份比，不一样就重建。
     *
     * @return 版本号
     */
    public static synchronized int generation() {
        return generation;
    }

    /** 丢掉缓存：资源重载时调用，下次 {@link #get()} 重新读 */
    public static synchronized void invalidate() {
        cached = null;
        cachedLanguage = null;
        generation++;
        GuideLog.reset();
    }

    /**
     * 当前客户端语言代码（小写，如 {@code zh_cn}）
     *
     * @return 语言代码
     */
    @Nonnull
    public static String currentLanguage() {
        String selected = Minecraft.getInstance().getLanguageManager().getSelected();
        return selected == null ? "en_us" : selected.toLowerCase(Locale.ROOT);
    }

    /**
     * 回退顺序
     *
     * @param language 当前语言
     * @return 依次尝试的语言目录
     */
    static List<String> fallbackChain(String language) {
        List<String> chain = new ArrayList<>(3);
        chain.add(language);
        if (language.startsWith("zh_")) {
            chain.add("zh_cn");
            chain.add("en_us");
        } else {
            chain.add("en_us");
            chain.add("zh_cn");
        }
        return chain.stream().distinct().toList();
    }

    // ==================== 加载 ====================

    private static Book load(ResourceManager manager, String language) {
        // 分类 id → (语言, 资源)。按回退顺序 putIfAbsent，靠前的语言优先
        Map<String, ResourceLocation> chosen = new LinkedHashMap<>();
        Map<String, String> chosenLang = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = new LinkedHashMap<>();
        for (String lang : fallbackChain(language)) {
            String dir = ROOT + "/" + lang;
            Map<ResourceLocation, Resource> found;
            try {
                found = manager.listResources(dir, rl -> rl.getPath().endsWith(".json"));
            } catch (RuntimeException e) {
                GuideLog.warnOnce("list:" + dir, "列出 " + dir + " 失败: " + e);
                continue;
            }
            for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
                ResourceLocation rl = e.getKey();
                if (!EssentialCraft5.MODID.equals(rl.getNamespace())) {
                    continue;
                }
                // 只认目录下的直接子文件：guide/zh_cn/start.json
                // （先确认前缀再截：别的资源包实现返回的路径不带这个前缀时，substring 会直接抛出）
                String path = rl.getPath();
                if (!path.startsWith(dir + "/")) {
                    continue;
                }
                String rest = path.substring(dir.length() + 1);
                if (rest.indexOf('/') >= 0) {
                    continue;
                }
                String id = rest.substring(0, rest.length() - ".json".length());
                if (!ID.matcher(id).matches()) {
                    GuideLog.warnOnce("file:" + rl, "文件名不是合法的分类 id（只能用小写字母、数字、下划线），跳过: " + rl);
                    continue;
                }
                if (!chosen.containsKey(id)) {
                    chosen.put(id, rl);
                    chosenLang.put(id, lang);
                    resources.put(rl, e.getValue());
                }
            }
        }

        List<Category> categories = new ArrayList<>();
        for (Map.Entry<String, ResourceLocation> e : chosen.entrySet()) {
            String id = e.getKey();
            String where = chosenLang.get(id) + "/" + id + ".json";
            try {
                JsonElement root = readJson(resources.get(e.getValue()));
                if (!root.isJsonObject()) {
                    GuideLog.warnOnce("root:" + where, where + "：根节点必须是对象，整个分类跳过");
                    continue;
                }
                Category category = parseCategory(id, root.getAsJsonObject(), where);
                if (category != null) {
                    categories.add(category);
                }
            } catch (Exception ex) {
                // 读不出来 / JSON 语法错误：只丢这一个分类
                GuideLog.warnOnce("parse:" + where, where + "：解析失败，整个分类跳过 —— " + ex.getMessage());
            }
        }
        // 全书顺序：入门 → 主线 → 支线，区内按 order（篇末翻页、首页、侧栏都按这个顺序）
        categories.sort(Comparator.comparingInt((Category c) -> c.group.ordinal()).thenComparingInt(c -> c.order).thenComparing(c -> c.id));
        return new Book(categories, language);
    }

    private static JsonElement readJson(Resource resource) throws Exception {
        String text;
        try (InputStream in = resource.open()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // 记事本另存的 UTF-8 带 BOM，Gson 会把它当成非法字符
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(true);
        return JsonParser.parseReader(reader);
    }

    @Nullable
    private static Category parseCategory(String fileId, JsonObject o, String where) {
        String declared = str(o, "id");
        if (declared != null && !declared.equals(fileId)) {
            GuideLog.warnOnce("catid:" + where, where + "：id「" + declared + "」与文件名不一致，以文件名为准");
        }
        JsonElement entriesJson = o.get("entries");
        if (entriesJson == null || !entriesJson.isJsonArray()) {
            GuideLog.warnOnce("entries:" + where, where + "：缺少 entries 数组，整个分类跳过");
            return null;
        }
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = 0;
        for (JsonElement el : entriesJson.getAsJsonArray()) {
            String entryWhere = where + " 第 " + (index++ + 1) + " 个条目";
            try {
                if (!el.isJsonObject()) {
                    GuideLog.warnOnce("entry:" + entryWhere, entryWhere + "：不是对象，跳过");
                    continue;
                }
                Entry entry = parseEntry(el.getAsJsonObject(), entryWhere);
                if (entry == null) {
                    continue;
                }
                if (!seen.add(entry.id)) {
                    GuideLog.warnOnce("dup:" + where + entry.id, where + "：条目 id「" + entry.id + "」重复，后一个跳过");
                    continue;
                }
                entries.add(entry);
            } catch (RuntimeException ex) {
                GuideLog.warnOnce("entry:" + entryWhere, entryWhere + "：解析失败，跳过 —— " + ex);
            }
        }
        int order = 1000;
        JsonElement orderJson = o.get("order");
        if (orderJson != null && orderJson.isJsonPrimitive() && orderJson.getAsJsonPrimitive().isNumber()) {
            order = orderJson.getAsInt();
        }
        String title = str(o, "title");
        String subtitle = str(o, "subtitle");
        String group = str(o, "group");
        if (group != null && !group.equals("start") && !group.equals("main") && !group.equals("side")) {
            GuideLog.warnOnce("group:" + where, where + "：group「" + group + "」不认识（只能是 start / main / side），按支线处理");
        }
        JsonElement draft = o.get("_draft");
        ResourceLocation advancement = location(o, "advancement", where);
        return new Category(fileId, order, Group.of(group), title != null ? title : fileId, subtitle != null ? subtitle : "",
                str(o, "icon"), advancement, lockedHint(o, advancement, where), draft != null && draft.isJsonPrimitive() && draft.getAsBoolean(),
                cond(o.get("if"), where), entries);
    }

    @Nullable
    private static Entry parseEntry(JsonObject o, String where) {
        String id = str(o, "id");
        if (id == null || !ID.matcher(id).matches()) {
            GuideLog.warnOnce("eid:" + where, where + "：id 缺失或不合法（只能用小写字母、数字、下划线），跳过");
            return null;
        }
        where = where + "「" + id + "」";
        List<Block> blocks = new ArrayList<>();
        JsonElement blocksJson = o.get("blocks");
        if (blocksJson != null && blocksJson.isJsonArray()) {
            int index = 0;
            for (JsonElement el : blocksJson.getAsJsonArray()) {
                String blockWhere = where + " 第 " + (index++ + 1) + " 块";
                try {
                    Block block = el.isJsonObject() ? parseBlock(el.getAsJsonObject(), blockWhere) : null;
                    if (block != null) {
                        blocks.add(block);
                    } else if (!el.isJsonObject()) {
                        GuideLog.warnOnce("block:" + blockWhere, blockWhere + "：不是对象，跳过");
                    }
                } catch (RuntimeException ex) {
                    GuideLog.warnOnce("block:" + blockWhere, blockWhere + "：解析失败，跳过 —— " + ex);
                }
            }
        } else {
            GuideLog.warnOnce("blocks:" + where, where + "：缺少 blocks 数组");
        }
        String title = str(o, "title");
        String summary = str(o, "summary");
        String itemsWhere = where + " 的 items";
        String optionalWhere = where + " 的 optional";
        // optional：布尔照旧；字符串 / 数组是「按条件可选」，写法同 if（GuideSteps.OptionalRule）
        GuideSteps.OptionalRule optional = GuideSteps.OptionalRule.parse(o.get("optional"),
                problem -> GuideLog.warnOnce("optional:" + optionalWhere, optionalWhere + "：" + problem));
        ResourceLocation advancement = location(o, "advancement", where);
        return new Entry(id, title != null ? title : id, str(o, "icon"), summary != null ? summary : "",
                cond(o.get("if"), where), blocks, location(o, "complete", where),
                optional,
                advancement,
                lockedHint(o, advancement, where),
                EntryItems.parse(o.get("items"), problem -> GuideLog.warnOnce("items:" + itemsWhere + problem, itemsWhere + "：" + problem)));
    }

    /**
     * 分类 / 条目的 {@code locked_hint}：被 advancement 锁住时的解锁提示。没写 advancement 时它永远用不上，照样读进来、只记一条警告
     * （validate_guide.py 对这种写法报错）
     */
    @Nullable
    private static String lockedHint(JsonObject o, @Nullable ResourceLocation advancement, String where) {
        String hint = str(o, "locked_hint");
        if (hint != null && advancement == null) {
            GuideLog.warnOnce("lockedhint:" + where, where + "：写了 locked_hint 却没有 advancement，这句提示不会显示");
        }
        return hint == null || hint.isBlank() ? null : hint;
    }

    @Nullable
    private static Block parseBlock(JsonObject o, String where) {
        String typeName = str(o, "type");
        BlockType type = BlockType.of(typeName);
        if (type == null) {
            GuideLog.warnOnce("type:" + where, where + "：未知块类型「" + typeName + "」，跳过");
            return null;
        }
        Block.Builder b = new Block.Builder(type);
        b.cond = cond(o.get("if"), where);
        switch (type) {
            case H, P, FORMULA -> {
                b.text = str(o, "text");
                if (b.text == null) {
                    return missing(where, "text");
                }
            }
            case TIP -> {
                b.text = str(o, "text");
                if (b.text == null) {
                    return missing(where, "text");
                }
                b.title = str(o, "title");
                String style = str(o, "style");
                if (style != null && !style.equals("info") && !style.equals("warn") && !style.equals("good")
                        && !style.equals("bad") && !style.equals("lore")) {
                    GuideLog.warnOnce("tipstyle:" + where, where + "：提示框样式「" + style + "」不认识（info / warn / good / bad / lore），按 info 画");
                }
                b.style = TipStyle.of(style);
            }
            case QUOTE -> {
                b.text = str(o, "text");
                if (b.text == null) {
                    return missing(where, "text");
                }
                b.caption = str(o, "by");
            }
            case LIST, STEPS -> {
                condItems(o.get("items"), b, where);
                if (b.items.isEmpty()) {
                    return missing(where, "items");
                }
                JsonElement ordered = o.get("ordered");
                b.ordered = ordered != null && ordered.isJsonPrimitive() && ordered.getAsBoolean();
            }
            case KV -> {
                b.rows = rows(o.get("rows"), 2, where, b);
                if (b.rows.isEmpty()) {
                    return missing(where, "rows");
                }
            }
            case TABLE -> {
                b.header = strList(o.get("header"));
                if (b.header.isEmpty()) {
                    return missing(where, "header");
                }
                b.rows = rows(o.get("rows"), b.header.size(), where, b);
                b.widths = widths(o.get("widths"), b.header.size(), where);
            }
            case ITEMS -> {
                b.items = strList(o.get("items"));
                if (b.items.isEmpty()) {
                    return missing(where, "items");
                }
                b.caption = str(o, "caption");
                JsonElement perRow = o.get("per_row");
                if (perRow != null && perRow.isJsonPrimitive() && perRow.getAsJsonPrimitive().isNumber()) {
                    b.perRow = Math.max(0, Math.min(16, perRow.getAsInt()));
                }
            }
            case RECIPE -> {
                b.figure = FigureSpecParser.recipes(o, where);
                if (b.figure == null) {
                    return null;
                }
                b.caption = str(o, "caption");
            }
            case STRUCTURE, RITUAL -> {
                b.figure = FigureSpecParser.structure(o, where, type == BlockType.RITUAL);
                if (b.figure == null) {
                    return null;
                }
                b.caption = str(o, "caption");
            }
            case IMAGE -> {
                b.figure = FigureSpecParser.image(o, where);
                if (b.figure == null) {
                    return null;
                }
                b.caption = str(o, "caption");
            }
            case ENTITY -> {
                b.entity = str(o, "id");
                if (b.entity == null) {
                    return missing(where, "id");
                }
                JsonElement h = o.get("height");
                if (h != null && h.isJsonPrimitive() && h.getAsJsonPrimitive().isNumber()) {
                    // 太矮看不清，太高一屏放不下
                    b.height = Math.max(24, Math.min(200, h.getAsInt()));
                }
                // 可选：nbt（SNBT，摆精英变体、颜色）和 scale（大首领缩小）。nbt 写错只记警告、预览照常建
                String nbt = str(o, "nbt");
                if (nbt != null) {
                    if (EntityPreviewSpec.parseNbt(nbt) == null) {
                        GuideLog.warnOnce("entitynbt:" + where, where + "：entity.nbt 不是合法的 SNBT，忽略 —— " + nbt);
                    } else {
                        b.entityNbt = nbt;
                    }
                }
                JsonElement scaleJson = o.get("scale");
                if (scaleJson != null && scaleJson.isJsonPrimitive() && scaleJson.getAsJsonPrimitive().isNumber()) {
                    b.entityScale = EntityPreviewSpec.clampScale(scaleJson.getAsDouble());
                }
                // 可选：name（语言键）。nbt 摆出的变体换了名字时写；键不存在时排版退回类型名、记一条警告
                b.entityName = str(o, "name");
            }
            case DIVIDER -> {
            }
        }
        return b.build();
    }

    @Nullable
    private static Block missing(String where, String field) {
        GuideLog.warnOnce("field:" + where, where + "：缺少字段 " + field + "，跳过");
        return null;
    }

    // ==================== 字段读取 ====================

    @Nullable
    private static String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        return el.isJsonPrimitive() ? el.getAsString() : null;
    }

    @Nullable
    private static net.minecraft.resources.ResourceLocation location(JsonObject o, String key, String where) {
        String value = str(o, key);
        if (value == null || value.isEmpty()) {
            return null;
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(value);
        if (rl == null) {
            GuideLog.warnOnce("rl:" + where + key, where + "：" + key + "「" + value + "」不是合法的资源名，忽略");
        }
        return rl;
    }

    private static List<String> strList(@Nullable JsonElement el) {
        if (el == null || !el.isJsonArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement x : el.getAsJsonArray()) {
            out.add(x != null && x.isJsonPrimitive() ? x.getAsString() : "");
        }
        return out;
    }

    /**
     * 行数组；列数不对的行补空或截断（报一次）。表格少一格总比整张表不显示强。
     */
    private static List<List<String>> rows(@Nullable JsonElement el, int columns, String where, Block.Builder b) {
        if (el == null || !el.isJsonArray()) {
            return List.of();
        }
        List<List<String>> out = new ArrayList<>();
        List<List<String>> conds = new ArrayList<>();
        for (JsonElement r : el.getAsJsonArray()) {
            // 行可以是 ["a", "b"]，也可以是 {"if": ..., "cells": ["a", "b"]}（只在条件成立时显示这一行）
            List<String> rowCond = List.of();
            if (r != null && r.isJsonObject()) {
                rowCond = cond(r.getAsJsonObject().get("if"), where);
                r = r.getAsJsonObject().get("cells");
            }
            conds.add(rowCond);
            List<String> row = new ArrayList<>(strList(r));
            if (row.size() != columns) {
                GuideLog.warnOnce("cols:" + where, where + "：有行的列数（" + row.size() + "）不等于 " + columns + "，已补齐 / 截断");
                while (row.size() < columns) {
                    row.add("");
                }
                while (row.size() > columns) {
                    row.remove(row.size() - 1);
                }
            }
            out.add(row);
        }
        b.rowConds = conds;
        return out;
    }

    /**
     * list / steps 的项：字符串，或 {"if": ..., "text": "..."}（只在条件成立时显示这一项）
     */
    private static void condItems(@Nullable JsonElement el, Block.Builder b, String where) {
        List<String> items = new ArrayList<>();
        List<List<String>> conds = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) {
                if (x != null && x.isJsonObject()) {
                    String t = str(x.getAsJsonObject(), "text");
                    items.add(t == null ? "" : t);
                    conds.add(cond(x.getAsJsonObject().get("if"), where));
                } else {
                    items.add(x != null && x.isJsonPrimitive() ? x.getAsString() : "");
                    conds.add(List.of());
                }
            }
        }
        b.items = items;
        b.itemConds = conds;
    }

    @Nullable
    private static float[] widths(@Nullable JsonElement el, int columns, String where) {
        if (el == null || !el.isJsonArray()) {
            return null;
        }
        JsonArray array = el.getAsJsonArray();
        if (array.size() != columns) {
            GuideLog.warnOnce("widths:" + where, where + "：widths 长度不等于列数，改为按内容自适应");
            return null;
        }
        float[] out = new float[columns];
        for (int i = 0; i < columns; i++) {
            JsonElement x = array.get(i);
            float v = x != null && x.isJsonPrimitive() && x.getAsJsonPrimitive().isNumber() ? x.getAsFloat() : 1f;
            out[i] = v > 0f ? v : 1f;
        }
        return out;
    }

    /**
     * if 字段：字符串或字符串数组。格式不对时换成一个必然不成立的条件 ——
     * 条件是用来藏掉「当前游戏里不存在的机制」的，写坏了宁可藏起来也别让玩家读到不存在的东西。
     * 解析本身在 {@link GuideConditions#parse}（条目 items 的条件组也用它）。
     */
    private static List<String> cond(@Nullable JsonElement el, String where) {
        return GuideConditions.parse(el, () -> GuideLog.warnOnce("if:" + where, where + "：if 必须是字符串或字符串数组，按不成立处理"));
    }
}
