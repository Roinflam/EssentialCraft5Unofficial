package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MagicalJukeboxBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalRepairerBlockEntity;
import com.flameforge.essentialcraft5.client.render.DeviceItemRenderer;
import com.flameforge.essentialcraft5.client.render.MagicalEnchanterRenderer;
import com.flameforge.essentialcraft5.client.screen.MagicalEnchanterScreen;
import com.flameforge.essentialcraft5.client.screen.MagicalJukeboxScreen;
import com.flameforge.essentialcraft5.client.screen.MagicalRepairerScreen;
import com.flameforge.essentialcraft5.client.screen.PotionSpreaderScreen;
import com.flameforge.essentialcraft5.registry.ECDevices;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 魔力器械的客户端注册（模组总线）：四个界面；修复器（旋转的工具）、附魔台（翻页的书）、唱片机（立着的唱片）三个 BER。
 * 药水扩散器原作没有 TESR，只在客户端方块实体里冒药水粒子。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DevicesClient {

    private DevicesClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECDevices.MAGICAL_REPAIRER_MENU.get(), MagicalRepairerScreen::new);
            MenuScreens.register(ECDevices.MAGICAL_ENCHANTER_MENU.get(), MagicalEnchanterScreen::new);
            MenuScreens.register(ECDevices.POTION_SPREADER_MENU.get(), PotionSpreaderScreen::new);
            MenuScreens.register(ECDevices.MAGICAL_JUKEBOX_MENU.get(), MagicalJukeboxScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECDevices.MAGICAL_REPAIRER_BE.get(),
                DeviceItemRenderer.spinning(MagicalRepairerBlockEntity.SLOT_ITEM));
        event.registerBlockEntityRenderer(ECDevices.MAGICAL_ENCHANTER_BE.get(), MagicalEnchanterRenderer::new);
        event.registerBlockEntityRenderer(ECDevices.MAGICAL_JUKEBOX_BE.get(),
                DeviceItemRenderer.fixed(MagicalJukeboxBlockEntity.SLOT_RECORD, 90F));
    }
}
