package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.CachedServerValues;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.hoanna.CatacombRevenantEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.InquisitoriumPatrollerEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.PatrollerDestroyerEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.RevenantBladeEntity;
import com.flameforge.essentialcraft5.hoanna.HoannaSpawnHooks;
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
 * 第十三批 13d-N3「审判庭巡卫、墓穴守灵、按群系生成表」（batch13.md §5.2、§6.4、§6.5、§9.4）：
 * <ul>
 *   <li>{@code inquisitorium_patroller} 审判庭巡卫（{@link InquisitoriumPatrollerEntity}）：半尺寸的全息人形，悬停；每 {@code destroyer_interval} 刻射一发
 *       巡卫护甲破坏者（{@code patroller_destroyer}，{@link PatrollerDestroyerEntity}：效果同全息投影的护甲破坏者），每 {@code strike_interval} 刻在目标脚下
 *       放一记 2 秒预警的小型轨道打击（4 点）。精英「审判庭执行机」一次三记。暗影腐化之地不看亮度刷；黑暗天灾时所有陆地群系追加（{@link HoannaSpawnHooks}）。
 *       <b>不掉故障核心</b>（第十九章的关键物品只从全息投影 / 哨兵来）。</li>
 *   <li>{@code catacomb_revenant} 墓穴守灵（{@link CatacombRevenantEntity}）：只在古代墓穴的拼件里刷（结构 {@code old_catacombs} 的 {@code spawn_overrides}），
 *       弹射物 30% 直接穿过、骨誓；精英「墓穴守灵长」放追踪灵刃（{@code revenant_blade}，{@link RevenantBladeEntity}）。13e 的宝库守卫、沉城卫兵也是它。</li>
 *   <li>刷怪蛋 {@code inquisitorium_patroller_spawn_egg}、{@code catacomb_revenant_spawn_egg}（只在创造栏；普通怪的蛋能改刷怪笼，改出来的来源是刷怪笼，关键掉落拿不到）。</li>
 *   <li>海纳尔刷怪钩子（{@link HoannaSpawnHooks}：潮汐系数、黑暗巡卫、风暴棱晶、毒雾拾荒者、天灾精英）的配置也在这里（{@code general.mobs.hoanna_spawns}）；
 *       按群系的刷怪表是 {@code tools/gen/hoanna_spawns.py} 生成的群系 JSON，五种巢穴类型和奖励表是 {@code tools/gen/hoanna_nest_loot.py} 生成的数据。</li>
 * </ul>
 * 掉落表 {@code data/essentialcraft5/loot_tables/entities/{inquisitorium_patroller, catacomb_revenant}.json}（公平击杀 + 按人递减，精英多一池）；
 * 文字 tools/lang/keys/echoannanativesc.json；音效 tools/sounds/echoannanativesc.json（都指原版声音、改音高）；客户端 client/HoannaNativesCClient；
 * 测试 gametest/HoannaNativesCTests、gametest/HoannaSpawnTests。服务端配置 {@code general.mobs.inquisitorium_patroller}、{@code catacomb_revenant}、
 * {@code hoanna_spawns}（{@link Config}）。
 */
public final class ECHoannaNativesC {

    /** 审判庭巡卫（暗影腐化之地；黑暗天灾时全陆地）：碰撞箱照全息人形缩 0.6 */
    public static final RegistryObject<EntityType<InquisitoriumPatrollerEntity>> INQUISITORIUM_PATROLLER = ECEntities.register("inquisitorium_patroller",
            EntityType.Builder.<InquisitoriumPatrollerEntity>of(InquisitoriumPatrollerEntity::new, MobCategory.MONSTER).sized(0.6F, 1.2F).clientTrackingRange(8));

    /** 墓穴守灵（古代墓穴，结构里的 spawn_overrides） */
    public static final RegistryObject<EntityType<CatacombRevenantEntity>> CATACOMB_REVENANT = ECEntities.register("catacomb_revenant",
            EntityType.Builder.<CatacombRevenantEntity>of(CatacombRevenantEntity::new, MobCategory.MONSTER).sized(0.6F, 1.99F).clientTrackingRange(8));

    /** 巡卫射的护甲破坏者：尺寸、同步照全息投影的那一发；不存盘、不能 /summon（没有射手就认不出该打谁） */
    public static final RegistryObject<EntityType<PatrollerDestroyerEntity>> PATROLLER_DESTROYER = ECEntities.register("patroller_destroyer",
            EntityType.Builder.<PatrollerDestroyerEntity>of(PatrollerDestroyerEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).noSave().noSummon().clientTrackingRange(4).updateInterval(10));

    /** 墓穴守灵长的追踪灵刃：能被打掉，碰撞箱比一般弹射物大一点；拐弯快，同步勤一点；不存盘、不能 /summon */
    public static final RegistryObject<EntityType<RevenantBladeEntity>> REVENANT_BLADE = ECEntities.register("revenant_blade",
            EntityType.Builder.<RevenantBladeEntity>of(RevenantBladeEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).noSave().noSummon().clientTrackingRange(4).updateInterval(2));

    public static final RegistryObject<ECEntities.SpawnEgg> INQUISITORIUM_PATROLLER_SPAWN_EGG =
            ECEntities.spawnEgg("inquisitorium_patroller_spawn_egg", INQUISITORIUM_PATROLLER, 0x1C2F3A, 0x7FE8FF, true);
    public static final RegistryObject<ECEntities.SpawnEgg> CATACOMB_REVENANT_SPAWN_EGG =
            ECEntities.spawnEgg("catacomb_revenant_spawn_egg", CATACOMB_REVENANT, 0x4A453B, 0x6FE3D8, true);

    // 音效（定义在 tools/sounds/echoannanativesc.json，全部指原版声音改音高 / 音量）
    public static final RegistryObject<SoundEvent> SOUND_PATROLLER_AMBIENT = ECSounds.register("sound.mob.inquisitorium_patroller.ambient");
    public static final RegistryObject<SoundEvent> SOUND_PATROLLER_HURT = ECSounds.register("sound.mob.inquisitorium_patroller.hurt");
    public static final RegistryObject<SoundEvent> SOUND_PATROLLER_DEATH = ECSounds.register("sound.mob.inquisitorium_patroller.death");
    public static final RegistryObject<SoundEvent> SOUND_PATROLLER_SHOOT = ECSounds.register("sound.mob.inquisitorium_patroller.shoot");
    public static final RegistryObject<SoundEvent> SOUND_PATROLLER_MARK = ECSounds.register("sound.mob.inquisitorium_patroller.mark");
    public static final RegistryObject<SoundEvent> SOUND_REVENANT_AMBIENT = ECSounds.register("sound.mob.catacomb_revenant.ambient");
    public static final RegistryObject<SoundEvent> SOUND_REVENANT_HURT = ECSounds.register("sound.mob.catacomb_revenant.hurt");
    public static final RegistryObject<SoundEvent> SOUND_REVENANT_DEATH = ECSounds.register("sound.mob.catacomb_revenant.death");
    public static final RegistryObject<SoundEvent> SOUND_REVENANT_STEP = ECSounds.register("sound.mob.catacomb_revenant.step");
    public static final RegistryObject<SoundEvent> SOUND_REVENANT_OATH = ECSounds.register("sound.mob.catacomb_revenant.oath");
    public static final RegistryObject<SoundEvent> SOUND_REVENANT_BLADES = ECSounds.register("sound.mob.catacomb_revenant.blades");
    public static final RegistryObject<SoundEvent> SOUND_BLADE_BREAK = ECSounds.register("sound.mob.revenant_blade.break");

    private ECHoannaNativesC() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；属性、刷怪位置、配置在这里登记 */
    public static void init() {
        ECEntities.attributes(INQUISITORIUM_PATROLLER, InquisitoriumPatrollerEntity::createAttributes);
        ECEntities.attributes(CATACOMB_REVENANT, CatacombRevenantEntity::createAttributes);
        // 巡卫不看亮度（全息自己发光），附近上限平时 48 格、黑暗天灾时 64 格；守灵只经墓穴的结构覆盖刷，光照 ≤ 7、结构内 32 格 ≤ 8
        ECEntities.spawnPlacement(INQUISITORIUM_PATROLLER, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                InquisitoriumPatrollerEntity::checkPatrollerSpawnRules);
        ECEntities.spawnPlacement(CATACOMB_REVENANT, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CatacombRevenantEntity.SPAWN_RULES);
        ECMonster.addEliteChanceMultiplier(HoannaSpawnHooks::eliteChanceMultiplier);
        ECConfig.general(Config::define);
    }

    /**
     * 服务端配置 {@code general.mobs.inquisitorium_patroller}、{@code catacomb_revenant}、{@code hoanna_spawns}。生命、护甲、攻击在生成时
     * （{@code finalizeSpawn}）按配置设成基础值，已经在世界里的怪不跟着改；别的数值每次用到时读。读数一律走 {@link #patroller()} / {@link #revenant()} /
     * {@link #spawns()}（配置没加载时给默认值，GameTest 的覆盖值优先）。
     */
    public static final class Config {

        /**
         * 审判庭巡卫的一套数值。
         *
         * @param maxHealth         最大生命
         * @param armor             护甲
         * @param destroyerInterval 两发护甲破坏者之间隔几刻（0 = 不射）
         * @param destroyerRange    多远以内才射（格）
         * @param strikeInterval    两次定标之间隔几刻（0 = 不定标）
         * @param strikeWarning     定标的预警（光柱立起到落下）多少刻
         * @param strikeDamage      一记轨道打击的伤害（固定值，精英不乘）
         * @param strikeRange       多远以内才定标（格）
         * @param eliteStrikes      审判庭执行机一次定几记
         * @param eliteStrikeGap    执行机连着定标，两记之间隔几刻
         * @param maxStrikes        每只同时最多几记还没落下的轨道打击
         * @param nearbyCap         自然生成时附近（平时 48 格）同种最多几只
         */
        public record PatrollerValues(double maxHealth, double armor, int destroyerInterval, int destroyerRange, int strikeInterval, int strikeWarning,
                                      double strikeDamage, int strikeRange, int eliteStrikes, int eliteStrikeGap, int maxStrikes, int nearbyCap) {
        }

        /** batch13.md §5.2：24 / 0；怪物稿 §2.4.7：每 80 刻一发破坏者、每 120 刻定标、2 秒预警、4 点、执行机三连隔 10 刻、同时 ≤ 3 记；48 格 ≤ 6 */
        public static final PatrollerValues PATROLLER_DEFAULTS = new PatrollerValues(24D, 0D, 80, 16, 120, 40, 4D, 24, 3, 10, 3, 6);

        /**
         * 墓穴守灵的一套数值。
         *
         * @param maxHealth      最大生命
         * @param armor          护甲
         * @param attackDamage   剑的伤害（精英 ×1.4）
         * @param passChance     弹射物从它身上直接穿过（不受伤）的几率
         * @param oathRadius     骨誓：一只死时多少格内的同伴得到力量
         * @param oathTicks      骨誓的力量 I 持续多少刻（0 = 关掉骨誓）
         * @param bladeInterval  墓穴守灵长两次放灵刃之间隔几刻（0 = 不放）
         * @param bladeCount     墓穴守灵长一次放几把灵刃（同时活着的也不超过这个数）
         * @param bladeDamage    一把灵刃打中的伤害
         * @param bladeHoming    灵刃追踪多少刻（之后直着飞，再过 40 刻散掉）
         * @param maxLight       刷怪处的亮度不超过这个数才刷
         * @param nearbyCap      自然生成时 32 格内同种最多几只
         */
        public record RevenantValues(double maxHealth, double armor, double attackDamage, double passChance, int oathRadius, int oathTicks,
                                     int bladeInterval, int bladeCount, double bladeDamage, int bladeHoming, int maxLight, int nearbyCap) {
        }

        /** batch13.md §5.2：40 / 10；怪物稿 §2.4.8：剑 9、弹射物 30% 穿过、骨誓 8 格力量 I 10 秒、灵刃每 12 秒 2 把 5 点追踪 3 秒；光照 ≤ 7、32 格 ≤ 8 */
        public static final RevenantValues REVENANT_DEFAULTS = new RevenantValues(40D, 10D, 9D, 0.3D, 8, 200, 240, 2, 5D, 60, 7, 8);

        /**
         * 海纳尔刷怪钩子（{@link HoannaSpawnHooks}）的一套数值。
         *
         * @param tideWeights                本地怪权重按魔力潮汐 ×(0.75 + 0.5T)
         * @param darknessPatrollerWeight    黑暗天灾时所有陆地群系追加的审判庭巡卫权重（0 = 不追加）
         * @param darknessPatrollerMaxNearby 黑暗天灾时 64 格内巡卫最多几只（追加的和群系本来的一起算）
         * @param stormPrismWeight           地脉风暴时所有陆地群系追加的浮游棱晶权重（0 = 不追加）
         * @param stormPrismMaxNearby        地脉风暴时 64 格内棱晶满这个数就不再追加
         * @param fumesScavengerMultiplier   毒雾时遗民拾荒者的权重倍数
         * @param calamityEliteMultiplier    海纳尔天灾（含地脉风暴）期间本模组怪的精英几率倍数
         */
        public record SpawnValues(boolean tideWeights, int darknessPatrollerWeight, int darknessPatrollerMaxNearby, int stormPrismWeight,
                                  int stormPrismMaxNearby, double fumesScavengerMultiplier, double calamityEliteMultiplier) {
        }

        /** batch13.md §6.5：潮汐开、黑暗巡卫 60 / 6、风暴棱晶 60 / 4（计划没给权重，取和黑暗巡卫同量级）、毒雾拾荒者 ×2、天灾精英 ×2 */
        public static final SpawnValues SPAWN_DEFAULTS = new SpawnValues(true, 60, 6, 60, 4, 2D, 2D);

        private static ForgeConfigSpec.DoubleValue patrollerHealth;
        private static ForgeConfigSpec.DoubleValue patrollerArmor;
        private static ForgeConfigSpec.IntValue patrollerDestroyerInterval;
        private static ForgeConfigSpec.IntValue patrollerDestroyerRange;
        private static ForgeConfigSpec.IntValue patrollerStrikeInterval;
        private static ForgeConfigSpec.IntValue patrollerStrikeWarning;
        private static ForgeConfigSpec.DoubleValue patrollerStrikeDamage;
        private static ForgeConfigSpec.IntValue patrollerStrikeRange;
        private static ForgeConfigSpec.IntValue patrollerEliteStrikes;
        private static ForgeConfigSpec.IntValue patrollerEliteStrikeGap;
        private static ForgeConfigSpec.IntValue patrollerMaxStrikes;
        private static ForgeConfigSpec.IntValue patrollerNearbyCap;

        private static ForgeConfigSpec.DoubleValue revenantHealth;
        private static ForgeConfigSpec.DoubleValue revenantArmor;
        private static ForgeConfigSpec.DoubleValue revenantAttack;
        private static ForgeConfigSpec.DoubleValue revenantPassChance;
        private static ForgeConfigSpec.IntValue revenantOathRadius;
        private static ForgeConfigSpec.IntValue revenantOathTicks;
        private static ForgeConfigSpec.IntValue revenantBladeInterval;
        private static ForgeConfigSpec.IntValue revenantBladeCount;
        private static ForgeConfigSpec.DoubleValue revenantBladeDamage;
        private static ForgeConfigSpec.IntValue revenantBladeHoming;
        private static ForgeConfigSpec.IntValue revenantMaxLight;
        private static ForgeConfigSpec.IntValue revenantNearbyCap;

        private static ForgeConfigSpec.BooleanValue spawnTideWeights;
        private static ForgeConfigSpec.IntValue spawnDarknessWeight;
        private static ForgeConfigSpec.IntValue spawnDarknessMaxNearby;
        private static ForgeConfigSpec.IntValue spawnStormWeight;
        private static ForgeConfigSpec.IntValue spawnStormMaxNearby;
        private static ForgeConfigSpec.DoubleValue spawnFumesScavenger;
        private static ForgeConfigSpec.DoubleValue spawnCalamityElite;

        @Nullable
        private static volatile PatrollerValues testPatroller;
        @Nullable
        private static volatile RevenantValues testRevenant;
        @Nullable
        private static volatile SpawnValues testSpawns;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            PatrollerValues p = PATROLLER_DEFAULTS;
            builder.comment("Inquisitorium Patroller: hovering half-size hologram of the Shadow Corruption; during Darkness it appears on all Hoanna land.",
                    "审判庭巡卫：暗影腐化之地悬停的半尺寸全息人形；黑暗天灾时海纳尔所有陆地都会出现").push(List.of(ECMobFramework.CONFIG_SECTION, "inquisitorium_patroller"));
            patrollerHealth = builder.comment("Max health (set when it spawns).", "最大生命（生成时设好）")
                    .defineInRange("max_health", p.maxHealth(), 1D, 1024D);
            patrollerArmor = builder.comment("Armor (set when it spawns).", "护甲（生成时设好）")
                    .defineInRange("armor", p.armor(), 0D, 30D);
            patrollerDestroyerInterval = builder.comment("Ticks between two armor destroyers (0 = never shoots them).",
                            "两发护甲破坏者之间隔几刻（0 = 不射）")
                    .defineInRange("destroyer_interval", p.destroyerInterval(), 0, 12_000);
            patrollerDestroyerRange = builder.comment("It only shoots armor destroyers at targets within this many blocks.", "多远以内才射护甲破坏者（格）")
                    .defineInRange("destroyer_range", p.destroyerRange(), 2, 64);
            patrollerStrikeInterval = builder.comment("Ticks between two target markings, each calling a small orbital strike (0 = never).",
                            "两次定标（召一记小型轨道打击）之间隔几刻（0 = 不定标）")
                    .defineInRange("strike_interval", p.strikeInterval(), 0, 12_000);
            patrollerStrikeWarning = builder.comment("Ticks between the marking beam appearing and the strike landing.", "定标的光柱立起到落下隔几刻（预警）")
                    .defineInRange("strike_warning_ticks", p.strikeWarning(), 20, 200);
            patrollerStrikeDamage = builder.comment("Damage of one small orbital strike (a fixed amount; elites do not multiply it).",
                            "一记小型轨道打击的伤害（固定值，精英不乘）")
                    .defineInRange("strike_damage", p.strikeDamage(), 0D, 100D);
            patrollerStrikeRange = builder.comment("It only marks targets within this many blocks.", "多远以内才定标（格）")
                    .defineInRange("strike_range", p.strikeRange(), 4, 64);
            patrollerEliteStrikes = builder.comment("Strikes the elite (Inquisitorium Executor) calls per marking.", "审判庭执行机一次定几记")
                    .defineInRange("elite_strikes", p.eliteStrikes(), 1, 8);
            patrollerEliteStrikeGap = builder.comment("Ticks between the elite's strikes in one marking.", "执行机一次定几记时，两记之间隔几刻")
                    .defineInRange("elite_strike_gap", p.eliteStrikeGap(), 1, 100);
            patrollerMaxStrikes = builder.comment("Strikes one patroller may have waiting to land at the same time.", "每只同时最多几记还没落下的轨道打击")
                    .defineInRange("max_strikes", p.maxStrikes(), 1, 16);
            patrollerNearbyCap = builder.comment("Natural spawning stops when this many patrollers are within 48 blocks (64 during Darkness, see hoanna_spawns).",
                            "自然生成时 48 格内已有这么多只就不刷（黑暗天灾时按 hoanna_spawns 的 64 格上限）")
                    .defineInRange("nearby_cap", p.nearbyCap(), 1, 64);
            builder.pop(2);

            RevenantValues r = REVENANT_DEFAULTS;
            builder.comment("Catacomb Revenant: spirit knight that only spawns inside the pieces of Old Catacombs.",
                    "墓穴守灵：只在古代墓穴拼件里刷的灵体骑士").push(List.of(ECMobFramework.CONFIG_SECTION, "catacomb_revenant"));
            revenantHealth = builder.comment("Max health (set when it spawns).", "最大生命（生成时设好）")
                    .defineInRange("max_health", r.maxHealth(), 1D, 1024D);
            revenantArmor = builder.comment("Armor (set when it spawns).", "护甲（生成时设好）")
                    .defineInRange("armor", r.armor(), 0D, 30D);
            revenantAttack = builder.comment("Sword damage (set when it spawns; elites x elite_attack_multiplier).",
                            "剑的伤害（生成时设好；精英再乘 elite_attack_multiplier）")
                    .defineInRange("attack_damage", r.attackDamage(), 0D, 100D);
            revenantPassChance = builder.comment("Chance that a projectile passes straight through it without hurting it (decided once per projectile).",
                            "弹射物从它身上直接穿过、不受伤的几率（每一发只判一次）")
                    .defineInRange("projectile_pass_chance", r.passChance(), 0D, 1D);
            revenantOathRadius = builder.comment("Bone Oath: when one dies, revenants within this many blocks gain Strength.", "骨誓：一只死时多少格内的同伴得到力量")
                    .defineInRange("oath_radius", r.oathRadius(), 1, 32);
            revenantOathTicks = builder.comment("Ticks the Strength I of the Bone Oath lasts (0 = no Bone Oath).", "骨誓的力量 I 持续多少刻（0 = 关掉骨誓）")
                    .defineInRange("oath_ticks", r.oathTicks(), 0, 6000);
            revenantBladeInterval = builder.comment("Ticks between two volleys of spirit blades from the elite (Revenant Warden) (0 = never).",
                            "墓穴守灵长两次放灵刃之间隔几刻（0 = 不放）")
                    .defineInRange("blade_interval", r.bladeInterval(), 0, 12_000);
            revenantBladeCount = builder.comment("Spirit blades per volley; it never has more of its own in the air than this.", "一次放几把灵刃；它放出的、还在飞的不会超过这个数")
                    .defineInRange("blade_count", r.bladeCount(), 1, 8);
            revenantBladeDamage = builder.comment("Damage of one spirit blade.", "一把灵刃打中的伤害")
                    .defineInRange("blade_damage", r.bladeDamage(), 0D, 100D);
            revenantBladeHoming = builder.comment("Ticks a spirit blade keeps turning towards its target; afterwards it flies straight and fades 40 ticks later.",
                            "灵刃追踪多少刻；之后直着飞，再过 40 刻散掉")
                    .defineInRange("blade_homing_ticks", r.bladeHoming(), 1, 400);
            revenantMaxLight = builder.comment("It only spawns where the light level is at most this.", "刷怪处的亮度不超过这个数才刷")
                    .defineInRange("max_light", r.maxLight(), 0, 15);
            revenantNearbyCap = builder.comment("Natural spawning stops when this many revenants are within 32 blocks.", "自然生成时 32 格内已有这么多只就不刷")
                    .defineInRange("nearby_cap", r.nearbyCap(), 1, 64);
            builder.pop(2);

            SpawnValues s = SPAWN_DEFAULTS;
            builder.comment("Hoanna spawn hooks: what changes the monster spawn lists of Hoanna at runtime (the base lists are biome JSON data packs can override).",
                    "海纳尔刷怪钩子：运行时改海纳尔怪物刷怪表的几条规则（基础刷怪表是群系 JSON，数据包可以覆盖）").push(List.of(ECMobFramework.CONFIG_SECTION, "hoanna_spawns"));
            spawnTideWeights = builder.comment("Weights of the Hoanna natives follow the mana tide: x(0.75 + 0.5 x tide), so x1.25 at full moon and x0.75 at new moon.",
                            "海纳尔本地怪的权重跟着魔力潮汐变：×(0.75 + 0.5 × 潮位)，满月 ×1.25、新月 ×0.75")
                    .define("tide_weights", s.tideWeights());
            spawnDarknessWeight = builder.comment("Weight of the Inquisitorium Patroller added to every Hoanna land biome during Darkness (0 = none).",
                            "黑暗天灾时往海纳尔每个陆地群系追加的审判庭巡卫权重（0 = 不追加）")
                    .defineInRange("darkness_patroller_weight", s.darknessPatrollerWeight(), 0, 1000);
            spawnDarknessMaxNearby = builder.comment("During Darkness, patrollers stop spawning when this many are within 64 blocks.",
                            "黑暗天灾时 64 格内已有这么多只巡卫就不再刷")
                    .defineInRange("darkness_patroller_max_nearby", s.darknessPatrollerMaxNearby(), 1, 64);
            spawnStormWeight = builder.comment("Weight of the Mana Prism added to every Hoanna land biome during a Ley Storm (0 = none).",
                            "地脉风暴时往海纳尔每个陆地群系追加的浮游棱晶权重（0 = 不追加）")
                    .defineInRange("storm_prism_weight", s.stormPrismWeight(), 0, 1000);
            spawnStormMaxNearby = builder.comment("During a Ley Storm, no prisms are added where this many are already within 64 blocks.",
                            "地脉风暴时 64 格内已有这么多只棱晶，就不再追加")
                    .defineInRange("storm_prism_max_nearby", s.stormPrismMaxNearby(), 1, 64);
            spawnFumesScavenger = builder.comment("Weight multiplier of the Hoanna Scavenger during Fumes (0 = no scavengers spawn during Fumes).",
                            "毒雾时遗民拾荒者的权重倍数（0 = 毒雾期间不刷拾荒者）")
                    .defineInRange("fumes_scavenger_multiplier", s.fumesScavengerMultiplier(), 0D, 10D);
            spawnCalamityElite = builder.comment("Elite chance multiplier for this mod's monsters in Hoanna while any calamity (Ley Storm included) is going on.",
                            "海纳尔天灾（含地脉风暴）期间本模组怪的精英几率倍数")
                    .defineInRange("calamity_elite_multiplier", s.calamityEliteMultiplier(), 0D, 10D);
            builder.pop(2);
        }

        /** 按配置版本号缓存的 patroller()（tick 里每刻读，命中时不分配） */
        private static final CachedServerValues<PatrollerValues> PATROLLER_CACHE = new CachedServerValues<>();

        /** 审判庭巡卫现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static PatrollerValues patroller() {
            PatrollerValues override = testPatroller;
            if (override != null) {
                return override;
            }
            if (patrollerHealth == null) {
                return PATROLLER_DEFAULTS;
            }
            return PATROLLER_CACHE.get(() -> new PatrollerValues(ECConfig.get(patrollerHealth), ECConfig.get(patrollerArmor), ECConfig.get(patrollerDestroyerInterval),
                    ECConfig.get(patrollerDestroyerRange), ECConfig.get(patrollerStrikeInterval), ECConfig.get(patrollerStrikeWarning),
                    ECConfig.get(patrollerStrikeDamage), ECConfig.get(patrollerStrikeRange), ECConfig.get(patrollerEliteStrikes),
                    ECConfig.get(patrollerEliteStrikeGap), ECConfig.get(patrollerMaxStrikes), ECConfig.get(patrollerNearbyCap)));
        }

        /** 按配置版本号缓存的 revenant()（tick 里每刻读，命中时不分配） */
        private static final CachedServerValues<RevenantValues> REVENANT_CACHE = new CachedServerValues<>();

        /** 墓穴守灵现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static RevenantValues revenant() {
            RevenantValues override = testRevenant;
            if (override != null) {
                return override;
            }
            if (revenantHealth == null) {
                return REVENANT_DEFAULTS;
            }
            return REVENANT_CACHE.get(() -> new RevenantValues(ECConfig.get(revenantHealth), ECConfig.get(revenantArmor), ECConfig.get(revenantAttack),
                    ECConfig.get(revenantPassChance), ECConfig.get(revenantOathRadius), ECConfig.get(revenantOathTicks),
                    ECConfig.get(revenantBladeInterval), ECConfig.get(revenantBladeCount), ECConfig.get(revenantBladeDamage),
                    ECConfig.get(revenantBladeHoming), ECConfig.get(revenantMaxLight), ECConfig.get(revenantNearbyCap)));
        }

        /** 按配置版本号缓存的 spawns()（tick 里每刻读，命中时不分配） */
        private static final CachedServerValues<SpawnValues> SPAWNS_CACHE = new CachedServerValues<>();

        /** 刷怪钩子现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static SpawnValues spawns() {
            SpawnValues override = testSpawns;
            if (override != null) {
                return override;
            }
            if (spawnTideWeights == null) {
                return SPAWN_DEFAULTS;
            }
            return SPAWNS_CACHE.get(() -> new SpawnValues(ECConfig.get(spawnTideWeights), ECConfig.get(spawnDarknessWeight), ECConfig.get(spawnDarknessMaxNearby),
                    ECConfig.get(spawnStormWeight), ECConfig.get(spawnStormMaxNearby), ECConfig.get(spawnFumesScavenger),
                    ECConfig.get(spawnCalamityElite)));
        }

        /** 只给 GameTest：在同一段同步代码里设、测、置回 null（别用 ConfigValue.set，文件监视器会冲掉） */
        public static void setTestPatroller(@Nullable PatrollerValues values) {
            testPatroller = values;
        }

        /** 只给 GameTest：同上 */
        public static void setTestRevenant(@Nullable RevenantValues values) {
            testRevenant = values;
        }

        /** 只给 GameTest：同上 */
        public static void setTestSpawns(@Nullable SpawnValues values) {
            testSpawns = values;
        }
    }
}
