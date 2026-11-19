package com.flameforge.essentialcraft5.config;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 特效底座（{@code client/vfx}）的纯客户端设置：CLIENT 配置 {@code config/essentialcraft5-client.toml} 的 {@code [vfx]}，每台电脑自己定，服务器不管、不同步。
 * 由 {@code ECVfx.init()} 调 {@code ECConfig.client(VfxClientConfig::define)} 登记。
 * <ul>
 *   <li>{@code quality}（默认 HIGH）：特效质量。LOW 粒子数约 1/3、不画屏幕扭曲；MEDIUM 约 2/3；HIGH 全量。帧率掉得厉害时底座还会自己再往下压（见 {@code Vfx}）；</li>
 *   <li>{@code shader_pack_mode}（默认 AUTO）：光影包开着时怎么画。AUTO = 检测到 Oculus / Iris 正在用光影包就换成原版着色器 + 预烘贴图的稳妥画法
 *       （光影包下自定义着色器常常不显示或变成黑块）；SAFE = 一直用稳妥画法；FULL = 一直用自带着色器（光影包下可能出错，排查用）；</li>
 *   <li>{@code distortion}（默认 true）：热浪、冲击波的屏幕扭曲。稳妥画法、Fabulous 画质、LOW 质量下一律不画。</li>
 * </ul>
 * common 类（只有 ForgeConfigSpec，不 import 客户端类）：专用服上 CLIENT 配置永远不加载，值一直是默认值。每帧读，所以缓存进 volatile 字段（同 {@code HudClientConfig}）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VfxClientConfig {

    /** 特效质量 */
    public enum Quality {
        LOW, MEDIUM, HIGH
    }

    /** 光影包开着时的画法 */
    public enum ShaderPackMode {
        AUTO, SAFE, FULL
    }

    private static ForgeConfigSpec.EnumValue<Quality> quality;
    private static ForgeConfigSpec.EnumValue<ShaderPackMode> shaderPackMode;
    private static ForgeConfigSpec.BooleanValue distortion;
    private static volatile Quality cachedQuality = Quality.HIGH;
    private static volatile ShaderPackMode cachedShaderPackMode = ShaderPackMode.AUTO;
    private static volatile boolean cachedDistortion = true;

    private VfxClientConfig() {
    }

    /** CLIENT 配置的 {@code [vfx]} 小节 */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Visual effects of weapons, machines, bosses and gear (client only).", "武器、机器、首领、装备的特效（只影响这台电脑）").push("vfx");
        quality = builder.comment("Effect quality. LOW = about a third of the particles, fewer glow layers, machine effects drawn only nearby and for fewer machines, and no screen distortion;",
                        "MEDIUM = about two thirds of the particles and a slightly shorter machine-effect range; HIGH = everything.",
                        "When the frame rate drops a lot the effects scale themselves down further.",
                        "特效质量：LOW 粒子约三分之一，减少光晕层数，机器特效只在近处、较少几台上显示，不画屏幕扭曲；MEDIUM 粒子约三分之二，机器特效的显示距离略短；HIGH 全量。帧率下降明显时还会自动降低")
                .defineEnum("quality", Quality.HIGH);
        shaderPackMode = builder.comment("How effects are drawn while a shader pack is on. AUTO = switch to the vanilla-shader fallback when Oculus/Iris has a pack loaded;",
                        "SAFE = always use the fallback; FULL = always use the built-in shaders (may break under shader packs, for troubleshooting).",
                        "光影包开着时的画法：AUTO 检测到 Oculus / Iris 在用光影包就换稳妥画法；SAFE 一直用稳妥画法；FULL 一直用自带着色器（光影包下可能出错，排查用）")
                .defineEnum("shader_pack_mode", ShaderPackMode.AUTO);
        distortion = builder.comment("Heat haze and shockwave screen distortion. Never drawn in the fallback mode, with Fabulous graphics, or at LOW quality.",
                        "热浪、冲击波的屏幕扭曲；稳妥画法、Fabulous 画质、LOW 质量下一律不画")
                .define("distortion", true);
        builder.pop();
    }

    public static Quality quality() {
        return cachedQuality;
    }

    public static ShaderPackMode shaderPackMode() {
        return cachedShaderPackMode;
    }

    public static boolean distortion() {
        return cachedDistortion;
    }

    private static void refresh() {
        cachedQuality = quality == null ? Quality.HIGH : ECConfig.getClient(quality);
        cachedShaderPackMode = shaderPackMode == null ? ShaderPackMode.AUTO : ECConfig.getClient(shaderPackMode);
        cachedDistortion = distortion == null || ECConfig.getClient(distortion);
    }

    @SubscribeEvent
    public static void onVfxClientConfigLoading(ModConfigEvent.Loading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }

    @SubscribeEvent
    public static void onVfxClientConfigReloading(ModConfigEvent.Reloading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }
}
