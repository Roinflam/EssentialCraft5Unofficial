package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.InquisitoriumBeaconBlock;
import com.flameforge.essentialcraft5.blockentity.InquisitoriumBeaconBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.InquisitoriumSentinelEntity;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorItem;
import com.flameforge.essentialcraft5.item.InquisitoriumChipItem;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.item.armor.ECArmorItem;
import com.flameforge.essentialcraft5.item.armor.ECArmorMaterial;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyNbtFunction;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 审判庭：第八批的审判庭套装，加上第十批 10c 的「第十九章 · 审判庭」（batch10.md §5.10）。
 *
 * <h2>审判庭套装（第八批 M1，batch8.md §4.1 末、§6.6；第十批 D22 改成生存可得）</h2>
 * 审判庭头盔 / 胸甲 / 护腿 / 靴子。新旧注册名：helm_inq / chest_inq / legs_inq / boots_inq（原作 ItemArmorEC 的 magicArmorItems[8~11]，aType 2）
 * → inquisitorium_helmet / inquisitorium_chestplate / inquisitorium_leggings / inquisitorium_boots。
 * <ul>
 *   <li>外观：原作 ModelArmorEC 形状（{@code special/armor/armorec3inquisitorium_0 / _1}，同虚空、魔力防护），物品图标是框架的 3D 盔甲图标（原作没有 2D 图标）。</li>
 *   <li>属性：原作靠 ISpecialArmor 每件吸收 10 / 25，四件 160%，穿三件就满耐久全免（和虚空套装原作的 100% 一样是数组凑出来的）；
 *       这里改成原版护甲属性，做成服务端配置 {@code general.inquisitorium_armor}（{@link Config}）：每件护甲 10、韧性 5、击退抗性 0.2，
 *       全套夹到原版上限 30 / 20，和虚空套装一样；胸甲 +30 生命、靴子移速 +0.1 照原作。</li>
 *   <li>来源：原作没有配方也不在战利品里。第十批定为生存可得（D22 A）：四件各要 1 个审判庭芯片（打倒审判庭哨兵才有），配方在
 *       {@code recipes/inquisitorium/inquisitorium_*.json}（虚空板、阿克罗尼特锭、富神经元护板）；不要 MRU，也不像虚空套装那样花 MRU 挡掉整下伤害，
 *       是给不想养电的人的一套重甲。</li>
 *   <li>耐久照原作 5 120 / 15 360 / 10 240 / 5 120，第十三批 13b 起穿在玩家身上不掉（D14）；附魔能力 60；没有修复材料。原作没有套装效果，
 *       13b 加了全套「审判庭之律」和四个同调阶（batch13.md §4.3、§4.4，{@code item/InquisitoriumArmorEvents}）。</li>
 *   <li>MRU 抗性每件 1 / 1 / 1（四件穿齐对 MRU 腐化、腐化辐射、海纳尔辐射全免，唯一像套装效果的地方）；头盔能看见 MRU
 *       （tools/tags/ecinquisitorium.json 把它加进 {@code essentialcraft5:mru_vision}）。</li>
 * </ul>
 *
 * <h2>第十九章 · 审判庭（第十批 10c-M1，batch10.md §5.10）</h2>
 * 原作研究书只留了伏笔：海纳尔某处藏着审判庭，全息投影台是向它发的信号，回应的却是全息投影。这一章把伏笔接下去：
 * <ol>
 *   <li>故障核心（{@code glitchy_core}，第六批 6b 在 ECMaterials 注册，只放创造栏）改为可得：全息投影被玩家打倒时必掉一个，
 *       带随机的目标平衡值（战利品函数 {@code essentialcraft5:random_target_balance}，{@code loot/RandomTargetBalance}；物品 {@code item/GlitchyCoreItem}）；</li>
 *   <li>解码：辐射仓的特殊分支（{@code blockentity/GlitchyCoreDecoder}），平衡值对准核心上写的目标，解出审判庭坐标；</li>
 *   <li>审判庭坐标 {@link #INQUISITORIUM_COORDINATES}：拿着走到海纳尔里那片暗影腐化之地；</li>
 *   <li>审判庭信标 {@link #INQUISITORIUM_BEACON}：坐标做进信标，放在坐标附近、夜里右键，召出审判庭哨兵 {@link #INQUISITORIUM_SENTINEL}；</li>
 *   <li>打倒哨兵掉审判庭芯片 {@link #INQUISITORIUM_CHIP}（第二十一章空白模块的料，也是审判庭套装的料）。</li>
 * </ol>
 * 新注册名（命名表 old_id 为 null，notes 写「EC5 新增（第十批 batch10.md）」）：{@code inquisitorium_coordinates}、{@code inquisitorium_chip}、
 * {@code inquisitorium_beacon}（方块 + 方块实体）、实体 {@code inquisitorium_sentinel} 与 {@code inquisitorium_sentinel_spawn_egg}（只放创造栏）。
 * 数据：群系标签 {@code essentialcraft5:inquisitorium_sites}（默认暗影腐化之地）、实体标签 {@code forge:bosses} 与 {@code essentialcraft5:divider_immune} 加了哨兵、
 * 掉落表 {@code entities/inquisitorium_sentinel} 与四张全息表的故障核心池（tools/gen/hoanna_loot.py 生成）、进度 {@code guide/inquisitorium/*}、
 * {@code main/inquisitorium}。配置：服务端 {@code general.inquisitorium}（{@link Rules}）。
 * <p>
 * 文字在 tools/lang/keys/ecinquisitorium.json；测试 gametest/InquisitoriumArmorTests、gametest/InquisitoriumTests。
 * 由 {@link ECHologram#init()} 一起接线（{@link #init()} 只生效一次）。
 */
public final class ECInquisitorium {

    /**
     * 原作 Inquisitorium（ItemsCore L468：耐久系数 64、护甲 {10,10,10,10}、附魔 60、韧性 0），耐久按件 setMaxDamage。
     * 护甲值、韧性、击退抗性写的是 {@link Config} 的默认值：属性由 {@link InquisitoriumArmorItem} 按配置给，这三个数只给原版按材料取值的地方。
     */
    public static final ECArmorMaterial MATERIAL = ECArmorMaterial.builder("inquisitorium")
            .durability(5120, 15360, 10240, 5120)
            .defense(10, 10, 10, 10)
            .enchantability(60)
            .toughness(5F)
            .knockbackResistance(0.2F)
            .build();

    public static final RegistryObject<InquisitoriumArmorItem> INQUISITORIUM_HELMET = armor("inquisitorium_helmet", ArmorItem.Type.HELMET,
            Tags.Items.ARMORS_HELMETS);
    public static final RegistryObject<InquisitoriumArmorItem> INQUISITORIUM_CHESTPLATE = armor("inquisitorium_chestplate",
            ArmorItem.Type.CHESTPLATE, Tags.Items.ARMORS_CHESTPLATES);
    public static final RegistryObject<InquisitoriumArmorItem> INQUISITORIUM_LEGGINGS = armor("inquisitorium_leggings",
            ArmorItem.Type.LEGGINGS, Tags.Items.ARMORS_LEGGINGS);
    public static final RegistryObject<InquisitoriumArmorItem> INQUISITORIUM_BOOTS = armor("inquisitorium_boots", ArmorItem.Type.BOOTS,
            Tags.Items.ARMORS_BOOTS);

    // ---------------- 第十九章 · 审判庭 ----------------

    /** 解码时坐标指向的群系（默认 {@code essentialcraft5:shadow_corruption}，静态 JSON {@code tags/worldgen/biome/inquisitorium_sites.json}） */
    public static final TagKey<Biome> INQUISITORIUM_SITES = TagKey.create(Registries.BIOME, EssentialCraft5.id("inquisitorium_sites"));

    /** 审判庭坐标：解码故障核心的产物，NBT {@code Dim / X / Z}；一组 1 个（每份坐标不同） */
    public static final RegistryObject<InquisitoriumCoordinatesItem> INQUISITORIUM_COORDINATES = ECItems.register("inquisitorium_coordinates",
            () -> new InquisitoriumCoordinatesItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)),
            ItemSpec.generated("item/misc/inquisitorium_coordinates"));

    /** 审判庭芯片：打倒审判庭哨兵掉 1 个；空白模块（第二十一章）和审判庭套装的料 */
    public static final RegistryObject<InquisitoriumChipItem> INQUISITORIUM_CHIP = ECItems.register("inquisitorium_chip",
            () -> new InquisitoriumChipItem(new Item.Properties().rarity(Rarity.EPIC)),
            ItemSpec.generated("item/misc/inquisitorium_chip"));

    /**
     * 审判庭信标：半格高的台子（同全息投影台：硬度 1、抗爆 1，空手也掉自己），顶面、侧面各一张贴图；记着做它时用的坐标
     * （方块实体 {@link InquisitoriumBeaconBlockEntity}，挖下来经掉落表的 copy_nbt 带回物品根标签，放下时读回）。微微发光。
     */
    public static final RegistryObject<InquisitoriumBeaconBlock> INQUISITORIUM_BEACON = ECBlocks.register("inquisitorium_beacon",
            () -> new InquisitoriumBeaconBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> InquisitoriumBeaconBlock.LIGHT)
                    .strength(1F, ECBlocks.legacyResistance(1F, 1F, true))),
            BlockSpec.of((provider, block) -> provider.simpleBlockWithItem(block,
                    provider.models().slab(provider.name(block), provider.tex("block/inquisitorium_beacon_side"),
                            provider.tex("block/inquisitorium_beacon_top"), provider.tex("block/inquisitorium_beacon_top"))))
                    .loot((loot, block) -> {
                        CopyNbtFunction.Builder copy = CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY);
                        for (String key : InquisitoriumCoordinatesItem.KEYS) {
                            copy.copy(key, key);
                        }
                        loot.table(block, LootTable.lootTable().withPool(LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1F))
                                .when(ExplosionCondition.survivesExplosion())
                                .add(LootItem.lootTableItem(block).apply(copy))));
                    })
                    .item(block -> new InquisitoriumBeaconBlock.BeaconItem(block, new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));

    public static final RegistryObject<BlockEntityType<InquisitoriumBeaconBlockEntity>> INQUISITORIUM_BEACON_BE =
            ECBlockEntities.type("inquisitorium_beacon", InquisitoriumBeaconBlockEntity::new, INQUISITORIUM_BEACON);

    /**
     * 审判庭哨兵：全息投影的进阶型（{@link InquisitoriumSentinelEntity}），体型、追踪距离同全息投影；MISC 类别，不占怪物刷怪上限。
     * 只从审判庭信标来；刷怪蛋只放创造栏，刷怪笼出来的一律取消（{@code entity/HologramEvents}，按 instanceof 认）。
     */
    public static final RegistryObject<EntityType<InquisitoriumSentinelEntity>> INQUISITORIUM_SENTINEL = ECEntities.register("inquisitorium_sentinel",
            EntityType.Builder.<InquisitoriumSentinelEntity>of(InquisitoriumSentinelEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).fireImmune().clientTrackingRange(5).updateInterval(3));

    /** 蛋壳取暗紫，斑点取品红（和哨兵的换色一致） */
    public static final int SENTINEL_EGG_BACKGROUND = 0x22102E;
    public static final int SENTINEL_EGG_HIGHLIGHT = 0xE040FF;

    public static final RegistryObject<ECEntities.SpawnEgg> INQUISITORIUM_SENTINEL_SPAWN_EGG =
            ECEntities.spawnEgg("inquisitorium_sentinel_spawn_egg", INQUISITORIUM_SENTINEL, SENTINEL_EGG_BACKGROUND, SENTINEL_EGG_HIGHLIGHT);

    // ---------------- 进度 ----------------

    /** 代码发的几个进度（minecraft:impossible）共用的条件名 */
    public static final String ADVANCEMENT_CRITERION = "awarded_by_code";
    /** 解出审判庭坐标（给解码那台辐射仓 16 格内的玩家） */
    public static final ResourceLocation ADVANCEMENT_DECODED = EssentialCraft5.id("guide/inquisitorium/decoded");
    /** 拿着坐标走到那片暗影腐化之地 {@code arrive_radius} 格内 */
    public static final ResourceLocation ADVANCEMENT_ARRIVED = EssentialCraft5.id("guide/inquisitorium/arrived");
    /** 打倒审判庭哨兵（参战者；条件名同全息投影的 {@link ECHologram#ADVANCEMENT_CRITERION}） */
    public static final ResourceLocation ADVANCEMENT_SENTINEL = EssentialCraft5.id("main/inquisitorium");

    private static boolean initialized;

    private ECInquisitorium() {
    }

    /** 物品、方块、实体在类加载时登记；这里登记两段服务端配置和哨兵的属性。重复调用只生效一次 */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ECConfig.general(Config::define);
        ECConfig.general(Rules::define);
        ECEntities.attributes(INQUISITORIUM_SENTINEL, InquisitoriumSentinelEntity::createAttributes);
    }

    /** 四件，按头 / 胸 / 腿 / 靴 */
    public static List<RegistryObject<InquisitoriumArmorItem>> all() {
        return List.of(INQUISITORIUM_HELMET, INQUISITORIUM_CHESTPLATE, INQUISITORIUM_LEGGINGS, INQUISITORIUM_BOOTS);
    }

    private static RegistryObject<InquisitoriumArmorItem> armor(String name, ArmorItem.Type type, TagKey<Item> forgeTag) {
        return ECItems.register(name, () -> new InquisitoriumArmorItem(type, new Item.Properties().rarity(Rarity.EPIC)),
                ECArmorItem.icon3d("item/misc/mithrilineplate").tags(forgeTag));
    }

    /**
     * 服务端配置 {@code general.inquisitorium_armor}：每件的护甲、韧性、击退抗性，胸甲的生命、靴子的移速（batch8.md §6.6）；
     * 第十三批 13b 起加上穿着不掉耐久、全套「审判庭之律」（{@link Law}）和同调阶的数（{@link Attuned}），规则在 {@code item/InquisitoriumArmorEvents}。
     * 属性改了以后已经穿在身上的要换一次装才更新（原版只在装备变化时重算属性）；审判庭之律和同调阶的数每次用到时现读。
     * GameTest 用 {@link #setTestOverrides}、{@link #setLawTestOverrides}、{@link #setAttunedTestOverrides}，不碰真配置。
     */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param armor               每件的护甲值
         * @param toughness           每件的盔甲韧性
         * @param knockbackResistance 每件的击退抗性
         * @param chestHealth         胸甲加的最大生命（生命值）
         * @param bootsSpeed          靴子加的移速（ADDITION，玩家基础 0.1）
         */
        public record Values(double armor, double toughness, double knockbackResistance, double chestHealth, double bootsSpeed) {
        }

        /** 默认值（§6.6）：护甲 10、韧性 5、击退抗性 0.2；胸甲 +30 生命、靴子 +0.1 移速照原作 */
        public static final Values DEFAULTS = new Values(10D, 5D, 0.2D, 30D, 0.1D);

        private static ForgeConfigSpec.DoubleValue armor;
        private static ForgeConfigSpec.DoubleValue toughness;
        private static ForgeConfigSpec.DoubleValue knockbackResistance;
        private static ForgeConfigSpec.DoubleValue chestHealth;
        private static ForgeConfigSpec.DoubleValue bootsSpeed;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Inquisitorium armor: attribute values. Pieces already worn update after being taken off and put on again.",
                    "审判庭套装的属性。改了以后，已经穿在身上的要脱下再穿一次才更新").push("inquisitorium_armor");
            armor = builder.comment("Armor points of each piece (vanilla caps a player's total armor at 30).",
                            "每件的护甲值（原版玩家的总护甲最多算 30）")
                    .defineInRange("armor", DEFAULTS.armor(), 0D, 30D);
            toughness = builder.comment("Armor toughness of each piece (vanilla caps the total at 20).", "每件的盔甲韧性（原版总韧性最多算 20）")
                    .defineInRange("toughness", DEFAULTS.toughness(), 0D, 20D);
            knockbackResistance = builder.comment("Knockback resistance of each piece (0 to 1; 1 = immune).",
                            "每件的击退抗性（0~1，1 = 完全不被击退）")
                    .defineInRange("knockback_resistance", DEFAULTS.knockbackResistance(), 0D, 1D);
            chestHealth = builder.comment("Extra max health of the chestplate, in health points (2 = one heart; 30 = original).",
                            "胸甲加的最大生命（生命值，2 = 一颗心；原作 30）")
                    .defineInRange("chest_health", DEFAULTS.chestHealth(), 0D, 1024D);
            bootsSpeed = builder.comment("Movement speed added by the boots (a player walks at 0.1; 0.1 = original).",
                            "靴子加的移速（玩家走路是 0.1；原作 0.1）")
                    .defineInRange("boots_speed", DEFAULTS.bootsSpeed(), 0D, 1D);
            defineLaw(builder);
            defineAttuned(builder);
            builder.pop();
        }

        // ---------------- 审判庭之律（第十三批 13b，batch13.md §4.3） ----------------

        /**
         * 0 阶的规则：穿着不掉耐久（D14），全套「审判庭之律」的抗魔、首领减伤、律令、定罪。减伤都写成「减掉几成」，填 0 = 这一条不起作用。
         *
         * @param keepDurability       穿在玩家身上不掉耐久（别的生物穿着照常掉）
         * @param magicResistance      抗魔：有攻击者的魔法伤害减掉的比例（0.35 = ×0.65）
         * @param bossResistance       首领减伤：攻击者在 {@code #forge:bosses} 里时减掉的比例（0.15 = ×0.85）
         * @param edictReduction       律令：有害效果的时长缩短的比例（0.4 = 约 ×0.6）
         * @param convictionBonus      定罪：打被你定罪的目标多出的伤害比例（对玩家再乘 {@code general.attunement.pvp_effect_scale}）
         * @param convictionTicks      定罪记多少刻（0 = 不记）
         * @param convictionMaxTargets 每人同时最多定罪几个（0 = 不记）
         */
        public record Law(boolean keepDurability, double magicResistance, double bossResistance, double edictReduction, double convictionBonus,
                          int convictionTicks, int convictionMaxTargets) {
        }

        /** 0 阶默认值（batch13.md §4.3 审判庭一行） */
        public static final Law LAW_DEFAULTS = new Law(true, 0.35D, 0.15D, 0.4D, 0.2D, 200, 8);

        /**
         * 同调阶的数（batch13.md §4.4 审判庭；几阶起生效是 {@code item/InquisitoriumArmorEvents} 里的常量，按四件里最低的阶算）。
         *
         * @param magicResistance       1 阶起抗魔换成这个比例
         * @param absolutionCooldown    1 阶「赦罪」的冷却（刻）
         * @param convictionBonus       2 阶起定罪的增伤换成这个比例
         * @param convictionBossBonus   2 阶起目标是首领时再加的比例
         * @param bulwarkThreshold      3 阶「壁垒」：护甲后单下超过最大生命这个比例的部分才减
         * @param bulwarkReduction      3 阶：超出的部分减掉的比例（0.5 = 超出部分只受一半）
         * @param piercingResistance    4 阶：有攻击者的无视护甲、不是魔法的伤害减掉的比例
         * @param bulwarkThresholdTop   4 阶起壁垒的门槛
         * @param bulwarkReductionTop   4 阶起超出部分减掉的比例（0.6 = 超出部分只受四成）
         */
        public record Attuned(double magicResistance, int absolutionCooldown, double convictionBonus, double convictionBossBonus,
                              double bulwarkThreshold, double bulwarkReduction, double piercingResistance, double bulwarkThresholdTop,
                              double bulwarkReductionTop) {
        }

        /** 同调阶默认值：抗魔 45%、赦罪 200 刻、定罪 +30%（首领再 +10%）、壁垒 30% 起超出减半、4 阶无视护甲 ×0.7 与壁垒 25% 起超出 ×0.4 */
        public static final Attuned ATTUNED_DEFAULTS = new Attuned(0.45D, 200, 0.3D, 0.1D, 0.3D, 0.5D, 0.3D, 0.25D, 0.6D);

        private static ForgeConfigSpec.BooleanValue keepDurability;
        private static ForgeConfigSpec.DoubleValue magicResistance;
        private static ForgeConfigSpec.DoubleValue bossResistance;
        private static ForgeConfigSpec.DoubleValue edictReduction;
        private static ForgeConfigSpec.DoubleValue convictionBonus;
        private static ForgeConfigSpec.IntValue convictionTicks;
        private static ForgeConfigSpec.IntValue convictionMaxTargets;
        private static ForgeConfigSpec.DoubleValue attunedMagicResistance;
        private static ForgeConfigSpec.IntValue absolutionCooldown;
        private static ForgeConfigSpec.DoubleValue attunedConvictionBonus;
        private static ForgeConfigSpec.DoubleValue convictionBossBonus;
        private static ForgeConfigSpec.DoubleValue bulwarkThreshold;
        private static ForgeConfigSpec.DoubleValue bulwarkReduction;
        private static ForgeConfigSpec.DoubleValue piercingResistance;
        private static ForgeConfigSpec.DoubleValue bulwarkThresholdTop;
        private static ForgeConfigSpec.DoubleValue bulwarkReductionTop;
        @Nullable
        private static volatile Law lawOverride;
        @Nullable
        private static volatile Attuned attunedOverride;

        /** 0 阶那 7 项（在 {@code inquisitorium_armor} 小节里，{@link #define} 调） */
        private static void defineLaw(ForgeConfigSpec.Builder builder) {
            Law d = LAW_DEFAULTS;
            keepDurability = builder.comment("Pieces worn by a player don't lose durability (other mobs wearing them still wear them down).",
                            "穿在玩家身上不掉耐久（别的生物穿着照常掉）")
                    .define("keep_durability", d.keepDurability());
            magicResistance = builder.comment("No longer read: superseded by general.gear_growth.inquisitorium_armor.magic_resistance (kept so old config files still load).", "已由 general.gear_growth.inquisitorium_armor.magic_resistance 接管，不再读（留着是为了老配置文件照常加载）", "Full set: magic damage from an attacker is reduced by this fraction (with or without bypasses_armor; damage with no attacker, such as corruption, is not reduced; 0 = off).",
                            "全套抗魔：有攻击者的魔法伤害减掉的比例（带不带 bypasses_armor 都算；腐化这类没有攻击者的不减；0 = 不减）")
                    .defineInRange("magic_resistance", d.magicResistance(), 0D, 1D);
            bossResistance = builder.comment("No longer read: superseded by general.gear_growth.inquisitorium_armor.boss_resistance (kept so old config files still load).", "已由 general.gear_growth.inquisitorium_armor.boss_resistance 接管，不再读（留着是为了老配置文件照常加载）", "Full set: damage from an attacker in #forge:bosses is reduced by this fraction (0 = off).",
                            "全套首领减伤：攻击者在 #forge:bosses 里时减掉的比例（0 = 不减）")
                    .defineInRange("boss_resistance", d.bossResistance(), 0D, 1D);
            edictReduction = builder.comment("No longer read: superseded by general.gear_growth.inquisitorium_armor.edict_reduction (kept so old config files still load).", "已由 general.gear_growth.inquisitorium_armor.edict_reduction 接管，不再读（留着是为了老配置文件照常加载）", "Full set Edict: harmful effects wear off this much sooner (0.4 = they last about 60% as long; 0 = off).",
                            "Effects with infinite duration and effects in the essentialcraft5:unfilterable tag are left alone; to keep Edict off an effect (for example another mod's cooldown made as a harmful effect), add it to that tag (the Matrix Filter module will then let it through too).",
                            "全套律令：有害效果的时长缩短的比例（0.4 = 约剩六成；0 = 不缩）",
                            "无限时长的、essentialcraft5:unfilterable 标签里的效果不缩；不想让律令缩某个效果（比如别的模组做成有害效果的冷却），把它加进这个标签（矩阵过滤模块同样不再拦它）")
                    .defineInRange("edict_reduction", d.edictReduction(), 0D, 0.9D);
            convictionBonus = builder.comment("Full set Conviction: extra damage dealt to a creature you have convicted (against players it is multiplied by general.attunement.pvp_effect_scale; 0 = off).",
                            "全套定罪：打被你定罪的目标多出的伤害比例（对玩家再乘 general.attunement.pvp_effect_scale；0 = 不加）")
                    .defineInRange("conviction_bonus", d.convictionBonus(), 0D, 10D);
            convictionTicks = builder.comment("Full set Conviction: ticks a creature stays convicted after a hit of it lands on you (0 = never convicted).",
                            "全套定罪：活物打实你一下以后被定罪多少刻（0 = 不定罪）")
                    .defineInRange("conviction_ticks", d.convictionTicks(), 0, 72_000);
            convictionMaxTargets = builder.comment("Full set Conviction: most creatures convicted at the same time per player (the one closest to expiring is dropped first; 0 = never convicted).",
                            "全套定罪：每人同时最多定罪几个（满了先挤掉最快到期的；0 = 不定罪）")
                    .defineInRange("conviction_max_targets", d.convictionMaxTargets(), 0, 64);
        }

        /** 同调阶那几项（同在 {@code inquisitorium_armor} 小节里） */
        private static void defineAttuned(ForgeConfigSpec.Builder builder) {
            Attuned d = ATTUNED_DEFAULTS;
            attunedMagicResistance = builder.comment("No longer read: superseded by general.gear_growth.inquisitorium_armor.magic_resistance (kept so old config files still load).", "已由 general.gear_growth.inquisitorium_armor.magic_resistance 接管，不再读（留着是为了老配置文件照常加载）", "Attunement stage 1 (all four pieces): magic resistance becomes this fraction.",
                            "同调 1 阶（四件都到）：抗魔换成这个比例")
                    .defineInRange("magic_resistance_stage_1", d.magicResistance(), 0D, 1D);
            absolutionCooldown = builder.comment("Attunement stage 1 Absolution: killing a creature you convicted clears one harmful effect from you, at most once per this many ticks.",
                            "同调 1 阶赦罪：亲手打死被你定罪的目标时清掉自己一个有害效果，这么多刻最多一次")
                    .defineInRange("absolution_cooldown", d.absolutionCooldown(), 0, 72_000);
            attunedConvictionBonus = builder.comment("Attunement stage 2: Conviction's extra damage becomes this fraction.", "同调 2 阶：定罪的增伤换成这个比例")
                    .defineInRange("conviction_bonus_stage_2", d.convictionBonus(), 0D, 10D);
            convictionBossBonus = builder.comment("Attunement stage 2: further extra damage when the convicted target is a boss.",
                            "同调 2 阶：被定罪的是首领时再加的比例")
                    .defineInRange("conviction_boss_bonus_stage_2", d.convictionBossBonus(), 0D, 10D);
            bulwarkThreshold = builder.comment("Attunement stage 3 Bulwark: after armor, the part of a single hit above this fraction of your max health is reduced (hits with an attacker only; absorption hearts are counted as part of the hit).",
                            "0 = every hit with an attacker is reduced by the ratio below; to turn Bulwark off, set bulwark_reduction_stage_3 to 0.",
                            "同调 3 阶壁垒：护甲后单下超过最大生命这个比例的部分才减（只管有攻击者的伤害；黄心吃掉的那截也算在这一下里）",
                            "0 = 有攻击者的每一下都按下面的比例减；要关掉壁垒请把 bulwark_reduction_stage_3 填 0")
                    .defineInRange("bulwark_threshold_stage_3", d.bulwarkThreshold(), 0D, 1D);
            bulwarkReduction = builder.comment("Attunement stage 3 Bulwark: the part above the threshold is reduced by this fraction (0.5 = you take half of it; 0 = off).",
                            "同调 3 阶壁垒：超出门槛的部分减掉的比例（0.5 = 只受一半；0 = 不减）")
                    .defineInRange("bulwark_reduction_stage_3", d.bulwarkReduction(), 0D, 1D);
            piercingResistance = builder.comment("Attunement stage 4: armor-piercing damage that is not magic and has an attacker is reduced by this fraction.",
                            "同调 4 阶：有攻击者、无视护甲、不是魔法的伤害减掉的比例")
                    .defineInRange("piercing_resistance_stage_4", d.piercingResistance(), 0D, 1D);
            bulwarkThresholdTop = builder.comment("Attunement stage 4: Bulwark's threshold becomes this fraction of your max health.",
                            "0 = every hit with an attacker is reduced by bulwark_reduction_stage_4; to turn Bulwark off at stage 4, set that to 0.",
                            "同调 4 阶：壁垒的门槛换成最大生命的这个比例",
                            "0 = 有攻击者的每一下都按 bulwark_reduction_stage_4 减；4 阶要关掉壁垒请把它填 0")
                    .defineInRange("bulwark_threshold_stage_4", d.bulwarkThresholdTop(), 0D, 1D);
            bulwarkReductionTop = builder.comment("Attunement stage 4: the part above Bulwark's threshold is reduced by this fraction (0.6 = you take 40% of it).",
                            "同调 4 阶：超出壁垒门槛的部分减掉的比例（0.6 = 只受四成）")
                    .defineInRange("bulwark_reduction_stage_4", d.bulwarkReductionTop(), 0D, 1D);
        }

        /** 0 阶规则现在的数：测试覆盖值 → 配置（没加载时默认值） */
        public static Law law() {
            Law override = lawOverride;
            if (override != null) {
                return override;
            }
            if (keepDurability == null) {
                return LAW_DEFAULTS;
            }
            return new Law(ECConfig.get(keepDurability), ECConfig.get(magicResistance), ECConfig.get(bossResistance), ECConfig.get(edictReduction),
                    ECConfig.get(convictionBonus), ECConfig.get(convictionTicks), ECConfig.get(convictionMaxTargets));
        }

        /** 同调阶的数：测试覆盖值 → 配置（没加载时默认值） */
        public static Attuned attuned() {
            Attuned override = attunedOverride;
            if (override != null) {
                return override;
            }
            if (attunedMagicResistance == null) {
                return ATTUNED_DEFAULTS;
            }
            return new Attuned(ECConfig.get(attunedMagicResistance), ECConfig.get(absolutionCooldown), ECConfig.get(attunedConvictionBonus),
                    ECConfig.get(convictionBossBonus), ECConfig.get(bulwarkThreshold), ECConfig.get(bulwarkReduction), ECConfig.get(piercingResistance),
                    ECConfig.get(bulwarkThresholdTop), ECConfig.get(bulwarkReductionTop));
        }

        /** GameTest 用：0 阶规则换成给定的数，null = 回到配置 */
        public static void setLawTestOverrides(@Nullable Law values) {
            lawOverride = values;
        }

        /** GameTest 用：同调阶的数换成给定的数，null = 回到配置 */
        public static void setAttunedTestOverrides(@Nullable Attuned values) {
            attunedOverride = values;
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (armor == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(armor), ECConfig.get(toughness), ECConfig.get(knockbackResistance), ECConfig.get(chestHealth),
                    ECConfig.get(bootsSpeed));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }

    /**
     * 服务端配置 {@code general.inquisitorium}（batch10.md §5.10.7）：哨兵、解码、坐标、信标的数值。
     * GameTest 用 {@link #setTestOverrides}（整套换掉，不碰真配置），设、用、置回都在同一段同步代码里。
     */
    public static final class Rules {

        /**
         * 一套数值。
         *
         * @param maxHealth          审判庭哨兵的最大生命（召出时设）
         * @param echoRatio          回声的强度（0 = 没有回声）：克隆体、二分场个数，护甲破坏者的频率，轨道打击的伤害都乘它
         * @param matrixDamagePerHit 哨兵的招式每打实一下参战者，另加多少矩阵损伤（0 = 不加）
         * @param decodeMru          解码一个故障核心一共花多少 MRU（按刻均摊）
         * @param decodeTicks        解码要几刻（平衡值在窗口里的刻才算）
         * @param decodeTolerance    平衡值和核心上的目标相差不超过多少才推进
         * @param balanceMin         故障核心目标平衡值的下限
         * @param balanceMax         故障核心目标平衡值的上限
         * @param searchRadius       解码时在海纳尔里找暗影腐化之地的半径（格）
         * @param searchStep         找的时候每隔几格看一次群系（一次最多 256 圈，超过时自动加大）
         * @param fallbackDistance   找不到时，坐标定在解码处多远（方向由世界种子和解码处的 x / z 定）
         * @param arriveRadius       离坐标多近算到了；信标也要放在坐标这么近以内
         * @param summonDimensions   审判庭信标能召唤的维度
         */
        public record Values(double maxHealth, double echoRatio, int matrixDamagePerHit, int decodeMru, int decodeTicks, double decodeTolerance,
                             double balanceMin, double balanceMax, int searchRadius, int searchStep, int fallbackDistance, int arriveRadius,
                             List<String> summonDimensions) {

            public Values {
                summonDimensions = List.copyOf(summonDimensions);
            }

            /** 换掉解码的总耗能和刻数（测试常用） */
            public Values withDecode(int mru, int ticks) {
                return new Values(maxHealth, echoRatio, matrixDamagePerHit, mru, ticks, decodeTolerance, balanceMin, balanceMax, searchRadius,
                        searchStep, fallbackDistance, arriveRadius, summonDimensions);
            }

            /** 换掉找暗影腐化之地的半径和退回距离（测试常用） */
            public Values withSearch(int radius, int fallback) {
                return new Values(maxHealth, echoRatio, matrixDamagePerHit, decodeMru, decodeTicks, decodeTolerance, balanceMin, balanceMax, radius,
                        searchStep, fallback, arriveRadius, summonDimensions);
            }

            /** 换掉信标能召唤的维度（混合端自检在主世界召） */
            public Values withSummonDimensions(List<String> dimensions) {
                return new Values(maxHealth, echoRatio, matrixDamagePerHit, decodeMru, decodeTicks, decodeTolerance, balanceMin, balanceMax,
                        searchRadius, searchStep, fallbackDistance, arriveRadius, dimensions);
            }

            /** 换掉哨兵的生命、回声强度、每下的矩阵损伤 */
            public Values withSentinel(double health, double echo, int matrixPerHit) {
                return new Values(health, echo, matrixPerHit, decodeMru, decodeTicks, decodeTolerance, balanceMin, balanceMax, searchRadius,
                        searchStep, fallbackDistance, arriveRadius, summonDimensions);
            }
        }

        /** 默认值（batch10.md §5.10.7） */
        public static final Values DEFAULTS = new Values(800D, 0.5D, 1000, 5000, 1000, 0.02D, 0.15D, 1.85D, 3000, 64, 1500, 32,
                List.of(ECHoanna.ID.toString()));

        private static ForgeConfigSpec.DoubleValue maxHealth;
        private static ForgeConfigSpec.DoubleValue echoRatio;
        private static ForgeConfigSpec.IntValue matrixDamagePerHit;
        private static ForgeConfigSpec.IntValue decodeMru;
        private static ForgeConfigSpec.IntValue decodeTicks;
        private static ForgeConfigSpec.DoubleValue decodeTolerance;
        private static ForgeConfigSpec.DoubleValue balanceMin;
        private static ForgeConfigSpec.DoubleValue balanceMax;
        private static ForgeConfigSpec.IntValue searchRadius;
        private static ForgeConfigSpec.IntValue searchStep;
        private static ForgeConfigSpec.IntValue fallbackDistance;
        private static ForgeConfigSpec.IntValue arriveRadius;
        private static ForgeConfigSpec.ConfigValue<List<? extends String>> summonDimensions;
        @Nullable
        private static volatile Values testOverride;

        private Rules() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            Values d = DEFAULTS;
            builder.comment("Inquisitorium (chapter 19): decoding the Glitchy Core, the coordinates, the beacon and the Inquisitorium Sentinel.",
                    "审判庭（第十九章）：解码故障核心、审判庭坐标、审判庭信标与审判庭哨兵").push("inquisitorium");
            maxHealth = builder.comment("Max health of an Inquisitorium Sentinel (its single-hit cap is general.hologram.max_damage_per_hit; vanilla caps max health at 1024).",
                            "审判庭哨兵的最大生命（单次受伤封顶照 general.hologram.max_damage_per_hit；原版生命属性上限 1024）")
                    .defineInRange("max_health", d.maxHealth(), 1D, 1024D);
            echoRatio = builder.comment("Strength of the echo: after an attack ends, the next attack is joined by this fraction of the previous one (0 = no echo).",
                            "回声的强度：一招放完，下一招出招时上一招按这个比例再来一次（0 = 没有回声）")
                    .defineInRange("echo_ratio", d.echoRatio(), 0D, 1D);
            matrixDamagePerHit = builder.comment("Matrix damage added each time one of its attacks lands on a fighter (0 = none; capped by general.corruption.max_matrix_damage).",
                            "它的招式每打实参战者一下，另加多少矩阵损伤（0 = 不加；不超过 general.corruption.max_matrix_damage）")
                    .defineInRange("matrix_damage_per_hit", d.matrixDamagePerHit(), 0, 72_000);
            decodeMru = builder.comment("Total MRU used to decode one Glitchy Core in a Radiating Chamber (spread over the ticks).",
                            "辐射仓解码一个故障核心一共花多少 MRU（按刻均摊）")
                    .defineInRange("decode_mru", d.decodeMru(), 0, 1_000_000);
            decodeTicks = builder.comment("Ticks of decoding needed (only ticks with the balance inside the window count).",
                            "解码要几刻（平衡值在窗口里的刻才算）")
                    .defineInRange("decode_ticks", d.decodeTicks(), 1, 72_000);
            decodeTolerance = builder.comment("How far the chamber's balance may be from the core's target balance while decoding.",
                            "解码时仓里的平衡值和核心上的目标最多差多少")
                    .defineInRange("decode_tolerance", d.decodeTolerance(), 0.001D, MRUBalance.MAX);
            balanceMin = builder.comment("Lowest target balance written on a Glitchy Core.", "故障核心上的目标平衡值最低多少")
                    .defineInRange("balance_min", d.balanceMin(), MRUBalance.MIN, MRUBalance.MAX);
            balanceMax = builder.comment("Highest target balance written on a Glitchy Core.", "故障核心上的目标平衡值最高多少")
                    .defineInRange("balance_max", d.balanceMax(), MRUBalance.MIN, MRUBalance.MAX);
            searchRadius = builder.comment("Radius (blocks) searched in Hoanna around the decoding spot: first for the nearest Inquisitorium Outpost, then for a #essentialcraft5:inquisitorium_sites biome.",
                            "解码时在海纳尔里、以解码处为中心找的半径（格）：先找最近的审判庭前哨，没有再找 #essentialcraft5:inquisitorium_sites 群系")
                    .defineInRange("search_radius", d.searchRadius(), 0, 12_000);
            searchStep = builder.comment("Blocks between two biome samples of that search (raised automatically so that one search covers at most 256 rings).",
                            "找的时候每隔几格看一次群系（半径 ÷ 步长超过 256 圈时自动加大步长）")
                    .defineInRange("search_step", d.searchStep(), 16, 512);
            fallbackDistance = builder.comment("If nothing is found, the coordinates point this many blocks away from the decoding spot, in a direction fixed by the world seed and that spot.",
                            "找不到时，坐标定在解码处多远（方向由世界种子和解码处定，同一处每次一样）")
                    .defineInRange("fallback_distance", d.fallbackDistance(), 0, 30_000);
            arriveRadius = builder.comment("Horizontal distance to the coordinates that counts as arrived; the beacon must be placed this close too.",
                            "离坐标水平多近算到了；审判庭信标也要放在这么近以内")
                    .defineInRange("arrive_radius", d.arriveRadius(), 4, 256);
            summonDimensions = builder.comment("Dimensions where the Inquisitorium Beacon can summon the Sentinel.", "审判庭信标能召出哨兵的维度")
                    .defineListAllowEmpty("summon_dimensions", d.summonDimensions(), o -> o instanceof String str && ResourceLocation.tryParse(str) != null);
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值）。目标平衡值的上下限写反了按大小对调 */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (maxHealth == null) {
                return DEFAULTS;
            }
            double low = ECConfig.get(balanceMin);
            double high = ECConfig.get(balanceMax);
            return new Values(ECConfig.get(maxHealth), ECConfig.get(echoRatio), ECConfig.get(matrixDamagePerHit), ECConfig.get(decodeMru),
                    ECConfig.get(decodeTicks), ECConfig.get(decodeTolerance), Math.min(low, high), Math.max(low, high), ECConfig.get(searchRadius),
                    ECConfig.get(searchStep), ECConfig.get(fallbackDistance), ECConfig.get(arriveRadius), dimensionList());
        }

        /** 维度名单（ECConfig.get 没有列表的重载：没加载时用默认值，同富集仓坍缩的维度名单） */
        private static List<String> dimensionList() {
            boolean loaded = ECConfig.spec() != null && ECConfig.spec().isLoaded();
            List<String> out = new ArrayList<>();
            for (String entry : loaded ? summonDimensions.get() : summonDimensions.getDefault()) {
                out.add(entry);
            }
            return List.copyOf(out);
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
