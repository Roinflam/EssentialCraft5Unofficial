package com.flameforge.essentialcraft5.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolAction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 风之剑（原作 ItemWindSword）：风之工具的「剑」形态，变形规则见 {@link WindToolForms}，数值和注册见 {@code ECWindGear}。
 * 右键先锄地（原作 onItemUse 转给风之锄）；打中生物不再变形。
 */
public class WindSwordItem extends SwordItem implements WindToolForms.FormItem {

    public WindSwordItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public WindToolForms.Form windForm() {
        return WindToolForms.Form.SWORD;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return WindToolForms.useOn(context, super::useOn);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return super.canPerformAction(stack, toolAction) || WindToolForms.extraAction(toolAction);
    }

    /** 同一格里换形态时不做「放下再举起」的换手动画，直接换模型；换快捷栏格子时照常 */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return !WindToolForms.isMorph(oldStack, newStack, slotChanged) && super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        WindToolForms.appendTooltip(stack, tooltip);
    }
}
