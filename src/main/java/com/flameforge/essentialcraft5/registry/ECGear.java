package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.datagen.ECItemModelProvider;
import com.flameforge.essentialcraft5.item.ElementalTier;
import com.flameforge.essentialcraft5.item.MHSArmorItem;
import com.flameforge.essentialcraft5.item.MRUCUContainerItem;
import com.flameforge.essentialcraft5.item.MRUMoverItem;
import com.flameforge.essentialcraft5.item.MRUMoverT2Item;
import com.flameforge.essentialcraft5.item.MRUPresenceSpawnEggItem;
import com.flameforge.essentialcraft5.item.MagicMonocleItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 装备与防护模块（第二批 §3.9）：元素工具、弱化元素工具、魔力防护套装、魔力单片镜、二阶 MRUCU 移动魔杖、MRU 聚集体刷怪蛋；
 * 另外给一批的矩阵投影接上了右键使用（item/MatrixProjectionItem，二批只有本模块改它）。
 * <p>
 * 新旧注册名（总表见 tools/id_map_items.json）：
 * <ul>
 *   <li>elemental_pick / axe / shovel / hoe / sword → elemental_pickaxe / axe / shovel / hoe / sword（材料见 {@link ElementalTier}）</li>
 *   <li>weak_elemental_pick / … → weak_elemental_pickaxe / axe / shovel / hoe / sword</li>
 *   <li>helm_magic / chest_magic / legs_magic / boots_magic（ItemArmorEC）→ mhs_helmet / chestplate / leggings / boots</li>
 *   <li>magicMonocle → magic_monocle；mruMover_t2 → mru_mover_t2；entityEgg:0（EntityMRUPresence）→ mru_presence_spawn_egg</li>
 * </ul>
 * 实现的框架 API：{@code IMRUResistanceItem}、{@code IMRUShieldArmor}（魔力防护四件）；{@code essentialcraft5:mru_vision} 标签成员（单片镜、二阶魔杖、防护头盔）
 * 写在 tools/tags/ecgear.json，不用代码。文字 tools/lang/keys/ecgear.json，配方 data/essentialcraft5/recipes/gear/，
 * 研究手册「装备与防护」assets/essentialcraft5/guide/gear.json，客户端注册 client/GearClient。
 * <p>
 * 第十三批 13b（batch13.md §4.3、§4.4）：魔力防护的属性改由服务端配置 {@code general.mhs_armor}（{@link MHSConfig}）给，每件自带 MRU、有电时挡一部分伤害（矩阵缓冲），
 * 同调家族 {@code mhs_armor}（最高 2 阶），升阶配方在 {@code data/essentialcraft5/recipes/attunement/mhs_*.json}；挨打以后的余流、脱下夹回生命在 {@code item/MHSArmorEvents}。
 * <p>
 * 工具的攻击数值：镐、锹、剑、锄照 1.12 原版公式换算到 1.20 的构造参数，结果和原作一样（镐 +1、锹 +1.5、剑 +3，再加材料的攻击加成；
 * 锄攻击速度 = 材料攻击加成 − 3）；斧见 {@link #axe} 的注释（修了原作的 bug）。
 */
public final class ECGear {

    /** 元素工具才能挖的方块（目前没有成员，给以后的方块和数据包用）。定义在 {@link ElementalTier#ELEMENTAL} 上 */
    public static final TagKey<Block> NEEDS_ELEMENTAL_TOOL = ElementalTier.ELEMENTAL.getTag();

    // ---------------- 元素工具（elemental） ----------------

    public static final RegistryObject<PickaxeItem> ELEMENTAL_PICKAXE = pickaxe("elemental_pickaxe", ElementalTier.ELEMENTAL, "elemental_pickaxe");
    public static final RegistryObject<AxeItem> ELEMENTAL_AXE = axe("elemental_axe", ElementalTier.ELEMENTAL, "elemental_axe");
    public static final RegistryObject<ShovelItem> ELEMENTAL_SHOVEL = shovel("elemental_shovel", ElementalTier.ELEMENTAL, "elemental_shovel");
    public static final RegistryObject<HoeItem> ELEMENTAL_HOE = hoe("elemental_hoe", ElementalTier.ELEMENTAL, -5, 2F, "elemental_hoe");
    public static final RegistryObject<SwordItem> ELEMENTAL_SWORD = sword("elemental_sword", ElementalTier.ELEMENTAL, "elemental_sword");

    // ---------------- 弱化元素工具（weakElemental） ----------------

    public static final RegistryObject<PickaxeItem> WEAK_ELEMENTAL_PICKAXE = pickaxe("weak_elemental_pickaxe", ElementalTier.WEAKENED, "weak_pickaxe");
    public static final RegistryObject<AxeItem> WEAK_ELEMENTAL_AXE = axe("weak_elemental_axe", ElementalTier.WEAKENED, "weak_axe");
    public static final RegistryObject<ShovelItem> WEAK_ELEMENTAL_SHOVEL = shovel("weak_elemental_shovel", ElementalTier.WEAKENED, "weak_shovel");
    /** 原作攻击 0（1.12 锄不加攻击），1.20 锄的参数只收整数：取 −2；第十三批起弱化材料的攻击加成是 3（原作 2.5，batch13.md §4.6），合计 +1，攻击力 2 */
    public static final RegistryObject<HoeItem> WEAK_ELEMENTAL_HOE = hoe("weak_elemental_hoe", ElementalTier.WEAKENED, -2, -0.5F, "weak_hoe");
    public static final RegistryObject<SwordItem> WEAK_ELEMENTAL_SWORD = sword("weak_elemental_sword", ElementalTier.WEAKENED, "weak_sword");

    // ---------------- 魔力防护套装（ItemArmorEC，MRUFortified） ----------------

    public static final RegistryObject<MHSArmorItem> MHS_HELMET = armor("mhs_helmet", ArmorItem.Type.HELMET, Tags.Items.ARMORS_HELMETS);
    public static final RegistryObject<MHSArmorItem> MHS_CHESTPLATE = armor("mhs_chestplate", ArmorItem.Type.CHESTPLATE, Tags.Items.ARMORS_CHESTPLATES);
    public static final RegistryObject<MHSArmorItem> MHS_LEGGINGS = armor("mhs_leggings", ArmorItem.Type.LEGGINGS, Tags.Items.ARMORS_LEGGINGS);
    public static final RegistryObject<MHSArmorItem> MHS_BOOTS = armor("mhs_boots", ArmorItem.Type.BOOTS, Tags.Items.ARMORS_BOOTS);

    // ---------------- 其它 ----------------

    /**
     * 原作 magicMonocle（耐久 16，从没扣过，已去掉）。原作 isFull3D → handheld。
     * head 显示变换：戴在头盔槽时 CustomHeadLayer 按 HEAD 画物品模型，这里把镜片（贴图 (8~14, 1~8) 那一圈）缩到约 2.6 像素，
     * 挪到玩家右眼前、皮肤第二层（帽子层，脸前 0.5 像素）之外（translation / scale 是按原版 translateToHead 的坐标系算的，进游戏要看一眼）。
     */
    public static final RegistryObject<MagicMonocleItem> MAGIC_MONOCLE = ECItems.register("magic_monocle",
            () -> new MagicMonocleItem(new Item.Properties().stacksTo(1)),
            ItemSpec.of((provider, item) -> provider.handheld(item, "item/tools/magicmonocle")
                    .transforms()
                    .transform(ItemDisplayContext.HEAD).rotation(0F, 180F, 0F).translation(5.3F, -2.9F, -7.6F).scale(0.6F).end()
                    .end()));

    /** 原作 mruMover_t2：耐久 256 × 6；模型和一阶一样按 essentialcraft5:active 切换使用中的贴图（客户端属性在 GearClient 注册） */
    public static final RegistryObject<MRUMoverT2Item> MRU_MOVER_T2 = ECItems.register("mru_mover_t2",
            () -> new MRUMoverT2Item(new Item.Properties().durability(256 * 6)),
            ItemSpec.of((provider, item) -> {
                ItemModelBuilder active = provider.withExistingParent("mru_mover_t2_active", provider.mcLoc("item/handheld"))
                        .texture("layer0", provider.tex("item/tools/mru_mover_t2_active"));
                provider.handheld(item, "item/tools/mru_mover_t2")
                        .override().predicate(MRUMoverItem.ACTIVE_PREDICATE, 1F).model(active).end();
            }));

    /**
     * 聚集体收容瓶（EC5 新增，原作没有）：空瓶一层贴图；装着时换成两层的模型，第二层按瓶里聚集体的平衡值染色
     * （物品属性和颜色在 GearClient 注册）。不怕火：扔进岩浆不会连带把聚集体烧掉
     */
    public static final RegistryObject<MRUCUContainerItem> MRUCU_CONTAINER = ECItems.register("mrucu_container",
            () -> new MRUCUContainerItem(new Item.Properties().stacksTo(1).fireResistant()),
            ItemSpec.of((provider, item) -> {
                ItemModelBuilder filled = provider.withExistingParent("mrucu_container_filled", provider.mcLoc("item/generated"))
                        .texture("layer0", provider.tex("item/tools/mrucu_container"))
                        .texture("layer1", provider.tex("item/tools/mrucu_container_contents"));
                provider.generated(item, "item/tools/mrucu_container")
                        .override().predicate(MRUCUContainerItem.FILLED_PREDICATE, 1F).model(filled).end();
            }));

    /** 创造物品；模型用原版刷怪蛋模板，两层颜色在 GearClient 注册 */
    public static final RegistryObject<MRUPresenceSpawnEggItem> MRU_PRESENCE_SPAWN_EGG = ECItems.register("mru_presence_spawn_egg",
            () -> new MRUPresenceSpawnEggItem(new Item.Properties()),
            ItemSpec.of((provider, item) -> provider.withExistingParent(provider.name(item), provider.mcLoc("item/template_spawn_egg"))));

    private ECGear() {
    }

    public static void init() {
        ElementalTier.init();
        ECConfig.general(MRUCUContainerItem.Config::define);
        ECConfig.general(MHSConfig::define);
    }

    // ---------------- 魔力防护的配置（第十三批 13b，batch13.md §4.3、§4.4） ----------------

    /**
     * 服务端配置 {@code general.mhs_armor}：魔力防护的属性、自带 MRU、矩阵缓冲（护盾）、余流。SERVER 配置随进服同步给客户端，提示框用的数和服务端一致。
     * 按同调阶取的几项是列表（下标 = 阶，从 0 阶起；阶超出列表时按最后一项）。改了属性以后，已经穿在身上的要换一次装才更新（原版只在装备变化时重算属性）；
     * 容量、份额每次现读，改了立刻生效：容量改小时，读出来的存量按新容量截，物品 NBT 里存的数不动，到下一次写入（扣电、充电）才按新容量截；
     * 改回大的，没写过的那件存量照原样回来。GameTest 用 {@link #setTestOverrides}，不碰真配置。
     */
    public static final class MHSConfig {

        /**
         * 一套数值。
         *
         * @param armorHelmet       头盔护甲值（胸甲、护腿、靴子同理）
         * @param toughness         每件韧性
         * @param knockbackResistance 每件击退抗性
         * @param maxHealth         每件加的最大生命
         * @param maxMru            每件自带的 MRU，按这件的同调阶
         * @param mruPerDamage      护盾每挡 1 点伤害花多少 MRU（每件至少按 1 点算）
         * @param sharesHelmet      头盔挡的份额（二十分之几），按这件的同调阶；胸甲、护腿、靴子同理
         * @param dischargePriceMultiplier 2 阶起挡怪物打来的 MRU 放电时，每点单价乘几
         * @param afterflowTicks    余流：急迫持续多少刻（0 = 不给）
         * @param afterflowCooldown 余流：两次之间至少隔多少刻
         */
        public record Values(double armorHelmet, double armorChestplate, double armorLeggings, double armorBoots,
                             double toughness, double knockbackResistance, double maxHealth,
                             List<Integer> maxMru, int mruPerDamage,
                             List<Integer> sharesHelmet, List<Integer> sharesChestplate, List<Integer> sharesLeggings, List<Integer> sharesBoots,
                             double dischargePriceMultiplier, int afterflowTicks, int afterflowCooldown) {

            public Values {
                maxMru = List.copyOf(maxMru);
                sharesHelmet = List.copyOf(sharesHelmet);
                sharesChestplate = List.copyOf(sharesChestplate);
                sharesLeggings = List.copyOf(sharesLeggings);
                sharesBoots = List.copyOf(sharesBoots);
            }

            /** 这一件的护甲值 */
            public double armor(ArmorItem.Type type) {
                return switch (type) {
                    case HELMET -> armorHelmet;
                    case CHESTPLATE -> armorChestplate;
                    case LEGGINGS -> armorLeggings;
                    case BOOTS -> armorBoots;
                };
            }

            /** 这一阶每件自带多少 MRU */
            public int maxMru(int stage) {
                return pick(maxMru, stage, 0, Integer.MAX_VALUE);
            }

            /** 这一件这一阶挡的份额（二十分之几，0~20） */
            public int share(ArmorItem.Type type, int stage) {
                List<Integer> list = switch (type) {
                    case HELMET -> sharesHelmet;
                    case CHESTPLATE -> sharesChestplate;
                    case LEGGINGS -> sharesLeggings;
                    case BOOTS -> sharesBoots;
                };
                return pick(list, stage, 0, MAX_SHARE);
            }

            /** 四件都是这一阶时合计的份额（二十分之几） */
            public int totalShare(int stage) {
                int total = 0;
                for (ArmorItem.Type type : ArmorItem.Type.values()) {
                    total += share(type, stage);
                }
                return total;
            }

            /** 拿这一套改几项（GameTest 用） */
            public Builder toBuilder() {
                return new Builder(this);
            }
        }

        /** {@link Values} 的改写器（GameTest 只改要测的几项） */
        public static final class Builder {
            private double armorHelmet;
            private double armorChestplate;
            private double armorLeggings;
            private double armorBoots;
            private double toughness;
            private double knockbackResistance;
            private double maxHealth;
            private List<Integer> maxMru;
            private int mruPerDamage;
            private List<Integer> sharesHelmet;
            private List<Integer> sharesChestplate;
            private List<Integer> sharesLeggings;
            private List<Integer> sharesBoots;
            private double dischargePriceMultiplier;
            private int afterflowTicks;
            private int afterflowCooldown;

            private Builder(Values v) {
                armorHelmet = v.armorHelmet();
                armorChestplate = v.armorChestplate();
                armorLeggings = v.armorLeggings();
                armorBoots = v.armorBoots();
                toughness = v.toughness();
                knockbackResistance = v.knockbackResistance();
                maxHealth = v.maxHealth();
                maxMru = v.maxMru();
                mruPerDamage = v.mruPerDamage();
                sharesHelmet = v.sharesHelmet();
                sharesChestplate = v.sharesChestplate();
                sharesLeggings = v.sharesLeggings();
                sharesBoots = v.sharesBoots();
                dischargePriceMultiplier = v.dischargePriceMultiplier();
                afterflowTicks = v.afterflowTicks();
                afterflowCooldown = v.afterflowCooldown();
            }

            /** 四件护甲值、韧性、击退抗性、生命一起改 */
            public Builder stats(double helmet, double chestplate, double leggings, double boots, double toughnessValue, double knockback, double health) {
                armorHelmet = helmet;
                armorChestplate = chestplate;
                armorLeggings = leggings;
                armorBoots = boots;
                toughness = toughnessValue;
                knockbackResistance = knockback;
                maxHealth = health;
                return this;
            }

            public Builder maxMru(List<Integer> value) {
                maxMru = value;
                return this;
            }

            public Builder mruPerDamage(int value) {
                mruPerDamage = value;
                return this;
            }

            /** 四件的份额列表一起改（每个列表按阶） */
            public Builder shares(List<Integer> helmet, List<Integer> chestplate, List<Integer> leggings, List<Integer> boots) {
                sharesHelmet = helmet;
                sharesChestplate = chestplate;
                sharesLeggings = leggings;
                sharesBoots = boots;
                return this;
            }

            public Builder dischargePriceMultiplier(double value) {
                dischargePriceMultiplier = value;
                return this;
            }

            public Builder afterflow(int ticks, int cooldown) {
                afterflowTicks = ticks;
                afterflowCooldown = cooldown;
                return this;
            }

            public Values build() {
                return new Values(armorHelmet, armorChestplate, armorLeggings, armorBoots, toughness, knockbackResistance, maxHealth,
                        maxMru, mruPerDamage, sharesHelmet, sharesChestplate, sharesLeggings, sharesBoots,
                        dischargePriceMultiplier, afterflowTicks, afterflowCooldown);
            }
        }

        /** 份额的上限（四件的份额合计到 20 就是整下挡掉，见 {@code MRUShield.TOTAL_SHARES}） */
        public static final int MAX_SHARE = 20;

        /**
         * 默认值（batch13.md §4.3、§4.4）：护甲 3 / 8 / 6 / 3（同钻石）、每件韧性 2、+2 生命；每件自带 2 000 / 4 000 / 8 000 MRU（0 / 1 / 2 阶）；
         * 每点 100 MRU；份额 0 阶 1 / 2 / 1 / 1（25%）、1 阶 2 / 3 / 1 / 1（35%）、2 阶 2 / 4 / 2 / 1（45%）；放电原价；余流 40 刻、200 刻一次
         */
        public static final Values DEFAULTS = new Values(3D, 8D, 6D, 3D, 2D, 0D, 2D,
                List.of(2000, 4000, 8000), 100,
                List.of(1, 2, 2), List.of(2, 3, 4), List.of(1, 1, 2), List.of(1, 1, 1),
                1D, 40, 200);

        private static ForgeConfigSpec.DoubleValue armorHelmet;
        private static ForgeConfigSpec.DoubleValue armorChestplate;
        private static ForgeConfigSpec.DoubleValue armorLeggings;
        private static ForgeConfigSpec.DoubleValue armorBoots;
        private static ForgeConfigSpec.DoubleValue toughness;
        private static ForgeConfigSpec.DoubleValue knockbackResistance;
        private static ForgeConfigSpec.DoubleValue maxHealth;
        private static ForgeConfigSpec.ConfigValue<List<? extends Number>> maxMru;
        private static ForgeConfigSpec.IntValue mruPerDamage;
        private static ForgeConfigSpec.ConfigValue<List<? extends Number>> sharesHelmet;
        private static ForgeConfigSpec.ConfigValue<List<? extends Number>> sharesChestplate;
        private static ForgeConfigSpec.ConfigValue<List<? extends Number>> sharesLeggings;
        private static ForgeConfigSpec.ConfigValue<List<? extends Number>> sharesBoots;
        private static ForgeConfigSpec.DoubleValue dischargePriceMultiplier;
        private static ForgeConfigSpec.IntValue afterflowTicks;
        private static ForgeConfigSpec.IntValue afterflowCooldown;
        @Nullable
        private static volatile Values testOverride;

        private MHSConfig() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Magical Hazmat Suit (MHS): attributes, the MRU each piece holds, Matrix Buffer (the MRU shield) and Afterflow. "
                            + "Lists are per attunement stage, stage 0 first (stages past the end use the last entry). "
                            + "Attribute changes reach pieces already worn after taking them off and putting them on again.",
                    "魔力防护：属性、每件自带的 MRU、矩阵缓冲（MRU 护盾）和余流。列表按同调阶排，从 0 阶起（超出的阶按最后一项）。"
                            + "改了属性以后，已经穿在身上的要脱下再穿一次才更新").push("mhs_armor");
            armorHelmet = builder.comment("Armor points of the helmet.", "头盔的护甲值")
                    .defineInRange("armor_helmet", DEFAULTS.armorHelmet(), 0D, 30D);
            armorChestplate = builder.comment("Armor points of the chestplate.", "胸甲的护甲值")
                    .defineInRange("armor_chestplate", DEFAULTS.armorChestplate(), 0D, 30D);
            armorLeggings = builder.comment("Armor points of the leggings.", "护腿的护甲值")
                    .defineInRange("armor_leggings", DEFAULTS.armorLeggings(), 0D, 30D);
            armorBoots = builder.comment("Armor points of the boots.", "靴子的护甲值")
                    .defineInRange("armor_boots", DEFAULTS.armorBoots(), 0D, 30D);
            toughness = builder.comment("Armor toughness of each piece (vanilla caps the total at 20).", "每件的盔甲韧性（原版总韧性最多算 20）")
                    .defineInRange("toughness", DEFAULTS.toughness(), 0D, 20D);
            knockbackResistance = builder.comment("Knockback resistance of each piece (0 to 1; 1 = immune).", "每件的击退抗性（0~1，1 = 完全不被击退）")
                    .defineInRange("knockback_resistance", DEFAULTS.knockbackResistance(), 0D, 1D);
            maxHealth = builder.comment("Extra max health of each piece, in health points (2 = one heart).", "每件加的最大生命（生命值，2 = 一颗心）")
                    .defineInRange("max_health", DEFAULTS.maxHealth(), 0D, 1024D);
            maxMru = builder.comment("MRU each piece holds, per attunement stage. The pieces are not MRU storages: nothing else draws power from them.",
                            "每件自带多少 MRU，按同调阶。不是储存物品：别的东西不会从它们身上取电")
                    .defineList("max_mru", DEFAULTS.maxMru(), o -> o instanceof Number n && n.longValue() >= 0L && n.longValue() <= 100_000_000L);
            mruPerDamage = builder.comment("Matrix Buffer: MRU spent per point of damage a charged piece blocks (each piece counts at least 1 point).",
                            "矩阵缓冲：有电的一件每挡下 1 点伤害花多少 MRU（每件至少按 1 点算）")
                    .defineInRange("mru_per_damage", DEFAULTS.mruPerDamage(), 0, 1_000_000);
            sharesHelmet = defineShares(builder, "shares_helmet", "helmet", "头盔", DEFAULTS.sharesHelmet());
            sharesChestplate = defineShares(builder, "shares_chestplate", "chestplate", "胸甲", DEFAULTS.sharesChestplate());
            sharesLeggings = defineShares(builder, "shares_leggings", "leggings", "护腿", DEFAULTS.sharesLeggings());
            sharesBoots = defineShares(builder, "shares_boots", "boots", "靴子", DEFAULTS.sharesBoots());
            dischargePriceMultiplier = builder.comment("Attunement stage 2+: MRU discharge damage from a non-player attacker is blocked too, at this many times the cost per point.",
                            "同调 2 阶起：也挡非玩家攻击者打来的 MRU 放电，每点单价乘这么多")
                    .defineInRange("discharge_price_multiplier", DEFAULTS.dischargePriceMultiplier(), 0D, 1000D);
            afterflowTicks = builder.comment("Afterflow (all four pieces at stage 1+): ticks of Haste I after the shield blocks part of a hit (0 = off). "
                                    + "Scaled by general.attunement.pvp_effect_scale when the attacker is a player.",
                            "余流（四件都到 1 阶）：护盾挡下一下后给几刻急迫 I（0 = 不给）。攻击者是玩家时乘 general.attunement.pvp_effect_scale")
                    .defineInRange("afterflow_ticks", DEFAULTS.afterflowTicks(), 0, 72_000);
            afterflowCooldown = builder.comment("Afterflow: minimum ticks between two triggers.", "余流：两次之间至少隔多少刻")
                    .defineInRange("afterflow_cooldown", DEFAULTS.afterflowCooldown(), 0, 72_000);
            builder.pop();
        }

        private static ForgeConfigSpec.ConfigValue<List<? extends Number>> defineShares(ForgeConfigSpec.Builder builder, String key, String en, String zh,
                                                                                        List<Integer> defaults) {
            return builder.comment("Matrix Buffer: share of each hit the " + en + " blocks while charged, in twentieths, per attunement stage "
                                    + "(four pieces adding up to 20 block the whole hit).",
                            "矩阵缓冲：有电时" + zh + "挡下每一下的几份（二十分之几），按同调阶（四件合计 20 就是整下挡掉）")
                    .defineList(key, defaults, o -> o instanceof Number n && n.longValue() >= 0L && n.longValue() <= MAX_SHARE);
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (armorHelmet == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(armorHelmet), ECConfig.get(armorChestplate), ECConfig.get(armorLeggings), ECConfig.get(armorBoots),
                    ECConfig.get(toughness), ECConfig.get(knockbackResistance), ECConfig.get(maxHealth),
                    ints(maxMru, DEFAULTS.maxMru()), ECConfig.get(mruPerDamage),
                    ints(sharesHelmet, DEFAULTS.sharesHelmet()), ints(sharesChestplate, DEFAULTS.sharesChestplate()),
                    ints(sharesLeggings, DEFAULTS.sharesLeggings()), ints(sharesBoots, DEFAULTS.sharesBoots()),
                    ECConfig.get(dischargePriceMultiplier), ECConfig.get(afterflowTicks), ECConfig.get(afterflowCooldown));
        }

        /**
         * 这一阶每件自带多少 MRU。物品的 MRU 能力每读一次存量都要问它，所以不建整套 {@link Values}，只读这一项。
         */
        public static int maxMru(int stage) {
            Values override = testOverride;
            if (override != null) {
                return override.maxMru(stage);
            }
            if (maxMru == null) {
                return DEFAULTS.maxMru(stage);
            }
            return pick(ints(maxMru, DEFAULTS.maxMru()), stage, 0, Integer.MAX_VALUE);
        }

        /** 列表配置按 Number 读（TOML 里写整数时会是 Long） */
        private static List<Integer> ints(ForgeConfigSpec.ConfigValue<List<? extends Number>> value, List<Integer> fallback) {
            List<? extends Number> raw;
            try {
                raw = ECConfig.getValue(value);
            } catch (IllegalStateException e) {
                return fallback;
            }
            List<Integer> out = new ArrayList<>(raw.size());
            for (Number n : raw) {
                out.add((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, n.longValue())));
            }
            return out;
        }

        /** 列表里第 stage 项（越界按两头夹；空表 0），再夹到 [min, max] */
        static int pick(List<Integer> list, int stage, int min, int max) {
            if (list.isEmpty()) {
                return 0;
            }
            int value = list.get(Math.max(0, Math.min(list.size() - 1, stage)));
            return Math.max(min, Math.min(max, value));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回（跨刻的测试在最后一步置回） */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }

    // ---------------- 注册工具 ----------------

    /** 原作 ItemPickaxeEC → 1.12 ItemPickaxe：攻击 1 + 材料加成，速度 −2.8 */
    private static RegistryObject<PickaxeItem> pickaxe(String name, ElementalTier tier, String texture) {
        return ECItems.register(name, () -> new PickaxeItem(tier, 1, -2.8F, new Item.Properties()),
                ItemSpec.handheld("item/tools/" + texture).tags(ItemTags.PICKAXES, ItemTags.CLUSTER_MAX_HARVESTABLES));
    }

    /**
     * 原作 ItemAxeEC 调的是 1.12 ItemAxe(material, damage, speed)，把材料的攻击 5 当伤害、把<b>挖掘效率 15</b> 当攻击速度传了进去：
     * 攻击速度 4 + 15 = 19，攻击冷却几乎没有，秒伤远超同材料的剑（显然是传错了参数）。这里照原版钻石 / 下界合金斧：伤害 +5（再加材料加成）、速度 −3.0。
     */
    private static RegistryObject<AxeItem> axe(String name, ElementalTier tier, String texture) {
        return ECItems.register(name, () -> new AxeItem(tier, 5F, -3F, new Item.Properties()),
                ItemSpec.handheld("item/tools/" + texture).tags(ItemTags.AXES));
    }

    /** 原作 ItemShovelEC → 1.12 ItemSpade：攻击 1.5 + 材料加成，速度 −3.0 */
    private static RegistryObject<ShovelItem> shovel(String name, ElementalTier tier, String texture) {
        return ECItems.register(name, () -> new ShovelItem(tier, 1.5F, -3F, new Item.Properties()),
                ItemSpec.handheld("item/tools/" + texture).tags(ItemTags.SHOVELS));
    }

    /** 原作 ItemHoeEC → 1.12 ItemHoe：不加攻击，攻击速度 = 材料攻击加成 − 3 */
    private static RegistryObject<HoeItem> hoe(String name, ElementalTier tier, int attackModifier, float speed, String texture) {
        return ECItems.register(name, () -> new HoeItem(tier, attackModifier, speed, new Item.Properties()),
                ItemSpec.handheld("item/tools/" + texture).tags(ItemTags.HOES));
    }

    /** 原作 ItemSwordEC → 1.12 ItemSword：攻击 3 + 材料加成，速度 −2.4 */
    private static RegistryObject<SwordItem> sword(String name, ElementalTier tier, String texture) {
        return ECItems.register(name, () -> new SwordItem(tier, 3, -2.4F, new Item.Properties()),
                ItemSpec.handheld("item/tools/" + texture).tags(ItemTags.SWORDS));
    }

    /**
     * 魔力防护：物品图标是 BEWLR 画的 3D 盔甲（builtin/entity），显示变换照原版方块（原作 ArmorRenderer 用的就是 DEFAULT_BLOCK）；
     * 粒子贴图借魔力板（盔甲贴图不在图集里）。不写 gui_light，默认就是方块那样的侧光。
     */
    private static RegistryObject<MHSArmorItem> armor(String name, ArmorItem.Type type, TagKey<Item> forgeTag) {
        return ECItems.register(name, () -> new MHSArmorItem(type, new Item.Properties()),
                ItemSpec.of(ECGear::builtinArmorModel).tags(forgeTag));
    }

    private static void builtinArmorModel(ECItemModelProvider provider, Item item) {
        provider.getBuilder(provider.name(item))
                .parent(new ModelFile.UncheckedModelFile("builtin/entity"))
                .texture("particle", provider.tex("item/misc/magicplate"))
                .transforms()
                .transform(ItemDisplayContext.GUI).rotation(30F, 225F, 0F).scale(0.625F).end()
                .transform(ItemDisplayContext.GROUND).translation(0F, 3F, 0F).scale(0.25F).end()
                .transform(ItemDisplayContext.FIXED).scale(0.5F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(75F, 45F, 0F).translation(0F, 2.5F, 0F).scale(0.375F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0F, 45F, 0F).scale(0.4F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0F, 225F, 0F).scale(0.4F).end()
                .end();
    }
}
