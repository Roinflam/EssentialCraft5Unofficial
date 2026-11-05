package com.flameforge.essentialcraft5.lore;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 源质残卷的登录判定（batch12.md §4.1.4）：每次登录再判一次集齐。
 * <p>
 * 读完一卷时已经判过，这里补的是改过开关的老存档：门开着时读了前五卷、后来门关了（集齐只要五卷），登录时补发集齐的进度。
 * 只查几次进度表，没有别的开销。处理器名带模块前缀（同包里不能和别的事件类撞名，tools/check_event_handlers.py 查）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ScrollEvents {

    private ScrollEvents() {
    }

    @SubscribeEvent
    public static void onScrollLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScrollVolumes.tryAwardComplete(player);
        }
    }
}
