package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 第十三批 13c-M2「聚能魔像」的注册类（batch13.md §5.3.1、§9.3）。
 * <ul>
 *   <li>实体 {@code mru_golem}（{@link MRUGolemEntity}），刷怪蛋 {@code mru_golem_spawn_egg}（只放创造栏，点刷怪笼不改类型）；
 *       {@code #forge:bosses}、{@code divider_immune}、{@code mru_coil_immune} 三个标签里有它；</li>
 *   <li>音效 {@code sound.mob.mru_golem.*}，定义在 tools/sounds/ecmrugolem.json（都指原版声音、改音高 / 音量）；</li>
 *   <li>服务端配置 {@code general.mobs.mru_golem}（{@link Config}）：触发、属性、护盾、招式、地脉探测仪「聚集体」档。</li>
 * </ul>
 * 触发在 {@code entity/mob/MRUGolemTrigger}，渲染器和模型层在 {@code client/MRUGolemClient}，战利品表 {@code entities/mru_golem}。
 */
public final class ECMRUGolem {

    /** 魔像：1.4 × 2.6 格（同铁傀儡；站在格子正中时要 3 × 3 × 3 的空地），追踪 10 区块 */
    public static final RegistryObject<EntityType<MRUGolemEntity>> MRU_GOLEM = ECEntities.register("mru_golem",
            EntityType.Builder.of(MRUGolemEntity::new, MobCategory.MONSTER).sized(1.4F, 2.6F).clientTrackingRange(10));
    public static final RegistryObject<ECEntities.SpawnEgg> MRU_GOLEM_SPAWN_EGG =
            ECEntities.spawnEgg("mru_golem_spawn_egg", MRU_GOLEM, 0x3B4654, 0x5FE3F0);

    /** 遗迹里第一次醒来（信标启动声，压低） */
    public static final RegistryObject<SoundEvent> SOUND_AWAKEN = ECSounds.register("sound.mob.mru_golem.awaken");
    public static final RegistryObject<SoundEvent> SOUND_HURT = ECSounds.register("sound.mob.mru_golem.hurt");
    public static final RegistryObject<SoundEvent> SOUND_DEATH = ECSounds.register("sound.mob.mru_golem.death");
    public static final RegistryObject<SoundEvent> SOUND_STEP = ECSounds.register("sound.mob.mru_golem.step");
    /** 重锤砸中 */
    public static final RegistryObject<SoundEvent> SOUND_HAMMER = ECSounds.register("sound.mob.mru_golem.hammer");
    /** 聚能冲击开始蓄力（蓝圈出现） */
    public static final RegistryObject<SoundEvent> SOUND_SLAM_CHARGE = ECSounds.register("sound.mob.mru_golem.slam_charge");
    /** 聚能冲击落地 */
    public static final RegistryObject<SoundEvent> SOUND_SLAM = ECSounds.register("sound.mob.mru_golem.slam");
    /** 电弧鞭 */
    public static final RegistryObject<SoundEvent> SOUND_WHIP = ECSounds.register("sound.mob.mru_golem.whip");
    /** 聚能护盾抵掉一下 */
    public static final RegistryObject<SoundEvent> SOUND_SHIELD = ECSounds.register("sound.mob.mru_golem.shield");

    private ECMRUGolem() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；属性、配置在这里登记 */
    public static void init() {
        ECEntities.attributes(MRU_GOLEM, MRUGolemEntity::createAttributes);
        ECConfig.general(Config::define);
    }

    /** 服务端配置 {@code general.mobs.mru_golem}（默认值照 batch13.md §5.3.1） */
    public static final class Config {

        /**
         * 遗迹触发。
         *
         * @param radius          生存 / 冒险玩家进聚集体多少格内触发（0 = 不再触发，已经出来的魔像照常）
         * @param hoannaChance    海纳尔里新生成的上古遗迹有守护的几率（主世界的一律有）
         * @param searchHeight    从聚集体往上找露天地面最多找几格
         * @param retryTicks      生成没成功（站不下、被取消）时隔多久再试
         * @param abandonDistance 聚集体被挪离原位超过这么远，这只魔像就不会再出来
         */
        public record Trigger(int radius, double hoannaChance, int searchHeight, int retryTicks, int abandonDistance) {
        }

        /**
         * 属性。
         *
         * @param leashRange 离醒来时聚集体所在的那一格（水平）超过这么远就往回走
         */
        public record Stats(double maxHealth, double armor, double toughness, double knockbackResistance, double perHitCap,
                            int xp, int leashRange) {
        }

        /**
         * 聚能护盾。
         *
         * @param range        魔像离聚集体（水平）多远以内护盾有效
         * @param minMru       聚集体至少还剩多少 MRU 护盾才有效
         * @param mruPerDamage 每抵掉 1 点伤害扣聚集体多少 MRU（0 = 没有护盾）
         */
        public record Shield(int range, int minMru, int mruPerDamage) {
        }

        /**
         * 招式。
         *
         * @param hammerDamage       重锤（近战）
         * @param slamDamage         聚能冲击的固定部分
         * @param slamHealthFraction 聚能冲击另加目标最大生命的这个比例
         * @param slamRadius         聚能冲击半径
         * @param slamWarningTicks   聚能冲击蓝圈预警多久
         * @param slamCooldownTicks  聚能冲击冷却
         * @param whipDamage         电弧鞭
         * @param whipMinRange       电弧鞭最近打多远的目标（更近的用重锤）；比最远距离大时按最远距离算
         * @param whipMaxRange       电弧鞭最远打多远
         * @param whipCooldownTicks  电弧鞭冷却
         */
        public record Attacks(double hammerDamage, double slamDamage, double slamHealthFraction, double slamRadius, int slamWarningTicks,
                              int slamCooldownTicks, double whipDamage, double whipMinRange, double whipMaxRange, int whipCooldownTicks) {

            public Attacks {
                // 两项是分开配的，最近距离填得比最远还大时电弧鞭永远用不出来
                whipMinRange = Math.min(whipMinRange, whipMaxRange);
            }
        }

        /**
         * 地脉探测仪的「聚集体」档。
         *
         * @param radius 找多远以内的
         * @param minMru 只指 MRU 不少于这么多的
         */
        public record Detector(int radius, int minMru) {
        }

        public record Values(Trigger trigger, Stats stats, Shield shield, Attacks attacks, Detector detector) {
        }

        public static final Values DEFAULTS = new Values(
                new Trigger(8, 0.2D, 48, 40, 16),
                new Stats(120D, 10D, 2D, 0.8D, 25D, 40, 16),
                new Shield(16, 500, 40),
                new Attacks(9D, 6D, 0.10D, 3.5D, 20, 160, 6D, 4D, 10D, 80),
                new Detector(64, 5000));

        private static ForgeConfigSpec.IntValue radius;
        private static ForgeConfigSpec.DoubleValue hoannaChance;
        private static ForgeConfigSpec.IntValue searchHeight;
        private static ForgeConfigSpec.IntValue retryTicks;
        private static ForgeConfigSpec.IntValue abandonDistance;
        private static ForgeConfigSpec.DoubleValue maxHealth;
        private static ForgeConfigSpec.DoubleValue armor;
        private static ForgeConfigSpec.DoubleValue toughness;
        private static ForgeConfigSpec.DoubleValue knockbackResistance;
        private static ForgeConfigSpec.DoubleValue perHitCap;
        private static ForgeConfigSpec.IntValue xp;
        private static ForgeConfigSpec.IntValue leashRange;
        private static ForgeConfigSpec.IntValue shieldRange;
        private static ForgeConfigSpec.IntValue shieldMinMru;
        private static ForgeConfigSpec.IntValue shieldMruPerDamage;
        private static ForgeConfigSpec.DoubleValue hammerDamage;
        private static ForgeConfigSpec.DoubleValue slamDamage;
        private static ForgeConfigSpec.DoubleValue slamHealthFraction;
        private static ForgeConfigSpec.DoubleValue slamRadius;
        private static ForgeConfigSpec.IntValue slamWarningTicks;
        private static ForgeConfigSpec.IntValue slamCooldownTicks;
        private static ForgeConfigSpec.DoubleValue whipDamage;
        private static ForgeConfigSpec.DoubleValue whipMinRange;
        private static ForgeConfigSpec.DoubleValue whipMaxRange;
        private static ForgeConfigSpec.IntValue whipCooldownTicks;
        private static ForgeConfigSpec.IntValue detectorRadius;
        private static ForgeConfigSpec.IntValue detectorMinMru;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("MRU Golem: the optional mini-boss guarding newly generated Ancient Ruins.",
                    "聚能魔像：守着新生成的上古遗迹的可选小 Boss").push(List.of(ECMobFramework.CONFIG_SECTION, "mru_golem"));
            Trigger t = DEFAULTS.trigger();
            radius = builder.comment("A survival or adventure player this close to a guarded ruin's MRUCU wakes the golem (0 = never).",
                            "Golems already out are not affected.",
                            "With 0 the Ancient Core has no source, so the MHS Armor 1->2, Void Armor 3->4, Computer Guy's Suit 3->4 and Verdict Blade 3->4",
                            "attunement stages can't be made; replace the Ancient Core in those four recipes with a data pack.",
                            "生存 / 冒险玩家进有守护的遗迹聚集体这么多格内，魔像就出来（0 = 不再出来，已经出来的不受影响）",
                            "填 0 时上古晶核没有来源，魔力防护套装 1→2、虚空套装 3→4、宅男套装 3→4、裁决之刃 3→4 四条同调做不了，要用数据包换掉这四条配方里的上古晶核")
                    .defineInRange("trigger_radius", t.radius(), 0, 32);
            hoannaChance = builder.comment("Chance that a newly generated Ancient Ruin in Hoanna gets a guardian. Overworld ruins always do.",
                            "With 0, ruins in Hoanna no longer bring out a golem. Only ruins generated after this version have guardians at all.",
                            "海纳尔里新生成的上古遗迹有守护的几率；主世界的一律有",
                            "填 0 时海纳尔的遗迹不再出魔像。只有这一版以后新生成的遗迹才有守护，已经生成过的区块不补")
                    .defineInRange("hoanna_chance", t.hoannaChance(), 0D, 1D);
            searchHeight = builder.comment("How far above the MRUCU to look for open-sky ground to stand the golem on.",
                            "从聚集体往上找头顶露天、脚下实心的地面最多找几格；找不到就在聚集体旁边站")
                    .defineInRange("spawn_search_height", t.searchHeight(), 0, 128);
            retryTicks = builder.comment("Ticks before trying again when the golem couldn't appear (no room, or another mod or plugin stopped it).",
                            "魔像没能出来（站不下、被别的模组或插件拦下）时隔多少刻再试")
                    .defineInRange("retry_ticks", t.retryTicks(), 1, 1200);
            abandonDistance = builder.comment("Moving the MRUCU this far from where the ruin put it, before the golem appears, means it never will.",
                            "魔像出来以前把聚集体挪离原位超过这么多格，这只魔像就不会再出来")
                    .defineInRange("abandon_distance", t.abandonDistance(), 1, 256);
            Stats s = DEFAULTS.stats();
            maxHealth = builder.comment("Max health of golems that appear from now on.", "之后出来的魔像的最大生命")
                    .defineInRange("max_health", s.maxHealth(), 1D, 4096D);
            armor = builder.comment("Armor.", "护甲").defineInRange("armor", s.armor(), 0D, 30D);
            toughness = builder.comment("Armor toughness.", "盔甲韧性").defineInRange("armor_toughness", s.toughness(), 0D, 20D);
            knockbackResistance = builder.comment("Knockback resistance (1 = can't be knocked back).", "击退抗性（1 = 打不退）")
                    .defineInRange("knockback_resistance", s.knockbackResistance(), 0D, 1D);
            perHitCap = builder.comment("No single hit takes more than this from the golem (0 = no cap).", "单次伤害最多扣这么多（0 = 不封顶）")
                    .defineInRange("per_hit_cap", s.perHitCap(), 0D, 1024D);
            xp = builder.comment("Experience dropped.", "掉的经验").defineInRange("xp", s.xp(), 0, 1000);
            leashRange = builder.comment("The golem walks back once it is this far (horizontally) from where its MRUCU was when it woke up.",
                            "魔像离醒来时聚集体所在的那一格（水平）超过这么多格就往回走")
                    .defineInRange("leash_range", s.leashRange(), 4, 64);
            Shield sh = DEFAULTS.shield();
            shieldRange = builder.comment("Concentration Shield works while the golem is within this many blocks (horizontally) of its MRUCU.",
                            "聚能护盾：魔像离聚集体（水平）这么多格以内才有效")
                    .defineInRange("shield_range", sh.range(), 0, 64);
            shieldMinMru = builder.comment("Concentration Shield works only while the MRUCU holds at least this much MRU.",
                            "聚能护盾：聚集体至少还剩这么多 MRU 才有效")
                    .defineInRange("shield_min_mru", sh.minMru(), 0, 20000);
            shieldMruPerDamage = builder.comment("MRU drained from the MRUCU for each point of damage the shield soaks (0 = no shield).",
                            "聚能护盾每抵掉 1 点伤害从聚集体扣多少 MRU（0 = 没有护盾）")
                    .defineInRange("shield_mru_per_damage", sh.mruPerDamage(), 0, 20000);
            Attacks a = DEFAULTS.attacks();
            hammerDamage = builder.comment("Hammer (melee) damage.", "重锤（近战）伤害").defineInRange("hammer_damage", a.hammerDamage(), 0D, 1000D);
            slamDamage = builder.comment("Concentration Slam: flat damage.", "聚能冲击：固定伤害").defineInRange("slam_damage", a.slamDamage(), 0D, 1000D);
            slamHealthFraction = builder.comment("Concentration Slam: extra damage as a share of the target's max health.", "聚能冲击：另加目标最大生命的这个比例")
                    .defineInRange("slam_health_fraction", a.slamHealthFraction(), 0D, 1D);
            slamRadius = builder.comment("Concentration Slam radius.", "聚能冲击半径").defineInRange("slam_radius", a.slamRadius(), 1D, 16D);
            slamWarningTicks = builder.comment("Concentration Slam: ticks the blue ring shows before it lands.", "聚能冲击：蓝圈预警多少刻")
                    .defineInRange("slam_warning_ticks", a.slamWarningTicks(), 5, 100);
            slamCooldownTicks = builder.comment("Concentration Slam cooldown in ticks.", "聚能冲击冷却（刻）")
                    .defineInRange("slam_cooldown_ticks", a.slamCooldownTicks(), 20, 2400);
            whipDamage = builder.comment("Arc Whip damage (MRU discharge with the golem as attacker).", "电弧鞭伤害（MRU 放电，攻击者是魔像）")
                    .defineInRange("whip_damage", a.whipDamage(), 0D, 1000D);
            whipMinRange = builder.comment("Arc Whip: closest target distance (closer ones get the hammer). Above whip_max_range it counts as whip_max_range.",
                            "电弧鞭：最近打多远的目标（更近的吃重锤）；填得比 whip_max_range 大时按 whip_max_range 算")
                    .defineInRange("whip_min_range", a.whipMinRange(), 0D, 32D);
            whipMaxRange = builder.comment("Arc Whip: farthest target distance.", "电弧鞭：最远打多远")
                    .defineInRange("whip_max_range", a.whipMaxRange(), 1D, 32D);
            whipCooldownTicks = builder.comment("Arc Whip cooldown in ticks.", "电弧鞭冷却（刻）")
                    .defineInRange("whip_cooldown_ticks", a.whipCooldownTicks(), 10, 2400);
            Detector d = DEFAULTS.detector();
            detectorRadius = builder.comment("Ley Detector, MRUCU mode: how far to look (loaded chunks only).",
                            "地脉探测仪「聚集体」档：找多远以内的（只查已加载的区块）")
                    .defineInRange("detector_radius", d.radius(), 8, 128);
            detectorMinMru = builder.comment("Ley Detector, MRUCU mode: only point at MRUCUs holding at least this much MRU.",
                            "地脉探测仪「聚集体」档：只指 MRU 不少于这么多的聚集体")
                    .defineInRange("detector_min_mru", d.minMru(), 0, 20000);
            builder.pop(2);
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (radius == null) {
                return DEFAULTS;
            }
            return new Values(
                    new Trigger(ECConfig.get(radius), ECConfig.get(hoannaChance), ECConfig.get(searchHeight), ECConfig.get(retryTicks),
                            ECConfig.get(abandonDistance)),
                    new Stats(ECConfig.get(maxHealth), ECConfig.get(armor), ECConfig.get(toughness), ECConfig.get(knockbackResistance),
                            ECConfig.get(perHitCap), ECConfig.get(xp), ECConfig.get(leashRange)),
                    new Shield(ECConfig.get(shieldRange), ECConfig.get(shieldMinMru), ECConfig.get(shieldMruPerDamage)),
                    new Attacks(ECConfig.get(hammerDamage), ECConfig.get(slamDamage), ECConfig.get(slamHealthFraction), ECConfig.get(slamRadius),
                            ECConfig.get(slamWarningTicks), ECConfig.get(slamCooldownTicks), ECConfig.get(whipDamage), ECConfig.get(whipMinRange),
                            ECConfig.get(whipMaxRange), ECConfig.get(whipCooldownTicks)),
                    new Detector(ECConfig.get(detectorRadius), ECConfig.get(detectorMinMru)));
        }

        /** 只给 GameTest：在同一段同步代码里设、测、置回 null（别用 ConfigValue.set，文件监视器会冲掉） */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
