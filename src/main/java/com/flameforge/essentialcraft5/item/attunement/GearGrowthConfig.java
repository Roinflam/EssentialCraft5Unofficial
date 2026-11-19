package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端配置 {@code general.gear_growth}：武器防具成长加强（tools/plan/gear_growth.md 第 3 节）。由 {@code registry/ECAttunement.init()} 登记。
 * <p>
 * <b>改了默认值的数一律放这一节的新键</b>：服主已有的 toml 里旧键会一直留着旧值（Forge 不拿新默认值覆盖），所以
 * {@code general.attunement.damage_per_stage} / {@code health_per_stage}、{@code general.verdict_blade.verdict.percent_per_stage} / {@code cap_per_stage}
 * 由这里的 {@code weapon.damage_per_stage}、{@code armor.health_per_stage}、{@code verdict_blade.percent_per_stage} / {@code cap_per_stage} 接管，旧键留着定义、代码不再读。
 * 刀剑每阶的增伤后来搬进了属性栏的攻击伤害（{@code verdict_blade.attack_per_stage}、{@code ember_sword.attack_per_stage}，见 {@link AttunementBonuses}），
 * {@code weapon.damage_per_stage} 从那以后只给枪读；盔甲每套的护甲、韧性、击退抗性、每阶生命在 {@code <家族>.base_stats}（{@link ArmorBaseStats}），
 * 虚空 / 审判庭 / 宅男的每阶生命由那里的 {@code health_per_stage} 接管（{@code armor.health_per_stage} 只剩魔力防护、风之沿用）。
 * <p>
 * 「按阶的列表」第 i 项 = i 阶（0 阶在第 0 项）；列表比阶数短时沿用最后一项，空列表当 0（同 {@link AttunementConfig#threshold}）。
 * <p>
 * 客户端读的是服务端同步来的这份配置（提示框、手册的数和实际结算对得上）。GameTest 不改真配置（会被文件监视器冲掉），
 * 用 {@link #setTestOverride} 按键路径临时盖值，测完 {@link #clearTestOverrides}。
 */
public final class GearGrowthConfig {

    // 总倍率
    private static ForgeConfigSpec.DoubleValue offenseScale;
    private static ForgeConfigSpec.DoubleValue defenseScale;
    private static ForgeConfigSpec.DoubleValue aoeScale;
    private static ForgeConfigSpec.BooleanValue aoeHitsPlayers;
    // 封顶
    private static ForgeConfigSpec.DoubleValue poolMob;
    private static ForgeConfigSpec.DoubleValue poolBoss;
    private static ForgeConfigSpec.DoubleValue poolPlayer;
    private static ForgeConfigSpec.DoubleValue reduction;
    private static ForgeConfigSpec.DoubleValue reductionPlayer;
    private static ForgeConfigSpec.DoubleValue armorPen;
    private static ForgeConfigSpec.DoubleValue armorPenPlayer;
    private static ForgeConfigSpec.DoubleValue lifestealPerSecond;
    private static ForgeConfigSpec.DoubleValue lifestealPerSecondPlayer;
    private static ForgeConfigSpec.DoubleValue lifestealPlayerScale;
    private static ForgeConfigSpec.DoubleValue cooldownReduction;
    private static ForgeConfigSpec.DoubleValue dodge;
    private static ForgeConfigSpec.DoubleValue dodgePlayer;
    // 通用每阶
    private static ForgeConfigSpec.DoubleValue weaponDamagePerStage;
    private static ForgeConfigSpec.DoubleValue verdictAttackPerStage;
    private static ForgeConfigSpec.DoubleValue emberAttackPerStage;
    private static ForgeConfigSpec.DoubleValue armorHealthPerStage;
    // 战痕
    private static ForgeConfigSpec.IntValue scarPointsPerLevel;
    private static ForgeConfigSpec.IntValue scarMaxLevel;
    private static ForgeConfigSpec.DoubleValue scarWeaponPerLevel;
    private static ForgeConfigSpec.DoubleValue scarArmorPerLevel;
    // 裁决之刃
    private static ForgeConfigSpec.DoubleValue verdictPercentPerStage;
    private static ForgeConfigSpec.DoubleValue verdictCapPerStage;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> verdictLifesteal;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> verdictArmorPen;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> verdictJudgmentCdr;
    private static ForgeConfigSpec.DoubleValue executeThreshold;
    private static ForgeConfigSpec.DoubleValue executeBonus;
    private static ForgeConfigSpec.IntValue executeStage;
    private static ForgeConfigSpec.IntValue finalVerdictStage;
    private static ForgeConfigSpec.IntValue finalVerdictMarkTicks;
    private static ForgeConfigSpec.DoubleValue finalVerdictPercent;
    private static ForgeConfigSpec.DoubleValue finalVerdictCap;
    private static ForgeConfigSpec.DoubleValue finalVerdictBossPool;
    // 余烬元素剑
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> emberLifesteal;
    private static ForgeConfigSpec.IntValue burstStage;
    private static ForgeConfigSpec.DoubleValue burstChance;
    private static ForgeConfigSpec.IntValue burstCooldown;
    private static ForgeConfigSpec.DoubleValue burstRadius;
    private static ForgeConfigSpec.IntValue burstMaxTargets;
    private static ForgeConfigSpec.DoubleValue burstFireDamage;
    private static ForgeConfigSpec.DoubleValue burstFirePercent;
    private static ForgeConfigSpec.DoubleValue burstFireCap;
    private static ForgeConfigSpec.DoubleValue burstQuakeDamage;
    private static ForgeConfigSpec.IntValue burstDebuffTicks;
    // 枪械同调
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> gunMruDiscount;
    private static ForgeConfigSpec.IntValue gunAccumulateStage;
    private static ForgeConfigSpec.DoubleValue gunAccumulateRatio;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> gunArmorPen;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> gunReloadCdr;
    private static ForgeConfigSpec.IntValue overloadStage;
    private static ForgeConfigSpec.IntValue overloadShots;
    private static ForgeConfigSpec.IntValue overloadShotsSingle;
    private static ForgeConfigSpec.DoubleValue overloadRatio;
    private static ForgeConfigSpec.DoubleValue overloadRadius;
    private static ForgeConfigSpec.IntValue overloadMaxTargets;

    public static final List<Double> DEFAULT_VERDICT_LIFESTEAL = List.of(0D, 0.02D, 0.02D, 0.02D, 0.04D, 0.04D);
    public static final List<Double> DEFAULT_VERDICT_ARMOR_PEN = List.of(0D, 0D, 0D, 0.15D, 0.15D, 0.25D);
    public static final List<Double> DEFAULT_VERDICT_JUDGMENT_CDR = List.of(0D, 0D, 0D, 0.2D, 0.2D, 0.2D);
    public static final List<Double> DEFAULT_EMBER_LIFESTEAL = List.of(0D, 0.04D, 0.04D);
    public static final List<Double> DEFAULT_GUN_MRU_DISCOUNT = List.of(0D, 0.1D, 0.1D, 0.1D, 0.1D);
    public static final List<Double> DEFAULT_GUN_ARMOR_PEN = List.of(0D, 0D, 0D, 0.2D, 0.2D);
    public static final List<Double> DEFAULT_GUN_RELOAD_CDR = List.of(0D, 0D, 0D, 0.2D, 0.2D);

    private GearGrowthConfig() {
    }

    /**
     * 按阶的比例列表。元素只校验「是数」、越界在 {@link #at} 里夹到 0~1：Forge 的列表纠错会删掉不合格的那一项，
     * 后面的阶就整体前移一位（3 阶拿到 4 阶的数），比夹一下更难发现。允许空列表，{@code []} 就是全阶 0。
     */
    static ForgeConfigSpec.ConfigValue<List<? extends Number>> fractions(ForgeConfigSpec.Builder builder, String key, List<Double> defaults) {
        return builder.defineListAllowEmpty(key, defaults, o -> o instanceof Number);
    }

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Gear growth: percent stats per attunement stage, stage-max awakenings, battle scars and the shared caps that keep them in check.",
                "装备成长：同调每阶的百分比词条、满阶觉醒、战痕，以及把它们夹住的总闸").push("gear_growth");
        offenseScale = builder.comment("Scale on this mod's whole damage bonus pool (before the caps).", "本模组增伤池整体乘的倍数（在封顶之前乘）")
                .defineInRange("offense_scale", 1D, 0D, 5D);
        defenseScale = builder.comment("Scale on this mod's whole damage reduction pool (before the caps).", "本模组减伤池整体乘的倍数（在封顶之前乘）")
                .defineInRange("defense_scale", 1D, 0D, 5D);
        aoeScale = builder.comment("Scale on area damage from gear awakenings (Element Burst, Overload Ray ...).", "装备觉醒的范围伤害（元素爆发、过载射线……）乘的倍数")
                .defineInRange("aoe_scale", 1D, 0D, 5D);
        aoeHitsPlayers = builder.comment("Area damage from gear awakenings also hits players (PvP, teams and claims still apply).",
                        "装备觉醒的范围伤害也打玩家（PvP、队伍、领地照常管）")
                .define("aoe_hits_players", false);

        builder.comment("Caps. This mod's bonuses are added together first and capped here; bonuses from other mods multiply outside.",
                "封顶。本模组的加成先相加、在这里封顶；别的模组的倍数在外面照常乘").push("caps");
        poolMob = builder.comment("Most extra damage from the bonus pool against ordinary creatures (1.0 = up to x2).", "增伤池打普通生物最多多打几成（1.0 = 最多 ×2）")
                .defineInRange("pool_mob", 1D, 0D, 10D);
        poolBoss = builder.comment("Same, against bosses (#forge:bosses and this mod's bosses).", "同上，打首领（#forge:bosses 和本模组的首领）")
                .defineInRange("pool_boss", 0.5D, 0D, 10D);
        poolPlayer = builder.comment("Same, against players.", "同上，打玩家")
                .defineInRange("pool_player", 0.3D, 0D, 10D);
        reduction = builder.comment("Most damage the reduction pool takes off a hit.", "减伤池一下最多减掉几成")
                .defineInRange("reduction", 0.6D, 0D, 0.95D);
        reductionPlayer = builder.comment("Same, when the attacker is another player.", "同上，攻击者是别的玩家时")
                .defineInRange("reduction_player", 0.4D, 0D, 0.95D);
        armorPen = builder.comment("Most armor and toughness ignored by armor penetration.", "护甲穿透最多无视几成护甲和韧性")
                .defineInRange("armor_pen", 0.3D, 0D, 1D);
        armorPenPlayer = builder.comment("Same, against players.", "同上，打玩家")
                .defineInRange("armor_pen_player", 0.15D, 0D, 1D);
        lifestealPerSecond = builder.comment("Most health lifesteal gives back per second, as a fraction of your max health.", "吸血每秒最多回最大生命的几成")
                .defineInRange("lifesteal_per_second", 0.06D, 0D, 1D);
        lifestealPerSecondPlayer = builder.comment("Same, for lifesteal from hitting players.", "同上，打玩家吸到的部分")
                .defineInRange("lifesteal_per_second_player", 0.03D, 0D, 1D);
        lifestealPlayerScale = builder.comment("Lifesteal from hitting players is multiplied by this.", "打玩家时吸血量乘的倍数")
                .defineInRange("lifesteal_player_scale", 0.5D, 0D, 1D);
        cooldownReduction = builder.comment("Most cooldown reduction from gear stats.", "装备词条的冷却缩减最多几成")
                .defineInRange("cooldown_reduction", 0.4D, 0D, 0.9D);
        dodge = builder.comment("Most dodge chance from gear.", "装备给的闪避几率最多几成")
                .defineInRange("dodge", 0.35D, 0D, 1D);
        dodgePlayer = builder.comment("Same, when the attacker is a player.", "同上，攻击者是玩家时")
                .defineInRange("dodge_player", 0.15D, 0D, 1D);
        builder.pop();

        builder.comment("Weapons.", "武器").push("weapon");
        weaponDamagePerStage = builder.comment("Damage bonus per attunement stage for MRU guns (goes into the bonus pool). Replaces general.attunement.damage_per_stage. "
                                + "Blades grow their attack damage attribute instead (verdict_blade / ember_sword attack_per_stage).",
                        "MRU 枪械每阶增伤（进增伤池）。接管 general.attunement.damage_per_stage。刀剑改为随阶提高属性栏攻击伤害（见 verdict_blade / ember_sword 的 attack_per_stage）")
                .defineInRange("damage_per_stage", 0.08D, 0D, 1D);
        builder.pop();
        builder.comment("Armor.", "盔甲").push("armor");
        armorHealthPerStage = builder.comment("Max health each armor piece adds per stage (2 = one heart). Replaces general.attunement.health_per_stage.",
                        "盔甲每阶每件加的最大生命（2 = 一颗心）。接管 general.attunement.health_per_stage")
                .defineInRange("health_per_stage", 2D, 0D, 20D);
        builder.pop();

        builder.comment("Battle scars: attunement points earned after the highest stage.", "战痕：最高阶以后接着攒的同调度").push("scars");
        scarPointsPerLevel = builder.comment("Points per scar level.", "每级战痕要多少点")
                .defineInRange("points_per_level", 2000, 1, 1_000_000);
        scarMaxLevel = builder.comment("Highest scar level (0 = no scars).", "战痕最高几级（0 = 不攒战痕）")
                .defineInRange("max_level", 10, 0, 100);
        scarWeaponPerLevel = builder.comment("Weapon damage bonus per scar level (bonus pool).", "武器每级战痕增伤（进增伤池）")
                .defineInRange("weapon_per_level", 0.01D, 0D, 1D);
        scarArmorPerLevel = builder.comment("Damage reduction per scar level of a full armor set (lowest piece counts).", "全套盔甲每级战痕减伤（按四件里最低的算）")
                .defineInRange("armor_per_level", 0.01D, 0D, 1D);
        builder.pop();

        builder.comment("Verdict Blade.", "裁决之刃").push("verdict_blade");
        verdictAttackPerStage = builder.comment("Attack damage added per stage, as a fraction of the base attack damage shown on the item (at least 1 per stage, rounded to 0.5).",
                        "每阶加的攻击伤害，按物品原本显示的攻击伤害算比例（每阶至少 1 点，取到 0.5）")
                .defineInRange("attack_per_stage", 0.08D, 0D, 1D);
        verdictPercentPerStage = builder.comment("Verdict: added percent of max health per stage. Replaces general.verdict_blade.verdict.percent_per_stage.",
                        "裁断：每阶再加最大生命的百分之几。接管 general.verdict_blade.verdict.percent_per_stage")
                .defineInRange("percent_per_stage", 1D, 0D, 100D);
        verdictCapPerStage = builder.comment("Verdict: added cap per stage. Replaces general.verdict_blade.verdict.cap_per_stage.",
                        "裁断：每阶封顶再加多少。接管 general.verdict_blade.verdict.cap_per_stage")
                .defineInRange("cap_per_stage", 20D, 0D, 100_000D);
        verdictLifesteal = fractions(builder.comment("Lifesteal by stage (entry i = stage i).", "按阶的吸血（第 i 项 = i 阶）"),
                "lifesteal", DEFAULT_VERDICT_LIFESTEAL);
        verdictArmorPen = fractions(builder.comment("Armor penetration by stage.", "按阶的护甲穿透"), "armor_pen", DEFAULT_VERDICT_ARMOR_PEN);
        verdictJudgmentCdr = fractions(builder.comment("Extra Judgment cooldown reduction by stage (capped by caps.cooldown_reduction).",
                "按阶的断罪冷却再打折（受 caps.cooldown_reduction 封顶）"), "judgment_cooldown_reduction", DEFAULT_VERDICT_JUDGMENT_CDR);
        executeThreshold = builder.comment("Execute: targets below this fraction of max health take the execute bonus (not bosses, not players).",
                        "斩杀：生命低于最大生命这个比例的目标吃斩杀加成（首领、玩家不算）")
                .defineInRange("execute_threshold", 0.3D, 0D, 1D);
        executeBonus = builder.comment("Execute bonus (bonus pool).", "斩杀加成（进增伤池）")
                .defineInRange("execute_bonus", 0.2D, 0D, 5D);
        executeStage = builder.comment("Stage that unlocks Execute.", "几阶解锁斩杀")
                .defineInRange("execute_stage", 4, 0, Attunement.MAX_STAGE);
        builder.comment("Final Verdict (stage-max awakening): Judgment marks targets; your next own hit detonates the mark.",
                "终审（满阶觉醒）：断罪给目标挂罪印，你下一次亲手打实时引爆").push("final_verdict");
        finalVerdictStage = builder.comment("Stage that unlocks Final Verdict.", "几阶解锁终审")
                .defineInRange("stage", 5, 0, Attunement.MAX_STAGE);
        finalVerdictMarkTicks = builder.comment("Ticks the mark lasts.", "罪印挂几刻")
                .defineInRange("mark_ticks", 100, 1, 6000);
        finalVerdictPercent = builder.comment("Ordinary targets: detonation deals this percent of max health (armor-piercing).", "普通目标：引爆补最大生命的百分之几（无视护甲）")
                .defineInRange("percent", 6D, 0D, 100D);
        finalVerdictCap = builder.comment("Cap on that detonation.", "这一下的封顶")
                .defineInRange("cap", 120D, 0D, 100_000D);
        finalVerdictBossPool = builder.comment("Bosses: the detonating hit gets this much extra in the bonus pool (still capped by caps.pool_boss).",
                        "首领：引爆的那一下增伤池再加这么多（照样受 caps.pool_boss 封顶）")
                .defineInRange("boss_pool", 0.4D, 0D, 5D);
        builder.pop();
        builder.pop();

        builder.comment("Ember Elemental Sword.", "余烬元素剑").push("ember_sword");
        emberAttackPerStage = builder.comment("Attack damage added per stage, as a fraction of the base attack damage shown on the item (at least 1 per stage, rounded to 0.5).",
                        "每阶加的攻击伤害，按物品原本显示的攻击伤害算比例（每阶至少 1 点，取到 0.5）")
                .defineInRange("attack_per_stage", 0.10D, 0D, 1D);
        emberLifesteal = fractions(builder.comment("Lifesteal by stage.", "按阶的吸血"), "lifesteal", DEFAULT_EMBER_LIFESTEAL);
        builder.comment("Element Burst (stage-max awakening): on hit, a chance to burst the sword's primary element around the target.",
                "元素爆发（满阶觉醒）：命中时有几率以目标为中心爆发剑的主元素").push("burst");
        burstStage = builder.comment("Stage that unlocks Element Burst.", "几阶解锁").defineInRange("stage", 2, 0, Attunement.MAX_STAGE);
        burstChance = builder.comment("Chance per hit.", "每下的几率").defineInRange("chance", 0.15D, 0D, 1D);
        burstCooldown = builder.comment("Ticks before it can trigger again for the same wielder.", "同一名持剑人多少刻才能再触发")
                .defineInRange("cooldown", 40, 0, 1200);
        burstRadius = builder.comment("Radius in blocks.", "半径（格）").defineInRange("radius", 3D, 0.5D, 16D);
        burstMaxTargets = builder.comment("Most creatures hit.", "最多打几个").defineInRange("max_targets", 8, 1, 64);
        burstFireDamage = builder.comment("Fire: flat damage to each target.", "火：每个目标的固定伤害").defineInRange("fire_damage", 4D, 0D, 1000D);
        burstFirePercent = builder.comment("Fire: plus this percent of the target's max health (not for bosses).", "火：再加目标最大生命的百分之几（首领不加）")
                .defineInRange("fire_percent", 2D, 0D, 100D);
        burstFireCap = builder.comment("Fire: most health each target loses from one burst, bosses included; 0 = no cap.",
                        "火：每个目标这一下最多掉多少血（首领同样封顶）；0 = 不封顶")
                .defineInRange("fire_cap", 100D, 0D, 100000D);
        burstQuakeDamage = builder.comment("Earth: damage to each target.", "地：每个目标的伤害").defineInRange("quake_damage", 3D, 0D, 1000D);
        burstDebuffTicks = builder.comment("Water: ticks of Slowness II and Weakness; Earth: ticks of your Resistance.", "水：缓慢 II、虚弱的刻数；地：自己抗性提升的刻数")
                .defineInRange("debuff_ticks", 60, 0, 1200);
        builder.pop();
        builder.pop();

        builder.comment("MRU guns.", "MRU 枪械").push("mru_gun");
        gunMruDiscount = fractions(builder.comment("MRU per shot discount by stage.", "按阶的每发 MRU 折扣"), "mru_discount", DEFAULT_GUN_MRU_DISCOUNT);
        gunAccumulateStage = builder.comment("Stage from which ray damage swallowed by hurt immunity is banked and added to the next shot (like the Accumulator module).",
                        "几阶起射线被受击无敌吞掉的伤害记账、补进下一发（同累加器模块）")
                .defineInRange("accumulate_stage", 2, 0, Attunement.MAX_STAGE);
        gunAccumulateRatio = builder.comment("Most banked damage added to one shot, as a fraction of that shot.", "一发最多补这一发的几倍")
                .defineInRange("accumulate_ratio", 1D, 0D, 5D);
        gunArmorPen = fractions(builder.comment("Armor penetration by stage.", "按阶的护甲穿透"), "armor_pen", DEFAULT_GUN_ARMOR_PEN);
        gunReloadCdr = fractions(builder.comment("Reload cooldown reduction by stage (capped by caps.cooldown_reduction).", "按阶的装填冷却缩减（受 caps.cooldown_reduction 封顶）"),
                "reload_cooldown_reduction", DEFAULT_GUN_RELOAD_CDR);
        builder.comment("Overload Ray (stage-max awakening): landed shots charge the gun; when full, the next shot bursts at the first target hit.",
                "过载射线（满阶觉醒）：打实的每发给枪充能，充满后下一发在第一个打中的目标处爆发").push("overload");
        overloadStage = builder.comment("Stage that unlocks Overload Ray.", "几阶解锁").defineInRange("stage", 4, 0, Attunement.MAX_STAGE);
        overloadShots = builder.comment("Landed shots to charge (rifles, shotguns, gatlings).", "充满要打实几发（步枪、霰弹、加特林）")
                .defineInRange("shots", 12, 1, 1000);
        overloadShotsSingle = builder.comment("Same, for pistols and snipers.", "同上，手枪、狙击").defineInRange("shots_single", 3, 1, 1000);
        overloadRatio = builder.comment("Burst damage to each target, as a fraction of that shot's damage.", "爆发对每个目标的伤害是这一发的几成")
                .defineInRange("ratio", 0.6D, 0D, 5D);
        overloadRadius = builder.comment("Burst radius in blocks.", "爆发半径（格）").defineInRange("radius", 3D, 0.5D, 16D);
        overloadMaxTargets = builder.comment("Most creatures hit by the burst.", "爆发最多打几个").defineInRange("max_targets", 8, 1, 64);
        builder.pop();
        builder.pop();

        ArmorGrowth.define(builder);
        builder.pop();
    }

    // ------------------------------------------------------------------
    // 测试覆盖
    // ------------------------------------------------------------------

    private static final Map<String, Object> TEST = new ConcurrentHashMap<>();

    /** GameTest：按键路径（例 {@code "caps.pool_mob"}、{@code "verdict_blade.lifesteal"}）盖一个值；列表给 {@code List<Double>}。用完 {@link #clearTestOverrides} */
    public static void setTestOverride(String key, Object value) {
        TEST.put(key, value);
    }

    public static void clearTestOverrides() {
        TEST.clear();
    }

    static double d(String key, @Nullable ForgeConfigSpec.DoubleValue value, double fallback) {
        Object o = TEST.get(key);
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return value == null ? fallback : ECConfig.get(value);
    }

    static int i(String key, @Nullable ForgeConfigSpec.IntValue value, int fallback) {
        Object o = TEST.get(key);
        if (o instanceof Number n) {
            return n.intValue();
        }
        return value == null ? fallback : ECConfig.get(value);
    }

    static boolean b(String key, @Nullable ForgeConfigSpec.BooleanValue value, boolean fallback) {
        Object o = TEST.get(key);
        if (o instanceof Boolean v) {
            return v;
        }
        return value == null ? fallback : ECConfig.get(value);
    }

    /** 按阶的比例列表取第 stage 项：比阶数短沿用最后一项，空列表 0，结果夹到 0~1（列表全是比例，见 {@link #fractions}） */
    static double at(String key, @Nullable ForgeConfigSpec.ConfigValue<List<? extends Number>> value, List<Double> fallback, int stage) {
        Object o = TEST.get(key);
        List<? extends Number> list;
        if (o instanceof List<?> raw) {
            @SuppressWarnings("unchecked")
            List<? extends Number> cast = (List<? extends Number>) raw;
            list = cast;
        } else {
            list = value == null ? fallback : ECConfig.getValue(value);
        }
        return Math.min(1D, entry(list, stage));
    }

    /** 按阶的数值列表（不是比例，不夹到 1）取第 stage 项，规则同 {@link #at}；结果再夹到 0~max */
    static double amountAt(String key, @Nullable ForgeConfigSpec.ConfigValue<List<? extends Number>> value, List<Double> fallback, int stage, double max) {
        Object o = TEST.get(key);
        List<? extends Number> list;
        if (o instanceof List<?> raw) {
            @SuppressWarnings("unchecked")
            List<? extends Number> cast = (List<? extends Number>) raw;
            list = cast;
        } else {
            list = value == null ? fallback : ECConfig.getValue(value);
        }
        return Math.min(max, entry(list, Math.max(0, stage)));
    }

    /** 列表第 stage 项（比阶数短沿用最后一项，空列表 0；负数、非有限数当 0） */
    public static double entry(List<? extends Number> list, int stage) {
        if (list.isEmpty()) {
            return 0D;
        }
        double v = list.get(Math.max(0, Math.min(stage, list.size() - 1))).doubleValue();
        return Double.isFinite(v) && v > 0D ? v : 0D;
    }

    // ------------------------------------------------------------------
    // 读法
    // ------------------------------------------------------------------

    public static double offenseScale() {
        return d("offense_scale", offenseScale, 1D);
    }

    public static double defenseScale() {
        return d("defense_scale", defenseScale, 1D);
    }

    public static double aoeScale() {
        return d("aoe_scale", aoeScale, 1D);
    }

    public static boolean aoeHitsPlayers() {
        return b("aoe_hits_players", aoeHitsPlayers, false);
    }

    public static double poolMob() {
        return d("caps.pool_mob", poolMob, 1D);
    }

    public static double poolBoss() {
        return d("caps.pool_boss", poolBoss, 0.5D);
    }

    public static double poolPlayer() {
        return d("caps.pool_player", poolPlayer, 0.3D);
    }

    public static double reductionCap() {
        return d("caps.reduction", reduction, 0.6D);
    }

    public static double reductionCapPlayer() {
        return d("caps.reduction_player", reductionPlayer, 0.4D);
    }

    public static double armorPenCap() {
        return d("caps.armor_pen", armorPen, 0.3D);
    }

    public static double armorPenCapPlayer() {
        return d("caps.armor_pen_player", armorPenPlayer, 0.15D);
    }

    public static double lifestealPerSecond() {
        return d("caps.lifesteal_per_second", lifestealPerSecond, 0.06D);
    }

    public static double lifestealPerSecondPlayer() {
        return d("caps.lifesteal_per_second_player", lifestealPerSecondPlayer, 0.03D);
    }

    public static double lifestealPlayerScale() {
        return d("caps.lifesteal_player_scale", lifestealPlayerScale, 0.5D);
    }

    public static double cooldownReductionCap() {
        return d("caps.cooldown_reduction", cooldownReduction, 0.4D);
    }

    public static double dodgeCap() {
        return d("caps.dodge", dodge, 0.35D);
    }

    public static double dodgeCapPlayer() {
        return d("caps.dodge_player", dodgePlayer, 0.15D);
    }

    public static double weaponDamagePerStage() {
        return d("weapon.damage_per_stage", weaponDamagePerStage, 0.08D);
    }

    /** 刀剑这一家族每阶加的攻击伤害比例（裁决之刃、余烬剑各一个键；别的刀剑家族沿用 {@code weapon.damage_per_stage}） */
    public static double bladeAttackPerStage(AttunementFamily family) {
        if (GearStatSheet.VERDICT_BLADE.equals(family.id())) {
            return d("verdict_blade.attack_per_stage", verdictAttackPerStage, 0.08D);
        }
        if (GearStatSheet.EMBER_SWORD.equals(family.id())) {
            return d("ember_sword.attack_per_stage", emberAttackPerStage, 0.10D);
        }
        return weaponDamagePerStage();
    }

    public static double armorHealthPerStage() {
        return d("armor.health_per_stage", armorHealthPerStage, 2D);
    }

    public static int scarPointsPerLevel() {
        return Math.max(1, i("scars.points_per_level", scarPointsPerLevel, 2000));
    }

    public static int scarMaxLevel() {
        return Math.max(0, i("scars.max_level", scarMaxLevel, 10));
    }

    public static double scarWeaponPerLevel() {
        return d("scars.weapon_per_level", scarWeaponPerLevel, 0.01D);
    }

    public static double scarArmorPerLevel() {
        return d("scars.armor_per_level", scarArmorPerLevel, 0.01D);
    }

    public static double verdictPercentPerStage() {
        return d("verdict_blade.percent_per_stage", verdictPercentPerStage, 1D);
    }

    public static double verdictCapPerStage() {
        return d("verdict_blade.cap_per_stage", verdictCapPerStage, 20D);
    }

    public static double verdictLifesteal(int stage) {
        return at("verdict_blade.lifesteal", verdictLifesteal, DEFAULT_VERDICT_LIFESTEAL, stage);
    }

    public static double verdictArmorPen(int stage) {
        return at("verdict_blade.armor_pen", verdictArmorPen, DEFAULT_VERDICT_ARMOR_PEN, stage);
    }

    public static double verdictJudgmentCdr(int stage) {
        return at("verdict_blade.judgment_cooldown_reduction", verdictJudgmentCdr, DEFAULT_VERDICT_JUDGMENT_CDR, stage);
    }

    public static double executeThreshold() {
        return d("verdict_blade.execute_threshold", executeThreshold, 0.3D);
    }

    public static double executeBonus() {
        return d("verdict_blade.execute_bonus", executeBonus, 0.2D);
    }

    public static int executeStage() {
        return i("verdict_blade.execute_stage", executeStage, 4);
    }

    public static int finalVerdictStage() {
        return i("verdict_blade.final_verdict.stage", finalVerdictStage, 5);
    }

    public static int finalVerdictMarkTicks() {
        return i("verdict_blade.final_verdict.mark_ticks", finalVerdictMarkTicks, 100);
    }

    public static double finalVerdictPercent() {
        return d("verdict_blade.final_verdict.percent", finalVerdictPercent, 6D);
    }

    public static double finalVerdictCap() {
        return d("verdict_blade.final_verdict.cap", finalVerdictCap, 120D);
    }

    public static double finalVerdictBossPool() {
        return d("verdict_blade.final_verdict.boss_pool", finalVerdictBossPool, 0.4D);
    }

    public static double emberLifesteal(int stage) {
        return at("ember_sword.lifesteal", emberLifesteal, DEFAULT_EMBER_LIFESTEAL, stage);
    }

    public static int burstStage() {
        return i("ember_sword.burst.stage", burstStage, 2);
    }

    public static double burstChance() {
        return d("ember_sword.burst.chance", burstChance, 0.15D);
    }

    public static int burstCooldown() {
        return i("ember_sword.burst.cooldown", burstCooldown, 40);
    }

    public static double burstRadius() {
        return d("ember_sword.burst.radius", burstRadius, 3D);
    }

    public static int burstMaxTargets() {
        return i("ember_sword.burst.max_targets", burstMaxTargets, 8);
    }

    public static double burstFireDamage() {
        return d("ember_sword.burst.fire_damage", burstFireDamage, 4D);
    }

    public static double burstFirePercent() {
        return d("ember_sword.burst.fire_percent", burstFirePercent, 2D);
    }

    /** 火爆发每个目标这一下最多掉多少血；0 = 不封顶 */
    public static double burstFireCap() {
        return d("ember_sword.burst.fire_cap", burstFireCap, 100D);
    }

    public static double burstQuakeDamage() {
        return d("ember_sword.burst.quake_damage", burstQuakeDamage, 3D);
    }

    public static int burstDebuffTicks() {
        return i("ember_sword.burst.debuff_ticks", burstDebuffTicks, 60);
    }

    public static double gunMruDiscount(int stage) {
        return at("mru_gun.mru_discount", gunMruDiscount, DEFAULT_GUN_MRU_DISCOUNT, stage);
    }

    public static int gunAccumulateStage() {
        return i("mru_gun.accumulate_stage", gunAccumulateStage, 2);
    }

    public static double gunAccumulateRatio() {
        return d("mru_gun.accumulate_ratio", gunAccumulateRatio, 1D);
    }

    public static double gunArmorPen(int stage) {
        return at("mru_gun.armor_pen", gunArmorPen, DEFAULT_GUN_ARMOR_PEN, stage);
    }

    public static double gunReloadCdr(int stage) {
        return at("mru_gun.reload_cooldown_reduction", gunReloadCdr, DEFAULT_GUN_RELOAD_CDR, stage);
    }

    public static int overloadStage() {
        return i("mru_gun.overload.stage", overloadStage, 4);
    }

    public static int overloadShots() {
        return Math.max(1, i("mru_gun.overload.shots", overloadShots, 12));
    }

    public static int overloadShotsSingle() {
        return Math.max(1, i("mru_gun.overload.shots_single", overloadShotsSingle, 3));
    }

    public static double overloadRatio() {
        return d("mru_gun.overload.ratio", overloadRatio, 0.6D);
    }

    public static double overloadRadius() {
        return d("mru_gun.overload.radius", overloadRadius, 3D);
    }

    public static int overloadMaxTargets() {
        return i("mru_gun.overload.max_targets", overloadMaxTargets, 8);
    }
}
