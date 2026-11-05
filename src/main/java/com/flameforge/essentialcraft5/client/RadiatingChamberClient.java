package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.RadiatingChamberRenderer;
import com.flameforge.essentialcraft5.client.screen.RadiatingChamberScreen;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 辐射仓模块的客户端注册（模组总线，Forge 自动发现）：界面、顶部物品的 BER。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RadiatingChamberClient {

    private RadiatingChamberClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECRadiatingChamber.RADIATING_CHAMBER_MENU.get(), RadiatingChamberScreen::new));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECRadiatingChamber.RADIATING_CHAMBER_BE.get(), RadiatingChamberRenderer::new);
    }
}
