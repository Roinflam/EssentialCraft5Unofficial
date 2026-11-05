package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.WindArmorItem;
import com.flameforge.essentialcraft5.item.WindAxeItem;
import com.flameforge.essentialcraft5.item.WindGearEvents;
import com.flameforge.essentialcraft5.item.WindHoeItem;
import com.flameforge.essentialcraft5.item.WindPickaxeItem;
import com.flameforge.essentialcraft5.item.WindShovelItem;
import com.flameforge.essentialcraft5.item.WindSwordItem;
import com.flameforge.essentialcraft5.item.WindTier;
import com.flameforge.essentialcraft5.item.WindToolForms;
import com.flameforge.essentialcraft5.item.armor.ArmorBonus;
import com.flameforge.essentialcraft5.item.armor.ArmorLook;
import com.flameforge.essentialcraft5.item.armor.ECArmorMaterial;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
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
 * 风之装备模块（第三批 §3.4）：风之工具 ×5（会自己变形）、风之护甲 ×4（可染色）。材料是风之水晶（ECWind 注册的 {@code essentialcraft5:wind_gem}，
 * 配方按注册名引用、带 {@code forge:item_exists} 条件，ECWind 没接线时配方不加载）。
 * <p>
 * 新旧注册名（总表见 tools/id_map_items.json）：wind_elemental_pick → wind_elemental_pickaxe；wind_elemental_axe / shovel / hoe / sword、
 * wind_helmet / chestplate / leggings / boots 不变。
 * 文字 tools/lang/keys/ecwindgear.json，标签 tools/tags/ecwindgear.json（风之头盔进 {@code essentialcraft5:mru_vision}），
 * 配方 data/essentialcraft5/recipes/windgear/，研究手册支线章「风之装备」assets/essentialcraft5/guide/wind_gear.json，
 * 事件 {@link WindGearEvents}（开始挖方块时换形态、护腿跳得高），GameTest gametest/WindGearTests。不需要客户端类：
 * 护甲的物品颜色、穿在身上的贴图由框架（client/armor/ECArmorModels、ECArmorClientExtensions）自动处理。
 * <p>
 * <b>风之工具</b>（材料 {@link WindTier#WIND}：挖掘等级排在元素之后、耐久 15684、效率 15、攻击加成 6、附魔 42）：
 * 攻击数值照 ECGear 的换算（1.12 原版公式 → 1.20 构造参数）：镐 1 + 6、锹 1.5 + 6、剑 3 + 6，速度 −2.8 / −3.0 / −2.4；
 * 锄照 1.12 ItemHoe：不加攻击（−6 抵掉材料加成）、攻击速度 = 材料攻击加成 − 3 = +3；斧修了原作把效率 15 当攻速传进去的 bug，照 ECGear 用 5 + 6、−3.0。
 * 镐、斧、锹、锄打中生物不扣耐久（同原作：这四个的 hitEntity 不调父类；原版挖掘工具打人扣 2），剑照原版每下扣 1。
 * 变形规则见 {@link WindToolForms}。
 * <p>
 * <b>风之护甲</b>（{@link WindArmorItem}）：耐久 624 / 1248 / 936 / 624（原作 setMaxDamage），护甲值头 4、胸 10、腿 7、靴 4（E4；原作实际是 0），
 * 韧性每件 2.5（第十三批 13b，batch13.md §4.3；原作 0），附魔能力 40，穿戴音效 generic（原作 ITEM_ARMOR_EQUIP_GENERIC）；没有修复材料（原作没设），铁砧只能两件合一。
 * 胸甲移速 +0.075（加在基础值 0.1 上，原作 ItemGenericArmor L103 的 operation 0），靴子登高到 1.2 格（STEP_HEIGHT_ADDITION +0.6；
 * 原作每 tick 直接写 stepHeight，会覆盖别的模组，还用永不清理的 HashMap 记状态），护腿起跳 +0.2（{@link WindGearEvents}）。
 * <p>
 * <b>全套「御风」与同调</b>（第十三批 13b，batch13.md §4.3、§4.4；四件都是风之护甲才算，阶取四件里最低的，见 {@link WindArmorItem#fullSetStage}）：
 * <ul>
 *   <li>0 阶：别人射来的弹射物按固定间隔偏开一颗（复用 {@code player/WindPact} 的偏转，间隔 120 刻；身上同时有风之契约的「风之偏转」时两者取短的再减半，默认 30 刻）；
 *       空中再跳一次（竖直速度 0.5，客户端改自己的速度，不加包，{@code client/WindArmorClient}）；摔落伤害 ×0.25。不加风之好感系数（会变相降低 3 500 的门槛）。</li>
 *   <li>1 阶：偏转间隔 80 刻；潜行时不被击退。2 阶：空中能跳两次；疾跑时移速再 +10%。3 阶：偏转间隔 40 刻；偏开以后 20 刻「顺风」（移速 +20%、攻速 +10%）。</li>
 *   <li>疾跑、顺风是临时属性修饰符（固定 UUID），服务端每刻按当时的状态补上或摘掉，不靠换装事件：同调写盘改了物品 NBT、原版当成换了一件，也不会把效果摘掉或叠两层。</li>
 * </ul>
 * 数值在 SERVER 配置 {@code general.wind_armor}（{@link Config}，Forge 会同步给客户端，空中跳要用），逻辑在 {@link WindGearEvents}；
 * 升阶配方 {@code data/essentialcraft5/recipes/attunement/wind_{1,2,3}.json}（§4.5；2 阶 13c 起用矩阵结晶，3 阶的地脉结晶上线之前先用兜底材料，§9.6）。
 * <p>
 * 和原作不同（改变玩法的写进交付报告和手册）：
 * <ul>
 *   <li>五个形态共用一份耐久；名字等其余 NBT 共用；只有附魔和铁砧累计惩罚按形态分开（原作整个物品栈按形态收起来，每个形态各有一份耐久）。</li>
 *   <li>风之锄打中生物后也变成剑（原作 1.12 的锄不是 ItemTool，那段代码判不成立）。</li>
 *   <li>斧、锹形态锄不了的方块做原版斧 / 锹的右键动作（剥树皮、铲土径）；原作 1.12 没有这些动作。</li>
 *   <li>换形态在开始挖方块时两端一起换（原作在挖掘速度事件里只换服务端），这次左键被领地等取消了也照样换；
 *       创造模式挖方块不换、打中生物照样变剑（都同原作）。</li>
 *   <li>风之护甲有护甲值（E4）。</li>
 * </ul>
 */
public final class ECWindGear {

    /** 每件的韧性（batch13.md §4.1：25 护甲 + 10 韧性在每档伤害下都比下界合金挨得少） */
    public static final float ARMOR_TOUGHNESS = 2.5F;

    /** 原作 windArmorMaterial（ItemsCore L469）：耐久按件覆盖、护甲值按 E4、附魔 40、音效 generic；韧性原作 0，第十三批改 2.5 */
    public static final ECArmorMaterial WIND_ARMOR_MATERIAL = ECArmorMaterial.builder("wind")
            .durability(624, 1248, 936, 624)
            .defense(4, 10, 7, 4)
            .toughness(ARMOR_TOUGHNESS)
            .enchantability(40)
            .sound(() -> SoundEvents.ARMOR_EQUIP_GENERIC)
            .build();

    /** 原作 ItemGenericArmor.getArmorTexture：护腿 wind_layer_2，其余 wind_layer_1（原版形状 64×32） */
    private static final ArmorLook WIND_ARMOR_LOOK = ArmorLook.vanilla("special/armor/wind_layer_1", "special/armor/wind_layer_2");

    /** 胸甲移速（加在基础值上） */
    public static final double CHESTPLATE_SPEED = 0.075D;
    /** 靴子登高（玩家基础 0.6，合计 1.2） */
    public static final double BOOTS_STEP_HEIGHT = 0.6D;

    // ---------------- 风之工具 ----------------

    public static final RegistryObject<WindPickaxeItem> WIND_ELEMENTAL_PICKAXE = ECItems.register("wind_elemental_pickaxe",
            () -> new WindPickaxeItem(WindTier.WIND, 1, -2.8F, new Item.Properties()),
            ItemSpec.handheld("item/tools/wind_pickaxe").tags(ItemTags.PICKAXES, ItemTags.CLUSTER_MAX_HARVESTABLES));
    public static final RegistryObject<WindAxeItem> WIND_ELEMENTAL_AXE = ECItems.register("wind_elemental_axe",
            () -> new WindAxeItem(WindTier.WIND, 5F, -3F, new Item.Properties()),
            ItemSpec.handheld("item/tools/wind_axe").tags(ItemTags.AXES));
    public static final RegistryObject<WindShovelItem> WIND_ELEMENTAL_SHOVEL = ECItems.register("wind_elemental_shovel",
            () -> new WindShovelItem(WindTier.WIND, 1.5F, -3F, new Item.Properties()),
            ItemSpec.handheld("item/tools/wind_shovel").tags(ItemTags.SHOVELS));
    public static final RegistryObject<WindHoeItem> WIND_ELEMENTAL_HOE = ECItems.register("wind_elemental_hoe",
            () -> new WindHoeItem(WindTier.WIND, -6, 3F, new Item.Properties()),
            ItemSpec.handheld("item/tools/wind_hoe").tags(ItemTags.HOES));
    public static final RegistryObject<WindSwordItem> WIND_ELEMENTAL_SWORD = ECItems.register("wind_elemental_sword",
            () -> new WindSwordItem(WindTier.WIND, 3, -2.4F, new Item.Properties()),
            ItemSpec.handheld("item/tools/wind_sword").tags(ItemTags.SWORDS));

    // ---------------- 风之护甲 ----------------

    public static final RegistryObject<WindArmorItem> WIND_HELMET = armor("wind_helmet", ArmorItem.Type.HELMET,
            ArmorBonus.none(), Tags.Items.ARMORS_HELMETS);
    public static final RegistryObject<WindArmorItem> WIND_CHESTPLATE = armor("wind_chestplate", ArmorItem.Type.CHESTPLATE,
            ArmorBonus.of().add(() -> Attributes.MOVEMENT_SPEED, CHESTPLATE_SPEED, AttributeModifier.Operation.ADDITION), Tags.Items.ARMORS_CHESTPLATES);
    public static final RegistryObject<WindArmorItem> WIND_LEGGINGS = armor("wind_leggings", ArmorItem.Type.LEGGINGS,
            ArmorBonus.none(), Tags.Items.ARMORS_LEGGINGS);
    public static final RegistryObject<WindArmorItem> WIND_BOOTS = armor("wind_boots", ArmorItem.Type.BOOTS,
            ArmorBonus.of().add(ForgeMod.STEP_HEIGHT_ADDITION, BOOTS_STEP_HEIGHT, AttributeModifier.Operation.ADDITION), Tags.Items.ARMORS_BOOTS);

    private ECWindGear() {
    }

    /** 物品在类加载时登记；这里登记全套效果的配置 {@code general.wind_armor}。{@link WindTier} 的排序由 ECRegistries 登记 */
    public static void init() {
        ECConfig.general(Config::define);
    }

    /** 物品图标是 2D 贴图（原作 essentialcraft:item 模型，一层 layer0），第 0 层按染的颜色上色（框架 ECArmorModels 自动注册） */
    private static RegistryObject<WindArmorItem> armor(String name, ArmorItem.Type type, ArmorBonus bonus, TagKey<Item> forgeTag) {
        return ECItems.register(name, () -> new WindArmorItem(WIND_ARMOR_MATERIAL, type, new Item.Properties(), WIND_ARMOR_LOOK, bonus),
                ItemSpec.generated("item/armor/" + name).tags(forgeTag));
    }

    // ---------------- 配置 ----------------

    /**
     * 风之护甲全套「御风」的服务端配置 {@code general.wind_armor}（batch13.md §4.3、§4.4）。SERVER 配置会同步给客户端，客户端的空中跳读的是服务器的值。
     * 按阶给的两项是列表（第 i 项 = 全套 i 阶；列表比阶数短时后面的阶沿用最后一项，空列表按默认）。
     * GameTest 用 {@link #setTestOverrides}，不许用 ConfigValue.set（服务端配置自动存盘，文件监视器会把 set 进去的值冲掉）。
     */
    public static final class Config {

        /**
         * @param deflect                  全套能不能偏开弹射物
         * @param deflectCooldownTicks     按全套阶：偏开一颗以后多少刻内新来的照常命中（0 = 不等）
         * @param airJumps                 按全套阶：离地以后在空中能再跳几次（0 = 关掉空中跳）
         * @param airJumpVelocity          空中跳给的竖直速度（0 = 关掉空中跳）
         * @param fallDamageMultiplier     全套时摔落伤害乘的倍数（乘在别的倍数上）
         * @param sprintSpeedBonus         2 阶起疾跑时的移速加成（MULTIPLY_TOTAL，0.1 = +10%；0 = 不给）
         * @param tailwindTicks            3 阶起偏开一颗以后「顺风」持续几刻（0 = 不给）
         * @param tailwindSpeedBonus       顺风的移速加成（MULTIPLY_TOTAL）
         * @param tailwindAttackSpeedBonus 顺风的攻速加成（MULTIPLY_TOTAL）
         */
        public record Values(boolean deflect, List<Integer> deflectCooldownTicks, List<Integer> airJumps, double airJumpVelocity,
                             double fallDamageMultiplier, double sprintSpeedBonus, int tailwindTicks, double tailwindSpeedBonus,
                             double tailwindAttackSpeedBonus) {

            public Values {
                deflectCooldownTicks = List.copyOf(deflectCooldownTicks);
                airJumps = List.copyOf(airJumps);
            }

            /** 全套 stage 阶的偏转间隔（刻） */
            public int deflectCooldown(int stage) {
                return Math.max(0, at(deflectCooldownTicks, stage, DEFAULT_DEFLECT_COOLDOWN));
            }

            /** 全套 stage 阶在空中能再跳几次；竖直速度填 0 时也是 0 */
            public int airJumps(int stage) {
                return airJumpVelocity <= 0D ? 0 : Math.max(0, at(airJumps, stage, DEFAULT_AIR_JUMPS));
            }

            private static int at(List<Integer> values, int stage, List<Integer> fallback) {
                List<Integer> list = values.isEmpty() ? fallback : values;
                return list.get(Math.max(0, Math.min(stage, list.size() - 1)));
            }
        }

        public static final List<Integer> DEFAULT_DEFLECT_COOLDOWN = List.of(120, 80, 80, 40);
        public static final List<Integer> DEFAULT_AIR_JUMPS = List.of(1, 1, 2, 2);
        public static final Values DEFAULTS = new Values(true, DEFAULT_DEFLECT_COOLDOWN, DEFAULT_AIR_JUMPS, 0.5D, 0.25D, 0.1D, 20, 0.2D, 0.1D);

        private static ForgeConfigSpec.BooleanValue deflect;
        private static ForgeConfigSpec.ConfigValue<List<? extends Number>> deflectCooldownTicks;
        private static ForgeConfigSpec.ConfigValue<List<? extends Number>> airJumps;
        private static ForgeConfigSpec.DoubleValue airJumpVelocity;
        private static ForgeConfigSpec.DoubleValue fallDamageMultiplier;
        private static ForgeConfigSpec.DoubleValue sprintSpeedBonus;
        private static ForgeConfigSpec.IntValue tailwindTicks;
        private static ForgeConfigSpec.DoubleValue tailwindSpeedBonus;
        private static ForgeConfigSpec.DoubleValue tailwindAttackSpeedBonus;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Wind Armor full set (Windwalk). Stage lists: entry i is for a full set at attunement stage i (the lowest of the four pieces).",
                    "风之护甲全套效果（御风）。按阶的列表：第 i 项对应全套 i 阶（四件里最低的阶）").push("wind_armor");
            deflect = builder.comment("A full set lets projectiles from others pass through you, one every deflect_cooldown_ticks.",
                            "全套时别人射来的弹射物每隔 deflect_cooldown_ticks 有一颗从身上穿过去")
                    .define("deflect", DEFAULTS.deflect());
            deflectCooldownTicks = builder.comment("Ticks after a deflection during which new projectiles hit you normally, by full-set stage (20 = 1 second). "
                                    + "With the wind pact's Wind Deflection as well, the shorter of the two is halved.",
                            "按全套阶：偏开一颗以后多少刻内新来的照常命中（20 = 1 秒）。身上同时有风之契约的「风之偏转」时，取两者较短的再减半")
                    .defineList("deflect_cooldown_ticks", DEFAULT_DEFLECT_COOLDOWN, o -> o instanceof Number n && n.intValue() >= 0 && n.intValue() <= 1200);
            airJumps = builder.comment("Extra jumps in midair, by full-set stage. This is a list: write [0] to turn them off (a bare 0 is not a list and is replaced "
                                    + "with the default). More jumps keep players in the air longer; with allow-flight=false, vanilla kicks anyone off the ground "
                                    + "for 80 ticks (4 seconds, falling counts) for flying.",
                            "按全套阶：离地以后在空中能再跳几次。这是列表，要关掉写 [0]（只写一个 0 不是列表，会被换回默认值）。"
                                    + "次数越多，人在空中停得越久；服务器 allow-flight=false 时，原版对连续离地满 80 刻（4 秒，下落也算）的人按飞行踢出")
                    .defineList("air_jumps", DEFAULT_AIR_JUMPS, o -> o instanceof Number n && n.intValue() >= 0 && n.intValue() <= 5);
            airJumpVelocity = builder.comment("Upward speed of a midair jump (a normal jump is 0.42; 0 = off). The client moves itself, so if an anti-cheat "
                                    + "kicks players for it, set this to 0 and only the fall damage reduction stays. Faster jumps keep players in the air longer "
                                    + "(see air_jumps for vanilla's 80-tick flying kick).",
                            "空中跳的竖直速度（原版起跳 0.42；0 = 关掉）。空中跳是客户端自己改速度，服务器上的反作弊因此踢人时填 0，只留减摔。"
                                    + "速度越大，人在空中停得越久（原版 80 刻的飞行踢人见 air_jumps）")
                    .defineInRange("air_jump_velocity", DEFAULTS.airJumpVelocity(), 0D, 1D);
            fallDamageMultiplier = builder.comment("Fall damage multiplier with a full set (applied on top of other multipliers).", "全套时摔落伤害乘的倍数（乘在别的倍数上）")
                    .defineInRange("fall_damage_multiplier", DEFAULTS.fallDamageMultiplier(), 0D, 1D);
            sprintSpeedBonus = builder.comment("Extra movement speed while sprinting, full set at stage 2 or higher (0.1 = +10%).", "全套 2 阶起疾跑时多出的移速（0.1 = +10%）")
                    .defineInRange("sprint_speed_bonus", DEFAULTS.sprintSpeedBonus(), 0D, 2D);
            tailwindTicks = builder.comment("Ticks of Tailwind after a deflection, full set at stage 3 (0 = off).", "全套 3 阶起偏开一颗以后「顺风」持续几刻（0 = 关掉）")
                    .defineInRange("tailwind_ticks", DEFAULTS.tailwindTicks(), 0, 1200);
            tailwindSpeedBonus = builder.comment("Movement speed bonus during Tailwind (0.2 = +20%).", "顺风时的移速加成（0.2 = +20%）")
                    .defineInRange("tailwind_speed_bonus", DEFAULTS.tailwindSpeedBonus(), 0D, 2D);
            tailwindAttackSpeedBonus = builder.comment("Attack speed bonus during Tailwind (0.1 = +10%).", "顺风时的攻速加成（0.1 = +10%）")
                    .defineInRange("tailwind_attack_speed_bonus", DEFAULTS.tailwindAttackSpeedBonus(), 0D, 2D);
            builder.pop();
        }

        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (deflect == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(deflect), ints(ECConfig.getValue(deflectCooldownTicks)), ints(ECConfig.getValue(airJumps)),
                    ECConfig.get(airJumpVelocity), ECConfig.get(fallDamageMultiplier), ECConfig.get(sprintSpeedBonus), ECConfig.get(tailwindTicks),
                    ECConfig.get(tailwindSpeedBonus), ECConfig.get(tailwindAttackSpeedBonus));
        }

        /** TOML 里的整数可能读成 Long，统一转成 Integer */
        private static List<Integer> ints(List<? extends Number> values) {
            return values.stream().map(Number::intValue).toList();
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
