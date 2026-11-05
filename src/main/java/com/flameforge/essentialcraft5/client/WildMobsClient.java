package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.ElementalWispModel;
import com.flameforge.essentialcraft5.client.model.mob.MatrixWraithModel;
import com.flameforge.essentialcraft5.client.render.mob.ECMobRenderer;
import com.flameforge.essentialcraft5.client.render.mob.ElementalWispRenderer;
import com.flameforge.essentialcraft5.registry.ECWildMobs;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13c-M1「元素游灵、矩阵游魂、风暴术士」的客户端注册（模组总线，Forge 自动发现）：两只新怪的模型层（手写 {@code LayerDefinition}）和渲染器；
 * 元素聚灵弹、疾风弹不画模型，只靠实体自己撒的粒子（{@link NoopRenderer}）。风暴术士沿用风法师的渲染器（第 4 张贴图）。
 * 注册类是 {@code registry/ECWildMobs}；刷怪蛋的颜色由 Forge 的 ForgeSpawnEggItem 自动注册。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WildMobsClient {

    private WildMobsClient() {
    }

    @SubscribeEvent
    public static void wildMobsLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ElementalWispModel.LAYER, ElementalWispModel::createLayer);
        event.registerLayerDefinition(MatrixWraithModel.LAYER, MatrixWraithModel::createLayer);
    }

    @SubscribeEvent
    public static void wildMobsRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECWildMobs.ELEMENTAL_WISP.get(), ElementalWispRenderer::new);
        // 模型连兜帽约 2.15 格高（袍子离地飘着），碰撞箱 1.9：整只缩到 0.9，头顶和碰撞箱齐平，砍头不会砍空
        event.registerEntityRenderer(ECWildMobs.MATRIX_WRAITH.get(),
                ctx -> new ECMobRenderer<>(ctx, new MatrixWraithModel(ctx.bakeLayer(MatrixWraithModel.LAYER)), 0.5F, "matrix_wraith")
                        .withScale(MatrixWraithModel.RENDER_SCALE).withGlow().withElite());
        event.registerEntityRenderer(ECWildMobs.WISP_BOLT.get(), NoopRenderer::new);
        event.registerEntityRenderer(ECWildMobs.STORM_GUST.get(), NoopRenderer::new);
    }
}
