package com.flameforge.essentialcraft5.mixin.curios;

import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import net.minecraftforge.common.util.NonNullConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

/**
 * Curios 让饰品生效的主路径：{@code CuriosEventHandler} 的每刻 tick（curioTick、属性、onEquip / onUnequip、同步）、
 * 末影人面具、经验修补、挖矿的时运经验。四个方法体里各只有一次 {@code getCuriosInventory(…).ifPresent(处理函数)}，
 * 这里把处理函数包一层：被饰品封禁场压制的人，交给原处理函数的是遮罩视图（{@code compat/curio/SealedCuriosView}），
 * 功能槽读出来是空的，Curios 自己就会按「有 → 空」去属性、调 onUnequip，之后不再 tick 它们。
 * <p>
 * {@code remap = false}：目标类和目标方法都是 Curios 自己声明的，写明文名；{@code LazyOptional} 是 Forge 的，也不混淆。
 * 只按名字写目标（{@code targets}），编译不依赖 Curios 的内部类。处理方法不是 static（四个目标都是实例方法）。
 * 不注入 tick 里的 lambda：lambda 的编号和是否 static 随编译变，对不上时目标类一加载就崩。
 */
@Mixin(targets = "top.theillusivec4.curios.common.event.CuriosEventHandler", remap = false)
public abstract class CuriosEventHandlerMixin {

    @ModifyArg(method = {
            "tick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            "enderManAnger(Lnet/minecraftforge/event/entity/living/EnderManAngerEvent;)V",
            "playerXPPickUp(Lnet/minecraftforge/event/entity/player/PlayerXpEvent$PickupXp;)V",
            "onBreakBlock(Lnet/minecraftforge/event/level/BlockEvent$BreakEvent;)V"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/common/util/LazyOptional;ifPresent(Lnet/minecraftforge/common/util/NonNullConsumer;)V"),
            index = 0, require = 0)
    private NonNullConsumer<ICuriosItemHandler> ec5$maskSealed(NonNullConsumer<ICuriosItemHandler> original) {
        // 两端都没人被压制时原样返回，不分配
        return CurioSeal.masking(original);
    }
}
