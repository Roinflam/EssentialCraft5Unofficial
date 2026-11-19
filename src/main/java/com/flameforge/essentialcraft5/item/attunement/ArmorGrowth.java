package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;

/**
 * 盔甲的成长（tools/plan/gear_growth.md 第 10 节，阶表照方案稿推荐档 3.3）：五套盔甲的配置 {@code general.gear_growth.<家族>.*}、
 * 词条表（{@link GearStatSheet} 的填表函数）、进总闸的池子来源，以及各套读新键的地方。
 * <p>
 * <b>全套才算</b>：盔甲的词条和觉醒都按「四件都穿着这一套、取四件里最低的阶」（{@link Attunement#fullSetStage}）算，和各套原来「四件都到 n 阶」的阶效果一致；
 * 只有移速、攻速是每件各给四分之一（属性修饰符，{@link AttunementBonuses}），四件加起来是表里的数；魔力防护、虚空的护盾单价折扣
 * 看每件自己的阶（护盾本来就是每件各挡各的份额）。
 * <p>
 * <b>改了默认值的一律用这里的新键</b>（同 G1）：审判庭抗魔 / 首领减伤 / 律令改成按阶的列表，接管 {@code general.inquisitorium_armor.magic_resistance}、
 * {@code boss_resistance}、{@code edict_reduction} 和 {@code magic_resistance_stage_1}；宅男矩阵修复、过载屏障冷却接管
 * {@code general.computer_armor.repair_percent} / {@code overload_cooldown}；虚空闪避原来是写死的常量，改成按阶的列表。旧键留着定义、代码不再读。
 * <p>
 * 池子来源（{@code ECAttunement.init} 里 {@link #register}）：
 * <ul>
 *   <li>{@code armor_set}（增伤）：全套词条里的增伤（风之 2 阶、虚空 2 阶、宅男 1 / 2 阶），只认穿的人亲手打的（{@link #ownHit}），打玩家乘 {@code pvp_effect_scale}；</li>
 *   <li>{@code armor_scar}（减伤）：全套四件里最低的战痕等级 × {@code scars.armor_per_level}；{@code bypasses_invulnerability} 的不减。</li>
 * </ul>
 */
public final class ArmorGrowth {

    public static final List<Double> DEFAULT_MHS_SHIELD_DISCOUNT = List.of(0D, 0.1D);
    public static final List<Double> DEFAULT_MHS_MOVE_SPEED = List.of(0D, 0.05D);
    public static final List<Double> DEFAULT_WIND_ATTACK_SPEED = List.of(0D, 0.06D);
    public static final List<Double> DEFAULT_WIND_DAMAGE = List.of(0D, 0D, 0.1D);
    public static final List<Double> DEFAULT_VOID_DODGE = List.of(0.2D, 0.25D, 0.27D, 0.3D, 0.35D);
    public static final List<Double> DEFAULT_VOID_SHIELD_DISCOUNT = List.of(0D, 0.05D);
    public static final List<Double> DEFAULT_VOID_DAMAGE = List.of(0D, 0D, 0.08D);
    public static final List<Double> DEFAULT_INQ_MAGIC = List.of(0.35D, 0.45D, 0.45D, 0.5D, 0.55D);
    public static final List<Double> DEFAULT_INQ_BOSS = List.of(0.15D, 0.15D, 0.18D, 0.21D, 0.25D);
    public static final List<Double> DEFAULT_INQ_EDICT = List.of(0.4D, 0.5D);
    public static final List<Double> DEFAULT_INQ_LIFESTEAL = List.of(0D, 0D, 0.02D);
    public static final List<Double> DEFAULT_COMPUTER_DAMAGE = List.of(0D, 0.04D, 0.08D);
    public static final List<Double> DEFAULT_COMPUTER_CDR = List.of(0D, 0D, 0D, 0D, 0.15D);

    // 魔力防护
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> mhsShieldDiscount;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> mhsMoveSpeed;
    private static ForgeConfigSpec.IntValue surgeStage;
    private static ForgeConfigSpec.DoubleValue surgeRatio;
    private static ForgeConfigSpec.DoubleValue surgeMax;
    private static ForgeConfigSpec.DoubleValue surgeThreshold;
    private static ForgeConfigSpec.DoubleValue surgeRadius;
    private static ForgeConfigSpec.IntValue surgeMaxTargets;
    // 风之
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> windAttackSpeed;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> windDamage;
    private static ForgeConfigSpec.IntValue stormStage;
    private static ForgeConfigSpec.DoubleValue stormRadius;
    private static ForgeConfigSpec.IntValue stormMaxTargets;
    private static ForgeConfigSpec.DoubleValue stormKnockback;
    private static ForgeConfigSpec.IntValue stormSlowTicks;
    private static ForgeConfigSpec.IntValue stormWindow;
    private static ForgeConfigSpec.DoubleValue stormBonus;
    // 虚空
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> voidDodge;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> voidShieldDiscount;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> voidDamage;
    private static ForgeConfigSpec.IntValue voidPiercingStage;
    private static ForgeConfigSpec.DoubleValue voidPiercingShare;
    private static ForgeConfigSpec.DoubleValue voidPiercingPrice;
    private static ForgeConfigSpec.DoubleValue voidPvpShare;
    private static ForgeConfigSpec.DoubleValue voidPvpPrice;
    private static ForgeConfigSpec.IntValue afterimageStage;
    private static ForgeConfigSpec.IntValue afterimageDelay;
    private static ForgeConfigSpec.DoubleValue afterimageRatio;
    private static ForgeConfigSpec.DoubleValue afterimageCap;
    private static ForgeConfigSpec.DoubleValue afterimageRadius;
    private static ForgeConfigSpec.IntValue afterimageMaxTargets;
    private static ForgeConfigSpec.IntValue afterimageSlowTicks;
    private static ForgeConfigSpec.IntValue afterimageCooldown;
    // 审判庭
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> inqMagic;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> inqBoss;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> inqEdict;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> inqLifesteal;
    private static ForgeConfigSpec.IntValue sentenceStage;
    private static ForgeConfigSpec.IntValue sentenceHits;
    private static ForgeConfigSpec.IntValue sentenceCooldown;
    private static ForgeConfigSpec.DoubleValue sentencePercent;
    private static ForgeConfigSpec.DoubleValue sentenceCap;
    private static ForgeConfigSpec.DoubleValue sentenceBossPool;
    // 宅男
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> computerDamage;
    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> computerCdr;
    private static ForgeConfigSpec.DoubleValue computerRepair;
    private static ForgeConfigSpec.IntValue computerOverloadCooldown;
    private static ForgeConfigSpec.IntValue pulseStage;
    private static ForgeConfigSpec.DoubleValue pulseRadius;
    private static ForgeConfigSpec.IntValue pulseMaxTargets;
    private static ForgeConfigSpec.DoubleValue pulseRatio;
    private static ForgeConfigSpec.DoubleValue pulseCap;
    private static ForgeConfigSpec.IntValue pulseSlowTicks;

    private ArmorGrowth() {
    }

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    /** 写在 {@code general.gear_growth} 里面（{@link GearGrowthConfig#define} 调） */
    static void define(ForgeConfigSpec.Builder b) {
        b.comment("Mana Shielding armor (full set; lists are by stage, entry i = stage i).", "魔力防护套装（全套；列表按阶，第 i 项 = i 阶）").push("mhs_armor");
        ArmorBaseStats.define(b, "mhs_armor", ArmorBaseStats.MHS);
        mhsShieldDiscount = GearGrowthConfig.fractions(b.comment("Shield MRU cost discount.", "护盾单价折扣"), "shield_discount", DEFAULT_MHS_SHIELD_DISCOUNT);
        mhsMoveSpeed = GearGrowthConfig.fractions(b.comment("Movement speed bonus of the four pieces together (each piece gives a quarter).", "四件合计的移速加成（每件给四分之一）"),
                "move_speed", DEFAULT_MHS_MOVE_SPEED);
        b.comment("Surge (stage-max awakening): part of what the shield blocks is stored; once enough is stored, your next melee hit releases it around you.",
                "回涌（满阶觉醒）：护盾挡下的伤害存一部分，存够以后你下一次近战打实时在身边放出去").push("surge");
        surgeStage = b.comment("Stage that unlocks Surge.", "几阶解锁").defineInRange("stage", 2, 0, Attunement.MAX_STAGE);
        surgeRatio = b.comment("Fraction of blocked damage stored.", "挡下的伤害存几成").defineInRange("ratio", 0.5D, 0D, 5D);
        surgeMax = b.comment("Most stored.", "最多存多少").defineInRange("max", 30D, 0D, 10_000D);
        surgeThreshold = b.comment("Stored amount needed before it releases.", "存到多少才放").defineInRange("threshold", 15D, 0D, 10_000D);
        surgeRadius = b.comment("Release radius in blocks.", "放出的半径（格）").defineInRange("radius", 3D, 0.5D, 16D);
        surgeMaxTargets = b.comment("Most creatures hit.", "最多打几个").defineInRange("max_targets", 8, 1, 64);
        b.pop();
        b.pop();

        b.comment("Wind armor (full set).", "风之护甲（全套）").push("wind_armor");
        ArmorBaseStats.define(b, "wind_armor", ArmorBaseStats.WIND);
        windAttackSpeed = GearGrowthConfig.fractions(b.comment("Attack speed bonus of the four pieces together (each piece gives a quarter).", "四件合计的攻速加成（每件给四分之一）"),
                "attack_speed", DEFAULT_WIND_ATTACK_SPEED);
        windDamage = GearGrowthConfig.fractions(b.comment("Damage bonus on your own hits (bonus pool).", "亲手打的增伤（进增伤池）"), "damage", DEFAULT_WIND_DAMAGE);
        b.comment("Storm Step (stage-max awakening): each midair jump bursts a wind ring that knocks enemies back and slows them; your next melee hit soon after deals more.",
                "风暴步（满阶觉醒）：空中跳时脚下炸开风环，击退并减速周围的敌对生物，之后不久你的下一下近战增伤").push("storm_step");
        stormStage = b.comment("Stage that unlocks Storm Step.", "几阶解锁").defineInRange("stage", 3, 0, Attunement.MAX_STAGE);
        stormRadius = b.comment("Ring radius in blocks.", "风环半径（格）").defineInRange("radius", 3D, 0.5D, 16D);
        stormMaxTargets = b.comment("Most creatures pushed.", "最多推几个").defineInRange("max_targets", 8, 1, 64);
        stormKnockback = b.comment("Horizontal knockback strength.", "水平击退力度").defineInRange("knockback", 0.8D, 0D, 5D);
        stormSlowTicks = b.comment("Ticks of Slowness I on enemies in the ring.", "风环里的敌人缓慢 I 几刻").defineInRange("slow_ticks", 20, 0, 1200);
        stormWindow = b.comment("Ticks after the jump in which your next melee hit gets the bonus.", "空中跳以后多少刻内的下一下近战有加成").defineInRange("window", 40, 0, 1200);
        stormBonus = b.comment("That hit's bonus (bonus pool).", "那一下的增伤（进增伤池）").defineInRange("bonus", 0.3D, 0D, 5D);
        b.pop();
        b.pop();

        b.comment("Void armor (dodge by chestplate stage; the rest full set).", "虚空套装（闪避看胸甲的阶，其余全套）").push("void_armor");
        ArmorBaseStats.define(b, "void_armor", ArmorBaseStats.VOID);
        voidDodge = GearGrowthConfig.fractions(b.comment("Chestplate dodge chance against attacks from other creatures, by chestplate stage (capped by caps.dodge / caps.dodge_player).",
                "胸甲闪避别的生物攻击的几率，按胸甲的阶（受 caps.dodge / caps.dodge_player 封顶）"), "dodge", DEFAULT_VOID_DODGE);
        voidShieldDiscount = GearGrowthConfig.fractions(b.comment("Shield MRU cost discount by piece stage (on top of the stage-3 7/8 price).",
                "按这件的阶的护盾单价折扣（在 3 阶 7/8 价之外再打）"), "shield_discount", DEFAULT_VOID_SHIELD_DISCOUNT);
        voidDamage = GearGrowthConfig.fractions(b.comment("Damage bonus on your own hits (bonus pool).", "亲手打的增伤（进增伤池）"), "damage", DEFAULT_VOID_DAMAGE);
        voidPiercingStage = b.comment("Piece stage from which the shield also takes part of armor-piercing damage from monsters.",
                "这件几阶起护盾也分走怪物打来的无视护甲伤害").defineInRange("piercing_stage", 3, 0, Attunement.MAX_STAGE);
        voidPiercingShare = b.comment("Fraction of that damage the shield takes (all four pieces together).", "这种伤害护盾分走几成（四件合计）")
                .defineInRange("piercing_share", 0.4D, 0D, 1D);
        voidPiercingPrice = b.comment("Cost per point for it, times the usual (never paid from infinite MRU crystals).", "这种每点电费是平时的几倍（不从无尽 MRU 晶体扣）")
                .defineInRange("piercing_price_multiplier", 3D, 0D, 100D);
        voidPvpShare = b.comment("When another player attacks, the shield takes at most this fraction of the hit (all four pieces together).",
                "攻击者是别的玩家时，护盾这一下最多挡几成（四件合计）").defineInRange("pvp_shield_max_share", 0.5D, 0D, 1D);
        voidPvpPrice = b.comment("When another player attacks, cost per point times this.", "攻击者是别的玩家时每点电费乘的倍数")
                .defineInRange("pvp_shield_cost_multiplier", 2D, 0D, 100D);
        b.comment("Afterimage (stage-max awakening): dodging an enemy leaves an afterimage that bursts a moment later.",
                "残像（满阶觉醒）：躲开敌人时原地留一个残影，随后炸开").push("afterimage");
        afterimageStage = b.comment("Stage that unlocks Afterimage.", "几阶解锁").defineInRange("stage", 4, 0, Attunement.MAX_STAGE);
        afterimageDelay = b.comment("Ticks before it bursts.", "多少刻后炸开").defineInRange("delay", 20, 1, 200);
        afterimageRatio = b.comment("Burst damage as a fraction of the dodged hit.", "炸开的伤害是被躲开那一下的几成").defineInRange("ratio", 0.6D, 0D, 5D);
        afterimageCap = b.comment("Cap on that damage.", "这一下的封顶").defineInRange("cap", 40D, 0D, 10_000D);
        afterimageRadius = b.comment("Burst radius in blocks.", "半径（格）").defineInRange("radius", 3D, 0.5D, 16D);
        afterimageMaxTargets = b.comment("Most creatures hit.", "最多打几个").defineInRange("max_targets", 8, 1, 64);
        afterimageSlowTicks = b.comment("Ticks of Slowness II on creatures it hits.", "被炸到的缓慢 II 几刻").defineInRange("slow_ticks", 40, 0, 1200);
        afterimageCooldown = b.comment("Ticks before it can trigger again.", "多少刻才能再触发").defineInRange("cooldown", 100, 0, 6000);
        b.pop();
        b.pop();

        b.comment("Inquisitorium armor (full set). Replaces general.inquisitorium_armor.magic_resistance / boss_resistance / edict_reduction and magic_resistance_stage_1.",
                "审判庭套装（全套）。接管 general.inquisitorium_armor.magic_resistance / boss_resistance / edict_reduction 和 magic_resistance_stage_1").push("inquisitorium_armor");
        ArmorBaseStats.define(b, "inquisitorium_armor", ArmorBaseStats.INQUISITORIUM);
        inqMagic = GearGrowthConfig.fractions(b.comment("Magic resistance by stage (reduction pool).", "按阶的抗魔（进减伤池）"), "magic_resistance", DEFAULT_INQ_MAGIC);
        inqBoss = GearGrowthConfig.fractions(b.comment("Damage reduction against bosses by stage (reduction pool).", "按阶的首领减伤（进减伤池）"), "boss_resistance", DEFAULT_INQ_BOSS);
        inqEdict = GearGrowthConfig.fractions(b.comment("Edict: how much sooner harmful effects wear off, by stage (at most 0.9).", "律令：有害效果的时长缩短几成，按阶（最多 0.9）"),
                "edict_reduction", DEFAULT_INQ_EDICT);
        inqLifesteal = GearGrowthConfig.fractions(b.comment("Lifesteal on your own hits against targets you convicted.", "亲手打被你定罪的目标时的吸血"),
                "lifesteal", DEFAULT_INQ_LIFESTEAL);
        b.comment("Sentence (stage-max awakening): a convicted target that takes enough of your own hits is sentenced.",
                "宣判（满阶觉醒）：被定罪的目标挨你亲手打实够几下就宣判").push("sentence");
        sentenceStage = b.comment("Stage that unlocks Sentence.", "几阶解锁").defineInRange("stage", 4, 0, Attunement.MAX_STAGE);
        sentenceHits = b.comment("Landed hits needed.", "要打实几下").defineInRange("hits", 5, 1, 100);
        sentenceCooldown = b.comment("Ticks before the same target can be sentenced again.", "同一个目标多少刻才能再宣判一次").defineInRange("cooldown", 200, 0, 72_000);
        sentencePercent = b.comment("Ordinary targets: extra hit of this percent of max health (armor-piercing).", "普通目标：补最大生命的百分之几（无视护甲）")
                .defineInRange("percent", 5D, 0D, 100D);
        sentenceCap = b.comment("Cap on that hit.", "这一下的封顶").defineInRange("cap", 100D, 0D, 100_000D);
        sentenceBossPool = b.comment("Bosses: the sentencing hit gets this much extra in the bonus pool (still capped by caps.pool_boss).",
                "首领：宣判的那一下增伤池再加这么多（照样受 caps.pool_boss 封顶）").defineInRange("boss_pool", 0.4D, 0D, 5D);
        b.pop();
        b.pop();

        b.comment("Computer armor (full set). Replaces general.computer_armor.repair_percent / overload_cooldown.",
                "宅男套装（全套）。接管 general.computer_armor.repair_percent / overload_cooldown").push("computer_armor");
        ArmorBaseStats.define(b, "computer_armor", ArmorBaseStats.COMPUTER);
        computerDamage = GearGrowthConfig.fractions(b.comment("Damage bonus on your own hits (bonus pool).", "亲手打的增伤（进增伤池）"), "damage", DEFAULT_COMPUTER_DAMAGE);
        computerCdr = GearGrowthConfig.fractions(b.comment("Cooldown reduction for the Rewind module and Overload Barrier (capped by caps.cooldown_reduction).",
                "回溯模块、过载屏障的冷却缩减（受 caps.cooldown_reduction 封顶）"), "cooldown_reduction", DEFAULT_COMPUTER_CDR);
        computerRepair = b.comment("Matrix Repair: fraction of max health healed per second.", "矩阵修复：每秒回最大生命的比例")
                .defineInRange("repair_percent", 0.015D, 0D, 1D);
        computerOverloadCooldown = b.comment("Overload Barrier: cooldown in ticks before cooldown reduction.", "过载屏障：冷却多少刻（冷却缩减之前）")
                .defineInRange("overload_cooldown", 900, 0, 72_000);
        b.comment("Overcharge Pulse (stage-max awakening): Overload Barrier also releases a pulse that slows and hurts enemies around you.",
                "超载脉冲（满阶觉醒）：过载屏障展开时同时放出脉冲，减速并伤害周围的敌对生物").push("overcharge_pulse");
        pulseStage = b.comment("Stage that unlocks Overcharge Pulse.", "几阶解锁").defineInRange("stage", 5, 0, Attunement.MAX_STAGE);
        pulseRadius = b.comment("Pulse radius in blocks.", "半径（格）").defineInRange("radius", 6D, 0.5D, 16D);
        pulseMaxTargets = b.comment("Most creatures hit.", "最多打几个").defineInRange("max_targets", 12, 1, 64);
        pulseRatio = b.comment("Damage as a fraction of the blocked hit.", "伤害是被挡那一下的几成").defineInRange("ratio", 0.5D, 0D, 5D);
        pulseCap = b.comment("Cap on that damage.", "这一下的封顶").defineInRange("cap", 40D, 0D, 10_000D);
        pulseSlowTicks = b.comment("Ticks of Slowness III on creatures it hits.", "被打到的缓慢 III 几刻").defineInRange("slow_ticks", 40, 0, 1200);
        b.pop();
        b.pop();
    }

    private static double d(String key, ForgeConfigSpec.DoubleValue value, double fallback) {
        return GearGrowthConfig.d(key, value, fallback);
    }

    private static int i(String key, ForgeConfigSpec.IntValue value, int fallback) {
        return GearGrowthConfig.i(key, value, fallback);
    }

    private static double at(String key, ForgeConfigSpec.ConfigValue<List<? extends Number>> value, List<Double> fallback, int stage) {
        return stage < 0 ? 0D : GearGrowthConfig.at(key, value, fallback, stage);
    }

    // 魔力防护
    public static double mhsShieldDiscount(int stage) { return at("mhs_armor.shield_discount", mhsShieldDiscount, DEFAULT_MHS_SHIELD_DISCOUNT, stage); }
    public static double mhsMoveSpeed(int stage) { return at("mhs_armor.move_speed", mhsMoveSpeed, DEFAULT_MHS_MOVE_SPEED, stage); }
    public static int surgeStage() { return i("mhs_armor.surge.stage", surgeStage, 2); }
    public static double surgeRatio() { return d("mhs_armor.surge.ratio", surgeRatio, 0.5D); }
    public static double surgeMax() { return d("mhs_armor.surge.max", surgeMax, 30D); }
    public static double surgeThreshold() { return d("mhs_armor.surge.threshold", surgeThreshold, 15D); }
    public static double surgeRadius() { return d("mhs_armor.surge.radius", surgeRadius, 3D); }
    public static int surgeMaxTargets() { return i("mhs_armor.surge.max_targets", surgeMaxTargets, 8); }
    // 风之
    public static double windAttackSpeed(int stage) { return at("wind_armor.attack_speed", windAttackSpeed, DEFAULT_WIND_ATTACK_SPEED, stage); }
    public static double windDamage(int stage) { return at("wind_armor.damage", windDamage, DEFAULT_WIND_DAMAGE, stage); }
    public static int stormStage() { return i("wind_armor.storm_step.stage", stormStage, 3); }
    public static double stormRadius() { return d("wind_armor.storm_step.radius", stormRadius, 3D); }
    public static int stormMaxTargets() { return i("wind_armor.storm_step.max_targets", stormMaxTargets, 8); }
    public static double stormKnockback() { return d("wind_armor.storm_step.knockback", stormKnockback, 0.8D); }
    public static int stormSlowTicks() { return i("wind_armor.storm_step.slow_ticks", stormSlowTicks, 20); }
    public static int stormWindow() { return i("wind_armor.storm_step.window", stormWindow, 40); }
    public static double stormBonus() { return d("wind_armor.storm_step.bonus", stormBonus, 0.3D); }
    // 虚空
    public static double voidDodge(int stage) { return at("void_armor.dodge", voidDodge, DEFAULT_VOID_DODGE, Math.max(0, stage)); }
    public static double voidShieldDiscount(int stage) { return at("void_armor.shield_discount", voidShieldDiscount, DEFAULT_VOID_SHIELD_DISCOUNT, stage); }
    public static double voidDamage(int stage) { return at("void_armor.damage", voidDamage, DEFAULT_VOID_DAMAGE, stage); }
    public static int voidPiercingStage() { return i("void_armor.piercing_stage", voidPiercingStage, 3); }
    public static double voidPiercingShare() { return d("void_armor.piercing_share", voidPiercingShare, 0.4D); }
    public static double voidPiercingPrice() { return d("void_armor.piercing_price_multiplier", voidPiercingPrice, 3D); }
    public static double voidPvpShare() { return d("void_armor.pvp_shield_max_share", voidPvpShare, 0.5D); }
    public static double voidPvpPrice() { return d("void_armor.pvp_shield_cost_multiplier", voidPvpPrice, 2D); }
    public static int afterimageStage() { return i("void_armor.afterimage.stage", afterimageStage, 4); }
    public static int afterimageDelay() { return i("void_armor.afterimage.delay", afterimageDelay, 20); }
    public static double afterimageRatio() { return d("void_armor.afterimage.ratio", afterimageRatio, 0.6D); }
    public static double afterimageCap() { return d("void_armor.afterimage.cap", afterimageCap, 40D); }
    public static double afterimageRadius() { return d("void_armor.afterimage.radius", afterimageRadius, 3D); }
    public static int afterimageMaxTargets() { return i("void_armor.afterimage.max_targets", afterimageMaxTargets, 8); }
    public static int afterimageSlowTicks() { return i("void_armor.afterimage.slow_ticks", afterimageSlowTicks, 40); }
    public static int afterimageCooldown() { return i("void_armor.afterimage.cooldown", afterimageCooldown, 100); }
    // 审判庭
    public static double inqMagicResistance(int stage) { return at("inquisitorium_armor.magic_resistance", inqMagic, DEFAULT_INQ_MAGIC, stage); }
    public static double inqBossResistance(int stage) { return at("inquisitorium_armor.boss_resistance", inqBoss, DEFAULT_INQ_BOSS, stage); }
    public static double inqEdict(int stage) { return Math.min(0.9D, at("inquisitorium_armor.edict_reduction", inqEdict, DEFAULT_INQ_EDICT, stage)); }
    public static double inqLifesteal(int stage) { return at("inquisitorium_armor.lifesteal", inqLifesteal, DEFAULT_INQ_LIFESTEAL, stage); }
    public static int sentenceStage() { return i("inquisitorium_armor.sentence.stage", sentenceStage, 4); }
    public static int sentenceHits() { return Math.max(1, i("inquisitorium_armor.sentence.hits", sentenceHits, 5)); }
    public static int sentenceCooldown() { return i("inquisitorium_armor.sentence.cooldown", sentenceCooldown, 200); }
    public static double sentencePercent() { return d("inquisitorium_armor.sentence.percent", sentencePercent, 5D); }
    public static double sentenceCap() { return d("inquisitorium_armor.sentence.cap", sentenceCap, 100D); }
    public static double sentenceBossPool() { return d("inquisitorium_armor.sentence.boss_pool", sentenceBossPool, 0.4D); }
    // 宅男
    public static double computerDamage(int stage) { return at("computer_armor.damage", computerDamage, DEFAULT_COMPUTER_DAMAGE, stage); }
    public static double computerCdr(int stage) { return at("computer_armor.cooldown_reduction", computerCdr, DEFAULT_COMPUTER_CDR, stage); }
    public static double computerRepairPercent() { return d("computer_armor.repair_percent", computerRepair, 0.015D); }
    public static int computerOverloadCooldownBase() { return i("computer_armor.overload_cooldown", computerOverloadCooldown, 900); }
    public static int pulseStage() { return i("computer_armor.overcharge_pulse.stage", pulseStage, 5); }
    public static double pulseRadius() { return d("computer_armor.overcharge_pulse.radius", pulseRadius, 6D); }
    public static int pulseMaxTargets() { return i("computer_armor.overcharge_pulse.max_targets", pulseMaxTargets, 12); }
    public static double pulseRatio() { return d("computer_armor.overcharge_pulse.ratio", pulseRatio, 0.5D); }
    public static double pulseCap() { return d("computer_armor.overcharge_pulse.cap", pulseCap, 40D); }
    public static int pulseSlowTicks() { return i("computer_armor.overcharge_pulse.slow_ticks", pulseSlowTicks, 40); }

    /** 过载屏障实际的冷却：{@code overload_cooldown} 再按全套这一阶的冷却缩减打折（过 {@code caps.cooldown_reduction}） */
    public static int computerOverloadCooldown(int setStage) {
        return GearCaps.reducedCooldown(computerOverloadCooldownBase(), computerCdr(setStage));
    }

    // ------------------------------------------------------------------
    // 词条表、觉醒、池子来源
    // ------------------------------------------------------------------

    private static void put(EnumMap<GearStat, Double> out, GearStat stat, double value) {
        if (value > 0D && Double.isFinite(value)) {
            out.merge(stat, value, Double::sum);
        }
    }

    /** {@code ECAttunement.init} 调：五套的填表函数、觉醒、池子来源 */
    public static void register() {
        GearStatSheet.register(ECAttunement.MHS_ARMOR.id(), (stack, stage, out) -> {
            put(out, GearStat.MRU_DISCOUNT, mhsShieldDiscount(stage));
            put(out, GearStat.MOVE_SPEED, mhsMoveSpeed(stage));
        });
        GearStatSheet.register(ECAttunement.WIND_ARMOR.id(), (stack, stage, out) -> {
            put(out, GearStat.ATTACK_SPEED, windAttackSpeed(stage));
            put(out, GearStat.DAMAGE, windDamage(stage));
        });
        GearStatSheet.register(ECAttunement.VOID_ARMOR.id(), (stack, stage, out) -> {
            put(out, GearStat.DODGE, Math.min(voidDodge(stage), GearCaps.dodgeCap(null)));
            put(out, GearStat.MRU_DISCOUNT, voidShieldDiscount(stage));
            put(out, GearStat.DAMAGE, voidDamage(stage));
        });
        GearStatSheet.register(ECAttunement.INQUISITORIUM_ARMOR.id(), (stack, stage, out) -> put(out, GearStat.LIFESTEAL, inqLifesteal(stage)));
        GearStatSheet.register(ECAttunement.COMPUTER_ARMOR.id(), (stack, stage, out) -> {
            put(out, GearStat.DAMAGE, computerDamage(stage));
            put(out, GearStat.COOLDOWN, computerCdr(stage));
        });

        String prefix = "tooltip." + EssentialCraft5.MODID + ".gear_awakening.";
        GearStatSheet.registerAwakening(ECAttunement.MHS_ARMOR.id(), new GearStatSheet.Awakening(ArmorGrowth::surgeStage, prefix + "surge",
                stack -> new Object[]{AttunementStageLines.percent(surgeRatio()), AttunementStageLines.number(surgeMax()),
                        AttunementStageLines.number(surgeThreshold()), AttunementStageLines.number(surgeRadius())}));
        GearStatSheet.registerAwakening(ECAttunement.WIND_ARMOR.id(), new GearStatSheet.Awakening(ArmorGrowth::stormStage, prefix + "storm_step",
                stack -> new Object[]{AttunementStageLines.number(stormRadius()), AttunementStageLines.seconds(stormWindow()),
                        AttunementStageLines.percent(stormBonus())}));
        GearStatSheet.registerAwakening(ECAttunement.VOID_ARMOR.id(), new GearStatSheet.Awakening(ArmorGrowth::afterimageStage, prefix + "afterimage",
                stack -> new Object[]{AttunementStageLines.seconds(afterimageDelay()), AttunementStageLines.percent(afterimageRatio()),
                        AttunementStageLines.number(afterimageCap()), AttunementStageLines.number(afterimageRadius()),
                        AttunementStageLines.seconds(afterimageCooldown())}));
        GearStatSheet.registerAwakening(ECAttunement.INQUISITORIUM_ARMOR.id(), new GearStatSheet.Awakening(ArmorGrowth::sentenceStage, prefix + "sentence",
                stack -> new Object[]{sentenceHits(), AttunementStageLines.number(sentencePercent()), AttunementStageLines.number(sentenceCap()),
                        AttunementStageLines.percent(sentenceBossPool()), AttunementStageLines.seconds(sentenceCooldown())}));
        GearStatSheet.registerAwakening(ECAttunement.COMPUTER_ARMOR.id(), new GearStatSheet.Awakening(ArmorGrowth::pulseStage, prefix + "overcharge_pulse",
                stack -> new Object[]{AttunementStageLines.number(pulseRadius()), AttunementStageLines.percent(pulseRatio()),
                        AttunementStageLines.number(pulseCap())}));

        GearPool.registerOffense("armor_set", context -> {
            if (!(context.attacker() instanceof ServerPlayer player) || !ownHit(context.source(), player, context.target())) {
                return 0D;
            }
            EnumMap<GearStat, Double> sheet = setSheet(player);
            Double damage = sheet == null ? null : sheet.get(GearStat.DAMAGE);
            return damage == null ? 0D : damage * context.pvpScale();
        });
        // 和别的减伤来源同一个口径：要有攻击者、不是自己打自己；bypasses_invulnerability、unshieldable 不减（腐化、摔落这类没有攻击者的不变轻）
        GearPool.registerDefense("armor_scar", context -> context.source().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                || context.source().is(com.flameforge.essentialcraft5.registry.ECTags.UNSHIELDABLE)
                || context.source().getEntity() == null || context.source().getEntity() == context.target()
                ? 0D : scarReduction(context.target()));
    }

    /**
     * 这一下是不是 player 亲手打的（盔甲的增伤认它）：真玩家、记在他头上、不是打自己；反伤、饰品打出的、范围技（{@link AttunementGains#notOwnHit}）
     * 和裁断 / 范围技补伤（{@link GearPool#skipsOffense}）不算。弹射物、射线照算，那是他自己打的。
     */
    public static boolean ownHit(DamageSource source, Player player, LivingEntity target) {
        return source.getEntity() == player && player != target && ECProtection.isRealPlayer(player) && !GearPool.skipsOffense(source)
                && !AttunementGains.notOwnHit(source) && !source.is(DamageTypes.THORNS);
    }

    /** 亲手近战（直接来源是他本人）：风暴步、回涌认它 */
    public static boolean ownMelee(DamageSource source, Player player, LivingEntity target) {
        return ownHit(source, player, target) && source.getDirectEntity() == player;
    }

    /** 穿着全套的哪一家（四件都是同一套盔甲家族）；没穿齐是 null */
    @Nullable
    public static AttunementFamily setFamily(LivingEntity entity) {
        AttunementFamily family = AttunementFamily.of(entity.getItemBySlot(EquipmentSlot.CHEST));
        if (family == null || family.kind() != AttunementFamily.Kind.ARMOR) {
            return null;
        }
        return Attunement.fullSetStage(entity, family) >= 0 ? family : null;
    }

    /** 全套这一阶的词条（取四件里最低的阶；没穿齐是 null） */
    @Nullable
    public static EnumMap<GearStat, Double> setSheet(LivingEntity entity) {
        AttunementFamily family = setFamily(entity);
        if (family == null) {
            return null;
        }
        int stage = Attunement.fullSetStage(entity, family);
        return GearStatSheet.at(entity.getItemBySlot(EquipmentSlot.CHEST), family, stage);
    }

    /** 全套的战痕等级：四件里最低的（没穿齐 0） */
    public static int scarLevel(LivingEntity entity) {
        if (setFamily(entity) == null) {
            return 0;
        }
        int min = Integer.MAX_VALUE;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            min = Math.min(min, BattleScars.level(entity.getItemBySlot(slot)));
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /** 全套战痕的减伤：最低等级 × {@code scars.armor_per_level} */
    public static double scarReduction(LivingEntity entity) {
        int level = scarLevel(entity);
        return level <= 0 ? 0D : level * Math.max(0D, GearGrowthConfig.scarArmorPerLevel());
    }

    /** 盔甲给的吸血率（审判庭：亲手打被自己定罪的目标）；没有是 0 */
    public static double lifestealRate(ServerPlayer player, LivingEntity target, DamageSource source) {
        if (!ownHit(source, player, target)) {
            return 0D;
        }
        int stage = Attunement.fullSetStage(player, ECAttunement.INQUISITORIUM_ARMOR);
        if (stage < 0) {
            return 0D;
        }
        double rate = inqLifesteal(stage);
        return rate > 0D && com.flameforge.essentialcraft5.item.InquisitoriumArmorEvents.isConvicted(player, target) ? rate : 0D;
    }

    /**
     * 一件盔甲的移速 / 攻速修饰符数额（{@code MULTIPLY_BASE}，四件相加 = 表里的数）：按这件自己的阶取四分之一。
     */
    public static double pieceSpeed(ItemStack stack, GearStat stat) {
        AttunementFamily family = AttunementFamily.of(stack);
        if (family == null || family.kind() != AttunementFamily.Kind.ARMOR) {
            return 0D;
        }
        Double value = GearStatSheet.at(stack, family, Attunement.stage(stack)).get(stat);
        return value == null ? 0D : value / 4D;
    }
}
