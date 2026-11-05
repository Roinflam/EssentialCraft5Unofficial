package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.EmberInlayItem;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 余烬嵌刻的客户端部分（第十批 10b）：嵌片两层按余烬染色（模组总线），嵌好的武器提示里多一行「余烬嵌刻：X + Y（效果）」（Forge 总线）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EmberInlayClient {

    private EmberInlayClient() {
    }

    @SubscribeEvent
    public static void emberInlayItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(EmberInlayItem::tint, ECEmberForge.EMBER_INLAY.get());
    }

    /** 武器提示（Forge 总线）：排在 LOW，放在别的模组加的行后面、紧跟物品自己的说明 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
    public static final class EmberInlayTooltips {

        private EmberInlayTooltips() {
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void onEmberInlayTooltip(ItemTooltipEvent event) {
            EmberInlayItem.inlayOf(event.getItemStack()).ifPresent(pair -> {
                int at = Math.min(1, event.getToolTip().size());
                event.getToolTip().add(at, Component.translatable("tooltip.essentialcraft5.ember_inlay.weapon",
                        EmberInlayItem.emberName(pair.first()), EmberInlayItem.emberName(pair.second()), EmberInlayItem.effectNames(pair))
                        .withStyle(ChatFormatting.GOLD));
            });
        }
    }
}
