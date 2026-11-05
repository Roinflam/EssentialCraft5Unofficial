package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.api.MagicianTableUpgrades;
import com.flameforge.essentialcraft5.blockentity.MagicianTableBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMagicianTable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 魔力工作台。原作 BlockMagicianTable（OBJ 模型，registerBlockSimple(…, 1, 1, 0) 不透光度 0）。
 * <p>
 * 右键：拿着升级物品（见 {@link MagicianTableUpgrades}）且工作台没装升级时装上、手里扣 1；否则打开界面。
 * 潜行空手右键：卸下升级，掉在方块上方 1.5 格处（拾取延迟 30 tick，与原作一致）。
 * 1.20.1 潜行时手里拿着东西根本不会触发方块的 use，所以只有空手才能卸（原作潜行拿任何非升级物品也能卸）。
 * <p>
 * 和原作的区别：已经装着升级时拿升级物品右键，原作什么都不做（界面也打不开），这里照常打开界面；创造模式装升级不扣物品。
 */
public class MagicianTableBlock extends MachineBlock<MagicianTableBlockEntity> {

    public MagicianTableBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<MagicianTableBlockEntity> blockEntityType() {
        return ECMagicianTable.MAGICIAN_TABLE_BE.get();
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MagicianTableBlockEntity table)) {
            return super.use(state, level, pos, player, hand, hit);
        }
        ItemStack held = player.getItemInHand(hand);
        boolean holdingUpgrade = MagicianTableUpgrades.get(held) != null;

        if (player.isShiftKeyDown()) {
            if (holdingUpgrade || !table.hasUpgrade()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                ItemStack removed = table.removeUpgrade();
                ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 1.5D, pos.getZ() + 0.5D, removed);
                entity.setPickUpDelay(30);
                level.addFreshEntity(entity);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (holdingUpgrade && !table.hasUpgrade()) {
            if (!level.isClientSide && table.installUpgrade(held) && !player.getAbilities().instabuild) {
                held.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
