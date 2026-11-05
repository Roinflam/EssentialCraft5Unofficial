package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.DimensionalTransceiverBlockEntity;
import com.flameforge.essentialcraft5.item.creative.CreativeOnly;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * MRU 跨维度收发器。对应原作 BlockDimensionalTransciever（Material.ROCK、整块 cube_bottom_top，侧面是动画贴图，不透光度 0）。
 * 只放创造栏：原作全仓没有配方、战利品、交易（policy §4.7）。逻辑在 {@link DimensionalTransceiverBlockEntity}。
 * 它没有手册条目，物品提示写清用途，最后一行「没有合成配方」（同 registry/ECCreative 的道具）。
 */
public class DimensionalTransceiverBlock extends MachineBlock<DimensionalTransceiverBlockEntity> {

    public static final String TOOLTIP_KEY = "tooltip.essentialcraft5.dimensional_transceiver";

    public DimensionalTransceiverBlock(Properties properties) {
        // 原作 setLightOpacity(0)：天空光直接穿过
        super(properties, true);
    }

    @Override
    protected BlockEntityType<DimensionalTransceiverBlockEntity> blockEntityType() {
        return ECVoidMachines.DIMENSIONAL_TRANSCEIVER_BE.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
        tooltip.add(CreativeOnly.tooltipLine());
    }
}
