package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.boss.BossDamageBudget;
import com.flameforge.essentialcraft5.entity.boss.BossHealthGuard;
import com.flameforge.essentialcraft5.entity.boss.BossRules;
import com.flameforge.essentialcraft5.entity.boss.BossTier;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityPillarEntity;
import com.flameforge.essentialcraft5.gametest.BossFrameworkTests.TestBoss;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 首领生命的统一结算（{@link BossHealthGuard}）：把常见的「绕开伤害流程掉血」写法逐个照做一遍，看首领是不是还按单次封顶 + 每秒桶掉血。
 * 写法只照通用 API 做（{@code setHealth}、反射改血量数据、{@code die()}、{@code kill()}、生命上限修饰符、伤害事件里抬数），不针对哪个模组。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BossHealthGuardTests {

    static final String BATCH = "ec5_boss_guard";
    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    private static final float EPS = 1.0E-3F;

    /** 反射拿血量那条同步数据（别的代码绕开 setHealth 的常见写法） */
    private static final EntityDataAccessor<Float> HEALTH = ObfuscationReflectionHelper.getPrivateValue(LivingEntity.class, null, "f_20961_");

    private BossHealthGuardTests() {
    }

    // ================================================================== 小工具

    /** 100 血的测试首领，单次封顶 / 每秒桶按给的；生成后认下当前生命（等于过了第一刻） */
    private static TestBoss boss(GameTestHelper helper, BossTier tier, Vec3 relative, float perHit, float perSecond) {
        TestBoss boss = new TestBoss(helper.getLevel(), tier);
        Vec3 at = helper.absoluteVec(relative);
        boss.moveTo(at.x, at.y, at.z, 0F, 0F);
        boss.setNoAi(true);
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        boss.getAttribute(Attributes.ARMOR).setBaseValue(0D);
        boss.setHealth(100F);
        boss.limits = new BossDamageBudget.Limits(perHit, perSecond);
        helper.getLevel().addFreshEntity(boss);
        boss.healthGuard().observe(boss, boss);
        return boss;
    }

    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        ECProtection.setOnlineOverride(player);
        return player;
    }

    private static DamageSource melee(ServerPlayer player) {
        return player.damageSources().playerAttack(player);
    }

    private static boolean hit(LivingEntity target, DamageSource source, float amount) {
        target.invulnerableTime = 0;
        return target.hurt(source, amount);
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < EPS;
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    // ================================================================== 直接改生命

    /**
     * 直接 setHealth(0)：按一下封顶掉（100 → 90）；同一刻再来一次不掉（绕开伤害流程的掉血没有受击无敌，10 刻里合起来算一下）；
     * 往上照收；血没扣到 0 调 die() 不算死；过了 10 刻又能掉一下（桶照扣）
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 60)
    public static void bossGuardDirectSetHealth(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), 10F, 20F);
        boss.setHealth(0F);
        helper.assertTrue(near(boss.getHealth(), 90F) && boss.isAlive(), "setHealth(0) 只掉一下封顶，实际 " + boss.getHealth());
        boss.setHealth(0F);
        boss.setHealth(boss.getHealth() - 3F);
        helper.assertTrue(near(boss.getHealth(), 90F), "同一窗里再直接掉不掉，实际 " + boss.getHealth());
        boss.setHealth(95F);
        helper.assertTrue(near(boss.getHealth(), 95F), "往上照收");
        boss.die(boss.damageSources().generic());
        helper.assertTrue(boss.isAlive() && !boss.isDeadOrDying() && boss.endedWith == null, "血没扣到 0 调 die() 不算死");
        helper.runAfterDelay(BossHealthGuard.DIRECT_WINDOW + 1, () -> {
            boss.setHealth(0F);
            try {
                helper.assertTrue(near(boss.getHealth(), 85F), "下一窗又能掉一下，实际 " + boss.getHealth());
            } finally {
                discard(boss);
            }
            helper.succeed();
        });
    }

    /** 反射拿到血量数据直接 set（不经过 setHealth）：当场结算、写回；先探到一半再设回原值的写法不丢血；写负无穷 / NaN 当成扣到 0，正无穷当成回满 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void bossGuardDataWrite(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.MINI, new Vec3(2.5D, 1D, 2.5D), 10F, 0F);
        TestBoss probe = boss(helper, BossTier.MINI, new Vec3(1.5D, 1D, 1.5D), 10F, 0F);
        TestBoss odd = boss(helper, BossTier.MINI, new Vec3(3.5D, 1D, 3.5D), 10F, 0F);
        try {
            odd.getEntityData().set(HEALTH, Float.NEGATIVE_INFINITY);
            helper.assertTrue(near(odd.getHealth(), 90F) && odd.isAlive(), "写负无穷：当成扣到 0，只掉一下封顶，实际 " + odd.getHealth());
            odd.getEntityData().set(HEALTH, Float.NaN);
            helper.assertTrue(near(odd.getHealth(), 90F), "写 NaN：同一窗不再掉，实际 " + odd.getHealth());
            odd.getEntityData().set(HEALTH, Float.POSITIVE_INFINITY);
            helper.assertTrue(near(odd.getHealth(), 100F), "写正无穷：当成回满，实际 " + odd.getHealth());

            boss.getEntityData().set(HEALTH, 0F);
            helper.assertTrue(near(boss.getHealth(), 90F) && boss.isAlive(), "数据项直接写 0：只掉一下封顶，实际 " + boss.getHealth());
            boss.getEntityData().set(HEALTH, 1F);
            helper.assertTrue(near(boss.getHealth(), 90F), "同一窗再写不掉，实际 " + boss.getHealth());

            float before = probe.getHealth();
            probe.setHealth(before * 0.5F);
            probe.setHealth(before);
            helper.assertTrue(near(probe.getHealth(), before), "探到一半再设回：原样，实际 " + probe.getHealth());
            probe.setHealth(probe.getHealth() - 50F);
            helper.assertTrue(probe.getHealth() >= before - 10F - EPS && probe.isAlive(), "之后再直接扣 50 最多掉一下封顶，实际 " + probe.getHealth());
        } finally {
            discard(boss, probe, odd);
        }
        helper.succeed();
    }

    // ================================================================== /kill 同款伤害、掉出世界

    /**
     * kill()（generic_kill、绕过无敌、没有攻击者）：没人敲 /kill 时小 Boss 照普通一下夹封顶、Boss 档没攻击者打不动；管理员敲的 /kill 照常打死。
     * 掉出世界只在真掉到世界底下时算；认 /kill 指令的写法
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void bossGuardKillCommandOnly(GameTestHelper helper) {
        TestBoss mini = boss(helper, BossTier.MINI, new Vec3(2.5D, 1D, 2.5D), 10F, 0F);
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(1.5D, 1D, 1.5D), 10F, 20F);
        try {
            mini.kill();
            helper.assertTrue(near(mini.getHealth(), 90F) && mini.isAlive(), "小 Boss：kill() 只掉一下封顶，实际 " + mini.getHealth());
            boss.kill();
            helper.assertTrue(near(boss.getHealth(), 100F) && boss.isAlive(), "Boss 档：没攻击者打不动，实际 " + boss.getHealth());
            mini.hurt(mini.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            helper.assertTrue(mini.isAlive(), "还在场地里：掉出世界的伤害照普通一下夹");

            helper.assertFalse(BossRules.unblockable(boss, boss.damageSources().fellOutOfWorld()), "没掉下去不算");
            double y = boss.getY();
            boss.moveTo(boss.getX(), helper.getLevel().getMinBuildHeight() - 1D, boss.getZ());
            helper.assertTrue(BossRules.unblockable(boss, boss.damageSources().fellOutOfWorld()), "掉到世界底下算");
            boss.moveTo(boss.getX(), y, boss.getZ());

            BossFrameworkTests.asKillCommand(boss::kill);
            helper.assertTrue(boss.isDeadOrDying(), "管理员敲的 /kill 照常打死");
            helper.assertFalse(BossRules.killCommandActive(), "标记只在敲的那一下");

            helper.assertTrue(BossRules.isKillCommand("kill") && BossRules.isKillCommand("/kill @e[type=zombie]") && BossRules.isKillCommand("minecraft:kill @s")
                    && BossRules.isKillCommand("execute as @a at @s run kill @e[distance=..3]"), "认 /kill 和 execute ... run kill");
            helper.assertFalse(BossRules.isKillCommand("killall") || BossRules.isKillCommand("say kill") || BossRules.isKillCommand("tp @s ~ ~ ~"),
                    "别的指令不算");
        } finally {
            discard(mini, boss);
        }
        helper.succeed();
    }

    // ================================================================== 生命上限、伤害流程

    /**
     * 生命上限挂 −90%：生命不跟着一下截到 10，按封顶一窗一窗往下降；这期间治疗不往上加，正常挨打照常扣；降完以后照原版
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 60)
    public static void bossGuardMaxHealthCut(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), 10F, 20F);
        ServerPlayer player = player(helper, "bg-cut", new Vec3(3.5D, 1D, 2.5D));
        AttributeInstance max = boss.getAttribute(Attributes.MAX_HEALTH);
        UUID id = UUID.fromString("6f5c1c0e-2d1b-4c55-9d43-3d2a0f9b7e11");
        max.addTransientModifier(new AttributeModifier(id, "bg-cut", -0.9D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        boss.healthGuard().observe(boss, boss);
        helper.assertTrue(near(boss.getMaxHealth(), 10F) && near(boss.getHealth(), 90F), "上限 10，生命只降一下到 90，实际 " + boss.getHealth());
        boss.heal(5F);
        helper.assertTrue(near(boss.getHealth(), 90F), "降的过程中治疗不往上加，实际 " + boss.getHealth());
        helper.assertTrue(hit(boss, melee(player), 5F) && near(boss.getHealth(), 85F), "正常挨打照常扣，实际 " + boss.getHealth());
        helper.runAfterDelay(15, () -> {
            try {
                float health = boss.getHealth();
                helper.assertTrue(health >= 65F - EPS && health <= 75F + EPS && boss.isAlive(), "每刻接着按窗往下降，实际 " + health);
            } finally {
                max.removeModifier(id);
                ECProtection.clearOnlineOverride(player.getUUID());
                discard(boss);
            }
            helper.succeed();
        });
    }

    /**
     * 伤害流程照常：打实扣血、超过封顶夹住；登记得比本模组晚的监听器在最后把这一下抬到 1000，这一下合起来也只掉一下封顶（不靠死亡兜底）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void bossGuardDamagePipeline(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), 10F, 0F);
        ServerPlayer player = player(helper, "bg-hit", new Vec3(3.5D, 1D, 2.5D));
        Consumer<LivingDamageEvent> late = event -> {
            if (event.getEntity() == boss) {
                event.setAmount(1000F);
            }
        };
        try {
            helper.assertTrue(hit(boss, melee(player), 5F) && near(boss.getHealth(), 95F), "打实 5，实际 " + boss.getHealth());
            helper.assertTrue(hit(boss, melee(player), 30F) && near(boss.getHealth(), 85F), "30 夹到 10，实际 " + boss.getHealth());
            boss.heal(3F);
            helper.assertTrue(near(boss.getHealth(), 88F), "治疗照收");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, late);
            try {
                hit(boss, melee(player), 5F);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(late);
            }
            helper.assertTrue(near(boss.getHealth(), 78F) && boss.isAlive(), "最后被抬到 1000：这一下合起来最多掉一下封顶 10，实际 " + boss.getHealth());
        } finally {
            ECProtection.clearOnlineOverride(player.getUUID());
            discard(boss);
        }
        helper.succeed();
    }

    // ================================================================== 限额跟着生命上限放大

    /** 生命上限 ×10（挂 MULTIPLY_TOTAL 修饰）：单次封顶、每秒桶都 ×10；绕开伤害流程的直接写血也按放大后的封顶掉 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void bossGuardScaleFollowsMaxHealth(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), 10F, 20F);
        ServerPlayer player = player(helper, "bg-scale", new Vec3(3.5D, 1D, 2.5D));
        boss.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(
                new AttributeModifier(UUID.fromString("0b6f4a63-8f0e-4f7e-a3a2-6c2f8e1d9a01"), "bg-scale", 9D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        boss.setHealth(1000F);
        try {
            helper.assertTrue(near(boss.getHealth(), 1000F), "上限 1000，回满，实际 " + boss.getHealth());
            BossDamageBudget.Limits limits = boss.effectiveLimits();
            helper.assertTrue(near(limits.perHit(), 100F) && near(limits.perSecond(), 200F), "限额 ×10，实际 " + limits);
            helper.assertTrue(hit(boss, melee(player), 500F) && near(boss.getHealth(), 900F), "一下封顶 100，实际 " + boss.getHealth());
            hit(boss, melee(player), 500F);
            helper.assertTrue(near(boss.getHealth(), 800F), "同一秒第二下：桶 200 只剩 100，实际 " + boss.getHealth());
            hit(boss, melee(player), 500F);
            helper.assertTrue(near(boss.getHealth(), 800F), "桶空了，实际 " + boss.getHealth());
        } finally {
            ECProtection.clearOnlineOverride(player.getUUID());
            discard(boss);
        }
        helper.succeed();
    }

    /**
     * 倍数最小 1：挂 −50% 压生命上限，限额不缩；满 {@value BossHealthGuard#SCALE_SETTLE_TICKS} 刻认下以后再抬生命上限也不跟，
     * 认下的数存在持久数据里，换一份结算（读档）照用
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void bossGuardScaleLatched(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), 10F, 20F);
        AttributeInstance max = boss.getAttribute(Attributes.MAX_HEALTH);
        UUID cut = UUID.fromString("0b6f4a63-8f0e-4f7e-a3a2-6c2f8e1d9a02");
        UUID boost = UUID.fromString("0b6f4a63-8f0e-4f7e-a3a2-6c2f8e1d9a03");
        try {
            max.addTransientModifier(new AttributeModifier(cut, "bg-cut", -0.5D, AttributeModifier.Operation.MULTIPLY_TOTAL));
            BossDamageBudget.Limits limits = boss.effectiveLimits();
            helper.assertTrue(near(limits.perHit(), 10F) && near(limits.perSecond(), 20F), "压低上限不缩限额，实际 " + limits);
            max.removeModifier(cut);
            max.addPermanentModifier(new AttributeModifier(boost, "bg-x3", 2D, AttributeModifier.Operation.MULTIPLY_TOTAL));
            boss.tickCount = BossHealthGuard.SCALE_SETTLE_TICKS;
            boss.healthGuard().observe(boss, boss);
            helper.assertTrue(near(boss.healthGuard().limitScale(boss), 3F)
                    && near(boss.getPersistentData().getFloat(BossHealthGuard.TAG_LIMIT_SCALE), 3F), "满 20 刻认下 ×3");
            max.removeModifier(boost);
            max.addTransientModifier(new AttributeModifier(boost, "bg-x20", 19D, AttributeModifier.Operation.MULTIPLY_TOTAL));
            helper.assertTrue(near(boss.effectiveLimits().perHit(), 30F), "认下以后再抬上限不跟，实际 " + boss.effectiveLimits());
            helper.assertTrue(near(new BossHealthGuard().limitScale(boss), 3F), "换一份结算照用存下的倍数");
        } finally {
            max.removeModifier(cut);
            max.removeModifier(boost);
            discard(boss);
        }
        helper.succeed();
    }

    /** 全息投影（不走首领基类）的单次封顶同样跟着生命上限放大 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void bossGuardScaleHologram(GameTestHelper helper) {
        HologramEntity boss = ECHologram.HOLOGRAM.get().create(helper.getLevel());
        helper.assertTrue(boss != null, "建得出全息投影");
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        boss.moveTo(at.x, at.y, at.z, 0F, 0F);
        boss.setNoAi(true);
        boss.applyConfiguredHealth();
        helper.getLevel().addFreshEntity(boss);
        try {
            float cap = (float) ECHologram.Config.values().maxDamagePerHit();
            helper.assertTrue(near(boss.damageCap(), cap), "没放大时照配置，实际 " + boss.damageCap());
            // 纯 Forge 的生命上限最多 1024（默认 400 只能放大到 ×2.56），这里只放大 ×2
            boss.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(
                    new AttributeModifier(UUID.fromString("0b6f4a63-8f0e-4f7e-a3a2-6c2f8e1d9a04"), "bg-holo", 1D, AttributeModifier.Operation.MULTIPLY_TOTAL));
            helper.assertTrue(near(boss.damageCap(), cap * 2F), "上限 ×2 封顶 ×2，实际 " + boss.damageCap());
        } finally {
            discard(boss);
        }
        helper.succeed();
    }

    // ================================================================== 全息投影

    /** 全息投影（不走首领基类，自己接的结算）：出招时直接写血不掉、die() 不算死、kill() 打不动；休息时直接写血按单次封顶掉一下；管理员 /kill 照常 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void bossGuardHologram(GameTestHelper helper) {
        HologramEntity boss = ECHologram.HOLOGRAM.get().create(helper.getLevel());
        helper.assertTrue(boss != null, "建得出全息投影");
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        boss.moveTo(at.x, at.y, at.z, 0F, 0F);
        boss.setNoAi(true);
        boss.applyConfiguredHealth();
        helper.getLevel().addFreshEntity(boss);
        boss.healthGuard().observe(boss, boss);
        try {
            float full = boss.getHealth();
            float cap = (float) ECHologram.Config.values().maxDamagePerHit();
            boss.setTestState(HologramEntity.ADDITION, 50, 0, null);
            boss.setHealth(0F);
            boss.getEntityData().set(HEALTH, 0F);
            boss.die(boss.damageSources().generic());
            helper.assertTrue(near(boss.getHealth(), full) && boss.isAlive(), "出招时直接写血不掉、die() 不算死，实际 " + boss.getHealth());
            boss.kill();
            helper.assertTrue(near(boss.getHealth(), full), "kill() 没有攻击者打不动，实际 " + boss.getHealth());
            boss.setTestState(HologramEntity.REST, 0, 100, null);
            boss.setHealth(0F);
            helper.assertTrue(near(boss.getHealth(), full - cap) && boss.isAlive(), "休息时直接写血按单次封顶掉一下，实际 " + boss.getHealth());
            BossFrameworkTests.asKillCommand(boss::kill);
            helper.assertTrue(boss.isDeadOrDying(), "管理员敲的 /kill 照常打死");
        } finally {
            discard(boss);
        }
        helper.succeed();
    }

    // ================================================================== 天灾之心、灾柱

    /**
     * 天灾之心护盾期打不动：直接写血、反射写血都不掉，die() 不算死；灾柱直接写血按灾柱封顶 40 掉一下；
     * 破盾窗口里直接写血压不到崩解线以下
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void bossGuardCalamityHeart(GameTestHelper helper) {
        CalamityHeartTests.whenSky(helper, () -> {
            CalamityHeartTests.scope(helper, CalamityHeartTests.local());
            CalamityHeartTests.Rig rig = null;
            try {
                rig = CalamityHeartTests.summon(helper, "bg-heart", CalamityHeartTests.beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                heart.serverBossStep();
                heart.healthGuard().observe(heart, heart);
                helper.assertTrue(heart.phase() == CalamityHeartEntity.Phase.SHIELD && !heart.shieldOpen(), "护盾期、盾没破");
                float full = heart.getHealth();
                heart.setHealth(1F);
                heart.getEntityData().set(HEALTH, 1F);
                heart.die(heart.damageSources().generic());
                helper.assertTrue(near(heart.getHealth(), full) && heart.isAlive(), "护盾期直接写血不掉、die() 不算死，实际 " + heart.getHealth());

                CalamityPillarEntity sun = heart.pillar(CalamityHeartEntity.Aspect.SUN_ARRAY.ordinal());
                helper.assertTrue(sun != null, "有日阵灾柱");
                float pillarFull = sun.getHealth();
                double cap = ECCalamityHeart.Config.values().fight().pillarPerHit();
                sun.setHealth(0F);
                sun.getEntityData().set(HEALTH, 0F);
                sun.die(sun.damageSources().generic());
                helper.assertTrue(near(sun.getHealth(), pillarFull - (float) cap) && sun.isAlive(),
                        "灾柱直接写血按封顶掉一下，实际 " + sun.getHealth() + " / " + pillarFull);

                CalamityPillarEntity quake = heart.pillar(CalamityHeartEntity.Aspect.EARTHQUAKE.ordinal());
                helper.assertTrue(quake != null, "有地震灾柱");
                BossFrameworkTests.asKillCommand(quake::kill);
                helper.assertTrue(heart.shieldOpen(), "倒一根：破盾");
                heart.heartSkills().cancelCast();
                float floor = (float) (heart.getMaxHealth() * ECCalamityHeart.Config.values().fight().collapseThreshold());
                heart.healthGuard().reset();
                heart.setHealth(floor + 10F);
                heart.healthGuard().observe(heart, heart);
                heart.setHealth(1F);
                helper.assertTrue(near(heart.getHealth(), floor) && heart.isAlive(), "破盾窗口：直接写血停在崩解线，实际 " + heart.getHealth() + " / " + floor);
            } finally {
                CalamityHeartTests.cleanup(helper, rig);
            }
        });
    }
}
