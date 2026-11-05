package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.mru.MRUDrain;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 用 MRU 驱动、用不完的三件随身物品（原作 ItemEmeraldHeart、ItemMagicalPorkchop、ItemMagicalWater，各 51 行，写法一样）：
 * 按住右键 {@value #USE_TICKS} 刻，用完扣 MRU 给效果，物品本身不消耗。自带 {@value CharmsMRU#MAX_MRU} MRU，在充能仓里充；
 * 扣费先扣身上的灵魂储存物品（MRUDrain），创造模式不扣。
 * <ul>
 *   <li>绿宝石之心：{@value #HEART_COST} MRU，回复 {@value #HEART_HEAL} 点生命值（吃的动作）。</li>
 *   <li>魔力猪排：{@value #PORKCHOP_COST} MRU，{@code FoodData.eat(5, 3.0)}：饥饿值 +5，饱和度 +30（不超过饥饿值），和原作 addStats 同一个公式。</li>
 *   <li>魔力水瓶：{@value #BOTTLE_COST} MRU，清掉身上所有状态效果，好的坏的都清（喝的动作）。原作 clearActivePotions 就是全清，
 *       不是原版牛奶桶按「可被牛奶治愈」挑着清。和合成材料「魔力水」（MagicalWaterItem）不是一个东西。</li>
 * </ul>
 * 移植改动：身上 MRU 不够时右键直接提示「MRU 不够」、不开始吃（原作照样吃 1.6 秒，吃完什么都没有）。
 * 满血、吃饱时照样能用（原作如此：猪排在饱食时吃可以补饱和度）。
 */
public class MRUConsumableItem extends Item implements CreativeVariants {

    /** 原作 getMaxItemUseDuration */
    public static final int USE_TICKS = 32;
    public static final int HEART_COST = 500;
    public static final float HEART_HEAL = 10F;
    public static final int PORKCHOP_COST = 1500;
    public static final int PORKCHOP_FOOD = 5;
    public static final float PORKCHOP_SATURATION_MODIFIER = 3F;
    public static final int BOTTLE_COST = 500;

    public enum Kind {
        EMERALD_HEART(HEART_COST, UseAnim.EAT),
        MAGICAL_PORKCHOP(PORKCHOP_COST, UseAnim.EAT),
        MAGIC_WATER_BOTTLE(BOTTLE_COST, UseAnim.DRINK);

        private final int cost;
        private final UseAnim animation;

        Kind(int cost, UseAnim animation) {
            this.cost = cost;
            this.animation = animation;
        }

        public int cost() {
            return cost;
        }
    }

    private final Kind kind;

    public MRUConsumableItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return CharmsMRU.storage(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!CharmsMRU.canAfford(player, stack, kind.cost)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.essentialcraft5.charms.no_mru", kind.cost)
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return kind.animation;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) {
            consume(player, stack);
        }
        return stack;
    }

    /** 服务端：扣 MRU 并给效果，返回扣成功没有（GameTest 直接调） */
    public boolean consume(Player player, ItemStack stack) {
        if (!MRUDrain.playerUseMRU(player, stack, kind.cost)) {
            return false;
        }
        switch (kind) {
            case EMERALD_HEART -> player.heal(HEART_HEAL);
            case MAGICAL_PORKCHOP -> player.getFoodData().eat(PORKCHOP_FOOD, PORKCHOP_SATURATION_MODIFIER);
            case MAGIC_WATER_BOTTLE -> player.removeAllEffects();
        }
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
            serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        }
        player.gameEvent(kind.animation == UseAnim.DRINK ? GameEvent.DRINK : GameEvent.EAT);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CharmsMRU.appendMRU(stack, tooltip);
        Component line = switch (kind) {
            case EMERALD_HEART -> Component.translatable("tooltip.essentialcraft5.emerald_heart.use", (int) HEART_HEAL, HEART_COST);
            case MAGICAL_PORKCHOP -> Component.translatable("tooltip.essentialcraft5.magical_porkchop.use", PORKCHOP_FOOD, PORKCHOP_COST);
            case MAGIC_WATER_BOTTLE -> Component.translatable("tooltip.essentialcraft5.magic_water_bottle.use", BOTTLE_COST);
        };
        tooltip.add(line.copy().withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return CharmsMRU.reequip(oldStack, newStack, slotChanged);
    }

    @Override
    public List<ItemStack> creativeVariants() {
        return CharmsMRU.emptyAndFull(new ItemStack(this));
    }
}
