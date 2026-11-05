package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.ReactorRenderer;
import com.flameforge.essentialcraft5.registry.ECReactor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 特斯拉反应堆模块的客户端注册（模组总线，Forge 自动发现）：核心的方块实体渲染器（浮球 + 电弧）和它要的额外 OBJ 模型。
 * 支架是普通方块模型（multipart，datagen 生成），没有客户端代码。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ReactorClient {

    private ReactorClient() {
    }

    @SubscribeEvent
    public static void reactorRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECReactor.REACTOR_BE.get(), ReactorRenderer::new);
    }

    /** 浮球的 OBJ 不属于任何方块状态，要单独登记才会被烘焙 */
    @SubscribeEvent
    public static void reactorModels(ModelEvent.RegisterAdditional event) {
        event.register(ReactorRenderer.SPHERE);
    }
}
