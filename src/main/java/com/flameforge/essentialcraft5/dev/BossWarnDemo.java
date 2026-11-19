package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.AbyssTyrantEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartSkills;
import com.flameforge.essentialcraft5.entity.boss.CalamityPillarEntity;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeEntity;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeSkills;
import com.flameforge.essentialcraft5.entity.boss.SentryTurretEntity;
import com.flameforge.essentialcraft5.entity.boss.TownSentryEntity;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemEntity;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.registry.ECMRUGolem;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import com.flameforge.essentialcraft5.registry.ECSunkenDistrict;
import com.flameforge.essentialcraft5.registry.ECTownSentry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 首领招式的演示开关（开发用，只在单人游戏的内置服务端起作用）：录特效对比时让真首领放招，预警走的是正式的服务端代码。
 * 录制工具 / TestHelper 用 {@code setstatic} 往 {@link #request} 写一行指令（同一个进程里的内置服务端下一刻读走），几条用分号隔开。
 * 生成的首领都不带 AI：天灾之心每刻推一下预警中的招，矩阵之影只在蓄招时推，魔像 / 守望者的蓄力在这里数。
 * <pre>
 * heart &lt;x&gt; &lt;y&gt; &lt;z&gt; [主灾]     在这块地面上摆一只天灾之心（场地中心 = 这格，四根灾柱照默认位置），本地玩家进名单；
 *                               主灾 SUN_ARRAY / FUMES / DARKNESS / EARTHQUAKE，不写是 SUN_ARRAY
 * heartskill &lt;招名&gt;             放一招（PRISM / MARK / BREATH / NOON / SHOCKWAVE / RUBBLE / BLACKOUT …），对着本地玩家
 * backlash &lt;dx&gt; &lt;dz&gt;            场地中心偏 (dx, dz) 处一次灾柱反噬
 * converge | combo &lt;0~3&gt;        四灾归心 / 合相技
 * shade &lt;x&gt; &lt;y&gt; &lt;z&gt;            摆一只矩阵之影；shadeskill &lt;招名&gt;（SLAM / CONVERGE / PILLARS …）
 * golem &lt;x&gt; &lt;y&gt; &lt;z&gt; | slam       摆一只聚能魔像 / 让它蓄一次聚能冲击
 * sentry &lt;x&gt; &lt;y&gt; &lt;z&gt; | suppress  摆一只废城守望者 / 让它蓄一次镇压
 * —— 第 2 批（结算画面）加的 ——
 * pulse | throe &lt;n&gt; | rise &lt;0~3&gt;   天灾之心崩解脉冲 / 第 n 次垂死反扑 / 第 i 根灾柱破土光柱
 * hitheart &lt;次数&gt;                本地玩家每 8 刻打天灾之心一下
 * shadephase &lt;body|mind|matrix&gt;  矩阵之影换相（镜影、心智钉只在心相里活着）
 * breakmirror | breakspike       打碎一个镜影 / 心智钉
 * whip                           魔像抬手 10 刻后电弧鞭抽本地玩家
 * turret &lt;dx&gt; &lt;dy&gt; &lt;dz&gt; | fire   在守望者旁边架一座炮台 / 炮台朝本地玩家开一枪
 * tyrant &lt;x&gt; &lt;y&gt; &lt;z&gt; | vortex     摆一只渊潮领主 / 起一次漩涡
 * clear                          删掉这里摆的全部首领
 * —— 实打（{@link CalamityFightBot}）——
 * fight &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;none|mid|max&gt; [主灾]   带 AI 的天灾之心 + 一档装备，自动走位出手，结果写进 {@link #result} 和日志「[实打]」
 * fightstop                      停下
 * </pre>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class BossWarnDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialCraft5/BossWarnDemo");

    /** 待执行的指令（TestHelper setstatic 写进来） */
    public static volatile String request = "";
    /** 上一条指令的结果 */
    public static volatile String result = "";

    @Nullable
    private static CalamityHeartEntity heart;
    @Nullable
    private static MatrixShadeEntity shade;
    @Nullable
    private static MRUGolemEntity golem;
    @Nullable
    private static TownSentryEntity sentry;
    @Nullable
    private static SentryTurretEntity turret;
    @Nullable
    private static AbyssTyrantEntity tyrant;
    private static int golemWindup;
    private static int sentryWindup;
    private static int whipWindup;
    private static int heartHits;
    private static int heartHitClock;

    private BossWarnDemo() {
    }

    @SubscribeEvent
    public static void onBossWarnDemoTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        String line = request;
        if (line != null && !line.isBlank()) {
            request = "";
            ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
            if (player != null) {
                for (String command : line.split(";")) {
                    try {
                        result = run(player.serverLevel(), player, command.trim());
                    } catch (RuntimeException e) {
                        result = "error " + e;
                        LOGGER.warn("[首领演示] 指令出错：{}", command, e);
                    }
                }
            }
        }
        CalamityFightBot.tick(server);
        if (heart != null && !heart.isRemoved() && heart.level() instanceof ServerLevel level) {
            heart.heartSkills().tickEffects(level);
        }
        if (shade != null && !shade.isRemoved() && shade.shadeSkills().casting()) {
            shade.serverBossStep();
        }
        if (golem != null && golemWindup > 0) {
            if (golemWindup % 4 == 0) {
                callIfPresent(golem, "slamWarning");
            }
            if (--golemWindup == 0) {
                golem.slamImpact();
            }
        }
        if (tyrant != null && !tyrant.isRemoved() && tyrant.vortexLeft() > 0) {
            // 摆的首领不带 AI，漩涡要在这里推（正式的是 customServerAiStep 里推）
            tyrant.tickVortex();
        }
        if (golem != null && whipWindup > 0 && --whipWindup == 0) {
            ServerPlayer p = firstPlayer(server);
            if (p != null) {
                golem.whipStrike(p);
            }
        }
        if (heart != null && heartHits > 0 && ++heartHitClock >= 8) {
            heartHitClock = 0;
            heartHits--;
            ServerPlayer p = firstPlayer(server);
            if (p != null) {
                heart.invulnerableTime = 0;
                heart.hurt(p.damageSources().playerAttack(p), 4F);
            }
        }
        if (sentry != null && sentryWindup > 0) {
            if (sentryWindup % 4 == 0) {
                callIfPresent(sentry, "suppressWarning");
            }
            if (--sentryWindup == 0) {
                sentry.suppressImpact();
            }
        }
    }

    private static String run(ServerLevel level, ServerPlayer player, String command) {
        String[] a = command.toLowerCase(Locale.ROOT).split("\\s+");
        switch (a[0]) {
            case "heart" -> {
                BlockPos floor = BlockPos.containing(num(a[1]), num(a[2]), num(a[3]));
                CalamityHeartEntity.Aspect mainAspect = a.length > 4
                        ? CalamityHeartEntity.Aspect.valueOf(a[4].toUpperCase(Locale.ROOT))
                        : CalamityHeartEntity.Aspect.SUN_ARRAY;
                heart = spawn(level, ECCalamityHeart.CALAMITY_HEART.get(), Vec3.atBottomCenterOf(floor).add(0D, 7D, 0D),
                        h -> h.setupArena(floor, mainAspect, null));
                if (heart != null) {
                    heart.syncPillars(level);
                    heart.bossRoster().add(player);
                }
                return "heart " + (heart != null);
            }
            case "fight" -> {
                return CalamityFightBot.start(level, player, a);
            }
            case "fightstop" -> {
                CalamityFightBot.stop();
                return "fightstop";
            }
            case "heartskill" -> {
                need(heart).heartSkills().start(CalamityHeartSkills.Skill.valueOf(a[1].toUpperCase(Locale.ROOT)), player);
                return "heartskill " + a[1];
            }
            case "backlash" -> {
                CalamityHeartEntity h = need(heart);
                Vec3 c = h.arenaCenter();
                h.heartSkills().backlash(level, new Vec3(c.x + num(a[1]), h.floorTop(), c.z + num(a[2])));
                return "backlash";
            }
            case "converge" -> {
                need(heart).heartSkills().converge(level);
                return "converge";
            }
            case "combo" -> {
                need(heart).heartSkills().combo(level, Integer.parseInt(a[1]));
                return "combo " + a[1];
            }
            case "shade" -> {
                shade = spawn(level, ECMatrixShade.MATRIX_SHADE.get(), new Vec3(num(a[1]), num(a[2]), num(a[3])), null);
                if (shade != null) {
                    shade.bossRoster().add(player);
                }
                return "shade " + (shade != null);
            }
            case "shadeskill" -> {
                need(shade).shadeSkills().start(MatrixShadeSkills.Skill.valueOf(a[1].toUpperCase(Locale.ROOT)), player);
                return "shadeskill " + a[1];
            }
            case "golem" -> {
                golem = spawn(level, ECMRUGolem.MRU_GOLEM.get(), new Vec3(num(a[1]), num(a[2]), num(a[3])), MRUGolemEntity::applyConfiguredStats);
                if (golem != null) {
                    // 电弧鞭只打名单里的参战者
                    golem.bossRoster().add(player);
                }
                return "golem " + (golem != null);
            }
            case "slam" -> {
                need(golem).beginSlam();
                golemWindup = ECMRUGolem.Config.values().attacks().slamWarningTicks();
                return "slam";
            }
            case "sentry" -> {
                sentry = spawn(level, ECTownSentry.TOWN_SENTRY.get(), new Vec3(num(a[1]), num(a[2]), num(a[3])), TownSentryEntity::applyConfiguredStats);
                if (sentry != null) {
                    sentry.bossRoster().add(player);
                }
                return "sentry " + (sentry != null);
            }
            case "suppress" -> {
                need(sentry).beginSuppress();
                sentryWindup = ECTownSentry.Config.values().attacks().suppressWarningTicks();
                return "suppress";
            }
            case "pulse" -> {
                CalamityHeartEntity h = need(heart);
                return "pulse " + h.pulse(level);
            }
            case "throe" -> {
                need(heart).heartSkills().deathThroe(level, Integer.parseInt(a[1]));
                return "throe " + a[1];
            }
            case "rise" -> {
                CalamityHeartEntity h = need(heart);
                CalamityPillarEntity pillar = h.pillar(Integer.parseInt(a[1]));
                if (pillar == null) {
                    return "rise no pillar";
                }
                try {
                    Method m = CalamityHeartEntity.class.getDeclaredMethod("pillarBurst", ServerLevel.class, CalamityPillarEntity.class);
                    m.setAccessible(true);
                    m.invoke(h, level, pillar);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                return "rise " + a[1];
            }
            case "hitheart" -> {
                CalamityHeartEntity h = need(heart);
                // 开场演出里、护盾期灾柱立着时本体打不动：跳过演出，破盾窗口开着（不倒柱子，画面上灾柱照旧）
                h.skipIntroForTest();
                try {
                    java.lang.reflect.Field window = CalamityHeartEntity.class.getDeclaredField("shieldWindow");
                    window.setAccessible(true);
                    window.setInt(h, 400);
                    Method flag = CalamityHeartEntity.class.getDeclaredMethod("setFlag", byte.class, boolean.class);
                    flag.setAccessible(true);
                    flag.invoke(h, (byte) 4, true);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                heartHits = Integer.parseInt(a[1]);
                heartHitClock = 7;
                return "hitheart " + heartHits;
            }
            case "shadephase" -> {
                need(shade).enterPhase(MatrixShadeEntity.Phase.valueOf(a[1].toUpperCase(Locale.ROOT)));
                return "shadephase " + a[1];
            }
            case "breakmirror", "breakspike" -> {
                String kind = a[0].equals("breakmirror") ? MatrixShadeSkills.KIND_MIRROR : MatrixShadeSkills.KIND_SPIKE;
                for (Entity e : need(shade).skills().alive(kind)) {
                    if (e instanceof LivingEntity living) {
                        living.invulnerableTime = 0;
                        living.hurt(player.damageSources().playerAttack(player), 999F);
                        return a[0] + " ok";
                    }
                }
                return a[0] + " none";
            }
            case "whip" -> {
                need(golem).beginWhip(player);
                whipWindup = 10;
                return "whip";
            }
            case "turret" -> {
                TownSentryEntity s = need(sentry);
                turret = spawn(level, ECTownSentry.SENTRY_TURRET.get(), s.position().add(num(a[1]), num(a[2]), num(a[3])), t -> t.bind(s));
                return "turret " + (turret != null);
            }
            case "fire" -> {
                return "fire " + need(turret).fire(player);
            }
            case "tyrant" -> {
                tyrant = spawn(level, ECSunkenDistrict.ABYSS_TYRANT.get(), new Vec3(num(a[1]), num(a[2]), num(a[3])), null);
                if (tyrant != null) {
                    tyrant.setNoGravity(true);
                    tyrant.bossRoster().add(player);
                }
                return "tyrant " + (tyrant != null);
            }
            case "vortex" -> {
                need(tyrant).startVortex();
                return "vortex";
            }
            case "clear" -> {
                if (heart != null) {
                    heart.heartSkills().cancelAll(level);
                    heart.skills().discardAll();
                    for (int i = 0; i < 4; i++) {
                        CalamityPillarEntity pillar = heart.pillar(i);
                        if (pillar != null) {
                            pillar.discard();
                        }
                    }
                    heart.discard();
                }
                if (shade != null) {
                    shade.skills().discardAll();
                    shade.discard();
                }
                if (golem != null) {
                    golem.discard();
                }
                if (sentry != null) {
                    sentry.discard();
                }
                if (turret != null) {
                    turret.discard();
                }
                if (tyrant != null) {
                    tyrant.discard();
                }
                heart = null;
                shade = null;
                golem = null;
                sentry = null;
                turret = null;
                tyrant = null;
                golemWindup = sentryWindup = whipWindup = heartHits = 0;
                return "clear";
            }
            default -> throw new IllegalArgumentException("不认识的首领演示指令：" + command);
        }
    }

    @Nullable
    private static ServerPlayer firstPlayer(MinecraftServer server) {
        return server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
    }

    private static <T> T need(@Nullable T boss) {
        if (boss == null) {
            throw new IllegalStateException("先摆首领");
        }
        return boss;
    }

    @Nullable
    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, Vec3 pos, @Nullable Consumer<T> setup) {
        T mob = type.create(level);
        if (mob == null) {
            return null;
        }
        mob.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        mob.setYHeadRot(0F);
        mob.setYBodyRot(0F);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        if (setup != null) {
            setup.accept(mob);
        }
        return level.addFreshEntity(mob) ? mob : null;
    }

    /** 有这个无参方法就调（同一份演示代码要能在改版前后的代码里都编译、都跑） */
    private static void callIfPresent(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            method.invoke(target);
        } catch (NoSuchMethodException ignored) {
            // 改版后预警改成起手发包，没有逐刻画预警的方法了
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[首领演示] 调 {} 出错", name, e);
        }
    }

    private static double num(String s) {
        return Double.parseDouble(s);
    }
}
