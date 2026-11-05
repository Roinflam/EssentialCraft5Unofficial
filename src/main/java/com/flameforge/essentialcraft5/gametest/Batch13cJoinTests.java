package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.BossDamageBudget;
import com.flameforge.essentialcraft5.entity.boss.BossTier;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.ElementalWispEntity;
import com.flameforge.essentialcraft5.entity.mob.MobDamageShare;
import com.flameforge.essentialcraft5.entity.mob.WispBoltEntity;
import com.flameforge.essentialcraft5.loot.FairKillCondition;
import com.flameforge.essentialcraft5.matrix.MatrixCodex;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十三批 13c 两条框架对接以后的 GameTest（batch13.md §5.3「共同」、§5.4「公平击杀」、§12.2 13c 行）：
 * <ul>
 *   <li>首领基类继承怪物基类：首领是 {@link ECMonster}，但没有精英（几率 1 也不掷、{@code setElite} 不起作用、存档里写着 {@code variant:1b} 读回来也不是），
 *       技能目标换成名单判定；</li>
 *   <li>小 Boss 的公平击杀：玩家伤害占比按封顶以后的数算（一下 30 封顶 10，只算 10），环境磨的多了就不成立；
 *       伤害占比监听器、封顶、矩阵图谱三个都在 {@code LivingDamageEvent} LOWEST，谁先跑都拿同一个结算数、桶只扣一次；
 *       多人各自过、门槛填 0 不看占比、被取消的一下不记；</li>
 *   <li>矩阵图谱的首领参战记账按结算数记；</li>
 *   <li>首领射出的怪物弹（{@code WildMobBolt}）按首领自己的技能目标判定（名单），普通怪射手照普通规矩。</li>
 * </ul>
 * 首领用 {@link BossFrameworkTests.TestBoss}（借僵尸，要进 {@code #forge:bosses} 的借凋灵；NoAI、最大生命 100、护甲 0）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch13cJoinTests {

    private static final String EMPTY = "empty";
    private static final float EPS = 1.0E-3F;

    private Batch13cJoinTests() {
    }

    // ================================================================== 工具

    private static BossFrameworkTests.TestBoss boss(GameTestHelper helper, EntityType<?> type, BossTier tier, BossDamageBudget.Limits limits,
                                                     double x, double z, boolean addToWorld) {
        BossFrameworkTests.TestBoss boss = new BossFrameworkTests.TestBoss(type, helper.getLevel(), tier);
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        boss.moveTo(at.x, at.y, at.z, 0F, 0F);
        boss.setNoAi(true);
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        boss.getAttribute(Attributes.ARMOR).setBaseValue(0D);
        boss.setHealth(100F);
        boss.limits = limits;
        if (addToWorld) {
            helper.getLevel().addFreshEntity(boss);
        }
        return boss;
    }

    /** 真玩家（生存），站在 relative 处 */
    private static ServerPlayer player(GameTestHelper helper, String name, double x, double z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static DamageSource melee(ServerPlayer player) {
        return player.damageSources().playerAttack(player);
    }

    /** 再打一下前清掉受击无敌（同步测试里两下之间没有刻） */
    private static void hit(LivingEntity victim, DamageSource source, float amount) {
        victim.invulnerableTime = 0;
        victim.hurt(source, amount);
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

    /** 测试用的元素聚灵弹：只为从外面问 {@code canHitEntity}（不进世界） */
    static final class BoltProbe extends WispBoltEntity {
        BoltProbe(Level level, LivingEntity shooter) {
            super(level, shooter, ElementalWispEntity.Element.FIRE);
        }

        boolean hits(Entity target) {
            return canHitEntity(target);
        }
    }

    // ================================================================== 首领继承怪物基类

    /**
     * 首领是 {@link ECMonster}，但没有精英：精英几率钉到 1、来源自然也不掷；{@code setElite(true)} 不起作用；存档里写着 {@code variant:1b}
     * 读回来照样是普通的，属性不多挂一层。对照：同样条件下的普通怪探针出精英。技能目标走名单判定（不在名单里的生存玩家不打）。
     * 首领自己那几条更严的规矩照旧（不消失、不捡东西、Boss 档推不动、拴不住）。
     */
    @GameTest(template = EMPTY)
    public static void joinBossIsMonsterWithoutElite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BossFrameworkTests.TestBoss boss = boss(helper, EntityType.ZOMBIE, BossTier.BOSS, BossDamageBudget.Limits.NONE, 2.5D, 2.5D, true);
        ServerPlayer player = player(helper, "join-elite", 3.5D, 2.5D);
        try {
            helper.assertTrue(boss instanceof ECMonster, "首领基类继承了怪物基类");
            helper.assertFalse(boss.getType().is(Tags.EntityTypes.BOSSES), "测试前提：借来的僵尸类型不在 #forge:bosses 里（只靠基类的开关关精英）");
            helper.assertFalse(boss.hasEliteVariant(), "首领没有精英变体");

            ECMobFramework.Config.setTestEliteChance(1D);
            try {
                MobFrameworkTests.Probe control = new MobFrameworkTests.Probe(EntityType.ILLUSIONER, level);
                Vec3 at = helper.absoluteVec(new Vec3(1.5D, 1D, 1.5D));
                control.moveTo(at.x, at.y, at.z, 0F, 0F);
                control.finalizeSpawn(level, level.getCurrentDifficultyAt(control.blockPosition()), MobSpawnType.NATURAL, null, null);
                helper.assertTrue(control.isElite(), "对照：几率 1、自然生成的普通怪出精英");
                SpawnOrigin.set(boss, Origin.NATURAL);
                boss.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()), MobSpawnType.NATURAL, null, null);
                helper.assertFalse(boss.isElite() || ECMonster.isElite(boss), "几率 1、自然生成：首领也不掷精英");
            } finally {
                ECMobFramework.Config.setTestEliteChance(null);
            }

            boss.setElite(true);
            helper.assertFalse(boss.isElite(), "setElite(true) 对首领不起作用");
            helper.assertTrue(boss.getAttribute(Attributes.MAX_HEALTH).getModifier(ECMonster.ELITE_HEALTH_ID) == null
                    && boss.getAttribute(Attributes.ARMOR).getModifier(ECMonster.ELITE_ARMOR_ID) == null, "没挂精英属性");
            helper.assertTrue(boss.getScale() == 1F && near(boss.getMaxHealth(), 100F), "体型、生命照旧");

            CompoundTag saved = boss.saveWithoutId(new CompoundTag());
            saved.putByte(ECMonster.TAG_VARIANT, ECMonster.VARIANT_ELITE);
            BossFrameworkTests.TestBoss reloaded = new BossFrameworkTests.TestBoss(EntityType.ZOMBIE, level, BossTier.BOSS);
            reloaded.load(saved);
            helper.assertFalse(reloaded.isElite(), "存档里写着 variant:1b 读回来也不是精英");
            helper.assertTrue(near(reloaded.getMaxHealth(), 100F) && reloaded.getAttribute(Attributes.MAX_HEALTH).getModifier(ECMonster.ELITE_HEALTH_ID) == null,
                    "读档后属性没多挂一层，最大生命 " + reloaded.getMaxHealth());

            helper.assertTrue(ECMonster.isSkillTarget(boss, player), "测试前提：按普通怪的规矩，生存玩家是技能目标");
            helper.assertFalse(boss.isSkillTarget(player), "首领换成名单判定：不在名单里的不打");
            boss.bossRoster().add(player);
            helper.assertTrue(boss.isSkillTarget(player), "进了名单就打");
            helper.assertTrue(boss.skillTargets(boss.getBoundingBox().inflate(4D)).isEmpty(), "盒子取目标也走名单判定（玩家没进世界，取不到别的）");

            helper.assertFalse(boss.removeWhenFarAway(10_000D) || boss.canPickUpLoot() || boss.isPushable() || boss.canBeLeashed(player),
                    "首领那几条更严的规矩照旧：不消失、不捡东西、推不动、拴不住");
        } finally {
            discard(boss);
        }
        helper.succeed();
    }

    // ================================================================== 小 Boss 的公平击杀

    /**
     * 小 Boss（单次封顶 10）：玩家打 30 只算 10，环境再打 6 + 6 → 占比 10 / 22 不到一半，公平击杀不成立（按封顶前的数是 30 / 42，会误判成立）；
     * 另一只玩家 10、环境 9 → 10 / 19 过半，成立。两名玩家各打 10、环境 6 → 两人都成立。门槛填 0 只看来源和真玩家。被取消的一下不记。
     */
    @GameTest(template = EMPTY)
    public static void joinMiniBossFairKillUsesCappedShare(GameTestHelper helper) {
        BossDamageBudget.Limits cap10 = new BossDamageBudget.Limits(10F, 0F);
        BossFrameworkTests.TestBoss ground = boss(helper, EntityType.ZOMBIE, BossTier.MINI, cap10, 1.5D, 1.5D, true);
        BossFrameworkTests.TestBoss fair = boss(helper, EntityType.ZOMBIE, BossTier.MINI, cap10, 3.5D, 1.5D, true);
        BossFrameworkTests.TestBoss duo = boss(helper, EntityType.ZOMBIE, BossTier.MINI, cap10, 1.5D, 3.5D, true);
        BossFrameworkTests.TestBoss blocked = boss(helper, EntityType.ZOMBIE, BossTier.MINI, cap10, 3.5D, 3.5D, true);
        ServerPlayer player = player(helper, "join-fair", 2.5D, 2.5D);
        ServerPlayer second = player(helper, "join-fair2", 2.5D, 1.5D);
        for (BossFrameworkTests.TestBoss boss : new BossFrameworkTests.TestBoss[]{ground, fair, duo, blocked}) {
            SpawnOrigin.set(boss, Origin.BOSS);
        }
        DamageSource environment = helper.getLevel().damageSources().generic();
        Consumer<LivingDamageEvent> cancel = event -> {
            if (event.getEntity() == blocked) {
                event.setCanceled(true);
            }
        };
        try {
            hit(ground, melee(player), 30F);
            helper.assertTrue(near(MobDamageShare.playerDamage(ground), 10F) && near(ground.playerDamage(player.getUUID()), 10F),
                    "玩家一下 30 封顶 10，伤害占比只记 10，实际 " + MobDamageShare.playerDamage(ground));
            hit(ground, environment, 6F);
            hit(ground, environment, 6F);
            helper.assertTrue(near(MobDamageShare.totalDamage(ground), 22F), "合计 10 + 6 + 6，实际 " + MobDamageShare.totalDamage(ground));
            helper.assertTrue(near(ground.playerDamageShare(), 10F / 22F) && near(ground.playerDamageShare(), ground.bossRoster().playerShare()),
                    "占比 10 / 22，和名单的账一致，实际 " + ground.playerDamageShare() + " / " + ground.bossRoster().playerShare());
            helper.assertFalse(FairKillCondition.test(ground, player), "环境磨了一大半：不是公平击杀（按封顶前的 30 / 42 会误判成立）");

            hit(fair, melee(player), 30F);
            hit(fair, environment, 9F);
            helper.assertTrue(near(fair.playerDamageShare(), 10F / 19F), "占比 10 / 19，实际 " + fair.playerDamageShare());
            helper.assertTrue(FairKillCondition.test(fair, player), "玩家打掉过半：公平击杀成立");

            hit(duo, melee(player), 30F);
            hit(duo, melee(second), 30F);
            hit(duo, environment, 6F);
            helper.assertTrue(MobDamageShare.contributors(duo).size() == 2 && near(duo.playerDamage(second.getUUID()), 10F),
                    "两人各记 10，实际 " + MobDamageShare.contributors(duo));
            helper.assertTrue(FairKillCondition.test(duo, player) && FairKillCondition.test(duo, second), "两人合计 20 / 26：谁拿到击杀都成立");

            ECMobFramework.Config.setTestFairKillShare(0D);
            try {
                helper.assertTrue(FairKillCondition.test(ground, player), "门槛填 0：不看占比");
            } finally {
                ECMobFramework.Config.setTestFairKillShare(null);
            }
            SpawnOrigin.set(fair, Origin.SPAWNER);
            helper.assertFalse(FairKillCondition.test(fair, player), "来源不公平的首领照样不成立");

            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDamageEvent.class, cancel);
            try {
                hit(blocked, melee(player), 30F);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(MobDamageShare.totalDamage(blocked) == 0F && blocked.bossRoster().totalDealt() == 0F && near(blocked.getHealth(), 100F),
                    "被取消的一下：占比、名单的账都不记");
        } finally {
            discard(ground, fair, duo, blocked);
        }
        helper.succeed();
    }

    /**
     * 伤害占比、封顶（含每秒桶）、矩阵图谱三个监听器同在 LOWEST：故意让占比和图谱先跑，拿到的也是结算数 10（不是 30），
     * 桶只扣一次（25 − 10 = 15）。借凋灵的类型（在 {@code #forge:bosses} 里，图谱才记）。另一只真打一下，图谱记 10。
     */
    @GameTest(template = EMPTY)
    public static void joinListenersShareResolvedBossDamage(GameTestHelper helper) {
        BossFrameworkTests.TestBoss ordered = boss(helper, EntityType.WITHER, BossTier.BOSS, new BossDamageBudget.Limits(10F, 25F), 2.5D, 2.5D, false);
        BossFrameworkTests.TestBoss real = boss(helper, EntityType.WITHER, BossTier.MINI, new BossDamageBudget.Limits(10F, 0F), 1.5D, 1.5D, false);
        ServerPlayer player = player(helper, "join-codex", 3.5D, 2.5D);
        helper.assertTrue(ordered.getType().is(Tags.EntityTypes.BOSSES), "测试前提：借来的凋灵类型在 #forge:bosses 里");
        SpawnOrigin.set(ordered, Origin.BOSS);
        long now = helper.getLevel().getGameTime();

        LivingDamageEvent event = new LivingDamageEvent(ordered, melee(player), 30F);
        MobDamageShare.onMobDamageShare(event);
        MatrixCodex.MatrixCodexEvents.matrixCodexBossDamage(event);
        helper.assertTrue(near(MobDamageShare.playerDamage(ordered), 10F), "占比监听器先跑：记结算数 10，实际 " + MobDamageShare.playerDamage(ordered));
        Float codex = MatrixCodex.bossDamage(ordered).get(player.getUUID());
        helper.assertTrue(codex != null && near(codex, 10F), "图谱先跑：记结算数 10，实际 " + codex);
        BossDamageBudget.BossBudgetEvents.bossBudgetDamage(event);
        helper.assertTrue(near(event.getAmount(), 10F), "封顶后跑：放行同一个 10，实际 " + event.getAmount());
        helper.assertTrue(near(ordered.bossBudget().tokens(ordered.limits, now), 15F),
                "三个监听器问同一个事件，每秒桶只扣一次（剩 15），实际 " + ordered.bossBudget().tokens(ordered.limits, now));
        helper.assertTrue(near(ordered.bossRoster().dealt(player.getUUID()), 10F), "名单的账同样是 10");

        hit(real, melee(player), 30F);
        Float realCodex = MatrixCodex.bossDamage(real).get(player.getUUID());
        helper.assertTrue(near(real.getHealth(), 90F) && realCodex != null && near(realCodex, 10F),
                "真打一下：扣 10、图谱记 10，实际剩 " + real.getHealth() + "、图谱 " + realCodex);
        helper.assertTrue(near(MobDamageShare.playerDamage(real), 10F), "伤害占比同样记 10");
        helper.succeed();
    }

    // ================================================================== 首领射出的怪物弹

    /**
     * 怪物弹按射手自己的技能目标判定：首领射的，不在名单里的生存玩家穿过去，进了名单才撞（不照普通怪的规矩打到路人）；
     * 对照：普通怪射的打生存玩家、不打创造模式的。
     */
    @GameTest(template = EMPTY)
    public static void joinBossBoltFollowsRoster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BossFrameworkTests.TestBoss boss = boss(helper, EntityType.ZOMBIE, BossTier.BOSS, BossDamageBudget.Limits.NONE, 2.5D, 2.5D, false);
        ServerPlayer passerby = player(helper, "join-bolt", 3.5D, 2.5D);
        ServerPlayer creative = player(helper, "join-bolt-c", 3.5D, 3.5D);
        creative.setGameMode(GameType.CREATIVE);
        BoltProbe fromBoss = new BoltProbe(level, boss);
        helper.assertTrue(ECMonster.isSkillTarget(boss, passerby), "测试前提：按普通怪的规矩，生存玩家是技能目标");
        helper.assertFalse(fromBoss.hits(passerby), "首领的弹：不在名单里的路人穿过去");
        boss.bossRoster().add(passerby);
        helper.assertTrue(fromBoss.hits(passerby), "进了名单就撞");

        MobFrameworkTests.Probe mob = new MobFrameworkTests.Probe(EntityType.HUSK, level);
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 1D, 1.5D));
        mob.moveTo(at.x, at.y, at.z, 0F, 0F);
        BoltProbe fromMob = new BoltProbe(level, mob);
        ServerPlayer survivor = player(helper, "join-bolt-s", 1.5D, 3.5D);
        helper.assertTrue(fromMob.hits(survivor), "对照：普通怪的弹打生存玩家");
        helper.assertFalse(fromMob.hits(creative), "对照：普通怪的弹不打创造模式的");
        helper.succeed();
    }
}
