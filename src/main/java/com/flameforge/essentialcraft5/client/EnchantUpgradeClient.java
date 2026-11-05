package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.EnchantUpgraderRenderer;
import com.flameforge.essentialcraft5.client.screen.EnchantUpgraderScreen;
import com.flameforge.essentialcraft5.registry.ECEnchantUpgrade;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 附魔增幅台、升华台的客户端注册（模组总线）：两个界面、悬浮的书（BER）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EnchantUpgradeClient {

    private EnchantUpgradeClient() {
    }

    @SubscribeEvent
    public static void enchantUpgradeClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECEnchantUpgrade.AMPLIFIER_MENU.get(), EnchantUpgraderScreen::new);
            MenuScreens.register(ECEnchantUpgrade.ASCENDER_MENU.get(), EnchantUpgraderScreen::new);
        });
    }

    @SubscribeEvent
    public static void enchantUpgradeRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECEnchantUpgrade.AMPLIFIER_BE.get(), EnchantUpgraderRenderer::new);
        event.registerBlockEntityRenderer(ECEnchantUpgrade.ASCENDER_BE.get(), EnchantUpgraderRenderer::new);
    }
}
