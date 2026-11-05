package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.HolopadBlock;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.ArmorDestroyerEntity;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.PlayerCloneEntity;
import com.flameforge.essentialcraft5.util.ECFlight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 全息投影台与全息投影 Boss（第八批 M1，计划 tools/plan/batch8.md §4.1）：全息投影台、全息投影、玩家克隆体、护甲破坏者、两个刷怪蛋；
 * 审判庭套装在 {@link ECInquisitorium}（只放创造栏，随本模块一起接线，见 {@link #init()}）。
 * <p>
 * 新旧注册名（总表见 tools/id_map_*.json；实体不在命名表里）：
 * <ul>
 *   <li>holopad（BlockHologramSpawner）→ {@code holopad}（{@link HolopadBlock}）</li>
 *   <li>实体 essentialcraft:entityhologram（EntityHologram）→ {@code essentialcraft5:hologram}（{@link HologramEntity}）；entityEgg:8 → hologram_spawn_egg</li>
 *   <li>实体 essentialcraft:entityplayerclone（EntityPlayerClone）→ {@code essentialcraft5:player_clone}（{@link PlayerCloneEntity}）；entityEgg:9 → player_clone_spawn_egg</li>
 *   <li>实体 essentialcraft:entityarmordestroyer（EntityArmorDestroyer）→ {@code essentialcraft5:armor_destroyer}（{@link ArmorDestroyerEntity}，没有刷怪蛋）</li>
 *   <li>robocalypse（ItemRecordEC，原作把曲名拼错了）→ music_disc_ropocalypse（照原版唱片的 music_disc_* 写法）</li>
 * </ul>
 * 数据：配方 {@code recipes/hologram/holopad.json}（照 1.10.2 代码配方：中间永恒物质、下面富神经元护板）；四张掉落表
 * {@code loot_tables/entities/hologram_{addition,subtraction,multiplication,division}.json}（tools/gen/hoanna_loot.py 生成）；
 * 实体标签 {@code forge:bosses} 加了全息投影（{@code data/forge/tags/entity_types/bosses.json}）；进度 {@code guide/hologram/holopad}、
 * {@code guide/hologram/summon}、{@code main/hologram_big}。文字 tools/lang/keys/echologram.json；客户端 client/HologramClient；测试 gametest/HologramTests。
 * 配置：服务端 {@code general.hologram}（{@link Config}）。
 *
 * <h2>全息唱片</h2>
 * Kevin MacLeod 的「Ropocolypse 2」（incompetech.com，CC BY 4.0，署名在 CREDITS.md「Music」；原作语言文件拼成 Ropocalypse 2），
 * 107.04 秒 = {@value #DISC_LENGTH_TICKS} 刻，一组 1 个；进 {@code minecraft:music_discs}，不进 {@code creeper_drop_music_discs}（原作苦力怕不掉它）。
 * 魔力唱片机按 {@code instanceof RecordItem} 收唱片，自动认它。描述键 {@code item.essentialcraft5.music_disc_ropocalypse.desc}（tools/lang/keys/framework8b.json）。
 *
 * <h2>音效</h2>
 * 文件是原作 0d0e9a1 随 Boss 加入的（第八批照第七批 D3 的规矩原样收录，tools/asset_map.tsv 的 SOUND 行），定义在 tools/sounds/echologram.json：
 * <ul>
 *   <li>{@code sound.mob.hologram.stop}（{@code hologram_rest}）：出招时放一次（音高 2）、收招再放一次（音高 0.5）；</li>
 *   <li>{@code sound.mob.hologram.damage.melee} / {@code .projectile}：被近战（音量 0.3）/ 远程（音量 5）打中；</li>
 *   <li>{@code sound.mob.hologram.shutdown}：死亡；</li>
 *   <li>{@code records.hologram}（{@code records/hologram_battle}，流式）：全息唱片和 Boss 战音乐共用。</li>
 * </ul>
 * 原作的 {@code sound.mob.hologram.start}（{@code hologram_attack.ogg}）注册了从没播放过，不导入。轨道打击的充能声 {@code sound.orbital_strike} 在 ECHoloWeapons。
 */
public final class ECHologram {

    /** 全息唱片长度：107.04 秒 × 20 取整 */
    public static final int DISC_LENGTH_TICKS = 2141;
    /**
     * 唱片机比较器输出（1.20.1 每张唱片自己给一个 1~15 的数）。原作 1.12 的比较器输出按物品数字 id 推算，模组唱片的数没有意义，这里取 15
     */
    public static final int DISC_COMPARATOR = 15;

    public static final RegistryObject<SoundEvent> SOUND_STOP = ECSounds.register("sound.mob.hologram.stop");
    public static final RegistryObject<SoundEvent> SOUND_DAMAGE_MELEE = ECSounds.register("sound.mob.hologram.damage.melee");
    public static final RegistryObject<SoundEvent> SOUND_DAMAGE_PROJECTILE = ECSounds.register("sound.mob.hologram.damage.projectile");
    public static final RegistryObject<SoundEvent> SOUND_SHUTDOWN = ECSounds.register("sound.mob.hologram.shutdown");
    /** 全息唱片与 Boss 战音乐（Boss 战音乐由 client/sound/HologramMusicSound 绑在实体上循环放，声音类别 RECORDS） */
    public static final RegistryObject<SoundEvent> RECORD = ECSounds.register("records.hologram");

    /** robocalypse：全息投影掉落 30%（四张掉落表各一池） */
    public static final RegistryObject<RecordItem> MUSIC_DISC_ROPOCALYPSE = ECItems.register("music_disc_ropocalypse",
            () -> new RecordItem(DISC_COMPARATOR, RECORD, new Item.Properties().stacksTo(1).rarity(Rarity.RARE), DISC_LENGTH_TICKS),
            ItemSpec.generated("item/record_holobattle").tags(ItemTags.MUSIC_DISCS));

    /** 进度「幻影斗士」：父 main/demon_trade，条件 minecraft:impossible，Boss 死时由代码发给在线、同维度的参战者 */
    public static final ResourceLocation ADVANCEMENT_HOLOGRAM = EssentialCraft5.id("main/hologram");
    /** 进度「兰博」：同上，另要四个盔甲槽全空（原作只写了注释，没发过） */
    public static final ResourceLocation ADVANCEMENT_HOLOGRAM_BIG = EssentialCraft5.id("main/hologram_big");
    /** 两个代码发的进度里唯一的条件名（JSON 里的 minecraft:impossible） */
    public static final String ADVANCEMENT_CRITERION = "defeated";

    // ---------------- 全息投影台 ----------------

    /**
     * 原作 registerBlockSimple(new BlockHologramSpawner(), "holopad", 1, 1, 15)：Material.ROCK、MapColor.QUARTZ，先抗爆后硬度（硬度 1、抗爆 1）；
     * 模型是原版下半台阶（六面同一张 {@code block/holopad}），碰撞箱也是半格。原作 Material.ROCK 要镐子才掉，这里照五芒星的先例空手也掉自己：
     * 它要一颗下界之星和一个虚空核心，徒手敲掉就没了太亏。
     */
    public static final RegistryObject<HolopadBlock> HOLOPAD = ECBlocks.register("holopad",
            () -> new HolopadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.STONE)
                    .strength(1F, ECBlocks.legacyResistance(1F, 1F, true))),
            BlockSpec.of((provider, block) -> provider.simpleBlockWithItem(block,
                    provider.models().slab(provider.name(block), provider.tex("block/holopad"), provider.tex("block/holopad"),
                            provider.tex("block/holopad")))));

    // ---------------- 实体 ----------------

    /**
     * 原作 registerEntity(EntityHologram.class, 32, 1, true)：追踪改成 5 区块（参战者离到 64 格脱战之前都看得到血条、听得到音乐；
     * 服务器视距小于 5 区块时以视距为准）。体型 0.6 × 1.8（原作 EntityLiving 默认），不怕火。
     * 不实现 Enemy（同恶魔：本模组别的「只打敌对生物」的东西都不打它）；MISC 类别：不占怪物刷怪上限，和平难度由它自己消失（见实体类注释）。
     */
    public static final RegistryObject<EntityType<HologramEntity>> HOLOGRAM = ECEntities.register("hologram",
            EntityType.Builder.<HologramEntity>of(HologramEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).fireImmune().clientTrackingRange(5).updateInterval(3));

    /** 原作 registerEntity(EntityPlayerClone.class, 32, 1, true)，继承僵尸（0.6 × 1.95）；这里继承 Monster、不存盘（只活 10 秒） */
    public static final RegistryObject<EntityType<PlayerCloneEntity>> PLAYER_CLONE = ECEntities.register("player_clone",
            EntityType.Builder.<PlayerCloneEntity>of(PlayerCloneEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).noSave().clientTrackingRange(4).updateInterval(3));

    /**
     * 原作 registerEntity(EntityArmorDestroyer.class, 32, 1, true)，EntityThrowable 的 0.25 × 0.25；同步照原版投掷物（10 刻、带速度）。
     * 不存盘；不能 /summon（没有放出它的全息投影就认不出参战者，谁也打不中）。
     */
    public static final RegistryObject<EntityType<ArmorDestroyerEntity>> ARMOR_DESTROYER = ECEntities.register("armor_destroyer",
            EntityType.Builder.<ArmorDestroyerEntity>of(ArmorDestroyerEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).noSave().noSummon().clientTrackingRange(4).updateInterval(10));

    /** 蛋壳取 boss.png 的暗紫红，斑点取二分场的品红（原作刷怪蛋是纯白的） */
    public static final int HOLOGRAM_EGG_BACKGROUND = 0x2C2027;
    public static final int HOLOGRAM_EGG_HIGHLIGHT = 0xFF00FF;
    /** 蛋壳取史蒂夫上衣的青色，斑点取裤子的蓝紫 */
    public static final int PLAYER_CLONE_EGG_BACKGROUND = 0x00A8A8;
    public static final int PLAYER_CLONE_EGG_HIGHLIGHT = 0x463AA5;

    /** 只放创造栏、点刷怪笼不改类型；全息投影来自刷怪蛋 / 发射器时照样查维度和 64 格（{@code entity/HologramEvents}） */
    public static final RegistryObject<ECEntities.SpawnEgg> HOLOGRAM_SPAWN_EGG =
            ECEntities.spawnEgg("hologram_spawn_egg", HOLOGRAM, HOLOGRAM_EGG_BACKGROUND, HOLOGRAM_EGG_HIGHLIGHT);
    public static final RegistryObject<ECEntities.SpawnEgg> PLAYER_CLONE_SPAWN_EGG =
            ECEntities.spawnEgg("player_clone_spawn_egg", PLAYER_CLONE, PLAYER_CLONE_EGG_BACKGROUND, PLAYER_CLONE_EGG_HIGHLIGHT);

    private ECHologram() {
    }

    /**
     * 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister；这里登记实体属性、服务端配置 general.hologram、
     * 参战者不能飞的压制器，并带上审判庭套装（{@link ECInquisitorium#init()} 只生效一次，集成时另接一行也不会重复登记）。
     */
    public static void init() {
        ECEntities.attributes(HOLOGRAM, HologramEntity::createAttributes);
        ECEntities.attributes(PLAYER_CLONE, PlayerCloneEntity::createAttributes);
        ECConfig.general(Config::define);
        ECFlight.registerSuppressor(HologramEntity::isEngaged);
        ECInquisitorium.init();
    }

    /**
     * 服务端配置 {@code general.hologram}（batch8.md §4.1「配置」、§6.2）：三个封顶（D2 = A）、实体上限、脱战与放弃、维度开关。
     * 封顶都能调回原作：{@code max_damage_per_hit} 0 = 不封顶、{@code orbital_max_damage} 1024 = 原作 10 发翻到 512 不受影响。
     * GameTest 用 {@link #setTestOverrides}（整套换掉，不碰真配置），设、用、置回都在同一段同步代码里。
     */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param maxHealth            全息投影的最大生命（召出时设，已经在世界里的不变）
         * @param maxDamagePerHit      单次受伤封顶（0 = 不封顶）
         * @param orbitalMaxDamage     乘法攻击轨道打击翻倍的封顶
         * @param cloneMaxAttackDamage 玩家克隆体攻击力封顶（难度缩放之前）
         * @param maxClones            每只全息投影同时最多几只玩家克隆体
         * @param maxArmorDestroyers   每只全息投影同时最多几发护甲破坏者
         * @param maxDividers          每只全息投影同时最多几个二分场
         * @param escapeRange          参战者离它超过几格就脱战（24 格以内不拉回）
         * @param abandonTicks         没人参战时多少刻后消失
         * @param otherDimensions      海纳尔以外的维度也能召唤（原作 AllowHologramInOtherDimensions）
         */
        public record Values(double maxHealth, double maxDamagePerHit, double orbitalMaxDamage, double cloneMaxAttackDamage,
                             int maxClones, int maxArmorDestroyers, int maxDividers, int escapeRange, int abandonTicks,
                             boolean otherDimensions) {

            /** 换掉维度开关的一份（测试常用） */
            public Values withOtherDimensions(boolean value) {
                return new Values(maxHealth, maxDamagePerHit, orbitalMaxDamage, cloneMaxAttackDamage, maxClones, maxArmorDestroyers,
                        maxDividers, escapeRange, abandonTicks, value);
            }
        }

        /** 默认值（batch8.md §4.1「配置」） */
        public static final Values DEFAULTS = new Values(400D, 40D, 32D, 10D, 12, 40, 12, 64, 600, false);

        private static ForgeConfigSpec.DoubleValue maxHealth;
        private static ForgeConfigSpec.DoubleValue maxDamagePerHit;
        private static ForgeConfigSpec.DoubleValue orbitalMaxDamage;
        private static ForgeConfigSpec.DoubleValue cloneMaxAttackDamage;
        private static ForgeConfigSpec.IntValue maxClones;
        private static ForgeConfigSpec.IntValue maxArmorDestroyers;
        private static ForgeConfigSpec.IntValue maxDividers;
        private static ForgeConfigSpec.IntValue escapeRange;
        private static ForgeConfigSpec.IntValue abandonTicks;
        private static ForgeConfigSpec.BooleanValue otherDimensions;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Holopad and the Holographic Projection boss.", "全息投影台与全息投影 Boss").push("hologram");
            maxHealth = builder.comment("Max health of a Holographic Projection (400 = original). Applies to newly summoned ones.",
                            "全息投影的最大生命（原作 400）。只对新召出来的生效")
                    .defineInRange("max_health", DEFAULTS.maxHealth(), 1D, 1024D);
            maxDamagePerHit = builder.comment("Most damage it takes from a single hit (0 = no cap, as original).",
                            "它一下最多挨多少伤害（0 = 不封顶，同原作）")
                    .defineInRange("max_damage_per_hit", DEFAULTS.maxDamagePerHit(), 0D, 1024D);
            orbitalMaxDamage = builder.comment("Cap of the doubling orbital strikes of its multiplication attack (1024 = original, 1 doubling to 512).",
                            "乘法攻击里轨道打击每发翻倍的封顶（1024 = 原作，从 1 翻到 512）")
                    .defineInRange("orbital_max_damage", DEFAULTS.orbitalMaxDamage(), 1D, 1024D);
            cloneMaxAttackDamage = builder.comment("Most attack damage of a player clone (3 + the copied weapon; before difficulty scaling).",
                            "玩家克隆体的攻击力上限（3 + 被复制的武器加成；难度缩放之前）")
                    .defineInRange("clone_max_attack_damage", DEFAULTS.cloneMaxAttackDamage(), 1D, 100D);
            maxClones = builder.comment("Most player clones alive at once per Holographic Projection.", "每只全息投影同时最多几只玩家克隆体")
                    .defineInRange("max_clones", DEFAULTS.maxClones(), 0, 64);
            maxArmorDestroyers = builder.comment("Most armor destroyers in flight at once per Holographic Projection.",
                            "每只全息投影同时最多几发护甲破坏者")
                    .defineInRange("max_armor_destroyers", DEFAULTS.maxArmorDestroyers(), 0, 256);
            maxDividers = builder.comment("Most divider fields at once per Holographic Projection.", "每只全息投影同时最多几个二分场")
                    .defineInRange("max_dividers", DEFAULTS.maxDividers(), 0, 64);
            escapeRange = builder.comment("A fighter farther than this (blocks) leaves the fight; between 24 and this they are pulled back.",
                            "参战者离它超过几格就脱战；24 格到这个距离之间会被拉回")
                    .defineInRange("escape_range", DEFAULTS.escapeRange(), 24, 256);
            abandonTicks = builder.comment("Ticks with nobody fighting before it shuts down (a holopad-summoned one drops its holopad back).",
                            "Also: a fighter beyond the pull-back distance who can't be pulled back (spot blocked, not allowed to build there, teleport stopped)",
                            "for this long in a row leaves the fight.",
                            "没人参战多少 tick 后它消失（投影台召出来的掉回投影台）。",
                            "参战者在拉回距离以外、连着这么久拉不回来（落点被堵、他在落点没有权限、传送被拦），也按脱战移出")
                    .defineInRange("abandon_ticks", DEFAULTS.abandonTicks(), 100, 72_000);
            otherDimensions = builder.comment("Allow summoning outside Hoanna (holopad and spawn egg; original AllowHologramInOtherDimensions).",
                            "海纳尔以外的维度也能召唤（投影台和刷怪蛋；原作 AllowHologramInOtherDimensions）")
                    .define("other_dimensions", DEFAULTS.otherDimensions());
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (maxHealth == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(maxHealth), ECConfig.get(maxDamagePerHit), ECConfig.get(orbitalMaxDamage),
                    ECConfig.get(cloneMaxAttackDamage), ECConfig.get(maxClones), ECConfig.get(maxArmorDestroyers), ECConfig.get(maxDividers),
                    ECConfig.get(escapeRange), ECConfig.get(abandonTicks), ECConfig.get(otherDimensions));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
