package com.flameforge.essentialcraft5.compat.curio;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 饰品封禁场的纯客户端设置：CLIENT 配置 {@code config/essentialcraft5-client.toml} 的 {@code [curio_seal]}，每台电脑自己定，服务器不管、不同步。
 * 由 {@code ECComputer.init()} 调 {@code ECConfig.client(CurioSealClientConfig::define)} 登记。
 * <ul>
 *   <li>{@code hud_icon}（默认 true）：饰品被封时在快捷栏左边画一个禁用图标（常驻标识；格子图标和物品提示不受这一项影响）。</li>
 * </ul>
 * common 类（只有 ForgeConfigSpec，不 import 客户端类）：专用服上 CLIENT 配置永远不加载，值一直是默认值。
 * 快捷栏图标每帧读，所以在 {@code ModConfigEvent.Loading / Reloading} 里缓存进 volatile 字段（同 {@code HoannaClientConfig}）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CurioSealClientConfig {

    public static final boolean DEFAULT_HUD_ICON = true;

    private static ForgeConfigSpec.BooleanValue hudIcon;
    private static volatile boolean cachedHudIcon = DEFAULT_HUD_ICON;

    private CurioSealClientConfig() {
    }

    /** CLIENT 配置的 {@code [curio_seal]} 小节（{@code ECConfig.client} 调） */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Curio Seal Field (client only).", "饰品封禁场（只影响这台电脑）").push("curio_seal");
        hudIcon = builder.comment("Show a small icon left of the hotbar while your curios are sealed.",
                        "饰品被封时在快捷栏左边显示一个小图标")
                .define("hud_icon", DEFAULT_HUD_ICON);
        builder.pop();
    }

    /** 快捷栏图标开着（每帧读，不装箱） */
    public static boolean hudIcon() {
        return cachedHudIcon;
    }

    private static void refresh() {
        cachedHudIcon = hudIcon == null ? DEFAULT_HUD_ICON : ECConfig.getClient(hudIcon);
    }

    @SubscribeEvent
    public static void onCurioSealClientConfigLoading(ModConfigEvent.Loading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }

    @SubscribeEvent
    public static void onCurioSealClientConfigReloading(ModConfigEvent.Reloading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }
}
