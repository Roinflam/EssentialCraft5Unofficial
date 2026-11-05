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
 * 魔力凿（原作 ItemMagicalChisel）：只在合成格里用的容器物品。花纹底材 + 凿（任意耐久）→ 4 个大方砖，凿子留在格子里、掉 1 耐久。
 * 没有世界交互（不覆盖 {@code useOn}；不加「右键换花纹」——那会绕开机械纹、符文、星纹、镀面、包边要的红石 / 魔力精华 / MRU 碎片 / 铁 / 皮革）。
 * <p>
 * 次数：耐久 {@value #USES}，合成一次损耗值 +1，<b>加到 {@value #USES} 时不再返还</b>，正好 {@value #USES} 次。
 * 原作 {@code hasContainerItem} 判的是「损耗值 &lt; 最大耐久」，损耗值 256 的凿子最后一次返还的是 257 的「坏凿子」、第 257 次才没了（共 257 次）。
 * <p>
 * 配方书自动摆放不认用过的凿子（原版 {@code StackedContents.accountSimpleStack} 跳过有损耗的物品），手动放进格子合成不受影响（配方匹配用的是 {@code accountStack}）。
 * <p>
 * <b>不可修理</b>（{@link #isRepairable}）：否则两把凿子走原版修理配方能复制，见那里的注释。
 */
public class MagicalChiselItem extends Item {

    /** 能用几次（= 最大耐久） */
    public static final int USES = 256;

    public MagicalChiselItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    /** 损耗值 +1 的一份；用满 {@value #USES} 次返回空（凿子没了） */
    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        int damage = stack.getDamageValue() + 1;
        if (damage >= stack.getMaxDamage()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = stack.copyWithCount(1);
        remaining.setDamageValue(damage);
        return remaining;
    }

    /**
     * 不可修理（审查 DECOR-1，原作 bug 顺手修）。原版修理配方 {@code minecraft:repair_item} 只要两件同种、各 1 个、{@code isRepairable()}
     * 就匹配（不要求有损耗），它不覆盖 {@code getRemainingItems}，默认实现给每格调 {@link #getCraftingRemainingItem}——
     * 凿子恒有剩余物，于是两把凿子各自损耗 +1 回到格子里，外加一把合出来的：2 把进 3 把出，魔力合成器接红石还能自动刷。
     * 原作 1.12 的 {@code RecipeRepairItem} + {@code getContainerItem} 也一样。
     * <p>
     * 返回 false 让修理配方不匹配。连带：魔力修复器（查 {@code isRepairable()}）不再修凿子、砂轮也不再合并两把凿子（砂轮两件都消耗，本来就不会复制）；
     * 凿子只要 4 颗元素晶石 + 1 根木棍，影响可以忽略。
     */
    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.magical_chisel").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.magical_chisel.uses", stack.getMaxDamage() - stack.getDamageValue())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
