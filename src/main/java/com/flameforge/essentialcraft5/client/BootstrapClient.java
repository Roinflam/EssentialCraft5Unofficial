package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.MatrixAbsorberRenderer;
import com.flameforge.essentialcraft5.client.screen.MatrixAbsorberScreen;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.Nullable;

/** ECBootstrap 模块的客户端注册（模组总线）：矩阵换能器的界面和 BER。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BootstrapClient {

    private BootstrapClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECBootstrap.MATRIX_ABSORBER_MENU.get(), MatrixAbsorberScreen::new));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECBootstrap.MATRIX_ABSORBER_BE.get(), MatrixAbsorberRenderer::new);
    }

    /** 本机玩家（灵魂石 tooltip 判断查看者是不是主人）。只能经 DistExecutor 在客户端调用。 */
    @Nullable
    public static Player localPlayer() {
        return Minecraft.getInstance().player;
    }
}
