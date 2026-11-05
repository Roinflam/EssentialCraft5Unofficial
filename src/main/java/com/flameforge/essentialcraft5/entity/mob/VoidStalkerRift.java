package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.teleport.ECTeleport;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 虚空潜行者挪人的两招共用的落点与传送（第十三批 13c-M3，batch13.md §5.2、policy §4.18「位移只改自己的速度或走可取消的传送」）：
 * 裂隙爪把目标闪到几格外（{@link #blinkSpot}），裂隙行者把目标拉到自己跟前（{@link #pullSpot}），都只经 {@link #displace} 传送。
 * <p>
 * <b>落点</b>（{@link #landing}）：脚那一格的区块已加载、在世界边界内、按目标的碰撞箱放得下（没方块、没液体），
 * 并且<b>脚下 {@value #FLOOR_DEPTH} 格以内有能站的方块</b>（中间是空的）；那块地、人站的格子、落下去穿过的格子都不伤人
 * （岩浆、火、岩浆块、仙人掌、凋灵玫瑰、甜浆果丛这些）。
 * 末地外岛四面是虚空，这条保证闪过去的人最多摔三格，不会被闪到虚空上面。
 * <p>
 * <b>不把人送进出不来的地方</b>（{@link #reachable}，policy §1.1 第 5 条「把别人困住」、batch13.md §7.5）：候选点光是 {@link #landing} 合格还不够，
 * <ul>
 *   <li>从出手那一方的眼睛（闪人看目标自己的眼睛，拉人看潜行者的眼睛）连到落点的眼睛高度，中间不能有挡人的方块（{@link #clearPath}）：
 *       按碰撞形状判，墙、玻璃、栅栏、关着的门都挡，区块没加载当挡住。墙后、带顶的封闭房间里的格子就不会被选上；</li>
 *   <li>目标是玩家时，落脚那格和脚下那块地都要对他 {@link ECProtection#check} 放行，只认 ALLOW；拒绝、这一刻给不出结论（预算用完、区块没加载）
 *       都换下一个候选。连线能从 1.5 格高的栅栏上越过去，别人领地里用栅栏围的畜栏光靠连线拦不住，要靠这一条。
 *       探测预算按潜行者算，只对前两关都过了的候选探测，一招最多问到几格。</li>
 * </ul>
 * 一个候选都不合格就不挪人（冷却照算，由调用方定）。
 * <p>
 * <b>传送</b>：玩家走 {@link ECTeleport#teleport}（发 Forge 的 {@code EntityTeleportEvent}，混合端插件在 {@code PlayerTeleportEvent} 里取消也认），
 * 别的生物照样先发 {@code EntityTeleportEvent}；被取消、被改了落点都当作没传成，什么都不变。传成以后清掉摔落距离。
 */
public final class VoidStalkerRift {

    /** 落点脚下几格以内要有能站的方块 */
    public static final int FLOOR_DEPTH = 3;
    /** 找闪人落点最多试几次 */
    public static final int BLINK_TRIES = 16;

    private VoidStalkerRift() {
    }

    /**
     * 闪人落点：以 target 为中心水平 min~max 格、上下 −2~+3 格里随机挑 {@value #BLINK_TRIES} 次，第一个 {@link #reachable} 的
     * （连线从 target 自己的眼睛起）；都不合格返回 null。
     *
     * @param caller 权限探测的预算按它算（潜行者传自己）
     */
    @Nullable
    public static Vec3 blinkSpot(ServerLevel level, LivingEntity target, RandomSource random, int min, int max, @Nullable Object caller) {
        int near = Math.max(1, Math.min(min, max));
        int far = Math.max(near, Math.max(min, max));
        Vec3 eye = target.getEyePosition();
        for (int i = 0; i < BLINK_TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double dist = near + random.nextDouble() * (far - near);
            BlockPos feet = BlockPos.containing(target.getX() + Math.cos(angle) * dist, target.getY() + random.nextInt(6) - 2,
                    target.getZ() + Math.sin(angle) * dist);
            Vec3 spot = reachable(level, target, feet, eye, caller);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /**
     * 拉人落点：puller 朝 target 那一侧 1.5~2.5 格、左右偏一点、上下一格以内，第一个 {@link #reachable} 的（连线从 puller 的眼睛起，
     * 探测预算按 puller 算）；都不合格返回 null。
     */
    @Nullable
    public static Vec3 pullSpot(ServerLevel level, Mob puller, LivingEntity target) {
        Vec3 dir = target.position().subtract(puller.position()).multiply(1D, 0D, 1D);
        if (dir.lengthSqr() < 1.0E-4D) {
            dir = Vec3.directionFromRotation(0F, puller.getYRot());
        }
        dir = dir.normalize();
        Vec3 eye = puller.getEyePosition();
        double[] distances = {2D, 1.5D, 2.5D};
        float[] turns = {0F, 0.5F, -0.5F, 1F, -1F};
        int[] heights = {0, 1, -1};
        for (int dy : heights) {
            for (float turn : turns) {
                Vec3 side = dir.yRot(turn);
                for (double d : distances) {
                    BlockPos feet = BlockPos.containing(puller.getX() + side.x * d, puller.getY() + dy, puller.getZ() + side.z * d);
                    Vec3 spot = reachable(level, target, feet, eye, puller);
                    if (spot != null) {
                        return spot;
                    }
                }
            }
        }
        return null;
    }

    /**
     * feet 这一格能不能把 target 放过去（见类注释）：{@link #landing} 合格、从 {@code from} 连到落点的眼睛高度看得通（{@link #clearPath}）、
     * target 是玩家时他对落脚那格和脚下那块地都有权限（只认 ALLOW）。合格返回脚的位置，否则 null。
     * 先做不花预算的两关，最后才探测领地。
     *
     * @param from   连线的起点（闪人是目标的眼睛，拉人是潜行者的眼睛）
     * @param caller 权限探测的预算按它算
     */
    @Nullable
    public static Vec3 reachable(ServerLevel level, LivingEntity target, BlockPos feet, Vec3 from, @Nullable Object caller) {
        BlockPos floor = landingFloor(level, target, feet);
        if (floor == null) {
            return null;
        }
        Vec3 at = feetPosition(feet);
        if (!clearPath(level, from, at.add(0D, eyeHeight(target), 0D))) {
            return null;
        }
        if (target instanceof ServerPlayer player && (ECProtection.check(player, level, feet, caller) != ECProtection.Verdict.ALLOW
                || ECProtection.check(player, level, floor, caller) != ECProtection.Verdict.ALLOW)) {
            return null;
        }
        return at;
    }

    /**
     * from → to 之间有没有挡人的方块：按碰撞形状判（不带实体的碰撞上下文：墙、玻璃、玻璃板、栅栏、铁栏杆、关着的门都挡；
     * 空气、草、花、液体不挡），碰到没加载的区块当挡住。直接读已加载的区块，不触发加载（写法同特斯拉线圈的视线，只是那边按视觉形状）。
     */
    public static boolean clearPath(ServerLevel level, Vec3 from, Vec3 to) {
        Boolean blocked = BlockGetter.traverseBlocks(from, to, level, (lvl, pos) -> {
            LevelChunk chunk = lvl.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
            if (chunk == null) {
                return Boolean.TRUE;
            }
            BlockState state = chunk.getBlockState(pos);
            if (state.isAir()) {
                return null;
            }
            VoxelShape shape = state.getCollisionShape(lvl, pos, CollisionContext.empty());
            return !shape.isEmpty() && shape.clip(from, to, pos) != null ? Boolean.TRUE : null;
        }, lvl -> Boolean.FALSE);
        return !Boolean.TRUE.equals(blocked);
    }

    /** 落到 feet 以后眼睛离脚多高：玩家按站立姿势（落点的碰撞箱也按站立算），别的生物按它现在的姿势 */
    private static double eyeHeight(LivingEntity entity) {
        return entity instanceof ServerPlayer player ? player.getEyeHeight(Pose.STANDING) : entity.getEyeHeight();
    }

    /** feet 这一格当落点合不合格（见类注释，不看连线和权限）；合格返回脚的位置（这一格的底面正中），否则 null */
    @Nullable
    public static Vec3 landing(ServerLevel level, LivingEntity entity, BlockPos feet) {
        return landingFloor(level, entity, feet) == null ? null : feetPosition(feet);
    }

    private static Vec3 feetPosition(BlockPos feet) {
        return new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
    }

    /** {@link #landing} 的判法；合格时返回人会落到的那块地（脚下 1~{@value #FLOOR_DEPTH} 格里第一块有碰撞的），否则 null */
    @Nullable
    private static BlockPos landingFloor(ServerLevel level, LivingEntity entity, BlockPos feet) {
        if (!level.isInWorldBounds(feet) || !ECLevels.isFullyLoaded(level, feet)) {
            return null;
        }
        Vec3 at = feetPosition(feet);
        AABB box = entity instanceof ServerPlayer player ? ECTeleport.standingBox(player, at)
                : entity.getDimensions(entity.getPose()).makeBoundingBox(at);
        if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(entity, box) || level.containsAnyLiquid(box)
                || hazardInside(level, box)) {
            return null;
        }
        for (int k = 1; k <= FLOOR_DEPTH; k++) {
            BlockPos below = feet.below(k);
            if (!level.isInWorldBounds(below)) {
                return null;
            }
            BlockState state = level.getBlockState(below);
            if (!state.getCollisionShape(level, below).isEmpty()) {
                return hurts(level, below, state) ? null : below.immutable();
            }
            // 落下去要穿过的空格：有液体、或者火、浆果丛这类没碰撞却伤人的东西就不算落点
            if (!state.getFluidState().isEmpty() || hurts(level, below, state)) {
                return null;
            }
        }
        return null;
    }

    /** 碰撞箱占到的格子里有没有伤人的东西（火、凋灵玫瑰、甜浆果丛这类没碰撞、noCollision 拦不住的；往里收一点，贴着的邻格不算） */
    private static boolean hazardInside(ServerLevel level, AABB box) {
        AABB inner = box.deflate(1.0E-7D);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(inner.minX, inner.minY, inner.minZ),
                BlockPos.containing(inner.maxX, inner.maxY, inner.maxZ))) {
            if (hurts(level, pos, level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    /** 站上去会受伤的地（只看这一格本身，不读邻格，免得在区块边上同步加载隔壁区块） */
    private static boolean hurts(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getFluidState().is(FluidTags.LAVA) || WalkNodeEvaluator.isBurningBlock(state) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)) {
            return true;
        }
        BlockPathTypes type = state.getBlockPathType(level, pos, null);
        return type == BlockPathTypes.LAVA || type == BlockPathTypes.DAMAGE_FIRE || type == BlockPathTypes.DAMAGE_OTHER
                || type == BlockPathTypes.DAMAGE_CAUTIOUS;
    }

    /**
     * 把 target 传到 at（脚的位置；同一个世界里）。被 Forge 事件 / 插件取消、被改了落点、传完人不在落点 1 格内都算没传成，返回 false。
     * 朝向不变；传成以后清摔落距离。
     */
    public static boolean displace(LivingEntity target, Vec3 at) {
        if (!(target.level() instanceof ServerLevel level)) {
            return false;
        }
        boolean moved;
        if (target instanceof ServerPlayer player) {
            moved = ECTeleport.teleport(player, level, at, player.getYRot(), player.getXRot());
        } else {
            EntityTeleportEvent event = new EntityTeleportEvent(target, at.x, at.y, at.z);
            if (MinecraftForge.EVENT_BUS.post(event) || event.getTargetX() != at.x || event.getTargetY() != at.y || event.getTargetZ() != at.z) {
                return false;
            }
            target.teleportTo(at.x, at.y, at.z);
            moved = target.position().distanceToSqr(at) <= 1D;
        }
        if (moved) {
            target.resetFallDistance();
        }
        return moved;
    }

    /** 两点之间的水平距离（测试、提示用） */
    public static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
