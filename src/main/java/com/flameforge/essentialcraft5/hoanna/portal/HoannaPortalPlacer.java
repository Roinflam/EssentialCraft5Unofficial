package com.flameforge.essentialcraft5.hoanna.portal;

import com.flameforge.essentialcraft5.block.HoannaPortalBlock;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 找门、建门（第六批 6a M2，batch6a.md §5.2、§4.3）。
 * <ul>
 *   <li><b>找门</b> {@link #find}：兴趣点 {@code essentialcraft5:hoanna_portal}，照原版 {@code PortalForcer.findPortalAround}（L42~65）：
 *       {@code PoiManager.ensureLoadedAndValid} 只把区块读到 EMPTY，再 {@code getInSquare}（正方形半径，默认 128），按距离、再按高度取最近的，
 *       找到就给它加一张 PORTAL 票（半径 3、300 刻自动过期）。原作每趟逐格扫 ±128 × 全高，约 1 690 万次读方块。</li>
 *   <li><b>建门</b> {@link #build}（只在去海纳尔时用；<b>回主世界只找不建</b>）：扫法照原版 {@code PortalForcer.createPortal}（L67~161）——
 *       以目标为中心螺旋 33 × 33 列、每列从 MOTION_BLOCKING 往下找「脚下实心、上面 4 格能替换」的位置；排序分两档（见 {@link #candidates}）：
 *       先是每列最上面那段空气里的点（门脚下是地表、头顶能见天），再是洞里的点；每档里前后 ±1 纵深都能放框的「好点」排前面、
 *       只有 0 纵深的「次好点」排后面，取前 {@code 次数 − 1} 个，最后加一个强制平台（挖 3 × 2 × 4、铺黑曜石地板；目标这一列顶上是水时铺在水面上，
 *       否则 y 夹在 70 ~ 逻辑高度 − 10）。附近一个露天的点都没有、目标又在水上时，水面平台提到最前面。
 *       每个候选点一次 {@code ECProtection.captureGuarded}（整片一次多格放置事件、朝上），被拒换下一个；全被拒返回空，调用方取消这次传送。
 *       候选点里有方块实体（箱子之类）或挖不动的方块（基岩）的格子直接跳过，不去替换。门的朝向跟入口一致；4 × 5 黑曜石框 + 2 × 3 门。</li>
 * </ul>
 * 建门时玩家还在主世界，放置事件里的快照是海纳尔的格子（事件的世界 = 海纳尔）：Forge 端领地模组按事件所在世界查没问题
 * （按玩家所在世界查的会查到主世界同坐标，整合包里没有这类模组，只记下）；混合端转成 Bukkit 事件时，6a 审查读混合端的
 * {@code BlockEventDispatcher.onMultiPlace} 得出「方块取自快照所在的世界 = 海纳尔」，实测见混合端自检 C20。
 */
public final class HoannaPortalPlacer {

    /** 建门的搜索半径（原版 PortalForcer 的 16：33 × 33 列） */
    public static final int CREATE_RADIUS = 16;
    /** 找到 / 建好门后给门所在区块加的 PORTAL 票半径（同原版 3） */
    public static final int TICKET_RADIUS = 3;

    /** 候选点是哪一种（决定排在哪一档、要不要先铺平台） */
    public enum Site {
        /** 每列最上面那段空气：门脚下是地表，头顶能见天 */
        OPEN,
        /** 同一列更下面的空气段：洞里 */
        COVERED,
        /** 强制平台（同原版）：y 夹在 70 ~ 逻辑高度 − 10 */
        PLATFORM,
        /** 强制平台铺在目标这一列的水面上：地板替换水面那层水，门立在地板上 */
        WATER_PLATFORM
    }

    /**
     * 一个建门候选点。
     *
     * @param pos     门左下角那一格（门格 = pos + 沿门宽方向 0~1 × 向上 0~2）
     * @param site    哪一种（平台两种会先挖出 3 × 2 × 4 的空间、铺黑曜石地板）
     * @param good    前后 ±1 纵深也能放框（原版优先挑的那种）
     * @param distSqr 离目标的距离平方（含 y）
     */
    public record Candidate(BlockPos pos, Site site, boolean good, double distSqr) {

        /** 强制平台（普通的或水面上的）：摆门前先挖空间、铺地板 */
        public boolean platform() {
            return site == Site.PLATFORM || site == Site.WATER_PLATFORM;
        }
    }

    private HoannaPortalPlacer() {
    }

    // ==================================================================
    // 找门
    // ==================================================================

    /**
     * 在 level 里找离 target 最近的海纳尔之门（正方形半径 radius，世界边界外的不算），返回整扇门的矩形（同原版 {@code findPortalAround}）。
     * 找到时给门所在区块加 PORTAL 票。只认兴趣点，不逐格扫。
     */
    public static Optional<BlockUtil.FoundRectangle> find(ServerLevel level, BlockPos target, int radius) {
        PoiManager poi = level.getPoiManager();
        poi.ensureLoadedAndValid(level, target, radius);
        WorldBorder border = level.getWorldBorder();
        Optional<PoiRecord> nearest = poi.getInSquare(holder -> holder.is(ECHoannaPortal.PORTAL_POI), target, radius, PoiManager.Occupancy.ANY)
                .filter(record -> border.isWithinBounds(record.getPos()))
                .sorted(Comparator.<PoiRecord>comparingDouble(record -> record.getPos().distSqr(target))
                        .thenComparingInt(record -> record.getPos().getY()))
                .filter(record -> level.getBlockState(record.getPos()).getBlock() instanceof HoannaPortalBlock)
                .findFirst();
        return nearest.map(record -> {
            BlockPos pos = record.getPos();
            level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), TICKET_RADIUS, pos);
            BlockState state = level.getBlockState(pos);
            return BlockUtil.getLargestRectangleAround(pos, state.getValue(HoannaPortalBlock.AXIS), HoannaPortalShape.MAX_WIDTH,
                    Direction.Axis.Y, HoannaPortalShape.MAX_HEIGHT, at -> level.getBlockState(at) == state);
        });
    }

    // ==================================================================
    // 建门
    // ==================================================================

    /**
     * 以玩家身份在 level 建一扇海纳尔之门：按 {@link #candidates} 的顺序一个个试，每个候选点整片一次 {@code captureGuarded}，
     * 放行就把门格改成 stable（登记兴趣点）、加 PORTAL 票并返回门的矩形；被拒换下一个。全部被拒（或一个候选点都没有）返回空，世界不变。
     */
    public static Optional<BlockUtil.FoundRectangle> build(ServerPlayer player, ServerLevel level, BlockPos target, Direction.Axis axis, int attempts) {
        for (Candidate candidate : candidates(level, target, axis, attempts)) {
            ECProtection.CaptureResult result = ECProtection.captureGuarded(player, level, Direction.UP, List.of(), () -> {
                place(level, candidate.pos(), axis, candidate.platform(), false);
                return true;
            });
            boolean placed = result.is(ECProtection.CaptureResult.Outcome.ALLOWED)
                    || result.is(ECProtection.CaptureResult.Outcome.NO_CHANGE) && level.getBlockState(candidate.pos()).getBlock() instanceof HoannaPortalBlock;
            if (placed) {
                stabilize(level, portalCells(candidate.pos(), axis));
                BlockPos pos = candidate.pos();
                level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), TICKET_RADIUS, pos);
                return Optional.of(new BlockUtil.FoundRectangle(pos, 2, 3));
            }
        }
        return Optional.empty();
    }

    /**
     * 建门的候选点，按要试的顺序，共 {@code attempts} 个：普通点 {@code attempts − 1} 个，最后一个是强制平台（放得下时）；
     * attempts 为 1 时只取最好的一个（一个普通点都没有时才是平台）。普通点分两档，第一档试完才轮到第二档：
     * <ol>
     *   <li><b>露天</b>（{@link Site#OPEN}）：每列最上面那段空气（从 MOTION_BLOCKING 高度图那一格往下），门脚下是地表、头顶能见天。
     *       一列至多一个，高度由地形决定，所以按<b>水平</b>距离排；</li>
     *   <li><b>洞里</b>（{@link Site#COVERED}）：同一列更下面的空气段，按含 y 的距离排（同原版）。</li>
     * </ol>
     * 每档里「好点」在前、「次好点」在后。为什么分档：目标 y 取的是玩家在主世界的 y，海纳尔的地形和主世界没有关系，
     * 像原版那样只按含 y 的距离排，常常挑中和这个 y 接近的洞，门建进地下。
     * <p>
     * 强制平台：目标这一列顶上是水时铺在水面这一层（{@link Site#WATER_PLATFORM}），否则同原版（{@link Site#PLATFORM}）。
     * <b>露天一个都没有、目标这一列又是水面时，水面平台提到最前面</b>，其后才是洞里的点：海上附近没有干地时，
     * 洞里的点多半在海底下面（6a 往返实测：水面 y 62、半径 16 内没有干地，门建进了 y −34 / −36 的洞），宁可在水面上铺一小块平台。
     * <p>
     * 扫描范围和每列的判定同原版（33 × 33 列、每列从高度图往下），判「好点」只在前面凑不够时才往后判，开销不比以前多。
     * 目标周围的区块没加载时会同步加载（同原版建门；开着预加载时早就好了）。
     */
    public static List<Candidate> candidates(ServerLevel level, BlockPos target, Direction.Axis axis, int attempts) {
        int limit = Math.max(1, attempts);
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis == Direction.Axis.Z ? Direction.Axis.Z : Direction.Axis.X);
        Direction.Axis portalAxis = along.getAxis();
        WorldBorder border = level.getWorldBorder();
        int top = Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight()) - 1;
        ensureChunks(level, target);

        // 1. 螺旋扫 33 × 33 列，收集「0 纵深能放框」的点（同原版 createPortal 的扫法），分成露天、洞里两档
        List<Candidate> open = new ArrayList<>();
        List<Candidate> covered = new ArrayList<>();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (BlockPos.MutableBlockPos column : BlockPos.spiralAround(target, CREATE_RADIUS, Direction.EAST, Direction.SOUTH)) {
            int x = column.getX();
            int z = column.getZ();
            if (!border.isWithinBounds(x, z) || !border.isWithinBounds(x + along.getStepX(), z + along.getStepZ())) {
                continue;
            }
            int sky = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            int surface = Math.min(top, sky);
            for (int y = surface; y >= level.getMinBuildHeight(); --y) {
                probe.set(x, y, z);
                if (!canPortalReplace(level, probe)) {
                    continue;
                }
                int airTop = y;
                while (y > level.getMinBuildHeight() && canPortalReplace(level, probe.move(Direction.DOWN))) {
                    --y;
                }
                if (y + 4 > top) {
                    continue;
                }
                int run = airTop - y;
                if (run > 0 && run < 3) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, y, z);
                if (canHostFrame(level, pos, along, 0) && safeToReplace(level, frameCells(pos, portalAxis))) {
                    // 从高度图那一格开始的那一段就是这一列最上面的空气（高度图被逻辑高度截断时没有这一段）
                    boolean isOpen = airTop == sky;
                    (isOpen ? open : covered).add(new Candidate(pos, isOpen ? Site.OPEN : Site.COVERED, false, target.distSqr(pos)));
                }
            }
        }
        // 稳定排序：同距离保持螺旋顺序
        open.sort(Comparator.comparingDouble(candidate -> horizontalDistSqr(target, candidate.pos())));
        covered.sort(Comparator.comparingDouble(Candidate::distSqr));
        Candidate platform = platform(level, target, along, top, border);

        List<Candidate> result = new ArrayList<>(limit);
        if (open.isEmpty() && platform != null && platform.site() == Site.WATER_PLATFORM) {
            // 2′. 附近没有露天的干地、目标在水上：先试水面平台，再试洞里（总数不变）
            result.add(platform);
            pick(level, covered, along, limit, result);
            return result;
        }

        // 2. 先露天、后洞里，各自从近到远判「好点」，凑够就停（原版：有好点取最近的好点，否则取最近的次好点）
        int normalSlots = limit >= 2 ? limit - 1 : 1;
        pick(level, open, along, normalSlots, result);
        pick(level, covered, along, normalSlots, result);
        if (limit == 1 && !result.isEmpty()) {
            return result;
        }

        // 3. 强制平台放最后
        if (platform != null) {
            result.add(platform);
        }
        return result;
    }

    /**
     * 从一档里挑点加进 result，直到 result 有 upTo 个：从近到远判「好点」，好点凑够空位就停（次好点用不上了）；
     * 凑不够时整档都判过，好点在前、次好点在后。sorted 已经按这一档的距离排好。
     */
    private static void pick(ServerLevel level, List<Candidate> sorted, Direction along, int upTo, List<Candidate> result) {
        int room = upTo - result.size();
        if (room <= 0) {
            return;
        }
        List<Candidate> good = new ArrayList<>();
        List<Candidate> ok = new ArrayList<>();
        for (Candidate candidate : sorted) {
            if (good.size() >= room) {
                break;
            }
            if (canHostFrame(level, candidate.pos(), along, -1) && canHostFrame(level, candidate.pos(), along, 1)) {
                good.add(new Candidate(candidate.pos(), candidate.site(), true, candidate.distSqr()));
            } else {
                ok.add(candidate);
            }
        }
        for (Candidate candidate : good) {
            if (result.size() >= upTo) {
                return;
            }
            result.add(candidate);
        }
        for (Candidate candidate : ok) {
            if (result.size() >= upTo) {
                return;
            }
            result.add(candidate);
        }
    }

    /**
     * 强制平台（原版 L117~135：挖 3 × 2 × 4、铺黑曜石地板，门立在地板上）放在目标这一列：
     * <ul>
     *   <li>这一列顶上是水（高度图下面那格是水）：地板铺在水面这一层（替换水面那层水），门脚下就是水面 → {@link Site#WATER_PLATFORM}；</li>
     *   <li>否则（或者水面上放不下）同原版：y 夹在 [max(最低 + 1, 70), 逻辑顶 − 9] → {@link Site#PLATFORM}。</li>
     * </ul>
     * 出了世界边界、或者要动的格子里有不能替换的（方块实体、挖不动的）就不要，返回 null。
     */
    @Nullable
    private static Candidate platform(ServerLevel level, BlockPos target, Direction along, int top, WorldBorder border) {
        int x = target.getX();
        int z = target.getZ();
        int sky = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (sky - 1 >= level.getMinBuildHeight() && sky + 4 <= top
                && level.getFluidState(new BlockPos(x, sky - 1, z)).is(FluidTags.WATER)) {
            BlockPos pos = new BlockPos(x, sky, z);
            if (platformFits(level, pos, along, border)) {
                return new Candidate(pos, Site.WATER_PLATFORM, false, target.distSqr(pos));
            }
        }
        int platformMin = Math.max(level.getMinBuildHeight() + 1, 70);
        int platformMax = top - 9;
        if (platformMax < platformMin) {
            return null;
        }
        BlockPos pos = new BlockPos(x, Mth.clamp(target.getY(), platformMin, platformMax), z);
        return platformFits(level, pos, along, border) ? new Candidate(pos, Site.PLATFORM, false, target.distSqr(pos)) : null;
    }

    /** 平台放得下：门的两列都在世界边界内，平台和框要动的格子都能放心替换 */
    private static boolean platformFits(ServerLevel level, BlockPos pos, Direction along, WorldBorder border) {
        if (!border.isWithinBounds(pos) || !border.isWithinBounds(pos.relative(along))) {
            return false;
        }
        List<BlockPos> cells = new ArrayList<>(platformCells(pos, along.getAxis()));
        cells.addAll(frameCells(pos, along.getAxis()));
        return safeToReplace(level, cells);
    }

    private static double horizontalDistSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * 摆一扇门：（强制平台时先挖 3 × 2 × 4、铺黑曜石地板）→ 4 × 5 黑曜石框（flag 3）→ 2 × 3 门（flag 18，不更新邻居形状）。
     * 不发任何事件：{@link #build} 在 captureGuarded 里调它；测试和展示厅可以直接调（stable 传 true 就登记兴趣点）。
     */
    public static void place(Level level, BlockPos base, Direction.Axis axis, boolean platform, boolean stable) {
        Direction.Axis portalAxis = axis == Direction.Axis.Z ? Direction.Axis.Z : Direction.Axis.X;
        if (platform) {
            Direction along = Direction.get(Direction.AxisDirection.POSITIVE, portalAxis);
            Direction depth = along.getClockWise();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int d = -1; d < 2; ++d) {
                for (int w = 0; w < 2; ++w) {
                    for (int h = -1; h < 3; ++h) {
                        if (d == 0 && h >= 0) {
                            continue;   // 门格下面直接放门，不先挖成空气（免得同一格在捕获里记两次）
                        }
                        cursor.setWithOffset(base, w * along.getStepX() + d * depth.getStepX(), h, w * along.getStepZ() + d * depth.getStepZ());
                        level.setBlock(cursor, h < 0 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        for (BlockPos cell : frameCells(base, portalAxis)) {
            level.setBlock(cell, Blocks.OBSIDIAN.defaultBlockState(), 3);
        }
        BlockState portal = ECHoannaPortal.HOANNA_PORTAL.get().portalState(portalAxis, stable);
        for (BlockPos cell : portalCells(base, portalAxis)) {
            level.setBlock(cell, portal, 18);
        }
    }

    /**
     * 把这些格子里的 {@code stable=false} 门格改成 {@code stable=true}（登记兴趣点）。在 {@code useOn} 的外层放置捕获里调也行：
     * 暂停捕获执行，不会再发一次放置事件（这几格的权限已经在同一次操作的多格放置事件里问过了）。
     */
    public static void stabilize(Level level, Collection<BlockPos> cells) {
        ECProtection.withoutCapture(level, () -> {
            for (BlockPos cell : cells) {
                BlockState state = level.getBlockState(cell);
                if (state.getBlock() instanceof HoannaPortalBlock && !state.getValue(HoannaPortalBlock.STABLE)) {
                    level.setBlock(cell, state.setValue(HoannaPortalBlock.STABLE, true), 18);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // 格子
    // ------------------------------------------------------------------

    /** 2 × 3 门格（沿门宽方向 0~1、向上 0~2） */
    public static List<BlockPos> portalCells(BlockPos base, Direction.Axis axis) {
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis == Direction.Axis.Z ? Direction.Axis.Z : Direction.Axis.X);
        List<BlockPos> cells = new ArrayList<>(6);
        for (int w = 0; w < 2; ++w) {
            for (int h = 0; h < 3; ++h) {
                cells.add(base.relative(along, w).above(h));
            }
        }
        return cells;
    }

    /** 4 × 5 黑曜石框（沿门宽方向 −1~2、向上 −1~3 的外圈，14 格；最下一排替换脚下的地面） */
    public static List<BlockPos> frameCells(BlockPos base, Direction.Axis axis) {
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis == Direction.Axis.Z ? Direction.Axis.Z : Direction.Axis.X);
        List<BlockPos> cells = new ArrayList<>(14);
        for (int w = -1; w < 3; ++w) {
            for (int h = -1; h < 4; ++h) {
                if (w == -1 || w == 2 || h == -1 || h == 3) {
                    cells.add(base.relative(along, w).above(h));
                }
            }
        }
        return cells;
    }

    /** 强制平台挖出来的 3 × 2 × 4（含黑曜石地板） */
    public static List<BlockPos> platformCells(BlockPos base, Direction.Axis axis) {
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis == Direction.Axis.Z ? Direction.Axis.Z : Direction.Axis.X);
        Direction depth = along.getClockWise();
        List<BlockPos> cells = new ArrayList<>(24);
        for (int d = -1; d < 2; ++d) {
            for (int w = 0; w < 2; ++w) {
                for (int h = -1; h < 3; ++h) {
                    cells.add(base.relative(along, w).relative(depth, d).above(h));
                }
            }
        }
        return cells;
    }

    /** 这个候选点会改动的全部格子（平台 + 框 + 门） */
    public static List<BlockPos> footprint(Candidate candidate, Direction.Axis axis) {
        List<BlockPos> cells = new ArrayList<>();
        if (candidate.platform()) {
            cells.addAll(platformCells(candidate.pos(), axis));
        }
        cells.addAll(frameCells(candidate.pos(), axis));
        cells.addAll(portalCells(candidate.pos(), axis));
        return cells;
    }

    // ------------------------------------------------------------------
    // 判定（照原版 PortalForcer L163~185）
    // ------------------------------------------------------------------

    /** 同原版 canPortalReplaceBlock：能被替换、没有液体 */
    private static boolean canPortalReplace(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** 同原版 canHostFrame：offset 是垂直于门面的纵深；最下一排要实心，上面 4 排能替换 */
    private static boolean canHostFrame(ServerLevel level, BlockPos base, Direction along, int offset) {
        Direction depth = along.getClockWise();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int w = -1; w < 3; ++w) {
            for (int h = -1; h < 4; ++h) {
                cursor.setWithOffset(base, along.getStepX() * w + depth.getStepX() * offset, h, along.getStepZ() * w + depth.getStepZ() * offset);
                if (h < 0 && !level.getBlockState(cursor).isSolid()) {
                    return false;
                }
                if (h >= 0 && !canPortalReplace(level, cursor)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 这些格子都能放心替换：空气，或者没有方块实体、挖得动（不去替换箱子、基岩；原版建门不查这个） */
    private static boolean safeToReplace(ServerLevel level, Collection<BlockPos> cells) {
        for (BlockPos cell : cells) {
            BlockState state = level.getBlockState(cell);
            if (state.isAir()) {
                continue;
            }
            if (state.hasBlockEntity() || state.getDestroySpeed(level, cell) < 0F) {
                return false;
            }
        }
        return true;
    }

    /** 目标 ±16 格覆盖的区块同步加载到 FULL（高度图要用；开着预加载时早就好了）。没加载时 Level.getHeight 会返回最低高度 */
    private static void ensureChunks(ServerLevel level, BlockPos target) {
        int minX = (target.getX() - CREATE_RADIUS) >> 4;
        int maxX = (target.getX() + CREATE_RADIUS) >> 4;
        int minZ = (target.getZ() - CREATE_RADIUS) >> 4;
        int maxZ = (target.getZ() + CREATE_RADIUS) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }
}
