package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.MRUConverterScreen;
import com.flameforge.essentialcraft5.registry.ECConverters;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * MRU 转换器的客户端注册（模组总线）：五台共用的界面。没有方块实体渲染器；渲染层（cutout）写在模型 JSON 里，
 * 侧面输出口的待机 / 工作两种样子靠方块状态 {@code active} 换模型。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ConvertersClient {

    private ConvertersClient() {
    }

    @SubscribeEvent
    public static void convertersClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECConverters.MRU_CONVERTER_MENU.get(), MRUConverterScreen::new));
    }
}
