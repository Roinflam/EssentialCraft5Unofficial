package com.flameforge.essentialcraft5.client.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 首领整屏画面的纯客户端设置：CLIENT 配置 {@code config/essentialcraft5-client.toml} 的 {@code [boss_fx]}，每台电脑自己定，服务器不管、不同步。
 * 由 {@code ECBossFramework.init()} 调 {@code ECConfig.client(BossFxClientConfig::define)} 登记（第十三批 13c-Fb，batch13.md §5.3.5「护眼」）。
 * <ul>
 *   <li>{@code screen_effects}（默认 true）：首领战的整屏画面（边缘晕影、换相闪光）。关掉以后收到的画面事件一律不画，战斗本身不受影响；</li>
 *   <li>{@code camera_shake}（默认 true）：首领战的镜头震动（天灾之心的开场、灾柱倒下、崩解、打倒，最多 1.2°、1 秒），关掉不晃。</li>
 * </ul>
 * common 类（只有 ForgeConfigSpec，不 import 客户端类）：专用服上 CLIENT 配置永远不加载，值一直是默认值。
 * 叠层每帧读，所以在 {@code ModConfigEvent.Loading / Reloading} 里缓存进 volatile 字段（同 {@code HudClientConfig}）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BossFxClientConfig {

    public static final boolean DEFAULT_SCREEN_EFFECTS = true;
    public static final boolean DEFAULT_CAMERA_SHAKE = true;

    private static ForgeConfigSpec.BooleanValue screenEffects;
    private static ForgeConfigSpec.BooleanValue cameraShake;
    private static volatile boolean cachedScreenEffects = DEFAULT_SCREEN_EFFECTS;
    private static volatile boolean cachedCameraShake = DEFAULT_CAMERA_SHAKE;

    private BossFxClientConfig() {
    }

    /** CLIENT 配置的 {@code [boss_fx]} 小节（{@code ECConfig.client} 调） */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Full-screen effects during boss fights (client only).", "首领战的整屏画面（只影响这台电脑）").push("boss_fx");
        screenEffects = builder.comment("Draw the edge tint and the rare flash some bosses use. Off = never drawn; the fight itself is unchanged.",
                        "画首领战的边缘晕影和偶尔的闪光；关掉以后一律不画，战斗本身不变")
                .define("screen_effects", DEFAULT_SCREEN_EFFECTS);
        cameraShake = builder.comment("Shake the camera a little at big moments of a boss fight (at most 1.2 degrees, 1 second). Off = never.",
                        "首领战的大场面镜头轻晃一下（最多 1.2°、1 秒）；关掉不晃")
                .define("camera_shake", DEFAULT_CAMERA_SHAKE);
        builder.pop();
    }

    /** 画不画（每帧读，不装箱） */
    public static boolean screenEffects() {
        return cachedScreenEffects;
    }

    /** 晃不晃镜头（每帧读） */
    public static boolean cameraShake() {
        return cachedCameraShake;
    }

    private static void refresh() {
        cachedScreenEffects = screenEffects == null ? DEFAULT_SCREEN_EFFECTS : ECConfig.getClient(screenEffects);
        cachedCameraShake = cameraShake == null ? DEFAULT_CAMERA_SHAKE : ECConfig.getClient(cameraShake);
    }

    @SubscribeEvent
    public static void onBossFxClientConfigLoading(ModConfigEvent.Loading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }

    @SubscribeEvent
    public static void onBossFxClientConfigReloading(ModConfigEvent.Reloading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }
}
