package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.DemonModel;
import com.flameforge.essentialcraft5.client.render.DemonRenderer;
import com.flameforge.essentialcraft5.client.render.DemonicPentacleRenderer;
import com.flameforge.essentialcraft5.client.screen.DemonTradeScreen;
import com.flameforge.essentialcraft5.registry.ECDemon;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 恶魔模块的客户端注册（模组总线，Forge 自动发现）：交易界面、恶魔的模型层与渲染器、恶魔五芒星地上的符文（BER）。
 * 刷怪蛋的颜色由 Forge 的 ForgeSpawnEggItem 自动注册。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DemonClient {

    private DemonClient() {
    }

    @SubscribeEvent
    public static void demonClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECDemon.DEMON_TRADE_MENU.get(), DemonTradeScreen::new));
    }

    @SubscribeEvent
    public static void demonLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DemonModel.LAYER, DemonModel::createLayer);
    }

    @SubscribeEvent
    public static void demonRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECDemon.DEMON.get(), DemonRenderer::new);
        event.registerBlockEntityRenderer(ECDemon.DEMONIC_PENTACLE_BE.get(), DemonicPentacleRenderer::new);
    }
}
