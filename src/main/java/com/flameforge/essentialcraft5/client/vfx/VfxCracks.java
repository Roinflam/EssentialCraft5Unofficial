package com.flameforge.essentialcraft5.client.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 一张贴地的裂纹网：首领预警时在范围里裂开、慢慢烧热，落下时整张交给结算炸亮（预警和结算是同一批缝，不换形状）。
 * 形状按种子建一次就不再变；点按圆心的相对位置存，跟随首领时整张平移。每道裂纹的点从「起始端」排起：
 * 往外劈的（{@link #radial}、{@link #ring}）从圆心那头，往里伸的（{@link #inward}）从场边那头，线上的（{@link #line}）从线的正中。
 * <p>
 * 贴地：第一次画之前每个点往下找一次地面顶面（{@link #groundAt}），相邻两点高度不一样的那节对半细分几次，台阶处只剩很短一截斜着跨过去；
 * 找不到地面的点（悬崖外、坑里、墙里）那几节不画。跟随的网挪了半格以上再重找一次高度。整张网大半都找不到地面时（圆心本身悬空）
 * 退回按圆心高度平铺，总比什么都看不到好。
 * <p>
 * 每道缝画三层，都是平躺在地上的条带（平视时不会立起来）：焦黑的底（普通半透明，白天在亮地面上靠它读出「地裂了」）、
 * 一层窄辉光（中档以上）、热芯。热芯颜色随热度从辉光色升到等离子层再到芯色，热度 1 就是一道白缝（结算顿帧）。
 * 只在客户端用。
 */
public final class VfxCracks {

    /** 焦黑底的颜色 */
    private static final int CHAR = 0x160A18;
    /** 每道裂纹最多几个点（坡地上细分后） */
    private static final int MAX_POINTS = 96;
    /** 地面往上最多认几格（再高就是墙），往下最多找几格 */
    private static final double STEP_UP = 1.3D;
    private static final double DROP = 4D;

    private static final class Crack {
        /** 相对圆心的 x、z */
        double[] p;
        /** 每个点离起始端几格 */
        float[] d;
        /** 每个点的地面高度（相对圆心 y）；NaN = 找不到地面，挨着它的节不画 */
        float[] y;
        float length;
        float width;
        int parent = -1;
        float fork;
    }

    private final Crack[] cracks;
    private Vec3 center;
    private boolean sampled;
    private double sampledX;
    private double sampledZ;
    private double sampledY;

    private VfxCracks(Vec3 center, List<Crack> list) {
        this.center = center;
        this.cracks = list.toArray(new Crack[0]);
    }

    // ------------------------------------------------------------------ 建网

    /**
     * 从中心往外劈：count 道主裂纹，起点在 rIn，尖落在 rTip ~ rTip + tipSpread；每道主裂纹带 branches 道侧枝（1 或 2），侧枝的尖也落在这个范围里
     * （标出来的范围只往外偏、不往里缩，站在边上的人宁可多跑半步）。
     * span 小于一整圈时是扇形：a0 起逆时针 span 弧度，头尾两道主裂纹贴着扇形的两条边走（往外让一点点），侧枝只往扇形里岔，扇形边不画线。
     */
    public static VfxCracks radial(long seed, Vec3 c, float rIn, float rTip, float tipSpread, int count, float a0, float span, int depth,
                                   int branches) {
        RandomSource random = RandomSource.create(seed);
        boolean full = span >= Mth.TWO_PI - 1.0E-3F;
        count = Math.max(full ? 3 : 2, count);
        List<Crack> list = new ArrayList<>(count * (1 + branches));
        double[] buf = new double[VfxPaths.jaggedPoints(Math.max(depth, 2)) * 3];
        double edgeOut = 0.2D / Math.max(1F, rTip);
        // 长裂纹别摆太开（第一层的横向位移 = 粗糙度 × 全长）：4 格以内照 0.12，再长按 0.6 次方收
        float rough = 0.12F * (float) Math.min(1D, Math.pow(4D / Math.max(0.5F, rTip), 0.6D));
        double[] angle = new double[count];
        for (int i = 0; i < count; i++) {
            double a;
            if (full) {
                a = a0 + (i + random.nextDouble() * 0.7D) * span / count;
            } else {
                double step = span / (count - 1D);
                a = a0 + i * step;
                if (i == 0) {
                    a -= edgeOut;
                } else if (i == count - 1) {
                    a += edgeOut;
                } else {
                    a += (random.nextDouble() - 0.5D) * 0.6D * step;
                }
            }
            angle[i] = a;
            boolean edge = !full && (i == 0 || i == count - 1);
            double tip = rTip + tipSpread * random.nextDouble();
            list.add(jagged(random, buf, polar(a, rIn), polar(a, tip), depth, edge ? 0.03F : rough, 0.07F + 0.03F * random.nextFloat()));
        }
        for (int b = 0; b < branches; b++) {
            for (int i = 0; i < count; i++) {
                Crack main = list.get(i);
                // 侧枝：第一道从主裂纹 35%~60% 处岔出，第二道 60%~80%，往另一边岔；尖也在范围边上
                double f = b == 0 ? 0.35D + 0.25D * random.nextDouble() : 0.6D + 0.2D * random.nextDouble();
                double sign;
                if (!full && i == 0) {
                    sign = 1D;
                } else if (!full && i == count - 1) {
                    sign = -1D;
                } else {
                    sign = (random.nextBoolean() ? 1D : -1D) * (b == 0 ? 1D : -1D);
                }
                double side = sign * (0.22D + 0.18D * random.nextDouble()) * (b == 0 ? 1D : 0.6D);
                double a = angle[i] + side;
                if (!full) {
                    a = Mth.clamp(a, a0, a0 + span);
                }
                double tip = rTip + tipSpread * random.nextDouble();
                Vec3 from = relAt(main, (float) f * main.length);
                Crack br = jagged(random, buf, from, polar(a, tip), Math.max(2, depth - 1), 0.15F, 0.045F + 0.02F * random.nextFloat());
                br.parent = i;
                br.fork = (float) f * main.length;
                list.add(br);
            }
        }
        return new VfxCracks(c, list);
    }

    /** 整圆、带一道侧枝的 {@link #radial} */
    public static VfxCracks radial(long seed, Vec3 c, float rIn, float rTip, float tipSpread, int count, int depth) {
        return radial(seed, c, rIn, rTip, tipSpread, count, 0F, Mth.TWO_PI, depth, 1);
    }

    /**
     * 从外往里伸：count 道主裂纹，外端在 rOuter × (0.9~1)，内端落在 rInner − innerSpread ~ rInner；外半段各分一叉（叉往里伸到半路）。
     */
    public static VfxCracks inward(long seed, Vec3 c, float rInner, float innerSpread, float rOuter, int count, int depth) {
        RandomSource random = RandomSource.create(seed);
        List<Crack> list = new ArrayList<>(count * 2);
        double[] buf = new double[VfxPaths.jaggedPoints(depth) * 3];
        double[] angle = new double[count];
        double[] inner = new double[count];
        double[] outer = new double[count];
        for (int i = 0; i < count; i++) {
            angle[i] = (i + random.nextDouble() * 0.8D) * Mth.TWO_PI / count;
            outer[i] = rOuter * (0.9D + 0.1D * random.nextDouble());
            inner[i] = rInner - innerSpread * random.nextDouble();
            list.add(jagged(random, buf, polar(angle[i], outer[i]), polar(angle[i], inner[i]), depth, 0.06F, 0.09F + 0.04F * random.nextFloat()));
        }
        for (int i = 0; i < count; i++) {
            Crack main = list.get(i);
            double f = 0.12D + 0.25D * random.nextDouble();
            Vec3 from = relAt(main, (float) f * main.length);
            double side = (random.nextBoolean() ? 1D : -1D) * (0.06D + 0.06D * random.nextDouble());
            double r2 = Mth.lerp(0.4D + 0.3D * random.nextDouble(), inner[i], outer[i]);
            Crack br = jagged(random, buf, from, polar(angle[i] + side, r2), Math.max(2, depth - 1), 0.1F, 0.04F + 0.02F * random.nextFloat());
            br.parent = i;
            br.fork = (float) f * main.length;
            list.add(br);
        }
        return new VfxCracks(c, list);
    }

    /**
     * 往外扩的环（震波）走过的地面：count 道从 rIn 一直劈到 rOut 的长裂纹，每道带两道侧枝（远处也够密）。
     * 环走到哪、哪段亮由画的人按半径给（{@link #distanceToRadius}）。
     */
    public static VfxCracks ring(long seed, Vec3 c, float rIn, float rOut, int count, int depth) {
        return radial(seed, c, rIn, rOut, 0F, count, 0F, Mth.TWO_PI, depth, 2);
    }

    /**
     * 贴地的直线（棱线）：圆心取 a、b 正中，一道主缝从正中往两头裂（两道，各自从正中排起），每隔约 spacing 格往左右交替岔出一道短侧缝，
     * 侧缝的尖落在离中线 half ~ half + spread（线有多宽看侧缝尖到哪）。主缝只在中线左右 0.35 格里摆，不会摆出打击带。
     */
    public static VfxCracks line(long seed, Vec3 a, Vec3 b, float half, float spread, float spacing) {
        RandomSource random = RandomSource.create(seed);
        Vec3 mid = a.add(b).scale(0.5D);
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        double ux = len < 1.0E-5D ? 1D : dx / len;
        double uz = len < 1.0E-5D ? 0D : dz / len;
        List<Crack> list = new ArrayList<>();
        double halfLen = len * 0.5D;
        list.add(walk(random, halfLen, ux, uz, 0.06F + 0.02F * random.nextFloat()));
        list.add(walk(random, halfLen, -ux, -uz, 0.06F + 0.02F * random.nextFloat()));
        double[] buf = new double[VfxPaths.jaggedPoints(2) * 3];
        int side = random.nextBoolean() ? 1 : -1;
        for (double s = spacing * (0.3D + 0.4D * random.nextDouble()); s < halfLen - 0.3D; s += spacing * (0.75D + 0.5D * random.nextDouble())) {
            for (int dirIndex = 0; dirIndex < 2; dirIndex++) {
                Crack spine = list.get(dirIndex);
                float at = (float) (s / halfLen) * spine.length;
                Vec3 from = relAt(spine, at);
                double sx = dirIndex == 0 ? ux : -ux;
                double sz = dirIndex == 0 ? uz : -uz;
                // 侧缝：往前斜一点，尖离中线 half ~ half + spread
                double off = half + spread * random.nextDouble();
                double drift = (random.nextDouble() - 0.3D) * Math.min(1D, spacing * 0.6D);
                double px = -sz * side;
                double pz = sx * side;
                Vec3 to = new Vec3((s + drift) * sx + px * off, 0D, (s + drift) * sz + pz * off);
                Crack br = jagged(random, buf, from, to, 2, 0.14F, 0.04F + 0.02F * random.nextFloat());
                br.parent = dirIndex;
                br.fork = at;
                list.add(br);
                side = random.nextFloat() < 0.75F ? -side : side;
            }
        }
        return new VfxCracks(mid, list);
    }

    /** 主缝：从原点沿 (ux, uz) 走 length 格，每 0.9~1.4 格一个点，横向随机游走、不超出 ±0.35 格 */
    private static Crack walk(RandomSource random, double length, double ux, double uz, float halfWidth) {
        int n = Math.min(MAX_POINTS, Math.max(2, (int) Math.ceil(length / 1.1D) + 1));
        double[] p = new double[n * 2];
        double lat = 0D;
        for (int j = 0; j < n; j++) {
            double s = length * j / (n - 1D);
            if (j > 0 && j < n - 1) {
                lat = Mth.clamp(lat + random.nextGaussian() * 0.16D, -0.35D, 0.35D);
            } else if (j == n - 1) {
                lat *= 0.5D;
            }
            p[j * 2] = ux * s - uz * lat;
            p[j * 2 + 1] = uz * s + ux * lat;
        }
        return finish(p, halfWidth);
    }

    private static Vec3 polar(double a, double r) {
        return new Vec3(Math.cos(a) * r, 0D, Math.sin(a) * r);
    }

    private static Crack jagged(RandomSource random, double[] buf, Vec3 from, Vec3 to, int depth, float rough, float halfWidth) {
        int n = VfxPaths.jagged(random, from, to, depth, rough, buf);
        double[] p = new double[n * 2];
        for (int j = 0; j < n; j++) {
            p[j * 2] = buf[j * 3];
            p[j * 2 + 1] = buf[j * 3 + 2];
        }
        return finish(p, halfWidth);
    }

    private static Crack finish(double[] p, float halfWidth) {
        Crack c = new Crack();
        c.p = p;
        c.width = halfWidth;
        measure(c);
        return c;
    }

    private static void measure(Crack c) {
        int n = c.p.length / 2;
        c.d = new float[n];
        float s = 0F;
        for (int j = 1; j < n; j++) {
            s += (float) Math.hypot(c.p[j * 2] - c.p[j * 2 - 2], c.p[j * 2 + 1] - c.p[j * 2 - 1]);
            c.d[j] = s;
        }
        c.length = s;
    }

    /** 某道裂纹离起始端 s 格处的点（相对圆心，y = 0） */
    private static Vec3 relAt(Crack c, float s) {
        int j = segment(c, s);
        float f = Mth.clamp((s - c.d[j - 1]) / Math.max(1.0E-5F, c.d[j] - c.d[j - 1]), 0F, 1F);
        return new Vec3(Mth.lerp(f, c.p[j * 2 - 2], c.p[j * 2]), 0D, Mth.lerp(f, c.p[j * 2 - 1], c.p[j * 2 + 1]));
    }

    private static int segment(Crack c, float s) {
        int n = c.d.length;
        int j = 1;
        while (j < n - 1 && c.d[j] < s) {
            j++;
        }
        return j;
    }

    // ------------------------------------------------------------------ 贴地

    /**
     * (x, z) 这一列的地面顶面：从 y0 + 1.25 往下找到 y0 − {@value #DROP}，第一块有碰撞箱的方块的顶面。
     * 顶面比 y0 高出 {@value #STEP_UP} 格以上（墙）或一直找不到（悬崖外、坑）就是 NaN。
     */
    public static float groundAt(BlockGetter level, double x, double z, double y0) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int top = Mth.floor(y0 + 1.25D);
        int bottom = Mth.floor(y0 - DROP);
        for (int y = top; y >= bottom; y--) {
            pos.set(bx, y, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double surface = y + shape.max(Direction.Axis.Y);
            return surface > y0 + STEP_UP ? Float.NaN : (float) surface;
        }
        return Float.NaN;
    }

    /** 找一遍地面高度（第一次顺带在台阶处细分）；没有世界时按圆心高度平铺 */
    private void sample() {
        BlockGetter level = Minecraft.getInstance().level;
        boolean refine = !sampled;
        sampled = true;
        sampledX = center.x;
        sampledZ = center.z;
        sampledY = center.y;
        if (level == null) {
            for (Crack c : cracks) {
                c.y = new float[c.d.length];
            }
            return;
        }
        int total = 0;
        int missing = 0;
        for (Crack c : cracks) {
            if (refine) {
                refine(level, c);
            } else {
                int n = c.d.length;
                for (int j = 0; j < n; j++) {
                    c.y[j] = lift(level, c.p[j * 2], c.p[j * 2 + 1]);
                }
            }
            for (float y : c.y) {
                total++;
                if (Float.isNaN(y)) {
                    missing++;
                }
            }
        }
        if (missing * 2 > total) {
            for (Crack c : cracks) {
                c.y = new float[c.d.length];
            }
        }
    }

    private float lift(BlockGetter level, double rx, double rz) {
        float g = groundAt(level, center.x + rx, center.z + rz, center.y);
        return Float.isNaN(g) ? Float.NaN : (float) (g - center.y);
    }

    /** 每个点找地面；相邻两点高度差 0.1 格以上的那节对半分（最多三次），台阶两边各自贴地 */
    private void refine(BlockGetter level, Crack c) {
        int n = c.p.length / 2;
        List<double[]> out = new ArrayList<>(n * 2);
        double x0 = c.p[0];
        double z0 = c.p[1];
        float y0 = lift(level, x0, z0);
        out.add(new double[]{x0, z0, y0});
        for (int j = 1; j < n; j++) {
            double x1 = c.p[j * 2];
            double z1 = c.p[j * 2 + 1];
            float y1 = lift(level, x1, z1);
            split(level, out, x0, z0, y0, x1, z1, y1, 3, n);
            out.add(new double[]{x1, z1, y1});
            x0 = x1;
            z0 = z1;
            y0 = y1;
        }
        int m = out.size();
        c.p = new double[m * 2];
        c.y = new float[m];
        for (int j = 0; j < m; j++) {
            double[] q = out.get(j);
            c.p[j * 2] = q[0];
            c.p[j * 2 + 1] = q[1];
            c.y[j] = (float) q[2];
        }
        // 细分点都在原来的直线段上，总长和侧枝的岔口位置都不变
        measure(c);
    }

    private void split(BlockGetter level, List<double[]> out, double x0, double z0, float y0, double x1, double z1, float y1, int left, int base) {
        if (left <= 0 || out.size() + 2 >= Math.max(MAX_POINTS, base)) {
            return;
        }
        boolean same = Float.isNaN(y0) ? Float.isNaN(y1) : !Float.isNaN(y1) && Math.abs(y0 - y1) < 0.1F;
        if (same || Math.hypot(x1 - x0, z1 - z0) < 0.12D) {
            return;
        }
        double mx = (x0 + x1) * 0.5D;
        double mz = (z0 + z1) * 0.5D;
        float my = lift(level, mx, mz);
        split(level, out, x0, z0, y0, mx, mz, my, left - 1, base);
        out.add(new double[]{mx, mz, my});
        split(level, out, mx, mz, my, x1, z1, y1, left - 1, base);
    }

    private void ensureSampled() {
        if (!sampled) {
            sample();
        } else {
            double dx = center.x - sampledX;
            double dz = center.z - sampledZ;
            if (dx * dx + dz * dz > 0.25D || Math.abs(center.y - sampledY) > 0.3D) {
                sample();
            }
        }
    }

    // ------------------------------------------------------------------ 查询

    /** 裂纹道数（含侧枝） */
    public int size() {
        return cracks.length;
    }

    /** 这道是不是侧枝 */
    public boolean isBranch(int i) {
        return cracks[i].parent >= 0;
    }

    /** 侧枝从哪道主裂纹岔出（主裂纹是 −1） */
    public int parent(int i) {
        return cracks[i].parent;
    }

    /** 侧枝岔出处离主裂纹起始端几格（主裂纹是 0） */
    public float forkOffset(int i) {
        return cracks[i].fork;
    }

    /** 这道裂纹长几格 */
    public float length(int i) {
        return cracks[i].length;
    }

    /**
     * 侧枝跟着主裂纹长：主裂纹的头走到 parentHead 格时，侧枝长到哪（主裂纹长过岔口才开始，主裂纹长满时侧枝也长满）
     */
    public float branchHead(int i, float parentHead) {
        Crack c = cracks[i];
        float mainLen = cracks[c.parent].length;
        float past = parentHead - c.fork;
        return past <= 0F ? 0F : Mth.clamp(past / Math.max(0.1F, mainLen - c.fork), 0F, 1F) * c.length;
    }

    /** 整张网平移到新的圆心（跟随首领；水平挪了半格以上、或上下挪了 0.3 格以上，下次画之前重找地面） */
    public void moveTo(Vec3 c) {
        center = c;
    }

    public Vec3 center() {
        return center;
    }

    /** 第 i 道裂纹从起始端走 f（0~1）处的点（世界坐标，贴地） */
    public Vec3 pointAt(int i, float f) {
        return at(i, Mth.clamp(f, 0F, 1F) * cracks[i].length);
    }

    /** 第 i 道裂纹离起始端 s 格处的点（世界坐标，贴地） */
    public Vec3 at(int i, float s) {
        ensureSampled();
        Crack c = cracks[i];
        int j = segment(c, s);
        float f = Mth.clamp((s - c.d[j - 1]) / Math.max(1.0E-5F, c.d[j] - c.d[j - 1]), 0F, 1F);
        double x = Mth.lerp(f, c.p[j * 2 - 2], c.p[j * 2]);
        double z = Mth.lerp(f, c.p[j * 2 - 1], c.p[j * 2 + 1]);
        return new Vec3(center.x + x, center.y + height(c, j, f), center.z + z);
    }

    /** 两点之间的地面高度；有一头找不到地面就用另一头，都找不到用圆心高度 */
    private static float height(Crack c, int j, float f) {
        float y0 = c.y[j - 1];
        float y1 = c.y[j];
        if (Float.isNaN(y0)) {
            return Float.isNaN(y1) ? 0F : y1;
        }
        return Float.isNaN(y1) ? y0 : Mth.lerp(f, y0, y1);
    }

    /** 第 i 道裂纹的尖（终点） */
    public Vec3 tip(int i) {
        return at(i, cracks[i].length);
    }

    /**
     * 第一次走到离圆心 r 格处时离起始端几格：往里伸的（outward = false）找第一次进到 r 以内，往外劈的找第一次出到 r 以外；
     * 走不到就是整道长度。
     */
    public float distanceToRadius(int i, float r, boolean outward) {
        Crack c = cracks[i];
        double prev = Math.hypot(c.p[0], c.p[1]);
        if (outward ? prev >= r : prev <= r) {
            return 0F;
        }
        for (int j = 1; j < c.d.length; j++) {
            double cur = Math.hypot(c.p[j * 2], c.p[j * 2 + 1]);
            if (outward ? cur >= r : cur <= r) {
                float f = (float) ((r - prev) / (cur - prev == 0D ? 1.0E-5D : cur - prev));
                return Mth.lerp(Mth.clamp(f, 0F, 1F), c.d[j - 1], c.d[j]);
            }
            prev = cur;
        }
        return c.length;
    }

    /** 往里伸的裂纹第一次走到离圆心 r 格以内时离起始端几格 */
    public float lengthAtRadius(int i, float r) {
        return distanceToRadius(i, r, false);
    }

    /** 第 i 道裂纹的点（世界坐标 xyz，略高于地面）写进 out，返回点数（沿缝跑电流用） */
    public int path(int i, double[] out) {
        ensureSampled();
        Crack c = cracks[i];
        int n = Math.min(c.d.length, out.length / 3);
        for (int j = 0; j < n; j++) {
            float y = c.y[j];
            out[j * 3] = center.x + c.p[j * 2];
            out[j * 3 + 1] = center.y + (Float.isNaN(y) ? 0F : y) + 0.06D;
            out[j * 3 + 2] = center.z + c.p[j * 2 + 1];
        }
        return n;
    }

    /** 网上随机一点（沿缝冒余烬用） */
    public Vec3 randomPoint(RandomSource random) {
        int i = random.nextInt(cracks.length);
        return at(i, random.nextFloat() * cracks[i].length);
    }

    // ------------------------------------------------------------------ 画

    /** 每道裂纹烧到 / 长到哪（离起始端几格） */
    @FunctionalInterface
    public interface Reach {
        float head(int crack);
    }

    /** 沿裂纹的一个量（热度、淡出系数）：behind = 这一点在「头」后面几格（负数 = 头前面还没烧到） */
    @FunctionalInterface
    public interface Heat {
        float at(int crack, float behind);
    }

    /** 不带淡出的 {@link #render(VfxDraw, Reach, Heat, Heat, boolean, float, VfxPlasma.Tint)} */
    public void render(VfxDraw draw, Reach reach, Heat heat, boolean drawAhead, float alpha, VfxPlasma.Tint tint) {
        render(draw, reach, heat, null, drawAhead, alpha, tint);
    }

    /**
     * 画整张网。头后面的缝按热度画三层；drawAhead 为 true 时头前面也画（只有焦黑底和一线暗芯，热度用 heat 给的负数段），
     * 为 false 时头前面不画（往外劈、边长边亮）。fade 给每一节的整体淡出（0 或更小就不画这一节，移动的环只画环附近那一段），null = 都是 1。
     */
    public void render(VfxDraw draw, Reach reach, Heat heat, @Nullable Heat fade, boolean drawAhead, float alpha, VfxPlasma.Tint tint) {
        if (alpha <= 0.01F) {
            return;
        }
        ensureSampled();
        int tier = Vfx.tier();
        int coreGlow = tint.glow();
        int coreInner = tint.inner();
        int coreHot = tint.core();
        for (int i = 0; i < cracks.length; i++) {
            Crack c = cracks[i];
            float head = reach.head(i);
            float limit = drawAhead ? c.length : Math.min(c.length, head);
            if (limit <= 0.01F) {
                continue;
            }
            double[] p = c.p;
            float[] d = c.d;
            float[] ys = c.y;
            for (int j = 1; j < d.length; j++) {
                float s0 = d[j - 1];
                if (s0 >= limit) {
                    break;
                }
                float y0r = ys[j - 1];
                float y1r = ys[j];
                if (Float.isNaN(y0r) || Float.isNaN(y1r)) {
                    continue;
                }
                float s1 = Math.min(d[j], limit);
                float mid = (s0 + s1) * 0.5F;
                float behind = head - mid;
                float fa = fade == null ? 1F : fade.at(i, behind);
                if (fa <= 0.01F) {
                    continue;
                }
                float f = (s1 - s0) / Math.max(1.0E-5F, d[j] - s0);
                double x0 = center.x + p[j * 2 - 2];
                double z0 = center.z + p[j * 2 - 1];
                double x1 = center.x + Mth.lerp(f, p[j * 2 - 2], p[j * 2]);
                double z1 = center.z + Mth.lerp(f, p[j * 2 - 1], p[j * 2 + 1]);
                double y0 = center.y + y0r + 0.03D;
                double y1 = center.y + Mth.lerp(f, y0r, y1r) + 0.03D;
                float a = alpha * Math.min(1F, fa);
                // 往尖收细
                float taper = 1F - 0.55F * mid / Math.max(0.5F, c.length);
                float h = Mth.clamp(heat.at(i, behind), 0F, 1F);
                boolean ahead = mid > head;
                float half = c.width * taper * (ahead ? 0.55F : 1F);
                quad(draw, x0, y0 - 0.005D, z0, x1, y1 - 0.005D, z1, half * 1.8F, Vfx.argb(CHAR, (ahead ? 0.22F : 0.38F) * a), 1, true);
                if (tier >= 1 && !ahead) {
                    quad(draw, x0, y0, z0, x1, y1, z1, half * 3.2F, Vfx.argb(coreGlow, 0.3F * h * a), 1, false);
                }
                int hot = h < 0.7F ? Vfx.lerp(coreGlow, coreInner, h / 0.7F) : Vfx.lerp(coreInner, coreHot, (h - 0.7F) / 0.3F);
                // 热芯只在烧得够热时才有白线（STRIP 的芯宽参数），冷的缝是一线暗光
                int core = h > 0.6F ? Math.round(80F + 260F * (h - 0.6F) / 0.4F) : 1;
                quad(draw, x0, y0 + 0.002D, z0, x1, y1 + 0.002D, z1, half, Vfx.argb(hot & 0xFFFFFF, (0.25F + 0.75F * h) * a), core, false);
            }
        }
    }

    /** 平躺的一节（两头可以不一样高：台阶处斜着跨过去），横截面用 STRIP 的软边 */
    private static void quad(VfxDraw draw, double x0, double y0, double z0, double x1, double y1, double z1, float half, int argb, int param,
                             boolean translucent) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5D || (argb >>> 24) < 2) {
            return;
        }
        // 两头各伸出半宽，相邻两节接缝处不露空
        double ex = dx / len * half * 0.5D;
        double ez = dz / len * half * 0.5D;
        double sx = -dz / len * half;
        double sz = dx / len * half;
        draw.quad(x0 - ex - sx, y0, z0 - ez - sz, 0F, -1F, x1 + ex - sx, y1, z1 + ez - sz, 1F, -1F,
                x1 + ex + sx, y1, z1 + ez + sz, 1F, 1F, x0 - ex + sx, y0, z0 - ez + sz, 0F, 1F,
                VfxMode.STRIP, param, argb, translucent);
    }
}
