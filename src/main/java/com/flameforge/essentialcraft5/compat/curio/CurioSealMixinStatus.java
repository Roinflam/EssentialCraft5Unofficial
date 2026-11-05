package com.flameforge.essentialcraft5.compat.curio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 饰品压制核心的五个 mixin 有没有打上（{@link CurioSealMixinPlugin} 写，{@link CurioSeal#availability()} 读）。
 * <p>
 * 只用 {@code java.*} 类型：混入插件在目标类变换的当口写这里，这时顺带加载任何游戏类（实体、物品）都可能让那些类在
 * 自己的 mixin 准备好之前被加载。
 * <p>
 * 核对口径（{@link #EXPECTED}）：每个目标方法里至少调到一次本模组的处理方法（混入后名字里带 {@code ec5$}）。
 * Curios 升级时照 tools/plan/batch10.md §4.1.8 的清单重新 {@code javap} 一遍，再改这张表。
 */
public final class CurioSealMixinStatus {

    /** Curios 版本门的结论 */
    public enum Gate {
        /** 还没轮到判（插件没加载过） */
        UNKNOWN,
        /** 是 5.14.x，打补丁 */
        ACCEPTED,
        /** 不是 5.14.x 或读不到版本，不打补丁，封禁场不可用 */
        REJECTED
    }

    /** 混入配置里的 mixin 名（相对 {@code com.flameforge.essentialcraft5.mixin}） → 目标方法（名字 + 描述符） */
    public static final Map<String, List<String>> EXPECTED;

    static {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("curios.CuriosEventHandlerMixin", List.of(
                "tick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
                "enderManAnger(Lnet/minecraftforge/event/entity/living/EnderManAngerEvent;)V",
                "playerXPPickUp(Lnet/minecraftforge/event/entity/player/PlayerXpEvent$PickupXp;)V",
                "onBreakBlock(Lnet/minecraftforge/event/level/BlockEvent$BreakEvent;)V"));
        expected.put("curios.CuriosUtilHooksMixin", List.of(
                "canNeutralizePiglins(Lnet/minecraft/world/entity/LivingEntity;)Z",
                "canWalkOnPowderSnow(Lnet/minecraft/world/entity/LivingEntity;)Z",
                "isFreezeImmune(Lnet/minecraft/world/entity/LivingEntity;)Z",
                "containsStack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z",
                "containsTag(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/tags/TagKey;)Z",
                "hasAnyMatching(Lnet/minecraft/world/entity/player/Player;Ljava/util/function/Predicate;)Z"));
        expected.put("curios.CurioInventoryWrapperMixin", List.of(
                "findFirstCurio(Lnet/minecraft/world/item/Item;)Ljava/util/Optional;",
                "findFirstCurio(Ljava/util/function/Predicate;)Ljava/util/Optional;",
                "findCurios(Lnet/minecraft/world/item/Item;)Ljava/util/List;",
                "findCurios(Ljava/util/function/Predicate;)Ljava/util/List;",
                "findCurios([Ljava/lang/String;)Ljava/util/List;",
                "findCurio(Ljava/lang/String;I)Ljava/util/Optional;",
                "getFortuneLevel(Lnet/minecraft/world/level/storage/loot/LootContext;)I",
                "getLootingLevel(Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/entity/LivingEntity;I)I"));
        expected.put("curios.CuriosHelperMixin", List.of(
                "findEquippedCurio(Ljava/util/function/Predicate;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;",
                "findEquippedCurio(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;"));
        expected.put("curios.SPacketSyncStackMixin", List.of(
                "<init>(ILjava/lang/String;ILnet/minecraft/world/item/ItemStack;"
                        + "Ltop/theillusivec4/curios/common/network/server/sync/SPacketSyncStack$HandlerType;Lnet/minecraft/nbt/CompoundTag;)V"));
        EXPECTED = Collections.unmodifiableMap(expected);
    }

    /** mixin 名 → 目标类（全名，{@code Class.forName} 用） */
    public static final Map<String, String> TARGETS = Map.of(
            "curios.CuriosEventHandlerMixin", "top.theillusivec4.curios.common.event.CuriosEventHandler",
            "curios.CuriosUtilHooksMixin", "top.theillusivec4.curios.mixin.CuriosUtilMixinHooks",
            "curios.CurioInventoryWrapperMixin", "top.theillusivec4.curios.common.capability.CurioInventoryCapability$CurioInventoryWrapper",
            "curios.CuriosHelperMixin", "top.theillusivec4.curios.common.CuriosHelper",
            "curios.SPacketSyncStackMixin", "top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack");

    static volatile Gate gate = Gate.UNKNOWN;
    /** 版本门看到的 Curios 版本（日志、终端显示原因用） */
    static volatile String curiosVersion = "";
    /** mixin 名 → 没打上的目标方法（空表 = 全部到位）。没打过的 mixin 不在表里 */
    static final Map<String, List<String>> MISSING = new ConcurrentHashMap<>();
    /** {@code FMLLoadCompleteEvent} 里核对过了 */
    static volatile boolean verified;

    private CurioSealMixinStatus() {
    }

    public static Gate gate() {
        return gate;
    }

    public static String curiosVersion() {
        return curiosVersion;
    }

    /** 五个 mixin 是否都打上了、每个目标方法都有本模组的调用 */
    public static boolean allApplied() {
        if (gate != Gate.ACCEPTED) {
            return false;
        }
        for (String mixin : EXPECTED.keySet()) {
            List<String> missing = MISSING.get(mixin);
            if (missing == null || !missing.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 没到位的说明（日志、测试失败信息用）；全部到位时是空表 */
    public static List<String> problems() {
        List<String> out = new ArrayList<>();
        if (gate == Gate.UNKNOWN) {
            // 插件一次都没跑：混入配置整个没加载，和 Curios 版本无关
            out.add("essentialcraft5.mixins.json was not loaded (the mixin plugin never ran; check MixinConfigs in the jar manifest)");
            return out;
        }
        if (gate != Gate.ACCEPTED) {
            out.add("Curios version gate: " + gate + " (" + curiosVersion + ")");
            return out;
        }
        for (String mixin : EXPECTED.keySet()) {
            List<String> missing = MISSING.get(mixin);
            if (missing == null) {
                out.add(mixin + ": not applied");
            } else {
                for (String method : missing) {
                    out.add(mixin + ": " + method);
                }
            }
        }
        return out;
    }

    /** 插件 postApply 调：记下这个 mixin 没打上的目标方法 */
    static void record(String mixin, List<String> missing) {
        MISSING.put(mixin, List.copyOf(missing));
    }
}
