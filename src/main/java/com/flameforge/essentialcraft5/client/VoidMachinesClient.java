package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.DimensionalTransceiverScreen;
import com.flameforge.essentialcraft5.client.screen.MagicalFurnaceScreen;
import com.flameforge.essentialcraft5.client.screen.MagicalTeleporterScreen;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 第六批 6b M2「传送与熔炼」的客户端注册（模组总线，Forge 自动发现）：三台机器的界面。
 * 粒子在各自方块实体的 clientTick 里放（{@code client/render/SpellFx}），三个方块都是整块模型，不用方块实体渲染器。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VoidMachinesClient {

    private VoidMachinesClient() {
    }

    @SubscribeEvent
    public static void voidMachinesClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECVoidMachines.MAGICAL_TELEPORTER_MENU.get(), MagicalTeleporterScreen::new);
            MenuScreens.register(ECVoidMachines.MAGICAL_FURNACE_MENU.get(), MagicalFurnaceScreen::new);
            MenuScreens.register(ECVoidMachines.DIMENSIONAL_TRANSCEIVER_MENU.get(), DimensionalTransceiverScreen::new);
        });
    }
}
