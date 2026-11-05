package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.boss.SentryTurretEntity;
import com.flameforge.essentialcraft5.entity.boss.TownSentryEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 第十三批 13e-T「废城守望者」的注册类（batch13.md §5.3.3、§9.5；MS §2.5.2）。
 * <ul>
 *   <li>小 Boss {@link #TOWN_SENTRY} 废城守望者（{@code entity/boss/TownSentryEntity}，小 Boss 档）与刷怪蛋（只放创造栏，点刷怪笼不改类型）；
 *       在 {@code #forge:bosses}、{@code divider_immune}、{@code mru_coil_immune} 里；</li>
 *   <li>技能实体 {@link #SENTRY_TURRET} 哨戒炮台（{@code entity/boss/SentryTurretEntity}）：在 {@code growth_excluded} 里，不存盘、不掉东西、不给经验；
 *       类别是 MONSTER（只由守望者放出来、同时最多 {@code turret_count} 座，占的刷怪上限可以不计）；</li>
 *   <li>城镇触发 {@code entity/boss/TownSentryTrigger}（存档 {@code essentialcraft5_town_guardians}，{@code entity/boss/TownGuardians}）；
 *       战利品表 {@code entities/town_sentry}；音效 {@code sound.mob.town_sentry.*} / {@code sound.mob.sentry_turret.*}，定义在
 *       tools/sounds/ectownsentry.json（都指原版声音、改音高 / 音量）。</li>
 * </ul>
 * 文字在 {@code tools/lang/keys/ectownsentry.json}，客户端 {@code client/TownSentryClient}。服务端配置 {@code general.town_sentry}（{@link Config}）。
 * 同一批顺带补了全息投影 / 审判庭哨兵拉回的两处口子（{@code HologramEntity}：按被拉的人探测落点权限、拉不回来满 {@code abandon_ticks} 移出）。
 */
public final class ECTownSentry {

    /** 废城守望者：聚能魔像（1.4 × 2.6）× 1.25 */
    public static final RegistryObject<EntityType<TownSentryEntity>> TOWN_SENTRY = ECEntities.register("town_sentry",
            EntityType.Builder.<TownSentryEntity>of(TownSentryEntity::new, MobCategory.MONSTER)
                    .sized(1.75F, 3.25F).fireImmune().clientTrackingRange(10).updateInterval(2));

    /** 哨戒炮台：不动的小炮塔 */
    public static final RegistryObject<EntityType<SentryTurretEntity>> SENTRY_TURRET = ECEntities.register("sentry_turret",
            EntityType.Builder.<SentryTurretEntity>of(SentryTurretEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.2F).fireImmune().noSummon().clientTrackingRange(8).updateInterval(4));

    public static final RegistryObject<ECEntities.SpawnEgg> TOWN_SENTRY_SPAWN_EGG =
            ECEntities.spawnEgg("town_sentry_spawn_egg", TOWN_SENTRY, 0x4A4F57, 0xE0B040);

    /** 城里第一次有人走近，守望者醒来 */
    public static final RegistryObject<SoundEvent> SOUND_AWAKEN = ECSounds.register("sound.mob.town_sentry.awaken");
    public static final RegistryObject<SoundEvent> SOUND_HURT = ECSounds.register("sound.mob.town_sentry.hurt");
    public static final RegistryObject<SoundEvent> SOUND_DEATH = ECSounds.register("sound.mob.town_sentry.death");
    public static final RegistryObject<SoundEvent> SOUND_STEP = ECSounds.register("sound.mob.town_sentry.step");
    /** 横扫 */
    public static final RegistryObject<SoundEvent> SOUND_SWEEP = ECSounds.register("sound.mob.town_sentry.sweep");
    /** 镇压开始蓄力（红圈出现） */
    public static final RegistryObject<SoundEvent> SOUND_SUPPRESS_CHARGE = ECSounds.register("sound.mob.town_sentry.suppress_charge");
    /** 镇压落地 */
    public static final RegistryObject<SoundEvent> SOUND_SUPPRESS = ECSounds.register("sound.mob.town_sentry.suppress");
    /** 全城警报（钟，连敲三下） */
    public static final RegistryObject<SoundEvent> SOUND_ALARM = ECSounds.register("sound.mob.town_sentry.alarm");
    /** 正面挡下一发弹射物 */
    public static final RegistryObject<SoundEvent> SOUND_DEFLECT = ECSounds.register("sound.mob.town_sentry.deflect");
    /** 炮台架起来 */
    public static final RegistryObject<SoundEvent> SOUND_TURRET_DEPLOY = ECSounds.register("sound.mob.sentry_turret.deploy");
    /** 炮台开火（发射器声，和遗民步枪的枪声一起放） */
    public static final RegistryObject<SoundEvent> SOUND_TURRET_FIRE = ECSounds.register("sound.mob.sentry_turret.fire");
    public static final RegistryObject<SoundEvent> SOUND_TURRET_HURT = ECSounds.register("sound.mob.sentry_turret.hurt");
    public static final RegistryObject<SoundEvent> SOUND_TURRET_DEATH = ECSounds.register("sound.mob.sentry_turret.death");

    private ECTownSentry() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；属性、配置在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
        ECEntities.attributes(TOWN_SENTRY, TownSentryEntity::createAttributes);
        ECEntities.attributes(SENTRY_TURRET, SentryTurretEntity::createAttributes);
    }

    /** 服务端配置 {@code general.town_sentry}（默认值照 batch13.md §5.3.3、MS §2.5.2） */
    public static final class Config {

        /**
         * 城镇触发。
         *
         * @param radius     生存 / 冒险玩家离城镇起点多少格以内（水平）守望者醒来（0 = 不再醒，已经醒了的照常）
         * @param retryTicks 没能出来（站不下、被取消）时隔多久再试
         */
        public record Trigger(int radius, int retryTicks) {
        }

        /**
         * 属性。
         *
         * @param leashRange 离醒来的位置（水平）超过这么远就往回走
         */
        public record Stats(double maxHealth, double armor, double toughness, double knockbackResistance, double perHitCap, int xp,
                            int leashRange) {
        }

        /**
         * 招式。
         *
         * @param sweepDamage            横扫（近战，吃护甲）
         * @param sweepAngle             横扫的扇形张角（度，以朝向为中线）
         * @param sweepRange             横扫够多远
         * @param suppressDamage         镇压的固定部分
         * @param suppressHealthFraction 镇压另加目标最大生命的这个比例
         * @param suppressRadius         镇压半径
         * @param suppressWarningTicks   镇压红圈预警多久
         * @param suppressCooldownTicks  镇压冷却
         */
        public record Attacks(double sweepDamage, double sweepAngle, double sweepRange, double suppressDamage, double suppressHealthFraction,
                              double suppressRadius, int suppressWarningTicks, int suppressCooldownTicks) {
        }

        /**
         * 哨戒炮台。
         *
         * @param count         同时最多几座（0 = 不放炮台，正面也不再挡弹射物）
         * @param health        每座的生命
         * @param damage        每发的伤害（{@code mob_bolt}）
         * @param intervalTicks 多久打一发
         * @param range         打多远
         * @param respawnTicks  被打掉以后多久补一座
         * @param guardAngle    炮台还在时守望者正面挡弹射物的张角（度，以朝向为中线；0 = 不挡）
         */
        public record Turrets(int count, double health, double damage, int intervalTicks, double range, int respawnTicks, double guardAngle) {
        }

        /**
         * 全城警报。
         *
         * @param healthFraction 第一次降到最大生命的这个比例以下时拉响（只一次）
         * @param count          叫来几名遗民拾荒者（0 = 不叫）
         */
        public record Alarm(double healthFraction, int count) {
        }

        public record Values(Trigger trigger, Stats stats, Attacks attacks, Turrets turrets, Alarm alarm) {
        }

        public static final Values DEFAULTS = new Values(
                new Trigger(32, 40),
                new Stats(260D, 14D, 4D, 1D, 30D, 60, 24),
                new Attacks(13D, 120D, 3D, 10D, 0.12D, 4D, 30, 280),
                new Turrets(2, 30D, 6D, 30, 16D, 400, 120D),
                new Alarm(0.5D, 2));

        private static ForgeConfigSpec.IntValue radius;
        private static ForgeConfigSpec.IntValue retryTicks;
        private static ForgeConfigSpec.DoubleValue maxHealth;
        private static ForgeConfigSpec.DoubleValue armor;
        private static ForgeConfigSpec.DoubleValue toughness;
        private static ForgeConfigSpec.DoubleValue knockbackResistance;
        private static ForgeConfigSpec.DoubleValue perHitCap;
        private static ForgeConfigSpec.IntValue xp;
        private static ForgeConfigSpec.IntValue leashRange;
        private static ForgeConfigSpec.DoubleValue sweepDamage;
        private static ForgeConfigSpec.DoubleValue sweepAngle;
        private static ForgeConfigSpec.DoubleValue sweepRange;
        private static ForgeConfigSpec.DoubleValue suppressDamage;
        private static ForgeConfigSpec.DoubleValue suppressHealthFraction;
        private static ForgeConfigSpec.DoubleValue suppressRadius;
        private static ForgeConfigSpec.IntValue suppressWarningTicks;
        private static ForgeConfigSpec.IntValue suppressCooldownTicks;
        private static ForgeConfigSpec.IntValue turretCount;
        private static ForgeConfigSpec.DoubleValue turretHealth;
        private static ForgeConfigSpec.DoubleValue turretDamage;
        private static ForgeConfigSpec.IntValue turretInterval;
        private static ForgeConfigSpec.DoubleValue turretRange;
        private static ForgeConfigSpec.IntValue turretRespawn;
        private static ForgeConfigSpec.DoubleValue guardAngle;
        private static ForgeConfigSpec.DoubleValue alarmHealthFraction;
        private static ForgeConfigSpec.IntValue alarmCount;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Ruin Watcher: the mini-boss guarding Hoanna's ruined towns.",
                    "废城守望者：守着海纳尔城镇废墟的小 Boss").push("town_sentry");
            Trigger t = DEFAULTS.trigger();
            radius = builder.comment("A survival or adventure player this close (horizontally) to a town's starting crossroads wakes its Ruin Watcher,",
                            "if they are allowed to build on that crossroads. Each town has one Watcher; towns generated before this version get one too",
                            "and it wakes the first time someone comes near. 0 = no Watcher wakes any more (ones already awake are not affected).",
                            "With 0 the Ruin Watcher's Ancient Core and Aberrant Heart are gone from the game.",
                            "生存 / 冒险玩家离城镇起点（那个十字路口）这么多格以内（水平），而且他能动起点那一格，城里的废城守望者就醒来。",
                            "每座城镇一只；这一版以前生成的老城镇也有，第一次有人走近时醒。0 = 不再醒（已经醒了的不受影响）。",
                            "填 0 时拿不到守望者掉的上古晶核和异变之心")
                    .defineInRange("trigger_radius", t.radius(), 0, 64);
            retryTicks = builder.comment("Ticks before trying again when the Watcher couldn't appear (no room, or another mod or plugin stopped it).",
                            "守望者没能出来（站不下、被别的模组或插件拦下）时隔多少刻再试")
                    .defineInRange("retry_ticks", t.retryTicks(), 1, 1200);
            Stats s = DEFAULTS.stats();
            maxHealth = builder.comment("Max health of Watchers that wake from now on.", "之后醒来的守望者的最大生命")
                    .defineInRange("max_health", s.maxHealth(), 1D, 4096D);
            armor = builder.comment("Armor.", "护甲").defineInRange("armor", s.armor(), 0D, 30D);
            toughness = builder.comment("Armor toughness.", "盔甲韧性").defineInRange("armor_toughness", s.toughness(), 0D, 20D);
            knockbackResistance = builder.comment("Knockback resistance (1 = can't be knocked back).", "击退抗性（1 = 打不退）")
                    .defineInRange("knockback_resistance", s.knockbackResistance(), 0D, 1D);
            perHitCap = builder.comment("No single hit takes more than this from the Watcher (0 = no cap).", "单次伤害最多扣这么多（0 = 不封顶）")
                    .defineInRange("per_hit_cap", s.perHitCap(), 0D, 1024D);
            xp = builder.comment("Experience dropped.", "掉的经验").defineInRange("xp", s.xp(), 0, 1000);
            leashRange = builder.comment("The Watcher walks back once it is this far (horizontally) from where it woke up.",
                            "守望者离醒来的地方（水平）超过这么多格就往回走")
                    .defineInRange("leash_range", s.leashRange(), 4, 64);
            Attacks a = DEFAULTS.attacks();
            sweepDamage = builder.comment("Sweep (melee) damage; armor applies.", "横扫（近战）伤害，吃护甲")
                    .defineInRange("sweep_damage", a.sweepDamage(), 0D, 1000D);
            sweepAngle = builder.comment("Sweep: width of the arc in front of it, in degrees.", "横扫：正前方扇形的张角（度）")
                    .defineInRange("sweep_angle", a.sweepAngle(), 10D, 360D);
            sweepRange = builder.comment("Sweep: reach in blocks.", "横扫：够多远（格）").defineInRange("sweep_range", a.sweepRange(), 1D, 8D);
            suppressDamage = builder.comment("Suppression: flat damage (ignores armor; a red ring warns before it lands).",
                            "镇压：固定伤害（无视护甲，落地前有红圈预警）")
                    .defineInRange("suppress_damage", a.suppressDamage(), 0D, 1000D);
            suppressHealthFraction = builder.comment("Suppression: extra damage as a share of the target's max health.", "镇压：另加目标最大生命的这个比例")
                    .defineInRange("suppress_health_fraction", a.suppressHealthFraction(), 0D, 1D);
            suppressRadius = builder.comment("Suppression radius.", "镇压半径").defineInRange("suppress_radius", a.suppressRadius(), 1D, 16D);
            suppressWarningTicks = builder.comment("Suppression: ticks the red ring shows before it lands.", "镇压：红圈预警多少刻")
                    .defineInRange("suppress_warning_ticks", a.suppressWarningTicks(), 5, 100);
            suppressCooldownTicks = builder.comment("Suppression cooldown in ticks.", "镇压冷却（刻）")
                    .defineInRange("suppress_cooldown_ticks", a.suppressCooldownTicks(), 20, 2400);
            Turrets tu = DEFAULTS.turrets();
            turretCount = builder.comment("Sentry Turrets the Watcher keeps up at once (0 = none, and its front no longer stops projectiles).",
                            "守望者同时最多架几座哨戒炮台（0 = 不架，正面也不再挡弹射物）")
                    .defineInRange("turret_count", tu.count(), 0, 4);
            turretHealth = builder.comment("Sentry Turret health.", "哨戒炮台的生命").defineInRange("turret_health", tu.health(), 1D, 1024D);
            turretDamage = builder.comment("Sentry Turret damage per shot.", "哨戒炮台每发的伤害").defineInRange("turret_damage", tu.damage(), 0D, 1000D);
            turretInterval = builder.comment("Ticks between Sentry Turret shots.", "哨戒炮台多少刻打一发")
                    .defineInRange("turret_interval_ticks", tu.intervalTicks(), 5, 1200);
            turretRange = builder.comment("Sentry Turret range in blocks.", "哨戒炮台打多远（格）").defineInRange("turret_range", tu.range(), 4D, 48D);
            turretRespawn = builder.comment("Ticks before a destroyed Sentry Turret is replaced.", "哨戒炮台被打掉以后多少刻补一座")
                    .defineInRange("turret_respawn_ticks", tu.respawnTicks(), 20, 12000);
            guardAngle = builder.comment("While a turret stands, projectiles hitting the Watcher's front within this arc (degrees) bounce off (0 = never).",
                            "炮台还在时，守望者正面这么大张角（度）以内打来的弹射物被挡下（0 = 不挡）")
                    .defineInRange("front_guard_angle", tu.guardAngle(), 0D, 360D);
            Alarm al = DEFAULTS.alarm();
            alarmHealthFraction = builder.comment("Town Alarm: the first time the Watcher drops to this share of its max health it calls for help (once).",
                            "全城警报：守望者第一次降到最大生命的这个比例以下时叫人（只一次）")
                    .defineInRange("alarm_health_fraction", al.healthFraction(), 0D, 1D);
            alarmCount = builder.comment("Town Alarm: Hoanna Scavengers it calls (0 = no alarm).", "全城警报：叫来几名遗民拾荒者（0 = 不叫）")
                    .defineInRange("alarm_count", al.count(), 0, 8);
            builder.pop();
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
                    new Trigger(ECConfig.get(radius), ECConfig.get(retryTicks)),
                    new Stats(ECConfig.get(maxHealth), ECConfig.get(armor), ECConfig.get(toughness), ECConfig.get(knockbackResistance),
                            ECConfig.get(perHitCap), ECConfig.get(xp), ECConfig.get(leashRange)),
                    new Attacks(ECConfig.get(sweepDamage), ECConfig.get(sweepAngle), ECConfig.get(sweepRange), ECConfig.get(suppressDamage),
                            ECConfig.get(suppressHealthFraction), ECConfig.get(suppressRadius), ECConfig.get(suppressWarningTicks),
                            ECConfig.get(suppressCooldownTicks)),
                    new Turrets(ECConfig.get(turretCount), ECConfig.get(turretHealth), ECConfig.get(turretDamage), ECConfig.get(turretInterval),
                            ECConfig.get(turretRange), ECConfig.get(turretRespawn), ECConfig.get(guardAngle)),
                    new Alarm(ECConfig.get(alarmHealthFraction), ECConfig.get(alarmCount)));
        }

        /** 只给 GameTest：在同一段同步代码里设、测、置回 null（别用 ConfigValue.set，文件监视器会冲掉） */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
