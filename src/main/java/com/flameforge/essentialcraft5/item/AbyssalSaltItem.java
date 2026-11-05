package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECHoannaSurvey;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 渊盐 {@code abyssal_salt}（第十三批 13d-H3，batch13.md §6.3「渊盐」、D33 A；HS §2.3.3）：铲子挖海纳尔之海海底的渊盐结壳得到，
 * 像食物一样吃（{@value #USE_TICKS} 刻，饱了也能吃，不给饱食度）：
 * <ul>
 *   <li>剂量减少「当前剂量 × {@code purge_ratio}」，至少 {@code purge_min}、至多 {@code purge_max}，不低于 0（{@link #purgeAmount}）；</li>
 *   <li>冷却 {@code cooldown_ticks}：以玩家数据 {@code abyssal_salt_cd}（到期时的<b>主世界</b> gameTime）为准，物品冷却只是给客户端看的，
 *       登录、重生时按剩余时间补上（{@link #restoreCooldown}）；冷却中吃不下去；</li>
 *   <li>副作用饥饿 I {@value #HUNGER_TICKS} 刻；不在海纳尔吃也有效；λ 指环不变。</li>
 * </ul>
 * 每 10 分钟吃一次时普通群系清干净、恐惧之地稳在约 1 级、热点稳在约 3 级，辐射不会变成摆设。
 * 只有服务端的真玩家吃得了：假玩家（机械手之类）右键直接放过，不扣物品。
 */
public class AbyssalSaltItem extends Item {

    /** 吃一口多少刻（同原版食物） */
    public static final int USE_TICKS = 32;
    /** 饥饿 I 多少刻（30 秒） */
    public static final int HUNGER_TICKS = 600;

    public static final String MSG_COOLDOWN = "message.essentialcraft5.abyssal_salt.cooldown";
    public static final String MSG_PURGED = "message.essentialcraft5.abyssal_salt.purged";

    public AbyssalSaltItem(Properties properties) {
        super(properties);
    }

    /**
     * 这一口清掉多少（纯函数）：round(dose × ratio) 夹在 [min, max] 里（max 比 min 小时按 min），再不超过 dose 本身。
     */
    public static long purgeAmount(long dose, double ratio, long min, long max) {
        if (dose <= 0L) {
            return 0L;
        }
        long low = Math.max(0L, min);
        long high = Math.max(low, max);
        double safeRatio = Double.isFinite(ratio) ? Math.max(0D, ratio) : 0D;
        long amount = Math.round(dose * safeRatio);
        amount = Math.max(low, Math.min(high, amount));
        return Math.min(dose, amount);
    }

    /** 主世界的 gameTime（冷却一律按它比，混合端插件建的世界 gameTime 不一样；没有主世界时用玩家所在世界的） */
    public static long now(ServerPlayer player) {
        return player.server.overworld() != null ? player.server.overworld().getGameTime() : player.level().getGameTime();
    }

    /** 还剩多少刻冷却（0 = 能吃）；不超过现在配置的冷却（服主调短了就跟着短） */
    public static long remainingCooldown(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return 0L;
        }
        long left = data.getAbyssalSaltCooldownEnd() - now(player);
        return Math.max(0L, Math.min(left, ECHoannaSurvey.Config.values().cooldownTicks()));
    }

    /** 按玩家数据补上物品冷却（登录、重生后物品冷却丢了，图标上的灰条靠它）；返回补了多少刻 */
    public static int restoreCooldown(ServerPlayer player) {
        long left = remainingCooldown(player);
        if (left <= 0L) {
            return 0;
        }
        int ticks = (int) Math.min(Integer.MAX_VALUE, left);
        player.getCooldowns().addCooldown(ECHoannaSurvey.ABYSSAL_SALT.get(), ticks);
        return ticks;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!ECProtection.isRealPlayer(serverPlayer)) {
                return InteractionResultHolder.pass(stack);
            }
            long left = remainingCooldown(serverPlayer);
            if (left > 0L) {
                restoreCooldown(serverPlayer);
                serverPlayer.displayClientMessage(Component.translatable(MSG_COOLDOWN, (left + 19L) / 20L), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || !ECProtection.isRealPlayer(player)) {
            return stack;
        }
        if (remainingCooldown(player) > 0L) {
            return stack;
        }
        eat(player);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }

    /**
     * 吃下一颗的效果（不扣物品、不查冷却）：清剂量、记冷却、物品冷却、饥饿 I、统计、音效、动作栏。返回清掉了多少剂量。
     * GameTest 直接调。
     */
    public static int eat(ServerPlayer player) {
        ECHoannaSurvey.Config.Values values = ECHoannaSurvey.Config.values();
        int dose = Radiation.get(player);
        long purged = purgeAmount(dose, values.purgeRatio(), values.purgeMin(), values.purgeMax());
        int after = Radiation.set(player, dose - purged);
        IPlayerData data = PlayerDataEvents.get(player);
        int cooldown = values.cooldownTicks();
        if (data != null) {
            data.setAbyssalSaltCooldownEnd(cooldown > 0 ? now(player) + cooldown : 0L);
        }
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(ECHoannaSurvey.ABYSSAL_SALT.get(), cooldown);
        }
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, HUNGER_TICKS, 0));
        player.awardStat(Stats.ITEM_USED.get(ECHoannaSurvey.ABYSSAL_SALT.get()));
        player.level().gameEvent(player, GameEvent.EAT, player.position());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F,
                player.getRandom().nextFloat() * 0.1F + 0.9F);
        player.displayClientMessage(Component.translatable(MSG_PURGED, dose - after, after).withStyle(ChatFormatting.AQUA), true);
        return dose - after;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ECHoannaSurvey.Config.Values values = ECHoannaSurvey.Config.values();
        tooltip.add(Component.translatable("tooltip.essentialcraft5.abyssal_salt.desc", Math.round(values.purgeRatio() * 100D), values.purgeMin(),
                values.purgeMax()).withStyle(ChatFormatting.GRAY));
        int minutes = (values.cooldownTicks() + 1199) / 1200;
        tooltip.add(Component.translatable("tooltip.essentialcraft5.abyssal_salt.cooldown", minutes).withStyle(ChatFormatting.DARK_GRAY));
    }
}
