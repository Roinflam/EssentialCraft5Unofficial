package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第八批框架 8-Fa 的客户端注册（模组总线，Forge 自动发现）：轨道打击、二分场两个技能实体的渲染器。
 * 两个实体的渲染器都是空的，画面由 {@code client/vfx/weapon}（OrbitalStrikeFx、DividerFieldFx）跟着实体画。
 * 遥控器的预览光柱、二分弹的渲染由 ECHoloWeapons 模块自己的客户端类注册。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoloStrikeClient {

    private HoloStrikeClient() {
    }

    @SubscribeEvent
    public static void holoStrikeRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECHoloWeapons.ORBITAL_STRIKE.get(), NoopRenderer::new);
        event.registerEntityRenderer(ECHoloWeapons.DIVIDER_FIELD.get(), NoopRenderer::new);
    }
}
