package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.HoloDecoyRenderer;
import com.flameforge.essentialcraft5.registry.ECHoloDecoy;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 全息诱饵的客户端注册（模组总线，Forge 自动发现）：诱饵实体的渲染器 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoloDecoyClient {

    private HoloDecoyClient() {
    }

    @SubscribeEvent
    public static void holoDecoyRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECHoloDecoy.HOLO_DECOY.get(), HoloDecoyRenderer::new);
    }
}
