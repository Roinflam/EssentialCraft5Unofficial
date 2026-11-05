package com.flameforge.essentialcraft5.entity.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 本模组首领的统一接口（第十三批 13c-Fb，batch13.md §5.3「共同」）：首领通用件（{@link BossRoster} 名单、{@link BossDamageBudget} 封顶、
 * {@link BossRules} 的受伤 / 效果 / 骑乘规矩、{@link BossLoot} 掉落）都按「是不是 ECBoss」认，不按具体实体类，
 * 所以首领可以继承 {@link ECBossMonster}，也可以继承别的基类、自己实现这个接口。
 * <p>
 * 只能由 {@link LivingEntity} 实现（{@link #bossEntity()} 直接强转）。自己实现时要做到 {@link ECBossMonster} 做的那几件：
 * 每刻（服务端）调 {@code bossRoster().tick()}，加进 / 移出世界时调 {@code attach()} / {@code detach()}，存读档调 {@code save} / {@code load}，
 * Boss 档覆盖 {@code isPushable} / {@code isPushedByFluid} 返回 false（推不动没有事件可挂），{@code shouldDespawnInPeaceful} 返回 false（和平难度由名单处理）。
 */
public interface ECBoss {

    /** 档位（规矩按档位给） */
    BossTier bossTier();

    /** 参战名单（每只一份） */
    BossRoster bossRoster();

    /** 伤害预算（每只一份） */
    BossDamageBudget bossBudget();

    /** 这一只现在的单次封顶 / 每秒桶（一般读自己的配置；0 = 不限）。小 Boss 填了每秒桶也不用 */
    BossDamageBudget.Limits bossLimits();

    /** 实际夹伤害用的限额：{@link #bossLimits} 按生命上限被放大的倍数同比放大（{@link BossHealthGuard#limitScaleOf}） */
    default BossDamageBudget.Limits effectiveLimits() {
        BossDamageBudget.Limits limits = bossLimits();
        return this instanceof LivingEntity entity ? limits.scaled(BossHealthGuard.limitScaleOf(entity)) : limits;
    }

    /** 名单的距离与开关（参战、拉回、脱战、散去、飞行压制） */
    BossRoster.Settings rosterSettings();

    /**
     * 场地的地面：拉回时落在它顶上，拉回 / 脱战的距离从它的顶面中心量。null = 没有固定场地，距离从首领自己量，
     * 拉回落点是首领脚下 {@value BossRoster#PULL_FLOOR_SEARCH} 格内第一个有碰撞的方块（找不到就不拉）。
     */
    @Nullable
    default BlockPos arenaFloor() {
        return null;
    }

    /**
     * 这名参战者跑出脱战距离了没有（{@link BossRoster#check} 调；center 是 {@link BossRoster#center()}，range 是 {@code escapeRange}）。
     * 默认按直线距离 &gt; range。守着一个地方、和参战者上下可能隔几十格的首领（聚能魔像出在埋着的遗迹正上方的地面上）覆盖成按水平量。
     */
    default boolean beyondEscape(ServerPlayer player, Vec3 center, double range) {
        return player.position().distanceTo(center) > range;
    }

    /**
     * 首领结束而没被打倒（名单空了到点、和平难度）。掉回召唤物、退还材料都在这里做；{@link ECBossMonster} 默认是原地散去。
     * 名单在调它之后不再管这只首领，实现里要把实体移除（或者交给 M4 那种「200 刻后散去」的自己的计时）。
     */
    void endBoss(BossEnd reason);

    /**
     * 打倒以后（服务端，掉落已经发完）：winners 是打出不少于 {@code loot_share} 有效伤害、此刻在线的参战者（按伤害从多到少）。
     * 首杀已经记进玩家数据、异兽录进度已经发过；这里给各只首领加自己的事（13e 天灾残核首杀 +1、退回信标之类）。
     */
    default void onBossDefeated(List<ServerPlayer> winners) {
    }

    /**
     * 一名参战者离开了名单（脱战、下线、死了、拉不回来满时间；{@link BossRoster} 调）。只发给名单的东西（整屏画面之类）在这里收掉：
     * online 是此刻还在线的那名玩家，下线的是 null。伤害账不动。
     */
    default void onFighterLeft(UUID player, @Nullable ServerPlayer online) {
    }

    /** 实现它的实体本身 */
    default LivingEntity bossEntity() {
        return (LivingEntity) this;
    }

    /** 首领没被打倒就结束的原因 */
    enum BossEnd {
        /** 名单空了到点 */
        ABANDONED,
        /** 和平难度 */
        PEACEFUL
    }
}
