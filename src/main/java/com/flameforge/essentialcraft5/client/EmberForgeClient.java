package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.ForgedSwordModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 余烬锻炉模块的客户端注册（第七批 M2）：余烬元素剑的模型 loader {@code essentialcraft5:forged_sword}。
 * 锻炉没有方块实体渲染器，粒子和火焰声在 {@code EmberForgeBlockEntity.clientTick} 里放。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EmberForgeClient {

    private EmberForgeClient() {
    }

    @SubscribeEvent
    public static void registerForgedSwordGeometry(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ForgedSwordModel.LOADER_NAME, ForgedSwordModel.Loader.INSTANCE);
    }
}
