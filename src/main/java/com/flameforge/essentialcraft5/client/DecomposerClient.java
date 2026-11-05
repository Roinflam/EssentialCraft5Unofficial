package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.ElementalDecomposerScreen;
import com.flameforge.essentialcraft5.registry.ECDecomposer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 元素分解器的客户端注册（模组总线）：只有界面。JEI 分类在 {@code compat/jei/DecomposerCategory}。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DecomposerClient {

    private DecomposerClient() {
    }

    @SubscribeEvent
    public static void decomposerClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECDecomposer.ELEMENTAL_DECOMPOSER_MENU.get(), ElementalDecomposerScreen::new));
    }
}
