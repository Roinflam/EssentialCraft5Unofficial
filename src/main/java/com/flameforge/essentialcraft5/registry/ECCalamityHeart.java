package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.boss.CalamitySealClient;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityPillarEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityRubbleEntity;
import com.flameforge.essentialcraft5.item.CalamityBeaconItem;
import com.flameforge.essentialcraft5.item.MobMaterialItem;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CCalamitySealPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 第十三批 13e-B「天灾之心」的注册类（batch13.md §5.3.5、§9.5；MS §2.6.2）：
 * <ul>
 *   <li>首领 {@link #CALAMITY_HEART} 天灾之心（{@code entity/boss/CalamityHeartEntity}，Boss 档）与刷怪蛋（只放创造栏，点刷怪笼不改类型）；
 *       在 {@code #forge:bosses}、{@code divider_immune}、{@code mru_coil_immune} 里；</li>
 *   <li>技能实体 {@link #CALAMITY_PILLAR} 灾柱（活物，不存盘，剩余血量记在首领身上、读档重建）、{@link #CALAMITY_RUBBLE} 落石（不是活物，不存盘，
 *       落地即消失、不放方块）：类别 MISC（不占刷怪上限），都在 {@code growth_excluded} 里；</li>
 *   <li>物品 {@link #CALAMITY_BEACON} 天灾信标（{@code item/CalamityBeaconItem}，一组 1，打倒后变「空」、回到召唤者手里）、
 *       {@link #CALAMITY_REMNANT} 天灾残核（主线唯一的怪物材料，九阶祭品、宅男 / 裁决之刃 5 阶）；</li>
 *   <li>物品标签 {@link #LIGHT_SOURCES} {@code essentialcraft5:calamity_light_sources}：熄光时拿着能看见它的东西，
 *       方块物品发光度 &gt; 0 的本来就算，标签给整合包补（{@code tools/tags/eccalamityheart.json}，默认空）；</li>
 *   <li>音效（{@code tools/sounds/eccalamityheart.json}，都指原版声音、改音高 / 音量；只有循环音乐是 {@code tools/gen/boss_music.py} 合成的）。</li>
 * </ul>
 * 召唤在 {@code entity/boss/CalamityHeartSummon}，信标与暗影核心的退还在 {@code entity/boss/CalamityHeartContracts}（SavedData），
 * 四相招式在 {@code entity/boss/CalamityHeartSkills}，按主灾加奖励的战利品条件在 {@code loot/condition/CalamityMainCondition}。
 * 伤害类型 {@code calamity}、{@code calamity_solar} 在 {@code ECDamageTypes}。文字在 {@code tools/lang/keys/eccalamityheart.json}，
 * 客户端 {@code client/CalamityHeartClient}。服务端配置 {@code general.calamity_heart}（{@link Config}），客户端配置 {@code [calamity_heart_music]}
 * （{@link ClientConfig}）。
 */
public final class ECCalamityHeart {

    /** 天灾之心：悬空的大核心 */
    public static final RegistryObject<EntityType<CalamityHeartEntity>> CALAMITY_HEART = ECEntities.register("calamity_heart",
            EntityType.Builder.<CalamityHeartEntity>of(CalamityHeartEntity::new, MobCategory.MONSTER)
                    .sized(2.0F, 3.0F).fireImmune().clientTrackingRange(10).updateInterval(2));

    /** 灾柱：MISC、不存盘 */
    public static final RegistryObject<EntityType<CalamityPillarEntity>> CALAMITY_PILLAR = ECEntities.register("calamity_pillar",
            EntityType.Builder.<CalamityPillarEntity>of(CalamityPillarEntity::new, MobCategory.MISC)
                    .sized(1.2F, 4.0F).fireImmune().noSummon().clientTrackingRange(10).updateInterval(4));

    /** 落石：MISC、不存盘 */
    public static final RegistryObject<EntityType<CalamityRubbleEntity>> CALAMITY_RUBBLE = ECEntities.register("calamity_rubble",
            EntityType.Builder.<CalamityRubbleEntity>of(CalamityRubbleEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().noSummon().clientTrackingRange(8).updateInterval(2));

    public static final RegistryObject<ECEntities.SpawnEgg> CALAMITY_HEART_SPAWN_EGG =
            ECEntities.spawnEgg("calamity_heart_spawn_egg", CALAMITY_HEART, 0x2A0A0A, 0xFF6A1A);

    /** 天灾信标：一组 1 */
    public static final RegistryObject<CalamityBeaconItem> CALAMITY_BEACON = ECItems.register("calamity_beacon",
            () -> new CalamityBeaconItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()), ItemSpec.generated("item/calamity_beacon"));

    /** 天灾残核：一组 64 */
    public static final RegistryObject<MobMaterialItem> CALAMITY_REMNANT = ECItems.register("calamity_remnant",
            () -> new MobMaterialItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), "calamity_remnant"),
            ItemSpec.generated("item/calamity_remnant"));

    /** 熄光时拿着能看见它的东西（方块物品发光度 &gt; 0 的不用进标签） */
    public static final TagKey<Item> LIGHT_SOURCES = TagKey.create(Registries.ITEM, EssentialCraft5.id("calamity_light_sources"));

    /** 召出来时（原版凋灵生成声，压低） */
    public static final RegistryObject<SoundEvent> SOUND_SUMMON = ECSounds.register("sound.mob.calamity_heart.summon", 64F);
    public static final RegistryObject<SoundEvent> SOUND_AMBIENT = ECSounds.register("sound.mob.calamity_heart.ambient");
    public static final RegistryObject<SoundEvent> SOUND_HURT = ECSounds.register("sound.mob.calamity_heart.hurt");
    public static final RegistryObject<SoundEvent> SOUND_DEATH = ECSounds.register("sound.mob.calamity_heart.death", 64F);
    /** 一根灾柱倒了 */
    public static final RegistryObject<SoundEvent> SOUND_PILLAR_BREAK = ECSounds.register("sound.mob.calamity_heart.pillar_break", 48F);
    /** 残响大招 */
    public static final RegistryObject<SoundEvent> SOUND_ECHO = ECSounds.register("sound.mob.calamity_heart.echo", 48F);
    /** 天光棱线落下 */
    public static final RegistryObject<SoundEvent> SOUND_PRISM = ECSounds.register("sound.mob.calamity_heart.prism");
    /** 烈日标记打上 / 落下 */
    public static final RegistryObject<SoundEvent> SOUND_MARK = ECSounds.register("sound.mob.calamity_heart.mark");
    /** 腐蚀吐息 */
    public static final RegistryObject<SoundEvent> SOUND_BREATH = ECSounds.register("sound.mob.calamity_heart.breath");
    /** 熄光开始 */
    public static final RegistryObject<SoundEvent> SOUND_BLACKOUT = ECSounds.register("sound.mob.calamity_heart.blackout");
    /** 暗袭打中 */
    public static final RegistryObject<SoundEvent> SOUND_STRIKE = ECSounds.register("sound.mob.calamity_heart.strike");
    /** 震波 */
    public static final RegistryObject<SoundEvent> SOUND_QUAKE = ECSounds.register("sound.mob.calamity_heart.quake");
    /** 落石砸地 */
    public static final RegistryObject<SoundEvent> SOUND_RUBBLE = ECSounds.register("sound.mob.calamity_heart.rubble");
    /** 开始崩解 */
    public static final RegistryObject<SoundEvent> SOUND_COLLAPSE = ECSounds.register("sound.mob.calamity_heart.collapse", 48F);
    /** 崩解脉冲 */
    public static final RegistryObject<SoundEvent> SOUND_PULSE = ECSounds.register("sound.mob.calamity_heart.pulse", 48F);
    /** 循环音乐（30 秒一圈，tools/gen/boss_music.py 合成） */
    public static final RegistryObject<SoundEvent> MUSIC = ECSounds.register("sound.music.calamity_heart");

    private ECCalamityHeart() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；属性、配置在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
        ECConfig.client(ClientConfig::define);
        // 天灾封印的状态（只发本人）；处理器只在客户端调（registerS2C 已套 DistExecutor），CalamitySealClient 到执行时才加载
        ECNetwork.registerS2C(S2CCalamitySealPacket.class, S2CCalamitySealPacket::encode, S2CCalamitySealPacket::decode,
                packet -> CalamitySealClient.onPacket(packet));
        ECEntities.attributes(CALAMITY_HEART, CalamityHeartEntity::createAttributes);
        ECEntities.attributes(CALAMITY_PILLAR, CalamityPillarEntity::createAttributes);
    }

    /**
     * 服务端配置 {@code general.calamity_heart}（召唤条件与花费、属性、场地、三阶段、四相）。读数一律走 {@link #values()}
     * （配置没加载时给默认值，GameTest 的覆盖值优先）。默认值照 batch13.md §5.3.5；表里没写的（几种招式的预警刻数、上限、
     * 腐蚀吐息的比例）是这里定的，写在 {@link #DEFAULTS} 的注释里。有预警的大招 = 固定值 + 目标最大生命 × 比例（比例不超过 0.2，policy §4.18）。
     */
    public static final class Config {

        /**
         * 召唤与结算。
         *
         * @param requireCalamity           要海纳尔此刻有天灾才召得出来（关掉时随机主灾、每次多吃 coresExtraWithoutCalamity 个暗影核心）
         * @param coresPerResummon          空信标再召一次吃几个暗影核心（满的信标第一次不吃）
         * @param coresExtraWithoutCalamity 关掉 requireCalamity 时每次多吃几个
         * @param pillarOffset              四根灾柱离召唤点几格（东南西北各一根），权限查这四处和中心；平整度在 (2 × 它 + 1) 见方里抽 25 列
         * @param maxUnevenness             那 25 列地面最高最低差不超过多少
         * @param exclusiveRadius           这么远以内有别的天灾之心 / 矩阵之影就召不出来
         * @param dissipateTicks            没打倒以后多少刻散去
         * @param firstKillBonus            每人第一次打倒多给几个天灾残核
         */
        public record Summon(boolean requireCalamity, int coresPerResummon, int coresExtraWithoutCalamity, int pillarOffset,
                             int maxUnevenness, int exclusiveRadius, int dissipateTicks, int firstKillBonus) {
        }

        /**
         * 属性、场地与三阶段。
         *
         * @param maxHealth           生命
         * @param armor               护甲
         * @param toughness           护甲韧性
         * @param perHit              单次封顶（0 = 不封）
         * @param perSecond           每秒桶（0 = 不限）
         * @param pillarHealth        普通灾柱的生命
         * @param mainPillarHealth    主灾那根的生命
         * @param pillarPerHit        灾柱的单次封顶（0 = 不封）
         * @param arenaRadius         场地半径（它自己不离开，招式只在这里面落）
         * @param pullRange           参战者离场地中心超过这么远被拽回，也是离它多远以内打得动它（0 = 不拽，这时按 escapeRange 算）
         * @param escapeRange         超过这么远退出战斗
         * @param shieldHover         护盾期悬在地面上几格
         * @param lowHover            破盾窗口、失衡期、崩解时离地几格
         * @param followSpeed         失衡期跟着参战者的重心挪的速度（格 / 刻）
         * @param shieldWindowTicks   打掉一根灾柱以后破盾多少刻
         * @param skillInterval       两个招式之间至少隔几刻
         * @param aspectSwapTicks     失衡期每多少刻换一对相
         * @param unbalancedCooldown  失衡期冷却乘多少
         * @param collapseThreshold   生命低于这个比例开始崩解
         * @param collapseTicks       崩解倒计时多少刻
         * @param collapseDamageTaken 崩解时挨打乘多少
         * @param pulseInterval       崩解时每多少刻一次脉冲
         * @param pulseRange          脉冲打多远以内的参战者
         * @param pulseDamage         脉冲固定值
         * @param pulsePercent        脉冲按目标最大生命的比例
         * @param reraiseHealth       第一次倒计时走完回到生命的这个比例
         * @param reraisePillars      第一次倒计时走完重立几根灾柱
         * @param experience          经验
         */
        public record Fight(double maxHealth, double armor, double toughness, double perHit, double perSecond,
                            double pillarHealth, double mainPillarHealth, double pillarPerHit,
                            double arenaRadius, double pullRange, double escapeRange, double shieldHover, double lowHover, double followSpeed,
                            int shieldWindowTicks, int skillInterval, int aspectSwapTicks, double unbalancedCooldown,
                            double collapseThreshold, int collapseTicks, double collapseDamageTaken, int pulseInterval, double pulseRange,
                            double pulseDamage, double pulsePercent, double reraiseHealth, int reraisePillars, int experience) {
        }

        /**
         * 四相招式。
         *
         * @param prismDamage       天光棱线固定值（{@code calamity_solar}）
         * @param prismPercent      天光棱线比例
         * @param prismLines        一次几条
         * @param maxPrisms         同时最多几条
         * @param prismWarnTicks    棱线预警刻数
         * @param prismFireSeconds  棱线打中后着火几秒
         * @param markPercent       烈日标记比例（落下那刻头顶 +2 格露天才挨）
         * @param markWarnTicks     烈日标记预警刻数
         * @param noonPercent       残响 · 正午比例（露天的参战者）
         * @param noonWarnTicks     残响 · 正午预警刻数
         * @param maxFumes          毒雾同时最多几团（残响 · 弥漫另放一份同样多的）
         * @param fumesPerCast      瘴云一次放几团
         * @param fumeTicks         瘴云放出的毒雾活多久
         * @param echoFumeTicks     残响 · 弥漫的毒雾活多久
         * @param breathDamage      腐蚀吐息固定值
         * @param breathPercent     腐蚀吐息比例
         * @param breathRange       腐蚀吐息够多远
         * @param breathWarnTicks   腐蚀吐息预警刻数
         * @param corrosionTicks    腐蚀（不能回血）多少刻
         * @param blackoutWarnTicks 熄光预警刻数
         * @param blackoutTicks     熄光多少刻
         * @param blackoutStrikes   熄光里几次暗袭
         * @param longNightTicks    残响 · 长夜多少刻
         * @param longNightStrikes  残响 · 长夜几次暗袭
         * @param strikeDamage      暗袭一下（吃护甲）
         * @param strikeWarnTicks   暗袭从起手到落下几刻（拿光源的人看得见这段轨迹）
         * @param quakeDamage       震波固定值
         * @param quakePercent      震波比例
         * @param quakeRings        震波一次几圈
         * @param faultRings        残响 · 断层几圈
         * @param ringWarnTicks     每圈的预警刻数
         * @param maxRubble         落石同时最多几块
         * @param rubbleCount       一次几块
         * @param rubbleDamage      落石一块（吃护甲）
         * @param rubbleWarnTicks   落石影子多少刻后落下
         */
        public record Skills(double prismDamage, double prismPercent, int prismLines, int maxPrisms, int prismWarnTicks, int prismFireSeconds,
                             double markPercent, int markWarnTicks, double noonPercent, int noonWarnTicks,
                             int maxFumes, int fumesPerCast, int fumeTicks, int echoFumeTicks,
                             double breathDamage, double breathPercent, double breathRange, int breathWarnTicks, int corrosionTicks,
                             int blackoutWarnTicks, int blackoutTicks, int blackoutStrikes, int longNightTicks, int longNightStrikes,
                             double strikeDamage, int strikeWarnTicks,
                             double quakeDamage, double quakePercent, int quakeRings, int faultRings, int ringWarnTicks,
                             int maxRubble, int rubbleCount, double rubbleDamage, int rubbleWarnTicks) {
        }

        /**
         * 加强那一轮加的（tools/plan/calamity_heart_v2.md）：开场演出、天灾封印、灾柱反噬、四灾归心、合相技、垂死反扑。每项填 0 / false 关掉。
         *
         * @param introTicks           开场演出多少刻（这段时间它和灾柱打不动、不出招；0 = 没有演出，封印一开始就落下）
         * @param seal                 天灾封印：参战者身上的附魔按位置分四处封住，打倒对应的灾柱解开
         * @param resealOnReraise      崩解后重立的灾柱把它那一处重新封上
         * @param backlashDamage       灾柱反噬固定值（每根第一次掉到一半时；无视护甲）
         * @param backlashPercent      灾柱反噬比例
         * @param backlashRadius       灾柱反噬打多远（以灾柱为中心，水平）
         * @param backlashWarnTicks    灾柱反噬预警多少刻
         * @param convergeDamage       四灾归心固定值（四根都倒那一刻；圈外的人挨）
         * @param convergePercent      四灾归心比例
         * @param convergeRadius       四灾归心的安全圈半径（以它为中心）
         * @param convergeWarnTicks    四灾归心预警多少刻
         * @param comboDelayTicks      失衡期换到一对相以后多少刻放合相技（0 = 不放）
         * @param collapseEchoInterval 崩解倒计时里每多少刻反扑一次（交替断层 / 棱线齐射；0 = 不反扑）
         * @param volleyLines          棱线齐射一次几条
         */
        public record Fury(int introTicks, boolean seal, boolean resealOnReraise,
                           double backlashDamage, double backlashPercent, double backlashRadius, int backlashWarnTicks,
                           double convergeDamage, double convergePercent, double convergeRadius, int convergeWarnTicks,
                           int comboDelayTicks, int collapseEchoInterval, int volleyLines) {

            /** 没有演出、封印和新招的那一份（旧机制的 GameTest 用） */
            public Fury quiet() {
                return new Fury(0, false, false, 0D, 0D, backlashRadius, backlashWarnTicks, 0D, 0D, convergeRadius, convergeWarnTicks,
                        0, 0, volleyLines);
            }
        }

        public record Values(Summon summon, Fight fight, Skills skills, Fury fury) {
        }

        /**
         * 默认值。§5.3.5 给了的：信标再召吃 2 个暗影核心、关掉天灾要求多吃 2；四根灾柱 ±12 格、25 列高差 ≤ 8、128 格互斥；
         * 生命 1 500、护甲 16、韧性 8、单次 40、每秒 30；灾柱 160、主灾 240、单次 40；场地 32、32~64 格拉回；护盾期悬 6 格、之后 3 格、
         * 跟着挪 0.15；破盾 20 秒；失衡期每 15 秒换两相、冷却 ×0.8；崩解 &lt; 20%、30 秒、受伤 ×1.25、每 5 秒 6 + 4%（24 格）、回到 35%、重立 2 根；
         * 首杀 +1 残核；经验 300；天光棱线 12 + 6%、1.5 秒、着火 5 秒；烈日标记 20%、4 秒；正午 15%、3 秒；毒雾 ≤ 6、残响 15 秒；
         * 腐蚀吐息 10、8 格、1 秒、腐蚀 5 秒；熄光 1 秒预警、6 秒、2 次暗袭 14；长夜 10 秒、4 次；震波 12 + 5%、3 圈、每圈 0.75 秒；
         * 断层 5 圈；落石 6 块、1 秒影子、12。
         * 加强那一轮（tools/plan/calamity_heart_v2.md §2）改成：生命 2 400、灾柱 240 / 主灾 360、两招之间 50 刻、失衡期冷却 ×0.7、经验 400；
         * 新加的：开场 120 刻、封印开、重立重新封；灾柱反噬 8 + 8%、6 格、1.5 秒；四灾归心 10 + 15%、安全圈 8 格、3 秒；
         * 换对后 60 刻放合相技；崩解里每 160 刻反扑一次，棱线齐射 5 条。
         * 这里定的：没打倒 200 刻散去（同矩阵之影）；棱线一次 3 条、同时 ≤ 4；瘴云一次 3 团、活 400 刻；
         * 腐蚀吐息另加 4%（有预警的招一律带百分比）；暗袭从起手到落下 20 刻；落石同时 ≤ 12。
         */
        public static final Values DEFAULTS = new Values(
                new Summon(true, 2, 2, 12, 8, 128, 200, 1),
                new Fight(2400D, 16D, 8D, 40D, 30D, 240D, 360D, 40D, 32D, 32D, 64D, 6D, 3D, 0.15D,
                        400, 50, 300, 0.7D, 0.2D, 600, 1.25D, 100, 24D, 6D, 0.04D, 0.35D, 2, 400),
                new Skills(12D, 0.06D, 3, 4, 30, 5, 0.2D, 80, 0.15D, 60,
                        6, 3, 400, 300,
                        10D, 0.04D, 8D, 20, 100,
                        20, 120, 2, 200, 4, 14D, 20,
                        12D, 0.05D, 3, 5, 15,
                        12, 6, 12D, 20),
                new Fury(120, true, true, 8D, 0.08D, 6D, 30, 10D, 0.15D, 8D, 60, 60, 160, 5));

        private static ForgeConfigSpec.BooleanValue requireCalamity;
        private static ForgeConfigSpec.IntValue coresPerResummon;
        private static ForgeConfigSpec.IntValue coresExtraWithoutCalamity;
        private static ForgeConfigSpec.IntValue pillarOffset;
        private static ForgeConfigSpec.IntValue maxUnevenness;
        private static ForgeConfigSpec.IntValue exclusiveRadius;
        private static ForgeConfigSpec.IntValue dissipateTicks;
        private static ForgeConfigSpec.IntValue firstKillBonus;

        private static ForgeConfigSpec.DoubleValue maxHealth;
        private static ForgeConfigSpec.DoubleValue armor;
        private static ForgeConfigSpec.DoubleValue toughness;
        private static ForgeConfigSpec.DoubleValue perHit;
        private static ForgeConfigSpec.DoubleValue perSecond;
        private static ForgeConfigSpec.DoubleValue pillarHealth;
        private static ForgeConfigSpec.DoubleValue mainPillarHealth;
        private static ForgeConfigSpec.DoubleValue pillarPerHit;
        private static ForgeConfigSpec.DoubleValue arenaRadius;
        private static ForgeConfigSpec.DoubleValue pullRange;
        private static ForgeConfigSpec.DoubleValue escapeRange;
        private static ForgeConfigSpec.DoubleValue shieldHover;
        private static ForgeConfigSpec.DoubleValue lowHover;
        private static ForgeConfigSpec.DoubleValue followSpeed;
        private static ForgeConfigSpec.IntValue shieldWindowTicks;
        private static ForgeConfigSpec.IntValue skillInterval;
        private static ForgeConfigSpec.IntValue aspectSwapTicks;
        private static ForgeConfigSpec.DoubleValue unbalancedCooldown;
        private static ForgeConfigSpec.DoubleValue collapseThreshold;
        private static ForgeConfigSpec.IntValue collapseTicks;
        private static ForgeConfigSpec.DoubleValue collapseDamageTaken;
        private static ForgeConfigSpec.IntValue pulseInterval;
        private static ForgeConfigSpec.DoubleValue pulseRange;
        private static ForgeConfigSpec.DoubleValue pulseDamage;
        private static ForgeConfigSpec.DoubleValue pulsePercent;
        private static ForgeConfigSpec.DoubleValue reraiseHealth;
        private static ForgeConfigSpec.IntValue reraisePillars;
        private static ForgeConfigSpec.IntValue experience;

        private static ForgeConfigSpec.DoubleValue prismDamage;
        private static ForgeConfigSpec.DoubleValue prismPercent;
        private static ForgeConfigSpec.IntValue prismLines;
        private static ForgeConfigSpec.IntValue maxPrisms;
        private static ForgeConfigSpec.IntValue prismWarnTicks;
        private static ForgeConfigSpec.IntValue prismFireSeconds;
        private static ForgeConfigSpec.DoubleValue markPercent;
        private static ForgeConfigSpec.IntValue markWarnTicks;
        private static ForgeConfigSpec.DoubleValue noonPercent;
        private static ForgeConfigSpec.IntValue noonWarnTicks;
        private static ForgeConfigSpec.IntValue maxFumes;
        private static ForgeConfigSpec.IntValue fumesPerCast;
        private static ForgeConfigSpec.IntValue fumeTicks;
        private static ForgeConfigSpec.IntValue echoFumeTicks;
        private static ForgeConfigSpec.DoubleValue breathDamage;
        private static ForgeConfigSpec.DoubleValue breathPercent;
        private static ForgeConfigSpec.DoubleValue breathRange;
        private static ForgeConfigSpec.IntValue breathWarnTicks;
        private static ForgeConfigSpec.IntValue corrosionTicks;
        private static ForgeConfigSpec.IntValue blackoutWarnTicks;
        private static ForgeConfigSpec.IntValue blackoutTicks;
        private static ForgeConfigSpec.IntValue blackoutStrikes;
        private static ForgeConfigSpec.IntValue longNightTicks;
        private static ForgeConfigSpec.IntValue longNightStrikes;
        private static ForgeConfigSpec.DoubleValue strikeDamage;
        private static ForgeConfigSpec.IntValue strikeWarnTicks;
        private static ForgeConfigSpec.DoubleValue quakeDamage;
        private static ForgeConfigSpec.DoubleValue quakePercent;
        private static ForgeConfigSpec.IntValue quakeRings;
        private static ForgeConfigSpec.IntValue faultRings;
        private static ForgeConfigSpec.IntValue ringWarnTicks;
        private static ForgeConfigSpec.IntValue maxRubble;
        private static ForgeConfigSpec.IntValue rubbleCount;
        private static ForgeConfigSpec.DoubleValue rubbleDamage;
        private static ForgeConfigSpec.IntValue rubbleWarnTicks;

        private static ForgeConfigSpec.IntValue introTicks;
        private static ForgeConfigSpec.BooleanValue seal;
        private static ForgeConfigSpec.BooleanValue resealOnReraise;
        private static ForgeConfigSpec.DoubleValue backlashDamage;
        private static ForgeConfigSpec.DoubleValue backlashPercent;
        private static ForgeConfigSpec.DoubleValue backlashRadius;
        private static ForgeConfigSpec.IntValue backlashWarnTicks;
        private static ForgeConfigSpec.DoubleValue convergeDamage;
        private static ForgeConfigSpec.DoubleValue convergePercent;
        private static ForgeConfigSpec.DoubleValue convergeRadius;
        private static ForgeConfigSpec.IntValue convergeWarnTicks;
        private static ForgeConfigSpec.IntValue comboDelayTicks;
        private static ForgeConfigSpec.IntValue collapseEchoInterval;
        private static ForgeConfigSpec.IntValue volleyLines;

        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        private static ForgeConfigSpec.DoubleValue num(ForgeConfigSpec.Builder b, String key, String en, String zh, double def, double min, double max) {
            return b.comment(en, zh).defineInRange(key, def, min, max);
        }

        private static ForgeConfigSpec.IntValue num(ForgeConfigSpec.Builder b, String key, String en, String zh, int def, int min, int max) {
            return b.comment(en, zh).defineInRange(key, def, min, max);
        }

        public static void define(ForgeConfigSpec.Builder b) {
            Summon s = DEFAULTS.summon();
            Fight f = DEFAULTS.fight();
            Skills k = DEFAULTS.skills();
            Fury u = DEFAULTS.fury();
            b.comment("Heart of Calamity: the final boss of Hoanna, summoned with a Calamity Beacon.",
                    "天灾之心：拿天灾信标在海纳尔召出来的终局首领").push("calamity_heart");

            requireCalamity = b.comment("Only summonable while Hoanna has a calamity (ley storms count). Off = a random main calamity, "
                                    + "and every summon costs cores_extra_without_calamity more Shade Cores. If calamities can never start on their own "
                                    + "(general.hoanna_events.chance_multiplier = 0, or every calamity turned off), this counts as off.",
                            "要海纳尔此刻有天灾（含地脉风暴）才召得出来。关掉时主灾随机，每次多吃 cores_extra_without_calamity 个暗影核心。"
                                    + "天灾不会自己发生时（general.hoanna_events.chance_multiplier 为 0，或者天灾全关了）按关掉处理")
                    .define("require_calamity", s.requireCalamity());
            coresPerResummon = num(b, "cores_per_resummon", "Shade Cores a spent beacon costs per summon (a fresh beacon costs none).",
                    "空信标每召一次吃几个暗影核心（新合成的满信标第一次不吃）", s.coresPerResummon(), 0, 64);
            coresExtraWithoutCalamity = num(b, "cores_extra_without_calamity", "Extra Shade Cores per summon while require_calamity is off.",
                    "关掉 require_calamity 时每次多吃几个暗影核心", s.coresExtraWithoutCalamity(), 0, 64);
            pillarOffset = num(b, "pillar_offset", "The four pillars stand this many blocks east, west, north and south of the summon point; "
                            + "the ground flatness is sampled over the square they span.",
                    "四根灾柱离召唤点几格（东西南北各一根）；平整度在它们围出来的方块里抽 25 列", s.pillarOffset(), 2, 32);
            maxUnevenness = num(b, "max_unevenness", "The 25 sampled columns may differ in height by at most this much.",
                    "抽的 25 列地面最高最低差不超过多少", s.maxUnevenness(), 0, 64);
            exclusiveRadius = num(b, "exclusive_radius", "No summoning while another Heart of Calamity or Matrix Specter is this close.",
                    "这么远以内有别的天灾之心 / 矩阵之影时召不出来", s.exclusiveRadius(), 4, 512);
            dissipateTicks = num(b, "dissipate_ticks", "When a fight is lost, the boss fades away after this many ticks.",
                    "没打倒时多少刻后散去", s.dissipateTicks(), 1, 72_000);
            firstKillBonus = num(b, "first_kill_bonus", "Extra Calamity Remnants for each player's first win.",
                    "每人第一次打倒多给几个天灾残核", s.firstKillBonus(), 0, 64);

            maxHealth = num(b, "max_health", "Max health.", "生命", f.maxHealth(), 1D, 100_000D);
            armor = num(b, "armor", "Armor.", "护甲", f.armor(), 0D, 30D);
            toughness = num(b, "armor_toughness", "Armor toughness.", "护甲韧性", f.toughness(), 0D, 20D);
            perHit = num(b, "per_hit_cap", "Damage cap per hit (0 = none).", "单次封顶（0 = 不封）", f.perHit(), 0D, 10_000D);
            perSecond = num(b, "per_second_cap", "Damage it can take per second (0 = no limit).", "每秒最多挨多少（0 = 不限）",
                    f.perSecond(), 0D, 10_000D);
            pillarHealth = num(b, "pillar_health", "Health of a pillar.", "灾柱的生命", f.pillarHealth(), 1D, 10_000D);
            mainPillarHealth = num(b, "main_pillar_health", "Health of the pillar of the main calamity.", "主灾那根灾柱的生命",
                    f.mainPillarHealth(), 1D, 10_000D);
            pillarPerHit = num(b, "pillar_per_hit_cap", "Damage cap per hit on a pillar (0 = none).", "灾柱的单次封顶（0 = 不封）",
                    f.pillarPerHit(), 0D, 10_000D);
            arenaRadius = num(b, "arena_radius", "Radius of the arena around the summon point.", "场地半径（以召唤点为中心）",
                    f.arenaRadius(), 4D, 64D);
            pullRange = num(b, "pull_range", "Fighters further than this from the summon point are pulled back; players within this distance "
                            + "of it can hurt it. 0 = never pull, and then escape_range sets how close you must be.",
                    "参战者离召唤点超过这么远被拽回，离它这么远以内才打得动它。0 = 不拽，这时按 escape_range 算", f.pullRange(), 0D, 128D);
            escapeRange = num(b, "escape_range", "Fighters further than this leave the fight.", "超过这么远退出战斗", f.escapeRange(), 8D, 256D);
            shieldHover = num(b, "shield_hover", "Height above the ground while any pillar stands.", "护盾期悬在地面上几格",
                    f.shieldHover(), 0D, 16D);
            lowHover = num(b, "low_hover", "Height above the ground while its shield is down.", "破盾、失衡期、崩解时离地几格",
                    f.lowHover(), 0D, 16D);
            followSpeed = num(b, "follow_speed", "Blocks per tick it drifts toward the fighters once all pillars are down.",
                    "四根都倒了以后跟着参战者挪的速度（格 / 刻）", f.followSpeed(), 0D, 1D);
            shieldWindowTicks = num(b, "shield_window_ticks", "Ticks its shield stays down after a pillar falls.", "打掉一根灾柱以后破盾多少刻",
                    f.shieldWindowTicks(), 0, 6000);
            skillInterval = num(b, "skill_interval", "Minimum ticks between two skills.", "两个招式之间至少隔几刻", f.skillInterval(), 10, 100_000);
            aspectSwapTicks = num(b, "aspect_swap_ticks", "Once all pillars are down it uses two aspects at a time and swaps the pair this often.",
                    "四根都倒了以后每次用两相，每隔这么多刻换一对", f.aspectSwapTicks(), 20, 12_000);
            unbalancedCooldown = num(b, "unbalanced_cooldown_multiplier", "Cooldown multiplier once all pillars are down.",
                    "四根都倒了以后冷却乘多少", f.unbalancedCooldown(), 0.1D, 1D);
            collapseThreshold = num(b, "collapse_threshold", "Below this share of health it starts to collapse.", "生命低于这个比例开始崩解",
                    f.collapseThreshold(), 0D, 0.9D);
            collapseTicks = num(b, "collapse_ticks", "Length of the collapse countdown.", "崩解倒计时多少刻", f.collapseTicks(), 20, 12_000);
            collapseDamageTaken = num(b, "collapse_damage_taken", "Damage it takes while collapsing is multiplied by this.",
                    "崩解时挨打乘多少", f.collapseDamageTaken(), 1D, 4D);
            pulseInterval = num(b, "pulse_interval", "Ticks between two pulses while collapsing.", "崩解时每多少刻一次脉冲",
                    f.pulseInterval(), 10, 1200);
            pulseRange = num(b, "pulse_range", "Pulses hit fighters within this distance.", "脉冲打多远以内的参战者", f.pulseRange(), 1D, 64D);
            pulseDamage = num(b, "pulse_damage", "Pulse: flat damage (ignores armor).", "脉冲：固定伤害（无视护甲）", f.pulseDamage(), 0D, 1000D);
            pulsePercent = num(b, "pulse_health_percent", "Pulse: plus this share of the target's max health.", "脉冲：再加目标最大生命的这个比例",
                    f.pulsePercent(), 0D, 0.2D);
            reraiseHealth = num(b, "reraise_health", "When the first countdown runs out it goes back to this share of health.",
                    "第一次倒计时走完回到生命的这个比例", f.reraiseHealth(), 0D, 1D);
            reraisePillars = num(b, "reraise_pillars", "Pillars raised again when the first countdown runs out (only once).",
                    "第一次倒计时走完重立几根灾柱（只一次）", f.reraisePillars(), 0, 4);
            experience = num(b, "experience", "Experience dropped.", "掉多少经验", f.experience(), 0, 100_000);

            prismDamage = num(b, "prism_damage", "Prism Line: flat damage (sunlight, ignores armor, 1.5 s gold warning line).",
                    "天光棱线：固定伤害（日光，无视护甲，1.5 秒地面金线预警）", k.prismDamage(), 0D, 1000D);
            prismPercent = num(b, "prism_health_percent", "Prism Line: plus this share of the target's max health.",
                    "天光棱线：再加目标最大生命的这个比例", k.prismPercent(), 0D, 0.2D);
            prismLines = num(b, "prism_lines", "Prism Lines per cast.", "天光棱线一次几条", k.prismLines(), 0, 8);
            maxPrisms = num(b, "max_prisms", "Prism Lines at once.", "天光棱线同时最多几条", k.maxPrisms(), 0, 16);
            prismWarnTicks = num(b, "prism_warning_ticks", "Ticks of warning before a Prism Line strikes.", "天光棱线预警多少刻",
                    k.prismWarnTicks(), 1, 200);
            prismFireSeconds = num(b, "prism_fire_seconds", "Seconds a Prism Line sets you on fire.", "天光棱线打中后着火几秒",
                    k.prismFireSeconds(), 0, 60);
            markPercent = num(b, "mark_health_percent", "Sun Mark: share of max health if the sky is open 2 blocks above your head when it lands.",
                    "烈日标记：落下那一刻你头顶上方 2 格还看得见天，挨最大生命的这个比例", k.markPercent(), 0D, 0.2D);
            markWarnTicks = num(b, "mark_warning_ticks", "Ticks before a Sun Mark lands.", "烈日标记多少刻后落下", k.markWarnTicks(), 1, 400);
            noonPercent = num(b, "noon_health_percent", "Echo of Noon: share of max health for every fighter under open sky.",
                    "残响 · 正午：露天的参战者挨最大生命的这个比例", k.noonPercent(), 0D, 0.2D);
            noonWarnTicks = num(b, "noon_warning_ticks", "Ticks of warning before the Echo of Noon.", "残响 · 正午预警多少刻",
                    k.noonWarnTicks(), 1, 400);
            maxFumes = num(b, "max_fumes", "Poison fumes it keeps at once (the Echo of Miasma adds as many again).",
                    "毒雾同时最多几团（残响 · 弥漫另放一份同样多的）", k.maxFumes(), 0, 16);
            fumesPerCast = num(b, "fumes_per_cast", "Fumes per Miasma cast.", "瘴云一次放几团", k.fumesPerCast(), 0, 16);
            fumeTicks = num(b, "fume_ticks", "Ticks a fume from Miasma lasts.", "瘴云放出的毒雾活多久", k.fumeTicks(), 20, 1000);
            echoFumeTicks = num(b, "echo_fume_ticks", "Ticks the fumes from the Echo of Miasma last.", "残响 · 弥漫的毒雾活多久",
                    k.echoFumeTicks(), 20, 1000);
            breathDamage = num(b, "breath_damage", "Corrosive Breath: flat damage (ignores armor, 1 s warning).", "腐蚀吐息：固定伤害（无视护甲，1 秒预警）",
                    k.breathDamage(), 0D, 1000D);
            breathPercent = num(b, "breath_health_percent", "Corrosive Breath: plus this share of the target's max health.",
                    "腐蚀吐息：再加目标最大生命的这个比例", k.breathPercent(), 0D, 0.2D);
            breathRange = num(b, "breath_range", "Corrosive Breath reaches this far.", "腐蚀吐息够多远", k.breathRange(), 1D, 32D);
            breathWarnTicks = num(b, "breath_warning_ticks", "Ticks of warning before the breath.", "腐蚀吐息预警多少刻",
                    k.breathWarnTicks(), 1, 200);
            corrosionTicks = num(b, "corrosion_ticks", "Ticks of Corrosion (no healing) from the breath.", "腐蚀吐息给的腐蚀（不能回血）多少刻",
                    k.corrosionTicks(), 0, 1200);
            blackoutWarnTicks = num(b, "blackout_warning_ticks", "Ticks of warning before a Blackout.", "熄光预警多少刻",
                    k.blackoutWarnTicks(), 1, 200);
            blackoutTicks = num(b, "blackout_ticks", "Ticks a Blackout lasts.", "熄光多少刻", k.blackoutTicks(), 20, 1200);
            blackoutStrikes = num(b, "blackout_strikes", "Shadow strikes during a Blackout.", "熄光里几次暗袭", k.blackoutStrikes(), 0, 16);
            longNightTicks = num(b, "long_night_ticks", "Ticks the Echo of the Long Night lasts.", "残响 · 长夜多少刻", k.longNightTicks(), 20, 2400);
            longNightStrikes = num(b, "long_night_strikes", "Shadow strikes during the Long Night.", "残响 · 长夜几次暗袭",
                    k.longNightStrikes(), 0, 16);
            strikeDamage = num(b, "strike_damage", "Damage of a shadow strike (reduced by armor). Holding a light source shows its path.",
                    "暗袭一下的伤害（吃护甲）；手里拿着光源看得见它的轨迹", k.strikeDamage(), 0D, 1000D);
            strikeWarnTicks = num(b, "strike_warning_ticks", "Ticks between a shadow strike's wind-up and its hit (step out of its path).",
                    "暗袭从起手到落下几刻（躲开轨迹就不挨）", k.strikeWarnTicks(), 1, 100);
            quakeDamage = num(b, "quake_damage", "Shockwave: flat damage if you are on the ground when a ring passes (ignores armor).",
                    "震波：圈扫过时你脚踩地面就挨的固定伤害（无视护甲）", k.quakeDamage(), 0D, 1000D);
            quakePercent = num(b, "quake_health_percent", "Shockwave: plus this share of the target's max health.", "震波：再加目标最大生命的这个比例",
                    k.quakePercent(), 0D, 0.2D);
            quakeRings = num(b, "quake_rings", "Rings per Shockwave.", "震波一次几圈", k.quakeRings(), 0, 16);
            faultRings = num(b, "fault_rings", "Rings in the Echo of the Fault.", "残响 · 断层几圈", k.faultRings(), 0, 16);
            ringWarnTicks = num(b, "ring_warning_ticks", "Ticks of warning before each ring rolls out.", "每圈震波的预警刻数",
                    k.ringWarnTicks(), 1, 200);
            maxRubble = num(b, "max_rubble", "Falling rocks at once.", "落石同时最多几块", k.maxRubble(), 0, 32);
            rubbleCount = num(b, "rubble_count", "Falling rocks per cast.", "落石一次几块", k.rubbleCount(), 0, 32);
            rubbleDamage = num(b, "rubble_damage", "Damage of a falling rock (reduced by armor; its shadow warns you).",
                    "落石一块的伤害（吃护甲，地上先有影子）", k.rubbleDamage(), 0D, 1000D);
            rubbleWarnTicks = num(b, "rubble_warning_ticks", "Ticks a rock's shadow shows before it falls.", "落石的影子多少刻后落下",
                    k.rubbleWarnTicks(), 1, 200);

            introTicks = num(b, "intro_ticks", "Length of the opening (it and its pillars cannot be hurt and do not attack meanwhile; 0 = no opening).",
                    "开场演出多少刻（这段时间它和灾柱打不动、不出招；0 = 没有开场）", u.introTicks(), 0, 600);
            seal = b.comment("Calamity Seal: the enchantments on a fighter's gear stop working, in four parts each held by one pillar "
                                    + "(Sun Array: helmet and chestplate; Earthquake: leggings and boots; Darkness: main hand; Miasma: off hand, "
                                    + "curios and the rest of the inventory). Breaking a pillar releases its part; the fight ending releases everything. "
                                    + "Curses are never sealed; add more to the enchantment tag essentialcraft5:calamity_seal_exempt.",
                            "天灾封印：参战者身上物品的附魔分四处失效，每处由一根灾柱压着（日阵：头盔、胸甲；地震：护腿、靴子；黑暗：主手；"
                                    + "毒雾：副手、饰品栏、背包里其余物品）。打倒灾柱解开它那一处，战斗一结束全部解开。诅咒不封；"
                                    + "别的不想封的附魔加进附魔标签 essentialcraft5:calamity_seal_exempt")
                    .define("enchant_seal", u.seal());
            resealOnReraise = b.comment("Pillars raised again after the first collapse seal their part again.",
                            "崩解后重立的灾柱把它那一处重新封上")
                    .define("reseal_on_reraise", u.resealOnReraise());
            backlashDamage = num(b, "backlash_damage", "Pillar Backlash (each pillar, the first time it drops to half health): flat damage around it (ignores armor).",
                    "灾柱反噬（每根第一次掉到一半时）：周围的人挨的固定伤害（无视护甲）", u.backlashDamage(), 0D, 1000D);
            backlashPercent = num(b, "backlash_health_percent", "Pillar Backlash: plus this share of the target's max health.",
                    "灾柱反噬：再加目标最大生命的这个比例", u.backlashPercent(), 0D, 0.2D);
            backlashRadius = num(b, "backlash_radius", "Pillar Backlash reaches this far from the pillar.", "灾柱反噬打多远（以灾柱为中心）",
                    u.backlashRadius(), 1D, 16D);
            backlashWarnTicks = num(b, "backlash_warning_ticks", "Ticks of warning before a Pillar Backlash.", "灾柱反噬预警多少刻",
                    u.backlashWarnTicks(), 1, 200);
            convergeDamage = num(b, "converge_damage", "Convergence (when the last pillar falls): flat damage to fighters outside the safe circle around it (ignores armor).",
                    "四灾归心（最后一根灾柱倒下时）：在它身边安全圈外的人挨的固定伤害（无视护甲）", u.convergeDamage(), 0D, 1000D);
            convergePercent = num(b, "converge_health_percent", "Convergence: plus this share of the target's max health.",
                    "四灾归心：再加目标最大生命的这个比例", u.convergePercent(), 0D, 0.2D);
            convergeRadius = num(b, "converge_radius", "Radius of the Convergence safe circle.", "四灾归心安全圈的半径", u.convergeRadius(), 1D, 32D);
            convergeWarnTicks = num(b, "converge_warning_ticks", "Ticks of warning before the Convergence lands.", "四灾归心预警多少刻",
                    u.convergeWarnTicks(), 1, 400);
            comboDelayTicks = num(b, "combo_delay_ticks", "Once all pillars are down, ticks after each new pair of aspects before their combined skill (0 = none).",
                    "四根都倒了以后，每换一对相过多少刻放这一对的合相技（0 = 不放）", u.comboDelayTicks(), 0, 12_000);
            collapseEchoInterval = num(b, "collapse_echo_interval", "Ticks between two Death Throes during a collapse countdown (alternating Fault rings and a Prism Volley; 0 = none).",
                    "崩解倒计时里每多少刻垂死反扑一次（交替断层和棱线齐射；0 = 不反扑）", u.collapseEchoInterval(), 0, 12_000);
            volleyLines = num(b, "volley_lines", "Prism Lines in a Prism Volley.", "棱线齐射一次几条", u.volleyLines(), 0, 16);
            b.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (requireCalamity == null) {
                return DEFAULTS;
            }
            return new Values(
                    new Summon(ECConfig.get(requireCalamity), ECConfig.get(coresPerResummon), ECConfig.get(coresExtraWithoutCalamity),
                            ECConfig.get(pillarOffset), ECConfig.get(maxUnevenness), ECConfig.get(exclusiveRadius), ECConfig.get(dissipateTicks),
                            ECConfig.get(firstKillBonus)),
                    new Fight(ECConfig.get(maxHealth), ECConfig.get(armor), ECConfig.get(toughness), ECConfig.get(perHit), ECConfig.get(perSecond),
                            ECConfig.get(pillarHealth), ECConfig.get(mainPillarHealth), ECConfig.get(pillarPerHit),
                            ECConfig.get(arenaRadius), ECConfig.get(pullRange), ECConfig.get(escapeRange), ECConfig.get(shieldHover),
                            ECConfig.get(lowHover), ECConfig.get(followSpeed), ECConfig.get(shieldWindowTicks), ECConfig.get(skillInterval),
                            ECConfig.get(aspectSwapTicks), ECConfig.get(unbalancedCooldown), ECConfig.get(collapseThreshold),
                            ECConfig.get(collapseTicks), ECConfig.get(collapseDamageTaken), ECConfig.get(pulseInterval), ECConfig.get(pulseRange),
                            ECConfig.get(pulseDamage), ECConfig.get(pulsePercent), ECConfig.get(reraiseHealth), ECConfig.get(reraisePillars),
                            ECConfig.get(experience)),
                    new Skills(ECConfig.get(prismDamage), ECConfig.get(prismPercent), ECConfig.get(prismLines), ECConfig.get(maxPrisms),
                            ECConfig.get(prismWarnTicks), ECConfig.get(prismFireSeconds), ECConfig.get(markPercent), ECConfig.get(markWarnTicks),
                            ECConfig.get(noonPercent), ECConfig.get(noonWarnTicks), ECConfig.get(maxFumes), ECConfig.get(fumesPerCast),
                            ECConfig.get(fumeTicks), ECConfig.get(echoFumeTicks), ECConfig.get(breathDamage), ECConfig.get(breathPercent),
                            ECConfig.get(breathRange), ECConfig.get(breathWarnTicks), ECConfig.get(corrosionTicks), ECConfig.get(blackoutWarnTicks),
                            ECConfig.get(blackoutTicks), ECConfig.get(blackoutStrikes), ECConfig.get(longNightTicks), ECConfig.get(longNightStrikes),
                            ECConfig.get(strikeDamage), ECConfig.get(strikeWarnTicks), ECConfig.get(quakeDamage), ECConfig.get(quakePercent),
                            ECConfig.get(quakeRings), ECConfig.get(faultRings), ECConfig.get(ringWarnTicks), ECConfig.get(maxRubble),
                            ECConfig.get(rubbleCount), ECConfig.get(rubbleDamage), ECConfig.get(rubbleWarnTicks)),
                    new Fury(ECConfig.get(introTicks), ECConfig.get(seal), ECConfig.get(resealOnReraise), ECConfig.get(backlashDamage),
                            ECConfig.get(backlashPercent), ECConfig.get(backlashRadius), ECConfig.get(backlashWarnTicks), ECConfig.get(convergeDamage),
                            ECConfig.get(convergePercent), ECConfig.get(convergeRadius), ECConfig.get(convergeWarnTicks), ECConfig.get(comboDelayTicks),
                            ECConfig.get(collapseEchoInterval), ECConfig.get(volleyLines)));
        }

        /** GameTest 用：整份覆盖（null = 照常读配置）。别用 ConfigValue.set 改真配置 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }

    /**
     * 客户端配置 {@code [calamity_heart_music]}（CLIENT 配置 {@code config/essentialcraft5-client.toml}，每台电脑自己定，服务器不管、不同步）：
     * {@code enabled}（默认 true）天灾之心的循环音乐。common 类，只有 ForgeConfigSpec；值在加载 / 重载时缓存进 volatile 字段。
     */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientConfig {

        public static final boolean DEFAULT_MUSIC = true;

        private static ForgeConfigSpec.BooleanValue music;
        private static volatile boolean cachedMusic = DEFAULT_MUSIC;

        private ClientConfig() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Heart of Calamity music (client only).", "天灾之心的战斗音乐（只影响这台电脑）").push("calamity_heart_music");
            music = builder.comment("Play the looping music while you fight a Heart of Calamity (Jukebox/Note Blocks volume).",
                            "打天灾之心时放循环音乐（音量跟「唱片机 / 音符盒」滑块）")
                    .define("enabled", DEFAULT_MUSIC);
            builder.pop();
        }

        /** 放不放音乐 */
        public static boolean music() {
            return cachedMusic;
        }

        private static void refresh() {
            cachedMusic = music == null ? DEFAULT_MUSIC : ECConfig.getClient(music);
        }

        @SubscribeEvent
        public static void onCalamityHeartClientConfigLoading(ModConfigEvent.Loading event) {
            if (ECConfig.isClientConfig(event.getConfig())) {
                refresh();
            }
        }

        @SubscribeEvent
        public static void onCalamityHeartClientConfigReloading(ModConfigEvent.Reloading event) {
            if (ECConfig.isClientConfig(event.getConfig())) {
                refresh();
            }
        }
    }
}
