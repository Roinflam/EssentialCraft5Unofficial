package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * MRUCU 移动魔杖。对应原作 ItemMRUMover（一阶 mruMover1；二阶 mruMover_t2 是子类 MRUMoverT2Item，在 ECGear 注册）。
 * <p>
 * 按住使用键：沿视线往前找 20 段（第 i 段是视线方向第 i+1 格处水平 ±1、竖直 ±2 的盒子），在最近的一段里随机挑一个聚集体，
 * 每 tick 沿视线方向移动 0.1 格——不潜行时往玩家这边拉，潜行时往外推。有耐久的魔杖每 20 tick 掉 1 点耐久（一阶没有耐久）。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>修 bug：原作扫描循环末尾有个无条件的 break，只查了第一段（前方 1 格附近），说明写的 20 段实际没生效。这里 20 段都查，
 *       并且只做一次覆盖全部 20 段的实体查询、再按段筛选，不是每段查一次。</li>
 *   <li>原作把「正在使用」写进物品 NBT（active）来切换贴图，每 tick 改 NBT 会不停同步物品栏；这里改用物品属性
 *       {@link #ACTIVE_PREDICATE}，直接看持有者是不是正在使用这个物品，不写 NBT。</li>
 *   <li>移动只在服务端做（位置自动同步），粒子只在客户端生成；原作两端都移动。</li>
 *   <li>原作挪到哪都行，能把一大团聚集体推进出生点。这里非管理员不能把聚集体从外面挪进出生点保护范围，也不能挪出世界边界；
 *       本来就在保护范围里的只能往远离出生点的方向挪（能挪出来，推不进中心）。规则见 {@link #canMoveTo}。</li>
 *   <li>第三批安全回补（policy §5，P1）：聚集体当前格和目的格都要使用者自己能动（领地插件说了算），假玩家一律挪不动，
 *       被拒不动、不扣耐久。规则见 {@link #mayMove}；它比上一条更严（出生点保护范围里的非管理员就挪不动了），两条都要过。</li>
 * </ul>
 */
public class MRUMoverItem extends Item {

    /** 正在使用时为 1（原作 active=true 的贴图） */
    public static final ResourceLocation ACTIVE_PREDICATE = EssentialCraft5.id("active");

    private static final int SEGMENTS = 20;
    /** 每 tick 移动的距离（原作 lookVec / 10） */
    private static final double STEP = 0.1D;

    public MRUMoverItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        MRUPresenceEntity target = findTarget(level, user);
        if (target == null) {
            return;
        }
        if (level.isClientSide) {
            // 原作：从玩家位置往聚集体方向放传送门粒子
            level.addParticle(ParticleTypes.PORTAL, user.getX(), user.getY(), user.getZ(),
                    target.getX() - user.getX(), target.getY() - user.getY() - 1D, target.getZ() - user.getZ());
            return;
        }
        Vec3 from = target.position();
        Vec3 destination = from.add(user.getLookAngle().scale(user.isShiftKeyDown() ? STEP : -STEP));
        // 原作能把一大团聚集体推进出生点，新玩家一出生就吃矩阵损伤：非管理员从外面挪进出生点保护范围、挪到世界边界外、
        // 或者在保护范围里往出生点方向挪的这一步不动，也不扣耐久（规则见 canMoveTo）。
        // 第三批安全回补：另外聚集体当前格和目的格都要使用者自己能动（mayMove），假玩家一律不行；先查前者，不花探测预算
        if (!canMoveTo(level, user, from, destination)) {
            return;
        }
        if (!mayMove(level, user, from, destination)) {
            // 按住使用键时每刻都会走到这里：提示每秒最多一次
            if (user instanceof Player player && ECProtection.isRealPlayer(player) && remainingUseDuration % 20 == 0) {
                player.displayClientMessage(Component.translatable("message.essentialcraft5.no_permission").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        target.setPos(destination.x, destination.y, destination.z);
        if (stack.isDamageableItem() && remainingUseDuration % 20 == 0) {
            stack.hurtAndBreak(1, user, entity -> entity.broadcastBreakEvent(user.getUsedItemHand()));
        }
    }

    /**
     * 玩家能不能把聚集体从 from（当前位置）挪到 destination；不是玩家在用就不拦（只看出生点和世界边界这层几何规则，
     * 不花权限探测的预算；假玩家、别的实体由 {@link #mayMove} 拦下，onUseTick 两条都查）。
     * 「能交互」按 {@code ServerLevel.mayInteract}：不在出生点保护范围内（管理员、没有管理员的服务器、主世界以外都不受保护）且在世界边界内。
     * <ul>
     *   <li>目的地在世界边界外：不行。</li>
     *   <li>目的地能交互：行。</li>
     *   <li>目的地在保护范围内、当前位置不在（从外面往里挪，或者当前位置在世界边界外）：不行。</li>
     *   <li>两处都在保护范围内（聚集体本来就在里面：遗迹生成在出生点附近、管理员放的）：只放行不靠近出生点的一步
     *       （{@link #notCloserToSpawn}），这样能挪出来，但推不进出生点中心。只看目的地的话，这种聚集体非管理员一步都挪不动。</li>
     * </ul>
     */
    public static boolean canMoveTo(Level level, LivingEntity user, Vec3 from, Vec3 destination) {
        if (!(user instanceof Player player)) {
            return true;
        }
        BlockPos to = BlockPos.containing(destination);
        if (!level.getWorldBorder().isWithinBounds(to)) {
            return false;
        }
        if (level.mayInteract(player, to)) {
            return true;
        }
        BlockPos start = BlockPos.containing(from);
        if (level.mayInteract(player, start) || !level.getWorldBorder().isWithinBounds(start)) {
            return false;
        }
        return notCloserToSpawn(level.getSharedSpawnPos(), from, destination);
    }

    /**
     * 权限（全模组安全原则 P1，policy.md §5、§7 裁定 6）：只有真玩家（{@link ECProtection#isRealPlayer}，假玩家、别的实体一律不行）、
     * 而且聚集体<b>当前所在格和目的格</b>都是他自己能动的（{@link ECProtection#canEdit}，按玩家、格子缓存 200 刻；
     * 聚集体每 10 刻才跨一格，所以正常拉拽几乎都命中缓存）才能挪。原作能把 2 万 MRU 的聚集体推进别人家里；
     * 漏进邻居领地里的聚集体由邻居自己挪。被拒的这一步不动、不扣耐久。
     * <p>
     * canEdit 里的 {@code level.mayInteract} 也管出生点保护和世界边界，所以对真玩家来说这里比 {@link #canMoveTo} 更严：
     * 出生点保护范围里的聚集体非管理员挪不动了（以前能往外挪），交给管理员。
     */
    public static boolean mayMove(Level level, LivingEntity user, Vec3 from, Vec3 destination) {
        if (!ECProtection.isRealPlayer(user)) {
            return false;
        }
        Player player = (Player) user;
        BlockPos start = BlockPos.containing(from);
        BlockPos to = BlockPos.containing(destination);
        return ECProtection.canEdit(player, level, start) && (to.equals(start) || ECProtection.canEdit(player, level, to));
    }

    /**
     * destination 离出生点不比 from 近。距离按出生点保护的量法：x、z 两轴离出生点方块中心的距离取大的，不看高度
     * （{@code DedicatedServer.isUnderSpawnProtection}）。
     */
    public static boolean notCloserToSpawn(BlockPos spawn, Vec3 from, Vec3 destination) {
        Vec3 center = Vec3.atBottomCenterOf(spawn);
        return spawnDistance(destination, center) >= spawnDistance(from, center);
    }

    private static double spawnDistance(Vec3 pos, Vec3 spawnCenter) {
        return Math.max(Math.abs(pos.x - spawnCenter.x), Math.abs(pos.z - spawnCenter.z));
    }

    /** 视线前方 20 段里最近一段中的聚集体（同一段有多个时随机挑一个），没有就返回 null */
    @Nullable
    public static MRUPresenceEntity findTarget(Level level, LivingEntity user) {
        Vec3 origin = user.position();
        Vec3 look = user.getLookAngle();
        // 各段盒子是同一个盒子沿直线平移，所有段的外包盒就是首尾两段的外包盒
        AABB sweep = segment(origin, look, 0).minmax(segment(origin, look, SEGMENTS - 1));
        List<MRUPresenceEntity> all = level.getEntitiesOfClass(MRUPresenceEntity.class, sweep);
        if (all.isEmpty()) {
            return null;
        }
        List<MRUPresenceEntity> hits = new ArrayList<>();
        for (int i = 0; i < SEGMENTS; ++i) {
            AABB box = segment(origin, look, i);
            for (MRUPresenceEntity presence : all) {
                if (presence.getBoundingBox().intersects(box)) {
                    hits.add(presence);
                }
            }
            if (!hits.isEmpty()) {
                return hits.get(level.random.nextInt(hits.size()));
            }
        }
        return null;
    }

    /** 第 i 段：原作 lookVec + lookVec × i，即视线方向第 i+1 格处，水平 ±1、竖直 ±2 */
    private static AABB segment(Vec3 origin, Vec3 look, int index) {
        Vec3 center = origin.add(look.scale(index + 1));
        return new AABB(center.x - 1D, center.y - 2D, center.z - 1D, center.x + 1D, center.y + 2D, center.z + 1D);
    }

    /** 耐久变化不触发重新举起的动画（原作只在换物品或 active 变化时触发） */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_mover.usage").withStyle(ChatFormatting.GRAY));
    }
}
