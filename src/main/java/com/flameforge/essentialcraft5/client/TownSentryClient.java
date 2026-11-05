package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.SentryTurretModel;
import com.flameforge.essentialcraft5.client.model.mob.TownSentryModel;
import com.flameforge.essentialcraft5.client.render.mob.SentryTurretRenderer;
import com.flameforge.essentialcraft5.client.render.mob.TownSentryRenderer;
import com.flameforge.essentialcraft5.registry.ECTownSentry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13e-T「废城守望者」的客户端注册（模组总线，Forge 自动发现）：守望者、哨戒炮台的模型层和渲染器。
 * 两者共用贴图 {@code textures/entity/town_sentry/town_sentry.png}（128 × 64）和发光层。注册类是 {@code registry/ECTownSentry}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TownSentryClient {

    private TownSentryClient() {
    }

    @SubscribeEvent
    public static void townSentryLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TownSentryModel.LAYER, TownSentryModel::createBodyLayer);
        event.registerLayerDefinition(SentryTurretModel.LAYER, SentryTurretModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void townSentryRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECTownSentry.TOWN_SENTRY.get(), TownSentryRenderer::new);
        event.registerEntityRenderer(ECTownSentry.SENTRY_TURRET.get(), SentryTurretRenderer::new);
    }
}
