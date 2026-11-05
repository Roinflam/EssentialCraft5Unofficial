package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.flameforge.essentialcraft5.entity.DividerProjectileEntity;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.item.DividerGunItem;
import com.flameforge.essentialcraft5.item.OrbitalRemoteItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 第八批「轨道打击遥控器与二分枪」模块的注册类（计划 tools/plan/batch8.md §3.1 第 5 条、§4.3）：
 * <ul>
 *   <li>两个技能实体 {@link #ORBITAL_STRIKE}、{@link #DIVIDER_FIELD}（框架做的）：全息投影（ECHologram）和两件武器共用，
 *       别的模块只调 {@code OrbitalStrikeEntity.spawn} / {@code DividerFieldEntity.spawn}。</li>
 *   <li>两件武器 {@link #ORBITAL_REMOTE}、{@link #DIVIDER_GUN}：行为在 {@link OrbitalRemoteItem}、{@link DividerGunItem} 的类注释里；
 *       只有全息投影的稀有掉落，没有配方。二分枪射出的弹丸是 {@link #DIVIDER_PROJECTILE}（{@link DividerProjectileEntity}）。</li>
 *   <li>充能声 {@link #SOUND_ORBITAL_STRIKE}、实体标签 {@link #DIVIDER_IMMUNE}；服务端配置 {@link Config}（{@code general.orbital_remote}、{@code general.divider_gun}）。</li>
 *   <li>进度 {@code main/hologram_remote}「红色大按钮」、{@code main/hologram_gun}「宇宙的数学」：拿到物品就给（{@code inventory_changed}），父进度 {@code main/hologram}。</li>
 * </ul>
 * 新旧名（物品在总表 tools/id_map_items.json；实体不在命名表里）：
 * <ul>
 *   <li>orbitalRemote（ItemOrbitalRemote）→ {@code orbital_remote}（{@link OrbitalRemoteItem}）；dividerGun（ItemDividerGun）→ {@code divider_gun}（{@link DividerGunItem}）。
 *       两件都叠 1、手持模型（原作 essentialcraft:tool），贴图 item/tools/orbitalstrikeremote、item/tools/dividinggun。</li>
 *   <li>实体 essentialcraft:entityorbitalstrike（EntityOrbitalStrike）→ {@code essentialcraft5:orbital_strike}（{@link OrbitalStrikeEntity}）；
 *       essentialcraft:entitydivider（EntityDivider）→ {@code essentialcraft5:divider_field}（{@link DividerFieldEntity}）；
 *       essentialcraft:entitydividerprojectile（EntityDividerProjectile）→ {@code essentialcraft5:divider_projectile}（{@link DividerProjectileEntity}）。</li>
 *   <li>音效 sound.orbital_strike 注册名不变（原作 sound/orbital_charge.ogg，定义在 tools/sounds/echoloweapons.json）。</li>
 * </ul>
 * 伤害类型 {@code orbital_strike} / {@code divider} 在 {@link ECDamageTypes}；技能实体的文字在 tools/lang/keys/framework8a.json，
 * 两件武器、二分弹、两个进度的文字在 tools/lang/keys/echoloweapons.json。客户端：技能实体 client/HoloStrikeClient，
 * 遥控器的预览光柱和二分弹 client/HoloWeaponsClient。测试 gametest/HoloStrikeFrameworkTests（技能实体）、gametest/HoloWeaponsTests（两件武器）。
 */
public final class ECHoloWeapons {

    // ---------------- 共用技能实体 ----------------

    /**
     * 原作 registerEntity(EntityOrbitalStrike.class, 32, 1, true)：追踪改成 4 区块（光柱高到建筑上限，遥控器射程 32 格，站在射程边上也要看得见）。
     * 同步字段只在放下时写一次；不存盘、不能 /summon（见实体类注释）。
     * 位置每刻检查一次（第十批 10b 轨道锁定：打击点跟着锁定的目标走，客户端的光柱要跟上）——原版只在位置真变了时发移动包，
     * 不动的打击每 60 刻才补一个，和以前「只发一次」差不多。
     */
    public static final RegistryObject<EntityType<OrbitalStrikeEntity>> ORBITAL_STRIKE = ECEntities.register("orbital_strike",
            EntityType.Builder.<OrbitalStrikeEntity>of(OrbitalStrikeEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).fireImmune().noSave().noSummon().clientTrackingRange(4).updateInterval(1));

    /** 原作 registerEntity(EntityDivider.class, 32, 1, true)：追踪同上改成 4 区块；不动，同步只在放下时一次 */
    public static final RegistryObject<EntityType<DividerFieldEntity>> DIVIDER_FIELD = ECEntities.register("divider_field",
            EntityType.Builder.<DividerFieldEntity>of(DividerFieldEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).fireImmune().noSave().noSummon().clientTrackingRange(4).updateInterval(Integer.MAX_VALUE));

    /**
     * 二分弹（原作 registerEntity(EntityDividerProjectile.class, 32, 1, true)）：大小、追踪、同步间隔照原版雪球（0.25、4 区块、10 刻）；
     * 不存盘；不能 /summon（没有射手的弹丸落地也不会张开二分场，召出来没有意义）。
     */
    public static final RegistryObject<EntityType<DividerProjectileEntity>> DIVIDER_PROJECTILE = ECEntities.register("divider_projectile",
            EntityType.Builder.<DividerProjectileEntity>of(DividerProjectileEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).noSave().noSummon().clientTrackingRange(4).updateInterval(10));

    /**
     * 二分场一律不碰的实体（{@code data/essentialcraft5/tags/entity_types/divider_immune.json}，默认 {@code essentialcraft5:hologram}，
     * 写成 {@code required: false}，全息投影注册以后自动生效）
     */
    public static final TagKey<EntityType<?>> DIVIDER_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id("divider_immune"));

    /** 轨道打击充能声（原作 SoundRegistry，第 3 刻放一次） */
    public static final RegistryObject<SoundEvent> SOUND_ORBITAL_STRIKE = ECSounds.register("sound.orbital_strike");

    // ---------------- 两件武器 ----------------

    public static final RegistryObject<OrbitalRemoteItem> ORBITAL_REMOTE = ECItems.register("orbital_remote",
            () -> new OrbitalRemoteItem(new Item.Properties().stacksTo(1)), ItemSpec.handheld("item/tools/orbitalstrikeremote"));

    public static final RegistryObject<DividerGunItem> DIVIDER_GUN = ECItems.register("divider_gun",
            () -> new DividerGunItem(new Item.Properties().stacksTo(1)), ItemSpec.handheld("item/tools/dividinggun"));

    /** 拿到遥控器就给的进度「红色大按钮」（{@code data/essentialcraft5/advancements/main/hologram_remote.json}） */
    public static final ResourceLocation ADVANCEMENT_REMOTE = EssentialCraft5.id("main/hologram_remote");
    /** 拿到二分枪就给的进度「宇宙的数学」（{@code data/essentialcraft5/advancements/main/hologram_gun.json}） */
    public static final ResourceLocation ADVANCEMENT_GUN = EssentialCraft5.id("main/hologram_gun");

    private ECHoloWeapons() {
    }

    public static void init() {
        ECConfig.general(Config::define);
    }

    /**
     * 服务端配置 {@code general.orbital_remote} / {@code general.divider_gun}（batch8 §6.3）。MRU、伤害、射程、延迟照原作；
     * 遥控器的冷却是新加的（原作没有冷却，带一块永恒物质按住右键每 4 刻一发）；打玩家倍率同枪械；二分枪默认不打 Boss
     * （线圈、反应堆、恶魔清怪都排除 {@code #forge:bosses}，不然凋灵 4 发削到约 19，下界之星变便宜）。
     * 第十批 10b（batch10.md §5.7.3、§5.7.4）加两项：{@code divider_gun.boss_mode_ticks}（不打首领时改挂多久的二分，0 = 维持以前的无效）、
     * {@code orbital_remote.lock_range}（准星对着多远内的生物就锁定它，0 = 不锁定）。
     * <p>
     * 读值走 {@link #values()}（配置没加载时是默认值）。GameTest 用 {@link #setTestOverrides} 整套换掉，<b>不许用 ConfigValue.set</b>
     * （服务端配置自动存盘、文件监视器后台重读会冲掉刚设的值）；覆盖值是全局的，只在同一段同步代码里设、测、置回 null。
     */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param mruPerStrike           遥控器每发花的 MRU
         * @param damage                 轨道打击打每个生物的伤害
         * @param playerDamageMultiplier 轨道打击打玩家时伤害再乘这个（0 = 不伤玩家）
         * @param cooldownTicks          遥控器用完一次的冷却（刻，0 = 没有冷却）
         * @param mruPerShot             二分枪每发花的 MRU
         * @param affectBosses           二分枪射出的场打不打 {@code #forge:bosses}
         * @param bossModeTicks          不打首领时，场里的首领改挂多久的二分（刻，0 = 不挂）
         * @param lockRange              遥控器准星对着多远内的生物就锁定它（格，0 = 不锁定）
         */
        public record Values(int mruPerStrike, double damage, double playerDamageMultiplier, int cooldownTicks,
                             int mruPerShot, boolean affectBosses, int bossModeTicks, double lockRange) {

            /** 第八批的六项（后两项取默认值），旧测试照用 */
            public Values(int mruPerStrike, double damage, double playerDamageMultiplier, int cooldownTicks,
                          int mruPerShot, boolean affectBosses) {
                this(mruPerStrike, damage, playerDamageMultiplier, cooldownTicks, mruPerShot, affectBosses,
                        DEFAULT_BOSS_MODE_TICKS, DEFAULT_LOCK_RANGE);
            }
        }

        /** 首领模式的二分时长默认值（10 秒） */
        public static final int DEFAULT_BOSS_MODE_TICKS = 200;
        /** 锁定距离默认值（同遥控器射程） */
        public static final double DEFAULT_LOCK_RANGE = 32D;

        /** 默认值：原作的 MRU 与伤害（10 000 / 128 / 5 000），冷却 5 秒，打玩家不打折，不打 Boss（改挂 10 秒二分），锁定 32 格 */
        public static final Values DEFAULTS = new Values(10000, 128D, 1D, 100, 5000, false, DEFAULT_BOSS_MODE_TICKS, DEFAULT_LOCK_RANGE);

        private static ForgeConfigSpec.IntValue mruPerStrike;
        private static ForgeConfigSpec.DoubleValue damage;
        private static ForgeConfigSpec.DoubleValue playerDamageMultiplier;
        private static ForgeConfigSpec.IntValue cooldownTicks;
        private static ForgeConfigSpec.IntValue mruPerShot;
        private static ForgeConfigSpec.BooleanValue affectBosses;
        private static ForgeConfigSpec.IntValue bossModeTicks;
        private static ForgeConfigSpec.DoubleValue lockRange;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Remote Orbital Strike.", "轨道打击遥控器").push("orbital_remote");
            mruPerStrike = builder.comment("MRU spent per strike (taken from MRU storages you carry first, then from the remote itself; "
                                    + "the remote holds 20000).",
                            "每发花多少 MRU（先扣身上的储存物品，凑不够再扣遥控器自己存的；遥控器最多存 20000）")
                    .defineInRange("mru_per_strike", DEFAULTS.mruPerStrike(), 0, 1_000_000);
            damage = builder.comment("Damage dealt to every creature in the 5x5x5 cube around the target. Armor still counts; "
                                    + "Resistance, Protection enchantments and shields do not.",
                            "落点 5×5×5 内每个生物挨的伤害。护甲照算，抗性提升、保护附魔、盾不算")
                    .defineInRange("damage", DEFAULTS.damage(), 0D, 1024D);
            playerDamageMultiplier = builder.comment("Damage multiplier when the strike hits a player (PvP, teams and claims still apply). "
                                    + "0 = players are never hurt.",
                            "打到玩家时伤害再乘这个（PvP、队伍、领地照常管）；0 = 不伤玩家")
                    .defineInRange("player_damage_multiplier", DEFAULTS.playerDamageMultiplier(), 0D, 10D);
            cooldownTicks = builder.comment("Cooldown after each strike, in ticks (20 = 1 second), shared by all remotes. 0 = no cooldown.",
                            "每用一次的冷却（刻，20 = 1 秒），所有遥控器共用；0 = 没有冷却")
                    .defineInRange("cooldown_ticks", DEFAULTS.cooldownTicks(), 0, 1200);
            lockRange = builder.comment("Lock-on: when the crosshair is on a creature within this many blocks, the strike follows it and stops "
                                    + "following 10 ticks before it lands. 0 = always strike the block you aim at.",
                            "锁定：准星对着这么多格内的生物时，打击点跟着它走，落下前 10 刻定住；0 = 一律打准星对着的方块")
                    .defineInRange("lock_range", DEFAULTS.lockRange(), 0D, 64D);
            builder.pop();

            builder.comment("Divider's Gun.", "二分枪").push("divider_gun");
            mruPerShot = builder.comment("MRU spent per shot (the gun holds 20000). You need this much when you start drawing, "
                                    + "and it is spent when the shell flies.",
                            "每发花多少 MRU（二分枪最多存 20000）。开始拉的时候就要够，射出去时才扣")
                    .defineInRange("mru_per_shot", DEFAULTS.mruPerShot(), 0, 1_000_000);
            affectBosses = builder.comment("Whether fields fired by the gun also halve bosses (#forge:bosses, e.g. the Wither). "
                                    + "The Ender Dragon and the Holographic Projection are never affected.",
                            "二分枪射出的场打不打 Boss（#forge:bosses，例如凋灵）；末影龙和全息投影一律不受影响")
                    .define("affect_bosses", DEFAULTS.affectBosses());
            bossModeTicks = builder.comment("When affect_bosses is off: bosses inside the field are not halved but get Bisected (they deal half damage and receive half healing) "
                                    + "for this many ticks. 0 = bosses are not affected at all.",
                            "affect_bosses 关着时：场里的 Boss 不减半，改挂这么多刻的「二分」（造成的伤害减半、受到的治疗减半）；0 = Boss 完全不受影响")
                    .defineInRange("boss_mode_ticks", DEFAULTS.bossModeTicks(), 0, 6000);
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (mruPerStrike == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(mruPerStrike), ECConfig.get(damage), ECConfig.get(playerDamageMultiplier), ECConfig.get(cooldownTicks),
                    ECConfig.get(mruPerShot), ECConfig.get(affectBosses), ECConfig.get(bossModeTicks), ECConfig.get(lockRange));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
