package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.CrafterScreen;
import com.flameforge.essentialcraft5.client.screen.CraftingFrameScreen;
import com.flameforge.essentialcraft5.registry.ECCrafter;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 自动合成模块的客户端注册（模组总线）：魔力合成器、合成框架两个界面 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrafterClient {

    private CrafterClient() {
    }

    @SubscribeEvent
    public static void crafterClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECCrafter.CRAFTER_MENU.get(), CrafterScreen::new);
            MenuScreens.register(ECCrafter.CRAFTING_FRAME_MENU.get(), CraftingFrameScreen::new);
        });
    }

    /** 合成框架的物品提示里「按住 Shift 列出样板」用（按键状态只在客户端有） */
    public static Boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }
}
