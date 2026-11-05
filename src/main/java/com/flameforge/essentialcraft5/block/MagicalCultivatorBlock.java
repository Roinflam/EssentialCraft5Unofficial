package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MagicalCultivatorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECCultivator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 魔力栽培器（EC5 新机器，第十二批 batch12.md §4.2）。没有朝向；右键开界面、挖掉掉出物品栏由 {@link MachineBlock} 做，
 * 方块只掉自己，不带进度和存着的 MRU。
 * <p>
 * 邻居变了（旁边放下 / 拿走了水）时让方块实体下一刻重查水源；平时每 100 刻也会自己重查一次（{@link MagicalCultivatorBlockEntity}）。
 */
public class MagicalCultivatorBlock extends MachineBlock<MagicalCultivatorBlockEntity> {

    public MagicalCultivatorBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<MagicalCultivatorBlockEntity> blockEntityType() {
        return ECCultivator.MAGICAL_CULTIVATOR_BE.get();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MagicalCultivatorBlockEntity be) {
            be.markWaterDirty();
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.magical_cultivator").withStyle(ChatFormatting.GRAY));
    }
}
