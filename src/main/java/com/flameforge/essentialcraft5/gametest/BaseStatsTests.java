package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.ForgedElementalSwordItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.attunement.ArmorBaseStats;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementBonuses;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.GearCaps;
import com.flameforge.essentialcraft5.item.attunement.GearCombatEvents;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.attunement.GearPool;
import com.flameforge.essentialcraft5.item.attunement.GearStat;
import com.flameforge.essentialcraft5.item.attunement.GearStatSheet;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData.Element;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECGuns;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 升阶时基础属性也涨：刀剑每阶的攻击并进属性栏本体那一条、总闸改管「属性栏 × 池子」的乘积；盔甲的护甲 / 韧性 / 击退抗性 / 生命按
 * {@code base_stats} 阶表并进本体那几条。每个属性只有一条修饰符，穿脱、升阶以后不留残余。
 * <p>
 * 临时登记的增伤来源只对本测试自己的玩家生效（并行的别的测试不受影响），finally 里撤掉、置回覆盖值。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BaseStatsTests {

    private static final String EMPTY = "empty";
    private static final double EPS = 1e-4D;

    private BaseStatsTests() {
    }

    // ================================================================ 刀剑

    /** 阶加成的算法：每阶 max(1, 原显示值 × 比例)，乘阶数取到 0.5（方案里的举例：51 → 71.5、10 → 15、45 → 54） */
    @GameTest(template = EMPTY)
    public static void baseStatsBladeAttackFormula(GameTestHelper helper) {
        AttunementFamily verdict = ECAttunement.VERDICT_BLADE;
        AttunementFamily ember = ECAttunement.EMBER_SWORD;
        double[] expected = {0D, 4D, 8D, 12D, 16.5D, 20.5D};
        for (int stage = 0; stage <= 5; stage++) {
            close(helper, AttunementBonuses.bladeAttackBonus(verdict, stage, 50D), expected[stage], "满余烬裁决之刃 " + stage + " 阶多出的攻击");
        }
        close(helper, AttunementBonuses.bladeAttackBonus(verdict, 5, 9D), 5D, "最低那把（显示 10）每阶保底 1 点");
        close(helper, AttunementBonuses.bladeAttackBonus(ember, 1, 44D), 4.5D, "满余烬余烬剑 1 阶 45 → 49.5");
        close(helper, AttunementBonuses.bladeAttackBonus(ember, 2, 44D), 9D, "满余烬余烬剑 2 阶 45 → 54");
        close(helper, AttunementBonuses.bladeAttackBonus(ember, 2, 24D), 5D, "典型余烬剑 2 阶 25 → 30");
        helper.succeed();
    }

    /** 属性栏攻击只有一条修饰符（本体那条 UUID），数额 = 原值 + 阶加成；刀剑的词条表里没有增伤、枪照旧有 */
    @GameTest(template = EMPTY)
    public static void baseStatsBladeSingleAttackModifier(GameTestHelper helper) {
        for (Item item : List.of(ECEmberForge.VERDICT_BLADE.get(), ECEmberForge.FORGED_ELEMENTAL_SWORD.get())) {
            ItemStack plain = sword(item, 0);
            double base = attack(plain).getAmount();
            AttunementFamily family = AttunementFamily.of(plain);
            for (int stage = 0; stage <= family.maxStage(); stage++) {
                ItemStack staged = sword(item, stage);
                Collection<AttributeModifier> attack = staged.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE);
                helper.assertTrue(attack.size() == 1, item + " " + stage + " 阶攻击修饰符应只有一条：" + attack);
                AttributeModifier only = attack.iterator().next();
                helper.assertTrue(only.getId().equals(ForgedElementalSwordItem.ATTACK_MODIFIER_ID) && only.getOperation() == AttributeModifier.Operation.ADDITION,
                        item + " 应并进本体那一条：" + only);
                close(helper, only.getAmount(), base + AttunementBonuses.bladeAttackBonus(family, stage, base), item + " " + stage + " 阶攻击");
                helper.assertTrue(GearStatSheet.get(staged, GearStat.DAMAGE) == 0D, item + " 刀剑词条表不该有增伤");
                helper.assertTrue(staged.getAttributeModifiers(EquipmentSlot.OFFHAND).get(Attributes.ATTACK_DAMAGE).isEmpty(), "副手不加");
            }
        }
        ItemStack gun = new ItemStack(ECGuns.PISTOL.get());
        Attunement.set(gun, 3, 0);
        close(helper, GearStatSheet.get(gun, GearStat.DAMAGE), 0.24D, "枪 3 阶增伤照旧在池里");
        helper.succeed();
    }

    /** 乘积闸：满阶 + 别的来源开很大，打怪 ≤ ×2、首领 ≤ ×1.5、玩家 ≤ ×1.3（都是对去掉属性那份以前的伤害）；没别的来源时打怪原样 */
    @GameTest(template = EMPTY)
    public static void baseStatsProductCap(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "bs-cap", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer victim = fighter(helper, "bs-cap-v", new Vec3(2.5D, 1D, 2.5D));
        Zombie zombie = dummy(helper, new BlockPos(2, 1, 1));
        String id = "test_bs_cap";
        try {
            ItemStack stack = sword(ECEmberForge.VERDICT_BLADE.get(), 5);
            wield(player, stack);
            double g = AttunementBonuses.bladeAttackFactor(stack);
            helper.assertTrue(g > 1.3D, "测试前提：满阶属性那份超过 ×1.3，实际 " + g);
            double attr = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            close(helper, attr, 1D + attack(stack).getAmount(), "玩家攻击属性吃到阶加成");
            close(helper, factor(player, zombie), 1D, "没有别的来源、没被夹：打怪照属性栏的数打");
            GearPool.registerOffense(id, context -> context.attacker() == player ? 5D : 0D);
            close(helper, factor(player, zombie) * g, 1D + GearGrowthConfig.poolMob(), "打怪乘积夹到 ×2");
            close(helper, factor(player, victim) * g, 1D + GearGrowthConfig.poolPlayer(), "打玩家乘积夹到 ×1.3");
            GearCaps.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            close(helper, factor(player, zombie) * g, 1D + GearGrowthConfig.poolBoss(), "打首领乘积夹到 ×1.5");
            // 断罪：伤害 = 攻击属性 × 倍数、伤害源是玩家近战，同一个闸
            close(helper, GearCombatEvents.factor(GearPool.context(player.damageSources().playerAttack(player), zombie, (float) (attr * 3D))) * g,
                    1D + GearGrowthConfig.poolBoss(), "断罪那一下同样被夹");
        } finally {
            GearPool.removeOffense(id);
            GearCaps.setTestBossOverride(null);
            GearGrowthConfig.clearTestOverrides();
            zombie.discard();
        }
        helper.succeed();
    }

    /** 打玩家：属性那份按 pvp_effect_scale 折（+40% 只算 +20%），没被夹时倍数 = (1 + (g − 1) × 0.5) ÷ g */
    @GameTest(template = EMPTY)
    public static void baseStatsPvpHalvesAttributeShare(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "bs-pvp", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer victim = fighter(helper, "bs-pvp-v", new Vec3(2.5D, 1D, 2.5D));
        try {
            ItemStack stack = sword(ECEmberForge.VERDICT_BLADE.get(), 2);
            wield(player, stack);
            double g = AttunementBonuses.bladeAttackFactor(stack);
            double scale = com.flameforge.essentialcraft5.item.attunement.AttunementConfig.pvpEffectScale();
            helper.assertTrue(g > 1D && 1D + (g - 1D) * scale < 1D + GearGrowthConfig.poolPlayer(), "测试前提：2 阶打玩家不被夹");
            close(helper, factor(player, victim), (1D + (g - 1D) * scale) / g, "打玩家属性那份折半");
            ItemStack offhandOnly = sword(ECEmberForge.VERDICT_BLADE.get(), 5);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getAttributes().removeAttributeModifiers(stack.getAttributeModifiers(EquipmentSlot.MAINHAND));
            player.setItemInHand(InteractionHand.OFF_HAND, offhandOnly);
            close(helper, factor(player, victim), 1D, "副手拿着不算亲手主手，不折也不夹");
        } finally {
            GearGrowthConfig.clearTestOverrides();
        }
        helper.succeed();
    }

    /**
     * 同一刻先切快捷栏再出手：攻击属性里还挂着满阶刀剑那条，主手已经换成木棍（不刷新属性）。
     * 总闸按身上挂着的那份折算，首领 ≤ ×1.5、玩家 ≤ ×1.3，和没切时一样。
     */
    @GameTest(template = EMPTY)
    public static void baseStatsSwapBeforeHitStillCapped(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "bs-swap", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer victim = fighter(helper, "bs-swap-v", new Vec3(2.5D, 1D, 2.5D));
        Zombie zombie = dummy(helper, new BlockPos(2, 1, 1));
        String id = "test_bs_swap";
        try {
            ItemStack stack = sword(ECEmberForge.VERDICT_BLADE.get(), 5);
            wield(player, stack);
            double g = AttunementBonuses.bladeAttackFactor(stack);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.STICK));
            close(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 1D + attack(stack).getAmount(), "测试前提：属性还是刀剑那份");
            close(helper, AttunementBonuses.appliedBladeFactor(player), g, "按身上挂着的修饰符算出同一个 g");
            close(helper, factor(player, zombie), 1D, "没有别的来源、没被夹：打怪照属性栏的数打");
            close(helper, factor(player, victim), (1D + (g - 1D) * com.flameforge.essentialcraft5.item.attunement.AttunementConfig.pvpEffectScale()) / g,
                    "打玩家属性那份照样折半");
            GearPool.registerOffense(id, context -> context.attacker() == player ? 5D : 0D);
            close(helper, factor(player, victim) * g, 1D + GearGrowthConfig.poolPlayer(), "打玩家乘积夹到 ×1.3");
            GearCaps.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            close(helper, factor(player, zombie) * g, 1D + GearGrowthConfig.poolBoss(), "打首领乘积夹到 ×1.5");
        } finally {
            GearPool.removeOffense(id);
            GearCaps.setTestBossOverride(null);
            GearGrowthConfig.clearTestOverrides();
            zombie.discard();
        }
        helper.succeed();
    }

    /** 配置热改后重挂：身上的攻击换成新数，按身上算的 g 和按物品算的一致 */
    @GameTest(template = EMPTY)
    public static void baseStatsReapplyAfterConfigChange(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "bs-reapply", new Vec3(0.5D, 1D, 0.5D));
        try {
            ItemStack stack = sword(ECEmberForge.VERDICT_BLADE.get(), 5);
            wield(player, stack);
            double before = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            GearGrowthConfig.setTestOverride("verdict_blade.attack_per_stage", 0.2D);
            close(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), before, "没重挂时还是旧数");
            AttunementBonuses.reapply(player);
            close(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 1D + attack(stack).getAmount(), "重挂后是新数");
            helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) > before, "比例调大后攻击变高");
            close(helper, AttunementBonuses.appliedBladeFactor(player), AttunementBonuses.bladeAttackFactor(stack), "按身上算的 g 和按物品算的一致");
        } finally {
            GearGrowthConfig.clearTestOverrides();
        }
        helper.succeed();
    }

    /** 配置热改：比例改了下一次取属性就是新数，还是一条 */
    @GameTest(template = EMPTY)
    public static void baseStatsBladeConfigHotChange(GameTestHelper helper) {
        try {
            ItemStack stack = sword(ECEmberForge.VERDICT_BLADE.get(), 5);
            double base = AttunementBonuses.bladeBaseAttack(stack);
            GearGrowthConfig.setTestOverride("verdict_blade.attack_per_stage", 0D);
            close(helper, attack(stack).getAmount(), base, "比例 0：只剩原值");
            helper.assertTrue(AttunementBonuses.bladeAttackFactor(stack) == 1D, "比例 0：g = 1");
            GearGrowthConfig.setTestOverride("verdict_blade.attack_per_stage", 0.2D);
            close(helper, attack(stack).getAmount(), base + Math.round(Math.max(1D, (1D + base) * 0.2D) * 5D * 2D) / 2D, "比例 0.2");
            helper.assertTrue(stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).size() == 1, "热改后仍是一条");
        } finally {
            GearGrowthConfig.clearTestOverrides();
        }
        helper.succeed();
    }

    // ================================================================ 盔甲

    private record Piece(Item item, EquipmentSlot slot) {
    }

    private static List<List<Piece>> sets() {
        return List.of(
                set(ECGear.MHS_HELMET.get(), ECGear.MHS_CHESTPLATE.get(), ECGear.MHS_LEGGINGS.get(), ECGear.MHS_BOOTS.get()),
                set(ECWindGear.WIND_HELMET.get(), ECWindGear.WIND_CHESTPLATE.get(), ECWindGear.WIND_LEGGINGS.get(), ECWindGear.WIND_BOOTS.get()),
                set(ECVoidArmor.VOID_HELMET.get(), ECVoidArmor.VOID_CHESTPLATE.get(), ECVoidArmor.VOID_LEGGINGS.get(), ECVoidArmor.VOID_BOOTS.get()),
                set(ECInquisitorium.INQUISITORIUM_HELMET.get(), ECInquisitorium.INQUISITORIUM_CHESTPLATE.get(), ECInquisitorium.INQUISITORIUM_LEGGINGS.get(),
                        ECInquisitorium.INQUISITORIUM_BOOTS.get()),
                set(ECComputer.COMPUTER_HELMET.get(), ECComputer.COMPUTER_CHESTPLATE.get(), ECComputer.COMPUTER_LEGGINGS.get(), ECComputer.COMPUTER_BOOTS.get()));
    }

    private static List<Piece> set(Item head, Item chest, Item legs, Item feet) {
        return List.of(new Piece(head, EquipmentSlot.HEAD), new Piece(chest, EquipmentSlot.CHEST), new Piece(legs, EquipmentSlot.LEGS), new Piece(feet, EquipmentSlot.FEET));
    }

    private static final List<Attribute> ARMOR_ATTRIBUTES = List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE, Attributes.MAX_HEALTH);

    /**
     * 五套 × 四件 × 各阶：每个属性最多一条加法修饰符；比 0 阶多出的 = 阶表；虚空 / 审判庭 / 宅男护甲、韧性不随阶变，生命每阶 3、满阶击退 +0.025；
     * 魔力防护 / 风之满阶护甲合计 28 / 30（≤ 30）、韧性 16 / 20（≤ 20）
     */
    @GameTest(template = EMPTY)
    public static void baseStatsArmorTables(GameTestHelper helper) {
        double[][] fullArmorTough = {{28D, 16D}, {30D, 20D}};
        for (List<Piece> set : sets()) {
            AttunementFamily family = AttunementFamily.of(new ItemStack(set.get(0).item()));
            String what = family.id().getPath();
            int max = family.maxStage();
            double armorTotal = 0D;
            double toughTotal = 0D;
            double knockTotal = 0D;
            for (Piece piece : set) {
                ItemStack plain = new ItemStack(piece.item());
                for (int stage = 0; stage <= max; stage++) {
                    ItemStack staged = new ItemStack(piece.item());
                    Attunement.set(staged, stage, 0);
                    for (Attribute attribute : ARMOR_ATTRIBUTES) {
                        long count = staged.getAttributeModifiers(piece.slot()).get(attribute).stream()
                                .filter(m -> m.getOperation() == AttributeModifier.Operation.ADDITION).count();
                        helper.assertTrue(count <= 1, what + " " + piece.slot() + " " + stage + " 阶 " + attribute.getDescriptionId() + " 有 " + count + " 条");
                    }
                    double armor = sum(staged, piece.slot(), Attributes.ARMOR) - sum(plain, piece.slot(), Attributes.ARMOR);
                    double tough = sum(staged, piece.slot(), Attributes.ARMOR_TOUGHNESS) - sum(plain, piece.slot(), Attributes.ARMOR_TOUGHNESS);
                    double knock = sum(staged, piece.slot(), Attributes.KNOCKBACK_RESISTANCE) - sum(plain, piece.slot(), Attributes.KNOCKBACK_RESISTANCE);
                    double health = sum(staged, piece.slot(), Attributes.MAX_HEALTH) - sum(plain, piece.slot(), Attributes.MAX_HEALTH);
                    close(helper, armor, ArmorBaseStats.armor(family, piece.slot(), stage), what + " " + piece.slot() + " " + stage + " 阶护甲");
                    close(helper, tough, ArmorBaseStats.toughness(family, stage), what + " " + stage + " 阶韧性");
                    close(helper, knock, ArmorBaseStats.knockback(family, stage), what + " " + stage + " 阶击退");
                    close(helper, health, ArmorBaseStats.healthPerStage(family) * stage, what + " " + stage + " 阶生命");
                    boolean capped = family != ECAttunement.MHS_ARMOR && family != ECAttunement.WIND_ARMOR;
                    if (capped) {
                        helper.assertTrue(armor == 0D && tough == 0D, what + " 护甲韧性不随阶变");
                        close(helper, ArmorBaseStats.healthPerStage(family), 3D, what + " 每阶每件生命 3");
                        close(helper, knock, stage == max ? 0.025D : 0D, what + " 只有满阶加击退");
                    }
                    if (stage == max) {
                        armorTotal += sum(staged, piece.slot(), Attributes.ARMOR);
                        toughTotal += sum(staged, piece.slot(), Attributes.ARMOR_TOUGHNESS);
                        knockTotal += sum(staged, piece.slot(), Attributes.KNOCKBACK_RESISTANCE);
                    }
                }
            }
            if (family == ECAttunement.MHS_ARMOR || family == ECAttunement.WIND_ARMOR) {
                double[] full = fullArmorTough[family == ECAttunement.MHS_ARMOR ? 0 : 1];
                close(helper, armorTotal, full[0], what + " 满阶护甲合计");
                close(helper, toughTotal, full[1], what + " 满阶韧性合计");
                helper.assertTrue(armorTotal <= 30D && toughTotal <= 20D, what + " 不超过原版上限");
            } else {
                close(helper, knockTotal, 0.9D, what + " 满阶击退合计 0.9（不补满）");
            }
        }
        helper.succeed();
    }

    /**
     * 穿上胸甲、原地升阶、脱下、刀剑换手：照原版换装的做法（去掉旧物品的修饰符、加上新物品的），属性回到原样，不留残余。
     * 靠的是同一种物品各阶用同一个 UUID。
     */
    @GameTest(template = EMPTY)
    public static void baseStatsNoResidueAfterSwap(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "bs-swap", new Vec3(0.5D, 1D, 0.5D));
        double armor0 = player.getAttributeValue(Attributes.ARMOR);
        double tough0 = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double health0 = player.getAttributeValue(Attributes.MAX_HEALTH);
        double attack0 = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack chest = new ItemStack(ECGear.MHS_CHESTPLATE.get());
        Attunement.set(chest, 1, 0);
        swap(player, EquipmentSlot.CHEST, ItemStack.EMPTY, chest);
        close(helper, player.getAttributeValue(Attributes.ARMOR) - armor0, 9D, "魔力防护 1 阶胸甲护甲 9");
        ItemStack upgraded = chest.copy();
        Attunement.set(upgraded, 2, 0);
        swap(player, EquipmentSlot.CHEST, chest, upgraded);
        close(helper, player.getAttributeValue(Attributes.ARMOR) - armor0, 10D, "原地升到 2 阶护甲 10");
        close(helper, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - tough0, 4D, "2 阶韧性 4");
        swap(player, EquipmentSlot.CHEST, upgraded, ItemStack.EMPTY);
        close(helper, player.getAttributeValue(Attributes.ARMOR), armor0, "脱下后护甲回原样");
        close(helper, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), tough0, "脱下后韧性回原样");
        close(helper, player.getAttributeValue(Attributes.MAX_HEALTH), health0, "脱下后生命回原样");
        ItemStack voidChest = new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get());
        Attunement.set(voidChest, 4, 0);
        swap(player, EquipmentSlot.CHEST, ItemStack.EMPTY, voidChest);
        close(helper, player.getAttributeValue(Attributes.MAX_HEALTH) - health0, sum(voidChest, EquipmentSlot.CHEST, Attributes.MAX_HEALTH), "虚空 4 阶胸甲生命");
        close(helper, sum(voidChest, EquipmentSlot.CHEST, Attributes.MAX_HEALTH), 32D, "虚空 4 阶胸甲 +32 生命（20 + 3 × 4）");
        swap(player, EquipmentSlot.CHEST, voidChest, ItemStack.EMPTY);
        close(helper, player.getAttributeValue(Attributes.MAX_HEALTH), health0, "脱下虚空胸甲生命回原样");
        close(helper, player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0D, "击退抗性回原样");
        ItemStack blade = sword(ECEmberForge.VERDICT_BLADE.get(), 5);
        swap(player, EquipmentSlot.MAINHAND, ItemStack.EMPTY, blade);
        close(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 1D + attack(blade).getAmount(), "主手拿着吃到阶加成");
        ItemStack lower = blade.copy();
        Attunement.set(lower, 2, 0);
        swap(player, EquipmentSlot.MAINHAND, blade, lower);
        close(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 1D + attack(lower).getAmount(), "换成 2 阶那把");
        swap(player, EquipmentSlot.MAINHAND, lower, ItemStack.EMPTY);
        swap(player, EquipmentSlot.OFFHAND, ItemStack.EMPTY, lower);
        close(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), attack0, "换到副手后攻击回原样");
        helper.succeed();
    }

    /** 原版 LivingEntity 换装时的做法：去掉旧物品在这个槽位的修饰符，加上新物品的 */
    private static void swap(ServerPlayer player, EquipmentSlot slot, ItemStack from, ItemStack to) {
        if (!from.isEmpty()) {
            player.getAttributes().removeAttributeModifiers(from.getAttributeModifiers(slot));
        }
        player.setItemSlot(slot, to);
        if (!to.isEmpty()) {
            player.getAttributes().addTransientAttributeModifiers(to.getAttributeModifiers(slot));
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static ServerPlayer fighter(GameTestHelper helper, String name, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void wield(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(EquipmentSlot.MAINHAND));
    }

    private static ItemStack sword(Item item, int stage) {
        ItemStack stack = MRUStaffItem.full(item);
        List<ResourceLocation> embers = List.of(EmberKind.COMMON.id(), EmberKind.COMMON.id(), EmberKind.COMMON.id(), EmberKind.COMMON.id());
        List<ResourceLocation> foci = List.of(Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus());
        new ForgedSwordData(embers, foci, Element.FIRE, Element.WATER).writeTo(stack);
        Attunement.set(stack, stage, 0);
        return stack;
    }

    private static AttributeModifier attack(ItemStack stack) {
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).iterator().next();
    }

    private static double sum(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        return stack.getAttributeModifiers(slot).get(attribute).stream()
                .filter(m -> m.getOperation() == AttributeModifier.Operation.ADDITION).mapToDouble(AttributeModifier::getAmount).sum();
    }

    private static Zombie dummy(GameTestHelper helper, BlockPos relative) {
        Zombie mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, relative);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        mob.setHealth(1000F);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(0D);
        return mob;
    }

    private static double factor(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target) {
        return GearCombatEvents.factor(GearPool.context(attacker.damageSources().playerAttack(attacker), target, 10F));
    }

    private static void close(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EPS, what + "：应为 " + expected + "，实际 " + actual);
    }
}
