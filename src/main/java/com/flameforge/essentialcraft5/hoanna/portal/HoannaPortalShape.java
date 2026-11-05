package com.flameforge.essentialcraft5.hoanna.portal;

import com.flameforge.essentialcraft5.block.HoannaPortalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 海纳尔之门的门框判定（第六批 6a M2，batch6a.md §5.2），照原版 {@code PortalShape}（1.20.1）写：框用 {@code isPortalFrame}（Forge 默认黑曜石），
 * 内框宽 2~21、高 3~21，从给的格子往下、往「左」找左下角，再量宽、量高、查顶上一排。
 * <p>
 * 和原版的区别：「空」和「门」<b>同时认 {@code nether_portal} 和 {@code hoanna_portal}</b>，并分开数两种门格。
 * 原因：石板换门被领地拒绝时，{@code ECProtection.captureGuarded} 按快照倒序用 {@code UPDATE_CLIENTS} 还原，还原一格就触发邻格的 {@code updateShape}，
 * 这时两种门格短暂混在一起；原版 {@code PortalShape.isEmpty}（L160~161）只认下界门，海纳尔之门的 {@code updateShape} 若照搬原版判定会把还没还原的格子
 * 当成「框坏了」，连锁塌成空气。混着也算完整，就不会塌。
 * <p>
 * 用法：
 * <ul>
 *   <li>海纳尔之门的 {@code updateShape}：{@code new HoannaPortalShape(level, pos, axis).isComplete()}；</li>
 *   <li>风之石板在主世界换门：{@link #findNetherPortal}（整扇都是下界门）；</li>
 *   <li>风之石板在海纳尔补门（D10 A）、在主世界点了没点燃的框：{@link #findEmptyShape}（框里一格门都没有）。</li>
 * </ul>
 */
public final class HoannaPortalShape {

    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;

    private final LevelAccessor level;
    private final Direction.Axis axis;
    private final Direction rightDir;
    @Nullable
    private BlockPos bottomLeft;
    private int height;
    private final int width;
    /** 内框里的门格（两种都算） */
    private int portalCells;
    private int netherCells;
    private int hoannaCells;

    public HoannaPortalShape(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        this.level = level;
        this.axis = axis;
        this.rightDir = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        this.bottomLeft = calculateBottomLeft(pos);
        if (this.bottomLeft == null) {
            this.bottomLeft = pos;
            this.width = 1;
            this.height = 1;
        } else {
            this.width = calculateWidth();
            if (this.width > 0) {
                this.height = calculateHeight();
            }
        }
    }

    // ------------------------------------------------------------------
    // 找框
    // ------------------------------------------------------------------

    /** 先按 preferred 朝向找，不成再换另一个水平朝向（同原版 {@code PortalShape.findPortalShape}） */
    public static Optional<HoannaPortalShape> find(LevelAccessor level, BlockPos pos, Predicate<HoannaPortalShape> accept, Direction.Axis preferred) {
        Direction.Axis first = preferred.isHorizontal() ? preferred : Direction.Axis.X;
        HoannaPortalShape shape = new HoannaPortalShape(level, pos, first);
        if (accept.test(shape)) {
            return Optional.of(shape);
        }
        HoannaPortalShape other = new HoannaPortalShape(level, pos, first == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        return accept.test(other) ? Optional.of(other) : Optional.empty();
    }

    /** 搭好的空框（内框全是空气 / 火，一格门都没有） */
    public static Optional<HoannaPortalShape> findEmptyShape(LevelAccessor level, BlockPos pos, Direction.Axis preferred) {
        return find(level, pos, HoannaPortalShape::isEmptyFrame, preferred);
    }

    /** 点燃的完整下界传送门（内框每一格都是下界门；朝向就是门格自己的朝向） */
    public static Optional<HoannaPortalShape> findNetherPortal(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        HoannaPortalShape shape = new HoannaPortalShape(level, pos, axis);
        return shape.isFullNetherPortal() ? Optional.of(shape) : Optional.empty();
    }

    /** 门格：下界门或海纳尔之门 */
    public static boolean isPortal(BlockState state) {
        return state.is(Blocks.NETHER_PORTAL) || state.getBlock() instanceof HoannaPortalBlock;
    }

    /** 内框里算「空」的：空气、火、两种门格 */
    public static boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || isPortal(state);
    }

    // ------------------------------------------------------------------
    // 结果
    // ------------------------------------------------------------------

    public boolean isValid() {
        return bottomLeft != null && width >= MIN_WIDTH && width <= MAX_WIDTH && height >= MIN_HEIGHT && height <= MAX_HEIGHT;
    }

    /** 框完整、内框全是门格（两种门混着也算） */
    public boolean isComplete() {
        return isValid() && portalCells == width * height;
    }

    /** 框完整、内框全是下界门 */
    public boolean isFullNetherPortal() {
        return isValid() && netherCells == width * height;
    }

    /** 框完整、内框一格门都没有 */
    public boolean isEmptyFrame() {
        return isValid() && portalCells == 0;
    }

    public Direction.Axis axis() {
        return axis;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int netherCells() {
        return netherCells;
    }

    public int hoannaCells() {
        return hoannaCells;
    }

    /** 左下角的内框格（{@link #isValid()} 为 false 时是传进来的格子） */
    public BlockPos bottomLeft() {
        return bottomLeft;
    }

    /** 内框全部格子（宽 × 高，≤ 441），从左下角起一列一列往上 */
    public List<BlockPos> cells() {
        List<BlockPos> cells = new ArrayList<>(Math.max(0, width * height));
        if (!isValid()) {
            return cells;
        }
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                cells.add(bottomLeft.relative(rightDir, i).above(j));
            }
        }
        return cells;
    }

    // ------------------------------------------------------------------
    // 量框（照原版 PortalShape L81~160，只改 isEmpty 和计数）
    // ------------------------------------------------------------------

    private boolean isFrame(BlockState state, BlockPos pos) {
        return state.isPortalFrame(level, pos);
    }

    @Nullable
    private BlockPos calculateBottomLeft(BlockPos pos) {
        int floor = Math.max(level.getMinBuildHeight(), pos.getY() - MAX_HEIGHT);
        while (pos.getY() > floor && isEmpty(level.getBlockState(pos.below()))) {
            pos = pos.below();
        }
        Direction left = rightDir.getOpposite();
        int distance = getDistanceUntilEdgeAboveFrame(pos, left) - 1;
        return distance < 0 ? null : pos.relative(left, distance);
    }

    private int calculateWidth() {
        int distance = getDistanceUntilEdgeAboveFrame(bottomLeft, rightDir);
        return distance >= MIN_WIDTH && distance <= MAX_WIDTH ? distance : 0;
    }

    private int getDistanceUntilEdgeAboveFrame(BlockPos from, Direction direction) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= MAX_WIDTH; i++) {
            cursor.set(from).move(direction, i);
            BlockState state = level.getBlockState(cursor);
            if (!isEmpty(state)) {
                if (isFrame(state, cursor)) {
                    return i;
                }
                break;
            }
            BlockState below = level.getBlockState(cursor.move(Direction.DOWN));
            if (!isFrame(below, cursor)) {
                break;
            }
        }
        return 0;
    }

    private int calculateHeight() {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int distance = getDistanceUntilTop(cursor);
        return distance >= MIN_HEIGHT && distance <= MAX_HEIGHT && hasTopFrame(cursor, distance) ? distance : 0;
    }

    private boolean hasTopFrame(BlockPos.MutableBlockPos cursor, int distance) {
        for (int i = 0; i < width; i++) {
            cursor.set(bottomLeft).move(Direction.UP, distance).move(rightDir, i);
            if (!isFrame(level.getBlockState(cursor), cursor)) {
                return false;
            }
        }
        return true;
    }

    private int getDistanceUntilTop(BlockPos.MutableBlockPos cursor) {
        for (int i = 0; i < MAX_HEIGHT; i++) {
            cursor.set(bottomLeft).move(Direction.UP, i).move(rightDir, -1);
            if (!isFrame(level.getBlockState(cursor), cursor)) {
                return i;
            }
            cursor.set(bottomLeft).move(Direction.UP, i).move(rightDir, width);
            if (!isFrame(level.getBlockState(cursor), cursor)) {
                return i;
            }
            for (int j = 0; j < width; j++) {
                cursor.set(bottomLeft).move(Direction.UP, i).move(rightDir, j);
                BlockState state = level.getBlockState(cursor);
                if (!isEmpty(state)) {
                    return i;
                }
                if (state.is(Blocks.NETHER_PORTAL)) {
                    portalCells++;
                    netherCells++;
                } else if (state.getBlock() instanceof HoannaPortalBlock) {
                    portalCells++;
                    hoannaCells++;
                }
            }
        }
        return MAX_HEIGHT;
    }
}
