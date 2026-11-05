package com.flameforge.essentialcraft5.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 风之锄（原作 ItemWindHoe）：风之工具的「锄」形态，变形规则见 {@link WindToolForms}，数值和注册见 {@code ECWindGear}。
 * 右键就是原版锄地（其它四个形态右键也转到这里）。
 * 原作的锄是 1.12 ItemHoe，不是 ItemTool，打中生物后不会变剑（原作 hitEntity 里 instanceof ItemTool 为假）；1.20 的锄和镐一样是挖掘工具，这里统一成打中生物后变剑。
 * 打中生物不扣耐久（同原作：hitEntity 不调父类）。
 */
public class WindHoeItem extends HoeItem implements WindToolForms.FormItem {

    public WindHoeItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    @Override
    public WindToolForms.Form windForm() {
        return WindToolForms.Form.HOE;
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
