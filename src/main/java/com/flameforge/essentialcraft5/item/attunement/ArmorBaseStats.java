package com.flameforge.essentialcraft5.item.attunement;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 盔甲随阶上涨的基础属性：配置 {@code general.gear_growth.<家族>.base_stats}，由 {@link AttunementBonuses} 并进物品本体的那几条修饰符
 * （提示框「穿在身上时」每个属性只有一行）。
 * <ul>
 *   <li>{@code armor_helmet / armor_chestplate / armor_leggings / armor_boots}：按阶的列表，第 i 项 = i 阶时这一件比 0 阶多几点护甲（累计）。
 *       原版把四件的护甲合计取整，所以默认值都是整数；</li>
 *   <li>{@code toughness}、{@code knockback}：每件，按阶（累计）；</li>
 *   <li>{@code health_per_stage}：每阶每件加的最大生命，−1 = 沿用 {@code armor.health_per_stage}。</li>
 * </ul>
 * 列表比阶数短时沿用最后一项，空列表当 0（同 {@link GearGrowthConfig#at}）。
 * <p>
 * 默认值的取法：魔力防护、风之的护甲、韧性离原版上限（护甲 30、韧性 20）还有余量，按阶涨，满阶四件合计正好不超过上限；
 * 虚空、审判庭、宅男 0 阶的护甲、韧性就已经超过上限（加了只是提示框数字变大），所以不加，改成每阶每件生命 3 点、满阶每件击退抗性 +0.025
 * （四件合计 0.8 → 0.9，不补满：本模组几个推人的机制和别的玩家的击退对满阶这三套还剩一成）。上限是原版属性自己的，这里不去抬。
 */
public final class ArmorBaseStats {

    /** 一套的默认值 */
    record Defaults(List<Double> helmet, List<Double> chestplate, List<Double> leggings, List<Double> boots,
                    List<Double> toughness, List<Double> knockback, double healthPerStage) {
    }

    private static final class Values {
        final Defaults defaults;
        ForgeConfigSpec.ConfigValue<List<? extends Number>> helmet;
        ForgeConfigSpec.ConfigValue<List<? extends Number>> chestplate;
        ForgeConfigSpec.ConfigValue<List<? extends Number>> leggings;
        ForgeConfigSpec.ConfigValue<List<? extends Number>> boots;
        ForgeConfigSpec.ConfigValue<List<? extends Number>> toughness;
        ForgeConfigSpec.ConfigValue<List<? extends Number>> knockback;
        ForgeConfigSpec.DoubleValue healthPerStage;

        Values(Defaults defaults) {
            this.defaults = defaults;
        }
    }

    private static final List<Double> NONE = List.of(0D);

    public static final Defaults MHS = new Defaults(List.of(0D, 1D, 2D), List.of(0D, 1D, 2D), List.of(0D, 1D, 2D), List.of(0D, 1D, 2D),
            List.of(0D, 1D, 2D), NONE, -1D);
    public static final Defaults WIND = new Defaults(List.of(0D, 0D, 1D, 1D), List.of(0D, 1D, 1D, 2D), List.of(0D, 1D, 1D, 1D), List.of(0D, 0D, 1D, 1D),
            List.of(0D, 1D, 2D, 2.5D), NONE, -1D);
    public static final Defaults VOID = new Defaults(NONE, NONE, NONE, NONE, NONE, List.of(0D, 0D, 0D, 0D, 0.025D), 3D);
    public static final Defaults INQUISITORIUM = VOID;
    public static final Defaults COMPUTER = new Defaults(NONE, NONE, NONE, NONE, NONE, List.of(0D, 0D, 0D, 0D, 0D, 0.025D), 3D);

    /** 家族路径（mhs_armor……）→ 配置 */
    private static final Map<String, Values> VALUES = new ConcurrentHashMap<>();

    private ArmorBaseStats() {
    }

    /** 在某一套的配置节里（已经 push 了家族那一节）写 {@code base_stats} 子节 */
    static void define(ForgeConfigSpec.Builder b, String family, Defaults defaults) {
        Values v = new Values(defaults);
        b.comment("Base attributes that grow with stage (merged into the armor's own attribute lines). Lists are by stage, entry i = stage i, cumulative per piece.",
                "随阶上涨的基础属性（并进盔甲本身的那几行属性）。列表按阶，第 i 项 = i 阶时每件比 0 阶多多少（累计）").push("base_stats");
        v.helmet = GearGrowthConfig.fractions(b.comment("Extra armor on the helmet.", "头盔多几点护甲"), "armor_helmet", defaults.helmet());
        v.chestplate = GearGrowthConfig.fractions(b.comment("Extra armor on the chestplate.", "胸甲多几点护甲"), "armor_chestplate", defaults.chestplate());
        v.leggings = GearGrowthConfig.fractions(b.comment("Extra armor on the leggings.", "护腿多几点护甲"), "armor_leggings", defaults.leggings());
        v.boots = GearGrowthConfig.fractions(b.comment("Extra armor on the boots.", "靴子多几点护甲"), "armor_boots", defaults.boots());
        v.toughness = GearGrowthConfig.fractions(b.comment("Extra armor toughness per piece.", "每件多几点盔甲韧性"), "toughness", defaults.toughness());
        v.knockback = GearGrowthConfig.fractions(b.comment("Extra knockback resistance per piece (1 = 100%).", "每件多几点击退抗性（1 = 100%）"),
                "knockback", defaults.knockback());
        v.healthPerStage = b.comment("Max health each piece adds per stage; -1 uses armor.health_per_stage.",
                        "每阶每件加的最大生命；-1 = 沿用 armor.health_per_stage")
                .defineInRange("health_per_stage", defaults.healthPerStage(), -1D, 20D);
        b.pop();
        VALUES.put(family, v);
    }

    @Nullable
    private static Values values(AttunementFamily family) {
        return VALUES.get(family.id().getPath());
    }

    /** 这一件在 stage 阶时比 0 阶多几点护甲（没登记这一套的是 0） */
    public static double armor(AttunementFamily family, EquipmentSlot slot, int stage) {
        Values v = values(family);
        if (v == null || stage <= 0) {
            return 0D;
        }
        String path = family.id().getPath() + ".base_stats.";
        return switch (slot) {
            case HEAD -> GearGrowthConfig.amountAt(path + "armor_helmet", v.helmet, v.defaults.helmet(), stage, 30D);
            case CHEST -> GearGrowthConfig.amountAt(path + "armor_chestplate", v.chestplate, v.defaults.chestplate(), stage, 30D);
            case LEGS -> GearGrowthConfig.amountAt(path + "armor_leggings", v.leggings, v.defaults.leggings(), stage, 30D);
            case FEET -> GearGrowthConfig.amountAt(path + "armor_boots", v.boots, v.defaults.boots(), stage, 30D);
            default -> 0D;
        };
    }

    /** 每件在 stage 阶时多几点韧性 */
    public static double toughness(AttunementFamily family, int stage) {
        Values v = values(family);
        if (v == null || stage <= 0) {
            return 0D;
        }
        return GearGrowthConfig.amountAt(family.id().getPath() + ".base_stats.toughness", v.toughness, v.defaults.toughness(), stage, 20D);
    }

    /** 每件在 stage 阶时多几点击退抗性 */
    public static double knockback(AttunementFamily family, int stage) {
        Values v = values(family);
        if (v == null || stage <= 0) {
            return 0D;
        }
        return GearGrowthConfig.amountAt(family.id().getPath() + ".base_stats.knockback", v.knockback, v.defaults.knockback(), stage, 1D);
    }

    /** 每阶每件的最大生命（这一套没写或写 −1 时沿用 {@code armor.health_per_stage}） */
    public static double healthPerStage(AttunementFamily family) {
        Values v = values(family);
        if (v == null) {
            return GearGrowthConfig.armorHealthPerStage();
        }
        double own = GearGrowthConfig.d(family.id().getPath() + ".base_stats.health_per_stage", v.healthPerStage, v.defaults.healthPerStage());
        return own < 0D ? GearGrowthConfig.armorHealthPerStage() : own;
    }
}
