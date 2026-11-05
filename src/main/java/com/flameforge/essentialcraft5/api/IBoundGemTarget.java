package com.flameforge.essentialcraft5.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 多格方块用：绑定宝石右键这个方块时，实际记下哪个坐标。
 * <p>
 * 原作 ItemBoundGem / ItemInventoryGem 里写死了「点在射线塔上半格时绑到下半格」（方块实体只在下半格）。
 * 这里改成方块自己声明：BoundGemItem.useOn 取点击处的方块，实现了本接口就换成返回的坐标。
 */
public interface IBoundGemTarget {

    /**
     * @param pos   被点击的坐标
     * @param state 被点击处的方块状态
     * @return 宝石要记下的坐标（一般是存 MRU 的那一格）
     */
    BlockPos boundGemTarget(BlockGetter level, BlockPos pos, BlockState state);
}
