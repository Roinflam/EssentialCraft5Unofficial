package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.MRUPresenceRenderer;
import com.flameforge.essentialcraft5.item.MRUMoverItem;
import com.flameforge.essentialcraft5.registry.ECMRUCU;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** MRU 聚集体模块的客户端注册（模组总线）：聚集体渲染器、移动魔杖的「使用中」贴图切换。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MRUCUClient {

    private MRUCUClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECMRUCU.MRU_PRESENCE.get(), MRUPresenceRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        // 原作把 active 写进 NBT；这里直接看持有者是不是正在使用这根魔杖
        event.enqueueWork(() -> ItemProperties.register(ECMRUCU.MRU_MOVER.get(), MRUMoverItem.ACTIVE_PREDICATE,
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1F : 0F));
    }
}
