package com.flameforge.essentialcraft5.item.module;

import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 四块生活模块里三块有配置（第十二批，batch12.md §4.7.4），挂在 {@code general.computer_modules} 下面：
 * {@code attraction.*}（吸附）、{@code stride.*}（攀行）、{@code veil.*}（隐匿）。稳手没有小节：不收电、倍数是常量，见 {@link UtilityModuleEvents}。
 * 随进服同步给客户端（提示行、手册用同一份数）。由 {@code registry/ECUtilityModules.init} 经 {@link ModuleConfig#section} 登记。
 * 读值走 {@link #attraction()} / {@link #stride()} / {@link #veil()}（没加载时是默认值）；GameTest 用各自的测试覆盖值，不碰真配置。
 */
public final class UtilityModuleConfig {

    /**
     * 吸附的数值。
     *
     * @param mruPerSecond  开着时每秒的电费（模块平台每秒统一扣）
     * @param radius        水平范围（格）：玩家脚下 1×1 的框往四周各扩这么多，竖直照磁化法杖（脚下 6 格到脚上 7 格）
     * @param intervalTicks 每几刻推一次（配置值；实际节拍看 {@link #effectiveIntervalTicks()}）
     */
    public record Attraction(int mruPerSecond, int radius, int intervalTicks) {

        /**
         * 实际节拍：半径超过 {@value UtilityModuleConfig#WIDE_RADIUS} 格时至少 {@value UtilityModuleConfig#WIDE_MIN_INTERVAL} 刻一次。
         * 半径 8 的查询框是 17 × 13 × 17，已到 policy P2「≤ 17³ 至少每 4 刻」那一档的边；再大就按「更大的至少每 10 刻」走，
         * 配置里把半径调到 12、间隔留在 4 也不会超（batch12.md §6.1 P2）
         */
        public int effectiveIntervalTicks() {
            int interval = Math.max(MIN_INTERVAL, intervalTicks);
            return radius > WIDE_RADIUS ? Math.max(WIDE_MIN_INTERVAL, interval) : interval;
        }
    }

    /** 吸附间隔的下限（配置范围也从这里起） */
    public static final int MIN_INTERVAL = 4;
    /** 半径超过这么多格算大范围 */
    public static final int WIDE_RADIUS = 8;
    /** 大范围时的最短间隔 */
    public static final int WIDE_MIN_INTERVAL = 10;

    /**
     * 攀行的数值。
     *
     * @param mruPerSecond 开着时每秒的电费
     * @param stepBonus    步高加多少格（加在原版 0.6 和宅男靴子自带的 0.6 上面）
     */
    public record Stride(int mruPerSecond, double stepBonus) {
    }

    /**
     * 隐匿的数值。
     *
     * @param mruPerSecond    开着时每秒的电费
     * @param multiplier      怪物发现你的距离乘多少
     * @param sneakMultiplier 潜行时改乘这个（和原版潜行自带的 × 0.8 连乘）
     */
    public record Veil(int mruPerSecond, double multiplier, double sneakMultiplier) {
    }

    /** 吸附默认：每秒 20 MRU、水平 6 格（法杖 12 的一半）、每 10 刻一次（batch12.md §4.7.3） */
    public static final Attraction ATTRACTION_DEFAULTS = new Attraction(20, 6, 10);
    /**
     * 攀行默认：每秒 10 MRU、步高 +0.4。宅男靴子本身已经给了 +0.6（原版 0.6 → 1.2，{@code ComputerArmorItem} 的 ArmorBonus），
     * 再加 0.4 正好 1.6 格：一格方块加半砖能直接走上去，不到两格（batch12.md §4.7.3 要的是 1.6 格，表里写的 +1.0 没算靴子自带的那 0.6，加上去会到 2.2）
     */
    public static final Stride STRIDE_DEFAULTS = new Stride(10, 0.4D);
    /** 隐匿默认：每秒 10 MRU、× 0.7（和原版隐身药水同档）、潜行 × 0.25 */
    public static final Veil VEIL_DEFAULTS = new Veil(10, 0.7D, 0.25D);

    @Nullable
    private static ForgeConfigSpec.IntValue attractionMru;
    @Nullable
    private static ForgeConfigSpec.IntValue attractionRadius;
    @Nullable
    private static ForgeConfigSpec.IntValue attractionInterval;
    @Nullable
    private static ForgeConfigSpec.IntValue strideMru;
    @Nullable
    private static ForgeConfigSpec.DoubleValue strideBonus;
    @Nullable
    private static ForgeConfigSpec.IntValue veilMru;
    @Nullable
    private static ForgeConfigSpec.DoubleValue veilMultiplier;
    @Nullable
    private static ForgeConfigSpec.DoubleValue veilSneakMultiplier;

    @Nullable
    private static volatile Attraction testAttraction;
    @Nullable
    private static volatile Stride testStride;
    @Nullable
    private static volatile Veil testVeil;

    private UtilityModuleConfig() {
    }

    /** 写在 general.computer_modules 里面（ModuleConfig.section 调） */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Attraction Module (leggings): pulls nearby item drops and experience orbs towards you; items someone threw are left alone.",
                "吸附模块（护腿）：把身边的掉落物和经验球往你身上推；有人扔出来的掉落物不吸").push("attraction");
        attractionMru = builder.comment("MRU per second while the module is on (paid by the module upkeep once a second).",
                        "开着时每秒耗的 MRU（模块每秒统一扣电时一起扣）")
                .defineInRange("mru_per_second", ATTRACTION_DEFAULTS.mruPerSecond(), 0, 10_000);
        attractionRadius = builder.comment("Horizontal reach in blocks around the wearer; vertically it reaches 6 blocks down and 7 up, like the Magnetizing Staff.",
                        "水平范围（格）；竖直方向照磁化法杖，脚下 6 格到脚上 7 格")
                .defineInRange("radius", ATTRACTION_DEFAULTS.radius(), 1, 12);
        attractionInterval = builder.comment("Ticks between two pulls (20 = 1 second). When radius is above 8, at least 10 ticks are used whatever this says.",
                        "每隔多少刻推一次（20 = 1 秒）；radius 超过 8 时不管这里写多少，实际至少 10 刻一次")
                .defineInRange("interval_ticks", ATTRACTION_DEFAULTS.intervalTicks(), MIN_INTERVAL, 100);
        builder.pop();

        builder.comment("Stride Module (boots): lets you walk straight up higher steps.",
                "攀行模块（靴子）：能直接走上更高的台阶").push("stride");
        strideMru = builder.comment("MRU per second while the module is on.",
                        "开着时每秒耗的 MRU")
                .defineInRange("mru_per_second", STRIDE_DEFAULTS.mruPerSecond(), 0, 10_000);
        strideBonus = builder.comment("Blocks added to the step height, on top of vanilla's 0.6 and the 0.6 the suit's boots already give (0.4 = 1.6 blocks in total).",
                        "步高加多少格，加在原版 0.6 和宅男靴子自带的 0.6 上面（0.4 = 合计 1.6 格）")
                .defineInRange("step_bonus", STRIDE_DEFAULTS.stepBonus(), 0.0D, 2.0D);
        builder.pop();

        builder.comment("Veil Module (helmet): mobs notice you from shorter distances. Has no effect on players.",
                "隐匿模块（头盔）：怪物要离你更近才发现你；对玩家没有效果").push("veil");
        veilMru = builder.comment("MRU per second while the module is on.",
                        "开着时每秒耗的 MRU")
                .defineInRange("mru_per_second", VEIL_DEFAULTS.mruPerSecond(), 0, 10_000);
        veilMultiplier = builder.comment("Multiplier on the distance at which mobs notice you.",
                        "怪物发现你的距离乘多少")
                .defineInRange("multiplier", VEIL_DEFAULTS.multiplier(), 0.0D, 1.0D);
        veilSneakMultiplier = builder.comment("Used instead of multiplier while sneaking (vanilla sneaking's own x0.8 still applies on top).",
                        "潜行时改乘这个（原版潜行自带的 × 0.8 照样连乘）")
                .defineInRange("sneak_multiplier", VEIL_DEFAULTS.sneakMultiplier(), 0.0D, 1.0D);
        builder.pop();
    }

    /** 吸附：测试覆盖值 → 配置（没加载时默认值） */
    public static Attraction attraction() {
        Attraction override = testAttraction;
        if (override != null) {
            return override;
        }
        if (attractionMru == null) {
            return ATTRACTION_DEFAULTS;
        }
        return new Attraction(ECConfig.get(attractionMru), ECConfig.get(attractionRadius), ECConfig.get(attractionInterval));
    }

    /** 攀行：测试覆盖值 → 配置（没加载时默认值） */
    public static Stride stride() {
        Stride override = testStride;
        if (override != null) {
            return override;
        }
        if (strideMru == null) {
            return STRIDE_DEFAULTS;
        }
        return new Stride(ECConfig.get(strideMru), ECConfig.get(strideBonus));
    }

    /** 隐匿：测试覆盖值 → 配置（没加载时默认值） */
    public static Veil veil() {
        Veil override = testVeil;
        if (override != null) {
            return override;
        }
        if (veilMru == null) {
            return VEIL_DEFAULTS;
        }
        return new Veil(ECConfig.get(veilMru), ECConfig.get(veilMultiplier), ECConfig.get(veilSneakMultiplier));
    }

    /** GameTest 用：吸附整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
    public static void setTestAttraction(@Nullable Attraction values) {
        testAttraction = values;
    }

    /** GameTest 用：攀行，写法同 {@link #setTestAttraction} */
    public static void setTestStride(@Nullable Stride values) {
        testStride = values;
    }

    /** GameTest 用：隐匿，写法同 {@link #setTestAttraction} */
    public static void setTestVeil(@Nullable Veil values) {
        testVeil = values;
    }
}
