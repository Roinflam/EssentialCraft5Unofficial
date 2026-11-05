package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.api.HoannaWard;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.VoidArmorEvents;
import com.flameforge.essentialcraft5.item.VoidArmorItem;
import com.flameforge.essentialcraft5.item.armor.ArmorBonus;
import com.flameforge.essentialcraft5.item.armor.ECArmorItem;
import com.flameforge.essentialcraft5.item.armor.ECArmorMaterial;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 虚空套装模块（第七批 M3，计划 tools/plan/batch7.md §4.3，防护的定法见 §9.1 D1）：虚空头盔 / 胸甲 / 护腿 / 靴子。
 * <p>
 * 新旧注册名：helm_void / chest_void / legs_void / boots_void（原作 ItemArmorEC 的 magicArmorItems[4~7]，aType 1）
 * → void_helmet / void_chestplate / void_leggings / void_boots。
 * <ul>
 *   <li>外观：原作 ModelArmorEC 形状（{@code special/armor/armorec3void_0 / _1}，同魔力防护、风法师套装），物品图标是框架的 3D 盔甲图标。</li>
 *   <li>属性：护甲、韧性、击退抗性、每件最大生命做成服务端配置 {@code general.void_armor}（{@link Config}），默认下界合金套的两倍
 *       （护甲 6 / 16 / 12 / 6、每件韧性 6、击退抗性 0.2），每件 +20 最大生命（四件合计 +80，原作只有胸甲 +20，算在里面）；
 *       不看电量。靴子另有移速 +0.075、登高 +0.6（{@link ArmorBonus}）。</li>
 *   <li>有电就挡物理伤害、胸甲闪避、靴子减摔、头盔饱食、脱下夹回生命：见 {@code item/VoidArmorEvents}；每件存的 MRU、每挡 1 点的单价
 *       （第十三批起是配置 {@code max_mru} / {@code mru_per_damage}，默认 5 万 / 400，batch13.md D13）、MRU 抗性、
 *       穿在玩家身上不掉耐久：见 {@link VoidArmorItem}。</li>
 *   <li>头盔能看见 MRU：tools/tags/ecvoidarmor.json 把它加进 {@code essentialcraft5:mru_vision}。</li>
 *   <li>第十三批 13b（batch13.md §4.3 / §4.4）：同调家族 {@code void_armor}（最高 4 阶，{@code ECAttunement.VOID_ARMOR}），阶效果、全套「虚空回击」、
 *       相位见 {@code item/VoidArmorEvents}；海纳尔环境抗性 {@code api/HoannaWard} 的来源在 {@link #init} 登记；
 *       升阶配方 data/essentialcraft5/recipes/attunement/void_*.json（§4.5）。</li>
 * </ul>
 * 配方 data/essentialcraft5/recipes/void_armor/（第二行中间放同部位的魔力防护，附魔不带过来，照原作）；文字 tools/lang/keys/ecvoidarmor.json；
 * 手册打勾进度 advancements/guide/gear/void_armor.json。
 */
public final class ECVoidArmor {

    /**
     * 原作 VoidFortified（ItemsCore L467：附魔 40、韧性 0，耐久按件 setMaxDamage 2048 / 6144 / 4096 / 2048），没有修复材料。
     * 护甲值、韧性、击退抗性写的是 {@link Config} 的默认值：盔甲属性由 {@link VoidArmorItem} 按配置给，这三个数只给原版按材料取值的地方
     * （生物挑装备比 {@code ArmorItem.getDefense()} 之类）。
     */
    public static final ECArmorMaterial MATERIAL = ECArmorMaterial.builder("void")
            .durability(2048, 6144, 4096, 2048)
            .defense(6, 16, 12, 6)
            .enchantability(40)
            .toughness(6F)
            .knockbackResistance(0.2F)
            .build();

    /** 靴子：移速 +0.075（原作 ItemArmorEC L94~96，ADDITION）、登高 +0.6 到 1.2 格（原作 PlayerTickHandler L442~450 每 tick 写 stepHeight） */
    public static final ArmorBonus BOOTS_BONUS = ArmorBonus.of()
            .add(() -> Attributes.MOVEMENT_SPEED, 0.075D, AttributeModifier.Operation.ADDITION)
            .add(ForgeMod.STEP_HEIGHT_ADDITION, 0.6D, AttributeModifier.Operation.ADDITION);

    public static final RegistryObject<VoidArmorItem> VOID_HELMET = armor("void_helmet", ArmorItem.Type.HELMET, ArmorBonus.none(),
            Tags.Items.ARMORS_HELMETS);
    public static final RegistryObject<VoidArmorItem> VOID_CHESTPLATE = armor("void_chestplate", ArmorItem.Type.CHESTPLATE, ArmorBonus.none(),
            Tags.Items.ARMORS_CHESTPLATES);
    public static final RegistryObject<VoidArmorItem> VOID_LEGGINGS = armor("void_leggings", ArmorItem.Type.LEGGINGS, ArmorBonus.none(),
            Tags.Items.ARMORS_LEGGINGS);
    public static final RegistryObject<VoidArmorItem> VOID_BOOTS = armor("void_boots", ArmorItem.Type.BOOTS, BOOTS_BONUS,
            Tags.Items.ARMORS_BOOTS);

    private ECVoidArmor() {
    }

    /**
     * 由 ECRegistries.register() 调用：物品在类加载时登记，这里登记服务端配置 general.void_armor、海纳尔环境抗性的来源，
     * 以及全套 2 阶海纳尔辐射易伤减半的倍率（{@link VoidArmorItem#radiationMultiplier}）
     */
    public static void init() {
        ECConfig.general(Config::define);
        HoannaWard.register(VoidArmorEvents::hoannaWard);
        MRUResistance.registerMultiplier(VoidArmorItem::radiationMultiplier);
    }

    /** 四件，按头 / 胸 / 腿 / 靴 */
    public static List<RegistryObject<VoidArmorItem>> all() {
        return List.of(VOID_HELMET, VOID_CHESTPLATE, VOID_LEGGINGS, VOID_BOOTS);
    }

    private static RegistryObject<VoidArmorItem> armor(String name, ArmorItem.Type type, ArmorBonus bonus, TagKey<Item> forgeTag) {
        return ECItems.register(name, () -> new VoidArmorItem(type, new Item.Properties(), bonus),
                ECArmorItem.icon3d("item/misc/voidplating").tags(forgeTag));
    }

    /**
     * 服务端配置 {@code general.void_armor}：四件的护甲值、每件的韧性 / 击退抗性 / 最大生命（{@link #values}），
     * 加上第十三批的每件存多少 MRU、每挡 1 点花多少（{@link #shield}，D13）。SERVER 配置随进服同步给客户端，提示框里的数和服务端一致。
     * 属性改了以后已经穿在身上的要换一次装才更新（原版只在装备变化时重算属性）；MRU 容量和单价每次现读，改了立刻生效（容量改小时存量按新容量截断着读）。
     * GameTest 用 {@link #setTestOverrides} / {@link #setShieldTestOverrides}，不碰真配置。
     */
    public static final class Config {

        /** 一套数值：四件的护甲值，加上每件都一样的韧性、击退抗性、最大生命 */
        public record Values(double helmet, double chestplate, double leggings, double boots,
                             double toughness, double knockbackResistance, double maxHealth) {

            /** 这一件的护甲值 */
            public double armor(ArmorItem.Type type) {
                return switch (type) {
                    case HELMET -> helmet;
                    case CHESTPLATE -> chestplate;
                    case LEGGINGS -> leggings;
                    case BOOTS -> boots;
                };
            }
        }

        /** 默认值：下界合金套（3 / 8 / 6 / 3、韧性 3、击退抗性 0.1）的两倍，每件 +20 生命 */
        public static final Values DEFAULTS = new Values(6D, 16D, 12D, 6D, 6D, 0.2D, 20D);

        /**
         * 电池（第十三批 D13）：0 阶每件存多少 MRU、挡下的伤害每点花多少（0 阶原价，同调阶的倍数另算，见 {@code item/VoidArmorItem}）。
         * 原作是 5 000 / 800，胸甲自己的电挡不了 18 点以上的一下，等于没有电池；想要旧手感的服把这两项改回去。
         *
         * @param maxMru       0 阶每件存多少 MRU（1 阶起 ×2）
         * @param mruPerDamage 每挡 1 点伤害花多少 MRU（0 = 不花电，只要这件自己有电就挡）
         */
        public record Shield(int maxMru, int mruPerDamage) {

            public Shield {
                maxMru = Math.max(1, maxMru);
                mruPerDamage = Math.max(0, mruPerDamage);
            }
        }

        public static final Shield SHIELD_DEFAULTS = new Shield(50_000, 400);

        private static ForgeConfigSpec.DoubleValue armorHelmet;
        private static ForgeConfigSpec.DoubleValue armorChestplate;
        private static ForgeConfigSpec.DoubleValue armorLeggings;
        private static ForgeConfigSpec.DoubleValue armorBoots;
        private static ForgeConfigSpec.DoubleValue toughness;
        private static ForgeConfigSpec.DoubleValue knockbackResistance;
        private static ForgeConfigSpec.DoubleValue maxHealth;
        private static ForgeConfigSpec.IntValue maxMru;
        private static ForgeConfigSpec.IntValue mruPerDamage;
        @Nullable
        private static volatile Values testOverride;
        @Nullable
        private static volatile Shield shieldOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Void armor: attribute values (always on, charged or not). Pieces already worn update after being taken off and put on again.",
                    "虚空套装的属性（有没有电都一样）。改了以后，已经穿在身上的要脱下再穿一次才更新").push("void_armor");
            armorHelmet = builder.comment("Armor points of the helmet (vanilla caps a player's total armor at 30).",
                            "头盔的护甲值（原版玩家的总护甲最多算 30）")
                    .defineInRange("armor_helmet", DEFAULTS.helmet(), 0D, 30D);
            armorChestplate = builder.comment("Armor points of the chestplate.", "胸甲的护甲值")
                    .defineInRange("armor_chestplate", DEFAULTS.chestplate(), 0D, 30D);
            armorLeggings = builder.comment("Armor points of the leggings.", "护腿的护甲值")
                    .defineInRange("armor_leggings", DEFAULTS.leggings(), 0D, 30D);
            armorBoots = builder.comment("Armor points of the boots.", "靴子的护甲值")
                    .defineInRange("armor_boots", DEFAULTS.boots(), 0D, 30D);
            toughness = builder.comment("Armor toughness of each piece (vanilla caps the total at 20).",
                            "每件的盔甲韧性（原版总韧性最多算 20）")
                    .defineInRange("toughness", DEFAULTS.toughness(), 0D, 20D);
            knockbackResistance = builder.comment("Knockback resistance of each piece (0 to 1; 1 = immune).",
                            "每件的击退抗性（0~1，1 = 完全不被击退）")
                    .defineInRange("knockback_resistance", DEFAULTS.knockbackResistance(), 0D, 1D);
            maxHealth = builder.comment("Extra max health of each piece, in health points (2 = one heart).",
                            "每件加的最大生命（生命值，2 = 一颗心）")
                    .defineInRange("max_health", DEFAULTS.maxHealth(), 0D, 1024D);
            maxMru = builder.comment("MRU stored by each piece at attunement stage 0 (doubled from stage 1). The original mod stored 5000.",
                            "0 阶每件存多少 MRU（同调 1 阶起翻倍）。原作是 5000")
                    .defineInRange("max_mru", SHIELD_DEFAULTS.maxMru(), 1, 100_000_000);
            mruPerDamage = builder.comment("MRU spent per point of physical damage a charged piece blocks (each piece counts at least 1 point; "
                                    + "attunement stage 3 lowers it to 7/8). 0 = blocking costs nothing while the piece holds any MRU. The original mod used 800.",
                            "有电的一件每挡下 1 点物理伤害花多少 MRU（每件至少按 1 点算；同调 3 阶起降到 7/8）。0 = 只要这件有电就白挡。原作是 800")
                    .defineInRange("mru_per_damage", SHIELD_DEFAULTS.mruPerDamage(), 0, 1_000_000);
            builder.pop();
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
                    ECConfig.get(toughness), ECConfig.get(knockbackResistance), ECConfig.get(maxHealth));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }

        /** 电池的现值：测试覆盖值 → 配置（没加载时默认值） */
        public static Shield shield() {
            Shield override = shieldOverride;
            if (override != null) {
                return override;
            }
            if (maxMru == null) {
                return SHIELD_DEFAULTS;
            }
            return new Shield(ECConfig.get(maxMru), ECConfig.get(mruPerDamage));
        }

        /** GameTest 用：电池换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setShieldTestOverrides(@Nullable Shield values) {
            shieldOverride = values;
        }
    }
}
