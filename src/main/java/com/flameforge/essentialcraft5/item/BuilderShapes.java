package com.flameforge.essentialcraft5.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * 铺面、直线的几何（纯函数，两端共用：客户端画虚影、算「要放」，服务端开工时用自己的数据重算一遍）。设计见 tools/plan/builder_upgrade.md §1.2、§1.3。
 * <pre>
 *                 铺面                                         直线
 * 点中顶面        上方那一层，以点中格为中心的 W × L            锚点 = 上方那格，沿玩家朝向延伸 L
 * 点中底面        下方那一层，以点中格为中心                    锚点 = 下方那格
 * 点中侧面        同高，从侧面外那格起沿面的法线往外延伸 L        锚点 = 侧面外那格，沿玩家朝向延伸
 * 没点中          脚下一层，从脚下那格沿玩家朝向延伸 L            同左
 * </pre>
 * 长 L 沿延伸方向，宽 W 横向（玩家朝向顺时针转 90° 是「右」）。偶数宽居中时多出来的那一格放在右边；
 * 以点中格为中心的偶数长，多出来的那一格放在前方（离玩家远的一边）。格子顺序：先按层，再按离锚点的切比雪夫距离一圈圈往外，
 * 再按欧氏距离平方，最后按（沿长、沿宽）的坐标，保证两端算出同一个顺序。
 * <p>
 * 上表是填充的起点。清除、替换这类作用于已有方块的模式（{@link BuilderMode#inPlace}）下，铺面点中方块时起点是点中的那格本身
 * （顶面 / 底面：点中格那一层，以它为中心；侧面：从点中格起逆着那一面的法线往里延伸 L、横向居中），不然圈到的是点中那一面外侧的空气；
 * 没点中时两类一样，都是脚下那一层。直线同理：点中方块时锚点是点中的那格本身，延伸方向照上表（沿玩家朝向）；没点中时照上表。
 * 竖墙见 {@link #wall}；沿面的范围要按世界测量，见 {@link BuilderSurface}，两者都按 {@link #ordered} 排序。
 */
public final class BuilderShapes {

    private BuilderShapes() {
    }

    /** 点中的格子和面；没点中（右键空气）时传 null */
    public record Hit(BlockPos pos, Direction face) {
    }

    /** 尺寸（宽、长），已经夹过 */
    public record Size(int width, int length) {
    }

    /**
     * 夹尺寸：宽、长各在 1 ~ maxSide，格数不超过 maxVolume（超了先缩长，长缩到 1 还超再缩宽）
     */
    public static Size clamp(int width, int length, int maxSide, int maxVolume) {
        int side = Math.max(1, maxSide);
        int w = Mth.clamp(width, 1, side);
        int l = Mth.clamp(length, 1, side);
        int volume = Math.max(1, maxVolume);
        if ((long) w * l > volume) {
            l = Math.max(1, volume / w);
            if ((long) w * l > volume) {
                w = volume;
                l = 1;
            }
        }
        return new Size(w, l);
    }

    /**
     * 脚下那一层的锚点：x、z 是玩家所在的那一列，y 站在地上时取脚下支撑的那一格（站在台阶上也对），悬空、飞着时取 floor(脚底) − 1
     */
    public static BlockPos feetAnchor(Player player) {
        BlockPos column = player.blockPosition();
        int y = player.onGround() ? player.getOnPos().getY() : Mth.floor(player.getY()) - 1;
        return new BlockPos(column.getX(), y, column.getZ());
    }

    /**
     * 铺面 / 直线的范围。facing 是玩家的水平朝向（{@code player.getDirection()}），feet 是 {@link #feetAnchor}（hit 为 null 时用）。
     * 两点形状不走这里（{@link BuilderArea#box}）。
     */
    public static BuilderArea area(BuilderShape shape, Size size, Direction facing, @Nullable Hit hit, BlockPos feet) {
        return area(shape, size, facing, hit, feet, false);
    }

    /**
     * 同上；inPlace 为 true（清除、替换这类作用于已有方块的模式，{@link BuilderMode#inPlace}）时铺面、直线、竖墙点中方块的起点是点中的那格本身，
     * 见类注释和 {@link #wall(Size, Direction, Hit, BlockPos, boolean)}。
     */
    public static BuilderArea area(BuilderShape shape, Size size, Direction facing, @Nullable Hit hit, BlockPos feet, boolean inPlace) {
        if (!shape.resizable()) {
            throw new IllegalArgumentException("这个形状不按尺寸算范围：" + shape);
        }
        if (shape == BuilderShape.WALL) {
            return wall(size, facing, hit, feet, inPlace);
        }
        Direction forward = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        // 走到这里的只有铺面和直线：清除、替换下两者点中方块时都从点中的那格本身起
        boolean onHit = inPlace;
        BlockPos anchor;
        Direction along;
        boolean centered;
        if (hit == null) {
            anchor = feet;
            along = forward;
            centered = false;
        } else if (hit.face() == Direction.UP || hit.face() == Direction.DOWN) {
            anchor = onHit ? hit.pos() : hit.pos().relative(hit.face());
            along = forward;
            centered = shape == BuilderShape.PLANE;
        } else {
            anchor = onHit ? hit.pos() : hit.pos().relative(hit.face());
            // 填充沿法线往外铺；清除、替换从点中格起往里（方块那边）长，不然往外那几排全是空气，全部替换在空气里就成了往外铺
            along = shape == BuilderShape.PLANE ? (onHit ? hit.face().getOpposite() : hit.face()) : forward;
            centered = false;
        }
        return build(anchor, along, centered, size.width(), size.length());
    }

    /** 在 anchor 所在的一层里，沿 along 走长、沿它的右手方向走宽，排好序 */
    static BuilderArea build(BlockPos anchor, Direction along, boolean centered, int width, int length) {
        Direction right = along.getClockWise();
        int aMin = centered ? -((length - 1) / 2) : 0;
        int aMax = aMin + length - 1;
        int bMin = -((width - 1) / 2);
        int bMax = bMin + width - 1;
        int count = width * length;
        long[] keys = new long[count];
        long[] cells = new long[count];
        int n = 0;
        for (int a = aMin; a <= aMax; a++) {
            for (int b = bMin; b <= bMax; b++) {
                int ring = Math.max(Math.abs(a), Math.abs(b));
                int dist = a * a + b * b;
                // 排序键：圈（≤ 255）、距离平方（< 2^17）、沿长坐标、沿宽坐标（各偏移到非负，< 2^9）
                keys[n] = ((long) ring << 40) | ((long) dist << 18) | ((long) (a + 256) << 9) | (b + 256);
                cells[n] = anchor.offset(along.getStepX() * a + right.getStepX() * b, 0, along.getStepZ() * a + right.getStepZ() * b).asLong();
                n++;
            }
        }
        Integer[] boxed = new Integer[count];
        for (int i = 0; i < count; i++) {
            boxed[i] = i;
        }
        Arrays.sort(boxed, (x, y) -> Long.compare(keys[x], keys[y]));
        long[] sorted = new long[count];
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            sorted[i] = cells[boxed[i]];
            BlockPos pos = BlockPos.of(sorted[i]);
            minX = Math.min(minX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        BoundingBox box = new BoundingBox(minX, anchor.getY(), minZ, maxX, anchor.getY(), maxZ);
        return new BuilderArea(box, anchor, sorted);
    }

    /**
     * 竖墙（size 的第二个数是高 H）。锚点：
     * <pre>
     * 点中顶面 / 侧面   那一面外侧那格，往上 H
     * 点中底面          底面下方那格，往下 H（贴着天花板往下挂，不往方块里长）
     * 没点中            玩家脚下那一格往前一格（站着的那层），往上 H
     * </pre>
     * 横向沿玩家朝向的左右展开 W，居中，偶数宽多出来的那一格在玩家右手边（同铺面）。格子顺序见 {@link #ordered}。
     * 这是填充的起点；清除、替换见 {@link #wall(Size, Direction, Hit, BlockPos, boolean)}。
     */
    public static BuilderArea wall(Size size, Direction facing, @Nullable Hit hit, BlockPos feet) {
        return wall(size, facing, hit, feet, false);
    }

    /**
     * 竖墙；inPlace 为 true（清除、替换这类作用于已有方块的模式）时，点中方块的起点是点中的那格本身：
     * 点中顶面 / 侧面从点中格往上 H，点中底面从点中格往下 H（这样对着一堵已有的墙点一下就能拆掉 / 换掉它）。
     * 点中侧面时横向沿点中的那一面展开（当作玩家正对着那一面，偶数宽多出的一格在那时的右手边），不按玩家朝向；
     * 点中顶面 / 底面、没点中时横向照旧按玩家朝向。往上长 / 往下挂的方向不变，没点中时两类一样。
     */
    public static BuilderArea wall(Size size, Direction facing, @Nullable Hit hit, BlockPos feet, boolean inPlace) {
        Direction forward = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        if (inPlace && hit != null && hit.face().getAxis().isHorizontal()) {
            // 清除、替换点中侧面：横向沿点中的那一面展开（当作正对着它），斜着看墙时也不会横穿点中格、切到墙后面的方块
            forward = hit.face().getOpposite();
        }
        Direction right = forward.getClockWise();
        int width = size.width();
        int height = size.length();
        BlockPos anchor;
        int step;
        if (hit == null) {
            anchor = feet.above().relative(forward);
            step = 1;
        } else if (hit.face() == Direction.DOWN) {
            anchor = inPlace ? hit.pos() : hit.pos().below();
            step = -1;
        } else {
            anchor = inPlace ? hit.pos() : hit.pos().relative(hit.face());
            step = 1;
        }
        int bMin = -((width - 1) / 2);
        long[] cells = new long[width * height];
        int n = 0;
        for (int dy = 0; dy < height; dy++) {
            for (int b = bMin; b < bMin + width; b++) {
                cells[n++] = anchor.offset(right.getStepX() * b, dy * step, right.getStepZ() * b).asLong();
            }
        }
        return ordered(anchor, cells);
    }

    /**
     * 任意一组格子按 §1.3 排序（竖墙、沿面用）：先按层从下往上（清除类的从上往下由 {@link BuilderArea} 另备），
     * 层内按离锚点那一列的切比雪夫距离一圈圈往外，再按水平距离平方，最后按 x、z（两端算出同一个顺序）。外框取这些格子的外接框。
     */
    static BuilderArea ordered(BlockPos anchor, long[] cells) {
        int count = cells.length;
        int[] ys = new int[count];
        int[] rings = new int[count];
        int[] dists = new int[count];
        int[] xs = new int[count];
        int[] zs = new int[count];
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            int x = BlockPos.getX(cells[i]);
            int y = BlockPos.getY(cells[i]);
            int z = BlockPos.getZ(cells[i]);
            int dx = x - anchor.getX();
            int dz = z - anchor.getZ();
            xs[i] = x;
            ys[i] = y;
            zs[i] = z;
            rings[i] = Math.max(Math.abs(dx), Math.abs(dz));
            dists[i] = dx * dx + dz * dz;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (p, q) -> {
            int c = Integer.compare(ys[p], ys[q]);
            if (c == 0) {
                c = Integer.compare(rings[p], rings[q]);
            }
            if (c == 0) {
                c = Integer.compare(dists[p], dists[q]);
            }
            if (c == 0) {
                c = Integer.compare(xs[p], xs[q]);
            }
            return c != 0 ? c : Integer.compare(zs[p], zs[q]);
        });
        long[] sorted = new long[count];
        for (int i = 0; i < count; i++) {
            sorted[i] = cells[order[i]];
        }
        BoundingBox box = count == 0 ? new BoundingBox(anchor) : new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        return new BuilderArea(box, anchor, sorted);
    }
}
