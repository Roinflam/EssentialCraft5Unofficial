package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.MagicalMirrorRenderer;
import com.flameforge.essentialcraft5.registry.ECMirror;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 魔力镜的客户端注册（模组总线，Forge 自动发现）：方块实体渲染器，和它要画的两份额外 OBJ 模型（镜框、镜面）。
 * 物品模型是普通的 OBJ 方块物品模型（datagen 生成），不需要物品渲染器。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MirrorClient {

    private MirrorClient() {
    }

    @SubscribeEvent
    public static void mirrorRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECMirror.MAGICAL_MIRROR_BE.get(), MagicalMirrorRenderer::new);
    }

    /** BER 画的两份 OBJ 不属于任何方块状态，要单独登记才会被烘焙 */
    @SubscribeEvent
    public static void mirrorModels(ModelEvent.RegisterAdditional event) {
        event.register(MagicalMirrorRenderer.FRAME);
        event.register(MagicalMirrorRenderer.GLASS);
    }
}
