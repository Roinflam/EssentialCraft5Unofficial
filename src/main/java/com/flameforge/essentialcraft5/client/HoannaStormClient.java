package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.hoanna.LeyStarfallRenderer;
import com.flameforge.essentialcraft5.registry.ECHoannaStorm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13d-H2「环境机制」的客户端注册（模组总线，Forge 自动发现）：地脉落晶的渲染器（{@link LeyStarfallRenderer}，用物品模型，没有自己的模型层）。
 * 风暴的天色、光幕、光点和辐射热点的细尘都在游戏总线的 {@code client/hoanna/*} 里（{@code HoannaSkyRenderer}、{@code HoannaEventVisuals}、
 * {@code HoannaStormVisuals}），用原版粒子，不注册新粒子。注册类是 {@code registry/ECHoannaStorm}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoannaStormClient {

    private HoannaStormClient() {
    }

    @SubscribeEvent
    public static void hoannaStormLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
    }

    @SubscribeEvent
    public static void hoannaStormRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECHoannaStorm.LEY_STARFALL.get(), LeyStarfallRenderer::new);
    }
}
