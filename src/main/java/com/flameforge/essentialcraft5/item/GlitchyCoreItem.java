package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.mru.MRUBalance;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * 故障核心（{@code glitchy_core}，原作 genItem:76，原作只当研究书「全息投影」条目的图标）。第十批 10c 起是第十九章的钥匙（batch10.md §5.10.1）：
 * 全息投影被玩家打倒时必掉一个，物品 NBT {@value #TAG_TARGET} 记着一个目标平衡值（{@code general.inquisitorium.balance_min ~ balance_max}
 * 均匀随机、两位小数，战利品函数 {@code essentialcraft5:random_target_balance} 写进去）；放进辐射仓、接入平衡值对准它的 MRU，解出审判庭坐标
 * （{@code blockentity/GlitchyCoreDecoder}）。
 * <p>
 * 没有这个 NBT 的（创造栏拿的、指令给的）第一次在服务端物品栏里 tick 时补一个随机值。提示行写目标平衡值（按冰冻 / 纯净 / 混沌的字色）和怎么用。
 */
public class GlitchyCoreItem extends Item {

    /** 目标平衡值（float，两位小数） */
    public static final String TAG_TARGET = "TargetBalance";

    public GlitchyCoreItem(Properties properties) {
        super(properties);
    }

    /** 这个核心上记着的目标平衡值；没有（或不是故障核心）返回 null */
    @Nullable
    public static Float targetBalance(ItemStack stack) {
        if (!(stack.getItem() instanceof GlitchyCoreItem)) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_TARGET, Tag.TAG_ANY_NUMERIC)) {
            return null;
        }
        return MRUBalance.clamp(tag.getFloat(TAG_TARGET));
    }

    /** 写目标平衡值（夹到 0~2、取两位小数） */
    public static void setTargetBalance(ItemStack stack, float balance) {
        stack.getOrCreateTag().putFloat(TAG_TARGET, roundBalance(balance));
    }

    /** 按配置的上下限均匀随机一个、取两位小数 */
    public static float rollTarget(RandomSource random) {
        ECInquisitorium.Rules.Values values = ECInquisitorium.Rules.values();
        double low = values.balanceMin();
        double high = values.balanceMax();
        return roundBalance((float) (low + random.nextDouble() * (high - low)));
    }

    /** 夹到 0~2、四舍五入到两位小数 */
    public static float roundBalance(float balance) {
        return Math.round(MRUBalance.clamp(balance) * 100F) / 100F;
    }

    /** 两位小数的写法（提示行、界面共用） */
    public static String format(float balance) {
        return String.format(Locale.ROOT, "%.2f", balance);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && targetBalance(stack) == null) {
            setTargetBalance(stack, rollTarget(level.random));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Float target = targetBalance(stack);
        if (target == null) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.glitchy_core.unknown").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // 界面上平衡值的字色（纯蓝 0000FF）在提示框的深底上看不清，提示行换成同色系的亮一档
        ChatFormatting color = switch (MRUBalance.kind(target)) {
            case FROZEN -> ChatFormatting.BLUE;
            case PURE -> ChatFormatting.AQUA;
            case CHAOS -> ChatFormatting.RED;
        };
        tooltip.add(Component.translatable("tooltip.essentialcraft5.glitchy_core.target",
                Component.literal(format(target)).withStyle(color)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.glitchy_core.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
