package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.entity.ArmorDestroyerEntity;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.gun.LensEffects;
import com.flameforge.essentialcraft5.gun.RayShot;
import com.flameforge.essentialcraft5.item.ElementalTier;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.MatrixTraits;
import com.flameforge.essentialcraft5.item.MatrixWeaponItem;
import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.matrix.MatrixPool;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import com.flameforge.essentialcraft5.matrix.MatrixSiphon;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECStaffs;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十三批 13a-M2「相性与矩阵特性」的 GameTest（batch13.md §3.2、§3.1.4 虹吸、§4.6 早期武器修补、§12.2 的 13a 行）：
 * <ul>
 *   <li><b>相性</b>：三 / 六 / 九阶的级、暗影八阶起直接 II；魔力的耗电倍数和虹吸上限；躁动加近战 / 弹射物、不加魔法和反伤、打玩家减半；
 *       凝滞减近战、不减无视护甲和弹射物、打实后反冻攻击者（魔法、无视护甲的伤害不反冻）；吞噬减有攻击者的魔法、不减没有攻击者的自伤。</li>
 *   <li><b>矩阵特性</b>：腐蚀 / 冰冻 / 驱散 / 吸取四种各自生效、强度随相性级和神圣重锤 ×2；没相性、不带标签的武器不触发；
 *       打实才给（攻击被拦、伤害事件被取消都不给）；PvP 关着不给、开着减半；扣不起不触发；同一刻对同一个目标只结算一次；腐蚀中 / 冻僵的目标 +10%；
 *       只吃到增伤、效果没挂上也照扣钱；打玩家时相性和特性的倍数连乘。</li>
 *   <li><b>透镜同型</b>、<b>早期武器修补</b>（弱化元素 +0.5、混沌之叉 12 / 2.0、神圣重锤打亡灵 +50%、两把重锤的上限加相性级，
 *       只加在目标身上、打玩家不加，混沌之叉给自己的力量不越过上限）；拆甲者的磨损按原价扣、不吃魔力相性的折扣。</li>
 *   <li><b>虹吸</b>：抽进容量、挨损伤（吃耐受）、冷却；阶不够、被稳定、领地里、容量满、面前没有都不抽；灵魂石潜行右键二阶起开关换入、对着聚集体开始按住；
 *       只认主手，副手拿着什么都不做；四阶起面前 REACH + 1 格内有聚集体时潜行右键不切换换入。</li>
 * </ul>
 * 测试玩家是 {@code ProtectionFrameworkTests.realPlayer}（真 ServerPlayer、不在世界的实体列表里）；本刻末的结算在 hurt 之后直接调 {@link MatrixTraits#flush()}。
 * 伤害量在 {@code LivingHurtEvent} LOWEST 读（别的减伤之前、难度缩放之后），用对照组比倍数。配置一律是默认值。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MatrixAlignmentTests {

    private static final String EMPTY = "empty";
    private static final float EPS = 1.0E-3F;
    private static final RayShot.MatrixFxSink NO_FX = (target, intensity) -> {
    };

    private MatrixAlignmentTests() {
    }

    // ================================================================== 相性

    /** 相性级：三 / 六 / 九阶 I / II / III，中性没有，暗影八阶起直接 II、九阶 III */
    @GameTest(template = EMPTY)
    public static void alignmentLevels(GameTestHelper helper) {
        int[][] expect = {
                // 阶 0~9 的混沌级、暗影级
                {0, 0}, {0, 0}, {0, 0}, {1, 0}, {1, 0}, {1, 0}, {2, 0}, {2, 0}, {2, 2}, {3, 3}};
        for (int rank = 0; rank <= 9; rank++) {
            helper.assertTrue(MatrixResonance.level(rank, MatrixResonance.CHAOS) == expect[rank][0], rank + " 阶混沌相性应为 " + expect[rank][0]);
            helper.assertTrue(MatrixResonance.level(rank, MatrixResonance.FROZEN) == expect[rank][0], rank + " 阶冰冻相性应同混沌");
            helper.assertTrue(MatrixResonance.level(rank, MatrixResonance.SHADE) == expect[rank][1], rank + " 阶暗影相性应为 " + expect[rank][1]);
            helper.assertTrue(MatrixResonance.level(rank, MatrixResonance.NEUTRAL) == 0, "中性没有相性");
        }
        helper.assertTrue(MatrixResonance.level(9, 7) == 0, "未知属性没有相性");
        helper.assertTrue(MatrixResonance.traitMultiplier(1) == 1D && MatrixResonance.traitMultiplier(2) == 1.25D
                && MatrixResonance.traitMultiplier(3) == 1.5D && MatrixResonance.traitMultiplier(0) == 0D, "特性强度 ×1 / ×1.25 / ×1.5，没有相性 0");
        helper.succeed();
    }

    /** 魔力相性：耗 MRU ×0.9 / 0.85 / 0.8（MRUDrain 先打折再扣），虹吸上限 ×1.25 / 1.5 / 2；别的属性原价 */
    @GameTest(template = EMPTY)
    public static void alignmentMagicCostAndSiphonCap(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-mc");
        double[] costs = {1D, 0.9D, 0.85D, 0.8D};
        int[] caps = {2000, 2500, 3000, 4000};
        int[] ranks = {2, 3, 6, 9};
        for (int i = 0; i < ranks.length; i++) {
            matrix(player, ranks[i], MatrixResonance.MAGIC);
            helper.assertTrue(Math.abs(MatrixResonance.costMultiplier(player) - costs[i]) < 1.0E-9D, ranks[i] + " 阶魔力：耗电倍数应为 " + costs[i]);
            helper.assertTrue(MatrixSiphon.cap(player) == caps[i], ranks[i] + " 阶魔力：虹吸上限应为 " + caps[i] + "，实际 " + MatrixSiphon.cap(player));
        }
        helper.assertTrue(MRUDrain.discounted(player, 50) == 40 && MRUDrain.discounted(player, 1) == 1, "九阶魔力：50 → 40，1 不折成 0");
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        helper.assertTrue(MRUDrain.playerUseMRU(player, ItemStack.EMPTY, 100) && mru(storage) == 920, "付 100 实扣 80，剩 " + mru(storage));
        helper.assertTrue(MRUDrain.playerUseMRUExact(player, ItemStack.EMPTY, 100) && mru(storage) == 820, "护盾那一路按原价扣，剩 " + mru(storage));
        matrix(player, 9, MatrixResonance.CHAOS);
        helper.assertTrue(MatrixResonance.costMultiplier(player) == 1D && MatrixSiphon.cap(player) == 2000, "混沌矩阵：原价、虹吸上限 2000");
        helper.succeed();
    }

    /** 躁动：近战、自己射出的箭 +10%（II）；魔法、荆棘不加；打玩家时减半；中性玩家不加 */
    @GameTest(template = EMPTY)
    public static void alignmentChaosBonus(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-cb");
        ServerPlayer other = player(helper, "ec5-ma-cb2");
        Zombie zombie = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        matrix(player, 6, MatrixResonance.CHAOS);
        DamageSource melee = player.damageSources().playerAttack(player);
        Arrow arrow = new Arrow(helper.getLevel(), player);
        DamageSource shot = player.damageSources().arrow(arrow, player);
        helper.assertTrue(near(MatrixResonance.chaosBonus(melee, zombie), 0.10D), "近战 +10%");
        helper.assertTrue(near(MatrixResonance.chaosBonus(shot, zombie), 0.10D), "自己射出的箭 +10%");
        helper.assertTrue(MatrixResonance.chaosBonus(player.damageSources().indirectMagic(player, player), zombie) == 0D, "魔法不加");
        helper.assertTrue(MatrixResonance.chaosBonus(player.damageSources().thorns(player), zombie) == 0D, "荆棘不加");
        helper.assertTrue(near(MatrixResonance.chaosBonus(melee, other), 0.05D), "打玩家时减半 +5%");
        matrix(player, 6, MatrixResonance.NEUTRAL);
        helper.assertTrue(MatrixResonance.chaosBonus(melee, zombie) == 0D, "中性不加");

        // 事件里实际乘上：同一下 4 点，混沌 II 读到 4.4
        matrix(player, 6, MatrixResonance.CHAOS);
        float[] seen = new float[1];
        Runnable off = listen(EventPriority.LOWEST, LivingHurtEvent.class, event -> {
            if (event.getEntity() == zombie) {
                seen[0] = event.getAmount();
            }
        });
        try {
            zombie.hurt(player.damageSources().playerAttack(player), 4F);
        } finally {
            off.run();
            MatrixTraits.flush();
        }
        helper.assertTrue(Math.abs(seen[0] - 4.4F) < EPS, "LivingHurtEvent 里应变成 4.4，实际 " + seen[0]);
        helper.succeed();
    }

    /** 凝滞：近战 −5%（I），无视护甲、弹射物不减；挨打以后攻击者冰冻刻 +30，弹射物的射手不冻 */
    @GameTest(template = EMPTY)
    public static void alignmentFrozenReducesAndRetaliates(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-fr");
        Zombie zombie = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie archer = zombie(helper, new Vec3(1.5D, 1D, 3.5D));
        matrix(player, 3, MatrixResonance.FROZEN);
        DamageSource melee = player.damageSources().mobAttack(zombie);
        helper.assertTrue(near(MatrixResonance.frozenReduction(melee, player), 0.05D), "近战 −5%");
        helper.assertTrue(MatrixResonance.frozenReduction(player.damageSources().sonicBoom(zombie), player) == 0D, "无视护甲的不减");
        Arrow arrow = new Arrow(helper.getLevel(), archer);
        helper.assertTrue(MatrixResonance.frozenReduction(player.damageSources().arrow(arrow, archer), player) == 0D, "弹射物不减");

        MatrixTraits.flush();
        player.hurt(melee, 4F);
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().arrow(arrow, archer), 4F);
        MatrixTraits.flush();
        helper.assertTrue(zombie.getTicksFrozen() == 30, "近战打实的僵尸冰冻刻应 +30，实际 " + zombie.getTicksFrozen());
        helper.assertTrue(archer.getTicksFrozen() == 0, "射箭的不冻，实际 " + archer.getTicksFrozen());
        helper.succeed();
    }

    /** 吞噬：八阶 II 有攻击者的魔法 −10%、九阶 III −15%；没有攻击者的魔法（腐化自伤）不减；七阶暗影还没有 */
    @GameTest(template = EMPTY)
    public static void alignmentShadeReducesMagic(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-sh");
        Zombie zombie = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        DamageSource magic = player.damageSources().indirectMagic(zombie, zombie);
        DamageSource self = player.damageSources().magic();
        matrix(player, 7, MatrixResonance.SHADE);
        helper.assertTrue(MatrixResonance.shadeReduction(magic, player) == 0D, "七阶暗影还没有相性");
        matrix(player, 8, MatrixResonance.SHADE);
        helper.assertTrue(near(MatrixResonance.shadeReduction(magic, player), 0.10D), "八阶 II −10%");
        helper.assertTrue(MatrixResonance.shadeReduction(self, player) == 0D, "没有攻击者的魔法不减");
        helper.assertTrue(MatrixResonance.shadeReduction(player.damageSources().mobAttack(zombie), player) == 0D, "物理伤害不减");
        matrix(player, 9, MatrixResonance.SHADE);
        helper.assertTrue(near(MatrixResonance.shadeReduction(magic, player), 0.15D), "九阶 III −15%");
        helper.assertTrue(player.damageSources().magic().getEntity() == null && !self.is(ECTags.UNSHIELDABLE), "前提：腐化用的 magic() 没有攻击者");
        helper.succeed();
    }

    // ================================================================== 矩阵特性

    /** 混沌特性：元素剑打实一下，僵尸挂 60 刻腐蚀（I）、扣 50；九阶 90 刻；神圣重锤九阶 180 刻（×2） */
    @GameTest(template = EMPTY)
    public static void traitChaosCorrodesAndScales(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-tc");
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        int[] ranks = {3, 9, 9};
        Item[] weapons = {ECGear.ELEMENTAL_SWORD.get(), ECGear.ELEMENTAL_SWORD.get(), ECStaffs.HOLY_MACE.get()};
        int[] ticks = {60, 90, 180};
        for (int i = 0; i < ranks.length; i++) {
            Zombie zombie = zombie(helper, new Vec3(2.5D, 1D, 1.5D + i));
            matrix(player, ranks[i], MatrixResonance.CHAOS);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapons[i]));
            int before = mru(storage);
            MatrixTraits.flush();
            zombie.hurt(player.damageSources().playerAttack(player), 2F);
            helper.assertTrue(MatrixTraits.pendingCount() >= 1, "打实应先记下");
            helper.assertFalse(zombie.hasEffect(ECEffects.CORROSION.get()), "刻末结算之前还没有效果");
            MatrixTraits.flush();
            MobEffectInstance corrosion = zombie.getEffect(ECEffects.CORROSION.get());
            helper.assertTrue(corrosion != null && corrosion.getDuration() == ticks[i], "第 " + i + " 组应挂 " + ticks[i] + " 刻腐蚀，实际 " + corrosion);
            int paid = before - mru(storage);
            helper.assertTrue(paid == 50, "每次触发扣 50，实际 " + paid);
        }
        helper.succeed();
    }

    /** 没有相性（二阶）、不带标签的武器（铁剑）都不触发、不扣 */
    @GameTest(template = EMPTY)
    public static void traitNeedsAlignmentAndTag(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-tn");
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        Zombie first = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie second = zombie(helper, new Vec3(1.5D, 1D, 3.5D));
        helper.assertTrue(new ItemStack(ECGear.ELEMENTAL_SWORD.get()).is(ECTags.MATRIX_ATTUNED)
                && new ItemStack(ECStaffs.FROZEN_MACE.get()).is(ECTags.MATRIX_ATTUNED), "前提：元素剑、冰冻重锤在 matrix_attuned 里");
        MatrixTraits.flush();
        matrix(player, 2, MatrixResonance.CHAOS);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
        first.hurt(player.damageSources().playerAttack(player), 2F);
        matrix(player, 9, MatrixResonance.CHAOS);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        second.hurt(player.damageSources().playerAttack(player), 2F);
        MatrixTraits.flush();
        helper.assertFalse(first.hasEffect(ECEffects.CORROSION.get()), "二阶没有相性，不触发");
        helper.assertFalse(second.hasEffect(ECEffects.CORROSION.get()), "铁剑不带矩阵特性");
        helper.assertTrue(mru(storage) == 1000, "都不该扣，剩 " + mru(storage));
        helper.succeed();
    }

    /** 冰冻特性：冰冻刻 +40；对冻僵的目标这一下 +10%（LivingHurtEvent 里 4 → 4.4） */
    @GameTest(template = EMPTY)
    public static void traitFrozenFreezesAndBonus(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-tf");
        player.getInventory().setItem(9, storage(1000));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
        matrix(player, 3, MatrixResonance.FROZEN);
        Zombie zombie = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie frozen = zombie(helper, new Vec3(1.5D, 1D, 3.5D));
        frozen.setTicksFrozen(frozen.getTicksRequiredToFreeze());
        float[] seen = new float[2];
        Runnable off = listen(EventPriority.LOWEST, LivingHurtEvent.class, event -> {
            if (event.getEntity() == zombie) {
                seen[0] = event.getAmount();
            } else if (event.getEntity() == frozen) {
                seen[1] = event.getAmount();
            }
        });
        try {
            MatrixTraits.flush();
            zombie.hurt(player.damageSources().playerAttack(player), 4F);
            frozen.hurt(player.damageSources().playerAttack(player), 4F);
            MatrixTraits.flush();
        } finally {
            off.run();
        }
        helper.assertTrue(zombie.getTicksFrozen() == 40, "冰冻刻应 +40，实际 " + zombie.getTicksFrozen());
        helper.assertTrue(Math.abs(seen[0] - 4F) < EPS, "没冻僵的不加，实际 " + seen[0]);
        helper.assertTrue(Math.abs(seen[1] - 4.4F) < EPS, "冻僵的 +10%，实际 " + seen[1]);
        helper.succeed();
    }

    /** 混沌特性的增伤：挂着腐蚀的目标 +10%；身上没有 MRU 时不加、也不触发，伤害照打 */
    @GameTest(template = EMPTY)
    public static void traitChaosBonusNeedsMru(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-tm");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
        matrix(player, 3, MatrixResonance.CHAOS);
        Zombie zombie = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        zombie.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), 20, 0));
        float[] seen = new float[1];
        Runnable off = listen(EventPriority.LOWEST, LivingHurtEvent.class, event -> {
            if (event.getEntity() == zombie) {
                seen[0] = event.getAmount();
            }
        });
        try {
            MatrixTraits.flush();
            float before = zombie.getHealth();
            zombie.hurt(player.damageSources().playerAttack(player), 4F);
            MatrixTraits.flush();
            // 三阶混沌的相性本身 +5%，特性的 +10% 因为付不起而没有
            helper.assertTrue(Math.abs(seen[0] - 4.2F) < EPS, "付不起时只有相性的 +5%，实际 " + seen[0]);
            helper.assertTrue(zombie.getHealth() < before, "伤害照打");
            MobEffectInstance corrosion = zombie.getEffect(ECEffects.CORROSION.get());
            helper.assertTrue(corrosion != null && corrosion.getDuration() <= 20, "付不起不该续腐蚀，实际 " + corrosion);

            player.getInventory().setItem(9, storage(1000));
            zombie.invulnerableTime = 0;
            zombie.hurt(player.damageSources().playerAttack(player), 4F);
            MatrixTraits.flush();
            helper.assertTrue(Math.abs(seen[0] - 4F * 1.05F * 1.1F) < EPS, "付得起：相性 ×1.05、腐蚀中再 ×1.1，实际 " + seen[0]);
        } finally {
            off.run();
        }
        helper.succeed();
    }

    /** 魔力特性：剥掉一个增益效果；同一个目标 40 刻内不再剥；清掉冷却后又能剥 */
    @GameTest(template = EMPTY)
    public static void traitMagicDispelCooldown(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-td");
        player.getInventory().setItem(9, storage(1000));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
        matrix(player, 3, MatrixResonance.MAGIC);
        Zombie zombie = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
        zombie.addEffect(new MobEffectInstance(MobEffects.JUMP, 300, 0));
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 900, 0));
        MatrixTraits.clearDispelCooldowns();
        MatrixTraits.flush();
        zombie.hurt(player.damageSources().playerAttack(player), 2F);
        MatrixTraits.flush();
        helper.assertTrue(!zombie.hasEffect(MobEffects.FIRE_RESISTANCE) && zombie.getActiveEffects().size() == 2, "先剥剩余最长的那个（抗火）");
        zombie.invulnerableTime = 0;
        zombie.hurt(player.damageSources().playerAttack(player), 2F);
        MatrixTraits.flush();
        helper.assertTrue(zombie.getActiveEffects().size() == 2, "40 刻内同一个目标不再剥");
        MatrixTraits.clearDispelCooldowns();
        zombie.invulnerableTime = 0;
        zombie.hurt(player.damageSources().playerAttack(player), 2F);
        MatrixTraits.flush();
        helper.assertTrue(zombie.getActiveEffects().size() == 1 && zombie.hasEffect(MobEffects.JUMP), "冷却过了再剥一个（迅捷）");
        helper.succeed();
    }

    /** 暗影特性：八阶 II（×1.25）吸取这一下最终伤害的 5% × 1.25 = 6.25% */
    @GameTest(template = EMPTY)
    public static void traitShadeDrains(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-ts");
        player.getInventory().setItem(9, storage(1000));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
        matrix(player, 8, MatrixResonance.SHADE);
        player.setHealth(10F);
        Zombie zombie = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        float[] dealt = new float[1];
        Runnable off = listen(EventPriority.LOWEST, LivingDamageEvent.class, event -> {
            if (event.getEntity() == zombie) {
                dealt[0] = event.getAmount();
            }
        });
        try {
            MatrixTraits.flush();
            zombie.hurt(player.damageSources().playerAttack(player), 8F);
            MatrixTraits.flush();
        } finally {
            off.run();
        }
        float healed = player.getHealth() - 10F;
        helper.assertTrue(dealt[0] > 0F && Math.abs(healed - dealt[0] * 0.0625F) < EPS, "应回 " + dealt[0] * 0.0625F + "，实际 " + healed);
        helper.succeed();
    }

    /**
     * 打实才给：攻击在 LivingAttackEvent 被拦下的不记；伤害事件被排在后面的监听器取消（LOWEST）的不结算；两下都不扣
     */
    @GameTest(template = EMPTY)
    public static void traitOnlyWhenLanded(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-tl");
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
        matrix(player, 3, MatrixResonance.CHAOS);
        Zombie guarded = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie late = zombie(helper, new Vec3(1.5D, 1D, 3.5D));
        Runnable offAttack = listen(EventPriority.NORMAL, LivingAttackEvent.class, event -> {
            if (event.getEntity() == guarded) {
                event.setCanceled(true);
            }
        });
        Runnable offDamage = listen(EventPriority.LOWEST, LivingDamageEvent.class, event -> {
            if (event.getEntity() == late) {
                event.setCanceled(true);
            }
        });
        int pendingAfterGuarded;
        try {
            MatrixTraits.flush();
            guarded.hurt(player.damageSources().playerAttack(player), 2F);
            pendingAfterGuarded = MatrixTraits.pendingCount();
            late.hurt(player.damageSources().playerAttack(player), 2F);
            MatrixTraits.flush();
        } finally {
            offAttack.run();
            offDamage.run();
        }
        helper.assertTrue(pendingAfterGuarded == 0, "攻击被拦下不该记，实际 " + pendingAfterGuarded);
        helper.assertFalse(guarded.hasEffect(ECEffects.CORROSION.get()) || late.hasEffect(ECEffects.CORROSION.get()), "两只都不该挂腐蚀");
        helper.assertTrue(mru(storage) == 1000, "两下都不该扣，剩 " + mru(storage));
        helper.succeed();
    }

    /** PvP：关着时打不到人、不给效果、不扣；开着时腐蚀 60 × 0.5 = 30 刻 */
    @GameTest(template = EMPTY)
    public static void traitPvpScaled(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer attacker = player(helper, "ec5-ma-pa");
        ServerPlayer victim = player(helper, "ec5-ma-pv");
        ItemStack storage = storage(1000);
        attacker.getInventory().setItem(9, storage);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
        matrix(attacker, 3, MatrixResonance.CHAOS);
        boolean pvp = server.isPvpAllowed();
        try {
            MatrixTraits.flush();
            server.setPvpAllowed(false);
            boolean hurtOff = victim.hurt(attacker.damageSources().playerAttack(attacker), 2F);
            MatrixTraits.flush();
            helper.assertFalse(hurtOff, "前提：PvP 关着时打不到人");
            helper.assertFalse(victim.hasEffect(ECEffects.CORROSION.get()), "PvP 关着不给效果");
            helper.assertTrue(mru(storage) == 1000, "PvP 关着不扣，剩 " + mru(storage));

            server.setPvpAllowed(true);
            victim.invulnerableTime = 0;
            boolean hurtOn = victim.hurt(attacker.damageSources().playerAttack(attacker), 2F);
            MatrixTraits.flush();
            MobEffectInstance corrosion = victim.getEffect(ECEffects.CORROSION.get());
            helper.assertTrue(hurtOn && corrosion != null && corrosion.getDuration() == 30, "PvP 开着：对玩家腐蚀减半 30 刻，实际 " + corrosion);
        } finally {
            server.setPvpAllowed(pvp);
        }
        helper.succeed();
    }

    /** 同一刻对同一个生物的两下只结算一次、只扣一次；不同生物各算 */
    @GameTest(template = EMPTY)
    public static void traitOncePerTargetPerTick(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-to");
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
        matrix(player, 3, MatrixResonance.FROZEN);
        Zombie twice = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie other = zombie(helper, new Vec3(1.5D, 1D, 3.5D));
        MatrixTraits.flush();
        twice.hurt(player.damageSources().playerAttack(player), 2F);
        twice.invulnerableTime = 0;
        twice.hurt(player.damageSources().playerAttack(player), 3F);
        other.hurt(player.damageSources().playerAttack(player), 2F);
        int pending = MatrixTraits.pendingCount();
        int applied = MatrixTraits.flush();
        helper.assertTrue(pending == 3, "三下伤害都该先记下，实际 " + pending);
        helper.assertTrue(applied == 2, "每只只结算一次，实际 " + applied);
        helper.assertTrue(twice.getTicksFrozen() == 40 && other.getTicksFrozen() == 40, "两只各 +40，实际 " + twice.getTicksFrozen() + " / " + other.getTicksFrozen());
        helper.assertTrue(mru(storage) == 900, "各扣一次 50，剩 " + mru(storage));
        helper.succeed();
    }

    /** 透镜同型：混沌三阶的人用混沌透镜，腐蚀 60 × 1.5 = 90 刻；冰冻矩阵的人用混沌透镜照旧 60；打玩家时多出来的部分减半（×1.25） */
    @GameTest(template = EMPTY)
    public static void lensSameTypeBoost(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-ma-ls");
        ServerPlayer target = player(helper, "ec5-ma-lt");
        Zombie same = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie other = zombie(helper, new Vec3(1.5D, 1D, 3.5D));
        GunStats chaosLens = GunStats.of(GunType.PISTOL, GunParts.uniform("mru").withLens("chaos"));
        matrix(shooter, 3, MatrixResonance.CHAOS);
        RayShot.strike(shooter, same, chaosLens, NO_FX);
        matrix(shooter, 3, MatrixResonance.FROZEN);
        RayShot.strike(shooter, other, chaosLens, NO_FX);
        MobEffectInstance a = same.getEffect(ECEffects.CORROSION.get());
        MobEffectInstance b = other.getEffect(ECEffects.CORROSION.get());
        helper.assertTrue(a != null && a.getDuration() == 90, "同型 ×1.5 → 90 刻，实际 " + a);
        helper.assertTrue(b != null && b.getDuration() == LensEffects.Config.corrosionTicks(), "不同型照旧，实际 " + b);
        matrix(shooter, 3, MatrixResonance.CHAOS);
        helper.assertTrue(near(MatrixResonance.lensMultiplier(shooter, target, LensEffects.CHAOS), 1.25D), "打玩家时 ×1.25");
        matrix(shooter, 2, MatrixResonance.CHAOS);
        helper.assertTrue(MatrixResonance.lensMultiplier(shooter, same, LensEffects.CHAOS) == 1D, "没有相性时 ×1");
        helper.succeed();
    }

    // ================================================================== 早期武器修补

    /** 弱化元素攻击加成 3（剑 7、斧 9）；混沌之叉攻击 12、攻速 2.0；冰冻重锤、神圣重锤不变 */
    @GameTest(template = EMPTY)
    public static void earlyWeaponNumbers(GameTestHelper helper) {
        helper.assertTrue(ElementalTier.WEAKENED.getAttackDamageBonus() == 3F, "弱化元素攻击加成应为 3");
        helper.assertTrue(near(attack(new ItemStack(ECGear.WEAK_ELEMENTAL_SWORD.get())) + 1D, 7D), "弱化元素剑应为 7，实际 " + (attack(new ItemStack(ECGear.WEAK_ELEMENTAL_SWORD.get())) + 1D));
        helper.assertTrue(near(attack(new ItemStack(ECGear.WEAK_ELEMENTAL_AXE.get())) + 1D, 9D), "弱化元素斧应为 9");
        ItemStack fork = new ItemStack(ECStaffs.CHAOS_FORK.get());
        helper.assertTrue(near(attack(fork) + 1D, 12D), "混沌之叉攻击应为 12，实际 " + (attack(fork) + 1D));
        helper.assertTrue(near(speed(fork) + 4D, 2D), "混沌之叉攻速应为 2.0，实际 " + (speed(fork) + 4D));
        helper.assertTrue(near(attack(new ItemStack(ECStaffs.FROZEN_MACE.get())) + 1D, 17D) && near(attack(new ItemStack(ECStaffs.HOLY_MACE.get())) + 1D, 13D),
                "两把重锤攻击 17 / 13 不变");
        helper.assertTrue(near(speed(new ItemStack(ECStaffs.HOLY_MACE.get())) + 4D, 1.6D), "神圣重锤攻速 1.6 不变");
        helper.succeed();
    }

    /** 神圣重锤：打亡灵（僵尸）这一下 ×1.5，打牛不加；元素剑打僵尸不加 */
    @GameTest(template = EMPTY)
    public static void holyMaceUndeadBonus(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-hm");
        Zombie zombie = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(1.5D, 1D, 3.5D));
        float[] seen = new float[2];
        Runnable off = listen(EventPriority.LOWEST, LivingHurtEvent.class, event -> {
            if (event.getEntity() == zombie) {
                seen[0] = event.getAmount();
            } else if (event.getEntity() == cow) {
                seen[1] = event.getAmount();
            }
        });
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, MRUStaffItem.full(ECStaffs.HOLY_MACE.get()));
            zombie.hurt(player.damageSources().playerAttack(player), 4F);
            cow.hurt(player.damageSources().playerAttack(player), 4F);
            helper.assertTrue(Math.abs(seen[0] - 6F) < EPS, "神圣重锤打僵尸 4 → 6，实际 " + seen[0]);
            helper.assertTrue(Math.abs(seen[1] - 4F) < EPS, "打牛不加，实际 " + seen[1]);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
            zombie.invulnerableTime = 0;
            zombie.hurt(player.damageSources().playerAttack(player), 4F);
            helper.assertTrue(Math.abs(seen[0] - 4F) < EPS, "元素剑打僵尸不加，实际 " + seen[0]);
        } finally {
            off.run();
            MatrixTraits.flush();
        }
        helper.succeed();
    }

    /** 混沌之叉叠效果的上限 = weapon_max_effect_level（3）+ 相性级：六阶混沌（II）封顶 V 级 */
    @GameTest(template = EMPTY)
    public static void maceCapPlusAlignment(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-mx");
        Zombie target = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        ItemStack fork = MRUStaffItem.full(ECStaffs.CHAOS_FORK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, fork);
        matrix(player, 6, MatrixResonance.CHAOS);
        for (int i = 0; i < 9; i++) {
            fork.hurtEnemy(target, player);
        }
        MobEffectInstance fatigue = target.getEffect(MobEffects.DIG_SLOWDOWN);
        int expected = ECStaffs.Config.weaponMaxLevel() + 2 - 1;
        helper.assertTrue(MatrixWeaponItem.maxEffectLevel(2) == ECStaffs.Config.weaponMaxLevel() + 2, "上限 = 配置 + 相性级");
        helper.assertTrue(fatigue != null && fatigue.getAmplifier() == expected, "挖掘疲劳应封顶在放大器 " + expected + "，实际 " + fatigue);
        helper.succeed();
    }

    // ================================================================== 虹吸

    /** 四阶虹吸：抽 2000 进容量（聚集体 5000 → 3000）、矩阵损伤 100 ÷ 1.4 = 71、灵魂石冷却 200 刻，冷却中再抽不了 */
    @GameTest(template = EMPTY)
    public static void siphonDrawsIntoPool(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-sd");
        matrix(player, 4, MatrixResonance.NEUTRAL);
        IPlayerData data = PlayerDataEvents.get(player);
        data.setMatrixDamage(0);
        MRUPresenceEntity presence = presence(helper, 5000);
        Item stone = ECBootstrap.SOUL_STONE.get();
        try {
            helper.assertTrue(MatrixSiphon.canStart(helper.getLevel(), player), "前提：面前有聚集体、四阶，能开始按");
            MatrixSiphon.Result result = MatrixSiphon.siphon(player, stone);
            helper.assertTrue(result == MatrixSiphon.Result.OK, "应抽成，实际 " + result);
            helper.assertTrue(data.getMatrixPool() == 2000 && presence.getMRU() == 3000, "容量 2000、聚集体剩 3000，实际 " + data.getMatrixPool() + " / " + presence.getMRU());
            helper.assertTrue(data.getMatrixDamage() == 71, "矩阵损伤 100 ÷ 1.4 = 71，实际 " + data.getMatrixDamage());
            helper.assertTrue(player.getCooldowns().isOnCooldown(stone), "灵魂石应进冷却");
            helper.assertTrue(MatrixSiphon.siphon(player, stone) == MatrixSiphon.Result.COOLDOWN && data.getMatrixPool() == 2000, "冷却中不再抽");
        } finally {
            presence.discard();
        }
        helper.succeed();
    }

    /** 抽不了的几种：三阶、被稳定、那一格在别人领地里、容量满了、面前没有；都不扣、不冷却 */
    @GameTest(template = EMPTY)
    public static void siphonRules(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-sr");
        IPlayerData data = PlayerDataEvents.get(player);
        Item stone = ECBootstrap.SOUL_STONE.get();
        helper.assertTrue(MatrixSiphon.siphon(player, stone) == MatrixSiphon.Result.RANK, "零阶不能虹吸");
        matrix(player, 4, MatrixResonance.NEUTRAL);
        helper.assertTrue(MatrixSiphon.siphon(player, stone) == MatrixSiphon.Result.NO_TARGET, "面前没有聚集体");
        MRUPresenceEntity presence = presence(helper, 5000);
        try {
            matrix(player, 3, MatrixResonance.NEUTRAL);
            helper.assertTrue(MatrixSiphon.siphon(player, stone) == MatrixSiphon.Result.RANK && !MatrixSiphon.canStart(helper.getLevel(), player), "三阶不能虹吸");
            matrix(player, 4, MatrixResonance.NEUTRAL);
            presence.stabilize(200);
            helper.assertTrue(MatrixSiphon.siphon(player, stone) == MatrixSiphon.Result.STABILIZED, "被稳定的不抽");
            presence.discard();
            presence = presence(helper, 5000);
            ECProtection.forget(player.getUUID());
            try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyAt(BlockPos.containing(presence.position()))) {
                helper.assertTrue(MatrixSiphon.siphon(player, stone) == MatrixSiphon.Result.DENIED, "那一格动不了就不抽");
            } finally {
                // 权限探测按（玩家、维度、格子）缓存，领地撤掉以后要忘掉，不然下面还读到「不许」
                ECProtection.forget(player.getUUID());
            }
            data.setMatrixPool(MatrixPool.capacity(data), true);
            MatrixSiphon.Result full = MatrixSiphon.siphon(player, stone);
            helper.assertTrue(full == MatrixSiphon.Result.POOL_FULL, "容量满了不抽，实际 " + full);
            helper.assertTrue(presence.getMRU() == 5000 && !player.getCooldowns().isOnCooldown(stone) && data.getMatrixDamage() == 0,
                    "都不该抽、不该冷却、不该挨损伤");
        } finally {
            presence.discard();
        }
        helper.succeed();
    }

    /** 灵魂石潜行右键：二阶起开关换入（不解绑）；零阶照旧解绑；四阶对着聚集体开始按住（不开关） */
    @GameTest(template = EMPTY)
    public static void soulStoneSneakUse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "ec5-ma-ss");
        IPlayerData data = PlayerDataEvents.get(player);
        ItemStack stone = new ItemStack(ECBootstrap.SOUL_STONE.get());
        SoulStoneItem.bind(stone, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        player.setShiftKeyDown(true);

        matrix(player, 2, MatrixResonance.NEUTRAL);
        boolean before = data.isMatrixPoolAbsorb();
        stone.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(data.isMatrixPoolAbsorb() != before, "二阶：潜行右键开关换入");
        helper.assertTrue(player.getUUID().equals(SoulStoneItem.getOwner(stone)), "二阶：不解绑");

        MRUPresenceEntity presence = presence(helper, 5000);
        try {
            matrix(player, 4, MatrixResonance.NEUTRAL);
            boolean absorb = data.isMatrixPoolAbsorb();
            stone.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.isUsingItem() && data.isMatrixPoolAbsorb() == absorb, "四阶对着聚集体：开始按住，不开关");
            player.stopUsingItem();
        } finally {
            presence.discard();
        }

        matrix(player, 0, MatrixResonance.NEUTRAL);
        stone.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(SoulStoneItem.getOwner(stone) == null, "零阶：潜行右键照旧解绑");
        helper.succeed();
    }

    // ================================================================== 审查修补（13a）

    /** 凝滞：监守者音爆、守卫者激光这类直接实体是怪本身的无视护甲 / 魔法伤害不算近战，不减、不反冻；僵尸近战照样冻 +60（III） */
    @GameTest(template = EMPTY)
    public static void alignmentFrozenRetaliationMeleeOnly(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-fm");
        matrix(player, 9, MatrixResonance.FROZEN);
        Warden warden = helper.spawnWithNoFreeWill(EntityType.WARDEN, new Vec3(2.5D, 1D, 2.5D));
        Guardian guardian = helper.spawnWithNoFreeWill(EntityType.GUARDIAN, new Vec3(1.5D, 1D, 3.5D));
        Zombie zombie = zombie(helper, new Vec3(3.5D, 1D, 1.5D));
        DamageSource boom = player.damageSources().sonicBoom(warden);
        DamageSource laser = player.damageSources().indirectMagic(guardian, guardian);
        DamageSource melee = player.damageSources().mobAttack(zombie);
        helper.assertTrue(boom.getDirectEntity() == warden && laser.getDirectEntity() == guardian, "前提：两种伤害的直接实体都是怪本身");
        helper.assertFalse(MatrixResonance.frozenMeleeHit(boom, player) || MatrixResonance.frozenMeleeHit(laser, player), "音爆、激光不算近战");
        helper.assertTrue(MatrixResonance.frozenMeleeHit(melee, player), "僵尸近战算");
        helper.assertTrue(MatrixResonance.frozenReduction(laser, player) == 0D && near(MatrixResonance.frozenReduction(melee, player), 0.15D),
                "激光不减、近战 −15%");
        MatrixTraits.flush();
        player.hurt(boom, 2F);
        player.invulnerableTime = 0;
        player.hurt(laser, 2F);
        player.invulnerableTime = 0;
        player.hurt(melee, 2F);
        MatrixTraits.flush();
        helper.assertTrue(warden.getTicksFrozen() == 0 && guardian.getTicksFrozen() == 0,
                "音爆、激光不反冻，实际 " + warden.getTicksFrozen() + " / " + guardian.getTicksFrozen());
        helper.assertTrue(zombie.getTicksFrozen() == 60, "近战打实的僵尸 +60，实际 " + zombie.getTicksFrozen());
        helper.succeed();
    }

    /**
     * 特性增伤给了就扣钱：目标身上已有更长的腐蚀（效果加不上）照扣一次、算一次 BONUS；效果加上了也只扣一次；
     * 打死目标的那一下照扣；伤害事件被取消的那一下不扣
     */
    @GameTest(template = EMPTY)
    public static void traitBonusChargedWithoutEffect(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-tb");
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
        matrix(player, 3, MatrixResonance.CHAOS);
        Zombie longer = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie shorter = zombie(helper, new Vec3(1.5D, 1D, 3.5D));
        Zombie dying = zombie(helper, new Vec3(3.5D, 1D, 1.5D));
        Zombie canceled = zombie(helper, new Vec3(3.5D, 1D, 3.5D));
        longer.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), 200, 0));
        shorter.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), 20, 0));
        dying.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), 200, 0));
        canceled.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), 200, 0));
        dying.setHealth(1F);
        float[] seen = new float[1];
        Runnable offHurt = listen(EventPriority.LOWEST, LivingHurtEvent.class, event -> {
            if (event.getEntity() == longer) {
                seen[0] = event.getAmount();
            }
        });
        Runnable offDamage = listen(EventPriority.LOWEST, LivingDamageEvent.class, event -> {
            if (event.getEntity() == canceled) {
                event.setCanceled(true);
            }
        });
        try {
            MatrixTraits.flush();
            longer.hurt(player.damageSources().playerAttack(player), 4F);
            int applied = MatrixTraits.flush();
            helper.assertTrue(Math.abs(seen[0] - 4F * 1.05F * 1.1F) < EPS, "吃了相性 ×1.05 和特性 ×1.1，实际 " + seen[0]);
            MobEffectInstance kept = longer.getEffect(ECEffects.CORROSION.get());
            helper.assertTrue(kept != null && kept.getDuration() > 60, "已有更长的腐蚀，没被换成 60 刻，实际 " + kept);
            helper.assertTrue(applied == 1 && mru(storage) == 950, "效果没加上也照扣一次：结算 " + applied + "，剩 " + mru(storage));

            shorter.hurt(player.damageSources().playerAttack(player), 4F);
            MatrixTraits.flush();
            MobEffectInstance refreshed = shorter.getEffect(ECEffects.CORROSION.get());
            helper.assertTrue(refreshed != null && refreshed.getDuration() == 60 && mru(storage) == 900, "效果加上了只扣一次，剩 " + mru(storage));

            dying.hurt(player.damageSources().playerAttack(player), 4F);
            MatrixTraits.flush();
            helper.assertTrue(dying.isDeadOrDying() && mru(storage) == 850, "打死的那一下照扣，剩 " + mru(storage));

            canceled.hurt(player.damageSources().playerAttack(player), 4F);
            MatrixTraits.flush();
            helper.assertTrue(mru(storage) == 850, "伤害事件被取消的不扣，剩 " + mru(storage));
        } finally {
            offHurt.run();
            offDamage.run();
        }
        helper.succeed();
    }

    /**
     * 对玩家叠满时的总乘数（batch13.md §3.2 / §12.4）：特性 +10%、躁动 +15% 打玩家时各乘 pvp_effect_scale；
     * 九阶混沌拿元素剑打挂着腐蚀的玩家，LivingHurtEvent 里 4 → 4 × (1 + 0.15 × s) × (1 + 0.10 × s)
     */
    @GameTest(template = EMPTY)
    public static void traitPvpTotalMultiplier(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer attacker = player(helper, "ec5-ma-pm");
        ServerPlayer victim = player(helper, "ec5-ma-pn");
        attacker.getInventory().setItem(9, storage(1000));
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
        matrix(attacker, 9, MatrixResonance.CHAOS);
        Zombie zombie = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        zombie.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), 200, 0));
        victim.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), 200, 0));
        DamageSource source = attacker.damageSources().playerAttack(attacker);
        double scale = AttunementConfig.pvpEffectScale();
        helper.assertTrue(near(MatrixTraits.traitBonus(source, zombie), 0.10D), "打生物特性 +10%");
        helper.assertTrue(near(MatrixTraits.traitBonus(source, victim), 0.10D * scale), "打玩家特性 × " + scale);
        helper.assertTrue(near(MatrixResonance.chaosBonus(source, victim), 0.15D * scale), "打玩家躁动 × " + scale);
        float[] seen = new float[1];
        Runnable off = listen(EventPriority.LOWEST, LivingHurtEvent.class, event -> {
            if (event.getEntity() == victim) {
                seen[0] = event.getAmount();
            }
        });
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            MatrixTraits.flush();
            victim.hurt(source, 4F);
            MatrixTraits.flush();
        } finally {
            off.run();
            server.setPvpAllowed(pvp);
        }
        double expected = 4D * (1D + 0.15D * scale) * (1D + 0.10D * scale);
        helper.assertTrue(Math.abs(seen[0] - expected) < EPS, "叠满的总乘数应为 " + expected / 4D + "，实际 " + seen[0] / 4F);
        helper.succeed();
    }

    /**
     * 两把重锤的相性加成只给目标身上的效果、只对不是玩家的目标：九阶混沌打僵尸挖掘疲劳封顶 上限 + 3，自己的力量仍封顶 上限；
     * 九阶冰冻打僵尸缓慢封顶 上限 + 3，打玩家封顶 上限
     */
    @GameTest(template = EMPTY)
    public static void maceAlignmentTargetsOnly(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-ma-mt");
        ServerPlayer victim = player(helper, "ec5-ma-mv");
        Zombie zombie = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie chilled = zombie(helper, new Vec3(1.5D, 1D, 3.5D));
        int base = ECStaffs.Config.weaponMaxLevel();
        ItemStack fork = MRUStaffItem.full(ECStaffs.CHAOS_FORK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, fork);
        matrix(player, 9, MatrixResonance.CHAOS);
        for (int i = 0; i < base + 4; i++) {
            fork.hurtEnemy(zombie, player);
        }
        MobEffectInstance fatigue = zombie.getEffect(MobEffects.DIG_SLOWDOWN);
        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        helper.assertTrue(fatigue != null && fatigue.getAmplifier() == base + 3 - 1, "僵尸的挖掘疲劳封顶放大器 " + (base + 2) + "，实际 " + fatigue);
        helper.assertTrue(strength != null && strength.getAmplifier() == base - 1, "自己的力量仍封顶放大器 " + (base - 1) + "，实际 " + strength);

        ItemStack mace = MRUStaffItem.full(ECStaffs.FROZEN_MACE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, mace);
        matrix(player, 9, MatrixResonance.FROZEN);
        for (int i = 0; i < base + 4; i++) {
            mace.hurtEnemy(chilled, player);
            mace.hurtEnemy(victim, player);
        }
        MobEffectInstance onMob = chilled.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        MobEffectInstance onPlayer = victim.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(onMob != null && onMob.getAmplifier() == base + 3 - 1, "僵尸的缓慢封顶放大器 " + (base + 2) + "，实际 " + onMob);
        helper.assertTrue(onPlayer != null && onPlayer.getAmplifier() == base - 1, "玩家的缓慢不加相性级，封顶放大器 " + (base - 1) + "，实际 " + onPlayer);
        helper.succeed();
    }

    /**
     * 灵魂石潜行右键只认主手：副手拿着自己的石头不开关、不解绑（三阶、零阶都一样）；
     * 四阶主手对着 5 格开外、6 格以内的聚集体，开始不了虹吸也不开关；面前没有聚集体时照常开关
     */
    @GameTest(template = EMPTY)
    public static void soulStoneMainHandOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "ec5-ma-sm");
        IPlayerData data = PlayerDataEvents.get(player);
        ItemStack stone = new ItemStack(ECBootstrap.SOUL_STONE.get());
        SoulStoneItem.bind(stone, player);
        player.setItemInHand(InteractionHand.OFF_HAND, stone);
        player.setShiftKeyDown(true);

        matrix(player, 3, MatrixResonance.NEUTRAL);
        boolean before = data.isMatrixPoolAbsorb();
        InteractionResultHolder<ItemStack> offhand = stone.use(level, player, InteractionHand.OFF_HAND);
        helper.assertTrue(offhand.getResult() == InteractionResult.PASS && data.isMatrixPoolAbsorb() == before, "副手：不开关，返回 PASS");
        helper.assertTrue(player.getUUID().equals(SoulStoneItem.getOwner(stone)), "副手：不解绑");
        matrix(player, 0, MatrixResonance.NEUTRAL);
        stone.use(level, player, InteractionHand.OFF_HAND);
        helper.assertTrue(player.getUUID().equals(SoulStoneItem.getOwner(stone)), "零阶副手也不解绑（一阶起副手拿着看聚集体，免得误触）");

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        matrix(player, 4, MatrixResonance.NEUTRAL);
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 1.5D, 6D));
        MRUPresenceEntity edge = MRUPresenceEntity.create(level, at.x, at.y, at.z, 5000, 1F, true, true);
        level.addFreshEntity(edge);
        try {
            double distance = edge.distanceTo(player);
            helper.assertTrue(distance > MatrixSiphon.REACH && distance <= MatrixSiphon.REACH + 1D && !MatrixSiphon.canStart(level, player),
                    "前提：聚集体在 5~6 格之间、开始不了虹吸，实际 " + distance);
            boolean absorb = data.isMatrixPoolAbsorb();
            stone.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(!player.isUsingItem() && data.isMatrixPoolAbsorb() == absorb, "边缘：不按住、也不开关");
        } finally {
            edge.discard();
        }
        boolean absorb = data.isMatrixPoolAbsorb();
        stone.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(data.isMatrixPoolAbsorb() != absorb, "面前没有聚集体：主手照常开关");
        helper.succeed();
    }

    /** 护甲破坏者磨护盾盔甲按原价扣，九阶魔力（相性 III）也不打折：虚空胸甲 5 000 → 5 000 − 5 × 单价 */
    @GameTest(template = EMPTY)
    public static void armorDestroyerNoMagicDiscount(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerData data = PlayerDataEvents.get(player);
        data.setMatrixRank(9);
        data.setMatrixType(MatrixResonance.MAGIC);
        helper.assertTrue(MatrixResonance.costMultiplier(player) < 1D, "前提：九阶魔力有折扣");
        ItemStack chest = new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get());
        chest.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(5000));
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        int per = ((IMRUShieldArmor) chest.getItem()).mruPerDamage(chest);
        ArmorDestroyerEntity.wear(player);
        int expected = 5000 - ArmorDestroyerEntity.WEAR * per;
        helper.assertTrue(per > 0 && mru(chest) == expected, "按原价扣到 " + expected + "，实际 " + mru(chest));
        helper.succeed();
    }

    // ================================================================== 工具

    /** 能挨打的真玩家（出生无敌只在 ServerPlayer.tick 里减，先空跑 61 次），生存模式、满血、站在 (0.5, 1, 0.5) 朝南 */
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 1D, 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setYHeadRot(0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void matrix(ServerPlayer player, int rank, int type) {
        IPlayerData data = PlayerDataEvents.get(player);
        data.setMatrixRank(rank);
        data.setMatrixType(type);
    }

    /** 200 血、没有护甲的僵尸（伤害读数不被护甲折掉），不会动 */
    private static Zombie zombie(GameTestHelper helper, Vec3 relative) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, relative);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0D);
        zombie.setHealth(200F);
        return zombie;
    }

    /** 玩家正前方 3 格的聚集体（遗迹那种归零也保留的，免得测试中途消失） */
    private static MRUPresenceEntity presence(GameTestHelper helper, int mru) {
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 1.5D, 3.5D));
        MRUPresenceEntity presence = MRUPresenceEntity.create(helper.getLevel(), at.x, at.y, at.z, mru, 1F, true, true);
        helper.getLevel().addFreshEntity(presence);
        return presence;
    }

    private static ItemStack storage(int mru) {
        ItemStack stack = new ItemStack(ECItems.DARK_SOUL_MATTER.get());
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    private static int mru(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
    }

    private static double attack(ItemStack stack) {
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream().mapToDouble(AttributeModifier::getAmount).sum();
    }

    private static double speed(ItemStack stack) {
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED).stream().mapToDouble(AttributeModifier::getAmount).sum();
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0E-6D;
    }

    private static <E extends Event> Runnable listen(EventPriority priority, Class<E> type, Consumer<E> listener) {
        MinecraftForge.EVENT_BUS.addListener(priority, false, type, listener);
        return () -> MinecraftForge.EVENT_BUS.unregister(listener);
    }
}
