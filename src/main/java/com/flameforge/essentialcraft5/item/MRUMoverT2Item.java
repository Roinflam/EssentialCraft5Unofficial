package com.flameforge.essentialcraft5.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 二阶 MRUCU 移动魔杖（原作 mruMover_t2：和一阶同一个类 ItemMRUMover，setMaxDamage(256 × 6) = 耐久 1536）。
 * 找目标、拉近推远、每 20 tick 掉 1 点耐久都和一阶一样（{@link MRUMoverItem}）；另外在 MRU 可见列表里
 * （原作 ApiCore.MRU_VISIBLE_LIST，这里是 {@code essentialcraft5:mru_vision} 标签），拿在手里能隔墙看见聚集体。
 * <p>
 * 出生点保护检查在一阶里（{@link MRUMoverItem#canMoveTo}），两阶共用。
 */
public class MRUMoverT2Item extends MRUMoverItem {

    public MRUMoverT2Item(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_vision.held").withStyle(ChatFormatting.DARK_AQUA));
    }
}
