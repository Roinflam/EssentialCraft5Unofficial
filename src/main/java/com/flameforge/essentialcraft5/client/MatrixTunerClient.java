package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.MatrixTunerScreen;
import com.flameforge.essentialcraft5.registry.ECMatrixTuner;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 矩阵调律器的客户端注册（模组总线）：只有界面。方块是普通 JSON 模型，没有 BER；声音和粒子走机器氛围框架。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MatrixTunerClient {

    private MatrixTunerClient() {
    }

    @SubscribeEvent
    public static void matrixTunerClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECMatrixTuner.MATRIX_TUNER_MENU.get(), MatrixTunerScreen::new));
    }
}
