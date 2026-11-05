package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.util.NumberText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 正文里的占位符与条件求值：{@code {cfg:键|格式}}、{@code {item:…}}、{@code {block:…}}、{@code {balance:…}}、
 * {@code {keybind:…}}、{@code {lang:…}} 和各级 {@code if}
 *
 * <p>一个实例对应<b>一份</b>配置快照（{@link GuideClientConfig#snapshot()}），界面打开时建一次，
 * 快照版本变了就整份换掉 —— 所以这里可以放心按键缓存解析结果，不用考虑失效。</p>
 *
 * <h3>数字怎么显示</h3>
 * <p>快照里的 double 是 {@code String.valueOf} 出来的（{@code 1.0}、{@code 0.075}、偶尔 {@code 1.0E-4}），
 * 直接显示会满书都是「1.0 倍」。统一走 {@link #formatNumber}：最多两位小数（{@code round:D} 可改）、
 * 四舍五入、去掉尾零。用 {@link BigDecimal} 而不是 {@code String.format}：后者在 0.075×100 这种
 * 浮点误差（7.499999…）上会给出 7.5 还是 7.49 取决于位数，前者先按十进制字面量转换再舍入，结果稳定。</p>
 * <p>整数部分 ≥ 1000 时按界面语言键 {@code guide.essentialcraft5.ui.fmt.group_sep} 插千位分隔符（英文逗号、中文不分组，
 * 分组本身在 {@link NumberText#group}）。raw / int / pct / pctv / x / sec 都走它；{@code count}（列表长度）不分组。</p>
 *
 * <h3>缺键</h3>
 * <p>内容里写错键名、或者服务端版本比客户端旧没有这一项时，显示警告色的「?」并按键只报一次。
 * 不显示默认值：默认值恰恰是「服主改过的配置」最可能不等于的那个数，显示出来就是误导。</p>
 *
 * <h3>EC5 的条件与占位符</h3>
 * <p>条件求值和配置值的类型转换在 {@link GuideConditions}（不带 {@code @OnlyIn}，GameTest 服务器上也能用），这里持有一份。
 * {@code bauble:} 与 {@code gamemode:creative} 的说明见那边。{@code gamemode:creative} 的值在建视图时取一次（构造参数），
 * 切换游戏模式后 {@code GuideView.isStale} 发现不一样就整份重建，一个实例内前后一致。</p>
 * <p>{@code {item:}} / {@code {block:}} 除了名字还带着物品（{@link Resolved#stack}），行内标记据此做成可点的物品链接；
 * {@code {balance:}} 带着平衡值三态自己的颜色（{@link Resolved#color}）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class GuideValues {

    /** 占位符的解析结果 */
    public static final class Resolved {
        public final String text;
        /** 缺键 / 缺物品等：界面用警告色显示 */
        public final boolean missing;
        /** 自带的颜色（平衡值三态、按键名）；0 表示跟随外层文字 */
        public final int color;
        /** {@code {item:}} / {@code {block:}} 对应的物品（做成可点的物品链接）；其它占位符为 EMPTY */
        public final ItemStack stack;

        Resolved(String text, boolean missing) {
            this(text, missing, 0, ItemStack.EMPTY);
        }

        Resolved(String text, boolean missing, int color, ItemStack stack) {
            this.text = text;
            this.missing = missing;
            this.color = color;
            this.stack = stack;
        }
    }

    private static final Resolved MISSING = new Resolved("?", true);
    /** 千位分隔符的界面语言键：英文 {@code ","}，中文空串（不分组）；繁体随 make_traditional 带过去，其它语言回退英文 */
    static final String GROUP_SEP_KEY = "guide.essentialcraft5.ui.fmt.group_sep";

    private final boolean synced;
    /** 条件求值与配置值（建视图时本地玩家是不是创造模式也在里面，条件 {@code gamemode:creative}） */
    private final GuideConditions conditions;
    private final Map<String, Resolved> itemNames = new HashMap<>();

    // 本地化字样，构造时取一次：实例的寿命不会跨过语言切换（切语言会重建整个视图）
    private final String wordOn;
    private final String wordOff;
    private final String wordYes;
    private final String wordNo;
    private final String wordNone;
    private final String listSeparator;
    private final String groupSeparator;

    /**
     * @param snapshot 配置快照（键 → 字符串值）
     * @param synced   是否来自服务端
     * @param creative 本地玩家现在是不是创造模式（条件 {@code gamemode:creative}；不在世界里传 false）
     */
    public GuideValues(@Nonnull Map<String, String> snapshot, boolean synced, boolean creative) {
        this.synced = synced;
        this.conditions = new GuideConditions(snapshot, creative, GuideLog::warnOnce);
        this.wordOn = I18n.get("guide.essentialcraft5.ui.fmt.on");
        this.wordOff = I18n.get("guide.essentialcraft5.ui.fmt.off");
        this.wordYes = I18n.get("guide.essentialcraft5.ui.fmt.yes");
        this.wordNo = I18n.get("guide.essentialcraft5.ui.fmt.no");
        this.wordNone = I18n.get("guide.essentialcraft5.ui.fmt.none");
        this.listSeparator = I18n.get("guide.essentialcraft5.ui.fmt.list_sep");
        this.groupSeparator = groupSeparator();
    }

    /**
     * 当前界面语言的千位分隔符
     *
     * <p>没有这个键（资源包盖掉了语言文件之类）时不分组：直接取 {@code I18n.get} 会拿到键名本身，
     * 满书数字中间夹着一串键名比不分组糟得多。</p>
     *
     * @return 分隔符；空串表示不分组
     */
    static String groupSeparator() {
        return Language.getInstance().getOrDefault(GROUP_SEP_KEY, "");
    }

    /** 当前数值是否来自服务端下发 */
    public boolean isSynced() {
        return synced;
    }

    // ==================== 占位符 ====================

    /**
     * 解析一个占位符（花括号里的全部内容）
     *
     * @param token 例如 {@code cfg:machines.enrichment_chamber.min_size|int}、{@code item:essentialcraft5:soul_stone}
     * @return 显示文本；解析不了时是警告色的「?」
     */
    @Nonnull
    public Resolved placeholder(@Nonnull String token) {
        if (token.startsWith("cfg:")) {
            return cfg(token.substring(4));
        }
        if (token.startsWith("item:")) {
            return itemName(token.substring(5).trim());
        }
        if (token.startsWith("block:")) {
            return blockName(token.substring(6).trim());
        }
        if (token.startsWith("balance:")) {
            return balance(token.substring(8).trim());
        }
        if (token.startsWith("keybind:")) {
            String key = token.substring(8).trim();
            try {
                return new Resolved("[" + KeyMapping.createNameSupplier(key).get().getString() + "]", false, GuideTheme.GLOW_BRIGHT, ItemStack.EMPTY);
            } catch (RuntimeException e) {
                GuideLog.warnOnce("keybind:" + key, "{keybind:" + key + "}：取按键名失败 —— " + e);
                return MISSING;
            }
        }
        if (token.startsWith("lang:")) {
            String key = token.substring(5).trim();
            Language language = Language.getInstance();
            if (!language.has(key)) {
                GuideLog.warnOnce("lang:" + key, "语言文件里没有键 " + key);
                return MISSING;
            }
            // 取原文不走 I18n.get：带 %s 的翻译在无参数格式化时会变成「Format error」
            String text = ChatFormatting.stripFormatting(language.getOrDefault(key));
            return new Resolved(text == null ? "" : text, false);
        }
        GuideLog.warnOnce("ph:" + token, "未知占位符 {" + token + "}");
        return MISSING;
    }

    /**
     * {@code cfg:键|运算…|显示格式}
     */
    private Resolved cfg(String spec) {
        String[] parts = spec.split("\\|", -1);
        String key = parts[0].trim();
        Object v = value(key);
        if (v == null) {
            GuideLog.warnOnce("cfg:" + key, "配置快照里没有键 " + key
                    + (synced ? "（来自服务端，可能服务端版本较旧）" : "（本地配置）"));
            return MISSING;
        }
        int decimals = 2;
        String format = "raw";
        boolean sign = false;
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            int colon = p.indexOf(':');
            if (colon < 0) {
                if ("sign".equals(p)) {
                    // 修饰而不是格式：可以和 pct / pctv / x 等任意组合（{cfg:…|sign|pct}）
                    sign = true;
                } else {
                    format = p;
                }
                continue;
            }
            String op = p.substring(0, colon);
            if ("addcfg".equals(op)) {
                // 加上另一项配置的值：两项配置合成的数（「0 阶的数 + 阶 × 每阶多的数」写成 {cfg:每阶|mul:3|addcfg:0 阶的键}）
                String other = p.substring(colon + 1).trim();
                Object add = value(other);
                if (!(v instanceof Double d) || !(add instanceof Double e)) {
                    GuideLog.warnOnce("addcfg:" + spec, "{cfg:" + spec + "}：" + p + " 两边都要是数字配置");
                    return MISSING;
                }
                v = d + e;
                continue;
            }
            double arg;
            try {
                arg = Double.parseDouble(p.substring(colon + 1));
            } catch (NumberFormatException e) {
                GuideLog.warnOnce("op:" + spec, "{cfg:" + spec + "} 里的运算参数不是数字: " + p);
                continue;
            }
            if ("at".equals(op)) {
                // 列表取第 N 项（0 起），之后的运算和显示格式都作用在这一项上；取不到时整个占位符显示「?」
                Object element = arg == Math.rint(arg) ? GuideConditions.element(v, (int) arg) : null;
                if (element == null) {
                    GuideLog.warnOnce("at:" + spec, "{cfg:" + spec + "}：值不是列表，或列表没有第 " + p.substring(colon + 1) + " 项");
                    return MISSING;
                }
                v = element;
                continue;
            }
            if ("stage".equals(op)) {
                // 按阶的列表取第 N 阶，列表比 N 短时沿用最后一项（同代码里按阶取值的补法）；空列表、不是列表显示「?」
                Object element = arg == Math.rint(arg) ? GuideConditions.stageElement(v, (int) arg) : null;
                if (element == null) {
                    GuideLog.warnOnce("stage:" + spec, "{cfg:" + spec + "}：值不是列表，或列表是空的");
                    return MISSING;
                }
                v = element;
                continue;
            }
            if (!(v instanceof Double d)) {
                GuideLog.warnOnce("opnum:" + spec, "{cfg:" + spec + "}：值不是数字，忽略运算 " + p);
                continue;
            }
            switch (op) {
                case "mul" -> v = d * arg;
                case "div" -> v = arg == 0 ? d : d / arg;
                case "add" -> v = d + arg;
                case "min" -> v = Math.min(d, arg);
                case "max" -> v = Math.max(d, arg);
                case "round" -> {
                    decimals = Math.max(0, Math.min(6, (int) arg));
                    v = round(d, decimals);
                }
                default -> GuideLog.warnOnce("opname:" + spec, "{cfg:" + spec + "}：未知运算 " + op);
            }
        }
        String text = display(v, format, decimals, spec);
        if (sign && v instanceof Double d && d > 0 && hasNonZeroDigit(text)) {
            // 带符号显示：正数补「+」，负数本来就有「-」。内容里别再在占位符前写死「+」——
            // 服主把数值改成负数时会显示成「+-10%」；默认为负、没写符号的改成正数后又看不出是加是减。
            // 舍入后显示为 0 的不补，免得出现「+0%」
            text = "+" + text;
        }
        return new Resolved(text, false);
    }

    private static boolean hasNonZeroDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '1' && c <= '9') {
                return true;
            }
        }
        return false;
    }

    private String display(Object v, String format, int decimals, String spec) {
        switch (format) {
            case "", "raw" -> {
                return auto(v, decimals);
            }
            case "int" -> {
                // formatNumber 按 HALF_UP 舍入：2.5 → 3，与「四舍五入」的直觉一致（Math.rint 会给 2）
                return v instanceof Double d ? number(d, 0) : auto(v, decimals);
            }
            case "pct" -> {
                if (v instanceof Double d) {
                    return number(d * 100.0, decimals) + "%";
                }
            }
            case "pctv" -> {
                if (v instanceof Double d) {
                    return number(d, decimals) + "%";
                }
            }
            case "x" -> {
                if (v instanceof Double d) {
                    return number(d, decimals) + "×";
                }
            }
            case "sec" -> {
                if (v instanceof Double d) {
                    return I18n.get("guide.essentialcraft5.ui.fmt.sec", number(d / 20.0, decimals));
                }
            }
            case "bool" -> {
                return truthy(v) ? wordOn : wordOff;
            }
            case "yesno" -> {
                return truthy(v) ? wordYes : wordNo;
            }
            case "list" -> {
                return auto(v, decimals);
            }
            case "count" -> {
                if (v instanceof List<?> l) {
                    return String.valueOf(l.size());
                }
            }
            default -> {
                GuideLog.warnOnce("fmt:" + spec, "{cfg:" + spec + "}：未知显示格式 " + format + "，按原样显示");
                return auto(v, decimals);
            }
        }
        GuideLog.warnOnce("fmtv:" + spec, "{cfg:" + spec + "}：格式 " + format + " 不适用于这个值，按原样显示");
        return auto(v, decimals);
    }

    private String auto(Object v, int decimals) {
        if (v instanceof Boolean b) {
            return b ? wordOn : wordOff;
        }
        if (v instanceof Double d) {
            return number(d, decimals);
        }
        if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                return wordNone;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    sb.append(listSeparator);
                }
                sb.append(l.get(i));
            }
            return sb.toString();
        }
        return String.valueOf(v);
    }

    private Resolved itemName(String id) {
        return itemNames.computeIfAbsent("i:" + id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
                GuideLog.warnOnce("item:" + id, "{item:" + id + "}：物品不存在");
                return MISSING;
            }
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            try {
                ItemStack stack = new ItemStack(item);
                return new Resolved(stack.getHoverName().getString(), false, 0, stack);
            } catch (RuntimeException e) {
                // 别的模组的物品取名字可能依赖世界 / NBT，抛出来会让整篇排版失败：只把这一处换成「?」
                GuideLog.warnOnce("itemname:" + id, "{item:" + id + "}：取物品名失败 —— " + e);
                return MISSING;
            }
        });
    }

    /** 方块名：给岩浆、水、火这类没有物品形态的方块用；有物品形态的同样做成物品链接 */
    private Resolved blockName(String id) {
        return itemNames.computeIfAbsent("b:" + id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || !ForgeRegistries.BLOCKS.containsKey(rl)) {
                GuideLog.warnOnce("block:" + id, "{block:" + id + "}：方块不存在");
                return MISSING;
            }
            Block block = ForgeRegistries.BLOCKS.getValue(rl);
            try {
                Item item = block.asItem();
                ItemStack stack = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
                return new Resolved(block.getName().getString(), false, 0, stack);
            } catch (RuntimeException e) {
                GuideLog.warnOnce("blockname:" + id, "{block:" + id + "}：取方块名失败 —— " + e);
                return MISSING;
            }
        });
    }

    /** 平衡值三态的名字（和机器界面同一个翻译键），带各自的颜色 */
    private static Resolved balance(String value) {
        int color = switch (value) {
            case "frozen" -> GuideTheme.FROZEN;
            case "pure" -> GuideTheme.PURE;
            case "chaos" -> GuideTheme.CHAOS;
            default -> 0;
        };
        if (color == 0) {
            GuideLog.warnOnce("balance:" + value, "{balance:" + value + "}：只能是 frozen / pure / chaos");
            return MISSING;
        }
        return new Resolved(I18n.get("gui.essentialcraft5.balance." + value), false, color, ItemStack.EMPTY);
    }

    // ==================== 条件 ====================

    /**
     * 一组条件全部成立（空表 = 无条件），规则见 {@link GuideConditions#test(String)}
     *
     * @param conds 条件列表
     * @return 是否成立
     */
    public boolean test(@Nonnull List<String> conds) {
        return conditions.test(conds);
    }

    /**
     * 单个条件，规则见 {@link GuideConditions#test(String)}
     *
     * @param cond 条件
     * @return 是否成立
     */
    public boolean test(@Nonnull String cond) {
        return conditions.test(cond);
    }

    private static boolean truthy(Object v) {
        return GuideConditions.truthy(v);
    }

    @Nullable
    private Object value(String key) {
        return conditions.value(key);
    }

    // ==================== 数字 ====================

    private static double round(double v, int decimals) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return v;
        }
        return new BigDecimal(Double.toString(v)).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 数字显示：最多 maxDecimals 位小数、四舍五入、去尾零（{@code 1.0 → 1}、{@code 0.250 → 0.25}）
     *
     * @param v           值
     * @param maxDecimals 最多几位小数
     * @return 文本
     */
    public static String formatNumber(double v, int maxDecimals) {
        return formatNumber(v, maxDecimals, "");
    }

    /**
     * 同 {@link #formatNumber(double, int)}，整数部分 ≥ 1000 时每三位插一个分隔符（负号、小数部分照旧）
     *
     * @param v           值
     * @param maxDecimals 最多几位小数
     * @param groupSep    千位分隔符；空串表示不分组
     * @return 文本
     */
    public static String formatNumber(double v, int maxDecimals, String groupSep) {
        if (Double.isNaN(v)) {
            return "?";
        }
        if (Double.isInfinite(v) || Math.abs(v) >= 1e15) {
            // Double.MAX_VALUE 这类「无上限」写法，展开成三百多位数字毫无意义
            return v < 0 ? "-∞" : "∞";
        }
        BigDecimal bd = new BigDecimal(Double.toString(v)).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros();
        if (bd.signum() == 0) {
            return "0";
        }
        return NumberText.group(bd.toPlainString(), groupSep);
    }

    /** 按本实例的界面语言格式化数字（带千位分隔） */
    private String number(double v, int maxDecimals) {
        return formatNumber(v, maxDecimals, groupSeparator);
    }
}
