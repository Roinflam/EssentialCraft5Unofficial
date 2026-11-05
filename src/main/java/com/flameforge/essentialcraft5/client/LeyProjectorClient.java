package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.LeyProjectorRenderer;
import com.flameforge.essentialcraft5.client.screen.LeyProjectorScreen;
import com.flameforge.essentialcraft5.registry.ECLeyProjector;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 地脉投影仪的客户端注册（模组总线）：界面、光束 BER。方块的渲染层（cutout_mipped）写在模型 JSON 里，不用另外登记。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LeyProjectorClient {

    private LeyProjectorClient() {
    }

    @SubscribeEvent
    public static void leyProjectorClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECLeyProjector.LEY_PROJECTOR_MENU.get(), LeyProjectorScreen::new));
    }

    @SubscribeEvent
    public static void leyProjectorRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECLeyProjector.LEY_PROJECTOR_BE.get(), LeyProjectorRenderer::new);
    }
}
