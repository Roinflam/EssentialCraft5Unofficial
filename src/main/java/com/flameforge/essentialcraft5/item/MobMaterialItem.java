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
 * 怪物身上掉的材料（第十三批 13c 起，batch13.md §5.4）：本身什么都不做，拿去做装备升阶这类配方。
 * 提示框一行灰字，键是 {@code tooltip.essentialcraft5.<注册名>.desc}，写从哪来、拿去干什么。
 * 13c 的上古晶核、矩阵结晶用它；13d 的异变残质、异变之心照这个写法加。
 */
public class MobMaterialItem extends Item {

    private final String tooltipKey;

    /**
     * @param name 注册名（提示框键从它拼出来）
     */
    public MobMaterialItem(Properties properties, String name) {
        super(properties);
        this.tooltipKey = tooltipKey(name);
    }

    /** 提示框那一行的键 */
    public static String tooltipKey(String name) {
        return "tooltip.essentialcraft5." + name + ".desc";
    }

    /** 这一刻提示框用哪个键（默认就是 {@link #tooltipKey(String)}；说法要随配置变的材料覆盖它） */
    protected String descriptionKey() {
        return tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(descriptionKey()).withStyle(ChatFormatting.GRAY));
    }
}
