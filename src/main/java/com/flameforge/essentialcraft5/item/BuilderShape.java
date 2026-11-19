package com.flameforge.essentialcraft5.item;

import org.jetbrains.annotations.Nullable;

/**
 * 创世神之杖「怎么圈范围」（和「每格做什么」的 {@link BuilderMode} 正交），存在魔杖 NBT {@value BuilderSelection#TAG_SHAPE}，
 * 没有这个键就是 {@link #POINTS}（老魔杖的行为完全不变）。几何见 {@link BuilderShapes}，设计见 tools/plan/builder_upgrade.md §1.1。
 */
public enum BuilderShape {
    /** 右键两个方块定两个角（原作的做法） */
    POINTS("points", 0, 0),
    /** 点一下铺一片 W × L 的水平面（点中顶面 / 底面以点中格为中心，侧面往外延伸，没点中铺脚下一层） */
    PLANE("plane", 5, 5),
    /** 从锚点沿玩家的水平朝向延伸 L 格、宽 W（横向居中），修桥用 */
    LINE("line", 1, 8),
    /**
     * 竖墙：从锚点沿玩家朝向的左右展开宽 W（居中）、往上高 H（点中底面时往下）。尺寸的第二个数是高（NBT 存成 {@code [W, 1, H]}），
     * 默认 5 × 4。二阶起（{@link BuilderTier#allows(BuilderShape)}）
     */
    WALL("wall", 5, 4),
    /**
     * 沿面：点中一个面，和它相连、同一种方块、同一面露在外面的那一片，外侧那一层全部铺上（{@link BuilderSurface}，服务端分几刻测量）。
     * 没有可调的尺寸，上限是档位的格数。二阶起
     */
    FACE("face", 0, 0);

    private static final BuilderShape[] VALUES = values();

    private final String id;
    private final int defaultWidth;
    private final int defaultLength;

    BuilderShape(String id, int defaultWidth, int defaultLength) {
        this.id = id;
        this.defaultWidth = defaultWidth;
        this.defaultLength = defaultLength;
    }

    /** NBT 和语言键里的名字 */
    public String id() {
        return id;
    }

    public int defaultWidth() {
        return defaultWidth;
    }

    public int defaultLength() {
        return defaultLength;
    }

    /**
     * 点一下就按点中的地方开工的形状（除两点以外都是）：右键方块开工、潜行对空气右键提示撤销、画虚影。
     * 能不能用滚轮调尺寸看 {@link #resizable}（沿面不能）。
     */
    public boolean sized() {
        return this != POINTS;
    }

    /** 能用「魔杖键 + 滚轮」调尺寸的形状：铺面、直线、竖墙（沿面的范围由测量决定，两点由两个角决定） */
    public boolean resizable() {
        return this == PLANE || this == LINE || this == WALL;
    }

    /**
     * 用不用远距离瞄准（{@link BuilderAim}，瞄准距离等于施工距离）：铺面、竖墙、沿面用，范围都长在点中处附近，远处也看得清铺到哪。
     * 直线不用：它从锚点沿玩家自己的朝向往前延伸，本意是从脚下或眼前的方块往前修桥、铺路；锚点挪到几十格外，整条线都朝远离玩家的方向伸出去，
     * 看不清终点在哪，所以直线照旧只认原版交互距离内点中的方块，点不中就铺脚下。
     */
    public boolean longAim() {
        return this == PLANE || this == WALL || this == FACE;
    }

    /** 单按魔杖键切到的下一个形状 */
    public BuilderShape next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    @Nullable
    public static BuilderShape byId(String id) {
        for (BuilderShape shape : VALUES) {
            if (shape.id.equals(id)) {
                return shape;
            }
        }
        return null;
    }

    public static BuilderShape byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : POINTS;
    }
}
