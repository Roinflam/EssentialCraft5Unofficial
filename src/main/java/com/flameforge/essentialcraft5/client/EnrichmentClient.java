package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.EnrichmentAcceptorScreen;
import com.flameforge.essentialcraft5.client.screen.EnrichmentStateCheckerScreen;
import com.flameforge.essentialcraft5.client.screen.IntersectorScreen;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 富集仓模块的客户端注册（模组总线，Forge 自动发现）：接收器、状态检测器、汇流器三个界面。
 * 原作接收器的 TESR（RenderMRULink）是空的，不移植；汇流器的渲染层写在模型 JSON 里（cutout_mipped）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EnrichmentClient {

    private EnrichmentClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECEnrichment.EC_ACCEPTOR_MENU.get(), EnrichmentAcceptorScreen::new);
            MenuScreens.register(ECEnrichment.EC_STATE_CHECKER_MENU.get(), EnrichmentStateCheckerScreen::new);
            MenuScreens.register(ECEnrichment.INTERSECTOR_MENU.get(), IntersectorScreen::new);
        });
    }
}
