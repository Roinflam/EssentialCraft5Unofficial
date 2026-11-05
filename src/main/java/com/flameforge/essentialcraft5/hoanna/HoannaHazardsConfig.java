package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 第六批 6a M4「辐射、天灾与毒雾」的服务端配置（SERVER，由 {@code ECHoannaHazards.init()} 登记进 general 小节，batch6a.md §5.4）：
 * <ul>
 *   <li>{@code general.radiation.enabled}（默认 true）：海纳尔辐射。关掉后不再积累（区块、μ 指环、毒雾、ξ 腰带都不加），已有的剂量照常消退，
 *       辐射效果（压生命上限）立刻撤掉。</li>
 *   <li>{@code general.radiation.cap_loss_percent_per_level}（默认 0.05）、{@code general.radiation.max_cap_loss_percent}（默认 0.5）：
 *       辐射每级把生命上限压低当前最大生命的多少、合计最多压低多少（2026-10-13 起按比例，原作每级 1 点；见 {@code effect/RadiationEffect}）。</li>
 *   <li>{@code general.hoanna_events.<事件>_enabled}（sun_array / fumes / darkness / earthquake，默认都 true）：这种天灾会不会自己发生。
 *       用布尔而不是列表：研究手册的 {@code if} 只支持布尔 / 数值配置。管理员用 {@code /ec5 hoanna_event start} 照样能开。</li>
 *   <li>{@code general.hoanna_events.chance_multiplier}（默认 1.0，0~10）：所有天灾发生概率的倍数，0 = 不会自己发生。</li>
 *   <li>{@code general.hoanna_events.earthquake_push}（默认 true）：地震推人（每秒小踉跄、偶尔强震）；关掉后只剩画面。</li>
 *   <li>{@code general.hoanna_events.fumes_max_nearby}（默认 24，0~64）：毒雾期间，64 格内已有这么多只毒雾就不再自然生成；
 *       0 = 不刷毒雾，毒雾天灾也不再自己发生（{@link #eventEnabled}；否则只剩红字和提示条、一团雾都没有，6a 审查 M4-6），指令照样能开。</li>
 *   <li>地脉风暴（第十三批 13d，batch13.md §6.2）：{@code general.hoanna_events.ley_storm_enabled}（默认 true）、{@code ley_storm_chance}（默认 0.3，
 *       大潮夜黄昏那一次的几率，再乘 {@code chance_multiplier}）、{@code ley_storm_starfall_per_player}（默认 8，每人名下同时最多几个落晶，0 = 不落晶）、
 *       {@code ley_storm_starfall_interval}（默认 100 刻，多久给每个露天的人掷一次落晶）、{@code ley_storm_radiation}（默认 2，露天每秒的基础剂量，
 *       至少吃一半易伤；0 = 不加）。魔力潮汐关掉时永远没有大潮，风暴也就不会自己来。</li>
 * </ul>
 * 读值一律走这里的静态方法（配置没加载时返回默认值）。GameTest 用 {@code setTest*} 临时覆盖，<b>不许用 ConfigValue.set</b>
 * （服务端配置自动存盘、文件监视器后台重读会冲掉刚设的值）；覆盖值是全局的，只在同一段同步代码里设、测，再 {@link #clearTestOverrides()}。
 */
public final class HoannaHazardsConfig {

    public static final boolean DEFAULT_RADIATION_ENABLED = true;
    public static final double DEFAULT_CHANCE_MULTIPLIER = 1.0D;
    public static final double MAX_CHANCE_MULTIPLIER = 10.0D;
    public static final boolean DEFAULT_EARTHQUAKE_PUSH = true;
    public static final int DEFAULT_FUMES_MAX_NEARBY = 24;
    public static final int MAX_FUMES_MAX_NEARBY = 64;
    /** 地脉风暴：大潮夜黄昏掷一次的几率 */
    public static final double DEFAULT_LEY_STORM_CHANCE = 0.3D;
    /** 地脉风暴：每人名下同时最多几个落晶 */
    public static final int DEFAULT_STARFALL_PER_PLAYER = 8;
    public static final int MAX_STARFALL_PER_PLAYER = 64;
    /** 地脉风暴：多久给每个露天的人掷一次落晶（刻） */
    public static final int DEFAULT_STARFALL_INTERVAL = 100;
    public static final int MIN_STARFALL_INTERVAL = 20;
    public static final int MAX_STARFALL_INTERVAL = 6_000;
    /** 地脉风暴：露天每秒的基础剂量 */
    public static final int DEFAULT_LEY_STORM_RADIATION = 2;
    public static final int MAX_LEY_STORM_RADIATION = 1_000;
    /** 每级压低最大生命的 5%（原作每级 1 点；20 点生命时一样） */
    public static final double DEFAULT_CAP_LOSS_PER_LEVEL = 0.05D;
    /** 合计最多压低一半 */
    public static final double DEFAULT_MAX_CAP_LOSS = 0.5D;

    @Nullable
    private static ForgeConfigSpec.BooleanValue radiationEnabled;
    @Nullable
    private static ForgeConfigSpec.BooleanValue sunArrayEnabled;
    @Nullable
    private static ForgeConfigSpec.BooleanValue fumesEnabled;
    @Nullable
    private static ForgeConfigSpec.BooleanValue darknessEnabled;
    @Nullable
    private static ForgeConfigSpec.BooleanValue earthquakeEnabled;
    @Nullable
    private static ForgeConfigSpec.DoubleValue chanceMultiplier;
    @Nullable
    private static ForgeConfigSpec.BooleanValue earthquakePush;
    @Nullable
    private static ForgeConfigSpec.IntValue fumesMaxNearby;
    @Nullable
    private static ForgeConfigSpec.BooleanValue leyStormEnabled;
    @Nullable
    private static ForgeConfigSpec.DoubleValue leyStormChance;
    @Nullable
    private static ForgeConfigSpec.IntValue starfallPerPlayer;
    @Nullable
    private static ForgeConfigSpec.IntValue starfallInterval;
    @Nullable
    private static ForgeConfigSpec.IntValue leyStormRadiation;
    @Nullable
    private static ForgeConfigSpec.DoubleValue capLossPerLevel;
    @Nullable
    private static ForgeConfigSpec.DoubleValue maxCapLoss;

    @Nullable
    private static volatile Boolean testRadiationEnabled;
    @Nullable
    private static volatile Double testChanceMultiplier;
    @Nullable
    private static volatile Boolean testEarthquakePush;
    @Nullable
    private static volatile Integer testFumesMaxNearby;
    @Nullable
    private static volatile Double testCapLossPerLevel;
    @Nullable
    private static volatile Double testMaxCapLoss;
    @Nullable
    private static volatile Boolean testLeyStormEnabled;
    @Nullable
    private static volatile Double testLeyStormChance;
    @Nullable
    private static volatile Integer testStarfallPerPlayer;
    @Nullable
    private static volatile Integer testStarfallInterval;
    @Nullable
    private static volatile Integer testLeyStormRadiation;

    private HoannaHazardsConfig() {
    }

    /** {@code ECConfig.general(HoannaHazardsConfig::define)}（ECHoannaHazards.init 调） */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Hoanna radiation.", "海纳尔辐射").push("radiation");
        radiationEnabled = builder.comment(
                        "Players slowly gather radiation in Hoanna (every 10,000 dose lowers maximum health). "
                                + "When off, nothing adds radiation any more (chunks, the mu ring, poison fumes, the xi belt), "
                                + "existing dose still fades and the radiation effect is removed.",
                        "在海纳尔会慢慢积累辐射（每 1 万剂量压低一次生命上限）。关掉后什么都不再加辐射（区块、μ 指环、毒雾、ξ 腰带），"
                                + "已有的剂量照常消退，辐射效果撤掉")
                .define("enabled", DEFAULT_RADIATION_ENABLED);
        capLossPerLevel = builder.comment(
                        "How much each radiation level lowers the health cap, as a fraction of the player's current max health (0.05 = 5%). "
                                + "Radiation II counts as 2 levels, III as 3 and so on; with the delta ring, (numeral - 1) / 2 + 1 rounded down"
                                + " (Radiation II counts as 1 level, III and IV as 2, V as 3).",
                        "辐射每级把生命上限压低当前最大生命的多少（0.05 = 5%）。辐射 II 算 2 级、III 算 3 级，依此类推；"
                                + "戴 δ 指环时按（罗马数字 − 1）/ 2 + 1 算、向下取整（辐射 II 算 1 级，III、IV 算 2 级，V 算 3 级）")
                .defineInRange("cap_loss_percent_per_level", DEFAULT_CAP_LOSS_PER_LEVEL, 0D, 1D);
        maxCapLoss = builder.comment(
                        "Radiation lowers the health cap by at most this fraction of max health in total (0.5 = half). The cap never goes below 1.",
                        "辐射合计最多把生命上限压低最大生命的多少（0.5 = 一半）。上限最低留 1 点")
                .defineInRange("max_cap_loss_percent", DEFAULT_MAX_CAP_LOSS, 0D, 1D);
        builder.pop();

        builder.comment("Hoanna calamities (sun array, poison fumes, darkness, earthquake, ley storm). They only happen while someone is in Hoanna.",
                "海纳尔的天灾（日阵、毒雾、黑暗、地震、地脉风暴），只在有人待在海纳尔时发生").push("hoanna_events");
        sunArrayEnabled = builder.comment("Sun array can happen on its own (rolled once at dawn).", "日阵会不会自己发生（每天黎明掷一次）")
                .define(HoannaEvent.SUN_ARRAY.key() + "_enabled", true);
        fumesEnabled = builder.comment("Poison fumes can happen on its own.", "毒雾会不会自己发生")
                .define(HoannaEvent.FUMES.key() + "_enabled", true);
        darknessEnabled = builder.comment("Darkness can happen on its own.", "黑暗会不会自己发生")
                .define(HoannaEvent.DARKNESS.key() + "_enabled", true);
        earthquakeEnabled = builder.comment("Earthquake can happen on its own.", "地震会不会自己发生")
                .define(HoannaEvent.EARTHQUAKE.key() + "_enabled", true);
        chanceMultiplier = builder.comment("Multiplier on the chance of every calamity. 0 = they never start on their own "
                                + "(/ec5 hoanna_event start still works).",
                        "所有天灾发生概率的倍数；0 = 不会自己发生（/ec5 hoanna_event start 照样能开）")
                .defineInRange("chance_multiplier", DEFAULT_CHANCE_MULTIPLIER, 0D, MAX_CHANCE_MULTIPLIER);
        earthquakePush = builder.comment("Earthquakes nudge players on the ground once a second and now and then jolt them "
                                + "(never hard enough to cause fall damage). When off only the camera shakes.",
                        "地震每秒推一下站在地上的玩家、偶尔来一次强震（不会高到摔伤）；关掉后只剩画面晃动")
                .define("earthquake_push", DEFAULT_EARTHQUAKE_PUSH);
        fumesMaxNearby = builder.comment("During poison fumes, no more fumes spawn naturally once this many are within 64 blocks. "
                                + "0 = no fumes spawn and the poison fumes calamity no longer starts on its own (/ec5 hoanna_event start still works).",
                        "毒雾期间，64 格内已有这么多只毒雾就不再自然生成；0 = 不刷毒雾，毒雾天灾也不再自己发生（/ec5 hoanna_event start 照样能开）")
                .defineInRange("fumes_max_nearby", DEFAULT_FUMES_MAX_NEARBY, 0, MAX_FUMES_MAX_NEARBY);
        leyStormEnabled = builder.comment("Ley storm can happen on its own. It is rolled once at dusk on spring-tide nights "
                                + "(the three nights around the full moon); with the mana tide turned off there is no spring tide, so it never comes on its own.",
                        "地脉风暴会不会自己发生。只在大潮夜（满月前后那三夜）的黄昏掷一次；关掉魔力潮汐时没有大潮，它也就不会自己来")
                .define(HoannaEvent.LEY_STORM.key() + "_enabled", true);
        leyStormChance = builder.comment("Chance of a ley storm at dusk on a spring-tide night (also multiplied by chance_multiplier).",
                        "大潮夜黄昏起地脉风暴的几率（还要乘 chance_multiplier）")
                .defineInRange("ley_storm_chance", DEFAULT_LEY_STORM_CHANCE, 0D, 1D);
        starfallPerPlayer = builder.comment("During a ley storm, at most this many ley starfalls exist for each player at once. 0 = no starfalls.",
                        "地脉风暴期间，每个人名下同时最多有几个地脉落晶；0 = 不落晶")
                .defineInRange("ley_storm_starfall_per_player", DEFAULT_STARFALL_PER_PLAYER, 0, MAX_STARFALL_PER_PLAYER);
        starfallInterval = builder.comment("During a ley storm, every this many ticks each player under the open sky has a 60% chance to get a starfall nearby.",
                        "地脉风暴期间，每隔多少刻给每个露天的人掷一次（六成几率在附近落一个地脉落晶）")
                .defineInRange("ley_storm_starfall_interval", DEFAULT_STARFALL_INTERVAL, MIN_STARFALL_INTERVAL, MAX_STARFALL_INTERVAL);
        leyStormRadiation = builder.comment("During a ley storm, radiation dose per second for players under the open sky. "
                                + "Armor can lower it to half at most. 0 = none. The storm never deals damage.",
                        "地脉风暴期间，露天每秒加多少辐射剂量；盔甲最多减到一半。0 = 不加。风暴本身不造成伤害")
                .defineInRange("ley_storm_radiation", DEFAULT_LEY_STORM_RADIATION, 0, MAX_LEY_STORM_RADIATION);
        builder.pop();
    }

    // ------------------------------------------------------------------ 读值

    public static boolean radiationEnabled() {
        Boolean test = testRadiationEnabled;
        if (test != null) {
            return test;
        }
        return radiationEnabled == null ? DEFAULT_RADIATION_ENABLED : ECConfig.get(radiationEnabled);
    }

    /**
     * 这种天灾会不会自己发生（NONE 恒 false；毒雾还要 {@code fumes_max_nearby > 0}，不然开始了也刷不出一团雾）。
     * 地脉风暴读 {@code ley_storm_enabled}；它不在常规掷骰的四种里，这里只给大潮夜黄昏那一次用。
     */
    public static boolean eventEnabled(HoannaEvent event) {
        ForgeConfigSpec.BooleanValue value = switch (event) {
            case SUN_ARRAY -> sunArrayEnabled;
            case FUMES -> fumesEnabled;
            case DARKNESS -> darknessEnabled;
            case EARTHQUAKE -> earthquakeEnabled;
            case LEY_STORM -> leyStormEnabled;
            case NONE -> null;
        };
        if (event.isNone()) {
            return false;
        }
        if (event == HoannaEvent.LEY_STORM) {
            Boolean test = testLeyStormEnabled;
            if (test != null) {
                return test;
            }
        }
        boolean enabled = value == null || ECConfig.get(value);
        return event == HoannaEvent.FUMES ? enabled && fumesMaxNearby() > 0 : enabled;
    }

    /**
     * 天灾能不能自己发生：{@code chance_multiplier > 0}，且四种天灾里至少一种开着（{@link #eventEnabled}，毒雾含 {@code fumes_max_nearby > 0}），
     * 或者地脉风暴开着、几率 > 0、魔力潮汐起作用（没有大潮就不会来）。为假时只剩 /ec5 hoanna_event start 能开天灾，
     * 要天灾才能做的事（天灾之心的 {@code require_calamity}）按不要求处理，免得主线卡住。
     */
    public static boolean calamitiesCanStartNaturally() {
        return calamitiesCanStartNaturally(chanceMultiplier(), HoannaHazardsConfig::eventEnabled, leyStormChance(), HoannaTide.active());
    }

    /** {@link #calamitiesCanStartNaturally()} 的纯函数版（GameTest 测各种配置组合；enabled 同 {@link #eventEnabled}） */
    public static boolean calamitiesCanStartNaturally(double multiplier, Predicate<HoannaEvent> enabled, double leyStormChance, boolean tide) {
        if (multiplier <= 0D) {
            return false;
        }
        if (enabled.test(HoannaEvent.SUN_ARRAY) || enabled.test(HoannaEvent.FUMES) || enabled.test(HoannaEvent.DARKNESS)
                || enabled.test(HoannaEvent.EARTHQUAKE)) {
            return true;
        }
        return enabled.test(HoannaEvent.LEY_STORM) && leyStormChance > 0D && tide;
    }

    public static double chanceMultiplier() {
        Double test = testChanceMultiplier;
        if (test != null) {
            return test;
        }
        return chanceMultiplier == null ? DEFAULT_CHANCE_MULTIPLIER : ECConfig.get(chanceMultiplier);
    }

    public static boolean earthquakePush() {
        Boolean test = testEarthquakePush;
        if (test != null) {
            return test;
        }
        return earthquakePush == null ? DEFAULT_EARTHQUAKE_PUSH : ECConfig.get(earthquakePush);
    }

    public static int fumesMaxNearby() {
        Integer test = testFumesMaxNearby;
        if (test != null) {
            return test;
        }
        return fumesMaxNearby == null ? DEFAULT_FUMES_MAX_NEARBY : ECConfig.get(fumesMaxNearby);
    }

    /** 辐射每级压低最大生命的比例（0~1） */
    public static double capLossPerLevel() {
        Double test = testCapLossPerLevel;
        if (test != null) {
            return test;
        }
        return capLossPerLevel == null ? DEFAULT_CAP_LOSS_PER_LEVEL : ECConfig.get(capLossPerLevel);
    }

    /** 辐射合计最多压低最大生命的比例（0~1） */
    public static double maxCapLoss() {
        Double test = testMaxCapLoss;
        if (test != null) {
            return test;
        }
        return maxCapLoss == null ? DEFAULT_MAX_CAP_LOSS : ECConfig.get(maxCapLoss);
    }

    /** 大潮夜黄昏起地脉风暴的几率（0~1，还没乘 chance_multiplier） */
    public static double leyStormChance() {
        Double test = testLeyStormChance;
        if (test != null) {
            return test;
        }
        return leyStormChance == null ? DEFAULT_LEY_STORM_CHANCE : ECConfig.get(leyStormChance);
    }

    /** 每人名下同时最多几个地脉落晶（0 = 不落晶） */
    public static int starfallPerPlayer() {
        Integer test = testStarfallPerPlayer;
        if (test != null) {
            return test;
        }
        return starfallPerPlayer == null ? DEFAULT_STARFALL_PER_PLAYER : ECConfig.get(starfallPerPlayer);
    }

    /** 多久给每个露天的人掷一次落晶（刻，≥ {@value #MIN_STARFALL_INTERVAL}） */
    public static int starfallInterval() {
        Integer test = testStarfallInterval;
        int value = test != null ? test : starfallInterval == null ? DEFAULT_STARFALL_INTERVAL : ECConfig.get(starfallInterval);
        return Math.max(MIN_STARFALL_INTERVAL, value);
    }

    /** 风暴中露天每秒的基础剂量（0 = 不加） */
    public static int leyStormRadiation() {
        Integer test = testLeyStormRadiation;
        if (test != null) {
            return test;
        }
        return leyStormRadiation == null ? DEFAULT_LEY_STORM_RADIATION : ECConfig.get(leyStormRadiation);
    }

    // ------------------------------------------------------------------ GameTest 覆盖（null = 按配置）

    /** 地脉风暴那一组（null = 按配置）：开关、几率、每人落晶上限、落晶间隔、每秒剂量 */
    public static void setTestLeyStorm(@Nullable Boolean enabled, @Nullable Double chance, @Nullable Integer perPlayer,
                                       @Nullable Integer interval, @Nullable Integer radiation) {
        testLeyStormEnabled = enabled;
        testLeyStormChance = chance;
        testStarfallPerPlayer = perPlayer;
        testStarfallInterval = interval;
        testLeyStormRadiation = radiation;
    }

    public static void setTestRadiationEnabled(@Nullable Boolean value) {
        testRadiationEnabled = value;
    }

    public static void setTestChanceMultiplier(@Nullable Double value) {
        testChanceMultiplier = value;
    }

    public static void setTestEarthquakePush(@Nullable Boolean value) {
        testEarthquakePush = value;
    }

    public static void setTestFumesMaxNearby(@Nullable Integer value) {
        testFumesMaxNearby = value;
    }

    public static void setTestCapLoss(@Nullable Double perLevel, @Nullable Double max) {
        testCapLossPerLevel = perLevel;
        testMaxCapLoss = max;
    }

    /** 全部覆盖值置回 null（测试 finally 里调） */
    public static void clearTestOverrides() {
        testRadiationEnabled = null;
        testChanceMultiplier = null;
        testEarthquakePush = null;
        testFumesMaxNearby = null;
        testCapLossPerLevel = null;
        testMaxCapLoss = null;
        testLeyStormEnabled = null;
        testLeyStormChance = null;
        testStarfallPerPlayer = null;
        testStarfallInterval = null;
        testLeyStormRadiation = null;
    }
}
