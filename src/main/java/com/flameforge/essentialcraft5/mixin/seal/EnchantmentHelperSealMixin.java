package com.flameforge.essentialcraft5.mixin.seal;

import com.flameforge.essentialcraft5.entity.boss.seal.CalamitySeal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 天灾封印在 {@code EnchantmentHelper} 里的三处（{@link CalamitySeal}）：
 * <ul>
 *   <li>{@code getTagEnchantmentLevel}（Forge 加的，明文名）：直接查标签等级的模组也封；</li>
 *   <li>{@code getEnchantments}（读整张附魔表，按整张附魔表分发效果的模组读的就是它）：去掉封着的，本人开着别的界面时不去；</li>
 *   <li>{@code setEnchantments}（写回附魔表）：写到被封的物品上时，把调用方没看见的那几条补回去，附魔不会因为封印丢掉。</li>
 * </ul>
 * 本模组没有 refmap，全部 {@code remap = false}：后两个是原版方法，开发环境里叫官方名、正式环境里叫 SRG 名，两个名字都写上，
 * 哪个环境都只对得上其中一个（{@code getEnchantments} = {@code m_44831_}，{@code setEnchantments} = {@code m_44865_}）。
 * 描述符里的类名一律官方名（类不混淆）。
 */
@Mixin(value = EnchantmentHelper.class, remap = false)
public abstract class EnchantmentHelperSealMixin {

    @Inject(method = "getTagEnchantmentLevel(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I",
            at = @At("RETURN"), cancellable = true)
    private static void ec5$sealTagLevel(Enchantment enchantment, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        int level = cir.getReturnValueI();
        int sealed = CalamitySeal.level(stack, enchantment, level);
        if (sealed != level) {
            cir.setReturnValue(sealed);
        }
    }

    @Inject(method = {"getEnchantments(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Map;", "m_44831_(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Map;"},
            at = @At("RETURN"), cancellable = true)
    private static void ec5$sealTable(ItemStack stack, CallbackInfoReturnable<Map<Enchantment, Integer>> cir) {
        Map<Enchantment, Integer> map = cir.getReturnValue();
        Map<Enchantment, Integer> filtered = CalamitySeal.filterTable(stack, map);
        if (filtered != map) {
            cir.setReturnValue(filtered);
        }
    }

    /** 要补的时候带着补全的表重调一次、取消这一次（重调时没东西可补，照常写） */
    @Inject(method = {"setEnchantments(Ljava/util/Map;Lnet/minecraft/world/item/ItemStack;)V", "m_44865_(Ljava/util/Map;Lnet/minecraft/world/item/ItemStack;)V"},
            at = @At("HEAD"), cancellable = true)
    private static void ec5$sealRestore(Map<Enchantment, Integer> map, ItemStack stack, CallbackInfo ci) {
        Map<Enchantment, Integer> fixed = CalamitySeal.restoreHidden(stack, map);
        if (fixed != map) {
            EnchantmentHelper.setEnchantments(fixed, stack);
            ci.cancel();
        }
    }
}
