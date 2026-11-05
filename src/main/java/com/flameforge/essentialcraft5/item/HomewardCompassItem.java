package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.HomewardCompassClient;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHomewardCompass;
import com.flameforge.essentialcraft5.teleport.HomewardChannels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * 归途罗盘（第十二批，batch12.md §4.5；注册名 {@code homeward_compass}，原作没有）。罗盘自己不存 MRU，全从身上的储存物品扣。
 *
 * <h2>绑定（潜行右键一个方块）</h2>
 * 写在 {@link #onItemUseFirst}：它在原版的物品冷却检查之前，所以罗盘冷却期间也能重新绑定（{@code useOn} 在冷却里不会被调用）；不潜行时直接放行，
 * 点箱子、门照常开。服务端对点的那格<b>上方</b>（传送时的落脚格）{@link ECProtection#check}：
 * ALLOW 才把那一格记进物品 NBT {@value #TAG_HOME}（{@code dim / x / y / z}，传送时它是脚下的地板），DENY、UNKNOWN 都不写，分别说「这里不能设」「再点一次」。
 * 进不了别人领地的人绑不进去；落脚格在建筑高度以外时只问点的那格。
 *
 * <h2>起手（右键，不潜行）</h2>
 * 只在服务端判（{@link #tryStart}），客户端直接返回 CONSUME、不自己进入使用状态：服务端放行后进入使用状态，原版的实体数据同步会让客户端跟着进入，
 * 松手时客户端照常发「停止使用」，服务端在 {@link #releaseUsing} 里取消。这样服务端拒绝时客户端不会卡在「举着」的状态。逐条判，不过就在动作栏说原因、不起手：
 * <ol>
 *   <li>配置开着；是真玩家（假玩家一律不行）；</li>
 *   <li>已绑定，且归途点的维度存在；</li>
 *   <li>不在冷却（物品冷却，或玩家持久数据里的到期刻 {@value #READY_KEY} 还没到，同全息诱饵投影仪）；</li>
 *   <li>没有「锚定」效果（相位锚模块给的）、没骑乘、没在睡觉；</li>
 *   <li>战斗锁：最近 {@code combat_lock_ticks}（200）刻内没被生物或玩家打过（记录在 {@code HomewardChannels}，见那里为什么不用原版的 lastHurtByMob）；</li>
 *   <li>跨维度时 {@link #targetAllowed}：允许跨维度，且目标不在 {@code denied_target_dimensions}（默认只有海纳尔）里；同维度不受限；</li>
 *   <li>扣得起 {@link #cost}（只看身上的储存物品）。</li>
 * </ol>
 * 过了就进入使用状态（{@code channel_ticks}，默认 100），在 {@code HomewardChannels} 登记，给目标区块加一张原版 PORTAL 票（300 刻自动过期），
 * 放一声音量 0.3 的传送门起动声。引导中的打断（走开、受伤、松手、被挂上锚定）和完成后的传送、扣费、冷却都在 {@code HomewardChannels}。
 *
 * <h2>价格</h2>
 * 同维度 {@code base_mru} + {@code mru_per_block} × 水平直线距离（向上取整），封顶 {@code max_mru_same_dimension}；跨维度一律 {@code cross_dimension_mru}。
 * 提示框按看的人自己的位置算距离和价格。
 */
public class HomewardCompassItem extends Item {

    /** 模型属性：绑定了是 1 */
    public static final ResourceLocation BOUND_PREDICATE = EssentialCraft5.id("bound");
    /** 物品 NBT：归途点（点的那一格，传送时是脚下的地板） */
    public static final String TAG_HOME = "Home";
    /** 冷却到期刻（{@code gameTime}，各维度共用）在玩家持久数据 PlayerPersisted 下的键 */
    public static final String READY_KEY = "essentialcraft5:homeward_ready_at";

    private static final String MSG = "message.essentialcraft5.homeward_compass.";
    public static final String MSG_BOUND = MSG + "bound";
    public static final String MSG_BIND_DENIED = MSG + "bind_denied";
    public static final String MSG_BIND_PENDING = MSG + "bind_pending";
    public static final String MSG_DISABLED = MSG + "disabled";
    public static final String MSG_UNBOUND = MSG + "unbound";
    public static final String MSG_NO_DIMENSION = MSG + "no_dimension";
    public static final String MSG_COOLDOWN = MSG + "cooldown";
    public static final String MSG_ANCHORED = MSG + "anchored";
    public static final String MSG_RIDING = MSG + "riding";
    public static final String MSG_COMBAT = MSG + "combat";
    public static final String MSG_CROSS_DISABLED = MSG + "cross_disabled";
    public static final String MSG_DENIED_DIMENSION = MSG + "denied_dimension";
    public static final String MSG_CHANNELING = MSG + "channeling";

    /**
     * 一个归途点。
     *
     * @param dimension 维度
     * @param pos       点的那一格（脚下的地板）
     */
    public record Home(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public HomewardCompassItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ NBT

    /** 物品上记着的归途点（没绑、坏数据时 null） */
    @Nullable
    public static Home home(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_HOME, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag home = tag.getCompound(TAG_HOME);
        ResourceLocation dimension = ResourceLocation.tryParse(home.getString("dim"));
        if (dimension == null || !home.contains("x", Tag.TAG_INT) || !home.contains("y", Tag.TAG_INT) || !home.contains("z", Tag.TAG_INT)) {
            return null;
        }
        return new Home(ResourceKey.create(Registries.DIMENSION, dimension), new BlockPos(home.getInt("x"), home.getInt("y"), home.getInt("z")));
    }

    public static boolean isBound(ItemStack stack) {
        return home(stack) != null;
    }

    public static void setHome(ItemStack stack, ResourceKey<Level> dimension, BlockPos pos) {
        CompoundTag home = new CompoundTag();
        home.putString("dim", dimension.location().toString());
        home.putInt("x", pos.getX());
        home.putInt("y", pos.getY());
        home.putInt("z", pos.getZ());
        stack.getOrCreateTag().put(TAG_HOME, home);
    }

    // ------------------------------------------------------------------ 绑定

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return bind(player, level, context.getClickedPos(), stack);
    }

    /** 服务端：把 clicked 记成归途点（见类注释）。ALLOW 才写 NBT */
    public static InteractionResult bind(Player player, Level level, BlockPos clicked, ItemStack stack) {
        BlockPos feet = clicked.above();
        BlockPos probe = level.isOutsideBuildHeight(feet) ? clicked : feet;
        ECProtection.Verdict verdict = ECProtection.check(player, level, probe, null);
        switch (verdict) {
            case ALLOW -> {
                setHome(stack, level.dimension(), clicked);
                level.playSound(null, clicked, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1F, 1F);
                player.displayClientMessage(Component.translatable(MSG_BOUND).withStyle(ChatFormatting.AQUA), true);
                return InteractionResult.CONSUME;
            }
            case DENY -> {
                player.displayClientMessage(Component.translatable(MSG_BIND_DENIED).withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            default -> {
                player.displayClientMessage(Component.translatable(MSG_BIND_PENDING).withStyle(ChatFormatting.YELLOW), true);
                return InteractionResult.FAIL;
            }
        }
    }

    // ------------------------------------------------------------------ 起手与引导

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }
        return tryStart(player, hand, stack) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    /**
     * 服务端起手（判定顺序见类注释）。已经在引导、或在等目的地加载时再右键（按住右键时客户端每 4 刻补发一次）什么都不做、返回 true。
     *
     * @return 起手了（或本来就在引导）
     */
    public static boolean tryStart(Player player, InteractionHand hand, ItemStack stack) {
        ECHomewardCompass.Config.Values config = ECHomewardCompass.Config.values();
        if (!(player instanceof ServerPlayer serverPlayer) || !ECProtection.isRealPlayer(player)) {
            return false;
        }
        if (HomewardChannels.busy(serverPlayer)) {
            return true;
        }
        if (!config.enabled()) {
            return refuse(player, Component.translatable(MSG_DISABLED));
        }
        Home home = home(stack);
        if (home == null) {
            return refuse(player, Component.translatable(MSG_UNBOUND));
        }
        ServerLevel dest = serverPlayer.server.getLevel(home.dimension());
        if (dest == null) {
            return refuse(player, Component.translatable(MSG_NO_DIMENSION));
        }
        if (coolingDown(player)) {
            long left = Math.max(remainingTicks(player), 1L);
            return refuse(player, Component.translatable(MSG_COOLDOWN, (left + 19L) / 20L));
        }
        if (player.hasEffect(ECEffects.ANCHORED.get())) {
            return refuse(player, Component.translatable(MSG_ANCHORED));
        }
        if (player.isPassenger()) {
            return refuse(player, Component.translatable(MSG_RIDING));
        }
        if (player.isSleeping()) {
            return false;
        }
        int locked = HomewardChannels.combatLockLeft(serverPlayer, config.combatLockTicks());
        if (locked > 0) {
            return refuse(player, Component.translatable(MSG_COMBAT, (locked + 19) / 20));
        }
        ResourceKey<Level> from = player.level().dimension();
        if (!targetAllowed(from, home.dimension(), config)) {
            return refuse(player, Component.translatable(config.allowCrossDimension() ? MSG_DENIED_DIMENSION : MSG_CROSS_DISABLED));
        }
        if (!rankAllows(player, from, home.dimension())) {
            return refuse(player, MatrixRank.gateMessage(player, MatrixRank.Gate.HOANNA_PORTAL));
        }
        int price = cost(player.blockPosition(), home.pos(), from.equals(home.dimension()), config);
        if (!MRUDrain.canAfford(player, ItemStack.EMPTY, price)) {
            MRUStaffItem.tellNoMRU(player, price);
            return false;
        }
        if (!HomewardChannels.begin(serverPlayer, hand, home, dest)) {
            return false;
        }
        player.displayClientMessage(Component.translatable(MSG_CHANNELING).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    private static boolean refuse(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        return false;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return ECHomewardCompass.Config.values().channelTicks();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /** 引导中每刻：客户端（自己和旁边看得见的人）在身边冒传送门粒子；服务端看有没有走开 */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide) {
            RandomSource random = entity.getRandom();
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.PORTAL, entity.getRandomX(0.6D), entity.getRandomY() - 0.25D, entity.getRandomZ(0.6D),
                        (random.nextDouble() - 0.5D) * 2D, -random.nextDouble(), (random.nextDouble() - 0.5D) * 2D);
            }
            return;
        }
        if (entity instanceof ServerPlayer player) {
            HomewardChannels.onUseTick(player);
        }
    }

    /** 引导满了（只在服务端调）：落点检查、传送、扣费、冷却 */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            HomewardChannels.finish(player);
        }
        return stack;
    }

    /** 松手：取消，不扣钱、不进冷却 */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            HomewardChannels.cancel(player);
        }
    }

    // ------------------------------------------------------------------ 纯函数

    /** 用现在的配置算价格 */
    public static int cost(BlockPos from, BlockPos to, boolean sameDimension) {
        return cost(from, to, sameDimension, ECHomewardCompass.Config.values());
    }

    /**
     * 价格：跨维度一口价；同维度 起价 + 每格 × 水平直线距离（x、z，向上取整），不超过封顶。
     * 距离按格子坐标算（起点是玩家脚下那格），所以价格和站在格子里的哪个位置无关。
     */
    public static int cost(BlockPos from, BlockPos to, boolean sameDimension, ECHomewardCompass.Config.Values config) {
        if (!sameDimension) {
            return config.crossDimensionMru();
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        long blocks = (long) Math.ceil(Math.sqrt(dx * dx + dz * dz));
        long price = (long) config.baseMru() + blocks * config.mruPerBlock();
        return (int) Math.min(price, config.maxMruSameDimension());
    }

    /** 用现在的配置判能不能从 from 传到 to */
    public static boolean targetAllowed(ResourceKey<Level> from, ResourceKey<Level> to) {
        return targetAllowed(from, to, ECHomewardCompass.Config.values());
    }

    /**
     * 同维度一律可以（在海纳尔里绑、在海纳尔里传也行）；跨维度要允许跨维度，且目标不在拒绝表里。
     * 拒绝表里的名字按 {@code ResourceLocation} 解析后比较（写 {@code the_nether} 和 {@code minecraft:the_nether} 一样）。
     */
    public static boolean targetAllowed(ResourceKey<Level> from, ResourceKey<Level> to, ECHomewardCompass.Config.Values config) {
        if (from.equals(to)) {
            return true;
        }
        if (!config.allowCrossDimension()) {
            return false;
        }
        for (String entry : config.deniedTargetDimensions()) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (to.location().equals(id)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 矩阵阶的海纳尔之门（第十三批 13a，batch13.md §2.5）：服主把海纳尔从 {@code denied_target_dimensions} 里拿掉以后，
     * 从海纳尔以外传进海纳尔同样要过 {@code MatrixRank.mayEnterHoanna}；从海纳尔出去、同维度不判。
     */
    public static boolean rankAllows(Player player, ResourceKey<Level> from, ResourceKey<Level> to) {
        return from.equals(to) || !ECHoanna.isHoanna(to) || ECHoanna.isHoanna(from) || MatrixRank.mayEnterHoanna(player);
    }

    // ------------------------------------------------------------------ 冷却（物品冷却 + 持久数据里的到期刻）

    /** 还在冷却：物品冷却还在，或者持久数据里的到期刻还没到（退出重进以后物品冷却清了也算） */
    public static boolean coolingDown(Player player) {
        return player.getCooldowns().isOnCooldown(ECHomewardCompass.HOMEWARD_COMPASS.get()) || remainingTicks(player) > 0L;
    }

    /** 持久数据里的冷却还剩几刻（没有记录、已经到期时 0） */
    public static long remainingTicks(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(READY_KEY)) {
            return 0L;
        }
        return Math.max(0L, persisted.getLong(READY_KEY) - player.level().getGameTime());
    }

    /** 传送成功后进冷却：持久数据里记到期刻（重进游戏不清），同时加同样长的物品冷却（客户端靠它画遮罩）。ticks ≤ 0 不进 */
    public static void startCooldown(Player player, int ticks) {
        if (ticks <= 0) {
            return;
        }
        setReadyAt(player, player.level().getGameTime() + ticks);
        player.getCooldowns().addCooldown(ECHomewardCompass.HOMEWARD_COMPASS.get(), ticks);
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
            player.getCooldowns().addCooldown(ECHomewardCompass.HOMEWARD_COMPASS.get(), (int) Math.min(Integer.MAX_VALUE, remaining));
        } else {
            clearReady(player);
        }
    }

    // ------------------------------------------------------------------ 提示框

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ECHomewardCompass.Config.Values config = ECHomewardCompass.Config.values();
        Home home = home(stack);
        if (home == null) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.homeward_compass.unbound").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos pos = home.pos();
            BlockPos viewer = level == null ? null : DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> HomewardCompassClient::viewerPosition);
            if (level != null && viewer != null && level.dimension().equals(home.dimension())) {
                double dx = pos.getX() - viewer.getX();
                double dz = pos.getZ() - viewer.getZ();
                long distance = (long) Math.ceil(Math.sqrt(dx * dx + dz * dz));
                tooltip.add(Component.translatable("tooltip.essentialcraft5.homeward_compass.home_here", pos.getX(), pos.getY(), pos.getZ(),
                        distance, cost(viewer, pos, true, config)).withStyle(ChatFormatting.GRAY));
            } else if (level != null && !targetAllowed(level.dimension(), home.dimension(), config)) {
                tooltip.add(Component.translatable("tooltip.essentialcraft5.homeward_compass.home_denied", pos.getX(), pos.getY(), pos.getZ(),
                        home.dimension().location().toString()).withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("tooltip.essentialcraft5.homeward_compass.home_other", pos.getX(), pos.getY(), pos.getZ(),
                        home.dimension().location().toString(), config.crossDimensionMru()).withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.homeward_compass.bind"));
        tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.homeward_compass.use", HoloDecoyProjectorItem.seconds(config.channelTicks())));
        tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.homeward_compass.rules", HoloDecoyProjectorItem.seconds(config.combatLockTicks())));
        tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.homeward_compass.cooldown",
                minutes(config.cooldownTicks()), minutes(config.crossDimensionCooldownTicks())));
    }

    /** 刻 → 分钟：整数不带小数点，其余保留一位（6000 → 5，3000 → 2.5） */
    static String minutes(int ticks) {
        double value = ticks / 1200D;
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
