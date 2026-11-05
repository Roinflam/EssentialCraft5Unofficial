package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.MRUPresenceIndex;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CLeyScanPacket;
import com.flameforge.essentialcraft5.registry.ECLeyDetector;
import com.flameforge.essentialcraft5.registry.ECMRUGolem;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * 地脉探测仪（第十二批，batch12.md §4.3；注册名 {@code ley_detector}，原作没有）。自带 MRU（{@code general.ley_detector.max_mru}，默认 1 万），
 * 充能仓能充，创造栏放空 / 满两份（{@link MRUStaffItem}）。
 * <ul>
 *   <li><b>右键</b>（只在服务端结算）：只认真玩家；冷却中（物品冷却，原版在调 {@code use} 之前就拦了）按不动；
 *       扣不起 {@code mru_per_scan}（默认 1 500，{@code MRUDrain}：先扣身上的储存物品，凑不够再扣探测仪自己的，创造模式不扣）就在动作栏说一句、不扣；
 *       扣得起就扣、上冷却 {@code cooldown_ticks}（默认 200），给<b>本人</b>发一个 {@link S2CLeyScanPacket}，只给本人放一声紫水晶块的叮。
 *       扫描在客户端（{@code client/ley/LeyScanClient}），服务端不读一格方块。冷却只是物品冷却、不存盘：10 秒的冷却重进游戏清掉也无所谓。</li>
 *   <li><b>潜行右键</b>：「全部矿石」→「只看贵重」→「聚集体」→「全部矿石」轮着切（物品 NBT {@value #TAG_VALUABLE_ONLY} / {@value #TAG_PRESENCE}，
 *       「全部矿石」两个键都不留），不花 MRU、不上冷却，动作栏写现在的模式。只在服务端改 NBT，客户端等物品栏同步，免得两边各切一次。</li>
 *   <li><b>「聚集体」档</b>（第十三批 13c，batch13.md §5.3.1，给找上古遗迹用）：右键的花费、冷却、真玩家要求和扫矿一样，但不发扫描包：
 *       服务端用 {@link MRUPresenceIndex} 找 {@code general.mobs.mru_golem.detector_radius}（64）格内最近、MRU 不少于 {@code detector_min_mru}（5 000）
 *       的一团（只查已加载的区块），动作栏写方位、水平距离、上下差和它的 MRU，只给本人在那个方向冒一串青色的点。</li>
 * </ul>
 * 配置关掉（{@code enabled = false}）时不潜行的右键没反应，切模式照样能切。
 */
public class LeyDetectorItem extends MRUStaffItem {

    /** 物品 NBT：只看贵重矿 */
    public static final String TAG_VALUABLE_ONLY = "ValuableOnly";
    /** 物品 NBT：「聚集体」档 */
    public static final String TAG_PRESENCE = "PresenceMode";

    public static final String MSG_MODE_ALL = "message.essentialcraft5.ley_detector.mode_all";
    public static final String MSG_MODE_VALUABLE = "message.essentialcraft5.ley_detector.mode_valuable";
    public static final String MSG_DISABLED = "message.essentialcraft5.ley_detector.disabled";
    public static final String MSG_MODE_PRESENCE = "message.essentialcraft5.ley_detector.mode_presence";
    public static final String MSG_PRESENCE_FOUND = "message.essentialcraft5.ley_detector.presence_found";
    public static final String MSG_PRESENCE_HERE = "message.essentialcraft5.ley_detector.presence_here";
    public static final String MSG_PRESENCE_NONE = "message.essentialcraft5.ley_detector.presence_none";
    public static final String MSG_PRESENCE_ABOVE = "message.essentialcraft5.ley_detector.presence_above";
    public static final String MSG_PRESENCE_BELOW = "message.essentialcraft5.ley_detector.presence_below";
    public static final String MSG_PRESENCE_LEVEL = "message.essentialcraft5.ley_detector.presence_level";

    /** 「聚集体」档指路的点（聚集体的青蓝） */
    private static final DustParticleOptions POINTER = new DustParticleOptions(new Vector3f(0.35F, 0.85F, 1F), 0.8F);

    /** 只给 GameTest 数：服务端发出去的扫描包一共几个（只在服务端线程加） */
    private static int testScansSent;

    public LeyDetectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, ECLeyDetector.Config.values().maxMru());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                String message;
                if (presenceMode(stack)) {
                    setPresenceMode(stack, false);
                    message = MSG_MODE_ALL;
                } else if (valuableOnly(stack)) {
                    setPresenceMode(stack, true);
                    message = MSG_MODE_PRESENCE;
                } else {
                    setValuableOnly(stack, true);
                    message = MSG_MODE_VALUABLE;
                }
                player.displayClientMessage(Component.translatable(message).withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        ECLeyDetector.Config.Values config = ECLeyDetector.Config.values();
        if (!config.enabled()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(MSG_DISABLED).withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide && !ECProtection.isRealPlayer(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!MRUDrain.canAfford(player, stack, config.mruPerScan())) {
            if (!level.isClientSide) {
                tellNoMRU(player, config.mruPerScan());
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        boolean done = presenceMode(stack) ? locate((ServerPlayer) player, stack, config) : scan((ServerPlayer) player, stack, config);
        return done ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    /**
     * 「聚集体」档：扣钱、上冷却，找最近的一团、动作栏说在哪，只给本人指个方向、放一声叮（找不到时低一点）。调用方已经确认过扣得起。
     *
     * @return 扣成了没有（找没找到都算用过一次）
     */
    private boolean locate(ServerPlayer player, ItemStack stack, ECLeyDetector.Config.Values config) {
        if (!MRUDrain.playerUseMRU(player, stack, config.mruPerScan())) {
            return false;
        }
        if (config.cooldownTicks() > 0) {
            player.getCooldowns().addCooldown(this, config.cooldownTicks());
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        ECMRUGolem.Config.Detector detector = ECMRUGolem.Config.values().detector();
        MRUPresenceEntity found = nearestPresence(player);
        player.displayClientMessage(describe(player.position(), found, detector), true);
        if (found != null) {
            pointAt(player, found.position());
        }
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, found != null ? 1.2F : 0.6F);
        return true;
    }

    /** 「聚集体」档会指的那一团：玩家 {@code detector_radius} 格内（球）、MRU 不少于 {@code detector_min_mru} 的最近一团，只查已加载的。服务端 */
    @Nullable
    public static MRUPresenceEntity nearestPresence(Player player) {
        ECMRUGolem.Config.Detector detector = ECMRUGolem.Config.values().detector();
        return MRUPresenceIndex.nearest(player.level(), player.position(), detector.radius(), detector.minMru(), false);
    }

    /** 动作栏的那句话：方位（八方位）、水平距离、上下差（方块层）、它的 MRU；水平不到 2 格时只说上下；没找到说范围和门槛 */
    public static Component describe(Vec3 from, @Nullable MRUPresenceEntity found, ECMRUGolem.Config.Detector detector) {
        if (found == null) {
            return Component.translatable(MSG_PRESENCE_NONE, detector.radius(), detector.minMru()).withStyle(ChatFormatting.GRAY);
        }
        double dx = found.getX() - from.x;
        double dz = found.getZ() - from.z;
        // 按两边所在的方块层相减（看 F3 坐标对得上）：差 1 层以内说「差不多高」
        int dy = Mth.floor(found.getY()) - Mth.floor(from.y);
        Component vertical = dy >= 2 ? Component.translatable(MSG_PRESENCE_ABOVE, dy)
                : dy <= -2 ? Component.translatable(MSG_PRESENCE_BELOW, -dy) : Component.translatable(MSG_PRESENCE_LEVEL);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 2D) {
            return Component.translatable(MSG_PRESENCE_HERE, vertical, found.getMRU()).withStyle(ChatFormatting.AQUA);
        }
        Component heading = Component.translatable(InquisitoriumCoordinatesItem.headingKey(InquisitoriumCoordinatesItem.heading(dx, dz)));
        return Component.translatable(MSG_PRESENCE_FOUND, heading, (int) Math.round(horizontal), vertical, found.getMRU()).withStyle(ChatFormatting.AQUA);
    }

    /** 从眼前往目标方向冒一串点（1~4 格），只发给这名玩家 */
    private static void pointAt(ServerPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        Vec3 direction = target.subtract(eye);
        if (direction.lengthSqr() < 1.0E-4D) {
            return;
        }
        direction = direction.normalize();
        for (int i = 0; i < 12; i++) {
            Vec3 at = eye.add(direction.scale(1D + i * 0.25D));
            player.serverLevel().sendParticles(player, POINTER, true, at.x, at.y, at.z, 1, 0D, 0D, 0D, 0D);
        }
    }

    /**
     * 服务端：扣钱、上冷却、给本人发包和声音。调用方已经确认过扣得起（同一刻里没有别的东西动 MRU，一定扣得到）。
     *
     * @return 扫成了没有
     */
    private boolean scan(ServerPlayer player, ItemStack stack, ECLeyDetector.Config.Values config) {
        if (!MRUDrain.playerUseMRU(player, stack, config.mruPerScan())) {
            return false;
        }
        if (config.cooldownTicks() > 0) {
            player.getCooldowns().addCooldown(this, config.cooldownTicks());
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        ECNetwork.sendTo(player, new S2CLeyScanPacket(config.radius(), config.displayTicks(), config.maxHighlights(), valuableOnly(stack)));
        testScansSent++;
        // playNotifySound 只发给这名玩家，旁边的人听不见
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1F);
        return true;
    }

    public static boolean valuableOnly(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_VALUABLE_ONLY) && !tag.getBoolean(TAG_PRESENCE);
    }

    /** 「全部矿石」时删掉这个键，NBT 和新做出来的一样（新旧两件能叠在配方里认作同一种）；设「只看贵重」时顺带退出「聚集体」档 */
    public static void setValuableOnly(ItemStack stack, boolean valuable) {
        if (valuable) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putBoolean(TAG_VALUABLE_ONLY, true);
            tag.remove(TAG_PRESENCE);
        } else if (stack.getTag() != null) {
            stack.getTag().remove(TAG_VALUABLE_ONLY);
        }
    }

    /** 是不是「聚集体」档 */
    public static boolean presenceMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_PRESENCE);
    }

    /** 进 / 出「聚集体」档；进的时候去掉「只看贵重」，出的时候两个键都不留（回到「全部矿石」） */
    public static void setPresenceMode(ItemStack stack, boolean presence) {
        if (presence) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putBoolean(TAG_PRESENCE, true);
            tag.remove(TAG_VALUABLE_ONLY);
        } else if (stack.getTag() != null) {
            stack.getTag().remove(TAG_PRESENCE);
            stack.getTag().remove(TAG_VALUABLE_ONLY);
        }
    }

    /** 只给 GameTest：到现在为止服务端发了几个扫描包 */
    public static int testScansSent() {
        return testScansSent;
    }

    @Override
    protected void addUsage(ItemStack stack, List<Component> tooltip) {
        ECLeyDetector.Config.Values config = ECLeyDetector.Config.values();
        if (presenceMode(stack)) {
            ECMRUGolem.Config.Detector detector = ECMRUGolem.Config.values().detector();
            tooltip.add(Component.translatable("tooltip.essentialcraft5.ley_detector.mode_presence").withStyle(ChatFormatting.AQUA));
            tooltip.add(usage("tooltip.essentialcraft5.ley_detector.presence_use", detector.radius(), detector.minMru(), config.mruPerScan()));
            tooltip.add(usage("tooltip.essentialcraft5.ley_detector.sneak"));
        } else {
            tooltip.add(Component.translatable(valuableOnly(stack) ? "tooltip.essentialcraft5.ley_detector.mode_valuable"
                    : "tooltip.essentialcraft5.ley_detector.mode_all").withStyle(ChatFormatting.AQUA));
            tooltip.add(usage("tooltip.essentialcraft5.ley_detector.use", config.radius(), config.mruPerScan()));
            tooltip.add(usage("tooltip.essentialcraft5.ley_detector.sneak"));
            tooltip.add(usage("tooltip.essentialcraft5.ley_detector.only_you", HoloDecoyProjectorItem.seconds(config.displayTicks())));
        }
        if (config.cooldownTicks() > 0) {
            tooltip.add(usage("tooltip.essentialcraft5.ley_detector.cooldown", HoloDecoyProjectorItem.seconds(config.cooldownTicks())));
        }
    }
}
