package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.registry.ECDecorations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 花纹拟态的方块实体（原作共用 TileMimic）：只换方块实体类型（{@code essentialcraft5:fancy_mimic}，16 种花纹拟态共用），
 * 存读档（键 {@code camo}）、更新包、模型数据、{@code setCamo} 全部继承 {@link MimicBlockEntity}（框架 F5-2 的带类型构造器）。
 */
public class FancyMimicBlockEntity extends MimicBlockEntity {

    public FancyMimicBlockEntity(BlockPos pos, BlockState state) {
        super(ECDecorations.FANCY_MIMIC_BE.get(), pos, state);
    }
}
