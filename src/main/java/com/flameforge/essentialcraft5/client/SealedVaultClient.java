package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13e-S1「封印宝库」的客户端注册（模组总线，Forge 自动发现）：宝库核心 / 祭座的方块实体渲染（如果要）、粒子在这里登记；
 * 要自定义粒子的在这里另加一个 {@code RegisterParticleProvidersEvent} 处理器（方法名带 {@code sealedVault} 前缀，免得和别的客户端类撞名）。
 * 现在是空壳，由 13e-S1 填。注册类是 {@code registry/ECSealedVault}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SealedVaultClient {

    private SealedVaultClient() {
    }

    @SubscribeEvent
    public static void sealedVaultLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
    }

    @SubscribeEvent
    public static void sealedVaultRenderers(EntityRenderersEvent.RegisterRenderers event) {}
}
