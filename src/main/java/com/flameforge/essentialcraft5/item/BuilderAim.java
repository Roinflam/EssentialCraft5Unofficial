package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 远距离瞄准（tools/plan/builder_upgrade.md §2.2「远距离瞄准」）。形状是 {@link BuilderShape#longAim} 的（铺面、竖墙、沿面）时，
 * 瞄准距离和这根魔杖的施工距离一样远（{@link #distance(ItemStack)}：档位的施工距离，装远域时再加，不超过 {@code general.builder.max_aim_distance}），
 * 准星看得到的地方只要开得了工就有虚影：
 * <ol>
 *   <li>客户端每刻自己做一条射线（{@link #trace(Player, ItemStack)}），原版准星没点中东西时用它点中的格子和面画虚影、算准星下提示；</li>
 *   <li>右键时原版走的是「对空气用物品」（远处的方块不在原版交互距离内），客户端把这一下拦下来，先照原版用物品时的做法补发一次位置朝向，
 *       再发操作包 {@code AIM_START}（格子 + 面）；</li>
 *   <li>服务端 {@link #start} 校验包、魔杖、形状、物品右键开工的间隔（{@link BuilderJobs#requestThrottled}，至少隔 6 刻）、区块已加载，
 *       用玩家在服务端的位置朝向自己再做一条射线复核（{@link #verify}），认了才按客户端报的格子和面交给 {@link BuilderJobs#request}
 *       （大单 / 缺料照旧先确认，施工距离、领地、每格规则一点不变）；不认就在动作栏提示「看不清那一面」。</li>
 * </ol>
 * 视线方向两端都按 {@link #look}（身体的俯仰和偏航，同原版物品自己做射线的算法）算，不用 {@code getViewVector}：
 * 服务端的生物视线取头部朝向，玩家的头部朝向要等下一刻才跟上刚收到的朝向。
 * <p>复核「认」的条件（{@link #judge}）：服务端射线点中了方块；客户端报的格子有轮廓；报的就是服务端点中的那一格时面也要相同；
 * 报的是挨着的一格（每个轴上相差都不超过 {@value #TOLERANCE}，两端位置朝向差一点点时会打到这里）时，那一面要朝着眼睛（按方块轮廓所在的平面算），
 * 而且从眼睛到那一面中心的连线最先碰到的就是这一格的这一面（墙后那一格、被盖住的面都不认）。认了以后锚点最多比服务端点中的偏一格。
 * <p>射线只读已加载区块里的方块：经过没加载的区块就停下、当作没点中（服务端报「没加载」）；算方块轮廓时读到没加载区块里的邻格一律当空气，
 * 不会为了瞄准去加载或生成区块。服务端每个开工包最多做两条射线（复核一条，报的是挨着的一格时再加一条遮挡检查），每条不超过瞄准距离；
 * 操作包本身限频（每 20 刻 10 个），间隔不够 6 刻的在射线之前就丢掉。
 */
public final class BuilderAim {

    /** 两端点中的格子在每个轴上最多差这么多格还算认 */
    public static final int TOLERANCE = 1;

    private static final AABB FULL_CUBE = new AABB(0D, 0D, 0D, 1D, 1D, 1D);

    /** 服务端复核的结论 */
    public enum Verdict {
        /** 认了（开没开成工看 {@link Outcome#start}） */
        OK,
        /** 包里的东西不合法（面不在 0~5、坐标出了建筑高度或世界边界），或者玩家死了 / 旁观：不提示，直接丢 */
        BAD_PACKET,
        /** 那只手里不是创世神之杖 */
        NOT_WAND,
        /** 现在的形状不用远距离瞄准 */
        WRONG_SHAPE,
        /** 离上一次物品右键开工不到 6 刻（按住右键的连发） */
        THROTTLED,
        /** 报的格子离眼睛比瞄准距离还远 */
        TOO_FAR,
        /** 报的格子或视线经过的区块没加载 */
        NOT_LOADED,
        /** 服务端射线对不上：「看不清那一面」 */
        MISMATCH
    }

    /** {@link #start} 的结果：verdict 是 OK 时 start 是交给 {@link BuilderJobs#request} 的结果，否则为 null */
    public record Outcome(Verdict verdict, @Nullable BuilderJobs.StartResult start) {
        public boolean accepted() {
            return verdict == Verdict.OK;
        }
    }

    /**
     * 一条射线的结果。hit 为 null 是没点中（瞄准距离内全是空气，或经过了没加载的区块：unloaded 为 true）
     */
    public record Trace(@Nullable BuilderShapes.Hit hit, boolean unloaded) {
        static final Trace MISS = new Trace(null, false);
        static final Trace UNLOADED = new Trace(null, true);
    }

    private BuilderAim() {
    }

    /** 这根魔杖现在用不用远距离瞄准：创世神之杖、形状是 {@link BuilderShape#longAim} */
    public static boolean applies(ItemStack wand) {
        return BuilderMode.of(wand) != null && BuilderSelection.shape(wand).longAim();
    }

    /**
     * 这根魔杖的瞄准距离（格）：施工距离（{@link BuilderTier.Stats#maxReach}，已含远域加的那部分），不超过 {@code max_aim_distance}。
     * 瞄得再远也开不了工（开工要求整个范围在施工距离内），所以两者取一样的数，虚影出现的地方就是右键能开工的地方。
     */
    public static int distance(ItemStack wand) {
        return Math.max(1, Math.min(BuilderTier.stats(wand).maxReach(), ECBuilder.Config.aimDistance()));
    }

    // ------------------------------------------------------------------
    // 射线（两端共用）
    // ------------------------------------------------------------------

    /** 俯仰 xRot、偏航 yRot（度）对应的单位视线方向，算法同原版物品自己做射线（Item#getPlayerPOVHitResult），两端用同一张三角函数表 */
    public static Vec3 look(float xRot, float yRot) {
        float yaw = -yRot * Mth.DEG_TO_RAD - Mth.PI;
        float pitch = -xRot * Mth.DEG_TO_RAD;
        float cosPitch = -Mth.cos(pitch);
        return new Vec3(Mth.sin(yaw) * cosPitch, Mth.sin(pitch), Mth.cos(yaw) * cosPitch);
    }

    /**
     * 从玩家眼睛沿视线（{@link #look}）做一条 {@link #distance(ItemStack)} 格的射线：客户端画虚影、拦右键，服务端复核都用它。
     * 按这一刻的位置和朝向算，和右键前补发给服务端的一致。
     */
    public static Trace trace(Player player, ItemStack wand) {
        Vec3 eye = player.getEyePosition();
        Vec3 to = eye.add(look(player.getXRot(), player.getYRot()).scale(distance(wand)));
        Level level = player.level();
        return trace(level, eye, to, CollisionContext.of(player), pos -> ECLevels.isFullyLoaded(level, pos));
    }

    /**
     * 从 from 到 to 的射线（GameTest 直接测）：和原版准星一样只看方块的轮廓（OUTLINE），不看液体、实体；按经过的顺序一格格查，
     * 第一格点中的就是结果。出了建筑高度的格子当空气；loaded 不认的格子不去读，直接停下返回 {@link Trace#UNLOADED}；
     * 算轮廓时方块要是去读 loaded 不认的邻格，读到的是空气。
     */
    public static Trace trace(BlockGetter level, Vec3 from, Vec3 to, CollisionContext context, Predicate<BlockPos> loaded) {
        BlockGetter view = new LoadedView(level, loaded);
        return BlockGetter.traverseBlocks(from, to, context, (ctx, pos) -> {
            if (level.isOutsideBuildHeight(pos)) {
                return null;
            }
            if (!loaded.test(pos)) {
                return Trace.UNLOADED;
            }
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getShape(view, pos, ctx);
            BlockHitResult hit = view.clipWithInteractionOverride(from, to, pos, shape, state);
            return hit == null ? null : new Trace(new BuilderShapes.Hit(hit.getBlockPos().immutable(), hit.getDirection()), false);
        }, ctx -> Trace.MISS);
    }

    /** 只露出已加载格子的世界视图：别的格子是空气、没有方块实体（方块算轮廓时读邻格不会加载区块） */
    private record LoadedView(BlockGetter level, Predicate<BlockPos> loaded) implements BlockGetter {
        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return loaded.test(pos) ? level.getBlockEntity(pos) : null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return loaded.test(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return loaded.test(pos) ? level.getFluidState(pos) : Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }

    // ------------------------------------------------------------------
    // 服务端
    // ------------------------------------------------------------------

    /**
     * 收到「按这个点中结果开工」（{@code C2SBuilderActionPacket.AIM_START}，GameTest 直接调）：校验、复核，认了就开工。
     * 物品右键开工的间隔（6 刻）和二次确认都照旧：间隔不够的这一下忽略、不提示；要确认的第一下只提示，60 刻内同一单再来一次才开工。
     *
     * @param face 面的序号（{@link Direction#get3DDataValue}），不在 0~5 的丢掉
     */
    public static Outcome start(ServerPlayer player, InteractionHand hand, BlockPos claimed, int face) {
        Verdict verdict = verify(player, player.getItemInHand(hand), claimed, face, true);
        if (verdict != Verdict.OK) {
            if (verdict == Verdict.MISMATCH) {
                MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.aim_unclear"));
            } else if (verdict == Verdict.NOT_LOADED) {
                MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.not_loaded"));
            }
            return new Outcome(verdict, null);
        }
        BuilderJobs.StartResult result = BuilderJobs.request(player, hand, new BuilderShapes.Hit(claimed.immutable(), Direction.from3DDataValue(face)), true);
        return new Outcome(Verdict.OK, result);
    }

    /**
     * 只复核、不开工（{@link #start} 用它；GameTest 测各种拒的情形时 throttle 传 false，不占物品右键开工的间隔）。
     * 先做便宜的检查（包、手里的东西、形状、离眼睛多远），再看间隔、区块，最后才做射线。
     */
    public static Verdict verify(ServerPlayer player, ItemStack wand, BlockPos claimed, int face, boolean throttle) {
        if (!ECProtection.isRealPlayer(player) || !player.isAlive() || player.isSpectator()) {
            return Verdict.BAD_PACKET;
        }
        ServerLevel level = player.serverLevel();
        if (face < 0 || face > 5 || level.isOutsideBuildHeight(claimed) || !level.getWorldBorder().isWithinBounds(claimed)) {
            return Verdict.BAD_PACKET;
        }
        if (BuilderMode.of(wand) == null) {
            return Verdict.NOT_WAND;
        }
        if (!BuilderSelection.shape(wand).longAim()) {
            return Verdict.WRONG_SHAPE;
        }
        Vec3 eye = player.getEyePosition();
        double limit = distance(wand) + 2D;   // 格子中心到点中处最多差半个对角线，再加 1 格容差
        if (eye.distanceToSqr(Vec3.atCenterOf(claimed)) > limit * limit) {
            return Verdict.TOO_FAR;
        }
        if (throttle && BuilderJobs.requestThrottled(player)) {
            return Verdict.THROTTLED;
        }
        if (!ECLevels.isFullyLoaded(level, claimed)) {
            return Verdict.NOT_LOADED;
        }
        Predicate<BlockPos> loaded = pos -> ECLevels.isFullyLoaded(level, pos);
        CollisionContext context = CollisionContext.of(player);
        Vec3 to = eye.add(look(player.getXRot(), player.getYRot()).scale(distance(wand)));
        Trace server = trace(level, eye, to, context, loaded);
        if (server.unloaded()) {
            return Verdict.NOT_LOADED;
        }
        Direction side = Direction.from3DDataValue(face);
        VoxelShape shape = level.getBlockState(claimed).getShape(new LoadedView(level, loaded), claimed, context);
        AABB bounds = shape.isEmpty() ? null : shape.bounds();
        Verdict verdict = judge(server.hit(), claimed, side, eye, bounds);
        if (verdict != Verdict.OK || server.hit() == null || server.hit().pos().equals(claimed)) {
            return verdict;
        }
        // 报的是挨着的一格：从眼睛看那一面的中心，最先碰到的要是这一格的这一面（墙后那一格、被盖住的面不认）
        Trace sight = trace(level, eye, faceTarget(claimed, side, bounds), context, loaded);
        if (sight.unloaded()) {
            return Verdict.NOT_LOADED;
        }
        BuilderShapes.Hit seen = sight.hit();
        return seen != null && seen.pos().equals(claimed) && seen.face() == side ? Verdict.OK : Verdict.MISMATCH;
    }

    /**
     * 复核的判定（纯函数）：server 是服务端射线点中的（null = 没点中），claimed / face 是客户端报的，eye 是服务端的眼睛位置，
     * bounds 是报的格子的轮廓外框（格子内的坐标，null = 没有轮廓）。认返回 {@link Verdict#OK}，否则 {@link Verdict#MISMATCH}。
     * 挨着那一格的遮挡检查要读世界，在 {@link #verify} 里。
     */
    public static Verdict judge(@Nullable BuilderShapes.Hit server, BlockPos claimed, Direction face, Vec3 eye, @Nullable AABB bounds) {
        if (server == null || bounds == null) {
            return Verdict.MISMATCH;
        }
        if (server.pos().equals(claimed)) {
            return server.face() == face ? Verdict.OK : Verdict.MISMATCH;
        }
        return near(server.pos(), claimed) && faces(eye, claimed, face, bounds) ? Verdict.OK : Verdict.MISMATCH;
    }

    /** 两格在每个轴上相差都不超过 {@value #TOLERANCE}（同一格或紧挨着的 26 格之一） */
    public static boolean near(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= TOLERANCE && Math.abs(a.getY() - b.getY()) <= TOLERANCE
                && Math.abs(a.getZ() - b.getZ()) <= TOLERANCE;
    }

    /** 整格方块的 face 那一面朝着 eye（同 {@link #faces(Vec3, BlockPos, Direction, AABB)} 传整格外框） */
    public static boolean faces(Vec3 eye, BlockPos pos, Direction face) {
        return faces(eye, pos, face, FULL_CUBE);
    }

    /**
     * pos 处轮廓外框为 bounds（格子内坐标）的方块，face 那一面朝着 eye：眼睛在那一面所在平面的外侧（地毯、半砖这类薄方块按它自己的顶面算）
     */
    public static boolean faces(Vec3 eye, BlockPos pos, Direction face, AABB bounds) {
        Direction.Axis axis = face.getAxis();
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double plane = pos.get(axis) + (positive ? bounds.max(axis) : bounds.min(axis));
        double outward = (eye.get(axis) - plane) * (positive ? 1D : -1D);
        return outward > 1.0E-6D;
    }

    /** 那一面中心往格子里挪一点点的点（遮挡检查的连线终点）：按轮廓外框算，薄方块也落在轮廓里 */
    static Vec3 faceTarget(BlockPos pos, Direction face, @Nullable AABB bounds) {
        AABB box = bounds == null ? FULL_CUBE : bounds;
        Direction.Axis axis = face.getAxis();
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double inset = Math.min(0.01D, (box.max(axis) - box.min(axis)) / 2D);
        double onAxis = positive ? box.max(axis) - inset : box.min(axis) + inset;
        double x = axis == Direction.Axis.X ? onAxis : (box.minX + box.maxX) / 2D;
        double y = axis == Direction.Axis.Y ? onAxis : (box.minY + box.maxY) / 2D;
        double z = axis == Direction.Axis.Z ? onAxis : (box.minZ + box.maxZ) / 2D;
        return new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
    }
}
