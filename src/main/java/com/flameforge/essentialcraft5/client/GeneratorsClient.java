package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.RayTowerRenderer;
import com.flameforge.essentialcraft5.client.screen.ColdDistillatorScreen;
import com.flameforge.essentialcraft5.client.screen.HeatGeneratorScreen;
import com.flameforge.essentialcraft5.client.screen.NaturalFurnaceScreen;
import com.flameforge.essentialcraft5.client.screen.RayTowerScreen;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 产能器与射线塔模块的客户端注册（模组总线，Forge 自动发现）：四个界面、射线塔水晶的 BER。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GeneratorsClient {

    private GeneratorsClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECGenerators.HEAT_GENERATOR_MENU.get(), HeatGeneratorScreen::new);
            MenuScreens.register(ECGenerators.NATURAL_FURNACE_MENU.get(), NaturalFurnaceScreen::new);
            MenuScreens.register(ECGenerators.COLD_DISTILLATOR_MENU.get(), ColdDistillatorScreen::new);
            MenuScreens.register(ECGenerators.RAY_TOWER_MENU.get(), RayTowerScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECGenerators.RAY_TOWER_BE.get(), RayTowerRenderer::new);
    }
}
