package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.ForceDomeBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECForceDome;
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
 * 力场穹顶（第十批 10c-M4）。右键开界面、挖掉掉出绑定宝石由 {@link MachineBlock} 做；方块只掉自己，存着的 MRU 不带走。
 * <p>
 * 放下时把「放下的时间」记进方块实体：同一主人同时工作的台数按放下的先后排（{@link ForceDomeBlockEntity}）。
 * BlockItem 先写物品上的 BlockEntityTag、再调 setPlacedBy，创造模式 Ctrl+中键复制出来的也会被改成「现在」，不能插队（同地脉凝聚器）。
 */
public class ForceDomeBlock extends MachineBlock<ForceDomeBlockEntity> {

    public ForceDomeBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    protected BlockEntityType<ForceDomeBlockEntity> blockEntityType() {
        return ECForceDome.FORCE_DOME_BE.get();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ForceDomeBlockEntity be) {
            be.markPlaced(level.getGameTime());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.force_dome", ECConfig.get(ForceDomeBlockEntity.Config.radius))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.force_dome.claims").withStyle(ChatFormatting.DARK_GRAY));
    }
}
