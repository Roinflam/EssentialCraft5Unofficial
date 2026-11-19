package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.registry.ECBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创世神之杖的一块模块（{@code builder_module_<短名>}，builder_upgrade.md §2.2）：本身什么都不做，在工作台里和创世神之杖或铺设魔杖合在一起装上
 * （{@code recipe/BuilderModuleRecipe}），装上后在 {@link BuilderTier#stats} 里生效。提示写效果（倍率、混铺最多几种读 {@code general.builder.modules.*}，
 * 客户端读的是登录时同步过来的服务端配置）、回收写一阶装不上，最后一行写怎么装。
 */
public class BuilderModuleItem extends Item {

    private final BuilderModules.Kind kind;

    public BuilderModuleItem(Properties properties, BuilderModules.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public BuilderModules.Kind kind() {
        return kind;
    }

    /** 这件物品是哪种模块；不是模块返回 null */
    @Nullable
    public static BuilderModules.Kind kindOf(ItemStack stack) {
        return stack.getItem() instanceof BuilderModuleItem module ? module.kind : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        ECBuilder.Config.ModuleValues values = ECBuilder.Config.moduleValues();
        String key = "tooltip.essentialcraft5.builder_module." + kind.id();
        Component effect = switch (kind) {
            case RANGE -> Component.translatable(key, number(values.rangeVolume()), number(values.rangeSide()));
            case HASTE -> Component.translatable(key, number(values.hasteCells()));
            case EFFICIENCY -> Component.translatable(key, number(values.efficiencyCost()));
            case REACH -> Component.translatable(key, values.reachBonus());
            case MIX -> Component.translatable(key, ECBuilder.Config.mixMaxKinds());
            default -> Component.translatable(key);
        };
        tooltip.add(effect.copy().withStyle(ChatFormatting.DARK_PURPLE));
        if (kind == BuilderModules.Kind.SALVAGE) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.builder_module.not_paver").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.builder_module.install").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** 倍率写成最短的样子：2.0 → 2，1.4 → 1.4 */
    static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
