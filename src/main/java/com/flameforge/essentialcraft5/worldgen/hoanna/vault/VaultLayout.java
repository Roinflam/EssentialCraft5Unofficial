package com.flameforge.essentialcraft5.worldgen.hoanna.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * 封印宝库的布局（batch13.md §6.4；HS §2.4.1），纯计算：给起点（地面那一列）和地面高度，算出四个拼件的包围盒和谜题方块的位置。
 * 朝向固定（不旋转、不镜像）：宝库室总在前厅北边，祭座、锁孔、核心、封印门之间的相对位置是写死的常量，
 * 谜题方块靠这些常量互相找（{@link #CORE_FROM_KEYHOLE} 等），不存坐标、不扫方块。<b>上线后这些偏移不能改</b>（已生成的宝库按旧偏移摆的）。
 * <pre>
 *   北 (−z)
 *   ┌───────────┐   宝库室 9 × 6 × 9（外框），东西各一个侧龛，中央宝库核心
 *   │   核  心   │
 *   └───┬封印┬───┘   封印门 3 × 3：宝库室南墙上九块封印石
 *   ┌───┴───┴───┐   前厅 11 × 7 × 11：中央锁孔，东南西北 3 格处各一座祭座
 *   │  祭 锁 祭  │
 *   │       ╔═╗ │   螺旋楼梯井 3 × 3（外墙 5 × 5）在前厅东南角正上方，起点就是井的中心那一列
 *   └───────╚═╝─┘
 *           平台     地面 3 × 3 平台 + 1 × 1 × 5 先辈碑，在井口南边
 *   南 (+z)
 * </pre>
 * 设起点 (ox, oz)、地面 g（起点那一列的第一格空位）、前厅地板 F = g − 深度（深度 = clamp(g − 56, 8, 20)，再不低于世界底 + 1）、
 * 前厅中心 (cx, cz) = (ox − 3, oz − 3)：
 * <ul>
 *   <li>前厅 x cx−5~cx+5、z cz−5~cz+5、y F~F+6（地板、墙、天花板各一层，里面 9 × 5 × 9）；锁孔 (cx, F+1, cz)，祭座在锁孔东南西北 3 格；北墙正中开 3 × 3 的门洞；</li>
 *   <li>宝库室 x cx−4~cx+4、z cz−14~cz−6、y F~F+5，侧龛把包围盒在东西各扩 2 格；核心 (cx, F+1, cz−10)，封印石 x cx−1~cx+1、y F+1~F+3、z cz−6；</li>
 *   <li>井 x ox−2~ox+2、z oz−2~oz+2、y F+1~g+3：中间一根柱子，外圈 8 格按顺时针每格降一级台阶，第 0 级在井口南边那格 (ox, g−1, oz+1)，最后一级落在前厅地上；</li>
 *   <li>平台 x ox−1~ox+1、z oz+3~oz+5，平台面 y g−1，碑 (ox, g~g+4, oz+4)，平台下往下补柱子最多 5 格。</li>
 * </ul>
 * 整座水平 13 × 23（≤ 24 × 24），竖直从 F 到 g+4。
 */
public final class VaultLayout {

    /** 深度下限 / 上限、算深度的基准（深度 = 地面 − 56，尽量留在海平面 − 10 以上，少和矿井穿插） */
    public static final int MIN_DEPTH = 8;
    public static final int MAX_DEPTH = 20;
    public static final int DEPTH_BASE = 56;
    /** 起点 → 前厅中心 */
    public static final int ANTE_OFFSET = -3;
    /** 前厅外框半宽（11 = 2 × 5 + 1）、外框高 7 */
    public static final int ANTE_HALF = 5;
    public static final int ANTE_HEIGHT = 7;
    /** 祭座离锁孔几格 */
    public static final int PEDESTAL_DISTANCE = 3;
    /** 锁孔 → 宝库核心 */
    public static final BlockPos CORE_FROM_KEYHOLE = new BlockPos(0, 0, -10);
    /** 宝库核心 → 封印门正中最下面那块（门 3 宽 3 高，向上 0~2、左右 −1~+1） */
    public static final BlockPos DOOR_FROM_CORE = new BlockPos(0, 0, 4);
    /** 宝库室外框：相对前厅中心的 z 范围、半宽、高 */
    public static final int CHAMBER_NORTH = -14;
    public static final int CHAMBER_SOUTH = -6;
    public static final int CHAMBER_HALF = 4;
    public static final int CHAMBER_HEIGHT = 6;
    /** 侧龛在宝库室东西墙外再占几格 */
    public static final int ALCOVE_DEPTH = 2;
    /** 先辈碑多高、碑相对起点的 z */
    public static final int STELE_HEIGHT = 5;
    public static final int STELE_DZ = 4;
    /** 先辈碑 → 宝库核心那一列（水平）；竖直方向从碑底往下找 {@link #STELE_PROBE_DEPTH} 格 */
    public static final int STELE_TO_CORE_DX = ANTE_OFFSET;
    public static final int STELE_TO_CORE_DZ = ANTE_OFFSET + CORE_FROM_KEYHOLE.getZ() - STELE_DZ;
    public static final int STELE_PROBE_DEPTH = MAX_DEPTH + 1;
    /** 平台下最多补几格柱子（包围盒留够这么深） */
    public static final int PLATFORM_FILL = 5;
    /** 井口以上多清几格空气 */
    public static final int SHAFT_CLEAR = 3;
    /** 整座的水平上限（每边） */
    public static final int MAX_SPAN = 24;

    /** 螺旋楼梯外圈 8 格（相对井中心），顺时针从南边那格开始；相邻两格只差一个水平方向 */
    private static final int[][] RING = {{0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}};

    private VaultLayout() {
    }

    /** 地面 g 时的深度：clamp(g − 56, 8, 20) */
    public static int depthFor(int ground) {
        return Mth.clamp(ground - DEPTH_BASE, MIN_DEPTH, MAX_DEPTH);
    }

    /**
     * 起点 (ox, oz)、地面 ground（起点那一列 {@code WORLD_SURFACE_WG} 的第一格空位）时的布局；minBuildY 是世界底
     * （超平坦之类的浅世界里前厅地板不低于它 + 1，深度会小于 8）。
     */
    public static Plan plan(int ox, int ground, int oz, int minBuildY) {
        int floor = Math.max(ground - depthFor(ground), minBuildY + 1);
        return new Plan(ox, ground, oz, floor);
    }

    /** 螺旋楼梯第 step 级（0 = 井口）在外圈哪一格：相对井中心的 {dx, dz} */
    public static int[] ringCell(int step) {
        return RING[Math.floorMod(step, RING.length)];
    }

    /** 第 step 级台阶的朝向（台阶高的一边、往上走的方向 = 朝上一级那格）；第 0 级朝南（平台那边） */
    public static Direction stairFacing(int step) {
        if (step <= 0) {
            return Direction.SOUTH;
        }
        int[] here = ringCell(step);
        int[] prev = ringCell(step - 1);
        return Direction.fromDelta(prev[0] - here[0], 0, prev[1] - here[1]);
    }

    /**
     * 一座宝库的全部位置。
     *
     * @param ox     起点 x（井中心）
     * @param ground 地面（井口那一层是 ground − 1）
     * @param oz     起点 z
     * @param floorY 前厅、宝库室的地板
     */
    public record Plan(int ox, int ground, int oz, int floorY) {

        public int cx() {
            return ox + ANTE_OFFSET;
        }

        public int cz() {
            return oz + ANTE_OFFSET;
        }

        public int depth() {
            return ground - floorY;
        }

        /** 楼梯级数：从井口 ground − 1 一路到前厅地上 floorY + 1 */
        public int stairSteps() {
            return Math.max(1, ground - 1 - floorY);
        }

        public BlockPos keyhole() {
            return new BlockPos(cx(), floorY + 1, cz());
        }

        public BlockPos pedestal(Direction side) {
            return keyhole().relative(side, PEDESTAL_DISTANCE);
        }

        public BlockPos core() {
            return keyhole().offset(CORE_FROM_KEYHOLE);
        }

        /** 先辈碑第 i 块（0 = 最下面） */
        public BlockPos stele(int i) {
            return new BlockPos(ox, ground + i, oz + STELE_DZ);
        }

        /** 封印门九块，从下往上、从西往东 */
        public List<BlockPos> doorCells() {
            return VaultLayout.doorCells(core());
        }

        public BoundingBox antechamberBox() {
            return new BoundingBox(cx() - ANTE_HALF, floorY, cz() - ANTE_HALF, cx() + ANTE_HALF, floorY + ANTE_HEIGHT - 1, cz() + ANTE_HALF);
        }

        /** 前厅里面（不含地板、墙、天花板）：答对那一刻站在这里的人进解开名单 */
        public BoundingBox antechamberInterior() {
            return VaultLayout.antechamberInterior(keyhole());
        }

        public BoundingBox chamberBox() {
            return new BoundingBox(cx() - CHAMBER_HALF - ALCOVE_DEPTH, floorY, cz() + CHAMBER_NORTH,
                    cx() + CHAMBER_HALF + ALCOVE_DEPTH, floorY + CHAMBER_HEIGHT - 1, cz() + CHAMBER_SOUTH);
        }

        public BoundingBox chamberInterior() {
            return VaultLayout.chamberInterior(core());
        }

        public BoundingBox shaftBox() {
            return new BoundingBox(ox - 2, floorY + 1, oz - 2, ox + 2, ground + SHAFT_CLEAR, oz + 2);
        }

        public BoundingBox steleBox() {
            return new BoundingBox(ox - 1, ground - 1 - PLATFORM_FILL, oz + STELE_DZ - 1, ox + 1, ground + STELE_HEIGHT - 1, oz + STELE_DZ + 1);
        }

        /** 四个拼件合起来的包围盒 */
        public BoundingBox bounds() {
            BoundingBox box = antechamberBox();
            box.encapsulate(chamberBox());
            box.encapsulate(shaftBox());
            box.encapsulate(steleBox());
            return box;
        }
    }

    /** 宝库核心 → 封印门九块（从下往上、从西往东） */
    public static List<BlockPos> doorCells(BlockPos core) {
        BlockPos base = core.offset(DOOR_FROM_CORE);
        List<BlockPos> cells = new ArrayList<>(9);
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                cells.add(base.offset(dx, dy, 0));
            }
        }
        return cells;
    }

    /** 封印石 seal 可能对应的宝库核心位置（它是门上九格中的哪一格不知道，九个候选） */
    public static List<BlockPos> coreCandidatesFromSeal(BlockPos seal) {
        List<BlockPos> cells = new ArrayList<>(9);
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                cells.add(seal.offset(-dx, -dy, 0).subtract(DOOR_FROM_CORE));
            }
        }
        return cells;
    }

    /** 锁孔 → 前厅里面 */
    public static BoundingBox antechamberInterior(BlockPos keyhole) {
        int r = ANTE_HALF - 1;
        return new BoundingBox(keyhole.getX() - r, keyhole.getY(), keyhole.getZ() - r, keyhole.getX() + r, keyhole.getY() + ANTE_HEIGHT - 3, keyhole.getZ() + r);
    }

    /** 宝库核心 → 宝库室里面（不含侧龛） */
    public static BoundingBox chamberInterior(BlockPos core) {
        int r = CHAMBER_HALF - 1;
        return new BoundingBox(core.getX() - r, core.getY(), core.getZ() - r, core.getX() + r, core.getY() + CHAMBER_HEIGHT - 3, core.getZ() + r);
    }
}
