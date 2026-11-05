package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.FilterScreen;
import com.flameforge.essentialcraft5.client.screen.MagicalHopperScreen;
import com.flameforge.essentialcraft5.registry.ECLogistics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 物流模块的客户端注册（模组总线）：魔力漏斗、物品过滤器两个界面。
 * 另给物品提示用一个「Shift 按着没有」（{@code FilterItem} 经 DistExecutor 调，专用服不加载这个类）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LogisticsClient {

    private LogisticsClient() {
    }

    @SubscribeEvent
    public static void logisticsClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECLogistics.MAGICAL_HOPPER_MENU.get(), MagicalHopperScreen::new);
            MenuScreens.register(ECLogistics.FILTER_MENU.get(), FilterScreen::new);
        });
    }

    /** 物品提示里「按住 Shift 列出样本」用 */
    public static Boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }
}
