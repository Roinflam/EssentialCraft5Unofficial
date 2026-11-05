package com.flameforge.essentialcraft5.mixin.curios;

import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 旧查询接口 {@code CuriosApi.getCuriosHelper().findEquippedCurio(…)}（{@code common/CuriosHelper}）：Predicate 版自己遍历饰品栏，
 * Item 版转调别的查询；两个重载都在返回处过滤一遍（过滤是幂等的，转调过的再过一遍什么都不变）。
 * {@code remap = false}：目标是 Curios 自己声明的方法，写明文名；处理方法不是 static（目标是实例方法）。
 */
@Mixin(targets = "top.theillusivec4.curios.common.CuriosHelper", remap = false)
public abstract class CuriosHelperMixin {

    @Inject(method = "findEquippedCurio(Ljava/util/function/Predicate;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ec5$filterLegacyPredicate(Predicate<ItemStack> filter, LivingEntity entity,
                                           CallbackInfoReturnable<Optional<ImmutableTriple<String, Integer, ItemStack>>> cir) {
        Optional<ImmutableTriple<String, Integer, ItemStack>> filtered = CurioSeal.filterLegacy(entity, cir.getReturnValue(), filter);
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }

    @Inject(method = "findEquippedCurio(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ec5$filterLegacyItem(Item item, LivingEntity entity,
                                      CallbackInfoReturnable<Optional<ImmutableTriple<String, Integer, ItemStack>>> cir) {
        Optional<ImmutableTriple<String, Integer, ItemStack>> filtered = CurioSeal.filterLegacy(entity, cir.getReturnValue(), item);
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
}
