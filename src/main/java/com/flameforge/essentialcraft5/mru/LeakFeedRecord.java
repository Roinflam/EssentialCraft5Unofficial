package com.flameforge.essentialcraft5.mru;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 机器的「往哪团聚集体里漏」记录（{@link ECCorruption} 的领地那一关，{@code general.corruption.respect_claims}）：
 * 以机器主人的身份问过的聚集体，记下结论（能动 / 不能动）和到期的游戏时间，每台机器最多 {@value #MAX_ENTRIES} 条。
 * <p>
 * 为什么要记、而且要存盘：问一次就是以主人的身份对聚集体那一格发一次破坏事件，混合端上插件会把它当成主人真的挖了一格
 * （给访客发「没有权限」、刷新挂机判定、给挖掘排行榜计数）。泄漏每 20 刻结算一次，按探测本身 200 刻的缓存问，机器在干活就一直问。
 * 记下来以后，同一团 {@value ECCorruption#FEED_RECORD_TICKS} 刻（1 小时游戏时间）内不再问；主人下线、服务器重启以后，
 * 以前问过「能动」的那团照样认（不然重启后主人不在线，机器就在自己旁边另凝一团没稳定的）。
 * <p>
 * 每条记着那团聚集体当时所在的格子：聚集体被挪到别的格子（移动魔杖）以后这条不算数，主人在线时重新问。
 * 记录属于问的那个人（{@link #by()}）：机器换了主人整份清掉（{@code MRUBlockEntity.setOwner}）。
 * 存进方块实体 NBT 的 {@value #TAG}，不发给客户端。只在服务端线程上用。
 */
public final class LeakFeedRecord {

    /** 方块实体 NBT 里的键 */
    public static final String TAG = "leak_feed";
    /** 每台机器最多记几团：泄漏一次最多试 {@link ECCorruption#MAX_FEED_TRIES} 团 */
    public static final int MAX_ENTRIES = 4;

    private record Entry(UUID presence, long cell, boolean allowed, long until) {
    }

    @Nullable
    private UUID by;
    private final List<Entry> entries = new ArrayList<>(MAX_ENTRIES);

    /** 记录是以谁的身份问出来的；没有记录时为 null */
    @Nullable
    public UUID by() {
        return by;
    }

    /**
     * 记着的结论：这团聚集体（还在记下时那一格）能不能动；没记过、聚集体换了格子返回 null。
     *
     * @param ignoreExpiry true 时过期的也认（主人不在线时认以前的「能动」）
     */
    @Nullable
    public Boolean lookup(UUID presence, long cell, long now, boolean ignoreExpiry) {
        for (Entry entry : entries) {
            if (entry.presence.equals(presence)) {
                if (entry.cell != cell || !ignoreExpiry && entry.until - now <= 0) {
                    return null;
                }
                return entry.allowed;
            }
        }
        return null;
    }

    /** 记下以 owner 的身份问出来的结论。换了人先整份清掉；满了挤掉最早到期的那条 */
    public void put(UUID owner, UUID presence, long cell, boolean allowed, long until) {
        if (!owner.equals(by)) {
            entries.clear();
            by = owner;
        }
        entries.removeIf(entry -> entry.presence.equals(presence));
        if (entries.size() >= MAX_ENTRIES) {
            Entry oldest = entries.get(0);
            for (Entry entry : entries) {
                if (entry.until < oldest.until) {
                    oldest = entry;
                }
            }
            entries.remove(oldest);
        }
        entries.add(new Entry(presence, cell, allowed, until));
    }

    public void clear() {
        entries.clear();
        by = null;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** 写进方块实体的 NBT（空的不写，老存档格式不变） */
    public void save(CompoundTag tag) {
        if (by == null || entries.isEmpty()) {
            return;
        }
        CompoundTag record = new CompoundTag();
        record.putUUID("by", by);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag item = new CompoundTag();
            item.putUUID("presence", entry.presence);
            item.putLong("cell", entry.cell);
            item.putBoolean("allowed", entry.allowed);
            item.putLong("until", entry.until);
            list.add(item);
        }
        record.put("entries", list);
        tag.put(TAG, record);
    }

    /** 从方块实体的 NBT 读（没有这个键就是空的；客户端收到的更新包里也没有） */
    public void load(CompoundTag tag) {
        clear();
        if (!tag.contains(TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag record = tag.getCompound(TAG);
        if (!record.hasUUID("by")) {
            return;
        }
        by = record.getUUID("by");
        ListTag list = record.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && entries.size() < MAX_ENTRIES; i++) {
            CompoundTag item = list.getCompound(i);
            if (item.hasUUID("presence")) {
                entries.add(new Entry(item.getUUID("presence"), item.getLong("cell"), item.getBoolean("allowed"), item.getLong("until")));
            }
        }
        if (entries.isEmpty()) {
            by = null;
        }
    }

    /** GameTest 用：所有记录的到期时间提前 ticks 刻（测「过期以后」，不用真等一小时） */
    public void ageForTest(long ticks) {
        entries.replaceAll(entry -> new Entry(entry.presence, entry.cell, entry.allowed, entry.until - ticks));
    }
}
