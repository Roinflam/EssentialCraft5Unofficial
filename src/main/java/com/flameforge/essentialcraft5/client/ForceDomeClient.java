package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.ForceDomeScreen;
import com.flameforge.essentialcraft5.registry.ECForceDome;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 力场穹顶的客户端注册（模组总线）：只有界面。方块是普通的 cube_bottom_top 模型，没有方块实体渲染。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForceDomeClient {

    private ForceDomeClient() {
    }

    @SubscribeEvent
    public static void forceDomeClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECForceDome.FORCE_DOME_MENU.get(), ForceDomeScreen::new));
    }
}
