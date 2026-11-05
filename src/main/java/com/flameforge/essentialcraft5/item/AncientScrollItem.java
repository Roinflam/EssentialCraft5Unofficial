package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.client.ScrollsClient;
import com.flameforge.essentialcraft5.lore.ScrollVolumes;
import com.flameforge.essentialcraft5.registry.ECScrolls;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 源质残卷（第十二批，batch12.md §4.1.2；卷数照 §10.1 D1 的定稿：序 + 卷一～卷六共 7 卷）：先辈留下的书里的一卷，NBT {@code Volume} 是卷号（0 = 序，1～6 = 卷一～卷六）。
 * <ul>
 *   <li>名字「源质残卷（卷三）」：{@code item.essentialcraft5.ancient_scroll.volume} 拼卷的短名；没有 {@code Volume}（用 contains 判断，0 是有效值）
 *       或越界（指令给出来的）叫「源质残卷（无字）」，不能读。</li>
 *   <li>读：按住右键 {@code general.ancient_scrolls.read_ticks} 刻（没有动作，客户端每 10 刻放一声翻页，只有自己听得见）。
 *       读完在服务端发这一卷的进度、按配置消耗一张（创造模式不消耗）、动作栏提示、再判一次集齐（{@link ScrollVolumes#tryAwardComplete}）。</li>
 *   <li>读过的卷：客户端按自己收到的进度直接不起手，服务端在起手和读完时各再判一次兜底；动作栏提示可以送人，不消耗。
 *       进度按玩家记，别人读了不算你的。</li>
 *   <li>提示框：卷名、已读 / 未读（客户端按自己的进度判断）、未读时「按住右键阅读」。</li>
 * </ul>
 * 蜡封（模型 {@code layer1}）按卷着色，见 {@link #tint}，颜色登记在 {@code client/ScrollsClient}。
 */
public class AncientScrollItem extends Item implements CreativeVariants {

    /** 七卷蜡封的颜色（batch12.md §4.1.5 的顺序给 0～6）：棕、紫、青、绿、橙、灰、红 */
    private static final int[] SEAL_COLORS = {0x8B5A2B, 0x8E5CC9, 0x6FC7C2, 0x5FBF3F, 0xD9822B, 0xA8A29A, 0xB0263A};
    /** 无字残卷的蜡封：暗灰 */
    private static final int BLANK_SEAL = 0x5A5A5A;
    /** 每隔多少刻放一声翻页 */
    private static final int PAGE_SOUND_INTERVAL = 10;

    public static final String NAME_VOLUME = "item.essentialcraft5.ancient_scroll.volume";
    public static final String NAME_BLANK = "item.essentialcraft5.ancient_scroll.blank";
    public static final String TOOLTIP_READ = "tooltip.essentialcraft5.ancient_scroll.read";
    public static final String TOOLTIP_UNREAD = "tooltip.essentialcraft5.ancient_scroll.unread";
    public static final String TOOLTIP_HOLD = "tooltip.essentialcraft5.ancient_scroll.hold_to_read";
    public static final String TOOLTIP_BLANK = "tooltip.essentialcraft5.ancient_scroll.blank";
    public static final String MSG_FINISHED = "message.essentialcraft5.ancient_scroll.finished";
    /** 读完的「不提手册」版：general.ancient_scrolls.enabled 关掉时「古卷」整章藏掉，不能再说「手册里多了一篇」 */
    public static final String MSG_FINISHED_PLAIN = "message.essentialcraft5.ancient_scroll.finished_plain";
    public static final String MSG_ALREADY_READ = "message.essentialcraft5.ancient_scroll.already_read";
    public static final String MSG_BLANK = "message.essentialcraft5.ancient_scroll.blank";

    public AncientScrollItem(Properties properties) {
        super(properties);
    }

    /** 某一卷的残卷（count 张）；卷号无效时给无字的 */
    public static ItemStack of(int volume, int count) {
        ItemStack stack = new ItemStack(ECScrolls.ANCIENT_SCROLL.get(), count);
        if (ScrollVolumes.valid(volume)) {
            stack.getOrCreateTag().putInt(ScrollVolumes.TAG, volume);
        }
        return stack;
    }

    /** 残卷的卷号；不是残卷、没有 {@code Volume}、越界都返回 −1 */
    public static int volume(ItemStack stack) {
        if (!(stack.getItem() instanceof AncientScrollItem)) {
            return -1;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ScrollVolumes.TAG, Tag.TAG_ANY_NUMERIC)) {
            return -1;
        }
        int volume = tag.getInt(ScrollVolumes.TAG);
        return ScrollVolumes.valid(volume) ? volume : -1;
    }

    /** 物品颜色：layer0 纸不染，layer1 蜡封按卷 */
    public static int tint(ItemStack stack, int layer) {
        if (layer != 1) {
            return -1;
        }
        int volume = volume(stack);
        return volume < 0 ? BLANK_SEAL : SEAL_COLORS[volume];
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int volume = volume(stack);
        if (volume < 0) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(MSG_BLANK), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (alreadyRead(level, player, volume)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(MSG_ALREADY_READ), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 客户端看自己收到的进度，服务端看真正的进度 */
    private static boolean alreadyRead(Level level, Player player, int volume) {
        if (level.isClientSide) {
            return Boolean.TRUE.equals(DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ScrollsClient.isRead(volume)));
        }
        return player instanceof ServerPlayer server && ScrollVolumes.isRead(server, volume);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return ECScrolls.Config.values().readTicks();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /** 读的时候客户端每 10 刻放一声翻页：只放给拿着它的本人（别人的客户端也会走这里，所以要判断是不是本地玩家） */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide || !(entity instanceof Player player) || !player.isLocalPlayer()) {
            return;
        }
        if ((getUseDuration(stack) - remaining) % PAGE_SOUND_INTERVAL == 0) {
            level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS,
                    1F, 0.9F + level.random.nextFloat() * 0.2F, false);
        }
    }

    /** 读完（服务端）：发进度 → 消耗 → 动作栏 → 判集齐。读过的（起手以后才在别处读完同一卷）不消耗、不重发 */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return stack;
        }
        int volume = volume(stack);
        if (volume < 0) {
            return stack;
        }
        if (!ScrollVolumes.award(player, volume)) {
            player.displayClientMessage(Component.translatable(MSG_ALREADY_READ), true);
            return stack;
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        if (ECScrolls.Config.values().consumeOnRead() && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.displayClientMessage(Component.translatable(finishedMessageKey(ECScrolls.Config.values().enabled()), ScrollVolumes.label(volume)), true);
        ScrollVolumes.tryAwardComplete(player);
        return stack;
    }

    /** 读完一卷的动作栏用哪句：手册「古卷」章显示着（general.ancient_scrolls.enabled）才提它 */
    public static String finishedMessageKey(boolean chapterShown) {
        return chapterShown ? MSG_FINISHED : MSG_FINISHED_PLAIN;
    }

    @Override
    public Component getName(ItemStack stack) {
        int volume = volume(stack);
        return volume < 0 ? Component.translatable(NAME_BLANK) : Component.translatable(NAME_VOLUME, ScrollVolumes.label(volume));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int volume = volume(stack);
        if (volume < 0) {
            tooltip.add(Component.translatable(TOOLTIP_BLANK).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable(ScrollVolumes.titleKey(volume)).withStyle(ChatFormatting.LIGHT_PURPLE));
        // 已读 / 未读只有客户端知道（按本人收到的进度）；服务端拼提示（极少见）时不写这两行
        Boolean read = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ScrollsClient.isRead(volume));
        if (read == null) {
            return;
        }
        if (read) {
            tooltip.add(Component.translatable(TOOLTIP_READ).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(TOOLTIP_UNREAD).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable(TOOLTIP_HOLD).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public List<ItemStack> creativeVariants() {
        List<ItemStack> out = new ArrayList<>(ScrollVolumes.COUNT);
        for (int v = 0; v < ScrollVolumes.COUNT; v++) {
            out.add(of(v, 1));
        }
        return out;
    }
}
