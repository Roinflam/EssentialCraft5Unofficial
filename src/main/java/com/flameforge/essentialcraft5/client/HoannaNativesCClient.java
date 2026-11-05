package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.CatacombRevenantModel;
import com.flameforge.essentialcraft5.client.render.mob.ECMobRenderer;
import com.flameforge.essentialcraft5.client.render.mob.InquisitoriumPatrollerRenderer;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13d-N3「审判庭巡卫、墓穴守灵」的客户端注册（模组总线，Forge 自动发现）：模型层（手写 {@code LayerDefinition}）和实体渲染器。
 * 巡卫用全息投影的模型层（{@code HologramClient} 已登记），这里只登记守灵的；巡卫的护甲破坏者画成一块魔力熔渣（同全息投影那一发），
 * 灵刃只有粒子（实体本身不画）。注册类是 {@code registry/ECHoannaNativesC}；刷怪蛋的颜色由 Forge 的 ForgeSpawnEggItem 自动注册。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoannaNativesCClient {

    private HoannaNativesCClient() {
    }

    @SubscribeEvent
    public static void hoannaNativesCLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CatacombRevenantModel.LAYER, CatacombRevenantModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void hoannaNativesCRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECHoannaNativesC.INQUISITORIUM_PATROLLER.get(), InquisitoriumPatrollerRenderer::new);
        event.registerEntityRenderer(ECHoannaNativesC.CATACOMB_REVENANT.get(),
                ctx -> new ECMobRenderer<>(ctx, new CatacombRevenantModel(ctx.bakeLayer(CatacombRevenantModel.LAYER)), 0.5F, "catacomb_revenant")
                        .withGlow().withElite());
        event.registerEntityRenderer(ECHoannaNativesC.PATROLLER_DESTROYER.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.75F, true));
        event.registerEntityRenderer(ECHoannaNativesC.REVENANT_BLADE.get(), NoopRenderer::new);
    }
}
