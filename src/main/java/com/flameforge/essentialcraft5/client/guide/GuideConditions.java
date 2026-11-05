package com.flameforge.essentialcraft5.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 手册各级 {@code if} 的求值，以及配置快照里值的类型转换
 *
 * <p>从 {@link GuideValues} 拆出来、<b>不带 {@code @OnlyIn}</b>：只用到配置快照、{@link ModList} 和资源名，
 * 两端都能加载。界面里由 {@link GuideValues} 持有一份；GameTest 服务器上拿真实的配置快照建一份，
 * 直接对手册内容求值（例如「海纳尔之门关着时这些物品不进查阅索引」，见 gametest/GuideFormatTests）。</p>
 *
 * <p>一个实例对应一份快照，按键缓存解析结果，不考虑失效（快照变了整份换掉）。警告走构造时给的回调：
 * 界面里是 {@code GuideLog::warnOnce}（按键去重），测试里可以收集起来断言。</p>
 *
 * <h3>EC5 的条件</h3>
 * <p>{@code bauble:<物品 id>}：这件饰品没被
 * {@code general.baubles.disabled} 禁用时成立。列表项和条件里的 id 都按 {@code ResourceLocation.tryParse} 解析后再比较，
 * 和游戏里的判定（{@code CuriosCompat.disabledIds}）一致 —— 写成 {@code zeta_amulet}（省略命名空间）时游戏里照样禁用，书也照样藏。</p>
 * <p>{@code module:<模块注册名>}（第十批 10c）：这块宅男套装模块没被 {@code general.computer_modules.disabled} 关掉时成立，解析和比较同 {@code bauble:}
 * （和游戏里的判定 {@code item/module/ModuleConfig} 一致）。第二十一章的六篇模块说明各带一个。</p>
 * <p>{@code gamemode:creative}：本地玩家现在是创造模式（{@code getAbilities().instabuild}）。值由建实例的一方给定，
 * 一个实例内前后一致。</p>
 * <p>{@code cfg:键|at:N}、{@code cfg:键|stage:N}（第十三批 13b）：列表键先取一项再判，取法同格式链里的 {@code at:N}（{@link #element}，越界取不到）
 * 和 {@code stage:N}（{@link #stageElement}，短了沿用最后一项），例 {@code cfg:general.mhs_armor.shares_chestplate|at:0==2}。
 * 按阶的列表配置（份额、容量）上，照默认数值算的例子只在默认值时显示，靠的就是它；取不到和缺键一样算判定不了。</p>
 */
public final class GuideConditions {

    /**
     * 条件 {@code cfg:键}、{@code cfg:键>=数}，列表键可以先取一项：{@code cfg:键|at:N>数}、{@code cfg:键|stage:N>数}（取法同格式链，
     * 见 {@link #element}、{@link #stageElement}）；冒号后、运算符两边的空白都容忍（整合包作者常写成 {@code cfg:a > 0}）
     */
    private static final Pattern CFG_COND = Pattern.compile(
            "^cfg:\\s*([A-Za-z0-9_.]+)\\s*(?:\\|\\s*(at|stage):(\\d+)\\s*)?(?:(>=|<=|==|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?))?$");
    /** 禁用的饰品（SERVER 配置 general.baubles.disabled，见 CuriosCompat.Config） */
    private static final String DISABLED_BAUBLES_KEY = "general.baubles.disabled";
    /** 关掉的宅男套装模块（SERVER 配置 general.computer_modules.disabled，见 item/module/ModuleConfig） */
    private static final String DISABLED_MODULES_KEY = "general.computer_modules.disabled";
    /** 格式不对的 if 换成的条件：不认识的前缀，必然不成立 */
    static final String INVALID = "?invalid";

    private final Map<String, String> snapshot;
    private final boolean creative;
    private final BiConsumer<String, String> warn;
    /** 键 → 解析后的值（Boolean / Double / List&lt;String&gt; / String） */
    private final Map<String, Object> typed = new HashMap<>();
    private final Set<ResourceLocation> disabledBaubles;
    private final Set<ResourceLocation> disabledModules;

    /**
     * @param snapshot 配置快照（键 → 字符串值，{@code GuideConfigSnapshot} 的格式）
     * @param creative 条件 {@code gamemode:creative} 的值
     * @param warn     警告回调：(去重键, 日志内容)
     */
    public GuideConditions(@Nonnull Map<String, String> snapshot, boolean creative, @Nonnull BiConsumer<String, String> warn) {
        this.snapshot = snapshot;
        this.creative = creative;
        this.warn = warn;
        this.disabledBaubles = idSet(DISABLED_BAUBLES_KEY);
        this.disabledModules = idSet(DISABLED_MODULES_KEY);
    }

    /** 配置里的注册名列表 → 集合（和 CuriosCompat.disabledIds、ModuleConfig 同一个解析：tryParse，解析不了的跳过） */
    private Set<ResourceLocation> idSet(String key) {
        Set<ResourceLocation> out = new HashSet<>();
        Object list = value(key);
        if (list instanceof List<?> l) {
            for (Object o : l) {
                ResourceLocation id = ResourceLocation.tryParse(String.valueOf(o));
                if (id != null) {
                    out.add(id);
                }
            }
        }
        return Collections.unmodifiableSet(out);
    }

    // ==================== if 字段 ====================

    /**
     * if 字段：字符串或字符串数组。格式不对时换成一个必然不成立的条件 ——
     * 条件是用来藏掉「当前游戏里不存在的机制」的，写坏了宁可藏起来也别让玩家读到不存在的东西。
     *
     * @param el  JSON 里的 if（可空 = 无条件）
     * @param bad 整个字段不是字符串 / 数组时调用一次（报警告用）
     * @return 条件列表；空表 = 无条件
     */
    @Nonnull
    public static List<String> parse(@Nullable JsonElement el, @Nonnull Runnable bad) {
        if (el == null || el.isJsonNull()) {
            return List.of();
        }
        if (el.isJsonPrimitive()) {
            return List.of(el.getAsString());
        }
        if (el.isJsonArray()) {
            List<String> out = new ArrayList<>();
            for (JsonElement x : el.getAsJsonArray()) {
                out.add(x != null && x.isJsonPrimitive() ? x.getAsString() : INVALID);
            }
            return out;
        }
        bad.run();
        return List.of(INVALID);
    }

    // ==================== 求值 ====================

    /**
     * 一组条件全部成立（空表 = 无条件）
     *
     * @param conds 条件列表
     * @return 是否成立
     */
    public boolean test(@Nonnull List<String> conds) {
        for (String c : conds) {
            if (!test(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 单个条件，可带前缀 {@code !} 取反；用 {@code ||} 连接的几项任一成立即成立
     *
     * <p>{@code ||} 是为「任一额外槽位开着」这类说明加的：副手、四个盔甲位、饰品栏各有开关，
     * 只写「并且」的话要拆成一堆互斥的块。</p>
     *
     * <h3>判定不了时</h3>
     * <p>键不在快照里、语法不对、前缀不认识，这三种是「不知道」而不是「不成立」：<b>不管前面有没有 {@code !}
     * 都按不成立处理</b>（{@code ||} 里的这一项同样算不成立）。要是先算成 false 再取反，
     * {@code !cfg:缺失键} 就成立了 —— 书里成对写的「开着时这样 / 关着时那样」会两头都错：
     * 服务端版本旧、缺一个键，正面的说明全藏掉，「已关闭」的替代说法反而全显示出来。
     * 写坏了宁可藏起来，和加载器的原则一致。</p>
     *
     * @param cond 条件
     * @return 是否成立
     */
    public boolean test(@Nonnull String cond) {
        if (cond.contains("||")) {
            for (String part : cond.split(Pattern.quote("||"))) {
                if (!part.isBlank() && test(part)) {
                    return true;
                }
            }
            return false;
        }
        String s = cond.trim();
        boolean negate = false;
        while (s.startsWith("!")) {
            negate = !negate;
            s = s.substring(1).trim();
        }
        Boolean result = evaluate(s);
        return result != null && negate != result;
    }

    /**
     * @return 成立 / 不成立；判定不了（缺键、语法错、不认识的前缀）为 null
     */
    @Nullable
    private Boolean evaluate(String s) {
        if (s.startsWith("cfg:")) {
            Matcher m = CFG_COND.matcher(s);
            if (!m.matches()) {
                warn.accept("cond:" + s, "条件语法不对，按不成立处理（取反也不成立）: " + s);
                return null;
            }
            String key = m.group(1);
            Object v = value(key);
            if (v == null) {
                warn.accept("cfg:" + key, "条件里的配置键不存在，按不成立处理（取反也不成立）: " + key);
                return null;
            }
            if (m.group(2) != null) {
                int index = Integer.parseInt(m.group(3));
                v = "stage".equals(m.group(2)) ? stageElement(v, index) : element(v, index);
                if (v == null) {
                    warn.accept("condat:" + s, "条件里的列表取不到这一项，按不成立处理（取反也不成立）: " + s);
                    return null;
                }
            }
            if (m.group(4) == null) {
                return truthy(v);
            }
            double left;
            if (v instanceof Double d) {
                left = d;
            } else if (v instanceof Boolean b) {
                left = b ? 1 : 0;
            } else if (v instanceof List<?> l) {
                left = l.size();
            } else {
                warn.accept("condnum:" + s, "条件比较的不是数字，按不成立处理（取反也不成立）: " + s);
                return null;
            }
            double right = Double.parseDouble(m.group(5));
            final double eps = 1e-9;
            return switch (m.group(4)) {
                case ">" -> left > right + eps;
                case ">=" -> left >= right - eps;
                case "<" -> left < right - eps;
                case "<=" -> left <= right + eps;
                case "==" -> Math.abs(left - right) <= eps;
                case "!=" -> Math.abs(left - right) > eps;
                default -> false;
            };
        }
        if (s.startsWith("mod:")) {
            return ModList.get().isLoaded(s.substring(4).trim());
        }
        if (s.startsWith("bauble:")) {
            ResourceLocation id = ResourceLocation.tryParse(s.substring(7).trim());
            if (id == null) {
                warn.accept("cond:" + s, "bauble: 后面不是合法的物品 id，按不成立处理（取反也不成立）: " + s);
                return null;
            }
            return !disabledBaubles.contains(id);
        }
        if (s.startsWith("module:")) {
            ResourceLocation id = ResourceLocation.tryParse(s.substring(7).trim());
            if (id == null) {
                warn.accept("cond:" + s, "module: 后面不是合法的模块 id，按不成立处理（取反也不成立）: " + s);
                return null;
            }
            return !disabledModules.contains(id);
        }
        if (s.startsWith("gamemode:")) {
            String mode = s.substring(9).trim();
            if (mode.equals("creative")) {
                return creative;
            }
            warn.accept("cond:" + s, "gamemode: 后面只认 creative，按不成立处理（取反也不成立）: " + s);
            return null;
        }
        warn.accept("cond:" + s, "不认识的条件，按不成立处理（取反也不成立）: " + s);
        return null;
    }

    /**
     * 「真」的判定：布尔为 true / 数字 ≠ 0 / 列表非空 / 字符串非空
     *
     * @param v 值（{@link #value} 的结果）
     * @return 是否为真
     */
    static boolean truthy(@Nullable Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Double d) {
            return d != 0.0;
        }
        if (v instanceof List<?> l) {
            return !l.isEmpty();
        }
        return v != null && !String.valueOf(v).isEmpty();
    }

    // ==================== 取值 ====================

    /**
     * 键对应的值，按形状转成 Boolean / Double / List / String
     *
     * @param key 配置键
     * @return 值；快照里没有这个键时为 null
     */
    @Nullable
    Object value(String key) {
        Object cached = typed.get(key);
        if (cached != null) {
            return cached;
        }
        String raw = snapshot.get(key);
        if (raw == null) {
            return null;
        }
        Object v = parseValue(raw);
        typed.put(key, v);
        return v;
    }

    /**
     * 格式链里 {@code at:N} 的取值：列表的第 index 项（0 起），按单个值的形状转成 Boolean / Double / String。
     * 列表配置按「第几级 / 第几阶各一个数」写（相性、宅男套装各阶单价），手册要单独写出其中一项时用。
     * 不是列表、下标越界时返回 null，界面上显示警告色的「?」：越界时各配置自己的补法不一样
     * （相性少写的级按 0、宅男套装少写的阶按最后一项），{@code at:N} 不去猜；代码按阶沿用最后一项的列表用 {@code stage:N}（{@link #stageElement}）。
     *
     * @param value {@link #value(String)} 的结果
     * @param index 第几项（0 起）
     * @return 那一项；取不到时为 null
     */
    @Nullable
    public static Object element(@Nullable Object value, int index) {
        if (!(value instanceof List<?> list) || index < 0 || index >= list.size()) {
            return null;
        }
        return parseValue(String.valueOf(list.get(index)));
    }

    /**
     * 格式链里 {@code stage:N} 的取值：按阶的列表取第 index 阶，列表比 index 短时沿用最后一项（同 {@code ECWindGear.Config.Values}
     * 按阶取值的补法，服主只填 {@code [0]} 时每一阶都是 0）。不是列表、空列表、负数下标返回 null（显示「?」）。
     *
     * @param value {@link #value(String)} 的结果
     * @param index 第几阶（0 起）
     * @return 那一项；取不到时为 null
     */
    @Nullable
    public static Object stageElement(@Nullable Object value, int index) {
        if (!(value instanceof List<?> list) || list.isEmpty() || index < 0) {
            return null;
        }
        return parseValue(String.valueOf(list.get(Math.min(index, list.size() - 1))));
    }

    /**
     * 快照里按阶的列表键的第 index 阶，同 {@code {cfg:键|stage:index}}
     *
     * @param key   配置键
     * @param index 第几阶（0 起）
     * @return 那一项；没有这个键、不是列表、空列表时为 null
     */
    @Nullable
    public Object stageElement(String key, int index) {
        return stageElement(value(key), index);
    }

    /**
     * 快照里列表键的第 index 项，同 {@code {cfg:键|at:index}}
     *
     * @param key   配置键
     * @param index 第几项（0 起）
     * @return 那一项；没有这个键、不是列表、越界时为 null
     */
    @Nullable
    public Object element(String key, int index) {
        return element(value(key), index);
    }

    private static Object parseValue(String raw) {
        String s = raw.trim();
        if (s.startsWith("[")) {
            try {
                JsonElement el = JsonParser.parseString(s);
                if (el.isJsonArray()) {
                    JsonArray array = el.getAsJsonArray();
                    List<String> out = new ArrayList<>(array.size());
                    for (JsonElement x : array) {
                        out.add(x.isJsonPrimitive() ? x.getAsString() : x.toString());
                    }
                    return Collections.unmodifiableList(out);
                }
            } catch (RuntimeException ignored) {
                // 不是合法 JSON 就当普通字符串
            }
        }
        if ("true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        try {
            double d = Double.parseDouble(s);
            if (!Double.isNaN(d)) {
                return d;
            }
        } catch (NumberFormatException ignored) {
            // 不是数字
        }
        return raw;
    }
}
