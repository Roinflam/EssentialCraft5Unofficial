package com.flameforge.essentialcraft5.entity.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * 首领「每人每游戏日一次」的钟（第十三批 13d 预备，13e 渊潮领主、天灾之心、沉城祭坛用；batch13.md §5.3.4、§5.3.5、§9.4）。
 * <p>
 * 日序号 = 主世界的 {@code getDayTime() / 24000}。用主世界的、不用首领所在世界的：海纳尔和混合端插件建的世界的时间可以和主世界不一样，
 * 按各自的世界算，同一个人换个世界打就能一天领两份。
 * <p>
 * 光看日序号不够：{@code doDaylightCycle} 关掉时日内时间不走（睡觉也跳不过夜），日序号永远不变，「今天用过」就成了一辈子用过；
 * {@code /time set day} 把日内时间设成绝对值 1000，日序号拨回 0，之前记过的人要等时间追回去。所以每条记录同时记下那一刻主世界的
 * {@code getGameTime()}（单调递增，不受 {@code /time set}、{@code doDaylightCycle} 影响），「新的一天」= 日序号比记的大（睡觉跳夜照样算），
 * 或者离记下那一刻满 {@value #DAY_TICKS} 刻 gameTime，或者 gameTime 比记的还小（整个世界回到了更早的存档）。
 * 往回拨时间最快也要等满 {@value #DAY_TICKS} 刻，不会多给。
 */
public final class BossDaily {

    /** 一个游戏日的刻数 */
    public static final long DAY_TICKS = 24_000L;
    /** 记录旁边存 gameTime 的键的后缀（{@code <键>_t}） */
    public static final String TIME_SUFFIX = "_t";

    /**
     * 某一刻的钟：日序号 + 主世界 gameTime。
     *
     * @param day      日序号（{@link #dayIndex(long)}）
     * @param gameTime 主世界 {@code getGameTime()}
     */
    public record Stamp(long day, long gameTime) {

        /** 从记下的 (storedDay, storedGameTime) 到这一刻，算不算新的一天 */
        public boolean isNewDaySince(long storedDay, long storedGameTime) {
            return day > storedDay || gameTime - storedGameTime >= DAY_TICKS || gameTime < storedGameTime;
        }
    }

    private BossDaily() {
    }

    /** 现在是第几个游戏日（主世界 {@code getDayTime() / 24000}，负数按 0）；服务器还没有主世界时返回 0 */
    public static long dayIndex(MinecraftServer server) {
        ServerLevel overworld = server == null ? null : server.overworld();
        return overworld == null ? 0L : dayIndex(overworld.getDayTime());
    }

    /** 纯函数：日内时间 → 日序号（GameTest 直接测） */
    public static long dayIndex(long dayTime) {
        return Math.max(0L, dayTime) / DAY_TICKS;
    }

    /** 主世界 gameTime；服务器还没有主世界时返回 0 */
    public static long gameTime(MinecraftServer server) {
        ServerLevel overworld = server == null ? null : server.overworld();
        return overworld == null ? 0L : overworld.getGameTime();
    }

    /** 现在这一刻的钟 */
    public static Stamp now(MinecraftServer server) {
        return new Stamp(dayIndex(server), gameTime(server));
    }

    /**
     * 记录表（玩家数据 {@code boss_daily}）里 key 这一条在 now 这一天用过没有。没有记录 = 没用过；
     * 只有日序号、没有 {@code <key>_t} 的记录只按日序号比（记的 ≥ 今天算用过）。
     */
    public static boolean usedToday(CompoundTag table, String key, Stamp now) {
        if (!table.contains(key, Tag.TAG_LONG)) {
            return false;
        }
        long day = table.getLong(key);
        String timeKey = key + TIME_SUFFIX;
        if (!table.contains(timeKey, Tag.TAG_LONG)) {
            return day >= now.day();
        }
        return !now.isNewDaySince(day, table.getLong(timeKey));
    }

    /** 在记录表里记下 now 用了一次：日序号只往大里改（往回拨时间后不会把记录拨小），gameTime 记成这一刻 */
    public static void mark(CompoundTag table, String key, Stamp now) {
        long day = table.contains(key, Tag.TAG_LONG) ? Math.max(table.getLong(key), now.day()) : now.day();
        table.putLong(key, day);
        table.putLong(key + TIME_SUFFIX, now.gameTime());
    }
}
