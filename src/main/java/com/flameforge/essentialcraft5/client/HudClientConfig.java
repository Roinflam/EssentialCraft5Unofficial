package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 快捷栏旁那几样显示的纯客户端设置：CLIENT 配置 {@code config/essentialcraft5-client.toml} 的 {@code [hud]}，每台电脑自己定，服务器不管、不同步。
 * 由 {@code ECBlockEntities.init()} 调 {@code ECConfig.client(HudClientConfig::define)} 登记（电量条是配着充能仓做的，第十三批 13b，batch13.md §4.7）。
 * <ul>
 *   <li>{@code armor_charge}（默认 true）：快捷栏左边的护甲电量条（{@code client/ArmorChargeHud}）。关掉以后矩阵后遗症的方框也不再让出那一格。</li>
 * </ul>
 * common 类（只有 ForgeConfigSpec，不 import 客户端类）：专用服上 CLIENT 配置永远不加载，值一直是默认值。
 * 电量条每帧读，所以在 {@code ModConfigEvent.Loading / Reloading} 里缓存进 volatile 字段（同 {@code CurioSealClientConfig}）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HudClientConfig {

    public static final boolean DEFAULT_ARMOR_CHARGE = true;

    private static ForgeConfigSpec.BooleanValue armorCharge;
    private static volatile boolean cachedArmorCharge = DEFAULT_ARMOR_CHARGE;

    private HudClientConfig() {
    }

    /** CLIENT 配置的 {@code [hud]} 小节（{@code ECConfig.client} 调） */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Things drawn next to the hotbar (client only).", "快捷栏旁的显示（只影响这台电脑）").push("hud");
        armorCharge = builder.comment("Show the combined charge of the MRU shield armor you wear, left of the hotbar.",
                        "在快捷栏左边显示身上 MRU 护盾盔甲的电量合计")
                .define("armor_charge", DEFAULT_ARMOR_CHARGE);
        builder.pop();
    }

    /** 护甲电量条开着（每帧读，不装箱） */
    public static boolean armorCharge() {
        return cachedArmorCharge;
    }

    private static void refresh() {
        cachedArmorCharge = armorCharge == null ? DEFAULT_ARMOR_CHARGE : ECConfig.getClient(armorCharge);
    }

    @SubscribeEvent
    public static void onHudClientConfigLoading(ModConfigEvent.Loading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }

    @SubscribeEvent
    public static void onHudClientConfigReloading(ModConfigEvent.Reloading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }
}
