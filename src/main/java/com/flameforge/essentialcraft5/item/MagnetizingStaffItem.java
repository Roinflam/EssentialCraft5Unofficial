package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECStaffs;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 磁化法杖（原作 ItemMagnetizingStaff）：按住右键，每 {@value #INTERVAL} 刻扣 {@value #COST} MRU，
 * 把玩家周围水平 ±{@value #RANGE_H} 格、下 {@value #RANGE_DOWN} 上 {@value #RANGE_UP} 格内的掉落物和经验球往自己这边推（只改速度，拾取走原版）。
 * 推法照原作：水平每轴朝玩家 0.1；比玩家高的往下 0.1，不高的往上 0.5。
 * <h2>限制（policy §4.2）与修掉的原作问题</h2>
 * <ul>
 *   <li>只拉「看得见」的：从玩家眼睛到物品中心的连线不穿过有碰撞箱的方块（{@code ClipContext.Block.COLLIDER}）。隔墙水道、别人仓库里漏出来的东西不再被拉走。</li>
 *   <li>别人的掉落物不拉：丢出它的人（thrower）或指定的拾取人（NBT 的 Owner）是别人时跳过。怪物、方块的掉落和自己丢的照拉。</li>
 *   <li>只拉自己能动的地方的掉落物和经验球（第十批权限修补 A4，{@link #mayPullFrom}）：所在那格以玩家身份探测一次，别人领地里的（刷怪塔、农场的产出和经验）不拉。
 *       看得见之后才探测；经验球排在掉落物后面，共用玩家每刻的探测预算，物品多时经验球这一下可能先不拉、下一下再问。不分纯 Forge / 混合端。</li>
 *   <li>每次最多拉 {@code general.staffs.magnetizing_max_items}（默认 32）个掉落物 + {@code magnetizing_max_orbs}（默认 32）个经验球，按离玩家近的先；
 *       遮挡检查每类最多做这么多次射线（默认合计 64 次 / 8 刻），离得近但被挡住的那几个直接跳过、不往远处补。</li>
 *   <li>原作经验球用了 1.12 的 {@code AxisAlignedBB.expand}（只往正方向扩），只拉 +x +y +z 一个卦限里的；现在和掉落物一样对称。</li>
 *   <li>节奏按「已经按住了几刻」算（第 7、15、23…… 刻），和原作一样；松手重按不会更快（原作也不会）。只在服务端推，改完标记 hasImpulse 让客户端立刻同步。</li>
 *   <li>假玩家不工作（policy「FakePlayer 不给权限」）。MRU 不够时在动作栏提示一句（原作什么都不说），不推。</li>
 * </ul>
 * 实体查询体积 25×13×25，每 8 刻一次、只在玩家按住时发生（policy §4.2 定的频率，照原作）。
 */
public class MagnetizingStaffItem extends MRUStaffItem {

    /** 每几刻一次（原作 count % 8 == 0） */
    public static final int INTERVAL = 8;
    /** 每次的花费（原作 playerUseMRU(…, 50)） */
    public static final int COST = 50;
    /** 原作玩家脚下 1×1×1 的框 grow(12, 6, 12)：水平 ±12.5（以玩家为中心），竖直从脚下 −6 到 +7 */
    public static final double RANGE_H = 12D;
    public static final double RANGE_DOWN = 6D;
    public static final double RANGE_UP = 7D;
    /** 原作每次推的速度 */
    private static final double PULL = 0.1D;
    private static final double LIFT = 0.5D;
    /** 按住的时长上限（原作 Integer.MAX_VALUE；72000 刻 = 1 小时，是 INTERVAL 的整数倍） */
    private static final int USE_DURATION = 72000;

    public MagnetizingStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !isPulseTick(getUseDuration(stack) - remainingUseDuration)) {
            return;
        }
        if (!(entity instanceof Player player) || !ECProtection.isRealPlayer(player)) {
            return;
        }
        if (!MRUDrain.playerUseMRU(player, stack, COST)) {
            tellNoMRU(player, COST);
            return;
        }
        pulse(level, player);
    }

    /** 按住第 elapsed 刻（从 0 数）要不要推：第 7、15、23…… 刻（原作 count % 8 == 0，count 从 Integer.MAX_VALUE 往下数） */
    public static boolean isPulseTick(int elapsed) {
        return elapsed % INTERVAL == INTERVAL - 1;
    }

    /** 一次拉取的结果（GameTest 用） */
    public record Pulse(int items, int orbs) {
    }

    /**
     * 服务端：推一次（不扣 MRU，扣费在 {@link #onUseTick}）。先一次查完范围里的掉落物和经验球，排除别人的掉落物，按离玩家远近排序，
     * 近的先做遮挡检查，看得见的推一把，推满上限为止。
     */
    public static Pulse pulse(Level level, Player player) {
        return pulse(level, player, RANGE_H);
    }

    /**
     * 同 {@link #pulse(Level, Player)}，水平范围自己给（GameTest 用小范围，免得推到旁边别的测试的掉落物）。
     *
     * @param rangeH 水平范围：玩家脚下 1×1 的框往四周各扩这么多格
     */
    public static Pulse pulse(Level level, Player player, double rangeH) {
        return pulse(level, player, rangeH, item -> true);
    }

    /**
     * 同 {@link #pulse(Level, Player, double)}，掉落物另加一道过滤（第十二批 batch12.md §4.7.2：吸附模块只吸没人扔的）。
     * extra 和 {@link #mayPull} 一起在查实体时就筛掉，不占每次的件数名额；经验球不受它影响。遮挡、领地探测、上限都和法杖一样。
     * 法杖自己的两个重载转调这里、extra 恒真，行为不变。
     *
     * @param rangeH 水平范围：玩家脚下 1×1 的框往四周各扩这么多格
     * @param extra  掉落物还要满足的条件
     */
    public static Pulse pulse(Level level, Player player, double rangeH, Predicate<ItemEntity> extra) {
        // 原作：玩家脚下 1×1×1 的框 grow(12, 6, 12) → 水平 ±12.5、竖直 −6 ~ +7
        double half = 0.5D + rangeH;
        AABB box = new AABB(player.getX() - half, player.getY() - RANGE_DOWN, player.getZ() - half,
                player.getX() + half, player.getY() + RANGE_UP, player.getZ() + half);
        Vec3 eye = player.getEyePosition();
        Comparator<Entity> nearest = Comparator.comparingDouble(e -> e.distanceToSqr(player));

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box,
                item -> item.isAlive() && mayPull(item, player) && extra.test(item));
        items.sort(nearest);
        int pulledItems = pullVisible(level, player, eye, items, ECStaffs.Config.magnetMaxItems(), item -> mayPullFrom(level, player, item));

        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box, Entity::isAlive);
        orbs.sort(nearest);
        int pulledOrbs = pullVisible(level, player, eye, orbs, ECStaffs.Config.magnetMaxOrbs(), orb -> mayPullFrom(level, player, orb));
        return new Pulse(pulledItems, pulledOrbs);
    }

    /** 离得最近的 max 个里，看得见、又过了 allowed 的推一把；遮挡检查最多 max 次 */
    private static <T extends Entity> int pullVisible(Level level, Player player, Vec3 eye, List<T> sorted, int max, Predicate<T> allowed) {
        int pulled = 0;
        for (int i = 0; i < sorted.size() && i < max; i++) {
            T entity = sorted.get(i);
            Vec3 center = entity.position().add(0D, entity.getBbHeight() / 2D, 0D);
            if (level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) {
                continue;
            }
            if (!allowed.test(entity)) {
                continue;
            }
            push(entity, player);
            pulled++;
        }
        return pulled;
    }

    /**
     * 第十批权限修补 A4：掉落物 / 经验球所在那格玩家自己能不能动（{@link ECProtection#check}，以玩家身份探测，预算按玩家算、结果缓存 200 刻）。
     * 没人扔的掉落物和经验球（别人刷怪塔、农场的产出）不再从别人的领地里吸出来；预算用完、区块没加载（UNKNOWN）的这一下先不拉，下一下再问。
     * 看得见之后才问，看不见的不发探测。
     */
    public static boolean mayPullFrom(Level level, Player player, Entity entity) {
        return ECProtection.check(player, level, entity.blockPosition(), player) == ECProtection.Verdict.ALLOW;
    }

    private static void push(Entity entity, Player player) {
        double dx = player.getX() < entity.getX() ? -PULL : PULL;
        double dy = player.getY() < entity.getY() ? -PULL : LIFT;
        double dz = player.getZ() < entity.getZ() ? -PULL : PULL;
        entity.setDeltaMovement(entity.getDeltaMovement().add(dx, dy, dz));
        entity.hasImpulse = true;
    }

    /**
     * 丢出它的人或指定的拾取人是别人时不拉。ItemEntity 的 thrower / target 是私有字段，读法（SRG 反射 + NBT 兜底）挪到了
     * {@link ECItemEntities}（第四批 F4-12，漏斗、补种器等共用），行为不变。
     */
    public static boolean mayPull(ItemEntity item, Player player) {
        UUID self = player.getUUID();
        UUID thrower = ECItemEntities.thrower(item);
        if (thrower != null && !thrower.equals(self)) {
            return false;
        }
        return ECItemEntities.pickableBy(item, self);
    }

    @Override
    protected void addUsage(ItemStack stack, List<Component> tooltip) {
        tooltip.add(usage("tooltip.essentialcraft5.magnetizing_staff.use", COST));
    }
}
