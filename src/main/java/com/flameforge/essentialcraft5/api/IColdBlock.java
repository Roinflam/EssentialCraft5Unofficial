package com.flameforge.essentialcraft5.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 冷凝器的冷源。对应原作 {@code essentialcraft.api.IColdBlock}（原作由霜冻石和花纹霜冻石实现，冷值 0.5）。
 * <p>
 * 冷凝器把周围 7×7×7 范围内所有方块的冷值加起来，向下取整就是每 tick 的产量。
 * 原版方块的冷值写死在 ColdDistillatorBlockEntity 里（蓝冰 0.4、浮冰 0.3、雪块和细雪 0.2、冰 0.15、雪 0.05）；
 * 实现了这个接口的方块以接口给出的值为准。原作参数是 IBlockAccess + 坐标，这里多带一个方块状态。
 */
public interface IColdBlock {

    /** @return 这个方块给冷凝器贡献的每 tick 产量（可以是小数，累加后才取整） */
    float getColdModifier(BlockGetter level, BlockPos pos, BlockState state);
}
