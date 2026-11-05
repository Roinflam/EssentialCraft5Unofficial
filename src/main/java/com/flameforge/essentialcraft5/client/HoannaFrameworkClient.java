package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13d-HF「海纳尔框架」的客户端注册（模组总线，Forge 自动发现）：巢穴核心的方块实体渲染、粒子在这里登记；
 * 要自定义粒子的在这里另加一个 {@code RegisterParticleProvidersEvent} 处理器（方法名带 {@code hoannaFramework} 前缀，免得和别的客户端类撞名）。
 * 现在是空壳，由 13d-HF 填。注册类是 {@code registry/ECHoannaFramework}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoannaFrameworkClient {

    private HoannaFrameworkClient() {
    }

    @SubscribeEvent
    public static void hoannaFrameworkLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
    }

    @SubscribeEvent
    public static void hoannaFrameworkRenderers(EntityRenderersEvent.RegisterRenderers event) {}
}
