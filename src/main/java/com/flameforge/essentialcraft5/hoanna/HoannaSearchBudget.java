package com.flameforge.essentialcraft5.hoanna;

import net.minecraft.server.MinecraftServer;

import java.lang.ref.WeakReference;

/**
 * 全服共用的「找地方」预算（第十三批 13d-HF，batch13.md §6.3）：采样群系源找坐标的几处——故障核心解码（{@code GlitchyCoreDecoder}）、
 * 海纳尔勘测图、里程碑、以后的解码器改指（{@code worldgen/hoanna/StructureForecast}）——<b>全服每刻合计</b>最多看
 * {@value #POINTS_PER_TICK} 个点、最多花 {@value #NANOS_PER_TICK} 纳秒（一个群系采样约 7~8 µs，256 个约 2 ms）。
 * 几处同一刻都在找时排着队用，不会叠加。原来是解码器自己的一份（{@code takeBudget}），13d 抽到这里，解码器的行为不变。
 * 13e 起结构预测找封印宝库、审判庭前哨时，群系和排除都过了的候选还要算一列噪声（中心是不是液体，约 3 ms，见 {@code StructureForecast} 第 4 步）：
 * 每算一列就看一次表，所以那一刻最多超出时间预算一列的量（只有比已找到的更近的候选才算，勘测图找宝库、解码器找前哨每次通常几列）。
 * <p>
 * 用法（只在服务端主线程）：
 * <pre>
 * HoannaSearchBudget.Grant grant = HoannaSearchBudget.take(server);
 * if (grant.empty()) return;                      // 这一刻用完了，下一刻再来
 * int before = search.samples(); long started = System.nanoTime();
 * search.advance(grant.points(), grant.nanos());
 * HoannaSearchBudget.charge(search.samples() - before, System.nanoTime() - started);   // 按实际看的点数和花的时间记账
 * </pre>
 * 服务器或刻数变了就重新给满。测试用 {@link #resetForTest} 把这一刻的预算摆成指定值。
 */
public final class HoannaSearchBudget {

    /** 全服每刻合计最多看几个点 */
    public static final int POINTS_PER_TICK = 256;
    /** 全服每刻合计最多花多少纳秒（慢一些的机器上点数没用完也停） */
    public static final long NANOS_PER_TICK = 2_000_000L;

    private static WeakReference<MinecraftServer> server = new WeakReference<>(null);
    private static int tick = -1;
    private static int points;
    private static long nanos;

    /**
     * 这一刻剩下的预算：点数（时间用完了算 0）和纳秒。
     *
     * @param points 还能看几个点（≥ 0）
     * @param nanos  还能花多少纳秒（可能 ≤ 0）
     */
    public record Grant(int points, long nanos) {

        /** 这一刻没得用了 */
        public boolean empty() {
            return points <= 0;
        }
    }

    private HoannaSearchBudget() {
    }

    /** 这一刻还剩多少（全服合计）：服务器或刻数变了就重新给满；时间用完了点数记 0。不扣，用完按实际调 {@link #charge} */
    public static Grant take(MinecraftServer current) {
        int now = current.getTickCount();
        if (server.get() != current || tick != now) {
            server = new WeakReference<>(current);
            tick = now;
            points = POINTS_PER_TICK;
            nanos = NANOS_PER_TICK;
        }
        return new Grant(nanos > 0L ? Math.max(0, points) : 0, nanos);
    }

    /** 记账：这一刻实际看了 used 个点、花了 spent 纳秒（先 {@link #take} 过同一刻才有意义） */
    public static void charge(int used, long spent) {
        points -= Math.max(0, used);
        nanos -= Math.max(0L, spent);
    }

    /** GameTest 用：把这一刻的全服预算摆成 points 个点、nanos 纳秒（测合计和时间保险）；下一刻照常重新给满 */
    public static void resetForTest(MinecraftServer current, int setPoints, long setNanos) {
        server = new WeakReference<>(current);
        tick = current.getTickCount();
        points = setPoints;
        nanos = setNanos;
    }
}
