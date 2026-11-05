package com.flameforge.essentialcraft5.hoanna.survey;

import com.flameforge.essentialcraft5.hoanna.HoannaSearchBudget;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 勘测图和里程碑的「找最近一处」（第十三批 13d-H3，batch13.md §6.3）：每名玩家同时最多一个搜索，跨刻推进，吃全服共用的
 * {@link HoannaSearchBudget}（和故障核心解码一起，全服每刻合计 ≤ 256 个群系采样点、≤ 2 ms）。搜索本身是
 * {@link StructureForecast.Search}，只读结构集的格子哈希和群系源，不加载、不生成任何区块。
 * <p>
 * 服务端每刻末（{@code ECHoannaSurvey.HoannaSurveyEvents}）调 {@link #tick}；玩家下线、换了世界的搜索直接作废，不回调。
 * 找完以后在服务端主线程回调 {@link Finish}，由发起的一方（勘测图写进物品、里程碑写动作栏）收尾。
 * <p>
 * 采样上限：{@link #start} 的 {@code sampleCap} 大于 0 时，最多看这么多个点（里程碑 16 个）。做法是把跳过表包一层：
 * {@code Search} 只在真要采样前问跳过表，所以问到第 cap 次以后一律答「跳过」，后面的格子只走哈希、不再采样，
 * 已经找到的最近一处照样作数。
 * <p>
 * 测试：{@link #setTestWorld} 把「海纳尔长什么样」换成固定种子的离线世界（GameTest 的测试世界是超平坦，没有城镇结构集），
 * {@link #finishAllForTest} 不限预算地一口气做完全部搜索。
 */
public final class SurveySearches {

    /** 搜索做完的回调（服务端主线程） */
    @FunctionalInterface
    public interface Finish {
        /**
         * @param player 发起的玩家（还在线、还在发起时那个世界）
         * @param result 最近一处的起点区块中心（y 海平面）；没找到为空
         */
        void done(ServerPlayer player, Optional<BlockPos> result);
    }

    /** 一个进行中的搜索 */
    public static final class Job {

        private final UUID player;
        private final ResourceKey<Level> dimension;
        private final StructureForecast.Search search;
        private final Finish finish;
        private boolean done;
        @Nullable
        private BlockPos result;

        private Job(UUID player, ResourceKey<Level> dimension, StructureForecast.Search search, Finish finish) {
            this.player = player;
            this.dimension = dimension;
            this.search = search;
            this.finish = finish;
        }

        /** 找完了没有 */
        public boolean done() {
            return done;
        }

        /** 找完以后的结果（没找完、没找到为空） */
        public Optional<BlockPos> result() {
            return Optional.ofNullable(result);
        }

        /** 到现在一共采了几个群系点 */
        public int samples() {
            return search.samples();
        }

        public UUID player() {
            return player;
        }
    }

    /** 跳过表外面包的那一层：前 cap 次照原表答，之后一律「跳过」（cap ≤ 0 不限） */
    private static final class CappedSkip implements Predicate<ChunkPos> {

        private final Predicate<ChunkPos> base;
        private final int cap;
        private int asked;

        CappedSkip(Predicate<ChunkPos> base, int cap) {
            this.base = base;
            this.cap = cap;
        }

        @Override
        public boolean test(ChunkPos chunk) {
            if (base.test(chunk)) {
                return true;
            }
            if (cap > 0 && asked >= cap) {
                return true;
            }
            asked++;
            return false;
        }
    }

    private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();
    @Nullable
    private static volatile StructureForecast.World testWorld;

    private SurveySearches() {
    }

    /** 预测用的世界：测试覆盖优先，否则就是这个世界自己的生成器（不碰区块） */
    public static StructureForecast.World world(ServerLevel level) {
        StructureForecast.World forced = testWorld;
        return forced != null ? forced : StructureForecast.World.of(level);
    }

    /** 这名玩家是不是有搜索在跑 */
    public static boolean busy(UUID player) {
        return JOBS.containsKey(player);
    }

    /** 作废这名玩家的搜索（不回调） */
    public static void cancel(UUID player) {
        JOBS.remove(player);
    }

    /**
     * 发起一个搜索。这名玩家已有搜索在跑时返回 null（每人同时最多一个）。
     *
     * @param level     玩家所在的世界（换了世界就作废）
     * @param world     预测用的世界（{@link #world}）
     * @param targets   要找的结构集
     * @param center    从哪里找起
     * @param radius    水平半径（格）
     * @param skip      跳过这些起点区块（勘测图的 Skip 表）
     * @param sampleCap 最多采几个点（≤ 0 不限）
     */
    @Nullable
    public static Job start(ServerPlayer player, ServerLevel level, StructureForecast.World world, List<StructureForecast.Target> targets,
                            BlockPos center, int radius, Predicate<ChunkPos> skip, int sampleCap, Finish finish) {
        UUID id = player.getUUID();
        if (JOBS.containsKey(id)) {
            return null;
        }
        StructureForecast.Search search = new StructureForecast.Search(world, targets, center, radius, new CappedSkip(skip, sampleCap));
        Job job = new Job(id, level.dimension(), search, finish);
        JOBS.put(id, job);
        return job;
    }

    /** 服务端每刻末调：按发起的先后，用全服剩下的预算推进；找完的回调并移走 */
    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        List<Job> finished = new ArrayList<>();
        Iterator<Job> it = JOBS.values().iterator();
        while (it.hasNext()) {
            Job job = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(job.player);
            if (player == null || !player.level().dimension().equals(job.dimension)) {
                it.remove();
                continue;
            }
            if (job.search.step(server)) {
                it.remove();
                finished.add(job);
            } else if (HoannaSearchBudget.take(server).empty()) {
                break;
            }
        }
        for (Job job : finished) {
            complete(server, job);
        }
    }

    private static void complete(@Nullable MinecraftServer server, Job job) {
        job.done = true;
        job.result = job.search.result().orElse(null);
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(job.player);
        if (player != null) {
            job.finish.done(player, job.search.result());
        }
    }

    /** 服务器停了：丢掉全部搜索（它们拿着世界的生成器） */
    public static void clear() {
        JOBS.clear();
    }

    // ------------------------------------------------------------------ 测试

    /** GameTest 用：预测用的世界换成 world（null 还原）。同一段同步代码里设、测、还原 */
    public static void setTestWorld(@Nullable StructureForecast.World world) {
        testWorld = world;
    }

    /**
     * GameTest 用：不限预算地做完全部搜索并回调。测试里的玩家不在服务器的玩家列表里，所以按传入的玩家回调
     * （找不到对应的就只记结果、不回调）。
     */
    public static void finishAllForTest(ServerPlayer... players) {
        List<Job> jobs = new ArrayList<>(JOBS.values());
        JOBS.clear();
        for (Job job : jobs) {
            job.search.finish();
            job.done = true;
            job.result = job.search.result().orElse(null);
            for (ServerPlayer player : players) {
                if (player.getUUID().equals(job.player)) {
                    job.finish.done(player, job.search.result());
                }
            }
        }
    }

    /** GameTest 用：只推进、不回调不移走（测全服预算的合计）；返回这一轮里还没找完的有几个 */
    public static int stepAllForTest(MinecraftServer server) {
        int pending = 0;
        for (Job job : JOBS.values()) {
            if (!job.search.step(server)) {
                pending++;
            }
        }
        return pending;
    }
}
