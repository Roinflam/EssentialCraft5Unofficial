package com.flameforge.essentialcraft5.hoanna.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 海纳尔现在的天灾（第六批 6a M4，batch6a.md §5.4）：挂在<b>海纳尔那个 ServerLevel</b> 的 {@code data/essentialcraft5_hoanna_events.dat} 上
 * （全模组第一份 SavedData）。原作是全局 static 加世界根目录的 EC3Data.dat：单人游戏里集成服和客户端共用一份，这里服务端、客户端分开
 * （客户端状态在 {@code client/hoanna/HoannaClientEvents}，靠 {@code S2CHoannaEventPacket} 同步）。
 * <p>
 * 字段：{@code event}（{@link HoannaEvent#id()}，上线后不许改编号）、{@code remaining} / {@code total}（tick）、
 * {@code last_day}（上次看到的「日序号」= dayTime / 24000，黎明掷日阵用；刚建时是 {@link #UNSET_DAY}）。
 * 运行中每 20 tick 才置一次 dirty，只在自动存盘时写（约 100 字节）。{@link #lastSeenTick} 不存盘：上一次「有人在」的那一刻，
 * 用来判断这次日序号变化是不是连续看到的（没人时维度不计时，有人回来时补上的日期变化不算黎明）。
 * <p>
 * 第十三批 13d 地脉风暴（batch13.md §6.2）另加两项：{@code storm_dawn}（风暴截止的那个黎明，绝对 dayTime；只在风暴进行中写）：
 * 风暴截到黎明，没人时停住、/time 往后拨都按它结束；{@link #lastDusk}（上次看到的「黄昏序号」，不存盘，同 {@link #lastSeenTick}），
 * 黄昏掷风暴用。缺 {@code storm_dawn} 的旧存档在第一刻按当时的下一个黎明补上。格式版本不变（只多一个可选键）。
 * <p>
 * GameTest 里被 {@code ECHoanna.setTestHoannaOverride} 当成海纳尔的超平坦主世界也会有一份（存在它自己的 data 目录里），
 * 测试开了事件要在 finally 里停掉。
 */
public final class HoannaEventData extends SavedData {

    public static final String NAME = "essentialcraft5_hoanna_events";
    /** 还没记过日序号 */
    public static final long UNSET_DAY = Long.MIN_VALUE;
    /** 存档格式版本（以后改格式时按它迁移） */
    public static final int VERSION = 1;

    private HoannaEvent event = HoannaEvent.NONE;
    private int remaining;
    private int total;
    private long lastDay = UNSET_DAY;
    /** 上一次有人在海纳尔、跑过一次 tick 的 gameTime（不存盘） */
    long lastSeenTick = Long.MIN_VALUE;
    /** 上一次有人在时看到的黄昏序号 floor((dayTime − 12000) / 24000)（不存盘；{@link #UNSET_DAY} = 还没看过） */
    long lastDusk = UNSET_DAY;
    /** 地脉风暴截止的黎明（绝对 dayTime；不是风暴时为 {@link #UNSET_DAY}） */
    private long stormDawn = UNSET_DAY;

    public HoannaEventData() {
    }

    /** 这个世界的天灾数据（没有就新建）；调用方保证它是海纳尔（{@code ECHoanna.isHoanna}），否则会在别的世界建一份空的 */
    public static HoannaEventData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HoannaEventData::load, HoannaEventData::new, NAME);
    }

    public static HoannaEventData load(CompoundTag tag) {
        HoannaEventData data = new HoannaEventData();
        data.event = HoannaEvent.fromId(tag.getInt("event"));
        data.total = Math.max(0, tag.getInt("total"));
        data.remaining = Math.max(0, Math.min(tag.getInt("remaining"), data.total));
        if (data.event.isNone() || data.total <= 0 || data.remaining <= 0) {
            data.event = HoannaEvent.NONE;
            data.remaining = 0;
            data.total = 0;
        }
        data.lastDay = tag.contains("last_day") ? tag.getLong("last_day") : UNSET_DAY;
        data.stormDawn = data.event == HoannaEvent.LEY_STORM && tag.contains("storm_dawn") ? tag.getLong("storm_dawn") : UNSET_DAY;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", VERSION);
        tag.putInt("event", event.id());
        tag.putInt("remaining", remaining);
        tag.putInt("total", total);
        if (lastDay != UNSET_DAY) {
            tag.putLong("last_day", lastDay);
        }
        if (event == HoannaEvent.LEY_STORM && stormDawn != UNSET_DAY) {
            tag.putLong("storm_dawn", stormDawn);
        }
        return tag;
    }

    public HoannaEvent event() {
        return event;
    }

    public int remaining() {
        return remaining;
    }

    public int total() {
        return total;
    }

    public long lastDay() {
        return lastDay;
    }

    /** 地脉风暴截止的黎明（绝对 dayTime）；不是风暴、或旧存档还没补上时为 {@link #UNSET_DAY} */
    public long stormDawn() {
        return event == HoannaEvent.LEY_STORM ? stormDawn : UNSET_DAY;
    }

    void setStormDawn(long dawn) {
        if (stormDawn != dawn) {
            stormDawn = dawn;
            setDirty();
        }
    }

    public boolean active() {
        return !event.isNone();
    }

    /** 开始一个事件（ticks ≥ 1；NONE 或 ticks ≤ 0 等于 {@link #clear()}） */
    void begin(HoannaEvent event, int ticks) {
        if (event.isNone() || ticks <= 0) {
            clear();
            return;
        }
        this.event = event;
        this.total = ticks;
        this.remaining = ticks;
        this.stormDawn = UNSET_DAY;
        setDirty();
    }

    /** 剩余时间最多留 max tick（日阵截到黄昏用；max ≥ 1，变了才置 dirty；总时长不动，客户端进度条按剩余 / 总时长画） */
    void limitRemaining(int max) {
        if (!event.isNone() && max >= 1 && remaining > max) {
            remaining = max;
            setDirty();
        }
    }

    /** 倒计时一 tick；返回剩下的 tick */
    int countDown() {
        if (remaining > 0) {
            remaining--;
        }
        return remaining;
    }

    void clear() {
        if (!event.isNone() || remaining != 0 || total != 0) {
            event = HoannaEvent.NONE;
            remaining = 0;
            total = 0;
            stormDawn = UNSET_DAY;
            setDirty();
        }
    }

    void setLastDay(long day) {
        if (lastDay != day) {
            lastDay = day;
            setDirty();
        }
    }
}
