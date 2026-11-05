package com.flameforge.essentialcraft5.api;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 饰品对击杀所得 UBMRU 的修正。对应原作 IUBMRUGainModifyHandler（原作注释「Not yet a capability」，这里同样是物品接口）。
 * <p>
 * 玩家击杀非玩家生物时，身上 Curios 饰品栏里每件实现了本接口的物品依次修正一遍（前一件的结果传给后一件，每次向下取整），
 * 只在所得大于 0 时调用，见 {@code player.UBMRUEvents}。从玩家身上夺来的 UBMRU 不经过这里（原作经过，×2 饰品互杀会无限翻倍）。
 * 第一批没有物品实现它，饰品批再接。
 */
public interface IUBMRUGainModifyHandler {

    /**
     * @param original 修正前的 UBMRU
     * @param stack    实现本接口的饰品
     * @return 修正后的 UBMRU（调用方向下取整）
     */
    float getModifiedValue(float original, ItemStack stack, RandomSource random, Player player);
}
