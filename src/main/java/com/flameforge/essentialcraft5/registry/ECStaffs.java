package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.ElementalTier;
import com.flameforge.essentialcraft5.item.MagicalShieldItem;
import com.flameforge.essentialcraft5.item.MagmaticStaffItem;
import com.flameforge.essentialcraft5.item.MagnetizingStaffItem;
import com.flameforge.essentialcraft5.item.MatrixTraits;
import com.flameforge.essentialcraft5.item.MatrixWeaponItem;
import com.flameforge.essentialcraft5.item.SpikyShieldItem;
import com.flameforge.essentialcraft5.item.StaffOfLifeItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;

/**
 * 法杖与武器模块（第三批 §3.2）：黄金法球、岩浆法杖、磁化法杖、生命法杖、魔力护盾、尖刺圆盾、混沌之叉、冰冻重锤、神圣重锤。
 * <p>
 * 新旧注册名（总表见 tools/id_map_items.json）：genitem:4（magicalGoldenOrb）→ magical_golden_orb；magmaticStaff → magmatic_staff；
 * magnetizingStaff → magnetizing_staff；staffOfLife → staff_of_life；magicalShield → magical_shield；spikyShield → spiky_shield；
 * chaosFork → chaos_fork；frozenMace → frozen_mace；holyMace → holy_mace。
 * <p>
 * 除黄金法球外都是 MRU 物品（自带 5 000 MRU，创造栏放空 / 满两份），扣费走 {@code MRUDrain.playerUseMRU}。
 * 改世界的两根法杖照 policy §4.2 限制：岩浆法杖先问破坏事件、放行才扣费（{@link MagmaticStaffItem}）；
 * 生命法杖点中的那一格走完整的放置事件、周围 11×11 只催作物且逐格问权限（{@link StaffOfLifeItem}）；
 * 磁化法杖只拉看得见的、不拉别人的掉落物、每次有上限（{@link MagnetizingStaffItem}）。
 * <p>
 * 文字 tools/lang/keys/ecstaffs.json，标签 tools/tags/ecstaffs.json（三把武器进 minecraft:swords），
 * 配方 data/essentialcraft5/recipes/staffs/（黄金法球的魔力工作台配方二批已预写在 alloys/magical_golden_orb.json），
 * 研究手册支线「法杖与武器」assets/essentialcraft5/guide/staffs.json。没有客户端类：物品都是普通的手持模型，不需要额外渲染。
 * 事件监听只有一个：生命法杖右键生物走 {@code item/StaffOfLifeInteractEvents}（Forge 总线、两端，抢在生物自己的右键之前）。
 * 配置在 SERVER 配置 {@code general.staffs}（{@link Config}）。
 * <p>
 * 第十三批 13a（batch13.md §4.6，早期武器修补，改的是回报不是门槛）：混沌之叉攻击 7 → 12、攻速 1.6 → 2.0（配方里那块永恒物质吃一颗下界之星，回报要对得上）；
 * 神圣重锤打亡灵 +{@code holy_mace_undead_bonus}（50%）、矩阵特性 ×{@code holy_mace_trait_multiplier}（2）；混沌之叉、冰冻重锤叠效果的上限再加相性级
 * （{@link MatrixWeaponItem}）。三把都在标签 {@code essentialcraft5:matrix_attuned} 里，相性 ≥ I 时打实另有矩阵特性（{@link MatrixTraits}，
 * 由这里的 {@link #init()} 登记）。弱化元素材料的攻击加成 2.5 → 3 在 {@link ElementalTier}。
 */
public final class ECStaffs {

    /** 原作 genitem:4：中间材料（磁化法杖 ×2、生命法杖、神圣重锤） */
    public static final RegistryObject<Item> MAGICAL_GOLDEN_ORB = ECItems.register("magical_golden_orb",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/magicalgoldenorb"));

    public static final RegistryObject<MagmaticStaffItem> MAGMATIC_STAFF = ECItems.register("magmatic_staff",
            () -> new MagmaticStaffItem(new Item.Properties().stacksTo(1)), ItemSpec.handheld("item/tools/magmaticwand"));

    public static final RegistryObject<MagnetizingStaffItem> MAGNETIZING_STAFF = ECItems.register("magnetizing_staff",
            () -> new MagnetizingStaffItem(new Item.Properties().stacksTo(1)), ItemSpec.handheld("item/tools/magnetizingstaff"));

    public static final RegistryObject<StaffOfLifeItem> STAFF_OF_LIFE = ECItems.register("staff_of_life",
            () -> new StaffOfLifeItem(new Item.Properties().stacksTo(1)), ItemSpec.handheld("item/tools/staffoflife"));

    public static final RegistryObject<MagicalShieldItem> MAGICAL_SHIELD = ECItems.register("magical_shield",
            () -> new MagicalShieldItem(new Item.Properties().stacksTo(1)), ItemSpec.handheld("item/tools/magicalshield"));

    public static final RegistryObject<SpikyShieldItem> SPIKY_SHIELD = ECItems.register("spiky_shield",
            () -> new SpikyShieldItem(new Item.Properties().stacksTo(1)), ItemSpec.handheld("item/tools/spikyshield"));

    /**
     * 原作攻击 +6、速度 −2.4；第十三批改成攻击 12、攻速 2.0（batch13.md §4.6）：1.20 的剑参数再加元素材料的攻击加成 5 和空手的 1，所以传 6、攻速修正 −2
     */
    public static final RegistryObject<MatrixWeaponItem> CHAOS_FORK = weapon("chaos_fork", 6, MatrixWeaponItem.CHAOS_FORK_SPEED, MatrixWeaponItem.Bonus.CHAOS, "chaosfork");
    /** 原作攻击 +16 */
    public static final RegistryObject<MatrixWeaponItem> FROZEN_MACE = weapon("frozen_mace", 11, MatrixWeaponItem.ATTACK_SPEED, MatrixWeaponItem.Bonus.FROZEN, "frozenmace");
    /** 原作攻击 +12，没有原作的矩阵效果（E7 照原作）；第十三批起打亡灵 +50%、矩阵特性 ×2（{@link Config}） */
    public static final RegistryObject<MatrixWeaponItem> HOLY_MACE = weapon("holy_mace", 7, MatrixWeaponItem.ATTACK_SPEED, MatrixWeaponItem.Bonus.NONE, "holymace");

    private ECStaffs() {
    }

    public static void init() {
        ElementalTier.init();
        ECConfig.general(Config::define);
        MatrixTraits.init();
    }

    private static RegistryObject<MatrixWeaponItem> weapon(String name, int attackModifier, float attackSpeed, MatrixWeaponItem.Bonus bonus, String texture) {
        return ECItems.register(name, () -> new MatrixWeaponItem(ElementalTier.ELEMENTAL, attackModifier, attackSpeed, bonus,
                new Item.Properties()), ItemSpec.handheld("item/tools/" + texture));
    }

    /**
     * SERVER 配置 {@code general.staffs}：第三批新定的冷却和上限（policy §4.2），原作的数值（MRU 花费、范围）写死在物品类里。
     * 读值用下面的静态方法，配置没加载时返回默认值。
     */
    public static final class Config {
        public static final int DEFAULT_LIFE_COOLDOWN = 10;
        public static final int DEFAULT_LIFE_RADIUS = 5;
        public static final int DEFAULT_MAGNET_MAX_ITEMS = 32;
        public static final int DEFAULT_MAGNET_MAX_ORBS = 32;
        public static final int DEFAULT_WEAPON_MAX_LEVEL = 3;
        public static final double DEFAULT_HOLY_MACE_UNDEAD_BONUS = 0.5D;
        public static final double DEFAULT_HOLY_MACE_TRAIT_MULTIPLIER = 2D;

        private static ForgeConfigSpec.IntValue lifeCooldown;
        private static ForgeConfigSpec.IntValue lifeRadius;
        private static ForgeConfigSpec.IntValue magnetMaxItems;
        private static ForgeConfigSpec.IntValue magnetMaxOrbs;
        private static ForgeConfigSpec.IntValue weaponMaxLevel;
        private static ForgeConfigSpec.DoubleValue holyMaceUndeadBonus;
        private static ForgeConfigSpec.DoubleValue holyMaceTraitMultiplier;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Staffs and MRU weapons.", "法杖与武器").push("staffs");
            lifeCooldown = builder.comment("Staff of Life: cooldown after each use, in ticks.", "生命法杖每次使用后的冷却（刻）")
                    .defineInRange("staff_of_life_cooldown", DEFAULT_LIFE_COOLDOWN, 0, 1200);
            lifeRadius = builder.comment("Staff of Life: radius of the crop area around the clicked block (5 = 11x11, the original).",
                            "生命法杖范围催熟的半径（5 即原作的 11×11，最多 121 格）")
                    .defineInRange("staff_of_life_radius", DEFAULT_LIFE_RADIUS, 0, 5);
            magnetMaxItems = builder.comment("Magnetizing Staff: most item entities pulled per pulse (nearest first).",
                            "磁化法杖每次最多拉几个掉落物（近的先）")
                    .defineInRange("magnetizing_max_items", DEFAULT_MAGNET_MAX_ITEMS, 0, 64);
            magnetMaxOrbs = builder.comment("Magnetizing Staff: most experience orbs pulled per pulse (nearest first).",
                            "磁化法杖每次最多拉几个经验球（近的先）")
                    .defineInRange("magnetizing_max_orbs", DEFAULT_MAGNET_MAX_ORBS, 0, 64);
            weaponMaxLevel = builder.comment("Chaos Fork / Frozen Mace: highest level their stacking effects can reach (the original had no cap). "
                                    + "Your Matrix Alignment level (I / II / III) is added on top.",
                            "混沌之叉、冰冻重锤叠加效果的最高等级（原作不封顶）；你的相性级（I / II / III）再加在上面")
                    .defineInRange("weapon_max_effect_level", DEFAULT_WEAPON_MAX_LEVEL, 1, 10);
            holyMaceUndeadBonus = builder.comment("Holy Mace: extra damage against undead mobs (0.5 = +50%).", "神圣重锤打亡灵多打多少（0.5 = +50%）")
                    .defineInRange("holy_mace_undead_bonus", DEFAULT_HOLY_MACE_UNDEAD_BONUS, 0D, 10D);
            holyMaceTraitMultiplier = builder.comment("Holy Mace: its Matrix Trait is this many times as strong as other weapons'.",
                            "神圣重锤的矩阵特性是别的武器的几倍")
                    .defineInRange("holy_mace_trait_multiplier", DEFAULT_HOLY_MACE_TRAIT_MULTIPLIER, 0D, 10D);
            builder.pop();
        }

        public static int lifeCooldown() {
            return lifeCooldown == null ? DEFAULT_LIFE_COOLDOWN : ECConfig.get(lifeCooldown);
        }

        public static int lifeRadius() {
            return lifeRadius == null ? DEFAULT_LIFE_RADIUS : ECConfig.get(lifeRadius);
        }

        public static int magnetMaxItems() {
            return magnetMaxItems == null ? DEFAULT_MAGNET_MAX_ITEMS : ECConfig.get(magnetMaxItems);
        }

        public static int magnetMaxOrbs() {
            return magnetMaxOrbs == null ? DEFAULT_MAGNET_MAX_ORBS : ECConfig.get(magnetMaxOrbs);
        }

        /** 叠加效果最高几级（1 = I 级）；放大器（amplifier）上限是它减 1。相性级另加，见 {@link MatrixWeaponItem} */
        public static int weaponMaxLevel() {
            return weaponMaxLevel == null ? DEFAULT_WEAPON_MAX_LEVEL : ECConfig.get(weaponMaxLevel);
        }

        public static double holyMaceUndeadBonus() {
            return holyMaceUndeadBonus == null ? DEFAULT_HOLY_MACE_UNDEAD_BONUS : ECConfig.get(holyMaceUndeadBonus);
        }

        public static double holyMaceTraitMultiplier() {
            return holyMaceTraitMultiplier == null ? DEFAULT_HOLY_MACE_TRAIT_MULTIPLIER : ECConfig.get(holyMaceTraitMultiplier);
        }
    }
}
