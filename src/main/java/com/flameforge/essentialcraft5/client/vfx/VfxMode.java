package com.flameforge.essentialcraft5.client.vfx;

/**
 * {@code vfx.fsh} 的模式号（顶点 UV2 的第一个分量）和各自参数的含义。编号也是 {@link VfxAtlas} 预烘贴图里的格子号（4 × 4，从左上往右、往下数），
 * 改这里要同时改着色器。
 * <p>
 * 局部坐标（UV0）分两种：长条类（{@link #isStrip}）x 沿长度、y 横向 −1~1；圆面类 x、y 都是 −1~1，圆心在 (0, 0)。
 */
public final class VfxMode {

    /** 光带：白热的芯 + 柔光。参数 = 芯宽（千分比，0 取 200） */
    public static final int STRIP = 0;
    /** 流动光束：x 用沿长度的格数，噪声往前流。参数 = 种子 */
    public static final int BEAM = 1;
    /** 柔光点。参数 = 硬度（千分比） */
    public static final int DOT = 2;
    /** 细环（半径 0.9）。参数 = 线宽（千分比，0 取 30） */
    public static final int RING = 3;
    /** 冲击波环（外沿锐利，往里拖尾巴）。参数 = 尾巴长度（千分比，0 取 250） */
    public static final int SHOCK = 4;
    /** 火花拖尾：x 从 0（尾）到 1（头） */
    public static final int SPARK = 5;
    /** 等离子光带（两股细丝反着流、快闪）。参数 = 种子 */
    public static final int PLASMA = 6;
    /** 预警填充。参数 = 进度（千分比）+ 1024 × 样式（0 钟面扫开，1 由里往外涨） */
    public static final int FILL = 7;
    /** 熔岩裂纹圆面。参数 = 热度（千分比）+ 1024 × 半径格数，用 {@link #molten} 拼（纹路按世界尺寸取，大圈小圈一样粗） */
    public static final int MOLTEN = 8;
    /** 星芒（光点 + 十字光线）。参数 = 光线长度（千分比，0 取 1000） */
    public static final int FLARE = 9;
    /** 烟团（普通半透明混合用）。参数 = 种子 */
    public static final int SMOKE = 10;
    /** 爆点（白热核 + 长短不一的放射光线）。参数 = 种子 */
    public static final int BURST = 11;

    /** 柔和冲击波（结算用：两边都软、没有亮边）。参数 = 往里拖的宽度（千分比，0 取 200） */
    public static final int WAVE = 12;

    /** 模式总数（预烘贴图的格子数） */
    public static final int COUNT = 13;

    /** 「进度」类参数的满值 */
    public static final int FULL = 1000;

    private VfxMode() {
    }

    /** 长条类：局部坐标 x 沿长度 */
    public static boolean isStrip(int mode) {
        return mode == STRIP || mode == BEAM || mode == SPARK || mode == PLASMA;
    }

    /** 熔岩纹参数：热度（0~1）+ 1024 × 圆面半径（格，夹到 1~31；顶点 UV2 是有符号 16 位） */
    public static int molten(float heat, float radius) {
        int h = Math.round(Math.max(0F, Math.min(1F, heat)) * FULL);
        return h + 1024 * Math.max(1, Math.min(31, Math.round(radius)));
    }

    /** 填充类参数：进度 + 1024 × 样式 */
    public static int progress(float progress, int variant) {
        int p = Math.round(Math.max(0F, Math.min(1F, progress)) * FULL);
        return p + 1024 * variant;
    }
}
