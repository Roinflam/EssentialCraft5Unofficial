package com.flameforge.essentialcraft5.util;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.ForceDomeBlockEntity;
import com.flameforge.essentialcraft5.registry.ECForceDome;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 力场穹顶的刷怪索引（第十批 10c-M4，batch10.md §5.13；policy 第十批 10c「力场穹顶」）：每个世界一张「区块 → 覆盖它的穹顶」，
 * 刷怪检查只做一次哈希查找 + 距离比较。
 * <p>
 * <b>登记什么</b>：穹顶（{@link ForceDomeBlockEntity}）在工作时，把<b>可拦</b>的区块（主人能动方块、结论没过期的）连同各自的到期时刻登记进来。
 * 每条覆盖带：穹顶中心、半径、这块区块的领地结论到哪一刻为止；另有穹顶自己的 {@link Handle#paidUntil}
 * （这一秒的 MRU 付到哪一刻）。两样都没过期、刷怪点在半径以内才拦。穹顶的方块实体不 tick 了（所在区块只加载不 tick）也不会白拦：
 * 付费每秒续一次，停了就过期。
 * <p>
 * MRU 不够、红石暂停、还没确认这几种会来回跳的状态，穹顶只把 {@code paidUntil} 置成「没付」（当刻就不拦），登记留着：下一秒又工作、区块没变时
 * {@link #publish} 什么都不做，不用整份重建快照。停用、没有主人、名额满、全被拒、换主人、卸载、拆掉时才 {@link #withdraw}。
 * <p>
 * <b>线程</b>：登记、撤掉只在服务端主线程做；查询可能来自世界生成的工作线程（生成区块时的初始刷怪 {@code CHUNK_GENERATION} 在工作线程上发
 * {@code SpawnPlacementCheck} / {@code FinalizeSpawn}）。所以查询读的是一份不可变的快照：每次登记 / 撤掉在主线程上整份重建，
 * 写进 volatile 字段，查询只读这个引用，不加锁、不装箱。登记变化很少（穹顶开工、停工、领地结论更新时才有），重建的开销是全部覆盖条数，几百条以内。
 *
 * <h2>拦哪些刷怪（{@link ForceDomeEvents}）</h2>
 * <ul>
 *   <li>{@code MobSpawnEvent.SpawnPlacementCheck}（建实体之前）：{@link MobCategory#MONSTER}、刷怪类型在 {@code machines.force_dome.spawn_types} 里
 *       （默认自然、生成区块、巡逻、增援）给 {@code DENY}。</li>
 *   <li>{@code MobSpawnEvent.FinalizeSpawn}：巡逻队（{@code PatrolSpawner} 直接调巡逻怪自己的规则，不经过 {@code SpawnPlacements.checkSpawnRules}，
 *       看不到上一个事件）和幻翼（{@code PhantomSpawner} 同样不经过它）都走这里：{@code setSpawnCancelled(true)}（Forge 在实体进世界时拦下，
 *       判的是 {@code isSpawnCancelled()}，不是 {@code isCanceled()}，同 {@code entity/HologramEvents}）。同样的刷怪类型、同样的类别，
 *       别的模组自己调 {@code finalizeSpawn} 的自然刷怪也一并拦下。</li>
 *   <li>不管：刷怪笼、刷怪蛋、发射器、指令、结构、袭击（这些刷怪类型写不进名单，见 {@link ForceDomeBlockEntity.Config#ALLOWED_SPAWN_TYPES}）；
 *       {@code #forge:bosses}、{@code #essentialcraft5:dome_exempt}；
 *       不是 MONSTER 类别的（动物、水生、村民）。只拦刷出，不碰已经在世界里的生物。</li>
 * </ul>
 * 两个监听器都在 LOW：别的模组先决定，这里只在还没被拒 / 取消时再判。
 */
public final class ForceDomeIndex {

    /**
     * 一台穹顶在索引里的身份（每台方块实体一个，穹顶自己持有）。{@link #paidUntil} 任何线程都读，主线程写；其余字段只在主线程上碰。
     */
    public static final class Handle {
        /** 这一秒的 MRU 付到哪一刻（游戏时间，不含）；没付是 {@link Long#MIN_VALUE} */
        volatile long paidUntil = Long.MIN_VALUE;
        @Nullable
        private ResourceKey<Level> dimension;
        private double centerX;
        private double centerZ;
        private double radiusSq;
        /** 登记着的区块 → 领地结论到哪一刻为止 */
        private final Long2LongOpenHashMap chunks = new Long2LongOpenHashMap();

        /** 付费到哪一刻（游戏时间，不含）；穹顶每次付完一秒调 */
        public void setPaidUntil(long gameTime) {
            paidUntil = gameTime;
        }

        public long paidUntil() {
            return paidUntil;
        }
    }

    /** 一条覆盖：哪台穹顶、中心、半径的平方、这块区块的领地结论到哪一刻为止 */
    private record Cover(Handle handle, double centerX, double centerZ, double radiusSq, long claimUntil) {
    }

    /** 主线程：每台登记着的穹顶 */
    private static final Map<Handle, Boolean> ACTIVE = new IdentityHashMap<>();
    /** 所有线程读：世界 → 区块 → 覆盖（整份不可变，换引用发布） */
    private static volatile Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Cover[]>> snapshot = Map.of();
    /** 重建过几次快照（只在主线程改；GameTest 用它断言来回跳的穹顶不重建） */
    private static int rebuilds;

    private ForceDomeIndex() {
    }

    /**
     * 登记（或更新）一台穹顶的覆盖，替换它以前登记的全部区块。主线程调。
     *
     * @param centerX 穹顶中心（方块坐标 + 0.5）
     * @param chunks  可拦的区块 → 领地结论到哪一刻为止；空 = 等于 {@link #withdraw}
     */
    public static void publish(Handle handle, ResourceKey<Level> dimension, double centerX, double centerZ, int radius, Long2LongMap chunks) {
        if (chunks.isEmpty()) {
            withdraw(handle);
            return;
        }
        double radiusSq = (double) radius * radius;
        if (ACTIVE.containsKey(handle) && dimension == handle.dimension && centerX == handle.centerX && centerZ == handle.centerZ
                && radiusSq == handle.radiusSq && handle.chunks.equals(chunks)) {
            return;
        }
        handle.dimension = dimension;
        handle.centerX = centerX;
        handle.centerZ = centerZ;
        handle.radiusSq = radiusSq;
        handle.chunks.clear();
        handle.chunks.putAll(chunks);
        ACTIVE.put(handle, Boolean.TRUE);
        rebuild();
    }

    /** 撤掉一台穹顶的全部覆盖（停用、没有主人、名额满、全被拒、换主人、卸载、拆掉）。主线程调；没登记过只把付费置成「没付」 */
    public static void withdraw(Handle handle) {
        handle.paidUntil = Long.MIN_VALUE;
        if (ACTIVE.remove(handle) != null) {
            handle.chunks.clear();
            handle.dimension = null;
            rebuild();
        }
    }

    /** 这台穹顶现在登记着、付过这一秒的钱的区块有几块（停付费、没登记都是 0；GameTest、界面排查用） */
    public static int publishedChunks(Handle handle) {
        return ACTIVE.containsKey(handle) && handle.paidUntil != Long.MIN_VALUE ? handle.chunks.size() : 0;
    }

    /** 到现在为止重建过几次快照（主线程读；GameTest 在同一段同步代码里前后各读一次相减） */
    public static int rebuildsForTest() {
        return rebuilds;
    }

    /**
     * 这个位置现在被不被穹顶罩住：所在区块登记过、那条的领地结论和穹顶的付费都没过期、离穹顶中心不超过半径。任何线程都能调。
     *
     * @param gameTime 世界的游戏时间（各维度共用一个）
     */
    public static boolean covers(ResourceKey<Level> dimension, long gameTime, double x, double z) {
        Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Cover[]>> current = snapshot;
        if (current.isEmpty()) {
            return false;
        }
        Long2ObjectOpenHashMap<Cover[]> chunks = current.get(dimension);
        if (chunks == null) {
            return false;
        }
        Cover[] covers = chunks.get(ChunkPos.asLong(Mth.floor(x) >> 4, Mth.floor(z) >> 4));
        if (covers == null) {
            return false;
        }
        for (Cover cover : covers) {
            if (gameTime >= cover.claimUntil() || gameTime >= cover.handle().paidUntil) {
                continue;
            }
            double dx = x - cover.centerX();
            double dz = z - cover.centerZ();
            if (dx * dx + dz * dz <= cover.radiusSq()) {
                return true;
            }
        }
        return false;
    }

    /** 主线程：按 {@link #ACTIVE} 整份重建快照 */
    private static void rebuild() {
        rebuilds++;
        Map<ResourceKey<Level>, Map<Long, List<Cover>>> building = new HashMap<>();
        for (Handle handle : ACTIVE.keySet()) {
            if (handle.dimension == null) {
                continue;
            }
            Map<Long, List<Cover>> perChunk = building.computeIfAbsent(handle.dimension, key -> new HashMap<>());
            for (Long2LongMap.Entry entry : handle.chunks.long2LongEntrySet()) {
                perChunk.computeIfAbsent(entry.getLongKey(), key -> new ArrayList<>(1))
                        .add(new Cover(handle, handle.centerX, handle.centerZ, handle.radiusSq, entry.getLongValue()));
            }
        }
        Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Cover[]>> next = new HashMap<>();
        for (Map.Entry<ResourceKey<Level>, Map<Long, List<Cover>>> world : building.entrySet()) {
            Long2ObjectOpenHashMap<Cover[]> chunks = new Long2ObjectOpenHashMap<>(world.getValue().size());
            for (Map.Entry<Long, List<Cover>> chunk : world.getValue().entrySet()) {
                chunks.put(chunk.getKey().longValue(), chunk.getValue().toArray(new Cover[0]));
            }
            next.put(world.getKey(), chunks);
        }
        snapshot = next.isEmpty() ? Map.of() : next;
    }

    /** 关服：全部撤掉（单人换存档不带过去） */
    private static void clear() {
        for (Handle handle : ACTIVE.keySet()) {
            handle.paidUntil = Long.MIN_VALUE;
            handle.chunks.clear();
            handle.dimension = null;
        }
        ACTIVE.clear();
        snapshot = Map.of();
    }

    /** 这种生物、这种刷怪类型归不归穹顶管（类别、名单、豁免标签）；不看位置。任何线程都能调 */
    public static boolean handles(EntityType<?> type, MobSpawnType spawnType) {
        return type.getCategory() == MobCategory.MONSTER
                && ForceDomeBlockEntity.Config.spawnTypes().contains(spawnType)
                && !type.is(Tags.EntityTypes.BOSSES)
                && !type.is(ECForceDome.DOME_EXEMPT);
    }

    /** 刷怪拦截与关服清理（监听类名带模块前缀，免得和同包的事件类撞名） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class ForceDomeEvents {

        private ForceDomeEvents() {
        }

        /** 建实体之前的放置检查：自然、生成区块、增援（巡逻队和幻翼不经过这里，见下一个） */
        @SubscribeEvent(priority = EventPriority.LOW)
        public static void forceDomeSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
            if (event.getResult() == Event.Result.DENY || snapshot.isEmpty() || !handles(event.getEntityType(), event.getSpawnType())) {
                return;
            }
            ServerLevel level = event.getLevel().getLevel();
            if (covers(level.dimension(), level.getGameTime(), event.getPos().getX() + 0.5D, event.getPos().getZ() + 0.5D)) {
                event.setResult(Event.Result.DENY);
            }
        }

        /**
         * finalizeSpawn 之前：巡逻队（PATROL）、幻翼（NATURAL）以及别的模组自己调 finalizeSpawn 的自然刷怪。
         * 收已取消的事件：FinalizeSpawn 被取消只表示跳过 finalizeSpawn，实体照样生成；判的是 isSpawnCancelled。
         * 已经进了世界的实体不能再标（Forge 会抛异常），跳过。
         */
        @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
        public static void forceDomeFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
            if (event.isSpawnCancelled() || snapshot.isEmpty()) {
                return;
            }
            Mob mob = event.getEntity();
            if (mob.isAddedToWorld() || !handles(mob.getType(), event.getSpawnType())) {
                return;
            }
            ServerLevel level = event.getLevel().getLevel();
            if (covers(level.dimension(), level.getGameTime(), event.getX(), event.getZ())) {
                event.setSpawnCancelled(true);
            }
        }

        @SubscribeEvent
        public static void forceDomeServerStopped(ServerStoppedEvent event) {
            clear();
            ForceDomeBlockEntity.clearLoaded();
        }
    }
}
