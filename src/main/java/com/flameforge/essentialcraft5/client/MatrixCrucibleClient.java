package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.MatrixCrucibleRenderer;
import com.flameforge.essentialcraft5.client.render.MatrixEchoRenderer;
import com.flameforge.essentialcraft5.client.screen.MatrixCrucibleScreen;
import com.flameforge.essentialcraft5.registry.ECMatrix;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 矩阵淬炼台与矩阵回响的客户端注册（模组总线）：界面、台座上转的灵魂石、回响的渲染。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MatrixCrucibleClient {

    private MatrixCrucibleClient() {
    }

    @SubscribeEvent
    public static void matrixCrucibleClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECMatrix.MATRIX_CRUCIBLE_MENU.get(), MatrixCrucibleScreen::new));
    }

    @SubscribeEvent
    public static void matrixCrucibleRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECMatrix.MATRIX_CRUCIBLE_BE.get(), MatrixCrucibleRenderer::new);
        event.registerEntityRenderer(ECMatrix.MATRIX_ECHO.get(), MatrixEchoRenderer::new);
    }
}
