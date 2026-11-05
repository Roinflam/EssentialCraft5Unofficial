package com.flameforge.essentialcraft5.block;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/**
 * 装饰方块（MRU 悬浮器、散射器）客户端粒子的「同一格冷却」：同一格 {@value #COOLDOWN_TICKS} tick 内只冒一次（policy §4.4 悬浮器 / 散射器行）。
 * <p>
 * 客户端每 tick 在玩家周围抽 667 × 2 个位置调 {@code animateTick}（{@code ClientLevel.animateTick}），离玩家两三格的方块每 tick 约 10%~15% 会被抽中，
 * 一座城镇约 31 个悬浮器 + 3 个散射器；原作每次抽中冒 48 / 50 个粒子。加了这道冷却后一个悬浮器每秒至多 12 个、散射器至多 10 个。
 * <p>
 * 纯数据、不引用客户端类（两端都能加载，GameTest 直接测）。按格子的 {@code BlockPos#asLong} 记最后一次的游戏时间；
 * 表长到 {@value #PRUNE_SIZE} 条时顺手清掉已经过了冷却的，清完还满就整表清空（最多让几格提前冒一次，无害）。
 * 游戏时间只增不减（/time 改的是日内时间，不是它），倒退只会是换了存档，当作过了冷却。只在客户端主线程用，不加锁。
 */
public final class ParticleCooldown {

    /** 同一格两次冒粒子至少隔几 tick */
    public static final int COOLDOWN_TICKS = 20;
    /** 表长到多少条时清理一次 */
    public static final int PRUNE_SIZE = 512;

    private static final long NEVER = Long.MIN_VALUE;

    private final Long2LongOpenHashMap last = new Long2LongOpenHashMap();

    public ParticleCooldown() {
        last.defaultReturnValue(NEVER);
    }

    /**
     * 这一格现在能不能冒粒子；能的话同时记下这次的时间。
     *
     * @param posKey   {@code BlockPos#asLong}
     * @param gameTime 当前游戏时间（{@code Level#getGameTime}）
     */
    public boolean tryFire(long posKey, long gameTime) {
        long previous = last.get(posKey);
        if (previous != NEVER && gameTime >= previous && gameTime - previous < COOLDOWN_TICKS) {
            return false;
        }
        if (previous == NEVER && last.size() >= PRUNE_SIZE) {
            prune(gameTime);
        }
        last.put(posKey, gameTime);
        return true;
    }

    /** 记着的格子数（测试用） */
    public int size() {
        return last.size();
    }

    public void clear() {
        last.clear();
    }

    private void prune(long gameTime) {
        last.long2LongEntrySet().removeIf(entry -> gameTime < entry.getLongValue() || gameTime - entry.getLongValue() >= COOLDOWN_TICKS);
        if (last.size() >= PRUNE_SIZE) {
            last.clear();
        }
    }
}
