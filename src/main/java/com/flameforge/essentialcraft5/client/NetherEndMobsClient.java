package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.NetherImpModel;
import com.flameforge.essentialcraft5.client.model.mob.VoidStalkerModel;
import com.flameforge.essentialcraft5.client.render.mob.ECMobRenderer;
import com.flameforge.essentialcraft5.client.render.mob.VoidStalkerRenderer;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13c-M3「炼狱小鬼、虚空潜行者」的客户端注册（模组总线，Forge 自动发现）：模型层（手写 {@code LayerDefinition}）和实体渲染器。
 * 余火弹画成一颗缩小的火焰弹（同原版小火球，物品图标、全亮）。注册类是 {@code registry/ECNetherEndMobs}；刷怪蛋的颜色由 Forge 的 ForgeSpawnEggItem 自动注册。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NetherEndMobsClient {

    private NetherEndMobsClient() {
    }

    @SubscribeEvent
    public static void netherEndMobsLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NetherImpModel.LAYER, NetherImpModel::createBodyLayer);
        event.registerLayerDefinition(VoidStalkerModel.LAYER, VoidStalkerModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void netherEndMobsRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECNetherEndMobs.NETHER_IMP.get(),
                ctx -> new ECMobRenderer<>(ctx, new NetherImpModel(ctx.bakeLayer(NetherImpModel.LAYER)), 0.35F, "nether_imp").withGlow().withElite());
        event.registerEntityRenderer(ECNetherEndMobs.IMP_EMBER_BOLT.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.5F, true));
        event.registerEntityRenderer(ECNetherEndMobs.VOID_STALKER.get(), VoidStalkerRenderer::new);
    }
}
