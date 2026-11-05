package com.flameforge.essentialcraft5.mixin.seal;

import com.flameforge.essentialcraft5.entity.boss.seal.CalamitySeal;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 只在客户端：被天灾封印压着的附魔物品不画附魔光泽（{@link CalamitySeal#isSealedStack}，本地玩家自己的物品），解封后光泽回来。
 * 只管带附魔的物品，下界之星这类自带光泽的照旧。{@code hasFoil} 是原版方法、没有 refmap：官方名和 SRG 名 {@code m_41790_} 都写上。
 */
@Mixin(value = ItemStack.class, remap = false)
public abstract class ItemStackFoilSealMixin {

    @Inject(method = {"hasFoil()Z", "m_41790_()Z"}, at = @At("HEAD"), cancellable = true)
    private void ec5$sealFoil(CallbackInfoReturnable<Boolean> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!CalamitySeal.noOne() && self.isEnchanted() && CalamitySeal.isSealedStack(self)) {
            cir.setReturnValue(false);
        }
    }
}
