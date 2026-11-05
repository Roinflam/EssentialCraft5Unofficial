package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeEntity;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeMirror;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadePillar;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeSpike;
import com.flameforge.essentialcraft5.item.MatrixConduitItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 第十三批 13c-M4「矩阵之影」的注册类（batch13.md §5.3.2、§9.3）。
 * <ul>
 *   <li>实体：首领 {@code matrix_shade}（{@link MatrixShadeEntity}，在 {@code #forge:bosses}、{@code divider_immune}、{@code mru_coil_immune} 里）、
 *       刷怪蛋 {@code matrix_shade_spawn_egg}（只放创造栏，点刷怪笼不改类型）；技能实体 {@code shade_mirror}（镜影）、{@code mind_spike}（心智钉）、
 *       {@code disorder_pillar}（失序之柱），都在 {@code growth_excluded} 里、不存盘、类别 MISC（不占刷怪上限）；</li>
 *   <li>物品：{@code matrix_conduit}（矩阵导流符，{@link MatrixConduitItem}，魔力工作台配方 recipes/matrix_shade/matrix_conduit.json）；</li>
 *   <li>音效（tools/sounds/ecmatrixshade.json，都指原版声音、改音高 / 音量）；</li>
 *   <li>服务端配置 {@code general.mobs.matrix_shade}（{@link Config}）。</li>
 * </ul>
 * 召唤在 {@code entity/boss/MatrixShadeSummon}，退还在 {@code entity/boss/MatrixShadeContracts}（SavedData），招式在 {@code entity/boss/MatrixShadeSkills}。
 * 渲染器、模型层在 {@code client/MatrixShadeClient}。文字在 tools/lang/keys/ecmatrixshade.json。
 */
public final class ECMatrixShade {

    public static final RegistryObject<EntityType<MatrixShadeEntity>> MATRIX_SHADE = ECEntities.register("matrix_shade",
            EntityType.Builder.<MatrixShadeEntity>of(MatrixShadeEntity::new, MobCategory.MONSTER)
                    .sized(MatrixShadeEntity.WIDTH, MatrixShadeEntity.HEIGHT).fireImmune().clientTrackingRange(10).updateInterval(2));

    public static final RegistryObject<EntityType<MatrixShadeMirror>> SHADE_MIRROR = ECEntities.register("shade_mirror",
            EntityType.Builder.<MatrixShadeMirror>of(MatrixShadeMirror::new, MobCategory.MISC)
                    .sized(MatrixShadeEntity.WIDTH, MatrixShadeEntity.HEIGHT).fireImmune().clientTrackingRange(10).updateInterval(2));

    public static final RegistryObject<EntityType<MatrixShadeSpike>> MIND_SPIKE = ECEntities.register("mind_spike",
            EntityType.Builder.<MatrixShadeSpike>of(MatrixShadeSpike::new, MobCategory.MISC)
                    .sized(0.8F, 1.6F).fireImmune().clientTrackingRange(10).updateInterval(4));

    public static final RegistryObject<EntityType<MatrixShadePillar>> DISORDER_PILLAR = ECEntities.register("disorder_pillar",
            EntityType.Builder.<MatrixShadePillar>of(MatrixShadePillar::new, MobCategory.MISC)
                    .sized(MatrixShadePillar.RADIUS * 2F, 3F).fireImmune().noSummon().clientTrackingRange(8).updateInterval(20));

    public static final RegistryObject<ECEntities.SpawnEgg> MATRIX_SHADE_SPAWN_EGG =
            ECEntities.spawnEgg("matrix_shade_spawn_egg", MATRIX_SHADE, 0x1A1026, 0xD040C0);

    public static final RegistryObject<MatrixConduitItem> MATRIX_CONDUIT = ECItems.register("matrix_conduit",
            () -> new MatrixConduitItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)), ItemSpec.generated("item/matrix_conduit"));

    /** 召出来时（指原版凋灵生成声，压低） */
    public static final RegistryObject<SoundEvent> SOUND_SUMMON = ECSounds.register("sound.mob.matrix_shade.summon");
    /** 平时（指原版恼鬼的声音，压低） */
    public static final RegistryObject<SoundEvent> SOUND_AMBIENT = ECSounds.register("sound.mob.matrix_shade.ambient");
    public static final RegistryObject<SoundEvent> SOUND_HURT = ECSounds.register("sound.mob.matrix_shade.hurt");
    public static final RegistryObject<SoundEvent> SOUND_DEATH = ECSounds.register("sound.mob.matrix_shade.death");
    /** 换相 */
    public static final RegistryObject<SoundEvent> SOUND_PHASE = ECSounds.register("sound.mob.matrix_shade.phase");
    /** 迟滞之握抓住人 */
    public static final RegistryObject<SoundEvent> SOUND_GRIP = ECSounds.register("sound.mob.matrix_shade.grip");
    /** 坠身落地 */
    public static final RegistryObject<SoundEvent> SOUND_SLAM = ECSounds.register("sound.mob.matrix_shade.slam");
    /** 放出镜影 */
    public static final RegistryObject<SoundEvent> SOUND_MIRROR = ECSounds.register("sound.mob.matrix_shade.mirror");
    /** 镜影碎掉 */
    public static final RegistryObject<SoundEvent> SOUND_MIRROR_BREAK = ECSounds.register("sound.mob.matrix_shade.mirror_break");
    /** 心智钉立起来 */
    public static final RegistryObject<SoundEvent> SOUND_SPIKE = ECSounds.register("sound.mob.matrix_shade.spike");
    /** 低语（只放给被盯上的人） */
    public static final RegistryObject<SoundEvent> SOUND_WHISPER = ECSounds.register("sound.mob.matrix_shade.whisper");
    /** 矩阵电弧 */
    public static final RegistryObject<SoundEvent> SOUND_ARC = ECSounds.register("sound.mob.matrix_shade.arc");
    /** 失序之柱炸开 */
    public static final RegistryObject<SoundEvent> SOUND_PILLAR = ECSounds.register("sound.mob.matrix_shade.pillar");
    /** 收束开始往回拉 */
    public static final RegistryObject<SoundEvent> SOUND_CONVERGE = ECSounds.register("sound.mob.matrix_shade.converge");

    private ECMatrixShade() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；属性、配置在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
        ECEntities.attributes(MATRIX_SHADE, MatrixShadeEntity::createAttributes);
        ECEntities.attributes(SHADE_MIRROR, MatrixShadeMirror::createAttributes);
        ECEntities.attributes(MIND_SPIKE, MatrixShadeSpike::createAttributes);
    }

    /**
     * 服务端配置 {@code general.mobs.matrix_shade}。读数一律走 {@link #values()}（配置没加载时给默认值，GameTest 的覆盖值优先）。
     * 默认值照 batch13.md §5.3.2；表里没写的（几种招式的伤害、上限）是这里定的，写在 {@link #DEFAULTS} 的注释里。
     */
    public static final class Config {

        /**
         * 召唤与结算。
         *
         * @param minDamage         召唤者的矩阵损伤至少这么多才召得出来
         * @param maxAbsorbed       最多转进去多少矩阵损伤
         * @param espePerDamage     每转进去 1 点损伤，从五芒星的柱子预扣多少有效 ESPE（按治腐化的折法，默认 0.5 有效约合 1.39 原始、3 档约 0.69；不收 MRU）
         * @param exclusiveRadius   这么远以内有别的矩阵之影（以后还有天灾之心）就召不出来
         * @param crystalStep       召唤者每转进去这么多损伤，多拿 1 颗矩阵结晶（0 = 不多给）
         * @param summonerAwayTicks 召唤者在拉回距离以外（不拽时按脱战距离）连续待多少刻算失败（回来就清零）
         * @param dissipateTicks    失败以后多少刻散去
         */
        public record Summon(int minDamage, int maxAbsorbed, double espePerDamage, int exclusiveRadius, int crystalStep,
                             int summonerAwayTicks, int dissipateTicks) {
        }

        /**
         * 属性与场地。
         *
         * @param baseHealth        基础生命
         * @param absorbedPerHealth 每转进去这么多损伤，多 1 点生命（0 = 不加）
         * @param armor             护甲
         * @param toughness         护甲韧性
         * @param attackDamage      双拳一下的伤害（吃护甲）
         * @param perHit            单次封顶
         * @param perSecond         每秒桶
         * @param arenaRadius       离五芒星中心多远以内是场地（它自己不离开）
         * @param pullRange         参战者离中心超过这么远被拽回，也是离它多远以内打得动它（0 = 不拽，这时按 escapeRange 算）
         * @param escapeRange       超过这么远退出战斗
         * @param phaseShiftTicks   换相时多少刻打不动
         * @param enrageTicks       打了这么多刻以后招式冷却变短
         * @param enrageCooldown    那时冷却乘多少
         * @param skillInterval     两个招式之间至少隔几刻
         * @param experience        经验
         */
        public record Fight(double baseHealth, int absorbedPerHealth, double armor, double toughness, double attackDamage,
                            double perHit, double perSecond, double arenaRadius, double pullRange, double escapeRange,
                            int phaseShiftTicks, int enrageTicks, double enrageCooldown, int skillInterval, int experience) {
        }

        /**
         * 招式。有预警的大招 = 固定值 + 目标最大生命 × 比例（policy §4.18「能躲的才穿甲」）。
         *
         * @param slamDamage       坠身固定值
         * @param slamPercent      坠身按目标最大生命的比例
         * @param pillarDamage     失序之柱固定值
         * @param pillarPercent    失序之柱比例
         * @param arcDamage        矩阵电弧固定值（每跳一人）
         * @param arcPercent       矩阵电弧比例
         * @param arcChainRange    电弧跳到下一人的最远距离
         * @param arcMaxTargets    电弧最多打几人
         * @param convergeDamage   收束冲击固定值
         * @param convergePercent  收束冲击比例
         * @param whisperDamage    低语固定值
         * @param whisperPercent   低语比例
         * @param gripTicks        迟滞之握抓多久
         * @param spikeDamageTaken 有心智钉立着时它挨的伤害乘多少
         * @param maxMirrors       镜影最多几个
         * @param maxSpikes        心智钉最多几根
         * @param maxPillars       失序之柱最多几根
         */
        public record Skills(double slamDamage, double slamPercent, double pillarDamage, double pillarPercent,
                             double arcDamage, double arcPercent, double arcChainRange, int arcMaxTargets,
                             double convergeDamage, double convergePercent, double whisperDamage, double whisperPercent,
                             int gripTicks, double spikeDamageTaken, int maxMirrors, int maxSpikes, int maxPillars) {
        }

        public record Values(Summon summon, Fight fight, Skills skills) {
        }

        /**
         * 默认值。§5.3.2 给了的：18 000 起召、转进去最多 72 000、每点 0.5 ESPE、128 格互斥、每 24 000 多 1 颗结晶、离开 30 秒、200 刻散去；
         * 生命 300 + a / 600、护甲 8、韧性 4、单次 25、每秒 15、场地 20 格、24~48 格拉回、换相 2 秒、5 分钟后冷却 ×0.7、经验 100；
         * 坠身 10 + 10%、失序之柱 8 + 12%、电弧相距 ≤ 10 格才跳。
         * 这里定的：双拳 9（同原版铁傀儡一档）、电弧每跳 4 + 6%、最多 4 人、收束冲击 6 + 8%、低语 6 + 8%、握 4 秒、
         * 有心智钉时挨打 ×0.5、镜影 3、心智钉 2、失序之柱 4、两招之间 50 刻。
         */
        public static final Values DEFAULTS = new Values(
                new Summon(18_000, 72_000, 0.5D, 128, 24_000, 600, 200),
                new Fight(300D, 600, 8D, 4D, 9D, 25D, 15D, 20D, 24D, 48D, 40, 6_000, 0.7D, 50, 100),
                new Skills(10D, 0.10D, 8D, 0.12D, 4D, 0.06D, 10D, 4, 6D, 0.08D, 6D, 0.08D, 80, 0.5D, 3, 2, 4));

        private static ForgeConfigSpec.IntValue minDamage;
        private static ForgeConfigSpec.IntValue maxAbsorbed;
        private static ForgeConfigSpec.DoubleValue espePerDamage;
        private static ForgeConfigSpec.IntValue exclusiveRadius;
        private static ForgeConfigSpec.IntValue crystalStep;
        private static ForgeConfigSpec.IntValue summonerAwayTicks;
        private static ForgeConfigSpec.IntValue dissipateTicks;

        private static ForgeConfigSpec.DoubleValue baseHealth;
        private static ForgeConfigSpec.IntValue absorbedPerHealth;
        private static ForgeConfigSpec.DoubleValue armor;
        private static ForgeConfigSpec.DoubleValue toughness;
        private static ForgeConfigSpec.DoubleValue attackDamage;
        private static ForgeConfigSpec.DoubleValue perHit;
        private static ForgeConfigSpec.DoubleValue perSecond;
        private static ForgeConfigSpec.DoubleValue arenaRadius;
        private static ForgeConfigSpec.DoubleValue pullRange;
        private static ForgeConfigSpec.DoubleValue escapeRange;
        private static ForgeConfigSpec.IntValue phaseShiftTicks;
        private static ForgeConfigSpec.IntValue enrageTicks;
        private static ForgeConfigSpec.DoubleValue enrageCooldown;
        private static ForgeConfigSpec.IntValue skillInterval;
        private static ForgeConfigSpec.IntValue experience;

        private static ForgeConfigSpec.DoubleValue slamDamage;
        private static ForgeConfigSpec.DoubleValue slamPercent;
        private static ForgeConfigSpec.DoubleValue pillarDamage;
        private static ForgeConfigSpec.DoubleValue pillarPercent;
        private static ForgeConfigSpec.DoubleValue arcDamage;
        private static ForgeConfigSpec.DoubleValue arcPercent;
        private static ForgeConfigSpec.DoubleValue arcChainRange;
        private static ForgeConfigSpec.IntValue arcMaxTargets;
        private static ForgeConfigSpec.DoubleValue convergeDamage;
        private static ForgeConfigSpec.DoubleValue convergePercent;
        private static ForgeConfigSpec.DoubleValue whisperDamage;
        private static ForgeConfigSpec.DoubleValue whisperPercent;
        private static ForgeConfigSpec.IntValue gripTicks;
        private static ForgeConfigSpec.DoubleValue spikeDamageTaken;
        private static ForgeConfigSpec.IntValue maxMirrors;
        private static ForgeConfigSpec.IntValue maxSpikes;
        private static ForgeConfigSpec.IntValue maxPillars;

        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            Summon s = DEFAULTS.summon();
            Fight f = DEFAULTS.fight();
            Skills k = DEFAULTS.skills();
            builder.comment("Matrix Specter: the optional boss summoned with a Matrix Conduit on a Soul Pentacle.",
                            "矩阵之影：拿矩阵导流符在灵魂五芒星上召出来的可选首领")
                    .push(List.of(ECMobFramework.CONFIG_SECTION, "matrix_shade"));

            minDamage = builder.comment("The summoner needs at least this much matrix damage.", "召唤者的矩阵损伤至少要这么多")
                    .defineInRange("min_matrix_damage", s.minDamage(), 1, Integer.MAX_VALUE);
            maxAbsorbed = builder.comment("At most this much of the summoner's matrix damage goes into the boss.",
                            "召唤时最多把召唤者的这么多矩阵损伤转给它")
                    .defineInRange("max_absorbed", s.maxAbsorbed(), 1, Integer.MAX_VALUE);
            espePerDamage = builder.comment("Effective ESPE taken from the pentacle's pillars per point of matrix damage moved, counted like pentacle cures "
                                    + "(0.5 effective is about 1.39 raw ESPE, about 0.69 on a tier-3 pentacle). No MRU is charged; in raw ESPE this costs "
                                    + "more than the Matrix Tuner's 0.5 per point.",
                            "每转 1 点矩阵损伤，从五芒星的柱子预扣多少有效 ESPE（按治腐化的折法：0.5 有效约合 1.39 原始，3 档约 0.69）。不收 MRU；"
                                    + "按原始 ESPE 算比矩阵调律器的每点 0.5 贵")
                    .defineInRange("espe_per_damage", s.espePerDamage(), 0D, 100D);
            exclusiveRadius = builder.comment("No summoning while another Matrix Specter is this close.", "这么远以内已经有矩阵之影时召不出来")
                    .defineInRange("exclusive_radius", s.exclusiveRadius(), 16, 512);
            crystalStep = builder.comment("The summoner gets one more Matrix Crystal per this much matrix damage moved (0 = no bonus).",
                            "召唤者每转进去这么多矩阵损伤，多拿 1 颗矩阵结晶（0 = 不多给）")
                    .defineInRange("crystal_step", s.crystalStep(), 0, Integer.MAX_VALUE);
            summonerAwayTicks = builder.comment("The fight fails if the summoner stays beyond pull_range (escape_range when pull_range is 0) for this many ticks.",
                            "召唤者在 pull_range 以外（pull_range 填 0 时按 escape_range）连续待满这么多刻，这一场算失败")
                    .defineInRange("summoner_away_ticks", s.summonerAwayTicks(), 20, 72_000);
            dissipateTicks = builder.comment("After a failed fight the boss fades away after this many ticks.", "失败以后多少刻散去")
                    .defineInRange("dissipate_ticks", s.dissipateTicks(), 1, 72_000);

            baseHealth = builder.comment("Base max health.", "基础生命").defineInRange("base_health", f.baseHealth(), 1D, 100_000D);
            absorbedPerHealth = builder.comment("One more max health per this much matrix damage moved into it (0 = none).",
                            "每转进去这么多矩阵损伤多 1 点生命（0 = 不加）")
                    .defineInRange("absorbed_per_health", f.absorbedPerHealth(), 0, Integer.MAX_VALUE);
            armor = builder.comment("Armor.", "护甲").defineInRange("armor", f.armor(), 0D, 30D);
            toughness = builder.comment("Armor toughness.", "护甲韧性").defineInRange("armor_toughness", f.toughness(), 0D, 20D);
            attackDamage = builder.comment("Damage of a fist blow (reduced by armor).", "双拳一下的伤害（吃护甲）")
                    .defineInRange("attack_damage", f.attackDamage(), 0D, 1000D);
            perHit = builder.comment("Damage cap per hit (0 = none).", "单次封顶（0 = 不封）").defineInRange("per_hit_cap", f.perHit(), 0D, 10_000D);
            perSecond = builder.comment("Damage it can take per second (0 = no limit).", "每秒最多挨多少（0 = 不限）")
                    .defineInRange("per_second_cap", f.perSecond(), 0D, 10_000D);
            arenaRadius = builder.comment("It stays within this distance of the pentacle center.", "它自己不离开五芒星中心这么远")
                    .defineInRange("arena_radius", f.arenaRadius(), 4D, 64D);
            pullRange = builder.comment("Fighters further than this from the pentacle center are pulled back; players within this distance of it can hurt it. "
                                    + "0 = never pull, and then escape_range sets how close you must be to hurt it.",
                            "参战者离五芒星中心超过这么远被拽回，离它这么远以内才打得动它。0 = 不拽，这时按 escape_range 算打得动的距离")
                    .defineInRange("pull_range", f.pullRange(), 0D, 128D);
            escapeRange = builder.comment("Fighters further than this leave the fight.", "超过这么远退出战斗")
                    .defineInRange("escape_range", f.escapeRange(), 8D, 256D);
            phaseShiftTicks = builder.comment("Ticks it can't be hurt while changing phase.", "换相时多少刻打不动")
                    .defineInRange("phase_shift_ticks", f.phaseShiftTicks(), 0, 400);
            enrageTicks = builder.comment("After this many ticks of fighting, its skills come faster.", "打了这么多刻以后招式冷却变短")
                    .defineInRange("enrage_ticks", f.enrageTicks(), 20, 1_000_000);
            enrageCooldown = builder.comment("Cooldown multiplier after enrage_ticks.", "那以后冷却乘多少")
                    .defineInRange("enrage_cooldown_multiplier", f.enrageCooldown(), 0.1D, 1D);
            skillInterval = builder.comment("Minimum ticks between two skills.", "两个招式之间至少隔几刻")
                    .defineInRange("skill_interval", f.skillInterval(), 10, 1200);
            experience = builder.comment("Experience dropped.", "掉多少经验").defineInRange("experience", f.experience(), 0, 10_000);

            slamDamage = builder.comment("Falling Body: flat damage (ignores armor, 1 s shadow warning).", "坠身：固定伤害（无视护甲，1 秒阴影圈预警）")
                    .defineInRange("slam_damage", k.slamDamage(), 0D, 1000D);
            slamPercent = builder.comment("Falling Body: plus this share of the target's max health.", "坠身：再加目标最大生命的这个比例")
                    .defineInRange("slam_health_percent", k.slamPercent(), 0D, 0.2D);
            pillarDamage = builder.comment("Pillar of Disorder: flat damage (ignores armor, magic, 1.5 s warning ring).",
                            "失序之柱：固定伤害（无视护甲、算魔法，1.5 秒预警圈）")
                    .defineInRange("pillar_damage", k.pillarDamage(), 0D, 1000D);
            pillarPercent = builder.comment("Pillar of Disorder: plus this share of the target's max health.", "失序之柱：再加目标最大生命的这个比例")
                    .defineInRange("pillar_health_percent", k.pillarPercent(), 0D, 0.2D);
            arcDamage = builder.comment("Matrix Arc: flat damage per person hit (1 s charge).", "矩阵电弧：每跳一人的固定伤害（1 秒蓄力）")
                    .defineInRange("arc_damage", k.arcDamage(), 0D, 1000D);
            arcPercent = builder.comment("Matrix Arc: plus this share of the target's max health.", "矩阵电弧：再加目标最大生命的这个比例")
                    .defineInRange("arc_health_percent", k.arcPercent(), 0D, 0.2D);
            arcChainRange = builder.comment("Matrix Arc only jumps to someone within this distance of the last person hit.",
                            "矩阵电弧只跳到离上一个人这么近的人身上")
                    .defineInRange("arc_chain_range", k.arcChainRange(), 0D, 64D);
            arcMaxTargets = builder.comment("Matrix Arc hits at most this many people.", "矩阵电弧最多打几人")
                    .defineInRange("arc_max_targets", k.arcMaxTargets(), 1, 16);
            convergeDamage = builder.comment("Convergence: flat damage of the shock at the end (a raised shield blocks it).",
                            "收束：最后那下冲击的固定伤害（举着盾不挨）")
                    .defineInRange("converge_damage", k.convergeDamage(), 0D, 1000D);
            convergePercent = builder.comment("Convergence: plus this share of the target's max health.", "收束：再加目标最大生命的这个比例")
                    .defineInRange("converge_health_percent", k.convergePercent(), 0D, 0.2D);
            whisperDamage = builder.comment("Whisper: flat damage if you are looking at it when the whisper ends (2 s warning).",
                            "低语：说完那一刻你还看着它就挨的固定伤害（2 秒预警）")
                    .defineInRange("whisper_damage", k.whisperDamage(), 0D, 1000D);
            whisperPercent = builder.comment("Whisper: plus this share of the target's max health.", "低语：再加目标最大生命的这个比例")
                    .defineInRange("whisper_health_percent", k.whisperPercent(), 0D, 0.2D);
            gripTicks = builder.comment("Lingering Grip lasts this many ticks unless you hit the boss.", "迟滞之握抓多久（打它一下就挣开）")
                    .defineInRange("grip_ticks", k.gripTicks(), 1, 1200);
            spikeDamageTaken = builder.comment("While a Mind Spike stands, damage it takes is multiplied by this.", "有心智钉立着时，它挨的伤害乘多少")
                    .defineInRange("spike_damage_taken", k.spikeDamageTaken(), 0D, 1D);
            maxMirrors = builder.comment("Mirror images at once.", "镜影最多同时几个").defineInRange("max_mirrors", k.maxMirrors(), 0, 8);
            maxSpikes = builder.comment("Mind Spikes at once.", "心智钉最多同时几根").defineInRange("max_spikes", k.maxSpikes(), 0, 8);
            maxPillars = builder.comment("Pillars of Disorder at once.", "失序之柱最多同时几根").defineInRange("max_pillars", k.maxPillars(), 0, 16);
            builder.pop(2);
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (minDamage == null) {
                return DEFAULTS;
            }
            return new Values(
                    new Summon(ECConfig.get(minDamage), ECConfig.get(maxAbsorbed), ECConfig.get(espePerDamage), ECConfig.get(exclusiveRadius),
                            ECConfig.get(crystalStep), ECConfig.get(summonerAwayTicks), ECConfig.get(dissipateTicks)),
                    new Fight(ECConfig.get(baseHealth), ECConfig.get(absorbedPerHealth), ECConfig.get(armor), ECConfig.get(toughness),
                            ECConfig.get(attackDamage), ECConfig.get(perHit), ECConfig.get(perSecond), ECConfig.get(arenaRadius),
                            ECConfig.get(pullRange), ECConfig.get(escapeRange), ECConfig.get(phaseShiftTicks), ECConfig.get(enrageTicks),
                            ECConfig.get(enrageCooldown), ECConfig.get(skillInterval), ECConfig.get(experience)),
                    new Skills(ECConfig.get(slamDamage), ECConfig.get(slamPercent), ECConfig.get(pillarDamage), ECConfig.get(pillarPercent),
                            ECConfig.get(arcDamage), ECConfig.get(arcPercent), ECConfig.get(arcChainRange), ECConfig.get(arcMaxTargets),
                            ECConfig.get(convergeDamage), ECConfig.get(convergePercent), ECConfig.get(whisperDamage), ECConfig.get(whisperPercent),
                            ECConfig.get(gripTicks), ECConfig.get(spikeDamageTaken), ECConfig.get(maxMirrors), ECConfig.get(maxSpikes),
                            ECConfig.get(maxPillars)));
        }

        /** 只给 GameTest：在同一段同步代码里设、测、置回 null（别用 ConfigValue.set，文件监视器会冲掉） */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
