package com.flameforge.essentialcraft5.mixin.curios;

import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Curios 的单格同步包（{@code SPacketSyncStack}）。压制开始那一刻 Curios 在 tick 里比对出「有 → 空」，会把空栈发给本人和旁观者，
 * 客户端的饰品栏就被刷空了（物品其实还在服务端）。这里在包构造完时，如果正处在服务端线程的遮罩作用域里、实体对得上、
 * 是功能槽（{@code handlerType} 0 = EQUIPMENT），就把包里的物品换回真槽里的拷贝；作用域外、别的线程（单人游戏客户端解码）原样。
 * <p>
 * {@code remap = false}：字段和构造器都是 Curios 自己声明的，写明文名。处理方法只收 {@code CallbackInfo}，不引用内部枚举 HandlerType。
 */
@Mixin(targets = "top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack", remap = false)
public abstract class SPacketSyncStackMixin {

    @Shadow
    private int entityId;
    @Shadow
    private int slotId;
    @Shadow
    private String curioId;
    @Shadow
    private ItemStack stack;
    @Shadow
    private int handlerType;

    @Inject(method = "<init>(ILjava/lang/String;ILnet/minecraft/world/item/ItemStack;"
            + "Ltop/theillusivec4/curios/common/network/server/sync/SPacketSyncStack$HandlerType;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("RETURN"), require = 0)
    private void ec5$keepRealStack(CallbackInfo ci) {
        if (this.handlerType == 0) {
            ItemStack real = CurioSeal.realStackForSync(this.entityId, this.curioId, this.slotId);
            if (real != null) {
                this.stack = real;
            }
        }
    }
}
