package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.UltraHeatGeneratorScreen;
import com.flameforge.essentialcraft5.client.screen.UltraNaturalFurnaceScreen;
import com.flameforge.essentialcraft5.registry.ECUltraGenerators;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 终极产能器模块的客户端注册（模组总线，Forge 自动发现）：两个界面。两台机器都是 OBJ 静态模型，没有 BER。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class UltraGeneratorsClient {

    private UltraGeneratorsClient() {
    }

    @SubscribeEvent
    public static void ultraGeneratorsClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECUltraGenerators.ULTRA_HEAT_GENERATOR_MENU.get(), UltraHeatGeneratorScreen::new);
            MenuScreens.register(ECUltraGenerators.ULTRA_NATURAL_FURNACE_MENU.get(), UltraNaturalFurnaceScreen::new);
        });
    }
}
