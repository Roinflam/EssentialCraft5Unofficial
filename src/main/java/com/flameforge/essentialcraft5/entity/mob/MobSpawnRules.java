package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * 新怪物的刷怪规则工具（13c-Fa，batch13.md §5.1「生成规则」，policy §4.18「刷怪挤占」）。最要紧的是附近上限 {@link #nearbyCount}：
 * 自然刷怪每刻会对同一片地方反复问「这一格能不能刷」，每问一次就数一遍附近的同种实体太贵，所以<b>按（维度, 类型, 区块坐标 &gt;&gt; 2, 半径）缓存 {@value #CACHE_TICKS} 刻</b>：
 * 同一个 64 × 64 格的方块里，40 刻内只数一次。
 * <p>
 * 数法：以那个 64 × 64 方块的中心为圆心、水平 radius 以内（盒子，竖直方向整个世界高度）同种、活着的实体。所以是个近似值
 * （真实位置在方块边上时会多数或少数几只），拿来压密度够用；要精确数的用 {@link #countExact}。
 * 一群怪在同一刻刷出来时缓存跟不上，用 {@link #tryReserve}：判定通过就把缓存的数 +1，下一只照新数判。
 * 预订了以后刷怪又被别的条件拦下（碰撞、刷怪花费、别的模组）也不退，所以这是压密度的节流、不是精确上限：宁可少刷几只。
 * <p>
 * 缓存按服务器刻（{@code MinecraftServer.getTickCount()}）计时，不用各世界的 gameTime：混合端插件建的世界各有一份时钟，
 * 和主世界能差出几千万刻，按世界时间记的条目在主世界的时钟下要么永远不过期、要么一建就被扫掉。
 * <p>
 * 只在服务端主线程上数（世界生成的工作线程上拿不到实体，返回 0：怪物类别本来就不在生成区块时刷）。
 * 刷怪蛋、/summon 不查刷怪规则，不受这里的限制；刷怪笼（{@code MobSpawnType.SPAWNER}）没写自定义规则时照原版要过刷怪规则，
 * {@link #capped} 对它只看附近满没满、不占预订名额（刷怪笼自己另有附近上限）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MobSpawnRules {

    /** 缓存多少刻 */
    public static final int CACHE_TICKS = 40;
    /** 每隔多少刻扫一遍过期的缓存 */
    private static final int PURGE_INTERVAL = 200;

    private record Key(ResourceKey<Level> dimension, EntityType<?> type, int cellX, int cellZ, int radius) {
    }

    private static final class Entry {
        final long expires;
        int count;

        Entry(long expires, int count) {
            this.expires = expires;
            this.count = count;
        }
    }

    private static final Map<Key, Entry> CACHE = new HashMap<>();
    private static int purgeTimer;

    private MobSpawnRules() {
    }

    /**
     * pos 所在的 64 × 64 方块附近（水平 radius 以内）有几只 type（缓存 {@value #CACHE_TICKS} 刻，见类注释）。
     * 不在服务端主线程、或者 level 不是服务端世界时返回 0。
     */
    public static int nearbyCount(LevelAccessor level, EntityType<?> type, BlockPos pos, int radius) {
        Entry entry = entry(level, type, pos, radius);
        return entry == null ? 0 : entry.count;
    }

    /**
     * 附近（同 {@link #nearbyCount}）不到 max 只时返回 true，并把缓存里的数 +1（这一只算预订上了；之后刷怪被拦下也不退，宁可少刷）。
     * max ≤ 0 时一律 false。
     */
    public static boolean tryReserve(LevelAccessor level, EntityType<?> type, BlockPos pos, int radius, int max) {
        if (max <= 0) {
            return false;
        }
        Entry entry = entry(level, type, pos, radius);
        if (entry == null) {
            return true;
        }
        if (entry.count >= max) {
            return false;
        }
        entry.count++;
        return true;
    }

    /**
     * 在 base 的基础上加一条「附近（radius 格）同种不到 max 只」。半径、上限从配置读的传 IntSupplier。
     * 自然刷怪通过时预订一个名额（{@link #tryReserve}）；刷怪笼来问的只比 {@link #nearbyCount}，不预订：刷怪笼有原版自己的附近上限，
     * 不该占掉自然刷怪的名额。
     * 例：{@code ECEntities.spawnPlacement(TYPE, ON_GROUND, …, MobSpawnRules.capped(MyMob::checkSpawn, () -> 32, () -> 4))}。
     */
    public static <T extends Mob> SpawnPlacements.SpawnPredicate<T> capped(SpawnPlacements.SpawnPredicate<T> base, IntSupplier radius, IntSupplier max) {
        return (type, level, spawnType, pos, random) -> {
            if (!base.test(type, level, spawnType, pos, random)) {
                return false;
            }
            int cap = max.getAsInt();
            if (spawnType == MobSpawnType.SPAWNER) {
                return cap > 0 && nearbyCount(level, type, pos, radius.getAsInt()) < cap;
            }
            return tryReserve(level, type, pos, radius.getAsInt(), cap);
        };
    }

    /** 同上，半径和上限是定值 */
    public static <T extends Mob> SpawnPlacements.SpawnPredicate<T> capped(SpawnPlacements.SpawnPredicate<T> base, int radius, int max) {
        return capped(base, () -> radius, () -> max);
    }

    /** 精确数 pos 周围（球，半径 radius）同种活着的实体，不走缓存 */
    public static int countExact(LevelAccessor level, EntityType<?> type, BlockPos pos, int radius) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) {
            return 0;
        }
        double radiusSq = (double) radius * radius;
        AABB box = new AABB(pos).inflate(radius);
        return server.getEntities(type, box, entity -> entity.isAlive() && entity.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= radiusSq).size();
    }

    /** 清空缓存（GameTest 用） */
    public static void clearCache() {
        CACHE.clear();
    }

    /** pos 所在的 64 × 64 方块在缓存里有没有条目（过没过期都算；只看、不新建，GameTest 用） */
    public static boolean isCached(LevelAccessor level, EntityType<?> type, BlockPos pos, int radius) {
        return level instanceof ServerLevel server && CACHE.containsKey(key(server, type, pos, radius));
    }

    /** 扫掉过期满 {@value #CACHE_TICKS} 刻的条目（每 {@value #PURGE_INTERVAL} 刻自动扫一次；GameTest 直接调） */
    public static void purgeExpired(MinecraftServer server) {
        long now = server.getTickCount();
        CACHE.values().removeIf(entry -> entry.expires <= now - CACHE_TICKS);
    }

    private static Key key(ServerLevel level, EntityType<?> type, BlockPos pos, int radius) {
        return new Key(level.dimension(), type, (pos.getX() >> 4) >> 2, (pos.getZ() >> 4) >> 2, radius);
    }

    private static Entry entry(LevelAccessor level, EntityType<?> type, BlockPos pos, int radius) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || radius <= 0) {
            return null;
        }
        Key key = key(server, type, pos, radius);
        long now = server.getServer().getTickCount();
        Entry entry = CACHE.get(key);
        if (entry != null && entry.expires > now) {
            return entry;
        }
        entry = new Entry(now + CACHE_TICKS, count(server, type, key.cellX(), key.cellZ(), radius));
        CACHE.put(key, entry);
        return entry;
    }

    private static int count(ServerLevel level, EntityType<?> type, int cellX, int cellZ, int radius) {
        double centerX = cellX * 64 + 32;
        double centerZ = cellZ * 64 + 32;
        AABB box = new AABB(centerX - radius, level.getMinBuildHeight(), centerZ - radius,
                centerX + radius, level.getMaxBuildHeight(), centerZ + radius);
        return level.getEntities(type, box, Entity::isAlive).size();
    }

    @SubscribeEvent
    public static void onMobSpawnRulesTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CACHE.isEmpty() || ++purgeTimer < PURGE_INTERVAL) {
            return;
        }
        purgeTimer = 0;
        purgeExpired(event.getServer());
    }

    @SubscribeEvent
    public static void onMobSpawnRulesStopped(ServerStoppedEvent event) {
        CACHE.clear();
        purgeTimer = 0;
    }
}
