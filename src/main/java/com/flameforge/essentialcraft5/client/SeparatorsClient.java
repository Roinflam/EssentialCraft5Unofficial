package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.AnimalSeparatorScreen;
import com.flameforge.essentialcraft5.registry.ECSeparators;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 动物分离器的客户端注册（模组总线，Forge 自动发现）：幼年 / 成年共用的界面。方块是六面同贴图的普通方块，没有 BER。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SeparatorsClient {

    private SeparatorsClient() {
    }

    @SubscribeEvent
    public static void separatorsClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECSeparators.ANIMAL_SEPARATOR_MENU.get(), AnimalSeparatorScreen::new));
    }
}
