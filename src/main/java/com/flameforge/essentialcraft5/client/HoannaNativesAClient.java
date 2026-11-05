package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.FrostbittenModel;
import com.flameforge.essentialcraft5.client.model.mob.HoannaScavengerModel;
import com.flameforge.essentialcraft5.client.model.mob.ManaPrismModel;
import com.flameforge.essentialcraft5.client.render.mob.ECMobRenderer;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13d-N1「霜蚀者、浮游棱晶、遗民拾荒者」的客户端注册（模组总线，Forge 自动发现）：模型层（手写 {@code LayerDefinition}）和渲染器。
 * 三只都用通用的 {@code ECMobRenderer}：霜蚀者、拾荒者叠精英层，棱晶另叠发光层（光点、晶脉）。棱晶锁定时的预览光线是实体自己在客户端撒的粒子，
 * 光束画面借枪械光束包。注册类是 {@code registry/ECHoannaNativesA}；刷怪蛋的颜色由 Forge 的 ForgeSpawnEggItem 自动注册。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoannaNativesAClient {

    private HoannaNativesAClient() {
    }

    @SubscribeEvent
    public static void hoannaNativesALayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(FrostbittenModel.LAYER, FrostbittenModel::createLayer);
        event.registerLayerDefinition(ManaPrismModel.LAYER, ManaPrismModel::createLayer);
        event.registerLayerDefinition(HoannaScavengerModel.LAYER, HoannaScavengerModel::createLayer);
    }

    @SubscribeEvent
    public static void hoannaNativesARenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECHoannaNativesA.FROSTBITTEN.get(),
                ctx -> new ECMobRenderer<>(ctx, new FrostbittenModel(ctx.bakeLayer(FrostbittenModel.LAYER)), 0.5F, "frostbitten").withElite());
        event.registerEntityRenderer(ECHoannaNativesA.MANA_PRISM.get(),
                ctx -> new ECMobRenderer<>(ctx, new ManaPrismModel(ctx.bakeLayer(ManaPrismModel.LAYER)), 0.4F, "mana_prism")
                        .withScale(ManaPrismModel.RENDER_SCALE).withGlow().withElite());
        event.registerEntityRenderer(ECHoannaNativesA.HOANNA_SCAVENGER.get(),
                ctx -> new ECMobRenderer<>(ctx, new HoannaScavengerModel(ctx.bakeLayer(HoannaScavengerModel.LAYER)), 0.5F, "hoanna_scavenger").withElite());
    }
}
