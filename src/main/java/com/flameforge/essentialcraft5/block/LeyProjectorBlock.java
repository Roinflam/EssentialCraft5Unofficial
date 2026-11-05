package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECLeyProjector;
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
 * 地脉投影仪（EC5 新机器，继承原作 BlockMagicalQuarry 的属性和渲染层）。右键开界面、挖掉掉出物品栏（含待放的产物）由 {@link MachineBlock} 做。
 * <p>
 * 原作不透光度 0 → {@code transmitsSkylight = true}；模型整格是透明玻璃壳，渲染层 cutout_mipped 写在模型 JSON 里。
 * <p>
 * 放下时除了记主人（{@link MachineBlock#setPlacedBy}），还把「放下的时间」记进方块实体：同一区块、同一主人的投影仪按它排先后，
 * 只有最早的 {@code max_per_owner_per_chunk} 台工作（{@link LeyProjectorBlockEntity}）。BlockItem 先写物品上的 BlockEntityTag、再调 setPlacedBy，
 * 所以创造模式 Ctrl+中键复制出来的（NBT 里带着旧时间）也会在这里被改成「现在」，不能插队。
 */
public class LeyProjectorBlock extends MachineBlock<LeyProjectorBlockEntity> {

    public LeyProjectorBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<LeyProjectorBlockEntity> blockEntityType() {
        return ECLeyProjector.LEY_PROJECTOR_BE.get();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LeyProjectorBlockEntity be) {
            be.markPlaced(level.getGameTime());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.ley_projector").withStyle(ChatFormatting.GRAY));
    }
}
