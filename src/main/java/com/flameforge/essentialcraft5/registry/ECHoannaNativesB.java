package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.CachedServerValues;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.hoanna.AbyssalEelEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ChaosAberrantEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ChaosLarvaEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.EmberSpiritBoltEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.EmberSpiritEntity;
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
 * 第十三批 13d-N2「混沌畸变体、畸变幼体、余烬灵、渊鳗」（batch13.md §5.2、§6.5、§9.4）：
 * <ul>
 *   <li>{@code chaos_aberrant} 混沌畸变体（{@link ChaosAberrantEntity}）：混沌 / 暗影腐化之地的四足肉怪。1 秒蓄力冲撞（有方向预警，
 *       12 + 目标最大生命 6%，{@code aberrant_strike}），撞墙自己眩晕；死时裂出两只畸变幼体；地震里出生的一出来就狂暴。精英「畸变母巢」边打边产幼体。</li>
 *   <li>{@code chaos_larva} 畸变幼体（{@link ChaosLarvaEntity}）：只由畸变体裂出 / 母巢产出的技能实体，不存盘、不再分裂、40 秒后散掉、什么都不掉。没有刷怪蛋。</li>
 *   <li>{@code ember_spirit} 余烬灵（{@link EmberSpiritEntity}）：恐惧之地、海纳尔沙漠里悬浮的余烬团，一只一种余烬，吐 {@code ember_spirit_bolt}
 *       （{@link EmberSpiritBoltEntity}，{@code mob_bolt}）带那种余烬的效果；日阵里白天也刷。精英「余烬双生」带两种余烬、一次吐两颗。</li>
 *   <li>{@code abyssal_eel} 渊鳗（{@link AbyssalEelEntity}）：海纳尔之海的水中怪（MONSTER 类别 + 水中放置），咬中把人往下拽 1 秒。
 *       精英「渊鳗群首」第一次挨打叫来两条、咬中带缓慢。</li>
 *   <li>刷怪蛋 {@code chaos_aberrant_spawn_egg}、{@code ember_spirit_spawn_egg}、{@code abyssal_eel_spawn_egg}（只在创造栏；普通怪的蛋能改刷怪笼，
 *       改出来的来源是刷怪笼，关键掉落拿不到）。</li>
 * </ul>
 * 这一路只做怪本身和「这一格能不能刷」的判定（{@link #init()} 里的 {@code spawnPlacement}，附近上限用 {@link MobSpawnRules#capped}）；
 * 群系刷怪表的权重、天灾 / 潮汐追加归 13d-N3（{@code tools/gen/hoanna_spawns.py}），巢穴归 13d-HF / N3。
 * 掉落表 {@code data/essentialcraft5/loot_tables/entities/{chaos_aberrant, chaos_larva, ember_spirit, abyssal_eel}.json}（公平击杀在前、按人递减在后）；
 * 文字 tools/lang/keys/echoannanativesb.json；音效 tools/sounds/echoannanativesb.json（都指原版声音、改音高）；客户端 client/HoannaNativesBClient；
 * 测试 gametest/HoannaNativesBTests。服务端配置 {@code general.mobs.chaos_aberrant}、{@code general.mobs.ember_spirit}、{@code general.mobs.abyssal_eel}（{@link Config}）。
 */
public final class ECHoannaNativesB {

    /** 混沌畸变体（混沌腐化之地） */
    public static final RegistryObject<EntityType<ChaosAberrantEntity>> CHAOS_ABERRANT = ECEntities.register("chaos_aberrant",
            EntityType.Builder.<ChaosAberrantEntity>of(ChaosAberrantEntity::new, MobCategory.MONSTER).sized(1.2F, 1.6F).clientTrackingRange(8));

    /** 畸变幼体 */
    public static final RegistryObject<EntityType<ChaosLarvaEntity>> CHAOS_LARVA = ECEntities.register("chaos_larva",
            EntityType.Builder.<ChaosLarvaEntity>of(ChaosLarvaEntity::new, MobCategory.MONSTER).sized(0.5F, 0.4F).clientTrackingRange(8));

    /** 余烬灵（恐惧之地、海纳尔沙漠；日阵时白天也刷） */
    public static final RegistryObject<EntityType<EmberSpiritEntity>> EMBER_SPIRIT = ECEntities.register("ember_spirit",
            EntityType.Builder.<EmberSpiritEntity>of(EmberSpiritEntity::new, MobCategory.MONSTER).sized(0.6F, 1.2F).fireImmune().clientTrackingRange(8));

    /** 余烬弹：尺寸、追踪、同步间隔同原版小火球（客户端照速度自己推，10 刻校一次） */
    public static final RegistryObject<EntityType<EmberSpiritBoltEntity>> EMBER_SPIRIT_BOLT = ECEntities.register("ember_spirit_bolt",
            EntityType.Builder.<EmberSpiritBoltEntity>of(EmberSpiritBoltEntity::new, MobCategory.MISC)
                    .sized(0.3125F, 0.3125F).clientTrackingRange(4).updateInterval(10));

    /** 渊鳗（海纳尔之海，水里） */
    public static final RegistryObject<EntityType<AbyssalEelEntity>> ABYSSAL_EEL = ECEntities.register("abyssal_eel",
            EntityType.Builder.<AbyssalEelEntity>of(AbyssalEelEntity::new, MobCategory.MONSTER).sized(0.9F, 0.6F).clientTrackingRange(8));

    public static final RegistryObject<ECEntities.SpawnEgg> CHAOS_ABERRANT_SPAWN_EGG =
            ECEntities.spawnEgg("chaos_aberrant_spawn_egg", CHAOS_ABERRANT, 0x4A1418, 0xC2343A, true);
    public static final RegistryObject<ECEntities.SpawnEgg> EMBER_SPIRIT_SPAWN_EGG =
            ECEntities.spawnEgg("ember_spirit_spawn_egg", EMBER_SPIRIT, 0x2B2420, 0xF0A040, true);
    public static final RegistryObject<ECEntities.SpawnEgg> ABYSSAL_EEL_SPAWN_EGG =
            ECEntities.spawnEgg("abyssal_eel_spawn_egg", ABYSSAL_EEL, 0x1E2C3A, 0x6FD0E0, true);

    // 音效（定义在 tools/sounds/echoannanativesb.json，全部指原版声音改音高 / 音量）
    public static final RegistryObject<SoundEvent> SOUND_ABERRANT_AMBIENT = ECSounds.register("sound.mob.chaos_aberrant.ambient");
    public static final RegistryObject<SoundEvent> SOUND_ABERRANT_HURT = ECSounds.register("sound.mob.chaos_aberrant.hurt");
    public static final RegistryObject<SoundEvent> SOUND_ABERRANT_DEATH = ECSounds.register("sound.mob.chaos_aberrant.death");
    public static final RegistryObject<SoundEvent> SOUND_ABERRANT_WINDUP = ECSounds.register("sound.mob.chaos_aberrant.windup");
    public static final RegistryObject<SoundEvent> SOUND_ABERRANT_CHARGE = ECSounds.register("sound.mob.chaos_aberrant.charge");
    public static final RegistryObject<SoundEvent> SOUND_ABERRANT_STUNNED = ECSounds.register("sound.mob.chaos_aberrant.stunned");
    public static final RegistryObject<SoundEvent> SOUND_ABERRANT_SPLIT = ECSounds.register("sound.mob.chaos_aberrant.split");
    public static final RegistryObject<SoundEvent> SOUND_LARVA_AMBIENT = ECSounds.register("sound.mob.chaos_larva.ambient");
    public static final RegistryObject<SoundEvent> SOUND_LARVA_HURT = ECSounds.register("sound.mob.chaos_larva.hurt");
    public static final RegistryObject<SoundEvent> SOUND_LARVA_DEATH = ECSounds.register("sound.mob.chaos_larva.death");
    public static final RegistryObject<SoundEvent> SOUND_EMBER_AMBIENT = ECSounds.register("sound.mob.ember_spirit.ambient");
    public static final RegistryObject<SoundEvent> SOUND_EMBER_HURT = ECSounds.register("sound.mob.ember_spirit.hurt");
    public static final RegistryObject<SoundEvent> SOUND_EMBER_DEATH = ECSounds.register("sound.mob.ember_spirit.death");
    public static final RegistryObject<SoundEvent> SOUND_EMBER_SHOOT = ECSounds.register("sound.mob.ember_spirit.shoot");
    public static final RegistryObject<SoundEvent> SOUND_EMBER_BOLT_FIZZLE = ECSounds.register("sound.mob.ember_spirit_bolt.fizzle");
    public static final RegistryObject<SoundEvent> SOUND_EEL_AMBIENT = ECSounds.register("sound.mob.abyssal_eel.ambient");
    public static final RegistryObject<SoundEvent> SOUND_EEL_HURT = ECSounds.register("sound.mob.abyssal_eel.hurt");
    public static final RegistryObject<SoundEvent> SOUND_EEL_DEATH = ECSounds.register("sound.mob.abyssal_eel.death");
    public static final RegistryObject<SoundEvent> SOUND_EEL_BITE = ECSounds.register("sound.mob.abyssal_eel.bite");
    public static final RegistryObject<SoundEvent> SOUND_EEL_FLOP = ECSounds.register("sound.mob.abyssal_eel.flop");
    public static final RegistryObject<SoundEvent> SOUND_EEL_CALL = ECSounds.register("sound.mob.abyssal_eel.call");

    /** 自然生成时，这么多格内同种不到 {@code nearby_cap} 只才刷（batch13.md §6.5：畸变体、余烬灵 48 格，渊鳗 64 格） */
    public static final int ABERRANT_NEARBY_RADIUS = 48;
    public static final int EMBER_NEARBY_RADIUS = 48;
    public static final int EEL_NEARBY_RADIUS = 64;

    private ECHoannaNativesB() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；属性、刷怪位置、配置在这里登记 */
    public static void init() {
        ECEntities.attributes(CHAOS_ABERRANT, ChaosAberrantEntity::createAttributes);
        ECEntities.attributes(CHAOS_LARVA, ChaosLarvaEntity::createAttributes);
        ECEntities.attributes(EMBER_SPIRIT, EmberSpiritEntity::createAttributes);
        ECEntities.attributes(ABYSSAL_EEL, AbyssalEelEntity::createAttributes);
        ECConfig.general(Config::define);
        // 畸变体：夜里原版怪物判定；余烬灵：夜里判定，日阵进行中头顶露天时白天也放行；渊鳗：水里、够深、高度在范围内。附近上限都在最外层
        ECEntities.spawnPlacement(CHAOS_ABERRANT, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MobSpawnRules.capped(ChaosAberrantEntity::checkAberrantSpawnRules, () -> ABERRANT_NEARBY_RADIUS, () -> Config.aberrant().nearbyCap()));
        ECEntities.spawnPlacement(EMBER_SPIRIT, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MobSpawnRules.capped(EmberSpiritEntity::checkEmberSpawnRules, () -> EMBER_NEARBY_RADIUS, () -> Config.ember().nearbyCap()));
        ECEntities.spawnPlacement(ABYSSAL_EEL, SpawnPlacements.Type.IN_WATER, Heightmap.Types.OCEAN_FLOOR,
                MobSpawnRules.capped(AbyssalEelEntity::checkEelSpawnRules, () -> EEL_NEARBY_RADIUS, () -> Config.eel().nearbyCap()));
    }

    /**
     * 服务端配置 {@code general.mobs.chaos_aberrant}、{@code general.mobs.ember_spirit}、{@code general.mobs.abyssal_eel}。
     * 生命、护甲、攻击在生成时（{@code finalizeSpawn}）按配置设成基础值，已经在世界里的怪不跟着改；别的数值每次用到时读。
     * 读数一律走 {@link #aberrant()} / {@link #ember()} / {@link #eel()}（配置没加载时给默认值，GameTest 的覆盖值优先）。
     */
    public static final class Config {

        /**
         * 混沌畸变体（含畸变幼体、畸变母巢）的一套数值。
         *
         * @param maxHealth           最大生命
         * @param armor               护甲
         * @param attackDamage        普通近战伤害（精英 ×1.4）
         * @param chargeCooldown      两次冲撞之间隔几刻（0 = 不冲撞）
         * @param chargeWindup        冲撞前低头蓄力几刻（这段时间地上画出冲撞路线）
         * @param chargeDistance      冲撞直线冲多远（格）
         * @param chargeDamage        冲撞撞中的固定伤害
         * @param chargeHealthPercent 冲撞另加目标最大生命的这个比例
         * @param stunTicks           撞墙后自己眩晕几刻（0 = 撞墙不眩晕）
         * @param stunDamageTaken     眩晕时受到的伤害乘这个倍数
         * @param frenzyHealth        生命剩到最大生命的这个比例以下就狂暴（0 = 不会残血狂暴）
         * @param frenzySpeed         狂暴时移动速度加成（0.3 = 快 30%）
         * @param quakeAttack         地震里出生的那只另加的攻击加成（0.25 = 高 25%）
         * @param splitCount          死时裂出几只幼体（0 = 不裂）
         * @param larvaLifetime       幼体活多少刻后自己散掉
         * @param broodInterval       畸变母巢每隔几刻产一只幼体（0 = 不产）
         * @param broodMax            畸变母巢产的、还活着的幼体最多几只
         * @param residueTicks        畸变母巢冲撞落点那滩混沌残液留几刻（0 = 不留）
         * @param nearbyCap           自然生成时 48 格内同种最多几只
         */
        public record AberrantValues(double maxHealth, double armor, double attackDamage, int chargeCooldown, int chargeWindup, double chargeDistance,
                                     double chargeDamage, double chargeHealthPercent, int stunTicks, double stunDamageTaken, double frenzyHealth,
                                     double frenzySpeed, double quakeAttack, int splitCount, int larvaLifetime, int broodInterval, int broodMax,
                                     int residueTicks, int nearbyCap) {
        }

        /** batch13.md §5.2：60 / 4，冲撞 12 + 6%；怪物稿 §2.4.3：每 8 秒冲一次、冲 8 格、撞墙晕 2 秒受伤 ×1.5、残血 30% 狂暴 ×1.3、幼体 40 秒、母巢 10 秒一只最多 3 只 */
        public static final AberrantValues ABERRANT_DEFAULTS = new AberrantValues(60D, 4D, 10D, 160, 20, 8D, 12D, 0.06D, 40, 1.5D, 0.3D, 0.3D, 0.25D,
                2, 800, 200, 3, 60, 6);

        /**
         * 余烬灵的一套数值。
         *
         * @param maxHealth     最大生命
         * @param armor         护甲
         * @param boltDamage    一颗余烬弹的伤害（也是它的攻击属性，精英 ×1.4）
         * @param shootInterval 两次出手之间隔几刻（上下浮动 10 刻）
         * @param shootRange    多远以内才吐余烬弹（格）
         * @param magicalDrain  魔力余烬打中时从目标身上的储存物品抽多少 MRU（0 = 不抽）
         * @param nearbyCap     自然生成时 48 格内同种最多几只
         */
        public record EmberValues(double maxHealth, double armor, double boltDamage, int shootInterval, int shootRange, int magicalDrain, int nearbyCap) {
        }

        /** batch13.md §5.2：28 / 4；怪物稿 §2.4.4：每 50 刻一发、6 点、魔力余烬抽 200 MRU */
        public static final EmberValues EMBER_DEFAULTS = new EmberValues(28D, 4D, 6D, 50, 16, 200, 6);

        /**
         * 渊鳗的一套数值。
         *
         * @param maxHealth        最大生命
         * @param armor            护甲
         * @param biteDamage       咬一口的伤害（也是它的攻击属性，精英 ×1.4）
         * @param dragTicks        咬中后往下拽几刻（0 = 不拽）
         * @param dragSpeed        拽的时候把目标往下拉的最大速度（格 / 刻）
         * @param minDepth         自然生成要求那一格所在的水柱至少几格深
         * @param minY             自然生成的最低高度
         * @param maxY             自然生成的最高高度
         * @param nearbyCap        自然生成时 64 格内同种最多几条
         * @param eliteSummonCount 渊鳗群首第一次挨打叫来几条（0 = 不叫）
         * @param eliteSlowTicks   渊鳗群首咬中附带缓慢 I 几刻（0 = 不带）
         * @param dryDamage        离了水每秒掉几点血
         */
        public record EelValues(double maxHealth, double armor, double biteDamage, int dragTicks, double dragSpeed, int minDepth, int minY, int maxY,
                                int nearbyCap, int eliteSummonCount, int eliteSlowTicks, double dryDamage) {
        }

        /** batch13.md §5.2、§6.5：34 / 4，水深 ≥ 6、y 30~56、64 格 ≤ 6；怪物稿 §2.4.6：咬 8、下拽 1 秒（−0.3 / 刻）、群首叫 2 条、缓慢 4 秒、离水每秒 1 点 */
        public static final EelValues EEL_DEFAULTS = new EelValues(34D, 4D, 8D, 20, 0.3D, 6, 30, 56, 6, 2, 80, 1D);

        private static ForgeConfigSpec.DoubleValue aberrantHealth;
        private static ForgeConfigSpec.DoubleValue aberrantArmor;
        private static ForgeConfigSpec.DoubleValue aberrantAttack;
        private static ForgeConfigSpec.IntValue aberrantChargeCooldown;
        private static ForgeConfigSpec.IntValue aberrantChargeWindup;
        private static ForgeConfigSpec.DoubleValue aberrantChargeDistance;
        private static ForgeConfigSpec.DoubleValue aberrantChargeDamage;
        private static ForgeConfigSpec.DoubleValue aberrantChargePercent;
        private static ForgeConfigSpec.IntValue aberrantStunTicks;
        private static ForgeConfigSpec.DoubleValue aberrantStunDamage;
        private static ForgeConfigSpec.DoubleValue aberrantFrenzyHealth;
        private static ForgeConfigSpec.DoubleValue aberrantFrenzySpeed;
        private static ForgeConfigSpec.DoubleValue aberrantQuakeAttack;
        private static ForgeConfigSpec.IntValue aberrantSplitCount;
        private static ForgeConfigSpec.IntValue aberrantLarvaLifetime;
        private static ForgeConfigSpec.IntValue aberrantBroodInterval;
        private static ForgeConfigSpec.IntValue aberrantBroodMax;
        private static ForgeConfigSpec.IntValue aberrantResidueTicks;
        private static ForgeConfigSpec.IntValue aberrantNearbyCap;

        private static ForgeConfigSpec.DoubleValue emberHealth;
        private static ForgeConfigSpec.DoubleValue emberArmor;
        private static ForgeConfigSpec.DoubleValue emberBoltDamage;
        private static ForgeConfigSpec.IntValue emberShootInterval;
        private static ForgeConfigSpec.IntValue emberShootRange;
        private static ForgeConfigSpec.IntValue emberMagicalDrain;
        private static ForgeConfigSpec.IntValue emberNearbyCap;

        private static ForgeConfigSpec.DoubleValue eelHealth;
        private static ForgeConfigSpec.DoubleValue eelArmor;
        private static ForgeConfigSpec.DoubleValue eelBiteDamage;
        private static ForgeConfigSpec.IntValue eelDragTicks;
        private static ForgeConfigSpec.DoubleValue eelDragSpeed;
        private static ForgeConfigSpec.IntValue eelMinDepth;
        private static ForgeConfigSpec.IntValue eelMinY;
        private static ForgeConfigSpec.IntValue eelMaxY;
        private static ForgeConfigSpec.IntValue eelNearbyCap;
        private static ForgeConfigSpec.IntValue eelEliteSummonCount;
        private static ForgeConfigSpec.IntValue eelEliteSlowTicks;
        private static ForgeConfigSpec.DoubleValue eelDryDamage;

        @Nullable
        private static volatile AberrantValues testAberrant;
        @Nullable
        private static volatile EmberValues testEmber;
        @Nullable
        private static volatile EelValues testEel;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            AberrantValues a = ABERRANT_DEFAULTS;
            builder.comment("Chaos Aberration: four-legged monster of the Chaos and Shadow Corruption in Hoanna; splits into Aberrant Larvae on death.",
                    "混沌畸变体：海纳尔混沌 / 暗影腐化之地的四足怪，死时裂出畸变幼体").push(List.of(ECMobFramework.CONFIG_SECTION, "chaos_aberrant"));
            aberrantHealth = builder.comment("Max health (set when it spawns).", "最大生命（生成时设好）")
                    .defineInRange("max_health", a.maxHealth(), 1D, 1024D);
            aberrantArmor = builder.comment("Armor (set when it spawns).", "护甲（生成时设好）")
                    .defineInRange("armor", a.armor(), 0D, 30D);
            aberrantAttack = builder.comment("Melee damage (set when it spawns; elites x elite_attack_multiplier).",
                            "普通近战伤害（生成时设好；精英再乘 elite_attack_multiplier）")
                    .defineInRange("attack_damage", a.attackDamage(), 0D, 100D);
            aberrantChargeCooldown = builder.comment("Ticks between two charges (0 = it never charges).", "两次冲撞之间隔几刻（0 = 不冲撞）")
                    .defineInRange("charge_cooldown", a.chargeCooldown(), 0, 12_000);
            aberrantChargeWindup = builder.comment("Ticks it lowers its head before charging; the path is marked on the ground meanwhile.",
                            "冲撞前低头蓄力几刻，这段时间地上画出冲撞路线")
                    .defineInRange("charge_windup", a.chargeWindup(), 1, 200);
            aberrantChargeDistance = builder.comment("Blocks one charge runs in a straight line.", "一次冲撞直线冲几格")
                    .defineInRange("charge_distance", a.chargeDistance(), 1D, 32D);
            aberrantChargeDamage = builder.comment("Flat damage of a charge (ignores armor; protection enchantments and Resistance still reduce it).",
                            "冲撞撞中的固定伤害（无视护甲，保护附魔、抗性效果照常减）")
                    .defineInRange("charge_damage", a.chargeDamage(), 0D, 100D);
            aberrantChargePercent = builder.comment("A charge also deals this share of the target's max health.", "冲撞另加目标最大生命的这个比例")
                    .defineInRange("charge_health_percent", a.chargeHealthPercent(), 0D, 0.2D);
            aberrantStunTicks = builder.comment("Ticks it is stunned after charging into a wall (0 = never stunned).", "冲撞撞墙后自己眩晕几刻（0 = 不眩晕）")
                    .defineInRange("stun_ticks", a.stunTicks(), 0, 400);
            aberrantStunDamage = builder.comment("Damage it takes while stunned is multiplied by this.", "眩晕时受到的伤害乘这个倍数")
                    .defineInRange("stun_damage_multiplier", a.stunDamageTaken(), 1D, 5D);
            aberrantFrenzyHealth = builder.comment("It goes into a frenzy when its health drops to this share of its max health (0 = never).",
                            "生命剩到最大生命的这个比例以下就狂暴（0 = 不会残血狂暴）")
                    .defineInRange("frenzy_health", a.frenzyHealth(), 0D, 1D);
            aberrantFrenzySpeed = builder.comment("Movement speed bonus while in a frenzy (0.3 = 30% faster).", "狂暴时移动速度加成（0.3 = 快 30%）")
                    .defineInRange("frenzy_speed", a.frenzySpeed(), 0D, 2D);
            aberrantQuakeAttack = builder.comment("Ones born during an earthquake are in a frenzy from the start and also get this much more attack (0.25 = 25%).",
                            "地震里出生的一出来就狂暴，攻击另高这么多（0.25 = 高 25%）")
                    .defineInRange("quake_attack_bonus", a.quakeAttack(), 0D, 2D);
            aberrantSplitCount = builder.comment("Larvae it splits into when it dies (0 = none). Only ones that spawned in the wild split.",
                            "死时裂出几只幼体（0 = 不裂）。只有野外刷出来的才裂")
                    .defineInRange("split_count", a.splitCount(), 0, 8);
            aberrantLarvaLifetime = builder.comment("Ticks a larva lives before it falls apart.", "幼体活多少刻后自己散掉")
                    .defineInRange("larva_lifetime", a.larvaLifetime(), 20, 12_000);
            aberrantBroodInterval = builder.comment("Ticks between two larvae spawned by the elite (Aberrant Broodmother) while fighting (0 = none).",
                            "畸变母巢打起来以后每隔几刻产一只幼体（0 = 不产）")
                    .defineInRange("brood_interval", a.broodInterval(), 0, 12_000);
            aberrantBroodMax = builder.comment("A Broodmother never has more of its own larvae alive than this.", "畸变母巢产的、还活着的幼体最多几只")
                    .defineInRange("brood_max", a.broodMax(), 0, 16);
            aberrantResidueTicks = builder.comment("Ticks the puddle of chaos residue stays where a Broodmother's charge ends (Slowness II inside; 0 = none).",
                            "畸变母巢冲撞落点那滩混沌残液留几刻（站在里面缓慢 II；0 = 不留）")
                    .defineInRange("residue_ticks", a.residueTicks(), 0, 1200);
            aberrantNearbyCap = builder.comment("Natural spawning stops when this many are within 48 blocks.", "自然生成时 48 格内已有这么多只就不刷")
                    .defineInRange("nearby_cap", a.nearbyCap(), 1, 64);
            builder.pop(2);

            EmberValues e = EMBER_DEFAULTS;
            builder.comment("Ember Spirit: floating cluster of embers in the Dreadlands and the Hoanna Desert; each one carries one kind of ember.",
                    "余烬灵：恐惧之地、海纳尔沙漠里悬浮的余烬团，每只带一种余烬").push(List.of(ECMobFramework.CONFIG_SECTION, "ember_spirit"));
            emberHealth = builder.comment("Max health (set when it spawns).", "最大生命（生成时设好）")
                    .defineInRange("max_health", e.maxHealth(), 1D, 1024D);
            emberArmor = builder.comment("Armor (set when it spawns).", "护甲（生成时设好）")
                    .defineInRange("armor", e.armor(), 0D, 30D);
            emberBoltDamage = builder.comment("Damage of one ember bolt (set when it spawns; elites x elite_attack_multiplier).",
                            "一颗余烬弹的伤害（生成时设好；精英再乘 elite_attack_multiplier）")
                    .defineInRange("bolt_damage", e.boltDamage(), 0D, 100D);
            emberShootInterval = builder.comment("Ticks between attacks (plus or minus 10).", "两次出手之间隔几刻（上下浮动 10 刻）")
                    .defineInRange("shoot_interval", e.shootInterval(), 20, 1200);
            emberShootRange = builder.comment("It only shoots at targets within this many blocks.", "多远以内才吐余烬弹（格）")
                    .defineInRange("shoot_range", e.shootRange(), 2, 64);
            emberMagicalDrain = builder.comment("MRU a magical ember bolt drains from the target's storage items (0 = none).",
                            "魔力余烬弹打中时从目标身上的储存物品抽多少 MRU（0 = 不抽）")
                    .defineInRange("magical_drain", e.magicalDrain(), 0, 100_000);
            emberNearbyCap = builder.comment("Natural spawning stops when this many are within 48 blocks.", "自然生成时 48 格内已有这么多只就不刷")
                    .defineInRange("nearby_cap", e.nearbyCap(), 1, 64);
            builder.pop(2);

            EelValues l = EEL_DEFAULTS;
            builder.comment("Abyssal Eel: monster of the Hoanna Sea that only fights in water and drags its prey down.",
                    "渊鳗：海纳尔之海的水中怪，只在水里打人，咬中往下拽").push(List.of(ECMobFramework.CONFIG_SECTION, "abyssal_eel"));
            eelHealth = builder.comment("Max health (set when it spawns).", "最大生命（生成时设好）")
                    .defineInRange("max_health", l.maxHealth(), 1D, 1024D);
            eelArmor = builder.comment("Armor (set when it spawns).", "护甲（生成时设好）")
                    .defineInRange("armor", l.armor(), 0D, 30D);
            eelBiteDamage = builder.comment("Damage of one bite (set when it spawns; elites x elite_attack_multiplier).",
                            "咬一口的伤害（生成时设好；精英再乘 elite_attack_multiplier）")
                    .defineInRange("bite_damage", l.biteDamage(), 0D, 100D);
            eelDragTicks = builder.comment("Ticks a bite drags the target down (0 = no drag).", "咬中后往下拽几刻（0 = 不拽）")
                    .defineInRange("drag_ticks", l.dragTicks(), 0, 200);
            eelDragSpeed = builder.comment("Top downward speed while dragged (blocks per tick).", "拽的时候往下拉的最大速度（格 / 刻）")
                    .defineInRange("drag_speed", l.dragSpeed(), 0D, 1D);
            eelMinDepth = builder.comment("Natural spawning needs the water column there to be at least this deep.", "自然生成要求那一格所在的水柱至少几格深")
                    .defineInRange("min_depth", l.minDepth(), 1, 64);
            eelMinY = builder.comment("Lowest Y for natural spawning.", "自然生成的最低高度")
                    .defineInRange("min_y", l.minY(), -64, 320);
            eelMaxY = builder.comment("Highest Y for natural spawning.", "自然生成的最高高度")
                    .defineInRange("max_y", l.maxY(), -64, 320);
            eelNearbyCap = builder.comment("Natural spawning stops when this many are within 64 blocks.", "自然生成时 64 格内已有这么多条就不刷")
                    .defineInRange("nearby_cap", l.nearbyCap(), 1, 64);
            eelEliteSummonCount = builder.comment("Eels the elite (Abyssal Eel Matriarch) calls in the first time it is hurt (0 = none).",
                            "渊鳗群首第一次挨打叫来几条（0 = 不叫）")
                    .defineInRange("elite_summon_count", l.eliteSummonCount(), 0, 8);
            eelEliteSlowTicks = builder.comment("Ticks of Slowness I a Matriarch's bite adds (0 = none).", "渊鳗群首咬中附带缓慢 I 几刻（0 = 不带）")
                    .defineInRange("elite_slow_ticks", l.eliteSlowTicks(), 0, 1200);
            eelDryDamage = builder.comment("Damage per second it takes out of water.", "离了水每秒掉几点血")
                    .defineInRange("dry_damage", l.dryDamage(), 0D, 20D);
            builder.pop(2);
        }

        /** 按配置版本号缓存的 aberrant()（tick 里每刻读，命中时不分配） */
        private static final CachedServerValues<AberrantValues> ABERRANT_CACHE = new CachedServerValues<>();

        /** 混沌畸变体现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static AberrantValues aberrant() {
            AberrantValues override = testAberrant;
            if (override != null) {
                return override;
            }
            if (aberrantHealth == null) {
                return ABERRANT_DEFAULTS;
            }
            return ABERRANT_CACHE.get(() -> new AberrantValues(ECConfig.get(aberrantHealth), ECConfig.get(aberrantArmor), ECConfig.get(aberrantAttack),
                    ECConfig.get(aberrantChargeCooldown), ECConfig.get(aberrantChargeWindup), ECConfig.get(aberrantChargeDistance),
                    ECConfig.get(aberrantChargeDamage), ECConfig.get(aberrantChargePercent), ECConfig.get(aberrantStunTicks),
                    ECConfig.get(aberrantStunDamage), ECConfig.get(aberrantFrenzyHealth), ECConfig.get(aberrantFrenzySpeed),
                    ECConfig.get(aberrantQuakeAttack), ECConfig.get(aberrantSplitCount), ECConfig.get(aberrantLarvaLifetime),
                    ECConfig.get(aberrantBroodInterval), ECConfig.get(aberrantBroodMax), ECConfig.get(aberrantResidueTicks),
                    ECConfig.get(aberrantNearbyCap)));
        }

        /** 按配置版本号缓存的 ember()（tick 里每刻读，命中时不分配） */
        private static final CachedServerValues<EmberValues> EMBER_CACHE = new CachedServerValues<>();

        /** 余烬灵现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static EmberValues ember() {
            EmberValues override = testEmber;
            if (override != null) {
                return override;
            }
            if (emberHealth == null) {
                return EMBER_DEFAULTS;
            }
            return EMBER_CACHE.get(() -> new EmberValues(ECConfig.get(emberHealth), ECConfig.get(emberArmor), ECConfig.get(emberBoltDamage), ECConfig.get(emberShootInterval),
                    ECConfig.get(emberShootRange), ECConfig.get(emberMagicalDrain), ECConfig.get(emberNearbyCap)));
        }

        /** 按配置版本号缓存的 eel()（tick 里每刻读，命中时不分配） */
        private static final CachedServerValues<EelValues> EEL_CACHE = new CachedServerValues<>();

        /** 渊鳗现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static EelValues eel() {
            EelValues override = testEel;
            if (override != null) {
                return override;
            }
            if (eelHealth == null) {
                return EEL_DEFAULTS;
            }
            return EEL_CACHE.get(() -> new EelValues(ECConfig.get(eelHealth), ECConfig.get(eelArmor), ECConfig.get(eelBiteDamage), ECConfig.get(eelDragTicks),
                    ECConfig.get(eelDragSpeed), ECConfig.get(eelMinDepth), ECConfig.get(eelMinY), ECConfig.get(eelMaxY), ECConfig.get(eelNearbyCap),
                    ECConfig.get(eelEliteSummonCount), ECConfig.get(eelEliteSlowTicks), ECConfig.get(eelDryDamage)));
        }

        /** 只给 GameTest：在同一段同步代码里设、测、置回 null（别用 ConfigValue.set，文件监视器会冲掉） */
        public static void setTestAberrant(@Nullable AberrantValues values) {
            testAberrant = values;
        }

        /** 只给 GameTest：同上 */
        public static void setTestEmber(@Nullable EmberValues values) {
            testEmber = values;
        }

        /** 只给 GameTest：同上 */
        public static void setTestEel(@Nullable EelValues values) {
            testEel = values;
        }
    }
}
