package com.flameforge.essentialcraft5.client.vfx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * 光影包检测：Oculus / Iris 在不在、有没有正在用光影包、此刻是不是在画阴影图。只用反射查 Iris 的公开 API
 * {@code net.irisshaders.iris.api.v0.IrisApi}（Oculus 也带着这个类），不做依赖：没装就当没有光影包。
 * <p>
 * 为什么要查：光影包开着时，世界画进光影包自己的多张缓冲（颜色、法线、材质……），原版着色器会被换成光影包的程序、把这些缓冲都写好；
 * 自带的着色器光影包不认识，只写第一张颜色缓冲，其余缓冲里留下的是没定义的值，光影包后面的光照 / 合成按这些值算，就会出现黑块、闪烁或者整个看不见。
 * 所以光影包开着时（{@link Vfx#safeMode()}）换成原版着色器 + 预烘贴图的画法，交给光影包当普通的发光半透明物体处理。
 * 阴影图那一遍（{@link #renderingShadowPass()}）一律不画：发光的东西不该投影子。
 */
public final class VfxCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialCraft5/Vfx");
    private static final MethodHandle SHADER_PACK_IN_USE;
    private static final MethodHandle SHADOW_PASS;
    private static final Object API;
    private static boolean warned;

    static {
        Object api = null;
        MethodHandle inUse = null;
        MethodHandle shadow = null;
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            inUse = lookup.findVirtual(type, "isShaderPackInUse", MethodType.methodType(boolean.class));
            shadow = lookup.findVirtual(type, "isRenderingShadowPass", MethodType.methodType(boolean.class));
            LOGGER.info("[特效] 检测到 Iris / Oculus，光影包开着时换稳妥画法");
        } catch (ClassNotFoundException ignored) {
            // 没装 Iris / Oculus
        } catch (Throwable e) {
            LOGGER.warn("[特效] Iris API 找到了但调不通，按没有光影包处理：{}", e.toString());
            api = null;
            inUse = null;
            shadow = null;
        }
        API = api;
        SHADER_PACK_IN_USE = inUse;
        SHADOW_PASS = shadow;
    }

    private VfxCompat() {
    }

    /** 装了 Iris / Oculus（不管开没开光影包） */
    public static boolean irisPresent() {
        return API != null;
    }

    /** 此刻有光影包在用 */
    public static boolean shaderPackInUse() {
        return call(SHADER_PACK_IN_USE);
    }

    /** 此刻在画光影包的阴影图 */
    public static boolean renderingShadowPass() {
        return call(SHADOW_PASS);
    }

    private static boolean call(MethodHandle handle) {
        if (handle == null) {
            return false;
        }
        try {
            return (boolean) handle.invoke(API);
        } catch (Throwable e) {
            if (!warned) {
                warned = true;
                LOGGER.warn("[特效] 调 Iris API 出错，之后按没有光影包处理：{}", e.toString());
            }
            return false;
        }
    }
}
