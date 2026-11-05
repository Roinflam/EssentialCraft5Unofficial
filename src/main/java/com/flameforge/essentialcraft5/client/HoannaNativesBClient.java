package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.AbyssalEelModel;
import com.flameforge.essentialcraft5.client.model.mob.ChaosAberrantModel;
import com.flameforge.essentialcraft5.client.model.mob.ChaosLarvaModel;
import com.flameforge.essentialcraft5.client.model.mob.EmberSpiritModel;
import com.flameforge.essentialcraft5.client.render.mob.ECMobRenderer;
import com.flameforge.essentialcraft5.client.render.mob.EmberSpiritRenderer;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13d-N2「混沌畸变体、畸变幼体、余烬灵、渊鳗」的客户端注册（模组总线，Forge 自动发现）：模型层（手写 {@code LayerDefinition}）和实体渲染器。
 * 畸变幼体和畸变体共用 {@code chaos_aberrant} 的贴图和发光层（幼体的 UV 在同一张图上）；余烬弹不画模型，只靠自己撒的粒子（{@link NoopRenderer}）。
 * 注册类是 {@code registry/ECHoannaNativesB}；刷怪蛋的颜色由 Forge 的 ForgeSpawnEggItem 自动注册。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoannaNativesBClient {

    private HoannaNativesBClient() {
    }

    @SubscribeEvent
    public static void hoannaNativesBLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ChaosAberrantModel.LAYER, ChaosAberrantModel::createLayer);
        event.registerLayerDefinition(ChaosLarvaModel.LAYER, ChaosLarvaModel::createLayer);
        event.registerLayerDefinition(EmberSpiritModel.LAYER, EmberSpiritModel::createLayer);
        event.registerLayerDefinition(AbyssalEelModel.LAYER, AbyssalEelModel::createLayer);
    }

    @SubscribeEvent
    public static void hoannaNativesBRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECHoannaNativesB.CHAOS_ABERRANT.get(),
                ctx -> new ECMobRenderer<>(ctx, new ChaosAberrantModel(ctx.bakeLayer(ChaosAberrantModel.LAYER)), 0.8F, "chaos_aberrant")
                        .withGlow().withElite());
        event.registerEntityRenderer(ECHoannaNativesB.CHAOS_LARVA.get(),
                ctx -> new ECMobRenderer<>(ctx, new ChaosLarvaModel(ctx.bakeLayer(ChaosLarvaModel.LAYER)), 0.25F, "chaos_aberrant").withGlow());
        event.registerEntityRenderer(ECHoannaNativesB.EMBER_SPIRIT.get(), EmberSpiritRenderer::new);
        event.registerEntityRenderer(ECHoannaNativesB.EMBER_SPIRIT_BOLT.get(), NoopRenderer::new);
        event.registerEntityRenderer(ECHoannaNativesB.ABYSSAL_EEL.get(),
                ctx -> new ECMobRenderer<>(ctx, new AbyssalEelModel(ctx.bakeLayer(AbyssalEelModel.LAYER)), 0.4F, "abyssal_eel")
                        .withGlow().withElite());
    }
}
