package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.MagicalChestRenderer;
import com.flameforge.essentialcraft5.client.render.MagicalDisplayRenderer;
import com.flameforge.essentialcraft5.client.screen.MagicalChestScreen;
import com.flameforge.essentialcraft5.registry.ECStorage;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 魔力箱、虚空箱与显示屏的客户端注册（模组总线）：两种箱子的界面、1.12 箱子模型层与渲染器（两种箱子共用，贴图按 Layout 取）、显示屏的渲染器。
 * 箱子在物品栏里用的是原作的 JSON 模型（models/block/legacy_chest.json + 箱子贴图），不需要物品渲染器。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StorageClient {

    private StorageClient() {
    }

    @SubscribeEvent
    public static void storageClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECStorage.MAGICAL_CHEST_MENU.get(), MagicalChestScreen::new);
            MenuScreens.register(ECStorage.VOID_CHEST_MENU.get(), MagicalChestScreen::new);
        });
    }

    @SubscribeEvent
    public static void storageLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MagicalChestRenderer.LAYER, MagicalChestRenderer::createLayer);
    }

    @SubscribeEvent
    public static void storageRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECStorage.MAGICAL_CHEST_BE.get(), MagicalChestRenderer::new);
        event.registerBlockEntityRenderer(ECStorage.VOID_CHEST_BE.get(), MagicalChestRenderer::new);
        event.registerBlockEntityRenderer(ECStorage.MAGICAL_DISPLAY_BE.get(), MagicalDisplayRenderer::new);
    }
}
