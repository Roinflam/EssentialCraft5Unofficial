package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 服务端配置 {@code general.matrix_rank.siphon} 与 {@code general.matrix_rank.resonance}（第十三批 13a，batch13.md §3.1.4、§3.1.5、§3.2）：
 * 矩阵虹吸（四阶），以及相性与矩阵特性（三 / 六 / 九阶）。由 {@link MatrixConfig#define} 在 {@code matrix_rank} 小节里调用。
 * <p>
 * {@code resonance.<属性>.*} 每项可调、填 0 = 关（类名、键名沿用 resonance，不给玩家看；玩家看到的叫「相性」）。按级给的几项是三个数的列表，
 * 依次是 I / II / III；暗影八阶起直接是 II，它的第一项用不到。列表按 {@link Number} 读（TOML 里写整数时读出来是 Long），少写的级按 0 算。
 * 矩阵特性的基础值（腐蚀刻数、冰冻刻、吸取比例）不在这里，复用透镜那几项（{@code general.guns.lens_*}，{@code gun/LensEffects.Config}）。
 */
public final class MatrixResonanceConfig {

    public static final List<Double> DEFAULT_TRAIT_LEVEL_MULTIPLIERS = List.of(1D, 1.25D, 1.5D);
    public static final List<Double> DEFAULT_CHAOS_DAMAGE_BONUS = List.of(0.05D, 0.10D, 0.15D);
    public static final List<Double> DEFAULT_FROZEN_MELEE_REDUCTION = List.of(0.05D, 0.10D, 0.15D);
    public static final List<Integer> DEFAULT_FROZEN_RETALIATION_TICKS = List.of(30, 45, 60);
    public static final List<Double> DEFAULT_MAGIC_COST_DISCOUNT = List.of(0.10D, 0.15D, 0.20D);
    public static final List<Double> DEFAULT_MAGIC_SIPHON_BONUS = List.of(0.25D, 0.50D, 1.00D);
    public static final List<Double> DEFAULT_SHADE_MAGIC_REDUCTION = List.of(0D, 0.10D, 0.15D);

    public static ForgeConfigSpec.IntValue siphonAmount;
    public static ForgeConfigSpec.IntValue siphonMatrixDamage;
    public static ForgeConfigSpec.IntValue siphonCooldownTicks;

    public static ForgeConfigSpec.IntValue traitMruCost;
    public static ForgeConfigSpec.ConfigValue<List<? extends Number>> traitLevelMultipliers;
    public static ForgeConfigSpec.DoubleValue sameTypeLensMultiplier;
    public static ForgeConfigSpec.ConfigValue<List<? extends Number>> chaosDamageBonus;
    public static ForgeConfigSpec.DoubleValue chaosTraitBonusVsCorroded;
    public static ForgeConfigSpec.ConfigValue<List<? extends Number>> frozenMeleeReduction;
    public static ForgeConfigSpec.ConfigValue<List<? extends Number>> frozenRetaliationTicks;
    public static ForgeConfigSpec.DoubleValue frozenTraitBonusVsFrozen;
    public static ForgeConfigSpec.ConfigValue<List<? extends Number>> magicCostDiscount;
    public static ForgeConfigSpec.ConfigValue<List<? extends Number>> magicSiphonBonus;
    public static ForgeConfigSpec.IntValue magicTraitDispelCooldown;
    public static ForgeConfigSpec.ConfigValue<List<? extends Number>> shadeMagicReduction;

    private MatrixResonanceConfig() {
    }

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Matrix Siphon (rank 4): hold a bound Soul Stone on a nearby MRUCU while sneaking to draw MRU into your reservoir.",
                "矩阵虹吸（四阶）：潜行、拿着绑定自己的灵魂石对着附近的聚集体，把 MRU 抽进矩阵容量").push("siphon");
        siphonAmount = builder.comment("Most MRU one siphon draws.", "一次虹吸最多抽多少 MRU")
                .defineInRange("amount", 2000, 0, 1_000_000);
        siphonMatrixDamage = builder.comment("Matrix damage taken per siphon (goes through tolerance and the damage cap).",
                        "每次虹吸自己挨的矩阵损伤（照样吃耐受和损伤上限）")
                .defineInRange("matrix_damage", 100, 0, 100_000);
        siphonCooldownTicks = builder.comment("Ticks between two siphons.", "两次虹吸之间隔多少刻")
                .defineInRange("cooldown_ticks", 200, 0, 72_000);
        builder.pop();

        builder.comment("Matrix Alignment (ranks 3 / 6 / 9 give levels I / II / III of the alignment that matches your current matrix type; Shade starts at II from rank 8)",
                        "and Matrix Traits (weapons tagged essentialcraft5:matrix_attuned trigger an effect on hits that land, from Alignment I).",
                        "Lists hold one number per level: I, II, III. 0 turns an entry off.",
                        "相性（三 / 六 / 九阶按你当前的矩阵属性给 I / II / III；暗影八阶起直接 II）与矩阵特性（带 essentialcraft5:matrix_attuned 标签的武器打实后触发，相性 I 起）。",
                        "列表按级各一个数：I、II、III。填 0 = 关")
                .push("resonance");
        traitMruCost = builder.comment("MRU each Matrix Trait trigger costs (MRU storages you carry first, then the matrix reservoir, then the weapon itself; "
                                + "the Clarity discount applies). If you can't pay, the trait doesn't trigger and the hit lands as normal.",
                        "矩阵特性每次触发花多少 MRU（先扣身上的储存物品，再扣矩阵容量，最后扣武器自己；魔力相性的折扣照算）。付不起不触发，伤害照打")
                .defineInRange("trait_mru_cost", 50, 0, 100_000);
        traitLevelMultipliers = builder.comment("Matrix Trait strength by Alignment level (multiplies durations, freeze ticks and drain, not bonus damage).",
                        "矩阵特性的强度倍数，按相性级（乘时长、冰冻刻、吸取比例，不乘增伤百分比）")
                .defineList("trait_level_multipliers", DEFAULT_TRAIT_LEVEL_MULTIPLIERS, MatrixResonanceConfig::multiplier);
        sameTypeLensMultiplier = builder.comment("Gun lenses: with a lens of the same type as your matrix and Alignment I or more, the lens effect is multiplied by this.",
                        "枪械透镜：透镜和你的矩阵属性相同、相性 I 起时，透镜效果乘这个数")
                .defineInRange("same_type_lens_multiplier", 1.5D, 1D, 10D);

        builder.comment("Chaos alignment (Agitation).", "混沌相性「躁动」").push("chaos");
        chaosDamageBonus = builder.comment("Bonus to your melee and projectile damage, by level (0.05 = +5%).", "你的近战、弹射物伤害加成，按级（0.05 = +5%）")
                .defineList("damage_bonus", DEFAULT_CHAOS_DAMAGE_BONUS, MatrixResonanceConfig::fraction);
        chaosTraitBonusVsCorroded = builder.comment("Matrix Trait: bonus damage against a target that has Corrosion.", "矩阵特性：对挂着腐蚀的目标多打多少（0.1 = +10%）")
                .defineInRange("trait_bonus_vs_corroded", 0.10D, 0D, 10D);
        builder.pop();

        builder.comment("Frozen alignment (Stasis).", "冰冻相性「凝滞」").push("frozen");
        frozenMeleeReduction = builder.comment("Melee damage you take is reduced by this, by level (damage that bypasses armor is not reduced).",
                        "你受到的近战伤害减少多少，按级（无视护甲的不减）")
                .defineList("melee_reduction", DEFAULT_FROZEN_MELEE_REDUCTION, MatrixResonanceConfig::fraction);
        frozenRetaliationTicks = builder.comment("Freeze ticks added to a living melee attacker whose hit on you lands, by level (140 = frozen solid).",
                        "用近战打实你的活物加多少冰冻刻，按级（140 冻僵）")
                .defineList("retaliation_freeze_ticks", DEFAULT_FROZEN_RETALIATION_TICKS, MatrixResonanceConfig::ticks);
        frozenTraitBonusVsFrozen = builder.comment("Matrix Trait: bonus damage against a target that is frozen solid.", "矩阵特性：对冻僵的目标多打多少（0.1 = +10%）")
                .defineInRange("trait_bonus_vs_frozen", 0.10D, 0D, 10D);
        builder.pop();

        builder.comment("Magic alignment (Clarity).", "魔力相性「澄澈」").push("magic");
        magicCostDiscount = builder.comment("MRU discount on this mod's handheld and worn items, by level (0.1 = 10% cheaper). Shield armor charges are not discounted.",
                        "本模组随身物品耗 MRU 便宜多少，按级（0.1 = 便宜 10%）。护盾盔甲的护盾扣费不打折")
                .defineList("cost_discount", DEFAULT_MAGIC_COST_DISCOUNT, MatrixResonanceConfig::discount);
        magicSiphonBonus = builder.comment("Extra share of the Matrix Siphon cap, by level (0.25 = +25%).", "矩阵虹吸单次上限多加多少，按级（0.25 = +25%）")
                .defineList("siphon_bonus", DEFAULT_MAGIC_SIPHON_BONUS, MatrixResonanceConfig::multiplier);
        magicTraitDispelCooldown = builder.comment("Matrix Trait: a target loses at most one beneficial effect per this many ticks.",
                        "矩阵特性：同一个目标每隔多少刻最多被驱散一次")
                .defineInRange("trait_dispel_cooldown_ticks", 40, 0, 72_000);
        builder.pop();

        builder.comment("Shade alignment (Devouring), from rank 8: the first list entry is never used.",
                "暗影相性「吞噬」，八阶起直接 II：列表第一项用不到").push("shade");
        shadeMagicReduction = builder.comment("Magic damage that has an attacker is reduced by this, by level. Damage with no attacker (corruption, MRU corruption) "
                                + "and damage types tagged essentialcraft5:unshieldable are not reduced.",
                        "有攻击者的魔法伤害减少多少，按级。没有攻击者的（腐化、MRU 腐化）和伤害类型标签 essentialcraft5:unshieldable 里的不减")
                .defineList("magic_reduction", DEFAULT_SHADE_MAGIC_REDUCTION, MatrixResonanceConfig::fraction);
        builder.pop();

        builder.pop();
    }

    private static boolean fraction(Object o) {
        return o instanceof Number n && n.doubleValue() >= 0D && n.doubleValue() <= 1D;
    }

    private static boolean discount(Object o) {
        return o instanceof Number n && n.doubleValue() >= 0D && n.doubleValue() < 1D;
    }

    private static boolean multiplier(Object o) {
        return o instanceof Number n && n.doubleValue() >= 0D && n.doubleValue() <= 10D;
    }

    private static boolean ticks(Object o) {
        return o instanceof Number n && n.intValue() >= 0 && n.intValue() <= 1200;
    }

    public static int siphonAmount() {
        return siphonAmount == null ? 2000 : ECConfig.get(siphonAmount);
    }

    public static int siphonMatrixDamage() {
        return siphonMatrixDamage == null ? 100 : ECConfig.get(siphonMatrixDamage);
    }

    public static int siphonCooldownTicks() {
        return siphonCooldownTicks == null ? 200 : ECConfig.get(siphonCooldownTicks);
    }

    public static int traitMruCost() {
        return traitMruCost == null ? 50 : ECConfig.get(traitMruCost);
    }

    public static double sameTypeLensMultiplier() {
        return sameTypeLensMultiplier == null ? 1.5D : ECConfig.get(sameTypeLensMultiplier);
    }

    public static double chaosTraitBonusVsCorroded() {
        return chaosTraitBonusVsCorroded == null ? 0.10D : ECConfig.get(chaosTraitBonusVsCorroded);
    }

    public static double frozenTraitBonusVsFrozen() {
        return frozenTraitBonusVsFrozen == null ? 0.10D : ECConfig.get(frozenTraitBonusVsFrozen);
    }

    public static int magicTraitDispelCooldown() {
        return magicTraitDispelCooldown == null ? 40 : ECConfig.get(magicTraitDispelCooldown);
    }

    /** 矩阵特性按相性级的强度倍数（level 1~3，别的是 0） */
    public static double traitLevelMultiplier(int level) {
        return byLevel(traitLevelMultipliers, DEFAULT_TRAIT_LEVEL_MULTIPLIERS, level);
    }

    public static double chaosDamageBonus(int level) {
        return byLevel(chaosDamageBonus, DEFAULT_CHAOS_DAMAGE_BONUS, level);
    }

    public static double frozenMeleeReduction(int level) {
        return Math.min(1D, byLevel(frozenMeleeReduction, DEFAULT_FROZEN_MELEE_REDUCTION, level));
    }

    public static int frozenRetaliationTicks(int level) {
        return (int) Math.round(byLevel(frozenRetaliationTicks, DEFAULT_FROZEN_RETALIATION_TICKS, level));
    }

    public static double magicCostDiscount(int level) {
        return Math.min(0.99D, byLevel(magicCostDiscount, DEFAULT_MAGIC_COST_DISCOUNT, level));
    }

    public static double magicSiphonBonus(int level) {
        return byLevel(magicSiphonBonus, DEFAULT_MAGIC_SIPHON_BONUS, level);
    }

    public static double shadeMagicReduction(int level) {
        return Math.min(1D, byLevel(shadeMagicReduction, DEFAULT_SHADE_MAGIC_REDUCTION, level));
    }

    /** 列表第 level 项（1 起）；配置没加载时用默认表；少写的级、不是有限正数的项按 0 */
    private static double byLevel(ForgeConfigSpec.ConfigValue<List<? extends Number>> value, List<? extends Number> fallback, int level) {
        if (level < 1) {
            return 0D;
        }
        List<? extends Number> list = value == null ? fallback : ECConfig.getValue(value);
        if (list == null || list.size() < level) {
            return 0D;
        }
        Object entry = list.get(level - 1);
        if (!(entry instanceof Number n)) {
            return 0D;
        }
        double d = n.doubleValue();
        return Double.isFinite(d) && d > 0D ? d : 0D;
    }
}
