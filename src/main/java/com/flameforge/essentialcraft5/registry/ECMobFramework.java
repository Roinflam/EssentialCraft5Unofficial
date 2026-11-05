package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第十三批 13c-Fa「生成与掉落框架」的注册类（batch13.md §5.1、§5.4、§9.3）：新怪物的基类与规则都在别处（{@code entity/mob/*}、{@code loot/*}、
 * {@code player/MobKillLedger}、{@code worldgen/AddSpawnCostsBiomeModifier}、{@code entity/MRUPresenceIndex}），这里只放要在模组构造阶段登记的东西：
 * <ul>
 *   <li>服务端配置 {@code general.mobs}（{@link Config}）：13c 各路怪物的配置都是它下面的子小节，这一层放共用的（精英、公平击杀的伤害占比、
 *       元素弱点倍率、按人递减各组的 F）；</li>
 *   <li>精英给的同调点倍数（{@link AttunementGains#addMultiplier}，batch13.md §3.3.2：精英 ×3）。</li>
 * </ul>
 * 战利品条件 {@code essentialcraft5:fair_kill}、{@code essentialcraft5:kill_fatigue} 和生物群系修改器类型 {@code essentialcraft5:add_spawn_costs}
 * 在各自类的嵌套注册类里用 RegisterEvent 登记（照 {@code loot/NotDisabledCondition} 的写法，类名带前缀，见 tools/check_event_handlers.py）。
 * 文字进 tools/lang/keys/ecmobframework.json。init 排在 13c 各路之前（ECRegistries），所以 {@code general.mobs} 这一层的说明由这里写。
 */
public final class ECMobFramework {

    /** 13c 各路怪物配置的上一层小节名：{@code general.mobs} */
    public static final String CONFIG_SECTION = "mobs";

    private ECMobFramework() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段） */
    public static void init() {
        ECConfig.general(Config::define);
        AttunementGains.addMultiplier(mob -> ECMonster.isElite(mob) ? Config.eliteAttunementMultiplier() : 1);
    }

    /**
     * 服务端配置 {@code general.mobs} 这一层：精英、公平击杀、元素弱点、按人递减（子小节 {@code kill_fatigue}）。各路怪物自己的子小节由各路的注册类登记，
     * 写在这一层下面。读数一律走这里的静态方法（配置没加载时给默认值，GameTest 的覆盖值优先）。
     */
    public static final class Config {

        /** 按人递减的组和默认的 F（batch13.md §5.4）：这名玩家最近一小时在线时间里杀到第 F 只以前不打折 */
        public static final Map<String, Integer> DEFAULT_FREE_KILLS;

        static {
            Map<String, Integer> free = new LinkedHashMap<>();
            free.put("overworld_common", 32);
            free.put("ember", 24);
            free.put("wind_storm", 8);
            free.put("hoanna_common", 30);
            free.put("hoanna_titanite", 20);
            free.put("elite", 6);
            DEFAULT_FREE_KILLS = Collections.unmodifiableMap(free);
        }

        public static final double DEFAULT_ELITE_CHANCE = 0.05D;
        public static final double DEFAULT_ELITE_CHANCE_HOANNA = 0.10D;
        public static final int DEFAULT_ELITE_RADIUS = 48;
        public static final int DEFAULT_ELITE_MAX_NEARBY = 1;
        public static final double DEFAULT_ELITE_HEALTH = 2.5D;
        public static final double DEFAULT_ELITE_ATTACK = 1.4D;
        public static final double DEFAULT_ELITE_ARMOR = 4D;
        public static final double DEFAULT_ELITE_KNOCKBACK = 0.3D;
        public static final int DEFAULT_ELITE_XP = 3;
        public static final int DEFAULT_ELITE_ATTUNEMENT = 3;
        public static final double DEFAULT_FAIR_KILL_SHARE = 0.5D;
        public static final double DEFAULT_WEAKNESS = 1.5D;
        public static final double DEFAULT_RESISTANCE = 0.5D;

        public static ForgeConfigSpec.DoubleValue eliteChance;
        public static ForgeConfigSpec.DoubleValue eliteChanceHoanna;
        public static ForgeConfigSpec.IntValue eliteRadius;
        public static ForgeConfigSpec.IntValue eliteMaxNearby;
        public static ForgeConfigSpec.DoubleValue eliteHealth;
        public static ForgeConfigSpec.DoubleValue eliteAttack;
        public static ForgeConfigSpec.DoubleValue eliteArmor;
        public static ForgeConfigSpec.DoubleValue eliteKnockback;
        public static ForgeConfigSpec.IntValue eliteXp;
        public static ForgeConfigSpec.IntValue eliteAttunement;
        public static ForgeConfigSpec.DoubleValue fairKillShare;
        public static ForgeConfigSpec.DoubleValue weakness;
        public static ForgeConfigSpec.DoubleValue resistance;
        private static final Map<String, ForgeConfigSpec.IntValue> FREE_KILLS = new HashMap<>();

        @Nullable
        private static volatile Double testEliteChance;
        @Nullable
        private static volatile Double testFairKillShare;
        private static final Map<String, Integer> TEST_FREE_KILLS = new ConcurrentHashMap<>();

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("New monsters and bosses (batch 13). Each monster has its own subsection below.",
                    "第十三批的新怪物与首领；每种怪物的设置在下面各自的子小节里").push(CONFIG_SECTION);
            eliteChance = builder.comment("Chance that a newly spawned monster of this mod is its elite variant (overworld, Nether, End and other dimensions). "
                                    + "Only natural, event, structure and nest spawns roll; spawners and spawn eggs never do. 0 = no elites.",
                            "本模组新怪物生成时是精英的几率（主世界、下界、末地和其它维度）。只有自然、事件、结构、巢穴生成的才掷，刷怪笼和刷怪蛋不出精英。0 = 没有精英")
                    .defineInRange("elite_chance", DEFAULT_ELITE_CHANCE, 0D, 1D);
            eliteChanceHoanna = builder.comment("Elite chance in Hoanna.", "海纳尔里的精英几率")
                    .defineInRange("elite_chance_hoanna", DEFAULT_ELITE_CHANCE_HOANNA, 0D, 1D);
            eliteRadius = builder.comment("Radius (blocks) checked for elites of the same kind when a new one would spawn.",
                            "要出精英时，在这个半径（格）以内数同种的精英")
                    .defineInRange("elite_radius", DEFAULT_ELITE_RADIUS, 1, 256);
            eliteMaxNearby = builder.comment("At most this many elites of one kind within elite_radius.", "elite_radius 以内同种精英最多几只")
                    .defineInRange("elite_max_nearby", DEFAULT_ELITE_MAX_NEARBY, 0, 64);
            eliteHealth = builder.comment("Elite max health, as a multiple of the base value.", "精英的最大生命是基础值的几倍")
                    .defineInRange("elite_health_multiplier", DEFAULT_ELITE_HEALTH, 1D, 20D);
            eliteAttack = builder.comment("Elite attack damage, as a multiple of the base value.", "精英的攻击伤害是基础值的几倍")
                    .defineInRange("elite_attack_multiplier", DEFAULT_ELITE_ATTACK, 1D, 20D);
            eliteArmor = builder.comment("Extra armor for elites.", "精英多加的护甲")
                    .defineInRange("elite_armor_bonus", DEFAULT_ELITE_ARMOR, 0D, 30D);
            eliteKnockback = builder.comment("Extra knockback resistance for elites.", "精英多加的击退抗性")
                    .defineInRange("elite_knockback_resistance", DEFAULT_ELITE_KNOCKBACK, 0D, 1D);
            eliteXp = builder.comment("Elites drop this many times the experience.", "精英掉的经验是几倍")
                    .defineInRange("elite_xp_multiplier", DEFAULT_ELITE_XP, 1, 100);
            eliteAttunement = builder.comment("Elites give this many times the attunement points (the per-creature cap grows with it).",
                            "精英给的同调点是几倍（每只的上限跟着乘）")
                    .defineInRange("elite_attunement_multiplier", DEFAULT_ELITE_ATTUNEMENT, 1, 100);
            fairKillShare = builder.comment("Fair kill: at least this share of the damage a monster took must come from players and their pets. 0 = any share.",
                            "公平击杀：怪受的伤害里至少要有这么大比例是玩家和玩家的宠物打的。0 = 不看占比")
                    .defineInRange("fair_kill_min_share", DEFAULT_FAIR_KILL_SHARE, 0D, 1D);
            weakness = builder.comment("Damage multiplier for the weaknesses in the tags essentialcraft5:weak_to_fire / weak_to_freezing / weak_to_lightning / weak_to_melee.",
                            "元素弱点标签（weak_to_fire / weak_to_freezing / weak_to_lightning / weak_to_melee）的伤害倍率")
                    .defineInRange("weakness_multiplier", DEFAULT_WEAKNESS, 1D, 10D);
            resistance = builder.comment("Damage multiplier for projectiles against monsters in the tag essentialcraft5:resists_projectile.",
                            "弹射物打 resists_projectile 标签里的怪时的伤害倍率")
                    .defineInRange("resistance_multiplier", DEFAULT_RESISTANCE, 0D, 1D);

            builder.comment("Diminishing drops per player. In each group a player gets the full drop chance for the first F kills per hour of online time.",
                            "Past F, each kill's chance is scaled by F / (kills in the last hour), so drops per hour stay at chance x F. 0 = no diminishing for that group.",
                            "按人递减：每组每名玩家每在线一小时前 F 只按原几率掉；超过以后每只的几率乘「F / 最近一小时杀的只数」，",
                            "每小时期望掉落停在「几率 × F」。0 = 这一组不递减")
                    .push("kill_fatigue");
            FREE_KILLS.clear();
            for (Map.Entry<String, Integer> entry : DEFAULT_FREE_KILLS.entrySet()) {
                FREE_KILLS.put(entry.getKey(), builder.defineInRange(entry.getKey(), entry.getValue().intValue(), 0, 100_000));
            }
            builder.pop();
            builder.pop();
        }

        /** 按人递减认得的组名（{@code kill_fatigue} 条件的 {@code group} 只能是这几个） */
        public static Set<String> fatigueGroups() {
            return DEFAULT_FREE_KILLS.keySet();
        }

        /** 某组的 F（0 = 这组不递减）；组名不认识返回 -1（条件读 JSON 时就拦下了，不该走到这里） */
        public static int freeKills(String group) {
            Integer test = TEST_FREE_KILLS.get(group);
            if (test != null) {
                return test;
            }
            ForgeConfigSpec.IntValue value = FREE_KILLS.get(group);
            if (value != null) {
                return ECConfig.get(value);
            }
            Integer fallback = DEFAULT_FREE_KILLS.get(group);
            return fallback != null ? fallback : -1;
        }

        /** 精英几率（海纳尔另有一项；测试覆盖优先，覆盖时不分维度） */
        public static double eliteChance(boolean hoanna) {
            Double test = testEliteChance;
            if (test != null) {
                return test;
            }
            if (hoanna) {
                return eliteChanceHoanna == null ? DEFAULT_ELITE_CHANCE_HOANNA : ECConfig.get(eliteChanceHoanna);
            }
            return eliteChance == null ? DEFAULT_ELITE_CHANCE : ECConfig.get(eliteChance);
        }

        public static int eliteRadius() {
            return eliteRadius == null ? DEFAULT_ELITE_RADIUS : ECConfig.get(eliteRadius);
        }

        public static int eliteMaxNearby() {
            return eliteMaxNearby == null ? DEFAULT_ELITE_MAX_NEARBY : ECConfig.get(eliteMaxNearby);
        }

        public static double eliteHealth() {
            return eliteHealth == null ? DEFAULT_ELITE_HEALTH : ECConfig.get(eliteHealth);
        }

        public static double eliteAttack() {
            return eliteAttack == null ? DEFAULT_ELITE_ATTACK : ECConfig.get(eliteAttack);
        }

        public static double eliteArmor() {
            return eliteArmor == null ? DEFAULT_ELITE_ARMOR : ECConfig.get(eliteArmor);
        }

        public static double eliteKnockback() {
            return eliteKnockback == null ? DEFAULT_ELITE_KNOCKBACK : ECConfig.get(eliteKnockback);
        }

        public static int eliteXp() {
            return eliteXp == null ? DEFAULT_ELITE_XP : ECConfig.get(eliteXp);
        }

        public static int eliteAttunementMultiplier() {
            return eliteAttunement == null ? DEFAULT_ELITE_ATTUNEMENT : ECConfig.get(eliteAttunement);
        }

        public static double fairKillShare() {
            Double test = testFairKillShare;
            if (test != null) {
                return test;
            }
            return fairKillShare == null ? DEFAULT_FAIR_KILL_SHARE : ECConfig.get(fairKillShare);
        }

        public static double weakness() {
            return weakness == null ? DEFAULT_WEAKNESS : ECConfig.get(weakness);
        }

        public static double resistance() {
            return resistance == null ? DEFAULT_RESISTANCE : ECConfig.get(resistance);
        }

        /**
         * GameTest：精英几率钉成这个数（null 回到配置）。不用 {@code ConfigValue.set}（服务端配置会存盘、文件监视器随后重读，可能冲掉刚设的值）。
         * 同一段同步代码里设、测、置回。
         */
        public static void setTestEliteChance(@Nullable Double chance) {
            testEliteChance = chance;
        }

        /** GameTest：公平击杀的伤害占比门槛（null 回到配置） */
        public static void setTestFairKillShare(@Nullable Double share) {
            testFairKillShare = share;
        }

        /** GameTest：某组的 F（null 回到配置） */
        public static void setTestFreeKills(String group, @Nullable Integer free) {
            if (free == null) {
                TEST_FREE_KILLS.remove(group);
            } else {
                TEST_FREE_KILLS.put(group, free);
            }
        }
    }
}
