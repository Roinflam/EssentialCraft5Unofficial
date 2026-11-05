package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 淡青宝石（原作 ItemGenericEC meta 40，ECDigger）：纯材料，提示「本模组的配方里可以当钻石或绿宝石用」。
 * 顶替靠 EC 内部物品标签 {@code essentialcraft5:diamonds} / {@code emeralds}（{@code registry/ECDigger}），不进任何 forge 共享标签。
 * <p>
 * 第五批起还是地脉投影仪的透镜（标签 {@code essentialcraft5:ley_projector_lenses}，放 1 颗 = 时运 I）：投影仪开着时多一行提示
 * （配置 {@code machines.ley_projector.enabled}，客户端读的是服务端同步来的值，没连上服务器时按默认值）。
 */
public class PaleGemItem extends Item {

    public PaleGemItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.pale_gem.substitute").withStyle(ChatFormatting.DARK_GRAY));
        if (ECConfig.get(LeyProjectorBlockEntity.Config.enabled)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.pale_gem.lens").withStyle(ChatFormatting.GRAY));
        }
    }
}
