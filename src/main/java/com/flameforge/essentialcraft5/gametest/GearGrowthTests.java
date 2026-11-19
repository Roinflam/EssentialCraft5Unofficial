package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.VerdictBladeItem;
import com.flameforge.essentialcraft5.item.VerdictSeal;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.item.attunement.AttunementLook;
import com.flameforge.essentialcraft5.item.attunement.AttunementShowcase;
import com.flameforge.essentialcraft5.item.attunement.BattleScars;
import com.flameforge.essentialcraft5.item.attunement.GearAoe;
import com.flameforge.essentialcraft5.item.attunement.GearCaps;
import com.flameforge.essentialcraft5.item.attunement.GearCombatEvents;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.attunement.GearPool;
import com.flameforge.essentialcraft5.item.attunement.GearStat;
import com.flameforge.essentialcraft5.item.attunement.GearStatSheet;
import com.flameforge.essentialcraft5.item.ember.ElementBurst;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData.Element;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECGear;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 武器防具成长加强 G1：总闸（增伤池 / 减伤池相加、封顶、穿透、吸血、补伤封顶）、战痕、裁决之刃和余烬元素剑的新阶效果与觉醒、事件包
 * （tools/plan/gear_growth.md 第 9 节）。枪械同调在 {@code GunAttunementTests}。
 * <p>
 * 池子的相加和封顶用临时登记的来源（{@code test_*}）验，算式只看 {@link GearCombatEvents#factor}；端到端的几条（吸血、补伤封顶、终审）走真的 hurt。
 * 覆盖值（首领判定、配置、掷骰）都在 finally 里置回。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GearGrowthTests {

    private static final String EMPTY = "empty";
    private static final double EPS = 1e-4D;

    private GearGrowthTests() {
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

    private static ItemStack blade(int stage) {
        ItemStack stack = MRUStaffItem.full(ECEmberForge.VERDICT_BLADE.get());
        sword(Element.WATER, Element.FIRE).writeTo(stack);
        Attunement.set(stack, stage, 0);
        return stack;
    }

    private static ItemStack emberSword(Element primary, int stage) {
        ItemStack stack = MRUStaffItem.full(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        sword(primary, Element.WATER).writeTo(stack);
        Attunement.set(stack, stage, 0);
        return stack;
    }

    private static ForgedSwordData sword(Element primary, Element secondary) {
        List<ResourceLocation> embers = List.of(EmberKind.COMMON.id(), EmberKind.COMMON.id(), EmberKind.COMMON.id(), EmberKind.COMMON.id());
        List<ResourceLocation> foci = List.of(Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus());
        return new ForgedSwordData(embers, foci, primary, secondary);
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

    private static double factor(DamageSource source, LivingEntity target, float amount) {
        return GearCombatEvents.factor(GearPool.context(source, target, amount));
    }

    /** 刀剑属性栏那份同调攻击的倍数 */
    private static double g(ItemStack stack) {
        return com.flameforge.essentialcraft5.item.attunement.AttunementBonuses.bladeAttackFactor(stack);
    }

    private static void close(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EPS, what + "：应为 " + expected + "，实际 " + actual);
    }

    private static void reset() {
        for (String id : List.of("test_a", "test_b", "test_big")) {
            GearPool.removeOffense(id);
            GearPool.removeDefense(id);
        }
        GearCaps.setTestBossOverride(null);
        VerdictBladeItem.setTestBossOverride(null);
        GearGrowthConfig.clearTestOverrides();
        ElementBurst.setTestRoll(null);
        ElementBurst.clearCooldowns();
        S2CGearFxPacket.setTestSink(null);
        VerdictBladeItem.flush();
        GearCombatEvents.flush();
    }

    private static void discard(LivingEntity... entities) {
        for (LivingEntity entity : entities) {
            if (entity != null) {
                VerdictSeal.forgetForTest(entity);
                entity.discard();
            }
        }
    }

    // ================================================================ 增伤池

    /** 两个来源 30% + 15% 相加：×1.45，不是 ×1.495 */
    @GameTest(template = EMPTY)
    public static void gearPoolAdditive(GameTestHelper helper) {
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1), 100D);
        Zombie attacker = dummy(helper, EntityType.ZOMBIE, new BlockPos(3, 1, 1), 20D);
        try {
            GearPool.registerOffense("test_a", context -> 0.30D);
            GearPool.registerOffense("test_b", context -> 0.15D);
            DamageSource source = zombie.damageSources().mobAttack(attacker);
            close(helper, factor(source, zombie, 10F), 1.45D, "两项相加");
            GearPool.Breakdown parts = GearPool.offense(GearPool.context(source, zombie, 10F));
            close(helper, parts.part("test_a") + parts.part("test_b"), 0.45D, "分项");
            float before = zombie.getHealth();
            zombie.hurt(source, 10F);
            close(helper, before - zombie.getHealth(), 14.5D, "真的打一下：10 × 1.45");
        } finally {
            discard(zombie, attacker);
            reset();
        }
        helper.succeed();
    }

    /** 增伤池封顶：普通生物 ×2.0、首领 ×1.5、玩家 ×1.3（来源开到 +500%） */
    @GameTest(template = EMPTY)
    public static void gearPoolCaps(GameTestHelper helper) {
        ServerPlayer attacker = fighter(helper, "e5-cap-a", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer victim = fighter(helper, "e5-cap-v", new Vec3(2.5D, 1D, 2.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 2), 100D);
        try {
            GearPool.registerOffense("test_big", context -> 5D);
            DamageSource source = attacker.damageSources().playerAttack(attacker);
            close(helper, factor(source, zombie, 10F), 2D, "打怪 ×2");
            GearCaps.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            close(helper, factor(source, zombie, 10F), 1.5D, "打首领 ×1.5");
            GearCaps.setTestBossOverride(null);
            close(helper, factor(source, victim, 10F), 1.3D, "打玩家 ×1.3");
            GearGrowthConfig.setTestOverride("offense_scale", 0.1D);
            close(helper, factor(source, zombie, 10F), 1.5D, "offense_scale 0.1：+500% × 0.1 = +50%");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    /** 裁断 / 终审的补伤（verdict）、范围技（gear_burst）不吃增伤池 */
    @GameTest(template = EMPTY)
    public static void gearPoolSkipsVerdictAndBurst(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-skip", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 100D);
        try {
            GearPool.registerOffense("test_big", context -> 1D);
            close(helper, factor(player.damageSources().playerAttack(player), zombie, 10F), 2D, "普通一下吃池");
            close(helper, factor(VerdictBladeItem.verdictSource(player), zombie, 10F), 1D, "verdict 不吃池");
            close(helper, factor(GearAoe.source(player), zombie, 10F), 1D, "gear_burst 不吃池");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    /** 减伤池：35% + 15% = 50%；叠到 80% 夹 60%；攻击者是玩家夹 40% */
    @GameTest(template = EMPTY)
    public static void gearReductionPoolAdditiveAndCapped(GameTestHelper helper) {
        ServerPlayer attacker = fighter(helper, "e5-red-a", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer victim = fighter(helper, "e5-red-v", new Vec3(2.5D, 1D, 2.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 2), 100D);
        try {
            GearPool.registerDefense("test_a", context -> 0.35D);
            GearPool.registerDefense("test_b", context -> 0.15D);
            DamageSource mob = victim.damageSources().mobAttack(zombie);
            close(helper, factor(mob, victim, 10F), 0.5D, "35% + 15%");
            GearPool.registerDefense("test_b", context -> 0.45D);
            close(helper, factor(mob, victim, 10F), 0.4D, "80% 夹到 60%");
            close(helper, factor(attacker.damageSources().playerAttack(attacker), victim, 10F), 0.6D, "攻击者是玩家夹到 40%");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    /** 护甲穿透：护甲 10 / 韧性 4、穿透 25% 的倍数等于原版公式的比值；带 bypasses_armor 的不算（护甲再厚，阶 40% + 穿透会顶到普通生物的池上限 100%） */
    @GameTest(template = EMPTY)
    public static void gearArmorPenConversion(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-pen", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 1000D);
        try {
            zombie.getAttribute(Attributes.ARMOR).setBaseValue(10D);
            zombie.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(4D);
            ItemStack stack = blade(5);
            wield(player, stack);
            helper.assertTrue(GearStatSheet.get(stack, GearStat.DAMAGE) == 0D, "刀剑的阶加成不在池里");
            double g = g(stack);
            close(helper, GearStatSheet.get(stack, GearStat.ARMOR_PEN), 0.25D, "5 阶穿透 25%");
            float d = 10F;
            double m = CombatRules.getDamageAfterAbsorb(d, 7.5F, 3F) / (double) CombatRules.getDamageAfterAbsorb(d, 10F, 4F);
            close(helper, factor(player.damageSources().playerAttack(player), zombie, 10F) * g, Math.min(g * m, 2D), "穿透折进池子，和属性那份相乘");
            DamageSource bypass = new DamageSource(ECDamageTypes.holder(helper.getLevel(), DamageTypes.SONIC_BOOM), player);
            // 音爆这类伤害不是亲手近战，整个增伤池都不吃（阶也不加），穿透更不会算
            close(helper, factor(bypass, zombie, 10F), 1D, "bypasses_armor 的伤害不算穿透");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    /** 斩杀（裁决之刃 4 阶）：30% 以下 +20%，以上 0；首领、玩家 0 */
    @GameTest(template = EMPTY)
    public static void gearExecuteBelowThreshold(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-exec", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer other = fighter(helper, "e5-exec-v", new Vec3(2.5D, 1D, 2.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 100D);
        try {
            ItemStack stack = blade(4);
            wield(player, stack);
            // 阶的攻击在属性栏里（倍数 g），factor × g 是这一下相对没同调时的总倍数
            double g = g(stack);
            DamageSource source = player.damageSources().playerAttack(player);
            zombie.setHealth(20F);
            close(helper, factor(source, zombie, 10F) * g, Math.min(g * 1.2D, 2D), "20% 生命：属性 × (1 + 斩杀 20%)");
            zombie.setHealth(50F);
            close(helper, factor(source, zombie, 10F), 1D, "50% 生命：只有属性那份");
            zombie.setHealth(20F);
            GearCaps.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            close(helper, factor(source, zombie, 10F) * g, Math.min(g, 1.5D), "首领不斩杀");
            GearCaps.setTestBossOverride(null);
            other.setHealth(2F);
            close(helper, factor(source, other, 10F) * g, Math.min(1D + (g - 1D) * 0.5D, 1.3D), "玩家不斩杀，属性那份 ×0.5");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    // ================================================================ 吸血

    /** 每秒最多回最大生命 6%（20 → 1.2）；同一刻再打不再回；挂腐蚀回不了 */
    @GameTest(template = EMPTY)
    public static void gearLifestealCapPerSecond(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-steal", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 5000D);
        try {
            wield(player, blade(4));
            GearCombatEvents.resetBucket(player);
            player.setHealth(5F);
            zombie.hurt(player.damageSources().playerAttack(player), 100F);
            helper.assertTrue(GearCombatEvents.pendingHeals() == 1, "打实记一条吸血，实际 " + GearCombatEvents.pendingHeals());
            float healed = GearCombatEvents.flush();
            close(helper, healed, 1.2D, "132 × 4% 被桶夹到 1.2");
            close(helper, player.getHealth(), 6.2D, "生命 5 → 6.2");
            zombie.invulnerableTime = 0;
            zombie.hurt(player.damageSources().playerAttack(player), 100F);
            close(helper, GearCombatEvents.flush(), 0D, "同一刻桶空了");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void gearLifestealBlockedByCorrosion(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-steal-c", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 5000D);
        try {
            wield(player, blade(4));
            GearCombatEvents.resetBucket(player);
            player.setHealth(5F);
            player.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), 200, 0));
            zombie.hurt(player.damageSources().playerAttack(player), 100F);
            GearCombatEvents.flush();
            close(helper, player.getHealth(), 5D, "腐蚀挡住治疗");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    // ================================================================ 补伤封顶

    /** 测试挂一个 LOWEST ×3 的监听器，裁断最终掉血仍 = 封顶 40 */
    @GameTest(template = EMPTY)
    public static void gearMaxHealthStrikeClamped(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-clamp", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 5000D);
        Consumer<LivingHurtEvent> triple = event -> {
            if (event.getSource().is(VerdictBladeItem.VERDICT)) {
                event.setAmount(event.getAmount() * 3F);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, triple);
        try {
            wield(player, blade(0));
            ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, player, 100, "f_20922_");
            MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(player, zombie));
            zombie.hurt(player.damageSources().playerAttack(player), 5F);
            float before = zombie.getHealth();
            VerdictBladeItem.flush();
            close(helper, before - zombie.getHealth(), 40D, "5000 × 3% = 150 夹 40，再 ×3 也只掉 40");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(triple);
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    // ================================================================ 通用每阶加成、老存档

    /** 2 阶剑没有攻击倍率修饰符（阶的攻击并进本体那条加法修饰符，见 BaseStatsTests）；魔力防护胸甲 2 阶 +4 生命（并进胸甲自己的那一条） */
    @GameTest(template = EMPTY)
    public static void gearWeaponStageNoAttributeModifier(GameTestHelper helper) {
        ItemStack sword = blade(2);
        Collection<AttributeModifier> damage = sword.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(damage.stream().noneMatch(m -> m.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL),
                "不再有 ×1.03 / 阶的攻击修饰符：" + damage);
        ItemStack chest = new ItemStack(ECGear.MHS_CHESTPLATE.get());
        double plain = chest.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.MAX_HEALTH).stream().mapToDouble(AttributeModifier::getAmount).sum();
        Attunement.set(chest, 2, 0);
        Collection<AttributeModifier> health = chest.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.MAX_HEALTH);
        // 同调生命并进胸甲自己按配置给的那一条，只剩一条合计
        helper.assertTrue(health.size() == 1 && Math.abs(health.iterator().next().getAmount() - (plain + 4D)) < EPS,
                "盔甲 2 阶 +4 生命（并成一条）：" + health);
        helper.succeed();
    }

    /** v1 的 NBT（只有 stage / points / total / rank）：阶照算、战痕 0、外观不报错 */
    @GameTest(template = EMPTY)
    public static void gearOldItemWithoutNewKeys(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ECEmberForge.VERDICT_BLADE.get());
        CompoundTag tag = new CompoundTag();
        tag.putByte(Attunement.KEY_STAGE, (byte) 3);
        tag.putInt(Attunement.KEY_POINTS, 10);
        tag.putInt(Attunement.KEY_TOTAL, 2000);
        tag.putByte(Attunement.KEY_RANK, (byte) 5);
        tag.putByte(Attunement.KEY_VERSION, (byte) 1);
        stack.getOrCreateTag().put(Attunement.TAG, tag);
        helper.assertTrue(Attunement.stage(stack) == 3 && Attunement.points(stack) == 10, "阶和点数照读");
        helper.assertTrue(Attunement.scar(stack) == 0 && Attunement.bosses(stack) == 0 && Attunement.dealt(stack) == 0L
                && BattleScars.level(stack) == 0, "新键没有 = 0");
        helper.assertTrue(g(stack) > 1D && GearStatSheet.get(stack, GearStat.DAMAGE) == 0D, "3 阶的攻击加在属性栏上");
        helper.assertTrue(AttunementLook.name(stack, stack.getItem().getName(stack)) != null && Attunement.hasGlint(stack), "名字、光泽照常");
        helper.succeed();
    }

    // ================================================================ 战痕

    /** 满阶攒点进战痕；2000 一级；封顶 10 级以后不再拿点 */
    @GameTest(template = EMPTY)
    public static void gearBattleScarOverflow(GameTestHelper helper) {
        ItemStack stack = blade(5);
        helper.assertTrue(Attunement.scarRoom(stack) == 20_000, "满阶战痕能攒 20 000，实际 " + Attunement.scarRoom(stack));
        helper.assertTrue(Attunement.addPoints(stack, 2500) == 2500 && Attunement.scar(stack) == 2500 && BattleScars.level(stack) == 1,
                "2500 点 = 1 级");
        helper.assertTrue(Attunement.points(stack) == 0, "本阶点数不动");
        helper.assertTrue(Attunement.addPoints(stack, 100_000) == 17_500 && BattleScars.level(stack) == 10, "封顶 10 级");
        helper.assertTrue(Attunement.addPoints(stack, 1) == 0 && Attunement.scarRoom(stack) == 0, "封顶以后不拿点");
        close(helper, GearStatSheet.scarDamage(stack), 0.1D, "10 级战痕 +10%");
        ItemStack low = blade(4);
        helper.assertTrue(Attunement.scarRoom(low) == 0, "没满阶不攒战痕");
        ItemStack sword = emberSword(Element.FIRE, 2);
        helper.assertTrue(Attunement.addPoints(sword, 2000) == 2000 && BattleScars.level(sword) == 1, "余烬剑 2 阶就是满阶");
        helper.succeed();
    }

    /** 首领覆盖下用同调武器击杀：bosses + 1 */
    @GameTest(template = EMPTY)
    public static void gearBossKillCounted(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-boss-k", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 10D);
        try {
            ItemStack stack = blade(1);
            wield(player, stack);
            GearCaps.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            zombie.hurt(player.damageSources().playerAttack(player), 100F);
            helper.assertTrue(zombie.isDeadOrDying(), "测试前提：打死了");
            helper.assertTrue(Attunement.bosses(stack) == 1, "首领击杀 + 1，实际 " + Attunement.bosses(stack));
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    /** 同调转移（旧件清空）、升格都不带战痕 */
    @GameTest(template = EMPTY)
    public static void gearScarNotTransferred(GameTestHelper helper) {
        ItemStack stack = blade(5);
        Attunement.addPoints(stack, 5000);
        Attunement.addBossKill(stack);
        ItemStack cleared = Attunement.cleared(stack);
        helper.assertTrue(Attunement.scar(cleared) == 0 && Attunement.stage(cleared) == 5 && Attunement.bosses(cleared) == 1,
                "转移后旧件：战痕清掉，阶和战绩留着");
        ItemStack sword = emberSword(Element.FIRE, 2);
        Attunement.addPoints(sword, 3000);
        Attunement.addBossKill(sword);
        Attunement.addDealt(sword, 1234L);
        ItemStack promoted = Attunement.promoted(sword, new ItemStack(ECEmberForge.VERDICT_BLADE.get()));
        helper.assertTrue(Attunement.scar(promoted) == 0 && Attunement.bosses(promoted) == 0 && Attunement.dealt(promoted) == 0L,
                "升格成裁决之刃：战痕、战绩从 0 起");
        helper.succeed();
    }

    // ================================================================ 裁决之刃

    /**
     * 5 阶裁断 8% / 140；3 阶断罪冷却按 −20% 打折。最大生命属性最多 1024，1024 × 8% 到不了 140，
     * 封顶那一下把每阶百分比临时盖成 30（3 + 5 × 30 = 153%），盖值和清掉都在这一段同步代码里
     */
    @GameTest(template = EMPTY)
    public static void verdictNewStageNumbers(GameTestHelper helper) {
        Zombie small = dummy(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1), 1000D);
        try {
            ItemStack stack = blade(5);
            close(helper, VerdictBladeItem.verdictAmount(stack, small), 80D, "1000 × 8%");
            GearGrowthConfig.setTestOverride("verdict_blade.percent_per_stage", 30D);
            float capped = VerdictBladeItem.verdictAmount(stack, small);
            GearGrowthConfig.clearTestOverrides();
            close(helper, capped, 140D, "1000 × 153% 夹到封顶 40 + 5 × 20");
            VerdictBladeItem.Config.Values values = VerdictBladeItem.Config.values();
            helper.assertTrue(VerdictBladeItem.judgmentCooldown(2, values) == 260, "2 阶：300 − 40");
            helper.assertTrue(VerdictBladeItem.judgmentCooldown(3, values) == 192, "3 阶：(300 − 60) × 0.8 = 192，实际 "
                    + VerdictBladeItem.judgmentCooldown(3, values));
        } finally {
            discard(small);
            reset();
        }
        helper.succeed();
    }

    /** 终审：断罪挂罪印 → 下一刻亲手打实引爆，普通目标补 6% 封顶 120；事件包发了挂印和引爆 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void verdictFinalVerdictMob(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-final", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 1000D);
        List<S2CGearFxPacket> sent = new ArrayList<>();
        ItemStack stack = blade(5);
        wield(player, stack);
        S2CGearFxPacket.setTestSink(sent::add);
        VerdictBladeItem.Judgment judgment = VerdictBladeItem.judgment(player, stack);
        S2CGearFxPacket.setTestSink(null);
        helper.assertTrue(judgment.outcome() == VerdictBladeItem.Outcome.DONE, "断罪放出来了：" + judgment);
        helper.assertTrue(VerdictSeal.marked(zombie, player), "挂上罪印");
        VerdictSeal.flush();
        helper.assertTrue(VerdictSeal.marked(zombie, player), "同一刻不引爆");
        helper.runAfterDelay(1, () -> {
            try {
                zombie.invulnerableTime = 0;
                zombie.hurt(player.damageSources().playerAttack(player), 1F);
                helper.assertFalse(VerdictSeal.marked(zombie, player), "打实以后罪印消失");
                float before = zombie.getHealth();
                S2CGearFxPacket.setTestSink(sent::add);
                // 最大生命属性最多 1024：把终审百分比临时盖成 18，1000 × 18% = 180 才顶得到封顶 120
                GearGrowthConfig.setTestOverride("verdict_blade.final_verdict.percent", 18D);
                int burst;
                try {
                    burst = VerdictSeal.flush();
                } finally {
                    GearGrowthConfig.clearTestOverrides();
                }
                helper.assertTrue(burst == 1, "引爆一下，实际 " + burst);
                close(helper, before - zombie.getHealth(), 120D, "1000 × 18% = 180 夹 120");
                helper.assertTrue(sent.stream().anyMatch(p -> p.kind() == GearFx.VERDICT_SEAL)
                        && sent.stream().anyMatch(p -> p.kind() == GearFx.VERDICT_BURST), "挂印、引爆各发了事件");
                helper.succeed();
            } finally {
                discard(zombie);
                reset();
            }
        });
    }

    /** 终审对首领：引爆的那一下池 +40%（和阶 40% 一起被 ×1.5 夹住）；罪印 100 刻后过期 */
    @GameTest(template = EMPTY, timeoutTicks = 160)
    public static void verdictFinalVerdictBoss(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-final-b", new Vec3(0.5D, 1D, 0.5D));
        Zombie boss = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 3000D);
        Zombie later = dummy(helper, EntityType.ZOMBIE, new BlockPos(4, 1, 4), 3000D);
        ItemStack stack = blade(5);
        wield(player, stack);
        VerdictSeal.mark(player, boss);
        VerdictSeal.mark(player, later);
        helper.runAfterDelay(1, () -> {
            // 首领覆盖只在这一段同步代码里开着（别的测试也在刷僵尸）
            GearCaps.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            VerdictBladeItem.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            try {
                DamageSource source = player.damageSources().playerAttack(player);
                double g = g(stack);
                close(helper, factor(source, boss, 10F) * g, Math.min(g * 1.4D, 1.5D), "属性那份 × (1 + 罪印 40%) 夹 ×1.5");
                boss.invulnerableTime = 0;
                float before = boss.getHealth();
                boss.hurt(source, 10F);
                close(helper, before - boss.getHealth(), 10D * Math.min(g * 1.4D, 1.5D) / g, "真的打一下（10 当作已含属性）");
                helper.assertFalse(VerdictSeal.marked(boss, player), "引爆以后消失");
                close(helper, factor(source, boss, 10F) * g, Math.min(g, 1.5D), "没罪印只有属性那份");
                helper.assertTrue(VerdictSeal.flush() == 0, "首领不补伤");
            } finally {
                GearCaps.setTestBossOverride(null);
                VerdictBladeItem.setTestBossOverride(null);
            }
        });
        helper.runAfterDelay(102, () -> {
            try {
                helper.assertFalse(VerdictSeal.marked(later, player), "100 刻后过期");
                helper.succeed();
            } finally {
                discard(boss, later);
                reset();
            }
        });
    }

    // ================================================================ 余烬元素剑

    /** 元素爆发（火）：周围僵尸各 4 + 2%；40 刻内不再触发；默认不打旁边的玩家；爆发伤害不给同调点、不吃池 */
    @GameTest(template = EMPTY)
    public static void emberBurstFire(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-burst", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer bystander = fighter(helper, "e5-burst-b", new Vec3(2.5D, 1D, 2.5D));
        Zombie main = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 100D);
        Zombie side = dummy(helper, EntityType.ZOMBIE, new BlockPos(3, 1, 1), 100D);
        try {
            ItemStack stack = emberSword(Element.FIRE, 2);
            wield(player, stack);
            ElementBurst.clearCooldowns();
            ElementBurst.setTestRoll(() -> 0D);
            float mainBefore = main.getHealth();
            float sideBefore = side.getHealth();
            Element used = ElementBurst.onHit(stack, main, player, Element.FIRE);
            helper.assertTrue(used == Element.FIRE, "必中：火");
            close(helper, mainBefore - main.getHealth(), 6D, "主目标 4 + 100 × 2%");
            close(helper, sideBefore - side.getHealth(), 6D, "旁边的也吃");
            helper.assertTrue(side.getRemainingFireTicks() > 0, "着火");
            close(helper, bystander.getHealth(), bystander.getMaxHealth(), "默认不打玩家");
            helper.assertTrue(ElementBurst.onHit(stack, main, player, Element.FIRE) == null, "40 刻冷却");
            helper.assertTrue(AttunementGains.pending(player, EquipmentSlot.MAINHAND) == 0F, "爆发不给同调点");
            ItemStack blade = blade(5);
            helper.assertFalse(ElementBurst.unlocked(blade), "裁决之刃不继承元素爆发");
        } finally {
            discard(main, side);
            reset();
        }
        helper.succeed();
    }

    /** 火爆发每个目标这一下最多掉 fire_cap（默认 100）：厚血的普通怪按百分比打出几百时夹到 100；首领同样封；设 0 不封顶 */
    @GameTest(template = EMPTY)
    public static void emberBurstFireCapped(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-burstcap", new Vec3(0.5D, 1D, 0.5D));
        // 爆发半径 3 格，场地只有 5 格见方：三个目标一次只摆一个，打完就移走
        Zombie thick = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 2), 1000D);
        Zombie open = null;
        net.minecraft.world.entity.monster.Husk boss = null;
        try {
            close(helper, GearGrowthConfig.burstFireCap(), 100D, "默认封顶 100");
            // 4 + 1000 × 50% = 504：夹到 100
            GearGrowthConfig.setTestOverride("ember_sword.burst.fire_percent", 50D);
            float before = thick.getHealth();
            ElementBurst.burst(player, thick, Element.FIRE);
            close(helper, before - thick.getHealth(), 100D, "普通怪按百分比超出封顶时夹到 100");
            discard(thick);
            boss = dummy(helper, EntityType.HUSK, new BlockPos(2, 1, 2), 1000D);
            // 首领只吃固定那一截，固定伤害调到 300 也夹到 100
            GearGrowthConfig.setTestOverride("ember_sword.burst.fire_damage", 300D);
            GearCaps.setTestBossOverride(type -> type == EntityType.HUSK);
            try {
                before = boss.getHealth();
                ElementBurst.burst(player, boss, Element.FIRE);
                close(helper, before - boss.getHealth(), 100D, "首领同样封顶");
            } finally {
                GearCaps.setTestBossOverride(null);
            }
            discard(boss);
            open = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 2), 1000D);
            // 0 = 不封顶
            GearGrowthConfig.setTestOverride("ember_sword.burst.fire_damage", 4D);
            GearGrowthConfig.setTestOverride("ember_sword.burst.fire_cap", 0D);
            before = open.getHealth();
            ElementBurst.burst(player, open, Element.FIRE);
            helper.assertTrue(before - open.getHealth() > 400F, "设 0 不封顶，实际掉血 " + (before - open.getHealth()));
        } finally {
            discard(thick, open, boss);
            reset();
        }
        helper.succeed();
    }

    /** gear_burst 进 notOwnHit：不算亲手、不吃池、不触发补伤 */
    @GameTest(template = EMPTY)
    public static void gearAoeNoChain(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-nochain", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 1), 100D);
        try {
            wield(player, blade(5));
            DamageSource burst = GearAoe.source(player);
            helper.assertTrue(AttunementGains.notOwnHit(burst), "gear_burst 不是亲手打的");
            helper.assertTrue(GearPool.ownWeapon(burst, zombie) == null, "不认主手武器");
            helper.assertTrue(VerdictBladeItem.bladeAttacker(burst, zombie) == null, "不补裁断");
            GearAoe.hit(player, zombie, 5F);
            helper.assertTrue(VerdictBladeItem.pendingCount() == 0 && GearCombatEvents.pendingHeals() == 0, "不排裁断、不吸血");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    // ================================================================ 事件包

    /** 战痕升级、升阶演出各发了对应类型的事件（终审在 verdictFinalVerdictMob、过载在 GunAttunementTests） */
    @GameTest(template = EMPTY)
    public static void gearFxPacketSent(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-fx", new Vec3(0.5D, 1D, 0.5D));
        List<S2CGearFxPacket> sent = new ArrayList<>();
        try {
            S2CGearFxPacket.setTestSink(sent::add);
            ItemStack stack = blade(5);
            Attunement.addPoints(stack, 1999);
            wield(player, stack);
            AttunementGains.addPendingForTest(player, EquipmentSlot.MAINHAND, 5F);
            AttunementGains.flush(player);
            helper.assertTrue(BattleScars.level(stack) == 1, "战痕升到 1 级，实际 " + BattleScars.level(stack));
            helper.assertTrue(sent.stream().anyMatch(p -> p.kind() == GearFx.SCAR_UP && p.param() == 1F), "发了 SCAR_UP");
            AttunementShowcase.ascended(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), blade(3));
            helper.assertTrue(sent.stream().anyMatch(p -> p.kind() == GearFx.ASCEND && p.param() == 3F), "发了 ASCEND");
        } finally {
            AttunementGains.forget(player);
            reset();
        }
        helper.succeed();
    }

    /** 光泽新规则：最高 2 阶的余烬剑满阶就亮，裁决之刃照旧 3 阶起 */
    @GameTest(template = EMPTY)
    public static void gearGlintAtFamilyMax(GameTestHelper helper) {
        helper.assertTrue(Attunement.hasGlint(emberSword(Element.FIRE, 2)), "余烬剑 2 阶（满阶）亮");
        helper.assertFalse(Attunement.hasGlint(emberSword(Element.FIRE, 1)), "余烬剑 1 阶不亮");
        helper.assertFalse(Attunement.hasGlint(blade(2)), "裁决之刃 2 阶不亮");
        helper.assertTrue(Attunement.hasGlint(blade(3)), "裁决之刃 3 阶亮");
        helper.succeed();
    }
}
