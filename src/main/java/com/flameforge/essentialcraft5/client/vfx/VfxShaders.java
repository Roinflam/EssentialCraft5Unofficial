package com.flameforge.essentialcraft5.client.vfx;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自带的两个 core shader（{@code assets/essentialcraft5/shaders/core/}）：
 * <ul>
 *   <li>{@code vfx}：所有发光 / 半透明特效共用，按顶点里的模式号画光带、光点、环、法阵、冲击波、火花……（模式见 {@link VfxMode}）；</li>
 *   <li>{@code vfx_distort}：屏幕扭曲，采 {@link VfxScreen} 拷下来的场景颜色。</li>
 * </ul>
 * 顶点格式都是 {@link DefaultVertexFormat#POSITION_COLOR_TEX_LIGHTMAP}：UV0 是面片的局部坐标，UV2 借来放（模式, 参数）两个整数。
 * 资源包重载时原版会重新编译（{@link RegisterShadersEvent} 每次重载都发）；编译失败只记日志，{@link #ready()} 为假时底座自动用稳妥画法。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VfxShaders {

    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialCraft5/Vfx");
    @Nullable
    private static ShaderInstance vfx;
    @Nullable
    private static ShaderInstance distort;

    private VfxShaders() {
    }

    @Nullable
    public static ShaderInstance vfx() {
        return vfx;
    }

    @Nullable
    public static ShaderInstance distort() {
        return distort;
    }

    /** 主着色器编译好了 */
    public static boolean ready() {
        return vfx != null;
    }

    @SubscribeEvent
    public static void onVfxRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), EssentialCraft5.id("vfx"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), shader -> vfx = shader);
        } catch (Exception e) {
            vfx = null;
            LOGGER.error("[特效] 主着色器编译失败，改用稳妥画法", e);
        }
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), EssentialCraft5.id("vfx_distort"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), shader -> distort = shader);
        } catch (Exception e) {
            distort = null;
            LOGGER.error("[特效] 扭曲着色器编译失败，不画屏幕扭曲", e);
        }
        VfxAtlas.invalidate();
    }
}
