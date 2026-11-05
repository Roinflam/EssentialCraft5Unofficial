package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已加载的 MRU 聚集体（{@link MRUPresenceEntity}）的索引（13c-Fa，batch13.md §5.1、§5.2 矩阵游魂、§5.3.1 聚能魔像；写法照 {@code util/ForceDomeIndex}）：
 * 每个世界一张「区块 → 那块区块里的聚集体」，找附近的聚集体只查周围几块区块的格子，不用每次在世界里按盒子搜实体。
 * <p>
 * <b>登记</b>：聚集体进世界（新放的、随区块加载读出来的）时登记，离开世界（被吞并、被收进收容瓶、区块卸载）时撤掉，所以<b>只认已加载的</b>，
 * 查询永远不加载区块。聚集体能被移动魔杖挪走：每 {@value #RECHECK_INTERVAL} 刻核一遍每团所在的区块，变了就重排。
 * <p>
 * <b>查询</b>（{@link #within}、{@link #nearest}、{@link #count}）带两个筛选：MRU 至少多少（读实时的 MRU）、要不要跳过「被稳定器压着」的
 * （{@link MRUPresenceEntity#isStabilized}：富集仓外壳里有稳定器、正在凝结 / 坍缩用的种子都压着）。矩阵游魂只在 24 格内有 ≥ 5 000 MRU、
 * 没被压着的聚集体旁边追加、生成时只抽这种（基地里养着的那团不招游魂）；地脉探测仪的「聚集体」档找 64 格内最近的 ≥ 5 000 MRU。
 * <p>
 * <b>线程</b>：登记、撤掉、重排只在服务端主线程做；查询读一份不可变的快照（volatile 引用，整份重建后换掉），世界生成的工作线程上问也不会出错
 * （那时看到的是上一份快照）。主线程上查询时有没重建的改动会先重建。重建的开销是已加载的聚集体总数，平时几十团。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MRUPresenceIndex {

    /** 每隔多少刻核一遍每团所在的区块（被移动魔杖挪过的重排） */
    public static final int RECHECK_INTERVAL = 20;

    private record Where(ResourceKey<Level> dimension, long chunk) {
    }

    /** 主线程：登记着的聚集体 → 登记时所在的世界和区块 */
    private static final Map<MRUPresenceEntity, Where> TRACKED = new IdentityHashMap<>();
    /** 所有线程读：世界 → 区块 → 聚集体（整份不可变，换引用发布） */
    private static volatile Map<ResourceKey<Level>, Long2ObjectOpenHashMap<MRUPresenceEntity[]>> snapshot = Map.of();
    private static boolean dirty;
    private static int recheckTimer;

    private MRUPresenceIndex() {
    }

    // ------------------------------------------------------------------ 查询

    /** 这团能不能被查到：活着、MRU ≥ minMru，要跳过被稳定器压着的时没被压着 */
    public static boolean usable(MRUPresenceEntity presence, int minMru, boolean skipStabilized) {
        return presence.isAlive() && !presence.isRemoved() && presence.getMRU() >= minMru && !(skipStabilized && presence.isStabilized());
    }

    /**
     * center 周围（球，半径 radius）符合条件的聚集体，从近到远排好。
     *
     * @param minMru         MRU 至少多少（0 = 不限）
     * @param skipStabilized true = 被稳定器压着的不算
     */
    public static List<MRUPresenceEntity> within(Level level, Vec3 center, double radius, int minMru, boolean skipStabilized) {
        List<MRUPresenceEntity> found = new ArrayList<>();
        Long2ObjectOpenHashMap<MRUPresenceEntity[]> chunks = chunks(level);
        if (chunks == null || chunks.isEmpty() || !(radius >= 0D)) {
            return found;
        }
        double radiusSq = radius * radius;
        int minX = ((int) Math.floor(center.x - radius)) >> 4;
        int maxX = ((int) Math.floor(center.x + radius)) >> 4;
        int minZ = ((int) Math.floor(center.z - radius)) >> 4;
        int maxZ = ((int) Math.floor(center.z + radius)) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                MRUPresenceEntity[] here = chunks.get(ChunkPos.asLong(cx, cz));
                if (here == null) {
                    continue;
                }
                for (MRUPresenceEntity presence : here) {
                    if (presence.level() == level && presence.distanceToSqr(center) <= radiusSq && usable(presence, minMru, skipStabilized)) {
                        found.add(presence);
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(presence -> presence.distanceToSqr(center)));
        return found;
    }

    /** center 周围（球，半径 radius）最近的一团符合条件的聚集体；没有返回 null */
    @Nullable
    public static MRUPresenceEntity nearest(Level level, Vec3 center, double radius, int minMru, boolean skipStabilized) {
        List<MRUPresenceEntity> found = within(level, center, radius, minMru, skipStabilized);
        return found.isEmpty() ? null : found.get(0);
    }

    /** center 周围符合条件的聚集体有几团 */
    public static int count(Level level, Vec3 center, double radius, int minMru, boolean skipStabilized) {
        return within(level, center, radius, minMru, skipStabilized).size();
    }

    /** 这个世界登记着几团（排查、GameTest 用） */
    public static int size(Level level) {
        Long2ObjectOpenHashMap<MRUPresenceEntity[]> chunks = chunks(level);
        if (chunks == null) {
            return 0;
        }
        int total = 0;
        for (MRUPresenceEntity[] here : chunks.values()) {
            total += here.length;
        }
        return total;
    }

    @Nullable
    private static Long2ObjectOpenHashMap<MRUPresenceEntity[]> chunks(Level level) {
        if (level.isClientSide()) {
            return null;
        }
        if (dirty && level instanceof ServerLevel server && server.getServer().isSameThread()) {
            rebuild();
        }
        return snapshot.get(level.dimension());
    }

    // ------------------------------------------------------------------ 登记

    /** 最低优先级、不收已取消的：别的模组拦下不让进世界的不登记 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void mruIndexJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof MRUPresenceEntity presence && !event.getLevel().isClientSide()) {
            TRACKED.put(presence, where(presence));
            dirty = true;
        }
    }

    @SubscribeEvent
    public static void mruIndexLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof MRUPresenceEntity presence && !event.getLevel().isClientSide() && TRACKED.remove(presence) != null) {
            dirty = true;
        }
    }

    /** 每刻末：到点核一遍位置（挪过区块的重排、已经移除的撤掉），有改动就重建快照 */
    @SubscribeEvent
    public static void mruIndexTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!TRACKED.isEmpty() && ++recheckTimer >= RECHECK_INTERVAL) {
            recheckTimer = 0;
            var iterator = TRACKED.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<MRUPresenceEntity, Where> entry = iterator.next();
                MRUPresenceEntity presence = entry.getKey();
                if (presence.isRemoved() || !presence.isAddedToWorld()) {
                    iterator.remove();
                    dirty = true;
                    continue;
                }
                Where now = where(presence);
                if (!now.equals(entry.getValue())) {
                    entry.setValue(now);
                    dirty = true;
                }
            }
        }
        if (dirty) {
            rebuild();
        }
    }

    @SubscribeEvent
    public static void mruIndexStopped(ServerStoppedEvent event) {
        TRACKED.clear();
        snapshot = Map.of();
        dirty = false;
        recheckTimer = 0;
    }

    private static Where where(Entity entity) {
        return new Where(entity.level().dimension(), ChunkPos.asLong(entity.getBlockX() >> 4, entity.getBlockZ() >> 4));
    }

    private static void rebuild() {
        Map<ResourceKey<Level>, Map<Long, List<MRUPresenceEntity>>> grouped = new HashMap<>();
        for (Map.Entry<MRUPresenceEntity, Where> entry : TRACKED.entrySet()) {
            Where where = entry.getValue();
            grouped.computeIfAbsent(where.dimension(), d -> new HashMap<>())
                    .computeIfAbsent(where.chunk(), c -> new ArrayList<>(1))
                    .add(entry.getKey());
        }
        Map<ResourceKey<Level>, Long2ObjectOpenHashMap<MRUPresenceEntity[]>> built = new HashMap<>();
        grouped.forEach((dimension, chunks) -> {
            Long2ObjectOpenHashMap<MRUPresenceEntity[]> map = new Long2ObjectOpenHashMap<>(chunks.size());
            chunks.forEach((chunk, list) -> map.put(chunk.longValue(), list.toArray(new MRUPresenceEntity[0])));
            built.put(dimension, map);
        });
        snapshot = Map.copyOf(built);
        dirty = false;
    }
}
