package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 每名玩家的击杀台账（13c-Fa，batch13.md §5.4「按人递减」、§3.3.2「同种每小时前 30 只」；policy §4.18）。存在玩家数据的
 * {@code mob_ledger}（{@link IPlayerData#getMobLedger}，返回的就是存着的那个标签，这里原地改，跟着玩家数据存盘），<b>只在服务端</b>、不同步，
 * 死亡、换维度、下线再上都接着算（玩家数据整份复制 / 存盘）。
 * <p>
 * <b>时钟按在线刻走</b>：{@value #KEY_CLOCK} 记这名玩家在线过多少刻（{@code PlayerTickEvent} 每 20 刻加 20），下线期间不走。
 * 每 {@value #BUCKET_TICKS} 刻（5 分钟）一个小桶、共 {@value #BUCKETS} 个，所有计数共用这一个时钟：某一组 / 某一种的计数是一个 12 格的整数数组，
 * 第 i 格是时钟第 i（模 12）个小桶里的数。时钟跨进新的小桶时把那一格清零（一次最多清 12 格），全 0 的数组整条删掉。
 * 合计 12 格 = 最近 55~60 分钟在线时间里的数。
 * <ul>
 *   <li>{@value #KEY_GROUPS}：按人递减的组（{@code essentialcraft5:kill_fatigue} 的 {@code group}）→ 击杀数。同一只怪的同一组只记一次（条件里去重）。</li>
 *   <li>{@value #KEY_TYPES}：实体类型 id → 这名玩家最近一小时从多少只这种生物身上拿过同调点（同调度「同种每小时前 30 只全额」，
 *       {@code AttunementGains}；以前是同调自己按服务器刻记在内存里，13c 起改读这里，关服重开也接着算）。</li>
 * </ul>
 * 大小：时钟一个 long，每个非空的组 / 种类一个 12 格整数数组（约 60 字节）；平时就几组、几种，几百字节。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MobKillLedger {

    public static final int BUCKETS = 12;
    public static final int BUCKET_TICKS = 6_000;
    /** 窗口长度（刻）：12 个小桶 = 一小时 */
    public static final int WINDOW_TICKS = BUCKETS * BUCKET_TICKS;
    /** 在线时钟每隔多少刻推一次 */
    public static final int TICK_STEP = 20;

    public static final String KEY_CLOCK = "c";
    public static final String KEY_GROUPS = "g";
    public static final String KEY_TYPES = "t";

    private MobKillLedger() {
    }

    // ------------------------------------------------------------------ 时钟

    /** 这名玩家在线过的刻数（台账的时钟） */
    public static long clock(IPlayerData data) {
        return data.getMobLedger().getLong(KEY_CLOCK);
    }

    /** 时钟往前走 ticks 刻（跨进新的小桶时清掉那几格）。ticks ≤ 0 不动 */
    public static void advance(IPlayerData data, long ticks) {
        if (ticks <= 0L) {
            return;
        }
        CompoundTag ledger = data.getMobLedger();
        long before = ledger.getLong(KEY_CLOCK);
        long after = before + ticks;
        ledger.putLong(KEY_CLOCK, after);
        long fromBucket = Math.floorDiv(before, BUCKET_TICKS);
        long toBucket = Math.floorDiv(after, BUCKET_TICKS);
        if (toBucket == fromBucket) {
            return;
        }
        long first = Math.max(fromBucket + 1, toBucket - BUCKETS + 1);
        for (String key : new String[]{KEY_GROUPS, KEY_TYPES}) {
            if (!ledger.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag counts = ledger.getCompound(key);
            List<String> empty = new ArrayList<>();
            for (String name : counts.getAllKeys()) {
                int[] slots = slots(counts, name);
                for (long bucket = first; bucket <= toBucket; bucket++) {
                    slots[slot(bucket)] = 0;
                }
                if (sum(slots) == 0) {
                    empty.add(name);
                } else {
                    counts.putIntArray(name, slots);
                }
            }
            empty.forEach(counts::remove);
            if (counts.isEmpty()) {
                ledger.remove(key);
            }
        }
    }

    // ------------------------------------------------------------------ 按人递减的组

    /** 最近一小时在线时间里这一组的击杀数 */
    public static int kills(IPlayerData data, String group) {
        return count(data, KEY_GROUPS, group);
    }

    /** 这一组记一只（当前小桶） */
    public static void addKill(IPlayerData data, String group) {
        add(data, KEY_GROUPS, group);
    }

    // ------------------------------------------------------------------ 同调度的同种计数

    /** 最近一小时在线时间里这名玩家从多少只这种生物身上拿过同调点 */
    public static int typeCount(IPlayerData data, EntityType<?> type) {
        String id = typeId(type);
        return id == null ? 0 : count(data, KEY_TYPES, id);
    }

    /** 这种生物记一只（当前小桶） */
    public static void addType(IPlayerData data, EntityType<?> type) {
        String id = typeId(type);
        if (id != null) {
            add(data, KEY_TYPES, id);
        }
    }

    /** 清掉同种计数（GameTest 用） */
    public static void clearTypes(IPlayerData data) {
        data.getMobLedger().remove(KEY_TYPES);
    }

    // ------------------------------------------------------------------

    @Nullable
    private static String typeId(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return id == null ? null : id.toString();
    }

    private static int count(IPlayerData data, String key, String name) {
        CompoundTag ledger = data.getMobLedger();
        if (!ledger.contains(key, Tag.TAG_COMPOUND)) {
            return 0;
        }
        CompoundTag counts = ledger.getCompound(key);
        return counts.contains(name, Tag.TAG_INT_ARRAY) ? sum(slots(counts, name)) : 0;
    }

    private static void add(IPlayerData data, String key, String name) {
        CompoundTag ledger = data.getMobLedger();
        CompoundTag counts = ledger.getCompound(key);
        int[] slots = slots(counts, name);
        int slot = slot(Math.floorDiv(ledger.getLong(KEY_CLOCK), BUCKET_TICKS));
        if (slots[slot] < Integer.MAX_VALUE) {
            slots[slot]++;
        }
        counts.putIntArray(name, slots);
        ledger.put(key, counts);
    }

    /** 某条的 12 格（没有或长度不对时给一份新的全 0） */
    private static int[] slots(CompoundTag counts, String name) {
        int[] stored = counts.getIntArray(name);
        return stored.length == BUCKETS ? stored : new int[BUCKETS];
    }

    private static int slot(long bucket) {
        return (int) Math.floorMod(bucket, (long) BUCKETS);
    }

    private static int sum(int[] slots) {
        long total = 0L;
        for (int value : slots) {
            total += value;
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** 在线时钟：每 {@value #TICK_STEP} 刻推一次（只在服务端、只给真玩家） */
    @SubscribeEvent
    public static void onMobLedgerPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide() || player.tickCount % TICK_STEP != 0
                || !ECProtection.isRealPlayer(player)) {
            return;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            advance(data, TICK_STEP);
        }
    }
}
