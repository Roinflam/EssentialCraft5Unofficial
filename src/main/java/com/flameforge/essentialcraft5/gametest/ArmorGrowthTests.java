package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.ComputerAttunement;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorEvents;
import com.flameforge.essentialcraft5.item.MHSArmorItem;
import com.flameforge.essentialcraft5.item.VoidArmorEvents;
import com.flameforge.essentialcraft5.item.VoidArmorItem;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShieldAbsorbedEvent;
import com.flameforge.essentialcraft5.item.attunement.ArmorAwakenings;
import com.flameforge.essentialcraft5.item.attunement.ArmorGrowth;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageLines;
import com.flameforge.essentialcraft5.item.attunement.BattleScars;
import com.flameforge.essentialcraft5.item.attunement.GearAoe;
import com.flameforge.essentialcraft5.item.attunement.GearCaps;
import com.flameforge.essentialcraft5.item.attunement.GearCombatEvents;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.attunement.GearPool;
import com.flameforge.essentialcraft5.item.attunement.GearStat;
import com.flameforge.essentialcraft5.item.attunement.GearStatSheet;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 武器防具成长 G2（盔甲）：五套的阶表（{@link ArmorGrowth}）、全套增伤 / 战痕减伤进总闸、审判庭按阶的抗魔 / 首领减伤 / 律令 / 吸血、
 * 虚空闪避表与 PvP 修正、宅男冷却缩减，以及五个满阶觉醒（{@link ArmorAwakenings}：回涌、风暴步、残像、宣判、超载脉冲）。
 * <p>
 * 觉醒打人都排到本刻末：这里在同一段同步代码里触发、{@link ArmorAwakenings#flushAllForTest} 结算、断言，不留待办给下一刻
 * （同一批的别的测试也会 flush）。配置覆盖值、首领覆盖、事件包收集都在 finally 里置回；状态只清点名的玩家 / 目标。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ArmorGrowthTests {

    private static final String EMPTY = "empty";
    private static final double EPS = 1e-4D;

    private static final List<Supplier<? extends Item>> MHS = List.of(ECGear.MHS_HELMET, ECGear.MHS_CHESTPLATE, ECGear.MHS_LEGGINGS, ECGear.MHS_BOOTS);
    private static final List<Supplier<? extends Item>> WIND = List.of(ECWindGear.WIND_HELMET, ECWindGear.WIND_CHESTPLATE,
            ECWindGear.WIND_LEGGINGS, ECWindGear.WIND_BOOTS);
    private static final List<Supplier<? extends Item>> VOID = List.copyOf(ECVoidArmor.all());
    private static final List<Supplier<? extends Item>> INQ = List.copyOf(ECInquisitorium.all());
    private static final List<Supplier<? extends Item>> COMPUTER = List.copyOf(ECComputer.armor());

    private ArmorGrowthTests() {
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 真玩家（ownHit 认它），不在世界的实体表里，所以范围技不会打到它自己以外的玩家 */
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

    private static Player mock(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static ItemStack piece(Supplier<? extends Item> item, int stage) {
        ItemStack stack = new ItemStack(item.get());
        if (stage > 0) {
            Attunement.set(stack, stage, 0);
        }
        return stack;
    }

    /** 穿四件 stage 阶（按头 / 胸 / 腿 / 靴放进槽，不加属性） */
    private static ItemStack[] suit(LivingEntity entity, List<Supplier<? extends Item>> set, int stage) {
        ItemStack[] stacks = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            stacks[i] = piece(set.get(i), stage);
            entity.setItemSlot(Attunement.ARMOR_SLOTS[i], stacks[i]);
        }
        return stacks;
    }

    private static <T extends Mob> T dummy(GameTestHelper helper, EntityType<T> type, BlockPos relative, double maxHealth) {
        T mob = helper.spawnWithNoFreeWill(type, relative);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        mob.setHealth((float) maxHealth);
        if (mob.getAttribute(Attributes.ARMOR) != null) {
            mob.getAttribute(Attributes.ARMOR).setBaseValue(0D);
        }
        return mob;
    }

    private static DamageSource source(GameTestHelper helper, ResourceKey<DamageType> type, Entity attacker) {
        return new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
    }

    private static void hit(LivingEntity target, DamageSource source, float amount) {
        target.invulnerableTime = 0;
        target.hurt(source, amount);
    }

    private static double factor(DamageSource source, LivingEntity target, float amount) {
        return GearCombatEvents.factor(GearPool.context(source, target, amount));
    }

    private static double offensePart(DamageSource source, LivingEntity target, String id) {
        return GearPool.offense(GearPool.context(source, target, 10F)).part(id);
    }

    private static double defensePart(DamageSource source, LivingEntity target, String id) {
        return GearPool.defense(GearPool.context(source, target, 10F)).part(id);
    }

    private static double stat(EnumMap<GearStat, Double> sheet, GearStat stat) {
        Double value = sheet.get(stat);
        return value == null ? 0D : value;
    }

    private static double sheet(List<Supplier<? extends Item>> set, int stage, GearStat stat) {
        return stat(GearStatSheet.of(piece(set.get(1), stage)), stat);
    }

    private static void close(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EPS, what + "：应为 " + expected + "，实际 " + actual);
    }

    /** 这件在 slot 上的某个属性修饰符（按名字找；没有是 null） */
    private static AttributeModifier modifier(ItemStack stack, EquipmentSlot slot, Attribute attribute, String name) {
        Collection<AttributeModifier> list = stack.getAttributeModifiers(slot).get(attribute);
        return list.stream().filter(m -> m.getName().equals(name)).findFirst().orElse(null);
    }

    private static boolean slowed(LivingEntity entity, int amplifier) {
        MobEffectInstance effect = entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return effect != null && effect.getAmplifier() == amplifier;
    }

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家没有玩家数据");
        }
        return data;
    }

    private static void reset() {
        GearCaps.setTestBossOverride(null);
        GearGrowthConfig.clearTestOverrides();
        S2CGearFxPacket.setTestSink(null);
        GearCombatEvents.flush();
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null) {
                if (entity instanceof LivingEntity living) {
                    ArmorAwakenings.forgetForTest(living);
                }
                entity.discard();
            }
        }
    }

    // ================================================================ 阶表

    /** 五套的词条表照方案稿推荐档：各取有代表性的阶；比阶数短的列表沿用最后一项 */
    @GameTest(template = EMPTY)
    public static void armorStageTables(GameTestHelper helper) {
        helper.assertTrue(GearStatSheet.of(piece(MHS.get(1), 0)).isEmpty(), "魔力防护 0 阶没有词条");
        close(helper, sheet(MHS, 1, GearStat.MRU_DISCOUNT), 0.1D, "魔力防护 1 阶护盾单价 −10%");
        close(helper, sheet(MHS, 2, GearStat.MOVE_SPEED), 0.05D, "魔力防护 2 阶沿用移速 +5%");

        close(helper, sheet(WIND, 1, GearStat.ATTACK_SPEED), 0.06D, "风之 1 阶攻速 +6%");
        close(helper, sheet(WIND, 1, GearStat.DAMAGE), 0D, "风之 1 阶还没有增伤");
        close(helper, sheet(WIND, 2, GearStat.DAMAGE), 0.1D, "风之 2 阶增伤 +10%");
        close(helper, sheet(WIND, 3, GearStat.DAMAGE), 0.1D, "风之 3 阶沿用");

        double[] dodge = {0.2D, 0.25D, 0.27D, 0.3D, 0.35D};
        for (int stage = 0; stage <= 4; stage++) {
            close(helper, sheet(VOID, stage, GearStat.DODGE), dodge[stage], "虚空 " + stage + " 阶闪避");
            close(helper, VoidArmorEvents.dodgeChance(stage), dodge[stage], "虚空 " + stage + " 阶 dodgeChance");
        }
        close(helper, sheet(VOID, 1, GearStat.MRU_DISCOUNT), 0.05D, "虚空 1 阶护盾单价 −5%");
        close(helper, sheet(VOID, 2, GearStat.DAMAGE), 0.08D, "虚空 2 阶增伤 +8%");

        double[] magic = {0.35D, 0.45D, 0.45D, 0.5D, 0.55D};
        double[] boss = {0.15D, 0.15D, 0.18D, 0.21D, 0.25D};
        for (int stage = 0; stage <= 4; stage++) {
            close(helper, ArmorGrowth.inqMagicResistance(stage), magic[stage], "审判庭 " + stage + " 阶抗魔");
            close(helper, ArmorGrowth.inqBossResistance(stage), boss[stage], "审判庭 " + stage + " 阶首领减伤");
        }
        close(helper, ArmorGrowth.inqEdict(0), 0.4D, "律令 0 阶");
        close(helper, ArmorGrowth.inqEdict(4), 0.5D, "律令 1 阶起 0.5");
        close(helper, sheet(INQ, 1, GearStat.LIFESTEAL), 0D, "审判庭 1 阶没有吸血");
        close(helper, sheet(INQ, 2, GearStat.LIFESTEAL), 0.02D, "审判庭 2 阶吸血 2%");

        close(helper, sheet(COMPUTER, 1, GearStat.DAMAGE), 0.04D, "宅男 1 阶增伤 +4%");
        close(helper, sheet(COMPUTER, 5, GearStat.DAMAGE), 0.08D, "宅男 2 阶起增伤 +8%");
        close(helper, sheet(COMPUTER, 3, GearStat.COOLDOWN), 0D, "宅男 3 阶还没有冷却缩减");
        close(helper, sheet(COMPUTER, 4, GearStat.COOLDOWN), 0.15D, "宅男 4 阶冷却缩减 15%");
        close(helper, ArmorGrowth.computerRepairPercent(), 0.015D, "矩阵修复每秒 1.5%");
        helper.assertTrue(ArmorGrowth.computerOverloadCooldown(3) == 900, "3 阶过载冷却 900，实际 " + ArmorGrowth.computerOverloadCooldown(3));
        helper.assertTrue(ArmorGrowth.computerOverloadCooldown(5) == 765, "5 阶 900 × 0.85 = 765，实际 " + ArmorGrowth.computerOverloadCooldown(5));
        try {
            GearGrowthConfig.setTestOverride("computer_armor.cooldown_reduction", List.of(0.9D));
            helper.assertTrue(ArmorGrowth.computerOverloadCooldown(5) == 540, "冷却缩减过 caps.cooldown_reduction 40%：900 × 0.6 = 540，实际 "
                    + ArmorGrowth.computerOverloadCooldown(5));
            GearGrowthConfig.setTestOverride("inquisitorium_armor.edict_reduction", List.of(0.99D));
            close(helper, ArmorGrowth.inqEdict(0), 0.9D, "律令最多 0.9");
        } finally {
            reset();
        }
        helper.succeed();
    }

    /** 单价折扣接进护盾：魔力防护 1 阶 ×0.9；虚空 1 阶 ×0.95、3 阶 7/8 × 0.95 */
    @GameTest(template = EMPTY)
    public static void armorShieldDiscounts(GameTestHelper helper) {
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 20D);
        try {
            DamageSource mob = zombie.damageSources().mobAttack(zombie);
            MHSArmorItem mhs = (MHSArmorItem) ECGear.MHS_CHESTPLATE.get();
            IMRUShieldArmor.Coverage mhs0 = mhs.coverage(piece(ECGear.MHS_CHESTPLATE, 0), mob);
            IMRUShieldArmor.Coverage mhs1 = mhs.coverage(piece(ECGear.MHS_CHESTPLATE, 1), mob);
            helper.assertTrue(mhs0 != null && mhs1 != null, "魔力防护挡物理");
            close(helper, mhs0.priceMultiplier(), 1D, "魔力防护 0 阶原价");
            close(helper, mhs1.priceMultiplier(), 0.9D, "魔力防护 1 阶 −10%");
            close(helper, VoidArmorItem.priceMultiplier(0), 1D, "虚空 0 阶原价");
            close(helper, VoidArmorItem.priceMultiplier(1), 0.95D, "虚空 1 阶 −5%");
            close(helper, VoidArmorItem.priceMultiplier(3), VoidArmorItem.DISCOUNT * 0.95D, "虚空 3 阶 7/8 再 −5%");
        } finally {
            discard(zombie);
        }
        helper.succeed();
    }

    /** 魔力防护的移速、风之的攻速每件给四分之一（MULTIPLY_BASE，四件合计是表里的数）；0 阶没有 */
    @GameTest(template = EMPTY)
    public static void armorPieceSpeedModifiers(GameTestHelper helper) {
        String move = "Attunement movement speed";
        String attack = "Attunement attack speed";
        for (int i = 0; i < 4; i++) {
            EquipmentSlot slot = Attunement.ARMOR_SLOTS[i];
            AttributeModifier m = modifier(piece(MHS.get(i), 1), slot, Attributes.MOVEMENT_SPEED, move);
            helper.assertTrue(m != null && m.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE && Math.abs(m.getAmount() - 0.0125D) < EPS,
                    "魔力防护 1 阶 " + slot + " 移速 +1.25%：" + m);
            AttributeModifier a = modifier(piece(WIND.get(i), 1), slot, Attributes.ATTACK_SPEED, attack);
            helper.assertTrue(a != null && a.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE && Math.abs(a.getAmount() - 0.015D) < EPS,
                    "风之 1 阶 " + slot + " 攻速 +1.5%：" + a);
            helper.assertTrue(modifier(piece(MHS.get(i), 0), slot, Attributes.MOVEMENT_SPEED, move) == null, "魔力防护 0 阶没有移速修饰符");
            helper.assertTrue(modifier(piece(WIND.get(i), 0), slot, Attributes.ATTACK_SPEED, attack) == null, "风之 0 阶没有攻速修饰符");
        }
        close(helper, ArmorGrowth.pieceSpeed(new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE), GearStat.MOVE_SPEED), 0D, "原版盔甲 0");
        helper.succeed();
    }

    /** 同调提示框的阶效果行（魔力防护、虚空、审判庭改成按配置现填）：默认的数和结算用的一致，改了配置跟着变 */
    @GameTest(template = EMPTY)
    public static void armorStageLinesFollowConfig(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ItemStack mhs = piece(ECGear.MHS_CHESTPLATE, 0);
        AttunementStageLines mhsLines = (AttunementStageLines) mhs.getItem();
        lineArgs(problems, mhsLines, mhs, "mhs_armor", 1, Map.of(0, "35", 2, "10"));
        lineArgs(problems, mhsLines, mhs, "mhs_armor", 2, Map.of(0, "45"));

        ItemStack voidChest = piece(ECVoidArmor.VOID_CHESTPLATE, 0);
        AttunementStageLines voidLines = (AttunementStageLines) voidChest.getItem();
        lineArgs(problems, voidLines, voidChest, "void_armor", 1, Map.of(0, "25", 1, "5"));
        lineArgs(problems, voidLines, voidChest, "void_armor", 2, Map.of(0, "27"));
        lineArgs(problems, voidLines, voidChest, "void_armor", 3, Map.of(0, "30", 1, "333", 2, "40", 3, "3"));
        lineArgs(problems, voidLines, voidChest, "void_armor", 4, Map.of(0, "35"));

        ItemStack inq = piece(INQ.get(1), 0);
        AttunementStageLines inqLines = (AttunementStageLines) inq.getItem();
        lineArgs(problems, inqLines, inq, "inquisitorium_armor", 1, Map.of(0, "45", 1, "50"));
        lineArgs(problems, inqLines, inq, "inquisitorium_armor", 2, Map.of(2, "45", 3, "18"));
        lineArgs(problems, inqLines, inq, "inquisitorium_armor", 3, Map.of(2, "50", 3, "21"));
        lineArgs(problems, inqLines, inq, "inquisitorium_armor", 4, Map.of(3, "55", 4, "25"));
        try {
            GearGrowthConfig.setTestOverride("void_armor.dodge", List.of(0.1D));
            GearGrowthConfig.setTestOverride("inquisitorium_armor.magic_resistance", List.of(0.12D));
            GearGrowthConfig.setTestOverride("mhs_armor.shield_discount", List.of(0D, 0.2D));
            lineArgs(problems, voidLines, voidChest, "void_armor", 2, Map.of(0, "10"));
            lineArgs(problems, inqLines, inq, "inquisitorium_armor", 4, Map.of(3, "12"));
            lineArgs(problems, mhsLines, mhs, "mhs_armor", 1, Map.of(2, "20"));
        } finally {
            reset();
        }
        helper.assertTrue(problems.isEmpty(), "阶效果行：" + String.join("；", problems));
        helper.succeed();
    }

    private static void lineArgs(List<String> problems, AttunementStageLines lines, ItemStack stack, String family, int stage, Map<Integer, String> want) {
        Component line = lines.attunementStageLine(stack, stage);
        String key = "tooltip.essentialcraft5.attunement." + family + ".stage_" + stage;
        if (line == null || !(line.getContents() instanceof TranslatableContents contents) || !key.equals(contents.getKey())) {
            problems.add(family + " " + stage + " 阶应是 " + key + "，实际 " + line);
            return;
        }
        Object[] args = contents.getArgs();
        want.forEach((index, value) -> {
            String actual = index < args.length ? String.valueOf(args[index]) : "（没有）";
            if (!value.equals(actual)) {
                problems.add(family + " " + stage + " 阶第 " + index + " 个参数应是 " + value + "，实际 " + actual);
            }
        });
    }

    // ================================================================ 全套增伤、战痕减伤、总闸

    /** armor_set：风之全套 2 阶亲手打 +10%，打玩家 × pvp_effect_scale；范围技、没穿齐不算 */
    @GameTest(template = EMPTY)
    public static void armorSetOffenseSource(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-aset", new Vec3(0.5D, 1D, 0.5D));
        Player other = mock(helper, 2.5D, 2.5D);
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 100D);
        try {
            suit(player, WIND, 2);
            DamageSource melee = player.damageSources().playerAttack(player);
            close(helper, offensePart(melee, zombie, "armor_set"), 0.1D, "亲手打怪 +10%");
            close(helper, offensePart(melee, other, "armor_set"), 0.05D, "打玩家 ×0.5");
            close(helper, offensePart(GearAoe.source(player), zombie, "armor_set"), 0D, "范围技不算");
            player.setItemSlot(EquipmentSlot.FEET, piece(VOID.get(3), 2));
            close(helper, offensePart(melee, zombie, "armor_set"), 0D, "混穿没有全套词条");
            suit(player, COMPUTER, 5);
            close(helper, offensePart(melee, zombie, "armor_set"), 0.08D, "宅男全套 5 阶 +8%");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    /** 全套战痕取四件里最低的等级 × 1%；混穿、没满阶（没有战痕）是 0 */
    @GameTest(template = EMPTY)
    public static void armorScarLowestPiece(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = suit(victim, INQ, 4);
        int[] levels = {3, 5, 2, 7};
        for (int i = 0; i < 4; i++) {
            Attunement.addPoints(stacks[i], levels[i] * GearGrowthConfig.scarPointsPerLevel());
            helper.assertTrue(BattleScars.level(stacks[i]) == levels[i], "测试前提：第 " + i + " 件战痕 " + levels[i] + " 级，实际 " + BattleScars.level(stacks[i]));
        }
        helper.assertTrue(ArmorGrowth.scarLevel(victim) == 2, "四件最低 2 级，实际 " + ArmorGrowth.scarLevel(victim));
        close(helper, ArmorGrowth.scarReduction(victim), 0.02D, "2 级 × 1%");
        victim.setItemSlot(EquipmentSlot.FEET, piece(VOID.get(3), 4));
        helper.assertTrue(ArmorGrowth.scarLevel(victim) == 0, "混穿 0");
        close(helper, ArmorGrowth.scarReduction(victim), 0D, "混穿不减");
        suit(victim, INQ, 3);
        helper.assertTrue(ArmorGrowth.scarLevel(victim) == 0, "没满阶没有战痕");
        helper.succeed();
    }

    /**
     * 减伤池：审判庭 4 阶抗魔 55% + 全套战痕 10 级 10% = 65%，夹到 60%（×0.4）；攻击者是玩家夹到 40%（×0.6）；
     * 首领近战 4 阶 25% + 10%；bypasses_invulnerability 两项都不减
     */
    @GameTest(template = EMPTY)
    public static void armorReductionPoolCapped(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Player other = mock(helper, 3.5D, 1.5D);
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(3, 1, 3), 20D);
        WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
        helper.assertTrue(wither != null, "测试前提：造得出凋灵");
        try {
            ItemStack[] stacks = suit(victim, INQ, 4);
            for (ItemStack stack : stacks) {
                Attunement.addPoints(stack, 10 * GearGrowthConfig.scarPointsPerLevel());
            }
            DamageSource spell = source(helper, DamageTypes.INDIRECT_MAGIC, zombie);
            close(helper, defensePart(spell, victim, "inquisitorium"), 0.55D, "抗魔 55%");
            close(helper, defensePart(spell, victim, "armor_scar"), 0.1D, "战痕 10%");
            close(helper, factor(spell, victim, 10F), 0.4D, "65% 夹 60%");
            close(helper, factor(source(helper, DamageTypes.INDIRECT_MAGIC, other), victim, 10F), 0.6D, "攻击者是玩家夹 40%");
            DamageSource bossMelee = victim.damageSources().mobAttack(wither);
            close(helper, defensePart(bossMelee, victim, "inquisitorium"), 0.25D, "4 阶首领减伤 25%");
            close(helper, factor(bossMelee, victim, 10F), 0.65D, "首领近战 25% + 10%");
            DamageSource kill = source(helper, DamageTypes.GENERIC_KILL, zombie);
            close(helper, defensePart(kill, victim, "armor_scar") + defensePart(kill, victim, "inquisitorium"), 0D, "无视无敌的不减");
            close(helper, defensePart(victim.damageSources().fall(), victim, "armor_scar"), 0D, "没有攻击者的（摔落）战痕不减");
            suit(victim, INQ, 2);
            close(helper, defensePart(bossMelee, victim, "inquisitorium"), 0.18D, "2 阶首领减伤 18%");
            close(helper, defensePart(spell, victim, "inquisitorium"), 0.45D, "2 阶抗魔 45%");
            GearGrowthConfig.setTestOverride("inquisitorium_armor.magic_resistance", List.of(0D));
            close(helper, defensePart(spell, victim, "inquisitorium"), 0D, "抗魔列表填 0 不减");
        } finally {
            discard(zombie, wither);
            reset();
        }
        helper.succeed();
    }

    /** 律令按阶：0 阶削 13 刻、1 阶起削 20 刻（×0.5） */
    @GameTest(template = EMPTY)
    public static void armorEdictByStage(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        suit(player, INQ, 0);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        helper.assertTrue(InquisitoriumArmorEvents.edict(player) == 1 && player.getEffect(MobEffects.POISON).getDuration() == 187, "0 阶 200 → 187");
        suit(player, INQ, 1);
        helper.assertTrue(InquisitoriumArmorEvents.edict(player) == 1 && player.getEffect(MobEffects.POISON).getDuration() == 167,
                "1 阶 187 → 167，实际 " + player.getEffect(MobEffects.POISON).getDuration());
        try {
            GearGrowthConfig.setTestOverride("inquisitorium_armor.edict_reduction", List.of(0D));
            helper.assertTrue(InquisitoriumArmorEvents.edict(player) == 0, "填 0 不削");
        } finally {
            reset();
        }
        helper.succeed();
    }

    /** 审判庭 2 阶吸血：只对自己定罪的目标、只认亲手打；真打一下回 dealt × 2% */
    @GameTest(template = EMPTY)
    public static void armorInquisitoriumLifesteal(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-inq-ls", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 500D);
        try {
            suit(player, INQ, 2);
            DamageSource melee = player.damageSources().playerAttack(player);
            close(helper, ArmorGrowth.lifestealRate(player, zombie, melee), 0D, "没定罪 0");
            InquisitoriumArmorEvents.convict(player, zombie);
            close(helper, ArmorGrowth.lifestealRate(player, zombie, melee), 0.02D, "定罪后 2%");
            close(helper, ArmorGrowth.lifestealRate(player, zombie, GearAoe.source(player)), 0D, "范围技不吸");
            GearCombatEvents.resetBucket(player);
            player.setHealth(5F);
            float before = zombie.getHealth();
            hit(zombie, melee, 10F);
            float dealt = before - zombie.getHealth();
            helper.assertTrue(dealt > 0F, "测试前提：打实了");
            float healed = GearCombatEvents.flush();
            close(helper, healed, dealt * 0.02D, "回 dealt × 2%");
            suit(player, INQ, 1);
            close(helper, ArmorGrowth.lifestealRate(player, zombie, melee), 0D, "1 阶没有吸血");
        } finally {
            InquisitoriumArmorEvents.forgetForTest(player);
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    // ================================================================ 虚空 PvP

    /** 攻击者是别的玩家：份额 ×0.5、单价 ×2；自己打自己照原样；3 阶起无视护甲的分走 40%、×3 价（标新覆盖面），2 阶不挡 */
    @GameTest(template = EMPTY)
    public static void armorVoidPvpShield(GameTestHelper helper) {
        Player wearer = mock(helper, 1.5D, 1.5D);
        Player other = mock(helper, 3.5D, 1.5D);
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(3, 1, 3), 20D);
        try {
            VoidArmorItem chest = ECVoidArmor.VOID_CHESTPLATE.get();
            ItemStack stack = suit(wearer, VOID, 0)[1];
            double share = VoidArmorItem.share(ArmorItem.Type.CHESTPLATE);
            IMRUShieldArmor.Coverage mob = chest.coverage(stack, wearer.damageSources().mobAttack(zombie));
            IMRUShieldArmor.Coverage pvp = chest.coverage(stack, wearer.damageSources().playerAttack(other));
            IMRUShieldArmor.Coverage self = chest.coverage(stack, wearer.damageSources().playerAttack(wearer));
            helper.assertTrue(mob != null && pvp != null && self != null, "三种都挡");
            close(helper, mob.share(), share, "怪物：整份");
            close(helper, mob.priceMultiplier(), 1D, "怪物：原价");
            close(helper, pvp.share(), share * 0.5D, "玩家：份额 ×0.5");
            close(helper, pvp.priceMultiplier(), 2D, "玩家：单价 ×2");
            close(helper, self.share(), share, "自己打自己照原样");

            ItemStack three = suit(wearer, VOID, 3)[1];
            IMRUShieldArmor.Coverage pierce = chest.coverage(three, source(helper, DamageTypes.CRAMMING, zombie));
            helper.assertTrue(pierce != null && pierce.extended(), "3 阶挡无视护甲，标新覆盖面：" + pierce);
            close(helper, pierce.share(), share * 0.4D, "分走 40%");
            close(helper, pierce.priceMultiplier(), VoidArmorItem.priceMultiplier(3) * 3D, "×3 价");
            IMRUShieldArmor.Coverage pvp3 = chest.coverage(three, wearer.damageSources().playerAttack(other));
            helper.assertTrue(pvp3 != null, "3 阶玩家也挡");
            close(helper, pvp3.priceMultiplier(), VoidArmorItem.priceMultiplier(3) * 2D, "3 阶玩家：折后单价 ×2");
            ItemStack two = suit(wearer, VOID, 2)[1];
            helper.assertTrue(chest.coverage(two, source(helper, DamageTypes.CRAMMING, zombie)) == null, "2 阶不挡无视护甲");
        } finally {
            discard(zombie);
        }
        helper.succeed();
    }

    /** 闪避：有活物攻击者按胸甲的阶（4 阶 35%），攻击者是玩家封顶 15%，没有攻击者按 0 阶 20%；列表开大也过 caps.dodge */
    @GameTest(template = EMPTY)
    public static void armorVoidDodgeCaps(GameTestHelper helper) {
        Player wearer = mock(helper, 1.5D, 1.5D);
        Player other = mock(helper, 3.5D, 1.5D);
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(3, 1, 3), 20D);
        try {
            suit(wearer, VOID, 4);
            DamageSource mob = wearer.damageSources().mobAttack(zombie);
            DamageSource pvp = wearer.damageSources().playerAttack(other);
            close(helper, VoidArmorEvents.dodgeChance(wearer, mob, 4), 0.35D, "怪物 4 阶 35%");
            close(helper, VoidArmorEvents.dodgeChance(wearer, pvp, 4), 0.15D, "玩家封顶 15%");
            close(helper, VoidArmorEvents.dodgeChance(wearer, pvp, 0), 0.15D, "0 阶对玩家也是 15%");
            close(helper, VoidArmorEvents.dodgeChance(wearer, wearer.damageSources().fall(), 4), 0.2D, "摔落按 0 阶 20%");
            close(helper, VoidArmorEvents.dodgeChance(wearer, wearer.damageSources().playerAttack(wearer), 4), 0.2D, "攻击者是自己按 0 阶");
            GearGrowthConfig.setTestOverride("void_armor.dodge", List.of(0.6D));
            close(helper, VoidArmorEvents.dodgeChance(wearer, mob, 4), 0.35D, "列表 60% 夹到 caps.dodge 35%");
            close(helper, VoidArmorEvents.dodgeChance(4), 0.35D, "无攻击者版本同样夹");
            GearGrowthConfig.setTestOverride("caps.dodge", 0.5D);
            close(helper, VoidArmorEvents.dodgeChance(wearer, mob, 4), 0.5D, "caps.dodge 改成 50%");
            close(helper, VoidArmorEvents.dodgeChance(wearer, pvp, 4), 0.15D, "玩家照样 15%");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    // ================================================================ 觉醒

    /** 回涌：护盾挡下的存一半（经 MRUShieldAbsorbedEvent），最多 30；存够 15 后亲手近战打实，本刻末周围的敌对生物各吃回涌值，然后清零 */
    @GameTest(template = EMPTY)
    public static void armorAwakeningSurge(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-surge", new Vec3(0.5D, 1D, 0.5D));
        Zombie target = dummy(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1), 200D);
        Zombie side = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 0), 200D);
        List<S2CGearFxPacket> sent = new ArrayList<>();
        try {
            suit(player, MHS, 1);
            DamageSource mob = player.damageSources().mobAttack(target);
            ArmorAwakenings.storeSurge(player, mob, 20F);
            close(helper, ArmorAwakenings.surge(player), 0D, "1 阶不存");
            suit(player, MHS, 2);
            MinecraftForge.EVENT_BUS.post(new MRUShieldAbsorbedEvent(player, mob, 20F, 20F, 0F));
            close(helper, ArmorAwakenings.surge(player), 10D, "护盾挡下 20 存 10");
            ArmorAwakenings.storeSurge(player, player.damageSources().lava(), 20F);
            ArmorAwakenings.storeSurge(player, player.damageSources().playerAttack(player), 20F);
            close(helper, ArmorAwakenings.surge(player), 10D, "环境伤害、自己打自己不存");
            DamageSource melee = player.damageSources().playerAttack(player);
            hit(target, melee, 1F);
            float idle = side.getHealth();
            ArmorAwakenings.flushAllForTest();
            helper.assertTrue(side.getHealth() == idle && ArmorAwakenings.surge(player) == 10D, "不到 15 不放");
            ArmorAwakenings.storeSurge(player, mob, 100F);
            close(helper, ArmorAwakenings.surge(player), 30D, "最多存 30");
            hit(target, GearAoe.source(player), 1F);
            close(helper, ArmorAwakenings.surge(player), 30D, "范围技打实不放");
            S2CGearFxPacket.setTestSink(sent::add);
            hit(target, melee, 1F);
            close(helper, ArmorAwakenings.surge(player), 0D, "亲手近战打实以后清零");
            float sideBefore = side.getHealth();
            helper.assertTrue(ArmorAwakenings.flushAllForTest() >= 1, "排了一件待办");
            close(helper, sideBefore - side.getHealth(), 30D, "旁边的僵尸吃 30");
            helper.assertTrue(sent.stream().anyMatch(p -> p.kind() == GearFx.SURGE), "发了 SURGE");
        } finally {
            discard(target, side);
            ArmorAwakenings.forgetForTest(player);
            reset();
        }
        helper.succeed();
    }

    /** 风暴步：风之 3 阶空中跳推开、减速周围的敌对生物，不碰玩家（开着 aoe_hits_players 也不碰）；之后下一下亲手近战池 +30%，问到就用掉 */
    @GameTest(template = EMPTY)
    public static void armorAwakeningStormStep(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-storm", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 100D);
        Player bystander = mock(helper, 1.5D, 2.5D);
        bystander.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(bystander);
        List<S2CGearFxPacket> sent = new ArrayList<>();
        try {
            suit(player, WIND, 3);
            helper.assertFalse(ArmorAwakenings.stormStep(player, 2), "2 阶不触发");
            helper.assertFalse(ArmorAwakenings.stormPrimed(player), "2 阶没有加成");
            GearGrowthConfig.setTestOverride("aoe_hits_players", true);
            helper.assertTrue(GearAoe.targets(player, player.position(), ArmorGrowth.stormRadius(), 8).contains(bystander),
                    "测试前提：开着 aoe_hits_players 时范围里找得到旁边的玩家");
            zombie.setDeltaMovement(Vec3.ZERO);
            S2CGearFxPacket.setTestSink(sent::add);
            helper.assertTrue(ArmorAwakenings.stormStep(player, 3), "3 阶触发");
            Vec3 push = zombie.getDeltaMovement();
            Vec3 away = zombie.position().subtract(player.position());
            helper.assertTrue(push.x * away.x + push.z * away.z > 0D, "僵尸被往外推：" + push);
            helper.assertTrue(slowed(zombie, 0), "僵尸缓慢 I");
            helper.assertTrue(bystander.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null, "玩家不挂缓慢");
            helper.assertTrue(bystander.getDeltaMovement().horizontalDistanceSqr() < 1e-8D, "玩家不被推：" + bystander.getDeltaMovement());
            helper.assertTrue(sent.stream().anyMatch(p -> p.kind() == GearFx.STORM_STEP), "发了 STORM_STEP");
            helper.assertTrue(ArmorAwakenings.stormPrimed(player), "加成挂上了");
            close(helper, offensePart(GearAoe.source(player), zombie, "storm_step"), 0D, "范围技不用掉加成");
            helper.assertTrue(ArmorAwakenings.stormPrimed(player), "还在");
            close(helper, offensePart(player.damageSources().playerAttack(player), zombie, "storm_step"), 0.3D, "亲手近战 +30%");
            helper.assertFalse(ArmorAwakenings.stormPrimed(player), "用掉了");
            close(helper, offensePart(player.damageSources().playerAttack(player), zombie, "storm_step"), 0D, "下一下没有");
        } finally {
            discard(zombie, bystander);
            ArmorAwakenings.forgetForTest(player);
            reset();
        }
        helper.succeed();
    }

    /** 残像：虚空 4 阶闪避后留残影，delay 刻后（不是当刻）炸开，打 min(被躲开 × 60%, 40) 加缓慢 II；冷却内不再留；3 阶没有 */
    @GameTest(template = EMPTY)
    public static void armorAwakeningAfterimage(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-after", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 200D);
        List<S2CGearFxPacket> sent = new ArrayList<>();
        try {
            DamageSource mob = player.damageSources().mobAttack(zombie);
            suit(player, VOID, 3);
            helper.assertFalse(ArmorAwakenings.afterimage(player, mob, 20F), "3 阶没有残像");
            suit(player, VOID, 4);
            helper.assertFalse(ArmorAwakenings.afterimage(player, player.damageSources().fall(), 20F), "没有攻击者不留");
            S2CGearFxPacket.setTestSink(sent::add);
            helper.assertTrue(ArmorAwakenings.afterimage(player, mob, 20F), "4 阶留残影");
            helper.assertFalse(ArmorAwakenings.afterimage(player, mob, 20F), "冷却中");
            float before = zombie.getHealth();
            ArmorAwakenings.flush(helper.getLevel().getServer());
            close(helper, zombie.getHealth(), before, "当刻不炸");
            ArmorAwakenings.flushAllForTest();
            close(helper, before - zombie.getHealth(), 12D, "20 × 60%");
            helper.assertTrue(slowed(zombie, 1), "缓慢 II");
            helper.assertTrue(sent.stream().filter(p -> p.kind() == GearFx.AFTERIMAGE).count() >= 2, "留下、炸开各发一次 AFTERIMAGE");
            ArmorAwakenings.forgetForTest(player);
            zombie.removeAllEffects();
            float again = zombie.getHealth();
            helper.assertTrue(ArmorAwakenings.afterimage(player, mob, 500F), "清了冷却再留");
            ArmorAwakenings.flushAllForTest();
            close(helper, again - zombie.getHealth(), 40D, "封顶 40");
        } finally {
            discard(zombie);
            ArmorAwakenings.forgetForTest(player);
            reset();
        }
        helper.succeed();
    }

    /** 宣判：审判庭 4 阶打被定罪的普通生物满 5 下，本刻末补 5% 最大生命（1000 → 50）；冷却内不再计；没定罪、3 阶不计 */
    @GameTest(template = EMPTY)
    public static void armorAwakeningSentenceMob(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-sent", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 1000D);
        Zombie free = dummy(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 3), 1000D);
        List<S2CGearFxPacket> sent = new ArrayList<>();
        try {
            DamageSource melee = player.damageSources().playerAttack(player);
            suit(player, INQ, 3);
            InquisitoriumArmorEvents.convict(player, zombie);
            hit(zombie, melee, 1F);
            helper.assertTrue(ArmorAwakenings.sentenceHits(zombie) == 0, "3 阶不计");
            suit(player, INQ, 4);
            InquisitoriumArmorEvents.convict(player, zombie);
            for (int i = 1; i <= 4; i++) {
                hit(zombie, melee, 1F);
                helper.assertTrue(ArmorAwakenings.sentenceHits(zombie) == i, "第 " + i + " 下，实际 " + ArmorAwakenings.sentenceHits(zombie));
            }
            S2CGearFxPacket.setTestSink(sent::add);
            hit(zombie, melee, 1F);
            helper.assertTrue(ArmorAwakenings.sentenceHits(zombie) == 0, "满 5 下清零");
            float before = zombie.getHealth();
            ArmorAwakenings.flushAllForTest();
            close(helper, before - zombie.getHealth(), 50D, "1000 × 5%");
            helper.assertTrue(sent.stream().anyMatch(p -> p.kind() == GearFx.SENTENCE), "发了 SENTENCE");
            hit(zombie, melee, 1F);
            helper.assertTrue(ArmorAwakenings.sentenceHits(zombie) == 0, "冷却内不计");
            hit(free, melee, 1F);
            helper.assertTrue(ArmorAwakenings.sentenceHits(free) == 0, "没定罪的不计");
        } finally {
            InquisitoriumArmorEvents.forgetForTest(player);
            discard(zombie, free);
            ArmorAwakenings.forgetForTest(player);
            reset();
        }
        helper.succeed();
    }

    /** 宣判对首领：不另打，第 5 下的增伤池 +40%（池来源 sentence）；对玩家：不计、不补 */
    @GameTest(template = EMPTY)
    public static void armorAwakeningSentenceBossAndPlayer(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-sent-b", new Vec3(0.5D, 1D, 0.5D));
        Zombie boss = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 1000D);
        Player victim = mock(helper, 1.5D, 2.5D);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        victim.setHealth(200F);
        try {
            suit(player, INQ, 4);
            DamageSource melee = player.damageSources().playerAttack(player);
            GearCaps.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            InquisitoriumArmorEvents.convict(player, boss);
            close(helper, offensePart(melee, boss, "sentence"), 0D, "还没挨够 4 下");
            for (int i = 0; i < 4; i++) {
                hit(boss, melee, 1F);
            }
            helper.assertTrue(ArmorAwakenings.sentenceHits(boss) == 4, "首领也计数，实际 " + ArmorAwakenings.sentenceHits(boss));
            close(helper, offensePart(melee, boss, "sentence"), 0.4D, "第 5 下池 +40%");
            hit(boss, melee, 1F);
            helper.assertTrue(ArmorAwakenings.sentenceHits(boss) == 0, "宣判后清零");
            float bossBefore = boss.getHealth();
            ArmorAwakenings.flushAllForTest();
            close(helper, boss.getHealth(), bossBefore, "首领不另打");
            close(helper, offensePart(melee, boss, "sentence"), 0D, "冷却内没有");
            GearCaps.setTestBossOverride(null);

            InquisitoriumArmorEvents.convict(player, victim);
            float landed = victim.getHealth();
            for (int i = 0; i < 5; i++) {
                hit(victim, melee, 1F);
            }
            helper.assertTrue(victim.getHealth() < landed, "测试前提：打玩家打实了");
            helper.assertTrue(ArmorAwakenings.sentenceHits(victim) == 0, "玩家不计");
            float before = victim.getHealth();
            ArmorAwakenings.flushAllForTest();
            close(helper, victim.getHealth(), before, "玩家不补");
        } finally {
            GearCaps.setTestBossOverride(null);
            InquisitoriumArmorEvents.forgetForTest(player);
            discard(boss);
            ArmorAwakenings.forgetForTest(player, victim);
            reset();
        }
        helper.succeed();
    }

    /** 超载脉冲：宅男 5 阶过载屏障展开时，本刻末周围的敌对生物吃 min(被挡 × 50%, 40) 加缓慢 III；冷却 765 刻 */
    @GameTest(template = EMPTY)
    public static void armorAwakeningOverchargePulse(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-pulse", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 200D);
        List<S2CGearFxPacket> sent = new ArrayList<>();
        try {
            // 默认半径 6 会够到旁边测试的僵尸：这一条缩到 3
            GearGrowthConfig.setTestOverride("computer_armor.overcharge_pulse.radius", 3D);
            suit(player, COMPUTER, 5);
            data(player).setAttunementOverloadCooldown(0);
            S2CGearFxPacket.setTestSink(sent::add);
            float before = zombie.getHealth();
            helper.assertTrue(ComputerAttunement.tryOverload(player, player.damageSources().mobAttack(zombie), 10F), "屏障展开");
            helper.assertTrue(data(player).getAttunementOverloadCooldown() == 765, "冷却 765，实际 " + data(player).getAttunementOverloadCooldown());
            close(helper, zombie.getHealth(), before, "当时还没打");
            helper.assertTrue(ArmorAwakenings.flushAllForTest() >= 1, "排了脉冲");
            close(helper, before - zombie.getHealth(), 5D, "10 × 50%");
            helper.assertTrue(slowed(zombie, 2), "缓慢 III");
            helper.assertTrue(sent.stream().anyMatch(p -> p.kind() == GearFx.OVERCHARGE_PULSE), "发了 OVERCHARGE_PULSE");
            float again = zombie.getHealth();
            helper.assertFalse(ArmorAwakenings.overchargePulse(player, player.damageSources().lava(), 500F), "环境伤害展开的屏障不放脉冲");
            helper.assertTrue(ArmorAwakenings.overchargePulse(player, player.damageSources().mobAttack(zombie), 500F), "直接放一次");
            ArmorAwakenings.flushAllForTest();
            close(helper, again - zombie.getHealth(), 40D, "封顶 40");
            GearGrowthConfig.setTestOverride("computer_armor.overcharge_pulse.stage", 6);
            helper.assertFalse(ArmorAwakenings.overchargePulse(player, player.damageSources().mobAttack(zombie), 10F), "解锁阶调高就不放");
        } finally {
            ComputerAttunement.forgetForTest(player);
            data(player).setAttunementOverloadCooldown(0);
            discard(zombie);
            ArmorAwakenings.forgetForTest(player);
            reset();
        }
        helper.succeed();
    }
}
