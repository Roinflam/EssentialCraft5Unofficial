package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.StormGustEntity;
import com.flameforge.essentialcraft5.entity.mob.ElementalWispEntity;
import com.flameforge.essentialcraft5.entity.mob.MatrixWraithEntity;
import com.flameforge.essentialcraft5.entity.mob.WispBoltEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第十三批 13c-M1「元素游灵、矩阵游魂、风暴术士」的注册类（batch13.md §5.2、§9.3）：
 * <ul>
 *   <li>实体：{@code elemental_wisp}（元素游灵，{@link ElementalWispEntity}）、{@code matrix_wraith}（矩阵游魂，{@link MatrixWraithEntity}），
 *       两种弹射物 {@code wisp_bolt}（元素聚灵弹，{@link WispBoltEntity}）、{@code storm_gust}（疾风弹，{@link StormGustEntity}）；
 *       刷怪蛋 {@code elemental_wisp_spawn_egg}、{@code matrix_wraith_spawn_egg}（只放创造栏，policy §4.7；普通怪的蛋能改刷怪笼，来源记刷怪笼，关键掉落拿不到）。
 *       风暴术士是风法师的第 4 种（{@code entity/WindMageEntity} 的 type 3），不另注册实体，规矩在 {@code entity/StormSorcerer}。</li>
 *   <li>生成：元素游灵走 {@code forge:add_spawns} + {@code essentialcraft5:add_spawn_costs}（数据在 forge/biome_modifier/elemental_wisp_*.json），
 *       刷怪判定是 {@link ElementalWispEntity#SPAWN_RULES}；矩阵游魂不进群系刷怪表，附近有够大的聚集体时由 {@code entity/mob/MatrixWraithEvents}
 *       往那一格的怪物刷怪表里追加。</li>
 *   <li>音效 {@code sound.mob.<怪>.<动作>}，定义在 tools/sounds/ecwildmobs.json（都指原版声音、改音高 / 音量），字幕和名字在 tools/lang/keys/ecwildmobs.json。</li>
 * </ul>
 * 模型层和渲染器在 {@code client/WildMobsClient}。服务端配置 {@code general.mobs.{elemental_wisp, matrix_wraith, storm_sorcerer}}（{@link Config}）。
 */
public final class ECWildMobs {

    // ------------------------------------------------------------------ 实体

    public static final RegistryObject<EntityType<ElementalWispEntity>> ELEMENTAL_WISP = ECEntities.register("elemental_wisp",
            EntityType.Builder.of(ElementalWispEntity::new, MobCategory.MONSTER).sized(0.6F, 0.8F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<MatrixWraithEntity>> MATRIX_WRAITH = ECEntities.register("matrix_wraith",
            EntityType.Builder.of(MatrixWraithEntity::new, MobCategory.MONSTER).sized(0.6F, 1.9F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<WispBoltEntity>> WISP_BOLT = ECEntities.register("wisp_bolt",
            EntityType.Builder.<WispBoltEntity>of(WispBoltEntity::new, MobCategory.MISC).sized(0.3125F, 0.3125F).clientTrackingRange(4).updateInterval(10));
    public static final RegistryObject<EntityType<StormGustEntity>> STORM_GUST = ECEntities.register("storm_gust",
            EntityType.Builder.<StormGustEntity>of(StormGustEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(10));

    public static final RegistryObject<ECEntities.SpawnEgg> ELEMENTAL_WISP_SPAWN_EGG =
            ECEntities.spawnEgg("elemental_wisp_spawn_egg", ELEMENTAL_WISP, 0xD6DEE6, 0x6FC7FF, true);
    public static final RegistryObject<ECEntities.SpawnEgg> MATRIX_WRAITH_SPAWN_EGG =
            ECEntities.spawnEgg("matrix_wraith_spawn_egg", MATRIX_WRAITH, 0x2B2238, 0xB45CFF, true);

    // ------------------------------------------------------------------ 音效

    public static final RegistryObject<SoundEvent> WISP_AMBIENT = ECSounds.register("sound.mob.elemental_wisp.ambient");
    public static final RegistryObject<SoundEvent> WISP_HURT = ECSounds.register("sound.mob.elemental_wisp.hurt");
    public static final RegistryObject<SoundEvent> WISP_DEATH = ECSounds.register("sound.mob.elemental_wisp.death");
    public static final RegistryObject<SoundEvent> WISP_CHARGE = ECSounds.register("sound.mob.elemental_wisp.charge");
    public static final RegistryObject<SoundEvent> WISP_DASH = ECSounds.register("sound.mob.elemental_wisp.dash");
    public static final RegistryObject<SoundEvent> WISP_BOLT_SHOOT = ECSounds.register("sound.mob.elemental_wisp.bolt");
    public static final RegistryObject<SoundEvent> WRAITH_AMBIENT = ECSounds.register("sound.mob.matrix_wraith.ambient");
    public static final RegistryObject<SoundEvent> WRAITH_HURT = ECSounds.register("sound.mob.matrix_wraith.hurt");
    public static final RegistryObject<SoundEvent> WRAITH_DEATH = ECSounds.register("sound.mob.matrix_wraith.death");
    public static final RegistryObject<SoundEvent> WRAITH_DRAIN = ECSounds.register("sound.mob.matrix_wraith.drain");
    public static final RegistryObject<SoundEvent> WRAITH_BLINK = ECSounds.register("sound.mob.matrix_wraith.blink");
    public static final RegistryObject<SoundEvent> STORM_GUST_SHOOT = ECSounds.register("sound.mob.storm_sorcerer.gust");
    public static final RegistryObject<SoundEvent> STORM_MARK = ECSounds.register("sound.mob.storm_sorcerer.mark");

    private ECWildMobs() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；属性、刷怪位置、配置在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
        ECEntities.attributes(ELEMENTAL_WISP, ElementalWispEntity::createAttributes);
        ECEntities.attributes(MATRIX_WRAITH, MatrixWraithEntity::createAttributes);
        ECEntities.spawnPlacement(ELEMENTAL_WISP, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ElementalWispEntity.SPAWN_RULES);
        ECEntities.spawnPlacement(MATRIX_WRAITH, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MatrixWraithEntity::checkWraithSpawnRules);
    }

    /**
     * 服务端配置 {@code general.mobs} 下的三个子小节。读数一律走这里的静态方法：配置没加载时给默认值，GameTest 用 {@link #setTest} 钉值（优先）。
     * 刷怪权重、每群数量、刷怪花费是数据（forge/biome_modifier），不在这里；矩阵游魂不进刷怪表，它的权重在这里。
     * 两种怪的基础生命、攻击、护甲是实体属性（注册时就定了，配置那时还没读），风暴术士是风法师加属性修饰符，所以能配。
     */
    public static final class Config {

        // 元素游灵
        public static final int DEFAULT_WISP_MAX_Y = 40;
        public static final int DEFAULT_WISP_NEARBY_RADIUS = 32;
        public static final int DEFAULT_WISP_MAX_NEARBY = 4;
        public static final int DEFAULT_WISP_EFFECT_SECONDS = 3;
        public static final int DEFAULT_WISP_BOLT_INTERVAL = 40;
        // 矩阵游魂
        public static final int DEFAULT_WRAITH_MATRIX_DAMAGE = 150;
        public static final int DEFAULT_WRAITH_PRESENCE_RADIUS = 24;
        public static final int DEFAULT_WRAITH_MIN_PRESENCE_MRU = 5000;
        public static final int DEFAULT_WRAITH_SPAWN_WEIGHT = 30;
        public static final int DEFAULT_WRAITH_MAX_PER_PRESENCE = 3;
        public static final int DEFAULT_WRAITH_DRAIN_MRU = 300;
        public static final int DEFAULT_WRAITH_BLINK_COOLDOWN = 160;
        // 风暴术士
        public static final double DEFAULT_STORM_THUNDER_CHANCE = 0.2D;
        public static final double DEFAULT_STORM_HOANNA_CHANCE = 0.1D;
        public static final double DEFAULT_STORM_HEALTH = 50D;
        public static final double DEFAULT_STORM_ARMOR = 2D;
        public static final double DEFAULT_STORM_GUST_DAMAGE = 4D;
        public static final double DEFAULT_STORM_GUST_KNOCKBACK = 1.2D;
        public static final double DEFAULT_STORM_LIGHTNING_DAMAGE = 8D;
        public static final int DEFAULT_STORM_LIGHTNING_WARNING = 20;
        public static final int DEFAULT_STORM_LIGHTNING_COOLDOWN = 160;
        public static final double DEFAULT_STORM_LIGHTNING_RADIUS = 1.5D;

        private static final Map<String, ForgeConfigSpec.IntValue> INTS = new ConcurrentHashMap<>();
        private static final Map<String, ForgeConfigSpec.DoubleValue> DOUBLES = new ConcurrentHashMap<>();
        private static final Map<String, Number> TEST = new ConcurrentHashMap<>();

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push(ECMobFramework.CONFIG_SECTION);

            builder.comment("Elemental Wisp: small flying monster underground in the overworld.", "元素游灵：主世界地下的小飞怪").push("elemental_wisp");
            intValue(builder, "elemental_wisp.max_y", "max_y", DEFAULT_WISP_MAX_Y, -64, 320,
                    "Natural spawning only at or below this height, and only where the sky can't be seen.", "只在这个高度及以下、看不见天的地方自然刷出");
            intValue(builder, "elemental_wisp.nearby_radius", "nearby_radius", DEFAULT_WISP_NEARBY_RADIUS, 8, 128,
                    "Horizontal radius for max_nearby.", "max_nearby 的水平半径");
            intValue(builder, "elemental_wisp.max_nearby", "max_nearby", DEFAULT_WISP_MAX_NEARBY, 0, 64,
                    "No more natural spawning while this many wisps are within nearby_radius (0 = no natural spawning).",
                    "nearby_radius 以内已有这么多只时不再自然刷出（0 = 不自然刷出）");
            intValue(builder, "elemental_wisp.effect_seconds", "effect_seconds", DEFAULT_WISP_EFFECT_SECONDS, 0, 30,
                    "How long the element effect of a ram or bolt lasts, in seconds (0 = no effects; the wind knock-up ignores it).",
                    "冲撞、元素弹带的元素效果持续几秒（0 = 不带效果；风的掀起不看它）");
            intValue(builder, "elemental_wisp.elite_bolt_interval", "elite_bolt_interval", DEFAULT_WISP_BOLT_INTERVAL, 10, 600,
                    "Ticks between bolts of the elite (Converged Wisp).", "精英（元素聚灵）每隔多少刻射一发元素弹");
            builder.pop();

            builder.comment("Matrix Wraith: appears near large MRU concentrations that no stabilizer holds.",
                    "矩阵游魂：附近有大团、没被稳定器压着的 MRU 聚集体时追加刷出").push("matrix_wraith");
            intValue(builder, "matrix_wraith.matrix_damage_per_hit", "matrix_damage_per_hit", DEFAULT_WRAITH_MATRIX_DAMAGE, 0, 100_000,
                    "Matrix damage added to a player each time its claw hits (before matrix rank reductions; 0 = none).",
                    "每抓中玩家一下给他加的矩阵损伤（按矩阵阶打折前；0 = 不加）");
            intValue(builder, "matrix_wraith.presence_radius", "presence_radius", DEFAULT_WRAITH_PRESENCE_RADIUS, 4, 64,
                    "It only spawns within this many blocks of a qualifying MRU concentration.", "只在够格的聚集体这么多格以内刷出");
            intValue(builder, "matrix_wraith.min_presence_mru", "min_presence_mru", DEFAULT_WRAITH_MIN_PRESENCE_MRU, 0, 1_000_000,
                    "A concentration needs at least this much MRU to attract wraiths.", "聚集体至少要有这么多 MRU 才招游魂");
            intValue(builder, "matrix_wraith.spawn_weight", "spawn_weight", DEFAULT_WRAITH_SPAWN_WEIGHT, 0, 1000,
                    "Spawn weight added to the monster list near such a concentration (0 = never spawns naturally).",
                    "在这种聚集体附近往怪物刷怪表里加的权重（0 = 不自然刷出）");
            intValue(builder, "matrix_wraith.max_per_presence", "max_per_presence", DEFAULT_WRAITH_MAX_PER_PRESENCE, 0, 32,
                    "At most this many wraiths within 32 blocks of one concentration.", "一团聚集体 32 格以内最多几只");
            intValue(builder, "matrix_wraith.drain_mru", "drain_mru", DEFAULT_WRAITH_DRAIN_MRU, 0, 100_000,
                    "MRU it takes from that concentration when it spawns.", "刷出时从那团聚集体抽走多少 MRU");
            intValue(builder, "matrix_wraith.elite_blink_cooldown", "elite_blink_cooldown", DEFAULT_WRAITH_BLINK_COOLDOWN, 20, 2400,
                    "Ticks between blinks of the elite (Vengeful Wraith).", "精英（矩阵怨灵）每次闪到人背后之间隔多少刻");
            builder.pop();

            builder.comment("Storm Sorcerer: the fourth kind of Wind Mage, spawning in thunderstorms.",
                    "风暴术士：风法师的第四种，雷雨时刷出").push("storm_sorcerer");
            doubleValue(builder, "storm_sorcerer.thunder_chance", "thunder_chance", DEFAULT_STORM_THUNDER_CHANCE, 0D, 1D,
                    "Chance that a Wind Mage spawning naturally during a thunderstorm is a Storm Sorcerer.", "雷雨时自然刷出的风法师是风暴术士的几率");
            doubleValue(builder, "storm_sorcerer.hoanna_chance", "hoanna_chance", DEFAULT_STORM_HOANNA_CHANCE, 0D, 1D,
                    "Same chance in Hoanna (no thunderstorm needed).", "海纳尔里的同一个几率（不用雷雨）");
            doubleValue(builder, "storm_sorcerer.max_health", "max_health", DEFAULT_STORM_HEALTH, 1D, 1000D,
                    "Max health of a Storm Sorcerer.", "风暴术士的最大生命");
            doubleValue(builder, "storm_sorcerer.armor", "armor", DEFAULT_STORM_ARMOR, 0D, 30D,
                    "Armor of a Storm Sorcerer.", "风暴术士的护甲");
            doubleValue(builder, "storm_sorcerer.gust_damage", "gust_damage", DEFAULT_STORM_GUST_DAMAGE, 0D, 100D,
                    "Damage of its gust bolt.", "疾风弹的伤害");
            doubleValue(builder, "storm_sorcerer.gust_knockback", "gust_knockback", DEFAULT_STORM_GUST_KNOCKBACK, 0D, 5D,
                    "Knockback of its gust bolt (0 = none).", "疾风弹的击退（0 = 不击退）");
            doubleValue(builder, "storm_sorcerer.lightning_damage", "lightning_damage", DEFAULT_STORM_LIGHTNING_DAMAGE, 0D, 100D,
                    "Damage of its warned lightning strike (ignores armor; 0 = it doesn't cast it).", "预警落雷的伤害（无视护甲；0 = 不放这一招）");
            intValue(builder, "storm_sorcerer.lightning_warning_ticks", "lightning_warning_ticks", DEFAULT_STORM_LIGHTNING_WARNING, 10, 100,
                    "Ticks between marking the spot and the strike.", "从标出落点到落雷隔多少刻");
            intValue(builder, "storm_sorcerer.lightning_cooldown_ticks", "lightning_cooldown_ticks", DEFAULT_STORM_LIGHTNING_COOLDOWN, 40, 2400,
                    "Ticks between two strikes of one Storm Sorcerer.", "同一只两次落雷之间隔多少刻");
            doubleValue(builder, "storm_sorcerer.lightning_radius", "lightning_radius", DEFAULT_STORM_LIGHTNING_RADIUS, 0.5D, 4D,
                    "Radius (blocks) of the marked spot.", "落点标记的半径（格）");
            builder.pop();

            builder.pop();
        }

        private static void intValue(ForgeConfigSpec.Builder builder, String key, String name, int def, int min, int max, String en, String zh) {
            INTS.put(key, builder.comment(en, zh).defineInRange(name, def, min, max));
        }

        private static void doubleValue(ForgeConfigSpec.Builder builder, String key, String name, double def, double min, double max, String en, String zh) {
            DOUBLES.put(key, builder.comment(en, zh).defineInRange(name, def, min, max));
        }

        /** GameTest 钉值：key 是「小节名.配置名」（如 {@code matrix_wraith.drain_mru}），value 为 null 时还原 */
        public static void setTest(String key, Number value) {
            if (value == null) {
                TEST.remove(key);
            } else {
                TEST.put(key, value);
            }
        }

        private static int i(String key, int def) {
            Number test = TEST.get(key);
            if (test != null) {
                return test.intValue();
            }
            ForgeConfigSpec.IntValue value = INTS.get(key);
            return value == null ? def : ECConfig.get(value);
        }

        private static double d(String key, double def) {
            Number test = TEST.get(key);
            if (test != null) {
                return test.doubleValue();
            }
            ForgeConfigSpec.DoubleValue value = DOUBLES.get(key);
            return value == null ? def : ECConfig.get(value);
        }

        public static int wispMaxY() {
            return i("elemental_wisp.max_y", DEFAULT_WISP_MAX_Y);
        }

        public static int wispNearbyRadius() {
            return i("elemental_wisp.nearby_radius", DEFAULT_WISP_NEARBY_RADIUS);
        }

        public static int wispMaxNearby() {
            return i("elemental_wisp.max_nearby", DEFAULT_WISP_MAX_NEARBY);
        }

        public static int wispEffectSeconds() {
            return i("elemental_wisp.effect_seconds", DEFAULT_WISP_EFFECT_SECONDS);
        }

        public static int wispBoltInterval() {
            return i("elemental_wisp.elite_bolt_interval", DEFAULT_WISP_BOLT_INTERVAL);
        }

        public static int wraithMatrixDamage() {
            return i("matrix_wraith.matrix_damage_per_hit", DEFAULT_WRAITH_MATRIX_DAMAGE);
        }

        public static int wraithPresenceRadius() {
            return i("matrix_wraith.presence_radius", DEFAULT_WRAITH_PRESENCE_RADIUS);
        }

        public static int wraithMinPresenceMru() {
            return i("matrix_wraith.min_presence_mru", DEFAULT_WRAITH_MIN_PRESENCE_MRU);
        }

        public static int wraithSpawnWeight() {
            return i("matrix_wraith.spawn_weight", DEFAULT_WRAITH_SPAWN_WEIGHT);
        }

        public static int wraithMaxPerPresence() {
            return i("matrix_wraith.max_per_presence", DEFAULT_WRAITH_MAX_PER_PRESENCE);
        }

        public static int wraithDrainMru() {
            return i("matrix_wraith.drain_mru", DEFAULT_WRAITH_DRAIN_MRU);
        }

        public static int wraithBlinkCooldown() {
            return i("matrix_wraith.elite_blink_cooldown", DEFAULT_WRAITH_BLINK_COOLDOWN);
        }

        public static double stormThunderChance() {
            return d("storm_sorcerer.thunder_chance", DEFAULT_STORM_THUNDER_CHANCE);
        }

        public static double stormHoannaChance() {
            return d("storm_sorcerer.hoanna_chance", DEFAULT_STORM_HOANNA_CHANCE);
        }

        public static double stormHealth() {
            return d("storm_sorcerer.max_health", DEFAULT_STORM_HEALTH);
        }

        public static double stormArmor() {
            return d("storm_sorcerer.armor", DEFAULT_STORM_ARMOR);
        }

        public static double stormGustDamage() {
            return d("storm_sorcerer.gust_damage", DEFAULT_STORM_GUST_DAMAGE);
        }

        public static double stormGustKnockback() {
            return d("storm_sorcerer.gust_knockback", DEFAULT_STORM_GUST_KNOCKBACK);
        }

        public static double stormLightningDamage() {
            return d("storm_sorcerer.lightning_damage", DEFAULT_STORM_LIGHTNING_DAMAGE);
        }

        public static int stormLightningWarning() {
            return i("storm_sorcerer.lightning_warning_ticks", DEFAULT_STORM_LIGHTNING_WARNING);
        }

        public static int stormLightningCooldown() {
            return i("storm_sorcerer.lightning_cooldown_ticks", DEFAULT_STORM_LIGHTNING_COOLDOWN);
        }

        public static double stormLightningRadius() {
            return d("storm_sorcerer.lightning_radius", DEFAULT_STORM_LIGHTNING_RADIUS);
        }
    }
}
