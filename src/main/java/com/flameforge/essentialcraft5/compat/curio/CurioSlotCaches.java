package com.flameforge.essentialcraft5.compat.curio;

import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * 解除压制时清掉槽位处理器自己按格记的缓存（两端都调）。
 * <p>
 * 有的 Curios 5.14.x 构建在槽位处理器里按格缓存了「这一格物品的 ICurio」，只在 Curios 自己比对出这一格换了物品时清
 * （{@code public void invalidateCache(int)}）。压制期间 Curios 拿到的是遮罩视图，比对不到真物品的变化，缓存也就清不到：
 * 压制中在被封的格子里换了东西，解除那一刻会有一刻拿旧物品的 ICurio 去 tick 新物品（下一刻 Curios 自己会清）。
 * 解除时把这名玩家每一格都清一遍就堵上了；缓存只是少查一次能力，清掉的代价是下一刻重新查。
 * <p>
 * 按名字在运行时类上找这个方法（官方 5.14.1 没有，找不到什么都不做），结果按类缓存；只用 Curios 的公开接口，编译不依赖它的内部类。
 */
public final class CurioSlotCaches {

    private static final Logger LOGGER = LoggerFactory.getLogger("essentialcraft5/curio_seal");
    private static final String METHOD = "invalidateCache";

    /** 运行时类 → 它的 {@code public void invalidateCache(int)}（没有为空） */
    private static final ClassValue<Optional<Method>> INVALIDATE = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                Method method = type.getMethod(METHOD, int.class);
                if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())) {
                    return Optional.empty();
                }
                return Optional.of(method);
            } catch (NoSuchMethodException | SecurityException e) {
                return Optional.empty();
            }
        }
    };

    /** 调用出过错（版本对不上、方法本身抛异常）以后就不再试，免得每次解除都刷日志 */
    private static volatile boolean broken;

    private CurioSlotCaches() {
    }

    /** 这名玩家（活体）饰品栏每一格的按格缓存都清掉；没有饰品栏、这份 Curios 没有这种缓存时什么都不做 */
    public static void resetAll(LivingEntity entity) {
        if (broken) {
            return;
        }
        CuriosApi.getCuriosInventory(entity).ifPresent(inventory -> {
            for (ICurioStacksHandler handler : inventory.getCurios().values()) {
                int slots = handler.getSlots();
                for (int i = 0; i < slots; i++) {
                    if (!reset(handler, i)) {
                        break;
                    }
                }
            }
        });
    }

    /**
     * 清 handler 第 slot 格的缓存。handler 的类没有这个方法时返回 false（这一类的别的格子也不用再试）。
     * 参数写成 Object：GameTest 用一个自带同名方法的小类核对「找得到就调、找不到不调」。
     */
    public static boolean reset(Object handler, int slot) {
        if (broken || handler == null) {
            return false;
        }
        Optional<Method> method = INVALIDATE.get(handler.getClass());
        if (method.isEmpty()) {
            return false;
        }
        try {
            method.get().invoke(handler, slot);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            broken = true;
            LOGGER.warn("Curio Seal Field: could not clear the per-slot cache of {}; skipping from now on", handler.getClass().getName(), e);
            return false;
        }
    }
}
