package com.flameforge.essentialcraft5.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 海纳尔各群系的特产结晶（第十三批 13d，batch13.md §6.1、§6.6）：地脉结晶、辐晶、潮晶、影晶。本身什么都不做，拿去做升阶、勘测图、六阶祭品这类配方。
 * 提示框一行灰字，键是 {@code tooltip.essentialcraft5.<注册名>.desc}，写从哪来、拿去干什么（写法照 {@link MobMaterialItem}）。
 */
public class HoannaSpecialtyItem extends Item {

    private final String tooltipKey;

    /**
     * @param name 注册名（提示框键从它拼出来）
     */
    public HoannaSpecialtyItem(Properties properties, String name) {
        super(properties);
        this.tooltipKey = MobMaterialItem.tooltipKey(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
    }
}
