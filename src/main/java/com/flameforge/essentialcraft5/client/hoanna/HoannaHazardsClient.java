package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoannaHazards;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * M4 在客户端只做一件事：给毒雾实体注册空渲染器（第六批 6a，batch6a.md §5.4、§5.8 裁定 13）。
 * 毒雾的样子（慢慢漂移的半透明黄绿雾团、近处淡出、附近雾色变黄绿）归 M3 的 {@code client/hoanna/FumeHazeRenderer}，它按注册名
 * {@code essentialcraft5:poison_fume} 跟踪实体；辐射晕影、事件画面也都在 M3。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoannaHazardsClient {

    private HoannaHazardsClient() {
    }

    @SubscribeEvent
    public static void onHoannaHazardsRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECHoannaHazards.POISON_FUME.get(), NoopRenderer::new);
    }
}
