package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.api.IColdBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 霜冻石：冷凝器的冷源，冷值 0.5。对应原作 BlockColdStone（Material.ICE，setLightOpacity(0)）。
 * <p>
 * 冷凝器查冷值：{@code state.getBlock() instanceof IColdBlock cold ? cold.getColdModifier(level, pos, state) : 原版表}。
 * <p>
 * 原作是整块不透明的立方体，但不挡光。1.20.1 要不挡光得去掉遮挡（注册时 noOcclusion）并让天空光直接穿过，
 * 否则 noOcclusion 的方块仍会挡 1 级光（规则同 MachineBlock 的 transmitsSkylight）。
 * AO 保持原样（原作 isFullCube 为 true，相邻方块的环境光遮蔽照常）。
 */
public class ColdStoneBlock extends Block implements IColdBlock {

    /** 原作 getColdModifier 的返回值 */
    public static final float COLD_MODIFIER = 0.5F;

    public ColdStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public float getColdModifier(BlockGetter level, BlockPos pos, BlockState state) {
        return COLD_MODIFIER;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
