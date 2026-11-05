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
 * 残破勘测图 {@code torn_survey_map}（第十三批 13d-H3，batch13.md §6.3）：城镇废墟城塔、古代墓穴的箱子另加的一池（12%，每座城镇约 1.5 张；
 * 13e 起沉城、宝库也出），三张加一颗地脉结晶无序合成一张海纳尔勘测图。本身没有用法，只有一行提示。
 */
public class TornSurveyMapItem extends Item {

    public TornSurveyMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.torn_survey_map.desc").withStyle(ChatFormatting.GRAY));
    }
}
