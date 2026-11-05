package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.AbyssTyrantModel;
import com.flameforge.essentialcraft5.client.model.mob.AbyssalEelModel;
import com.flameforge.essentialcraft5.client.render.mob.ECMobRenderer;
import com.flameforge.essentialcraft5.registry.ECSunkenDistrict;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13e-S2「沉没城区与渊潮领主」的客户端注册（模组总线，Forge 自动发现）：渊潮领主的模型层（几何照渊鳗）和渲染器
 * （放大 {@value AbyssTyrantModel#SCALE} 倍、带发光层，贴图 {@code textures/entity/abyss_tyrant/}）。沉城祭坛是普通方块，没有方块实体渲染。
 * 注册类是 {@code registry/ECSunkenDistrict}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SunkenDistrictClient {

    private SunkenDistrictClient() {
    }

    @SubscribeEvent
    public static void sunkenDistrictLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AbyssTyrantModel.LAYER, AbyssalEelModel::createLayer);
    }

    @SubscribeEvent
    public static void sunkenDistrictRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECSunkenDistrict.ABYSS_TYRANT.get(),
                ctx -> new ECMobRenderer<>(ctx, new AbyssTyrantModel(ctx.bakeLayer(AbyssTyrantModel.LAYER)), 0.4F, "abyss_tyrant")
                        .withGlow().withScale(AbyssTyrantModel.SCALE));
    }
}
