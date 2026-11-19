package com.flameforge.essentialcraft5.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * 一次施工要走的格子：外框 + 锚点 + 按顺序排好的格子（{@link BuilderShapes}、{@link BuilderSurface}）。两点形状不另存格子，
 * 顺序照旧按 {@link BuilderSelection#cellAt}（cells 为 null）；别的形状开工时排一次序存成 long 数组（{@code BlockPos.asLong}）。
 * <p>
 * 存的顺序是放置类用的「从下往上」（一层一层、层内从锚点列一圈圈往外）；跨几层的范围（竖墙、带台阶的沿面）给清除类另备一份
 * 「从上往下」：层倒过来，层内的先后不变（tools/plan/builder_upgrade.md §1.3）。
 */
public final class BuilderArea {

    private final BoundingBox box;
    private final BlockPos anchor;
    @Nullable
    private final long[] cells;
    /** 从上往下的顺序；只有一层时就是 {@link #cells} 本身 */
    @Nullable
    private final long[] topDown;

    BuilderArea(BoundingBox box, BlockPos anchor, @Nullable long[] cells) {
        this.box = box;
        this.anchor = anchor;
        this.cells = cells;
        this.topDown = cells == null || box.minY() == box.maxY() ? cells : topDown(cells);
    }

    /** 两点围成的长方体 */
    public static BuilderArea box(BoundingBox box) {
        return new BuilderArea(box, new BlockPos(box.minX(), box.minY(), box.minZ()), null);
    }

    public BoundingBox box() {
        return box;
    }

    public BlockPos anchor() {
        return anchor;
    }

    /** 要走几格 */
    public int size() {
        return cells == null ? BuilderSelection.cellCount(box) : cells.length;
    }

    /** 第 index 格（0 起）；bottomUp 为 false（清除类）时一层一层从上往下 */
    public BlockPos cellAt(int index, boolean bottomUp) {
        if (cells == null) {
            return BuilderSelection.cellAt(box, index, bottomUp);
        }
        return BlockPos.of(bottomUp ? cells[index] : topDown[index]);
    }

    /** 是不是两点形状的长方体 */
    public boolean isBox() {
        return cells == null;
    }

    /**
     * 按 bottomUp 的顺序复制一份格子（{@code BlockPos.asLong}）；两点形状返回 null（格子按 {@link #cellAt} 现算）。
     * 面板蓝图画「这一下要动哪些格子」可以直接用它。
     */
    @Nullable
    public long[] cells(boolean bottomUp) {
        if (cells == null) {
            return null;
        }
        return (bottomUp ? cells : topDown).clone();
    }

    /** 已按「层从下往上」排好的格子改成层从上往下，层内先后不变（计数排序，稳定） */
    private static long[] topDown(long[] sorted) {
        int n = sorted.length;
        long[] out = new long[n];
        int i = 0;
        // 输入里同一层是连着的一段：从最后一段（最高层）起依次搬到输出前面
        int[] starts = new int[n + 1];
        int layers = 0;
        while (i < n) {
            starts[layers++] = i;
            int y = BlockPos.getY(sorted[i]);
            while (i < n && BlockPos.getY(sorted[i]) == y) {
                i++;
            }
        }
        starts[layers] = n;
        int at = 0;
        for (int layer = layers - 1; layer >= 0; layer--) {
            int from = starts[layer];
            int to = starts[layer + 1];
            System.arraycopy(sorted, from, out, at, to - from);
            at += to - from;
        }
        return out;
    }
}
