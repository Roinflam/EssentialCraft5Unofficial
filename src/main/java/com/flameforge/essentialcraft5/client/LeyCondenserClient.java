package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.LeyCondenserRenderer;
import com.flameforge.essentialcraft5.client.screen.LeyCondenserScreen;
import com.flameforge.essentialcraft5.registry.ECLeyCondenser;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 地脉凝聚器的客户端注册（模组总线）：界面、头顶影像与光束的 BER。渲染层（cutout_mipped）写在模型 JSON 里。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LeyCondenserClient {

    private LeyCondenserClient() {
    }

    @SubscribeEvent
    public static void leyCondenserClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECLeyCondenser.LEY_CONDENSER_MENU.get(), LeyCondenserScreen::new));
    }

    @SubscribeEvent
    public static void leyCondenserRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECLeyCondenser.LEY_CONDENSER_BE.get(), LeyCondenserRenderer::new);
    }
}
