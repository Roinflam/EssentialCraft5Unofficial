package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity;
import com.flameforge.essentialcraft5.registry.ECLeyCondenser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 地脉凝聚器（EC5 新机器，第九批）。右键开界面、挖掉掉出物品栏由 {@link MachineBlock} 做；方块只掉自己，不带进度和存着的 MRU / ESPE。
 * <p>
 * 和地脉投影仪一样，放下时把「放下的时间」记进方块实体：同一区块、同一主人只有最早的几台工作，全服同主人也按它排先后
 * （{@link LeyCondenserBlockEntity}）。BlockItem 先写物品上的 BlockEntityTag、再调 setPlacedBy，创造模式 Ctrl+中键复制出来的也会被改成「现在」，不能插队。
 */
public class LeyCondenserBlock extends MachineBlock<LeyCondenserBlockEntity> {

    public LeyCondenserBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<LeyCondenserBlockEntity> blockEntityType() {
        return ECLeyCondenser.LEY_CONDENSER_BE.get();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LeyCondenserBlockEntity be) {
            be.markPlaced(level.getGameTime());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.ley_condenser").withStyle(ChatFormatting.GRAY));
    }
}
