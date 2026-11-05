package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 灵魂五芒星（原作 BlockPlayerPentacle）：地上一张 1/16 高的符文。搭好结构（{@link ECPentacle#TIER_PATTERNS}）后右键打开界面，
 * 花周围水晶柱里的 ESPE 治自己身上的腐化效果（见 {@link PlayerPentacleBlockEntity}、menu/PlayerPentacleMenu）。
 * <p>
 * 右键开界面、潜行不开、记放置者当主人都是 {@link MachineBlock} 做的（主人这台用不到：五芒星不动世界）。
 * 原作 breakBlock 把方块实体强转成 IInventory 再掉物品，五芒星根本没有物品栏，一挖就抛 ClassCastException（batch3 §3.7 修 2）；
 * 这里的方块实体没有物品栏，MachineBlock.onRemove 什么都不会掉，方块本身按战利品表正常掉落。
 * 不透光度 0（原作），传 transmitsSkylight。
 */
public class PlayerPentacleBlock extends MachineBlock<PlayerPentacleBlockEntity> {

    /** 原作 BLOCK_AABB (0, 0, 0, 1, 0.0625, 1) */
    private static final VoxelShape SHAPE = Block.box(0D, 0D, 0D, 16D, 1D, 16D);

    public PlayerPentacleBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<PlayerPentacleBlockEntity> blockEntityType() {
        return ECPentacle.PLAYER_PENTACLE_BE.get();
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
