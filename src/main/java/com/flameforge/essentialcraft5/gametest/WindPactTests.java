package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.WindTabletItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.WindPact;
import com.flameforge.essentialcraft5.player.WindWishes;
import com.flameforge.essentialcraft5.registry.ECWind;
import net.minecraft.advancements.Advancement;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * 风之契约（第十批 10b，batch10.md §5.8.1，{@link WindPact}）的 GameTest：
 * <ul>
 *   <li>不动如山：潜行 + 好感 7 000 + 真名已显 → 击退取消（真走 {@code LivingEntity.knockback}，速度不变）；不潜行、差 1 点、真名没显（心愿不够，或者门槛调低到
 *       石板门槛以下时好感不够石板）、门槛 0 都照常被击退。</li>
 *   <li>风之偏转：好感 12 000 → 第一支箭穿过，60 刻内新来的照常命中，同一支一直穿过，过了冷却下一支又穿过（真等 61 刻）；
 *       自己射的、差 1 点、真名没显、门槛 0 都不偏；雪球这种非箭类：同一颗连着几刻都放行，冷却内新来的一颗照常命中。</li>
 *   <li>两个进度在门槛处发（6 999 不发、7 000 发不动如山、12 000 发风之偏转）。</li>
 * </ul>
 * 测试玩家是 {@code ProtectionFrameworkTests.realPlayer}（不在世界里）；弹射物不放进世界，直接走 {@code ForgeEventFactory.onProjectileImpactResult}（真发事件、
 * 走真监听）。「真名已显」按风之石板认的那一套：好感数直接写，心愿数写 20（配置上限，不管 {@code required} 被调成多少都够）。
 * 门槛用 {@code WindPact.Config.setTestOverrides}，只在同一段同步代码里设、测、还原；跨刻的那条用默认值。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class WindPactTests {

    private static final String EMPTY = "empty";
    /** 心愿数写满（配置 general.wind_wish.required 的上限） */
    private static final int ALL_WISHES = 20;
    private static final double EPS = 1.0E-9D;

    private WindPactTests() {
    }

    // ================================================================== 不动如山

    /** 潜行 + 7 000 + 真名已显：knockback 什么都不改（事件被取消），默认门槛是 7 000 */
    @GameTest(template = EMPTY)
    public static void pactSteadfastWhileSneaking(GameTestHelper helper) {
        helper.assertTrue(WindPact.Config.DEFAULTS.steadfastPoints() == 7000, "不动如山默认门槛 7 000");
        ServerPlayer player = player(helper, "ec5-pact-st");
        wind(player, 7000, ALL_WISHES);
        helper.assertTrue(WindTabletItem.attuned(player), "好感 7 000、心愿写满时真名应已显");
        player.setShiftKeyDown(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.knockback(0.8D, 1D, 0D);
        helper.assertTrue(player.getDeltaMovement().lengthSqr() < EPS, "潜行时不该被击退，速度 " + player.getDeltaMovement());
        helper.assertTrue(WindPact.resistsKnockback(player), "判定应为不动");
        helper.succeed();
    }

    /** 不潜行、差 1 点、真名没显、门槛 0：照常被击退 */
    @GameTest(template = EMPTY)
    public static void pactSteadfastNeedsSneakPointsAndName(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-pact-st2");
        wind(player, 7000, ALL_WISHES);
        player.setShiftKeyDown(false);
        helper.assertTrue(knockedBack(player), "不潜行应照常被击退");

        player.setShiftKeyDown(true);
        wind(player, 6999, ALL_WISHES);
        helper.assertTrue(knockedBack(player), "差 1 点应照常被击退");

        if (WindWishes.required() > 0) {
            wind(player, 7000, 0);
            helper.assertFalse(WindTabletItem.attuned(player), "一个心愿都没有时真名不该显");
            helper.assertTrue(knockedBack(player), "真名没显（心愿不够）应照常被击退");
        }
        try {
            // 门槛调到石板门槛以下：好感够门槛、却不够石板，真名没显
            int below = Math.max(1, ECWind.Config.tabletPoints() / 4);
            WindPact.Config.setTestOverrides(new WindPact.Config.Values(below, below * 2, 60));
            wind(player, below + 1, ALL_WISHES);
            helper.assertFalse(WindTabletItem.attuned(player), "好感不够石板门槛时真名不该显");
            helper.assertTrue(knockedBack(player), "真名没显（好感不够石板）应照常被击退");

            WindPact.Config.setTestOverrides(new WindPact.Config.Values(0, 12000, 60));
            wind(player, 1_000_000, ALL_WISHES);
            helper.assertTrue(knockedBack(player), "门槛 0 = 关掉，应照常被击退");
        } finally {
            WindPact.Config.setTestOverrides(null);
        }
        helper.succeed();
    }

    // ================================================================== 风之偏转

    /**
     * 12 000：第一支箭穿过（SKIP_ENTITY），紧接着另一支照常命中，第一支再碰到还是穿过；61 刻后（默认冷却 60）新的一支又穿过
     */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void pactDeflectOneThenCooldown(GameTestHelper helper) {
        helper.assertTrue(WindPact.Config.DEFAULTS.deflectPoints() == 12000 && WindPact.Config.DEFAULTS.deflectCooldownTicks() == 60,
                "风之偏转默认门槛 12 000、冷却 60 刻");
        ServerLevel level = helper.getLevel();
        ServerPlayer target = player(helper, "ec5-pact-df");
        ServerPlayer shooter = player(helper, "ec5-pact-sh");
        wind(target, 12000, ALL_WISHES);
        Arrow first = new Arrow(level, shooter);
        Arrow second = new Arrow(level, shooter);
        helper.assertTrue(impact(first, target) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "第一支箭应穿过");
        long left = WindPact.deflectCooldownLeft(target);
        helper.assertTrue(left > 0 && left <= 60, "偏开以后进冷却，剩 " + left);
        helper.assertTrue(impact(second, target) == ProjectileImpactEvent.ImpactResult.DEFAULT, "冷却内新来的一支应照常命中");
        helper.assertTrue(impact(first, target) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "偏过的那一支再碰到还是穿过");
        helper.startSequence()
                .thenIdle(61)
                .thenExecute(() -> {
                    helper.assertTrue(WindPact.deflectCooldownLeft(target) == 0, "61 刻后冷却应结束");
                    Arrow third = new Arrow(level, shooter);
                    helper.assertTrue(impact(third, target) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "冷却过了，新的一支应穿过");
                })
                .thenSucceed();
    }

    /** 自己射的、差 1 点、真名没显、门槛 0：都不偏 */
    @GameTest(template = EMPTY)
    public static void pactDeflectRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = player(helper, "ec5-pact-dr");
        ServerPlayer shooter = player(helper, "ec5-pact-dr2");
        wind(target, 12000, ALL_WISHES);
        helper.assertTrue(impact(new Arrow(level, target), target) == ProjectileImpactEvent.ImpactResult.DEFAULT, "自己射的箭不偏");
        helper.assertTrue(WindPact.deflectCooldownLeft(target) == 0, "自己的箭不该让偏转进冷却");

        wind(target, 11999, ALL_WISHES);
        helper.assertTrue(impact(new Arrow(level, shooter), target) == ProjectileImpactEvent.ImpactResult.DEFAULT, "差 1 点不偏");
        if (WindWishes.required() > 0) {
            wind(target, 12000, 0);
            helper.assertTrue(impact(new Arrow(level, shooter), target) == ProjectileImpactEvent.ImpactResult.DEFAULT, "心愿不够、真名没显不偏");
        }
        try {
            int below = Math.max(1, ECWind.Config.tabletPoints() / 4);
            WindPact.Config.setTestOverrides(new WindPact.Config.Values(below, below, 60));
            wind(target, below + 1, ALL_WISHES);
            helper.assertTrue(impact(new Arrow(level, shooter), target) == ProjectileImpactEvent.ImpactResult.DEFAULT, "好感不够石板、真名没显不偏");

            WindPact.Config.setTestOverrides(new WindPact.Config.Values(7000, 0, 60));
            wind(target, 1_000_000, ALL_WISHES);
            helper.assertTrue(impact(new Arrow(level, shooter), target) == ProjectileImpactEvent.ImpactResult.DEFAULT, "门槛 0 = 关掉，不偏");
        } finally {
            WindPact.Config.setTestOverrides(null);
        }
        helper.succeed();
    }

    /** 雪球（非箭类，SKIP 只跳过这一刻）：同一颗连着三刻都放行，冷却内新来的一颗照常命中 */
    @GameTest(template = EMPTY)
    public static void pactDeflectKeepsPassingSameProjectile(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = player(helper, "ec5-pact-sb");
        ServerPlayer shooter = player(helper, "ec5-pact-sb2");
        wind(target, 12000, ALL_WISHES);
        Snowball snowball = new Snowball(level, shooter);
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(impact(snowball, target) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "同一颗雪球第 " + (i + 1) + " 次碰到应放行");
        }
        Snowball another = new Snowball(level, shooter);
        helper.assertTrue(impact(another, target) == ProjectileImpactEvent.ImpactResult.DEFAULT, "冷却内新来的雪球应照常命中");
        helper.assertTrue(impact(snowball, target) == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "偏过的那颗还是放行");
        helper.succeed();
    }

    // ================================================================== 进度

    /** 6 999 什么都不发；7 000 发不动如山；12 000 发风之偏转 */
    @GameTest(template = EMPTY)
    public static void pactStepsAtThresholds(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-pact-adv");
        wind(player, 6999, ALL_WISHES);
        WindPact.checkSteps(player);
        helper.assertFalse(done(player, WindPact.STEP_STEADFAST), "6 999 时不发不动如山");
        helper.assertFalse(done(player, WindPact.STEP_DEFLECT), "6 999 时不发风之偏转");

        wind(player, 7000, ALL_WISHES);
        WindPact.checkSteps(player);
        helper.assertTrue(done(player, WindPact.STEP_STEADFAST), "7 000 时应发不动如山");
        helper.assertFalse(done(player, WindPact.STEP_DEFLECT), "7 000 时还不发风之偏转");

        wind(player, 12000, ALL_WISHES);
        WindPact.checkSteps(player);
        helper.assertTrue(done(player, WindPact.STEP_DEFLECT), "12 000 时应发风之偏转");
        helper.succeed();
    }

    // ================================================================== 工具

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** 写好感和心愿数（真名按风之石板那一套算） */
    private static void wind(Player player, int points, int wishes) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家没有玩家数据");
        }
        data.setWindPoints(points);
        data.setWindWishes(wishes);
    }

    /** 从静止开始挨一下 knockback，速度变了没有 */
    private static boolean knockedBack(Player player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.knockback(0.8D, 1D, 0D);
        boolean moved = player.getDeltaMovement().lengthSqr() > EPS;
        player.setDeltaMovement(Vec3.ZERO);
        return moved;
    }

    private static ProjectileImpactEvent.ImpactResult impact(Projectile projectile, Player target) {
        return ForgeEventFactory.onProjectileImpactResult(projectile, new EntityHitResult(target));
    }

    private static boolean done(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
}
