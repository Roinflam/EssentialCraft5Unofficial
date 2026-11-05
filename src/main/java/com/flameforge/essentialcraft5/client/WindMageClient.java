package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.MRUArrowRenderer;
import com.flameforge.essentialcraft5.client.render.WindMageRenderer;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 风法师模块的客户端注册（模组总线）：风法师的模型层（64×32 人形）与渲染器、MRU 箭的渲染器。
 * 刷怪蛋的颜色由 Forge 的 ForgeSpawnEggItem 自动注册，风法师套装的盔甲模型和 3D 图标由框架（client/armor）处理，这里都不用管。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WindMageClient {

    private WindMageClient() {
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WindMageRenderer.LAYER, WindMageRenderer::createLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECWindMage.WIND_MAGE.get(), WindMageRenderer::new);
        event.registerEntityRenderer(ECWindMage.MRU_ARROW.get(), MRUArrowRenderer::new);
    }
}
