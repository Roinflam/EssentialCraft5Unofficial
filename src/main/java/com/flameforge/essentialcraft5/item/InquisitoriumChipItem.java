package com.flameforge.essentialcraft5.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 审判庭芯片（{@code inquisitorium_chip}，第十批 10c，batch10.md §5.10.5）：打倒审判庭哨兵必掉 1 个（只在玩家打倒时，{@code entities/inquisitorium_sentinel}）。
 * 第二十一章空白模块的料（一个芯片出两块），审判庭套装每件也要一个。普通物品，没有合成配方；提示行写它是哪来的、做什么用。
 */
public class InquisitoriumChipItem extends Item {

    public InquisitoriumChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.inquisitorium_chip").withStyle(ChatFormatting.GRAY));
    }
}
