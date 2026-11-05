package com.flameforge.essentialcraft5.teleport;

import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 把一名玩家传到「某个方块的顶上」：落点检查 {@link #check} 与真正的传送 {@link #teleport}（第六批 6b，魔力传送器用，batch6b.md §4.2）。
 *
 * <h2>落点（{@link #check}）</h2>
 * 按顺序，第一个不合格的就是结果，合格时给出算好的落点（方块列的正中、脚下碰撞形状的顶面）：
 * <ol>
 *   <li>{@link Result#OUT_OF_WORLD}：脚下那格在建筑高度、水平世界范围以外，或者站立碰撞箱出了世界边界，
 *       或者在有天花板的维度（下界）里脚的高度到了逻辑高度以上（同原版紫颂果，下界的基岩顶传不上去）。
 *       头顶超出建筑高度上限不拦（原版本来就能站在最高那层方块上）；</li>
 *   <li>{@link Result#UNLOADED}：脚下那格所在区块没完全加载（{@code getChunkNow}，绝不同步加载；碰撞箱宽 0.6、站在列正中，不会跨到别的区块）；</li>
 *   <li>{@link Result#NO_FLOOR}：脚下那格没有碰撞形状（空气、水、岩浆）；落脚高度 = 它的碰撞形状顶面（台阶 0.5、栅栏 1.5 都对）。
 *       宝石点在矮草、一层雪、藤蔓这类没有碰撞、能被直接替换掉的东西上时，往下找一格，落在下面那块地上（原作直接传到绑定坐标 +1，不会失败）。
 *       碰撞形状按「不指定实体」取（细雪这类看实体的方块按不站人的形状算），结果和玩家现在站在哪儿无关；</li>
 *   <li>{@link Result#BLOCKED}：按玩家站立尺寸的碰撞箱有碰撞（方块、船、矿车这类能挡人的实体）；</li>
 *   <li>{@link Result#HAZARD}：碰撞箱碰到的格子、或者脚下那格会伤人（{@link #hurts}：岩浆、火、岩浆块、点着的营火、仙人掌、甜浆果丛、
 *       凋灵玫瑰、细雪，以及寻路钩子标成伤害的模组方块，如恐惧仙人掌）。只看这几格本身，旁边一格有仙人掌不算；</li>
 *   <li>{@link Result#DENIED} / {@link Result#PENDING}：给了玩家时才查——落脚格和脚下那格都要对他 {@link ECProtection#check} 放行
 *       （有的保护模组对空气格不处理，所以多查脚下一格；出生点保护、世界边界随 check 一起查）；有一格拒绝就是 DENIED，
 *       有一格给不出结论（本刻预算用完、区块没加载）就是 PENDING，下一刻再试，<b>不当成拒绝</b>。
 *       落脚格超出建筑高度（站在最高那层方块上）时只查脚下那格：那一格什么都放不了，问了也只会是「没加载」。</li>
 * </ol>
 * 不给玩家（界面里的预览）时按原版玩家的站立尺寸算、跳过权限。安全检查都不花探测预算，放在权限前面。
 * {@link #checkSafe} 按玩家自己的站立尺寸只做前五项、不探测领地：一次要挑很多候选落点的（回溯模块）先用它筛，再只对少数几个问权限。
 *
 * <h2>传送（{@link #teleport}）</h2>
 * <ul>
 *   <li>同维度：先发 Forge {@link EntityTeleportEvent} 基类（可取消；被取消或被改了目标都当作取消），再 {@code teleportTo}，
 *       然后核对人在落点 1 格内（混合端在 {@code connection.teleport} 里发 {@code PlayerTeleportEvent}，原因 UNKNOWN，
 *       插件取消时玩家留在原地，原版方法没有返回值，只能事后核对）；</li>
 *   <li>跨维度：{@code changeDimension(目标, new FixedTeleporter(落点))}，落点在调用前算好；返回非 null 且人已在目标世界才算成功。
 *       Forge 在里面发 {@code EntityTravelToDimensionEvent}（被取消返回 null），混合端发 {@code PlayerTeleportEvent}（原因 UNKNOWN，可取消）。</li>
 * </ul>
 * 不用跨维度的 {@code teleportTo(别的世界, …)}：原因是 UNKNOWN 时混合端不发 {@code PlayerTeleportEvent}、插件拦不住（batch6b.md §5.3）。
 * 两条路径的原因都是 UNKNOWN，按原因（下界门、命令）拦截或计冷却的插件看不到这里的传送（同海纳尔之门）；
 * 设原因要引用 Bukkit 的类，通用模组不做，主防线是 {@link #check} 里的三态权限。
 */
public final class ECTeleport {

    /** 落点检查的结果（界面状态按序号同步，只往后加） */
    public enum Result {
        OK,
        UNLOADED,
        BLOCKED,
        NO_FLOOR,
        HAZARD,
        DENIED,
        PENDING,
        OUT_OF_WORLD;

        private static final Result[] VALUES = values();

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 越界的序号（客户端拿到的旧数据）按 OK */
        public static Result byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : OK;
        }
    }

    /**
     * 一次落点检查的结论。
     *
     * @param landing 合格时的落点（脚的位置）；不合格为 null
     */
    public record Check(Result result, @Nullable Vec3 landing) {

        static Check fail(Result result) {
            return new Check(result, null);
        }

        public boolean ok() {
            return result == Result.OK;
        }
    }

    private ECTeleport() {
    }

    // ------------------------------------------------------------------
    // 落点
    // ------------------------------------------------------------------

    /**
     * 检查能不能把玩家放到 {@code target} 这格的顶上（见类注释的顺序；点在草、一层雪上时是它下面那格）。只在服务端主线程调，不加载区块。
     *
     * @param player 要传的玩家；null 表示只看落点安全（界面预览），不查权限
     * @param caller 探测预算按它算（传送器传自己）；null 时按玩家算
     */
    public static Check check(@Nullable ServerPlayer player, ServerLevel dest, BlockPos target, @Nullable Object caller) {
        return check(player, dest, target, caller, true);
    }

    /**
     * 只看落点安全（同 {@link #check} 的前五项），碰撞箱按这名玩家的站立尺寸算，不发任何权限探测、不花预算。
     * 结论是 OK 时再用 {@link #check} 问权限（它会把安全检查再做一遍，都是只读的）。
     */
    public static Check checkSafe(@Nullable ServerPlayer player, ServerLevel dest, BlockPos target) {
        return check(player, dest, target, null, false);
    }

    private static Check check(@Nullable ServerPlayer player, ServerLevel dest, BlockPos target, @Nullable Object caller, boolean probeClaims) {
        if (!dest.isInWorldBounds(target)) {
            return Check.fail(Result.OUT_OF_WORLD);
        }
        if (!ECLevels.isFullyLoaded(dest, target)) {
            return Check.fail(Result.UNLOADED);
        }
        BlockPos floor = target;
        BlockState floorState = dest.getBlockState(floor);
        VoxelShape shape = floorState.getCollisionShape(dest, floor, CollisionContext.empty());
        if (shape.isEmpty() && isThinCover(floorState) && dest.isInWorldBounds(floor.below())) {
            // 点在草、一层雪上：落在下面那块地上（只往下找一格；同一列，区块已经确认加载了）
            floor = floor.below();
            floorState = dest.getBlockState(floor);
            shape = floorState.getCollisionShape(dest, floor, CollisionContext.empty());
        }
        if (shape.isEmpty()) {
            return Check.fail(Result.NO_FLOOR);
        }
        Vec3 landing = new Vec3(floor.getX() + 0.5D, floor.getY() + shape.max(Direction.Axis.Y), floor.getZ() + 0.5D);
        AABB box = standingBox(player, landing);
        if (!dest.getWorldBorder().isWithinBounds(box)) {
            return Check.fail(Result.OUT_OF_WORLD);
        }
        BlockPos feet = BlockPos.containing(landing);
        if (aboveCeiling(dest, feet)) {
            return Check.fail(Result.OUT_OF_WORLD);
        }
        // 不传实体：带实体的碰撞上下文会按玩家「现在」的高度判断脚手架、细雪这类方块，人还在别处（甚至别的维度），结论没有意义
        if (!dest.noCollision(null, box)) {
            return Check.fail(Result.BLOCKED);
        }
        // 岩浆块顶面正好在 1.0，碰撞箱从它上面一格算起，所以脚下那格单独看一次
        if (hazardous(dest, box) || hurts(dest, floor, floorState)) {
            return Check.fail(Result.HAZARD);
        }
        if (probeClaims && player != null) {
            // 站在最高那层方块上时落脚格在建筑高度以外：那一格什么都放不了，只看脚下那格
            ECProtection.Verdict feetVerdict = dest.isOutsideBuildHeight(feet) ? ECProtection.Verdict.ALLOW
                    : ECProtection.check(player, dest, feet, caller);
            if (feetVerdict == ECProtection.Verdict.DENY) {
                return Check.fail(Result.DENIED);
            }
            ECProtection.Verdict floorVerdict = feet.equals(floor) ? feetVerdict : ECProtection.check(player, dest, floor, caller);
            if (floorVerdict == ECProtection.Verdict.DENY) {
                return Check.fail(Result.DENIED);
            }
            if (feetVerdict == ECProtection.Verdict.UNKNOWN || floorVerdict == ECProtection.Verdict.UNKNOWN) {
                return Check.fail(Result.PENDING);
            }
        }
        return new Check(Result.OK, landing);
    }

    /** 没有碰撞、能被直接替换掉的薄东西（矮草、蕨、一层雪、藤蔓、火……）；空气和带流体的（水、海草）不算 */
    private static boolean isThinCover(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && state.canBeReplaced();
    }

    /**
     * 有天花板的维度（下界、带顶的模组维度）里，脚的高度不能到逻辑高度以上：原版紫颂果把落点夹在 {@code minBuildHeight + logicalHeight - 1} 以下，
     * 下界门也只在逻辑高度以内找位置。主世界、海纳尔没有天花板，不受影响（底层基岩照样能当落点）。
     */
    private static boolean aboveCeiling(ServerLevel level, BlockPos feet) {
        return level.dimensionType().hasCeiling() && feet.getY() >= level.getMinBuildHeight() + level.getLogicalHeight();
    }

    /** 站立姿势的碰撞箱（没有玩家时用原版玩家的尺寸 0.6 × 1.8） */
    public static AABB standingBox(@Nullable ServerPlayer player, Vec3 feet) {
        EntityDimensions dimensions = player != null ? player.getDimensions(Pose.STANDING) : EntityType.PLAYER.getDimensions();
        return dimensions.makeBoundingBox(feet);
    }

    /** 碰撞箱碰到的格子里有没有会伤人的方块（往里收一点，贴着的邻格不算） */
    private static boolean hazardous(ServerLevel level, AABB box) {
        AABB inner = box.deflate(1.0E-7D);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(inner.minX, inner.minY, inner.minZ),
                BlockPos.containing(inner.maxX, inner.maxY, inner.maxZ))) {
            if (hurts(level, pos, level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 站在这一格里（或踩在它上面）会不会受伤。只读这一格本身：{@code WalkNodeEvaluator.getBlockPathTypeStatic} 会再读周围 26 格，
     * 落点在区块边上时会同步加载隔壁区块，而且旁边有一朵凋灵玫瑰也会判成危险，所以不用它。
     * 原版的几种直接认；模组方块问 Forge 的寻路钩子（恐惧仙人掌靠覆盖它标成伤害）。
     */
    private static boolean hurts(ServerLevel level, BlockPos pos, BlockState state) {
        // isBurningBlock：火、灵魂火、岩浆、岩浆块、点着的营火、岩浆炼药锅
        if (state.getFluidState().is(FluidTags.LAVA) || WalkNodeEvaluator.isBurningBlock(state) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)) {
            return true;
        }
        BlockPathTypes type = state.getBlockPathType(level, pos, null);
        return type == BlockPathTypes.LAVA || type == BlockPathTypes.DAMAGE_FIRE || type == BlockPathTypes.DAMAGE_OTHER
                || type == BlockPathTypes.DAMAGE_CAUTIOUS || type == BlockPathTypes.POWDER_SNOW;
    }

    // ------------------------------------------------------------------
    // 传送
    // ------------------------------------------------------------------

    /**
     * 把玩家传到 {@code at}（脚的位置，一般是 {@link #check} 算好的落点），返回传成没有。只在服务端主线程调。
     * 传成以后摔落距离由调用方清；失败时什么都没变（跨维度被取消时 Forge 在移除玩家之前就返回了）。
     */
    public static boolean teleport(ServerPlayer player, ServerLevel dest, Vec3 at, float yRot, float xRot) {
        if (player.level() == dest) {
            EntityTeleportEvent event = new EntityTeleportEvent(player, at.x, at.y, at.z);
            if (MinecraftForge.EVENT_BUS.post(event)
                    || event.getTargetX() != at.x || event.getTargetY() != at.y || event.getTargetZ() != at.z) {
                return false;
            }
            player.teleportTo(dest, at.x, at.y, at.z, yRot, xRot);
            return player.level() == dest && player.position().distanceToSqr(at) <= 1.0D;
        }
        PortalInfo info = new PortalInfo(at, Vec3.ZERO, yRot, xRot);
        DimensionChange hook = TEST_DIMENSION_CHANGES.isEmpty() ? null : TEST_DIMENSION_CHANGES.get(player.getUUID());
        if (hook != null) {
            return hook.change(player, dest, info);
        }
        return player.changeDimension(dest, new FixedTeleporter(info)) != null && player.level() == dest;
    }

    // ------------------------------------------------------------------
    // 给测试看的
    // ------------------------------------------------------------------

    /** {@link #setTestDimensionChange} 的钩子：代替真的换维度，返回 true 当作换成功 */
    @FunctionalInterface
    public interface DimensionChange {
        boolean change(ServerPlayer player, ServerLevel dest, PortalInfo info);
    }

    /** 按玩家 UUID 登记的测试钩子（只有 GameTest 往里放；正常游戏永远是空表，跨维度传送时只多一次 isEmpty） */
    private static final Map<UUID, DimensionChange> TEST_DIMENSION_CHANGES = new HashMap<>();

    /**
     * 只给 GameTest：这名玩家跨维度传送时，{@link #teleport} 不真的 {@code changeDimension}，改调 hook
     * （GameTest 的测试玩家不在世界里、不在玩家列表里，真换维度会被加进目标世界）。按 UUID 登记；hook 为 null 时摘掉。
     * 同 6a 海纳尔之门的 {@code HoannaTeleport.setTestDimensionChange}。
     */
    public static void setTestDimensionChange(UUID player, @Nullable DimensionChange hook) {
        if (hook == null) {
            TEST_DIMENSION_CHANGES.remove(player);
        } else {
            TEST_DIMENSION_CHANGES.put(player, hook);
        }
    }
}
