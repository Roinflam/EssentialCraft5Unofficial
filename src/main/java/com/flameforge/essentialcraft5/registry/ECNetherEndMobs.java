package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.NetherImpEmberBoltEntity;
import com.flameforge.essentialcraft5.entity.mob.NetherImpEntity;
import com.flameforge.essentialcraft5.entity.mob.VoidStalkerEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 第十三批 13c-M3「炼狱小鬼、虚空潜行者」（batch13.md §5.1、§5.2、§9.3）：
 * <ul>
 *   <li>{@code nether_imp} 炼狱小鬼（{@link NetherImpEntity}）：下界荒地权重 5、灵魂沙峡谷权重 2（绯红森林不刷，§5.1 挤占表），
 *       刷怪表是数据 {@code data/essentialcraft5/forge/biome_modifier/nether_imp_*.json}；它吐的余火弹是 {@code imp_ember_bolt}
 *       （{@link NetherImpEmberBoltEntity}）。精英「小鬼头目」三连发、叫来两只小鬼。灵魂能收（{@code soul_capturable}），恶魔交易池有一条要两个它的灵魂。</li>
 *   <li>{@code void_stalker} 虚空潜行者（{@link VoidStalkerEntity}）：不进任何群系刷怪表，由 {@code entity/mob/VoidStalkerSpawner} 照幻翼的写法按玩家刷在末地外岛。
 *       实体化 / 相位交替，裂隙爪把人闪到附近，精英「裂隙行者」把人拉到跟前。</li>
 *   <li>刷怪蛋 {@code nether_imp_spawn_egg}、{@code void_stalker_spawn_egg}（只在创造栏，policy §4.7；普通怪的蛋能改刷怪笼，改出来的来源是刷怪笼，关键掉落拿不到）。</li>
 * </ul>
 * 掉落表 {@code data/essentialcraft5/loot_tables/entities/{nether_imp, void_stalker}.json}（公平击杀 + 按人递减，精英多一池）；
 * 文字 tools/lang/keys/ecnetherendmobs.json；音效 tools/sounds/ecnetherendmobs.json（都指原版声音、改音高）；客户端 client/NetherEndMobsClient；
 * 测试 gametest/NetherEndMobsTests。服务端配置 {@code general.mobs.nether_imp}、{@code general.mobs.void_stalker}（{@link Config}）。
 */
public final class ECNetherEndMobs {

    /** 飞的小怪；碰撞箱比模型矮一点（角伸出去不算），追踪 8 区块（飞得到处是，太近会突然冒出来） */
    public static final RegistryObject<EntityType<NetherImpEntity>> NETHER_IMP = ECEntities.register("nether_imp",
            EntityType.Builder.<NetherImpEntity>of(NetherImpEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.2F).fireImmune().clientTrackingRange(8));

    /** 余火弹：尺寸、追踪、同步间隔同原版小火球（客户端照加速度自己推，10 刻校一次） */
    public static final RegistryObject<EntityType<NetherImpEmberBoltEntity>> IMP_EMBER_BOLT = ECEntities.register("imp_ember_bolt",
            EntityType.Builder.<NetherImpEmberBoltEntity>of(NetherImpEmberBoltEntity::new, MobCategory.MISC)
                    .sized(0.3125F, 0.3125F).clientTrackingRange(4).updateInterval(10));

    /** 高瘦的人形，比末影人矮一截 */
    public static final RegistryObject<EntityType<VoidStalkerEntity>> VOID_STALKER = ECEntities.register("void_stalker",
            EntityType.Builder.<VoidStalkerEntity>of(VoidStalkerEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.3F).clientTrackingRange(8));

    public static final RegistryObject<ECEntities.SpawnEgg> NETHER_IMP_SPAWN_EGG =
            ECEntities.spawnEgg("nether_imp_spawn_egg", NETHER_IMP, 0x5A1A10, 0xF28C28, true);
    public static final RegistryObject<ECEntities.SpawnEgg> VOID_STALKER_SPAWN_EGG =
            ECEntities.spawnEgg("void_stalker_spawn_egg", VOID_STALKER, 0x1B1026, 0xB070E0, true);

    // 音效（定义在 tools/sounds/ecnetherendmobs.json，全部指原版声音改音高 / 音量）
    public static final RegistryObject<SoundEvent> SOUND_IMP_AMBIENT = ECSounds.register("sound.mob.nether_imp.ambient");
    public static final RegistryObject<SoundEvent> SOUND_IMP_HURT = ECSounds.register("sound.mob.nether_imp.hurt");
    public static final RegistryObject<SoundEvent> SOUND_IMP_DEATH = ECSounds.register("sound.mob.nether_imp.death");
    public static final RegistryObject<SoundEvent> SOUND_IMP_SHOOT = ECSounds.register("sound.mob.nether_imp.shoot");
    public static final RegistryObject<SoundEvent> SOUND_IMP_SUMMON = ECSounds.register("sound.mob.nether_imp.summon");
    public static final RegistryObject<SoundEvent> SOUND_IMP_FLEE = ECSounds.register("sound.mob.nether_imp.flee");
    public static final RegistryObject<SoundEvent> SOUND_EMBER_FIZZLE = ECSounds.register("sound.mob.imp_ember_bolt.fizzle");
    public static final RegistryObject<SoundEvent> SOUND_STALKER_AMBIENT = ECSounds.register("sound.mob.void_stalker.ambient");
    public static final RegistryObject<SoundEvent> SOUND_STALKER_HURT = ECSounds.register("sound.mob.void_stalker.hurt");
    public static final RegistryObject<SoundEvent> SOUND_STALKER_DEATH = ECSounds.register("sound.mob.void_stalker.death");
    public static final RegistryObject<SoundEvent> SOUND_STALKER_PHASE_WARN = ECSounds.register("sound.mob.void_stalker.phase_warn");
    public static final RegistryObject<SoundEvent> SOUND_STALKER_PHASE_OUT = ECSounds.register("sound.mob.void_stalker.phase_out");
    public static final RegistryObject<SoundEvent> SOUND_STALKER_PHASE_IN = ECSounds.register("sound.mob.void_stalker.phase_in");
    public static final RegistryObject<SoundEvent> SOUND_STALKER_RIFT_CLAW = ECSounds.register("sound.mob.void_stalker.rift_claw");
    public static final RegistryObject<SoundEvent> SOUND_STALKER_RIFT_OPEN = ECSounds.register("sound.mob.void_stalker.rift_open");
    public static final RegistryObject<SoundEvent> SOUND_STALKER_RIFT_PULL = ECSounds.register("sound.mob.void_stalker.rift_pull");

    /** 小鬼自然生成时，这么多格内同种不到 {@code nether_imp.nearby_cap} 只才刷 */
    public static final int IMP_NEARBY_RADIUS = 32;

    private ECNetherEndMobs() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；属性、刷怪位置、配置在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
        ECEntities.attributes(NETHER_IMP, NetherImpEntity::createAttributes);
        ECEntities.attributes(VOID_STALKER, VoidStalkerEntity::createAttributes);
        // 小鬼：任何亮度都能刷（同烈焰人），附近已有好几只就不刷；潜行者只经独立生成器刷，这里登记的是生成器自己也要过的那一份判定
        ECEntities.spawnPlacement(NETHER_IMP, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MobSpawnRules.capped(NetherImpEntity::checkImpSpawnRules, () -> IMP_NEARBY_RADIUS, () -> Config.imp().nearbyCap()));
        ECEntities.spawnPlacement(VOID_STALKER, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                VoidStalkerEntity::checkStalkerSpawnRules);
    }

    /**
     * 服务端配置 {@code general.mobs.nether_imp} 与 {@code general.mobs.void_stalker}。生命、护甲、攻击在生成时（{@code finalizeSpawn}）按配置设成基础值，
     * 已经在世界里的怪不跟着改；别的数值每次用到时读。读数一律走 {@link #imp()} / {@link #stalker()}（配置没加载时给默认值，GameTest 的覆盖值优先）。
     */
    public static final class Config {

        /**
         * 炼狱小鬼的一套数值。
         *
         * @param maxHealth           最大生命
         * @param armor               护甲
         * @param boltDamage          余火弹伤害（也是它的攻击属性，精英 ×1.4）
         * @param burnSeconds         余火弹打中时让目标烧几秒（0 = 不烧；落地一律不放火）
         * @param shootInterval       两次出手之间隔几刻（上下浮动 10 刻）
         * @param shootRange          多远以内才吐余火弹（格）
         * @param fleeHealth          生命剩到最大生命的这个比例以下就逃（0 = 不逃）
         * @param fleeTicks           一次逃多少刻
         * @param fleeCooldown        逃完以后隔多少刻才会再逃
         * @param eliteBurst          小鬼头目一次连发几颗
         * @param eliteSummonCount    小鬼头目叫来几只（它叫来的、活着的不超过这个数；0 = 不叫）
         * @param eliteSummonCooldown 小鬼头目叫帮手的冷却（刻）
         * @param nearbyCap           自然生成时 32 格内同种最多几只
         */
        public record ImpValues(double maxHealth, double armor, double boltDamage, int burnSeconds, int shootInterval, int shootRange,
                                double fleeHealth, int fleeTicks, int fleeCooldown, int eliteBurst, int eliteSummonCount, int eliteSummonCooldown,
                                int nearbyCap) {
        }

        /** batch13.md §5.2：16 / 2；伤害、节奏照烈焰人小一号定 */
        public static final ImpValues IMP_DEFAULTS = new ImpValues(16D, 2D, 4D, 2, 50, 16, 0.3D, 60, 200, 3, 2, 600, 6);

        /**
         * 虚空潜行者的一套数值（含独立生成器）。
         *
         * @param maxHealth        最大生命
         * @param armor            护甲
         * @param attackDamage     爪击伤害（精英 ×1.4）
         * @param solidTicks       每轮实体化多少刻（含最后的预警）
         * @param warningTicks     相位前的预警多少刻
         * @param phaseTicks       每轮相位多少刻（0 = 不进相位）
         * @param riftClawCooldown 裂隙爪的冷却（刻，0 = 不闪人）
         * @param blinkMin         闪人落点离原处至少几格
         * @param blinkMax         闪人落点离原处至多几格
         * @param pullCooldown     裂隙行者拉人的冷却（刻，0 = 不拉人）
         * @param pullWarning      拉人前的预警多少刻
         * @param pullRange        多远以内能拉（格）
         * @param spawnInterval    生成器每隔多少刻给外岛上的每名玩家掷一次
         * @param spawnChance      每次掷中的几率（0 = 不刷）
         * @param spawnMin         离玩家至少几格才刷
         * @param spawnMax         离玩家至多几格才刷
         * @param spawnMaxNearby   刷的地方 64 格内已有这么多只就不刷
         */
        public record StalkerValues(double maxHealth, double armor, double attackDamage, int solidTicks, int warningTicks, int phaseTicks,
                                    int riftClawCooldown, int blinkMin, int blinkMax, int pullCooldown, int pullWarning, int pullRange,
                                    int spawnInterval, double spawnChance, int spawnMin, int spawnMax, int spawnMaxNearby) {
        }

        /** batch13.md §5.2：45 / 8；实体化 4 秒（含 0.5 秒预警）/ 相位 2 秒；生成器每 600 刻 25%、24~64 格、64 格内 ≤ 3 */
        public static final StalkerValues STALKER_DEFAULTS = new StalkerValues(45D, 8D, 6D, 80, 10, 40, 160, 4, 8, 240, 15, 16,
                600, 0.25D, 24, 64, 3);

        private static ForgeConfigSpec.DoubleValue impHealth;
        private static ForgeConfigSpec.DoubleValue impArmor;
        private static ForgeConfigSpec.DoubleValue impBoltDamage;
        private static ForgeConfigSpec.IntValue impBurnSeconds;
        private static ForgeConfigSpec.IntValue impShootInterval;
        private static ForgeConfigSpec.IntValue impShootRange;
        private static ForgeConfigSpec.DoubleValue impFleeHealth;
        private static ForgeConfigSpec.IntValue impFleeTicks;
        private static ForgeConfigSpec.IntValue impFleeCooldown;
        private static ForgeConfigSpec.IntValue impEliteBurst;
        private static ForgeConfigSpec.IntValue impEliteSummonCount;
        private static ForgeConfigSpec.IntValue impEliteSummonCooldown;
        private static ForgeConfigSpec.IntValue impNearbyCap;

        private static ForgeConfigSpec.DoubleValue stalkerHealth;
        private static ForgeConfigSpec.DoubleValue stalkerArmor;
        private static ForgeConfigSpec.DoubleValue stalkerAttack;
        private static ForgeConfigSpec.IntValue stalkerSolidTicks;
        private static ForgeConfigSpec.IntValue stalkerWarningTicks;
        private static ForgeConfigSpec.IntValue stalkerPhaseTicks;
        private static ForgeConfigSpec.IntValue stalkerRiftClawCooldown;
        private static ForgeConfigSpec.IntValue stalkerBlinkMin;
        private static ForgeConfigSpec.IntValue stalkerBlinkMax;
        private static ForgeConfigSpec.IntValue stalkerPullCooldown;
        private static ForgeConfigSpec.IntValue stalkerPullWarning;
        private static ForgeConfigSpec.IntValue stalkerPullRange;
        private static ForgeConfigSpec.IntValue stalkerSpawnInterval;
        private static ForgeConfigSpec.DoubleValue stalkerSpawnChance;
        private static ForgeConfigSpec.IntValue stalkerSpawnMin;
        private static ForgeConfigSpec.IntValue stalkerSpawnMax;
        private static ForgeConfigSpec.IntValue stalkerSpawnMaxNearby;

        @Nullable
        private static volatile ImpValues testImp;
        @Nullable
        private static volatile StalkerValues testStalker;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            ImpValues imp = IMP_DEFAULTS;
            builder.comment("Nether Imp: small flying monster of the Nether Wastes and Soul Sand Valley.",
                    "炼狱小鬼：下界荒地、灵魂沙峡谷里会飞的小怪").push(List.of(ECMobFramework.CONFIG_SECTION, "nether_imp"));
            impHealth = builder.comment("Max health (set when it spawns).", "最大生命（生成时设好）")
                    .defineInRange("max_health", imp.maxHealth(), 1D, 1024D);
            impArmor = builder.comment("Armor (set when it spawns).", "护甲（生成时设好）")
                    .defineInRange("armor", imp.armor(), 0D, 30D);
            impBoltDamage = builder.comment("Damage of one ember bolt (set when it spawns; elites x elite_attack_multiplier).",
                            "一颗余火弹的伤害（生成时设好；精英再乘 elite_attack_multiplier）")
                    .defineInRange("bolt_damage", imp.boltDamage(), 0D, 100D);
            impBurnSeconds = builder.comment("Seconds a target hit by an ember bolt keeps burning (0 = none). Bolts never set blocks on fire.",
                            "被余火弹打中的目标烧几秒（0 = 不烧）。落地一律不放火")
                    .defineInRange("burn_seconds", imp.burnSeconds(), 0, 30);
            impShootInterval = builder.comment("Ticks between attacks (plus or minus 10).", "两次出手之间隔几刻（上下浮动 10 刻）")
                    .defineInRange("shoot_interval", imp.shootInterval(), 20, 1200);
            impShootRange = builder.comment("It only shoots at targets within this many blocks.", "多远以内才吐余火弹（格）")
                    .defineInRange("shoot_range", imp.shootRange(), 2, 64);
            impFleeHealth = builder.comment("It flees when its health drops to this share of its max health (0 = never flees).",
                            "生命剩到最大生命的这个比例以下就逃（0 = 不逃）")
                    .defineInRange("flee_health", imp.fleeHealth(), 0D, 1D);
            impFleeTicks = builder.comment("Ticks one flight lasts.", "一次逃多少刻")
                    .defineInRange("flee_ticks", imp.fleeTicks(), 1, 1200);
            impFleeCooldown = builder.comment("Ticks after fleeing before it can flee again.", "逃完以后隔多少刻才会再逃")
                    .defineInRange("flee_cooldown", imp.fleeCooldown(), 0, 12_000);
            impEliteBurst = builder.comment("Bolts per volley of the elite (Imp Chieftain).", "小鬼头目一次连发几颗")
                    .defineInRange("elite_burst", imp.eliteBurst(), 1, 10);
            impEliteSummonCount = builder.comment("Imps the Imp Chieftain calls in; it never has more of its own alive than this (0 = none).",
                            "小鬼头目叫来几只；它叫来的、还活着的不会超过这个数（0 = 不叫）")
                    .defineInRange("elite_summon_count", imp.eliteSummonCount(), 0, 8);
            impEliteSummonCooldown = builder.comment("Ticks between two calls for help.", "两次叫帮手之间隔几刻")
                    .defineInRange("elite_summon_cooldown", imp.eliteSummonCooldown(), 20, 72_000);
            impNearbyCap = builder.comment("Natural spawning stops when this many imps are within 32 blocks.", "自然生成时 32 格内已有这么多只就不刷")
                    .defineInRange("nearby_cap", imp.nearbyCap(), 1, 64);
            builder.pop(2);

            StalkerValues stalker = STALKER_DEFAULTS;
            builder.comment("Void Stalker: monster of the outer End islands, spawned by its own spawner near players (it is in no biome's spawn list).",
                    "虚空潜行者：末地外岛的怪，由它自己的生成器在玩家附近刷（不在任何群系的刷怪表里）").push(List.of(ECMobFramework.CONFIG_SECTION, "void_stalker"));
            stalkerHealth = builder.comment("Max health (set when it spawns).", "最大生命（生成时设好）")
                    .defineInRange("max_health", stalker.maxHealth(), 1D, 1024D);
            stalkerArmor = builder.comment("Armor (set when it spawns).", "护甲（生成时设好）")
                    .defineInRange("armor", stalker.armor(), 0D, 30D);
            stalkerAttack = builder.comment("Claw damage (set when it spawns; elites x elite_attack_multiplier).",
                            "爪击伤害（生成时设好；精英再乘 elite_attack_multiplier）")
                    .defineInRange("attack_damage", stalker.attackDamage(), 0D, 100D);
            stalkerSolidTicks = builder.comment("Ticks it stays solid each cycle, the warning included.", "每轮实体化多少刻（含最后的预警）")
                    .defineInRange("solid_ticks", stalker.solidTicks(), 20, 6000);
            stalkerWarningTicks = builder.comment("Ticks of warning before it phases out (counted inside solid_ticks).", "相位前预警多少刻（算在 solid_ticks 里）")
                    .defineInRange("warning_ticks", stalker.warningTicks(), 0, 100);
            stalkerPhaseTicks = builder.comment("Ticks it stays phased each cycle; nothing can hurt it then (0 = never phases).",
                            "每轮相位多少刻，这时什么都打不动它（0 = 不进相位）")
                    .defineInRange("phase_ticks", stalker.phaseTicks(), 0, 6000);
            stalkerRiftClawCooldown = builder.comment("Ticks between rift claws, which blink the target a few blocks away (0 = never).",
                            "裂隙爪（把目标闪到几格外）的冷却刻数（0 = 不闪人）")
                    .defineInRange("rift_claw_cooldown", stalker.riftClawCooldown(), 0, 12_000);
            stalkerBlinkMin = builder.comment("A rift claw lands the target at least this many blocks away.", "裂隙爪把目标闪到至少几格外")
                    .defineInRange("blink_min_distance", stalker.blinkMin(), 1, 32);
            stalkerBlinkMax = builder.comment("A rift claw lands the target at most this many blocks away.", "裂隙爪把目标闪到至多几格外")
                    .defineInRange("blink_max_distance", stalker.blinkMax(), 1, 32);
            stalkerPullCooldown = builder.comment("Ticks between rift pulls of the elite (Riftwalker) (0 = never).", "裂隙行者拉人的冷却刻数（0 = 不拉人）")
                    .defineInRange("pull_cooldown", stalker.pullCooldown(), 0, 12_000);
            stalkerPullWarning = builder.comment("Ticks the rift shows under the target before the pull.", "拉人前裂隙在目标脚下预警多少刻")
                    .defineInRange("pull_warning_ticks", stalker.pullWarning(), 1, 200);
            stalkerPullRange = builder.comment("Riftwalkers pull targets within this many blocks.", "裂隙行者能拉多远的目标（格）")
                    .defineInRange("pull_range", stalker.pullRange(), 4, 48);
            stalkerSpawnInterval = builder.comment("Ticks between spawn rolls for each survival / adventure player on the outer End islands.",
                            "生成器每隔多少刻给末地外岛上的每名生存 / 冒险玩家掷一次")
                    .defineInRange("spawn_interval", stalker.spawnInterval(), 20, 72_000);
            stalkerSpawnChance = builder.comment("Chance per roll (0 = the spawner is off).", "每次掷中的几率（0 = 关掉生成器）")
                    .defineInRange("spawn_chance", stalker.spawnChance(), 0D, 1D);
            stalkerSpawnMin = builder.comment("Spawn at least this many blocks from the player.", "离玩家至少几格才刷")
                    .defineInRange("spawn_min_distance", stalker.spawnMin(), 8, 128);
            stalkerSpawnMax = builder.comment("Spawn at most this many blocks from the player.", "离玩家至多几格才刷")
                    .defineInRange("spawn_max_distance", stalker.spawnMax(), 8, 128);
            stalkerSpawnMaxNearby = builder.comment("No spawn when this many Void Stalkers are within 64 blocks of the spot (0 = the spawner is off).",
                            "刷的地方 64 格内已有这么多只就不刷（0 = 关掉生成器）")
                    .defineInRange("spawn_max_nearby", stalker.spawnMaxNearby(), 0, 32);
            builder.pop(2);
        }

        /** 炼狱小鬼现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static ImpValues imp() {
            ImpValues override = testImp;
            if (override != null) {
                return override;
            }
            if (impHealth == null) {
                return IMP_DEFAULTS;
            }
            return new ImpValues(ECConfig.get(impHealth), ECConfig.get(impArmor), ECConfig.get(impBoltDamage), ECConfig.get(impBurnSeconds),
                    ECConfig.get(impShootInterval), ECConfig.get(impShootRange), ECConfig.get(impFleeHealth), ECConfig.get(impFleeTicks),
                    ECConfig.get(impFleeCooldown), ECConfig.get(impEliteBurst), ECConfig.get(impEliteSummonCount),
                    ECConfig.get(impEliteSummonCooldown), ECConfig.get(impNearbyCap));
        }

        /** 虚空潜行者现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static StalkerValues stalker() {
            StalkerValues override = testStalker;
            if (override != null) {
                return override;
            }
            if (stalkerHealth == null) {
                return STALKER_DEFAULTS;
            }
            return new StalkerValues(ECConfig.get(stalkerHealth), ECConfig.get(stalkerArmor), ECConfig.get(stalkerAttack),
                    ECConfig.get(stalkerSolidTicks), ECConfig.get(stalkerWarningTicks), ECConfig.get(stalkerPhaseTicks),
                    ECConfig.get(stalkerRiftClawCooldown), ECConfig.get(stalkerBlinkMin), ECConfig.get(stalkerBlinkMax),
                    ECConfig.get(stalkerPullCooldown), ECConfig.get(stalkerPullWarning), ECConfig.get(stalkerPullRange),
                    ECConfig.get(stalkerSpawnInterval), ECConfig.get(stalkerSpawnChance), ECConfig.get(stalkerSpawnMin),
                    ECConfig.get(stalkerSpawnMax), ECConfig.get(stalkerSpawnMaxNearby));
        }

        /** 只给 GameTest：在同一段同步代码里设、测、置回 null（别用 ConfigValue.set，文件监视器会冲掉） */
        public static void setTestImp(@Nullable ImpValues values) {
            testImp = values;
        }

        /** 只给 GameTest：同上 */
        public static void setTestStalker(@Nullable StalkerValues values) {
            testStalker = values;
        }
    }
}
