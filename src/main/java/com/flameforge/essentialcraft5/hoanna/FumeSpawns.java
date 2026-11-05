package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.PoisonFumeEntity;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaHazards;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 毒雾的自然生成与海纳尔的刷怪表（第六批 6a M4，batch6a.md §5.4、policy §4.4）。
 * <ul>
 *   <li><b>不进静态刷怪表</b>（原作挂在全部群系的怪物表上，主世界约 1/6 的刷怪组白白落空）：{@code LevelEvent.PotentialSpawns} 只在海纳尔、
 *       怪物类别、毒雾事件进行中、{@code fumes_max_nearby > 0} 时加一条（权重 100、每群 8~16，照原作；不覆写每批上限，实际每批 ≤ 4）。
 *       加的是<b>同一个静态实例</b>：{@code NaturalSpawner.canSpawnMobAt} 会再发一次事件、用 {@code List.contains} 按引用比对选中的那条。</li>
 *   <li><b>刷在哪</b>（{@link #checkSpawn}，登记成 {@code SpawnPlacements}：ON_GROUND + MOTION_BLOCKING_NO_LEAVES）：不是自然生成的（刷怪蛋、指令、刷怪笼）直接放行；
 *       自然生成要求海纳尔、毒雾事件进行中、64 格内的毒雾少于 {@code fumes_max_nearby}（默认 24）。不查亮度（原作 {@code isValidLightLevel} 恒 true，
 *       所以毒雾继承 Mob 而不是 Monster / PathfinderMob，后两者的走路偏好会让它在亮处刷不出来）。</li>
 *   <li><b>计数</b>不做 128³ 的实体查询：{@code EntityJoin / LeaveLevelEvent} 维护每个世界的毒雾集合（弱引用），每个 gameTime 第一次查询时按 64 格分桶，
 *       查询只看周围 3 × 3 桶里水平 64 格内的；同一 tick 里新加入的直接进桶，离开的让本 tick 的桶作废重建。</li>
 *   <li><b>海纳尔不刷被动生物</b>（原作 {@code getPossibleCreatures} L79~84）：同一个钩子里 CREATURE / AMBIENT / WATER_CREATURE / WATER_AMBIENT /
 *       UNDERGROUND_WATER_CREATURE / AXOLOTLS 类别在最低优先级整批取消。群系 JSON 本来就是空的，这是挡别的模组用 biome_modifier 加进来的。</li>
 * </ul>
 * 只在服务端主线程用。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class FumeSpawns {

    /** 自然生成权重、每群数量（原作 EntitiesCore：EntityPoisonFume 100, 8, 16） */
    public static final int WEIGHT = 100;
    public static final int MIN_GROUP = 8;
    public static final int MAX_GROUP = 16;
    /** 数「附近有几只」的水平半径（格） */
    public static final int NEARBY_RADIUS = 64;
    /** 海纳尔里整批取消的刷怪类别 */
    public static final Set<MobCategory> PASSIVE = Collections.unmodifiableSet(EnumSet.of(MobCategory.CREATURE, MobCategory.AMBIENT,
            MobCategory.WATER_CREATURE, MobCategory.WATER_AMBIENT, MobCategory.UNDERGROUND_WATER_CREATURE, MobCategory.AXOLOTLS));

    private static final int BUCKET_SHIFT = 6;

    /** 每个世界的毒雾（弱引用集合 + 本 tick 的分桶） */
    private static final class Tracker {
        final Set<PoisonFumeEntity> fumes = Collections.newSetFromMap(new WeakHashMap<>());
        final Long2ObjectOpenHashMap<List<PoisonFumeEntity>> buckets = new Long2ObjectOpenHashMap<>();
        long builtAt = Long.MIN_VALUE;
    }

    private static final Map<ServerLevel, Tracker> TRACKERS = new WeakHashMap<>();
    @Nullable
    private static MobSpawnSettings.SpawnerData spawner;

    private FumeSpawns() {
    }

    /** 加进刷怪表的那一条（懒建的单例；必须每次都是同一个实例，见类注释） */
    public static MobSpawnSettings.SpawnerData spawnerData() {
        MobSpawnSettings.SpawnerData data = spawner;
        if (data == null) {
            data = new MobSpawnSettings.SpawnerData(ECHoannaHazards.POISON_FUME.get(), WEIGHT, MIN_GROUP, MAX_GROUP);
            spawner = data;
        }
        return data;
    }

    /** 这个世界现在会不会刷毒雾（海纳尔、毒雾进行中、上限 &gt; 0） */
    public static boolean fumesSpawning(ServerLevel level) {
        return HoannaHazardsConfig.fumesMaxNearby() > 0 && HoannaEvents.isActive(level, HoannaEvent.FUMES);
    }

    // ------------------------------------------------------------------ 刷怪表

    @SubscribeEvent
    public static void onFumePotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() == MobCategory.MONSTER && event.getLevel() instanceof ServerLevel level
                && ECHoanna.isHoanna(level) && fumesSpawning(level)) {
            event.addSpawnerData(spawnerData());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHoannaPassiveSpawns(LevelEvent.PotentialSpawns event) {
        if (PASSIVE.contains(event.getMobCategory()) && event.getLevel() instanceof ServerLevel level && ECHoanna.isHoanna(level)) {
            event.setCanceled(true);
        }
    }

    /** 毒雾的 SpawnPlacements 谓词（ECHoannaHazards.init 登记） */
    public static boolean checkSpawn(EntityType<PoisonFumeEntity> type, ServerLevelAccessor accessor, MobSpawnType reason,
                                     BlockPos pos, RandomSource random) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }
        ServerLevel level = accessor.getLevel();
        return ECHoanna.isHoanna(level) && fumesSpawning(level) && nearbyCount(level, pos) < HoannaHazardsConfig.fumesMaxNearby();
    }

    // ------------------------------------------------------------------ 计数

    /** pos 周围水平 {@value #NEARBY_RADIUS} 格内有几只毒雾（本 tick 的分桶） */
    public static int nearbyCount(ServerLevel level, BlockPos pos) {
        Tracker tracker = TRACKERS.get(level);
        if (tracker == null || tracker.fumes.isEmpty()) {
            return 0;
        }
        long now = level.getGameTime();
        if (tracker.builtAt != now) {
            rebuild(level, tracker, now);
        }
        int bx = pos.getX() >> BUCKET_SHIFT;
        int bz = pos.getZ() >> BUCKET_SHIFT;
        double limit = (double) NEARBY_RADIUS * NEARBY_RADIUS;
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<PoisonFumeEntity> bucket = tracker.buckets.get(key(bx + dx, bz + dz));
                if (bucket == null) {
                    continue;
                }
                for (PoisonFumeEntity fume : bucket) {
                    if (fume.isRemoved()) {
                        continue;
                    }
                    double ox = fume.getX() - (pos.getX() + 0.5D);
                    double oz = fume.getZ() - (pos.getZ() + 0.5D);
                    if (ox * ox + oz * oz <= limit) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void rebuild(ServerLevel level, Tracker tracker, long now) {
        tracker.buckets.clear();
        List<PoisonFumeEntity> stale = null;
        for (PoisonFumeEntity fume : tracker.fumes) {
            if (fume.isRemoved() || fume.level() != level) {
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(fume);
                continue;
            }
            insert(tracker, fume);
        }
        if (stale != null) {
            stale.forEach(tracker.fumes::remove);
        }
        tracker.builtAt = now;
    }

    private static void insert(Tracker tracker, PoisonFumeEntity fume) {
        tracker.buckets.computeIfAbsent(key(fume.getBlockX() >> BUCKET_SHIFT, fume.getBlockZ() >> BUCKET_SHIFT), k -> new ArrayList<>(4)).add(fume);
    }

    private static long key(int bx, int bz) {
        return ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
    }

    // 最低优先级、不收已取消的：别的模组取消了加入，这里就看不到，不会数进一只不存在的毒雾
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFumeJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof PoisonFumeEntity fume && event.getLevel() instanceof ServerLevel level) {
            Tracker tracker = TRACKERS.computeIfAbsent(level, l -> new Tracker());
            if (tracker.fumes.add(fume) && tracker.builtAt == level.getGameTime()) {
                insert(tracker, fume);
            }
        }
    }

    @SubscribeEvent
    public static void onFumeLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof PoisonFumeEntity fume && event.getLevel() instanceof ServerLevel level) {
            Tracker tracker = TRACKERS.get(level);
            if (tracker != null && tracker.fumes.remove(fume)) {
                tracker.builtAt = Long.MIN_VALUE;
            }
        }
    }

    @SubscribeEvent
    public static void onFumeServerStopped(ServerStoppedEvent event) {
        TRACKERS.clear();
        spawner = null;
    }
}
