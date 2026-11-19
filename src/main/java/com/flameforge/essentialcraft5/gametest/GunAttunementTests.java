package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.gun.GunAttunement;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.gun.RayShot;
import com.flameforge.essentialcraft5.item.GunItem;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.module.combat.AccumulatorEvents;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe.Counted;
import com.flameforge.essentialcraft5.recipe.AttunementTransferRecipe;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECGuns;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 枪械同调（家族 {@code mru_gun}，tools/plan/gear_growth.md §4.3、§9）的 GameTest：射线给同调度、每发 MRU 折扣、装填后冷却缩减、
 * 2 阶弹道累积、4 阶过载射线（充能、就绪 / 爆发事件、爆发范围和伤害、上限、清零）、同家族转移和剑枪不能互转。
 * <p>
 * 枪的同调阶用 {@link Attunement#set} 直接写；枪进家族靠标签 {@code essentialcraft5:attunement/mru_gun}（datagen 生成，先跑 runData）。
 * 射手和靶子的站位同 {@code GunsTests}（模板 {@code empty_large}：射手脚在 (5.5, 1, 1.5) 朝 +z，靶子在 x = 5.5 这条线上，z = 10 立挡墙）。
 * 靶子都在同一刻里打完、discard，不 tick（白天不会烧）；要连打同一只的，每发之前清掉受击无敌。
 * 改配置一律用 {@link GearGrowthConfig#setTestOverride}，在 finally 里 {@link GearGrowthConfig#clearTestOverrides}；事件测试出口同样在 finally 里摘掉。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GunAttunementTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final Vec3 SHOOTER_FEET = new Vec3(5.5D, 1D, 1.5D);
    private static final int LANE_X = 5;
    private static final float EPS = 0.01F;
    private static final float DUMMY_HEALTH = 1000F;

    private GunAttunementTests() {
    }

    // ------------------------------------------------------------------ 工具

    /** 带满武器预算的真玩家，脚在 relativeFeet、朝 +z */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家应挂着玩家数据");
        }
        data.setAttunementBudget(IPlayerData.BudgetKind.WEAPON, AttunementConfig.budgetCap(), true);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 魔力锭部件的枪，装 mru，同调到 stage 阶 */
    private static ItemStack gun(GunType type, int stage, int mru) {
        ItemStack stack = GunItem.create(type, GunParts.uniform("mru"), mru);
        if (stack.isEmpty()) {
            throw new IllegalStateException("枪没注册：" + type.itemId());
        }
        if (stage > 0) {
            Attunement.set(stack, stage, 0);
        }
        return stack;
    }

    private static int mruOf(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandlerItem::getMRU).orElse(-1);
    }

    /** 散布改成 0 的属性（射线不靠随机） */
    private static GunStats steady(ItemStack stack) {
        GunStats base = GunStats.of(stack).orElseThrow();
        return new GunStats(base.damage(), base.durability(), base.reload(), base.recoil(), base.speed(), 0F, base.shots(), base.zoom(),
                base.balance());
    }

    /** 本模组枪的射线（每次新建一个伤害源实例，同 RayShot） */
    private static DamageSource ray(ServerPlayer shooter) {
        return new DamageSource(ECDamageTypes.holder(shooter.level(), ECGuns.MRU_RAY), null, shooter);
    }

    /** 关 AI、最大生命 1000、护甲 0 的僵尸，脚在 relativeFeet */
    private static Zombie dummyZombie(GameTestHelper helper, Vec3 relativeFeet) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new IllegalStateException("建不出僵尸");
        }
        Vec3 at = helper.absoluteVec(relativeFeet);
        zombie.moveTo(at.x, at.y, at.z, 180F, 0F);
        zombie.setNoAi(true);
        AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(DUMMY_HEALTH);
        }
        AttributeInstance armor = zombie.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(0D);
        }
        zombie.setHealth(DUMMY_HEALTH);
        level.addFreshEntity(zombie);
        return zombie;
    }

    /** 最大生命 1000 的猪（累加器测试的靶子） */
    private static Pig dummyPig(GameTestHelper helper, BlockPos pos) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, pos);
        AttributeInstance health = pig.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(DUMMY_HEALTH);
        }
        pig.setHealth(DUMMY_HEALTH);
        return pig;
    }

    private static float lost(LivingEntity entity) {
        return DUMMY_HEALTH - entity.getHealth();
    }

    /** z = 10 的挡墙（x 0~10、y 1~4）：光束停在本测试里 */
    private static void backstop(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 10), Blocks.STONE);
            }
        }
    }

    /** 光束出口：测试玩家不在玩家列表里，不发包 */
    private static final RayShot.BeamSink NO_BEAM = (level, shooter, from, to, balance) -> {
    };

    /** 走 GunItem.shoot 开一枪（扣 MRU、计数、射线、过载都照正式的走），固定种子 */
    private static void fire(ServerPlayer player, ItemStack stack, GunType type, GunStats stats) {
        if (!GunItem.shoot(player, stack, type, stats, RandomSource.create(1L), false, NO_BEAM)) {
            throw new IllegalStateException("没打出去（MRU 不够？）");
        }
    }

    // ------------------------------------------------------------------ 攒点

    /** 主手同调枪：本人的射线打实正在追你的尸壳给 1、击杀给 2；主手是剑时射线不给，主手是枪时近战不给 */
    @GameTest(template = EMPTY)
    public static void gunRayGrantsAttunement(GameTestHelper helper) {
        ServerPlayer player = player(helper, "gunatt-gain", new Vec3(0.5D, 1D, 0.5D));
        try {
            ItemStack pistol = gun(GunType.PISTOL, 0, 5000);
            helper.assertTrue(AttunementFamily.of(pistol) == ECAttunement.MRU_GUN, "手枪应在 mru_gun 家族里（标签 attunement/mru_gun，先跑 runData）");
            player.setItemSlot(EquipmentSlot.MAINHAND, pistol);
            AttunementGains.setTestTime(1000L);
            Husk chasing = EntityType.HUSK.create(helper.getLevel());
            helper.assertTrue(chasing != null, "造不出尸壳");
            chasing.moveTo(helper.absoluteVec(new Vec3(1.5D, 1D, 1.5D)));
            chasing.setTarget(player);
            helper.assertTrue(AttunementGains.weaponHit(player, chasing, ray(player)) == AttunementConfig.pointsHit(),
                    "主手同调枪的射线打实追着你的怪：给 points_hit");
            Husk second = EntityType.HUSK.create(helper.getLevel());
            second.moveTo(helper.absoluteVec(new Vec3(1.5D, 1D, 1.5D)));
            second.setTarget(player);
            helper.assertTrue(AttunementGains.weaponHit(player, second, player.damageSources().playerAttack(player)) == 0F,
                    "拿着枪去砍（近战）不给");
            AttunementGains.setTestTime(1100L);
            Husk victim = EntityType.HUSK.create(helper.getLevel());
            victim.moveTo(helper.absoluteVec(new Vec3(1.5D, 1D, 1.5D)));
            victim.setTarget(player);
            helper.assertTrue(AttunementGains.weaponKill(player, victim, ray(player)) == AttunementConfig.pointsKill(), "射线击杀给 points_kill");

            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
            AttunementGains.setTestTime(1200L);
            Husk swordCase = EntityType.HUSK.create(helper.getLevel());
            swordCase.moveTo(helper.absoluteVec(new Vec3(1.5D, 1D, 1.5D)));
            swordCase.setTarget(player);
            helper.assertTrue(AttunementGains.weaponHit(player, swordCase, ray(player)) == 0F, "主手不是枪（剑）时射线不给");
            helper.assertTrue(AttunementGains.weaponHit(player, swordCase, player.damageSources().playerAttack(player)) == AttunementConfig.pointsHit(),
                    "对照：剑近战照旧给");
        } finally {
            AttunementGains.setTestTime(-1L);
            AttunementGains.forget(player);
            AttunementGains.forgetTypeCounts(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ MRU、冷却

    /** 1 阶每发 MRU −10%（手枪 133 → 120，加特林 26 → 23；提示框、开火同一个数）；3 阶装填后冷却 60 → 48 刻 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunStageMruAndReload(GameTestHelper helper) {
        helper.setBlock(new BlockPos(LANE_X, 2, 2), Blocks.STONE);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "gunatt-mru", SHOOTER_FEET);
        try {
            ItemStack plain = gun(GunType.PISTOL, 0, 5000);
            GunStats stats = GunStats.of(plain).orElseThrow();
            helper.assertTrue(GunAttunement.mruPerShot(plain, GunType.PISTOL, stats) == 133, "0 阶手枪每发 133");
            ItemStack stage1 = gun(GunType.PISTOL, 1, 5000);
            helper.assertTrue(GunAttunement.mruPerShot(stage1, GunType.PISTOL, stats) == 120,
                    "1 阶手枪每发 133 × 0.9 ≈ 120，实际 " + GunAttunement.mruPerShot(stage1, GunType.PISTOL, stats));
            ItemStack gatling = gun(GunType.GATLING, 1, 5000);
            helper.assertTrue(GunAttunement.mruPerShot(gatling, GunType.GATLING, GunStats.of(gatling).orElseThrow()) == 23, "1 阶加特林每发 26 × 0.9 ≈ 23");

            player.setItemInHand(InteractionHand.MAIN_HAND, stage1);
            stage1.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(GunParts.fired(stage1) == 1 && mruOf(stage1) == 5000 - 120, "1 阶手枪开一发扣 120，剩 " + mruOf(stage1));

            helper.assertTrue(GunAttunement.reloadCooldown(plain) == GunItem.RELOAD_COOLDOWN && GunAttunement.reloadCooldown(gun(GunType.PISTOL, 2, 0)) == 60,
                    "0 / 2 阶装填后冷却照旧 60 刻");
            ItemStack stage3 = gun(GunType.PISTOL, 3, 5000);
            helper.assertTrue(GunAttunement.reloadCooldown(stage3) == 48, "3 阶装填后冷却 60 × 0.8 = 48，实际 " + GunAttunement.reloadCooldown(stage3));
            GunParts.setFired(stage3, stats.magazine());
            player.getCooldowns().removeCooldown(stage3.getItem());
            player.setItemInHand(InteractionHand.MAIN_HAND, stage3);
            stage3.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.isUsingItem(), "打空的枪右键应开始装填");
            stage3.finishUsingItem(level, player);
            player.stopUsingItem();
            for (int i = 0; i < 47; i++) {
                player.getCooldowns().tick();
            }
            helper.assertTrue(player.getCooldowns().isOnCooldown(stage3.getItem()), "47 刻后还在冷却");
            player.getCooldowns().tick();
            helper.assertFalse(player.getCooldowns().isOnCooldown(stage3.getItem()), "48 刻后冷却结束");
        } finally {
            AttunementGains.forget(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 弹道累积

    /**
     * 2 阶枪（不装累加器模块）：射线被受击无敌整下吞掉的那一下记账、不清无敌帧，下一发打实时补上（≥ 4 + 4）；1 阶枪不记
     */
    @GameTest(template = EMPTY)
    public static void gunAccumulateAtStage2(GameTestHelper helper) {
        ServerPlayer stage2 = player(helper, "gunatt-acc2", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer stage1 = player(helper, "gunatt-acc1", new Vec3(4.5D, 1D, 0.5D));
        stage2.setItemSlot(EquipmentSlot.MAINHAND, gun(GunType.GATLING, 2, 5000));
        stage1.setItemSlot(EquipmentSlot.MAINHAND, gun(GunType.GATLING, 1, 5000));
        Pig boosted = dummyPig(helper, new BlockPos(1, 1, 3));
        Pig normal = dummyPig(helper, new BlockPos(3, 1, 3));
        try {
            AccumulatorEvents.flush();
            for (Pig pig : List.of(boosted, normal)) {
                pig.invulnerableTime = 0;
                pig.hurt(ray(pig == boosted ? stage2 : stage1), 4F);
            }
            AccumulatorEvents.flush();
            int invulnBefore = boosted.invulnerableTime;
            boosted.hurt(ray(stage2), 4F);
            normal.hurt(ray(stage1), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(AccumulatorEvents.balance(stage2, boosted) == 4F, "2 阶：被吞掉的一下记账 4，实际 " + AccumulatorEvents.balance(stage2, boosted));
            helper.assertTrue(boosted.invulnerableTime == invulnBefore && invulnBefore > 10, "不清无敌帧：受击无敌还是 " + invulnBefore + "，实际 " + boosted.invulnerableTime);
            helper.assertTrue(AccumulatorEvents.balance(stage1, normal) == 0F, "1 阶不记账，实际 " + AccumulatorEvents.balance(stage1, normal));

            float before = lost(boosted);
            float beforeNormal = lost(normal);
            boosted.invulnerableTime = 0;
            normal.invulnerableTime = 0;
            boosted.hurt(ray(stage2), 4F);
            normal.hurt(ray(stage1), 4F);
            AccumulatorEvents.flush();
            float gained = lost(boosted) - before;
            float plain = lost(normal) - beforeNormal;
            helper.assertTrue(gained >= 8F - EPS, "2 阶下一发补上记的账：至少 4 + 4（再乘增伤池），实际 " + gained);
            helper.assertTrue(plain < 8F, "对照 1 阶没有补：不到 8，实际 " + plain);
            helper.assertTrue(AccumulatorEvents.balance(stage2, boosted) == 0F, "结算以后账清掉");
        } finally {
            AccumulatorEvents.forgetForTest(stage2);
            AccumulatorEvents.forgetForTest(stage1);
            AttunementGains.forget(stage2);
            AttunementGains.forget(stage1);
            boosted.discard();
            normal.discard();
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 过载射线

    /**
     * 4 阶手枪：打实 3 发充满（第 3 发发 OVERLOAD_READY，前两发不发）→ 第 4 发以第一个打实的目标为中心爆发：半径 3 内两只僵尸各吃
     * 这一发 × 60%，4 格外的不打；发 OVERLOAD_FIRE、充能清零。再把充满改成 1 发、最多 1 个目标：只打离中心最近的那只（主目标）。
     * 3 阶的枪打实不充能。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void gunOverloadBurst(GameTestHelper helper) {
        backstop(helper);
        ServerPlayer player = player(helper, "gunatt-ovl", SHOOTER_FEET);
        Zombie primary = dummyZombie(helper, new Vec3(LANE_X + 0.5D, 1D, 6.5D));
        Zombie left = dummyZombie(helper, new Vec3(LANE_X - 1.5D, 1D, 6.5D));
        Zombie right = dummyZombie(helper, new Vec3(LANE_X + 2.5D, 1D, 6.5D));
        Zombie far = dummyZombie(helper, new Vec3(LANE_X - 3.5D, 1D, 6.5D));
        List<S2CGearFxPacket> events = new ArrayList<>();
        int shooterId = player.getId();
        S2CGearFxPacket.setTestSink(packet -> {
            if (packet.entity() == shooterId) {
                events.add(packet);
            }
        });
        try {
            ItemStack stage3 = gun(GunType.PISTOL, 3, 5000);
            player.setItemSlot(EquipmentSlot.MAINHAND, stage3);
            primary.invulnerableTime = 0;
            fire(player, stage3, GunType.PISTOL, steady(stage3));
            helper.assertTrue(GunAttunement.chargeMax(stage3) == 0 && GunAttunement.charge(stage3) == 0, "3 阶没有过载：不充能");

            ItemStack pistol = gun(GunType.PISTOL, 4, 5000);
            player.setItemSlot(EquipmentSlot.MAINHAND, pistol);
            GunStats stats = steady(pistol);
            helper.assertTrue(GunAttunement.chargeMax(pistol) == GearGrowthConfig.overloadShotsSingle() && GunAttunement.chargeMax(pistol) == 3,
                    "4 阶手枪充满要 3 发，实际 " + GunAttunement.chargeMax(pistol));
            helper.assertTrue(GunAttunement.chargeMax(gun(GunType.RIFLE, 4, 0)) == 12, "4 阶步枪充满要 12 发");
            events.clear();
            for (int i = 1; i <= 3; i++) {
                primary.invulnerableTime = 0;
                fire(player, pistol, GunType.PISTOL, stats);
                helper.assertTrue(GunAttunement.charge(pistol) == i, "打实 " + i + " 发，充能应为 " + i + "，实际 " + GunAttunement.charge(pistol));
                long ready = events.stream().filter(e -> e.kind() == GearFx.OVERLOAD_READY).count();
                helper.assertTrue(ready == (i == 3 ? 1 : 0), "第 " + i + " 发后 OVERLOAD_READY 应有 " + (i == 3 ? 1 : 0) + " 个，实际 " + ready);
            }
            helper.assertTrue(GunAttunement.ready(pistol), "3 发后充满");
            S2CGearFxPacket readyEvent = events.stream().filter(e -> e.kind() == GearFx.OVERLOAD_READY).findFirst().orElseThrow();
            helper.assertTrue(Math.abs(readyEvent.param() - 3F) < EPS, "OVERLOAD_READY 的 param = 充满的发数 3，实际 " + readyEvent.param());

            float leftBefore = lost(left);
            float rightBefore = lost(right);
            float farBefore = lost(far);
            primary.invulnerableTime = 0;
            Vec3 center = primary.getBoundingBox().getCenter();
            fire(player, pistol, GunType.PISTOL, stats);
            float expected = (float) (stats.damage() * GearGrowthConfig.overloadRatio() * GearGrowthConfig.aoeScale());
            helper.assertTrue(Math.abs(lost(left) - leftBefore - expected) < EPS, "左边的僵尸吃这一发 × 60% = " + expected + "，实际 " + (lost(left) - leftBefore));
            helper.assertTrue(Math.abs(lost(right) - rightBefore - expected) < EPS, "右边的僵尸吃这一发 × 60% = " + expected + "，实际 " + (lost(right) - rightBefore));
            helper.assertTrue(lost(far) == farBefore, "4 格外的僵尸不打");
            helper.assertTrue(GunAttunement.charge(pistol) == 0, "爆发以后充能清零（放过载的这一发不再充）");
            List<S2CGearFxPacket> fires = events.stream().filter(e -> e.kind() == GearFx.OVERLOAD_FIRE).toList();
            helper.assertTrue(fires.size() == 1, "应发一个 OVERLOAD_FIRE，实际 " + fires.size());
            helper.assertTrue(fires.get(0).pos().distanceTo(center) < 0.05D, "爆发中心 = 第一个打实的目标，实际 " + fires.get(0).pos() + "，应为 " + center);
            helper.assertTrue(Math.abs(fires.get(0).param() - (float) GearGrowthConfig.overloadRadius()) < EPS, "OVERLOAD_FIRE 的 param = 半径");

            GearGrowthConfig.setTestOverride("mru_gun.overload.shots_single", 1);
            GearGrowthConfig.setTestOverride("mru_gun.overload.max_targets", 1);
            leftBefore = lost(left);
            rightBefore = lost(right);
            float primaryBefore = lost(primary);
            primary.invulnerableTime = 0;
            fire(player, pistol, GunType.PISTOL, stats);
            float rayOnly = lost(primary) - primaryBefore;
            helper.assertTrue(GunAttunement.ready(pistol), "充满改成 1 发：打实一发就满");
            leftBefore = lost(left);
            rightBefore = lost(right);
            primaryBefore = lost(primary);
            primary.invulnerableTime = 0;
            fire(player, pistol, GunType.PISTOL, stats);
            helper.assertTrue(lost(left) == leftBefore && lost(right) == rightBefore, "最多 1 个目标：旁边两只不打");
            float burstShot = lost(primary) - primaryBefore;
            helper.assertTrue(Math.abs(burstShot - rayOnly - expected) < EPS,
                    "主目标（离中心最近）挨了射线 " + rayOnly + " 又挨了爆发 " + expected + "，实际合计 " + burstShot);
            helper.assertTrue(GunAttunement.charge(pistol) == 0, "又清零");
        } finally {
            GearGrowthConfig.clearTestOverrides();
            S2CGearFxPacket.setTestSink(null);
            AccumulatorEvents.forgetForTest(player);
            AttunementGains.forget(player);
            primary.discard();
            left.discard();
            right.discard();
            far.discard();
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 转移

    /**
     * 正式配方 {@code attunement/transfer_gun}：手枪 → 步枪带走一半（同家族之间能转），手枪 → 手枪也行；剑和枪不能互转。
     * 不写 family 的武器转移（测试里现造一条）：余烬剑 → 裁决之刃可以（同一条线、往高档），枪 → 裁决之刃不行（换线）。
     */
    @GameTest(template = EMPTY)
    public static void gunTransferSameFamily(GameTestHelper helper) {
        Recipe<?> found = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id("attunement/transfer_gun")).orElse(null);
        helper.assertTrue(found instanceof AttunementTransferRecipe, "枪的转移配方 JSON 应能加载");
        AttunementTransferRecipe recipe = (AttunementTransferRecipe) found;
        helper.assertTrue(ECAttunement.MRU_GUN.id().equals(recipe.family()) && recipe.getMRU() == 10000, "只认 mru_gun、10 000 MRU");
        ItemStack dust = new ItemStack(ECAlloys.CRYSTAL_DUST.get(), 2);
        ItemStack alloy = new ItemStack(ECAlloys.COMBINED_MAGICAL_ALLOYS.get());

        ItemStack rifle = gun(GunType.RIFLE, 0, 0);
        ItemStack oldPistol = worn(gun(GunType.PISTOL, 2, 0), 500);
        helper.assertTrue(matches(helper, recipe, rifle, oldPistol, dust, alloy), "手枪 → 步枪：可以（同家族）");
        ItemStack out = recipe.assemble(new SimpleContainer(rifle, oldPistol, dust, alloy, ItemStack.EMPTY), helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ECGuns.RIFLE.get()) && Attunement.points(out) == 250 && Attunement.stage(out) == 0,
                "新步枪拿到累计 500 的一半 250，实际 " + Attunement.points(out));
        ItemStack kept = recipe.remainderAt(AttunementTransferRecipe.OLD_SLOT, oldPistol);
        helper.assertTrue(kept != null && Attunement.total(kept) == 0 && Attunement.stage(kept) == 2, "旧手枪留下：阶照留、累计清掉");
        helper.assertTrue(matches(helper, recipe, gun(GunType.PISTOL, 0, 0), worn(gun(GunType.PISTOL, 1, 0), 500), dust, alloy), "手枪 → 手枪：也可以");
        helper.assertFalse(matches(helper, recipe, gun(GunType.RIFLE, 0, 0), worn(new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()), 500), dust, alloy),
                "余烬剑 → 步枪：不行");
        helper.assertFalse(matches(helper, recipe, new ItemStack(ECEmberForge.VERDICT_BLADE.get()), worn(gun(GunType.PISTOL, 1, 0), 500), dust, alloy),
                "手枪 → 裁决之刃：不行（配方只认枪）");
        ItemStack staged = gun(GunType.RIFLE, 1, 0);
        helper.assertFalse(matches(helper, recipe, staged, worn(gun(GunType.PISTOL, 1, 0), 500), dust, alloy), "新件不是 0 阶：不行");

        AttunementTransferRecipe generic = AttunementTransferRecipe.of(EssentialCraft5.id("gametest/transfer_weapon"), AttunementFamily.Kind.WEAPON,
                List.of(new Counted(Ingredient.of(ECAlloys.CRYSTAL_DUST.get()), 2), new Counted(Ingredient.of(ECAlloys.COMBINED_MAGICAL_ALLOYS.get()), 1)), 10);
        ItemStack verdict = new ItemStack(ECEmberForge.VERDICT_BLADE.get());
        helper.assertTrue(matches(helper, generic, verdict, worn(new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()), 500), dust, alloy),
                "对照：余烬剑 → 裁决之刃（同一条线、往高档）可以");
        helper.assertFalse(matches(helper, generic, new ItemStack(ECEmberForge.VERDICT_BLADE.get()), worn(gun(GunType.PISTOL, 1, 0), 500), dust, alloy),
                "手枪 → 裁决之刃：不行（剑和枪不在一条线上）");
        helper.assertFalse(matches(helper, generic, gun(GunType.RIFLE, 0, 0), worn(gun(GunType.PISTOL, 1, 0), 500), dust, alloy),
                "不写 family 的配方：同家族之间不转");
        helper.succeed();
    }

    /** 旧件：累计 total 点 */
    private static ItemStack worn(ItemStack stack, int total) {
        if (!Attunement.hasData(stack)) {
            Attunement.set(stack, 0, 0);
        }
        stack.getOrCreateTagElement(Attunement.TAG).putInt(Attunement.KEY_TOTAL, total);
        return stack;
    }

    private static boolean matches(GameTestHelper helper, AttunementTransferRecipe recipe, ItemStack center, ItemStack old, ItemStack dust, ItemStack alloy) {
        return recipe.matches(new SimpleContainer(center, old, dust, alloy, ItemStack.EMPTY), helper.getLevel());
    }
}
