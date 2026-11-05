package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MagicalMirrorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/**
 * 魔力镜（ECMirror）。原作 BlockMagicalMirror：{@code registerBlockSimple(…, "magicalMirror", 1, 1, 0)}（先抗爆后硬度 → 硬度 1、抗爆 1.0，
 * 透光度 0 → {@code transmitsSkylight}），Material.ROCK、MapColor.BLACK；不是完整方块（isOpaqueCube / isFullCube 为假），碰撞仍是整格（没覆盖包围盒）。
 * 方块模型只有破坏粒子，镜框和镜面由方块实体渲染器画（{@code client/render/MagicalMirrorRenderer}）。
 * <p>
 * 没有界面（原作右键「开界面」但没注册界面，什么也不发生，M-12）：{@link #opensMenu()} 为假，拿控制棒右键时基类返回 PASS，控制棒的 useOn 才接得到。
 * 空手（主手空、没潜行）右键在动作栏显示链接状态和没在送的原因（{@link MagicalMirrorBlockEntity#statusMessage()}；新加，原作什么都不显示，玩家不知道它为什么不送）；别的情况照旧 PASS。
 */
public class MagicalMirrorBlock extends MachineBlock<MagicalMirrorBlockEntity> {

    private final Supplier<BlockEntityType<MagicalMirrorBlockEntity>> type;

    public MagicalMirrorBlock(Properties properties, Supplier<BlockEntityType<MagicalMirrorBlockEntity>> type) {
        super(properties, true);
        this.type = type;
    }

    @Override
    protected BlockEntityType<MagicalMirrorBlockEntity> blockEntityType() {
        return type.get();
    }

    @Override
    protected boolean opensMenu() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof MagicalMirrorBlockEntity mirror)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(mirror.statusMessage(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
