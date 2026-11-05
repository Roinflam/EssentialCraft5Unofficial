package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.CachedServerValues;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.hoanna.FrostbittenEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.HoannaScavengerEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ManaPrismEntity;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 第十三批 13d-N1「霜蚀者、浮游棱晶、遗民拾荒者」（batch13.md §5.2、§6.5、§9.4；policy §4.18、§4.19）：
 * <ul>
 *   <li>{@code frostbitten} 霜蚀者（{@link FrostbittenEntity}）：冰冻腐化之地。近战命中加冰冻刻，每 12 秒一口 1 秒蓄力的冰息；
 *       精英「霜棺督卫」正面举冰盾、每 15 秒冰封身边的人。</li>
 *   <li>{@code mana_prism} 浮游棱晶（{@link ManaPrismEntity}）：魔力腐化之地（地脉风暴时的全陆地追加由 13d-N3 的刷怪钩子做）。
 *       锁定 1.5 秒后沿锁定那一刻的方向射光束，横移就躲得开；怕近战、不怕箭。精英「棱晶集束」一次三束（扇形）。</li>
 *   <li>{@code hoanna_scavenger} 遗民拾荒者（{@link HoannaScavengerEntity}）：恐惧之地、海纳尔沙漠，白天也在地表刷。
 *       撬棍或步枪（生成时定、存盘），命中加辐射剂量；残血打针。精英「遗民队长」背护盾、第一次被打到一半时打信号弹叫两名拾荒者。</li>
 *   <li>刷怪蛋 {@code <id>_spawn_egg} ×3（只在创造栏，policy §4.7；能改刷怪笼，改出来的来源是刷怪笼，关键掉落拿不到）。</li>
 * </ul>
 * 刷怪表的权重、每群数量、天灾 / 潮汐 / 毒雾的追加是 13d-N3 的数据和钩子，这里只登记「这一格能不能刷」的判定和附近上限。
 * 掉落表 {@code data/essentialcraft5/loot_tables/entities/{frostbitten, mana_prism, hoanna_scavenger}.json}（公平击杀 + 按人递减，精英多一池）；
 * 文字 tools/lang/keys/echoannanativesa.json；音效 tools/sounds/echoannanativesa.json（都指原版声音、改音高）；客户端 client/HoannaNativesAClient；
 * 测试 gametest/HoannaNativesATests。服务端配置 {@code general.mobs.{frostbitten, mana_prism, hoanna_scavenger}}（{@link Config}）。
 */
public final class ECHoannaNativesA {

    /** 霜蚀者（冰冻腐化之地） */
    public static final RegistryObject<EntityType<FrostbittenEntity>> FROSTBITTEN = ECEntities.register("frostbitten",
            EntityType.Builder.<FrostbittenEntity>of(FrostbittenEntity::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));

    /** 浮游棱晶（魔力腐化之地；地脉风暴时全陆地） */
    public static final RegistryObject<EntityType<ManaPrismEntity>> MANA_PRISM = ECEntities.register("mana_prism",
            EntityType.Builder.<ManaPrismEntity>of(ManaPrismEntity::new, MobCategory.MONSTER).sized(0.8F, 1.2F).clientTrackingRange(8));

    /** 遗民拾荒者（恐惧之地、海纳尔沙漠，白天也刷） */
    public static final RegistryObject<EntityType<HoannaScavengerEntity>> HOANNA_SCAVENGER = ECEntities.register("hoanna_scavenger",
            EntityType.Builder.<HoannaScavengerEntity>of(HoannaScavengerEntity::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));

    public static final RegistryObject<ECEntities.SpawnEgg> FROSTBITTEN_SPAWN_EGG =
            ECEntities.spawnEgg("frostbitten_spawn_egg", FROSTBITTEN, 0x9FC3D6, 0xE8F7FF, true);
    public static final RegistryObject<ECEntities.SpawnEgg> MANA_PRISM_SPAWN_EGG =
            ECEntities.spawnEgg("mana_prism_spawn_egg", MANA_PRISM, 0x5B2A7A, 0xE07CFF, true);
    public static final RegistryObject<ECEntities.SpawnEgg> HOANNA_SCAVENGER_SPAWN_EGG =
            ECEntities.spawnEgg("hoanna_scavenger_spawn_egg", HOANNA_SCAVENGER, 0x5E5240, 0x9DB04A, true);

    // 音效（定义在 tools/sounds/echoannanativesa.json，全部指原版声音改音高 / 音量）
    public static final RegistryObject<SoundEvent> SOUND_FROSTBITTEN_AMBIENT = ECSounds.register("sound.mob.frostbitten.ambient");
    public static final RegistryObject<SoundEvent> SOUND_FROSTBITTEN_HURT = ECSounds.register("sound.mob.frostbitten.hurt");
    public static final RegistryObject<SoundEvent> SOUND_FROSTBITTEN_DEATH = ECSounds.register("sound.mob.frostbitten.death");
    public static final RegistryObject<SoundEvent> SOUND_FROSTBITTEN_BREATH_CHARGE = ECSounds.register("sound.mob.frostbitten.breath_charge");
    public static final RegistryObject<SoundEvent> SOUND_FROSTBITTEN_BREATH = ECSounds.register("sound.mob.frostbitten.breath");
    public static final RegistryObject<SoundEvent> SOUND_FROSTBITTEN_SEAL_WARN = ECSounds.register("sound.mob.frostbitten.seal_warn");
    public static final RegistryObject<SoundEvent> SOUND_FROSTBITTEN_SEAL = ECSounds.register("sound.mob.frostbitten.seal");
    public static final RegistryObject<SoundEvent> SOUND_FROSTBITTEN_SHIELD = ECSounds.register("sound.mob.frostbitten.shield");
    public static final RegistryObject<SoundEvent> SOUND_PRISM_AMBIENT = ECSounds.register("sound.mob.mana_prism.ambient");
    public static final RegistryObject<SoundEvent> SOUND_PRISM_HURT = ECSounds.register("sound.mob.mana_prism.hurt");
    public static final RegistryObject<SoundEvent> SOUND_PRISM_DEATH = ECSounds.register("sound.mob.mana_prism.death");
    public static final RegistryObject<SoundEvent> SOUND_PRISM_LOCK = ECSounds.register("sound.mob.mana_prism.lock");
    public static final RegistryObject<SoundEvent> SOUND_PRISM_SHOOT = ECSounds.register("sound.mob.mana_prism.shoot");
    public static final RegistryObject<SoundEvent> SOUND_SCAVENGER_AMBIENT = ECSounds.register("sound.mob.hoanna_scavenger.ambient");
    public static final RegistryObject<SoundEvent> SOUND_SCAVENGER_HURT = ECSounds.register("sound.mob.hoanna_scavenger.hurt");
    public static final RegistryObject<SoundEvent> SOUND_SCAVENGER_DEATH = ECSounds.register("sound.mob.hoanna_scavenger.death");
    public static final RegistryObject<SoundEvent> SOUND_SCAVENGER_AIM = ECSounds.register("sound.mob.hoanna_scavenger.aim");
    public static final RegistryObject<SoundEvent> SOUND_SCAVENGER_SHOOT = ECSounds.register("sound.mob.hoanna_scavenger.shoot");
    public static final RegistryObject<SoundEvent> SOUND_SCAVENGER_STIM = ECSounds.register("sound.mob.hoanna_scavenger.stim");
    public static final RegistryObject<SoundEvent> SOUND_SCAVENGER_FLARE = ECSounds.register("sound.mob.hoanna_scavenger.flare");
    public static final RegistryObject<SoundEvent> SOUND_SCAVENGER_SHIELD = ECSounds.register("sound.mob.hoanna_scavenger.shield");

    /** 霜蚀者自然生成时，这么多格内同种不到 {@code frostbitten.nearby_cap} 只才刷（batch13.md §6.5：8 / 48 格） */
    public static final int FROSTBITTEN_NEARBY_RADIUS = 48;
    /** 浮游棱晶：5 / 64 格（地脉风暴追加的那一条另按 hoanna_spawns.storm_prism_max_nearby，见 ManaPrismEntity.checkPrismSpawnRulesCapped） */
    public static final int PRISM_NEARBY_RADIUS = 64;
    /** 遗民拾荒者：4 / 64 格（白天也刷，上限压低） */
    public static final int SCAVENGER_NEARBY_RADIUS = 64;

    private ECHoannaNativesA() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；属性、刷怪位置、配置在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
        ECEntities.attributes(FROSTBITTEN, FrostbittenEntity::createAttributes);
        ECEntities.attributes(MANA_PRISM, ManaPrismEntity::createAttributes);
        ECEntities.attributes(HOANNA_SCAVENGER, HoannaScavengerEntity::createAttributes);
        // 霜蚀者、棱晶照原版怪物（夜里 / 暗处）；拾荒者另外放行露天、y ≥ surface_min_y 的地表（白天也刷），见 HoannaScavengerEntity.checkScavengerSpawnRules
        ECEntities.spawnPlacement(FROSTBITTEN, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MobSpawnRules.capped(FrostbittenEntity::checkFrostbittenSpawnRules, () -> FROSTBITTEN_NEARBY_RADIUS, () -> Config.frost().nearbyCap()));
        ECEntities.spawnPlacement(MANA_PRISM, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ManaPrismEntity::checkPrismSpawnRulesCapped);
        ECEntities.spawnPlacement(HOANNA_SCAVENGER, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MobSpawnRules.capped(HoannaScavengerEntity::checkScavengerSpawnRules, () -> SCAVENGER_NEARBY_RADIUS,
                        () -> Config.scavenger().nearbyCap()));
    }

    /**
     * 打实以后才加的副作用（{@code LivingDamageEvent} LOWEST + {@link ECLandedHit#landed}；被混合端插件或别的模组拦下的那一下什么都不加）：
     * <ul>
     *   <li>霜蚀者亲手（直接实体就是它）的近战：目标加冰冻刻（{@link FrostbittenEntity#chill}，穿皮革的照原版不冻）；</li>
     *   <li>拾荒者的撬棍（直接实体是它）/ 步枪（{@code mob_bolt}、没有直接实体）打中真玩家：加辐射剂量，乘普通易伤（{@link HoannaScavengerEntity#irradiate}）。</li>
     * </ul>
     */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class HoannaNativesAEvents {

        private HoannaNativesAEvents() {
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void hoannaNativesAHitLanded(LivingDamageEvent event) {
            DamageSource source = event.getSource();
            Entity attacker = source.getEntity();
            if (!(attacker instanceof FrostbittenEntity) && !(attacker instanceof HoannaScavengerEntity)) {
                return;
            }
            if (event.getEntity().level().isClientSide || !ECLandedHit.landed(event)) {
                return;
            }
            if (attacker instanceof FrostbittenEntity frost && FrostbittenEntity.isMeleeFrom(source, frost)) {
                frost.chill(event.getEntity(), Config.frost().freezePerHit());
            } else if (attacker instanceof HoannaScavengerEntity scavenger && event.getEntity() instanceof ServerPlayer player
                    && !(player instanceof FakePlayer)) {
                scavenger.irradiate(player, source);
            }
        }
    }

    /**
     * 服务端配置 {@code general.mobs.frostbitten}、{@code general.mobs.mana_prism}、{@code general.mobs.hoanna_scavenger}。
     * 生命、护甲、攻击在生成时（{@code finalizeSpawn}）按配置设成基础值，已经在世界里的怪不跟着改；别的数值每次用到时读。
     * 读数一律走 {@link #frost()} / {@link #prism()} / {@link #scavenger()}（配置没加载时给默认值，GameTest 的覆盖值优先）。
     */
    public static final class Config {

        /**
         * 霜蚀者的一套数值。
         *
         * @param maxHealth            最大生命
         * @param armor                护甲
         * @param attackDamage         近战伤害（精英 ×1.4）
         * @param freezePerHit         近战命中给目标加几刻冰冻（0 = 不冻）
         * @param breathInterval       两口冰息之间隔几刻（0 = 不吐）
         * @param breathCharge         冰息蓄力几刻（预警：白雾往嘴边聚）
         * @param breathRange          冰息够多远（格，前方 90° 锥形）
         * @param breathDamage         冰息的固定伤害
         * @param breathHealthPercent  冰息另加目标最大生命的这个比例
         * @param breathFreeze         冰息给目标加几刻冰冻
         * @param sealInterval         霜棺督卫两次冰封之间隔几刻（0 = 不冰封）
         * @param sealWarning          冰封前地上的冰圈预警几刻
         * @param sealRadius           冰封的半径（格）
         * @param sealTicks            冰封把人冻住几刻
         * @param shieldMeleeReduction 霜棺督卫的冰盾正面挡掉多少近战伤害（0~1；正面的弹射物一律挡下）
         * @param nearbyCap            自然生成时 48 格内同种最多几只
         */
        public record FrostValues(double maxHealth, double armor, double attackDamage, int freezePerHit, int breathInterval, int breathCharge,
                                  double breathRange, double breathDamage, double breathHealthPercent, int breathFreeze, int sealInterval,
                                  int sealWarning, double sealRadius, int sealTicks, double shieldMeleeReduction, int nearbyCap) {
        }

        /** batch13.md §5.2：44 / 6；怪物稿 §2.4.1：攻击 8、命中冻 60 刻、冰息每 12 秒 1 秒蓄力 5 格 4 点 + 80 冻结刻、冰封每 15 秒 3 格 */
        public static final FrostValues FROST_DEFAULTS = new FrostValues(44D, 6D, 8D, 60, 240, 20, 5D, 4D, 0.05D, 80, 300, 20, 3D, 40, 0.3D, 8);

        /**
         * 浮游棱晶的一套数值。
         *
         * @param maxHealth         最大生命
         * @param armor             护甲
         * @param beamDamage        光束的固定伤害
         * @param beamHealthPercent 光束另加目标最大生命的这个比例
         * @param lockInterval      两次锁定之间隔几刻
         * @param lockTicks         锁定多少刻以后开火（预警：一道细光线连到锁定的方向）
         * @param beamRange         光束最远射多远（格，也是开始锁定的距离）
         * @param keepMin           它想离目标至少几格
         * @param keepMax           它想离目标至多几格
         * @param eliteBeams        棱晶集束一次几束
         * @param eliteSpread       棱晶集束相邻两束之间差几度（水平扇形）
         * @param nearbyCap         自然生成时 64 格内同种最多几只
         */
        public record PrismValues(double maxHealth, double armor, double beamDamage, double beamHealthPercent, int lockInterval, int lockTicks,
                                  double beamRange, double keepMin, double keepMax, int eliteBeams, double eliteSpread, int nearbyCap) {
        }

        /** batch13.md §5.2：30 / 12，锁定 1.5 秒、光束 5 + 3%；怪物稿 §2.4.2：每 80 刻锁一次、保持 8~14 格 */
        public static final PrismValues PRISM_DEFAULTS = new PrismValues(30D, 12D, 5D, 0.03D, 80, 30, 20D, 8D, 14D, 3, 15D, 5);

        /**
         * 遗民拾荒者的一套数值。
         *
         * @param maxHealth        最大生命
         * @param armor            护甲
         * @param crowbarDamage    撬棍伤害（拿撬棍的攻击属性，精英 ×1.4）
         * @param rifleDamage      步枪一发的伤害（拿步枪的攻击属性，精英 ×1.4）
         * @param rifleChance      生成时拿步枪的几率（其余拿撬棍）
         * @param rifleAim         步枪瞄多少刻才开火
         * @param rifleRange       步枪射程（格）
         * @param rifleRest        开完一枪以后歇几刻再瞄
         * @param crowbarDose      撬棍打实一下给玩家加的辐射剂量（乘玩家的辐射易伤）
         * @param rifleDose        步枪打实一下给玩家加的辐射剂量（乘玩家的辐射易伤）
         * @param stimHealth       生命剩到最大生命的这个比例以下就打针（0 = 不打）
         * @param stimTicks        打针给几刻再生 II
         * @param stimCooldown     打完一针以后隔几刻才能再打（存盘）
         * @param eliteSummonCount 遗民队长的信号弹叫来几名（0 = 不叫）
         * @param eliteFlareHealth 遗民队长第一次被打到最大生命的这个比例以下时打信号弹
         * @param surfaceMinY      白天地表变体要求的最低高度（脚所在的 y）
         * @param nearbyCap        自然生成时 64 格内同种最多几只
         */
        public record ScavengerValues(double maxHealth, double armor, double crowbarDamage, double rifleDamage, double rifleChance, int rifleAim,
                                      double rifleRange, int rifleRest, int crowbarDose, int rifleDose, double stimHealth, int stimTicks,
                                      int stimCooldown, int eliteSummonCount, double eliteFlareHealth, int surfaceMinY, int nearbyCap) {
        }

        /** batch13.md §5.2：36 / 8；怪物稿 §2.4.5：撬棍 9 / 步枪 6（2 秒瞄准、15 格），剂量 +150 / +300，残血 25% 打针（再生 II 3 秒）；§6.5：y ≥ 60、64 格 ≤ 4 */
        public static final ScavengerValues SCAVENGER_DEFAULTS = new ScavengerValues(36D, 8D, 9D, 6D, 0.5D, 40, 15D, 30, 150, 300, 0.25D, 60,
                1200, 2, 0.5D, 60, 4);

        private static ForgeConfigSpec.DoubleValue frostHealth;
        private static ForgeConfigSpec.DoubleValue frostArmor;
        private static ForgeConfigSpec.DoubleValue frostAttack;
        private static ForgeConfigSpec.IntValue frostFreezePerHit;
        private static ForgeConfigSpec.IntValue frostBreathInterval;
        private static ForgeConfigSpec.IntValue frostBreathCharge;
        private static ForgeConfigSpec.DoubleValue frostBreathRange;
        private static ForgeConfigSpec.DoubleValue frostBreathDamage;
        private static ForgeConfigSpec.DoubleValue frostBreathPercent;
        private static ForgeConfigSpec.IntValue frostBreathFreeze;
        private static ForgeConfigSpec.IntValue frostSealInterval;
        private static ForgeConfigSpec.IntValue frostSealWarning;
        private static ForgeConfigSpec.DoubleValue frostSealRadius;
        private static ForgeConfigSpec.IntValue frostSealTicks;
        private static ForgeConfigSpec.DoubleValue frostShieldReduction;
        private static ForgeConfigSpec.IntValue frostNearbyCap;

        private static ForgeConfigSpec.DoubleValue prismHealth;
        private static ForgeConfigSpec.DoubleValue prismArmor;
        private static ForgeConfigSpec.DoubleValue prismBeamDamage;
        private static ForgeConfigSpec.DoubleValue prismBeamPercent;
        private static ForgeConfigSpec.IntValue prismLockInterval;
        private static ForgeConfigSpec.IntValue prismLockTicks;
        private static ForgeConfigSpec.DoubleValue prismBeamRange;
        private static ForgeConfigSpec.DoubleValue prismKeepMin;
        private static ForgeConfigSpec.DoubleValue prismKeepMax;
        private static ForgeConfigSpec.IntValue prismEliteBeams;
        private static ForgeConfigSpec.DoubleValue prismEliteSpread;
        private static ForgeConfigSpec.IntValue prismNearbyCap;

        private static ForgeConfigSpec.DoubleValue scavHealth;
        private static ForgeConfigSpec.DoubleValue scavArmor;
        private static ForgeConfigSpec.DoubleValue scavCrowbarDamage;
        private static ForgeConfigSpec.DoubleValue scavRifleDamage;
        private static ForgeConfigSpec.DoubleValue scavRifleChance;
        private static ForgeConfigSpec.IntValue scavRifleAim;
        private static ForgeConfigSpec.DoubleValue scavRifleRange;
        private static ForgeConfigSpec.IntValue scavRifleRest;
        private static ForgeConfigSpec.IntValue scavCrowbarDose;
        private static ForgeConfigSpec.IntValue scavRifleDose;
        private static ForgeConfigSpec.DoubleValue scavStimHealth;
        private static ForgeConfigSpec.IntValue scavStimTicks;
        private static ForgeConfigSpec.IntValue scavStimCooldown;
        private static ForgeConfigSpec.IntValue scavEliteSummonCount;
        private static ForgeConfigSpec.DoubleValue scavEliteFlareHealth;
        private static ForgeConfigSpec.IntValue scavSurfaceMinY;
        private static ForgeConfigSpec.IntValue scavNearbyCap;

        @Nullable
        private static volatile FrostValues testFrost;
        @Nullable
        private static volatile PrismValues testPrism;
        @Nullable
        private static volatile ScavengerValues testScavenger;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            FrostValues frost = FROST_DEFAULTS;
            builder.comment("Frostbitten: frozen human remains of the Frozen Corrupted Lands in Hoanna.",
                    "霜蚀者：海纳尔冰冻腐化之地里冻在冰壳里的人形遗骸").push(List.of(ECMobFramework.CONFIG_SECTION, "frostbitten"));
            frostHealth = builder.comment("Max health (set when it spawns).", "最大生命（生成时设好）")
                    .defineInRange("max_health", frost.maxHealth(), 1D, 1024D);
            frostArmor = builder.comment("Armor (set when it spawns).", "护甲（生成时设好）")
                    .defineInRange("armor", frost.armor(), 0D, 30D);
            frostAttack = builder.comment("Melee damage (set when it spawns; elites x elite_attack_multiplier).",
                            "近战伤害（生成时设好；精英再乘 elite_attack_multiplier）")
                    .defineInRange("attack_damage", frost.attackDamage(), 0D, 100D);
            frostFreezePerHit = builder.comment("Freeze ticks added to a target its melee hit lands on (0 = none). Leather armor protects as in vanilla.",
                            "近战打中时给目标加几刻冰冻（0 = 不冻）。穿皮革护甲的照原版不会冻")
                    .defineInRange("freeze_per_hit", frost.freezePerHit(), 0, 600);
            frostBreathInterval = builder.comment("Ticks between two Frost Breaths (0 = never).", "两口冰息之间隔几刻（0 = 不吐）")
                    .defineInRange("breath_interval", frost.breathInterval(), 0, 12_000);
            frostBreathCharge = builder.comment("Ticks a Frost Breath charges (the mist gathers at its mouth).", "冰息蓄力几刻（白雾往嘴边聚）")
                    .defineInRange("breath_charge", frost.breathCharge(), 1, 200);
            frostBreathRange = builder.comment("Reach of the Frost Breath cone in front of it (blocks).", "冰息往前喷多远（格，前方 90° 锥形）")
                    .defineInRange("breath_range", frost.breathRange(), 1D, 16D);
            frostBreathDamage = builder.comment("Flat damage of the Frost Breath (ignores armor).", "冰息的固定伤害（无视护甲）")
                    .defineInRange("breath_damage", frost.breathDamage(), 0D, 100D);
            frostBreathPercent = builder.comment("Frost Breath also deals this share of the target's max health.", "冰息另加目标最大生命的这个比例")
                    .defineInRange("breath_health_percent", frost.breathHealthPercent(), 0D, 0.2D);
            frostBreathFreeze = builder.comment("Freeze ticks the Frost Breath adds.", "冰息给目标加几刻冰冻")
                    .defineInRange("breath_freeze_ticks", frost.breathFreeze(), 0, 600);
            frostSealInterval = builder.comment("Ticks between two Ice Seals of the elite (Frost Coffin Warden) (0 = never).",
                            "霜棺督卫两次冰封之间隔几刻（0 = 不冰封）")
                    .defineInRange("elite_seal_interval", frost.sealInterval(), 0, 12_000);
            frostSealWarning = builder.comment("Ticks the ice ring shows on the ground before an Ice Seal.", "冰封前地上的冰圈亮几刻")
                    .defineInRange("elite_seal_warning", frost.sealWarning(), 1, 200);
            frostSealRadius = builder.comment("Radius of the Ice Seal (blocks).", "冰封的半径（格）")
                    .defineInRange("elite_seal_radius", frost.sealRadius(), 1D, 12D);
            frostSealTicks = builder.comment("Ticks an Ice Seal keeps you fully frozen and slowed.", "冰封让你冻透、走不动几刻")
                    .defineInRange("elite_seal_ticks", frost.sealTicks(), 1, 400);
            frostShieldReduction = builder.comment("Share of melee damage the Warden's ice shield blocks from the front (projectiles from the front are always blocked).",
                            "霜棺督卫的冰盾正面挡掉多少近战伤害（0~1；正面来的弹射物一律挡下）")
                    .defineInRange("elite_shield_melee_reduction", frost.shieldMeleeReduction(), 0D, 1D);
            frostNearbyCap = builder.comment("Natural spawning stops when this many are within 48 blocks.", "自然生成时 48 格内已有这么多只就不刷")
                    .defineInRange("nearby_cap", frost.nearbyCap(), 1, 64);
            builder.pop(2);

            PrismValues prism = PRISM_DEFAULTS;
            builder.comment("Mana Prism: floating crystal of the Mana Corrupted Lands in Hoanna.",
                    "浮游棱晶：海纳尔魔力腐化之地里飘着的晶体").push(List.of(ECMobFramework.CONFIG_SECTION, "mana_prism"));
            prismHealth = builder.comment("Max health (set when it spawns).", "最大生命（生成时设好）")
                    .defineInRange("max_health", prism.maxHealth(), 1D, 1024D);
            prismArmor = builder.comment("Armor (set when it spawns).", "护甲（生成时设好）")
                    .defineInRange("armor", prism.armor(), 0D, 30D);
            prismBeamDamage = builder.comment("Flat damage of one beam.", "一道光束的固定伤害")
                    .defineInRange("beam_damage", prism.beamDamage(), 0D, 100D);
            prismBeamPercent = builder.comment("A beam also deals this share of the target's max health.", "光束另加目标最大生命的这个比例")
                    .defineInRange("beam_health_percent", prism.beamHealthPercent(), 0D, 0.2D);
            prismLockInterval = builder.comment("Ticks between two lock-ons.", "两次锁定之间隔几刻")
                    .defineInRange("lock_interval", prism.lockInterval(), 20, 12_000);
            prismLockTicks = builder.comment("Ticks it holds a lock before firing; the beam goes where it was aimed when the lock began.",
                            "锁定多少刻以后开火；光束沿锁定开始那一刻的方向射出")
                    .defineInRange("lock_ticks", prism.lockTicks(), 1, 200);
            prismBeamRange = builder.comment("Beam range; it only locks on targets this close (blocks).", "光束射程，也只锁这么近的目标（格）")
                    .defineInRange("beam_range", prism.beamRange(), 4D, 64D);
            prismKeepMin = builder.comment("It tries to stay at least this far from its target (blocks).", "它想离目标至少几格")
                    .defineInRange("keep_min_distance", prism.keepMin(), 1D, 32D);
            prismKeepMax = builder.comment("It tries to stay at most this far from its target (blocks).", "它想离目标至多几格")
                    .defineInRange("keep_max_distance", prism.keepMax(), 1D, 32D);
            prismEliteBeams = builder.comment("Beams per volley of the elite (Prism Cluster), fanned out.", "棱晶集束一次射几束（扇形排开）")
                    .defineInRange("elite_beams", prism.eliteBeams(), 1, 7);
            prismEliteSpread = builder.comment("Degrees between two neighbouring beams of the Prism Cluster.", "棱晶集束相邻两束之间差几度")
                    .defineInRange("elite_spread_degrees", prism.eliteSpread(), 1D, 45D);
            prismNearbyCap = builder.comment("Natural spawning stops when this many are within 64 blocks.", "自然生成时 64 格内已有这么多只就不刷")
                    .defineInRange("nearby_cap", prism.nearbyCap(), 1, 64);
            builder.pop(2);

            ScavengerValues scav = SCAVENGER_DEFAULTS;
            builder.comment("Remnant Scavenger: survivor in rags and a gas mask, found in the Dread Lands and Hoanna deserts, by day as well.",
                    "遗民拾荒者：裹着破布、戴防毒面具的遗民，恐惧之地和海纳尔沙漠里白天也有").push(List.of(ECMobFramework.CONFIG_SECTION, "hoanna_scavenger"));
            scavHealth = builder.comment("Max health (set when it spawns).", "最大生命（生成时设好）")
                    .defineInRange("max_health", scav.maxHealth(), 1D, 1024D);
            scavArmor = builder.comment("Armor (set when it spawns).", "护甲（生成时设好）")
                    .defineInRange("armor", scav.armor(), 0D, 30D);
            scavCrowbarDamage = builder.comment("Crowbar damage (set when it spawns; elites x elite_attack_multiplier).",
                            "撬棍伤害（生成时设好；精英再乘 elite_attack_multiplier）")
                    .defineInRange("crowbar_damage", scav.crowbarDamage(), 0D, 100D);
            scavRifleDamage = builder.comment("Damage of one rifle shot (set when it spawns; elites x elite_attack_multiplier).",
                            "步枪一发的伤害（生成时设好；精英再乘 elite_attack_multiplier）")
                    .defineInRange("rifle_damage", scav.rifleDamage(), 0D, 100D);
            scavRifleChance = builder.comment("Chance to carry a rifle instead of a crowbar (decided once when it spawns).", "生成时拿步枪的几率（其余拿撬棍，生成时定好）")
                    .defineInRange("rifle_chance", scav.rifleChance(), 0D, 1D);
            scavRifleAim = builder.comment("Ticks it aims before a rifle shot.", "步枪瞄多少刻才开火")
                    .defineInRange("rifle_aim_ticks", scav.rifleAim(), 1, 200);
            scavRifleRange = builder.comment("Rifle range (blocks).", "步枪射程（格）")
                    .defineInRange("rifle_range", scav.rifleRange(), 2D, 48D);
            scavRifleRest = builder.comment("Ticks it waits after a shot before aiming again.", "开完一枪以后歇几刻再瞄")
                    .defineInRange("rifle_rest_ticks", scav.rifleRest(), 0, 600);
            scavCrowbarDose = builder.comment("Radiation dose a landed crowbar hit adds to a player (times the player's radiation vulnerability).",
                            "撬棍打中玩家时加的辐射剂量（乘玩家的辐射易伤）")
                    .defineInRange("crowbar_dose", scav.crowbarDose(), 0, 100_000);
            scavRifleDose = builder.comment("Radiation dose a landed rifle shot adds to a player (times the player's radiation vulnerability).",
                            "步枪打中玩家时加的辐射剂量（乘玩家的辐射易伤）")
                    .defineInRange("rifle_dose", scav.rifleDose(), 0, 100_000);
            scavStimHealth = builder.comment("It injects a stim when its health drops to this share of its max health (0 = never).",
                            "生命剩到最大生命的这个比例以下就打针（0 = 不打）")
                    .defineInRange("stim_health", scav.stimHealth(), 0D, 1D);
            scavStimTicks = builder.comment("Ticks of Regeneration II a stim gives.", "一针给几刻再生 II")
                    .defineInRange("stim_ticks", scav.stimTicks(), 1, 1200);
            scavStimCooldown = builder.comment("Ticks after a stim before it can inject another.", "打完一针以后隔几刻才能再打")
                    .defineInRange("stim_cooldown", scav.stimCooldown(), 0, 72_000);
            scavEliteSummonCount = builder.comment("Scavengers the Remnant Captain's flare calls in (0 = no flare).", "遗民队长的信号弹叫来几名（0 = 不打信号弹）")
                    .defineInRange("elite_summon_count", scav.eliteSummonCount(), 0, 8);
            scavEliteFlareHealth = builder.comment("The Captain fires its flare the first time its health drops to this share of its max health.",
                            "遗民队长第一次被打到最大生命的这个比例以下时打信号弹")
                    .defineInRange("elite_flare_health", scav.eliteFlareHealth(), 0.05D, 1D);
            scavSurfaceMinY = builder.comment("Lowest y where scavengers spawn in daylight (open sky above, any light level).",
                            "白天也刷的地表变体最低要多高（头顶露天、亮度不限）")
                    .defineInRange("surface_min_y", scav.surfaceMinY(), -64, 320);
            scavNearbyCap = builder.comment("Natural spawning stops when this many are within 64 blocks.", "自然生成时 64 格内已有这么多只就不刷")
                    .defineInRange("nearby_cap", scav.nearbyCap(), 1, 64);
            builder.pop(2);
        }

        /** 按配置版本号缓存的 frost()（tick 里每刻读，命中时不分配） */
        private static final CachedServerValues<FrostValues> FROST_CACHE = new CachedServerValues<>();

        /** 霜蚀者现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static FrostValues frost() {
            FrostValues override = testFrost;
            if (override != null) {
                return override;
            }
            if (frostHealth == null) {
                return FROST_DEFAULTS;
            }
            return FROST_CACHE.get(() -> new FrostValues(ECConfig.get(frostHealth), ECConfig.get(frostArmor), ECConfig.get(frostAttack), ECConfig.get(frostFreezePerHit),
                    ECConfig.get(frostBreathInterval), ECConfig.get(frostBreathCharge), ECConfig.get(frostBreathRange), ECConfig.get(frostBreathDamage),
                    ECConfig.get(frostBreathPercent), ECConfig.get(frostBreathFreeze), ECConfig.get(frostSealInterval), ECConfig.get(frostSealWarning),
                    ECConfig.get(frostSealRadius), ECConfig.get(frostSealTicks), ECConfig.get(frostShieldReduction), ECConfig.get(frostNearbyCap)));
        }

        /** 按配置版本号缓存的 prism()（tick 里每刻读，命中时不分配） */
        private static final CachedServerValues<PrismValues> PRISM_CACHE = new CachedServerValues<>();

        /** 浮游棱晶现在的数值：测试覆盖值 → 配置（没加载时默认值）；最近距离填得比最远距离大时按最远距离算 */
        public static PrismValues prism() {
            PrismValues override = testPrism;
            PrismValues values;
            if (override != null) {
                values = override;
            } else if (prismHealth == null) {
                values = PRISM_DEFAULTS;
            } else {
                values = PRISM_CACHE.get(() -> new PrismValues(ECConfig.get(prismHealth), ECConfig.get(prismArmor), ECConfig.get(prismBeamDamage),
                        ECConfig.get(prismBeamPercent), ECConfig.get(prismLockInterval), ECConfig.get(prismLockTicks), ECConfig.get(prismBeamRange),
                        ECConfig.get(prismKeepMin), ECConfig.get(prismKeepMax), ECConfig.get(prismEliteBeams), ECConfig.get(prismEliteSpread),
                        ECConfig.get(prismNearbyCap)));
            }
            if (values.keepMin() > values.keepMax()) {
                values = new PrismValues(values.maxHealth(), values.armor(), values.beamDamage(), values.beamHealthPercent(), values.lockInterval(),
                        values.lockTicks(), values.beamRange(), values.keepMax(), values.keepMax(), values.eliteBeams(), values.eliteSpread(),
                        values.nearbyCap());
            }
            return values;
        }

        /** 按配置版本号缓存的 scavenger()（tick 里每刻读，命中时不分配） */
        private static final CachedServerValues<ScavengerValues> SCAVENGER_CACHE = new CachedServerValues<>();

        /** 遗民拾荒者现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static ScavengerValues scavenger() {
            ScavengerValues override = testScavenger;
            if (override != null) {
                return override;
            }
            if (scavHealth == null) {
                return SCAVENGER_DEFAULTS;
            }
            return SCAVENGER_CACHE.get(() -> new ScavengerValues(ECConfig.get(scavHealth), ECConfig.get(scavArmor), ECConfig.get(scavCrowbarDamage),
                    ECConfig.get(scavRifleDamage), ECConfig.get(scavRifleChance), ECConfig.get(scavRifleAim), ECConfig.get(scavRifleRange),
                    ECConfig.get(scavRifleRest), ECConfig.get(scavCrowbarDose), ECConfig.get(scavRifleDose), ECConfig.get(scavStimHealth),
                    ECConfig.get(scavStimTicks), ECConfig.get(scavStimCooldown), ECConfig.get(scavEliteSummonCount),
                    ECConfig.get(scavEliteFlareHealth), ECConfig.get(scavSurfaceMinY), ECConfig.get(scavNearbyCap)));
        }

        /** 只给 GameTest：在同一段同步代码里设、测、置回 null（别用 ConfigValue.set，文件监视器会冲掉） */
        public static void setTestFrost(@Nullable FrostValues values) {
            testFrost = values;
        }

        /** 只给 GameTest：同上 */
        public static void setTestPrism(@Nullable PrismValues values) {
            testPrism = values;
        }

        /** 只给 GameTest：同上 */
        public static void setTestScavenger(@Nullable ScavengerValues values) {
            testScavenger = values;
        }
    }
}
