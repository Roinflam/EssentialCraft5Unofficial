package com.flameforge.essentialcraft5.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 魔力单片镜。对应原作 ItemMonocle（magicMonocle）：拿在手里时能看见 MRU（原作 IMRUVisibilityHandler.canSeeMRU 恒为 true）。
 * 看见 MRU 靠 {@code essentialcraft5:mru_vision} 物品标签（tools/tags/ecgear.json），见 api/MRUVision。
 * <p>
 * 移植改动（D12）：
 * <ul>
 *   <li>能戴在头盔槽：实现原版 {@link Equipable}（头部），右键直接戴上，也能在物品栏里放进头盔格；戴着和拿着一样能看见 MRU。
 *       戴在头上时用物品模型的 head 显示变换画成右眼前的一小片镜片（见 ECGear 的物品模型）。</li>
 *   <li>去掉原作的耐久 16：原作从来没有扣过（没有任何地方调 damageItem）。没有耐久以后也就不能附魔（原作附魔能力 10），
 *       原作能附的也只有耐久、经验修补这类对它没用的附魔。</li>
 * </ul>
 */
public class MagicMonocleItem extends Item implements Equipable {

    public MagicMonocleItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_vision.held_or_worn").withStyle(ChatFormatting.DARK_AQUA));
    }
}
