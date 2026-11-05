package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.SolarPrismRenderer;
import com.flameforge.essentialcraft5.client.screen.EnderGeneratorScreen;
import com.flameforge.essentialcraft5.client.screen.SunRayAbsorberScreen;
import com.flameforge.essentialcraft5.registry.ECSolar;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 日光与末影模块的客户端注册（模组总线，Forge 自动发现）：两个界面、太阳棱镜光柱的 BER。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SolarClient {

    private SolarClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECSolar.SUN_RAY_ABSORBER_MENU.get(), SunRayAbsorberScreen::new);
            MenuScreens.register(ECSolar.ENDER_GENERATOR_MENU.get(), EnderGeneratorScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECSolar.SOLAR_PRISM_BE.get(), SolarPrismRenderer::new);
    }
}
