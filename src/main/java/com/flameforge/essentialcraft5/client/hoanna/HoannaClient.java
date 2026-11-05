package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 海纳尔画面的客户端注册（第六批 6a M3，batch6a.md §5.3；模组总线、只在客户端，Forge 自动发现）：
 * <ul>
 *   <li>维度效果 {@code essentialcraft5:hoanna} → {@link HoannaEffects}（维度类型 JSON 的 effects；没注册时客户端退回主世界效果）；</li>
 *   <li>HUD 层 {@code hoanna_radiation}（{@link RadiationOverlay}，原版霜冻覆盖层之上）、{@code hoanna_event_hud}（{@link HoannaEventHud}，Boss 血条之上）；</li>
 *   <li>资源重载时丢掉天空的 VBO（{@link HoannaSkyRenderer#release}，下次画天空时在渲染线程重建）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoannaClient {

    private HoannaClient() {
    }

    @SubscribeEvent
    public static void registerHoannaEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ECHoanna.EFFECTS, new HoannaEffects());
    }

    @SubscribeEvent
    public static void registerHoannaOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.FROSTBITE.id(), "hoanna_radiation", RadiationOverlay::render);
        event.registerAbove(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), "hoanna_event_hud", HoannaEventHud::render);
    }

    @SubscribeEvent
    public static void registerHoannaReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> HoannaSkyRenderer.release());
    }
}
