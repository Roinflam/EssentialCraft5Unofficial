package com.flameforge.essentialcraft5.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolAction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 风之锹（原作 ItemWindShovel）：风之工具的「锹」形态，变形规则见 {@link WindToolForms}，数值和注册见 {@code ECWindGear}。
 * 右键先锄地（原作 onItemUse 转给风之锄），锄不了的方块照原版锹铲土径 / 灭营火；打中生物不扣耐久、之后变成剑（原作 hitEntity）。
 */
public class WindShovelItem extends ShovelItem implements WindToolForms.FormItem {

    public WindShovelItem(Tier tier, float attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public WindToolForms.Form windForm() {
        return WindToolForms.Form.SHOVEL;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return WindToolForms.useOn(context, super::useOn);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return super.canPerformAction(stack, toolAction) || WindToolForms.extraAction(toolAction);
    }

    /** 打中生物：不扣耐久（原作 hitEntity 不调父类、直接返回 true；原版挖掘工具打人扣 2），然后主手的这把变成剑 */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        WindToolForms.afterHit(stack, attacker);
        return true;
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
