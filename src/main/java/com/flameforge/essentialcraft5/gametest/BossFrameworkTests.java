package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.EntityPreviewSpec;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.boss.BossDamageBudget;
import com.flameforge.essentialcraft5.entity.boss.BossFxRules;
import com.flameforge.essentialcraft5.entity.boss.BossLoot;
import com.flameforge.essentialcraft5.entity.boss.BossRoster;
import com.flameforge.essentialcraft5.entity.boss.BossRules;
import com.flameforge.essentialcraft5.entity.boss.BossSkillSlots;
import com.flameforge.essentialcraft5.entity.boss.BossTier;
import com.flameforge.essentialcraft5.entity.boss.ECBossMonster;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeEntity;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBossFramework;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 第十三批 13c-Fb「首领与渲染框架」（batch13.md §5.3「共同」、§12.2 13c 行的首领部分）的 GameTest：
 * <ul>
 *   <li>伤害预算：单次封顶（别的模组在 Hurt 里再乘也照样夹）、每秒桶（纯函数、同一刻多人共用一个桶）、同一个事件只算一次、死亡兜底、配置填 0；</li>
 *   <li>谁打得动：Boss 档只认附近的生存 / 冒险真玩家（生物、假玩家、远处的、旁观的、轨道打击、环境伤害都拒；{@code /kill}、创造模式放行），
 *       小 Boss 谁打都算；非近战 ×0.5；混合端取消了攻击 / 伤害事件的不进名单、不扣桶；</li>
 *   <li>名单：拉回（扣血、留在名单、血条只给名单里的人）、拉回被取消（C65：不拉、不扣血、人留名单）、落点被堵 / 被拉的人在落点没权限
 *       （不拉、不扣血，连着拉不回来满 abandon_ticks 脱战，回来清零）、各种脱战、死亡事件当场移出、飞行压制；名单空了散去、和平难度结束；</li>
 *   <li>打倒：多人按伤害分（60% / 30% / 5% / 不在线），每人一份进背包，首杀只记一次；{@code /kill}、掉出世界不掉不记（刚有人打过也一样）；
 *       玩家打出来的「真伤」照样按参战规矩、照样封顶；</li>
 *   <li>存读档不叠加、读档后满桶；技能实体上限；不吃效果、不骑载具、推不动、不吃击退、没有摔落（Boss 档），小 Boss 照常；</li>
 *   <li>纯函数：整屏画面护眼数值（{@link BossFxRules}）、动画事件号对照（{@link MobAnimStates}）、手册实体预览的 nbt / scale（{@link EntityPreviewSpec}）。</li>
 * </ul>
 * 首领用测试专用的 {@link TestBoss}（借僵尸的实体类型和属性，NoAI，最大生命 100、护甲 0），名单、伤害都由测试同步推进。
 * 改配置、挂临时监听器、切难度都在同一段同步代码里设、测、复原。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BossFrameworkTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    private static final float EPS = 1.0E-3F;
    private static final ECBossFramework.Config.Values DEFAULTS = ECBossFramework.Config.DEFAULTS;

    private BossFrameworkTests() {
    }

    // ================================================================== 测试首领

    /** 测试用首领：档位、限额、名单设置、场地、战利品表都由测试给 */
    static final class TestBoss extends ECBossMonster {

        BossDamageBudget.Limits limits = BossDamageBudget.Limits.NONE;
        BossRoster.Settings settings = BossRoster.Settings.arena(16D, 32D);
        @Nullable
        BlockPos arena;
        @Nullable
        LootTable table;
        @Nullable
        BossEnd endedWith;
        List<ServerPlayer> defeatedBy = List.of();
        final List<UUID> left = new ArrayList<>();

        TestBoss(Level level, BossTier tier) {
            this(EntityType.ZOMBIE, level, tier);
        }

        /** 借别的原版类型（要进 {@code #forge:bosses} 的测试借凋灵：默认属性按借来的类型取） */
        @SuppressWarnings("unchecked")
        TestBoss(EntityType<?> type, Level level, BossTier tier) {
            super((EntityType<? extends ECBossMonster>) type, level, tier, BossEvent.BossBarColor.PURPLE);
        }

        @Override
        public BossDamageBudget.Limits bossLimits() {
            return limits;
        }

        @Override
        public BossRoster.Settings rosterSettings() {
            return settings;
        }

        @Override
        public @Nullable BlockPos arenaFloor() {
            return arena;
        }

        @Override
        public void endBoss(BossEnd reason) {
            endedWith = reason;
            super.endBoss(reason);
        }

        @Override
        public void onBossDefeated(List<ServerPlayer> winners) {
            defeatedBy = List.copyOf(winners);
        }

        @Override
        public void onFighterLeft(UUID player, @Nullable ServerPlayer online) {
            left.add(player);
        }

        @Override
        protected LootTable bossLootTable(ServerLevel server) {
            return table != null ? table : LootTable.EMPTY;
        }
    }

    private static TestBoss boss(GameTestHelper helper, BossTier tier, Vec3 relative, @Nullable Consumer<TestBoss> setup) {
        TestBoss boss = new TestBoss(helper.getLevel(), tier);
        Vec3 at = helper.absoluteVec(relative);
        boss.moveTo(at.x, at.y, at.z, 0F, 0F);
        boss.setNoAi(true);
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        boss.getAttribute(Attributes.ARMOR).setBaseValue(0D);
        boss.setHealth(100F);
        if (setup != null) {
            setup.accept(boss);
        }
        helper.getLevel().addFreshEntity(boss);
        return boss;
    }

    /** 真玩家：过了出生无敌、满血、在 relativeFeet；online 时登记成在线 */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet, GameType mode, boolean online) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(mode);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        if (online) {
            ECProtection.setOnlineOverride(player);
        }
        return player;
    }

    private static void offline(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            ECProtection.clearOnlineOverride(player.getUUID());
        }
    }

    private static void moveTo(GameTestHelper helper, ServerPlayer player, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
    }

    private static DamageSource melee(ServerPlayer player) {
        return player.damageSources().playerAttack(player);
    }

    /** 再打一下前清掉受击无敌（同步测试里两下之间没有刻） */
    private static boolean hit(TestBoss boss, DamageSource source, float amount) {
        boss.invulnerableTime = 0;
        return boss.hurt(source, amount);
    }

    /** 玩家打出来的「真伤」：伤害类型带 bypasses_invulnerability（掉出世界），攻击者是这名玩家 */
    private static DamageSource trueDamage(ServerPlayer player) {
        return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.FELL_OUT_OF_WORLD),
                player);
    }

    /** 当成管理员这一刻敲了 /kill 来做（generic_kill 只在这时不夹封顶，见 {@link BossRules#unblockable}） */
    static void asKillCommand(Runnable body) {
        BossRules.setKillCommandForTest(true);
        try {
            body.run();
        } finally {
            BossRules.setKillCommandForTest(false);
        }
    }

    static <T> T underKillCommand(Supplier<T> body) {
        BossRules.setKillCommandForTest(true);
        try {
            return body.get();
        } finally {
            BossRules.setKillCommandForTest(false);
        }
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

    private static <T extends Entity> List<T> around(GameTestHelper helper, Class<T> type, Vec3 relative, double radius) {
        Vec3 at = helper.absoluteVec(relative);
        return helper.getLevel().getEntitiesOfClass(type, new AABB(at, at).inflate(radius), Entity::isAlive);
    }

    /** 每个池都带 killed_by_player 的表：一颗钻石 */
    private static LootTable diamondTable() {
        LootTable table = LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1F)).add(LootItem.lootTableItem(Items.DIAMOND))
                        .when(LootItemKilledByPlayerCondition.killedByPlayer()))
                .build();
        table.setLootTableId(EssentialCraft5.id("gametest/boss_framework_diamond"));
        return table;
    }

    // ================================================================== 伤害预算

    /** 单次封顶：别的模组在 Hurt 里乘过的也照样夹；伤害账记夹过的数；bypasses_invulnerability 不夹、不记 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkPerHitCap(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.MINI, new Vec3(2.5D, 1D, 2.5D), b -> b.limits = new BossDamageBudget.Limits(10F, 0F));
        ServerPlayer player = player(helper, "bf-cap", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        Consumer<LivingHurtEvent> triple = event -> {
            if (event.getEntity() == boss) {
                event.setAmount(event.getAmount() * 3F);
            }
        };
        try {
            hit(boss, melee(player), 30F);
            helper.assertTrue(near(boss.getHealth(), 90F), "30 点封顶 10，实际剩 " + boss.getHealth());
            helper.assertTrue(near(boss.bossRoster().dealt(player.getUUID()), 10F) && boss.bossRoster().contains(player.getUUID()),
                    "伤害账记夹过的 10，打的人进名单");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingHurtEvent.class, triple);
            try {
                hit(boss, melee(player), 8F);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(triple);
            }
            helper.assertTrue(near(boss.getHealth(), 80F), "别的模组乘到 24 也只放行 10，实际剩 " + boss.getHealth());
            // 掉出世界只在真掉到世界底下时算
            double y = boss.getY();
            boss.moveTo(boss.getX(), helper.getLevel().getMinBuildHeight() - 1D, boss.getZ());
            hit(boss, boss.damageSources().fellOutOfWorld(), 15F);
            boss.moveTo(boss.getX(), y, boss.getZ());
            helper.assertTrue(near(boss.getHealth(), 65F), "掉出世界（bypasses_invulnerability）不夹，实际剩 " + boss.getHealth());
            hit(boss, boss.damageSources().mobAttack(zombie), 30F);
            helper.assertTrue(near(boss.getHealth(), 55F), "生物打的照样夹（小 Boss 谁打都算），实际剩 " + boss.getHealth());
            helper.assertTrue(near(boss.bossRoster().totalDealt(), 30F) && near(boss.bossRoster().playerDealt(), 20F),
                    "合计 30（不记掉出世界）、玩家 20，实际 " + boss.bossRoster().totalDealt() + " / " + boss.bossRoster().playerDealt());
            helper.assertTrue(near(boss.bossRoster().playerShare(), 20F / 30F), "玩家伤害占比 2/3");
        } finally {
            offline(player);
            discard(boss, zombie);
        }
        helper.succeed();
    }

    /** 每秒桶的纯函数：满桶起、按游戏刻回、一下最多拿走剩的、时间倒退不回、容量变小跟着缩；封顶和桶叠用；非正数、NaN 不扣 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkBucketMath(GameTestHelper helper) {
        BossDamageBudget budget = new BossDamageBudget();
        BossDamageBudget.Limits bucket = new BossDamageBudget.Limits(0F, 40F);
        helper.assertTrue(near(budget.take(30F, bucket, 100L), 30F), "满桶先放 30");
        helper.assertTrue(near(budget.take(30F, bucket, 100L), 10F), "同一刻只剩 10");
        helper.assertTrue(near(budget.take(5F, bucket, 100L), 0F), "空了一点都不放");
        helper.assertTrue(near(budget.take(30F, bucket, 110L), 20F), "10 刻回半桶 20");
        helper.assertTrue(near(budget.take(30F, bucket, 1000L), 30F) && near(budget.tokens(bucket, 1000L), 10F), "回满只到 40，不攒");
        helper.assertTrue(near(budget.take(30F, bucket, 900L), 10F), "时间倒退按没过时间算");
        helper.assertTrue(near(budget.tokens(new BossDamageBudget.Limits(0F, 5F), 900L), 0F), "容量改小，桶跟着缩");
        helper.assertTrue(budget.take(Float.NaN, bucket, 2000L) == 0F && budget.take(-5F, bucket, 2000L) == 0F && budget.take(0F, bucket, 2000L) == 0F,
                "非正数、NaN 一律放 0");
        helper.assertTrue(near(budget.tokens(bucket, 2000L), 40F), "放 0 的不扣桶");

        BossDamageBudget both = new BossDamageBudget();
        BossDamageBudget.Limits mixed = new BossDamageBudget.Limits(25F, 40F);
        helper.assertTrue(near(both.take(100F, mixed, 0L), 25F) && near(both.take(100F, mixed, 0L), 15F), "先封顶 25，再夹桶里剩的 15");
        both.reset();
        helper.assertTrue(near(both.tokens(mixed, 0L), 40F), "reset 回满桶");

        BossDamageBudget free = new BossDamageBudget();
        helper.assertTrue(near(free.take(1000F, BossDamageBudget.Limits.NONE, 0L), 1000F)
                && free.tokens(BossDamageBudget.Limits.NONE, 0L) == Float.POSITIVE_INFINITY, "不限时原样放");
        helper.assertTrue(BossDamageBudget.Limits.of(Double.NaN, -3D).equals(BossDamageBudget.Limits.NONE), "配置里的 NaN、负数按 0（不限）");
        helper.assertTrue(BossDamageBudget.Limits.of(10D, 40D).withoutBucket().equals(new BossDamageBudget.Limits(10F, 0F)), "小 Boss 去掉每秒桶");
        helper.succeed();
    }

    /** 同一刻多人共用一个每秒桶（Boss 档）：先打的拿走大头，后打的只剩零头 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkSharedBucket(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), b -> b.limits = new BossDamageBudget.Limits(0F, 30F));
        ServerPlayer first = player(helper, "bf-share1", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        ServerPlayer second = player(helper, "bf-share2", new Vec3(1.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        try {
            hit(boss, melee(first), 25F);
            hit(boss, melee(second), 25F);
            helper.assertTrue(near(boss.getHealth(), 70F), "一秒的份 30 两人共用，实际剩 " + boss.getHealth());
            helper.assertTrue(near(boss.bossRoster().dealt(first.getUUID()), 25F) && near(boss.bossRoster().dealt(second.getUUID()), 5F),
                    "账上 25 / 5，实际 " + boss.bossRoster().ledger());
            helper.assertTrue(boss.bossRoster().contains(first.getUUID()) && boss.bossRoster().contains(second.getUUID()), "两人都进名单");
        } finally {
            offline(first, second);
            discard(boss);
        }
        helper.succeed();
    }

    /** 同一个伤害事件问几次都是同一个数、桶只扣一次（矩阵图谱和封顶谁先跑都一样）；小 Boss 不扣桶；/kill 原样 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkResolveOnce(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), b -> b.limits = new BossDamageBudget.Limits(0F, 25F));
        TestBoss mini = boss(helper, BossTier.MINI, new Vec3(1.5D, 1D, 1.5D), b -> b.limits = new BossDamageBudget.Limits(10F, 25F));
        ServerPlayer player = player(helper, "bf-once", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        try {
            long now = helper.getLevel().getGameTime();
            LivingDamageEvent event = new LivingDamageEvent(boss, melee(player), 30F);
            float a = BossDamageBudget.resolve(event);
            float b = BossDamageBudget.resolve(event);
            helper.assertTrue(near(a, 25F) && near(b, 25F), "同一个事件两次都是 25，实际 " + a + " / " + b);
            helper.assertTrue(near(boss.bossBudget().tokens(boss.limits, now), 0F), "桶只扣了一次（剩 0）");
            helper.assertTrue(near(BossDamageBudget.resolve(new LivingDamageEvent(boss, melee(player), 30F)), 0F), "新的一下桶已经空了");
            helper.assertTrue(near(underKillCommand(() -> BossDamageBudget.resolve(new LivingDamageEvent(boss, boss.damageSources().genericKill(), 30F))), 30F),
                    "管理员敲的 /kill 不夹");
            helper.assertTrue(near(BossDamageBudget.resolve(new LivingDamageEvent(boss, boss.damageSources().genericKill(), 30F)), 0F),
                    "别的代码拿 /kill 同款伤害：照普通的一下走（桶已经空了）");
            float m1 = BossDamageBudget.resolve(new LivingDamageEvent(mini, melee(player), 30F));
            float m2 = BossDamageBudget.resolve(new LivingDamageEvent(mini, melee(player), 30F));
            helper.assertTrue(near(m1, 10F) && near(m2, 10F), "小 Boss 只封顶不扣桶，实际 " + m1 + " / " + m2);
            helper.assertTrue(near(BossDamageBudget.resolve(new LivingDamageEvent(player, melee(player), 30F)), 30F), "不是首领的原样");
        } finally {
            offline(player);
            discard(boss, mini);
        }
        helper.succeed();
    }

    /**
     * 死亡兜底：同在 LOWEST、登记得比本模组晚的监听器把这一下抬到 1000，首领按夹过的数本来死不了 → 取消死亡、生命设回 50 − 10；
     * 按夹过的数本来就该死的照样死
     */
    @GameTest(template = EMPTY)
    public static void bossFrameworkDeathGuard(GameTestHelper helper) {
        TestBoss guarded = boss(helper, BossTier.MINI, new Vec3(2.5D, 1D, 2.5D), b -> b.limits = new BossDamageBudget.Limits(10F, 0F));
        TestBoss doomed = boss(helper, BossTier.MINI, new Vec3(1.5D, 1D, 1.5D), b -> b.limits = new BossDamageBudget.Limits(10F, 0F));
        ServerPlayer player = player(helper, "bf-guard", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        Consumer<LivingDamageEvent> late = event -> {
            if (event.getEntity() == guarded || event.getEntity() == doomed) {
                event.setAmount(1000F);
            }
        };
        guarded.setHealth(50F);
        doomed.setHealth(5F);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, late);
        try {
            hit(guarded, melee(player), 30F);
            hit(doomed, melee(player), 30F);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(late);
        }
        try {
            helper.assertTrue(guarded.isAlive() && near(guarded.getHealth(), 40F) && !guarded.defeated(),
                    "兜底取消死亡、生命设回 40，实际 " + guarded.getHealth() + " / 活着 " + guarded.isAlive());
            helper.assertTrue(doomed.isDeadOrDying(), "夹过以后本来就该死的照样死");
        } finally {
            offline(player);
            discard(guarded, doomed);
            around(helper, ExperienceOrb.class, new Vec3(1.5D, 1D, 1.5D), 2D).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** 配置填 0：封顶 / 桶 0 = 不限；非近战倍率 0 = 不吃非近战；分掉落门槛 0 = 打过就算（没打过的不算） */
    @GameTest(template = EMPTY)
    public static void bossFrameworkZeroConfig(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), b -> b.limits = BossDamageBudget.Limits.of(0D, 0D));
        ServerPlayer player = player(helper, "bf-zero", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        ServerPlayer idle = player(helper, "bf-idle", new Vec3(1.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        Arrow arrow = new Arrow(helper.getLevel(), player);
        ECBossFramework.Config.setTestOverrides(new ECBossFramework.Config.Values(0D, 0D, 0D, DEFAULTS.abandonTicks()));
        try {
            hit(boss, melee(player), 60F);
            helper.assertTrue(near(boss.getHealth(), 40F), "封顶、桶都填 0 = 不限，实际剩 " + boss.getHealth());
            hit(boss, boss.damageSources().arrow(arrow, player), 20F);
            helper.assertTrue(near(boss.getHealth(), 40F), "非近战倍率 0：箭一点不掉，实际剩 " + boss.getHealth());
            helper.assertTrue(near(boss.bossRoster().dealt(player.getUUID()), 60F), "打了 0 的那下不进账");
            boss.bossRoster().add(idle);
            List<ServerPlayer> winners = BossLoot.winners(boss);
            helper.assertTrue(winners.contains(player) && !winners.contains(idle), "门槛 0：打过的算，站着的不算，实际 " + winners.size());
        } finally {
            ECBossFramework.Config.setTestOverrides(null);
            offline(player, idle);
            discard(boss, arrow);
        }
        helper.succeed();
    }

    // ================================================================== 谁打得动

    /**
     * Boss 档只认附近的生存 / 冒险真玩家（来源不公平的一律拒）：生物、假玩家、远处的、旁观的、没有攻击者的、轨道打击、摔落 / 溺水 / 卡墙都拒；
     * /kill 放行；创造模式不限距离但不进名单、不进个人账
     */
    @GameTest(template = EMPTY)
    public static void bossFrameworkFightersOnly(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), b -> b.settings = BossRoster.Settings.arena(8D, 32D));
        ServerPlayer near = player(helper, "bf-near", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        ServerPlayer far = player(helper, "bf-far", new Vec3(2.5D, 1D, 14.5D), GameType.ADVENTURE, true);
        ServerPlayer ghost = player(helper, "bf-spec", new Vec3(1.5D, 1D, 2.5D), GameType.SPECTATOR, true);
        ServerPlayer admin = player(helper, "bf-admin", new Vec3(2.5D, 1D, 30.5D), GameType.CREATIVE, true);
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        try {
            helper.assertTrue(BossRules.accepts(boss, melee(near)), "附近的生存玩家打得动");
            helper.assertFalse(BossRules.accepts(boss, melee(far)), "12 格外（参战距离 8）打不动");
            helper.assertFalse(BossRules.accepts(boss, boss.damageSources().mobAttack(zombie)), "生物打不动");
            helper.assertFalse(BossRules.accepts(boss, boss.damageSources().playerAttack(fake)), "假玩家打不动");
            helper.assertFalse(BossRules.accepts(boss, melee(ghost)), "旁观者打不动");
            helper.assertFalse(BossRules.accepts(boss, boss.damageSources().magic()), "没有攻击者的打不动");
            helper.assertFalse(BossRules.accepts(boss, ECDamageTypes.orbitalStrike(helper.getLevel(), near)), "轨道打击一律拒");
            helper.assertFalse(BossRules.accepts(boss, boss.damageSources().fall()) || BossRules.accepts(boss, boss.damageSources().drown())
                    || BossRules.accepts(boss, boss.damageSources().inWall()) || BossRules.accepts(boss, boss.damageSources().cramming()),
                    "摔落、溺水、卡墙、挤压都拒");
            helper.assertTrue(underKillCommand(() -> BossRules.accepts(boss, boss.damageSources().genericKill())), "管理员敲的 /kill 放行");
            helper.assertFalse(BossRules.accepts(boss, boss.damageSources().genericKill()), "别的代码调 kill()：没有攻击者，打不动");
            helper.assertTrue(BossRules.accepts(boss, melee(admin)), "创造模式不限距离");

            helper.assertFalse(hit(boss, boss.damageSources().mobAttack(zombie), 10F), "生物这一下在攻击事件里就被取消");
            helper.assertFalse(hit(boss, boss.damageSources().playerAttack(fake), 10F), "假玩家同样");
            helper.assertTrue(near(boss.getHealth(), 100F) && boss.bossRoster().totalDealt() == 0F, "被拒的不扣血、不进账");
            hit(boss, melee(admin), 10F);
            helper.assertTrue(near(boss.getHealth(), 90F), "创造模式打得掉血");
            helper.assertFalse(boss.bossRoster().contains(admin.getUUID()) || boss.bossRoster().dealt(admin.getUUID()) > 0F,
                    "创造模式不进名单、不进个人账");
            hit(boss, melee(near), 10F);
            helper.assertTrue(boss.bossRoster().contains(near.getUUID()) && near(boss.bossRoster().dealt(near.getUUID()), 10F), "生存玩家打实了进名单");
            helper.assertTrue(boss.isSkillTarget(near) && !boss.isSkillTarget(far) && !boss.isSkillTarget(zombie), "技能只打名单里的参战者");
        } finally {
            offline(near, far, ghost, admin);
            discard(boss, zombie);
        }
        helper.succeed();
    }

    /** 小 Boss 不过「只认参战者」这一关：生物、摔落照样打得动，非近战不打折 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkMiniTakesAll(GameTestHelper helper) {
        TestBoss mini = boss(helper, BossTier.MINI, new Vec3(2.5D, 1D, 2.5D), null);
        ServerPlayer player = player(helper, "bf-mini", new Vec3(2.5D, 1D, 14.5D), GameType.SURVIVAL, true);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        Arrow arrow = new Arrow(helper.getLevel(), player);
        try {
            helper.assertTrue(BossRules.accepts(mini, mini.damageSources().mobAttack(zombie)) && BossRules.accepts(mini, melee(player)),
                    "生物、远处的玩家都打得动");
            helper.assertTrue(BossRules.nonMeleeMultiplier(mini, mini.damageSources().arrow(arrow, player)) == 1F, "非近战不打折");
            hit(mini, mini.damageSources().fall(), 5F);
            helper.assertTrue(near(mini.getHealth(), 95F), "小 Boss 吃摔落，实际剩 " + mini.getHealth());
            helper.assertTrue(BossTier.MINI.usesBucket() == false && BossTier.BOSS.usesBucket(), "只有 Boss 档用每秒桶");
        } finally {
            offline(player);
            discard(mini, zombie, arrow);
        }
        helper.succeed();
    }

    /** Boss 档非近战（箭）× non_melee_multiplier（默认 0.5），近战不打折；配置改成 1 就不打折 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkNonMelee(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), null);
        ServerPlayer player = player(helper, "bf-arrow", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        Arrow arrow = new Arrow(helper.getLevel(), player);
        try {
            ECBossFramework.Config.setTestOverrides(DEFAULTS);
            try {
                hit(boss, boss.damageSources().arrow(arrow, player), 20F);
                helper.assertTrue(near(boss.getHealth(), 90F), "箭 20 × 0.5，实际剩 " + boss.getHealth());
                hit(boss, melee(player), 20F);
                helper.assertTrue(near(boss.getHealth(), 70F), "近战不打折，实际剩 " + boss.getHealth());
            } finally {
                ECBossFramework.Config.setTestOverrides(null);
            }
            ECBossFramework.Config.setTestOverrides(new ECBossFramework.Config.Values(1D, DEFAULTS.lootShare(), DEFAULTS.pullDamage(), DEFAULTS.abandonTicks()));
            try {
                hit(boss, boss.damageSources().arrow(arrow, player), 20F);
                helper.assertTrue(near(boss.getHealth(), 50F), "倍率 1 不打折，实际剩 " + boss.getHealth());
            } finally {
                ECBossFramework.Config.setTestOverrides(null);
            }
            helper.assertTrue(near(boss.bossRoster().dealt(player.getUUID()), 50F), "账上记打折以后的 10 + 20 + 20");
        } finally {
            offline(player);
            discard(boss, arrow);
        }
        helper.succeed();
    }

    /** 混合端插件取消了攻击事件 / 伤害事件：不扣血、不进名单、不进账、不扣桶 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkHybridCancelled(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), b -> b.limits = new BossDamageBudget.Limits(0F, 30F));
        ServerPlayer player = player(helper, "bf-hybrid", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        Consumer<LivingAttackEvent> denyAttack = event -> {
            if (event.getEntity() == boss) {
                event.setCanceled(true);
            }
        };
        Consumer<LivingDamageEvent> denyDamage = event -> {
            if (event.getEntity() == boss) {
                event.setCanceled(true);
            }
        };
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingAttackEvent.class, denyAttack);
            try {
                helper.assertFalse(hit(boss, melee(player), 20F), "攻击事件被取消，这一下不算");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(denyAttack);
            }
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDamageEvent.class, denyDamage);
            try {
                hit(boss, melee(player), 20F);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(denyDamage);
            }
            helper.assertTrue(near(boss.getHealth(), 100F), "两种取消都不扣血，实际剩 " + boss.getHealth());
            helper.assertFalse(boss.bossRoster().contains(player.getUUID()), "被拦下的攻击不进名单");
            helper.assertTrue(boss.bossRoster().ledger().isEmpty() && boss.bossRoster().totalDealt() == 0F, "不进账");
            helper.assertTrue(near(boss.bossBudget().tokens(boss.limits, helper.getLevel().getGameTime()), 30F), "不扣桶");
        } finally {
            offline(player);
            discard(boss);
        }
        helper.succeed();
    }

    // ================================================================== 名单

    /** 场地：石头地面在 (5, 1, 5)，首领在 (2.5, 2, 2.5)；拉回 4 格、脱战 9 格 */
    private static TestBoss arenaBoss(GameTestHelper helper) {
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.STONE);
        BlockPos floor = helper.absolutePos(new BlockPos(5, 1, 5));
        return boss(helper, BossTier.BOSS, new Vec3(2.5D, 2D, 2.5D), b -> {
            b.settings = BossRoster.Settings.arena(4D, 9D);
            b.arena = floor;
        });
    }

    /** 拉回：跑到 5 格外被拽回场地地面、扣血、留在名单；血条只给名单里的人；pull_damage 填 0 只拽不扣血 */
    @GameTest(template = LARGE)
    public static void bossFrameworkPullBack(GameTestHelper helper) {
        TestBoss boss = arenaBoss(helper);
        ServerPlayer fighter = player(helper, "bf-pull", new Vec3(5.5D, 2D, 10.5D), GameType.SURVIVAL, true);
        ServerPlayer bystander = player(helper, "bf-look", new Vec3(6.5D, 2D, 5.5D), GameType.SURVIVAL, true);
        Vec3 landing = helper.absoluteVec(new Vec3(5.5D, 2D, 5.5D));
        try {
            boss.bossRoster().add(fighter);
            ECBossFramework.Config.setTestOverrides(DEFAULTS);
            try {
                boss.bossRoster().check(helper.getLevel());
            } finally {
                ECBossFramework.Config.setTestOverrides(null);
            }
            helper.assertTrue(fighter.position().distanceTo(landing) < 1D, "拽回场地地面，实际 " + fighter.position());
            helper.assertTrue(fighter.getHealth() < fighter.getMaxHealth(), "拽回扣血");
            helper.assertTrue(boss.bossRoster().contains(fighter.getUUID()), "拽回来照样在名单里");
            helper.assertTrue(boss.bossRoster().bar().getPlayers().contains(fighter) && !boss.bossRoster().bar().getPlayers().contains(bystander),
                    "血条只给名单里的人");

            moveTo(helper, fighter, new Vec3(5.5D, 2D, 10.5D));
            fighter.setHealth(fighter.getMaxHealth());
            ECBossFramework.Config.setTestOverrides(new ECBossFramework.Config.Values(DEFAULTS.nonMeleeMultiplier(), DEFAULTS.lootShare(), 0D,
                    DEFAULTS.abandonTicks()));
            try {
                boss.bossRoster().check(helper.getLevel());
            } finally {
                ECBossFramework.Config.setTestOverrides(null);
            }
            helper.assertTrue(fighter.position().distanceTo(landing) < 1D && fighter.getHealth() == fighter.getMaxHealth(),
                    "pull_damage 0：只拽不扣血，实际 " + fighter.getHealth());
        } finally {
            offline(fighter, bystander);
            discard(boss);
        }
        helper.succeed();
    }

    /** C65：拉回的传送事件被取消（混合端插件）→ 不拉、不扣血、人留在名单、技能照样打他；跑出脱战距离才移出 */
    @GameTest(template = LARGE)
    public static void bossFrameworkPullCancelled(GameTestHelper helper) {
        TestBoss boss = arenaBoss(helper);
        ServerPlayer fighter = player(helper, "bf-tpx", new Vec3(5.5D, 2D, 10.5D), GameType.SURVIVAL, true);
        Vec3 before = fighter.position();
        Consumer<EntityTeleportEvent> cancel = event -> {
            if (event.getEntity() == fighter) {
                event.setCanceled(true);
            }
        };
        try {
            boss.bossRoster().add(fighter);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTeleportEvent.class, cancel);
            try {
                boss.bossRoster().check(helper.getLevel());
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(fighter.position().distanceToSqr(before) < EPS, "传送被取消就不拉，实际 " + fighter.position());
            helper.assertTrue(fighter.getHealth() == fighter.getMaxHealth(), "不扣血，实际 " + fighter.getHealth());
            helper.assertTrue(boss.bossRoster().contains(fighter.getUUID()) && boss.isSkillTarget(fighter), "人留在名单里，技能照样打他");
            moveTo(helper, fighter, new Vec3(5.5D, 2D, 15.5D));
            boss.bossRoster().check(helper.getLevel());
            helper.assertFalse(boss.bossRoster().contains(fighter.getUUID()), "跑出 9 格才脱战");
        } finally {
            offline(fighter);
            discard(boss);
        }
        helper.succeed();
    }

    /**
     * 落点被堵：拉不回来就不拉、不扣血、人留在名单里，连着拉不回来满 abandon_ticks（600 刻，按每次检查 10 刻累计）按脱战移出，首领收到离开通知；
     * 中途回到拉回距离以内就清零重计；存读档带着累计
     */
    @GameTest(template = LARGE)
    public static void bossFrameworkPullStranded(GameTestHelper helper) {
        TestBoss boss = arenaBoss(helper);
        helper.setBlock(new BlockPos(5, 2, 5), Blocks.STONE);
        ServerPlayer fighter = player(helper, "bf-strd", new Vec3(5.5D, 2D, 10.5D), GameType.SURVIVAL, true);
        ServerPlayer back = player(helper, "bf-back", new Vec3(4.5D, 2D, 10.5D), GameType.SURVIVAL, true);
        Vec3 out = fighter.position();
        ECBossFramework.Config.setTestOverrides(DEFAULTS);
        try {
            boss.bossRoster().add(fighter);
            boss.bossRoster().add(back);
            for (int i = 0; i < 59; i++) {
                boss.bossRoster().check(helper.getLevel());
            }
            helper.assertTrue(fighter.position().distanceToSqr(out) < EPS && fighter.getHealth() == fighter.getMaxHealth(), "落点被堵：不拉、不扣血");
            helper.assertTrue(boss.bossRoster().contains(fighter.getUUID()) && boss.bossRoster().strandedTicks(fighter.getUUID()) == 590,
                    "590 刻还在名单里，实际 " + boss.bossRoster().strandedTicks(fighter.getUUID()));
            CompoundTag saved = new CompoundTag();
            boss.bossRoster().save(saved);
            TestBoss copy = new TestBoss(helper.getLevel(), BossTier.BOSS);
            copy.bossRoster().load(saved);
            helper.assertTrue(copy.bossRoster().strandedTicks(fighter.getUUID()) == 590, "存读档带着累计");

            moveTo(helper, back, new Vec3(5.5D, 2D, 7.5D));
            boss.bossRoster().check(helper.getLevel());
            helper.assertFalse(boss.bossRoster().contains(fighter.getUUID()), "满 600 刻按脱战移出");
            helper.assertTrue(boss.left.contains(fighter.getUUID()) && fighter.getHealth() == fighter.getMaxHealth(), "首领收到离开通知，没扣血");
            helper.assertTrue(boss.bossRoster().contains(back.getUUID()) && boss.bossRoster().strandedTicks(back.getUUID()) == 0,
                    "回到拉回距离以内：清零");
            moveTo(helper, back, new Vec3(4.5D, 2D, 10.5D));
            boss.bossRoster().check(helper.getLevel());
            helper.assertTrue(boss.bossRoster().strandedTicks(back.getUUID()) == 10, "再出去从头计");
            helper.assertFalse(boss.left.contains(back.getUUID()), "没离开的不通知");
        } finally {
            ECBossFramework.Config.setTestOverrides(null);
            offline(fighter, back);
            discard(boss);
        }
        helper.succeed();
    }

    /**
     * 被拉的人在落点没有权限（场地在别人的领地里）：不拉、不扣血、人留名单，按拉不回来计时；领地放开后换个人照常拽回。
     * 权限按被拉的这个人探测（探测事件以他的身份发）
     */
    @GameTest(template = LARGE)
    public static void bossFrameworkPullDenied(GameTestHelper helper) {
        TestBoss boss = arenaBoss(helper);
        ServerPlayer fighter = player(helper, "bf-deny", new Vec3(5.5D, 2D, 10.5D), GameType.SURVIVAL, true);
        ServerPlayer fresh = player(helper, "bf-open", new Vec3(4.5D, 2D, 10.5D), GameType.SURVIVAL, true);
        Vec3 out = fighter.position();
        Vec3 landing = helper.absoluteVec(new Vec3(5.5D, 2D, 5.5D));
        ECBossFramework.Config.setTestOverrides(DEFAULTS);
        try {
            boss.bossRoster().add(fighter);
            try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(new BlockPos(5, 2, 5))) {
                boss.bossRoster().check(helper.getLevel());
                helper.assertTrue(claims.breaksBy(new BlockPos(5, 2, 5), fighter.getUUID()) >= 1, "按被拉的人探测落点");
            }
            helper.assertTrue(fighter.position().distanceToSqr(out) < EPS && fighter.getHealth() == fighter.getMaxHealth(), "没权限：不拉、不扣血");
            helper.assertTrue(boss.bossRoster().contains(fighter.getUUID()) && boss.bossRoster().strandedTicks(fighter.getUUID()) == 10,
                    "人留在名单里，按拉不回来计时");

            boss.bossRoster().add(fresh);
            boss.bossRoster().check(helper.getLevel());
            helper.assertTrue(fresh.position().distanceTo(landing) < 1D && fresh.getHealth() < fresh.getMaxHealth(), "领地放开：照常拽回、扣血，实际 " + fresh.position());
        } finally {
            ECBossFramework.Config.setTestOverrides(null);
            offline(fighter, fresh);
            discard(boss);
        }
        helper.succeed();
    }

    /** 有场地的名单设置：pull 填 0 时打得动的距离按 escape、召唤者离开按 escape；pull 比 escape 大时 escape 跟上 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkArenaSettings(GameTestHelper helper) {
        BossRoster.Settings noPull = BossRoster.Settings.arena(0D, 32D);
        helper.assertTrue(noPull.pullRange() == 0D && noPull.engageRange() == 32D && noPull.awayRange() == 32D, "pull 0：不拽、32 格内打得动，实际 " + noPull);
        BossRoster.Settings nan = BossRoster.Settings.arena(Double.NaN, 32D);
        helper.assertTrue(nan.pullRange() == 0D && nan.engageRange() == 32D, "pull 不是数：当 0，实际 " + nan);
        BossRoster.Settings normal = BossRoster.Settings.arena(24D, 48D);
        helper.assertTrue(normal.engageRange() == 24D && normal.awayRange() == 24D && normal.escapeRange() == 48D, "24 / 48，实际 " + normal);
        BossRoster.Settings wide = BossRoster.Settings.arena(60D, 48D);
        helper.assertTrue(wide.escapeRange() == 60D && wide.awayRange() == 60D, "pull 比 escape 大：escape 跟上，实际 " + wide);
        helper.succeed();
    }

    /** 脱战：下线、改创造、死亡事件（当场从每只首领的名单里移出，别人和别的生物不受影响）；脱战不清伤害账 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkLeaveRoster(GameTestHelper helper) {
        TestBoss first = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), null);
        TestBoss second = boss(helper, BossTier.MINI, new Vec3(1.5D, 1D, 1.5D), null);
        ServerPlayer dying = player(helper, "bf-died", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        ServerPlayer creative = player(helper, "bf-crea", new Vec3(2.5D, 1D, 3.5D), GameType.SURVIVAL, true);
        ServerPlayer leaving = player(helper, "bf-quit", new Vec3(1.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        try {
            for (ServerPlayer player : List.of(dying, creative, leaving)) {
                first.bossRoster().add(player);
            }
            second.bossRoster().add(dying);
            second.bossRoster().add(leaving);
            first.bossRoster().noteDamage(melee(dying), 12F);
            BossRules.BossRuleEvents.bossRuleFighterDeath(new LivingDeathEvent(dying, dying.damageSources().generic()));
            helper.assertFalse(first.bossRoster().contains(dying.getUUID()) || second.bossRoster().contains(dying.getUUID()),
                    "死了当场从每只的名单里移出");
            helper.assertTrue(first.left.contains(dying.getUUID()) && second.left.contains(dying.getUUID()), "两只都收到离开通知");
            helper.assertTrue(near(first.bossRoster().dealt(dying.getUUID()), 12F), "伤害账不清");
            BossRules.BossRuleEvents.bossRuleFighterDeath(new LivingDeathEvent(zombie, zombie.damageSources().generic()));
            helper.assertTrue(second.bossRoster().contains(leaving.getUUID()), "别的生物死了不碰名单");

            creative.setGameMode(GameType.CREATIVE);
            offline(leaving);
            first.bossRoster().check(helper.getLevel());
            helper.assertFalse(first.bossRoster().contains(creative.getUUID()), "改创造模式脱战");
            helper.assertFalse(first.bossRoster().contains(leaving.getUUID()), "下线脱战");
            helper.assertTrue(first.left.contains(creative.getUUID()) && first.left.contains(leaving.getUUID()), "脱战的都通知");
            helper.assertFalse(first.bossRoster().add(creative), "创造模式加不进名单");
        } finally {
            offline(dying, creative, leaving);
            discard(first, second, zombie);
        }
        helper.succeed();
    }

    /** 飞行压制：只关参战者的飞行；压制器只认开了压制、还活着的首领的参战者，首领没了就不认 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkFlight(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), null);
        TestBoss guardian = boss(helper, BossTier.MINI, new Vec3(1.5D, 1D, 1.5D), b -> b.settings = BossRoster.Settings.guardian(32D));
        ServerPlayer fighter = player(helper, "bf-fly", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        ServerPlayer bystander = player(helper, "bf-glide", new Vec3(1.5D, 1D, 3.5D), GameType.SURVIVAL, true);
        ServerPlayer guarded = player(helper, "bf-guardfly", new Vec3(0.5D, 1D, 0.5D), GameType.SURVIVAL, true);
        try {
            boss.bossRoster().add(fighter);
            guardian.bossRoster().add(guarded);
            for (ServerPlayer player : List.of(fighter, bystander, guarded)) {
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
            }
            boss.bossRoster().check(helper.getLevel());
            guardian.bossRoster().check(helper.getLevel());
            helper.assertFalse(fighter.getAbilities().flying, "参战者的飞行被关掉");
            helper.assertTrue(bystander.getAbilities().flying && guarded.getAbilities().flying, "不参战的、守卫不压飞行的都不碰");
            helper.assertTrue(BossRoster.isEngaged(fighter) && !BossRoster.isEngaged(bystander) && !BossRoster.isEngaged(guarded),
                    "压制器只认开了压制的首领的参战者");
            boss.discard();
            helper.assertFalse(BossRoster.isEngaged(fighter), "首领没了就不压");
        } finally {
            offline(fighter, bystander, guarded);
            discard(boss, guardian);
        }
        helper.succeed();
    }

    /** 名单空了到点散去（守卫不散）；有人在不散；和平难度当场结束；结束只交一次 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkAbandonPeaceful(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        TestBoss empty = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), null);
        TestBoss watched = boss(helper, BossTier.BOSS, new Vec3(1.5D, 1D, 1.5D), null);
        TestBoss guardian = boss(helper, BossTier.MINI, new Vec3(3.5D, 1D, 3.5D), b -> b.settings = BossRoster.Settings.guardian(32D));
        ServerPlayer player = player(helper, "bf-stay", new Vec3(1.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        try {
            watched.bossRoster().add(player);
            ECBossFramework.Config.setTestOverrides(new ECBossFramework.Config.Values(DEFAULTS.nonMeleeMultiplier(), DEFAULTS.lootShare(),
                    DEFAULTS.pullDamage(), 100));
            try {
                for (int i = 0; i < 99; i++) {
                    empty.serverBossStep();
                }
                helper.assertFalse(empty.isRemoved(), "99 刻还不散");
                empty.serverBossStep();
                for (int i = 0; i < 150; i++) {
                    watched.serverBossStep();
                    guardian.serverBossStep();
                }
            } finally {
                ECBossFramework.Config.setTestOverrides(null);
            }
            helper.assertTrue(empty.isRemoved() && empty.endedWith == ECBossMonster.BossEnd.ABANDONED, "名单空了第 100 刻散去");
            helper.assertFalse(watched.isRemoved() || guardian.isRemoved(), "有人在的不散，守卫不散");
            helper.assertTrue(watched.bossRoster().abandonTicks() == 0 && guardian.bossRoster().abandonTicks() == 0, "计时没走");

            Difficulty old = level.getDifficulty();
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                watched.serverBossStep();
                watched.serverBossStep();
            } finally {
                server.setDifficulty(old, true);
            }
            helper.assertTrue(watched.isRemoved() && watched.endedWith == ECBossMonster.BossEnd.PEACEFUL && watched.bossRoster().ended(),
                    "和平难度当场结束");
        } finally {
            offline(player);
            discard(empty, watched, guardian);
        }
        helper.succeed();
    }

    // ================================================================== 打倒

    /**
     * 多人分账：60% / 30% / 5% / 20%（不在线）。≥ 10% 且在线的两人各一颗钻石进背包（地上没有），5% 的和不在线的没有；
     * 首杀记给分得到的人、只记一次
     */
    @GameTest(template = EMPTY)
    public static void bossFrameworkLootShares(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), b -> b.table = diamondTable());
        ServerPlayer top = player(helper, "bf-top", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        ServerPlayer mid = player(helper, "bf-mid", new Vec3(1.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        ServerPlayer low = player(helper, "bf-low", new Vec3(2.5D, 1D, 3.5D), GameType.SURVIVAL, true);
        ServerPlayer away = player(helper, "bf-away", new Vec3(2.5D, 1D, 1.5D), GameType.SURVIVAL, true);
        String typeId = "minecraft:zombie";
        try {
            boss.bossRoster().noteDamage(melee(top), 59F);
            boss.bossRoster().noteDamage(melee(mid), 30F);
            boss.bossRoster().noteDamage(melee(low), 5F);
            boss.bossRoster().noteDamage(melee(away), 20F);
            offline(away);
            ECBossFramework.Config.setTestOverrides(DEFAULTS);
            try {
                boss.setHealth(1F);
                hit(boss, melee(top), 5F);
            } finally {
                ECBossFramework.Config.setTestOverrides(null);
            }
            helper.assertTrue(boss.isDeadOrDying() && boss.defeated(), "最后一下打死");
            helper.assertTrue(top.getInventory().countItem(Items.DIAMOND) == 1 && mid.getInventory().countItem(Items.DIAMOND) == 1,
                    "60% 和 30% 的各一颗进背包");
            helper.assertTrue(low.getInventory().countItem(Items.DIAMOND) == 0 && away.getInventory().countItem(Items.DIAMOND) == 0,
                    "5% 的、不在线的没有");
            helper.assertTrue(around(helper, ItemEntity.class, new Vec3(2.5D, 1D, 2.5D), 3D).isEmpty(), "地上不掉东西");
            helper.assertTrue(boss.defeatedBy.equals(List.of(top, mid)), "按伤害从多到少：" + boss.defeatedBy);
            helper.assertTrue(PlayerDataEvents.get(top).getBossFirstKills().contains(typeId) && PlayerDataEvents.get(mid).getBossFirstKills().contains(typeId),
                    "分得到的人记首杀");
            helper.assertFalse(PlayerDataEvents.get(low).getBossFirstKills().contains(typeId), "5% 的不算首杀");
            helper.assertTrue(BossLoot.recordVictory(boss, List.of(top, mid)).isEmpty(), "首杀只记一次");
            helper.assertTrue(PlayerDataEvents.get(top).getBossFirstKills().size() == 1, "不重复记");
        } finally {
            offline(top, mid, low, away);
            discard(boss);
            around(helper, ExperienceOrb.class, new Vec3(2.5D, 1D, 2.5D), 3D).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** /kill（没有玩家参与的死亡）：表里每个池都带 killed_by_player，什么都不掉，不算首杀 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkKillNoLoot(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), b -> b.table = diamondTable());
        ServerPlayer player = player(helper, "bf-kill", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        try {
            boss.bossRoster().noteDamage(melee(player), 50F);
            asKillCommand(() -> hit(boss, boss.damageSources().genericKill(), Float.MAX_VALUE));
            helper.assertTrue(boss.isDeadOrDying(), "/kill 打得死");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "不掉东西");
            helper.assertTrue(boss.defeatedBy.isEmpty() && PlayerDataEvents.get(player).getBossFirstKills().isEmpty(), "不算首杀");
        } finally {
            offline(player);
            discard(boss);
            around(helper, ExperienceOrb.class, new Vec3(2.5D, 1D, 2.5D), 3D).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * 有人打过以后被 /kill、掉出世界（没有攻击者、绕过无敌）：最后一下不是参战的人打的，不算打倒——不掉东西、不给经验、不算首杀、不调 onBossDefeated
     */
    @GameTest(template = EMPTY)
    public static void bossFrameworkKillAfterHitNoLoot(GameTestHelper helper) {
        TestBoss killed = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), b -> b.table = diamondTable());
        TestBoss fell = boss(helper, BossTier.BOSS, new Vec3(1.5D, 1D, 1.5D), b -> b.table = diamondTable());
        ServerPlayer player = player(helper, "bf-hitk", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        try {
            helper.assertTrue(hit(killed, melee(player), 10F) && hit(fell, melee(player), 10F), "先各打实一下");
            asKillCommand(() -> hit(killed, killed.damageSources().genericKill(), Float.MAX_VALUE));
            // 掉出世界只在真掉到世界底下时算
            fell.moveTo(fell.getX(), helper.getLevel().getMinBuildHeight() - 1D, fell.getZ());
            hit(fell, fell.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            helper.assertTrue(killed.isDeadOrDying() && fell.isDeadOrDying(), "都打得死");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0 && around(helper, ItemEntity.class, new Vec3(2D, 1D, 2D), 3D).isEmpty(),
                    "刚有人打过也不掉东西");
            helper.assertTrue(around(helper, ExperienceOrb.class, new Vec3(2D, 1D, 2D), 3D).isEmpty(), "不给经验");
            helper.assertTrue(killed.defeatedBy.isEmpty() && fell.defeatedBy.isEmpty() && PlayerDataEvents.get(player).getBossFirstKills().isEmpty(),
                    "不算首杀、不算打倒");
        } finally {
            offline(player);
            discard(killed, fell);
            around(helper, ExperienceOrb.class, new Vec3(2D, 1D, 2D), 4D).forEach(Entity::discard);
            around(helper, ItemEntity.class, new Vec3(2D, 1D, 2D), 4D).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * 玩家打出来的「真伤」（伤害类型绕过无敌、攻击者是玩家）：按参战规矩判（离太远的打不动），照样单次封顶、算非近战；
     * 没有攻击者的 /kill 照旧不夹
     */
    @GameTest(template = EMPTY)
    public static void bossFrameworkPlayerTrueDamage(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), b -> b.limits = new BossDamageBudget.Limits(4F, 0F));
        ServerPlayer player = player(helper, "bf-true", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        ServerPlayer far = player(helper, "bf-tfar", new Vec3(2.5D, 1D, 40.5D), GameType.SURVIVAL, true);
        ECBossFramework.Config.setTestOverrides(DEFAULTS);
        try {
            helper.assertTrue(hit(boss, trueDamage(player), 30F), "附近的参战者打得动");
            helper.assertTrue(near(boss.getHealth(), 96F), "30 × 0.5 = 15，封顶 4，实际剩 " + boss.getHealth());
            helper.assertFalse(hit(boss, trueDamage(far), 30F), "离太远的真伤打不动");
            helper.assertTrue(near(boss.getHealth(), 96F) && !boss.bossRoster().contains(far.getUUID()), "没掉血、没进名单");
            asKillCommand(() -> hit(boss, boss.damageSources().genericKill(), 30F));
            helper.assertTrue(near(boss.getHealth(), 66F), "管理员敲的 /kill 照旧不夹，实际剩 " + boss.getHealth());
        } finally {
            ECBossFramework.Config.setTestOverrides(null);
            offline(player, far);
            discard(boss);
        }
        helper.succeed();
    }

    // ================================================================== 存档、技能实体、免疫

    /** 存读档：名单、伤害账、合计原样；同一份读两次不叠加；最大生命不变；读档后满桶 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkSaveLoad(GameTestHelper helper) {
        BossDamageBudget.Limits limits = new BossDamageBudget.Limits(0F, 30F);
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), b -> b.limits = limits);
        ServerPlayer a = player(helper, "bf-save1", new Vec3(3.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        ServerPlayer b = player(helper, "bf-save2", new Vec3(1.5D, 1D, 2.5D), GameType.SURVIVAL, true);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        TestBoss copy = new TestBoss(helper.getLevel(), BossTier.BOSS);
        copy.limits = limits;
        try {
            boss.bossRoster().noteDamage(melee(a), 30F);
            boss.bossRoster().noteDamage(boss.damageSources().mobAttack(zombie), 10F);
            boss.bossRoster().add(b);
            long now = helper.getLevel().getGameTime();
            boss.bossBudget().take(25F, limits, now);
            CompoundTag tag = boss.saveWithoutId(new CompoundTag());
            copy.load(tag);
            copy.load(tag);
            boss.load(tag);
            for (TestBoss loaded : List.of(copy, boss)) {
                BossRoster roster = loaded.bossRoster();
                helper.assertTrue(roster.participants().size() == 2 && roster.contains(a.getUUID()) && roster.contains(b.getUUID()), "名单原样");
                helper.assertTrue(near(roster.dealt(a.getUUID()), 30F) && roster.ledger().size() == 1, "伤害账不叠加：" + roster.ledger());
                helper.assertTrue(near(roster.totalDealt(), 40F), "合计不叠加：" + roster.totalDealt());
                helper.assertTrue(near(loaded.getMaxHealth(), 100F), "最大生命不变：" + loaded.getMaxHealth());
                helper.assertTrue(near(loaded.bossBudget().tokens(limits, now), 30F), "读档后满桶");
            }
            CompoundTag broken = new CompoundTag();
            CompoundTag rosterTag = new CompoundTag();
            rosterTag.putFloat("total_dealt", Float.NaN);
            broken.put("boss_roster", rosterTag);
            copy.readAdditionalSaveData(broken);
            helper.assertTrue(copy.bossRoster().participants().isEmpty() && copy.bossRoster().totalDealt() == 0F, "坏数据读成空的");
        } finally {
            offline(a, b);
            discard(boss, zombie);
        }
        helper.succeed();
    }

    /** 技能实体上限：按种类数、被移除的自动腾位子、上限 0 一个都不放；首领散去 / 移出世界时收掉 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkSkillSlots(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), null);
        TestBoss other = boss(helper, BossTier.MINI, new Vec3(1.5D, 1D, 1.5D), null);
        List<Entity> spawned = new ArrayList<>();
        try {
            BossSkillSlots slots = boss.skills();
            for (int i = 0; i < 2; i++) {
                helper.assertTrue(slots.hasRoom("orb", 2), "第 " + (i + 1) + " 个放得下");
                Entity orb = helper.spawn(EntityType.SNOWBALL, new Vec3(2.5D, 2D, 2.5D));
                spawned.add(orb);
                slots.track("orb", orb);
            }
            helper.assertFalse(slots.hasRoom("orb", 2), "满了");
            helper.assertTrue(slots.hasRoom("spike", 2) && !slots.hasRoom("spike", 0), "别的种类另算；上限 0 不放");
            spawned.get(0).discard();
            helper.assertTrue(slots.count("orb") == 1 && slots.hasRoom("orb", 2), "被移除的腾出位子");
            Entity mine = helper.spawn(EntityType.SNOWBALL, new Vec3(1.5D, 2D, 1.5D));
            spawned.add(mine);
            other.skills().track("mine", mine);
            boss.dissipate();
            helper.assertTrue(boss.isRemoved() && spawned.get(1).isRemoved() && slots.total() == 0, "散去时收掉还在的");
            other.discard();
            helper.assertTrue(mine.isRemoved(), "首领移出世界时收掉");
        } finally {
            spawned.forEach(Entity::discard);
            discard(boss, other);
        }
        helper.succeed();
    }

    /** Boss 档：不吃状态效果（发光除外）、不骑载具、推不动、不吃击退、没有摔落；小 Boss 照常吃效果、吃击退（两档都不骑载具） */
    @GameTest(template = EMPTY)
    public static void bossFrameworkImmunities(GameTestHelper helper) {
        TestBoss boss = boss(helper, BossTier.BOSS, new Vec3(2.5D, 1D, 2.5D), null);
        TestBoss mini = boss(helper, BossTier.MINI, new Vec3(1.5D, 1D, 1.5D), null);
        Boat boat = helper.spawn(EntityType.BOAT, new Vec3(3.5D, 1D, 3.5D));
        try {
            helper.assertFalse(boss.addEffect(new MobEffectInstance(MobEffects.POISON, 100)) || boss.hasEffect(MobEffects.POISON), "Boss 不吃中毒");
            helper.assertTrue(boss.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100)) && boss.hasEffect(MobEffects.GLOWING), "发光照吃");
            helper.assertTrue(mini.addEffect(new MobEffectInstance(MobEffects.POISON, 100)), "小 Boss 吃中毒");

            helper.assertFalse(boss.startRiding(boat) || mini.startRiding(boat), "不上船");
            helper.assertFalse(boss.startRiding(boat, true) || mini.startRiding(boat, true) || boss.isPassenger() || mini.isPassenger(),
                    "强行上船也被骑乘事件拦下");

            boss.setDeltaMovement(Vec3.ZERO);
            mini.setDeltaMovement(Vec3.ZERO);
            boss.knockback(1D, 1D, 0D);
            mini.knockback(1D, 1D, 0D);
            helper.assertTrue(boss.getDeltaMovement().lengthSqr() < EPS && mini.getDeltaMovement().lengthSqr() > EPS, "Boss 不吃击退，小 Boss 吃");
            helper.assertFalse(boss.isPushable() || boss.isPushedByFluid(), "Boss 推不动、水冲不动");
            helper.assertTrue(mini.isPushable(), "小 Boss 推得动");

            helper.assertFalse(boss.causeFallDamage(12F, 1F, boss.damageSources().fall()), "Boss 没有摔落");
            helper.assertTrue(near(boss.getHealth(), 100F), "摔落不扣血");
            mini.causeFallDamage(12F, 1F, mini.damageSources().fall());
            helper.assertTrue(mini.getHealth() < 100F, "小 Boss 摔得疼");
        } finally {
            discard(boss, mini, boat);
        }
        helper.succeed();
    }

    // ================================================================== 纯函数

    /** 整屏画面护眼：每次切换走满 200 刻、平滑、合计不超过 0.20、闪光全局 ≥ 200 刻一次且峰值 ≤ 0.35、隐藏闪烁时 1/3 且间隔翻倍 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkFxRules(GameTestHelper helper) {
        BossFxRules.Fade fade = new BossFxRules.Fade();
        fade.retarget(1F);
        float previous = 0F;
        for (int i = 1; i < BossFxRules.FADE_TICKS; i++) {
            fade.tick();
            helper.assertTrue(fade.current() >= previous && fade.current() < 1F, "第 " + i + " 刻还没到、只升不降：" + fade.current());
            helper.assertTrue(fade.current() - previous <= 2.5F / BossFxRules.FADE_TICKS, "每刻变化不陡：" + (fade.current() - previous));
            previous = fade.current();
        }
        fade.tick();
        helper.assertTrue(near(fade.current(), 1F), "第 200 刻到位");
        fade.retarget(0.5F);
        for (int i = 0; i < 100; i++) {
            fade.tick();
        }
        helper.assertTrue(fade.current() > 0.5F + EPS, "变浅也要走满 200 刻：" + fade.current());
        fade.retarget(0.5F);
        for (int i = 0; i < 100; i++) {
            fade.tick();
        }
        helper.assertTrue(near(fade.current(), 0.5F), "同一个目标再设一次不重新起算");
        fade.retarget(0F, 20);
        for (int i = 0; i < 199; i++) {
            fade.tick();
        }
        helper.assertFalse(fade.finished(), "要求 20 刻也按 200 刻");
        fade.tick();
        fade.tick();
        helper.assertTrue(fade.finished(), "淡完可以扔掉");

        helper.assertTrue(near(BossFxRules.edgeAlpha(5F), BossFxRules.EDGE_MAX) && BossFxRules.edgeAlpha(Float.NaN) == 0F
                && BossFxRules.EDGE_MAX <= 0.20F, "单层最浓 0.20，NaN 按 0");
        helper.assertTrue(near(BossFxRules.combinedEdge(0.2F, 0.2F, 0.2F, Float.NaN), 0.2F), "几层合计不超过 0.20");
        helper.assertTrue(near(0.3F * BossFxRules.edgeScale(0.3F), 0.2F) && BossFxRules.edgeScale(0.1F) == 1F, "超过就按比例缩");

        BossFxRules.FlashLimiter limiter = new BossFxRules.FlashLimiter();
        helper.assertTrue(limiter.tryFlash(1000L, BossFxRules.flashInterval(false)), "第一次闪");
        helper.assertFalse(limiter.tryFlash(1199L, BossFxRules.flashInterval(false)), "199 刻后不许再闪");
        helper.assertTrue(limiter.tryFlash(1200L, BossFxRules.flashInterval(false)), "200 刻后可以（≤ 0.1 次 / 秒）");
        helper.assertFalse(limiter.tryFlash(1500L, BossFxRules.flashInterval(true)), "隐藏闪烁时间隔翻倍");
        helper.assertTrue(near(BossFxRules.flashPeak(1F, false), BossFxRules.FLASH_MAX) && BossFxRules.FLASH_MAX <= 0.35F, "峰值 ≤ 0.35");
        helper.assertTrue(near(BossFxRules.flashPeak(1F, true), BossFxRules.FLASH_MAX / 3F), "隐藏闪烁只剩 1/3");
        helper.assertTrue(BossFxRules.flashAlpha(0.3F, BossFxRules.FLASH_TICKS) == 0F && BossFxRules.flashAlpha(0.3F, -1F) == 0F
                && near(BossFxRules.flashAlpha(0.3F, 0F), 0.3F), "闪光 12 刻淡完");
        helper.succeed();
    }

    /**
     * 晕影换色也走满 200 刻（batch13.md §5.3.5「护眼」）：lerpRgb 两端和中点、t 越界夹住；矩阵之影换相（暗红 → 紫，强度可能不变）后第 1 刻
     * 颜色离旧色不超过总差的 1/100，每刻变化不陡，第 180 刻还没到、第 200 刻正好是新色；同一颜色再设一次不重新起算；
     * 中途再换色从那一刻的颜色接着走，不跳；snap 直接到位；两刻之间的插值落在两刻颜色之间
     */
    @GameTest(template = EMPTY)
    public static void bossFrameworkFxColorFade(GameTestHelper helper) {
        helper.assertTrue(BossFxRules.lerpRgb(0x000000, 0xFFFFFF, 0F) == 0x000000 && BossFxRules.lerpRgb(0x000000, 0xFFFFFF, 1F) == 0xFFFFFF
                && BossFxRules.lerpRgb(0x000000, 0xFFFFFF, 0.5F) == 0x808080, "lerpRgb 两端和中点");
        int body = MatrixShadeEntity.Phase.BODY.color();
        int mind = MatrixShadeEntity.Phase.MIND.color();
        int matrix = MatrixShadeEntity.Phase.MATRIX.color();
        helper.assertTrue(BossFxRules.lerpRgb(body, mind, 0.5F) == 0x7A2463, "按分量插值：" + Integer.toHexString(BossFxRules.lerpRgb(body, mind, 0.5F)));
        helper.assertTrue(BossFxRules.lerpRgb(body, mind, 2F) == mind && BossFxRules.lerpRgb(body, mind, -1F) == body
                && BossFxRules.lerpRgb(body, mind, Float.NaN) == body, "t 越界夹到 0~1，NaN 按 0");
        helper.assertTrue(BossFxRules.smoothstep(0F) == 0F && BossFxRules.smoothstep(1F) == 1F && near(BossFxRules.smoothstep(0.5F), 0.5F),
                "smoothstep 两端和中点");

        BossFxRules.ColorFade color = new BossFxRules.ColorFade();
        color.snap(body);
        color.tick();
        helper.assertTrue(color.current() == body && color.value(0.5F) == body, "snap 直接到位");
        int span = maxChannelDistance(body, mind);
        color.retarget(mind);
        color.tick();
        helper.assertTrue(maxChannelDistance(color.current(), body) * 100 <= span, "换色后第 1 刻离旧色不超过总差的 1/100："
                + Integer.toHexString(color.current()));
        int previous = color.current();
        for (int i = 2; i < BossFxRules.FADE_TICKS; i++) {
            color.tick();
            helper.assertTrue(maxChannelDistance(color.current(), previous) <= Math.ceil(2.5D * span / BossFxRules.FADE_TICKS) + 1,
                    "第 " + i + " 刻变化不陡");
            int between = color.value(0.5F);
            helper.assertTrue(maxChannelDistance(between, previous) <= maxChannelDistance(color.current(), previous) + 1,
                    "两刻之间的插值落在中间");
            if (i == BossFxRules.FADE_TICKS * 9 / 10) {
                helper.assertTrue(color.current() != mind, "第 180 刻还没到（各分量取整，最后几刻才落到新色）");
            }
            previous = color.current();
        }
        color.tick();
        helper.assertTrue(color.current() == mind, "第 200 刻正好是新色");
        color.retarget(mind);
        color.tick();
        helper.assertTrue(color.current() == mind, "同一颜色再设一次不重新起算");

        color.retarget(matrix);
        for (int i = 0; i < 100; i++) {
            color.tick();
        }
        int midway = color.current();
        helper.assertTrue(midway != mind && midway != matrix, "走到一半");
        color.retarget(body);
        color.tick();
        helper.assertTrue(maxChannelDistance(color.current(), midway) * 100 <= maxChannelDistance(midway, body) + 100,
                "中途再换色从当前颜色接着走，不跳：" + Integer.toHexString(midway) + " → " + Integer.toHexString(color.current()));
        for (int i = 1; i < BossFxRules.FADE_TICKS; i++) {
            color.tick();
        }
        helper.assertTrue(color.current() == body, "重新起的一段也走满 200 刻到位");
        helper.succeed();
    }

    /** 两个 RGB 各分量差的最大值 */
    private static int maxChannelDistance(int a, int b) {
        int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
        int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        return Math.max(dr, Math.max(dg, db));
    }

    /** 动画事件号：第 i 段 = 100 + i，最多 24 段；别的号不认；同通道互斥、通道 0 不互斥；超出段数的事件交回父类 */
    @GameTest(template = EMPTY)
    public static void bossFrameworkAnimEvents(GameTestHelper helper) {
        helper.assertTrue(MobAnimStates.eventId(0) == 100 && MobAnimStates.eventId(23) == 123, "100~123");
        helper.assertTrue(MobAnimStates.slotOf((byte) 99) == -1 && MobAnimStates.slotOf((byte) 124) == -1 && MobAnimStates.slotOf((byte) 3) == -1,
                "原版的号、超出的号都不认");
        boolean threw = false;
        try {
            MobAnimStates.eventId(24);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        helper.assertTrue(threw, "第 25 段直接报错");

        MobAnimStates anims = new MobAnimStates(3).channel(0, 1).channel(1, 1);
        helper.assertTrue(anims.handle(MobAnimStates.eventId(0), 10), "本只怪的事件自己处理");
        AnimationState slam = anims.get(0);
        helper.assertTrue(slam.isStarted(), "起了第 0 段");
        anims.handle(MobAnimStates.eventId(1), 20);
        helper.assertTrue(!slam.isStarted() && anims.get(1).isStarted(), "同通道互斥");
        anims.handle(MobAnimStates.eventId(2), 30);
        helper.assertTrue(anims.get(1).isStarted() && anims.get(2).isStarted(), "通道 0 不互斥");
        helper.assertFalse(anims.handle(MobAnimStates.eventId(5), 40), "超出段数的交回父类");
        helper.assertFalse(anims.handle((byte) 3, 40), "原版事件交回父类");
        anims.freeze(true);
        helper.assertTrue(anims.speed() == 0F, "定格时速度 0");
        anims.freeze(false);
        anims.stopAll();
        helper.assertFalse(anims.get(1).isStarted() || anims.get(2).isStarted(), "全停");
        helper.succeed();
    }

    /** 手册实体预览：scale 夹到 0.25~2、NaN 按 1；nbt 写错读成 null；合并前拿掉位置、UUID 等键；缓存键带 nbt */
    @GameTest(template = EMPTY)
    public static void bossFrameworkEntityPreviewSpec(GameTestHelper helper) {
        helper.assertTrue(EntityPreviewSpec.clampScale(0.1D) == EntityPreviewSpec.MIN_SCALE && EntityPreviewSpec.clampScale(3D) == EntityPreviewSpec.MAX_SCALE
                && EntityPreviewSpec.clampScale(Double.NaN) == 1F && near(EntityPreviewSpec.clampScale(0.5D), 0.5F), "缩放夹范围");
        helper.assertTrue(EntityPreviewSpec.parseNbt("{bad") == null && EntityPreviewSpec.parseNbt("  ") == null && EntityPreviewSpec.parseNbt(null) == null,
                "写错、空的读成 null");
        CompoundTag parsed = EntityPreviewSpec.parseNbt("{Health:5f,Pos:[0d,0d,0d],Motion:[1d,0d,0d],UUID:[I;1,2,3,4],Variant:1b}");
        helper.assertTrue(parsed != null && parsed.contains("Pos"), "合法的读得出来");
        CompoundTag clean = EntityPreviewSpec.sanitized(parsed);
        helper.assertTrue(!clean.contains("Pos") && !clean.contains("Motion") && !clean.contains("UUID") && clean.getByte("Variant") == 1
                && clean.getFloat("Health") == 5F, "拿掉位置、速度、UUID，别的留着");
        helper.assertTrue(parsed.contains("Pos"), "原件不改");
        helper.assertTrue(EntityPreviewSpec.cacheKey("a:b", null).equals("a:b") && !EntityPreviewSpec.cacheKey("a:b", "{x:1}").equals("a:b"),
                "缓存键带 nbt");
        helper.succeed();
    }
}
