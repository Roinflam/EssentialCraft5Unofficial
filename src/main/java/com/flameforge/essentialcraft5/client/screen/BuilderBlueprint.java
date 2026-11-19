package com.flameforge.essentialcraft5.client.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 面板左边那张蓝图的投影和缩放：<b>纯函数</b>，不碰客户端类（GameTest 直接测）。
 * <ul>
 *   <li>投影：外框只有一层高、或者两个水平方向都不止一格时画俯视图，玩家朝的方向朝上（右手边朝右）；竖着的一片（竖墙、贴在墙上的沿面：
 *       高不止一格、某个水平方向只有一格）画正视图，横轴是不为一格的那个水平方向，往上为上。几格投到同一个点时按优先级留一格：
 *       要拆 &gt; 缺料 &gt; 要放 &gt; 放不了。</li>
 *   <li>缩放（{@link #fit}）：每格边长取放得下的最大整数，夹在 {@value #MIN_CELL}～{@value #MAX_CELL} 像素；边长 ≥ {@value #GAP_FROM} 时格间留 1 像素缝。
 *       放不下 {@value #MIN_CELL} 像素一格时把 k × k 格并成一格（k 取最小够用的整数），并的时候同样按优先级留一格。整张图在区域里居中，奇数像素留右下。</li>
 * </ul>
 */
public final class BuilderBlueprint {

    /** 每格至少几像素（更小就并格） */
    public static final int MIN_CELL = 2;
    /** 每格最多几像素（格子少时不放得太大） */
    public static final int MAX_CELL = 12;
    /** 每格到这么大时格间留 1 像素缝 */
    public static final int GAP_FROM = 4;

    /** 格子种类 */
    public static final byte EMPTY = 0;
    public static final byte BLOCKED = 1;
    /** 要放、还没放（施工中） */
    public static final byte TODO = 2;
    public static final byte SHORT = 3;
    public static final byte PLACE = 4;
    public static final byte BREAK = 5;
    /** 要拆、已经拆了（施工中） */
    public static final byte BROKEN = 6;

    /** 投到同一点、并格时谁留下：要拆 &gt; 缺料 &gt; 要放 &gt; 放不了 */
    private static final int[] PRIORITY = {0, 1, 2, 3, 2, 4, 4};

    private BuilderBlueprint() {
    }

    /** 一张投影好的格子图（按行存，cols × rows），colour 是每格的底色（0xRRGGBB，不用的格为 0） */
    public record Grid(int cols, int rows, byte[] kinds, int[] colours, boolean frontView) {

        public byte kind(int col, int row) {
            return kinds[row * cols + col];
        }

        public int colour(int col, int row) {
            return colours[row * cols + col];
        }

        /** 不同种类的格各有几个（测试用） */
        public int count(byte kind) {
            int n = 0;
            for (byte k : kinds) {
                if (k == kind) {
                    n++;
                }
            }
            return n;
        }
    }

    /**
     * 格子图放进一块区域的结果：k 是几格并一格，cols × rows 是并完的格数，cell 是每格边长（含缝），gap 是缝宽，(x, y) 是左上角
     */
    public record Fit(int k, int cols, int rows, int cell, int gap, int x, int y) {

        public int width() {
            return cols * cell;
        }

        public int height() {
            return rows * cell;
        }
    }

    /** 把 srcCols × srcRows 格放进 (x, y, w, h)；区域连一格 {@value #MIN_CELL} 像素都放不下时返回 cols = 0 的空结果 */
    public static Fit fit(int srcCols, int srcRows, int x, int y, int w, int h) {
        if (srcCols <= 0 || srcRows <= 0 || w < MIN_CELL || h < MIN_CELL) {
            return new Fit(1, 0, 0, 0, 0, x, y);
        }
        int k = Math.max(1, Math.max(ceilDiv(srcCols * MIN_CELL, w), ceilDiv(srcRows * MIN_CELL, h)));
        while (true) {
            int cols = ceilDiv(srcCols, k);
            int rows = ceilDiv(srcRows, k);
            int cell = Math.min(MAX_CELL, Math.min(w / cols, h / rows));
            if (cell >= MIN_CELL) {
                int gap = cell >= GAP_FROM ? 1 : 0;
                return new Fit(k, cols, rows, cell, gap, x + (w - cols * cell) / 2, y + (h - rows * cell) / 2);
            }
            k++;
        }
    }

    /**
     * 投影：前 n 项格子（坐标 {@link BlockPos#asLong}、种类、底色）按玩家朝向投成平面图，见类注释
     */
    public static Grid project(long[] cells, byte[] kinds, int[] colours, int n, Direction facing) {
        if (n <= 0) {
            return new Grid(0, 0, new byte[0], new int[0], false);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            long p = cells[i];
            int x = BlockPos.getX(p);
            int y = BlockPos.getY(p);
            int z = BlockPos.getZ(p);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        int xs = maxX - minX + 1;
        int ys = maxY - minY + 1;
        int zs = maxZ - minZ + 1;
        Direction forward = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        Direction right = forward.getClockWise();
        boolean front = ys > 1 && Math.min(xs, zs) == 1;
        int[] us = new int[n];
        int[] vs = new int[n];
        int minU = Integer.MAX_VALUE;
        int minV = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int maxV = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            long p = cells[i];
            int x = BlockPos.getX(p);
            int y = BlockPos.getY(p);
            int z = BlockPos.getZ(p);
            int u;
            int v;
            if (front) {
                // 横轴：不为一格的那个水平方向；和右手边同一根轴时按右手边的正负，否则按前方的正负
                boolean alongX = xs >= zs;
                Direction axisDir = right.getAxis() == (alongX ? Direction.Axis.X : Direction.Axis.Z) ? right : forward;
                u = alongX ? x * axisDir.getStepX() : z * axisDir.getStepZ();
                v = -y;
            } else {
                u = x * right.getStepX() + z * right.getStepZ();
                v = -(x * forward.getStepX() + z * forward.getStepZ());
            }
            us[i] = u;
            vs[i] = v;
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        int cols = maxU - minU + 1;
        int rows = maxV - minV + 1;
        byte[] outKinds = new byte[cols * rows];
        int[] outColours = new int[cols * rows];
        for (int i = 0; i < n; i++) {
            int index = (vs[i] - minV) * cols + (us[i] - minU);
            byte kind = kinds[i];
            if (PRIORITY[kind] > PRIORITY[outKinds[index]]) {
                outKinds[index] = kind;
                outColours[index] = colours[i];
            }
        }
        return new Grid(cols, rows, outKinds, outColours, front);
    }

    /** k × k 并一格（k = 1 原样返回），并的时候按优先级留一格 */
    public static Grid downsample(Grid grid, int k) {
        if (k <= 1 || grid.cols == 0) {
            return grid;
        }
        int cols = ceilDiv(grid.cols, k);
        int rows = ceilDiv(grid.rows, k);
        byte[] kinds = new byte[cols * rows];
        int[] colours = new int[cols * rows];
        for (int r = 0; r < grid.rows; r++) {
            for (int c = 0; c < grid.cols; c++) {
                byte kind = grid.kind(c, r);
                int index = (r / k) * cols + c / k;
                if (PRIORITY[kind] > PRIORITY[kinds[index]]) {
                    kinds[index] = kind;
                    colours[index] = grid.colour(c, r);
                }
            }
        }
        return new Grid(cols, rows, kinds, colours, grid.frontView);
    }

    /** 两点形状、撤销这些没有逐格数据时画的示意外框：w × l 格，全部按 kind 填 */
    public static Grid box(int w, int l, byte kind, int colour) {
        int cols = Math.max(1, w);
        int rows = Math.max(1, l);
        byte[] kinds = new byte[cols * rows];
        int[] colours = new int[cols * rows];
        java.util.Arrays.fill(kinds, kind);
        java.util.Arrays.fill(colours, colour);
        return new Grid(cols, rows, kinds, colours, false);
    }

    /** 两种颜色三个通道之差的和不超过这个值就算相近（石砖、苔石砖、裂纹石砖的地图色完全一样，深板岩和石头差 36） */
    public static final int NEAR_COLOUR = 60;
    /** 相近的几种之间明度（0～255）拉开的级差 */
    public static final int SPREAD_STEP = 36;
    /** 拉开后明度的上下限：再往外，白色系、黑色系几种之间就看不出差别了 */
    private static final int SPREAD_MIN = 24;
    private static final int SPREAD_MAX = 236;

    /**
     * 混铺几种方块在蓝图和比例条上的颜色（0xRRGGBB，按表里的顺序）：颜色相近的连成一组（A 近 B、B 近 C 时三种同组），
     * 组里按表里的顺序从暗到亮排明度，相邻两级差 {@value #SPREAD_STEP}，整组以组的平均明度为中心；
     * 靠近纯白或纯黑时整组往中间挪，放不下时缩小级差。不和别的相近的颜色不变。
     */
    public static int[] distinctColours(int[] base) {
        int n = base.length;
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (near(base[i], base[j])) {
                    int a = root(parent, i);
                    int b = root(parent, j);
                    parent[Math.max(a, b)] = Math.min(a, b);
                }
            }
        }
        int[] out = base.clone();
        boolean[] done = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (done[i]) {
                continue;
            }
            int g = root(parent, i);
            int members = 0;
            int sum = 0;
            for (int k = i; k < n; k++) {
                if (root(parent, k) == g) {
                    members++;
                    sum += luma(base[k]);
                }
            }
            if (members <= 1) {
                done[i] = true;
                continue;
            }
            float step = Math.min(SPREAD_STEP, (SPREAD_MAX - SPREAD_MIN) / (float) (members - 1));
            float half = step * (members - 1) / 2F;
            float centre = Math.max(SPREAD_MIN + half, Math.min(SPREAD_MAX - half, sum / (float) members));
            int rank = 0;
            for (int k = i; k < n; k++) {
                if (root(parent, k) == g) {
                    out[k] = towardLuma(base[k], centre - half + rank * step);
                    done[k] = true;
                    rank++;
                }
            }
        }
        return out;
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            i = parent[i];
        }
        return i;
    }

    /** 明度：0.299 R + 0.587 G + 0.114 B */
    private static int luma(int rgb) {
        return ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
    }

    /** 往白或往黑混到明度约为 target（三个通道按同一比例混，明度随之线性变化） */
    private static int towardLuma(int rgb, float target) {
        int l = luma(rgb);
        if (Math.abs(target - l) < 0.5F) {
            return rgb;
        }
        float t = target > l ? (target - l) / Math.max(1F, 255F - l) : (target - l) / Math.max(1F, l);
        return shade(rgb, t);
    }

    private static boolean near(int a, int b) {
        int d = Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
        return d <= NEAR_COLOUR;
    }

    /** t &gt; 0 往白混 t，t &lt; 0 往黑混 −t */
    private static int shade(int rgb, float t) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = rgb >> shift & 0xFF;
            int v = t >= 0 ? Math.round(c + (255 - c) * Math.min(1F, t)) : Math.round(c * Math.max(0F, 1F + t));
            out |= Math.max(0, Math.min(255, v)) << shift;
        }
        return out;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
