package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.effect.ParanormalLightnessEffect;
import com.flameforge.essentialcraft5.player.WindRelations;
import com.flameforge.essentialcraft5.registry.ECWind;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
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
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 风之药水（原作 ItemLiquidAir）：水瓶在风之符文上注风 250 ESPE 得到。喝下去（32 tick）得到「轻盈」（{@link ParanormalLightnessEffect}），风之好感 +100（× 系数）。
 * 叠喝：第一瓶 I 级 2 分钟，之后每瓶时长 +2 分钟、等级按剩余时长每 8 分钟一级，封顶 III 级、60 分钟（配置）。
 * <p>
 * 和原作的区别：原作按「主手当前格」扣一个（副手喝的扣错了格子），现在扣正在喝的那一组（batch3.md §3.3 修复 3）；
 * 叠喝加了上限（policy §4.2）。原作喝完不还玻璃瓶，照旧；创造模式照原作也扣。
 */
public class AirPotionItem extends Item {

    /** 原作 getMaxItemUseDuration */
    public static final int USE_TICKS = 32;
    /** 喝一瓶的原始好感（× 系数，0.1 时实得 10） */
    public static final int POINTS = 100;

    public AirPotionItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) {
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
            }
            ParanormalLightnessEffect.drink(player);
            WindRelations.grant(player, POINTS, player.getRandom());
            player.awardStat(Stats.ITEM_USED.get(this));
            stack.shrink(1);
        }
        entity.gameEvent(GameEvent.DRINK);
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.air_potion", ECWind.Config.lightnessMaxLevel(), ECWind.Config.lightnessMaxMinutes())
                .withStyle(ChatFormatting.GRAY));
    }
}
