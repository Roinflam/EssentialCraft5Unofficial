package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.RedstoneTransmitterScreen;
import com.flameforge.essentialcraft5.registry.ECRedstoneTools;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 拟态方块与红石小件的客户端注册（模组总线）：红石传递器的界面。
 * 拟态方块的伪装模型与颜色由框架 {@code client/camo/CamoClient} 统一包（{@code ECRedstoneTools.init()} 里登记了拟态方块），这里不再注册 BlockColor；
 * 红石传递器模型的 cutout_mipped 写在模型 JSON 的 {@code render_type} 里。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RedstoneToolsClient {

    private RedstoneToolsClient() {
    }

    @SubscribeEvent
    public static void redstoneToolsClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECRedstoneTools.REDSTONE_TRANSMITTER_MENU.get(), RedstoneTransmitterScreen::new));
    }
}
