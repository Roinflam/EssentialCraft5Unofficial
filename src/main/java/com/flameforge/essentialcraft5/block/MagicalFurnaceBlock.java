package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MagicalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 魔力熔炉。对应原作 BlockMagicalFurnace（Material.ROCK、整块 cube_top：顶面 magic_furnace_top、侧面强化石，不透光度 0）。
 * 右键开界面（只有一格 MRU 绑定宝石）、潜行右键不开、挖掉撒出宝石，都由 {@link MachineBlock} 做；逻辑在 {@link MagicalFurnaceBlockEntity}。
 * <p>
 * 正下方那块虚空石变了时让结构缓存标脏（四角和产能器离得远，靠 20 刻复查）。
 */
public class MagicalFurnaceBlock extends MachineBlock<MagicalFurnaceBlockEntity> {

    public MagicalFurnaceBlock(Properties properties) {
        // 原作 setLightOpacity(0)：天空光直接穿过
        super(properties, true);
    }

    @Override
    protected BlockEntityType<MagicalFurnaceBlockEntity> blockEntityType() {
        return ECVoidMachines.MAGICAL_FURNACE_BE.get();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MagicalFurnaceBlockEntity be) {
            be.structure().markDirty();
        }
    }
}
