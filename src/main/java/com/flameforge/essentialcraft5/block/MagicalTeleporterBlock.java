package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MagicalTeleporterBlockEntity;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 魔力传送器。对应原作 BlockMagicalTeleporter（Material.ROCK、MapColor.PURPLE、整块 cube_top：顶面 magic_teleporter_top、侧面魔力护板，不透光度 0）。
 * 右键开界面、潜行右键不开、挖掉撒出两颗宝石，都由 {@link MachineBlock} 做；逻辑在 {@link MagicalTeleporterBlockEntity}。
 * <p>
 * 紧挨着的四格虚空石变了时让结构缓存标脏（离得远的柱子靠 20 刻复查）。
 */
public class MagicalTeleporterBlock extends MachineBlock<MagicalTeleporterBlockEntity> {

    public MagicalTeleporterBlock(Properties properties) {
        // 原作 setLightOpacity(0)：天空光直接穿过
        super(properties, true);
    }

    @Override
    protected BlockEntityType<MagicalTeleporterBlockEntity> blockEntityType() {
        return ECVoidMachines.MAGICAL_TELEPORTER_BE.get();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MagicalTeleporterBlockEntity be) {
            be.structure().markDirty();
        }
    }
}
