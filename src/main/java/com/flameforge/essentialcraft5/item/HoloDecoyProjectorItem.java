package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.entity.HoloDecoyEntity;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECHoloDecoy;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 全息诱饵投影仪（第十批 10b，batch10.md §5.8.2；注册名 {@code holo_decoy_projector}，原作没有）。自带 {@value #CAPACITY} MRU，
 * 创造栏放空 / 满两份（{@link MRUStaffItem}）。配方见 {@code ECHoloDecoy} 的类注释。
 * <ul>
 *   <li><b>右键</b>（只在服务端结算，客户端只预判要不要挥手）：只认真玩家（{@code ECProtection.isRealPlayer}：假玩家配无尽 MRU 晶体就成了一台不用人的引怪机）；
 *       冷却中按不动；准星前 {@code general.holo_decoy.max_range}（默认 8）格内要有能站的地方（{@code HoloDecoyEntity.findSpot}），没有就不扣、动作栏说一句；
 *       先确认扣得起 {@code mru_per_use}（默认 5 000，{@code MRUDrain}：先扣身上的储存物品，凑不够再扣投影仪自己存的，创造模式不扣），
 *       <b>诱饵生成了才扣</b>（生成被别的模组取消、或者在加入事件里被直接移除时什么都不扣、不进冷却），扣完才让诱饵开始骗怪、顶掉主人原来的那个；
 *       然后进冷却 {@code cooldown_ticks}（默认 600，物品冷却，所有投影仪共用），第一次投成发手册进度 {@code guide/hologram/decoy}。</li>
 *   <li><b>冷却记在人身上</b>：原版物品冷却不存盘，退出重进就清零，所以到期刻（{@code gameTime}，各维度共用）另记进玩家持久数据
 *       PlayerPersisted 下的 {@value #READY_KEY}（同 θ 腰带），物品冷却和它有一样还在就按不动；登录、从末地回来时把剩下的刻数补回物品冷却
 *       （{@code HoloDecoyEvents}，客户端靠它画遮罩、预判不挥手）；死亡重生时清掉，和 θ 一样死了冷却清零。</li>
 *   <li>诱饵本身的规矩（骗谁、Boss 只骗几秒、什么时候消失）见 {@link HoloDecoyEntity}。</li>
 * </ul>
 * 不探测领地：投出来的只是一个几秒就消失的实体，不改方块、不碰别的玩家；混合端上插件拦下实体生成时这里照常不扣。
 */
public class HoloDecoyProjectorItem extends MRUStaffItem {

    /** 自带的 MRU（同轨道打击遥控器、二分枪） */
    public static final int CAPACITY = 20000;
    /** 准星前没有能站的地方 */
    public static final String MSG_NO_SPOT = "message.essentialcraft5.holo_decoy.no_spot";
    /** 生成被拦下 */
    public static final String MSG_FAILED = "message.essentialcraft5.holo_decoy.failed";
    /** 冷却到期刻（{@code gameTime}）在玩家持久数据 PlayerPersisted 下的键 */
    public static final String READY_KEY = "essentialcraft5:holo_decoy_ready";

    public HoloDecoyProjectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, CAPACITY);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (coolingDown(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide && !ECProtection.isRealPlayer(player)) {
            return InteractionResultHolder.fail(stack);
        }
        ECHoloDecoy.Config.Values config = ECHoloDecoy.Config.values();
        Vec3 spot = HoloDecoyEntity.findSpot(level, player, config.maxRange());
        if (spot == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(MSG_NO_SPOT, config.maxRange()).withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!MRUDrain.canAfford(player, stack, config.mruPerUse())) {
            if (!level.isClientSide) {
                tellNoMRU(player, config.mruPerUse());
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }
        return project(serverLevel, player, stack, spot, config) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    /**
     * 服务端：在 spot 投出诱饵，生成了才扣 MRU、开始骗怪、进冷却、发进度。调用方已经确认过扣得起（同一刻里没有别的东西动 MRU，一定扣得到；
     * 万一扣不到就把刚生成的诱饵撤掉，主人原来的诱饵不受影响）。
     *
     * @return 投成了没有
     */
    private boolean project(ServerLevel level, Player player, ItemStack stack, Vec3 spot, ECHoloDecoy.Config.Values config) {
        HoloDecoyEntity decoy = HoloDecoyEntity.spawn(level, player, spot, config);
        if (decoy == null) {
            player.displayClientMessage(Component.translatable(MSG_FAILED).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!MRUDrain.playerUseMRU(player, stack, config.mruPerUse())) {
            decoy.discard();
            return false;
        }
        decoy.activate();
        if (config.cooldownTicks() > 0) {
            player.getCooldowns().addCooldown(this, config.cooldownTicks());
            setReadyAt(player, level.getGameTime() + config.cooldownTicks());
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerPentacleBlockEntity.award(serverPlayer, ECHoloDecoy.ADVANCEMENT_DECOY, ECHoloDecoy.ADVANCEMENT_DECOY_CRITERION);
        }
        return true;
    }

    // ------------------------------------------------------------------ 冷却（物品冷却 + 持久数据里的到期刻）

    /** 还在冷却：物品冷却还在，或者持久数据里的到期刻还没到（退出重进以后物品冷却清了也算）；客户端只看得到物品冷却 */
    public static boolean coolingDown(Player player) {
        return player.getCooldowns().isOnCooldown(ECHoloDecoy.HOLO_DECOY_PROJECTOR.get()) || remainingTicks(player) > 0L;
    }

    /** 持久数据里的冷却还剩几刻（没有记录、已经到期时 0） */
    public static long remainingTicks(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(READY_KEY)) {
            return 0L;
        }
        return Math.max(0L, persisted.getLong(READY_KEY) - player.level().getGameTime());
    }

    /** 记下冷却到期刻（GameTest 也用它把冷却拨到期） */
    public static void setReadyAt(Player player, long gameTime) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putLong(READY_KEY, gameTime);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** 删掉冷却记录（死亡重生时） */
    public static void clearReady(Player player) {
        CompoundTag root = player.getPersistentData();
        if (root.contains(Player.PERSISTED_NBT_TAG)) {
            root.getCompound(Player.PERSISTED_NBT_TAG).remove(READY_KEY);
        }
    }

    /** 登录、从末地回来时：还在冷却就把剩下的刻数补回物品冷却（客户端靠它画遮罩），已经到期的记录顺手删掉 */
    public static void restoreCooldown(Player player) {
        if (player.level().isClientSide || !player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(READY_KEY)) {
            return;
        }
        long remaining = remainingTicks(player);
        if (remaining > 0L) {
            player.getCooldowns().addCooldown(ECHoloDecoy.HOLO_DECOY_PROJECTOR.get(), (int) Math.min(Integer.MAX_VALUE, remaining));
        } else {
            clearReady(player);
        }
    }

    @Override
    protected void addUsage(ItemStack stack, List<Component> tooltip) {
        ECHoloDecoy.Config.Values config = ECHoloDecoy.Config.values();
        tooltip.add(usage("tooltip.essentialcraft5.holo_decoy_projector.use", config.maxRange(), config.mruPerUse()));
        tooltip.add(usage("tooltip.essentialcraft5.holo_decoy_projector.lure", config.radius(), seconds(config.lifetimeTicks())));
        tooltip.add(usage("tooltip.essentialcraft5.holo_decoy_projector.only_hostile"));
        if (config.cooldownTicks() > 0) {
            tooltip.add(usage("tooltip.essentialcraft5.holo_decoy_projector.cooldown", seconds(config.cooldownTicks())));
        }
    }

    /** 刻 → 秒：整数不带小数点，其余保留一位（160 → 8，150 → 7.5） */
    static String seconds(int ticks) {
        double value = ticks / 20D;
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
