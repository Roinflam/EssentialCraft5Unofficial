package com.flameforge.essentialcraft5.mixin.curios;

import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Curios 饰品栏能力（{@code CurioInventoryCapability$CurioInventoryWrapper}）的查询接口：别的模组问「戴没戴」「时运几级」都走这里。
 * 持有者被压制时在返回处过滤：结果里的饰品不在豁免标签里就去掉，{@code findFirstCurio} 去掉后接着找下一个没被压制的；
 * 时运、抢夺按豁免的那几件重算。在「本次查询不过滤」里时原样返回；持有者正在死亡时照样过滤（免死类饰品在死亡事件里查）。
 * {@code isEquipped} 是 api 里的默认方法，转调 {@code findFirstCurio}，跟着过滤。
 * <p>
 * {@code remap = false}：八个目标都是 Curios 自己声明的方法，写明文名，描述符里的类名写 Mojmap。
 * 处理方法不是 static（目标都是实例方法）。{@code getEquippedCurios()} / {@code getCurios()} 不过滤：Curios 自己和墓碑、
 * 自动装备、仓储类模组拿它们存取物品，遮了会丢东西。
 */
@Mixin(targets = "top.theillusivec4.curios.common.capability.CurioInventoryCapability$CurioInventoryWrapper", remap = false)
public abstract class CurioInventoryWrapperMixin {

    @Unique
    private ICuriosItemHandler ec5$self() {
        return (ICuriosItemHandler) (Object) this;
    }

    @Inject(method = "findFirstCurio(Lnet/minecraft/world/item/Item;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true, require = 0)
    private void ec5$filterFirstItem(Item item, CallbackInfoReturnable<Optional<SlotResult>> cir) {
        Optional<SlotResult> filtered = CurioSeal.filterFirst(ec5$self(), cir.getReturnValue(), item);
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }

    @Inject(method = "findFirstCurio(Ljava/util/function/Predicate;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true, require = 0)
    private void ec5$filterFirstPredicate(Predicate<ItemStack> filter, CallbackInfoReturnable<Optional<SlotResult>> cir) {
        Optional<SlotResult> filtered = CurioSeal.filterFirst(ec5$self(), cir.getReturnValue(), filter);
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }

    @Inject(method = {
            "findCurios(Lnet/minecraft/world/item/Item;)Ljava/util/List;",
            "findCurios(Ljava/util/function/Predicate;)Ljava/util/List;",
            "findCurios([Ljava/lang/String;)Ljava/util/List;"},
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ec5$filterAll(CallbackInfoReturnable<List<SlotResult>> cir) {
        List<SlotResult> filtered = CurioSeal.filterAll(ec5$self(), cir.getReturnValue());
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }

    @Inject(method = "findCurio(Ljava/lang/String;I)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true, require = 0)
    private void ec5$filterSlot(CallbackInfoReturnable<Optional<SlotResult>> cir) {
        Optional<SlotResult> filtered = CurioSeal.filterSlot(ec5$self(), cir.getReturnValue());
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }

    @Inject(method = "getFortuneLevel(Lnet/minecraft/world/level/storage/loot/LootContext;)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void ec5$filterFortune(LootContext lootContext, CallbackInfoReturnable<Integer> cir) {
        Integer level = CurioSeal.filterFortune(ec5$self(), lootContext);
        if (level != null) {
            cir.setReturnValue(level);
        }
    }

    @Inject(method = "getLootingLevel(Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/entity/LivingEntity;I)I",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ec5$filterLooting(DamageSource source, LivingEntity target, int baseLooting, CallbackInfoReturnable<Integer> cir) {
        Integer level = CurioSeal.filterLooting(ec5$self(), source, target, baseLooting);
        if (level != null) {
            cir.setReturnValue(level);
        }
    }
}
