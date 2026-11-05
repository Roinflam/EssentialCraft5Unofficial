package com.flameforge.essentialcraft5.mixin.seal;

import com.flameforge.essentialcraft5.entity.boss.seal.CalamitySeal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.extensions.IForgeItemStack;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;

/**
 * 天灾封印的效果路（{@link CalamitySeal}）：给 ItemStack 补上 Forge 两个接口默认方法的实现，先照默认实现问物品本身，再按封印过滤。
 * ItemStack 自己没有覆写这两个方法，所以这里加的方法直接盖过接口默认方法，所有调用方（原版的 {@code EnchantmentHelper.getItemEnchantmentLevel}、
 * {@code runIterationOnItem}，以及直接调 {@code stack.getEnchantmentLevel} / {@code getAllEnchantments} 的模组）都走到这里。
 * 两个方法名是 Forge 声明的（不参与混淆），{@code remap = false}；方法体里调原版方法照常写官方名，出 jar 时由 reobf 改成 SRG。
 */
@Mixin(value = ItemStack.class, remap = false)
public abstract class ItemStackSealMixin implements IForgeItemStack {

    @Override
    public int getEnchantmentLevel(Enchantment enchantment) {
        ItemStack self = (ItemStack) (Object) this;
        return CalamitySeal.level(self, enchantment, self.getItem().getEnchantmentLevel(self, enchantment));
    }

    @Override
    public Map<Enchantment, Integer> getAllEnchantments() {
        ItemStack self = (ItemStack) (Object) this;
        return CalamitySeal.filterEffects(self, self.getItem().getAllEnchantments(self));
    }
}
