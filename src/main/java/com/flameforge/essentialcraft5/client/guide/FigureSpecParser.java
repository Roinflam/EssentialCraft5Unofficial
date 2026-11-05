package com.flameforge.essentialcraft5.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析内容 JSON 里的 {@code recipe / structure / ritual / image} 块（格式见 tools/guide/FORMAT.md §1.3）。
 * <p>
 * 从旧手册的 {@code GuideLoader} 搬过来：宽容解析的规矩照旧 —— 哪一块写错了只跳过它自己、报一次（{@link GuideLog}），
 * 其余照常载入；结构的写法（layers / key / anchor / throw / result / chance、key 的简写）和旧格式一致。
 */
@OnlyIn(Dist.CLIENT)
final class FigureSpecParser {

    private FigureSpecParser() {
    }

    // ==================== recipe ====================

    /**
     * {@code item} / {@code items}（两个都写时合并）、{@code recipe}、{@code recipe_type}、{@code compact?}、{@code optional?}
     *
     * @return 解析结果；三样都没有时为 null（块跳过）
     */
    @Nullable
    static FigureSpec.Recipes recipes(JsonObject json, String where) {
        List<ResourceLocation> items = new ArrayList<>(locations(json, "item", where));
        for (ResourceLocation id : locations(json, "items", where)) {
            if (!items.contains(id)) {
                items.add(id);
            }
        }
        ResourceLocation recipe = location(json, "recipe", where);
        ResourceLocation recipeType = location(json, "recipe_type", where);
        if (items.isEmpty() && recipe == null && recipeType == null) {
            problem(where, "配方块没有 item / items / recipe / recipe_type，跳过");
            return null;
        }
        return new FigureSpec.Recipes(List.copyOf(items), recipe, recipeType, bool(json, "compact"), bool(json, "optional"));
    }

    // ==================== image ====================

    @Nullable
    static FigureSpec.Image image(JsonObject json, String where) {
        ResourceLocation texture = location(json, "texture", where);
        if (texture == null) {
            problem(where, "插图块没有 texture，跳过");
            return null;
        }
        int width = intOr(json, "width", 256);
        int height = intOr(json, "height", 256);
        if (width <= 0 || height <= 0) {
            problem(where, "插图尺寸不对（width / height 必须是正数），跳过");
            return null;
        }
        return new FigureSpec.Image(texture, width, height);
    }

    // ==================== structure / ritual ====================

    /**
     * 结构图，三种写法：
     * <ul>
     *   <li>{@code "structure": "essentialcraft5:mithriline_furnace"}：画代码里登记的结构（StructurePatterns）；</li>
     *   <li>{@code "layers": [{"pattern": […], "key": {…}, "label": 文字}, …]}：多层，从下到上；块级的 {@code key} 是各层共用的，层里的 key 优先；</li>
     *   <li>{@code "pattern": […], "key": {…}}：单层（仪式也用它，可带 throw / result / chance）。</li>
     * </ul>
     * {@code "anchor": "F"}（可选）：机器所在的字符，四角品红光；多层时其它层在同一竖列画小标记，层标题写相对机器的高度。
     *
     * @param ritual 是不是仪式块（仪式只认单层 pattern，要有 throw）
     */
    @Nullable
    static FigureSpec.Structure structure(JsonObject json, String where, boolean ritual) {
        float chance = json.has("chance") && json.get("chance").isJsonPrimitive() ? json.get("chance").getAsFloat() : -1F;
        ResourceLocation thrown = location(json, "throw", where);
        ResourceLocation result = location(json, "result", where);
        if (!ritual && json.has("structure")) {
            ResourceLocation id = location(json, "structure", where);
            if (id == null) {
                return null;
            }
            if (json.has("pattern") || json.has("layers")) {
                problem(where, "写了 structure 就不要再写 pattern / layers（以代码里登记的结构为准）");
            }
            return new FigureSpec.Structure(List.of(), id, null, null, null, -1F);
        }
        Map<Character, FigureSpec.StructureKey> shared = keys(json, where);
        List<FigureSpec.StructureLayer> layers = new ArrayList<>();
        if (!ritual && json.has("layers")) {
            if (json.has("pattern")) {
                problem(where, "pattern 和 layers 只能写一种，按 layers 画");
            }
            JsonArray array = json.get("layers").isJsonArray() ? json.getAsJsonArray("layers") : new JsonArray();
            for (int i = 0; i < array.size(); i++) {
                String at = where + " 第 " + (i + 1) + " 层";
                if (!array.get(i).isJsonObject()) {
                    problem(at, "必须是对象 {pattern, key, label}");
                    continue;
                }
                JsonObject layer = array.get(i).getAsJsonObject();
                // 公共 key 在前、层里的覆盖在后：图例按书写顺序列，公共的排前面
                Map<Character, FigureSpec.StructureKey> key = new LinkedHashMap<>(shared);
                key.putAll(keys(layer, at));
                FigureSpec.StructureLayer parsed = layer(layer, key, string(layer, "label"), at);
                if (parsed != null) {
                    layers.add(parsed);
                }
            }
            if (thrown != null || result != null) {
                problem(where, "多层结构不画 throw / result（仪式请用 ritual 块）");
                thrown = null;
                result = null;
            }
        } else {
            FigureSpec.StructureLayer parsed = layer(json, shared, null, where);
            if (parsed != null) {
                layers.add(parsed);
            }
        }
        if (layers.isEmpty()) {
            problem(where, (ritual ? "仪式块" : "结构块") + "没有 pattern，跳过");
            return null;
        }
        if (ritual && thrown == null) {
            problem(where, "仪式块没有 throw（要丢的物品）");
        }
        Character anchor = null;
        String anchorText = string(json, "anchor");
        if (anchorText != null) {
            if (anchorText.length() != 1 || anchorText.charAt(0) == ' ') {
                problem(where, "anchor 必须是一个非空格字符");
            } else {
                anchor = anchorText.charAt(0);
                boolean found = false;
                for (FigureSpec.StructureLayer layer : layers) {
                    for (String row : layer.pattern()) {
                        found |= row.indexOf(anchor) >= 0;
                    }
                }
                if (!found) {
                    problem(where, "anchor「" + anchor + "」在 pattern 里没有出现");
                    anchor = null;
                }
            }
        }
        if (layers.size() > 1) {
            FigureSpec.StructureLayer first = layers.get(0);
            for (int i = 1; i < layers.size(); i++) {
                if (layers.get(i).pattern().size() != first.pattern().size() || width(layers.get(i)) != width(first)) {
                    problem(where, "第 " + (i + 1) + " 层和第 1 层大小不一样，上下对不齐（空位用空格补齐）");
                    break;
                }
            }
        }
        return new FigureSpec.Structure(List.copyOf(layers), null, anchor, thrown, result, chance);
    }

    private static int width(FigureSpec.StructureLayer layer) {
        int width = 0;
        for (String row : layer.pattern()) {
            width = Math.max(width, row.length());
        }
        return width;
    }

    @Nullable
    private static FigureSpec.StructureLayer layer(JsonObject json, Map<Character, FigureSpec.StructureKey> key, @Nullable String label,
                                                   String where) {
        List<String> pattern = new ArrayList<>();
        if (json.has("pattern") && json.get("pattern").isJsonArray()) {
            for (JsonElement row : json.getAsJsonArray("pattern")) {
                pattern.add(row != null && row.isJsonPrimitive() ? row.getAsString() : "");
            }
        }
        if (pattern.isEmpty()) {
            problem(where, "没有 pattern");
            return null;
        }
        for (String row : pattern) {
            for (char c : row.toCharArray()) {
                if (c != ' ' && !key.containsKey(c)) {
                    problem(where, "pattern 里的「" + c + "」在 key 里没有定义（按空格画）");
                }
            }
        }
        // 保留 key 的书写顺序：图例按这个顺序列
        return new FigureSpec.StructureLayer(List.copyOf(pattern), Collections.unmodifiableMap(key), label);
    }

    private static Map<Character, FigureSpec.StructureKey> keys(JsonObject json, String where) {
        Map<Character, FigureSpec.StructureKey> key = new LinkedHashMap<>();
        JsonObject keys = json.has("key") && json.get("key").isJsonObject() ? json.getAsJsonObject("key") : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : keys.entrySet()) {
            if (entry.getKey().length() != 1 || entry.getKey().charAt(0) == ' ') {
                problem(where, "key 里的「" + entry.getKey() + "」必须是一个非空格字符");
                continue;
            }
            FigureSpec.StructureKey parsed = key(entry.getValue(), where);
            if (parsed != null) {
                key.put(entry.getKey().charAt(0), parsed);
            }
        }
        return key;
    }

    /**
     * key 的值可以简写：「minecraft:lava」一个方块、「#minecraft:sand」方块标签、「air」必须是空气；
     * 也可以写对象：{"block": id 或 [id…], "tag": id, "air": true, "label": 文字}。
     */
    @Nullable
    private static FigureSpec.StructureKey key(JsonElement value, String where) {
        if (value.isJsonPrimitive()) {
            String s = value.getAsString();
            if (s.equals("air")) {
                return new FigureSpec.StructureKey(List.of(), null, true, null);
            }
            if (s.startsWith("#")) {
                ResourceLocation tag = parse(s.substring(1), where);
                return tag == null ? null : new FigureSpec.StructureKey(List.of(), tag, false, null);
            }
            ResourceLocation block = parse(s, where);
            return block == null ? null : new FigureSpec.StructureKey(List.of(block), null, false, null);
        }
        if (!value.isJsonObject()) {
            problem(where, "key 的值格式不对");
            return null;
        }
        JsonObject json = value.getAsJsonObject();
        boolean air = bool(json, "air");
        List<ResourceLocation> blocks = locations(json, "block", where);
        ResourceLocation tag = location(json, "tag", where);
        if (!air && blocks.isEmpty() && tag == null) {
            problem(where, "key 的对象里至少要有 block、tag、air 之一");
            return null;
        }
        return new FigureSpec.StructureKey(List.copyOf(blocks), tag, air, string(json, "label"));
    }

    // ==================== 小工具 ====================

    private static void problem(String where, String message) {
        GuideLog.warnOnce("figure:" + where + message, where + "：" + message);
    }

    @Nullable
    static String string(JsonObject json, String key) {
        JsonElement el = json.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static boolean bool(JsonObject json, String key) {
        JsonElement el = json.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsBoolean();
    }

    private static int intOr(JsonObject json, String key, int fallback) {
        JsonElement el = json.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber() ? el.getAsInt() : fallback;
    }

    @Nullable
    private static ResourceLocation location(JsonObject json, String key, String where) {
        String value = string(json, key);
        return value == null ? null : parse(value, where);
    }

    /** 字符串或字符串数组 */
    static List<ResourceLocation> locations(JsonObject json, String key, String where) {
        List<ResourceLocation> out = new ArrayList<>();
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return out;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item != null && item.isJsonPrimitive()) {
                    ResourceLocation location = parse(item.getAsString(), where);
                    if (location != null) {
                        out.add(location);
                    }
                }
            }
        } else if (element.isJsonPrimitive()) {
            ResourceLocation location = parse(element.getAsString(), where);
            if (location != null) {
                out.add(location);
            }
        }
        return out;
    }

    @Nullable
    private static ResourceLocation parse(String value, String where) {
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            problem(where, "「" + value + "」不是合法的资源名");
        }
        return location;
    }
}
