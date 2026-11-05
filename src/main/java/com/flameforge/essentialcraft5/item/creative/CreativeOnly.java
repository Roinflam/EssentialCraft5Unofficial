package com.flameforge.essentialcraft5.item.creative;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 只放创造栏的道具（registry/ECCreative 的无尽道具、MRU 跨维度收发器，policy §4.7）共用的提示。
 * 这些东西没有配方、不进战利品和交易，由服主用指令或商店发给玩家；到手以后生存里照常能用，方块也能用镐挖走。
 * 提示框里先写用途（{@code tooltipKey}，可以带参数），最后一行金色斜体「没有合成配方」。
 */
public final class CreativeOnly {

    /** 「没有合成配方」那一行（键名沿用最初的 creative.only） */
    public static final String TOOLTIP_KEY = "tooltip.essentialcraft5.creative.only";

    private CreativeOnly() {
    }

    public static Component tooltipLine() {
        return Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
    }

    /** 方块物品：用途一行（灰色）+「没有合成配方」，史诗稀有度（名字紫色） */
    public static BlockItem blockItem(Block block, String usageKey, Object... args) {
        return new CreativeBlockItem(block, usageKey, args);
    }

    static final class CreativeBlockItem extends BlockItem {
        private final String usageKey;
        private final Object[] args;

        CreativeBlockItem(Block block, String usageKey, Object... args) {
            super(block, new Properties().rarity(Rarity.EPIC));
            this.usageKey = usageKey;
            this.args = args;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable(usageKey, args).withStyle(ChatFormatting.GRAY));
            tooltip.add(tooltipLine());
        }
    }
}
