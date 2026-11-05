package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.item.ElementalCrystalItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 手册里「画出来看不见」的物品换一个显示用的替身
 *
 * <h3>为什么要有</h3>
 * <p>元素水晶的图标按大小分 21 档（{@link ElementalCrystalItem#stage}），第 0 档是原作那张<b>全透明</b>的贴图：
 * 不带数据的默认栈（条目图标、侧栏、物品块、月亮井配方里的原料格）画出来就是一个空格子。
 * 手册里把它画成长成的水晶（大小 100、不带元素，颜色是原作的中性色），提示仍按默认栈显示（{@link #forTooltip}），
 * 查阅、链接按物品（不看数据）找，都不受影响。</p>
 *
 * <p>只在排版时（建图标、物品块、配方图）调用，渲染路径不分配。改内容文件解决不了配方原料格（那是从配方表取的），
 * 所以放在引擎里，内容照常写 {@code essentialcraft5:elemental_crystal}。</p>
 */
@OnlyIn(Dist.CLIENT)
final class GuideStacks {

    /** 替身的标记：替身栈的根标签里有这个键（提示时换回默认栈） */
    private static final String MARK = "ec5_guide_display";

    private GuideStacks() {}

    /**
     * 显示用的栈：需要替身的换成替身（数量照旧），其余原样返回同一个对象
     *
     * @param stack 原栈（不修改）
     * @return 显示用的栈
     */
    static ItemStack display(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ElementalCrystalItem) || ElementalCrystalItem.stage(stack) > 0) {
            return stack;
        }
        ItemStack shown = ElementalCrystalItem.create(IElementalCrystal.MAX_SIZE, 0D, 0D, 0D, 0D);
        shown.setCount(stack.getCount());
        shown.getOrCreateTag().putBoolean(MARK, true);
        return shown;
    }

    /**
     * 一组候选物品里有要换的就换（返回新数组），没有就原样返回同一个数组（配方原料的数组是配方自己的，不能改）
     */
    static ItemStack[] display(ItemStack[] stacks) {
        ItemStack[] out = null;
        for (int i = 0; i < stacks.length; i++) {
            ItemStack shown = display(stacks[i]);
            if (shown != stacks[i]) {
                if (out == null) {
                    out = stacks.clone();
                }
                out[i] = shown;
            }
        }
        return out == null ? stacks : out;
    }

    /**
     * 物品提示用的栈：替身换回不带数据的默认栈（元素水晶的提示会写大小，替身的「大小 100、已长成」不该出现在
     * 「月亮井要一颗水晶，大小不限」的原料格上）。每次悬停只在提示那一帧建一次，和提示本身的分配同量级
     */
    static ItemStack forTooltip(ItemStack stack) {
        if (!stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(MARK)) {
            return new ItemStack(stack.getItem(), stack.getCount());
        }
        return stack;
    }
}
