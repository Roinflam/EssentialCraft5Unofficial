package com.flameforge.essentialcraft5.item.module;

import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 三块生存模块的服务端配置（第十批 10c-M2，batch10.md §5.11），挂在 {@code general.computer_modules} 下面：
 * {@code rewind.*}（回溯）、{@code matrix_filter.*}（矩阵过滤）、{@code matrix_shunt.*}（矩阵分流）。随进服同步给客户端（提示行、手册用同一份数）。
 * 由 {@code registry/ECSurvivalModules.init} 经 {@link ModuleConfig#section} 登记。读值走 {@link #rewind()} / {@link #filter()} / {@link #shunt()}
 * （没加载时是默认值）；GameTest 用各自的测试覆盖值，不碰真配置。
 */
public final class SurvivalModuleConfig {

    /**
     * 回溯的数值。
     *
     * @param secondsBack   退回大约几秒前（快照每秒一条，留最近 secondsBack × 2 条）
     * @param minAgeTicks   快照至少要多少刻以前的才用（刚记下的那条多半就站在出事的地方）
     * @param cooldownTicks 救下一次以后的冷却（刻）
     * @param mruCost       每救一次花的 MRU
     * @param matrixDamage  每救一次加的矩阵损伤（夹在上限以内）
     * @param rescueVoid    掉进虚空摔死的救不救
     */
    public record Rewind(int secondsBack, int minAgeTicks, int cooldownTicks, int mruCost, int matrixDamage, boolean rescueVoid) {
    }

    /**
     * 矩阵过滤的数值。
     *
     * @param matrixPerEffectSecond 拦下一个有害效果：每秒持续时间 ×（等级 + 1）记这么多矩阵损伤
     * @param mruPerEffect          每拦下一个新的有害效果花的 MRU（同一个效果在已拦下的时间里再来不收）
     */
    public record Filter(int matrixPerEffectSecond, int mruPerEffect) {
    }

    /**
     * 矩阵分流的数值。
     *
     * @param ratio              魔法伤害里分流掉的比例（0~0.9；不到 1，剩下的那点伤害照常走完伤害流程，矩阵损伤才记得上）
     * @param matrixPerMaxHealth 分流掉的伤害正好等于受害者的最大生命时记多少矩阵损伤（按比例折算：分走最大生命的 1% 记它的 1 / 100）
     */
    public record Shunt(double ratio, int matrixPerMaxHealth) {
    }

    /** 回溯默认：约 5 秒前、至少 3 秒前的快照、冷却 5 分钟、每次 5 万 MRU + 5 000 矩阵损伤、掉进虚空也救（D11 A） */
    public static final Rewind REWIND_DEFAULTS = new Rewind(5, 60, 6000, 50_000, 5000, true);
    /** 矩阵过滤默认：每秒 × 级 20 点矩阵损伤、每个 500 MRU */
    public static final Filter FILTER_DEFAULTS = new Filter(20, 500);
    /**
     * 矩阵分流默认：一半；按最大生命的比例记，2000 = 最大生命 20 点（原版）时每点 100，和以前按点数换算的数一样。
     * 以前是每点伤害固定 100：整合包里生命几百上千、法术一下几百点，几下就把损伤记满了；按比例换算以后血再多也和原版一样耐用
     * （同第十一批腐化扣血按最大生命的口径）
     */
    public static final Shunt SHUNT_DEFAULTS = new Shunt(0.5D, 2000);
    /** 分流比例的上限 */
    public static final double MAX_SHUNT_RATIO = 0.9D;

    @Nullable
    private static ForgeConfigSpec.IntValue secondsBack;
    @Nullable
    private static ForgeConfigSpec.IntValue minAgeTicks;
    @Nullable
    private static ForgeConfigSpec.IntValue cooldownTicks;
    @Nullable
    private static ForgeConfigSpec.IntValue mruCost;
    @Nullable
    private static ForgeConfigSpec.IntValue matrixDamage;
    @Nullable
    private static ForgeConfigSpec.BooleanValue rescueVoid;
    @Nullable
    private static ForgeConfigSpec.IntValue matrixPerEffectSecond;
    @Nullable
    private static ForgeConfigSpec.IntValue mruPerEffect;
    @Nullable
    private static ForgeConfigSpec.DoubleValue ratio;
    @Nullable
    private static ForgeConfigSpec.IntValue matrixPerMaxHealth;

    @Nullable
    private static volatile Rewind testRewind;
    @Nullable
    private static volatile Filter testFilter;
    @Nullable
    private static volatile Shunt testShunt;

    private SurvivalModuleConfig() {
    }

    /** 写在 general.computer_modules 里面（ModuleConfig.section 调） */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Rewind Module (helmet): a fatal blow sends you back to where you stood a few seconds ago.",
                "回溯模块（头盔）：受到致命一击时退回几秒前站过的地方").push("rewind");
        secondsBack = builder.comment("Roughly how many seconds back you are sent. One position is remembered per second (in memory only), "
                                + "the last seconds_back × 2 are kept.",
                        "大约退回几秒前。每秒记一个位置（只在内存里），留最近 seconds_back × 2 个")
                .defineInRange("seconds_back", REWIND_DEFAULTS.secondsBack(), 1, 30);
        minAgeTicks = builder.comment("Only positions at least this many ticks old are used (20 = 1 second).",
                        "只用至少这么多刻以前的位置（20 = 1 秒）")
                .defineInRange("min_age_ticks", REWIND_DEFAULTS.minAgeTicks(), 0, 600);
        cooldownTicks = builder.comment("Cooldown after a rewind, in ticks; kept across relogs and deaths.",
                        "救下一次以后的冷却（刻），退出重进、死了都不清")
                .defineInRange("cooldown_ticks", REWIND_DEFAULTS.cooldownTicks(), 0, 1_728_000);
        mruCost = builder.comment("MRU spent per rewind (MRU storages you carry first, then the chestplate's own MRU). Spent only when the rewind happens.",
                        "每救一次花多少 MRU（先扣身上的储存物品，凑不够再扣胸甲自己存的）；救成了才扣")
                .defineInRange("mru_cost", REWIND_DEFAULTS.mruCost(), 0, 100_000_000);
        matrixDamage = builder.comment("Matrix damage added per rewind (capped by general.corruption.max_matrix_damage).",
                        "每救一次加多少矩阵损伤（不超过 general.corruption.max_matrix_damage）")
                .defineInRange("matrix_damage", REWIND_DEFAULTS.matrixDamage(), 0, 1_000_000);
        rescueVoid = builder.comment("Whether falling out of the world is rescued too. /kill never is.",
                        "掉进虚空摔死的救不救；/kill 一律不救")
                .define("rescue_void", REWIND_DEFAULTS.rescueVoid());
        builder.pop();

        builder.comment("Matrix Filter Module (helmet): harmful effects are not applied and become matrix damage instead.",
                "矩阵过滤模块（头盔）：有害的状态效果加不到身上，改记矩阵损伤").push("matrix_filter");
        matrixPerEffectSecond = builder.comment("Matrix damage per second of duration × (amplifier + 1) for each blocked effect (instant effects count as 1 second). "
                                + "An effect is only blocked if all of its matrix damage still fits under the cap.",
                        "拦下一个效果：每秒持续时间 ×（等级 + 1）记多少矩阵损伤（瞬间效果按 1 秒）；记不下这个效果的全部损伤时就不拦")
                .defineInRange("matrix_per_effect_second", FILTER_DEFAULTS.matrixPerEffectSecond(), 0, 100_000);
        mruPerEffect = builder.comment("MRU spent per newly blocked effect; the same effect arriving again while already blocked costs no MRU "
                                + "(only the extra duration is added as matrix damage). Not blocked if you can't pay.",
                        "每拦下一个新效果花多少 MRU；同一个效果在已拦下的时间里再来不收 MRU（超出的那段时间照记矩阵损伤）；付不起就不拦")
                .defineInRange("mru_per_effect", FILTER_DEFAULTS.mruPerEffect(), 0, 1_000_000);
        builder.pop();

        builder.comment("Matrix Shunt Module (chestplate): part of each magic damage is taken as matrix damage instead of health.",
                "矩阵分流模块（胸甲）：魔法伤害的一部分不扣血，改记矩阵损伤").push("matrix_shunt");
        ratio = builder.comment("Share of each magic damage that is shunted (only as much as still fits under the matrix damage cap).",
                        "每次魔法伤害分流掉的比例（只分流矩阵损伤还记得下的那部分）")
                .defineInRange("ratio", SHUNT_DEFAULTS.ratio(), 0.0D, MAX_SHUNT_RATIO);
        matrixPerMaxHealth = builder.comment("Matrix damage for shunted damage equal to your whole max health; scales with the share of max health "
                                + "(2000 = 100 per point at the vanilla 20 max health, 20 per 1% of max health at any health).",
                        "分流掉的伤害正好等于自己的最大生命时记多少矩阵损伤，按占最大生命的比例折算"
                                + "（2000 = 原版 20 点生命时每点 100；不论多少血，分走最大生命的 1% 记 20）")
                .defineInRange("matrix_per_max_health", SHUNT_DEFAULTS.matrixPerMaxHealth(), 0, 2_000_000);
        builder.pop();
    }

    /** 回溯：测试覆盖值 → 配置（没加载时默认值） */
    public static Rewind rewind() {
        Rewind override = testRewind;
        if (override != null) {
            return override;
        }
        if (secondsBack == null) {
            return REWIND_DEFAULTS;
        }
        return new Rewind(ECConfig.get(secondsBack), ECConfig.get(minAgeTicks), ECConfig.get(cooldownTicks), ECConfig.get(mruCost),
                ECConfig.get(matrixDamage), ECConfig.get(rescueVoid));
    }

    /** 矩阵过滤：测试覆盖值 → 配置（没加载时默认值） */
    public static Filter filter() {
        Filter override = testFilter;
        if (override != null) {
            return override;
        }
        if (matrixPerEffectSecond == null) {
            return FILTER_DEFAULTS;
        }
        return new Filter(ECConfig.get(matrixPerEffectSecond), ECConfig.get(mruPerEffect));
    }

    /** 矩阵分流：测试覆盖值 → 配置（没加载时默认值） */
    public static Shunt shunt() {
        Shunt override = testShunt;
        if (override != null) {
            return override;
        }
        if (ratio == null) {
            return SHUNT_DEFAULTS;
        }
        return new Shunt(ECConfig.get(ratio), ECConfig.get(matrixPerMaxHealth));
    }

    /** GameTest 用：回溯整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
    public static void setTestRewind(@Nullable Rewind values) {
        testRewind = values;
    }

    /** GameTest 用：矩阵过滤，写法同 {@link #setTestRewind} */
    public static void setTestFilter(@Nullable Filter values) {
        testFilter = values;
    }

    /** GameTest 用：矩阵分流，写法同 {@link #setTestRewind} */
    public static void setTestShunt(@Nullable Shunt values) {
        testShunt = values;
    }
}
