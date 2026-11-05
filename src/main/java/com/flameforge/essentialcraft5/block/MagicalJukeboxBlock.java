package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MagicalJukeboxBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 魔力唱片机。原作 BlockMagicalJukebox（Material.ROCK、MapColor.WOOD，OBJ 模型，registerBlockSimple(…, 1, 1, 0)）。
 * 挖掉时先停掉正在放的唱片（原作 breakBlock 里的 stopRecord），再由基类掉出物品栏。
 */
public class MagicalJukeboxBlock extends MachineBlock<MagicalJukeboxBlockEntity> {

    public MagicalJukeboxBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<MagicalJukeboxBlockEntity> blockEntityType() {
        return ECDevices.MAGICAL_JUKEBOX_BE.get();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof MagicalJukeboxBlockEntity jukebox) {
            jukebox.stopPlaying();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
