package com.flameforge.essentialcraft5.client.builder;

/**
 * 创世神之杖飞行特效里不碰渲染的那部分计算：落位时刻、容量和每刻起飞上限、弧线路径、长大和转正、落位时的状态走向。
 * 只用 Java 自带的数学，不引用任何游戏类，专用服的 GameTest 也能直接调（{@code gametest/BuilderFlightMathTests}）。
 * 时间单位都是客户端刻，可以带小数。
 */
public final class BuilderFlightMath {

    /** 飞出时从起飞到落位的刻数（提前量不够时按提前量缩短） */
    public static final double FLIGHT_TICKS = 10D;
    /** 到了落位时刻真方块还没出现时，停在落点上最多再等几刻 */
    public static final double WAIT_MAX = 10D;
    /** 收到收尾包时格子里还没方块的，先等几刻再判断（最后一批真放和收尾包同一刻发，方块变化下一刻才同步过来） */
    public static final double END_GRACE = 2D;
    /** 落位前几刻转正 */
    public static final double SETTLE_TICKS = 2D;
    /** 「光点凝成」画法：落位前几刻光点到位、方块从小长满 */
    public static final double CONDENSE_TICKS = 4D;
    /** 飞回（撤销）的刻数下限和上限，按包里的提前量取 */
    public static final double BACK_MIN = 6D;
    public static final double BACK_MAX = 14D;
    /** 起飞时方块的大小（落位时是 1） */
    public static final double START_SCALE = 0.4D;
    /** 离镜头这么远以外只闪不飞、再远不画 */
    public static final double FLASH_DISTANCE = 64D;
    public static final double CULL_DISTANCE = 128D;
    /** 配置填 0 时按特效画质（低 / 中 / 高）取的同屏上限 */
    private static final int[] CAPACITY_BY_TIER = {24, 64, 128};
    /** 没等到真方块的飞行方块碎掉要几刻：先抖一下、迸出碎块，再缩小、往薰衣草色褪掉 */
    public static final double BREAK_TICKS = 7D;
    /** 碎掉的头几刻只抖不缩（让人先看清是这一块要碎） */
    public static final double BREAK_HOLD_TICKS = 1.5D;

    /** {@link #settle} 的结果 */
    public static final int FLYING = 0;
    public static final int LAND = 1;
    public static final int HOVER = 2;
    public static final int SHATTER = 3;

    /** {@link #range} 的结果 */
    public static final int RANGE_FLY = 0;
    public static final int RANGE_FLASH = 1;
    public static final int RANGE_NONE = 2;

    private BuilderFlightMath() {
    }

    // ------------------------------------------------------------------ 数量

    /** 同时在飞的方块最多几块：配置大于 0 用配置，否则按画质档（0 低、1 中、2 高） */
    public static int capacity(int configured, int tier) {
        if (configured > 0) {
            return configured;
        }
        return CAPACITY_BY_TIER[Math.max(0, Math.min(CAPACITY_BY_TIER.length - 1, tier))];
    }

    /** 每刻最多起飞几块（容量的四分之一，至少一块） */
    public static int launchesPerTick(int capacity) {
        return Math.max(1, capacity / 4);
    }

    /** 按离镜头的距离平方定：飞、只闪、不画 */
    public static int range(double distanceSqr) {
        if (distanceSqr >= CULL_DISTANCE * CULL_DISTANCE) {
            return RANGE_NONE;
        }
        return distanceSqr >= FLASH_DISTANCE * FLASH_DISTANCE ? RANGE_FLASH : RANGE_FLY;
    }

    // ------------------------------------------------------------------ 时刻

    /** 同一包里第 i 格（共 n 格）的落位时刻 = 收包时刻 + 提前量 + i / n */
    public static double landAt(double received, int lead, int i, int n) {
        return received + Math.max(0, lead) + (n <= 0 ? 0D : (double) i / n);
    }

    /** 起飞时刻：落位前 {@link #FLIGHT_TICKS} 刻，提前量不够时从收包那一刻起飞；离落位不到一刻的仍按一刻算（画面从半路开始） */
    public static double launchAt(double received, double landAt) {
        double flight = Math.min(FLIGHT_TICKS, Math.max(1D, landAt - received));
        return landAt - flight;
    }

    /**
     * 碎掉过程中方块的大小（相对开始碎时的大小）：头 {@link #BREAK_HOLD_TICKS} 刻保持原样，之后按二次缓入缩到 0，
     * 到 {@link #BREAK_TICKS} 刻时正好为 0。elapsed 是从开始碎起过了几刻。
     */
    public static double breakScale(double elapsed) {
        if (elapsed <= BREAK_HOLD_TICKS) {
            return 1D;
        }
        double k = clamp((elapsed - BREAK_HOLD_TICKS) / (BREAK_TICKS - BREAK_HOLD_TICKS), 0D, 1D);
        return 1D - k * k;
    }

    /** 碎掉过程中往薰衣草色拉的比例：从 0 线性涨到 0.8 */
    public static float breakTint(double elapsed) {
        return (float) (0.8D * clamp(elapsed / BREAK_TICKS, 0D, 1D));
    }

    /** 碎掉过程中的抖动幅度（格）：开始时 0.05，随时间减到 0 */
    public static double breakJitter(double elapsed) {
        return 0.05D * (1D - clamp(elapsed / BREAK_TICKS, 0D, 1D));
    }

    /** 飞回（撤销）一格要几刻 */
    public static double backTicks(int lead) {
        return Math.max(BACK_MIN, Math.min(BACK_MAX, lead));
    }

    /** 飞到哪儿了（0 = 刚起飞，1 = 到落点），超出两端截住 */
    public static double progress(double now, double launchAt, double landAt) {
        double span = landAt - launchAt;
        if (span <= 1.0E-6D) {
            return now >= landAt ? 1D : 0D;
        }
        return clamp((now - launchAt) / span, 0D, 1D);
    }

    // ------------------------------------------------------------------ 路径

    /** 弧线顶点比两端连线的中点高几格 */
    public static double apexLift(double distance) {
        return 0.3D * distance + 1D;
    }

    /**
     * 二次贝塞尔：起点 → 顶点（连线中点往上 {@link #apexLift}）→ 终点，取参数 t 处的点写进 out[off..off+2]。
     */
    public static void bezier(double fx, double fy, double fz, double tx, double ty, double tz, double t, double[] out, int off) {
        double dx = tx - fx;
        double dy = ty - fy;
        double dz = tz - fz;
        double lift = apexLift(Math.sqrt(dx * dx + dy * dy + dz * dz));
        double mx = (fx + tx) * 0.5D;
        double my = (fy + ty) * 0.5D + lift;
        double mz = (fz + tz) * 0.5D;
        double k = 1D - t;
        double a = k * k;
        double b = 2D * k * t;
        double c = t * t;
        out[off] = a * fx + b * mx + c * tx;
        out[off + 1] = a * fy + b * my + c * ty;
        out[off + 2] = a * fz + b * mz + c * tz;
    }

    /** 弧线全长（分 16 段量），拖尾按它换算成固定的格数 */
    public static double arcLength(double fx, double fy, double fz, double tx, double ty, double tz) {
        double[] p = new double[6];
        bezier(fx, fy, fz, tx, ty, tz, 0D, p, 0);
        double total = 0D;
        for (int i = 1; i <= 16; i++) {
            int cur = (i & 1) * 3;
            int prev = 3 - cur;
            bezier(fx, fy, fz, tx, ty, tz, i / 16D, p, cur);
            double dx = p[cur] - p[prev];
            double dy = p[cur + 1] - p[prev + 1];
            double dz = p[cur + 2] - p[prev + 2];
            total += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return total;
    }

    /** 拖尾尾端的路径参数：头在 head，往回约 length 格（按全长 pathLength 折算），不早于起点 */
    public static double trailTail(double head, double pathLength, double length) {
        if (pathLength <= 1.0E-6D) {
            return head;
        }
        return Math.max(0D, head - length / pathLength);
    }

    public static double easeOutCubic(double t) {
        double k = 1D - clamp(t, 0D, 1D);
        return 1D - k * k * k;
    }

    /** 位置用的路径参数：飞行进度过一遍 easeOutCubic（出手快、落位前放慢） */
    public static double pathParam(double progress) {
        return easeOutCubic(progress);
    }

    /** 方块大小：从 {@link #START_SCALE} 长到 1，跟位置用同一条缓动 */
    public static double grow(double progress) {
        return START_SCALE + (1D - START_SCALE) * easeOutCubic(progress);
    }

    /** 离镜头这么近（格）以内的飞行方块不画 */
    public static final double NEAR_HIDE = 1.0D;
    /** 离镜头这么远（格）起按原大小画 */
    public static final double NEAR_FULL = 5.0D;

    /**
     * 离镜头近的飞行方块再缩小的倍数：杖尖离眼睛不到 1 格，铺的范围又常在脚边，方块刚出手、沿弧线从头顶和镜头前横穿时按原大小画会挡住大半个画面，
     * 所以在 {@link #NEAR_HIDE} 到 {@link #NEAR_FULL} 格之间按 smoothstep 从 0 长到 1。
     */
    public static double nearCamera(double distance) {
        double t = clamp((distance - NEAR_HIDE) / (NEAR_FULL - NEAR_HIDE), 0D, 1D);
        return t * t * (3D - 2D * t);
    }

    /**
     * 绕自身一轴转过的角度（弧度）：起飞时半圈，平滑转回，落位前 {@link #SETTLE_TICKS} 刻已经转正（0）。
     *
     * @param age      起飞后过了几刻
     * @param duration 起飞到落位一共几刻
     */
    public static double spin(double age, double duration) {
        double turn = duration - SETTLE_TICKS;
        if (turn <= 1.0E-6D) {
            return 0D;
        }
        double s = clamp(age / turn, 0D, 1D);
        return Math.PI * (1D - s * s * (3D - 2D * s));
    }

    // ------------------------------------------------------------------ 落位

    /**
     * 收到收尾包时这一格什么时候再判断：格子里已经有方块的不用判断（返回 -1，照常落位），没有的等 {@link #END_GRACE} 刻。
     */
    public static double endCheckAt(double now, boolean present) {
        return present ? -1D : now + END_GRACE;
    }

    /**
     * 这一刻这一格该怎么走：
     * <ul>
     *   <li>收尾包的判断时刻到了、格子里还是没有方块：{@link #SHATTER}；</li>
     *   <li>还没到落位时刻：{@link #FLYING}；</li>
     *   <li>到了，真方块已经在：{@link #LAND}（闪一下消失，真方块接手）；</li>
     *   <li>到了还没有，等满 {@link #WAIT_MAX} 刻：{@link #SHATTER}，没满：{@link #HOVER}（停在落点上慢慢旋）。</li>
     * </ul>
     *
     * @param endCheckAt 收尾包定下的判断时刻，没有收尾包时传负数
     */
    public static int settle(double now, double landAt, boolean present, double endCheckAt) {
        if (endCheckAt >= 0D && now >= endCheckAt && !present) {
            return SHATTER;
        }
        if (now < landAt) {
            return FLYING;
        }
        if (present) {
            return LAND;
        }
        return now - landAt >= WAIT_MAX ? SHATTER : HOVER;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
