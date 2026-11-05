package com.flameforge.essentialcraft5.hoanna.portal;

import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import net.minecraft.BlockUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 过海纳尔之门（第六批 6a M2，batch6a.md §5.2）：{@link HoannaPortalTracker} 到点时在玩家自己的刻末尾调 {@link #travel}。
 * <ol>
 *   <li>目标维度：在海纳尔就回主世界，否则去海纳尔（海纳尔之门只能在主世界换出来、在海纳尔补出来，D10）。</li>
 *   <li>入口矩形 + 原版 {@code PortalShape.getRelativePosition} 算玩家在门里的相对位置（入口坐标自己记，不用受保护的 {@code portalEntrancePos}）。</li>
 *   <li>目标点 = 目标世界边界夹住的玩家坐标（coordinate_scale 1，两边同坐标）。</li>
 *   <li>{@link HoannaPortalPlacer#find} 找门；找不到时，去海纳尔就 {@link HoannaPortalPlacer#build}，<b>回主世界不建门</b>，
 *       落到重生点（只在重生维度就是主世界时用，见 {@link #fallbackArrival}）或世界出生点，并提示。</li>
 *   <li>有门时用原版 {@code PortalShape.createPortalInfo}（带防卡墙落点）；设冷却；{@code changeDimension(目标, new HoannaTeleporter(落点))}。</li>
 * </ol>
 * <b>落点必须在 {@code changeDimension} 之前算好</b>：{@code ServerPlayer.changeDimension}（L720~）先发重生包、把玩家从原世界移除，然后才问
 * {@code getPortalInfo}；那时再返回 null，玩家会卡在没有世界的状态。所以 {@link HoannaTeleporter} 只装一个算好的落点、永不返回 null。
 * 建门的候选点全被领地拒绝时取消这次传送、不扣任何东西，这名玩家的门冷却至少 {@value #RETRY_TICKS} 刻（站在门里时冷却不减，得先走出来）。
 * {@code EntityTravelToDimensionEvent} 被别的模组取消时，海纳尔那边可能已经建好了门——接受。
 * <p>
 * 矩阵阶的门（第十三批 13a）：去海纳尔时先问 {@code MatrixRank.mayEnterHoanna}，阶不够就不传（什么都不算、不建门），动作栏写要几阶、现在几阶；
 * 等待计时照常走完才判，站着的人每过一个等待时间看到一次。回主世界不判。
 */
public final class HoannaTeleport {

    /** 建门全部失败后，多久以后才会再试（门冷却，刻） */
    public static final int RETRY_TICKS = 100;

    /**
     * 算好的一次传送。
     *
     * @param info     落点（位置、速度、朝向）
     * @param portal   落到的那扇门（回主世界找不到门、落到重生点时为 null）
     * @param fellBack 回主世界时附近没有门，落到了重生点或世界出生点
     */
    public record Plan(PortalInfo info, @Nullable BlockUtil.FoundRectangle portal, boolean fellBack) {
    }

    private HoannaTeleport() {
    }

    /**
     * 从 from 过门去哪个世界：海纳尔 → 主世界，其它 → 海纳尔。海纳尔被数据包删掉、或目标就是 from 自己（测试覆盖时）返回 null。
     */
    @Nullable
    public static ServerLevel destination(ServerLevel from) {
        MinecraftServer server = from.getServer();
        ServerLevel dest = ECHoanna.isHoanna(from) ? server.overworld() : ECHoanna.level(server);
        return dest == from ? null : dest;
    }

    /** 目标点：目标世界边界夹住的实体坐标（两边同坐标） */
    public static BlockPos target(ServerLevel dest, Entity entity) {
        return dest.getWorldBorder().clampToBounds(entity.getX(), entity.getY(), entity.getZ());
    }

    /**
     * 预加载（batch6a §4.3）：刚站进门、计时开始时给目标处加一张 PORTAL 票（半径 3、300 刻自动过期，同原版过下界门后的票），
     * 区块在等待的 80 刻里由工作线程异步生成，到点时找门 / 建门基本不在主线程同步生成区块。调用方负责「每人每冷却周期一次」和开关。
     */
    public static void preload(ServerPlayer player) {
        ServerLevel dest = destination(player.serverLevel());
        if (dest == null) {
            return;
        }
        BlockPos pos = target(dest, player);
        dest.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), HoannaPortalPlacer.TICKET_RADIUS, pos);
    }

    /**
     * 过门：算落点（找门 / 建门 / 回重生点）→ 设冷却 → 换维度。返回有没有换成功。
     * 只在服务端主线程、玩家自己的刻里调（{@link HoannaPortalTracker} 的 PlayerTickEvent END）。
     */
    public static boolean travel(ServerPlayer player, BlockPos entrance) {
        // 开关关掉只挡从主世界进去：人在海纳尔时照样放行回程（回程只找不建）
        if (!ECHoannaPortal.Config.portalEnabledIn(player.level())) {
            hint(player, "message.essentialcraft5.hoanna.disabled");
            return false;
        }
        if (!player.isAlive() || player.isPassenger() || player.isVehicle() || !player.canChangeDimensions()) {
            return false;
        }
        ServerLevel from = player.serverLevel();
        ServerLevel dest = destination(from);
        if (dest == null) {
            hint(player, "message.essentialcraft5.hoanna.disabled");
            return false;
        }
        boolean toHoanna = !ECHoanna.isHoanna(from);
        // 矩阵阶的门（第十三批 13a，batch13.md §2.5）：站满等待时间时判，阶不够不传、不建门，动作栏写要几阶；只挡进，回程不判
        if (toHoanna && !MatrixRank.mayEnterHoanna(player)) {
            player.displayClientMessage(MatrixRank.gateMessage(player, MatrixRank.Gate.HOANNA_PORTAL), true);
            return false;
        }
        Plan plan = plan(player, from, entrance, dest, toHoanna);
        if (plan == null) {
            hint(player, "message.essentialcraft5.hoanna.no_arrival_site");
            player.setPortalCooldown(Math.max(player.getPortalCooldown(), RETRY_TICKS));
            return false;
        }
        // 冷却要在 changeDimension 之前设：重生包里带着冷却值，客户端靠它知道这 15 秒里门不会再响应
        player.setPortalCooldown(ECHoannaPortal.Config.cooldownTicks());
        DimensionChange testHook = TEST_DIMENSION_CHANGES.isEmpty() ? null : TEST_DIMENSION_CHANGES.get(player.getUUID());
        boolean moved = testHook != null
                ? testHook.change(player, dest, plan)
                : player.changeDimension(dest, new HoannaTeleporter(plan.info())) != null;
        if (!moved) {
            return false;
        }
        if (plan.fellBack()) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.hoanna.returned_to_spawn")
                    .withStyle(ChatFormatting.DARK_AQUA), false);
        }
        return true;
    }

    /**
     * 算落点（不换维度、不设冷却）：找门，找不到时 build 为 true 就建门、为 false 就回重生点 / 出生点。
     * 返回 null 表示要建门但所有候选点都被拒（世界没有变化）。测试直接调它。
     *
     * @param entrance 玩家站的门格（不是门格时按原版的默认：X 朝向、正中）
     * @param build    找不到门时要不要建（去海纳尔 true；回主世界 false：<b>只找不建</b>）
     */
    @Nullable
    public static Plan plan(ServerPlayer player, ServerLevel from, BlockPos entrance, ServerLevel dest, boolean build) {
        BlockState entranceState = from.getBlockState(entrance);
        Direction.Axis axis;
        Vec3 relative;
        if (entranceState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            axis = entranceState.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            BlockUtil.FoundRectangle entry = BlockUtil.getLargestRectangleAround(entrance, axis, HoannaPortalShape.MAX_WIDTH,
                    Direction.Axis.Y, HoannaPortalShape.MAX_HEIGHT, at -> from.getBlockState(at) == entranceState);
            Vec3 inside = PortalShape.getRelativePosition(entry, axis, player.position(), player.getDimensions(player.getPose()));
            // 同 LivingEntity.getRelativePortalPosition：活体落地时纵深归零，站在门的正中面上
            relative = new Vec3(inside.x, inside.y, 0.0D);
        } else {
            axis = Direction.Axis.X;
            relative = new Vec3(0.5D, 0.0D, 0.0D);
        }
        BlockPos target = target(dest, player);
        Optional<BlockUtil.FoundRectangle> portal = HoannaPortalPlacer.find(dest, target, ECHoannaPortal.Config.searchRadius());
        if (portal.isEmpty() && build) {
            portal = HoannaPortalPlacer.build(player, dest, target, axis, ECHoannaPortal.Config.buildAttempts());
        }
        if (portal.isPresent()) {
            PortalInfo info = PortalShape.createPortalInfo(dest, portal.get(), axis, relative, player, player.getDeltaMovement(),
                    player.getYRot(), player.getXRot());
            return new Plan(info, portal.get(), false);
        }
        if (build) {
            return null;
        }
        return new Plan(fallbackArrival(player, dest), null, true);
    }

    /**
     * 回主世界找不到门时的落点（policy §1.3：往返不许在主世界自动放方块）：
     * 玩家的重生点——<b>只在重生维度就是 dest 时</b>用（床 / 强制重生点；alive = true 不扣重生锚充能；
     * 【核查补】在海纳尔睡过床、或重生锚在下界时，重生坐标属于别的维度，拿到主世界去查会落到错的地方）；
     * 床没了、不在这个维度就落到世界出生点的地表（{@link #surfaceArrival}：先加载出生点区块再读高度图，同原版末地回主世界）。
     */
    public static PortalInfo fallbackArrival(ServerPlayer player, ServerLevel dest) {
        BlockPos respawn = player.getRespawnPosition();
        if (respawn != null && dest.dimension().equals(player.getRespawnDimension())) {
            Optional<Vec3> stand = Player.findRespawnPositionAndUseSpawnBlock(dest, respawn, player.getRespawnAngle(),
                    player.isRespawnForced(), true);
            if (stand.isPresent()) {
                return new PortalInfo(stand.get(), Vec3.ZERO, player.getYRot(), player.getXRot());
            }
        }
        return new PortalInfo(surfaceArrival(dest, dest.getSharedSpawnPos()), Vec3.ZERO, player.getYRot(), player.getXRot());
    }

    /**
     * 落到 xz 这一列的地表：MOTION_BLOCKING_NO_LEAVES 最高那格之上（同原版末地回主世界），站在列的正中。
     * <b>先同步把这一列所在的区块加载到 FULL 再读高度图</b>（6a 审查 M2-1）：出生点区块不常驻时（混合端插件 {@code setKeepSpawnInMemory(false)} /
     * Multiverse 的 keepSpawnInMemory、去掉出生点区块的模组），原版 {@code Level.getHeight}（L332~345）对没加载的区块直接返回最低高度，
     * 人会落进 y = −64 的基岩里窒息。读完给落点加一张 PORTAL 票（半径 {@value HoannaPortalPlacer#TICKET_RADIUS}、300 刻自动过期，同找门 / 建门），
     * 撑到玩家到达。只在服务端主线程调。
     */
    public static Vec3 surfaceArrival(ServerLevel dest, BlockPos xz) {
        LevelChunk chunk = dest.getChunk(SectionPos.blockToSectionCoord(xz.getX()), SectionPos.blockToSectionCoord(xz.getZ()));
        // ChunkAccess.getHeight 返回最高那格（首个可用高度 − 1），+1 即站立高度，同 Level.getHeight
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xz.getX(), xz.getZ()) + 1;
        BlockPos at = new BlockPos(xz.getX(), y, xz.getZ());
        dest.getChunkSource().addRegionTicket(TicketType.PORTAL, chunk.getPos(), HoannaPortalPlacer.TICKET_RADIUS, at);
        return new Vec3(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
    }

    // ------------------------------------------------------------------
    // 给测试看的
    // ------------------------------------------------------------------

    /** {@link #setTestDimensionChange} 的钩子：代替真的换维度，返回 true 当作换成功 */
    @FunctionalInterface
    public interface DimensionChange {
        boolean change(ServerPlayer player, ServerLevel dest, Plan plan);
    }

    /** 按玩家 UUID 登记的测试钩子（只有 GameTest 往里放；正常游戏永远是空表，travel 里只多一次 isEmpty） */
    private static final Map<UUID, DimensionChange> TEST_DIMENSION_CHANGES = new HashMap<>();

    /**
     * 只给 GameTest：这名玩家过门时，{@link #travel} 算好落点、设好冷却后不真的 {@code changeDimension}，改调 hook（GameTest 的测试玩家不在世界里、
     * 不在玩家列表里，不能真换维度）。按 UUID 登记，测试中途失败没摘掉也只影响那个随机 UUID。hook 为 null 时摘掉。
     */
    public static void setTestDimensionChange(UUID player, @Nullable DimensionChange hook) {
        if (hook == null) {
            TEST_DIMENSION_CHANGES.remove(player);
        } else {
            TEST_DIMENSION_CHANGES.put(player, hook);
        }
    }

    /** 快捷栏上方一句提示 */
    static void hint(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.DARK_AQUA), true);
    }
}
