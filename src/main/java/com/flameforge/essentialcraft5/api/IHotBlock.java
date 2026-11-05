package com.flameforge.essentialcraft5.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 热力产能器的热源。对应原作 {@code essentialcraft.api.IHotBlock}。
 * <p>
 * 热力产能器看东南西北<b>相隔一格</b>的四个方块，把它们的系数连乘再乘基础产量。
 * 原版方块的系数写死在 HeatGeneratorBlockEntity 里（空气 0、下界岩 0.75、岩浆 0.95、火 0.7、其它 0.5）；
 * 别的方块实现这个接口就能给出自己的系数。原作参数是 IBlockAccess + 坐标，这里多带一个方块状态，省得再查一次。
 */
public interface IHotBlock {

    /** @return 产量乘数（1 表示不衰减） */
    float getHeatModifier(BlockGetter level, BlockPos pos, BlockState state);
}
