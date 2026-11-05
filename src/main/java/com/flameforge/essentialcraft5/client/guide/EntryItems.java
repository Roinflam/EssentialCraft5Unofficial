package com.flameforge.essentialcraft5.client.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 条目的 {@code items}（这个条目负责介绍的物品，查阅索引的第一优先级）：物品 id 加上各自的条件
 *
 * <p>写法是物品 id 和<b>条件组</b>混排：</p>
 * <pre>
 * "items": ["essentialcraft5:fancy_mru_big_tile", …,
 *           {"if": "cfg:general.hoanna.portal_enabled", "items": ["essentialcraft5:fancy_void_stone_big_tile", …]}]
 * </pre>
 * <p>条件组的 {@code if} 写法同块级 {@code if}（字符串或数组，见 FORMAT.md §1.6），不成立时组里的物品不进查阅索引、
 * 也不进搜索的物品名 —— 例如海纳尔之门关着时，虚空石花纹方块按住 Ctrl 查不到这一篇。组不能嵌套。
 * 一篇里只有一部分物品受开关控制时用它；整篇都受控制就给条目写 {@code if}。</p>
 *
 * <p>不带 {@code @OnlyIn}：只用 Gson 和资源名，GameTest 服务器上直接拿它解析手册文件、配合 {@link GuideConditions}
 * 按真实配置求值（gametest/GuideFormatTests）。界面那边由 {@code GuideContentLoader} 解析、{@code GuideView} 按当前配置过滤。</p>
 */
public final class EntryItems {

    public static final EntryItems EMPTY = new EntryItems(List.of(), List.of());

    private final List<ResourceLocation> ids;
    /** 与 ids 等长：每一项的条件（空表 = 总是算） */
    private final List<List<String>> conds;

    private EntryItems(List<ResourceLocation> ids, List<List<String>> conds) {
        this.ids = List.copyOf(ids);
        this.conds = List.copyOf(conds);
    }

    /**
     * 解析条目的 {@code items} 字段
     *
     * <p>写坏的部分跳过并报一条：不是合法资源名的 id、既不是字符串也不是对象的项、组里不是字符串的项（含嵌套的组）。
     * 组的 {@code if} 格式不对时按不成立处理（{@link GuideConditions#parse}），宁可查不到也别查到不存在的东西。</p>
     *
     * @param el      JSON 里的 items（可空）
     * @param problem 报问题的回调（参数是问题描述，调用方自己加位置）
     * @return 物品表；没写或全写坏时为 {@link #EMPTY}
     */
    @Nonnull
    public static EntryItems parse(@Nullable JsonElement el, @Nonnull Consumer<String> problem) {
        if (el == null || el.isJsonNull()) {
            return EMPTY;
        }
        List<ResourceLocation> ids = new ArrayList<>();
        List<List<String>> conds = new ArrayList<>();
        if (el.isJsonPrimitive()) {
            // 单个字符串当成只有一项（和以前的解析一致）
            add(el.getAsString(), List.of(), ids, conds, problem);
        } else if (el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) {
                if (x != null && x.isJsonPrimitive()) {
                    add(x.getAsString(), List.of(), ids, conds, problem);
                } else if (x != null && x.isJsonObject()) {
                    group(x.getAsJsonObject(), ids, conds, problem);
                } else {
                    problem.accept("items 里的项要么是物品 id，要么是 {\"if\": …, \"items\": […]} 条件组，跳过: " + x);
                }
            }
        } else {
            problem.accept("items 必须是数组，整个字段忽略");
        }
        return ids.isEmpty() ? EMPTY : new EntryItems(ids, conds);
    }

    private static void group(JsonObject o, List<ResourceLocation> ids, List<List<String>> conds, Consumer<String> problem) {
        for (Map.Entry<String, JsonElement> kv : o.entrySet()) {
            if (!kv.getKey().equals("if") && !kv.getKey().equals("items") && !kv.getKey().startsWith("_")) {
                problem.accept("条件组不认识字段 " + kv.getKey());
            }
        }
        if (!o.has("if")) {
            // 没写条件的组没有意义，多半是漏写：按写坏处理，组里的物品不收
            problem.accept("条件组缺少 if，整组跳过");
            return;
        }
        List<String> cond = GuideConditions.parse(o.get("if"), () -> problem.accept("条件组的 if 必须是字符串或字符串数组，按不成立处理"));
        JsonElement items = o.get("items");
        if (items == null || !items.isJsonArray() || items.getAsJsonArray().isEmpty()) {
            problem.accept("条件组的 items 必须是非空数组，整组跳过");
            return;
        }
        for (JsonElement x : items.getAsJsonArray()) {
            if (x != null && x.isJsonPrimitive()) {
                add(x.getAsString(), cond, ids, conds, problem);
            } else {
                problem.accept("条件组的 items 里只能写物品 id（组不能嵌套），跳过: " + x);
            }
        }
    }

    private static void add(String raw, List<String> cond, List<ResourceLocation> ids, List<List<String>> conds, Consumer<String> problem) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            problem.accept("「" + raw + "」不是合法的资源名");
            return;
        }
        ids.add(id);
        conds.add(cond);
    }

    /**
     * 当前配置下算数的物品：不在条件组里的，加上条件成立的组里的
     *
     * <p>故意只有这一种取法、不提供「不管条件的全部物品」：查阅索引（{@code GuideView}）和搜索的物品名
     * （{@code GuideSearch}）只能拿到过滤后的结果，改回不过滤就得先把那种取法加回来。</p>
     *
     * @param test 条件求值（界面里是 {@code GuideValues::test}，同块级 if 的规则）
     * @return 物品 id，按书写顺序；没有任何条件组时就是全部物品
     */
    @Nonnull
    public List<ResourceLocation> visible(@Nonnull Predicate<List<String>> test) {
        boolean any = false;
        for (List<String> c : conds) {
            if (!c.isEmpty()) {
                any = true;
                break;
            }
        }
        if (!any) {
            return ids;
        }
        List<ResourceLocation> out = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            List<String> c = conds.get(i);
            if (c.isEmpty() || test.test(c)) {
                out.add(ids.get(i));
            }
        }
        return List.copyOf(out);
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }
}
