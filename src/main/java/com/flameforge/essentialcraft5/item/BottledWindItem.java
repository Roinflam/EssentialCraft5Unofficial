package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.player.WindRelations;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 瓶中之风 / 禁锢之风 / 受困之风（原作 ItemWindKeeper(10 / 25 / 100)）：只从风法师身上掉（ECWindMage 的掉落表）。
 * 右键打碎瓶子放风出来：玻璃碎的声音，风之好感 + 原始值 × 系数（系数 0.1 时实得 1 / 2 / 10），聊天栏收到一声极轻的「谢谢」。
 * <p>
 * 和原作的区别：原作两端都 shrink（客户端先扣、服务端再扣一次，靠同步盖回来）；现在只在服务端扣（batch3.md §3.3 修复 3）。
 * FakePlayer（机械手）右键不处理、不扣（原作 FakePlayer 加不了好感，白白打碎）。创造模式照原作也扣。
 */
public class BottledWindItem extends Item {

    /** 带风暴余息时的提示（第十三批 13c-M1，{@link WindAffinity#isStormResidue}） */
    public static final String STORM_RESIDUE_TOOLTIP = "tooltip.essentialcraft5.storm_residue";
    public static final String STORM_RESIDUE_REFUSED = "message.essentialcraft5.storm_residue.refused";

    private final int release;

    /** @param release 打碎时给的原始好感（原作 rel） */
    public BottledWindItem(Properties properties, int release) {
        super(properties);
        this.release = release;
    }

    public int release() {
        return release;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof FakePlayer) {
            return InteractionResultHolder.pass(stack);
        }
        if (WindAffinity.isStormResidue(stack)) {
            // 风暴余息（13c-M1）：不给好感、不消耗，提示一句
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(STORM_RESIDUE_REFUSED).withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1F, 1F);
            WindRelations.grant(player, release, player.getRandom());
            player.sendSystemMessage(Component.translatable("message.essentialcraft5.wind_keeper.thanks")
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
            player.awardStat(Stats.ITEM_USED.get(this));
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // 实得按基础系数算（装备、饰品的加成只在服务端算得准）
        if (WindAffinity.isStormResidue(stack)) {
            tooltip.add(Component.translatable(STORM_RESIDUE_TOOLTIP).withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.bottled_wind", release, WindAffinity.scaled(release, WindAffinity.baseCoefficient()))
                .withStyle(ChatFormatting.GRAY));
    }
}
