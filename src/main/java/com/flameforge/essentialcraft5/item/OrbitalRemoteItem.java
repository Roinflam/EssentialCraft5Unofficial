package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.gun.RayShot;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * 轨道打击遥控器（原作 {@code ItemOrbitalRemote}，68 行；注册名 {@code orbital_remote}，batch8 §4.3、policy §4.6）。
 * 自带 {@value #CAPACITY} MRU（原作 setMaxMRU(20000)），创造栏放空 / 满两份（{@link MRUStaffItem}）。只有全息投影的稀有掉落，没有配方。
 * <ul>
 *   <li><b>瞄准</b>（{@link #aimFor} → {@link #aim}）：按玩家这一刻的位置和朝向（不插值），从眼睛沿视线 {@value #RANGE} 格找第一个方块，{@code ClipContext.Block.OUTLINE} + {@code Fluid.SOURCE_ONLY}
 *       （原作 {@code rayTraceBlocks(…, true, false, false)}：静止液体的液面、没有碰撞箱的草和花都挡）。落点是那一格<b>顶面正中</b>
 *       （{@link #strikePoint}，原作 L53；打中侧面也落在那一格顶上）。没打中方块就不扣、不放，动作栏提示一行。</li>
 *   <li><b>右键</b>（只在服务端结算，客户端只预判要不要挥手）：只认真玩家（{@code ECProtection.isRealPlayer}，同枪械、灵魂刻刀——
 *       假玩家配上无尽 MRU 晶体就是一台自动刷怪塔）；冷却中按不动；先确认扣得起 {@code general.orbital_remote.mru_per_strike}
 *       （{@code MRUDrain}：先扣身上的储存物品，凑不够再扣遥控器自己存的，创造模式不扣），<b>打击实体放下了才扣</b>
 *       （生成被别的模组取消、或者在加入事件里被直接移除时什么都不扣）；然后进冷却 {@code cooldown_ticks}（物品冷却，所有遥控器共用）。
 *       放成了返回 consume：原作返回父类的 PASS，主手用完还会接着用副手的东西。</li>
 *   <li><b>打击</b>：{@code OrbitalStrikeEntity.spawn(…, damage, player_damage_multiplier, }{@value #STRIKE_DELAY}{@code  刻, 使用者, 目标规则)}。
 *       伤害记在使用者头上（PvP、队伍、领地都按使用者问），打死的算使用者击杀、照常掉落和给经验（不进 {@code no_death_loot}：
 *       要真玩家亲手瞄准、每发 10 000 MRU、5 秒冷却，和拿剑刷没有区别）。伤害真落下才着火、不击退、不改方块、使用者不在了就熄灭，见实体类注释。</li>
 *   <li><b>目标规则</b>（{@link #isTarget}）：活体；跳过使用者、使用者的盟友（同队伍的、自己驯服的，{@code RayShot.isAlly}）、旁观者、盔甲架；
 *       不是玩家的再跳过 {@code ECEntityRules.isSpared}（起了名字的、村民、不论谁驯服的、拴着绳的、玩家造的傀儡、装了鞍的骆驼）。
 *       范围伤害没法精确瞄准，这些「有主的」一律不碰（policy P1 第 3 条），想打就用近战或枪。玩家照 PvP、队伍、领地规则挨打，伤害再乘打玩家倍率。</li>
 *   <li><b>预览光柱</b>：主手或副手拿着时，客户端每帧调同一个 {@link #aimFor}，在落点画品红光柱（{@code client/HoloWeaponsClient}；原作挂在雾事件上，一帧画好几次、只看主手）。</li>
 *   <li><b>锁定</b>（第十批 10b，batch10.md §5.7.4，{@link #lockTargetFor}）：右键时准星对着 {@code general.orbital_remote.lock_range} 格内的一个目标
 *       （{@link #isTarget} 认的；挡在方块后面的、隐身的不算），就把打击放在它脚下、让打击点跟着它水平移动，落下前 10 刻定住
 *       （{@code OrbitalStrikeEntity} 的 {@code follow}）。没对准生物时照旧打准星对着的方块。预览光柱同样画在被锁定的目标身上。
 *       锁定只决定落点，伤害规矩不变（带使用者、PvP / 队伍 / 领地照管、只打目标规则放行的）。</li>
 * </ul>
 * 和原作不同的地方：加了冷却（原作没有，带一块永恒物质就是 100 发、按住右键每 4 刻一发）；只在服务端扣 MRU（原作两端都扣）；
 * 打击不再烧地形（原作删水冰雪、把方块就地按熔炉配方烧掉，见 {@code OrbitalStrikeEntity}）。
 */
public class OrbitalRemoteItem extends MRUStaffItem {

    /** 原作 setMaxMRU(20000) */
    public static final int CAPACITY = 20000;
    /** 射程（原作 d3 = 32） */
    public static final double RANGE = 32D;
    /** 放下后多少刻落下（原作 delay 3 秒） */
    public static final int STRIKE_DELAY = 60;
    /** 没瞄到方块时的动作栏提示 */
    public static final String MSG_NO_TARGET = "message.essentialcraft5.orbital_remote.no_target";

    public OrbitalRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, CAPACITY);
    }

    // ------------------------------------------------------------------ 右键

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide && !ECProtection.isRealPlayer(player)) {
            return InteractionResultHolder.fail(stack);
        }
        // 两端都按这一刻的位置和朝向（不插值；客户端先发移动包再发使用包，服务端拿到的就是它），客户端只用来预判
        ECHoloWeapons.Config.Values config = ECHoloWeapons.Config.values();
        LivingEntity lock = lockTargetFor(player, config.lockRange());
        BlockHitResult hit = lock == null ? aimFor(player) : null;
        if (lock == null && hit == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(MSG_NO_TARGET, (int) RANGE).withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!MRUDrain.canAfford(player, stack, config.mruPerStrike())) {
            if (!level.isClientSide) {
                tellNoMRU(player, config.mruPerStrike());
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }
        Vec3 point = lock != null ? lock.position() : strikePoint(hit);
        return strike(serverLevel, player, stack, point, lock, config) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    /**
     * 服务端：在 point 放下一记打击（锁定了目标时跟着它走），放下了才扣 MRU、进冷却。调用方已经确认过扣得起（同一刻里没有别的东西动 MRU，一定扣得到；
     * 万一扣不到就把刚放下的打击撤掉）。
     *
     * @param follow 锁定的目标；没锁定为 null
     * @return 放成了没有（生成被别的模组取消、或者在加入事件里被移除时 false，什么都没扣）
     */
    private boolean strike(ServerLevel level, Player player, ItemStack stack, Vec3 point, @Nullable LivingEntity follow,
                           ECHoloWeapons.Config.Values config) {
        OrbitalStrikeEntity strike = OrbitalStrikeEntity.spawn(level, point, (float) config.damage(), (float) config.playerDamageMultiplier(),
                STRIKE_DELAY, player, targets(player), follow);
        // 有的模组在加入事件里直接 discard 而不取消，addFreshEntity 照样返回 true，打击却不会落下：一并算没放成
        if (strike == null || strike.isRemoved()) {
            return false;
        }
        if (!MRUDrain.playerUseMRU(player, stack, config.mruPerStrike())) {
            strike.discard();
            return false;
        }
        if (config.cooldownTicks() > 0) {
            player.getCooldowns().addCooldown(this, config.cooldownTicks());
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    // ------------------------------------------------------------------ 瞄准（两端共用，预览光柱也用）

    /**
     * 按玩家这一刻的位置和朝向瞄准（不插值）：右键结算和客户端的预览光柱都调它，两端走同一个函数。
     * 方向取 {@code getLookAngle()}（身体朝向 yRot），不取 {@code getViewVector(1F)}：后者在活体上用的是头的朝向 yHeadRot，
     * 服务端只在每刻的玩家更新里刷新它，处理使用包时还是上一刻的值，边转视角边右键会落后一刻。
     */
    @Nullable
    public static BlockHitResult aimFor(Player player) {
        return aim(player.level(), player.getEyePosition(), player.getLookAngle(), player);
    }

    /**
     * 从 eye 沿 direction 找 {@value #RANGE} 格内的第一个方块（静止液体的液面、没有碰撞箱的草和花都算，见类注释）。
     *
     * @param viewer 碰撞上下文（脚手架之类按谁看来定形状）；没有传 null
     * @return 打中的方块；没打中（对着天空、太远）返回 null
     */
    @Nullable
    public static BlockHitResult aim(Level level, Vec3 eye, Vec3 direction, @Nullable Entity viewer) {
        if (direction.lengthSqr() < 1.0E-12D) {
            return null;
        }
        Vec3 end = eye.add(direction.normalize().scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, viewer));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** 按这一刻的配置找锁定目标（预览光柱用，同右键时的判断） */
    @Nullable
    public static LivingEntity lockTargetFor(Player player) {
        return lockTargetFor(player, ECHoloWeapons.Config.values().lockRange());
    }

    /**
     * 准星对着的锁定目标：从眼睛沿视线（身体朝向，同 {@link #aimFor}）range 格内、在第一个挡住视线的方块之前、碰撞箱外扩各自的拾取半径以后
     * 被视线穿过的、离得最近的目标（{@link #isTarget}）。隐身的不锁（预览光柱会把它的位置画出来，等于一台探测器），照旧打它身后的方块。
     * range ≤ 0 或没有时返回 null。
     */
    @Nullable
    public static LivingEntity lockTargetFor(Player player, double range) {
        if (!(range > 0D)) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        if (direction.lengthSqr() < 1.0E-12D) {
            return null;
        }
        Vec3 end = eye.add(direction.normalize().scale(range));
        BlockHitResult wall = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() == HitResult.Type.BLOCK) {
            end = wall.getLocation();
        }
        AABB search = new AABB(eye, end).inflate(1D);
        // 原版准星选实体的那一套：碰撞箱外扩拾取半径、取最近的
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, search,
                entity -> entity instanceof LivingEntity living && !living.isInvisible() && isTarget(player, living), eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /** 落点：打中的那一格顶面正中（原作 getBlockPos() + (0.5, 1, 0.5)） */
    public static Vec3 strikePoint(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D);
    }

    // ------------------------------------------------------------------ 目标

    /** 这位使用者召下的打击打哪些生物（见 {@link #isTarget}） */
    public static Predicate<LivingEntity> targets(Entity user) {
        return living -> isTarget(user, living);
    }

    /**
     * 遥控器的目标规则（见类注释）：活着、不是使用者、不是旁观者、不是盔甲架、不是使用者的盟友；不是玩家的还要不在 {@code ECEntityRules.isSpared} 里。
     * 玩家能不能挨打由伤害本身问 PvP、队伍、领地。
     */
    public static boolean isTarget(Entity user, LivingEntity living) {
        if (living == user || !living.isAlive() || living.isSpectator() || living instanceof ArmorStand) {
            return false;
        }
        if (RayShot.isAlly(user, living)) {
            return false;
        }
        return living instanceof Player || !ECEntityRules.isSpared(living);
    }

    // ------------------------------------------------------------------ 提示

    @Override
    protected void addUsage(ItemStack stack, List<Component> tooltip) {
        ECHoloWeapons.Config.Values config = ECHoloWeapons.Config.values();
        tooltip.add(usage("tooltip.essentialcraft5.orbital_remote.use", (int) RANGE, STRIKE_DELAY / 20, config.mruPerStrike()));
        tooltip.add(usage("tooltip.essentialcraft5.orbital_remote.strike", number(config.damage())));
        tooltip.add(usage("tooltip.essentialcraft5.orbital_remote.spares"));
        if (config.lockRange() > 0D) {
            tooltip.add(usage("tooltip.essentialcraft5.orbital_remote.lock", number(config.lockRange())));
        }
        if (config.cooldownTicks() > 0) {
            tooltip.add(usage("tooltip.essentialcraft5.orbital_remote.cooldown", number(config.cooldownTicks() / 20D)));
        }
    }

    /** 整数不带小数点，其余保留一位（128.0 → 128，1.5 → 1.5） */
    static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E9D) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
