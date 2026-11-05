package com.flameforge.essentialcraft5.structure;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结构定义的登记表：按 ResourceLocation 登记和查找。服务端、客户端都有（登记发生在类加载时，两端都跑）。
 * <p>
 * 模块在自己的注册类里把结构存成静态字段并顺手登记，研究手册的 structure 页写 {@code "structure": "essentialcraft5:mithriline_furnace"}
 * 就能直接画出这份定义——书和机器判定用的是同一份数据，改了结构书自动跟着变：
 * <pre>{@code
 * public static final StructurePattern FURNACE_STRUCTURE = StructurePatterns.register(EssentialCraft5.id("mithriline_furnace"),
 *         StructurePattern.builder()...build());
 * }</pre>
 * 注册类的静态字段在 {@code EC<模块>.init()} 被调用时（模组构造阶段）初始化，早于资源加载，手册载入时一定查得到。
 * 同一个 id 登记两次直接抛异常（两个模块撞名要当场发现）。
 */
public final class StructurePatterns {

    private static final Map<ResourceLocation, StructurePattern> BY_ID = new ConcurrentHashMap<>();

    private StructurePatterns() {
    }

    /** 登记并原样返回，方便写成 static final 字段 */
    public static StructurePattern register(ResourceLocation id, StructurePattern pattern) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(pattern);
        StructurePattern old = BY_ID.putIfAbsent(id, pattern);
        if (old != null && old != pattern) {
            throw new IllegalStateException("结构 " + id + " 登记了两次");
        }
        return pattern;
    }

    @Nullable
    public static StructurePattern get(ResourceLocation id) {
        return BY_ID.get(id);
    }

    /** 反查 id（日志、调试用），没登记过返回 null */
    @Nullable
    public static ResourceLocation idOf(StructurePattern pattern) {
        for (Map.Entry<ResourceLocation, StructurePattern> entry : BY_ID.entrySet()) {
            if (entry.getValue() == pattern) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static Set<ResourceLocation> ids() {
        return Set.copyOf(BY_ID.keySet());
    }
}
