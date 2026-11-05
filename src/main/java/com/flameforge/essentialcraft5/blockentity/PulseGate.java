package com.flameforge.essentialcraft5.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.function.IntSupplier;

/**
 * 红石脉冲的最小间隔闸门（batch4 F4-8），放在方块实体里（{@link RedstonePulseReceiver#pulseGate()}），由 {@code block/RedstonePulse} 驱动。
 * <p>
 * 记三件事：上次真正触发的<b>游戏时间</b>、有没有一个「冷却中到来、还没执行」的上升沿（只记一次）、上一次脉冲是不是还在续做（{@link RedstonePulseReceiver#continuePulse}）。
 * 时间一律用 {@code level.getGameTime()}：有的服务端会按距离给方块实体节流，按方块实体 tick 计数的间隔会被拉长；游戏时间 + 计划刻不受影响。
 * 游戏时间只会往前走（{@code /time} 改的是昼夜时间，不是它）；万一读到的上次时间比现在还晚（存档被拷贝、回档），当成已经可以触发，免得机器永久卡住。
 * <p>
 * 存读档：方块实体在 {@code saveAdditional / load} 里调 {@link #save} / {@link #load}（只存上次时间和挂起的上升沿；续做不存，区块卸载就算这次脉冲做完了）。
 * 挖起再放是新的方块实体，闸门从零开始。
 */
public final class PulseGate {

    /** 从没触发过 */
    public static final long NEVER = Long.MIN_VALUE;

    private final IntSupplier minInterval;
    private long lastFire = NEVER;
    private boolean pending;
    private boolean continuing;

    /** @param minInterval 两次触发之间最少隔几刻（一般读服务端配置，每次现取）；≤0 表示不限 */
    public PulseGate(IntSupplier minInterval) {
        this.minInterval = minInterval;
    }

    /** 当前的最小间隔（刻，≥0） */
    public int interval() {
        return Math.max(0, minInterval.getAsInt());
    }

    /** 上次触发的游戏时间；没触发过为 {@link #NEVER} */
    public long lastFire() {
        return lastFire;
    }

    /** 这一刻能不能触发（离上次 ≥ 间隔） */
    public boolean isReady(long gameTime) {
        return lastFire == NEVER || gameTime < lastFire || gameTime - lastFire >= interval();
    }

    /** 最早什么时候能再触发（游戏时间）；现在就能的返回 {@link #NEVER} */
    public long readyAt() {
        return lastFire == NEVER ? NEVER : lastFire + interval();
    }

    /**
     * 能触发就记下这次（清掉挂起的上升沿、结束上一次的续做）并返回 true；间隔没到返回 false（什么都不改）。
     * 纯逻辑，不管方块、不管计划刻。
     */
    public boolean tryFire(long gameTime) {
        if (!isReady(gameTime)) {
            return false;
        }
        lastFire = gameTime;
        pending = false;
        continuing = false;
        return true;
    }

    /** 收到一个上升沿、等着执行（冷却中再来几个也只记一次） */
    public void markPending() {
        pending = true;
    }

    public boolean isPending() {
        return pending;
    }

    /** 上一次脉冲没做完，下一刻接着做（{@link RedstonePulseReceiver#continuePulse}），最长到间隔结束 */
    public void startContinuing() {
        continuing = true;
    }

    public void stopContinuing() {
        continuing = false;
    }

    public boolean isContinuing() {
        return continuing;
    }

    /** 续做最晚做到哪一刻之前（= {@link #readyAt()}）：到点就算做完，不再续 */
    public boolean mayContinue(long gameTime) {
        return continuing && lastFire != NEVER && gameTime >= lastFire && gameTime < readyAt();
    }

    /**
     * 下一次需要醒来的游戏时间（给计划刻用）：在续做 → 下一刻；有挂起的上升沿 → 间隔结束那一刻（至少下一刻）；都没有返回 {@link #NEVER}。
     */
    public long nextWake(long gameTime) {
        if (continuing) {
            return gameTime + 1;
        }
        if (pending) {
            long ready = readyAt();
            return ready == NEVER ? gameTime + 1 : Math.max(gameTime + 1, ready);
        }
        return NEVER;
    }

    /** 清空（从没触发过、没有挂起、没有续做） */
    public void reset() {
        lastFire = NEVER;
        pending = false;
        continuing = false;
    }

    /** 存进 tag 的 key 下：{@code {last: long, pending: bool}}；从没触发过也没挂起的什么都不写 */
    public void save(CompoundTag tag, String key) {
        if (lastFire == NEVER && !pending) {
            tag.remove(key);
            return;
        }
        CompoundTag data = new CompoundTag();
        if (lastFire != NEVER) {
            data.putLong("last", lastFire);
        }
        if (pending) {
            data.putBoolean("pending", true);
        }
        tag.put(key, data);
    }

    /** 从 tag 的 key 下读（没有就是初始状态） */
    public void load(CompoundTag tag, String key) {
        reset();
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag data = tag.getCompound(key);
        if (data.contains("last", Tag.TAG_LONG)) {
            lastFire = data.getLong("last");
        }
        pending = data.getBoolean("pending");
    }
}
