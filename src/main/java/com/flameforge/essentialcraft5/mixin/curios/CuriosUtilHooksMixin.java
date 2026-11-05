package com.flameforge.essentialcraft5.mixin.curios;

import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import net.minecraftforge.common.util.NonNullFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

/**
 * Curios 混进原版的钩子（{@code CuriosUtilMixinHooks}，Curios 自己的普通类，不在它的 mixin 包里）：
 * <ul>
 *   <li>猪灵中立、细雪行走、免冻：被压制的人换成遮罩视图，饰品当不存在。</li>
 *   <li>「背包里有没有」（{@code Inventory.contains(ItemStack / TagKey)}、{@code hasAnyMatching}）：本次查询不过滤，
 *       任务、钥匙类判断照常认得饰品栏里的东西。</li>
 * </ul>
 * 六个方法各只有一次 {@code getCuriosInventory(…).map(函数)}。{@code remap = false}：目标是 Curios 自己声明的方法，写明文名。
 * 六个目标都是 {@code public static}，所以处理方法也必须 static（不一致时目标类加载那一刻就崩）。
 */
@Mixin(targets = "top.theillusivec4.curios.mixin.CuriosUtilMixinHooks", remap = false)
public abstract class CuriosUtilHooksMixin {

    @ModifyArg(method = {
            "canNeutralizePiglins(Lnet/minecraft/world/entity/LivingEntity;)Z",
            "canWalkOnPowderSnow(Lnet/minecraft/world/entity/LivingEntity;)Z",
            "isFreezeImmune(Lnet/minecraft/world/entity/LivingEntity;)Z"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/common/util/LazyOptional;map(Lnet/minecraftforge/common/util/NonNullFunction;)Ljava/util/Optional;"),
            index = 0, require = 0)
    private static NonNullFunction<ICuriosItemHandler, Object> ec5$maskSealed(NonNullFunction<ICuriosItemHandler, Object> original) {
        return CurioSeal.maskingFn(original);
    }

    @ModifyArg(method = {
            "containsStack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z",
            "containsTag(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/tags/TagKey;)Z",
            "hasAnyMatching(Lnet/minecraft/world/entity/player/Player;Ljava/util/function/Predicate;)Z"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/common/util/LazyOptional;map(Lnet/minecraftforge/common/util/NonNullFunction;)Ljava/util/Optional;"),
            index = 0, require = 0)
    private static NonNullFunction<ICuriosItemHandler, Object> ec5$unfiltered(NonNullFunction<ICuriosItemHandler, Object> original) {
        return CurioSeal.unfilteredFn(original);
    }
}
