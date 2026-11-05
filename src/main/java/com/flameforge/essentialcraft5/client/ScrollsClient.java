package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.data.GuideUnlocks;
import com.flameforge.essentialcraft5.item.AncientScrollItem;
import com.flameforge.essentialcraft5.lore.ScrollVolumes;
import com.flameforge.essentialcraft5.registry.ECScrolls;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 源质残卷的客户端部分（第十二批，batch12.md §4.1.5）：蜡封（模型 layer1）按卷着色；「这一卷读过没有」按本人收到的进度判断。
 * <p>
 * 读过的状态是代码发的隐藏进度。隐藏进度完成以后原版一定会同步给本人的客户端（完成的进度必定可见，见 {@link GuideUnlocks} 类注释），
 * 所以提示框的「已读 / 未读」、右键时客户端先不起手，都直接查它；刚进世界、进度包还没到的那一两刻按没读过算，服务端读完时还会再判一次。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ScrollsClient {

    private ScrollsClient() {
    }

    @SubscribeEvent
    public static void scrollsItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(AncientScrollItem::tint, ECScrolls.ANCIENT_SCROLL.get());
    }

    /** 本地玩家读过这一卷没有；卷号无效按没读过 */
    public static boolean isRead(int volume) {
        return ScrollVolumes.valid(volume) && GuideUnlocks.isDone(ScrollVolumes.advancement(volume));
    }
}
