package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「轨道打击遥控器与二分枪」的客户端部分（registry/ECHoloWeapons）：
 * <ul>
 *   <li>二分弹画成一块魔力熔渣（原版投掷物渲染器，原作 {@code ThrowableRenderFactory(magicalSlag)}），尾迹由 {@code client/vfx/weapon/ProjectileTrailFx} 画。</li>
 *   <li>遥控器的预览光柱在 {@code client/vfx/weapon/OrbitalPreviewFx}（等离子光柱，原作是品红信标光柱）。</li>
 * </ul>
 * 轨道打击、二分场两个技能实体的渲染器在 {@link HoloStrikeClient}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoloWeaponsClient {

    private HoloWeaponsClient() {
    }

    @SubscribeEvent
    public static void holoWeaponsRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECHoloWeapons.DIVIDER_PROJECTILE.get(), ThrownItemRenderer::new);
    }
}
