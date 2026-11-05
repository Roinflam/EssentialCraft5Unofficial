package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.boss.BossFxClient;
import com.flameforge.essentialcraft5.client.boss.CalamityHeartOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13c-Fb「首领与渲染框架」的客户端注册（模组总线，Forge 自动发现）。
 * <ul>
 *   <li>首领整屏画面的 HUD 层（{@link BossFxClient#render}）：登记在霜冻覆盖层之上，和辐射晕影同一层次（原版暗角、南瓜头、霜冻都在下面，准星、快捷栏在上面）；</li>
 *   <li>模型层、渲染器：框架本身没有实体，留给用框架的首领在自己的客户端类里登记（13d / 13e），这两个处理器保持空。</li>
 * </ul>
 * 注册类是 {@code registry/ECBossFramework}；渲染基类在 {@code client/render/mob}，关键帧辅助在 {@code client/model/mob/anim}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BossFrameworkClient {

    private BossFrameworkClient() {
    }

    @SubscribeEvent
    public static void bossFrameworkLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
    }

    @SubscribeEvent
    public static void bossFrameworkRenderers(EntityRenderersEvent.RegisterRenderers event) {
    }

    @SubscribeEvent
    public static void bossFrameworkOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.FROSTBITE.id(), "boss_fx", BossFxClient::render);
        // 天灾之心的名字大字、横幅：压在所有原版层上面（同标题字幕）
        event.registerAboveAll("calamity_heart_title", CalamityHeartOverlay::render);
    }
}
