package com.flameforge.essentialcraft5.item.module;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 空白模块 {@code blank_module}（batch10.md §4.3 第 9 条）：六块宅男套装模块的底料，本身没有效果、装不上盔甲，只多一行说明。
 * 配方（D8 A）：审判庭芯片 + 富神经元护板 ×2 + 逻辑核心 + 恶魔核心 ×2 + 虚空板 ×2 → 2 个。
 */
public class BlankModuleItem extends Item {

    public static final String KEY_DESC = "tooltip.essentialcraft5.blank_module";

    public BlankModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(KEY_DESC).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
