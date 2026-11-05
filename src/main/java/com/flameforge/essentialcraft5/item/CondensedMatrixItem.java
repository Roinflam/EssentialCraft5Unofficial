package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 凝结矩阵（{@code condensed_matrix}，第十三批 13a，batch13.md §3.5）：富集仓平衡值在中间时，把关在里面的聚集体凝成的一团矩阵。
 * 本身什么都不做，是矩阵淬炼台四阶起的祭品、装备升阶的材料。提示两行：从哪来（平衡值区间按服务端配置写，客户端读到的是同步过来的值）、拿去干什么。
 */
public class CondensedMatrixItem extends Item {

    public static final String TOOLTIP_SOURCE = "tooltip.essentialcraft5.condensed_matrix.source";
    public static final String TOOLTIP_USE = "tooltip.essentialcraft5.condensed_matrix.use";

    public CondensedMatrixItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        EnrichmentControllerBlockEntity.CondenseValues values = EnrichmentControllerBlockEntity.Config.condense();
        tooltip.add(Component.translatable(TOOLTIP_SOURCE, MRUBalance.format((float) values.balanceMin()),
                MRUBalance.format((float) values.balanceMax())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(TOOLTIP_USE).withStyle(ChatFormatting.DARK_PURPLE));
    }
}
