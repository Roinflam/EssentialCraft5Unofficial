package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13c-Fa「生成与掉落框架」的客户端注册（模组总线，Forge 自动发现）。Fa 本身不画东西（渲染基类、发光 / 精英层归 13c-Fb），
 * 留这一个入口给要在客户端登记的零碎（比如精英变体的同步数据要客户端侧的什么登记），方法名带 {@code mobFramework} 前缀。现在是空壳。
 * 注册类是 {@code registry/ECMobFramework}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MobFrameworkClient {

    private MobFrameworkClient() {
    }

    @SubscribeEvent
    public static void mobFrameworkLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
    }

    @SubscribeEvent
    public static void mobFrameworkRenderers(EntityRenderersEvent.RegisterRenderers event) {
    }
}
