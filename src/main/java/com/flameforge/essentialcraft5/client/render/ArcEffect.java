package com.flameforge.essentialcraft5.client.render;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 一道电弧的折线（第四批框架 F4-14），移植 DummyCore {@code Utils/Lightning}（149 行）。特斯拉线圈、线圈基座、反应堆共用，以后全息也用得上。
 * 画在客户端：{@link ArcRenderer} 把它画成两层发光的细条；世界坐标的电弧交给 {@link ArcEffects}（没有方块实体的基座、打向远处目标的线圈）。
 * <p>
 * <b>这个类只做几何和寿命，不引用任何客户端类、不加 {@code @OnlyIn}</b>：GameTest 在专用服上直接测它（段数、分叉数、端点、寿命）。
 * 坐标系由调用方决定：给方块实体渲染器用就传相对方块角的坐标（原作都是这样），给 {@link ArcEffects} 用就传世界坐标。
 *
 * <h2>形状（和原作的对应）</h2>
 * <ul>
 *   <li>主干 {@link #TRUNK_SEGMENTS} 段，从起点到终点（原作 {@code generateLightningBetween2Points(start, end, 0, 8, curve)}）。
 *       原作每一段都在「均分的一步」上再加随机偏移、偏移会一路累积，终点最多偏出 8 × curve 格（线圈打怪时打不到怪身上）；
 *       这里改成均分点各自抖动、<b>两端钉在起点和终点上</b>。</li>
 *   <li>支流 {@link #FORK_SEGMENTS} 段，从主干第 {@link #FORK_VERTEX} 个点再牵一股到终点，抖动 ×3（原作目标是
 *       {@code (end.x × curve×10, end.y, end.z × curve×10)}：curve = 0.1 的线圈正好就是终点，别的 curve 下是相对方块角的怪值，统一成终点）。</li>
 *   <li>分叉 {@link #BRANCHES} 条，第 i 条从「主干各段起点 + 支流各段起点」的第 i 个点出发，1~2 段，朝随机方向伸出至多 1/4 全长，抖动 ÷2（同原作）。
 *       原作还有一层「分叉上的小分叉」，但步数传的是 0、一段都不生成，这里不移植。</li>
 * </ul>
 * 原作按 {@code renderTicksExisted} 只画前 N 段、到 20 才移除，但 DummyCoreUnofficial 2.4.112.5 和 EC4U 的源码里都没有地方增加这个值
 * （按源码看电弧画不出来、也永不过期；没在 1.12 里实测）；这里整道电弧一出现就全画，寿命按 tick 走（{@link #tick}），最后四分之一的时间淡出（{@link #alpha}）。
 * <p>
 * 同一套形状还有个平面版 {@link #planar}：研究手册界面上的 2D 电弧用，写进调用方复用的 float 数组、不分配
 * （短火花可以少长几条分叉，见带 {@code branches} 参数的那个）。
 */
public final class ArcEffect {

    /** 主干段数 */
    public static final int TRUNK_SEGMENTS = 8;
    /** 支流段数 */
    public static final int FORK_SEGMENTS = 4;
    /** 支流从主干的第几个点（0 = 起点）分出去 */
    public static final int FORK_VERTEX = 3;
    /** 分叉条数（每条 1~2 段） */
    public static final int BRANCHES = 12;
    /** 默认寿命（tick），原作线圈的两种电弧都是 20 */
    public static final int DEFAULT_LIFETIME = 20;
    /** 一道电弧最多几段（主干 + 支流 + 每条分叉至多 2 段）：{@link #planar} 的调用方按它开数组 */
    public static final int MAX_SEGMENTS = TRUNK_SEGMENTS + FORK_SEGMENTS + BRANCHES * 2;

    /** 一段折线属于哪一部分 */
    public enum Kind {
        TRUNK, FORK, BRANCH
    }

    /** 一段折线（两个端点，坐标系同 {@link ArcEffect}） */
    public record Segment(Vec3 from, Vec3 to, Kind kind) {
    }

    /**
     * 电弧的样式：弯曲程度（原作的 curve / factor）、颜色（0~1）、寿命（tick）、粗细倍数（1 = {@link ArcRenderer} 的默认粗细）。
     * 预设的数值照原作各处 {@code new Lightning(...)} 的参数。
     */
    public record Style(float curve, float red, float green, float blue, int lifetime, float width) {

        /** 线圈待机时身上冒的电弧（{@code TileMRUCoil} L149：curve 0.1，颜色 1 / 0.2 / 1，20 tick） */
        public static final Style COIL_IDLE = new Style(0.1F, 1F, 0.2F, 1F, 20, 1F);
        /** 线圈打向目标的电弧（{@code TileMRUCoil} L221：curve 0.1，颜色 1 / 0 / 0.7，20 tick） */
        public static final Style COIL_STRIKE = new Style(0.1F, 1F, 0F, 0.7F, 20, 1F);
        /** 线圈基座侧面的小火花（{@code TileMRUCoilHardener} L35：curve 0.03，颜色 1 / 0.1 / 0.8，40 tick，原作线宽同上，这里细一点） */
        public static final Style COIL_HARDENER = new Style(0.03F, 1F, 0.1F, 0.8F, 40, 0.7F);
        /** 反应堆上方的电弧（{@code TileMRUReactor} L178：curve 0.2，颜色 1 / 0.2 / 1，21 tick） */
        public static final Style REACTOR = new Style(0.2F, 1F, 0.2F, 1F, 21, 1F);

        public Style {
            if (lifetime <= 0) {
                throw new IllegalArgumentException("电弧寿命要大于 0：" + lifetime);
            }
        }

        /** 换一个寿命 */
        public Style withLifetime(int ticks) {
            return new Style(curve, red, green, blue, ticks, width);
        }

        /** 换一个颜色 */
        public Style withColor(float r, float g, float b) {
            return new Style(curve, r, g, b, lifetime, width);
        }
    }

    private final Vec3 start;
    private final Vec3 end;
    private final Style style;
    private final List<Segment> segments;
    private final AABB bounds;
    private int age;

    public ArcEffect(RandomSource random, Vec3 start, Vec3 end, Style style) {
        this.start = start;
        this.end = end;
        this.style = style;
        this.segments = Collections.unmodifiableList(generate(random, start, end, style.curve()));
        this.bounds = boundsOf(segments, start);
    }

    // ------------------------------------------------------------------
    // 访问
    // ------------------------------------------------------------------

    public Vec3 start() {
        return start;
    }

    public Vec3 end() {
        return end;
    }

    public Style style() {
        return style;
    }

    /** 全部折线段：先主干（{@link #TRUNK_SEGMENTS} 段），再支流（{@link #FORK_SEGMENTS} 段），再各条分叉 */
    public List<Segment> segments() {
        return segments;
    }

    /** 某一部分有几段 */
    public int count(Kind kind) {
        int n = 0;
        for (Segment segment : segments) {
            if (segment.kind() == kind) {
                n++;
            }
        }
        return n;
    }

    /** 包住所有折线段的盒子（视锥剔除、渲染范围用） */
    public AABB bounds() {
        return bounds;
    }

    public int age() {
        return age;
    }

    public int lifetime() {
        return style.lifetime();
    }

    public boolean isExpired() {
        return age >= style.lifetime();
    }

    /** 过一个 tick；返回这次之后是否到期（到期的由持有者移除） */
    public boolean tick() {
        if (age < style.lifetime()) {
            age++;
        }
        return isExpired();
    }

    /** 当前不透明度倍数：前四分之三的寿命是 1，之后线性淡到 0 */
    public float alpha(float partialTick) {
        float lifetime = style.lifetime();
        float t = Math.min(lifetime, age + partialTick);
        float fadeStart = lifetime * 0.75F;
        if (t <= fadeStart) {
            return 1F;
        }
        return Math.max(0F, (lifetime - t) / (lifetime - fadeStart));
    }

    /** 把一组电弧各过一个 tick，到期的删掉（持有者在自己的客户端 tick 里调） */
    public static void tickAll(Collection<ArcEffect> arcs) {
        arcs.removeIf(ArcEffect::tick);
    }

    // ------------------------------------------------------------------
    // 生成
    // ------------------------------------------------------------------

    private static List<Segment> generate(RandomSource random, Vec3 start, Vec3 end, float curve) {
        List<Segment> result = new ArrayList<>(TRUNK_SEGMENTS + FORK_SEGMENTS + BRANCHES * 2);
        List<Vec3> branchRoots = new ArrayList<>(TRUNK_SEGMENTS + FORK_SEGMENTS);

        List<Vec3> trunk = polyline(random, start, end, TRUNK_SEGMENTS, curve);
        addSegments(result, branchRoots, trunk, Kind.TRUNK);

        List<Vec3> fork = polyline(random, trunk.get(FORK_VERTEX), end, FORK_SEGMENTS, curve * 3F);
        addSegments(result, branchRoots, fork, Kind.FORK);

        double reach = start.distanceTo(end) / 4D;
        for (int i = 0; i < BRANCHES; i++) {
            Vec3 root = branchRoots.get(i);
            int steps = 1 + random.nextInt(2);
            Vec3 tip = root.add(spread(random) * reach, spread(random) * reach, spread(random) * reach);
            addSegments(result, null, polyline(random, root, tip, steps, curve / 2F), Kind.BRANCH);
        }
        return result;
    }

    /** from → to 均分成 steps 段，中间各点在三个轴上各抖动 ±curve（原作 MathUtils.randomFloat：两个 [0,1) 相减），两端不动 */
    private static List<Vec3> polyline(RandomSource random, Vec3 from, Vec3 to, int steps, float curve) {
        List<Vec3> points = new ArrayList<>(steps + 1);
        points.add(from);
        for (int k = 1; k < steps; k++) {
            Vec3 base = from.lerp(to, (double) k / steps);
            points.add(base.add(spread(random) * curve, spread(random) * curve, spread(random) * curve));
        }
        points.add(to);
        return points;
    }

    private static void addSegments(List<Segment> out, List<Vec3> roots, List<Vec3> points, Kind kind) {
        for (int i = 0; i + 1 < points.size(); i++) {
            out.add(new Segment(points.get(i), points.get(i + 1), kind));
            if (roots != null) {
                roots.add(points.get(i));
            }
        }
    }

    private static double spread(RandomSource random) {
        return random.nextFloat() - random.nextFloat();
    }

    // ------------------------------------------------------------------
    // 平面版（研究手册的 2D 电弧）
    // ------------------------------------------------------------------

    /**
     * 同一套形状的平面版：研究手册在界面上画 2D 电弧用（{@code client/guide/GuideArcs}），<b>不分配</b>，结果写进调用方复用的数组。
     * <p>
     * 步骤和 {@link #generate} 一一对应（两处要一起改）：主干 {@link #TRUNK_SEGMENTS} 段均分点各抖 ±curve、两端钉住；
     * 支流 {@link #FORK_SEGMENTS} 段从主干第 {@link #FORK_VERTEX} 点到终点、抖动 ×3；{@link #BRANCHES} 条分叉依次从「主干各段起点 + 支流各段起点」出发，
     * 每条 1~2 段、朝随机方向伸出至多 {@code reach}、抖动 ÷2；随机数的取用顺序也相同，只是每个点少取一个 z。
     * 和 3D 版唯一的区别：抖动和分叉长度直接给界面像素（原作按格子算、分叉长 = 全长 / 4），调用方按电弧长短自己定。
     *
     * @param curve 主干抖动幅度（坐标单位，原作的 curve）
     * @param reach 分叉每个轴上最多伸出多远（原作是全长的 1/4）
     * @param out   段端点，每段 4 个 float（x0, y0, x1, y1），从第 {@code first} 段写起，至少要留 {@link #MAX_SEGMENTS} 段
     * @param kinds 每段属于哪一部分（{@link Kind#ordinal()}），下标同段号
     * @param first 从第几段开始写（多道电弧共用一个大数组时的偏移，单位是段）
     * @return 写了几段（{@code TRUNK_SEGMENTS + FORK_SEGMENTS + BRANCHES} ~ {@link #MAX_SEGMENTS}）
     */
    public static int planar(RandomSource random, float x0, float y0, float x1, float y1, float curve, float reach,
                             float[] out, byte[] kinds, int first) {
        return planar(random, x0, y0, x1, y1, curve, reach, BRANCHES, out, kinds, first);
    }

    /**
     * 同 {@link #planar(RandomSource, float, float, float, float, float, float, float[], byte[], int)}，但只长 {@code branches} 条分叉
     * （0~{@link #BRANCHES}，超出按边界算）：研究手册里十几像素长的短火花塞满 12 条分叉会成一团毛刺，读不出电弧的样子。
     * 分叉的根在 12 个候选根（主干 8 段、支流 4 段的起点）里等距取：第 i 条取第 {@code i × 12 / branches} 个。
     * {@code branches = 12} 时和上面那个完全一样（根、随机数取用顺序都相同）。
     *
     * @return 写了几段（{@code TRUNK_SEGMENTS + FORK_SEGMENTS + branches} ~ {@code TRUNK_SEGMENTS + FORK_SEGMENTS + branches × 2}）
     */
    public static int planar(RandomSource random, float x0, float y0, float x1, float y1, float curve, float reach, int branches,
                             float[] out, byte[] kinds, int first) {
        int nb = Math.max(0, Math.min(BRANCHES, branches));
        int n = first;
        n = planarPolyline(random, x0, y0, x1, y1, TRUNK_SEGMENTS, curve, Kind.TRUNK, out, kinds, n);
        float fx = out[(first + FORK_VERTEX) * 4];
        float fy = out[(first + FORK_VERTEX) * 4 + 1];
        n = planarPolyline(random, fx, fy, x1, y1, FORK_SEGMENTS, curve * 3F, Kind.FORK, out, kinds, n);
        // 分叉的根：主干 8 段、支流 4 段的起点，按生成顺序正好是前 12 段的起点
        for (int i = 0; i < nb; i++) {
            int root = i * BRANCHES / nb;
            float rx = out[(first + root) * 4];
            float ry = out[(first + root) * 4 + 1];
            int steps = 1 + random.nextInt(2);
            float tx = rx + (random.nextFloat() - random.nextFloat()) * reach;
            float ty = ry + (random.nextFloat() - random.nextFloat()) * reach;
            n = planarPolyline(random, rx, ry, tx, ty, steps, curve / 2F, Kind.BRANCH, out, kinds, n);
        }
        return n - first;
    }

    /** 平面版的 {@link #polyline}：from → to 均分 steps 段、中间点抖动，直接写成段 */
    private static int planarPolyline(RandomSource random, float ax, float ay, float bx, float by, int steps, float curve,
                                      Kind kind, float[] out, byte[] kinds, int n) {
        float px = ax;
        float py = ay;
        for (int k = 1; k <= steps; k++) {
            float qx;
            float qy;
            if (k == steps) {
                qx = bx;
                qy = by;
            } else {
                float t = (float) k / steps;
                qx = ax + (bx - ax) * t + (random.nextFloat() - random.nextFloat()) * curve;
                qy = ay + (by - ay) * t + (random.nextFloat() - random.nextFloat()) * curve;
            }
            int o = n * 4;
            out[o] = px;
            out[o + 1] = py;
            out[o + 2] = qx;
            out[o + 3] = qy;
            kinds[n] = (byte) kind.ordinal();
            n++;
            px = qx;
            py = qy;
        }
        return n;
    }

    private static AABB boundsOf(List<Segment> segments, Vec3 fallback) {
        double minX = fallback.x, minY = fallback.y, minZ = fallback.z;
        double maxX = minX, maxY = minY, maxZ = minZ;
        for (Segment segment : segments) {
            for (Vec3 p : new Vec3[]{segment.from(), segment.to()}) {
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                minZ = Math.min(minZ, p.z);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
                maxZ = Math.max(maxZ, p.z);
            }
        }
        // 留出条带本身的粗细
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.1D);
    }
}
