package com.flameforge.essentialcraft5.item.module;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 宅男套装模块的物品（batch10.md §4.3 第 1 条）：本身什么都不做，在工作台里和一件宅男盔甲合在一起装上去（{@code recipe/ArmorModuleRecipe}）。
 * <p>
 * 提示行：说明（{@link ModuleType#descriptionKey()}，各模块自己写进语言键）→ 能装在哪几件 → 每秒耗电 / 每次花费（有才写）→ 怎么装 →
 * 被 {@code general.computer_modules.disabled} 关掉时一行红字。服务端配置随进服同步，客户端读到的就是服务端的数。
 */
public class ArmorModuleItem extends Item {

    public static final String KEY_PARTS = "tooltip.essentialcraft5.module.parts";
    public static final String KEY_PER_SECOND = "tooltip.essentialcraft5.module.per_second";
    public static final String KEY_PER_USE = "tooltip.essentialcraft5.module.per_use";
    public static final String KEY_HOW = "tooltip.essentialcraft5.module.how";
    public static final String KEY_DISABLED = "tooltip.essentialcraft5.module.disabled";
    /** 部位名（宅男终端已有的键） */
    public static final String KEY_PART = "gui.essentialcraft5.computer_terminal.part.";

    private final ModuleType type;

    public ArmorModuleItem(ModuleType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public ModuleType moduleType() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(type.descriptionKey()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(KEY_PARTS, partsText(type)).withStyle(ChatFormatting.DARK_PURPLE));
        if (type.mruPerSecond() > 0) {
            tooltip.add(Component.translatable(KEY_PER_SECOND, type.mruPerSecond()).withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (type.mruPerUse() > 0) {
            tooltip.add(Component.translatable(KEY_PER_USE, type.mruPerUse()).withStyle(ChatFormatting.DARK_PURPLE));
        }
        tooltip.add(Component.translatable(KEY_HOW).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        if (ModuleConfig.isDisabled(type.id())) {
            tooltip.add(Component.translatable(KEY_DISABLED).withStyle(ChatFormatting.RED));
        }
    }

    /** 「头盔 / 胸甲」这样的部位列表（按头、胸、腿、靴的顺序） */
    public static Component partsText(ModuleType type) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (ArmorItem.Type part : ArmorItem.Type.values()) {
            if (!type.fits(part)) {
                continue;
            }
            if (!first) {
                out.append(" / ");
            }
            out.append(Component.translatable(KEY_PART + part.getName()));
            first = false;
        }
        return out;
    }
}
